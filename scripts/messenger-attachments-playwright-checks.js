#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Messenger attachments: chart items rendered to PDF, and transferred chart items.
 * User path: Master Record ▸ E-Chart ▸ Messenger "+" ▸ Attach Patient
 * (messenger/attachmentFrameset ▸ messenger/PreviewPDF) ▸ Preview / tick items ▸
 * Attach (messenger/Doc2PDF) ▸ Send to self; Schedule ▸ Msg ▸ message ▸ Attachment
 * (messenger/ViewPDFAttach ▸ messenger/ViewPDFFile). And for a received message
 * carrying transferred chart items: message ▸ Attachment (messenger/ViewAttach) ▸
 * Save Attachments (messenger/AdjustAttachments) ▸ Compose ▸ Send.
 * Asserts the preview and the downloaded attachment are real PDF bytes, the sent
 * messagetbl row holds both rendered PDFs (pdfattachment) and the transferred items
 * (attachment), its messagelisttbl/msgDemoMap rows, and that the received message
 * renders the attachment.
 * Fixtures: an owned FAKE- patient (runWorkflow), an owned messenger group holding
 * the test provider (the recipient), and two SQL-delivered messages (one with two
 * stored PDFs, one with a FAKE-PW transferred item); cleanup deletes only those rows
 * and the messages carrying the marker. Steps that hit known defects run last.
 * Not covered (no UI entry): messenger/Transfer/SelectItems and Transfer/PostItems.
 * Implements coverage plan §3.4 messenger-attachments.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const TIMEOUT = 30000;

async function settle(page) {
  await page.waitForLoadState('domcontentloaded', { timeout: TIMEOUT });
  await page.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
  await h.assertNotErrorPage(page, 'messenger page');
}

async function clickAndLoad(page, locator, pathname) {
  await Promise.all([
    page.waitForResponse(r => r.request().isNavigationRequest() && r.frame() === page.mainFrame()
      && new URL(r.url()).pathname.endsWith(pathname), { timeout: TIMEOUT }),
    locator.click(),
  ]);
  await settle(page);
}

// Observe the bytes of a streamed PDF without changing what the browser receives:
// a PDF navigation becomes a download in headless Chromium and has no readable body.
async function capturePdf(page, pathname, trigger) {
  let captured;
  const handler = async route => {
    const response = await route.fetch();
    captured = { status: response.status(), type: response.headers()['content-type'] || '',
      body: await response.body(), post: route.request().postData() || '' };
    await route.fulfill({ response });
  };
  const pattern = `**${pathname}`;
  await page.context().route(pattern, handler);
  try {
    await trigger();
    const deadline = Date.now() + TIMEOUT;
    while (!captured && Date.now() < deadline) await new Promise(resolve => setTimeout(resolve, 100));
  } finally {
    await page.context().unroute(pattern, handler);
  }
  h.assert(captured, `${pathname} was not requested`);
  h.assert(captured.status === 200, `${pathname} returned HTTP ${captured.status}`);
  h.assert(captured.body.subarray(0, 5).toString('latin1') === '%PDF-', `${pathname} did not stream PDF bytes`);
  const tail = captured.body.subarray(-1024).toString('latin1');
  h.assert(/%%EOF\s*$/.test(tail), `${pathname} appended content after the PDF trailer`);
  return captured;
}

