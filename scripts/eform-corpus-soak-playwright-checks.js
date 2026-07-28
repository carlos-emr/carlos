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
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/**
 * eForm CORPUS SOAK — imports real third-party eForm ZIP packages through the production
 * import route and reports which ones actually render to PDF.
 *
 * WHY THIS EXISTS
 * The repo's other eForm Playwright checks use synthetic fixtures authored to suit the renderer.
 * Real clinic forms (e.g. the collections published on oscargalaxy.org) are two decades of
 * hand-authored HTML and exercise paths fixtures never reach: bare relative image srcs,
 * CDN-hosted jQuery, signature blocks, multi-page scanned backgrounds. This script measures the
 * corpus compatibility gap instead of guessing at it.
 *
 * IMPORTANT — it imports through the REAL route: a ZIP POST to
 * /eform/manageEForm?method=importEForm (EFormExportZip.importForm), the same path an
 * administrator uses. Do NOT "simplify" it to upload the HTML and images separately: that
 * bypasses the importer entirely and produces failures that look like renderer defects but are
 * artifacts of the harness (filenames are normalised differently on the two paths).
 *
 * Local developer tool, deliberately NOT part of CI: it needs eForm packages that are not in the
 * repo, and third-party form quality is outside CARLOS's control.
 *
 * SEED A PROVIDER SIGNATURE FIRST, or the numbers understate compatibility badly. A large share of
 * the corpus carries the legacy stamp script, which builds its URL at runtime:
 *
 *   document.getElementById('StampSignature').src =
 *           "../eform/displayImage.do?imagefile=consult_sig_" + ProviderNumber + ".png";
 *
 * With no signature on disk for the test provider that request 404s and the render is refused as
 * incomplete - correctly, but for a reason that says nothing about CARLOS. Measured on one 49-package
 * batch: 31/49 without a signature, 41/49 with one. Drop any PNG at
 * <eform image dir>/consult_sig_<providerNo>.png (999998 for the devcontainer's carlosdoc).
 *
 * USAGE
 *   EFORM_CORPUS_DIR=/path/to/zips npm run test:eform-corpus-soak
 *
 * Each *.zip in EFORM_CORPUS_DIR is imported, opened for EFORM_CORPUS_DEMOGRAPHIC (default 1),
 * saved, and downloaded as a PDF. Results are written to <EFORM_CORPUS_OUT>/corpus-soak.json
 * alongside each PDF, so the PDFs can be opened and inspected — which is the point. A clean
 * completeness gate does not mean the page is correct; a blank background and a letter printed as
 * raw markup both passed every automated check before anyone looked at the output.
 */

const { chromium } = require('playwright');
const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');
// Shared eForm Playwright helpers. gotoApp is the repository's guarded navigation path:
// validateBaseUrl restricts the host to loopback unless explicitly overridden. Using it instead of
// raw page.goto() is what keeps this script — which
// logs in and performs destructive imports and saves — from being a URL-injection sink.
const {
  getLaunchOptions,
  gotoApp,
  validateBaseUrl,
} = require('./eform-local-playwright-utils');

const config = {
  baseUrl: validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos'),
  chromePath: process.env.CHROME_PATH || '',
  testUser: process.env.TEST_USER || 'carlosdoc',
  testPassword: process.env.TEST_PASSWORD || 'carlos2026',
  testPin: process.env.TEST_PIN || '2026',
  corpusDir: process.env.EFORM_CORPUS_DIR || '',
  outDir: process.env.EFORM_CORPUS_OUT || '/tmp/eform-corpus-soak',
  demographicNo: process.env.EFORM_CORPUS_DEMOGRAPHIC || '1',
  renderTimeoutMs: Number(process.env.EFORM_CORPUS_RENDER_TIMEOUT_MS || 120000),
};

/**
 * Reads one entry out of a ZIP without extracting it or shelling out to `unzip` (which is not
 * installed in the devcontainer). Walks the central directory, then inflates the entry.
 *
 * Scanning the archive as text does NOT work: eform.properties is deflated, so `form.name=` never
 * appears literally in the bytes. An earlier version did exactly that, silently found no names,
 * and reported "0/5 rendered" for a run in which all five had in fact imported correctly.
 */
function readZipEntry(zipPath, nameSuffix) {
  const buf = fs.readFileSync(zipPath);
  let eocd = -1;
  for (let i = buf.length - 22; i >= 0 && i > buf.length - 65558; i--) {
    if (buf.readUInt32LE(i) === 0x06054b50) { eocd = i; break; }
  }
  if (eocd < 0) return null;
  let offset = buf.readUInt32LE(eocd + 16);
  const entries = buf.readUInt16LE(eocd + 10);
  for (let n = 0; n < entries; n++) {
    if (buf.readUInt32LE(offset) !== 0x02014b50) return null;
    const method = buf.readUInt16LE(offset + 10);
    const compressedSize = buf.readUInt32LE(offset + 20);
    const nameLen = buf.readUInt16LE(offset + 28);
    const extraLen = buf.readUInt16LE(offset + 30);
    const commentLen = buf.readUInt16LE(offset + 32);
    const localOffset = buf.readUInt32LE(offset + 42);
    const name = buf.toString('utf8', offset + 46, offset + 46 + nameLen);
    if (name.endsWith(nameSuffix)) {
      const localNameLen = buf.readUInt16LE(localOffset + 26);
      const localExtraLen = buf.readUInt16LE(localOffset + 28);
      const start = localOffset + 30 + localNameLen + localExtraLen;
      const raw = buf.subarray(start, start + compressedSize);
      return method === 0 ? raw.toString('utf8') : zlib.inflateRawSync(raw).toString('utf8');
    }
    offset += 46 + nameLen + extraLen + commentLen;
  }
  return null;
}

