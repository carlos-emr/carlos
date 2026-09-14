// Inbound fax inbox lifecycle test: takes an inbound fax that the scheduler
// has imported and left UNCLAIMED (see backbone-loopback.js, which must run
// first to produce one), then drives the real provider workflow through the
// live server actions and asserts each database transition:
//
//   1. redirected to the inbox   — providerLabRouting(DOC) provider_no=0 status=N
//   2. attached to a patient     — documentUpdate(demog) -> ctl_document module_id
//                                  set to the demographic + a chart note created
//   3. attached to a provider    — documentUpdate(flagproviders) -> a
//                                  providerLabRouting row for that provider
//   4. provider interacts (files)— fileLabAjax(DOC) -> that row's status -> 'F'
//
// The two server actions (documentManager/ManageDocument!documentUpdate and
// oscarMDS/FileLabs!fileLabAjax) are the exact endpoints the inbox UI calls;
// they are posted from within an authenticated page so the session cookies and
// the scraped CSRFGuard token travel with them. Assertions are made against the
// database via the mariadb CLI, matching backbone-loopback.js.
//
// Talks to a live deployment (no SRFax traffic of its own). Never run in CI.
// Prereqs: fixtures.sql loaded; backbone-loopback.js has imported >=1 inbound
// fax; the login user has _edoc and _lab write privileges (carlosdoc does).
'use strict';
const { execFileSync } = require('child_process');
const { launch, login, cfg } = require('./lib');

const MARIADB = (process.env.MARIADB || 'mariadb').split(/\s+/);
const DB = process.env.CARLOS_DB_NAME || 'carlos';
function sql(q) {
  const [cmd, ...pre] = MARIADB;
  return execFileSync(cmd, [...pre, '-N', DB, '-e', q], { encoding: 'utf8' }).trim();
}
const must = (cond, msg) => { if (!cond) throw new Error(msg); };
// Escape a value for embedding inside a single-quoted SQL string literal.
const sqlLit = (v) => String(v).replace(/'/g, "''");

async function main() {
  const c = cfg({ srfax: false });

  // Resolve the fixture patient and the login's provider from the database so
  // nothing is hardcoded to a particular install.
  const demo = sql(`SELECT demographic_no FROM demographic WHERE last_name='Loopback' AND first_name='Faxtest' ORDER BY demographic_no LIMIT 1`);
  must(demo, 'fixture demographic Loopback/Faxtest not found — load fixtures.sql first');
  const provider = sql(`SELECT provider_no FROM security WHERE user_name='${sqlLit(c.user)}' ORDER BY security_no LIMIT 1`);
  must(provider, `no provider mapped to login ${c.user} in the security table`);

  // Find an imported inbound fax still sitting UNCLAIMED in the inbox.
  const doc = sql(`SELECT r.lab_no FROM providerLabRouting r JOIN document d ON d.document_no=r.lab_no `
    + `WHERE r.lab_type='DOC' AND r.provider_no='0' AND r.status='N' AND d.doctype='Received Fax' `
    + `ORDER BY r.lab_no DESC LIMIT 1`);
  must(doc, 'no UNCLAIMED inbound fax in the inbox — run backbone-loopback.js first');
  console.log(`unclaimed inbound fax document=${doc}; target patient=${demo} provider=${provider}`);
  console.log('STEP 1 redirected-to-inbox: PASS (providerLabRouting DOC provider_no=0 status=N)');

  const b = await launch();
  const ctx = await b.newContext({ ignoreHTTPSErrors: true });
  try {
    const p = await login(ctx, c);
    // Land on an app page so a CSRFGuard token is present to scrape.
    // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- validated base + constant path
    await p.goto(c.base + '/web/inboxhub/Inboxhub', { waitUntil: 'domcontentloaded' });
    await p.waitForTimeout(800);
    const { postForm } = require('./lib');

    // STEP 2 + 3: file the document to the patient and route it to the provider.
    const r1 = await postForm(p, c.base + '/documentManager/ManageDocument', {
      method: 'documentUpdate', documentId: doc, doc_no: doc, docType: 'Received Fax',
      documentDescription: 'E2E inbound fax', observationDate: '', demog: demo, flagproviders: provider,
    });
    must(r1.status === 200, `documentUpdate returned HTTP ${r1.status} (expected 200)`);

    const linked = sql(`SELECT COUNT(*) FROM ctl_document WHERE document_no=${doc} AND module='demographic' AND module_id=${demo}`);
    must(parseInt(linked, 10) > 0, `document ${doc} was not linked to demographic ${demo}`);
    const noted = sql(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no='${demo}' AND note LIKE '%E2E inbound fax%'`);
    must(parseInt(noted, 10) > 0, 'no chart note was created on the patient for the filed fax');
    console.log(`STEP 2 attached-to-patient: PASS (ctl_document -> demographic ${demo}, chart note created)`);

    const routed = sql(`SELECT COUNT(*) FROM providerLabRouting WHERE lab_no=${doc} AND lab_type='DOC' AND provider_no='${provider}'`);
    must(parseInt(routed, 10) > 0, `document ${doc} was not routed to provider ${provider}`);
    console.log(`STEP 3 attached-to-provider: PASS (providerLabRouting -> provider ${provider})`);

    // STEP 4: the provider files (acknowledges) the item in their inbox.
    const r2 = await postForm(p, c.base + '/oscarMDS/FileLabs', {
      method: 'fileLabAjax', flaggedLabId: doc, labType: 'DOC',
    });
    must(r2.status === 200, `fileLabAjax returned HTTP ${r2.status} (expected 200)`);
    const filed = sql(`SELECT status FROM providerLabRouting WHERE lab_no=${doc} AND lab_type='DOC' AND provider_no='${provider}' ORDER BY id DESC LIMIT 1`);
    must(filed === 'F', `provider routing status is '${filed}', expected 'F' (filed)`);
    console.log(`STEP 4 provider-files-in-inbox: PASS (routing status -> F)`);

    console.log('INBOX LIFECYCLE: PASS');
  } finally {
    await b.close();
  }
}

main().catch((e) => { console.error('INBOX LIFECYCLE: FAIL —', e.message); process.exit(1); });
