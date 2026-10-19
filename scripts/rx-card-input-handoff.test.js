/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// The prescribing card's page code around #4996, #5000, #5002, #5003 and #5004, run from the
// JSP sources with stand-ins for the DOM and CarlosAjax:
//  - choosing a favourite does not re-parse its directions (which cleared a saved PRN flag);
//  - multiline directions render in a textarea, not a single-line input that drops line breaks;
//  - a cleared pick-up field raises nothing; an invalid one names the entry;
//  - a non-numeric inline Min/Max is refused on the card, and a server refusal is reported;
//  - a Patient Dx Registry link puts the code, not its description, in the box Save reads;
//  - the timeline form tells an empty submitted selection from a first visit.
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const RxInputValidation = require('../src/main/webapp/share/javascript/rx-input-validation');

const searchDrug = fs.readFileSync('src/main/webapp/WEB-INF/jsp/rx/SearchDrug3.jsp', 'utf8');
const prescribe = fs.readFileSync('src/main/webapp/WEB-INF/jsp/rx/prescribe.jsp', 'utf8');

function slice(source, from, to) {
  const start = source.indexOf(from);
  const end = source.indexOf(to, start + from.length);
  assert.ok(start >= 0 && end > start, `slice ${JSON.stringify(from)} .. ${JSON.stringify(to)} is available`);
  return source.slice(start, end).replaceAll('${carlos:forJavaScript(ctx)}', '/carlos');
}

const jsMsg = {
  doseInvalid: 'dose "{0}"', repeatsInvalid: 'repeats "{0}"', requestRefused: 'refused',
  pickupDateInvalid: 'pick-up date "{0}"', pickupTimeInvalid: 'pick-up time "{0}"',
};

test('choosing a favourite skips the directions re-parse while its card scripts run', () => {
  const source = slice(searchDrug, '    skipParseInstr = false;\n    function useFav2', '   function Delete2(');
  const parsed = [];
  let cardScript = null;
  const context = {
    Math, ctx: '/carlos', parsed, renderRxStage() {}, reportRefusedRequest() {}, updateQty() {},
    parseIntr(element) { parsed.push(element.id); return true; },
    document: { getElementById: id => ({ id }) },
    CarlosAjax: {
      updater(target, url, options) {
        // CarlosAjax runs the inserted card's scripts inside onSuccess, before onComplete.
        cardScript();
        options.onComplete({ status: 200 });
      },
    },
  };
  vm.runInNewContext(source, context);
  cardScript = () => { context.calculateRxData('777'); context.skipParseInstr = false; };
  context.useFav2('9');
  assert.deepEqual(parsed, [], 'the favourite card re-parsed its directions and could clear its PRN flag');
  assert.equal(context.skipParseInstr, false, 'the flag must not stay set for the next staged drug');
  context.calculateRxData('778');
  assert.deepEqual(parsed, ['instructions_778'], 'an ordinary card still parses its directions');
});

test('a refused favourite request does not leave parsing switched off', () => {
  const source = slice(searchDrug, '    skipParseInstr = false;\n    function useFav2', '   function Delete2(');
  const context = {
    Math, ctx: '/carlos', renderRxStage() {}, reportRefusedRequest() {}, parseIntr() { return false; },
    document: { getElementById: id => ({ id }) },
    CarlosAjax: { updater(target, url, options) { options.onComplete({ status: 403 }); } },
  };
  vm.runInNewContext(source, context);
  context.useFav2('9');
  assert.equal(context.skipParseInstr, false);
});

