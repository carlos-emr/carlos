#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

// Consume only the exact intentional 404 and matching console message after the UI assertions.
// All other HTTP, runtime, console and request failures remain available to the strict harness.
function consumeExpectedFavoriteFailure(recorder, url, responseStart, consoleStart) {
  const responses = recorder.badResponses.slice(responseStart);
  const consoles = recorder.consoleIssues.slice(consoleStart);
  h.assert(responses.length === 1 && responses[0].url === url && responses[0].status === 404
    && responses[0].method === 'POST', 'Unexpected HTTP failure during deleted-favorite control');
  h.assert(consoles.length === 1 && consoles[0].location.url === url && consoles[0].type === 'error'
    && /Failed to load resource.*404/.test(consoles[0].text), 'Unexpected console failure during deleted-favorite control');
  recorder.badResponses.splice(responseStart, 1);
  recorder.consoleIssues.splice(consoleStart, 1);
}

async function workflow(s) {
  let script;
  s.cleanup(() => {
    s.sql.execute(`DELETE FROM favorites WHERE provider_no=${h.sqlString(s.provider)} AND favoritename=${h.sqlString(s.marker)};
      DELETE FROM drugs WHERE demographic_no=${s.patient} AND customName=${h.sqlString(s.marker)}`);
    if (script) s.sql.execute(`DELETE FROM prescription WHERE script_no=${script} AND demographic_no=${s.patient}`);
    h.assert(s.sql.value(`SELECT COUNT(*) FROM favorites WHERE provider_no=${h.sqlString(s.provider)} AND favoritename=${h.sqlString(s.marker)}`) === '0', 'Owned favorite cleanup failed');
  });
  script = s.sql.value(`INSERT INTO prescription(provider_no,demographic_no,date_prescribed,date_printed,textView,lastUpdateDate)
    VALUES(${h.sqlString(s.provider)},${s.patient},CURDATE(),CURDATE(),'Synthetic prescription',NOW()); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9][0-9]*$/.test(script), 'Owned prescription was not created');
  const drug = s.sql.value(`INSERT INTO drugs(provider_no,demographic_no,rx_date,end_date,written_date,BN,customName,special,script_no,create_date,lastUpdateDate)
    VALUES(${h.sqlString(s.provider)},${s.patient},CURDATE(),DATE_ADD(CURDATE(),INTERVAL 7 DAY),CURDATE(),${h.sqlString(s.marker)},${h.sqlString(s.marker)},'One tablet daily',${script},NOW(),NOW()); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9][0-9]*$/.test(drug), 'Owned drug was not created');
  const page = await s.context.newPage();
  await s.step('load a prescription with schema-legal NULL dose, flags, repeats and quantity', async () => {
    await page.goto(`${s.config.baseUrl}/rx/choosePatient?demographicNo=${s.patient}`);
    await page.locator('#searchString').waitFor({ state: 'visible' });
    await page.waitForLoadState('networkidle');
  });
  await s.step('save the legacy prescription as a favorite through its history control', async () => {
    await page.goto(`${s.config.baseUrl}/rx/ViewStaticScript2?cn=${encodeURIComponent(s.marker)}`);
    const button = page.locator(`[onclick*="addFavorite2(${drug},"]`);
    await button.waitFor({ state: 'visible' });
    const dialogs = await h.withExpectedDialogs(page, async () => {
      const [response] = await Promise.all([
        page.waitForResponse(r => new URL(r.url()).pathname.endsWith('/rx/addFavorite2') && r.request().method() === 'POST'),
        button.click(),
      ]);
      h.assert(response.ok(), 'Saving the legacy favorite failed');
    }, { promptText: s.marker });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'prompt', 'Favorite naming prompt was not shown');
    await expectValue(s.sql, `SELECT COUNT(*) FROM favorites WHERE provider_no=${h.sqlString(s.provider)} AND favoritename=${h.sqlString(s.marker)} AND \`repeat\`=0 AND special='One tablet daily'`, '1', 'Legacy favorite did not preserve instructions and default repeats');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM drugs WHERE drugid=${drug} AND demographic_no=${s.patient} AND \`repeat\` IS NULL AND quantity IS NULL`) === '1', 'Reading the legacy drug rewrote its nullable fields');
  });
  await s.step('favorite edits retain their row identity and week/month duration units', async () => {
    const names = [`${s.marker}-edit-a`, `${s.marker}-edit-b`];
    const ownedNames = names.map(h.sqlString).join(',');
    s.cleanup(() => {
      s.sql.execute(`DELETE FROM favorites WHERE provider_no=${h.sqlString(s.provider)}
        AND favoritename IN (${ownedNames})`);
      h.assert(s.sql.value(`SELECT COUNT(*) FROM favorites WHERE provider_no=${h.sqlString(s.provider)}
        AND favoritename IN (${ownedNames})`) === '0', 'Owned editing favorites were not removed');
    });
    const ids = names.map((name, index) => s.sql.value(`INSERT INTO favorites
      (provider_no,favoritename,BN,GCN_SEQNO,customName,takemin,takemax,freqcode,duration,durunit,
       quantity,\`repeat\`,nosubs,prn,special,GN,unitName,custom_instructions,dispenseInternal)
      VALUES(${h.sqlString(s.provider)},${h.sqlString(name)},'Synthetic brand','0','Synthetic drug',
        1,1,'OD','7','W','7',0,0,0,'Synthetic instructions','Synthetic generic','tablet',0,${index === 0 ? 1 : 0});
      SELECT LAST_INSERT_ID()`));
    h.assert(ids.every(id => /^[1-9][0-9]*$/.test(id)), 'Editing favorite fixtures were not created');
    const editor = await s.context.newPage();
    await h.gotoApp(editor, s.config.baseUrl, '/rx/updateFavorite');
    const identity = editor.locator(`input[name^="fldFavoriteId"][value="${ids[0]}"]`);
    await identity.waitFor({ state: 'attached' });
    const index = (await identity.getAttribute('name')).replace('fldFavoriteId', '');
    const nameRow = editor.locator(`tr[name="record${index}Line1"]`);
    const doseRow = editor.locator(`tr[name="record${index}Line3"]`);
    const favoriteName = nameRow.getByLabel('Favorite Name:', { exact: true });
    await nameRow.locator(`label[for="fldFavoriteName${index}"]`).click();
    h.assert(await favoriteName.evaluate(input => input === document.activeElement),
      'Favorite name label did not focus its own row control');
    const duration = doseRow.getByLabel('Duration:', { exact: true });
    await doseRow.locator(`label[for="fldDuration${index}"]`).click();
    h.assert(await duration.evaluate(input => input === document.activeElement),
      'Duration label did not focus its own row control');
    h.assert(await duration.inputValue() === '7', 'Duration label resolved to another favorite row');
    h.assert(await doseRow.getByLabel('Frequency', { exact: true }).inputValue() === 'OD',
      'Editing fixture did not retain the frequency needed for the duplicate regression');
    const unit = doseRow.getByLabel('Duration unit', { exact: true });
    h.assert(await unit.inputValue() === 'W', 'Opening the editor silently reset weeks to days');
    await favoriteName.fill(names[1]);
    const [saved] = await Promise.all([
      editor.waitForResponse(response => new URL(response.url()).pathname.endsWith('/rx/updateFavorite2')
        && response.request().method() === 'POST'),
      editor.locator(`a[onclick="javascript:ajaxUpdateRow(${index});"]`).click(),
    ]);
    h.assert(saved.status() === 200, 'Favorite edit failed');
    await editor.locator(`#saveSuccess_${index}`).waitFor({ state: 'visible' });
    await expectValue(s.sql, `SELECT COUNT(*) FROM favorites WHERE favoriteid=${ids[0]}
      AND provider_no=${h.sqlString(s.provider)} AND favoritename=${h.sqlString(names[1])} AND durunit='W' AND dispenseInternal=1`,
    '1', 'Editing a matching favorite saved to the wrong row or changed its duration unit');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM favorites WHERE favoriteid=${ids[1]}
      AND favoritename=${h.sqlString(names[1])} AND durunit='W' AND dispenseInternal=0`) === '1', 'The matching favorite was modified');
    const csrf = await editor.locator('input[name="CSRF-TOKEN"]').first().inputValue();
    const rejected = await s.context.request.get(h.appUrl(s.config.baseUrl,
      `/rx/updateFavorite2?method=ajaxEditFavorite&favoriteId=${ids[0]}&favoriteName=unsafe`),
    { headers: { 'CSRF-TOKEN': csrf } });
    h.assert(rejected.status() === 405, 'A GET request was allowed to edit a favorite');
    s.sql.execute(`UPDATE favorites SET durunit='M' WHERE favoriteid=${ids[0]}
      AND provider_no=${h.sqlString(s.provider)} AND favoritename=${h.sqlString(names[1])}`);
    await editor.reload();
    const monthIdentity = editor.locator(`input[name^="fldFavoriteId"][value="${ids[0]}"]`);
    const monthIndex = (await monthIdentity.getAttribute('name')).replace('fldFavoriteId', '');
    h.assert(await editor.locator(`[name="fldDurationUnit${monthIndex}"]`).inputValue() === 'M',
      'Opening the editor silently reset months to days');
    await editor.close();
  });
  await s.step('stage re-prescribing only after both protected history requests succeed', async () => {
    await page.waitForLoadState('networkidle');
    await page.locator('input[value="Represcribe"]').click();
    await page.waitForURL('**/rx/prescribing');
    await page.waitForLoadState('networkidle');
    await page.locator('[id^="quantity_"]').first().waitFor({ state: 'attached' });
    h.assert(s.sql.value(`SELECT COUNT(*) FROM prescription WHERE demographic_no=${s.patient}`) === '1',
      'Staging re-prescription unexpectedly saved another prescription');
  });
  await s.step('favorite staging succeeds and a deleted favorite reports failure without changing staged drugs', async () => {
    const favorite = s.sql.value(`SELECT favoriteid FROM favorites
      WHERE provider_no=${h.sqlString(s.provider)} AND favoritename=${h.sqlString(s.marker)}`);
    h.assert(/^[1-9][0-9]*$/.test(favorite), 'Owned favorite is missing');
    const link = page.locator(`a[title="${s.marker}"][onclick*="useFav2"]`);
    await link.waitFor({ state: 'visible' });
    const isFavoritePost = response => new URL(response.url()).pathname.endsWith('/rx/useFavorite')
      && response.request().method() === 'POST';
    const [success] = await Promise.all([page.waitForResponse(isFavoritePost), link.click()]);
    h.assert(success.status() === 200, 'Existing favorite could not be staged');
    await success.finished();
    await page.waitForLoadState('networkidle');
    const stagedState = () => page.locator('#rxText').evaluate(root => Array.from(
      root.querySelectorAll('input,textarea,select')).map(control => ({
        id: control.id, name: control.name, value: control.value, checked: control.checked,
      })));
    const before = await stagedState();
    h.assert(before.some(control => control.id.startsWith('quantity_')), 'No prescription was staged before the failure');
    s.sql.execute(`DELETE FROM favorites WHERE favoriteid=${favorite}
      AND provider_no=${h.sqlString(s.provider)} AND favoritename=${h.sqlString(s.marker)}`);
    h.assert(s.sql.value(`SELECT COUNT(*) FROM favorites WHERE favoriteid=${favorite}`) === '0', 'Owned favorite deletion failed');
    const responseStart = s.recorder.badResponses.length;
    const consoleStart = s.recorder.consoleIssues.length;
    let failedResponse;
    const dialogs = await h.withExpectedDialogs(page, async () => {
      const [response] = await Promise.all([
        page.waitForResponse(isFavoritePost),
        page.waitForEvent('dialog'),
        page.waitForEvent('console', { predicate: message => message.type() === 'error'
          && /Failed to load resource.*404/.test(message.text()) }),
        link.click(),
      ]);
      failedResponse = response;
      h.assert(response.status() === 404, 'Missing favorite did not return an explicit not-found status');
      await response.finished();
    });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'alert'
      && dialogs[0].text.includes('Favorite could not be loaded'), 'Missing favorite did not show the localized error');
    h.assert(JSON.stringify(await stagedState()) === JSON.stringify(before), 'Missing favorite changed staged prescription controls');
    consumeExpectedFavoriteFailure(s.recorder, failedResponse.url(), responseStart, consoleStart);

    // Exercise the legacy selection and both editing routes without generating browser console noise.
    const csrf = await page.evaluate(() => CarlosAjax.getCsrfToken());
    for (const path of ['/rx/useFavorite', '/rx/updateFavorite', '/rx/updateFavorite2?method=ajaxEditFavorite']) {
      const response = await s.context.request.post(h.appUrl(s.config.baseUrl, path), {
        headers: { 'CSRF-TOKEN': csrf }, form: { favoriteId: favorite, 'CSRF-TOKEN': csrf },
      });
      h.assert(response.status() === 404, 'A deleted favorite was not rejected by a legacy selection/editing route');
    }
    h.assert(s.sql.value(`SELECT COUNT(*) FROM prescription WHERE demographic_no=${s.patient}`) === '1',
      'Favorite staging/failure unexpectedly persisted another prescription');
  });
  await s.step('a deleted history item returns not found and leaves existing staged drugs intact', async () => {
    const stagedValues = () => page.locator('[id^="quantity_"]').evaluateAll(
      controls => controls.map(control => ({ id: control.id, value: control.value })));
    const before = await stagedValues();
    h.assert(before.length > 0, 'Expected staged prescriptions before the stale history check');
    await h.gotoApp(page, s.config.baseUrl, `/rx/ViewStaticScript2?cn=${encodeURIComponent(s.marker)}`);
    const button = page.locator('input[value="Represcribe"]');
    await button.waitFor({ state: 'visible' });
    s.sql.execute(`DELETE FROM drugs WHERE drugid=${drug} AND demographic_no=${s.patient}`);
    const responseStart = s.recorder.badResponses.length;
    const consoleStart = s.recorder.consoleIssues.length;
    let failedResponse;
    const dialogs = await h.withExpectedDialogs(page, async () => {
      const [response] = await Promise.all([
        page.waitForResponse(r => new URL(r.url()).pathname.endsWith('/rx/rePrescribe2')
          && r.request().method() === 'POST'),
        page.waitForEvent('dialog'),
        page.waitForEvent('console', { predicate: message => message.type() === 'error'
          && /Failed to load resource.*404/.test(message.text()) }),
        button.click(),
      ]);
      failedResponse = response;
      h.assert(response.status() === 404, 'Deleted history item was not reported as unavailable');
      await response.finished();
    });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'alert', 'Stale history failure was not visible');
    h.assert(new URL(page.url()).pathname.endsWith('/rx/ViewStaticScript2'),
      'Failed history staging navigated away from the history page');
    consumeExpectedFavoriteFailure(s.recorder, failedResponse.url(), responseStart, consoleStart);
    await h.gotoApp(page, s.config.baseUrl, '/rx/prescribing');
    await page.locator('[id^="quantity_"]').first().waitFor({ state: 'attached' });
    h.assert(JSON.stringify(await stagedValues()) === JSON.stringify(before),
      'Deleted history item changed the staged prescriptions');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM prescription WHERE demographic_no=${s.patient}`) === '1',
      'Deleted history staging persisted another prescription');
  });


}
if (require.main === module) runWorkflow('rx-legacy-null-fields', workflow);
module.exports = { workflow, consumeExpectedFavoriteFailure };
