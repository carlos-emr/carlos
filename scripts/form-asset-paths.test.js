/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * The static half of the #3727 regression guard.
 *
 * form-asset-paths-playwright-checks.js proves in a browser that a handful of
 * named forms load their assets. It cannot prove the sweep was complete: a JSP
 * nobody listed keeps its broken reference and the suite stays green. That is
 * exactly how the stylesheet references survived the first pass, and how
 * formannualfemaleprint kept two image references that the browser check's
 * three-form list could never reach.
 *
 * So resolve every reference statically instead. These pages carry
 *
 *   <base href="<%= ...getContextPath() %>/">
 *
 * which means a relative asset reference resolves against the CONTEXT ROOT, not
 * against /form/ where the file actually sits. Each relative reference is
 * resolved the way the browser would and the target has to exist on disk.
 */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

const WEBAPP = path.join(__dirname, '..', 'src', 'main', 'webapp');
const FORM_JSP_DIR = path.join(WEBAPP, 'WEB-INF', 'jsp', 'form');

const BASE_TAG = /<base\b[^>]*?>/i;
const RELATIVE_REF = /(?:src|href)\s*=\s*"([A-Za-z0-9_][A-Za-z0-9_./-]*\.(?:js|css|gif|png|jpe?g|bmp))"/gi;

/*
 * The other half of the same question. RELATIVE_REF deliberately starts at an
 * alphanumeric, so it matches only references the sweep has NOT rewritten --
 * which means every path this change produced was invisible to it. A typo in
 * one of the ~1,150 rewritten references would have stayed green here, and the
 * browser check samples four forms. These resolve against the webapp root.
 */
const CONTEXT_ROOTED_REF =
  /(?:src|href)\s*=\s*"(?:<%=\s*request\.getContextPath\(\)\s*%>|\$\{pageContext\.request\.contextPath\})(\/[A-Za-z0-9_./-]*\.(?:js|css|gif|png|jpe?g|bmp))"/gi;

/*
 * Three stylesheets are referenced by forms but ship nowhere in the webapp:
 * formalpha's alphaStyle.css (the Alpha form is not in FrmRecordFactory's
 * allow-list either, so the page is unreachable), the Style1.css that the
 * counsellor and reception assessments ask for, and formPositionHazard's
 * positionHazardStyle.css, which exists nowhere in the repository.
 * Context-rooting a reference to a file that does not exist would only move the
 * 404, so they are recorded here rather than silently skipped.
 */
const KNOWN_ABSENT_ASSETS = new Set(['alphaStyle.css', 'Style1.css', 'positionHazardStyle.css']);

function formJsps() {
  return fs.readdirSync(FORM_JSP_DIR).filter((name) => name.endsWith('.jsp')).sort();
}

/**
 * The prefix a relative reference resolves against, or null when the page has
 * no <base>. Every <base> in these pages is built from getContextPath() plus
 * string literals, so the literals after that call are the path under the
 * context root.
 */
function baseprefix(source) {
  const tag = BASE_TAG.exec(source);
  if (!tag) {
    return null;
  }
  if (!tag[0].includes('getContextPath()')) {
    return null;
  }
  // Stop at the end of the scriptlet before reading literals. Taking them from the whole
  // remainder swept up the tag's own closing `">`, which then had to be stripped back out
  // one character at a time -- and stripping only the first occurrence is exactly the
  // incomplete-escaping shape CodeQL flags. Cutting at `%>` means there is nothing to strip.
  const expression = tag[0].split('getContextPath()').pop().split('%>')[0];
  const literals = expression.match(/"[^"]*"/g) || [];
  const prefix = literals.map((literal) => literal.slice(1, -1)).join('');
  return prefix.startsWith('/') ? prefix : `/${prefix}`;
}

