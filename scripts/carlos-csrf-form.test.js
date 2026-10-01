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

/*
 * Unit tests for src/main/webapp/share/javascript/carlosCsrfForm.js (issue #4130).
 *
 * The helper runs in a browser; it is loaded here into a vm context with a
 * deliberately small DOM: just enough elements, attribute reflection, selector
 * matching and a MutationObserver to exercise it. Run with:
 *   node --test scripts/carlos-csrf-form.test.js
 *
 * What these pin:
 *   - a runtime-built POST form leaves WITH the session token, whichever of
 *     the page's sources supplies it (page input, csrf-token.jspf bootstrap,
 *     CSRFGuard servlet);
 *   - when no token can be had the form is NOT submitted and the user is told,
 *     rather than a doomed POST being refused silently with a 403;
 *   - forms nested inside an inserted container (the Administration shell's
 *     .load()) are tokenised, which CSRFGuard's own observer does not do;
 *   - the token never goes to another origin, GET forms are left alone, and a
 *     form whose controls have numeric names (the dx search page) is handled.
 *
 * The live counterpart is scripts/csrf-runtime-forms-playwright-checks.js.
 */

const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const SOURCE = fs.readFileSync(
  path.join(__dirname, '..', 'src', 'main', 'webapp', 'share', 'javascript', 'carlosCsrfForm.js'),
  'utf8',
);

const ORIGIN = 'https://emr.example';
const PAGE_URL = `${ORIGIN}/carlos/billing/CA/ON/billingONHistory`;
const SERVLET_JS = 'var masterTokenValue = "SERVLET-TOKEN";';

/* ------------------------------------------------------------------------ */
/* A minimal DOM                                                            */
/* ------------------------------------------------------------------------ */

/** Matches the handful of selectors the helper uses. */
function matches(element, selector) {
  if (element.nodeType !== 1) {
    return false;
  }
  const attr = /^([a-z]+)\[([a-z-]+)(?:="([^"]*)")?\]$/i.exec(selector);
  if (attr) {
    const [, tag, name, value] = attr;
    if (element.tagName.toLowerCase() !== tag.toLowerCase()) {
      return false;
    }
    const actual = element.getAttribute(name);
    return value === undefined ? actual !== null : actual === value;
  }
  return element.tagName.toLowerCase() === selector.toLowerCase();
}

