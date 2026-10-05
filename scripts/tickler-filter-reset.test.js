/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const { resetProviderFilters } = require('./search-sort-tickler-list-playwright-checks');

function filters(assigneeOptions, assigneeValue) {
  const selected = [];
  return {
    selected,
    locator(selector) {
      return {
        locator(option) {
          assert.equal(option, 'option[value="all"]');
          return { count: async () => selector === '#assignedTo' ? assigneeOptions : 1 };
        },
        selectOption: async value => selected.push([selector, value]),
        inputValue: async () => assigneeValue,
      };
    },
  };
}

test('saved provider filters all reset when each selector offers all providers', async () => {
  const page = filters(1, '999998');
  await resetProviderFilters(page);
  assert.deepEqual(page.selected, [['#providerview', 'all'], ['#assignedTo', 'all'], ['#mrpview', 'all']]);
});

test('an empty multisite assignee selector stays unfiltered without selecting a nonexistent option', async () => {
  const page = filters(0, '');
  await resetProviderFilters(page);
  assert.deepEqual(page.selected, [['#providerview', 'all'], ['#mrpview', 'all']]);
});

test('an uncleared filter fails explicitly rather than testing an incomplete fixture set', async () => {
  await assert.rejects(resetProviderFilters(filters(0, '999998')), /saved filter cannot be cleared/);
});
