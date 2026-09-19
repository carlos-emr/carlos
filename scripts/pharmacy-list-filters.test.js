/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const test = require('node:test');
const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/rx/SelectPharmacy2.jsp'), 'utf8');
const start = jsp.indexOf('const pharmacyFilters =');
const end = jsp.indexOf('$(".pharmacyItem").click', start);
assert.ok(start >= 0 && end > start);
const script = jsp.slice(start, end);

function fixture() {
  const inputs = {};
  const handlers = {};
  const rows = [
    {pharmacyName:'Alpha [Care]+ Pharmacy', address:'10 Clinic Street', city:'Montréal', postalCode:'H1A 1A1', fax:'4165550100', phone:'4165550200'},
    {pharmacyName:'Alpha Care Pharmacy', address:'20 Other Street', city:'Toronto', postalCode:'M1A 1A1', fax:'4165550101', phone:'4165550201'},
  ].map(values => ({values, visible:true}));
  const $ = selector => {
    if (typeof selector !== 'string') return {
      find: column => ({text: () => selector.values[column.slice(1)]}),
      toggle: visible => { selector.visible = visible; },
    };
    if (selector === '.pharmacyItem') return {each: callback => rows.forEach(row => callback.call(row))};
    if (selector.includes(',')) return {on: (event, callback) => {
      assert.equal(event, 'input');
      for (const id of selector.split(', ')) handlers[id] = callback;
    }};
    return {val: () => inputs[selector] || ''};
  };
  vm.runInNewContext(script, {$});
  return {rows, inputs, fill(id, value) {
    assert.ok(handlers[id], `No input handler for ${id}`);
    inputs[id] = value; handlers[id]();
  }, visible: () => rows.map(row => row.visible)};
}

for (const text of ['[', ']+', '[Care]+', 'ALPHA [care]+']) {
  test(`name punctuation is a literal case-insensitive search: ${text}`, () => {
    const f = fixture(); f.fill('#pharmacySearch', text);
    assert.deepEqual(f.visible(), [true, false]);
  });
}

test('matching name and address stay visible in either editing order', () => {
  for (const order of [[['#pharmacySearch','Alpha'],['#pharmacyAddressSearch','10 Clinic']],
    [['#pharmacyAddressSearch','10 Clinic'],['#pharmacySearch','Alpha']]]) {
    const f = fixture();
    for (const [field, text] of order) f.fill(field, text);
    assert.deepEqual(f.visible(), [true, false]);
  }
});

for (const [field, value] of [['#pharmacyAddressSearch','10 Clinic'], ['#pharmacyCitySearch','MONTRÉAL'],
  ['#pharmacyPostalCodeSearch','h1a'], ['#pharmacyFaxSearch','0100'], ['#pharmacyPhoneSearch','0200']]) {
  test(`changing name retains the active ${field} criterion`, () => {
    const f = fixture(); f.fill(field, value); f.fill('#pharmacySearch', 'alpha');
    assert.deepEqual(f.visible(), [true, false]);
    f.fill(field, 'not found'); assert.deepEqual(f.visible(), [false, false]);
    f.fill('#pharmacySearch', 'Alpha'); assert.deepEqual(f.visible(), [false, false]);
    f.fill(field, ''); assert.deepEqual(f.visible(), [true, true]);
  });
}

test('all six filters are conjunctive and clearing restores both rows', () => {
  const f = fixture();
  for (const [field, value] of [['#pharmacySearch','Alpha'], ['#pharmacyAddressSearch','Street'],
    ['#pharmacyCitySearch','Montréal'], ['#pharmacyPostalCodeSearch','H1A'],
    ['#pharmacyFaxSearch','0100'], ['#pharmacyPhoneSearch','0200']]) f.fill(field, value);
  assert.deepEqual(f.visible(), [true, false]);
  for (const field of Object.keys(f.inputs)) f.fill(field, '');
  assert.deepEqual(f.visible(), [true, true]);
});

test('display text is searched literally without treating HTML entities as markup', () => {
  const f = fixture(); f.rows[0].values.pharmacyName = 'A&B <Care>';
  f.fill('#pharmacySearch', 'A&B <Care>'); assert.deepEqual(f.visible(), [true, false]);
  f.fill('#pharmacySearch', '&amp;'); assert.deepEqual(f.visible(), [false, false]);
});
