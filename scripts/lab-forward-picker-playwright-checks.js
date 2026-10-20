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
 *   list         the patient lab list without a demographicNo (lab/ViewDemographicLab, the only mode with
 *                Forward and File, which lists labs filed under demographic 0): the owned HL7 lab's
 *                checkbox carries data-lab-type HL7; ticked, Forward opens the picker, which posts
 *                oscarMDS/ReportReassign (not AJAX) with exactly "<id>:HL7" and the recipient; the list
 *                lands back on itself; the recipient holds a New routing row and the sender's row and
 *                forwarding favourites are unchanged.
 *
 * Fixtures: the owned FAKE- patient (runWorkflow, no master record opened); one CML lab seeded by SQL by
 * legacy-lab-display's describeLab under a random 10-digit number verified unoccupied; one synthetic HL7 lab
 * uploaded through Inbox > HL7 Lab Upload with a unique accession, matched to the owned patient and then filed
 * under demographic 0 by SQL (where an unlinked lab lives); a throwaway provider login that is the recipient.
 * Cleanup removes exactly those rows by key (removeLab proves the CML header still carries the run's marker
 * first; the HL7 lab is found by its accession, with its upload checksum row and the archived upload file under
 * LAB_UPLOAD_DOCUMENT_STORE) and asserts them gone. The test login's forwarding favourites are snapshotted,
 * one favourite to the recipient is seeded so that "favourites unchanged" is asserted against a list that is
 * never empty, and cleanup restores the snapshot, so this must not run concurrently with another check that
 * creates favourites (live wrapper: EXCLUSIVE=1).
 */
