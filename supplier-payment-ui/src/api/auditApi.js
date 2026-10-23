
import { apiRequest } from "./apiClient";

const BASE_URL = "/audit-api/api/v1/audit-events";

export function getPaymentAuditTimeline(token, paymentId) {
  return apiRequest(
    `${BASE_URL}/payment/${encodeURIComponent(paymentId)}`,
    { token }
  );
}

export function getPaymentDecisionHistory(token, paymentId) {
  return apiRequest(
    `${BASE_URL}/payment/${encodeURIComponent(paymentId)}/decision-history`,
    { token }
  );
}

export function getPaymentRiskHistory(token, paymentId) {
  return apiRequest(
    `${BASE_URL}/payment/${encodeURIComponent(paymentId)}/risk-history`,
    { token }
  );
}

export function getRiskDecisionSnapshot(token, paymentId) {
  return apiRequest(
    `${BASE_URL}/payment/${encodeURIComponent(paymentId)}/risk-decision-snapshot`,
    { token }
  );
}

export function searchAuditEvents(token, filters = {}) {
  const params = new URLSearchParams();

  for (const [key, value] of Object.entries(filters)) {
    if (value !== null && value !== undefined && value !== "") {
      params.set(key, String(value));
    }
  }

  const query = params.toString();

  return apiRequest(
    query ? `${BASE_URL}?${query}` : BASE_URL,
    { token }
  );
}
