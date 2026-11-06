/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

/*
 * A Master Record link to another module, found by the URL it opens.
 *
 * Since #4468 the record's navigation is demographic/patient-nav.jsp: each link is a plain href inside
 * `.patient-nav`, which share/javascript/demographic/patient-nav.js opens as a popup. The rest of the record
 * (edit-view.jsp) and older builds (release/2026.08) still open the same URLs from an onclick. A check that
 * matches only `a[onclick*="..."]` finds nothing in the navigation, so this matches either form.
 * Since the alpha20 back-merge (#5007) the record's other links (edit.jsp) carry the URL in their href and open it
 * with popupPage(..., this.href), so the URL is in neither the navigation nor the onclick: that form matches too.
 */

/**
 * @param {import('playwright').Page} master the Master Record page
 * @param {...string} urlFragments parts of the URL the link opens; every one must be present
 * @returns {import('playwright').Locator} the matching links, in document order
 */
function masterRecordLink(master, ...urlFragments) {
  if (!urlFragments.length || urlFragments.some(fragment => typeof fragment !== 'string' || !fragment
    || fragment.includes('"') || fragment.includes('\\'))) {
    throw new Error(`masterRecordLink needs plain URL fragments, got ${JSON.stringify(urlFragments)}`);
  }
  const href = urlFragments.map(fragment => `[href*="${fragment}"]`).join('');
  const onclick = urlFragments.map(fragment => `[onclick*="${fragment}"]`).join('');
  return master.locator(`.patient-nav a${href}, a${onclick}, a${href}[onclick*="this.href"]`);
}

module.exports = { masterRecordLink };
