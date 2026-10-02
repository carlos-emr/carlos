#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Stored-markup walk of the Messenger and the consultation screens.
// User path: Schedule > Msg (inbox) > message subject (view) > Compose (Search Patient/address book);
// Schedule > Consultations (list) > a request (the request form); Administration > Consultation Settings
// (services, specialists); Master Record > Consultations.
// Fixtures: an owned FAKE patient, a message (subject/body/sender/recipients) for the test provider
// attached to that patient, a consultation service and specialist, and a consultation request; every text
// column carries inert markup (INSERTed, bypassing the WAF). Cleanup removes exactly those rows by key.
// Asserted per surface: literal text visible, no `[data-xp]` element in any frame, no script error;
// findings are collected for the whole walk and the check fails once at the end.
// Implements: wave-6 xss-poison (stored markup / output-encoding walk).
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const ui = require('./lib/playwright-ui');
const { payload, inspect, Findings, Seeder, openMasterByChartNo, walkLinks, trackServiceScript } = require('./lib/xss-poison-helpers');
const { seedPatient } = require('./lib/xss-poison-patient');
const { releaseChartLocks } = require('./lib/chart-lock-cleanup');

async function workflow(s) {
  const fields = {};
  // Payload numbers start at 800: each check owns its own range, so a concurrent xss-poison run's rows on a
  // shared list are never mistaken for this run's (inspect() ignores a number it did not create).
  let n = 800;
  const P = (name, max = 255) => { n += 1; fields[n] = name; return payload(n, max); };
  const seed = new Seeder(s.sql, s.cleanup, s.marker);
  const hex = s.marker.slice(-8);
  const chartNo = `XM${hex.slice(0, 6)}`;
  let demo; let msg; let req; let bodyField;
  await s.step('seed the poisoned patient, message, consultation request and specialist', async () => {
    demo = seedPatient(seed, P, '999998', chartNo);
    const d = Number(demo);
    bodyField = n + 1;
    msg = seed.insert('messagetbl', { thedate: { raw: 'CURDATE()' }, theime: { raw: 'CURTIME()' }, themessage: P('message body'), thesubject: P('message subject', 128),
      sentby: P('message sender', 62), sentto: P('message recipients'), sentbyNo: '999998', sentByLocation: 145, actionstatus: 'N', type: 2 }, { key: 'messageid' });
    seed.insert('messagelisttbl', { message: Number(msg), provider_no: '999998', status: 'new', remoteLocation: 145, destinationFacilityId: 0, sourceFacilityId: 0 }, { key: 'id' });
    seed.insert('msgDemoMap', { messageID: Number(msg), demographic_no: d }, { key: 'id' });
    const service = seed.insert('consultationServices', { serviceDesc: P('consultation service'), active: '1' }, { key: 'serviceId' });
    trackServiceScript(seed, service);
    const spec = seed.insert('professionalSpecialists', { fName: P('specialist first name', 32), lName: P('specialist last name', 32), proLetters: P('specialist letters', 20), address: P('specialist address'),
      phone: '555', fax: '555', website: P('specialist website', 128), email: P('specialist email', 128), specType: P('specialist type', 128), lastUpdated: { raw: 'NOW()' }, annotation: P('specialist annotation'),
      salutation: '', institutionId: 0, departmentId: 0, hideFromView: 0, deleted: 0 }, { key: 'specId' });
    seed.insert('serviceSpecialists', { serviceId: Number(service), specId: Number(spec) }, { where: `serviceId=${service} AND specId=${spec}` });
    req = seed.insert('consultationRequests', { referalDate: { raw: 'CURDATE()' }, serviceId: Number(service), specId: Number(spec), reason: P('consult reason'), clinicalInfo: P('consult clinical info'),
      currentMeds: P('consult current meds'), allergies: P('consult allergies'), providerNo: '999998', demographicNo: d, status: '1', statusText: P('consult status text'), concurrentProblems: P('consult concurrent problems'),
      urgency: '2', appointmentInstructions: P('consult appointment instructions', 256), patientWillBook: 0, site_name: P('consult site name'), letterheadName: P('consult letterhead name'),
      letterheadAddress: P('consult letterhead address'), lastUpdateDate: { raw: 'NOW()' } }, { key: 'requestId' });
  });
  const f = new Findings(s.recorder);
  const step = f.stepper(s);
  // The message body is Markdown composed in the Toast UI editor and shown through its viewer with DOMPurify
  // (ViewMessage.jsp), so inert markup in it is meant to render; every other field must stay literal text.
  f.richText.push({ field: bodyField, path: '/messenger/ViewMessage' });
  const topBar = async (selector, label) => ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator(selector).first(),
    { context: s.context, label, recorder: s.recorder, timeout: 20000 });
  await step('Msg inbox, the message view and Compose show stored values as text', async () => {
    const { page: inbox, isPopup } = await topBar('a[onclick*="DisplayMessages"]', 'messenger');
    let since = f.mark();
    await inspect(f, 'messenger inbox', inbox, fields, since);
    const subject = inbox.locator(`a[href*="ViewMessage?messageID=${msg}&"]`).first();
    if (await subject.count()) {
      await ui.clickAndAwaitReload(inbox, subject, { timeout: 20000, label: 'message view' });
      since = f.mark();
      await inspect(f, 'message view', inbox, fields, since);
      await inbox.goBack().catch(() => {});
    } else f.add('messenger inbox', 'MISSING', 'the seeded message is not listed in the test provider inbox');
    const compose = inbox.locator('a[href*="CreateMessage"], input[value*="Compose" i], a:has-text("Compose")').first();
    if (await compose.count()) {
      await ui.clickAndAwaitReload(inbox, compose, { timeout: 20000, label: 'compose' });
      since = f.mark();
      await inspect(f, 'message compose', inbox, fields, since);
    }
    if (isPopup) await inbox.close(); else await s.schedule.goto(s.schedule.url());
  });
  await step('Consultations list and the request form show stored values as text', async () => {
    const { page: list, isPopup } = await topBar('a[onclick*="IncomingConsultation"]', 'consultations');
    let since = f.mark();
    await inspect(f, 'consultation list', list, fields, since);
    const row = list.locator(`tr[onclick*="requestId=${req}"], tr[onclick*="requestId=${req}&"]`).first();
    if (await row.count()) {
      const form = await ui.clickOpensPopupOrNavigates(list, row, { context: s.context, label: 'consult-form', recorder: s.recorder, timeout: 20000 });
      since = f.mark();
      await inspect(f, 'consultation request form', form.page, fields, since);
      if (form.isPopup) await form.page.close(); else await form.page.goBack().catch(() => {});
    } else f.add('consultation list', 'MISSING', 'the seeded request is not listed');
    if (isPopup) await list.close(); else await s.schedule.goto(s.schedule.url());
  });
  await step('Master Record > Consultations shows stored values as text', async () => {
    const { master } = await openMasterByChartNo(s, chartNo);
    const link = master.locator('a').filter({ hasText: /^\s*Consultations?\s*$/i }).first();
    if (await link.count()) {
      await walkLinks({ context: s.context, recorder: s.recorder, host: master, findings: f, fields, label: 'master',
        items: [{ text: 'Consultations', index: 0, selector: 'a:text-matches("^\\\\s*Consultations?\\\\s*$", "i")' }],
        beforeClose: page => releaseChartLocks(s.context, s.config.baseUrl, [page]).catch(() => {}) });
    }
    await master.close();
  });
  await step('the walk found no output-encoding defect', async () => { f.assertNone('Messenger and consultation walk'); });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('xss-poison-messenger-consult', workflow, { openPatient: false });
