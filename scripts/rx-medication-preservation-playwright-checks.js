#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * End-to-end medication data preservation checks (PR #6424 / issues #6398, #6417):
 *   1. Staged card on SearchDrug3.jsp refuses count-based duration calculation for unit-qualified
 *      quantities ("30 mL"), preserves decimal quantities ("7.50") through Save Only into `drugs`.
 *   2. Staged card stale-draft HTTP 409 conflict (`STALE_RX_STASH`) surfaces the conflict message
 *      and Reopen button while keeping Retry hidden and blocking Save.
 *   3. Legacy WriteScript.jsp accepts decimal quantities ("7.50") on submitForm('update') instead
 *      of rejecting the decimal point with /\D/.
 *   4. BPMH form renders PRN in structured directions, rejects whitespace-only patient-reported
 *      medication names with HTTP 400 while preserving entered note, use, instruction, and Remove
 *      button, preserves editable `form.note` textarea and Remove buttons across HTTP 409 review
 *      conflict, saves `otc_reviewed=1` and reported rows cleanly, prints with `Cache-Control:
 *      no-store`, and rejects negative `formId` with HTTP 400.
 *   5. REST POST `/ws/rs/rx/favorites` with explicit `medicationDetails: { components: null }`
 *      creates a distinct favorite rather than wildcard-matching a multi-component favorite.
 *
 * Fixtures: owned synthetic FAKE-PW patient, owned allergy, prescriptions/drugs, formBPMH rows,
 * and provider favorites keyed by `marker`. Cleanup removes every created row and asserts none
 * remain; must not run concurrently with other checks (live wrapper: EXCLUSIVE=1).
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const {
  openRx, stageCustomDrugSettled, saveOnly, clearOwnedPrescriptionRows,
} = require('./rx-stash-patient-isolation-playwright-checks');
const { captureBpmhPrint } = require('./bpmh-form-playwright-checks');

const TIMEOUT = 30000;

function isRxActionPost(response, route, action) {
  const request = response.request();
  return request.method() === 'POST' && h.pathOnly(response.url()).endsWith(route)
    && new URLSearchParams(request.postData() || '').get('action') === action;
}

function consumeSingleExpectedFailure(recorder, status, pathSuffix, responseStart, consoleStart) {
  const responses = recorder.badResponses.slice(responseStart);
  const consoles = recorder.consoleIssues.slice(consoleStart);
  h.assert(responses.length === 1 && responses[0].status === status
    && h.pathOnly(responses[0].url).endsWith(pathSuffix),
  `Expected 1 HTTP ${status} for ${pathSuffix}, got ${JSON.stringify(responses)}`);
  const matchingConsoles = consoles.filter(entry => entry.type === 'error'
    && /Failed to load resource/.test(entry.text) && entry.text.includes(String(status)));
  h.assert(matchingConsoles.length <= 1 && matchingConsoles.length === consoles.length,
    `Unexpected console entries during expected HTTP ${status}: ${JSON.stringify(consoles)}`);
  recorder.badResponses.splice(responseStart, 1);
  if (consoles.length > 0) recorder.consoleIssues.splice(consoleStart, consoles.length);
}

