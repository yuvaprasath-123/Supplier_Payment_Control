package com.ofss.dto;
import java.time.OffsetDateTime;
import tools.jackson.databind.JsonNode;
public record RiskDecisionSnapshot(Long paymentId,Long sourceEventId,OffsetDateTime recordedAt,JsonNode riskState,
        JsonNode configurationVersion,JsonNode configurationSnapshot,JsonNode scoreBreakdown) {}
