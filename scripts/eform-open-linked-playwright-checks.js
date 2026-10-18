#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// eform-open-linked: an eForm button that opens another eForm and remembers the copy it opened
// (oscarOPEN="<eForm name>"; the saved opener keeps the linked copy's id in the button as link$eform).
// User path: E-Chart ▸ eForms "+" (efmformslistadd) ▸ the opener eForm ▸
//   1. its oscarOPEN button opens a new copy of the linked eForm for the same patient
//      (eform/efmformadd_data?fid=<linked>&demographic_no=<patient>, with an eform_link; the link's format is
//      internal, so only its presence is checked);
//   2. Submit the linked copy, then Submit the opener: the opener's saved copy keeps the linked copy's id in
//      the button (link$eform="<fdid>");
//   3. E-Chart ▸ the patient's eForm list (efmpatientformlist) ▸ a saved opener whose button holds
//      link$eform="<fdid>": the button opens that saved copy (eform/efmshowform_data?fdid=<fdid>), not a new one.
// The button is set up by EForm.setOscarOPEN ▸ EFormLoader.getOpenEform; the linked copy's save puts its id in
// the session under the eform_link key (AddEForm2Action), and the opener's save writes it into the button
// (EForm.setOpenerValues). Each part breaks on its own, so all three run first and print their outcome, and
// the steps then report them in order: step 1 reads the URL the button opens rather than following a broken
// one; step 2 opens the linked eForm at the route and query that button names; step 3 seeds its saved copies.
// No other check opens a linked eForm (eform-test-pattern only checks that the oscarOPEN attribute survives).
// Fixtures: the owned FAKE- patient; two marker-named eForms in the clinic's eForm library (status 1); for
// step 3, a saved copy of each seeded by SQL. Cleanup deletes the eForms and every copy saved from them, and
// asserts none remain. The eForm library is clinic-wide (EXCLUSIVE).
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const q = h.sqlString;

