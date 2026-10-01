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
 * Page-health engine: "open every link a user can click one level deep and judge
 * the PAGE, not just whether it loaded".
 *
 * WHAT THE EXISTING LINK AUDITS ALREADY ASSERT (lib/playwright-link-audit.js): HTTP
 * status, the CARLOS error page, a blank body, uncaught page errors, severe console
 * messages, failed sub-resources, unanswered dialogs and the CSRF-token bootstrap.
 * This engine re-uses those signals and adds the ones no audit read:
 *
 *   text      visible text and form values that carry a rendering accident: a literal
 *             "null" / "undefined" / "NaN" / "Invalid Date", an unresolved resource key
 *             ("???key???"), "[object Object]", an unresolved ${...} / %{...} / <%= %>
 *             expression, a double-encoded entity ("&amp;amp;"), or a leaked Java
 *             class/exception name.
 *   headers   every same-origin document response carries the front door's security
 *             headers once each (X-Frame-Options, nosniff, a frame-ancestors CSP,
 *             Cache-Control: no-store), no Cross-Origin-Opener-Policy that would sever
 *             window.opener (the popup refresh pattern depends on it), a charset on
 *             HTML, no server-version disclosure, and session cookies with the
 *             HttpOnly/Secure/SameSite flags.
 *   off-host  any request to a host other than the application is ABORTED in the
 *             browser (nothing leaves the machine) and recorded, as is any http:
 *             sub-resource on an https front door (mixed content).
 *   session   a click that ends on the login page ("kicked out") rather than the page.
 *
 * ATTRIBUTION. Findings carry the label of the item that produced them and are
 * de-duplicated on (kind, detail), keeping the first page and a count, so one
 * defect in a shared include is one finding rather than 120.
 *
 * READ-ONLY. It clicks navigation links and closes what they open. The caller's
 * skip rules keep it away from anything that logs out or mutates.
 */

const h = require('./playwright-harness');
const {
  ERROR_PAGE_RE, bodyFingerprint, destinationText, findingsSince, isCurrentDocumentLink,
  resolveAuditLink, revealAuditLink, snapshotRecorder,
} = require('./playwright-link-audit');
const { clickOpensPopupOrNavigates } = require('./playwright-ui');

const NAVIGATION_START_TIMEOUT = 4000;

/** A catalogued link that is not visible even after its menus were opened. */
class NotVisible extends Error {
  constructor(text) {
    super(`link "${text}" is not visible`);
    this.name = 'NotVisible';
  }
}

/*
 * Text rules. Each has a reason, because a text scan that cannot say why a hit is a
 * defect gets switched off the first time it is noisy.
 */
