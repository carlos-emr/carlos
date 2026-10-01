#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Document inbox routing honours forwarding-rule type scoping (issue #4120).
 *
 * Each forwarding rule (incomingLabRules) can be limited to HL7 labs, documents (DOC) or HRM
 * reports through incomingLabRulesType; a rule with no type rows applies to all three. The lab
 * and HRM routing paths always honoured that. The document inbox path
 * (ProviderInboxRoutingDaoImpl.addToProviderInboxStrict) followed EVERY rule, so a document sent
 * to a provider who forwards only lab results also landed in the forwarding recipient's inbox,
 * and a rule's "file" status filed documents it was never meant for.
 *
 * The check drives Inbox > Upload documents (documentManager/ViewHtml5AddDocuments, which posts
 * to addEditDocument?method=html5MultiUpload and routes through addToProviderInbox) the way an
 * operator does: pick "Send to Provider", choose a PDF, press Upload File. Two uploads:
 *
 *   1. The owner forwards to P1 for HL7 only and to P2 for HRM only, filing ("F") on the HRM
 *      rule. The document reaches the owner alone, as a NEW (unfiled) item.
 *   2. The owner forwards documents to P1; P1 forwards everything (a legacy rule with no type
 *      rows) to P2; P2 forwards HL7 only to P3 and documents back to the owner (a cycle); an
 *      archived document rule from the owner to P3 files. The document reaches the owner, P1 and
 *      P2 -- once each -- and neither P3 nor the archived rule's "file" status applies.
 *
 * FIXTURES. Four synthetic providers ("Fwdscope" surname) with provider numbers unused by any
 * provider or forwarding rule, created and removed by this check, plus their rules. Nothing else
 * in the database is read or written: assertions are confined to this run's uploads, found by
 * the run's unique file name.
 *
 * CLEANUP (pass or fail): this run's document rows and their routing, the fixture rules and the
 * fixture providers. The uploaded PDF files are deleted too when FORWARDING_SCOPE_DOCUMENT_STORE
 * names the server's document directory (for example
 * /var/lib/carlos-emr/CarlosDocument/carlos/document on a packaged install); otherwise they stay
 * there as harmless synthetic content. Nothing this check prints carries patient data.
 *
 * Environment: the common contract in lib/playwright-harness.js readConfig() (BASE_URL,
 * TEST_USER, TEST_PASSWORD, TEST_PIN, CHROME_PATH, MYSQL_*). The account must hold _edoc write.
 * Run it against a disposable database only.
 */

'use strict';

const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {
  appUrl,
  assert,
  assertNotErrorPage,
  createRecorder,
  createSqlRunner,
  gotoApp,
  launchBrowser,
  login,
  newContext,
  readConfig,
  runCheck,
  sqlString,
  wirePage,
} = require('./lib/playwright-harness');

const SURNAME = 'Fwdscope';

/** A real one-page PDF: the uploader filters on the extension and the action counts pages. */
function writePdf(dir, name) {
  const pdf = Buffer.from(
    '%PDF-1.4\n'
    + '1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n'
    + '2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n'
    + '3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\n'
    + 'trailer<</Root 1 0 R>>\n'
    + '%%EOF\n',
    'latin1',
  );
  assert(/^[a-z0-9-]+\.pdf$/.test(name), 'Probe file name must be a fixed safe pattern');
  // dir comes from fs.mkdtempSync and name is checked above; no request data reaches this join.
  const file = path.join(dir, name); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal
  fs.writeFileSync(file, pdf);
  return file;
}

/** Four provider numbers that no provider, forwarding rule or inbox route uses yet. */
function freeProviderNumbers(sql) {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    const base = 970000 + crypto.randomInt(0, 2990) * 10;
    const numbers = [0, 1, 2, 3].map((offset) => String(base + offset));
    const list = numbers.map(sqlString).join(',');
    const used = sql.value(`SELECT
        (SELECT COUNT(*) FROM provider WHERE provider_no IN (${list}))
      + (SELECT COUNT(*) FROM incomingLabRules WHERE provider_no IN (${list}) OR frwdProvider_no IN (${list}))
      + (SELECT COUNT(*) FROM providerLabRouting WHERE provider_no IN (${list}))`);
    if (used === '0') return numbers;
  }
  throw new Error('Could not find four unused provider numbers for the forwarding fixture');
}

