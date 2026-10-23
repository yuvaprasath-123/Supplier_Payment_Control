import {
  test,
  expect
} from "@playwright/test";

import AxeBuilder
  from "@axe-core/playwright";

test(
  "login page has no serious or critical accessibility violations",
  async ({ page }) => {

    await page.goto(
      "/login"
    );

    const accessibilityScanResults =
      await new AxeBuilder({
        page
      })
        .withTags([
          "wcag2a",
          "wcag2aa"
        ])
        .analyze();

    const seriousOrCriticalViolations =
      accessibilityScanResults
        .violations
        .filter(
          violation =>
            violation.impact === "serious"
            ||
            violation.impact === "critical"
        );

    console.log(
      "Accessibility violations:",
      seriousOrCriticalViolations
    );

    expect(
      seriousOrCriticalViolations
    ).toEqual([]);
  }
);