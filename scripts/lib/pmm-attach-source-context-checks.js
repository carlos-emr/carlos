/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

const h = require('./playwright-harness');
const ui = require('./playwright-ui');
const { waitForNavbars } = require('../echart-navbar-modules-playwright-checks');
const { readPatientState, assertPatientStateUnchanged } = require('../echart-two-patient-isolation-playwright-checks');
const q = h.sqlString;
const TIMEOUT = 30000;
const NOTE = '#caseManagementEntryForm textarea[name="caseNote_note"]';

/** Refuse a parent deletion if any of the fixture's relationships acquired another owner. */
function assertOwnedPrograms(sql, programs, { provider, facility, patients, accessId, accessProgram, accessType }) {
    for (const { id, name } of programs) {
        h.assert(sql.value(`SELECT COUNT(*) FROM program WHERE id=${id} AND name=${q(name)}
            AND facilityId=${facility}`) === '1', 'PMM context program ownership changed');
        h.assert(sql.value(`SELECT COUNT(*) FROM program_provider WHERE program_id=${id}
            AND (provider_no IS NULL OR provider_no<>${q(provider)})`) === '0',
        'Another provider joined a PMM context program; retaining it');
        h.assert(sql.value(`SELECT COUNT(*) FROM admission WHERE program_id=${id}
            AND (client_id IS NULL OR client_id NOT IN (${patients.join(',')}))`) === '0',
        'Another patient joined a PMM context program; retaining it');
        h.assert(sql.rows(`SELECT id,access_type_id,all_roles FROM program_access WHERE program_id=${id}`)
            .every(row => id === accessProgram && row[0] === accessId && row[1] === accessType && row[2] === '1'),
        'An unexpected policy was attached to a PMM context program; retaining it');
    }
    if (accessId) h.assert(sql.rows(`SELECT program_id,access_type_id,all_roles FROM program_access WHERE id=${accessId}`)
        .every(row => row[0] === accessProgram && row[1] === accessType && row[2] === '1'),
    'The owned read-only policy changed program or definition; retaining it');
}

/** Adds exactly one owned note; every preexisting clinical row must remain byte-for-byte equal. */
function assertOnlyCopiedNote(before, after, copy, label) {
    const copyJson = JSON.stringify(copy);
    h.assert(after.notes.filter(row => JSON.stringify(row) === copyJson).length === 1,
        `${label}: the acknowledged copied note is missing or repeated`);
    const withoutCopy = { ...after, notes: after.notes.filter(row => JSON.stringify(row) !== copyJson) };
    assertPatientStateUnchanged(before, withoutCopy, label);
}

const PROVENANCE_COLUMNS = ['program_no', 'provider_no', 'reporter_caisi_role', 'reporter_program_team'];

/** Compare the chosen source's authored provenance using the installed SELECT * column order. */
function assertCopiedProvenance(sourceNotes, copy, columns, sourceId) {
    h.assert(new Set(columns).size === columns.length
        && ['note_id', ...PROVENANCE_COLUMNS].every(name => columns.includes(name)),
    'The installed note schema cannot identify copied provenance');
    h.assert(Array.isArray(copy) && copy.length === columns.length
        && sourceNotes.every(row => Array.isArray(row) && row.length === columns.length),
    'The copied provenance snapshot is incomplete');
    const sources = sourceNotes.filter(row => row[columns.indexOf('note_id')] === sourceId);
    h.assert(sources.length === 1, 'The chosen PMM source note is missing or ambiguous');
    for (const name of PROVENANCE_COLUMNS) {
        const index = columns.indexOf(name);
        h.assert(copy[index] === sources[0][index], `The PMM copy changed its source ${name}`);
    }
}

function assertStagedDefaults(actual, expected) {
    h.assert(Array.isArray(expected) && expected.length === 1
        && JSON.stringify(actual) === JSON.stringify(expected),
    'The staged PMM default changed; retaining all fixture rows');
}

