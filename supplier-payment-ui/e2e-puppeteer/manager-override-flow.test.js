import puppeteer from 'puppeteer';

const APP_URL =
    process.env.E2E_APP_URL ||
    'http://localhost:4173';

const username =
    process.env.E2E_MANAGER_USERNAME;

const password =
    process.env.E2E_MANAGER_PASSWORD;

const paymentId =
    process.env.E2E_PAYMENT_ID;

if (
    !username ||
    !password ||
    !paymentId
) {
    console.error(
        'Missing E2E_MANAGER_USERNAME, E2E_MANAGER_PASSWORD, or E2E_PAYMENT_ID.'
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
            window.location.pathname === expectedPath,
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
        'PASS: Finance Manager authentication completed.'
    );
}


async function apiGet(path) {
    return page.evaluate(
        async requestPath => {
            const token =
                sessionStorage.getItem(
                    'accessToken'
                );

            const response =
                await fetch(
                    requestPath,
                    {
                        headers: {
                            Authorization:
                                `Bearer ${token}`
                        }
                    }
                );

            let body = null;

            try {
                body =
                    await response.json();
            } catch {
                body = null;
            }

            return {
                ok: response.ok,
                status: response.status,
                body
            };
        },
        path
    );
}


function normalizeArray(value) {
    if (
        Array.isArray(value)
    ) {
        return value;
    }

    if (
        Array.isArray(
            value?.content
        )
    ) {
        return value.content;
    }

    if (
        Array.isArray(
            value?.items
        )
    ) {
        return value.items;
    }

    return [];
}


