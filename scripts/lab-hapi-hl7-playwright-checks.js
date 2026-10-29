#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Browser check for issue #5414: the HAPI HL7 v2 parser (upgraded from 1.0.1 to 2.6.0) must not
 * follow what a hostile HL7 v2 XML message points at, on a deployed CARLOS.
 *
 * HAPI HL7 v2 1.0.1 loaded external entities and external DTDs when it parsed HL7 v2 XML. The only
 * code that hands uploaded text to HAPI's XML parser is the PHS/STAR handler, reached from the HL7
 * Lab Upload popup with the lab type PHS. Each synthetic message below is HL7 v2 XML that declares a
 * DOCTYPE which points at a loopback HTTP listener this script starts, or that expands an internal
 * entity exponentially. For each one:
 *
 *   1. the HL7 Lab Upload popup does not report "Uploaded successfully";
 *   2. the listener received no request (the server went nowhere to fetch the entity or the DTD);
 *   3. the application still answers afterwards (an entity expansion did not tie it up).
 *
 * A control first requests the listener from this script, so a count of zero means something.
 *
 * Nothing leaves the machine: the listener binds the loopback interface and serves a fixed string.
 * The messages carry no patient data. The packaged server runs on the same host as this script
 * (host networking in scripts/deb-docker-validation.sh), so the loopback address is reachable from
 * it; set HOSTILE_OBSERVER_HOST to the address the server reaches this machine on when it runs
 * elsewhere. The uploads create no lab rows; the file-upload bookkeeping row and the archived copy
 * of each upload are removed in cleanup (LAB_UPLOAD_DOCUMENT_STORE, as in
 * lab-upload-playwright-checks.js).
 *
 * Environment: the common contract in lib/playwright-harness.js, plus
 *   HOSTILE_OBSERVER_HOST      address the SERVER can reach this script's listener on and the
 *                              listener binds (default 127.0.0.1)
 *   LAB_UPLOAD_DOCUMENT_STORE  the server's DOCUMENT_DIR, mounted or local; enables removal of this
 *                              run's archived files
 */

const crypto = require('node:crypto');
const fs = require('node:fs');
const http = require('node:http');
const os = require('node:os');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

const TIMEOUT = 90000;
const OBSERVER_HOST = process.env.HOSTILE_OBSERVER_HOST || '127.0.0.1';

/** A loopback listener that counts the requests it gets and answers a harmless DTD fragment. */
function startObserver() {
  const requests = [];
  const server = http.createServer((req, res) => {
    requests.push(req.url);
    res.writeHead(200, { 'content-type': 'text/plain' });
    res.end('<!ELEMENT any ANY>');
  });
  return new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(0, OBSERVER_HOST, () => {
      const { port } = server.address();
      resolve({
        requests,
        url: `http://${OBSERVER_HOST}:${port}`,
        close: () => new Promise((done) => server.close(() => done())),
      });
    });
  });
}

/** A minimal HL7 v2 XML ADT^A01 whose family name is `nameContent`, under the given DOCTYPE. */
function hostileXml(doctype, nameContent) {
  return [
    '<?xml version="1.0" encoding="UTF-8"?>',
    doctype,
    '<ADT_A01 xmlns="urn:hl7-org:v2xml">',
    '<MSH><MSH.1>|</MSH.1><MSH.2>^~\\&amp;</MSH.2>',
    '<MSH.3><HD.1>PW5414</HD.1></MSH.3><MSH.7><TS.1>20261029120000</TS.1></MSH.7>',
    '<MSH.9><CM_MSG.1>ADT</CM_MSG.1><CM_MSG.2>A01</CM_MSG.2></MSH.9>',
    '<MSH.10>PW5414</MSH.10><MSH.11><PT.1>P</PT.1></MSH.11><MSH.12>2.3</MSH.12></MSH>',
    '<EVN><EVN.1>A01</EVN.1></EVN>',
    `<PID><PID.1>1</PID.1><PID.5><XPN.1>${nameContent}</XPN.1><XPN.2>FAKE-PW5414</XPN.2></PID.5></PID>`,
    '<PV1><PV1.1>1</PV1.1></PV1>',
    '</ADT_A01>',
    '',
  ].join('\n');
}

/** The hostile messages, each keyed by what it asks the parser for. */
function hostileMessages(observerUrl) {
  let expansion = '<!DOCTYPE ADT_A01 [<!ENTITY lol0 "0123456789">';
  for (let level = 1; level <= 9; level += 1) {
    expansion += `<!ENTITY lol${level} "${`&lol${level - 1};`.repeat(10)}">`;
  }
  expansion += ']>';
  return {
    'external-entity': hostileXml(
      `<!DOCTYPE ADT_A01 [<!ENTITY xxe SYSTEM "${observerUrl}/pw5414-entity">]>`, '&xxe;'),
    'parameter-entity': hostileXml(
      `<!DOCTYPE ADT_A01 [<!ENTITY % remote SYSTEM "${observerUrl}/pw5414-parameter.dtd"> %remote;]>`, 'FAKE-PW5414'),
    'external-dtd': hostileXml(`<!DOCTYPE ADT_A01 SYSTEM "${observerUrl}/pw5414-external.dtd">`, 'FAKE-PW5414'),
    'entity-expansion': hostileXml(expansion, '&lol9;'),
  };
}

