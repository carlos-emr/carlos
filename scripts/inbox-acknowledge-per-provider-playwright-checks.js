#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Acknowledging an HL7 lab that two providers hold is one provider's review, not the lab's (coverage plan:
 * inbox-acknowledge-per-provider).
 *
 * WHY THIS CHECK EXISTS. A lab routed to two providers gives each a providerLabRouting row of their own, and
 * each must review it: the acknowledgement writes the reviewing provider's row only (oscarMDS/UpdateStatus
 * takes the provider from the session, never from the form), the result leaves that provider's Inbox only,
 * and the lab page shows every provider's review with its own status, time and comment. lab-acknowledge drives
 * the acknowledgement for one provider; legacy-lab-display, inbox-file-forward and lab-forwarding-rules assert
 * that acknowledging, filing or forwarding creates no row for anyone else. Nothing had a SECOND provider who
 * already holds the lab as New and checked that the first provider's review leaves them outstanding, that their
 * own review later leaves the first provider's comment alone, or that the comment prompt offers each provider
 * their own comment. A regression in any of these marks a result reviewed for a clinician who never saw it, or
 * copies one clinician's comment into another's review.
 *
 * User path (provider A is the shared test login, provider B a throwaway login):
 *   Schedule > Inbox > list mode, New, filtered to the owned patient > the lab's own link (the lab popup,
 *   lab/CA/ALL/ViewLabDisplay) > Acknowledge > the comment prompt.
 *   B logs in > Schedule > Inbox > the same lab > Acknowledge > the comment prompt.
 *   A > Inbox > Acknowledged > the lab.
 *
 * Asserted, in order, on owned rows only:
 *   1. One synthetic CML lab, uploaded through Inbox > HL7 Lab Upload, is routed to A (the patient's MRP) as New;
 *      B is given a New row for it too. Each provider's Inbox, filtered to the owned patient, lists exactly
 *      that lab. Any other provider's rows (the demo clinic forwards A's labs to another provider by a rule)
 *      are recorded.
 *   2. A opens it: the page lists both providers as Not Acknowledged with no comment. Acknowledge asks for a
 *      comment, offering A's own (empty) one; the answer is posted with status A for this lab, the server reports
 *      one routing row cleared from New, and the popup closes. A's row is now A, with the comment and a time no
 *      older than the step. B's row is unchanged (id, status, comment, time), and so is every other provider's.
 *   3. A's Inbox drops the row in place and a fresh New search no longer lists it; B's Inbox still lists it.
 *   4. B opens it: the page shows A Acknowledged, with a time and A's comment, and B Not Acknowledged with no
 *      comment. B's Acknowledge offers B's own (empty) comment, not A's; B answers with nothing. B's row is now
 *      A with no comment, A's row is exactly as step 2 left it, and every other provider's row is unchanged.
 *   5. B's Inbox no longer lists the lab as New. A's Inbox lists it under Acknowledged, and the page shows both
 *      providers Acknowledged, each with their own comment.
 *
 * Fixtures: the owned FAKE- patient (runWorkflow; its NULL HIN set to the empty HIN the demographic form stores,
 * which the Inbox name search needs, after the lab is matched), a throwaway provider login for B
 * (lib/throwaway-login-fixture.js), and one synthetic CML lab with a unique accession (the archived upload file
 * under LAB_UPLOAD_DOCUMENT_STORE is removed by name). B's New routing row is written directly: how a lab reaches
 * a second provider (Forward, a forwarding rule) is inbox-file-forward's and lab-forwarding-rules' subject.
 * Cleanup removes the lab's rows, the throwaway login and the rows the application wrote for it, and asserts them
 * gone. The audit rows A's acknowledgement and page views write stay, as every check's rows for the test login do.
 * A test login whose lab_ack_comment property skips the comment prompt is reported as a skip: the comment cannot
 * be entered at the acknowledgement there.
 */
