#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Timeline Drug Profile > select multiple products > reduce/clear selection.
const h=require('./lib/playwright-harness');
const ui=require('./lib/playwright-ui');
const {runWorkflow}=require('./lib/workflow-session');
const {prepareRx}=require('./lib/rx-ui-workflow');
const clear=process.env.RX_TIMELINE_ACTION==='clear';
const name=clear?'rx-timeline-empty-selection':'rx-timeline-multiple-selection';
async function workflow(s){
  const {sql,patient}=s,f=await prepareRx(s);
  const products=sql.rows("SELECT regional_identifier,MIN(BN) FROM drugs WHERE demographic_no<>"+patient+" AND customName IS NULL AND regional_identifier IS NOT NULL AND regional_identifier<>'' GROUP BY regional_identifier ORDER BY regional_identifier LIMIT 2");
  h.assert(products.length===2,'Two established product identifiers are required');
  for(const [din,brand]of products)f.seed('timeline',{regional_identifier:din,BN:brand,customName:null});
  f.seed('custom not graphed');
  const before=JSON.stringify(sql.rows(`SELECT * FROM drugs WHERE demographic_no=${patient} ORDER BY drugid`)),rx=await f.open();
  const chart=await s.popup(rx,rx.locator('a[href*="/rx/ViewChartDrugProfile"]').first(),'rx-timeline');
  const box=din=>chart.locator(`input[name="drug"][value="${din}"]`);
  const graph=chart.locator('img[src*="/encounter/GraphMeasurements"]');
  const submit=()=>ui.clickAndAwaitReload(chart,chart.locator('input[type="submit"]').first(),{label:'Add Meds to Graph'});
  const expectGraph=async expected=>{
    const url=new URL(await graph.evaluate(el=>el.src));
    h.assert(url.searchParams.get('demographic_no')===patient,'Timeline graphs another patient');
    h.assert(JSON.stringify(url.searchParams.getAll('drug').sort())===JSON.stringify([...expected].sort()),'Timeline graph differs from the checked selection');
    h.assert(await graph.evaluate(img=>img.complete&&img.naturalWidth>0),'Timeline image did not render');
  };
  await s.step('two selected products graph together while custom drugs are excluded',async()=>{
    h.assert(await chart.locator('input[name="drug"]').count()===2,'Timeline includes the custom medication or misses a product');
    for(const [din]of products)await box(din).check();await submit();await expectGraph(products.map(x=>x[0]));
  });
  await s.step(clear?'clearing every medication leaves an empty graph and no checked products':'reducing the selection graphs only the remaining checked product',async()=>{
    await box(products[0][0]).uncheck();if(clear)await box(products[1][0]).uncheck();await submit();
    h.assert(await chart.locator('input[name="drug"]:checked').count()===(clear?0:1),'Timeline restored an unselected medication');
    await expectGraph(clear?[]:[products[1][0]]);
    h.assert(JSON.stringify(sql.rows(`SELECT * FROM drugs WHERE demographic_no=${patient} ORDER BY drugid`))===before,'Timeline changed clinical records');
  });
}
if(require.main===module)runWorkflow(name,workflow);
module.exports={workflow};
