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
 * Static audit of CLAUDE.md's CSRF token bootstrapping rule.
 *
 * THE RULE, and why it needs enforcing. CSRFGuard's client script only injects
 * the hidden <input name="CSRF-TOKEN"> into a <form> whose action is a real URL
 * and whose method is non-GET. A page that reads that input from a fetch() or
 * XHR but has no such form sends an EMPTY token; CSRFGuard rejects the request
 * with an HTML error page; and response.json() throws into a catch block the
 * user never sees. Nothing renders, nothing is logged, and the action the
 * clinician asked for simply does not happen.
 *
 * So a page must satisfy one of:
 *   (a) contain at least one <form action="<real URL>" method="post">, or
 *   (b) include /WEB-INF/jspf/csrf-token.jspf, which pulls in csrfTokenFetch.js,
 *       adds the hidden input and populates it on DOMContentLoaded.
 *
 * WHY A STATIC AUDIT AND NOT A BROWSER CHECK. This one is genuinely decidable
 * from the source, it needs no deployment, and it can therefore run on every
 * pull request rather than only when somebody runs the browser suite. The rule
 * is written down in CLAUDE.md and has had no enforcement at all, which is how a
 * documented rule quietly becomes a documented aspiration. The browser half
 * still exists -- csrfBootstrapFinding() in lib/playwright-link-audit.js checks
 * the token is actually POPULATED on pages the audits open -- and the two are
 * complementary: this one sees every page, that one sees whether the bootstrap
 * really ran.
 *
 * THE ANTI-PATTERN IS CALLED OUT SEPARATELY. `<form id="csrfForm"
 * style="display:none;"></form>` looks like compliance and is not: CSRFGuard
 * skips a form with no action, so the input is never populated. A page carrying
 * only that is reported as a violation with its own message, because "add a
 * form" is the wrong advice for someone who already thinks they have one.
 */

const fs = require('node:fs');
const path = require('node:path');

const WEBAPP = path.join(__dirname, '..', '..', 'src', 'main', 'webapp');

