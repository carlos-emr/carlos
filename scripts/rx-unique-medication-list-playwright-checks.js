#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Unique medication list (#4420): the E-Chart Medications panel, a new consultation's "Current Medications" and
 * the REST medication summary list each current product and regimen once, and agree with each other.
 * User path: Schedule ▸ Search ▸ Master Record ▸ E-Chart (Medications module) ▸ Consultations "+" (new request).
 * The REST summary is the one the chart's record UI reads (GET /ws/rs/recordUX/{demo}/fullSummary/meds).
 *
 * Only ReRx archives the source row, so a drug prescribed again by searching for it, and CDS-imported history, leave
 * every earlier copy unarchived. #4286 keyed the list on the dated fields too, so each renewal added a line.
 *
 * Asserts: a renewal whose earlier copy expired, two overlapping copies of one regimen, and a ReRx pair each give one
 * line; two concurrent regimens of one product (10 mg and 20 mg) give one line each (#4270); a newer archived copy
 * does not hide its current sibling. The consultation lists each current product once; the REST summary lists the
 * same entries as the E-Chart panel. Every seeded drug row is still in the database afterwards (read-only views).
 * Fixtures: the owned FAKE-PW patient with one SQL-seeded prescription row and ten drug rows; cleanup deletes the
 * patient's drugs and prescription rows and asserts them gone. Nothing is saved from the consultation form.
 * Operator prerequisite: CONSULTATION_AUTO_INCLUDE_MEDICATIONS=true in carlos.properties (the packaged default is false;
 * the form's Import button uses another path), then restart. See docs/ui-tests/deb-install-validation.md section 4.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

const q = h.sqlString;

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  s.cleanup(() => {
    sql.execute(`DELETE FROM drugs WHERE demographic_no=${patient}; DELETE FROM prescription WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM prescription WHERE demographic_no=${patient})`) === '0', 'Owned drug rows were not removed');
  });
  const script = sql.value(`INSERT INTO prescription(provider_no,demographic_no,date_prescribed,date_printed,textView,lastUpdateDate)
    VALUES(${q(provider)},${patient},CURDATE(),CURDATE(),'Synthetic prescription',NOW()); SELECT LAST_INSERT_ID()`);
  // Rows are inserted oldest first, so a higher id is a newer prescription, as in the application.
  const drug = (name, { start = 60, end = 30, dose = 10, archived = '' } = {}) => sql.execute(`INSERT INTO drugs(provider_no,
      demographic_no,rx_date,end_date,written_date,BN,GCN_SEQNO,customName,dosage,unit,takemin,takemax,freqcode,duration,durunit,
      quantity,\`repeat\`,special,archived,archived_reason,archived_date,script_no,position,dispenseInternal,create_date,lastUpdateDate)
    VALUES(${q(provider)},${patient},DATE_SUB(CURDATE(),INTERVAL ${start} DAY),DATE_ADD(CURDATE(),INTERVAL ${end} DAY),
      DATE_SUB(CURDATE(),INTERVAL ${start} DAY),${q(`${marker}-${name}`)},0,${q(`${marker}-${name}`)},${q(String(dose))},'mg',1,1,'OD',
      '30','D','30',0,${q(`${marker}-${name} ${dose} mg daily`)},${archived ? 1 : 0},${q(archived)},${archived ? 'NOW()' : 'NULL'},
      ${script},0,0,NOW(),NOW())`);
  drug('RENEWED', { start: 120, end: -90 });
  drug('RENEWED', { start: 20, end: 10 });
  drug('OVERLAP', { start: 60, end: 30 });
  drug('OVERLAP', { start: 30, end: 60 });
  drug('TWOREG', { dose: 10 });
  drug('TWOREG', { dose: 20 });
  drug('RERX', { archived: 'represcribed' });
  drug('RERX');
  drug('STATUS');
  drug('STATUS', { archived: 'other' });
  const seeded = sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`);
  h.assert(seeded === '10', `Expected 10 seeded drug rows, found ${seeded}`);

  // One line per product, except the two regimens of TWOREG, which keep one line each.
  const expected = { RENEWED: 1, OVERLAP: 1, RERX: 1, STATUS: 1, 'TWOREG 10 mg': 1, 'TWOREG 20 mg': 1 };
  const counts = lines => Object.fromEntries(Object.keys(expected).map(key => {
    const [name, ...dose] = key.split(' ');
    const needle = `${marker}-${name}`;
    return [key, lines.filter(line => line.includes(needle) && (!dose.length || line.includes(` ${dose.join(' ')}`))).length];
  }));
  const assertCounts = (where, actual) => {
    for (const [key, times] of Object.entries(expected)) {
      h.assert(actual[key] === times, `${where} lists ${key} ${actual[key]} time(s), expected ${times} (${JSON.stringify(actual)})`);
    }
  };

  const chart = await s.chart();
  let panel;
  await s.step('the E-Chart Medications panel lists each current product and regimen once', async () => {
    const rx = chart.locator('#Rx');
    await rx.locator('li').first().waitFor({ state: 'attached', timeout: 20000 });
    // The title may be cropped; the link title carries the whole outline.
    panel = await rx.locator('li').evaluateAll(items => items.map(item => {
      const link = item.querySelector('a[title]');
      return `${item.textContent || ''} ${link ? link.getAttribute('title') : ''}`.replace(/\s+/g, ' ');
    }).filter(text => text.trim()));
    assertCounts('The E-Chart Medications panel', counts(panel));
  });

  await s.step('a new consultation lists each current medication once in Current Medications', async () => {
    const form = await s.popup(chart, chart.locator('a[onclick*="ViewConsultationFormRequest?de="]').first(), 'consult-form');
    try {
      const meds = form.locator('textarea[name="currentMedications"]');
      await meds.waitFor({ state: 'attached', timeout: 30000 });
      const text = await meds.inputValue();
      h.assert(text.includes(marker), 'Current Medications was not filled from the chart (CONSULTATION_AUTO_INCLUDE_MEDICATIONS off?)');
      assertCounts('The consultation Current Medications', counts(text.split('\n')));
    } finally {
      if (!form.isClosed()) await form.close().catch(() => {});
    }
  });

  await s.step('the REST medication summary lists the same entries as the E-Chart panel', async () => {
    const url = h.appUrl(s.config.baseUrl, `/ws/rs/recordUX/${patient}/fullSummary/meds`);
    const response = await s.context.request.get(url, { headers: { Accept: 'application/json' } });
    h.assert(response.status() === 200, `The medication summary answered HTTP ${response.status()}`);
    const body = await response.json();
    const items = [].concat(body.summaryItem || []).map(item => String(item.displayName || ''));
    assertCounts('The REST medication summary', counts(items));
    h.assert(JSON.stringify(counts(items)) === JSON.stringify(counts(panel)), 'The REST summary and the E-Chart panel disagree');
  });

  await s.step('viewing the lists changed no drug row', async () => {
    h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`) === seeded, 'A drug row was added or removed');
    h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient} AND archived=1`) === '2',
      'A drug row was archived or restored');
  });
}

if (require.main === module) {
  runWorkflow('rx-unique-medication-list', workflow, { openPatient: true });
}
module.exports = { workflow };
