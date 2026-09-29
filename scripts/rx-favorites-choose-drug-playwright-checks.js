#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * The Rx favourites page, the drug chooser and the favourite/reprint/CPP mutators behave as
 * PR #3908's review pass left them (adapted from MagentaHealth/Open-O and the CARLOS
 * contributors credited on that PR):
 *
 *   1. The "Edit favourites" side link opens the favourites page. It used to open
 *      rx/updateFavorite, the favourite WRITE action, with no favourite to update; that action
 *      is POST-only now and the link goes through the rx/ViewEditFavorites2 read gate.
 *   2. Choosing a drug (here the custom-drug link on the results page) POSTs to rx/chooseDrug and
 *      lands on the write-script page. It used to be a GET link, staging a card outside CSRFGuard.
 *   3. rx/chooseDrug, the legacy rx/rePrescribe2 reprint path, rx/hideCpp and rx/reorderDrug
 *      refuse GET with 405 rather than mutating the chart.
 *   4. rx/useFavorite stages only the caller's own favourite: another provider's favourite is
 *      403, an unknown id 404, a malformed id 400, and the caller's own favourite is staged.
 *
 * The check owns one synthetic patient, the favourites it inserts, and any drug row it stages
 * (legacy save coverage persists and cleans up its owned prescription). It needs no DrugRef lookup.
 *
 * Environment (see docs/ui-tests/deb-install-validation.md section 6):
 *   BASE_URL, CHROME_PATH, TEST_USER, TEST_PASSWORD, TEST_PIN, MYSQL_*
 */

const h = require('./lib/playwright-harness');
const { randomBytes } = require('node:crypto');
const { runWorkflow } = require('./lib/workflow-session');
const { stageCustomDrug, clearOwnedPrescriptionRows } = require('./rx-stash-patient-isolation-playwright-checks');

/** A provider number no real login owns: the favourites table takes six characters. */
const FOREIGN_PROVIDER = 'FAKEPW';

async function openRx(session, demographicNo) {
  const page = await session.context.newPage();
  await h.gotoApp(page, session.config.baseUrl, `/rx/choosePatient?demographicNo=${demographicNo}`);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await h.assertNotErrorPage(page, 'Rx page');
  await page.locator('#searchString').waitFor({ state: 'visible', timeout: 30000 });
  return page;
}

