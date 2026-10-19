#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Reprint audit authorization (#3585), against a disposable CARLOS deployment.
 * Uses the standard BASE_URL, TEST_USER/TEST_PASSWORD/TEST_PIN and MYSQL_* contract.
 * A browser signs in, opens Rx and reprints a saved synthetic script. Database assertions
 * verify dates_reprinted and REPRINT logs. Both reprint routes refuse GET/HEAD, tokenless
 * POSTs, read-only logins and patient-specific write restrictions without audit changes.
 * Fixtures: one owned patient, prescription/drug and two throwaway roles/logins, removed
 * and verified at teardown. Run exclusively, without concurrent fixture-writing checks.
 */
'use strict';
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { authzReadFixture, WRITE_RESTRICTED_PRIVILEGES } = require('./lib/authz-read-fixture');
const { signIn, urlFor } = require('./lib/authz-read-probe');
const q = h.sqlString;

async function csrfToken(context, config) {
  const response = await context.request.get(urlFor(config, 'csrfguard'));
  h.assert(response.status() === 200, 'CSRF bootstrap was not served');
  const token = /masterTokenValue\s*=\s*["']([^"']+)["']/.exec(await response.text());
  h.assert(token, 'CSRF bootstrap did not contain the master token');
  return token[1];
}

