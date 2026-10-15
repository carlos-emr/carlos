/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

for (const file of ['StaticScript2.jsp', 'ViewScript2.jsp', 'WriteScript.jsp']) {
  const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/rx', file), 'utf8');
  const source = jsp.match(/function ShowDrugInfo\([^]*?\n\s*\}/)?.[0];
  assert.ok(source, `${file} has no Info handler`);
  const rendered = source.replace(/<%= request.getContextPath\(\) %>|\$\{carlos:forJavaScript\(ctx\)\}/g, '/carlos');

  for (const din of ['02230711', null, undefined, '', 'null', '0']) {
    test(`${file} keeps Info on host and preserves the name with DIN ${String(din)}`, () => {
      const calls = [];
      const context = vm.createContext({ window: { open: (...args) => calls.push(args) } });
      vm.runInContext(rendered, context);
      const name = 'Synthetic & ingredient / 20mg # + "quoted"';
      context.ShowDrugInfo(name, din);
      assert.equal(calls.length, 1);
      const target = new URL(calls[0][0], 'https://clinic.example/carlos/');
      assert.equal(target.origin, 'https://clinic.example');
      assert.equal(target.pathname, '/carlos/rx/drugInfo');
      assert.equal(target.searchParams.get('GN'), name);
      assert.equal(target.searchParams.get('DIN'), din === '02230711' ? din : null);
      assert.equal(target.hash, '');
    });
  }
}
