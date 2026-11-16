/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The H1N1 / pandemic-flu form's clinical options (issue #5279, sections 1-5).
 *
 * H1N1 is the one prevention type on the "h1n1" layout of AddPreventionData.jsp. Its Info box carries a chronic
 * condition and a healthcare-worker category (selects whose first option, No, posts "false"), and independent
 * checkboxes for pregnancy, a remote setting, household contact, police and firefighter first responders and the
 * first and second dose. AddPrevention2Action stores each posted value as one preventionsExt row under the field's
 * name; an unticked box posts nothing, so its row is absent rather than "false".
 *
 * Each case is reached through the real form: a new record is saved for the first case and every later case edits the
 * live record, so each one also exercises the amendment contract (the edited record is archived with its own values,
 * one replacement carries the new ones). Stored values, archived values and the reopened form are compared for every
 * case and every mismatch is collected, so one wrong option cannot hide another.
 */
const h = require('./playwright-harness');
const { offeredOptions, openPreventionIndex, ownPatientPreventions, preventionForms } = require('./prevention-coverage');

const TYPE = 'H1N1';
const GIVEN_ON = '2026-02-03';

/** The categories the form offers, in its order: the value posted and the label shown (English bundle). */
const CHRONIC_CONDITIONS = Object.freeze([
  { value: 'false', label: 'No' },
  { value: 'true', label: 'Yes' },
  { value: 'cardiac disorder', label: 'Cardiac Disorder' },
  { value: 'diabetes', label: 'Diabetes' },
  { value: 'cancer', label: 'Cancer' },
  { value: 'immunodeficiency', label: 'Immunodeficiency' },
  { value: 'immunosuppression', label: 'Immunosuppression' },
  { value: 'renal disease', label: 'Renal Disease' },
  { value: 'anemia or hemoglobinopathy', label: 'Anemia or Hemoglobinopathy' },
  { value: 'compromised management of respiratory secretions', label: 'Compromised Management of Respiratory Secretions' },
  { value: 'Children/Adolescent with Longterm Acetylsalicylic Acid', label: 'Children/Adolescent with Longterm Acetylsalicylic Acid' },
]);

const HEALTHCARE_WORKER_CATEGORIES = Object.freeze([
  { value: 'false', label: 'No' },
  { value: 'true', label: 'Yes' },
  { value: 'acute care', label: 'Acute Care' },
  { value: 'chronic care', label: 'Chronic Care' },
  { value: 'community care', label: 'Ambulatory/Community Care' },
  { value: 'emergency medical services', label: 'Emergency Medical Services' },
  { value: 'laboratory', label: 'Laboratory' },
  { value: 'public health', label: 'Public Health' },
  { value: 'pharmacy', label: 'Pharmacy' },
  { value: 'vaccine manufacturer', label: 'Vaccine Mfr' },
]);

/** Every checkbox on the H1N1 Info and Result boxes, so a case can prove the boxes it did not touch kept their state. */
const FLAG_FIELDS = Object.freeze(['dose1', 'dose2', 'pregnant', 'remote', 'householdcontact', 'firstresponderpolice',
  'firstresponderfire', 'swineworker', 'poultryworker', 'firstnations']);

function describeOptions(options) {
  return options.map(option => `${option.value}=${option.label}`).join(', ');
}

/** Opens the owned patient's Preventions page and the H1N1 forms on it. */
async function h1n1Forms(s) {
  ownPatientPreventions(s);
  const index = await openPreventionIndex(s);
  return preventionForms(s, index, TYPE);
}

/** Fills the parts of a new H1N1 that every case keeps: Completed, a date, a lot and a comment. */
async function fillNewRecord(editor, details) {
  await editor.locator('[name="given"][value="given"]').check();
  await editor.locator('#prevDate').fill(GIVEN_ON);
  await editor.locator('[name="lot"]').fill(details.lot);
  await editor.locator('[name="comments"]').fill(details.comments);
}

