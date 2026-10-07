#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/* Real filtered profile responses, deliberately delaying the replacement response. */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  s.cleanup(() => {
    sql.execute(`DELETE FROM drugs WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`) === '0',
      'Owned profile drugs remain');
  });
  for (const [suffix, longTerm] of [['LT', 1], ['ACUTE', 0]]) {
    sql.execute(`INSERT INTO drugs(provider_no,demographic_no,rx_date,end_date,written_date,
      BN,GCN_SEQNO,customName,takemin,takemax,freqcode,duration,durunit,quantity,\`repeat\`,
      special,archived,archived_reason,position,dispenseInternal,create_date,lastUpdateDate,long_term)
      VALUES(${h.sqlString(provider)},${patient},CURDATE(),DATE_ADD(CURDATE(),INTERVAL 30 DAY),CURDATE(),
      ${h.sqlString(`${marker}-${suffix}`)},0,${h.sqlString(`${marker}-${suffix}`)},1,1,'OD','30','D','30',0,
      ${h.sqlString(`${marker}-${suffix} one tablet daily`)},0,'',0,0,NOW(),NOW(),${longTerm})`);
  }
  const rx = await s.popup(s.master,
    s.master.locator('a[onclick*="/rx/choosePatient"]').first(), 'rx-profile');
  h.assert(new URL(rx.url()).searchParams.get('demographicNo') === patient,
    'Prescription window opened another patient');
  await rx.locator('#searchString').waitFor({state:'visible'});
  await rx.waitForLoadState('networkidle');

  await s.step('LT / Acute retains both server-filtered sections when replacement is delayed', async () => {
    let releaseAcute;
    const acuteRendered = new Promise(resolve => { releaseAcute = resolve; });
    const requests = [];
    const completed = [];
    const routing = '**/rx/ViewListDrugs?**';
    await rx.route(routing, async route => {
      const url = new URL(route.request().url());
      const heading = url.searchParams.get('heading');
      if (!['Long Term Meds', 'Acute'].includes(heading)) return route.continue();
      h.assert(url.searchParams.get('demographicNo') === patient, 'Profile request lost its patient');
      requests.push(heading);
      const response = await route.fetch();
      h.assert(response.status() === 200, `Profile response answered ${response.status()}`);
      if (heading === 'Long Term Meds') {
        // The fixed sequential loader cannot request Acute until this response lands.
        // The old parallel loader renders Acute first; wait for that DOM insertion so
        // releasing the replacement deterministically exposes the lost section.
        await Promise.race([acuteRendered, new Promise(resolve => setTimeout(resolve, 2000))]);
      }
      await route.fulfill({response});
      completed.push(heading);
      if (heading === 'Acute') {
        await rx.locator('#drugProfile h4').filter({hasText:/^Acute$/}).waitFor({state:'visible'});
        releaseAcute();
      }
    });
    try {
      await rx.getByRole('link', {name:'Longterm/Acute', exact:true}).click();
      const deadline = Date.now() + 15000;
      while (completed.length < 2 && Date.now() < deadline) await rx.waitForTimeout(50);
      h.assert(completed.length === 2 && requests.length === 2, 'Both filtered profile requests must complete');
      await rx.waitForLoadState('networkidle');
      const headings = await rx.locator('#drugProfile h4').allTextContents();
      h.assert(JSON.stringify(headings.map(value => value.trim())) === JSON.stringify(['Long Term Meds','Acute']),
        `Combined profile lost or reordered sections: ${JSON.stringify(headings)}`);
      const body = await rx.locator('#drugProfile').innerText();
      h.assert(body.includes(`${marker}-LT`) && body.includes(`${marker}-ACUTE`),
        'Combined profile omitted an owned medication');
    } finally {
      await rx.unroute(routing);
    }
  });
}

if (require.main === module) runWorkflow('rx-profile-legend', workflow);
module.exports = {workflow};
