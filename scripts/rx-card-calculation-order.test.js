/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const jsp = fs.readFileSync('src/main/webapp/WEB-INF/jsp/rx/SearchDrug3.jsp', 'utf8');
const source = jsp.slice(jsp.indexOf('function updateQty(element)'), jsp.indexOf('    function addLuCode('));

function fixture() {
  const elements = new Map();
  for (const id of ['saveButton', 'saveOnlyButton']) elements.set(id, { disabled: false });
  for (const card of ['11', '22']) {
    for (const prefix of ['set', 'instructions', 'quantity', 'method', 'route', 'frequency',
      'minimum', 'maximum', 'duration', 'durationUnit', 'quantityStr', 'unitName', 'prn', 'prnVal']) {
      const id = `${prefix}_${card}`;
      elements.set(id, { id, value: '', textContent: '' });
    }
  }
  const requests = [];
  const context = {
    ctx: '/carlos', encodeURIComponent, alert() {},
    document: { getElementById: id => elements.get(id) },
    CarlosAjax: { request(url, options) { requests.push({ url, options }); } },
  };
  vm.runInNewContext(source, context);
  const reply = (request, values) => request.options.onSuccess({ responseText: JSON.stringify({
    method: 'tab', route: 'PO', takeMin: 1, takeMax: 1, frequency: 'BID', duration: '14',
    durationUnit: 'D', calQuantity: '28', unitName: null, prn: false, ...values,
  }) });
  return { context, elements, requests, reply, field: (prefix, card = '11') => elements.get(`${prefix}_${card}`) };
}

test('a delayed staging quantity response cannot erase newer parsed directions (#5183)', () => {
  const f = fixture();
  f.context.updateQty(f.field('quantity'));
  f.field('instructions').value = '1 tab PO BID x 14 days';
  f.context.parseIntr(f.field('instructions'));
  f.reply(f.requests[1]);
  f.reply(f.requests[0], { frequency: '', duration: '', calQuantity: '' });
  assert.equal(f.field('frequency').textContent, 'BID');
  assert.equal(f.field('duration').textContent, '14');
  assert.equal(f.field('quantityStr').textContent, '28');
  assert.equal(f.field('quantity').value, '28');
});

test('the latest quantity calculation updates its card and does not invalidate another card', () => {
  const f = fixture();
  f.context.updateQty(f.field('quantity'));
  f.context.updateQty(f.field('quantity', '22'));
  f.context.updateQty(f.field('quantity'));
  f.reply(f.requests[2], { duration: '7', calQuantity: '14' });
  f.reply(f.requests[1], { frequency: 'OD', duration: '30', calQuantity: '30' });
  f.reply(f.requests[0]);
  assert.equal(f.field('duration').textContent, '7');
  assert.equal(f.field('quantityStr').textContent, '14');
  assert.equal(f.field('frequency', '22').textContent, 'OD');
  assert.equal(f.field('duration', '22').textContent, '30');
});

test('a response for a discarded card cannot change a replacement with the same id', () => {
  const f = fixture();
  f.context.updateQty(f.field('quantity'));
  f.elements.set('set_11', {});
  f.reply(f.requests[0]);
  assert.equal(f.field('frequency').textContent, '');
});
