#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * provider-signature-contact — coverage plan §2.2 (provider preferences: signature stamp and text
 * signature, and where clinical output shows them).
 *
 * User paths, as a THROWAWAY login (lib/throwaway-login-fixture.js): its stamp is
 * consult_sig_<own provider_no>.png, so the shared provider's stamp file other checks rely on is
 * never overwritten and nothing on the test provider needs a snapshot:
 *   Schedule ▸ Preferences ▸ Signature Stamp ▸ choose file ▸ Upload (provider/providerSignatureStamp);
 *   Search ▸ Master Record ▸ E-Chart ▸ Consultations "+" (the consultation request form);
 *   Preferences ▸ Signature Stamp ▸ Delete; Preferences ▸ Edit text signature (EnterSignature);
 *   E-Chart ▸ new note ▸ Sign & Save.
 * Asserted: a non-image upload is refused with nothing stored; a PNG upload writes the
 * provider_consult_signature property and the stamp file, the preview loads, and the consultation
 * form for the owned patient shows that stamp (same pixel width) instead of the signature pad; a
 * GET delete is refused; Delete removes row and file and the form falls back to the pad; the text
 * signature saved through EnterSignature is the name a signed note is stamped with.
 * Fixtures: the throwaway login and the owned FAKE- patient (runWorkflow). Cleanup removes the
 * stamp file if still present, the notes on the owned patient, the throwaway's providerExt and
 * ProviderPreference rows, then the throwaway itself (property, roles, log rows).
 */
const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');
const { openChart, waitForNavbars } = require('./echart-navbar-modules-playwright-checks');

// Where the packaged install keeps eForm images (and stamps); the file-level assertions are made
// only when the check runs on the application host and the directory is readable.
const STAMP_DIR = process.env.EFORM_IMAGE_DIR || '/var/lib/carlos-emr/CarlosDocument/carlos/eform/images';
const STAMP_WIDTH = 160;
const STAMP_HEIGHT = 48;
const STAMP_REQUEST = /\/provider\/providerSignatureImage(\?|$)/;

function crc32(buffer) {
  let crc = ~0;
  for (const byte of buffer) {
    crc ^= byte;
    for (let k = 0; k < 8; k++) crc = (crc >>> 1) ^ (0xedb88320 & -(crc & 1));
  }
  return (~crc) >>> 0;
}

/** A small black-on-white PNG with a diagonal stroke, built without image libraries. */
function stampPng(width, height) {
  const raw = Buffer.alloc((width * 3 + 1) * height, 0xff);
  for (let y = 0; y < height; y++) {
    raw[y * (width * 3 + 1)] = 0;
    for (let x = 0; x < width; x++) {
      if (Math.abs(Math.round((x * height) / width) - y) < 2) raw.fill(0, y * (width * 3 + 1) + 1 + x * 3, y * (width * 3 + 1) + 4 + x * 3);
    }
  }
  const chunk = (type, data) => {
    const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
    const length = Buffer.alloc(4); length.writeUInt32BE(data.length);
    const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(body));
    return Buffer.concat([length, body, crc]);
  };
  const header = Buffer.alloc(13);
  header.writeUInt32BE(width, 0); header.writeUInt32BE(height, 4);
  header[8] = 8; header[9] = 2;
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', header),
    chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}

