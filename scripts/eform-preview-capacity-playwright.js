/* Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
'use strict';
const assert = require('node:assert/strict');

/**
 * Checks the INSTALLED attachment UI. Busy responses are deliberately injected, not evidence
 * that the renderer was saturated. The successful final response must come from the real app.
 * Uses the caller's owned saved form, performs no clinical save/send action, and runs serially.
 */
async function checkPreviewCapacity(context, options) {
    const { appUrl, fdid, demographicNo } = options;
    const page = await context.newPage();
    const endpoint = new URL(appUrl('/previewDocs'));
    const expectedFailures = new WeakSet();
    const unexpected = [];
    const pageErrors = [];
    let mode = 'recover', attempts = 0;
    const isPreview = request => {
        const url = new URL(request.url());
        if (url.origin !== endpoint.origin || url.pathname !== endpoint.pathname || request.method() !== 'POST') return false;
        const fields = new URLSearchParams(request.postData() || '');
        return fields.get('method') === 'renderEFormPDF' && fields.get('eFormId') === String(fdid)
            && fields.get('demographicNo') === String(demographicNo);
    };
    page.on('response', response => {
        if (response.status() >= 400 && !expectedFailures.has(response.request())
                && !new URL(response.url()).pathname.endsWith('/favicon.ico')) {
            unexpected.push({ status: response.status(), pathname: new URL(response.url()).pathname });
        }
    });
    page.on('pageerror', error => pageErrors.push(error.message));
    page.on('dialog', dialog => {
        if (mode !== 'permanent') {
            pageErrors.push('Unexpected preview consent or error dialog');
            void dialog.dismiss();
        }
    });
    const routeMatcher = url => url.origin === endpoint.origin && url.pathname === endpoint.pathname;
    const intercept = async route => {
        if (!isPreview(route.request())) return route.continue();
        attempts++;
        if (mode === 'recover' && attempts > 2) return route.continue();
        expectedFailures.add(route.request());
        return route.fulfill({ status: 503, contentType: 'application/json',
            headers: { 'Retry-After': '2', 'Cache-Control': 'no-store' },
            body: JSON.stringify(mode === 'permanent'
                ? { errorCode: 'eform_render_failed', errorMessage: 'Injected permanent test failure.' }
                : { errorCode: 'eform_render_busy', errorMessage: 'Injected renderer capacity response.',
                    retryable: true, retryAfterSeconds: 2, renderApproval: null }) });
    };
    const startPreview = () => page.evaluate(({ fdid, demographicNo }) => {
        // Exercise the installed entry point against the owned saved form. The library selector
        // excludes the currently-open form to prevent self-attachment, so it has no own-row link.
        pdfCache = [];
        getPdf('EFORM', String(fdid), new URLSearchParams({
            method: 'renderEFormPDF', eFormId: String(fdid), demographicNo: String(demographicNo)
        }).toString());
    }, { fdid, demographicNo });
    try {
        await page.goto(appUrl(`/eform/efmshowform_data?fdid=${encodeURIComponent(fdid)}&parentAjaxId=eforms`),
            { waitUntil: 'domcontentloaded', timeout: 60000 });
        const attach = page.locator('[data-poload]');
        assert.equal(await attach.count(), 1, 'Expected one installed attachment-dialog entry point');
        await attach.click();
        await page.locator('#attachDocumentsForm').waitFor({ state: 'visible', timeout: 30000 });
        await page.waitForFunction(() => typeof CarlosDocumentPreviewRetry !== 'undefined'
            && typeof attachmentPreviewController !== 'undefined');
        await page.route(routeMatcher, intercept);

        const rendered = page.waitForResponse(response => isPreview(response.request()) && response.status() === 200,
            { timeout: 180000 });
        await startPreview();
        await page.locator('#preview-waiting').waitFor({ state: 'visible', timeout: 10000 });
        const realResponse = await rendered;
        const result = await realResponse.json();
        assert.equal(result.missingContent, undefined, 'Owned fixture unexpectedly needs clinical omission consent');
        assert.equal(typeof result.base64Data, 'string', 'Busy recovery did not return a genuine renderer PDF');
        assert.equal(Buffer.from(result.base64Data, 'base64').subarray(0, 5).toString('ascii'), '%PDF-');
        await page.waitForFunction(() => document.getElementById('pdfObject').src.startsWith('blob:')
            && !document.getElementById('pdfObject').classList.contains('d-none')
            && document.getElementById('preview-waiting').classList.contains('d-none'));
        assert.equal(attempts, 3, 'Expected exactly two injected busy responses then one real render');
        const cachedVisible = await page.evaluate(({ fdid, demographicNo }) => {
            getPdf('EFORM', String(fdid), new URLSearchParams({
                method: 'renderEFormPDF', eFormId: String(fdid), demographicNo: String(demographicNo)
            }).toString());
            return !document.getElementById('pdfObject').classList.contains('d-none')
                && document.getElementById('pdfObject').src.startsWith('blob:');
        }, { fdid, demographicNo });
        assert.equal(cachedVisible, true, 'A cached preview no longer displays immediately');

        mode = 'cancel'; attempts = 0;
        // Establish an actual previous PDF plus a visible advisory. The application must clear
        // both when starting the next uncached preview; the harness must not clear them for it.
        await page.evaluate(() => showAdvisory(2));
        await startPreview();
        await page.locator('#preview-waiting').waitFor({ state: 'visible', timeout: 10000 });
        const cleared = await page.evaluate(() => ({
            frameHidden: document.getElementById('pdfObject').classList.contains('d-none'),
            src: document.getElementById('pdfObject').getAttribute('src'),
            advisoryHidden: document.getElementById('preview-advisory').classList.contains('d-none'),
            advisoryText: document.getElementById('preview-advisory').textContent,
            blob: previewBlobUrl
        }));
        assert.deepEqual(cleared, { frameHidden: true, src: null, advisoryHidden: true, advisoryText: '', blob: null },
            'Busy preview exposed the previous attachment or its advisory');
        await page.locator('#preview-waiting button').click();
        await page.locator('#preview-waiting').waitFor({ state: 'hidden' });
        await page.waitForTimeout(3000); // Longer than the injected 2s delay plus maximum 0.5s jitter.
        assert.equal(attempts, 1, 'Cancelled preview continued polling');

        mode = 'permanent'; attempts = 0;
        const alert = page.waitForEvent('dialog', { timeout: 15000 });
        await startPreview();
        const dialog = await alert;
        const message = dialog.message();
        await dialog.accept();
        assert.match(message, /Injected permanent test failure/);
        await page.waitForTimeout(3000);
        assert.equal(attempts, 1, 'An untyped permanent503 was retried');
        assert.deepEqual(unexpected, [], 'Unexpected HTTP failures during preview capacity check');
        assert.deepEqual(pageErrors, [], 'Installed preview UI raised JavaScript exceptions');
        console.log('PASS installed eForm preview waits through two injected busy responses for a real PDF, cancels waiting, and refuses untyped503 retries');
    } finally {
        await page.unroute(routeMatcher, intercept);
        if (!page.isClosed()) {
            await page.evaluate(() => {
                if (typeof cancelAttachmentPreview === 'function') cancelAttachmentPreview();
            }).catch(() => {});
            await page.close({ runBeforeUnload: false });
        }
    }
}

module.exports = { checkPreviewCapacity };
