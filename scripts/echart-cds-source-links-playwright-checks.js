#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
/*
 * The encounter chart shows CDS source links only for a patient with imported CDS data.
 *
 * The chart once printed "Clinical notes: CDS source fields — Additional clinical categories: CDS
 * source fields" and a note about CDS creation times above the CPP boxes of every patient, with the
 * dash arriving as mojibake. This opens the real chart for an owned patient no import touched and
 * asserts none of that wording is there, then gives that patient synthetic preserved source fields
 * and asserts each link returns for its own category only.
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

/** What the centre column holds above the CPP boxes, and where the boxes start within it. */
async function aboveCppBoxes(chart) {
  await chart.locator('#cppBoxes').waitFor({state: 'attached'});
  return chart.evaluate(() => {
    const boxes = document.getElementById('cppBoxes');
    const column = boxes.parentElement;
    const nodes = [...column.childNodes];
    return {
      text: nodes.slice(0, nodes.indexOf(boxes)).map(node => (node.textContent || '').trim()).filter(Boolean).join(' '),
      offset: Math.round(boxes.getBoundingClientRect().top - column.getBoundingClientRect().top),
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
  let ordinaryOffset;
  await s.step('a patient no import touched has no CDS wording above the CPP boxes', async () => {
    const above = await aboveCppBoxes(chart);
    h.assert(await chart.locator(CDS_WORDING).count() === 0,
      'The chart of a patient without imported data still renders CDS source links or the CDS timing note');
    h.assert(above.text === '', `The centre column prints text above the CPP boxes: ${above.text.slice(0, 120)}`);
    h.assert(!/CDS source fields|For CDS imports/.test(await chart.locator('body').innerText()),
      'The chart of a patient without imported data mentions CDS source fields');
    ordinaryOffset = above.offset;
  });

  await s.step('imported clinical notes bring back their link and the timing note, and nothing for other categories', async () => {
    seed('ClinicalNotes');
    await reloadChart(s, chart, 'chart with CDS clinical-note source fields');
    const link = sourceLinks(chart, 'ClinicalNotes');
    await link.first().waitFor({state: 'visible'});
    h.assert(await link.count() === 1, 'The clinical-notes source link is missing or repeated');
    const href = new URL(await link.first().getAttribute('href'), chart.url());
    h.assert(href.pathname.endsWith('/demographic/cdsSourceFields') && href.searchParams.get('demographicNo') === patient,
      'The clinical-notes source link names another patient or route');
    h.assert(await sourceLinks(chart, 'NewCategory').count() === 0,
      'An additional-categories link is shown without source fields in that category');
    const timing = chart.locator('.cds-note-source-timing');
    h.assert(await timing.count() === 1 && await timing.isVisible() && /CDS/.test(await timing.innerText()),
      'The source-creation timing note is missing beside imported clinical notes');
    const block = await chart.locator('.cds-chart-source-links').innerText();
    h.assert(!/—|â/.test(block), 'A separator is printed beside a single source link');
    h.assert((await aboveCppBoxes(chart)).offset > ordinaryOffset,
      'The source links take no room above the CPP boxes, so the ordinary-chart layout assertion measures nothing');
  });

  await s.step('both categories are separated by a real dash', async () => {
    seed('NewCategory');
    await reloadChart(s, chart, 'chart with both CDS source categories');
    await sourceLinks(chart, 'NewCategory').first().waitFor({state: 'visible'});
    h.assert(await sourceLinks(chart, 'ClinicalNotes').count() === 1 && await sourceLinks(chart, 'NewCategory').count() === 1,
      'The chart does not show exactly one link per imported category');
    const block = await chart.locator('.cds-chart-source-links').innerText();
    h.assert(/—/.test(block) && !/â/.test(block), `The separator between the source links is not a dash: ${block.slice(0, 160)}`);
  });
}

if (require.main === module) runWorkflow('echart-cds-source-links', workflow);
module.exports = {workflow, aboveCppBoxes};
