#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Patient photo upload, replace, refusal and clear, driven from the chart.
 * User path: Schedule > Search > Master Record > E-Chart > patient photo in the right column
 *   (rightColumn.jsp, popupUploadPage) > Client Image Manager popup (casemgmt/ViewUploadimage)
 *   > choose file > Upload (ClientImage, method=saveImage) / Clear Photo (method=deleteImage).
 *   The Master Record renders no patient photo, so the chart header column is the only viewer.
 * Asserts: an owned JPEG is stored once in client_image (type jpeg, decoded bytes = uploaded),
 * the popup closes and reloads the chart, and the chart's photo request answers exactly the
 * uploaded bytes; a GIF replaces it in the same single row; ClientImage refuses GET for
 * deleteImage; a non-image upload is refused, keeps the manager open, leaves the stored photo
 * untouched and shows an error; Clear Photo (confirm) removes the row and the chart falls back
 * to the placeholder. (PNG is not accepted by design: the action allows GIF/JPEG only.)
 * Fixtures: the owned FAKE- patient and its client_image rows (images carry the run marker in a
 * comment segment); cleanup deletes only that patient's rows and verifies they are gone.
 * Implements docs/ui-tests/playwright-coverage-plan-2026.08.md chart section (patient-photo-upload).
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { waitForNavbars } = require('./echart-navbar-modules-playwright-checks');

// 2x2 solid images generated with ImageMagick (-strip); the marker is added per run below.
const JPEG = Buffer.from('/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAMCAgICAgMCAgIDAwMDBAYEBAQEBAgGBgUGCQgKCgkICQkKDA8MCgsOCwkJDREN'
  + 'Dg8QEBEQCgwSExIQEw8QEBD/2wBDAQMDAwQDBAgEBAgQCwkLEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBD/wAAR'
  + 'CAACAAIDAREAAhEBAxEB/8QAFAABAAAAAAAAAAAAAAAAAAAAB//EABQQAQAAAAAAAAAAAAAAAAAAAAD/xAAVAQEBAAAAAAAAAAAAAAAAAAAHCP/EABQRAQAA'
  + 'AAAAAAAAAAAAAAAAAAD/2gAMAwEAAhEDEQA/AAIZLjf/2Q==', 'base64');
const GIF = Buffer.from('R0lGODlhAgACAPAAAB4eyAAAACH5BAAAAAAALAAAAAACAAIAAAIChFEAOw==', 'base64');

/** JPEG with a COM segment right after SOI carrying the marker, so each run's bytes are unique. */
function markedJpeg(marker) {
  const text = Buffer.from(marker, 'latin1');
  const length = Buffer.alloc(2);
  length.writeUInt16BE(text.length + 2);
  return Buffer.concat([JPEG.subarray(0, 2), Buffer.from([0xff, 0xfe]), length, text, JPEG.subarray(2)]);
}

/** GIF89a with a comment extension inserted before the trailer. */
function markedGif(marker) {
  const text = Buffer.from(marker, 'latin1');
  return Buffer.concat([GIF.subarray(0, -1), Buffer.from([0x21, 0xfe, text.length]), text, Buffer.from([0x00, 0x3b])]);
}

const sha = bytes => require('node:crypto').createHash('sha256').update(bytes).digest('hex');
const photo = chart => chart.locator('#rightNavBar img[title="Click to upload a new photo."]');
const isPhotoRequest = patient => response => {
  const url = new URL(response.url());
  return url.pathname.endsWith('/imageRenderingServlet') && url.searchParams.get('source') === 'local_client'
    && url.searchParams.get('clientId') === patient;
};

// The chart releases its note lock with a sendBeacon on pagehide; when the image manager
// reloads the chart, Chromium aborts that ping as the document unloads. Consume only that
// entry, only from this reload (as encounter-header-i18n does); every other signal stays strict.
function consumeUnloadBeacon(recorder, since) {
  const added = recorder.requestFailures.splice(since);
  recorder.requestFailures.push(...added.filter(entry => !(entry.resourceType === 'ping'
    && entry.errorText === 'net::ERR_ABORTED' && new URL(entry.url).pathname.endsWith('/CaseManagementEntry'))));
}

