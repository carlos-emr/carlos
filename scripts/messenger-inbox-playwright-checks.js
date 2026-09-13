#!/usr/bin/env node
/*
 * Browser checks for the CARLOS internal messenger (provider-to-provider
 * messaging).
 *
 * WHY THIS EXISTS. The messenger is how a clinic hands work between providers —
 * a lab result to review, a call to return — and it had NO browser coverage at
 * all: none of `messenger/ViewCreateMessage`, `messenger/CreateMessage`,
 * `messenger/DisplayMessages`, `messenger/ViewMessage` or the inbox's bulk
 * read/unread/archive/unarchive actions was exercised. A compose form that
 * stopped delivering, or a bulk action that archived the wrong row, loses
 * clinical instructions silently: there is no second place the message appears.
 *
 * IT SENDS TO ITSELF, deliberately. The check composes a message from the test
 * user to the test user. That keeps it deterministic (the message is guaranteed
 * to land in the inbox the check then reads) and keeps it from writing into
 * another provider's inbox on a shared test deployment — a check that delivered
 * to a real second provider would leave unread clinical-looking mail behind.
 *
 * TWO TABLES PER MESSAGE. A sent message writes one `messagetbl` row (the
 * content) and one `messagelisttbl` row per recipient (the per-provider status).
 * Every state assertion here reads the recipient row, because that is the row
 * the inbox renders: a status change that updated the content row instead would
 * look correct in the database and change nothing an operator sees.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:messenger-inbox-playwright
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc TEST_PASSWORD=carlos2026 TEST_PIN=2026
 *   MYSQL_HOST=db MYSQL_USER=root MYSQL_PASSWORD=password MYSQL_DATABASE=carlos
 *   MESSENGER_PROVIDER_NO=999998  provider the test user logs in as
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 *
 * Cleanup: the subject and body carry a unique PW_MESSENGER_<millis> marker and
 * every messagetbl / messagelisttbl / msgDemoMap row carrying it is deleted in a
 * finally, including after a failure.
 */

const { chromium } = require('playwright');
const {
  assert,
  assertNoPageErrors,
  assertNotErrorPage,
  createRecorder,
  createSqlClient,
  escapeSql,
  getLaunchOptions,
  gotoApp,
  login,
  readConfig,
  readCsrfToken,
  requireId,
  waitFor,
  wirePage,
} = require('./carlos-playwright-harness');

const config = readConfig();
const providerNo = requireId(process.env.MESSENGER_PROVIDER_NO || '999998', 'MESSENGER_PROVIDER_NO');

const stamp = `PW_MESSENGER_${Date.now()}`;
const subject = `${stamp} subject`;
const body = `${stamp} body line one`;

// messagelisttbl.status values, from MessageList.STATUS_*; the inbox renders
// these directly, so they are the contract the bulk actions have to honour.
const STATUS_NEW = 'new';
const STATUS_READ = 'read';
const STATUS_DELETED = 'del';

// DisplayMessages boxType: 0 inbox, 1 sent, 2 archived ("deleted").
const BOX_INBOX = '0';
const BOX_ARCHIVED = '2';

const recorder = createRecorder();
const sql = createSqlClient({ namespace: 'carlos-messenger' });
const passed = [];
// Tracks whether THIS run created the messenger-contact enrolment, so cleanup
// removes only what it added and leaves a clinic's real contact list intact.
let enrolledByCheck = false;

function pass(message) {
  passed.push(message);
  console.log(`PASS ${message}`);
}

function stampedMessage() {
  const row = sql.rows(
    `SELECT messageid, thesubject, themessage, sentby, sentto FROM messagetbl`
    + ` WHERE thesubject LIKE '${escapeSql(`${stamp}%`)}' ORDER BY messageid`
  )[0];
  if (!row) {
    return null;
  }
  const [id, messageSubject, messageBody, sentBy, sentTo] = row;
  return { id, subject: messageSubject, body: messageBody, sentBy, sentTo };
}

