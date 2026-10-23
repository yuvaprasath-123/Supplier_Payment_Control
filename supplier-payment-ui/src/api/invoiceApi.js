import {
  apiRequest
} from "./apiClient";

const BASE_URL =
  "/supplier-api/api/v1/invoices";

export function getInvoices(
  token,
  supplierId = null
) {

  let url =
    BASE_URL;

  if (supplierId) {

    url +=
      `?supplierId=${supplierId}`;
  }

  return apiRequest(
    url,
    {
      token
    }
  );
}

export function getInvoice(
  token,
  invoiceId
) {

  return apiRequest(
    `${BASE_URL}/${invoiceId}`,
    {
      token
    }
  );
}

export function createInvoice(
  token,
  invoiceData
) {

  return apiRequest(
    BASE_URL,
    {
      token,

      method:
        "POST",

      body:
        invoiceData
    }
  );
}

export function updateInvoice(
  token,
  invoiceId,
  invoiceData
) {

  return apiRequest(
    `${BASE_URL}/${invoiceId}`,
    {
      token,

      method:
        "PUT",

      body:
        invoiceData
    }
  );
}

export function checkExactDuplicate(
  token,
  supplierId,
  invoiceNumber
) {

  const params =
    new URLSearchParams({
      supplierId:
        String(
          supplierId
        ),

      invoiceNumber
    });

  return apiRequest(
    `${BASE_URL}/duplicate/exact?${params.toString()}`,
    {
      token
    }
  );
}

export function checkNearDuplicate(
  token,
  supplierId,
  invoiceAmount
) {

  const params =
    new URLSearchParams({
      supplierId:
        String(
          supplierId
        ),

      invoiceAmount:
        String(
          invoiceAmount
        )
    });

  return apiRequest(
    `${BASE_URL}/duplicate/near?${params.toString()}`,
    {
      token
    }
  );
}