async function workflow(s) {
  const provider = h.sqlString(s.provider);
  const patient = Number(s.patient);
  const like = h.sqlString(`%${s.marker}%`);
  let groupId;
  s.cleanup(() => {
    const ids = s.sql.rows(`SELECT messageid FROM messagetbl WHERE thesubject LIKE ${like}`).map(([id]) => Number(id));
    if (ids.length) {
      const list = ids.join(',');
      s.sql.execute(`DELETE FROM msgDemoMap WHERE messageID IN (${list});
        DELETE FROM messagelisttbl WHERE message IN (${list});
        DELETE FROM messagetbl WHERE messageid IN (${list}) AND thesubject LIKE ${like}`);
      h.assert(s.sql.value(`SELECT (SELECT COUNT(*) FROM messagetbl WHERE messageid IN (${list}))
        + (SELECT COUNT(*) FROM messagelisttbl WHERE message IN (${list}))
        + (SELECT COUNT(*) FROM msgDemoMap WHERE messageID IN (${list}))`) === '0', 'Owned messages were not removed');
    }
    if (groupId) {
      s.sql.execute(`DELETE FROM groupMembers_tbl WHERE groupID=${groupId} AND provider_No=${provider};
        DELETE FROM groups_tbl WHERE groupID=${groupId} AND groupDesc=${h.sqlString(s.marker)}`);
      h.assert(s.sql.value(`SELECT (SELECT COUNT(*) FROM groups_tbl WHERE groupID=${groupId})
        + (SELECT COUNT(*) FROM groupMembers_tbl WHERE groupID=${groupId})`) === '0', 'Owned messenger group was not removed');
    }
  });
  // An owned group makes the test provider a recipient without touching the shared
  // contact registry (groupID 0) the other messenger checks enrol and remove.
  groupId = Number(s.sql.value(`INSERT INTO groups_tbl(parentID,groupDesc) VALUES(0,${h.sqlString(s.marker)}); SELECT LAST_INSERT_ID()`));
  h.assert(groupId > 0, 'Messenger group fixture was not created');
  s.sql.execute(`INSERT INTO groupMembers_tbl(groupID,provider_No,facilityId) VALUES(${groupId},${provider},0)`);
  const location = s.sql.value('SELECT locationId FROM oscarcommlocations WHERE current1=1 LIMIT 1');
  h.assert(/^\d+$/.test(location), 'No current messenger location is configured');

  const pdfSubject = `${s.marker} chart PDFs`;
  const itemSubject = `${s.marker} transferred items`;
  const forwardSubject = `${s.marker} forwarded items`;
  const itemName = `${s.marker} item`;
  const sent = subject => s.sql.rows(`SELECT messageid, attachment IS NOT NULL, pdfattachment IS NOT NULL
    FROM messagetbl WHERE thesubject=${h.sqlString(subject)}`);

  async function sendToSelf(page, subject) {
    await page.locator(`#member_group_${groupId}`).check();
    h.assert(await page.locator(`input[name="provider"].member_group_${groupId}[value^="${s.provider}-"]`).isChecked(),
      'Ticking the owned group did not select the test provider');
    await page.locator('#subject').fill(subject);
    const editor = page.locator('.toastui-editor-ww-container .ProseMirror').first();
    await editor.click();
    await page.keyboard.type(`${s.marker} body`);
    await clickAndLoad(page, page.locator('button[type="submit"]', { hasText: /Send Message/i }), '/messenger/CreateMessage');
    await expectValue(s.sql, `SELECT COUNT(*) FROM messagetbl WHERE thesubject=${h.sqlString(subject)}`, '1', 'Send did not write one messagetbl row');
    const [[id]] = sent(subject);
    h.assert(s.sql.value(`SELECT GROUP_CONCAT(status) FROM messagelisttbl WHERE message=${id} AND provider_no=${provider}`) === 'new',
      'The message was not delivered once, unread, to the test provider');
    return id;
  }

  let compose;
  let attach;
  let main;
  const openAttachments = async () => {
    // A frameset has no body text, so the generic popup helper's blank-page guard
    // cannot judge it; the main frame's form is asserted instead.
    [attach] = await Promise.all([
      s.context.waitForEvent('page', { timeout: TIMEOUT }),
      compose.locator('input[name="attachDemo"]').click(),
    ]);
    h.wireStrictPage(attach, 'messenger-attachments', s.recorder);
    await attach.waitForURL(/\/messenger\/attachmentFrameset/, { timeout: TIMEOUT });
    await attach.waitForLoadState('domcontentloaded');
    h.assert(new URL(attach.url()).pathname.endsWith('/messenger/attachmentFrameset'), 'Attach Patient did not open the attachment frameset');
    await attach.locator('frame[name="main"]').waitFor({ state: 'attached' });
    main = attach.frame({ name: 'main' });
    h.assert(main, 'The attachment frameset has no main frame');
    await main.waitForURL(/\/messenger\/PreviewPDF/, { timeout: TIMEOUT });
    await main.locator('form[action$="/messenger/Doc2PDF"]').waitFor({ timeout: TIMEOUT });
    h.assert(new URL(main.url()).pathname.endsWith('/messenger/PreviewPDF'), 'The main frame did not load messenger/PreviewPDF');
  };

  // A small, valid PDF standing in for one Doc2PDF stored earlier (offsets computed).
  const minimalPdf = () => {
    const objects = ['<< /Type /Catalog /Pages 2 0 R >>', '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
      '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] >>'];
    let out = '%PDF-1.4\n';
    const offsets = objects.map((body, n) => { const at = out.length; out += `${n + 1} 0 obj\n${body}\nendobj\n`; return at; });
    const xref = out.length;
    out += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`
      + offsets.map(at => `${String(at).padStart(10, '0')} 00000 n \n`).join('')
      + `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
    return Buffer.from(out, 'latin1');
  };
  const deliver = (subject, columns, values) => {
    const id = s.sql.value(`INSERT INTO messagetbl(thedate,theime,themessage,thesubject,sentby,sentto,sentbyNo,sentByLocation,type${columns})
      VALUES(CURDATE(),CURTIME(),'FAKE-PW received',${h.sqlString(subject)},'FAKE-PW sender','FAKE-PW recipient',${provider},${location},0${values});
      SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), 'Received-message fixture was not created');
    s.sql.execute(`INSERT INTO messagelisttbl(message,provider_no,status,remoteLocation,destinationFacilityId,sourceFacilityId)
      VALUES(${id},${provider},'new',${location},0,0)`);
    return id;
  };

  // Open the transferred-items tree the way a reader does: table node, then item node.
  async function expandItem(items) {
    await items.locator('span.treeNode', { hasText: `${s.marker} table` }).click();
    const item = items.locator('span.treeNode', { hasText: itemName });
    await item.waitFor({ state: 'visible' });
    await item.click();
    await items.getByText('FAKE-PW detail', { exact: true }).waitFor({ state: 'visible' });
  }

  let inbox;
  const openMessage = async id => {
    await clickAndLoad(inbox, inbox.locator(`a[href*="/messenger/ViewMessage?messageID=${id}&"]`).first(), '/messenger/ViewMessage');
    await inbox.locator('#msgSubject').waitFor();
  };
  const toInbox = () => clickAndLoad(inbox, inbox.locator('a.btn[href*="/messenger/DisplayMessages"]:visible,'
    + ' a.nav-link[href*="/messenger/DisplayMessages"]:not([href*="boxType"]):not([href*="orderby"]):visible').first(), '/messenger/DisplayMessages');

  // Opens the stored-PDF list for a message and downloads entry `index`.
  async function downloadStoredPdf(id, expectedTitles, index) {
    await openMessage(id);
    const viewer = await s.popup(inbox, inbox.locator('a[href*="ViewPDFAttach?attachId="]'), 'messenger-pdf-attachment');
    await settle(viewer);
    h.assert(new URL(viewer.url()).pathname.endsWith('/messenger/ViewPDFAttach'), 'The attachment link did not open ViewPDFAttach');
    const rows = viewer.locator('form[action$="/messenger/ViewPDFFile"] tr');
    const titles = (await rows.allInnerTexts()).map(text => text.trim());
    h.assert(titles.length === expectedTitles.length && expectedTitles.every((title, n) => titles[n].includes(title)),
      'The attachment viewer did not list the stored PDFs by title');
    const captured = await capturePdf(viewer, '/messenger/ViewPDFFile', () => rows.nth(index).locator('button[type="submit"]').click());
    h.assert(new URLSearchParams(captured.post).get('file_id') === String(index), 'The download did not request the chosen attachment');
    h.assert(/application\/pdf/.test(captured.type), 'ViewPDFFile did not answer application/pdf');
    await viewer.close();
    return captured.body;
  }

  await s.step('a received message with stored PDFs lists them and ViewPDFFile downloads the exact bytes', async () => {
    const pdf = minimalPdf();
    const xml = ` <PDF><FILE_ID>0</FILE_ID><STATUS>OK</STATUS><TITLE>${s.marker} first</TITLE><CONTENT>${pdf.toString('base64')}</CONTENT></PDF>`
      + ` <PDF><FILE_ID>1</FILE_ID><STATUS>OK</STATUS><TITLE>${s.marker} second</TITLE><CONTENT>${pdf.toString('base64')}</CONTENT></PDF>`;
    const id = deliver(`${s.marker} received PDFs`, ',pdfattachment', `,${h.sqlString(xml)}`);
    // Waits for the popup to leave about:blank (or for an in-place navigation).
    ({ page: inbox } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('a:has(#oscar_new_msg)').first(),
      { context: s.context, recorder: s.recorder, label: 'messenger-inbox', timeout: TIMEOUT }));
    await settle(inbox);
    h.assert(/\/messenger\/DisplayMessages/.test(inbox.url()), 'The Msg link did not open the messenger inbox');
    const body = await downloadStoredPdf(id, [`${s.marker} first`, `${s.marker} second`], 1);
    h.assert(body.equals(pdf), 'ViewPDFFile did not return the stored PDF byte for byte');
  });

  let adjustedItems;
  await s.step('chart Messenger + opens a compose form with the patient attached and the owned group offered', async () => {
    const chart = await s.chart();
    compose = await s.popup(chart, chart.locator('a[onclick*="/messenger/SendDemoMessage?demographic_no="]').first(), 'messenger-compose');
    await settle(compose);
    await compose.locator('#subject').waitFor();
    h.assert(await compose.locator('input[name="demographic_no"]').inputValue() === String(patient), 'Compose did not carry the chart patient');
    await compose.locator(`#member_group_${groupId}`).waitFor({ state: 'attached' });
  });

  await s.step('Attach Patient opens the frameset whose PreviewPDF page lists the patient items', async () => {
    await openAttachments();
    const uris = await main.locator('input[name="uriArray"]').evaluateAll(inputs => inputs.map(input => input.value));
    h.assert(uris.some(uri => uri.includes(`/demographic/DemographicPdfLabel?demographic_no=${patient}`))
      && uris.some(uri => uri.includes(`/rx/ViewPrintDrugProfile2?demographic_no=${patient}`)),
      'The attachment page did not offer the patient information and prescriptions of the chart patient');
    h.assert((await main.locator('body').innerText()).toLowerCase().includes(s.marker.toLowerCase()), 'The attachment page did not name the patient');
    // Close asks for confirmation and closes the whole frameset window.
    const dialogs = await h.withExpectedDialogs(attach, async () => {
      await Promise.all([attach.waitForEvent('close', { timeout: TIMEOUT }), main.locator('button[onclick*="MSGS.exitConfirm"]').click()]);
    });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Close did not ask once for confirmation');
    h.assert(sent(pdfSubject).length === 0, 'Opening the attachment page wrote a message');
  });

  let itemsPage;
  await s.step('a received message with transferred items renders them in ViewAttach', async () => {
    const xml = `<root><table name="${s.marker} table"><item itemId="0" name="${itemName}" value="FAKE-PW value" removable="false">`
      + '<content><fld name="FAKE-PW field" value="FAKE-PW detail"/></content><data/></item></table></root>';
    const id = deliver(itemSubject, ',attachment', `,${h.sqlString(xml)}`);
    await toInbox();
    await openMessage(id);
    const items = await s.popup(inbox, inbox.locator('a[href*="ViewAttach?attachId="]'), 'messenger-items');
    itemsPage = items;
    await settle(items);
    h.assert(new URL(items.url()).pathname.endsWith('/messenger/ViewAttach'), 'The attachment link did not open ViewAttach');
    await expandItem(items);
  });

  // Kept after every other provable step: the hand-over page (msgSearchDemo.jsp)
  // runs write2Parent on load even with no patient chosen and throws (see report).
  await s.step('Save Attachments keeps the items and hands over to the patient search without a script error', async () => {
    h.assert(itemsPage && !itemsPage.isClosed(), 'The transferred-items window is not open');
    const items = itemsPage;
    // Save Attachments keeps the selection in the session and hands over to the
    // patient search (AdjustAttachments redirects to DemographicLinkMsg).
    const [adjust] = await Promise.all([
      items.waitForResponse(r => new URL(r.url()).pathname.endsWith('/messenger/AdjustAttachments'), { timeout: TIMEOUT }),
      items.locator('form[action$="/messenger/AdjustAttachments"] input[type="submit"]').click(),
    ]);
    h.assert(adjust.status() === 302 && /\/demographic\/DemographicLinkMsg$/.test(adjust.headers().location || ''),
      'Save Attachments did not hand over to the patient search');
    adjustedItems = true;
    // Let the hand-over page run its own scripts before the window goes away.
    await Promise.race([
      items.waitForEvent('close', { timeout: TIMEOUT }),
      items.waitForURL(/\/demographic\/DemographicLinkMsg/, { timeout: TIMEOUT, waitUntil: 'load' }),
    ]).catch(() => {});
    if (!items.isClosed()) await items.close();
  });

  await s.step('composing after Save Attachments sends the transferred items, and the received copy renders them', async () => {
    h.assert(adjustedItems, 'No transferred items were saved');
    // Compose from the message view: the inbox page itself drops pending attachments.
    await clickAndLoad(inbox, inbox.locator('a[href*="/messenger/ViewCreateMessage"]:visible').first(), '/messenger/ViewCreateMessage');
    await inbox.locator('#subject').waitFor();
    h.assert(/Attachments/i.test(await inbox.locator('#scrollNumber1').innerText()), 'Compose did not show the pending attachment');
    const id = await sendToSelf(inbox, forwardSubject);
    const stored = s.sql.value(`SELECT attachment FROM messagetbl WHERE messageid=${id}`);
    h.assert(stored.includes(`name="${itemName}"`) && stored.includes('FAKE-PW detail'), 'The new message did not store the transferred item');
    h.assert(s.sql.value(`SELECT pdfattachment IS NULL FROM messagetbl WHERE messageid=${id}`) === '1', 'A PDF attachment leaked onto the message');
    await toInbox();
    await openMessage(id);
    const items = await s.popup(inbox, inbox.locator('a[href*="ViewAttach?attachId="]'), 'messenger-sent-items');
    await settle(items);
    await expandItem(items);
    await items.close();
  });

  // From here on every step posts rendered chart HTML to messenger/Doc2PDF, which
  // the front-door WAF blocks in this build (see report); kept last so all of the
  // above is proven first. The steps assert the correct behaviour.
  await s.step('Preview posts the rendered patient page to Doc2PDF and streams a PDF without attaching', async () => {
    await openAttachments();
    const preview = main.locator('button[data-preview-uri*="/demographic/DemographicPdfLabel"]');
    const captured = await capturePdf(attach, '/messenger/Doc2PDF', () => preview.click());
    const post = new URLSearchParams(captured.post);
    h.assert(post.get('isPreview') === 'true' && (post.get('srcText') || '').length > 0, 'Preview did not post the rendered patient page');
    h.assert(sent(pdfSubject).length === 0, 'Previewing wrote a message');
    // Let the frames finish loading so closing the window aborts nothing.
    await attach.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
    await attach.close();
  });

  await s.step('ticking the patient information and prescriptions and Attach renders both and returns to compose', async () => {
    await openAttachments();
    const rows = main.locator('tr', { has: main.locator('input[name="indexArray"]') });
    for (const route of ['/demographic/DemographicPdfLabel', '/rx/ViewPrintDrugProfile2']) {
      await rows.filter({ has: main.locator(`input[name="uriArray"][value*="${route}"]`) }).locator('input[name="indexArray"]').check();
    }
    const posts = [];
    const onRequest = request => {
      if (request.method() === 'POST' && new URL(request.url()).pathname.endsWith('/messenger/Doc2PDF')) posts.push(request.postData() || '');
    };
    const since = s.recorder.requestFailures.length;
    s.context.on('request', onRequest);
    try {
      await Promise.all([
        attach.waitForEvent('close', { timeout: 60000 }),
        main.locator('button[name="Attach"]').click(),
      ]);
    } finally { s.context.off('request', onRequest); }
    // generatePreviewPDF.jsp re-submits itself from an inline script between renders,
    // which cancels its own csrfguard script load; consume exactly those aborts.
    for (let i = s.recorder.requestFailures.length - 1; i >= since; i--) {
      const entry = s.recorder.requestFailures[i];
      if (entry.label === 'messenger-attachments' && entry.resourceType === 'script' && entry.errorText === 'net::ERR_ABORTED'
        && new URL(entry.url).pathname.endsWith('/csrfguard')) s.recorder.requestFailures.splice(i, 1);
    }
    h.assert(posts.length === 2 && posts.every(body => new URLSearchParams(body).get('isAttaching') === 'true'),
      `Attach did not render exactly the two ticked items (saw ${posts.length} renders)`);
    await compose.locator('#pdf-attachment-indicator').waitFor({ state: 'visible' });
  });

  let pdfMessageId;
  await s.step('sending stores both rendered items on the message, linked to the patient', async () => {
    pdfMessageId = await sendToSelf(compose, pdfSubject);
    h.assert(s.sql.value(`SELECT demographic_no FROM msgDemoMap WHERE messageID=${pdfMessageId}`) === String(patient),
      'The chart-sent message was not linked to the patient');
    const stored = s.sql.value(`SELECT CAST(pdfattachment AS CHAR) FROM messagetbl WHERE messageid=${pdfMessageId}`);
    const entries = stored.match(/<PDF>.*?<\/PDF>/g) || [];
    h.assert(entries.length === 2, 'The message did not store one entry per ticked item');
    const content = (entries[0].match(/<CONTENT>([^<]*)<\/CONTENT>/) || [])[1] || '';
    h.assert(entries[0].includes('<STATUS>OK</STATUS>') && Buffer.from(content, 'base64').subarray(0, 5).toString('latin1') === '%PDF-',
      'The patient information was not stored as a rendered PDF');
    await compose.close();
  });

  await s.step('the received message downloads the rendered patient information PDF', async () => {
    await toInbox();
    // The viewer must list each stored entry by the title Attach stored for it.
    const stored = s.sql.value(`SELECT CAST(pdfattachment AS CHAR) FROM messagetbl WHERE messageid=${pdfMessageId}`);
    const titles = [...stored.matchAll(/<TITLE>([^<]*)<\/TITLE>/g)].map(match => match[1].trim());
    h.assert(titles.length === 2 && titles.every(Boolean), 'The sent message did not store a title for each rendered item');
    await downloadStoredPdf(pdfMessageId, titles, 0);
  });

  // Asserted separately: in this build the prescriptions page fails to render
  // (Doc2PDF XHTML parse error) and is stored as a BAD "(N/A)" entry (see report).
  await s.step('the current prescriptions item was rendered to a PDF too', async () => {
    const stored = s.sql.value(`SELECT CAST(pdfattachment AS CHAR) FROM messagetbl WHERE messageid=${pdfMessageId}`);
    h.assert((stored.match(/<STATUS>OK<\/STATUS>/g) || []).length === 2, 'The current prescriptions attachment was stored as a failed render');
  });
}

if (require.main === module) runWorkflow('messenger-attachments', workflow, { openPatient: true });
module.exports = { workflow };