/** A returned chart may acquire one lock; it may not alter an existing editor's lock. */
function assertCopyLocks(before, after, { provider, patient, note, session }) {
    h.assert(typeof session === 'string' && session.split('.')[0], 'The native PMM session cookie is missing');
    const sessionIds = new Set([session, session.split('.')[0]]);
    const oldIds = new Set(before.map(row => row[0]));
    h.assert(before.every(row => after.some(current => JSON.stringify(current) === JSON.stringify(row))),
        'Native PMM copy changed a preexisting editor lock');
    const added = after.filter(row => !oldIds.has(row[0]));
    h.assert(added.length <= 1 && added.every(row => row[1] === provider && row[2] === patient
        && row[3] === note && sessionIds.has(row[4])), 'Native PMM copy acquired an unrelated editor lock');
}

async function checkPmmAttachSourceContext(s, { context, login, target, ownedPatient, ownChartRows, saveNote, settle }) {
    const { sql, marker, config, provider } = s;
    // Use existing cached roles and grants. The fixture never reloads or changes a shared policy.
    const pair = sql.rows(`SELECT r.role_no,h.role_no FROM secRole r CROSS JOIN secRole h
        WHERE r.role_no<>h.role_no AND r.role_name NOT LIKE 'FAKE%' AND h.role_name NOT LIKE 'FAKE%'
        AND r.role_name=TRIM(r.role_name) AND h.role_name=TRIM(h.role_name)
        AND r.role_name<>'' AND h.role_name<>''
        AND NOT EXISTS (SELECT 1 FROM default_role_access d JOIN access_type a ON a.access_id=d.access_id
            WHERE d.role_id=r.role_no AND a.name=CONCAT('read ',LOWER(h.role_name),' notes'))
        ORDER BY r.role_no,h.role_no LIMIT 1`)[0];
    h.assert(pair && pair.every(id => /^[1-9]\d*$/.test(id)),
        'No preexisting cached role pair can prove the PMM source-program refusal');
    const readOnlyType = sql.rows("SELECT access_id FROM access_type WHERE name='note-read-only' AND type='access'")[0]?.[0];
    h.assert(/^[1-9]\d*$/.test(readOnlyType || ''), 'The install has no note-read-only access type');
    const facilities = sql.rows(`SELECT f.id FROM Facility f JOIN provider_facility p ON p.facility_id=f.id
        WHERE p.provider_no=${q(login.providerNo)} AND f.disabled=0 ORDER BY f.id`);
    h.assert(facilities.length === 1 && /^[1-9]\d*$/.test(facilities[0][0]),
        'The owned PMM login needs one unambiguous current facility');
    const facility = facilities[0][0];
    const noteColumns = sql.rows('SHOW COLUMNS FROM casemgmt_note').map(row => row[0]);
    h.assert(new Set(noteColumns).size === noteColumns.length
        && ['note_id', ...PROVENANCE_COLUMNS].every(name => noteColumns.includes(name)),
    'The installed note schema cannot identify copied provenance');
    const originalPolicy = JSON.stringify(sql.rows('SELECT * FROM default_role_access ORDER BY id'));
    const defaults = () => sql.rows(`SELECT id,provider_no,program_id,signnote FROM provider_default_program
        WHERE provider_no=${q(login.providerNo)} ORDER BY id`);
    const originalDefaults = defaults();
    const names = ['origin', 'default', 'readonly'].map(label => `${marker}-pmm-context-${label}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM program WHERE name IN (${names.map(q).join(',')})`) === '0',
        'An intended PMM context program already exists');
    const programs = [];
    const bodies = new Set();
    const copiedIds = [];
    let source;
    let accessId;
    let changedDefaults = false;
    let stagedDefaults;
    // Register before the first owned insert, including recovery after an interrupted setup.
    s.cleanup(() => {
        const recovered = sql.rows(`SELECT id,name,facilityId FROM program WHERE name IN (${names.map(q).join(',')})`);
        h.assert(recovered.every(row => row[2] === facility)
            && new Set(recovered.map(row => row[1])).size === recovered.length,
        'PMM context program recovery is ambiguous');
        for (const [id, name] of recovered) if (!programs.some(row => row.id === id)) programs.push({ id, name });
        const accessProgram = programs.find(row => row.name === names[2])?.id;
        if (!accessId && accessProgram) {
            const recoveredAccess = sql.rows(`SELECT id,access_type_id,all_roles FROM program_access WHERE program_id=${accessProgram}`);
            h.assert(recoveredAccess.length <= 1 && recoveredAccess.every(row => row[1] === readOnlyType && row[2] === '1'),
                'Owned PMM read-only policy recovery is ambiguous');
            accessId = recoveredAccess[0]?.[0];
        }
        assertOwnedPrograms(sql, programs, { provider: login.providerNo, facility,
            patients: [source, target].filter(Boolean), accessId, accessProgram, accessType: readOnlyType });
        h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${q(login.providerNo)}
            AND last_name=${q(marker)}`) === '1', 'The PMM login changed owner; retaining its defaults');
        if (changedDefaults) assertStagedDefaults(defaults(), stagedDefaults);
        if (accessId) h.assert(sql.value(`SELECT COUNT(*) FROM program_access_roles WHERE id=${accessId}`) === '0',
            'The owned read-only policy acquired role links; retaining all fixture rows');
        const candidates = bodies.size ? sql.rows(`SELECT note_id,demographic_no,note FROM casemgmt_note
            WHERE demographic_no=${target} AND note IN (${[...bodies].map(q).join(',')})`) : [];
        h.assert(candidates.length <= 2 && candidates.every(row => row[1] === target && bodies.has(row[2])),
            'Unexpected copies were added to the PMM context target; retaining them');
        if (copiedIds.length) {
            h.assert(sql.rows(`SELECT note_id,demographic_no,note FROM casemgmt_note WHERE note_id IN (${copiedIds.join(',')})`)
                .every(row => row[1] === target && bodies.has(row[2])), 'A tracked PMM copy changed owner');
        }
        for (const [id] of candidates) {
            h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_issue_notes WHERE note_id=${id})
                + (SELECT COUNT(*) FROM casemgmt_note_ext WHERE note_id=${id})
                + (SELECT COUNT(*) FROM casemgmt_note_link WHERE note_id=${id})`) === '0',
            'A PMM copy acquired clinical relationships; retaining it');
        }
        for (const [id] of candidates) sql.execute(`DELETE FROM casemgmt_note WHERE note_id=${id}
            AND demographic_no=${target} AND note IN (${[...bodies].map(q).join(',')})`);
        if (changedDefaults) {
            sql.execute(`DELETE FROM provider_default_program WHERE provider_no=${q(login.providerNo)}`);
            for (const [id, owner, program, sign] of originalDefaults) sql.execute(`INSERT INTO provider_default_program
                (id,provider_no,program_id,signnote) VALUES (${id},${q(owner)},${program},${sign === null ? 'NULL' : Number(sign)})`);
            h.assert(JSON.stringify(defaults()) === JSON.stringify(originalDefaults),
            'The owned PMM login default was not restored');
        }
        if (accessId) {
            sql.execute(`DELETE FROM program_access WHERE id=${accessId} AND access_type_id=${q(readOnlyType)}`);
        }
        for (const { id, name } of programs) {
            sql.execute(`DELETE FROM admission WHERE program_id=${id} AND client_id IN (${[source, target].filter(Boolean).join(',')});
                DELETE FROM program_provider WHERE program_id=${id} AND provider_no=${q(login.providerNo)};
                DELETE FROM program WHERE id=${id} AND name=${q(name)} AND facilityId=${facility}`);
            h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM program WHERE id=${id})
                + (SELECT COUNT(*) FROM program_provider WHERE program_id=${id})
                + (SELECT COUNT(*) FROM admission WHERE program_id=${id})
                + (SELECT COUNT(*) FROM program_access WHERE program_id=${id})`) === '0',
            'Owned PMM context program residue');
        }
        h.assert(JSON.stringify(sql.rows('SELECT * FROM default_role_access ORDER BY id')) === originalPolicy,
            'Shared reporter-role grants changed');
    });
    source = ownedPatient(s, 'PmmSource');
    ownChartRows(s, [source]);
    for (const [index, name] of names.entries()) {
        const id = h.insertId(sql, `INSERT INTO program
            (facilityId,name,type,maxAllowed,programStatus,transgender,firstNation,alcohol,
             physicalHealth,mentalHealth,housing,exclusiveView,ageMin,ageMax,
             enableEncounterTime,enableEncounterTransportationTime,enableOCAN)
            VALUES (${facility},${q(name)},'service',2,'active',0,0,0,0,0,0,'none',0,150,0,0,0)`, 'PMM context program');
        h.assert(/^[1-9]\d*$/.test(id), 'The owned PMM context program key is invalid');
        programs.push({ id, name });
        const role = index === 0 ? pair[1] : pair[0];
        sql.execute(`INSERT INTO program_provider(program_id,provider_no,role_id,team_id)
            VALUES (${id},${q(login.providerNo)},${role},0);
            INSERT INTO admission(client_id,program_id,provider_no,admission_date,
                admission_from_transfer,discharge_from_transfer,admission_status,lastUpdateDate)
            VALUES (${source},${id},${q(login.providerNo)},NOW(),0,0,'current',NOW()),
                (${target},${id},${q(login.providerNo)},NOW(),0,0,'current',NOW())`);
    }
    const [origin, fallback, readonly] = programs.map(row => row.id);
    accessId = h.insertId(sql, `INSERT INTO program_access(program_id,access_type_id,all_roles)
        VALUES (${readonly},${q(readOnlyType)},1)`, 'owned PMM read-only policy');
    h.assert(/^[1-9]\d*$/.test(accessId), 'The owned read-only policy key is invalid');
    changedDefaults = true;
    sql.execute(`DELETE FROM provider_default_program WHERE provider_no=${q(login.providerNo)};
        INSERT INTO provider_default_program(provider_no,program_id,signnote)
        VALUES (${q(login.providerNo)},${fallback},0)`);
    stagedDefaults = defaults();
    h.assert(stagedDefaults.length === 1 && stagedDefaults[0][1] === login.providerNo
        && stagedDefaults[0][2] === fallback && stagedDefaults[0][3] === '0',
    'The staged PMM fallback default is not the exact owned fixture row');
    const sourceCharts = [];
    const sourceNotes = [];
    const sourceTokens = [];
    for (const [label, program] of [['origin', origin], ['default', fallback]]) {
        const page = await context.newPage();
        await h.gotoApp(page, config.baseUrl, `/encounter/IncomingEncounter?demographicNo=${source}
            &providerNo=${login.providerNo}&appointmentNo=0&programId=${program}`.replace(/\s+/g, ''));
        await settle(page, `PMM ${label} source chart`);
        await waitForNavbars(page, TIMEOUT);
        const id = await saveNote(page, sql, source, `${marker}-pmm-${label}-context-copy`);
        const token = await page.locator('#caseManagementEntryForm input[name="chartContext"]').inputValue();
        const body = sql.value(`SELECT note FROM casemgmt_note WHERE note_id=${id} AND demographic_no=${source}`);
        h.assert(body.includes(`${marker}-pmm-${label}-context-copy`), 'The owned PMM source body is absent');
        bodies.add(body);
        sourceCharts.push(page);
        sourceNotes.push(id);
        sourceTokens.push(token);
    }
    h.assert(sourceTokens[0] && sourceTokens[1] && sourceTokens[0] !== sourceTokens[1],
        'Two source programs did not issue independent signed contexts');
    h.assert(sql.value(`SELECT reporter_caisi_role FROM casemgmt_note WHERE note_id=${sourceNotes[0]}`) === pair[1],
        'The nondefault source does not carry the distinct hidden reporter role');
    const targetChart = await context.newPage();
    await h.gotoApp(targetChart, config.baseUrl, `/encounter/IncomingEncounter?demographicNo=${target}
        &providerNo=${login.providerNo}&appointmentNo=0&programId=${readonly}`.replace(/\s+/g, ''));
    await settle(targetChart, 'PMM read-only target chart');
    await waitForNavbars(targetChart, TIMEOUT);
    const targetToken = await targetChart.evaluate(() => window.carlosChartContext
        || document.forms.caseManagementEntryForm?.elements.chartContext?.value || '');
    h.assert(targetToken, 'The native target chart did not issue its own context');
    const snapshot = () => ({ source: readPatientState(sql, source), target: readPatientState(sql, target),
        locks: sql.rows(`SELECT id,provider_no,demographic_no,note_id,session_id,lock_acquired,ip_address
            FROM casemgmt_note_lock WHERE demographic_no IN (${source},${target}) ORDER BY id`) });
    const editorState = async () => Promise.all([...sourceCharts, targetChart].map(page => page.evaluate(() => {
        const form = document.forms.caseManagementEntryForm;
        return { patient: form?.elements.demographicNo?.value ?? null,
            token: form?.elements.chartContext?.value ?? window.carlosChartContext ?? null,
            note: form?.elements.noteId?.value ?? null, text: form?.elements.caseNote_note?.value ?? null };
    })));
    const unchanged = (before, label) => {
        const after = snapshot();
        assertPatientStateUnchanged(before.source, after.source, `${label} source`);
        assertPatientStateUnchanged(before.target, after.target, `${label} target`);
        h.assert(JSON.stringify(after.locks) === JSON.stringify(before.locks), `${label} changed editor locks`);
    };
    const nativeCopy = async (index, tokenless) => {
        const [page] = await Promise.all([context.waitForEvent('page', { timeout: TIMEOUT }),
            sourceCharts[index].locator('#attachNoteImg').click()]);
        await settle(page, 'PMM source popup');
        if (tokenless) {
            const legacy = new URL(page.url());
            legacy.searchParams.delete('sourceChartContext');
            await page.goto(legacy.href, { waitUntil: 'domcontentloaded' });
            await settle(page, 'Tokenless PMM source popup');
        }
        const field = page.locator('input[name="sourceChartContext"]');
        h.assert(await field.count() === (tokenless ? 0 : 1), 'The native popup has a missing or blank legacy context control');
        if (!tokenless) h.assert(await field.inputValue() === sourceTokens[index], 'The source popup changed its nondefault program');
        await page.locator('input[name="criteria.lastName"]').fill(marker);
        await page.locator('input[name="criteria.firstName"]').fill('PatientCharlie');
        await page.locator('input[name="criteria.searchOutsideDomain"]').check();
        const [searched] = await Promise.all([page.waitForRequest(request => request.method() === 'POST'
            && new URL(request.url()).pathname.endsWith('/PMmodule/ClientSearch2'), { timeout: TIMEOUT }),
        ui.clickAndAwaitReload(page, page.locator('input[type="submit"][value="search"]'),
            { label: 'PMM signed/legacy native search', timeout: TIMEOUT })]);
        const searchFields = new URLSearchParams(searched.postData() || '');
        h.assert(tokenless ? !searchFields.has('sourceChartContext')
            : searchFields.get('sourceChartContext') === sourceTokens[index], 'The native search lost or fabricated a source context');
        await settle(page, 'PMM native search results');
        const baseline = snapshot();
        const beforeEditor = JSON.stringify(await editorState());
        const [posted] = await Promise.all([page.waitForRequest(request => request.method() === 'POST'
            && new URL(request.url()).pathname.endsWith('/encounter/IncomingEncounter'), { timeout: TIMEOUT }),
        ui.clickAndAwaitReload(page, page.locator(`a.attach-note-to-client[data-client="${target}"]`).first(),
            { label: 'PMM source-context copy', timeout: TIMEOUT })]);
        const fields = new URLSearchParams(posted.postData() || '');
        h.assert(fields.get('noteId') === sourceNotes[index] && fields.get('demographicNo') === target
            && fields.get('selectId') === target && /\S/.test(fields.get('CSRF-TOKEN') || ''), 'Native PMM copy changed patient/note identity');
        h.assert(tokenless ? !fields.has('sourceChartContext') : fields.get('sourceChartContext') === sourceTokens[index],
            'The native copy lost its signed source program or submitted a blank legacy token');
        h.assert(!fields.has('chartContext'), 'The source token was mistaken for the different target patient context');
        await settle(page, 'PMM returned target chart');
        await waitForNavbars(page, TIMEOUT);
        const copies = sql.rows(`SELECT note_id,uuid,appointmentNo,history,note FROM casemgmt_note
            WHERE demographic_no=${target} AND note=${q([...bodies][index])}`);
        h.assert(copies.length === 1 && copies[0][1] !== sql.value(`SELECT uuid FROM casemgmt_note WHERE note_id=${sourceNotes[index]}`)
            && copies[0][2] === '0' && copies[0][3] === copies[0][4], 'The native PMM copy retained a source identity, visit or old history');
        copiedIds.push(copies[0][0]);
        const after = snapshot();
        const copiedRow = sql.rows(`SELECT * FROM casemgmt_note WHERE note_id=${copies[0][0]}`)[0];
        // Native notes may have NULL/zero teams. Match that actual value exactly;
        // this fixture does not claim coverage of copying a nonzero team.
        assertCopiedProvenance(baseline.source.notes, copiedRow, noteColumns, sourceNotes[index]);
        assertPatientStateUnchanged(baseline.source, after.source, 'Native copy source');
        assertOnlyCopiedNote(baseline.target, after.target, copiedRow, 'Native copy target');
        const returned = await page.evaluate(() => {
            const form = document.forms.caseManagementEntryForm;
            return { patient: form?.elements.demographicNo?.value, note: form?.elements.noteId?.value };
        });
        h.assert(returned.patient === target && /^(?:0|-1|[1-9]\d*)$/.test(returned.note || ''),
            'The returned native PMM editor changed patient or note identity');
        if (!['0', '-1'].includes(returned.note)) h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note
            WHERE note_id=${returned.note} AND demographic_no=${target}`) === '1',
        'The returned editor note is outside the PMM target');
        const sessions = (await context.cookies(config.baseUrl.href)).filter(cookie => cookie.name === 'JSESSIONID');
        h.assert(sessions.length === 1 && sessions[0].value, 'The native PMM copy has no unique authenticated session');
        assertCopyLocks(baseline.locks, after.locks,
            { provider: login.providerNo, patient: target, note: returned.note, session: sessions[0].value });
        h.assert(JSON.stringify(await editorState()) === beforeEditor, 'A native copy replaced either source editor draft');
    };
    await s.step('native PMM copy keeps its nondefault source program and tokenless searches omit the source control', async () => {
        await nativeCopy(0, false);
        await nativeCopy(1, true);
    });
    await s.step('PMM forged, foreign and hidden source contexts and read-only target refuse without clinical or draft changes', async () => {
        const csrf = await ui.csrfTokenPresent(sourceCharts[0]);
        const token = sourceTokens[0];
        const forged = token.slice(0, -1) + (token.endsWith('A') ? 'B' : 'A');
        for (const [label, sourceToken, targetContext] of [
            ['forged source', forged, null], ['other patient source', targetToken, null],
            ['hidden reporter in default source', sourceTokens[1], null],
            ['read-only target program', sourceTokens[0], targetToken],
        ]) {
            const before = snapshot();
            const beforeEditor = JSON.stringify(await editorState());
            const form = { PEAttach: 'yes', noteId: sourceNotes[0], selectId: target, demographicNo: target,
                appointmentNo: '0', sourceChartContext: sourceToken, 'CSRF-TOKEN': csrf };
            if (targetContext) form.chartContext = targetContext;
            const response = await context.request.post(h.appUrl(config.baseUrl, '/encounter/IncomingEncounter'), {
                form, headers: { 'CSRF-TOKEN': csrf }, maxRedirects: 0, failOnStatusCode: false,
            });
            try {
                h.assert(response.status() === 403 && Object.hasOwn(response.headers(), h.APPLICATION_HEADER),
                    `PMM ${label} did not return application HTTP 403`);
                const body = await response.text();
                h.assert(!/ModSecurity|<center>nginx<\/center>/i.test(body)
                    && ['origin', 'default'].every(name => !body.includes(`${marker}-pmm-${name}-context-copy`)),
                `PMM ${label} leaked note text or was blocked before the application`);
            } finally { await response.dispose(); }
            unchanged(before, label);
            h.assert(JSON.stringify(await editorState()) === beforeEditor, `PMM ${label} changed the source drafts`);
        }
    });
}

module.exports = { checkPmmAttachSourceContext, assertOwnedPrograms, assertOnlyCopiedNote,
    assertStagedDefaults, assertCopyLocks, assertCopiedProvenance };
