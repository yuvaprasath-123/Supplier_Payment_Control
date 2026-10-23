import puppeteer from "puppeteer";


const APP_URL =
  process.env.E2E_APP_URL ||
  "http://localhost:4173";

const USERNAME =
  process.env.E2E_USERNAME ||
  "requester2";

const PASSWORD =
  process.env.E2E_PASSWORD;

const PAYMENT_ID =
  process.env.E2E_PAYMENT_ID;


const MAX_STATUS_REFRESH_ATTEMPTS = 12;
const STATUS_REFRESH_DELAY_MS = 3000;


/*
 * ============================================================
 * BASIC VALIDATION
 * ============================================================
 */

if (!PASSWORD) {
  console.error(
    "ERROR: E2E_PASSWORD environment variable is required."
  );

  process.exit(1);
}


if (!PAYMENT_ID) {
  console.error(
    "ERROR: E2E_PAYMENT_ID environment variable is required."
  );

  process.exit(1);
}


/*
 * ============================================================
 * HELPERS
 * ============================================================
 */

function printSection(title) {

  console.log("");
  console.log("==========================================");
  console.log(title);
  console.log("==========================================");
}


async function delay(ms) {

  await new Promise(
    resolve =>
      setTimeout(
        resolve,
        ms
      )
  );
}


async function getBodyText(page) {

  return page.evaluate(
    () =>
      document.body?.innerText || ""
  );
}


async function waitForBodyText(
  page,
  text,
  timeout = 15000
) {

  await page.waitForFunction(
    expectedText =>
      document.body &&
      document.body.innerText.includes(
        expectedText
      ),
    {
      timeout
    },
    text
  );
}


async function getPaymentStatus(page) {

  return page.evaluate(
    () => {

      /*
       * Try to locate:
       *
       * <strong>Status:</strong>
       * <span class="status">PENDING_APPROVAL</span>
       */

      const strongElements =
        Array.from(
          document.querySelectorAll(
            "strong"
          )
        );

      const statusLabel =
        strongElements.find(
          element =>
            element.textContent
              ?.trim()
              .toLowerCase() ===
            "status:"
        );

      if (statusLabel) {

        const parent =
          statusLabel.parentElement;

        if (parent) {

          const statusElement =
            parent.querySelector(
              ".status"
            );

          if (
            statusElement
              ?.textContent
              ?.trim()
          ) {

            return statusElement
              .textContent
              .trim();
          }


          const fullText =
            parent.textContent
              ?.replace(
                statusLabel.textContent,
                ""
              )
              .trim();

          if (fullText) {
            return fullText;
          }
        }
      }


      /*
       * Fallback:
       * inspect known status elements.
       */

      const statusElements =
        Array.from(
          document.querySelectorAll(
            ".status"
          )
        );

      const knownStatuses = [
        "DRAFT",
        "PENDING_VALIDATION",
        "PENDING_APPROVAL",
        "ESCALATED",
        "MANUAL_REVIEW",
        "APPROVED",
        "PROCESSING",
        "COMPLETE",
        "REJECTED",
        "FAILED"
      ];


      for (
        const element
        of statusElements
      ) {

        const value =
          element.textContent
            ?.trim()
            ?.toUpperCase();

        if (
          knownStatuses.includes(
            value
          )
        ) {

          return value;
        }
      }


      return null;
    }
  );
}


async function authenticate(page) {

  printSection(
    "AUTHENTICATION"
  );


  await page.goto(
    `${APP_URL}/login`,
    {
      waitUntil:
        "networkidle2"
    }
  );


  await page.waitForSelector(
    "#username",
    {
      visible: true
    }
  );


  await page.waitForSelector(
    "#password",
    {
      visible: true
    }
  );


  await page.type(
    "#username",
    USERNAME
  );


  await page.type(
    "#password",
    PASSWORD
  );


  await page.click(
    'button[type="submit"]'
  );


  /*
   * Wait until login either reaches OTP page
   * or login produces an error.
   */

  await page.waitForFunction(
    () => {

      const path =
        window.location.pathname;

      const bodyText =
        document.body
          ?.innerText || "";

      return (
        path.includes(
          "verify-otp"
        ) ||
        bodyText.includes(
          "Verify OTP"
        ) ||
        bodyText.includes(
          "MFA"
        ) ||
        bodyText.includes(
          "Invalid"
        )
      );
    },
    {
      timeout: 15000
    }
  );


  const bodyText =
    await getBodyText(
      page
    );


  if (
    bodyText.includes(
      "Invalid username"
    ) ||
    bodyText.includes(
      "Invalid password"
    )
  ) {

    throw new Error(
      "Authentication failed before MFA."
    );
  }


  console.log(
    "PASS: MFA challenge started."
  );

  console.log("");
  console.log(
    ">>> Enter the current 6-digit OTP"
  );

  console.log(
    ">>> in the browser and click Verify OTP."
  );

  console.log("");


  /*
   * OTP is intentionally entered manually.
   *
   * Wait until OTP verification succeeds and
   * React Router moves away from verify-otp.
   */

  await page.waitForFunction(
    () => {

      const path =
        window.location.pathname;

      return (
        !path.includes(
          "verify-otp"
        ) &&
        !path.includes(
          "login"
        )
      );
    },
    {
      timeout: 180000
    }
  );


  console.log(
    "PASS: Authentication completed."
  );
}


