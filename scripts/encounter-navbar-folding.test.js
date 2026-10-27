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
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

for (const modern of [false, true]) {
  const source = fs.readFileSync(path.join(__dirname, modern
    ? '../src/main/webapp/js/newCaseManagementView.js.jsp'
    : '../src/main/webapp/encounter/js/encounter.js'), 'utf8');
  const start = source.indexOf('function listDisplay(');
  const end = source.indexOf(modern ? 'function grabEnter(' : '// Navbar Loader', start);
  assert.ok(start >= 0 && end > start);

  test(`${modern ? 'modern' : 'legacy'} cached Forms list folds flex rows and restores the layout`, () => {
    const items = Array.from({length: 9}, () => ({style: {display: 'flex'}}));
    const images = Object.fromEntries([0, 5, 8].map(index => [`imgforms${index}`, {
      listeners: new Map(),
      addEventListener(name, callback) { this.listeners.set(name, callback); },
      removeEventListener(name, callback) { if (this.listeners.get(name) === callback) this.listeners.delete(name); },
    }]));
    const elements = {...images, formslist: {getElementsByTagName: () => items, getAttribute: () => null}};
    const paths = {transparent: '/carlos/images/clear.gif', expand: '/carlos/encounter/graphics/expand.gif',
      collapse: '/carlos/messenger/img/collapse.gif'};
    const context = {document: {getElementById: id => elements[id]},
      encounterConfig: {images: paths}, imgfunc: {}, obj: {}, ctx: '/carlos',
      $: id => elements[id], $A: Array.from, Event: {stop() {}},
      Element: {
        observe: (element, name, callback) => element.addEventListener(name, callback),
        stopObserving: (element, name, callback) => element.removeEventListener(name, callback),
      }};
    vm.createContext(context);
    vm.runInContext(`Function.prototype.bindAsEventListener = function(object, ...args) {
      return event => this.call(object, event, ...args);
    };`, context);
    vm.runInContext(source.slice(start, end), context);
    context.listDisplay('forms', 6);
    assert.deepEqual(items.slice(6).map(item => item.style.display), ['none', 'none', 'none']);
    assert.deepEqual(items.slice(0, 6).map(item => item.style.display), Array(6).fill('flex'));
    assert.equal(images.imgforms5.src, paths.expand);
    images.imgforms5.listeners.get('click')({});
    assert.deepEqual(items.map(item => item.style.display), Array(9).fill('flex'));
    assert.equal(images.imgforms0.src, paths.collapse);
    images.imgforms0.listeners.get('click')({});
    assert.deepEqual(items.slice(6).map(item => item.style.display), ['none', 'none', 'none']);
  });
}
