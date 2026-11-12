#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Stored-markup check of the Vascular Tracker (VT) form (issue #4681).
// User path: the VT registration is hidden by default, so the form is opened the way the chart's form list
// opens it, by its Setup route: /form/SetupForm?formName=VTForm&demographic_no=N&formId=ID.
// Fixtures: one owned FAKE patient; a measurement row for every VT measurement type whose value carries inert
// markup; an active allergy and drug; the chart's Ongoing Concerns; and a saved VT record whose smoking and
// foot-exam comments carry markup. All of it is INSERTed (bypassing the WAF). Cleanup removes exactly these
// rows by key.
// Asserted: literal text visible, no `[data-xp]` element in any frame, no script error, every seeded value the
// form is known to show is shown, and the foot-exam comment box holds the stored comment exactly (a template
// that indents the <textarea> content pads the stored comment with whitespace on every save).
// Implements: wave-6 xss-poison (stored markup / output-encoding walk).
const fs = require('node:fs');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { payload, inspect, Findings, Seeder, fieldIds } = require('./lib/xss-poison-helpers');
const { seedPatient } = require('./lib/xss-poison-patient');

const decode = text => text.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&amp;/g, '&');

/** Every measurement the VT registration defines, with the exact measuring instruction the form looks its last value up by. */
function vtMeasurements() {
  const xml = fs.readFileSync(path.join(__dirname, '../src/main/webapp/form/VTForm.xml'), 'utf8');
  return [...xml.matchAll(/<measurement>([\s\S]*?)<\/measurement>/g)].map(match => ({
    type: match[1].match(/<type>([^<]*)<\/type>/)?.[1].trim(),
    instruction: decode(match[1].match(/<measuringInstrc>([^<]*)<\/measuringInstrc>/)?.[1].trim() ?? ''),
  })).filter(entry => entry.type && entry.instruction);
}

