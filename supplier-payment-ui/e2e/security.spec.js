import {
  test,
  expect
} from "@playwright/test";

test(
  "supplier input does not execute script",
  async ({ page }) => {

    let dialogOpened =
      false;

    page.on(
      "dialog",
      async dialog => {

        dialogOpened =
          true;

        await dialog.dismiss();
      }
    );


    // LOGIN

    await page.goto(
      "/login"
    );

    await page
      .getByLabel(
        "Username"
      )
      .fill(
        "requester1"
      );

    await page
      .getByLabel(
        "Password"
      )
      .fill(
        "requester@123"
      );

    await page
      .getByRole(
        "button",
        {
          name:
            "Login"
        }
      )
      .click();

    await expect(
      page
    ).toHaveURL(
      /\/$/,
      {
        timeout:
          10000
      }
    );

    await expect(
      page.getByRole(
        "heading",
        {
          name:
            /dashboard/i
        }
      )
    ).toBeVisible();


    // OPEN SUPPLIERS

    await page
      .getByRole(
        "link",
        {
          name:
            "Suppliers",
          exact:
            true
        }
      )
      .click();

    await expect(
      page
    ).toHaveURL(
      /\/suppliers/
    );

    await expect(
      page.getByRole(
        "heading",
        {
          name:
            "Suppliers"
        }
      )
    ).toBeVisible();


    // XSS PAYLOAD

    const payload =
      `<script>alert("xss-${Date.now()}")</script>`;

    await page
      .getByPlaceholder(
        "Supplier name"
      )
      .fill(
        payload
      );

    await page
      .getByRole(
        "button",
        {
          name:
            "Create Supplier"
        }
      )
      .click();


    // VERIFY PAYLOAD IS RENDERED AS TEXT

    const createdSupplier =
      page.getByRole(
        "cell",
        {
          name:
            payload,
          exact:
            true
        }
      );

    await expect(
      createdSupplier
    ).toHaveCount(
      1
    );

    await expect(
      createdSupplier
    ).toBeVisible();


    // VERIFY SCRIPT DID NOT EXECUTE

    expect(
      dialogOpened
    ).toBe(
      false
    );
  }
);