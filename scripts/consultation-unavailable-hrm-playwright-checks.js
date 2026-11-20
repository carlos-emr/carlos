#!/usr/bin/env node
/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
// Owned referrals and HRM sources. Do not run concurrently with other browser checks:
// ownedReferrals temporarily adds a service and specialist to clinic-wide pickers.
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { csrfTokenPresent } = require('./lib/playwright-ui');
const { ownedReferrals, openConsultationsList, openFromList, save, UPDATED } = require('./lib/consultation-referral-fixture');
const { createOwnedHrmReport } = require('./lib/gap-records-hrm-fixture');

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const referrals = ownedReferrals(s);
  const reason = `${marker} unavailable HRM`;
  const requestId = referrals.seed({ reason });
  const missing = createOwnedHrmReport({ sql, marker, suffix: 'deleted', description: `${marker} deleted title`, reportText: 'Synthetic deleted report' });
  const hidden = createOwnedHrmReport({ sql, marker, suffix: 'hidden', description: `${marker} hidden title`, reportText: 'Synthetic unlinked report' });
  s.cleanup(() => { missing.remove(); hidden.remove(); });
  const missingId = missing.create(); const hiddenId = hidden.create();
  const attach = report => sql.value(`INSERT INTO consultdocs (requestId,document_no,doctype,attach_date,provider_no)
    VALUES (${requestId},${report},'H',CURDATE(),${h.sqlString(provider)}); SELECT LAST_INSERT_ID()`);
  const removedRelationship = attach(missingId); const hiddenRelationship = attach(hiddenId);
  sql.execute(`DELETE FROM HRMDocument WHERE id=${missingId}`);
  const active = id => sql.value(`SELECT COUNT(*) FROM consultdocs WHERE id=${id} AND deleted IS NULL`);
  let list; let form;
  await s.step('ordinary Update retains missing and permission-hidden relationships', async () => {
    list = await openConsultationsList(s);
    form = await openFromList(s, list, requestId, reason);
    h.assert(await form.locator('#removeUnavailableHrm').isVisible(), 'The missing source has no explicit removal control');
    const text = await form.locator('body').innerText();
    h.assert(!text.includes(`${marker} hidden title`) && !text.includes(`${marker} deleted title`), 'An unavailable title was disclosed');
    await save(form, 'update', UPDATED);
    h.assert(active(removedRelationship) === '1' && active(hiddenRelationship) === '1', 'An ordinary save detached an unavailable relationship');
    await form.close();
    form = await openFromList(s, list, requestId, reason);
  });
  let selection; let csrf;
  await s.step('GET and a tokenless POST cannot detach the selected relationship', async () => {
    selection = await form.locator('#removeUnavailableHrm').getAttribute('data-selection');
    csrf = await csrfTokenPresent(form);
    const route = h.appUrl(s.config.baseUrl, '/documentManager/removeUnavailableHrm');
    const get = await s.context.request.get(route);
    h.assert(get.status() === 405, `GET returned ${get.status()}`); await get.dispose();
    const post = await s.context.request.post(route, { form: { selection } });
    h.assert(post.status() === 403, `A tokenless POST returned ${post.status()}`); await post.dispose();
    h.assert(active(removedRelationship) === '1', 'A refused request detached the relationship');
  });
  await s.step('Cancel keeps unsaved text; confirmed removal affects only the missing relationship', async () => {
    const notes = form.locator('[name="clinicalInformation"]');
    await notes.fill(`${marker} unsaved notes`);
    const button = form.locator('#removeUnavailableHrm');
    const dialogs = await h.withExpectedDialogs(form, () => button.click(), { accept: false });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm' && active(removedRelationship) === '1', 'Cancel did not preserve the relationship');
    const [post] = await Promise.all([
      form.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith('/documentManager/removeUnavailableHrm')),
      h.withExpectedDialogs(form, () => button.click()),
    ]);
    h.assert(post.status() === 200 && (await post.json()).success === true, 'Removal was not confirmed');
    h.assert(active(removedRelationship) === '0' && active(hiddenRelationship) === '1', 'Removal did not preserve the hidden existing report');
    h.assert(await notes.inputValue() === `${marker} unsaved notes`, 'Removal lost unsaved consultation edits');
    h.assert(await button.isHidden(), 'The confirmed removal control was not retired');
  });
  await s.step('replaying the old selection preserves a newly attached relationship', async () => {
    const reattached = attach(missingId);
    const post = await s.context.request.post(h.appUrl(s.config.baseUrl, '/documentManager/removeUnavailableHrm'),
      { form: { selection, 'CSRF-TOKEN': csrf } });
    h.assert(post.status() === 200 && (await post.json()).success === true, 'An idempotent replay was not confirmed'); await post.dispose();
    h.assert(active(reattached) === '1' && active(hiddenRelationship) === '1', 'An old selection removed a newer relationship');
  });
}

if (require.main === module) runWorkflow('consultation-unavailable-hrm', workflow);
module.exports = { workflow };
