/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const source = fs.readFileSync('src/main/webapp/share/javascript/select.js', 'utf8');

for (const [name, origin] of [
  ['document scroll', {top: -800, left: -40}],
  ['positioned editor', {top: 1200, left: 108}],
  ['scrolled positioned editor', {top: -300, left: 130}],
]) {
  test(`SelectBox anchors choices to its input with ${name}`, () => {
    const element = () => ({style: {}, dataset: {}, children: [],
      appendChild(child) { this.children.push(child); }, addEventListener() {}});
    const context = {document: {createElement: element, documentElement: {}},
      window: {pageYOffset: 800, pageXOffset: 40}};
    vm.createContext(context);
    vm.runInContext(source, context);
    const dropdown = element();
    dropdown.getBoundingClientRect = () => {
      assert.equal(dropdown.style.display, 'block', 'the origin must be measured while visible');
      assert.equal(dropdown.style.top, '0px');
      assert.equal(dropdown.style.left, '0px');
      return origin;
    };
    const widget = Object.create(context.CarlosAutocomplete.SelectBox.prototype);
    Object.assign(widget, {dropdown, input: {getBoundingClientRect: () => ({bottom: 480, left: 160, width: 200})},
      allItems: [{text: 'telephone', value: 'telephone'}]});
    widget.filterAndShow('');
    assert.equal(dropdown.style.top, `${480 - origin.top}px`);
    assert.equal(dropdown.style.left, `${160 - origin.left}px`);
    assert.equal(dropdown.style.width, '200px');
    assert.equal(widget.isOpen, true);
    assert.equal(dropdown.children[0].children[0].dataset.value, 'telephone');
  });
}
