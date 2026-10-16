#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The lab Forward provider picker (oscarMDS/ViewSelectProvider) and what it refuses (issue #4558,
 * finding 184; coverage plan: lab-forward-picker).
 *
 * WHY THIS CHECK EXISTS. legacy-lab-display drives a successful Forward from the CML and MDS lab pages.
 * This check holds the picker's own contract around that, which a successful Forward cannot show:
 *   - the picker opened as a popup has a Forward button, and the same page loaded the way the Inbox's
 *     Forward dialog loads it (an AJAX POST, oscarMDSIndex.js ForwardSelectedRows) does not, because that
 *     dialog has its own Forward button and a second one would post nothing;
 *   - Forward from a picker with no lab page behind it, or with an empty Forward List, says why with an
 *     alert, stays open and sends nothing (the pre-fix picker closed itself and sent nothing, silently);
 *   - the request the lab pages' own reassignForm sent before the fix (bare id lists: flaggedLabs=<id>,
 *     selectedProviders=<provider_no>, favorites=) is refused with 400 and writes nothing. Before the fix it
 *     was answered 302 to "<route>&providerNo=..." (not a route), routed nothing, and deleted every one of
 *     the sender's forwarding favourites because it carried no selectedFavorites.
 *
 * Steps:
 *   seed         an owned CML lab for the owned patient, routed to the test provider as New; a throwaway
 *                recipient login; the test login's forwarding favourites snapshotted.
 *   buttons      GET oscarMDS/ViewSelectProvider: #submitButton is there and reads Forward. The same page as
 *                the Inbox dialog requests it (jQuery POST with forwardList and isListView): the provider
 *                search, the Forward List and Favorites are there, #submitButton is not.
 *   no opener    in that picker (opened with no lab page behind it), the provider search puts the recipient
 *                in the Forward List; Forward raises exactly the "no longer open" alert, the picker stays
 *                open, no Forward request is sent and the recipient holds no routing row.
 *   empty list   the CML lab page's Forward opens the picker; Forward with nothing in the Forward List raises
 *                exactly the "choose at least one provider" alert, marks the list, the picker stays open,
 *                the lab page posts nothing.
 *   legacy form  the lab page's own reassignForm fields, posted as they were before the fix (with the
 *                page's CSRF token, so only the payload is under test), answer 400; the recipient holds no
 *                routing row; the sender's routing row and forwarding favourites are unchanged.
 *
 * Fixtures: the owned FAKE- patient (runWorkflow, no master record opened); one CML lab seeded by SQL by
 * legacy-lab-display's describeLab under a random 10-digit number verified unoccupied; a throwaway provider
 * login that is the would-be recipient. Cleanup removes exactly those rows by key (removeLab proves the lab
 * header still carries the run's marker first) and asserts them gone. The test login's forwarding favourites
 * are snapshotted, asserted unchanged, and restored if anything rewrote them, so this must not run
 * concurrently with another check that creates favourites (live wrapper: EXCLUSIVE=1).
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { throwawayLoginFixture, bundleMessage } = require('./lib/throwaway-login-fixture');
const { describeLab, removeLab } = require('./legacy-lab-display-playwright-checks');

const NAME = 'lab-forward-picker';
const TIMEOUT = 30000;
const q = h.sqlString;

/* Literal step labels: the manifest's expectedFailure.step is matched against this file's text. */
const STEP = Object.freeze({
  seed: 'an owned CML lab is seeded for the owned patient and routed to the test provider',
  buttons: 'the picker opened as a popup has Forward and the Inbox dialog request of the same page does not',
  noOpener: 'Forward from a picker with no lab page behind it says so, stays open and sends nothing',
  emptyList: 'Forward with an empty Forward List says so, stays open and sends nothing',
  legacyForm: 'the pre-fix lab page form with bare id lists is refused with 400 and writes nothing',
});

const FORWARD_ROUTES = /\/(oscarMDS\/Forward|oscarMDS\/ReportReassign|lab\/CA\/(ON|BC|ALL)\/Forward)$/;

async function workflow(s) {
  const { sql, config, recorder, marker, patient, provider } = s;
  const hex = marker.slice('FAKE-PW'.length);
  h.assert(/^[0-9a-f]{16}$/.test(hex), 'The run marker is not in the FAKE-PW<hex> form this check relies on');
  const recipient = throwawayLoginFixture({ sql, marker, provider, testUser: config.testUser });
  const lab = describeLab({ sql, marker, hex, patient, provider, type: 'CML' });
  const forwardLabel = bundleMessage('oscarMDS.index.btnForward', 'Forward');
  const msgOpenerGone = bundleMessage('oscarMDS.selectProvider.msgOpenerGone',
    'The lab page that opened this window is no longer open, so nothing was forwarded. Close this window and press Forward on the lab again.');
  const msgChooseProvider = bundleMessage('oscarMDS.selectProvider.msgChooseProvider',
    'Choose at least one provider for the Forward List.');

  const routing = (who) => JSON.stringify(sql.rows(`SELECT id, provider_no, status, IFNULL(comment,'<null>'), IFNULL(timestamp,'<null>')
    FROM providerLabRouting WHERE lab_type=${q(lab.labType)} AND lab_no=${lab.id} AND provider_no=${q(who)} ORDER BY id`));
  const favourites = () => JSON.stringify(sql.rows(`SELECT id, route_to_provider_no FROM providerLabRoutingFavorites
    WHERE provider_no=${q(provider)} ORDER BY id`));
  const favouritesBefore = favourites();

  // Cleanup runs last-registered first; everything is registered before its first write.
  s.cleanup(() => recipient.cleanup());
  s.cleanup(() => {
    if (favourites() === favouritesBefore) return;
    sql.execute(`DELETE FROM providerLabRoutingFavorites WHERE provider_no=${q(provider)}`);
    for (const [id, to] of JSON.parse(favouritesBefore)) {
      h.assert(/^[1-9]\d*$/.test(id) && /^[A-Za-z0-9]{1,6}$/.test(to), 'A snapshotted favourite has an unexpected shape');
      sql.execute(`INSERT INTO providerLabRoutingFavorites (id,provider_no,route_to_provider_no) VALUES (${id},${q(provider)},${q(to)})`);
    }
    h.assert(favourites() === favouritesBefore, 'The test login\'s forwarding favourites were not restored');
    console.log('  note: the test login\'s forwarding favourites were rewritten; they were restored as found');
  });
  s.cleanup(() => removeLab({ sql, lab, patient, provider }));

  /** Every Forward/ReportReassign POST a page sends, so a refusal can be shown to have sent nothing. */
  function watchForwardPosts(page) {
    const posts = [];
    page.on('request', (request) => {
      if (request.method() === 'POST' && FORWARD_ROUTES.test(new URL(request.url()).pathname)) posts.push(request.url());
    });
    return posts;
  }

  /** Search for the recipient in the picker and put it in the Forward List, as a clinician does. */
  async function chooseRecipient(picker) {
    await picker.locator('#autocompleteprov').waitFor({ state: 'visible', timeout: TIMEOUT });
    await picker.locator('#autocompleteprov').fill(`${marker}, Throwaway`);
    const offered = picker.locator('.ui-autocomplete:visible .ui-menu-item').filter({ hasText: marker }).first();
    await offered.waitFor({ state: 'visible', timeout: 15000 }).catch(() => {});
    h.assert(await offered.isVisible(), 'The provider picker\'s search offered nothing for the recipient');
    await offered.click();
    const listed = await picker.locator('#fwdProviders option').evaluateAll((options) => options.map((option) => option.value));
    h.assert(JSON.stringify(listed) === JSON.stringify([recipient.providerNo]),
      'Selecting the search result did not put exactly the recipient in the Forward List');
  }

  /** Press Forward expecting exactly one alert with `text`; the picker must stay open. */
  async function forwardExpectingAlert(picker, text, what) {
    const dialogs = await h.withExpectedDialogs(picker, async () => {
      await picker.locator('#submitButton').click();
      await picker.waitForTimeout(500);
    });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'alert' && dialogs[0].text === text,
      `Forward ${what} did not raise exactly the expected alert (${dialogs.map((d) => `${d.type}: ${d.text}`).join(' | ') || 'no dialog'})`);
    h.assert(!picker.isClosed(), `The picker closed itself on Forward ${what}`);
  }

  await s.step(STEP.seed, async () => {
    recipient.create();
    lab.seed();
    sql.execute(`INSERT INTO patientLabRouting(demographic_no,lab_no,lab_type,created) VALUES(${patient},${lab.id},${q(lab.labType)},NOW());
      INSERT INTO providerLabRouting(provider_no,lab_no,lab_type,status) VALUES(${q(provider)},${lab.id},${q(lab.labType)},'N')`);
    h.assert(sql.value(lab.headerSql) === '1', 'The CML lab header was not seeded');
    h.assert(routing(recipient.providerNo) === '[]', 'The recipient already holds the CML lab');
  });

  const picker = await s.context.newPage();
  const pickerPosts = watchForwardPosts(picker);
  try {
    await s.step(STEP.buttons, async () => {
      await h.gotoApp(picker, config.baseUrl, '/oscarMDS/ViewSelectProvider');
      await h.assertNotErrorPage(picker, 'the provider picker');
      const button = picker.locator('#submitButton');
      h.assert(await button.count() === 1 && await button.isVisible(), 'The provider picker opened as a page has no visible Forward button');
      h.assert((await button.innerText()).trim() === forwardLabel, `The picker's button does not read "${forwardLabel}"`);
      // The Inbox's dialog request, made from a CARLOS page so CSRFGuard's XHR hook adds the token as it does there.
      const html = await picker.evaluate((url) => new Promise((resolve, reject) => {
        window.jQuery.ajax({ url, method: 'POST', data: { isListView: 'true', forwardList: '1:HL7' } })
          .done((body) => resolve(String(body))).fail((xhr) => reject(new Error(`the dialog request answered HTTP ${xhr.status}`)));
      }), h.appUrl(config.baseUrl, '/oscarMDS/ViewSelectProvider'));
      for (const id of ['autocompleteprov', 'fwdProviders', 'favorites']) {
        h.assert(html.includes(`id="${id}"`), `The picker as the Inbox dialog loads it has no #${id}`);
      }
      h.assert(!html.includes('id="submitButton"'),
        'The picker as the Inbox dialog loads it renders its own Forward button beside the dialog\'s');
    });

    await s.step(STEP.noOpener, async () => {
      await chooseRecipient(picker);
      await forwardExpectingAlert(picker, msgOpenerGone, 'with no lab page behind the picker');
      h.assert(pickerPosts.length === 0, `The picker sent a Forward request with no lab page behind it (${pickerPosts.join(', ')})`);
      h.assert(routing(recipient.providerNo) === '[]', 'The recipient was given a routing row although nothing was forwarded');
    });
  } finally {
    await picker.close().catch(() => {});
  }

  const labPage = await s.context.newPage();
  const labPosts = watchForwardPosts(labPage);
  try {
    await s.step(STEP.emptyList, async () => {
      await h.gotoApp(labPage, config.baseUrl, lab.displayPath);
      await h.assertNotErrorPage(labPage, 'the CML lab page');
      await labPage.locator('form[name="reassignForm"]').waitFor({ state: 'attached', timeout: TIMEOUT });
      const opened = await s.popup(labPage, labPage.getByRole('button', { name: 'Forward', exact: true }).first(), `${NAME}-picker`);
      try {
        await opened.waitForLoadState('load', { timeout: TIMEOUT });
        await opened.locator('#fwdProviders').waitFor({ state: 'attached', timeout: TIMEOUT });
        h.assert(await opened.locator('#fwdProviders option').count() === 0, 'The Forward List is not empty when the picker opens');
        await forwardExpectingAlert(opened, msgChooseProvider, 'with an empty Forward List');
        h.assert(await opened.locator('#fwdProviders.input-error').count() === 1, 'The empty Forward List is not marked');
        h.assert(labPosts.length === 0, `The lab page posted a Forward request with an empty Forward List (${labPosts.join(', ')})`);
        h.assert(routing(recipient.providerNo) === '[]', 'The recipient was given a routing row although nothing was forwarded');
      } finally {
        await opened.close().catch(() => {});
      }
    });

    await s.step(STEP.legacyForm, async () => {
      const senderBefore = routing(provider);
      // The page's own form, with the provider a pre-fix picker would have written into it. Its CSRF token is
      // the page's, so the request is refused, if at all, for its payload and not for a missing token.
      const fields = await labPage.evaluate((recipientNo) => {
        const form = document.forms.reassignForm;
        form.elements.selectedProviders.value = recipientNo;
        const out = {};
        for (const element of form.elements) {
          if (element.name) out[element.name] = element.value;
        }
        const token = document.querySelector('input[name="CSRF-TOKEN"]');
        if (!out['CSRF-TOKEN'] && token) out['CSRF-TOKEN'] = token.value;
        return out;
      }, recipient.providerNo);
      h.assert(fields.flaggedLabs === String(lab.id) && fields.selectedProviders === recipient.providerNo,
        'The CML page\'s reassignForm no longer carries the bare lab number this step posts');
      h.assert(fields['CSRF-TOKEN'], 'The CML lab page carries no CSRF token to post with');
      const response = await s.context.request.post(h.appUrl(config.baseUrl, lab.forwardPath), {
        form: fields, maxRedirects: 0, headers: { Referer: labPage.url() },
      });
      h.assert(response.status() === 400,
        `The pre-fix reassignForm post was answered HTTP ${response.status()}${response.headers().location ? ` -> ${response.headers().location}` : ''}, not 400`);
      h.assert(routing(recipient.providerNo) === '[]', 'The refused post gave the recipient a routing row');
      h.assert(routing(provider) === senderBefore, 'The refused post changed the sender\'s routing row');
      h.assert(favourites() === favouritesBefore, 'The refused post changed the sender\'s forwarding favourites');
    });
  } finally {
    await labPage.close().catch(() => {});
  }
  h.assertStrictPage(recorder);
}

if (require.main === module) {
  runWorkflow(NAME, workflow, { openMaster: false });
}
module.exports = { workflow, STEP };
