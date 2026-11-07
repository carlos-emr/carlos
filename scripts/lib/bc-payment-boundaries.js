/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
const crypto=require('node:crypto');
const fs=require('node:fs');
const h=require('./playwright-harness');
const {openAdmin,adminFrame}=require('../billing-on-admin-config-playwright-checks');
const {parseCsv}=require('./export-content-helpers');
const q=h.sqlString;

function seedProvider(s) {
  const {sql,marker}=s;let id;
  for(let attempt=0;attempt<40;attempt++) {
    const candidate=String(crypto.randomInt(10000,90000));
    if(sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${q(candidate)} OR ohip_no=${q(candidate)}`)==='0'){id=candidate;break;}
  }
  h.assert(id,'No free BC payment provider number');
  s.cleanup(()=>{
    h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${q(id)} AND last_name=${q(marker)}`)==='1','BC payment provider ownership changed');
    sql.execute(`DELETE FROM provider WHERE provider_no=${q(id)} AND last_name=${q(marker)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${q(id)}`)==='0','BC payment provider remains');
  });
  sql.execute(`INSERT INTO provider(provider_no,last_name,first_name,provider_type,specialty,sex,status,ohip_no,lastUpdateUser,lastUpdateDate)
    VALUES(${q(id)},${q(marker)},'Payment','doctor','','F','1',${q(id)},${q(s.provider)},NOW())`);
  return id;
}

