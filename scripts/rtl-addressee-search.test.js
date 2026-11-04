/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
'use strict';

/*
 * The Rich Text Letter's addressee box: typing a specialist's name searches the professional
 * specialist directory, and choosing a result fills the hidden CopyTo address block that the
 * "Paste Selected" button inserts into the letter.
 *
 * The search requested ../encounter/oscarConsultationRequest/searchProfessionalSpecialist.json.
 * No such route exists (".json" is also excluded from Struts outright), so every search answered
 * 404 and the box only ever said "Search failed". The directory route is /searchProfessionalSpecialist.
 *
 * The real search block is executed from editControl2.js against a minimal DOM.
 */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const EDIT_CONTROL_2_JS = path.join(__dirname, '../src/main/webapp/WEB-INF/eform-assets/editControl2.js');

class FakeNode {
  constructor(tagName) {
    this.tagName = tagName;
    this.children = [];
    this.attributes = {};
    this.listeners = {};
    this.style = {};
    this.value = '';
    this.className = '';
    this.offsetWidth = 140;
    this.offsetHeight = 20;
    this.offsetLeft = 0;
    this.offsetTop = 0;
    this.scrollLeft = 0;
    this.scrollTop = 0;
    this.offsetParent = null;
  }

  get firstChild() { return this.children[0] || null; }

  get textContent() {
    return this.children.map((child) => (child.nodeType === 3 ? child.data : child.textContent)).join('');
  }

  set textContent(value) { this.children = [{ nodeType: 3, data: String(value) }]; }

  set innerHTML(value) { this.children = value ? [{ nodeType: 3, data: String(value) }] : []; }

  appendChild(child) { this.children.push(child); return child; }

  removeChild(child) { this.children = this.children.filter((node) => node !== child); return child; }

  setAttribute(name, value) { this.attributes[name] = String(value); }

  getAttribute(name) { return Object.prototype.hasOwnProperty.call(this.attributes, name) ? this.attributes[name] : null; }

  addEventListener(type, listener) { this.listeners[type] = listener; }

  click() { this.listeners.click.call(this); }
}

function searchBlock() {
  const source = fs.readFileSync(EDIT_CONTROL_2_JS, 'utf8');
  const start = source.indexOf('//-- Autocomplete  script --');
  const end = source.indexOf('// filesaver.js');
  assert.ok(start >= 0 && end > start, 'the addressee search block was not found in editControl2.js');
  return source.slice(start, end);
}

function loadSearch() {
  const elements = {};
  for (const id of ['tempBin', 'referral_name', 'consultantFilter', 'CopyTo']) {
    elements[id] = new FakeNode(id === 'tempBin' ? 'div' : 'input');
  }
  const requests = [];
  class FakeXhr {
    open(method, url) { this.method = method; this.url = url; }
    setRequestHeader() {}
    send() { requests.push(this); }
    respond(status, body) {
      this.status = status;
      this.response = body;
      this.onload.call(this);
    }
  }
  const context = {
    document: {
      getElementById: (id) => elements[id] || null,
      createElement: (tag) => new FakeNode(tag),
    },
    XMLHttpRequest: FakeXhr,
    setTimeout: (callback) => callback(),
    console: { error() {}, log() {} },
    isNaN,
  };
  vm.createContext(context);
  vm.runInContext(searchBlock(), context);
  return { context, elements, requests };
}

const directoryEntry = (fields) => Object.assign({
  id: 9001, firstName: 'FAKE-Alice', lastName: 'FAKE-Specialist-01', streetAddress: '', fax: null,
}, fields);

test('the addressee search asks the professional specialist directory route', () => {
  const { context, requests } = loadSearch();
  context.consultantSearch('FAKE Spec');

  assert.equal(requests.length, 1);
  assert.equal(requests[0].method, 'GET');
  assert.equal(requests[0].url, '../searchProfessionalSpecialist?keyword=FAKE%20Spec');
});

test('choosing a full directory entry fills the addressee name, address block and filter', () => {
  const { context, elements, requests } = loadSearch();
  context.consultantSearch('FAKE');
  requests[0].respond(200, JSON.stringify([directoryEntry({
    streetAddress: '123 Example St, Faketown', fax: '555-555-8001',
  })]));

  const option = elements.tempBin.children[0].children[0];
  assert.equal(option.textContent, 'FAKE-Specialist-01, FAKE-Alice');
  option.click();

  assert.equal(elements.referral_name.value, 'FAKE-Specialist-01, FAKE-Alice');
  assert.equal(elements.consultantFilter.value, '9001');
  assert.equal(elements.CopyTo.value, 'FAKE-Alice FAKE-Specialist-01\r\n123 Example St, Faketown\r\nFax: 555-555-8001');
});

test('a directory entry with no address or fax pastes no "null" text', () => {
  // The directory leaves these columns empty for many specialists; JSON null was concatenated in
  // as the literal word and pasted into the letter as "null" and "Fax: null".
  const { context, elements, requests } = loadSearch();
  context.consultantSearch('FAKE');
  requests[0].respond(200, JSON.stringify([directoryEntry({ streetAddress: null, fax: null })]));

  elements.tempBin.children[0].children[0].click();

  assert.equal(elements.CopyTo.value, 'FAKE-Alice FAKE-Specialist-01');
});

test('a refused or failed search says so instead of offering results', () => {
  const { context, elements, requests } = loadSearch();
  context.consultantSearch('FAKE');
  requests[0].respond(403, '<html>forbidden</html>');

  assert.equal(elements.tempBin.textContent, 'Search failed. Please try again.');
});

test('the minimum-length hint asks for a specialist name', () => {
  const { context, elements, requests } = loadSearch();
  context.consultantSearch('F');

  assert.equal(requests.length, 0);
  assert.match(elements.tempBin.textContent, /specialist/i);
  assert.doesNotMatch(elements.tempBin.textContent, /patient/i);
});