/** Reading the token out of the DOM, by any of the ways CARLOS pages do it. */
const READS_TOKEN_INPUT = [
  // Matched inside the call's parentheses rather than inside one quoted string:
  // the selector is written 'input[name="CSRF-TOKEN"]', so a pattern that
  // stopped at the first inner quote found nothing at all and the whole audit
  // silently reported zero applicable pages.
  /querySelector(?:All)?\s*\([^)]{0,200}CSRF-TOKEN/,
  /getElementsByName\s*\(\s*['"]CSRF-TOKEN['"]/,
  /\$\s*\([^)]{0,200}CSRF-TOKEN/,
  /jQuery\s*\([^)]{0,200}CSRF-TOKEN/,
  // The helper's accessor reads the same input. A page that calls it and then
  // sends the value by a transport CSRFGuard does not intercept is exactly the
  // fetch() case with one indirection, so it is judged like one.
  /\bCarlosAjax\.getCsrfToken\s*\(/,
];

/** Sending it anywhere the CSRFGuard client script does not hijack. */
const SENDS_OVER_AJAX = [
  /\bfetch\s*\(/,
  /\bXMLHttpRequest\b/,
  /\$\.ajax\s*\(/,
  /\$\.post\s*\(/,
  /jQuery\.ajax\s*\(/,
  /jQuery\.post\s*\(/,
  // sendBeacon() is not XMLHttpRequest: CSRFGuard's script never sees it, so
  // the token it carries can only come from the hidden input.
  /\bnavigator\.sendBeacon\s*\(/,
];

/*
 * THE SHARED HELPER IS OUTSIDE THIS RULE, and deliberately so.
 *
 * share/javascript/carlos-ajax.js does read input[name="CSRF-TOKEN"] on the
 * caller's behalf, and for a while this audit treated every page that POSTs
 * through CarlosAjax as governed by the bootstrapping rule. It reported six
 * such pages as violations (issue #3665, finding 10) because none of them
 * carries a populated input. The model was wrong: the hidden input is not the
 * token's only carrier. CarlosAjax sends with XMLHttpRequest, never fetch(),
 * PRECISELY so that CSRFGuard's own script -- which CsrfGuardScriptInjectionFilter
 * puts on every HTML response -- injects the CSRF-TOKEN and X-Requested-With
 * headers into every send, and CSRFGuard validates the header before it looks
 * at the body (docs/csrf-protection-architecture.md). The body token the helper
 * adds is a second copy, not the first. The bootstrapping rule exists for
 * fetch() callers, which that script cannot reach.
 *
 * So a page whose only AJAX writes go through the helper has nothing to
 * bootstrap. scripts/csrf-xhr-token-playwright-checks.js is the live proof: it
 * opens the three whole pages the finding named, POSTs through their own
 * CarlosAjax, and reads the token header back off the wire. The test file pins
 * that the helper still uses XMLHttpRequest, because that is the fact this
 * exclusion rests on.
 *
 * THE EXCLUSION IS FOR THE HELPER'S OWN SENDS, NOT FOR EVERY PAGE THAT LOADS
 * IT. js/newCaseManagementView.js.jsp releases the note lock on pagehide with
 * navigator.sendBeacon(), carrying the value of CarlosAjax.getCsrfToken() in
 * the body. That send goes nowhere near XMLHttpRequest, so the header cannot
 * rescue it: it is a bootstrapping-rule page, and READS_TOKEN_INPUT and
 * SENDS_OVER_AJAX both name that shape so the audit sees it. It is satisfied
 * because the chart's `frmIssueNotes` form has action="" and method="post",
 * which CSRFGuard injects into (see FORM_ACTION below).
 */

/**
 * (a) A form CSRFGuard will actually inject into: real action, non-GET.
 *
 * WHY A WINDOW AND NOT ONE PATTERN OVER THE TAG. A CARLOS <form> tag routinely
 * spans several lines and its attribute values contain ">" characters, because
 * they are built from <%= ... %> and <carlos:encode ... />. A pattern anchored
 * with [^>]* stops inside the first such expression and finds nothing -- which
 * made the first version of this audit report labDisplay.jsp, the reference
 * implementation CLAUDE.md itself cites, as a violation. So the tag is read as a
 * bounded window and the two attributes are looked for inside it.
 */
const FORM_WINDOW = 600;
/*
 * THE ACTION CSRFGuard ACCEPTS, no stricter and no looser. This mirrors
 * isValidUrl() in csrfguard.js (META-INF/csrfguard.js in
 * csrfguard-4.5.0-jakarta.jar), which injectTokenForm() applies to the raw
 * attribute value:
 *
 *   - no `action` attribute at all: skipped (getAttribute() is null);
 *   - starts with `#`: skipped, an anchor;
 *   - starts with `//` or with a scheme (`javascript:`, `data:`, ...): skipped,
 *     except http(s) to the page's own host, which this audit cannot decide
 *     statically and so accepts;
 *   - anything else, INCLUDING THE EMPTY STRING and a value that is only
 *     whitespace: injected. "" is not trimmed and does not start with `/`, so it
 *     falls to the `has no scheme` branch and is a local resource.
 *
 * The chart (newEncounterLayout.jsp) writes <form action="" method="post">, and
 * its hidden input is populated live: the earlier `[^"'\s]` requirement called
 * that form unusable and was wrong about it. Being stricter than CSRFGuard here
 * is not a safe direction: it would have the audit demand a bootstrap on a page
 * that already has one, and a maintainer who adds the include to quiet it would
 * be adding a second copy of the token to a page that never lacked the first.
 */
const FORM_ACTION = /\baction\s*=\s*(["'])(?!#|\/\/|(?!https?:)[a-z][a-z0-9.+-]*:)[^"']*\1/i;
const FORM_METHOD_NON_GET = /\bmethod\s*=\s*["']\s*(?!get\b)[a-z]+\s*["']/i;

function hasRealPostForm(source) {
  for (const found of source.matchAll(/<form\b/gi)) {
    const region = source.slice(found.index, found.index + FORM_WINDOW);
    // Stop at the next <form so two adjacent tags cannot lend each other halves
    // of the requirement.
    const next = region.slice(1).search(/<form\b/i);
    const tag = next >= 0 ? region.slice(0, next + 1) : region;
    if (FORM_ACTION.test(tag) && FORM_METHOD_NON_GET.test(tag)) {
      return true;
    }
  }
  return false;
}

/** (b) The canonical bootstrap fragment. */
const BOOTSTRAP_INCLUDE = /include\s+file\s*=\s*["'][^"']*jspf\/csrf-token\.jspf["']/;

/**
 * (b, written out by hand) The same three things csrf-token.jspf does.
 *
 * admin/updateDrugref.jsp pulls in csrfTokenFetch.js, renders the hidden input
 * and calls fetchCsrfToken(ctx) itself. That is the include's body, inlined, and
 * it works -- so reporting it would be reporting correct behaviour. The include
 * is still the form to reach for in new code, and the audit says so in the
 * reason it returns rather than by failing.
 */
const INLINE_BOOTSTRAP = [
  /share\/javascript\/csrfTokenFetch\.js/,
  /fetchCsrfToken\s*\(/,
  /<input[^>]*name\s*=\s*["']CSRF-TOKEN["']/i,
];

/**
 * The anti-pattern: a form with no action, which CSRFGuard skips.
 *
 * Bounded-window scan for the SAME reason hasRealPostForm uses one, and this
 * had the bug that note describes. The earlier `<form\b(?![^>]*\baction\s*=)`
 * lookahead stops at the first `>` in the tag, and in a JSP that `>` is
 * routinely inside an attribute value built from `<%= ... %>` -- so a form
 * whose action came after such an attribute read as having no action at all.
 *
 * The verdict would have been unsatisfied either way, but the REASON is what a
 * maintainer acts on: "remove your empty placeholder form" sends them looking
 * for something that is not there, on a page whose real problem is that its
 * form is a GET.
 */
function hasActionlessForm(source) {
  for (const found of source.matchAll(/<form\b/gi)) {
    const region = source.slice(found.index, found.index + FORM_WINDOW);
    // Stop at the next <form, so one tag's action cannot excuse another's.
    const next = region.slice(1).search(/<form\b/i);
    const tag = next >= 0 ? region.slice(0, next + 1) : region;
    if (!FORM_ACTION.test(tag)) {
      return true;
    }
  }
  return false;
}

function jspFiles(root = WEBAPP) {
  const found = [];
  const walk = (directory) => {
    for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
      const full = path.join(directory, entry.name);
      if (entry.isDirectory()) {
        walk(full);
      } else if (/\.jspf?$/.test(entry.name)) {
        found.push(full);
      }
    }
  };
  walk(root);
  return found.sort();
}

const matchesAny = (patterns, text) => patterns.some((pattern) => pattern.test(text));

/**
 * Classify one page against the rule.
 *
 * @returns {null} when the rule does not apply to this page, otherwise
 *   { file, satisfied, reason }
 */
function auditSource(relativePath, source) {
  // One way in: the page reads the token AND sends it itself. A page that only
  // delegates to CarlosAjax is not judged (see the note above SHARED HELPER).
  const sendsItself = matchesAny(READS_TOKEN_INPUT, source) && matchesAny(SENDS_OVER_AJAX, source);
  if (!sendsItself) {
    return null;
  }
  if (BOOTSTRAP_INCLUDE.test(source)) {
    return { file: relativePath, satisfied: true, reason: 'includes /WEB-INF/jspf/csrf-token.jspf' };
  }
  if (hasRealPostForm(source)) {
    return { file: relativePath, satisfied: true, reason: 'renders a form with a real action and a non-GET method' };
  }
  if (INLINE_BOOTSTRAP.every((pattern) => pattern.test(source))) {
    return {
      file: relativePath,
      satisfied: true,
      reason: 'bootstraps the token inline (csrfTokenFetch.js + the hidden input + fetchCsrfToken()) rather than '
        + 'through the include; equivalent, though the include is what new code should use',
    };
  }
  if (hasActionlessForm(source)) {
    return {
      file: relativePath,
      satisfied: false,
      reason: 'has a form with NO action attribute. CSRFGuard skips action-less forms, so the hidden input is '
        + 'never populated — this is the empty-placeholder anti-pattern CLAUDE.md names, not compliance. '
        + 'Add the /WEB-INF/jspf/csrf-token.jspf include.',
    };
  }
  return {
    file: relativePath,
    satisfied: false,
    reason: 'reads input[name="CSRF-TOKEN"] for an AJAX request but renders neither a form with a real action '
      + 'and a non-GET method nor the /WEB-INF/jspf/csrf-token.jspf include, so the token is sent empty and '
      + 'every one of those requests is rejected with an HTML error page',
  };
}

/**
 * Struts routes whose result renders this file, so a host can be found by route.
 *
 * rx/ListDrugs.jsp has no <html> of its own: SearchDrug3.jsp fetches
 * `/rx/ViewListDrugs` and injects the markup into its own `drugProfile`
 * element. The token input it reads is therefore SearchDrug3's, and attributing
 * the rule to ListDrugs would report a page that cannot satisfy it.
 */
function routesRenderingFile(strutsDirectory, jspPath, webappRoot = WEBAPP) {
  // path.relative() AND THEN forward slashes, rather than cutting at a literal
  // 'src/main/webapp'. The old form assumed POSIX separators in a path this
  // module builds with path.join: on Windows it matched nothing, `suffix`
  // stayed the whole absolute path, no action contained it, and every fragment
  // lost its route-based host attribution. That surfaces (the unattributed
  // assertion fires) rather than passing quietly, so this is portability rather
  // than a live defect -- but the audit uses path.* everywhere else and there
  // is no reason for one hand-rolled exception.
  //
  // Struts results are written with forward slashes whatever the platform, so
  // the comparison normalises to those.
  const suffix = `/${path.relative(webappRoot, jspPath).split(path.sep).join('/')}`;
  const routes = [];
  for (const name of fs.readdirSync(strutsDirectory)) {
    if (!name.startsWith('struts') || !name.endsWith('.xml')) {
      continue;
    }
    const xml = fs.readFileSync(path.join(strutsDirectory, name), 'utf8');
    for (const action of xml.split('<action ').slice(1)) {
      if (!action.includes(suffix)) {
        continue;
      }
      const named = action.match(/name="([^"]+)"/);
      if (named) {
        routes.push(named[1]);
      }
    }
  }
  return routes;
}

/**
 * Pages that render this one into their own document.
 *
 * A page without an <html> element is not a page: it is a fragment, either
 * statically included or fetched and injected. Either way the document the
 * browser runs is the HOST's, so the host is what must carry the token input.
 * Attributing the rule to the fragment would demand a form of something that
 * has no document to put one in.
 *
 * The match is a plain substring of the file name or of a route that renders
 * it, looked for in the candidate's CODE AND MARKUP ONLY. A mention in a comment
 * renders nothing: ChartNotesAjax.jsp names js/newCaseManagementView.js.jsp
 * twice, both times in prose about the script that fetches it, and read
 * as a host it turned a page whose token input is populated live into a
 * reported violation. Missing a real host is still the costlier direction, so
 * the match stays a substring; only comments are excluded.
 */
function hostsOf(file, files, strutsDirectory, webappRoot = WEBAPP) {
  const base = path.basename(file);
  const routes = routesRenderingFile(strutsDirectory, file, webappRoot);
  const needles = [base, ...routes];
  return files.filter((candidate) => {
    if (candidate === file) {
      return false;
    }
    const source = stripComments(fs.readFileSync(candidate, 'utf8'));
    return needles.some((needle) => source.includes(needle));
  });
}

/**
 * The source with JSP, HTML, block and whole-line `//` comments removed.
 *
 * Only a `//` that opens its line is a comment: a `//` mid-line is far more
 * often the one in `http://`. A block comment inside a string literal would be
 * cut too, which for host attribution errs towards finding fewer hosts and is
 * accepted; hostsOf() is a substring match, not a parser.
 */
function stripComments(source) {
  return source
    .replace(/<%--[\s\S]*?--%>/g, '')
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^[ \t]*\/\/.*$/gm, '');
}

const isSelfContained = (source) => /<html\b/i.test(source);

/** Audit the whole webapp. */
function auditWebapp(options = {}) {
  const root = options.root || WEBAPP;
  const strutsDirectory = options.strutsDirectory || path.join(root, 'WEB-INF', 'classes');
  const files = options.files || jspFiles(root);
  const repoRoot = path.join(root, '..', '..', '..');
  const results = [];
  const unattributed = [];

  for (const file of files) {
    const source = fs.readFileSync(file, 'utf8');
    const relative = path.relative(repoRoot, file);
    const verdict = auditSource(relative, source);
    if (!verdict) {
      continue;
    }
    if (verdict.satisfied || isSelfContained(source)) {
      results.push(verdict);
      continue;
    }

    // A fragment that does not satisfy the rule itself is satisfied when every
    // page that renders it does -- the token input lives in the host document.
    const hosts = hostsOf(file, files, strutsDirectory, root);
    if (hosts.length === 0) {
      unattributed.push({
        ...verdict,
        reason: `${verdict.reason}. It also has no <html> of its own and no page that renders it could be found, `
          + 'so the audit cannot say which document supplies the token: a human has to look',
      });
      continue;
    }
    const unsatisfiedHosts = hosts.filter((host) => {
      const hostSource = fs.readFileSync(host, 'utf8');
      return !(BOOTSTRAP_INCLUDE.test(hostSource)
        || hasRealPostForm(hostSource)
        || INLINE_BOOTSTRAP.every((pattern) => pattern.test(hostSource)));
    });
    if (unsatisfiedHosts.length === 0) {
      results.push({
        file: relative,
        satisfied: true,
        reason: `rendered into ${hosts.map((host) => path.basename(host)).join(', ')}, which carry the token input`,
      });
      continue;
    }
    results.push({
      file: relative,
      satisfied: false,
      reason: `${verdict.reason}. It is rendered into `
        + `${unsatisfiedHosts.map((host) => path.relative(repoRoot, host)).join(', ')}, which do not carry it either`,
    });
  }

  return {
    applicable: results,
    violations: results.filter((entry) => !entry.satisfied),
    unattributed,
  };
}

module.exports = {
  BOOTSTRAP_INCLUDE,
  FORM_WINDOW,
  INLINE_BOOTSTRAP,
  READS_TOKEN_INPUT,
  SENDS_OVER_AJAX,
  WEBAPP,
  auditSource,
  auditWebapp,
  hasActionlessForm,
  hasRealPostForm,
  hostsOf,
  isSelfContained,
  jspFiles,
  routesRenderingFile,
  stripComments,
};
