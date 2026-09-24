/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

const PatientLink = require('../src/main/webapp/js/appointmentPatientLink');

/*
 * Issue #3883: arrowing to a patient in the Add/Edit Appointment typeahead and
 * then leaving the field saved the appointment with the name but no
 * demographic_no. These tests drive the shared controller through the same
 * DOM events the browser delivers, using minimal fake elements, so the
 * commit-on-blur and reconcile rules are pinned without a browser or Tomcat.
 */

class FakeElement {
  constructor(value = '') {
    this.value = value;
    this.listeners = {};
    this.form = null;
  }

  addEventListener(type, listener, capture = false) {
    (this.listeners[type] = this.listeners[type] || []).push({ listener, capture: Boolean(capture) });
  }

  fire(type, props = {}) {
    const event = { type, target: this, ...props };
    for (const { listener } of this.listeners[type] || []) {
      listener(event);
    }
    return event;
  }
}

/**
 * Dispatch a submit event the way the DOM does: capture-phase listeners on the
 * document first, then the form's own listeners in registration order. The
 * form's inline onsubmit attribute is registered at parse time, before any
 * script, so setup() adds it first -- which is exactly why a plain listener
 * added by the controller would run after it.
 */
function dispatchSubmit(form) {
  const event = { type: 'submit', target: form };
  for (const { listener, capture } of form.ownerDocument.listeners.submit || []) {
    if (capture) listener(event);
  }
  for (const { listener } of form.listeners.submit || []) {
    listener(event);
  }
  for (const { listener, capture } of form.ownerDocument.listeners.submit || []) {
    if (!capture) listener(event);
  }
}

const SMITH = {
  value: '101', provider: 'Dr. Who', formattedName: 'SMITH, JOHN', alert: 'Latex allergy',
};
const JONES = { value: '202', provider: 'Dr. No', formattedName: 'JONES, ANN' };

function setup({ name = '', demographicNo = '', provider = '' } = {}) {
  const form = new FakeElement();
  form.ownerDocument = new FakeElement();
  const nameField = new FakeElement(name);
  nameField.form = form;
  const demographicField = new FakeElement(demographicNo);
  const providerField = new FakeElement(provider);
  // Native HTMLFormElement.submit(): records what would be posted, and, like
  // the real method, dispatches no submit event.
  form.submitted = [];
  form.submit = function submit() {
    this.submitted.push({
      keyword: nameField.value, demographicNo: demographicField.value, mrp: providerField.value,
    });
  };
  // Stand-in for onsubmit="return onAdd()": records what the page's own submit
  // handler would read. Registered before create(), as the parsed attribute is.
  form.inlineSaw = [];
  form.addEventListener('submit', () => {
    form.inlineSaw.push({ keyword: nameField.value, demographicNo: demographicField.value });
  });
  const commits = [];
  let unlinks = 0;
  const link = PatientLink.create({
    nameField,
    demographicField,
    providerField,
    onCommit: (item) => commits.push(item),
    onUnlink: () => { unlinks += 1; },
  });
  return {
    link, form, nameField, demographicField, providerField, commits, unlinks: () => unlinks,
  };
}

/** The user types text: value changes, then an input event fires. */
function type(field, value) {
  field.value = value;
  field.fire('input');
}

test('a highlighted but unselected patient is committed on blur', () => {
  const page = setup();
  type(page.nameField, 'smi');
  page.link.highlight(SMITH);
  assert.equal(page.nameField.value, 'SMITH, JOHN');
  assert.equal(page.demographicField.value, '', 'highlighting alone must not link');

  page.nameField.fire('blur');

  assert.equal(page.demographicField.value, '101');
  assert.equal(page.providerField.value, 'Dr. Who');
  assert.equal(page.nameField.value, 'SMITH, JOHN');
  assert.deepEqual(page.commits, [SMITH], 'banners must refresh for a blur commit too');
});

