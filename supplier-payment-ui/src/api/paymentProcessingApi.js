
import { apiRequest } from "./apiClient";

const BASE_URL =
  "/processing-api/api/v1/payment-processing";

export function getPaymentProcessingStatus(
  token,
  paymentId
) {
  return apiRequest(
    `${BASE_URL}/${encodeURIComponent(paymentId)}`,
    { token }
  );
}
