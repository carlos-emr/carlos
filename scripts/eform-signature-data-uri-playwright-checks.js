#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Saves a drawn eForm signature through the packaged front door (#3552).
 *
 * Signature pads keep data URIs in the eForm's own, author-named fields. The page moves each one
 * into the fixed carlos-data-uri argument as the request is built (leaving
 * carlos-data-uri:<nonce>:<n> in the field, the nonce posted as carlos-data-uri-nonce),
 * AddEForm2Action restores it before storing, and WAF rule 1040 exempts only that argument. This check draws a real signature on a canvas pad, saves it with the toolbar, and
 * verifies:
 *   - the request carries references and the carried values, never a raw signature field;
 *   - eform_values and the reopened form hold the signature itself, never a reference, and typed
 *     text that only reads like a reference is stored as typed;
 *   - a carried value that is not an image is refused (400) before storage, and the same rendering
 *     can still save once afterwards;
 *   - through the front door (EXPECT_FRONT_DOOR=true, or an nginx Server header), a data:text/html
 *     value or an uncarried signature in an ordinary field is answered with the WAF's 403. Without a
 *     front door (devcontainer) those two requests are not sent.
 *
 * Owns its patient, template and revisions (cleanup verifies removal); run exclusively.
 * Uses the common BASE_URL, TEST_*, CHROME_PATH and MYSQL_* harness contract.
 */
const h = require('./lib/playwright-harness');
const { clickAndAwaitReload } = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');

const JSIGNATURE = 'data:image/jsignature;base30,3E13Z5Y5_1O24Z66_1O1Z3';
const HTML_PAYLOAD = 'data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==';
const SVG_PAYLOAD = 'data:image/svg+xml;base64,PHN2ZyBvbmxvYWQ9YWxlcnQoMSk+';
const NONCE = '0123456789abcdef0123456789abcdef';
const TYPED = 'carlos-data-uri:0';
const expectFrontDoor = /^(1|true|yes)$/i.test(process.env.EXPECT_FRONT_DOOR || '');

// The template's own pad: a canvas whose mouseup copies toDataURL() into two hidden fields (the
// second mirrors it, as "slave" pads do), beside a jSignature-style field and an ordinary note.
const TEMPLATE = '<html><head><title>Signature fixture</title></head><body><form method="post">'
  + '<input name="clinicalNote" id="clinicalNote" value="Synthetic signature check">'
  + '<input name="typedNote" id="typedNote" value="' + TYPED + '">'
  + '<canvas id="pad" width="300" height="80" style="border:1px solid #000;touch-action:none"></canvas>'
  + '<input type="hidden" name="SignaturePad" id="SignaturePad">'
  + '<input type="hidden" name="MirrorPad" id="MirrorPad">'
  + '<input type="hidden" name="StoreSignature1" id="StoreSignature1">'
  + '<script>(function(){var c=document.getElementById("pad"),g=c.getContext("2d"),down=false;'
  + 'c.addEventListener("mousedown",function(e){down=true;g.beginPath();g.moveTo(e.offsetX,e.offsetY);});'
  + 'c.addEventListener("mousemove",function(e){if(down){g.lineTo(e.offsetX,e.offsetY);g.stroke();}});'
  + 'c.addEventListener("mouseup",function(){down=false;var v=c.toDataURL();'
  + 'document.getElementById("SignaturePad").value=v;document.getElementById("MirrorPad").value=v;});'
  + '})();</script>'
  + '</form></body></html>';

/**
 * Posts the rendered eForm as the browser would, but serialized by hand so the page's carrier
 * never runs: what is sent is exactly the fields plus {@code overrides}. Returns status and body.
 */
async function postForm(page, overrides) {
  return page.evaluate(async (replace) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- the page function is a literal; the overrides are this file's constants and the drawn PNG, structured-cloned rather than interpolated into page script
    const form = Array.from(document.forms).find((f) => /\/eform\/addEForm/.test(f.getAttribute('action') || ''));
    if (!form) throw new Error('no eForm save form on the page');
    const body = new URLSearchParams();
    for (const control of form.elements) {
      if (!control.name || control.disabled || Object.prototype.hasOwnProperty.call(replace, control.name)) continue;
      if (/^(?:button|file|image|reset|submit)$/i.test(control.type)) continue;
      if (/^(?:checkbox|radio)$/i.test(control.type) && !control.checked) continue;
      body.append(control.name, control.value);
    }
    for (const [name, value] of Object.entries(replace)) body.append(name, value);
    const token = form.querySelector('input[name="CSRF-TOKEN"]');
    const response = await fetch(new URL(form.getAttribute('action'), document.baseURI), {
      method: 'POST',
      credentials: 'same-origin',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        ...(token && token.value ? { 'CSRF-TOKEN': token.value } : {}),
      },
      body: body.toString(),
      redirect: 'manual',
    });
    return { status: response.status, server: response.headers.get('server') || '', text: (await response.text()).slice(0, 20000) };
  }, overrides);
}