test('menu closing (not by Escape) commits the highlighted patient', () => {
  const page = setup();
  type(page.nameField, 'smi');
  page.link.highlight(SMITH);
  page.link.menuClosed({ type: 'autocompleteclose', originalEvent: { type: 'blur' } });
  assert.equal(page.demographicField.value, '101');
});

test('Escape closes the menu without committing and forgets the highlight', () => {
  const page = setup();
  type(page.nameField, 'smi');
  page.link.highlight(SMITH);
  // jQuery UI puts the typed term back before it closes the menu.
  page.nameField.value = 'smi';
  page.link.menuClosed({
    type: 'autocompleteclose',
    originalEvent: { type: 'keydown', keyCode: 27 },
  });
  assert.equal(page.demographicField.value, '');
  assert.equal(page.link.state().highlighted, null);
});

test('Escape is recognised even when the typed term equals the highlighted name', () => {
  const page = setup();
  type(page.nameField, 'SMITH, JOHN');
  page.link.highlight(SMITH);
  page.link.menuClosed({ originalEvent: { originalEvent: { type: 'keydown', key: 'Escape' } } });
  page.nameField.fire('blur');
  assert.equal(page.demographicField.value, '', 'a backed-out row must not be committed');
});

test('arrowing past the end of the menu restores the term, so blur does not commit', () => {
  const page = setup();
  type(page.nameField, 'smi');
  page.link.highlight(SMITH);
  page.nameField.value = 'smi'; // jQuery UI _move() at the menu edge: no input event
  page.nameField.fire('blur');
  assert.equal(page.demographicField.value, '');
  assert.equal(page.nameField.value, 'smi');
});

test('select commits immediately and a later blur is a no-op', () => {
  const page = setup();
  page.link.commit(JONES);
  assert.equal(page.demographicField.value, '202');
  page.nameField.fire('blur');
  assert.equal(page.demographicField.value, '202');
  assert.equal(page.commits.length, 1);
});

test('typing never unlinks while the user is still in the field', () => {
  const page = setup({ name: 'SMITH, JOHN', demographicNo: '101', provider: 'Dr. Who' });
  type(page.nameField, 'SMITH, JOH');
  type(page.nameField, '');
  type(page.nameField, 'jon');
  // The menu also closes mid-typing when a search returns nothing.
  page.link.menuClosed({ type: 'autocompleteclose' });
  assert.equal(page.demographicField.value, '101');
  assert.equal(page.providerField.value, 'Dr. Who');
  assert.equal(page.unlinks(), 0);
});

test('typing drops a stale highlight so blur cannot commit it', () => {
  const page = setup();
  type(page.nameField, 'smi');
  page.link.highlight(SMITH);
  type(page.nameField, 'SMITH, JOHNX');
  type(page.nameField, 'SMITH, JOHN'); // back to the row's text by hand
  page.nameField.fire('blur');
  assert.equal(page.demographicField.value, '', 'only a highlight or pick links a patient');
});

test('leaving the field with a different name removes the stale link', () => {
  const page = setup({ name: 'SMITH, JOHN', demographicNo: '101', provider: 'Dr. Who' });
  type(page.nameField, 'Staff lunch');
  page.nameField.fire('blur');
  assert.equal(page.demographicField.value, '');
  assert.equal(page.providerField.value, '');
  assert.equal(page.nameField.value, 'Staff lunch');
  assert.equal(page.unlinks(), 1, 'the page must be told so it can hide the banners');
});

test('clearing the name and leaving restores the linked patient instead of dropping it', () => {
  const page = setup({ name: 'SMITH, JOHN', demographicNo: '101', provider: 'Dr. Who' });
  type(page.nameField, '');
  page.nameField.fire('blur');
  assert.equal(page.demographicField.value, '101');
  assert.equal(page.nameField.value, 'SMITH, JOHN', 'the kept link must be visible');
  assert.equal(page.unlinks(), 0);
});

