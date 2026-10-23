import {
  render,
  screen,
  waitFor
} from "@testing-library/react";

import userEvent
  from "@testing-library/user-event";

import {
  beforeEach,
  describe,
  expect,
  it,
  vi
} from "vitest";

import {
  AuthProvider,
  useAuth
} from "./AuthContext";

import {
  apiRequest
} from "../api/apiClient";

vi.mock(
  "../api/apiClient",
  () => ({
    apiRequest:
      vi.fn()
  })
);

function TestComponent() {

  const {
    token,
    user,
    loading,
    isAuthenticated,
    login,
    logout,
    hasRole
  } = useAuth();

  async function handleLogin() {

    await login(
      "requester1",
      "password"
    );
  }

  return (
    <div>

      <div data-testid="loading">
        {String(loading)}
      </div>

      <div data-testid="token">
        {
          token ||
          "no-token"
        }
      </div>

      <div data-testid="user">
        {
          user?.username ||
          "no-user"
        }
      </div>

      <div data-testid="authenticated">
        {
          String(
            isAuthenticated
          )
        }
      </div>

      <div data-testid="requester-role">
        {
          String(
            hasRole(
              "PAYMENT_REQUESTER"
            )
          )
        }
      </div>

      <button
        type="button"
        onClick={
          handleLogin
        }
      >
        Login
      </button>

      <button
        type="button"
        onClick={
          logout
        }
      >
        Logout
      </button>

    </div>
  );
}

