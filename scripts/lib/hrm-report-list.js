/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

/** Locate one owned report in ViewDocList, keeping the ID parameter boundary exact. */
function hrmReportLink(list, reportId) {
  if (!/^[1-9]\d*$/.test(String(reportId))) throw new Error('Invalid HRM report ID');
  // displayHRMDocList.jsp carries demographicNo after id; a closing quote no longer follows it.
  return list.locator(`#tblHRM a[onclick*="/hospitalReportManager/Display?id=${reportId}&"]`);
}

module.exports = { hrmReportLink };
