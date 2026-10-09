#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
/*
 * Installed-app regression checks for email recovery and compose state. Creates
 * only owned synthetic email logs for demo patient 1; never submits or sends an
 * email. Uses the deb-install environment contract. The copied log carries a
 * formatted footer (issue #3981) with a script appended: the compose screen must
 * show it cleaned in the Footer card, open it in the Edit footer window, and put
 * the cleaned footer in the form it posts. Nothing in it may run.
 */
// Credentials: TEST_USER, TEST_PASSWORD, TEST_PIN; MYSQL_HOST/USER/PASSWORD/DATABASE.
// Run only against a disposable local demo database. Optional EMAIL_RECOVERY_DEMO_NO.
const { chromium } = require('playwright');
const { execFileSync } = require('node:child_process');
const { randomUUID } = require('node:crypto');
const { createGracefulSignalCancellation, settleOperations } = require('./graceful-signal-cancellation');
const { appUrl, assert, assertNotErrorPage, buildFailureDetails, createRecorder,
  getLaunchOptions, gotoApp, login, validateBaseUrl, wirePage } = require('./eform-local-playwright-utils');

const config = {
  baseUrl: validateBaseUrl(process.env.BASE_URL || 'https://127.0.0.1/carlos'),
  chromePath: process.env.CHROME_PATH || '',
  testUser: process.env.TEST_USER || 'carlosdoc',
  testPassword: process.env.TEST_PASSWORD || 'carlos2026',
  testPin: process.env.TEST_PIN || '2026',
};
// Fixtures are staged in the local database, so a remote app is never a valid target.
const isLoopback = ['localhost', '127.0.0.1', '[::1]'].includes(config.baseUrl.hostname);
assert(isLoopback, 'Email recovery fixture requires a loopback BASE_URL');
const mysqlHost = process.env.MYSQL_HOST || 'localhost';
assert(['localhost', '127.0.0.1', '::1'].includes(mysqlHost), 'Email recovery fixture requires a loopback MYSQL_HOST');
const demographicNo = process.env.EMAIL_RECOVERY_DEMO_NO || '1';
assert(/^[1-9]\d*$/.test(demographicNo), 'EMAIL_RECOVERY_DEMO_NO must be a positive identifier');
const marker = `PWRECOVERY${randomUUID().replaceAll('-', '')}`;
// Stored as a log would never hold it, to prove the copy path cleans what it reads.
const storedFooter = '<b>Synthetic</b> recovery footer<script>window.__footerScriptRan = true</script>';
const cleanFooter = '<b>Synthetic</b> recovery footer';
function sql(query) {
  return execFileSync('mariadb', ['--no-defaults', '-h', mysqlHost,
    '-u', process.env.MYSQL_USER || 'root', process.env.MYSQL_DATABASE || 'carlos', '-N', '-B', '-e', query],
  { encoding: 'utf8', timeout: 15000, env: { ...process.env, MYSQL_PWD: process.env.MYSQL_PASSWORD || '' },
    stdio: ['ignore', 'pipe', 'pipe'] }).trim();
}

