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
const {
  appUrl,
  assert,
  getLaunchOptions,
  isSevereConsoleMessage,
  validateBaseUrl,
} = require('./eform-local-playwright-utils');

const IMAGE_WAIT_TIMEOUT_MS = 5000;

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
  await page.evaluate(async (imageWaitTimeoutMs) => {
    if (document.fonts && document.fonts.ready instanceof Promise) {
      await document.fonts.ready;
    }

    function imageLabel(image) {
      return image.currentSrc || image.src || image.getAttribute('src') || image.id || '<unknown image>';
    }

    function waitForImageComplete(image, timeoutMs) {
      if (!image.currentSrc && !image.src && !image.getAttribute('src')) {
        return Promise.resolve();
      }

      if (image.complete) {
        if (image.naturalWidth > 0 && image.naturalHeight > 0) {
          return Promise.resolve();
        }
        return Promise.reject(new Error(`Image failed to load before PDF capture: ${imageLabel(image)}`));
      }

      return new Promise((resolve, reject) => {
        let settled = false;
        let timeoutId;
        const finish = (error) => {
          if (settled) {
            return;
          }
          settled = true;
          clearTimeout(timeoutId);
          image.removeEventListener('load', handleLoad);
          image.removeEventListener('error', handleError);
          if (error) {
            reject(error);
          } else {
            resolve();
          }
        };
        const handleLoad = () => {
          if (image.naturalWidth > 0 && image.naturalHeight > 0) {
            finish();
            return;
          }
          finish(new Error(`Image loaded without decoded dimensions before PDF capture: ${imageLabel(image)}`));
        };
        const handleError = () => finish(new Error(`Image failed to load before PDF capture: ${imageLabel(image)}`));
        timeoutId = setTimeout(
          () => finish(new Error(`Timed out waiting for image before PDF capture: ${imageLabel(image)}`)),
          timeoutMs,
        );
        image.addEventListener('load', handleLoad, { once: true });
        image.addEventListener('error', handleError, { once: true });
      });
    }

    await Promise.all(Array.from(document.images).map((image) => waitForImageComplete(image, imageWaitTimeoutMs)));

    await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)));
  }, IMAGE_WAIT_TIMEOUT_MS);
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
    const captureToRect = (capture) => ({
      left: capture.x,
      top: capture.y,
      right: capture.x + capture.width,
      bottom: capture.y + capture.height,
      width: capture.width,
      height: capture.height,
    });
    const rectToCapture = (rect) => ({
      x: Math.max(0, rect.left),
      y: Math.max(0, rect.top),
      width: rect.right - rect.left,
      height: rect.bottom - rect.top,
    });
    const mergeCaptureRegions = (regions) => {
      const rects = regions.filter(Boolean).map(captureToRect);
      if (rects.length === 0) {
        return null;
      }
      return rectToCapture({
        left: Math.min(...rects.map((rect) => rect.left)),
        top: Math.min(...rects.map((rect) => rect.top)),
        right: Math.max(...rects.map((rect) => rect.right)),
        bottom: Math.max(...rects.map((rect) => rect.bottom)),
      });
    };
    const backgroundCandidates = (elements) => elements
      .filter((el) => el.tagName === 'IMG')
      .filter((el) => /(^BGImage$|background image|bgimage)/i.test(el.id || '')
        || /background image/i.test(el.getAttribute('alt') || ''))
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

    const pageNodes = Array.from(document.querySelectorAll('[id]')).filter((el) => /^page\d+$/i.test(el.id));
    const captures = pageNodes
      .map((pageNode) => {
        const pageElements = [pageNode, ...pageNode.querySelectorAll('*')];
        return mergeCaptureRegions([
          rectFromLargestCandidate(backgroundCandidates(pageElements)),
          unionRects(pageElements),
        ]);
      })
      .filter(Boolean);

    if (captures.length > 0) {
      return captures;
    }

    const fallback = unionRects([document.body, ...document.body.querySelectorAll('*')].filter(Boolean));
    return fallback ? [fallback] : [];
  });
}

function isSameOriginUrl(rawUrl, baseUrl) {
  try {
    return new URL(rawUrl).origin === baseUrl.origin;
  } catch {
    return false;
  }
}

async function installSameOriginRequestGuard(context, baseUrl) {
  await context.route('**/*', (route) => {
    const requestUrl = route.request().url();
    if (isSameOriginUrl(requestUrl, baseUrl)) {
      return route.continue();
    }
    return route.abort('blockedbyclient');
  });
}

function assertRendererPageOrigin(page, baseUrl) {
  const currentUrl = page.url();
  assert(isSameOriginUrl(currentUrl, baseUrl), `Renderer navigated outside ${baseUrl.origin}: ${currentUrl}`);
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

  const consoleIssues = [];
  const pageErrors = [];
  let captureFiles = [];
  const browser = await chromium.launch(getLaunchOptions(rawChromePath));
  try {
    const context = await browser.newContext({
      viewport: { width: 1800, height: 3200 },
      ignoreHTTPSErrors: baseUrl.protocol === 'https:',
    });
    await installSameOriginRequestGuard(context, baseUrl);
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
    page.on('console', (message) => {
      if (isSevereConsoleMessage(message)) {
        consoleIssues.push({
          type: message.type(),
          text: message.text(),
          location: message.location(),
        });
      }
    });
    page.on('pageerror', (error) => {
      pageErrors.push(error?.name || 'Error');
    });

    await page.emulateMedia({ media: 'screen' });
    const response = await page.goto(appUrl(baseUrl, rawAppPath), { waitUntil: 'domcontentloaded', timeout: 30000 }); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- validateBaseUrl restricts hosts to local/private by default, appUrl rejects non-root-relative or protocol-relative paths, and installSameOriginRequestGuard aborts cross-origin requests before navigation
    assertRendererPageOrigin(page, baseUrl);
    assert(response?.ok(), `Renderer route returned HTTP ${response?.status() ?? 'no response'}`);
    await waitForStableRender(page);
    assertRendererPageOrigin(page, baseUrl);
    await preparePageForCapture(page);
    assertRendererPageOrigin(page, baseUrl);
    captureFiles = await capturePages(page, outputDir);
  } finally {
    await browser.close();
  }

  if (captureFiles.length === 0) {
    throw new Error('Playwright completed without creating any page captures');
  }

  if (consoleIssues.length || pageErrors.length) {
    const details = {
      consoleErrorCount: consoleIssues.length,
      pageErrorCount: pageErrors.length,
      consoleErrors: consoleIssues.slice(0, 10),
      pageErrorTypes: [...new Set(pageErrors)],
    };
    console.error(JSON.stringify(details));
  }
  if (consoleIssues.length) {
    throw new Error('Console error while rendering eForm PDF');
  }
  if (pageErrors.length) {
    throw new Error('Unhandled page error while rendering eForm PDF');
  }
}

main().catch((error) => {
  console.error(error?.stack ? error.stack : String(error));
  process.exitCode = 1;
});
