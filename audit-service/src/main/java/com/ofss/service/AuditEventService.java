package com.ofss.service;
import com.ofss.dto.*;
import com.ofss.entity.AuditEvent;
import com.ofss.repository.AuditEventRepository;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
@Service
public class AuditEventService {
    private final AuditEventRepository repository;
    private final JsonMapper json=JsonMapper.builder().build();
    public AuditEventService(AuditEventRepository repository) { this.repository=repository; }
    @Transactional public AuditEventResponse record(CreateAuditEventRequest r) {
        AuditEvent e=new AuditEvent();
        e.setEventType(r.eventType()); e.setResourceType(r.resourceType()); e.setResourceId(r.resourceId());
        e.setActorId(r.actorId()); e.setCorrelationId(r.correlationId()); e.setBeforeState(r.beforeState());
        e.setAfterState(r.afterState()); e.setMetadata(r.metadata()); e.setPaymentId(r.paymentId());
        if(e.getPaymentId()==null && Set.of("PAYMENT","PAYMENT_REQUEST").contains(r.resourceType())) {
            try { e.setPaymentId(Long.valueOf(r.resourceId())); } catch(NumberFormatException ignored) { /* Legacy nonnumeric resource ID. */ }
        }
        e.setOccurredAt(OffsetDateTime.now());
        return toResponse(repository.save(e));
    }
    @Transactional(readOnly=true) public List<AuditEventResponse> find(String resourceType,String resourceId,String correlationId) {
        List<AuditEvent> events;
        if(resourceType!=null&&resourceId!=null) events=repository.findByResourceTypeAndResourceIdOrderByOccurredAtDesc(resourceType,resourceId);
        else if(correlationId!=null) events=repository.findByCorrelationIdOrderByOccurredAtDesc(correlationId);
        else throw new IllegalArgumentException("Provide resourceType and resourceId, or correlationId");
        return events.stream().map(this::toResponse).toList();
    }
    @Transactional(readOnly=true) public List<AuditEventResponse> search(String resourceType,String resourceId,String correlationId,String eventType,String actor,
            OffsetDateTime from,OffsetDateTime to) {
        if((resourceType==null)!=(resourceId==null)) throw new IllegalArgumentException("Provide resourceType and resourceId together");
        if(from!=null&&to!=null&&from.isAfter(to)) throw new IllegalArgumentException("from must not be after to");
        return repository.findAll((root,query,cb)->{
            var p=new ArrayList<jakarta.persistence.criteria.Predicate>();
            if(resourceType!=null) { p.add(cb.equal(root.get("resourceType"),resourceType)); p.add(cb.equal(root.get("resourceId"),resourceId)); }
            if(correlationId!=null) p.add(cb.equal(root.get("correlationId"),correlationId));
            if(eventType!=null) p.add(cb.equal(root.get("eventType"),eventType));
            if(actor!=null) p.add(cb.equal(root.get("actorId"),actor));
            if(from!=null) p.add(cb.greaterThanOrEqualTo(root.get("occurredAt"),from));
            if(to!=null) p.add(cb.lessThanOrEqualTo(root.get("occurredAt"),to));
            return cb.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));
        },Sort.by("occurredAt").ascending().and(Sort.by("id"))).stream().map(this::toResponse).toList();
    }
    @Transactional(readOnly=true) public List<AuditEventResponse> byPayment(Long paymentId) {
        return repository.findPaymentTimeline(paymentId,paymentId.toString()).stream().map(this::toResponse).toList();
    }
    @Transactional(readOnly=true) public RiskDecisionSnapshot snapshot(Long paymentId) {
        var events=byPayment(paymentId);
        AuditEventResponse event=events.stream().filter(e->Set.of("RISK_ASSESSMENT_COMPLETED","RISK_ASSESSED").contains(e.eventType()))
                .reduce((older,newer)->newer).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"No completed risk assessment recorded for this payment"));
        try {
            JsonNode state=json.readTree(event.afterState());
            JsonNode metadata=json.readTree(event.metadata());
            if(state==null||metadata==null||!state.hasNonNull("riskScore")||!state.hasNonNull("riskBand")
                    ||!metadata.hasNonNull("configurationVersion")||!metadata.hasNonNull("configurationSnapshot")||!metadata.hasNonNull("scoreBreakdown"))
                throw new IllegalArgumentException("Incomplete snapshot");
            return new RiskDecisionSnapshot(paymentId,event.id(),event.occurredAt(),state,metadata.get("configurationVersion"),
                    metadata.get("configurationSnapshot"),metadata.get("scoreBreakdown"));
        } catch(Exception ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Latest risk event lacks a complete frozen state, configuration snapshot, or score breakdown");
        }
    }
    private AuditEventResponse toResponse(AuditEvent e) {
        return new AuditEventResponse(e.getId(),e.getEventType(),e.getResourceType(),e.getResourceId(),e.getActorId(),e.getCorrelationId(),
                e.getBeforeState(),e.getAfterState(),e.getMetadata(),e.getOccurredAt());
    }
}
