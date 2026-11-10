#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
'use strict';
const fs=require('node:fs'),path=require('node:path');
const h=require('./lib/playwright-harness'),ui=require('./lib/playwright-ui');
const {runWorkflow}=require('./lib/workflow-session');
const {authenticatedProbe}=require('./lib/authenticated-probe');
const {retireOwnedFile}=require('./lib/owned-file-retirement');
const base=require('./billing-on-ra-import-playwright-checks');
const {openAdminFrame}=require('./billing-on-ohip-simulation-report-playwright-checks');
const {settleOperations}=require('./graceful-signal-cancellation');
const q=h.sqlString,NAME='billing-on-ra-replay';

/** Fixed-width MOH adjustment: no new invoiced fee and an explicit -5.00 recovery. */
function adjustmentMessage({owned,marker,claimId,code}) {
  h.assert(/^FAKE-PW[a-f0-9]{16}$/.test(marker)&&typeof claimId==='string'&&/^[1-9]\d{0,7}$/.test(claimId),'Invalid owned adjustment identity');
  h.assert(/^\d{4}$/.test(owned.groupNo)&&/^\d{6}$/.test(owned.ohipNo)&&/^\d{10}$/.test(owned.hin)&&/^[A-Z0-9]{5}$/.test(code),'Invalid adjustment provider/HIN/code');
  const field=(text,width)=>{h.assert(text.length<=width,'Adjustment field overflow');return text.padEnd(width,' ');};
  const record=text=>field(text,79),claimNo='PW000000001';
  return [record('HR1V030'+owned.groupNo+owned.ohipNo+'00  '+'20040616'+field(marker,30)+'000000500'+'-'),
    record('HR4'+claimNo+'1'+owned.ohipNo+'00'+claimId.padStart(8,'0')+field('FAKEPW',14)+field('WORK',5)+'ON'+field(owned.hin,12)+'ZZHCP'),
    record('HR5'+claimNo+'1'+'20040512'+'01'+code+' '+'000000'+'000500'+'-'+'  '),
    record('HR6'+'000000000 '.repeat(4)),record('HR8'+field(marker+' ADJUSTMENT SYNTHETIC',70))].join('\r\n')+'\r\n';
}
const cents=value=>{h.assert(/^\d+\.\d{2}$/.test(value),'Noncanonical RA fixture amount');return BigInt(value.replace('.',''));};
const money=value=>(value<0n?'-':'')+String((value<0n?-value:value)/100n)+'.'+String((value<0n?-value:value)%100n).padStart(2,'0');

