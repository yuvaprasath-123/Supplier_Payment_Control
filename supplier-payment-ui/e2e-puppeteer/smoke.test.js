import puppeteer from 'puppeteer';

const APP_URL = 'http://localhost:4173';

const browser = await puppeteer.launch({
    headless: false,
    slowMo: 100
});

const page = await browser.newPage();

try {
    await page.goto(APP_URL, {
        waitUntil: 'networkidle2',
        timeout: 30000
    });

    console.log('Page title:', await page.title());

    const bodyText = await page.$eval(
        'body',
        (body) => body.innerText
    );

    console.log('Application loaded successfully');
    console.log('Body preview:');
    console.log(bodyText.substring(0, 500));
} catch (error) {
    console.error('Smoke test failed:');
    console.error(error);
    process.exitCode = 1;
} finally {
    await browser.close();
}