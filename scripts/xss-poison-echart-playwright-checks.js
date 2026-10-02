#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Stored-markup walk of the E-Chart and its navigation modules.
// User path: Schedule > Search > Chart No > Master Demographic File > E-Chart, then every navbar link
// (the same catalogue as echart-navbar-modules).
// Fixtures: one owned FAKE patient and chart rows (progress note, CPP notes, allergy, drug, measurement,
// prevention, document, eForm instance, tickler, consultation request) whose text columns carry inert
// markup (INSERTed, bypassing the WAF). Cleanup removes exactly these rows by key and asserts they are gone.
// Asserted per page: literal text visible, no `[data-xp]` element in any frame, no script error, and the
// seeded values the chart and the named modules show are shown; a navbar link that cannot be opened is a
// NOT-OPENED finding unless the fixture cannot back it. Findings are collected across the walk and the check
// fails once at the end.
// Implements: wave-6 xss-poison (stored markup / output-encoding walk).
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const ui = require('./lib/playwright-ui');
const { catalogueLinks, dedupe } = require('./lib/playwright-link-audit');
const { NAVBAR_SELECTOR, SKIP_ITEMS, openChart, waitForNavbars } = require('./echart-navbar-modules-playwright-checks');
const { releaseChartLocks } = require('./lib/chart-lock-cleanup');
const { payload, inspect, Findings, Seeder, openMasterByChartNo, walkLinks, fieldIds } = require('./lib/xss-poison-helpers');
const { seedPatient } = require('./lib/xss-poison-patient');

