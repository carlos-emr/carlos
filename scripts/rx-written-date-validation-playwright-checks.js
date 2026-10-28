#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// A prescriber corrects an impossible/future written date without losing the
// staged medication, then saves once with a valid date.
const h=require('./lib/playwright-harness');
const {runWorkflow,expectValue}=require('./lib/workflow-session');
const {prepareRx,saveOnly}=require('./lib/rx-ui-workflow');
const {stageCustomDrug}=require('./rx-stash-patient-isolation-playwright-checks');
const future=process.env.RX_WRITTEN_DATE_ACTION!=='impossible';
const name=future?'rx-written-date-future-correction':'rx-written-date-impossible-correction';
async function workflow(s){
  const {sql,patient,marker}=s,f=await prepareRx(s),rx=await f.open(),key=await stageCustomDrug(rx,marker+' dated');
  await rx.locator(`#instructions_${key}`).fill('1 tab PO OD x 30 days');
  await Promise.all([rx.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/rx/UpdateScript')),rx.locator(`label[for="jsonDxSearch_${key}"]`).click()]);
  await rx.locator(`#moreLessWord_${key}`).click();
  const bad=future?sql.value("SELECT DATE_FORMAT(DATE_ADD(CURDATE(),INTERVAL 10 DAY),'%Y-%m-%d')"):'2026-02-31';
  await s.step('an invalid written date is refused visibly and retains the unsaved medication',async()=>{
    await rx.locator(`#writtenDate_${key}`).fill(bad);
    const dialogs=await h.withExpectedDialogs(rx,async()=>{
      await rx.locator('#saveOnlyButton').click();
      await rx.waitForLoadState('networkidle');
    });
    h.assert(dialogs.some(x=>x.type==='alert'&&/date|day|future|valid/i.test(x.text)),`Invalid written date was not explained; saved dates: ${JSON.stringify(sql.rows(`SELECT DATE(written_date) FROM drugs WHERE demographic_no=${patient}`))}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`)==='0','Invalid written date saved a medication');
    h.assert(await rx.locator(`#drugName_${key}`).count()===1,'Date refusal lost the staged card');
  });
  await s.step('correcting the date on the same card saves one prescription with the exact valid day',async()=>{
    await rx.locator(`#writtenDate_${key}`).fill('2026-02-28');await saveOnly(rx);
    await expectValue(sql,`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`,'1','Date correction did not save exactly once');
    h.assert(sql.value(`SELECT DATE(written_date) FROM drugs WHERE demographic_no=${patient}`)==='2026-02-28','Written date was normalized to another day');
  });
}
if(require.main===module)runWorkflow(name,workflow);
module.exports={workflow};
