#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
'use strict';
const fs=require('node:fs');
const h=require('./lib/playwright-harness'),x=require('./lib/export-content-helpers');
const {runWorkflow,expectValue}=require('./lib/workflow-session');
const {createOwnedBillingPhysician}=require('./lib/owned-billing-physician');
const {requirePoppler}=require('./lib/stored-pdf-documents');
const {seedOwnedBill,openHistory,historyRow,openCorrection,captureNativeInvoiceContext,billDate,awaitResponse}=require('./billing-on-invoice-third-party-playwright-checks');
const graph=require('./lib/pdf-graph');
const q=h.sqlString,NAME='billing-on-multi-item-payments',ROUTE='/billing/CA/ON/billingON3rdPayments';
const money=cents=>Math.floor(cents/100)+'.'+String(cents%100).padStart(2,'0');
const FEES=[1123,2457,3809],STAGES=[[321,543,0],[802,400,0],[0,1514,3809]];

/** Independently authored cent allocations, actual native saves and per-line reconciliation. */
async function workflow(s) {
  const {sql,patient,marker,provider}=s,physician=createOwnedBillingPhysician(s),billingContext=await captureNativeInvoiceContext(s,physician);
  const codes=sql.rows("SELECT DISTINCT service_code FROM billingservice WHERE service_code LIKE '\\_%' ORDER BY service_code LIMIT 3").flat();
  if(codes.length!==3)throw new h.SkipCheck('Three distinct configured private billing codes are required');
  h.assert(codes.every(code=>/^_[A-Z0-9_]{1,9}$/.test(code)),'Unsupported private billing code identity');
  let bill,control;
  // Wrap the existing fixture cleanup with identity/line guards, including a
  // lost setup acknowledgment recovered by its claimed marker namespace.
  function fixture(label,fees) {
    const tag=marker+'-'+label;let result;
    const dependent=Object.assign(Object.create(physician.dependentSession),{marker:tag,cleanup(fn){
      physician.dependentSession.cleanup(()=>{
        const ids=new Set(sql.rows(`SELECT id FROM billing_on_cheader1 WHERE demographic_no=${patient} AND comment1 LIKE ${q(tag+'%')}`).flat());
        if(result)ids.add(result.headerId);
        for(const id of ids) {
          h.assert(/^[1-9]\d*$/.test(id),'Invalid owned invoice identity');
          const headers=sql.rows(`SELECT demographic_no,provider_no,comment1 FROM billing_on_cheader1 WHERE id=${id}`);
          if(headers.length)h.assert(headers.length===1&&headers[0][0]===patient&&headers[0][1]===physician.providerNo&&headers[0][2].startsWith(tag),'Invoice ownership changed; retaining its rows');
          const items=sql.rows(`SELECT id,service_code,fee FROM billing_on_item WHERE ch1_id=${id}`);
          h.assert(items.length<=fees.length&&items.every(([,code,fee])=>codes.some((c,index)=>index<fees.length&&c===code&&fee===money(fees[index])))
            &&new Set(items.map(row=>row[1])).size===items.length,'Invoice lines changed; retaining its rows');
          const itemIds=items.map(row=>row[0]);h.assert(itemIds.every(value=>/^[1-9]\d*$/.test(value)),'Invalid owned invoice line');
          h.assert(sql.value(`SELECT COUNT(*) FROM billing_on_item_payment WHERE ch1_id=${id} AND billing_on_item_id NOT IN (${itemIds.join(',')||'0'})`)==='0','Unexpected item-payment attribution; retaining invoice');
        }
        fn();
      });
    }});
    result=seedOwnedBill(dependent,{payProgram:'PAT',status:'P',code:codes[0],fee:money(fees[0]),date:billDate(),billingContext});
    return result;
  }
  bill=fixture('MAIN',FEES);control=fixture('CONTROL',[1999]);
  const items=[bill.itemId];
  for(let i=1;i<3;i++) {
    const id=h.insertId(sql,`INSERT INTO billing_on_item(ch1_id,transc_id,rec_id,service_code,fee,ser_num,service_date,dx,dx1,dx2,status)
      VALUES(${bill.headerId},'HE','T',${q(codes[i])},${q(money(FEES[i]))},'1',${q(billDate())},'250','','','P')`,'Owned invoice line');items.push(id);
  }
  sql.execute(`UPDATE billing_on_cheader1 SET total=73.89 WHERE id=${bill.headerId} AND demographic_no=${patient} AND comment1=${q(marker+'-MAIN')}`);
  const snapshot=id=>JSON.stringify({header:sql.rows(`SELECT * FROM billing_on_cheader1 WHERE id=${id}`),
    items:sql.rows(`SELECT * FROM billing_on_item WHERE ch1_id=${id} ORDER BY id`),ext:sql.rows(`SELECT * FROM billing_on_ext WHERE billing_no=${id} ORDER BY id`),
    payments:sql.rows(`SELECT * FROM billing_on_payment WHERE billing_no=${id} ORDER BY payment_id`),
    allocation:sql.rows(`SELECT * FROM billing_on_item_payment WHERE ch1_id=${id} ORDER BY id`),
    transactions:sql.rows(`SELECT * FROM billing_on_transaction WHERE ch1_id=${id} ORDER BY id`)});
  const controlBefore=snapshot(control.headerId),lineBefore=JSON.stringify(sql.rows(`SELECT * FROM billing_on_item WHERE ch1_id=${bill.headerId} ORDER BY id`));
  const scratch=x.scratchDir();s.cleanup(()=>fs.rmSync(scratch,{recursive:true,force:true}));
  const history=await openHistory(s),paid=[0,0,0],payments=[];
  const unchanged=()=>{
    h.assert(snapshot(control.headerId)===controlBefore,'Control invoice changed');
    h.assert(JSON.stringify(sql.rows(`SELECT * FROM billing_on_item WHERE ch1_id=${bill.headerId} ORDER BY id`))===lineBefore,'Payment changed invoice line identities/fees');
  };
  async function openPayments() {
    const correction=await openCorrection(s,history,bill.headerId);
    const page=await s.popup(correction,correction.locator('#thirdPartyPymnt a',{hasText:'Payments List'}),'multi-item-payments');
    await page.locator('#editPayment').waitFor();
    h.assert(await page.locator('#editPayment input[name="billingNo"]').inputValue()===bill.headerId,'Payment form names another invoice');
    h.assert(await page.locator('#editPayment input[name="size"]').inputValue()==='3','Payment form does not contain exactly three lines');
    return {page,correction};
  }
  async function verifyRows(page) {
    for(let i=0;i<3;i++) {
      const index=await page.locator(`input[value="${items[i]}"][name^="itemId"]`).getAttribute('name');
      h.assert(/^itemId\d+$/.test(index),'Native payment row has no stable item identity');
      const row=page.locator(`input[name="${index}"]`).locator('xpath=ancestor::tr[1]');
      const text=(await row.innerText()).replace(/\s+/g,' ');
      h.assert(text.includes(codes[i])&&text.includes('$'+money(FEES[i]))&&new RegExp('Paid:\\s*[^\\d]*'+money(paid[i]).replace('.','\\.')).test(text)
        &&new RegExp('Balance:\\s*[^\\d]*'+money(FEES[i]-paid[i]).replace('.','\\.')).test(text),'Native row has wrong code, fee, paid or balance: item '+i);
    }
    h.assert(await page.locator('a',{hasText:'view'}).count()===payments.length,'Payment history has extra/missing entries');
  }
  async function verifyInvoice(stage) {
    const invoice=await s.popup(history,historyRow(history,bill.headerId).locator('a',{hasText:'Print'}).first(),'multi-item-invoice');
    await invoice.locator('#invoiceNo').waitFor({state:'attached'});
    const text=(await invoice.locator('body').innerText()).replace(/\s+/g,' '),totalPaid=paid.reduce((sum,value)=>sum+value,0);
    h.assert(text.includes('Invoice - '+bill.headerId)&&codes.every(code=>text.includes(code)),'Displayed invoice loses invoice/line identity');
    for(const [label,value] of [['Total:','73.89'],['Payments:',money(totalPaid)],['Balance:',money(7389-totalPaid)]]) {
      const amount=invoice.locator('tr',{has:invoice.locator('td',{hasText:label})}).last().locator('td').last();
      h.assert((await amount.innerText()).trim()===value,'Displayed invoice has wrong '+label+' at stage '+stage);
    }
    const pdf=await x.saveDownload(invoice,scratch,()=>invoice.locator('input[name="printInvoice"]').click(),{timeout:60000});
    const content=graph.pageText(pdf.bytes,1).replace(/\s+/g,' ');
    h.assert(graph.pageCount(pdf.bytes)===1&&content.includes(bill.headerId)&&codes.every(code=>content.includes(code)),'Printed invoice loses invoice/line identity');
    for(const [label,value] of [['Total:','73.89'],['Payments:',money(totalPaid)],['Balance Due:',money(7389-totalPaid)]])
      h.assert(new RegExp(label+'\\s*\\$?\\s*'+value.replace('.','\\.')+'(?:\\s|$)').test(content),'Printed invoice has wrong '+label+' at stage '+stage);
    await invoice.close();unchanged();
  }
  for(let stage=0;stage<STAGES.length;stage++)await s.step('allocation '+(stage+1)+': exact per-line payments, balances, details, history, invoice and PDF',async()=>{
    const {page,correction}=await openPayments();await verifyRows(page);
    for(let i=0;i<3;i++) {
      const input=page.locator(`input[value="${items[i]}"][name^="itemId"]`),index=(await input.getAttribute('name')).slice('itemId'.length);
      await page.locator('#sel'+index).selectOption('payment');await page.locator('#payment'+index).fill(money(STAGES[stage][i]));await page.locator('#payment'+index).dispatchEvent('change');
    }
    await page.locator('#editPayment input[name="paymentType"]').first().check();
    let response;
    const dialogs=await h.withExpectedDialogs(page,async()=>{
      const reload=page.waitForEvent('load');[response]=await Promise.all([awaitResponse(s.context,ROUTE),page.locator('#saveBtn').click()]);await reload;
    });
    h.assert(response.status()===200&&dialogs.length===1&&dialogs[0].text==='Save payments successfully!','Native multi-item save did not truthfully confirm once');
    for(let i=0;i<3;i++)paid[i]+=STAGES[stage][i];
    await expectValue(sql,`SELECT COUNT(*) FROM billing_on_payment WHERE billing_no=${bill.headerId}`,String(stage+1),'Payment save did not add exactly one parent');
    const parents=sql.rows(`SELECT payment_id,total_payment,total_discount,total_credit,total_refund,creator FROM billing_on_payment WHERE billing_no=${bill.headerId} ORDER BY payment_id`);
    const parent=parents[stage];h.assert(JSON.stringify(parent.slice(1))===JSON.stringify([money(STAGES[stage].reduce((sum,value)=>sum+value,0)),'0.00','0.00','0.00',provider]),'Payment parent total/creator is wrong');payments.push(parent[0]);
    const allocation=sql.rows(`SELECT billing_on_item_id,paid,discount,credit,refund FROM billing_on_item_payment WHERE ch1_id=${bill.headerId} AND billing_on_payment_id=${parent[0]} ORDER BY billing_on_item_id`);
    const expected=items.map((id,index)=>[id,money(STAGES[stage][index]),'0.00','0.00','0.00']).filter((_,index)=>STAGES[stage][index]>0).sort((a,b)=>Number(a[0])-Number(b[0]));
    h.assert(JSON.stringify(allocation)===JSON.stringify(expected),'Payment was allocated to the wrong line or untouched line');
    for(let i=0;i<3;i++)h.assert(sql.value(`SELECT COALESCE(SUM(paid),0.00) FROM billing_on_item_payment WHERE ch1_id=${bill.headerId} AND billing_on_item_id=${items[i]}`)===money(paid[i]),'Persisted per-item paid balance is wrong');
    const transactions=sql.rows(`SELECT i.billing_on_item_id,t.payment_id,t.service_code,t.service_code_paid,t.service_code_discount,t.service_code_credit,t.service_code_refund,t.demographic_no
      FROM billing_on_transaction t JOIN billing_on_item_payment i ON i.id=t.billing_on_item_payment_id
      WHERE t.ch1_id=${bill.headerId} AND t.payment_id=${parent[0]} ORDER BY i.billing_on_item_id`);
    h.assert(JSON.stringify(transactions)===JSON.stringify(expected.map(row=>[row[0],parent[0],codes[items.indexOf(row[0])],row[1],'0.00','0.00','0.00',patient])),'Payment audit allocations do not reconcile');
    const totalPaid=money(paid.reduce((sum,value)=>sum+value,0));
    h.assert(sql.value(`SELECT CONCAT_WS('|',status,total,paid) FROM billing_on_cheader1 WHERE id=${bill.headerId}`)==='P|73.89|'+totalPaid,'One paid line incorrectly settled the invoice or header paid is wrong');
    h.assert(sql.value(`SELECT value FROM billing_on_ext WHERE billing_no=${bill.headerId} AND key_val='payment' AND status='1' ORDER BY id DESC LIMIT 1`)===totalPaid,'Header payment extension does not reconcile');
    await page.locator('#editPayment').waitFor();await verifyRows(page);
    const before=snapshot(bill.headerId),link=page.locator(`a[href*="onViewPayment('${parent[0]}')"],a[href*="method=viewPayment_ext"][href$="billPaymentId=${parent[0]}"]`);
    const details=await s.popup(page,link,'multi-item-payment-details');await details.getByText('View payment details:',{exact:true}).waitFor();
    const rows=await details.locator('table').first().locator('tr').allTextContents();h.assert(rows.length===expected.length+1,'Payment details show extra/missing item allocations');
    for(const row of expected) {
      const code=codes[items.indexOf(row[0])],cells=await details.locator('tr',{has:details.locator('td',{hasText:code})}).first().locator('td').allTextContents();
      h.assert(cells.length===7&&cells[0]===marker+'-MAIN,Workflow'&&cells[1]===bill.headerId&&cells[2]===code&&cells[3]===row[1]&&cells.slice(4).every(value=>value==='0.00'),'Payment details lose line/patient/amount identity');
    }
    await details.close();h.assert(snapshot(bill.headerId)===before,'Viewing payment details changed financial rows');
    await page.close();if(!correction.isClosed())await correction.close();
    const reopened=await openPayments();await verifyRows(reopened.page);await reopened.page.close();if(!reopened.correction.isClosed())await reopened.correction.close();
    await verifyInvoice(stage+1);unchanged();
  });
}
if(require.main===module)runWorkflow(NAME,workflow,{preflightConfig:()=>{
  if(process.env.CARLOS_DISPOSABLE_HOST!=='true'||process.env.EXCLUSIVE!=='1')throw new h.SkipCheck('Private invoice allocation requires CARLOS_DISPOSABLE_HOST=true and EXCLUSIVE=1');
  requirePoppler('pdfinfo','pdftotext');
}});
module.exports={workflow};
