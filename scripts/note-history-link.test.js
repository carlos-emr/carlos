#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/newCaseManagementView.js.jsp'), 'utf8');
const handler = source.match(/    function showHistory\(noteId, event\) \{[\s\S]*?\n    \}/)[0];
for (const [patient, note] of [['101', '501'], ['101&noteId=999', '501&demographicNo=999']]) {
  test(`history link carries separate patient and note values: ${patient}`, () => {
    let opened;
    const event = {};
    let stopped;
    const sandbox = {ctx: '/carlos', demographicNo: patient, Event: {stop: value => { stopped = value; }},
      window: {open: url => { opened = url; }}};
    vm.createContext(sandbox);
    vm.runInContext(handler, sandbox);
    assert.equal(sandbox.showHistory(note, event), false);
    assert.equal(stopped, event);
    const url = new URL(opened, 'https://example.invalid');
    assert.equal(url.pathname, '/carlos/CaseManagementEntry');
    assert.deepEqual([...url.searchParams], [['method', 'notehistory'], ['noteId', note], ['demographicNo', patient]]);
  });
}

const ticklerSource = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/ticklerNoteDialog.js'), 'utf8');
for (const [patient, note] of [['101', '501'], ['101&noteId=999', '501&demographicNo=999']]) {
  test(`tickler history carries the dialog patient and note: ${patient}`, () => {
    const elements = {
      tickler_note_demographicNo: {value: patient},
      tickler_note_noteId: {value: ''},
      tickler_note_revision_url: {setAttribute(name, value) { this[name] = value; }},
    };
    let opened;
    const sandbox = {document: {getElementById: id => elements[id]}, window: {open: url => { opened = url; }}};
    vm.createContext(sandbox);
    vm.runInContext(ticklerSource, sandbox);
    sandbox.applyTicklerNoteFields({noteId: note}, '/carlos');
    assert.equal(elements.tickler_note_noteId.value, note);
    vm.runInContext(`(function() { ${elements.tickler_note_revision_url.onclick} })()`, sandbox);
    const url = new URL(opened, 'https://example.invalid');
    assert.equal(url.pathname, '/carlos/CaseManagementEntry');
    assert.deepEqual([...url.searchParams], [['method', 'notehistory'], ['noteId', note], ['demographicNo', patient]]);
  });
}
