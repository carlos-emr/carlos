/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
'use strict';

/*
 * Unit coverage of the CARLOS patches to the CSRFGuard client template
 * (src/main/resources/csrfguard/carlos-csrfguard.js, issue #4130), run in a vm
 * against a minimal DOM stand-in so it needs no browser. The real-browser
 * counterpart is scripts/csrfguard-client-browser-check.js.
 */

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const {renderCsrfGuardTemplate} = require('./lib/csrfguard-template');

const TOKEN = 'UNIT-MASTER-TOKEN';
const HOST = '127.0.0.1';

class FakeElement {
  constructor(tag) {
    this.tagName = tag.toUpperCase();
    this.nodeType = 1;
    this.attributes = {};
    this.children = [];
  }

  setAttribute(name, value) {
    this.attributes[name] = String(value);
    if (name === 'name' || name === 'value' || name === 'type') {
      this[name] = String(value);
    }
  }

  getAttribute(name) {
    return Object.hasOwn(this.attributes, name) ? this.attributes[name] : null;
  }

  hasAttribute(name) {
    return Object.hasOwn(this.attributes, name);
  }

  appendChild(child) {
    this.children.push(child);
    return child;
  }

  descendants() {
    return this.children.flatMap(child => [child, ...(child.descendants ? child.descendants() : [])]);
  }

  querySelectorAll(selector) {
    assert.equal(selector, 'form', 'the patch only ever looks for nested forms');
    return this.descendants().filter(node => node.tagName === 'FORM');
  }
}

/** Builds one isolated page: its own HTMLFormElement, document and recorded submissions. */
function loadPage({pathname = '/carlos/billing/CA/ON/history', evaluations = 1} = {}) {
  const submitted = [];
  const listeners = {};
  const observers = [];

  class HTMLFormElement extends FakeElement {
    constructor() {
      super('form');
    }

    /**
     * Shaped like the browser's HTMLFormControlsCollection where it matters: controls
     * by position, plus an enumerable key for a control whose NAME is numeric, which
     * (being an array index) resolves positionally -- to undefined past the end.
     * That is what made upstream's Object.keys(form.elements) walk throw on the dx
     * search results.
     */
    get elements() {
      const controls = this.descendants().filter(node => node.tagName === 'INPUT' || node.tagName === 'BUTTON');
      const collection = {};
      controls.forEach((control, index) => { collection[index] = control; });
      for (const control of controls) {
        if (/^\d+$/.test(control.name || '') && !(control.name in collection)) collection[control.name] = undefined;
      }
      Object.defineProperty(collection, 'length', {value: controls.length, enumerable: false});
      return collection;
    }
  }
  /** Records the form data set as a browser builds it: disabled controls are left out. */
  HTMLFormElement.prototype.submit = function () {
    submitted.push(Array.from(this.elements).filter(field => !field.disabled).map(field => [field.name, field.value]));
  };

  const href = `https://${HOST}${pathname}`;
  const document = {
    domain: HOST,
    baseURI: href,
    addEventListener(type, fn, capture) {
      (listeners[type] = listeners[type] || []).push({fn, capture});
    },
    createElement: tag => (tag.toLowerCase() === 'form' ? new HTMLFormElement() : new FakeElement(tag)),
    getElementsByTagName: () => [],
  };
  function XMLHttpRequest() {}
  XMLHttpRequest.prototype.open = function () {};
  XMLHttpRequest.prototype.send = function () {};
  class MutationObserver {
    constructor(callback) {
      observers.push(callback);
    }

    observe() {}

    disconnect() {}
  }

  const windowListeners = {};
  const context = vm.createContext({
    document,
    HTMLFormElement,
    XMLHttpRequest,
    MutationObserver,
    setTimeout,
    window: {
      addEventListener(type, fn, capture) {
        (windowListeners[type] = windowListeners[type] || []).push({fn, capture});
      },
    },
    navigator: {appName: 'Netscape'},
    location: {hostname: HOST, pathname, search: '', href, origin: `https://${HOST}`},
    URL,
    console: {debug() {}, warn() {}, error() {}, log() {}},
  });
  const script = renderCsrfGuardTemplate({host: HOST, token: TOKEN, contextPath: '/carlos'});
  for (let i = 0; i < evaluations; i++) {
    // A second evaluation in a fresh realm sharing the same prototype stands in for
    // the script being served twice into one document.
    vm.runInContext(script, i === 0 ? context : vm.createContext({...context, owaspCSRFGuardScriptHasLoaded: undefined}));
  }

  function form({method = 'post', action = null, fields = {}} = {}) {
    const element = new HTMLFormElement();
    if (method !== null) element.setAttribute('method', method);
    if (action !== null) element.setAttribute('action', action);
    for (const [name, value] of Object.entries(fields)) {
      const input = new FakeElement('input');
      input.setAttribute('name', name);
      input.setAttribute('value', value);
      element.appendChild(input);
    }
    return element;
  }

  /** Capture on document, then bubble to window; returns the data a navigation would send. */
  function dispatchSubmit(target, submitter = null, {pageCancels = false} = {}) {
    const event = {target, submitter, defaultPrevented: false};
    for (const {fn, capture} of listeners.submit || []) {
      if (capture) fn(event);
    }
    if (pageCancels) event.defaultPrevented = true;
    for (const {fn} of windowListeners.submit || []) fn(event);
    return Array.from(target.elements).filter(field => !field.disabled).map(field => [field.name, field.value]);
  }

  function setBase(url) {
    document.baseURI = url;
  }

  return {submitted, observers, form, dispatchSubmit, HTMLFormElement, setBase};
}

