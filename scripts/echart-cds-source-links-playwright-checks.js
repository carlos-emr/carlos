#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
/*
 * The encounter chart shows CDS source links only for a patient with imported CDS data, and never
 * above the notes.
 *
 * The chart once printed "Clinical notes: CDS source fields — Additional clinical categories: CDS
 * source fields" and a note about CDS creation times above the CPP boxes of every patient, with the
 * dash arriving as mojibake. This opens the real chart for an owned patient no import touched and
 * asserts none of that wording is anywhere on it, then gives that patient synthetic preserved
 * source fields and asserts each short link appears in the notes toolbar (beside the Filter button)
 * for its own category only, with the timing note as hover text, while neither the header, the CPP
 * boxes nor the notes below the toolbar move.
 *
 * The source-field rows are seeded directly: the import that would write them needs the qualified
 * CDS tier (scripts/demographic-cds-import-playwright-checks.js covers the real import). They carry
 * no parent ledger rows, so the insert suspends foreign-key checks for its own statement; cleanup
 * removes them by their exact ids before the owned patient is released.
 */
const {createHash} = require('node:crypto');
const h = require('./lib/playwright-harness');
const {runWorkflow} = require('./lib/workflow-session');
const {reloadChart} = require('./chart-session-state-two-window-isolation-playwright-checks');

const CDS_WORDING = '.cds-source-fields-link, .cds-chart-source-links, .cds-note-source-timing';
const SOURCE_RUN = '00000000-0000-4000-8000-0000000000c5';

/**
 * What the centre column holds above the CPP boxes, and where the page's landmarks sit: the boxes
 * within their column, the header's height, and the notes list below the toolbar.
 */
async function aboveCppBoxes(chart) {
  await chart.locator('#cppBoxes').waitFor({state: 'attached'});
  await chart.locator('#notesFilterToggle').waitFor({state: 'visible'});
  return chart.evaluate(() => {
    const boxes = document.getElementById('cppBoxes');
    const column = boxes.parentElement;
    const nodes = [...column.childNodes];
    const toggle = document.getElementById('notesFilterToggle');
    const notes = document.getElementById('encMainDiv');
    return {
      text: nodes.slice(0, nodes.indexOf(boxes)).map(node => (node.textContent || '').trim()).filter(Boolean).join(' '),
      offset: Math.round(boxes.getBoundingClientRect().top - column.getBoundingClientRect().top),
      header: Math.round(document.getElementById('header-bottom-row').parentElement.getBoundingClientRect().height),
      // The notes list relative to the toolbar row: unchanged when the links add no height.
      notesBelowToolbar: notes ? Math.round(notes.getBoundingClientRect().top - toggle.getBoundingClientRect().top) : null,
    };
  });
}

function sourceLinks(chart, category) {
  return chart.locator(`.cds-source-fields-link[href*="category=${category}"]`);
}