async function workflow(s) {
  const fields = {};
  // Payload numbers start at 500: each check owns its own range, so a concurrent xss-poison run's rows on a
  // shared list are never mistaken for this run's (inspect() ignores a number it did not create).
  let n = 500;
  const P = (name, max = 255) => { n += 1; fields[n] = name; return payload(n, max); };
  const seed = new Seeder(s.sql, s.cleanup, s.marker);
  const hex = s.marker.slice(-8);
  const chartNo = `XE${hex}`;
  let demo;
  await s.step('seed the poisoned patient and chart rows', async () => {
    demo = seedPatient(seed, P, '999998', chartNo);
    const d = Number(demo);
    const note = (text, extra = {}) => seed.insert('casemgmt_note', {
      update_date: { raw: 'NOW()' }, observation_date: { raw: 'NOW()' }, demographic_no: d, provider_no: '999998', note: text,
      signed: 1, include_issue_innote: 0, signing_provider_no: '999998', encounter_type: '', billing_code: '', program_no: '10034',
      reporter_caisi_role: '2', reporter_program_team: '0', history: text, locked: '0', archived: 0, position: 0, uuid: { raw: 'UUID()' }, ...extra,
    }, { key: 'note_id' });
    note(P('progress note'), { encounter_type: P('note encounter type', 100) });
    for (const [issue, label] of [[66, 'social history'], [67, 'medical history'], [68, 'ongoing concerns'], [69, 'reminders'], [70, 'family history'], [71, 'risk factors'], [65, 'other meds']]) {
      const noteId = note(P(`CPP ${label} note`));
      const ci = seed.insert('casemgmt_issue', { demographic_no: d, issue_id: issue, acute: 0, certain: 0, major: 0, resolved: 0, program_id: 10034, type: 'nurse', update_date: { raw: 'NOW()' } }, { key: 'id' });
      seed.insert('casemgmt_issue_notes', { id: Number(ci), note_id: Number(noteId) }, { where: `id=${ci} AND note_id=${noteId}` });
    }
    seed.insert('allergies', { demographic_no: d, entry_date: { raw: 'CURDATE()' }, DESCRIPTION: P('allergy description', 50), reaction: P('allergy reaction'), TYPECODE: 0, archived: 0, position: 0, lastUpdateDate: { raw: 'NOW()' }, nonDrug: 1, severity_of_reaction: '2', onset_of_reaction: '1' }, { key: 'allergyid' });
    seed.insert('drugs', { provider_no: '999998', demographic_no: d, rx_date: { raw: 'CURDATE()' }, end_date: { raw: 'DATE_ADD(CURDATE(), INTERVAL 30 DAY)' }, written_date: { raw: 'CURDATE()' }, customName: P('drug custom name', 60), GN: P('drug generic name'), BN: P('drug brand name'), special: P('drug instructions'), archived: 0, GCN_SEQNO: 0, script_no: 0, create_date: { raw: 'NOW()' }, quantity: '1', repeat: 0, long_term: 1 }, { key: 'drugid' });
    seed.insert('measurements', { type: 'HT', demographicNo: d, providerNo: '999998', dataField: '170', measuringInstruction: P('measuring instruction'), comments: P('measurement comment'), dateObserved: { raw: 'NOW()' }, dateEntered: { raw: 'NOW()' } }, { key: 'id' });
    const prev = seed.insert('preventions', { demographic_no: d, creation_date: { raw: 'NOW()' }, prevention_date: { raw: 'NOW()' }, provider_no: '999998', provider_name: P('prevention provider name'), prevention_type: 'Flu', deleted: '0', refused: '0', never: '0', creator: 999998, lastUpdateDate: { raw: 'NOW()' } }, { key: 'id' });
    seed.insert('preventionsExt', { prevention_id: Number(prev), keyval: 'comments', val: P('prevention comment') }, { key: 'id' });
    seed.insert('preventionsExt', { prevention_id: Number(prev), keyval: 'location', val: P('prevention location') }, { key: 'id' });
    const doc = seed.insert('document', { doctype: 'consult', docdesc: P('document description'), docfilename: 'xp.pdf', doccreator: '999998', responsible: '999998', source: P('document source', 60), sourceFacility: P('document source facility', 120), updatedatetime: { raw: 'NOW()' }, status: 'A', contenttype: 'application/pdf', contentdatetime: { raw: 'NOW()' }, public1: 0, observationdate: { raw: 'CURDATE()' }, number_of_pages: 1, restrictToProgram: 0, abnormal: 0, reviewer: '' }, { key: 'document_no' });
    seed.insert('ctl_document', { module: 'demographic', module_id: d, document_no: Number(doc), status: 'A' }, { where: `module='demographic' AND module_id=${d} AND document_no=${doc}` });
    // The instance must hang off an active template, or no eForm screen lists it and its coverage is silently lost.
    const eform = s.sql.value('SELECT MIN(fid) FROM eform WHERE status=1');
    h.assert(/^[1-9]\d*$/.test(eform), 'No active eForm template exists to attach the seeded eForm instance to');
    seed.insert('eform_data', { fid: Number(eform), form_name: P('eform instance name'), subject: P('eform instance subject'), demographic_no: d, status: 1, form_date: { raw: 'CURDATE()' }, form_time: { raw: 'CURTIME()' }, form_provider: '999998', form_data: '<html><body><form name="FormName" method="post" action=""><input type="text" name="note"></form></body></html>', showLatestFormOnly: 0, patient_independent: 0 }, { key: 'fdid' });
    seed.insert('tickler', { demographic_no: d, message: P('tickler message'), status: 'A', update_date: { raw: 'NOW()' }, service_date: { raw: 'DATE_SUB(NOW(), INTERVAL 1 DAY)' }, creator: '999998', priority: 'Normal', task_assigned_to: '999998' }, { key: 'tickler_no' });
    seed.insert('consultationRequests', { referalDate: { raw: 'CURDATE()' }, serviceId: 1, reason: P('consult reason'), clinicalInfo: P('consult clinical info'), currentMeds: P('consult current meds'), allergies: P('consult allergies'), providerNo: '999998', demographicNo: d, status: '1', statusText: P('consult status text'), concurrentProblems: P('consult concurrent problems'), urgency: '2', appointmentInstructions: P('consult appointment instructions', 256), patientWillBook: 0, site_name: P('consult site name'), letterheadName: P('consult letterhead name'), letterheadAddress: P('consult letterhead address'), lastUpdateDate: { raw: 'NOW()' } }, { key: 'requestId' });
  });
  const f = new Findings(s.recorder);
  const step = f.stepper(s);
  // The seeded document row has no file behind it, so its viewer legitimately answers 500: fixture limit, not a finding.
  f.ignoredPaths.push('/documentManager/ManageDocument');
  const E = (...names) => fieldIds(fields, ...names);
  const patient = E('patient last name', 'patient first name');
  let master;
  await step('open the E-Chart from the Master Record', async () => {
    ({ master } = await openMasterByChartNo(s, chartNo));
  });
  let chart;
  await step('E-Chart header, notes and navigation show stored values as text', async () => {
    chart = await openChart(s.context, master, s.recorder, 20000);
    await waitForNavbars(chart, 20000);
    await chart.waitForLoadState('networkidle').catch(() => {});
    const since = f.mark();
    await inspect(f, 'e-chart', chart, fields, since, { expect: [...patient, ...E('progress note', 'CPP social history note', 'CPP medical history note',
      'allergy description', 'drug instructions', 'document description', 'eform instance name', 'tickler message')] });
  });
  await step('walk every E-Chart navigation link', async () => {
    const items = dedupe(await catalogueLinks(chart, { selector: NAVBAR_SELECTOR, identity: true }));
    h.assert(items.length > 0, 'The E-Chart offered no navigation links');
    const [docField] = E('document description');
    await walkLinks({ context: s.context, recorder: s.recorder, host: chart, items, findings: f, fields, timeout: 40000, label: 'echart', skip: SKIP_ITEMS,
      // Named modules the walk must reach, with the seeded values each is known to show.
      expect: [
        { match: /^Medications$/, fields: E('drug instructions'), page: true },
        { match: /^Tickler$/, fields: [...patient, ...E('tickler message')], page: true },
        { match: /^eForms$/, fields: [...patient, ...E('eform instance name')], page: true },
        { match: /^Disease Registry$/, fields: patient, page: true },
      ],
      optional: [
        { match: /^\d{2}-[A-Za-z]{3}-\d{4}$/, reason: 'a dated encounter entry the chart re-renders while it is walked' },
        { match: /^[\u25cb\u26a0]\s/, reason: 'a prevention-due entry the chart redraws once a prevention page has been opened' },
        { match: new RegExp(`data-xp="?${docField}[">]`), reason: 'the seeded document has no file behind it, so its viewer never finishes loading' },
      ],
      beforeItem: () => waitForNavbars(chart, 20000),
      beforeClose: page => releaseChartLocks(s.context, s.config.baseUrl, [page]).catch(() => {}) });
    await releaseChartLocks(s.context, s.config.baseUrl, [chart]);
  });
  await step('the walk found no output-encoding defect', async () => { f.assertNone('E-Chart walk'); });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('xss-poison-echart', workflow, { openPatient: false });