function insertFavorite(sql, providerNo, name) {
  const id = sql.value(`INSERT INTO favorites (provider_no, favoritename, customName, GCN_SEQNO, takemin,
    takemax, freqcode, duration, durunit, quantity, \`repeat\`, nosubs, prn, special)
    VALUES (${h.sqlString(providerNo)}, ${h.sqlString(name)}, ${h.sqlString(name)}, 0, 1, 1, 'OID', '30',
    'D', '30', 0, 0, 0, ''); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(id), `the favourite ${name} was not created`);
  return id;
}

/**
 * POST rx/useFavorite the way SearchDrug3's useFav2() does: the page's CSRF token in the
 * CSRF-TOKEN header, the AJAX marker, and the window's patient (CarlosAjax adds it to every Rx
 * request; staging is refused for a request that names no patient). Sent through the context's
 * request API, which shares the session cookie, so the refusals this check expects (403/404/400)
 * are not logged as resource failures on the strict page.
 */
async function useFavorite(session, page, demographicNo, favoriteId) {
  const token = await page.locator('input[name="CSRF-TOKEN"]').first().inputValue().catch(() => '');
  h.assert(token, 'the Rx page has no CSRF token to send');
  const response = await session.context.request.post(`${String(session.config.baseUrl).replace(/\/$/, '')}/rx/useFavorite`, {
    headers: { 'CSRF-TOKEN': token, 'X-Requested-With': 'XMLHttpRequest' },
    form: { parameterValue: 'useFav2', favoriteId, randomId: '424242', demographicNo: String(demographicNo) },
    maxRedirects: 0,
    timeout: 20000,
  });
  const text = await response.text().catch(() => '');
  return { status: response.status(), body: text.replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 400) };
}

/** Native form submission serializes textarea line breaks as CRLF; HTML textarea.value uses LF. */
function clinicalFieldDifferences(columns, actual, expected) {
  if (actual.length !== columns.length || expected.length !== columns.length) return ['column_count'];
  const canonical = (column, value) => column === 'special' && typeof value === 'string'
    ? value.replace(/\r\n/g, '\n') : value;
  return columns.filter((column, index) => canonical(column, actual[index]) !== canonical(column, expected[index]));
}

async function workflow(session) {
  const { sql, patient, provider, marker, config } = session;
  session.cleanup(() => sql.execute(`DELETE FROM favorites WHERE favoritename LIKE ${h.sqlString(`${marker}%`)}`));
  // Legacy Print can stamp the saved prescription; remove its owned, unreferenced signature
  // artifacts as well as prescription/drug rows before the synthetic patient is deleted.
  session.cleanup(() => clearOwnedPrescriptionRows(sql, patient, marker));

  await session.step('the Edit favourites side link opens the favourites page', async () => {
    const rx = await openRx(session, patient);
    const link = rx.locator('a[href*="/rx/ViewEditFavorites2"]').first();
    await link.waitFor({ state: 'visible', timeout: 20000 });
    h.assert(new URL(await link.getAttribute('href'), config.baseUrl).searchParams.get('demographicNo') === patient,
      'the Edit favourites link does not carry the open patient');
    await Promise.all([
      rx.waitForURL(/\/rx\/ViewEditFavorites2\?/, { timeout: 30000 }),
      link.click(),
    ]);
    await rx.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await h.assertNotErrorPage(rx, 'Edit favourites page');
    await rx.locator('form[name="DispForm"]').waitFor({ state: 'attached', timeout: 20000 });
    await rx.locator('form[action$="/rx/deleteFavorite2"] input[name="favoriteId"]')
      .waitFor({ state: 'attached', timeout: 20000 });
    await rx.close();
  });

  await session.step('favorite delete and copy refresh retain their originating patient across tabs', async () => {
    const otherPatient = sql.value(`INSERT INTO demographic (last_name, first_name, year_of_birth,
      month_of_birth, date_of_birth, sex, patient_status, provider_no, hc_type, province,
      roster_status, lastUpdateDate) VALUES (${h.sqlString(marker)}, 'FavoritesOther', '1980', '01',
      '02', 'F', 'AC', ${h.sqlString(provider)}, 'ON', 'ON', 'NR', NOW()); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(otherPatient), 'the second patient was not created');
    session.cleanup(() => sql.execute(`DELETE FROM demographic WHERE demographic_no=${otherPatient}
      AND last_name=${h.sqlString(marker)}`));
    const favorite = insertFavorite(sql, provider, `${marker}-delete-context`);
    const editor = await session.context.newPage();
    await h.gotoApp(editor, config.baseUrl, `/rx/ViewEditFavorites2?demographicNo=${patient}`);
    const rowName = await editor.locator(`input[name^="fldFavoriteId"][value="${favorite}"]`).getAttribute('name');
    const row = rowName.replace('fldFavoriteId', '');
    const copy = await session.context.newPage();
    await h.gotoApp(copy, config.baseUrl, `/rx/copyFavorite?demographicNo=${patient}`);
    const other = await openRx(session, otherPatient);
    await h.withExpectedDialogs(editor, async () => {
      await Promise.all([
        editor.waitForURL(/\/rx\/deleteFavorite2/),
        editor.locator(`a[href="javascript:deleteRow(${row});"]`).click(),
      ]);
    }, {accept: true});
    h.assert(sql.value(`SELECT COUNT(*) FROM favorites WHERE favoriteid=${favorite}`) === '0',
      'the owned favorite was not deleted');
    // Trigger the real provider-selection refresh without copying another provider's data.
    await Promise.all([
      copy.waitForURL(/\/rx\/copyFavorite2/),
      copy.locator('select[name="ddl_provider"]').selectOption(''),
    ]);
    for (const page of [editor, copy]) {
      await h.assertNotErrorPage(page, 'favorite postback');
      const back = page.locator('input[value="Back to Search For Drug"]').first();
      await Promise.all([page.waitForURL(/\/rx\/searchDrug\?/), back.click()]);
      h.assert(new URL(page.url()).searchParams.get('demographicNo') === patient,
        'favorite postback switched its Back link to the other tab patient');
      await page.close();
    }
    await other.close();
  });

  await session.step('sharing and copying favorites use the logged-in destination and only public sources', async () => {
    const fixtures = [];
    for (const shared of [true, false]) {
      let id;
      for (let attempt = 0; attempt < 20; attempt++) {
        const candidate = `F${randomBytes(3).toString('hex').slice(0, 5)}`;
        if (sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${h.sqlString(candidate)}`) === '0') {
          id = candidate;
          break;
        }
      }
      h.assert(id, 'could not allocate a synthetic favorite source provider');
      sql.execute(`INSERT INTO provider (provider_no, last_name, first_name, provider_type, specialty,
        sex, status, lastUpdateDate) VALUES (${h.sqlString(id)}, ${h.sqlString(marker)},
        '${shared ? 'Shared' : 'Private'}', 'doctor', '', 'U', '1', NOW())`);
      session.cleanup(() => sql.execute(`DELETE FROM favorites WHERE provider_no=${h.sqlString(id)}
        AND favoritename LIKE ${h.sqlString(`${marker}%`)};
        DELETE FROM favoritesprivilege WHERE provider_no=${h.sqlString(id)};
        DELETE FROM provider WHERE provider_no=${h.sqlString(id)} AND last_name=${h.sqlString(marker)}`));
      sql.execute(`INSERT INTO favoritesprivilege (provider_no,opentopublic,writeable)
        VALUES (${h.sqlString(id)},${shared ? '1' : '0'},0)`);
      const name = `${marker}-${shared ? 'public' : 'private'} <&>`;
      const favorite = insertFavorite(sql, id, name);
      fixtures.push({id, name, favorite});
    }
    const [shared, privateSource] = fixtures;
    const unselectedName = `${marker}-public-not-selected`;
    insertFavorite(sql, shared.id, unselectedName);
    const ownSharing = sql.rows(`SELECT id,opentopublic,writeable FROM favoritesprivilege
      WHERE provider_no=${h.sqlString(provider)}`);
    h.assert(ownSharing.length <= 1, 'the test provider has ambiguous sharing preferences');
    session.cleanup(() => {
      sql.execute(`DELETE FROM favoritesprivilege WHERE provider_no=${h.sqlString(provider)}`);
      for (const [id, open, writeable] of ownSharing) {
        h.assert(/^[0-9]+$/.test(id) && /^[01]$/.test(open) && /^[01]$/.test(writeable),
          'the sharing preference snapshot contains invalid values');
        sql.execute(`INSERT INTO favoritesprivilege(id,provider_no,opentopublic,writeable)
          VALUES (${id},${h.sqlString(provider)},${open},${writeable})`);
      }
    });
    const page = await session.context.newPage();
    await h.gotoApp(page, config.baseUrl, `/rx/copyFavorite?demographicNo=${patient}`);
    const initialShare = ownSharing.length ? ownSharing[0][1] : '0';
    h.assert(await page.locator(`input[name="rb_share"][value="${initialShare}"]`).isChecked(),
      'sharing controls did not display the logged-in provider preference');
    h.assert(await page.locator(`select[name="ddl_provider"] option[value="${privateSource.id}"]`).count() === 0,
      'the copy selector disclosed a private provider');
    const changedShare = initialShare === '0' ? '1' : '0';
    await page.locator(`input[name="rb_share"][value="${changedShare}"]`).check();
    const updated = page.waitForResponse(response => new URL(response.url()).pathname.endsWith('/rx/copyFavorite2')
      && response.request().method() === 'POST');
    await page.locator('input[value="Save sharing preference"]').click();
    h.assert((await updated).ok(), 'updating the caller sharing preference failed');
    await page.waitForLoadState('networkidle');
    h.assert(sql.value(`SELECT opentopublic FROM favoritesprivilege WHERE provider_no=${h.sqlString(provider)}`)
      === changedShare, 'the caller sharing preference was not saved');
    const refreshed = page.waitForResponse(response => new URL(response.url()).pathname.endsWith('/rx/copyFavorite2')
      && response.request().method() === 'POST');
    await page.locator('select[name="ddl_provider"]').selectOption(shared.id);
    h.assert((await refreshed).ok(), 'shared favorite list refresh failed');
    await page.waitForLoadState('networkidle');
    const rowInput = page.locator(`input[name^="fldFavoriteId"][value="${shared.favorite}"]`);
    const row = (await rowInput.getAttribute('name')).replace('fldFavoriteId', '');
    h.assert(await page.locator('input[name="countFavorites"]').inputValue() === '2',
      'the form did not render both shared favorites');
    h.assert((await page.locator(`label[for="selected${row}"]`).innerText()).includes(shared.name),
      'stored favorite text was not rendered faithfully');
    await page.locator(`input[name="selected${row}"]`).check();
    const copied = page.waitForResponse(response => new URL(response.url()).pathname.endsWith('/rx/copyFavorite2')
      && response.request().method() === 'POST');
    await page.locator('input[name="b_copy"]').click();
    h.assert((await copied).ok(), 'copying a shared favorite failed');
    await page.waitForLoadState('networkidle');
    h.assert((await page.locator('[role="status"]').innerText()).includes('1'),
      'copy did not acknowledge the selected favorite count');
    h.assert(sql.value(`SELECT COUNT(*) FROM favorites WHERE provider_no=${h.sqlString(provider)}
      AND favoritename=${h.sqlString(shared.name)}`) === '1', 'shared favorite was not copied to the logged-in provider');
    h.assert(sql.value(`SELECT COUNT(*) FROM favorites WHERE provider_no=${h.sqlString(provider)}
      AND favoritename=${h.sqlString(unselectedName)}`) === '0', 'copy included an unchecked favorite');
    const fields = 'customName,GCN_SEQNO,takemin,takemax,freqcode,duration,durunit,quantity,`repeat`,nosubs,prn,special';
    const original = sql.rows(`SELECT ${fields} FROM favorites WHERE favoriteid=${shared.favorite}`);
    const copy = sql.rows(`SELECT ${fields} FROM favorites WHERE provider_no=${h.sqlString(provider)}
      AND favoritename=${h.sqlString(shared.name)}`);
    h.assert(JSON.stringify(copy) === JSON.stringify(original), 'copy changed the favorite clinical fields');
    const snapshot = () => JSON.stringify({
      favorites: sql.rows(`SELECT favoriteid,provider_no,favoritename FROM favorites
        WHERE favoritename LIKE ${h.sqlString(`${marker}%`)} ORDER BY favoriteid`),
      sharing: sql.rows(`SELECT id,provider_no,opentopublic,writeable FROM favoritesprivilege
        WHERE provider_no IN (${[provider, shared.id, privateSource.id].map(h.sqlString).join(',')}) ORDER BY id`),
    });
    const before = snapshot();
    const token = await page.locator('input[name="CSRF-TOKEN"]').first().inputValue();
    h.assert(token, 'copy form has no CSRF token');
    const base = String(config.baseUrl).replace(/\/$/, '');
    const post = form => session.context.request.post(`${base}/rx/copyFavorite2`, {
      headers: {'CSRF-TOKEN': token, 'X-Requested-With': 'XMLHttpRequest'},
      form: {demographicNo: patient, ...form}, maxRedirects: 0,
    });
    const copyForm = {dispatch: 'copy', ddl_provider: shared.id, countFavorites: '1', selected0: '1',
      fldFavoriteId0: shared.favorite};
    const probes = [
      ['GET copy', () => session.context.request.get(`${base}/rx/copyFavorite2?${new URLSearchParams({demographicNo: patient, ...copyForm})}`, {maxRedirects: 0}), 405],
      ['GET sharing update', () => session.context.request.get(`${base}/rx/copyFavorite2?dispatch=update&rb_share=1&demographicNo=${patient}`, {maxRedirects: 0}), 405],
      ['private source list', () => session.context.request.get(`${base}/rx/copyFavorite?demographicNo=${patient}&ddl_provider=${privateSource.id}`, {maxRedirects: 0}), 403],
      ['private source copy', () => post({...copyForm, ddl_provider: privateSource.id, fldFavoriteId0: privateSource.favorite}), 403],
      ['foreign sharing target', () => post({dispatch: 'update', rb_share: '1', userProviderNo: privateSource.id}), 403],
      ['foreign copy destination', () => post({...copyForm, providerNo: privateSource.id}), 403],
      ['mixed source batch', () => post({...copyForm, countFavorites: '2', selected1: '1', fldFavoriteId1: privateSource.favorite}), 403],
    ];
    for (const [label, probe, status] of probes) {
      const response = await probe();
      h.assert(response.status() === status, `${label} answered HTTP ${response.status()}, expected ${status}`);
      h.assert(snapshot() === before, `${label} changed favorites or sharing preferences`);
    }
    await page.close();
  });

  await session.step('choosing a custom drug POSTs to rx/chooseDrug and opens the write-script page', async () => {
    const page = await session.context.newPage();
    await h.gotoApp(page, config.baseUrl, `/rx/searchDrug?demographicNo=${patient}`
      + `&searchString=${encodeURIComponent(`${marker}-nosuchdrug`)}`);
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await h.assertNotErrorPage(page, 'drug search results');
    const form = page.locator('form#chooseDrugForm[method="post"][action$="/rx/chooseDrug"]');
    await form.waitFor({ state: 'attached', timeout: 20000 });
    h.assert(await form.locator('input[name="demographicNo"]').inputValue() === patient,
      'the chooser form does not name the open patient');
    h.assert((await page.locator('a[href*="/rx/chooseDrug?"]').count()) === 0,
      'the results page still links to rx/chooseDrug with GET');
    const chosen = page.waitForResponse((response) => /\/rx\/chooseDrug(\?|$)/.test(response.url()), { timeout: 30000 });
    // The custom-drug link confirms first; accept it like the prescriber does. The link is a
    // javascript: URL, so its confirm opens after click() resolves: keep the expected-dialog
    // handler in place until the chooser has answered, or the strict page dismisses it.
    let response;
    await h.withExpectedDialogs(page, async () => {
      await page.locator('a[href="javascript:customWarning();"]').click();
      response = await chosen;
    }, { accept: true });
    h.assert(response.request().method() === 'POST', `rx/chooseDrug was requested with ${response.request().method()}`);
    h.assert(response.status() < 400, `rx/chooseDrug answered HTTP ${response.status()}`);
    // The chosen drug is a staged card and the write-script page ("Step 3") opens for it, with its
    // form bound: the page's scripts named a form that does not exist and threw on every field
    // (a page error fails this strict page). It used to bounce back to the Rx page with nothing
    // staged, because DrugRef refused the blank id before the custom-drug fallback ran.
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await h.assertNotErrorPage(page, 'write-script page');
    await page.locator('form#frm textarea[name="customName"]').waitFor({ state: 'attached', timeout: 20000 });
    h.assert(await page.locator('form#frm input[name="action"]').count() === 1,
      'legacy editor contains multiple action fields; nested forms prevent Print from selecting its action');
    h.assert(await page.locator('form#frm input[name="demographicNo"]').count() === 1,
      'legacy editor contains multiple patient fields');
    h.assert(await page.locator('form#RxStashForm[name="RxStashForm"]').count() === 1,
      'legacy stash actions have no separate named form');
    const cardKey = await page.locator('form#frm input[name="randomId"]').inputValue();
    const revision = await page.locator('form#frm input[name="draftRevision"]').inputValue();
    h.assert(/^[0-9]+$/.test(cardKey) && revision.length > 0,
      'legacy editor did not bind a stable card identity and revision');
    h.assert(await page.locator(`form#frm input[name="draftRevision_${cardKey}"]`).inputValue() === revision,
      'legacy print did not bind the version of every displayed card');
    h.assert(await page.evaluate(() => frm === document.forms.frm && typeof frm.quantity === 'object'),
      'the write-script page did not bind its form');
    const legacyName = `${marker}-legacy-save`;
    const field = name => page.locator(`form#frm [name="${name}"]`);
    h.assert(await field('GCN_SEQNO').inputValue() === '0', 'custom editor lost its drug identity');
    const rxDate = await field('rxDate').inputValue();
    const writtenDate = await field('writtenDate').inputValue();
    h.assert(/^\d{4}-\d{2}-\d{2}$/.test(rxDate) && /^\d{4}-\d{2}-\d{2}$/.test(writtenDate),
      'the editor did not render the prepared prescription dates');
    // Exercise the actual controls, then Update and reload before Print. The old migration
    // discarded every prepared value on rendering, silently replacing doses/flags/directions.
    await field('customInstr').check();
    await field('customName').fill(legacyName);
    const frequencies = await field('frequencyCode').locator('option').evaluateAll(options => options.map(option => option.value));
    const frequency = frequencies.includes('BID') ? 'BID' : frequencies.find(value => value && value !== 'OID');
    h.assert(frequency, 'the fixture has no nonempty dosing frequency');
    await field('frequencyCode').selectOption(frequency);
    await field('method').selectOption('Apply');
    await field('take').selectOption('1-2');
    await field('unit').selectOption('mL');
    await field('route').selectOption('TOP');
    await field('cmbDuration').selectOption('2');
    await field('durationUnit').selectOption('W');
    await field('cmbRepeat').selectOption('3');
    await field('quantity').fill('37');
    await field('unitName').fill(`mL's <&>`);
    await field('prn').check();
    await field('nosubs').check();
    await field('longTermFlag').check();
    await field('patientComplianceN').check();
    await page.locator('#ocheck').check();
    await field('outsideProviderName').fill(`Dr O'Fixture <&>`);
    await field('outsideProviderOhip').fill('123456');
    const instructions = 'Use 1-2 mL <literal &> per the written schedule.\nKeep this wording.';
    await field('special').fill(instructions);
    // Automatic instruction mode must also preserve wording on initial rendering; rewriting
    // belongs to an explicit dosing edit, not the Update response's onload handler.
    await field('customInstr').uncheck();
    const updated = page.waitForResponse(response => new URL(response.url()).pathname.endsWith('/rx/writeScript')
      && response.request().method() === 'POST');
    await page.locator('input[onclick="submitForm(\'update\');"]').click();
    h.assert((await updated).ok(), 'legacy Update refused valid clinical fields');
    await page.waitForLoadState('networkidle');
    const expectedFields = {GCN_SEQNO: '0', customName: legacyName, rxDate, writtenDate, method: 'Apply',
      unit: 'mL', route: 'TOP', frequencyCode: frequency, duration: '2', durationUnit: 'W', repeat: '3',
      quantity: '37', unitName: `mL's <&>`, special: instructions, longTerm: 'true',
      patientCompliance: 'false', outsideProviderName: `Dr O'Fixture <&>`, outsideProviderOhip: '123456'};
    for (const [name, expected] of Object.entries(expectedFields)) {
      h.assert(await field(name).inputValue() === expected, `legacy Update lost or rewrote ${name}`);
    }
    h.assert(Number(await field('takeMin').inputValue()) === 1 && Number(await field('takeMax').inputValue()) === 2,
      'legacy Update lost the selected dose range');
    h.assert(await field('prn').isChecked() && await field('nosubs').isChecked()
      && await field('longTermFlag').isChecked() && await field('patientComplianceN').isChecked()
      && !await field('patientComplianceY').isChecked() && !await field('customInstr').isChecked(),
      'legacy Update lost the clinical flags');
    const payload = await page.locator('form#frm').evaluate(form => Object.fromEntries(new FormData(form)));
    payload.action = 'updateAndPrint';
    h.assert(payload['CSRF-TOKEN'], 'the legacy editor has no CSRF token');
    const postCapturedEditor = () => session.context.request.post(`${String(config.baseUrl).replace(/\/$/, '')}/rx/writeScript`, {
      headers: {'CSRF-TOKEN': payload['CSRF-TOKEN'], 'X-Requested-With': 'XMLHttpRequest'},
      form: payload, maxRedirects: 0,
    });
    // Another window adding a card must not cause legacy Print to save an unseen medication.
    const other = await openRx(session, patient);
    const unseen = await stageCustomDrug(other, `${marker}-legacy-unseen`);
    const refused = await postCapturedEditor();
    h.assert(refused.status() === 409 && (await refused.json()).error === 'STALE_RX_STASH',
      'the legacy editor saved a card added by another window without review');
    h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}
      AND customName IN (${h.sqlString(legacyName)},${h.sqlString(`${marker}-legacy-unseen`)})`) === '0',
      'refused legacy save persisted a medication');
    await other.locator(`#set_${unseen} a[onclick^="removePrescribingDrug"]`).click();
    await other.locator(`#set_${unseen}`).waitFor({state: 'detached'});
    await other.close();
    // The unchanged original form is now current again; exercise its actual Submit handler and
    // Struts binding instead of constructing the successful save through an API call.
    const saved = page.waitForResponse(response => new URL(response.url()).pathname.endsWith('/rx/writeScript')
      && response.request().method() === 'POST');
    await page.locator('input[onclick="submitForm(\'updateAndPrint\');"]').click();
    const savedResponse = await saved;
    const submitted = new URLSearchParams(savedResponse.request().postData());
    h.assert(JSON.stringify(submitted.getAll('action')) === JSON.stringify(['updateAndPrint']),
      'legacy Print did not submit exactly one updateAndPrint action');
    h.assert(savedResponse.ok(), `the current legacy editor was refused with HTTP ${savedResponse.status()}`);
    await page.locator('iframe#preview').waitFor({state: 'attached'});
    await h.assertNotErrorPage(page, 'saved legacy preview');
    h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}
      AND customName=${h.sqlString(legacyName)}`) === '1', 'legacy Print did not save exactly one medication');
    const clinical = sql.rows(`SELECT rx_date,written_date,takemin,takemax,freqcode,duration,durunit,
      quantity,unit,unitName,method,route,\`repeat\`,prn,nosubs,long_term,patient_compliance,
      outside_provider_name,outside_provider_ohip,special FROM drugs WHERE demographic_no=${patient}
      AND customName=${h.sqlString(legacyName)}`);
    const expectedClinical = [rxDate, writtenDate, '1', '2', frequency, '2', 'W', '37', 'mL', `mL's <&>`,
      'Apply', 'TOP', '3', '1', '1', '1', '0', `Dr O'Fixture <&>`, '123456', instructions];
    const clinicalColumns = ['rx_date', 'written_date', 'takemin', 'takemax', 'freqcode', 'duration',
      'durunit', 'quantity', 'unit', 'unitName', 'method', 'route', 'repeat', 'prn', 'nosubs', 'long_term',
      'patient_compliance', 'outside_provider_name', 'outside_provider_ohip', 'special'];
    h.assert(clinical.length === 1, 'legacy Print did not persist exactly one clinical row');
    const differences = clinicalFieldDifferences(clinicalColumns, clinical[0], expectedClinical);
    if (differences.length === 0 && clinical[0][19] !== expectedClinical[19]) {
      console.log(`  INFO textarea persistence differs only by CRLF serialization (${clinical[0][19].length} stored / ${expectedClinical[19].length} editor characters)`);
    }
    h.assert(differences.length === 0,
      `legacy Print changed clinical field(s): ${differences.join(', ')}; values withheld`);
    const duplicate = await postCapturedEditor();
    h.assert(duplicate.status() === 409 && (await duplicate.json()).error === 'STALE_RX_STASH',
      'duplicate legacy Print was accepted');
    h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}
      AND customName=${h.sqlString(legacyName)}`) === '1', 'duplicate legacy Print created another medication');
    await page.close();
  });

  await session.step('legacy stash Edit and Delete keep their card identity after another window shifts positions', async () => {
    const pad = await openRx(session, patient);
    const first = await stageCustomDrug(pad, `${marker}-legacy-first`);
    const target = await stageCustomDrug(pad, `${marker}-legacy-target`);
    const last = await stageCustomDrug(pad, `${marker}-legacy-last`);
    const editors = [];
    for (let i = 0; i < 2; i += 1) {
      const editor = await session.context.newPage();
      await h.gotoApp(editor, config.baseUrl, `/rx/stash?parameterValue=setStashIndex&randomId=${target}&demographicNo=${patient}`);
      await editor.locator('form#frm input[name="randomId"]').waitFor({state: 'attached'});
      h.assert(await editor.locator('form#frm input[name="randomId"]').inputValue() === target,
        'legacy editor opened a different card');
      editors.push(editor);
    }
    // Both legacy lists retain their original positions while the modern pad removes an earlier
    // card. An index-based legacy link would now target the following medication instead.
    await pad.locator(`#set_${first} a[onclick^="removePrescribingDrug"]`).click();
    await pad.locator(`#set_${first}`).waitFor({state: 'detached'});
    const pendingLink = (editor, action) => editor.locator(
      `a[href^="javascript:submitPending('${target}',"][href$=", '${action.toLowerCase()}');"]`).first();
    const edited = editors[0].waitForResponse(response => new URL(response.url()).pathname.endsWith('/rx/stash')
      && response.request().method() === 'POST');
    await pendingLink(editors[0], 'Edit').click();
    h.assert((await edited).ok(), 'legacy stable-card Edit was refused');
    await editors[0].waitForLoadState('networkidle');
    h.assert(await editors[0].locator('form#frm input[name="randomId"]').inputValue() === target,
      'legacy Edit selected the following card after an earlier card disappeared');
    h.assert(await editors[0].locator('form#frm textarea[name="customName"]').inputValue() === `${marker}-legacy-target`,
      'legacy Edit displayed the wrong medication');
    const deleted = editors[1].waitForResponse(response => new URL(response.url()).pathname.endsWith('/rx/stash')
      && response.request().method() === 'POST');
    await pendingLink(editors[1], 'Delete').click();
    const deleteResponse = await deleted;
    const deletedPayload = new URLSearchParams(deleteResponse.request().postData());
    h.assert(deleteResponse.ok() && deletedPayload.get('randomId') === target
      && deletedPayload.get('draftRevision') && !deletedPayload.has('stashId'),
      'legacy Delete did not submit a stable card identity and revision');
    await editors[1].waitForLoadState('networkidle');
    const fresh = await openRx(session, patient);
    h.assert(await fresh.locator(`#set_${first}`).count() === 0, 'removed first card reappeared');
    h.assert(await fresh.locator(`#set_${target}`).count() === 0, 'legacy Delete retained the selected card');
    h.assert(await fresh.locator(`#set_${last}`).count() === 1, 'legacy Delete removed the following card');
    // A replay of the now-deleted row must report stale state, never fall back to its old index.
    const replay = await session.context.request.post(`${String(config.baseUrl).replace(/\/$/, '')}/rx/stash`, {
      headers: {'CSRF-TOKEN': deletedPayload.get('CSRF-TOKEN'), 'X-Requested-With': 'XMLHttpRequest'},
      form: Object.fromEntries(deletedPayload), maxRedirects: 0,
    });
    h.assert(replay.status() === 409 && (await replay.json()).error === 'STALE_RX_STASH',
      'replayed legacy Delete did not refuse the missing card');
    const verify = await openRx(session, patient);
    h.assert(await verify.locator(`#set_${last}`).count() === 1, 'replayed Delete removed another card');
    await verify.locator(`#set_${last} a[onclick^="removePrescribingDrug"]`).click();
    await verify.locator(`#set_${last}`).waitFor({state: 'detached'});
    for (const page of [...editors, pad, fresh, verify]) await page.close();
  });

  // Exercise each distinct sidebar fragment on its actual page. Hold the initial stash read
  // so favorite staging cannot race its response and append the same card twice.
  const sidebarPages = [
    ['allergies', `/rx/showAllergy?demographicNo=${patient}`],
    ['drug chooser', `/rx/searchDrug?demographicNo=${patient}&searchString=${encodeURIComponent(`${marker}-nosuchdrug`)}`],
    ['saved prescriptions', `/rx/ViewStaticScript2?demographicNo=${patient}`],
  ];
  for (const [label, url] of sidebarPages) {
    await session.step(`the ${label} favorite link restores the draft before staging exactly one card`, async () => {
      const name = `${marker}-sidebar-${sidebarPages.findIndex(entry => entry[0] === label)}`;
      const favoriteId = insertFavorite(sql, provider, name);
      // Each handoff owns an explicit pending draft. Earlier steps now save/remove their cards,
      // so relying on them to leave one behind masks whether this handoff preserves the draft.
      const page = await openRx(session, patient);
      const priorName = `${name}-pending`;
      const priorKey = await stageCustomDrug(page, priorName);
      await h.gotoApp(page, config.baseUrl, url);
      await h.assertNotErrorPage(page, `${label} sidebar`);
      let releaseStash;
      const stashGate = new Promise(resolve => { releaseStash = resolve; });
      let receivedStash;
      const stashReceived = new Promise(resolve => { receivedStash = resolve; });
      let favoriteRequests = 0;
      const isFavorite = request => /\/rx\/useFavorite$/.test(new URL(request.url()).pathname)
        && new URLSearchParams(request.postData() || '').get('favoriteId') === favoriteId;
      page.on('request', request => { if (isFavorite(request)) favoriteRequests += 1; });
      const routePattern = '**/rx/WriteScript*';
      await page.route(routePattern, async route => {
        const request = route.request();
        const params = new URLSearchParams(request.postData() || new URL(request.url()).search);
        if (params.get('parameterValue') === 'iterateStash') {
          receivedStash();
          await stashGate;
        }
        await route.continue();
      });
      try {
        await page.locator(`a[title="${name}"]`).click();
        await page.waitForURL(/\/rx\/choosePatient\?[^#]*usefav=true/, { timeout: 15000 });
        await Promise.race([stashReceived, page.waitForTimeout(15000).then(() => {
          throw new Error('favorite handoff did not request the initial draft');
        })]);
        await page.waitForTimeout(200);
        h.assert(favoriteRequests === 0, 'favorite staging started before the initial draft finished loading');
        const staged = page.waitForResponse(response => isFavorite(response.request()), { timeout: 20000 });
        releaseStash();
        h.assert((await staged).status() === 200, 'sidebar favorite was not staged successfully');
        await page.waitForLoadState('networkidle');
        const cards = await page.locator('input[id^="drugName_"]').evaluateAll(inputs =>
          inputs.map(input => ({ id: input.id, name: input.value })));
        h.assert(cards.filter(card => card.name === name).length === 1,
          'sidebar favorite did not produce exactly one visible card');
        h.assert(new Set(cards.map(card => card.id)).size === cards.length,
          'the initial draft response duplicated favorite card identifiers');
        h.assert(cards.some(card => card.id === `drugName_${priorKey}` && card.name === priorName),
          'favorite handoff discarded or changed its previously staged custom drug');
      } finally {
        releaseStash();
        await page.unroute(routePattern);
        await page.close();
      }
    });
  }

  await session.step('chooseDrug, legacy reprint, hideCpp and reorderDrug refuse GET', async () => {
    const base = String(config.baseUrl).replace(/\/$/, '');
    const probes = [
      `/rx/chooseDrug?demographicNo=${patient}&BN=&drugId=`,
      `/rx/rePrescribe2?demographicNo=${patient}&drugList=1`,
      '/rx/hideCpp?prescriptId=1&value=true',
      '/rx/reorderDrug?prescriptId=1&position=1',
    ];
    const failures = [];
    for (const probe of probes) {
      const response = await session.context.request.fetch(`${base}${probe}`, { method: 'GET', maxRedirects: 0, timeout: 15000 });
      if (response.status() !== 405) failures.push(`${probe} answered HTTP ${response.status()}, not 405`);
    }
    h.assert(failures.length === 0, `mutator GET probe(s) not refused:\n    - ${failures.join('\n    - ')}`);
  });

  await session.step('useFavorite stages only the caller\'s own favourite', async () => {
    const own = insertFavorite(sql, provider, `${marker}-own`);
    const foreign = insertFavorite(sql, FOREIGN_PROVIDER, `${marker}-foreign`);
    const rx = await openRx(session, patient);

    const refused = await useFavorite(session, rx, patient, foreign);
    h.assert(refused.status === 403, `another provider's favourite answered HTTP ${refused.status}: ${refused.body}`);
    const missing = await useFavorite(session, rx, patient, '999999999');
    h.assert(missing.status === 404, `an unknown favourite answered HTTP ${missing.status}: ${missing.body}`);
    const malformed = await useFavorite(session, rx, patient, 'abc');
    h.assert(malformed.status === 400, `a malformed favourite id answered HTTP ${malformed.status}: ${malformed.body}`);

    const staged = await useFavorite(session, rx, patient, own);
    h.assert(staged.status === 200, `the caller's own favourite answered HTTP ${staged.status}: ${staged.body}`);
    // The stash belongs to the session, so a fresh Rx page for the patient shows the card under
    // the key the page asked for, named after the favourite.
    const again = await openRx(session, patient);
    const card = again.locator('#drugName_424242');
    await card.waitFor({ state: 'attached', timeout: 20000 });
    h.assert((await card.inputValue()).includes(`${marker}-own`),
      'the staged card is not the caller\'s favourite');
    await again.close();
    await rx.close();
  });
}

if (require.main === module) runWorkflow('rx-favorites-choose-drug', workflow);
module.exports = { workflow, clinicalFieldDifferences };
