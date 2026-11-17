/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/demographic/demographicImport.jsp'), 'utf8');
const start = jsp.indexOf('function importOutcome(');
const end = jsp.indexOf('function showResponse(', start);
const outcome = vm.runInNewContext(`(${jsp.slice(start, end).trim()})`);
for (const [imported, refused, heading] of [
  [1, 0, 'Imported Successfully'], [3, 0, 'Imported Successfully'],
  [0, 1, 'No patients imported'], [0, 3, 'No patients imported'],
  [0, 0, 'No patients imported'], [1, 1, 'Import completed with refusals'],
  [2, 3, 'Import completed with refusals'],
]) {
  test(`${imported} imported and ${refused} refused: ${heading}`, () => {
    assert.equal(outcome(imported, refused).heading, heading);
    assert.equal(outcome(imported, refused).color === 'green', imported > 0 && refused === 0);
  });
}
for (const [imported, refused] of [[undefined, undefined], ['1', 0], [1, -1], [-1, 0], [NaN, 0], [0.5, 0]]) {
  test(`Invalid counts ${imported}/${refused} cannot claim success`, () => {
    assert.equal(outcome(imported, refused).heading, 'Import outcome unavailable');
    assert.equal(outcome(imported, refused).color, 'red');
  });
}