const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { throwawayLoginFixture, bundleMessage } = require('./lib/throwaway-login-fixture');
const { describeLab, removeLab } = require('./legacy-lab-display-playwright-checks');
const { syntheticCmlLab } = require('./lab-upload-playwright-checks');
const { removeOwnedHl7Labs, removeArchiveFiles, archivesNamed, uploadFromInbox } = require('./lab-forwarding-rules-playwright-checks');

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
  listForward: 'Forward from the patient lab list routes the checked lab and returns to the list',
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
  // favouritesOriginal is what cleanup restores. favouritesBaseline is the state after the seed step adds a
  // fixture favourite, so "favourites unchanged" is asserted against a list that is never empty: a Forward
  // that wiped the sender's favourites (the pre-#4558 action did, for any post without selectedFavorites)
  // cannot pass by finding an empty list empty.
  const favouritesOriginal = favourites();
  let favouritesBaseline = null;

  // Cleanup runs last-registered first; everything is registered before its first write.
  s.cleanup(() => recipient.cleanup());
  s.cleanup(() => {
    if (favourites() === favouritesOriginal) return;
    sql.execute(`DELETE FROM providerLabRoutingFavorites WHERE provider_no=${q(provider)}`);
    for (const [id, to] of JSON.parse(favouritesOriginal)) {
      h.assert(/^[1-9]\d*$/.test(id) && /^[A-Za-z0-9]{1,6}$/.test(to), 'A snapshotted favourite has an unexpected shape');
      sql.execute(`INSERT INTO providerLabRoutingFavorites (id,provider_no,route_to_provider_no) VALUES (${id},${q(provider)},${q(to)})`);
    }
    h.assert(favourites() === favouritesOriginal, 'The test login\'s forwarding favourites were not restored');
  });
  s.cleanup(() => removeLab({ sql, lab, patient, provider }));

  // The patient lab list's own fixture: one synthetic HL7 lab uploaded through Inbox > HL7 Lab Upload.
  const stamp = crypto.randomBytes(4).toString('hex').toUpperCase();
  const listLab = { labNo: null, accession: `FP${stamp}`, fileName: `lab-forward-picker-${stamp}.hl7` };
  const workDir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-lab-forward-picker-'));
  s.cleanup(() => {
    fs.rmSync(workDir, { recursive: true, force: true });
    const found = sql.rows(`SELECT lab_no FROM hl7TextInfo WHERE accessionNum=${q(listLab.accession)}`).map(([id]) => id);
    removeOwnedHl7Labs(sql, [...found, listLab.labNo].filter(Boolean));
    // A rejected upload leaves its checksum row and archive without any lab row; the unique name finds both.
    sql.execute(`DELETE FROM fileUploadCheck WHERE filename LIKE ${q(`LabUpload.${listLab.fileName}.%`)}`);
    removeArchiveFiles(archivesNamed(listLab.fileName));
    h.assert(sql.value(`SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum=${q(listLab.accession)}`) === '0',
      'The synthetic HL7 lab was not removed');
  });

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
    sql.execute(`INSERT INTO providerLabRoutingFavorites (provider_no,route_to_provider_no) VALUES (${q(provider)},${q(recipient.providerNo)})`);
    h.assert(sql.value(`SELECT COUNT(*) FROM providerLabRoutingFavorites WHERE provider_no=${q(provider)}
      AND route_to_provider_no=${q(recipient.providerNo)}`) === '1', 'The fixture forwarding favourite was not seeded');
    favouritesBaseline = favourites();
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
      h.assert(favourites() === favouritesBaseline, 'The refused post changed the sender\'s forwarding favourites');
    });
  } finally {
    await labPage.close().catch(() => {});
  }

  await s.step(STEP.listForward, async () => {
    // Fixture: the lab is uploaded matched to the owned patient (routed to the test provider as its MRP),
    // then filed under demographic 0, where an unlinked lab lives. The list without a demographicNo, the
    // only mode that shows Forward and File, lists exactly those labs.
    const file = path.join(workDir, listLab.fileName); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal -- fixed file name under the run's own temp dir
    fs.writeFileSync(file, Buffer.from(syntheticCmlLab(listLab.accession, marker), 'latin1'));
    const inbox = await s.context.newPage();
    let status;
    try {
      await h.gotoApp(inbox, config.baseUrl, '/web/inboxhub/Inboxhub');
      await inbox.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
      status = await uploadFromInbox(inbox, recorder, file, listLab.fileName);
    } finally {
      await inbox.close().catch(() => {});
    }
    h.assert(status === 'Uploaded successfully', `The synthetic HL7 lab upload reported "${status}"`);
    await expectValue(sql, `SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum=${q(listLab.accession)}`, '1',
      'The synthetic HL7 lab did not reach hl7TextInfo');
    listLab.labNo = sql.value(`SELECT lab_no FROM hl7TextInfo WHERE accessionNum=${q(listLab.accession)}`);
    h.assert(/^[1-9]\d*$/.test(listLab.labNo), 'The synthetic HL7 lab has no lab number');
    const hl7 = (who) => JSON.stringify(sql.rows(`SELECT id, provider_no, status, IFNULL(comment,'<null>'), IFNULL(timestamp,'<null>')
      FROM providerLabRouting WHERE lab_type='HL7' AND lab_no=${listLab.labNo} AND provider_no=${q(who)} ORDER BY id`));
    h.assert(sql.value(`SELECT IFNULL(GROUP_CONCAT(status),'') FROM providerLabRouting WHERE lab_type='HL7'
      AND lab_no=${listLab.labNo} AND provider_no=${q(provider)}`) === 'N', 'The upload did not route the HL7 lab to the test provider as New');
    h.assert(hl7(recipient.providerNo) === '[]', 'The recipient already holds the HL7 lab');
    sql.execute(`UPDATE patientLabRouting SET demographic_no=0 WHERE lab_type='HL7' AND lab_no=${listLab.labNo} AND demographic_no=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM patientLabRouting WHERE lab_type='HL7' AND lab_no=${listLab.labNo} AND demographic_no=0`) === '1',
      'The synthetic HL7 lab could not be filed under demographic 0');
    const senderBefore = hl7(provider);

    const list = await s.context.newPage();
    try {
      await h.gotoApp(list, config.baseUrl, '/lab/ViewDemographicLab');
      await h.assertNotErrorPage(list, 'the patient lab list');
      await list.locator('#labResultsTbl').waitFor({ state: 'attached', timeout: TIMEOUT });
      // The list pages its rows (DataTables); page forward, as a clinician would, until the owned lab is shown.
      const box = list.locator(`input[name="flaggedLabs"][value="${listLab.labNo}"]`);
      for (let pageNo = 0; pageNo < 200 && await box.count() === 0; pageNo += 1) {
        const next = list.locator('#labResultsTbl_next:not(.disabled) a, #labResultsTbl_next:not(.disabled)').first();
        if (await next.count() === 0) break;
        await next.click();
      }
      h.assert(await box.count() === 1, 'The patient lab list does not list the owned unlinked HL7 lab');
      h.assert(await box.getAttribute('data-lab-type') === 'HL7', 'The owned lab\'s checkbox does not carry its lab type');
      await box.check();

      const picker = await s.popup(list, list.getByRole('button', { name: forwardLabel, exact: true }).first(), `${NAME}-list-picker`);
      try {
        await picker.waitForLoadState('load', { timeout: TIMEOUT });
        await chooseRecipient(picker);
        const sent = list.waitForRequest((request) => request.method() === 'POST'
          && new URL(request.url()).pathname.endsWith('/oscarMDS/ReportReassign'), { timeout: 15000 }).catch(() => null);
        const returned = list.waitForResponse((response) => {
          const navigation = response.request();
          const from = navigation.redirectedFrom();
          return navigation.isNavigationRequest() && navigation.method() === 'GET' && from !== null && from.method() === 'POST';
        }, { timeout: TIMEOUT }).catch(() => null);
        const closed = picker.waitForEvent('close', { timeout: 15000 }).then(() => true, () => false);
        await picker.locator('#submitButton').click();
        const request = await sent;
        h.assert(request, 'Forward from the patient lab list posted no oscarMDS/ReportReassign request');
        h.assert(await closed, 'The picker opened from the patient lab list stayed open after Forward');
        const body = new URLSearchParams(request.postData() || '');
        const parsed = (name, key) => {
          try { return JSON.parse(body.get(name))[key]; } catch (error) { return undefined; }
        };
        h.assert(JSON.stringify(parsed('flaggedLabs', 'files')) === JSON.stringify([`${listLab.labNo}:HL7`]),
          'The patient lab list\'s Forward request does not name exactly the checked lab as "<id>:HL7"');
        h.assert(JSON.stringify(parsed('selectedProviders', 'providers')) === JSON.stringify([recipient.providerNo]),
          'The patient lab list\'s Forward request does not name exactly the recipient');
        h.assert(body.has('ajax') === false, 'The patient lab list\'s Forward request was sent as an AJAX request');
        const landing = await returned;
        h.assert(landing, 'The patient lab list\'s Forward post was not redirected to a page');
        const landed = new URL(landing.url());
        h.assert(landed.pathname.endsWith('/lab/ViewDemographicLab') && landed.searchParams.get('providerNo') === provider,
          `After Forward the patient lab list did not return to itself (it was sent to ${landed.pathname})`);
        h.assert(landing.status() === 200, `The patient lab list the Forward post returned to answered HTTP ${landing.status()}`);
        await list.waitForURL(landing.url(), { timeout: TIMEOUT });
        await h.assertNotErrorPage(list, 'the patient lab list after Forward');
      } finally {
        await picker.close().catch(() => {});
      }
      await expectValue(sql, `SELECT IFNULL(GROUP_CONCAT(status),'') FROM providerLabRouting WHERE lab_type='HL7'
        AND lab_no=${listLab.labNo} AND provider_no=${q(recipient.providerNo)}`, 'N',
      'Forward from the patient lab list did not give the recipient one New routing row');
      h.assert(hl7(provider) === senderBefore, 'Forward from the patient lab list changed the sender\'s routing row');
      h.assert(favourites() === favouritesBaseline, 'Forward from the patient lab list changed the sender\'s forwarding favourites');
    } finally {
      await list.close().catch(() => {});
    }
  });
  h.assertStrictPage(recorder);
}

if (require.main === module) {
  runWorkflow(NAME, workflow, { openMaster: false });
}
module.exports = { workflow, STEP };
