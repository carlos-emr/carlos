/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const {chromium} = require(process.env.CDS_BROWSER_PLAYWRIGHT);

async function main() {
    const options = JSON.parse(await fs.readFile(process.argv[2], 'utf8'));
    const browser = await chromium.launch({headless: true, args: ["--ignore-certificate-errors-spki-list=" + options.tlsSpki]});
    const context = await browser.newContext({acceptDownloads: true});
    const page = await context.newPage();
    page.setDefaultTimeout(45000);
    let stage = 'LOGIN';
    let acceptedRunId = null;
    try {
        await page.goto(options.base + '/index', {waitUntil: 'domcontentloaded'});
        await page.locator('#username').fill(options.username);
        await page.locator('#password').fill(options.password);
        if (await page.locator('#pin').isVisible()) await page.locator('#pin').fill(options.pin);
        await Promise.all([
            page.waitForURL(url => !['/carlos/index', '/carlos/login'].includes(url.pathname)),
            page.locator('form[name="loginForm"] input[type="submit"]').first().click()
        ]);
        stage = 'AUTHENTICATED_EXPORT_PAGE';
        await page.goto(options.base + '/form/cdsExport', {waitUntil: 'domcontentloaded'});
        await page.locator('#cdsExport').waitFor();
        assert.equal(await page.locator('#exportOptions').isDisabled(), false);
        await page.waitForFunction(() => document.querySelector('input[name="CSRF-TOKEN"]')?.value);
        assert.equal(await page.locator('#profile').inputValue(), 'omd-5.2');

        stage = 'METHOD_AND_CSRF_BOUNDARY';
        const get = await context.request.get(options.base + '/form/cdsExportSubmit?apiVersion=1');
        assert.equal(get.status(), 405);
        const tokenless = await context.request.post(options.base + '/form/cdsExportSubmit', {
            form: {apiVersion: '1', submissionKey: 'must-not-create-csrf-job'},
            headers: {'Origin': options.base.replace(/\/carlos$/, '')}
        });
        assert.equal(tokenless.status(), 403);

        stage = 'REAL_FORM_ACCEPTANCE';
        await page.locator('#providerNo').fill(options.provider);
        await page.locator('#facilityId').fill('');
        await page.locator('#selection').selectOption('SELECTED_PATIENTS');
        await page.locator('#patientNos').fill(String(options.patient));
        const wanted = new Set(['Demographics', 'PersonalHistory', 'FamilyHistory', 'PastHealth',
            'ProblemList', 'RiskFactors', 'AlertsAndSpecialNeeds', 'ClinicalNotes', 'CareElements', 'NewCategory']);
        const categories = page.locator('.export-category');
        for (let i = 0; i < await categories.count(); i++) {
            const category = categories.nth(i);
            await category.setChecked(wanted.has(await category.inputValue()));
        }
        await page.locator('#recipientId').fill('native');
        await page.locator('#auditPlanId').fill('native');
        const key = await page.locator('#submissionKey').inputValue();
        const responsePromise = page.waitForResponse(response =>
            response.url().includes('/form/cdsExportSubmit') && response.request().method() === 'POST');
        await page.locator('#submitExport').click();
        const response = await responsePromise;
        assert.equal(response.status(), 202);
        assert.equal(response.headers()['x-carlos-cds-export-api-version'], '1');
        const accepted = await response.json();
        assert.equal(accepted.ok, true);
        assert.equal(accepted.submissionKey, key);
        assert.equal(accepted.job.state, 'ACCEPTED');
        assert.equal(accepted.job.complete, false);
        assert.equal(accepted.job.artifactAvailable, false);
        assert.match(accepted.job.id, /^[0-9a-f-]{36}$/);
        const runId = accepted.job.id;
        acceptedRunId = runId;

        stage = 'RELOAD_AND_ASYNCHRONOUS_WORKER';
        await page.goto(options.base + '/form/cdsExport?runId=' + runId, {waitUntil: 'domcontentloaded'});
        await page.waitForFunction(() => {
            const state = document.querySelector('#exportState')?.textContent || '';
            return ['Transfer package ready', 'Review required — package unavailable',
                'Export stopped — package unavailable', 'Export cancelled', 'Transfer package expired'].includes(state);
        }, null, {timeout: 300000});
        stage = 'RELOADED_JOB_READY_STATE';
        assert.equal(await page.locator('#exportState').textContent(), 'Transfer package ready');
        stage = 'RELOADED_JOB_BINDING_AND_STATUS';
        assert.equal(await page.locator('#runId').inputValue(), runId);
        assert.equal(await page.locator('#downloadExport').isDisabled(), false);
        const statusResponse = await context.request.get(options.base + '/form/cdsExportStatus?apiVersion=1&runId=' + runId);
        assert.equal(statusResponse.status(), 200);
        const status = await statusResponse.json();
        assert.equal(status.job.complete, true);
        assert.equal(status.job.totalPatients, 1);
        assert.equal(status.job.processedPatients, 1);
        assert.equal(status.job.blockedPatients, 0);
        assert.equal(status.job.artifactAvailable, true);
        assert.equal(status.job.workerOwned, false);
        const recovered = await context.request.get(options.base + '/form/cdsExportStatus?apiVersion=1&submissionKey=' + encodeURIComponent(key));
        assert.equal(recovered.status(), 200);
        assert.equal((await recovered.json()).job.id, runId);

        stage = 'AUTHORIZED_FORM_DOWNLOAD';
        let timer;
        let onPage;
        let onDownload;
        const received = new Promise((resolve, reject) => {
            onDownload = resolve;
            onPage = popup => popup.once('download', resolve);
            context.on('page', onPage);
            page.once('download', onDownload);
            timer = setTimeout(() => reject(new Error('CDS_EXPORT_BROWSER_DOWNLOAD_TIMEOUT')), 60000);
        });
        let download;
        try {
            await page.locator('#downloadExport').click();
            download = await received;
        } finally {
            clearTimeout(timer);
            context.off('page', onPage);
            page.off('download', onDownload);
        }
        assert.equal(await download.failure(), null);
        await download.saveAs(options.download);
        const bytes = (await fs.stat(options.download)).size;
        assert.ok(bytes > 0 && bytes < (2 << 20));
        await fs.writeFile(options.proof, JSON.stringify({
            protocol: 1, status: 'PASSED', runId, acceptanceStatus: 202,
            acceptanceComplete: false, realLogin: true, csrfDenied: true, getDenied: true,
            recoveredAfterReload: true, submissionRecoveredSameJob: true,
            state: 'READY', totalPatients: 1, processedPatients: 1, blockedPatients: 0,
            actualFormDownload: true, ciphertextBytes: bytes
        }, null, 2));
        console.log('PASS actual authenticated asynchronous CDS export browser flow');
    } catch (failure) {
        // No page content, credentials, storage state, trace or network body is published.
        const diagnostic = {protocol: 1, status: 'FAILED', stage, failureClass: failure.constructor.name};
        if (acceptedRunId && /^[0-9a-f-]{36}$/.test(acceptedRunId)) {
            diagnostic.runId = acceptedRunId;
            try {
                const response = await context.request.get(options.base + '/form/cdsExportStatus?apiVersion=1&runId=' + acceptedRunId,
                    {timeout: 10000});
                diagnostic.statusHttp = response.status();
                if (response.status() === 200) {
                    const result = await response.json();
                    const job = result.job || {};
                    diagnostic.state = /^[A-Z_]{1,32}$/.test(job.state) ? job.state : 'UNAVAILABLE';
                    diagnostic.safeCode = typeof job.safeCode === 'string' && /^CDS_[A-Z0-9_]{1,90}$/.test(job.safeCode)
                        ? job.safeCode : null;
                    for (const field of ['complete', 'artifactAvailable', 'workerOwned']) {
                        if (typeof job[field] === 'boolean') diagnostic[field] = job[field];
                    }
                    for (const field of ['totalPatients', 'processedPatients', 'blockedPatients']) {
                        if (job[field] === null || Number.isSafeInteger(job[field])) diagnostic[field] = job[field];
                    }
                }
            } catch (unavailable) {
                diagnostic.statusDiagnosticUnavailable = true;
            }
        }
        console.error('CDS_EXPORT_BROWSER_DIAGNOSTIC=' + JSON.stringify(diagnostic));
        await fs.writeFile(options.proof, JSON.stringify(diagnostic, null, 2));
        console.error('CDS_EXPORT_BROWSER_FAILED:' + stage + ':' + failure.constructor.name);
        throw new Error('CDS_EXPORT_BROWSER_FAILED:' + stage);
    } finally {
        await context.close();
        await browser.close();
    }
}
main().catch(() => { process.exitCode = 1; });
