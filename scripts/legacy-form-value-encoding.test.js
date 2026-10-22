/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Issue #5090: the BC form pages print every saved value encoded for where it is printed, so a value with
// a double quote, <, > or & comes back exactly as typed when the form is reopened. These tests pin that the
// codemod (scripts/encode-legacy-form-values.js) reached every box on every page the issue names, and
// cover the scanner on small pages. The browser half (type, save, reopen) is
// scripts/form-bc-saved-value-encoding-playwright-checks.js.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const lib = require('./lib/legacy-form-encoding');

const read = (page) => fs.readFileSync(path.join(lib.FORM_DIR, page), 'utf8');

test('every BC form page the issue names exists', () => {
  assert.equal(lib.BC_LEGACY_FORM_PAGES.length, 21);
  for (const page of lib.BC_LEGACY_FORM_PAGES) assert.ok(fs.existsSync(path.join(lib.FORM_DIR, page)), page);
});

for (const page of lib.BC_LEGACY_FORM_PAGES) {
  test(`${page}: every saved value is encoded for where it is printed`, () => {
    const src = read(page);
    const { encode, unhandled, kept } = lib.classify(src);
    const where = (o) => `line ${lib.lineOf(src, o.start)}: <%= ${o.expr.slice(0, 100)} %>`;
    assert.deepEqual(encode.map(where), [], 'saved values printed without encoding');
    assert.deepEqual(unhandled.map((o) => `${where(o)} (${o.reason})`), [], 'saved values printed where no encoder fits');
    // The codemod is a fixed point: running it again changes nothing.
    assert.equal(lib.rewrite(src).changed, 0);
    // Pages that print a saved value import the encoder.
    if (kept.some((o) => o.reason === 'encoded' && /^SafeEncode\./.test(o.expr))) {
      assert.match(src, /<%@\s*page\s+import="io\.github\.carlos_emr\.carlos\.utility\.SafeEncode"\s*%>/);
    }
  });
}

test('the BC-AR 2007 and 2012 EPDS answers are restored from an encoded string, not printed as script', () => {
  for (const page of ['formbcar2007pg2.jsp', 'formbcar2012pg2.jsp']) {
    const src = read(page);
    assert.match(src, /function epdsScore\(saved\) \{\s*return \/\^\[0-3\]\$\/\.test\(saved\) \? saved : 'null';/, page);
    for (let item = 1; item <= 10; item++) {
      assert.ok(src.includes(`epdsScore('<%= SafeEncode.forJavaScript(props.getProperty("ar2_EPDS${item}", "")) %>')`),
        `${page}: EPDS item ${item}`);
    }
  }
});

test('scanner: a value in a quoted attribute is encoded as an attribute', () => {
  const page = '<%@ page import="java.util.*" %>\n<input type="text" name="h" value="<%= props.getProperty("h", "") %>">';
  const { src, changed } = lib.rewrite(page);
  assert.equal(changed, 1);
  assert.ok(src.includes('value="<%= SafeEncode.forHtmlAttribute(props.getProperty("h", "")) %>"'));
  assert.ok(src.includes(lib.SAFE_ENCODE_IMPORT));
  assert.equal(lib.rewrite(src).changed, 0);
});

test('scanner: script strings and script comments are encoded for JavaScript, text for HTML content', () => {
  const page = [
    '<script type="text/javascript">',
    '  var a = "<%=props.getProperty("a", "")%>"; // "<%=props.getProperty("b", "")%>"',
    "  /* '<%=props.getProperty(\"c\", \"\")%>' */",
    '</script>',
    '<td><%=props.getProperty("d", "")%></td>',
  ].join('\n');
  const { encode } = lib.classify(page);
  assert.deepEqual(encode.map((o) => [o.ctx, o.encoder]), [
    ['JS_STRING', 'forJavaScript'], ['JS_COMMENT', 'forJavaScript'], ['JS_COMMENT', 'forJavaScript'],
    ['TEXT', 'forHtmlContent'],
  ]);
});

test('scanner: a closing script tag ends the script block', () => {
  const page = '<script src="x.js"></script>\n<input value="<%= props.getProperty("v", "") %>">';
  assert.deepEqual(lib.classify(page).encode.map((o) => o.ctx), ['ATTR']);
});

test('scanner: checkbox state, constant ternaries and custom tags are left alone', () => {
  const page = [
    '<input type="checkbox" name="x" <%= props.getProperty("x", "") %>>',
    '<option value="M" <%=props.getProperty("s", "").equals("M") ? "selected" : ""%>>M</option>',
    '<textarea name="n"><carlos:encode value=\'<%= props.getProperty("n", "") %>\'/></textarea>',
    '<script>var u = "a<%= props.getProperty("d", "") == "" ? "&x=1" : "&y=2" %>";</script>',
  ].join('\n');
  const { encode, unhandled, kept } = lib.classify(page);
  assert.deepEqual(encode, []);
  assert.deepEqual(unhandled, []);
  assert.deepEqual(kept.map((o) => o.reason), ['checkbox state', 'constant', 'custom tag attribute', 'constant']);
});

test('scanner: a saved value printed as bare script or bare in a text box tag is reported, not guessed at', () => {
  const page = [
    '<script>var v = <%= props.getProperty("v", "0") %>;</script>',
    '<input type="text" <%= props.getProperty("t", "") %>>',
    '<a onclick="go(\'<%= props.getProperty("e", "") %>\')">x</a>',
  ].join('\n');
  assert.deepEqual(lib.classify(page).unhandled.map((o) => o.ctx), ['JS_CODE', 'TAG_BARE', 'ATTR_EVENT']);
});