async function workflow(s) {
  const { sql, patient, provider, marker, context, config, recorder } = s;
  const q = h.sqlString;
  const tag = marker.slice(-8);
  const favName = `${marker}-fav`;

  s.cleanup(() => {
    clearOwnedPrescriptionRows(sql, patient, marker);
    sql.execute(`DELETE FROM formBPMH WHERE demographic_no=${patient};
      DELETE FROM drugReason WHERE demographicNo=${patient};
      DELETE FROM drugs WHERE demographic_no=${patient};
      DELETE FROM allergies WHERE demographic_no=${patient};
      DELETE FROM favorites WHERE provider_no=${q(provider)} AND favoritename=${q(favName)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM formBPMH WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM allergies WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM favorites WHERE provider_no=${q(provider)} AND favoritename=${q(favName)})`) === '0',
    'Owned medication preservation fixtures were not removed');
  });

  // Step 1: Unit-qualified quantity refusal + decimal quantity persistence on SearchDrug3.jsp
  const rx = await openRx(s, patient);
  await s.step('unit-qualified quantity skips count-based duration and decimal quantity saves cleanly', async () => {
    const key = await stageCustomDrugSettled(rx, `${marker}-decimal`);
    await rx.locator(`#instructions_${key}`).fill('Take 1 tab po daily');
    const [parsed] = await Promise.all([
      rx.waitForResponse(r => isRxActionPost(r, '/rx/UpdateScript', 'parseInstructions'), { timeout: TIMEOUT }),
      rx.locator(`label[for="jsonDxSearch_${key}"]`).click(),
    ]);
    h.assert(parsed.status() === 200, `parseInstructions answered HTTP ${parsed.status()}`);

    const qty = rx.locator(`#quantity_${key}`);
    await qty.fill('30 mL');
    const [unitResp] = await Promise.all([
      rx.waitForResponse(r => isRxActionPost(r, '/rx/WriteScript', 'updateQty'), { timeout: TIMEOUT }),
      qty.blur(),
    ]);
    h.assert(unitResp.status() === 200, `updateQty(30 mL) answered HTTP ${unitResp.status()}`);
    const unitBody = await unitResp.json();
    h.assert(String(unitBody.calQuantity) === '30' && unitBody.unitName === 'mL'
      && (unitBody.duration === null || unitBody.duration === ''),
    `Unit-qualified quantity should leave duration blank, got ${JSON.stringify(unitBody)}`);

    await qty.fill('7.50');
    const [decResp] = await Promise.all([
      rx.waitForResponse(r => isRxActionPost(r, '/rx/WriteScript', 'updateQty'), { timeout: TIMEOUT }),
      qty.blur(),
    ]);
    h.assert(decResp.status() === 200, `updateQty(7.50) answered HTTP ${decResp.status()}`);
    const decBody = await decResp.json();
    h.assert(String(decBody.calQuantity) === '7.5', `Expected canonical calQuantity 7.5, got ${JSON.stringify(decBody)}`);

    await saveOnly(rx);
    await rx.locator(`#set_${key}`).waitFor({ state: 'detached', timeout: TIMEOUT });
    await expectValue(sql, `SELECT quantity FROM drugs WHERE demographic_no=${patient} AND customName=${q(`${marker}-decimal`)}`,
      '7.50', 'Saved drug did not preserve decimal quantity 7.50');
  });

  // Step 2: Stale-draft 409 conflict shows Reopen button (and keeps Retry hidden)
  let staleKey;
  let staleRealRevision;
  await s.step('stale-draft 409 calculation conflict surfaces Reopen button and blocks Save', async () => {
    staleKey = await stageCustomDrugSettled(rx, `${marker}-stale`);
    staleRealRevision = await rx.locator(`#draftRevision_${staleKey}`).inputValue();
    await rx.locator(`#draftRevision_${staleKey}`).evaluate(el => { el.value = 'stale-revision-check'; });
    const responseStart = recorder.badResponses.length;
    const consoleStart = recorder.consoleIssues.length;
    const qty = rx.locator(`#quantity_${staleKey}`);
    await qty.fill('14');
    const [conflictResp] = await Promise.all([
      rx.waitForResponse(r => isRxActionPost(r, '/rx/WriteScript', 'updateQty'), { timeout: TIMEOUT }),
      qty.blur(),
    ]);
    h.assert(conflictResp.status() === 409, `Expected 409 for stale draftRevision, got HTTP ${conflictResp.status()}`);
    const conflictBody = await conflictResp.json();
    h.assert(conflictBody.error === 'STALE_RX_STASH',
      `Expected STALE_RX_STASH error code, got ${JSON.stringify(conflictBody)}`);
    await rx.locator(`#rxCalculationReopen_${staleKey}`).waitFor({ state: 'visible', timeout: TIMEOUT });
    h.assert(await rx.locator(`#rxCalculationRetry_${staleKey}`).isHidden(),
      'Retry button must stay hidden on STALE_RX_STASH conflict');
    h.assert(await rx.locator(`#set_${staleKey}`).getAttribute('data-calculation-blocked') === 'true',
      'Conflicted card must remain calculation-blocked');
    consumeSingleExpectedFailure(recorder, 409, '/rx/WriteScript', responseStart, consoleStart);
  });

  // Step 3: Legacy WriteScript.jsp accepts decimal quantity 7.50 on Update
  await s.step('legacy WriteScript.jsp accepts decimal quantity 7.50 without rejecting the decimal point', async () => {
    const legacy = await context.newPage();
    await h.gotoApp(legacy, config.baseUrl,
      `/rx/stash?demographicNo=${patient}&action=edit&randomId=${encodeURIComponent(staleKey)}&draftRevision=${encodeURIComponent(staleRealRevision)}`);
    await h.assertNotErrorPage(legacy, 'legacy WriteScript editor');
    await legacy.locator('input[name="quantity"]').waitFor({ state: 'visible', timeout: TIMEOUT });
    await legacy.locator('textarea[name="special"]').fill('Take 1 tablet by mouth daily');
    await legacy.locator('input[name="quantity"]').fill('7.50');
    const [submitted] = await Promise.all([
      legacy.waitForResponse(r => r.request().method() === 'POST'
        && h.pathOnly(r.url()).endsWith('/rx/writeScript'), { timeout: TIMEOUT }),
      legacy.evaluate(() => submitForm('update')),
    ]);
    h.assert(submitted.status() === 200, `Legacy WriteScript update answered HTTP ${submitted.status()}`);
    await legacy.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
    await h.assertNotErrorPage(legacy, 'legacy WriteScript after decimal quantity update');
    const afterValue = await legacy.locator('input[name="quantity"]').inputValue();
    h.assert(afterValue === '7.5' || afterValue === '7.50', `Legacy WriteScript quantity changed to ${JSON.stringify(afterValue)}`);
    await legacy.close();
  });
  await rx.close();

  // Step 4: BPMH PRN fallback, whitespace-only 400 validation, 409 editable note/Remove, save + print Cache-Control, negative formId 400
  await s.step('BPMH preserves PRN, validates whitespace-only reported row, keeps editable note on 409, saves OTC flag and sets no-store on Print', async () => {
    sql.execute(`INSERT INTO allergies (demographic_no,entry_date,DESCRIPTION,TYPECODE,reaction,archived,start_date,age_of_onset,
        severity_of_reaction,onset_of_reaction,life_stage,position,lastUpdateDate,providerNo,nonDrug)
      VALUES (${patient},CURDATE(),${q(`FAKE ${tag} latex`)},0,'hives',0,NULL,0,'2','1','A',0,NOW(),${q(provider)},0)`);
    const prnDrugId = sql.value(`INSERT INTO drugs (provider_no,demographic_no,rx_date,end_date,written_date,customName,special,archived,archived_reason,
        create_date,position,lastUpdateDate,dispenseInternal,method,takemin,takemax,unit,freqcode,route,prn,quantity,duration,durunit)
      VALUES (${q(provider)},${patient},CURDATE(),DATE_ADD(CURDATE(),INTERVAL 30 DAY),CURDATE(),${q(`FAKE ${tag} ibuprofen`)},
        ${q(`FAKE ${tag} ibuprofen 200 mg`)},0,'',NOW(),1,NOW(),0,'Take',1,2,'TAB','Q6H','PO',1,'30','30','D'); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(prnDrugId), 'PRN drug fixture was not created');

    const bpmh = await context.newPage();
    await h.gotoApp(bpmh, config.baseUrl, `/formBPMH?demographic_no=${patient}&formId=0`);
    await h.assertNotErrorPage(bpmh, 'BPMH draft');
    const initialBody = await bpmh.locator('body').innerText();
    h.assert(initialBody.includes('PRN'), 'BPMH structured directions did not include PRN for PRN drug');

    const draftNote = `${marker} reconciliation draft note`;
    await bpmh.locator('#bpmhNote').fill(draftNote);
    await bpmh.locator('#bpmhAddReportedMedication').click();
    const reportedName = bpmh.locator('input[name$=".reportedMedication"]').first();
    await reportedName.fill('   ');
    await bpmh.locator('tr:has(input[name$=".reportedMedication"]) textarea[name$=".patientReportedUse"]').fill('Taken at bedtime');
    await bpmh.locator('tr:has(input[name$=".reportedMedication"]) textarea[name$=".instruction"]').fill('Verify OTC label');

    // Bypass HTML5 client validation to exercise server-side HTTP 400 validation & draft preservation
    const respStart400 = recorder.badResponses.length;
    const consoleStart400 = recorder.consoleIssues.length;
    await bpmh.locator('form').evaluate(f => { f.noValidate = true; });
    const [resp400] = await Promise.all([
      bpmh.waitForResponse(r => r.request().method() === 'POST' && h.pathOnly(r.url()).endsWith('/formBPMH'), { timeout: TIMEOUT }),
      bpmh.locator('button[name="method"][value="save"]').click(),
    ]);
    h.assert(resp400.status() === 400, `Whitespace-only reported medicine answered HTTP ${resp400.status()}, expected 400`);
    await bpmh.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
    h.assert(await bpmh.locator('#bpmhNote').inputValue() === draftNote, 'HTTP 400 lost editable form.note text');
    h.assert(await bpmh.locator('[data-remove-reported-medication]').count() === 1,
      'HTTP 400 lost Remove button on server-rendered reported medication row');
    h.assert(await bpmh.locator('tr:has(input[name$=".reportedMedication"]) textarea[name$=".patientReportedUse"]').inputValue() === 'Taken at bedtime',
      'HTTP 400 lost patientReportedUse on invalid reported row');
    h.assert(sql.value(`SELECT COUNT(*) FROM formBPMH WHERE demographic_no=${patient}`) === '0',
      'Invalid whitespace-only reported row saved a formBPMH record');
    consumeSingleExpectedFailure(recorder, 400, '/formBPMH', respStart400, consoleStart400);

    // Fix reported medicine name, then mutate chart drug to trigger HTTP 409 review conflict
    await bpmh.locator('input[name$=".reportedMedication"]').first().fill('Vitamin D3 1000 IU');
    sql.execute(`UPDATE drugs SET special=${q(`FAKE ${tag} ibuprofen 200 mg\nTake 1 tab Q6H PRN updated`)} WHERE drugid=${prnDrugId}`);

    const respStart409 = recorder.badResponses.length;
    const consoleStart409 = recorder.consoleIssues.length;
    const [resp409] = await Promise.all([
      bpmh.waitForResponse(r => r.request().method() === 'POST' && h.pathOnly(r.url()).endsWith('/formBPMH'), { timeout: TIMEOUT }),
      bpmh.locator('button[name="method"][value="save"]').click(),
    ]);
    h.assert(resp409.status() === 409, `Stale BPMH review answered HTTP ${resp409.status()}, expected 409`);
    await bpmh.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
    h.assert(await bpmh.locator('#bpmhNote').inputValue() === draftNote,
      'HTTP 409 review conflict replaced editable form.note textarea or lost note');
    h.assert(await bpmh.locator('[data-remove-reported-medication]').count() === 1,
      'HTTP 409 review conflict lost Remove button on server-rendered reported row');
    consumeSingleExpectedFailure(recorder, 409, '/formBPMH', respStart409, consoleStart409);

    // Confirm OTC reviewed and save cleanly
    await bpmh.locator('#confirm').check();
    const [respSave] = await Promise.all([
      bpmh.waitForResponse(r => r.request().method() === 'POST' && h.pathOnly(r.url()).endsWith('/formBPMH'), { timeout: TIMEOUT }),
      bpmh.locator('button[name="method"][value="save"]').click(),
    ]);
    h.assert(respSave.status() === 302, `BPMH save answered HTTP ${respSave.status()}, expected 302`);
    await bpmh.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
    const savedId = new URL(bpmh.url()).searchParams.get('formId');
    h.assert(/^[1-9]\d*$/.test(savedId || ''), `Saved BPMH URL did not carry formId: ${bpmh.url()}`);
    h.assert(sql.value(`SELECT otc_reviewed FROM formBPMH WHERE id=${savedId}`) === '1',
      'Saved formBPMH row did not persist otc_reviewed=1');
    h.assert(sql.value(`SELECT LOCATE('Vitamin D3 1000 IU', drugs) > 0 FROM formBPMH WHERE id=${savedId}`) === '1',
      'Saved formBPMH row lost patient-reported medication');

    // Capture CSRF token before Print navigates the tab to the inline PDF viewer
    const csrf = await bpmh.locator('input[name="CSRF-TOKEN"]').first().inputValue();

    // Print saved form and assert Cache-Control: no-store
    const pdf = await captureBpmhPrint(bpmh, { patient, saved: savedId });
    h.assert(pdf.status === 200 && /^application\/pdf(?:\s*;|$)/i.test(pdf.headers['content-type'] || ''),
      `BPMH print answered HTTP ${pdf.status} ${pdf.headers['content-type']}`);
    h.assert((pdf.headers['cache-control'] || '').toLowerCase().includes('no-store'),
      `BPMH print response missing Cache-Control: no-store (${pdf.headers['cache-control']})`);

    // Negative formId print probe must return HTTP 400
    const negResp = await context.request.post(h.appUrl(config.baseUrl, '/formBPMH'), {
      headers: { 'CSRF-TOKEN': csrf },
      form: { 'CSRF-TOKEN': csrf, method: 'print', 'form.demographicNo': String(patient), 'form.formId': '-1' },
      maxRedirects: 0,
      failOnStatusCode: false,
    });
    const negStatus = negResp.status();
    await negResp.dispose();
    h.assert(negStatus === 400, `Negative formId print answered HTTP ${negStatus}, expected 400`);
    await bpmh.close();
  });

  // Step 5: REST favorites explicit null components vs existing multi-component favorite
  await s.step('REST POST /ws/rs/rx/favorites treats explicit medicationDetails.components=null as distinct from a multi-component favorite', async () => {
    const restFavUrl = `${config.baseUrl}/ws/rs/rx/favorites`;
    const basePayload = {
      favoriteName: favName,
      brandName: 'Hydrocortisone / Clotrimazole',
      genericName: 'Hydrocortisone / Clotrimazole',
      providerNo: Number(provider),
      takeMin: 1,
      takeMax: 1,
      frequency: 'BID',
      duration: 14,
      durationUnit: 'D',
      repeats: 0,
      prn: false,
      noSubstitutions: false,
      instructions: 'Apply sparingly BID x 14 days',
      method: 'Apply',
      route: 'TOP',
      form: 'CRM',
    };
    const multiResp = await context.request.post(restFavUrl, {
      headers: { Accept: 'application/json' },
      data: {
        ...basePayload,
        medicationDetails: {
          quantityText: '7.5',
          dispenseUnit: 'g',
          components: [
            { name: 'Hydrocortisone', strength: '1', unit: '%' },
            { name: 'Clotrimazole', strength: '1', unit: '%' },
          ],
        },
      },
    });
    h.assert(multiResp.status() === 200, `Multi-component favorite POST answered HTTP ${multiResp.status()}`);
    const multiBody = await multiResp.json();
    await multiResp.dispose();
    h.assert(multiBody.status === 'SUCCESS', `Multi-component favorite failed: ${JSON.stringify(multiBody)}`);

    const clearedResp = await context.request.post(restFavUrl, {
      headers: { Accept: 'application/json' },
      data: {
        ...basePayload,
        medicationDetails: {
          quantityText: '7.5',
          dispenseUnit: 'g',
          components: null,
        },
      },
    });
    h.assert(clearedResp.status() === 200, `Explicit-null favorite POST answered HTTP ${clearedResp.status()}`);
    const clearedBody = await clearedResp.json();
    await clearedResp.dispose();
    h.assert(clearedBody.status === 'SUCCESS', `Explicit-null favorite failed: ${JSON.stringify(clearedBody)}`);

    const counts = sql.rows(`SELECT COUNT(*), SUM(medication_components IS NOT NULL), SUM(medication_components IS NULL)
      FROM favorites WHERE provider_no=${q(provider)} AND favoritename=${q(favName)}`)[0];
    h.assert(JSON.stringify(counts) === JSON.stringify(['2', '1', '1']),
      `Expected 2 distinct favorites (1 with components, 1 null), got ${JSON.stringify(counts)}`);
  });
}

if (require.main === module) runWorkflow('rx-medication-preservation', workflow);
module.exports = { workflow };
