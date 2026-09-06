/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.lab.ca.all;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Pins the browser side of "an acknowledged item leaves the inbox".
 *
 * <p>Two things kept an acknowledged lab visible for alpha testers, and neither is reachable from
 * a Java test: a lab macro that only told the Inboxhub anything when it was configured to close
 * its window, and an inbox refresh that re-fetched the result LIST while its Documents/Labs/HRMs
 * counters kept counting the acknowledged item until a full page reload. Both are one-line
 * regressions to re-introduce, so the wiring is asserted at the source level here.
 *
 * @since 2026-09-06
 */
@DisplayName("Inbox acknowledge notification regression tests")
@Tag("unit")
@Tag("lab")
class InboxAcknowledgeNotificationRegressionTest {

    private static final Path LAB_DISPLAY_JSP = Path.of(
            "src", "main", "webapp", "WEB-INF", "jsp", "lab", "CA", "ALL", "labDisplay.jsp");
    private static final Path SHOW_DOCUMENT_JSP = Path.of(
            "src", "main", "webapp", "WEB-INF", "jsp", "documentManager", "showDocument.jsp");
    private static final Path INBOXHUB_FORM_JSP = Path.of(
            "src", "main", "webapp", "WEB-INF", "jsp", "web", "inboxhub", "InboxhubForm.jsp");
    private static final Path INBOXHUB_LIST_MODE_JSP = Path.of(
            "src", "main", "webapp", "WEB-INF", "jsp", "web", "inboxhub", "InboxhubListMode.jsp");
    private static final Path OSCAR_MDS_INDEX_JS = Path.of(
            "src", "main", "webapp", "share", "javascript", "oscarMDSIndex.js");
    private static final Path REPORT_MACRO_ACTION = Path.of(
            "src", "main", "java", "io", "github", "carlos_emr", "carlos", "mds", "pageUtil",
            "ReportMacro2Action.java");
    private static final Path HRM_ACTIONS_JS = Path.of(
            "src", "main", "webapp", "hospitalReportManager", "hrmActions.js");
    private static final Path COMMON_LAB_RESULT_DATA = Path.of(
            "src", "main", "java", "io", "github", "carlos_emr", "carlos", "lab", "ca", "on",
            "CommonLabResultData.java");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("should notify the inbox after a lab macro even when the macro does not close the window")
    void shouldNotifyInbox_whenLabMacroDoesNotCloseWindow() throws IOException {
        String labDisplay = read(LAB_DISPLAY_JSP);

        int notifyCall = labDisplay.indexOf("notifyInboxhubAfterMacro(formid, json.clearedCount);");
        int closeCall = labDisplay.indexOf("if (closeOnSuccess) {");
        assertThat(notifyCall)
                .as("lab macro success handler must notify the Inboxhub")
                .isGreaterThan(-1);
        assertThat(notifyCall)
                .as("the notification must not sit inside the closeOnSuccess branch")
                .isLessThan(closeCall);
    }

    @Test
    @DisplayName("should tell the inbox only when the macro actually acknowledged something")
    void shouldNotNotifyInbox_whenMacroDidNotAcknowledge() throws IOException {
        // A macro need not acknowledge: one that only files a tickler runs to completion and
        // leaves the lab NEW. Dropping it from the inbox on success alone hides work nobody
        // has done, so both macro handlers gate on the server's acknowledged flag.
        String labDisplay = read(LAB_DISPLAY_JSP);
        assertThat(labDisplay).contains("if (json.acknowledged) {");
        assertThat(labDisplay)
                .as("closing the window must not take an unacknowledged lab out of the inbox")
                .contains("closeLabAfterMacro(formid, json.acknowledged);")
                .contains("if (acknowledged && self.opener "
                        + "&& typeof self.opener.removeInboxhubRow === 'function'");
        assertThat(read(SHOW_DOCUMENT_JSP)).contains("if (json.acknowledged) {");
        assertThat(read(REPORT_MACRO_ACTION))
                .as("the server must report acknowledgement separately from success")
                .contains("result.put(\"acknowledged\", outcome.acknowledged());")
                .contains("return MacroOutcome.ran(acknowledged, clearedCount);");
    }

    @Test
    @DisplayName("should notify the inbox after a document macro")
    void shouldNotifyInbox_whenDocumentMacroSucceeds() throws IOException {
        assertThat(read(SHOW_DOCUMENT_JSP))
                .as("document macro success handler must notify the Inboxhub")
                .contains("notifyInboxhubAfterDocMacro(formEl, json.clearedCount);");
    }

    @Test
    @DisplayName("should name the acknowledged item and its type on every inbox refresh broadcast")
    void shouldBroadcastSegmentIdAndType_fromEveryAcknowledgePath() throws IOException {
        // The id is what lets the inbox drop the item from its counters; a bare 'refresh'
        // message re-draws the list and leaves the badges counting an acknowledged item.
        // The type goes with it because segment ids are not unique across report types.
        assertThat(read(OSCAR_MDS_INDEX_JS))
                .contains("segmentID: String(doclabid),")
                .contains("labType: labType,");
        assertThat(read(LAB_DISPLAY_JSP))
                .contains("segmentID: segmentId,")
                .contains("labType: labType,");
        assertThat(read(SHOW_DOCUMENT_JSP))
                .contains("segmentID: segmentId,")
                .contains("labType: labType,");
        assertThat(read(HRM_ACTIONS_JS))
                .contains("bc.postMessage({ action: 'refresh', segmentID: String(reportId), labType: 'HRM' });");
    }

