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
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

// Execute the actual JSP constructor calls. The live browser regression separately
// uses the bundled Toast UI implementation and intercepts every off-host request.
for (const [page, mount] of [
  ['admin/resourcebaseurl.jsp', 'resource_helpHtml_editor'],
  ['messenger/CreateMessage.jsp', 'messagediv'],
  ['messenger/ViewMessage.jsp', 'viewer'],
]) {
  test(`${page} disables editor telemetry and retains HTML sanitization`, () => {
    const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp', page), 'utf8');
    const constructors = [...source.matchAll(/new (?:Editor|toastui\.Editor\.factory)\(\{[\s\S]*?\n\s*\}\);/g)];
    assert.equal(constructors.length, 1, 'Expected the page editor initialization');
    const options = [];
    function Editor(configuration) { options.push(configuration); }
    Editor.factory = Editor;
    const sanitized = [];
    vm.runInNewContext(constructors[0][0], {
      Editor, toastui: { Editor }, content: 'Synthetic message',
      document: {
        getElementById: id => ({ id }),
        querySelector: selector => ({ id: selector.slice(1) }),
      },
      DOMPurify: { sanitize: html => { sanitized.push(html); return 'sanitized HTML'; } },
    });
    assert.equal(options.length, 1);
    assert.equal(options[0].usageStatistics, false, 'Usage beacons must be explicitly disabled');
    assert.equal(options[0].el.id, mount);
    assert.equal(options[0].customHTMLSanitizer('synthetic HTML'), 'sanitized HTML');
    assert.deepEqual(sanitized, ['synthetic HTML']);
  });
}
