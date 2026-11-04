#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * rx-interaction-warnings — drug-drug interaction markers on the Rx page's staged cards (#4506).
 *
 * User path: Search ▸ Master Record ▸ Prescriptions (Rx module) ▸ drug search ▸ staged cards ▸
 * the drug profile's "Discon" panel. The page asks GET rx/interactionWarnings?demographicNo=N
 * whenever a card is staged or removed (and on load), fills each card's
 * major_/moderate_/minor_/unknown_<randomId> marker, and records on #interactingDrugErrorMsg
 * (data-check-status) that the answer was drawn. Every step waits for that, so a marker that is
 * absent was checked and found absent, not merely not drawn yet.
 *
 * Asserted, with every expectation read from DrugRef's own `interactions` table:
 *   - staging warfarin, which interacts with nothing the patient takes, is checked against live
 *     DrugRef and drawn clean (DrugRef's empty answer is not mistaken for a failure);
 *   - staging ciprofloxacin marks its card with the major interaction against the patient's
 *     CURRENT theophylline (a seeded long-term drug row, not a card) and both staged cards with
 *     their moderate interaction, naming the partner, with effect, evidence and comment in the tooltip;
 *   - the warning level is applied: the provider's High hides the moderate interaction and keeps the
 *     major one, the provider's None shows nothing (status "off"), and the patient's own level
 *     overrides the provider's (facility < provider < patient);
 *   - removing a card re-checks and clears its partner's warning;
 *   - discontinuing the current theophylline re-checks and clears the ciprofloxacin card's warning;
 *   - a custom drug, which has no ATC code and so cannot be checked, is marked "not checked"
 *     rather than left looking clean;
 *   - a check the server reports as unavailable is shown as "unavailable", never as a clean card;
 *   - the endpoint answers only for a named patient whose Rx is open (400 / 404 otherwise).
 *
 * Fixtures: the owned synthetic patient, one owned `drugs` row (theophylline, long term), the
 * patient's demographicExt warning level and the chart note the discontinue files, all removed by
 * patient id; a second owned synthetic patient (never opened in Rx) for the 404 case. The provider's
 * rxInteractionWarningLevel `property` rows are snapshotted and restored exactly. Nothing is
 * prescribed: staged cards stay in the session and cleanup asserts only the seeded drug existed.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { stageFromSearch } = require('./rx-edit-discontinue-playwright-checks');
const { stageCustomDrug } = require('./rx-stash-patient-isolation-playwright-checks');
const { masterRecordLink } = require('./lib/master-record-link');

const CIPRO = { term: 'CIPRO 500', name: 'CIPRO 500MG', atc: 'J01MA02' };
const WARFARIN = { term: 'COUMADIN 5', name: 'COUMADIN TAB 5MG', atc: 'B01AA03' };
const THEOPHYLLINE = { brand: 'UNIPHYL 400MG', generic: 'THEOPHYLLINE', atc: 'R03DA04' };
const SEVERITY = { 1: 'minor', 2: 'moderate', 3: 'major' };
const LABEL = { major: 'Major interaction', moderate: 'Moderate interaction', minor: 'Minor interaction' };
const EFFECT = { A: 'augments', I: 'inhibits' };
const EVIDENCE = { G: 'good', F: 'fair', P: 'poor' };
const UNAVAILABLE = 'Drug interaction check unavailable';
const NOT_CHECKED = 'Interactions not checked';

/** DrugRef's graded record for a pair, in whichever direction it is filed, as the page words it. */
function drugRefPair(sql, one, other) {
  const rows = sql.rows(`SELECT affectingdrug, BINARY effect, affecteddrug, significance, evidence, TRIM(comment)
    FROM drugref2.interactions WHERE significance IN ('1','2','3') AND BINARY effect IN ('A','I')
      AND ((affectingatc=${h.sqlString(one)} AND affectedatc=${h.sqlString(other)})
        OR (affectingatc=${h.sqlString(other)} AND affectedatc=${h.sqlString(one)}))
    ORDER BY significance DESC`);
  h.assert(rows.length === 1, `DrugRef records ${rows.length} graded interactions between ${one} and ${other}, not one`);
  const [affecting, effect, affected, significance, evidence, comment] = rows[0];
  return {
    severity: SEVERITY[significance],
    summary: `${affecting} ${EFFECT[effect]} ${affected} (evidence: ${EVIDENCE[evidence] || 'unknown'})`,
    comment: comment.replace(/^\s+|\s+$/g, ''),
    affecting,
    affected,
  };
}

/** Runs action; returns the JSON of the interaction check it caused once the page has drawn it. */
async function interactionCheck(rx, patient, action) {
  const [response] = await Promise.all([
    rx.waitForResponse(r => h.pathOnly(r.url()).endsWith('/rx/interactionWarnings') && r.request().method() === 'GET',
      { timeout: 60000 }),
    action(),
  ]);
  h.assert(response.status() === 200, `rx/interactionWarnings answered HTTP ${response.status()}`);
  h.assert(new URL(response.url()).searchParams.get('demographicNo') === patient,
    'The interaction check did not name the page\'s patient');
  const body = await response.json();
  // The page marks "pending" before it sends, so this waits for this answer's render, not an older one's.
  const drawn = ['checked', 'off'].includes(body.status) ? body.status : 'unavailable';
  await rx.locator(`#interactingDrugErrorMsg[data-check-status="${drawn}"]`).waitFor({ state: 'attached', timeout: 15000 });
  return body;
}

/** The staged card whose drug-name field holds this product. */
async function cardFor(rx, name) {
  const cards = await rx.locator('[id^="drugName_"]').evaluateAll(inputs =>
    inputs.map(input => ({ id: input.id.slice('drugName_'.length), name: input.value })));
  const card = cards.find(entry => entry.name.toUpperCase().includes(name.toUpperCase()));
  h.assert(card, `No staged card for ${name}`);
  return card.id;
}

/** Every visible marker as "<marker id>: <text>", sorted. */
async function shownMarkers(rx) {
  return rx.locator('[data-rx-interaction]').evaluateAll(markers => markers
    .filter(marker => marker.style.display === 'inline')
    .map(marker => `${marker.id}: ${marker.textContent}`)
    .sort());
}

function assertMarkers(shown, expected, when) {
  h.assert(JSON.stringify(shown) === JSON.stringify([...expected].sort()),
    `Markers ${when}: ${JSON.stringify(shown)}, expected ${JSON.stringify([...expected].sort())}`);
}

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const predicate = `provider_no=${h.sqlString(provider)} AND name='rxInteractionWarningLevel'`;
  const snapshotQuery = `SELECT id,COALESCE(HEX(value),''),value IS NULL FROM property WHERE ${predicate} ORDER BY id`;
  const original = sql.rows(snapshotQuery);
  const patientLevel = `demographic_no=${patient} AND key_val='rxInteractionWarningLevel'`;
  const otherMarker = `${marker}X`;
  let seededDrug = null;
  let otherPatient = null;
  s.cleanup(() => {
    const statements = [`DELETE FROM property WHERE ${predicate}`];
    for (const [id, hex, isNull] of original) {
      h.assert(/^\d+$/.test(id) && /^[0-9a-f]*$/i.test(hex), 'Invalid warning-level restore snapshot');
      statements.push(`INSERT INTO property(id,provider_no,name,value) VALUES (${id},${h.sqlString(provider)},
        'rxInteractionWarningLevel',${isNull === '1' ? 'NULL' : `UNHEX('${hex}')`})`);
    }
    sql.execute(`START TRANSACTION;${statements.join(';')};COMMIT`);
    h.assert(JSON.stringify(sql.rows(snapshotQuery)) === JSON.stringify(original), 'The warning level was not restored to its snapshot');
    sql.execute(`DELETE FROM demographicExt WHERE ${patientLevel}`);
    // Only the seeded current medication may exist: a staged card must never have been saved.
    const drugs = sql.rows(`SELECT drugid FROM drugs WHERE demographic_no=${patient}`).map(row => row[0]);
    const notes = sql.rows(`SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`).map(row => row[0]);
    h.assert([...drugs, ...notes].every(id => /^[1-9]\d*$/.test(id)), 'Owned Rx row id is invalid');
    if (notes.length) {
      sql.execute(`DELETE FROM casemgmt_note_link WHERE note_id IN (${notes.join(',')});
        DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes.join(',')});
        DELETE FROM casemgmt_note WHERE demographic_no=${patient} AND note_id IN (${notes.join(',')})`);
    }
    sql.execute(`DELETE FROM drugs WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient})`) === '0', 'Owned Rx rows were not removed');
    h.assert(JSON.stringify(drugs) === JSON.stringify(seededDrug ? [seededDrug] : []),
      `Drugs other than the seeded theophylline were saved for the patient: ${drugs.join(',')}`);
  });
  s.cleanup(() => {
    if (otherPatient) {
      sql.execute(`DELETE FROM demographic WHERE demographic_no=${otherPatient} AND last_name=${h.sqlString(otherMarker)}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${otherPatient}`) === '0',
        'The second owned patient was not removed');
    }
  });
  const setProviderLevel = level => sql.execute(`START TRANSACTION;DELETE FROM property WHERE ${predicate};
    INSERT INTO property(provider_no,name,value) VALUES (${h.sqlString(provider)},'rxInteractionWarningLevel','${level}');COMMIT`);
  setProviderLevel('0');

  seededDrug = sql.value(`INSERT INTO drugs (provider_no,demographic_no,rx_date,end_date,written_date,BN,GN,ATC,GCN_SEQNO,
      special,archived,archived_reason,create_date,position,lastUpdateDate,dispenseInternal,long_term,quantity,duration,durunit,freqcode)
    VALUES (${h.sqlString(provider)},${patient},DATE_SUB(CURDATE(),INTERVAL 30 DAY),DATE_ADD(CURDATE(),INTERVAL 60 DAY),
      DATE_SUB(CURDATE(),INTERVAL 30 DAY),${h.sqlString(THEOPHYLLINE.brand)},${h.sqlString(THEOPHYLLINE.generic)},
      ${h.sqlString(THEOPHYLLINE.atc)},0,${h.sqlString(`${THEOPHYLLINE.brand} 1 tab PO daily`)},0,'',NOW(),0,NOW(),0,1,'30','30','D','OD');
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(seededDrug), 'The current theophylline fixture was not created');
  otherPatient = sql.value(`INSERT INTO demographic (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,
      patient_status,provider_no,hc_type,province,roster_status,lastUpdateDate)
    VALUES (${h.sqlString(otherMarker)},'Workflow','1981','03','04','M','AC',${h.sqlString(provider)},'ON','ON','NR',NOW());
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(otherPatient), 'The second owned patient was not created');

  const ciproTheo = drugRefPair(sql, CIPRO.atc, THEOPHYLLINE.atc);
  const ciproWarfarin = drugRefPair(sql, CIPRO.atc, WARFARIN.atc);
  h.assert(ciproTheo.severity === 'major' && ciproWarfarin.severity === 'moderate',
    `The fixture pairs are graded ${ciproTheo.severity}/${ciproWarfarin.severity} in DrugRef, not major/moderate`);
  h.assert(sql.value(`SELECT COUNT(*) FROM drugref2.interactions WHERE BINARY effect IN ('A','I')
      AND ((affectingatc=${h.sqlString(WARFARIN.atc)} AND affectedatc=${h.sqlString(THEOPHYLLINE.atc)})
        OR (affectingatc=${h.sqlString(THEOPHYLLINE.atc)} AND affectedatc=${h.sqlString(WARFARIN.atc)}))`) === '0',
  'DrugRef now records a warfarin-theophylline interaction; the clean-check step needs another fixture pair');
  const theophylline = ciproTheo.affecting.includes('CIPRO') ? ciproTheo.affected : ciproTheo.affecting;
  const warfarin = ciproWarfarin.affecting.includes('CIPRO') ? ciproWarfarin.affected : ciproWarfarin.affecting;
  const ciprofloxacin = ciproWarfarin.affecting.includes('CIPRO') ? ciproWarfarin.affecting : ciproWarfarin.affected;

  const rx = await s.popup(s.master, masterRecordLink(s.master, '/rx/choosePatient').first(), 'rx-module');
  h.assert(new URL(rx.url()).searchParams.get('demographicNo') === patient, 'The Rx module opened for another patient');
  await rx.locator('#searchString').waitFor({ state: 'visible' });
  await rx.waitForLoadState('networkidle');
  let ciproCard;
  let warfarinCard;
  const allThree = () => [
    `major_${ciproCard}: ${LABEL.major}: ${theophylline}`,
    `moderate_${ciproCard}: ${LABEL.moderate}: ${warfarin}`,
    `moderate_${warfarinCard}: ${LABEL.moderate}: ${ciprofloxacin}`,
  ];

  await s.step('staging warfarin, which interacts with nothing the patient takes, is checked and drawn clean', async () => {
    const body = await interactionCheck(rx, patient, async () => { warfarinCard = await stageFromSearch(rx, WARFARIN.term, WARFARIN.name); });
    h.assert(body.status === 'checked' && body.level === 0 && body.warnings.length === 0, `Unexpected check: ${JSON.stringify(body)}`);
    assertMarkers(await shownMarkers(rx), [], 'for warfarin alone');
    h.assert(!(await rx.locator('#interactingDrugErrorMsg').isVisible()), 'A clean check shows the unavailable message');
  });

  await s.step('staging ciprofloxacin marks it with the major interaction against the current theophylline and both cards with their moderate one', async () => {
    const body = await interactionCheck(rx, patient, async () => { ciproCard = await stageFromSearch(rx, CIPRO.term, CIPRO.name); });
    h.assert(body.status === 'checked', `Unexpected check: ${JSON.stringify(body)}`);
    assertMarkers(await shownMarkers(rx), allThree(), 'with ciprofloxacin and warfarin staged');
    const title = await rx.locator(`#major_${ciproCard}`).getAttribute('title');
    h.assert(title.includes(ciproTheo.summary) && (!ciproTheo.comment || title.includes(ciproTheo.comment)),
      `The major marker's tooltip does not carry DrugRef's detail: ${title}`);
    const moderateTitle = await rx.locator(`#moderate_${warfarinCard}`).getAttribute('title');
    h.assert(moderateTitle.includes(ciproWarfarin.summary), `The moderate marker's tooltip is ${moderateTitle}`);
  });

  const reloadAt = async (expectedLevel) => {
    const body = await interactionCheck(rx, patient, () => rx.reload());
    await rx.locator('#searchString').waitFor({ state: 'visible' });
    h.assert(body.level === expectedLevel, `The check applied level ${body.level}, not ${expectedLevel}`);
    ciproCard = await cardFor(rx, CIPRO.name);
    warfarinCard = await cardFor(rx, WARFARIN.name);
    return body;
  };

  await s.step('the provider\'s High warning level hides the moderate interaction and keeps the major one', async () => {
    setProviderLevel('3');
    const body = await reloadAt(3);
    h.assert(body.status === 'checked', `Unexpected check: ${JSON.stringify(body)}`);
    assertMarkers(await shownMarkers(rx), [`major_${ciproCard}: ${LABEL.major}: ${theophylline}`], 'at High');
  });

  await s.step('the provider\'s None warning level shows no interaction warning at all', async () => {
    setProviderLevel('4');
    const body = await reloadAt(4);
    h.assert(body.status === 'off' && body.warnings.length === 0, `Unexpected check: ${JSON.stringify(body)}`);
    assertMarkers(await shownMarkers(rx), [], 'at None');
    h.assert(!(await rx.locator('#interactingDrugErrorMsg').isVisible()), 'Level None reads as a failed check');
  });

  await s.step('the patient\'s own warning level overrides the provider\'s', async () => {
    sql.execute(`DELETE FROM demographicExt WHERE ${patientLevel};
      INSERT INTO demographicExt(demographic_no,provider_no,key_val,value,date_time,hidden)
      VALUES (${patient},${h.sqlString(provider)},'rxInteractionWarningLevel','2',NOW(),'0')`);
    const body = await reloadAt(2);
    h.assert(body.status === 'checked', `Unexpected check: ${JSON.stringify(body)}`);
    assertMarkers(await shownMarkers(rx), allThree(), 'at the patient\'s Medium over the provider\'s None');
  });

  await s.step('removing the warfarin card re-checks and clears the moderate warning from the ciprofloxacin card', async () => {
    const remove = rx.locator(`#set_${warfarinCard} a[onclick*="removePrescribingDrug"]`);
    await interactionCheck(rx, patient, () => remove.click());
    h.assert(await rx.locator(`#set_${warfarinCard}`).count() === 0, 'The warfarin card is still on the page');
    assertMarkers(await shownMarkers(rx), [`major_${ciproCard}: ${LABEL.major}: ${theophylline}`], 'after removing warfarin');
  });

  await s.step('discontinuing the current theophylline re-checks and clears the ciprofloxacin card\'s warning', async () => {
    await rx.locator(`#discont_${seededDrug}`).click();
    const panel = rx.locator('#discontinueUI');
    await panel.waitFor({ state: 'visible' });
    h.assert((await rx.locator('#disDrug').innerText()).includes(THEOPHYLLINE.brand), 'The discontinue panel names another drug');
    await rx.locator('#disReason').selectOption('drugInteraction');
    const body = await interactionCheck(rx, patient, () => panel.locator('input[onclick*="Discontinue2("]').click());
    h.assert(sql.value(`SELECT CONCAT(archived,'|',archived_reason) FROM drugs WHERE drugid=${seededDrug}`) === '1|drugInteraction',
      'The theophylline was not discontinued');
    h.assert(body.status === 'checked' && body.warnings.length === 0, `Unexpected check after discontinuing: ${JSON.stringify(body)}`);
    assertMarkers(await shownMarkers(rx), [], 'after discontinuing theophylline');
  });

  await s.step('a custom drug DrugRef cannot classify is marked as not checked, never as clean', async () => {
    let customCard;
    const body = await interactionCheck(rx, patient, async () => { customCard = await stageCustomDrug(rx, `${marker} compounded cream`); });
    h.assert(body.status === 'checked' && JSON.stringify(body.unchecked) === JSON.stringify([customCard]),
      `The custom drug was not reported as unchecked: ${JSON.stringify(body)}`);
    assertMarkers(await shownMarkers(rx), [`unchecked_${customCard}: ${NOT_CHECKED}`], 'with a custom drug staged');
    h.assert((await rx.locator(`#unchecked_${customCard}`).getAttribute('title')).includes('no ATC code'),
      'The not-checked note does not say why');
  });

  await s.step('a check the server reports as unavailable is shown as unavailable, not as a clean card', async () => {
    const pattern = /\/rx\/interactionWarnings(\?|$)/;
    await rx.route(pattern, route => route.fulfill({ status: 200, contentType: 'application/json',
      body: JSON.stringify({ status: 'unavailable', level: 0, warnings: [] }) }));
    try {
      await interactionCheck(rx, patient, () => rx.reload());
      const message = rx.locator('#interactingDrugErrorMsg');
      h.assert(await message.isVisible() && (await message.innerText()).includes(UNAVAILABLE), 'The unavailable message is not shown');
      assertMarkers(await shownMarkers(rx), [], 'for an unavailable check');
    } finally {
      await rx.unroute(pattern);
    }
  });

  await s.step('rx/interactionWarnings answers only for a named patient whose Rx is open', async () => {
    const base = h.appUrl(s.config.baseUrl, '/rx/interactionWarnings');
    const get = query => s.context.request.get(`${base}${query}`, { maxRedirects: 0 });
    const unnamed = await get('');
    h.assert(unnamed.status() === 400, `A request naming no patient answered HTTP ${unnamed.status()}`);
    const conflicting = await get(`?demographicNo=${patient}&demographic_no=${otherPatient}`);
    h.assert(conflicting.status() === 400, `Conflicting patients answered HTTP ${conflicting.status()}`);
    const notOpen = await get(`?demographicNo=${otherPatient}`);
    h.assert(notOpen.status() === 404, `A patient with no open Rx answered HTTP ${notOpen.status()}`);
    const own = await get(`?demographicNo=${patient}`);
    h.assert(own.status() === 200 && /^application\/json/.test(own.headers()['content-type'] || ''),
      `The patient's own check answered HTTP ${own.status()}`);
    h.assert(own.headers()['cache-control'] === 'no-store', 'The interaction answer may be cached');
  });
}

if (require.main === module) runWorkflow('rx-interaction-warnings', workflow, { openPatient: true });
module.exports = { workflow };
