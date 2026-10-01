#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * User path: Schedule > Search > Master Record > Tickler > New Tickler > Suggested Text
 *   (tickler/ViewTicklerSuggestedText) > Add Text > Save (tickler/EditTicklerTextSuggest);
 *   the reloaded New Tickler offers it > Save; E-Chart > Documents > owned PDF (showDocument) >
 *   "open ticklers" (ticklerDemoMain) > Complete (tickler/DbTicklerDemoMain); Suggested Text >
 *   ">>" (inactive) > Save; Schedule > Tickler > Save Settings (saveWorkView) as a throwaway
 *   login; Schedule > WorkFlow (oscarWorkflow/WorkFlowList) only when WORKFLOW=yes.
 * Asserts the owned suggestion row (active, creator), that saving the suggestion page leaves every
 * pre-existing suggestion untouched, the tickler saved from the suggestion, the Complete status
 * change, deactivation removing the offer, the saved tickler view rows and their round trip, and
 * that EditTicklerTextSuggest and saveWorkView refuse GET.
 * Fixtures: owned FAKE- patient, its suggestion, tickler, one PDF document (row, link, file) and
 * a throwaway login whose view rows are its own; all removed and verified gone.
 * Coverage plan: workflow-tickler-suggested-text (tickler suggested text, patient tickler list,
 * tickler view settings, WorkFlow). Env: DOCUMENT_DIR (or RX_FAX_DOCUMENT_DIR).
 */
const fs = require('node:fs');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');

const TIMEOUT = 20000;

