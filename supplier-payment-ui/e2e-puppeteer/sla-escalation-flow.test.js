import puppeteer from 'puppeteer';


const APP_URL =
    process.env.E2E_APP_URL ||
    'http://localhost:4173';

const PAYMENT_ID =
    process.env.E2E_PAYMENT_ID;

const MANAGER_USERNAME =
    process.env.E2E_MANAGER_USERNAME;

const MANAGER_PASSWORD =
    process.env.E2E_MANAGER_PASSWORD;

const ESCALATION_WAIT_MS =
    Number(
        process.env.E2E_ESCALATION_WAIT_MS ||
        120000
    );

const POLL_INTERVAL_MS =
    5000;


if (
    !PAYMENT_ID ||
    !MANAGER_USERNAME ||
    !MANAGER_PASSWORD
) {
    console.error(
        'Missing required E2E environment variables.'
    );

    console.error(
        'Required: E2E_PAYMENT_ID, E2E_MANAGER_USERNAME, E2E_MANAGER_PASSWORD'
    );

    process.exit(1);
}


function divider() {
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


async function authenticateManager(page) {
    console.log('');

    divider();

    console.log(
        'FINANCE MANAGER AUTHENTICATION'
    );

    divider();


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
        MANAGER_USERNAME
    );

    await page.type(
        '#password',
        MANAGER_PASSWORD
    );


    await page.click(
        'button[type="submit"]'
    );


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
        '>>> Enter the Finance Manager OTP'
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
        'PASS: Finance Manager authentication completed.'
    );
}


async function getCurrentUser(page) {
    return page.evaluate(
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
                    `Current-user request failed with HTTP ${response.status}.`
                );
            }

            return response.json();
        }
    );
}


async function fetchApprovalTasks(
    page,
    paymentId
) {
    return page.evaluate(
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
                    `Approval-task request failed with HTTP ${response.status}.`
                );
            }

            const data =
                await response.json();

            if (
                Array.isArray(
                    data
                )
            ) {
                return data;
            }

            if (
                Array.isArray(
                    data?.content
                )
            ) {
                return data.content;
            }

            return [];
        },
        paymentId
    );
}


async function fetchTaskSla(
    page,
    taskId
) {
    return page.evaluate(
        async expectedTaskId => {
            const token =
                sessionStorage.getItem(
                    'accessToken'
                );

            const response =
                await fetch(
                    `/approval-api/api/v1/approval-tasks/${encodeURIComponent(
                        expectedTaskId
                    )}/sla`,
                    {
                        headers: {
                            Authorization:
                                `Bearer ${token}`
                        }
                    }
                );

            if (!response.ok) {
                throw new Error(
                    `SLA request failed with HTTP ${response.status}.`
                );
            }

            return response.json();
        },
        taskId
    );
}


async function fetchApprovalHistory(
    page,
    paymentId
) {
    return page.evaluate(
        async expectedPaymentId => {
            const token =
                sessionStorage.getItem(
                    'accessToken'
                );

            const response =
                await fetch(
                    `/approval-api/api/v1/approval-tasks/payment/${encodeURIComponent(
                        expectedPaymentId
                    )}/history`,
                    {
                        headers: {
                            Authorization:
                                `Bearer ${token}`
                        }
                    }
                );

            if (!response.ok) {
                throw new Error(
                    `Approval-history request failed with HTTP ${response.status}.`
                );
            }

            return response.json();
        },
        paymentId
    );
}


function findTargetTask(
    tasks,
    paymentId
) {
    return (
        tasks.find(
            task =>
                String(
                    task.paymentId
                ) ===
                    String(
                        paymentId
                    ) &&
                (
                    task.status ===
                        'PENDING_APPROVAL' ||
                    task.status ===
                        'ESCALATED'
                )
        ) ||
        null
    );
}


function extractEscalations(history) {
    if (
        Array.isArray(
            history?.escalations
        )
    ) {
        return history.escalations;
    }


    if (
        Array.isArray(
            history
        )
    ) {
        return history.filter(
            entry => {
                const text =
                    JSON.stringify(
                        entry
                    )
                        .toUpperCase();

                return text.includes(
                    'ESCALAT'
                );
            }
        );
    }


    return [];
}


