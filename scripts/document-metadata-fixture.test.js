/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {createHash} = require('node:crypto');
const {createStoredDocumentFixture} = require('./lib/stored-document-mutation-fixture');
const {captureMetadataState} = require('./lib/document-metadata-check');
const hash = value => value.repeat(64);
const clone = value => JSON.parse(JSON.stringify(value));

// Real private files, fixture journal, fsync/rename and acceptance adapter; only
// SQL result rows are modeled. No acceptance/cleanup method is replaced.
function ownedFixture() {
    const store = fs.mkdtempSync(path.join(os.tmpdir(), 'metadata-fixture-adapter-test-'));
    const saved = Object.fromEntries(['DOCUMENT_DIR', 'RX_FAX_DOCUMENT_DIR', 'DOCUMENT_CACHE_DIR'].map(key => [key, process.env[key]]));
    process.env.DOCUMENT_DIR = store; delete process.env.RX_FAX_DOCUMENT_DIR;
    process.env.DOCUMENT_CACHE_DIR = path.join(store, 'unused-cache');
    const schema = {schemas: new Map(Object.entries({
        document: ['document_no', 'docdesc', 'doctype', 'updatedatetime', 'observationdate'],
        ctl_document: ['module_id', 'module', 'document_no'], patientLabRouting: ['id', 'lab_no', 'lab_type'],
        providerLabRouting: ['id', 'lab_no', 'lab_type', 'provider_no', 'status', 'timestamp'],
        queue_document_link: ['id', 'document_id', 'queue_id', 'status'], document_storage: ['documentNo'],
    }).map(([table, columns]) => [table, {engine: 'InnoDB', columns}])), refs: [['document_storage', 'documentNo', '0']]};
    const callbacks = [], transactions = [];
    const rows = {
        document: [['42', hash('a'), hash('b')]], ctl_document: [['123', 'demographic', '42', hash('c'), hash('d')]],
        patientLabRouting: [['11', hash('e'), hash('f')]], providerLabRouting: [['12', hash('1'), hash('2')]],
        queue_document_link: [['13', hash('3'), hash('4')]],
    };
    const values = {document: [['FAKE-PW1234567890abcdef stored mutation', 'Lab']], providers: [['12', '999998', 'N']], queues: [['13', '1', 'A']]};
    let foreign = false;
    const sql = {
        rows(query) {
            if (query.includes('ORDER BY ORDINAL_POSITION')) {
                const table = /TABLE_NAME='([^']+)'/.exec(query)[1];
                return schema.schemas.get(table).columns.map(column => [column]);
            }
            if (query.includes('information_schema.COLUMNS c')) return clone(schema.refs);
            if (query.includes('FROM document ORDER BY')) return [['5', hash('9')], ...rows.document.map(row => row.slice(0, -1))];
            if (query.startsWith('SELECT docdesc,doctype')) return clone(values.document);
            if (query.startsWith('SELECT id,queue_id,status')) return clone(values.queues);
            if (query.startsWith('SELECT id,provider_no,status')) return clone(values.providers);
            const table = /FROM `([^`]+)`/.exec(query)?.[1];
            if (table && rows[table]) return clone(rows[table].map(row => (query.match(/SHA2\(/g) || []).length === 2 ? row : row.slice(0, -1)));
            throw new Error('Unexpected metadata fixture rows query');
        },
        value(query) {
            if (query.startsWith('INSERT INTO document')) {
                assert.equal(callbacks.length, 1, 'Cleanup registered before insertion');
                assert.match(query, /restrictToProgram,observationdate/);
                assert.match(query, /DATE\(NOW\(\)\)/, 'Valid observation date is included before the initial snapshot');
                return '42';
            }
            if (query.includes('START TRANSACTION')) {
                transactions.push(query);
                for (const table of Object.keys(rows)) rows[table] = [];
                return '1';
            }
            if (query.includes('FROM demographic')) return '1';
            if (query.includes('information_schema.TABLES')) return '0';
            if (query.includes('document_storage') || query.includes('casemgmt_note_link')) return foreign ? '1' : '0';
            if (query.startsWith('SELECT SHA2(') && query.includes('FROM document WHERE')) return hash('8');
            throw new Error('Unexpected metadata fixture scalar query');
        },
        execute(query) { assert(query.startsWith('INSERT INTO ctl_document')); },
    };
    const session = {sql, marker: 'FAKE-PW1234567890abcdef', patient: '123', provider: '999998', cleanup(callback) {callbacks.push(callback);}};
    const fixture = createStoredDocumentFixture(session, '10', undefined, {metadataSchema: schema});
    const identity = {document: fixture.sourceId, patient: session.patient, provider: session.provider, marker: session.marker, file: fixture.sourceFile, store};
    function prepare() {
        const intent = {kind: 'metadata', document: '42', patient: session.patient, provider: session.provider,
            description: session.marker + ' metadata accepted', classification: 'Consult', body: 'owned-frozen-request'};
        const before = captureMetadataState(sql, identity, schema, 'metadata');
        fixture.begin();
        rows.document[0][1] = hash('7'); values.document = [[intent.description, intent.classification]];
        const after = captureMetadataState(sql, identity, schema, 'metadata');
        return {kind: 'metadata', intent, before, after, response: {success: true, accepted: true, document: 42, patientId: '123'},
            requestBodySha256: createHash('sha256').update(intent.body).digest('hex')};
    }
    return {fixture, rows, values, transactions, prepare, foreign() {foreign = true;},
        journal() {return JSON.parse(fs.readFileSync(fixture.journal, 'utf8'));},
        async retained() {
            fixture.closeAfterDrain(); await assert.rejects(callbacks[0]());
            assert.equal(transactions.length, 0); assert.equal(rows.document.length, 1);
            assert(fs.existsSync(fixture.sourceFile)); assert.equal(fixture.isCleaned(), false);
        },
        cleanup: callbacks[0],
        dispose() {
            for (const [key, value] of Object.entries(saved)) {if (value === undefined) delete process.env[key]; else process.env[key] = value;}
            fs.rmSync(store, {recursive: true, force: true}); fs.rmSync(path.dirname(fixture.journal), {recursive: true, force: true});
        },
    };
}

test('real adapter fsyncs immutable full proof before advancing its journal and permits exact cleanup', async () => {
    const env = ownedFixture(), receipt = env.prepare();
    const sync = fs.fsyncSync, rename = fs.renameSync;
    let synced = 0, advanced = false;
    try {
        const original = env.journal(); assert.equal(original.phase, 'mutation-pending');
        fs.fsyncSync = descriptor => {synced++; return sync(descriptor);};
        fs.renameSync = (source, destination) => {
            if (destination === env.fixture.journal) {
                const proofPath = path.join(path.dirname(destination), 'metadata-1.json');
                assert(fs.existsSync(proofPath)); assert(synced >= 2, 'Receipt file and parent directory were synchronized');
                const proof = JSON.parse(fs.readFileSync(proofPath));
                assert.deepEqual(proof.before, receipt.before); assert.deepEqual(proof.after, receipt.after);
                assert.equal(env.journal().phase, 'mutation-pending'); advanced = true;
            }
            return rename(source, destination);
        };
        env.fixture.acceptMetadataTransition(receipt);
        fs.fsyncSync = sync; fs.renameSync = rename;
        assert(advanced); const journal = env.journal(); assert.equal(journal.phase, 'metadata-confirmed'); assert.equal(journal.uncertain, false);
        assert.equal(journal.metadataReceipts.length, 1);
        const record = journal.metadataReceipts[0], bytes = fs.readFileSync(record.file);
        assert.equal(createHash('sha256').update(bytes).digest('hex'), record.sha256);
        assert.equal(fs.statSync(record.file).mode & 0o777, 0o600);
        assert(!bytes.includes(Buffer.from(receipt.intent.body)), 'Evidence omits raw request/session fields');
        assert.deepEqual(journal.documents[0][1].snapshot.document, [['42', hash('7')]]);
        env.fixture.assertUnchanged(); env.fixture.closeAfterDrain(); await env.cleanup();
        assert.equal(env.transactions.length, 1); assert.equal(env.fixture.isCleaned(), true);
    } finally {fs.fsyncSync = sync; fs.renameSync = rename; env.dispose();}
});

for (const defect of ['before snapshot', 'after snapshot', 'request hash', 'wrong patient', 'response patient', 'response document', 'foreign reference', 'concurrent route', 'changed PDF', 'transport uncertainty']) {
    test('real adapter refuses ' + defect + ' without adopting rows or permitting cleanup', async () => {
        const env = ownedFixture();
        try {
            const receipt = env.prepare(), original = env.journal().documents;
            if (defect === 'before snapshot') receipt.before.rows.document[0][1] = hash('0');
            if (defect === 'after snapshot') receipt.after.rows.document[0][1] = hash('0');
            if (defect === 'request hash') receipt.requestBodySha256 = hash('0');
            if (defect === 'wrong patient') receipt.intent.patient = '124';
            if (defect === 'response patient') receipt.response.patientId = '124';
            if (defect === 'response document') receipt.response.document = 43;
            if (defect === 'foreign reference') env.foreign();
            if (defect === 'concurrent route') env.rows.patientLabRouting.push(['18', hash('a'), hash('b')]);
            if (defect === 'changed PDF') fs.appendFileSync(env.fixture.sourceFile, 'unconfirmed edit');
            if (defect === 'transport uncertainty') env.fixture.transportFailed();
            assert.throws(() => env.fixture.acceptMetadataTransition(receipt));
            assert.deepEqual(env.journal().documents, original);
            assert.equal(env.journal().metadataReceipts.length, 0);
            await env.retained();
        } finally {env.dispose();}
    });
}
for (const failure of ['receipt fsync', 'receipt directory fsync', 'journal publication']) {
    test(failure + ' failure keeps acceptance uncertain and retains both source and database rows', async () => {
        const env = ownedFixture(), receipt = env.prepare(), sync = fs.fsyncSync, rename = fs.renameSync;
        let syncs = 0;
        try {
            const before = env.journal().documents;
            fs.fsyncSync = descriptor => {
                syncs++;
                if ((failure === 'receipt fsync' && syncs === 1) || (failure === 'receipt directory fsync' && syncs === 2)) throw new Error('Injected durable write failure');
                return sync(descriptor);
            };
            fs.renameSync = (source, destination) => {
                if (failure === 'journal publication' && destination === env.fixture.journal) throw new Error('Injected checkpoint publication failure');
                return rename(source, destination);
            };
            assert.throws(() => env.fixture.acceptMetadataTransition(receipt), /Injected/);
            fs.fsyncSync = sync; fs.renameSync = rename;
            assert.equal(env.journal().phase, 'mutation-pending'); assert.deepEqual(env.journal().documents, before);
            // An interrupted journal publication leaves its private .new file. This
            // alone must also prevent a misleading browser-drained/cleaned checkpoint.
            if (failure === 'journal publication') {
                assert.throws(() => env.fixture.closeAfterDrain());
                await assert.rejects(env.cleanup()); assert.equal(env.transactions.length, 0);
                assert.equal(env.rows.document.length, 1); assert(fs.existsSync(env.fixture.sourceFile));
            } else await env.retained();
        } finally {fs.fsyncSync = sync; fs.renameSync = rename; env.dispose();}
    });
}
test('late delivery uncertainty remains sticky even after a valid durable transition', async () => {
    const env = ownedFixture();
    try {
        env.fixture.acceptMetadataTransition(env.prepare()); env.fixture.transportFailed();
        assert.equal(env.journal().transportUncertain, true); await env.retained();
    } finally {env.dispose();}
});
