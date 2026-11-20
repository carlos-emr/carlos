/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const { createHash } = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

test('bundled Chart.js matches the published 4.5.1 artifact', () => {
    const bundle = fs.readFileSync(path.join(__dirname, '../src/main/webapp/library/chartjs/chart.umd.min.js'));
    // From chart.js-4.5.1.tgz, independently matched against jsDelivr; see the vendor README.
    assert.equal(createHash('sha256').update(bundle).digest('hex'),
        '48444a82d4edcb5bec0f1965faacdde18d9c17db3063d042abada2f705c9f54a',
        'Restore the upstream bundle; broad source rewrites must not alter vendored JavaScript');
});
