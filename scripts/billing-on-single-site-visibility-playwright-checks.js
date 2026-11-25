#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { randomBytes } = require('node:crypto');
const h = require('./lib/playwright-harness');
const { authenticatedProbe } = require('./lib/authenticated-probe');
const { runWorkflow } = require('./lib/workflow-session');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');
const { checkedDiskDirectory } = require('./billing-on-group-disk-zero-total-playwright-checks');
const { unusedNumber } = require('./billing-on-ohip-simulation-report-playwright-checks');

async function workflow(s) {
  const { sql, marker } = s;
  const q = h.sqlString;
  const noSites = provider => sql.value(`SELECT COUNT(*) FROM providersite WHERE provider_no=${q(provider)}`) === '0';
  const normalAdmin = provider => sql.value(`SELECT
    EXISTS (SELECT 1 FROM secUserRole WHERE provider_no=${q(provider)} AND role_name='admin' AND activeyn=1)
    AND EXISTS (SELECT 1 FROM secUserRole r JOIN secObjPrivilege p ON p.roleUserGroup=r.role_name
      WHERE r.provider_no=${q(provider)} AND r.activeyn=1 AND p.objectName='_billing' AND p.privilege IN ('r','x'))
    AND NOT EXISTS (SELECT 1 FROM secUserRole r JOIN secObjPrivilege p ON p.roleUserGroup=r.role_name
      WHERE r.provider_no=${q(provider)} AND r.activeyn=1 AND p.objectName IN ('_team_access_privacy','_team_billing_only') AND p.privilege='x')`) === '1';
  h.assert(sql.value("SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup='admin' AND objectName='_site_access_privacy' AND privilege='x'") === '1',
    'The seeded administrator role must hold its real site restriction');

  let context = s.context;
  let admin = s.provider;
  // The seeded login combines the admin site restriction and the doctor's ordinary billing grant.
  if (!normalAdmin(admin) || !noSites(admin)) {
    const login = throwawayLoginFixture({ sql, marker, provider: s.provider, testUser: s.config.testUser });
    s.cleanup(() => login.cleanup());
    login.create({ roleNames: ['admin', 'doctor'] });
    admin = login.providerNo;
    context = await h.newContext(s.context.browser(), s.config);
    context.on('page', page => h.wireStrictPage(page, 'billing-on-single-site-visibility', s.recorder));
    await h.login(context, { ...s.config, testUser: login.username }, s.recorder);
  }
  h.assert(normalAdmin(admin) && noSites(admin), 'The administrator must have billing access, no site memberships and no team restriction');

  const doctor = unusedNumber('8', 6, id => sql.value(`SELECT
    (SELECT COUNT(*) FROM provider WHERE provider_no=${q(id)}) + (SELECT COUNT(*) FROM providersite WHERE provider_no=${q(id)})
    + (SELECT COUNT(*) FROM billing_on_filename WHERE providerno=${q(id)})`) !== '0');
  const ohip = unusedNumber('8', 6, id => sql.value(`SELECT COUNT(*) FROM provider WHERE ohip_no=${q(id)}`) !== '0');
  const disk = `${marker}.ohip`, html = `${marker}.html`;
  const diskDir = checkedDiskDirectory(process.env.OHIP_DISK_DIR);
  const contents = new Map([[disk, Buffer.from(`Synthetic billing disk ${marker}\n`)],
    [html, Buffer.from(`<p>Synthetic billing summary ${marker}</p>\n`)]]);
  const createdFiles = new Map();
  s.cleanup(() => {
    for (const [file, original] of createdFiles) {
      const current = fs.lstatSync(file);
      h.assert(current.isFile() && current.dev === original.dev && current.ino === original.ino, 'An owned billing file was replaced');
      fs.unlinkSync(file);
      h.assert(!fs.existsSync(file), 'An owned billing file was not removed');
    }
  });
  const ownDisk = `ohipfilename=${q(disk)} AND creator=${q(doctor)}`;
  h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM billing_on_diskname WHERE ohipfilename=${q(disk)})
    + (SELECT COUNT(*) FROM billing_on_filename WHERE htmlfilename=${q(html)})`) === '0', 'The synthetic billing filenames already exist');
  s.cleanup(() => {
    sql.execute(`START TRANSACTION;
      DELETE f FROM billing_on_filename f JOIN billing_on_diskname d ON d.id=f.disk_id
        WHERE d.ohipfilename=${q(disk)} AND d.creator=${q(doctor)} AND f.providerno=${q(doctor)} AND f.htmlfilename=${q(html)};
      DELETE FROM billing_on_diskname WHERE ${ownDisk} AND NOT EXISTS
        (SELECT 1 FROM billing_on_filename WHERE disk_id=billing_on_diskname.id);
      COMMIT`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM billing_on_diskname WHERE ohipfilename=${q(disk)})
      + (SELECT COUNT(*) FROM billing_on_filename WHERE htmlfilename=${q(html)} OR providerno=${q(doctor)})`) === '0', 'Billing fixture ownership changed during history cleanup');
    h.assert(noSites(doctor), 'The independent physician acquired a site membership');
    sql.execute(`DELETE FROM provider WHERE provider_no=${q(doctor)} AND last_name=${q(marker)} AND ohip_no=${q(ohip)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${q(doctor)}`) === '0', 'The synthetic billing physician was not removed');
  });
  sql.execute(`INSERT INTO provider (provider_no,first_name,last_name,provider_type,sex,ohip_no,status,lastUpdateUser,lastUpdateDate)
    VALUES (${q(doctor)},'Billing',${q(marker)},'doctor','F',${q(ohip)},'1',${q(admin)},NOW())`);
  function writeOwnedFile(name, bytes) {
    const file = path.join(diskDir, name);
    const fd = fs.openSync(file, 'wx', 0o644);
    try {
      createdFiles.set(file, fs.fstatSync(fd));
      fs.writeFileSync(fd, bytes);
    } finally { fs.closeSync(fd); }
  }
  for (const [name, bytes] of contents) writeOwnedFile(name, bytes);
  sql.execute(`START TRANSACTION;
    INSERT INTO billing_on_diskname (monthCode,batchcount,ohipfilename,groupno,creator,claimrecord,createdatetime,status,total)
      VALUES ('A',1,${q(disk)},'000000',${q(doctor)},'1',NOW(),'U','7.25');
    INSERT INTO billing_on_filename (disk_id,htmlfilename,providerohipno,providerno,claimrecord,status,total)
      VALUES (LAST_INSERT_ID(),${q(html)},${q(ohip)},${q(doctor)},'1','U','7.25');
    COMMIT`);
  h.assert(doctor !== admin && noSites(doctor), 'The independent billing physician must have no site memberships');

  await s.step('the site-restricted admin can select the independent physician in both single-site OHIP forms and see the prior billing disk', async () => {
    const simulation = await context.newPage();
    await h.gotoApp(simulation, s.config.baseUrl, '/billing/CA/ON/ViewBillingOHIPsimulation');
    const simulationForm = simulation.locator('form#serviceform');
    await simulationForm.locator('#summaryView').waitFor({ state: 'visible' }); // Only rendered in single-site mode.
    const generation = await context.newPage();
    await h.gotoApp(generation, s.config.baseUrl, '/billing/CA/ON/ViewBillingOHIPreport');
    const generationForm = generation.locator('form[name="form1"]');
    await generationForm.waitFor();
    const physicianOption = `select[name="providers"] option[value="${doctor}"]`;
    const simulationProvider = await simulationForm.locator(physicianOption).count() === 1;
    const generationProvider = await generationForm.locator(physicianOption).count() === 1;
    const row = generation.locator('tbody tr').filter({ has: generation.getByRole('link', { name: disk, exact: true }) });
    const history = await row.count() === 1 && await row.getByRole('link', { name: html, exact: true }).count() === 1;
    h.assert(simulationProvider && generationProvider && history,
      `Single-site OHIP visibility: simulation physician=${simulationProvider}, generation physician=${generationProvider}, historical disk=${history}`);
    for (const form of [simulationForm, generationForm]) {
      await form.locator('select[name="providers"]').selectOption(doctor);
      h.assert(await form.locator('select[name="providers"]').inputValue() === doctor, 'The independent physician could not be selected');
    }
    for (const [name, bytes] of contents) {
      const link = row.getByRole('link', { name, exact: true });
      const download = await authenticatedProbe({ context, config: s.config }, new URL(await link.getAttribute('href'), generation.url()));
      try {
        h.assert(download.status() === 200 && (await download.body()).equals(bytes), 'The historical link did not return its owned billing file');
      } finally { await download.dispose(); }
    }
  });
  await checkTeamHistory(s, { disk, html, doctor, writeOwnedFile });
}

