/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// The eForm signature library (signatureControl.jsp) shows a saved signature when an eForm opens and a new
// one after the signature pad saves. This runs the library's own initialize() against a small jQuery
// stand-in: a saved signature value is applied as an image source only, and only when it is the stored-
// signature address CARLOS gives it; anything else shows no image.
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const jsp = fs.readFileSync(path.join(__dirname,
  '../src/main/webapp/WEB-INF/jsp/library/eforms/signatureControl.jsp'), 'utf8');
const CONTEXT = '/carlos';
const STORED = `${CONTEXT}/imageRenderingServlet?source=signature_stored&digitalSignatureId=`;
/** The note's text in the rendered library; the server writes the translated note here. */
const NOTE = 'FAKE note: the saved signature cannot be shown here.';

/** The library as the server sends it, with eForm signatures on. */
function render(contextPath) {
  const script = jsp
    .replace(/<%--[\s\S]*?--%>/g, '')
    .replace(/<%@[\s\S]*?%>/g, '')
    .replaceAll('<%= request.getContextPath() %>', contextPath)
    .replace(/<%[\s\S]*?%>/g, '')
    .replace(/<fmt:[^>]*\/>/g, '')
    .replaceAll('${carlos:forJavaScript(signatureNotShownNote)}', NOTE);
  assert.doesNotMatch(script, /<%|<fmt:|\$\{/, 'the library carries a server expression this test does not render');
  return script;
}

/** One element of the stand-in page: its value, the markup strings it was given, and its child nodes. */
function element(tag) {
  return { tag, value: '', attrs: {}, children: [], markup: [], css: {} };
}

/** A jQuery stand-in for the calls the library makes, over a page with the eForm's signature elements. */
function page(savedValue) {
  const nodes = {
    '#signatureDisplay': element('div'),
    '#signatureInput': element('div'),
    '#signatureValue': Object.assign(element('input'), { value: savedValue }),
    '.DoNotPrint': element('div'),
  };
  const wrap = node => {
    const api = {
      val(value) {
        if (value === undefined) return node ? node.value : undefined;
        if (node) node.value = value;
        return api;
      },
      html(markup) {
        if (markup === undefined) return '';
        if (node) { node.markup.push(String(markup)); node.children = []; }
        return api;
      },
      empty() { if (node) { node.children = []; } return api; },
      text(value) {
        if (value === undefined) return node ? node.text || '' : '';
        if (node) node.text = String(value);
        return api;
      },
      append(child) { if (node) node.children.push(child.node); return api; },
      attr(name, value) {
        if (typeof name === 'object') { Object.assign(node.attrs, name); return api; }
        if (value === undefined) return node.attrs[name];
        node.attrs[name] = String(value);
        return api;
      },
      css(name, value) { if (node) node.css[name] = value; return api; },
      node,
    };
    return api;
  };
  const jQuery = selector => {
    const created = /^<(\w+)\s*\/?>$/.exec(selector);
    if (created) return wrap(element(created[1]));
    return wrap(nodes[selector]);
  };
  const context = vm.createContext({ jQuery, alert() {}, Math });
  context.$ = jQuery;
  context.window = context;
  vm.runInContext(`${render(CONTEXT)}\nthis.signatureControl = signatureControl;`, context);
  return { context, nodes };
}

/** The images now in the signature display, as tag and attributes. */
function images(nodes) {
  return nodes['#signatureDisplay'].children.filter(child => child.tag === 'img').map(child => child.attrs);
}

/** The notes now in the signature display, as text. */
function notes(nodes) {
  return nodes['#signatureDisplay'].children.filter(child => child.tag === 'span').map(child => child.text);
}

/** Every markup string the display was given, with no image built from a saved value. */
function markupWith(nodes, text) {
  return nodes['#signatureDisplay'].markup.filter(markup => markup.includes(text));
}

test('a saved stored-signature address is shown as the image source', () => {
  const saved = `${STORED}42&r=17`;
  const { context, nodes } = page(saved);
  context.signatureControl.initialize({ eform: true });
  assert.deepEqual(images(nodes), [{ src: saved, alt: 'signature' }]);
  assert.deepEqual(markupWith(nodes, '<img'), []);
});

test('a saved stored-signature address without the refresh number is shown too', () => {
  const saved = `${STORED}7`;
  const { context, nodes } = page(saved);
  context.signatureControl.initialize({ eform: true });
  assert.deepEqual(images(nodes), [{ src: saved, alt: 'signature' }]);
});

test('a saved signature value is applied as an image source only, never as markup', () => {
  const saved = `${STORED}42' data-extra='1`;
  const { context, nodes } = page(saved);
  context.signatureControl.initialize({ eform: true });
  assert.deepEqual(images(nodes), []);
  assert.deepEqual(markupWith(nodes, 'data-extra'), []);
});

for (const [label, saved] of [
  ['another site', `https://example.invalid/imageRenderingServlet?source=signature_stored&digitalSignatureId=1`],
  ['a protocol-relative address', `//example.invalid/imageRenderingServlet?source=signature_stored&digitalSignatureId=1`],
  ['a scripted address', 'javascript:void(0)'],
  ['another image source', `${CONTEXT}/imageRenderingServlet?source=signature_preview&digitalSignatureId=1`],
  ['a signature id that is not a number', `${STORED}1a`],
  ['an extra parameter', `${STORED}1&r=2&next=3`],
  ['a fragment', `${STORED}1#top`],
  ['plain text', 'FAKE signature text'],
  ['a dot segment in the path', `/carlos/../imageRenderingServlet?source=signature_stored&digitalSignatureId=1`],
  ['an escaped address with markup after the id', `${CONTEXT}/imageRenderingServlet?source=signature_stored&amp;digitalSignatureId=1' data-extra='1`],
  ['a doubly escaped address', `${CONTEXT}/imageRenderingServlet?source=signature_stored&amp;amp;digitalSignatureId=1`],
  ['an escape in place of the query mark', `${CONTEXT}/imageRenderingServlet?amp;source=signature_stored&amp;digitalSignatureId=1`],
  ['an escaped address with an escaped extra parameter', `${CONTEXT}/imageRenderingServlet?source=signature_stored&amp;digitalSignatureId=1&amp;r=2&amp;next=3`],
  ['an escaped address from another site', `https://example.invalid/imageRenderingServlet?source=signature_stored&amp;digitalSignatureId=1`],
]) {
  test(`a saved value that is ${label} shows no image, only the note`, () => {
    const { context, nodes } = page(saved);
    context.signatureControl.initialize({ eform: true });
    assert.deepEqual(images(nodes), []);
    assert.deepEqual(notes(nodes), [NOTE]);
    assert.deepEqual(markupWith(nodes, saved), []);
  });
}

test('an empty saved value shows nothing, not even the note', () => {
  const { context, nodes } = page('');
  context.signatureControl.initialize({ eform: true });
  assert.deepEqual(images(nodes), []);
  assert.deepEqual(notes(nodes), []);
  assert.deepEqual(nodes['#signatureDisplay'].markup, []);
});

for (const [label, saved, shown] of [
  ['with its refresh number', `${CONTEXT}/imageRenderingServlet?source=signature_stored&amp;digitalSignatureId=42&amp;r=17`, `${STORED}42&r=17`],
  ['without the refresh number', `${CONTEXT}/imageRenderingServlet?source=signature_stored&amp;digitalSignatureId=7`, `${STORED}7`],
]) {
  test(`an older HTML-escaped stored-signature address ${label} is shown at its plain address`, () => {
    const { context, nodes } = page(saved);
    context.signatureControl.initialize({ eform: true });
    assert.deepEqual(images(nodes), [{ src: shown, alt: 'signature' }]);
    assert.deepEqual(notes(nodes), []);
    // The form keeps the value it was saved with until the pad saves a new signature.
    assert.equal(nodes['#signatureValue'].value, saved);
  });
}

test('numeric width and height are set on the image; other values are left out', () => {
  const saved = `${STORED}42`;
  const sized = page(saved);
  sized.context.signatureControl.initialize({ eform: true, width: 200, height: '60' });
  assert.deepEqual(images(sized.nodes), [{ src: saved, alt: 'signature', width: '200', height: '60' }]);

  const unsized = page(saved);
  unsized.context.signatureControl.initialize({ eform: true, width: "200' data-extra='1", height: '60px' });
  assert.deepEqual(images(unsized.nodes), [{ src: saved, alt: 'signature' }]);
});

test('a signature saved from the pad is shown and kept as the stored-signature address', () => {
  const { context, nodes } = page('');
  context.signatureControl.initialize({ eform: true });
  context.signatureHandler({ isDirty: false, isSave: true, storedImageUrl: `${STORED}99` });
  const [image] = images(nodes);
  assert.match(image.src, /^\/carlos\/imageRenderingServlet\?source=signature_stored&digitalSignatureId=99&r=\d+$/);
  assert.equal(image.alt, 'signature');
  assert.equal(nodes['#signatureValue'].value, image.src);
  assert.deepEqual(markupWith(nodes, '<img'), []);
});

test('a pad save the check refuses keeps the saved value and shows the note', () => {
  const saved = `${STORED}5`;
  const { context, nodes } = page(saved);
  context.signatureControl.initialize({ eform: true });
  context.signatureHandler({ isDirty: false, isSave: true, storedImageUrl: `/car los/imageRenderingServlet?source=signature_stored&digitalSignatureId=99` });
  assert.deepEqual(images(nodes), []);
  assert.deepEqual(notes(nodes), [NOTE]);
  assert.equal(nodes['#signatureValue'].value, saved);
});