function makeDom() {
  const observers = [];
  let submissions = [];

  function notify(target, node) {
    const record = { type: 'childList', target, addedNodes: [node] };
    observers.forEach((observer) => observer.pending.push(record));
    // Mutation observers run as a microtask, after the current task, which is
    // exactly why a form built and submitted in one click handler escaped
    // CSRFGuard's injector.
    queueMicrotask(() => observers.forEach((observer) => observer.flush()));
  }

  class Element {
    constructor(tagName) {
      this.nodeType = 1;
      this.tagName = tagName.toUpperCase();
      this.attributes = new Map();
      this.children = [];
      this.parentNode = null;
      this.style = {};
    }

    getAttribute(name) {
      return this.attributes.has(name) ? this.attributes.get(name) : null;
    }

    setAttribute(name, value) {
      this.attributes.set(name, String(value));
    }

    hasAttribute(name) {
      return this.attributes.has(name);
    }

    appendChild(child) {
      child.parentNode = this;
      this.children.push(child);
      if (this.isConnected()) {
        notify(this, child);
      }
      return child;
    }

    removeChild(child) {
      this.children = this.children.filter((candidate) => candidate !== child);
      child.parentNode = null;
      return child;
    }

    isConnected() {
      let node = this;
      while (node.parentNode) {
        node = node.parentNode;
      }
      return node === documentElement;
    }

    descendants() {
      const out = [];
      const walk = (node) => node.children.forEach((child) => { out.push(child); walk(child); });
      walk(this);
      return out;
    }

    querySelectorAll(selector) {
      return this.descendants().filter((node) => matches(node, selector));
    }

    querySelector(selector) {
      return this.querySelectorAll(selector)[0] || null;
    }
  }

  // Attribute-reflecting properties, as on the real elements.
  for (const name of ['name', 'type', 'target', 'id', 'src']) {
    Object.defineProperty(Element.prototype, name, {
      get() { return this.getAttribute(name) || ''; },
      set(value) { this.setAttribute(name, value); },
    });
  }
  Object.defineProperty(Element.prototype, 'method', {
    get() { return (this.getAttribute('method') || 'get').toLowerCase(); },
    set(value) { this.setAttribute('method', value); },
  });
  Object.defineProperty(Element.prototype, 'action', {
    get() { return new URL(this.getAttribute('action') || PAGE_URL, PAGE_URL).href; },
    set(value) { this.setAttribute('action', value); },
  });
  // An input's value is live state, not the attribute.
  Object.defineProperty(Element.prototype, 'value', {
    get() { return this._value !== undefined ? this._value : (this.getAttribute('value') || ''); },
    set(value) { this._value = String(value); },
  });

  class HTMLFormElement extends Element {}
  HTMLFormElement.prototype.requestSubmit = function requestSubmit(submitter) {
    // Like the browser: dispatch a cancelable submit event, then submit.
    const event = {
      target: this,
      submitter: submitter || null,
      defaultPrevented: false,
      preventDefault() { this.defaultPrevented = true; },
      stopImmediatePropagation() {},
    };
    document.fire('submit', event);
    if (!event.defaultPrevented) {
      HTMLFormElement.prototype.submit.call(this, submitter);
    }
  };
  HTMLFormElement.prototype.submit = function submit(submitter) {
    const fields = {};
    this.querySelectorAll('input').forEach((input) => {
      if (fields[input.name] === undefined) {
        fields[input.name] = [];
      }
      fields[input.name].push(input.value);
    });
    if (submitter && submitter.name) {
      fields[submitter.name] = [submitter.value];
    }
    submissions.push({
      method: this.method,
      action: this.action,
      target: this.target,
      fields,
    });
  };

  const documentElement = new Element('html');
  const head = new Element('head');
  const body = new Element('body');
  documentElement.appendChild(head);
  documentElement.appendChild(body);

  const document = {
    readyState: 'complete',
    documentElement,
    head,
    body,
    currentScript: null,
    createElement: (tag) => (tag.toLowerCase() === 'form' ? new HTMLFormElement(tag) : new Element(tag)),
    querySelectorAll: (selector) => documentElement.querySelectorAll(selector),
    querySelector: (selector) => documentElement.querySelector(selector),
    listeners: {},
    addEventListener(type, handler) {
      (this.listeners[type] = this.listeners[type] || []).push(handler);
    },
    fire(type, event) {
      (this.listeners[type] || []).forEach((handler) => handler(event));
    },
  };

  class MutationObserver {
    constructor(callback) {
      this.callback = callback;
      this.pending = [];
      observers.push(this);
    }

    observe() {}

    flush() {
      if (this.pending.length === 0) {
        return;
      }
      const records = this.pending;
      this.pending = [];
      this.callback(records, this);
    }
  }

  return {
    document,
    MutationObserver,
    HTMLFormElement,
    submissions: () => submissions,
    resetSubmissions: () => { submissions = []; },
  };
}

/** Builds a page-like element tree: form(method, action) with inputs. */
function buildForm(dom, { method = 'post', action = null, inputs = [] } = {}) {
  const form = dom.document.createElement('form');
  if (method !== null) {
    form.setAttribute('method', method);
  }
  if (action !== null) {
    form.setAttribute('action', action);
  }
  inputs.forEach(([name, value]) => {
    const input = dom.document.createElement('input');
    input.setAttribute('type', 'hidden');
    input.setAttribute('name', name);
    input.value = value;
    form.appendChild(input);
  });
  return form;
}

/**
 * Loads the helper into a fresh context.
 *
 * @param {object} options
 * @param {Function} [options.fetchImpl] stands in for window.fetch
 * @param {string} [options.pageToken] a token already on the page (CSRFGuard's own injection)
 * @param {string} [options.scriptSrc] the src the helper was loaded from
 * @param {Promise} [options.csrfTokenReady] the csrf-token.jspf bootstrap promise
 */
