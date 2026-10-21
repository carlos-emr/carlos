/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const h = require('./playwright-harness');
const ui = require('./playwright-ui');

// Lists belong to the login's existing schedule group. No shared preference is changed.
// The second patient and all list names carry this run's marker; cleanup checks
// ownership and refuses to delete a list acquired by another patient.
async function prepareWaitingLists(s, { members = false, providerChoices = false } = {}) {
  const group = s.sql.value(`SELECT COALESCE(myGroupNo,'') FROM ProviderPreference
    WHERE providerNo=${h.sqlString(s.provider)}`);
  if (!group) throw new h.SkipCheck('The login needs an existing schedule group for waiting-list management');
  if (providerChoices) {
    // Some demo groups contain no explicit members: the manager falls back to
    // one provider, but Flip View's picker reads only mygroup. Add owned group
    // memberships for two existing active providers, without changing their
    // records or the login preference. This variant must run exclusively.
    const providers = s.sql.rows(`SELECT provider_no FROM provider WHERE status='1'
      AND provider_no NOT LIKE '-%' AND provider_no NOT IN
        (SELECT provider_no FROM mygroup WHERE mygroup_no=${h.sqlString(group)})
      ORDER BY provider_no LIMIT 2`).map(row => row[0]);
    if (providers.length < 2) throw new h.SkipCheck('Two active providers without membership in the login group are needed');
    const inserted = [];
    s.cleanup(() => {
      for (const provider of inserted) {
        const key = `mygroup_no=${h.sqlString(group)} AND provider_no=${h.sqlString(provider)}`;
        h.assert(s.sql.value(`SELECT COUNT(*) FROM mygroup WHERE ${key}
          AND last_name=${h.sqlString(s.marker)} AND first_name='Booking'`) === '1', 'Owned group membership changed');
        s.sql.execute(`DELETE FROM mygroup WHERE ${key} AND last_name=${h.sqlString(s.marker)} AND first_name='Booking'`);
        h.assert(s.sql.value(`SELECT COUNT(*) FROM mygroup WHERE ${key}`) === '0', 'Owned group membership remains');
      }
    });
    for (const provider of providers) {
      s.sql.execute(`INSERT INTO mygroup(mygroup_no,provider_no,last_name,first_name)
        VALUES(${h.sqlString(group)},${h.sqlString(provider)},${h.sqlString(s.marker)},'Booking')`);
      inserted.push(provider);
    }
  }
  const patients = [s.patient].filter(Boolean);
  let control;
  if (members) {
    s.cleanup(() => {
      if (!control) return;
      h.assert(s.sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${control}
        AND last_name=${h.sqlString(`${s.marker} C`)}`) === '1', 'Control patient ownership changed');
      s.sql.execute(`DELETE FROM demographic WHERE demographic_no=${control}`);
      h.assert(s.sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${control}`) === '0',
        'Control patient remains');
    });
    control = s.sql.value(`INSERT INTO demographic(last_name,first_name,year_of_birth,month_of_birth,
      date_of_birth,sex,patient_status,provider_no,hc_type,province,roster_status,lastUpdateDate)
      VALUES(${h.sqlString(`${s.marker} C`)},'Workflow','1981','02','03','F','AC',
        ${h.sqlString(s.provider)},'ON','ON','NR',NOW()); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(control), 'Control patient was not created');
    patients.push(control);
  }
  const owned = `name LIKE ${h.sqlString(`${s.marker}%`)} AND group_no=${h.sqlString(group)}
    AND provider_no=${h.sqlString(s.provider)}`;
  s.cleanup(() => {
    if (control) h.assert(s.sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${control}
      AND last_name=${h.sqlString(`${s.marker} C`)}`) === '1', 'Control patient ownership changed before list cleanup');
    const ids = s.sql.rows(`SELECT ID FROM waitingListName WHERE ${owned}`).map(row => row[0]);
    for (const id of ids) {
      h.assert(/^[1-9]\d*$/.test(id), 'Owned list ID is invalid');
      h.assert(s.sql.value(`SELECT COUNT(*) FROM waitingList WHERE listID=${id}${patients.length
        ? ` AND demographic_no NOT IN (${patients.join(',')})` : ''}`) === '0', 'Another patient acquired an owned list');
      s.sql.execute(`DELETE FROM waitingList WHERE listID=${id}`);
    }
    s.sql.execute(`DELETE FROM waitingListName WHERE ${owned}`);
    h.assert(s.sql.value(`SELECT COUNT(*) FROM waitingListName WHERE ${owned}`) === '0', 'Owned lists remain');
  });
  function seedList(label) {
    const name = `${s.marker} ${label}`;
    const id = s.sql.value(`INSERT INTO waitingListName(name,group_no,provider_no,create_date,is_history)
      VALUES(${h.sqlString(name)},${h.sqlString(group)},${h.sqlString(s.provider)},NOW(),'N'); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), 'Owned list was not created');
    return { id, name };
  }
  const list = seedList('list');
  if (members) {
    for (const [index, patient] of patients.entries()) {
      s.sql.execute(`INSERT INTO waitingList(listID,demographic_no,note,onListSince,position,is_history)
        VALUES(${list.id},${patient},${h.sqlString(`${s.marker} ${index ? 'control' : 'original'} note`)},
          ${h.sqlString(index ? '2026-02-02' : '2026-03-03')},${index ? 1 : 2},'N')`);
    }
  }
  const reportResult = await ui.clickOpensPopupOrNavigates(s.schedule,
    s.schedule.locator("a[onclick*='/report/ViewReportindex'], a[href*='/report/ViewReportindex']").first(),
    { context: s.context, recorder: s.recorder, label: 'waiting-list-report', timeout: 20000 });
  const report = reportResult.page;
  const entry = report.locator('#waitingListReport');
  h.assert(await entry.count() === 1, 'Report has no Waiting List entry (DEMOGRAPHIC_WAITING_LIST=true required)');
  const manager = await s.popup(report, entry, 'waiting-list-manager');
  await manager.locator('#selectedWL').waitFor();
  await manager.locator('#selectedWL').selectOption(list.id);
  await ui.clickAndAwaitReload(manager, manager.locator('input[onclick*="goToPage"]'),
    { timeout: 20000, label: 'Generate Report' });
  await h.assertNotErrorPage(manager, 'waiting-list manager');
  const row = patient => manager.locator('tr').filter({has: manager.locator(`a[onclick*="demographic_no=${patient}'"]`)});
  // The master-record link contains the exact patient ID, avoiding a match on
  // another patient's notes, names, or a numeric ID prefix.
  function patientRow(patient) {
    return row(patient).filter({has: manager.locator('textarea[name$="].note"]')}).last();
  }
  async function editor() {
    const page = await s.popup(manager, manager.getByRole('link', { name: 'Create List', exact: true }), 'waiting-list-name-editor');
    await page.locator('#selectedWL').waitFor();
    return page;
  }
  return { list, control, patients, manager, editor, seedList, patientRow };
}
module.exports = { prepareWaitingLists };
