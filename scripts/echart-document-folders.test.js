/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/newCaseManagementView.js.jsp'), 'utf8');
const listDisplay = source.slice(source.indexOf('    function listDisplay('), source.indexOf('    function clickListDisplay('));
const adjustStart = source.indexOf('        this.adjust = function');
const adjust = source.slice(adjustStart, source.indexOf('\n        };', adjustStart) + '\n        };'.length);

function harness() {
  const rows = Array.from({length: 6}, () => ({style: {display: 'block'}}));
  const folderRows = Array.from({length: 3}, () => ({style: {display: 'block'}}));
  const folderHeaders = [{style: {display: 'block'}}, {style: {display: 'block'}}];
  const folderItems = [folderHeaders[0], folderRows[0], folderRows[1], folderHeaders[1], folderRows[2]];
  const elements = {
    docsnum: {value: '2'}, labsnum: {value: '6'}, docsthreshold: {value: '6'}, labsthreshold: {value: '6'},
    docslist: {
      getAttribute: () => 'true',
      getElementsByTagName: () => folderItems,
    },
    labslist: {getAttribute: () => 'false', getElementsByTagName: () => rows},
  };
  for (let i = 0; i < rows.length; i++) elements['imglabs' + i] = {src: '', title: ''};
  // There are three row images, but five descendant li elements including the headers.
  for (let i = 0; i < folderRows.length; i++) elements['imgdocs' + i] = {src: '', title: ''};
  const lookups = [];
  const context = {
    ctx: '/carlos', imgfunc: {}, obj: {},
    $: id => {lookups.push(id); return elements[id] || null;},
    $F: id => elements[id].value, $A: value => Array.from(value),
    Element: {observe() {}, stopObserving() {}},
    clickListDisplay: Object.assign(function () {}, {bindAsEventListener: () => function () {}}),
  };
  vm.createContext(context);
  vm.runInContext(listDisplay + '\nvar loader = {}; (function () {' + adjust + '}).call(loader);', context);
  return {context, elements, rows, folderItems, lookups};
}

test('overflow leaves folder disclosures alone and still adjusts later panels', () => {
  const h = harness();
  const docs = {id: 'docs', style: {visibility: 'hidden'}};
  const labs = {id: 'labs', style: {visibility: 'hidden'}};

  h.context.loader.adjust([docs, labs], 10, 4);

  assert.equal(docs.style.visibility, 'visible');
  assert.equal(labs.style.visibility, 'visible');
  assert.equal(h.elements.docsthreshold.value, '6');
  assert.ok(h.folderItems.every(item => item.style.display === 'block'));
  assert.ok(!h.lookups.some(id => id.startsWith('imgdocs')));
  assert.deepEqual(h.rows.map(row => row.style.display), ['block', 'block', 'block', 'none', 'none', 'none']);
  assert.equal(h.elements.imglabs2.src, '/carlos/encounter/graphics/expand.gif');
});

test('flat panels retain their collapse and expand controls', () => {
  const h = harness();
  h.context.listDisplay('labs', 3);
  assert.deepEqual(h.rows.map(row => row.style.display), ['block', 'block', 'block', 'none', 'none', 'none']);
  h.context.listDisplay('labs', 3);
  assert.ok(h.rows.every(row => row.style.display === 'block'));
  assert.equal(h.elements.imglabs0.src, '/carlos/messenger/img/collapse.gif');
  assert.equal(h.elements.imglabs5.src, '/carlos/messenger/img/collapse.gif');
});
