/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
'use strict';
const {assert}=require('./playwright-harness');

/** Reserve an unused sender identity above legacy log references, without changing them. */
function nextEmailSenderId(sql) {
  const logical=sql.rows("SELECT TABLE_NAME,COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND LOWER(COLUMN_NAME)='configid' ORDER BY TABLE_NAME,COLUMN_NAME");
  assert(JSON.stringify(logical)===JSON.stringify([['emailLog','configId']]),'Unreviewed email sender logical reference inventory');
  const foreign=sql.rows("SELECT TABLE_NAME,COLUMN_NAME,REFERENCED_COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME='emailConfig' ORDER BY TABLE_NAME,COLUMN_NAME");
  assert(foreign.every(row=>JSON.stringify(row)===JSON.stringify(['emailLog','configId','id']))&&foreign.length<=1,'Unreviewed email sender foreign reference inventory');
  const row=sql.rows(`SELECT (SELECT COALESCE(MAX(id),0) FROM emailConfig),
    (SELECT COALESCE(MAX(configId),0) FROM emailLog),
    (SELECT AUTO_INCREMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='emailConfig')`);
  assert(row.length===1&&row[0].length===3&&row[0].every(value=>/^[0-9]+$/.test(value)),'Invalid sender identity allocation evidence');
  const values=row[0].map(Number);
  assert(values.every(value=>Number.isSafeInteger(value)&&value>=0&&value<2147483647)&&values[2]>0,'Sender identity range exhausted');
  const id=Math.max(values[0]+1,values[1]+1,values[2]);
  assert(id<2147483647,'Sender identity range exhausted');
  assert(sql.value(`SELECT (SELECT COUNT(*) FROM emailConfig WHERE id=${id})+(SELECT COUNT(*) FROM emailLog WHERE configId=${id})`)==='0','Sender identity became referenced before allocation');
  return String(id);
}
module.exports={nextEmailSenderId};
