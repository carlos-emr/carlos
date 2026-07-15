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
 * Browser-backed regression coverage for the main eForm render surfaces using
 * the Child Psychiatry referral form as a stable background-heavy baseline.
 *
 * Covered surfaces:
 *   1. Library/admin preview
 *   2. Add/open eForm surface
 *   3. Saved eForm direct reopen
 *   4. Patient eForm list popup reopen
 *   5. Toolbar PDF download generation
 *   6. Consultation attachment PDF preview
 *   7. Fax preview page PDF preview
 *
 * Defaults are for the local devcontainer:
 *   node scripts/eform-render-surfaces-playwright-checks.js
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc
 *   TEST_PASSWORD=carlos2026
 *   TEST_PIN=2026
 *   EFORM_RENDER_SURFACE_DEMOGRAPHIC_NO=1
 *   EFORM_RENDER_SURFACE_SCREENSHOT_DIR=/tmp/eform-render-surfaces
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 */

const fs = require('fs');
const path = require('path');
const os = require('os');
const crypto = require('crypto');
const { execFileSync } = require('child_process');
const { chromium } = require('playwright');
const {
  assert,
  buildArtifactPath,
  createRecorder,
  getLaunchOptions,
  gotoApp,
  login,
  openManager,
  validateBaseUrl,
  wirePage,
} = require('./eform-local-playwright-utils');

const formName = 'FH Child and Youth Psychiatry Clinics Referral';
const demographicNo = process.env.EFORM_RENDER_SURFACE_DEMOGRAPHIC_NO || '1';
const screenshotDir = process.env.EFORM_RENDER_SURFACE_SCREENSHOT_DIR || '/tmp/eform-render-surfaces';
const chromePath = process.env.CHROME_PATH || '';
const config = {
  baseUrl: validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos'),
  testUser: process.env.TEST_USER || 'carlosdoc',
  testPassword: process.env.TEST_PASSWORD || 'carlos2026',
  testPin: process.env.TEST_PIN || '2026',
};

const PDFBOX_VERSION = '3.0.7';
const PDFBOX_CLASSPATH = [
  `/root/.m2/repository/org/apache/pdfbox/pdfbox/${PDFBOX_VERSION}/pdfbox-${PDFBOX_VERSION}.jar`,
  `/root/.m2/repository/org/apache/pdfbox/fontbox/${PDFBOX_VERSION}/fontbox-${PDFBOX_VERSION}.jar`,
  `/root/.m2/repository/org/apache/pdfbox/pdfbox-io/${PDFBOX_VERSION}/pdfbox-io-${PDFBOX_VERSION}.jar`,
  '/root/.m2/repository/commons-logging/commons-logging/1.3.6/commons-logging-1.3.6.jar',
].join(':');
const PDFBOX_RENDER_DUMP_SOURCE = `
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.ImageType;
public class PdfRenderDump {
  public static void main(String[] args) throws Exception {
    Path pdf = Path.of(args[0]);
    String prefix = args[1];
    try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
      PDFRenderer renderer = new PDFRenderer(doc);
      for (int i = 0; i < doc.getNumberOfPages(); i++) {
        BufferedImage img = renderer.renderImageWithDPI(i, 96, ImageType.RGB);
        Path out = Path.of(prefix + "-page" + (i + 1) + ".png");
        ImageIO.write(img, "png", out.toFile());
      }
    }
  }
}
`;

function waitQuiet(page) {
  return Promise.all([
    page.waitForLoadState('domcontentloaded', { timeout: 30000 }).catch(() => {}),
    page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {}),
  ]);
}

async function waitForFaxPreviewElement(page) {
  try {
    await Promise.any([
      page.locator('#previewPDF').first().waitFor({ state: 'attached', timeout: 15000 }),
      page.locator('img[src*="/fax/faxAction?method=getPreview"]').first().waitFor({ state: 'attached', timeout: 15000 }),
    ]);
  } catch {
    throw new Error('Fax preview page did not render a PDF object or image preview within 15 seconds');
  }
}

function toDataUrl(filePath) {
  const ext = path.extname(filePath).toLowerCase();
  const mimeType = ext === '.png' ? 'image/png' : 'application/octet-stream';
  return `data:${mimeType};base64,${fs.readFileSync(filePath).toString('base64')}`;
}

function sha256(buffer) {
  return crypto.createHash('sha256').update(buffer).digest('hex');
}

