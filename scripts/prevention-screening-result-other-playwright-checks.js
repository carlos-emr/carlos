#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Issue #5279 section 6: a screening's Result "Other" and its explanation. prevention-screening-result covers Pending,
 * Normal and Abnormal on PAP; this covers Other on FOBT, another type on the screening (PAPMAM) layout.
 * User path: E-Chart ▸ Preventions ▸ FOBT ▸ Completed, date, Result Other, an explanation, a comment ▸ Save; the list;
 * the record (reopened); Enable Print ▸ Print; the record ▸ a new explanation, Date cleared ▸ Save (refused) ▸ Date
 * restored ▸ Save; the record ▸ explanation cleared ▸ Save; Print; the record ▸ Result Abnormal, a reason ▸ Save.
 * The form has no required-explanation rule: AddPrevention2Action stores Other with or without one, and the printout
 * (PreventionPrintPdf) states "(no reason submitted)" when none was given. The only rule that refuses a save is the
 * required Date, so that is the refused save this check makes.
 * Asserts: Other and its explanation reach preventionsExt exactly; the list shows the record on the Other colour with
 * no result marker; the reopened form has Other ticked, the explanation and the summary lines; the printout reads
 * "Result: other (explanation)". A save refused for a missing date posts nothing, leaves the stored record exactly as
 * it was and keeps every entry in the form, which then saves once the date is back. Other with no explanation is
 * stored with an empty one and prints "(no reason submitted)". Amending Other to Abnormal stores the new result and
 * reason, keeps Other and its explanation on the archived record, and the list marks the record abnormal.
 * Fixtures: the runWorkflow FAKE- patient; its preventions and their ext rows are removed in cleanup. Reads the PDF
 * with Poppler pdftotext (SKIP without it).
 */
const h = require('./lib/playwright-harness');
const { requirePoppler } = require('./lib/export-content-helpers');
const {
  LIST_COLOURS, listedRecord, openPreventionIndex, ownPatientPreventions, preventionForms, printPreventions, printedIncludes,
} = require('./lib/prevention-coverage');
const { runWorkflow } = require('./lib/workflow-session');

const TYPE = 'FOBT';
const DONE_ON = '2026-03-04';

