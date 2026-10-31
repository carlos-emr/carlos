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
 * Last (app-findings-log.md finding 156, fixed by #4546): with the photo managers of TWO patients
 * open in one login, Clear Photo and Upload in the first must act on the first patient and keep the
 * second's photo. Each manager posts the patient it was opened for (demographicNo); ClientImage acts
 * on that patient only, never on a session-wide clientId, refuses a request that names none, and
 * refuses a login locked out of the named patient (|o| on _demographic$N and _eChart$N) while the
 * same login may still clear an unlocked patient's photo (control).
 * Fixtures: the owned FAKE- patient and its client_image rows (images carry the run marker in a
 * comment segment), plus for finding 156 a second FAKE- patient (same last name, first name
 * PhotoBravo) holding one photo and one throwaway doctor login locked out of it
 * (lib/authz-read-fixture.js); cleanup deletes only those rows and verifies they are gone.
 * Implements docs/ui-tests/playwright-coverage-plan-2026.08.md chart section (patient-photo-upload).
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { waitForNavbars } = require('./echart-navbar-modules-playwright-checks');
const { authzReadFixture } = require('./lib/authz-read-fixture');
const { signIn } = require('./lib/authz-read-probe');

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

const CLEAR_STEP = 'Clear Photo in the first photo manager keeps the photo of the patient whose manager was opened second';
const sha = bytes => require('node:crypto').createHash('sha256').update(bytes).digest('hex');
const photo = chart => chart.locator('#rightNavBar img[title="Click to upload a new photo."]');
const isClientImagePost = r => new URL(r.url()).pathname.endsWith('/ClientImage') && r.request().method() === 'POST';
/**
 * The patient a manager's form posts: the value of the one hidden demographicNo in the form carrying the given
 * method, or null when that form posts none (the page before #4546 posted no patient at all). The form is rendered
 * by the server, so it is complete once the manager has loaded; no wait is needed.
 */
async function postedPatient(manager, method) {
  const field = manager.locator(`form:has(input[name="method"][value="${method}"]) input[name="demographicNo"]`);
  return await field.count() === 1 ? field.inputValue() : null;
}
/** Accept every dialog on a page the strict recorder does not wire (a throwaway login's); returns their types. */
function acceptDialogs(page) {
  const types = [];
  page.on('dialog', dialog => { types.push(dialog.type()); dialog.accept().catch(() => {}); });
  return types;
}
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
    // Clear Photo posts method=deleteImage with the manager's demographicNo; this GET names the
    // owned patient the same way, so it targets exactly the photo the real form would delete.
    const manager = await openManager();
    h.assert(await postedPatient(manager, 'deleteImage') === patient,
      'Clear Photo does not post the patient the manager was opened for');
    await manager.locator('button.btn-secondary', { hasText: 'Cancel' }).click();
    if (!manager.isClosed()) await manager.waitForEvent('close');
    const response = await s.context.request.get(
      h.appUrl(s.config.baseUrl, `/ClientImage?method=deleteImage&demographicNo=${patient}`),
      { maxRedirects: 0, failOnStatusCode: false });
    const status = response.status();
    await response.dispose();
    h.assert(sql.value(`SELECT COUNT(*) FROM client_image WHERE demographic_no=${patient}`) === '1',
      'A GET request deleted the patient photo (ClientImage mutates on GET)');
    h.assert(status === 405, `GET ClientImage deleteImage answered HTTP ${status}, expected 405`);
  });

  await s.step('Clear Photo asks to confirm, removes cached and legacy duplicate photos and restores the placeholder', async () => {
    // Older installations may have multiple rows. Clearing a photo must not reveal an older one.
    sql.execute(`INSERT INTO client_image (demographic_no,image_type,contents,update_date)
      SELECT demographic_no,image_type,contents,DATE_SUB(update_date,INTERVAL 1 DAY)
      FROM client_image WHERE demographic_no=${patient}`);
    h.assert(stored().length === 2, 'The owned legacy duplicate photo was not created');
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

  // ---- Finding 156 (#4546): two photo managers open in one login -----------------------------------
  // The manager page used to store its demographicNo as the session's clientId and ClientImage read
  // it back, so two managers shared one target: the one opened last. Each form now posts its own
  // demographicNo, and ClientImage checks the login's access to that patient before acting.
  const { provider, marker } = s;
  const q = h.sqlString;
  const bPhoto = markedJpeg(`${marker}-B`);
  let patientB;
  const storedFor = demo => sql.rows(`SELECT image_type,SHA2(FROM_BASE64(contents),256) FROM client_image
    WHERE demographic_no=${demo}`);
  let managerA;
  let managerB;
  await s.step('two photo managers are open for two patients that each hold a photo', async () => {
    patientB = sql.value(`INSERT INTO demographic (last_name, first_name, year_of_birth, month_of_birth,
      date_of_birth, sex, patient_status, provider_no, hc_type, province, roster_status, lastUpdateDate)
      VALUES (${q(marker)}, 'PhotoBravo', '1975', '03', '04', 'M', 'AC', ${q(provider)}, 'ON', 'ON', 'NR', NOW());
      SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(patientB), 'The second patient fixture was not created');
    s.cleanup(() => {
      h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patientB}
        AND last_name=${q(marker)} AND first_name='PhotoBravo'`) === '1', 'The second patient fixture ownership changed');
      sql.execute(`DELETE FROM client_image WHERE demographic_no=${patientB};
        DELETE FROM demographicExt WHERE demographic_no=${patientB};
        DELETE FROM demographicArchive WHERE demographic_no=${patientB};
        DELETE FROM demographic WHERE demographic_no=${patientB} AND last_name=${q(marker)}`);
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM demographic WHERE demographic_no=${patientB})
        + (SELECT COUNT(*) FROM client_image WHERE demographic_no=${patientB})`) === '0',
      'The second patient fixture was not removed');
    });
    sql.execute(`INSERT INTO client_image (demographic_no,image_type,contents,update_date)
      VALUES (${patientB},'jpeg',${q(bPhoto.toString('base64'))},NOW())`);
    h.assert(JSON.stringify(storedFor(patientB)) === JSON.stringify([['jpeg', sha(bPhoto)]]),
      'The second patient photo fixture was not stored as seeded');
    // The first patient's photo goes through the manager, as a clinician uploads one.
    await upload({ name: 'photo.jpg', mimeType: 'image/jpeg', buffer: jpeg });
    h.assert(JSON.stringify(storedFor(patient)) === JSON.stringify([['jpeg', sha(jpeg)]]),
      'The first patient photo was not stored as uploaded');
    managerA = await openManager();
    // The second manager is opened by the address the chart's photo opens (rightColumn.jsp
    // popupUploadPage), in its own window, after the first: it is the last one opened.
    managerB = await s.context.newPage();
    await h.gotoApp(managerB, s.config.baseUrl, `/casemgmt/ViewUploadimage?demographicNo=${patientB}`);
    h.assert(new URL(managerB.url()).searchParams.get('demographicNo') === patientB
      && await managerB.locator('button[type="submit"]', { hasText: 'Clear Photo' }).count() === 1,
    'The second photo manager did not open for the second patient');
    h.assert(await managerA.locator('button[type="submit"]', { hasText: 'Clear Photo' }).count() === 1
      && new URL(managerA.url()).searchParams.get('demographicNo') === patient,
    'The first photo manager is not bound to the first patient');
    for (const method of ['saveImage', 'deleteImage']) {
      h.assert(await postedPatient(managerA, method) === patient && await postedPatient(managerB, method) === patientB,
      `The ${method} form of each photo manager does not post its own patient`);
    }
  });

  // The action of the finding-156 step below, with its controls: Clear Photo is pressed in the FIRST manager and asks
  // exactly once to confirm, the manager closes and the chart reloads. What it did to each patient's photo is judged by
  // the next two steps, so a confirm that is not asked (or a click that does nothing) fails here, not there.
  await s.step('Clear Photo in the first photo manager asks once to confirm, closes the manager and reloads the chart', async () => {
    const since = s.recorder.requestFailures.length;
    const dialogs = await h.withExpectedDialogs(managerA, async () => {
      await Promise.all([
        managerA.waitForEvent('close'),
        chart.waitForEvent('load'),
        managerA.locator('button[type="submit"]', { hasText: 'Clear Photo' }).click(),
      ]);
    });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Clear Photo did not ask exactly once to confirm');
    await waitForNavbars(chart, 20000);
    consumeUnloadBeacon(s.recorder, since);
  });

  // Holds only the assertion finding 156 broke (the step name is the one the suite manifest pinned).
  await s.step(CLEAR_STEP, async () => {
    h.assert(JSON.stringify(storedFor(patientB)) === JSON.stringify([['jpeg', sha(bPhoto)]]),
      'Clear Photo in the first photo manager deleted the photo of the patient whose manager was opened second '
      + '(ClientImage acts on the session-wide clientId)');
  });

  // The other half of the same behaviour, in its own step so the one above holds only what finding 156
  // broke: the first manager's Clear Photo must also reach the first patient.
  await s.step('Clear Photo in the first photo manager clears the first patient photo', async () => {
    await expectValue(sql, `SELECT COUNT(*) FROM client_image WHERE demographic_no=${patient}`, '0',
      'Clear Photo in the first manager did not clear the first patient photo');
  });

  // The upload half of finding 156: the second patient's manager is (re)opened LAST, after the first patient's.
  const aPhoto = markedJpeg(`${marker}-A2`);
  await s.step('Upload in the first photo manager stores the photo for the first patient and keeps the second', async () => {
    const manager = await openManager();
    await h.gotoApp(managerB, s.config.baseUrl, `/casemgmt/ViewUploadimage?demographicNo=${patientB}`);
    h.assert(await postedPatient(managerB, 'saveImage') === patientB,
      'The reopened second photo manager does not post the second patient');
    await upload({ name: 'photo.jpg', mimeType: 'image/jpeg', buffer: aPhoto }, manager);
    await expectValue(sql, `SELECT CONCAT(COUNT(*),'|',MAX(image_type),'|',MAX(SHA2(FROM_BASE64(contents),256)))
      FROM client_image WHERE demographic_no=${patient}`, `1|jpeg|${sha(aPhoto)}`,
    'Upload in the first photo manager did not store the photo for the first patient');
    h.assert(JSON.stringify(storedFor(patientB)) === JSON.stringify([['jpeg', sha(bPhoto)]]),
      'Upload in the first photo manager replaced the photo of the patient whose manager was opened second');
  });

  await s.step('A photo change that names no patient changes no photo, whichever manager was opened last', async () => {
    // The second patient's manager was the last one opened; a Clear Photo POST without demographicNo
    // used to fall back to the clientId that page left in the session. Sent with the login's own token.
    const tokenField = managerB.locator('form input[name="CSRF-TOKEN"]').first();
    await tokenField.waitFor({ state: 'attached' });
    const token = await tokenField.inputValue();
    h.assert(token, 'The second photo manager carries no CSRF token');
    const before = sql.value(`SELECT COUNT(*) FROM client_image WHERE demographic_no IN (${patient},${patientB})`);
    const response = await s.context.request.post(h.appUrl(s.config.baseUrl, '/ClientImage'), {
      form: { method: 'deleteImage', 'CSRF-TOKEN': token }, maxRedirects: 0, failOnStatusCode: false,
    });
    const body = await response.text();
    await response.dispose();
    h.assert(!/opener\.location\.reload/.test(body), 'A Clear Photo POST that names no patient was answered as a success');
    h.assert(sql.value(`SELECT COUNT(*) FROM client_image WHERE demographic_no IN (${patient},${patientB})`) === before
      && JSON.stringify(storedFor(patientB)) === JSON.stringify([['jpeg', sha(bPhoto)]]),
    'A Clear Photo POST that names no patient deleted a photo (ClientImage fell back to the session clientId)');
    await managerB.close();
  });

  // Access to the posted patient: a doctor login locked out of the second patient (|o| on _demographic$N and
  // _eChart$N) opens that patient's manager (the page shows no patient data) but may not clear the photo.
  const fixture = authzReadFixture({ sql, marker, provider, testUser: s.config.testUser });
  s.cleanup(() => fixture.cleanup());
  let doctor;
  await s.step('a doctor login locked out of the second patient cannot clear that patient photo', async () => {
    const login = fixture.addLogin('doctor');
    h.assert(fixture.lockPatient(login, patientB).length === 2, 'The patient lock rows were not written');
    doctor = await signIn(s, login);
    const deniedPage = await doctor.context.newPage();
    const before = sql.value(`SELECT COUNT(*) FROM client_image WHERE demographic_no=${patientB}`);
    const view = await h.gotoApp(deniedPage, s.config.baseUrl, `/casemgmt/ViewUploadimage?demographicNo=${patientB}`);
    await h.assertRefused(s, {response: view, table: 'client_image', where: `demographic_no=${patientB}`, before,
      label: 'Opening the locked patient photo manager'});
    await deniedPage.close();

    // The clinical view gate now refuses the manager itself. Capture real forms
    // from the same doctor's permitted patient, then replay only their patient
    // parameter. Requiring a forbidden page to render a form never reaches the
    // mutation boundary and used to misreport this as a missing fixture (#5378).
    const manager = await doctor.context.newPage();
    await h.gotoApp(manager, s.config.baseUrl, `/casemgmt/ViewUploadimage?demographicNo=${patient}`);
    for (const method of ['deleteImage', 'saveImage']) {
      h.assert(await postedPatient(manager, method) === patient,
        'The allowed control manager must post its own patient');
      if (method === 'saveImage') await manager.locator('#clientImage').setInputFiles({
        name: 'locked-patient-probe.jpg', mimeType: 'image/jpeg', buffer: markedJpeg(marker),
      });
      const [post] = await Promise.all([
        manager.waitForResponse(isClientImagePost),
        manager.evaluate(async ({method, patientB}) => { // nosemgrep: javascript.playwright.security.audit.page-evaluate -- intentional denied-target replay of the real permitted form
          const form = [...document.forms].find(form => form.querySelector('input[name="method"]')?.value === method);
          if (!form) throw new Error('The permitted photo manager form is missing');
          const data = new FormData(form);
          data.set('demographicNo', patientB);
          const response = await fetch(form.action, {method: 'POST', credentials: 'same-origin', body: data});
          await response.arrayBuffer();
        }, {method, patientB}),
      ]);
      await h.assertRefused(s, {response: post, table: 'client_image', where: `demographic_no=${patientB}`, before,
        label: `${method} by a login locked out of the patient`});
      h.assert(JSON.stringify(storedFor(patientB)) === JSON.stringify([['jpeg', sha(bPhoto)]]),
        `${method} by the locked doctor changed the patient's photo`);
    }
    await manager.close();
  });

  await s.step('the same doctor login clears the photo of a patient it is not locked out of (control)', async () => {
    const manager = await doctor.context.newPage();
    await h.gotoApp(manager, s.config.baseUrl, `/casemgmt/ViewUploadimage?demographicNo=${patient}`);
    // Opened by address, this manager has no opener to reload and is not closable by script, so the
    // control reads the answer and the table rather than waiting for the popup to close.
    acceptDialogs(manager);
    const [post] = await Promise.all([
      manager.waitForResponse(isClientImagePost),
      manager.locator('button[type="submit"]', { hasText: 'Clear Photo' }).click(),
    ]);
    h.assert(post.status() === 200, `Clear Photo by the unlocked doctor login answered HTTP ${post.status()}`);
    await expectValue(sql, `SELECT COUNT(*) FROM client_image WHERE demographic_no=${patient}`, '0',
      'The doctor login could not clear the photo of a patient it is not locked out of');
    h.assert(JSON.stringify(storedFor(patientB)) === JSON.stringify([['jpeg', sha(bPhoto)]]),
      'Clear Photo of the first patient by the doctor login changed the second patient photo');
    await doctor.context.close();
  });
}

if (require.main === module) runWorkflow('patient-photo-upload', workflow, { openPatient: true });
module.exports = { workflow };