function sameOriginUrl(rawUrl, label) {
  const candidate = new URL(rawUrl, config.baseUrl);
  const expectedOrigin = new URL(config.baseUrl).origin;
  assert(candidate.origin === expectedOrigin, `${label} must be same-origin with ${expectedOrigin}: ${rawUrl}`);
  return candidate.href;
}

function sameOriginUrls(rawUrls, label) {
  return rawUrls.map((rawUrl, index) => sameOriginUrl(rawUrl, `${label} ${index + 1}`));
}

function assertDataImageUrl(dataUrl, label) {
  assert(/^data:image\/png;base64,[A-Za-z0-9+/=]+$/.test(dataUrl), `${label} must be a PNG data URL`);
}

function isIgnorableLegacyFaxIssue(urlOrText) {
  return /onBodyLoad_Oct2018\.js/.test(urlOrText)
    || /jSignature\.min\.js/.test(urlOrText)
    || /consult_sig_.*\.png/.test(urlOrText)
    || /provider\/providerSignatureImage\?providerNo=999998/.test(urlOrText)
    || /code\.jquery\.com\/jquery-2\.2\.1\.min\.js/.test(urlOrText);
}

async function compareImageFiles(comparePage, baselinePath, candidatePath) {
  const baselineSrc = toDataUrl(baselinePath);
  const candidateSrc = toDataUrl(candidatePath);
  assertDataImageUrl(baselineSrc, 'baseline image');
  assertDataImageUrl(candidateSrc, 'candidate image');
  return comparePage.evaluate(async ({ baselineSrc, candidateSrc }) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- arguments are locally generated PNG data URLs, not network URLs or interpolated code
    function loadImage(src) {
      return new Promise((resolve, reject) => {
        const image = new Image();
        image.onload = () => resolve(image);
        image.onerror = () => reject(new Error(`Failed to load image ${src.slice(0, 64)}`));
        image.src = src;
      });
    }

    const [baselineImage, candidateImage] = await Promise.all([
      loadImage(baselineSrc),
      loadImage(candidateSrc),
    ]);

    const width = baselineImage.naturalWidth;
    const height = baselineImage.naturalHeight;
    const baselineCanvas = document.createElement('canvas');
    baselineCanvas.width = width;
    baselineCanvas.height = height;
    const baselineContext = baselineCanvas.getContext('2d', { willReadFrequently: true });
    baselineContext.drawImage(baselineImage, 0, 0, width, height);

    const candidateCanvas = document.createElement('canvas');
    candidateCanvas.width = width;
    candidateCanvas.height = height;
    const candidateContext = candidateCanvas.getContext('2d', { willReadFrequently: true });
    candidateContext.imageSmoothingEnabled = false;
    candidateContext.drawImage(candidateImage, 0, 0, width, height);

    const baselinePixels = baselineContext.getImageData(0, 0, width, height).data;
    const candidatePixels = candidateContext.getImageData(0, 0, width, height).data;

    let mismatchPixels = 0;
    let totalDelta = 0;
    let maxChannelDelta = 0;
    const channelTolerance = 16;

    for (let index = 0; index < baselinePixels.length; index += 4) {
      const redDelta = Math.abs(baselinePixels[index] - candidatePixels[index]);
      const greenDelta = Math.abs(baselinePixels[index + 1] - candidatePixels[index + 1]);
      const blueDelta = Math.abs(baselinePixels[index + 2] - candidatePixels[index + 2]);
      const alphaDelta = Math.abs(baselinePixels[index + 3] - candidatePixels[index + 3]);
      const pixelDelta = Math.max(redDelta, greenDelta, blueDelta, alphaDelta);
      totalDelta += redDelta + greenDelta + blueDelta + alphaDelta;
      if (pixelDelta > channelTolerance) {
        mismatchPixels += 1;
      }
      if (pixelDelta > maxChannelDelta) {
        maxChannelDelta = pixelDelta;
      }
    }

    return {
      baselineSize: { width: baselineImage.naturalWidth, height: baselineImage.naturalHeight },
      candidateSize: { width: candidateImage.naturalWidth, height: candidateImage.naturalHeight },
      comparedSize: { width, height },
      mismatchPixels,
      totalPixels: width * height,
      mismatchRatio: mismatchPixels / (width * height),
      meanChannelDelta: totalDelta / (width * height * 4),
      maxChannelDelta,
    };
  }, { baselineSrc, candidateSrc });
}

