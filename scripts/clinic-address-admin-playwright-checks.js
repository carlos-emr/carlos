#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * clinic-address-admin — the clinic's own name and address, edited the way an administrator does:
 *   Schedule ▸ Administration ▸ System Management ▸ Clinic/Agency Address (admin/ManageClinic, in the
 *   shell's iframe) ▸ change every field ▸ Submit (POST method=update) ▸ open the page again.
 * Asserts:
 *   - The page opens on the stored `clinic` row: every input shows its column.
 *   - Submit stores exactly what was typed into the one clinic row, punctuation, accents and the
 *     phone/fax delimiter fields included, and adds no second row.
 *   - The page shown after Submit, and the page opened again from the menu, show the stored values.
 *   - The letterheads follow the edit, for the owned patient: the Rx print preview (E-Chart ▸ Prescriptions ▸ a
 *     custom drug ▸ Save And Print, the nested ViewPreview2 frame) shows the edited clinic name and address, and
 *     its phone and fax unless the prescriber's own Rx contact details replace them (RxProviderData.convertProvider:
 *     the rxAddress/rxPhone/faxnumber preferences, the provider's address, work phone or xml_p_fax); a new referral
 *     (E-Chart ▸ Consultations "+") offers the clinic letterhead with the edited name, address, phone and fax, and
 *     its Submit Consultation Request And Print Preview PDF carries them at the top.
 *   - GET and HEAD carrying the save (method=update) are refused and change nothing.
 * Fixtures: the owned FAKE- patient, with its custom drug and script and its referral (to a service and specialist
 * the install already has; removed in cleanup and asserted gone). The clinic row is clinic-wide: the check snapshots every
 * column first (as HEX, NULL kept apart from '') and restores it with UNHEX in cleanup, verifying it the same way,
 * so it must not run concurrently with other checks (live wrapper: EXCLUSIVE=1). Reading the PDF needs Poppler's
 * pdftotext; without it only that assertion is skipped, and the run prints a SKIP line for it.
 */
const fs = require('node:fs');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { requirePoppler, pdfTextBuffer } = require('./lib/export-content-helpers');
const { stageCustomDrug, clearOwnedPrescriptionRows } = require('./rx-stash-patient-isolation-playwright-checks');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow } = require('./lib/workflow-session');

const TIMEOUT = 20000;
const SECTION = '/admin/ManageClinic';
const COLUMNS = {
  'clinic.clinicName': 'clinic_name',
  'clinic.clinicAddress': 'clinic_address',
  'clinic.clinicCity': 'clinic_city',
  'clinic.clinicPostal': 'clinic_postal',
  'clinic.clinicPhone': 'clinic_phone',
  'clinic.clinicFax': 'clinic_fax',
  'clinic.clinicLocationCode': 'clinic_location_code',
  'clinic.clinicProvince': 'clinic_province',
  'clinic.clinicDelimPhone': 'clinic_delim_phone',
  'clinic.clinicDelimFax': 'clinic_delim_fax',
};

const squash = text => String(text ?? '').replace(/\s+/g, ' ').trim();

/**
 * What the Rx print preview's clinic block should show for this prescriber once the clinic row holds `typed`
 * (RxProviderData.convertProvider and Preview2.jsp): the clinic's name always; its address, city, province and
 * postal code unless the prescriber has an rxAddress preference (then those four come from rxAddress, rxCity,
 * rxProvince, rxPostal) or, for the street address alone, a provider address; its phone unless rxPhone or the
 * provider's work phone is set; its fax unless faxnumber or the provider's xml_p_fax is set.
 */
