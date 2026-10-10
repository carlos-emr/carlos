/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Shared implementation of the Administration-panel stored-markup walk (xss-poison). Two scripts run it, one
// per half of the menu, so each stays well inside its time budget on a busy host:
//   xss-poison-admin-users-billing  (part 1)  and  xss-poison-admin-reports-system  (part 2).
// Each run seeds its own inert-markup rows (payload numbers start at part*100, so concurrent runs and shared
// global lists never collide and inspect() ignores a number it did not create), clicks every menu item of its
// half through the same catalogue as admin-index-links, and asserts per item: literal text visible, no
// `[data-xp]` element in any frame, no script error. Findings are collected and the check fails once.
const h = require('./playwright-harness');
const ui = require('./playwright-ui');
const { catalogueLinks, dedupe } = require('./playwright-link-audit');
const { payload, Findings, Seeder, walkLinks, waitForProviderCache, freeProviderNo, trackServiceScript, fieldIds } = require('./xss-poison-helpers');

// The schedule top bar (#firstTable) repeats inside the panel; those openers have their own checks. It is
// recognised by where the link sits, not by its text: the panel's own menu has items with the same words (its
// Messenger section's "Messages"), and those are this walk's to open. The text list is only a fallback for a
// top-bar link drawn outside #firstTable; Inbox, Tickler and Msg carry a live count suffix ("Tickler3").
const TOP_BAR_ID = 'firstTable';
const TOP_BAR = /^(Schedule|Search|Inbox\d*|U|Tickler\d*|Msg\d*|Consultations\d*|eDoc|Report|Administration|Administration Panel|doctor carlosdoc)$/i;
const SKIP = [
  { match: /update\s*drugref/i }, { match: /database\/document download/i }, { match: /^log\s*out$/i },
];

