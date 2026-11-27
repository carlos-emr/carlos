#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const {runWorkflow} = require('./lib/workflow-session');

async function workflow(s) {
  const fixture = path.join(process.env.CARLOS_INSTALLED_WEBAPP_DIR || '/usr/share/carlos-emr/webapp/carlos', 'gpl.htm');
  h.assert(fs.existsSync(fixture), 'Run this check on the host serving the installed static HTML fixture');
  const original = fs.readFileSync(fixture);
  const body = /<body\b[^>]*>([\s\S]*?)<\/body>/i.exec(original.toString('utf8'));
  h.assert(body && body[1].length > 100, 'The installed static HTML fixture has no usable original body');
  const originalBody = Buffer.from(body[1]);
  const url = h.appUrl(s.config.baseUrl, '/gpl.htm');
  async function fetch(method, headers = {}, target = url) {
    const response = await s.context.request.fetch(target, {
      method, headers: {'Accept-Encoding': 'identity', ...headers}, maxRedirects: 0,
    });
    try {
      const result = {status: response.status(), headers: response.headers(), bytes: await response.body()};
      h.assert(Object.hasOwn(result.headers, 'x-permitted-cross-domain-policies'),
        'The static HTML response did not come from the application');
      return result;
    } finally {
      await response.dispose();
    }
  }
  function decorated(response) {
    h.assert(response.status === 200 && /^text\/html(?:;|$)/i.test(response.headers['content-type'] || ''),
      'The decorated HTML request did not succeed');
    h.assert(response.bytes.includes(originalBody), 'The decorated response lost the installed page content');
    h.assert(response.bytes.includes(Buffer.from('carlos-print-decoration')), 'The configured privacy notice is unavailable');
  }
  function noBareValidators(response) {
    for (const header of ['etag', 'last-modified', 'accept-ranges']) {
      h.assert(!Object.hasOwn(response.headers, header), `The decorated representation retained bare-file ${header}`);
    }
  }
  let fullLength;
  await s.step('the decorated static page preserves its content and omits bare-file validators', async () => {
    const response = await fetch('GET');
    decorated(response);
    noBareValidators(response);
    fullLength = response.bytes.length;
  });
  await s.step('a two-mebibyte owned static page has no bare-file metadata after its body commits', async () => {
    const name = 'privacy-large-' + s.marker.replace(/[^A-Za-z0-9_-]/g, '_') + '.html';
    const owned = path.join(path.dirname(fixture), name);
    let created = false;
    s.cleanup(() => {
      if (created) { fs.unlinkSync(owned); h.assert(!fs.existsSync(owned), 'The owned large-page fixture remained'); }
    });
    const payload = '<!doctype html><html><body>FAKE-owned-large-page-' + 'x'.repeat(2 * 1024 * 1024) + '</body></html>';
    fs.writeFileSync(owned, payload, {flag: 'wx', mode: 0o644});
    created = true;
    const response = await fetch('GET', {}, h.appUrl(s.config.baseUrl, '/' + name));
    h.assert(response.status === 200 && response.bytes.includes(Buffer.from(payload)), 'The large static response lost owned content');
    h.assert(response.bytes.includes(Buffer.from('carlos-print-decoration')), 'The large response omitted the privacy notice');
    noBareValidators(response);
  });
  await s.step('browser fetches keep AJAX and ordinary cache variants separate at the same URL', async () => {
    const page = await s.context.newPage();
    try {
      h.wireStrictPage(page, 'privacy-cache-variants', s.recorder);
      await page.goto(url);
      const variants = await page.evaluate(async target => {
        const results = [];
        for (const ajax of [true, false, true, false]) {
          const response = await fetch(target, {headers: ajax ? {'X-Requested-With': 'XMLHttpRequest'} : {}});
          results.push({status: response.status, vary: response.headers.get('Vary'), text: await response.text(), ajax});
        }
        return results;
      }, url);
      for (const variant of variants) {
        h.assert(variant.status === 200, 'A browser cache variant failed');
        h.assert((variant.vary || '').split(',').some(token => token.trim().toLowerCase() === 'x-requested-with' || token.trim() === '*'),
          'The response omitted the AJAX cache variant');
        h.assert(variant.text.includes('carlos-print-decoration') !== variant.ajax,
          'The browser reused the wrong privacy-notice representation');
        h.assert(variant.text.includes(body[1]), 'A browser cache variant lost the installed page content');
      }
    } finally { await page.close(); }
  });
  await s.step('HEAD sends no body and omits or matches the decorated GET length', async () => {
    const response = await fetch('HEAD');
    h.assert(response.status === 200 && response.bytes.length === 0, 'The static HEAD request failed or sent a body');
    noBareValidators(response);
    h.assert(response.headers['content-length'] === undefined || Number(response.headers['content-length']) === fullLength,
      'HEAD advertised the undecorated file length');
  });
  await s.step('a conditional request using the installed file date still receives the current decorated page', async () => {
    const response = await fetch('GET', {'If-Modified-Since': fs.statSync(fixture).mtime.toUTCString()});
    decorated(response);
    noBareValidators(response);
  });
  await s.step('the wildcard existence condition still returns an empty 304 without bare-file validators', async () => {
    const response = await fetch('GET', {'If-None-Match': '*'});
    h.assert(response.status === 304 && response.bytes.length === 0, 'The wildcard existence condition changed');
    noBareValidators(response);
  });
}
if (require.main === module) runWorkflow('privacy-static-html-metadata', workflow, {openPatient: false});
module.exports = {workflow};