test('the card renders directions in a textarea that keeps line breaks', () => {
  assert.match(prescribe, /<textarea id="instructions_<carlos:encode value='<%= rand %>' context="htmlAttribute"\/>"/);
  assert.doesNotMatch(prescribe, /<input type="text" id="instructions_/, 'a text input drops the saved line break');
  // The newline after the opening tag is the one the HTML parser discards.
  assert.match(prescribe, /onchange="parseIntr\(this\);">\n<carlos:encode value='<%= instructions %>' context="html"\/><\/textarea>/);
  assert.match(prescribe, /jQuery\("\[id\^='instructions_'\]"\)\.autocomplete/, 'the autocomplete still binds to the directions box');
});

test('Enter in the directions box opens Special Instructions; Shift+Enter adds a line', () => {
  const source = slice(prescribe, '            handleEnter=function handleEnter(inField, ev){', '            showHideSpecInst=function');
  const toggled = [];
  const context = { window: {}, showHideSpecInst: id => toggled.push(id) };
  vm.runInNewContext(source, context);
  let prevented = 0;
  context.handleEnter({ id: 'instructions_5' }, { which: 13, shiftKey: false, preventDefault() { prevented++; } });
  assert.deepEqual(toggled, ['siAutoComplete_5']);
  assert.equal(prevented, 1, 'plain Enter must not add a line');
  context.handleEnter({ id: 'instructions_5' }, { which: 13, shiftKey: true, preventDefault() { prevented++; } });
  assert.deepEqual(toggled, ['siAutoComplete_5']);
  assert.equal(prevented, 1, 'Shift+Enter must add the line');
});

function cardFields() {
  const source = slice(searchDrug, '    function updateProperty(elementId){', '    function lookNonEdittable(elementId){');
  const requests = [];
  const alerts = [];
  const refused = [];
  const elements = {};
  const context = {
    ctx: '/carlos', Math, encodeURIComponent, jsMsg, RxInputValidation,
    alert: message => alerts.push(message),
    reportRefusedRequest: transport => refused.push(transport),
    document: { getElementById: id => elements[id] },
    CarlosAjax: { request(url, options) { requests.push({ url, options }); } },
  };
  vm.runInNewContext(source, context);
  const element = (id, props) => (elements[id] = { id, className: '', attributes: {}, setAttribute(k, v) { this.attributes[k] = v; }, ...props });
  return { context, requests, alerts, refused, element };
}

test('a non-numeric inline Min or Max is refused on the card without a request', () => {
  for (const field of ['minimum_5', 'maximum_5']) {
    const s = cardFields();
    const el = s.element(field, { textContent: ' one ' });
    s.context.updateProperty(field);
    assert.deepEqual(s.requests, [], `${field}: a refused dose was still sent`);
    assert.deepEqual(s.alerts, ['dose "one"']);
    assert.equal(el.className, 'highlight');
    assert.equal(el.attributes['aria-invalid'], 'true');
  }
});

test('a numeric inline dose is sent, and a server refusal is explained', () => {
  const s = cardFields();
  s.element('minimum_5', { textContent: '0.5' });
  s.context.updateProperty('minimum_5');
  assert.equal(s.requests.length, 1);
  assert.equal(new URLSearchParams(s.requests[0].options.parameters).get('propertyValue'), '0.5');
  s.requests[0].options.onFailure({ status: 400, responseJSON: { error: 'INVALID_RX_DOSE' } });
  assert.deepEqual(s.alerts, ['dose "0.5"']);
  s.requests[0].options.onFailure({ status: 409, responseJSON: null });
  assert.equal(s.refused.length, 1, 'other refusals keep the generic message');
});

test('a late answer for an older inline edit does not decide the field\'s invalid state', () => {
  const s = cardFields();
  const el = s.element('minimum_5', { textContent: '0.5' });
  s.context.updateProperty('minimum_5');
  el.textContent = '0.25';
  s.context.updateProperty('minimum_5');
  const [older, newer] = s.requests;
  newer.options.onFailure({ status: 400, responseJSON: { error: 'INVALID_RX_DOSE' } });
  assert.equal(el.attributes['aria-invalid'], 'true');
  el.removeAttribute = name => { delete el.attributes[name]; };
  older.options.onSuccess({ status: 200 });
  assert.equal(el.attributes['aria-invalid'], 'true', 'the older success cleared the newer refusal');
  older.options.onFailure({ status: 400, responseJSON: { error: 'INVALID_RX_DOSE' } });
  assert.equal(s.alerts.length, 1, 'a stale refusal raised a second alert');
});

test('a value refused on the card supersedes an older edit still in flight', () => {
  const s = cardFields();
  const el = s.element('maximum_5', { textContent: '2' });
  el.removeAttribute = name => { delete el.attributes[name]; };
  s.context.updateProperty('maximum_5');
  el.textContent = 'two';
  s.context.updateProperty('maximum_5');
  assert.equal(s.requests.length, 1, 'the refused value was sent');
  s.requests[0].options.onSuccess({ status: 200 });
  assert.equal(el.attributes['aria-invalid'], 'true', 'the older success cleared the card-side refusal');
});

test('a cleared pick-up field raises nothing; an invalid one names the entry and is cleared', () => {
  const s = cardFields();
  for (const kind of ['date', 'time']) {
    const blank = { value: '  ' };
    s.context.checkPickupField(blank, kind);
    assert.equal(blank.value, '');
  }
  assert.deepEqual(s.alerts, []);
  const date = { value: '2026-02-30' };
  s.context.checkPickupField(date, 'date');
  const time = { value: '25:00' };
  s.context.checkPickupField(time, 'time');
  assert.deepEqual(s.alerts, ['pick-up date "2026-02-30"', 'pick-up time "25:00"']);
  assert.equal(date.value, '');
  assert.equal(time.value, '');
  const valid = { value: ' 2026-10-18 ' };
  s.context.checkPickupField(valid, 'date');
  assert.equal(valid.value, '2026-10-18');
  assert.equal(s.alerts.length, 2);
});

test('the card wires the pick-up fields to checkPickupField, not the alerting checkDate.js', () => {
  assert.match(prescribe, /id="pickupDate_<%=rand%>"[^\n]*\n\s*onchange="checkPickupField\(this, 'date'\);"/);
  assert.match(prescribe, /id="pickupTime_<%=rand%>"[^\n]*\n\s*onchange="checkPickupField\(this, 'time'\);"/);
  assert.doesNotMatch(prescribe, /isValidDate\(this\.value\)|isValidTime\(this\.value\)/);
});

test('the written-date check refuses a day the calendar does not have', () => {
  const source = slice(searchDrug, '    function validateWrittenDate() {', '        return x;\n    }') + 'return x; }';
  const alerts = [];
  const run = value => {
    let focused = 0;
    const field = { val: () => value, focus() { focused++; } };
    const jQuery = arg => (typeof arg === 'string'
      ? { each(fn) { fn.call(field); } }
      : { val: () => value, focus() { focused++; } });
    const context = { jQuery, jsMsg: { ...jsMsg, dateNotOnCalendar: 'not a day "{0}"', writtenDateFuture: 'future' },
      RxInputValidation, alert: m => alerts.push(m), Date };
    vm.runInNewContext(source, context);
    return { ok: context.validateWrittenDate(), focused };
  };
  assert.deepEqual(run('2026-02-31'), { ok: false, focused: 1 });
  assert.deepEqual(alerts, ['not a day "2026-02-31"']);
  assert.equal(run('2026-02-28').ok, true);
  assert.equal(run('2026-02').ok, true, 'partial dates keep their meaning');
});

test('a Patient Dx Registry link fills the code box Save reads, and shows the description beside it', () => {
  const jsp = fs.readFileSync('src/main/webapp/WEB-INF/jsp/rx/SelectReason.jsp', 'utf8');
  const source = slice(jsp, '            function assignPatientDxLink(id, name) {', '            function toggleArchiveMenu(id) {');
  const fields = {};
  const handlers = {};
  const $ = selector => {
    if (selector && selector.nodeType === 9) return { on(event, target, fn) { handlers[`${event} ${target}`] = fn; } };
    const field = (fields[selector] ||= { value: '', text: '', props: {}, css: {} });
    const api = {
      val(v) { if (v === undefined) return field.value; field.value = v; return api; },
      text(v) { field.text = v; return api; },
      prop(k, v) { field.props[k] = v; return api; },
      css(k, v) { field.css[k] = v; return api; },
    };
    return api;
  };
  const context = vm.createContext({ $, document: { nodeType: 9 } });
  vm.runInContext(source, context);
  context.assignPatientDxLink('401', 'ESSENTIAL HYPERTENSION');
  assert.equal(fields['#jsonDxSearch'].value, '401', 'Save validates this box as the ICD9 code');
  assert.equal(fields['#codeTxt'].value, '401');
  assert.equal(fields['#jsonDxSearchDescription'].text, 'ESSENTIAL HYPERTENSION');
  assert.equal(fields['#jsonDxSearch'].props.title, '401 - ESSENTIAL HYPERTENSION');
  handlers['input #jsonDxSearch']();
  assert.equal(fields['#jsonDxSearchDescription'].text, '', 'typing another code clears the stale description');
  assert.equal(fields['#codeTxt'].value, '');
  assert.match(jsp, /<span id="jsonDxSearchDescription" aria-live="polite"><\/span>/);
});

test('the timeline form marks a submitted selection so an empty one is not a first visit', () => {
  const jsp = fs.readFileSync('src/main/webapp/WEB-INF/jsp/rx/chartDrugProfile.jsp', 'utf8');
  assert.match(jsp, /<input type="hidden" name="selectionSubmitted" value="true"\/>/);
  assert.match(jsp, /boolean selectionSubmitted = "true"\.equals\(request\.getParameter\("selectionSubmitted"\)\);/);
  assert.match(jsp, /if \(selectionSubmitted \|\| request\.getParameterValues\("drug"\) != null\) \{/);
});