function loadHelper(options = {}) {
  const dom = makeDom();
  const alerts = [];
  const warnings = [];
  const errors = [];
  const fetches = [];
  const opened = [];

  if (options.pageToken !== undefined) {
    const staticForm = buildForm(dom, {
      action: '/carlos/messenger/HandleMessages',
      inputs: [['CSRF-TOKEN', options.pageToken]],
    });
    dom.document.body.appendChild(staticForm);
  }
  if (options.scriptSrc) {
    dom.document.currentScript = { src: options.scriptSrc };
  }
  if (options.baseURI) {
    dom.document.baseURI = options.baseURI;
  }
  if (options.readyState) {
    dom.document.readyState = options.readyState;
  }

  const fetchImpl = options.fetchImpl
    || (async () => ({ ok: true, text: async () => SERVLET_JS }));

  const window = {
    location: new URL(PAGE_URL),
    document: dom.document,
    MutationObserver: dom.MutationObserver,
    alert: (message) => alerts.push(message),
    open: (url, name) => { opened.push(name); return {}; },
    csrfTokenReady: options.csrfTokenReady,
  };
  const context = {
    window,
    document: dom.document,
    HTMLFormElement: dom.HTMLFormElement,
    URL,
    Promise,
    setTimeout,
    queueMicrotask,
    console: {
      warn: (...args) => warnings.push(args.map(String).join(' ')),
      error: (...args) => errors.push(args.map(String).join(' ')),
    },
    fetch: (url, init) => {
      fetches.push(url);
      return fetchImpl(url, init);
    },
  };
  vm.createContext(context);
  vm.runInContext(SOURCE, context);
  // The helper stores its API on window; mirror globals the way a browser does.
  ['CarlosCsrf', 'carlosPostForm', 'carlosSubmitForm'].forEach((name) => {
    context[name] = window[name];
  });

  return { dom, window, alerts, warnings, errors, fetches, opened };
}

/** Lets queued microtasks and setTimeout(..., 0) callbacks run. */
function settle() {
  return new Promise((resolve) => setTimeout(resolve, 10));
}

/* ------------------------------------------------------------------------ */
/* carlosPostForm / carlosSubmitForm                                        */
/* ------------------------------------------------------------------------ */

test('carlosPostForm submits the fields and the page token in one task', async () => {
  const helper = loadHelper({ pageToken: 'PAGE-TOKEN' });

  // No await: a click handler that builds and submits a form must already
  // have sent it with the token before any observer could run.
  helper.window.carlosPostForm('/carlos/billing/CA/ON/BillingDeleteNoAppt',
    { billing_no: '42', billCode: 'O', dboperation: 'delete_bill', hotclick: 0 },
    { target: 'unbill_popup' });

  const [submission] = helper.dom.submissions();
  assert.ok(submission, 'the form was submitted synchronously');
  assert.equal(submission.method, 'post');
  assert.equal(submission.action, `${ORIGIN}/carlos/billing/CA/ON/BillingDeleteNoAppt`);
  assert.equal(submission.target, 'unbill_popup');
  assert.deepEqual(submission.fields['CSRF-TOKEN'], ['PAGE-TOKEN']);
  assert.deepEqual(submission.fields.billing_no, ['42']);
  assert.deepEqual(submission.fields.hotclick, ['0']);
  assert.equal(helper.fetches.length, 0, 'a token already on the page costs no request');
});

test('carlosPostForm accepts [name, value] pairs so a name can repeat', async () => {
  const helper = loadHelper({ pageToken: 'PAGE-TOKEN' });

  await helper.window.carlosPostForm('/carlos/x', [['id', '1'], ['id', '2'], ['empty', null]]);

  const [submission] = helper.dom.submissions();
  assert.deepEqual(submission.fields.id, ['1', '2']);
  assert.deepEqual(submission.fields.empty, [''], 'null is sent as an empty value, not "null"');
});

test('an empty bootstrap input is skipped in favour of a populated one', async () => {
  const helper = loadHelper({ pageToken: 'PAGE-TOKEN' });
  // csrf-token.jspf's input, still empty, placed first in the document.
  const empty = helper.dom.document.createElement('input');
  empty.setAttribute('name', 'CSRF-TOKEN');
  helper.dom.document.body.children.unshift(empty);
  empty.parentNode = helper.dom.document.body;

  await helper.window.carlosPostForm('/carlos/x', {});

  assert.deepEqual(helper.dom.submissions()[0].fields['CSRF-TOKEN'], ['PAGE-TOKEN']);
});