async function compareImageSeries(comparePage, baselinePaths, candidatePaths, label) {
  assert(baselinePaths.length === candidatePaths.length, `${label} image count ${candidatePaths.length} did not match baseline ${baselinePaths.length}`);
  const comparisons = [];
  for (let index = 0; index < baselinePaths.length; index += 1) {
    comparisons.push({
      page: index + 1,
      baselinePath: baselinePaths[index],
      candidatePath: candidatePaths[index],
      ...(await compareImageFiles(comparePage, baselinePaths[index], candidatePaths[index])),
    });
  }
  return comparisons;
}

function assertComparisonWithinThreshold(comparisons, label, maxMismatchRatio, maxMeanChannelDelta) {
  for (const comparison of comparisons) {
    assert(
      comparison.baselineSize.width === comparison.candidateSize.width
        && comparison.baselineSize.height === comparison.candidateSize.height,
      `${label} page ${comparison.page} size ${comparison.candidateSize.width}x${comparison.candidateSize.height} did not match baseline ${comparison.baselineSize.width}x${comparison.baselineSize.height}`,
    );
    assert(
      comparison.mismatchRatio <= maxMismatchRatio,
      `${label} page ${comparison.page} mismatch ratio ${comparison.mismatchRatio.toFixed(4)} exceeded ${maxMismatchRatio}`,
    );
    assert(
      comparison.meanChannelDelta <= maxMeanChannelDelta,
      `${label} page ${comparison.page} mean channel delta ${comparison.meanChannelDelta.toFixed(4)} exceeded ${maxMeanChannelDelta}`,
    );
  }
}

function renderPdfToImages(pdfPath, outputPrefix) {
  const compileDir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-pdfbox-render-dump-'));
  const sourcePath = path.join(compileDir, 'PdfRenderDump.java');
  try {
    fs.chmodSync(compileDir, 0o700);
    fs.writeFileSync(sourcePath, PDFBOX_RENDER_DUMP_SOURCE, { mode: 0o600 });
    execFileSync('javac', ['-cp', PDFBOX_CLASSPATH, sourcePath], { stdio: 'pipe' });
    execFileSync('java', ['-Djava.awt.headless=true', '-cp', `${compileDir}:${PDFBOX_CLASSPATH}`, 'PdfRenderDump', pdfPath, outputPrefix], { stdio: 'pipe' });
    const files = [];
    for (let page = 1; ; page += 1) {
      const candidate = `${outputPrefix}-page${page}.png`;
      if (!fs.existsSync(candidate)) {
        break;
      }
      files.push(candidate);
    }
    assert(files.length > 0, `No rendered PDF page images were produced for ${pdfPath}`);
    return files;
  } finally {
    fs.rmSync(compileDir, { recursive: true, force: true });
  }
}

async function comparePdfFilesVisually(comparePage, baselinePdfPath, candidatePdfPath, label) {
  const renderRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-pdf-compare-'));
  try {
    const baselinePrefix = path.join(renderRoot, 'baseline');
    const candidatePrefix = path.join(renderRoot, 'candidate');
    const baselineImages = renderPdfToImages(baselinePdfPath, baselinePrefix);
    const candidateImages = renderPdfToImages(candidatePdfPath, candidatePrefix);
    return await compareImageSeries(comparePage, baselineImages, candidateImages, label);
  } finally {
    fs.rmSync(renderRoot, { recursive: true, force: true });
  }
}

async function extractSurfaceState(page, label) {
  const bodyText = await page.locator('body').innerText({ timeout: 10000 }).catch(() => '');
  assert(!/CARLOS has encountered an unexpected error|HTTP Status 500|Exception Report|Whitelabel Error Page/i.test(bodyText), `${label} rendered an error page`);

  const pageIds = [];
  for (const id of ['page1', 'page2', 'page3', 'page4']) {
    if (await page.locator(`#${id}`).count()) {
      pageIds.push(id);
    }
  }
  assert(pageIds.length >= 1, `${label} did not render any page containers`);

  const bgImages = await page.locator('img').evaluateAll((imgs) => imgs
    .filter((img) => /displayImage\?imagefile=/.test(img.src))
    .map((img) => ({
      id: img.id || '',
      src: img.src,
      file: (() => {
        try {
          return new URL(img.src).searchParams.get('imagefile') || img.src;
        } catch (error) {
          return img.src;
        }
      })(),
      width: img.naturalWidth || 0,
      height: img.naturalHeight || 0,
    })));
  assert(bgImages.length >= 2, `${label} expected at least two background images, got ${JSON.stringify(bgImages)}`);

  return { pageIds, bgImages };
}

