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
    private static final Path LEGACY_ADJUST_ATTACHMENTS =
            Path.of("src/main/webapp/messenger/AdjustAttachments.jsp");

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
    @DisplayName("generate preview JSP should use migrated messenger routes without conflict markers")
    void generatePreviewJspShouldUseMigratedRoutes() throws Exception {
        String jsp = Files.readString(GENERATE_PREVIEW);

        assertThat(jsp)
                .doesNotContain("<<<<<<<", "=======", ">>>>>>>")
                .contains("/messenger/Doc2PDF")
                .contains("/demographic/DemographicPdfLabel?demographic_no=")
                .contains("/encounter/ViewEcharthistoryprint?echartid=")
                .contains("/rx/ViewPrintDrugProfile2?demographic_no=")
                .contains("/securityError?type=_msg")
                .contains("errorPage=\"/WEB-INF/jsp/error/errorpage.jsp\"")
                .doesNotContain("/messenger/Doc2PDF.do")
                .doesNotContain("/demographic/DemographicPdfLabel.do")
                .doesNotContain("echarthistoryprint.jsp")
                .doesNotContain("PrintDrugProfile2.jsp")
                .doesNotContain("/securityError.jsp?type=_msg");
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
    @DisplayName("generate preview JSP should localize attachment titles and restore checked batch indexes")
    void generatePreviewJspShouldLocalizeAttachmentTitlesAndRestoreIndexes() throws Exception {
        String jsp = Files.readString(GENERATE_PREVIEW);

        assertThat(jsp)
                .contains("<fmt:message key=\"messenger.generatePreviewPDF.information\" var=\"informationLabel\"/>")
                .contains("<fmt:message key=\"messenger.generatePreviewPDF.encounter\" var=\"encounterLabel\"/>")
                .contains("request.getParameterValues(\"indexArray\")")
                .contains("selectedIndexes.contains(")
                .contains("checked")
                .contains("<html lang=\"${carlos:forHtmlAttribute(pageContext.request.locale.language)}\">")
                .doesNotContain("<%@ taglib uri=\"owasp.encoder.jakarta\" prefix=\"e\" %>")
                .doesNotContain("pageContext.setAttribute(\"demoTitleValue\", demoName + \" information\");")
                .doesNotContain("pageContext.setAttribute(\"ecTitleValue\", \"Encounter: \" + ec.getTimestamp().toString());")
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
    @DisplayName("legacy AdjustAttachments JSP should stay removed")
    void legacyAdjustAttachmentsJspShouldStayRemoved() {
        assertThat(LEGACY_ADJUST_ATTACHMENTS)
                .doesNotExist();
    }
}