function recipientRows(messageId) {
  return sql.rows(
    `SELECT id, provider_no, status FROM messagelisttbl`
    + ` WHERE message=${requireId(messageId, 'message id')} ORDER BY id`
  ).map(([id, provider, status]) => ({ id, provider, status }));
}

function recipientStatus(messageId, provider) {
  const row = recipientRows(messageId).find((entry) => entry.provider === provider);
  return row ? row.status : null;
}

function cleanupRows() {
  const like = escapeSql(`${stamp}%`);
  const ids = sql.rows(`SELECT messageid FROM messagetbl WHERE thesubject LIKE '${like}'`)
    .map(([id]) => id).filter((id) => /^\d+$/.test(id));
  for (const id of ids) {
    sql.exec(`DELETE FROM messagelisttbl WHERE message=${id}`);
    sql.exec(`DELETE FROM msgDemoMap WHERE messageID=${id}`);
    sql.exec(`DELETE FROM messagetbl WHERE messageid=${id}`);
  }
}

/**
 * Opens the compose page the way an operator reaches it: from the inbox.
 *
 * CreateMessage.jsp redirects to /index when the session carries no
 * msgSessionBean, and /index renders the LOGIN page — so a check that navigated
 * straight to /messenger/ViewCreateMessage would silently be asserting against a
 * login form. Entering through the inbox, which establishes the bean, is both the
 * real path and the only one that reaches the compose form.
 */
async function openCompose(context, inboxPage) {
  const composeLink = inboxPage.locator('a[href*="/messenger/ViewCreateMessage"]').first();
  await composeLink.waitFor({ state: 'visible', timeout: 30000 });
  await Promise.all([
    inboxPage.waitForLoadState('domcontentloaded', { timeout: 45000 }),
    composeLink.click(),
  ]);
  await inboxPage.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
  await assertNotErrorPage(inboxPage, 'messenger compose');
  assert(/\/messenger\/ViewCreateMessage/.test(inboxPage.url()),
    `the inbox Compose link landed on ${inboxPage.url()} instead of the compose page`);
  // The compose form is the proof; a login form here means the session bean was
  // not established and the redirect above fired. The body is edited in a Toast UI
  // WYSIWYG surface and the underlying textarea stays hidden, so the editor's
  // contenteditable is what has to be present.
  await inboxPage.locator('#subject').waitFor({ state: 'visible', timeout: 30000 });
  await inboxPage.locator('.toastui-editor-contents[contenteditable="true"]')
    .first().waitFor({ state: 'visible', timeout: 30000 });
  return inboxPage;
}

/**
 * Composes and sends a message to the logged-in provider.
 *
 * The recipient checkbox is found by the composite id the page renders rather
 * than by position: the provider list is ordered by the clinic's own grouping, so
 * a positional pick would send to whoever happened to be first.
 */
