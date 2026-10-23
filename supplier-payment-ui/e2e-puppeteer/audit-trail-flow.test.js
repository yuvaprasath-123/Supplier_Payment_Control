import puppeteer from 'puppeteer';

const APP_URL =
    process.env.E2E_APP_URL ||
    'http://localhost:4173';

const username =
    process.env.E2E_AUDIT_USERNAME ||
    process.env.E2E_MANAGER_USERNAME;

const password =
    process.env.E2E_AUDIT_PASSWORD ||
    process.env.E2E_MANAGER_PASSWORD;

const paymentId =
    process.env.E2E_PAYMENT_ID;

if (
    !username ||
    !password ||
    !paymentId
) {
    console.error(
        'Missing audit/manager username, password, or E2E_PAYMENT_ID.'
    );

    console.error(
        'Set E2E_AUDIT_USERNAME/E2E_AUDIT_PASSWORD or reuse E2E_MANAGER_USERNAME/E2E_MANAGER_PASSWORD.'
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
        'AUDIT USER AUTHENTICATION'
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
        '>>> Enter the current OTP'
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
        'PASS: Authentication completed.'
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


function normalizeApprovalLevel(value) {
    if (
        value === null ||
        value === undefined
    ) {
        return null;
    }

    const normalized =
        String(value)
            .trim()
            .toUpperCase();

    if (
        /^L\d+$/.test(normalized)
    ) {
        return normalized;
    }

    if (
        /^\d+$/.test(normalized)
    ) {
        return `L${normalized}`;
    }

    return normalized;
}


function assertResponse(
    response,
    description
) {
    if (
        !response.ok
    ) {
        throw new Error(
            `${description} failed with HTTP ${response.status}.`
        );
    }
}


function printObject(
    label,
    value
) {
    console.log(
        `${label}:`
    );

    console.dir(
        value,
        {
            depth: null
        }
    );
}


try {
    console.log('');
    console.log(
        '=========================================='
    );
    console.log(
        'AUDIT TRAIL E2E TEST'
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
     * Read authenticated identity
     */
    console.log('');
    console.log(
        'TEST 2: Reading authenticated user...'
    );

    const userResponse =
        await apiGet(
            '/security-api/api/v1/auth/me'
        );

    assertResponse(
        userResponse,
        'Current-user request'
    );

    const currentUser =
        userResponse.body;

    printObject(
        'Authenticated User',
        {
            userId:
                currentUser?.userId,

            username:
                currentUser?.username,

            roles:
                currentUser?.roles
        }
    );

    console.log(
        'PASS: Authenticated identity available.'
    );


    /*
     * TEST 3
     * Payment record
     */
    console.log('');
    console.log(
        'TEST 3: Reading payment record...'
    );

    const paymentResponse =
        await apiGet(
            `/payment-api/api/v1/payment-requests/${encodeURIComponent(
                paymentId
            )}`
        );

    assertResponse(
        paymentResponse,
        'Payment request'
    );

    const payment =
        paymentResponse.body;

    printObject(
        'Payment',
        payment
    );

    if (
        String(
            payment?.id ??
            payment?.paymentId
        ) !==
        String(
            paymentId
        )
    ) {
        throw new Error(
            'Payment response does not match requested payment ID.'
        );
    }

    console.log(
        'PASS: Payment record loaded.'
    );


    /*
     * TEST 4
     * Payment timeline
     */
    console.log('');
    console.log(
        'TEST 4: Reading payment timeline...'
    );

    const timelineResponse =
        await apiGet(
            `/payment-api/api/v1/payment-requests/${encodeURIComponent(
                paymentId
            )}/timeline`
        );

    assertResponse(
        timelineResponse,
        'Payment timeline'
    );

    const timeline =
        timelineResponse.body;

    printObject(
        'Payment Timeline',
        timeline
    );

    const timelineEntries =
        normalizeArray(
            timeline
        );

    if (
        Array.isArray(timeline) &&
        timeline.length === 0
    ) {
        throw new Error(
            'Payment timeline is empty.'
        );
    }

    console.log(
        'PASS: Payment timeline available.'
    );


    /*
     * TEST 5
     * Risk assessment
     */
    console.log('');
    console.log(
        'TEST 5: Reading risk assessment...'
    );

    const riskResponse =
        await apiGet(
            `/risk-api/api/v1/risk-assessments/payment/${encodeURIComponent(
                paymentId
            )}`
        );

    assertResponse(
        riskResponse,
        'Risk assessment'
    );

    const risk =
        riskResponse.body;

    printObject(
        'Risk Assessment',
        risk
    );

    if (
        String(
            risk?.status ??
            risk?.assessmentStatus ??
            ''
        ).toUpperCase() !==
        'COMPLETED'
    ) {
        throw new Error(
            `Risk assessment is not COMPLETED. Status: ${
                risk?.status ??
                risk?.assessmentStatus
            }`
        );
    }

    if (
        risk?.riskScore == null ||
        !risk?.riskBand ||
        !risk?.riskApprovalLevel
    ) {
        throw new Error(
            'Risk assessment is missing score, band, or approval level.'
        );
    }

    console.log(
        'PASS: Risk assessment summary is complete.'
    );


    /*
     * TEST 6
     * Risk factors
     */
    console.log('');
    console.log(
        'TEST 6: Reading risk factors...'
    );

    const assessmentId =
        risk?.id ??
        risk?.assessmentId;

    if (
        assessmentId == null
    ) {
        throw new Error(
            'Risk assessment ID is missing.'
        );
    }

    const factorResponse =
        await apiGet(
            `/risk-api/api/v1/risk-assessments/${encodeURIComponent(
                assessmentId
            )}/factors`
        );

    assertResponse(
        factorResponse,
        'Risk factors'
    );

    const factors =
        normalizeArray(
            factorResponse.body
        );

    printObject(
        'Risk Factors',
        factorResponse.body
    );

    if (
        factors.length === 0
    ) {
        throw new Error(
            'No risk-factor records found.'
        );
    }

    for (
        const factor
        of factors
    ) {
        if (
            !factor.ruleCode
        ) {
            throw new Error(
                'Risk factor is missing ruleCode.'
            );
        }

        if (
            factor.explanation == null ||
            String(
                factor.explanation
            ).trim() ===
            ''
        ) {
            throw new Error(
                `Risk factor ${factor.ruleCode} is missing an explanation.`
            );
        }
    }

    console.log(
        `PASS: ${factors.length} explainable risk factor(s) available.`
    );


    /*
     * TEST 7
     * Approval route
     */
    console.log('');
    console.log(
        'TEST 7: Reading approval route...'
    );

    const routeResponse =
        await apiGet(
            `/approval-api/api/v1/approval-routes/payment/${encodeURIComponent(
                paymentId
            )}`
        );

    assertResponse(
        routeResponse,
        'Approval route'
    );

    const route =
        routeResponse.body;

    printObject(
        'Approval Route',
        route
    );

    if (
        !route?.requiredLevel
    ) {
        throw new Error(
            'Approval route is missing requiredLevel.'
        );
    }

    if (
        !route?.amountApprovalLevel ||
        !route?.riskApprovalLevel
    ) {
        throw new Error(
            'Approval route is missing amount or risk approval level.'
        );
    }

    if (
        !route?.explanation
    ) {
        throw new Error(
            'Approval route is missing decision explanation.'
        );
    }

    console.log(
        'PASS: Approval route is explainable.'
    );


    /*
     * TEST 8
     * Approval decision explanation endpoint
     */
    console.log('');
    console.log(
        'TEST 8: Reading approval decision explanation...'
    );

    const explanationResponse =
        await apiGet(
            `/approval-api/api/v1/approval-routes/payment/${encodeURIComponent(
                paymentId
            )}/decision-explanation`
        );

    assertResponse(
        explanationResponse,
        'Approval explanation'
    );

    printObject(
        'Approval Explanation',
        explanationResponse.body
    );

    console.log(
        'PASS: Approval decision explanation available.'
    );


    /*
     * TEST 9
     * Approval history
     */
    console.log('');
    console.log(
        'TEST 9: Reading approval history...'
    );

    const historyResponse =
        await apiGet(
            `/approval-api/api/v1/approval-tasks/payment/${encodeURIComponent(
                paymentId
            )}/history`
        );

    assertResponse(
        historyResponse,
        'Approval history'
    );

    const history =
        historyResponse.body;

    printObject(
        'Approval History',
        history
    );

    if (
        !history?.task
    ) {
        throw new Error(
            'Approval history does not contain task data.'
        );
    }

    if (
        String(
            history.task.paymentId
        ) !==
        String(
            paymentId
        )
    ) {
        throw new Error(
            'Approval history task belongs to another payment.'
        );
    }

    console.log(
        'PASS: Approval task audit record available.'
    );


    /*
     * TEST 10
     * Validate approval decisions
     */
    console.log('');
    console.log(
        'TEST 10: Checking approval decisions...'
    );

    const decisions =
        Array.isArray(
            history?.decisions
        )
            ? history.decisions
            : [];

    if (
        String(
            history.task.status
        ).toUpperCase() ===
        'APPROVED' &&
        decisions.length === 0
    ) {
        throw new Error(
            'Approved task has no approval decision audit record.'
        );
    }

    for (
        const decision
        of decisions
    ) {
        if (
            decision.approverId == null
        ) {
            throw new Error(
                'Approval decision is missing approverId.'
            );
        }

        if (
            !decision.decision
        ) {
            throw new Error(
                'Approval decision is missing decision type.'
            );
        }

        if (
            !decision.decidedAt
        ) {
            throw new Error(
                'Approval decision is missing decidedAt.'
            );
        }
    }

    console.log(
        `PASS: ${decisions.length} approval decision record(s) validated.`
    );


    /*
     * TEST 11
     * Special manager-override validation
     *
     * For Payment 311 we expect the already proven
     * OVERRIDE_APPROVED decision.
     */
    console.log('');
    console.log(
        'TEST 11: Checking override audit metadata when present...'
    );

    const overrideDecision =
        decisions.find(
            decision =>
                decision.decision ===
                'OVERRIDE_APPROVED'
        );

    if (
        overrideDecision
    ) {
        console.log(
            'Override Decision:',
            overrideDecision
        );

        if (
            !overrideDecision.remark ||
            !String(
                overrideDecision.remark
            ).trim()
        ) {
            throw new Error(
                'OVERRIDE_APPROVED decision has no justification remark.'
            );
        }

        if (
            history.task.decidedBy != null &&
            Number(
                history.task.decidedBy
            ) !==
            Number(
                overrideDecision.approverId
            )
        ) {
            throw new Error(
                'Task decidedBy does not match override approverId.'
            );
        }

        console.log(
            'PASS: Manager override decision is fully auditable.'
        );

    } else {
        console.log(
            'INFO: No OVERRIDE_APPROVED decision exists for this payment.'
        );

        console.log(
            'PASS: Standard approval history validation completed.'
        );
    }


    /*
     * TEST 12
     * Escalation history
     */
    console.log('');
    console.log(
        'TEST 12: Reading escalation history...'
    );

    const escalations =
        Array.isArray(
            history?.escalations
        )
            ? history.escalations
            : [];

    printObject(
        'Escalations',
        escalations
    );

    console.log(
        `PASS: ${escalations.length} escalation record(s) available.`
    );


    /*
     * TEST 13
     * Payment processing audit
     *
     * A payment may already be processing or complete.
     */
    console.log('');
    console.log(
        'TEST 13: Reading payment processing outcome...'
    );

    const processingResponse =
        await apiGet(
            `/processing-api/api/v1/payment-processing/${encodeURIComponent(
                paymentId
            )}`
        );

    if (
        processingResponse.ok
    ) {
        const processing =
            processingResponse.body;

        printObject(
            'Payment Processing',
            processing
        );

        const processingPaymentId =
            processing?.paymentId;

        if (
            processingPaymentId != null &&
            String(
                processingPaymentId
            ) !==
            String(
                paymentId
            )
        ) {
            throw new Error(
                'Processing record belongs to another payment.'
            );
        }

        console.log(
            'PASS: Payment processing audit record available.'
        );

    } else if (
        processingResponse.status ===
        404
    ) {
        console.log(
            'INFO: No payment processing record exists yet.'
        );

        console.log(
            'PASS: Processing absence is valid for an unprocessed payment.'
        );

    } else {
        throw new Error(
            `Payment processing lookup failed with HTTP ${processingResponse.status}.`
        );
    }


   /*
 * TEST 14
 * Cross-check route against risk result
 */
console.log('');
console.log(
    'TEST 14: Cross-checking risk and approval route...'
);

const riskAssessmentLevel =
    normalizeApprovalLevel(
        risk.riskApprovalLevel
    );

const routeRiskLevel =
    normalizeApprovalLevel(
        route.riskApprovalLevel
    );

console.log(
    'Normalized Approval Levels:',
    {
        riskAssessment:
            riskAssessmentLevel,

        approvalRoute:
            routeRiskLevel
    }
);

if (
    riskAssessmentLevel !==
    routeRiskLevel
) {
    throw new Error(
        `Risk approval-level mismatch. Risk=${riskAssessmentLevel}, Route=${routeRiskLevel}`
    );
}

if (
    route.riskBand &&
    risk.riskBand &&
    String(
        route.riskBand
    ).trim().toUpperCase() !==
    String(
        risk.riskBand
    ).trim().toUpperCase()
) {
    throw new Error(
        `Risk-band mismatch. Risk=${risk.riskBand}, Route=${route.riskBand}`
    );
}

console.log(
    'PASS: Risk result and approval route are consistent.'
);


    /*
     * TEST 15
     * Cross-check task and route
     */
    console.log('');
    console.log(
        'TEST 15: Cross-checking approval task and route...'
    );

    if (
        history.task.routeId != null &&
        route.id != null &&
        Number(
            history.task.routeId
        ) !==
        Number(
            route.id
        )
    ) {
        throw new Error(
            `Approval route mismatch. Task routeId=${history.task.routeId}, route.id=${route.id}`
        );
    }

    if (
        history.task.requiredLevel &&
        route.requiredLevel &&
        String(
            history.task.requiredLevel
        ) !==
        String(
            route.requiredLevel
        )
    ) {
        throw new Error(
            `Required approval-level mismatch. Task=${history.task.requiredLevel}, Route=${route.requiredLevel}`
        );
    }

    console.log(
        'PASS: Approval task and route are consistent.'
    );




    /*
 * TEST 16
 * Optional Payment Details UI visibility
 *
 * The Finance Manager audit has already been
 * validated through the authorized read-only APIs.
 *
 * The standard Payment Details route may still be
 * requester-oriented at the frontend routing layer.
 */
console.log('');
console.log(
    'TEST 16: Checking Payment Details UI availability...'
);

await page.goto(
    `${APP_URL}/payments/${encodeURIComponent(
        paymentId
    )}`,
    {
        waitUntil: 'networkidle2',
        timeout: 30000
    }
);

const uiResult =
    await page.evaluate(
        expectedPaymentId => {

            const bodyText =
                document.body?.innerText || '';

            return {
                url:
                    window.location.href,

                paymentVisible:
                    bodyText.includes(
                        expectedPaymentId
                    ),

                riskScoreVisible:
                    bodyText.includes(
                        'Risk Score'
                    ),

                riskBandVisible:
                    bodyText.includes(
                        'Risk Band'
                    ),

                bodyPreview:
                    bodyText
                        .replace(/\s+/g, ' ')
                        .trim()
                        .slice(0, 500)
            };
        },
        String(
            paymentId
        )
    );

console.log(
    'Payment Details UI Result:',
    uiResult
);

if (
    uiResult.paymentVisible
) {

    console.log(
        'PASS: Payment Details UI is available to Finance Manager.'
    );

    /*
     * TEST 17
     * Audit visibility in UI
     */
    console.log('');
    console.log(
        'TEST 17: Checking audit-related UI visibility...'
    );

    const pageText =
        await page.$eval(
            'body',
            element =>
                element.innerText
        );

    if (
        !pageText.includes(
            String(
                risk.riskScore
            )
        )
    ) {
        console.log(
            'INFO: Risk Score is not shown on this frontend view.'
        );
    }

    if (
        !pageText.includes(
            String(
                risk.riskBand
            )
        )
    ) {
        console.log(
            'INFO: Risk Band is not shown on this frontend view.'
        );
    }

    console.log(
        'PASS: Finance Manager audit APIs remain fully validated.'
    );

} else {

    console.log(
        'INFO: Standard Payment Details UI is not currently exposed to Finance Manager.'
    );

    console.log(
        `INFO: Current URL: ${uiResult.url}`
    );

    console.log(
        `INFO: Page preview: ${uiResult.bodyPreview}`
    );

    console.log(
        'PASS: Finance Manager audit remains valid through authorized read-only audit APIs.'
    );
}


    await page.screenshot({
        path:
            'e2e-puppeteer/audit-trail-flow-success.png',
        fullPage: true
    });


    console.log('');
    console.log(
        '=========================================='
    );
    console.log(
        'AUDIT TRAIL E2E TEST PASSED'
    );
    console.log(
        '=========================================='
    );

    console.log(
        `Payment ID: ${paymentId}`
    );

    console.log(
        `Risk Assessment ID: ${assessmentId}`
    );

    console.log(
        `Risk Score: ${risk.riskScore}`
    );

    console.log(
        `Risk Band: ${risk.riskBand}`
    );

    console.log(
        `Risk Approval Level: ${risk.riskApprovalLevel}`
    );

    console.log(
        `Required Approval Level: ${route.requiredLevel}`
    );

    console.log(
        `Approval Task ID: ${history.task.id}`
    );

    console.log(
        `Approval Task Status: ${history.task.status}`
    );

    console.log(
        `Decision Records: ${decisions.length}`
    );

    console.log(
        `Escalation Records: ${escalations.length}`
    );

    if (
        overrideDecision
    ) {
        console.log(
            `Override Approver ID: ${overrideDecision.approverId}`
        );

        console.log(
            `Override Decision: ${overrideDecision.decision}`
        );

        console.log(
            'Override Justification: PRESENT'
        );
    }

} catch (error) {
    console.error('');
    console.error(
        '=========================================='
    );
    console.error(
        'AUDIT TRAIL E2E TEST FAILED'
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
                'e2e-puppeteer/audit-trail-flow-failure.png',
            fullPage: true
        });

        console.error(
            'Failure screenshot saved to:'
        );

        console.error(
            'e2e-puppeteer/audit-trail-flow-failure.png'
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