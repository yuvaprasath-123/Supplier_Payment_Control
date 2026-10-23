import puppeteer from 'puppeteer';

const APP_URL =
    process.env.E2E_APP_URL ||
    'http://localhost:4173';

const username =
    process.env.E2E_USERNAME;

const password =
    process.env.E2E_PASSWORD;

const supplierName =
    process.env.E2E_SUPPLIER_NAME;

if (
    !username ||
    !password ||
    !supplierName
) {

    console.error(
        'Missing E2E_USERNAME, E2E_PASSWORD, or E2E_SUPPLIER_NAME.'
    );

    console.error(
        'Use a synthetic PAYMENT_REQUESTER account and an existing ACTIVE supplier.'
    );

    process.exit(1);
}

const runId =
    Date.now();

const invoiceNumber =
    `UI-E2E-INV-${runId}`;

const invoiceAmount =
    '54321.67';


/*
 * --------------------------------------------------
 * DATE HELPERS
 * --------------------------------------------------
 *
 * Application/test-readable format:
 * DD-MM-YYYY
 *
 * Native HTML date input format:
 * YYYY-MM-DD
 */

function pad2(
    value
) {

    return String(
        value
    ).padStart(
        2,
        '0'
    );
}

function toDisplayDate(
    date
) {

    return [
        pad2(
            date.getDate()
        ),

        pad2(
            date.getMonth() + 1
        ),

        date.getFullYear()
    ].join('-');
}

function toIsoDate(
    ddMmYyyy
) {

    const parts =
        ddMmYyyy.split('-');

    if (
        parts.length !== 3
    ) {

        throw new Error(
            `Invalid DD-MM-YYYY date: ${ddMmYyyy}`
        );
    }

    const [
        day,
        month,
        year
    ] = parts;

    if (
        !/^\d{2}$/.test(day) ||
        !/^\d{2}$/.test(month) ||
        !/^\d{4}$/.test(year)
    ) {

        throw new Error(
            `Invalid DD-MM-YYYY date: ${ddMmYyyy}`
        );
    }

    return `${year}-${month}-${day}`;
}


/*
 * Automatically use:
 *
 * invoice date = today
 * due date     = today + 30 days
 *
 * Display format remains DD-MM-YYYY.
 */

const invoiceDateObject =
    new Date();

const dueDateObject =
    new Date(
        invoiceDateObject
    );

dueDateObject.setDate(
    dueDateObject.getDate() + 30
);

const invoiceDateDisplay =
    toDisplayDate(
        invoiceDateObject
    );

const dueDateDisplay =
    toDisplayDate(
        dueDateObject
    );

const invoiceDate =
    toIsoDate(
        invoiceDateDisplay
    );

const dueDate =
    toIsoDate(
        dueDateDisplay
    );


const browser =
    await puppeteer.launch({
        headless: false,
        slowMo: 100,
        defaultViewport: null,
        args: [
            '--start-maximized'
        ]
    });

const page =
    await browser.newPage();


async function waitForPath(
    pathname,
    timeout = 15000
) {

    await page.waitForFunction(
        expectedPath =>
            window.location.pathname ===
            expectedPath,
        {
            timeout,
            polling: 250
        },
        pathname
    );
}


async function getBodyText() {

    return page.$eval(
        'body',
        element =>
            element.innerText
    );
}


async function authenticate() {

    console.log('');
    console.log(
        '=========================================='
    );

    console.log(
        'AUTHENTICATION'
    );

    console.log(
        '=========================================='
    );

    await page.goto(
        `${APP_URL}/login`,
        {
            waitUntil: 'networkidle2',
            timeout: 30000
        }
    );

    await page.waitForSelector(
        '#username',
        {
            visible: true
        }
    );

    await page.waitForSelector(
        '#password',
        {
            visible: true
        }
    );

    await page.type(
        '#username',
        username
    );

    await page.type(
        '#password',
        password
    );

    await page.click(
        'button[type="submit"]'
    );

    await waitForPath(
        '/verify-otp'
    );

    await page.waitForSelector(
        '#otp',
        {
            visible: true
        }
    );

    console.log(
        'PASS: MFA challenge started.'
    );

    console.log('');
    console.log(
        '>>> Enter the current 6-digit OTP'
    );

    console.log(
        '>>> in the opened browser and click Verify OTP.'
    );

    console.log('');

    await waitForPath(
        '/',
        180000
    );

    await page.waitForFunction(
        () =>
            Boolean(
                sessionStorage.getItem(
                    'accessToken'
                )
            ),
        {
            timeout: 15000
        }
    );

    console.log(
        'PASS: Authentication completed.'
    );
}