async function workflow(s) {
  const { sql, patient, provider, marker, config } = s;
  const fixture = authzReadFixture({ sql, marker, provider, testUser: config.testUser });
  s.cleanup(() => fixture.cleanup());
  s.cleanup(() => {
    sql.execute(`DELETE FROM drugs WHERE demographic_no=${patient}; DELETE FROM prescription WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM prescription WHERE demographic_no=${patient})`) === '0', 'Owned prescription rows remain');
  });
  const script = sql.value(`INSERT INTO prescription(provider_no,demographic_no,date_prescribed,date_printed,textView,lastUpdateDate)
    VALUES(${q(provider)},${patient},CURDATE(),CURDATE(),${q(marker)},NOW()); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(script), 'Synthetic prescription was not created');
  sql.execute(`INSERT INTO drugs(provider_no,demographic_no,rx_date,end_date,written_date,BN,GCN_SEQNO,customName,
    dosage,unit,takemin,takemax,freqcode,duration,durunit,quantity,\`repeat\`,special,archived,script_no,position,
    dispenseInternal,create_date,lastUpdateDate)
    VALUES(${q(provider)},${patient},CURDATE(),DATE_ADD(CURDATE(),INTERVAL 30 DAY),CURDATE(),${q(marker)},0,${q(marker)},
    '10','mg',1,1,'OD','30','D','30',0,'Synthetic instructions',0,${script},0,0,NOW(),NOW())`);
  const snapshot = () => JSON.stringify(sql.rows(`SELECT COALESCE(dates_reprinted,''),
    (SELECT COUNT(*) FROM log WHERE demographic_no=${patient} AND action='reprint'
      AND content='prescription' AND contentId=${q(script)})
    FROM prescription WHERE script_no=${script} AND demographic_no=${patient}`));
  const stamps = () => sql.value(`SELECT COALESCE(dates_reprinted,'') FROM prescription WHERE script_no=${script}`)
    .split(',').filter(Boolean);
  const auditQuery = `SELECT COUNT(*) FROM log WHERE demographic_no=${patient}
    AND action='reprint' AND content='prescription' AND contentId=${q(script)}`;
  const auditCount = () => Number(sql.value(auditQuery));
  const endpoint = (method) => method === 'reprint' ? 'rx/rePrescribe' : 'rx/rePrescribe2';
  const fields = (method) => ({ method, scriptNo: script, drugList: script, demographicNo: patient });
  const send = async (context, method, verb, token) => {
    const url = new URL(urlFor(config, endpoint(method)));
    const params = fields(method);
    if (verb !== 'POST') Object.entries(params).forEach(([key, value]) => url.searchParams.set(key, value));
    return context.request.fetch(url.href, {
      method: verb, maxRedirects: 0, failOnStatusCode: false,
      ...(verb === 'POST' ? { form: { ...params, ...(token ? { 'CSRF-TOKEN': token } : {}) } } : {}),
    });
  };
  const open = async (context) => {
    const page = await context.newPage();
    await h.gotoApp(page, config.baseUrl, `/rx/choosePatient?demographicNo=${patient}`);
    await page.locator('#searchString').waitFor({ state: 'visible', timeout: 30000 });
    return page;
  };

  await s.step('GET and HEAD reject both reprint dispatches without changing persisted audit history', async () => {
    const before = snapshot();
    for (const method of ['reprint', 'reprint2']) {
      for (const verb of ['GET', 'HEAD']) {
        const response = await send(s.context, method, verb);
        h.assert(response.status() === 405 && response.headers().allow === 'POST', `${verb} ${method} did not answer 405/Allow POST`);
        h.assert(snapshot() === before, `${verb} ${method} changed persisted audit history`);
      }
    }
  });

  await s.step('tokenless POSTs cannot record reprints', async () => {
    const before = snapshot();
    for (const method of ['reprint', 'reprint2']) {
      const response = await send(s.context, method, 'POST');
      h.assert(response.status() === 403, `Tokenless ${method} did not answer 403`);
      h.assert(snapshot() === before, `Tokenless ${method} changed persisted audit history`);
    }
  });

  await s.step('read-only and patient-restricted writers cannot record either reprint', async () => {
    for (const patientRestricted of [false, true]) {
      const role = fixture.addRole({ ...WRITE_RESTRICTED_PRIVILEGES, _rx: patientRestricted ? 'w' : 'r' });
      if (patientRestricted) {
        // Global write remains granted; the named patient's override grants read only.
        // fixture.cleanup removes all privileges owned by this throwaway role.
        sql.execute(`INSERT INTO secObjPrivilege(roleUserGroup,objectName,privilege,priority,provider_no)
          VALUES(${q(role)},${q(`_rx$${patient}`)},'r',0,${q(provider)})`);
      }
      const login = fixture.addLogin(role);
      fixture.joinTestLoginPrograms(login);
      const restricted = await signIn(s, login);
      try {
        await open(restricted.context);
        const token = await csrfToken(restricted.context, config);
        const before = snapshot();
        for (const method of ['reprint', 'reprint2']) {
          const response = await send(restricted.context, method, 'POST', token);
          h.assert(response.status() === 403 && Object.hasOwn(response.headers(), h.APPLICATION_HEADER),
            `${patientRestricted ? 'Patient-restricted' : 'Read-only'} ${method} was not refused by CARLOS`);
          h.assert(snapshot() === before, `Unauthorized ${method} changed persisted audit history`);
        }
      } finally { await restricted.context.close(); }
    }
  });

  await s.step('the browser reprint workflow records one audit stamp and displays the saved script', async () => {
    const page = await open(s.context);
    const before = stamps().length;
    const beforeAudit = auditCount();
    await page.locator('a[onclick*="getElementById(\'reprint\')"]').click();
    const printed = page.waitForResponse(response => response.request().method() === 'POST'
      && new URL(response.url()).pathname.endsWith('/rx/rePrescribe2'));
    const popup = s.context.waitForEvent('page');
    await page.locator(`a[onclick="reprint2('${script}')"]`).first().click();
    const response = await printed;
    h.assert(response.status() === 200, `Browser reprint answered ${response.status()}`);
    const view = await popup;
    await view.waitForLoadState('domcontentloaded');
    h.assert((await view.locator('body').innerText()).includes(marker), 'Reprint did not display the owned script');
    h.assert(stamps().length === before + 1 && stamps().at(-1).endsWith(`;${provider}`),
      'Browser reprint did not record exactly one stamp for the provider');
    await expectValue(sql, auditQuery, String(beforeAudit + 1), 'Browser reprint did not record exactly one REPRINT log');
    await view.close();
    await page.close();
  });

  await s.step('the legacy POST reprint records one stamp with write access', async () => {
    const before = stamps().length;
    const beforeAudit = auditCount();
    const token = await csrfToken(s.context, config);
    const response = await send(s.context, 'reprint', 'POST', token);
    h.assert(response.status() === 200 && (await response.text()).includes(marker), 'Legacy reprint did not render the owned script');
    h.assert(stamps().length === before + 1 && stamps().at(-1).endsWith(`;${provider}`),
      'Legacy reprint did not record exactly one stamp for the provider');
    await expectValue(sql, auditQuery, String(beforeAudit + 1), 'Legacy reprint did not record exactly one REPRINT log');
  });
}

if (require.main === module) runWorkflow('rx-reprint-authorization', workflow, { openMaster: false });
module.exports = { workflow };
