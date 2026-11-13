/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
// Limits per model class, read from the Java sources the fragment prints them from.
const limitsOf = className => {
  const model = fs.readFileSync(path.join(__dirname,`../src/main/java/io/github/carlos_emr/carlos/commn/model/${className}.java`),'utf8');
  return Object.fromEntries([...model.matchAll(/public static final int ([A-Z_]+_MAX_LENGTH) = (\d+);/g)]
    .map(([,name,value]) => [name,Number(value)]));
};
const limitsByClass = {Demographic: limitsOf('Demographic'), DemographicCust: limitsOf('DemographicCust'),
  WaitingList: limitsOf('WaitingList')};
const fragment = fs.readFileSync(path.join(__dirname,'../src/main/webapp/WEB-INF/jspf/demographic-field-length-limits.jspf'),'utf8');
// Extract the one known source block; this test does not filter or sanitize HTML.
const scriptOpeningTag = '<script type="text/javascript">';
const scriptStart = fragment.indexOf(scriptOpeningTag);
const scriptEnd = fragment.indexOf('</script>', scriptStart + scriptOpeningTag.length);
assert.notEqual(scriptStart, -1, 'Expected the shared fragment script block');
assert.ok(scriptEnd > scriptStart, 'Expected the shared fragment script closing marker');
assert.equal(fragment.indexOf('<script', scriptStart + scriptOpeningTag.length), -1,
  'Expected exactly one shared fragment script block');
const script = fragment.slice(scriptStart + scriptOpeningTag.length, scriptEnd)
  .replace(/<%= (?:io\.github\.carlos_emr\.carlos\.commn\.model\.)?(Demographic|DemographicCust|WaitingList)\.([A-Z_]+) %>/g,
    (_,className,name) => {
      assert.ok(Number.isInteger(limitsByClass[className][name]), `Unknown ${className} field limit: ${name}`);
      return String(limitsByClass[className][name]);
    })
  .replace('${carlos:forJavaScript(demographicCodePointLimitMessage)}','Maximum {0} characters.');
assert.doesNotMatch(script, /<%/, 'Every server expression in the script block is substituted');
// Runs the fragment against one fake field and returns it with its listeners and attributes.
const applyTo = (fieldName, initial) => {
  const handlers={}; const formHandlers={}; const attributes={};
  const field = {value:initial,form:{addEventListener:(name,fn)=>{formHandlers[name]=fn;}},
    setAttribute:(name,value)=>{attributes[name]=value;},setCustomValidity(message){this.error=message;},
    addEventListener:(name,fn)=>{handlers[name]=fn;},reportValidity(){this.reported=true;}};
  const document={querySelectorAll:selector=>selector.includes(`name="${fieldName}"`)?[field]:[],addEventListener(){}};
  const context={document};vm.createContext(context);vm.runInContext(script,context);context.applyDemographicFieldLengthLimits();
  return {field, handlers, formHandlers, attributes};
};
// The patient alert (edit: alert, add: cust3) and the waiting-list note are VARCHAR(255) (#5869).
const fieldLimits = {first_name: 50, last_name: 50, pronouns: 25, gender: 25,
  alert: 255, cust3: 255, waiting_list_note: 255};
for (const [fieldName, maximum] of Object.entries(fieldLimits)) {
  test(`${fieldName} preserves ${maximum} supplementary characters and refuses ${maximum + 1} without truncation`, () => {
    const initial = '😀'.repeat(maximum);
    const {field, handlers, formHandlers, attributes} = applyTo(fieldName, initial);
    assert.equal(field.value,initial);assert.equal(field.error,'');assert.equal(attributes.maxlength,maximum * 2);
    assert.equal(attributes['data-max-codepoints'],maximum);
    field.value+='😀';handlers.input();assert.equal(field.error,`Maximum ${maximum} characters.`);
    let prevented=false;formHandlers.submit({preventDefault(){prevented=true;}});
    assert.equal(prevented,true);assert.equal(field.reported,true);assert.equal(field.value,'😀'.repeat(maximum + 1));
    field.value='A'.repeat(maximum);handlers.input();assert.equal(field.error,'');
    field.value+='A';handlers.input();assert.notEqual(field.error,'');
  });
}
for (const fieldName of ['alert','cust3']) {
  test(`${fieldName} counts each line break as the CR LF the browser submits`, () => {
    // 253 letters and one line break are 255 characters once submitted; a second break makes 257.
    const {field, handlers} = applyTo(fieldName, 'A'.repeat(126) + '\n' + 'A'.repeat(127));
    assert.equal(field.error,'');
    field.value = 'A'.repeat(126) + '\n' + 'A'.repeat(126) + '\n' + 'A';handlers.input();
    assert.equal(field.error,'Maximum 255 characters.');
    field.value = 'A'.repeat(126) + '\r\n' + 'A'.repeat(127);handlers.input();
    assert.equal(field.error,'');
  });
}
