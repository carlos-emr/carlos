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
 * Rich Text Letter attachment families end to end: each family the letter's Attach popup offers
 * (documents, labs, HRM reports, other eForms, encounter forms) is attached to its own saved
 * letter, must SHOW on the saved letter (the "Attached Files" panel, the hidden attachment input
 * the toolbar re-submits, the toolbar badge) and must APPEAR in the PDF from both download paths:
 * the toolbar's Download (saveAndDownloadEForm) and printControl.js's PDF button (print=true,
 * the legacy alias AddEForm2Action folds into the same download). Appearing is proven by page
 * count: the merged PDF must carry more pages than the same letter downloaded before the
 * attachment, and both paths must agree. When poppler's pdftotext is on PATH the letter's typed
 * marker (and, for labs/HRM/eForms, a family-specific text) is also required in the PDF text.
 *
 * Page counts are read without any PDF library: the merged file's page dictionaries live inside
 * FlateDecode object streams after the PDFBox merge, so the script inflates those with zlib and
 * counts /Type /Page entries (preferring the root /Pages /Count when it can find it).
 *
 * Prerequisites (see docs/ui-tests/eform-pdf-render-smoke-test.md and
 * docs/ui-tests/deb-install-validation.md):
 *   - the demo dataset (the RTL eForm and demographic 1's documents, labs, eForms and the
 *     PDF-ready "Annual" encounter form come with it);
 *   - the demo DOCUMENT FILES in the document store (the rows alone make every document
 *     render fail with "could not be converted into a PDF") and the fictitious HRM report
 *     (.devcontainer/db/db_data/hrm/demo-hrm-diagnostic-imaging.xml) that demo-hrm-report.sql
 *     points one demographic-1 HRMDocument row at — without it the popup lists no HRM
 *     documents at all. The devcontainer seed and `carlos-ctl demo-data` both place them.
 * A family with nothing to attach is reported as SKIP and does not fail the run unless
 * RTL_REQUIRE_ALL_FAMILIES=1.
 *
 * Environment: BASE_URL, CHROME_PATH, TEST_USER/TEST_PASSWORD/TEST_PIN, RTL_DEMOGRAPHIC_NO,
 * RTL_FORM_NAME, RTL_SCREENSHOT_DIR, RTL_REQUIRE_ALL_FAMILIES, RTL_HRM_TEXT_MARKER.
 */
const fs = require('fs');
const zlib = require('zlib');
const { spawnSync } = require('child_process');
const { chromium } = require('playwright');
const {
  assert,
  buildArtifactPath,
  buildFailureDetails,
  createRecorder,
  findLibraryEform,
  getLaunchOptions,
  gotoApp,
  invokeFetchAttached,
  login,
  openAddEform,
  openAttachPopup,
  openManager,
  saveCurrentEform,
  screenshot,
  validateBaseUrl,
  waitForPopupReady,
  wirePage,
} = require('./eform-local-playwright-utils');

const config = {
  baseUrl: validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos'),
  chromePath: process.env.CHROME_PATH || '',
  testUser: process.env.TEST_USER || 'carlosdoc',
  testPassword: process.env.TEST_PASSWORD || 'carlos2026',
  testPin: process.env.TEST_PIN || '2026',
  demographicNo: process.env.RTL_DEMOGRAPHIC_NO || '1',
  screenshotDir: process.env.RTL_SCREENSHOT_DIR || '/tmp',
  formName: process.env.RTL_FORM_NAME || 'Rich Text Letter',
  requireAllFamilies: process.env.RTL_REQUIRE_ALL_FAMILIES === '1',
  hrmTextMarker: process.env.RTL_HRM_TEXT_MARKER || 'SEED-HRM-ATTACHMENT-MARKER',
};