/** The lot and comment a case must not lose, and any other values the caller set as unrelated, still stored. */
function unrelatedChanges(forms, id, details, unrelated = {}) {
  const ext = forms.exts(id);
  const changes = [];
  if (ext.lot !== details.lot) changes.push(`lot became ${ext.lot}`);
  if (ext.comments !== details.comments) changes.push('the comment changed');
  for (const [key, value] of Object.entries(unrelated)) {
    const stored = key in ext ? ext[key] : '(none)';
    if (stored !== value) changes.push(`${key} became ${stored}`);
  }
  if (changes.length) return [`record ${id}: ${changes.join(', ')}`];
  return [];
}

/**
 * Sections 1 and 3: every category of one select is chosen through the form, stored exactly, archived when replaced
 * and reopened selected; then the select goes back to No.
 *
 * @param {object} s the runWorkflow session
 * @param {{field: string, label: string, noun: string, expected: Array<{value: string, label: string}>}} options the
 *        select's name, its label on the form, the noun for one of its options in step labels ("chronic-condition
 *        category"), and the options it must offer
 */
async function categorySelectWorkflow(s, { field, label, noun, expected }) {
  const forms = await h1n1Forms(s);
  const details = { lot: `PWLOT${s.marker.slice(-8)}`, comments: `${s.marker} ${field}` };
  const select = editor => editor.locator(`select[name="${field}"]`);
  const categories = expected.filter(option => option.value !== 'false');
  let editor;
  let id;

  await s.step(`a new H1N1 opens with ${label} at No and offers each documented ${noun}`, async () => {
    editor = await forms.openNew();
    h.assert(await select(editor).inputValue() === 'false', `A new H1N1 opened with ${label} at ${await select(editor).inputValue()}, not No`);
    const offered = await offeredOptions(select(editor));
    h.assert(describeOptions(offered) === describeOptions(expected),
      `The ${label} select offers [${describeOptions(offered)}], expected [${describeOptions(expected)}]`);
  });

  await s.step(`each ${noun} is stored exactly, reopens selected and is kept on the record it replaced`, async () => {
    await fillNewRecord(editor, details);
    await select(editor).selectOption(categories[0].value);
    id = await forms.saveNew(editor);
    const failures = [];
    for (let i = 0; i < categories.length; i += 1) {
      const category = categories[i];
      const stored = forms.ext(id, field);
      if (stored !== category.value) failures.push(`"${category.label}" was stored as ${stored}`);
      failures.push(...unrelatedChanges(forms, id, details));
      editor = await forms.open(id);
      const reopened = await select(editor).inputValue();
      if (reopened !== category.value) failures.push(`"${category.label}" reopened as ${reopened}`);
      if (i + 1 === categories.length) break; // the last one stays open for the return to No
      await select(editor).selectOption(categories[i + 1].value);
      const previous = id;
      id = await forms.saveAmendment(editor, previous);
      const archived = forms.ext(previous, field);
      if (archived !== category.value) failures.push(`the archived "${category.label}" record now holds ${archived}`);
    }
    h.assert(!failures.length, `${failures.length} ${label} mismatch(es): ${failures.join('; ')}`);
  });

  await s.step(`returning ${label} to No stores "false" and keeps the ${noun} on the archived record`, async () => {
    const last = categories[categories.length - 1];
    await select(editor).selectOption('false');
    const previous = id;
    id = await forms.saveAmendment(editor, previous);
    h.assert(forms.ext(id, field) === 'false', `No was stored as ${forms.ext(id, field)}`);
    h.assert(forms.ext(previous, field) === last.value, `The archived record now holds ${forms.ext(previous, field)}`);
    const unrelated = unrelatedChanges(forms, id, details);
    h.assert(!unrelated.length, unrelated.join('; '));
    editor = await forms.open(id);
    h.assert(await select(editor).inputValue() === 'false', `The record reopened with ${label} at ${await select(editor).inputValue()}`);
    await editor.close();
  });
}