    @Test
    @DisplayName("should match an inbox item by type as well as id, because ids repeat across types")
    void shouldMatchInboxItem_byTypeAndId() throws IOException {
        // Documents, HRM reports and HL7 labs have independent key sequences and all render
        // as id="labdoc_<id>", so an id alone can name another type's row — and decrementing
        // the wrong stored total is not repaired by the list re-fetch.
        String inboxhubForm = read(INBOXHUB_FORM_JSP);

        assertThat(inboxhubForm)
                .contains("candidates.filter('[data-lab-type=\"' + labType + '\"]')");
        assertThat(read(INBOXHUB_LIST_MODE_JSP))
                .as("removeReport must resolve its row through the type-qualified lookup")
                .contains("inboxhubItemElement(reportId, labType)")
                .doesNotContain("jQuery(\"#labdoc_\" + reportId)");
    }

    @Test
    @DisplayName("should move the overall total only when the type's own total moved")
    void shouldKeepTotalsConsistent_whenTypeCountIsAlreadyZero() throws IOException {
        // Decrementing the type total and the overall total independently let a type already
        // at zero walk the "all results" figure below the truth on every stray message.
        assertThat(read(INBOXHUB_FORM_JSP))
                .contains("if (isNaN(typeCount) || typeCount <= 0) { return; }");
    }

    @Test
    @DisplayName("should ignore a broadcast whose id or type is not shaped like a real one")
    void shouldIgnoreBroadcast_withMalformedIdentifiers() throws IOException {
        // These values reach a jQuery attribute selector and the counter bookkeeping, so a
        // malformed id must not be interpolated, nor mint fresh keys that each buy another
        // decrement of a counter.
        String inboxhubForm = read(INBOXHUB_FORM_JSP);

        assertThat(inboxhubForm)
                .contains("return value !== null && value !== undefined && /^[A-Za-z0-9_-]+$/.test(String(value));");
        assertThat(inboxhubForm)
                .as("the counter path must validate, not just the DOM lookup")
                .contains("if (!isInboxhubItemToken(segmentId) || !isInboxhubItemToken(labType)) { return; }");
    }

    @Test
    @DisplayName("should still update the counters when the acknowledged row is no longer rendered")
    void shouldCountAcknowledgedItem_whenRowIsAlreadyGone() throws IOException {
        // A filter change while the popup was open removes the row without acknowledging
        // anything, and the stored totals still count the item. Keying the guard on the item
        // rather than on DOM presence is what keeps the badge correct in that case.
        assertThat(read(INBOXHUB_FORM_JSP))
                .contains("countAcknowledgedInboxhubItem(segmentId, resolvedType, clearedCount);")
                .contains("if (countedAcknowledgedItems[key]) { return; }");
    }

    @Test
    @DisplayName("should drop the acknowledged item from the inbox counters on refresh")
    void shouldDecrementCounters_whenAcknowledgementIsBroadcast() throws IOException {
        String inboxhubForm = read(INBOXHUB_FORM_JSP);

        assertThat(inboxhubForm)
                .as("the refresh listener must drop the item before re-fetching the list")
                .contains("dropAcknowledgedInboxhubItem(acknowledgedId, acknowledgedType, acknowledgedRows);");
        assertThat(inboxhubForm)
                .as("the stored totals, not just the rendered badges, must be decremented")
                .contains("typeInput.val(typeCount - taken);")
                .contains("allInput.val(Math.max(0, allCount - taken));");
    }

    @Test
    @DisplayName("should decrement the stored totals from removeReport rather than the badge text")
    void shouldDecrementStoredTotals_whenRemovingAnInboxRow() throws IOException {
        // The badges are repainted from the hidden inputs on every list draw, so a badge-only
        // edit here is silently undone by the refresh that follows an acknowledgement.
        String listMode = read(INBOXHUB_LIST_MODE_JSP);

        assertThat(listMode).contains("countAcknowledgedInboxhubItem(reportId, resolvedType);");
        assertThat(listMode)
                .as("removeReport must not hand-edit the rendered badge")
                .doesNotContain("totalLabsCountStat');");
    }

    @Test
    @DisplayName("should count one routing row per cleared lab version, not one per collapsed row")
    void shouldCountEveryClearedRoutingRow_forAMultiVersionLab() throws IOException {
        // The inbox counters count providerLabRouting rows — one per lab VERSION — while the
        // list collapses a version chain to a single row. Acknowledging a two-version lab
        // therefore removes one row but takes two rows out of NEW, and a client that assumed
        // one left the badge one ahead of the figure the next page load computes.
        assertThat(read(COMMON_LAB_RESULT_DATA))
                .as("the server must report how many routing rows it cleared")
                .contains("return 1 + olderLabNos.size();");
        assertThat(read(REPORT_MACRO_ACTION))
                .as("the macro response must carry that count to the browser")
                .contains("result.put(\"clearedCount\", outcome.clearedCount());");
        assertThat(read(INBOXHUB_FORM_JSP))
                .as("the listener must move the totals by the reported count, not by one")
                .contains("decrementInboxhubStatFor(labType, (isNaN(rows) || rows < 1) ? 1 : rows);");
    }

