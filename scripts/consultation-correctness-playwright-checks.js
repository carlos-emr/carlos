#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
// Installed ON acceptance for #5775, #5874, #5932, #5756, #6013, #5841 and #5928.
// Owns every patient, referral, specialist and authorization identity it creates.
// Packet preview is read locally; this check sends no fax or external message.
'use strict';
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { ownedReferrals } = require('./lib/consultation-referral-fixture');
const { authzReadFixture } = require('./lib/authz-read-fixture');
const q = h.sqlString;

async function workflow(s) {
    const { sql, marker, patient, provider, context, config, recorder } = s;
    const referrals = ownedReferrals(s);
    const other = referrals.extraPatient('Restricted');
    const team = marker.slice(-10);
    const nullId = referrals.seed({ reason: marker + ' unset', status: null, sendTo: team, site_name: 'A' });
    const doneId = referrals.seed({ reason: marker + ' complete', status: '4', sendTo: team, site_name: 'B' });
    const hrmId = referrals.seed({ reason: marker + ' HRM', sendTo: team, site_name: 'Z' });
    const hiddenId = referrals.seed({ reason: marker + ' restricted', demographicNo: other.demographicNo, sendTo: team, site_name: 'A' });
    const phantom = 9005756;
    h.assert(sql.value(`SELECT COUNT(*) FROM HRMDocument WHERE id=${phantom}`) === '0', 'Synthetic HRM ID is in use');
    const association = h.insertId(sql, `INSERT INTO consultdocs(requestId,document_no,doctype,provider_no,attach_date)
        VALUES(${hrmId},${phantom},'H',${q(provider)},CURDATE())`, 'HRM association');
    sql.execute(`INSERT INTO consultdocs(requestId,document_no,doctype,provider_no,attach_date)
        VALUES(${hrmId},${phantom},'H',${q(provider)},CURDATE())`);
    const identities = authzReadFixture({ sql, marker, provider, testUser: config.testUser });
    s.cleanup(() => identities.cleanup());
    const role = identities.addRole({ _appointment: 'r', _demographic: 'r', _con: 'w', _eChart: 'r', '_casemgmt.notes': 'r', _admin: 'r' });
    const restricted = identities.addLogin(role);
    identities.lockPatient(restricted, other.demographicNo);
    const limited = await h.newContext(context.browser(), config);
    limited.on('page', page => h.wireStrictPage(page, 'limited-consultation', recorder));
    await h.login(limited, { ...config, testUser: restricted.username }, recorder);

    const listUrl = `/encounter/ViewConsultation?sendTo=${team}&orderby=10&desc=0&limit=2`;
    const rowIds = page => page.locator('table.consult-table tbody tr[onclick]').evaluateAll(rows => rows.map(row =>
        /requestId=(\d+)/.exec(row.getAttribute('onclick'))?.[1]).filter(Boolean));
    async function openForm(browserContext, id) {
        const page = await browserContext.newPage();
        await h.gotoApp(page, config.baseUrl, `/encounter/ViewRequest?requestId=${id}`);
        await page.locator('#EctConsultationFormRequest2Form').waitFor();
        return page;
    }
    await s.step('restricted team rows do not erase readable rows or consume the next page; NULL status renders in both modes', async () => {
        const list = await limited.newPage();
        await h.gotoApp(list, config.baseUrl, listUrl);
        h.assert(JSON.stringify(await rowIds(list)) === JSON.stringify([nullId]), 'Restricted page window changed');
        h.assert(await list.locator('[role="status"]').filter({ hasText: 'access permissions' }).count() > 0, 'Restricted rows have no notice');
        h.assert(!(await list.locator('body').innerText()).includes(other.marker), 'Restricted patient name was disclosed');
        await h.gotoApp(list, config.baseUrl, listUrl + '&offset=2');
        h.assert(JSON.stringify(await rowIds(list)) === JSON.stringify([hrmId]), 'Raw second page was skipped or repeated');
        await h.gotoApp(list, config.baseUrl, listUrl.replace('limit=2', 'limit=20') + '&includeCompleted=on');
        const ids = await rowIds(list);
        h.assert(ids.includes(nullId) && ids.includes(doneId) && !ids.includes(hiddenId), 'Completion filter lost or disclosed a row');
        const schedule = await limited.newPage();
        const response = await schedule.goto(h.appUrl(config.baseUrl, '/provider/providercontrol'));
        h.assert(response.status() === 200, 'Read-only list restriction locked the session');
    });
    await s.step('record restrictions refuse the shared form before patient data is rendered', async () => {
        const denied = await limited.request.get(h.appUrl(config.baseUrl, `/encounter/ViewRequest?requestId=${hiddenId}`));
        h.assert(denied.status() === 403, 'Restricted form did not return 403');
        const body = await denied.text();
        h.assert(!body.includes('EctConsultationFormRequest2Form') && !body.includes(other.marker), 'Restricted form emitted clinical markup');
    });
    await s.step('optional HRM access writes no false denial audit; deleted attachments need explicit authorized removal', async () => {
        const deniedAudit = () => sql.value(`SELECT COUNT(*) FROM log WHERE provider_no=${q(restricted.providerNo)}
            AND content='UNAUTHORIZED' AND action='HRMUtil.listHRMDocuments'`);
        const before = deniedAudit();
        const optional = await openForm(limited, hrmId);
        h.assert(await optional.locator('.remove-unavailable-hrm').count() === 0, 'HRM removal was offered without HRM read');
        h.assert(deniedAudit() === before, 'Optional HRM lookup created a false denial audit');
        const form = await openForm(context, hrmId);
        const reason = form.locator('[name="reasonForConsultation"]');
        await reason.fill(marker + ' unsaved draft');
        const beforeLinks = sql.value(`SELECT COUNT(*) FROM consultdocs WHERE requestId=${hrmId} AND deleted IS NULL`);
        h.assert(beforeLinks === '2', 'Opening the form silently detached HRM links');
        const get = await context.request.get(h.appUrl(config.baseUrl,
            `/encounter/DetachConsultationHrm?requestId=${hrmId}&associationId=${association}`));
        h.assert(get.status() === 405, 'HRM detach accepted GET');
        const button = form.locator('.remove-unavailable-hrm');
        h.assert(await button.count() === 1, 'Unavailable duplicate attachments were not grouped');
        const [response] = await Promise.all([
            form.waitForResponse(r => r.url().includes('/DetachConsultationHrm') && r.request().method() === 'POST'),
            button.click(),
        ]);
        h.assert(response.status() === 204, 'Explicit HRM removal failed');
        await button.waitFor({ state: 'detached' });
        h.assert(await reason.inputValue() === marker + ' unsaved draft', 'HRM removal lost the clinical draft');
        h.assert(sql.value(`SELECT COUNT(*) FROM consultdocs WHERE requestId=${hrmId} AND deleted='Y'`) === '2', 'Duplicate stored links survived removal');
        const reopened = await openForm(context, hrmId);
        h.assert(await reopened.locator('#consultAttachmentsNotIncluded').count() === 0, 'HRM omission notice survived explicit removal');
    });

    const contact = h.insertId(sql, `INSERT INTO DemographicContact(demographicNo,contactId,category,type,role,deleted,facilityId,creator)
        VALUES(${patient},${q(referrals.specId)},'professional',3,'1',0,1,${q(provider)})`, 'specialist contact');
    s.cleanup(() => sql.execute(`DELETE FROM DemographicContact WHERE id=${contact} AND demographicNo=${patient}`));
    async function editor() {
        const page = await context.newPage();
        await h.gotoApp(page, config.baseUrl, `/demographic/Contact?method=editHealthCareTeam&contactId=${contact}`);
        await page.locator('#addEditProfessionalForm').waitFor();
        return page;
    }
    async function saveEditor(page) {
        const [response] = await Promise.all([
            page.waitForNavigation({ waitUntil: 'domcontentloaded' }),
            page.locator('[name="submitbtn"]').click(),
        ]);
        return response.status();
    }
    await s.step('specialist addresses round-trip intact and stale full-field edits are refused', async () => {
        for (const address of ['a, Toronto, M5V 1A1, ON, CA, ', '123 Main St, Toronto, ON M5V 1A1',
                '1 Fake St, Testville, A1A 1A1, ZZ, CA, ', '1 Fake St\nTestville ON']) {
            sql.execute(`UPDATE professionalSpecialists SET address=${q(address)} WHERE specId=${referrals.specId}`);
            const page = await editor();
            if (address.startsWith('a,')) h.assert(await page.locator('[name="pcontact.country"]').inputValue() === 'CA', 'Country was not parsed');
            if (address.includes(', ZZ,')) h.assert(await page.locator('[name="pcontact.province"]').inputValue() === 'ZZ', 'Unknown province defaulted');
            h.assert(await saveEditor(page) === 200, 'Unchanged specialist save failed');
            h.assert(await saveEditor(page) === 200, 'Successful edit did not retain a usable version');
            h.assert(sql.value(`SELECT HEX(address) FROM professionalSpecialists WHERE specId=${referrals.specId}`)
                === Buffer.from(address).toString('hex').toUpperCase(), 'Unchanged address bytes changed');
            await page.close();
        }
        const first = await editor(); const stale = await editor();
        await first.locator('[name="pcontact.workPhone"]').fill('555-0109');
        h.assert(await saveEditor(first) === 200, 'Fresh specialist edit failed');
        await stale.locator('[name="pcontact.workPhone"]').fill('555-0110');
        const badBefore = recorder.badResponses.length;
        const consoleBefore = recorder.consoleIssues.length;
        h.assert(await saveEditor(stale) === 409, 'Stale specialist edit was accepted');
        h.assert(await stale.locator('[name="pcontact.workPhone"]').inputValue() === '555-0110', 'Rejected edit lost submitted text');
        h.assert(sql.value(`SELECT phone FROM professionalSpecialists WHERE specId=${referrals.specId}`) === '555-0109', 'Stale edit overwrote the newer contact');
        const refused = recorder.badResponses.slice(badBefore);
        h.assert(refused.length === 1 && refused[0].status === 409 && refused[0].method === 'POST'
            && new URL(refused[0].url).pathname.endsWith('/demographic/Contact'), 'Stale edit produced an unrelated HTTP failure');
        recorder.badResponses.splice(badBefore, 1);
        for (let index = recorder.consoleIssues.length - 1; index >= consoleBefore; index--) {
            const entry = recorder.consoleIssues[index];
            if (entry.location.url === refused[0].url && /^Failed to load resource:.*\b409\b/.test(entry.text)) recorder.consoleIssues.splice(index, 1);
        }
    });

    const directoryName = marker + 'Page';
    s.cleanup(() => sql.execute(`DELETE FROM professionalSpecialists WHERE lName=${q(directoryName)}`));
    const expected = [];
    for (let i = 0; i < 37; i++) expected.push(h.insertId(sql, `INSERT INTO professionalSpecialists
        (fName,lName,address,hideFromView,deleted) VALUES('Same',${q(directoryName)},'Synthetic',0,0)`, 'paged specialist'));
    await s.step('Referral Doctors sends bounded pages with no duplicate or missing specialist and retains batch selections', async () => {
        const directory = await context.newPage();
        await h.gotoApp(directory, config.baseUrl, '/admin/ManageBillingReferral?method=list');
        h.assert(await directory.locator('#referral tbody tr').count() <= 15, 'Unfiltered directory emitted more than 15 rows');
        await h.gotoApp(directory, config.baseUrl, `/admin/ManageBillingReferral?method=searchByName&search=${encodeURIComponent(directoryName)}`);
        const actual = [];
        for (let page = 0; page < 3; page++) {
            await directory.waitForFunction(() => !document.querySelector('[data-server-page-region]')?.dataset.loading);
            const ids = await directory.locator('#referral tbody input[type="checkbox"]').evaluateAll(inputs => inputs.map(input => input.id.replace('referral-select-', '')));
            h.assert(ids.length <= 15, 'Filtered directory emitted more than 15 rows'); actual.push(...ids);
            if (page === 0) await Promise.all([
                directory.waitForResponse(r => r.request().method() === 'POST' && r.url().includes('/admin/ManageBillingReferral')),
                directory.locator('#referral tbody input[type="checkbox"]').first().check(),
            ]);
            if (page < 2) {
                const selected = await directory.locator('#checked_items').innerHTML();
                const old = await directory.locator('[data-server-page-region]').elementHandle();
                await directory.locator('[data-server-page-form] button[name="tablePage"]').nth(2).click();
                await directory.waitForFunction(element => !element.isConnected, old);
                h.assert(await directory.locator('#checked_items').innerHTML() === selected, 'Paging lost the batch selections');
            }
        }
        h.assert(new Set(actual).size === 37 && actual.join(',') === expected.join(','), 'Directory traversal omitted or repeated a specialist');
        const old = await directory.locator('[data-server-page-region]').elementHandle();
        await directory.locator('[data-server-page-form] button[name="tablePage"]').first().click();
        await directory.waitForFunction(element => !element.isConnected, old);
        h.assert(await directory.locator('#referral tbody input[type="checkbox"]').first().isChecked(), 'Returning to the first page lost the checked state');
    });
}

if (require.main === module) runWorkflow('consultation-correctness', workflow, { openMaster: false });
module.exports = { workflow };