async function workflow(s) {
  const {sql, marker, patient} = s;
  const fieldId = category => createHash('sha256').update(`${marker}:${category}`).digest('hex');
  const key = label => createHash('sha256').update(`${marker}:${label}:key`).digest('hex');
  const seeded = [];
  const seed = category => {
    const id = fieldId(category);
    seeded.push(id);
    sql.execute(`SET FOREIGN_KEY_CHECKS=0;
      INSERT INTO cds_import_field (source_namespace,id,run_id,patient_key,unit_key,demographic_no,category,
        source_path,occurrence,value_type,original_value,normalized_value,payload_offset,payload_length)
      VALUES ('cds',${h.sqlString(id)},${h.sqlString(SOURCE_RUN)},${h.sqlString(key('patient'))},${h.sqlString(key(category))},
        ${patient},${h.sqlString(category)},${h.sqlString(`/OmdCds/PatientRecord/${category}[1]`)},1,'TEXT',
        ${h.sqlString(`${marker} synthetic source value`)},${h.sqlString(`${marker} synthetic source value`)},0,0);
      SET FOREIGN_KEY_CHECKS=1`);
  };
  s.cleanup(() => {
    if (!seeded.length) return;
    const ids = seeded.map(h.sqlString).join(',');
    sql.execute(`DELETE FROM cds_import_field WHERE demographic_no=${patient} AND id IN (${ids})`);
    h.assert(sql.value(`SELECT COUNT(*) FROM cds_import_field WHERE id IN (${ids})`) === '0',
      'Synthetic CDS source fields were retained');
  });
  h.assert(sql.value(`SELECT COUNT(*) FROM cds_import_field WHERE demographic_no=${patient}`) === '0'
    && sql.value(`SELECT COUNT(*) FROM cds_import_patient WHERE target_demographic_no=${patient}`) === '0',
  'The owned patient already carries CDS import rows');

  const chart = await s.chart();
  const toolbarLinks = category => chart.locator('#notCPP').locator(sourceLinks(chart, category));
  let ordinary;
  await s.step('a patient no import touched has no CDS wording anywhere on the chart', async () => {
    ordinary = await aboveCppBoxes(chart);
    h.assert(await chart.locator(CDS_WORDING).count() === 0,
      'The chart of a patient without imported data still renders CDS source links or the CDS timing note');
    h.assert(ordinary.text === '', `The centre column prints text above the CPP boxes: ${ordinary.text.slice(0, 120)}`);
    h.assert(!/CDS source|For CDS imports/.test(await chart.locator('body').innerText()),
      'The chart of a patient without imported data mentions CDS source fields');
  });

  const assertNotesColumnUntouched = async label => {
    const above = await aboveCppBoxes(chart);
    h.assert(above.text === '' && above.offset === ordinary.offset,
      `${label}: the CDS links moved the CPP boxes or printed above them (${above.text.slice(0, 120)})`);
    h.assert(above.header === ordinary.header && above.notesBelowToolbar === ordinary.notesBelowToolbar,
      `${label}: the CDS links lengthened the header or pushed the notes down `
        + `(header ${ordinary.header}->${above.header}, notes ${ordinary.notesBelowToolbar}->${above.notesBelowToolbar})`);
    h.assert(await chart.locator('#header-bottom-row .cds-source-fields-link').count() === 0,
      `${label}: a CDS source link is in the chart header`);
    h.assert(await chart.locator('.cds-note-source-timing').count() === 0,
      `${label}: the CDS timing note is printed on the chart`);
  };

  await s.step('imported clinical notes add one toolbar link with the timing note as hover text', async () => {
    seed('ClinicalNotes');
    await reloadChart(s, chart, 'chart with CDS clinical-note source fields');
    const link = toolbarLinks('ClinicalNotes');
    await link.first().waitFor({state: 'visible'});
    h.assert(await sourceLinks(chart, 'ClinicalNotes').count() === 1, 'The clinical-notes source link is missing, repeated or outside the notes toolbar');
    const href = new URL(await link.first().getAttribute('href'), chart.url());
    h.assert(href.pathname.endsWith('/demographic/cdsSourceFields') && href.searchParams.get('demographicNo') === patient,
      'The clinical-notes source link names another patient or route');
    const hint = await link.first().getAttribute('title') || '';
    h.assert(/CDS/.test(hint) && /CARLOS/.test(hint), 'The clinical-notes source link lost its source-creation timing hint');
    h.assert(await sourceLinks(chart, 'NewCategory').count() === 0,
      'An additional-categories link is shown without source fields in that category');
    await assertNotesColumnUntouched('clinical notes only');
  });

  await s.step('additional categories add their own toolbar link', async () => {
    seed('NewCategory');
    await reloadChart(s, chart, 'chart with both CDS source categories');
    await toolbarLinks('NewCategory').first().waitFor({state: 'visible'});
    h.assert(await sourceLinks(chart, 'ClinicalNotes').count() === 1 && await sourceLinks(chart, 'NewCategory').count() === 1,
      'The chart does not show exactly one link per imported category');
    const labels = await chart.locator('.cds-chart-source-links').allInnerTexts();
    h.assert(labels.length === 2 && new Set(labels.map(text => text.trim())).size === 2 && labels.every(text => !/\u00e2/.test(text)),
      `The two source links are not distinct, cleanly encoded labels: ${labels.join(' | ').slice(0, 160)}`);
    await assertNotesColumnUntouched('both categories');
  });
}

if (require.main === module) runWorkflow('echart-cds-source-links', workflow);
module.exports = {workflow, aboveCppBoxes};