/** Original mixed RA, exact re-import, protected settle replay and later signed adjustment. */
async function workflow(s) {
  const {sql,marker,patient}=s;
  const snapshotClaim=id=>JSON.stringify({header:sql.rows(`SELECT * FROM billing_on_cheader1 WHERE id=${id}`),
    items:sql.rows(`SELECT * FROM billing_on_item WHERE ch1_id=${id} ORDER BY id`),ext:sql.rows(`SELECT * FROM billing_on_ext WHERE billing_no=${id} ORDER BY id`),
    payments:sql.rows(`SELECT * FROM billing_on_payment WHERE billing_no=${id} ORDER BY payment_id`),
    allocations:sql.rows(`SELECT * FROM billing_on_item_payment WHERE ch1_id=${id} ORDER BY id`),
    transactions:sql.rows(`SELECT * FROM billing_on_transaction WHERE ch1_id=${id} ORDER BY id`)});
  await base.workflow(s,{async beforeImport(source){
    const {owned}=source;
    const control=owned.addClaim({tag:'RA-CONTROL',date:'2004-05-12',status:'B',items:[{code:'A001A',fee:'17.23'}]});
    const controlBefore=snapshotClaim(control.id),premiumsBefore=JSON.stringify(sql.rows('SELECT * FROM billing_on_premium ORDER BY premium_id'));
    h.assert(source.statuses()==='B|B','Initial mixed RA claims are not both submitted');
    const originalFees=source.raClaims.map(claim=>({id:claim.id,fee:claim.fee,paid:claim.paid}));
    h.assert(cents(originalFees[0].fee)>500n,'The paid claim fee must exceed the recovery amount');
    const columns=sql.rows("SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='billing_on_cheader1' ORDER BY ORDINAL_POSITION").flat();
    const statusIndex=columns.indexOf('status'),timestampIndex=columns.indexOf('timestamp1');
    h.assert(statusIndex>=0&&timestampIndex>=0,'Claim snapshot lacks status/audit timestamp columns');
    const started=sql.value('SELECT UNIX_TIMESTAMP()');h.assert(/^\d+$/.test(started),'Initial RA time boundary is unreadable');
    return {control,controlBefore,premiumsBefore,statusIndex,timestampIndex,started,initial:source.raClaims.map(claim=>snapshotClaim(claim.id))};
  },async afterSettlement(source){
    const {owned,paidClaim,rejectedClaim,raClaims,remittance,raName,documentDir,edtInbox,admin,raNo,settlementRequest,extensionState:state}=source;
    const claimIds=[paidClaim.id,rejectedClaim.id,state.control.id].join(','),adjustmentName=raName.replace(/\.001$/,'.002');
    const adjustment=adjustmentMessage({owned,marker,claimId:paidClaim.id,code:raClaims[0].code}),bytes=Buffer.from(adjustment,'latin1');
    let adjustmentId,adjustmentClaimed=false;
    for(const directory of [documentDir,edtInbox])h.assert(!fs.existsSync(path.join(directory,adjustmentName)),'Adjustment file namespace exists');
    h.assert(sql.value(`SELECT COUNT(*) FROM raheader WHERE filename=${q(adjustmentName)}`)==='0','Adjustment header namespace exists');
    s.cleanup(()=>{
      if(!adjustmentClaimed)return;
      const named=sql.rows(`SELECT raheader_no FROM raheader WHERE filename=${q(adjustmentName)}`).flat();
      const ids=[...new Set([...named,...(adjustmentId?[adjustmentId]:[])])];
      h.assert(named.length<=1&&ids.every(id=>/^[1-9]\d*$/.test(id)),'Ambiguous adjustment ownership');
      for(const id of ids) {
        if(sql.value(`SELECT COUNT(*) FROM raheader WHERE raheader_no=${id}`)==='0')continue;
        h.assert(sql.value(`SELECT COUNT(*) FROM raheader WHERE raheader_no=${id} AND filename=${q(adjustmentName)} AND TRIM(payable)=${q(marker)} AND paymentdate='20040616'`)==='1','Adjustment header ownership changed');
        h.assert(sql.value(`SELECT COUNT(*) FROM radetail WHERE raheader_no=${id} AND COALESCE((billing_no=${paidClaim.id} AND providerohip_no=${q(owned.ohipNo)}),0)=0`)==='0','Adjustment contains an unowned claim/provider');
        sql.execute(`DELETE FROM billing_on_premium WHERE raheader_no=${id}; DELETE FROM radetail WHERE raheader_no=${id}; DELETE FROM raheader WHERE raheader_no=${id} AND filename=${q(adjustmentName)}`);
        h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM raheader WHERE raheader_no=${id})+(SELECT COUNT(*) FROM radetail WHERE raheader_no=${id})+(SELECT COUNT(*) FROM billing_on_premium WHERE raheader_no=${id})`)==='0','Owned adjustment rows remain');
      }
      for(const directory of [documentDir,edtInbox]) {
        const file=path.join(directory,adjustmentName);retireOwnedFile(file,data=>data.equals(bytes));
        h.assert(!fs.existsSync(file),'Owned adjustment file remains');
      }
    });
    const control=()=>{
      h.assert(snapshotClaim(state.control.id)===state.controlBefore,'Unrelated control claim changed');
      h.assert(sql.value(`SELECT status FROM billing_on_cheader1 WHERE id=${rejectedClaim.id}`)==='B','Rejected claim left its intended submitted state');
      h.assert(JSON.stringify(sql.rows('SELECT * FROM billing_on_premium ORDER BY premium_id'))===state.premiumsBefore,'RA replay/adjustment created or changed premium state');
    };
    const finances=()=>JSON.stringify([snapshotClaim(paidClaim.id),snapshotClaim(rejectedClaim.id),snapshotClaim(state.control.id)]);
    const details=id=>JSON.stringify(sql.rows(`SELECT * FROM radetail WHERE raheader_no=${id} ORDER BY radetail_no`));
    const header=id=>JSON.stringify(sql.rows(`SELECT raheader_no,filename,paymentdate,TRIM(payable),totalamount,records,claims,status FROM raheader WHERE raheader_no=${id}`));
    const originalDetails=details(raNo),originalHeader=header(raNo),settledFinancial=finances();
    const originalFiles=()=>[documentDir,edtInbox].map(directory=>{const file=path.join(directory,raName);return fs.existsSync(file)?fs.readFileSync(file).toString('hex'):null;});
    const fileSnapshot=JSON.stringify(originalFiles());
    control();
    h.assert(source.statuses()==='S|B','Initial native settlement did not settle only the paid claim');
    for(const [index,claim] of [paidClaim,rejectedClaim].entries()) {
      const expected=JSON.parse(state.initial[index]);h.assert(expected.header.length===1,'Initial claim snapshot is ambiguous');
      expected.header[0][state.statusIndex]=index===0?'S':'B';
      const actual=JSON.parse(snapshotClaim(claim.id));
      if(index===0) {
        h.assert(sql.value(`SELECT COUNT(*) FROM billing_on_cheader1 WHERE id=${claim.id} AND UNIX_TIMESTAMP(timestamp1) BETWEEN ${state.started} AND UNIX_TIMESTAMP()`)==='1','Initial settlement audit timestamp lies outside its observed time boundary');
        expected.header[0][state.timestampIndex]=actual.header[0][state.timestampIndex];
      }
      h.assert(JSON.stringify(actual)===JSON.stringify(expected),'Initial import/settlement changed claim fields beyond its intended header status/audit timestamp');
    }
    const frameFor=async()=>{
      // The existing iframe may already have this URL. Await the new document
      // event, rather than accepting that stale URL/load state after a click.
      const navigation=admin.waitForEvent('framenavigated',{timeout:30000,
        predicate:frame=>new URL(frame.url()).pathname.endsWith('/billing/CA/ON/ViewGenRA')});
      const [,frame]=await settleOperations([openAdminFrame(admin,'/billing/CA/ON/ViewGenRA','table'),navigation]);
      await frame.waitForLoadState('networkidle');return frame;
    };
    async function summary(frame,id,expectedTotal,rows) {
      const row=base.raRow(frame,marker).filter({has:frame.locator(`a[href*="rano=${id}"]`)});
      await row.waitFor({state:'visible',timeout:30000});
      h.assert(await row.count()===1,'RA list identity is missing or ambiguous');
      const view=await s.popup(admin,row.locator('a',{hasText:'Summary'}),'RA-replay-summary');
      await view.waitForLoadState('networkidle');
      await view.locator('select[name="proNo"]').selectOption(owned.ohipNo);
      await ui.clickAndAwaitReload(view,view.locator('input[type="submit"][value="Generate"]'));
      await view.locator('#ra_table').waitFor();
      await view.waitForLoadState('networkidle');
      for(const expected of rows) {
        const cells=(await view.locator('#ra_table tbody tr').filter({hasText:expected.code}).first().locator('td').allInnerTexts()).map(cell=>cell.trim());
        h.assert(cells[0]===expected.id&&cells[6]===expected.code&&cells[7]===expected.fee&&cells[8]===expected.paid,'RA summary changed claim/code/invoiced/paid amounts');
      }
      h.assert((await view.locator('#amountPay').innerText()).trim()===expectedTotal,'RA summary paid total is wrong');
      h.assert(sql.value(`SELECT content FROM raheader WHERE raheader_no=${id}`).includes('<xml_total>'+expectedTotal+'</xml_total>'),'RA summary did not persist its exact derived total');
      await view.close();control();
    }
    await s.step('same RA bytes re-import without duplicate remittances, details, settlement or financial effect',async()=>{
      const imported=await base.uploadMohFile(s,admin,raName,remittance.text,'/servlet/io.github.carlos_emr.DocumentUploadServlet');
      h.assert(imported.status===200,'RA re-import was not truthfully accepted');
      h.assert(sql.value(`SELECT COUNT(*) FROM raheader WHERE filename=${q(raName)}`)==='1'&&header(raNo)===originalHeader&&details(raNo)===originalDetails,'RA re-import duplicated or altered active remittance/detail state');
      h.assert(finances()===settledFinancial&&JSON.stringify(originalFiles())===fileSnapshot,'RA re-import changed money, claim state or original upload bytes');
      await summary(imported.frame,raNo,remittance.cheque,raClaims);control();
    });
    await s.step('settled UI offers no Settle; replay of its actual protected POST has no second financial effect',async()=>{
      const frame=await frameFor(),row=base.raRow(frame,marker).filter({has:frame.locator(`a[href*="rano=${raNo}"]`)});
      await row.waitFor({state:'visible',timeout:30000});
      h.assert(await row.locator('a',{hasText:'Settle'}).count()===0,'Settled RA still offers native Settle');
      h.assert(settlementRequest.method==='POST'&&new URL(settlementRequest.url).pathname.endsWith('/billing/CA/ON/ViewOnGenRAsettle'),'No actual native settlement request was captured');
      const form=Object.fromEntries(new URLSearchParams(settlementRequest.body));h.assert(form.rano===String(raNo),'Captured settlement names another RA');delete form['CSRF-TOKEN'];
      const response=await authenticatedProbe(s,settlementRequest.url,{method:'POST',allowPost:true,mutatingPost:true,form});
      try {h.assert(response.status()===200&&(await response.text()).includes('location.replace(raList)'),'Protected settlement replay did not reach its native completion');}finally{await response.dispose();}
      h.assert(finances()===settledFinancial&&header(raNo)===originalHeader&&details(raNo)===originalDetails,'Settlement replay duplicated financial effect or changed claim/remittance state');control();
    });
    await s.step('subsequent signed adjustment imports exactly one -5.00 recovery for the original paid claim',async()=>{
      adjustmentClaimed=true;
      const imported=await base.uploadMohFile(s,admin,adjustmentName,adjustment,'/servlet/io.github.carlos_emr.DocumentUploadServlet');
      h.assert(imported.status===200,'Adjustment upload failed');
      const ids=sql.rows(`SELECT raheader_no FROM raheader WHERE filename=${q(adjustmentName)}`).flat();h.assert(ids.length===1&&/^[1-9]\d*$/.test(ids[0]),'Adjustment header identity is ambiguous');adjustmentId=ids[0];
      h.assert(sql.value(`SELECT CONCAT_WS('|',paymentdate,totalamount,records,claims,status) FROM raheader WHERE raheader_no=${adjustmentId}`)==='20040616|-5.00|1|1|N','Signed adjustment header changed its amount/date/count/status');
      const actual=sql.rows(`SELECT billing_no,providerohip_no,service_code,amountclaim,amountpay,service_date,error_code FROM radetail WHERE raheader_no=${adjustmentId}`);
      h.assert(JSON.stringify(actual)===JSON.stringify([[paidClaim.id,owned.ohipNo,raClaims[0].code,'0.00','-5.00','20040512','']]),'Adjustment detail lost its sign, original claim or service identity');
      h.assert(finances()===settledFinancial&&details(raNo)===originalDetails&&header(raNo)===originalHeader,'Adjustment ingestion modified the settled original or claim/payment state');
      const net=money(cents(raClaims[0].paid)-500n);
      h.assert(sql.value(`SELECT CAST(SUM(CAST(amountpay AS DECIMAL(12,2))) AS DECIMAL(12,2)) FROM radetail WHERE raheader_no IN (${raNo},${adjustmentId}) AND billing_no=${paidClaim.id}`)===net,'Original plus adjustment net payment is double-counted');
      h.assert(sql.value(`SELECT CAST(${q(raClaims[0].fee)}-SUM(CAST(amountpay AS DECIMAL(12,2))) AS DECIMAL(12,2)) FROM radetail WHERE raheader_no IN (${raNo},${adjustmentId}) AND billing_no=${paidClaim.id}`)==='5.00','Adjusted paid-claim balance is not exactly 5.00');
      const row=base.raRow(imported.frame,marker).filter({has:imported.frame.locator(`a[href*="rano=${adjustmentId}"]`)});
      const report=await s.popup(admin,row.locator('a',{hasText:'Report'}),'RA-adjustment-report');
      await report.waitForLoadState('networkidle');
      h.assert((await report.locator('body').innerText()).replace(/\s+/g,' ').includes('Cheque amount: -5.00'),'Actual adjustment report lost the signed cheque total');await report.close();
      await summary(imported.frame,adjustmentId,'-5.00',[{id:paidClaim.id,code:raClaims[0].code,fee:'0.00',paid:'-5.00'}]);control();
    });
    await s.step('native adjustment settlement and its duplicate import preserve net balance and original/rejected/control claims',async()=>{
      const frame=await frameFor(),row=base.raRow(frame,marker).filter({has:frame.locator(`a[href*="rano=${adjustmentId}"]`)});
      const dialogs=await h.withExpectedDialogs(admin,async()=>{
        const [response]=await settleOperations([admin.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/billing/CA/ON/ViewOnGenRAsettle')),
          admin.waitForEvent('framenavigated',{predicate:f=>f===frame&&new URL(f.url()).pathname.endsWith('/billing/CA/ON/ViewGenRA')}).then(f=>f.waitForLoadState('networkidle')),
          row.locator('a',{hasText:'Settle'}).click()]);h.assert(response.status()===200,'Native adjustment settlement failed');
      });
      h.assert(dialogs.length===1&&dialogs[0].type==='confirm'&&dialogs[0].text==='You are about to reconcile the file, are you sure?','Adjustment settlement confirmation changed');
      h.assert(sql.value(`SELECT status FROM raheader WHERE raheader_no=${adjustmentId}`)==='S','Adjustment RA did not settle');
      const adjustedHeader=header(adjustmentId),adjustedDetails=details(adjustmentId);
      const repeat=await base.uploadMohFile(s,admin,adjustmentName,adjustment,'/servlet/io.github.carlos_emr.DocumentUploadServlet');
      h.assert(repeat.status===200&&sql.value(`SELECT COUNT(*) FROM raheader WHERE filename=${q(adjustmentName)}`)==='1'&&header(adjustmentId)===adjustedHeader&&details(adjustmentId)===adjustedDetails,'Adjustment re-import repeated or changed its remittance effect');
      h.assert(finances()===settledFinancial&&source.statuses()==='S|B'&&header(raNo)===originalHeader&&details(raNo)===originalDetails,'Adjustment settlement/replay changed original/rejected/control financial state');
      h.assert(sql.value(`SELECT COUNT(*) FROM billing_on_payment WHERE billing_no IN (${claimIds})`)==='0','OHIP RA processing unexpectedly wrote private payment rows');
      h.assert(fs.readFileSync(path.join(documentDir,adjustmentName)).equals(bytes),'Adjustment upload bytes changed');control();
    });
  }});
}
if(require.main===module)runWorkflow(NAME,workflow,{openMaster:false,preflightConfig:()=>{
  if(process.env.EXCLUSIVE!=='1'||process.env.CARLOS_DISPOSABLE_HOST!=='true')throw new h.SkipCheck('RA replay requires CARLOS_DISPOSABLE_HOST=true and EXCLUSIVE=1 on the installed host');
}});
module.exports={workflow,adjustmentMessage};
