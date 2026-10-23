package com.ofss;
import com.ofss.dto.*;
import com.ofss.service.AuditEventService;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:audit;MODE=Oracle;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop"})
class AuditIntegrationTest {
    @Autowired AuditEventService service;
    @Autowired org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping mappings;
    @Test void exposesEveryBlueprintAuditPath() {
        var paths=mappings.getHandlerMethods().keySet().stream().flatMap(m->m.getPatternValues().stream()).collect(java.util.stream.Collectors.toSet());
        for(String path:java.util.List.of("","/payment/{paymentId}","/payment/{paymentId}/decision-history","/payment/{paymentId}/risk-history","/payment/{paymentId}/risk-decision-snapshot"))
            assertTrue(paths.contains("/api/v1/audit-events"+path),path);
    }
    @Test void paymentTimelineIncludesLinkedNonPaymentResources() {
        service.record(new CreateAuditEventRequest("APPROVAL_GRANTED","APPROVAL_TASK","88","2","corr",null,"APPROVED",null,101L));
        service.record(new CreateAuditEventRequest("PAYMENT_SUBMITTED","PAYMENT_REQUEST","101","1","corr",null,null,null));
        assertEquals(2,service.byPayment(101L).size());
        assertTrue(service.byPayment(102L).isEmpty());
    }
    @Test void combinesStringActorEventAndDateFilters() {
        service.record(new CreateAuditEventRequest("FILTER_TEST","PAYMENT_REQUEST","201","employee-a","filter",null,null,null));
        var list=service.search(null,null,"filter","FILTER_TEST","employee-a",OffsetDateTime.now().minusMinutes(1),OffsetDateTime.now().plusMinutes(1));
        assertEquals(1,list.size());
        assertTrue(service.search(null,null,"filter","FILTER_TEST","employee-b",null,null).isEmpty());
        assertThrows(IllegalArgumentException.class,()->service.search(null,null,null,null,null,OffsetDateTime.now().plusDays(1),OffsetDateTime.now()));
    }
    @Test void reconstructsFrozenRiskDataAndRefusesIncompleteLatestEvent() {
        String state="{\"riskScore\":65,\"riskBand\":\"HIGH\"}";
        String metadata="{\"configurationVersion\":\"v7\",\"configurationSnapshot\":{\"highAbove\":60},\"scoreBreakdown\":[{\"rule\":\"TEST\",\"score\":65}]}";
        service.record(new CreateAuditEventRequest("RISK_ASSESSMENT_COMPLETED","RISK_ASSESSMENT","900","2",null,null,state,metadata,301L));
        var snapshot=service.snapshot(301L);
        assertEquals(65,snapshot.riskState().get("riskScore").asInt());
        assertEquals("v7",snapshot.configurationVersion().asString());
        service.record(new CreateAuditEventRequest("RISK_ASSESSMENT_COMPLETED","RISK_ASSESSMENT","901","2",null,null,state,"{}",301L));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.snapshot(301L)).getStatusCode().value());
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.snapshot(302L)).getStatusCode().value());
    }
}