/** A one-page PDF naming the document, so the viewer has real bytes to render. */
function textPdf(label) {
  const content = `BT /F1 12 Tf 40 700 Td (${label}) Tj ET\n`;
  const objects = ['<< /Type /Catalog /Pages 2 0 R >>', '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
    `<< /Length ${Buffer.byteLength(content)} >>\nstream\n${content}endstream`];
  let pdf = '%PDF-1.4\n';
  const offsets = [];
  objects.forEach((object, index) => { offsets.push(Buffer.byteLength(pdf)); pdf += `${index + 1} 0 obj\n${object}\nendobj\n`; });
  const xref = Buffer.byteLength(pdf);
  pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n${offsets.map(o => `${String(o).padStart(10, '0')} 00000 n \n`).join('')}`;
  return Buffer.from(`${pdf}trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`);
}

function documentStore() {
  const configured = process.env.DOCUMENT_DIR || process.env.RX_FAX_DOCUMENT_DIR;
  if (!configured) throw new h.SkipCheck('Set DOCUMENT_DIR to the installed document directory');
  const real = fs.realpathSync(configured);
  h.assert(fs.statSync(real).isDirectory(), 'DOCUMENT_DIR is not a directory');
  return real;
}

async function workflow(s) {
  const { sql, marker, patient, provider, recorder } = s;
  const suggestion = `${marker} recall suggestion`;
  const S = h.sqlString(suggestion);
  const store = documentStore();
  const docFile = path.join(store, `20260101000000${marker}-tickler.pdf`);
  const fixture = throwawayLoginFixture({ sql, marker, provider, testUser: s.config.testUser });
  s.cleanup(() => fixture.cleanup());
  let documentNo;
  s.cleanup(() => {
    const ticklers = `SELECT tickler_no FROM tickler WHERE demographic_no=${patient}`;
    sql.execute([
      `DELETE FROM tickler_update WHERE tickler_no IN (${ticklers})`,
      `DELETE FROM tickler_comments WHERE tickler_no IN (${ticklers})`,
      `DELETE FROM tickler_link WHERE tickler_no IN (${ticklers})`,
      `DELETE FROM tickler WHERE demographic_no=${patient}`,
      `DELETE FROM tickler_text_suggest WHERE suggested_text=${S} AND creator=${h.sqlString(provider)}`,
      `DELETE FROM ctl_document WHERE module='demographic' AND module_id=${patient}`,
      `DELETE FROM document WHERE docdesc=${h.sqlString(marker)}`,
      `DELETE FROM eChart WHERE demographicNo=${patient}`,
      ...(fixture.providerNo ? [`DELETE FROM view WHERE providerNo=${h.sqlString(fixture.providerNo)}`] : []),
    ].join(';'));
    if (fs.existsSync(docFile)) fs.unlinkSync(docFile);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM tickler WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM tickler_text_suggest WHERE suggested_text=${S})
      + (SELECT COUNT(*) FROM ctl_document WHERE module='demographic' AND module_id=${patient})
      + (SELECT COUNT(*) FROM document WHERE docdesc=${h.sqlString(marker)})
      + (SELECT COUNT(*) FROM view WHERE providerNo=${h.sqlString(fixture.providerNo || '')})`) === '0'
      && !fs.existsSync(docFile), 'Owned tickler, suggestion, document or view rows were not removed');
  });

  // Saving the suggestion page re-posts every listed suggestion; none of the existing ones may change.
  const baselineMax = Number(sql.value('SELECT COALESCE(MAX(id),0) FROM tickler_text_suggest'));
  const existing = () => JSON.stringify(sql.rows(`SELECT id,suggested_text,active,creator,create_date
    FROM tickler_text_suggest WHERE id<=${baselineMax} ORDER BY id`));
  const before = existing();
  const ownedSuggestion = `SELECT CONCAT(COUNT(*),':',COALESCE(MAX(active),'-')) FROM tickler_text_suggest
    WHERE suggested_text=${S} AND creator=${h.sqlString(provider)} AND id>${baselineMax}`;

  const ticklerList = await s.popup(s.master, s.master.locator('a[onclick*="/tickler/ViewTicklerMain"]').first(), 'tickler-list');
  await ticklerList.waitForLoadState('domcontentloaded');
  async function openAdd() {
    const add = await s.popup(ticklerList, ticklerList.locator('input.btn-primary[onclick*="/tickler/ViewAddTickler"]').first(), 'tickler-add');
    await add.locator('form[name="serviceform"]').waitFor();
    h.assert(await add.locator('form[name="serviceform"] input[name="demographic_no"]').last().inputValue() === patient,
      'The New Tickler popup opened for another patient');
    return add;
  }
  async function openSuggestions(add) {
    const page = await s.popup(add, add.locator('a', { hasText: 'Suggested Text' }).first(), 'tickler-suggested-text');
    await page.locator('select[name="activeText"]').waitFor();
    return page;
  }
  // Save submits, closes the window and reloads the New Tickler opener.
  async function saveSuggestions(page, add) {
    const reloaded = add.waitForEvent('load', { timeout: TIMEOUT });
    const [response] = await Promise.all([
      page.waitForResponse(r => new URL(r.url()).pathname.endsWith('/tickler/EditTicklerTextSuggest') && r.request().method() === 'POST'),
      page.waitForEvent('close', { timeout: TIMEOUT }),
      page.locator('input[name="saveTextChanges"]').click(),
    ]);
    h.assert(response.status() === 200, `Saving suggestions answered HTTP ${response.status()}`);
    await reloaded;
    await add.locator('select[name="suggestedText"]').waitFor();
  }
  const offered = add => add.locator('select[name="suggestedText"] option').evaluateAll(
    (options, text) => options.filter(option => option.textContent.trim() === text).length, suggestion);

  let add;
  await s.step('Suggested Text adds an owned active suggestion and leaves the existing ones untouched', async () => {
    add = await openAdd();
    const page = await openSuggestions(add);
    await page.locator('#newTextSuggest').fill(suggestion);
    await page.locator('input[name="addNewTextSuggest"]').click();
    h.assert(await page.locator('select[name="activeText"] option').evaluateAll(
      (options, text) => options.filter(option => option.textContent.trim() === text).length, suggestion) === 1,
    'Add Text did not list the new suggestion as active');
    await saveSuggestions(page, add);
    await expectValue(sql, ownedSuggestion, '1:1', 'The suggestion was not stored exactly once as active for the user');
    h.assert(existing() === before, 'Saving the suggestion page changed pre-existing suggestions');
  });

  let ticklerNo;
  await s.step('the reloaded New Tickler offers the suggestion and saves it as the owned patient\'s tickler', async () => {
    h.assert(await offered(add) === 1, 'The reloaded New Tickler does not offer the new suggestion');
    await add.locator('select[name="suggestedText"]').selectOption({ label: suggestion });
    h.assert(await add.locator('#ticklerMessage').inputValue() === suggestion, 'Choosing the suggestion did not fill the message');
    await add.locator('select[name="task_assigned_to"]').first().selectOption(provider);
    await add.locator('input.btn-primary[name="Button"]').first().click();
    await add.waitForFunction(() => {
      const frame = document.getElementById('ticklerSubmitFrame');
      return Boolean(frame && frame.contentDocument && frame.contentDocument.getElementById('tickler-save-ok'));
    }, undefined, { timeout: 30000 }).catch(error => { if (!add.isClosed()) throw error; });
    await expectValue(sql, `SELECT COUNT(*) FROM tickler WHERE demographic_no=${patient} AND message=${S}
      AND status='A' AND task_assigned_to=${h.sqlString(provider)}`, '1', 'The tickler from the suggestion was not saved');
    ticklerNo = sql.value(`SELECT tickler_no FROM tickler WHERE demographic_no=${patient} AND message=${S}`);
    if (!add.isClosed()) await add.close();
  });

  await s.step('E-Chart document > open ticklers > Complete (DbTicklerDemoMain) completes the owned tickler', async () => {
    const owner = fs.statSync(store);
    fs.writeFileSync(docFile, textPdf(marker), { flag: 'wx', mode: 0o640 });
    fs.chownSync(docFile, owner.uid, owner.gid);
    documentNo = sql.value(`INSERT INTO document (doctype,docdesc,docfilename,doccreator,responsible,source,updatedatetime,
        status,contenttype,contentdatetime,public1,observationdate,number_of_pages,restrictToProgram,abnormal)
      VALUES ('others',${h.sqlString(marker)},${h.sqlString(path.basename(docFile))},${h.sqlString(provider)},${h.sqlString(provider)},
        '',NOW(),'A','application/pdf',NOW(),0,CURDATE(),1,0,0); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(documentNo), 'The document fixture was not created');
    sql.execute(`INSERT INTO ctl_document (module,module_id,document_no,status) VALUES ('demographic',${patient},${documentNo},'A')`);
    const chart = await s.chart();
    const link = chart.locator('#leftNavBar a, #rightNavBar a').filter({ hasText: marker }).first();
    const viewer = await s.popup(chart, link, 'show-document');
    const alert = viewer.locator('.alert-info', { hasText: suggestion });
    await alert.waitFor();
    const list = await s.popup(viewer, alert.locator('a.alert-link', { hasText: 'open ticklers' }), 'tickler-demo-main');
    const box = list.locator(`form[name="ticklerform"] input[name="checkbox"][value="${ticklerNo}"]`);
    await box.check();
    await Promise.all([
      list.waitForURL(/\/tickler\/ViewTicklerMain\?/),
      list.locator('form[name="ticklerform"] input[type="button"][value="Complete"]').click(),
    ]);
    await expectValue(sql, `SELECT status FROM tickler WHERE tickler_no=${ticklerNo}`, 'C', 'Complete did not mark the tickler completed');
    h.assert(new URL(list.url()).searchParams.get('demoview') === patient, 'Complete returned to another patient\'s list');
  });

  await s.step('moving the suggestion to Inactive stores it inactive and New Tickler stops offering it', async () => {
    add = await openAdd();
    const page = await openSuggestions(add);
    const id = sql.value(`SELECT id FROM tickler_text_suggest WHERE suggested_text=${S} AND id>${baselineMax}`);
    await page.locator('select[name="activeText"]').selectOption(id);
    await page.locator('input[name="movetoInactive"]').click();
    h.assert(await page.locator(`select[name="inactiveText"] option[value="${id}"]`).count() === 1, '>> did not move the suggestion');
    await saveSuggestions(page, add);
    await expectValue(sql, ownedSuggestion, '1:0', 'The suggestion was not stored inactive');
    h.assert(await offered(add) === 0, 'New Tickler still offers the inactive suggestion');
    h.assert(existing() === before, 'Saving the suggestion page changed pre-existing suggestions');
    await add.close();
  });

  await s.step('EditTicklerTextSuggest refuses GET with 405 and changes nothing', async () => {
    const id = sql.value(`SELECT id FROM tickler_text_suggest WHERE suggested_text=${S} AND id>${baselineMax}`);
    const response = await s.context.request.get(h.appUrl(s.config.baseUrl,
      `/tickler/EditTicklerTextSuggest?method=updateTextSuggest&activeText=${id}&inactiveText=0`), { maxRedirects: 0 });
    h.assert(response.status() === 405, `GET EditTicklerTextSuggest answered HTTP ${response.status()}`);
    h.assert(sql.value(ownedSuggestion) === '1:0' && existing() === before, 'A refused GET changed suggestions');
  });

  await s.step('Tickler > Save Settings stores the throwaway user\'s view and the list reopens with it', async () => {
    fixture.create();
    const P = h.sqlString(fixture.providerNo);
    const ctx = await h.newContext(s.context.browser(), s.config);
    ctx.setDefaultTimeout(TIMEOUT);
    ctx.on('page', page => h.wireStrictPage(page, 'tickler-view-user', recorder));
    const schedule = await h.login(ctx, { ...s.config, testUser: fixture.username }, recorder, { label: 'tickler-view-schedule' });
    const open = () => ui.clickOpensPopup(schedule, schedule.locator('#oscar_new_tickler'),
      { context: ctx, recorder, label: 'tickler-main', timeout: TIMEOUT });
    let main = await open();
    await main.locator('#ticklerview').waitFor();
    await main.locator('#ticklerview').selectOption('C');
    const [response] = await Promise.all([
      main.waitForResponse(r => new URL(r.url()).pathname.endsWith('/saveWorkView') && r.request().method() === 'POST'),
      main.locator('#saveViewButton').click(),
    ]);
    h.assert(response.status() === 200, `Save Settings answered HTTP ${response.status()}`);
    await main.locator('#saveViewButton.btn-success').waitFor();
    await expectValue(sql, `SELECT value FROM view WHERE providerNo=${P} AND view_name='tickler' AND name='ticklerview'`, 'C',
      'The saved tickler view was not stored for the user');
    const rows = () => JSON.stringify(sql.rows(`SELECT name,value FROM view WHERE providerNo=${P} ORDER BY name`));
    const saved = rows();
    await main.close();
    main = await open();
    h.assert(await main.locator('#ticklerview').inputValue() === 'C', 'The reopened tickler list ignored the saved view');
    const refused = await ctx.request.get(h.appUrl(s.config.baseUrl,
      '/saveWorkView?method=save&view_name=tickler&ticklerview=D'), { maxRedirects: 0 });
    h.assert(refused.status() === 405, `GET saveWorkView answered HTTP ${refused.status()}`);
    h.assert(rows() === saved, 'A refused GET changed the saved view');
    await ctx.close();
  });

  const workflowLink = s.schedule.locator('a', { hasText: /^\s*WorkFlow\s*$/ });
  if (await workflowLink.count() === 0) {
    console.log('  SKIP workflow-tickler-suggested-text: WorkFlow list (WORKFLOW property is off; no top-bar entry)');
  } else {
    await s.step('Schedule > WorkFlow opens the WorkFlow list', async () => {
      const page = await s.popup(s.schedule, workflowLink.first(), 'workflow-list');
      await page.locator('#scrollNumber1').waitFor();
      await h.assertNotErrorPage(page, 'the WorkFlow list');
      h.assert((await page.locator('.MainTableTopRowLeftColumn').innerText()).trim() === 'workFlow', 'The WorkFlow list did not render');
    });
  }
}

if (require.main === module) runWorkflow('workflow-tickler-suggested-text', workflow, { openPatient: true });
module.exports = { workflow };
