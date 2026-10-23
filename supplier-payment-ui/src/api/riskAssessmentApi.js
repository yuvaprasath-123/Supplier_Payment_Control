
import {
  apiRequest
} from "./apiClient";

const BASE_URL =
  "/risk-api/api/v1/risk-assessments";

// API Name: Get Risk Assessment by Payment ID
// Method: GET
// URL: /api/v1/risk-assessments/payment/{paymentId}
export function getRiskAssessmentByPaymentId(
  token,
  paymentId
) {
  return apiRequest(
    `${BASE_URL}/payment/${encodeURIComponent(paymentId)}`,
    {
      token
    }
  );
}

// API Name: Get Risk Assessment by Assessment ID
// Method: GET
// URL: /api/v1/risk-assessments/{assessmentId}
export function getRiskAssessmentById(
  token,
  assessmentId
) {
  return apiRequest(
    `${BASE_URL}/${encodeURIComponent(assessmentId)}`,
    {
      token
    }
  );
}

// API Name: Get Risk Assessment Factors
// Method: GET
// URL: /api/v1/risk-assessments/{assessmentId}/factors
export function getRiskAssessmentFactors(
  token,
  assessmentId
) {
  return apiRequest(
    `${BASE_URL}/${encodeURIComponent(assessmentId)}/factors`,
    {
      token
    }
  );
}
