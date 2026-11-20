/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
// A small deterministic DOM for running the shipped mycarlos-send.js and mycarlos-send-dialog.js
// under node:test: only what those scripts use, and only the selectors they use (any other throws).
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const SCRIPTS = path.join(__dirname, '../../src/main/webapp/share/javascript/demographic');
const camel = name => name.replace(/-([a-z])/g, (_, letter) => letter.toUpperCase());

class Element {
    constructor(owner, tag = 'div', part) {
        this.owner = owner; this.tagName = tag.toUpperCase(); this.dataset = part ? {mycarlosPart: part} : {};
        this.children = []; this.listeners = {}; this.textContent = ''; this.hidden = false; this.disabled = false;
        this.parent = null;
    }
    get isConnected() { return true; }
    append(...children) {
        children.filter(child => typeof child !== 'string').forEach(child => { child.parent = this; this.children.push(child); });
        // A select with nothing selected selects its first option, as a browser's does.
        if (this.tagName === 'SELECT' && this.options.length && !this.options.some(option => option.value === this._value)) this._value = this.options[0].value;
    }
    replaceChildren(...children) { this.children = []; if (this.tagName === 'SELECT') this._value = ''; this.append(...children); }
    get rows() { return this.children.filter(child => child.tagName === 'TR'); }
    get options() { return this.children.filter(child => child.tagName === 'OPTION'); }
    get lastElementChild() { return this.children.at(-1); }
    get value() { return this._value || ''; }
    set value(value) { this._value = this.tagName === 'SELECT' && !this.options.some(option => option.value === value) ? '' : value; }
    addEventListener(type, handler) { (this.listeners[type] ||= []).push(handler); }
    dispatch(type, fields = {}) {
        const event = {type, target: this, defaultPrevented: false, preventDefault() { this.defaultPrevented = true; }, ...fields};
        (this.listeners[type] || []).forEach(handler => handler(event));
        return event;
    }
    click(fields = {}) { if (!this.disabled) return this.owner.dispatchClick(this, fields); return null; }
    focus() { this.owner.focused = this; }
    descendants() { return this.children.flatMap(child => [child, ...child.descendants()]); }
    matches(selector) {
        let match = selector.match(/^\[data-mycarlos-part="([^"]+)"\]$/);
        if (match) return this.dataset.mycarlosPart === match[1];
        if (selector === 'input[name="CSRF-TOKEN"]') return this.tagName === 'INPUT' && this.name === 'CSRF-TOKEN';
        match = selector.match(/^\[data-([a-z-]+)\]$/);
        if (match) return camel(match[1]) in this.dataset;
        throw new Error('the fake DOM does not know ' + selector);
    }
    querySelectorAll(selector) { return this.descendants().filter(element => element.matches(selector)); }
    querySelector(selector) { return this.querySelectorAll(selector)[0] || null; }
    closest(selector) { for (let element = this; element; element = element.parent) if (element.matches(selector)) return element; return null; }
}

class Dialog extends Element {
    constructor(owner) { super(owner, 'dialog'); this.open = false; this.showModal = () => { this.open = true; }; }
    close() { if (!this.open) return; this.open = false; this.dispatch('close'); }
    // What a browser does for Esc: keydown (the page's capturing listeners first, then the dialog's,
    // when the focus is inside it), then a cancel, then close. A browser may make a repeated cancel
    // without other input not cancelable, so a refused one closes anyway.
    pressEscape({focusInside = true, cancelable = true} = {}) {
        if (this.owner.dispatchKey({key: 'Escape'}).defaultPrevented) return false;
        if (focusInside && this.dispatch('keydown', {key: 'Escape'}).defaultPrevented) return false;
        if (this.dispatch('cancel', {cancelable}).defaultPrevented && cancelable) return false;
        this.close(); return true;
    }
}

class FakeDocument {
    constructor() { this.ids = {}; this.clickHandlers = []; this.keyHandlers = []; this.focused = null; this.pageInputs = []; }
    getElementById(id) { return this.ids[id] || null; }
    createElement(tag) { return new Element(this, tag); }
    querySelectorAll(selector) {
        if (selector !== 'input[name="CSRF-TOKEN"]') throw new Error('the fake DOM does not know ' + selector);
        return [...this.pageInputs, ...Object.values(this.ids).flatMap(root => root.querySelectorAll(selector))];
    }
    querySelector(selector) { return this.querySelectorAll(selector)[0] || null; }
    addEventListener(type, handler) {
        if (type === 'click') this.clickHandlers.push(handler);
        else if (type === 'keydown') this.keyHandlers.push(handler);
    }
    dispatchKey(fields) {
        const event = {type: 'keydown', defaultPrevented: false, preventDefault() { this.defaultPrevented = true; }, ...fields};
        this.keyHandlers.forEach(handler => handler(event));
        return event;
    }
    dispatchClick(target, fields = {}) {
        const event = target.dispatch('click', fields);
        this.clickHandlers.forEach(handler => handler(event));
        return event;
    }
}

