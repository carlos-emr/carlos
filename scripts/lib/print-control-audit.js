#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Static audit of "a page that prints itself must not print its own controls".
 *
 * THE FAILURE MODE (issue #3278). A legacy CARLOS report page prints with
 * window.print(), which reproduces the live page verbatim. Whatever chrome sits
 * on that page - the Print button the operator just clicked, a Close button, a
 * provider picker, a Generate submit - lands on the paper, and a billing
 * reconciliation printout that shows its own Print button reads as a broken
 * report to the clinic that files it.
 *
 * THE RULE. Every control that fires window.print(), and every sibling control
 * in the same toolbar, must be inside an element marked d-print-none (the
 * Bootstrap 5 utility class) or the older CARLOS .noprint spelling. The page
 * must also actually DEFINE that class, either by loading Bootstrap or by
 * linking css/print-controls.css - a marker class nothing implements hides
 * nothing, and is the quiet way this regresses.
 *
 * WHY A STATIC AUDIT. Print output is close to untestable in a headless browser
 * (Chromium's print preview is not reachable from Playwright, and emulating
 * print media only proves CSS matched, not that the page linked it). The markup
 * and the stylesheet link are the two things that actually broke, and both are
 * visible in the source, so they are what this checks - on every pull request,
 * with no Tomcat and no database.
 */

const fs = require('node:fs');
const path = require('node:path');

const WEBAPP = path.join(__dirname, '..', '..', 'src', 'main', 'webapp');

/** The shared stylesheet that defines the marker class for non-Bootstrap pages. */
const PRINT_CONTROLS_CSS = path.join(WEBAPP, 'css', 'print-controls.css');

/** Marker classes that mean "not on paper". Bootstrap's spelling, then the legacy one. */
const MARKERS = ['d-print-none', 'noprint'];

/**
 * Elements that never wrap anything, so they can never be a marked ancestor and
 * must not be pushed onto the nesting stack.
 */
const VOID_TAGS = new Set([
  'area', 'base', 'br', 'col', 'embed', 'hr', 'img', 'input',
  'link', 'meta', 'param', 'source', 'track', 'wbr',
]);

/**
 * Tags whose close tag these legacy pages routinely omit. Opening one closes an
 * open sibling of the same kind; opening a row also closes any open cell. This
 * is what keeps the ancestor stack honest on markup that no strict parser would
 * accept - genRADesc.jsp's premium table leaves <td> unclosed, and onGenRA*.jsp
 * put <form> directly inside <table>, before the first <tr>.
 */
const IMPLIED_CLOSE = {
  td: ['td', 'th'],
  th: ['td', 'th'],
  tr: ['td', 'th', 'tr'],
  li: ['li'],
  option: ['option'],
  p: ['p'],
};

/** JSP comments and scriptlets can contain angle brackets; they are not markup. */
function stripJspNoise(source) {
  return source
    .replace(/<%--[\s\S]*?--%>/g, (match) => ' '.repeat(match.length))
    .replace(/<%[\s\S]*?%>/g, (match) => ' '.repeat(match.length));
}

function hasMarker(startTag) {
  const classAttr = /\bclass\s*=\s*("([^"]*)"|'([^']*)')/i.exec(startTag);
  if (!classAttr) return false;
  const classes = (classAttr[2] !== undefined ? classAttr[2] : classAttr[3]).split(/\s+/);
  return MARKERS.some((marker) => classes.includes(marker));
}

/**
 * Walk the page's start/end tags, and for every offset of interest report the
 * start tag it sits inside plus that element's open ancestors.
 *
 * @param source raw JSP text (JSP noise already blanked, offsets preserved)
 * @param offsets character offsets to resolve, e.g. where window.print() appears
 * @returns Map from offset to { element, ancestors } start-tag strings
 */
function resolveElements(source, offsets) {
  const wanted = [...offsets].sort((a, b) => a - b);
  const resolved = new Map();
  const stack = [];
  const tagPattern = /<(\/?)([a-zA-Z][a-zA-Z0-9]*)((?:"[^"]*"|'[^']*'|[^>"'])*)>/g;

  let match = tagPattern.exec(source);
  while (match !== null) {
    const [tag, closing, rawName, attrs] = match;
    const name = rawName.toLowerCase();
    const start = match.index;
    const end = start + tag.length;

    // An onClick="window.print()" offset falls INSIDE its own start tag, so the
    // element that owns the handler is this tag, not whatever contains it.
    for (const offset of wanted) {
      if (offset >= start && offset < end && !resolved.has(offset)) {
        resolved.set(offset, { element: tag, ancestors: [...stack] });
      }
    }

    if (closing) {
      const popTo = stack.map((entry) => entry.name).lastIndexOf(name);
      if (popTo !== -1) stack.length = popTo;
    } else if (!VOID_TAGS.has(name) && !/\/\s*$/.test(attrs)) {
      const implied = IMPLIED_CLOSE[name];
      if (implied) {
        while (stack.length > 0 && implied.includes(stack[stack.length - 1].name)) stack.pop();
      }
      stack.push({ name, tag });
    }

    match = tagPattern.exec(source);
  }

  return resolved;
}

/** Offsets of every window.print() call in the page. */
function printCallOffsets(source) {
  const offsets = [];
  const pattern = /window\s*\.\s*print\s*\(/g;
  let match = pattern.exec(source);
  while (match !== null) {
    offsets.push(match.index);
    match = pattern.exec(source);
  }
  return offsets;
}

/**
 * Does this page define the marker class at all? Either it loads Bootstrap
 * (which ships d-print-none), or it links the shared print stylesheet.
 */
function definesMarker(source) {
  return /css\/print-controls\.css/.test(source)
    || /bootstrap[^"']*\.css/i.test(source);
}

/**
 * Audit one JSP.
 *
 * @returns null when the page never calls window.print(); otherwise a report
 *          with the unmarked print controls and whether the marker is defined.
 */
function auditJsp(file) {
  const raw = fs.readFileSync(file, 'utf8');
  const source = stripJspNoise(raw);
  const offsets = printCallOffsets(source);
  if (offsets.length === 0) return null;

  const resolved = resolveElements(source, offsets);
  const unmarked = [];
  for (const offset of offsets) {
    const entry = resolved.get(offset);
    if (!entry) {
      // No start tag contains the call, so it is script-body print() (a
      // deliberate print action, not a rendered control) - nothing to hide.
      continue;
    }
    const marked = hasMarker(entry.element)
      || entry.ancestors.some((ancestor) => hasMarker(ancestor.tag));
    if (!marked) unmarked.push(entry.element.replace(/\s+/g, ' ').trim());
  }

  return {
    file: path.relative(path.join(__dirname, '..', '..'), file),
    printControls: offsets.length,
    unmarked,
    definesMarker: definesMarker(raw),
  };
}

/** Every marker-carrying element on the page, as its start tag. */
function markedElements(file) {
  const source = stripJspNoise(fs.readFileSync(file, 'utf8'));
  const tags = source.match(/<[a-zA-Z][a-zA-Z0-9]*(?:"[^"]*"|'[^']*'|[^>"'])*>/g) || [];
  return tags.filter(hasMarker);
}

module.exports = {
  MARKERS,
  PRINT_CONTROLS_CSS,
  WEBAPP,
  auditJsp,
  definesMarker,
  hasMarker,
  markedElements,
  printCallOffsets,
  resolveElements,
  stripJspNoise,
};
