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
async function pressPrint(chart, scratch, trigger = () => chart.locator('#printOp').click()) {
  const printResponse = chart.waitForResponse(
    r => /\/CaseManagementEntry$/.test(new URL(r.url()).pathname) && r.request().method() === 'POST'
      && /(?:^|&)method=print(?:&|$)/.test(r.request().postData() || ''), { timeout: 40000 });
  const download = chart.waitForEvent('download', { timeout: 40000 });
  download.catch(() => {});
  printResponse.catch(() => {});
  await trigger();
  const response = await printResponse;
  h.assert(response.status() === 200, `Print answered HTTP ${response.status()}`);
  const file = path.join(scratch, `print-${Date.now()}.pdf`);
  await (await download).saveAs(file);
  const bytes = fs.readFileSync(file);
  h.assert(bytes.subarray(0, 5).toString('latin1') === '%PDF-', 'The print is not a PDF');
  const text = pdfText(file);
  fs.rmSync(file, { force: true });
  // printNotes() holds the note lock for 3 s after the submit; let the page settle before the next click.
  await chart.waitForTimeout(3200);
  return { response, text, bytes: bytes.length };
}

function scratchDir() {
  return fs.mkdtempSync(path.join(os.tmpdir(), 'gap-clinical-print-'));
}

module.exports = { FLAG_ICONS, flagState, openPrintDialog, pressPrint, scratchDir, setFlags };