async function screenshotPages(page, prefix) {
  const screenshots = [];
  for (const id of ['page1', 'page2', 'page3', 'page4']) {
    const locator = page.locator(`#${id}`);
    if (!(await locator.count())) {
      continue;
    }
    const outputPath = buildArtifactPath(screenshotDir, `${prefix}-${id}`);
    await locator.screenshot({ path: outputPath });
    screenshots.push(outputPath);
  }
  return screenshots;
}

async function downloadBackgroundImages(page, images, prefix) {
  const safeImages = images.map((image, index) => ({
    ...image,
    src: sameOriginUrl(image.src, `background image ${index + 1}`),
  }));
  const downloaded = await page.evaluate(async (bgImages) => Promise.all(bgImages.map(async (img) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- img.src values are prevalidated against the configured app origin and rechecked in-page before fetch
    const imageUrl = new URL(img.src, window.location.href);
    if (imageUrl.origin !== window.location.origin) {
      throw new Error(`Refusing to fetch cross-origin background image: ${img.src}`);
    }
    const response = await fetch(imageUrl.href, { credentials: 'same-origin' });
    const buffer = await response.arrayBuffer();
    const bytes = new Uint8Array(buffer);
    let binary = '';
    for (let index = 0; index < bytes.length; index += 1) {
      binary += String.fromCharCode(bytes[index]);
    }
    return {
      file: img.file,
      contentType: response.headers.get('content-type') || '',
      base64: btoa(binary),
    };
  })), safeImages);

  return downloaded.map((image, index) => {
    const outputPath = buildArtifactPath(screenshotDir, `${prefix}-page${index + 1}`);
    fs.writeFileSync(outputPath, Buffer.from(image.base64, 'base64'));
    return outputPath;
  });
}

async function openManagerPreview(context, row, recorder) {
  const popupPromise = context.waitForEvent('page');
  await row.locator('a[onclick*="efmshowform_data?fid="]').first().click();
  const popup = await popupPromise;
  wirePage(popup, 'manager-preview', recorder);
  await waitQuiet(popup);
  return popup;
}

async function openAddSurface(context, fid, recorder) {
  const page = await context.newPage();
  await page.addInitScript(() => {
    window.close = () => {
      window.__playwrightCloseIntercepted = true;
    };
  });
  wirePage(page, 'add-surface', recorder);
  await gotoApp(page, config.baseUrl, `/eform/efmformadd_data?fid=${encodeURIComponent(fid)}&demographic_no=${encodeURIComponent(demographicNo)}`);
  await waitQuiet(page);
  return page;
}

async function openSavedDirect(context, fdid, recorder) {
  const page = await context.newPage();
  wirePage(page, 'saved-direct', recorder);
  await gotoApp(page, config.baseUrl, `/eform/efmshowform_data?fdid=${encodeURIComponent(fdid)}`);
  await waitQuiet(page);
  return page;
}

async function openPatientListPopup(context, fdid, recorder) {
  const page = await context.newPage();
  wirePage(page, 'patient-list', recorder);
  await gotoApp(page, config.baseUrl, `/eform/efmpatientformlist?demographic_no=${encodeURIComponent(demographicNo)}`);
  await waitQuiet(page);

  let popupPromise = context.waitForEvent('page');
  const directLink = page.locator(`a[href*="fdid=${fdid}"]`).first();
  if (await directLink.count()) {
    await directLink.click();
  } else {
    const onclickLink = page.locator(`a[onclick*="fdid=${fdid}"]`).first();
    assert(await onclickLink.count(), `Could not find patient list entry for fdid ${fdid}`);
    await onclickLink.click();
  }

  const popup = await popupPromise;
  wirePage(popup, 'patient-list-popup', recorder);
  await waitQuiet(popup);
  await page.close();
  return popup;
}

async function saveCurrentEform(page) {
  const subject = page.locator('#remote_eform_subject');
  if (await subject.count()) {
    await subject.fill(`Child psychiatry render verification ${Date.now()}`);
  }
  await page.locator('#remoteSubmitButton').click();
  await waitQuiet(page);
  await page.locator('#fdid').waitFor({ state: 'attached', timeout: 20000 });
  const fdid = await page.locator('#fdid').inputValue();
  assert(/^\d+$/.test(fdid), `Expected numeric fdid after save, got ${fdid}`);
  return fdid;
}

