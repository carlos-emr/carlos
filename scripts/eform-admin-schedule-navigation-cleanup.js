/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h=require('./lib/playwright-harness');
const FIXTURE_TEXT='Temporary Playwright navigation regression eForm.';
function ownedRows(rows,{marker,name,subject}) {
  h.assert(/^FAKE-PW[0-9a-f]{16}$/.test(marker)&&name===marker+' Admin Nav'&&subject===marker+' Schedule navigation','Invalid owned eForm identity');
  for(const [fid,formName,storedSubject,sha,html] of rows)h.assert(/^[1-9]\d*$/.test(fid)&&formName===name&&(storedSubject===subject||storedSubject===null||storedSubject==='')&&/^[a-f0-9]{64}$/.test(sha)&&typeof html==='string'&&html.includes(marker)&&html.includes(FIXTURE_TEXT),'eForm fixture ownership changed; retain it for recovery');
  return rows;
}
function removeOwnedForms(sql,identity) {
  const rows=ownedRows(sql.rows(`SELECT fid,form_name,subject,SHA2(form_html,256),form_html FROM eform WHERE form_name=${h.sqlString(identity.name)}`),identity);
  for(const [fid,name,subject,sha] of rows) {
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM eform_data WHERE fid=${fid})+(SELECT COUNT(*) FROM eform_groups WHERE fid=${fid})`)==='0','Owned library form acquired patient instances or group references; retain it');
    const subjectClause=subject===null?'subject IS NULL':`subject=${h.sqlString(subject)}`;
    sql.execute(`DELETE FROM eform WHERE fid=${fid} AND form_name=${h.sqlString(name)} AND ${subjectClause} AND SHA2(form_html,256)=${h.sqlString(sha)}`);
  }
  h.assert(sql.value(`SELECT COUNT(*) FROM eform WHERE form_name=${h.sqlString(identity.name)}`)==='0','Owned library forms remain after cleanup');
}
module.exports={ownedRows,removeOwnedForms};
