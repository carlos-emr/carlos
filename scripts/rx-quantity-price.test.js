/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const webapp = path.join(__dirname, '../src/main/webapp');
const jsp = fs.readFileSync(path.join(webapp, 'WEB-INF/jsp/rx/SearchDrug3.jsp'), 'utf8');
const start = jsp.indexOf('function getLUC(');
const end = jsp.indexOf('function validateRxDate(', start);
assert.ok(start >= 0 && end > start, 'The prescribing card lookup handlers must exist');
const handler = jsp.slice(start, end);
const ajax = fs.readFileSync(path.join(webapp, 'share/javascript/carlos-ajax.js'), 'utf8');
const errorPage = '<html><body><main id="error-code">CARLOS Error: service unavailable</main></body></html>';
const jsMsg = {
  limitedUseLookupFailed: 'Limited-use codes could not be loaded.',
  costLookupFailed: 'The drug price could not be loaded.',
};

function panel(id) {
  let html = '';
  return {
    nodeType: 1,
    id,
    dataset: { din: '' },
    notice: null,
    get innerHTML() { return html; },
    set innerHTML(value) { html = value; this.notice = null; },
    set textContent(value) { this.innerHTML = value; },
    replaceChildren(notice) { this.innerHTML = ''; this.notice = notice; },
    insertAdjacentHTML(position, markup) {
      assert.equal(position, 'beforeend');
      this.innerHTML += markup;
    },
  };
}

