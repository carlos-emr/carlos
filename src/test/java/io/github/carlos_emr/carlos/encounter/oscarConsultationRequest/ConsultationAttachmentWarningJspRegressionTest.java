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

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

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
    @DisplayName("should list each left-out attachment, worded from the bundle and encoded, under the shared heading")
    void shouldListUnavailableAttachments_forFaxAndPrintPages(String jspPath) throws Exception {
        String jsp = read(jspPath);

        int block = jsp.indexOf("<c:if test=\"${ not empty attachmentWarnings");
        assertThat(block).isGreaterThanOrEqualTo(0);
        String warning = jsp.substring(block, jsp.indexOf("</ul>", block));
        assertThat(warning)
                .contains("<fmt:message key=\"encounter.oscarConsultationRequest.msgAttachmentsUnavailable\"/>")
                .contains("<c:forEach items=\"${ attachmentWarnings }\" var=\"attachmentWarning\">")
                .contains("<fmt:message key=\"${ attachmentWarning.typeLabelKey }\"/>")
                .contains("<fmt:message key=\"${ attachmentWarning.messageKey }\"><fmt:param value=\"${ attachmentTypeLabel }\"/>"
                        + "<fmt:param value=\"${ attachmentWarning.id }\"/></fmt:message>")
                .contains("<li><carlos:encode value=\"${ attachmentWarningText }\"/></li>")
                .doesNotContain("<li>${");
    }

    @Test
    @DisplayName("should make staff confirm, before the fax goes, that the listed attachments will be left out")
    void shouldRequireConfirmation_beforeFaxingWithoutUnavailableAttachments() throws Exception {
        String jsp = read("src/main/webapp/WEB-INF/jsp/fax/CoverPage.jsp");

        int block = jsp.indexOf("<c:if test=\"${ not empty attachmentWarnings and transactionType eq 'CONSULTATION' }\">");
        int form = jsp.indexOf("<form id=\"coverPageForm\"");
        int formEnd = jsp.indexOf("</form>", form);
        // The consult-only block ends where the consult's documents card begins: the box must
        // stay inside it, or every eForm and document fax would be blocked by submitForm.
        int blockEnd = jsp.indexOf("<c:if test=\"${ not empty documents and transactionType eq 'CONSULTATION' }\">", block);
        assertThat(block).isGreaterThan(form).isLessThan(formEnd);
        assertThat(blockEnd).isGreaterThan(block).isLessThan(formEnd);
        // The keys the page listed go back with the fax, and the box must be ticked.
        assertThat(jsp.substring(block, blockEnd))
                .contains("name=\"confirmedUnavailableAttachments\"")
                .contains("<carlos:encode value='${ confirmedWarning.key }' context='htmlAttribute'/>")
                .contains("id=\"confirmSendWithoutUnavailable\"")
                .contains("name=\"confirmSendWithoutUnavailable\" value=\"true\" required>")
                .contains("<fmt:message key=\"consultation.fax.confirmSendWithoutUnavailable\"/>");
        // The form is novalidate, so the submit handler checks the box itself, before the send.
        int submit = jsp.indexOf("function submitForm(event)");
        int check = jsp.indexOf("if (confirmLeftOut && !confirmLeftOut.checked) {", submit);
        int send = jsp.indexOf("return ShowSpin(true);", submit);
        assertThat(check).isGreaterThan(submit).isLessThan(send);
        // jQuery Validate reads the HTML required too; its own rule for the box is switched off so
        // it adds no untranslated "This field is required." label next to the browser's prompt.
        int rules = jsp.indexOf("$('#coverPageForm').validate({");
        assertThat(jsp.substring(rules, jsp.indexOf("messages:", rules)).replaceAll("\\s+", " "))
                .contains("confirmSendWithoutUnavailable: { required: false }");
    }

    @Test
    @DisplayName("should preselect the default referring provider when a saved consult has none")
    void shouldPreselectDefaultReferringProvider_whenSavedProviderIsBlank() throws Exception {
        String jsp = read("src/main/webapp/WEB-INF/jsp/encounter/oscarConsultationRequest/ConsultationFormRequest.jsp");

        // EctConsultationFormRequestUtil turns a NULL provider into "", so "" must count as none.
        assertThat(jsp)
                .contains("(StringUtils.isNullOrEmpty(consultUtil.providerNo) && p.getProviderNo().equalsIgnoreCase(referringProviderDefault))")
                .doesNotContain("(consultUtil.providerNo == null && referringProviderDefault.equalsIgnoreCase(p.getProviderNo()))");
    }

    @Test
    @DisplayName("should not list a provider with no number when a saved consult has none")
    void shouldSkipInactiveProviderLookup_whenSavedProviderIsBlank() throws Exception {
        String jsp = read("src/main/webapp/WEB-INF/jsp/encounter/oscarConsultationRequest/ConsultationFormRequest.jsp");

        // rx.getProvider("") gave a Provider with a null number, and the provider list then threw a
        // NullPointerException on getProviderNo().equalsIgnoreCase("-1"), so the consult would not open.
        assertThat(jsp)
                .contains("if (!isProviderActive && !StringUtils.isNullOrEmpty(consultUtil.providerNo)) {")
                .contains("if (inactiveProvider != null && inactiveProvider.getProviderNo() != null) {")
                .doesNotContain("if (!isProviderActive && consultUtil.providerNo != null) {");
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

    private static final String PREFIX = "encounter.oscarConsultationRequest.";
    /** Keys worded through MessageFormat (fmt:param or ConsultAttachmentWarning), where a lone ' is eaten. */
    private static final List<String> FORMATTED_KEYS = List.of(
            PREFIX + "attachmentWarning.unavailable", PREFIX + "attachmentWarning.fileUnavailable",
            PREFIX + "attachmentWarning.notRendered",
            PREFIX + "attachmentWarning.printBlocked", "consultation.fax.unavailableNotConfirmed",
            "consultation.fax.attachmentsNotRendered");
    private static final List<String> NEW_KEYS = List.of(
            PREFIX + "msgAttachmentsUnavailable",
            PREFIX + "ConsultationFormRequest.msgPreviewAttachmentsUnavailable",
            PREFIX + "ConsultationFormRequest.msgPreviewRequestFailed",
            PREFIX + "attachmentWarning.unavailable", PREFIX + "attachmentWarning.fileUnavailable",
            PREFIX + "attachmentWarning.notRendered",
            PREFIX + "attachmentWarning.printBlocked",
            PREFIX + "attachmentType.document", PREFIX + "attachmentType.lab", PREFIX + "attachmentType.eform",
            PREFIX + "attachmentType.hrm", PREFIX + "attachmentType.form", PREFIX + "attachmentType.unknown",
            "consultation.fax.confirmSendWithoutUnavailable", "consultation.fax.unavailableNotConfirmed",
            "consultation.fax.attachmentsNotRendered");

    @ParameterizedTest
    @ValueSource(strings = {"es", "fr", "pl", "pt_BR"})
    @DisplayName("should translate every new attachment-warning key, keeping its placeholders")
    void shouldTranslateNewKeys_forEveryLocale(String locale) throws Exception {
        Properties english = bundle("en");
        Properties translated = bundle(locale);

        for (String key : NEW_KEYS) {
            String englishText = english.getProperty(key);
            String text = translated.getProperty(key);
            assertThat(englishText).as("en %s", key).isNotBlank();
            assertThat(text).as("%s %s", locale, key).isNotBlank();
            // "Document" is the same word in French; everything else is translated.
            if (!(locale.equals("fr") && key.equals(PREFIX + "attachmentType.document"))) {
                assertThat(text).as("%s %s is translated", locale, key).isNotEqualTo(englishText);
            }
            for (String placeholder : List.of("{0}", "{1}")) {
                assertThat(text.contains(placeholder)).as("%s %s keeps %s", locale, key, placeholder)
                        .isEqualTo(englishText.contains(placeholder));
            }
        }
        for (String key : FORMATTED_KEYS) {
            assertThat(translated.getProperty(key)).as("%s %s has no lone apostrophe", locale, key).doesNotContain("'");
            assertThat(english.getProperty(key)).as("en %s has no lone apostrophe", key).doesNotContain("'");
        }
    }

    private static Properties bundle(String locale) throws Exception {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(
                resolveProjectPath(Path.of("src/main/resources/oscarResources_" + locale + ".properties")),
                StandardCharsets.ISO_8859_1)) {
            properties.load(reader);
        }
        return properties;
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
