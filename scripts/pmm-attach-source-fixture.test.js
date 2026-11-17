/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const { assertOwnedPrograms, assertOnlyCopiedNote, assertStagedDefaults, assertCopyLocks, assertCopiedProvenance }
    = require('./lib/pmm-attach-source-context-checks');

const programs = [{ id: '11', name: 'FAKE-PW-owned-origin' }, { id: '12', name: 'FAKE-PW-owned-readonly' }];
const ownership = { provider: '899991', facility: '1', patients: ['901', '902'],
    accessId: '31', accessProgram: '12', accessType: '41' };

function repository(change = '') {
    return {
        value(query) {
            if (query.includes('FROM program WHERE')) return change === 'name' ? '0' : '1';
            if (query.includes('FROM program_provider')) return change === 'provider' ? '1' : '0';
            if (query.includes('FROM admission')) return change === 'patient' ? '1' : '0';
            assert.fail(`Unexpected ownership query: ${query}`);
        },
        rows(query) {
            if (query.includes('WHERE program_id=11')) return change === 'foreign-policy' ? [['99', '41', '1']] : [];
            if (query.includes('WHERE program_id=12')) return change === 'definition'
                ? [['31', '42', '1']] : change === 'roles' ? [['31', '41', '0']] : [['31', '41', '1']];
            if (query.includes('WHERE id=31')) return [[change === 'moved-policy' ? '999' : '12', '41', '1']];
            assert.fail(`Unexpected policy query: ${query}`);
        },
    };
}

test('owned PMM programs require one exact linked read-only definition', () => {
    assert.doesNotThrow(() => assertOwnedPrograms(repository(), programs, ownership));
});

for (const change of ['name', 'provider', 'patient', 'foreign-policy', 'definition', 'roles', 'moved-policy']) {
    test(`PMM parent cleanup refuses ${change} ownership drift before deletion`, () => {
        let reachedDeletion = false;
        assert.throws(() => {
            assertOwnedPrograms(repository(change), programs, ownership);
            reachedDeletion = true;
        }, /ownership changed|retaining it/);
        assert.equal(reachedDeletion, false);
    });
}

test('a native copy adds one note while every existing clinical row stays exact', () => {
    const before = { notes: [['5', 'FAKE existing note']], cpp: [['7', 'FAKE existing CPP']], drafts: [['8', 'FAKE draft']] };
    const copied = ['9', 'FAKE copied note'];
    assert.doesNotThrow(() => assertOnlyCopiedNote(before,
        { ...before, notes: [...before.notes, copied] }, copied, 'Native PMM copy'));
});

for (const change of ['existing-note', 'cpp', 'draft', 'missing-section', 'missing-old-note', 'duplicate-copy']) {
    test(`native PMM copy snapshot rejects ${change} changes`, () => {
        const before = { notes: [['5', 'FAKE existing note']], cpp: [['7', 'FAKE CPP']], drafts: [['8', 'FAKE draft']] };
        const copied = ['9', 'FAKE copied note'];
        const after = structuredClone({ ...before, notes: [...before.notes, copied] });
        if (change === 'existing-note') after.notes[0][1] = 'FAKE changed';
        if (change === 'cpp') after.cpp[0][1] = 'FAKE changed';
        if (change === 'draft') after.drafts[0][1] = 'FAKE changed';
        if (change === 'missing-section') delete after.drafts;
        if (change === 'missing-old-note') after.notes.shift();
        if (change === 'duplicate-copy') after.notes.push(copied);
        assert.throws(() => assertOnlyCopiedNote(before, after, copied, 'Native PMM copy'),
            /rows changed|snapshot is incomplete|missing or repeated/);
    });
}

test('cleanup accepts only the exact captured owned default row including its identity and sign flag', () => {
    const staged = [['91', '899991', '12', '0']];
    assert.doesNotThrow(() => assertStagedDefaults(structuredClone(staged), staged));
    for (const actual of [[], [...staged, ['92', '899991', '13', '0']],
        [['92', '899991', '12', '0']], [['91', '899992', '12', '0']],
        [['91', '899991', '13', '0']], [['91', '899991', '12', '1']]]) {
        let reachedDeletion = false;
        assert.throws(() => { assertStagedDefaults(actual, staged); reachedDeletion = true; }, /retaining all fixture rows/);
        assert.equal(reachedDeletion, false);
    }
    assert.throws(() => assertStagedDefaults([], undefined), /retaining all fixture rows/);
});

