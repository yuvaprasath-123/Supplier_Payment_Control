import puppeteer from 'puppeteer';


const APP_URL =
    process.env.E2E_APP_URL ||
    'http://localhost:4173';

const PAYMENT_ID =
    process.env.E2E_PAYMENT_ID ||
    '370';

const APPROVER_USERNAME =
    process.env.E2E_APPROVER_USERNAME;

const APPROVER_PASSWORD =
    process.env.E2E_APPROVER_PASSWORD;


if (
    !APPROVER_USERNAME ||
    !APPROVER_PASSWORD
) {
    console.error(
        'Missing Finance Approver credentials.'
    );

    console.error(
        'Required environment variables:'
    );

    console.error(
        'E2E_APPROVER_USERNAME'
    );

    console.error(
        'E2E_APPROVER_PASSWORD'
    );

    process.exit(1);
}


function printDivider() {
    console.log(
        '=========================================='
    );
}


function sleep(milliseconds) {
    return new Promise(
        resolve =>
            setTimeout(
                resolve,
                milliseconds
            )
    );
}


async function waitForPath(
    page,
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


async function getCurrentUser(
    page
) {
    return page.evaluate(
        async () => {
            const token =
                sessionStorage.getItem(
                    'accessToken'
                );

            if (!token) {
                throw new Error(
                    'No access token exists in sessionStorage.'
                );
            }

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
                    `Current-user request failed with HTTP ${response.status}.`
                );
            }

            return response.json();
        }
    );
}


async function findButtonByExactText(
    page,
    buttonText
) {
    const handle =
        await page.evaluateHandle(
            expectedText => {
                return Array
                    .from(
                        document.querySelectorAll(
                            'button'
                        )
                    )
                    .find(
                        button =>
                            button.textContent
                                .trim() ===
                            expectedText
                    ) || null;
            },
            buttonText
        );

    return handle.asElement();
}


async function findLinkByExactText(
    page,
    linkText
) {
    const handle =
        await page.evaluateHandle(
            expectedText => {
                return Array
                    .from(
                        document.querySelectorAll(
                            'a'
                        )
                    )
                    .find(
                        link =>
                            link.textContent
                                .trim() ===
                            expectedText
                    ) || null;
            },
            linkText
        );

    return handle.asElement();
}


async function authenticateApprover(
    page
) {
    console.log('');

    printDivider();

    console.log(
        'APPROVER AUTHENTICATION'
    );

    printDivider();

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
            visible: true,
            timeout: 15000
        }
    );

    await page.waitForSelector(
        '#password',
        {
            visible: true,
            timeout: 15000
        }
    );

    await page.type(
        '#username',
        APPROVER_USERNAME
    );

    await page.type(
        '#password',
        APPROVER_PASSWORD
    );

    const loginButton =
        await page.$(
            'button[type="submit"]'
        );

    if (!loginButton) {
        throw new Error(
            'Login submit button not found.'
        );
    }

    await loginButton.click();

    await waitForPath(
        page,
        '/verify-otp',
        15000
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
        page,
        '/',
        180000
    );

    console.log(
        'PASS: Approver authentication completed.'
    );
}


async function openApprovalWorkflow(
    page
) {
    console.log('');

    console.log(
        'TEST 2: Opening Approval Workflow...'
    );

    const approvalsLink =
        await findLinkByExactText(
            page,
            'Approvals'
        );

    if (approvalsLink) {
        await approvalsLink.click();

        await waitForPath(
            page,
            '/approvals',
            15000
        );
    } else {
        await page.goto(
            `${APP_URL}/approvals`,
            {
                waitUntil: 'networkidle2',
                timeout: 30000
            }
        );
    }

    await page.waitForFunction(
        () =>
            document.body.innerText
                .includes(
                    'Approval'
                ),
        {
            timeout: 15000
        }
    );

    console.log(
        'PASS: Approval Workflow page loaded.'
    );
}


async function filterPendingApprovalTasks(
    page
) {
    console.log('');

    console.log(
        'TEST 3: Filtering PENDING_APPROVAL tasks...'
    );

    const statusSelect =
        await page.$(
            'select'
        );

    if (statusSelect) {
        const options =
            await page.evaluate(
                select =>
                    Array
                        .from(
                            select.options
                        )
                        .map(
                            option => ({
                                text:
                                    option.textContent
                                        .trim(),
                                value:
                                    option.value
                            })
                        ),
                statusSelect
            );

        const pendingOption =
            options.find(
                option =>
                    option.value ===
                    'PENDING_APPROVAL' ||
                    option.text ===
                    'PENDING_APPROVAL'
            );

        if (pendingOption) {
            await statusSelect.select(
                pendingOption.value
            );

            await sleep(
                1000
            );
        }
    }

    console.log(
        'PASS: Approval tasks loaded.'
    );
}