describe(
  "AuthContext",
  () => {

    beforeEach(
      () => {

        vi.clearAllMocks();

        sessionStorage.clear();
      }
    );

    it(
      "starts unauthenticated",
      async () => {

        render(
          <AuthProvider>
            <TestComponent />
          </AuthProvider>
        );

        await waitFor(
          () => {

            expect(
              screen.getByTestId(
                "loading"
              )
            ).toHaveTextContent(
              "false"
            );
          }
        );

        expect(
          screen.getByTestId(
            "token"
          )
        ).toHaveTextContent(
          "no-token"
        );

        expect(
          screen.getByTestId(
            "user"
          )
        ).toHaveTextContent(
          "no-user"
        );

        expect(
          screen.getByTestId(
            "authenticated"
          )
        ).toHaveTextContent(
          "false"
        );
      }
    );

    it(
      "logs in and loads current user",
      async () => {

        apiRequest.mockImplementation(
          async (
            url
          ) => {

            if (
              url ===
              "/security-api/api/v1/auth/login"
            ) {

              return {
                accessToken:
                  "test-jwt-token"
              };
            }

            if (
              url ===
              "/security-api/api/v1/auth/me"
            ) {

              return {
                userId:
                  1,

                username:
                  "requester1",

                roles: [
                  "PAYMENT_REQUESTER"
                ]
              };
            }

            throw new Error(
              `Unexpected API request: ${url}`
            );
          }
        );

        const user =
          userEvent.setup();

        render(
          <AuthProvider>
            <TestComponent />
          </AuthProvider>
        );

        await waitFor(
          () => {

            expect(
              screen.getByTestId(
                "loading"
              )
            ).toHaveTextContent(
              "false"
            );
          }
        );

        await user.click(
          screen.getByRole(
            "button",
            {
              name:
                "Login"
            }
          )
        );

        await waitFor(
          () => {

            expect(
              screen.getByTestId(
                "user"
              )
            ).toHaveTextContent(
              "requester1"
            );
          }
        );

        expect(
          screen.getByTestId(
            "token"
          )
        ).toHaveTextContent(
          "test-jwt-token"
        );

        expect(
          screen.getByTestId(
            "authenticated"
          )
        ).toHaveTextContent(
          "true"
        );

        expect(
          screen.getByTestId(
            "requester-role"
          )
        ).toHaveTextContent(
          "true"
        );

        expect(
          sessionStorage.getItem(
            "accessToken"
          )
        ).toBe(
          "test-jwt-token"
        );

        expect(
          apiRequest
        ).toHaveBeenCalledWith(
          "/security-api/api/v1/auth/login",
          {
            method:
              "POST",

            body: {
              username:
                "requester1",

              password:
                "password"
            }
          }
        );

        expect(
          apiRequest
        ).toHaveBeenCalledWith(
          "/security-api/api/v1/auth/me",
          {
            token:
              "test-jwt-token"
          }
        );
      }
    );

    it(
      "restores existing session",
      async () => {

        sessionStorage.setItem(
          "accessToken",
          "stored-token"
        );

        apiRequest.mockImplementation(
          async (
            url
          ) => {

            if (
              url ===
              "/security-api/api/v1/auth/me"
            ) {

              return {
                userId:
                  1,

                username:
                  "requester1",

                roles: [
                  "PAYMENT_REQUESTER"
                ]
              };
            }

            throw new Error(
              `Unexpected API request: ${url}`
            );
          }
        );

        render(
          <AuthProvider>
            <TestComponent />
          </AuthProvider>
        );

        await waitFor(
          () => {

            expect(
              screen.getByTestId(
                "user"
              )
            ).toHaveTextContent(
              "requester1"
            );
          }
        );

        expect(
          screen.getByTestId(
            "authenticated"
          )
        ).toHaveTextContent(
          "true"
        );

        expect(
          screen.getByTestId(
            "requester-role"
          )
        ).toHaveTextContent(
          "true"
        );

        expect(
          apiRequest
        ).toHaveBeenCalledWith(
          "/security-api/api/v1/auth/me",
          {
            token:
              "stored-token"
          }
        );
      }
    );

    it(
      "clears invalid stored token",
      async () => {

        sessionStorage.setItem(
          "accessToken",
          "invalid-token"
        );

        apiRequest.mockRejectedValue(
          new Error(
            "Unauthorized"
          )
        );

        render(
          <AuthProvider>
            <TestComponent />
          </AuthProvider>
        );

        await waitFor(
          () => {

            expect(
              screen.getByTestId(
                "loading"
              )
            ).toHaveTextContent(
              "false"
            );
          }
        );

        expect(
          sessionStorage.getItem(
            "accessToken"
          )
        ).toBeNull();

        expect(
          screen.getByTestId(
            "token"
          )
        ).toHaveTextContent(
          "no-token"
        );

        expect(
          screen.getByTestId(
            "authenticated"
          )
        ).toHaveTextContent(
          "false"
        );
      }
    );

    it(
      "logs out",
      async () => {

        sessionStorage.setItem(
          "accessToken",
          "stored-token"
        );

        apiRequest.mockImplementation(
          async (
            url
          ) => {

            if (
              url ===
              "/security-api/api/v1/auth/me"
            ) {

              return {
                userId:
                  1,

                username:
                  "requester1",

                roles: [
                  "PAYMENT_REQUESTER"
                ]
              };
            }

            throw new Error(
              `Unexpected API request: ${url}`
            );
          }
        );

        const user =
          userEvent.setup();

        render(
          <AuthProvider>
            <TestComponent />
          </AuthProvider>
        );

        await waitFor(
          () => {

            expect(
              screen.getByTestId(
                "authenticated"
              )
            ).toHaveTextContent(
              "true"
            );
          }
        );

        await user.click(
          screen.getByRole(
            "button",
            {
              name:
                "Logout"
            }
          )
        );

        expect(
          sessionStorage.getItem(
            "accessToken"
          )
        ).toBeNull();

        expect(
          screen.getByTestId(
            "token"
          )
        ).toHaveTextContent(
          "no-token"
        );

        expect(
          screen.getByTestId(
            "user"
          )
        ).toHaveTextContent(
          "no-user"
        );

        expect(
          screen.getByTestId(
            "authenticated"
          )
        ).toHaveTextContent(
          "false"
        );
      }
    );
  }
);