// One entry per attachment family the popup (attachEform.jsp) and the packet renderer
// (DocumentAttachmentManagerImpl.renderEFormPacket) know about. `panelPrefix` is the label
// displayAttachedFiles.jsp prints; `inputName` is the checkbox/hidden-input name the save re-posts.
const FAMILIES = [
  { key: 'document', label: 'Document (eDoc)', inputName: 'docNo', panelPrefix: 'Doc' },
  { key: 'lab', label: 'Lab result', inputName: 'labNo', panelPrefix: 'Lab', textFromPopupLabel: true },
  { key: 'hrm', label: 'HRM report', inputName: 'hrmNo', panelPrefix: 'HRM', textMarker: config.hrmTextMarker },
  { key: 'eform', label: 'Other eForm', inputName: 'eFormNo', panelPrefix: 'EForm', attachPreviousLetter: true },
  { key: 'form', label: 'Encounter form', inputName: 'formNo', panelPrefix: 'Form' },
];

const results = [];
function record(family, name, ok, detail) {
  results.push({ family, name, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'} [${family}] ${name}${detail ? ' :: ' + detail : ''}`);
}
function skip(family, reason) {
  results.push({ family, name: 'skipped', ok: !config.requireAllFamilies, skipped: true, detail: reason });
  console.log(`${config.requireAllFamilies ? 'FAIL' : 'SKIP'} [${family}] ${reason}`);
}

/* ---------- PDF inspection (no PDF library) ---------- */

// Extracts every dictionary-bearing text fragment of a PDF: the raw file plus the inflated body
// of each FlateDecode object stream (where PDFBox parks page dictionaries after a merge).
function pdfTextFragments(buffer) {
  const raw = buffer.toString('latin1');
  const fragments = [raw];
  const objRe = /(\d+)\s+(\d+)\s+obj\s*/g;
  let match;
  while ((match = objRe.exec(raw)) !== null) {
    const dictStart = raw.indexOf('<<', match.index + match[0].length);
    if (dictStart < 0 || dictStart - (match.index + match[0].length) > 4) {
      continue;
    }
    let depth = 0;
    let i = dictStart;
    for (; i < raw.length - 1; i += 1) {
      if (raw[i] === '<' && raw[i + 1] === '<') { depth += 1; i += 1; } else if (raw[i] === '>' && raw[i + 1] === '>') { depth -= 1; i += 1; if (depth === 0) { i += 1; break; } }
    }
    const dict = raw.slice(dictStart, i);
    if (!/\/Type\s*\/ObjStm/.test(dict) || !/\/FlateDecode/.test(dict)) {
      continue;
    }
    const streamKeyword = raw.indexOf('stream', i);
    if (streamKeyword < 0 || streamKeyword - i > 4) {
      continue;
    }
    let dataStart = streamKeyword + 'stream'.length;
    if (raw[dataStart] === '\r') dataStart += 1;
    if (raw[dataStart] === '\n') dataStart += 1;
    const dataEnd = raw.indexOf('endstream', dataStart);
    if (dataEnd < 0) {
      continue;
    }
    try {
      fragments.push(zlib.inflateSync(Buffer.from(raw.slice(dataStart, dataEnd), 'latin1')).toString('latin1'));
    } catch (error) {
      // A stream that will not inflate cleanly is skipped; the page count then falls back to
      // whatever is visible, which the caller cross-checks against the letter-only baseline.
    }
  }
  return fragments;
}

function countPdfPages(buffer) {
  const fragments = pdfTextFragments(buffer);
  let pageObjects = 0;
  let rootCount = null;
  for (const fragment of fragments) {
    pageObjects += (fragment.match(/\/Type\s*\/Page(?![A-Za-z])/g) || []).length;
    const dictRe = /<<(?:[^<>]|<<[^<>]*>>)*>>/g;
    let dict;
    while ((dict = dictRe.exec(fragment)) !== null) {
      if (/\/Type\s*\/Pages(?![A-Za-z])/.test(dict[0]) && !/\/Parent/.test(dict[0])) {
        const count = dict[0].match(/\/Count\s+(\d+)/);
        if (count) {
          rootCount = Math.max(rootCount || 0, Number(count[1]));
        }
      }
    }
  }
  return rootCount || pageObjects;
}

const pdftotextAvailable = spawnSync('pdftotext', ['-v']).error == null;
function pdfText(file) {
  if (!pdftotextAvailable) {
    return null;
  }
  const run = spawnSync('pdftotext', ['-layout', file, '-']);
  return run.status === 0 ? run.stdout.toString('utf8') : null;
}

/* ---------- page helpers ---------- */

async function waitForEditor(page) {
  await page.locator('#remotePrintButton').waitFor({ state: 'attached', timeout: 30000 });
  await page.locator('iframe#edit').waitFor({ state: 'attached', timeout: 30000 });
  await page.waitForFunction(() => {
    const sel = document.getElementById('template');
    return sel && !Array.from(sel.options).some((o) => o.textContent.trim() === 'loading...');
  }, null, { timeout: 30000 });
  await page.waitForFunction(() => {
    const f = document.getElementById('edit');
    try { return f && f.contentWindow.document.readyState === 'complete' && f.contentWindow.document.body != null; } catch (e) { return false; }
  }, null, { timeout: 30000 });
  await page.waitForTimeout(500);
}

async function typeIntoLetter(page, text) {
  const frame = page.frames().find((fr) => fr.parentFrame() === page.mainFrame());
  assert(frame, 'editor iframe not found');
  await frame.locator('body').click();
  await page.keyboard.press('End');
  await page.keyboard.type(text);
}

async function downloadPdf(page, locator, label) {
  const file = buildArtifactPath(config.screenshotDir, `rtl-attachment-pdf-${label}-${Date.now()}`, '.pdf');
  const downloadPromise = page.waitForEvent('download', { timeout: 120000 });
  const responsePromise = page.waitForResponse(
    (response) => response.url().includes('/eform/addEForm') && response.request().method() === 'POST',
    { timeout: 120000 },
  );
  await locator.click();
  const response = await responsePromise;
  const download = await downloadPromise;
  await download.saveAs(file);
  try {
    const bytes = fs.readFileSync(file);
    assert(bytes.subarray(0, 5).toString('utf8') === '%PDF-', `${label}: payload was not a PDF`);
    assert(bytes.toString('latin1').includes('%%EOF'), `${label}: PDF is truncated (missing %%EOF)`);
    return { status: response.status(), size: bytes.length, pages: countPdfPages(bytes), text: pdfText(file) };
  } finally {
    // The PDF is evidence for this run only; never leave it behind, assertions passed or not.
    fs.rmSync(file, { force: true });
  }
}

// Both download paths re-render the saved view; wait for it to settle before the next click.
async function settleSavedView(page) {
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await waitForEditor(page);
}

async function openSavedView(context, recorder, fdid, label) {
  const view = await context.newPage();
  wirePage(view, label, recorder);
  await gotoApp(view, config.baseUrl, `/eform/efmshowform_data?fdid=${encodeURIComponent(fdid)}`);
  await settleSavedView(view);
  return view;
}

async function createLetter(context, recorder, fid, marker, label) {
  const addPage = await openAddEform(context, config, recorder, fid, config.demographicNo, label);
  await waitForEditor(addPage);
  await typeIntoLetter(addPage, `${marker} attachment family check`);
  const fdid = await saveCurrentEform(addPage, `RTL attachment ${label} ${Date.now()}`);
  await addPage.close();
  return fdid;
}

/* ---------- one family ---------- */

async function checkFamily(context, recorder, fid, family, previousLetter) {
  const marker = `LETTERMARK-${family.key.toUpperCase()}-${Date.now()}`;
  // Every page this family opens; closed in the finally so a failure part-way does not leave
  // orphaned pages behind for the next family.
  const openPages = new Set();
  const track = (page) => { openPages.add(page); return page; };
  const closeTracked = async (page) => { openPages.delete(page); await page.close().catch(() => {}); };
  try {
    let fdid = await createLetter(context, recorder, fid, marker, `rtl-attach-${family.key}`);

    // Baseline: the letter alone, before anything is attached.
    const view = track(await openSavedView(context, recorder, fdid, `rtl-attach-${family.key}-view`));
    const baseline = await downloadPdf(view, view.locator('#remoteDownloadButton'), `${family.key}-baseline`);
    record(family.key, 'letter-only baseline PDF downloads', baseline.pages >= 1, `pages=${baseline.pages} size=${baseline.size}`);
    await settleSavedView(view);
    // Every download first saves the letter, and a save whose HTML changed is a NEW eForm instance:
    // the re-rendered page carries the new fdid and the paperclip attaches to that one. Follow it.
    fdid = await view.locator('#fdid').inputValue();

    // Attach one item of this family through the letter's own paperclip popup.
    const popup = track(await openAttachPopup(view, context));
    await waitForPopupReady(popup, recorder, `rtl-attach-${family.key}-popup`);
    let candidate = popup.locator(`input[name="${family.inputName}"]`).first();
    if (family.attachPreviousLetter && previousLetter) {
      const previous = popup.locator(`input[name="${family.inputName}"][value="${previousLetter.fdid}"]`);
      if (await previous.count()) {
        candidate = previous;
      }
    }
    if ((await candidate.count()) === 0) {
      skip(family.key, `popup offers no ${family.label} for demographic ${config.demographicNo}`);
      return { fdid, marker };
    }
    const value = await candidate.getAttribute('value');
    // The popup's label for the item (a document title, a lab test name, ...). On a real patient
    // that is clinical data: it is used for the PDF text assertion below but never logged.
    const popupLabel = (await candidate.evaluate((input) => {
      const ids = (input.getAttribute('aria-labelledby') || '').split(/\s+/).filter(Boolean);
      const text = ids.map((id) => (document.getElementById(id) || {}).textContent || '').join(' ');
      return (text || (input.parentElement ? input.parentElement.textContent : '')).replace(/\s+/g, ' ').trim();
    })).slice(0, 80);
    await candidate.check();
    await Promise.all([
      popup.waitForLoadState('domcontentloaded').catch(() => {}),
      popup.locator('input[type="submit"][value="Attach Selected"]').click(),
    ]);
    await popup.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    const popupText = (await popup.locator('body').innerText().catch(() => '')).trim();
    record(family.key, 'paperclip attach submit returns ok', popupText === 'ok',
      `${family.inputName}=${value} (label ${popupLabel.length} chars) -> ${popupText === 'ok' ? 'ok' : `${popupText.length} chars`}`);
    await closeTracked(popup);
    await closeTracked(view);

    // The attachment must SHOW on a fresh load of the saved letter.
    const saved = track(await openSavedView(context, recorder, fdid, `rtl-attach-${family.key}-saved`));
    await saved.waitForFunction(() => {
      const t = document.getElementById('tdAttachedDocs');
      return t && t.innerText.trim().length > 0;
    }, null, { timeout: 30000 }).catch(() => {});
    const panelText = (await saved.locator('#tdAttachedDocs').innerText().catch(() => '')).trim();
    // Plain string matching (no RegExp built from page values): "Doc #3" must not match "Doc #31".
    // The panel prints "<Type> #<id>" lines only, so counting those lines is a safe detail to log.
    const panelEntries = (text) => (text || '').split(/\r?\n/).map((line) => line.trim()).filter((line) => /^[A-Za-z]+ #\d+$/.test(line));
    const panelEntry = { test: (text) => panelEntries(text).includes(`${family.panelPrefix} #${value}`) };
    record(family.key, 'Attached Files panel lists it', panelEntry.test(panelText), `entries=${panelEntries(panelText).length}`);
    const hidden = await saved.locator(`input[name="${family.inputName}"]`).evaluateAll((els) => els.map((e) => e.value));
    record(family.key, 'saved view re-submits it as a hidden input', hidden.includes(value), `${family.inputName}=${JSON.stringify(hidden)}`);
    const badge = (await saved.locator('#remoteTotalAttachments').innerText().catch(() => '')).trim();
    record(family.key, 'toolbar Attach badge counts it', Number(badge) >= 1, `badge=${badge}`);
    await screenshot(saved, config.screenshotDir, `rtl-attachment-pdf-${family.key}-saved`);

    // The attachment must APPEAR in the PDF from both download paths.
    const toolbar = await downloadPdf(saved, saved.locator('#remoteDownloadButton'), `${family.key}-toolbar`);
    record(family.key, 'toolbar Download PDF gains the attachment pages',
      toolbar.status === 200 && toolbar.pages > baseline.pages,
      `pages=${toolbar.pages} (baseline ${baseline.pages}) size=${toolbar.size}`);
    await settleSavedView(saved);
    const printAlias = await downloadPdf(saved, saved.locator('input[name="pdfButton"]'), `${family.key}-print-alias`);
    record(family.key, 'form PDF button (print=true alias) PDF matches the toolbar PDF',
      printAlias.status === 200 && printAlias.pages === toolbar.pages,
      `pages=${printAlias.pages} size=${printAlias.size}`);

    if (toolbar.text != null) {
      // [needle, description, loggable]: the synthetic markers this run typed are safe to print;
      // anything read from the patient's record is described by length only.
      const expected = [[marker, 'letter marker', true]];
      if (family.textMarker) {
        expected.push([family.textMarker, `${family.label} marker`, true]);
      }
      if (family.textFromPopupLabel && popupLabel) {
        // The lab's test name as the popup showed it (e.g. "URINALYSIS"), without the date.
        expected.push([popupLabel.replace(/\s+\d{4}-\d{2}-\d{2}.*$/, '').trim(), `${family.label} name`, false]);
      }
      if (family.attachPreviousLetter && previousLetter && value === String(previousLetter.fdid)) {
        expected.push([previousLetter.marker, 'attached letter marker', true]);
      }
      for (const [needle, what, loggable] of expected) {
        record(family.key, `PDF text contains the ${what}`,
          toolbar.text.includes(needle) && printAlias.text != null && printAlias.text.includes(needle),
          loggable ? needle : `${needle.length} chars`);
      }
      // The packet is letter first, attachments after: every family-specific text must come after
      // the letter's own marker.
      for (const [needle, what] of expected.slice(1)) {
        record(family.key, `letter page precedes the ${what}`,
          toolbar.text.indexOf(marker) >= 0 && toolbar.text.indexOf(marker) < toolbar.text.indexOf(needle));
      }
    }

    // The two re-saves the downloads performed must not have detached it.
    await settleSavedView(saved);
    const fetched = await invokeFetchAttached(saved);
    record(family.key, 'still attached after both downloads', panelEntry.test(fetched.text || ''), `entries=${panelEntries(fetched.text).length}`);
    // The downloads saved newer instances; hand the current one to the eForm family so it attaches
    // a letter the popup still lists.
    const currentFdid = await saved.locator('#fdid').inputValue().catch(() => fdid);
    return { fdid: currentFdid, marker };
  } finally {
    for (const page of openPages) {
      await page.close().catch(() => {});
    }
    openPages.clear();
  }
}

/* ---------- main ---------- */

(async () => {
  const recorder = createRecorder();
  const browser = await chromium.launch(getLaunchOptions(config.chromePath));
  try {
    const context = await browser.newContext({ acceptDownloads: true, ignoreHTTPSErrors: true, viewport: { width: 1440, height: 1100 } });
    const landingPage = await login(context, config, recorder);
    await landingPage.close();
    const managerPage = await openManager(context, config, recorder, 'rtl-manager');
    const { fid } = await findLibraryEform(managerPage, config.formName);
    await managerPage.close();
    console.log(`pdftotext ${pdftotextAvailable ? 'found: text assertions enabled' : 'not on PATH: page-count assertions only'}`);

    let previousLetter = null;
    for (const family of FAMILIES) {
      try {
        previousLetter = await checkFamily(context, recorder, fid, family, previousLetter);
      } catch (error) {
        record(family.key, 'family check completed', false, String(error && error.stack || error));
      }
    }
    await context.close();
  } catch (error) {
    record('run', 'completed without exception', false, String(error && error.stack || error));
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2).slice(0, 6000));
  } finally {
    await browser.close();
  }
  const failed = results.filter((r) => !r.ok).length;
  const skipped = results.filter((r) => r.skipped).length;
  console.log(`\n${results.length - failed}/${results.length} checks passed${skipped ? ` (${skipped} family skipped)` : ''}`);
  process.exit(failed ? 1 : 0);
})();