const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const { clickOpensPopup } = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { throwawayLoginFixture, bundleMessage } = require('./lib/throwaway-login-fixture');
const { openSecondSession } = require('./lib/concurrency-support');
const { syntheticCmlLab } = require('./lab-upload-playwright-checks');
const { removeOwnedHl7Labs, removeArchiveFiles, archivesNamed } = require('./lab-forwarding-rules-playwright-checks');
const { shownRows } = require('./inboxhub-filters-playwright-checks');
const { uploadLab, openInbox, openSidebar, setMode, search } = require('./inbox-file-forward-playwright-checks');

const NAME = 'inbox-acknowledge-per-provider';
const TIMEOUT = 30000;
const q = h.sqlString;
const ACKNOWLEDGE = 'input[onclick*="ackLab"], button[onclick*="ackLab"]';
const PROMPT = bundleMessage('oscarMDS.segmentDisplay.msgComment', 'Please enter a comment (max. 255 characters)');
const ACKNOWLEDGED = bundleMessage('oscarMDS.segmentDisplay.statusAcknowledged', 'Acknowledged');
const NOT_ACKNOWLEDGED = bundleMessage('oscarMDS.segmentDisplay.statusNotAcknowledged', 'Not Acknowledged');
const STEP = {
  routed: 'the owned lab is New in both providers\' Inboxes',
  firstReview: 'the first provider\'s acknowledgement writes only their own routing row',
  firstInbox: 'the lab leaves the first provider\'s Inbox and stays in the second\'s',
  secondReview: 'the second provider sees the first review and acknowledges without touching it',
  bothReviewed: 'each provider\'s review shows with its own comment',
};

const escapeRegExp = (text) => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

/**
 * The review line the lab page renders for one provider: "<name> : <status> [<time>,] no comment | comment : <text>".
 * The time is ReportStatus's "dd-MMM-yy HH:mm" (English month); the routing row's own time is asserted in SQL.
 */
function reviewLine(name, status, comment) {
  const time = status === ACKNOWLEDGED ? ' \\d{2}-[A-Z][a-z]{2}-\\d{2} \\d{2}:\\d{2},' : '';
  const tail = comment ? `comment : ${escapeRegExp(comment)}` : 'no comment';
  // nosemgrep: javascript.lang.security.audit.detect-non-literal-regexp.detect-non-literal-regexp -- every interpolated value goes through escapeRegExp
  return new RegExp(`^${escapeRegExp(name)} : ${escapeRegExp(status)}${time} ${tail}$`);
}

/**
 * Read the lab page's review line for each provider: the run of the status block up to the <br> that ends the
 * line holding that provider's own comment span (id "<provider_no>_<segment>commentText").
 */
async function reviewLines(popup, segment, providerNos) {
  h.assert(/^[1-9]\d*$/.test(String(segment)) && providerNos.every((no) => /^\d+$/.test(no)),
    'Review line lookup has an unexpected shape');
  const ids = providerNos.map((no) => `${no}_${segment}commentText`);
  return popup.evaluate((spanIds) => spanIds.map((id) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- the ids are built from validated digits
    const spans = document.querySelectorAll(`[id="${id}"]`);
    if (spans.length !== 1) return `<${spans.length} comment spans>`;
    const parts = [];
    // Comment nodes are skipped: the block carries a stray <!--center--> whose text is not on the page.
    for (let node = spans[0]; node && node.nodeName !== 'BR'; node = node.previousSibling) {
      if (node.nodeType !== Node.COMMENT_NODE) parts.unshift(node.textContent);
    }
    return parts.join('').replace(/\s+/g, ' ').trim();
  }), ids);
}

/** Open the lab from an Inbox list row, the way a clinician does, and wait for its acknowledge form. */
async function openLab(inbox, context, recorder, segment, label) {
  const row = inbox.locator(`tr[data-lab-type="HL7"][data-segment-id="${segment}"]`);
  await row.first().waitFor({ state: 'visible', timeout: TIMEOUT });
  const popup = await clickOpensPopup(inbox, row.locator('a[onclick*="reportWindow"]').first(),
    { context, recorder, label, timeout: TIMEOUT });
  await popup.locator(`form#acknowledgeForm_${segment}`).waitFor({ state: 'attached', timeout: TIMEOUT });
  return popup;
}

