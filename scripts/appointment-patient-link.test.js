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

  addEventListener(type, listener) {
    (this.listeners[type] = this.listeners[type] || []).push(listener);
  }

  fire(type, props = {}) {
    const event = { type, ...props };
    for (const listener of this.listeners[type] || []) {
      listener(event);
    }
    return event;
  }
}

const SMITH = {
  value: '101', provider: 'Dr. Who', formattedName: 'SMITH, JOHN', alert: 'Latex allergy',
};
const JONES = { value: '202', provider: 'Dr. No', formattedName: 'JONES, ANN' };

function setup({ name = '', demographicNo = '', provider = '' } = {}) {
  const form = new FakeElement();
  const nameField = new FakeElement(name);
  nameField.form = form;
  const demographicField = new FakeElement(demographicNo);
  const providerField = new FakeElement(provider);
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
  page.form.fire('submit');
  assert.equal(page.demographicField.value, '');
});

test('submit with a highlighted row commits it', () => {
  const page = setup();
  type(page.nameField, 'jo');
  page.link.highlight(JONES);
  page.form.fire('submit');
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

test('addappointment.jsp Do Not Book drops the patient link', () => {
  const source = fs.readFileSync(path.join(JSP_DIR, 'addappointment.jsp'), 'utf8');
  const onNotBook = source.match(/function onNotBook\(\) \{[\s\S]*?\n\s{12}\}/);
  assert.ok(onNotBook, 'onNotBook() not found');
  assert.match(onNotBook[0], /CarlosAppointmentPatientLink\.unlink\(/);
});
