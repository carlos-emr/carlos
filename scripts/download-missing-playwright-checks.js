#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
// #4569: use authenticated sessions armed by the real listing pages, then request a missing basename.
const fs = require('node:fs');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const D = require('./lib/download-contract');
const { runWorkflow } = require('./lib/workflow-session');

async function workflow(s) {
  const filename = `${s.marker}-missing.txt`;
  for (const target of [
    { name: 'BackupDownload', directory: process.env.ONEDT_INBOX,
      page: '/billing/CA/ON/moveMOHFiles?folder=INBOX', pageHas: /id="reportFolder"/,
      download: `/servlet/BackupDownload?filename=${encodeURIComponent(filename)}` },
    { name: 'OscarDownload', directory: process.env.DOCUMENT_DIR,
      page: '/oscarReport/obec', pageHas: /id="obecForm"/,
      download: `/servlet/OscarDownload?homepath=obecdownload&filename=${encodeURIComponent(filename)}` },
  ]) {
    await s.step(`${target.name} returns an application 404 for an authorized missing file`, async () => {
      h.assert(target.directory && fs.statSync(target.directory).isDirectory(), `${target.name} fixture directory is unavailable`);
      h.assert(!fs.existsSync(path.join(target.directory, filename)), 'The missing-file marker already exists');
      const listing = await D.fetchRaw(s.context, h.appUrl(s.config.baseUrl, target.page));
      h.assert(listing.status === 200 && listing.fromApp && target.pageHas.test(listing.text),
        `${target.name} session directory was not armed by its real page`);
      const response = await D.fetchRaw(s.context, h.appUrl(s.config.baseUrl, target.download));
      h.assert(response.status === 404 && response.fromApp, `${target.name} answered ${response.status}, expected the application's 404`);
      D.assertNothingServed(response, { label: target.name, forbidden: [] });
    });
  }
}

if (require.main === module) runWorkflow('download-missing', workflow, { openPatient: false });
module.exports = { workflow };
