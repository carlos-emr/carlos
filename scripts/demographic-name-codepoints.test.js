/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
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
  .replace(/<%= Demographic\.[A-Z_]+ %>/g,'50')
  .replace('${carlos:forJavaScript(demographicCodePointLimitMessage)}','Maximum {0} characters.');
for (const fieldName of ['first_name','last_name']) {
  test(`${fieldName} preserves 50 supplementary characters and refuses 51 without truncation`, () => {
    const initial = '😀'.repeat(50); const handlers={}; const formHandlers={}; const attributes={};
    const field = {value:initial,form:{addEventListener:(name,fn)=>{formHandlers[name]=fn;}},
      setAttribute:(name,value)=>{attributes[name]=value;},setCustomValidity(message){this.error=message;},
      addEventListener:(name,fn)=>{handlers[name]=fn;},reportValidity(){this.reported=true;}};
    const document={querySelectorAll:selector=>selector.includes(`name="${fieldName}"`)?[field]:[],addEventListener(){}};
    const context={document};vm.createContext(context);vm.runInContext(script,context);context.applyDemographicFieldLengthLimits();
    assert.equal(field.value,initial);assert.equal(field.error,'');assert.equal(attributes.maxlength,100);
    assert.equal(attributes['data-max-codepoints'],50);
    field.value+='😀';handlers.input();assert.equal(field.error,'Maximum 50 characters.');
    let prevented=false;formHandlers.submit({preventDefault(){prevented=true;}});
    assert.equal(prevented,true);assert.equal(field.reported,true);assert.equal(field.value,'😀'.repeat(51));
    field.value='A'.repeat(50);handlers.input();assert.equal(field.error,'');
    field.value+='A';handlers.input();assert.notEqual(field.error,'');
  });
}
