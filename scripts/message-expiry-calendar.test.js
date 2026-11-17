/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

// The System Message and Facility Message editors' Expiry Day calendar (calendar/oscarCalendarPopup?type=caisi).
// Picking a day runs the popup's typeCaisiDate with the opener form and field names the editor's calendar link
// gives it. The editors' forms lost those names when the Struts 1 tags went, so the pick wrote nothing.

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const read = file => fs.readFileSync(path.join(__dirname, '..', file), 'utf8');
const popup = read('src/main/webapp/WEB-INF/jsp/calendar/oscarCalendarPopup.jsp');

function typeCaisiDateSource() {
    const start = popup.indexOf('function typeCaisiDate(');
    const end = popup.indexOf('//-->', start);
    assert.ok(start >= 0 && end > start, 'could not find typeCaisiDate');
    return popup.slice(start, end);
}

/** The editor's form name, its field names and the names its calendar link passes to the popup. */
function editor(file) {
    const jsp = read(file);
    const formStart = jsp.indexOf('<form');
    const form = jsp.slice(formStart, jsp.indexOf('</form>', formStart));
    const tag = form.slice(0, form.indexOf('>'));
    const link = /openerForm=([A-Za-z]+)&amp;openerElement=([A-Za-z_.]+)/.exec(form);
    assert.ok(link, `${file} has no calendar link`);
    return {
        formName: (/\bname="([^"]+)"/.exec(tag) || [])[1],
        fields: [...form.matchAll(/<(?:input|select|textarea)\b[^>]*\bname="([^"]+)"/g)].map(m => m[1]),
        openerForm: link[1],
        openerElement: link[2],
    };
}

/** A page holding one form, the way a browser exposes it: by name on document and in document.forms. */
function openerPage({ formName, fields }) {
    const elements = fields.map(name => ({ name, value: '' }));
    const form = { name: formName, elements: { namedItem: name => elements.find(el => el.name === name) || null } };
    for (const el of elements) form.elements[el.name] = el;
    const document = {
        forms: { namedItem: name => (formName && name === formName ? form : null) },
        getElementsByName: name => elements.filter(el => el.name === name),
    };
    if (formName) document[formName] = form;
    return { document, field: name => elements.find(el => el.name === name) };
}

/** Picks 5 October 2026 in a popup opened by the given page. */
function pick(page, openerForm, openerElement, closedOpener = false) {
    const self = { closed: 0, close() { this.closed++; } };
    vm.runInNewContext(`${typeCaisiDateSource()}\ntypeCaisiDate(openerForm, openerElement, 2026, 10, 5);`,
        { opener: closedOpener ? { closed: true } : { closed: false, document: page.document }, self, openerForm, openerElement });
    return self.closed;
}

for (const file of ['src/main/webapp/WEB-INF/jsp/caisicore/SystemMessageForm.jsp',
                    'src/main/webapp/WEB-INF/jsp/caisicore/FacilityMessageForm.jsp']) {
    test(`picking a day writes it into ${path.basename(file, '.jsp')}'s Expiry Day box and closes the popup`, () => {
        const shape = editor(file);
        const page = openerPage(shape);
        assert.ok(shape.fields.includes(shape.openerElement), `the form has no ${shape.openerElement} field`);
        assert.equal(pick(page, shape.openerForm, shape.openerElement), 1);
        assert.equal(page.field(shape.openerElement).value, '2026-10-5');
    });
}

test('a form found only by its field name still gets the date', () => {
    const page = openerPage({ formName: undefined, fields: ['system_message.expiry_day'] });
    assert.equal(pick(page, 'systemMessageForm', 'system_message.expiry_day'), 1);
    assert.equal(page.field('system_message.expiry_day').value, '2026-10-5');
});

test('a pick whose form and field are not on the page writes nothing and still closes the popup', () => {
    const page = openerPage({ formName: 'otherForm', fields: ['other.field'] });
    assert.equal(pick(page, 'missingForm', 'missing.field'), 1);
    assert.equal(page.field('other.field').value, '');
});

test('a pick after the editor window closed just closes the popup', () => {
    assert.equal(pick(openerPage({ formName: 'systemMessageForm', fields: [] }), 'systemMessageForm',
        'system_message.expiry_day', true), 1);
});