async function workflow(s) {
  const { sql, patient, marker } = s;
  ownPatientPreventions(s);
  const index = await openPreventionIndex(s);
  const forms = preventionForms(s, index, TYPE);
  const explanation = `${marker} sample unsuitable, repeat kit sent`;
  const corrected = `${marker} kit returned unlabelled, repeat sent`;
  const abnormalReason = `${marker} positive FIT, colonoscopy referral`;
  const comment = `${marker} result other`;
  let id;
  let editor;

  await s.step('Completed with Result Other and an explanation stores both, and the list shows the Other colour', async () => {
    editor = await forms.openNew();
    h.assert(await editor.locator('[name="result"][value="pending"]').isChecked(), 'A new screening did not open with Result Pending');
    await editor.locator('[name="given"][value="given"]').check();
    await editor.locator('#prevDate').fill(DONE_ON);
    await editor.locator('[name="result"][value="other"]').check();
    await editor.locator('[name="reason"]').fill(explanation);
    await editor.locator('[name="comments"]').fill(comment);
    id = await forms.saveNew(editor);
    h.assert(sql.value(`SELECT CONCAT(refused,'|',DATE(prevention_date)) FROM preventions WHERE id=${id}`) === `0|${DONE_ON}`,
      'The completed screening was not stored with its date');
    h.assert(forms.ext(id, 'result') === 'other', `The stored result is ${forms.ext(id, 'result')}, not other`);
    h.assert(forms.ext(id, 'reason') === explanation, 'The stored explanation is not the one entered');
    const { text, colour } = await listedRecord(forms.record(id));
    h.assert(colour === LIST_COLOURS.other, `The Other result is listed on ${colour}, not the Other colour`);
    h.assert(text.includes(DONE_ON) && !text.includes('result:'), `The list shows the Other record as: ${text.slice(0, 160)}`);
  });

  await s.step('reopened, Other and the explanation show, and the summary and the printout carry them', async () => {
    editor = await forms.open(id);
    h.assert(await editor.locator('[name="result"][value="other"]').isChecked(), 'The reopened record lost Result Other');
    h.assert(await editor.locator('[name="reason"]').inputValue() === explanation, 'The reopened record lost its explanation');
    const summary = await editor.locator('#summary').inputValue();
    h.assert(summary.includes('Result: other') && summary.includes(`Reason: ${explanation}`),
      'The reopened record\'s summary does not show Result other and its explanation');
    await editor.close();
    const printed = await printPreventions(s, index);
    h.assert(printedIncludes(printed, `Result: other (${explanation})`), 'The printout does not show "Result: other" with its explanation');
  });

  await s.step('a save refused for a missing date posts nothing, changes nothing and keeps the entries in the form', async () => {
    editor = await forms.open(id);
    await editor.locator('[name="reason"]').fill(corrected);
    await editor.locator('[name="comments"]').fill(`${comment} corrected`);
    await editor.locator('#prevDate').fill('');
    const posts = [];
    editor.on('request', request => {
      if (request.method() === 'POST' && new URL(request.url()).pathname.endsWith('/prevention/AddPrevention')) posts.push(request.url());
    });
    await editor.locator('input[type="submit"][name="action"]').first().click();
    await editor.waitForTimeout(1500);
    h.assert(!editor.isClosed(), 'The form closed although its date was empty');
    h.assert(posts.length === 0, `The form posted ${posts.length} time(s) with an empty date`);
    h.assert(await editor.locator('#prevDate').evaluate(input => !input.validity.valid), 'The empty date was not flagged as invalid');
    h.assert(forms.active() === id && sql.value(`SELECT deleted FROM preventions WHERE id=${id}`) === '0', 'The refused save changed the stored record');
    h.assert(forms.ext(id, 'reason') === explanation && forms.ext(id, 'result') === 'other', 'The refused save changed the stored result');
    h.assert(await editor.locator('[name="result"][value="other"]').isChecked(), 'The refused save cleared Result Other in the form');
    h.assert(await editor.locator('[name="reason"]').inputValue() === corrected, 'The refused save lost the new explanation');
    h.assert(await editor.locator('[name="comments"]').inputValue() === `${comment} corrected`, 'The refused save lost the new comment');
    await editor.locator('#prevDate').fill(DONE_ON);
    const previous = id;
    id = await forms.saveAmendment(editor, previous);
    h.assert(forms.ext(id, 'reason') === corrected && forms.ext(id, 'comments') === `${comment} corrected`,
      'Once the date was back, the kept entries were not the ones stored');
    h.assert(forms.ext(previous, 'reason') === explanation, 'The archived record lost its original explanation');
  });

  await s.step('Other with no explanation is stored with an empty one and prints "(no reason submitted)"', async () => {
    editor = await forms.open(id);
    await editor.locator('[name="reason"]').fill('');
    const previous = id;
    id = await forms.saveAmendment(editor, previous);
    h.assert(forms.ext(id, 'result') === 'other', `The result was stored as ${forms.ext(id, 'result')}`);
    h.assert(forms.ext(id, 'reason') === '', `The empty explanation was stored as ${forms.ext(id, 'reason')}`);
    h.assert(forms.ext(previous, 'reason') === corrected, 'The archived record lost its explanation');
    const printed = await printPreventions(s, index);
    h.assert(printedIncludes(printed, 'Result: other (no reason submitted)'), 'The printout does not say the Other result has no reason');
  });

  await s.step('amending Other to Abnormal stores the new result and reason, and the archived record keeps Other', async () => {
    editor = await forms.open(id);
    await editor.locator('[name="result"][value="abnormal"]').check();
    await editor.locator('[name="reason"]').fill(abnormalReason);
    const previous = id;
    id = await forms.saveAmendment(editor, previous);
    h.assert(forms.ext(id, 'result') === 'abnormal' && forms.ext(id, 'reason') === abnormalReason,
      `The amendment stored ${forms.ext(id, 'result')} with another reason`);
    h.assert(forms.ext(previous, 'result') === 'other', 'The archived record lost Result Other');
    h.assert(forms.ext(id, 'comments') === `${comment} corrected`, 'The amendment lost the comment');
    const { text, colour } = await listedRecord(forms.record(id));
    h.assert(text.includes('result:abnormal') && colour === LIST_COLOURS.abnormal,
      `The amended record is listed on ${colour} as: ${text.slice(0, 160)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM preventions WHERE demographic_no=${patient} AND deleted=0`) === '1',
      'More than one live record remains');
  });
}

if (require.main === module) {
  runWorkflow('prevention-screening-result-other', workflow, { preflight: () => requirePoppler('pdftotext') });
}
module.exports = { workflow };
