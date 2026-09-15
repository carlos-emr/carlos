#!/usr/bin/env node
/** Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
'use strict';

/*
 * Live prescription workspace regression, using two synthetic patients in ONE login.
 * Requires a disposable instance: it saves two prescriptions with unique test names.
 * Reset the disposable database after testing; no existing medication is edited.
 *
 * Required: RX_WORKSPACE_DISPOSABLE_FIXTURES=true, RX_PATIENT_A, RX_PATIENT_B,
 * BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN.
 * Optional: CHROME_PATH, ARTIFACT_DIR, RX_CHECK_DRUGREF=true.
 * Run: node scripts/rx-workspace-playwright-checks.js
 */
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { randomUUID } = require('node:crypto');
const { login, createRecorder, wirePage, validateBaseUrl, appUrl,
    buildArtifactPath } = require('./eform-local-playwright-utils');

async function main() {
    assert.equal(process.env.RX_WORKSPACE_DISPOSABLE_FIXTURES, 'true',
            'This check saves synthetic prescriptions; opt in only for a disposable instance');
    const patients = [process.env.RX_PATIENT_A, process.env.RX_PATIENT_B];
    patients.forEach((patient) => assert.match(patient || '', /^[1-9]\d{0,8}$/));
    assert.notEqual(patients[0], patients[1], 'Two distinct synthetic patients are required');
    const baseUrl = validateBaseUrl(process.env.BASE_URL);
    const url = (route) => appUrl(baseUrl, route);
    const artifacts = process.env.ARTIFACT_DIR || '/tmp/rx-workspace-playwright';
    const artifact = (name, ext) => buildArtifactPath(artifacts, name, ext);
    const marker = 'PW479_' + randomUUID().replaceAll('-', '').slice(0,16);
    const names = [marker + '_A', marker + '_B'];
    const results = [];
    let customDrugPage = null;
    const recorder = createRecorder();
    const browser = await chromium.launch({headless: true, args: ['--no-sandbox'],
        ...(process.env.CHROME_PATH ? {executablePath: process.env.CHROME_PATH} : {})});
    const context = await browser.newContext({viewport: {width: 1440, height: 1100}});
    context.setDefaultTimeout(45000);
    context.setDefaultNavigationTimeout(90000);
    async function check(name, run) {
        await run();
        results.push(name);
        console.log('PASS ' + name);
    }
    async function open(route) {
        const page = await context.newPage();
        wirePage(page, 'workspace', recorder, async (dialog) => {
            const message = dialog.message();
            if (dialog.type() === 'confirm' && customDrugPage === page) await dialog.accept();
            else if (message.includes('new, independent draft')) await dialog.accept();
            else { recorder.pageErrors.push({text: 'Unexpected prescription dialog'}); await dialog.dismiss(); }
        });
        await page.goto(url(route));
        await page.waitForFunction(() => window.RxContext && !document.documentElement.hasAttribute('data-rx-owner-check'));
        return page;
    }
    const draftNames = (page) => page.locator('input[id^="drugName_"]').evaluateAll((inputs) => inputs.map((input) => input.value));
    async function stage(page, name) {
        await page.bringToFront();
        await page.locator('#searchString').fill(name);
        customDrugPage = page;
        try { await page.locator('#customDrug').click(); } finally { customDrugPage = null; }
        await page.locator('input[id^="drugName_"]').last().waitFor();
        assert.ok((await draftNames(page)).includes(name));
    }
    async function reload(page, expectedName) {
        await page.reload();
        await page.waitForFunction(() => window.RxContext && !document.documentElement.hasAttribute('data-rx-owner-check'));
        await page.locator('#drugForm').waitFor();
        // The page restores its draft through an onload AJAX request.
        await page.waitForFunction((name) => Array.from(document.querySelectorAll('input[id^="drugName_"]')).some((input) => input.value === name), expectedName);
    }
    try {
        await login(context, {baseUrl, testUser: process.env.TEST_USER,
            testPassword: process.env.TEST_PASSWORD, testPin: process.env.TEST_PIN}, recorder);
        const a = await open('/rx/choosePatient?demographicNo=' + patients[0]);
        const aid = await a.evaluate(() => RxContext.id);
        const aRoute = '/rx/choosePatient?demographicNo=' + patients[0] + '&rxContextId=' + aid;
        const b = await open('/rx/choosePatient?demographicNo=' + patients[1]);
        const bid = await b.evaluate(() => RxContext.id);
        await check('Standards mode and separate patient contexts', async () => {
            assert.equal(await a.evaluate(() => document.compatMode), 'CSS1Compat');
            assert.notEqual(aid, bid);
        });
        await check('Independent drafts survive interleaved requests and reload', async () => {
            await stage(a, names[0]); await stage(b, names[1]);
            await reload(a, names[0]); await reload(b, names[1]);
            assert.deepEqual(await draftNames(a), [names[0]]);
            assert.deepEqual(await draftNames(b), [names[1]]);
        });
        const duplicate = await open(aRoute);
        await check('Duplicating a tab creates an empty independent workspace', async () => {
            await duplicate.waitForFunction((id) => RxContext.id !== id, aid);
            assert.deepEqual(await draftNames(duplicate), []);
            assert.deepEqual(await draftNames(a), [names[0]]);
        });
        await check('History with explicit patient preserves the unsaved draft', async () => {
            const history = await open('/rx/ViewStaticScript2?demographicNo=' + patients[0] + '&rxContextId=' + aid);
            await history.close(); await reload(a, names[0]);
            assert.deepEqual(await draftNames(a), [names[0]]);
        });
        await check('Allergy Back link preserves patient and workspace', async () => {
            const allergy = await open('/rx/showAllergy?demographicNo=' + patients[0] + '&rxContextId=' + aid);
            await allergy.evaluate(() => {
                const form = document.createElement('form');
                form.method = 'POST'; form.action = RxContext.addToUrl('addReaction2');
                const fields = {ID: '0', type: '0', name: 'PW479_ALLERGY',
                    'CSRF-TOKEN': document.querySelector('meta[name="rx-csrf-token"]').content};
                Object.entries(fields).forEach(([name, value]) => {
                    const input = document.createElement('input'); input.name = name; input.value = value; form.append(input);
                });
                document.body.append(form); form.requestSubmit();
            });
            await allergy.locator('input[value="Back to View Allergies"]').click();
            await allergy.locator('#searchString').waitFor();
            assert.equal(await allergy.evaluate(() => RxContext.id), aid);
            await allergy.close();
        });
        await check('Invalid, conflicting, and wrong-patient contexts fail closed', async () => {
            const heartbeat = '/rx/workspaceHeartbeat?rxContextId=' + aid;
            for (const [route, headers] of [
                [heartbeat + '&demographicNo=' + patients[1], {}],
                [heartbeat, {'X-Rx-Context': bid}],
                ['/rx/workspaceHeartbeat?rxContextId=00000000-0000-0000-0000-000000000000', {}],
                ['/rx/workspaceHeartbeat', {}]
            ]) assert.equal((await context.request.get(url(route), {headers})).status(), 409);
            assert.equal((await context.request.get(url('/rx/workspaceClose?rxContextId=' + aid))).status(), 405);
            assert.deepEqual(await draftNames(a), [names[0]]);
        });
        await check('A different login session cannot reuse the workspace', async () => {
            const otherContext = await browser.newContext();
            try {
                await login(otherContext, {baseUrl, testUser: process.env.TEST_USER,
                    testPassword: process.env.TEST_PASSWORD, testPin: process.env.TEST_PIN}, createRecorder());
                const response = await otherContext.request.get(url('/rx/workspaceHeartbeat?rxContextId=' + aid));
                assert.equal(response.status(), 409);
            } finally { await otherContext.close(); }
        });
        if (process.env.RX_CHECK_DRUGREF === 'true') {
            await check('Live DrugRef autocomplete', async () => {
                await duplicate.bringToFront();
                await duplicate.locator('#searchString').fill('AMOXICILLIN');
                await duplicate.locator('.ui-autocomplete .ui-menu-item').first().waitFor();
            });
        }
        for (const [index, page, id, other] of [[0, a, aid, b], [1, b, bid, a]]) {
            await check('Save and print patient ' + (index === 0 ? 'A' : 'B'), async () => {
                await page.bringToFront();
                await page.locator('input[id^="instructions_"]').fill('1 tab daily for 7 days');
                await page.locator('input[id^="instructions_"]').press('Tab');
                await page.locator('input[id^="quantity_"]').fill('7');
                await page.locator('input[id^="quantity_"]').press('Tab');
                await page.locator('#saveButton').click();
                // The preview is nested inside the modal's prescription frame.
                await page.waitForFunction(() => Array.from(document.querySelectorAll('iframe')).some((frame) => frame.contentDocument?.querySelector('#preview')));
                const modal = page.frames().find((frame) => frame.url().includes('/rx/viewScript'));
                assert.ok(modal, 'Prescription modal must load');
                const printFrame = modal.frameLocator('#preview');
                await printFrame.locator('body').getByText(names[index], {exact: false}).waitFor();
                assert.equal(await printFrame.locator('body').evaluate(() => document.compatMode), 'CSS1Compat');
                assert.ok(!(await printFrame.locator('body').innerText()).includes(names[1-index]));
                if (index === 0) assert.deepEqual(await draftNames(other), [names[1]]);
                await page.screenshot({path: artifact('patient-' + index + '-preview', '.png'), fullPage: true});
                if (index === 0) await modal.locator('[onclick*="resetStashAndClose"]').click();
                else await page.locator('#carlosModalCloseBtn').click();
                assert.equal((await context.request.get(url('/rx/workspaceHeartbeat?rxContextId=' + id))).status(), 204);
            });
        }
        await check('Direct drug-profile print entry keeps its patient context', async () => {
            const profile = await open('/rx/ViewPrintDrugProfile2?demographicNo=' + patients[0]);
            assert.equal(await profile.locator('meta[name="rx-demographic-no"]').getAttribute('content'), patients[0]);
            assert.ok((await profile.locator('body').innerText()).includes(names[0]));
            assert.ok(!(await profile.locator('body').innerText()).includes(names[1]));
            await profile.pdf({path: artifact('drug-profile', '.pdf')}); await profile.close();
        });
        assert.deepEqual(recorder.pageErrors, [], 'No browser exceptions during the prescription workflow');
        assert.deepEqual(recorder.badResponses, [], 'No failed browser requests during the prescription workflow');
        console.log('PASS Rx workspace live checks (' + results.length + ' checks)');
    } finally {
        fs.writeFileSync(artifact('results', '.json'), JSON.stringify({results, fixtureNames: names,
            badResponses: recorder.badResponses, pageErrors: recorder.pageErrors}, null, 2));
        await browser.close();
    }
}
main().catch((error) => { console.error(error.message); process.exitCode = 1; });
