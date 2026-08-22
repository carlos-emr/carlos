// Prescription DrugRef lookup test: proves the DrugRef2 drug-reference
// integration that the prescription module depends on is live and answering
// real queries — the lookup a provider does before writing (and faxing) a
// prescription. It drives the real Rx search action (rx/searchDrug ->
// RxSearchDrug2Action -> RxDrugRef.list_drug*), which queries DrugRef over
// XML-RPC, and asserts:
//
//   1. a common drug name returns real reference results (generic/brand rows);
//   2. a nonsense string returns no results — so we know the lookup is a real
//      query against the dataset, not a stub that always "matches";
//   3. (optional) a second common drug also resolves, guarding against a
//      single cached/fluke hit.
//
// Scope note: this covers the DrugRef lookup that gates prescribing. The fax
// TRANSMISSION of the resulting prescription funnels into the same outbound
// backbone (WAITING faxes row -> scheduler -> SRFax Queue_Fax -> SENT) that
// backbone-loopback.js already proves end to end; it is not re-driven here
// because the full write-script -> PDF -> fax-to-pharmacy UI is a brittle
// multi-step flow unsuitable for a stable automated check.
//
// Talks to a live deployment; sends no faxes, needs no SRFax credentials.
// Never run in CI. Prereqs: fixtures.sql loaded (provides the patient), the
// carlos-emr-drugref package installed and its dataset loaded.
'use strict';
const { execFileSync } = require('child_process');
const { launch, login, cfg } = require('./lib');

const MARIADB = (process.env.MARIADB || 'mariadb').split(/\s+/);
const DB = process.env.CARLOS_DB_NAME || 'oscar';
function sql(q) {
  const [cmd, ...pre] = MARIADB;
  return execFileSync(cmd, [...pre, '-N', DB, '-e', q], { encoding: 'utf8' }).trim();
}
const must = (cond, msg) => { if (!cond) throw new Error(msg); };

// Run one rx/searchDrug query in the authenticated Rx session and report how
// many reference rows came back and whether the term appears in them.
async function searchDrug(p, base, demo, term) {
  // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- validated base + constant path, term is URL-encoded
  await p.goto(base + '/rx/searchDrug?demographicNo=' + demo + '&searchString=' + encodeURIComponent(term),
    { waitUntil: 'domcontentloaded' });
  await p.waitForTimeout(600);
  return p.evaluate((t) => {
    const text = document.body.innerText || '';
    // Result rows in ChooseDrug.jsp carry an "(Info)" affordance per drug.
    const hits = [...document.querySelectorAll('a,tr,li')]
      .map((e) => (e.textContent || '').trim())
      .filter((s) => new RegExp(t, 'i').test(s) && s.length < 120);
    return { count: hits.length, sample: hits.slice(0, 3), failedOrEmpty: /unavailable|no results|not found|failed/i.test(text) || text.length < 400 };
  }, term);
}

async function main() {
  const c = cfg({ srfax: false });
  const demo = sql(`SELECT demographic_no FROM demographic WHERE last_name='Loopback' AND first_name='Faxtest' ORDER BY demographic_no LIMIT 1`);
  must(demo, 'fixture demographic Loopback/Faxtest not found — load fixtures.sql first');

  const b = await launch();
  const ctx = await b.newContext({ ignoreHTTPSErrors: true });
  try {
    const p = await login(ctx, c);
    // Open the patient's Rx session so drug search has a demographic context.
    // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- validated base + constant path
    await p.goto(c.base + '/rx/choosePatient?demographicNo=' + demo, { waitUntil: 'domcontentloaded' }).catch(() => {});
    await p.waitForTimeout(400);

    // 1. positive lookup
    const pos = await searchDrug(p, c.base, demo, 'amoxicillin');
    must(pos.count > 0 && !pos.failedOrEmpty, `DrugRef returned no results for "amoxicillin" (count=${pos.count}) — is carlos-emr-drugref up?`);
    console.log(`STEP 1 drugref-positive: PASS (amoxicillin -> ${pos.count} row(s), e.g. ${JSON.stringify(pos.sample[0] || '')})`);

    // 2. negative lookup
    const neg = await searchDrug(p, c.base, demo, 'zzzznotarealdrugxyz');
    must(neg.count === 0, `DrugRef returned ${neg.count} result(s) for a nonsense term — lookup is not a real query`);
    console.log('STEP 2 drugref-negative: PASS (nonsense term -> no results)');

    // 3. second positive, to guard against a single fluke/cached hit
    const pos2 = await searchDrug(p, c.base, demo, 'metformin');
    must(pos2.count > 0 && !pos2.failedOrEmpty, `DrugRef returned no results for "metformin" (count=${pos2.count})`);
    console.log(`STEP 3 drugref-second-drug: PASS (metformin -> ${pos2.count} row(s))`);

    console.log('PRESCRIPTION DRUGREF: PASS');
  } finally {
    await b.close();
  }
}

main().catch((e) => { console.error('PRESCRIPTION DRUGREF: FAIL —', e.message); process.exit(1); });