/** Open the HL7 Lab Upload popup from the Inbox hub and upload one file as the given lab type. */
async function uploadAs(s, type, filePath, fileName) {
  const inbox = await s.context.newPage();
  await h.gotoApp(inbox, s.config.baseUrl, '/web/inboxhub/Inboxhub');
  await inbox.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  const link = inbox.locator('a[href*="ViewInsideLabUpload"]').first();
  await link.waitFor({ state: 'attached', timeout: 20000 });
  const popup = await s.popup(inbox, link, 'hl7-hardening-upload');
  try {
    await popup.locator('#uploadForm').waitFor({ state: 'visible', timeout: 20000 });
    await popup.locator('#importFiles').setInputFiles(filePath);
    await popup.locator('#type').selectOption(type);
    const [response] = await Promise.all([
      popup.waitForResponse((r) => r.request().method() === 'POST'
        && new URL(r.url()).pathname.endsWith('/lab/CA/ALL/insideLabUpload'), { timeout: TIMEOUT }),
      popup.locator('#uploadForm button[type="submit"]').click(),
    ]);
    h.assert(response.status() < 500, `HL7 lab upload answered HTTP ${response.status()}`);
    await popup.waitForLoadState('domcontentloaded');
    const item = popup.locator('#file-list .file-item').filter({ hasText: fileName });
    await item.first().waitFor({ state: 'visible', timeout: 20000 });
    return (await item.first().locator('.upload-text').innerText()).trim();
  } finally {
    await popup.close().catch(() => {});
    await inbox.close().catch(() => {});
  }
}

/** Deletes this run's archived uploads when LAB_UPLOAD_DOCUMENT_STORE names the store. */
function removeArchivedUploads(stamp) {
  const store = process.env.LAB_UPLOAD_DOCUMENT_STORE;
  if (!store) {
    console.warn(`    archived uploads retained: set LAB_UPLOAD_DOCUMENT_STORE to remove LabUpload.lab-hapi-probe-${stamp}-*`);
    return;
  }
  const root = fs.realpathSync(store);
  const prefix = `LabUpload.lab-hapi-probe-${stamp}-`;
  const ownFiles = () => fs.readdirSync(root)
    .filter((name) => /^LabUpload\.lab-hapi-probe-[0-9A-F]{8}-[a-z-]+\.xml\.\d+$/.test(name) && name.startsWith(prefix));
  // name matched the fixed pattern above and is joined to the resolved store root.
  for (const name of ownFiles()) fs.unlinkSync(path.join(root, name)); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal
  h.assert(ownFiles().length === 0, 'The archived hostile uploads were not all removed');
}

async function workflow(s) {
  const { sql } = s;
  const stamp = crypto.randomBytes(4).toString('hex').toUpperCase();
  const ownUpload = `filename LIKE ${h.sqlString(`%lab-hapi-probe-${stamp}-%`)}`;
  const workDir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-lab-hapi-'));
  const observer = await startObserver();

  s.cleanup(async () => {
    await observer.close();
    fs.rmSync(workDir, { recursive: true, force: true });
    sql.execute(`DELETE FROM fileUploadCheck WHERE ${ownUpload}`);
    removeArchivedUploads(stamp);
    h.assert(sql.value(`SELECT COUNT(*) FROM fileUploadCheck WHERE ${ownUpload}`) === '0',
      'The hostile upload bookkeeping rows were not all removed');
  });

  await s.step('the listener sees a request made to it, so a count of zero means something', async () => {
    const reply = await fetch(`${observer.url}/pw5414-control`);
    h.assert(reply.status === 200, `the control request answered ${reply.status}`);
    h.assert(observer.requests.length === 1, 'the listener did not record the control request');
    observer.requests.length = 0;
  });

  const messages = hostileMessages(observer.url);
  for (const [kind, xml] of Object.entries(messages)) {
    await s.step(`PHS upload of an XML message with an ${kind} is refused and nothing is fetched`, async () => {
      const fileName = `lab-hapi-probe-${stamp}-${kind}.xml`;
      // fileName is built from a fixed prefix, random hex and a key of the table above.
      const filePath = path.join(workDir, fileName); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal
      fs.writeFileSync(filePath, xml, 'utf8');

      const started = Date.now();
      const status = await uploadAs(s, 'PHS', filePath, fileName);
      h.assert(status !== 'Uploaded successfully', `the ${kind} message was reported "${status}"`);
      h.assert(observer.requests.length === 0,
        `the server requested ${observer.requests.join(', ')} while reading the ${kind} message`);
      // An unguarded 10^9-copy expansion does not finish inside this bound.
      h.assert(Date.now() - started < 60000, `the ${kind} message tied the server up for ${Date.now() - started} ms`);
    });
  }

  await s.step('the application still answers after the hostile uploads', async () => {
    const page = await s.context.newPage();
    try {
      await h.gotoApp(page, s.config.baseUrl, '/web/inboxhub/Inboxhub');
      await page.locator('a[href*="ViewInsideLabUpload"]').first().waitFor({ state: 'attached', timeout: 30000 });
    } finally {
      await page.close().catch(() => {});
    }
  });
}

if (require.main === module) runWorkflow('lab-hapi-hl7', workflow, { openPatient: false });
module.exports = { workflow, hostileMessages, hostileXml, startObserver };
