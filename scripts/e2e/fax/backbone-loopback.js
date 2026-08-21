// Backbone loopback test: injects a WAITING outbound fax to the account's own
// number, confirms the scheduler sends it through the real SRFax Queue_Fax API
// (WAITING -> SENT with a provider job id), then confirms an inbound fax is
// downloaded (Retrieve_Fax) and imported (status RECEIVED) and routed to the
// UNCLAIMED inbox so it can be signed to a provider.
//
// This exercises the shared send/receive backbone that every clinical outbound
// entry point funnels into. It talks to the live SRFax API — never run in CI.
//
// Prereqs: fixtures.sql loaded, SRFax configured (see lib.ensureSrfaxConfigured
// or the admin UI). DB access is via the mariadb CLI as root (local socket),
// matching how the deployment is administered; pass MARIADB="sudo mariadb" etc.
'use strict';
const { execFileSync } = require('child_process');
const { cfg } = require('./lib');

// MARIADB may be a multi-word launcher (e.g. "sudo mariadb"); split into argv
// so nothing is ever parsed by a shell. execFileSync takes an argv array — no
// shell, so DB names/queries cannot be shell-injected.
const MARIADB = (process.env.MARIADB || 'mariadb').split(/\s+/);
const DB = process.env.CARLOS_DB_NAME || 'oscar';
const DOCDIR = process.env.CARLOS_DOCUMENT_DIR
  || '/var/lib/carlos-emr/OscarDocument/carlos/document';

function sql(q) {
  const [cmd, ...pre] = MARIADB;
  return execFileSync(cmd, [...pre, '-N', DB, '-e', q], { encoding: 'utf8' }).trim();
}
function sh(cmd, args, opts) { return execFileSync(cmd, args, opts); }

async function main() {
  const c = cfg();
  const faxNo = c.srfax.faxNumber;
  const loopbackDial = process.env.SRFAX_LOOPBACK_DIAL || ('1' + faxNo);

  // Stage a one-page PDF under the document directory.
  const pdf = '%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n'
    + '2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n'
    + '3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]/Resources<</Font'
    + '<</F1 4 0 R>>>>/Contents 5 0 R>>endobj\n'
    + '4 0 obj<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>endobj\n'
    + '5 0 obj<</Length 60>>stream\nBT /F1 18 Tf 72 700 Td (CARLOS E2E loopback) Tj ET\n'
    + 'endstream endobj\ntrailer<</Root 1 0 R/Size 6>>\n%%EOF';
  const fname = 'e2e-backbone-loopback.pdf';
  sh('install', ['-m', '0644', '/dev/stdin', `${DOCDIR}/${fname}`], { input: pdf });

  const demo = sql(`SELECT demographic_no FROM demographic WHERE last_name='Loopback' AND first_name='Faxtest' ORDER BY demographic_no LIMIT 1`);
  if (!demo) throw new Error('fixture demographic Loopback/Faxtest not found — load fixtures.sql first');
  const before = parseInt(sql(`SELECT COUNT(*) FROM faxes WHERE direction='IN'`), 10) || 0;
  sql(`INSERT INTO faxes (filename,faxline,destination,recipient,status,statusString,`
    + `numPages,stamp,user,oscarUser,demographicNo,sender,direction) VALUES `
    + `('${fname}','${faxNo}','${loopbackDial}','E2E loopback','WAITING',`
    + `'queued by backbone-loopback',1,NOW(),'999998','999998',${demo},'${faxNo}','OUT')`);
  const id = sql(`SELECT id FROM faxes WHERE filename='${fname}' ORDER BY id DESC LIMIT 1`);
  console.log(`injected WAITING outbound fax id=${id} -> ${loopbackDial}`);

  // Wait for the scheduler (60s poll) to send it via the real SRFax API.
  let sent = false;
  for (let i = 0; i < 10 && !sent; i++) {
    sh('sleep', ['15']);
    const st = sql(`SELECT status FROM faxes WHERE id=${id}`);
    const job = sql(`SELECT COALESCE(jobId,'') FROM faxes WHERE id=${id}`);
    console.log(`  t+${(i + 1) * 15}s status=${st} jobId=${job || '(none)'}`);
    if (st === 'SENT' && job) { sent = true; console.log(`OUTBOUND OK: SENT with provider job id ${job}`); }
    if (st === 'ERROR') throw new Error('outbound fax went to ERROR: ' + sql(`SELECT statusString FROM faxes WHERE id=${id}`));
  }
  if (!sent) throw new Error('outbound fax did not reach SENT within the timeout');

  // Wait for an inbound fax to be downloaded + imported (RECEIVED) and routed.
  console.log('waiting for the inbound loopback copy to import (several minutes)...');
  let imported = false;
  for (let i = 0; i < 40 && !imported; i++) {
    sh('sleep', ['15']);
    const inCount = parseInt(sql(`SELECT COUNT(*) FROM faxes WHERE direction='IN'`), 10) || 0;
    if (inCount > before) {
      const unclaimed = parseInt(sql(`SELECT COUNT(*) FROM providerLabRouting WHERE provider_no='0' AND status='N'`), 10) || 0;
      console.log(`INBOUND OK: ${inCount - before} new inbound fax(es) imported; ${unclaimed} in the UNCLAIMED inbox ready to sign`);
      imported = true;
    }
  }
  if (!imported) throw new Error('no inbound fax imported within the timeout (SRFax loopback delivery can be slow)');
  console.log('BACKBONE LOOPBACK: PASS');
}

main().catch((e) => { console.error('BACKBONE LOOPBACK: FAIL —', e.message); process.exit(1); });
