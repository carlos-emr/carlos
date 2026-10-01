#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */

/*
 * Report day sheets and the Ontario PHCP report, driven through their real openers
 * (coverage plan: report day sheets / PHCP).
 *
 * User paths:
 *   Administration ▸ Reports ▸ PHCP "(Setting: Provider)" ▸ Update one provider's role, then
 *   Administration ▸ Reports ▸ PHCP ▸ DxCode / ServiceCode ▸ Go       (report/ViewReportonbilled*)
 *   Schedule ▸ Report ▸ Day Sheet ▸ provider / group / All Providers,
 *   time window, "Show Only Self Booked", "Non Rostered Only"            (report/ViewReportdaysheet)
 * Asserted: each day sheet lists exactly the owned appointments its filters select, with the
 * patient's phone/sex/HIN/version/chart/enrolment, booking source, family-doctor tag and reason
 * (rendered as text); viewing never changes appointment status; the role update lands in
 * secUserRole for that provider only and moves it into the PHCP Nurse list; PHCP Dx and
 * service-code rows count only non-deleted bills per patient/visit, sex and age band. The last
 * three steps assert correct behaviour the application currently lacks (GET role mutation,
 * ignored Non Rostered Only filter, sort links dropping the time window).
 * Fixtures: the runWorkflow FAKE- patient, two FAKE- providers (one with an 'other' secUserRole
 * row), one mygroup row, five appointments today and three legacy billing/billingdetail rows, all
 * marker/id scoped and removed (and re-checked) in cleanup. No demo row is changed.
 */
const { randomInt } = require('node:crypto');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const DX_CODE = '009';
const SERVICE_CODE = 'A007A';

/** Port of io.github.carlos_emr.Misc.toUpperLowerCase, which the day sheet applies to names. */
function upperLower(value) {
  let out = '';
  let upper = true;
  for (const c of value.trim().toLowerCase()) {
    out += upper ? c.toUpperCase() : c;
    upper = c === ' ' || c === ',';
  }
  return out;
}

const norm = text => text.replace(/\s+/g, ' ').trim();
const owned = id => /^[1-9]\d*$/.test(id);

function unusedProviderNo(sql) {
  for (let attempt = 0; attempt < 20; attempt++) {
    const candidate = String(randomInt(800000, 899999));
    if (sql.value(`SELECT (SELECT COUNT(*) FROM provider WHERE provider_no=${h.sqlString(candidate)})
      + (SELECT COUNT(*) FROM secUserRole WHERE provider_no=${h.sqlString(candidate)})`) === '0') return candidate;
  }
  throw new Error('No unused provider number was found');
}

async function openAdminFrame(admin, rel, ready) {
  const link = admin.locator(`a.xlink[rel$="${rel}"]`).first();
  await revealAuditLink(admin, link, 20000);
  await link.click();
  const iframe = admin.locator('#dynamic-content iframe#myFrame');
  await iframe.waitFor();
  const frame = await (await iframe.elementHandle()).contentFrame();
  h.assert(frame, `${rel} did not load into the administration frame`);
  await frame.locator(ready).first().waitFor();
  return frame;
}

