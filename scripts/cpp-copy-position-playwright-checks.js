#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * cpp-copy-position — the CPP item editor's Position list and its "Copy to Current Note" button:
 *   Schedule ▸ Search ▸ Master Record ▸ E-Chart ▸ Medical History "+" (or an item) ▸ the editor
 *   (#showEditNote) ▸ Position / Copy to Current Note / Archive / Sign & Save (POST
 *   CaseManagementEntry method=issueNoteSave), then the encounter note's Sign & Save (saveAndExit).
 * Asserts:
 *   - A new item's Position list offers one place per item plus one and starts on 1; each new item
 *     goes first and the earlier ones move down (casemgmt_note.position and the box's order agree).
 *   - Moving the last item to Position 1 puts it first and moves the others down; moving the first
 *     item to the last place puts it last and moves the others up. The positions stay 0..n-1.
 *   - Archive takes an item out of the box and closes the gap in the positions.
 *   - Copy to Current Note appends a line break and the item's text to the open encounter note and
 *     leaves the item unchanged; Sign & Save then stores the copied text in today's signed note,
 *     which is not filed under Medical History.
 * Fixtures: the owned FAKE-PW patient (runWorkflow). Every note, issue link, extension, casemgmt_issue,
 * casemgmt_cpp and eChart row of that patient is deleted in cleanup and asserted gone.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

const TIMEOUT = 30000;
const DIALOG = '#showEditNote';
const BOX = '#divR1I2';

const isEntry = method => r => r.request().method() === 'POST' && /\/CaseManagementEntry(\?|$)/.test(r.url())
  && (new URL(r.url()).searchParams.get('method') === method
    || new URLSearchParams(r.request().postData() || '').get('method') === method);

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const q = h.sqlString;
  const owned = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${owned});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${owned});
      DELETE FROM casemgmt_note_link WHERE note_id IN (${owned});
      DELETE FROM casemgmt_note WHERE demographic_no=${patient};
      DELETE FROM casemgmt_issue WHERE demographic_no=${patient};
      DELETE FROM casemgmt_cpp WHERE demographic_no=${patient};
      DELETE FROM eChart WHERE demographicNo=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM casemgmt_issue WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM casemgmt_cpp WHERE demographic_no=${patient})`) === '0', 'Owned chart rows were not removed');
  });
  h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}`) === '0', 'The owned patient already has notes');

  const text = name => `${marker} Medical History item ${name}`;
  const ids = {};
  const nameOf = id => Object.keys(ids).find(name => ids[name] === id) || `#${id}`;
  /** Active Medical History items as "name@position", in stored order. */
  const storedOrder = () => sql.rows(`SELECT n.note_id, n.position FROM casemgmt_note n
      JOIN casemgmt_issue_notes cin ON cin.note_id=n.note_id JOIN casemgmt_issue ci ON ci.id=cin.id JOIN issue i ON i.issue_id=ci.issue_id
    WHERE n.demographic_no=${patient} AND i.code='MedHistory' AND n.archived=0
      AND n.note_id=(SELECT MAX(r.note_id) FROM casemgmt_note r WHERE r.uuid=n.uuid)
    ORDER BY n.position, n.note_id`).map(([id, position]) => `${nameOf(id)}@${position}`);

  const chart = await s.chart();
  const dialog = chart.locator(DIALOG);
  const button = title => dialog.locator(`input[type="image"][title="${title}"]`);
  /** The box's items, top to bottom, by the note id each item's link opens (noteId=0 is the "+" for a new item). */
  const boxOrder = () => chart.evaluate(selector => {
    const seen = [];
    for (const link of document.querySelectorAll(`${selector} a[onclick*="noteId="]`)) {
      const id = (link.getAttribute('onclick').match(/noteId=(\d+)/) || [])[1];
      if (id && id !== '0' && !seen.includes(id)) seen.push(id);
    }
    return seen;
  }, BOX);
  /** Waits for the box to list exactly these items in this order (it repaints after each save). */
  async function expectOrder(names, label) {
    const wanted = names.map((name, index) => `${name}@${index}`);
    h.assert(JSON.stringify(storedOrder()) === JSON.stringify(wanted),
      `${label}: the stored positions are ${JSON.stringify(storedOrder())}, not ${JSON.stringify(wanted)}`);
    const wantedIds = names.map(name => ids[name]);
    await chart.waitForFunction(({ selector, expected }) => {
      const seen = [];
      for (const link of document.querySelectorAll(`${selector} a[onclick*="noteId="]`)) {
        const id = (link.getAttribute('onclick').match(/noteId=(\d+)/) || [])[1];
        if (id && id !== '0' && !seen.includes(id)) seen.push(id);
      }
      return JSON.stringify(seen) === JSON.stringify(expected);
    }, { selector: BOX, expected: wantedIds }, { timeout: TIMEOUT })
      .catch(async () => { throw new Error(`${label}: the box lists ${JSON.stringify((await boxOrder()).map(nameOf))}, not ${JSON.stringify(names)}`); });
  }
  const positionOptions = () => chart.evaluate(() => [...document.getElementById('position').options]
    .map(option => ({ value: option.value, text: option.text, selected: option.selected })));
  async function openItem(name) {
    await chart.locator(`${BOX} a[onclick*="noteId=${ids[name]}"]`).first().click();
    await dialog.waitFor({ state: 'visible', timeout: TIMEOUT });
    h.assert((await chart.locator('#noteEditTxt').inputValue()).trim() === text(name), `The editor did not open item ${name}`);
  }
  async function press(title, label, name) {
    const [response] = await Promise.all([
      chart.waitForResponse(isEntry('issueNoteSave'), { timeout: TIMEOUT }),
      button(title).click(),
    ]);
    h.assert(response.status() < 400, `${label} answered HTTP ${response.status()}`);
    await dialog.waitFor({ state: 'hidden', timeout: TIMEOUT });
    // A saved CPP edit creates a revision; subsequent clicks and row checks must use its ID.
    if (name) {
      const savedId = sql.value(`SELECT MAX(note_id) FROM casemgmt_note WHERE demographic_no=${patient} AND note=${q(text(name))}`);
      h.assert(/^[1-9]\d*$/.test(savedId), `${label} did not store item ${name}`);
      return savedId;
    }
  }

  await s.step('Medical History "+" offers one place per item plus one, starting on 1, and each new item goes first', async () => {
    const added = [];
    for (const name of ['A', 'B', 'C']) {
      await chart.locator(`${BOX} a[title="Add Item"]`).first().click();
      await dialog.waitFor({ state: 'visible', timeout: TIMEOUT });
      const options = await positionOptions();
      h.assert(JSON.stringify(options.map(o => o.text)) === JSON.stringify(Array.from({ length: added.length + 1 }, (_, i) => String(i + 1)))
        && options[0].selected && options[0].value === '0',
      `Adding item ${name} offered positions ${JSON.stringify(options)}`);
      await chart.locator('#noteEditTxt').fill(text(name));
      await press('Sign & Save', `Saving item ${name}`);
      ids[name] = sql.value(`SELECT MAX(note_id) FROM casemgmt_note WHERE demographic_no=${patient} AND note=${q(text(name))}`);
      h.assert(/^[1-9]\d*$/.test(ids[name]), `Item ${name} was not stored`);
      added.unshift(name);
      await expectOrder(added, `After adding ${name}`);
    }
  });

  await s.step('moving the last item to Position 1 puts it first and moves the others down', async () => {
    await openItem('A');
    const options = await positionOptions();
    h.assert(options.find(o => o.selected)?.value === '2', `Item A (third) opened on position ${JSON.stringify(options.find(o => o.selected))}`);
    // By value: the list shows 1..n+1 for values 0..n, so a bare '2' would also match the label "2" (value 1).
    await chart.locator('#position').selectOption({ value: '0' });
    ids.A = await press('Sign & Save', 'Moving item A up', 'A');
    await expectOrder(['A', 'C', 'B'], 'After moving A to Position 1');
  });

  await s.step('moving the first item to the last place puts it last and moves the others up', async () => {
    await openItem('A');
    h.assert((await positionOptions()).find(o => o.selected)?.value === '0', 'Item A (now first) did not open on Position 1');
    await chart.locator('#position').selectOption({ value: '2' });
    ids.A = await press('Sign & Save', 'Moving item A down', 'A');
    await expectOrder(['C', 'B', 'A'], 'After moving A to Position 3');
  });

  await s.step('moving the last item to the middle shifts only the following item', async () => {
    await openItem('A');
    await chart.locator('#position').selectOption({ value: '1' });
    ids.A = await press('Sign & Save', 'Moving item A to the middle', 'A');
    await expectOrder(['C', 'A', 'B'], 'After moving A to Position 2');
    await openItem('A');
    await chart.locator('#position').selectOption({ value: '2' });
    ids.A = await press('Sign & Save', 'Returning item A to the last place', 'A');
    await expectOrder(['C', 'B', 'A'], 'After returning A to Position 3');
  });

  await s.step('Archive takes an item out of the box and closes the gap in the positions', async () => {
    await openItem('C');
    ids.C = await press('Archive', 'Archiving item C', 'C');
    h.assert(sql.value(`SELECT archived FROM casemgmt_note WHERE note_id=${ids.C}`) === '1', 'Item C was not archived');
    await expectOrder(['B', 'A'], 'After archiving C');
  });

  await s.step('Copy to Current Note appends the item to the open note, and Sign & Save stores it in today\'s note, not in the item', async () => {
    const editor = chart.locator('#encMainDiv textarea[name="caseNote_note"]');
    await editor.first().waitFor({ state: 'visible', timeout: TIMEOUT });
    h.assert(await editor.count() === 1, `Expected one open note editor, found ${await editor.count()}`);
    const before = await editor.inputValue();
    const itemBefore = sql.rows(`SELECT note_id, note, position, archived FROM casemgmt_note WHERE uuid=(SELECT uuid FROM casemgmt_note WHERE note_id=${ids.B}) ORDER BY note_id`);
    await openItem('B');
    await button('Copy to Current Note').click();
    h.assert(await editor.inputValue() === `${before}\n${text('B')}`,
      `Copy to Current Note left the note as ${JSON.stringify(await editor.inputValue())}`);
    h.assert(await dialog.isVisible(), 'Copy to Current Note closed the item editor');
    await button('Exit').click();
    await dialog.waitFor({ state: 'hidden', timeout: TIMEOUT });
    const closed = chart.waitForEvent('close', { timeout: TIMEOUT }).then(() => true).catch(() => false);
    const [response] = await Promise.all([
      chart.waitForResponse(isEntry('saveAndExit'), { timeout: TIMEOUT }),
      chart.locator('#signSaveImg').first().click(),
    ]);
    h.assert(response.status() < 400, `Sign & Save answered HTTP ${response.status()}`);
    h.assert(await closed, 'Sign & Save did not close the chart window');
    const today = sql.rows(`SELECT n.note_id, n.signed, COALESCE(n.signing_provider_no,'') FROM casemgmt_note n
      WHERE n.demographic_no=${patient} AND LOCATE(${q(`\n${text('B')}`)}, n.note) > 0
        AND NOT EXISTS (SELECT 1 FROM casemgmt_issue_notes cin JOIN casemgmt_issue ci ON ci.id=cin.id JOIN issue i ON i.issue_id=ci.issue_id
          WHERE cin.note_id=n.note_id AND i.code='MedHistory')`);
    h.assert(today.length === 1, `Expected today's note to hold the copied item once, found ${today.length} note(s)`);
    h.assert(today[0][1] === '1' && today[0][2] === provider, 'Today\'s note with the copied item was not stored signed by the test provider');
    h.assert(JSON.stringify(sql.rows(`SELECT note_id, note, position, archived FROM casemgmt_note WHERE uuid=(SELECT uuid FROM casemgmt_note WHERE note_id=${ids.B}) ORDER BY note_id`))
      === JSON.stringify(itemBefore), 'Copying item B or signing the note changed the item');
  });
}

if (require.main === module) runWorkflow('cpp-copy-position', workflow);
module.exports = { workflow };
