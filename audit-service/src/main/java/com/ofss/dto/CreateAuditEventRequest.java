package com.ofss.dto;
import jakarta.validation.constraints.*;
public record CreateAuditEventRequest(
        @NotBlank @Size(max=100) String eventType,@NotBlank @Size(max=100) String resourceType,
        @NotBlank @Size(max=100) String resourceId,@Size(max=100) String actorId,
        @Size(max=100) String correlationId,String beforeState,String afterState,String metadata,@Positive Long paymentId) {
    public CreateAuditEventRequest(String eventType,String resourceType,String resourceId,String actorId,String correlationId,
            String beforeState,String afterState,String metadata) {
        this(eventType,resourceType,resourceId,actorId,correlationId,beforeState,afterState,metadata,null);
    }
}
