/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

for (const asset of ['src/main/webapp/WEB-INF/eform-assets/editControl2.js', 'src/main/webapp/library/eforms/editControl.js', 'release/editControl2.js']) {
  const source = fs.readFileSync(path.join(__dirname, '..', asset), 'utf8');
  const code = source.slice(source.indexOf('function setLetterTemplateSubject('), source.indexOf('function loadDefaultTemplate('));
  test(`${asset}: automatic templates preserve a saved or typed subject, including punctuation`, () => {
    const subject = {value: 'Follow-up & "results" <test>', dispatchEvent() { throw new Error('must not overwrite'); }};
    const context = vm.createContext({document: {getElementById: () => subject}});
    vm.runInContext(code, context);
    context.setLetterTemplateSubject('blank.rtl', true);
    context.setLetterTemplateSubject('Referral.rtl', true);
    assert.equal(subject.value, 'Follow-up & "results" <test>');
  });
  test(`${asset}: template subject changes dispatch input in engines without an Event constructor`, () => {
    const events = [];
    const subject = {value: '', dispatchEvent: event => events.push(event)};
    const document = {
      getElementById: () => subject,
      createEvent: type => {
        assert.equal(type, 'Event');
        return {initEvent(name, bubbles, cancelable) { Object.assign(this, {type: name, bubbles, cancelable}); }};
      },
    };
    const context = vm.createContext({document});
    vm.runInContext(code, context);
    context.setLetterTemplateSubject('Referral.rtl', true);
    assert.equal(subject.value, 'Referral');
    assert.equal(events.length, 1);
    assert.equal(events[0].type, 'input');
    assert.equal(events[0].bubbles, true);
    assert.equal(events[0].cancelable, false);
  });
  test(`${asset}: defaults initialize an empty subject and explicit choices update the toolbar`, () => {
    const events = [];
    const subject = {value: '', dispatchEvent: event => events.push(event)};
    const context = vm.createContext({document: {getElementById: () => subject}, Event});
    vm.runInContext(code, context);
    context.setLetterTemplateSubject('Referral.rtl', true);
    assert.equal(subject.value, 'Referral');
    context.setLetterTemplateSubject('blank.rtl', false);
    assert.equal(subject.value, '');
    context.setLetterTemplateSubject('Follow-up.rtl', false);
    assert.equal(subject.value, 'Follow-up');
    assert.equal(events.length, 3);
    assert.ok(events.every(event => event.type === 'input' && event.bubbles));
    const defaultLoader = source.slice(source.indexOf('function loadDefaultTemplate('), source.indexOf('function loadTemplate('));
    assert.match(defaultLoader, /setLetterTemplateSubject\(selected, true\)/);
    assert.match(source.slice(source.indexOf('function loadTemplate(')), /setLetterTemplateSubject\(selected, false\)/);
  });
  test(`${asset}: the default template keeps a subject typed into the toolbar before it loads`, () => {
    const subject = {value: '', dispatchEvent() { throw new Error('must not overwrite the toolbar'); }};
    const toolbar = {value: 'Typed & "early" <subject>'};
    const byId = {subject, remote_eform_subject: toolbar};
    const context = vm.createContext({document: {getElementById: id => byId[id] || null}, Event});
    vm.runInContext(code, context);
    context.setLetterTemplateSubject('blank.rtl', true);
    context.setLetterTemplateSubject('Referral.rtl', true);
    assert.equal(subject.value, '');
    assert.equal(toolbar.value, 'Typed & "early" <subject>');
  });
  test(`${asset}: an unchanged default subject dispatches nothing, an explicit choice still syncs`, () => {
    const events = [];
    const subject = {value: '', dispatchEvent: event => events.push(event)};
    const byId = {subject, remote_eform_subject: {value: ''}};
    const context = vm.createContext({document: {getElementById: id => byId[id] || null}, Event});
    vm.runInContext(code, context);
    context.setLetterTemplateSubject('blank.rtl', true);
    assert.equal(events.length, 0);
    byId.remote_eform_subject.value = 'Typed later';
    context.setLetterTemplateSubject('Referral.rtl', false);
    assert.equal(subject.value, 'Referral');
    assert.equal(events.length, 1);
  });
}

test('the asynchronously loaded toolbar receives the current subject after its input exists', () => {
  const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/eform/eformFloatingToolbar/eform_floating_toolbar.js'), 'utf8');
  const include = source.slice(source.indexOf('function includeHTML('), source.indexOf('function addNavElement('));
  const reverse = source.slice(source.indexOf('function moveSubjectReverse('), source.indexOf('function closeToolbar('));
  let request;
  let remote;
  const subject = {value: 'Saved & "quoted" subject'};
  const context = vm.createContext({
    document: {
      forms: [{elements: {subject}}],
      getElementById: () => remote,
      createElement: () => ({setAttribute() {}}),
    },
    XMLHttpRequest: function () { request = this; this.open = () => {}; this.send = () => {}; },
    hideAdminPreviewSaveButton() {}, handleEmailPrivilege() {},
    jQuery: () => ({empty: () => ({append() {}}), length: 0}),
  });
  vm.runInContext(include + reverse, context);
  context.moveSubjectReverse(); // input can fire before the toolbar response arrives
  context.includeHTML({append() { remote = {value: ''}; }});
  assert.equal(remote, undefined);
  request.readyState = 4;
  request.status = 200;
  request.responseText = '<input id="remote_eform_subject">';
  request.onreadystatechange();
  assert.equal(remote.value, subject.value);
});

function loadMoveSubject(remote, subject) {
  const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/eform/eformFloatingToolbar/eform_floating_toolbar.js'), 'utf8');
  const move = source.slice(source.indexOf('function moveSubject('), source.indexOf('function moveSubjectReverse('));
  const context = vm.createContext({
    document: {forms: [{elements: {subject}}], getElementById: id => (id === 'remote_eform_subject' ? remote : null)},
  });
  vm.runInContext(move, context);
  return context;
}

test('saving without a toolbar input keeps the form subject instead of writing "undefined"', () => {
  const subject = {value: 'Saved & "quoted" subject'};
  loadMoveSubject(null, subject).moveSubject();
  assert.equal(subject.value, 'Saved & "quoted" subject');
});

test('saving copies the toolbar subject into the form verbatim, including an intentional clear', () => {
  const subject = {value: 'Old'};
  const remote = {value: 'Follow-up & <results>'};
  const context = loadMoveSubject(remote, subject);
  context.moveSubject();
  assert.equal(subject.value, 'Follow-up & <results>');
  remote.value = '';
  context.moveSubject();
  assert.equal(subject.value, '');
});