async function openPaymentDetails(
  page
) {

  printSection(
    "TEST 2: Opening Payment Details..."
  );


  await page.goto(
    `${APP_URL}/payments/${PAYMENT_ID}`,
    {
      waitUntil:
        "networkidle2"
    }
  );


  await waitForBodyText(
    page,
    "Payment Details",
    15000
  );


  const bodyText =
    await getBodyText(
      page
    );


  if (
    !bodyText.includes(
      `Payment ID: ${PAYMENT_ID}`
    ) &&
    !bodyText.includes(
      PAYMENT_ID
    )
  ) {

    throw new Error(
      `Payment ${PAYMENT_ID} was not displayed on the Payment Details page.`
    );
  }


  console.log(
    "PASS: Payment Details page loaded."
  );
}


async function waitForWorkflowStatus(
  page
) {

  printSection(
    "TEST 3: Checking current payment status..."
  );


  let currentStatus =
    await getPaymentStatus(
      page
    );


  console.log(
    `Current payment status: ${currentStatus || "UNKNOWN"}`
  );


  /*
   * A newly submitted payment may briefly remain
   * PENDING_VALIDATION while backend risk evaluation
   * and approval routing are being created.
   *
   * Because requesters can no longer access the
   * risk refresh control, refresh the PAYMENT PAGE,
   * not the risk assessment.
   */

  for (
    let attempt = 1;
    attempt <=
      MAX_STATUS_REFRESH_ATTEMPTS;
    attempt += 1
  ) {

    if (
      currentStatus &&
      currentStatus !==
        "PENDING_VALIDATION"
    ) {

      break;
    }


    console.log(
      `Payment is still ${currentStatus || "UNKNOWN"}. ` +
      `Page refresh attempt ${attempt}/${MAX_STATUS_REFRESH_ATTEMPTS}...`
    );


    await delay(
      STATUS_REFRESH_DELAY_MS
    );


    await page.reload(
      {
        waitUntil:
          "networkidle2"
      }
    );


    await waitForBodyText(
      page,
      "Payment Details",
      15000
    );


    currentStatus =
      await getPaymentStatus(
        page
      );
  }


  if (!currentStatus) {

    throw new Error(
      "Unable to determine current payment status."
    );
  }


  console.log(
    `Final observed payment status: ${currentStatus}`
  );


  /*
   * For this E2E flow, a successful validation/risk
   * workflow should route the payment for approval.
   *
   * We also tolerate later lifecycle states so the test
   * can safely be rerun against an already-progressed
   * payment.
   */

  const validPostRiskStatuses = [
    "PENDING_APPROVAL",
    "ESCALATED",
    "MANUAL_REVIEW",
    "APPROVED",
    "PROCESSING",
    "COMPLETE",
    "REJECTED",
    "FAILED"
  ];


  if (
    currentStatus ===
      "PENDING_VALIDATION"
  ) {

    throw new Error(
      `Payment ${PAYMENT_ID} remained PENDING_VALIDATION after ${MAX_STATUS_REFRESH_ATTEMPTS} refresh attempts.`
    );
  }


  if (
    !validPostRiskStatuses.includes(
      currentStatus
    )
  ) {

    throw new Error(
      `Unexpected payment status after validation/risk processing: ${currentStatus}`
    );
  }


  console.log(
    "PASS: Payment progressed beyond PENDING_VALIDATION."
  );


  return currentStatus;
}


async function verifyRequesterCannotSeeRisk(
  page
) {

  printSection(
    "TEST 4: Verifying requester risk-data restriction..."
  );


  const result =
    await page.evaluate(
      () => {

        const text =
          document.body
            ?.innerText || "";


        const restrictedText = [
          "Risk Assessment Details",
          "Risk Factor Breakdown",
          "Refresh Risk Assessment",
          "Risk Approval Level",
          "Configuration Version ID",
          "Configured Weight",
          "Contribution"
        ];


        const visibleRestrictedText =
          restrictedText.filter(
            item =>
              text.includes(
                item
              )
          );


        /*
         * Payment summary previously exposed these
         * values as well, so validate them separately.
         */

        const exposedSummaryFields = [
  "Risk Score:",
  "Risk Band:"
].filter(
  item =>
    text.includes(
      item
    )
);


        const buttons =
          Array.from(
            document.querySelectorAll(
              "button"
            )
          ).map(
            button =>
              button.textContent
                ?.trim()
          );


        const refreshRiskButtonVisible =
          buttons.some(
            text =>
              text ===
              "Refresh Risk Assessment" ||
              text ===
              "Refreshing..."
          );


        return {
          visibleRestrictedText,
          exposedSummaryFields,
          refreshRiskButtonVisible
        };
      }
    );


  if (
    result
      .visibleRestrictedText
      .length > 0
  ) {

    throw new Error(
      "Requester can see restricted risk information: " +
      result
        .visibleRestrictedText
        .join(", ")
    );
  }


  if (
    result
      .exposedSummaryFields
      .length > 0
  ) {

    throw new Error(
      "Requester can see restricted payment risk fields: " +
      result
        .exposedSummaryFields
        .join(", ")
    );
  }


  if (
    result
      .refreshRiskButtonVisible
  ) {

    throw new Error(
      "Requester can see the Refresh Risk Assessment button."
    );
  }


  console.log(
    "PASS: Risk Assessment Details are hidden."
  );

  console.log(
    "PASS: Risk Score is hidden."
  );

  console.log(
    "PASS: Risk Band is hidden."
  );

  console.log(
    "PASS: Required Approval Level is hidden."
  );

  console.log(
    "PASS: Risk Factor Breakdown is hidden."
  );

  console.log(
    "PASS: Refresh Risk Assessment button is hidden."
  );
}