async function generatePdfFromSurface(page, artifactBaseName = 'child-psychiatry-rendered') {
  const [download] = await Promise.all([
    page.waitForEvent('download', { timeout: 60000 }),
    page.locator('#remoteDownloadButton').click(),
  ]);
  const pdfPath = buildArtifactPath(screenshotDir, artifactBaseName, '.pdf');
  await download.saveAs(pdfPath);

  const pdfBytes = fs.readFileSync(pdfPath);
  assert(pdfBytes.subarray(0, 5).toString('utf8') === '%PDF-', `Downloaded artifact is not a PDF at ${pdfPath}`);

  return {
    pdfPath,
    pdfBytes: pdfBytes.length,
    pdfSha256: sha256(pdfBytes),
    suggestedFilename: download.suggestedFilename(),
  };
}

async function openConsultationPage(context, recorder) {
  const page = await context.newPage();
  wirePage(page, 'consultation', recorder);
  await gotoApp(page, config.baseUrl, `/encounter/oscarConsultationRequest/ViewConsultationFormRequest?de=${encodeURIComponent(demographicNo)}&teamVar=&appNo=`);
  await waitQuiet(page);
  return page;
}

async function prepareConsultationForm(page) {
  const specialistSelect = page.locator('#specialist');
  assert(await specialistSelect.count(), 'Consultation page did not render specialist selector');

  const options = await specialistSelect.locator('option').evaluateAll((nodes) => nodes
    .map((node) => ({ value: node.value, text: (node.textContent || '').trim() }))
    .filter((option) => option.value && option.value !== '-1'));

  if (options.length > 0) {
    await specialistSelect.selectOption(options[0].value);
    await page.waitForFunction(() => {
      const serviceField = document.forms.EctConsultationFormRequest2Form && document.forms.EctConsultationFormRequest2Form.service;
      return serviceField && serviceField.value && serviceField.value !== '0';
    }, { timeout: 15000 });
  }

  await page.locator('textarea[name="appointmentNotes"]').fill(`Child psychiatry render verification ${Date.now()}`);
}

async function attachAndPreviewConsultationEform(page, fdid) {
  await page.locator('#attachDocumentPanelBtn').click();
  await page.locator(`#eFormNo${fdid}`).waitFor({ state: 'visible', timeout: 15000 });
  const eformEntry = page.locator(`#eFormNo${fdid}`).locator('xpath=ancestor::li[1]');

  const previewResponsePromise = page.waitForResponse((response) => response.url().includes('/previewDocs?method=renderEFormPDF') && response.request().method() === 'GET', { timeout: 30000 });
  await eformEntry.locator('button.preview-button').click();
  const response = await previewResponsePromise;

  await eformEntry.locator(`input[type="checkbox"][value="${fdid}"]`).check();
  const screenshot = buildArtifactPath(screenshotDir, 'child-psychiatry-consultation-attachment');
  await page.screenshot({ path: screenshot, fullPage: true });

  return { status: response.status(), url: response.url(), screenshot };
}