async function workflow(s) {
  const fields = {};
  // Payload numbers start at 900: each check owns its own range, so a concurrent xss-poison run's rows on a
  // shared list are never mistaken for this run's (inspect() ignores a number it did not create).
  let n = 900;
  const P = (name, max = 255) => { n += 1; fields[n] = name; return payload(n, max); };
  const seed = new Seeder(s.sql, s.cleanup, s.marker);
  const hex = s.marker.slice(-8);
  const chartNo = `XV${hex}`;
  const measurements = vtMeasurements();
  h.assert(measurements.length >= 20, 'VTForm.xml defines no measurements to seed');
  let demo;
  let formId;
  const stored = {};
  await s.step('seed the poisoned patient, measurements, medications and a saved VT record', async () => {
    demo = seedPatient(seed, P, '999998', chartNo);
    const d = Number(demo);
    for (const { type, instruction } of measurements) {
      seed.insert('measurements', { type, demographicNo: d, providerNo: '999998', dataField: P(`${type} last value`),
        measuringInstruction: instruction, comments: '', dateObserved: { raw: 'NOW()' }, dateEntered: { raw: 'NOW()' } }, { key: 'id' });
    }
    seed.insert('allergies', { demographic_no: d, entry_date: { raw: 'CURDATE()' }, DESCRIPTION: P('allergy description', 50), reaction: '', TYPECODE: 0,
      archived: 0, position: 0, lastUpdateDate: { raw: 'NOW()' }, nonDrug: 1, severity_of_reaction: '2', onset_of_reaction: '1' }, { key: 'allergyid' });
    seed.insert('drugs', { provider_no: '999998', demographic_no: d, rx_date: { raw: 'CURDATE()' }, end_date: { raw: 'DATE_ADD(CURDATE(), INTERVAL 30 DAY)' },
      written_date: { raw: 'CURDATE()' }, customName: P('drug custom name', 60), archived: 0, GCN_SEQNO: 0, script_no: 0, create_date: { raw: 'NOW()' },
      quantity: '1', repeat: 0, long_term: 1 }, { key: 'drugid' });
    seed.insert('eChart', { timeStamp: { raw: 'NOW()' }, demographicNo: d, providerNo: '999998', subject: '', socialHistory: '', familyHistory: '',
      medicalHistory: '', ongoingConcerns: P('ongoing concerns', 255), reminders: '', encounter: '' }, { key: 'eChartId' });
    stored.smoking = P('smoking comment', 25);
    stored.foot = P('foot exam comment', 255);
    formId = seed.insert('formVTForm', { demographic_no: d, provider_no: 999998, formCreated: { raw: 'CURDATE()' }, visitCod: '2026-01-01',
      SmkSDate: '2026-01-02', SmkSComments: stored.smoking, FTExComments: stored.foot }, { key: 'ID' });
  });
  const f = new Findings(s.recorder);
  const step = f.stepper(s);
  const E = (...names) => fieldIds(fields, ...names);
  let page;
  await step('open the VT form for the saved record', async () => {
    page = await s.context.newPage();
    h.wireStrictPage(page, 'vt-form', s.recorder);
    const since = f.mark();
    await page.goto(`${s.config.baseUrl}/form/SetupForm?formName=VTForm&demographic_no=${demo}&formId=${formId}`, { waitUntil: 'domcontentloaded' });
    await page.locator('input[value="Update VT"], input[type="submit"]').first().waitFor({ state: 'visible', timeout: 20000 });
    await h.assertNotErrorPage(page, 'The Vascular Tracker form');
    // Show Detail reveals the medication and allergy lists, which the form hides until it is toggled.
    await page.evaluate(() => { if (typeof showHideDetail === 'function') showHideDetail(); });
    await inspect(f, 'vascular tracker form', page, fields, since, {
      // The patient's name is not printed by this form, so it is not expected here.
      expect: [...E('WC last value', 'WT last value', 'HC last value', 'WHRB last value', 'HR last value', 'HT last value', 'BP last value',
        'allergy description', 'drug custom name', 'ongoing concerns', 'smoking comment', 'foot exam comment')],
    });
  });
  await step('vital-sign controls have visible names and follow the keyboard reading order', async () => {
    h.assert(await page.locator('form [tabindex]').evaluateAll(elements => elements.every(element => element.tabIndex <= 0)),
      'Positive tab indices send keyboard users out of the clinical reading order');
    for (const code of ['BP', 'WC', 'HC', 'HR', 'HT', 'WT']) {
      const label = page.locator(`#vt-${code}-label`);
      const input = page.locator(`#vt-${code}-value`);
      h.assert((await label.textContent()).trim().length > 0, `The ${code} field has no visible name`);
      h.assert(await label.getAttribute('for') === await input.getAttribute('id'), `The ${code} label lost its field`);
      await input.focus();
      await page.keyboard.press('Tab');
      h.assert(await page.locator(`#vt-${code}-comments`).evaluate(element => element === document.activeElement),
        `Tab skipped the ${code} comment field`);
    }
  });
  await step('the page printed none of the stored markup as elements', async () => {
    h.assert(await page.locator('[data-xp]').count() === 0, 'Stored markup became an element on the Vascular Tracker form');
    f.assertNone('Vascular Tracker form');
  });
  await step('the foot-exam and smoking comment boxes hold exactly the stored text', async () => {
    h.assert(await page.locator('textarea[name="FTComments"]').inputValue() === stored.foot,
      'The foot-exam comment box does not hold the stored comment exactly (template whitespace or encoding changed it)');
    h.assert(await page.locator('input[name="SmkComments"]').inputValue() === stored.smoking,
      'The smoking comment box does not hold the stored comment exactly');
  });
  await page.close().catch(() => {});
}

module.exports = { workflow, vtMeasurements };
if (require.main === module) runWorkflow('vt-form-xss', workflow, { openPatient: false });