async function workflow(s) {
  const { sql, patient } = s;
  const stored = () => sql.rows(`SELECT image_type,SHA2(FROM_BASE64(contents),256) FROM client_image
    WHERE demographic_no=${patient}`);
  s.cleanup(() => {
    sql.execute(`DELETE FROM client_image WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM client_image WHERE demographic_no=${patient}`) === '0',
      'The owned patient photo rows were not removed');
  });
  h.assert(stored().length === 0, 'The new owned patient already has a photo');
  let chart = await s.chart();

  async function openManager() {
    const manager = await s.popup(chart, photo(chart).first(), 'client-image-manager');
    h.assert(new URL(manager.url()).pathname.endsWith('/casemgmt/ViewUploadimage')
      && new URL(manager.url()).searchParams.get('demographicNo') === patient,
    'The photo opened the image manager for another patient');
    return manager;
  }

  // Upload closes the popup and reloads the chart; the reloaded chart fetches the photo.
  async function upload(file, manager) {
    if (!manager) manager = await openManager();
    await manager.locator('#clientImage').setInputFiles(file);
    const since = s.recorder.requestFailures.length;
    const [post, , , image] = await Promise.all([
      manager.waitForResponse(r => new URL(r.url()).pathname.endsWith('/ClientImage') && r.request().method() === 'POST'),
      manager.waitForEvent('close'),
      chart.waitForEvent('load'),
      chart.waitForResponse(isPhotoRequest(patient)),
      manager.locator('button[type="submit"]', { hasText: 'Upload' }).click(),
    ]);
    h.assert(post.status() === 200, `The photo upload answered HTTP ${post.status()}`);
    await waitForNavbars(chart, 20000);
    consumeUnloadBeacon(s.recorder, since);
    return image;
  }

  await s.step('Chart shows the placeholder photo that opens the Client Image Manager', async () => {
    await photo(chart).first().waitFor({ state: 'visible' });
    h.assert(await photo(chart).getAttribute('alt') === 'No_Id_Photo', 'A patient without a photo did not show the placeholder');
    const manager = await openManager();
    await manager.locator('button.btn-secondary', { hasText: 'Cancel' }).click();
    if (!manager.isClosed()) await manager.waitForEvent('close');
  });

  const jpeg = markedJpeg(s.marker);
  await s.step('Upload a JPEG: one client_image row with the exact bytes, rendered in the chart', async () => {
    const image = await upload({ name: 'photo.jpg', mimeType: 'image/jpeg', buffer: jpeg });
    await expectValue(sql, `SELECT CONCAT(COUNT(*),'|',MAX(image_type),'|',MAX(SHA2(FROM_BASE64(contents),256)))
      FROM client_image WHERE demographic_no=${patient}`, `1|jpeg|${sha(jpeg)}`, 'The JPEG was not stored exactly once as uploaded');
    h.assert(await photo(chart).getAttribute('id') === 'ci' && await photo(chart).getAttribute('alt') === 'id_photo',
      'The reloaded chart did not render the stored photo');
    h.assert(image.status() === 200 && /^image\/jpeg(;|$)/.test(image.headers()['content-type'] || ''), 'The chart photo is not served as a JPEG');
    h.assert(sha(await image.body()) === sha(jpeg), 'The chart photo bytes differ from the uploaded JPEG');
  });

  const gif = markedGif(s.marker);
  await s.step('Replace it with a GIF: the same single row now holds the GIF and the chart renders it', async () => {
    const image = await upload({ name: 'photo.gif', mimeType: 'image/gif', buffer: gif });
    await expectValue(sql, `SELECT CONCAT(COUNT(*),'|',MAX(image_type),'|',MAX(SHA2(FROM_BASE64(contents),256)))
      FROM client_image WHERE demographic_no=${patient}`, `1|gif|${sha(gif)}`, 'The GIF did not replace the stored photo');
    h.assert(image.status() === 200 && /^image\/gif(;|$)/.test(image.headers()['content-type'] || ''), 'The chart photo is not served as a GIF');
    h.assert(sha(await image.body()) === sha(gif), 'The chart photo bytes differ from the uploaded GIF');
  });

  let refused;
  await s.step('A non-image upload is refused: the manager stays open and the stored photo is unchanged', async () => {
    const before = JSON.stringify(stored());
    const manager = await openManager();
    await manager.locator('#clientImage').setInputFiles({
      name: 'notes<em>.txt', mimeType: 'text/plain', buffer: Buffer.from(`${s.marker} not an image\n`),
    });
    const [post] = await Promise.all([
      manager.waitForResponse(r => new URL(r.url()).pathname.endsWith('/ClientImage') && r.request().method() === 'POST'),
      manager.waitForEvent('load'),
      manager.locator('button[type="submit"]', { hasText: 'Upload' }).click(),
    ]);
    h.assert(post.status() === 200, `The refused upload answered HTTP ${post.status()}`);
    h.assert(!manager.isClosed() && await manager.locator('#clientImage').count() === 1,
      'The refused upload did not keep the image manager open for another try');
    h.assert(JSON.stringify(stored()) === before, 'The refused upload changed the stored photo');
    refused = manager;
  });

  await s.step('The refused upload explains the error and accepts a corrected image', async () => {
    // The multipart interceptor rejects the part (logged "Content-Type not allowed") and lands
    // on the "input" result; uploadimage.jsp promises to show that rejection.
    const error = refused.locator('.alert-danger');
    h.assert(await error.count() === 1 && (await error.innerText()).trim().length > 0,
      'The refused upload re-rendered the form with no error message');
    const message = await error.innerText();
    h.assert(message.includes('Content-Type not allowed') && message.includes('notes<em>.txt'),
      'The upload error did not explain which file type was rejected');
    h.assert(await error.locator('em').count() === 0, 'The rejected filename rendered as markup');
    const image = await upload({ name: 'corrected.jpg', mimeType: 'image/jpeg', buffer: jpeg }, refused);
    await expectValue(sql, `SELECT CONCAT(COUNT(*),'|',MAX(image_type),'|',MAX(SHA2(FROM_BASE64(contents),256)))
      FROM client_image WHERE demographic_no=${patient}`, `1|jpeg|${sha(jpeg)}`,
    'The corrected upload did not replace the photo exactly once');
    h.assert(image.status() === 200 && sha(await image.body()) === sha(jpeg),
      'The chart did not render the corrected upload');
  });

  await s.step('ClientImage refuses a GET deleteImage and keeps the stored photo', async () => {
    // ClientImage takes no patient parameter: Clear Photo posts only method=deleteImage and the
    // action deletes the photo of the session's clientId, which the manager page binds from its
    // demographicNo. Open (and cancel) the manager for the owned patient first so this GET, sent
    // with the same session cookies, targets exactly the photo the real form would delete.
    const manager = await openManager();
    await manager.locator('button.btn-secondary', { hasText: 'Cancel' }).click();
    if (!manager.isClosed()) await manager.waitForEvent('close');
    const response = await s.context.request.get(h.appUrl(s.config.baseUrl, '/ClientImage?method=deleteImage'),
      { maxRedirects: 0, failOnStatusCode: false });
    const status = response.status();
    await response.dispose();
    h.assert(sql.value(`SELECT COUNT(*) FROM client_image WHERE demographic_no=${patient}`) === '1',
      'A GET request deleted the patient photo (ClientImage mutates on GET)');
    h.assert(status === 405, `GET ClientImage deleteImage answered HTTP ${status}, expected 405`);
  });

  await s.step('Clear Photo asks to confirm, removes the row and the chart shows the placeholder', async () => {
    const manager = await openManager();
    const since = s.recorder.requestFailures.length;
    const dialogs = await h.withExpectedDialogs(manager, async () => {
      await Promise.all([
        manager.waitForEvent('close'),
        chart.waitForEvent('load'),
        manager.locator('button[type="submit"]', { hasText: 'Clear Photo' }).click(),
      ]);
    });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Clear Photo did not ask exactly once to confirm');
    await expectValue(sql, `SELECT COUNT(*) FROM client_image WHERE demographic_no=${patient}`, '0', 'Clear Photo left the photo row');
    await waitForNavbars(chart, 20000);
    consumeUnloadBeacon(s.recorder, since);
    h.assert(await photo(chart).getAttribute('alt') === 'No_Id_Photo', 'The chart still shows a photo after Clear Photo');
  });
}

if (require.main === module) runWorkflow('patient-photo-upload', workflow, { openPatient: true });
module.exports = { workflow };
