/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('src/main/webapp/encounter/js/encounter.js', 'utf8');

function fixture(action) {
  const calls = [];
  const context = {
    encounterConfig: {}, document: {getElementById() { return {value: 'Form (new)'}; }},
    addEventListener() {}, setTimeout() {},
    openEncounterForm(...args) { calls.push(['guard', ...args]); },
  };
  context.window = context;
  vm.createContext(context);
  vm.runInContext(source, context);
  context.autoCompleted['Form (new)'] = action;
  context.popupPage = (...args) => calls.push(['popup', ...args]);
  context.ajaxInsertTemplate = (...args) => calls.push(['template', ...args]);
  return {calls, run: () => context.menuAction()};
}

test('legacy autocomplete dispatches form JSON without changing patient, punctuation or Unicode', () => {
  const args = ['/clinic/form/formcaregiver?demographic_no=123&appointmentNo=11', '123new', 'Caregiver\'s, "record" \\ suivi é'];
  const f = fixture(`openEncounterForm.apply(null,${JSON.stringify(args)});`);
  f.run();
  assert.deepEqual(f.calls, [['guard', ...args]]);
});

for (const action of [
  'openEncounterForm.apply(null,notJson);',
  'openEncounterForm.apply(null,{"0":"url","1":"win","2":"name"});',
  'openEncounterForm.apply(null,["url","win"]);',
  'openEncounterForm.apply(null,["url","win",123]);',
  'openEncounterForm.apply(null,["url","win","name"]);throw new Error("executed");',
]) {
  test(`legacy autocomplete rejects malformed form action: ${action}`, () => {
    const f = fixture(action);
    assert.doesNotThrow(f.run);
    assert.deepEqual(f.calls, []);
  });
}

test('legacy autocomplete retains calculator dispatch', () => {
  const f = fixture("popupPage(700,960,'calculator','/clinic/calculator?a=1,b=2')");
  f.run();
  assert.deepEqual(f.calls, [['popup', 700, 960, 'calculator', '/clinic/calculator?a=1,b=2']]);
});

test('legacy autocomplete retains encounter template insertion', () => {
  const f = fixture("ajaxInsertTemplate('Own template')");
  f.run();
  assert.deepEqual(f.calls, [['template', 'Own template']]);
});
