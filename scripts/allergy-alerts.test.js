/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const {render} = require('../src/main/webapp/share/javascript/allergy-alerts');
function fixture() {
  function element() {
    return {style: {}, childNodes: [], set textContent(value) {this.text = value; this.childNodes = [];},
      get textContent() {return this.text || '';}, appendChild(child) {this.childNodes.push(child);}};
  }
  const cell = element(); const table = element();
  const document = {createElement: element, getElementById: id => id === 'alleg_7' ? cell : id === 'alleg_tbl_7' ? table : null};
  return {document, cell, table, text: () => cell.childNodes.map(child => child.textContent).join('\n')};
}
test('shows confirmed matches and every unresolved allergy without interpreting clinical text as HTML', () => {
  const f = fixture();
  render(f.document, 7, {results: [{DESCRIPTION:'PENICILLINS', reaction:'rash'}],
    unchecked:[{DESCRIPTION:'<img onerror=attack()>', reaction:'swelling'}], checkComplete:false});
  assert.match(f.text(), /Allergy: PENICILLINS Reaction: rash/);
  assert.match(f.text(), /Not checked: <img onerror=attack\(\)> Reaction: swelling/);
  assert.equal(f.cell.childNodes.length, 2);
  assert.equal(f.table.style.display, 'block');
});
for (const result of [null, {}, {results:[]}, {results:[null],unchecked:[],checkComplete:true}, {results:[],unchecked:[],checkFailed:true,checkComplete:false}]) {
  test(`keeps an unavailable or incomplete check visible: ${JSON.stringify(result)}`, () => {
    const f = fixture(); render(f.document, 7, result);
    assert.match(f.text(), /Allergy check (unavailable|incomplete)/);
    assert.equal(f.table.style.display, 'block');
  });
}
test('clears a prior warning only after a complete negative result', () => {
  const f=fixture(); render(f.document, 7, null);
  render(f.document, 7, {results:[],unchecked:[],checkComplete:true});
  assert.equal(f.table.style.display,'none'); assert.equal(f.text(),'');
});
test('shows pending and deliberately disabled checks distinctly', () => {
  const f=fixture(); render(f.document,7,{pending:true}); assert.match(f.text(),/Checking allergies/);
  render(f.document,7,{disabled:true}); assert.match(f.text(),/checking is disabled/);
});
test('ignores results after their prescription was removed', () => {
  assert.doesNotThrow(() => render({getElementById:()=>null},7,null));
});
