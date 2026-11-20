/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h=require('./playwright-harness');
const {openMasterRecord}=require('../master-record-tabs-playwright-checks');

// A separate top-level schedule tab gives the legacy named popups independent
// opener trees, as a clinician working in two browser windows does. All patient
// selection still goes through the schedule's Search and Master Record controls.
async function openSecondPatient(s,patient,searchTerm) {
  const route=scheduleRoute(s.config.baseUrl,s.scheduleUrl),schedule=await s.context.newPage();
  await h.gotoApp(schedule,s.config.baseUrl,route);
  const master=await openMasterRecord(s.context,schedule,s.recorder,{searchTerm,preferredDemographicNo:patient,timeout:20000});
  h.assert(new URL(master.masterPage.url()).searchParams.get('demographic_no')===String(patient),'Second Master Record does not belong to the requested patient');
  return master;
}
function scheduleRoute(baseUrl,savedUrl) {
  h.assert(typeof savedUrl==='string'&&savedUrl.length>0,'The original schedule URL was not captured');
  const url=new URL(savedUrl),prefix=baseUrl.pathname.replace(/\/$/,'');
  h.assert(url.origin===baseUrl.origin&&!url.username&&!url.password&&url.pathname.startsWith(prefix+'/'),'Second schedule must stay in the application');
  return url.pathname.slice(prefix.length)+url.search;
}
module.exports={openSecondPatient,scheduleRoute};
