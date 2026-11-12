#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * lab-linked-tickler — a lab result with ticklers attached to it still opens, and lists them (#4722):
 *   Schedule ▸ Inbox ▸ the lab's row ▸ the lab display ▸ Tickler (tickler/ForwardDemographicTickler
 *   ?docType=HL7&docId=<lab>) ▸ save; twice, then Schedule ▸ Inbox ▸ the same row again.
 * Asserts:
 *   - Before any tickler is attached, the lab opens and shows no "Ticklers Linked to this Lab" section.
 *   - The Tickler button opens a tickler form for the lab's patient that carries the lab.
 *   - Saving it once assigned to another provider and once to the logged-in provider stores two ticklers
 *     for the lab's patient, each linked to the lab in ticklerdocs.
 *   - The lab opened again from the Inbox is not an error page, and "View Ticklers Linked to this Lab"
 *     lists both ticklers with their messages and their assignees' names. (Before the fix for #4722 the
 *     page answered 500: the display read each tickler's lazily loaded assignee after its query had
 *     returned.)
 * Fixtures: one synthetic PATHL7 ORU^R01 (serviceName CARLOSLABTICKLER) in hl7TextMessage/hl7TextInfo,
 * routed to the test provider and to the owned FAKE-PW patient; the two ticklers carry the run marker.
 * Cleanup deletes the ticklers with their ticklerdocs, comments and updates, then the lab rows, by id, and
 * asserts them gone. No clinic-wide state is changed.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');

const TIMEOUT = 20000;
const SERVICE = 'CARLOSLABTICKLER';

function hl7(marker, accession, stamp) {
  return [
    `MSH|^~\\&|PATHL7|CW|CARLOS|TEST|${stamp}||ORU^R01|LABTICKLER${stamp}|P|2.3|||ER|AL`,
    `PID||9999999999|9999999999||${marker}^Linked||19800101|F`,
    `ORC|RE||${accession}|||||||||99999^FAKE-ORDERING^DOC`,
    `OBR|1||${accession}|CHEM^Chemistry||${stamp}|${stamp}|||||||${stamp}||99999^FAKE-ORDERING^DOC||||||${stamp}||CHEM4|F|||99999^FAKE-ORDERING^DOC`,
    `OBX|1|NM|2345-7^Glucose Random||9.8|mmol/L|3.3-7.7|H|||F|||${stamp}`,
  ].join('\r');
}