test('with no token on the page it fetches one from the CSRFGuard servlet first', async () => {
  const helper = loadHelper({ scriptSrc: `${ORIGIN}/carlos/share/javascript/carlosCsrfForm.js` });

  await helper.window.carlosPostForm('/carlos/billing/CA/ON/ViewGenRADesc', { rano: '7' });

  assert.deepEqual(helper.fetches, ['/carlos/csrfguard'],
    'the servlet URL is built from the context path the script was loaded under');
  const [submission] = helper.dom.submissions();
  assert.deepEqual(submission.fields['CSRF-TOKEN'], ['SERVLET-TOKEN']);
  assert.deepEqual(helper.alerts, []);
});

test('the fetched token is cached and seeds empty bootstrap inputs', async () => {
  const helper = loadHelper({ scriptSrc: `${ORIGIN}/carlos/share/javascript/carlosCsrfForm.js` });
  const bootstrapInput = helper.dom.document.createElement('input');
  bootstrapInput.setAttribute('name', 'CSRF-TOKEN');
  helper.dom.document.body.appendChild(bootstrapInput);

  await helper.window.carlosPostForm('/carlos/a', {});
  await helper.window.carlosPostForm('/carlos/b', {});

  assert.equal(helper.fetches.length, 1);
  assert.equal(bootstrapInput.value, 'SERVLET-TOKEN');
  assert.deepEqual(helper.dom.submissions().map((s) => s.fields['CSRF-TOKEN'][0]),
    ['SERVLET-TOKEN', 'SERVLET-TOKEN']);
});

test('a pending csrf-token.jspf bootstrap is awaited instead of a second request', async () => {
  let resolveBootstrap;
  const csrfTokenReady = new Promise((resolve) => { resolveBootstrap = resolve; });
  const helper = loadHelper({ csrfTokenReady });
  const bootstrapInput = helper.dom.document.createElement('input');
  bootstrapInput.setAttribute('name', 'CSRF-TOKEN');
  helper.dom.document.body.appendChild(bootstrapInput);

  const sent = helper.window.carlosPostForm('/carlos/eform/unRemoveEForm', { fdid: '3' });
  assert.equal(helper.dom.submissions().length, 0, 'nothing is sent before the token exists');

  bootstrapInput.value = 'BOOTSTRAP-TOKEN';
  resolveBootstrap();
  await sent;

  assert.equal(helper.fetches.length, 0);
  assert.deepEqual(helper.dom.submissions()[0].fields['CSRF-TOKEN'], ['BOOTSTRAP-TOKEN']);
});

test('a failed lookup does not submit, tells the user, and rejects', async () => {
  const helper = loadHelper({ fetchImpl: async () => ({ ok: false, status: 500 }) });

  await assert.rejects(helper.window.carlosPostForm('/carlos/billing/CA/ON/BillingDeleteNoAppt', {}));

  assert.equal(helper.dom.submissions().length, 0, 'a POST that would be refused is not sent');
  assert.equal(helper.alerts.length, 1);
  assert.match(helper.alerts[0], /reload the page/i);
});

test('a failed lookup is retried on the next attempt rather than replayed', async () => {
  let calls = 0;
  const helper = loadHelper({
    fetchImpl: async () => {
      calls += 1;
      return calls === 1
        ? { ok: false, status: 503 }
        : { ok: true, text: async () => SERVLET_JS };
    },
  });

  await assert.rejects(helper.window.carlosPostForm('/carlos/x', {}));
  await helper.window.carlosPostForm('/carlos/x', {});

  assert.equal(calls, 2);
  assert.equal(helper.dom.submissions().length, 1);
});

test('a _blank target is opened by name before the token fetch, keeping the click gesture', async () => {
  const helper = loadHelper();

  const sent = helper.window.carlosPostForm('/carlos/billing/CA/ON/ViewOnGenRASummary', { rano: '1' },
    { target: '_blank' });
  assert.equal(helper.opened.length, 1, 'the window opened synchronously, inside the click');
  await sent;

  const [submission] = helper.dom.submissions();
  assert.equal(submission.target, helper.opened[0]);
  assert.notEqual(submission.target, '_blank');
});