function createFixture(sql) {
  const [owner, p1, p2, p3] = freeProviderNumbers(sql);
  const fixture = { owner, p1, p2, p3, all: [owner, p1, p2, p3] };
  // One statement, so a failed insert leaves no provider behind: each sql call is its own client,
  // and cleanup only learns the fixture's numbers once this returns.
  const values = fixture.all.map((providerNo, index) => `(${sqlString(providerNo)}, ${sqlString(SURNAME)},
    ${sqlString(['Owner', 'One', 'Two', 'Three'][index])}, 'doctor', '', 'F', '1', NOW())`).join(',');
  sql.execute(`INSERT INTO provider (provider_no, last_name, first_name, provider_type, specialty, sex, status, lastUpdateDate)
    VALUES ${values}`);
  return fixture;
}

/** Replaces the fixture's rules: rows are [from, to, status, archive, types[]]. */
function setRules(sql, fixture, rules) {
  deleteRules(sql, fixture);
  for (const [from, to, status, archive, types] of rules) {
    sql.execute(`INSERT INTO incomingLabRules (provider_no, frwdProvider_no, status, archive)
      VALUES (${sqlString(from)}, ${sqlString(to)}, ${sqlString(status)}, ${sqlString(archive)})`);
    // Every statement runs in its own client, so LAST_INSERT_ID() is not available: read the id
    // back. (from, to) is unique within the fixture's rules.
    const ruleId = sql.value(`SELECT MAX(id) FROM incomingLabRules
      WHERE provider_no=${sqlString(from)} AND frwdProvider_no=${sqlString(to)}`);
    assert(/^\d+$/.test(ruleId), 'Fixture rule was not stored');
    for (const type of types) {
      sql.execute(`INSERT INTO incomingLabRulesType (forward_rule_id, type) VALUES (${ruleId}, ${sqlString(type)})`);
    }
  }
}

function deleteRules(sql, fixture) {
  const list = fixture.all.map(sqlString).join(',');
  sql.execute(`DELETE t FROM incomingLabRulesType t JOIN incomingLabRules r ON r.id=t.forward_rule_id
    WHERE r.provider_no IN (${list})`);
  sql.execute(`DELETE FROM incomingLabRules WHERE provider_no IN (${list})`);
}

/** Uploads one PDF through the HTML5 uploader with the owner as "Send to Provider". */
async function upload(context, config, recorder, fixture, file, label) {
  const uploadPath = new URL(appUrl(config.baseUrl, '/documentManager/addEditDocument')).pathname;
  const page = await context.newPage();
  wirePage(page, label, recorder);
  try {
    await gotoApp(page, config.baseUrl, '/documentManager/ViewHtml5AddDocuments');
    await assertNotErrorPage(page, 'document upload page');
    await page.waitForLoadState('load', { timeout: 30000 });
    const drop = page.locator('#providerDrop');
    await drop.waitFor({ state: 'visible', timeout: 20000 });
    await drop.selectOption(fixture.owner);
    await page.locator('input[type="file"][name="filedata"]').setInputFiles(file);
    const [response] = await Promise.all([
      page.waitForResponse((r) => r.request().method() === 'POST'
        && new URL(r.url()).pathname === uploadPath, { timeout: 60000 }),
      page.locator('#noswfuploadSubmit').click(),
    ]);
    const posted = new URL(response.url()).searchParams;
    assert(posted.get('providers') === fixture.owner,
      `The upload was not sent to the selected provider (providers=${posted.get('providers')})`);
    assert(response.status() < 400, `Upload returned HTTP ${response.status()}`);
    assert(!response.headers().oscar_error, 'Upload was refused with an oscar_error header');
  } finally {
    await page.close().catch(() => {});
  }
}

function uploadedDocument(sql, name) {
  const ids = sql.rows(`SELECT document_no FROM document WHERE docfilename LIKE ${sqlString(`%${name}`)}`);
  assert(ids.length === 1, `Expected exactly one stored document for this upload, found ${ids.length}`);
  return ids[0][0];
}

/** {provider_no: status} for the document's inbox rows; duplicates fail the check. */
function inbox(sql, documentNo) {
  const rows = sql.rows(`SELECT provider_no, status FROM providerLabRouting
    WHERE lab_type='DOC' AND lab_no=${Number(documentNo)} ORDER BY provider_no`);
  const result = {};
  for (const [providerNo, status] of rows) {
    assert(!(providerNo in result), `Provider ${providerNo} has more than one inbox row for the document`);
    result[providerNo] = status;
  }
  return result;
}

function sameRouting(actual, expected, message) {
  const a = JSON.stringify(actual);
  const e = JSON.stringify(expected);
  assert(a === e, `${message}: expected ${e}, got ${a}`);
}

/**
 * Deletes this run's stored PDFs when FORWARDING_SCOPE_DOCUMENT_STORE names the server's mounted
 * document directory; without it the files are retained and reported. Only plain file names that
 * end in one of this run's unique upload names, resolving directly inside the store, are removed.
 */
