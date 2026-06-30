#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Static browser regression checks for the eForm admin navigation and editor
 * redirect markup touched by PR 2710.
 *
 * This does not require a running CARLOS app. It renders the JSP fragment after
 * replacing server-side placeholders, loads the real bundled Bootstrap 5 script,
 * and verifies the Create eForm dropdown behaves like a user-clickable menu.
 *
 * Optional environment:
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   EFORM_SCREENSHOT_DIR=/tmp
 */

const fs = require('fs');
const { chromium } = require('playwright');
const { buildArtifactPath } = require('./eform-local-playwright-utils');

const chromePath = process.env.CHROME_PATH || '';
const screenshotDir = process.env.EFORM_SCREENSHOT_DIR || '/tmp';

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

// nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal
// __dirname is a Node.js built-in constant for this script's directory, not user-controlled input
const navJspPath = `${__dirname}/../src/main/webapp/WEB-INF/jsp/eform/efmTopNav.jspf`;
const editorJspPath = `${__dirname}/../src/main/webapp/WEB-INF/jsp/eform/efmformmanageredit.jsp`;
const bootstrapJsPath = `${__dirname}/../src/main/webapp/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js`;
const bootstrapCssPath = `${__dirname}/../src/main/webapp/library/bootstrap/5.3.8/css/bootstrap.min.css`;

function readNavJsp() {
  return fs.readFileSync(navJspPath, 'utf8');
}

function readEditorJsp() {
  return fs.readFileSync(editorJspPath, 'utf8');
}

function jspFragmentToHtml(jsp) {
  return jsp
    .replace(/<%--[\s\S]*?--%>/g, '')
    .replace(/<%@[^%]*%>/g, '')
    .replace(/<%=\s*request\.getContextPath\(\)\s*%>/g, '/carlos')
    .replace(/<%=\s*[^%]+\s*%>/g, '/carlos')
    .replace(/<fmt:setBundle\b[^>]*\/?>/g, '')
    .replace(/<fmt:message\s+key="([^"]+)"\s*\/?>/g, '$1');
}

function appPathFromHref(href) {
  const parsed = new URL(href, 'http://localhost');
  return parsed.pathname;
}