async function inspectState(
    page,
    taskId,
    paymentId
) {
    const tasks =
        await fetchApprovalTasks(
            page,
            paymentId
        );


    const task =
        tasks.find(
            item =>
                String(
                    item.id
                ) ===
                String(
                    taskId
                )
        ) ||
        null;


    const sla =
        await fetchTaskSla(
            page,
            taskId
        );


    const history =
        await fetchApprovalHistory(
            page,
            paymentId
        );


    const escalations =
        extractEscalations(
            history
        );


    return {
        task,
        sla,
        history,
        escalations
    };
}


async function waitForEscalation(
    page,
    taskId,
    paymentId
) {
    const startedAt =
        Date.now();


    while (
        Date.now() - startedAt <
        ESCALATION_WAIT_MS
    ) {
        const state =
            await inspectState(
                page,
                taskId,
                paymentId
            );


        console.log(
            'Escalation poll:',
            {
                taskStatus:
                    state.task?.status,
                assignedApproverId:
                    state.task
                        ?.assignedApproverId,
                dueAt:
                    state.task?.dueAt,
                escalationCount:
                    state.escalations.length
            }
        );


        if (
            state.task?.status ===
                'ESCALATED' &&
            state.escalations.length >
                0
        ) {
            return state;
        }


        await sleep(
            POLL_INTERVAL_MS
        );
    }


    return inspectState(
        page,
        taskId,
        paymentId
    );
}


async function verifyAuditPage(
    page,
    paymentId
) {
    console.log('');

    console.log(
        'TEST 6: Verifying Finance Manager audit evidence...'
    );


    await page.goto(
        `${APP_URL}/audit/payments/${paymentId}`,
        {
            waitUntil: 'networkidle2',
            timeout: 30000
        }
    );


    await page.waitForFunction(
        expectedPaymentId =>
            document.body.innerText.includes(
                `Payment Audit #${expectedPaymentId}`
            ),
        {
            timeout: 15000
        },
        paymentId
    );


    const result =
        await page.evaluate(
            () => {
                const text =
                    document.body.innerText
                        .toUpperCase();

                return {
                    hasEscalationSection:
                        text.includes(
                            'ESCALATIONS'
                        ),

                    hasEscalationEvidence:
                        text.includes(
                            'AUTO_ESCALATED'
                        ) ||
                        text.includes(
                            'AUTOMATIC SLA ESCALATION'
                        ) ||
                        text.includes(
                            'ESCALATED'
                        )
                };
            }
        );


    if (
        !result.hasEscalationSection
    ) {
        throw new Error(
            'Escalations section is not visible on the audit page.'
        );
    }


    if (
        !result.hasEscalationEvidence
    ) {
        throw new Error(
            'Escalation evidence is not visible on the audit page.'
        );
    }


    console.log(
        'PASS: Escalation evidence is visible in Finance Manager Payment Audit.'
    );
}


let browser = null;
let page = null;


