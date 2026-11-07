/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const fs = require('node:fs');
const path = require('node:path');
module.exports = async function* (events) {
  const cases = [];
  let summary;
  for await (const {type, data} of events) {
    if (type === 'test:summary' && !data.file) summary = data;
    if (['test:pass', 'test:fail'].includes(type) && data.details?.type !== 'suite') {
      cases.push({name: data.name, file: data.file, outcome: data.skip ? 'SKIP' : data.todo ? 'TODO'
        : type === 'test:pass' ? 'PASS' : 'FAIL'});
    }
    if (type === 'test:fail') yield 'Failed fixture case: ' + data.name + '\n';
  }
  fs.writeFileSync(path.join(process.env.CARLOS_FIXTURE_PROOFS, process.pid + '-tests.json'),
    JSON.stringify({schemaVersion: 1, summary, cases}) + '\n', {flag: 'wx', mode: 0o600});
};
