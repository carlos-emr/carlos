/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h=require('./playwright-harness');
function programReferenceGuard(sql,program) {
  h.assert(/^[1-9]\d*$/.test(String(program)),'Invalid program reference key');
  const refs=sql.rows(`SELECT DISTINCT c.TABLE_NAME,c.COLUMN_NAME FROM information_schema.COLUMNS c
    JOIN information_schema.TABLES t ON t.TABLE_SCHEMA=c.TABLE_SCHEMA AND t.TABLE_NAME=c.TABLE_NAME AND t.TABLE_TYPE='BASE TABLE'
    WHERE c.TABLE_SCHEMA=DATABASE() AND (LOWER(REPLACE(c.COLUMN_NAME,'_','')) IN ('programid','programno')
      OR EXISTS (SELECT 1 FROM information_schema.KEY_COLUMN_USAGE k WHERE k.TABLE_SCHEMA=c.TABLE_SCHEMA
        AND k.TABLE_NAME=c.TABLE_NAME AND k.COLUMN_NAME=c.COLUMN_NAME AND LOWER(k.REFERENCED_TABLE_NAME)='program'))`);
  for(const [table,column] of refs)h.assert(/^[A-Za-z_][A-Za-z0-9_]*$/.test(table)&&/^[A-Za-z_][A-Za-z0-9_]*$/.test(column),'Unsafe program reference metadata');
  h.assert(refs.length>0,'Program reference discovery found no schema references');
  return {assertUnused(){for(const [table,column] of refs)h.assert(sql.value(`SELECT COUNT(*) FROM \`${table}\` WHERE \`${column}\`=${h.sqlString(String(program))}`)==='0',`Program ID is referenced by ${table}; retain the fixture and choose an unused ID`);}};
}
module.exports={programReferenceGuard};