function setup() {
  const requests = [];
  const elements = new Map(['cost_7', 'cost_8', 'luc_7'].map(id => [id, panel(id)]));
  class XMLHttpRequest {
    constructor() { this.aborted = false; requests.push(this); }
    open(method, url, asynchronous) { Object.assign(this, { method, url, asynchronous }); }
    setRequestHeader() {}
    send(body) { this.body = body; }
    abort() { this.aborted = true; }
    respond(html, status = 200, finalUrl = this.url) {
      Object.assign(this, { status, responseText: html, responseURL: finalUrl });
      this.onload();
    }
  }
  const context = vm.createContext({
    ctx: '/carlos', XMLHttpRequest, URL, URLSearchParams, FormData, console, jsMsg,
    window: { location: { href: 'https://carlos.invalid/carlos/rx/Show' } },
    document: {
      getElementById: id => elements.get(id),
      createElement: tagName => ({ tagName, setAttribute(name, value) { this[name] = value; } }),
    },
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

test('card pricing uses its DIN and omits the dispense count when it is unknown', () => {
  const s = setup();
  s.elements.get('cost_7').dataset.din = '02477580';
  s.context.refreshDrugPrice('7', null);
  s.context.refreshDrugPrice('8', '30');
  assert.equal(s.requests.length, 1, 'a custom drug has no price lookup');
  const url = new URL(s.requests[0].url, 'https://carlos.invalid');
  assert.equal(url.searchParams.get('din'), '02477580');
  assert.equal(url.searchParams.get('qty'), '');
  s.requests[0].respond('<span>$0.08 per unit</span>');
  assert.equal(s.elements.get('cost_7').innerHTML, '<span>$0.08 per unit</span>');
});

test('an unavailable price clears an earlier amount instead of leaving stale pricing', () => {
  const s = setup();
  s.elements.get('cost_7').innerHTML = '<span>$3.00/30</span>';
  s.context.getCost('cost_7', '7', '', '30');
  s.requests[0].respond('');
  assert.equal(s.elements.get('cost_7').innerHTML, '');
});


test('a newer quantity aborts the previous lookup so its late response cannot overwrite the current price', () => {
  const s = setup();
  s.context.getCost('cost_7', '7', '0001234', '30');
  s.context.getCost('cost_7', '7', '0001234', '60');
  assert.equal(s.requests[0].aborted, true);
  assert.equal(s.requests[1].aborted, false);
  s.requests[1].respond('<span>$6.00/60</span>');
  s.requests[0].respond('<span>$3.00/30</span>');
  assert.equal(s.elements.get('cost_7').innerHTML, '<span>$6.00/60</span>');
});

test('editing the quantity clears the quote and ignores a response already in flight', () => {
  const s = setup();
  s.context.getCost('cost_7', '7', '02477580', '30');
  s.context.clearDrugPrice('cost_7');
  assert.equal(s.requests[0].aborted, true);
  s.requests[0].respond('<span>$2.45/30</span>');
  assert.equal(s.elements.get('cost_7').innerHTML, '');
});

test('editing another prescription card does not cancel its independent price lookup', () => {
  const s = setup();
  s.context.getCost('cost_7', '7', '0001234', '30');
  s.context.getCost('cost_8', '8', '0005678', '60');
  assert.equal(s.requests[0].aborted, false);
  assert.equal(s.requests[1].aborted, false);
  s.requests[1].respond('<span>$12.00/60</span>');
  s.requests[0].respond('<span>$3.00/30</span>');
  assert.equal(s.elements.get('cost_7').innerHTML, '<span>$3.00/30</span>');
  assert.equal(s.elements.get('cost_8').innerHTML, '<span>$12.00/60</span>');
  s.context.getCost('cost_7', '7', '0001234', '60');
  assert.equal(s.requests[0].aborted, false, 'Completed requests should be released');
});

test('a removed prescription card does not start a price request', () => {
  const s = setup();
  s.elements.delete('cost_7');
  s.context.getCost('cost_7', '7', '0001234', '30');
  assert.equal(s.requests.length, 0);
});


for (const failure of ['network', 'http']) {
  test(`a changed quantity replaces its previous quote with a local notice after ${failure} failure`, () => {
    const s = setup();
    const cost = s.elements.get('cost_7');
    s.context.getCost('cost_7', '7', '0001234', '30');
    s.requests[0].respond('<span>$3.00/30</span>');
    s.context.getCost('cost_7', '7', '0001234', '60');
    assert.equal(cost.innerHTML, '', 'The pending lookup must not retain the old quote');
    if (failure === 'network') s.requests[1].onerror();
    else s.requests[1].respond(errorPage, 503);
    assert.equal(cost.innerHTML, '', 'A failed lookup must not insert its response document');
    assert.equal(cost.notice.role, 'alert');
    assert.equal(cost.notice.textContent, jsMsg.costLookupFailed + (failure === 'http' ? ' (HTTP 503)' : ''));
    assert.equal(cost._priceRequest, null);

    s.context.getCost('cost_7', '7', '0001234', '60');
    s.requests[2].respond('<span>$6.00/60</span>');
    assert.equal(cost.innerHTML, '<span>$6.00/60</span>');
    assert.equal(cost.notice, null);
  });
}

test('the limited-use lookup shows a local notice on refusal and renders a later successful fragment', () => {
  const s = setup();
  const limitedUse = s.elements.get('luc_7');
  s.context.getLUC('luc_7', '7', '02010909');
  const request = s.requests[0];
  const url = new URL(request.url, 'https://carlos.invalid');
  assert.equal(request.method, 'GET');
  assert.equal(url.pathname, '/carlos/rx/ViewLimitedUseCode');
  assert.deepEqual([...url.searchParams], [['randomId', '7'], ['din', '02010909']]);
  request.respond(errorPage, 403);
  assert.equal(limitedUse.innerHTML, '');
  assert.equal(limitedUse.notice.role, 'alert');
  assert.equal(limitedUse.notice.textContent, `${jsMsg.limitedUseLookupFailed} (HTTP 403)`);

  s.context.getLUC('luc_7', '7', '02010909');
  const fragment = '<table><tr><th>Limited Use Codes</th></tr></table>';
  s.requests[1].respond(fragment);
  assert.equal(limitedUse.innerHTML, fragment);
  assert.equal(limitedUse.notice, null);
});

for (const [handlerName, panelId, expected] of [
  ['getCost', 'cost_7', jsMsg.costLookupFailed],
  ['getLUC', 'luc_7', jsMsg.limitedUseLookupFailed],
]) {
  test(handlerName + ' rejects a sign-in response before inserting it into the prescription', () => {
    const s = setup();
    for (const redirected of [true, false]) {
      s.context[handlerName](panelId, '7', '02010909', '30');
      const request = s.requests.at(-1);
      const page = redirected ? '<form id="loginForm">Sign in</form>' : '<!doctype html><html><body>Sign in</body></html>';
      request.respond(page, 200, redirected ? '/carlos/logoutPage' : request.url);
      assert.equal(s.elements.get(panelId).innerHTML, '');
      assert.equal(s.elements.get(panelId).notice.textContent, expected);
    }
  });
}
