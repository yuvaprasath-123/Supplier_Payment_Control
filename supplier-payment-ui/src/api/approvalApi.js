
import { apiRequest } from "./apiClient";

const BASE_URL =
  "/approval-api/api/v1";

export function getApprovalTasks(
  token,
  {
    paymentId,
    assignedApproverId,
    status
  } = {}
) {
  const params = new URLSearchParams();

  if (paymentId != null) {
    params.set("paymentId", String(paymentId));
  }

  if (assignedApproverId != null) {
    params.set(
      "assignedApproverId",
      String(assignedApproverId)
    );
  }

  if (status) {
    params.set("status", status);
  }

  const query = params.toString();

  return apiRequest(
    `${BASE_URL}/approval-tasks${query ? `?${query}` : ""}`,
    { token }
  );
}

export function getApprovalTask(
  token,
  approvalTaskId
) {
  return apiRequest(
    `${BASE_URL}/approval-tasks/${approvalTaskId}`,
    { token }
  );
}

export function approveTask(
  token,
  approvalTaskId,
  decision
) {
  return apiRequest(
    `${BASE_URL}/approval-tasks/${approvalTaskId}/approve`,
    {
      token,
      method: "POST",
      body: decision
    }
  );
}

export function rejectTask(
  token,
  approvalTaskId,
  decision
) {
  return apiRequest(
    `${BASE_URL}/approval-tasks/${approvalTaskId}/reject`,
    {
      token,
      method: "POST",
      body: decision
    }
  );
}

export function overrideAndApproveTask(
  token,
  approvalTaskId,
  decision
) {
  return apiRequest(
    `${BASE_URL}/approval-tasks/${approvalTaskId}/override-and-approve`,
    {
      token,
      method: "POST",
      body: decision
    }
  );
}

export function getApprovalHistory(
  token,
  paymentId
) {
  return apiRequest(
    `${BASE_URL}/approval-tasks/payment/${paymentId}/history`,
    { token }
  );
}

export function getApprovalTaskSla(
  token,
  approvalTaskId
) {
  return apiRequest(
    `${BASE_URL}/approval-tasks/${approvalTaskId}/sla`,
    { token }
  );
}

export function getApprovalRoute(
  token,
  paymentId
) {
  return apiRequest(
    `${BASE_URL}/approval-routes/payment/${paymentId}`,
    { token }
  );
}

export function getApprovalExplanation(
  token,
  paymentId
) {
  return apiRequest(
    `${BASE_URL}/approval-routes/payment/${paymentId}/decision-explanation`,
    { token }
  );
}

export function getApprovalWorkflow(
  token,
  workflowId
) {
  return apiRequest(
    `${BASE_URL}/approval-workflows/${workflowId}`,
    { token }
  );
}