/**
 * Press the page's Acknowledge, answer the comment prompt with `answer`, and wait for the popup to close.
 * Returns the prompts raised (with the comment each offered) and the UpdateStatus POST (status, params, body).
 */
async function acknowledge(popup, answer) {
  const offered = [];
  const readOffer = (dialog) => { offered.push(dialog.type() === 'prompt' ? dialog.defaultValue() : null); };
  popup.on('dialog', readOffer);
  let response;
  let body = null;
  let dialogs;
  // The popup closes the moment the answer arrives, before its body can be read from the page, so the body is
  // read on the way through: the same request goes to the server and the page gets the server's own answer.
  const passThrough = async (route) => {
    const answered = await route.fetch();
    body = await answered.text();
    await route.fulfill({ response: answered });
  };
  await popup.route('**/oscarMDS/UpdateStatus', passThrough);
  try {
    dialogs = await h.withExpectedDialogs(popup, async () => {
      const closed = popup.waitForEvent('close', { timeout: TIMEOUT });
      closed.catch(() => {});
      const responded = popup.waitForResponse((r) => r.request().method() === 'POST'
        && new URL(r.url()).pathname.endsWith('/oscarMDS/UpdateStatus'), { timeout: TIMEOUT });
      responded.catch(() => {});
      await popup.locator(ACKNOWLEDGE).first().click({ timeout: TIMEOUT });
      response = await responded;
      // The inbox popup closes itself once the acknowledgement is answered; one that stays open hides the outcome.
      await closed;
    }, { promptText: answer });
  } finally {
    popup.off('dialog', readOffer);
    await popup.unroute('**/oscarMDS/UpdateStatus', passThrough).catch(() => {});
  }
  return {
    prompts: dialogs.map((dialog, index) => ({ type: dialog.type, text: dialog.text, offered: offered[index] })),
    status: response.status(),
    params: new URLSearchParams(response.request().postData() || ''),
    body: body === null ? '<no answer read>' : body,
  };
}

