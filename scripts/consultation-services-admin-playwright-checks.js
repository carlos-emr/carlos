#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Coverage plan §3.3 consultation-services-admin. User path: Schedule ▸ Consultations ▸
// "Consultation configuration" popup (EctConTitlebar menu) ▸ Add Service / Show All Services /
// Delete Services / Show All Institutions / Enable Consultation Request/Response, plus
// Master Record ▸ Consultation ▸ New Consultation for the owned patient.
// Asserts: a blank service name is refused by its alert; AddService persists an active row;
// the owned consultant is assigned to it and the new-consultation pickers offer exactly that
// consultant for it; DelService honours its confirm, inactivates only the checked row and the
// service stops being offered; UpdateInstitutionDepartment adds and then clears the owned
// department link; EnableConRequestResponse stores both flags and toggles the "Referring Doctor"
// service. Fixtures: FAKE- patient (runWorkflow), FAKE- specialist, institution, department and
// a sentinel service seeded by SQL; the UI-created service. Cleanup deletes only owned rows.
// GLOBAL: the request/response flags and the Referring Doctor service are snapshotted and
// restored exactly, so this check must run with EXCLUSIVE=1.
const h = require('./lib/playwright-harness');
const { clickAndAwaitReload, clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const FLAGS = ['consultRequestEnabled', 'consultResponseEnabled'];
const REFERRING = 'Referring Doctor';

function insertId(sql, statement, what) {
  const id = sql.value(`${statement}; SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(id), `The owned ${what} fixture was not created`);
  return id;
}

/** Snapshot of the clinic-wide request/response switch: two property rows and one service. */
function snapshotSwitch(sql) {
  const names = FLAGS.map(h.sqlString).join(',');
  return {
    properties: sql.rows(`SELECT id,name,IFNULL(value,'<NULL>') FROM property WHERE name IN (${names}) ORDER BY id`),
    services: sql.rows(`SELECT serviceId,IFNULL(active,'<NULL>') FROM consultationServices
      WHERE serviceDesc=${h.sqlString(REFERRING)} ORDER BY serviceId`),
  };
}

function restoreSwitch(sql, before) {
  const names = FLAGS.map(h.sqlString).join(',');
  const keepProps = before.properties.map(row => row[0]);
  const keepServices = before.services.map(row => row[0]);
  sql.execute(`DELETE FROM property WHERE name IN (${names})
    ${keepProps.length ? `AND id NOT IN (${keepProps.join(',')})` : ''}`);
  for (const [id, , value] of before.properties) {
    sql.execute(`UPDATE property SET value=${value === '<NULL>' ? 'NULL' : h.sqlString(value)} WHERE id=${id}`);
  }
  // A Referring Doctor row that did not exist before was created by this check's toggle.
  sql.execute(`DELETE FROM consultationServices WHERE serviceDesc=${h.sqlString(REFERRING)}
    ${keepServices.length ? `AND serviceId NOT IN (${keepServices.join(',')})` : ''}`);
  for (const [id, active] of before.services) {
    sql.execute(`UPDATE consultationServices SET active=${active === '<NULL>' ? 'NULL' : h.sqlString(active)} WHERE serviceId=${id}`);
  }
  const after = snapshotSwitch(sql);
  h.assert(JSON.stringify(after) === JSON.stringify(before),
    'The consultation request/response switch was not restored to its snapshot');
}

async function openNewConsultation(s, label) {
  const list = await s.popup(s.master,
    s.master.locator("a[onclick*='ViewDisplayDemographicConsultationRequests']"), `${label}-list`);
  const form = await s.popup(list, list.locator('a.btn', { hasText: /New Consultation/i }), label);
  await form.locator('#EctConsultationFormRequest2Form').waitFor({ state: 'attached' });
  h.assert(await form.locator('#demographicNo').inputValue() === s.patient,
    'The consultation form was opened for a patient other than the owned fixture');
  return { list, form };
}

/** Labels of the jQuery UI menu entries the service picker offers for a typed term. */
async function offeredServices(form, term) {
  const input = form.locator('#serviceInput');
  await input.click();
  await input.fill('');
  await input.type(term, { delay: 30 });
  const items = form.locator('ul.ui-autocomplete:visible li');
  await items.first().waitFor({ state: 'visible' }).catch(() => {});
  return (await items.allInnerTexts()).map(text => text.trim());
}

async function workflow(s) {
  const { sql, marker } = s;
  const serviceName = `${marker}-Svc`;
  const sentinelName = `${marker}-Keep`;
  const lastName = `${marker}-Spec`;
  const before = snapshotSwitch(sql);
  let serviceId;
  // Registered first so it runs last: the switch is restored after every owned row is gone.
  s.cleanup(() => restoreSwitch(sql, before));
  const ownedServices = `serviceDesc IN (${h.sqlString(serviceName)},${h.sqlString(sentinelName)})`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM serviceSpecialists WHERE serviceId IN
      (SELECT serviceId FROM consultationServices WHERE ${ownedServices});
      DELETE FROM consultationServices WHERE ${ownedServices}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM consultationServices WHERE ${ownedServices}`) === '0',
      'Owned consultation services were not removed');
  });
  const sentinel = insertId(sql, `INSERT INTO consultationServices(serviceDesc,active)
    VALUES(${h.sqlString(sentinelName)},'1')`, 'sentinel service');
  s.cleanup(() => {
    sql.execute(`DELETE FROM serviceSpecialists WHERE specId IN
      (SELECT specId FROM professionalSpecialists WHERE lName=${h.sqlString(lastName)});
      DELETE FROM professionalSpecialists WHERE lName=${h.sqlString(lastName)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM professionalSpecialists WHERE lName=${h.sqlString(lastName)}`) === '0',
      'The owned specialist was not removed');
  });
  const specId = insertId(sql, `INSERT INTO professionalSpecialists
    (fName,lName,phone,fax,streetAddress,lastUpdated,institutionId,departmentId,hideFromView,deleted)
    VALUES('Ann',${h.sqlString(lastName)},'555-0150','555-0151','1 Synthetic Way',NOW(),0,0,0,0)`, 'specialist');
  const institutionName = `${marker}-Inst`;
  const departmentName = `${marker}-Dept`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM InstitutionDepartment WHERE institutionId IN
      (SELECT id FROM Institution WHERE name=${h.sqlString(institutionName)});
      DELETE FROM Institution WHERE name=${h.sqlString(institutionName)};
      DELETE FROM Department WHERE name=${h.sqlString(departmentName)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM Institution WHERE name=${h.sqlString(institutionName)})
      + (SELECT COUNT(*) FROM Department WHERE name=${h.sqlString(departmentName)})`) === '0',
    'Owned institution or department was not removed');
  });
  const institutionId = insertId(sql, `INSERT INTO Institution(name) VALUES(${h.sqlString(institutionName)})`, 'institution');
  const departmentId = insertId(sql, `INSERT INTO Department(name) VALUES(${h.sqlString(departmentName)})`, 'department');

  const { page: list } = await clickOpensPopupOrNavigates(s.schedule,
    s.schedule.getByRole('link', { name: 'Consultations', exact: true }),
    { context: s.context, recorder: s.recorder, label: 'consultations' });
  const config = await s.popup(list, list.locator('a[href*="ViewShowAllServices"]'), 'consultation-config');
  const menu = suffix => clickAndAwaitReload(config, config.locator(`nav a[href$="/${suffix}"]`), { label: suffix });

  await s.step('Add Service refuses a blank name and persists a named service as active', async () => {
    await menu('ViewAddService');
    const blank = await h.withExpectedDialogs(config, () => config.locator('input[type="submit"]').click());
    h.assert(blank.length === 1 && blank[0].type === 'alert', 'A blank service name was not refused by its alert');
    h.assert(new URL(config.url()).pathname.endsWith('/ViewAddService'), 'The blank service name was submitted');
    await config.locator('#service').fill(serviceName);
    await clickAndAwaitReload(config, config.locator('input[type="submit"]'));
    h.assert((await config.locator('.alert-success').innerText()).includes(serviceName),
      'Add Service did not confirm the new service');
    serviceId = sql.value(`SELECT serviceId FROM consultationServices WHERE serviceDesc=${h.sqlString(serviceName)}`);
    h.assert(/^[1-9]\d*$/.test(serviceId), 'Add Service did not persist exactly one service row');
    h.assert(sql.value(`SELECT active FROM consultationServices WHERE serviceId=${serviceId}`) === '1',
      'The added service is not active');
  });

  await s.step('Show All Services assigns the owned consultant to the new service', async () => {
    await menu('ViewShowAllServices');
    await clickAndAwaitReload(config, config.getByRole('link', { name: serviceName, exact: true }));
    const box = config.locator(`input[name="specialists"][value="${specId}"]`);
    h.assert(!(await box.isChecked()), 'A new service already lists the owned consultant');
    await box.check();
    await clickAndAwaitReload(config, config.locator('form[action$="/UpdateServiceSpecialists"] input[type="submit"]'));
    h.assert(sql.value(`SELECT GROUP_CONCAT(specId) FROM serviceSpecialists WHERE serviceId=${serviceId}`) === specId,
      'The service-consultant assignment did not persist exactly the owned consultant');
  });

  await s.step('a new consultation for the owned patient offers the service and only its consultant', async () => {
    const { list: patientList, form } = await openNewConsultation(s, 'new-consultation');
    const offered = await offeredServices(form, marker);
    h.assert(offered.includes(serviceName) && offered.includes(sentinelName),
      'The service picker does not offer the owned active services');
    await form.locator('ul.ui-autocomplete:visible li', { hasText: serviceName }).first().click();
    h.assert(await form.locator('#service').inputValue() === serviceId, 'Picking the service did not set its id');
    await form.locator('#specialistInput').click();
    const consultants = form.locator('ul.ui-autocomplete:visible li');
    await consultants.first().waitFor({ state: 'visible' });
    h.assert(await consultants.count() === 1 && (await consultants.first().innerText()).includes(lastName),
      'The consultant picker did not offer exactly the consultant assigned to the service');
    await consultants.first().click();
    h.assert(await form.locator('#specialist').inputValue() === specId, 'Picking the consultant did not set its id');
    h.assert(await form.locator('[name="fax"]').inputValue() === '555-0151', 'The consultant fax was not copied');
    await form.close();
    await patientList.close();
  });

  await s.step('Delete Services: cancel keeps, confirm inactivates only the checked service', async () => {
    await menu('ViewDeleteServices');
    await config.locator(`input[name="service"][value="${serviceId}"]`).check();
    const remove = config.locator('input[name="delete"]');
    const cancel = await h.withExpectedDialogs(config, () => remove.click(), { accept: false });
    h.assert(cancel.length === 1 && cancel[0].type === 'confirm', 'Delete Services omitted its confirmation');
    h.assert(sql.value(`SELECT active FROM consultationServices WHERE serviceId=${serviceId}`) === '1',
      'A cancelled delete changed the service');
    const accept = await h.withExpectedDialogs(config, () => clickAndAwaitReload(config, remove));
    h.assert(accept.length === 1 && accept[0].type === 'confirm', 'Delete Services bypassed its confirmation');
    await expectValue(sql, `SELECT active FROM consultationServices WHERE serviceId=${serviceId}`, '02',
      'Delete Services did not inactivate the checked service');
    h.assert(sql.value(`SELECT active FROM consultationServices WHERE serviceId=${sentinel}`) === '1',
      'Delete Services changed an unchecked service');
    await menu('ViewShowAllServices');
    h.assert(await config.getByRole('link', { name: serviceName, exact: true }).count() === 0,
      'The deleted service is still listed');
    h.assert(await config.getByRole('link', { name: sentinelName, exact: true }).count() === 1,
      'The unchecked service is no longer listed');
  });

  await s.step('a deleted service is no longer offered for a new consultation', async () => {
    const { list: patientList, form } = await openNewConsultation(s, 'new-consultation-after-delete');
    const offered = await offeredServices(form, marker);
    h.assert(offered.includes(sentinelName), 'The still-active owned service is not offered');
    h.assert(!offered.includes(serviceName), 'The deleted service is still offered');
    await form.close();
    await patientList.close();
  });

  await s.step('Show All Institutions links and then clears the owned department', async () => {
    const link = suffix => `SELECT COUNT(*) FROM InstitutionDepartment WHERE institutionId=${institutionId}${suffix}`;
    const openInstitution = async () => {
      await menu('ViewShowAllInstitutions');
      await clickAndAwaitReload(config, config.getByRole('link', { name: institutionName, exact: true }));
      return config.locator(`input[name="specialists"][value="${departmentId}"]`);
    };
    const update = () => clickAndAwaitReload(config, config.locator('form[action$="/UpdateInstitutionDepartment"] input[type="submit"]'));
    let box = await openInstitution();
    h.assert(!(await box.isChecked()), 'A new institution already lists the owned department');
    await box.check();
    await update();
    h.assert(sql.value(link(` AND departmentId=${departmentId}`)) === '1' && sql.value(link('')) === '1',
      'The institution-department link did not persist exactly the owned department');
    box = await openInstitution();
    h.assert(await box.isChecked(), 'The saved department is not shown as linked');
    await box.uncheck();
    await update();
    h.assert(sql.value(link('')) === '0', 'Unchecking the department did not remove the link');
  });

  const referringActive = `SELECT GROUP_CONCAT(active ORDER BY serviceId) FROM consultationServices
    WHERE serviceDesc=${h.sqlString(REFERRING)}`;
  const flag = name => `SELECT IFNULL(GROUP_CONCAT(IFNULL(value,'NULL')),'') FROM property WHERE name=${h.sqlString(name)}`;
  const submitSwitch = async () => {
    await clickAndAwaitReload(config, config.locator('form[action$="/EnableConRequestResponse"] input[type="submit"]'));
    await config.locator('.alert-success').waitFor({ state: 'visible' });
  };
  await s.step('Enable Request/Response stores both flags and activates Referring Doctor', async () => {
    await menu('ViewEnableRequestResponse');
    await config.locator('#reqEnabled').check();
    await config.locator('#respEnabled').check();
    await submitSwitch();
    h.assert(sql.value(flag('consultRequestEnabled')) === 'Y' && sql.value(flag('consultResponseEnabled')) === 'Y',
      'The request/response flags were not stored');
    h.assert(sql.value(referringActive) === '1', 'Enabling responses did not activate the Referring Doctor service');
    await menu('ViewEnableRequestResponse');
    h.assert(await config.locator('#reqEnabled').isChecked() && await config.locator('#respEnabled').isChecked(),
      'The saved request/response flags did not render back');
    await menu('ViewShowAllServices');
    h.assert(await config.getByRole('link', { name: REFERRING, exact: true }).count() === 1,
      'The activated Referring Doctor service is not listed');
  });

  await s.step('disabling responses clears the flag and inactivates Referring Doctor', async () => {
    await menu('ViewEnableRequestResponse');
    await config.locator('#respEnabled').uncheck();
    await submitSwitch();
    h.assert(sql.value(flag('consultRequestEnabled')) === 'Y' && sql.value(flag('consultResponseEnabled')) === 'NULL',
      'Disabling responses did not clear only the response flag');
    h.assert(sql.value(referringActive) === '02', 'Disabling responses did not inactivate the Referring Doctor service');
    await menu('ViewShowAllServices');
    h.assert(await config.getByRole('link', { name: REFERRING, exact: true }).count() === 0,
      'The inactive Referring Doctor service is still listed');
  });
}

if (require.main === module) runWorkflow('consultation-services-admin', workflow, { openPatient: true });
module.exports = { workflow };
