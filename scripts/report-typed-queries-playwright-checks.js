#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Qualifies typed report configuration, legacy billed-provider projections and CSV dispatch on Ontario.
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
async function workflow(s) {
  const {sql, marker, provider, patient} = s;
  const q = h.sqlString;
  const page = await s.context.newPage();
  const base = String(s.config.baseUrl).replace(/\/$/, '');
  let reportId, billingId;
  const roleProvider = `R${marker.slice(-5)}`;
  let ownsRoleProvider = false;
  let extensionPatient, ownsExtensionPatient = false;
  s.cleanup(() => {
    if (ownsRoleProvider) {
      sql.execute(`DELETE FROM secUserRole WHERE provider_no=${q(roleProvider)}; DELETE FROM provider WHERE provider_no=${q(roleProvider)} AND last_name=${q(marker)}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${q(roleProvider)} AND last_name=${q(marker)}`) === '0', 'Owned role provider remains');
    }
    // Recover generated IDs by the owned markers even if navigation failed after a committed write.
    const reports = sql.rows(`SELECT id FROM reportItem WHERE report_name=${q(marker)}`).map(row => row[0]);
    for (const id of reports) {
      h.assert(/^[1-9]\d*$/.test(id), 'Owned report identifier is invalid');
      sql.execute(`DELETE FROM reportConfig WHERE report_id=${id}; DELETE FROM reportFilter WHERE report_id=${id}; DELETE FROM reportItem WHERE id=${id} AND report_name=${q(marker)}`);
    }
    const bills = sql.rows(`SELECT billing_no FROM billing WHERE demographic_no=${patient} AND demographic_name=${q(marker)}`).map(row => row[0]);
    for (const id of bills) {
      h.assert(/^[1-9]\d*$/.test(id), 'Owned billing identifier is invalid');
      sql.execute(`DELETE FROM billingdetail WHERE billing_no=${id}; DELETE FROM billing WHERE billing_no=${id} AND demographic_no=${patient} AND demographic_name=${q(marker)}`);
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM reportItem WHERE report_name=${q(marker)}`) === '0', 'Owned report remains');
    h.assert(sql.value(`SELECT COUNT(*) FROM billing WHERE demographic_no=${patient} AND demographic_name=${q(marker)}`) === '0', 'Owned billing remains');
  });
  s.cleanup(() => {
    if (!ownsExtensionPatient) return;
    const owned = sql.rows('SELECT demographic_no FROM demographic WHERE last_name=? AND first_name=? AND provider_no=?',
      [marker, 'Extension', provider]).map(row => row[0]);
    h.assert(owned.length <= 1 && (!extensionPatient || owned.length === 0 || owned[0] === extensionPatient),
      'The extension report patient ownership changed');
    for (const id of [patient, ...owned]) {
      h.assert(/^[1-9]\d*$/.test(id), 'Invalid extension report patient identifier');
      sql.execute('DELETE FROM demographicExt WHERE demographic_no=? AND provider_no=? AND key_val=?',
        [id, provider, 'prefer_language']);
      h.assert(sql.value('SELECT COUNT(*) FROM demographicExt WHERE demographic_no=?', [id]) === '0',
        'An unexpected extension remains on the owned report patient');
    }
    for (const id of owned) sql.execute('DELETE FROM demographic WHERE demographic_no=? AND last_name=? AND first_name=? AND provider_no=?',
      [id, marker, 'Extension', provider]);
    h.assert(sql.value('SELECT COUNT(*) FROM demographic WHERE last_name=? AND first_name=? AND provider_no=?',
      [marker, 'Extension', provider]) === '0', 'The extension report patient remains');
  });
  await s.step('report configuration creates an identifier and commits a field add/delete', async () => {
    await page.goto(`${base}/report/ViewReportFormRecord`);
    await h.assertNotErrorPage(page, 'report list');
    await page.locator('input[name="name"]').fill(marker);
    await Promise.all([page.waitForEvent('framenavigated', {predicate:f=>f===page.mainFrame()}), page.locator('input[type="submit"][value="Add"]').click()]);
    reportId = sql.value(`SELECT id FROM reportItem WHERE report_name=${q(marker)}`);
    h.assert(/^[1-9]\d*$/.test(reportId), 'Report did not receive a generated ID');
    await page.goto(`${base}/report/ViewReportFormConfig?id=${reportId}&tableName=formAlpha`);
    await h.assertNotErrorPage(page, 'form metadata and captions');
    const field = await page.locator('select[name="selField"] option').evaluateAll(options => options.find(o => o.value.includes('|'))?.value);
    h.assert(field, 'Allowed empty-table metadata did not expose a configurable field');
    const fieldName=field.split('|')[1].trim();
    await page.locator('select[name="selField"]').selectOption(field);
    await Promise.all([page.waitForEvent('framenavigated', {predicate:f=>f===page.mainFrame()}), page.locator('input[type="submit"][value="Add"]').click()]);
    h.assert(sql.value(`SELECT COUNT(*) FROM reportConfig WHERE report_id=${reportId} AND name=${q(fieldName)} AND order_no=1`) === '1', 'Configuration write/order did not commit');
    const chosen = await page.locator('select[name="selConfig"] option').first().getAttribute('value');
    await page.locator('select[name="selConfig"]').selectOption(chosen);
    await Promise.all([page.waitForEvent('framenavigated', {predicate:f=>f===page.mainFrame()}), page.locator('input[type="submit"][value="Delete"]').click()]);
    h.assert(sql.value(`SELECT COUNT(*) FROM reportConfig WHERE report_id=${reportId}`) === '0', 'Configuration delete did not commit');
    await page.goto(`${base}/report/ViewReportFilter?id=${reportId}`);
    await h.assertNotErrorPage(page, 'typed report filter list');
  });
  await s.step('typed provider-role and PHCP report pages render native billed counts', async () => {
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM provider WHERE provider_no=${q(roleProvider)}) + (SELECT COUNT(*) FROM secUserRole WHERE provider_no=${q(roleProvider)})`) === '0', 'Synthetic role provider identifier is already in use');
    ownsRoleProvider = true;
    sql.execute(`INSERT INTO provider (provider_no,first_name,last_name,provider_type,sex,specialty,ohip_no,lastUpdateDate) VALUES (${q(roleProvider)},'Workflow',${q(marker)},'doctor','F','','',NOW()); INSERT INTO secUserRole (provider_no,role_name,orgcd,activeyn,lastUpdateDate) VALUES (${q(roleProvider)},'doctor','',1,NOW())`);
    await page.goto(`${base}/report/ViewReportonbilledvisitprovider`);
    await h.assertNotErrorPage(page, 'typed provider role report');
    const roleSelect = page.locator(`select[name="name${roleProvider}"]`);
    h.assert(await roleSelect.inputValue() === 'doctor', 'Typed provider projection lost the confirmed role');
    h.assert((await roleSelect.locator('xpath=ancestor::tr').textContent()).includes(marker), 'Typed provider projection lost the provider name');
    billingId = sql.value(`INSERT INTO billing (demographic_no,demographic_name,provider_no,creator,billing_date,status) VALUES (${patient},${q(marker)},${q(provider)},${q(provider)},'2026-08-01','O'); SELECT LAST_INSERT_ID()`);
    sql.execute(`INSERT INTO billingdetail (billing_no,service_code,service_desc,billing_amount,diagnostic_code,appointment_date,status) VALUES (${billingId},'Q5549',${q(marker)},'12.30','311','2026-08-01','O')`);
    try {
      await page.goto(`${base}/report/ViewReportonbilledphcp?submit=Go&codeType=ServiceCode&startDate=2026-08-01&endDate=2026-08-01&providerNoDoctor=${provider}`);
    } catch {
      throw new Error('Unable to open the typed PHCP report');
    }
    await h.assertNotErrorPage(page, 'typed PHCP report');
    const row = page.locator('tr').filter({has:page.locator('td').filter({hasText:marker})}).last();
    const cells = await row.locator('td').allTextContents();
    h.assert(cells[0].trim()==='Q5549' && cells[1].trim()===marker, 'Report lost service code or description');
    h.assert(cells[2].trim()==='1' && cells[3].trim()==='1', 'Native report did not render one patient and visit');
  });
  await s.step('demographic CSV includes each patient’s current extension, including NULL timestamps', async () => {
    h.assert(sql.value('SELECT COUNT(*) FROM demographic WHERE last_name=? AND first_name=? AND provider_no=?',
      [marker, 'Extension', provider]) === '0', 'The secondary report patient namespace is occupied');
    h.assert(sql.value('SELECT COUNT(*) FROM demographicExt WHERE demographic_no=?', [patient]) === '0',
      'The primary report patient has preexisting extensions');
    ownsExtensionPatient = true;
    extensionPatient = sql.insertId(`INSERT INTO demographic
      (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
       provider_no,hc_type,province,roster_status,lastUpdateDate)
      VALUES (?,?,'1980','01','02','F','AC',?,'ON','ON','NR',NOW())`,
    [marker, 'Extension', provider]);
    h.assert(/^[1-9]\d*$/.test(extensionPatient), 'The secondary report patient was not created');
    for (const [id, value, date] of [
      [patient, 'French', '2026-08-02 00:00:00'], [extensionPatient, 'English', '2026-08-03 00:00:00']
    ]) sql.execute('INSERT INTO demographicExt (demographic_no,provider_no,key_val,value,date_time,hidden) VALUES (?,?,?,?,?,0)',
      [id, provider, 'prefer_language', value, date]);
    const keyRows = sql.rows(`SELECT x.demographic_no,k.extension_key,x.value FROM demographicExt x
      JOIN (SELECT ? AS extension_key UNION ALL SELECT ? AS extension_key) k ON x.key_val=k.extension_key
      WHERE x.demographic_no IN (?,?) ORDER BY x.date_time,x.id`,
    ['prefer_language', 'PREFER_LANGUAGE', patient, extensionPatient]);
    const expectedKeys = new Set([
      `${patient}|prefer_language|French`, `${patient}|PREFER_LANGUAGE|French`,
      `${extensionPatient}|prefer_language|English`, `${extensionPatient}|PREFER_LANGUAGE|English`
    ]);
    h.assert(keyRows.length === 4 && keyRows.every(row => expectedKeys.delete(row.join('|'))) && expectedKeys.size === 0,
      'The physical database did not preserve both bound key spellings for each current patient value');
    await page.goto(`${base}/report/ViewReportFormRecord`);
    const token = await page.locator('input[name="CSRF-TOKEN"]').first().inputValue();
    async function verifyCohort() {
      const response = await s.context.request.post(`${base}/report/ViewReportResult`, {headers:{'CSRF-TOKEN':token}, form:{
        'CSRF-TOKEN':token, submit:'Report in CSV', demoReport:'true', last_name:'on', first_name:'on',
        prefer_language:'on', filter_1:'on', value_1:`demographic.demographic_no IN (${patient},${extensionPatient})`, dateFormat_1:''
      }});
      h.assert(response.status() === 200, `The cohort extension CSV failed with HTTP ${response.status()}`);
      const rows = (await response.text()).trimEnd().split(/\r?\n/).map(line => line.split('\t'));
      h.assert(rows.length === 3, 'The cohort CSV did not contain exactly two patients');
      const nameIndex = rows[0].indexOf('First Name'), languageIndex = rows[0].indexOf('Preferred Language');
      h.assert(nameIndex >= 0 && languageIndex >= 0, 'The cohort CSV lost its selected columns');
      const first = rows.slice(1).find(row => row[nameIndex] === 'Workflow');
      const second = rows.slice(1).find(row => row[nameIndex] === 'Extension');
      h.assert(first && first[languageIndex] === 'French', 'The first patient lost its current extension value');
      h.assert(second && second[languageIndex] === 'English', 'The second patient lost its current extension value');
      await response.dispose();
    }
    await verifyCohort();
    sql.execute('UPDATE demographicExt SET date_time=NULL WHERE demographic_no=? AND provider_no=? AND key_val=?',
      [extensionPatient, provider, 'prefer_language']);
    await verifyCohort();
  });
  await s.step('detached demographic CSV preserves the selected synthetic row', async () => {
    // An authenticated form POST exercises the gated CSV dispatch and its download response.
    await page.goto(`${base}/report/ViewReportFormRecord`);
    const token = await page.locator('input[name="CSRF-TOKEN"]').first().inputValue();
    const response = await s.context.request.post(`${base}/report/ViewReportResult`, {headers:{'CSRF-TOKEN':token},form:{
      'CSRF-TOKEN':token, submit:'Report in CSV', demoReport:'true', last_name:'on', filter_1:'on',
      value_1:`demographic.demographic_no=${patient}`, dateFormat_1:''
    }});
    h.assert(response.status()===200, `CSV failed with HTTP ${response.status()}`);
    h.assert((response.headers()['content-disposition'] || '').startsWith('attachment;'), 'CSV lost its attachment response header');
    h.assert((response.headers()['content-type'] || '').includes('text/csv'), 'CSV lost its content type');
    const csv = await response.text();
    h.assert(csv.includes(marker) && csv.includes('Last Name'), 'CSV omitted the selected synthetic demographic or header');
    const failed = await s.context.request.post(`${base}/report/ViewReportResult`, {headers:{'CSRF-TOKEN':token},form:{
      'CSRF-TOKEN':token, submit:'Report in CSV', demoReport:'true', last_name:'on', filter_1:'on',
      value_1:'demographic.missing_report_column_5549=1', dateFormat_1:''
    }});
    h.assert(failed.status() === 500, 'Failed gated CSV query did not retain its HTTP error status');
  });
}
if (require.main === module) runWorkflow('report-typed-queries', workflow, {openPatient: true, openMaster: false});
module.exports = {workflow};
