package com.ofss.controller;
import com.ofss.dto.*;
import com.ofss.service.AuditEventService;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/audit-events")
public class AuditEventController {
    private final AuditEventService service;
    public AuditEventController(AuditEventService service) { this.service=service; }
    @PostMapping public ResponseEntity<AuditEventResponse> record(@Valid @RequestBody CreateAuditEventRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.record(r));
    }
    @GetMapping public List<AuditEventResponse> find(
            @RequestParam(required=false) String resourceType,@RequestParam(required=false) String resourceId,
            @RequestParam(required=false) String correlationId,@RequestParam(required=false) String eventType,
            @RequestParam(required=false) String performedBy,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return service.search(resourceType,resourceId,correlationId,eventType,performedBy,from,to);
    }
    @GetMapping("/payment/{paymentId}") public List<AuditEventResponse> payment(@PathVariable Long paymentId) { return service.byPayment(paymentId); }
    @GetMapping("/payment/{paymentId}/decision-history") public List<AuditEventResponse> decisions(@PathVariable Long paymentId) {
        return service.byPayment(paymentId).stream().filter(e->e.eventType().contains("APPROV")||e.eventType().contains("ROUT")||e.eventType().contains("ESCALAT")||e.eventType().contains("REJECT")).toList();
    }
    @GetMapping("/payment/{paymentId}/risk-history") public List<AuditEventResponse> risk(@PathVariable Long paymentId) {
        return service.byPayment(paymentId).stream().filter(e->e.eventType().startsWith("RISK_")).toList();
    }
    @GetMapping("/payment/{paymentId}/risk-decision-snapshot") public RiskDecisionSnapshot snapshot(@PathVariable Long paymentId) { return service.snapshot(paymentId); }
}
