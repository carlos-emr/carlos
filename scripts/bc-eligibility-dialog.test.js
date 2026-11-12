/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const bindEligibilityDialog = require('../src/main/webapp/demographic/eligibilityDialog');

function textNode(text) { return {nodeType: 3, nodeValue: text, textContent: text}; }
function parsedElement(tagName, ...childNodes) { return {nodeType: 1, tagName, childNodes}; }
function element(dataset = {}) {
  return {
    dataset, attributes: {}, listeners: {}, childNodes: [], style: {}, hidden: false,
    get textContent() { return this.childNodes.map(child => child.textContent).join(''); },
    set textContent(value) { this.childNodes = value ? [textNode(value)] : []; },
    set innerHTML(_) { throw new Error('Eligibility must never insert response HTML'); },
    appendChild(node) { this.childNodes.push(node); return node; },
    replaceChildren(fragment) { this.childNodes = [...fragment.childNodes]; },
    addEventListener(type, listener) { this.listeners[type] = listener; },
    setAttribute(name, value) { this.attributes[name] = value; },
    focus() { this.focused = true; },
    fire(type, key) {
      const event = { key, prevented: false, preventDefault() { this.prevented = true; } };
      this.listeners[type]?.(event);
      return event;
    },
  };
}

function setup({ permitted = true, parsedNodes } = {}) {
  const trigger = element();
  const panel = element({ url: '/carlos/billing/CA/BC/ManageTeleplan', demographic: '123',
    title: 'Check Eligibility', close: 'Close', error: 'Request failed <please retry>' });
  const spinner = element();
  const result = element();
  const elements = { checkEligibility: trigger, menu2: panel, search_spinner: spinner, returnTeleplanMsg: result };
  // Unit-test the allowlist on parsed nodes; the browser harness exercises real HTML parsing.
  const document = { getElementById: id => permitted ? elements[id] : null,
    createTextNode: textNode, createDocumentFragment: () => element(),
    createElement: name => Object.assign(element(), {nodeType: 1, tagName: name.toUpperCase()}),
    defaultView: {DOMParser: class {
      parseFromString(html, type) {
        assert.equal(type, 'text/html');
        return {body: {childNodes: parsedNodes || [textNode(html)]}};
      }
    }},
  };
  const requests = [];
  let options;
  let visible = false;
  const dialog = { dialog(arg) {
    if (typeof arg === 'object') { options = arg; visible = arg.autoOpen; }
    if (arg === 'open') visible = true;
    return dialog;
  } };
  const $ = () => dialog;
  $.ajax = (config) => {
    const callbacks = {};
    const request = { config,
      done(fn) { callbacks.done = fn; return request; },
      fail(fn) { callbacks.fail = fn; return request; },
      always(fn) { callbacks.always = fn; return request; },
      settle(success, html) { callbacks[success ? 'done' : 'fail'](html); callbacks.always(); },
    };
    requests.push(request);
    return request;
  };
  bindEligibilityDialog(document, $);
  return { trigger, panel, spinner, result, requests,
    visible: () => visible, close() { visible = false; options.close(); } };
}

test('shouldLeaveEligibilityClosed_withoutActivationOrOnHover', () => {
  const s = setup();
  s.trigger.fire('mouseover');
  s.trigger.fire('mouseenter');
  s.trigger.fire('focus');
  assert.equal(s.visible(), false);
  assert.equal(s.requests.length, 0);
});

test('shouldDoNothing_whenServerOmitsThePermittedBCControl', () => {
  assert.equal(setup({ permitted: false }).requests.length, 0);
});

test('shouldPostEligibilityParametersAndRenderResult_onExplicitClick', () => {
  const s = setup();
  assert.equal(s.trigger.fire('click').prevented, true);
  assert.equal(s.visible(), true);
  assert.equal(s.panel.attributes['aria-busy'], 'true');
  assert.equal(s.trigger.attributes['aria-expanded'], 'true');
  assert.equal(s.spinner.hidden, false);
  assert.equal(s.requests.length, 1);
  assert.deepEqual(s.requests[0].config, {
    url: '/carlos/billing/CA/BC/ManageTeleplan', method: 'POST', dataType: 'text', cache: false,
    timeout: 30000, data: { demographic: '123', method: 'checkElig' },
  });
  s.requests[0].settle(true, 'Eligible (test response)');
  assert.equal(s.result.textContent, 'Eligible (test response)');
  assert.equal(s.spinner.hidden, true);
  assert.equal(s.panel.attributes['aria-busy'], 'false');
});

