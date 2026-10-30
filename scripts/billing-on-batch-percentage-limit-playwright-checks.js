#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */

/*
 * Ontario batch billing bills a percentage code within the code's configured min/max (issue #5307).
 * User path: Schedule ▸ Administration ▸ Billing ▸ Batch Billing (BatchBill, #myFrame) ▸ provider ▸
 * tick both rows ▸ Generate Batch Invoices.
 * Asserted: two queue rows, each a percentage-only code (so its base is 0.00). The code with a
 * configured limit bills that limit's minimum, as the interactive review page clamps it; the code
 * with no limit of its own bills 0.00, even though a limit row keyed by its numeric fee-row id exists.
 * Before #5307 the fee row's percentage-limit mapping joined limits on that id, not on the code, so
 * the batch total read the decoy row and never the code's own limit. Both queue rows are stamped
 * with the billed amount.
 * Fixtures: runWorkflow FAKE- patient + a second FAKE- patient, a FAKE- billable provider (with the
 * operator's site memberships), two FAKE- percentage fee rows with codes no fee schedule uses, their
 * limit rows and two batch_billing rows; all removed and re-checked in cleanup.
 */

const { randomInt } = require('node:crypto');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const DX = '250';
const PERCENTAGE = '0.30';
const MINIMUM = '7.50';
const DECOY_MINIMUM = '3.00';
const owned = id => /^[1-9]\d*$/.test(String(id));

function unusedProviderNo(sql) {
  for (let attempt = 0; attempt < 20; attempt++) {
    const candidate = String(randomInt(800000, 899999));
    if (sql.value(`SELECT (SELECT COUNT(*) FROM provider WHERE provider_no=${h.sqlString(candidate)})
      + (SELECT COUNT(*) FROM batch_billing WHERE billing_provider_no=${h.sqlString(candidate)})`) === '0') return candidate;
  }
  throw new Error('No unused provider number was found');
}

/** A five-character Ontario-shaped code that no fee row, limit or queue row uses yet. */
function unusedServiceCode(sql, taken) {
  for (let attempt = 0; attempt < 50; attempt++) {
    const candidate = `Q${randomInt(900, 999)}${'PQRSTU'[randomInt(0, 6)]}`;
    if (taken.includes(candidate)) continue;
    const c = h.sqlString(candidate);
    // The batch total resolves a code with LIKE 'code%', so no fee row may even start with it.
    if (sql.value(`SELECT (SELECT COUNT(*) FROM billingservice WHERE service_code LIKE ${h.sqlString(`${candidate}%`)})
      + (SELECT COUNT(*) FROM billingperclimit WHERE service_code=${c})
      + (SELECT COUNT(*) FROM batch_billing WHERE service_code=${c})`) === '0') return candidate;
  }
  throw new Error('No unused service code was found');
}

/** Administration ▸ <left-nav item> loaded into the #myFrame iframe. */
async function openAdminFrame(admin, rel, ready) {
  const link = admin.locator(`a.xlink[rel$="${rel}"]`).first();
  await link.waitFor({ state: 'attached' });
  await revealAuditLink(admin, link, 20000);
  await link.click();
  const iframe = admin.locator('#dynamic-content iframe#myFrame');
  await iframe.waitFor();
  const frame = await (await iframe.elementHandle()).contentFrame();
  h.assert(frame, `${rel} did not load into the administration frame`);
  await frame.locator(ready).first().waitFor();
  return frame;
}

