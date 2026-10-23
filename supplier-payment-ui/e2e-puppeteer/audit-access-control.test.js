import puppeteer from 'puppeteer';

const APP_URL =
    process.env.E2E_APP_URL ||
    'http://localhost:4173';

const requesterUsername =
    process.env.E2E_USERNAME;

const requesterPassword =
    process.env.E2E_PASSWORD;

const approverUsername =
    process.env.E2E_APPROVER_USERNAME;

const approverPassword =
    process.env.E2E_APPROVER_PASSWORD;

if (
    !requesterUsername ||
    !requesterPassword ||
    !approverUsername ||
    !approverPassword
) {
    console.error(
        'Missing requester or approver E2E credentials.'
    );

    console.error(
        'Required: E2E_USERNAME, E2E_PASSWORD, E2E_APPROVER_USERNAME, E2E_APPROVER_PASSWORD.'
    );

    process.exit(1);
}


async function createBrowser() {
    return puppeteer.launch({
        headless: false,
        slowMo: 100,
        defaultViewport: null,
        args: ['--start-maximized']
    });
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


async function authenticate(
    page,
    username,
    password,
    roleLabel
) {
    console.log('');
    console.log(
        '=========================================='
    );

    console.log(
        `${roleLabel} AUTHENTICATION`
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
        page,
        '/verify-otp'
    );

    console.log(
        'PASS: MFA challenge started.'
    );

    console.log('');
    console.log(
        `>>> Enter the OTP for ${roleLabel}`
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
        `PASS: ${roleLabel} authentication completed.`
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
}


async function verifyAuditAccessBlocked(
    page,
    expectedRole,
    label
) {
    console.log('');
    console.log(
        `${label} TEST 1: Verifying authenticated role...`
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
            expectedRole
        )
    ) {
        throw new Error(
            `${label} account does not have expected role ${expectedRole}.`
        );
    }

    if (
        user.roles.includes(
            'FINANCE_MANAGER'
        )
    ) {
        throw new Error(
            `${label} account unexpectedly has FINANCE_MANAGER role.`
        );
    }

    console.log(
        `PASS: ${expectedRole} role confirmed without FINANCE_MANAGER.`
    );


    /*
     * Navbar visibility
     */
    console.log('');
    console.log(
        `${label} TEST 2: Checking Payment Audit navigation visibility...`
    );

    const paymentAuditVisible =
        await page.evaluate(
            () => {
                return Array
                    .from(
                        document.querySelectorAll(
                            'a'
                        )
                    )
                    .some(
                        link =>
                            link.textContent
                                .trim() ===
                            'Payment Audit'
                    );
            }
        );

    if (
        paymentAuditVisible
    ) {
        throw new Error(
            `SECURITY FAILURE: ${label} can see Payment Audit navigation.`
        );
    }

    console.log(
        'PASS: Payment Audit navigation is hidden.'
    );


    /*
     * Direct list route
     */
    console.log('');
    console.log(
        `${label} TEST 3: Attempting direct /audit/payments access...`
    );

    await page.goto(
        `${APP_URL}/audit/payments`,
        {
            waitUntil: 'networkidle2',
            timeout: 30000
        }
    );

    await new Promise(
        resolve =>
            setTimeout(
                resolve,
                800
            )
    );

    const listRouteResult =
        await page.evaluate(
            () => ({
                pathname:
                    window.location.pathname,
                bodyText:
                    document.body.innerText
            })
        );

    console.log(
        'Route result:',
        {
            pathname:
                listRouteResult.pathname
        }
    );

    if (
        listRouteResult.pathname ===
        '/audit/payments'
    ) {
        if (
            listRouteResult.bodyText.includes(
                'Finance Manager Payment Audit'
            )
        ) {
            throw new Error(
                `SECURITY FAILURE: ${label} can access Finance Manager Payment Audit page directly.`
            );
        }

        throw new Error(
            `${label} remained on /audit/payments instead of being redirected or blocked.`
        );
    }

    console.log(
        `PASS: Direct Payment Audit access blocked. Redirected to ${listRouteResult.pathname}.`
    );


    /*
     * Direct details route
     */
    console.log('');
    console.log(
        `${label} TEST 4: Attempting direct audit-details access...`
    );

    await page.goto(
        `${APP_URL}/audit/payments/311`,
        {
            waitUntil: 'networkidle2',
            timeout: 30000
        }
    );

    await new Promise(
        resolve =>
            setTimeout(
                resolve,
                800
            )
    );

    const detailsRouteResult =
        await page.evaluate(
            () => ({
                pathname:
                    window.location.pathname,
                bodyText:
                    document.body.innerText
            })
        );

    console.log(
        'Route result:',
        {
            pathname:
                detailsRouteResult.pathname
        }
    );

    if (
        detailsRouteResult.pathname ===
        '/audit/payments/311'
    ) {
        if (
            detailsRouteResult.bodyText.includes(
                'Payment Audit #311'
            )
        ) {
            throw new Error(
                `SECURITY FAILURE: ${label} can access Finance Manager audit details directly.`
            );
        }

        throw new Error(
            `${label} remained on the restricted audit-details route.`
        );
    }

    console.log(
        `PASS: Direct audit-details access blocked. Redirected to ${detailsRouteResult.pathname}.`
    );


    console.log('');
    console.log(
        `${label} ACCESS CONTROL PASSED`
    );
}


let requesterBrowser = null;
let approverBrowser = null;

try {
    console.log('');
    console.log(
        '=========================================='
    );

    console.log(
        'PAYMENT AUDIT ACCESS CONTROL E2E TEST'
    );

    console.log(
        '=========================================='
    );


    /*
     * PART 1
     * PAYMENT_REQUESTER
     */
    requesterBrowser =
        await createBrowser();

    const requesterPage =
        await requesterBrowser.newPage();

    await authenticate(
        requesterPage,
        requesterUsername,
        requesterPassword,
        'PAYMENT REQUESTER'
    );

    await verifyAuditAccessBlocked(
        requesterPage,
        'PAYMENT_REQUESTER',
        'PAYMENT REQUESTER'
    );

    await requesterPage.screenshot({
        path:
            'e2e-puppeteer/audit-access-requester-success.png',
        fullPage: true
    });

    await requesterBrowser.close();

    requesterBrowser = null;


    /*
     * PART 2
     * FINANCE_APPROVER
     */
    approverBrowser =
        await createBrowser();

    const approverPage =
        await approverBrowser.newPage();

    await authenticate(
        approverPage,
        approverUsername,
        approverPassword,
        'FINANCE APPROVER'
    );

    await verifyAuditAccessBlocked(
        approverPage,
        'FINANCE_APPROVER',
        'FINANCE APPROVER'
    );

    await approverPage.screenshot({
        path:
            'e2e-puppeteer/audit-access-approver-success.png',
        fullPage: true
    });


    console.log('');
    console.log(
        '=========================================='
    );

    console.log(
        'PAYMENT AUDIT ACCESS CONTROL E2E TEST PASSED'
    );

    console.log(
        '=========================================='
    );

    console.log(
        'PAYMENT_REQUESTER access: BLOCKED'
    );

    console.log(
        'FINANCE_APPROVER access: BLOCKED'
    );

    console.log(
        'FINANCE_MANAGER-only audit boundary: VERIFIED'
    );

} catch (error) {
    console.error('');
    console.error(
        '=========================================='
    );

    console.error(
        'PAYMENT AUDIT ACCESS CONTROL E2E TEST FAILED'
    );

    console.error(
        '=========================================='
    );

    console.error(
        error
    );

    process.exitCode = 1;

} finally {
    if (
        requesterBrowser
    ) {
        try {
            await requesterBrowser.close();
        } catch {
            // Ignore browser cleanup noise.
        }
    }

    if (
        approverBrowser
    ) {
        try {
            await approverBrowser.close();
        } catch {
            // Ignore browser cleanup noise.
        }
    }
}