async function workflow(s) {
  const { sql, config, recorder, marker, patient, provider } = s;
  if (sql.value(`SELECT COUNT(*) FROM property WHERE provider_no=${q(provider)} AND name='lab_ack_comment'
    AND LOWER(TRIM(value))='yes'`) !== '0') {
    throw new h.SkipCheck('The test login\'s lab_ack_comment property skips the acknowledgement comment prompt');
  }
  const stamp = crypto.randomBytes(4).toString('hex').toUpperCase();
  const second = throwawayLoginFixture({ sql, marker, provider, testUser: config.testUser });
  const workDir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-inbox-ack-'));
  const lab = { labNo: null, accession: `AK${stamp}`, fileName: `inbox-acknowledge-per-provider-${stamp}.hl7` };
  const firstComment = `${marker} first review comment`;

  // A provider's routing row for the lab, identified by id, status, comment and time: "unchanged" means no write
  // touched it at all (the time column updates itself on any UPDATE of the row).
  const rowOf = (who) => JSON.stringify(sql.rows(`SELECT id, status, IFNULL(comment,'<null>'), IFNULL(timestamp,'<null>')
    FROM providerLabRouting WHERE lab_type='HL7' AND lab_no=${lab.labNo} AND provider_no=${q(who)} ORDER BY id`));
  const othersOf = () => JSON.stringify(sql.rows(`SELECT id, provider_no, status, IFNULL(comment,'<null>'),
    IFNULL(timestamp,'<null>') FROM providerLabRouting WHERE lab_type='HL7' AND lab_no=${lab.labNo}
    AND provider_no NOT IN (${q(provider)},${q(second.providerNo)}) ORDER BY id`));
  const statusAndComment = (who) => sql.rows(`SELECT status, IFNULL(comment,'<null>') FROM providerLabRouting
    WHERE lab_type='HL7' AND lab_no=${lab.labNo} AND provider_no=${q(who)} ORDER BY id`);
  const nameOf = (who) => sql.value(`SELECT CONCAT(first_name,' ',last_name) FROM provider WHERE provider_no=${q(who)}`);

  // Cleanup runs last-registered first. Everything below is registered before its first write.
  s.cleanup(() => second.cleanup());
  s.cleanup(() => {
    fs.rmSync(workDir, { recursive: true, force: true });
    const found = sql.rows(`SELECT lab_no FROM hl7TextInfo WHERE accessionNum=${q(lab.accession)}`).map(([id]) => id);
    removeOwnedHl7Labs(sql, [...found, lab.labNo].filter(Boolean));
    // A rejected upload leaves its checksum row and archive without any lab row; the unique name finds both.
    sql.execute(`DELETE FROM fileUploadCheck WHERE filename LIKE ${q(`LabUpload.${lab.fileName}.%`)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM fileUploadCheck WHERE filename LIKE ${q(`LabUpload.${lab.fileName}.%`)}`) === '0',
      'The synthetic lab upload checksum row was not removed');
    removeArchiveFiles(archivesNamed(lab.fileName));
    h.assert(sql.value(`SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum=${q(lab.accession)}`) === '0',
      'The synthetic lab was not removed');
  });

  second.create();
  const secondNo = second.providerNo;
  const names = { first: nameOf(provider), second: nameOf(secondNo) };
  h.assert(names.first && names.second, 'A provider name is missing');
  let firstInbox;   // provider A's Inbox page
  let secondSession; // provider B's session: { context, schedule }
  let secondInbox;  // provider B's Inbox page
  let firstRowAfterReview;
  let othersBefore; // every other provider's rows for the lab, as the upload left them

  await s.step(STEP.routed, async () => {
    const file = path.join(workDir, lab.fileName); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal
    fs.writeFileSync(file, Buffer.from(syntheticCmlLab(lab.accession, marker), 'latin1'));
    const status = await uploadLab(s, file, lab.fileName);
    h.assert(status === 'Uploaded successfully', `The lab upload reported "${status}"`);
    await expectValue(sql, `SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum=${q(lab.accession)}`, '1',
      'The lab did not reach hl7TextInfo');
    lab.labNo = sql.value(`SELECT lab_no FROM hl7TextInfo WHERE accessionNum=${q(lab.accession)}`);
    h.assert(/^[1-9]\d*$/.test(lab.labNo), 'The lab has no lab number');
    h.assert(sql.value(`SELECT demographic_no FROM patientLabRouting WHERE lab_type='HL7' AND lab_no=${lab.labNo}`) === patient,
      'The lab was not matched to the owned patient');
    h.assert(JSON.stringify(statusAndComment(provider).map(([st]) => st)) === '["N"]',
      'The upload did not route the lab to the test provider as New (the patient\'s MRP)');
    h.assert(rowOf(secondNo) === '[]', 'The second provider already holds the lab');
    othersBefore = othersOf();
    sql.execute(`INSERT INTO providerLabRouting (provider_no,lab_no,lab_type,status) VALUES (${q(secondNo)},${lab.labNo},'HL7','N')`);
    h.assert(JSON.stringify(statusAndComment(secondNo)) === '[["N",""]]', 'The second provider\'s New routing row was not written');
    // The harness patient has a NULL HIN; the Inbox name search matches `hin LIKE '%%'`, which NULL never
    // satisfies, so give the owned patient the empty HIN the demographic form itself stores (after matching).
    sql.execute(`UPDATE demographic SET hin='' WHERE demographic_no=${patient} AND last_name=${q(marker)} AND hin IS NULL`);

    firstInbox = await openInbox(s.schedule, s.context, recorder, 'inbox-ack-first');
    secondSession = await openSecondSession(s, { label: 'inbox-ack-second', openMaster: false,
      config: { ...config, testUser: second.username } });
    secondInbox = await openInbox(secondSession.schedule, secondSession.context, recorder, 'inbox-ack-second');
    for (const [who, inbox] of [['test provider', firstInbox], ['second provider', secondInbox]]) {
      await setMode(inbox, 'list');
      await openSidebar(inbox);
      h.assert(await inbox.locator('#specificProvider').isChecked(), `The ${who}'s Inbox does not default to their own results`);
      await inbox.locator('#statusNew').check();
      await inbox.locator('#specificPatients').check();
      await inbox.locator('#inputLastName').fill(marker);
      await search(inbox);
      h.assert(JSON.stringify(await shownRows(inbox)) === JSON.stringify([`HL7:${lab.labNo}`]),
        `The ${who}'s New Inbox does not list exactly the owned lab (${JSON.stringify(await shownRows(inbox))})`);
    }
  });

  await s.step(STEP.firstReview, async () => {
    const stepStart = sql.value('SELECT NOW()');
    const secondBefore = rowOf(secondNo);
    const popup = await openLab(firstInbox, s.context, recorder, lab.labNo, 'inbox-ack-first-lab');
    let sent;
    try {
      h.assert(await popup.locator('input[name="CSRF-TOKEN"]').evaluateAll((inputs) => inputs.some((input) => input.value)),
        'The lab page bootstrapped no CSRF-TOKEN, so its acknowledgement would be refused');
      const [first, secondLine] = await reviewLines(popup, lab.labNo, [provider, secondNo]);
      h.assert(reviewLine(names.first, NOT_ACKNOWLEDGED, '').test(first),
        `Before any review the lab page shows the test provider as "${first}", not Not Acknowledged`);
      h.assert(reviewLine(names.second, NOT_ACKNOWLEDGED, '').test(secondLine),
        `Before any review the lab page shows the second provider as "${secondLine}", not Not Acknowledged`);
      sent = await acknowledge(popup, firstComment);
    } finally {
      await popup.close().catch(() => {});
    }
    h.assert(sent.prompts.length === 1 && sent.prompts[0].type === 'prompt' && sent.prompts[0].text === PROMPT,
      `Acknowledge did not ask once for a comment (${sent.prompts.map((p) => p.type).join(',') || 'no dialog'})`);
    h.assert(sent.prompts[0].offered === '', 'The comment prompt did not offer the test provider\'s own (empty) comment');
    h.assert(sent.status === 200, `UpdateStatus answered HTTP ${sent.status}`);
    h.assert(sent.params.get('segmentID') === lab.labNo && sent.params.get('status') === 'A'
      && sent.params.get('labType') === 'HL7' && sent.params.get('comment') === firstComment,
    'Acknowledge did not post status A with the comment for exactly this lab');
    h.assert(/"clearedCount"\s*:\s*1\b/.test(sent.body),
      `The acknowledgement did not report exactly one routing row cleared from New (${JSON.stringify(sent.body.slice(0, 80))})`);
    await expectValue(sql, `SELECT IFNULL(GROUP_CONCAT(status),'') FROM providerLabRouting WHERE lab_type='HL7'
      AND lab_no=${lab.labNo} AND provider_no=${q(provider)}`, 'A', 'The test provider\'s routing row is not Acknowledged');
    h.assert(JSON.stringify(statusAndComment(provider)) === JSON.stringify([['A', firstComment]]),
      'The test provider\'s routing row does not carry the comment entered at the prompt');
    h.assert(sql.value(`SELECT COUNT(*) FROM providerLabRouting WHERE lab_type='HL7' AND lab_no=${lab.labNo}
      AND provider_no=${q(provider)} AND timestamp >= ${q(stepStart)}`) === '1',
    'The test provider\'s routing row was not stamped with the acknowledgement time');
    h.assert(rowOf(secondNo) === secondBefore, 'The test provider\'s acknowledgement changed the second provider\'s routing row');
    h.assert(othersOf() === othersBefore, 'The test provider\'s acknowledgement changed another provider\'s routing rows');
    firstRowAfterReview = rowOf(provider);
  });

  await s.step(STEP.firstInbox, async () => {
    await firstInbox.locator(`tr[data-lab-type="HL7"][data-segment-id="${lab.labNo}"]`)
      .waitFor({ state: 'detached', timeout: TIMEOUT });
    await search(firstInbox);
    h.assert(JSON.stringify(await shownRows(firstInbox)) === '[]',
      `The test provider's New Inbox still lists the acknowledged lab (${JSON.stringify(await shownRows(firstInbox))})`);
    await search(secondInbox);
    h.assert(JSON.stringify(await shownRows(secondInbox)) === JSON.stringify([`HL7:${lab.labNo}`]),
      `The second provider's New Inbox no longer lists the lab the test provider acknowledged (${JSON.stringify(await shownRows(secondInbox))})`);
  });

  await s.step(STEP.secondReview, async () => {
    const popup = await openLab(secondInbox, secondSession.context, recorder, lab.labNo, 'inbox-ack-second-lab');
    let sent;
    try {
      const [first, secondLine] = await reviewLines(popup, lab.labNo, [provider, secondNo]);
      h.assert(reviewLine(names.first, ACKNOWLEDGED, firstComment).test(first),
        `The second provider's lab page shows the test provider as "${first}", not Acknowledged with their comment`);
      h.assert(reviewLine(names.second, NOT_ACKNOWLEDGED, '').test(secondLine),
        `The second provider's lab page shows the second provider as "${secondLine}", not Not Acknowledged`);
      sent = await acknowledge(popup, '');
    } finally {
      await popup.close().catch(() => {});
    }
    h.assert(sent.prompts.length === 1 && sent.prompts[0].type === 'prompt',
      `Acknowledge did not ask the second provider once for a comment (${sent.prompts.map((p) => p.type).join(',') || 'no dialog'})`);
    h.assert(sent.prompts[0].offered === '',
      sent.prompts[0].offered === firstComment
        ? 'The second provider\'s comment prompt offered the test provider\'s comment as their own'
        : 'The second provider\'s comment prompt did not offer their own (empty) comment');
    h.assert(sent.status === 200 && sent.params.get('segmentID') === lab.labNo && sent.params.get('status') === 'A',
      `The second provider's Acknowledge did not post status A for this lab (HTTP ${sent.status})`);
    await expectValue(sql, `SELECT IFNULL(GROUP_CONCAT(status),'') FROM providerLabRouting WHERE lab_type='HL7'
      AND lab_no=${lab.labNo} AND provider_no=${q(secondNo)}`, 'A', 'The second provider\'s routing row is not Acknowledged');
    h.assert(JSON.stringify(statusAndComment(secondNo)) === '[["A",""]]',
      'The second provider\'s acknowledgement without a comment left a comment on their routing row');
    h.assert(rowOf(provider) === firstRowAfterReview,
      'The second provider\'s acknowledgement changed the test provider\'s routing row (status, comment or time)');
    h.assert(othersOf() === othersBefore, 'The second provider\'s acknowledgement changed another provider\'s routing rows');
  });

  await s.step(STEP.bothReviewed, async () => {
    await search(secondInbox);
    h.assert(JSON.stringify(await shownRows(secondInbox)) === '[]',
      `The second provider's New Inbox still lists the lab they acknowledged (${JSON.stringify(await shownRows(secondInbox))})`);
    await openSidebar(firstInbox);
    await firstInbox.locator('#statusAcknowledged').check();
    await search(firstInbox);
    h.assert(JSON.stringify(await shownRows(firstInbox)) === JSON.stringify([`HL7:${lab.labNo}`]),
      `The test provider's Acknowledged Inbox does not list exactly the lab (${JSON.stringify(await shownRows(firstInbox))})`);
    const popup = await openLab(firstInbox, s.context, recorder, lab.labNo, 'inbox-ack-first-reviewed');
    try {
      const [first, secondLine] = await reviewLines(popup, lab.labNo, [provider, secondNo]);
      h.assert(reviewLine(names.first, ACKNOWLEDGED, firstComment).test(first),
        `After both reviews the lab page shows the test provider as "${first}"`);
      h.assert(reviewLine(names.second, ACKNOWLEDGED, '').test(secondLine),
        `After both reviews the lab page shows the second provider as "${secondLine}"`);
    } finally {
      await popup.close().catch(() => {});
    }
  });
}

if (require.main === module) runWorkflow(NAME, workflow, { openMaster: false });
module.exports = { workflow, STEP };
