/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

/*
 * The encounter form list (encounterForm), as a check records it and as a clinic sees it.
 *
 * encounterForm.hidden is the display order; 0 leaves a form off the eChart's form menu. Before any Add, Hide, Move Up
 * or Move Down, Administration ▸ Select Forms gives the shown forms the orders 1..n in menu order (#4952), so the
 * numbers of forms a check never touched can change while the menu stays the same. A check that adds forms through
 * Select Forms and removes them again therefore compares the list as seen: every row's page and table, whether it is
 * shown, and the shown forms' order. Its cleanup still puts the recorded numbers back and checks them exactly.
 */

/**
 * The removed CAISI forms (EctFormData.isRemovedCaisiForm, compared without case): no page lists them, and Select
 * Forms hides any that are still shown when it renumbers (#4952), so the menu never shows them either way.
 */
const REMOVED_CAISI_FORMS = new Set(['counsellor assessment', 'discharge summary', 'reception assessment']);
const shownOnMenu = ([name, , , hidden]) => Number(hidden) > 0 && !REMOVED_CAISI_FORMS.has(String(name).toLowerCase());

/** Every encounterForm row, in a stable order, as [form_name, form_value, form_table, hidden]. */
function encounterForms(sql) {
  return sql.rows('SELECT form_name,form_value,form_table,hidden FROM encounterForm ORDER BY form_name,form_value');
}

/**
 * The list as the eChart's form menu shows it. The shown forms are ordered by display order, then by name, the
 * order Select Forms keeps (ShownEncounterForms: natural string order, as JavaScript's < compares).
 */
function formListAsSeen(rows) {
  const shown = rows.filter(shownOnMenu)
    .sort((a, b) => Number(a[3]) - Number(b[3]) || (a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0))
    .map(row => row[0]);
  return JSON.stringify({ forms: rows.map(row => [row[0], row[1], row[2], shownOnMenu(row)]), shown });
}

module.exports = { encounterForms, formListAsSeen };
