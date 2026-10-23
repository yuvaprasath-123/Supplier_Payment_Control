package com.ofss;
import com.ofss.dto.ApprovalRequests.*;
import com.ofss.entity.*;
import com.ofss.repository.*;
import com.ofss.service.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:approval;MODE=Oracle;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop"})
class ApprovalIntegrationTest {
    @Autowired ApprovalService service;
    @Autowired ApprovalTaskRepository tasks;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ApproverAuthorization authorization;
    @LocalServerPort int port;
    @Autowired org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping mappings;
    private static final AtomicLong ids=new AtomicLong(100);
    @Test void exposesEveryBlueprintApprovalPath() {
        var paths=mappings.getHandlerMethods().keySet().stream().flatMap(m->m.getPatternValues().stream()).collect(java.util.stream.Collectors.toSet());
        for(String path:java.util.List.of("/approval-routes","/approval-routes/payment/{paymentId}","/approval-routes/payment/{paymentId}/decision-explanation",
            "/approval-tasks","/approval-tasks/{approvalTaskId}","/approval-tasks/{approvalTaskId}/approve","/approval-tasks/{approvalTaskId}/reject",
            "/approval-tasks/{approvalTaskId}/override-and-approve","/approval-tasks/payment/{paymentId}/history","/approval-tasks/{approvalTaskId}/sla",
            "/approval-tasks/{approvalTaskId}/reassign-to-backup","/approval-tasks/{approvalTaskId}/escalate-to-next-level")) assertTrue(paths.contains("/api/v1"+path),path);
    }
    ApprovalTask task(ApprovalLevel amount,RiskBand band) {
        long payment=ids.incrementAndGet();
        service.route(new Route(payment,1L,new BigDecimal("75000.00"),band,amount,"v7"));
        return service.create(new Task(payment,2L,60),"Bearer test");
    }
    void expire(ApprovalTask t) { jdbc.update("update APPROVAL_TASKS set DUE_AT=? where ID=?",OffsetDateTime.now().minusMinutes(1),t.id); }
    @Test void routeUsesStricterLevelAndKeepsConfiguration() {
        ApprovalTask t=task(ApprovalLevel.L2,RiskBand.HIGH);
        assertEquals(ApprovalLevel.L3,t.requiredLevel);
        assertEquals("v7",service.routeForPayment(t.paymentId).configurationVersion);
        assertEquals(ApprovalLevel.L3,ApprovalService.stricter(ApprovalLevel.L3,ApprovalLevel.L1));
    }
    @Test void approvalPersistsSeparateDecisionAndCannotBeRepeated() {
        ApprovalTask t=task(ApprovalLevel.L1,RiskBand.LOW);
        assertEquals(ApprovalStatus.APPROVED,service.decide(t.id,new Decision(2L,"Reviewed"),"Bearer test",false,false).status);
        assertEquals(1,service.history(t.paymentId).decisions().size());
        assertThrows(ResponseStatusException.class,()->service.decide(t.id,new Decision(2L,"again"),"Bearer test",false,false));
        assertEquals(1,service.history(t.paymentId).decisions().size());
    }
    @Test void selfApprovalAndWrongAssignmentAreBlocked() {
        ApprovalTask t=task(ApprovalLevel.L1,RiskBand.LOW);
        assertEquals(403,assertThrows(ResponseStatusException.class,()->service.decide(t.id,new Decision(1L,"own"),"Bearer test",false,true)).getStatusCode().value());
        assertEquals(403,assertThrows(ResponseStatusException.class,()->service.decide(t.id,new Decision(3L,"other"),"Bearer test",false,false)).getStatusCode().value());
        assertTrue(service.history(t.paymentId).decisions().isEmpty());
    }
    @Test void rejectionAndOverrideRequireReasonsAndManagerIsVerified() {
        ApprovalTask t=task(ApprovalLevel.L1,RiskBand.LOW);
        assertThrows(ResponseStatusException.class,()->service.decide(t.id,new Decision(2L," "),"Bearer test",true,false));
        assertThrows(ResponseStatusException.class,()->service.decide(t.id,new Decision(3L," "),"Bearer test",false,true));
        service.decide(t.id,new Decision(3L,"Manager reviewed exception"),"Bearer manager",false,true);
        verify(authorization).decision("Bearer manager",3L,ApprovalLevel.L1,true);
        assertEquals("OVERRIDE_APPROVED",service.history(t.paymentId).decisions().getFirst().decision);
    }
    @Test void rejectionIsSavedWithItsReason() {
        ApprovalTask t=task(ApprovalLevel.L1,RiskBand.LOW);
        service.decide(t.id,new Decision(2L,"Incorrect invoice"),"Bearer test",true,false);
        assertEquals(ApprovalStatus.REJECTED,service.get(t.id).status);
        assertEquals("Incorrect invoice",service.history(t.paymentId).decisions().getFirst().remark);
    }
    @Test void escalationRequiresExpiredSlaAndRecordsPreviousAssignment() {
        ApprovalTask t=task(ApprovalLevel.L1,RiskBand.LOW);
        assertThrows(ResponseStatusException.class,()->service.escalate(t.id,new Escalate(3L,"SLA expired"),"Bearer manager"));
        expire(t); service.escalate(t.id,new Escalate(3L,"SLA expired"),"Bearer manager");
        assertEquals(ApprovalLevel.L2,service.get(t.id).requiredLevel);
        assertEquals(2L,service.history(t.paymentId).escalations().getFirst().previousApproverId);
    }
    @Test void expiredL3BecomesManualReviewAndManagerCanResolveIt() {
        ApprovalTask t=task(ApprovalLevel.L3,RiskBand.HIGH); expire(t);
        assertEquals(ApprovalStatus.MANUAL_REVIEW,service.escalate(t.id,new Escalate(null,"L3 expired"),"Bearer manager").status);
        service.decide(t.id,new Decision(4L,"Manager resolution"),"Bearer manager",false,true);
        assertEquals(ApprovalStatus.APPROVED,service.get(t.id).status);
    }
    @Test void backupPreservesLevelAndRenewsDeadline() {
        ApprovalTask t=task(ApprovalLevel.L2,RiskBand.LOW); expire(t);
        ApprovalTask result=service.reassign(t.id,new Reassign(3L,"Use same-level backup"),"Bearer manager");
        assertEquals(ApprovalLevel.L2,result.requiredLevel); assertEquals(3L,result.assignedApproverId);
        assertTrue(result.dueAt.isAfter(OffsetDateTime.now()));
        assertEquals("REASSIGNED_TO_BACKUP",service.history(t.paymentId).escalations().getFirst().action);
    }
    @Test void httpRouteAndTaskEndpointsUseSeparateResourcesAndValidateInput() throws Exception {
        var client=HttpClient.newHttpClient();
        var invalid=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/approval-routes"))
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{}" )).build();
        assertEquals(400,client.send(invalid,HttpResponse.BodyHandlers.ofString()).statusCode());
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/approval-tasks?status=PENDING_APPROVAL")).GET().build();
        assertEquals(200,client.send(request,HttpResponse.BodyHandlers.ofString()).statusCode());
    }
}