// The URL an oscarOPEN button opens: its onclick is window.open('<url>').
async function buttonTarget(page, button) {
  const onclick = (await button.getAttribute('onclick')) || '';
  const match = onclick.match(/window\.open\('([^']+)'\)/);
  h.assert(match, `The oscarOPEN button has no window.open target: ${JSON.stringify(onclick)}`);
  return new URL(match[1], page.url());
}

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const linkedName = `${marker} linked`;
  const openerName = `${marker} opener`;
  const formNames = [linkedName, openerName].map(q).join(',');
  s.cleanup(() => {
    sql.execute(`DELETE FROM eform_values WHERE fdid IN (SELECT fdid FROM eform_data WHERE form_name IN (${formNames}));
      DELETE FROM eform_data WHERE form_name IN (${formNames}); DELETE FROM eform WHERE form_name IN (${formNames})`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM eform WHERE form_name IN (${formNames}))
      + (SELECT COUNT(*) FROM eform_data WHERE form_name IN (${formNames}))`) === '0', 'Owned eForms or their saved copies remain');
  });
  const page = (name, body) => `<html><head><title>${name}</title></head><body><form method="post" action="" name="FormName" id="FormName">`
    + `${body}<input type="submit" value="Submit" id="SubmitButton"></form></body></html>`;
  const linkedBody = note => `<input type="text" name="note" id="note"${note ? ` value="${note}"` : ''}>`;
  const openerBody = link => '<input type="text" name="note" id="note">'
    + `<input type="button" name="openLinked" id="openLinked" value="Open linked" oscarOPEN="${linkedName}"${link ? ` link$eform="${link}"` : ''}>`;
  const eform = (name, html) => {
    const fid = sql.value(`INSERT INTO eform(form_name,file_name,subject,form_date,form_time,form_creator,
        status,form_html,showLatestFormOnly,patient_independent,roleType,restrictToProgram,stable)
      VALUES(${q(name)},'',${q(name)},CURDATE(),CURTIME(),${q(provider)},1,${q(html)},0,0,'',0,1); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(fid || ''), `The ${name} eForm was not created`);
    return fid;
  };
  const linkedFid = eform(linkedName, page(linkedName, linkedBody()));
  const openerFid = eform(openerName, page(openerName, openerBody()));
  const latestCopy = fid => sql.value(`SELECT IFNULL(MAX(fdid),'') FROM eform_data WHERE fid=${fid} AND demographic_no=${patient} AND status=1`);

  const outcomes = [];
  const attempt = async (label, body) => {
    let error = null;
    try { await body(); } catch (caught) { error = caught; }
    outcomes.push({ label, error });
    console.log(`  ${error ? 'BAD' : 'OK '} eform-open-linked: ${label}${error ? ` -> ${error.message}` : ''}`);
  };

  const chart = await s.chart();
  const list = await s.popup(chart, chart.locator('a[onclick*="efmformslistadd"]').first(), 'eform-add-list');
  const opener = await s.popup(list, list.locator(`[onclick*="efmformadd_data?fid=${openerFid}&"]`).first(), 'eform-opener');
  await opener.locator('#openLinked').waitFor({ state: 'visible' });
  const opened = await buttonTarget(opener, opener.locator('#openLinked'));

  await attempt('the oscarOPEN button opens a new copy of the linked eForm for the patient', async () => {
    const shown = { path: opened.pathname, fid: opened.searchParams.get('fid'), patient: opened.searchParams.get('demographic_no'),
      link: opened.searchParams.get('eform_link') };
    h.assert(shown.path.endsWith('/eform/efmformadd_data') && shown.fid === linkedFid && shown.patient === patient
      && Boolean(shown.link), `The oscarOPEN button opens ${JSON.stringify(shown)}`);
    const linked = await s.popup(opener, opener.locator('#openLinked'), 'eform-linked');
    await linked.locator('#remoteSubmitButton').waitFor({ state: 'visible' });
    await linked.close();
  });

  await attempt('saving the linked copy and then the opener keeps the linked copy\'s id in the button', async () => {
    const linked = await s.context.newPage();
    await h.gotoApp(linked, s.config.baseUrl, `/eform/efmformadd_data${opened.search}`);
    await linked.locator('#note').fill(`${marker} linked note`);
    await linked.locator('#remoteSubmitButton').click();
    await expectValue(sql, `SELECT COUNT(*) FROM eform_data WHERE fid=${linkedFid} AND demographic_no=${patient} AND status=1`, '1',
      'Submit did not save the linked copy');
    const linkedFdid = latestCopy(linkedFid);
    await opener.locator('#note').fill(`${marker} opener note`);
    await opener.locator('#remoteSubmitButton').click();
    await expectValue(sql, `SELECT COUNT(*) FROM eform_data WHERE fid=${openerFid} AND demographic_no=${patient} AND status=1`, '1',
      'Submit did not save the opener');
    const html = sql.value(`SELECT form_data FROM eform_data WHERE fdid=${latestCopy(openerFid)}`);
    const button = (html.match(/<input[^>]*id="openLinked"[^>]*>/) || [''])[0];
    h.assert(button.includes(`link$eform="${linkedFdid}"`), `The saved opener's button is ${JSON.stringify(button)}; expected link$eform="${linkedFdid}"`);
  });

  await attempt('a saved opener\'s button opens the saved linked copy it holds', async () => {
    const seed = (fid, name, html) => {
      const fdid = sql.value(`INSERT INTO eform_data(fid,form_name,subject,demographic_no,status,form_date,form_time,form_provider,
          form_data,showLatestFormOnly,patient_independent,roleType)
        VALUES(${fid},${q(name)},${q(name)},${patient},1,CURDATE(),CURTIME(),${q(provider)},${q(html)},0,0,'');
        SELECT LAST_INSERT_ID()`);
      h.assert(/^[1-9]\d*$/.test(fdid || ''), `A saved copy of ${name} was not created`);
      return fdid;
    };
    const linkedFdid = seed(linkedFid, linkedName, page(linkedName, linkedBody(`${marker} seeded linked note`)));
    const openerFdid = seed(openerFid, openerName, page(openerName, openerBody(linkedFdid)));
    const saved = await s.popup(chart, chart.locator('a[onclick*="/eform/efmpatientformlist?"]').first(), 'eform-patient-list');
    const view = await s.popup(saved, saved.locator(`[onclick*="efmshowform_data?fdid=${openerFdid}&"]`).first(), 'eform-saved-opener');
    await view.locator('#openLinked').waitFor({ state: 'visible' });
    const target = await buttonTarget(view, view.locator('#openLinked'));
    const shown = { path: target.pathname, fdid: target.searchParams.get('fdid'), fid: target.searchParams.get('fid') };
    h.assert(shown.path.endsWith('/eform/efmshowform_data') && shown.fdid === linkedFdid,
      `The saved opener's button opens ${JSON.stringify(shown)}; expected the saved linked copy ${linkedFdid}`);
    const copy = await s.popup(view, view.locator('#openLinked'), 'eform-saved-linked');
    h.assert(await copy.locator('#note').inputValue() === `${marker} seeded linked note`, 'The saved linked copy does not show its note');
  });

  for (const { label, error } of outcomes) await s.step(label, async () => { if (error) throw error; });
}

if (require.main === module) runWorkflow('eform-open-linked', workflow, { openPatient: true });
module.exports = { workflow };
