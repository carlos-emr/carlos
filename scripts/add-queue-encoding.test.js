/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

// Execute the production page's click handler. Only the DOM and transport are synthetic.
const jsp = fs.readFileSync(path.join(__dirname,
  '../src/main/webapp/WEB-INF/jsp/admin/addQueue.jsp'), 'utf8');
const script = jsp.match(/<script type="text\/javascript">([\s\S]*?)<\/script>/)[1]
  .replace(/<fmt:message\b[^>]*\/>/g, 'fixture message');

function submit(name) {
  const document = { title: 'Queue fixture' };
  let clicked;
  const requests = [];
  const $ = selector => {
    if (selector === document) return { ready: callback => callback($) };
    if (selector === '#add-btn') return { click: callback => { clicked = callback; } };
    if (selector === '#newQueueName') return { val: () => name };
    const element = { removeClass: () => element, addClass: () => element,
      show: () => element, hide: () => element, html: () => element };
    return element;
  };
  $.ajax = options => requests.push(options);
  vm.runInNewContext(script, { document, $, registerFormSubmit: () => {} });
  assert.equal(typeof clicked, 'function', 'The page must bind its Add button');
  let prevented = false;
  clicked({ preventDefault: () => { prevented = true; } });
  assert.equal(prevented, true);
  return requests;
}

for (const name of ['Labs & Imaging', 'A+B', '100% results', 'Équipe 漢字',
  'Queue &method=deleteQueue&id=1', 'Queue #1 = results']) {
  test(`shouldPreserveTheWholeQueueName_for${JSON.stringify(name)}`, () => {
    const requests = submit(`  ${name}  `);
    assert.equal(requests.length, 1);
    const request = requests[0];
    assert.equal(request.method, 'POST');
    assert.equal(request.dataType, 'json');
    const body = new URLSearchParams(request.data);
    assert.deepEqual([...body.entries()], [['method', 'addNewQueue'], ['newQueueName', name]]);
  });
}

for (const name of ['', '   ', 'default', ' default ']) {
  test(`shouldNotSendAQueueCreation_forReservedOrBlank${JSON.stringify(name)}`, () => {
    assert.equal(submit(name).length, 0);
  });
}