test('a _blank target is left alone when the token is already on the page', async () => {
  const helper = loadHelper({ pageToken: 'PAGE-TOKEN' });

  await helper.window.carlosPostForm('/carlos/x', {}, { target: '_blank' });

  assert.equal(helper.opened.length, 0);
  assert.equal(helper.dom.submissions()[0].target, '_blank');
});

test('carlosSubmitForm reuses the form\'s own token input instead of adding a second', async () => {
  const helper = loadHelper({ pageToken: 'PAGE-TOKEN' });
  const form = buildForm(helper.dom, { action: '/carlos/x', inputs: [['CSRF-TOKEN', 'STALE']] });
  helper.dom.document.body.appendChild(form);

  await helper.window.carlosSubmitForm(form);

  // The page's static form is first in the document, so PAGE-TOKEN wins.
  assert.deepEqual(helper.dom.submissions()[0].fields['CSRF-TOKEN'], ['PAGE-TOKEN']);
});

test('carlosSubmitForm still works when a control named "submit" shadows form.submit', async () => {
  const helper = loadHelper({ pageToken: 'PAGE-TOKEN' });
  const form = buildForm(helper.dom, { action: '/carlos/x' });
  form.submit = { tagName: 'INPUT' }; // what form.submit resolves to in that case
  helper.dom.document.body.appendChild(form);

  await helper.window.carlosSubmitForm(form);

  assert.equal(helper.dom.submissions().length, 1);
});

test('carlosSubmitForm never sends the token to another origin', async () => {
  const helper = loadHelper({ pageToken: 'PAGE-TOKEN' });
  const form = buildForm(helper.dom, { action: 'https://elsewhere.example/collect' });
  helper.dom.document.body.appendChild(form);

  await helper.window.carlosSubmitForm(form);

  const [submission] = helper.dom.submissions();
  assert.equal(submission.fields['CSRF-TOKEN'], undefined);
});

test('carlosSubmitForm does not add a token to a GET form', async () => {
  const helper = loadHelper({ pageToken: 'PAGE-TOKEN' });
  const form = buildForm(helper.dom, { method: 'get', action: '/carlos/schedule/EditTemplate' });
  helper.dom.document.body.appendChild(form);

  await helper.window.carlosSubmitForm(form);

  assert.equal(helper.dom.submissions()[0].fields['CSRF-TOKEN'], undefined);
});

/* ------------------------------------------------------------------------ */
/* Automatic injection                                                      */
/* ------------------------------------------------------------------------ */

test('forms nested in an inserted container are tokenised (Administration shell .load())', async () => {
  const helper = loadHelper({ pageToken: 'PAGE-TOKEN' });
  await settle();

  // What $("#dynamic-content").load() inserts: a container whose descendants
  // include the eForm group forms. CSRFGuard's observer sees only the div.
  const panel = helper.dom.document.createElement('div');
  const table = helper.dom.document.createElement('table');
  const removeFromGroup = buildForm(helper.dom, {
    action: '/carlos/eforms/removeFromGroup', inputs: [['fid', '5'], ['groupName', 'G']],
  });
  const delGroup = buildForm(helper.dom, { action: '/carlos/eforms/delGroup', inputs: [['group_name', 'G']] });
  table.appendChild(removeFromGroup);
  table.appendChild(delGroup);
  panel.appendChild(table);
  helper.dom.document.body.appendChild(panel);
  await settle();

  for (const form of [removeFromGroup, delGroup]) {
    const tokens = form.querySelectorAll('input[name="CSRF-TOKEN"]').map((input) => input.value);
    assert.deepEqual(tokens, ['PAGE-TOKEN'], `${form.getAttribute('action')} carries the token`);
  }
});