function removeOwnedFiles(storedNames, names) {
  const store = process.env.FORWARDING_SCOPE_DOCUMENT_STORE;
  if (!store) {
    if (storedNames.length) {
      console.warn('Fixture PDFs retained: set FORWARDING_SCOPE_DOCUMENT_STORE for complete local teardown');
    }
    return;
  }
  const root = fs.realpathSync(store);
  for (const stored of storedNames) {
    assert(stored && path.basename(stored) === stored && names.some((name) => stored.endsWith(name)),
      'Refusing to remove a document file this run did not upload');
    // root comes from realpathSync and stored is a checked basename owned by this run.
    const file = path.join(root, stored); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal
    assert(path.dirname(file) === root, 'Document file resolved outside the document store');
    fs.rmSync(file, { force: true });
  }
}

/** Removes this run's documents and routing, then the fixture rules and providers. */
function cleanupFixture(sql, fixture, names) {
  const patterns = names.map((name) => `docfilename LIKE ${sqlString(`%${name}`)}`).join(' OR ');
  const rows = sql.rows(`SELECT document_no, docfilename FROM document WHERE ${patterns}`);
  const ids = rows.map(([id]) => Number(id));
  if (ids.length) {
    assert(ids.every((id) => Number.isInteger(id) && id > 0), 'Fixture cleanup returned an invalid document id');
    removeOwnedFiles(rows.map(([, file]) => file), names);
    const list = ids.join(',');
    sql.execute(`DELETE FROM document_storage WHERE documentNo IN (${list})`);
    sql.execute(`DELETE FROM providerLabRouting WHERE lab_type='DOC' AND lab_no IN (${list})`);
    sql.execute(`DELETE FROM queue_document_link WHERE document_id IN (${list})`);
    sql.execute(`DELETE FROM ctl_document WHERE document_no IN (${list})`);
    sql.execute(`DELETE FROM document WHERE document_no IN (${list})`);
  }
  if (fixture) {
    deleteRules(sql, fixture);
    const list = fixture.all.map(sqlString).join(',');
    sql.execute(`DELETE FROM provider WHERE provider_no IN (${list}) AND last_name=${sqlString(SURNAME)}`);
  }
}

async function main() {
  const config = readConfig({ require: ['MYSQL_PASSWORD'] });
  const sql = createSqlRunner(config.mysql);
  const stamp = `${Date.now()}-${crypto.randomInt(1000, 9999)}`;
  const names = [`fwdscope-${stamp}-a.pdf`, `fwdscope-${stamp}-b.pdf`];
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'fwdscope-'));
  let fixture = null;
  let browser = null;
  try {
    fixture = createFixture(sql);
    const { owner, p1, p2, p3 } = fixture;
    browser = await launchBrowser(config);
    const context = await newContext(browser, config);
    const recorder = createRecorder();
    await login(context, config, recorder);

    // 1. Rules for other result types neither forward nor file the document.
    setRules(sql, fixture, [
      [owner, p1, 'N', '0', ['HL7']],
      [owner, p2, 'F', '0', ['HRM']],
    ]);
    await upload(context, config, recorder, fixture, writePdf(dir, names[0]), 'upload-type-scoped');
    sameRouting(inbox(sql, uploadedDocument(sql, names[0])), { [owner]: 'N' },
      'A document followed a forwarding rule scoped to HL7 or HRM');
    console.log('PASS HL7-only and HRM-only rules neither forward nor file a document');

    // 2. Document and untyped rules chain; HL7-only, archived and cyclic rules do not extend it.
    setRules(sql, fixture, [
      [owner, p1, 'N', '0', ['DOC']],
      [p1, p2, 'N', '0', []],
      [p2, p3, 'N', '0', ['HL7']],
      [p2, owner, 'N', '0', ['DOC']],
      [owner, p3, 'F', '1', ['DOC']],
    ]);
    await upload(context, config, recorder, fixture, writePdf(dir, names[1]), 'upload-chain');
    sameRouting(inbox(sql, uploadedDocument(sql, names[1])),
      Object.fromEntries([[owner, 'N'], [p1, 'N'], [p2, 'N']].sort()),
      'Document forwarding chain did not follow exactly the DOC and untyped rules');
    console.log('PASS document and untyped rules chain; HL7-only, archived and cyclic rules stop it');
  } finally {
    if (browser) await browser.close().catch(() => {});
    fs.rmSync(dir, { recursive: true, force: true });
    try {
      cleanupFixture(sql, fixture, names);
    } finally {
      sql.dispose();
    }
  }
}

if (require.main === module) {
  runCheck({ name: 'inbox-document-forwarding-scope', run: main });
}