/** Run `action` and wait for `frame` to finish its next navigation. */
async function frameNavigation(admin, frame, action) {
  const navigated = admin.waitForEvent('framenavigated', { predicate: f => f === frame, timeout: 30000 });
  navigated.catch(() => {});
  await action();
  await navigated;
  await frame.waitForLoadState('domcontentloaded');
  await h.assertNotErrorPage(frame, 'administration frame');
}

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const q = h.sqlString;
  const ids = { other: '', providerNo: '', limited: '', unlimited: '', limitedFee: '', unlimitedFee: '' };
  const patients = () => [patient, ids.other].filter(owned).join(',');
  const codes = () => [ids.limited, ids.unlimited].filter(Boolean).map(q).join(',') || "''";
  const feeRows = () => [ids.limitedFee, ids.unlimitedFee].filter(owned);

  s.cleanup(() => {
    const demos = patients();
    const headers = sql.rows(`SELECT id FROM billing_on_cheader1 WHERE demographic_no IN (${demos})`).map(r => r[0]);
    const hIds = headers.length ? headers.map(Number).join(',') : '0';
    sql.execute(`DELETE FROM billing_on_ext WHERE billing_no IN (${hIds});
      DELETE FROM billing_on_item WHERE ch1_id IN (${hIds});
      DELETE FROM billing_on_cheader1 WHERE id IN (${hIds}) AND demographic_no IN (${demos});
      DELETE FROM batch_billing WHERE demographic_no IN (${demos})`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM billing_on_cheader1 WHERE demographic_no IN (${demos}))
      + (SELECT COUNT(*) FROM billing_on_item WHERE ch1_id IN (${hIds}))
      + (SELECT COUNT(*) FROM batch_billing WHERE demographic_no IN (${demos}))`) === '0',
    'Owned claims or batch queue rows were not removed');
    // The limit rows are keyed by the owned codes and, for the decoy, by the owned fee row's id.
    const limitKeys = [codes(), ...feeRows().map(id => q(String(id)))].join(',');
    sql.execute(`DELETE FROM billingperclimit WHERE service_code IN (${limitKeys});
      DELETE FROM billingservice WHERE service_code IN (${codes()}) AND description=${q(marker)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM billingperclimit WHERE service_code IN (${limitKeys}))
      + (SELECT COUNT(*) FROM billingservice WHERE service_code IN (${codes()}))`) === '0',
    'The owned fee rows or their limit rows were not removed');
    if (ids.providerNo) {
      const p = q(ids.providerNo);
      sql.execute(`DELETE FROM providersite WHERE provider_no=${p};
        DELETE FROM provider WHERE provider_no=${p} AND last_name=${q(marker)}`);
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM provider WHERE provider_no=${p})
        + (SELECT COUNT(*) FROM providersite WHERE provider_no=${p})`) === '0', 'The owned provider was not removed');
    }
    if (owned(ids.other)) {
      sql.execute(`DELETE FROM demographicArchive WHERE demographic_no=${ids.other};
        DELETE FROM demographic WHERE demographic_no=${ids.other} AND last_name=${q(`${marker}-B`)}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${ids.other}`) === '0',
        'The second owned patient was not removed');
    }
  });

  ids.limited = unusedServiceCode(sql, []);
  ids.unlimited = unusedServiceCode(sql, [ids.limited]);
  const fee = code => sql.value(`INSERT INTO billingservice (service_code,description,value,percentage,
      billingservice_date,specialty,region,anaesthesia,termination_date,sliFlag,gstFlag)
    VALUES (${q(code)},${q(marker)},'',${q(PERCENTAGE)},'2000-01-01','','ON','00','9999-12-31',0,0); SELECT LAST_INSERT_ID()`);
  ids.limitedFee = fee(ids.limited);
  ids.unlimitedFee = fee(ids.unlimited);
  h.assert(owned(ids.limitedFee) && owned(ids.unlimitedFee), 'The percentage fee-row fixtures were not created');
  sql.execute(`INSERT INTO billingperclimit (service_code,min,max,effective_date)
      VALUES (${q(ids.limited)},${q(MINIMUM)},'9.00','2000-01-01');
    INSERT INTO billingperclimit (service_code,min,max,effective_date)
      VALUES (${q(String(ids.unlimitedFee))},${q(DECOY_MINIMUM)},'4.00','2000-01-01')`);

  ids.providerNo = unusedProviderNo(sql);
  const p = q(ids.providerNo);
  sql.execute(`INSERT INTO provider (provider_no,last_name,first_name,provider_type,specialty,sex,ohip_no,rma_no,status,
      lastUpdateUser,lastUpdateDate) VALUES (${p},${q(marker)},'Batch','doctor','','F',${q(String(randomInt(100000, 999999)))},'','1',
      ${q(provider)},NOW());
    INSERT INTO providersite (provider_no,site_id) SELECT ${p},site_id FROM providersite WHERE provider_no=${q(provider)}`);
  ids.other = sql.value(`INSERT INTO demographic (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,
      patient_status,provider_no,hc_type,province,roster_status,lastUpdateDate)
    VALUES (${q(`${marker}-B`)},'Second','1975','03','04','M','AC',${q(provider)},'ON','ON','NR',NOW()); SELECT LAST_INSERT_ID()`);
  h.assert(owned(ids.other), 'The second patient fixture was not created');
  const rows = [[patient, ids.limited], [ids.other, ids.unlimited]];
  for (const [demo, code] of rows) {
    sql.execute(`INSERT INTO batch_billing (demographic_no,billing_provider_no,service_code,dxcode,create_date,creator)
      VALUES (${demo},${p},${q(code)},${q(DX)},NOW(),${q(provider)})`);
  }
  h.assert(sql.value(`SELECT COUNT(*) FROM batch_billing WHERE billing_provider_no=${p}`) === '2',
    'The batch queue fixtures were not created');
  const total = demo => sql.value(`SELECT CONCAT_WS('|', COUNT(*), IFNULL(MAX(total),'-'))
    FROM billing_on_cheader1 WHERE demographic_no=${demo}`);
  const queueRow = demo => sql.value(`SELECT CONCAT_WS('|', IFNULL(billing_amount,'-'), IFNULL(lastbilled_date,'-'))
    FROM batch_billing WHERE demographic_no=${demo} AND billing_provider_no=${p}`);

  const { page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'administration', timeout: 20000 });
  let batch;

  await s.step('Batch Billing lists the two percentage-code queue rows', async () => {
    batch = await openAdminFrame(admin, '/billing/CA/ON/BatchBill?service_code=all', 'select[name="providers"]');
    await frameNavigation(admin, batch, () => batch.locator('select[name="providers"]').selectOption(ids.providerNo));
    const boxes = await batch.locator('input[type="checkbox"][name="bill"]').evaluateAll(b => b.map(x => x.value));
    h.assert(boxes.length === 2
      && boxes.includes(`${ids.limited};${DX};${patient};${ids.providerNo}`)
      && boxes.includes(`${ids.unlimited};${DX};${ids.other};${ids.providerNo}`),
    'The batch list for the owned provider does not show exactly its two percentage-code rows');
  });

  let billDate;
  await s.step('Generate bills each percentage code within its own configured limit', async () => {
    billDate = await batch.locator('#BillDate').inputValue();
    h.assert(/^\d{4}-\d{2}-\d{2}$/.test(billDate), 'The batch service date is not prefilled as YYYY-MM-DD');
    for (const [demo] of rows) await batch.locator(`input[name="bill"][value*=";${demo};"]`).check();
    await frameNavigation(admin, batch, () => batch.locator('input[type="button"].btn-primary').click());
    await expectValue(sql, `SELECT COUNT(*) FROM billing_on_cheader1 WHERE demographic_no IN (${patients()})`, '2',
      'Generating the batch did not create one claim per ticked row');
    // A percentage-only claim has a 0.00 base, so the configured minimum is what it bills.
    h.assert(total(patient) === `1|${MINIMUM}`,
      `The limited percentage code billed ${total(patient)}, not its configured minimum ${MINIMUM}`);
    // No limit is configured for this code; the row keyed by its fee-row id belongs to no code.
    h.assert(total(ids.other) === '1|0.00',
      `The unlimited percentage code billed ${total(ids.other)}; it read a limit that is not its own`);
    h.assert(queueRow(patient) === `${MINIMUM}|${billDate}` && queueRow(ids.other) === `0.00|${billDate}`,
      'The billed queue rows were not stamped with their amounts and the service date');
  });
}

if (require.main === module) runWorkflow('billing-on-batch-percentage-limit', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
