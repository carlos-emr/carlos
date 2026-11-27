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
      // The page clears a refused dose's aria-invalid flag when the server recalculates it.
      const attributes = new Map();
      elements.set(id, {
        id, value: '', textContent: '', attributes,
        setAttribute(name, value) { attributes.set(name, String(value)); },
        removeAttribute(name) { attributes.delete(name); },
      });
    }
    elements.set('draftRevision_' + card, { value: 'current-draft' });
    elements.set('rxCalculationStatus_' + card, { dataset: { failure: 'Retry calculation', pending: 'Calculating' } });
    elements.set('rxCalculationRetry_' + card, { hidden: true });
  }
  const requests = [];
  const quotes = [];
  const context = {
    ctx: '/carlos', encodeURIComponent, alert() {},
    refreshDrugPrice(card, quantity) { quotes.push({ card, quantity }); },
    document: { getElementById: id => elements.get(id) },
    CarlosAjax: { request(url, options) { requests.push({ url, options }); } },
  };
  vm.runInNewContext(source, context);
  const reply = (request, values) => request.options.onSuccess({ responseText: JSON.stringify({
    calculationSequence: Number(new URLSearchParams(request.options.parameters).get('calculationSequence')),
    method: 'tab', route: 'PO', takeMin: 1, takeMax: 1, frequency: 'BID', duration: '14',
    durationUnit: 'D', calQuantity: '28', priceQuantity: '28', unitName: null, prn: false,
    policyViolations: [], ...values,
  }) });
  return { context, elements, requests, quotes, reply, field: (prefix, card = '11') => elements.get(`${prefix}_${card}`) };
}

test('a delayed staging quantity response cannot erase newer parsed directions (#5183)', () => {
  const f = fixture();
  f.field('minimum').setAttribute('aria-invalid', 'true');
  f.context.updateQty(f.field('quantity'));
  f.field('instructions').value = '1 tab PO BID x 14 days';
  f.context.parseIntr(f.field('instructions'));
  f.reply(f.requests[1]);
  f.reply(f.requests[0], { frequency: '', duration: '', calQuantity: '' });
  assert.equal(f.field('frequency').textContent, 'BID');
  assert.equal(f.field('duration').textContent, '14');
  assert.equal(f.field('quantityStr').textContent, '28');
  assert.equal(f.field('quantity').value, '28');
  assert.equal(f.field('minimum').attributes.has('aria-invalid'), false);
  assert.deepEqual(f.quotes, [{ card: '11', quantity: '28' }]);
});

test('the latest quantity calculation updates its card and does not invalidate another card', () => {
  const f = fixture();
  f.context.updateQty(f.field('quantity'));
  f.context.updateQty(f.field('quantity', '22'));
  f.context.updateQty(f.field('quantity'));
  f.reply(f.requests[2], { duration: '7', calQuantity: '14', priceQuantity: '14' });
  f.reply(f.requests[1], { frequency: 'OD', duration: '30', calQuantity: '30', priceQuantity: '30' });
  f.reply(f.requests[0]);
  assert.equal(f.field('duration').textContent, '7');
  assert.equal(f.field('quantityStr').textContent, '14');
  assert.equal(f.field('frequency', '22').textContent, 'OD');
  assert.equal(f.field('duration', '22').textContent, '30');
  assert.deepEqual(f.quotes, [{ card: '11', quantity: '14' }, { card: '22', quantity: '30' }]);
});

test('a quantity calculation completed during a subsequent edit cannot quote the previous value', () => {
  const f = fixture();
  f.field('quantity').value = '30 tabs';
  f.context.updateQty(f.field('quantity'));
  f.field('quantity').value = '30 days';
  f.reply(f.requests[0], { calQuantity: '30', priceQuantity: '30' });
  assert.deepEqual(f.quotes, []);
  f.context.updateQty(f.field('quantity'));
  f.reply(f.requests[1], { calQuantity: '30', priceQuantity: null, unitName: 'days' });
  assert.deepEqual(f.quotes, [{ card: '11', quantity: null }]);
});

test('a response for a discarded card cannot change a replacement with the same id', () => {
  const f = fixture();
  f.field('minimum').setAttribute('aria-invalid', 'true');
  f.context.updateQty(f.field('quantity'));
  f.elements.set('set_11', {});
  f.reply(f.requests[0]);
  assert.equal(f.field('frequency').textContent, '');
  assert.equal(f.field('minimum').attributes.get('aria-invalid'), 'true');
});

test('HTTP and malformed success failures block only the affected card until retry', () => {
  const f = fixture();
  f.field('instructions').value = 'Keep this text on failure';
  f.context.parseIntr(f.field('instructions'));
  f.requests[0].options.onSuccess({responseText: '{}'});
  assert.equal(f.field('set').attributes.get('data-calculation-blocked'), 'true');
  assert.match(f.elements.get('rxCalculationStatus_11').textContent, /Retry/);
  assert.equal(f.field('instructions').value, 'Keep this text on failure');
  f.context.updateQty(f.field('quantity', '22'));
  f.reply(f.requests[1]);
  assert.equal(f.field('set').attributes.get('data-calculation-blocked'), 'true');
  assert.equal(f.field('set', '22').attributes.has('data-calculation-blocked'), false);
  f.context.parseIntr(f.field('instructions'));
  f.requests[2].options.onFailure({status: 500});
  assert.equal(f.field('set').attributes.get('data-calculation-blocked'), 'true');
  f.context.parseIntr(f.field('instructions'));
  f.reply(f.requests[3]);
  assert.equal(f.field('set').attributes.has('data-calculation-blocked'), false);
});

test('a parseIntr reply with calQuantity 0 keeps the displayed quantity and unit unchanged', () => {
  const f = fixture();
  f.field('quantity').value = '200';
  f.field('quantityStr').textContent = '200';
  f.field('unitName').textContent = 'mL';
  f.field('instructions').value = '20mg/ml';
  f.context.parseIntr(f.field('instructions'));
  f.reply(f.requests[0], { calQuantity: '0', priceQuantity: null, unitName: null });
  assert.equal(f.field('quantity').value, '200');
  assert.equal(f.field('quantityStr').textContent, '200');
  assert.equal(f.field('unitName').textContent, 'mL');
  assert.deepEqual(f.quotes, []);
});