try {
    console.log('');
    console.log(
        '=========================================='
    );
    console.log(
        'MANAGER OVERRIDE E2E TEST'
    );
    console.log(
        '=========================================='
    );

    console.log(
        `Payment ID: ${paymentId}`
    );


    /*
     * TEST 1
     * Authenticate Finance Manager
     */
    await authenticate();


    /*
     * TEST 2
     * Verify manager identity and role
     */
    console.log('');
    console.log(
        'TEST 2: Verifying Finance Manager identity...'
    );

    const userResponse =
        await apiGet(
            '/security-api/api/v1/auth/me'
        );

    if (
        !userResponse.ok
    ) {
        throw new Error(
            `Unable to read authenticated user. HTTP ${userResponse.status}`
        );
    }

    const currentUser =
        userResponse.body;

    console.log(
        'Authenticated User:',
        {
            userId:
                currentUser?.userId,
            username:
                currentUser?.username,
            roles:
                currentUser?.roles
        }
    );

    if (
        !Array.isArray(
            currentUser?.roles
        ) ||
        !currentUser.roles.includes(
            'FINANCE_MANAGER'
        )
    ) {
        throw new Error(
            'Logged-in account does not have FINANCE_MANAGER role.'
        );
    }

    console.log(
        'PASS: FINANCE_MANAGER role confirmed.'
    );


    /*
     * TEST 3
     * Find approval task
     */
    console.log('');
    console.log(
        'TEST 3: Finding approval task...'
    );

    const taskResponse =
        await apiGet(
            `/approval-api/api/v1/approval-tasks?paymentId=${encodeURIComponent(
                paymentId
            )}`
        );

    if (
        !taskResponse.ok
    ) {
        throw new Error(
            `Unable to load approval task. HTTP ${taskResponse.status}`
        );
    }

    const tasks =
        normalizeArray(
            taskResponse.body
        );

    const task =
        tasks.find(
            item =>
                String(
                    item.paymentId
                ) ===
                String(
                    paymentId
                )
        );

    if (!task) {
        throw new Error(
            `No approval task found for Payment ${paymentId}.`
        );
    }

    console.log(
        'Approval Task:',
        {
            taskId:
                task.id,
            paymentId:
                task.paymentId,
            requesterId:
                task.requesterId,
            assignedApproverId:
                task.assignedApproverId,
            status:
                task.status
        }
    );

    if (
        ![
            'PENDING_APPROVAL',
            'ESCALATED',
            'MANUAL_REVIEW'
        ].includes(
            task.status
        )
    ) {
        throw new Error(
            `Task ${task.id} is not in an overrideable state. Current status: ${task.status}`
        );
    }

    if (
        Number(
            task.requesterId
        ) ===
        Number(
            currentUser.userId
        )
    ) {
        throw new Error(
            'Finance Manager is also the requester. Self-override must not be tested with this account.'
        );
    }

    console.log(
        `PASS: Task ${task.id} is eligible for manager override.`
    );


    /*
     * TEST 4
     * Open Approval Workflow
     */
    console.log('');
    console.log(
        'TEST 4: Opening Approval Workflow...'
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
     * TEST 5
     * Filter current task status
     */
    console.log('');
    console.log(
        `TEST 5: Filtering ${task.status} tasks...`
    );

    await page.waitForSelector(
        '#approval-status',
        {
            visible: true
        }
    );

    await page.select(
        '#approval-status',
        task.status
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
     * TEST 6
     * Find target row and Manager Override action
     */
    console.log('');
    console.log(
        `TEST 6: Checking Manager Override action for Payment ${paymentId}...`
    );

    const uiTask =
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

                    if (
                        cells[1]
                            .textContent
                            .trim() !==
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
                            cells[1]
                                .textContent
                                .trim(),
                        assignedApproverId:
                            cells[2]
                                .textContent
                                .trim(),
                        status:
                            cells[3]
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
            String(
                paymentId
            )
        );

    if (!uiTask) {
        throw new Error(
            `Payment ${paymentId} is not visible in the Approval Workflow.`
        );
    }

    console.log(
        'UI Task:',
        uiTask
    );

    if (
        !uiTask.actions.includes(
            'Manager Override'
        )
    ) {
        throw new Error(
            'Manager Override action is not available.'
        );
    }

    console.log(
        'PASS: Manager Override action is available.'
    );


    /*
     * TEST 7
     * Open Manager Override form
     */
    console.log('');
    console.log(
        'TEST 7: Opening Manager Override form...'
    );

    const opened =
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
                        cells.length < 6 ||
                        cells[1]
                            .textContent
                            .trim() !==
                        expectedPaymentId
                    ) {
                        continue;
                    }

                    const buttons =
                        Array.from(
                            cells[5]
                                .querySelectorAll(
                                    'button'
                                )
                        );

                    const button =
                        buttons.find(
                            item =>
                                item.textContent
                                    .trim() ===
                                'Manager Override'
                        );

                    if (!button) {
                        return false;
                    }

                    button.click();

                    return true;
                }

                return false;
            },
            String(
                paymentId
            )
        );

    if (!opened) {
        throw new Error(
            'Unable to open Manager Override form.'
        );
    }

    await page.waitForFunction(
        () =>
            document.body.innerText.includes(
                'Manager Override'
            ) &&
            Boolean(
                document.querySelector(
                    '#approval-remark'
                )
            ),
        {
            timeout: 10000
        }
    );

    console.log(
        'PASS: Manager Override form opened.'
    );


    /*
     * TEST 8
     * Verify mandatory justification
     *
     * IMPORTANT:
     * Do not register a dialog listener here.
     *
     * Native HTML required validation prevents
     * submitDecision() from running, so no
     * window.confirm() should be reached.
     */
    console.log('');
    console.log(
        'TEST 8: Verifying mandatory justification...'
    );

    const remarkRequired =
        await page.$eval(
            '#approval-remark',
            element =>
                element.required
        );

    if (!remarkRequired) {
        throw new Error(
            'Manager Override remark is not marked required.'
        );
    }

    await page.click(
        '.approval-decision-form button[type="submit"]'
    );

    await new Promise(
        resolve =>
            setTimeout(
                resolve,
                500
            )
    );

    const validity =
        await page.$eval(
            '#approval-remark',
            element => ({
                valid:
                    element.checkValidity(),
                valueMissing:
                    element.validity
                        .valueMissing,
                value:
                    element.value
            })
        );

    if (
        validity.valid ||
        !validity.valueMissing
    ) {
        throw new Error(
            'Empty manager override justification was not blocked.'
        );
    }

    const decisionFormStillOpen =
        await page.$(
            '.approval-decision-form'
        );

    if (!decisionFormStillOpen) {
        throw new Error(
            'Manager Override form disappeared after empty submission.'
        );
    }

    console.log(
        'PASS: Empty justification is blocked.'
    );


    /*
     * TEST 9
     * Enter synthetic justification
     */
    console.log('');
    console.log(
        'TEST 9: Entering manager override justification...'
    );

    const syntheticRemark =
        `Synthetic E2E manager override for Payment ${paymentId}`;

    await page.type(
        '#approval-remark',
        syntheticRemark
    );

    const enteredRemark =
        await page.$eval(
            '#approval-remark',
            element =>
                element.value
        );

    if (
        enteredRemark !==
        syntheticRemark
    ) {
        throw new Error(
            'Manager override justification was not entered correctly.'
        );
    }

    console.log(
        'PASS: Manager override justification entered.'
    );


    /*
     * TEST 10
     * Submit override and capture API
     */
    console.log('');
    console.log(
        'TEST 10: Submitting Manager Override...'
    );

    const overrideResponsePromise =
        page.waitForResponse(
            response => {
                const url =
                    response.url();

                return (
                    url.includes(
                        `/approval-api/api/v1/approval-tasks/${task.id}/override-and-approve`
                    ) &&
                    response.request().method() ===
                    'POST'
                );
            },
            {
                timeout: 20000
            }
        );

    /*
     * Defensive cleanup.
     *
     * There must be exactly one dialog handler
     * for the real confirmation dialog.
     */
    page.removeAllListeners(
        'dialog'
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

    await page.click(
        '.approval-decision-form button[type="submit"]'
    );

    const overrideResponse =
        await overrideResponsePromise;

    if (
        !overrideResponse.ok()
    ) {
        let responseBody =
            '';

        try {
            responseBody =
                await overrideResponse.text();
        } catch {
            responseBody =
                '';
        }

        throw new Error(
            `Manager Override API failed with HTTP ${overrideResponse.status()}. ${responseBody}`
        );
    }

    console.log(
        'PASS: Manager Override API completed successfully.'
    );


    /*
     * TEST 11
     * Verify navigation
     */
    console.log('');
    console.log(
        'TEST 11: Checking post-override navigation...'
    );

    await page.waitForFunction(
        expectedPaymentId => {
            const url =
                new URL(
                    window.location.href
                );

            return (
                url.pathname ===
                '/payment-processing' &&
                url.searchParams.get(
                    'paymentId'
                ) ===
                expectedPaymentId
            );
        },
        {
            timeout: 15000
        },
        String(
            paymentId
        )
    );

    console.log(
        `PASS: Navigated to payment processing for Payment ${paymentId}.`
    );


    /*
     * TEST 12
     * Verify Payment Processing page
     */
    console.log('');
    console.log(
        'TEST 12: Checking Payment Processing page...'
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
     * TEST 13
     * Verify task status via API
     */
    console.log('');
    console.log(
        'TEST 13: Verifying approval task status...'
    );

    const updatedTaskResponse =
        await apiGet(
            `/approval-api/api/v1/approval-tasks/${task.id}`
        );

    if (
        !updatedTaskResponse.ok
    ) {
        throw new Error(
            `Unable to read updated approval task. HTTP ${updatedTaskResponse.status}`
        );
    }

    const updatedTask =
        updatedTaskResponse.body;

    console.log(
        'Updated Approval Task:',
        {
            taskId:
                updatedTask?.id,
            paymentId:
                updatedTask?.paymentId,
            status:
                updatedTask?.status,
            decidedBy:
                updatedTask?.decidedBy,
            remark:
                updatedTask?.remark
        }
    );

    if (
        updatedTask?.status !==
        'APPROVED'
    ) {
        throw new Error(
            `Expected approval task status APPROVED but found ${updatedTask?.status}.`
        );
    }

    console.log(
        'PASS: Approval task status is APPROVED.'
    );


    /*
     * TEST 14
     * Verify manager was recorded as decision maker
     */
    console.log('');
    console.log(
        'TEST 14: Checking manager decision metadata...'
    );

    if (
        updatedTask?.decidedBy != null &&
        Number(
            updatedTask.decidedBy
        ) !==
        Number(
            currentUser.userId
        )
    ) {
        throw new Error(
            `Approval was decided by user ${updatedTask.decidedBy}, expected manager user ${currentUser.userId}.`
        );
    }

    if (
        updatedTask?.remark &&
        updatedTask.remark !==
        syntheticRemark
    ) {
        throw new Error(
            'Stored manager override remark does not match the submitted justification.'
        );
    }

    console.log(
        'PASS: Manager decision metadata is consistent.'
    );


    /*
     * TEST 15
     * Verify approval history
     */
    console.log('');
    console.log(
        'TEST 15: Verifying approval history...'
    );

    const historyResponse =
        await apiGet(
            `/approval-api/api/v1/approval-tasks/payment/${encodeURIComponent(
                paymentId
            )}/history`
        );

    if (
        !historyResponse.ok
    ) {
        throw new Error(
            `Unable to load approval history. HTTP ${historyResponse.status}`
        );
    }

    console.log(
        'Approval History:'
    );

    console.dir(
        historyResponse.body,
        {
            depth: null
        }
    );

    const historyTask =
        historyResponse.body?.task;

    if (
        historyTask &&
        historyTask.status !==
        'APPROVED'
    ) {
        throw new Error(
            `Approval history task status is ${historyTask.status}, expected APPROVED.`
        );
    }

    console.log(
        'PASS: Approval history available after manager override.'
    );


    await page.screenshot({
        path:
            'e2e-puppeteer/manager-override-flow-success.png',
        fullPage: true
    });


    console.log('');
    console.log(
        '=========================================='
    );
    console.log(
        'MANAGER OVERRIDE E2E TEST PASSED'
    );
    console.log(
        '=========================================='
    );

    console.log(
        `Payment ID: ${paymentId}`
    );

    console.log(
        `Approval Task ID: ${task.id}`
    );

    console.log(
        `Finance Manager User ID: ${currentUser.userId}`
    );

    console.log(
        'Final Approval Task Status: APPROVED'
    );

} catch (error) {
    console.error('');
    console.error(
        '=========================================='
    );
    console.error(
        'MANAGER OVERRIDE E2E TEST FAILED'
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
                'e2e-puppeteer/manager-override-flow-failure.png',
            fullPage: true
        });

        console.error(
            'Failure screenshot saved to:'
        );

        console.error(
            'e2e-puppeteer/manager-override-flow-failure.png'
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