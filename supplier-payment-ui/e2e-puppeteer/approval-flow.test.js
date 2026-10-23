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
        'APPROVER AUTHENTICATION'
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
        '>>> Enter the approver OTP'
    );

    console.log(
        '>>> in the browser and click Verify OTP.'
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
        'PASS: Approver authentication completed.'
    );
}


try {

    console.log('');
    console.log(
        '=========================================='
    );

    console.log(
        'APPROVAL FLOW E2E TEST'
    );

    console.log(
        '=========================================='
    );

    console.log(
        `Payment ID: ${paymentId}`
    );


    /*
     * TEST 1
     * Authenticate as Finance Approver
     */
    await authenticate();


    /*
     * TEST 2
     * Open Approval Workflow directly
     */
    console.log('');
    console.log(
        'TEST 2: Opening Approval Workflow...'
    );

    await page.goto(
        `${APP_URL}/approvals`,
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
                'Approval Workflow'
            );
        },
        {
            timeout: 15000
        }
    );

    console.log(
        'PASS: Approval Workflow page loaded.'
    );


    /*
     * TEST 3
     * Filter by pending approval
     */
    console.log('');
    console.log(
        'TEST 3: Filtering PENDING_APPROVAL tasks...'
    );

    await page.waitForSelector(
        '#approval-status',
        {
            visible: true
        }
    );

    await page.select(
        '#approval-status',
        'PENDING_APPROVAL'
    );

    await page.waitForFunction(
        () =>
            !document.body.innerText.includes(
                'Loading approvals...'
            ),
        {
            timeout: 15000
        }
    );

    console.log(
        'PASS: Approval tasks loaded.'
    );


    /*
     * TEST 4
     * Find Payment task
     */
    console.log('');
    console.log(
        `TEST 4: Finding task for Payment ${paymentId}...`
    );

    const task =
        await page.evaluate(
            expectedPaymentId => {

                const rows =
                    Array.from(
                        document.querySelectorAll(
                            '.approvals-table tbody tr'
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
                        cells.length < 6
                    ) {
                        continue;
                    }

                    const rowPaymentId =
                        cells[1]
                            .textContent
                            .trim();

                    if (
                        rowPaymentId !==
                        expectedPaymentId
                    ) {
                        continue;
                    }

                    return {
                        taskId:
                            cells[0]
                                .textContent
                                .trim(),

                        paymentId:
                            rowPaymentId,

                        assignedApproverId:
                            cells[2]
                                .textContent
                                .trim(),

                        status:
                            cells[3]
                                .textContent
                                .trim(),

                        dueAt:
                            cells[4]
                                .textContent
                                .trim(),

                        actions:
                            Array.from(
                                cells[5]
                                    .querySelectorAll(
                                        'button'
                                    )
                            ).map(
                                button =>
                                    button.textContent
                                        .trim()
                            )
                    };
                }

                return null;
            },
            paymentId
        );

    if (!task) {

        throw new Error(
            `No PENDING_APPROVAL task found for Payment ${paymentId}.`
        );
    }

    console.log(
        'Approval Task:',
        task
    );

    if (
        task.status !==
        'PENDING_APPROVAL'
    ) {

        throw new Error(
            `Expected PENDING_APPROVAL but found ${task.status}.`
        );
    }

    console.log(
        `PASS: Approval Task ${task.taskId} found.`
    );

    console.log(
        `Assigned Approver ID: ${task.assignedApproverId}`
    );


    /*
     * TEST 5
     * Verify assigned approver permission
     */
    console.log('');
    console.log(
        'TEST 5: Checking approval permission...'
    );

    if (
        !task.actions.includes(
            'Approve'
        )
    ) {

        throw new Error(
            `The logged-in account cannot approve Task ${task.taskId}. Assigned Approver ID is ${task.assignedApproverId}.`
        );
    }

    if (
        !task.actions.includes(
            'Reject'
        )
    ) {

        throw new Error(
            'Reject action is not available to the assigned approver.'
        );
    }

    console.log(
        'PASS: Approve action is available.'
    );

    console.log(
        'PASS: Reject action is available.'
    );


    /*
     * TEST 6
     * Open decision form for exact payment row
     */
    console.log('');
    console.log(
        'TEST 6: Opening approval decision form...'
    );

    const approveClicked =
        await page.evaluate(
            expectedPaymentId => {

                const rows =
                    Array.from(
                        document.querySelectorAll(
                            '.approvals-table tbody tr'
                        )
                    );

                const row =
                    rows.find(
                        candidate => {

                            const cells =
                                candidate.querySelectorAll(
                                    'td'
                                );

                            return (
                                cells.length >= 2 &&
                                cells[1]
                                    .textContent
                                    .trim() ===
                                expectedPaymentId
                            );
                        }
                    );

                if (!row) {
                    return false;
                }

                const approveButton =
                    Array.from(
                        row.querySelectorAll(
                            'button'
                        )
                    ).find(
                        button =>
                            button.textContent
                                .trim() ===
                            'Approve'
                    );

                if (!approveButton) {
                    return false;
                }

                approveButton.click();

                return true;
            },
            paymentId
        );

    if (!approveClicked) {

        throw new Error(
            'Could not click Approve for the target payment.'
        );
    }

    await page.waitForFunction(
        expectedPaymentId =>
            document.body.innerText.includes(
                'Approve Payment'
            ) &&
            document.body.innerText.includes(
                `Payment #${expectedPaymentId}`
            ),
        {
            timeout: 10000
        },
        paymentId
    );

    console.log(
        'PASS: Approval decision form opened.'
    );


    /*
     * TEST 7
     * Enter optional remark
     */
    console.log('');
    console.log(
        'TEST 7: Entering approval remark...'
    );

    await page.waitForSelector(
        '#approval-remark',
        {
            visible: true
        }
    );

    await page.type(
        '#approval-remark',
        `Approved by E2E test for Payment ${paymentId}`
    );

    console.log(
        'PASS: Approval remark entered.'
    );


    /*
     * TEST 8
     * Confirm approval
     */
    console.log('');
    console.log(
        'TEST 8: Confirming approval...'
    );

    page.once(
        'dialog',
        async dialog => {

            console.log(
                `Confirmation dialog: ${dialog.message()}`
            );

            await dialog.accept();
        }
    );

    const approvalResponsePromise =
        page.waitForResponse(
            response =>
                response.url().includes(
                    `/approval-api/api/v1/approval-tasks/${task.taskId}/approve`
                ) &&
                response.request().method() ===
                'POST',
            {
                timeout: 30000
            }
        );

    await page.click(
        '.approval-decision-form button[type="submit"]'
    );

    const approvalResponse =
        await approvalResponsePromise;

    if (
        !approvalResponse.ok()
    ) {

        const responseText =
            await approvalResponse.text();

        throw new Error(
            `Approval failed with HTTP ${approvalResponse.status()}: ${responseText}`
        );
    }

    console.log(
        'PASS: Approval API completed successfully.'
    );


    /*
     * TEST 9
     * Verify navigation to Payment Processing
     */
    console.log('');
    console.log(
        'TEST 9: Checking post-approval navigation...'
    );

    await page.waitForFunction(
        expectedPaymentId => {

            const params =
                new URLSearchParams(
                    window.location.search
                );

            return (
                window.location.pathname ===
                    '/payment-processing' &&
                params.get(
                    'paymentId'
                ) ===
                    expectedPaymentId
            );
        },
        {
            timeout: 15000
        },
        paymentId
    );

    console.log(
        `PASS: Navigated to payment processing for Payment ${paymentId}.`
    );


    /*
     * TEST 10
     * Check processing page
     */
    console.log('');
    console.log(
        'TEST 10: Checking Payment Processing page...'
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

    const processingBody =
        await getBodyText();

    if (
        !processingBody.includes(
            'Payment Processing'
        )
    ) {

        throw new Error(
            'Payment Processing page was not loaded.'
        );
    }

    console.log(
        'PASS: Payment Processing page loaded.'
    );


    /*
     * Success screenshot
     */
    await page.screenshot({
        path:
            'e2e-puppeteer/approval-flow-success.png',
        fullPage: true
    });

    console.log('');
    console.log(
        '=========================================='
    );

    console.log(
        'APPROVAL FLOW E2E TEST PASSED'
    );

    console.log(
        '=========================================='
    );

    console.log(
        `Payment ID: ${paymentId}`
    );

    console.log(
        `Approval Task ID: ${task.taskId}`
    );

    console.log(
        `Assigned Approver ID: ${task.assignedApproverId}`
    );

} catch (error) {

    console.error('');
    console.error(
        '=========================================='
    );

    console.error(
        'APPROVAL FLOW E2E TEST FAILED'
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
                'e2e-puppeteer/approval-flow-failure.png',
            fullPage: true
        });

        console.error(
            'Failure screenshot saved to:'
        );

        console.error(
            'e2e-puppeteer/approval-flow-failure.png'
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