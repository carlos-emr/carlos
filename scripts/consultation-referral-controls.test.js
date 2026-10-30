/*
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
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
// Static regressions for findings 322-324 (issues #5272, #5274, #5278), and the safety rules of the fixture
// libraries the referral-form checks share. The live round trips are pinned by consultation-followup-date,
// consultation-multisite-site and consultation-fax-account; these keep the page contracts from regressing in CI,
// which does not run the browser checks.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const root = path.join(__dirname, '..');
const form = fs.readFileSync(path.join(root, 'src/main/webapp/WEB-INF/jsp/encounter/oscarConsultationRequest/ConsultationFormRequest.jsp'), 'utf8');
const cover = fs.readFileSync(path.join(root, 'src/main/webapp/WEB-INF/jsp/fax/CoverPage.jsp'), 'utf8');

test('shouldRefuseAnUnreadableDate_beforeSubmitOrUpdatePostsIt', () => {
  const check = form.slice(form.indexOf('function checkForm(submissionVal, formName)'), form.indexOf('ShowSpin(true);'));
  assert.match(check, /\^\(Submit\|Update\)/, 'the date guard must cover every save button');
  assert.match(check, /submissionVal === 'And Print Preview'/, 'and the unsaved Print Preview, which would print without the date');
  for (const id of ['referalDate', 'appointmentDate', 'followUpDate']) assert.ok(check.includes(`'${id}'`), `the guard skips ${id}`);
  assert.match(check, /validity\.badInput/, 'an unreadable date is what the browser reports as badInput');
  assert.match(check, /return false;/, 'the guard must stop the submit');
  assert.match(form, /msgInvalidDate">\s*<fmt:param value="__FIELD__"\/>/, 'the alert names the refused field');
});

test('shouldSelectTheSavedSite_inTheMultisiteSiteList', () => {
  const select = form.slice(form.indexOf('<select name="siteName" id="siteName"'), form.indexOf('</select>', form.indexOf('id="siteName"')));
  assert.match(select, /te\.equals\(defaultSiteName\)/, 'the option matching the saved or defaulted site must be identified');
  assert.match(select, /selectedSite \? " selected" : ""/, 'that option must render selected');
  assert.match(select, /if \(!savedSiteOffered\)[\s\S]*selected>/, 'a saved site the login is not linked to must stay selected');
});

test('shouldSelectTheChosenSender_byItsFaxNumberOnTheCoverPage', () => {
  const sender = cover.slice(cover.indexOf('<c:set var="chosenSenderFax"'), cover.indexOf('</select>', cover.indexOf('id="senderFaxAccount"')));
  assert.ok(sender, 'the cover page sender selection was not found');
  assert.doesNotMatch(sender, /account\.id eq requestScope\.faxAccount/, 'the posted faxAccount is a fax number, never an account id');
  assert.match(sender, /not empty requestScope\.faxAccount \? requestScope\.faxAccount : param\.letterheadFax/,
    'a posted choice wins; without one the letterhead fax picks, as for the other fax entry points');
  assert.match(sender, /chosenSenderOffered and account\.faxNumber eq chosenSenderFax \? 'selected'/);
  assert.match(sender, /not empty requestScope\.faxAccount and not chosenSenderOffered[\s\S]*<option value="" selected>/,
    'a chosen sender that is no longer offered must leave no other account selected');
});

/** A SQL runner that answers from a script of [pattern, answer] pairs and records every statement. */
function fakeSql(answers = []) {
  const seen = [];
  const answer = query => {
    seen.push(query);
    const hit = answers.find(([pattern]) => pattern.test(query));
    return hit ? hit[1] : '0';
  };
  return { seen, value: answer, rows: query => { const a = answer(query); return Array.isArray(a) ? a : []; }, execute: query => { seen.push(query); } };
}

