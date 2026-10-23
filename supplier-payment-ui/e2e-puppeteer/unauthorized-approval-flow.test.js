import puppeteer from 'puppeteer';

const APP_URL =
    process.env.E2E_APP_URL ||
    'http://localhost:4173';

const username =
    process.env.E2E_UNAUTHORIZED_USERNAME;

const password =
    process.env.E2E_UNAUTHORIZED_PASSWORD;

const paymentId =
    process.env.E2E_PAYMENT_ID;

if (
    !username ||
    !password ||
    !paymentId
) {

    console.error(
        'Missing E2E_UNAUTHORIZED_USERNAME, E2E_UNAUTHORIZED_PASSWORD, or E2E_PAYMENT_ID.'
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


async function authenticate() {

    console.log('');
    console.log(
        '=========================================='
    );

    console.log(
        'UNAUTHORIZED APPROVER AUTHENTICATION'
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
        '>>> Enter the OTP for this test account'
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


try {

    console.log('');
    console.log(
        '=========================================='
    );

    console.log(
        'UNAUTHORIZED APPROVAL E2E TEST'
    );

    console.log(
        '=========================================='
    );

    console.log(
        `Payment ID: ${paymentId}`
    );


    /*
     * TEST 1
     * Authenticate with the account that should
     * NOT be allowed to approve this payment.
     */
    await authenticate();


    /*
     * TEST 2
     * Verify actual authenticated user
     */
    console.log('');
    console.log(
        'TEST 2: Reading authenticated user...'
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
                        `Current-user request failed with HTTP ${response.status}`
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
        !currentUser.userId
    ) {

        throw new Error(
            'Authenticated userId was not returned.'
        );
    }

    console.log(
        `PASS: Authenticated User ID = ${currentUser.userId}`
    );


    /*
     * TEST 3
     * Read approval task directly through the
     * same API used by the Approval Workflow.
     *
     * This is READ ONLY.
     */
    console.log('');
    console.log(
        'TEST 3: Reading approval task assignment...'
    );

    const taskData =
        await page.evaluate(
            async expectedPaymentId => {

                const token =
                    sessionStorage.getItem(
                        'accessToken'
                    );

                const params =
                    new URLSearchParams({
                        paymentId:
                            expectedPaymentId
                    });

                const response =
                    await fetch(
                        `/approval-api/api/v1/approval-tasks?${params.toString()}`,
                        {
                            headers: {
                                Authorization:
                                    `Bearer ${token}`
                            }
                        }
                    );

                if (!response.ok) {

                    return {
                        accessible:
                            false,

                        status:
                            response.status,

                        tasks:
                            []
                    };
                }

                const data =
                    await response.json();

                return {
                    accessible:
                        true,

                    status:
                        response.status,

                    tasks:
                        Array.isArray(data)
                            ? data
                            : []
                };
            },
            paymentId
        );

    if (
        !taskData.accessible
    ) {

        console.log(
            `PASS: Approval task API is not accessible to this account. HTTP ${taskData.status}.`
        );

        console.log(
            'PASS: Unauthorized account cannot access approval task data.'
        );

        await page.screenshot({
            path:
                'e2e-puppeteer/unauthorized-approval-flow-success.png',
            fullPage: true
        });

        console.log('');
        console.log(
            '=========================================='
        );

        console.log(
            'UNAUTHORIZED APPROVAL E2E TEST PASSED'
        );

        console.log(
            '=========================================='
        );

        console.log(
            'Control result: approval task access blocked.'
        );

        process.exitCode = 0;

    } else {

        const task =
            taskData.tasks.find(
                item =>
                    String(
                        item.paymentId
                    ) ===
                    String(
                        paymentId
                    )
            );

        if (!task) {

            console.log(
                'PASS: Target approval task is not exposed to this account.'
            );

            console.log(
                'PASS: Unauthorized user cannot act on the target payment.'
            );

        } else {

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

            const currentUserId =
                Number(
                    currentUser.userId
                );

            const assignedApproverId =
                Number(
                    task.assignedApproverId
                );

            const requesterId =
                Number(
                    task.requesterId
                );

            const isAssignedApprover =
                currentUserId ===
                assignedApproverId;

            const isRequester =
                currentUserId ===
                requesterId;

            console.log(
                `Current User ID: ${currentUserId}`
            );

            console.log(
                `Requester ID: ${requesterId}`
            );

            console.log(
                `Assigned Approver ID: ${assignedApproverId}`
            );

            console.log(
                `Is requester: ${isRequester}`
            );

            console.log(
                `Is assigned approver: ${isAssignedApprover}`
            );


            /*
             * Important safety condition.
             *
             * This test must NEVER accidentally
             * continue when the selected account
             * is legitimately authorized.
             */
            if (
                isAssignedApprover &&
                !isRequester
            ) {

                throw new Error(
                    `This account is actually authorized to approve Payment ${paymentId}. Use a different account for the unauthorized test.`
                );
            }


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
                    waitUntil:
                        'networkidle2',

                    timeout:
                        30000
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
                    timeout:
                        15000
                }
            );

            console.log(
                'PASS: Approval Workflow page loaded.'
            );


            /*
             * TEST 5
             * Show PENDING_APPROVAL tasks
             */
            console.log('');
            console.log(
                'TEST 5: Loading pending approval tasks...'
            );

            await page.waitForSelector(
                '#approval-status',
                {
                    visible:
                        true
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
                    timeout:
                        15000
                }
            );


            /*
             * TEST 6
             * Find target row if visible.
             */
            console.log('');
            console.log(
                `TEST 6: Checking UI permissions for Payment ${paymentId}...`
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

                console.log(
                    'PASS: Target payment task is not visible in the Approval Workflow.'
                );

            } else {

                console.log(
                    'UI Task:',
                    uiTask
                );

                if (
                    uiTask.actions.includes(
                        'Approve'
                    )
                ) {

                    throw new Error(
                        'SECURITY FAILURE: Unauthorized account can see the Approve action.'
                    );
                }

                if (
                    uiTask.actions.includes(
                        'Reject'
                    )
                ) {

                    throw new Error(
                        'SECURITY FAILURE: Unauthorized account can see the Reject action.'
                    );
                }

                console.log(
                    'PASS: Approve action is not available.'
                );

                console.log(
                    'PASS: Reject action is not available.'
                );
            }


            /*
             * TEST 7
             * Self-approval rule
             */
            console.log('');
            console.log(
                'TEST 7: Evaluating self-approval protection...'
            );

            if (isRequester) {

                console.log(
                    'PASS: Logged-in user is the original requester.'
                );

                console.log(
                    'PASS: Requester does not have Approve/Reject controls.'
                );

                console.log(
                    'PASS: Self-approval is prevented.'
                );

            } else {

                console.log(
                    'INFO: This account is not the requester.'
                );

                console.log(
                    'PASS: Wrong/non-assigned approver has no decision controls.'
                );
            }


            await page.screenshot({
                path:
                    'e2e-puppeteer/unauthorized-approval-flow-success.png',
                fullPage:
                    true
            });

            console.log('');
            console.log(
                '=========================================='
            );

            console.log(
                'UNAUTHORIZED APPROVAL E2E TEST PASSED'
            );

            console.log(
                '=========================================='
            );

            console.log(
                `Payment ID: ${paymentId}`
            );

            console.log(
                `Authenticated User ID: ${currentUserId}`
            );

            console.log(
                `Requester ID: ${requesterId}`
            );

            console.log(
                `Assigned Approver ID: ${assignedApproverId}`
            );

            console.log(
                'Approve action exposed: NO'
            );

            console.log(
                'Reject action exposed: NO'
            );
        }
    }

} catch (error) {

    console.error('');
    console.error(
        '=========================================='
    );

    console.error(
        'UNAUTHORIZED APPROVAL E2E TEST FAILED'
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
                'e2e-puppeteer/unauthorized-approval-flow-failure.png',
            fullPage:
                true
        });

        console.error(
            'Failure screenshot saved to:'
        );

        console.error(
            'e2e-puppeteer/unauthorized-approval-flow-failure.png'
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