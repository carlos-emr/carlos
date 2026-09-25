/* Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
'use strict';
// Issue #3901: printing an eForm that dirty detection reports as unedited used to skip the save
// silently. These tests pin the print/save decision in eform_floating_toolbar.js.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const toolbarPath = path.join(__dirname, '../src/main/webapp/eform/eformFloatingToolbar/eform_floating_toolbar.js');
const jspfPath = path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/eform/eformFloatingToolbar/eform_floating_toolbar.jspf');
const source = fs.readFileSync(toolbarPath, 'utf8');
const start = source.indexOf('function remoteSaveOnly() {');
const end = source.indexOf('function hailMary() {');
assert.ok(start > 0 && end > start, 'remoteSaveOnly..hailMary slice must exist');
const previewStart = source.indexOf('function isAdminPreview() {');
const previewEnd = source.indexOf('function hideAdminPreviewSaveButton() {');
assert.ok(previewStart > 0 && previewEnd > previewStart, 'isAdminPreview slice must exist');
const printCode = source.slice(previewStart, previewEnd) + source.slice(start, end);

const FALLBACK = "You haven't manually edited this eForm. Would you like to save a copy to the patient's chart anyway?";

/**
 * Runs the real remotePrint()/print-save helpers against stubs.
 * dirtyDeclaration: JS declaring the eForm's dirty flag, or '' for a form without dirty detection.
 */
function setup({dirtyDeclaration = '', confirmAnswer = true, toolbarMessage, demographicNo = '42'} = {}) {
  const events = [];
  const toolbar = toolbarMessage === undefined ? null : {
    getAttribute: (name) => (name === 'data-print-save-unedited-confirm' ? toolbarMessage : null),
  };
  const context = vm.createContext({
    console: {log() {}},
    document: {
      getElementById: (id) => {
        if (id === 'eform_floating_toolbar') return toolbar;
        if (id === 'demographicNo') return demographicNo === null ? null : {value: demographicNo};
        return null;
      },
      getElementsByName: () => [],
    },
    confirm: (message) => { events.push(['confirm', message]); return confirmAnswer; },
    editorStillLoading: () => false,
    clearWorkflowFlags: () => events.push(['clearWorkflowFlags']),
    formPrint: () => events.push(['print']),
    hailMary: () => events.push(['hailMary']),
    remoteSave: () => { events.push(['save']); return true; },
  });
  vm.runInContext(dirtyDeclaration + '\n' + printCode, context);
  return {context, events, kinds: () => events.map((e) => e[0])};
}

test('printSaveDecision saves edited forms and forms without dirty detection, confirms clean ones', () => {
  const {context} = setup();
  assert.equal(context.printSaveDecision(undefined), 'save');
  assert.equal(context.printSaveDecision(true), 'save');
  assert.equal(context.printSaveDecision(false), 'confirm');
  // Legacy forms occasionally use 0/'' for "clean"; falsy but declared means detection is present.
  assert.equal(context.printSaveDecision(0), 'confirm');
  assert.equal(context.printSaveDecision(''), 'confirm');
});

test('edited form prints then saves once without prompting', () => {
  const f = setup({dirtyDeclaration: 'var needToConfirm = true;'});
  f.context.remotePrint();
  assert.deepEqual(f.kinds(), ['clearWorkflowFlags', 'print', 'save']);
});

test('form without dirty detection keeps saving on every print without prompting', () => {
  const f = setup({dirtyDeclaration: ''});
  f.context.remotePrint();
  assert.deepEqual(f.kinds(), ['clearWorkflowFlags', 'print', 'save']);
});

test('declared-but-undefined dirty flag is treated as no dirty detection, as before', () => {
  const f = setup({dirtyDeclaration: 'var needToConfirm;'});
  f.context.remotePrint();
  assert.deepEqual(f.kinds(), ['clearWorkflowFlags', 'print', 'save']);
});

test('unedited form asks once after printing and saves on OK', () => {
  const f = setup({dirtyDeclaration: 'var needToConfirm = false;', confirmAnswer: true});
  f.context.remotePrint();
  assert.deepEqual(f.kinds(), ['clearWorkflowFlags', 'print', 'confirm', 'save']);
});

