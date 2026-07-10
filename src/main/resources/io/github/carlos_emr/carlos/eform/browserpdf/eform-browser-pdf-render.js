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

const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');
const { appUrl, assert, getLaunchOptions, validateBaseUrl } = require('./eform-local-playwright-utils');

function parseArgs(argv) {
  const parsed = {};
  for (let index = 0; index < argv.length; index += 1) {
    const key = argv[index];
    const value = argv[index + 1];
    if (!key.startsWith('--') || value === undefined) {
      throw new Error(`Invalid argument sequence near ${key || '<eof>'}`);
    }
    parsed[key.slice(2)] = value;
    index += 1;
  }
  return parsed;
}

async function waitForStableRender(page) {
  await page.waitForLoadState('domcontentloaded', { timeout: 30000 });
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await page.waitForTimeout(1500);
  await page.evaluate(async () => {
    if (document.fonts && document.fonts.ready instanceof Promise) {
      await document.fonts.ready;
    }

    const pendingImages = Array.from(document.images).filter((image) => !image.complete);
    await Promise.all(pendingImages.map((image) => new Promise((resolve) => {
      image.addEventListener('load', resolve, { once: true });
      image.addEventListener('error', resolve, { once: true });
    })));

    await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)));
  });
}

async function preparePageForCapture(page) {
  await page.evaluate(() => {
    const existingCleanupStyle = document.getElementById('eform-browser-pdf-render-cleanup');
    if (!existingCleanupStyle) {
      const cleanupStyle = document.createElement('style');
      cleanupStyle.id = 'eform-browser-pdf-render-cleanup';
      cleanupStyle.textContent = `
        .DoNotPrint,
        #BottomButtons,
        #BaseSelect,
        #SupplementalInfo,
        #labDetail {
          display: none !important;
          visibility: hidden !important;
        }
        textarea {
          resize: none !important;
        }
      `;
      document.head.appendChild(cleanupStyle);
    }

    const body = document.body;
    if (body) {
      body.style.margin = '0';
      body.style.padding = '0';
      body.style.width = 'max-content';
      body.style.overflow = 'visible';
    }
    const html = document.documentElement;
    if (html) {
      html.style.margin = '0';
      html.style.padding = '0';
      html.style.background = 'white';
      html.style.overflow = 'visible';
    }
  });
}

async function computeCaptureRegions(page) {
  return page.evaluate(() => {
    function rectFromElement(el) {
      const rect = el.getBoundingClientRect();
      return {
        left: rect.left + window.scrollX,
        top: rect.top + window.scrollY,
        right: rect.right + window.scrollX,
        bottom: rect.bottom + window.scrollY,
        width: rect.width,
        height: rect.height,
      };
    }

    function unionRects(elements) {
      let left = Number.POSITIVE_INFINITY;
      let top = Number.POSITIVE_INFINITY;
      let right = 0;
      let bottom = 0;
      for (const el of elements) {
        const style = window.getComputedStyle(el);
        if (style.display === 'none' || style.visibility === 'hidden' || style.position === 'fixed') {
          continue;
        }
        const rect = rectFromElement(el);
        if (rect.width <= 0 || rect.height <= 0) {
          continue;
        }
        left = Math.min(left, rect.left);
        top = Math.min(top, rect.top);
        right = Math.max(right, rect.right);
        bottom = Math.max(bottom, rect.bottom);
      }
      if (!Number.isFinite(left) || !Number.isFinite(top) || right <= left || bottom <= top) {
        return null;
      }
      return { x: Math.max(0, left), y: Math.max(0, top), width: right - left, height: bottom - top };
    }

    const pageNodes = Array.from(document.querySelectorAll('[id]')).filter((el) => /^page\d+$/i.test(el.id));
    const captures = [];
    for (const pageNode of pageNodes) {
      const pageElements = [pageNode, ...pageNode.querySelectorAll('*')];
      const backgroundCandidates = pageElements
        .filter((el) => el.tagName === 'IMG')
        .filter((el) => /(^BGImage$|background image|bgimage)/i.test(el.id || '') || /background image/i.test(el.getAttribute('alt') || ''))
        .map((el) => rectFromElement(el))
        .filter((rect) => rect.width > 0 && rect.height > 0)
        .sort((a, b) => (b.width * b.height) - (a.width * a.height));
      const rect = backgroundCandidates.length > 0
        ? { x: Math.max(0, backgroundCandidates[0].left), y: Math.max(0, backgroundCandidates[0].top), width: backgroundCandidates[0].width, height: backgroundCandidates[0].height }
        : unionRects(pageElements);
      if (rect) {
        captures.push(rect);
      }
    }

    if (captures.length > 0) {
      return captures;
    }

    const fallback = unionRects(Array.from(document.body.querySelectorAll('*')));
    return fallback ? [fallback] : [];
  });
}