async function checkTeamHistory(s, { disk, html, doctor, writeOwnedFile }) {
  const { sql } = s;
  const q = h.sqlString;
  const marker = `FAKE-PW${randomBytes(8).toString('hex')}`;
  const role = `PW${marker.slice(-16)}`;
  const roleLabel = `${marker} billing team`;
  h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM secRole WHERE role_name=${q(role)})
    + (SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup=${q(role)})`) === '0', 'The synthetic billing role already exists');
  s.cleanup(() => {
    h.assert(sql.value(`SELECT COUNT(*) FROM secUserRole WHERE role_name=${q(role)}`) === '0', 'The synthetic billing role is still assigned');
    sql.execute(`START TRANSACTION;
      DELETE p FROM secObjPrivilege p JOIN secRole r ON r.role_name=p.roleUserGroup
        WHERE r.role_name=${q(role)} AND r.description=${q(roleLabel)} AND p.objectName='_team_access_privacy' AND p.privilege='x';
      DELETE FROM secRole WHERE role_name=${q(role)} AND description=${q(roleLabel)}
        AND NOT EXISTS (SELECT 1 FROM secObjPrivilege WHERE roleUserGroup=${q(role)});
      COMMIT`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM secRole WHERE role_name=${q(role)})
      + (SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup=${q(role)})`) === '0', 'The synthetic team role changed during cleanup');
  });
  sql.execute(`START TRANSACTION;
    INSERT INTO secRole (role_name,description) VALUES (${q(role)},${q(roleLabel)});
    INSERT INTO secObjPrivilege (roleUserGroup,objectName,privilege,priority,provider_no)
      VALUES (${q(role)},'_team_access_privacy','x',0,${q(s.provider)}); COMMIT`);
  const login = throwawayLoginFixture({ sql, marker, provider: s.provider, testUser: s.config.testUser });
  s.cleanup(() => login.cleanup());
  login.create({ roleNames: ['doctor', role] });
  const member = login.providerNo;
  h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${q(member)} AND last_name=${q(marker)}
    AND COALESCE(ohip_no,'')='' AND COALESCE(team,'')=''`) === '1', 'The isolated team reader must start without a biller or a team');
  const context = await h.newContext(s.context.browser(), s.config);
  context.on('page', page => h.wireStrictPage(page, 'billing-on-single-site-visibility', s.recorder));
  await h.login(context, { ...s.config, testUser: login.username }, s.recorder);
  const page = await context.newPage();
  const url = '/billing/CA/ON/ViewBillingOHIPreport';
  const form = page.locator('form[name="form1"]');
  await s.step('a team with no eligible biller gets an empty provider list and cannot create a report', async () => {
    await h.gotoApp(page, s.config.baseUrl, url);
    await form.waitFor();
    h.assert(await form.locator('select[name="providers"] option').count() === 0, 'An empty billing team was offered provider options');
    const create = form.locator('input[type="submit"][name="Submit"]');
    h.assert(await create.count() === 1 && await create.isDisabled(), 'Create Report was available without an eligible provider');
  });

  const ohip = unusedNumber('7', 6, id => sql.value(`SELECT COUNT(*) FROM provider WHERE ohip_no=${q(id)}`) !== '0');
  const memberHtml = `${marker}.html`;
  h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM provider WHERE team=${q(role)})
    + (SELECT COUNT(*) FROM billing_on_filename WHERE htmlfilename=${q(memberHtml)} OR providerno=${q(member)})`) === '0',
    'The synthetic team name or member file already exists');
  s.cleanup(() => {
    sql.execute(`DELETE f FROM billing_on_filename f JOIN billing_on_diskname d ON d.id=f.disk_id
      WHERE d.ohipfilename=${q(disk)} AND d.creator=${q(doctor)} AND f.providerno=${q(member)}
        AND f.providerohipno=${q(ohip)} AND f.htmlfilename=${q(memberHtml)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM billing_on_filename WHERE htmlfilename=${q(memberHtml)} OR providerno=${q(member)}`) === '0',
      'The team member file changed during cleanup');
  });
  const bytes = Buffer.from(`<p>Synthetic team billing summary ${marker}</p>\n`);
  writeOwnedFile(memberHtml, bytes);
  sql.execute(`START TRANSACTION;
    UPDATE provider SET team=${q(role)},ohip_no=${q(ohip)} WHERE provider_no=${q(member)}
      AND last_name=${q(marker)} AND COALESCE(ohip_no,'')='' AND COALESCE(team,'')='';
    INSERT INTO billing_on_filename (disk_id,htmlfilename,providerohipno,providerno,claimrecord,status,total)
      SELECT id,${q(memberHtml)},${q(ohip)},${q(member)},'1','U','3.25' FROM billing_on_diskname
       WHERE ohipfilename=${q(disk)} AND creator=${q(doctor)}; COMMIT`);
  await s.step('a team member on a mixed disk sees their own HTML, with no shared disk download or regeneration', async () => {
    await h.gotoApp(page, s.config.baseUrl, url);
    await form.waitFor();
    h.assert(await form.locator('select[name="providers"] option').count() === 1
      && await form.locator('select[name="providers"]').inputValue() === member, 'The team member is not the sole selectable physician');
    const ownLink = page.getByRole('link', { name: memberHtml, exact: true });
    const row = page.locator('tbody tr').filter({ has: ownLink });
    h.assert(await row.count() === 1, 'The permitted member has no history row');
    h.assert(await page.getByRole('link', { name: disk, exact: true }).count() === 0
      && await page.getByRole('link', { name: html, exact: true }).count() === 0
      && await row.locator('input[value="R"]').count() === 0, 'The mixed disk exposed a shared file, another member’s summary or regeneration');
    const download = await authenticatedProbe({ context, config: s.config }, new URL(await ownLink.getAttribute('href'), page.url()));
    try { h.assert(download.status() === 200 && (await download.body()).equals(bytes), 'The permitted team summary did not download'); }
    finally { await download.dispose(); }
  });
}

if (require.main === module) runWorkflow('billing-on-single-site-visibility', workflow, { openPatient: false, openMaster: false });
module.exports = { workflow };
