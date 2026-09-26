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
package io.github.carlos_emr.carlos.web.inboxhub;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards Rapid Review auto-advance behavior in the Inbox Hub JSP.
 */
@DisplayName("Inbox Hub Rapid Review JSP behavior")
@Tag("unit")
@Tag("fast")
@Tag("inboxhub")
class InboxhubFormRapidReviewUnitTest {

    private static final Path INBOXHUB_FORM =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "web", "inboxhub", "InboxhubForm.jsp");

    @Test
    @DisplayName("should open the row that followed the acknowledged one, not the first row of the table")
    void shouldOpenFollowingRow_whenAdvancingRapidReviewInPlace() throws Exception {
        // An alpha15 tester started Rapid Review part-way down the list and was handed the lab
        // at the TOP of the table after each acknowledgement. The in-place route must resolve
        // the remembered successor first and fall back to the first row only when there was
        // none; and it must NOT wait on a draw event, because nothing is being re-fetched and a
        // listener for a redraw that never comes would silently stop advancing.
        String jsp = Files.readString(INBOXHUB_FORM);

        assertThat(extractFunction(jsp, "openNextInboxItem"))
                .as("the in-place route opens the remembered successor before it considers the first row")
                .contains("const nextLink = nextInboxhubListRowLink() || document.querySelector('#inbox_table tbody tr a');")
                .contains("nextLink.click();")
                .as("and it advances preview mode to the card that took the acknowledged "
                        + "one's place rather than to the top of the list")
                .contains("nextCard[0].scrollIntoView({ block: 'start' });")
                .doesNotContain("draw.dt")
                .doesNotContain("setTimeout");

        assertThat(extractFunction(jsp, "removeInboxhubRow"))
                .as("the successor is remembered BEFORE the row goes, in both modes")
                .containsSubsequence(
                        "rememberNextInboxhubItem(row.next('tr'), row.prev('tr'));",
                        "jQuery('#inbox_table').DataTable().row(rowEl).remove().draw(false);",
                        "rememberNextInboxhubItem(card.next('.document-card'), card.prev('.document-card'));",
                        "card.remove();");
    }

    @Test
    @DisplayName("should advance Rapid Review after each drawn page on the re-fetch route")
    void shouldAdvanceAfterEachDrawnPage_whenRapidReviewFollowsRefetch() throws Exception {
        // The re-fetch route still has to wait for the redraw, because the row it wants does
        // not exist yet and may not land in the order it was appended; and the list arrives a
        // page at a time, so the row may not be on page one. The advance therefore runs from
        // addDataInInboxhubListTable AFTER the DataTable draw on EVERY page, opens the
        // remembered row as soon as a page holds it, and falls back to the first row only
        // once nothing remains to load.
        String jsp = Files.readString(INBOXHUB_FORM);

        assertThat(extractFunction(jsp, "advancePendingRapidReview"))
                .contains("if (!pendingRapidReviewOpen) { return; }")
                .contains("const nextLink = nextInboxhubListRowLink();")
                .as("while pages remain the remembered row may still arrive, so nothing is opened yet")
                .contains("if (hasRememberedNextInboxhubItem() && hasMoreData) { return; }")
                .contains("document.querySelector('#inbox_table tbody tr a')")
                .doesNotContain("setTimeout");

        int addDataFunctionStart = jsp.indexOf("function addDataInInboxhubListTable(data)");
        int pageOneStart = jsp.indexOf("if (page == 1) {", addDataFunctionStart);
        int redrawCall = jsp.indexOf("jQuery('#inbox_table').DataTable().draw(false);", pageOneStart);
        int pageOneAdvance = jsp.indexOf("advancePendingRapidReview();", pageOneStart);
        int pageOneReturn = jsp.indexOf("return;", pageOneStart);
        int laterPageAdvance = jsp.indexOf("advancePendingRapidReview();", pageOneReturn);

        assertThat(addDataFunctionStart).isNotNegative();
        assertThat(pageOneStart).isNotNegative();
        assertThat(redrawCall).isNotNegative();
        assertThat(pageOneAdvance).as("page one advances").isGreaterThan(redrawCall);
        assertThat(pageOneAdvance).as("before the early return").isLessThan(pageOneReturn);
        assertThat(laterPageAdvance).as("and every later page advances too").isNotNegative();
        assertThat(jsp).doesNotContain("openNextInboxItemAfterDraw");
    }

    @Test
    @DisplayName("should re-sync only the boundary page in preview mode while pages remain")
    void shouldResyncBoundaryPageOnly_whenPreviewPagesRemain() throws Exception {
        // "Preview is still slow on reloads": preview pages only as the clinician scrolls, so
        // the whole-search re-fetch that guarded the offset-paging boundary re-rendered every
        // card's iframe on almost every acknowledgement. Only the last loaded page can have
        // changed, so that page alone is re-fetched and merged without moving any card. The
        // behaviour is exercised in scripts/inbox-acknowledge-in-place.test.js; pinned here is
        // the contract wiring that test stubs around.
        String jsp = Files.readString(INBOXHUB_FORM);

        assertThat(extractFunction(jsp, "dropAcknowledgedInboxhubItem"))
                .as("the helper answers 'no full re-sync needed' once everything is loaded OR "
                        + "once preview has taken the boundary page on itself")
                .contains("const settled = !hasMoreData || resyncInboxhubPreviewBoundary();")
                .as("and it owns the Rapid Review decision for every route that reaches it: "
                        + "advance at once when settled, else after the redraw the re-fetch brings")
                .containsSubsequence(
                        "if (rapidReviewState) {",
                        "advanceRapidReviewOnce(segmentId, resolvedType);",
                        "armPendingRapidReview(inboxhubResultSetGeneration + 1);",
                        "return settled;");
        assertThat(extractFunction(jsp, "resyncInboxhubPreviewBoundary"))
                .as("a next-page fetch in flight may carry the pre-shift window; it is withdrawn and asked for again after the merge")
                .containsSubsequence(
                        "if (isFetchingData && currentFetchRequest) {",
                        "currentFetchRequest.abort();",
                        "isFetchingData = false;",
                        "resumePaging = true;",
                        "mergeInboxhubPreviewCards(data);",
                        "if (resumePaging) { fetchInboxhubViewData(); }");
        assertThat(extractFunction(jsp, "resetDataPageCount"))
                .as("a search the clinician makes drops a pending advance armed for another result set")
                .contains("if (!pendingRapidReviewOpen || pendingRapidReviewGeneration !== inboxhubResultSetGeneration) {");
        assertThat(extractFunction(jsp, "advanceRapidReviewOnce"))
                .as("the opener call and its broadcast both reach the helper; the second must not open a second result")
                .contains("if (advancedInboxhubItems[key]) { return; }")
                .contains("openNextInboxItem();");
        assertThat(extractFunction(jsp, "resyncInboxhubPreviewBoundary"))
                .as("list mode still needs the full re-fetch")
                .contains("if (jQuery('#inboxViewItems').length === 0) { return false; }")
                .as("preview increments page after each append, so the last loaded page is page - 1")
                .contains("const boundaryPage = page - 1;")
                .contains("\"&page=\" + boundaryPage + \"&pageSize=\" + pageSize")
                .as("a stale answer after the clinician changed the search must be dropped")
                .contains("if (generation !== inboxhubResultSetGeneration) { return; }");
        assertThat(extractFunction(jsp, "mergeInboxhubPreviewCards"))
                .as("rendered cards are never moved: moving an iframe reloads it")
                .contains("if (entry.rendered !== null || isInboxhubItemHandled(entry.segmentId, entry.labType)) { return; }")
                .contains("anchor.after(entry.card);")
                .contains("following.before(entry.card);")
                .contains("container.append(entry.card);")
                .as("the page's scripts are not executed; the end-of-results flag is read off the text")
                .contains("if (/hasMoreData\\s*=\\s*false/.test(data)) { hasMoreData = false; }");
        assertThat(extractFunction(jsp, "resetDataPageCount"))
                .as("a new result set invalidates any boundary answer still in flight")
                .containsSubsequence("forgetHandledInboxhubItems();", "inboxhubResultSetGeneration++;");
    }

    @Test
    @DisplayName("should leave date filters unchanged when toggling acknowledged results")
    void shouldLeaveDateFiltersUnchanged_whenTogglingAcknowledgedResults() throws Exception {
        String jsp = Files.readString(INBOXHUB_FORM);
        String toggleAcknowledged = extractToggleAcknowledged(jsp);

        assertThat(toggleAcknowledged)
                .contains("changeValueElementByName('query.status', checked ? 'A' : 'N');")
                .contains("fetchInboxhubData();")
                .doesNotContain("startDate")
                .doesNotContain("_flatpickr")
                .doesNotContain("new Date()");
    }

    @Test
    @DisplayName("should use the Inbox Hub context path for provider autocomplete")
    void shouldUseInboxContextPath_whenInitializingProviderAutocomplete() throws Exception {
        String jsp = Files.readString(INBOXHUB_FORM);

        assertThat(jsp)
                .contains("source: inboxContextPath + \"/provider/SearchProvider?method=labSearch\"")
                .doesNotContain("source: contextPath + \"/provider/SearchProvider?method=labSearch\"");
    }

    /**
     * One named function's body, delimited by brace matching.
     *
     * NOT by "everything up to the next landmark". That is how the draw-event assertion
     * above kept passing after openNextInboxItem stopped using a draw event: a second
     * function had been added between it and the landmark, so the extracted span covered
     * both and the assertion found its listener in the wrong one.
     */
    private String extractFunction(String jsp, String name) {
        // Matched on the opening parenthesis, not on "()": the functions pinned here take
        // parameters, and an empty-parenthesis match silently returns -1 for every one of them.
        int start = jsp.indexOf("function " + name + "(");
        assertThat(start).as("function %s must exist", name).isNotNegative();
        int open = jsp.indexOf('{', start);
        assertThat(open).isNotNegative();
        int depth = 0;
        for (int at = open; at < jsp.length(); at++) {
            char ch = jsp.charAt(at);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return jsp.substring(start, at + 1);
                }
            }
        }
        throw new AssertionError("unbalanced braces reading function " + name);
    }

    private String extractToggleAcknowledged(String jsp) {
        int start = jsp.indexOf("function toggleAcknowledged(checked)");
        int end = jsp.indexOf("\n    /**\n     * Toggles Rapid Review mode.", start);

        assertThat(start).isNotNegative();
        assertThat(end).isNotNegative();
        return jsp.substring(start, end);
    }
}
