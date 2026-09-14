#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * The surfaces a clinician or administrator reaches from the schedule, and how
 * they are reached -- as data, by clicking.
 *
 * WHY A TABLE. Every one of these surfaces needs the same check (open it, open
 * everything it offers, fail on anything broken) and differs only in how it is
 * entered. Writing one script per surface would be a dozen copies of the same
 * 150 lines, each drifting separately. One table plus one tested engine means a
 * fix reaches every surface and a new surface is four lines.
 *
 * HOW A CONTROL IS IDENTIFIED. Each row names the control the way a user
 * identifies it, and the schedule does not use one convention for all of them:
 *
 *   .label     the anchor's visible text. The top bar renders labels through
 *              <fmt:message> with embedded accesskey markup ("T<u>i</u>ckler"),
 *              which textContent flattens, so the text is reliable where it
 *              exists -- but several labels are FOLLOWED by a live count
 *              (<oscar:newLab> appends "<sup>3</sup>" when labs are waiting), so
 *              these regexes anchor at the start and must not anchor at the end.
 *              Nor may they end in \b: the count is a digit, and there is no
 *              word boundary between "Tickler" and "3".
 *   .title     the control's title attribute, for the two icon-only controls in
 *              #userSettings, which render an inline <svg> and no text at all.
 *              The title IS the accessible name, so this is still "what the user
 *              sees", not an implementation detail.
 *   .selector  a CSS selector, for a control whose text carries a count AND
 *              whose sibling would also match the text (the Inbox link, next to
 *              the unclaimed-lab "U" badge).
 *
 * Exactly one of the three per row; the engine asserts that.
 *
 * `entry.popup` true when the control opens a new window (most of them do).
 * `optional`   the control is behind a property or module flag that a given
 *              deployment may legitimately have off (WORKFLOW, referral_menu).
 *              PRESENT means a missing control is a SKIP rather than a failure,
 *              and the string says which flag. Everything WITHOUT this key must
 *              be present, because "control missing" is the failure this
 *              catches -- see openSurface in surface-audit-playwright-checks.js,
 *              which throws SkipCheck only when the key is set and asserts
 *              otherwise. (This read "Absent means SKIP", which is the rule
 *              backwards and would have had the next surface definition marking
 *              a genuinely required control as skippable.)
 * `scope`      optional CSS scope for the catalogue, when only part of the
 *              surface belongs to it.
 * `minimum`    how many items the surface must offer. This is the guard against
 *              the catalogue silently matching nothing and the check passing by
 *              opening zero pages -- the failure mode that makes a green suite
 *              worthless. Set it from what the surface actually renders.
 * `skip`       items not to open, each with a reason.
 * `province`   'all', or the billregion this surface exists in.
 *
 * WHAT IS DELIBERATELY NOT HERE. There is no Billing entry: the schedule top bar
 * has no Billing control (verified against appointmentprovideradminday.jsp --
 * the only billing links on that page are the per-appointment "B" badges, which
 * are a workflow, not a surface). Ontario billing is reached through the
 * Administration panel, which admin-index-links already audits.
 */

const NEVER_OPEN = [
  { match: /^(log\s*out|logout|exit|close|cancel)$/i, reason: 'ends the session or the window the remaining items need' },
  { match: /^(delete|remove|purge)\b/i, reason: 'mutates data; this family is read-only and each delete belongs to its own workflow check' },
];

const SURFACES = [
  {
    name: 'report-index',
    title: 'Report index',
    entry: { label: /^\s*Report/i, popup: true },
    minimum: 3,
    province: 'all',
    note: 'schedule-links opens this page but never opens anything on it',
  },
  {
    name: 'inbox-surface',
    title: 'Inbox (Inboxhub)',
    // The label is "Inbox" (global.lab), but <oscar:newLab> appends a count and
    // the adjacent unclaimed-lab badge is another anchor in the same <li>.
    entry: { selector: '#inboxLink', popup: true },
    minimum: 4,
    province: 'all',
    note: 'issue #3313 item 1 was a pageerror on this page that no check saw',
  },
  {
    name: 'consultations-surface',
    title: 'Consultations list',
    entry: { label: /^\s*Consultations/i, popup: true },
    minimum: 3,
    province: 'all',
  },
  {
    name: 'messenger-surface',
    title: 'Messenger',
    entry: { label: /^\s*Msg/i, popup: true },
    minimum: 3,
    province: 'all',
  },
  {
    name: 'tickler-surface',
    title: 'Tickler',
    entry: { label: /^\s*Tickler/i, popup: true },
    minimum: 3,
    province: 'all',
  },
  {
    name: 'edoc-surface',
    title: 'eDoc document report',
    entry: { label: /^\s*eDoc/i, popup: true },
    minimum: 3,
    province: 'all',
  },
  {
    name: 'referrals-surface',
    title: 'Manage billing referrals',
    // Label is "Ref" (global.manageReferrals), anchored both ends so it cannot
    // swallow "Report". Gated by the referral_menu property.
    entry: { label: /^\s*Ref\s*$/i, popup: true },
    optional: 'the referral_menu property is not set to yes on this deployment',
    minimum: 2,
    province: 'all',
  },
  {
    name: 'preferences-surface',
    title: 'Provider preferences',
    // Icon-only control in #userSettings; its title is its accessible name.
    entry: { title: /Edit your personal setting/i, popup: true },
    minimum: 2,
    province: 'all',
    note: 'every Preferences section writes provider properties; this opens what it offers read-only',
  },
  {
    name: 'workflow-surface',
    title: 'WorkFlow list',
    entry: { label: /^\s*WorkFlow/i, popup: true },
    optional: 'the WORKFLOW property is not set to yes on this deployment',
    minimum: 1,
    province: 'all',
  },
  {
    name: 'scratch-surface',
    title: 'Scratch pad',
    // Icon-only control in #userSettings; its title is its accessible name.
    entry: { title: /Scratch\s*Pad/i, popup: true },
    minimum: 1,
    province: 'all',
  },
];

/** How a row names its control, for messages and for the one-strategy rule. */
function entryStrategy(surface) {
  const entry = surface.entry || {};
  const used = ['label', 'title', 'selector'].filter((key) => entry[key]);
  if (used.length !== 1) {
    throw new Error(`surface ${surface.name} must name its control with exactly one of label/title/selector; found ${used.length}`);
  }
  return used[0];
}

/** Human-readable form of the control this surface is reached by. */
function describeEntry(surface) {
  const strategy = entryStrategy(surface);
  return `${strategy} ${String(surface.entry[strategy])}`;
}

function surfaceByName(name) {
  return SURFACES.find((surface) => surface.name === name) || null;
}

function surfacesForProvince(province) {
  if (!province) {
    return SURFACES.slice();
  }
  return SURFACES.filter((surface) => surface.province === 'all' || surface.province === province);
}

module.exports = {
  NEVER_OPEN, SURFACES, describeEntry, entryStrategy, surfaceByName, surfacesForProvince,
};