const tokensIn = fields => fields.filter(([name]) => name === 'CSRF-TOKEN').map(([, value]) => value);
const tokenFieldsOf = form => Array.from(form.elements).filter(field => field.name === 'CSRF-TOKEN').map(field => field.value);

test('form.submit() on a script-built POST form adds the token before posting', () => {
  const page = loadPage();
  page.form({action: '/carlos/billing/CA/ON/BillingDeleteNoAppt', fields: {billing_no: '7'}}).submit();
  assert.deepEqual(tokensIn(page.submitted[0]), [TOKEN]);
  assert.deepEqual(page.submitted[0][0], ['billing_no', '7']);
});

test('relative, upper-case-method and action-less POST forms all get the token', () => {
  const page = loadPage();
  page.form({method: 'POST', action: 'addEditTemplatesAction'}).submit();
  page.form({action: ''}).submit();
  page.form({}).submit();
  assert.deepEqual(page.submitted.map(tokensIn), [[TOKEN], [TOKEN], [TOKEN]]);
});

test('GET forms, forms without a method and cross-origin actions never get the token', () => {
  const page = loadPage();
  page.form({method: 'get', action: '/carlos/search'}).submit();
  page.form({method: null, action: '/carlos/search'}).submit();
  page.form({action: 'https://example.invalid/collect'}).submit();
  page.form({action: '//example.invalid/collect'}).submit();
  page.form({action: 'javascript:void(0)'}).submit();
  assert.deepEqual(page.submitted.map(tokensIn), [[], [], [], [], []]);
});

test('another port or scheme on the same host is another origin and never gets the token', () => {
  // The upstream isValidUrl() compares the hostname alone; patch 3 must not.
  const page = loadPage();
  page.form({action: `https://${HOST}:8443/carlos/x`}).submit();
  page.form({action: `http://${HOST}/carlos/x`}).submit();
  page.form({action: `https://${HOST}/carlos/x`}).submit();
  page.form({action: `https://${HOST}:443/carlos/x`}).submit();
  assert.deepEqual(page.submitted.map(tokensIn), [[], [], [TOKEN], [TOKEN]]);
});

test('an existing token field is filled, not duplicated', () => {
  const page = loadPage();
  page.form({action: '/carlos/eform/delEForm', fields: {'CSRF-TOKEN': ''}}).submit();
  assert.deepEqual(tokensIn(page.submitted[0]), [TOKEN]);
});

test('the submit event honours the submitter formmethod and formaction', () => {
  const page = loadPage();
  const getForm = page.form({method: 'get', action: '/carlos/search'});
  const postButton = new FakeElement('button');
  postButton.setAttribute('formmethod', 'post');
  page.dispatchSubmit(getForm, postButton);
  assert.deepEqual(tokenFieldsOf(getForm), [TOKEN]);

  const postForm = page.form({action: '/carlos/eforms/delGroup'});
  const crossOrigin = new FakeElement('button');
  crossOrigin.setAttribute('formaction', 'https://example.invalid/');
  page.dispatchSubmit(postForm, crossOrigin);
  assert.deepEqual(tokenFieldsOf(postForm), []);
});

test('forms nested in inserted HTML get the token, including numerically named controls', () => {
  const page = loadPage();
  assert.equal(page.observers.length, 1, 'the dynamic-node observer was not installed');
  const wrapper = new FakeElement('div');
  const groups = page.form({action: '/carlos/eforms/removeFromGroup', fields: {'250': '250', '4019': '4019'}});
  const later = page.form({action: '/carlos/eform/removeEForm'});
  wrapper.appendChild(groups);
  wrapper.appendChild(later);
  page.observers[0]([{type: 'childList', addedNodes: [wrapper]}]);
  assert.deepEqual(tokenFieldsOf(groups), [TOKEN]);
  assert.deepEqual(tokenFieldsOf(later), [TOKEN]);
});

