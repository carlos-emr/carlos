/**
 * Copyright (c) 2026 CARLOS Contributors
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 * CARLOS EMR
 */

// escapeNoteText() in newCaseManagementView.js.jsp is what stands between a stored encounter
// note and markup in the chart: the packaged WAF no longer scores the note body for XSS, and
// both the save-on-switch view (completeChangeToView) and the editor (editNote) splice the
// note into HTML through it. CaseManagementCppSaveRegressionTest pins that it is called at
// those sites; this test pins what it does. Run with `npm run test:scripts`.
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const SOURCE = fs.readFileSync(
  path.join(__dirname, '..', 'src', 'main', 'webapp', 'js', 'newCaseManagementView.js.jsp'),
  'utf8',
);

function loadEscapeNoteText() {
  const start = SOURCE.indexOf('function escapeNoteText(text)');
  assert.ok(start >= 0, 'escapeNoteText() is defined in newCaseManagementView.js.jsp');
  const end = SOURCE.indexOf('\n    }\n', start);
  assert.ok(end > start, 'escapeNoteText() ends with the file\'s four-space-indented closing brace');
  const context = {};
  vm.createContext(context);
  vm.runInContext(SOURCE.slice(start, end + '\n    }\n'.length), context);
  assert.equal(typeof context.escapeNoteText, 'function');
  return context.escapeNoteText;
}

// The five entities a textarea's RCDATA and an element's text content decode back, which
// is how the escaped note reaches the clinician unchanged.
function decodeEntities(html) {
  return html
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&amp;/g, '&');
}

test('escapes every character that could open markup or an attribute', () => {
  const escapeNoteText = loadEscapeNoteText();
  assert.equal(
    escapeNoteText('H&P reviewed <b>not bold</b> "quoted" it\'s'),
    'H&amp;P reviewed &lt;b&gt;not bold&lt;/b&gt; &quot;quoted&quot; it&#39;s',
  );
});

test('a note that tries to close the editor textarea stays text', () => {
  const escapeNoteText = loadEscapeNoteText();
  const escaped = escapeNoteText('</textarea><img src=x onerror=alert(1)><script>alert(2)</script>');
  assert.equal(escaped.indexOf('<'), -1, 'no raw "<" survives');
  assert.equal(escaped.indexOf('>'), -1, 'no raw ">" survives');
  assert.ok(!/&(?!amp;|lt;|gt;|quot;|#39;)/.test(escaped), 'every "&" is one of the five entities');
  assert.equal(escaped, '&lt;/textarea&gt;&lt;img src=x onerror=alert(1)&gt;&lt;script&gt;alert(2)&lt;/script&gt;');
});

test('the escaped note decodes back to the clinician\'s exact text', () => {
  const escapeNoteText = loadEscapeNoteText();
  const notes = [
    'BP > 140/90, HR < 60; pt\'s father had COPD "since 2019" & lives alone',
    '&amp; is typed literally here, &lt; too, and 2+2\nsecond line',
    '</textarea><img src=x onerror=alert(1)>',
    '',
    12345,
  ];
  for (const note of notes) {
    assert.equal(decodeEntities(escapeNoteText(note)), String(note));
  }
});