const TEXT_RULES = [
  { kind: 'literal-null', re: /(?<![\w./@-])null(?![\w@-])/, why: 'a null value was concatenated into visible text' },
  { kind: 'literal-undefined', re: /(?<![\w./@-])undefined(?![\w@-])/, why: 'an undefined JavaScript value reached visible text' },
  { kind: 'literal-nan', re: /(?<![\w./@-])NaN(?![\w@-])/, why: 'a failed number conversion reached visible text' },
  { kind: 'invalid-date', re: /Invalid Date/, why: 'a failed date parse reached visible text' },
  { kind: 'missing-resource-key', re: /\?\?\?[\w.\-]+\?\?\?/, why: 'a resource-bundle key is missing or its <fmt:setBundle> is not in scope' },
  { kind: 'object-object', re: /\[object [A-Za-z]+\]/, why: 'an object was string-concatenated in script' },
  { kind: 'unresolved-el', re: /\$\{[^}\n]{1,80}\}/, why: 'a JSP/EL expression was emitted verbatim (escaped, or in a context that does not evaluate it)' },
  { kind: 'unresolved-ognl', re: /%\{[^}\n]{1,80}\}/, why: 'a Struts OGNL expression was emitted verbatim' },
  { kind: 'unresolved-scriptlet', re: /<%[=@!]?|%>/, why: 'a JSP scriptlet delimiter reached the browser' },
  { kind: 'double-encoded-entity', re: /&(?:amp|lt|gt|quot|apos|#\d{2,5}|#x[0-9a-fA-F]{2,4});/, why: 'a value was HTML-encoded twice, so the user sees the entity text' },
  { kind: 'java-leak', re: /\b(?:java|jakarta|org\.hibernate|org\.springframework|org\.apache|io\.github\.carlos_emr)\.[A-Za-z0-9_.$]+(?:Exception|Error|@[0-9a-f]{5,})/, why: 'a Java class or exception name was rendered to the user' },
];

/*
 * Hits that are DATA or documentation, not rendering accidents. Each has its reason.
 * Kept narrow and matched on the snippet, so a real defect on the same page still shows.
 */
const DEFAULT_ALLOW = [
  // Other suites seed deliberately hostile names (xss-poison-helpers.js) whose literal
  // text includes an entity and markup. They are rendered correctly as typed.
  { kind: /^double-encoded-entity$|^literal-/, match: /data-xp/ },
  // The provider preferences help line documents the ${token} syntax of Quick Links.
  { kind: /^unresolved-el$/, match: /\$\{contextPath\}|\$\{demographicId\}|\$\{appointmentId\}|\$\{provider|\$\{demographic/ },
];

/*
 * Defects already filed (ISSUES.md / docs/ui-tests/app-findings-log.md). They are counted
 * and printed ("not failed: ...") so the rule still runs and the count is visible, but they
 * do not fail this sweep a second time. Remove an entry when its fix lands.
 */
const KNOWN_FINDINGS = [
  { kind: /^missing-resource-key$/, match: /manageHealthCareTeam/, reason: 'Health Care Team panel bundle, ISSUES.md L284' },
];

/** Runs IN THE PAGE. Returns the scoped visible text plus the value-like text of controls. */
function readPageText(scopeSelector) {
  const scope = scopeSelector ? document.querySelector(scopeSelector) : document.body;
  if (!scope) return { text: '', values: [], title: document.title || '' };
  // innerText of a subtree that is not being rendered (a hidden iframe, a collapsed
  // panel) is its raw textContent -- including every <script> body -- so reading it
  // reports JavaScript source as "visible text". Nothing the user cannot see is judged.
  if (scope.getClientRects().length === 0) return { text: '', values: [], title: document.title || '' };
  const text = scope.innerText || '';
  const values = [];
  for (const element of scope.querySelectorAll('input, select, textarea')) {
    const type = (element.getAttribute('type') || '').toLowerCase();
    if (['hidden', 'password', 'file', 'checkbox', 'radio', 'submit', 'button', 'image', 'reset'].includes(type)) continue;
    if (element.tagName === 'SELECT') {
      for (const option of element.selectedOptions) values.push(option.text);
    } else if (element.tagName === 'TEXTAREA') {
      // A textarea is often a template or a source editor; only a bare placeholder
      // word counts (checked by the caller against an exact match).
      values.push(element.value);
    } else {
      values.push(element.value);
      if (element.placeholder) values.push(element.placeholder);
    }
  }
  return { text, values, title: document.title || '' };
}

/**
 * Context of a hit, used ONLY to decide whether an allow rule applies ("data-xp" seeded
 * hostile data, the documented ${token} help text). It is never reported: the page may be
 * a real patient's chart when an operator points a check at one (MASTER_RECORD_SEARCH,
 * MASTER_RECORD_DEMOGRAPHIC_NO), and the text around a defect is patient text.
 */
function snippet(text, index, length) {
  const start = Math.max(0, index - 30);
  return text.slice(start, index + length + 30).replace(/\s+/g, ' ').trim();
}

/**
 * The part of a hit that is safe to print: the defect token itself, never its neighbours.
 * The tokens the rules look for are code-shaped (null, NaN, ???key???, [object X], an
 * entity, a Java class name). A template expression's inner text is free text on a page,
 * so unless it is a plain identifier path it is reduced to its delimiters.
 */
function needle(kind, matched) {
  const token = String(matched).replace(/\s+/g, ' ').trim();
  if (kind === 'unresolved-el' || kind === 'unresolved-ognl') {
    const open = token.slice(0, 2);
    return /^[%$]\{[A-Za-z_][\w.[\]]{0,60}\}$/.test(token) ? token : `${open}...}`;
  }
  if (kind === 'unresolved-scriptlet') return token;
  return token.length > 80 ? `${token.slice(0, 77)}...` : token;
}

/**
 * Pure: apply the text rules to what readPageText returned. Exported for the unit test.
 * A finding's detail names the source and the defect token only; no page text around it.
 */
function scanText(read, extraAllow = []) {
  const findings = [];
  const seen = new Set();
  const consider = (kind, why, text, index, length, source) => {
    const context = `${source}: "${snippet(text, index, length)}"`;
    if ([...DEFAULT_ALLOW, ...extraAllow].some(rule => (rule.kind instanceof RegExp ? rule.kind.test(kind) : rule.kind === kind)
      && rule.match.test(context))) return;
    const detail = `${source}: "${needle(kind, text.slice(index, index + length))}"`;
    const key = `${kind}|${detail}`;
    if (seen.has(key)) return;
    seen.add(key);
    findings.push({ kind, detail, why });
  };
  for (const rule of TEXT_RULES) {
    const flags = rule.re.flags.includes('g') ? rule.re.flags : `${rule.re.flags}g`;
    for (const source of [['visible text', read.text], ['title', read.title]]) {
      const re = new RegExp(rule.re.source, flags);
      let match;
      let guard = 0;
      while ((match = re.exec(source[1])) && guard < 5) {
        consider(rule.kind, rule.why, source[1], match.index, match[0].length, source[0]);
        guard += 1;
      }
    }
  }
  for (const value of read.values) {
    const trimmed = String(value || '').trim();
    if (/^(?:null|undefined|NaN|Invalid Date|\[object [A-Za-z]+\])$/.test(trimmed)) {
      consider(`field-${trimmed.replace(/\W+/g, '-').toLowerCase()}`, 'a form control is pre-filled with a rendering accident', trimmed, 0, trimmed.length, 'form value');
    }
    const key = /\?\?\?[\w.\-]+\?\?\?/.exec(trimmed);
    if (key) {
      consider('missing-resource-key', 'a resource-bundle key is missing', trimmed, key.index, key[0].length, 'form value');
    }
  }
  return findings;
}

/*
 * Header rules for one same-origin response. Pure so the unit test can drive it.
 * `headers` is Playwright's allHeaders(): lower-case names, repeated headers joined by "\n".
 */
function headerFindings(entry, { https }) {
  const out = [];
  const headers = entry.headers || {};
  const isHtml = /^text\/html/i.test(entry.contentType || '');
  const isDocument = entry.resourceType === 'document' || entry.resourceType === 'subdocument' || isHtml;
  const duplicated = (name) => typeof headers[name] === 'string' && headers[name].split('\n').length > 1;
  for (const name of ['x-frame-options', 'x-content-type-options', 'referrer-policy', 'strict-transport-security', 'cache-control', 'content-security-policy']) {
    if (duplicated(name)) {
      out.push({ kind: 'header-duplicated', detail: `${name} is sent ${headers[name].split('\n').length} times (${headers[name].split('\n').join(' | ').slice(0, 160)})` });
    }
  }
  if (entry.status >= 400) return out;
  if (!/nosniff/i.test(headers['x-content-type-options'] || '')) {
    out.push({ kind: 'header-missing-nosniff', detail: `${entry.resourceType} responses carry no X-Content-Type-Options: nosniff (${entry.route})` });
  }
  if (isDocument && isHtml) {
    if (!/^(?:sameorigin|deny)$/i.test((headers['x-frame-options'] || '').split('\n')[0].trim())) {
      out.push({ kind: 'header-xfo', detail: `HTML document has X-Frame-Options "${headers['x-frame-options'] || '(absent)'}" (${entry.route})` });
    }
    const csp = headers['content-security-policy'] || '';
    if (!/frame-ancestors\s+(?:'self'|'none')/i.test(csp)) {
      out.push({ kind: 'header-csp-frame-ancestors', detail: `HTML document has no enforcing frame-ancestors 'self'/'none' (${entry.route})` });
    }
    if (/(?:script-src|default-src)[^;]*(?:\s\*(?:\s|;|$)|\shttp:)/i.test(csp)) {
      out.push({ kind: 'header-csp-wildcard', detail: `the enforcing CSP allows any/insecure script source (${entry.route})` });
    }
    if (!/no-store/i.test(headers['cache-control'] || '')) {
      out.push({ kind: 'header-cache-no-store', detail: `authenticated HTML document is cacheable: Cache-Control "${headers['cache-control'] || '(absent)'}" (${entry.route})` });
    }
    if (!/charset=/i.test(entry.contentType || '')) {
      out.push({ kind: 'header-no-charset', detail: `HTML document content-type has no charset ("${entry.contentType}") so accents depend on browser sniffing (${entry.route})` });
    }
    const coop = (headers['cross-origin-opener-policy'] || '').trim().toLowerCase();
    if (coop && coop !== 'unsafe-none') {
      out.push({ kind: 'header-coop-severs-opener', detail: `Cross-Origin-Opener-Policy "${coop}" severs window.opener, which the popup refresh pattern relies on (${entry.route})` });
    }
  }
  if (headers['x-powered-by']) {
    out.push({ kind: 'header-disclosure', detail: `X-Powered-By "${headers['x-powered-by']}" is sent` });
  }
  if (/\d+\.\d+|coyote|tomcat|jetty/i.test(headers.server || '')) {
    out.push({ kind: 'header-disclosure', detail: `Server header discloses "${headers.server}"` });
  }
  for (const cookie of String(headers['set-cookie'] || '').split('\n').filter(Boolean)) {
    const name = cookie.split('=')[0];
    const missing = [];
    if (!/;\s*HttpOnly/i.test(cookie)) missing.push('HttpOnly');
    if (https && !/;\s*Secure/i.test(cookie)) missing.push('Secure');
    if (!/;\s*SameSite=/i.test(cookie)) missing.push('SameSite');
    if (missing.length) {
      out.push({ kind: 'cookie-flags', detail: `cookie ${name} lacks ${missing.join(', ')}` });
    }
  }
  return out;
}

/**
 * Install the probes on a context: off-host blocking and response-header capture.
 * Call once, before the first page is created, so the login page is covered too.
 */
async function installProbes(context, config) {
  const appOrigin = new URL(config.baseUrl).origin;
  const https = new URL(config.baseUrl).protocol === 'https:';
  const probe = { offHost: [], responses: [], appOrigin, https };
  await context.route('**/*', (route) => {
    const request = route.request();
    let parsed;
    try {
      parsed = new URL(request.url());
    } catch {
      return route.continue();
    }
    // chrome-extension: is Chromium's own PDF viewer asking for its UI files; it is part of
    // the browser, not of the page under test.
    if (['data:', 'blob:', 'about:', 'chrome-extension:', 'chrome:', 'devtools:'].includes(parsed.protocol)) return route.continue();
    if (parsed.origin === appOrigin) return route.continue();
    // Aborted BEFORE it leaves the machine. Recorded without its query string: an
    // off-host URL is the one place a patient identifier could ride out.
    probe.offHost.push({
      url: `${parsed.origin}${parsed.pathname}`.slice(0, 160),
      resourceType: request.resourceType(),
      mixed: https && parsed.protocol === 'http:' && parsed.hostname === new URL(appOrigin).hostname,
      from: h.withoutQueryStrings(request.frame() ? request.frame().url() : ''),
    });
    return route.abort('blockedbyclient');
  });
  context.on('response', (response) => {
    let parsed;
    try {
      parsed = new URL(response.url());
    } catch {
      return;
    }
    if (parsed.origin !== appOrigin) return;
    const request = response.request();
    const index = probe.responses.length;
    probe.responses.push({ pending: true });
    response.allHeaders().then((headers) => {
      probe.responses[index] = {
        route: parsed.pathname.replace(/^\/carlos/, '').slice(0, 120),
        status: response.status(),
        resourceType: request.resourceType(),
        contentType: headers['content-type'] || '',
        headers,
      };
    }, () => { probe.responses[index] = { pending: false, skipped: true }; });
  });
  return probe;
}

/** Collects findings, keeps the first page and a count per distinct (kind, detail). */
function createLedger() {
  const map = new Map();
  const suppressed = new Map();
  const knownReasons = new Map();
  return {
    /** A kind that is already filed elsewhere: counted, reported once in the log, not failed. */
    suppress(kind, reason) { suppressed.set(kind, { reason, count: 0 }); },
    known(reason) { knownReasons.set(reason, (knownReasons.get(reason) || 0) + 1); },
    suppressedSummary() {
      return [...suppressed.entries()].filter(([, v]) => v.count > 0)
        .map(([kind, v]) => `${kind} x${v.count} (known: ${v.reason})`)
        .concat([...knownReasons.entries()].map(([reason, count]) => `x${count} (known: ${reason})`));
    },
    add(page, kind, detail, why) {
      if (suppressed.has(kind)) { suppressed.get(kind).count += 1; return; }
      const key = `${kind}|${detail}`;
      const existing = map.get(key);
      if (existing) {
        existing.count += 1;
        if (existing.pages.length < 4 && !existing.pages.includes(page)) existing.pages.push(page);
        return;
      }
      map.set(key, { kind, detail, why: why || '', count: 1, pages: [page] });
    },
    list() { return [...map.values()]; },
    lines() {
      return this.list().map(f => `[${f.kind}] ${f.detail} -- on ${f.pages.join(', ')}${f.count > 1 ? ` (${f.count} pages)` : ''}${f.why ? `; ${f.why}` : ''}`);
    },
  };
}

/*
 * Same-origin frames of a page, one level deep (what the apps use). With a scope (the
 * Administration shell's #dynamic-content panel) only the frames whose <iframe> element
 * sits INSIDE the scope belong to the destination: the shell's own frames are not judged
 * as part of an item, but an iframe-backed .xlink destination is.
 */
async function frameReads(page, scopeSelector) {
  const reads = [];
  const main = await page.evaluate(readPageText, scopeSelector).catch(() => null);
  if (main) reads.push({ where: 'page', read: main });
  for (const frame of page.frames()) {
    if (frame === page.mainFrame()) continue;
    if (!frame.url() || frame.url() === 'about:blank') continue;
    if (scopeSelector) {
      const owner = await frame.frameElement().catch(() => null);
      const inside = owner
        ? await owner.evaluate((element, selector) => !!element.closest(selector), scopeSelector).catch(() => false)
        : false;
      if (owner) await owner.dispose().catch(() => {});
      if (!inside) continue;
    }
    const read = await frame.evaluate(readPageText, null).catch(() => null);
    // The URL rides along so a frame that landed on the login page is reported as a lost session.
    if (read) reads.push({ where: 'frame', read, url: frame.url() });
  }
  return reads;
}

/**
 * Judge one destination page and record the verdict into the ledger.
 * `scope` is a CSS selector for an in-place destination (the admin shell's panel).
 */
async function judgePage(ledger, probe, label, page, options = {}) {
  const { scope = '', responsesFrom = 0, allow = [], skipHeaders = false } = options;
  // Let the response listeners that read allHeaders() settle.
  for (let i = 0; i < 20 && probe.responses.slice(responsesFrom).some(r => r.pending); i += 1) {
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  if (!skipHeaders) {
    for (const entry of probe.responses.slice(responsesFrom)) {
      if (entry.pending || entry.skipped) continue;
      for (const finding of headerFindings({ ...entry }, { https: probe.https })) {
        ledger.add(label, finding.kind, finding.detail, finding.why);
      }
    }
  }
  if (page.isClosed()) return;
  const url = page.url();
  if (!scope && /\/(?:index\.jsp|logout|login)(?:[?#;]|$)/i.test(new URL(url).pathname)) {
    ledger.add(label, 'session-lost', `the link ended on ${new URL(url).pathname.replace(/^\/carlos/, '')}, the login/logout page`);
    return;
  }
  const reads = await frameReads(page, scope);
  if (!reads.length) {
    // A binary destination (PDF viewer) has no text; the response header check above covered it.
    return;
  }
  for (const { where, read, url: frameUrl } of reads) {
    if (where === 'frame') {
      let framePath = '';
      try { framePath = new URL(frameUrl).pathname; } catch { /* not a URL: nothing to judge */ }
      if (/\/(?:index\.jsp|logout|login)(?:[?#;]|$)/i.test(framePath)) {
        ledger.add(label, 'session-lost', `a frame ended on ${framePath.replace(/^\/carlos/, '')}, the login/logout page`);
        continue;
      }
      if (!read.text && !read.values.length) continue;
    }
    for (const finding of scanText(read, allow)) {
      const known = KNOWN_FINDINGS.find(rule => rule.kind.test(finding.kind) && rule.match.test(finding.detail));
      if (known) {
        ledger.known(known.reason);
        continue;
      }
      ledger.add(label, finding.kind, `${finding.detail}${where === 'frame' ? ' [frame]' : ''}`, finding.why);
    }
  }
}

/** Pause until the page settles: load + short network idle. */
async function settle(page, timeout) {
  await page.waitForLoadState('domcontentloaded', { timeout }).catch(() => {});
  await page.waitForLoadState('networkidle', { timeout }).catch(() => {});
}

/** Click one catalogued link and hand back where it went. */
async function openLink(context, hostPage, item, timeout) {
  const link = await resolveAuditLink(hostPage, item, timeout);
  try {
    const stillThere = ((await link.textContent({ timeout }).catch(() => null)) || '').replace(/\s+/g, ' ').trim();
    h.assert(stillThere === item.text,
      `the page changed under the crawl: item ${item.index} was "${item.text}" and is "${stillThere}" now`);
    await revealAuditLink(hostPage, link, timeout);
    await link.scrollIntoViewIfNeeded({ timeout }).catch(() => {});
    if (!(await link.isVisible().catch(() => false))) {
      // Not on screen after the menus were opened: a link in a collapsed or alternate
      // panel the user cannot click either.
      throw new NotVisible(item.text);
    }
    const before = hostPage.url();
    if (item.opensPopup) {
      const outcome = await clickOpensPopupOrNavigates(hostPage, link, { context, label: item.text, timeout, allowPdf: true });
      return { page: outcome.page, isPopup: outcome.isPopup, before, inPlace: false };
    }
    const startTimeout = Math.min(timeout, NAVIGATION_START_TIMEOUT);
    const navigationStarted = hostPage.waitForURL(url => String(url) !== before, { timeout: startTimeout })
      .then(() => true, () => false);
    const fingerprint = await hostPage.evaluate(bodyFingerprint, null).catch(() => null);
    const pagesBefore = [...context.pages()];
    await link.click({ timeout });
    const navigated = await navigationStarted;
    if (navigated) {
      await settle(hostPage, timeout);
      return { page: hostPage, isPopup: false, before, inPlace: false };
    }
    const appeared = context.pages().find(candidate => !pagesBefore.includes(candidate));
    if (appeared) {
      await appeared.waitForURL(url => String(url) !== 'about:blank', { timeout }).catch(() => {});
      await settle(appeared, timeout);
      return { page: appeared, isPopup: true, before, inPlace: false };
    }
    const changed = fingerprint === null ? false : await hostPage.waitForFunction(
      bodyFingerprint, fingerprint, { timeout: startTimeout },
    ).then(() => true, () => false);
    await settle(hostPage, timeout);
    return { page: hostPage, isPopup: false, before, inPlace: true, acted: changed };
  } finally {
    if (item.identity && link.dispose) await link.dispose();
  }
}

/**
 * Click the About / License footer links of a destination page, as a user does, and
 * require each to open its popup. Most pages carry the footer
 *   <a href="javascript:popupStart(300,400,'.../encounter/ViewAbout')">About</a>
 * and popupStart is defined only by encounter.js and a few pages' own scripts, so on the
 * rest the click throws ReferenceError and nothing opens. The throw is recorded by the
 * strict page wiring (reported per page by the crawl); this adds the "nothing opened"
 * finding to the ledger.
 */
async function clickFooterLinks(context, page, label, timeout, ledger) {
  if (page.isClosed()) return;
  const candidates = page.locator('a[href^="javascript:"]').filter({ hasText: /^\s*(About|License)\s*$/ });
  const count = await candidates.count().catch(() => 0);
  for (let index = 0; index < Math.min(count, 2); index += 1) {
    const link = candidates.nth(index);
    if (!(await link.isVisible().catch(() => false))) continue;
    const text = ((await link.textContent().catch(() => '')) || '').trim();
    const popupArrived = context.waitForEvent('page', { timeout: 3000 }).catch(() => null);
    await link.click({ timeout }).catch(() => {});
    const popup = await popupArrived;
    if (!popup) {
      ledger.add(label, 'dead-footer-link', `the "${text}" footer link opened nothing`);
      continue;
    }
    // The popup event fires while the window is still about:blank, where "domcontentloaded"
    // is already true; a link that never navigates must not pass as a working footer link.
    const navigated = await popup.waitForURL(url => String(url) !== 'about:blank', { timeout })
      .then(() => true, () => false);
    if (navigated) await popup.waitForLoadState('domcontentloaded', { timeout }).catch(() => {});
    else ledger.add(label, 'dead-footer-link', `the "${text}" footer link opened a window that never navigated`);
    await popup.close().catch(() => {});
  }
}

/**
 * The ENTRY phase of a check: everything between "the session was healthy" and the first
 * crawled item (opening the surface, the search, the Master Record, the chart ...). The
 * crawl snapshots the recorder per item, so signals raised while getting THERE would
 * otherwise belong to nobody. Take the window before the first opening click and hand it
 * to crawl({entry}) or entryFailures().
 */
function beginEntry(session) {
  return { before: snapshotRecorder(session.recorder), offHostFrom: session.probe.offHost.length };
}

/**
 * Failure lines for the browser signals recorded since `entry` began, plus off-host and
 * mixed-content requests into the ledger. `ignore` lists "uncaught ..." lines the caller
 * already reports itself (the footer check owns its ReferenceError findings).
 */
function entryFailures({ recorder, probe, ledger }, entry, label, ignore = []) {
  for (const off of probe.offHost.slice(entry.offHostFrom)) {
    ledger.add(label, off.mixed ? 'mixed-content' : 'off-host-request',
      `${off.resourceType} request to ${off.url} was blocked (requested by ${off.from || 'unknown'})`);
  }
  return findingsSince(recorder, entry.before, label)
    .filter(line => !/ERR_BLOCKED_BY_CLIENT/.test(line))
    .filter(line => !ignore.some(text => line === `${label}: ${text}`));
}

/**
 * The crawl: click each item, judge the destination, put the host back.
 * `options.entry` ({window: beginEntry(session), label}) adds the browser signals of the
 * entry phase to the failures before the first item.
 *
 * @returns {{opened:string[], skipped:number, failures:string[], ledger}}
 */
async function crawl(options) {
  const {
    context, hostPage, items, recorder, probe, labelPrefix,
  } = options;
  const timeout = options.timeout || 20000;
  const inPlaceTarget = options.inPlaceTarget || '';
  const skipRules = options.skipRules || [];
  const limit = options.limit || 0;
  const allow = options.allow || [];
  const ledger = options.ledger || createLedger();
  const hostUrl = hostPage.url();
  const opened = [];
  const failures = [];
  const reshuffled = [];
  const hidden = [];
  let skipped = 0;
  let attempted = 0;
  if (options.entry) {
    failures.push(...entryFailures({ recorder, probe, ledger }, options.entry.window, options.entry.label));
  }
  for (const item of items) {
    if (limit && attempted >= limit) break;
    if (skipRules.find(rule => rule.match.test(item.text)) || isCurrentDocumentLink(item, hostUrl)) {
      skipped += 1;
      continue;
    }
    attempted += 1;
    const label = `${labelPrefix}:${item.text}`.slice(0, 70);
    const before = snapshotRecorder(recorder);
    const responsesFrom = probe.responses.length;
    const offHostFrom = probe.offHost.length;
    let target = null;
    try {
      if (options.beforeItem) await options.beforeItem(item);
      target = await openLink(context, hostPage, item, timeout);
      if (target.inPlace && target.acted === false) {
        failures.push(`${label}: clicking it did nothing (no navigation, no popup, no page change)`);
      } else {
        // Same two asserts the other audits make, so a page that is an error page is
        // reported as that, not as clean text.
        let body = '';
        try {
          body = target.isPopup || !inPlaceTarget || !target.inPlace
            ? await h.assertNotErrorPage(target.page, label, { allowPdf: true })
            : await destinationText(target, inPlaceTarget, timeout);
        } catch (error) {
          failures.push(`${label}: ${h.withoutQueryStrings(String(error.message).split('\n')[0])}`);
        }
        if (ERROR_PAGE_RE.test(body)) failures.push(`${label}: rendered an error page`);
        else if (!String(body).trim()) failures.push(`${label}: rendered a blank page`);
        await judgePage(ledger, probe, label, target.page, {
          scope: target.inPlace ? inPlaceTarget : '', responsesFrom, allow,
        });
        if (!target.inPlace && options.footerLinks !== false) {
          await clickFooterLinks(context, target.page, label, timeout, ledger);
        }
        opened.push(item.text);
      }
    } catch (error) {
      const message = String(error.message).split('\n')[0];
      if (error instanceof NotVisible) {
        skipped += 1;
        hidden.push(item.text);
      } else if (options.tolerateReshuffle && /missing or changed in its original container|disappeared before it could be selected|changed after identity resolution/.test(message)) {
        // A module that re-renders after an earlier destination closed (the chart's
        // prevention-due list) moved this link; that is the audit's own timing, not a
        // page defect. Counted so it is visible.
        skipped += 1;
        reshuffled.push(item.text);
      } else {
        failures.push(`${label}: ${h.withoutQueryStrings(message)}`);
      }
    } finally {
      // A request this engine aborted shows up in the browser's own signals as a failed
      // resource and a console error; it is reported once, below, as off-host.
      failures.push(...findingsSince(recorder, before, label).filter(line => !/ERR_BLOCKED_BY_CLIENT/.test(line)));
      for (const off of probe.offHost.slice(offHostFrom)) {
        ledger.add(label, off.mixed ? 'mixed-content' : 'off-host-request',
          `${off.resourceType} request to ${off.url} was blocked (requested by ${off.from || 'unknown'})`);
      }
      if (target && target.isPopup) {
        try {
          if (options.beforePopupClose) await options.beforePopupClose(target.page);
        } finally {
          await target.page.close().catch(() => {});
        }
      } else if (hostPage.url() !== hostUrl) {
        await hostPage.goBack({ timeout }).catch(() => {});
        await hostPage.waitForLoadState('domcontentloaded', { timeout }).catch(() => {});
      }
    }
  }
  // Failures already filed elsewhere are counted, not failed again.
  const known = options.knownFailures || [];
  for (let i = failures.length - 1; i >= 0; i -= 1) {
    const rule = known.find(candidate => candidate.match.test(failures[i]));
    if (rule) {
      ledger.known(rule.reason);
      failures.splice(i, 1);
    }
  }
  if (hidden.length) console.log(`  not clickable (hidden in the page): ${hidden.length} link(s)`);
  if (reshuffled.length) console.log(`  not opened (the module re-rendered under the crawl): ${reshuffled.join(', ')}`);
  return { opened, skipped, failures, ledger };
}

/** Fail with one readable list: browser signals first, then hygiene findings. */
function assertHealthy(result, options = {}) {
  const surface = options.surface || 'surface';
  const minimumOpened = options.minimumOpened || 1;
  for (const line of result.ledger.suppressedSummary()) console.log(`  not failed: ${line}`);
  const lines = [...result.failures, ...result.ledger.lines()];
  h.assert(lines.length === 0,
    `${lines.length} page-health finding(s) on ${surface}:\n    - ${lines.join('\n    - ')}`);
  h.assert(result.opened.length >= minimumOpened,
    `Only ${result.opened.length} ${surface} item(s) opened; expected at least ${minimumOpened}, so the catalogue is probably broken rather than the pages`);
}

/**
 * Browser + logged-in context with the probes installed before the first page exists.
 * The caller closes it (always, in a finally).
 */
async function startSession(config) {
  const recorder = h.createRecorder();
  const browser = await h.launchBrowser(config);
  try {
    const context = await h.newContext(browser, config);
    context.setDefaultTimeout(20000);
    const probe = await installProbes(context, config);
    context.on('page', page => h.wireStrictPage(page, 'page-health', recorder));
    const schedulePage = await h.login(context, config, recorder);
    const ledger = createLedger();
    // Filed already (ISSUES.md L95/L100/L135): the Struts `coop` interceptor sends
    // Cross-Origin-Opener-Policy: same-origin on every action response, which severs
    // window.opener app-wide. Counted here so the rule still runs, but not failed again
    // on all 100+ pages -- one pattern, one issue.
    ledger.suppress('header-coop-severs-opener', 'Struts coop interceptor, ISSUES.md L100 pattern');
    // The pages BEFORE the crawl: the login redirect and the schedule itself are pages
    // too, and nothing else reads what the browser said about them.
    await judgePage(ledger, probe, 'schedule', schedulePage, {});
    for (const off of probe.offHost) {
      ledger.add('login/schedule', off.mixed ? 'mixed-content' : 'off-host-request',
        `${off.resourceType} request to ${off.url} was blocked (requested by ${off.from || 'unknown'})`);
    }
    h.assertStrictPage(recorder);
    return {
      browser, context, recorder, probe, schedulePage, ledger, config,
      async close() { await browser.close().catch(() => {}); },
    };
  } catch (error) {
    await browser.close().catch(() => {});
    throw error;
  }
}

module.exports = {
  TEXT_RULES, assertHealthy, beginEntry, createLedger, crawl, entryFailures, headerFindings, installProbes, judgePage,
  readPageText, scanText, startSession,
};
