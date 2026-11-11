#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * E-Chart ▸ Preventions ▸ Enable Print ▸ Print, and the vaccine search box beside it.
 *
 * An alpha tester reported that Enable Print did not work on the Preventions page and that the
 * same thing still happened "when adding vaccines". Three faults sat behind that:
 *   1. The Print form posted every record's comments, reasons, vaccine names and lots back to
 *      /prevention/printPrevention. The packaged ModSecurity front door inspects that route (only
 *      /prevention/AddPrevention has the prose exclusion), so one ordinary comment such as
 *      "Reviewed labs with the patient; find attached the CBC and lytes." anywhere in the chart had
 *      the whole print refused with 403. Bare Tomcat has no WAF, so this check pins the cause: the
 *      Print POST must carry the selection only, and the PDF must still show the chart's records.
 *   2. The "Pick vaccine brand/generic" box sat inside the Print form, so Enter in it (with no
 *      suggestion highlighted) submitted the form: typing a vaccine and pressing Enter printed.
 *   3. Print Immunizations Only skipped the "nothing selected" check, and a failed print answered
 *      the page's HTML under the PDF's Content-Disposition, so the browser saved HTML as
 *      Prevention.pdf.
 *   4. The PDF printed every comment under a "Comments:" label whatever prevention_show_comments
 *      said, and a second time when it was on. The setting now decides once for page and paper:
 *      inline on the page means printed once, a footnote marker means left off the printout.
 *   5. Chinese, Japanese and Korean characters and emoji silently dropped out of the PDF. The
 *      vaccine added through the picker is given by an outside provider typed in all three
 *      scripts, with an emoji comment, and both must survive the save and the printout.
 *
 * User path: Schedule > Search > Master Record > E-Chart > Preventions (navbar heading) > the
 * patient's Preventions page.
 *
 * Fixtures: the owned FAKE- patient with four seeded preventions (Inf with a WAF-tripping comment,
 * brand and lot; Td; a refused HPV; a PAP screening). One Tdap is added through the brand picker.
 * Cleanup deletes the patient's preventions (+ext) and any chart notes the popups left, and asserts
 * none remain.
 */
const fs = require('node:fs');
const { execFileSync } = require('node:child_process');
const h = require('./lib/playwright-harness');
const { clickDownloadsOrOpens } = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const PRINT_PATH = /\/prevention\/printPrevention$/;
// The clinical-prose corpus phrase that CRS rules 932100/932110 refuse at paranoia level 1.
const WAF_COMMENT = 'Reviewed labs with the patient; find attached the CBC and lytes.';
const SELECTION_FIELDS = new Set(['CSRF-TOKEN', 'immunizationOnly', 'demographic_no', 'demographicNo', 'printHP']);
// An outside provider's name in Chinese, Japanese and Korean, and a comment with emoji.
const OUTSIDE_PROVIDER = '王医生 さくら診療所 김민준';
const EMOJI_COMMENT = 'Tolerated well 👍 no reaction ❤';

/** A utf8mb4 SQL literal that does not depend on the mysql client's character set. */
function utf8Literal(value) {
  return `CONVERT(X'${Buffer.from(value, 'utf8').toString('hex')}' USING utf8mb4)`;
}

function pdfText(bytes) {
  h.assert(bytes.subarray(0, 5).toString('latin1') === '%PDF-', `Print did not return PDF bytes: ${bytes.subarray(0, 60).toString('utf8')}`);
  return execFileSync('pdftotext', ['-raw', '-', '-'], { input: bytes, encoding: 'utf8' });
}

/**
 * Watches the page for Print POSTs and downloads; read the arrays after the action under test.
 * Dialogs are left to the strict harness, which fails a step on any it was not told to expect.
 */
function watchPrints(page) {
  const posts = [];
  const downloads = [];
  page.on('request', request => {
    if (request.method() === 'POST' && PRINT_PATH.test(new URL(request.url()).pathname)) posts.push(request.postData() || '');
  });
  page.on('download', download => downloads.push(download));
  return { posts, downloads };
}

async function printFrom(s, index, button) {
  const outcome = await clickDownloadsOrOpens(index, button, { context: s.context, recorder: s.recorder, label: 'prevention-print', timeout: 30000 });
  h.assert(outcome.kind === 'download', 'Print did not deliver a file');
  h.assert(outcome.download.suggestedFilename() === 'Prevention.pdf', `Print saved ${outcome.download.suggestedFilename()}`);
  return pdfText(fs.readFileSync(await outcome.download.path()));
}

function assertSelectionOnly(body) {
  const params = new URLSearchParams(body);
  const extra = [...new Set(params.keys())].filter(name => !SELECTION_FIELDS.has(name));
  h.assert(!extra.length, `The Print POST still carries chart fields: ${extra.slice(0, 8).join(', ')}`);
  h.assert(!body.includes('find+attached') && !body.includes('find%20attached'), 'The Print POST carries a clinical comment');
  return params;
}