async function workflow(s) {
  const { sql, config, recorder, patient, marker } = s;
  const fixture = throwawayLoginFixture({ sql, marker, provider: s.provider, testUser: config.testUser });
  s.cleanup(() => fixture.cleanup());
  const stampFile = () => path.join(STAMP_DIR, `consult_sig_${fixture.providerNo}.png`);
  const canSeeFiles = fs.existsSync(STAMP_DIR);
  s.cleanup(() => {
    if (!fixture.providerNo) return;
    if (canSeeFiles && fs.existsSync(stampFile())) fs.unlinkSync(stampFile());
    h.assert(!canSeeFiles || !fs.existsSync(stampFile()), 'The owned stamp file was not removed');
    const owner = h.sqlString(fixture.providerNo);
    const notes = sql.rows(`SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`).map(row => row[0]);
    h.assert(notes.every(id => /^\d+$/.test(id)), 'Owned note id is invalid');
    const list = notes.length ? notes.join(',') : '0';
    sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${list});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${list});
      DELETE FROM casemgmt_note_link WHERE note_id IN (${list});
      DELETE FROM casemgmt_note WHERE demographic_no=${patient};
      DELETE FROM eChart WHERE demographicNo=${patient};
      DELETE FROM providerExt WHERE provider_no=${owner};
      DELETE FROM ProviderPreferenceAppointmentScreenQuickLink WHERE providerNo=${owner};
      DELETE FROM ProviderPreference WHERE providerNo=${owner}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM providerExt WHERE provider_no=${owner})
      + (SELECT COUNT(*) FROM ProviderPreference WHERE providerNo=${owner})`) === '0', 'Owned note/signature rows were not removed');
  });
  fixture.create();
  const owner = h.sqlString(fixture.providerNo);
  // Signing a note needs program membership (without it the save rolls back with HTTP 500); mirror
  // the test provider's. The fixture's cleanup removes program_provider rows for its provider.
  sql.execute(`INSERT INTO program_provider (program_id,provider_no,role_id,team_id)
    SELECT program_id,${owner},role_id,team_id FROM program_provider WHERE provider_no=${h.sqlString(s.provider)}`);
  const stampProperty = () => sql.value(`SELECT COALESCE(value,'') FROM property WHERE provider_no=${owner}
    AND name='provider_consult_signature'`);

  const context = await h.newContext(s.context.browser(), config);
  context.setDefaultTimeout(20000);
  context.on('page', page => h.wireStrictPage(page, 'throwaway', recorder));
  const schedule = await h.login(context, { ...config, testUser: fixture.username }, recorder, { label: 'throwaway-login' });
  const popup = (page, locator, label) => ui.clickOpensPopup(page, locator, { context, recorder, label, timeout: 20000 });
  const prefs = await popup(schedule, schedule.getByTitle(/Edit your personal setting/i).first(), 'preferences');
  const fileInput = prefs.locator('#sigFileInput');
  await revealAuditLink(prefs, fileInput, 20000);
  const status = prefs.locator('#sigStatusMsg');
  async function upload(file) {
    await fileInput.setInputFiles(file);
    const [answer] = await Promise.all([
      prefs.waitForResponse(r => r.request().method() === 'POST' && h.pathOnly(r.url()).endsWith('/provider/providerSignatureStamp')),
      prefs.locator('button[onclick="uploadSignatureFile()"]').click(),
    ]);
    await status.waitFor({ state: 'visible' });
    return answer.json();
  }

  await s.step('a non-image file is refused with an error message and nothing stored', async () => {
    const result = await upload({ name: `${marker}.png`, mimeType: 'image/png', buffer: Buffer.from(`${marker} not an image`) });
    h.assert(result.success === false && /alert-danger/.test(await status.getAttribute('class')),
      'A non-image upload was not refused on the page');
    h.assert(stampProperty() === '' && (!canSeeFiles || !fs.existsSync(stampFile())), 'A refused upload stored a stamp');
  });

  await s.step('a PNG upload stores the stamp file and property and the preview loads it', async () => {
    const result = await upload({ name: 'stamp.png', mimeType: 'image/png', buffer: stampPng(STAMP_WIDTH, STAMP_HEIGHT) });
    h.assert(result.success === true && /alert-success/.test(await status.getAttribute('class')), 'The PNG upload was not accepted');
    h.assert(stampProperty() === `consult_sig_${fixture.providerNo}.png`, 'The upload did not record provider_consult_signature');
    if (canSeeFiles) {
      const stored = fs.readFileSync(stampFile());
      h.assert(stored.readUInt32BE(16) === STAMP_WIDTH && stored.readUInt32BE(20) === STAMP_HEIGHT, 'The stored stamp is not the uploaded image');
    }
    await prefs.waitForFunction(() => {
      const img = document.getElementById('sigPreviewImg');
      return img && img.complete && img.naturalWidth > 0 && img.style.display !== 'none';
    });
    h.assert(await prefs.locator('#sigDeleteSection button').isVisible(), 'The preview did not offer Delete after the upload');
  });

  const { masterPage: master } = await openMasterRecord(context, schedule, recorder,
    { searchTerm: marker, preferredDemographicNo: patient, timeout: 20000 });
  const chart = await openChart(context, master, recorder, 20000);
  await waitForNavbars(chart, 20000);
  async function openConsultForm(label) {
    const form = await popup(chart, chart.locator('a[onclick*="ViewConsultationFormRequest"]').first(), label);
    await form.locator('#EctConsultationFormRequest2Form').waitFor({ state: 'attached' });
    h.assert(await form.locator('#signatureProviderNo').inputValue() === fixture.providerNo,
      'The consultation form is not signing as the logged-in provider');
    return form;
  }

  await s.step('the consultation request form for the owned patient shows the uploaded stamp, not the pad', async () => {
    const form = await openConsultForm('consult-with-stamp');
    await form.waitForFunction(() => {
      const img = document.getElementById('signatureImgTag');
      return img && img.complete && img.naturalWidth > 0;
    });
    h.assert(await form.locator('#signatureImgTag').evaluate(img => img.naturalWidth) === STAMP_WIDTH,
      'The consultation form rendered an image other than the uploaded stamp');
    h.assert(await form.locator('#newSignature').inputValue() === 'false' && await form.locator('#signatureShow').isVisible(),
      'The consultation form did not treat the stamp as the stored signature');
    await form.close();
  });

  await s.step('a GET delete of the stamp is refused and the stamp survives', async () => {
    const answer = await context.request.get(h.appUrl(config.baseUrl, '/provider/providerSignatureStamp?method=delete'), { maxRedirects: 0 });
    const body = await answer.json().catch(() => ({}));
    h.assert(body.success !== true, 'A GET delete reported success');
    h.assert(stampProperty() !== '' && (!canSeeFiles || fs.existsSync(stampFile())), 'A GET delete removed the stamp');
  });

  await s.step('Delete removes the stamp row and file and the consultation form falls back to the pad', async () => {
    const dialogs = await h.withExpectedDialogs(prefs, async () => {
      await Promise.all([
        prefs.waitForResponse(r => r.request().method() === 'POST' && h.pathOnly(r.url()).endsWith('/provider/providerSignatureStamp')),
        prefs.locator('#sigDeleteSection button').click(),
      ]);
    });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Delete did not ask for confirmation');
    await prefs.locator('#sigDeleteSection').waitFor({ state: 'detached' });
    await expectValue(sql, `SELECT COUNT(*) FROM property WHERE provider_no=${owner} AND name='provider_consult_signature'`, '0',
      'Delete left the provider_consult_signature row');
    h.assert(!canSeeFiles || !fs.existsSync(stampFile()), 'Delete left the stamp file on disk');
    const form = await openConsultForm('consult-without-stamp');
    await form.waitForLoadState('networkidle').catch(() => {});
    h.assert(await form.locator('#newSignature').inputValue() === 'true' && await form.locator('#signatureFrame').isVisible()
      && !(await form.locator('#signatureShow').isVisible()), 'Without a stamp the form did not offer the signature pad');
    await form.close();
  });

  const textSignature = `${marker} Dr Throwaway`;
  await s.step('Preferences ▸ Edit text signature (EnterSignature) stores the signature text', async () => {
    const link = prefs.locator('a[href$="/provider/ViewEditSignature"]');
    await revealAuditLink(prefs, link, 20000);
    const editor = await popup(prefs, link, 'text-signature');
    await editor.locator('#signature').fill(textSignature);
    await ui.clickAndAwaitReload(editor, editor.locator('input[type="submit"]'), { label: 'text signature Save' });
    h.assert(h.pathOnly(editor.url()).endsWith('/EnterSignature'), 'The text signature did not post to EnterSignature');
    h.assert(sql.value(`SELECT COALESCE(signature,'') FROM providerExt WHERE provider_no=${owner}`) === textSignature,
      'The text signature was not stored');
    await editor.close();
  });

  await s.step('a note signed in the chart is stamped with the saved text signature', async () => {
    const noteText = `${marker} signature consumer note`;
    const editor = chart.locator('#encMainDiv textarea[name="caseNote_note"]').first();
    await editor.click();
    await editor.fill(noteText);
    const closed = chart.waitForEvent('close', { timeout: 30000 });
    await chart.locator('#signSaveImg').first().click();
    await closed;
    await expectValue(sql, `SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient} AND signed=1
      AND note LIKE ${h.sqlString(`${noteText}%`)} AND note LIKE ${h.sqlString(`%Signed on%${textSignature}%`)}`, '1',
    'The signed note was not stamped with the provider\'s text signature');
  });
}

if (require.main === module) runWorkflow('provider-signature-contact', workflow, { openPatient: true, openMaster: false });
module.exports = { stampPng, workflow };
