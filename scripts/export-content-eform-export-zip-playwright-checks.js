#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Manage eForms ▸ Export (the .zip a clinic shares between installs): the archive's CONTENT against the library row.
 * User path: Schedule ▸ Administration ▸ Forms ▸ Manage eForms ▸ the row's download (Export) icon.
 * eform-admin-schedule-navigation round-trips one plain form and only asserts the file is not empty.
 *
 * Asserts: the archive holds <name without spaces>/eform.properties and <name without spaces>/<file name>; the HTML
 * entry is byte-for-byte the stored form_html (accents, a non-Latin-1 letter, CRLF newlines, quotes, script tags);
 * eform.properties, read as Java properties, carries the stored name, subject (accent, quote, comma, ampersand),
 * creator, date and the two flags; the same holds for a form whose name has an accent, a quote and a letter outside
 * Latin-1. LAST (fails today): a form named like clinics name them, "Well Baby 0/6 months", exports a zip (its name
 * is rejected as a path component and the click answers an error page); and the attachment is named after the form
 * (the code splices the numeric form id in place of every space in the name, and the header is not RFC 6266 safe).
 * Fixtures: three FAKE- eform rows (status 1) inserted by SQL; cleanup deletes only those fids and asserts them gone.
 */
const h = require('./lib/playwright-harness');
const { clickInjectsPanel, clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow } = require('./lib/workflow-session');
const x = require('./lib/export-content-helpers');

const q = h.sqlString;

