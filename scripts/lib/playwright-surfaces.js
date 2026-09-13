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
 * WHY TEXT LOCATORS, NOT IDS. The schedule's top bar renders its labels through
 * <fmt:message> with embedded <u> accesskey markup ("T<u>i</u>ckler"), so the id
 * is not always present but the text always is, and textContent flattens the
 * markup. A label regex is also what the coverage plan's paths say, which keeps
 * the check and the documentation describing the same click.
 *
 * `entry`      how to reach it from the post-login schedule.
 *   .label     regex matched against the anchor's text on the schedule.
 *   .popup     true when the control opens a new window (most of them do).
 * `scope`      optional CSS scope for the catalogue, when only part of the
 *              surface belongs to it.
 * `minimum`    how many items the surface must offer. This is the guard against
 *              the catalogue silently matching nothing and the check passing by
 *              opening zero pages -- the failure mode that makes a green suite
 *              worthless. Set it from what the surface actually renders.
 * `skip`       items not to open, each with a reason.
 * `province`   'all', or the billregion this surface exists in.
 */

const NEVER_OPEN = [
  { match: /^(log\s*out|logout|exit|close|cancel)$/i, reason: 'ends the session or the window the remaining items need' },
  { match: /^(delete|remove|purge)\b/i, reason: 'mutates data; this family is read-only and each delete belongs to its own workflow check' },
];

const SURFACES = [
  {
    name: 'report-index',
    title: 'Report index',
    entry: { label: /^\s*Report\s*$/i, popup: true },
    minimum: 3,
    province: 'all',
    note: 'schedule-links opens this page but never opens anything on it',
  },
  {
    name: 'inbox-surface',
    title: 'Inbox (Inboxhub)',
    entry: { label: /^\s*Inbox\s*$/i, popup: true },
    minimum: 4,
    province: 'all',
    note: 'issue #3313 item 1 was a pageerror on this page that no check saw',
  },
  {
    name: 'consultations-surface',
    title: 'Consultations list',
    entry: { label: /^\s*Consultations\s*$/i, popup: true },
    minimum: 3,
    province: 'all',
  },
  {
    name: 'messenger-surface',
    title: 'Messenger',
    entry: { label: /^\s*Msg\s*$/i, popup: true },
    minimum: 3,
    province: 'all',
  },
  {
    name: 'tickler-surface',
    title: 'Tickler',
    entry: { label: /^\s*Tickler\s*$/i, popup: true },
    minimum: 3,
    province: 'all',
  },
  {
    name: 'edoc-surface',
    title: 'eDoc document report',
    entry: { label: /^\s*eDoc\s*$/i, popup: true },
    minimum: 3,
    province: 'all',
  },
  {
    name: 'billing-surface',
    title: 'Billing',
    entry: { label: /^\s*Billing\s*$/i, popup: true },
    minimum: 3,
    province: 'ON',
    note: 'the Ontario billing module is 4% covered by route; this is its first surface check',
  },
  {
    name: 'preferences-surface',
    title: 'Provider preferences',
    entry: { label: /^\s*Preferences\s*$/i, popup: true },
    minimum: 2,
    province: 'all',
    note: 'every Preferences section writes provider properties; this opens what it offers read-only',
  },
  {
    name: 'workflow-surface',
    title: 'WorkFlow list',
    entry: { label: /^\s*WorkFlow\s*$/i, popup: true },
    minimum: 1,
    province: 'all',
  },
  {
    name: 'scratch-surface',
    title: 'Scratch pad',
    entry: { label: /^\s*Scratch/i, popup: true },
    minimum: 1,
    province: 'all',
  },
];

function surfaceByName(name) {
  return SURFACES.find((surface) => surface.name === name) || null;
}

function surfacesForProvince(province) {
  if (!province) {
    return SURFACES.slice();
  }
  return SURFACES.filter((surface) => surface.province === 'all' || surface.province === province);
}

module.exports = { NEVER_OPEN, SURFACES, surfaceByName, surfacesForProvince };