// The Footer card on a copied email: the footer that was sent, cleaned; the warning line; the
// shared Edit footer window with its limit and counter; and a place in the posted form. Switching
// sender never changes the footer. Nothing from the stored footer runs.
async function assertCopiedFooter(compose) {
  const footer = compose.locator('#footerEmail');
  assert(await footer.inputValue() === cleanFooter, 'Copying a log lost its footer or did not clean it');
  const preview = compose.locator('#footerEmailPreview');
  assert(await preview.innerText() === 'Synthetic recovery footer', 'Footer card does not show the footer');
  assert(await preview.locator('b').count() === 1, 'Footer card lost the footer formatting');
  const help = await compose.locator('#footerEmailHelp').innerText();
  assert(/unencrypted/i.test(help) && /not saved to the chart/i.test(help) && /patient information/i.test(help),
    'Footer help line does not warn that the footer is unencrypted, not charted and PHI-free');

  await compose.locator('#footerEmailEdit').click();
  const modal = compose.locator('#footerEditorModal');
  await modal.waitFor({ state: 'visible' });
  assert(await modal.getAttribute('data-max-length') === '2000', 'Edit footer window lost its 2,000-character limit');
  assert(await compose.locator('#footerEditorCount').innerText() === String('Synthetic recovery footer'.length),
    'Edit footer window does not count the plain-text characters');
  await compose.locator('#footerEditorText').click();
  await compose.keyboard.press('End');
  await compose.keyboard.type(' edited');
  await compose.locator('#footerEditorApply').click();
  await modal.waitFor({ state: 'hidden' });
  assert(/^<b>Synthetic<\/b> recovery footer edited/.test(await footer.inputValue()),
    'Applying the Edit footer window did not update the footer');

  // A footer smuggled into the field is cleaned before the window shows it: nothing runs.
  await compose.evaluate(() => {
    document.getElementById('footerEmail').value = '<img src="x" onerror="window.__footerScriptRan = true"><i>ok</i>';
  });
  await compose.locator('#footerEmailEdit').click();
  await modal.waitFor({ state: 'visible' });
  assert(await compose.locator('#footerEditorText img').count() === 0, 'Edit footer window showed an image tag');
  await compose.locator('#footerEditorModal .btn-close').click();
  await modal.waitFor({ state: 'hidden' });
  assert(await compose.evaluate(() => window.__footerScriptRan === undefined), 'A footer script ran on the compose screen');
  await compose.evaluate(html => { document.getElementById('footerEmail').value = html; }, cleanFooter);

  const posted = await compose.evaluate(() => new FormData(document.getElementById('emailComposeForm')).get('footerEmail'));
  assert(posted === cleanFooter, 'Footer is not part of the posted compose form');
  const senders = compose.locator('#senderEmailAddress option');
  if (await senders.count() > 1 && await compose.locator('#senderEmailAddress').isEnabled()) {
    const current = await compose.locator('#senderEmailAddress').inputValue();
    const other = await senders.evaluateAll((options, value) => options.map(o => o.value).find(v => v !== value), current);
    await compose.locator('#senderEmailAddress').selectOption(other);
    assert(await footer.inputValue() === cleanFooter, 'Switching sender replaced a copied footer');
    await compose.locator('#senderEmailAddress').selectOption(current);
  }
}