function fakeSession(sql) {
  const cleanups = [];
  return { sql, marker: 'FAKE-PW0123456789abcdef', patient: '41', provider: '999998', cleanups, cleanup: fn => cleanups.push(fn) };
}

test('shouldRefuseAnUnsafeColumn_whenSeedingAReferral', () => {
  const { ownedReferrals } = require('./lib/consultation-referral-fixture');
  const sql = fakeSql([[/LAST_INSERT_ID/, '7']]);
  const referrals = ownedReferrals(fakeSession(sql));
  assert.throws(() => referrals.seed({ reason: 'x', 'status=1;DROP TABLE demographic;--': '1' }), /Not a column name/);
  assert.throws(() => referrals.seed({ reason: 'x', demographicNo: '99' }), /owned patient/);
  assert.equal(referrals.seed({ reason: "O'Neil", status: '2' }), '7');
  assert.ok(sql.seen.some(query => /'O''Neil'/.test(query)), 'seeded values must be quoted');
});

test('shouldRemoveOnlyTheOwnedReferralRows_inCleanup', () => {
  const { ownedReferrals } = require('./lib/consultation-referral-fixture');
  const sql = fakeSql([[/LAST_INSERT_ID/, '7'], [/SELECT requestId FROM consultationRequests WHERE demographicNo=41/, [['12']]]]);
  const session = fakeSession(sql);
  ownedReferrals(session);
  session.cleanups.forEach(cleanup => cleanup());
  const deletes = sql.seen.filter(query => /DELETE/.test(query)).join('\n');
  assert.match(deletes, /DELETE FROM consultationRequests WHERE requestId=12 AND demographicNo=41/);
  assert.doesNotMatch(deletes, /DELETE FROM consultationRequests WHERE (?!requestId=12 AND demographicNo=41)/,
    'cleanup may delete only the owned patient\'s referrals');
});

test('shouldNeverDeleteAFileOutsideTheDocumentStore_whenRemovingOwnedFaxJobs', () => {
  const store = fs.mkdtempSync(path.join(os.tmpdir(), 'consult-fax-store-'));
  const outside = fs.mkdtempSync(path.join(os.tmpdir(), 'consult-fax-outside-'));
  const victim = path.join(outside, 'victim.pdf');
  fs.writeFileSync(victim, '%PDF-1.4');
  fs.symlinkSync(victim, path.join(store, 'linked.pdf'));
  fs.writeFileSync(path.join(store, 'owned.pdf'), '%PDF-1.4');
  const previous = process.env.CONSULT_FAX_DOCUMENT_STORE;
  process.env.CONSULT_FAX_DOCUMENT_STORE = store;
  delete require.cache[require.resolve('./lib/consultation-fax-fixture')];
  try {
    const { ownedJobs } = require('./lib/consultation-fax-fixture');
    const run = jobs => {
      const sql = fakeSql([[/SELECT id, IFNULL\(filename,''\) FROM faxes/, jobs]]);
      const session = fakeSession(sql);
      ownedJobs(session, ['41']);
      return () => session.cleanups.forEach(cleanup => cleanup());
    };
    run([['5', 'owned.pdf'], ['6', 'linked.pdf']])();
    assert.ok(!fs.existsSync(path.join(store, 'owned.pdf')), 'the owned PDF is removed');
    assert.ok(fs.existsSync(victim), 'a link out of the store is never followed');
    assert.throws(run([['7', '../outside.pdf']]), /unexpected file/);
    assert.throws(() => ownedJobs(fakeSession(fakeSql()), ['41 OR 1=1']), /patient key is invalid/);
  } finally {
    if (previous === undefined) delete process.env.CONSULT_FAX_DOCUMENT_STORE; else process.env.CONSULT_FAX_DOCUMENT_STORE = previous;
    delete require.cache[require.resolve('./lib/consultation-fax-fixture')];
    fs.rmSync(store, { recursive: true, force: true });
    fs.rmSync(outside, { recursive: true, force: true });
  }
});