test('injectIntoForms skips GET, javascript: and cross-origin forms', async () => {
  const helper = loadHelper({ pageToken: 'PAGE-TOKEN' });
  const container = helper.dom.document.createElement('div');
  const getForm = buildForm(helper.dom, { method: 'get', action: '/carlos/search' });
  const scriptForm = buildForm(helper.dom, { action: 'javascript:void(0)' });
  const foreignForm = buildForm(helper.dom, { action: 'https://elsewhere.example/' });
  const actionless = buildForm(helper.dom, { action: null });
  [getForm, scriptForm, foreignForm, actionless].forEach((form) => container.appendChild(form));

  const count = await helper.window.CarlosCsrf.injectIntoForms(container);

  assert.equal(count, 1, 'only the action-less POST (which posts back to this page) qualifies');
  assert.equal(getForm.querySelector('input[name="CSRF-TOKEN"]'), null);
  assert.equal(scriptForm.querySelector('input[name="CSRF-TOKEN"]'), null);
  assert.equal(foreignForm.querySelector('input[name="CSRF-TOKEN"]'), null);
  assert.equal(actionless.querySelector('input[name="CSRF-TOKEN"]').value, 'PAGE-TOKEN');
});

test('a form whose controls have numeric names gets the token (dx code search)', async () => {
  const helper = loadHelper({ pageToken: 'PAGE-TOKEN' });
  const form = buildForm(helper.dom, {
    action: '/carlos/billing/CA/BC/ViewBillingDigUpdate',
    inputs: [['250', 'Diabetes mellitus'], ['401', 'Hypertension']],
  });

  await helper.window.CarlosCsrf.injectIntoForms(form);

  assert.equal(form.querySelector('input[name="CSRF-TOKEN"]').value, 'PAGE-TOKEN');
});

test('a page whose forms are all tokenised makes no servlet request', async () => {
  const helper = loadHelper({ pageToken: 'PAGE-TOKEN' });
  await settle();
  const form = buildForm(helper.dom, { action: '/carlos/y', inputs: [['CSRF-TOKEN', 'PAGE-TOKEN']] });
  helper.dom.document.body.appendChild(form);
  await settle();

  assert.equal(helper.fetches.length, 0);
});

test('forms the parser inserts are left to CSRFGuard: nothing is fetched while the page loads', async () => {
  const helper = loadHelper({ readyState: 'loading' });
  // A static form arriving during parse, before CSRFGuard's DOMContentLoaded
  // pass has tokenised it. Reacting here would cost a request per page load.
  const staticForm = buildForm(helper.dom, { action: '/carlos/messenger/HandleMessages' });
  helper.dom.document.body.appendChild(staticForm);
  await settle();
  assert.equal(helper.fetches.length, 0);

  // CSRFGuard's own pass, then DOMContentLoaded reaches the helper.
  const token = helper.dom.document.createElement('input');
  token.setAttribute('name', 'CSRF-TOKEN');
  token.value = 'PAGE-TOKEN';
  staticForm.appendChild(token);
  helper.dom.document.fire('DOMContentLoaded');
  await settle();
  assert.equal(helper.fetches.length, 0, 'a page CSRFGuard fully tokenised costs no request');

  // After load, an injected panel form is still picked up.
  const panel = helper.dom.document.createElement('div');
  const panelForm = buildForm(helper.dom, { action: '/carlos/eforms/delGroup' });
  panel.appendChild(panelForm);
  helper.dom.document.body.appendChild(panel);
  await settle();
  assert.equal(panelForm.querySelector('input[name="CSRF-TOKEN"]').value, 'PAGE-TOKEN');
});

test('a form CSRFGuard missed at load is tokenised by the deferred initial pass', async () => {
  const helper = loadHelper({ readyState: 'loading', pageToken: 'PAGE-TOKEN' });
  // e.g. the BC dx search: CSRFGuard threw on the first form and stopped.
  const missed = buildForm(helper.dom, {
    action: '/carlos/billing/CA/BC/ViewBillingDigUpdate', inputs: [['250', 'Diabetes']],
  });
  helper.dom.document.body.appendChild(missed);
  helper.dom.document.fire('DOMContentLoaded');
  await settle();

  assert.equal(missed.querySelector('input[name="CSRF-TOKEN"]').value, 'PAGE-TOKEN');
});

