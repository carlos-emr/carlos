/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins that the fax cover page, the fax and print confirmation page and the uncertain-fax page
 * all name the attachments a consultation lists that were left out, as the preview does.
 *
 * @since 2026-09-29
 */
@DisplayName("Consultation attachment warning JSP regressions")
@Tag("unit")
@Tag("consultation")
class ConsultationAttachmentWarningJspRegressionTest {

    private static final int MAX_PARENT_SEARCH_DEPTH = 5;
    private static final String CONFIRM_JSP =
            "src/main/webapp/WEB-INF/jsp/encounter/oscarConsultationRequest/ConfirmConsultationRequest.jsp";

    @ParameterizedTest
    @ValueSource(strings = {
            "src/main/webapp/WEB-INF/jsp/fax/CoverPage.jsp",
            CONFIRM_JSP,
            "src/main/webapp/WEB-INF/jsp/encounter/oscarConsultationRequest/FaxSubmissionUncertain.jsp"})
    @DisplayName("should list each unavailable attachment, encoded, under the shared heading")
    void shouldListUnavailableAttachments_forFaxAndPrintPages(String jspPath) throws Exception {
        String jsp = read(jspPath);

        int block = jsp.indexOf("<c:if test=\"${ not empty attachmentWarnings");
        assertThat(block).isGreaterThanOrEqualTo(0);
        String warning = jsp.substring(block, jsp.indexOf("</c:if>", block));
        assertThat(warning)
                .contains("<fmt:message key=\"encounter.oscarConsultationRequest.msgAttachmentsUnavailable\"/>")
                .contains("<c:forEach items=\"${ attachmentWarnings }\" var=\"attachmentWarning\">")
                .contains("<carlos:encode value=\"${ attachmentWarning }\"/>")
                .doesNotContain("<li>${");
    }

    @Test
    @DisplayName("should keep the confirmation page open while it shows an attachment warning")
    void shouldNotAutoClose_whenConfirmationShowsAttachmentWarning() throws Exception {
        String jsp = read(CONFIRM_JSP);

        int finishPage = jsp.indexOf("function finishPage(secs)");
        int guard = jsp.indexOf("if (<%= hasAttachmentWarnings %>)", finishPage);
        int autoClose = jsp.indexOf("window.setTimeout(closeOrReturn, secs * 1000);", finishPage);
        assertThat(finishPage).isGreaterThanOrEqualTo(0);
        int download = jsp.indexOf("downloadConsultForm(consultPDFName, consultPDF);", finishPage);
        // After the download branch, so a print with a warning still downloads; before the timer.
        assertThat(download).isGreaterThan(finishPage);
        assertThat(guard).isGreaterThan(download).isLessThan(autoClose);
        assertThat(jsp).contains("<% if (!\"true\".equals(isPreview) && !hasAttachmentWarnings) { %>");
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "es", "fr", "pl", "pt_BR"})
    @DisplayName("should define the heading in every locale, as the English placeholder")
    void shouldDefineHeading_forEveryLocale(String locale) throws Exception {
        String bundle = Files.readString(
                resolveProjectPath(Path.of("src/main/resources/oscarResources_" + locale + ".properties")),
                StandardCharsets.ISO_8859_1);

        assertThat(bundle).contains("encounter.oscarConsultationRequest.msgAttachmentsUnavailable="
                + "Some attachments listed on this consultation were unavailable and were not included:");
    }

    private static String read(String relativePath) throws Exception {
        return Files.readString(resolveProjectPath(Path.of(relativePath)), StandardCharsets.UTF_8);
    }

    private static Path resolveProjectPath(Path relative) {
        Path directory = Path.of("").toAbsolutePath();
        for (int depth = 0; depth <= MAX_PARENT_SEARCH_DEPTH && directory != null; depth++) {
            Path candidate = directory.resolve(relative);
            if (Files.exists(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("Could not find " + relative);
    }
}
