/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const { assert, sqlString } = require('./lib/playwright-harness');

/** Create an owned sender so fax UI checks do not depend on unrelated clinic accounts. */
function stageRxFaxAccount(db, faxNumber) {
  assert(/^[0-9]{10}$/.test(faxNumber), 'Fixture sender number must contain ten digits');
  const existing = db.value(`SELECT id FROM fax_config WHERE faxNumber=${sqlString(faxNumber)} LIMIT 1`);
  assert(!existing, 'Random sender number collided with an existing account; rerun with a new fixture');
  const id = db.value('INSERT INTO fax_config '
    + '(providerType,active,faxNumber,faxReply,accountName,senderEmail,faxUser,siteUser,passwd,faxPasswd,gatewayName,queue,url,download) '
    + `VALUES ('SRFAX',1,${sqlString(faxNumber)},${sqlString(faxNumber)},'Playwright Fax','fax@example.invalid','faxuser','siteuser','x','x','srfax','0','',1); SELECT LAST_INSERT_ID()`);
  assert(/^[1-9][0-9]*$/.test(id), 'Owned fax sender was not created');
  return { id, faxNumber };
}

/** Remove only this invocation's sender; a replaced record makes cleanup fail visibly. */
function cleanupRxFaxAccount(db, fixture) {
  if (!fixture) return;
  assert(/^[1-9][0-9]*$/.test(fixture.id) && /^[0-9]{10}$/.test(fixture.faxNumber), 'Invalid owned fax sender identifiers');
  db.execute(`DELETE FROM fax_config WHERE id=${fixture.id} AND faxNumber=${sqlString(fixture.faxNumber)} AND accountName='Playwright Fax'`);
  assert(db.value(`SELECT COUNT(*) FROM fax_config WHERE id=${fixture.id}`) === '0', 'Owned fax sender cleanup failed or ownership changed');
}
module.exports = { stageRxFaxAccount, cleanupRxFaxAccount };
