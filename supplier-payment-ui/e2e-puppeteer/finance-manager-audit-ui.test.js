import puppeteer from 'puppeteer';

const APP_URL =
    process.env.E2E_APP_URL ||
    'http://localhost:4173';

const username =
    process.env.E2E_MANAGER_USERNAME;

const password =
    process.env.E2E_MANAGER_PASSWORD;

const paymentId =
    process.env.E2E_PAYMENT_ID ||
    '311';

if (
    !username ||
    !password
) {
    console.error(
        'Missing E2E_MANAGER_USERNAME or E2E_MANAGER_PASSWORD.'
    );

    process.exit(1);
}

const browser =
    await puppeteer.launch({
        headless: false,
        slowMo: 100,
        defaultViewport: null,
        args: ['--start-maximized']
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


async function authenticate() {
    console.log('');
    console.log(
        '=========================================='
    );
    console.log(
        'FINANCE MANAGER AUTHENTICATION'
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

    console.log(
        'PASS: MFA challenge started.'
    );

    console.log('');
    console.log(
        '>>> Enter the Finance Manager OTP'
    );
    console.log(
        '>>> in the browser and click Verify OTP.'
    );
    console.log('');

    await waitForPath(
        '/',
        180000
    );

    console.log(
        'PASS: Finance Manager authentication completed.'
    );
}


try {
    console.log('');
    console.log(
        '=========================================='
    );
    console.log(
        'FINANCE MANAGER AUDIT UI E2E TEST'
    );
    console.log(
        '=========================================='
    );

    console.log(
        `Payment ID: ${paymentId}`
    );


    /*
     * TEST 1
     * Authenticate
     */
    await authenticate();


    /*
     * TEST 2
     * Confirm Payment Audit navigation exists
     */
    console.log('');
    console.log(
        'TEST 2: Checking Payment Audit navigation...'
    );

    const paymentAuditLink =
        await page.evaluate(
            () => {
                const links =
                    Array.from(
                        document.querySelectorAll(
                            'a'
                        )
                    );

                const link =
                    links.find(
                        item =>
                            item.textContent
                                .trim() ===
                            'Payment Audit'
                    );

                if (!link) {
                    return null;
                }

                return {
                    text:
                        link.textContent
                            .trim(),
                    href:
                        link.getAttribute(
                            'href'
                        )
                };
            }
        );

    if (!paymentAuditLink) {
        throw new Error(
            'Payment Audit navigation item is not visible.'
        );
    }

    if (
        paymentAuditLink.href !==
        '/audit/payments'
    ) {
        throw new Error(
            `Unexpected Payment Audit href: ${paymentAuditLink.href}`
        );
    }

    console.log(
        'PASS: Payment Audit navigation is visible.'
    );


    /*
     * TEST 3
     * Open Payment Audit list
     */
    console.log('');
    console.log(
        'TEST 3: Opening Payment Audit page...'
    );

    await page.goto(
        `${APP_URL}/audit/payments`,
        {
            waitUntil: 'networkidle2',
            timeout: 30000
        }
    );

    await waitForPath(
        '/audit/payments'
    );

    await page.waitForFunction(
        () =>
            document.body.innerText.includes(
                'Finance Manager Payment Audit'
            ),
        {
            timeout: 15000
        }
    );

    console.log(
        'PASS: Payment Audit page loaded.'
    );


    /*
     * TEST 4
     * Search for target payment
     */
    console.log('');
    console.log(
        `TEST 4: Searching for Payment ${paymentId}...`
    );

    const searchInput =
        await page.$(
            'input[type="search"]'
        );

    if (!searchInput) {
        throw new Error(
            'Payment Audit search field was not found.'
        );
    }

    await searchInput.click({
        clickCount: 3
    });

    await searchInput.type(
        String(paymentId)
    );

    await page.waitForFunction(
        expectedPaymentId => {

            const rows =
                Array.from(
                    document.querySelectorAll(
                        'tbody tr'
                    )
                );

            return rows.some(
                row =>
                    row.innerText.includes(
                        expectedPaymentId
                    )
            );
        },
        {
            timeout: 15000
        },
        String(paymentId)
    );

    console.log(
        `PASS: Payment ${paymentId} appears in audit results.`
    );


    /*
     * TEST 5
     * Open View Audit for target payment
     */
    console.log('');
    console.log(
        'TEST 5: Opening Payment Audit details...'
    );

    const opened =
        await page.evaluate(
            expectedPaymentId => {

                const rows =
                    Array.from(
                        document.querySelectorAll(
                            'tbody tr'
                        )
                    );

                for (
                    const row
                    of rows
                ) {
                    const cells =
                        Array.from(
                            row.querySelectorAll(
                                'td'
                            )
                        );

                    if (
                        cells.length === 0
                    ) {
                        continue;
                    }

                    if (
                        cells[0]
                            .textContent
                            .trim() !==
                        expectedPaymentId
                    ) {
                        continue;
                    }

                    const link =
                        Array.from(
                            row.querySelectorAll(
                                'a'
                            )
                        )
                            .find(
                                item =>
                                    item.textContent
                                        .trim() ===
                                    'View Audit'
                            );

                    if (!link) {
                        return false;
                    }

                    link.click();

                    return true;
                }

                return false;
            },
            String(paymentId)
        );

    if (!opened) {
        throw new Error(
            `Unable to open audit details for Payment ${paymentId}.`
        );
    }

    await waitForPath(
        `/audit/payments/${paymentId}`
    );

    console.log(
        `PASS: Navigated to /audit/payments/${paymentId}.`
    );


    /*
     * TEST 6
     * Confirm details page loaded
     */
    console.log('');
    console.log(
        'TEST 6: Checking Payment Audit details page...'
    );

    await page.waitForFunction(
        expectedPaymentId =>
            document.body.innerText.includes(
                `Payment Audit #${expectedPaymentId}`
            ),
        {
            timeout: 15000
        },
        String(paymentId)
    );

    console.log(
        'PASS: Payment Audit details page loaded.'
    );


    /*
     * TEST 7
     * Verify major audit sections
     */
    console.log('');
    console.log(
        'TEST 7: Verifying audit sections...'
    );

    const requiredSections = [
        'Payment',
        'Risk Assessment',
        'Risk Factors',
        'Approval Routing',
        'Approval History',
        'Decisions',
        'Escalations',
        'Payment Processing',
        'Lifecycle Timeline'
    ];

    const pageText =
        await page.$eval(
            'body',
            element =>
                element.innerText
        );

    for (
        const section
        of requiredSections
    ) {
        if (
            !pageText.includes(
                section
            )
        ) {
            throw new Error(
                `Missing audit section: ${section}`
            );
        }

        console.log(
            `PASS: ${section} section visible.`
        );
    }


    /*
     * TEST 8
     * Check Payment 311-specific audit evidence
     *
     * These checks are conditional so the test
     * can also be used for other payment IDs.
     */
    console.log('');
    console.log(
        'TEST 8: Checking audit evidence...'
    );

    if (
        String(paymentId) ===
        '311'
    ) {
        const expectedValues = [
            'COMPLETE',
            '25',
            'LOW',
            'L1',
            'OVERRIDE_APPROVED',
            'COMPLETED'
        ];

        for (
            const value
            of expectedValues
        ) {
            if (
                !pageText.includes(
                    value
                )
            ) {
                throw new Error(
                    `Expected audit value not visible: ${value}`
                );
            }
        }

        console.log(
            'PASS: Payment 311 audit evidence is visible.'
        );

    } else {
        console.log(
            'INFO: Payment-specific assertions skipped for non-311 payment.'
        );
    }


    /*
     * TEST 9
     * Verify read-only behavior
     */
    console.log('');
    console.log(
        'TEST 9: Verifying read-only audit controls...'
    );

    const forbiddenControls = [
        'Create Payment',
        'Update Payment',
        'Submit Payment',
        'Approve Payment',
        'Reject Payment',
        'Manager Override',
        'Confirm Decision'
    ];

    for (
        const control
        of forbiddenControls
    ) {
        const matchingButtons =
            await page.evaluate(
                expectedText => {

                    return Array
                        .from(
                            document.querySelectorAll(
                                'button'
                            )
                        )
                        .filter(
                            button =>
                                button.textContent
                                    .trim() ===
                                expectedText
                        )
                        .length;
                },
                control
            );

        if (
            matchingButtons > 0
        ) {
            throw new Error(
                `Read-only violation: "${control}" button is visible on audit page.`
            );
        }
    }

    console.log(
        'PASS: No payment mutation or approval decision controls are exposed.'
    );


    /*
     * TEST 10
     * Confirm manager identity remains active
     */
    console.log('');
    console.log(
        'TEST 10: Confirming Finance Manager session...'
    );

    const currentUser =
        await page.evaluate(
            async () => {

                const token =
                    sessionStorage.getItem(
                        'accessToken'
                    );

                const response =
                    await fetch(
                        '/security-api/api/v1/auth/me',
                        {
                            headers: {
                                Authorization:
                                    `Bearer ${token}`
                            }
                        }
                    );

                if (!response.ok) {
                    throw new Error(
                        `HTTP ${response.status}`
                    );
                }

                return response.json();
            }
        );

    console.log(
        'Authenticated User:',
        {
            userId:
                currentUser.userId,

            username:
                currentUser.username,

            roles:
                currentUser.roles
        }
    );

    if (
        !Array.isArray(
            currentUser.roles
        ) ||
        !currentUser.roles.includes(
            'FINANCE_MANAGER'
        )
    ) {
        throw new Error(
            'Current session is not FINANCE_MANAGER.'
        );
    }

    console.log(
        'PASS: Finance Manager session confirmed.'
    );


    await page.screenshot({
        path:
            'e2e-puppeteer/finance-manager-audit-ui-success.png',
        fullPage: true
    });


    console.log('');
    console.log(
        '=========================================='
    );
    console.log(
        'FINANCE MANAGER AUDIT UI E2E TEST PASSED'
    );
    console.log(
        '=========================================='
    );

    console.log(
        `Payment ID: ${paymentId}`
    );

    console.log(
        'Audit page role: FINANCE_MANAGER'
    );

    console.log(
        'Read-only controls verified: YES'
    );

} catch (error) {
    console.error('');
    console.error(
        '=========================================='
    );
    console.error(
        'FINANCE MANAGER AUDIT UI E2E TEST FAILED'
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
                'e2e-puppeteer/finance-manager-audit-ui-failure.png',
            fullPage: true
        });

        console.error(
            'Failure screenshot saved to:'
        );

        console.error(
            'e2e-puppeteer/finance-manager-audit-ui-failure.png'
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