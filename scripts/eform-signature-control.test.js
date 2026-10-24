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
 * The eForm signature pad (library/eforms/signatureControl.jsp).
 *
 * The pad is an iframe on signature_pad/tabletSignature; saving a drawing POSTs to
 * SaveSignatureUpload, which stores a DigitalSignature row for the demographicNo and ModuleType the
 * iframe URL carries. The library used to leave demographicNo empty and send no ModuleType, so the
 * save was persisted as patient -1 and failed on the demographic foreign key (HTTP 500), and even a
 * stored signature could not be rendered back (imageRenderingServlet maps only CONSULTATION,
 * PRESCRIPTION and E_FORM to a read privilege). That is the Rich Text Letter's broken signature box.
 *
 * The JSP's script body is executed here, not restated: an assertion on its text would keep
 * passing against a rewrite that builds the URL differently.
 */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const SIGNATURE_CONTROL_JSP = path.join(__dirname,
  '../src/main/webapp/WEB-INF/jsp/library/eforms/signatureControl.jsp');
const RTL_SIG_HTML = '../signature_pad/tabletSignature?inWindow=true&saveToDB=true&demographicNo=';

function signatureControlScript() {
  const jsp = fs.readFileSync(SIGNATURE_CONTROL_JSP, 'utf8');
  const enabled = jsp.indexOf('if (props.isEFormSignatureEnabled()) {');
  assert.ok(enabled >= 0, 'the eForm signature property gate was not found');
  const start = jsp.indexOf('%>', enabled) + 2;
  const end = jsp.lastIndexOf('<% } %>');
  assert.ok(start > 1 && end > start, 'the signatureControl script body was not found');
  const script = jsp.slice(start, end).replaceAll('<%= request.getContextPath() %>', '/carlos');
  assert.ok(!script.includes('<%'), 'unexpected JSP scriptlet inside the script body');
  return script;
}

/** Runs the library against a page holding the given elements (id -> value). */
function loadSignatureControl({ elements = {}, search = '' } = {}) {
  const nodes = {};
  for (const [id, value] of Object.entries(elements)) {
    nodes[id] = { id, value, html: '', text: '', css: {} };
  }
  nodes.signatureInput = nodes.signatureInput || { id: 'signatureInput', value: '', html: '', text: '', css: {} };
  const wrap = (list) => ({
    length: list.length,
    html(value) {
      if (value === undefined) { return list.length ? list[0].html : undefined; }
      list.forEach((node) => { node.html = String(value); node.text = ''; });
      return this;
    },
    text(value) {
      if (value === undefined) { return list.length ? list[0].text : undefined; }
      list.forEach((node) => { node.text = String(value); node.html = ''; });
      return this;
    },
    val(value) {
      if (value === undefined) { return list.length ? list[0].value : undefined; }
      list.forEach((node) => { node.value = value; });
      return this;
    },
    css(name, value) { list.forEach((node) => { node.css[name] = value; }); return this; },
    append() { return this; },
  });
  const jQuery = (selector) => {
    if (typeof selector !== 'string' || !selector.startsWith('#')) { return wrap([]); }
    const node = nodes[selector.slice(1)];
    return wrap(node ? [node] : []);
  };
  const context = {
    jQuery,
    $: jQuery,
    URLSearchParams,
    alert(message) { throw new Error(`unexpected alert: ${message}`); },
    console,
    document: { getElementById: (id) => nodes[id] || null },
  };
  context.window = { location: { search } };
  vm.createContext(context);
  vm.runInContext(signatureControlScript(), context);
  return { context, nodes };
}

function padUrl(nodes) {
  const match = /<iframe[^>]*\ssrc="([^"]*)"/.exec(nodes.signatureInput.html);
  assert.ok(match, `no signature iframe was written: ${JSON.stringify(nodes.signatureInput)}`);
  return match[1];
}

function query(url) {
  return new URLSearchParams(url.slice(url.indexOf('?') + 1));
}

