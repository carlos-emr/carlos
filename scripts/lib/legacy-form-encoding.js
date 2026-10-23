/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Encode the saved values that legacy encounter-form pages print back into their boxes (issue #5090).
//
// The older BC form pages print each saved value straight into the page:
//   <input type="text" name="pg1_ht" value="<%= props.getProperty("pg1_ht", "") %>">
// so a height of 5'6" or a note with a quoted word is cut off at the first double quote when the
// form is reopened, and the next save stores the cut-off text. A value with <, > or & is changed too.
// This module finds every JSP output expression (<%= ... %>) that prints a saved value (anything read
// from `props`) and wraps it in the CARLOS null-safe encoder for the place it is printed:
//   inside a quoted HTML attribute       SafeEncode.forHtmlAttribute(...)
//   between tags                         SafeEncode.forHtmlContent(...)
//   inside a JavaScript string literal   SafeEncode.forJavaScript(...)
//   inside a JavaScript comment          SafeEncode.forJavaScript(...)  (the server still prints it there)
// The browser decodes each of these back to the exact characters, so the box shows, and the next save
// posts, what was typed.
//
// Left as they are, because they print no typed text:
//   - a bare expression inside a tag on a checkbox or radio input: FrmRecordHelp loads a boolean column
//     as the fixed attribute text checked='checked' (or ""), which the box needs unencoded;
//   - a ternary whose branches are string literals (selected="", print-URL fragments): a constant;
//   - expressions inside a JSP custom tag's attribute (<carlos:encode value='<%= ... %>'/>), which the
//     tag already encodes.
// Anything else (a saved value printed as bare JavaScript code, or into an event-handler attribute) is
// reported as unhandled, so it is fixed by hand rather than guessed at.
//
// Used by scripts/encode-legacy-form-values.js (the codemod) and scripts/legacy-form-value-encoding.test.js
// (which pins that every listed page has no unencoded saved value left).
'use strict';

const path = require('node:path');

const FORM_DIR = path.join(__dirname, '..', '..', 'src', 'main', 'webapp', 'WEB-INF', 'jsp', 'form');

/** The BC form pages issue #5090 names: BC-AR (original, 2007, 2012), both Birth Summaries, both
 * BC-NewBorn versions, BC-INR, Chart Checklist and the BC Health Passport. */
const BC_LEGACY_FORM_PAGES = [
  'formbcarpg1.jsp', 'formbcarpg2.jsp', 'formbcarpg3.jsp',
  'formbcar2007pg1.jsp', 'formbcar2007pg2.jsp', 'formbcar2007pg3.jsp',
  'formbcar2012pg1.jsp', 'formbcar2012pg2.jsp', 'formbcar2012pg3.jsp',
  'formbcbirthsummo.jsp', 'formbcbirthsummo2008.jsp',
  'formbcnewbornpg1.jsp', 'formbcnewbornpg2.jsp', 'formbcnewbornpg3.jsp',
  'formBCNewBorn2008pg1.jsp', 'formBCNewBorn2008pg2.jsp', 'formBCNewBorn2008pg3.jsp',
  'formbcinr.jsp', 'formbcclientchartchecklist.jsp',
  'formbchppg1.jsp', 'formbchppg2.jsp',
];

const ENCODER_FOR = {
  ATTR: 'forHtmlAttribute',
  TEXT: 'forHtmlContent',
  JS_STRING: 'forJavaScript',
  // Inside a // or /* */ comment a raw newline (or */) would end the comment; forJavaScript escapes both.
  JS_COMMENT: 'forJavaScript',
};

const SAFE_ENCODE_IMPORT = '<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>';

/**
 * Every JSP output expression in a page, with the HTML context it is printed in.
 * ctx is one of: TEXT, ATTR (quoted attribute value), ATTR_EVENT (quoted on* attribute), ATTR_UNQUOTED,
 * TAG_BARE (inside a tag, not in an attribute value), CUSTOM_TAG (an attribute of a JSP custom tag such as
 * carlos:encode), CUSTOM_TAG_IN_ATTR (an attribute of a custom tag that is itself inside a quoted attribute value; the
 * entry carries the tag's text as nestedText), JS_STRING (inside a <script> string literal), JS_COMMENT (inside a
 * <script> comment) or JS_CODE (inside <script>, outside a string or comment).
 */
function scanOutputs(src) {
  const outputs = [];
  let i = 0;
  let mode = 'text'; // text | tag | script
  let quote = null; // open quote inside a tag attribute or a script string
  let attr = '';
  let afterEquals = false;
  let tagName = '';
  let closingTag = false;
  let tagStart = -1;
  let scriptComment = null; // '//' or '/*' inside a script block
  while (i < src.length) {
    if (src.startsWith('<%--', i)) {
      const end = src.indexOf('--%>', i);
      i = end < 0 ? src.length : end + 4;
      continue;
    }
    if (src.startsWith('<%=', i)) {
      const end = src.indexOf('%>', i + 3);
      if (end < 0) break;
      const raw = src.slice(i + 3, end);
      const lead = raw.length - raw.trimStart().length;
      const expr = raw.trim();
      let ctx;
      if (mode === 'script') ctx = scriptComment ? 'JS_COMMENT' : quote ? 'JS_STRING' : 'JS_CODE';
      else if (mode === 'tag') {
        if (tagName.includes(':')) ctx = 'CUSTOM_TAG';
        else if (quote) ctx = /^on/i.test(attr) ? 'ATTR_EVENT' : 'ATTR';
        else ctx = afterEquals ? 'ATTR_UNQUOTED' : 'TAG_BARE';
      } else ctx = 'TEXT';
      outputs.push({
        start: i, end: end + 2, exprStart: i + 3 + lead, exprEnd: i + 3 + lead + expr.length,
        expr, ctx, attr: mode === 'tag' ? attr : '', tagName: mode === 'tag' ? tagName : '', tagStart,
      });
      // An unquoted attribute value made of the expression ends with it.
      if (mode === 'tag' && !quote && afterEquals) { afterEquals = false; attr = ''; }
      i = end + 2;
      continue;
    }
    if (src.startsWith('<%', i)) {
      const end = src.indexOf('%>', i + 2);
      i = end < 0 ? src.length : end + 2;
      continue;
    }
    const c = src[i];
    if (mode === 'script') {
      if (scriptComment === '//') { if (c === '\n') scriptComment = null; i++; continue; }
      if (scriptComment === '/*') { if (src.startsWith('*/', i)) { scriptComment = null; i += 2; } else i++; continue; }
      if (quote) {
        if (c === '\\') { i += 2; continue; }
        if (c === quote || (c === '\n' && quote !== '`')) quote = null;
        i++;
        continue;
      }
      if (/^<\/script/i.test(src.slice(i, i + 8))) { mode = 'text'; continue; }
      if (src.startsWith('//', i)) { scriptComment = '//'; i += 2; continue; }
      if (src.startsWith('/*', i)) { scriptComment = '/*'; i += 2; continue; }
      if (c === '"' || c === "'" || c === '`') quote = c;
      i++;
      continue;
    }
    if (mode === 'tag') {
      if (quote) {
        // A JSP custom tag inside the quoted value (value="<carlos:encode value='<%= v %>' context="htmlAttribute"/>")
        // prints its own output into this attribute: its expressions are that tag's attributes, not this value's text.
        const nested = c === '<' && /^<([A-Za-z][\w.-]*:[\w.-]+)/.exec(src.slice(i, i + 64));
        if (nested) {
          const found = [];
          let j = i + nested[0].length;
          while (j < src.length) {
            if (src.startsWith('<%=', j)) {
              const end = src.indexOf('%>', j + 3);
              if (end < 0) { j = src.length; break; }
              const raw = src.slice(j + 3, end);
              const lead = raw.length - raw.trimStart().length;
              const expr = raw.trim();
              found.push({
                start: j, end: end + 2, exprStart: j + 3 + lead, exprEnd: j + 3 + lead + expr.length,
                expr, ctx: 'CUSTOM_TAG_IN_ATTR', attr, tagName: nested[1], tagStart: i,
              });
              j = end + 2;
              continue;
            }
            if (src.startsWith('/>', j)) { j += 2; break; }
            if (src[j] === '>') { j++; break; }
            j++;
          }
          const nestedText = src.slice(i, j);
          for (const output of found) outputs.push({ ...output, nestedText });
          i = j;
          continue;
        }
        if (c === quote) { quote = null; attr = ''; afterEquals = false; }
        i++;
        continue;
      }
      if (c === '>') {
        mode = /^script$/i.test(tagName) && !closingTag && src[i - 1] !== '/' ? 'script' : 'text';
        quote = null; attr = ''; afterEquals = false;
        i++;
        continue;
      }
      if (afterEquals) {
        if (c === '"' || c === "'") { quote = c; i++; continue; }
        if (/\s/.test(c)) { i++; continue; }
        // An unquoted value runs to the next space or '>'.
        let j = i;
        while (j < src.length && !/[\s>]/.test(src[j]) && !src.startsWith('<%', j)) j++;
        if (src.startsWith('<%', j)) { i = j; continue; }
        afterEquals = false; attr = ''; i = j;
        continue;
      }
      const m = /^([^\s=>/"'<]+)\s*=\s*/.exec(src.slice(i, i + 200));
      if (m) { attr = m[1]; afterEquals = true; i += m[0].length; continue; }
      i++;
      continue;
    }
    // text
    if (c === '<') {
      if (src.startsWith('<!--', i)) { i += 4; continue; }
      const m = /^<\/?([A-Za-z][A-Za-z0-9:_.-]*)/.exec(src.slice(i, i + 64));
      if (m) {
        mode = 'tag'; tagName = m[1]; closingTag = src[i + 1] === '/'; tagStart = i;
        attr = ''; afterEquals = false; quote = null;
        i += m[0].length;
        continue;
      }
    }
    i++;
  }
  return outputs;
}

/** True when an expression reads a saved form value (the page's `props`). */
function readsSavedValue(expr) {
  return /\bprops\s*\.\s*getProperty\s*\(/.test(expr);
}

/** True when an expression is already encoded. */
function isEncoded(expr) {
  return /^SafeEncode\.for[A-Za-z]+\(/.test(expr) || /\bEncode\.for[A-Za-z]+\(/.test(expr);
}

/** Splits "cond ? a : b" at its top-level ? and :, or returns null. */
function splitTernary(expr) {
  let depth = 0;
  let q = null;
  let qm = -1;
  for (let i = 0; i < expr.length; i++) {
    const c = expr[i];
    if (q) { if (c === '\\') { i++; continue; } if (c === q) q = null; continue; }
    if (c === '"' || c === "'") { q = c; continue; }
    if (c === '(') depth++;
    else if (c === ')') depth--;
    else if (depth === 0 && c === '?' && qm < 0) qm = i;
    else if (depth === 0 && c === ':' && qm >= 0) return [expr.slice(0, qm), expr.slice(qm + 1, i), expr.slice(i + 1)];
  }
  return null;
}

/** True for a ternary whose two branches are string literals: it prints one of two constants. */
function isConstantTernary(expr) {
  const parts = splitTernary(expr);
  if (!parts) return false;
  const literal = /^\s*"(?:[^"\\]|\\.)*"\s*$/;
  return literal.test(parts[1]) && literal.test(parts[2]);
}

/** The tag text an output sits in (from its '<' to the next '>' outside quotes), for checkbox checks. */
function tagText(src, output) {
  let q = null;
  for (let i = output.tagStart; i < src.length; i++) {
    if (src.startsWith('<%', i)) { const end = src.indexOf('%>', i); i = end + 1; continue; }
    const c = src[i];
    if (q) { if (c === q) q = null; continue; }
    if (c === '"' || c === "'") { q = c; continue; }
    if (c === '>') return src.slice(output.tagStart, i + 1);
  }
  return src.slice(output.tagStart);
}

/**
 * Classifies every saved-value output of a page.
 * Returns { encode: [...outputs needing a wrapper], unhandled: [...], kept: [...] } where each
 * kept/unhandled entry has a `reason`.
 */
function classify(src) {
  const encode = [];
  const unhandled = [];
  const kept = [];
  for (const o of scanOutputs(src)) {
    if (!readsSavedValue(o.expr)) continue;
    if (o.ctx === 'CUSTOM_TAG_IN_ATTR') {
      // The custom tag's own output lands in this attribute: it must encode for an attribute itself (the tag's
      // default HTML-content context leaves '"' as it is), and its value must not be encoded a second time.
      if (isEncoded(o.expr)) unhandled.push({ ...o, reason: `${o.tagName} encodes an already encoded value` });
      else if (/\bcontext\s*=\s*["'](htmlAttribute|uriComponent)["']/.test(o.nestedText)) {
        kept.push({ ...o, reason: 'custom tag encoding for this attribute' });
      } else unhandled.push({ ...o, reason: `${o.tagName} inside the ${o.attr || 'quoted'} attribute without an attribute context` });
      continue;
    }
    if (isEncoded(o.expr)) { kept.push({ ...o, reason: 'encoded' }); continue; }
    if (o.ctx === 'CUSTOM_TAG') { kept.push({ ...o, reason: 'custom tag attribute' }); continue; }
    if (isConstantTernary(o.expr)) { kept.push({ ...o, reason: 'constant' }); continue; }
    if (o.ctx === 'TAG_BARE') {
      if (/\btype\s*=\s*["']?(checkbox|radio)\b/i.test(tagText(src, o))) kept.push({ ...o, reason: 'checkbox state' });
      else unhandled.push({ ...o, reason: 'bare value inside a tag that is not a checkbox or radio' });
      continue;
    }
    if (ENCODER_FOR[o.ctx]) encode.push({ ...o, encoder: ENCODER_FOR[o.ctx] });
    else unhandled.push({ ...o, reason: `saved value printed in ${o.ctx}` });
  }
  return { encode, unhandled, kept };
}

/** Adds the SafeEncode import after the page's last page import (or its first directive). */
function ensureImport(src) {
  if (/import\s*=\s*"[^"]*\bio\.github\.carlos_emr\.carlos\.utility\.SafeEncode\b/.test(src)) return src;
  const imports = [...src.matchAll(/<%@\s*page\s+import\s*=[^%]*%>[ \t]*\r?\n?/g)];
  const anchor = imports.length ? imports[imports.length - 1] : /<%@[^%]*%>[ \t]*\r?\n?/.exec(src);
  if (!anchor) return SAFE_ENCODE_IMPORT + '\n' + src;
  const at = anchor.index + anchor[0].length;
  const nl = anchor[0].endsWith('\n') ? '' : '\n';
  return src.slice(0, at) + nl + SAFE_ENCODE_IMPORT + '\n' + src.slice(at);
}

/** Rewrites a page so every saved value is encoded for its context. Idempotent. */
function rewrite(src) {
  const { encode } = classify(src);
  if (!encode.length) return { src, changed: 0 };
  let out = '';
  let last = 0;
  for (const o of encode) {
    out += src.slice(last, o.exprStart) + `SafeEncode.${o.encoder}(${o.expr})`;
    last = o.exprEnd;
  }
  out += src.slice(last);
  return { src: ensureImport(out), changed: encode.length };
}

function lineOf(src, index) {
  return src.slice(0, index).split('\n').length;
}

module.exports = {
  BC_LEGACY_FORM_PAGES, FORM_DIR, SAFE_ENCODE_IMPORT,
  classify, ensureImport, isConstantTernary, lineOf, rewrite, scanOutputs,
};
