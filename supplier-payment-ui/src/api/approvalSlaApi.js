import {
  apiRequest
} from "./apiClient";

const BASE_URL = "/approval-api/api/v1/payments";

/**
 * API Name: Get Payment SLA Approval Tracker
 * Method: GET
 * Backend: Approval Workflow Service (8089)
 * Endpoint: /api/v1/payments/{paymentId}/sla-tracker
 *
 * Uses the existing Vite /approval-api proxy.
 */
export function getPaymentSlaTracker(
  token,
  paymentId
) {
  return apiRequest(
    `${BASE_URL}/${encodeURIComponent(paymentId)}/sla-tracker`,
    {
      token
    }
  );
}