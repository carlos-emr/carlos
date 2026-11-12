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

test('no BC form page breaks a tag open over the field inside it, or prints a checkbox state outside its box', () => {
  // BC-AR 2007's doula boxes sat in a "<td" with no ">": the browser read the <input> as the cell's attributes, so the
  // saved doula was never shown and never posted again. BC-AR 2012's substance-use "No" box printed its saved state as
  // text after the box, so it never showed ticked and the next Save cleared it.
  for (const page of lib.BC_LEGACY_FORM_PAGES) {
    const lines = read(page).split('\n');
    lines.forEach((line, i) => {
      if (/^\s*<(td|tr|th|table|div|span)\s*$/i.test(line)) {
        const next = lines.slice(i + 1).find(l => l.trim() !== '') || '';
        assert.ok(!/^\s*</.test(next), `${page}:${i + 1}: "${line.trim()}" is left open over "${next.trim().slice(0, 40)}"`);
      }
    });
    const src = lines.join('\n');
    for (const m of src.matchAll(/<input type="checkbox" name="(\w+)"\s*\/>\s*<%=[^%]*props\.getProperty\("\1"/g)) {
      assert.fail(`${page}: the saved state of checkbox ${m[1]} is printed after its box`);
    }
  }
});

test('the BC-AR 2007 and 2012 page 2s run their EPDS restore when the page opens', () => {
  // The radios carry no checked state of their own: a page that defines setEPDSscores but never calls it reopens a
  // record with no answer ticked, and its next Save stores the answers as empty (BC-AR 2012 did, before #5090).
  const calls = (src) => src.split('\n').filter(line => /setEPDSscores\(\)\s*;/.test(line) && !/function\s+setEPDSscores/.test(line));
  assert.ok(calls(read('formbcar2007pg2.jsp')).some(line => /<body[^>]*onload="setEPDSscores\(\);"/.test(line)),
    'formbcar2007pg2.jsp: the body onload calls setEPDSscores()');
  const src2012 = read('formbcar2012pg2.jsp');
  const ready = src2012.indexOf('$(function () {');
  const restore = src2012.indexOf('setEPDSscores();', ready);
  const dirtyCheck = src2012.indexOf("$('form').areYouSure(", ready);
  assert.ok(ready >= 0 && restore > ready && dirtyCheck > restore,
    'formbcar2012pg2.jsp: the DOM-ready handler calls setEPDSscores() before the dirty check records the form');
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
  // nosemgrep: javascript.lang.security.audit.unknown-value-with-script-tag.unknown-value-with-script-tag -- A fixed JSP source string handed to the static encoding scanner under test; it is never rendered or served.
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

test('scanner: a custom tag inside a quoted attribute is judged by its own context, never wrapped again', () => {
  const field = (inner) => `<input type="text" name="x" value="${inner}">`;
  const tag = (expr, context) => `<carlos:encode value='<%= ${expr} %>'${context ? ` context="${context}"` : ''}/>`;
  const saved = 'props.getProperty("x", "")';
  // Encoding for the attribute itself: kept, and the codemod leaves it alone (wrapping it would print "&amp;amp;").
  const safe = field(tag(saved, 'htmlAttribute'));
  assert.equal(lib.rewrite(safe).changed, 0);
  assert.deepEqual(lib.classify(safe).unhandled, []);
  // The tag's default HTML-content context leaves '"' as it is, so inside an attribute it is reported, not kept.
  const defaultContext = lib.classify(field(tag(saved)));
  assert.equal(defaultContext.kept.length, 0);
  assert.match(defaultContext.unhandled[0].reason, /without an attribute context/);
  // A value encoded before the tag encodes it again is reported too.
  const twice = lib.classify(field(tag(`SafeEncode.forHtmlAttribute(${saved})`, 'htmlAttribute')));
  assert.match(twice.unhandled[0].reason, /already encoded/);
});

test('no saved text value is concatenated raw into an input a scriptlet builds', () => {
  // The Chart Checklist builds its free-text description boxes as Java strings ("<input ... value='" + value + "'"),
  // which the output scanner never sees: a saved "'" ended the attribute, and "<" or "&" did not come back as typed.
  const raw = [];
  for (const page of lib.BC_LEGACY_FORM_PAGES) {
    read(page).split('\n').forEach((line, i) => {
      if (/value=\\?'"\s*\+\s*props\.getProperty\(/.test(line)) raw.push(`${page}:${i + 1}`);
    });
  }
  assert.deepEqual(raw, [], `saved values concatenated raw into a value attribute: ${raw.join(', ')}`);
  assert.equal((read('formbcclientchartchecklist.jsp').match(/value='" \+ SafeEncode\.forHtmlAttribute\(props\.getProperty\("desc/g) || []).length, 17);
});

test("each page's date box shows that page's own saved date", () => {
  // BC-NewBorn pages 2 and 3 printed page 1's date in their own date boxes, so a reopened record showed page 1's date
  // there and the next Save stored it over the page's own.
  const wrong = [];
  for (const page of lib.BC_LEGACY_FORM_PAGES) {
    for (const m of read(page).matchAll(/name="(pg\d_formDate)"[^>]*?value="<%=([^%]*)%>"/g)) {
      const fields = [...m[2].matchAll(/props\.getProperty\("(\w+)"/g)].map(match => match[1]);
      if (!fields.length || fields.some(field => field !== m[1])) wrong.push(`${page}: ${m[1]} shows ${fields.join(', ') || 'no saved value'}`);
    }
  }
  assert.deepEqual(wrong, [], wrong.join('; '));
});