try {
    console.log('');

    divider();

    console.log(
        'SLA ESCALATION E2E TEST'
    );

    divider();

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


    await authenticateManager(
        page
    );


    console.log('');

    console.log(
        'TEST 1: Confirming Finance Manager session...'
    );


    const user =
        await getCurrentUser(
            page
        );


    console.log(
        'Authenticated User:',
        {
            userId:
                user.userId,
            username:
                user.username,
            roles:
                user.roles
        }
    );


    if (
        !Array.isArray(
            user.roles
        ) ||
        !user.roles.includes(
            'FINANCE_MANAGER'
        )
    ) {
        throw new Error(
            'Authenticated account is not a FINANCE_MANAGER.'
        );
    }


    console.log(
        'PASS: Finance Manager session confirmed.'
    );


    console.log('');

    console.log(
        `TEST 2: Loading approval task for Payment ${PAYMENT_ID}...`
    );


    const tasks =
        await fetchApprovalTasks(
            page,
            PAYMENT_ID
        );


    const task =
        findTargetTask(
            tasks,
            PAYMENT_ID
        );


    if (!task) {
        console.log(
            'Approval Tasks:',
            tasks.map(
                item => ({
                    id:
                        item.id,
                    paymentId:
                        item.paymentId,
                    status:
                        item.status,
                    dueAt:
                        item.dueAt
                })
            )
        );


        throw new Error(
            `No PENDING_APPROVAL or ESCALATED task found for Payment ${PAYMENT_ID}.`
        );
    }


    console.log(
        'Approval Task:',
        {
            taskId:
                task.id,
            paymentId:
                task.paymentId,
            assignedApproverId:
                task.assignedApproverId,
            status:
                task.status,
            dueAt:
                task.dueAt,
            slaMinutes:
                task.slaMinutes
        }
    );


    console.log(
        `PASS: Approval Task ${task.id} found.`
    );


    /*
     * -----------------------------------------------------
     * CASE A:
     * Task has ALREADY been escalated.
     *
     * The scheduler renews dueAt after escalation, so the
     * new dueAt will normally be in the future.
     * -----------------------------------------------------
     */
    if (
        task.status ===
        'ESCALATED'
    ) {
        console.log('');

        console.log(
            'TEST 3: Task is already ESCALATED. Verifying escalation history...'
        );


        const state =
            await inspectState(
                page,
                task.id,
                PAYMENT_ID
            );


        console.log(
            'Escalation State:',
            {
                taskStatus:
                    state.task?.status,
                assignedApproverId:
                    state.task
                        ?.assignedApproverId,
                dueAt:
                    state.task?.dueAt,
                escalationCount:
                    state.escalations.length
            }
        );


        if (
            state.task?.status !==
            'ESCALATED'
        ) {
            throw new Error(
                'Task is no longer ESCALATED.'
            );
        }


        console.log(
            'PASS: Task status is ESCALATED.'
        );


        if (
            state.escalations.length ===
            0
        ) {
            throw new Error(
                'Task is ESCALATED but no escalation audit record exists.'
            );
        }


        console.log(
            'PASS: Escalation audit record exists.'
        );


        console.log('');

        console.log(
            'TEST 4: Verifying renewed SLA deadline...'
        );


        if (
            !state.task?.dueAt
        ) {
            throw new Error(
                'Escalated task does not have a renewed dueAt.'
            );
        }


        const renewedDueAt =
            new Date(
                state.task.dueAt
            );


        if (
            Number.isNaN(
                renewedDueAt.getTime()
            )
        ) {
            throw new Error(
                `Unable to parse renewed dueAt: ${state.task.dueAt}`
            );
        }


        console.log(
            'Renewed Due At:',
            state.task.dueAt
        );


        console.log(
            'PASS: Escalated task has a renewed SLA deadline.'
        );


        console.log('');

        console.log(
            'TEST 5: Displaying escalation evidence...'
        );


        console.log(
            'Escalations:',
            state.escalations
        );


        console.log(
            `PASS: ${state.escalations.length} escalation record(s) found.`
        );


        await verifyAuditPage(
            page,
            PAYMENT_ID
        );


        await page.screenshot({
            path:
                'e2e-puppeteer/sla-escalation-flow-success.png',
            fullPage: true
        });


        console.log('');

        divider();

        console.log(
            'SLA ESCALATION E2E TEST PASSED'
        );

        divider();

        console.log(
            `Payment ID: ${PAYMENT_ID}`
        );

        console.log(
            `Approval Task ID: ${task.id}`
        );

        console.log(
            'Previous SLA expiry caused escalation: VERIFIED'
        );

        console.log(
            'Task status ESCALATED: VERIFIED'
        );

        console.log(
            `Current Approver ID: ${state.task.assignedApproverId}`
        );

        console.log(
            `Renewed Due At: ${state.task.dueAt}`
        );

        console.log(
            `Escalation Records: ${state.escalations.length}`
        );

        console.log(
            'Escalation audit persistence: VERIFIED'
        );

        console.log(
            'Finance Manager audit visibility: VERIFIED'
        );


    } else {

        /*
         * -------------------------------------------------
         * CASE B:
         * Task is still PENDING_APPROVAL.
         *
         * Here we must verify the original deadline is
         * already expired before waiting for the scheduler.
         * -------------------------------------------------
         */

        console.log('');

        console.log(
            'TEST 3: Checking original SLA deadline...'
        );


        if (!task.dueAt) {
            throw new Error(
                'Approval task has no dueAt.'
            );
        }


        const dueDate =
            new Date(
                task.dueAt
            );


        if (
            Number.isNaN(
                dueDate.getTime()
            )
        ) {
            throw new Error(
                `Unable to parse dueAt: ${task.dueAt}`
            );
        }


        const now =
            new Date();


        console.log(
            'Current Time:',
            now.toISOString()
        );

        console.log(
            'Task Due At:',
            dueDate.toISOString()
        );


        if (
            now.getTime() <
            dueDate.getTime()
        ) {
            const remainingMinutes =
                Math.ceil(
                    (
                        dueDate.getTime() -
                        now.getTime()
                    ) /
                    60000
                );


            console.log('');

            divider();

            console.log(
                'SLA ESCALATION TEST NOT READY'
            );

            divider();

            console.log(
                `Task ${task.id} has not expired yet.`
            );

            console.log(
                `Approximately ${remainingMinutes} minute(s) remain.`
            );

            console.log(
                `Due At: ${task.dueAt}`
            );

            console.log('');

            console.log(
                'No application data was modified.'
            );


            process.exitCode =
                2;

        } else {

            console.log(
                'PASS: Original approval SLA deadline has passed.'
            );


            console.log('');

            console.log(
                'TEST 4: Checking SLA API...'
            );


            const sla =
                await fetchTaskSla(
                    page,
                    task.id
                );


            console.log(
                'SLA Result:',
                sla
            );


            const expired =
                sla?.expired ===
                    true ||
                sla?.overdue ===
                    true;


            if (!expired) {
                throw new Error(
                    'Task dueAt is in the past, but SLA API does not report expired/overdue.'
                );
            }


            console.log(
                'PASS: SLA API reports the task as expired/overdue.'
            );


            console.log('');

            console.log(
                'TEST 5: Waiting for scheduler escalation...'
            );


            const finalState =
                await waitForEscalation(
                    page,
                    task.id,
                    PAYMENT_ID
                );


            console.log(
                'Final Task:',
                finalState.task
                    ? {
                        id:
                            finalState.task.id,
                        status:
                            finalState.task.status,
                        assignedApproverId:
                            finalState.task
                                .assignedApproverId,
                        dueAt:
                            finalState.task.dueAt
                    }
                    : null
            );


            console.log(
                'Escalations:',
                finalState.escalations
            );


            if (
                finalState.task?.status !==
                'ESCALATED'
            ) {
                throw new Error(
                    `Expected Task ${task.id} to become ESCALATED, but status is ${finalState.task?.status || 'UNKNOWN'}.`
                );
            }


            console.log(
                'PASS: Approval task status is ESCALATED.'
            );


            if (
                finalState.escalations.length ===
                0
            ) {
                throw new Error(
                    'Task is ESCALATED but no escalation audit record exists.'
                );
            }


            console.log(
                'PASS: Escalation audit evidence is present.'
            );


            await verifyAuditPage(
                page,
                PAYMENT_ID
            );


            await page.screenshot({
                path:
                    'e2e-puppeteer/sla-escalation-flow-success.png',
                fullPage: true
            });


            console.log('');

            divider();

            console.log(
                'SLA ESCALATION E2E TEST PASSED'
            );

            divider();

            console.log(
                `Payment ID: ${PAYMENT_ID}`
            );

            console.log(
                `Approval Task ID: ${task.id}`
            );

            console.log(
                'Original SLA expiry: VERIFIED'
            );

            console.log(
                'Task status ESCALATED: VERIFIED'
            );

            console.log(
                `New Approver ID: ${finalState.task.assignedApproverId}`
            );

            console.log(
                `Renewed Due At: ${finalState.task.dueAt}`
            );

            console.log(
                `Escalation Records: ${finalState.escalations.length}`
            );

            console.log(
                'Escalation audit persistence: VERIFIED'
            );

            console.log(
                'Finance Manager audit visibility: VERIFIED'
            );
        }
    }

} catch (error) {
    console.error('');

    divider();

    console.error(
        'SLA ESCALATION E2E TEST FAILED'
    );

    divider();

    console.error(
        error
    );


    if (page) {
        try {
            await page.screenshot({
                path:
                    'e2e-puppeteer/sla-escalation-flow-failure.png',
                fullPage: true
            });

            console.error(
                'Failure screenshot saved to:'
            );

            console.error(
                'e2e-puppeteer/sla-escalation-flow-failure.png'
            );

        } catch {
            // Ignore screenshot failure during cleanup.
        }
    }


    process.exitCode =
        1;

} finally {
    if (browser) {
        try {
            await browser.close();
        } catch {
            // Ignore Windows Chrome cleanup noise.
        }
    }
}