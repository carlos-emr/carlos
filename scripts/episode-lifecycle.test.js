/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const assert = require('node:assert/strict');
const test = require('node:test');
const {
  EPISODE_OBJECT, episodeModuleGranted, requireEpisodeModule,
} = require('./episode-lifecycle-playwright-checks');

test('the seeded `o` privilege hides the Episode module', () => {
  assert.equal(episodeModuleGranted(['o']), false);
  assert.equal(episodeModuleGranted([]), false);
  assert.equal(episodeModuleGranted([null, '']), false);
});

test('any read-satisfying right on any of the login roles shows the module', () => {
  for (const right of ['x', 'r', 'u', 'w', 'X', ' r ']) {
    assert.equal(episodeModuleGranted(['o', right]), true, `right ${JSON.stringify(right)}`);
  }
  assert.equal(episodeModuleGranted(['o|r']), true);
  assert.equal(episodeModuleGranted(['d']), false);
});

test('legacy "only" tokens grant read the way checkRights strips their leading o', () => {
  for (const token of ['or', 'ou', 'ow', 'ox', 'OR', '|or|']) {
    assert.equal(episodeModuleGranted([token]), true, `token ${JSON.stringify(token)}`);
  }
  assert.equal(episodeModuleGranted(['od']), false);
});

test('a login without the grant SKIPs naming the object instead of timing out on a hidden menu', () => {
  const queries = [];
  const sql = { rows(query) { queries.push(query); return [['o']]; } };
  assert.throws(() => requireEpisodeModule({ sql, provider: '999998' }), (error) => {
    assert.equal(error.name, 'SkipCheck');
    assert.match(error.message, new RegExp(EPISODE_OBJECT.replace('.', '\\.')));
    return true;
  });
  assert.match(queries[0], /objectName='_newCasemgmt\.episode'/);
  assert.match(queries[0], /provider_no='999998'/);
  assert.match(queries[0], /activeyn=1/, 'an inactive role assignment does not reach the session role string');
});

test('a login with the grant runs the workflow', () => {
  const sql = { rows() { return [['o'], ['x']]; } };
  assert.doesNotThrow(() => requireEpisodeModule({ sql, provider: '999998' }));
});
