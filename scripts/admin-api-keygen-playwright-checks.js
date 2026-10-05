#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * REST client and lab key administration (coverage plan admin-api-keygen).
 * User path: Schedule ▸ Administration ▸ Integration ▸ REST Clients (admin/ViewApiClients
 * in #myFrame, driving admin/api/clientManage) ▸ Add New ▸ Add Client, row delete icon;
 * System Management ▸ Key Pair Generator (admin/ViewKeygenKeyManager) ▸ Service Name
 * (admin/keygen/getPublicKey) ▸ Update Matching Professional Specialist / Create New Key.
 * Asserted: a client round-trips to ServiceClient with the listed key, URI and TTL and a
 * distinct secret that no response or page echoes; a duplicate name is refused by alert;
 * a GET delete is refused (405); the delete icon removes exactly the owned row. The key
 * manager returns the owned service's private key (no-store JSON) as base64 PKCS#8 that
 * pairs with the stored public key, and audits the read. Last (fails today): its two
 * buttons must reach routes inside the application context and save/open.
 * Fixtures: the client is created through the UI; the publicKeys row is seeded with a
 * fresh RSA-2048 pair (Create New Key has no working entry). Cleanup deletes only the
 * marker rows (ServiceClient, publicKeys, their audit rows) and asserts they are gone.
 */
const crypto = require('node:crypto');
const h = require('./lib/playwright-harness');
const {clickOpensPopupOrNavigates} = require('./lib/playwright-ui');
const {revealAuditLink} = require('./lib/playwright-link-audit');
const {runWorkflow, expectValue} = require('./lib/workflow-session');

const TIMEOUT = 20000;

const isClientManage = method => response => {
  const url = new URL(response.url());
  if (!url.pathname.endsWith('/admin/api/clientManage')) return false;
  const body = response.request().postData() || '';
  return (url.searchParams.get('method') || new URLSearchParams(body).get('method')) === method;
};

async function workflow(s) {
  const {sql, marker, provider} = s;
  const contextPath = new URL(s.config.baseUrl).pathname.replace(/\/+$/, '');
  const clientName = `${marker}-client O'Neil "A&B"`;
  const clientUri = `https://localhost/${marker}/callback`;
  const clientHigh = Number(sql.value('SELECT COALESCE(MAX(id),0) FROM ServiceClient'));
  const ownedClient = `id>${clientHigh} AND name LIKE ${h.sqlString(marker + '%')}`;
  const service = marker;
  const keyAudit = `action='read' AND content='PublicKey' AND contentId=${h.sqlString(service)}`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM ServiceClient WHERE ${ownedClient}; DELETE FROM publicKeys WHERE service=${h.sqlString(service)};
      DELETE FROM log WHERE ${keyAudit}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM ServiceClient WHERE ${ownedClient})
      + (SELECT COUNT(*) FROM publicKeys WHERE service=${h.sqlString(service)}) + (SELECT COUNT(*) FROM log WHERE ${keyAudit})`) === '0',
    'Owned REST client, key or audit rows were not removed');
  });

  let admin;
  async function openSection(linkName, route) {
    if (!admin || admin.isClosed()) {
      ({page: admin} = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
        {context: s.context, recorder: s.recorder, label: 'api-keygen-administration', timeout: TIMEOUT}));
      // The .xlink handlers bind on document ready; a click before then is lost.
      await admin.waitForLoadState('load', {timeout: TIMEOUT});
    }
    const link = admin.getByRole('link', {name: linkName, exact: true, includeHidden: true});
    await revealAuditLink(admin, link, TIMEOUT);
    await link.click();
    const iframe = admin.locator(`#dynamic-content iframe#myFrame[src*="${route}"]`);
    await iframe.waitFor({timeout: TIMEOUT});
    const frame = await (await iframe.elementHandle()).contentFrame();
    h.assert(frame, `${linkName} did not load in the administration frame`);
    await frame.waitForLoadState('domcontentloaded', {timeout: TIMEOUT});
    return frame;
  }
  const idle = frame => frame.waitForFunction(() => !window.jQuery || window.jQuery.active === 0, null, {timeout: TIMEOUT});

  // ---- REST Clients --------------------------------------------------------
  let clients;
  let clientId;
  let secret;
  const clientRow = () => clients.locator('#clientTable tbody tr', {hasText: marker});
  const responseBodies = [];
  async function addClient(name) {
    await clients.getByRole('button', {name: 'Add New', exact: true}).click();
    await clients.locator('#new-form').waitFor({state: 'visible', timeout: TIMEOUT});
    await clients.locator('#clientName').fill(name);
    await clients.locator('#clientURI').fill(clientUri);
    await clients.locator('#lifetime').fill('3600');
    const [response] = await Promise.all([
      admin.waitForResponse(isClientManage('add'), {timeout: TIMEOUT}),
      clients.locator('.ui-dialog:has(#new-form)').getByRole('button', {name: 'Add Client', exact: true}).click(),
    ]);
    h.assert(response.status() === 200 && response.request().method() === 'POST', 'Add Client did not POST successfully');
    const body = await response.text();
    responseBodies.push(body);
    return JSON.parse(body);
  }
  await s.step('Add Client stores the client and lists its key, URI and TTL', async () => {
    clients = await openSection('REST Clients', '/admin/ViewApiClients');
    await clients.locator('#clientTable').waitFor({timeout: TIMEOUT});
    await idle(clients);
    h.assert(await clientRow().count() === 0, 'An owned client was listed before it was created');
    const listed = admin.waitForResponse(isClientManage('list'), {timeout: TIMEOUT});
    const result = await addClient(clientName);
    h.assert(result.success === true && result.error === '', 'Add Client did not report success');
    responseBodies.push(await (await listed).text());
    await clientRow().waitFor({timeout: TIMEOUT});
    h.assert(sql.value(`SELECT COUNT(*) FROM ServiceClient WHERE ${ownedClient}`) === '1', 'Exactly one owned client was not stored');
    const [id, name, key, storedSecret, uri, lifetime] = sql.rows(`SELECT id,name,clientKey,clientSecret,uri,lifetime
      FROM ServiceClient WHERE ${ownedClient}`)[0];
    clientId = id;
    secret = storedSecret;
    h.assert(name === clientName && uri === clientUri && lifetime === '3600', 'The stored client does not match the form');
    h.assert(/^[0-9a-z]{16}$/.test(key) && /^[0-9a-z]{16}$/.test(secret) && key !== secret,
      'The client key and secret are not distinct generated values');
    const cells = (await clientRow().locator('td').allInnerTexts()).map(text => text.trim());
    h.assert(cells[0] === clientName && cells[1] === key && cells[2] === clientUri && cells[3] === '3600',
      'The client list does not show the stored name, key, URI and TTL');
  });
  await s.step('the client secret is not echoed by the add or list responses or the page', async () => {
    h.assert(responseBodies.length === 2 && responseBodies.every(body => !body.includes(secret)),
      'A clientManage response echoed the client secret');
    h.assert(!(await clients.content()).includes(secret), 'The REST Clients page shows the client secret');
  });
  await s.step('a duplicate client name is refused by alert and stores nothing', async () => {
    let result;
    const dialogs = await h.withExpectedDialogs(admin, async () => {
      result = await addClient(clientName);
      await idle(clients);
    });
    h.assert(result.success === false && dialogs.length === 1 && dialogs[0].text.includes('Name already being used'),
      'The duplicate name was not refused with its alert');
    h.assert(responseBodies.every(body => !body.includes(secret)), 'The refused add echoed the client secret');
    h.assert(sql.value(`SELECT COUNT(*) FROM ServiceClient WHERE ${ownedClient}`) === '1', 'The duplicate name stored a second client');
    h.assert(await clientRow().count() === 1, 'The list shows the owned client other than once');
  });
  await s.step('a GET delete is refused with 405 and leaves the client', async () => {
    // Negative probe only, after the positive path ran through the UI.
    const response = await s.context.request.get(`${s.config.baseUrl}/admin/api/clientManage?method=delete&id=${clientId}`,
      {maxRedirects: 0, failOnStatusCode: false});
    h.assert(response.status() === 405, `A GET delete answered HTTP ${response.status()}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM ServiceClient WHERE id=${clientId}`) === '1', 'A GET delete removed the client');
  });
  await s.step('the delete icon removes exactly the owned client', async () => {
    const before = sql.value(`SELECT COUNT(*) FROM ServiceClient WHERE id<>${clientId}`);
    const [response] = await Promise.all([
      admin.waitForResponse(isClientManage('delete'), {timeout: TIMEOUT}),
      clientRow().getByRole('img', {name: 'Delete'}).click(),
    ]);
    h.assert(response.status() === 200 && (await response.json()).success === true, 'Delete did not report success');
    await clientRow().waitFor({state: 'detached', timeout: TIMEOUT});
    h.assert(sql.value(`SELECT COUNT(*) FROM ServiceClient WHERE id=${clientId}`) === '0', 'The deleted client is still stored');
    h.assert(sql.value(`SELECT COUNT(*) FROM ServiceClient WHERE id<>${clientId}`) === before, 'Delete removed other clients');
  });

  // ---- Key Pair Generator --------------------------------------------------
  // Seeded the way KeyPairGen stores a pair (unchunked base64 X.509 / PKCS#8 DER):
  // the Create New Key button does not reach its route (asserted last).
  const pair = crypto.generateKeyPairSync('rsa', {modulusLength: 2048,
    publicKeyEncoding: {type: 'spki', format: 'der'}, privateKeyEncoding: {type: 'pkcs8', format: 'der'}});
  const publicB64 = pair.publicKey.toString('base64');
  const privateB64 = pair.privateKey.toString('base64');
  sql.execute(`INSERT INTO publicKeys(service,type,pubKey,privateKey) VALUES(${h.sqlString(service)},'FAKEPW',
    ${h.sqlString(publicB64)},${h.sqlString(privateB64)})`);
  h.assert(sql.value(`SELECT COUNT(*) FROM publicKeys WHERE service=${h.sqlString(service)}`) === '1', 'The key fixture was not created');
  let keys;
  await s.step('choosing the owned service shows its private key as base64 PKCS#8 matching its public key', async () => {
    const fetched = [];
    const listener = response => {
      if (new URL(response.url()).pathname.endsWith('/admin/keygen/getPublicKey')) fetched.push(response);
    };
    s.context.on('response', listener);
    try {
      keys = await openSection('Key Pair Generator', '/admin/ViewKeygenKeyManager');
      await keys.locator('#selectKeyList').waitFor({timeout: TIMEOUT});
      await idle(keys);
      h.assert((await keys.locator('.oscarBlueForeground + div').first().innerText()).trim()
        .endsWith(`${contextPath}/lab/newLabUpload`), 'The key manager does not show the lab upload URL of this application');
      if (await keys.locator('#selectKeyList').inputValue() !== service) {
        await Promise.all([
          admin.waitForResponse(response => new URL(response.url()).pathname.endsWith('/admin/keygen/getPublicKey')
            && new URL(response.url()).searchParams.get('id') === service, {timeout: TIMEOUT}),
          keys.locator('#selectKeyList').selectOption(service),
        ]);
      }
      await idle(keys);
    } finally { s.context.off('response', listener); }
    const response = fetched.find(item => new URL(item.url()).searchParams.get('id') === service);
    h.assert(response && response.status() === 200 && response.request().method() === 'GET', 'The owned key was not fetched');
    const headers = response.headers();
    h.assert(/no-store/.test(headers['cache-control'] || '') && /^application\/json/.test(headers['content-type'] || ''),
      'The key response is not uncacheable JSON');
    const json = await response.json();
    h.assert(json.success === true && json.service === service && json.type === 'FAKEPW', 'The key response is not the owned service');
    const shown = (await keys.locator('#privateKey').innerText()).trim();
    h.assert(shown === privateB64 && json.base64EncodedPrivateKey === privateB64, 'The page does not show the stored private key bytes');
    h.assert(/^[A-Za-z0-9+/]+={0,2}$/.test(shown) && Buffer.from(shown, 'base64').toString('base64') === shown,
      'The private key is not canonical base64');
    const pem = `-----BEGIN PRIVATE KEY-----\n${shown.match(/.{1,64}/g).join('\n')}\n-----END PRIVATE KEY-----\n`;
    const privateKey = crypto.createPrivateKey(pem);
    h.assert(privateKey.asymmetricKeyType === 'rsa' && privateKey.asymmetricKeyDetails.modulusLength === 2048,
      'The private key is not an RSA-2048 PKCS#8 key');
    h.assert(crypto.createPublicKey(privateKey).export({type: 'spki', format: 'der'}).equals(pair.publicKey),
      'The private key does not pair with the stored public key');
    await expectValue(sql, `SELECT COUNT(*)>0 FROM log WHERE ${keyAudit} AND provider_no=${h.sqlString(provider)}`, '1',
      'Reading the private key was not audited');
  });
  await s.step('the key manager buttons save the matching specialist and open Create New Key in the application', async () => {
    const problems = [];
    const option = keys.locator('#selectProfessionalSpecialistList option:not([value=""])').first();
    h.assert(await option.count() === 1, 'No professional specialist is offered to match');
    const specialistId = await option.getAttribute('value');
    await keys.locator('#selectProfessionalSpecialistList').selectOption(specialistId);
    const matching = () => sql.value(`SELECT COALESCE(matchingProfessionalSpecialistId,'') FROM publicKeys WHERE service=${h.sqlString(service)}`);
    const before = matching();
    const route = h.appUrl(s.config.baseUrl, '/admin/ViewKeygenUpdateMatchingProfessionalSpecialist');
    for (const method of ['GET', 'HEAD']) {
      const response = await s.context.request.fetch(route, {method,
        params: {serviceName: service, professionalSpecialistId: specialistId}, maxRedirects: 0});
      h.assert(response.status() === 405 && response.headers().allow === 'POST', `${method} did not refuse specialist mutation`);
      await response.dispose();
      h.assert(matching() === before, `${method} changed the specialist`);
    }
    const refused = await s.context.request.post(route,
      {form: {serviceName: service, professionalSpecialistId: specialistId}, maxRedirects: 0});
    h.assert(refused.status() === 403 && matching() === before, 'An update without CSRF was not refused without changes');
    await refused.dispose();
    let update;
    const dialogs = await h.withExpectedDialogs(admin, async () => {
      [update] = await Promise.all([
        admin.waitForResponse(response => response.request().method() === 'POST'
          && new URL(response.url()).pathname.endsWith('/ViewKeygenUpdateMatchingProfessionalSpecialist'), {timeout: TIMEOUT}),
        keys.getByRole('button', {name: 'Update Matching Professional Specialist', exact: true}).click(),
      ]);
      await idle(keys);
    });
    const updatePath = new URL(update.url()).pathname;
    // The exact route, as for Create New Key below: on a web-root deployment contextPath is ''
    // and a prefix test would accept any path.
    if (updatePath !== `${contextPath}/admin/ViewKeygenUpdateMatchingProfessionalSpecialist` || update.status() !== 200) {
      problems.push(`Update Matching Professional Specialist posted to ${updatePath} (HTTP ${update.status()}) `
        + `instead of ${contextPath}/admin/ViewKeygenUpdateMatchingProfessionalSpecialist`);
    }
    if (!dialogs.some(dialog => dialog.text === 'Changes saved.')) problems.push('no "Changes saved." confirmation');
    if (sql.value(`SELECT COALESCE(matchingProfessionalSpecialistId,'') FROM publicKeys WHERE service=${h.sqlString(service)}`) !== specialistId) {
      problems.push('the matching specialist was not stored');
    }
    const navigated = admin.waitForEvent('framenavigated', {predicate: frame => frame === keys, timeout: TIMEOUT});
    await keys.getByRole('button', {name: 'Create New Key', exact: true}).click();
    await navigated;
    await keys.waitForLoadState('domcontentloaded', {timeout: TIMEOUT});
    const createPath = new URL(keys.url()).pathname;
    if (createPath !== `${contextPath}/admin/ViewKeygenCreateKey` || await keys.title() !== 'CARLOS - Key Pair Creation') {
      problems.push(`Create New Key opened ${createPath} instead of the Key Pair Creation page`);
    }
    h.assert(!problems.length, `Key manager buttons do not reach their routes: ${problems.join('; ')}`);
  });
}

if (require.main === module) runWorkflow('admin-api-keygen', workflow, {openPatient: false});
module.exports = {workflow};