async function submitInFrame(admin, frame, button) {
  const navigated = admin.waitForEvent('framenavigated', { predicate: f => f === frame, timeout: 20000 });
  navigated.catch(() => {});
  await button.click();
  await navigated;
  await frame.waitForLoadState('domcontentloaded');
  await h.assertNotErrorPage(frame, 'administration frame');
}

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const q = h.sqlString;
  const today = sql.value('SELECT CURDATE()');
  const state = { providers: [], group: null, appts: {}, bills: [] };

  // Cleanups run in reverse registration order, so providers go last.
  s.cleanup(() => {
    if (!state.providers.length) return;
    const ids = state.providers.map(q).join(',');
    sql.execute(`DELETE FROM secUserRole WHERE provider_no IN (${ids});
      DELETE FROM providersite WHERE provider_no IN (${ids});
      DELETE FROM provider WHERE provider_no IN (${ids}) AND last_name=${q(marker)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM secUserRole WHERE provider_no IN (${ids}))
      + (SELECT COUNT(*) FROM providersite WHERE provider_no IN (${ids}))
      + (SELECT COUNT(*) FROM provider WHERE provider_no IN (${ids}))`) === '0', 'Owned providers, sites or roles were not removed');
  });
  s.cleanup(() => {
    if (!state.group) return;
    sql.execute(`DELETE FROM mygroup WHERE mygroup_no=${q(state.group)} AND last_name=${q(marker)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM mygroup WHERE mygroup_no=${q(state.group)}`) === '0', 'Owned group was not removed');
  });
  s.cleanup(() => {
    const ids = Object.values(state.appts);
    if (!ids.length) return;
    sql.execute(`DELETE FROM appointment WHERE appointment_no IN (${ids.join(',')}) AND demographic_no=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE appointment_no IN (${ids.join(',')})`) === '0',
      'Owned appointments were not removed');
  });
  s.cleanup(() => {
    if (!state.bills.length) return;
    const ids = state.bills.join(',');
    sql.execute(`DELETE FROM billingdetail WHERE billing_no IN (${ids});
      DELETE FROM billing WHERE billing_no IN (${ids}) AND demographic_no=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM billing WHERE billing_no IN (${ids}))
      + (SELECT COUNT(*) FROM billingdetail WHERE billing_no IN (${ids}))`) === '0', 'Owned bills were not removed');
  });

  // Providers: A has no role (New Provider-Role List), B starts as 'other' (Confirmed list).
  const roleless = unusedProviderNo(sql);
  state.providers.push(roleless);
  sql.execute(`INSERT INTO provider (provider_no,last_name,first_name,provider_type,specialty,sex,status,lastUpdateUser,lastUpdateDate)
    VALUES (${q(roleless)},${q(marker)},'Unroled','nurse','','F','1',${q(provider)},NOW())`);
  const nurse = unusedProviderNo(sql);
  state.providers.push(nurse);
  sql.execute(`INSERT INTO provider (provider_no,last_name,first_name,provider_type,specialty,sex,status,lastUpdateUser,lastUpdateDate)
    VALUES (${q(nurse)},${q(marker)},'Nurse','doctor','','F','1',${q(provider)},NOW());
    INSERT INTO secUserRole (provider_no,role_name,orgcd,activeyn,lastUpdateDate) VALUES (${q(nurse)},'other','R0000001',1,NOW())`);
  h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no IN (${q(roleless)},${q(nurse)}) AND last_name=${q(marker)}`) === '2',
    'Provider fixtures were not created');
  // Site access privacy hides providers outside the viewer's sites. The nurse shares the test
  // provider's first site; the role-less provider has none.
  const site = sql.value(`SELECT MIN(site_id) FROM providersite WHERE provider_no=${q(provider)}`);
  const sitePrivacy = sql.value(`SELECT COUNT(*) FROM secObjPrivilege o JOIN secUserRole r ON r.role_name=o.roleUserGroup
    WHERE r.provider_no=${q(provider)} AND o.objectName='_site_access_privacy' AND o.privilege<>'o'`) !== '0';
  if (site) sql.execute(`INSERT INTO providersite (provider_no,site_id) VALUES (${q(nurse)},${site})`);

  state.group = `PW${marker.slice(-8)}`;
  sql.execute(`INSERT INTO mygroup (mygroup_no,provider_no,last_name,first_name,vieworder)
    VALUES (${q(state.group)},${q(provider)},${q(marker)},'Group','1')`);

  const patientFields = { phone: '905-555-0142', hin: `9${String(randomInt(0, 1e9)).padStart(9, '0')}`, ver: 'PW',
    chart: `PW${marker.slice(-6)}` };
  sql.execute(`UPDATE demographic SET phone=${q(patientFields.phone)},hin=${q(patientFields.hin)},ver=${q(patientFields.ver)},
    chart_no=${q(patientFields.chart)},roster_status='RO',provider_no=${q(nurse)}
    WHERE demographic_no=${patient} AND last_name=${q(marker)}`);

  const reasons = {
    visit: `${marker} visit <b>x</b> & "q"`,
    self: `${marker} self booked`,
    cancelled: `${marker} cancelled`,
    late: `${marker} late`,
    other: `${marker} other provider`,
    offsite: `${marker} other site`,
  };
  const appts = [
    ['visit', provider, '09:00:00', '09:14:00', 't', null],
    ['self', provider, '09:15:00', '09:29:00', 't', 'ONLINE'],
    ['cancelled', provider, '09:30:00', '09:44:00', 'C', null],
    ['late', provider, '20:30:00', '20:44:00', 't', null],
    ['other', nurse, '10:00:00', '10:14:00', 't', null],
    ['offsite', roleless, '10:15:00', '10:29:00', 't', null],
  ];
  for (const [key, prov, start, end, status, source] of appts) {
    const id = sql.value(`INSERT INTO appointment (provider_no,appointment_date,start_time,end_time,name,demographic_no,program_id,
        reason,status,bookingSource,createdatetime,updatedatetime,creator,lastupdateuser)
      VALUES (${q(prov)},${q(today)},${q(start)},${q(end)},${q(`${marker},Workflow`)},${patient},0,${q(reasons[key])},
        ${q(status)},${source ? q(source) : 'NULL'},NOW(),NOW(),'playwright',${q(provider)}); SELECT LAST_INSERT_ID()`);
    h.assert(owned(id), `Appointment fixture ${key} was not created`);
    state.appts[key] = id;
  }
  const apptIds = Object.values(state.appts).join(',');
  const apptSnapshot = () => sql.rows(`SELECT appointment_no,status FROM appointment WHERE appointment_no IN (${apptIds})
    ORDER BY appointment_no`).map(r => r.join(':')).join(',');
  const apptsBefore = apptSnapshot();

  // Two live bills and one deleted bill, all created by the nurse provider for the patient (F, born 1980).
  const serviceDesc = `${marker} visit`;
  for (const status of ['O', 'O', 'D']) {
    const id = sql.value(`INSERT INTO billing (clinic_no,demographic_no,provider_no,demographic_name,billing_date,billing_time,
        status,creator,total,update_date,update_time,visitdate,billingtype)
      VALUES (0,${patient},${q(nurse)},${q(marker)},${q(today)},'10:00:00',${q(status)},${q(nurse)},'33.70',CURDATE(),CURTIME(),
        ${q(today)},'ON'); SELECT LAST_INSERT_ID()`);
    h.assert(owned(id), 'Billing fixture was not created');
    state.bills.push(id);
    sql.execute(`INSERT INTO billingdetail (billing_no,service_code,service_desc,billing_amount,diagnostic_code,appointment_date,
        status,billingunit)
      VALUES (${id},${q(SERVICE_CODE)},${q(serviceDesc)},'33.70',${q(DX_CODE)},${q(today)},${q(status)},'1')`);
  }

  // ── Administration ▸ Reports ▸ PHCP settings and report ─────────────────────────────────────
  const { page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'administration', timeout: 20000 });
  const roleRows = () => sql.rows(`SELECT id,provider_no,role_name FROM secUserRole WHERE provider_no NOT IN
    (${state.providers.map(q).join(',')}) ORDER BY id`).map(r => r.join(':')).join(',');
  const otherRolesBefore = roleRows();

  let settings;
  await s.step('PHCP provider settings list the role-less provider with its type default and the confirmed role', async () => {
    settings = await openAdminFrame(admin, '/report/ViewReportonbilledvisitprovider', 'form[name="myform"]');
    const fresh = settings.locator('tr').filter({ has: settings.locator(`input[name="type${roleless}"]`) });
    h.assert(await fresh.count() === 1, 'The role-less owned provider is not in the New Provider-Role List');
    h.assert(norm(await fresh.locator('td').nth(2).innerText()) === marker, 'New Provider-Role List shows the wrong last name');
    h.assert(await fresh.locator(`input[name="type${roleless}"][value="nurse"]`).isChecked(),
      'A nurse-type provider is not defaulted to the Nurse role');
    const confirmed = settings.locator(`select[name="name${nurse}"]`);
    h.assert(await confirmed.count() === 1, 'The owned confirmed provider is missing from the Confirmed Provider-Role List');
    h.assert(await confirmed.inputValue() === 'other', 'The Confirmed list does not show the stored role');
  });

  await s.step('Update changes only the chosen provider role in secUserRole', async () => {
    await settings.locator(`select[name="name${nurse}"]`).selectOption('nurse');
    const row = settings.locator('tr').filter({ has: settings.locator(`select[name="name${nurse}"]`) });
    await submitInFrame(admin, settings, row.locator('input[name="buttonUpdate"]'));
    await expectValue(sql, `SELECT GROUP_CONCAT(role_name) FROM secUserRole WHERE provider_no=${q(nurse)}`, 'nurse',
      'The role update did not reach secUserRole');
    h.assert(await settings.locator(`select[name="name${nurse}"]`).inputValue() === 'nurse', 'The saved role was not redisplayed');
    h.assert(sql.value(`SELECT COUNT(*) FROM secUserRole WHERE provider_no=${q(roleless)}`) === '0',
      'Updating one provider also created a role for the role-less provider');
    h.assert(roleRows() === otherRolesBefore, 'Updating one provider changed another provider\'s roles');
  });

  let phcp;
  await s.step('PHCP Go without a code and dates raises the selection alert and submits nothing', async () => {
    phcp = await openAdminFrame(admin, '/report/ViewReportonbilledphcp', 'form[name="myform"]');
    const url = phcp.url();
    const dialogs = await h.withExpectedDialogs(admin, async () => {
      await phcp.locator('input[type="submit"][name="submit"]').click();
      await phcp.waitForTimeout(500);
    });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'alert', 'PHCP Go with nothing selected raised no alert');
    h.assert(phcp.url() === url && await phcp.getByText('PATIENT VISIT LIST').count() === 0, 'PHCP submitted an incomplete form');
  });

  async function runPhcp(codeType) {
    await phcp.locator('select[name="codeType"]').selectOption(codeType);
    await ui.pickDate(phcp, '#startDate', today);
    await ui.pickDate(phcp, '#endDate', today);
    h.assert(await phcp.locator(`select[name="providerNoNP"] option[value="${nurse}"]`).count() === 1,
      'The provider whose role became Nurse is not offered in the PHCP Nurse list');
    await phcp.locator('select[name="providerNoNP"]').selectOption(nurse);
    await submitInFrame(admin, phcp, phcp.locator('input[type="submit"][name="submit"]'));
    h.assert(norm(await phcp.locator('th').filter({ hasText: 'PATIENT VISIT LIST' }).first().innerText())
      === `Nurse ${marker} - PATIENT VISIT LIST`, 'PHCP result is not headed by the selected provider');
  }
  // pt/visit: total, F, M, then age bands 0-1 .. 71+. The patient is female, 46 (35-50 band, index 7 pair).
  const expectedCounts = ['1', '2', '1', '2', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '1', '2', '0', '0', '0', '0', '0', '0'];
  // A code row is [code, description, 22 counts]; the sub-total row is [label, 22 counts].
  async function countRow(predicate, label, width = 24) {
    const rows = phcp.locator('tr');
    const n = await rows.count();
    for (let i = 0; i < n; i++) {
      const cells = (await rows.nth(i).locator('td').allInnerTexts()).map(norm);
      if (cells.length === width && predicate(cells)) return cells;
    }
    throw new Error(`${label}: the PHCP report has no row for the owned bills`);
  }
  await s.step('PHCP DxCode report counts the owned live bills per patient, visit, sex and age band', async () => {
    await runPhcp('DxCode');
    const cells = await countRow(c => c[0] === DX_CODE, 'DxCode');
    h.assert(cells[1] === norm(sql.value(`SELECT description FROM diagnosticcode WHERE diagnostic_code=${q(DX_CODE)}`)),
      'PHCP DxCode row shows the wrong description');
    h.assert(JSON.stringify(cells.slice(2)) === JSON.stringify(expectedCounts),
      `PHCP DxCode counts were [${cells.slice(2).join(',')}], expected [${expectedCounts.join(',')}]`);
    const category = sql.rows(`SELECT level1,level2 FROM dxphcpgroup WHERE dxcode=${q(DX_CODE)}`)[0];
    h.assert(await phcp.locator('td', { hasText: `${category[0].toUpperCase()} - ${category[1]}` }).count() > 0,
      'PHCP DxCode report did not group the code under its PHCP category');
    // Other codes billed today belong to other creators, so this provider's sub-total is exactly the owned row.
    const subtotal = await countRow(c => c[0] === 'Sub. Total:', 'Sub Total', 23);
    h.assert(JSON.stringify(subtotal.slice(1)) === JSON.stringify(expectedCounts), 'PHCP DxCode sub-total disagrees');
  });
  await s.step('PHCP ServiceCode report counts the owned service code and description', async () => {
    await runPhcp('ServiceCode');
    const cells = await countRow(c => c[0] === SERVICE_CODE && c[1] === serviceDesc, 'ServiceCode');
    h.assert(JSON.stringify(cells.slice(2)) === JSON.stringify(expectedCounts),
      `PHCP ServiceCode counts were [${cells.slice(2).join(',')}], expected [${expectedCounts.join(',')}]`);
  });

  // ── Schedule ▸ Report ▸ Day Sheet ───────────────────────────────────────────────────────────
  if (!admin.isClosed() && admin !== s.schedule) await admin.close();
  const { page: index } = await ui.clickOpensPopupOrNavigates(s.schedule,
    s.schedule.locator("a[onclick*='/report/ViewReportindex']").first(),
    { context: s.context, recorder: s.recorder, label: 'report index', timeout: 20000 });
  await index.locator('#asdate').waitFor();

  const nameText = upperLower(`${marker},Workflow`);
  async function daySheet(label, { providerNo, eTime = '20', nonRostered = false }) {
    await index.locator('select[name="provider_no"]').selectOption(providerNo);
    await ui.pickDate(index, '#asdate', today);
    await ui.pickDate(index, '#aedate', today);
    await index.locator('select[name="sTime"]').selectOption('8');
    await index.locator('select[name="eTime"]').selectOption(eTime);
    await index.locator('#rosteredOnly').setChecked(nonRostered);
    const sheet = await s.popup(index, index.getByRole('link', { name: 'All appointments' }), label);
    h.assert(new URL(sheet.url()).pathname.endsWith('/report/ViewReportdaysheet'), `${label} opened another page`);
    return sheet;
  }
  async function listed(sheet) {
    const found = [];
    for (const [key, reason] of Object.entries(reasons)) {
      const n = await sheet.locator('tbody tr').filter({ hasText: reason }).count();
      for (let i = 0; i < n; i++) found.push(key);
    }
    return found.sort();
  }
  const same = (a, b) => JSON.stringify(a) === JSON.stringify([...b].sort());

  let providerSheet;
  await s.step('provider day sheet lists the owned in-window appointments with patient fields, not the cancelled or late ones', async () => {
    const sheet = providerSheet = await daySheet('day sheet provider', { providerNo: provider });
    h.assert(same(await listed(sheet), ['visit', 'self']), `Provider day sheet listed [${await listed(sheet)}]`);
    const headers = (await sheet.locator('thead').first().locator('th').allInnerTexts()).map(norm);
    const row = sheet.locator('tbody tr').filter({ hasText: reasons.visit });
    const cells = (await row.locator('td').allInnerTexts()).map(norm);
    const col = name => cells[headers.indexOf(name)];
    h.assert(col('Appt Time') === '09:00', 'Day sheet shows the wrong appointment time');
    h.assert(await row.locator('td').first().getAttribute('title') === 'End Time: 09:14:00', 'Day sheet lost the end time');
    h.assert(col("Patient's Last Name") === nameText, 'Day sheet shows the wrong patient name');
    h.assert(col('Phone') === patientFields.phone && col('Gender') === 'F' && col('Health Card') === patientFields.hin
      && col('Version') === patientFields.ver && col('Chart No.') === patientFields.chart, 'Day sheet patient fields are wrong');
    h.assert(col('Enrolment Status') === 'RO' || headers.includes('DOB'), 'Day sheet shows the wrong enrolment status');
    h.assert(col('Booking Status') === '', 'A clinic-booked appointment is marked self booked');
    h.assert(col('Comments') === `[${marker}, N] ${reasons.visit}`, 'Day sheet comments lost the family doctor or reason');
    h.assert(await row.locator('b').count() === 0, 'The appointment reason was rendered as HTML');
    const selfCells = (await sheet.locator('tbody tr').filter({ hasText: reasons.self }).locator('td').allInnerTexts()).map(norm);
    h.assert(selfCells[headers.indexOf('Booking Status')] === 'Self', 'A self-booked appointment is not marked Self');
    h.assert(apptSnapshot() === apptsBefore, 'Viewing the day sheet changed appointment status');
  });
  await s.step('Show Only Self Booked hides the clinic-booked row and keeps the self-booked one', async () => {
    await providerSheet.locator('#onlySelfBooked').check();
    h.assert(!await providerSheet.locator('tbody tr').filter({ hasText: reasons.visit }).isVisible(),
      'Show Only Self Booked left the clinic-booked appointment visible');
    h.assert(await providerSheet.locator('tbody tr').filter({ hasText: reasons.self }).isVisible(), 'Show Only Self Booked hid a self booking');
    await providerSheet.close();
  });
  await s.step('extending the end time to 11 pm adds the late appointment', async () => {
    const sheet = await daySheet('day sheet late', { providerNo: provider, eTime: '23' });
    h.assert(same(await listed(sheet), ['visit', 'self', 'late']), `Late-window day sheet listed [${await listed(sheet)}]`);
    await sheet.close();
  });
  await s.step('group day sheet lists members only', async () => {
    const sheet = await daySheet('day sheet group', { providerNo: `_grp_${state.group}` });
    h.assert(same(await listed(sheet), ['visit', 'self']), `Group day sheet listed [${await listed(sheet)}]`);
    await sheet.close();
  });
  await s.step('All Providers day sheet adds the same-site provider, honours site privacy, and tags only foreign family doctors', async () => {
    h.assert(site || !sitePrivacy, 'The test provider has site access privacy but no site');
    const sheet = await daySheet('day sheet all', { providerNo: '*' });
    const expected = sitePrivacy ? ['visit', 'self', 'other'] : ['visit', 'self', 'other', 'offsite'];
    h.assert(same(await listed(sheet), expected), `All-provider day sheet listed [${await listed(sheet)}], expected [${expected}]`);
    const cells = (await sheet.locator('tbody tr').filter({ hasText: reasons.other }).locator('td').allInnerTexts()).map(norm);
    h.assert(cells[cells.length - 1] === reasons.other, 'The family-doctor tag was shown for the patient\'s own doctor');
    h.assert(apptSnapshot() === apptsBefore, 'Viewing the all-provider day sheet changed appointment status');
    await sheet.close();
  });

  // ── Correct behaviour the application does not have yet (asserted last) ────────────────────
  await s.step('sorting a time-windowed day sheet keeps its time window', async () => {
    const sheet = await daySheet('day sheet sort', { providerNo: provider });
    await Promise.all([sheet.waitForNavigation({ waitUntil: 'domcontentloaded' }),
      sheet.getByRole('link', { name: "Patient's Last Name" }).first().click()]);
    const found = await listed(sheet);
    await sheet.close();
    h.assert(same(found, ['visit', 'self']), `Sorting dropped the 8 am-8 pm window and listed [${found}]`);
  });
  await s.step('Non Rostered Only leaves the rostered patient off the day sheet', async () => {
    const sheet = await daySheet('day sheet non-rostered', { providerNo: provider, nonRostered: true });
    const found = await listed(sheet);
    await sheet.close();
    h.assert(found.length === 0, `Non Rostered Only still listed the rostered patient's appointments [${found}]`);
  });
}

if (require.main === module) runWorkflow('report-daysheet-labs', workflow, { openPatient: true });
module.exports = { workflow };
