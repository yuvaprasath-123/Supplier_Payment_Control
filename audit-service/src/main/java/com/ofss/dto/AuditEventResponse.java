package com.ofss.dto;

import java.time.OffsetDateTime;

public record AuditEventResponse(Long id, String eventType, String resourceType, String resourceId,
                                 String actorId, String correlationId, String beforeState, String afterState,
                                 String metadata, OffsetDateTime occurredAt) { }
