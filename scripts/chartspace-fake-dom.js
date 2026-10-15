/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Minimal fake DOM for the ChartSpace node tests (no jsdom dependency).
 *
 * Supports only what chartspace.js and chartspace-allergies.js use:
 * createElement, getElementById (attached nodes only), className, textContent
 * (assigning replaces all children), appendChild (re-parents), removeChild,
 * firstChild, parentNode, set/get/has/removeAttribute, hidden, disabled,
 * focus() + document.activeElement, contains, addEventListener, and
 * querySelector('.modal.show') -> null. Any other selector throws so an
 * unsupported use fails loudly instead of passing by accident.
 *
 * Shared by chartspace-allergies.test.js and chartspace-shell.test.js; the name
 * does not match *.test.js, so node --test does not run it as a test file.
 */
'use strict';

class FakeText {
  constructor(data) {
    this.nodeType = 3;
    this.data = String(data);
    this.parentNode = null;
  }

  get textContent() {
    return this.data;
  }
}

class FakeElement {
  constructor(doc, tagName) {
    this.nodeType = 1;
    this.ownerDocument = doc;
    this.tagName = String(tagName).toUpperCase();
    this.childNodes = [];
    this.parentNode = null;
    this.attributes = new Map();
    this.className = '';
    this.hidden = false;
    this.disabled = false;
    this.listeners = {};
  }

  get id() {
    return this.getAttribute('id') || '';
  }

  get firstChild() {
    return this.childNodes[0] || null;
  }

  get children() {
    return this.childNodes.filter((n) => n.nodeType === 1);
  }

  get textContent() {
    return this.childNodes.map((n) => n.textContent).join('');
  }

  set textContent(value) {
    this.childNodes.slice().forEach((n) => this.removeChild(n));
    const text = value === null || value === undefined ? '' : String(value);
    if (text !== '') {
      this.appendChild(new FakeText(text));
    }
  }

  appendChild(child) {
    if (child.parentNode) {
      child.parentNode.removeChild(child);
    }
    this.childNodes.push(child);
    child.parentNode = this;
    return child;
  }

  removeChild(child) {
    const i = this.childNodes.indexOf(child);
    if (i === -1) {
      throw new Error('removeChild: not a child');
    }
    this.childNodes.splice(i, 1);
    child.parentNode = null;
    return child;
  }

  setAttribute(name, value) {
    this.attributes.set(name, String(value));
  }

  getAttribute(name) {
    return this.attributes.has(name) ? this.attributes.get(name) : null;
  }

  hasAttribute(name) {
    return this.attributes.has(name);
  }

  removeAttribute(name) {
    this.attributes.delete(name);
  }

  contains(node) {
    for (let n = node; n; n = n.parentNode) {
      if (n === this) {
        return true;
      }
    }
    return false;
  }

  focus() {
    this.ownerDocument.activeElement = this;
  }

  addEventListener(type, fn) {
    (this.listeners[type] = this.listeners[type] || []).push(fn);
  }

  /** Test helper: fires listeners with a minimal event object. */
  dispatch(type, props) {
    const event = Object.assign({
      type,
      target: this,
      defaultPrevented: false,
      preventDefault() { this.defaultPrevented = true; }
    }, props);
    (this.listeners[type] || []).forEach((fn) => fn.call(this, event));
    return event;
  }

  /** Test helper: a real click on a disabled button fires nothing. */
  click() {
    if (!this.disabled) {
      this.dispatch('click');
    }
  }
}

class FakeDocument {
  constructor() {
    this.listeners = {};
    this.body = new FakeElement(this, 'body');
    this.activeElement = this.body;
  }

  createElement(tag) {
    return new FakeElement(this, tag);
  }

  getElementById(id) {
    return descendants(this.body).find((n) => n.id === id) || null;
  }

  querySelector(selector) {
    if (selector === '.modal.show') {
      return null;
    }
    throw new Error('fake DOM: unsupported selector ' + selector);
  }

  addEventListener(type, fn) {
    (this.listeners[type] = this.listeners[type] || []).push(fn);
  }

  dispatch(type, props) {
    return FakeElement.prototype.dispatch.call(this, type, Object.assign({ target: this.activeElement }, props));
  }
}

/** All element descendants of root (excluding root), depth first. */
function descendants(root) {
  const out = [];
  (function walk(node) {
    node.children.forEach((c) => {
      out.push(c);
      walk(c);
    });
  })(root);
  return out;
}

/** Elements under root whose tag matches (case-insensitive). */
function byTag(root, tag) {
  return descendants(root).filter((n) => n.tagName === tag.toUpperCase());
}

/** Elements under root carrying the given class token. */
function byClass(root, cls) {
  return descendants(root).filter((n) => n.className.split(/\s+/).includes(cls));
}

/**
 * Builds an element and appends it to parent. attrs: id and other attributes;
 * the props className, hidden and disabled are set as properties.
 */
function make(doc, parent, tag, attrs) {
  const node = doc.createElement(tag);
  Object.entries(attrs || {}).forEach(([k, v]) => {
    if (k === 'className' || k === 'hidden' || k === 'disabled') {
      node[k] = v;
    } else {
      node.setAttribute(k, v);
    }
  });
  parent.appendChild(node);
  return node;
}

module.exports = { FakeDocument, FakeElement, descendants, byTag, byClass, make };