async function findApprovalTask(
    page,
    paymentId
) {
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
                            'table tbody tr'
                        )
                    );

                for (
                    const row of rows
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

                    const texts =
                        cells.map(
                            cell =>
                                cell.textContent
                                    .trim()
                        );

                    const paymentMatches =
                        texts.some(
                            text =>
                                text ===
                                String(
                                    expectedPaymentId
                                )
                        );

                    const statusMatches =
                        texts.some(
                            text =>
                                text ===
                                'PENDING_APPROVAL'
                        );

                    if (
                        !paymentMatches ||
                        !statusMatches
                    ) {
                        continue;
                    }

                    const buttons =
                        Array
                            .from(
                                row.querySelectorAll(
                                    'button'
                                )
                            )
                            .map(
                                button =>
                                    button.textContent
                                        .trim()
                            );

                    return {
                        rowText:
                            row.textContent
                                .trim(),
                        cells:
                            texts,
                        actions:
                            buttons
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


    /*
     * The current Approval table layout is:
     *
     * Task ID
     * Payment ID
     * Assigned Approver
     * Status
     * Due At
     * Actions
     */
    const taskId =
        task.cells[0];

    const rowPaymentId =
        task.cells[1];

    const assignedApproverId =
        task.cells[2];

    const status =
        task.cells[3];

    const result = {
        taskId,
        paymentId:
            rowPaymentId,
        assignedApproverId,
        status,
        actions:
            task.actions
    };

    console.log(
        'Approval Task:',
        result
    );

    console.log(
        `PASS: Approval Task ${taskId} found.`
    );

    console.log(
        `Assigned Approver ID: ${assignedApproverId}`
    );

    return result;
}


async function verifyRejectPermission(
    task
) {
    console.log('');

    console.log(
        'TEST 5: Checking rejection permission...'
    );

    if (
        !task.actions.includes(
            'Reject'
        )
    ) {
        throw new Error(
            `The logged-in account cannot reject Task ${task.taskId}. Assigned ApproverID is ${task.assignedApproverId}.`
        );
    }

    console.log(
        'PASS: Reject action is available.'
    );
}


async function clickTaskRejectButton(
    page,
    paymentId
) {
    console.log('');

    console.log(
        'TEST 6: Opening rejection form...'
    );

    const clicked =
        await page.evaluate(
            expectedPaymentId => {
                const rows =
                    Array.from(
                        document.querySelectorAll(
                            'table tbody tr'
                        )
                    );

                for (
                    const row of rows
                ) {
                    const cells =
                        Array.from(
                            row.querySelectorAll(
                                'td'
                            )
                        );

                    const hasPayment =
                        cells.some(
                            cell =>
                                cell.textContent
                                    .trim() ===
                                String(
                                    expectedPaymentId
                                )
                        );

                    if (!hasPayment) {
                        continue;
                    }

                    const rejectButton =
                        Array
                            .from(
                                row.querySelectorAll(
                                    'button'
                                )
                            )
                            .find(
                                button =>
                                    button.textContent
                                        .trim() ===
                                    'Reject'
                            );

                    if (!rejectButton) {
                        return false;
                    }

                    rejectButton.click();

                    return true;
                }

                return false;
            },
            paymentId
        );

    if (!clicked) {
        throw new Error(
            `Reject button could not be clicked for Payment ${paymentId}.`
        );
    }

    await page.waitForSelector(
        '#approval-remark',
        {
            visible: true,
            timeout: 10000
        }
    );

    console.log(
        'PASS: Rejection form opened.'
    );
}


async function verifyMandatoryRemark(
    page
) {
    console.log('');

    console.log(
        'TEST 7: Verifying mandatory rejection remark...'
    );

    /*
     * Verify that the textarea exists and that HTML
     * validation marks it as required.
     */
    const validationState =
        await page.$eval(
            '#approval-remark',
            element => ({
                required:
                    element.required,

                value:
                    element.value,

                valid:
                    element.checkValidity(),

                validationMessage:
                    element.validationMessage
            })
        );


    if (
        !validationState.required
    ) {
        throw new Error(
            'Rejection remark field is not marked as required.'
        );
    }


    console.log(
        'PASS: Rejection remark is required.'
    );


    /*
     * Make absolutely sure the field is empty.
     */
    await page.$eval(
        '#approval-remark',
        element => {
            const setter =
                Object
                    .getOwnPropertyDescriptor(
                        HTMLTextAreaElement.prototype,
                        'value'
                    )
                    .set;

            setter.call(
                element,
                ''
            );

            element.dispatchEvent(
                new Event(
                    'input',
                    {
                        bubbles: true
                    }
                )
            );

            element.dispatchEvent(
                new Event(
                    'change',
                    {
                        bubbles: true
                    }
                )
            );
        }
    );


    /*
     * Test browser validation directly.
     *
     * This avoids opening any confirm() dialog and therefore
     * avoids leaving behind stale Puppeteer dialog listeners.
     */
    const emptyFieldValidation =
        await page.$eval(
            '#approval-remark',
            element => ({
                required:
                    element.required,

                value:
                    element.value,

                valid:
                    element.checkValidity(),

                valueMissing:
                    element.validity.valueMissing,

                validationMessage:
                    element.validationMessage
            })
        );


    if (
        emptyFieldValidation.value !==
        ''
    ) {
        throw new Error(
            'Rejection remark was expected to be empty.'
        );
    }


    if (
        emptyFieldValidation.valid
    ) {
        throw new Error(
            'Empty rejection remark unexpectedly passed HTML validation.'
        );
    }


    if (
        !emptyFieldValidation.valueMissing
    ) {
        throw new Error(
            'Empty rejection remark did not report the required-field validation error.'
        );
    }


    /*
     * Confirm the decision form cannot be submitted while
     * the required textarea is empty.
     */
    const formValidation =
        await page.$eval(
            '#approval-remark',
            element => {
                const form =
                    element.closest(
                        'form'
                    );

                if (!form) {
                    return {
                        formFound: false,
                        valid: false
                    };
                }

                return {
                    formFound: true,
                    valid:
                        form.checkValidity()
                };
            }
        );


    /*
     * Some implementations may not wrap the decision UI
     * in a normal <form>. In that case textarea validity
     * above is sufficient.
     */
    if (
        formValidation.formFound &&
        formValidation.valid
    ) {
        throw new Error(
            'Decision form unexpectedly reports valid while rejection remark is empty.'
        );
    }


    console.log(
        'PASS: Empty rejection remark was blocked.'
    );
}

async function enterRejectionReason(
    page
) {
    console.log('');

    console.log(
        'TEST 8: Entering rejection reason...'
    );

    const rejectionReason =
        `Synthetic E2E rejection for Payment ${PAYMENT_ID}`;

    await page.focus(
        '#approval-remark'
    );

    await page.evaluate(
        () => {
            const textarea =
                document.querySelector(
                    '#approval-remark'
                );

            if (textarea) {
                textarea.value =
                    '';
            }
        }
    );

    await page.type(
        '#approval-remark',
        rejectionReason
    );

    const enteredValue =
        await page.$eval(
            '#approval-remark',
            element =>
                element.value
        );

    if (
        enteredValue !==
        rejectionReason
    ) {
        throw new Error(
            'Rejection reason was not entered correctly.'
        );
    }

    console.log(
        'PASS: Rejection reason entered.'
    );

    return rejectionReason;
}


async function confirmRejection(
    page,
    taskId
) {
    console.log('');

    console.log(
        'TEST 9: Confirming rejection...'
    );


    /*
     * At this point there have been NO previous dialog
     * listeners registered by this test.
     *
     * This is the only handler for the real confirm dialog.
     */
    const dialogPromise =
        new Promise(
            (
                resolve,
                reject
            ) => {
                const timeoutId =
                    setTimeout(
                        () => {
                            reject(
                                new Error(
                                    'Timed out waiting for rejection confirmation dialog.'
                                )
                            );
                        },
                        10000
                    );


                page.once(
                    'dialog',
                    async dialog => {
                        const message =
                            dialog.message();

                        console.log(
                            'Confirmation dialog:',
                            message
                        );


                        try {
                            await dialog.accept();

                            clearTimeout(
                                timeoutId
                            );

                            resolve(
                                message
                            );

                        } catch (error) {
                            clearTimeout(
                                timeoutId
                            );

                            reject(
                                error
                            );
                        }
                    }
                );
            }
        );


    const confirmButton =
        await findButtonByExactText(
            page,
            'Confirm Decision'
        );


    if (!confirmButton) {
        throw new Error(
            'Confirm Decision button not found.'
        );
    }


    await confirmButton.click();


    const confirmationMessage =
        await dialogPromise;


    if (
        !confirmationMessage.includes(
            `Reject task ${taskId}`
        )
    ) {
        throw new Error(
            `Unexpected rejection confirmation dialog: ${confirmationMessage}`
        );
    }


    console.log(
        'PASS: Rejection confirmation accepted.'
    );
}


async function waitForRejectionResult(
    page,
    paymentId
) {
    console.log('');

    console.log(
        'TEST 10: Verifying rejection result...'
    );


    /*
     * Wait for the React page to update after the API call.
     */
    await sleep(
        1200
    );


    /*
     * We may currently be showing only PENDING_APPROVAL,
     * meaning the rejected task disappears from the table.
     * Switch filter to REJECTED when available.
     */
    const selects =
        await page.$$('select');

    for (
        const select of selects
    ) {
        const options =
            await page.evaluate(
                element =>
                    Array
                        .from(
                            element.options
                        )
                        .map(
                            option => ({
                                text:
                                    option.textContent
                                        .trim(),
                                value:
                                    option.value
                            })
                        ),
                select
            );

        const rejectedOption =
            options.find(
                option =>
                    option.value ===
                    'REJECTED' ||
                    option.text ===
                    'REJECTED'
            );

        if (rejectedOption) {
            await select.select(
                rejectedOption.value
            );

            await sleep(
                1000
            );

            break;
        }
    }


    const result =
        await page.evaluate(
            expectedPaymentId => {
                const rows =
                    Array.from(
                        document.querySelectorAll(
                            'table tbody tr'
                        )
                    );

                for (
                    const row of rows
                ) {
                    const cells =
                        Array.from(
                            row.querySelectorAll(
                                'td'
                            )
                        )
                            .map(
                                cell =>
                                    cell.textContent
                                        .trim()
                            );

                    const hasPayment =
                        cells.some(
                            text =>
                                text ===
                                String(
                                    expectedPaymentId
                                )
                        );

                    if (!hasPayment) {
                        continue;
                    }

                    return {
                        cells,
                        rowText:
                            row.textContent
                                .trim()
                    };
                }

                return null;
            },
            paymentId
        );


    if (!result) {
        /*
         * If the page does not expose rejected tasks in the
         * current view, verify via the approval API directly.
         */
        const apiResult =
            await page.evaluate(
                async expectedPaymentId => {
                    const token =
                        sessionStorage.getItem(
                            'accessToken'
                        );

                    const response =
                        await fetch(
                            `/approval-api/api/v1/approval-tasks?paymentId=${encodeURIComponent(
                                expectedPaymentId
                            )}`,
                            {
                                headers: {
                                    Authorization:
                                        `Bearer ${token}`
                                }
                            }
                        );

                    if (!response.ok) {
                        throw new Error(
                            `Approval-task verification failed with HTTP ${response.status}.`
                        );
                    }

                    return response.json();
                },
                paymentId
            );


        const tasks =
            Array.isArray(
                apiResult
            )
                ? apiResult
                : (
                    Array.isArray(
                        apiResult?.content
                    )
                        ? apiResult.content
                        : []
                );


        const rejectedTask =
            tasks.find(
                task =>
                    String(
                        task.paymentId
                    ) ===
                    String(
                        paymentId
                    ) &&
                    task.status ===
                    'REJECTED'
            );


        if (!rejectedTask) {
            throw new Error(
                `Payment ${paymentId} does not have a REJECTED approval task after confirmation.`
            );
        }


        console.log(
            'Rejected Task:',
            {
                taskId:
                    rejectedTask.id,
                paymentId:
                    rejectedTask.paymentId,
                status:
                    rejectedTask.status,
                decidedBy:
                    rejectedTask.decidedBy,
                remark:
                    rejectedTask.remark
            }
        );


        console.log(
            'PASS: Task status is REJECTED.'
        );


        if (
            !rejectedTask.remark ||
            !rejectedTask.remark.includes(
                'Synthetic E2E rejection'
            )
        ) {
            throw new Error(
                'Rejection remark was not persisted.'
            );
        }


        console.log(
            'PASS: Rejection remark was persisted.'
        );

        return rejectedTask;
    }


    const rowIsRejected =
        result.cells.some(
            text =>
                text ===
                'REJECTED'
        );


    if (!rowIsRejected) {
        throw new Error(
            `Payment ${paymentId} is visible but the approval task is not REJECTED.`
        );
    }


    console.log(
        'PASS: Task status is REJECTED.'
    );


    if (
        !result.rowText.includes(
            'Synthetic E2E rejection'
        )
    ) {
        /*
         * The table may not display remarks.
         * Verify persistence through API instead.
         */
        const rejectedTask =
            await page.evaluate(
                async expectedPaymentId => {
                    const token =
                        sessionStorage.getItem(
                            'accessToken'
                        );

                    const response =
                        await fetch(
                            `/approval-api/api/v1/approval-tasks?paymentId=${encodeURIComponent(
                                expectedPaymentId
                            )}`,
                            {
                                headers: {
                                    Authorization:
                                        `Bearer ${token}`
                                }
                            }
                        );

                    if (!response.ok) {
                        throw new Error(
                            `Approval-task verification failed with HTTP ${response.status}.`
                        );
                    }

                    const data =
                        await response.json();

                    const tasks =
                        Array.isArray(
                            data
                        )
                            ? data
                            : (
                                Array.isArray(
                                    data?.content
                                )
                                    ? data.content
                                    : []
                            );

                    return tasks.find(
                        task =>
                            String(
                                task.paymentId
                            ) ===
                            String(
                                expectedPaymentId
                            ) &&
                            task.status ===
                            'REJECTED'
                    ) || null;
                },
                paymentId
            );


        if (
            !rejectedTask ||
            !rejectedTask.remark ||
            !rejectedTask.remark.includes(
                'Synthetic E2E rejection'
            )
        ) {
            throw new Error(
                'Rejection remark was not persisted.'
            );
        }
    }


    console.log(
        'PASS: Rejection remark was persisted.'
    );

    return result;
}


let browser = null;
let page = null;


try {
    console.log('');

    printDivider();

    console.log(
        'REJECTION FLOW E2E TEST'
    );

    printDivider();

    console.log(
        `Payment ID: ${PAYMENT_ID}`
    );


    browser =
        await puppeteer.launch({
            headless: false,
            slowMo: 100,
            defaultViewport: null,
            args: [
                '--start-maximized'
            ]
        });


    page =
        await browser.newPage();


    /*
     * No global page.on('dialog') handler is registered.
     *
     * Each dialog is handled only where it is expected.
     */


    await authenticateApprover(
        page
    );


    /*
     * Useful diagnostic because assignment is based on
     * numeric user ID, not only role.
     */
    const authenticatedUser =
        await getCurrentUser(
            page
        );

    console.log(
        'Authenticated Approver:',
        {
            userId:
                authenticatedUser.userId,
            username:
                authenticatedUser.username,
            roles:
                authenticatedUser.roles
        }
    );


    await openApprovalWorkflow(
        page
    );


    await filterPendingApprovalTasks(
        page
    );


    const task =
        await findApprovalTask(
            page,
            PAYMENT_ID
        );


    await verifyRejectPermission(
        task
    );


    await clickTaskRejectButton(
        page,
        PAYMENT_ID
    );


    await verifyMandatoryRemark(
        page
    );


    const rejectionReason =
        await enterRejectionReason(
            page
        );


    await confirmRejection(
        page,
        task.taskId
    );


    const rejectionResult =
        await waitForRejectionResult(
            page,
            PAYMENT_ID
        );


    await page.screenshot({
        path:
            'e2e-puppeteer/rejection-flow-success.png',
        fullPage: true
    });


    console.log('');

    printDivider();

    console.log(
        'REJECTION FLOW E2E TEST PASSED'
    );

    printDivider();

    console.log(
        `Payment ID: ${PAYMENT_ID}`
    );

    console.log(
        `Approval Task ID: ${task.taskId}`
    );

    console.log(
        `Assigned Approver ID: ${task.assignedApproverId}`
    );

    console.log(
        `Authenticated User ID: ${authenticatedUser.userId}`
    );

    console.log(
        'Final Approval Status: REJECTED'
    );

    console.log(
        `Rejection Remark: ${rejectionReason}`
    );

    console.log(
        'Mandatory rejection remark: VERIFIED'
    );

    console.log(
        'Rejection audit persistence: VERIFIED'
    );


} catch (error) {
    console.error('');

    printDivider();

    console.error(
        'REJECTION FLOW E2E TEST FAILED'
    );

    printDivider();

    console.error(
        error
    );


    if (page) {
        try {
            await page.screenshot({
                path:
                    'e2e-puppeteer/rejection-flow-failure.png',
                fullPage: true
            });

            console.error(
                'Failure screenshot saved to:'
            );

            console.error(
                'e2e-puppeteer/rejection-flow-failure.png'
            );

        } catch {
            // Ignore screenshot failure during cleanup.
        }
    }


    process.exitCode = 1;


} finally {
    if (browser) {
        try {
            await browser.close();
        } catch {
            /*
             * Windows/Chrome can print harmless child-process
             * termination messages after the real test result.
             */
        }
    }
}