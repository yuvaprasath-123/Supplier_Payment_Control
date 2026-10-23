import {
  render,
  screen,
  waitFor
} from "@testing-library/react";

import userEvent
  from "@testing-library/user-event";

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

import LoginPage
  from "./LoginPage";

import {
  useAuth
} from "../auth/AuthContext";

import {
  useTheme
} from "../theme/ThemeContext";

vi.mock(
  "../auth/AuthContext",
  () => ({
    useAuth:
      vi.fn()
  })
);

vi.mock(
  "../theme/ThemeContext",
  () => ({
    useTheme:
      vi.fn()
  })
);

vi.mock(
  "react-i18next",
  () => ({
    useTranslation:
      () => ({
        t: (
          key
        ) => {

          const translations = {
            appName:
              "Supplier Payment Control",
            "login.subtitle":
              "Sign in to continue",
            "login.username":
              "Username",
            "login.password":
              "Password",
            "login.button":
              "Login",
            "login.loggingIn":
              "Logging in...",
            "login.error":
              "Login failed"
          };

          return (
            translations[
              key
            ] ||
            key
          );
        }
      })
  })
);

vi.mock(
  "../components/LanguageSelector",
  () => ({
    default:
      () => (
        <select
          aria-label="Language"
        >
          <option>
            English
          </option>
        </select>
      )
  })
);

describe(
  "LoginPage",
  () => {

    const loginMock =
      vi.fn();

    const toggleThemeMock =
      vi.fn();

    beforeEach(
      () => {

        vi.clearAllMocks();

        useAuth.mockReturnValue({
          login:
            loginMock
        });

        useTheme.mockReturnValue({
          theme:
            "dark",
          toggleTheme:
            toggleThemeMock
        });
      }
    );

    function renderPage() {

      return render(
        <MemoryRouter
          initialEntries={[
            "/login"
          ]}
        >

          <Routes>

            <Route
              path="/login"
              element={
                <LoginPage />
              }
            />

            <Route
              path="/"
              element={
                <div>
                  Dashboard
                </div>
              }
            />

          </Routes>

        </MemoryRouter>
      );
    }

    it(
      "renders login form",
      () => {

        renderPage();

        expect(
          screen.getByText(
            "Supplier Payment Control"
          )
        ).toBeInTheDocument();

        expect(
          screen.getByLabelText(
            "Username"
          )
        ).toBeInTheDocument();

        expect(
          screen.getByLabelText(
            "Password"
          )
        ).toBeInTheDocument();

        expect(
          screen.getByRole(
            "button",
            {
              name:
                "Login"
            }
          )
        ).toBeInTheDocument();
      }
    );

    it(
      "logs in and navigates to dashboard",
      async () => {

        loginMock.mockResolvedValue({
          username:
            "requester1"
        });

        const user =
          userEvent.setup();

        renderPage();

        await user.type(
          screen.getByLabelText(
            "Username"
          ),
          "requester1"
        );

        await user.type(
          screen.getByLabelText(
            "Password"
          ),
          "password"
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
              loginMock
            ).toHaveBeenCalledWith(
              "requester1",
              "password"
            );
          }
        );

        expect(
          await screen.findByText(
            "Dashboard"
          )
        ).toBeInTheDocument();
      }
    );

    it(
      "shows login error",
      async () => {

        loginMock.mockRejectedValue(
          new Error(
            "Invalid credentials"
          )
        );

        const user =
          userEvent.setup();

        renderPage();

        await user.type(
          screen.getByLabelText(
            "Username"
          ),
          "wrong"
        );

        await user.type(
          screen.getByLabelText(
            "Password"
          ),
          "wrong"
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

        expect(
          await screen.findByText(
            "Invalid credentials"
          )
        ).toBeInTheDocument();
      }
    );

    it(
      "allows user to switch theme",
      async () => {

        const user =
          userEvent.setup();

        renderPage();

        await user.click(
          screen.getByRole(
            "button",
            {
              name:
                "Switch to light mode"
            }
          )
        );

        expect(
          toggleThemeMock
        ).toHaveBeenCalledTimes(
          1
        );
      }
    );
  }
);