#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * rx-interaction-warnings — drug-drug interaction markers on the Rx page's staged cards (#4506).
 *
 * User path: Search ▸ Master Record ▸ Prescriptions (Rx module) ▸ drug search ▸ staged cards.
 * The page asks GET rx/interactionWarnings?demographicNo=N whenever a card is staged or removed
 * (and on load), and fills each card's major_/moderate_/minor_/unknown_<randomId> marker.
 *
 * Asserted, with every expectation read from DrugRef's own `interactions` table:
 *   - staging ciprofloxacin marks its card with the interaction against the patient's CURRENT
 *     theophylline (a seeded long-term drug row, not a staged card), at DrugRef's significance,
 *     naming the partner and carrying the effect, evidence and comment in the tooltip;
 *   - staging warfarin beside it marks BOTH staged cards with their moderate interaction;
 *   - the warning level is applied: the provider's High hides the moderate interaction and keeps
 *     the major one, the provider's None shows nothing (status "off"), and the patient's own level
 *     overrides the provider's (facility < provider < patient);
 *   - removing a card re-checks and clears its partner's warning;
 *   - a check the server reports as unavailable is shown as "unavailable", never as a clean card;
 *   - the endpoint answers only for a named patient whose Rx is open (400 / 404 otherwise).
 *
 * Fixtures: the owned synthetic patient, one owned `drugs` row (theophylline, long term) and the
 * patient's demographicExt warning level, all removed by patient id. The provider's
 * rxInteractionWarningLevel `property` rows are snapshotted and restored exactly. Nothing is
 * prescribed: staged cards stay in the session and cleanup asserts only the seeded drug existed.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { stageFromSearch } = require('./rx-edit-discontinue-playwright-checks');

const CIPRO = { term: 'CIPRO 500', name: 'CIPRO 500MG', atc: 'J01MA02' };
const WARFARIN = { term: 'APO-WARFARIN 5', name: 'APO-WARFARIN 5MG', atc: 'B01AA03' };
const THEOPHYLLINE = { brand: 'UNIPHYL 400MG', generic: 'THEOPHYLLINE', atc: 'R03DA04' };
const SEVERITY = { 1: 'minor', 2: 'moderate', 3: 'major' };
const LABEL = { major: 'Major interaction', moderate: 'Moderate interaction', minor: 'Minor interaction' };
const EFFECT = { A: 'augments', I: 'inhibits' };
const EVIDENCE = { G: 'good', F: 'fair', P: 'poor' };
const UNAVAILABLE = 'Drug interaction check unavailable';

/** DrugRef's graded record for a pair, in whichever direction it is filed, as the page words it. */
function drugRefPair(sql, staged, partner) {
  const rows = sql.rows(`SELECT affectingdrug, BINARY effect, affecteddrug, significance, evidence, TRIM(comment)
    FROM drugref2.interactions WHERE significance IN ('1','2','3') AND BINARY effect IN ('A','I')
      AND ((affectingatc=${h.sqlString(staged)} AND affectedatc=${h.sqlString(partner)})
        OR (affectingatc=${h.sqlString(partner)} AND affectedatc=${h.sqlString(staged)}))
    ORDER BY significance DESC`);
  h.assert(rows.length, `DrugRef records no graded interaction between ${staged} and ${partner}`);
  const [affecting, effect, affected, significance, evidence, comment] = rows[0];
  return {
    severity: SEVERITY[significance],
    summary: `${affecting} ${EFFECT[effect]} ${affected} (evidence: ${EVIDENCE[evidence] || 'unknown'})`,
    comment: comment.replace(/^\s+|\s+$/g, ''),
    names: [affecting, affected],
  };
}

/** Runs action and returns the interaction check it caused, after asserting it named this patient. */
async function interactionCheck(rx, patient, action) {
  const [response] = await Promise.all([
    rx.waitForResponse(r => h.pathOnly(r.url()).endsWith('/rx/interactionWarnings') && r.request().method() === 'GET',
      { timeout: 60000 }),
    action(),
  ]);
  h.assert(response.status() === 200, `rx/interactionWarnings answered HTTP ${response.status()}`);
  h.assert(new URL(response.url()).searchParams.get('demographicNo') === patient,
    'The interaction check did not name the page\'s patient');
  return response.json();
}

/** The staged card whose drug-name field holds this product. */
async function cardFor(rx, name) {
  const cards = await rx.locator('[id^="drugName_"]').evaluateAll(inputs =>
    inputs.map(input => ({ id: input.id.slice('drugName_'.length), name: input.value })));
  const card = cards.find(entry => entry.name.toUpperCase().includes(name.toUpperCase()));
  h.assert(card, `No staged card for ${name}`);
  return card.id;
}