    @Test
    @DisplayName("should count one routing row for each id the manual acknowledge path clears")
    void shouldCountPerCall_whenOpenerClearsEachChainVersion() throws IOException {
        // The manual path has the chain client-side and calls removeReport once per id it
        // cleared, so each call is exactly one routing row. Making that count conditional on
        // a row being present dropped the older versions from the badge, because the inbox
        // collapses a chain and only the newest version ever has a row.
        assertThat(read(INBOXHUB_LIST_MODE_JSP))
                .as("removeReport counts its call, whether or not a row was on screen")
                .contains("removeInboxhubRow(reportId, resolvedType);")
                .contains("countAcknowledgedInboxhubItem(reportId, resolvedType);")
                .doesNotContain("if (rowEl.length === 0) { return; }");
        assertThat(read(OSCAR_MDS_INDEX_JS))
                .as("the opener loop must walk the same versions the server files")
                .contains("return at < 0 ? [target] : chain.slice(0, at + 1);")
                .contains("notifyInboxhubAcknowledged(doclabid, data.labType, clearedIds.length);");
    }

    @Test
    @DisplayName("should name the report type on every document acknowledge that reaches the opener")
    void shouldPassReportType_fromEveryDocumentOpenerCall() throws IOException {
        // Segment ids are not unique across report types, so an untyped call can remove a
        // lab's row and decrement the Labs total when a document was acknowledged.
        assertThat(read(OSCAR_MDS_INDEX_JS))
                .contains("self.opener.removeReport(num, 'DOC');")
                .contains("self.opener.removeReport(docId, type);")
                .as("no acknowledge path may reach removeReport without a type")
                .doesNotContain("self.opener.removeReport(num);")
                .doesNotContain("self.opener.removeReport(docId);");
    }

    @Test
    @DisplayName("should do nothing for an untyped id that two report types share")
    void shouldSkipAmbiguousItem_whenTypeIsUnknown() throws IOException {
        // One match is unambiguous; two means the id is shared across report types, and
        // picking either would remove a row and decrement a total at random.
        assertThat(read(INBOXHUB_FORM_JSP))
                .contains("return candidates.length === 1 ? candidates : jQuery();");
    }

    @Test
    @DisplayName("should count an acknowledged item once when the opener already removed its row")
    void shouldNotDoubleCount_whenOpenerAlreadyRemovedTheRow() throws IOException {
        // A popup whose window.opener survived has already called removeReport by the time
        // its broadcast lands; the per-item key is what makes the two routes idempotent.
        assertThat(read(INBOXHUB_FORM_JSP))
                .contains("countedAcknowledgedItems[key] = true;")
                .contains("var countedAcknowledgedItems = Object.create(null);");
    }

    @Test
    @DisplayName("should not tell the inbox anything when a document macro reports failure")
    void shouldNotNotifyInbox_whenDocumentMacroReportsFailure() throws IOException {
        // RunMacro reports a logical failure as HTTP 200 with {"success": false}; notifying
        // on response.ok alone would drop a document that was never acknowledged.
        String showDocument = read(SHOW_DOCUMENT_JSP);

        int successCheck = showDocument.indexOf("if (!json.success) {");
        int notifyCall = showDocument.indexOf("notifyInboxhubAfterDocMacro(formEl, json.clearedCount);");
        assertThat(successCheck).as("document macro must inspect the JSON body").isGreaterThan(-1);
        assertThat(successCheck)
                .as("the failure check must gate the notification")
                .isLessThan(notifyCall);
    }

    @Test
    @DisplayName("should touch the inbox table only when it is the mode on screen")
    void shouldGuardDataTableAccess_whenRemovingFromPreviewMode() throws IOException {
        // Popups call removeReport through window.opener and cannot know which mode the
        // inbox is showing; preview mode has cards and no #inbox_table, and reaching for the
        // DataTable API there would throw and take the counter update down with it.
        assertThat(read(INBOXHUB_FORM_JSP))
                .contains("if (jQuery('#inbox_table').length === 0) { return; }");
    }

    @Test
    @DisplayName("should still accept the plain refresh message senders that carry no item id")
    void shouldTolerateIdlessMessage_fromLegacySenders() throws IOException {
        assertThat(read(INBOXHUB_FORM_JSP))
                .as("a message without a segmentID must still refresh rather than throw")
                .contains("const acknowledgedId = (event && event.data && event.data.segmentID) "
                        + "? event.data.segmentID : null;")
                .contains("const acknowledgedType = (event && event.data && event.data.labType) "
                        + "? event.data.labType : null;");
    }
}
