
import {
  apiRequest
} from "./apiClient";

const BASE_URL =
  "/configuration-api/api/v1/configuration/versions";

const CONFIGURATION_BASE_URL =
  "/configuration-api/api/v1/configuration";

// API Name: List Configuration Versions
// Method: GET
// URL: /api/v1/configuration/versions
export function getConfigurationVersions(token) {
  return apiRequest(BASE_URL, {
    token
  });
}

// API Name: Create Configuration Version
// Method: POST
// URL: /api/v1/configuration/versions
export function createConfigurationVersion(
  token,
  requestBody
) {
  return apiRequest(BASE_URL, {
    token,
    method: "POST",
    body: requestBody
  });
}

// API Name: Get Configuration Version Details
// Method: GET
// URL: /api/v1/configuration/versions/{versionId}
export function getConfigurationVersionDetails(
  token,
  versionId
) {
  return apiRequest(
    `${BASE_URL}/${encodeURIComponent(versionId)}`,
    {
      token
    }
  );
}

// API Name: Get Active Configuration
// Method: GET
// URL: /api/v1/configuration/active
export function getActiveConfiguration(token) {
  return apiRequest(
    `${CONFIGURATION_BASE_URL}/active`,
    {
      token
    }
  );
}

// API Name: Get Publication Readiness
// Method: GET
// URL: /api/v1/configuration/versions/{versionId}/publication-readiness
export function getPublicationReadiness(
  token,
  versionId
) {
  return apiRequest(
    `${BASE_URL}/${encodeURIComponent(versionId)}/publication-readiness`,
    {
      token
    }
  );
}

// API Name: Get Risk Rules
// Method: GET
// URL: /api/v1/configuration/versions/{versionId}/risk-rules
export function getRiskRules(
  token,
  versionId
) {
  return apiRequest(
    `${BASE_URL}/${encodeURIComponent(versionId)}/risk-rules`,
    {
      token
    }
  );
}

// API Name: Update Draft Risk Rule
// Method: PUT
// URL: /api/v1/configuration/versions/{versionId}/risk-rules/{ruleCode}
export function updateDraftRiskRule(
  token,
  versionId,
  ruleCode,
  requestBody
) {
  return apiRequest(
    `${BASE_URL}/${encodeURIComponent(versionId)}/risk-rules/${encodeURIComponent(ruleCode)}`,
    {
      token,
      method: "PUT",
      body: requestBody
    }
  );
}

// API Name: Get Risk Bands
// Method: GET
// URL: /api/v1/configuration/versions/{versionId}/risk-bands
export function getRiskBands(
  token,
  versionId
) {
  return apiRequest(
    `${BASE_URL}/${encodeURIComponent(versionId)}/risk-bands`,
    {
      token
    }
  );
}

// API Name: Update Risk Band
// Method: PUT
// URL: /api/v1/configuration/versions/{versionId}/risk-bands/{bandCode}
export function updateRiskBand(
  token,
  versionId,
  bandCode,
  requestBody
) {
  return apiRequest(
    `${BASE_URL}/${encodeURIComponent(versionId)}/risk-bands/${encodeURIComponent(bandCode)}`,
    {
      token,
      method: "PUT",
      body: requestBody
    }
  );
}

// API Name: Get Approval Thresholds
// Method: GET
// URL: /api/v1/configuration/versions/{versionId}/approval-thresholds
export function getApprovalThresholds(
  token,
  versionId
) {
  return apiRequest(
    `${BASE_URL}/${encodeURIComponent(versionId)}/approval-thresholds`,
    {
      token
    }
  );
}

// API Name: Update Approval Threshold
// Method: PUT
// URL: /api/v1/configuration/versions/{versionId}/approval-thresholds/{approvalLevel}
export function updateApprovalThreshold(
  token,
  versionId,
  approvalLevel,
  requestBody
) {
  return apiRequest(
    `${BASE_URL}/${encodeURIComponent(versionId)}/approval-thresholds/${encodeURIComponent(approvalLevel)}`,
    {
      token,
      method: "PUT",
      body: requestBody
    }
  );
}

// API Name: Get Approval SLAs
// Method: GET
// URL: /api/v1/configuration/versions/{versionId}/approval-slas
export function getApprovalSlas(
  token,
  versionId
) {
  return apiRequest(
    `${BASE_URL}/${encodeURIComponent(versionId)}/approval-slas`,
    {
      token
    }
  );
}

// API Name: Update Draft Approval SLA
// Method: PUT
// URL: /api/v1/configuration/versions/{versionId}/approval-slas/{approvalLevel}
export function updateDraftApprovalSla(
  token,
  versionId,
  approvalLevel,
  requestBody
) {
  return apiRequest(
    `${BASE_URL}/${encodeURIComponent(versionId)}/approval-slas/${encodeURIComponent(approvalLevel)}`,
    {
      token,
      method: "PUT",
      body: requestBody
    }
  );
}
