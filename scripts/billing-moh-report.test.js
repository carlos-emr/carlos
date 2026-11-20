/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const script = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/billing-moh-report.js'), 'utf8');

async function render(options = {}) {
  const calls = [];
  const fragment = { hasChildNodes: () => !options.empty };
  const output = { replaceChildren: (...children) => { output.children = children; } };
  const error = { hidden: true };
  const source = { value: 'report', dataset: { stylesheet: '/carlos/billing/CA/ON/ES.xsl' } };
  let ready;
  const context = {
    document: {
      getElementById: id => ({ MOHreport: output, MOHreportError: error, MOHreportSource: source })[id],
      addEventListener: (name, callback) => { assert.equal(name, 'DOMContentLoaded'); ready = callback; },
    },
    DOMParser: class {
      parseFromString(value) {
        return {
          documentElement: { nodeName: options.wrongRoot ? 'OTHER' : 'REPORT' },
          querySelector: () => (value === 'report' ? options.badXml : options.badXsl),
        };
      }
    },
    XSLTProcessor: class {
      importStylesheet() { if (options.transformError) throw new Error('transform unavailable'); }
      transformToFragment() { return options.nullFragment ? null : fragment; }
    },
    fetch: async (url, settings) => {
      calls.push([url, settings.credentials]);
      if (options.networkError) throw new Error('network unavailable');
      return { ok: !options.httpError, text: async () => 'stylesheet' };
    },
  };
  vm.runInNewContext(script, context);
  await ready();
  return { output, error, fragment, calls };
}

test('renders the transformed report using its context-relative stylesheet', async () => {
  const result = await render();
  assert.deepEqual(result.calls, [['/carlos/billing/CA/ON/ES.xsl', 'same-origin']]);
  assert.deepEqual(result.output.children, [result.fragment]);
  assert.equal(result.error.hidden, true);
});

for (const failure of ['badXml', 'wrongRoot', 'httpError', 'networkError', 'badXsl', 'transformError', 'empty', 'nullFragment']) {
  test(`shows a visible error and clears the report on ${failure}`, async () => {
    const result = await render({ [failure]: true });
    assert.deepEqual(result.output.children, []);
    assert.equal(result.error.hidden, false);
    if (failure === 'badXml' || failure === 'wrongRoot') assert.deepEqual(result.calls, []);
  });
}