test('shouldSharePendingRequest_whenClickedAgainOrReopened', () => {
  const s = setup();
  s.trigger.fire('click');
  s.trigger.fire('click');
  s.close();
  assert.equal(s.trigger.attributes['aria-expanded'], 'false');
  assert.equal(s.trigger.focused, true);
  s.trigger.fire('click');
  assert.equal(s.visible(), true);
  assert.equal(s.requests.length, 1);
  s.close();
  s.requests[0].settle(true, 'Eligible');
  assert.equal(s.visible(), false, 'A late response must not reopen a dismissed dialog');
});

test('shouldAllowRetryAndEndLoading_whenRequestFailsOrTimesOut', () => {
  const s = setup();
  s.trigger.fire('click');
  s.requests[0].settle(false);
  assert.equal(s.result.textContent, s.panel.dataset.error);
  assert.equal(s.result.childNodes[0].nodeType, 3, 'Error text must not be interpreted as markup');
  assert.equal(s.spinner.hidden, true);
  assert.equal(s.panel.attributes['aria-busy'], 'false');
  s.close();
  s.trigger.fire('click');
  assert.equal(s.requests.length, 2);
  assert.equal(s.result.textContent, '');
  assert.equal(s.spinner.hidden, false);
});

test('shouldCheckAgain_whenExplicitlyActivatedAfterCompletion', () => {
  const s = setup();
  s.trigger.fire('click');
  assert.equal(s.requests.length, 1);
  s.requests[0].settle(true, 'Eligible');
  s.close();
  s.trigger.fire('click');
  assert.equal(s.requests.length, 2);
  assert.equal(s.panel.attributes['aria-busy'], 'true');
});


test('shouldPreserveDecodedTextBreaksAndDenialHighlight_fromTheServerFragmentContract', () => {
  const s = setup({parsedNodes: [textNode('Failure-claim <unsafe> & "quoted"'), parsedElement('BR'),
    parsedElement('SPAN', textNode('ELIG_ON_DOS: no')), parsedElement('BR'), textNode('Last line 🩺')]});
  s.trigger.fire('click');
  s.requests[0].settle(true, 'parsed response fixture');
  assert.equal(s.result.textContent, 'Failure-claim <unsafe> & "quoted"ELIG_ON_DOS: noLast line 🩺');
  assert.deepEqual(s.result.childNodes.map(node => node.tagName || '#text'), ['#text', 'BR', 'SPAN', 'BR', '#text']);
  assert.deepEqual(s.result.childNodes[2].style, {color: 'red', fontWeight: 'bold'});
});

test('shouldRebuildOnlyContractNodes_withoutAdoptingResponseAttributesOrActiveContent', () => {
  const unsafeSpan = Object.assign(parsedElement('SPAN', textNode('ELIG_ON_DOS: no'),
    parsedElement('IMG'), parsedElement('SCRIPT', textNode('malicious()'))),
  {attributes: {onclick: 'malicious()', style: 'background:url(/leak)', id: 'checkEligibility'}});
  const s = setup({parsedNodes: [textNode('Success-safe message'), unsafeSpan,
    parsedElement('SCRIPT', textNode('malicious()')), parsedElement('SVG', parsedElement('SPAN', textNode('unsafe'))),
    parsedElement('IFRAME'), parsedElement('STYLE', textNode('body{display:none}')), parsedElement('IMG'),
    {nodeType: 8, nodeValue: 'comment'}, textNode('remaining text')]});
  s.trigger.fire('click');
  s.requests[0].settle(true, 'parsed malicious response fixture');
  assert.equal(s.result.textContent, 'Success-safe messageELIG_ON_DOS: noremaining text');
  assert.deepEqual(s.result.childNodes.map(node => node.tagName || '#text'), ['#text', 'SPAN', '#text']);
  assert.notEqual(s.result.childNodes[1], unsafeSpan);
  assert.deepEqual(s.result.childNodes[1].attributes, {});
  assert.deepEqual(s.result.childNodes[1].style, {color: 'red', fontWeight: 'bold'});
  assert.equal(s.result.childNodes[1].childNodes.length, 1);
});
