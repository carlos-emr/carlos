#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
// #5163: explicit partial_date metadata from imports/older saves explains the placeholder
// beside a normal picker. An unrelated edit keeps precision; exact confirmation clears it.
// Owned FAKE patient only; cleanup removes all owned clinical rows and precision metadata.
const { assert, sqlString } = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const ownedAllergies = `SELECT allergyid FROM allergies WHERE demographic_no=${patient}`;
  const ownedPreventions = `SELECT id FROM preventions WHERE demographic_no=${patient}`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM partial_date WHERE (table_name=1 AND table_id IN (${ownedAllergies}))
      OR (table_name=4 AND table_id IN (${ownedPreventions}));
      DELETE FROM allergies WHERE demographic_no=${patient};
      DELETE FROM preventionsExt WHERE prevention_id IN (${ownedPreventions});
      DELETE FROM preventions WHERE demographic_no=${patient}`);
    assert(sql.value(`SELECT COUNT(*) FROM allergies WHERE demographic_no=${patient}`) === '0', 'Allergy cleanup failed');
    assert(sql.value(`SELECT COUNT(*) FROM preventions WHERE demographic_no=${patient}`) === '0', 'Prevention cleanup failed');
  });
  const cases = [['2019-01-01', 'YYYY', 'Recorded year only: 2019.'],
    ['2019-05-01', 'YYYY-MM', 'Recorded year and month only: May 2019.'], ['2019-01-01', null, null]];
  const allergies = [], preventions = [];
  for (const [date, format] of cases) {
    const allergy = sql.value(`INSERT INTO allergies (demographic_no,entry_date,start_date,DESCRIPTION,TYPECODE,reaction,nonDrug,archived,position,lastUpdateDate,providerNo)
      VALUES (${patient},CURDATE(),${sqlString(date)},${sqlString(marker + (format || 'full'))},0,'FAKE rash',1,0,0,NOW(),${sqlString(provider)}); SELECT LAST_INSERT_ID()`);
    const prevention = sql.value(`INSERT INTO preventions (demographic_no,creation_date,prevention_date,provider_no,prevention_type,deleted,refused,never,creator,lastUpdateDate)
      VALUES (${patient},NOW(),${sqlString(date)},${sqlString(provider)},'Inf',0,0,0,${sqlString(provider)},NOW()); SELECT LAST_INSERT_ID()`);
    assert(/^[1-9]\d*$/.test(allergy || '') && /^[1-9]\d*$/.test(prevention || ''), 'Fixtures were not persisted');
    if (format) sql.execute(`INSERT INTO partial_date (table_name,table_id,field_name,format)
      VALUES (1,${allergy},1,${sqlString(format)}),(4,${prevention},7,${sqlString(format)})`);
    sql.execute(`INSERT INTO preventionsExt (prevention_id,keyval,val) VALUES (${prevention},'comments','FAKE original comment'),
      (${prevention},'cdsDatePrecision',${sqlString(format === 'YYYY' ? 'YEAR' : format === 'YYYY-MM' ? 'YEAR_MONTH' : 'FULL_DATE')})`);
    allergies.push(allergy); preventions.push(prevention);
  }
  const chart = await s.chart();
  const list = await s.popup(chart, chart.locator('a[onclick*="showAllergy"]').first(), 'allergy-list');
  for (let i = 0; i < cases.length; i++) {
    const [date, format, explanation] = cases[i];
    await s.step(`allergy ${format || 'full'} explanation and amendment`, async () => {
      const row = list.locator(`#allergy_${allergies[i]}`);
      const notes = row.locator('.partial-date-explanation');
      assert(explanation ? (await notes.innerText()).includes(explanation) : !(await notes.innerText()).trim(), 'Allergy explanation differs from explicit precision');
      await row.locator('a.modifyAllergyLink').click();
      const form = list.locator('#RxAddAllergyForm');
      await form.waitFor({ state: 'visible' });
      assert(await form.locator('#startDate').inputValue() === date, 'Allergy picker lacks normalized date');
      if (explanation) assert((await form.locator('#startDateExplanation').innerText()).includes(explanation), 'Edit explanation missing');
      await form.locator('#reactionDescription').fill('FAKE amended rash');
      await Promise.all([list.waitForEvent('load'), form.locator('input[type="submit"]').click()]);
      await expectValue(sql, `SELECT archived FROM allergies WHERE allergyid=${allergies[i]}`, '1', 'Original allergy was not archived');
      const replacement = sql.value(`SELECT allergyid FROM allergies WHERE demographic_no=${patient} AND archived=0 AND DESCRIPTION=${sqlString(marker + (format || 'full'))}`);
      const precision = sql.value(`SELECT IFNULL(MAX(format),'full') FROM partial_date WHERE table_name=1 AND field_name=1 AND table_id=${replacement}`);
      assert(precision === (format || 'full'), 'Unrelated allergy edit changed precision');
    });
  }
  const index = await s.popup(chart, chart.locator('a[onclick*="ViewPreventionIndex"]').first(), 'prevention-index');
  for (let i = 0; i < cases.length; i++) {
    const [date, format, explanation] = cases[i];
    await s.step(`prevention ${format || 'full'} picker, explanation and comment amendment`, async () => {
      const link = index.locator(`[onclick*="ViewAddPreventionData"][onclick*="id=${preventions[i]}"]`).first();
      await link.waitFor({ state: 'visible' });
      if (explanation) assert((await link.innerText()).includes(explanation), 'Prevention list explanation missing');
      const editor = await s.popup(index, link, 'prevention-editor');
      const dateBox = editor.locator('#prevDate');
      assert(await dateBox.getAttribute('type') === 'date' && await dateBox.inputValue() === date, 'Native picker lost its placeholder');
      if (explanation) assert((await editor.locator('#prevDateExplanation').innerText()).includes(explanation), 'Prevention edit explanation missing');
      await editor.locator('[name="comments"]').fill('FAKE original comment, amended');
      await Promise.all([editor.waitForEvent('close'), editor.locator('input[type="submit"][name="action"]').first().click()]);
      await expectValue(sql, `SELECT deleted FROM preventions WHERE id=${preventions[i]}`, '1', 'Original prevention was not replaced');
      const replacement = sql.value(`SELECT p.id FROM preventions p JOIN preventionsExt x ON x.prevention_id=p.id AND x.keyval='previousId' AND x.val=${sqlString(preventions[i])} WHERE p.demographic_no=${patient} AND p.deleted=0`);
      assert(sql.value(`SELECT IFNULL(MAX(format),'full') FROM partial_date WHERE table_name=4 AND field_name=7 AND table_id=${replacement}`) === (format || 'full'), 'Comment amendment changed prevention precision');
      assert(sql.value(`SELECT COUNT(*) FROM preventionsExt WHERE prevention_id=${replacement} AND keyval='comments'`) === '1', 'Comment was lost or duplicated');
      assert(sql.value(`SELECT val FROM preventionsExt WHERE prevention_id=${replacement} AND keyval='comments'`) === 'FAKE original comment, amended', 'Comment value changed');
      const expectedPrecision = format === 'YYYY' ? 'YEAR' : format === 'YYYY-MM' ? 'YEAR_MONTH' : 'FULL_DATE';
      const checkProvenance = id => {
        assert(sql.value(`SELECT COUNT(*) FROM preventionsExt WHERE prevention_id=${id} AND keyval='cdsDatePrecision'`) === '1', 'Import provenance was lost or duplicated');
        assert(sql.value(`SELECT val FROM preventionsExt WHERE prevention_id=${id} AND keyval='cdsDatePrecision'`) === expectedPrecision, 'Source precision value changed');
      };
      checkProvenance(replacement);
      await index.reload({ waitUntil: 'load' });
      if (format) {
        const current = index.locator(`[onclick*="ViewAddPreventionData"][onclick*="id=${replacement}"]`).first();
        const exactEditor = await s.popup(index, current, 'exact-prevention-editor');
        await exactEditor.locator('[name="prevDateConfirmedExact"]').check();
        await Promise.all([exactEditor.waitForEvent('close'), exactEditor.locator('input[type="submit"][name="action"]').first().click()]);
        const exact = sql.value(`SELECT p.id FROM preventions p JOIN preventionsExt x ON x.prevention_id=p.id AND x.keyval='previousId' AND x.val=${sqlString(replacement)} WHERE p.demographic_no=${patient} AND p.deleted=0`);
        assert(sql.value(`SELECT COUNT(*) FROM partial_date WHERE table_name=4 AND field_name=7 AND table_id=${exact}`) === '0', 'Exact-date confirmation retained partial precision');
        checkProvenance(exact);
        await index.reload({ waitUntil: 'load' });
      }
    });
  }
}
if (require.main === module) runWorkflow('imported-partial-date-notes', workflow);
module.exports = { workflow };
