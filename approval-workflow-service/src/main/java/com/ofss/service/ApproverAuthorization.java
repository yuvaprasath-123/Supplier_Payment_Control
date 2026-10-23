package com.ofss.service;
import com.ofss.entity.ApprovalLevel;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;
/** Verify identity and authority with Developer 1 rather than accepting roles from a request body. */
@Component
public class ApproverAuthorization {
    private final RestClient client;
    public ApproverAuthorization(@Value("${integration.security-url:http://localhost:8081}") String url) {
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        client=RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }
    public void decision(String token,Long actorId,ApprovalLevel level,boolean manager) {
        User user=current(token);
        if (!user.userId().equals(actorId)) deny("approverId must match the authenticated user");
        if (manager) {
            if (user.roles()==null || !user.roles().contains("FINANCE_MANAGER")) deny("Finance Manager role required");
        } else {
            if (user.roles()==null || user.roles().stream().noneMatch(r->r.equals("FINANCE_APPROVER")||r.equals("FINANCE_MANAGER"))) deny("Approval role required");
            assignee(token,actorId,level);
        }
    }
    public void manager(String token) {
        User user=current(token);
        if (user.roles()==null || !user.roles().contains("FINANCE_MANAGER")) deny("Finance Manager role required");
    }
    public void assignee(String token,Long userId,ApprovalLevel level) {
        requireToken(token);
        try {
            User user=client.get().uri("/api/v1/users/{id}",userId).header("Authorization",token).retrieve().body(User.class);
            if(user==null || !"ACTIVE".equals(user.status()) || user.roles()==null || user.roles().stream().noneMatch(r->r.equals("FINANCE_APPROVER")||r.equals("FINANCE_MANAGER"))) deny("Assigned user must be an active approver");
            // This repository's Developer 1 controller exposes GET /approvers, but no per-user authority GET.
            // Query the requested level and higher levels to preserve the 'sufficient authority' rule.
            for(ApprovalLevel candidate:ApprovalLevel.values()) {
                if(candidate.ordinal()<level.ordinal()) continue;
                Authority[] matches=client.get().uri("/api/v1/approvers?approvalLevel={level}",candidate)
                        .header("Authorization",token).retrieve().body(Authority[].class);
                if(matches!=null && java.util.Arrays.stream(matches).anyMatch(a->userId.equals(a.userId())
                        && Boolean.TRUE.equals(a.active()) && a.approvalLevel()!=null && a.approvalLevel().ordinal()>=level.ordinal())) return;
            }
            deny("Assigned user lacks active approval authority");
        } catch (HttpClientErrorException ex) { throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Security service rejected the assignee or token"); }
        catch (RestClientException ex) { throw unavailable(); }
    }
    private User current(String token) {
        requireToken(token);
        try {
            User user=client.get().uri("/api/v1/auth/me").header("Authorization",token).retrieve().body(User.class);
            if (user==null || user.userId()==null || !"ACTIVE".equals(user.status())) deny("Active user required");
            return user;
        } catch (HttpClientErrorException ex) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Security service rejected the token"); }
        catch (RestClientException ex) { throw unavailable(); }
    }
    private void requireToken(String token) { if(token==null || !token.startsWith("Bearer ")) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Bearer token required"); }
    private void deny(String reason) { throw new ResponseStatusException(HttpStatus.FORBIDDEN,reason); }
    private ResponseStatusException unavailable() { return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Cannot verify approval authority with security-service"); }
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
    public record User(Long userId,String status,List<String> roles) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
    public record Authority(Long userId,ApprovalLevel approvalLevel,Boolean active) {}
}
