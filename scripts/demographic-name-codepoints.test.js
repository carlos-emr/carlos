/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const model = fs.readFileSync(path.join(__dirname,'../src/main/java/io/github/carlos_emr/carlos/commn/model/Demographic.java'),'utf8');
const limits = Object.fromEntries([...model.matchAll(/public static final int ([A-Z_]+_MAX_LENGTH) = (\d+);/g)]
  .map(([,name,value]) => [name,Number(value)]));
const fragment = fs.readFileSync(path.join(__dirname,'../src/main/webapp/WEB-INF/jspf/demographic-field-length-limits.jspf'),'utf8');
// Extract the one known source block; this test does not filter or sanitize HTML.
const scriptOpeningTag = '<script type="text/javascript">';
const scriptStart = fragment.indexOf(scriptOpeningTag);
// nosemgrep: javascript.lang.security.audit.unknown-value-with-script-tag.unknown-value-with-script-tag -- Indexes the fixed checked-in JSP above; no external HTML is accepted or rendered.
const scriptEnd = fragment.indexOf('</script>', scriptStart + scriptOpeningTag.length);
assert.notEqual(scriptStart, -1, 'Expected the shared fragment script block');
assert.ok(scriptEnd > scriptStart, 'Expected the shared fragment script closing marker');
// nosemgrep: javascript.lang.security.audit.unknown-value-with-script-tag.unknown-value-with-script-tag -- Source-structure assertion over that same fixed JSP, not a rendering sink.
assert.equal(fragment.indexOf('<script', scriptStart + scriptOpeningTag.length), -1,
  'Expected exactly one shared fragment script block');
// nosemgrep: javascript.lang.security.audit.unknown-value-with-script-tag.unknown-value-with-script-tag -- Extracts executable test code only from the fixed repository source block whose boundaries were checked above.
const script = fragment.slice(scriptStart + scriptOpeningTag.length, scriptEnd)
  .replace(/<%= Demographic\.([A-Z_]+) %>/g, (_,name) => {
    assert.ok(Number.isInteger(limits[name]), `Unknown demographic field limit: ${name}`);
    return String(limits[name]);
  })
  .replace('${carlos:forJavaScript(demographicCodePointLimitMessage)}','Maximum {0} characters.');
for (const fieldName of ['first_name','last_name','pronouns','gender']) {
  const maximum = fieldName === 'first_name' || fieldName === 'last_name' ? 50 : 25;
  test(`${fieldName} preserves ${maximum} supplementary characters and refuses ${maximum + 1} without truncation`, () => {
    const initial = '😀'.repeat(maximum); const handlers={}; const formHandlers={}; const attributes={};
    const field = {value:initial,form:{addEventListener:(name,fn)=>{formHandlers[name]=fn;}},
      setAttribute:(name,value)=>{attributes[name]=value;},setCustomValidity(message){this.error=message;},
      addEventListener:(name,fn)=>{handlers[name]=fn;},reportValidity(){this.reported=true;}};
    const document={querySelectorAll:selector=>selector.includes(`name="${fieldName}"`)?[field]:[],addEventListener(){}};
    const context={document};vm.createContext(context);vm.runInContext(script,context);context.applyDemographicFieldLengthLimits();
    assert.equal(field.value,initial);assert.equal(field.error,'');assert.equal(attributes.maxlength,maximum * 2);
    assert.equal(attributes['data-max-codepoints'],maximum);
    field.value+='😀';handlers.input();assert.equal(field.error,`Maximum ${maximum} characters.`);
    let prevented=false;formHandlers.submit({preventDefault(){prevented=true;}});
    assert.equal(prevented,true);assert.equal(field.reported,true);assert.equal(field.value,'😀'.repeat(maximum + 1));
    field.value='A'.repeat(maximum);handlers.input();assert.equal(field.error,'');
    field.value+='A';handlers.input();assert.notEqual(field.error,'');
  });
}
