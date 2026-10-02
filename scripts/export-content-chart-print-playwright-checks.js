#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * E-Chart print: does the PDF carry each note's text exactly as stored?
 * User path: Schedule ▸ Search ▸ Master Record ▸ E-Chart ▸ the control-panel printer icon ▸ Print all ▸ Print.
 * gap-clinical-chart-print-scope proves WHICH notes print; this proves WHAT they print: a chart print leaves the
 * building (referrals, medico-legal requests), so a dropped accent or a clipped note is a clinical-record defect.
 *
 * Asserts (pdftotext of the downloaded PDF, compared with the stored note text with whitespace ignored): a note
 * with Latin-1 accents, quotes, apostrophes, an ampersand, angle brackets, degree and fraction signs prints as
 * typed; a multi-line note keeps every line in order; a 6 000-character note prints to its last word (more than one
 * page, nothing clipped); the patient header carries the accented first name; and (LAST, fails today when the PDF
 * font lacks the glyphs) notes in Vietnamese, Polish and Turkish letters print their text, because patients and
 * clinicians in Canada write names and findings in them.
 * Fixtures: the owned FAKE-PW patient with SQL-seeded signed notes; cleanup deletes the patient's notes and asserts.
 * Needs pdftotext. Reads only.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const print = require('./lib/gap-clinical-print');
const x = require('./lib/export-content-helpers');