async function capturePages(page, outputDir) {
  const regions = await computeCaptureRegions(page);
  assert(regions.length > 0, 'Could not determine any eForm page regions to capture');
  const files = [];
  for (let index = 0; index < regions.length; index += 1) {
    const clip = regions[index];
    const outputPath = path.join(outputDir, `page-${String(index + 1).padStart(3, '0')}.png`); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal -- outputDir is normalized from a validated local artifact directory and the filename is a fixed renderer-generated basename
    await page.screenshot({
      path: outputPath,
      clip: {
        x: Math.max(0, Math.floor(clip.x)),
        y: Math.max(0, Math.floor(clip.y)),
        width: Math.ceil(clip.width),
        height: Math.ceil(clip.height),
      },
      type: 'png',
    });
    files.push(outputPath);
  }
  return files;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const rawBaseUrl = process.env.CARLOS_EFORM_RENDER_BASE_URL || args['base-url'];
  const rawAppPath = process.env.CARLOS_EFORM_RENDER_APP_PATH || args['app-path'];
  const rawCookieHeader = process.env.CARLOS_EFORM_RENDER_COOKIE_HEADER || args['cookie-header'];
  const rawChromePath = process.env.CARLOS_EFORM_RENDER_CHROME_PATH || args['chrome-path'];

  assert(rawBaseUrl, 'Missing renderer base URL');
  assert(rawAppPath, 'Missing renderer application path');
  assert(args['output-dir'], 'Missing --output-dir');

  const baseUrl = validateBaseUrl(rawBaseUrl);
  const outputDir = path.resolve(args['output-dir']);
  fs.mkdirSync(outputDir, { recursive: true });

  const browser = await chromium.launch(getLaunchOptions(rawChromePath));
  const page = await browser.newPage({ viewport: { width: 1800, height: 3200 } });
  if (rawCookieHeader) {
    await page.setExtraHTTPHeaders({ Cookie: rawCookieHeader });
  }
  const consoleIssues = [];
  const pageErrors = [];
  page.on('console', (message) => {
    if (message.type() === 'error') {
      consoleIssues.push(message.text());
    }
  });
  page.on('pageerror', (error) => {
    pageErrors.push(error.stack ?? error.message);
  });

  let captureFiles = [];
  try {
    await page.emulateMedia({ media: 'screen' });
    await page.goto(appUrl(baseUrl, rawAppPath), { waitUntil: 'domcontentloaded', timeout: 30000 }); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- validateBaseUrl restricts hosts to local/private by default and appUrl rejects non-root-relative or protocol-relative paths
    await waitForStableRender(page);
    await preparePageForCapture(page);
    captureFiles = await capturePages(page, outputDir);
  } finally {
    await browser.close();
  }

  if (captureFiles.length === 0) {
    throw new Error('Playwright completed without creating any page captures');
  }

  const manifestPath = path.join(outputDir, 'manifest.json');
  fs.writeFileSync(manifestPath, JSON.stringify({ captureFiles }, null, 2));

  if (consoleIssues.length || pageErrors.length) {
    const details = { consoleIssues, pageErrors };
    console.error(JSON.stringify(details));
  }
}

main().catch((error) => {
  console.error(error?.stack ? error.stack : String(error));
  process.exitCode = 1;
});