function rxLetterhead(sql, providerNo, typed) {
  const q = h.sqlString;
  const prop = name => sql.value(`SELECT IFNULL(MAX(value),'') FROM property WHERE provider_no=${q(providerNo)} AND name=${q(name)}`) || '';
  const [[address, workPhone, comments]] = sql.rows(`SELECT IFNULL(address,''), IFNULL(work_phone,''), IFNULL(comments,'')
    FROM provider WHERE provider_no=${q(providerNo)}`);
  const xmlFax = ((comments || '').match(/<xml_p_fax>([^<]*)<\/xml_p_fax>/) || [])[1] || '';
  const rxAddress = prop('rxAddress');
  return {
    name: typed['clinic.clinicName'],
    address: rxAddress || address || typed['clinic.clinicAddress'],
    city: rxAddress ? prop('rxCity') : typed['clinic.clinicCity'],
    province: rxAddress ? prop('rxProvince') : typed['clinic.clinicProvince'],
    postal: rxAddress ? prop('rxPostal') : typed['clinic.clinicPostal'],
    phone: prop('rxPhone') || workPhone || typed['clinic.clinicPhone'],
    fax: prop('faxnumber') || xmlFax || typed['clinic.clinicFax'],
  };
}

/** The print window is an iframe over the Rx page, and its preview a frame inside it; poll for both. */
async function rxPreviewFrame(page, timeout = 60000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const script = page.frames().find(f => /\/rx\/viewScript\?/.test(f.url()));
    const preview = script && script.childFrames().find(f => /\/rx\/ViewPreview2\?/.test(f.url()));
    if (preview && await preview.locator('#clinicAddress').count()) return preview;
    await page.waitForTimeout(250);
  }
  throw new Error('The Rx print window or its preview did not open');
}