test('a failed automatic injection warns but never alerts', async () => {
  const helper = loadHelper({ fetchImpl: async () => ({ ok: false, status: 403 }) });
  await settle();
  helper.dom.document.body.appendChild(buildForm(helper.dom, { action: '/carlos/z' }));
  await settle();

  assert.equal(helper.alerts.length, 0, 'nobody clicked anything');
  assert.ok(helper.warnings.some((line) => /could not be added/.test(line)));
});

test('a <base href> on another origin keeps the token off a root-relative action', async () => {
  // Resolution must follow the browser's: '/carlos/mcedt/update' under a
  // cross-origin <base> goes to that origin, so it must not get the token.
  const helper = loadHelper({ pageToken: 'PAGE-TOKEN', baseURI: 'https://elsewhere.example/carlos/' });
  const form = buildForm(helper.dom, { action: '/carlos/mcedt/update' });
  helper.dom.document.body.appendChild(form);

  await helper.window.carlosSubmitForm(form);

  assert.equal(helper.dom.submissions()[0].fields['CSRF-TOKEN'], undefined);
});

test('a same-origin <base href> still gets the token', async () => {
  const helper = loadHelper({ pageToken: 'PAGE-TOKEN', baseURI: `${ORIGIN}/carlos/` });
  const form = buildForm(helper.dom, { action: 'mcedt/update' });
  helper.dom.document.body.appendChild(form);

  await helper.window.carlosSubmitForm(form);

  assert.deepEqual(helper.dom.submissions()[0].fields['CSRF-TOKEN'], ['PAGE-TOKEN']);
});

/* ------------------------------------------------------------------------ */
/* Native submissions while the lookup is pending                           */
/* ------------------------------------------------------------------------ */

test('a native submit during a pending lookup is held, then replayed with its submitter', async () => {
  let release;
  const helper = loadHelper({
    fetchImpl: () => new Promise((resolve) => {
      release = () => resolve({ ok: true, text: async () => SERVLET_JS });
    }),
  });
  await settle();
  const form = buildForm(helper.dom, { action: '/carlos/billing/CA/BC/ViewBillingDigUpdate' });
  const button = helper.dom.document.createElement('input');
  button.setAttribute('type', 'submit');
  button.setAttribute('name', 'update');
  button.value = 'Update 250';
  button.form = form;
  form.appendChild(button);
  helper.dom.document.body.appendChild(form);

  // The user clicks before any token exists.
  form.requestSubmit(button);
  assert.equal(helper.dom.submissions().length, 0, 'the token-less POST was held back');

  release();
  await settle();
  const [submission] = helper.dom.submissions();
  assert.ok(submission, 'the held submission went out once the token arrived');
  assert.deepEqual(submission.fields['CSRF-TOKEN'], ['SERVLET-TOKEN']);
  assert.deepEqual(submission.fields.update, ['Update 250'], 'the clicked button\'s value survives the replay');
});

test('a native submit whose lookup fails is stopped and the user is told', async () => {
  const helper = loadHelper({ fetchImpl: async () => ({ ok: false, status: 503 }) });
  await settle();
  const form = buildForm(helper.dom, { action: '/carlos/eforms/delGroup' });
  helper.dom.document.body.appendChild(form);

  form.requestSubmit();
  await settle();

  assert.equal(helper.dom.submissions().length, 0);
  assert.equal(helper.alerts.length, 1);
});

test('a native submit with a token on the page goes straight through', async () => {
  const helper = loadHelper({ pageToken: 'PAGE-TOKEN' });
  await settle();
  const form = buildForm(helper.dom, { action: '/carlos/eforms/removeFromGroup' });
  helper.dom.document.body.children.push(form);
  form.parentNode = helper.dom.document.body; // inserted without the observer seeing it

  form.requestSubmit();

  assert.deepEqual(helper.dom.submissions()[0].fields['CSRF-TOKEN'], ['PAGE-TOKEN']);
  assert.equal(helper.fetches.length, 0);
});

