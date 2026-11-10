#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Key Pair Creation on an install without the CARLOS key pair (issue #6009).
 * User path: Administration ▸ System Management ▸ Key Pair Generator ▸ Create New Key, which opens
 * admin/ViewKeygenCreateKey. admin-api-keygen asserts that the button reaches that route; this check
 * opens it directly, so it never triggers the Key Manager's read of another service's private key.
 * Asserted, on an install whose CARLOS ("oscar") key pair has not been created yet:
 *  1. creating a client key is refused with a message that says to create the CARLOS key pair
 *     first, and stores no publicKeys row, so the service name stays free;
 *  2. the page creates the CARLOS key pair under the service name 'oscar' (an RSA-2048 pair);
 *  3. the same client name then succeeds: the page downloads keyPair.key, whose client private key
 *     is the stored one and pairs with the stored public key, and whose CARLOS public key is the
 *     stored one;
 *  4. a further attempt with that name is refused as a duplicate and changes nothing.
 * Before the fix, step 1 stored the client key and step 3 was refused as a duplicate.
 * Fixtures: SKIPs when the CARLOS key pair already exists; an existing one is never changed (as in
 * lab-upload-signed-feed). Cleanup removes the marker service's publicKeys row and the CARLOS key
 * pair this run created, and asserts both are gone. No key material is printed or kept: the
 * downloaded file is deleted once read. SCREENSHOT_DIR, when set, receives one screenshot per state.
 */
const crypto = require('node:crypto');
const fs = require('node:fs');
const h = require('./lib/playwright-harness');
const {runWorkflow} = require('./lib/workflow-session');

const TIMEOUT = 20000;
const ROUTE = '/admin/ViewKeygenCreateKey';
const CARLOS_KEY = `SELECT COUNT(*) FROM oscarKeys WHERE name='oscar'`;

async function preflight({sql}) {
  if (sql.value(CARLOS_KEY) !== '0') {
    throw new h.SkipCheck('The CARLOS key pair (oscarKeys name oscar) already exists; this check needs an install '
      + 'where it has not been created yet, and never changes an existing one');
  }
}

// keyPair.key, section by section in the order createKey.jsp writes them. "Oscar Public Key" is the
// file's own title for the CARLOS public key: lab upload clients read it, so the page keeps it.
const KEY_FILE = /^-+ Service Name -+\r?\n([\s\S]*?)\r?\n-{30}\r?\n-+ Client Private Key -+\r?\n([\s\S]*?)\r?\n-{30}\r?\n-+ Oscar Public Key -+\r?\n([\s\S]*?)\r?\n-{30}\s*$/;

/** The three sections of the keyPair.key download, with the line breaks of the base64 removed. */
function parseKeyFile(text) {
  const match = KEY_FILE.exec(text);
  const section = index => (match ? match[index].replace(/\s+/g, '') : null);
  return {service: section(1), clientPrivate: section(2), carlosPublic: section(3)};
}

const der = base64 => Buffer.from(base64, 'base64');

