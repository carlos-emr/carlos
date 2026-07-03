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
    @DisplayName("should use DataTable draw event when opening next Rapid Review item")
    void shouldUseDataTableDrawEvent_whenOpeningNextRapidReviewItem() throws Exception {
        String jsp = Files.readString(INBOXHUB_FORM);
        String openNextInboxItem = extractOpenNextInboxItem(jsp);

        assertThat(openNextInboxItem)
                .contains("jQuery('#inbox_table').one('draw.dt', function()")
                .contains("document.querySelector('#inbox_table tbody tr a')")
                .contains("nextLink.click();")
                .doesNotContain("setTimeout");
    }

    @Test
    @DisplayName("should register Rapid Review draw listener before redrawing table")
    void shouldRegisterDrawListener_beforeRedrawingTable() throws Exception {
        String jsp = Files.readString(INBOXHUB_FORM);
        int addDataFunctionStart = jsp.indexOf("function addDataInInboxhubListTable(data)");
        int pageOneStart = jsp.indexOf("if (page == 1) {", addDataFunctionStart);
        int pendingRapidReviewBlock = jsp.indexOf("if (pendingRapidReviewOpen)", pageOneStart);
        int openNextCall = jsp.indexOf("openNextInboxItem();", pendingRapidReviewBlock);
        int redrawCall = jsp.indexOf("jQuery('#inbox_table').DataTable().draw(false);", pageOneStart);

        assertThat(addDataFunctionStart).isNotNegative();
        assertThat(pageOneStart).isNotNegative();
        assertThat(pendingRapidReviewBlock).isNotNegative();
        assertThat(openNextCall).isNotNegative();
        assertThat(redrawCall).isNotNegative();
        assertThat(openNextCall).isLessThan(redrawCall);
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

    private String extractOpenNextInboxItem(String jsp) {
        int start = jsp.indexOf("function openNextInboxItem()");
        int end = jsp.indexOf("\n    // State variables preserved", start);

        assertThat(start).isNotNegative();
        assertThat(end).isNotNegative();
        return jsp.substring(start, end);
    }

    private String extractToggleAcknowledged(String jsp) {
        int start = jsp.indexOf("function toggleAcknowledged(checked)");
        int end = jsp.indexOf("\n    /**\n     * Toggles Rapid Review mode.", start);

        assertThat(start).isNotNegative();
        assertThat(end).isNotNegative();
        return jsp.substring(start, end);
    }
}