/** Java Properties.load for an ISO-8859-1 stream with \uXXXX escapes, enough for eform.properties. */
function loadProperties(buffer) {
  const props = {};
  const unescape = value => value.replace(/\\u([0-9a-fA-F]{4})/g, (_, hex) => String.fromCharCode(parseInt(hex, 16)))
    .replace(/\\([:=#!\\ ])/g, '$1').replace(/\\n/g, '\n').replace(/\\r/g, '\r').replace(/\\t/g, '\t');
  for (const line of buffer.toString('latin1').split(/\r?\n/)) {
    if (!line || /^[#!]/.test(line)) continue;
    const at = line.search(/(?<!\\)[=:]/);
    props[unescape(line.slice(0, at))] = unescape(line.slice(at + 1));
  }
  return props;
}

async function workflow(s) {
  const { sql, provider, marker } = s;
  const scratch = x.scratchDir();
  const fids = [];
  s.cleanup(() => require('node:fs').rmSync(scratch, { recursive: true, force: true }));
  s.cleanup(() => {
    sql.execute(`DELETE FROM eform WHERE form_name LIKE ${q(`${marker}%`)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM eform WHERE form_name LIKE ${q(`${marker}%`)}`) === '0', 'Owned eForms were not removed');
  });
  const html = '<!doctype html>\r\n<html><head><meta charset="utf-8"><title>Dépistage Łódź</title></head>\r\n'
    + '<body>\r\n<form method="post"><input type="hidden" name="demographic_no" value="">\r\n'
    + '<p>Zoë "Zed" O\'Neil &amp; fils — ½ dose…</p><script>var a = "<b>x</b>";</script>\r\n</form></body></html>\r\n';
  h.assert(html.length > 0, 'The form HTML fixture is empty');
  const forms = {
    plain: { name: `${marker} Export Plain`, subject: 'Dépistage, "annuel" & Ł' },
    accent: { name: `${marker} Ça "va" Łódź`, subject: 'Subject' },
    slash: { name: `${marker} Well Baby 0/6 months`, subject: 'Slash' },
  };
  for (const [key, form] of Object.entries(forms)) {
    form.fid = sql.value(`INSERT INTO eform (form_name,file_name,subject,form_date,form_time,form_creator,status,form_html,
        showLatestFormOnly,patient_independent,roleType,restrictToProgram,stable)
      VALUES (${q(form.name)},${q(`${marker}-${key}.html`)},${q(form.subject)},'2024-05-06','07:08:09',${q(provider)},1,${q(html)},1,0,'',0,1);
      SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(form.fid), 'The eForm fixture was not created');
    fids.push(form.fid);
  }
  h.assert(sql.value(`SELECT form_html FROM eform WHERE fid=${forms.plain.fid}`).replace(/\\r/g, '\r').replace(/\\n/g, '\n') === html,
    'The HTML fixture was not stored as written');

  const { page: admin } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'eform-export-administration', timeout: 20000 });
  const link = admin.locator('a.contentLink.defaultForms').first();
  await link.waitFor({ state: 'attached', timeout: 20000 });
  await revealAuditLink(admin, link, 20000);
  await clickInjectsPanel(admin, link, { marker: '#dynamic-content #eformTbl' });
  const exportLink = form => admin.locator(`#eformTbl a[href*="method=exportEForm"][href*="fid=${form.fid}"]`);

  const check = async (form, label) => {
    const file = await x.saveDownload(admin, scratch, () => exportLink(form).click(), { route: /\/eform\/manageEForm$/ });
    h.assert(file.status === 200 && /zip/.test(file.headers['content-type'] || ''), `${label}: Export did not answer a zip`);
    const entries = x.unzip(file.bytes);
    const folder = form.name.replace(/\s/g, '');
    const names = Object.keys(entries).sort();
    h.assert(JSON.stringify(names) === JSON.stringify([`${folder}/${marker}-${label}.html`, `${folder}/eform.properties`].sort()),
      `${label}: the archive entries are not <form>/eform.properties and <form>/<file>`);
    const stored = sql.value(`SELECT DATE_FORMAT(form_date,'%Y-%m-%d') FROM eform WHERE fid=${form.fid}`);
    h.assert(entries[`${folder}/${marker}-${label}.html`].toString('utf8') === html, `${label}: the exported HTML differs from the stored form_html`);
    const props = loadProperties(entries[`${folder}/eform.properties`]);
    h.assert(props['form.name'] === form.name, `${label}: eform.properties lost the form name`);
    h.assert(props['form.details'] === form.subject, `${label}: eform.properties lost the subject`);
    h.assert(props['form.creator'] === provider, `${label}: eform.properties lost the creator`);
    h.assert(props['form.date'] === stored, `${label}: eform.properties carries a different date`);
    h.assert(props['form.showLatestFormOnly'] === 'true' && props['form.patientIndependent'] === undefined, `${label}: eform.properties flags differ from the row`);
    return file;
  };
  let plainFile;
  await s.step('the plain form exports its HTML and properties exactly as stored', async () => {
    plainFile = await check({ ...forms.plain }, 'plain');
  });
  let accentFile;
  await s.step('a form named with an accent, a quote and a non-Latin-1 letter exports the same content', async () => {
    accentFile = await check({ ...forms.accent }, 'accent');
  });

  await s.step('a slash in the name still exports, and the attachment is named after the form', async () => {
    const problems = [];
    const nameOf = file => file.headers['content-disposition'] || '';
    const fidSplice = new RegExp(`Export${forms.plain.fid}Plain`);
    if (fidSplice.test(nameOf(plainFile))) {
      problems.push(`the attachment name splices the form id into the name ("${nameOf(plainFile).replace(marker, 'M')}")`);
    }
    const accentName = nameOf(accentFile);
    // RFC 6266: a quote inside a quoted-string must be escaped, and non-ASCII needs filename*= (or a transliteration).
    if (/filename="[^"]*"[^;]*"/.test(accentName) || (/[^\x20-\x7e]/.test(Buffer.from(accentName, 'latin1').toString('latin1')) && !/filename\*=/.test(accentName))) {
      problems.push('the attachment name of an accented, quoted form is not a valid Content-Disposition value');
    }
    const responding = admin.waitForResponse(r => /\/eform\/manageEForm$/.test(new URL(r.url()).pathname), { timeout: 20000 });
    responding.catch(() => {});
    const downloading = admin.waitForEvent('download', { timeout: 15000 }).catch(() => null);
    await exportLink(forms.slash).click();
    const response = await responding.catch(() => null);
    const download = await downloading;
    if (!download) problems.push(`Export of "Well Baby 0/6 months" sent no zip (HTTP ${response ? response.status() : 'none'}): a "/" in the form name is rejected as a path component by EFormExportZip`);
    h.assert(!problems.length, `eForm export problems: ${problems.join('; ')}`);
  });
}

if (require.main === module) runWorkflow('export-content-eform-export-zip', workflow, { openPatient: false, openMaster: false });
module.exports = { workflow };