async function boundaries({s,pay,receipt,billStatus,billingNo,billingmasterNo,submit,status}) {
  const {sql,marker}=s;
  // Clone only owned setup rows, retaining a distinguishable control invoice.
  function clone(table,key,original,replacements) {
    const columns=sql.rows(`SELECT COLUMN_NAME FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=${q(table)} ORDER BY ORDINAL_POSITION`)
      .flat().filter(name=>name!==key);
    h.assert(columns.length&&columns.every(name=>/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(name)),'Unexpected BC fixture column identity');
    const names=columns.map(name=>'`'+name+'`');
    const values=columns.map((name,index)=>Object.hasOwn(replacements,name)?replacements[name]:names[index]);
    const id=sql.value(`INSERT INTO ${table}(${names.join(',')}) SELECT ${values.join(',')} FROM ${table} WHERE ${key}=${original}; SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id),'Control invoice clone failed');return id;
  }
  const controlBill=clone('billing','billing_no',billingNo,{demographic_name:q(marker+' CONTROL')});
  const controlMaster=clone('billingmaster','billingmaster_no',billingmasterNo,{billing_no:controlBill});
  const snapshot=(bill,master)=>JSON.stringify({bill:sql.rows(`SELECT * FROM billing WHERE billing_no=${bill}`),
    master:sql.rows(`SELECT * FROM billingmaster WHERE billingmaster_no=${master}`),
    history:sql.rows(`SELECT * FROM billing_history WHERE billingmaster_no=${master} ORDER BY id`),
    notes:sql.rows(`SELECT * FROM billingnote WHERE billingmaster_no=${master}`),
    private:sql.rows(`SELECT * FROM billing_private_transactions WHERE billingmaster_no=${master}`)});
  const unchanged=snapshot(controlBill,controlMaster);
  const method=await pay.locator('select#paymentMethod').inputValue();
  const count=()=>sql.value(`SELECT COUNT(*) FROM billing_history WHERE billingmaster_no=${billingmasterNo}`);
  const received=()=>sql.value(`SELECT CAST(COALESCE(SUM(CAST(amount_received AS DECIMAL(10,2))),0)*100 AS SIGNED) FROM billing_history WHERE billingmaster_no=${billingmasterNo}`);
  async function reopen() {
    await receipt.locator('.rcvPayment a',{hasText:'Receive Payment'}).first().click();
    await pay.waitForURL(/\/billing\/CA\/BC\/viewReceivePaymentAction\?/,{waitUntil:'load'});
    h.assert(await pay.locator('input#billingmasterNo').inputValue()===billingmasterNo,'Payment reopen changed billing line');
    await pay.locator('select#paymentMethod').selectOption(method);
  }
  function unchangedControl(){h.assert(snapshot(controlBill,controlMaster)===unchanged,'Payment operation changed the control invoice');}
  await s.step('a tokenless payment POST is refused without changing either invoice',async()=>{
    const before=snapshot(billingNo,billingmasterNo);
    const response=await s.context.request.post(h.appUrl(s.config.baseUrl,'/billing/CA/BC/receivePaymentAction'),{
      form:{billingmasterNo,billNo:billingNo,amountReceived:'2.00',paymentMethod:method},maxRedirects:0});
    h.assert(response.status()===403,'Tokenless payment POST was not refused by CSRF protection');
    h.assert(snapshot(billingNo,billingmasterNo)===before,'Tokenless POST changed financial rows');unchangedControl();
    await response.dispose();
  });
  await s.step('zero, negative, malformed, excessive and sub-cent amounts are refused without partial financial rows',async()=>{
    for(const amount of ['0.00','-0.01','FAKE','10000.0','1.234']) {
      const before=snapshot(billingNo,billingmasterNo);
      await pay.locator('select#paymentMethod').selectOption(method);await submit(amount,'invalid payment amount');
      h.assert(await pay.locator('#receivePaymentError').count()===1,'Invalid amount produced no validation feedback');
      h.assert(/Enter (an amount|a payment)/.test(await pay.locator('#receivePaymentError').innerText()),'Amount refusal feedback is not the payment validation result');
      h.assert(await pay.locator('#billNo').inputValue()===billingNo&&await pay.locator('#billingmasterNo').inputValue()===billingmasterNo,'Refused input changed invoice attribution');
      h.assert(snapshot(billingNo,billingmasterNo)===before,'Refused input changed financial rows');unchangedControl();
    }
    h.assert(await pay.locator('input[name="isRefund"]').count()===0,'The target now offers a refund control; update the qualification contract');
    console.log('  Contract: the release offers no native refund control; negative amount text is refused.');
  });
  await s.step('corrected partial cents and an above-balance payment retain exact amounts and payment method',async()=>{
    await pay.locator('select#paymentMethod').selectOption(method);await submit('10.01','corrected partial payment');
    h.assert(count()==='1'&&received()==='1001'&&status()==='P','Partial payment/history/status disagree');
    h.assert(/10\.01/.test(await pay.locator('.msgDisplay').first().innerText()),'Partial-payment confirmation lost its exact cents');
    unchangedControl();await reopen();await submit('20.00','overpayment');
    h.assert(count()==='2'&&received()==='3001'&&status()==='A','Accepted overpayment/history/paid-private status disagree');
    const rows=sql.rows(`SELECT CAST(CAST(amount_received AS DECIMAL(10,2))*100 AS SIGNED),payment_type_id FROM billing_history WHERE billingmaster_no=${billingmasterNo} ORDER BY id`);
    h.assert(JSON.stringify(rows)===JSON.stringify([['1001',method],['2000',method]]),'History has a rounded, duplicated or wrong-method payment');
    h.assert(sql.value(`SELECT CAST(CAST(bill_amount AS DECIMAL(10,2))*100 AS SIGNED) FROM billingmaster WHERE billingmaster_no=${billingmasterNo}`)==='2300','Payment overwrote the billed fee');
    unchangedControl();
  });
  await s.step('reopened Invoice List and accounting export reconcile the exact payment and credit balance',async()=>{
    await billStatus.reload({waitUntil:'load'});
    const row=billStatus.locator(`input[value="${billingNo}_${billingmasterNo}"]`).locator('xpath=ancestor::tr[1]');
    await row.waitFor();
    const texts=(await row.locator('td').allInnerTexts()).map(text=>text.trim());
    const headers=(await row.locator('xpath=ancestor::table[1]').locator('th').allInnerTexts()).map(text=>text.trim());
    function cents(text) {
      const value=text.replace(/^(-?)(?:CA)?[$¤]/,'$1');
      h.assert(/^-?\d+\.\d{2}$/.test(value),'Invoice amount is not exact formatted cents');
      return BigInt(value.replace('.',''));
    }
    for(const [label,expected] of [['AMT',2300n],['PAID',3001n],['OWED',-701n]]) {
      const index=headers.indexOf(label);h.assert(index>=0,'Invoice amount column is missing');
      h.assert(cents(texts[index])===expected,'Invoice List does not show billed, paid and credit amounts consistently');
    }
    await pay.close();if(!receipt.isClosed())await receipt.close();
    const reopened=await s.popup(billStatus,row.getByRole('link',{name:billingNo,exact:true}),'bc-reopened-payment-receipt');
    h.assert((await reopened.locator('body').innerText()).includes(marker)
      &&await reopened.getByText(billingNo,{exact:true}).count()>0,'Reopened receipt lost patient or invoice attribution');
    h.assert(/23\.00/.test(await reopened.locator('body').innerText()),'Receipt face amount changed after payment');
    await reopened.close();
    const admin=await openAdmin(s);const frame=await adminFrame(admin,'/billing/CA/BC/ViewBillingAccountReports','select[name="repType"]');
    await frame.locator('select[name="repType"]').selectOption('REP_PAYREF');
    for(const name of ['selProv','selPayee','selAccount'])await frame.locator(`select[name="${name}"]`).selectOption('ALL');
    await frame.locator('select[name="docFormat"]').selectOption('csv');
    const date=sql.value("SELECT DATE_FORMAT(CURDATE(),'%Y-%m-%d')");
    for(const [name,trigger] of [['xml_vdate','#hlSDate'],['xml_appointment_date','#hlADate']]) {
      await frame.locator(trigger).click();
      const label=new Intl.DateTimeFormat('en-US',{month:'long',day:'numeric',year:'numeric',timeZone:'UTC'}).format(new Date(date+'T12:00:00Z'));
      await frame.locator(`.flatpickr-calendar.open .flatpickr-day[aria-label="${label}"]:not(.flatpickr-disabled)`).click();
      h.assert(await frame.locator(`[name="${name}"]`).inputValue()===date,'Accounting date picker did not select the independent current date');
    }
    const [download,response]=await Promise.all([
      admin.waitForEvent('download'),admin.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/createBillingReportAction')),
      frame.locator('input[type="submit"][name="Submit"]').click(),
    ]);
    h.assert(response.status()===200&&/^application\/csv/i.test(response.headers()['content-type']||''),'Payments accounting report did not produce CSV');
    try {
      h.assert(await download.failure()===null,'Payments accounting download failed');
      const grid=parseCsv(fs.readFileSync(await download.path(),'utf8'));const header=grid[0];
      const person=header.indexOf('Patient'),amount=header.indexOf('Amount');h.assert(person>=0&&amount>=0,'Payments report columns are missing');
      const own=grid.slice(1).filter(row=>row[person]?.includes(marker));
      const actual=own.map(row=>row[amount]).sort();h.assert(JSON.stringify(actual)===JSON.stringify(['10.01','20.00']),'Accounting report lost or duplicated a recorded payment');
      h.assert(actual.reduce((sum,text)=>{h.assert(/^\d+\.\d{2}$/.test(text),'Malformed exported money');return sum+BigInt(text.replace('.',''));},0n)===3001n,'Accounting subtotal disagrees with recorded cents');
    }finally{await download.delete();}
    unchangedControl();await admin.close();
  });
}
module.exports={seedProvider,boundaries};