async function workflow(s) {
  const name = `${s.marker} signature data URIs`;
  s.cleanup(() => {
    s.sql.execute(`DELETE FROM EFormDocs WHERE fdid IN
      (SELECT fdid FROM eform_data WHERE fid IN (SELECT fid FROM eform WHERE form_name=?))`, [name]);
    s.sql.execute(`DELETE FROM eform_values WHERE fdid IN
      (SELECT fdid FROM eform_data WHERE fid IN (SELECT fid FROM eform WHERE form_name=?))`, [name]);
    s.sql.execute('DELETE FROM eform_data WHERE fid IN (SELECT fid FROM eform WHERE form_name=?)', [name]);
    s.sql.execute('DELETE FROM eform WHERE form_name=?', [name]);
    h.assert(s.sql.value('SELECT COUNT(*) FROM eform WHERE form_name=?', [name]) === '0',
      'Owned signature template remains');
  });
  s.sql.execute(`INSERT INTO eform (form_name,file_name,subject,form_date,form_time,form_creator,status,
      form_html,showLatestFormOnly,patient_independent)
    VALUES (?,'','',CURDATE(),CURTIME(),?,1,?,0,0)`, [name, s.provider, TEMPLATE]);
  const fid = s.sql.value('SELECT fid FROM eform WHERE form_name=?', [name]);
  h.assert(/^[1-9]\d*$/.test(fid), 'Owned signature template was not created');
  const revisions = () => Number(s.sql.value('SELECT COUNT(*) FROM eform_data WHERE fid=? AND demographic_no=?',
    [fid, s.patient]));

  const page = await s.context.newPage();
  await page.addInitScript(() => { window.close = () => {}; });
  const openNew = async () => {
    await h.gotoApp(page, s.config.baseUrl, `/eform/efmformadd_data?fid=${fid}&demographic_no=${s.patient}`);
    await page.locator('#remoteSubmitButton').waitFor();
  };
  const draw = async () => {
    const box = await page.locator('#pad').boundingBox();
    h.assert(box, 'The signature pad is not visible');
    await page.mouse.move(box.x + 10, box.y + 60);
    await page.mouse.down();
    for (const [dx, dy] of [[60, 15], [110, 55], [170, 20], [230, 50], [285, 25]]) {
      await page.mouse.move(box.x + dx, box.y + dy, { steps: 4 });
    }
    await page.mouse.up();
    await page.evaluate((value) => { document.getElementById('StoreSignature1').value = value; }, JSIGNATURE); // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- literal page function, module constant argument
    const png = await page.locator('#SignaturePad').inputValue();
    h.assert(/^data:image\/png;base64,[A-Za-z0-9+/]+={0,2}$/.test(png) && png.length > 500,
      'Drawing did not write a PNG data URI into the template field');
    return png;
  };

  let png;
  await s.step('an invalid carried value is refused before storage', async () => {
    await openNew();
    png = await draw();
    const before = revisions();
    const ref = `carlos-data-uri:${NONCE}:0`;
    const refused = await postForm(page, { SignaturePad: ref, MirrorPad: ref, StoreSignature1: ref,
      'carlos-data-uri': SVG_PAYLOAD, 'carlos-data-uri-nonce': NONCE });
    h.assert(refused.status === 400, `An SVG carried value was answered ${refused.status}, not 400`);
    h.assert(/signature or drawing on it could not be read/.test(refused.text),
      'The refusal did not tell the clinician the signature could not be read');
    h.assert(revisions() === before, 'A refused carried value still stored a revision');
  });

  let fdid;
  await s.step('the same rendering then saves the drawn signature with the toolbar', async () => {
    const [posted, answered] = await Promise.all([
      page.waitForRequest((request) => request.method() === 'POST'
        && new URL(request.url()).pathname.endsWith('/eform/addEForm')),
      page.waitForResponse((response) => response.request().method() === 'POST'
        && new URL(response.url()).pathname.endsWith('/eform/addEForm')),
      clickAndAwaitReload(page, page.locator('#remoteSubmitButton'), { timeout: 30000, label: 'signature save' }),
    ]);
    h.assert(answered.status() < 400, `The signature save was answered ${answered.status()}`);
    const body = new URLSearchParams(posted.postData());
    const nonces = body.getAll('carlos-data-uri-nonce');
    h.assert(nonces.length === 1 && /^[0-9a-f]{32}$/.test(nonces[0]), 'The save did not post one nonce');
    const ref = (index) => `carlos-data-uri:${nonces[0]}:${index}`;
    h.assert(body.get('SignaturePad') === ref(0) && body.get('MirrorPad') === ref(0)
      && body.get('StoreSignature1') === ref(1), 'The save did not post references in the signature fields');
    h.assert(body.get('typedNote') === TYPED, 'Typed text that reads like a reference was rewritten');
    const carried = body.getAll('carlos-data-uri');
    h.assert(carried.length === 2 && carried[0] === png && carried[1] === JSIGNATURE,
      'The save did not carry the PNG and jSignature values once each');
    h.assert(body.get('clinicalNote') === 'Synthetic signature check', 'The ordinary field changed');
    await h.assertNotErrorPage(page, 'signature save');
    const rows = s.sql.rows('SELECT fdid FROM eform_data WHERE fid=? AND demographic_no=?', [fid, s.patient]);
    h.assert(rows.length === 1, `Expected exactly one stored revision, found ${rows.length}`);
    fdid = rows[0][0];
  });

  await s.step('the stored values are the signatures themselves', async () => {
    const value = (field) => s.sql.value('SELECT var_value FROM eform_values WHERE fdid=? AND var_name=?', [fdid, field]);
    h.assert(value('SignaturePad') === png, 'eform_values does not hold the drawn PNG');
    h.assert(value('MirrorPad') === png, 'eform_values does not hold the mirrored PNG');
    h.assert(value('StoreSignature1') === JSIGNATURE, 'eform_values does not hold the jSignature strokes');
    h.assert(value('typedNote') === TYPED, 'Typed text that reads like a reference was not stored as typed');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM eform_values WHERE fdid=?
      AND (var_name IN ('carlos-data-uri','carlos-data-uri-nonce') OR var_value LIKE 'carlos-data-uri:%:%')`,
    [fdid]) === '0', 'The carrier, its nonce or a reference leaked into eform_values');
    h.assert(s.sql.value('SELECT COUNT(*) FROM eform_data WHERE fdid=? AND form_data REGEXP ?',
      [fdid, 'carlos-data-uri:[0-9a-f]{32}:']) === '0', 'A reference leaked into the stored eForm HTML');
  });

  await s.step('the reopened eForm holds the signature', async () => {
    await h.gotoApp(page, s.config.baseUrl, `/eform/efmshowform_data?fdid=${fdid}`);
    await page.locator('#remoteSubmitButton').waitFor();
    h.assert(await page.locator('#SignaturePad').inputValue() === png, 'Reopening lost the drawn signature');
    h.assert(await page.locator('#StoreSignature1').inputValue() === JSIGNATURE, 'Reopening lost the jSignature strokes');
  });

  await s.step('the front door scores data URIs in ordinary fields', async () => {
    await openNew();
    const probe = await postForm(page, { clinicalNote: HTML_PAYLOAD });
    const frontDoor = expectFrontDoor || /nginx/i.test(probe.server);
    if (!frontDoor) {
      // No WAF in front of this app (devcontainer): the request reached the application.
      console.log('NOTE no packaged front door detected; WAF assertions not applicable here');
      return;
    }
    h.assert(probe.status === 403 && /nginx/i.test(probe.server),
      `data:text/html in an ordinary field was answered ${probe.status} (server '${probe.server}'), not the WAF's 403`);
    const uncarried = await postForm(page, { StoreSignature1: JSIGNATURE });
    h.assert(uncarried.status === 403 && /nginx/i.test(uncarried.server),
      `An uncarried signature in an author-named field was answered ${uncarried.status}, not the WAF's 403`);
    h.assert(revisions() === 1, 'A WAF-blocked request stored a revision');
  });
}

if (require.main === module) runWorkflow('eform-signature-data-uri', workflow);
module.exports = { workflow };