/**
 * Sections 2, 4 and 5: each case ticks exactly its boxes through the form; the record then stores "true" for each
 * ticked box and no row at all for each clear one (a stale value must not survive clearing), keeps every value named
 * in `unrelated`, archives the record it replaced with that record's own boxes, and reopens with the same boxes ticked.
 *
 * @param {object} s the runWorkflow session
 * @param {{flags: string[], cases: Array<{label: string, ticked: string[]}>, unrelated?: object,
 *          setUnrelated?: function(import('playwright').Page): Promise<void>}} options
 *        the boxes under test, the cases in order (each a step), and the unrelated stored values the cases must not
 *        change with how to set them on the new record
 * @returns {Promise<{forms: object, id: function(): string}>} the forms and the live record's id, for a later step
 */
async function flagWorkflow(s, { flags, cases, unrelated = {}, setUnrelated }) {
  const forms = await h1n1Forms(s);
  const details = { lot: `PWLOT${s.marker.slice(-8)}`, comments: `${s.marker} flags` };
  const box = (editor, flag) => editor.locator(`input[type="checkbox"][name="${flag}"]`);
  let id = null;

  for (const [n, testCase] of cases.entries()) {
    await s.step(testCase.label, async () => {
      let editor;
      if (n === 0) {
        editor = await forms.openNew();
        for (const flag of FLAG_FIELDS) {
          h.assert(!(await box(editor, flag).isChecked()), `A new H1N1 opened with ${flag} ticked`);
        }
        await fillNewRecord(editor, details);
        if (setUnrelated) await setUnrelated(editor);
      } else {
        editor = await forms.open(id);
      }
      const failures = [];
      const state = async () => new Map(await Promise.all(FLAG_FIELDS.map(async flag => [flag, await box(editor, flag).isChecked()])));
      // One click per box that must change, each followed by a look at every box, so a box whose click also changed
      // another one is caught rather than put right by the next click.
      for (const flag of flags) {
        const want = testCase.ticked.includes(flag);
        const was = await state();
        if (was.get(flag) === want) continue;
        await box(editor, flag).setChecked(want);
        const now = await state();
        for (const other of FLAG_FIELDS) {
          const expected = other === flag ? want : was.get(other);
          if (now.get(other) !== expected) failures.push(`${want ? 'ticking' : 'clearing'} ${flag} left ${other} ${now.get(other) ? 'ticked' : 'clear'}`);
        }
      }
      const before = await state();
      const previous = id;
      id = previous === null ? await forms.saveNew(editor) : await forms.saveAmendment(editor, previous);

      for (const flag of flags) {
        const stored = forms.ext(id, flag);
        const want = testCase.ticked.includes(flag) ? 'true' : '(none)';
        if (stored !== want) failures.push(`${flag} was stored as ${stored}, expected ${want}`);
      }
      if (previous !== null) {
        const previousCase = cases[n - 1];
        for (const flag of flags) {
          const archived = forms.ext(previous, flag);
          const want = previousCase.ticked.includes(flag) ? 'true' : '(none)';
          if (archived !== want) failures.push(`the archived record's ${flag} became ${archived}, expected ${want}`);
        }
      }
      failures.push(...unrelatedChanges(forms, id, details, unrelated));

      const reopened = await forms.open(id);
      for (const flag of FLAG_FIELDS) {
        const ticked = await box(reopened, flag).isChecked();
        const want = flags.includes(flag) ? testCase.ticked.includes(flag) : before.get(flag);
        if (ticked !== want) failures.push(`${flag} reopened ${ticked ? 'ticked' : 'clear'}`);
      }
      await reopened.close();
      h.assert(!failures.length, `${failures.length} mismatch(es): ${failures.join('; ')}`);
    });
  }
  return { forms, id: () => id };
}

module.exports = {
  CHRONIC_CONDITIONS,
  FLAG_FIELDS,
  GIVEN_ON,
  HEALTHCARE_WORKER_CATEGORIES,
  TYPE,
  categorySelectWorkflow,
  describeOptions,
  flagWorkflow,
  h1n1Forms,
  unrelatedChanges,
};
