import {
  test,
  expect
} from "@playwright/test";

test(
  "complete requester business flow",
  async ({ page }) => {

    const unique =
      Date.now();

    const supplierName =
      `FULL-E2E-SUP-${unique}`;

    const invoiceNumber =
      `FULL-E2E-INV-${unique}`;

    const amount =
      "54321.67";


    // =========================================================
    // 1. LOGIN
    // =========================================================

    await page.goto(
      "/login"
    );

    await expect(
      page.getByRole(
        "button",
        {
          name:
            "Login"
        }
      )
    ).toBeVisible();

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

    const loginResponsePromise =
      page.waitForResponse(
        response =>
          response
            .url()
            .includes(
              "/api/v1/auth/login"
            )
          &&
          response
            .request()
            .method() ===
            "POST"
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

    const loginResponse =
      await loginResponsePromise;

    expect(
      loginResponse.status()
    ).toBe(
      200
    );

    const loginBody =
      await loginResponse.json();

    expect(
      loginBody.accessToken
    ).toBeTruthy();

    expect(
      loginBody.roles
    ).toContain(
      "PAYMENT_REQUESTER"
    );

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


    // =========================================================
    // 2. OPEN SUPPLIERS
    // =========================================================

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


    // =========================================================
    // 3. CREATE SUPPLIER
    // =========================================================

    await page
      .getByPlaceholder(
        "Supplier name"
      )
      .fill(
        supplierName
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

    await expect(
      page.getByText(
        supplierName,
        {
          exact:
            true
        }
      )
    ).toBeVisible({
      timeout:
        10000
    });


    // =========================================================
    // 4. TEST SUPPLIER ID SORTING
    // =========================================================

    const supplierSortButton =
      page.getByRole(
        "button",
        {
          name:
            /Sort supplier ID/
        }
      );

    await expect(
      supplierSortButton
    ).toBeVisible();

    await supplierSortButton
      .click();

    await expect(
      page.getByRole(
        "button",
        {
          name:
            "Sort supplier ID ascending"
        }
      )
    ).toBeVisible();

    await page
      .getByRole(
        "button",
        {
          name:
            "Sort supplier ID ascending"
        }
      )
      .click();

    await expect(
      page.getByRole(
        "button",
        {
          name:
            "Sort supplier ID descending"
        }
      )
    ).toBeVisible();


    // =========================================================
    // 5. OPEN INVOICES
    // =========================================================

    await page
      .getByRole(
        "link",
        {
          name:
            "Invoices",
          exact:
            true
        }
      )
      .click();

    await expect(
      page
    ).toHaveURL(
      /\/invoices/
    );

    await expect(
      page.getByRole(
        "heading",
        {
          name:
            "Invoices"
        }
      )
    ).toBeVisible();


    // =========================================================
    // 6. CREATE INVOICE
    // =========================================================

    const invoiceForm =
      page.locator(
        "form.form-row"
      );

    const supplierSelect =
      invoiceForm.locator(
        "select"
      );

    await expect(
      supplierSelect
    ).toBeVisible();

    await supplierSelect
      .selectOption({
        label:
          supplierName
      });

    await page
      .getByPlaceholder(
        "Invoice number"
      )
      .fill(
        invoiceNumber
      );

    await page
      .getByPlaceholder(
        "Invoice amount"
      )
      .fill(
        amount
      );

    const invoiceDateInputs =
      invoiceForm.locator(
        'input[type="date"]'
      );

    await expect(
      invoiceDateInputs
    ).toHaveCount(
      2
    );

    await invoiceDateInputs
      .nth(0)
      .fill(
        "2026-10-13"
      );

    await invoiceDateInputs
      .nth(1)
      .fill(
        "2026-11-13"
      );

    await page
      .getByRole(
        "button",
        {
          name:
            "Create Invoice"
        }
      )
      .click();

    await expect(
      page.getByText(
        invoiceNumber,
        {
          exact:
            true
        }
      )
    ).toBeVisible({
      timeout:
        10000
    });


    // =========================================================
    // 7. TEST INVOICE ID SORTING
    // =========================================================

    const invoiceSortButton =
      page.getByRole(
        "button",
        {
          name:
            /Sort invoice ID/
        }
      );

    await expect(
      invoiceSortButton
    ).toBeVisible();

    await invoiceSortButton
      .click();

    await expect(
      page.getByRole(
        "button",
        {
          name:
            "Sort invoice ID ascending"
        }
      )
    ).toBeVisible();

    await page
      .getByRole(
        "button",
        {
          name:
            "Sort invoice ID ascending"
        }
      )
      .click();

    await expect(
      page.getByRole(
        "button",
        {
          name:
            "Sort invoice ID descending"
        }
      )
    ).toBeVisible();


    // =========================================================
    // 8. OPEN PAYMENTS
    // =========================================================

    await page
      .getByRole(
        "link",
        {
          name:
            "Payments",
          exact:
            true
        }
      )
      .click();

    await expect(
      page
    ).toHaveURL(
      /\/payments/
    );

    await expect(
      page.getByRole(
        "heading",
        {
          name:
            "Payment Requests"
        }
      )
    ).toBeVisible();


    // =========================================================
    // 9. CREATE PAYMENT
    // =========================================================

    const paymentForm =
      page.locator(
        "form.form-row"
      );

    const invoiceSelect =
      paymentForm.locator(
        "select"
      );

    await expect(
      invoiceSelect
    ).toBeVisible();

    await invoiceSelect
      .selectOption({
        label:
          `${invoiceNumber} - ${supplierName}`
      });

    const paymentAmountInput =
      paymentForm.locator(
        'input[type="number"]'
      );

    await expect(
      paymentAmountInput
    ).toHaveValue(
      amount
    );

    await page
      .getByRole(
        "button",
        {
          name:
            "Create Payment"
        }
      )
      .click();


    // =========================================================
    // 10. VERIFY PAYMENT CREATED AS DRAFT
    // =========================================================

    const paymentRow =
      page
        .locator(
          "tbody tr"
        )
        .filter({
          hasText:
            amount
        })
        .filter({
          hasText:
            "DRAFT"
        });

    await expect(
      paymentRow
    ).toHaveCount(
      1
    );

    await expect(
      paymentRow
    ).toContainText(
      "DRAFT"
    );


    // =========================================================
    // 11. TEST PAYMENT ID SORTING
    // =========================================================

    const paymentSortButton =
      page.getByRole(
        "button",
        {
          name:
            /Sort payment ID/
        }
      );

    await expect(
      paymentSortButton
    ).toBeVisible();

    await paymentSortButton
      .click();

    await expect(
      page.getByRole(
        "button",
        {
          name:
            "Sort payment ID ascending"
        }
      )
    ).toBeVisible();

    await page
      .getByRole(
        "button",
        {
          name:
            "Sort payment ID ascending"
        }
      )
      .click();

    await expect(
      page.getByRole(
        "button",
        {
          name:
            "Sort payment ID descending"
        }
      )
    ).toBeVisible();


    // =========================================================
    // 12. OPEN PAYMENT DETAILS
    // =========================================================

    await paymentRow
      .getByRole(
        "link",
        {
          name:
            "View"
        }
      )
      .click();

    await expect(
      page
    ).toHaveURL(
      /\/payments\/\d+/
    );

    await expect(
      page.getByRole(
        "heading",
        {
          name:
            "Payment Details"
        }
      )
    ).toBeVisible();

    await expect(
      page
        .getByText(
          "DRAFT"
        )
        .first()
    ).toBeVisible();


    // =========================================================
    // 13. SUBMIT PAYMENT FOR VALIDATION
    // =========================================================

    await page
      .getByRole(
        "button",
        {
          name:
            "Submit Payment"
        }
      )
      .click();

    await expect(
      page
        .getByText(
          "PENDING_VALIDATION"
        )
        .first()
    ).toBeVisible({
      timeout:
        15000
    });


    // =========================================================
    // 14. VERIFY PAYMENT TIMELINE
    // =========================================================

    await expect(
      page.getByText(
        "PENDING_VALIDATION"
      ).first()
    ).toBeVisible();


    // =========================================================
    // 15. LOGOUT
    // =========================================================

    await page
      .getByRole(
        "button",
        {
          name:
            "Logout"
        }
      )
      .click();

    await expect(
      page
    ).toHaveURL(
      /\/login/
    );

    await expect(
      page.getByRole(
        "button",
        {
          name:
            "Login"
        }
      )
    ).toBeVisible();
  }
);