#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

async function workflow(s) {
  const q = h.sqlString;
  const date = '2091-02-28';
  let appointment;
  s.cleanup(() => {
    if (!appointment) return;
    h.assert(s.sql.value(`SELECT COUNT(*) FROM appointment WHERE appointment_no=${appointment} AND name=${q(s.marker)}`) === '1',
      'Jasper appointment ownership changed');
    s.sql.execute(`DELETE FROM appointment WHERE appointment_no=${appointment} AND name=${q(s.marker)}`);
    h.assert(s.sql.value(`SELECT COUNT(*) FROM appointment WHERE appointment_no=${appointment}`) === '0',
      'Jasper appointment fixture remains');
  });
  appointment = s.sql.value(`INSERT INTO appointment
    (provider_no,appointment_date,start_time,end_time,name,demographic_no,notes,reason,location,resources,type,style,billing,status,creator,remarks)
    VALUES (${q(s.provider)},${q(date)},'09:12:00','09:27:00',${q(s.marker)},${s.patient},'','','','','','','','A',${q(s.provider)},'');
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(appointment), 'Jasper fixture did not receive an identifier');
  for (const style of ['labDaySheet.xml', 'billDaySheet.xml']) {
    await s.step(`${style} downloads the owned appointment through the fixed PDF route`, async () => {
      const response = await s.context.request.get(h.appUrl(s.config.baseUrl,
        `/report/printLabDaySheetAction?input_date=${date}&xmlStyle=${style}`));
      h.assert(response.status() === 200, `Jasper report returned HTTP ${response.status()}`);
      h.assert((response.headers()['content-type'] || '').startsWith('application/pdf'), 'Jasper content type is not PDF');
      const bytes = await response.body();
      h.assert(bytes.subarray(0, 5).toString('ascii') === '%PDF-', 'Jasper report has no PDF signature');
      h.assert(/%%EOF\s*$/.test(bytes.subarray(-64).toString('ascii')), 'Jasper PDF has trailing or incomplete output');
      const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-fixed-jasper-'));
      try {
        const file = path.join(directory, 'report.pdf');
        fs.writeFileSync(file, bytes, { mode: 0o600 });
        const text = execFileSync('pdftotext', ['-layout', file, '-'], { encoding: 'utf8', timeout: 15000 });
        h.assert(text.includes(s.marker), 'Jasper PDF omitted the owned appointment');
      } finally { fs.rmSync(directory, { recursive: true, force: true }); }
      await response.dispose();
    });
  }
  await s.step('no appointments returns explicit no-content and malformed dates return an error', async () => {
    const empty = await s.context.request.get(h.appUrl(s.config.baseUrl,
      '/report/printLabDaySheetAction?input_date=2091-03-01&xmlStyle=labDaySheet.xml'));
    h.assert(empty.status() === 204 && (await empty.body()).length === 0, 'Empty Jasper result was not explicit no-content');
    await empty.dispose();
    const invalid = await s.context.request.get(h.appUrl(s.config.baseUrl,
      '/report/printLabDaySheetAction?input_date=2091-02-31&xmlStyle=labDaySheet.xml'));
    h.assert(invalid.status() === 400, 'Malformed Jasper date was not rejected');
    await invalid.dispose();
    h.assert(s.sql.value(`SELECT status FROM appointment WHERE appointment_no=${appointment}`) === 'A',
      'Jasper printing changed appointment state');
  });
}
if (require.main === module) runWorkflow('report-fixed-jasper', workflow, {
  openMaster: false,
  preflight() { execFileSync('pdftotext', ['-v'], { stdio: 'pipe', timeout: 5000 }); },
});
module.exports = { workflow };