const COPIES = ['retry', 'retried', 'error', 'busy', 'sync', 'dateNotRecorded', 'sending', 'sent'];
const STATUSES = ['staged', 'waiting', 'picked_up', 'expired', 'removed', 'fenced', 'error'];

/** One send root (the standalone page's main, or a dialog), with every part the scripts look for. */
function sendRoot(document, {dialog = false, data = {}, rows = [], devices = [], token = 'FAKE-CSRF'} = {}) {
    const root = dialog ? new Dialog(document) : new Element(document, 'main');
    Object.assign(root.dataset, {context: '/FAKE', patient: '6001', document: '44'}, data);
    const parts = {};
    const add = (parent, tag, part) => { const element = new Element(document, tag, part); parent.append(element); parts[part] = element; return element; };
    add(root, 'p', 'document-title'); add(root, 'span', 'service-date'); add(root, 'div', 'message');
    const form = add(root, 'form', 'form');
    const input = new Element(document, 'input'); input.name = 'CSRF-TOKEN'; input.value = token; form.append(input);
    const select = add(form, 'select', 'device');
    devices.forEach(id => { const option = new Element(document, 'option'); option.value = id; select.append(option); });
    if (devices.length) select.value = devices[0];
    add(form, 'p', 'no-device'); add(form, 'button', 'send'); add(form, 'button', 'check');
    add(form, dialog ? 'button' : 'a', 'new');
    const history = add(root, 'tbody', 'history');
    rows.forEach(item => {
        const row = new Element(document, 'tr');
        row.dataset = {delivery: item.deliveryId, device: item.deviceKeyId, status: item.status};
        row.append(new Element(document, 'td'), new Element(document, 'td')); history.append(row);
    });
    add(root, 'p', 'empty'); add(root, 'button', 'more');
    if (dialog) add(root, 'button', 'close');
    const copy = add(root, 'div', 'copy');
    COPIES.forEach(key => { const span = new Element(document, 'span'); span.dataset.copy = key; span.textContent = key; copy.append(span); });
    STATUSES.forEach(key => { const span = new Element(document, 'span'); span.dataset.status = key; span.textContent = key; copy.append(span); });
    return {root, parts};
}

/** Runs the shipped scripts against `document`; fetch calls wait until the test answers them. */
function load(document, {scripts = ['mycarlos-send.js'], origin = 'https://localhost', globals = {}} = {}) {
    const calls = [];
    const window = {location: {origin, href: origin + '/FAKE/demographic/PortalDelivery?demographicNo=6001&documentId=44'},
        history: {replaced: [], replaceState(state, title, url) { this.replaced.push(String(url)); }}, csrfTokenReady: Promise.resolve()};
    // `globals` replaces browser globals, as for a browser that lacks one.
    const context = {URL, URLSearchParams, Date, console, Promise, AbortSignal, AbortController, setTimeout, document, window, ...globals,
        fetch: async (url, options) => {
            const call = {url, options}; calls.push(call);
            return new Promise((resolve, reject) => {
                call.resolve = (payload, status = 200) => resolve({ok: status < 400, status, json: async () => payload});
                call.reject = reject;
                // As a browser's fetch does, a request whose signal aborts fails with the signal's reason.
                const signal = options && options.signal;
                if (signal) signal.addEventListener('abort',
                    () => reject(signal.reason || new DOMException('The operation was aborted.', 'AbortError')), {once: true});
            });
        }};
    vm.createContext(context);
    // A script is a file name, or {name, transform} to run a changed copy of it.
    scripts.forEach(script => {
        const {name, transform = source => source} = typeof script === 'string' ? {name: script} : script;
        vm.runInContext(transform(fs.readFileSync(path.join(SCRIPTS, name), 'utf8')), context);
    });
    return {window, calls};
}

const tick = () => new Promise(resolve => setImmediate(resolve));
const delivery = (index, status = 'picked_up') => ({
    deliveryId: `00000000-0000-4000-8000-${String(index).padStart(12, '0')}`,
    deviceKeyId: `FAKE-historical-device-${index}`, queuedAt: '2026-10-26T00:00:00Z', status
});
const state = (deliveries, extra = {}) => ({ok: true, title: 'FAKE Document', devices: [{deviceKeyId: 'FAKE-current-device',
    acceptedAt: '2026-10-26T00:00:00Z', fingerprint: '0'.repeat(64)}], deliveries, nextCursor: null, serviceDate: null, ...extra});

module.exports = {Element, Dialog, FakeDocument, sendRoot, load, tick, delivery, state};
