import {
  apiRequest
} from "./apiClient";

const BASE_URL =
  "/payment-api/api/v1/payment-requests";

export function getPayments(
  token,
  supplierId = null,
  status = null
) {

  const params =
    new URLSearchParams();

  if (supplierId) {

    params.set(
      "supplierId",
      String(
        supplierId
      )
    );
  }

  if (status) {

    params.set(
      "status",
      status
    );
  }

  const query =
    params.toString();

  const url =
    query
      ? `${BASE_URL}?${query}`
      : BASE_URL;

  return apiRequest(
    url,
    {
      token
    }
  );
}

export function getPayment(
  token,
  paymentId
) {

  return apiRequest(
    `${BASE_URL}/${paymentId}`,
    {
      token
    }
  );
}

export function createPayment(
  token,
  invoiceId,
  paymentAmount
) {

  return apiRequest(
    BASE_URL,
    {
      token,

      method:
        "POST",

      body: {
        invoiceId:
          Number(
            invoiceId
          ),

        paymentAmount:
          Number(
            paymentAmount
          )
      }
    }
  );
}

export function updatePayment(
  token,
  paymentId,
  paymentAmount
) {

  return apiRequest(
    `${BASE_URL}/${paymentId}`,
    {
      token,

      method:
        "PUT",

      body: {
        paymentAmount:
          Number(
            paymentAmount
          )
      }
    }
  );
}

export function submitPayment(
  token,
  paymentId
) {

  return apiRequest(
    `${BASE_URL}/${paymentId}/submit`,
    {
      token,

      method:
        "POST"
    }
  );
}

export function getPaymentTimeline(
  token,
  paymentId
) {

  return apiRequest(
    `${BASE_URL}/${paymentId}/timeline`,
    {
      token
    }
  );
}