try {

    console.log('');
    console.log(
        '=========================================='
    );

    console.log(
        'PAYMENT REQUEST FULL E2E TEST'
    );

    console.log(
        '=========================================='
    );

    console.log(
        `Existing supplier: ${supplierName}`
    );

    console.log(
        `New invoice: ${invoiceNumber}`
    );

    console.log(
        `Invoice amount: ${invoiceAmount}`
    );

    console.log(
        `Invoice date display: ${invoiceDateDisplay}`
    );

    console.log(
        `Invoice date internal: ${invoiceDate}`
    );

    console.log(
        `Due date display: ${dueDateDisplay}`
    );

    console.log(
        `Due date internal: ${dueDate}`
    );


    /*
     * --------------------------------------------------
     * TEST 1
     * Login
     * --------------------------------------------------
     */

    await authenticate();


    /*
     * --------------------------------------------------
     * TEST 2
     * Open Invoices page
     * --------------------------------------------------
     */

    console.log('');
    console.log(
        'TEST 2: Opening Invoices page...'
    );

    await page.goto(
        `${APP_URL}/invoices`,
        {
            waitUntil: 'networkidle2',
            timeout: 30000
        }
    );

    await page.waitForFunction(
        () => {

            const heading =
                document.querySelector(
                    'h1'
                );

            return (
                heading &&
                heading.textContent
                    .trim() ===
                'Invoices'
            );
        },
        {
            timeout: 15000
        }
    );

    console.log(
        'PASS: Invoices page loaded.'
    );


    /*
     * --------------------------------------------------
     * TEST 3
     * Select existing ACTIVE supplier
     * --------------------------------------------------
     */

    console.log('');
    console.log(
        'TEST 3: Selecting existing supplier...'
    );

    await page.waitForSelector(
        'form.card.form-row select',
        {
            visible: true
        }
    );

    const supplierOption =
        await page.$$eval(
            'form.card.form-row select option',
            (
                options,
                expectedSupplierName
            ) => {

                const matching =
                    options.find(
                        option =>
                            option.textContent
                                .trim() ===
                            expectedSupplierName
                    );

                if (!matching) {
                    return null;
                }

                return {
                    value:
                        matching.value,

                    text:
                        matching.textContent
                            .trim()
                };
            },
            supplierName
        );

    if (
        !supplierOption
    ) {

        throw new Error(
            `Existing ACTIVE supplier "${supplierName}" was not found on the Invoices page.`
        );
    }

    await page.select(
        'form.card.form-row select',
        supplierOption.value
    );

    console.log(
        `PASS: Existing supplier selected: ${supplierOption.text}`
    );

    console.log(
        `Supplier ID: ${supplierOption.value}`
    );


    /*
     * --------------------------------------------------
     * TEST 4
     * Fill NEW invoice
     * --------------------------------------------------
     */

    console.log('');
    console.log(
        'TEST 4: Creating a new invoice for the existing supplier...'
    );

    const invoiceNumberSelector =
        'input[placeholder="Invoice number"]';

    const invoiceAmountSelector =
        'input[placeholder="Invoice amount"]';

    await page.waitForSelector(
        invoiceNumberSelector,
        {
            visible: true
        }
    );

    await page.type(
        invoiceNumberSelector,
        invoiceNumber
    );

    await page.type(
        invoiceAmountSelector,
        invoiceAmount
    );


    /*
     * Important:
     *
     * Do NOT use page.type() for input[type="date"].
     *
     * Native date inputs require ISO values internally.
     */

    await page.evaluate(
        (
            invoiceDateValue,
            dueDateValue
        ) => {

            const dateInputs =
                document.querySelectorAll(
                    'form.card.form-row input[type="date"]'
                );

            if (
                dateInputs.length !== 2
            ) {

                throw new Error(
                    `Expected 2 date inputs but found ${dateInputs.length}.`
                );
            }

            function setNativeDateValue(
                input,
                value
            ) {

                const nativeSetter =
                    Object.getOwnPropertyDescriptor(
                        HTMLInputElement.prototype,
                        'value'
                    ).set;

                nativeSetter.call(
                    input,
                    value
                );

                input.dispatchEvent(
                    new Event(
                        'input',
                        {
                            bubbles: true
                        }
                    )
                );

                input.dispatchEvent(
                    new Event(
                        'change',
                        {
                            bubbles: true
                        }
                    )
                );
            }

            setNativeDateValue(
                dateInputs[0],
                invoiceDateValue
            );

            setNativeDateValue(
                dateInputs[1],
                dueDateValue
            );

        },
        invoiceDate,
        dueDate
    );


    /*
     * Verify browser values before submit.
     */

    const enteredDates =
        await page.$$eval(
            'form.card.form-row input[type="date"]',
            inputs =>
                inputs.map(
                    input =>
                        input.value
                )
        );

    if (
        enteredDates.length !== 2
    ) {

        throw new Error(
            'Unable to verify invoice date fields.'
        );
    }

    if (
        enteredDates[0] !==
        invoiceDate
    ) {

        throw new Error(
            `Invoice date mismatch. Expected ${invoiceDate}, browser has ${enteredDates[0]}.`
        );
    }

    if (
        enteredDates[1] !==
        dueDate
    ) {

        throw new Error(
            `Due date mismatch. Expected ${dueDate}, browser has ${enteredDates[1]}.`
        );
    }

    console.log(
        'PASS: Date fields entered correctly.'
    );

    console.log(
        'Dates actually stored in browser:',
        {
            invoiceDate:
                enteredDates[0],

            dueDate:
                enteredDates[1]
        }
    );

    console.log(
        'Dates in DD-MM-YYYY:',
        {
            invoiceDate:
                invoiceDateDisplay,

            dueDate:
                dueDateDisplay
        }
    );


    /*
     * Print expected request.
     */

    console.log(
        'Invoice payload expected:',
        {
            supplierId:
                Number(
                    supplierOption.value
                ),

            invoiceNumber,

            invoiceAmount:
                Number(
                    invoiceAmount
                ),

            invoiceDate,

            dueDate
        }
    );


    /*
     * Capture real invoice API response.
     */

    const invoiceResponsePromise =
        page.waitForResponse(
            response =>
                response.url().includes(
                    '/supplier-api/api/v1/invoices'
                ) &&
                response.request().method() ===
                'POST',
            {
                timeout: 30000
            }
        );

    await page.click(
        'form.card.form-row button[type="submit"]'
    );

    const invoiceResponse =
        await invoiceResponsePromise;

    if (
        !invoiceResponse.ok()
    ) {

        const responseText =
            await invoiceResponse.text();

        throw new Error(
            `Invoice creation failed with HTTP ${invoiceResponse.status()}: ${responseText}`
        );
    }

    const createdInvoice =
        await invoiceResponse.json();

    if (
        !createdInvoice.invoiceId
    ) {

        throw new Error(
            'Invoice creation succeeded but invoiceId was not returned.'
        );
    }

    const newInvoiceId =
        String(
            createdInvoice.invoiceId
        );

    console.log(
        'PASS: New invoice created.'
    );

    console.log(
        `Invoice ID: ${newInvoiceId}`
    );

    console.log(
        `Invoice Number: ${invoiceNumber}`
    );


    /*
     * --------------------------------------------------
     * TEST 5
     * Verify invoice appears in UI
     * --------------------------------------------------
     */

    console.log('');
    console.log(
        'TEST 5: Verifying new invoice in invoice table...'
    );

    await page.waitForFunction(
        expectedInvoiceNumber => {

            const rows =
                Array.from(
                    document.querySelectorAll(
                        'table tbody tr'
                    )
                );

            return rows.some(
                row =>
                    row.innerText.includes(
                        expectedInvoiceNumber
                    )
            );
        },
        {
            timeout: 15000
        },
        invoiceNumber
    );

    console.log(
        'PASS: New invoice appears in invoice table.'
    );


    /*
     * --------------------------------------------------
     * TEST 6
     * Open Payments page
     * --------------------------------------------------
     */

    console.log('');
    console.log(
        'TEST 6: Opening Payments page...'
    );

    await page.goto(
        `${APP_URL}/payments`,
        {
            waitUntil: 'networkidle2',
            timeout: 30000
        }
    );

    await page.waitForFunction(
        () => {

            const heading =
                document.querySelector(
                    'h1'
                );

            return (
                heading &&
                heading.textContent
                    .trim() ===
                'Payment Requests'
            );
        },
        {
            timeout: 15000
        }
    );

    console.log(
        'PASS: Payment Requests page loaded.'
    );


    /*
     * --------------------------------------------------
     * TEST 7
     * Select newly-created invoice
     * --------------------------------------------------
     */

    console.log('');
    console.log(
        'TEST 7: Selecting the newly-created invoice...'
    );

    await page.waitForSelector(
        'form.card.form-row select',
        {
            visible: true
        }
    );

    const paymentInvoiceOption =
        await page.$$eval(
            'form.card.form-row select option',
            (
                options,
                expectedInvoiceNumber
            ) => {

                const matching =
                    options.find(
                        option =>
                            option.textContent
                                .includes(
                                    expectedInvoiceNumber
                                )
                    );

                if (!matching) {
                    return null;
                }

                return {
                    value:
                        matching.value,

                    text:
                        matching.textContent
                            .trim()
                };
            },
            invoiceNumber
        );

    if (
        !paymentInvoiceOption
    ) {

        throw new Error(
            `New invoice "${invoiceNumber}" was not available on Payments page.`
        );
    }

    if (
        paymentInvoiceOption.value !==
        newInvoiceId
    ) {

        throw new Error(
            `Expected invoice ID ${newInvoiceId} but Payments page showed ${paymentInvoiceOption.value}.`
        );
    }

    await page.select(
        'form.card.form-row select',
        paymentInvoiceOption.value
    );

    console.log(
        `PASS: New invoice selected: ${paymentInvoiceOption.text}`
    );


    /*
     * Payment amount should auto-fill.
     */

    await page.waitForFunction(
        expectedAmount => {

            const input =
                document.querySelector(
                    'form.card.form-row input[type="number"]'
                );

            if (!input) {
                return false;
            }

            return (
                Number(input.value) ===
                Number(expectedAmount)
            );
        },
        {
            timeout: 10000
        },
        invoiceAmount
    );

    const autoFilledAmount =
        await page.$eval(
            'form.card.form-row input[type="number"]',
            input =>
                input.value
        );

    console.log(
        `PASS: Payment amount auto-filled: ${autoFilledAmount}`
    );


    /*
     * --------------------------------------------------
     * TEST 8
     * Create payment
     * --------------------------------------------------
     */

    console.log('');
    console.log(
        'TEST 8: Creating new payment request...'
    );

    const paymentResponsePromise =
        page.waitForResponse(
            response =>
                response.url().includes(
                    '/payment-api/api/v1/payment-requests'
                ) &&
                response.request().method() ===
                'POST' &&
                !response.url().includes(
                    '/submit'
                ),
            {
                timeout: 30000
            }
        );

    await page.click(
        'form.card.form-row button[type="submit"]'
    );

    const paymentResponse =
        await paymentResponsePromise;

    if (
        !paymentResponse.ok()
    ) {

        const responseText =
            await paymentResponse.text();

        throw new Error(
            `Payment creation failed with HTTP ${paymentResponse.status()}: ${responseText}`
        );
    }

    const createdPayment =
        await paymentResponse.json();

    if (
        !createdPayment.paymentId
    ) {

        throw new Error(
            'Payment creation succeeded but paymentId was not returned.'
        );
    }

    const paymentId =
        String(
            createdPayment.paymentId
        );

    if (
        createdPayment.status !==
        'DRAFT'
    ) {

        throw new Error(
            `Expected DRAFT but backend returned ${createdPayment.status}.`
        );
    }

    console.log(
        'PASS: New payment created.'
    );

    console.log(
        `Payment ID: ${paymentId}`
    );

    console.log(
        'PASS: Initial status is DRAFT.'
    );


    /*
     * --------------------------------------------------
     * TEST 9
     * Open Payment Details
     * --------------------------------------------------
     */

    console.log('');
    console.log(
        'TEST 9: Opening Payment Details...'
    );

    await page.goto(
        `${APP_URL}/payments/${paymentId}`,
        {
            waitUntil: 'networkidle2',
            timeout: 30000
        }
    );

    await page.waitForFunction(
        expectedPaymentId =>
            document.body.innerText.includes(
                `Payment ID: ${expectedPaymentId}`
            ),
        {
            timeout: 15000
        },
        paymentId
    );

    let bodyText =
        await getBodyText();

    if (
        !bodyText.includes(
            'Status: DRAFT'
        )
    ) {

        throw new Error(
            'Payment Details page does not show DRAFT status.'
        );
    }

    console.log(
        'PASS: Payment Details confirms DRAFT.'
    );


    /*
     * --------------------------------------------------
     * TEST 10
     * Submit payment
     * --------------------------------------------------
     */

    console.log('');
    console.log(
        'TEST 10: Submitting payment...'
    );

    const submitButton =
        await page.evaluateHandle(
            () =>
                Array.from(
                    document.querySelectorAll(
                        'button'
                    )
                ).find(
                    button =>
                        button.textContent
                            .trim() ===
                        'Submit Payment'
                )
        );

    const submitElement =
        submitButton.asElement();

    if (
        !submitElement
    ) {

        throw new Error(
            'Submit Payment button was not found.'
        );
    }

    await submitElement.click();

    const expectedSuccessPath =
        `/payments/${paymentId}/submission-success`;

    await waitForPath(
        expectedSuccessPath,
        30000
    );

    await page.waitForFunction(
        () =>
            document.body.innerText.includes(
                'Payment Submitted Successfully'
            ),
        {
            timeout: 15000
        }
    );

    bodyText =
        await getBodyText();

    if (
        !bodyText.includes(
            'PENDING_VALIDATION'
        )
    ) {

        throw new Error(
            'Expected PENDING_VALIDATION after submission.'
        );
    }

    console.log(
        'PASS: Payment submitted successfully.'
    );

    console.log(
        'PASS: Status is PENDING_VALIDATION.'
    );


    /*
     * --------------------------------------------------
     * Success screenshot
     * --------------------------------------------------
     */

    await page.screenshot({
        path:
            'e2e-puppeteer/payment-request-flow-success.png',
        fullPage: true
    });

    console.log('');
    console.log(
        '=========================================='
    );

    console.log(
        'PAYMENT REQUEST FULL E2E TEST PASSED'
    );

    console.log(
        '=========================================='
    );

    console.log(
        `Supplier: ${supplierName}`
    );

    console.log(
        `Invoice Number: ${invoiceNumber}`
    );

    console.log(
        `Invoice ID: ${newInvoiceId}`
    );

    console.log(
        `Invoice Date: ${invoiceDateDisplay}`
    );

    console.log(
        `Due Date: ${dueDateDisplay}`
    );

    console.log(
        `Payment ID: ${paymentId}`
    );

    console.log(
        'Final expected state: PENDING_VALIDATION'
    );

} catch (error) {

    console.error('');
    console.error(
        '=========================================='
    );

    console.error(
        'PAYMENT REQUEST FULL E2E TEST FAILED'
    );

    console.error(
        '=========================================='
    );

    console.error(
        error
    );

    try {

        await page.screenshot({
            path:
                'e2e-puppeteer/payment-request-flow-failure.png',
            fullPage: true
        });

        console.error(
            'Failure screenshot saved to:'
        );

        console.error(
            'e2e-puppeteer/payment-request-flow-failure.png'
        );

    } catch {

        console.error(
            'Unable to save failure screenshot.'
        );
    }

    process.exitCode = 1;

} finally {

    await browser.close();
}