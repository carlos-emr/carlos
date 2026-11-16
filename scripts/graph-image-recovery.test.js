/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

for (const page of ['rx/chartDrugProfile.jsp', 'lab/CA/ON/labValuesGraph.jsp']) {
    const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp', page), 'utf8');
    const script = source.match(/<script data-carlos-graph-recovery>([\s\S]*?)<\/script>/)?.[1];
    assert.ok(script, `${page} must register its image failure recovery`);
    for (const [name, complete, naturalWidth, initiallyHidden] of [
        ['pending image', false, 0, true],
        ['cached failure', true, 0, false],
        ['healthy image', true, 1000, true]
    ]) {
        test(`${page}: ${name} preserves recovery for subsequent image errors`, () => {
            const warning = {hidden: true};
            let onError;
            const image = {complete, naturalWidth, addEventListener(event, listener) {
                assert.equal(event, 'error');
                onError = listener;
            }};
            vm.runInNewContext(script, {document: {getElementById(id) {
                return id === 'measurementGraph' ? image : id === 'graphUnavailable' ? warning : null;
            }}});
            assert.equal(warning.hidden, initiallyHidden);
            assert.equal(typeof onError, 'function');
            onError();
            assert.equal(warning.hidden, false);
        });
    }
    test(`${page}: missing recovery markup raises an explicit error`, () => {
        assert.throws(() => vm.runInNewContext(script, {document: {getElementById() {return null;}}}),
            /Graph recovery markup is missing/);
    });
}
