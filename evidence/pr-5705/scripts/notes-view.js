#!/usr/bin/env node
// Capture the eChart note list (grouped form entries) for the synthetic patient. Usage: node notes-view.js <before|after>
'use strict';
const fs = require('node:fs'); const path = require('node:path'); const { execFileSync } = require('node:child_process');
const SCRIPTS = '/home/user/build/after/scripts';
const h = require(path.join(SCRIPTS, 'lib/playwright-harness'));
const { openMasterRecord } = require(path.join(SCRIPTS, 'master-record-tabs-playwright-checks'));
const { openChart, waitForNavbars } = require(path.join(SCRIPTS, 'echart-navbar-modules-playwright-checks'));
const phase = process.argv[2]; const OUT = path.join('/home/user/build/visual', phase);
const sql = q => execFileSync('docker', ['exec', 'carlos-deb-validation', 'mariadb', '-u', 'root', 'carlos', '-N', '-B', '-e', q], { encoding: 'utf8' }).trim();
(async () => {
  const env = Object.fromEntries(execFileSync('docker', ['exec', 'carlos-deb-validation', 'bash', '-c', '. /root/suite-env.sh; env'], { encoding: 'utf8' })
    .split('\n').filter(l => /^(TEST_USER|TEST_PASSWORD|TEST_PIN)=/.test(l)).map(l => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]));
  Object.assign(process.env, env, { BASE_URL: 'https://127.0.0.1/carlos' });
  const config = h.readConfig(); config.chromePath = '';
  const patient = JSON.parse(fs.readFileSync('/home/user/build/visual/state.json', 'utf8')).patient;
  sql(`DELETE FROM casemgmt_note_lock WHERE demographic_no=${patient}`);
  const browser = await h.launchBrowser(config);
  const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1280, height: 1400 }, timezoneId: 'America/Toronto', locale: 'en-CA' });
  const problems = [];
  context.on('page', page => { page.on('pageerror', e => problems.push(`pageerror ${e.message}`)); page.on('response', r => { if (r.status() >= 500) problems.push(`HTTP ${r.status()} ${r.url()}`); }); });
  const recorder = h.createRecorder();
  const schedule = await h.login(context, config, recorder);
  const { masterPage } = await openMasterRecord(context, schedule, recorder, { searchTerm: 'FAKE-GROWTHVIS', preferredDemographicNo: String(patient), timeout: 30000 });
  const chart = await openChart(context, masterPage, recorder, 30000);
  await waitForNavbars(chart, 30000);
  await chart.getByRole('button', { name: 'Load All Notes' }).click().catch(() => {});
  await chart.waitForTimeout(4000);
  const growth = await chart.evaluate(() => [...document.querySelectorAll('a, div, span, td')]
    .filter(e => e.children.length === 0 && /Growth 0-36/i.test(e.textContent))
    .map(e => { let p = e, path = []; while (p && path.length < 6) { path.push((p.id ? '#' + p.id : p.tagName) + (p.className && typeof p.className === 'string' ? '.' + p.className.split(' ')[0] : '')); p = p.parentElement; }
      const box = e.closest('[id^="nc"], .note, [id^="n"]');
      return { text: e.textContent.trim().replace(/\s+/g, ' '), path: path.join(' < '), around: (e.closest('div') || e).innerText.trim().replace(/\s+/g, ' ').slice(0, 160), visible: !!(e.offsetWidth || e.offsetHeight) }; }));
  await chart.screenshot({ path: path.join(OUT, '09-echart-all-notes.png'), fullPage: true });
  const notesBox = chart.locator('#encMainDiv').first();
  if (await notesBox.count()) await notesBox.screenshot({ path: path.join(OUT, '10-echart-note-list.png') }).catch(() => {});
  fs.writeFileSync(path.join(OUT, 'notes.json'), JSON.stringify({ growth, problems }, null, 2));
  console.log(JSON.stringify({ phase, growth, problems }, null, 2));
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
