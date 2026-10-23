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
        'Missing E2E_USERNAME or E2E_PASSWORD environment variable.'
    );

    console.error(
        'Use a synthetic/test account. Do not hard-code credentials in this file.'
    );

    process.exit(1);
}

const browser = await puppeteer.launch({
    headless: false,
    slowMo: 100
});

const page = await browser.newPage();

try {

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

    /*
     * Verify login controls exist.
     */
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

    await page.waitForSelector(
        'button[type="submit"]',
        {
            visible: true
        }
    );

    console.log(
        'PASS: Login page loaded.'
    );


    /*
     * Verify password input is actually protected.
     */
    const passwordType =
        await page.$eval(
            '#password',
            element => element.type
        );

    if (
        passwordType !== 'password'
    ) {
        throw new Error(
            'Password field is not using type="password".'
        );
    }

    console.log(
        'PASS: Password input is protected.'
    );


    /*
     * Enter the synthetic E2E credentials.
     */
    await page.type(
        '#username',
        username
    );

    await page.type(
        '#password',
        password
    );

    console.log(
        'TEST 2: Submitting login...'
    );

    await page.click(
        'button[type="submit"]'
    );


    /*
     * React Router performs client-side navigation,
     * so do not rely on waitForNavigation().
     *
     * Wait until the route becomes /verify-otp.
     */
    await page.waitForFunction(
        () =>
            window.location.pathname ===
            '/verify-otp',
        {
            timeout: 15000
        }
    );

    console.log(
        'PASS: Authentication started MFA.'
    );

    console.log(
        'PASS: Browser routed to /verify-otp.'
    );


    /*
     * Verify OTP page.
     */
    await page.waitForSelector(
        '#otp',
        {
            visible: true
        }
    );

    const heading =
        await page.$eval(
            'h1',
            element =>
                element.textContent.trim()
        );

    if (
        heading !== 'Verify OTP'
    ) {
        throw new Error(
            `Expected "Verify OTP" heading but found "${heading}".`
        );
    }

    console.log(
        'PASS: Verify OTP page displayed.'
    );


    /*
     * Verify OTP input security/validation attributes.
     */
    const otpProperties =
        await page.$eval(
            '#otp',
            element => ({
                maxLength:
                    element.maxLength,

                inputMode:
                    element.inputMode,

                autocomplete:
                    element.autocomplete
            })
        );

    if (
        otpProperties.maxLength !== 6
    ) {
        throw new Error(
            'OTP input does not have maxLength=6.'
        );
    }

    if (
        otpProperties.inputMode !==
        'numeric'
    ) {
        throw new Error(
            'OTP input does not use numeric input mode.'
        );
    }

    if (
        otpProperties.autocomplete !==
        'one-time-code'
    ) {
        throw new Error(
            'OTP input does not use one-time-code autocomplete.'
        );
    }

    console.log(
        'PASS: OTP field configuration is correct.'
    );


    /*
     * Verify OTP submit starts disabled because
     * no six-digit OTP has been entered.
     */
    const otpSubmitDisabled =
        await page.$eval(
            'button[type="submit"]',
            button =>
                button.disabled
        );

    if (
        !otpSubmitDisabled
    ) {
        throw new Error(
            'Verify OTP button should be disabled before 6 digits are entered.'
        );
    }

    console.log(
        'PASS: OTP submit is disabled for empty OTP.'
    );


    /*
     * Verify only numeric characters survive.
     *
     * Component logic removes non-digits and
     * limits the value to 6 characters.
     */
    await page.type(
        '#otp',
        '12ab345678'
    );

    const otpValue =
        await page.$eval(
            '#otp',
            element =>
                element.value
        );

    if (
        otpValue !== '123456'
    ) {
        throw new Error(
            `OTP sanitization failed. Expected 123456 but found ${otpValue}.`
        );
    }

    console.log(
        'PASS: OTP accepts digits only and limits input to 6 digits.'
    );


    /*
     * Six digits should enable Verify OTP.
     *
     * We deliberately DO NOT submit it here.
     * A real OTP must never be hard-coded in E2E source.
     */
    const enabledAfterSixDigits =
        await page.$eval(
            'button[type="submit"]',
            button =>
                !button.disabled
        );

    if (
        !enabledAfterSixDigits
    ) {
        throw new Error(
            'Verify OTP button was not enabled after six digits.'
        );
    }

    console.log(
        'PASS: OTP submit becomes enabled after 6 digits.'
    );

    console.log('');
    console.log(
        '======================================'
    );
    console.log(
        'LOGIN -> MFA E2E TEST PASSED'
    );
    console.log(
        '======================================'
    );

} catch (error) {

    console.error('');
    console.error(
        'LOGIN -> MFA E2E TEST FAILED'
    );

    console.error(
        error
    );

    /*
     * Keep a screenshot when something fails.
     */
    await page.screenshot({
        path:
            'e2e-puppeteer/login-otp-failure.png',
        fullPage: true
    });

    console.error(
        'Failure screenshot saved to:'
    );

    console.error(
        'e2e-puppeteer/login-otp-failure.png'
    );

    process.exitCode = 1;

} finally {

    await browser.close();
}