async function workflow(s) {
  const {sql, marker, config} = s;
  // createKey.jsp accepts letters, digits, '.', '_' and '-'; the marker is FAKE-PW<hex>.
  const service = `${marker}.lab`;
  const owned = `service=${h.sqlString(service)}`;
  const contextPath = new URL(config.baseUrl).pathname.replace(/\/+$/, '');
  // Set before the page is asked to create the CARLOS key pair, and its public key recorded as
  // soon as it is stored. The preflight found none and the suite runs one check at a time, so a
  // CARLOS key pair present at cleanup after that request is this run's.
  let carlosKeyRequested = false;
  let createdCarlosKey = null;
  s.cleanup(() => {
    sql.execute(`DELETE FROM publicKeys WHERE ${owned}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM publicKeys WHERE ${owned}`) === '0', 'The owned client key row was not removed');
    if (!carlosKeyRequested || sql.value(CARLOS_KEY) === '0') return;
    const stored = sql.value(`SELECT COALESCE(pubKey,'') FROM oscarKeys WHERE name='oscar'`);
    h.assert(createdCarlosKey === null || stored === createdCarlosKey,
      'The CARLOS key pair was replaced during the run; it is left in place');
    sql.execute(`DELETE FROM oscarKeys WHERE name='oscar' AND COALESCE(pubKey,'')=${h.sqlString(stored)}`);
    h.assert(sql.value(CARLOS_KEY) === '0', 'The CARLOS key pair this run created was not removed');
  });

  const page = await s.context.newPage();
  const shot = name => h.screenshot(page, config.screenshotDir, `keygen-6009-${name}`);
  const message = async () => (await page.locator('#fail').innerText()).trim();
  const clientRows = () => sql.value(`SELECT COUNT(*) FROM publicKeys WHERE ${owned}`);

  async function fillForm(name) {
    await page.locator('#name').fill(name);
    await page.locator('#selection').selectOption('CML');
  }
  const createButton = () => page.getByRole('button', {name: 'Create Key Pair', exact: true});
  /** Submits a request the page answers with itself (a refusal or the CARLOS key confirmation). */
  async function submitForPage(name) {
    await fillForm(name);
    const [response] = await Promise.all([
      page.waitForResponse(item => item.request().method() === 'POST'
        && new URL(item.url()).pathname === `${contextPath}${ROUTE}`, {timeout: TIMEOUT}),
      createButton().click(),
    ]);
    // 'load', not just the document: the CSRF client that tokens the next submission is a script.
    await page.waitForLoadState('load', {timeout: TIMEOUT});
    h.assert(response.status() === 200, `Creating '${name}' answered HTTP ${response.status()}`);
    await h.assertNotErrorPage(page, `Key Pair Creation for '${name}'`);
    return message();
  }

  await s.step('Key Pair Creation opens on an install without the CARLOS key pair', async () => {
    await h.gotoApp(page, config.baseUrl, ROUTE, 'load');
    h.assert(await page.title() === 'CARLOS - Key Pair Creation', 'The Create New Key route did not open Key Pair Creation');
    h.assert(sql.value(CARLOS_KEY) === '0' && clientRows() === '0', 'The run did not start without the CARLOS key or with its service name free');
    await shot('0-empty');
  });

  await s.step('creating a client key is refused until the CARLOS key pair exists and stores nothing', async () => {
    const shown = await submitForPage(service);
    await shot('1-missing-carlos-key');
    // The stored row first: it is issue #6009 itself, whatever the page says.
    h.assert(clientRows() === '0', 'The refused attempt stored the client key, so its service name can no longer be used');
    h.assert(sql.value(CARLOS_KEY) === '0', 'The refused attempt created the CARLOS key pair');
    h.assert(shown.includes('The CARLOS key pair has not been created yet')
      && shown.includes("service name 'oscar'") && shown.includes(service),
    `The refusal does not say to create the CARLOS key pair first: "${shown}"`);
  });

  await s.step("the page creates the CARLOS key pair under the service name 'oscar'", async () => {
    carlosKeyRequested = true;
    const shown = await submitForPage('oscar');
    createdCarlosKey = sql.value(`SELECT COALESCE(pubKey,'') FROM oscarKeys WHERE name='oscar'`) || null;
    await shot('2-carlos-key-created');
    h.assert(shown === 'CARLOS key pair created successfully', `Creating the CARLOS key pair showed "${shown}"`);
    h.assert(createdCarlosKey, 'The CARLOS key pair was not stored');
    const publicKey = crypto.createPublicKey({key: der(createdCarlosKey), format: 'der', type: 'spki'});
    h.assert(publicKey.asymmetricKeyType === 'rsa' && publicKey.asymmetricKeyDetails.modulusLength >= 2048,
      'The CARLOS public key is not an RSA key of at least 2048 bits');
  });

  await s.step('the same client name then downloads its key pair with the CARLOS public key', async () => {
    await fillForm(service);
    const downloaded = page.waitForEvent('download', {timeout: TIMEOUT}).catch(() => null);
    await createButton().click();
    const download = await downloaded;
    await shot('3-retry-downloaded');
    if (!download) {
      await page.waitForLoadState('load', {timeout: TIMEOUT});
      h.assert(false, `Retrying the service name did not download its key pair: "${await message()}"`);
    }
    let text;
    try {
      h.assert(download.suggestedFilename() === 'keyPair.key', 'The download is not keyPair.key');
      text = fs.readFileSync(await download.path(), 'utf8');
    } finally {
      await download.delete();
    }
    const file = parseKeyFile(text);
    h.assert(file.service === service && file.clientPrivate && file.carlosPublic,
      'keyPair.key does not carry the service name, the client private key and the CARLOS public key');
    h.assert(clientRows() === '1', 'The retry did not store exactly one client key');
    const [type, storedPublic, storedPrivate] = sql.rows(`SELECT type,pubKey,privateKey FROM publicKeys WHERE ${owned}`)[0];
    h.assert(type === 'CML', 'The client key was not stored with the chosen lab type');
    h.assert(file.clientPrivate === storedPrivate.replace(/\s+/g, ''), 'The downloaded client private key is not the stored one');
    const privateKey = crypto.createPrivateKey({key: der(file.clientPrivate), format: 'der', type: 'pkcs8'});
    h.assert(crypto.createPublicKey(privateKey).export({type: 'spki', format: 'der'}).equals(der(storedPublic)),
      'The downloaded client private key does not pair with the stored client public key');
    h.assert(file.carlosPublic === createdCarlosKey.replace(/\s+/g, ''), 'The downloaded CARLOS public key is not the stored one');
  });

  await s.step('a further attempt with that name is refused as a duplicate and changes nothing', async () => {
    const before = sql.value(`SELECT privateKey FROM publicKeys WHERE ${owned}`);
    const shown = await submitForPage(service);
    await shot('4-duplicate-refused');
    h.assert(shown === `Failed: Key pair has already been created for the service '${service}'`,
      `The duplicate was not refused as already created: "${shown}"`);
    h.assert(clientRows() === '1' && sql.value(`SELECT privateKey FROM publicKeys WHERE ${owned}`) === before,
      'The refused duplicate changed the stored client key');
  });
}

if (require.main === module) runWorkflow('admin-keygen-create-key-retry', workflow, {openPatient: false, preflight});
module.exports = {workflow, preflight, parseKeyFile};