async function openFaxPreviewPage(context, fdid, recorder) {
  const page = await context.newPage();
  await page.addInitScript(() => {
    window.close = () => {
      window.__playwrightCloseIntercepted = true;
    };
  });
  wirePage(page, 'fax-preview', recorder);
  await gotoApp(page, config.baseUrl, `/eform/efmshowform_data?fdid=${encodeURIComponent(fdid)}`);
  await waitQuiet(page);

  const warmupPdf = await generatePdfFromSurface(page, 'child-psychiatry-fax-warmup');
  const referencePdf = await generatePdfFromSurface(page, 'child-psychiatry-fax-reference');

  const faxPagePromise = page.waitForNavigation({
    url: /\/fax\/faxAction\?method=prepareFax/i,
    timeout: 30000,
  }).catch(() => null);

  await page.locator('#remoteFaxButton').click();
  const faxPageResponse = await faxPagePromise;
  await waitQuiet(page);
  assert(page.url().includes('/fax/faxAction?method=prepareFax'), `Fax preview page did not open as expected: ${page.url()}`);

  await waitForFaxPreviewElement(page);

  const previewObject = page.locator('#previewPDF');
  const previewObjectCount = await previewObject.count();
  const imagePreviewLocators = page.locator('img[src*="/fax/faxAction?method=getPreview"]');
  const imagePreviewCount = await imagePreviewLocators.count();

  let previewData = null;
  let previewStatus = null;
  let previewBytes = null;
  let previewContentType = '';
  let previewMode = null;
  let previewImagePaths = [];

  if (previewObjectCount > 0) {
    await previewObject.waitFor({ state: 'visible', timeout: 15000 });
    previewData = await previewObject.getAttribute('data');
    assert(previewData && previewData.includes('/fax/faxAction?method=getPreview'), `Fax preview object did not point at getPreview: ${previewData}`);

    const previewFetchUrl = sameOriginUrl(previewData, 'fax preview PDF object URL');
    const previewFetch = await page.evaluate(async (url) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- URL is prevalidated against the configured app origin and rechecked in-page before fetch
      const previewUrl = new URL(url, window.location.href);
      if (previewUrl.origin !== window.location.origin) {
        throw new Error(`Refusing to fetch cross-origin fax preview object: ${url}`);
      }
      const response = await fetch(previewUrl.href, { credentials: 'same-origin' });
      const bytes = await response.arrayBuffer();
      return {
        status: response.status,
        byteLength: bytes.byteLength,
        contentType: response.headers.get('content-type') || '',
      };
    }, previewFetchUrl);
    previewStatus = previewFetch.status;
    previewBytes = previewFetch.byteLength;
    previewContentType = previewFetch.contentType;
    previewMode = 'pdf-object';
  } else {
    assert(imagePreviewCount > 0, 'Fax preview page did not render a PDF object or image preview');
    const imagePreviewResponses = recorder.requestLog.filter((entry) => entry.label === 'fax-preview' && entry.url.includes('/fax/faxAction?method=getPreview') && entry.url.includes('showAs=image') && entry.method === 'GET');
    assert(imagePreviewResponses.length > 0, 'Fax preview image requests were not captured');

    const previewImageUrls = sameOriginUrls(
      await imagePreviewLocators.evaluateAll((images) => images.map((img) => img.currentSrc || img.src).filter(Boolean)),
      'fax preview image URL',
    );
    assert(previewImageUrls.length > 0, 'Fax preview image URLs were not available in the DOM');

    const previewImages = await page.evaluate(async (urls) => Promise.all(urls.map(async (url) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- URLs are prevalidated against the configured app origin and rechecked in-page before fetch
      const previewUrl = new URL(url, window.location.href);
      if (previewUrl.origin !== window.location.origin) {
        throw new Error(`Refusing to fetch cross-origin fax preview image: ${url}`);
      }
      const response = await fetch(previewUrl.href, { credentials: 'same-origin' });
      const buffer = await response.arrayBuffer();
      const bytes = new Uint8Array(buffer);
      let binary = '';
      for (let index = 0; index < bytes.length; index += 1) {
        binary += String.fromCharCode(bytes[index]);
      }
      return {
        url,
        contentType: response.headers.get('content-type') || '',
        base64: btoa(binary),
      };
    })), previewImageUrls);

    previewImagePaths = previewImages.map((image, index) => {
      const outputPath = buildArtifactPath(screenshotDir, `child-psychiatry-fax-preview-page${index + 1}`);
      fs.writeFileSync(outputPath, Buffer.from(image.base64, 'base64'));
      return outputPath;
    });

    previewData = imagePreviewResponses[0].url;
    previewStatus = imagePreviewResponses[0].status;
    previewContentType = imagePreviewResponses[0].contentType;
    previewBytes = imagePreviewResponses.length;
    previewMode = 'image-pages';
  }

  const previewPdfHref = await page.locator('#previewPdfLink').getAttribute('href').catch(() => null);
  let previewPdfPath = null;
  let previewPdfSha256 = null;
  let previewPdfBytes = null;
  if (previewPdfHref) {
    const previewPdfUrl = sameOriginUrl(previewPdfHref, 'fax preview source PDF URL');
    const previewPdfBytesArray = await page.evaluate(async (href) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- href is prevalidated against the configured app origin and rechecked in-page before fetch
      const pdfUrl = new URL(href, window.location.href);
      if (pdfUrl.origin !== window.location.origin) {
        throw new Error(`Refusing to fetch cross-origin fax preview PDF: ${href}`);
      }
      const response = await fetch(pdfUrl.href, { credentials: 'same-origin' });
      const buffer = await response.arrayBuffer();
      return Array.from(new Uint8Array(buffer));
    }, previewPdfUrl);
    const previewPdfBuffer = Buffer.from(previewPdfBytesArray);
    previewPdfPath = buildArtifactPath(screenshotDir, 'child-psychiatry-fax-preview-source', '.pdf');
    fs.writeFileSync(previewPdfPath, previewPdfBuffer);
    previewPdfBytes = previewPdfBuffer.length;
    previewPdfSha256 = sha256(previewPdfBuffer);
  }

  const screenshot = buildArtifactPath(screenshotDir, 'child-psychiatry-fax-preview');
  await page.screenshot({ path: screenshot, fullPage: true });

  return {
    warmupPdf,
    referencePdf,
    prepareFaxStatus: faxPageResponse ? faxPageResponse.status() : null,
    previewMode,
    previewData,
    previewStatus,
    previewBytes,
    previewContentType,
    previewImagePaths,
    previewPdfHref,
    previewPdfPath,
    previewPdfBytes,
    previewPdfSha256,
    screenshot,
  };
}

