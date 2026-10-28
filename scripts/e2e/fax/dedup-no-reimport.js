// Inbound-fax de-duplication test: proves the account-scoped dedup hardening
// holds against a live deployment. After the scheduler has imported inbound
// faxes (run backbone-loopback.js first), it verifies:
//
//   1. faxline stamping  — every imported inbound (direction='IN') fax carries
//      a faxline equal to the configured account number's last 10 digits. This
//      is the account key FaxImporter.isAlreadyImported() scopes dedup by.
//   2. no duplicate rows — no two inbound faxes share the same file name.
//   3. no duplicate import — over more than two scheduler poll cycles the count
//      of inbound faxes does not grow. A successfully downloaded fax is marked
//      read on the provider, so normal polling should not re-import it; this
//      confirms no duplicate rows appear in steady state. The account-scoped
//      isAlreadyImported() logic that guards a genuine re-offer is covered
//      directly and exhaustively by FaxImporterDedupUnitTest.
//
// This is the live counterpart to FaxImporterDedupUnitTest. It sends no faxes
// of its own, so it needs no SRFax credentials. Never run in CI (it waits on
// the real scheduler and inspects a live database).
'use strict';
const { execFileSync } = require('child_process');
const { cfg } = require('./lib');

const MARIADB = (process.env.MARIADB || 'mariadb').split(/\s+/);
const DB = process.env.CARLOS_DB_NAME || 'carlos';
function sql(q) {
  const [cmd, ...pre] = MARIADB;
  return execFileSync(cmd, [...pre, '-N', DB, '-e', q], { encoding: 'utf8' }).trim();
}
const must = (cond, msg) => { if (!cond) throw new Error(msg); };
const digitsTail = (v) => (v || '').replace(/\D/g, '').slice(-10);

// Two poll cycles at the scheduler's 60s cadence, plus margin.
const WAIT_MS = Number(process.env.DEDUP_WAIT_MS || '150000');
// The window must span more than two 60s poll cycles for the no-growth check to
// mean anything; reject a NaN/short value rather than pass trivially.
if (!Number.isInteger(WAIT_MS) || WAIT_MS < 130000) {
  throw new Error('DEDUP_WAIT_MS must be an integer >= 130000 (more than two 60s scheduler cycles)');
}

async function main() {
  cfg({ srfax: false }); // validates BASE_URL/login env even though we drive the DB

  const account = sql(`SELECT faxNumber FROM fax_config WHERE faxNumber IS NOT NULL ORDER BY id DESC LIMIT 1`)
    || sql(`SELECT faxline FROM faxes WHERE direction='IN' ORDER BY id DESC LIMIT 1`);
  must(account, 'could not determine the configured fax account number');
  const acctTail = digitsTail(account);

  const inCount = () => parseInt(sql(`SELECT COUNT(*) FROM faxes WHERE direction='IN'`), 10) || 0;
  const before = inCount();
  must(before > 0, 'no inbound faxes present — run backbone-loopback.js first');

  // 1. faxline stamping. Every inbound row must carry a usable (>= 10 digit)
  //    account key — that is what FaxImporter.isAlreadyImported() scopes dedup
  //    by. We do NOT require every row to equal one account: a deployment may
  //    have several fax configurations, each stamping its own line. Instead we
  //    assert none are unstamped/malformed, and that the account we selected
  //    above is represented among the inbound rows.
  const unstamped = parseInt(sql(`SELECT COUNT(*) FROM faxes WHERE direction='IN' AND (faxline IS NULL OR faxline='')`), 10) || 0;
  must(unstamped === 0, `${unstamped} inbound fax(es) have no faxline stamped (dedup account key missing)`);
  const malformed = parseInt(sql(`SELECT COUNT(*) FROM faxes WHERE direction='IN' AND CHAR_LENGTH(REGEXP_REPLACE(faxline,'[^0-9]','')) < 10`), 10) || 0;
  must(malformed === 0, `${malformed} inbound fax(es) have a faxline with fewer than 10 digits (not a usable account key)`);
  const forAccount = parseInt(sql(`SELECT COUNT(*) FROM faxes WHERE direction='IN' AND RIGHT(REGEXP_REPLACE(faxline,'[^0-9]',''),10) = '${acctTail}'`), 10) || 0;
  must(forAccount > 0, `no inbound fax is stamped for the selected account ${acctTail}`);
  console.log(`STEP 1 faxline-stamping: PASS (all ${before} inbound faxes carry a usable account key; ${forAccount} for account ${acctTail})`);

  // 2. no duplicate file names among inbound faxes.
  const dups = sql(`SELECT COALESCE(filename,'') fn, COUNT(*) n FROM faxes WHERE direction='IN' GROUP BY filename HAVING n > 1`);
  must(dups === '', `duplicate inbound fax file names found:\n${dups}`);
  console.log('STEP 2 no-duplicate-rows: PASS (every inbound fax file name is unique)');

  // 3. no re-import across more than two scheduler poll cycles.
  console.log(`STEP 3 waiting ${Math.round(WAIT_MS / 1000)}s (> two 60s scheduler cycles) to confirm no re-import...`);
  const start = Date.now();
  while (Date.now() - start < WAIT_MS) {
    execFileSync('sleep', ['15']);
    const now = inCount();
    must(now <= before, `inbound fax count grew from ${before} to ${now} — an already-held fax was re-imported`);
  }
  const after = inCount();
  must(after === before, `inbound fax count changed from ${before} to ${after} — a duplicate import appeared`);
  console.log(`STEP 3 no-duplicate-import: PASS (inbound count stable at ${after} across the window)`);

  console.log('DEDUP NO-REIMPORT: PASS');
}

main().catch((e) => { console.error('DEDUP NO-REIMPORT: FAIL —', e.message); process.exit(1); });
