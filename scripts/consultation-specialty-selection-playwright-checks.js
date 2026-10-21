#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Issue #4188: imported specialty names must not be coerced to numeric service IDs.
// Open each owned specialist through Administration > Consultation Settings and
// verify the rendered editor, its HTTP response and the selected specialty.
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { clickAdminItem } = require('./lib/xss-poison-helpers');

async function workflow(s) {
  const { sql, marker } = s;
  const serviceName = `${marker} Specialty`;
  const owner = `fName=${h.sqlString(marker)}`;
  // Register before inserting, so a partially seeded run also cleans up.
  s.cleanup(() => {
    sql.execute(`DELETE FROM professionalSpecialists WHERE ${owner};
      DELETE FROM consultationServices WHERE serviceDesc=${h.sqlString(serviceName)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM professionalSpecialists WHERE ${owner})
      + (SELECT COUNT(*) FROM consultationServices WHERE serviceDesc=${h.sqlString(serviceName)})`) === '0',
    'Owned specialty-selection fixtures were not removed');
  });
  const serviceId = h.insertId(sql, `INSERT INTO consultationServices(serviceDesc, active)
    VALUES(${h.sqlString(serviceName)}, '1')`, 'specialty service');
  const cases = [
    { label: 'legacy specialty name', value: 'Cardiology', selected: '0' },
    { label: 'unrecognised text', value: 'FAKE <b>specialty</b> & other', selected: '0' },
    { label: 'matching service ID', value: serviceId, selected: serviceId },
    { label: 'unmatched service ID', value: '2147483647', selected: '0' },
    { label: 'zero service ID', value: '0', selected: '0' },
    { label: 'empty specialty', value: '', selected: '0' },
    { label: 'null specialty', value: null, selected: '0' },
  ];
  for (const [index, entry] of cases.entries()) {
    entry.id = h.insertId(sql, `INSERT INTO professionalSpecialists
      (fName,lName,specType,lastUpdated,institutionId,departmentId,hideFromView,deleted)
      VALUES(${h.sqlString(marker)},'Specialty${index}',${entry.value === null ? 'NULL' : h.sqlString(entry.value)},
        NOW(),0,0,0,0)`, 'specialist');
  }

  const { page: admin } = await clickOpensPopupOrNavigates(s.schedule,
    s.schedule.locator('#admin-panel, #admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'specialty-administration', timeout: 20000 });
  for (const entry of cases) {
    await s.step(`${entry.label} opens HTTP 200 with the expected specialty selected`, async () => {
      await clickAdminItem(admin, 'Consultation Settings');
      const iframe = admin.locator('#dynamic-content iframe').first();
      await iframe.waitFor({ timeout: 20000 });
      const frame = await (await iframe.elementHandle()).contentFrame();
      h.assert(frame, 'Consultation Settings did not load its frame');
      const link = frame.locator(`a[href$="/encounter/EditSpecialists?specId=${entry.id}"]`);
      const [response] = await Promise.all([
        frame.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 20000 }),
        link.click({ timeout: 20000 }),
      ]);
      h.assert(response && response.status() === 200, `${entry.label}: specialist editor did not answer HTTP 200`);
      await h.assertNotErrorPage(frame, entry.label);
      h.assert(await frame.locator('#specId').inputValue() === entry.id, 'The editor opened another specialist');
      h.assert(await frame.locator('#firstName').inputValue() === marker, 'The editor did not display the stored name');
      h.assert(await frame.locator('#specType').inputValue() === entry.selected,
        `${entry.label}: the editor selected the wrong specialty`);
      h.assert(sql.value(`SELECT COUNT(*) FROM professionalSpecialists WHERE specId=${entry.id}
        AND specType <=> ${entry.value === null ? 'NULL' : h.sqlString(entry.value)}`) === '1',
      'Opening the editor changed the stored specialty');
    });
  }
}

module.exports = { workflow };
if (require.main === module) runWorkflow('consultation-specialty-selection', workflow, { openPatient: false });