test('unedited form still prints but is not saved on Cancel', () => {
  const f = setup({dirtyDeclaration: 'var needToConfirm = false;', confirmAnswer: false});
  f.context.remotePrint();
  assert.deepEqual(f.kinds(), ['clearWorkflowFlags', 'print', 'confirm']);
});

test('dirty flag set with let at eForm top level is honoured', () => {
  const f = setup({dirtyDeclaration: 'let needToConfirm = false;', confirmAnswer: false});
  f.context.remotePrint();
  assert.deepEqual(f.kinds(), ['clearWorkflowFlags', 'print', 'confirm']);
});

test('prompt uses the server-localized toolbar text when present', () => {
  const f = setup({dirtyDeclaration: 'var needToConfirm = false;', toolbarMessage: 'Enregistrer une copie?'});
  f.context.remotePrint();
  assert.equal(f.events.find((e) => e[0] === 'confirm')[1], 'Enregistrer une copie?');
});

test('prompt falls back to English when the toolbar fragment is missing or empty', () => {
  for (const toolbarMessage of [undefined, '', null]) {
    const f = setup({dirtyDeclaration: 'var needToConfirm = false;', toolbarMessage});
    f.context.remotePrint();
    assert.equal(f.events.find((e) => e[0] === 'confirm')[1], FALLBACK);
  }
});

test('loading-editor guard still aborts before printing, prompting or saving', () => {
  const f = setup({dirtyDeclaration: 'var needToConfirm = false;'});
  f.context.editorStillLoading = () => true;
  f.context.remotePrint();
  assert.deepEqual(f.kinds(), []);
});

// efmshowform_data.jsp renders the eForm manager preview with demographic "-1": there is no chart.
test('admin preview prints without prompting or saving, whatever the dirty flag says', () => {
  for (const dirtyDeclaration of ['var needToConfirm = false;', 'var needToConfirm = true;', '']) {
    const f = setup({dirtyDeclaration, demographicNo: '-1'});
    f.context.remotePrint();
    assert.deepEqual(f.kinds(), ['clearWorkflowFlags', 'print'], dirtyDeclaration || 'no dirty detection');
  }
});

test('a page without the demographicNo input keeps the patient print/save behaviour', () => {
  const f = setup({dirtyDeclaration: 'var needToConfirm = true;', demographicNo: null});
  f.context.remotePrint();
  assert.deepEqual(f.kinds(), ['clearWorkflowFlags', 'print', 'save']);
});

test('toolbar fragment publishes the localized prompt as an encoded data attribute', () => {
  const jspf = fs.readFileSync(jspfPath, 'utf8');
  assert.match(jspf, /<fmt:message key="eform\.floatingToolbar\.printSaveUneditedConfirm" var="printSaveUneditedConfirm"\/>/);
  assert.match(jspf, /data-print-save-unedited-confirm="\$\{carlos:forHtmlAttribute\(printSaveUneditedConfirm\)\}"/);
});

test('every locale bundle defines the print-save prompt key', () => {
  const resources = path.join(__dirname, '../src/main/resources');
  for (const locale of ['en', 'es', 'fr', 'pl', 'pt_BR']) {
    const bundle = fs.readFileSync(path.join(resources, `oscarResources_${locale}.properties`), 'utf8');
    assert.match(bundle, /^eform\.floatingToolbar\.printSaveUneditedConfirm=\S/m, locale);
  }
  const en = fs.readFileSync(path.join(resources, 'oscarResources_en.properties'), 'utf8');
  assert.ok(en.includes(`eform.floatingToolbar.printSaveUneditedConfirm=${FALLBACK}\n`),
    'JS English fallback must match the en bundle');
});

test('remoteSaveOnly refuses the manager preview and saves a patient form', () => {
  const preview = setup({demographicNo: '-1'});
  assert.equal(preview.context.remoteSaveOnly(), false);
  assert.deepEqual(preview.kinds(), []);

  const chart = setup();
  assert.equal(chart.context.remoteSaveOnly(), true);
  assert.deepEqual(chart.kinds(), ['clearWorkflowFlags', 'save']);
});