test('an edit that ends at the linked name (modulo whitespace) keeps the link', () => {
  const page = setup({ name: 'SMITH, JOHN', demographicNo: '101' });
  type(page.nameField, 'SMITH, JO');
  type(page.nameField, '  SMITH, JOHN ');
  page.nameField.fire('blur');
  assert.equal(page.demographicField.value, '101');
  assert.equal(page.nameField.value, 'SMITH, JOHN');
});

test('Enter-submit without a blur still reconciles the link', () => {
  const page = setup({ name: 'SMITH, JOHN', demographicNo: '101' });
  type(page.nameField, 'JONES');
  dispatchSubmit(page.form);
  assert.equal(page.demographicField.value, '');
});

test("the form's inline onsubmit handler sees the reconciled link, not the stale one", () => {
  const page = setup({ name: 'SMITH, JOHN', demographicNo: '101' });
  type(page.nameField, '.late');
  dispatchSubmit(page.form);
  assert.deepEqual(page.form.inlineSaw, [{ keyword: '.late', demographicNo: '' }],
    "onAdd's no-show rule must see demographic_no already cleared");
});

test("the form's inline onsubmit handler sees a highlighted row already committed", () => {
  const page = setup();
  type(page.nameField, 'jo');
  page.link.highlight(JONES);
  dispatchSubmit(page.form);
  assert.deepEqual(page.form.inlineSaw, [{ keyword: 'JONES, ANN', demographicNo: '202' }]);
});

test('submit events for another form on the page are ignored', () => {
  const page = setup({ name: 'SMITH, JOHN', demographicNo: '101' });
  type(page.nameField, 'JONES');
  const other = new FakeElement();
  other.ownerDocument = page.form.ownerDocument;
  dispatchSubmit(other);
  assert.equal(page.demographicField.value, '101', 'only this form\'s submit reconciles');
});

test('the controller registers its submit listener in the capture phase on the document', () => {
  const page = setup();
  const docListeners = page.form.ownerDocument.listeners.submit || [];
  assert.equal(docListeners.length, 1);
  assert.equal(docListeners[0].capture, true);
  assert.equal(page.form.listeners.submit.length, 1, 'only the inline stand-in is on the form itself');
});

test('submit with a highlighted row commits it', () => {
  const page = setup();
  type(page.nameField, 'jo');
  page.link.highlight(JONES);
  dispatchSubmit(page.form);
  assert.equal(page.demographicField.value, '202');
});

test('picking another patient replaces the link and becomes the new baseline', () => {
  const page = setup({ name: 'SMITH, JOHN', demographicNo: '101' });
  type(page.nameField, 'jo');
  page.link.highlight(JONES);
  page.nameField.fire('blur');
  assert.equal(page.demographicField.value, '202');
  type(page.nameField, '');
  page.nameField.fire('blur');
  assert.equal(page.nameField.value, 'JONES, ANN', 'restores the new patient, not the old one');
});

test('a free-text appointment with no link is left alone', () => {
  const page = setup({ name: 'Staff lunch' });
  type(page.nameField, 'Staff meeting');
  page.nameField.fire('blur');
  assert.equal(page.nameField.value, 'Staff meeting');
  assert.equal(page.demographicField.value, '');
  assert.equal(page.unlinks(), 0);
});

test('rebase() after pasteAppt-style direct writes adopts the pasted patient', () => {
  const page = setup({ name: 'SMITH, JOHN', demographicNo: '101' });
  page.nameField.value = 'JONES, ANN';
  page.demographicField.value = '202';
  PatientLink.rebase(page.nameField);
  page.nameField.fire('blur');
  assert.equal(page.demographicField.value, '202');
  assert.equal(page.nameField.value, 'JONES, ANN');
});

