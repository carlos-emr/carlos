/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const helperFile = path.join(__dirname, 'lib/legacy-message-handoff-checks.js');
const APPLICATION_HEADER = 'x-carlos-application';
const patient = '11';
const otherPatient = '12';
const provider = '101';
const marker = 'FAKE-legacy-handoff-unit';

/** Execute the real helper against stateful database/browser boundaries, without a server. */
function harness({ missingProbe = 'success' } = {}) {
  const messages = new Map();
  const links = [];
  const recipients = [];
  const cleanups = [];
  const events = [];
  const destructiveSql = [];
  const roles = new Map();
  const locks = new Map();
  let nextMessage = 100;
  let nextLogin = 200;
  let vanished;
  let probeReached = false;
  const requestFailure = new Error('controlled missing-message request failure');
  const unquote = value => value.replace(/''/g, "'");
  const normal = value => value.replace(/\s+/g, ' ').trim();
  const dangling = () => links.filter(link => !messages.has(link.message));
  const idsFrom = query => query.match(/(?:messageid|messageID|message) IN \(([^)]+)\)/i)[1]
    .split(',').map(value => value.trim());
  const remove = (rows, predicate) => {
    for (let index = rows.length - 1; index >= 0; index--) if (predicate(rows[index])) rows.splice(index, 1);
  };
  const sql = {
    rows(statement) {
      const query = normal(statement);
      if (/^SELECT \* FROM casemgmt_note_lock /i.test(query)) return [];
      const selected = query.match(/^SELECT demographic_no FROM msgDemoMap WHERE messageID=(\d+)$/i);
      if (selected) {
        if (probeReached && missingProbe === 'ownership-read-failure') throw new Error('controlled ownership read failure');
        return links.filter(link => link.message === selected[1]).map(link => [link.patient]);
      }
      throw new Error(`Unexpected SQL rows operation: ${query}`);
    },
    value(statement) {
      const query = normal(statement);
      if (/^SELECT COUNT\(\*\) FROM demographic WHERE demographic_no=(11|12) AND last_name=/i.test(query)) return '1';
      if (/^SELECT locationId FROM oscarcommlocations WHERE current1=1 LIMIT 1$/i.test(query)) return '7';
      if (/^SELECT COALESCE\(MAX\(messageid\),0\) FROM messagetbl$/i.test(query)) return String(nextMessage);
      const disappear = query.match(/^DELETE FROM messagetbl WHERE messageid=(\d+) AND thesubject='((?:''|[^'])*)' AND themessage='((?:''|[^'])*)'; SELECT ROW_COUNT\(\)$/i);
      if (disappear) {
        const [id, subject, body] = disappear.slice(1);
        const message = messages.get(id);
        const owned = message && message.subject === unquote(subject) && message.body === unquote(body);
        if (owned) {
          messages.delete(id);
          vanished = id;
          destructiveSql.push(query);
          events.push({ type: 'vanish', id });
        }
        return owned ? '1' : '0';
      }
      const linked = query.match(/^SELECT COUNT\(\*\) FROM msgDemoMap WHERE messageID=(\d+)(?: AND demographic_no=(\d+))?$/i);
      if (linked) return String(links.filter(link => link.message === linked[1]
        && (!linked[2] || link.patient === linked[2])).length);
      const body = query.match(/^SELECT COUNT\(\*\) FROM messagetbl WHERE messageid=(\d+)$/i);
      if (body) return messages.has(body[1]) ? '1' : '0';
      if (/^SELECT COUNT\(\*\) FROM messagetbl WHERE messageid IN /i.test(query)) {
        const ids = idsFrom(query);
        return String(ids.filter(id => messages.has(id) && !messages.get(id).subject.startsWith(marker)).length);
      }
      if (/^SELECT \(SELECT COUNT\(\*\) FROM messagetbl WHERE messageid IN /i.test(query)) {
        const ids = idsFrom(query);
        return String(ids.filter(id => messages.has(id)).length
          + links.filter(link => ids.includes(link.message)).length
          + recipients.filter(row => ids.includes(row.message)).length);
      }
      throw new Error(`Unexpected SQL value operation: ${query}`);
    },
    execute(statement) {
      for (const part of statement.split(';').map(normal).filter(Boolean)) {
        let match = part.match(/^INSERT INTO messagelisttbl .* VALUES \((\d+),'([^']*)','new',7,0,0\)$/i);
        if (match) { recipients.push({ message: match[1], provider: match[2] }); continue; }
        match = part.match(/^INSERT INTO msgDemoMap\(messageID,demographic_no\) VALUES \((\d+),(\d+)\)$/i);
        if (match) { links.push({ message: match[1], patient: match[2] }); continue; }
        match = part.match(/^DELETE FROM msgDemoMap WHERE messageID=(\d+) AND demographic_no=(\d+)$/i);
        if (match) {
          destructiveSql.push(part);
          remove(links, link => link.message === match[1] && link.patient === match[2]);
          events.push({ type: 'unlink', id: match[1] });
          continue;
        }
        if (/^DELETE FROM (?:msgDemoMap|messagelisttbl|messagetbl) WHERE /i.test(part)) {
          const ids = idsFrom(part);
          destructiveSql.push(part);
          if (/^DELETE FROM msgDemoMap /i.test(part)) remove(links, link => ids.includes(link.message));
          else if (/^DELETE FROM messagelisttbl /i.test(part)) remove(recipients, row => ids.includes(row.message));
          else for (const id of ids) if (messages.get(id)?.subject.startsWith(marker)) messages.delete(id);
          continue;
        }
        throw new Error(`Unexpected SQL execute operation: ${part}`);
      }
    },
  };
  const response = (status, text = 'controlled response') => ({
    status: () => status, headers: () => ({ [APPLICATION_HEADER]: 'true' }),
    text: async () => text, dispose: async () => {},
  });
  const contextFor = (loginProvider, grants) => ({
    async newPage() {
      let selectedMessage;
      return {
        async goto(address) {
          const url = new URL(address);
          assert.equal(dangling().length, 0, 'A native editor encountered a deliberately dangling message link');
          const id = url.searchParams.get('msgId');
          selectedMessage = id ? messages.get(String(Number(id))) : null;
          events.push({ type: 'native', patient: url.searchParams.get('demographicNo'), id });
          return response(200);
        },
        locator: () => ({ waitFor: async () => {}, inputValue: async () => selectedMessage?.body || '' }),
        close: async () => {},
      };
    },
    request: {
      async get(address) {
        const url = new URL(address);
        const selectedPatient = url.searchParams.get('demographicNo');
        const raw = url.searchParams.getAll('msgId');
        if (raw.some(id => !/^\d+$/.test(id) || Number(id) <= 0 || Number(id) > 2147483647)
            || new Set(raw.map(Number)).size > 1) return response(400);
        if (!grants._msg || locks.get(loginProvider) === selectedPatient) return response(403);
        const id = String(Number(raw[0]));
        if (!links.some(link => link.message === id && link.patient === selectedPatient)) return response(403);
        if (messages.has(id)) throw new Error('The negative probe unexpectedly requested a complete linked message');
        assert.equal(dangling().length, 1, 'Only the missing-message probe may contain one dangling link');
        assert.equal(id, vanished);
        probeReached = true;
        events.push({ type: 'missing-probe', id });
        if (missingProbe === 'foreign-link') links.find(link => link.message === id).patient = '999';
        if (missingProbe === 'replacement-body') messages.set(id, {
          id, body: 'replacement body must survive', subject: `${marker} replacement body`,
        });
        if (missingProbe !== 'success') throw requestFailure;
        return response(404);
      },
    },
    close: async () => {},
  });
  const fixture = {
    addRole: grants => grants,
    addLogin(grants) { const providerNo = String(++nextLogin); roles.set(providerNo, grants); return { providerNo }; },
    joinTestLoginPrograms() {},
    lockPatient: (login, selectedPatient) => locks.set(login.providerNo, String(selectedPatient)),
    cleanup() {},
  };
  const h = {
    assert: (condition, message) => assert.ok(condition, message), APPLICATION_HEADER,
    sqlString: value => `'${String(value).replace(/'/g, "''")}'`,
    appUrl: (base, address) => new URL(address, base).href,
    insertId(_sql, statement) {
      const values = normal(statement).match(/VALUES \(CURDATE\(\),CURTIME\(\),'((?:''|[^'])*)','((?:''|[^'])*)',/i);
      assert.ok(values, 'The message fixture INSERT must supply its body and subject');
      const id = String(++nextMessage);
      messages.set(id, { id, body: unquote(values[1]), subject: unquote(values[2]) });
      return id;
    },
  };
  const dependencies = {
    './playwright-harness': h,
    './authz-read-fixture': { authzReadFixture: () => fixture },
    './authz-read-probe': { signIn: async (_s, login) => ({ context: contextFor(login.providerNo, roles.get(login.providerNo)) }) },
    '../echart-two-patient-isolation-playwright-checks': {
      readPatientState: (_sql, id) => ({ clinical: [`unchanged patient ${id}`] }),
      assertPatientStateUnchanged: (before, after, label) => assert.deepEqual(before, after, label),
    },
  };
  const module = { exports: {} };
  vm.runInNewContext(fs.readFileSync(helperFile, 'utf8'), {
    module, exports: module.exports, URLSearchParams,
    require(name) {
    if (name === './lib/authenticated-probe') return require('./lib/authenticated-probe'); assert.ok(Object.hasOwn(dependencies, name), `Unexpected dependency ${name}`); return dependencies[name]; },
  }, { filename: helperFile });
  const s = {
    sql, marker, provider, config: { baseUrl: 'https://fixture.invalid', testUser: 'FAKE-unit' },
    context: contextFor(provider, { _msg: 'r' }),
    cleanup: callback => cleanups.push(callback),
    step: async (_label, callback) => callback(),
  };
  return {
    run: () => module.exports.checkLegacyMessageHandoff(s, { patient, otherPatient,
      trackEditorModules: () => async () => assert.equal(dangling().length, 0) }),
    async cleanup() {
      const failures = [];
      for (const callback of [...cleanups].reverse()) {
        try { await callback(); } catch (error) { failures.push(error); }
      }
      return failures;
    },
    messages, links, recipients, events, destructiveSql, requestFailure,
    get vanished() { return vanished; },
  };
}

test('native editors see complete messages and only the direct typed404 probe has a dangling link', async () => {
  const fixture = harness();
  await fixture.run();
  assert.equal(fixture.events.filter(event => event.type === 'native').length, 6);
  assert.equal(fixture.events.filter(event => event.type === 'missing-probe').length, 1);
  assert.equal(fixture.events.filter(event => event.type === 'unlink' && event.id === fixture.vanished).length, 1);
  assert.equal(fixture.links.some(link => link.message === fixture.vanished), false);
  assert.deepEqual(await fixture.cleanup(), []);
  assert.equal(fixture.messages.size, 0);
  assert.deepEqual(fixture.links, []);
  assert.deepEqual(fixture.recipients, []);
});

test('a failed direct404 request still removes its owned orphan before outer cleanup', async () => {
  const fixture = harness({ missingProbe: 'request-failure' });
  await assert.rejects(fixture.run(), error => error === fixture.requestFailure);
  assert.equal(fixture.links.some(link => link.message === fixture.vanished), false);
  assert.equal(fixture.events.filter(event => event.type === 'unlink' && event.id === fixture.vanished).length, 1);
  assert.deepEqual(await fixture.cleanup(), []);
  assert.equal(fixture.messages.size, 0);
  assert.deepEqual(fixture.links, []);
  assert.deepEqual(fixture.recipients, []);
});

for (const mode of ['foreign-link', 'replacement-body', 'ownership-read-failure']) {
  test(`a ${mode} refuses orphan cleanup and registered outer cleanup also retains it`, async () => {
    const fixture = harness({ missingProbe: mode });
    await assert.rejects(fixture.run(), mode === 'replacement-body' ? /replaced/ : mode === 'foreign-link'
      ? /changed ownership/ : /controlled ownership read failure/);
    const before = { messages: [...fixture.messages], links: JSON.stringify(fixture.links),
      recipients: JSON.stringify(fixture.recipients), destructiveSql: fixture.destructiveSql.length };
    const failures = await fixture.cleanup();
    assert.equal(failures.length, 1);
    assert.match(failures[0].message, /ownership is unconfirmed/);
    assert.deepEqual([...fixture.messages], before.messages);
    assert.equal(JSON.stringify(fixture.links), before.links);
    assert.equal(JSON.stringify(fixture.recipients), before.recipients);
    assert.equal(fixture.destructiveSql.length, before.destructiveSql);
    assert.equal(fixture.links.some(link => link.message === fixture.vanished), true);
    if (mode === 'foreign-link') assert.equal(fixture.links.find(link => link.message === fixture.vanished).patient, '999');
    if (mode === 'replacement-body') assert.equal(fixture.messages.get(fixture.vanished).body, 'replacement body must survive');
  });
}
