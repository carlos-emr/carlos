#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/* Saves JS-set image sources through the packaged WAF and reopens the stored HTML.
 * Owns its patient, template and revisions; must run exclusively with other checks.
 * Uses the common BASE_URL, TEST_*, CHROME_PATH and MYSQL_* harness contract.
 */
const h = require('./lib/playwright-harness');
const { clickAndAwaitReload } = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');

async function workflow(s) {
  const name = `${s.marker} image links`;
  const owner = `SELECT fid FROM eform WHERE form_name=${h.sqlString(name)}`;
  // Register before INSERT so a failure reading back its ID still cleans up.
  s.cleanup(() => {
    const revisions = `SELECT fdid FROM eform_data WHERE fid IN (${owner})`;
    s.sql.execute(`DELETE FROM EFormDocs WHERE fdid IN (${revisions});
      DELETE FROM eform_values WHERE fdid IN (${revisions});
      DELETE FROM eform_data WHERE fid IN (${owner});
      DELETE FROM eform WHERE form_name=${h.sqlString(name)}`);
    h.assert(s.sql.value(`SELECT COUNT(*) FROM eform WHERE form_name=${h.sqlString(name)}`) === '0',
      'Owned image template remains');
  });
  const html = '<html><head><title>Image fixture</title></head><body><form method="post">'
    + '<input name="clinicalNote" value="Synthetic image check">'
    + '<img id="signature" src=""><img id="letterhead" src="">'
    + '</form></body></html>';
  s.sql.execute(`INSERT INTO eform (form_name,file_name,subject,form_date,form_time,form_creator,status,
      form_html,showLatestFormOnly,patient_independent)
    VALUES (${h.sqlString(name)},'','',CURDATE(),CURTIME(),${h.sqlString(s.provider)},1,${h.sqlString(html)},0,0)`);
  const fid = s.sql.value(owner);
  h.assert(/^[1-9]\d*$/.test(fid), 'Owned image template was not created');
  const page = await s.context.newPage();
  await page.addInitScript(() => { window.close = () => {}; });
  const image = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=';
  const sources = { signature: image, letterhead: `${new URL(s.config.baseUrl).pathname}/images/favicon.ico` };

  await s.step('new eForm posts each image using carlos-image-link', async () => {
    await h.gotoApp(page, s.config.baseUrl, `/eform/efmformadd_data?fid=${fid}&demographic_no=${s.patient}`);
    await page.locator('#remoteSubmitButton').waitFor();
    await page.evaluate(values => {
      for (const [id, value] of Object.entries(values)) document.getElementById(id).setAttribute('src', value);
      appendImageInputs();
      appendImageInputs(); // refresh replaces placeholders rather than duplicating them.
    }, sources);
    const placeholders = await page.locator('input[name="carlos-image-link"]').evaluateAll(inputs =>
      inputs.map(input => JSON.parse(input.value)));
    h.assert(placeholders.length === 2, 'Refreshing image placeholders duplicated or lost an image');
    h.assert(placeholders.every(item => sources[item.id] === item.value), 'Image placeholders have the wrong sources');
    h.assert(await page.locator('input[name="openosp-image-link"]').count() === 0, 'The toolbar still submits the old field');
    const posted = page.waitForRequest(request => request.method() === 'POST'
      && new URL(request.url()).pathname.endsWith('/eform/addEForm'));
    await clickAndAwaitReload(page, page.locator('#remoteSubmitButton'), { timeout: 30000, label: 'image save' });
    const body = new URLSearchParams((await posted).postData());
    h.assert(body.getAll('carlos-image-link').length === 2 && !body.has('openosp-image-link'),
      'The actual save did not carry exactly two CARLOS image links');
    await h.assertNotErrorPage(page, 'image save');
  });

  let fdid;
  await s.step('image sources reach stored HTML without creating transport-field values', async () => {
    const rows = s.sql.rows(`SELECT fdid,form_data FROM eform_data WHERE fid=${fid} AND demographic_no=${s.patient}`);
    h.assert(rows.length === 1, 'Image save did not store exactly one revision');
    fdid = rows[0][0];
    h.assert(rows[0][1].includes(image), 'The JS-set signature was not stored in the eForm HTML');
    h.assert(rows[0][1].includes(sources.letterhead), 'The JS-set letterhead was not stored in the eForm HTML');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM eform_values WHERE fdid=${fdid}
      AND var_name IN ('carlos-image-link','openosp-image-link')`) === '0', 'Image transport fields leaked into eform_values');
  });

  await s.step('reopened eForm displays the stored signature', async () => {
    await h.gotoApp(page, s.config.baseUrl, `/eform/efmshowform_data?fdid=${fdid}`);
    await page.locator('#remoteSubmitButton').waitFor();
    h.assert(await page.locator('#signature').getAttribute('src') === image, 'Reopening lost the JS-set signature');
    h.assert(await page.locator('#letterhead').getAttribute('src') === sources.letterhead, 'Reopening lost the JS-set letterhead');
    h.assert(await page.locator('#signature').evaluate(img => img.complete && img.naturalWidth === 1),
      'The reopened signature image did not decode');
  });
}

if (require.main === module) runWorkflow('eform-image-links', workflow);
module.exports = { workflow };