test('an eForm Generator signature pad stores the drawing against the form patient', () => {
  // eformGenerator.jsp emits signatureControl.initialize({eform:true, height, width, top, left}).
  // The host pages (efmformadd_data / efmshowform_data) always add the hidden #demographicNo.
  const { context, nodes } = loadSignatureControl({ elements: { demographicNo: '17' } });
  context.signatureControl.initialize({ eform: true, height: 100, width: 300, top: 10, left: 20 });

  const url = padUrl(nodes);
  assert.ok(url.startsWith('/carlos/signature_pad/tabletSignature?'), url);
  const params = query(url);
  assert.equal(params.get('demographicNo'), '17');
  assert.equal(params.get('ModuleType'), 'E_FORM');
  assert.equal(params.get('saveToDB'), 'true');
  assert.equal(params.get('inWindow'), 'true');
});

test('the Rich Text Letter signature pad receives the letter patient it passes', () => {
  // editControl2.js passes a URL ending in "demographicNo=" and the number separately; the library
  // ignored the number, so every letter signature was saved as patient -1.
  const { context, nodes } = loadSignatureControl({ elements: { demographicNo: '17' } });
  context.signatureControl.initialize({
    sigHTML: RTL_SIG_HTML,
    demographicNo: '17',
    refreshImage() {},
    signatureInput: '#signatureInput',
  });

  const url = padUrl(nodes);
  assert.equal(url, '../signature_pad/tabletSignature?inWindow=true&saveToDB=true&demographicNo=17&ModuleType=E_FORM');
});

test('the form patient fills in when the caller has no patient number', () => {
  // A saved letter reopened from efmshowform_data has no demographic_no in its own URL.
  const { context, nodes } = loadSignatureControl({ elements: { demographicNo: '23' } });
  context.signatureControl.initialize({
    sigHTML: RTL_SIG_HTML, demographicNo: '', refreshImage() {}, signatureInput: '#signatureInput',
  });

  assert.equal(query(padUrl(nodes)).get('demographicNo'), '23');
});

test('the page URL supplies the patient on a host without the hidden input', () => {
  const { context, nodes } = loadSignatureControl({ search: '?fid=4&demographic_no=31' });
  context.signatureControl.initialize({ eform: true, height: 100, width: 300, top: 10, left: 20 });

  assert.equal(query(padUrl(nodes)).get('demographicNo'), '31');
});

test('a caller URL that already names its patient and module is kept as written', () => {
  const callerUrl = '/carlos/signature_pad/tabletSignature?inWindow=true&saveToDB=true&demographicNo=5&ModuleType=E_FORM';
  const { context, nodes } = loadSignatureControl({ elements: { demographicNo: '17' } });
  context.signatureControl.initialize({
    sigHTML: callerUrl, demographicNo: '5', refreshImage() {}, signatureInput: '#signatureInput',
  });

  assert.equal(padUrl(nodes), callerUrl);
});

test('a patient number that is not digits never reaches the pad URL', () => {
  const { context, nodes } = loadSignatureControl({ elements: { demographicNo: '17' } });
  context.signatureControl.initialize({
    sigHTML: RTL_SIG_HTML,
    demographicNo: '17&saveToDB=false',
    refreshImage() {},
    signatureInput: '#signatureInput',
  });

  const params = query(padUrl(nodes));
  assert.equal(params.get('demographicNo'), '17');
  assert.deepEqual(params.getAll('saveToDB'), ['true']);
});

test('a form with no patient explains why it has no signature pad', () => {
  // A patient-independent eForm carries demographicNo -1: a drawing could never be stored, so the
  // pad would only answer every save with HTTP 500.
  const { context, nodes } = loadSignatureControl({ elements: { demographicNo: '-1' } });
  context.signatureControl.initialize({ eform: true, height: 100, width: 300, top: 10, left: 20 });

  assert.ok(!nodes.signatureInput.html.includes('<iframe'), nodes.signatureInput.html);
  assert.match(nodes.signatureInput.text, /patient/i);
});
