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
const test = require('node:test');
const assert = require('node:assert/strict');
const { isFocusCheckNotApplicable } = require('./chartspace-playwright-checks');

// Nothing keyboard-reachable is only "n/a" when the page is settled with a disabled
// toggle and no focusable element; OK and NO_ACCESS both render that way.
const CASES = [
  ['NO_ACCESS', true, 0, true],
  ['OK', true, 0, true],
  ['ERROR', true, 0, false],
  ['OK', false, 0, false],
  ['NO_ACCESS', true, 1, false],
  ['NO_ACCESS', false, 0, false],
];

for (const [state, disabled, focusable, expected] of CASES) {
  test(`isFocusCheckNotApplicable(${state}, ${disabled}, ${focusable}) is ${expected}`, () => {
    assert.equal(isFocusCheckNotApplicable(state, disabled, focusable), expected);
  });
}
