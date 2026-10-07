#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/* Real filtered profile responses, deliberately delaying the replacement response. */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { failureMark, consumeExpectedFailure, waitUntil } = require('./lib/concurrency-support');

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  s.cleanup(() => {
    sql.execute(`DELETE FROM drugs WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`) === '0',
      'Owned profile drugs remain');
  });
  for (const [suffix, longTerm] of [['LT', 1], ['ACUTE', 0], ['INACTIVE', 0], ['EXTERNAL', 0]]) {
    sql.execute(`INSERT INTO drugs(provider_no,demographic_no,rx_date,end_date,written_date,
      BN,GCN_SEQNO,customName,takemin,takemax,freqcode,duration,durunit,quantity,\`repeat\`,
      special,archived,archived_reason,position,dispenseInternal,create_date,lastUpdateDate,long_term,
      outside_provider_name,outside_provider_ohip)
      VALUES(${h.sqlString(provider)},${patient},CURDATE(),${suffix === 'INACTIVE' ? 'DATE_SUB(CURDATE(),INTERVAL 7 DAY)' : 'DATE_ADD(CURDATE(),INTERVAL 30 DAY)'},CURDATE(),
      ${h.sqlString(`${marker}-${suffix}`)},0,${h.sqlString(`${marker}-${suffix}`)},1,1,'OD','30','D','30',0,
      ${h.sqlString(`${marker}-${suffix} one tablet daily`)},0,'',0,0,NOW(),NOW(),${longTerm},
      ${suffix === 'EXTERNAL' ? "'FAKE external','000000'" : "NULL,NULL"})`);
  }
  const rx = await s.popup(s.master,
    s.master.locator('a[onclick*="/rx/choosePatient"]').first(), 'rx-profile');
  h.assert(new URL(rx.url()).searchParams.get('demographicNo') === patient,
    'Prescription window opened another patient');
  await rx.locator('#searchString').waitFor({state:'visible'});
  await rx.waitForLoadState('networkidle');
  const combinedLink = () => rx.getByRole('link', {name:'Longterm/Acute/Inactive/External', exact:true});
  const profile = rx.locator('#drugProfile');
  async function ready(headings) {
    await rx.waitForFunction(() => document.getElementById('drugProfile').getAttribute('aria-busy') === 'false');
    h.assert(await profile.isVisible(), 'The completed profile is hidden');
    h.assert(JSON.stringify((await profile.locator('h4').allTextContents()).map(value => value.trim())) === JSON.stringify(headings),
      'The completed profile has missing or reordered sections');
  }

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

  await s.step('the four-section view preserves server filtering and order', async () => {
    await combinedLink().click();
    await ready(['Long Term Meds','Acute','Inactive','External']);
    for (const [heading, suffix] of [['Long Term Meds','LT'], ['Acute','ACUTE'], ['Inactive','INACTIVE'], ['External','EXTERNAL']]) {
      const text = await profile.locator(`table[id="Drug_table${heading}"]`).innerText();
      h.assert(text.includes(`${marker}-${suffix}`), `The ${heading} section omitted its owned medication`);
      if (heading === 'Inactive') h.assert(!text.includes(`${marker}-ACUTE`), 'Inactive contains the active medication');
      if (heading === 'External') h.assert(!text.includes(`${marker}-LT`), 'External contains the local medication');
    }
  });

  await s.step('changing long-term status refreshes the selected four-section view', async () => {
    const id = sql.value(`SELECT drugid FROM drugs WHERE demographic_no=${patient} AND customName=${h.sqlString(`${marker}-ACUTE`)}`);
    h.assert(/^[1-9]\d*$/.test(id), 'Owned acute medication was not found');
    // This is the visible label for the application's hidden checkbox.
    const dialogs = await h.withExpectedDialogs(rx,
      () => profile.locator(`label[id="drugMaintenanceSwitchLbl_${id}"]`).first().click());
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Long-term change did not ask for confirmation');
    await rx.waitForLoadState('networkidle');
    await ready(['Long Term Meds','Acute','Inactive','External']);
    h.assert(sql.value(`SELECT long_term FROM drugs WHERE drugid=${id}`) === '1', 'Long-term change was not saved');
    h.assert((await profile.locator('table[id="Drug_tableLong Term Meds"]').innerText()).includes(`${marker}-ACUTE`),
      'Refresh did not move the changed medication into Long Term Meds');
    h.assert((await combinedLink().getAttribute('class') || '').includes('selected'), 'Refresh changed the selected legend');
  });

  await s.step('a newer selection replaces a delayed older view without leftover sections', async () => {
    const routing = '**/rx/ViewListDrugs?**';
    let release;
    const held = new Promise(resolve => { release = resolve; });
    let started = false;
    const seen = [];
    await rx.route(routing, async route => {
      const url = new URL(route.request().url());
      const heading = url.searchParams.get('heading');
      seen.push(heading || url.searchParams.get('status'));
      if (heading === 'Long Term Meds') {
        const response = await route.fetch();
        started = true;
        await held;
        await route.fulfill({response});
      } else await route.continue();
    });
    try {
      await combinedLink().click();
      await waitUntil(() => started, 'delayed profile request');
      await rx.locator('a[onclick="selectDrugProfile(\'inactive\', this);"]').click();
      h.assert(await profile.isHidden(), 'An incomplete profile became visible');
      release();
      await rx.waitForLoadState('networkidle');
      await ready([]);
      h.assert(JSON.stringify(seen) === JSON.stringify(['Long Term Meds','inactive']), 'The superseded view still requested additions');
      const text = await profile.innerText();
      h.assert(text.includes(`${marker}-INACTIVE`) && !text.includes(`${marker}-LT`), 'The final selection contains stale medications');
    } finally {
      release();
      await rx.unroute(routing);
    }
  });

  await s.step('a failed section clears the partial list and selecting the view again retries it', async () => {
    const routing = '**/rx/ViewListDrugs?**';
    const mark = failureMark(s.recorder);
    await rx.route(routing, route => new URL(route.request().url()).searchParams.get('heading') === 'Acute'
      ? route.fulfill({status:503, contentType:'text/plain', body:'Controlled profile failure'}) : route.continue());
    try {
      await combinedLink().click();
      await rx.waitForLoadState('networkidle');
      await ready([]);
      const text = await profile.innerText();
      h.assert(text.includes('could not be loaded completely') && !text.includes(marker),
        'A failed profile presented a partial medication list');
      consumeExpectedFailure(s.recorder, mark, {status:503, method:'GET', path:/\/rx\/ViewListDrugs$/});
    } finally {
      await rx.unroute(routing);
    }
    await combinedLink().click();
    await ready(['Long Term Meds','Acute','Inactive','External']);
    h.assert(!(await profile.innerText()).includes('could not be loaded completely'), 'Retry did not clear the error');
  });
}

if (require.main === module) runWorkflow('rx-profile-legend', workflow);
module.exports = {workflow};