async function waitForVaccineEditor(editor, config, patient) {
  const expected = new URL(h.appUrl(config.baseUrl, '/prevention/ViewAddPreventionData'));
  // The page event can expose the initial empty/about:blank document. Its load
  // state does not establish that the native vaccine-picker navigation committed.
  await editor.waitForURL(url => url.origin === expected.origin && url.pathname === expected.pathname,
    { timeout: 20000, waitUntil: 'domcontentloaded' });
  const url = new URL(editor.url());
  h.assert(url.origin === expected.origin && url.pathname === expected.pathname
    && url.searchParams.get('demographic_no') === String(patient)
    && url.searchParams.get('prevention') === 'Tdap' && url.searchParams.get('brandName') === 'ADACEL',
  `Enter opened ${url.pathname}${url.search}`);
}

/** The popup event can precede its first navigation (its URL is still empty). */
async function openVaccineEditor(index, box, recorder, config, patient) {
  const popup = index.waitForEvent('popup', { timeout: 20000 });
  popup.catch(() => {});
  await box.press('Enter');
  const editor = await popup;
  h.wireStrictPage(editor, 'prevention-editor', recorder);
  // A load-state wait alone can resolve for the initial, empty document.
  if (config) await waitForVaccineEditor(editor, config, patient);
  else await editor.waitForURL(/\/prevention\/ViewAddPreventionData(?:\?|$)/,
    { timeout: 20000, waitUntil: 'domcontentloaded' });
  return editor;
}