test('a fetched token never seeds an empty token field in a GET or foreign form', async () => {
  const helper = loadHelper();
  const standalone = helper.dom.document.createElement('input'); // csrf-token.jspf's
  standalone.setAttribute('name', 'CSRF-TOKEN');
  helper.dom.document.body.appendChild(standalone);
  const getForm = buildForm(helper.dom, { method: 'get', action: '/carlos/search', inputs: [['CSRF-TOKEN', '']] });
  const foreign = buildForm(helper.dom, { action: 'https://elsewhere.example/x', inputs: [['CSRF-TOKEN', '']] });
  const ours = buildForm(helper.dom, { action: '/carlos/y', inputs: [['CSRF-TOKEN', '']] });
  [getForm, foreign, ours].forEach((form) => helper.dom.document.body.appendChild(form));

  await helper.window.CarlosCsrf.token();

  assert.equal(standalone.value, 'SERVLET-TOKEN');
  assert.equal(ours.querySelector('input[name="CSRF-TOKEN"]').value, 'SERVLET-TOKEN');
  assert.equal(getForm.querySelector('input[name="CSRF-TOKEN"]').value, '', 'would leak into a URL');
  assert.equal(foreign.querySelector('input[name="CSRF-TOKEN"]').value, '', 'would leak to another host');
});

/** A submit button on `form`, optionally overriding its action or method. */
function submitButton(helper, form, attributes = {}) {
  const button = helper.dom.document.createElement('button');
  button.setAttribute('type', 'submit');
  Object.entries(attributes).forEach(([name, value]) => button.setAttribute(name, value));
  button.form = form;
  form.appendChild(button);
  return button;
}

test('a submitter whose formaction points at another origin carries no token', async () => {
  const helper = loadHelper({ pageToken: 'PAGE-TOKEN' });
  await settle();
  const form = buildForm(helper.dom, { action: '/carlos/y', inputs: [['CSRF-TOKEN', 'PAGE-TOKEN']] });
  const away = submitButton(helper, form, { formaction: 'https://elsewhere.example/collect' });
  helper.dom.document.body.appendChild(form);

  form.requestSubmit(away);

  const [submission] = helper.dom.submissions();
  assert.deepEqual(submission.fields['CSRF-TOKEN'], [''], 'the token already in the form was blanked');
});

test('a submitter with formmethod="get" carries no token into the URL', async () => {
  const helper = loadHelper({ pageToken: 'PAGE-TOKEN' });
  await settle();
  const form = buildForm(helper.dom, { action: '/carlos/y', inputs: [['CSRF-TOKEN', 'PAGE-TOKEN']] });
  const viaGet = submitButton(helper, form, { formmethod: 'get' });
  helper.dom.document.body.appendChild(form);

  form.requestSubmit(viaGet);

  assert.deepEqual(helper.dom.submissions()[0].fields['CSRF-TOKEN'], ['']);
});

test('after a blanked override, an ordinary submit of the same form is re-tokenised', async () => {
  const helper = loadHelper({ pageToken: 'PAGE-TOKEN' });
  await settle();
  const form = buildForm(helper.dom, { action: '/carlos/y', inputs: [['CSRF-TOKEN', 'PAGE-TOKEN']] });
  const viaGet = submitButton(helper, form, { formmethod: 'get' });
  const save = submitButton(helper, form);
  helper.dom.document.body.appendChild(form);

  form.requestSubmit(viaGet);
  form.requestSubmit(save);

  assert.deepEqual(helper.dom.submissions()[1].fields['CSRF-TOKEN'], ['PAGE-TOKEN']);
});

/* ------------------------------------------------------------------------ */
/* Context path                                                             */
/* ------------------------------------------------------------------------ */

test('the context path falls back to the first path segment when the script URL is unknown', async () => {
  // jQuery .load() evaluates a fragment's scripts with no document.currentScript.
  const helper = loadHelper();

  await helper.window.carlosPostForm('/carlos/x', {});

  assert.deepEqual(helper.fetches, ['/carlos/csrfguard']);
});

test('window.carlosContextPath pins the context path', async () => {
  const helper = loadHelper({ scriptSrc: `${ORIGIN}/carlos/share/javascript/carlosCsrfForm.js` });
  helper.window.carlosContextPath = '/emr';

  await helper.window.carlosPostForm('/emr/x', {});

  assert.deepEqual(helper.fetches, ['/emr/csrfguard']);
});
