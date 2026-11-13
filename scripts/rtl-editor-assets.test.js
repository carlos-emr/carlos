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
 * Rich Text Letter editor: the files it loads and writes on its own.
 *
 * Executes the relevant blocks of the real editControl2.js.
 */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const WEBAPP = path.join(__dirname, '../src/main/webapp');
const EDIT_CONTROL_2_JS = path.join(WEBAPP, 'WEB-INF/eform-assets/editControl2.js');
const LETTER_PAGE = 'http://127.0.0.1:8080/carlos/eform/efmformadd_data?fid=1&demographic_no=17';

function slice(startMarker, endMarker) {
  const source = fs.readFileSync(EDIT_CONTROL_2_JS, 'utf8');
  const start = source.indexOf(startMarker);
  const end = source.indexOf(endMarker, start);
  assert.ok(start >= 0 && end > start, `${startMarker} was not found in editControl2.js`);
  return source.slice(start, end);
}

/** Maps a URL under the /carlos context onto the file the webapp serves for it. */
function webappFile(url) {
  const pathname = new URL(url).pathname;
  assert.ok(pathname.startsWith('/carlos/'), `${url} is outside the CARLOS context`);
  return path.join(WEBAPP, pathname.slice('/carlos/'.length));
}

test('the editor loads imageControl from a script the webapp ships when the page lacks it', () => {
  // A stored letter that never loaded library/eforms/imageControl.js gets it added on DOM ready.
  // The fallback pointed at share/javascript/eforms/imageControl.js, which does not exist.
  const appended = [];
  const context = {
    document: {
      createElement: () => ({ setAttribute(name, value) { this[name] = value; } }),
      getElementsByTagName: () => [{ appendChild: (script) => appended.push(script) }],
    },
  };
  context.jQuery = () => ({ ready: (callback) => callback() });
  context.jQuery.fn = {};
  vm.createContext(context);
  vm.runInContext(slice('function addJavascript(', 'function popup('), context);

  assert.equal(appended.length, 1);
  const file = webappFile(new URL(appended[0].src, LETTER_PAGE).href);
  assert.ok(fs.existsSync(file), `the fallback script ${appended[0].src} resolves to ${file}, which does not exist`);
});

function exportedName(subject) {
  const saved = [];
  const context = {
    Blob: function Blob(parts, options) { this.parts = parts; this.options = options; },
    saveAs: (blob, name) => saved.push(name),
    cancelPendingFaxSubmission() {},
    assertMeasurementHistoryReady() {},
    document: {
      getElementById: (id) => (id === 'subject'
        ? { value: subject }
        : { contentWindow: { document: { documentElement: { outerHTML: '<html></html>' } } } }),
    },
  };
  vm.createContext(context);
  vm.runInContext(slice('function exportFileName(', 'function doEdit() {'), context);
  context.doExport();
  assert.equal(saved.length, 1);
  return saved[0];
}

test('an exported letter template is named after the letter subject', () => {
  assert.equal(exportedName('Referral to cardiology'), 'Referral to cardiology.rtl');
});

test('an exported letter template with no subject is still a named .rtl file', () => {
  // ".rtl" alone is a hidden dotfile name; Chromium saved it as "rtl.html", which the template
  // dropdown never lists.
  assert.equal(exportedName(''), 'letter.rtl');
  assert.equal(exportedName('   '), 'letter.rtl');
});

test('an exported letter template name holds no path or reserved characters', () => {
  assert.equal(exportedName('Re: Dr/Smith <urgent>'), 'Re Dr Smith urgent.rtl');
});

test('the shipped stamps.js placeholder runs and leaves the letter ImgArray alone', () => {
  // The stored letter loads ../eform/displayImage.do?imagefile=stamps.js on every open. With no
  // file the request 404ed and the browser refused the HTML error page as a script. The seeded
  // placeholder must be valid script that changes nothing: the letter declares ImgArray itself.
  const stampsJs = path.join(WEBAPP, 'WEB-INF/eform-assets/stamps.js');
  assert.ok(fs.existsSync(stampsJs), 'WEB-INF/eform-assets/stamps.js is not shipped');
  const imgArray = ['Smith|smith_signature.png'];
  const context = { ImgArray: imgArray };
  vm.createContext(context);
  vm.runInContext(fs.readFileSync(stampsJs, 'utf8'), context);

  assert.equal(context.ImgArray, imgArray);
  assert.deepEqual(Object.keys(context), ['ImgArray']);
});
