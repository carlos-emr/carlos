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
const DIAGNOSTIC_PREFIX = 'CARLOS_EFORM_RENDER_DIAGNOSTIC ';

function diagnosticLog(payload) {
  console.error(`${DIAGNOSTIC_PREFIX}${JSON.stringify(payload)}`);
}

function canParseUrl(rawUrl) {
  if (!rawUrl) {
    return false;
  }
  try {
    new URL(rawUrl);
    return true;
  } catch {
    return false;
  }
}

function sanitizeDiagnosticUrl(rawUrl) {
  if (!canParseUrl(rawUrl)) {
    return null;
  }
  const parsed = new URL(rawUrl);
  return `${parsed.origin}${parsed.pathname}`;
}

function redactDiagnosticUrls(text) {
  return String(text).replace(/https?:\/\/[^\s'"<>]+/gi, '[redacted-url]');
}

function sanitizeDiagnosticError(error) {
  if (!error) {
    return 'unknown';
  }
  if (typeof error === 'string') {
    return redactDiagnosticUrls(error);
  }
  if (typeof error.message === 'string' && error.message.trim() !== '') {
    return redactDiagnosticUrls(error.message);
  }
  return redactDiagnosticUrls(String(error));
}

function summarizeCountMap(entries) {
  return Object.fromEntries(Object.entries(entries).sort(([left], [right]) => left.localeCompare(right)));
}

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

function isAllowedRendererRequestUrl(requestUrl, allowedOrigin) {
  if (requestUrl.startsWith('data:') || requestUrl.startsWith('blob:') || requestUrl.startsWith('about:')) {
    return true;
  }
  if (!canParseUrl(requestUrl)) {
    return false;
  }
  const parsed = new URL(requestUrl);
  return ['http:', 'https:'].includes(parsed.protocol) && parsed.origin === allowedOrigin;
}

async function computeCaptureRegions(page) {
  return page.evaluate(() => {
    const rectFromElement = (el) => {
      const elementRect = el.getBoundingClientRect();
      return {
        left: elementRect.left + window.scrollX,
        top: elementRect.top + window.scrollY,
        right: elementRect.right + window.scrollX,
        bottom: elementRect.bottom + window.scrollY,
        width: elementRect.width,
        height: elementRect.height,
      };
    };
    const isVisibleCaptureCandidate = (el) => {
      const style = window.getComputedStyle(el);
      return style.display !== 'none' && style.visibility !== 'hidden' && style.position !== 'fixed';
    };
    const unionRects = (elements) => {
      let left = Number.POSITIVE_INFINITY;
      let top = Number.POSITIVE_INFINITY;
      let right = 0;
      let bottom = 0;
      for (const el of elements) {
        if (!isVisibleCaptureCandidate(el)) {
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
    };
    const backgroundCandidates = (elements) => elements
      .filter((el) => el.tagName === 'IMG')
      .filter((el) => /(^BGImage$|background image|bgimage)/i.test(el.id || '')
        || /background image/i.test(el.getAttribute('alt') || ''))
      .filter(isVisibleCaptureCandidate)
      .map(rectFromElement)
      .filter((rect) => rect.width > 0 && rect.height > 0)
      .sort((a, b) => (b.width * b.height) - (a.width * a.height));
    const rectFromLargestCandidate = (candidateRects) => {
      if (candidateRects.length === 0) {
        return null;
      }
      const rect = candidateRects[0];
      return {
        x: Math.max(0, rect.left),
        y: Math.max(0, rect.top),
        width: rect.width,
        height: rect.height,
      };
    };
    const dedupeAndSortCaptureRects = (rects) => rects
      .sort((a, b) => a.top - b.top || a.left - b.left)
      .filter((rect, index, sorted) => {
        if (index === 0) {
          return true;
        }
        const previous = sorted[index - 1];
        return Math.abs(rect.left - previous.left) > 2
          || Math.abs(rect.top - previous.top) > 2
          || Math.abs(rect.width - previous.width) > 2
          || Math.abs(rect.height - previous.height) > 2;
      })
      .map((rect) => ({
        x: Math.max(0, rect.left),
        y: Math.max(0, rect.top),
        width: rect.width,
        height: rect.height,
      }));

    const allElements = Array.from(document.body ? document.body.querySelectorAll('*') : []);
    const pageNodes = allElements.filter((el) => /^page\d+$/i.test(el.id));
    const captures = pageNodes
      .map((pageNode) => {
        const pageElements = [pageNode, ...pageNode.querySelectorAll('*')];
        return rectFromLargestCandidate(backgroundCandidates(pageElements)) || unionRects(pageElements);
      })
      .filter(Boolean);

    if (captures.length > 0) {
      return captures;
    }

    const pageBackgroundCaptures = dedupeAndSortCaptureRects(backgroundCandidates(allElements));
    if (pageBackgroundCaptures.length > 0) {
      return pageBackgroundCaptures;
    }

    const fallback = unionRects(allElements);
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
  assert(fs.existsSync(outputDir) && fs.statSync(outputDir).isDirectory(), `Renderer output directory must already exist, got ${outputDir}`);

  const browser = await chromium.launch(getLaunchOptions(rawChromePath));
  const context = await browser.newContext({ viewport: { width: 1800, height: 3200 } });
  const diagnostics = {
    stage: 'launch',
    baseUrlOrigin: baseUrl.origin,
    baseUrlPath: baseUrl.pathname,
    appPath: sanitizeDiagnosticUrl(appUrl(baseUrl, rawAppPath)),
    mainDocumentStatus: null,
    finalPageUrl: null,
    consoleErrorCount: 0,
    pageErrorCount: 0,
    blockedRequestCounts: {},
    requestFailureCounts: {},
    captureCount: 0,
  };
  diagnosticLog({ event: 'start', ...diagnostics });
  if (rawCookieHeader) {
    // Scope the session cookie to the validated renderer application URL instead of a page-wide
    // extra header, so it stays confined to the intended host and app context.
    const cookies = rawCookieHeader.split(';')
      .map((pair) => pair.trim())
      .filter((pair) => pair.includes('='))
      .map((pair) => {
        const separator = pair.indexOf('=');
        return {
          name: pair.slice(0, separator).trim(),
          value: pair.slice(separator + 1).trim(),
          url: baseUrl.href,
        };
      });
    if (cookies.length) {
      await context.addCookies(cookies);
    }
  }
  const page = await context.newPage();
  page.on('response', (response) => {
    const request = response.request();
    if (request.isNavigationRequest() && request.frame() === page.mainFrame()) {
      diagnostics.mainDocumentStatus = response.status();
      diagnostics.finalPageUrl = sanitizeDiagnosticUrl(response.url());
      diagnostics.stage = 'navigated';
    }
  });
  await page.route('**/*', async (route) => {
    const request = route.request();
    const requestUrl = request.url();
    if (isAllowedRendererRequestUrl(requestUrl, baseUrl.origin)) {
      await route.continue();
      return;
    }

    const resourceType = request.resourceType();
    diagnostics.blockedRequestCounts[resourceType] = (diagnostics.blockedRequestCounts[resourceType] || 0) + 1;
    await route.abort('blockedbyclient').catch(() => {});
  });
  page.on('requestfailed', (request) => {
    const failureText = request.failure()?.errorText || 'unknown';
    diagnostics.requestFailureCounts[failureText] = (diagnostics.requestFailureCounts[failureText] || 0) + 1;
  });
  page.on('console', (message) => {
    if (message.type() === 'error') {
      diagnostics.consoleErrorCount += 1;
    }
  });
  page.on('pageerror', () => {
    diagnostics.pageErrorCount += 1;
  });

  let captureFiles = [];
  try {
    try {
      await page.emulateMedia({ media: 'screen' });
      await page.goto(appUrl(baseUrl, rawAppPath), { waitUntil: 'domcontentloaded', timeout: 30000 }); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- validateBaseUrl restricts hosts to local/private by default and appUrl rejects non-root-relative or protocol-relative paths
      await waitForStableRender(page);
      await preparePageForCapture(page);
      diagnostics.stage = 'capturing';
      captureFiles = await capturePages(page, outputDir);
      diagnostics.captureCount = captureFiles.length;
      diagnostics.stage = 'captured';
      diagnosticLog({ event: 'captured', ...diagnostics });
    } catch (error) {
      diagnosticLog({
        event: 'failure',
        ...diagnostics,
        reason: 'render_exception',
        error: sanitizeDiagnosticError(error),
        blockedRequestCounts: summarizeCountMap(diagnostics.blockedRequestCounts),
        requestFailureCounts: summarizeCountMap(diagnostics.requestFailureCounts),
      });
      throw error;
    }
  } finally {
    await browser.close();
  }

  if (captureFiles.length === 0) {
    diagnosticLog({ event: 'failure', ...diagnostics, reason: 'no_captures' });
    throw new Error('Playwright completed without creating any page captures');
  }

  if (diagnostics.consoleErrorCount || diagnostics.pageErrorCount || Object.keys(diagnostics.blockedRequestCounts).length) {
    diagnosticLog({
      event: 'failure',
      ...diagnostics,
      reason: 'browser_errors',
      blockedRequestCounts: summarizeCountMap(diagnostics.blockedRequestCounts),
      requestFailureCounts: summarizeCountMap(diagnostics.requestFailureCounts),
    });
    throw new Error('Playwright render surfaced browser errors');
  }
}

main().catch((error) => {
  console.error(error?.stack ? error.stack : String(error));
  process.exitCode = 1;
});