test('rebase() to a different patient without metadata clears the MRP and hides the banners', () => {
  const page = setup({ name: 'SMITH, JOHN', demographicNo: '101', provider: 'Dr. Who' });
  page.nameField.value = 'JONES, ANN';
  page.demographicField.value = '202';
  PatientLink.rebase(page.nameField);
  assert.equal(page.providerField.value, '', "Dr. Who is SMITH's MRP, not JONES's");
  assert.equal(page.unlinks(), 1, 'onStale defaults to onUnlink, which hides the banners');
  assert.deepEqual(page.commits, []);
  assert.equal(page.link.state().linked.value, '202');
});

test('rebase() to "no patient" (demographic_no 0) hides the banners', () => {
  const page = setup({ name: 'SMITH, JOHN', demographicNo: '101', provider: 'Dr. Who' });
  page.nameField.value = 'Staff lunch';
  page.demographicField.value = '0';
  PatientLink.rebase(page.nameField);
  assert.equal(page.providerField.value, '');
  assert.equal(page.unlinks(), 1);
  assert.equal(page.link.state().linked, null);
});

test('rebase() with the pasted patient metadata refreshes the banners via onCommit', () => {
  const page = setup({ name: 'SMITH, JOHN', demographicNo: '101', provider: 'Dr. Who' });
  page.nameField.value = 'JONES, ANN';
  page.demographicField.value = '202';
  PatientLink.rebase(page.nameField, JONES);
  assert.equal(page.providerField.value, 'Dr. No');
  assert.deepEqual(page.commits, [JONES]);
  assert.equal(page.unlinks(), 0);
});

test('rebase() to the same patient leaves the MRP and banners alone', () => {
  const page = setup({ name: 'SMITH, JOHN', demographicNo: '101', provider: 'Dr. Who' });
  PatientLink.rebase(page.nameField);
  assert.equal(page.providerField.value, 'Dr. Who');
  assert.equal(page.unlinks(), 0);
  assert.deepEqual(page.commits, []);
});

test('onStale, when given, is called instead of onUnlink with the new baseline', () => {
  const nameField = new FakeElement('SMITH, JOHN');
  const demographicField = new FakeElement('101');
  const stale = [];
  let unlinks = 0;
  PatientLink.create({
    nameField, demographicField, onStale: (linked) => stale.push(linked), onUnlink: () => { unlinks += 1; },
  });
  demographicField.value = '202';
  PatientLink.rebase(nameField);
  assert.equal(stale.length, 1);
  assert.equal(stale[0].value, '202');
  assert.equal(unlinks, 0);
});

test('submitForm() reconciles a hand-edited name before the native submit', () => {
  const page = setup({ name: 'SMITH, JOHN', demographicNo: '101', provider: 'Dr. Who' });
  type(page.nameField, 'Staff lunch');
  PatientLink.submitForm(page.form);
  assert.deepEqual(page.form.submitted, [{ keyword: 'Staff lunch', demographicNo: '', mrp: '' }]);
  assert.equal(page.unlinks(), 1);
});

test('submitForm() commits a highlighted row before the native submit', () => {
  const page = setup();
  type(page.nameField, 'jo');
  page.link.highlight(JONES);
  PatientLink.submitForm(page.form);
  assert.deepEqual(page.form.submitted, [{ keyword: 'JONES, ANN', demographicNo: '202', mrp: 'Dr. No' }]);
});

test('submitForm() still submits a form with no controller', () => {
  const form = new FakeElement();
  let submitted = 0;
  form.submit = () => { submitted += 1; };
  PatientLink.submitForm(form);
  assert.equal(submitted, 1);
});

test('a link written by page code without rebase() is picked up on focus', () => {
  const page = setup();
  page.nameField.value = 'JONES, ANN';
  page.demographicField.value = '202';
  page.nameField.fire('focus');
  type(page.nameField, '');
  page.nameField.fire('blur');
  assert.equal(page.nameField.value, 'JONES, ANN');
  assert.equal(page.demographicField.value, '202');
});

