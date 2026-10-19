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

/*
 * Confines a stylesheet to the elements inside one scope class, so a component library can style
 * CARLOS chrome that shares a document with content it must not touch (issue #3944: Bootstrap and
 * the legacy eForms the floating toolbar sits under).
 *
 * The transform, for every style rule (including those inside @media, @supports, @container and
 * @layer blocks):
 *   - a selector whose first compound is :root, html or body is rewritten onto the scope element
 *     itself, so the custom properties and the base font/colour a framework puts on the document
 *     land on the scope and are inherited by its contents (`:root{--x:1}` -> `.scope{--x:1}`);
 *   - every other selector becomes a descendant of the scope (`.btn` -> `.scope .btn`).
 * Declarations, comments, @charset, @keyframes and @font-face are copied unchanged; any other
 * at-rule (@import, @page, @property, ...) is refused.
 *
 * Contract for the markup: the scope class goes on a plain container, and the framework's
 * component classes on its descendants. A component class on the scope element itself is not
 * matched, which keeps the transform a pure prefix with no duplicated rules.
 *
 * Deliberately dependency-free and strict: it understands the subset of CSS a compiled framework
 * build uses and throws on anything else (nested rules, a selector that starts with a combinator,
 * unbalanced input) rather than emit a rule that silently escapes the scope.
 */

/** At-rules whose block holds ordinary style rules, which must be scoped too. */
const GROUPING_AT_RULES = new Set(['media', 'supports', 'container', 'layer', 'document', '-moz-document']);
/**
 * At-rules copied unchanged because they style no element: the encoding, animation keyframes and
 * font faces. Any other at-rule is refused: @import would pull in an unscoped stylesheet, @page
 * changes the printed page margins of the whole document, and an unknown rule cannot be judged.
 */
const VERBATIM_AT_RULES = new Set(['charset', 'keyframes', '-webkit-keyframes', 'font-face']);
/** A leading :root, html or body compound (not, say, `bodyish` or `html-x`). */
const ROOT_COMPOUND = /^(?::root|html|body)(?![\w-])/i;
const SCOPE_CLASS = /^-?[_a-zA-Z][_a-zA-Z0-9-]*$/;

/** Index just past a quoted string starting at `start`, honouring backslash escapes. */
function skipString(css, start) {
  const quote = css[start];
  let i = start + 1;
  while (i < css.length && css[i] !== quote) {
    if (css[i] === '\\') i += 1;
    i += 1;
  }
  if (i >= css.length) throw new Error(`unterminated string at offset ${start}`);
  return i + 1;
}

/** Index just past a comment starting at `start`. */
function skipComment(css, start) {
  const end = css.indexOf('*/', start + 2);
  if (end < 0) throw new Error(`unterminated comment at offset ${start}`);
  return end + 2;
}

/**
 * Index of the first `stops` character at bracket depth zero, from `start`, skipping strings,
 * comments and escapes; -1 when there is none.
 */
function findTopLevel(css, start, stops) {
  let depth = 0;
  for (let i = start; i < css.length;) {
    const ch = css[i];
    if (ch === '"' || ch === "'") { i = skipString(css, i); continue; }
    if (ch === '/' && css[i + 1] === '*') { i = skipComment(css, i); continue; }
    if (ch === '\\') { i += 2; continue; }
    if (ch === '(' || ch === '[') depth += 1;
    else if (ch === ')' || ch === ']') depth -= 1;
    else if (depth === 0 && stops.includes(ch)) return i;
    i += 1;
  }
  return -1;
}

/** Index of the `}` matching the `{` at `open`. */
function matchingBrace(css, open) {
  let depth = 0;
  for (let i = open; i < css.length;) {
    const ch = css[i];
    if (ch === '"' || ch === "'") { i = skipString(css, i); continue; }
    if (ch === '/' && css[i + 1] === '*') { i = skipComment(css, i); continue; }
    if (ch === '\\') { i += 2; continue; }
    if (ch === '{') depth += 1;
    else if (ch === '}') {
      depth -= 1;
      if (depth === 0) return i;
    }
    i += 1;
  }
  throw new Error(`unterminated block at offset ${open}`);
}

