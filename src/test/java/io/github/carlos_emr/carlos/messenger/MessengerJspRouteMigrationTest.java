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
package io.github.carlos_emr.carlos.messenger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the messenger attachment-preview JSP migration so merges do not
 * regress the new Struts-backed routes or accidentally keep conflict markers
 * in the checked-in JSPs.
 */
@DisplayName("Messenger JSP route migration tests")
@Tag("unit")
@Tag("fast")
@Tag("messenger")
class MessengerJspRouteMigrationTest {

    private static final Path ATTACHMENT_FRAMESET =
            Path.of("src/main/webapp/WEB-INF/jsp/messenger/attachmentFrameset.jsp");
    private static final Path GENERATE_PREVIEW =
            Path.of("src/main/webapp/WEB-INF/jsp/messenger/generatePreviewPDF.jsp");
    private static final Path SELF_CLOSE_AND_REFRESH_OPENER =
            Path.of("src/main/webapp/WEB-INF/jsp/messenger/selfCloseAndRefreshOpener.jsp");
    private static final Path MESSENGER_SCHEDULE_NAV =
            Path.of("src/main/webapp/WEB-INF/jsp/messenger/messengerScheduleNav.jspf");
    private static final Path LEGACY_ADJUST_ATTACHMENTS =
            Path.of("src/main/webapp/messenger/AdjustAttachments.jsp");
    private static final List<Path> MESSENGER_EXIT_PAGES = List.of(
            Path.of("src/main/webapp/WEB-INF/jsp/messenger/DisplayMessages.jsp"),
            Path.of("src/main/webapp/WEB-INF/jsp/messenger/CreateMessage.jsp"),
            Path.of("src/main/webapp/WEB-INF/jsp/messenger/ViewMessage.jsp"),
            Path.of("src/main/webapp/WEB-INF/jsp/messenger/SentMessage.jsp"));

    @Test
    @DisplayName("attachment frameset should target the gated PreviewPDF action")
    void attachmentFramesetShouldTargetPreviewAction() throws Exception {
        String jsp = Files.readString(ATTACHMENT_FRAMESET);

        assertThat(jsp)
                .doesNotContain("<<<<<<<", "=======", ">>>>>>>")
                .contains("/messenger/PreviewPDF?demographic_no=")
                .doesNotContain("generatePreviewPDF.jsp?demographic_no=");
    }

    @Test
    @DisplayName("generate preview JSP should post item keys to Doc2PDF and never captured page HTML")
    void shouldPostItemKeysNotHtml_forGeneratePreviewJsp() throws Exception {
        String jsp = Files.readString(GENERATE_PREVIEW);

        // #4133: the chooser used to load each item into a hidden frame and post its HTML as
        // srcText. It now names items; MsgPdfAttachmentResolver owns the routes.
        assertThat(jsp)
                .doesNotContain("<<<<<<<", "=======", ">>>>>>>")
                .contains("<form id=\"attachForm\" action=\"${pageContext.request.contextPath}/messenger/Doc2PDF\" method=\"post\">")
                .contains("name=\"item\" value=\"demographic\"")
                .contains("name=\"item\" value=\"encounter\"")
                .contains("name=\"item\" value=\"prescriptions\"")
                .contains("name=\"previewItem\"")
                // Items are offered only with the read Doc2PDF enforces, and the encounter is not
                // even looked up without _eChart read (its existence and timestamp are chart data).
                .contains("MsgAttachPDF2Action.canReadItem(")
                .contains("isAllowedAccessToPatientRecord(loggedInInfo, demographicNoInt)")
                .contains("canEncounter ? eChartDao.getLatestChart(demographicNoInt) : null")
                .contains("<c:if test=\"${canDemographic}\">")
                .contains("<c:if test=\"${canPrescriptions}\">")
                .contains("/securityError?type=_msg")
                .contains("errorPage=\"/WEB-INF/jsp/error/errorpage.jsp\"")
                .doesNotContain("srcText")
                .doesNotContain("srcFrame.document")
                .doesNotContain("uriArray")
                .doesNotContain("titleArray")
                .doesNotContain("/messenger/Doc2PDF.do")
                .doesNotContain("/securityError.jsp?type=_msg");
        // The patient gate runs before any patient data is loaded or put in the session.
        assertThat(jsp.indexOf("isAllowedAccessToPatientRecord("))
                .isLessThan(jsp.indexOf("new DemographicData()"))
                .isLessThan(jsp.indexOf("setAttribute(\"EctSessionBean\""));
    }

