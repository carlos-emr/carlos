#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert=require('node:assert/strict'),h=require('./lib/playwright-harness');
const {runWorkflow}=require('./lib/workflow-session');
const base=require('./form-rourke2020-growth-playwright-checks');
const oracle=require('./lib/growth-clinical-oracle');
const {newOwnedMarker,createOwnedPatient,removeOwnedPatient}=require('./lib/owned-patient');
const {openMasterRecord}=require('./master-record-tabs-playwright-checks');
const {openChart,waitForNavbars}=require('./echart-navbar-modules-playwright-checks');
const {releaseChartLocks}=require('./lib/chart-lock-cleanup');
const {consumeUnloadBeacons}=require('./echart-note-draft-recovery-playwright-checks');
const {failureMark}=require('./lib/concurrency-support');
const {readPatientState,assertPatientStateUnchanged}=require('./echart-two-patient-isolation-playwright-checks');
const {hasUncertainWrites}=require('./lib/uncertain-server-writes');
const q=h.sqlString;
const ROURKE=[
  {days:0,weight:3.2,length:50.5,head:34.5},{days:7,weight:3.6,length:52.1,head:35.5},
  {days:14,weight:3.9,length:53.6,head:36.1},{days:61,weight:5.1,length:57.2,head:38.5},
  {days:274,weight:8.2,length:70.3,head:43.1},{days:548,weight:10.2,length:80.1,head:46.8},
];
const GROWTH=[
  {days:2,age:0,weight:3.3,length:50,head:34.5},{days:30,age:1,weight:4.4,length:54.8,head:37.2},
  {days:120,age:3.9,weight:6.5,length:61.5,head:40.8},{days:365,age:12,weight:9.2,length:74.5,head:45.5},
  {days:729,age:23.95,weight:12.4,length:87.6,head:48.7},
];
const MONTHS_PER_DAY=12/365.2425;
function expectedObservations({form,head,stage}){
  const source=form==='rourke2020'?ROURKE:GROWTH;
  const age=row=>form==='rourke2020'?row.days*MONTHS_PER_DAY:row.age;
  const points=source.flatMap(row=>head?[{kind:'head',age:age(row),value:row.head},{kind:'weightLength',length:row.length,value:row.weight}]
    :[{kind:'weight',age:age(row),value:row.weight},{kind:'length',age:age(row),value:row.length}]);
  if(stage==='measurements')for(let copy=0;copy<2;copy++)points.push({kind:'weight',age:100*MONTHS_PER_DAY,value:6.4},{kind:'length',age:100*MONTHS_PER_DAY,value:62.5});
  if(stage==='combined'){
    // Identical same-date WT/HT pairs share one weight-for-length location.
    // The date-based weight/length plots above retain both source observations.
    points.push({kind:'weightLength',length:62.5,value:6.4});
    for(const row of GROWTH)points.push({kind:'head',age:row.days*MONTHS_PER_DAY,value:row.head},{kind:'weightLength',length:row.length,value:row.weight});
  }
  return points;
}
function inspectGraph(evidence){
  const {form,what,body,sex,stage}=evidence,head=/head/i.test(what),points=oracle.circles(body);
  const expected=expectedObservations({form,head,stage});
  assert.equal(points.length,form==='rourke2020'&&!head?2:1,'Native growth PDF page count differs');
  for(const page of points.slice(1))assert.equal(page.length,0,'Infant data leaked onto the older-child page');
  const blueSeries=head?'head':form==='rourke2020'?'weight':'length';
  const coordinates=oracle.verifyPoints(points[0],expected,{blueSeries,sex});
  const curves=oracle.verifyCurves(body,oracle.reference(sex,head?'head':form==='rourke2020'?'length':'growthLength'),points[0],{head});
  if(points.length===2)oracle.verifyCurves(body,oracle.reference(sex,'length'),[],{page:2});
  let medianRelationship;
  if(!head){
    // WHO 2006 weight-for-age median table, kg: at 12 months girls8.9/boys9.6;
    // at 18 months girls10.2/boys10.9. These clinical reference values are
    // independent of the active servlet and its plotting properties.
    const age=form==='growth036'?12:548*MONTHS_PER_DAY,value=form==='growth036'?9.2:10.2;
    const median=form==='growth036'?(sex==='F'?8.9:9.6):(sex==='F'?10.2:10.9);
    const expectedPoint=oracle.expectedPoint({kind:'weight',age,value},sex);
    const actual=points[0].filter(point=>point.colour===(blueSeries==='weight'?'0,0,1':'1,0,0'))
      .sort((a,b)=>Math.hypot(a.x-expectedPoint.x,a.y-expectedPoint.y)-Math.hypot(b.x-expectedPoint.x,b.y-expectedPoint.y))[0];
    const medianY=oracle.expectedPoint({kind:'weight',age,value:median},sex).y,difference=medianY-actual.y;
    medianRelationship=value===median?'on 50th':value>median?'above 50th':'below 50th';
    assert(value===median?Math.abs(difference)<=3:value>median?difference>5:difference< -5,
      'Native observation has the wrong relationship to the independent WHO median curve');
  }
  console.log('  EVIDENCE independent growth '+JSON.stringify({sex,form,stage,head,medianRelationship,...coordinates,...curves}));
}
async function workflow(s){
  const {sql,provider}=s,owned=[];
  // Register partial-setup recovery before claiming either extra patient's name.
  for(const role of ['male','control']){
    const fixture={marker:newOwnedMarker(),role};owned.push(fixture);
    s.cleanup(()=>{
      fixture.patient||=sql.value(`SELECT demographic_no FROM demographic WHERE last_name=${q(fixture.marker)}`);
      if(fixture.patient)removeOwnedPatient(sql,fixture.patient,fixture.marker);
    });
    fixture.patient=createOwnedPatient(sql,{marker:fixture.marker,provider});
  }
  const [male,control]=owned;
  sql.execute(`UPDATE demographic SET year_of_birth='2024',month_of_birth='11',date_of_birth='01' WHERE demographic_no=${control.patient};
    INSERT INTO measurements(type,demographicNo,providerNo,dataField,measuringInstruction,comments,dateObserved,dateEntered,appointmentNo)
    VALUES('WT',${control.patient},${q(provider)},'6.123','in kg',${q(control.marker)},'2025-02-20 10:00:00',NOW(),0),
      ('HT',${control.patient},${q(provider)},'63.1','in cm',${q(control.marker)},'2025-02-20 10:00:00',NOW(),0)`);
  const controlBefore=readPatientState(sql,control.patient),failures=[];
  for(const fixture of [{patient:s.patient,marker:s.marker,sex:'F'}, {...male,sex:'M'}]){
    let master,chart;
    let rourkeBefore,growthBefore,measurementsBefore;
    const records=table=>sql.rows(`SELECT * FROM ${table} WHERE demographic_no=${fixture.patient} ORDER BY ID`);
    const inspect=evidence=>{
      const rourke=records('formRourke2020');
      if(!rourkeBefore)rourkeBefore=rourke;else assert.deepEqual(rourke,rourkeBefore,'Native graph/print changed saved Rourke history');
      if(evidence.form==='growth036'||evidence.stage==='combined'){
        const growth=records('formGrowth0_36');
        if(!growthBefore)growthBefore=growth;else assert.deepEqual(growth,growthBefore,'Native graph/print changed saved Growth history');
      }
      if(evidence.stage==='measurements'||growthBefore){
        const measurements=sql.rows(`SELECT * FROM measurements WHERE demographicNo=${fixture.patient} ORDER BY id`);
        if(!measurementsBefore)measurementsBefore=measurements;else assert.deepEqual(measurements,measurementsBefore,'Native graph/print changed module observations');
      }
      inspectGraph(evidence);
    };
    if(fixture.patient===s.patient)master=s.master;
    else ({masterPage:master}=await openMasterRecord(s.context,s.schedule,s.recorder,{searchTerm:fixture.marker,preferredDemographicNo:fixture.patient,timeout:20000}));
    const session={...s,patient:fixture.patient,marker:fixture.marker,master,async chart(){
      if(!chart||chart.isClosed()){chart=await openChart(s.context,master,s.recorder,20000);await waitForNavbars(chart,20000);}return chart;
    }};
    try{
      const dob={y:2024,m:11,d:fixture.sex==='F'?1:2},last=GROWTH.at(-1);
      const date=base.day(dob,last.days),additionalGrowthFields=[
        {kind:'date',name:'date_4',page:0,value:`${date.y}/${String(date.m).padStart(2,'0')}/${String(date.d).padStart(2,'0')}`},
        ...Object.entries({age:last.age,weight:last.weight,length:last.length,headCirc:last.head,comment:'boundary23.95'})
          .map(([key,value])=>({kind:'text',name:key+'_4',page:0,value:String(value)})),
      ];
      await s.step(fixture.sex+' infant native growth coordinates, curve identity and independent source inclusion',()=>base.workflow(session,{
        select:['rourke2020.graph','rourke2020.graphmeasure','rourke2020.graphgrowth','growth036.graph','growth036.printdob'],
        sex:fixture.sex,birthDate:dob,additionalGrowthFields,repeatMeasurementFixture:true,inspectGraph:inspect,
      }));
      assert.equal(sql.value(`SELECT COUNT(*) FROM measurements WHERE demographicNo=${fixture.patient}`),'4','Graph reads changed or duplicated the four independent module fixtures');
      assert(rourkeBefore?.length&&growthBefore?.length,'Saved graph-source history snapshots were not reached');
      assert.deepEqual(records('formRourke2020'),rourkeBefore,'Growth reads changed saved Rourke history');
      assert.deepEqual(records('formGrowth0_36'),growthBefore,'Growth reads changed saved Growth history');
      assertPatientStateUnchanged(controlBefore,readPatientState(sql,control.patient),'Growth clinical control');
    }catch(error){
      failures.push(error);if(hasUncertainWrites(s.context))throw error;
    }
    // Retire this run's chart locks before another fixture's native navigation.
    const mark=failureMark(s.recorder);await releaseChartLocks(s.context,s.config.baseUrl);
    for(const page of s.context.pages())if(page!==s.schedule&&!page.isClosed()){
      await page.waitForLoadState('networkidle');await page.close();
    }
    consumeUnloadBeacons(s.recorder,mark);
  }
  if(failures.length)throw new AggregateError(failures,'Independent growth acceptance failed: '+failures.map(error=>error.message).join('; '));
}
function preflightConfig(){
  if(process.env.EXCLUSIVE!=='1')throw new h.SkipCheck('Growth clinical geometry requires EXCLUSIVE=1 for owned Forms-menu registrations');
  require('./lib/export-content-helpers').requirePoppler('pdftotext','pdfinfo','pdftoppm');
  for(const sex of ['F','M'])for(const kind of ['length','growthLength','head'])oracle.reference(sex,kind);
}
if(require.main===module)runWorkflow('form-growth-clinical-geometry',workflow,{preflightConfig,contextOptions:{timezoneId:base.zoneWhereTodayDiffersFromUtc()}});
module.exports={workflow,preflightConfig,expectedObservations,inspectGraph,ROURKE,GROWTH};