const q = h.sqlString;
const FIRST = 'Zoë Ñandú';
const WORD = i => `mark${String(i).padStart(3, '0')}x`;

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const scratch = print.scratchDir();
  s.cleanup(() => require('node:fs').rmSync(scratch, { recursive: true, force: true }));
  s.cleanup(() => {
    const owned = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`;
    sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${owned});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${owned});
      DELETE FROM casemgmt_note_link WHERE note_id IN (${owned});
      DELETE FROM casemgmt_note WHERE demographic_no=${patient};
      DELETE FROM casemgmt_issue WHERE demographic_no=${patient};
      DELETE FROM eChart WHERE demographicNo=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}`) === '0', 'Owned notes were not removed');
  });
  sql.execute(`UPDATE demographic SET first_name=${q(FIRST)} WHERE demographic_no=${patient} AND last_name=${q(marker)}`);
  const program = sql.value('SELECT id FROM program WHERE name=\'OSCAR\' ORDER BY id LIMIT 1');
  const role = sql.value(`SELECT role_id FROM program_provider WHERE provider_no=${q(provider)} AND program_id=${program || 0} LIMIT 1`);
  h.assert(program && role, 'The default OSCAR program or the test provider\'s role in it is missing');

  const notes = {
    latin: `${marker} LATIN Patient says "chest tightness" — O'Neil's café visit; temp 38.5°C; BP 120/80 & HR 72 <stable> `
      + '½ tablet bid; naïve façade; “smart quotes” and ‘single’; Zoë Ünal Ñandú; 5 µg',
    lines: `${marker} LINES first line\nsecond line with detail\n\nfourth line after a blank line\n\tindented fifth line`,
    long: `${marker} LONG ${Array.from({ length: 700 }, (_, i) => (i % 40 === 0 ? WORD(i / 40) : 'lorem')).join(' ')} ENDOFLONGNOTE`,
    viet: `${marker} VIET Nguyễn Thị Hương đau đầu`,
    polish: `${marker} POLISH Łukasz Żółć ąęśń`,
    turkish: `${marker} TURKISH İstanbul şğı Yaşar`,
  };
  const seed = (text, minutes) => sql.value(`INSERT INTO casemgmt_note (update_date,observation_date,demographic_no,provider_no,note,signed,
      signing_provider_no,encounter_type,program_no,reporter_caisi_role,history,uuid,locked,archived)
    VALUES (NOW(),DATE_SUB(NOW(), INTERVAL ${minutes} MINUTE),${patient},${q(provider)},${q(text)},1,${q(provider)},'',${q(program)},
      ${q(role)},${q(text)},UUID(),'0',0); SELECT LAST_INSERT_ID()`);
  const ids = {};
  let minutes = 10;
  for (const [key, text] of Object.entries(notes)) ids[key] = seed(text, minutes++);
  const stored = key => sql.value(`SELECT note FROM casemgmt_note WHERE note_id=${ids[key]}`);
  h.assert(Object.keys(notes).every(key => stored(key) === notes[key]), 'The seeded notes were not stored as written');

  let text;
  let file;
  const chart = await s.chart();
  for (const id of Object.values(ids)) await chart.locator(`#print${id}`).waitFor({ state: 'attached', timeout: 20000 });

  await s.step('Print all downloads one PDF holding the owned notes', async () => {
    await print.openPrintDialog(chart);
    await chart.locator('#printopAll').check();
    await print.setFlags(chart, []);
    const out = await print.pressPrint(chart, scratch);
    text = out.text;
    file = out;
    h.assert(text.includes(`${marker} LATIN`) && text.includes(`${marker} LONG`), 'The print is missing an owned note');
  });

  const squashed = () => x.squash(text);
  const mustContain = (key, label) => {
    const want = x.squash(stored(key));
    if (squashed().includes(want)) return;
    let at = 0;
    const have = squashed();
    const start = have.indexOf(x.squash(`${marker} ${key.toUpperCase()}`));
    if (start >= 0) while (at < want.length && have[start + at] === want[at]) at++;
    const around = start >= 0 ? ` | printed: "${have.slice(start + at - 20, start + at + 50)}" stored: "${want.slice(at - 20, at + 50)}"` : '';
    throw new Error(`${label}: the printed note differs from the stored note${start >= 0 ? ` from character ${at} of ${want.length}` : ' (its opening is missing)'}${around}`);
  };

  await s.step('the Latin-1 note prints as typed (accents, quotes, apostrophes, ampersand, angle brackets, degree and fraction signs)', async () => {
    mustContain('latin', 'Latin-1 note');
  });

  await s.step('the multi-line note keeps every line in order', async () => {
    const lines = stored('lines').split('\n').map(l => l.trim()).filter(Boolean);
    let from = text.indexOf(`${marker} LINES`);
    h.assert(from >= 0, 'The multi-line note is missing from the print');
    for (const line of lines) {
      const at = text.indexOf(line, from);
      h.assert(at >= 0, 'A line of the multi-line note is missing or out of order in the print');
      from = at;
    }
  });

  await s.step('the long note prints to its last word', async () => {
    // The confidentiality footer interrupts the running text at each page break, so compare the words, not a substring.
    const words = text.split(/\s+/);
    const marks = words.filter(w => /^mark\d{3}x$/.test(w));
    const wantMarks = Array.from({ length: 18 }, (_, i) => WORD(i));
    h.assert(JSON.stringify(marks) === JSON.stringify(wantMarks), `The long note prints ${marks.length} of its 18 landmark words, or out of order`);
    const lorem = words.filter(w => w === 'lorem').length;
    h.assert(lorem === 682, `The long note prints ${lorem} of its 682 filler words`);
    h.assert(text.includes('ENDOFLONGNOTE'), 'The long note is clipped before its last word');
  });

  await s.step('the patient header carries the accented first name', async () => {
    h.assert(squashed().includes(x.squash(FIRST)), 'The print header does not carry the patient\'s accented first name');
  });

  await s.step('notes in Vietnamese, Polish and Turkish letters print their text', async () => {
    const lost = {};
    const printed = new Set([...text.normalize('NFC')]);
    for (const key of ['viet', 'polish', 'turkish']) {
      const missing = [...new Set([...stored(key).normalize('NFC')])].filter(c => !/\s/.test(c) && !printed.has(c));
      if (missing.length) { lost[key] = missing.map(c => `U+${c.codePointAt(0).toString(16).toUpperCase().padStart(4, '0')}`).join(' '); continue; }
      // Every letter is somewhere in the PDF, but the note must also print as stored (order, no corruption).
      try { mustContain(key, `${key} note`); } catch (error) { lost[key] = 'the printed text differs from the stored note'; }
    }
    h.assert(!Object.keys(lost).length, `The chart print does not carry these notes as stored (letters are dropped when the PDF font has no glyph for them): ${JSON.stringify(lost)}`);
  });
  h.assert(file.bytes > 0, 'No PDF was captured');
}

if (require.main === module) {
  runWorkflow('export-content-chart-print', workflow, { openPatient: true, preflight: () => x.requirePoppler('pdftotext') });
}
module.exports = { workflow };