async function workflow(s) {
  const { sql, marker, context, recorder, patient, provider } = s;
  const q = h.sqlString;
  const tag = marker.slice(-6);
  const rows = () => sql.rows(`SELECT clinic_no FROM clinic ORDER BY clinic_no`);
  const original = rows();
  h.assert(original.length === 1, `Expected one clinic row, found ${original.length}`);
  const [[clinicNo]] = original;
  h.assert(/^[1-9]\d*$/.test(clinicNo), 'The clinic row has no numeric id');
  // Byte-exact snapshot: each column is 'N' for NULL or 'X' + HEX of its text, so NULL stays apart from ''. `mysql -B`
  // prints NULL and the text 'NULL' alike and the harness reads both as null, so neither marker may be spelled NULL.
  // CAST ... AS BINARY makes HEX read a number's text (status) rather than its value, so UNHEX puts the same number back.
  const columns = [...Object.values(COLUMNS), 'status'];
  const snapshotQuery = `SELECT ${columns.map(c => `IF(${c} IS NULL,'N',CONCAT('X',HEX(CAST(${c} AS BINARY))))`).join(',')} FROM clinic WHERE clinic_no=${clinicNo}`;
  const [snapshot] = sql.rows(snapshotQuery);
  h.assert(snapshot.every(value => value === 'N' || /^X[0-9A-F]*$/.test(value)), 'The clinic snapshot is not N or X plus hex');
  s.cleanup(() => {
    // The failure this check exists to catch is Submit inserting a second row. The precondition above makes the
    // snapshotted row the only one, so any other row is this run's: remove it (the check is exclusive) before the
    // count is asserted, so the clinic-wide table is left as found and the next run does not stop at its precondition.
    sql.execute(`DELETE FROM clinic WHERE clinic_no<>${clinicNo}`);
    const assignments = columns.map((column, index) => `${column}=${snapshot[index] === 'N' ? 'NULL' : `UNHEX('${snapshot[index].slice(1)}')`}`);
    sql.execute(`UPDATE clinic SET ${assignments.join(',')} WHERE clinic_no=${clinicNo}`);
    h.assert(JSON.stringify(sql.rows(snapshotQuery)[0]) === JSON.stringify(snapshot), 'The clinic row was not restored to its snapshot');
    h.assert(sql.value('SELECT COUNT(*) FROM clinic') === '1', 'A clinic row was added and not removed');
  });

  const typed = {
    'clinic.clinicName': `FAKE ${tag} Clinique & Co. (Test)`,
    'clinic.clinicAddress': `12-B O'Neil Street, Suite #3`,
    'clinic.clinicCity': 'Trois-Rivières',
    'clinic.clinicPostal': 'A1A 1A1',
    'clinic.clinicPhone': '555-010-0199',
    'clinic.clinicFax': '555-010-0198',
    'clinic.clinicLocationCode': `F${tag.slice(0, 3)}`,
    'clinic.clinicProvince': 'QC',
    'clinic.clinicDelimPhone': '555-010-0199 x12',
    'clinic.clinicDelimFax': '555-010-0198',
  };
  const stored = () => {
    const [row] = sql.rows(`SELECT ${Object.values(COLUMNS).join(',')} FROM clinic WHERE clinic_no=${clinicNo}`);
    return Object.fromEntries(Object.keys(COLUMNS).map((name, index) => [name, row[index]]));
  };

  const { page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    { context, recorder, label: 'administration', timeout: TIMEOUT });
  await admin.waitForLoadState('load');
  const menuEntry = admin.locator(`a.xlink[rel$="${SECTION}"]`).first();
  h.assert(await menuEntry.count() === 1, 'Administration offers no Clinic/Agency Address entry');

  let frame;
  /** Click something that navigates the admin iframe, and wait for the new document. */
  async function frameNavigation(action, label, isTarget = candidate => candidate === frame) {
    const navigated = admin.waitForEvent('framenavigated', { predicate: isTarget, timeout: TIMEOUT });
    navigated.catch(() => {});
    await action();
    const next = await navigated;
    await next.waitForLoadState('domcontentloaded', { timeout: TIMEOUT }).catch(() => {});
    await h.assertNotErrorPage(next, label);
    return next;
  }
  const openFromMenu = async label => {
    await revealAuditLink(admin, menuEntry, TIMEOUT);
    frame = await frameNavigation(() => menuEntry.click({ timeout: TIMEOUT }), label,
      candidate => candidate !== admin.mainFrame() && new URL(candidate.url()).pathname.endsWith(SECTION));
  };
  const shown = async () => Object.fromEntries(await Promise.all(Object.keys(COLUMNS).map(async name =>
    [name, await frame.locator(`input[name="${name}"]`).inputValue()])));
  // A NULL column reads back as null, and the page shows it as an empty input.
  const asStored = values => Object.fromEntries(Object.entries(values).map(([name, value]) => [name, value ?? '']));

  await s.step('Administration ▸ Clinic/Agency Address opens on the stored clinic row', async () => {
    await openFromMenu('the clinic address page');
    h.assert(JSON.stringify(await shown()) === JSON.stringify(asStored(stored())),
      'The clinic address page does not show the stored clinic row');
  });

  await s.step('Submit stores every typed field in the one clinic row, punctuation and accents included', async () => {
    for (const [name, value] of Object.entries(typed)) {
      await frame.locator(`input[name="${name}"]`).fill(value);
    }
    await frameNavigation(() => frame.locator('form[action$="/admin/ManageClinic"] input[type="submit"]').click(), 'the page after Submit');
    const after = stored();
    for (const [name, value] of Object.entries(typed)) {
      h.assert(after[name] === value, `${name} was stored as "${after[name]}", not "${value}"`);
    }
    h.assert(sql.value('SELECT COUNT(*) FROM clinic') === '1', 'Submit added a second clinic row instead of updating the one');
    h.assert(JSON.stringify(await shown()) === JSON.stringify(typed), 'The page after Submit does not show the stored values');
  });

  await s.step('the page opened again from the menu shows the stored values', async () => {
    await openFromMenu('the clinic address page, reopened');
    h.assert(JSON.stringify(await shown()) === JSON.stringify(typed), 'The reopened clinic address page lost a stored value');
  });

  const drug = `${marker}-clinic`;
  s.cleanup(() => {
    clearOwnedPrescriptionRows(sql, patient, marker);
    h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient} AND customName LIKE ${q(`${marker}%`)}`) === '0',
      'Owned prescription drugs were not removed');
  });
  await s.step('the Rx print preview shows the edited clinic name and address, and its phone and fax unless the prescriber replaces them', async () => {
    const expected = rxLetterhead(sql, provider, typed);
    const chart = await s.chart();
    const rx = await s.popup(chart, chart.locator('#menuTitleRx a').first(), 'rx-page');
    const key = await stageCustomDrug(rx, drug);
    await rx.locator(`#instructions_${key}`).fill('Take one tablet daily');
    await rx.locator(`#instructions_${key}`).blur();
    await rx.locator('#saveButton').click();
    const preview = await rxPreviewFrame(rx);
    const header = squash(await preview.locator('#clinicAddress').textContent());
    for (const [field, value] of Object.entries({ name: expected.name, address: expected.address,
      place: `${expected.city} ${expected.province} ${expected.postal}`, phone: `Tel: ${expected.phone}` })) {
      h.assert(header.includes(squash(value)), `The Rx preview's clinic block does not show the ${field} "${value}": ${JSON.stringify(header.slice(0, 300))}`);
    }
    // The fax line is printed only where the RXFAX property is on.
    if (/Fax:/.test(header)) h.assert(header.includes(`Fax: ${expected.fax}`), `The Rx preview's fax is not "${expected.fax}"`);
    await rx.close();
  });

  const reason = `${marker} clinic letterhead referral`;
  s.cleanup(() => {
    const requests = sql.rows(`SELECT requestId FROM consultationRequests WHERE demographicNo=${patient}`)
      .map(([id]) => id).filter(id => /^[1-9]\d*$/.test(id));
    for (const id of requests) {
      sql.execute(`DELETE FROM consultationRequestExtArchive WHERE requestId=${id};
        DELETE FROM consultationRequestsArchive WHERE requestId=${id} AND demographicNo=${patient};
        DELETE FROM consultationRequestExt WHERE requestId=${id};
        DELETE FROM consultationRequests WHERE requestId=${id} AND demographicNo=${patient}`);
    }
    sql.execute(`DELETE FROM DigitalSignature WHERE demographicId=${patient} AND ModuleType='CONSULTATION'`);
    h.assert(sql.value(`SELECT COUNT(*) FROM consultationRequests WHERE demographicNo=${patient}`) === '0',
      'The owned referral was not removed');
  });

  let referralPdf;
  await s.step('a new referral offers the edited clinic as its letterhead and prints a PDF', async () => {
    const clinicAddress = squash([typed['clinic.clinicAddress'], typed['clinic.clinicCity'], typed['clinic.clinicProvince'],
      typed['clinic.clinicPostal']].join(' '));
    const chart = await s.chart();
    const form = await s.popup(chart, chart.locator('a[onclick*="ViewConsultationFormRequest?de="]').first(), 'consult-new');
    await form.locator('#EctConsultationFormRequest2Form').waitFor({ state: 'attached' });
    const section = form.locator('#collapseLetterhead');
    if (!(await section.isVisible())) {
      await form.locator('a[href="#collapseLetterhead"]').click();
      await section.waitFor({ state: 'visible' });
    }
    // A new referral starts on the signed-in provider's letterhead unless the user chose another default
    // (consultation_letterheadname_default); the clinic is chosen here the way the clinician does.
    const select = form.locator('#letterheadName');
    h.assert(await select.locator('option').first().getAttribute('value') === typed['clinic.clinicName'],
      'The Letterhead list does not offer the edited clinic name');
    await select.selectOption({ value: typed['clinic.clinicName'] });
    const span = async id => squash(await form.locator(`#${id}`).textContent());
    h.assert(await span('letterheadAddressSpan') === clinicAddress, `The clinic letterhead shows the address "${await span('letterheadAddressSpan')}"`);
    h.assert(await span('letterheadPhoneSpan') === typed['clinic.clinicPhone'], 'The clinic letterhead does not show the edited phone');
    h.assert(await span('letterheadFaxSpan') === typed['clinic.clinicFax'], 'The clinic letterhead does not show the edited fax');
    // The pickers' lists load after the page, and the specialist list is cached, so a specialist added by SQL would
    // not be offered: the referral goes to a specialist the form already lists, under one of its services, picked
    // the way the clinician does (type to filter, click the entry) so the posted hidden ids come from the pickers.
    const pick = await form.waitForFunction(() => {
      if (typeof allServicesData === 'undefined' || typeof allSpecialistsData === 'undefined') return null;
      const spec = allSpecialistsData.find(entry => entry.name && (entry.serviceIds || []).length && (entry.serviceNames || []).length
        && allServicesData.some(service => String(service.serviceId) === String(entry.serviceIds[0])));
      return spec ? { service: spec.serviceNames[0], serviceId: String(spec.serviceIds[0]), name: spec.name, specId: String(spec.specId) } : null;
    }, null, { timeout: 20000 }).then(handle => handle.jsonValue())
      .catch(() => { throw new Error('The referral form lists no specialist with a service to refer to'); });
    for (const [input, name] of [['#serviceInput', pick.service], ['#specialistInput', pick.name]]) {
      await form.locator(input).click();
      await form.locator(input).pressSequentially(name);
      const item = form.locator('ul.ui-autocomplete:visible li.ui-menu-item').filter({ hasText: name }).first();
      await item.waitFor({ state: 'visible', timeout: 15000 });
      await item.click();
    }
    h.assert(await form.locator('#service').inputValue() === pick.serviceId, 'The service picker did not set the chosen service');
    h.assert(await form.locator('#specialist').inputValue() === pick.specId, 'The specialist picker did not set the chosen specialist');
    await form.locator('#urgency').selectOption('1');
    await form.locator('textarea[name="reasonForConsultation"]').fill(reason);
    const download = form.waitForEvent('download', { timeout: 30000 });
    await ui.clickAndAwaitReload(form, form.locator('input[name="submitAndPrint"]'));
    referralPdf = fs.readFileSync(await (await download).path());
    h.assert(referralPdf.subarray(0, 5).toString('latin1') === '%PDF-', 'Submit Consultation Request And Print Preview did not answer a PDF');
    await form.close();
  });

  // Reading the PDF needs Poppler's pdftotext. Without it only this assertion is skipped, and the run says so.
  const pdfLabel = 'the referral PDF\'s letterhead carries the edited clinic name, address, city, phone and fax';
  let poppler = true;
  try {
    requirePoppler('pdftotext');
  } catch (error) {
    if (!(error instanceof h.SkipCheck || (error && error.name === 'SkipCheck'))) throw error;
    poppler = false;
  }
  if (!poppler) {
    console.log(`  SKIP clinic-address-admin: ${pdfLabel} -- Poppler's pdftotext is not installed, so the printed PDF was not read`);
  } else {
    await s.step(pdfLabel, async () => {
      const text = squash(pdfTextBuffer(referralPdf));
      h.assert(text.includes(reason), 'The PDF is not the new referral');
      const header = text.slice(0, Math.max(0, text.indexOf(reason)));
      for (const [field, value] of Object.entries({ name: typed['clinic.clinicName'], address: typed['clinic.clinicAddress'],
        city: typed['clinic.clinicCity'], phone: typed['clinic.clinicPhone'], fax: typed['clinic.clinicFax'] })) {
        h.assert(header.includes(squash(value)), `The referral PDF's letterhead does not carry the edited ${field}: ${JSON.stringify(header.slice(0, 300))}`);
      }
    });
  }

  await s.step('GET and HEAD carrying the save are refused and change nothing', async () => {
    const probeName = `FAKE ${tag} written by GET`;
    const where = `clinic_no=${clinicNo} AND clinic_name=${q(probeName)}`;
    for (const method of ['GET', 'HEAD']) {
      const response = await context.request.fetch(h.appUrl(s.config.baseUrl, SECTION)
        + `?method=update&clinic.id=${clinicNo}&clinic.clinicName=${encodeURIComponent(probeName)}`,
      { method, maxRedirects: 0, failOnStatusCode: false });
      await h.assertRefused(s, { label: `${method} admin/ManageClinic method=update`, table: 'clinic', where, before: '0', response });
    }
    h.assert(stored()['clinic.clinicName'] === typed['clinic.clinicName'], 'A refused request changed the clinic name');
  });
}

if (require.main === module) runWorkflow('clinic-address-admin', workflow, { openPatient: true });
module.exports = { workflow };
