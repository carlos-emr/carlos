#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
// #5782: the installed DefaultServlet's HTML range bytes retain their original framing.
const fs = require('node:fs');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const {runWorkflow} = require('./lib/workflow-session');

async function workflow(s) {
  const fixture = path.join(process.env.CARLOS_INSTALLED_WEBAPP_DIR || '/usr/share/carlos-emr/webapp/carlos', 'gpl.htm');
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
  await s.step('the complete installed HTML response carries the configured privacy notice', async () => {
    const response = await fetch();
    h.assert(response.status === 200, 'The complete static HTML response failed');
    h.assert(response.bytes.includes(Buffer.from('carlos-print-decoration')),
      'The installed privacy notice is unavailable for range qualification');
    h.assert(response.bytes.includes(originalBody), 'The decorated response lost original HTML body content');
  });
  await s.step('a single HTML byte range retains exact bytes and Content-Range without decoration', async () => {
    const response = await fetch({'X-Requested-With': 'XMLHttpRequest', Range: 'bytes=0-99'});
    h.assert(response.status === 206, 'The static HTML range did not return partial content');
    h.assert(response.bytes.equals(original.subarray(0, 100)), 'The partial HTML body no longer matches its range');
    h.assert(response.headers['content-range'] === `bytes 0-99/${original.length}`, 'The HTML Content-Range is incorrect');
    if (response.headers['content-length'] !== undefined) {
      h.assert(response.headers['content-length'] === '100', 'The HTML partial Content-Length is incorrect');
    }
  });
  await s.step('an ignored If-Range request still returns the full decorated HTML response', async () => {
    const response = await fetch({Range: 'bytes=0-99', 'If-Range': 'Thu, 01 Jan 1970 00:00:00 GMT'});
    h.assert(response.status === 200 && response.bytes.includes(Buffer.from('carlos-print-decoration')),
      'The full HTML response lost its privacy notice when If-Range was ignored');
    h.assert(response.bytes.includes(originalBody), 'The ignored If-Range response lost original HTML body content');
  });
}
if (require.main === module) runWorkflow('privacy-html-range', workflow, {openPatient: false});
module.exports = {workflow};