/** Every visible marker as "<marker id>: <text>", once the page has rendered what it was sent. */
async function shownMarkers(rx, expectedCount) {
  await rx.waitForFunction(count => document.querySelectorAll('[data-rx-interaction]').length > 0
    && [...document.querySelectorAll('[data-rx-interaction]')].filter(m => m.style.display === 'inline').length === count,
  expectedCount, { timeout: 15000 }).catch(() => {});
  return rx.locator('[data-rx-interaction]').evaluateAll(markers => markers
    .filter(marker => marker.style.display === 'inline')
    .map(marker => `${marker.id}: ${marker.textContent}`)
    .sort());
}

async function workflow(s) {
  const { sql, patient, provider } = s;
  const predicate = `provider_no=${h.sqlString(provider)} AND name='rxInteractionWarningLevel'`;
  const snapshotQuery = `SELECT id,COALESCE(HEX(value),''),value IS NULL FROM property WHERE ${predicate} ORDER BY id`;
  const original = sql.rows(snapshotQuery);
  const patientLevel = `demographic_no=${patient} AND key_val='rxInteractionWarningLevel'`;
  let seededDrug = null;
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
    sql.execute(`DELETE FROM drugs WHERE demographic_no=${patient}`);
    h.assert(JSON.stringify(drugs) === JSON.stringify(seededDrug ? [seededDrug] : []),
      `Drugs other than the seeded theophylline were saved for the patient: ${drugs.join(',')}`);
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

  const ciproTheo = drugRefPair(sql, CIPRO.atc, THEOPHYLLINE.atc);
  const ciproWarfarin = drugRefPair(sql, CIPRO.atc, WARFARIN.atc);
  h.assert(ciproTheo.severity === 'major' && ciproWarfarin.severity === 'moderate',
    `The fixture pairs are graded ${ciproTheo.severity}/${ciproWarfarin.severity} in DrugRef, not major/moderate`);
  const partnerOf = (pair, own) => pair.names.find(name => !name.includes(own));

  const rx = await s.popup(s.master, s.master.locator('a[onclick*="/rx/choosePatient"]').first(), 'rx-module');
  h.assert(new URL(rx.url()).searchParams.get('demographicNo') === patient, 'The Rx module opened for another patient');
  await rx.locator('#searchString').waitFor({ state: 'visible' });
  await rx.waitForLoadState('networkidle');
  let ciproCard;
  let warfarinCard;
  const theoName = partnerOf(ciproTheo, 'CIPRO');
  const warfarinName = partnerOf(ciproWarfarin, 'CIPRO');

  await s.step('staging ciprofloxacin marks its card with the major interaction against the patient\'s current theophylline', async () => {
    let body;
    body = await interactionCheck(rx, patient, async () => { ciproCard = await stageFromSearch(rx, CIPRO.term, CIPRO.name); });
    h.assert(body.status === 'checked' && body.level === 0, `Unexpected check: ${JSON.stringify(body)}`);
    h.assert(body.warnings.some(w => w.randomId === ciproCard && w.severity === 'major' && w.partner === theoName),
      `The answer has no major warning for the ciprofloxacin card: ${JSON.stringify(body.warnings)}`);
    const shown = await shownMarkers(rx, 1);
    h.assert(JSON.stringify(shown) === JSON.stringify([`major_${ciproCard}: ${LABEL.major}: ${theoName}`]),
      `Markers shown: ${JSON.stringify(shown)}`);
    const title = await rx.locator(`#major_${ciproCard}`).getAttribute('title');
    h.assert(title.includes(ciproTheo.summary) && (!ciproTheo.comment || title.includes(ciproTheo.comment)),
      `The marker tooltip does not carry DrugRef's detail: ${title}`);
    h.assert(!(await rx.locator('#interactingDrugErrorMsg').isVisible()), 'A clean check shows the unavailable message');
  });

  await s.step('staging warfarin beside it marks both staged cards with their moderate interaction', async () => {
    const body = await interactionCheck(rx, patient, async () => { warfarinCard = await stageFromSearch(rx, WARFARIN.term, WARFARIN.name); });
    h.assert(body.status === 'checked', `Unexpected check: ${JSON.stringify(body)}`);
    const shown = await shownMarkers(rx, 3);
    const expected = [
      `major_${ciproCard}: ${LABEL.major}: ${theoName}`,
      `moderate_${ciproCard}: ${LABEL.moderate}: ${warfarinName}`,
      `moderate_${warfarinCard}: ${LABEL.moderate}: ${partnerOf(ciproWarfarin, 'WARFARIN')}`,
    ].sort();
    h.assert(JSON.stringify(shown) === JSON.stringify(expected), `Markers shown: ${JSON.stringify(shown)}`);
  });

  const reloadWithLevel = async (expectedLevel) => {
    const body = await interactionCheck(rx, patient, () => rx.reload());
    await rx.locator('#searchString').waitFor({ state: 'visible' });
    h.assert(body.level === expectedLevel, `The check applied level ${body.level}, not ${expectedLevel}`);
    ciproCard = await cardFor(rx, CIPRO.name);
    warfarinCard = await cardFor(rx, WARFARIN.name);
    return body;
  };

  await s.step('the provider\'s High warning level hides the moderate interaction and keeps the major one', async () => {
    setProviderLevel('3');
    const body = await reloadWithLevel(3);
    h.assert(body.status === 'checked', `Unexpected check: ${JSON.stringify(body)}`);
    const shown = await shownMarkers(rx, 1);
    h.assert(JSON.stringify(shown) === JSON.stringify([`major_${ciproCard}: ${LABEL.major}: ${theoName}`]),
      `Markers shown at High: ${JSON.stringify(shown)}`);
  });

  await s.step('the provider\'s None warning level shows no interaction warning at all', async () => {
    setProviderLevel('4');
    const body = await reloadWithLevel(4);
    h.assert(body.status === 'off' && body.warnings.length === 0, `Unexpected check: ${JSON.stringify(body)}`);
    h.assert((await shownMarkers(rx, 0)).length === 0, 'A marker is shown at level None');
    h.assert(!(await rx.locator('#interactingDrugErrorMsg').isVisible()), 'Level None reads as a failed check');
  });

  await s.step('the patient\'s own warning level overrides the provider\'s', async () => {
    sql.execute(`DELETE FROM demographicExt WHERE ${patientLevel};
      INSERT INTO demographicExt(demographic_no,provider_no,key_val,value,date_time,hidden)
      VALUES (${patient},${h.sqlString(provider)},'rxInteractionWarningLevel','2',NOW(),'0')`);
    const body = await reloadWithLevel(2);
    h.assert(body.status === 'checked', `Unexpected check: ${JSON.stringify(body)}`);
    h.assert((await shownMarkers(rx, 3)).length === 3, 'The patient\'s Medium level does not show the major and moderate warnings');
  });

  await s.step('removing the warfarin card re-checks and clears the moderate warning from the ciprofloxacin card', async () => {
    const remove = rx.locator(`#set_${warfarinCard} a[onclick*="removePrescribingDrug"]`);
    await interactionCheck(rx, patient, () => remove.click());
    await rx.locator(`#set_${warfarinCard}`).waitFor({ state: 'detached' });
    const shown = await shownMarkers(rx, 1);
    h.assert(JSON.stringify(shown) === JSON.stringify([`major_${ciproCard}: ${LABEL.major}: ${theoName}`]),
      `Markers shown after removing warfarin: ${JSON.stringify(shown)}`);
  });

  await s.step('a check the server reports as unavailable is shown as unavailable, not as a clean card', async () => {
    const pattern = /\/rx\/interactionWarnings(\?|$)/;
    await rx.route(pattern, route => route.fulfill({ status: 200, contentType: 'application/json',
      body: JSON.stringify({ status: 'unavailable', level: 0, warnings: [] }) }));
    try {
      await interactionCheck(rx, patient, () => rx.reload());
      const message = rx.locator('#interactingDrugErrorMsg');
      await message.waitFor({ state: 'visible' });
      h.assert((await message.innerText()).includes(UNAVAILABLE), 'The unavailable message is not shown');
      h.assert((await shownMarkers(rx, 0)).length === 0, 'Markers are shown for an unavailable check');
    } finally {
      await rx.unroute(pattern);
    }
  });

  await s.step('rx/interactionWarnings answers only for a named patient whose Rx is open', async () => {
    const base = h.appUrl(s.config.baseUrl, '/rx/interactionWarnings');
    const unnamed = await s.context.request.get(base, { maxRedirects: 0 });
    h.assert(unnamed.status() === 400, `A request naming no patient answered HTTP ${unnamed.status()}`);
    const conflicting = await s.context.request.get(`${base}?demographicNo=${patient}&demographic_no=${Number(patient) + 1}`, { maxRedirects: 0 });
    h.assert(conflicting.status() === 400, `Conflicting patients answered HTTP ${conflicting.status()}`);
    const other = sql.value(`SELECT MIN(demographic_no) FROM demographic WHERE demographic_no<>${patient} AND patient_status='AC'`);
    const notOpen = await s.context.request.get(`${base}?demographicNo=${other}`, { maxRedirects: 0 });
    h.assert(notOpen.status() === 404, `A patient with no open Rx answered HTTP ${notOpen.status()}`);
    const own = await s.context.request.get(`${base}?demographicNo=${patient}`, { maxRedirects: 0 });
    h.assert(own.status() === 200 && /^application\/json/.test(own.headers()['content-type'] || ''),
      `The patient's own check answered HTTP ${own.status()}`);
    h.assert(own.headers()['cache-control'] === 'no-store', 'The interaction answer may be cached');
  });
}

if (require.main === module) runWorkflow('rx-interaction-warnings', workflow, { openPatient: true });
module.exports = { workflow };
