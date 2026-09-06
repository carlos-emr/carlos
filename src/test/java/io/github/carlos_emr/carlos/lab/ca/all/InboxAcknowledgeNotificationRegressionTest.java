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

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("should notify the inbox after a lab macro even when the macro does not close the window")
    void shouldNotifyInbox_whenLabMacroDoesNotCloseWindow() throws IOException {
        String labDisplay = read(LAB_DISPLAY_JSP);

        int notifyCall = labDisplay.indexOf("notifyInboxhubAfterMacro(formid);");
        int closeCall = labDisplay.indexOf("if (closeOnSuccess) {");
        assertThat(notifyCall)
                .as("lab macro success handler must notify the Inboxhub")
                .isGreaterThan(-1);
        assertThat(notifyCall)
                .as("the notification must not sit inside the closeOnSuccess branch")
                .isLessThan(closeCall);
    }

    @Test
    @DisplayName("should notify the inbox after a document macro")
    void shouldNotifyInbox_whenDocumentMacroSucceeds() throws IOException {
        assertThat(read(SHOW_DOCUMENT_JSP))
                .as("document macro success handler must notify the Inboxhub")
                .contains("notifyInboxhubAfterDocMacro(formEl);");
    }

    @Test
    @DisplayName("should name the acknowledged item on every inbox refresh broadcast")
    void shouldBroadcastSegmentId_fromEveryAcknowledgePath() throws IOException {
        // The id is what lets the inbox drop the item from its counters; a bare 'refresh'
        // message re-draws the list and leaves the badges counting an acknowledged item.
        assertThat(read(OSCAR_MDS_INDEX_JS))
                .contains("bc.postMessage({ action: 'refresh', segmentID: String(doclabid) });");
        assertThat(read(LAB_DISPLAY_JSP))
                .contains("bc.postMessage({ action: 'refresh', segmentID: segmentId });");
        assertThat(read(SHOW_DOCUMENT_JSP))
                .contains("bc.postMessage({ action: 'refresh', segmentID: segmentId });");
    }

    @Test
    @DisplayName("should drop the acknowledged item from the inbox counters on refresh")
    void shouldDecrementCounters_whenAcknowledgementIsBroadcast() throws IOException {
        String inboxhubForm = read(INBOXHUB_FORM_JSP);

        assertThat(inboxhubForm)
                .as("the refresh listener must drop the item before re-fetching the list")
                .contains("dropAcknowledgedInboxhubItem(acknowledgedId);");
        assertThat(inboxhubForm)
                .as("the stored totals, not just the rendered badges, must be decremented")
                .contains("[countInputId, 'totalResultsCount'].forEach(");
    }

    @Test
    @DisplayName("should decrement the stored totals from removeReport rather than the badge text")
    void shouldDecrementStoredTotals_whenRemovingAnInboxRow() throws IOException {
        // The badges are repainted from the hidden inputs on every list draw, so a badge-only
        // edit here is silently undone by the refresh that follows an acknowledgement.
        String listMode = read(INBOXHUB_LIST_MODE_JSP);

        assertThat(listMode).contains("decrementInboxhubStatFor(labType);");
        assertThat(listMode)
                .as("removeReport must not hand-edit the rendered badge")
                .doesNotContain("totalLabsCountStat');");
    }

    @Test
    @DisplayName("should count an acknowledged item once when the opener already removed its row")
    void shouldNotDoubleCount_whenOpenerAlreadyRemovedTheRow() throws IOException {
        // A popup whose window.opener survived has already called removeReport by the time its
        // broadcast lands; presence in the DOM is what makes the two routes idempotent.
        assertThat(read(INBOXHUB_FORM_JSP))
                .contains("const itemEl = jQuery('#labdoc_' + segmentId);")
                .contains("if (itemEl.length === 0) { return; }");
    }

    @Test
    @DisplayName("should still accept the plain refresh message senders that carry no item id")
    void shouldTolerateIdlessMessage_fromLegacySenders() throws IOException {
        assertThat(read(INBOXHUB_FORM_JSP))
                .as("a message without a segmentID must still refresh rather than throw")
                .contains("const acknowledgedId = (event && event.data && event.data.segmentID) "
                        + "? event.data.segmentID : null;");
    }
}