test('the submit hook wraps form.submit() only once per window', () => {
  const once = loadPage();
  const twice = loadPage({evaluations: 2});
  // The second evaluation must leave the prototype's submit exactly as the first set it.
  assert.equal(twice.HTMLFormElement.prototype.submit.toString(), once.HTMLFormElement.prototype.submit.toString());
  assert.equal(Object.getOwnPropertyDescriptor(twice.HTMLFormElement.prototype, 'carlosCsrfSubmitHooked').value, true);
  twice.form({action: '/carlos/x'}).submit();
  assert.equal(twice.submitted.length, 1, 'the native submit ran more than once');
  assert.deepEqual(tokensIn(twice.submitted[0]), [TOKEN]);
});

test('a token already in the form is withheld from a GET or cross-origin form.submit() and then restored', () => {
  // The load-time scan fills forms that have no method attribute; their native submit is a GET.
  const page = loadPage();
  const getForm = page.form({method: null, action: '/carlos/search', fields: {'CSRF-TOKEN': TOKEN, q: 'x'}});
  getForm.submit();
  const crossForm = page.form({action: 'https://example.invalid/collect', fields: {'CSRF-TOKEN': TOKEN}});
  crossForm.submit();
  assert.deepEqual(page.submitted.map(tokensIn), [[], []]);
  assert.deepEqual(tokenFieldsOf(getForm), [TOKEN], 'the withheld field must stay in the form');
  assert.equal(getForm.elements[0].disabled, false, 'the withheld field must be enabled again');
});

test('a submit event leaving the origin or as a GET does not send an existing token', () => {
  const page = loadPage();
  // A fresh form per case: a withheld field is enabled again on the next task.
  const form = () => page.form({action: '/carlos/eforms/delGroup', fields: {'CSRF-TOKEN': TOKEN}});
  const away = new FakeElement('button');
  away.setAttribute('formaction', 'https://example.invalid/collect');
  assert.deepEqual(tokensIn(page.dispatchSubmit(form(), away)), []);
  const asGet = new FakeElement('button');
  asGet.setAttribute('formmethod', 'get');
  assert.deepEqual(tokensIn(page.dispatchSubmit(form(), asGet)), []);
  // A page handler that cancels the navigation to post the form itself keeps the token.
  assert.deepEqual(tokensIn(page.dispatchSubmit(form(), away, {pageCancels: true})), [TOKEN]);
});

test('a withheld submit-event token is enabled again on the next task', async () => {
  const page = loadPage();
  const form = page.form({method: 'get', action: '/carlos/search', fields: {'CSRF-TOKEN': TOKEN}});
  page.dispatchSubmit(form);
  assert.equal(form.elements[0].disabled, true);
  await new Promise(resolve => setTimeout(resolve, 0));
  assert.equal(form.elements[0].disabled, false);
});

test('a cross-origin <base href> does not make a relative action count as same-origin', () => {
  const page = loadPage();
  page.setBase('https://example.invalid/');
  page.form({action: 'collect'}).submit();
  // A form with no action posts to the document URL whatever <base> says, so it keeps the token.
  page.form({}).submit();
  page.form({action: ''}).submit();
  assert.deepEqual(page.submitted.map(tokensIn), [[], [TOKEN], [TOKEN]]);
});

test('Owasp.CsrfGuard.properties serves the patched template', () => {
  const properties = fs.readFileSync(path.join(__dirname,
    '../src/main/webapp/WEB-INF/Owasp.CsrfGuard.properties'), 'utf8');
  assert.match(properties,
    /^org\.owasp\.csrfguard\.JavascriptServlet\.sourceFile\s*=\s*classpath:csrfguard\/carlos-csrfguard\.js\s*$/m);
});

test('the template renderer refuses non-plain values and allows an empty context path', () => {
  assert.throws(() => renderCsrfGuardTemplate({host: 127, token: TOKEN}), /host must be/);
  assert.throws(() => renderCsrfGuardTemplate({host: '', token: TOKEN}), /host must be/);
  assert.throws(() => renderCsrfGuardTemplate({host: HOST, token: ''}), /token must be/);
  assert.throws(() => renderCsrfGuardTemplate({host: HOST, token: "x'; alert(1); '"}), /token must be/);
  assert.doesNotThrow(() => renderCsrfGuardTemplate({host: HOST, token: TOKEN, contextPath: ''}));
});