(async () => {
  const navJsp = readNavJsp();
  const editorJsp = readEditorJsp();
  const navHtml = jspFragmentToHtml(navJsp);

  assert(!navHtml.includes('javascript:void(0)'), 'eForm nav must not use javascript:void(0) links');
  assert(!editorJsp.includes('window.opener.location'), 'eForm editor must not navigate window.opener after save');
  assert(editorJsp.includes('window.location.href'), 'eForm editor should navigate the current window after save');

  const browser = await chromium.launch({
    headless: true,
    executablePath: chromePath || undefined,
  });

  try {
    const page = await browser.newPage({ viewport: { width: 960, height: 480 } });
    const consoleIssues = [];
    page.on('console', (message) => {
      if (['error', 'warning'].includes(message.type())) {
        consoleIssues.push(`${message.type()}: ${message.text()}`);
      }
    });
    page.on('pageerror', (error) => consoleIssues.push(error.stack || error.message));

    // nosemgrep: javascript.playwright.security.audit.playwright-setcontent-injection.playwright-setcontent-injection
    // setContent receives a hardcoded HTML skeleton, not user-supplied data
    await page.setContent(`<!doctype html>
<html>
<head>
<meta charset="utf-8">
</head>
<body><div id="fixture-root"></div></body>
</html>`, { waitUntil: 'domcontentloaded' });
    // nosemgrep: javascript.lang.security.audit.unknown-value-with-script-tag.unknown-value-with-script-tag
    // bootstrapCssPath and bootstrapJsPath are repo-local file paths derived from __dirname, not user input
    await page.addStyleTag({ path: bootstrapCssPath });
    await page.addScriptTag({ path: bootstrapJsPath });
    // nosemgrep: javascript.lang.security.audit.unknown-value-with-script-tag.unknown-value-with-script-tag
    // navHtml is derived from reading a local JSP file via fs.readFileSync and regex-stripping JSP tags
    await page.locator('#fixture-root').evaluate((root, html) => {
      root.innerHTML = html;
    }, navHtml);

    const toggle = page.locator('li.nav-item.dropdown button.contentLink.nav-link.dropdown-toggle[data-bs-toggle="dropdown"]').first();
    await toggle.waitFor({ state: 'visible', timeout: 10000 });

    const toggleState = await toggle.evaluate((button) => ({
      tagName: button.tagName,
      type: button.getAttribute('type'),
      classes: button.getAttribute('class'),
      dataToggle: button.getAttribute('data-bs-toggle'),
      ariaHasPopup: button.getAttribute('aria-haspopup'),
      ariaExpanded: button.getAttribute('aria-expanded'),
    }));
    assert(toggleState.tagName === 'BUTTON', 'Create eForm toggle should be a button');
    assert(toggleState.type === 'button', 'Create eForm button should not submit forms');
    assert(/\bcontentLink\b/.test(toggleState.classes), 'Create eForm toggle should retain contentLink styling');
    assert(/\bnav-link\b/.test(toggleState.classes), 'Create eForm toggle should use Bootstrap nav-link styling');
    assert(/\bdropdown-toggle\b/.test(toggleState.classes), 'Create eForm toggle should be a dropdown-toggle');
    assert(toggleState.dataToggle === 'dropdown', 'Create eForm toggle should use Bootstrap 5 data-bs-toggle');
    assert(toggleState.ariaHasPopup === 'true', 'Create eForm toggle should advertise popup semantics');
    assert(toggleState.ariaExpanded === 'false', 'Create eForm toggle should start collapsed');
    assert((await toggle.textContent()).includes('eform.create'), 'Create eForm toggle should render the eform.create message key in the static JSP fixture');

    await toggle.click();
    const menu = page.locator('li.nav-item.dropdown .dropdown-menu').first();
    await page.waitForFunction(() => document.querySelector('li.nav-item.dropdown .dropdown-menu')?.classList.contains('show'));
    assert(await menu.isVisible(), 'Create eForm dropdown menu should be visible after click');
    assert(await toggle.getAttribute('aria-expanded') === 'true', 'Create eForm toggle should expand aria-expanded after click');

    const menuItems = await page.locator('li.nav-item.dropdown .dropdown-menu a').evaluateAll((links) => links.map((link) => ({
      text: link.textContent.trim(),
      href: link.getAttribute('href'),
      classes: link.getAttribute('class') || '',
      onclick: link.getAttribute('onclick') || '',
    })));
    assert(menuItems.length === 3, `Expected 3 Create eForm menu items, got ${menuItems.length}`);
    assert(menuItems.every((item) => /\bdropdown-item\b/.test(item.classes)), 'Every Create eForm menu item should use dropdown-item');
    assert(menuItems.some((item) => appPathFromHref(item.href) === '/carlos/eform/visualEformEditor'), 'Visual editor route missing');
    assert(menuItems.some((item) => appPathFromHref(item.href) === '/carlos/eform/efmformmanageredit'), 'Create-in-editor route missing');
    assert(menuItems.some((item) => item.onclick.includes('/carlos/eform/eformGenerator')), 'eForm generator popup route missing');

    const screenshotPath = buildArtifactPath(screenshotDir, 'eform-admin-dropdown');
    await page.screenshot({ path: screenshotPath, fullPage: true }); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal -- buildArtifactPath constrains output to a validated local artifact directory with a sanitized basename

    assert(consoleIssues.length === 0, `Unexpected browser console issues:\n${consoleIssues.join('\n')}`);
    console.log(`eForm admin Playwright checks passed; screenshot: ${screenshotPath}`);
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error(error.stack || error.message);
  process.exit(1);
});