/**
 * Splits a selector list on its top-level commas only: commas inside :not(), :is(), attribute
 * values or escapes belong to their selector.
 */
function splitSelectorList(prelude) {
  const parts = [];
  let start = 0;
  for (let comma = findTopLevel(prelude, 0, ','); comma >= 0; comma = findTopLevel(prelude, start, ',')) {
    parts.push(prelude.slice(start, comma).trim());
    start = comma + 1;
  }
  parts.push(prelude.slice(start).trim());
  return parts;
}

function scopeSelector(selector, scope) {
  if (!selector) throw new Error('empty selector in a selector list');
  if (/^[>+~]/.test(selector)) {
    throw new Error(`selector "${selector}" starts with a combinator and cannot be scoped`);
  }
  const root = ROOT_COMPOUND.exec(selector);
  if (root) return scope + selector.slice(root[0].length);
  return `${scope} ${selector}`;
}

/**
 * Walks a rule list, calling `onStyleRule(selectors)` for each style rule's selector list and
 * returning the text rebuilt with whatever it returns.
 */
function walkRules(css, onStyleRule) {
  let out = '';
  let i = 0;
  while (i < css.length) {
    const ch = css[i];
    if (/\s/.test(ch)) { out += ch; i += 1; continue; }
    if (ch === '/' && css[i + 1] === '*') {
      const end = skipComment(css, i);
      out += css.slice(i, end);
      i = end;
      continue;
    }
    if (ch === '}') throw new Error(`unbalanced "}" at offset ${i}`);
    const stop = findTopLevel(css, i, ch === '@' ? '{;' : '{;}');
    if (stop < 0) throw new Error(`unterminated rule at offset ${i}`);
    if (ch === '@') {
      const name = /^@([-\w]+)/.exec(css.slice(i))?.[1]?.toLowerCase() || '';
      if (!GROUPING_AT_RULES.has(name) && !VERBATIM_AT_RULES.has(name)) {
        throw new Error(`@${name} at offset ${i} cannot be scoped`);
      }
      if (css[stop] === ';') {
        out += css.slice(i, stop + 1);
        i = stop + 1;
        continue;
      }
      const close = matchingBrace(css, stop);
      out += GROUPING_AT_RULES.has(name)
        ? `${css.slice(i, stop + 1)}${walkRules(css.slice(stop + 1, close), onStyleRule)}}`
        : css.slice(i, close + 1);
      i = close + 1;
      continue;
    }
    if (css[stop] !== '{') throw new Error(`expected a declaration block at offset ${stop}`);
    const close = matchingBrace(css, stop);
    const body = css.slice(stop + 1, close);
    if (findTopLevel(body, 0, '{') >= 0) {
      throw new Error(`nested rule inside "${css.slice(i, stop).trim()}" is not supported`);
    }
    out += `${onStyleRule(splitSelectorList(css.slice(i, stop)))}{${body}}`;
    i = close + 1;
  }
  return out;
}

/**
 * Returns `css` with every style rule confined to elements inside `.${scopeClass}`.
 *
 * @param {string} css a compiled stylesheet (no CSS nesting)
 * @param {string} scopeClass a bare class name, without the leading dot
 * @returns {string} the scoped stylesheet
 * @throws {Error} on input the transform cannot scope without letting a rule escape
 */
function scopeStylesheet(css, scopeClass) {
  if (!SCOPE_CLASS.test(scopeClass || '')) {
    throw new Error(`scope must be a bare class name, got "${scopeClass}"`);
  }
  const scope = `.${scopeClass}`;
  return walkRules(css, (selectors) => selectors.map((selector) => scopeSelector(selector, scope)).join(','));
}

/** Every selector of every style rule in `css`, in source order, including those in at-rule blocks. */
function styleRuleSelectors(css) {
  const selectors = [];
  walkRules(css, (list) => {
    selectors.push(...list);
    return list.join(',');
  });
  return selectors;
}

module.exports = { scopeStylesheet, splitSelectorList, styleRuleSelectors };