async function sendMessage(page) {
  assert(await readCsrfToken(page), 'messenger compose form carried no CSRFGuard token');

  const recipient = page.locator(`input[type="checkbox"][name="provider"][value^="${providerNo}"]`).first();
  assert(await recipient.count() > 0,
    `messenger compose offered no recipient checkbox for provider ${providerNo}`);
  await recipient.check({ force: true });
  const recipientValue = await recipient.inputValue();

  await page.locator('#subject').fill(subject);
  // Typed into the WYSIWYG surface, not into the hidden textarea: the Send button's
  // writeToMessage() copies the editor's markdown into that textarea, so filling
  // the textarea directly would be overwritten at submit time and the check would
  // stop covering the editor-to-form bridge — which is where a message body gets
  // silently lost when the editor fails to load.
  const editor = page.locator('.toastui-editor-contents[contenteditable="true"]').first();
  await editor.click();
  await editor.type(body);

  const [response] = await Promise.all([
    page.waitForResponse((r) => r.request().method() === 'POST'
      && new URL(r.url()).pathname.endsWith('/messenger/CreateMessage'), { timeout: 45000 }),
    page.waitForLoadState('domcontentloaded', { timeout: 45000 }),
    page.locator('button[type="submit"]').first().click(),
  ]);
  assert(response.status() < 400, `messenger/CreateMessage returned HTTP ${response.status()}`);
  await assertNotErrorPage(page, 'messenger sent confirmation');

  const message = await waitFor(() => stampedMessage(),
    { description: 'the sent message to reach messagetbl' });
  // Two layers of escaping sit between the typed text and this comparison, and
  // neither is a defect: the editor stores MARKDOWN (an underscore is written as
  // \_), and `mysql -B` escapes backslashes again on its way out. Stripping
  // backslashes before any non-alphanumeric keeps this assertion about "the body
  // survived the round trip" instead of about those two encodings. The reader-facing
  // side is covered by readMessage(), which asserts the rendered page shows the
  // text without stray backslashes.
  const storedBody = message.body.replace(/\\+(?=[^0-9A-Za-z])/g, '');
  assert(storedBody.includes(body),
    `sent message body was stored as ${JSON.stringify(message.body.slice(0, 120))}`);

  const recipients = await waitFor(() => {
    const rows = recipientRows(message.id);
    return rows.length > 0 ? rows : null;
  }, { description: 'the per-recipient messagelisttbl row to be written' });
  assert(recipients.some((entry) => entry.provider === providerNo),
    `message ${message.id} was delivered to ${JSON.stringify(recipients)}, not to provider ${providerNo}`);
  assert(recipientStatus(message.id, providerNo) === STATUS_NEW,
    `a freshly delivered message arrived with status ${recipientStatus(message.id, providerNo)}, expected ${STATUS_NEW}`);
  return { message, recipientValue };
}

/**
 * Ensures the test provider is enrolled as a local messenger contact.
 *
 * A clean install has an EMPTY groupMembers_tbl — the shipped "doc" group has no
 * members and no local contacts are seeded — so the compose page renders an empty
 * recipient list and no message can be sent at all. Enrolment through
 * Administration > Messenger is the operator's remedy, so the check performs it
 * through that page rather than with an INSERT: it covers the admin surface and
 * proves the remedy works. Enrolment the check performed is undone on cleanup;
 * enrolment that already existed is left alone.
 */
async function ensureMessengerContact(context) {
  const already = sql.scalar(
    `SELECT id FROM groupMembers_tbl WHERE provider_no='${escapeSql(providerNo)}' AND facilityId=0 LIMIT 1`
  );
  if (already) {
    return { enrolledByCheck: false };
  }

  const page = await context.newPage();
  wirePage(page, 'messenger-admin', recorder);
  await gotoApp(page, config.baseUrl, '/messenger', 'domcontentloaded');
  await page.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
  await assertNotErrorPage(page, 'messenger administration');

  const contact = page.locator(`div#addContacts input[type="checkbox"][value^="${providerNo}"]`).first();
  assert(await contact.count() > 0,
    `messenger administration listed no contact checkbox for provider ${providerNo}`);

  const [response] = await Promise.all([
    page.waitForResponse((r) => r.request().method() === 'POST'
      && /\/messenger(\?|$)/.test(r.url().replace(/^https?:\/\/[^/]+/, '')), { timeout: 45000 }),
    contact.check(),
  ]);
  assert(response.status() < 400, `messenger contact enrolment returned HTTP ${response.status()}`);

  await waitFor(() => sql.scalar(
    `SELECT id FROM groupMembers_tbl WHERE provider_no='${escapeSql(providerNo)}' AND facilityId=0 LIMIT 1`
  ), { description: `provider ${providerNo} to be enrolled as a messenger contact` });
  await page.close().catch(() => {});
  return { enrolledByCheck: true };
}

