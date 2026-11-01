/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h=require('./playwright-harness');
const {openMasterRecord}=require('../master-record-tabs-playwright-checks');

// A separate top-level schedule tab gives the legacy named popups independent
// opener trees, as a clinician working in two browser windows does. All patient
// selection still goes through the schedule's Search and Master Record controls.
async function openSecondPatient(s,patient,searchTerm) {
  const schedule=await s.context.newPage(),url=new URL(s.schedule.url());
  h.assert(url.origin===s.config.baseUrl.origin&&url.pathname.startsWith(s.config.baseUrl.pathname+'/'),'Second schedule must stay in the application');
  await h.gotoApp(schedule,s.config.baseUrl,url.pathname.slice(s.config.baseUrl.pathname.length)+url.search);
  return openMasterRecord(s.context,schedule,s.recorder,{searchTerm,preferredDemographicNo:patient,timeout:20000});
}
module.exports={openSecondPatient};
