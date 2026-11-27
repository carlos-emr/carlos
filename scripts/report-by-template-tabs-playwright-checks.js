#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
// #5813: a lone empty text parameter survives upload/edit/reopen/run.
// #5725: four native report instances in ONE context/login, including two
// additional instances of the same template. Never intercept the clear beacon.
// #5917: CSV guards stay intact while XLSX restores raw text and padded numeric cells.
// #6236 (829c8033b91e): reportByTemplateLayout.css scrolls a wide result inside its region, wraps
// template XML instead of widening the page, and hides the navigation bar when a template is printed.
const fs=require('node:fs');
const h=require('./lib/playwright-harness');
const {runWorkflow}=require('./lib/workflow-session');
const {openAdmin,navigates}=require('./billing-on-admin-config-playwright-checks');
const {revealAuditLink}=require('./lib/playwright-link-audit');
const x=require('./lib/export-content-helpers');
const q=h.sqlString;
async function workflow(s) {
  const {sql,marker,patient,provider}=s;
  const scratch=x.scratchDir();
  s.cleanup(()=>fs.rmSync(scratch,{recursive:true,force:true}));
  let second;
  const secondName='O\'Neil, "A&B"';
  const titles=[marker+' TAB A',marker+' TAB B',marker+' TAB WIDE'];
  s.cleanup(()=>{
    sql.execute(`DELETE FROM reportTemplates WHERE templatetitle IN (${titles.map(q).join(',')})`);
    h.assert(sql.value(`SELECT COUNT(*) FROM reportTemplates WHERE templatetitle IN (${titles.map(q).join(',')})`)==='0','Owned tab templates remain');
    // Recover setup even if INSERT succeeded but its ID read was interrupted.
    const ids=sql.rows(`SELECT demographic_no FROM demographic WHERE last_name=${q(marker)} AND first_name=${q(secondName)}`).flat();
    for(const id of ids){h.assert(/^[1-9]\d*$/.test(id),'Invalid owned second patient');sql.execute(`DELETE FROM demographic WHERE demographic_no=${id} AND last_name=${q(marker)}`);}
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE last_name=${q(marker)} AND first_name=${q(secondName)}`)==='0','Owned second report patient remains');
  });
  second=sql.value(`INSERT INTO demographic(last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,provider_no,hc_type,province,roster_status,lastUpdateDate)
    VALUES(${q(marker)},${q(secondName)},'1975','03','04','M','AC',${q(provider)},'ON','ON','NR',NOW()); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(second),'Second owned report patient was not created');
  const clinical=()=>JSON.stringify({patients:sql.rows(`SELECT * FROM demographic WHERE demographic_no IN (${patient},${second}) ORDER BY demographic_no`),
    ext:sql.rows(`SELECT * FROM demographicExt WHERE demographic_no IN (${patient},${second}) ORDER BY id`)});
  const before=clinical();
  const templates=[
    {title:titles[0],parameterType:'text',header:['patient_id','surname','given','sex_code'],types:['number','text','text','text'],
      query:`SELECT demographic_no AS patient_id,last_name AS surname,first_name AS given,sex AS sex_code FROM demographic WHERE last_name='${marker}' AND sex='{pick}' ORDER BY demographic_no`,
      row:sex=>[sex==='F'?patient:second,marker,sex==='F'?'Workflow':secondName,sex]},
    {title:titles[1],parameterType:'list',header:['patient_label','charge','sex_code','phone','handle','note','formula','spaced_amount','literal_tab'],
      types:['text','number','text','text','text','text','text','number','text'],
      query:`SELECT CONCAT('T2-',first_name) AS patient_label,CAST('12.34' AS DECIMAL(10,2)) AS charge,sex AS sex_code,'+1 555-0123' AS phone,'@FAKE' AS handle,'- see note' AS note,'=1+1' AS formula,CAST(' 12.50' AS CHAR) AS spaced_amount,CONCAT(CHAR(9),'FAKE tab') AS literal_tab FROM demographic WHERE last_name='${marker}' AND sex='{pick}' ORDER BY demographic_no`,
      row:sex=>['T2-'+(sex==='F'?'Workflow':secondName),'12.34',sex,'+1 555-0123','@FAKE','- see note','=1+1',' 12.50','\tFAKE tab'],
      // CSV keeps its transport guards; the workbook uses raw text and numeric cells.
      csvRow:sex=>['T2-'+(sex==='F'?'Workflow':secondName),'12.34',sex,'\t+1 555-0123','\t@FAKE','\t- see note','\t=1+1',' 12.50','\t\tFAKE tab']},
  ];
  const admin=await openAdmin(s);
  const link=admin.getByRole('link',{name:'Report by Template',exact:true,includeHidden:true});
  await revealAuditLink(admin,link,20000);await link.click();
  const iframe=admin.locator('#dynamic-content iframe#myFrame');await iframe.waitFor();
  const library=await(await iframe.elementHandle()).contentFrame();
  h.assert(library,'Report library frame did not open');
  await library.waitForURL(/\/reportByTemplate\/ViewHomePage/);
  const click=locator=>navigates(admin,library,locator);
  async function upload(template) {
    await click(library.getByRole('link',{name:'Add Template',exact:true}));
    const escape=value=>value.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/"/g,'&quot;');
    const parameter=template.parameterType==='text'
      ? '<param\nid="pick"\ttype="text" description="Sex" meta:hint="optional"/><!-- <param id="commentedOut" type="text" description="Unused"/> -->'
      : '<param id="pick" type="list" description="Sex"><choice id="F">Female</choice><choice id="M">Male</choice><choice id="X">No matching patient</choice></param>';
    const root=template.parameterType==='text'?'r:report':'report';
    const xml=`<${root} xmlns:r="urn:carlos:report" xmlns:meta="urn:carlos:regression-metadata" title="${template.title}" description="${marker} tab isolation" active="1"><query>${escape(template.query)}</query>${parameter}</${root}>`;
    await library.locator('#uploadReportXml').setInputFiles({name:'owned-report-tabs.xml',mimeType:'text/xml',buffer:Buffer.from(xml)});
    await click(library.locator('input[type="submit"][value^="Upload"]'));
    await library.locator('.alert-success',{hasText:'Saved Successfully'}).waitFor();
    template.id=sql.value(`SELECT GROUP_CONCAT(templateid) FROM reportTemplates WHERE templatetitle=${q(template.title)}`);
    h.assert(/^[1-9]\d*$/.test(template.id),'Upload did not create exactly one owned template');
    h.assert(sql.value(`SELECT templatesql FROM reportTemplates WHERE templateid=${template.id}`)===template.query,'Uploaded template SQL changed');
  }
  await s.step('two distinguishable report templates upload through Administration',async()=>{
    for(const template of templates)await upload(template);
    await click(library.getByRole('link',{name:'Template Library',exact:true}));
    await library.locator('#userSearch').fill(marker);
  });
  async function open(template) {
    const target=library.getByRole('link',{name:template.title,exact:true});
    // A genuine middle-click on the library href opens a browser tab sharing
    // this context's existing JSESSIONID. No copied/new login or report GET.
    const [page]=await Promise.all([s.context.waitForEvent('page',{timeout:20000}),target.click({button:'middle'})]);
    await page.waitForLoadState('load');
    h.assert(page.context()===s.context&&new URL(page.url()).searchParams.get('templateid')===template.id,'Report tab lost its session or template');
    await page.locator(template.parameterType==='text'?'input#pick':'select#pick').waitFor();
    h.assert(await page.locator('#commentedOut').count()===0,'A commented parameter became an input');
    return {page,template};
  }
  const beacons=[];
  const isBeacon=request=>new URL(request.url()).pathname.endsWith('/oscarReport/reportByTemplate/ViewClearSession');
  s.context.on('request',request=>{if(isBeacon(request))beacons.push(request);});
  async function table(instance,sex) {
    const {page,template}=instance;instance.sex=sex;
    h.assert((await page.locator('h3').innerText()).includes(template.title),'Result has another template title');
    if(sex==='X') {
      h.assert(await page.locator('table#report2').count()===0,'Empty report substituted a stale table');
      h.assert((await page.locator('body').innerText()).includes('The query returned no results.'),'Empty report has no explicit empty-result feedback');
      h.assert(await page.locator('input[name="csv"],input[name="getCSV"],input[name="getXLS"]').count()===0,'Empty report offered stale export data');
    } else {
      const header=(await page.locator('#report2 thead th').allInnerTexts()).map(text=>text.trim());
      const rows=[];for(const tr of await page.locator('#report2 tbody tr').all())rows.push((await tr.locator('td').allInnerTexts()).map(text=>text.trim()));
      const displayed=template.row(sex).map(value=>String(value).trim());
      h.assert(JSON.stringify(header)===JSON.stringify(template.header)&&JSON.stringify(rows)===JSON.stringify([displayed]),'Report headers or rows leaked another instance');
    }
    h.assert(clinical()===before,'Report execution changed clinical data');
  }
  async function run(instance,sex) {
    const field=instance.page.locator('#pick');
    if(instance.template.parameterType==='text')await field.fill(sex);else await field.selectOption(sex);
    await Promise.all([instance.page.waitForNavigation({waitUntil:'load'}),instance.page.locator('input[type="submit"][value="Run Query"]').click()]);
    await table(instance,sex);
  }
  async function exports(instance) {
    await table(instance,instance.sex);
    const {page,template,sex}=instance;
    if(sex==='X')return; // The release offers no export for an empty result.
    for(const kind of ['CSV','XLS']) {
      const file=await x.saveDownload(page,scratch,()=>page.locator(`input[name="get${kind}"]`).click());
      try {
        h.assert(file.name===`oscarReport.${kind==='XLS'?'xlsx':'csv'}`,'Report export has the wrong filename');
        if(kind==='CSV') {
          const actual=x.parseCsv(file.bytes.toString('utf8'));
          const expected=[template.header,template.csvRow?template.csvRow(sex):template.row(sex)];
          h.assert(JSON.stringify(actual)===JSON.stringify(expected),'CSV leaked another report, parameters or stale rows');
        } else {
          const cells=x.xlsxCells(file.bytes);
          h.assert(cells.length===2&&cells[0].length===template.header.length&&cells[1].length===template.header.length,'XLSX has another report\'s dimensions');
          h.assert(JSON.stringify(cells[0].map(cell=>cell.value))===JSON.stringify(template.header),'XLSX headers leaked another report');
          for(let i=0;i<template.header.length;i++) {
            const cell=cells[1][i],expected=template.row(sex)[i];
            h.assert(cell.type===template.types[i]&&(cell.type==='number'?cell.value===Number(expected):cell.value===expected),'XLSX cell value or type leaked another report');
          }
        }
      } finally{fs.rmSync(file.file,{force:true});}
    }
    h.assert(clinical()===before,'Report export changed clinical data');
  }
  async function leave(instance,action) {
    const baseline=beacons.length;
    const observed=s.context.waitForEvent('request',{predicate:isBeacon,timeout:15000});
    observed.catch(()=>{});
    await action();const beacon=await observed;
    h.assert(beacon.method()==='POST'&&beacons.length===baseline+1,'Normal report exit did not send exactly one clear-session beacon');
    // As in report-by-template, Chromium can abort this unloading ping before
    // exposing a response. Consume only its specific ERR_ABORTED transport event.
    // Sending is observed; this is not a claim that its HTTP response was seen.
    await new Promise(resolve=>setTimeout(resolve,300));
    for(let i=s.recorder.requestFailures.length-1;i>=0;i--) {
      const entry=s.recorder.requestFailures[i];
      if(entry.resourceType==='ping'&&entry.requestRef?.deref()===beacon&&/ERR_ABORTED/.test(entry.errorText))s.recorder.requestFailures.splice(i,1);
    }
  }
  await s.step('a lone empty text parameter survives native upload, edit, reopen, run and export',async()=>{
    const otherBefore=JSON.stringify(sql.rows('SELECT * FROM reportTemplates WHERE templateid=?',[templates[1].id]));
    const proof=await open(templates[0]);
    h.assert(await proof.page.locator('input#pick').inputValue()==='', 'The empty text parameter acquired a value');
    await Promise.all([proof.page.waitForNavigation({waitUntil:'load'}),proof.page.getByRole('link',{name:'Edit Template',exact:true}).click()]);
    const textarea=proof.page.locator('textarea#xmltext');
    const source=await textarea.inputValue();
    const editedQuery=templates[0].query.replace(' ORDER BY'," AND 'A&B'='A&B' ORDER BY");
    const changed=source.replace(`${marker} tab isolation`,`${marker} tab isolation edited`)
      .replace(templates[0].query,editedQuery);
    h.assert(changed!==source&&changed.includes(editedQuery),'The owned description or SQL was not present in the editor');
    await textarea.fill(changed);
    await Promise.all([proof.page.waitForNavigation({waitUntil:'load'}),proof.page.locator('input[type="submit"][value="Done"]').click()]);
    await proof.page.locator('input#pick').waitFor();
    h.assert(await proof.page.locator('input#pick').inputValue()==='', 'The edited text parameter lost its empty state');
    templates[0].query=editedQuery;
    const persistedXml=sql.value('SELECT templatexml FROM reportTemplates WHERE templateid=?',[templates[0].id]);
    h.assert(persistedXml.includes('meta:hint="optional"')
      && persistedXml.includes('xmlns:meta="urn:carlos:regression-metadata"'),
    'The empty parameter lost its inherited namespace metadata');
    h.assert(sql.value('SELECT templatesql FROM reportTemplates WHERE templateid=?',[templates[0].id])===editedQuery,
      'Editing the report changed its legacy raw ampersand SQL');
    h.assert(JSON.stringify(sql.rows('SELECT * FROM reportTemplates WHERE templateid=?',[templates[1].id]))===otherBefore,
      'Editing one template changed the other template');
    await run(proof,'F');await exports(proof);
    await leave(proof,()=>Promise.all([proof.page.waitForNavigation({waitUntil:'load'}),proof.page.locator('input[type="button"][value="Back"]').click()]));
    await proof.page.close();
  });
  let a,b,c,d;
  await s.step('different templates and parameters in two shared-session tabs retain exact rows and CSV/XLS exports',async()=>{
    a=await open(templates[0]);b=await open(templates[1]);await run(a,'F');await run(b,'M');await exports(a);await exports(b);await exports(a);
  });
  await s.step('two additional instances of one template retain different parameters including an empty result',async()=>{
    c=await open(templates[0]);d=await open(templates[0]);await run(c,'M');await run(d,'X');
    await exports(c);await exports(d);await exports(a);await exports(b);
  });
  await s.step('Back and rerun in one tab send the native clear beacon and preserve other results and exports',async()=>{
    await leave(a,()=>Promise.all([a.page.waitForNavigation({waitUntil:'load'}),a.page.locator('input[type="button"][value="Back"]').click()]));
    await exports(c);await exports(b);await run(a,'F');await exports(a);
  });
  await s.step('leaving and closing one report preserves the other populated and empty instances',async()=>{
    await leave(b,()=>Promise.all([b.page.waitForNavigation({waitUntil:'load'}),b.page.locator('input[type="button"][value="Back"]').click()]));
    await b.page.close();h.assert(b.page.isClosed(),'The report tab did not close');
    await exports(a);await exports(c);await exports(d);
    h.assert(clinical()===before,'Cross-tab lifecycle changed clinical data');
  });
  // reportByTemplateLayout.css (829c8033b91e), linked by resultReport.jsp:95 and viewTemplate.jsp:60, styles only
  // body.rbt-page (resultReport.jsp:129, viewTemplate.jsp:73). One template with a long unbroken token serves both
  // pages: the token is a 400-character line fragment in the template XML and a 400-character result cell.
  const wideValue='W'.repeat(400);
  const wide={title:titles[2],parameterType:'list',header:['patient_id','wide_value'],types:['number','text'],
    query:`SELECT demographic_no AS patient_id,'${wideValue}' AS wide_value FROM demographic WHERE last_name='${marker}' AND sex='{pick}' ORDER BY demographic_no`,
    row:sex=>[sex==='F'?patient:second,wideValue]};
  // Computed layout of the result page's report region (resultReport.jsp:149) and of the page itself.
  const resultLayout=page=>page.evaluate(()=>{
    const region=document.querySelector('body.rbt-page > .reportBorderDiv');
    const actions=document.querySelector('body.rbt-page .form-actions > .d-flex');
    if(!region||!actions)return null;
    const style=getComputedStyle(region);
    return {overflowX:style.overflowX,marginLeft:style.marginLeft,marginRight:style.marginRight,
      scrollWidth:region.scrollWidth,clientWidth:region.clientWidth,actionsWrap:getComputedStyle(actions).flexWrap,
      pageScrollWidth:document.documentElement.scrollWidth,pageClientWidth:document.documentElement.clientWidth,
      noprint:[...document.querySelectorAll('body.rbt-page .noprint')].map(node=>getComputedStyle(node).display)};
  });
  // Computed layout of View Template XML's template text (viewTemplate.jsp:86-87) and its bars.
  const templateLayout=page=>page.evaluate(()=>{
    const pre=document.querySelector('body.rbt-page .xmlBorderDiv pre');
    if(!pre)return null;
    const style=getComputedStyle(pre);
    const rem=parseFloat(getComputedStyle(document.documentElement).fontSize);
    return {text:pre.textContent,whiteSpace:style.whiteSpace,overflowWrap:style.overflowWrap,
      scrollWidth:pre.scrollWidth,clientWidth:pre.clientWidth,
      pageScrollWidth:document.documentElement.scrollWidth,pageClientWidth:document.documentElement.clientWidth,
      gutter:[parseFloat(getComputedStyle(document.body).paddingLeft),parseFloat(getComputedStyle(document.body).paddingRight),rem],
      noprint:[...document.querySelectorAll('body.rbt-page .noprint')].map(node=>getComputedStyle(node).display)};
  });
  await s.step('the layout stylesheet scrolls a wide result inside its region, wraps template XML and hides the bar in print',async()=>{
    await upload(wide);
    await click(library.getByRole('link',{name:'Template Library',exact:true}));
    const report=await open(wide);
    const {page}=report;
    await run(report,'F');
    const screen=await resultLayout(page);
    h.assert(screen,'The result page has no .reportBorderDiv or action row in body.rbt-page');
    h.assert(screen.overflowX==='auto'&&screen.marginLeft==='0px'&&screen.marginRight==='0px',
      `The report region does not take the layout stylesheet's screen rules: ${JSON.stringify(screen)}`);
    h.assert(screen.scrollWidth>screen.clientWidth,'The 400-character result cell does not overflow the report region');
    h.assert(screen.pageScrollWidth<=screen.pageClientWidth,
      `The wide result widens the page (${screen.pageScrollWidth}px > ${screen.pageClientWidth}px) instead of scrolling in its region`);
    h.assert(screen.actionsWrap==='wrap','The result actions row does not wrap');
    await page.emulateMedia({media:'print'});
    let printed;
    try{printed=await resultLayout(page);}finally{await page.emulateMedia({media:null});}
    // The scroll rule is screen-only, so a printed report is not clipped to the region.
    h.assert(printed.overflowX==='visible'&&printed.noprint.length>0&&printed.noprint.every(display=>display==='none'),
      `The printed result clips the report or keeps a .noprint bar: ${JSON.stringify(printed)}`);
    await leave(report,()=>Promise.all([page.waitForNavigation({waitUntil:'load'}),page.locator('input[type="button"][value="Back"]').click()]));
    // reportConfiguration.jsp:243 links View Template XML.
    await Promise.all([page.waitForNavigation({waitUntil:'load'}),page.getByRole('link',{name:'View Template XML',exact:true}).click()]);
    const view=await templateLayout(page);
    h.assert(view&&view.text.includes(wideValue),'View Template XML does not show the template with its long token');
    h.assert(view.whiteSpace==='pre-wrap'&&view.overflowWrap==='anywhere',`The template XML does not wrap: ${view.whiteSpace}/${view.overflowWrap}`);
    h.assert(view.scrollWidth<=view.clientWidth+1&&view.pageScrollWidth<=view.pageClientWidth,
      `The long XML line widens its box (${view.scrollWidth}px > ${view.clientWidth}px) or the page instead of wrapping`);
    h.assert(view.gutter[0]===view.gutter[2]&&view.gutter[1]===view.gutter[2],`The page has no 1rem screen gutter: ${JSON.stringify(view.gutter)}`);
    h.assert(view.noprint.length>=2&&view.noprint.every(display=>display!=='none'),'The navigation bar or template actions are hidden on screen');
    await page.emulateMedia({media:'print'});
    let printedView;
    try{printedView=await templateLayout(page);}finally{await page.emulateMedia({media:null});}
    // viewTemplate.jsp has no print rule of its own: the stylesheet hides rbtTopNav.jspf's bar and the actions.
    h.assert(printedView.noprint.length>=2&&printedView.noprint.every(display=>display==='none'),
      `A printed template keeps the navigation bar or its actions: ${JSON.stringify(printedView.noprint)}`);
    await page.close();
    h.assert(clinical()===before,'The layout checks changed clinical data');
  });
}
if(require.main===module)runWorkflow('report-by-template-tabs',workflow,{openMaster:false});
module.exports={workflow};
