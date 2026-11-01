#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Double-submit check: E-Chart progress note Sign & Save and Save (HIGH severity if it duplicates).
 *
 * User path: Schedule > Search > Master Record > E-Chart > type into the note > Sign & Save
 * (#signSaveImg) or Save (#saveImg). For each rapid activation (dblclick(), two back-to-back clicks,
 * slow-response re-click) the check types ONE note with its own marker text and asserts the owned
 * patient has EXACTLY ONE logical note (distinct casemgmt_note.uuid) carrying that marker and, for Sign
 * & Save, that it is signed. (A textarea swallows Enter as a newline, so there is no double-Enter mode.)
 *
 * Fixtures: the owned FAKE- patient; every casemgmt_note row for that patient carrying the run marker
 * (plus issue links, ext rows, drafts, locks) is deleted and asserted gone. Wave-6 pattern sweep
 * "double-submit".
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { MODES, rapid, settledCount, watchPosts, verdicts, armSlowServer, sleep } = require('./lib/double-submit-helpers');

const {failureMark,consumeExpectedFailure}=require('./lib/concurrency-support');

const NOTE = 'textarea[name="caseNote_note"]';

async function consumeOwnedConflict(s,mark,responses,text,count) {
  const conflicts=responses.filter(response=>response.status()===409);
  if(!conflicts.length)return;
  h.assert(conflicts.length===1&&count===1,'A rapid save has unexplained conflicts or duplicate durable notes');
  const response=conflicts[0],request=response.request(),url=new URL(response.url()),body=new URLSearchParams(request.postData()||'');
  h.assert(url.origin===s.config.baseUrl.origin&&url.pathname===new URL(h.appUrl(s.config.baseUrl,'/CaseManagementEntry')).pathname
    &&request.method()==='POST'&&body.get('demographicNo')===s.patient&&[...body.values()].some(value=>value.includes(text))
    &&Object.hasOwn(response.headers(),h.APPLICATION_HEADER)&&!h.isWafPage(response.status(),await response.text()),'The conflict is not the owned native note submission');
  consumeExpectedFailure(s.recorder,mark,{status:409,path:/\/CaseManagementEntry$/});
}
async function workflow(s) {
  const { sql, patient, marker } = s;
  const likeAll = h.sqlString(`%${marker}-%`);
  const noteIds = () => sql.rows(`SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient} AND note LIKE ${likeAll}`).map(([id]) => id);
  const wipe = () => {
    const ids = noteIds();
    for (const id of ids) h.assert(/^[1-9]\d*$/.test(id), 'Owned note id is invalid');
    if (ids.length) {
      const list = ids.join(',');
      sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${list})`);
      sql.execute(`DELETE FROM casemgmt_note_ext WHERE note_id IN (${list})`);
      sql.execute(`DELETE FROM casemgmt_note_link WHERE note_id IN (${list})`);
      sql.execute(`DELETE FROM casemgmt_note WHERE note_id IN (${list}) AND demographic_no=${patient}`);
    }
    sql.execute(`DELETE FROM eChart WHERE demographicNo=${patient};DELETE FROM casemgmt_tmpsave WHERE demographic_no=${patient}`);
    sql.execute(`DELETE FROM casemgmt_note_lock WHERE demographic_no=${patient}`);
  };
  s.cleanup(() => {
    wipe();
    h.assert(noteIds().length === 0&&sql.value(`SELECT COUNT(*) FROM eChart WHERE demographicNo=${patient}`)==='0', 'Owned chart notes or legacy chart rows were not removed');
  });

  const v = verdicts('chart-note');
  const modes = MODES.filter((mode) => mode.key !== 'doubleEnter');

  for (const [verb, button, route] of [
    ['Sign & Save', '#signSaveImg', /\/CaseManagementEntry$/],
    ['Save', '#saveImg', /\/CaseManagementEntry$/],
  ]) {
    for (const mode of modes) {
      await s.step(`${verb} via ${mode.label} writes one note`, async () => {
        wipe();
        const chart = await s.chart();
        const note = chart.locator(NOTE).first();
        await note.waitFor({ state: 'visible', timeout: 30000 });
        await note.click();
        const text = `${marker}-${verb === 'Save' ? 'S' : 'G'}${mode.tag} double submit`;
        await note.fill(text);
        const posts = watchPosts(chart.context(), route),mark=failureMark(s.recorder),responses=[];
        const observed=response=>{if(response.request().method()==='POST'&&new URL(response.url()).pathname.endsWith('/CaseManagementEntry')&&response.request().frame()===chart.mainFrame())responses.push(response);};
        chart.on('response',observed);
        const disarm = mode.key === 'slowResubmit' ? await armSlowServer(s.context, route) : null;
        try {
        const savingMessage=await chart.evaluate(()=>window.noteSavingMessage);
        h.assert(typeof savingMessage==='string'&&savingMessage.length>0,'Native saving-state message is unavailable');
        const dialogs=await h.withExpectedDialogs(chart,()=>rapid(mode.key,chart.locator(button).first()));
        h.assert(dialogs.length<=1&&dialogs.every(dialog=>dialog.type==='alert'&&dialog.text===savingMessage),'Rapid activation raised an unrelated dialog');
        const like = h.sqlString(`%${text}%`);
        const count = await settledCount(sql,
          `SELECT COUNT(DISTINCT uuid) FROM casemgmt_note WHERE demographic_no=${patient} AND note LIKE ${like}`, { min: 1, quietMs: 3500 });
        if (disarm) await disarm();
        posts.stop();
        const rows = sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient} AND note LIKE ${like}`);
        const signed = sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient} AND note LIKE ${like} AND signed=1`);
        console.log(`    (${posts.seen.length} CaseManagementEntry POST(s); ${rows} row(s); ${signed} signed)`);
        v.record(`${verb} ${mode.label}`, count, { exactly: 1 });
        // Sign & Save must leave its one logical note signed (distinct UUIDs, so revisions of one note are not duplicates).
        if (verb === 'Sign & Save') {
          const signedNotes = Number(sql.value(`SELECT COUNT(DISTINCT uuid) FROM casemgmt_note WHERE demographic_no=${patient} AND note LIKE ${like} AND signed=1`));
          v.record(`${verb} ${mode.label} (signed notes)`, signedNotes, { exactly: 1 });
        }
        await consumeOwnedConflict(s,mark,responses,text,count);
        if (!chart.isClosed()){await chart.waitForLoadState('networkidle');await chart.close();}
        const reopened=await s.chart(),id=sql.value(`SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient} AND note LIKE ${like} ORDER BY note_id DESC LIMIT 1`);
        h.assert(/^[1-9]\d*$/.test(id),'Saved rapid note has no identity');
        const card=reopened.locator(`#encMainDiv #n${id}`);await card.waitFor({state:'visible'});
        const editor=card.locator(NOTE);
        if(!(await editor.isVisible())){
          const [edit]=await Promise.all([reopened.waitForResponse(response=>response.request().method()==='POST'&&new URL(response.url()).pathname.endsWith('/CaseManagementEntry')&&new URLSearchParams(response.request().postData()||'').get('method')==='edit'),card.locator('a[id^="edit"]').first().click()]);
          h.assert(edit.status()===200,'Native Edit refused the saved rapid note');
        }
        await editor.waitFor({state:'visible'});h.assert((await editor.inputValue()).includes(text),'Reopen/Edit lost the exact rapid-save text');
        await reopened.waitForFunction(()=>!window.autoSaveXhr&&!window.autoSaveOperation);
        await reopened.evaluate(()=>{if(typeof clearAutoSaveTimer==='function')clearAutoSaveTimer();});
        await reopened.waitForLoadState('networkidle');
        await reopened.close();await sleep(500);
        }finally{chart.off('response',observed);posts.stop();if(disarm)await disarm();}
      });
    }
  }
  wipe();
  v.finish();
}

module.exports = { workflow,consumeOwnedConflict };
if (require.main === module) runWorkflow('double-submit-chart-note', workflow, { openPatient: true });
