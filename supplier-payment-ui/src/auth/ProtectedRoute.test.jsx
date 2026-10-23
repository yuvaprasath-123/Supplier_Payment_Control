import {
  render,
  screen
} from "@testing-library/react";

import {
  MemoryRouter,
  Route,
  Routes
} from "react-router";

import {
  beforeEach,
  describe,
  expect,
  it,
  vi
} from "vitest";

import ProtectedRoute
  from "./ProtectedRoute";

import {
  useAuth
} from "./AuthContext";

vi.mock(
  "./AuthContext",
  () => ({
    useAuth:
      vi.fn()
  })
);

function renderProtectedRoute(
  allowedRoles = [
    "PAYMENT_REQUESTER"
  ]
) {

  return render(
    <MemoryRouter
      initialEntries={[
        "/protected"
      ]}
    >

      <Routes>

        <Route
          path="/login"
          element={
            <div>
              Login Page
            </div>
          }
        />

        <Route
          path="/"
          element={
            <div>
              Home Page
            </div>
          }
        />

        <Route
          path="/protected"
          element={
            <ProtectedRoute
              allowedRoles={
                allowedRoles
              }
            >
              <div>
                Protected Content
              </div>
            </ProtectedRoute>
          }
        />

      </Routes>

    </MemoryRouter>
  );
}

describe(
  "ProtectedRoute",
  () => {

    beforeEach(
      () => {

        vi.clearAllMocks();
      }
    );

    it(
      "shows loading state while authentication is loading",
      () => {

        useAuth.mockReturnValue({
          user:
            null,
          loading:
            true,
          isAuthenticated:
            false
        });

        renderProtectedRoute();

        expect(
          screen.getByText(
            "Loading..."
          )
        ).toBeInTheDocument();
      }
    );

    it(
      "redirects unauthenticated users to login",
      () => {

        useAuth.mockReturnValue({
          user:
            null,
          loading:
            false,
          isAuthenticated:
            false
        });

        renderProtectedRoute();

        expect(
          screen.getByText(
            "Login Page"
          )
        ).toBeInTheDocument();
      }
    );

    it(
      "allows an authenticated user with the required role",
      () => {

        useAuth.mockReturnValue({
          user: {
            roles: [
              "PAYMENT_REQUESTER"
            ]
          },
          loading:
            false,
          isAuthenticated:
            true
        });

        renderProtectedRoute();

        expect(
          screen.getByText(
            "Protected Content"
          )
        ).toBeInTheDocument();
      }
    );

    it(
      "redirects authenticated users with the wrong role",
      () => {

        useAuth.mockReturnValue({
          user: {
            roles: [
              "FINANCE_APPROVER"
            ]
          },
          loading:
            false,
          isAuthenticated:
            true
        });

        renderProtectedRoute();

        expect(
          screen.getByText(
            "Home Page"
          )
        ).toBeInTheDocument();
      }
    );

    it(
      "allows authenticated users when no role restriction exists",
      () => {

        useAuth.mockReturnValue({
          user: {
            roles: [
              "FINANCE_APPROVER"
            ]
          },
          loading:
            false,
          isAuthenticated:
            true
        });

        renderProtectedRoute(
          []
        );

        expect(
          screen.getByText(
            "Protected Content"
          )
        ).toBeInTheDocument();
      }
    );
  }
);