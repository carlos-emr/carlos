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
package io.github.carlos_emr.carlos.waitinglist;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source-level guards for issue #4229: the Master Record pages must take waiting-list
 * availability from {@code DemographicEdit2Action} (and so from
 * {@link WaitingListAvailabilityService}) instead of re-deriving it, and the
 * waiting-list pages must encode list names, notes and dates they print.
 *
 * <p>The installed-browser check {@code scripts/waiting-list-playwright-checks.js}
 * proves the rendered behaviour; this test fails fast in CI if the inverted switch
 * or an unencoded value comes back.</p>
 *
 * @since 2026-10-15
 */
@DisplayName("Waiting-list Master Record JSP regressions (#4229)")
@Tag("unit")
@Tag("waitinglist")
class WaitingListMasterRecordJspRegressionTest {

    private static final Path DEMOGRAPHIC_JSP_DIR = Path.of("src/main/webapp/WEB-INF/jsp/demographic");
    private static final Path EDIT_JSP = DEMOGRAPHIC_JSP_DIR.resolve("edit.jsp");
    private static final Path EDIT_FORM_CLINICAL_JSP = DEMOGRAPHIC_JSP_DIR.resolve("edit-form-clinical.jsp");
    private static final Path REPORT_INDEX_JSP = Path.of("src/main/webapp/WEB-INF/jsp/report/reportindex.jsp");
    private static final Path DISPLAY_WAITING_LIST_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/waitinglist/DisplayWaitingList.jsp");
    private static final Path EDIT_WAITING_LIST_NAME_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/waitinglist/EditWaitingListName.jsp");

    /** The shape of the inverted switch: read-only whenever the feature is ON. */
    private static final Pattern INVERTED_SWITCH = Pattern.compile(
            "\"true\"\\.equals\\(oscarProps\\.getProperty\\(\"DEMOGRAPHIC_WAITING_LIST\"\\)\\)\\)\\s*\\{\\s*wLReadonly\\s*=\\s*\"readonly\"");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @ValueSource(strings = {"edit.jsp", "edit-form-clinical.jsp", "edit-form-personal.jsp", "edit-view.jsp"})
    @DisplayName("should never make the waiting list read-only because the feature is switched on")
    void shouldNotInvertTheFeatureSwitch_inMasterRecordPages(String page) throws IOException {
        String jsp = read(DEMOGRAPHIC_JSP_DIR.resolve(page));

        assertThat(INVERTED_SWITCH.matcher(jsp).find()).as(page).isFalse();
    }

    @Test
    @DisplayName("should show the Waiting List link from the action's availability decision")
    void shouldGateTheWaitingListLink_onTheActionAttribute() throws IOException {
        String jsp = read(DEMOGRAPHIC_JSP_DIR.resolve("patient-nav.jsp"));
        String model = read(Path.of("src/main/java/io/github/carlos_emr/carlos/demographic/pageUtil/PatientNavModel.java"));

        assertThat(read(EDIT_JSP)).contains("patient-nav.jsp");
        assertThat(jsp).contains("${nav.waitingListShown}");
        assertThat(model).contains("request.getAttribute(\"waitingListLinkVisible\")")
                .contains("WaitingListAvailabilityService.class).hasCurrentLists()")
                .doesNotContain("WaitingList.getInstance()");
    }

    @Test
    @DisplayName("should offer and enable exactly the lists the action supplies")
    void shouldOfferTheActionSuppliedLists_inTheEditForm() throws IOException {
        String jsp = read(EDIT_FORM_CLINICAL_JSP);

        assertThat(jsp).contains("request.getAttribute(\"selectableWaitingLists\")")
                .contains("String wLReadonly = selectableWaitingLists.isEmpty() ? \"readonly\" : \"\";")
                .contains("for (WaitingListName wln : selectableWaitingLists)")
                // The group lookup now lives in the action; the old inline cast threw a
                // NullPointerException for a session without a provider preference.
                .doesNotContain("waitingListNameDao.findCurrentByGroup(");
    }

    @Test
    @DisplayName("should encode the list name, note and date the edit form prints")
    void shouldEncodeWaitingListValues_inTheEditForm() throws IOException {
        String jsp = read(EDIT_FORM_CLINICAL_JSP);

        assertThat(jsp).contains("<%=SafeEncode.forHtmlContent(wln.getName())%>")
                .contains("value=\"<%=SafeEncode.forHtmlAttribute(wlnote)%>\"")
                .contains("value=\"<%=SafeEncode.forHtmlAttribute(wlReferralDate)%>\"")
                .doesNotContain("<%=wln.getName()%>")
                .doesNotContain("value=\"<%=wlnote%>\"");
    }

    @Test
    @DisplayName("should give the waiting-list management page a Report menu entry while the feature is on")
    void shouldLinkWaitingListManagement_fromTheReportIndex() throws IOException {
        String jsp = read(REPORT_INDEX_JSP);
        int gate = jsp.indexOf("<oscar:oscarPropertiesCheck property=\"DEMOGRAPHIC_WAITING_LIST\" value=\"true\">");
        int link = jsp.indexOf("/waitinglist/SetupDisplayWaitingList");

        assertThat(gate).isNotNegative();
        assertThat(link).isGreaterThan(gate);
        assertThat(jsp.substring(gate, link)).contains("objectName=\"_demographic\" rights=\"r\"");
        assertThat(jsp).contains("<fmt:message key=\"report.reportindex.btnWaiting\"/>");
    }

    @ParameterizedTest
    @ValueSource(strings = {"DisplayWaitingList.jsp", "EditWaitingListName.jsp"})
    @DisplayName("should encode list names on the waiting-list management pages")
    void shouldEncodeListNames_onManagementPages(String page) throws IOException {
        Path path = "DisplayWaitingList.jsp".equals(page) ? DISPLAY_WAITING_LIST_JSP : EDIT_WAITING_LIST_NAME_JSP;
        String jsp = read(path);

        assertThat(jsp).contains("<%@ taglib uri=\"carlos\" prefix=\"carlos\" %>")
                .contains("<carlos:encode value='<%= name %>'/>")
                .doesNotContain("<%=name%>");
    }
}
