#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// rx-cpp-visibility-save-refusal: the medication profile's "Hide from CPP" checkbox tells the truth
// about what was stored (#5001).
// User path: Schedule ▸ Search ▸ Master Record ▸ Prescriptions ▸ the medication's Hide from CPP
// checkbox (ListDrugs.jsp ▸ RxHideCpp.save ▸ POST rx/hideCpp method=update).
//   1. a refused save (the check answers exactly that one POST with 403, so nothing reaches the
//      server) is reported to the user and the box shows the stored, unchecked state again;
//   2. a real save stores hide_cpp=1 and keeps the box checked, with no message;
//   3. unchecking stores hide_cpp=0 again.
// A second medication's flag is the control and never changes.
// Fixtures: the owned FAKE- patient and two marker-named custom drug rows inserted directly (no
// DrugRef data). Cleanup removes the patient's drugs and prescription rows and asserts none remain.
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { openRx } = require('./rx-stash-patient-isolation-playwright-checks');
const { failureMark, consumeExpectedFailure } = require('./lib/concurrency-support');

function insertDrug(sql, provider, patient, name) {
  return h.insertId(sql, `INSERT INTO drugs (provider_no,demographic_no,rx_date,end_date,written_date,customName,special,
      archived,archived_reason,create_date,position,lastUpdateDate,dispenseInternal,quantity,duration,durunit,freqcode,hide_cpp)
    VALUES (${h.sqlString(provider)},${patient},CURDATE(),DATE_ADD(CURDATE(),INTERVAL 30 DAY),CURDATE(),${h.sqlString(name)},
      ${h.sqlString(`${name}\n1 tab PO daily`)},0,'',NOW(),0,NOW(),0,'30','30','D','OD',0)`, 'drug');
}

const isHideCpp = request => request.method() === 'POST' && h.pathOnly(request.url()).endsWith('/rx/hideCpp');

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  s.cleanup(() => {
    sql.execute(`DELETE FROM drugs WHERE demographic_no=${patient}; DELETE FROM prescription WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`) === '0', 'Owned drug rows were not removed');
  });
  const drug = insertDrug(sql, provider, patient, `${marker} HIDDEN`);
  const control = insertDrug(sql, provider, patient, `${marker} CONTROL`);
  const flag = id => sql.value(`SELECT hide_cpp FROM drugs WHERE drugid=${id}`);
  const rx = await openRx(s, patient);
  const box = rx.locator(`#hidecpp_${drug}`);
  await box.waitFor({ state: 'visible' });
  h.assert(!(await box.isChecked()), 'The fixture drug starts hidden from the CPP');

  await s.step('a refused Hide CPP save reports failure and restores the actual unchecked state', async () => {
    const mark = failureMark(s.recorder);
    let refused = 0;
    await rx.route('**/rx/hideCpp', async route => {
      if (!isHideCpp(route.request())) return route.continue();
      refused++;
      return route.fulfill({ status: 403, contentType: 'text/html', body: '<html><body>Forbidden</body></html>' });
    });
    try {
      const dialogs = await h.withExpectedDialogs(rx, async () => {
        await box.click();
        await rx.waitForFunction(id => {
          const el = document.getElementById(`hidecpp_${id}`);
          return el && !el.disabled && !el.checked;
        }, drug);
      });
      h.assert(refused === 1, `${refused} Hide CPP request(s) reached the refusal instead of one`);
      h.assert(dialogs.length === 1 && dialogs[0].type === 'alert' && /NOT saved/i.test(dialogs[0].text),
        `The refused save raised ${JSON.stringify(dialogs)}`);
    } finally {
      await rx.unroute('**/rx/hideCpp');
    }
    consumeExpectedFailure(s.recorder, mark, { status: 403, path: /\/rx\/hideCpp$/ });
    h.assert(!(await box.isChecked()), 'The box claims the refused change');
    h.assert(flag(drug) === '0' && flag(control) === '0', 'A refused save changed a stored flag');
  });

  await s.step('a successful hide and unhide persist and keep the box in step', async () => {
    const [hid] = await Promise.all([rx.waitForResponse(r => isHideCpp(r.request())), box.click()]);
    h.assert(hid.status() === 200, `Hide answered HTTP ${hid.status()}`);
    await expectValue(sql, `SELECT hide_cpp FROM drugs WHERE drugid=${drug}`, '1', 'Hide was not stored');
    await rx.waitForFunction(id => !document.getElementById(`hidecpp_${id}`).disabled, drug);
    h.assert(await box.isChecked(), 'The box lost a stored hide');
    const [shown] = await Promise.all([rx.waitForResponse(r => isHideCpp(r.request())), box.click()]);
    h.assert(shown.status() === 200, `Unhide answered HTTP ${shown.status()}`);
    await expectValue(sql, `SELECT hide_cpp FROM drugs WHERE drugid=${drug}`, '0', 'Unhide was not stored');
    await rx.waitForFunction(id => !document.getElementById(`hidecpp_${id}`).disabled, drug);
    h.assert(!(await box.isChecked()), 'The box kept a removed hide');
    h.assert(flag(control) === '0', 'Toggling one medication changed the control');
  });
}

if (require.main === module) runWorkflow('rx-cpp-visibility-save-refusal', workflow, { openPatient: true });
module.exports = { workflow };