test('unlink() (Do Not Book) removes the link immediately', () => {
  const page = setup({ name: 'SMITH, JOHN', demographicNo: '101', provider: 'Dr. Who' });
  page.nameField.value = 'Do_Not_Book';
  PatientLink.unlink(page.nameField);
  assert.equal(page.demographicField.value, '');
  assert.equal(page.providerField.value, '');
  page.nameField.fire('blur');
  assert.equal(page.nameField.value, 'Do_Not_Book');
});

test('a pasted free-text appointment (demographic_no "0") is not treated as linked', () => {
  const page = setup({ name: 'Staff lunch', demographicNo: '0' });
  type(page.nameField, '');
  page.nameField.fire('blur');
  assert.equal(page.nameField.value, '', 'nothing to restore for a non-patient booking');
  assert.equal(page.unlinks(), 0);
});

test('module-level helpers ignore a field with no controller', () => {
  assert.doesNotThrow(() => PatientLink.rebase(new FakeElement()));
  assert.doesNotThrow(() => PatientLink.unlink(null));
  assert.equal(PatientLink.forField(new FakeElement()), null);
});

/*
 * Source contract: both appointment pages must load the shared script and route
 * every autocomplete path through it. A page that went back to writing
 * #demographic_no in its own select callback would silently lose the blur fix.
 */
const JSP_DIR = path.join(__dirname, '..', 'src', 'main', 'webapp', 'WEB-INF', 'jsp', 'appointment');

