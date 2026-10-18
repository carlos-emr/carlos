#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Patient tickler list status changes, entered the way a clinician enters them:
 *   E-Chart ▸ Documents ▸ owned PDF (showDocument) ▸ "open ticklers" alert link
 *   (tickler/ViewTicklerDemoMain) ▸ tick ▸ Delete or Complete (POST tickler/DbTicklerDemoMain),
 *   which returns to the patient's tickler list (tickler/ViewTicklerMain); and the list's
 *   "Move to" filter (Active / Completed / Deleted ▸ Create Report).
 * Asserts the `tickler` status of every owned tickler after each action (only the ticked ones
 * change), one `tickler_update` row per change, the return to the same patient's list, the
 * buttons each view offers (Complete and Delete on Active and Completed; Erase Completely, and
 * neither of the others, on Deleted), and that GET and a tokenless POST to DbTicklerDemoMain are
 * refused without writing. Complete from the Active view is also covered by
 * workflow-tickler-suggested-text. Erase Completely removes only the ticked deleted tickler from
 * the Deleted queue, keeps its tickler_update history and leaves the unticked deleted tickler alone
 * (#4993).
 * Fixtures: the synthetic patient from lib/workflow-session.js, three active ticklers due today,
 * and one PDF document (row, ctl_document link and file) so the document viewer offers the list;
 * all removed and verified gone.
 * Env: DOCUMENT_DIR (or RX_FAX_DOCUMENT_DIR), the installed document directory; the check SKIPs
 * without it.
 */
const fs = require('node:fs');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const TIMEOUT = 20000;
const FORM = 'form[name="ticklerform"]';
// The buttons are told apart by what they post, not by their (translated) labels.
const BUTTON = {
  complete: `${FORM} input[type="button"][onclick*="submit_form.value='Complete'"]`,
  delete: `${FORM} input[type="button"][onclick*="submit_form.value='Delete'"]`,
  erase: `${FORM} input[type="button"][onclick*="submit_form.value='Erase Completely'"]`,
};

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
  const store = documentStore();
  const docFile = path.join(store, `20260101000000${marker}-ticklers.pdf`);
  const tickler = {};
  const ownedTicklers = `SELECT tickler_no FROM tickler WHERE demographic_no=${patient}`;
  s.cleanup(() => {
    sql.execute([
      `DELETE FROM tickler_update WHERE tickler_no IN (${ownedTicklers}) OR tickler_no=${tickler.first || 0}`,
      `DELETE FROM tickler_comments WHERE tickler_no IN (${ownedTicklers})`,
      `DELETE FROM tickler_link WHERE tickler_no IN (${ownedTicklers})`,
      `DELETE FROM tickler WHERE demographic_no=${patient}`,
      `DELETE FROM ctl_document WHERE module='demographic' AND module_id=${patient}`,
      `DELETE FROM document WHERE docdesc=${h.sqlString(marker)}`,
      `DELETE FROM eChart WHERE demographicNo=${patient}`,
    ].join(';'));
    if (fs.existsSync(docFile)) fs.unlinkSync(docFile);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM tickler WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM ctl_document WHERE module='demographic' AND module_id=${patient})
      + (SELECT COUNT(*) FROM document WHERE docdesc=${h.sqlString(marker)})`) === '0'
      && !fs.existsSync(docFile), 'Owned tickler or document rows were not removed');
  });

  // Due today, so the document viewer's six-week tickler alert lists them.
  for (const label of ['first', 'second', 'third']) {
    tickler[label] = sql.value(`INSERT INTO tickler(demographic_no,message,status,update_date,service_date,creator,priority,task_assigned_to)
      VALUES(${patient},${h.sqlString(`${marker} ${label} tickler`)},'A',NOW(),CURDATE(),${h.sqlString(provider)},'Normal',${h.sqlString(provider)});
      SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(tickler[label]), `The ${label} tickler fixture was not created`);
  }
  // An erased tickler has no row; it reads as "-".
  const statuses = () => ['first', 'second', 'third']
    .map(label => sql.value(`SELECT COALESCE((SELECT status FROM tickler WHERE tickler_no=${tickler[label]}),'-')`)).join('');
  const updates = (label, status) => sql.value(`SELECT COUNT(*) FROM tickler_update WHERE tickler_no=${tickler[label]}
    AND status=${h.sqlString(status)} AND provider_no=${h.sqlString(provider)}`);

  const owner = fs.statSync(store);
  fs.writeFileSync(docFile, textPdf(marker), { flag: 'wx', mode: 0o640 });
  fs.chownSync(docFile, owner.uid, owner.gid);
  const documentNo = sql.value(`INSERT INTO document (doctype,docdesc,docfilename,doccreator,responsible,source,updatedatetime,
      status,contenttype,contentdatetime,public1,observationdate,number_of_pages,restrictToProgram,abnormal)
    VALUES ('others',${h.sqlString(marker)},${h.sqlString(path.basename(docFile))},${h.sqlString(provider)},${h.sqlString(provider)},
      '',NOW(),'A','application/pdf',NOW(),0,CURDATE(),1,0,0); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(documentNo), 'The document fixture was not created');
  sql.execute(`INSERT INTO ctl_document (module,module_id,document_no,status) VALUES ('demographic',${patient},${documentNo},'A')`);

  let viewer;
  let list;
  /** The viewer's alert link opens the list in a named window; close the old one so each open is a fresh popup. */
  async function openList() {
    if (list && !list.isClosed()) await list.close();
    const link = viewer.locator('.alert-info a.alert-link[onclick*="/tickler/ViewTicklerDemoMain"]');
    list = await s.popup(viewer, link, 'tickler-demo-main');
    await list.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(list, 'the patient tickler list');
    return list;
  }
  const box = label => list.locator(`${FORM} input[name="checkbox"][value="${tickler[label]}"]`);
  async function press(button) {
    await Promise.all([
      list.waitForURL(/\/tickler\/ViewTicklerMain\?/, { timeout: TIMEOUT }),
      list.locator(BUTTON[button]).click(),
    ]);
    await h.assertNotErrorPage(list, `the tickler list after ${button}`);
    const returned = new URL(list.url()).searchParams;
    h.assert(returned.get('demoview') === patient, `${button} returned to another patient's tickler list`);
    h.assert(!returned.has('failCount'), `${button} reported ${returned.get('failCount')} tickler(s) it could not change`);
  }
  async function moveTo(view) {
    await list.locator('form[name="serviceform"] select[name="ticklerview"]').selectOption(view);
    await Promise.all([
      list.waitForURL(url => new URL(url).searchParams.get('ticklerview') === view, { timeout: TIMEOUT }),
      list.locator('form[name="serviceform"] input[type="submit"]').click(),
    ]);
    await list.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(list, `the ${view} view of the patient tickler list`);
  }
  async function offers(expected) {
    const found = {};
    for (const button of Object.keys(BUTTON)) found[button] = await list.locator(BUTTON[button]).count() > 0;
    h.assert(JSON.stringify(found) === JSON.stringify(expected),
      `The list offers ${JSON.stringify(found)}; expected ${JSON.stringify(expected)}`);
  }
  async function lists(expected) {
    const found = {};
    for (const label of Object.keys(tickler)) found[label] = await box(label).count() === 1;
    h.assert(JSON.stringify(found) === JSON.stringify(expected),
      `The list shows ${JSON.stringify(found)}; expected ${JSON.stringify(expected)}`);
  }

  await s.step('E-Chart document ▸ open ticklers lists the three owned active ticklers with Complete and Delete', async () => {
    const chart = await s.chart();
    const link = chart.locator(`#leftNavBar a[onclick*="segmentID=${documentNo}'"], #rightNavBar a[onclick*="segmentID=${documentNo}'"]`).first();
    // The trailing date <span> overlays the link's centre (as in the eChart navbar), so click its start.
    viewer = await ui.clickOpensPopup(chart, link,
      { context: s.context, recorder, label: 'show-document', timeout: TIMEOUT, position: { x: 4, y: 4 } });
    await viewer.locator('.alert-info', { hasText: `${marker} first tickler` }).waitFor({ timeout: TIMEOUT });
    await openList();
    await lists({ first: true, second: true, third: true });
    await offers({ complete: true, delete: true, erase: false });
  });

  await s.step('Delete with one tickler ticked marks only that one deleted and returns to the patient\'s list', async () => {
    await box('first').check();
    await press('delete');
    await expectValue(sql, `SELECT status FROM tickler WHERE tickler_no=${tickler.first}`, 'D', 'Delete did not mark the ticked tickler deleted');
    h.assert(statuses() === 'DAA', `Delete changed an unticked tickler (statuses first/second/third: ${statuses()})`);
    h.assert(updates('first', 'D') === '1', 'Delete wrote no tickler_update row for the deleted tickler');
  });

  await s.step('Complete with two ticklers ticked marks both completed and leaves the deleted one deleted', async () => {
    await openList();
    await lists({ first: false, second: true, third: true });
    await box('second').check();
    await box('third').check();
    await press('complete');
    await expectValue(sql, `SELECT status FROM tickler WHERE tickler_no=${tickler.third}`, 'C', 'Complete did not mark the ticked ticklers completed');
    h.assert(statuses() === 'DCC', `Complete left first/second/third as ${statuses()}`);
    h.assert(updates('second', 'C') === '1' && updates('third', 'C') === '1', 'Complete wrote no tickler_update row for each ticked tickler');
  });

  await s.step('Move to ▸ Deleted lists only the deleted tickler and offers Erase Completely instead of Complete and Delete', async () => {
    await openList();
    await moveTo('D');
    await lists({ first: true, second: false, third: false });
    await offers({ complete: false, delete: false, erase: true });
  });

  await s.step('Move to ▸ Completed lists both completed ticklers, and Delete there marks only the ticked one deleted', async () => {
    await moveTo('C');
    await lists({ first: false, second: true, third: true });
    await offers({ complete: true, delete: true, erase: false });
    await box('second').check();
    await press('delete');
    await expectValue(sql, `SELECT status FROM tickler WHERE tickler_no=${tickler.second}`, 'D', 'Delete from the Completed view did not mark the tickler deleted');
    h.assert(statuses() === 'DDC', `Delete from the Completed view left first/second/third as ${statuses()}`);
  });

  await s.step('Move to ▸ Deleted ▸ Erase Completely removes only the ticked tickler and keeps its history', async () => {
    await moveTo('D');
    await lists({ first: true, second: true, third: false });
    await offers({ complete: false, delete: false, erase: true });
    const history = sql.value(`SELECT COUNT(*) FROM tickler_update WHERE tickler_no=${tickler.first}`);
    await box('first').check();
    await press('erase');
    h.assert(sql.value(`SELECT COUNT(*) FROM tickler WHERE tickler_no=${tickler.first}`) === '0', 'Erase Completely left the ticked tickler in the database');
    h.assert(sql.value(`SELECT COUNT(*) FROM tickler_update WHERE tickler_no=${tickler.first}`) === history && history !== '0',
      'Erase Completely did not keep the erased tickler\'s status history');
    await expectValue(sql, `SELECT status FROM tickler WHERE tickler_no=${tickler.second}`, 'D', 'Erase Completely changed the unticked deleted tickler');
    await moveTo('D');
    await lists({ first: false, second: true, third: false });
  });

  await s.step('GET and a tokenless POST to DbTicklerDemoMain are refused and change nothing', async () => {
    const where = `tickler_no=${tickler.third} AND status='C'`;
    const fields = { checkbox: tickler.third, submit_form: 'Delete', demoview: patient, updateParent: 'true' };
    const route = h.appUrl(s.config.baseUrl, '/tickler/DbTicklerDemoMain');
    await h.assertRefused(s, { label: 'GET DbTicklerDemoMain', table: 'tickler', where, before: '1',
      response: await s.context.request.get(route, { params: fields, maxRedirects: 0 }) });
    await h.assertRefused(s, { label: 'POST DbTicklerDemoMain without a CSRF token', table: 'tickler', where, before: '1',
      response: await s.context.request.post(route, { form: fields, maxRedirects: 0 }) });
    h.assert(statuses() === '-DC', `A refused request changed a tickler (first/second/third: ${statuses()})`);
  });
}

if (require.main === module) runWorkflow('tickler-demo-main-status', workflow, { openPatient: true });
module.exports = { workflow };