(async () => {
  const cancellation = createGracefulSignalCancellation();
  const recorder = createRecorder();
  const ids = [];
  let browser;
  try {
    await cancellation.run(async () => {
      assert(sql(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${demographicNo}`) === '1',
        'Synthetic demo patient is missing');
      for (const age of ['NOW()', 'DATE_SUB(NOW(), INTERVAL 1 DAY)']) {
        const id = sql(`INSERT INTO emailLog (fromEmail,toEmail,subject,body,footer,status,timestamp,isEncrypted,
          isAttachmentEncrypted,chartDisplayOption,transactionType,demographicNo,providerNo)
          VALUES ('sender@example.invalid','recipient@example.invalid','${marker}',TO_BASE64('Synthetic recovery message'),
          TO_BASE64('${storedFooter}'),'PENDING',${age},1,1,'WITHOUT_NOTE','DIRECT',${demographicNo},'999998');
          SELECT LAST_INSERT_ID()`);
        assert(/^[1-9]\d*$/.test(id), 'Email fixture did not return an identifier');
        ids.push(id);
      }
      const [freshId, staleId] = ids;
      browser = await chromium.launch({ ...getLaunchOptions(config.chromePath), handleSIGINT: false, handleSIGTERM: false });
      cancellation.throwIfCancelled();
      const context = await browser.newContext({ ignoreHTTPSErrors: isLoopback });
      const page = await login(context, config, recorder);
      page.removeAllListeners('dialog');
      page.on('dialog', async dialog => {
        if (dialog.type() === 'confirm' && /resolv/i.test(dialog.message())) await dialog.accept();
        else { recorder.dialogs.push({ type: dialog.type(), text: dialog.message() }); await dialog.dismiss(); }
      });
      await gotoApp(page, config.baseUrl, '/admin/ManageEmails');
      await assertNotErrorPage(page, 'Manage Emails');
      await page.locator('#dateBegin').fill(sql('SELECT DATE_SUB(CURDATE(), INTERVAL 2 DAY)'));
      await page.locator('#dateEnd').fill(sql('SELECT DATE_ADD(CURDATE(), INTERVAL 1 DAY)'));
      await settleOperations([page.waitForResponse(r => r.url().includes('/admin/ManageEmails')
        && r.request().method() === 'POST'), page.locator('#btnFetch').click()]);
      await page.locator(`#emailStatus${freshId}`).waitFor();
      assert(await page.locator(`#btnResolve${freshId}, #btnResend${freshId}`).count() === 0,
        'In-flight email incorrectly offered resolve/resend');
      assert(await page.locator(`#btnResolve${staleId}`).count() === 1, 'Stale pending email cannot be recovered');
      const token = await page.locator('input[name="CSRF-TOKEN"]').first().inputValue();
      const rejected = await context.request.post(appUrl(config.baseUrl, '/admin/ManageEmails'),
        { form: { method: 'setResolved', logId: freshId, 'CSRF-TOKEN': token } });
      assert(rejected.status() === 409, `In-flight resolution should return 409, got ${rejected.status()}`);
      assert(sql(`SELECT status FROM emailLog WHERE id=${freshId}`) === 'PENDING', 'Rejected recovery changed the original log');

      const secrets = [];
      for (let attempt = 0; attempt < 2; attempt += 1) {
        cancellation.throwIfCancelled();
        const [compose] = await settleOperations([context.waitForEvent('page'), page.locator(`#btnResend${staleId}`).click()]);
        wirePage(compose, 'email-copy', recorder);
        await compose.locator('#emailPDFPassword').waitFor({ state: 'attached' });
        await compose.waitForLoadState('networkidle');
        await assertNotErrorPage(compose, 'email copy');
        assert(await compose.locator('#message').inputValue() === 'Synthetic recovery message',
          'Copying a legacy log lost its message when the optional encrypted field was null');
        await assertCopiedFooter(compose);
        assert(await compose.locator('#emailResendWarning').isVisible(), 'Unconfirmed delivery warning is missing');
        const passphrase = await compose.locator('#emailPDFPassword').inputValue();
        const composeToken = await compose.locator('input[name="emailPDFPasswordToken"]').inputValue();
        assert(passphrase.length >= 16 && composeToken.length > 0, 'Compose lacks a random passphrase or state token');
        assert(!secrets.some(s => s.passphrase === passphrase || s.token === composeToken), 'Separate compose windows reused secrets');
        secrets.push({ passphrase, token: composeToken });
        const warnings = compose.locator('#errorMessageModal');
        if (await compose.locator('#totalSenderEmails').inputValue() === '0') {
          assert(await warnings.isVisible() && /no outgoing email account/i.test(await warnings.innerText()),
            'Missing sender configuration did not produce an explicit operator warning');
          assert(await compose.locator('#btnSend').isDisabled(), 'Unconfigured compose still permits sending');
        }
        if (await warnings.isVisible()) await warnings.locator('[data-bs-dismiss="modal"]').first().click();
        const [cancel] = await settleOperations([compose.waitForResponse(r => r.url().includes('emailSendAction?method=cancel')),
          compose.waitForEvent('close'), compose.locator('#btnCancel').click()]);
        assert(cancel.status() === 204, `Compose cleanup failed with HTTP ${cancel.status()}`);
      }
      assert(sql(`SELECT status FROM emailLog WHERE id=${staleId}`) === 'PENDING', 'Copy/cancel changed the original delivery status');
      await settleOperations([page.waitForResponse(r => r.url().includes('/admin/ManageEmails')
        && r.request().postData()?.includes('setResolved')), page.locator(`#btnResolve${staleId}`).click()]);
      await page.locator(`#emailStatus${staleId}`).filter({ hasText: /resolved/i }).waitFor();
      assert(sql(`SELECT status FROM emailLog WHERE id=${staleId}`) === 'RESOLVED', 'UI resolution was not persisted');
      assert(sql(`SELECT COUNT(*) FROM outboundEmailArchive WHERE emailLogId IN (${ids.join(',')})`) === '0',
        'Copy/cancel unexpectedly archived or sent the fixture');
      assert(recorder.badResponses.filter(r => r.status >= 500).length === 0, 'Email recovery produced a server error');
      assert(recorder.pageErrors.length === 0, 'Email recovery produced an uncaught browser error');
      console.log('PASS email recovery: fresh-send rejection, stale warning/resolve, copied footer, separate random compose secrets, and acknowledged cancellation; no email sent');
    });
  } catch (error) {
    console.error('FAIL email recovery Playwright check');
    console.error(error.stack || error.message);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = cancellation.exitCode || 1;
  } finally {
    try { if (browser) await browser.close(); } finally {
      try {
        for (const id of ids) sql(`DELETE FROM emailLog WHERE id=${id} AND subject='${marker}'`);
        assert(sql(`SELECT COUNT(*) FROM emailLog WHERE subject='${marker}'`) === '0', 'Owned email fixture cleanup failed');
      } finally { cancellation.dispose(); }
    }
  }
})().catch(() => { console.error('Email recovery cleanup failed'); process.exitCode = process.exitCode || 1; });