(async () => {
  const recorder = createRecorder();
  const browser = await chromium.launch(getLaunchOptions(chromePath));
  const context = await browser.newContext({
    acceptDownloads: true,
    ignoreHTTPSErrors: true,
    viewport: { width: 1600, height: 2200 },
  });
  const comparePage = await context.newPage();

  const result = {
    formName,
    screenshotDir,
  };

  try {
    const landingPage = await login(context, config, recorder);
    await landingPage.close();

    const manager = await openManager(context, config, recorder, 'manager');
    const row = manager.locator('#eformTbl tbody tr', { hasText: formName }).first();
    await row.waitFor({ state: 'visible', timeout: 15000 });

    const onclick = await row.locator('a[onclick*="efmshowform_data?fid="]').first().getAttribute('onclick');
    const fidMatch = onclick && onclick.match(/fid=([^&'"]+)/);
    assert(fidMatch?.[1], `Could not extract fid for ${formName}`);
    result.fid = decodeURIComponent(fidMatch[1]);

    const managerPreview = await openManagerPreview(context, row, recorder);
    result.original = await extractSurfaceState(managerPreview, 'manager preview');
    result.originalScreenshots = await screenshotPages(managerPreview, 'child-psychiatry-original');
    result.originalImageArtifacts = await downloadBackgroundImages(managerPreview, result.original.bgImages, 'child-psychiatry-original-asset');
    await managerPreview.close();
    await manager.close();

    const addSurface = await openAddSurface(context, result.fid, recorder);
    result.addSurface = await extractSurfaceState(addSurface, 'add surface');
    result.addSurfaceScreenshots = await screenshotPages(addSurface, 'child-psychiatry-add-surface');
    result.fdid = await saveCurrentEform(addSurface);
    result.postSaveScreenshots = await screenshotPages(addSurface, 'child-psychiatry-post-save');
    await addSurface.close();

    const savedDirect = await openSavedDirect(context, result.fdid, recorder);
    result.savedDirect = await extractSurfaceState(savedDirect, 'saved direct');
    result.savedDirectScreenshots = await screenshotPages(savedDirect, 'child-psychiatry-saved-direct');
    result.pdf = await generatePdfFromSurface(savedDirect);
    await savedDirect.close();

    const patientListPopup = await openPatientListPopup(context, result.fdid, recorder);
    result.patientList = await extractSurfaceState(patientListPopup, 'patient list popup');
    result.patientListScreenshots = await screenshotPages(patientListPopup, 'child-psychiatry-patient-list');
    await patientListPopup.close();

    const consult = await openConsultationPage(context, recorder);
    await prepareConsultationForm(consult);
    result.consultation = await attachAndPreviewConsultationEform(consult, result.fdid);
    await consult.close();

    result.faxPreview = await openFaxPreviewPage(context, result.fdid, recorder);

    result.surfaceComparisons = {
      addSurface: await compareImageSeries(comparePage, result.originalScreenshots, result.addSurfaceScreenshots, 'add surface'),
      postSave: await compareImageSeries(comparePage, result.originalScreenshots, result.postSaveScreenshots, 'post-save'),
      savedDirect: await compareImageSeries(comparePage, result.originalScreenshots, result.savedDirectScreenshots, 'saved direct'),
      patientList: await compareImageSeries(comparePage, result.originalScreenshots, result.patientListScreenshots, 'patient list'),
    };
    if (result.faxPreview.previewImagePaths.length > 0) {
      result.faxPreview.surfaceComparisons = await compareImageSeries(comparePage, result.originalScreenshots, result.faxPreview.previewImagePaths, 'fax preview surface');
    }
    for (const [label, comparisons] of Object.entries(result.surfaceComparisons)) {
      assertComparisonWithinThreshold(comparisons, label, 0.002, 0.2);
    }
    if (result.faxPreview.surfaceComparisons) {
      assertComparisonWithinThreshold(result.faxPreview.surfaceComparisons, 'fax preview surface', 0.002, 0.2);
    }
    result.pdfVisualStability = await comparePdfFilesVisually(comparePage, result.pdf.pdfPath, result.faxPreview.referencePdf.pdfPath, 'saved-form pdf stability');
    if (result.faxPreview.previewPdfPath) {
      result.faxPreview.pdfVisualComparison = await comparePdfFilesVisually(comparePage, result.faxPreview.referencePdf.pdfPath, result.faxPreview.previewPdfPath, 'fax preview pdf');
    }

    for (const key of ['addSurface', 'savedDirect', 'patientList']) {
      const surface = result[key];
      assert(surface.pageIds.length === result.original.pageIds.length, `${key} page count ${surface.pageIds.length} did not match original ${result.original.pageIds.length}`);
      const originalImages = result.original.bgImages.map((img) => `${img.file}:${img.width}x${img.height}`);
      const surfaceImages = surface.bgImages.map((img) => `${img.file}:${img.width}x${img.height}`);
      assert(JSON.stringify(surfaceImages) === JSON.stringify(originalImages), `${key} background files/dimensions did not match original: ${JSON.stringify(surfaceImages)} vs ${JSON.stringify(originalImages)}`);
    }

    assert(result.pdf.pdfBytes > 10000, `Rendered PDF was unexpectedly small: ${result.pdf.pdfBytes} bytes`);
    assert(result.consultation.status === 200, `Consultation preview response was ${result.consultation.status}`);
    assert(result.faxPreview.prepareFaxStatus === null || result.faxPreview.prepareFaxStatus === 200, `Fax prepare preview response was ${result.faxPreview.prepareFaxStatus}`);
    assert(result.faxPreview.previewStatus === 200, `Fax preview response was ${result.faxPreview.previewStatus}`);
    assert(result.faxPreview.referencePdf.pdfBytes > 10000, `Fax reference PDF was unexpectedly small: ${result.faxPreview.referencePdf.pdfBytes} bytes`);
    assert(result.faxPreview.previewPdfSha256, 'Fax preview PDF hash was not captured');
    result.faxPreview.samePageReferencePdfBytesMatch = result.faxPreview.previewPdfBytes === result.faxPreview.referencePdf.pdfBytes;
    assert(result.faxPreview.previewPdfSha256 === result.faxPreview.referencePdf.pdfSha256,
      `Fax preview PDF hash ${result.faxPreview.previewPdfSha256} did not match reference ${result.faxPreview.referencePdf.pdfSha256}`);
    assertComparisonWithinThreshold(result.pdfVisualStability, 'saved-form pdf stability', 0.002, 0.2);
    if (result.faxPreview.pdfVisualComparison) {
      assertComparisonWithinThreshold(result.faxPreview.pdfVisualComparison, 'fax preview pdf', 0.0001, 0.01);
    }

    const labels = new Set(['manager-preview', 'add-surface', 'saved-direct', 'patient-list-popup', 'consultation', 'fax-preview']);
    const fatalBadResponses = recorder.badResponses.filter((entry) => labels.has(entry.label) && !isIgnorableLegacyFaxIssue(entry.url));
    const fatalConsoleIssues = recorder.consoleIssues.filter((entry) => labels.has(entry.label) && !isIgnorableLegacyFaxIssue(entry.text) && !isIgnorableLegacyFaxIssue((entry.location && entry.location.url) || ''));
    const fatalPageErrors = recorder.pageErrors.filter((entry) => labels.has(entry.label) && !isIgnorableLegacyFaxIssue(entry.text));
    result.ignoredHttp404s = recorder.badResponses.filter((entry) => labels.has(entry.label) && isIgnorableLegacyFaxIssue(entry.url));
    result.ignoredConsoleIssues = recorder.consoleIssues.filter((entry) => labels.has(entry.label) && (isIgnorableLegacyFaxIssue(entry.text) || isIgnorableLegacyFaxIssue((entry.location && entry.location.url) || '')));
    result.ignoredPageErrors = recorder.pageErrors.filter((entry) => labels.has(entry.label) && isIgnorableLegacyFaxIssue(entry.text));

    assert(fatalBadResponses.length === 0, `Unexpected HTTP errors: ${JSON.stringify(fatalBadResponses, null, 2)}`);
    assert(fatalConsoleIssues.length === 0, `Unexpected console errors: ${JSON.stringify(fatalConsoleIssues, null, 2)}`);
    assert(fatalPageErrors.length === 0, `Unexpected page errors: ${JSON.stringify(fatalPageErrors, null, 2)}`);

    console.log(JSON.stringify(result, null, 2));
  } finally {
    await comparePage.close().catch(() => {});
    await context.close().catch(() => {});
    await browser.close().catch(() => {});
  }
})().catch((error) => {
  console.error(error.stack || error.message);
  process.exit(1);
});
