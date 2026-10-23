import puppeteer from 'puppeteer';

const APP_URL =
    process.env.E2E_APP_URL ||
    'http://localhost:4173';

const username =
    process.env.E2E_USERNAME;

const password =
    process.env.E2E_PASSWORD;

if (!username || !password) {

    console.error(
        'Missing E2E_USERNAME or E2E_PASSWORD.'
    );

    console.error(
        'Use synthetic/test credentials through environment variables only.'
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

try {

    console.log('');
    console.log(
        '=========================================='
    );

    console.log(
        'FULL AUTHENTICATION E2E TEST'
    );

    console.log(
        '=========================================='
    );


    /*
     * TEST 1
     * Open login page.
     */
    console.log('');
    console.log(
        'TEST 1: Opening login page...'
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

    console.log(
        'PASS: Login page loaded.'
    );


    /*
     * TEST 2
     * Login using test credentials.
     */
    console.log('');
    console.log(
        'TEST 2: Entering credentials...'
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

    await page.waitForFunction(
        () =>
            window.location.pathname ===
            '/verify-otp',
        {
            timeout: 15000
        }
    );

    console.log(
        'PASS: Login accepted and MFA started.'
    );


    /*
     * TEST 3
     * Verify that AuthContext stored
     * the MFA challenge correctly.
     */
    console.log('');
    console.log(
        'TEST 3: Checking MFA challenge state...'
    );

    const mfaState =
        await page.evaluate(
            () => ({
                challengeExists:
                    Boolean(
                        sessionStorage.getItem(
                            'mfaChallengeId'
                        )
                    ),

                usernameExists:
                    Boolean(
                        sessionStorage.getItem(
                            'mfaUsername'
                        )
                    ),

                accessTokenExists:
                    Boolean(
                        sessionStorage.getItem(
                            'accessToken'
                        )
                    )
            })
        );

    if (
        !mfaState.challengeExists
    ) {

        throw new Error(
            'MFA challenge ID was not stored.'
        );
    }

    if (
        !mfaState.usernameExists
    ) {

        throw new Error(
            'Pending MFA username was not stored.'
        );
    }

    if (
        mfaState.accessTokenExists
    ) {

        throw new Error(
            'Access token exists before OTP verification.'
        );
    }

    console.log(
        'PASS: MFA challenge is stored.'
    );

    console.log(
        'PASS: No access token exists before OTP verification.'
    );


    /*
     * TEST 4
     * Wait for the real OTP to be entered manually.
     *
     * Do not hard-code OTPs into E2E source code.
     */
    console.log('');
    console.log(
        'TEST 4: Manual OTP verification required.'
    );

    console.log('');
    console.log(
        '>>> Look at the opened Chrome window.'
    );

    console.log(
        '>>> Enter the current 6-digit OTP from your'
    );

    console.log(
        '>>> security-service console and click "Verify OTP".'
    );

    console.log('');
    console.log(
        'Waiting for successful authentication...'
    );


    /*
     * Allow up to 3 minutes for manual OTP entry.
     */
    await page.waitForFunction(
        () =>
            window.location.pathname === '/',
        {
            timeout: 180000,
            polling: 250
        }
    );

    console.log(
        'PASS: OTP accepted.'
    );

    console.log(
        'PASS: Application navigated to /.'
    );


    /*
     * TEST 5
     * Verify authenticated session state.
     */
    console.log('');
    console.log(
        'TEST 5: Checking authenticated session...'
    );

    const authenticatedState =
        await page.evaluate(
            () => ({
                accessTokenExists:
                    Boolean(
                        sessionStorage.getItem(
                            'accessToken'
                        )
                    ),

                challengeExists:
                    Boolean(
                        sessionStorage.getItem(
                            'mfaChallengeId'
                        )
                    ),

                pendingUsernameExists:
                    Boolean(
                        sessionStorage.getItem(
                            'mfaUsername'
                        )
                    )
            })
        );

    if (
        !authenticatedState.accessTokenExists
    ) {

        throw new Error(
            'Access token was not stored after OTP verification.'
        );
    }

    if (
        authenticatedState.challengeExists
    ) {

        throw new Error(
            'MFA challenge was not removed after successful authentication.'
        );
    }

    if (
        authenticatedState.pendingUsernameExists
    ) {

        throw new Error(
            'Pending MFA username was not removed after authentication.'
        );
    }

    console.log(
        'PASS: Authenticated session contains an access token.'
    );

    console.log(
        'PASS: MFA challenge was cleared.'
    );

    console.log(
        'PASS: Pending MFA username was cleared.'
    );


    /*
     * TEST 6
     * Refresh and make sure the authenticated
     * application can restore the session using
     * the accessToken from sessionStorage.
     */
    console.log('');
    console.log(
        'TEST 6: Refreshing authenticated application...'
    );

    await page.reload({
        waitUntil: 'networkidle2',
        timeout: 30000
    });

    await page.waitForFunction(
        () =>
            window.location.pathname !==
            '/login',
        {
            timeout: 15000
        }
    );

    const tokenAfterRefresh =
        await page.evaluate(
            () =>
                Boolean(
                    sessionStorage.getItem(
                        'accessToken'
                    )
                )
        );

    if (
        !tokenAfterRefresh
    ) {

        throw new Error(
            'Authenticated session was lost after page refresh.'
        );
    }

    console.log(
        'PASS: Session survived page refresh.'
    );

    console.log(
        'PASS: AuthContext restored authentication.'
    );


    /*
     * Store success screenshot.
     */
    await page.screenshot({
        path:
            'e2e-puppeteer/authenticated-flow-success.png',
        fullPage: true
    });

    console.log('');
    console.log(
        '=========================================='
    );

    console.log(
        'FULL AUTHENTICATION E2E TEST PASSED'
    );

    console.log(
        '=========================================='
    );

    console.log('');
    console.log(
        'Screenshot:'
    );

    console.log(
        'e2e-puppeteer/authenticated-flow-success.png'
    );

} catch (error) {

    console.error('');
    console.error(
        '=========================================='
    );

    console.error(
        'FULL AUTHENTICATION E2E TEST FAILED'
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
                'e2e-puppeteer/authenticated-flow-failure.png',
            fullPage: true
        });

        console.error(
            'Failure screenshot saved to:'
        );

        console.error(
            'e2e-puppeteer/authenticated-flow-failure.png'
        );

    } catch (screenshotError) {

        console.error(
            'Could not save failure screenshot.'
        );
    }

    process.exitCode = 1;

} finally {

    await browser.close();
}