/** The form.name the importer will store for this package, or null if unreadable. */
function packageFormName(zipPath) {
  const properties = readZipEntry(zipPath, 'eform.properties');
  const match = properties && properties.match(/^form\.name=(.*)$/m);
  return match ? match[1].trim() || null : null;
}

/**
 * Rasterizes page 1 of a random sample of successfully rendered PDFs so a human can actually look at
 * them.
 *
 * A clean completeness gate does not mean the page is correct — a blank background and a letter
 * printed as raw markup both passed every automated check in this project before anyone opened the
 * output. The sample is random rather than fixed so repeated runs cover different forms over time
 * instead of re-confirming the same handful.
 *
 * The PICKS are always reported, even where no rasterizer is available — that list is the portable
 * part and is what tells a reviewer which files to open. Rasterization via pdftoppm (poppler-utils)
 * is a convenience layered on top; its absence is noted, never fatal, since the render results are
 * the primary product.
 */
function renderVisualSample(results, outDir, sampleSize) {
  const rendered = results.filter((r) => r.outcome === 'PDF OK' && r.pdfBytes > 0);
  if (rendered.length === 0) return [];
  const pool = rendered.slice();
  const picked = [];
  while (picked.length < Math.min(sampleSize, pool.length)) {
    picked.push(pool.splice(Math.floor(Math.random() * pool.length), 1)[0]);
  }
  console.log(`\nvisual check — ${picked.length} randomly picked of ${rendered.length} rendered:`);
  for (const result of picked) {
    const safe = String(result.form).replace(/[^A-Za-z0-9]+/g, '_').slice(0, 60);
    console.log(`  ${path.join(outDir, `${safe}.pdf`)}\t${result.form}`);
  }

  const sampleDir = path.join(outDir, 'visual-sample');
  fs.mkdirSync(sampleDir, { recursive: true });
  const images = [];
  for (const result of picked) {
    const safeName = String(result.form).replace(/[^A-Za-z0-9]+/g, '_').slice(0, 60);
    const pdf = path.join(outDir, `${safeName}.pdf`);
    if (!fs.existsSync(pdf)) continue;
    const stem = path.join(sampleDir, safeName);
    try {
      require('node:child_process').execFileSync(
        'pdftoppm', ['-png', '-r', '80', '-f', '1', '-l', '1', pdf, stem], { stdio: 'pipe' });
      for (const produced of fs.readdirSync(sampleDir)) {
        if (produced.startsWith(`${safeName}-`) && produced.endsWith('.png')) {
          images.push({ form: result.form, image: path.join(sampleDir, produced) });
        }
      }
    } catch (error) {
      // No rasterizer (or a corrupt PDF). The pick list above still names the file to open.
      console.log(`  (no preview image for ${result.form}: ${String(error.message).split('\n')[0].slice(0, 60)})`);
    }
  }
  return images;
}

function launchOptions() {
  return getLaunchOptions(config.chromePath);
}

