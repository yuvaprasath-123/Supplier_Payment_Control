import {
  apiRequest
} from "./apiClient";

const BASE_URL =
  "/supplier-api/api/v1/suppliers";

export function getSuppliers(
  token,
  status = null
) {

  let url =
    BASE_URL;

  if (status) {

    const params =
      new URLSearchParams({
        status
      });

    url =
      `${BASE_URL}?${params.toString()}`;
  }

  return apiRequest(
    url,
    {
      token
    }
  );
}

export function getSupplier(
  token,
  supplierId
) {

  return apiRequest(
    `${BASE_URL}/${supplierId}`,
    {
      token
    }
  );
}

export function createSupplier(
  token,
  supplierName,
  status
) {

  return apiRequest(
    BASE_URL,
    {
      token,

      method:
        "POST",

      body: {
        supplierName,
        status
      }
    }
  );
}

export function updateSupplier(
  token,
  supplierId,
  supplierName
) {

  return apiRequest(
    `${BASE_URL}/${supplierId}`,
    {
      token,

      method:
        "PUT",

      body: {
        supplierName
      }
    }
  );
}

export function updateSupplierStatus(
  token,
  supplierId,
  status
) {

  return apiRequest(
    `${BASE_URL}/${supplierId}/status`,
    {
      token,

      method:
        "PATCH",

      body: {
        status
      }
    }
  );
}

export function getSupplierStatusHistory(
  token,
  supplierId
) {

  return apiRequest(
    `${BASE_URL}/${supplierId}/status-history`,
    {
      token
    }
  );
}