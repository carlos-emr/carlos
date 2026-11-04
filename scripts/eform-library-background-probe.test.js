/** Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const { storedBackgroundSnapshot, expectedBackgrounds, imageIdentity, assertLibraryBackgrounds } = require('./eform-library-background-probe');

const base = 'https://127.0.0.1/carlos';
const names = [
  '2025_06_12_PCXX108060A_Regional_Community_Pain_Self-_Management_Program_Referral_-_Form_NWM-1[1].png',
  '2025_06_12_PCXX108060A_Regional_Community_Pain_Self-_Management_Program_Referral_-_Form_NWM-2[1].png',
];
const snapshot = () => names.map((name, index) => ({ id: `BGImage${index + 1}`, count: 1, tagName: 'IMG', src: '${oscar_image_path}' + name }));
const expected = () => expectedBackgrounds(snapshot());
const imageUrl = (name) => `${base}/eform/displayImage?imagefile=${encodeURIComponent(name)}`;
const actual = () => names.map((name, index) => ({ id: `BGImage${index + 1}`, count: 1, complete: true,
  width: 750, height: 1000, src: imageUrl(name) }));
const responses = () => names.map((name) => ({ url: imageUrl(name), status: 200, contentType: 'image/png' }));

test('stored filenames preserve the actual corpus punctuation instead of historical derivative names', () => {
  assert.deepEqual(expected(), names.map((imageName, index) => ({ id: `BGImage${index + 1}`, imageName })));
});

test('stored background validation refuses missing, duplicate and non-image elements', () => {
  assert.throws(() => expectedBackgrounds([]), /Missing/);
  for (const change of [{ count: 0 }, { count: 2 }, { tagName: 'DIV' }, { id: 'different' }]) {
    const input = snapshot();
    Object.assign(input[0], change);
    assert.throws(() => expectedBackgrounds(input), /exactly one image/);
  }
});

test('stored backgrounds require literal contained oscar image references', () => {
  for (const source of ['https://remote.invalid/p.png', 'data:image/png;base64,AA', 'p.png', null,
    '${oscar_image_path}', '${oscar_image_path}../x.png', '${oscar_image_path}a\\x.png',
    '${oscar_image_path}a.png?imagefile=other', '${oscar_image_path}a.png#other',
    '${oscar_image_path}${other}', '${oscar_image_path}a\0.png']) {
    const input = snapshot();
    input[0].src = source;
    assert.throws(() => expectedBackgrounds(input), /reference|filename/);
  }
});

test('DOM snapshot reads the browser-decoded editor value and parses it as a separate inert document', () => {
  const calls = [];
  const decoded = '<html>browser-decoded stored template</html>';
  class Parser {
    parseFromString(html, type) {
      calls.push([html, type]);
      if (calls.length === 1) return { querySelectorAll(selector) {
        assert.equal(selector, 'textarea[name="formHtml"]');
        return [{ value: decoded, innerHTML: '&lt;wrong-double-encoded-source&gt;' }];
      } };
      assert.equal(html, decoded);
      return { querySelectorAll(selector) {
        const index = ['[id="BGImage1"]', '[id="BGImage2"]'].indexOf(selector);
        assert.notEqual(index, -1);
        return [{ tagName: 'IMG', getAttribute(name) {
          assert.equal(name, 'src');
          return '${oscar_image_path}' + names[index];
        } }];
      } };
    }
  }
  // This unit verifies the DOM boundary contract, not an HTML parser substitute.
  // The installed acceptance browser executes the same function with Chromium's
  // actual DOMParser on the real manager editor and stored corpus template.
  const result = vm.runInNewContext(`(${storedBackgroundSnapshot.toString()})('editor response')`, { DOMParser: Parser });
  assert.deepEqual(JSON.parse(JSON.stringify(result)), snapshot());
  assert.deepEqual(calls, [['editor response', 'text/html'], [decoded, 'text/html']]);
});

test('DOM snapshot refuses absent or ambiguous stored HTML editors', () => {
  for (const count of [0, 2]) {
    class Parser { parseFromString() { return { querySelectorAll() { return Array(count).fill({ value: '' }); } }; } }
    assert.throws(() => vm.runInNewContext(`(${storedBackgroundSnapshot.toString()})('editor')`, { DOMParser: Parser }), /exactly one/);
  }
});

test('image URL identity decodes exact query values including punctuation and ampersands', () => {
  assert.equal(imageIdentity(`${base}/eform/displayImage.do?cache=1&imagefile=${encodeURIComponent('A&B [1].png')}`, base), 'A&B [1].png');
  const input = snapshot();
  input[0].src = '${oscar_image_path}A&B [1].png';
  assert.equal(expectedBackgrounds(input)[0].imageName, 'A&B [1].png');
});

test('image URL identity rejects alternate hosts, contexts, routes, duplicate parameters and credentials', () => {
  for (const url of ['https://other.invalid/carlos/eform/displayImage?imagefile=a.png',
    'https://127.0.0.1/other/eform/displayImage?imagefile=a.png',
    `${base}/eform/notDisplayImage?imagefile=a.png`, `${base}/eform/displayImage?imagefile=a.png&imagefile=b.png`,
    `${base}/eform/displayImage?imagefile=a.png#fragment`, 'https://user:secret@127.0.0.1/carlos/eform/displayImage?imagefile=a.png',
    'not a URL']) assert.equal(imageIdentity(url, base), null);
});

test('both exact decoded backgrounds and successful image responses provide the library proof', () => {
  const proof = assertLibraryBackgrounds(expected(), actual(), responses(), base);
  assert.deepEqual(proof, { bgImageCount: 2, images: expected(), renderSurfaceUsable: true });
});

test('swapped or substitute decoded images fail even when both image requests succeeded', () => {
  for (const src of [imageUrl(names[1]), imageUrl('substitute.png'), 'data:image/png;base64,AA']) {
    const rendered = actual();
    rendered[0].src = src;
    assert.throws(() => assertLibraryBackgrounds(expected(), rendered, responses(), base), /different image/);
  }
});

test('missing duplicate incomplete and tiny runtime backgrounds cannot pass', () => {
  assert.throws(() => assertLibraryBackgrounds(expected(), actual().slice(1), responses(), base), /Missing/);
  for (const change of [{ count: 2 }, { complete: false }, { width: 0 }, { height: 1 }]) {
    const rendered = actual();
    Object.assign(rendered[0], change);
    assert.throws(() => assertLibraryBackgrounds(expected(), rendered, responses(), base), /duplicated|decode/);
  }
});

test('loaded image state alone cannot replace successful exact displayImage response evidence', () => {
  for (const entries of [[], responses().slice(1), responses().map((entry) => ({ ...entry, status: 304 })),
    responses().map((entry) => ({ ...entry, contentType: 'text/html' })),
    responses().map((entry) => ({ ...entry, url: entry.url.replace('127.0.0.1', 'other.invalid') }))]) {
    assert.throws(() => assertLibraryBackgrounds(expected(), actual(), entries, base), /successful displayImage/);
  }
});

test('a failed exact image response remains a failure even alongside an eventual success', () => {
  assert.throws(() => assertLibraryBackgrounds(expected(), actual(), [...responses(), { ...responses()[0], status: 404 }], base), /successful displayImage/);
});