const oldLock = ['21', '899991', '901', '5', 'owned-session', '2026-10-25 12:00:00', '127.0.0.1'];
const ownTargetLock = ['22', '899991', '902', '0', 'owned-session', '2026-10-25 12:01:00', '127.0.0.1'];
const lockOwner = { provider: '899991', patient: '902', note: '0', session: 'owned-session' };

test('native copies preserve old locks and may acquire one exact returned-target editor lock', () => {
    assert.doesNotThrow(() => assertCopyLocks([oldLock], [structuredClone(oldLock)], lockOwner));
    assert.doesNotThrow(() => assertCopyLocks([oldLock], [oldLock, ownTargetLock], lockOwner));
});

for (const stored of ['owned-session.routeA', 'owned-session']) {
    test(`native PMM lock owner accepts the observed routed cookie or its exact core: ${stored}`, () => {
        const lock = [...ownTargetLock];
        lock[4] = stored;
        assert.doesNotThrow(() => assertCopyLocks([oldLock], [oldLock, lock],
            { ...lockOwner, session: 'owned-session.routeA' }));
    });
}

test('native PMM lock owner rejects a different route suffix despite the same session core', () => {
    const lock = [...ownTargetLock];
    lock[4] = 'owned-session.routeB';
    assert.throws(() => assertCopyLocks([oldLock], [oldLock, lock],
        { ...lockOwner, session: 'owned-session.routeA' }), /unrelated editor lock/);
});

for (const change of ['old-missing', 'old-renewed', 'foreign-provider', 'source-patient', 'other-note', 'other-session', 'two-added']) {
    test(`native PMM lock accounting rejects ${change}`, () => {
        const after = structuredClone([oldLock, ownTargetLock]);
        if (change === 'old-missing') after.shift();
        if (change === 'old-renewed') after[0][5] = '2026-10-25 12:02:00';
        if (change === 'foreign-provider') after[1][1] = '899992';
        if (change === 'source-patient') after[1][2] = '901';
        if (change === 'other-note') after[1][3] = '6';
        if (change === 'other-session') after[1][4] = 'foreign-session';
        if (change === 'two-added') after.push(['23', ...ownTargetLock.slice(1)]);
        assert.throws(() => assertCopyLocks([oldLock], after, lockOwner), /editor lock/);
    });
}

// Deliberately use a different schema order from the production baseline so the
// check must use the installed column names rather than hard-coded offsets.
const provenanceColumns = ['note', 'reporter_program_team', 'program_no', 'note_id', 'provider_no', 'reporter_caisi_role'];
const chosenSource = ['FAKE chosen source', null, '11', '5', '899991', '7'];
const otherSource = ['FAKE other source', '0', '12', '6', '899992', '8'];
const provenanceCopy = ['FAKE chosen source', null, '11', '19', '899991', '7'];

test('native copy provenance matches the chosen source snapshot in actual schema order including SQLNULL team', () => {
    assert.doesNotThrow(() => assertCopiedProvenance([otherSource, chosenSource], provenanceCopy, provenanceColumns, '5'));
});

for (const name of ['program_no', 'provider_no', 'reporter_caisi_role', 'reporter_program_team']) {
    test(`native copy provenance rejects rewriting ${name} to the target or a default`, () => {
        const copy = [...provenanceCopy];
        copy[provenanceColumns.indexOf(name)] = name === 'reporter_program_team' ? '0' : '999';
        assert.throws(() => assertCopiedProvenance([chosenSource], copy, provenanceColumns, '5'),
            new RegExp(`changed its source ${name}`));
    });
}

for (const change of ['source-missing', 'source-ambiguous', 'source-truncated', 'copy-truncated', 'schema-missing', 'schema-duplicate']) {
    test(`native copy provenance refuses ${change} evidence`, () => {
        let sources = [chosenSource];
        let copy = provenanceCopy;
        let columns = provenanceColumns;
        if (change === 'source-missing') sources = [otherSource];
        if (change === 'source-ambiguous') sources = [chosenSource, chosenSource];
        if (change === 'source-truncated') sources = [chosenSource.slice(1)];
        if (change === 'copy-truncated') copy = provenanceCopy.slice(1);
        if (change === 'schema-missing') columns = provenanceColumns.filter(name => name !== 'reporter_program_team');
        if (change === 'schema-duplicate') columns = [...provenanceColumns, 'program_no'];
        assert.throws(() => assertCopiedProvenance(sources, copy, columns, '5'),
            /cannot identify|snapshot is incomplete|missing or ambiguous/);
    });
}
