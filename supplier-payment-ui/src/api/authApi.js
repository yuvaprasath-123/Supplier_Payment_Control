import {
  apiRequest
} from "./apiClient";

const BASE =
  "/security-api/api/v1/auth";

export function login(
  username,
  password
) {

  return apiRequest(
    `${BASE}/login`,
    {
      method: "POST",

      body: {
        username,
        password
      }
    }
  );
}

export function verifyOtp(
  challengeId,
  otp
) {

  return apiRequest(
    `${BASE}/verify-otp`,
    {
      method: "POST",

      body: {
        challengeId,
        otp
      }
    }
  );
}

export function getCurrentUser(
  token
) {

  return apiRequest(
    `${BASE}/me`,
    {
      token
    }
  );
}