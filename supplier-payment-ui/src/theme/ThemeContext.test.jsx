import {
  render,
  screen
} from "@testing-library/react";

import userEvent
  from "@testing-library/user-event";

import {
  beforeEach,
  describe,
  expect,
  it
} from "vitest";

import {
  ThemeProvider,
  useTheme
} from "./ThemeContext";

function TestComponent() {

  const {
    theme,
    toggleTheme,
    isDark
  } = useTheme();

  return (
    <div>

      <div data-testid="theme">
        {theme}
      </div>

      <div data-testid="dark">
        {
          String(
            isDark
          )
        }
      </div>

      <button
        onClick={
          toggleTheme
        }
      >
        Toggle Theme
      </button>

    </div>
  );
}

describe(
  "ThemeContext",
  () => {

    beforeEach(
      () => {

        localStorage.clear();

        document.documentElement.removeAttribute(
          "data-theme"
        );
      }
    );

    it(
      "switches between light and dark mode",
      async () => {

        localStorage.setItem(
          "appTheme",
          "light"
        );

        const user =
          userEvent.setup();

        render(
          <ThemeProvider>
            <TestComponent />
          </ThemeProvider>
        );

        expect(
          screen.getByTestId(
            "theme"
          )
        ).toHaveTextContent(
          "light"
        );

        await user.click(
          screen.getByRole(
            "button",
            {
              name:
                "Toggle Theme"
            }
          )
        );

        expect(
          screen.getByTestId(
            "theme"
          )
        ).toHaveTextContent(
          "dark"
        );

        expect(
          document.documentElement
        ).toHaveAttribute(
          "data-theme",
          "dark"
        );

        expect(
          localStorage.getItem(
            "appTheme"
          )
        ).toBe(
          "dark"
        );
      }
    );
  }
);