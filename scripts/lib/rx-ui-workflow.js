/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const h = require('./playwright-harness');
const {expectValue} = require('./workflow-session');

// Own every clinical row in the synthetic patient's medication workflow. Shared
// provider settings and catalogues are never changed by this helper.
async function prepareRx(s) {
  const {sql, patient, marker, provider} = s;
  s.cleanup(() => {
    const drugs = `SELECT drugid FROM drugs WHERE demographic_no=${patient}`;
    const notes = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`;
    sql.execute(`DELETE FROM drugReason WHERE demographicNo=${patient};
      DELETE FROM partial_date WHERE table_name=2 AND table_id IN (${drugs});
      DELETE FROM drugs WHERE demographic_no=${patient};
      DELETE FROM prescription WHERE demographic_no=${patient};
      DELETE FROM DigitalSignature WHERE demographicId=${patient} AND ModuleType='PRESCRIPTION';
      DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_link WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note WHERE demographic_no=${patient};
      DELETE FROM casemgmt_issue WHERE demographic_no=${patient};
      DELETE FROM eChart WHERE demographicNo=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient})
      +(SELECT COUNT(*) FROM prescription WHERE demographic_no=${patient})
      +(SELECT COUNT(*) FROM drugReason WHERE demographicNo=${patient})
      +(SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient})`) === '0', 'Owned Rx clinical rows remain');
  });
  const today = sql.value("SELECT DATE_FORMAT(CURDATE(),'%Y-%m-%d')");
  const end = sql.value("SELECT DATE_FORMAT(DATE_ADD(CURDATE(),INTERVAL 90 DAY),'%Y-%m-%d')");
  function seed(label, overrides = {}) {
    const values = {provider_no:provider, demographic_no:patient, rx_date:today, end_date:end,
      written_date:today, BN:`${marker} ${label}`, customName:`${marker} ${label}`,
      special:'1 tab PO OD x 30 days', position:0, dispenseInternal:0,
      archived:0, hide_cpp:0, long_term:0, past_med:0, prn:0, nosubs:0,
      quantity:'30',repeat:0,dosage:'1',freqcode:'OD',duration:'30',durunit:'D',method:'tab',route:'PO',
      create_date:`${today} 09:00:00`,lastUpdateDate:`${today} 09:00:00`,...overrides};
    h.assert(Object.keys(values).every(key => /^[a-zA-Z_]+$/.test(key)), 'Invalid fixture column');
    const literal = value => value === null || value === undefined ? 'NULL' : h.sqlString(value);
    return h.insertId(sql, `INSERT INTO drugs (${Object.keys(values).map(key=>`\`${key}\``).join(',')})
      VALUES (${Object.values(values).map(literal).join(',')})`, 'owned medication');
  }
  async function open() {
    const chart = await s.chart();
    const rx = await s.popup(chart, chart.locator('#menuTitleRx a').first(), 'rx-owned-workflow');
    await rx.locator('#searchString').waitFor({state:'visible'});
    await rx.waitForLoadState('networkidle');
    h.assert(new URL(rx.url()).searchParams.get('demographicNo')===patient, 'Rx opened another patient');
    return rx;
  }
  return {seed, open, today, end};
}

async function saveOnly(rx) {
  const [response] = await Promise.all([
    rx.waitForResponse(r=>r.request().method()==='POST' && new URL(r.url()).pathname.endsWith('/rx/WriteScript')
      && new URL(r.url()).searchParams.get('parameterValue')==='updateSaveAllDrugs'),
    rx.locator('#saveOnlyButton').click(),
  ]);
  h.assert(response.status()===200, `Save Only answered HTTP ${response.status()}`);
  const result = await response.json();
  h.assert(/^[1-9]\d*$/.test(String(result.scriptId)), 'Save Only returned no prescription identity');
  return String(result.scriptId);
}

async function stageExisting(rx, drug) {
  await rx.locator(`#reRxCheckBox_${drug}`).check();
  await rx.locator('[onclick="stageSelectedReRxMedications()"] ').click();
  await rx.locator('[id^="drugName_"]').first().waitFor({state:'visible'});
  const ids = await rx.locator('[id^="drugName_"]').evaluateAll(nodes=>nodes.map(node=>node.id.split('_')[1]));
  h.assert(ids.length===1, 'ReRx did not stage exactly one medication');
  return ids[0];
}

module.exports = {prepareRx, saveOnly, stageExisting, expectValue};
