import puppeteer from 'puppeteer';

const APP_URL =
    process.env.E2E_APP_URL ||
    'http://localhost:4173';

const username =
    process.env.E2E_APPROVER_USERNAME;

const password =
    process.env.E2E_APPROVER_PASSWORD;

const paymentId =
    process.env.E2E_PAYMENT_ID ||
    '331';

if (
    !username ||
    !password
) {

    console.error(
        'Missing E2E_APPROVER_USERNAME or E2E_APPROVER_PASSWORD.'
    );

    process.exit(1);
}

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
        '>>> Enter the OTP in the browser'
    );

    console.log(
        '>>> and click Verify OTP.'
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


async function readProcessingResult() {

    return page.evaluate(
        () => {

            function getValue(
                label
            ) {

                const paragraphs =
                    Array.from(
                        document.querySelectorAll(
                            '.card p'
                        )
                    );

                const matching =
                    paragraphs.find(
                        paragraph =>
                            paragraph.innerText
                                .trim()
                                .startsWith(
                                    label
                                )
                    );

                if (!matching) {
                    return null;
                }

                return matching.innerText
                    .replace(
                        label,
                        ''
                    )
                    .trim();
            }

            return {
                paymentId:
                    getValue(
                        'Payment ID:'
                    ),

                paymentStatus:
                    getValue(
                        'Payment Status:'
                    ),

                processingStatus:
                    getValue(
                        'Processing Status:'
                    ),

                attemptCount:
                    getValue(
                        'Attempt Count:'
                    ),

                stripePaymentIntentId:
                    getValue(
                        'Stripe PaymentIntent:'
                    )
            };
        }
    );
}


async function clickCheckStatus() {

    const responsePromise =
        page.waitForResponse(
            response =>
                response.url().includes(
                    `/processing-api/api/v1/payment-processing/${paymentId}`
                ) &&
                response.request().method() ===
                'GET',
            {
                timeout: 30000
            }
        );

    await page.click(
        'button[type="submit"]'
    );

    const response =
        await responsePromise;

    if (
        !response.ok()
    ) {

        const text =
            await response.text();

        throw new Error(
            `Processing status request failed with HTTP ${response.status()}: ${text}`
        );
    }

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
}


try {

    console.log('');
    console.log(
        '=========================================='
    );

    console.log(
        'PAYMENT PROCESSING E2E TEST'
    );

    console.log(
        '=========================================='
    );

    console.log(
        `Payment ID: ${paymentId}`
    );


    /*
     * TEST 1
     * Login
     */
    await authenticate();


    /*
     * TEST 2
     * Open Payment Processing page
     */
    console.log('');
    console.log(
        'TEST 2: Opening Payment Processing page...'
    );

    await page.goto(
        `${APP_URL}/payment-processing`,
        {
            waitUntil: 'networkidle2',
            timeout: 30000
        }
    );

    await page.waitForFunction(
        () =>
            document.body.innerText.includes(
                'Payment Processing'
            ),
        {
            timeout: 15000
        }
    );

    console.log(
        'PASS: Payment Processing page loaded.'
    );


    /*
     * TEST 3
     * Enter Payment ID
     */
    console.log('');
    console.log(
        'TEST 3: Entering payment ID...'
    );

    await page.waitForSelector(
        '#processing-payment-id',
        {
            visible: true
        }
    );

    await page.$eval(
        '#processing-payment-id',
        input => {
            input.value = '';
        }
    );

    await page.type(
        '#processing-payment-id',
        paymentId
    );

    console.log(
        `PASS: Payment ID ${paymentId} entered.`
    );


    /*
     * TEST 4
     * First status check
     */
    console.log('');
    console.log(
        'TEST 4: Checking processing status...'
    );

    await clickCheckStatus();

    let bodyText =
        await getBodyText();

    if (
        !bodyText.includes(
            'Processing Details'
        )
    ) {

        throw new Error(
            'Processing Details were not displayed.'
        );
    }

    const firstResult =
        await readProcessingResult();

    console.log(
        'First Processing Result:',
        firstResult
    );

    if (
        firstResult.paymentId !==
        paymentId
    ) {

        throw new Error(
            `Expected Payment ID ${paymentId} but found ${firstResult.paymentId}.`
        );
    }

    if (
        !firstResult.paymentStatus
    ) {

        throw new Error(
            'Payment Status is missing.'
        );
    }

    console.log(
        `PASS: Payment Status = ${firstResult.paymentStatus}`
    );

    if (
        firstResult.processingStatus
    ) {

        console.log(
            `PASS: Processing Status = ${firstResult.processingStatus}`
        );

    } else {

        console.log(
            'INFO: No processing attempt has been recorded yet.'
        );
    }


    /*
     * TEST 5
     * Validate attempt count
     */
    console.log('');
    console.log(
        'TEST 5: Checking processing attempt count...'
    );

    let firstAttemptCount =
        null;

    if (
        firstResult.attemptCount &&
        firstResult.attemptCount !==
        '—'
    ) {

        firstAttemptCount =
            Number(
                firstResult.attemptCount
            );

        if (
            !Number.isFinite(
                firstAttemptCount
            )
        ) {

            throw new Error(
                `Invalid attempt count: ${firstResult.attemptCount}`
            );
        }

        console.log(
            `PASS: Attempt Count = ${firstAttemptCount}`
        );

    } else {

        console.log(
            'INFO: Attempt Count is not available yet.'
        );
    }


    /*
     * TEST 6
     * Second status check
     *
     * This should only read status.
     * It must not create another processing attempt.
     */
    console.log('');
    console.log(
        'TEST 6: Re-checking processing status for idempotency...'
    );

    await clickCheckStatus();

    const secondResult =
        await readProcessingResult();

    console.log(
        'Second Processing Result:',
        secondResult
    );

    if (
        firstAttemptCount !== null &&
        secondResult.attemptCount &&
        secondResult.attemptCount !==
        '—'
    ) {

        const secondAttemptCount =
            Number(
                secondResult.attemptCount
            );

        if (
            secondAttemptCount !==
            firstAttemptCount
        ) {

            throw new Error(
                `Attempt count changed from ${firstAttemptCount} to ${secondAttemptCount} just by checking status.`
            );
        }

        console.log(
            `PASS: Attempt count remained ${secondAttemptCount}.`
        );

        console.log(
            'PASS: Status re-check did not create another processing attempt.'
        );

    } else {

        console.log(
            'INFO: Attempt-count idempotency could not be asserted because no numeric attempt count is available.'
        );
    }


    /*
     * TEST 7
     * Transaction reference consistency
     */
    console.log('');
    console.log(
        'TEST 7: Checking transaction reference consistency...'
    );

    if (
        firstResult.stripePaymentIntentId &&
        firstResult.stripePaymentIntentId !==
        '—'
    ) {

        if (
            secondResult.stripePaymentIntentId !==
            firstResult.stripePaymentIntentId
        ) {

            throw new Error(
                'Stripe PaymentIntent changed between status checks.'
            );
        }

        console.log(
            `PASS: Stripe PaymentIntent remained ${firstResult.stripePaymentIntentId}.`
        );

    } else {

        console.log(
            'INFO: No Stripe PaymentIntent is available yet.'
        );
    }


    /*
     * TEST 8
     * Final status classification
     */
    console.log('');
    console.log(
        'TEST 8: Evaluating final processing state...'
    );

    const acceptablePaymentStates = [
        'APPROVED',
        'PROCESSING',
        'COMPLETE',
        'FAILED'
    ];

    if (
        !acceptablePaymentStates.includes(
            secondResult.paymentStatus
        )
    ) {

        throw new Error(
            `Unexpected payment status after approval: ${secondResult.paymentStatus}`
        );
    }

    console.log(
        `PASS: Valid post-approval payment state = ${secondResult.paymentStatus}`
    );

    if (
        secondResult.paymentStatus ===
        'COMPLETE'
    ) {

        if (
            !secondResult.stripePaymentIntentId ||
            secondResult.stripePaymentIntentId ===
            '—'
        ) {

            throw new Error(
                'Payment is COMPLETE but Stripe PaymentIntent is missing.'
            );
        }

        console.log(
            'PASS: Completed payment has a transaction reference.'
        );
    }


    /*
     * Success screenshot
     */
    await page.screenshot({
        path:
            'e2e-puppeteer/payment-processing-flow-success.png',
        fullPage: true
    });

    console.log('');
    console.log(
        '=========================================='
    );

    console.log(
        'PAYMENT PROCESSING E2E TEST PASSED'
    );

    console.log(
        '=========================================='
    );

    console.log(
        `Payment ID: ${paymentId}`
    );

    console.log(
        `Payment Status: ${secondResult.paymentStatus}`
    );

    console.log(
        `Processing Status: ${secondResult.processingStatus}`
    );

    console.log(
        `Attempt Count: ${secondResult.attemptCount}`
    );

    console.log(
        `Stripe PaymentIntent: ${secondResult.stripePaymentIntentId}`
    );

} catch (error) {

    console.error('');
    console.error(
        '=========================================='
    );

    console.error(
        'PAYMENT PROCESSING E2E TEST FAILED'
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
                'e2e-puppeteer/payment-processing-flow-failure.png',
            fullPage: true
        });

        console.error(
            'Failure screenshot saved to:'
        );

        console.error(
            'e2e-puppeteer/payment-processing-flow-failure.png'
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