test('every relative asset reference in a form JSP resolves to a file that exists', () => {
  const unresolved = [];
  for (const name of formJsps()) {
    const source = fs.readFileSync(path.join(FORM_JSP_DIR, name), 'utf8');
    const prefix = baseprefix(source);
    if (prefix === null) {
      continue;
    }
    for (const [, ref] of source.matchAll(RELATIVE_REF)) {
      if (KNOWN_ABSENT_ASSETS.has(path.basename(ref))) {
        continue;
      }
      if (!fs.existsSync(path.join(WEBAPP, prefix, ref))) {
        unresolved.push(`${name}: "${ref}" resolves to ${prefix}${ref}`);
      }
    }
  }
  assert.deepEqual(unresolved, [],
    `these references resolve against the page's <base> to somewhere no file exists:\n  ${unresolved.join('\n  ')}`);
});

test('every context-rooted asset reference in a form JSP resolves to a file that exists', () => {
  // Guards the output of the sweep itself: a mistyped segment in a rewritten path
  // produces a reference no relative-reference scan can see, because it no longer
  // starts relative.
  const unresolved = [];
  for (const name of formJsps()) {
    const source = fs.readFileSync(path.join(FORM_JSP_DIR, name), 'utf8');
    for (const [, ref] of source.matchAll(CONTEXT_ROOTED_REF)) {
      if (KNOWN_ABSENT_ASSETS.has(path.basename(ref))) {
        continue;
      }
      if (!fs.existsSync(path.join(WEBAPP, ref.replace(/^\//, '')))) {
        unresolved.push(`${name}: "${ref}" resolves to ${ref}`);
      }
    }
  }
  assert.deepEqual(unresolved, [],
    `these context-rooted references point at somewhere no file exists:\n  ${unresolved.join('\n  ')}`);
});

test('the context-rooted scan actually covers the sweep', () => {
  // Without this, a regex that stopped matching would make the test above pass
  // by checking nothing -- the same blindness the <base> parser guard prevents.
  let found = 0;
  for (const name of formJsps()) {
    const source = fs.readFileSync(path.join(FORM_JSP_DIR, name), 'utf8');
    found += [...source.matchAll(CONTEXT_ROOTED_REF)].length;
  }
  assert.ok(found >= 1000,
    `only ${found} context-rooted references matched; the sweep rewrote far more than that`);
});

test('no form JSP builds an asset path in a scriptlet without the context path', () => {
  // formannualfemaleprint's checkMarks() assembled "<img src='graphics/...'>" in a
  // JSP declaration, where the reference is invisible to the src="..." scan above.
  const offenders = [];
  for (const name of formJsps()) {
    const source = fs.readFileSync(path.join(FORM_JSP_DIR, name), 'utf8');
    for (const [match] of source.matchAll(/src='[A-Za-z0-9_][A-Za-z0-9_./-]*\.(?:js|css|gif|png|jpe?g|bmp)'/gi)) {
      offenders.push(`${name}: ${match}`);
    }
  }
  assert.deepEqual(offenders, [],
    `these scriptlet-built references carry no context path:\n  ${offenders.join('\n  ')}`);
});

test('the base parser understands the pages it is meant to cover', () => {
  // Without this, a parser that quietly stopped recognising <base> would make the
  // resolution test above pass by covering nothing at all.
  const parsed = formJsps()
    .map((name) => baseprefix(fs.readFileSync(path.join(FORM_JSP_DIR, name), 'utf8')))
    .filter((prefix) => prefix !== null);
  assert.ok(parsed.length >= 90, `only ${parsed.length} form pages parsed a <base>; the scan has gone blind`);
  for (const prefix of parsed) {
    assert.match(prefix, /^\/[A-Za-z0-9_./-]*$/, `unparsable base prefix: ${JSON.stringify(prefix)}`);
  }
});

test('the known-absent list names only assets that really are absent', () => {
  // A guard on the guard: once one of these ships, the exemption has to go or it
  // starts hiding a real regression.
  for (const asset of KNOWN_ABSENT_ASSETS) {
    assert.ok(!fs.existsSync(path.join(WEBAPP, 'form', asset)),
      `${asset} now exists; remove it from KNOWN_ABSENT_ASSETS and context-root its references`);
  }
});
