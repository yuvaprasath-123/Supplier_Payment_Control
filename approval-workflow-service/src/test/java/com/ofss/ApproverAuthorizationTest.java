package com.ofss;
import com.ofss.entity.ApprovalLevel;
import com.ofss.service.ApproverAuthorization;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
class ApproverAuthorizationTest {
    HttpServer server;
    ApproverAuthorization authorization;
    String role="FINANCE_APPROVER";
    String level="L2";
    String status="ACTIVE";
    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v1/",exchange->{
            String path=exchange.getRequestURI().getPath();
            String body=path.endsWith("/approvers")
                ? (exchange.getRequestURI().getQuery().equals("approvalLevel="+level)
                    ? "[{\"userId\":2,\"username\":\"demo\",\"approvalLevel\":\""+level+"\",\"active\":true}]" : "[]")
                : "{\"userId\":2,\"username\":\"demo\",\"email\":\"demo@example.test\",\"status\":\""+status+"\",\"roles\":[\""+role+"\"]}";
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json");
            int code="Bearer valid".equals(exchange.getRequestHeaders().getFirst("Authorization"))?200:401;
            exchange.sendResponseHeaders(code,bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start(); authorization=new ApproverAuthorization("http://127.0.0.1:"+server.getAddress().getPort());
    }
    @AfterEach void stop() { server.stop(0); }
    @Test void acceptsFullDeveloper1ResponseAndForwardsToken() {
        assertDoesNotThrow(()->authorization.decision("Bearer valid",2L,ApprovalLevel.L2,false));
        assertDoesNotThrow(()->authorization.assignee("Bearer valid",2L,ApprovalLevel.L1));
    }
    @Test void rejectsInsufficientAuthorityAndMismatchedIdentity() {
        assertEquals(403,assertThrows(ResponseStatusException.class,()->authorization.decision("Bearer valid",2L,ApprovalLevel.L3,false)).getStatusCode().value());
        assertThrows(ResponseStatusException.class,()->authorization.decision("Bearer valid",3L,ApprovalLevel.L1,false));
    }
    @Test void managerOverrideNeedsManagerRole() {
        assertThrows(ResponseStatusException.class,()->authorization.decision("Bearer valid",2L,ApprovalLevel.L3,true));
        role="FINANCE_MANAGER";
        assertDoesNotThrow(()->authorization.decision("Bearer valid",2L,ApprovalLevel.L3,true));
    }
    @Test void missingInvalidAndInactiveUsersAreDenied() {
        assertEquals(401,assertThrows(ResponseStatusException.class,()->authorization.manager(null)).getStatusCode().value());
        assertEquals(401,assertThrows(ResponseStatusException.class,()->authorization.manager("Bearer invalid")).getStatusCode().value());
        status="INACTIVE";
        assertThrows(ResponseStatusException.class,()->authorization.decision("Bearer valid",2L,ApprovalLevel.L1,false));
    }
}
