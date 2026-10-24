#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The unlinked patient spreadsheet action was retired for issue #3965. Authenticated
 * bookmarks and CSRF-valid POSTs must both reach a 404, without patient data or an XLS
 * attachment. Use an owned synthetic patient so the same check can reproduce the old
 * download before removal. The patient-letters-envelopes workflow covers the neighboring
 * supported report actions through their real UI.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

async function workflow(s) {
  const url = h.appUrl(s.config.baseUrl, '/report/GenerateSpreadsheet');
  const csrfToken = await s.schedule.evaluate(async (ctx) => {
    const response = await fetch(`${ctx}/csrfguard`, { credentials: 'same-origin' });
    if (!response.ok) return '';
    const match = (await response.text()).match(/masterTokenValue\s*=\s*["']([^"']+)["']/);
    return match ? match[1] : '';
  }, new URL(s.config.baseUrl).pathname.replace(/\/$/, ''));
  h.assert(csrfToken, 'Could not read the session CSRF token');

  async function assertRetired(response, method) {
    try {
      const bytes = await response.body();
      const spreadsheet = bytes.subarray(0, 8).equals(Buffer.from('d0cf11e0a1b11ae1', 'hex'));
      h.assert(response.status() === 404,
        `Retired spreadsheet ${method} answered ${response.status()}, expected 404 (XLS bytes: ${spreadsheet})`);
      h.assert(!spreadsheet && !/attachment/i.test(response.headers()['content-disposition'] || ''),
        'The retired route still returned a download');
      h.assert(!bytes.includes(Buffer.from(s.marker)), 'The retired route returned the owned patient name');
    } finally {
      await response.dispose();
    }
  }

  await s.step('an authenticated spreadsheet bookmark returns 404 without patient data', async () => {
    await assertRetired(await s.context.request.get(url, {
      params: { demo: s.patient }, maxRedirects: 0, failOnStatusCode: false,
    }), 'GET');
  });
  await s.step('a CSRF-valid spreadsheet POST returns 404 without a download', async () => {
    await assertRetired(await s.context.request.post(url, {
      form: { demo: s.patient, 'CSRF-TOKEN': csrfToken },
      headers: { Referer: s.schedule.url() }, maxRedirects: 0, failOnStatusCode: false,
    }), 'POST');
  });
}

if (require.main === module) runWorkflow('patient-retired-spreadsheet', workflow, { openMaster: false });
module.exports = { workflow };