async function workflow(s) {
  const { sql, patient, marker, provider, context, config } = s;
  s.cleanup(() => {
    const notes = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`;
    sql.execute(`DELETE x FROM preventionsExt x JOIN preventions p ON p.id=x.prevention_id WHERE p.demographic_no=${patient};
      DELETE FROM preventions WHERE demographic_no=${patient};
      DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_link WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM preventions WHERE demographic_no=${patient}`) === '0', 'Owned prevention rows were not removed');
  });
  const seed = (type, date, refused, ext) => {
    const id = sql.value(`INSERT INTO preventions(demographic_no,creation_date,prevention_date,provider_no,prevention_type,
        deleted,refused,never,creator,lastUpdateDate)
      VALUES(${patient},NOW(),${h.sqlString(date)},${h.sqlString(provider)},${h.sqlString(type)},'0',${h.sqlString(refused)},'0',${h.sqlString(provider)},NOW());
      SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), `${type} fixture was not created`);
    for (const [key, value] of Object.entries(ext)) {
      sql.execute(`INSERT INTO preventionsExt(prevention_id,keyval,val) VALUES(${id},${h.sqlString(key)},${h.sqlString(value)})`);
    }
  };
  const lot = `PWLOT${marker.slice(-8)}`;
  seed('Inf', '2025-10-01', '0', { comments: WAF_COMMENT, name: 'FLUZONE High-Dose', manufacture: 'Sanofi Pasteur', lot });
  seed('Td', '2020-01-01', '0', { lot: `${lot}TD` });
  seed('HPV', '2015-03-03', '1', {});
  seed('PAP', '2024-05-05', '0', { result: 'normal' });

  const chart = await s.chart();
  const openIndex = () => s.popup(chart, chart.locator('a[onclick*="ViewPreventionIndex"]').first(), 'prevention-index');

  await s.step('Enable Print shows one ticked box per recorded type, named by the type', async () => {
    const index = await openIndex();
    const boxes = index.locator('input[name="printHP"]');
    h.assert(await boxes.count() === 4, `Expected four print boxes, found ${await boxes.count()}`);
    h.assert(!(await boxes.first().isVisible()), 'Print boxes show before Enable Print');
    await index.locator('input[name="printButton"]').click();
    h.assert(await index.locator('input[name="printButton"]').inputValue() === 'Print', 'Enable Print did not switch the button to Print');
    h.assert(await index.locator('input[name="printImmButton"]').count() === 1, 'Print Immunizations Only did not appear');
    const values = await boxes.evaluateAll(els => els.map(el => `${el.value}:${el.checked}:${el.offsetParent !== null}`).sort());
    h.assert(JSON.stringify(values) === JSON.stringify(['HPV:true:true', 'Inf:true:true', 'PAP:true:true', 'Td:true:true']),
      `Print boxes are not the ticked, visible type names: ${values.join(' ')}`);
    await index.close();
  });

  await s.step('Print posts only the selection and the PDF carries the chart records as the page shows them', async () => {
    const index = await openIndex();
    // prevention_show_comments shows a comment inline on the page, or only a footnote marker.
    const commentShown = await index.locator('div.comments', { hasText: WAF_COMMENT }).count() > 0;
    const watch = watchPrints(index);
    const button = index.locator('input[name="printButton"]');
    await button.click();
    const text = await printFrom(s, index, button);
    h.assert(watch.posts.length === 1, `Print posted ${watch.posts.length} times`);
    const params = assertSelectionOnly(watch.posts[0]);
    h.assert(JSON.stringify(params.getAll('printHP').sort()) === JSON.stringify(['HPV', 'Inf', 'PAP', 'Td']), 'Print did not post the four ticked types');
    for (const expected of [marker, 'Prevention Inf', 'Prevention Td', 'Prevention HPV', 'Prevention PAP', '2025-10-01', '2020-01-01',
      '2015-03-03', '2024-05-05', 'FLUZONE High-Dose', 'Sanofi Pasteur', lot, 'Status: Refused', 'Result: normal', 'Screenings:']) {
      h.assert(text.includes(expected), `The printed PDF lacks "${expected}"`);
    }
    const printed = text.replace(/\s+/g, ' ').split(WAF_COMMENT).length - 1;
    h.assert(printed === (commentShown ? 1 : 0),
      `The PDF prints the Inf comment ${printed} time(s) while the page ${commentShown ? 'shows it inline' : 'shows only its footnote marker'}`);
    h.assert(text.indexOf('Prevention PAP') > text.indexOf('Prevention Td'), 'Screenings printed before immunizations');
    h.assert(!/\bnull\b/.test(text), 'The printed PDF shows "null"');
    await index.close();
  });

  await s.step('Print Immunizations Only leaves the screening out', async () => {
    const index = await openIndex();
    const watch = watchPrints(index);
    await index.locator('input[name="printButton"]').click();
    const text = await printFrom(s, index, index.locator('input[name="printImmButton"]'));
    h.assert(assertSelectionOnly(watch.posts[0]).get('immunizationOnly') === 'true', 'Print Immunizations Only did not ask for immunizations only');
    h.assert(text.includes('Prevention Inf') && text.includes('Prevention HPV'), 'Immunizations are missing');
    h.assert(!text.includes('Prevention PAP') && !text.includes('Screenings'), 'Print Immunizations Only printed the screening');
    h.assert(await index.locator('input[name="immunizationOnly"]').inputValue() === 'false', 'The full Print would now be immunizations only');
    await index.close();
  });

  await s.step('with nothing ticked both Print buttons warn and post nothing', async () => {
    const index = await openIndex();
    const watch = watchPrints(index);
    await index.locator('input[name="printButton"]').click();
    await index.locator('input[name="printHP"]').evaluateAll(els => els.forEach(el => { el.checked = false; }));
    const dialogs = await h.withExpectedDialogs(index, async () => {
      await index.locator('input[name="printButton"]').click();
      await index.locator('input[name="printImmButton"]').click();
      await index.waitForTimeout(1500);
    });
    h.assert(dialogs.length === 2 && dialogs.every(d => d.type === 'alert' && /check at least one prevention/.test(d.text)),
      `Expected two "check at least one prevention" alerts, got: ${dialogs.map(d => d.text).join('; ') || 'none'}`);
    h.assert(!watch.posts.length && !watch.downloads.length, 'An empty selection was posted or downloaded');
    await index.close();
  });

  await s.step('a forged or empty print request never prints request text and never saves HTML as a PDF', async () => {
    const index = await openIndex();
    const token = await index.locator('form[name="printFrm"] input[name="CSRF-TOKEN"]').inputValue();
    const action = new URL(await index.locator('form[name="printFrm"]').getAttribute('action'), index.url()).href;
    const post = fields => context.request.post(action, {
      headers: { Origin: new URL(config.baseUrl).origin, Referer: index.url() },
      form: { 'CSRF-TOKEN': token, demographic_no: patient, demographicNo: patient, ...fields },
      failOnStatusCode: false, maxRedirects: 0,
    });
    const forged = await post({ printHP: 'Inf', immunizationOnly: 'false', preventionHeader0: 'Inf', 'preventProcedureAge0-0': '1',
      'preventProcedureComments0-0': `${marker}-FORGED`, hin: '9999999999' });
    h.assert(forged.status() === 200 && /application\/pdf/.test(forged.headers()['content-type'] || ''), `The forged request answered HTTP ${forged.status()}`);
    const text = pdfText(await forged.body());
    h.assert(!text.includes('FORGED') && !text.includes('9999999999'), 'Request text reached the printed PDF');
    h.assert(text.includes('Prevention Inf') && !text.includes('Prevention Td'), 'The forged request did not print exactly the selected chart type');
    for (const fields of [{ immunizationOnly: 'false' }, { printHP: 'NotAPrevention', immunizationOnly: 'false' }, { printHP: 'PAP', immunizationOnly: 'true' }]) {
      const response = await post(fields);
      h.assert(response.status() === 400, `An unprintable selection answered HTTP ${response.status()}: ${JSON.stringify(fields)}`);
      h.assert(!/attachment/i.test(response.headers()['content-disposition'] || ''), 'An unprintable selection was answered as a download');
      h.assert(/An error occurred while trying to print/.test(await response.text()), 'The unprintable selection did not show the print error');
    }
    await index.close();
  });

  let editor;
  let index;
  await s.step('Enter in the vaccine box opens the top suggestion and never prints', async () => {
    index = await openIndex();
    const watch = watchPrints(index);
    const box = index.locator('#immunization');
    await box.click();
    await box.pressSequentially('zzqx', { delay: 30 });
    await box.press('Enter');
    await index.waitForTimeout(1000);
    h.assert(!index.isClosed() && /ViewPreventionIndex/.test(index.url()), 'Enter with no suggestion left the Preventions page');
    await box.fill('');
    await box.pressSequentially('adac', { delay: 30 });
    await index.locator('#immunization_choices .ac-item').first().waitFor();
    // An IME's Enter only confirms the composed text: no pick, no submit.
    const pagesBefore = context.pages().length;
    await box.evaluate(el => el.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', isComposing: true, bubbles: true, cancelable: true })));
    await index.waitForTimeout(1000);
    h.assert(context.pages().length === pagesBefore && !watch.posts.length, 'An IME composition Enter picked a vaccine or printed');
    editor = await openVaccineEditor(index, box, s.recorder, config, patient);
    await index.waitForTimeout(1000);
    h.assert(!watch.posts.length && !watch.downloads.length, 'Enter in the vaccine box printed');
    // Left open: the saved form's close page refreshes this opener and then closes itself.
  });

  await s.step('the vaccine added from the picker is saved with its brand, lot and non-Latin text, refreshes the page and prints', async () => {
    await editor.locator('[name="given"][value="given"]').check();
    await editor.locator('#prevDate').fill('2026-10-01');
    await editor.locator('input[name="lot"]:visible').first().fill(`${lot}TDAP`);
    await editor.locator('select[name="provider"]:visible').first().selectOption('-1');
    await editor.locator('input[name="providerName"]:visible').first().fill(OUTSIDE_PROVIDER);
    await editor.locator('textarea[name="comments"]:visible').first().fill(EMOJI_COMMENT);
    const closed = editor.waitForEvent('close', { timeout: 30000 });
    const reloaded = index.waitForEvent('load', { timeout: 30000 });
    await editor.locator('input[type="submit"][name="action"]').first().click({ noWaitAfter: true });
    await closed;
    await reloaded;
    await expectValue(sql, `SELECT COUNT(*) FROM preventions p JOIN preventionsExt x ON x.prevention_id=p.id
      WHERE p.demographic_no=${patient} AND p.prevention_type='Tdap' AND p.deleted=0 AND x.keyval='lot' AND x.val=${h.sqlString(`${lot}TDAP`)}`,
    '1', 'The Tdap added through the brand picker was not stored with its lot');
    for (const [key, value] of [['providerName', OUTSIDE_PROVIDER], ['comments', EMOJI_COMMENT]]) {
      await expectValue(sql, `SELECT COUNT(*) FROM preventions p JOIN preventionsExt x ON x.prevention_id=p.id
        WHERE p.demographic_no=${patient} AND p.prevention_type='Tdap' AND x.keyval='${key}' AND x.val=${utf8Literal(value)}`,
      '1', `The Tdap's ${key} was not stored intact`);
    }
    h.assert(await index.locator('input[name="printHP"][value="Tdap"]').count() === 1, 'The refreshed page offers no print box for the new Tdap');
    const commentShown = await index.locator('div.comments', { hasText: EMOJI_COMMENT }).count() > 0;
    const button = index.locator('input[name="printButton"]');
    await button.click();
    const text = await printFrom(s, index, button);
    h.assert(text.includes('Prevention Tdap') && text.includes('ADACEL') && text.includes(`${lot}TDAP`) && text.includes('2026-10-01'),
      'The printed PDF lacks the Tdap added through the brand picker');
    const flat = text.replace(/\s+/g, ' ');
    h.assert(flat.includes(`Provider: ${OUTSIDE_PROVIDER}`), 'The printed PDF lost the outside provider\'s Chinese, Japanese or Korean name');
    h.assert(flat.includes(EMOJI_COMMENT) === commentShown,
      `The printed PDF ${commentShown ? 'lost the emoji comment the page shows' : 'prints a comment the page only footnotes'}`);
    await index.close();
  });
}

if (require.main === module) runWorkflow('prevention-print', workflow, { openPatient: true });
module.exports = { workflow, assertSelectionOnly, openVaccineEditor };
