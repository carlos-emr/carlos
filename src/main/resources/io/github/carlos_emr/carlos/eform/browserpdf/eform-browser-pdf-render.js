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

const fs = require('fs');
const path = require('path');
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
    if (document.fonts && document.fonts.ready) {
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

async function main() {
  const args = parseArgs(process.argv.slice(2));
  assert(args['base-url'], 'Missing --base-url');
  assert(args['app-path'], 'Missing --app-path');
  assert(args['output-path'], 'Missing --output-path');

  const baseUrl = validateBaseUrl(args['base-url']);
  const outputPath = path.resolve(args['output-path']);
  fs.mkdirSync(path.dirname(outputPath), { recursive: true });

  const browser = await chromium.launch(getLaunchOptions(args['chrome-path']));
  const page = await browser.newPage();
  const consoleIssues = [];
  const pageErrors = [];
  page.on('console', (message) => {
    if (message.type() === 'error') {
      consoleIssues.push(message.text());
    }
  });
  page.on('pageerror', (error) => {
    pageErrors.push(error.stack || error.message);
  });

  try {
    await page.emulateMedia({ media: 'screen' });
    await page.goto(appUrl(baseUrl, args['app-path']), { waitUntil: 'domcontentloaded', timeout: 30000 });
    await waitForStableRender(page);
    await page.pdf({
      path: outputPath,
      format: 'Letter',
      printBackground: true,
      preferCSSPageSize: true,
      margin: {
        top: '0.4in',
        right: '0.3in',
        bottom: '0.4in',
        left: '0.3in',
      },
    });
  } finally {
    await browser.close();
  }

  if (!fs.existsSync(outputPath) || fs.statSync(outputPath).size === 0) {
    throw new Error('Playwright completed without creating a readable PDF file');
  }

  if (consoleIssues.length || pageErrors.length) {
    const details = { consoleIssues, pageErrors };
    console.error(JSON.stringify(details));
  }
}

main().catch((error) => {
  console.error(error && error.stack ? error.stack : String(error));
  process.exitCode = 1;
});
