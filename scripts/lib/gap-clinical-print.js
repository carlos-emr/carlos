/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Shared driver for the E-Chart print dialog (gap-clinical checks): opens the dialog from the chart's
 * own printer icon, picks the scope with real clicks, presses Print and returns the downloaded PDF's
 * text. The dialog is the one newEncounterLayout.jsp renders (#printOps); the print is the
 * CaseManagementEntry method=print form post answered with a PDF download.
 */
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const h = require('./playwright-harness');
const { pdfText } = require('./stored-pdf-documents');
const { watchChartPrintDownload } = require('./chart-print-download');

const FLAG_ICONS = {
  printCPP: '#imgPrintCPP', printRx: '#imgPrintRx', printLabs: '#imgPrintLabs',
  printPreventions: '#imgPrintPreventions', printAllergies: '#imgPrintAllergies',
};

/** Opens the print dialog with the chart's printer button, as a clinician does. */
async function openPrintDialog(chart) {
  await chart.locator('#imgPrintEncounter').click();
  await chart.locator('#printOps').waitFor({ state: 'visible' });
}

/** The state of the five section flags the dialog carries (hidden inputs the icons toggle). */
async function flagState(chart) {
  return chart.evaluate(ids => Object.fromEntries(ids.map(id => [id, document.getElementById(id).value])),
    Object.keys(FLAG_ICONS));
}

/** Clicks each section icon until the flag set equals `wanted` (icons toggle). */
async function setFlags(chart, wanted) {
  const state = await flagState(chart);
  for (const [flag, icon] of Object.entries(FLAG_ICONS)) {
    if ((state[flag] === 'true') !== wanted.includes(flag)) await chart.locator(icon).click();
  }
  const after = await flagState(chart);
  for (const flag of Object.keys(FLAG_ICONS)) {
    h.assert((after[flag] === 'true') === wanted.includes(flag), `The ${flag} icon did not toggle the section`);
  }
}

/** Presses Print (or runs `trigger`, e.g. the dialog's Today link, which prints at once) and returns {response, text, bytes} for the PDF the browser downloads. */
async function pressPrint(chart, scratch, trigger = () => chart.locator('#printOp').click(), verifyPdf = () => {}) {
  const context = chart.context();
  const endpoint = new URL(await chart.locator('#caseManagementEntryForm').evaluate(form => form.action));
  h.assert(endpoint.origin === new URL(chart.url()).origin && /\/CaseManagementEntry$/.test(endpoint.pathname),
    'The chart print form does not target its own note action');
  let resolveResponse;
  let responseTimer;
  let responseSettled = false;
  const printResponse = new Promise(resolve => { resolveResponse = resolve; });
  const finishResponse = response => {
    if (responseSettled) return;
    responseSettled = true;
    clearTimeout(responseTimer);
    context.off('response', receivedResponse);
    resolveResponse(response);
  };
  const receivedResponse = response => {
    const address = new URL(response.url());
    if (address.origin === endpoint.origin && address.pathname === endpoint.pathname
      && response.request().method() === 'POST'
      && new URLSearchParams(response.request().postData() || '').get('method') === 'print') finishResponse(response);
  };
  context.on('response', receivedResponse);
  responseTimer = setTimeout(() => finishResponse(null), 40000);
  // Register on the context before the native click: the PDF can belong to the
  // opener or be the first response/download of a transient print popup.
  const downloaded = watchChartPrintDownload(context, endpoint.href);
  let file;
  try {
    await trigger();
    const response = await printResponse;
    h.assert(response, 'The native chart print POST did not return a response');
    h.assert(response.status() === 200, `Print answered HTTP ${response.status()}`);
    const download = await downloaded.promise;
    h.assert(download, 'The native chart print did not produce a PDF download');
    h.assert(download.url() === response.url(), 'The downloaded PDF belongs to another print response');
    h.assert(await download.failure() === null, 'The native chart PDF download failed');
    file = path.join(scratch, `print-${Date.now()}.pdf`);
    await download.saveAs(file);
    const bytes = fs.readFileSync(file);
    h.assert(bytes.subarray(0, 5).toString('latin1') === '%PDF-', 'The print is not a PDF');
    // A truncated download still starts with %PDF- and pdftotext may extract a prefix of it; the
    // trailer must be complete before the text is trusted.
    h.assert(/%%EOF\s*$/.test(bytes.subarray(-1024).toString('latin1')), 'The print is a truncated PDF (no %%EOF in the final 1 KiB)');
    const text = pdfText(file);
    verifyPdf(file);
    // printNotes() holds the note lock for 3 s after the submit; let the page settle before the next click.
    await chart.waitForTimeout(3200);
    return { response, text, bytes: bytes.length };
  } finally {
    finishResponse(null);
    downloaded.cancel();
    if (file) fs.rmSync(file, { force: true });
  }
}

function scratchDir() {
  return fs.mkdtempSync(path.join(os.tmpdir(), 'gap-clinical-print-'));
}

module.exports = { FLAG_ICONS, flagState, openPrintDialog, pressPrint, scratchDir, setFlags };
