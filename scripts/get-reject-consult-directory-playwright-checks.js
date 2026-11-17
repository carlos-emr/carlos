#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * GET-rejection sweep, consultation directory. User path: Schedule ▸ Consultations ▸
 * directory (ViewShowAllServices) ▸ Edit Institutions / Edit Departments / Edit
 * Specialists ▸ tick a row ▸ Delete (confirm).
 *
 * encounter/EditInstitutions, encounter/EditDepartments and encounter/EditSpecialists
 * delete (hard, hard, soft) on `delete=<button label>&<checkbox>=<id>` without any POST
 * check, and their names start with "Edit", which HttpMethodGuardFilter deliberately does
 * not treat as a mutator; none is in MutatorActionGetRejectionContractUnitTest. For each,
 * the check deletes owned row A through the page (capturing the exact POST), then replays
 * that request as HEAD and GET against owned row B and asserts B survives and the route
 * answered 405. Every probe is asserted in the LAST step.
 *
 * Fixtures: marker-named Institution / Department rows and professionalSpecialists rows
 * (fName FAKE, lName = marker); cleanup deletes only those and asserts they are gone.
 * Risk sweep "get-reject" (STATE-CHANGING ACTIONS THAT ACCEPT GET).
 */
const h = require('./lib/playwright-harness');
const { clickAndAwaitReload, clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { captureRequest, replayParams, createLedger } = require('./lib/get-reject-probe');

const NAME = 'get-reject-consult-directory';

async function workflow(s) {
  const { sql, marker } = s;
  const q = h.sqlString;
  const ledger = createLedger(NAME);
  const { page: list } = await clickOpensPopupOrNavigates(s.schedule,
    s.schedule.getByRole('link', { name: 'Consultations', exact: true }),
    { context: s.context, recorder: s.recorder, label: 'consultations' });
  const page = await s.popup(list, list.locator('a[href*="ViewShowAllServices"]'), 'consultation-directory');
  const navigate = suffix => clickAndAwaitReload(page, page.locator(`a[href$="/${suffix}"]`).first());

  const defs = [
    { table: 'Institution', key: 'id', edit: 'ViewEditInstitutions', route: '/encounter/EditInstitutions', checkbox: 'institutions',
      seed: n => `INSERT INTO Institution(name) VALUES(${q(n)})`, alive: id => `SELECT COUNT(*) FROM Institution WHERE id=${id}`,
      owned: names => `SELECT id FROM Institution WHERE name IN (${names})`, del: names => `DELETE FROM Institution WHERE name IN (${names})` },
    { table: 'Department', key: 'id', edit: 'ViewEditDepartments', route: '/encounter/EditDepartments', checkbox: 'specialists',
      seed: n => `INSERT INTO Department(name) VALUES(${q(n)})`, alive: id => `SELECT COUNT(*) FROM Department WHERE id=${id}`,
      owned: names => `SELECT id FROM Department WHERE name IN (${names})`, del: names => `DELETE FROM Department WHERE name IN (${names})` },
    { table: 'professionalSpecialists', key: 'specId', edit: 'ViewEditSpecialists', route: '/encounter/EditSpecialists', checkbox: 'specialists',
      seed: n => `INSERT INTO professionalSpecialists(fName,lName,lastUpdated,institutionId,departmentId,hideFromView,deleted)
        VALUES('FAKE',${q(n)},NOW(),0,0,0,0)`,
      alive: id => `SELECT COUNT(*) FROM professionalSpecialists WHERE specId=${id} AND deleted=0`,
      owned: names => `SELECT specId FROM professionalSpecialists WHERE fName='FAKE' AND lName IN (${names})`,
      del: names => `DELETE FROM professionalSpecialists WHERE fName='FAKE' AND lName IN (${names})` },
  ];

  for (const def of defs) {
    // Department.name / professionalSpecialists.lName are short; keep the marker tail.
    const base = `${def.table.slice(0, 4)}${marker.slice(-12)}`;
    const names = [`${base}A`, `${base}B`];
    const nameList = names.map(q).join(',');
    s.cleanup(() => {
      sql.execute(def.del(nameList));
      h.assert(sql.value(`SELECT COUNT(*) FROM (${def.owned(nameList)}) t`) === '0', `Owned ${def.table} fixtures were not removed`);
    });
    const [idA, idB] = names.map(n => sql.value(`${def.seed(n)}; SELECT LAST_INSERT_ID()`));
    h.assert(/^[1-9]\d*$/.test(idA) && /^[1-9]\d*$/.test(idB), `${def.table} fixtures were not created`);
    let captured;

    await s.step(`${def.table}: the directory's Delete removes the ticked owned row (POST ${def.route})`, async () => {
      await navigate(def.edit);
      const box = page.locator(`input[name="${def.checkbox}"][value="${idA}"]`);
      await box.waitFor({ state: 'attached' });
      await box.check();
      const dialogs = await h.withExpectedDialogs(page, async () => {
        captured = await captureRequest(page, url => url.pathname.endsWith(def.route),
          () => clickAndAwaitReload(page, page.locator('input[name="delete"]').first()));
      });
      h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', `${def.table} delete did not confirm once`);
      h.assert(captured.params.get('delete') && captured.params.getAll(def.checkbox).includes(idA),
        `${def.table} delete did not post the button label and the ticked id`);
      await expectValue(sql, def.alive(idA), '0', `${def.table} delete through the page did not remove the row`);
      h.assert(sql.value(def.alive(idB)) === '1', `${def.table} delete removed an unticked row`);
    });

    await s.step(`${def.table}: the same delete replayed as GET/HEAD against owned row B is recorded`, async () => {
      await ledger.probe(s, {
        label: `${def.route.slice(1)} delete`,
        path: captured.path,
        params: replayParams(captured.params, { [def.checkbox]: idB }),
        snapshot: () => sql.value(def.alive(idB)),
      });
    });
  }

  await s.step('every consultation-directory delete refused GET/HEAD and left the owned rows in place', async () => {
    ledger.assertAllRefused();
  });
}

if (require.main === module) runWorkflow(NAME, workflow, { openPatient: false });
module.exports = { workflow };