async function workflow(s) {
  const { sql, patient, provider, marker, context, recorder } = s;
  const q = h.sqlString;
  const otherRow = sql.rows(`SELECT provider_no, last_name, first_name FROM provider WHERE status='1' AND provider_no NOT LIKE '-%'
    AND provider_no<>${q(provider)} AND last_name LIKE 'FAKE-%' ORDER BY last_name, first_name, provider_no LIMIT 1`)[0];
  if (!otherRow) throw new h.SkipCheck('No other active FAKE provider exists to assign a tickler to');
  const [other, otherLast, otherFirst] = otherRow;
  const [[selfLast, selfFirst]] = sql.rows(`SELECT last_name, first_name FROM provider WHERE provider_no=${q(provider)}`);
  const ticklers = [
    { message: `${marker} lab follow-up for another provider`, assignee: other, name: `${otherLast}, ${otherFirst}` },
    { message: `${marker} lab follow-up for me`, assignee: provider, name: `${selfLast}, ${selfFirst}` },
  ];

  const ownedTicklers = `demographic_no=${patient} AND message LIKE ${q(`${marker}%`)}`;
  s.cleanup(() => {
    const ids = sql.rows(`SELECT tickler_no FROM tickler WHERE ${ownedTicklers}`).map(row => row[0]);
    h.assert(ids.every(id => /^[1-9]\d*$/.test(id)), 'Owned tickler ID is invalid');
    if (ids.length) {
      sql.execute(`DELETE FROM ticklerdocs WHERE tickler_id IN (${ids.join(',')});
        DELETE FROM tickler_comments WHERE tickler_no IN (${ids.join(',')});
        DELETE FROM tickler_update WHERE tickler_no IN (${ids.join(',')});
        DELETE FROM tickler WHERE ${ownedTicklers} AND tickler_no IN (${ids.join(',')})`);
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM tickler WHERE ${ownedTicklers}`) === '0', 'Owned ticklers were not removed');
  });

  const owned = { labNo: null };
  s.cleanup(() => {
    if (!owned.labNo) return;
    const lab = Number(owned.labNo);
    h.assert(sql.value(`SELECT COUNT(*) FROM hl7TextMessage WHERE lab_id=${lab} AND serviceName=${q(SERVICE)}`) === '1', 'Owned lab identity changed');
    sql.execute(`DELETE FROM patientLabRouting WHERE lab_no=${lab} AND lab_type='HL7'; DELETE FROM providerLabRouting WHERE lab_no=${lab} AND lab_type='HL7';
      DELETE FROM hl7TextInfo WHERE lab_no=${lab}; DELETE FROM hl7TextMessage WHERE lab_id=${lab} AND serviceName=${q(SERVICE)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM patientLabRouting WHERE lab_no=${lab} AND lab_type='HL7')
      + (SELECT COUNT(*) FROM providerLabRouting WHERE lab_no=${lab} AND lab_type='HL7')
      + (SELECT COUNT(*) FROM hl7TextInfo WHERE lab_no=${lab}) + (SELECT COUNT(*) FROM hl7TextMessage WHERE lab_id=${lab})`) === '0', 'Owned lab rows remain');
  });
  const accession = `LABTKL-${marker.slice(-10)}`;
  const now = new Date();
  const stamp = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}100000`;
  const labNo = sql.value(`INSERT INTO hl7TextMessage (fileUploadCheck_id, message, type, serviceName, created)
    VALUES (0, ${q(Buffer.from(hl7(marker, accession, stamp), 'utf8').toString('base64'))}, 'PATHL7', ${q(SERVICE)}, NOW()); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(labNo), 'The fixture lab did not insert');
  owned.labNo = labNo;
  sql.execute(`INSERT INTO hl7TextInfo (lab_no, sex, health_no, result_status, final_result_count, obr_date, priority, requesting_client,
      discipline, last_name, first_name, report_status, accessionNum, filler_order_num, sending_facility)
    VALUES (${labNo}, 'F', '9999999999', 'A', 1, NOW(), 'R', 'FAKE-ORDERING, DOC', 'CHEM', ${q(marker)}, 'Linked', 'F', ${q(accession)}, ${q(accession)}, 'CW');
    INSERT INTO providerLabRouting (provider_no, lab_no, status, lab_type) VALUES (${q(provider)}, ${labNo}, 'N', 'HL7');
    INSERT INTO patientLabRouting (demographic_no, lab_no, lab_type, created) VALUES (${patient}, ${labNo}, 'HL7', NOW())`);

  let inbox;
  async function openLab(label) {
    if (!inbox || inbox.isClosed()) {
      ({ page: inbox } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('a#inboxLink').first(),
        { context, recorder, label: 'lab-tickler-inbox', timeout: 30000 }));
    } else {
      await inbox.reload({ waitUntil: 'domcontentloaded' });
    }
    await inbox.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
    const row = inbox.locator(`tr[data-segment-id="${labNo}"][data-lab-type="HL7"]`).first();
    await row.waitFor({ state: 'attached', timeout: 45000 });
    // s.popup fails on the application's error page (what the lab answered before #4722 was fixed) and closes
    // the window; the same address is loaded once more so the screenshot shows what the provider saw.
    const opened = [];
    const collect = page => opened.push(page);
    context.on('page', collect);
    let lab;
    try {
      lab = await s.popup(inbox, row.locator('a[onclick*="reportWindow"]').first(), label);
    } catch (error) {
      const url = opened.length ? opened[opened.length - 1].url() : '';
      if (s.config.screenshotDir && /^https?:/.test(url)) {
        const again = await context.newPage();
        await again.goto(url).catch(() => {});
        await h.screenshot(again, s.config.screenshotDir, `${label}-failed`).catch(() => {});
        await again.close().catch(() => {});
      }
      throw error;
    } finally {
      context.off('page', collect);
    }
    await lab.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    h.assert((await lab.locator('body').innerText()).includes(accession), 'The lab display is not the owned lab');
    await h.screenshot(lab, s.config.screenshotDir, label);
    return lab;
  }

  let lab;
  await s.step('the Inbox opens the lab, which lists no linked tickler yet', async () => {
    lab = await openLab('lab-tickler-display');
    h.assert(await lab.locator('#ticklerWrap').count() === 0, 'The lab lists linked ticklers before any was attached');
  });

  const ownedRows = () => sql.rows(`SELECT tickler_no, message, task_assigned_to, creator FROM tickler WHERE ${ownedTicklers} ORDER BY tickler_no`);
  await s.step('the lab\'s Tickler button opens a tickler form for the lab\'s patient that carries the lab, and saving it twice links both ticklers to the lab', async () => {
    for (const tickler of ticklers) {
      const form = await s.popup(lab, lab.locator('input[type="button"][value="Tickler"]').first(), 'lab-tickler-form');
      const url = new URL(form.url());
      h.assert(h.pathOnly(form.url()).endsWith('/tickler/ForwardDemographicTickler') && url.searchParams.get('docType') === 'HL7'
        && url.searchParams.get('docId') === labNo && url.searchParams.get('demographic_no') === patient,
      `The Tickler button opened ${h.pathOnly(form.url())}${url.search}`);
      const serviceForm = form.locator('form[name="serviceform"]');
      await serviceForm.waitFor({ state: 'visible', timeout: TIMEOUT });
      h.assert(await serviceForm.locator('input[name="demographic_no"]').last().inputValue() === patient, 'The tickler form is not for the lab\'s patient');
      h.assert(await serviceForm.locator('input[name="docType"]').inputValue() === 'HL7'
        && await serviceForm.locator('input[name="docId"]').inputValue() === labNo, 'The tickler form does not carry the lab');
      await serviceForm.locator('select[name="task_assigned_to"]').selectOption(tickler.assignee);
      await form.locator('#ticklerMessage').fill(tickler.message);
      await serviceForm.locator('input.btn-primary[name="Button"]').first().click();
      const sentinel = await form.waitForFunction(() => {
        const frame = document.getElementById('ticklerSubmitFrame');
        const doc = frame && frame.contentDocument;
        if (!doc) return null;
        return ['tickler-save-ok', 'tickler-save-ok-link-failed'].find(id => doc.getElementById(id)) || null;
      }, null, { timeout: 30000 }).then(handle => handle.jsonValue());
      h.assert(sentinel === 'tickler-save-ok', `The tickler saved with "${sentinel}", not a clean save`);
      if (!form.isClosed()) await form.close();
    }
    const rows = ownedRows();
    h.assert(JSON.stringify(rows.map(([, message, assignee, creator]) => [message, assignee, creator]))
      === JSON.stringify(ticklers.map(t => [t.message, t.assignee, provider])), `The saved ticklers were ${JSON.stringify(rows)}`);
    for (const [ticklerNo] of rows) {
      const link = sql.rows(`SELECT doctype, lab_type, document_no, deleted IS NULL FROM ticklerdocs WHERE tickler_id=${Number(ticklerNo)}`);
      h.assert(JSON.stringify(link) === JSON.stringify([['L', 'HL7', labNo, '1']]), `Tickler ${ticklerNo}'s ticklerdocs rows were ${JSON.stringify(link)}`);
    }
    await lab.close();
  });

  await s.step('the lab opened again from the Inbox lists both linked ticklers with their assignees', async () => {
    const reopened = await openLab('lab-tickler-display-linked');
    const wrap = reopened.locator('#ticklerWrap');
    h.assert(await wrap.count() === 1, 'The reopened lab has no "Ticklers Linked to this Lab" section');
    await reopened.locator('#open-ticklers').click();
    const display = reopened.locator('#ticklerDisplay');
    await display.waitFor({ state: 'visible', timeout: TIMEOUT });
    const text = await display.innerText();
    for (const tickler of ticklers) {
      h.assert(text.includes(tickler.message), `The linked ticklers do not list "${tickler.message}"`);
      h.assert(text.includes(tickler.name), `The linked ticklers do not name the assignee ${tickler.name}`);
    }
    await h.screenshot(reopened, s.config.screenshotDir, 'lab-tickler-display-linked-open');
    await reopened.close();
  });
}

if (require.main === module) runWorkflow('lab-linked-tickler', workflow, { openMaster: false });
module.exports = { workflow };
