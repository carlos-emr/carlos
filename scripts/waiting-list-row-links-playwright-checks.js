#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Schedule > Report > Waiting List: the links each manager row renders for an existing entry.
// Issue #5477 replaced the last OWASP encoder taglib call (e:forUriComponent) in the patient-name
// link with carlos:forUriComponent and removed encoder-jakarta-jsp from the WAR. The row-edit,
// remove and booking checks find rows through that link but never follow it; this check pins
// every row link to its own patient and opens the Master Record from it.
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { prepareWaitingLists } = require('./lib/waiting-list-ui-workflow');

async function workflow(s) {
  const f = await prepareWaitingLists(s, { members: true });
  h.assert(f.patients.length === 2, 'The fixture needs the workflow patient and one control patient');
  // The IDs are interpolated into SQL, selectors and patterns below; accept positive integers only.
  for (const patient of f.patients) h.assert(/^[1-9]\d*$/.test(String(patient)), `Unexpected patient ID ${patient}`);
  h.assert(/^[1-9]\d*$/.test(String(f.list.id)), `Unexpected list ID ${f.list.id}`);
  // DisplayWaitingList.jsp prints request.getContextPath() in front of each route.
  const managerPath = new URL(f.manager.url()).pathname;
  const contextPath = managerPath.slice(0, managerPath.indexOf('/waitinglist/'));
  h.assert(managerPath.startsWith(`${contextPath}/waitinglist/`), `Unexpected manager URL ${managerPath}`);
  const lastName = patient => s.sql.value(`SELECT last_name FROM demographic WHERE demographic_no=${patient}`);
  const firstName = patient => s.sql.value(`SELECT first_name FROM demographic WHERE demographic_no=${patient}`);
  const masterButtonSelector = 'button[type="button"][onclick*="popupDemographicPage("]';

  await s.step('each row renders its own patient in the Master Record, Make Appointment and Remove controls', async () => {
    const masterButtons = f.manager.locator(masterButtonSelector);
    h.assert(await masterButtons.count() === f.patients.length,
      `The list shows ${await masterButtons.count()} Master Record buttons for ${f.patients.length} entries`);
    for (const patient of f.patients) {
      const row = f.patientRow(patient);
      h.assert(await row.count() === 1, `No single manager row for patient ${patient}`);
      const master = row.locator(masterButtonSelector);
      h.assert(await master.count() === 1, `Row ${patient} has no single Master Record button`);
      // Byte-for-byte the onclick the OWASP taglib expression rendered for a numeric ID.
      h.assert(await master.getAttribute('onclick')
        === `popupDemographicPage('${contextPath}/demographic/DemographicEdit?demographic_no=${patient}'); return false;`,
        `Row ${patient} Master Record action changed: ${await master.getAttribute('onclick')}`);
      h.assert((await master.innerText()).trim() === `${lastName(patient)}, ${firstName(patient)}`,
        `Row ${patient} button does not name its patient`);
      const appointment = row.locator('a[onclick*="popupPage("]');
      h.assert(await appointment.count() === 1, `Row ${patient} has no single Make Appointment link`);
      const appointmentOnClick = await appointment.getAttribute('onclick');
      const appointmentPatient = /^popupPage\(\d+, '([1-9]\d*)', '[^']+', 400, 780, /.exec(appointmentOnClick)?.[1];
      h.assert(appointmentPatient === patient, `Row ${patient} Make Appointment names another patient`);
      const remove = row.locator('a[onclick*="removePatient("]');
      h.assert(await remove.count() === 1, `Row ${patient} has no single Remove link`);
      h.assert(await remove.getAttribute('onclick') === `removePatient('${patient}', '${f.list.id}');`,
        `Row ${patient} Remove link names another patient or list: ${await remove.getAttribute('onclick')}`);
    }
    // No row may print an empty or literal "null" demographic number into its Master Record action.
    for (const onclick of await masterButtons.evaluateAll(buttons => buttons.map(button => button.getAttribute('onclick')))) {
      h.assert(/demographic_no=[1-9]\d*'\); return false;$/.test(onclick), `Malformed Master Record action: ${onclick}`);
    }
  });

  await s.step('clicking each row button opens that patient\'s Master Record', async () => {
    for (const patient of f.patients) {
      const master = await s.popup(f.manager, f.patientRow(patient).locator(masterButtonSelector),
        `waiting-list-master-record-${patient}`);
      try {
        await master.waitForURL(/\/demographic\/DemographicEdit\?/, { timeout: 20000 });
        const url = new URL(master.url());
        h.assert(url.pathname === `${contextPath}/demographic/DemographicEdit`, `Opened ${url.pathname}`);
        h.assert(JSON.stringify(url.searchParams.getAll('demographic_no')) === JSON.stringify([patient]),
          `The Master Record opened for ${url.search}, not demographic ${patient}`);
        await h.assertNotErrorPage(master, `Master Record for waiting-list row ${patient}`);
        const header = master.locator('.patient-header-name');
        await header.waitFor({ timeout: 20000 });
        h.assert((await header.innerText()).trim() === `${lastName(patient)}, ${firstName(patient)}`,
          `The Master Record opened from row ${patient} shows another patient`);
      } finally {
        await master.close();
      }
    }
  });
}
if (require.main === module) runWorkflow('waiting-list-row-links', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