    @Test
    @DisplayName("attachment frameset should have no hidden source frame to capture pages into")
    void shouldHaveNoSourceFrame_inAttachmentFrameset() throws Exception {
        String jsp = Files.readString(ATTACHMENT_FRAMESET);

        assertThat(jsp)
                .contains("<iframe name=\"main\"")
                .doesNotContain("<frameset")
                .doesNotContain("name=\"srcFrame\"");
    }

    @Test
    @DisplayName("attachment frameset should encode the locale lang attribute")
    void attachmentFramesetShouldEncodeLocaleLangAttribute() throws Exception {
        String jsp = Files.readString(ATTACHMENT_FRAMESET);

        assertThat(jsp)
                .contains("<%@ taglib uri=\"carlos\" prefix=\"carlos\" %>")
                .contains("<html lang=\"${carlos:forHtmlAttribute(pageContext.request.locale.language)}\">")
                .doesNotContain("<%@ taglib uri=\"owasp.encoder.jakarta\" prefix=\"e\" %>")
                .doesNotContain("<html lang=\"${pageContext.request.locale.language}\">");
    }

    @Test
    @DisplayName("generate preview JSP should localize its labels and encode the locale lang attribute")
    void shouldLocalizeLabels_inGeneratePreviewJsp() throws Exception {
        String jsp = Files.readString(GENERATE_PREVIEW);

        assertThat(jsp)
                .contains("<fmt:message key=\"messenger.generatePreviewPDF.information\" var=\"informationLabel\"/>")
                .contains("<fmt:message key=\"messenger.generatePreviewPDF.encounter\" var=\"encounterLabel\"/>")
                .contains("<html lang=\"${carlos:forHtmlAttribute(pageContext.request.locale.language)}\">")
                .doesNotContain("<%@ taglib uri=\"owasp.encoder.jakarta\" prefix=\"e\" %>")
                .doesNotContain("<html lang=\"${pageContext.request.locale.language}\">");
    }

    @Test
    @DisplayName("self-close helper should always close the popup in finally")
    void selfCloseHelperShouldAlwaysClosePopup() throws Exception {
        String jsp = Files.readString(SELF_CLOSE_AND_REFRESH_OPENER);

        assertThat(jsp)
                .contains("try {")
                .contains("} catch (e) {")
                .contains("} finally {")
                .contains("!top.opener.closed")
                .contains("top.window.close();");
    }

    @Test
    @DisplayName("messenger exit controls should be hidden only in same-tab schedule navigation")
    void shouldHideExitControls_whenSameTabScheduleNavigation() throws Exception {
        String helper = Files.readString(MESSENGER_SCHEDULE_NAV);

        assertThat(helper)
                .contains("boolean showScheduleNav = \"1\".equals(request.getParameter(\"scheduleNav\"));")
                .contains("Object messengerProvider = session.getAttribute(\"user\");")
                .contains("if (messengerProvider instanceof String)")
                .contains("UserProperty.resolveScheduleNavigationMode")
                .contains("UserProperty.SCHEDULE_NAVIGATION_MODE_FOCUSED")
                .contains("boolean showMessengerExitButton = !showScheduleNav")
                .doesNotContain("(String) session.getAttribute(\"user\")")
                .doesNotContain("UserProperty.SCHEDULE_NAVIGATION_MODE_TAB.equals(messengerScheduleNavigationMode)");

        for (Path page : MESSENGER_EXIT_PAGES) {
            String jsp = Files.readString(page);

            assertThat(jsp)
                    .contains("<%@ include file=\"messengerScheduleNav.jspf\" %>")
                    .contains("showMessengerExitButton")
                    .contains("BackToCarlos()");
        }
    }

    @Test
    @DisplayName("legacy AdjustAttachments JSP should stay removed")
    void legacyAdjustAttachmentsJspShouldStayRemoved() {
        assertThat(LEGACY_ADJUST_ATTACHMENTS)
                .doesNotExist();
    }
}