async function workflow(s, part = 1) {
  const fields = {};
  let n = part === 2 ? 100 : 0;
  const P = (name, max = 255) => { n += 1; fields[n] = name; return payload(n, max); };
  const seed = new Seeder(s.sql, s.cleanup, s.marker);
  const hex = s.marker.slice(-6);
  let provNo;
  await s.step('seed inert-markup rows across the administration tables', async () => {
    provNo = freeProviderNo(s.sql, 800000 + (parseInt(hex, 16) % 99000));
    seed.insert('provider', {
      provider_no: provNo, last_name: P('provider last', 30), first_name: P('provider first', 30), provider_type: 'doctor',
      sex: 'F', specialty: P('provider specialty', 40), team: P('provider team', 20), address: P('provider address', 40),
      phone: '555', work_phone: '555', ohip_no: '', billing_no: '', status: '1', comments: P('provider comments'),
      practitionerNo: '', init: '', job_title: P('provider job title', 100), email: P('provider email', 60),
      title: '', lastUpdateDate: { raw: 'NOW()' }, practitionerNoType: P('provider practitionerNoType'),
    }, { where: `provider_no='${provNo}'` });
    seed.insert('secUserRole', { provider_no: provNo, role_name: 'doctor', activeyn: 1, lastUpdateDate: { raw: 'NOW()' } }, { key: 'id' });
    const grp = `XP${hex}`.slice(0, 8);
    seed.insert('mygroup', { mygroup_no: grp, provider_no: provNo, last_name: P('group member last', 30), first_name: P('group member first', 30), vieworder: '1' },
      { where: `mygroup_no='${grp}' AND provider_no='${provNo}'` });
    seed.insert('secRole', { role_name: P('role name', 60), description: P('role description', 60) }, { key: 'role_no' });
    seed.insert('OscarJob', { name: P('job name'), description: P('job description'), oscarJobTypeId: 1, cronExpression: '0 0 3 1 1 ?', providerNo: '999998', enabled: 0, updated: { raw: 'NOW()' } }, { key: 'id' });
    seed.insert('OscarJobType', { name: P('job type name'), description: P('job type description'), className: 'io.github.carlos_emr.carlos.jobs.OscarOnCallClinic', enabled: 0, updated: { raw: 'NOW()' } }, { key: 'id' });
    const list = seed.insert('LookupList', { name: `xp${hex}`, listTitle: P('lookup list title', 50), description: P('lookup list description'), categoryId: 1, active: 1, createdBy: '999998' }, { key: 'id' });
    seed.insert('LookupListItem', { lookupListId: list, value: P('lookup item value', 50), label: P('lookup item label'), displayOrder: 1, active: 1, createdBy: '999998' }, { key: 'id' });
    // Customize Consult Appointment Instructions edits the list named consultApptInst; find it by that name.
    const consultList = s.sql.value("SELECT id FROM LookupList WHERE name='consultApptInst'");
    h.assert(/^[1-9]\d*$/.test(consultList), 'The consultApptInst lookup list (Customize Consult Appointment Instructions) does not exist');
    seed.insert('LookupListItem', { lookupListId: Number(consultList), value: `xp${hex}`, label: P('consult instruction label'), displayOrder: 99, active: 1, createdBy: '999998' }, { key: 'id' });
    seed.insert('quickList', { quickListName: P('dx quick list name'), createdByProvider: '999998', dxResearchCode: '250', codingSystem: 'icd9' }, { key: 'id' });
    seed.insert('documentDescriptionTemplate', { doctype: 'consult', description: P('document description template'), descriptionShortcut: P('doc template shortcut', 20), provider_no: '999998' }, { key: 'id' });
    seed.insert('ctl_doctype', { module: 'demographic', doctype: P('document type', 60), status: 'A' }, { key: 'id' });
    seed.insert('queue', { name: P('queue name', 40) }, { key: 'id' });
    const fid = seed.insert('eform', { form_name: P('eform name'), file_name: P('eform file name'), subject: P('eform subject'), form_date: { raw: 'CURDATE()' }, form_time: { raw: 'CURTIME()' }, form_creator: '999998', status: 1, form_html: '<html><body>xp</body></html>', showLatestFormOnly: 0, patient_independent: 0 }, { key: 'fid' });
    seed.insert('eform_groups', { fid, group_name: P('eform group name', 20) }, { key: 'id' });
    seed.insert('reportTemplates', { templatetitle: P('report template title', 80), templatedescription: P('report template description'), templatesql: 'SELECT 1', templatexml: '<report id="9990" title="xp" description="xp" active="1"><query>SELECT 1</query><parameters/></report>', active: 1, type: 'sql', uuid: `xp${hex}` }, { key: 'templateid' });
    seed.insert('measurementType', { type: `X${hex}`, typeDisplayName: P('measurement display name'), typeDescription: P('measurement description'), measuringInstruction: P('measuring instruction'), validation: 'Numeric Value Between 1 and 2', createDate: { raw: 'NOW()' } }, { key: 'id' });
    seed.insert('measurementGroup', { name: P('measurement group name', 100), typeDisplayName: P('measurement group type') }, { key: 'id' });
    const templateName = P('encounter template name', 50);
    seed.insert('encountertemplate', { encountertemplate_name: templateName, encountertemplate_value: P('encounter template value'), creator: '999998', createdatetime: { raw: 'NOW()' } }, { where: `encountertemplate_name=${h.sqlString(templateName)}` });
    seed.insert('appointmentType', { name: P('appointment type name', 50), notes: P('appointment type notes', 80), reason: P('appointment type reason', 80), location: P('appointment type location'), resources: '', duration: 15 }, { key: 'id' });
    seed.insert('tickler_category', { category: P('tickler category', 55), description: P('tickler category description'), active: { raw: 'b\'1\'' } }, { key: 'id' });
    seed.insert('billing_payment_type', { payment_type: P('payment type', 25) }, { key: 'id' });
    seed.insert('cssStyles', { name: P('service code style name'), style: 'color:red', status: 'A' }, { key: 'id' });
    seed.insert('clinic_location', { clinic_location_no: `X${hex}`, clinic_no: 1, clinic_location_name: P('billing location', 40) }, { key: 'id' });
    trackServiceScript(seed, seed.insert('consultationServices', { serviceDesc: P('consult service'), active: '1' }, { key: 'serviceId' }));
    seed.insert('Institution', { name: P('institution name'), address: P('institution address'), city: P('institution city', 100) }, { key: 'id' });
    seed.insert('ServiceClient', { name: P('rest client name'), clientKey: `k${hex}`, clientSecret: `s${hex}`, uri: P('rest client uri'), lifetime: 1 }, { key: 'id' });
    seed.insert('billingservice', { service_code: `X${hex}`.slice(0, 6), description: P('billing service description'), value: '1.00', billingservice_date: { raw: 'CURDATE()' }, region: 'ON' }, { key: 'billingservice_no' });
    seed.insert('PreventionsLotNrs', { creationDate: { raw: 'NOW()' }, providerNo: '999998', preventionType: 'Flu', lotNr: P('lot number'), deleted: 0, lastUpdateDate: { raw: 'NOW()' } }, { key: 'id' });
    seed.insert('dsGuidelines', { uuid: `xp${hex}`, title: P('guideline title', 100), version: 1, author: P('guideline author', 60), xml: '<guideline/>', source: 'xp', engine: 'drools' }, { key: 'id' });
    seed.insert('waitingListName', { name: P('waiting list name', 80), group_no: '', provider_no: '999998', create_date: { raw: 'NOW()' }, is_history: 'N' }, { key: 'ID' });
    seed.insert('SystemMessage', { message: P('system message'), creationDate: { raw: 'NOW()' }, expiryDate: { raw: 'DATE_ADD(NOW(), INTERVAL 1 DAY)' } }, { key: 'id' });
  });
  await waitForProviderCache(s);
  const f = new Findings(s.recorder);
  const step = f.stepper(s);
  let admin;
  await step('open Administration from the schedule', async () => {
    ({ page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
      { context: s.context, label: 'administration', recorder: s.recorder, timeout: 20000 }));
    h.assertStrictPage(s.recorder, ['login', 'administration']);
  });
  await step('walk every Administration item and inspect it for injected markup', async () => {
    const all = dedupe((await catalogueLinks(admin, { identity: true }))
      .filter(item => !item.identity.ancestorIds.includes(TOP_BAR_ID) && !TOP_BAR.test(item.text))
      // Located by catalogue position, as before; the identity was needed only to see where each link sits.
      .map(({ identity, ...item }) => item));
    h.assert(all.length > 40, 'Administration offered too few items');
    // Half of the menu per run; the split point is the first item of the Reports group.
    const split = all.findIndex(item => /^Query By Example$/i.test(item.text));
    h.assert(split > 10, 'The Administration menu no longer has the Reports group the split is anchored on');
    const items = part === 2 ? all.slice(split) : all.slice(0, split);
    // Items of this half that must be opened, with the seeded values each is known to show (live runs on the
    // packaged install): a menu that drifts away from them is a MISSING finding, not a quieter pass.
    const E = (...names) => fieldIds(fields, ...names);
    const expect = part === 2
      // Appointment Type List is opened but its seeded row not required: appointment types are served from a
      // 30-minute cache (CacheConfig.APPOINTMENT_TYPES) that a run minutes earlier leaves warm.
      ? [{ match: /^Appointment Type List$/, fields: [] }, { match: /^Manage Lookup Lists$/, fields: E('lookup list title') },
        { match: /^Jobs Management$/, fields: E('job name') }, { match: /^REST Clients$/, fields: E('rest client name') }, { match: /^Messages$/, fields: [] }]
      : [{ match: /^Manage eForms$/, fields: E('eform name', 'eform subject') }, { match: /^Add New Queue$/, fields: E('queue name') },
        { match: /^Manage Payment Type$/, fields: E('payment type') }, { match: /^Add Billing Location$/, fields: E('billing location') },
        // Opened, but its seeded label is not required: LookupListManager serves lists from a 30-minute cache
        // (CacheConfig.LOOKUP_LISTS) that only its own writes evict, so a row INSERTed by SQL is not listed yet.
        { match: /^Customize Consult Appointment Instructions$/, fields: [] }];
    await walkLinks({ context: s.context, recorder: s.recorder, host: admin, items, findings: f, fields, timeout: 40000, label: 'admin', skip: SKIP, expect });
  });
  await step('the walk found no output-encoding defect', async () => { f.assertNone('Administration walk'); });
}

module.exports = { workflow };
