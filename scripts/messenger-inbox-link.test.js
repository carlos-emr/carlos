/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

// Pins the shared "back to the Inbox" locator of the messenger browser checks to the
// messenger JSPs (issue #5349). #5250 moved the box pages' Inbox tab from a bare
// DisplayMessages href to DisplayMessages?boxType=0, which the old locator excluded; the
// checks then clicked nothing from the Archived box and failed as an anonymous 20 s
// waitForResponse timeout. This renders each page's anchors from the real JSP source and
// asserts the locator's first match is the Inbox, so a changed href fails here, offline.

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const { INBOX_LINK } = require('./messenger-demographic-link-playwright-checks');

const JSP_DIR = path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/messenger');

// The anchors a browser would see, in document order, with their class list and href.
// JSP comments, scriptlets and expressions are dropped (an expression only appends
// query parameters to these hrefs), and EL becomes the context path, so hrefs keep their
// path and their literal query start. Entities are decoded as getAttribute would.
function anchors(source) {
  const html = source
    .replace(/<%--[\s\S]*?--%>/g, '')
    .replace(/<%[\s\S]*?%>/g, '')
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\$\{pageContext\.request\.contextPath\}/g, '/carlos')
    .replace(/\$\{[^}]*\}/g, '')
    .replace(/<[a-z]+:[^>]*\/>/gi, '');
  return [...html.matchAll(/<a\b([^>]*)>/gi)].map(([, attrs]) => {
    const attr = name => {
      const m = attrs.match(new RegExp(`\\b${name}\\s*=\\s*("([^"]*)"|'([^']*)')`, 'i'));
      return m ? (m[2] ?? m[3]).replaceAll('&amp;', '&') : null;
    };
    return { tag: 'a', classes: new Set((attr('class') || '').split(/\s+/).filter(Boolean)), href: attr('href') };
  });
}

// A matcher for exactly the selector forms INBOX_LINK uses: a tag, .class, [attr*="v"],
// :not([attr*="v"]) and Playwright's :visible (true for a rendered anchor). Anything else
// throws, so a locator rewritten in other syntax cannot pass by being half understood.
function compile(selectorList) {
  return selectorList.split(',').map(part => {
    let rest = part.trim();
    const checks = [];
    const tag = rest.match(/^[a-z]+/i);
    if (tag) {
      checks.push(el => el.tag === tag[0].toLowerCase());
      rest = rest.slice(tag[0].length);
    }
    while (rest) {
      let m;
      if ((m = rest.match(/^\.([\w-]+)/))) {
        const cls = m[1];
        checks.push(el => el.classes.has(cls));
      } else if ((m = rest.match(/^\[(\w+)\*="([^"]*)"\]/))) {
        const [, name, value] = m;
        checks.push(el => el[name] != null && el[name].includes(value));
      } else if ((m = rest.match(/^:not\(\[(\w+)\*="([^"]*)"\]\)/))) {
        const [, name, value] = m;
        checks.push(el => !(el[name] ?? '').includes(value));
      } else if ((m = rest.match(/^:visible/))) {
        // Every anchor rendered here is visible.
      } else {
        throw new Error(`unsupported selector syntax at "${rest}" in "${part.trim()}"`);
      }
      rest = rest.slice(m[0].length);
    }
    return el => checks.every(check => check(el));
  });
}

const matchers = compile(INBOX_LINK);
const firstMatch = elements => elements.find(el => matchers.some(match => match(el)));

// The Inbox: DisplayMessages with no box or box 0. It may carry the current sort and search
// (the tab keeps them since #5250), but a column-sort link, whose query starts with orderby,
// re-sorts the page in view and is not the way back.
function isInbox(href) {
  const url = new URL(href, 'https://clinic.test/');
  return url.pathname.endsWith('/messenger/DisplayMessages')
    && [null, '0'].includes(url.searchParams.get('boxType'))
    && !url.search.startsWith('?orderby=');
}

for (const [page, why] of [
  ['DisplayMessages.jsp', 'the Inbox, Sent and Archived box pages (one template), via the Inbox tab'],
  ['ViewMessage.jsp', 'a message, via the Inbox button ahead of the Sent button'],
  ['CreateMessage.jsp', 'a compose or reply draft, via the Inbox button'],
  ['SentMessage.jsp', 'the sent confirmation, via the Inbox button'],
]) {
  test(`shouldReachInbox_from${page.replace('.jsp', '')}`, () => {
    const elements = anchors(fs.readFileSync(path.join(JSP_DIR, page), 'utf8'));
    const first = firstMatch(elements);
    assert.ok(first, `INBOX_LINK matches no anchor on ${page} (${why})`);
    assert.ok(isInbox(first.href), `INBOX_LINK's first match on ${page} is ${first.href}, not the Inbox (${why})`);
  });
}

test('shouldPickInboxTab_overSentArchivedAndSortLinksOnBoxPage', () => {
  const elements = anchors(fs.readFileSync(path.join(JSP_DIR, 'DisplayMessages.jsp'), 'utf8'));
  const tabs = elements.filter(el => el.classes.has('nav-link') && /\/messenger\/DisplayMessages\?boxType=\d/.test(el.href || ''));
  assert.deepEqual(tabs.map(el => new URL(el.href, 'https://clinic.test').searchParams.get('boxType')), ['0', '1', '2'],
    'the box page renders Inbox, Sent and Archived tabs');
  const matched = elements.filter(el => matchers.some(match => match(el)));
  assert.ok(matched.length > 0, 'INBOX_LINK matches no anchor on the box page');
  assert.deepEqual(matched.map(el => isInbox(el.href)), matched.map(() => true),
    'on the box page INBOX_LINK matches only the Inbox tab, never Sent, Archived or a column-sort link');
});

test('shouldMatchPre5250InboxTab_whenHrefCarriesNoBoxType', () => {
  const tab = { tag: 'a', classes: new Set(['nav-link']), href: '/carlos/messenger/DisplayMessages?scheduleNav=1' };
  const sort = { tag: 'a', classes: new Set(['nav-link']), href: '/carlos/messenger/DisplayMessages?orderby=subject&boxType=2' };
  const archived = { tag: 'a', classes: new Set(['nav-link']), href: '/carlos/messenger/DisplayMessages?boxType=2&orderby=!date' };
  assert.equal(firstMatch([sort, archived, tab]), tab, 'packages built before #5250 still reach the Inbox');
});

test('shouldRejectUnsupportedSyntax_forSelectorMatcher', () => {
  assert.throws(() => compile('a > span'), /unsupported selector syntax/);
});