(async () => {
  if (!config.corpusDir) {
    console.error('EFORM_CORPUS_DIR is required — point it at a directory of eForm .zip packages.');
    process.exit(2);
  }
  const zips = fs.readdirSync(config.corpusDir).filter((f) => f.toLowerCase().endsWith('.zip')).sort();
  if (zips.length === 0) {
    console.error(`No .zip packages found in ${config.corpusDir}`);
    process.exit(2);
  }
  fs.mkdirSync(config.outDir, { recursive: true });

  const browser = await chromium.launch(launchOptions());
  const results = [];
  try {
    const context = await browser.newContext({
      ignoreHTTPSErrors: true,
      viewport: { width: 1400, height: 1100 },
      acceptDownloads: true,
    });
    // The toolbar hands the generated PDF back as base64 in a hidden input and then clears it, so
    // latch the value on DOMContentLoaded before downloadEForm() wipes it.
    await context.addInitScript(() => {
      document.addEventListener('DOMContentLoaded', () => {
        const el = document.getElementById('eFormPDF');
        if (el && el.value) window.__carlosCapturedPdf = el.value;
      });
    });

    const page = await context.newPage();
    await gotoApp(page, config.baseUrl, '/');
    await page.fill('input[name="username"]', config.testUser);
    await page.fill('input[name="password"]', config.testPassword);
    await page.fill('input[name="pin"]', config.testPin);
    await Promise.all([
      page.waitForLoadState('domcontentloaded'),
      page.click('button[type="submit"], input[type="submit"]'),
    ]);
    await page.waitForLoadState('networkidle').catch(() => {});

    // --- import every package through the production ZIP importer ---
    const importer = await context.newPage();
    for (const zip of zips) {
      // Go straight to the import partial rather than the manager page: on the manager the partial
      // lives in a collapsed accordion, so its submit button is present but never visible.
      await gotoApp(importer, config.baseUrl, '/eform/partials/import');
      await importer.waitForLoadState('networkidle').catch(() => {});
      await importer.locator('#zippedForm').setInputFiles(path.join(config.corpusDir, zip));
      await Promise.all([
        importer.waitForLoadState('domcontentloaded').catch(() => {}),
        importer.locator('input[type="submit"][name="subm"]').click(),
      ]);
      await importer.waitForTimeout(2500);
      console.log(`imported ${zip}`);
    }
    await importer.close();

    // --- render each imported form ---
    const manager = await context.newPage();
    await gotoApp(manager, config.baseUrl, '/eform/efmformmanager');
    await manager.waitForLoadState('networkidle').catch(() => {});

    for (const zip of zips) {
      const formName = packageFormName(path.join(config.corpusDir, zip));
      const result = {
        package: zip, form: formName, fid: null, fdid: null,
        pdfBytes: 0, outcome: '', httpErrors: [], consoleErrors: [],
      };
      if (!formName) {
        result.outcome = 'SKIPPED: no form.name in eform.properties';
        results.push(result);
        console.log(JSON.stringify(result));
        continue;
      }
      try {
        const row = manager.locator('#eformTbl tbody tr').filter({ hasText: formName }).first();
        const href = await row.locator('a[href*="efmformmanageredit?fid="]').first().getAttribute('href');
        result.fid = new URL(href, config.baseUrl).searchParams.get('fid');
      } catch (error) {
        result.outcome = `NOT IMPORTED: ${error.message.split('\n')[0].slice(0, 120)}`;
        results.push(result);
        console.log(JSON.stringify(result));
        continue;
      }

      const form = await context.newPage();
      form.on('console', (m) => {
        if (m.type() === 'error') result.consoleErrors.push(m.text().slice(0, 160));
      });
      form.on('response', (r) => {
        if (r.status() >= 400) {
          result.httpErrors.push(`${r.status()} ${r.url().replace(config.baseUrl, '').slice(0, 110)}`);
        }
      });
      try {
        // Identifiers are encoded rather than trusted: fid is scraped from the manager page and
        // demographicNo comes from the environment, so neither is inherently safe to interpolate.
        await gotoApp(form, config.baseUrl,
            `/eform/efmformadd_data?fid=${encodeURIComponent(result.fid)}`
            + `&demographic_no=${encodeURIComponent(config.demographicNo)}`);
        await form.waitForLoadState('networkidle').catch(() => {});
        await form.evaluate(() => {
          const subject = document.getElementById('remote_eform_subject');
          if (subject) subject.value = 'corpus soak';
        });
        await Promise.all([
          form.waitForLoadState('domcontentloaded'),
          form.click('#remoteSubmitButton'),
        ]);
        await form.waitForLoadState('networkidle').catch(() => {});
        result.fdid = await form.locator('#fdid').inputValue().catch(() => null);

        await form.click('#remoteDownloadButton');
        await form.waitForFunction(() => !!window.__carlosCapturedPdf, null, { timeout: config.renderTimeoutMs });
        const pdf = Buffer.from(await form.evaluate(() => window.__carlosCapturedPdf), 'base64');
        const safeName = formName.replace(/[^A-Za-z0-9]+/g, '_').slice(0, 60);
        // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal -- basename is sanitized to [A-Za-z0-9_] above and written under the configured output directory
        fs.writeFileSync(path.join(config.outDir, `${safeName}.pdf`), pdf);
        result.pdfBytes = pdf.length;
        result.outcome = pdf.subarray(0, 5).toString('latin1') === '%PDF-' ? 'PDF OK' : 'NOT A PDF';
      } catch (error) {
        result.outcome = `NO PDF: ${error.message.split('\n')[0].slice(0, 120)}`;
      } finally {
        await form.close().catch(() => {});
      }
      results.push(result);
      console.log(JSON.stringify(result));
    }
    await manager.close();
  } finally {
    await browser.close();
  }

  fs.writeFileSync(path.join(config.outDir, 'corpus-soak.json'), JSON.stringify(results, null, 1));
  const ok = results.filter((r) => r.outcome === 'PDF OK').length;
  console.log(`\ncorpus soak: ${ok}/${results.length} rendered — PDFs in ${config.outDir}`);
  console.log('Open them. A clean render gate does not mean the page is correct.');

  const sample = renderVisualSample(results, config.outDir, Number(process.env.EFORM_CORPUS_VISUAL_SAMPLE || 6));
  if (sample.length) {
    console.log(`\npreview images (page 1 each):`);
    for (const entry of sample) {
      console.log(`  ${entry.image}\t${entry.form}`);
    }
  }
})().catch((error) => {
  console.error('FAIL eForm corpus soak');
  console.error(error.stack || error.message);
  process.exit(1);
});