for (const page of ['addappointment.jsp', 'editappointment.jsp']) {
  test(`${page} routes the patient typeahead through appointmentPatientLink.js`, () => {
    const source = fs.readFileSync(path.join(JSP_DIR, page), 'utf8');
    assert.match(source, /<script src="\$\{pageContext\.request\.contextPath\}\/js\/appointmentPatientLink\.js"><\/script>/);
    assert.match(source, /CarlosAppointmentPatientLink\.create\(\{/);
    assert.match(source, /focus: function \(event, ui\) \{\s*patientLink\.highlight\(ui\.item\);/);
    assert.match(source, /select: function \(event, ui\) \{\s*patientLink\.commit\(ui\.item\);/);
    assert.match(source, /close: function \(event\) \{\s*patientLink\.menuClosed\(event\);/);
    assert.match(source, /CarlosAppointmentPatientLink\.rebase\(/, 'pasteAppt must rebase');
    assert.doesNotMatch(source, /\("#demographic_no"\)\.val\(ui\.item/,
      'the link must only be written by the shared controller');
  });
}

/*
 * HTMLFormElement.submit() dispatches no submit event, so a raw call skips the
 * reconcile step and can post a hand-edited name with the old demographic_no.
 * Every scripted submission must go through submitForm(). The expected counts
 * are the call sites audited for #3883, so a new one has to be looked at.
 */
const SUBMIT_SITES = { 'addappointment.jsp': 1, 'editappointment.jsp': 6 };

for (const [page, expected] of Object.entries(SUBMIT_SITES)) {
  test(`${page} has no scripted submit that bypasses the patient-link controller`, () => {
    const source = fs.readFileSync(path.join(JSP_DIR, page), 'utf8');
    const raw = source.split('\n')
      .map((line, index) => ({ line, number: index + 1 }))
      .filter(({ line }) => /\.submit\s*\(\s*\)|\.requestSubmit\s*\(|\$\([^)]*\)\.submit\s*\(|\.trigger\(\s*['"]submit['"]/.test(line));
    assert.deepEqual(raw.map(({ number, line }) => `${number}: ${line.trim()}`), [],
      'use CarlosAppointmentPatientLink.submitForm(form) instead');
    const routed = source.match(/CarlosAppointmentPatientLink\.submitForm\(/g) || [];
    assert.equal(routed.length, expected, `${page} scripted submit sites changed; audit and update SUBMIT_SITES`);
  });
}

test('addappointment.jsp Do Not Book drops the patient link', () => {
  const source = fs.readFileSync(path.join(JSP_DIR, 'addappointment.jsp'), 'utf8');
  const onNotBook = source.match(/function onNotBook\(\) \{[\s\S]*?\n\s{12}\}/);
  assert.ok(onNotBook, 'onNotBook() not found');
  assert.match(onNotBook[0], /CarlosAppointmentPatientLink\.unlink\(/);
});

/*
 * Repeat booking (Add page): onButRepeat() runs calculateEndTime(), whose "."
 * no-show rule reads #demographic_no, BEFORE submitForm(). The link must be
 * settled first or a hand-edited "." name keeps the stale link and misses
 * status N. noShowRule() is that rule's condition, as calculateEndTime() has it.
 */
function noShowRule(page) {
  return page.nameField.value.substring(0, 1) === '.' && page.demographicField.value === '' ? 'N' : '';
}

test('settle(form) reconciles a hand-edited "." name before the no-show rule reads the link', () => {
  const page = setup({ name: 'SMITH, JOHN', demographicNo: '101', provider: 'Dr. Who' });
  type(page.nameField, '.late walk-in');
  assert.equal(noShowRule(page), '', 'unsettled, the stale link hides the no-show');
  PatientLink.settle(page.form);
  assert.equal(noShowRule(page), 'N');
  assert.equal(page.providerField.value, '');
  PatientLink.submitForm(page.form);
  assert.deepEqual(page.form.submitted, [{ keyword: '.late walk-in', demographicNo: '', mrp: '' }]);
  assert.equal(page.unlinks(), 1, 'settling twice (settle, then submitForm) unlinks once');
});

test('settle(field) commits a highlighted row, and settle() ignores targets with no controller', () => {
  const page = setup();
  type(page.nameField, 'jo');
  page.link.highlight(JONES);
  PatientLink.settle(page.nameField);
  assert.equal(page.demographicField.value, '202');
  assert.doesNotThrow(() => PatientLink.settle(null));
  assert.doesNotThrow(() => PatientLink.settle(new FakeElement()));
});

test('addappointment.jsp onButRepeat settles the link before calculateEndTime()', () => {
  const source = fs.readFileSync(path.join(JSP_DIR, 'addappointment.jsp'), 'utf8');
  const onButRepeat = source.match(/function onButRepeat\(\) \{[\s\S]*?\n\s{12}\}/);
  assert.ok(onButRepeat, 'onButRepeat() not found');
  const settleAt = onButRepeat[0].indexOf('CarlosAppointmentPatientLink.settle(');
  const calcAt = onButRepeat[0].indexOf('if (calculateEndTime())');
  assert.ok(settleAt >= 0, 'onButRepeat must settle the patient link');
  assert.ok(calcAt > settleAt, 'the settle must precede the "." no-show rule in calculateEndTime()');
});

/*
 * The browser check must not print patient identifiers: runCheck() logs a
 * thrown message verbatim, and the check may be pointed at a database that is
 * not the FAKE- demo set. Values reach messages only through describe().
 */
const TYPEAHEAD_CHECK = path.join(__dirname, 'appointment-patient-typeahead-playwright-checks.js');

test('the typeahead browser check describes field values without printing them', () => {
  const { describe } = require(TYPEAHEAD_CHECK);
  for (const secret of ['FAKE-SMITH, JOHN', '101', '999998']) {
    assert.ok(!describe(secret).includes(secret), 'a value must never be echoed');
  }
  assert.equal(describe(''), 'empty');
  assert.equal(describe(null), 'absent');
  assert.equal(describe(undefined), 'absent');
});

test('the typeahead browser check routes every patient-derived value in a message through describe()', () => {
  const source = fs.readFileSync(TYPEAHEAD_CHECK, 'utf8');
  const patientDerived = /\b(item|first|state|shown|searchTerm)\b|posted\.get\((?!'status'\))/;
  const leaks = [...source.matchAll(/\$\{([^}]*)\}/g)]
    .map((match) => match[1].trim())
    .filter((expr) => patientDerived.test(expr) && !expr.startsWith('describe('));
  assert.deepEqual(leaks, [], 'wrap these interpolations in describe()');
});
