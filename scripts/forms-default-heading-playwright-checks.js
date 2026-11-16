#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h = require('./lib/playwright-harness');
const {runWorkflow} = require('./lib/workflow-session');

function preflightConfig() {
  if (process.env.CARLOS_DISPOSABLE_HOST !== 'true' || process.env.EXCLUSIVE !== '1') {
    throw new h.SkipCheck('Requires a disposable exclusive application host');
  }
}

async function workflow(s) {
  async function read(heading) {
    const suffix = heading === undefined ? '' : `?heading=${encodeURIComponent(heading)}`;
    const response = await s.context.request.get(h.appUrl(s.config.baseUrl, `/ws/rs/forms/${s.patient}/all${suffix}`),
      {maxRedirects: 0, timeout: 30000});
    h.assert(response.status() === 200, `Forms heading request returned HTTP ${response.status()}`);
    h.assert((response.headers()['content-type'] || '').includes('application/json'), 'Forms response was not JSON');
    const body = await response.json();
    h.assert(body && Array.isArray(body.list), 'Forms response omitted its list');
    h.assert(body.list.every(form => String(form.demographicNo) === s.patient), 'Forms response used a different patient context');
    return body.list;
  }
  let available;
  await s.step('the explicit Available heading returns the existing available form list', async () => {
    available = await read('Available');
  });
  await s.step('an omitted heading returns the same available forms without a server error', async () => {
    h.assert(JSON.stringify(await read()) === JSON.stringify(available), 'Omitting heading changed the available forms');
  });
  await s.step('empty and unrecognized headings retain the existing available-form behavior', async () => {
    for (const heading of ['', 'Unknown', 'completed']) {
      h.assert(JSON.stringify(await read(heading)) === JSON.stringify(available), 'A non-Completed heading changed the available forms');
    }
  });
  await s.step('Completed stays scoped to the owned patient and does not return available templates', async () => {
    h.assert((await read('Completed')).length === 0, 'The new owned patient has unexpected completed forms');
  });
}

if (require.main === module) runWorkflow('forms-default-heading', workflow,
  {openMaster: false, preflightConfig});
module.exports = {preflightConfig, workflow};
