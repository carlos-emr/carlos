/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),{deferNativeLinkSyntaxErrors}=require('./rx-limited-use-code-persistence-playwright-checks');
const hrefs=Array(3).fill('javascript: return void();'),error=()=>({label:'limited-use-rx',text:'Illegal return statement'});
test('LU diagnostic deferral preserves unrelated errors and requires three exact native broken hrefs',()=>{
 const before={label:'other',text:'earlier'},other={label:'limited-use-rx',text:'other problem'},recorder={pageErrors:[before,error(),error(),error(),other]};
 assert.equal(deferNativeLinkSyntaxErrors(recorder,1,hrefs).length,3);assert.deepEqual(recorder.pageErrors,[before,other]);
 for(const links of [hrefs.slice(1),['#',...hrefs.slice(1)]]){const current={pageErrors:[error(),error(),error()]};assert.equal(deferNativeLinkSyntaxErrors(current,0,links).length,0);assert.equal(current.pageErrors.length,3);}
 const current={pageErrors:[error(),error()]};assert.equal(deferNativeLinkSyntaxErrors(current,0,hrefs).length,0);assert.equal(current.pageErrors.length,2);
});

const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const rxDirectory = path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/rx');

for (const page of ['LimitedUseCode.jsp', 'WriteScript.jsp']) {
  test(`${page}: offered LU links cancel navigation and append repeated codes to only their directions`, () => {
    const source = fs.readFileSync(path.join(rxDirectory, page), 'utf8');
    const rawAnchor = source.match(/<a\s+onclick="[^]*?addLuCode\([^]*?<\/a>/)[0];
    const anchor = rawAnchor
      .replace(/<carlos:encode[^]*?\/>/g, tag => tag.includes('randomId') ? 'selected' : '123')
      .replace(/<%=\s*limitedUseCode\.getUseId\(\)\s*%>/g, '123');
    const handler = anchor.match(/onclick="([^"]*)"/)[1];
    const href = anchor.match(/href="([^"]*)"/)[1];
    // A link's default action must also be syntactically valid if invoked.
    if (/^javascript:/i.test(href)) new vm.Script(href.slice('javascript:'.length));
    const selected = {value: '1 tab daily '}, control = {value: 'control directions'};
    const elements = {instructions_selected: selected, instructions_control: control};
    const context = vm.createContext({
      frm: {special: selected, control},
      document: {getElementById(id) {
        assert.ok(Object.hasOwn(elements, id), 'LU selection must target an existing medication');
        return elements[id];
      }},
    });
    const functionPage = page === 'LimitedUseCode.jsp' ? 'SearchDrug3.jsp' : page;
    const functionSource = fs.readFileSync(path.join(rxDirectory, functionPage), 'utf8')
      .match(/function addLuCode\([^]*?\n\s*\}/)[0];
    vm.runInContext(functionSource, context);
    const click = vm.runInContext(`(function(event) { ${handler} })`, context);
    for (let index = 0; index < 3; index++) {
      const event = {defaultPrevented: false, preventDefault() {this.defaultPrevented = true;}};
      click(event);
      assert.equal(event.defaultPrevented, true, 'LU selection must cancel the link default action');
    }
    assert.equal((selected.value.match(/LU Code: 123/g) || []).length, 3);
    assert.ok(selected.value.startsWith('1 tab daily '));
    assert.equal(control.value, 'control directions');
  });
}
