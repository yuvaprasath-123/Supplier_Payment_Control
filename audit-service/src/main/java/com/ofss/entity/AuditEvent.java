package com.ofss.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@org.hibernate.annotations.Immutable
@Table(name = "AUD_AUDIT_EVENTS", indexes = {
        @Index(name = "IX_AUD_EVENT_RESOURCE", columnList = "RESOURCE_TYPE,RESOURCE_ID"),
        @Index(name = "IX_AUD_EVENT_OCCURRED", columnList = "OCCURRED_AT")
})
public class AuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "audit_event_seq")
    @SequenceGenerator(name = "audit_event_seq", sequenceName = "AUD_AUDIT_EVENTS_SEQ", allocationSize = 50)
    @Column(name = "AUDIT_EVENT_ID")
    private Long id;

    @Column(name = "PAYMENT_ID") private Long paymentId;

    @Column(name = "EVENT_TYPE", nullable = false, length = 100) private String eventType;
    @Column(name = "RESOURCE_TYPE", nullable = false, length = 100) private String resourceType;
    @Column(name = "RESOURCE_ID", nullable = false, length = 100) private String resourceId;
    @Column(name = "ACTOR_ID", length = 100) private String actorId;
    @Column(name = "CORRELATION_ID", length = 100) private String correlationId;
    @Lob @Column(name = "BEFORE_STATE") private String beforeState;
    @Lob @Column(name = "AFTER_STATE") private String afterState;
    @Lob @Column(name = "METADATA") private String metadata;
    @Column(name = "OCCURRED_AT", nullable = false) private OffsetDateTime occurredAt;

    @PrePersist void prePersist() { if (occurredAt == null) occurredAt = OffsetDateTime.now(); }
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getPaymentId() { return paymentId; } public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
    public String getEventType() { return eventType; } public void setEventType(String eventType) { this.eventType = eventType; }
    public String getResourceType() { return resourceType; } public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public String getResourceId() { return resourceId; } public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public String getActorId() { return actorId; } public void setActorId(String actorId) { this.actorId = actorId; }
    public String getCorrelationId() { return correlationId; } public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public String getBeforeState() { return beforeState; } public void setBeforeState(String beforeState) { this.beforeState = beforeState; }
    public String getAfterState() { return afterState; } public void setAfterState(String afterState) { this.afterState = afterState; }
    public String getMetadata() { return metadata; } public void setMetadata(String metadata) { this.metadata = metadata; }
    public OffsetDateTime getOccurredAt() { return occurredAt; } public void setOccurredAt(OffsetDateTime occurredAt) { this.occurredAt = occurredAt; }
}
