/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const webapp = path.join(__dirname, '../src/main/webapp');
const jsp = fs.readFileSync(path.join(webapp, 'WEB-INF/jsp/rx/SearchDrug3.jsp'), 'utf8');
const start = jsp.indexOf('function getCost(');
const end = jsp.indexOf('function validateRxDate(', start);
assert.ok(start >= 0 && end > start, 'The prescribing price handler must exist');
const handler = jsp.slice(start, end);
const ajax = fs.readFileSync(path.join(webapp, 'share/javascript/carlos-ajax.js'), 'utf8');

function setup() {
  const requests = [];
  const elements = new Map(['cost_7', 'cost_8'].map(id => [id, {
    nodeType: 1,
    innerHTML: '',
    insertAdjacentHTML(position, html) {
      assert.equal(position, 'beforeend');
      this.innerHTML += html;
    },
  }]));
  class XMLHttpRequest {
    constructor() { requests.push(this); }
    open(method, url, asynchronous) { Object.assign(this, { method, url, asynchronous }); }
    setRequestHeader() {}
    send(body) { this.body = body; }
    respond(html) {
      Object.assign(this, { status: 200, responseText: html, responseURL: this.url });
      this.onload();
    }
  }
  const context = vm.createContext({
    ctx: '/carlos', XMLHttpRequest, URLSearchParams, FormData, console,
    document: { getElementById: id => elements.get(id) },
  });
  // Use the shipped Ajax helper; no Prototype globals or compatibility stubs.
  vm.runInContext(ajax, context);
  vm.runInContext(handler, context);
  return { context, requests, elements };
}

test('quantity pricing sends an asynchronous GET with encoded DIN and quantity through the native helper', () => {
  const s = setup();
  s.context.getCost('cost_7', '7', '0001234&extra=value', '12.5+units');
  assert.equal(s.requests.length, 1);
  const request = s.requests[0];
  const url = new URL(request.url, 'https://carlos.invalid');
  assert.equal(url.pathname, '/carlos/rx/ViewDrugPrice');
  assert.equal(request.method, 'GET');
  assert.equal(request.asynchronous, true);
  assert.equal(request.body, null);
  assert.deepEqual([...url.searchParams], [['randomId', '7'], ['din', '0001234&extra=value'], ['qty', '12.5+units']]);
  request.respond('<span>$3.00/30</span>');
  assert.equal(s.elements.get('cost_7').innerHTML, '<span>$3.00/30</span>');
  assert.equal(s.elements.get('cost_8').innerHTML, '');
});

test('a subsequent quantity price replaces the previous amount on the same card', () => {
  const s = setup();
  s.context.getCost('cost_7', '7', '0001234', '30');
  s.requests[0].respond('<span>$3.00/30</span>');
  s.context.getCost('cost_7', '7', '0001234', '60');
  s.requests[1].respond('<span>$6.00/60</span>');
  assert.equal(s.elements.get('cost_7').innerHTML, '<span>$6.00/60</span>');
});

test('an unavailable price clears an earlier amount instead of leaving stale pricing', () => {
  const s = setup();
  s.elements.get('cost_7').innerHTML = '<span>$3.00/30</span>';
  s.context.getCost('cost_7', '7', '', '30');
  s.requests[0].respond('');
  assert.equal(s.elements.get('cost_7').innerHTML, '');
});