async function verifyNormalPaymentInformationVisible(
  page
) {

  printSection(
    "TEST 5: Verifying requester payment visibility..."
  );


  const bodyText =
    await getBodyText(
      page
    );


  const requiredVisibleFields = [
    "Payment ID:",
    "Invoice ID:",
    "Supplier ID:",
    "Requester ID:",
    "Payment Amount:",
    "Status:",
    "Timeline"
  ];


  const missingFields =
    requiredVisibleFields.filter(
      field =>
        !bodyText.includes(
          field
        )
    );


  if (
    missingFields.length > 0
  ) {

    throw new Error(
      "Requester is missing expected payment fields: " +
      missingFields.join(", ")
    );
  }


  console.log(
    "PASS: Normal payment details remain visible to requester."
  );

  console.log(
    "PASS: Payment timeline remains visible to requester."
  );
}


/*
 * ============================================================
 * MAIN TEST
 * ============================================================
 */

const browser =
  await puppeteer.launch(
    {
      headless: false,
      slowMo: 100,
      defaultViewport: null,
      args: [
        "--start-maximized"
      ]
    }
  );


let page;


try {

  printSection(
    "RISK ASSESSMENT ACCESS CONTROL E2E TEST"
  );

  console.log(
    `Payment ID: ${PAYMENT_ID}`
  );


  page =
    await browser.newPage();


  /*
   * Useful browser diagnostics without exposing
   * authentication credentials.
   */

  page.on(
    "pageerror",
    error => {

      console.log(
        `Browser page error: ${error.message}`
      );
    }
  );


  /*
   * TEST 1
   * Authenticate requester with MFA.
   */

  await authenticate(
    page
  );


  /*
   * TEST 2
   * Open Payment Details.
   */

  await openPaymentDetails(
    page
  );


  /*
   * TEST 3
   * Verify backend workflow progressed beyond
   * PENDING_VALIDATION.
   */

  const finalStatus =
    await waitForWorkflowStatus(
      page
    );


  /*
   * TEST 4
   * Verify internal risk information is NOT
   * visible to PAYMENT_REQUESTER.
   */

  await verifyRequesterCannotSeeRisk(
    page
  );


  /*
   * TEST 5
   * Ensure hiding risk information did not
   * accidentally hide normal requester data.
   */

  await verifyNormalPaymentInformationVisible(
    page
  );


  printSection(
    "RISK ASSESSMENT ACCESS CONTROL E2E TEST PASSED"
  );


  console.log(
    `Payment ID: ${PAYMENT_ID}`
  );

  console.log(
    `Payment Status: ${finalStatus}`
  );

  console.log(
    "PAYMENT_REQUESTER risk visibility: BLOCKED"
  );

  console.log(
    "Risk Assessment Details: HIDDEN"
  );

  console.log(
    "Risk Score / Risk Band: HIDDEN"
  );

  console.log(
    "Required Approval Level: HIDDEN"
  );

  console.log(
    "Risk Factor Breakdown: HIDDEN"
  );

  console.log(
    "Refresh Risk Assessment: HIDDEN"
  );

  console.log(
    "Requester payment details: VISIBLE"
  );


} catch (error) {

  printSection(
    "RISK ASSESSMENT ACCESS CONTROL E2E TEST FAILED"
  );


  console.error(
    error
  );


  if (page) {

    try {

      await page.screenshot(
        {
          path:
            "e2e-puppeteer/risk-assessment-flow-failure.png",
          fullPage: true
        }
      );


      console.log(
        "Failure screenshot saved to:"
      );

      console.log(
        "e2e-puppeteer/risk-assessment-flow-failure.png"
      );

    } catch (
      screenshotError
    ) {

      console.error(
        "Unable to save failure screenshot:",
        screenshotError.message
      );
    }
  }


  process.exitCode = 1;


} finally {

  try {

    await browser.close();

  } catch (error) {

    console.log(
      "Browser cleanup warning:",
      error.message
    );
  }
}