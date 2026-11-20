#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
// #5782: the installed DefaultServlet's HTML range bytes retain their original framing.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const {parseProperties} = require('./lib/java-properties');
const {runWorkflow} = require('./lib/workflow-session');

const NOTICE_START = '<p class="yesprint carlos-print-decoration"><b>\n';
const NOTICE_END = '</b><br/><b>END OF PRINTED DOCUMENT</b></p>';

function configuredStatement(webapp) {
  const effective = new Map();
  const sources = [
    path.join(webapp, 'WEB-INF/classes/carlos.properties'),
    process.env.CARLOS_PROPERTIES_FILE || '/etc/carlos-emr/carlos.properties',
    '/var/lib/carlos-emr/carlos.properties',
  ];
  for (const file of sources) {
    if (!fs.existsSync(file)) continue;
    for (const [key, value] of parseProperties(fs.readFileSync(file))) {
      if (key.startsWith('confidentiality_statement.v')) effective.set(key, value);
    }
  }
  // The server selects the last consecutive version beginning with v1.
  let statement;
  for (let version = 1; effective.has('confidentiality_statement.v' + version); version++) {
    statement = effective.get('confidentiality_statement.v' + version);
  }
  h.assert(statement?.trim(), 'The installed confidentiality statement is not configured');
  return statement;
}

async function workflow(s) {
  const webapp = process.env.CARLOS_INSTALLED_WEBAPP_DIR || '/usr/share/carlos-emr/webapp/carlos';
  const fixture = path.join(webapp, 'gpl.htm');
  const expectedNotice = NOTICE_START + configuredStatement(webapp) + NOTICE_END;
  h.assert(fs.existsSync(fixture), 'The installed static HTML fixture is unavailable; run this check on the host serving the packaged install');
  const original = fs.readFileSync(fixture);
  h.assert(original.length > 100, 'The installed static HTML fixture is unavailable');
  const body = /<body\b[^>]*>([\s\S]*?)<\/body>/i.exec(original.toString('utf8'));
  h.assert(body && body[1].length > 100, 'The installed static HTML fixture has no usable original body');
  const originalBody = Buffer.from(body[1]);
  const url = h.appUrl(s.config.baseUrl, '/gpl.htm');
  async function fetch(headers = {}) {
    const response = await s.context.request.get(url, {
      headers: {'Accept-Encoding': 'identity', ...headers}, maxRedirects: 0,
    });
    h.assert(Object.hasOwn(response.headers(), 'x-permitted-cross-domain-policies'),
      'The static HTML response did not come from the application');
    h.assert(/^text\/html(?:;|$)/i.test(response.headers()['content-type'] || ''),
      'The static HTML response has the wrong content type');
    return {status: response.status(), headers: response.headers(), bytes: await response.body()};
  }
  function assertDecorated(response) {
    h.assert(response.status === 200, 'The complete static HTML response failed');
    const charset = /charset\s*=\s*"?([^";\s]+)/i.exec(response.headers['content-type'] || '')?.[1] || 'utf-8';
    const document = new TextDecoder(charset).decode(response.bytes);
    h.assert(document.includes(expectedNotice), 'The installed privacy notice is missing or incomplete');
    h.assert(response.bytes.includes(originalBody), 'The decorated response lost original HTML body content');
  }
  function assertPartial(response, length) {
    h.assert(response.status === 206, 'The static HTML range did not return partial content');
    h.assert(response.bytes.equals(original.subarray(0, length)), 'The partial HTML body no longer matches its range');
    h.assert(response.headers['content-range'] === `bytes 0-${length - 1}/${original.length}`,
      'The HTML Content-Range is incorrect');
    h.assert(response.headers['content-length'] === String(length), 'The HTML partial Content-Length is incorrect');
  }
  let decorated;
  await s.step('the complete installed HTML response carries the configured privacy notice', async () => {
    decorated = await fetch();
    assertDecorated(decorated);
    const opening = Buffer.from(NOTICE_START);
    const noticeStart = decorated.bytes.indexOf(opening);
    h.assert(noticeStart >= 0, 'The decorated response has no notice opening');
    const markerOnly = {...decorated, bytes: decorated.bytes.subarray(0, noticeStart + opening.length)};
    assert.throws(() => assertDecorated(markerOnly), /privacy notice is missing or incomplete/);
  });
  const stale = 'Thu, 01 Jan 1970 00:00:00 GMT';
  const length = Math.min(16384, original.length - 1);
  const range = `bytes=0-${length - 1}`;
  await s.step('ordinary HTML ranges follow the advertised representation and keep exact framing', async () => {
    if (decorated.headers['accept-ranges'] === 'bytes') {
      const validator = decorated.headers['last-modified'];
      h.assert(validator, 'The range-capable static response did not provide a Last-Modified validator');
      assertPartial(await fetch({Range: range}), length);
      assertPartial(await fetch({Range: range, 'If-Range': validator}), length);
    } else {
      h.assert(!decorated.headers['last-modified'] && !decorated.headers.etag,
        'The decorated response without ranges exposed bare file validators');
      assertDecorated(await fetch({Range: range}));
    }
    assertDecorated(await fetch({Range: range, 'If-Range': stale}));
  });
  await s.step('matching and stale If-Range requests keep the raw HTML representation', async () => {
    const ajax = {'X-Requested-With': 'XMLHttpRequest'};
    const raw = await fetch(ajax);
    const validator = raw.headers['last-modified'];
    h.assert(raw.status === 200 && raw.bytes.equals(original) && validator,
      'The raw HTML response did not provide exact bytes and a Last-Modified validator');
    assertPartial(await fetch({...ajax, Range: range}), length);
    assertPartial(await fetch({...ajax, Range: range, 'If-Range': validator}), length);
    const ignored = await fetch({...ajax, Range: range, 'If-Range': stale});
    h.assert(ignored.status === 200 && ignored.bytes.equals(original), 'The stale raw range did not return the exact file');
  });
}
if (require.main === module) runWorkflow('privacy-html-range', workflow, {openPatient: false});
module.exports = {workflow};