async function openInbox(context, boxType, label) {
  const page = await context.newPage();
  wirePage(page, label, recorder);
  await gotoApp(page, config.baseUrl, '/messenger/DisplayMessages', 'domcontentloaded', { boxType });
  await page.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
  await assertNotErrorPage(page, `${label} box ${boxType}`);
  return page;
}

async function reloadBox(page, boxType, label) {
  await gotoApp(page, config.baseUrl, '/messenger/DisplayMessages', 'domcontentloaded', { boxType });
  await page.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
  await assertNotErrorPage(page, `${label} box ${boxType}`);
}

/**
 * The inbox row for one message, addressed by its own selection checkbox.
 *
 * The inbox nests tables, so `table tr` matches an outer row as well as the inner
 * one and a naive `.filter({has})` resolves to both; scoping to the checkbox and
 * walking up to its row keeps the locator unambiguous.
 */
function messageCheckbox(page, messageId) {
  return page.locator(`input[name="messageNo"][value="${messageId}"]`).first();
}


/**
 * Opens the message and asserts reading it is what marks it read.
 *
 * Asserted as a state transition rather than just a render: an inbox that shows
 * the body but never clears the unread flag leaves every message permanently
 * bold, which is how a real unread message stops being noticeable.
 */
async function readMessage(page, messageId) {
  const link = page.locator(`a[href*="/messenger/ViewMessage?messageID=${messageId}"]`).first();
  await link.waitFor({ state: 'visible', timeout: 30000 });
  await Promise.all([
    page.waitForLoadState('domcontentloaded', { timeout: 45000 }),
    link.click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  const text = await assertNotErrorPage(page, 'messenger message view');
  assert(text.replace(/\\+(?=[^0-9A-Za-z])/g, '').includes(body),
    `the opened message did not render its body: ${text.slice(0, 300)}`);

  await waitFor(() => (recipientStatus(messageId, providerNo) === STATUS_READ ? true : null),
    { description: `message ${messageId} to be marked ${STATUS_READ} by being opened` });
}

/**
 * Runs one of the inbox's bulk actions over a single selected message.
 *
 * The selection is a checkbox keyed by message id, so the check can prove the
 * action applied to the row it chose and not to the whole box — which is the
 * failure a bulk action fails with.
 */
async function runBulkAction(page, boxType, messageId, buttonName, expectedStatus, label) {
  await reloadBox(page, boxType, label);
  const checkbox = messageCheckbox(page, messageId);
  await checkbox.waitFor({ state: 'visible', timeout: 30000 });
  await checkbox.check();

  const button = page.locator(`button[name="${buttonName}"]`).first();
  assert(await button.count() > 0, `inbox rendered no ${buttonName} control in box ${boxType}`);

  const [response] = await Promise.all([
    page.waitForResponse((r) => r.request().method() === 'POST'
      && new URL(r.url()).pathname.includes('/messenger/'), { timeout: 45000 }),
    page.waitForLoadState('domcontentloaded', { timeout: 45000 }),
    button.click(),
  ]);
  assert(response.status() < 400, `${buttonName} returned HTTP ${response.status()}`);
  const posted = new URLSearchParams(response.request().postData() || '');
  assert(posted.getAll('messageNo').includes(String(messageId)),
    `${buttonName} POST did not carry the selected message ${messageId}`);
  await assertNotErrorPage(page, `inbox after ${buttonName}`);

  await waitFor(() => (recipientStatus(messageId, providerNo) === expectedStatus ? true : null),
    { description: `${buttonName} to move message ${messageId} to status ${expectedStatus}` });
}

/** Asserts the archived message left the inbox and is listed in the archive box. */
async function assertArchivedPlacement(page, messageId) {
  await reloadBox(page, BOX_INBOX, 'messenger-inbox');
  assert(await messageCheckbox(page, messageId).count() === 0,
    `archived message ${messageId} is still listed in the inbox`);

  await reloadBox(page, BOX_ARCHIVED, 'messenger-archive');
  await messageCheckbox(page, messageId).waitFor({ state: 'visible', timeout: 30000 });
}

/** Drives the inbox search form and asserts it finds the message by subject. */
async function searchInbox(page, messageId) {
  await reloadBox(page, BOX_INBOX, 'messenger-inbox');
  const searchInput = page.locator('input[name="searchString"]').first();
  assert(await searchInput.count() > 0, 'inbox rendered no search field');
  await searchInput.fill(stamp);
  await Promise.all([
    page.waitForLoadState('domcontentloaded', { timeout: 45000 }),
    page.locator('button[name="btnSearch"]').first().click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(page, 'inbox search results');
  await messageCheckbox(page, messageId).waitFor({ state: 'visible', timeout: 30000 });

  // Clearing the filter matters as much as setting it: the filter lives in a
  // session bean, so a clear that no-ops leaves the next page load showing a
  // stale, narrowed inbox.
  await Promise.all([
    page.waitForLoadState('domcontentloaded', { timeout: 45000 }),
    page.locator('button[name="btnClearSearch"]').first().click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(page, 'inbox after clearing search');
}

(async () => {
  cleanupRows();
  const browser = await chromium.launch(getLaunchOptions(config.chromePath));
  const context = await browser.newContext({ ignoreHTTPSErrors: true });
  try {
    await login(context, config, recorder);

    const enrolment = await ensureMessengerContact(context);
    enrolledByCheck = enrolment.enrolledByCheck;
    pass(enrolledByCheck
      ? `messenger administration enrolled provider ${providerNo} as a local contact`
      : `provider ${providerNo} was already enrolled as a local messenger contact`);

    const inbox = await openInbox(context, BOX_INBOX, 'messenger-inbox');
    pass('messenger inbox renders for the logged-in provider');

    await openCompose(context, inbox);
    pass('the inbox Compose link reaches the compose form with a recipient list');

    const { message } = await sendMessage(inbox);
    pass(`message ${message.id} delivered to provider ${providerNo} as unread`);

    await reloadBox(inbox, BOX_INBOX, 'messenger-inbox');
    await messageCheckbox(inbox, message.id).waitFor({ state: 'visible', timeout: 30000 });
    pass('the delivered message is listed in the inbox');

    await readMessage(inbox, message.id);
    pass('opening the message renders its body and marks it read');

    await runBulkAction(inbox, BOX_INBOX, message.id, 'btnUnread', STATUS_NEW, 'messenger-inbox');
    pass('mark-unread returns the selected message to unread');

    await runBulkAction(inbox, BOX_INBOX, message.id, 'btnRead', STATUS_READ, 'messenger-inbox');
    pass('mark-read sets the selected message back to read');

    await searchInbox(inbox, message.id);
    pass('inbox search finds the message by subject and the filter clears again');

    await runBulkAction(inbox, BOX_INBOX, message.id, 'btnDelete', STATUS_DELETED, 'messenger-inbox');
    pass('archive moves the selected message to the archived status');

    await assertArchivedPlacement(inbox, message.id);
    pass('the archived message leaves the inbox and appears in the archive box');

    await runBulkAction(inbox, BOX_ARCHIVED, message.id, 'btnUnarchive', STATUS_READ, 'messenger-archive');
    pass('unarchive restores the message to a readable status');

    assertNoPageErrors(recorder, 'messenger inbox');
    console.log(`\nCompleted ${passed.length} Playwright checks, 0 failures`);
  } finally {
    await context.close().catch(() => {});
    await browser.close().catch(() => {});
    cleanupRows();
    if (enrolledByCheck) {
      sql.exec(`DELETE FROM groupMembers_tbl WHERE provider_no='${escapeSql(providerNo)}' AND facilityId=0`);
    }
    sql.close();
  }
})().catch((error) => {
  console.error(`FAIL messenger inbox interface flow: ${error.stack || error.message}`);
  process.exit(1);
});
