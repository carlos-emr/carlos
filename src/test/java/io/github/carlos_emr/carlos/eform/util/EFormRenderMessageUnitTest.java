/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.eform.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import io.github.carlos_emr.carlos.utility.LocaleUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #6162: the messages staff see when an eForm cannot be fully rendered come from the resource bundles, in
 * the page's language, and the yes/no values in the issue lists are words rather than raw true/false.
 */
@DisplayName("eForm render-failure messages")
@Tag("unit")
@Tag("eform")
class EFormRenderMessageUnitTest {

    private static final Path RESOURCES = Path.of("src", "main", "resources");
    private static final Path JSP = Path.of("src", "main", "webapp", "WEB-INF", "jsp");
    private static final Path JAVA = Path.of("src", "main", "java", "io", "github", "carlos_emr", "carlos");
    /** Keys only the JSPs use; the rest are the enum's. */
    private static final List<String> JSP_KEYS = List.of("eform.renderIssue.yes", "eform.renderIssue.no",
            "eform.preview.confirmApprove", "eform.preview.advisoryScriptErrors", "document.preview.msgFailed",
            "document.preview.btnCancel", "document.preview.frameTitle");
    /** Yes/no flags of the completeness report, shown on all three surfaces. */
    private static final List<String> FLAGS = List.of("signatureMissing", "providerStampMissing",
            "timerCompatibilityFailure", "stabilizationCapped", "labDecisionSupportStubbed");

    private String originalBaseName;

    @BeforeEach
    void useTheApplicationBundle() {
        originalBaseName = LocaleUtils.BASE_NAME;
        LocaleUtils.BASE_NAME = "oscarResources";
    }

    @AfterEach
    void restoreTheBundleName() {
        LocaleUtils.BASE_NAME = originalBaseName;
    }

    private static Properties bundle(String locale) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(RESOURCES.resolve("oscarResources_" + locale + ".properties"))) {
            properties.load(in);
        }
        return properties;
    }

    private static List<String> allKeys() {
        List<String> keys = new ArrayList<>();
        for (EFormRenderMessage message : EFormRenderMessage.values()) {
            keys.add(message.key());
        }
        keys.addAll(JSP_KEYS);
        return keys;
    }

    @Test
    @DisplayName("should keep the English bundle identical to each message's English text")
    void shouldMatchTheEnglishBundle_forEveryMessage() throws IOException {
        Properties en = bundle("en");
        for (EFormRenderMessage message : EFormRenderMessage.values()) {
            assertThat(en.getProperty(message.key())).as(message.key()).isEqualTo(message.english());
        }
        for (String key : JSP_KEYS) {
            assertThat(en.getProperty(key)).as(key).isNotBlank();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"fr", "es", "pl", "pt_BR"})
    @DisplayName("should have a real translation of every message in every bundle")
    void shouldHaveATranslation_inEveryBundle(String locale) throws IOException {
        Properties en = bundle("en");
        Properties translated = bundle(locale);
        // Spanish "No" is the same word as the English.
        Set<String> sameAsEnglish = "es".equals(locale) ? Set.of("eform.renderIssue.no") : Set.of();
        for (String key : allKeys()) {
            String text = translated.getProperty(key);
            assertThat(text).as("%s in %s", key, locale).isNotBlank();
            if (!sameAsEnglish.contains(key)) {
                assertThat(text).as("%s in %s is still English", key, locale).isNotEqualTo(en.getProperty(key));
            }
            // Typographic apostrophes only: a straight one is MessageFormat's quote character.
            assertThat(text).as("%s in %s", key, locale).doesNotContain("'");
        }
        // The preview banner fills in the count itself.
        assertThat(translated.getProperty("eform.preview.advisoryScriptErrors")).containsOnlyOnce("{0}");
    }

    @ParameterizedTest
    @CsvSource({"fr-CA, fr", "es-ES, es", "pl-PL, pl", "pt-BR, pt_BR"})
    @DisplayName("should give each message in the language the request's pages use")
    void shouldResolveTheRequestLanguage_forEachSupportedLocale(String header, String bundle) throws IOException {
        Properties expected = bundle(bundle);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", header);
        request.setPreferredLocales(List.of(Locale.forLanguageTag(header)));
        for (EFormRenderMessage message : EFormRenderMessage.values()) {
            assertThat(message.text(request)).as("%s for %s", message.key(), header)
                    .isEqualTo(expected.getProperty(message.key()));
        }
    }

    @Test
    @DisplayName("should give English when the browser sends no language preference")
    void shouldGiveEnglish_whenNoLanguageIsRequested() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat(EFormRenderMessage.NOT_ADDED_TO_DOCUMENTS.text(request))
                .isEqualTo(EFormRenderMessage.NOT_ADDED_TO_DOCUMENTS.english());
        assertThat(EFormRenderMessage.EDOC_FAILURE.text(null)).isEqualTo(EFormRenderMessage.EDOC_FAILURE.english());
    }

    @Test
    @DisplayName("should give English when the application has not set its bundle name")
    void shouldGiveEnglish_whenTheBundleNameIsUnset() {
        LocaleUtils.BASE_NAME = null;
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "fr-CA");
        assertThat(EFormRenderMessage.NOT_FAXED.text(request)).isEqualTo(EFormRenderMessage.NOT_FAXED.english());
    }

    @ParameterizedTest
    @ValueSource(strings = {"eform/EFormRenderMissingContent.jsp", "fax/EFormMissingContent.jsp"})
    @DisplayName("should show the issue list's yes/no values as translated words on both missing-content pages")
    void shouldShowYesOrNo_onTheMissingContentPages(String page) throws IOException {
        String jsp = Files.readString(JSP.resolve(page), StandardCharsets.UTF_8);
        for (String flag : FLAGS) {
            assertThat(jsp).as(flag)
                    .contains("<fmt:message key=\"${" + flag + " ? 'eform.renderIssue.yes' : 'eform.renderIssue.no'}\"/>")
                    .doesNotContain("<carlos:encode value=\"${" + flag + "}\"/>");
        }
    }

    @Test
    @DisplayName("should build the document preview's render-failure dialog from bundle text")
    void shouldTranslateThePreviewDialog_inTheAttachmentPage() throws IOException {
        String jsp = Files.readString(JSP.resolve("documentManager/attachDocument.jsp"), StandardCharsets.UTF_8);
        assertThat(jsp)
                .contains("<fmt:message var=\"lblYes\" key=\"eform.renderIssue.yes\"/>")
                .contains("<fmt:message var=\"lblNo\" key=\"eform.renderIssue.no\"/>")
                .contains("<fmt:message var=\"msgPreviewFailed\" key=\"document.preview.msgFailed\"/>")
                .contains("<fmt:message var=\"msgConfirmApprove\" key=\"eform.preview.confirmApprove\"/>")
                .contains("<fmt:message var=\"msgAdvisoryScriptErrors\" key=\"eform.preview.advisoryScriptErrors\"/>")
                .contains("\"${carlos:forJavaScript(msgAdvisoryScriptErrors)}\".replace(\"{0}\", count)")
                .doesNotContain("Approve these issues and render?")
                .doesNotContain("A preview of this document could not be generated.")
                .doesNotContain("This form reported ");
        for (String flag : FLAGS) {
            assertThat(jsp).as(flag).contains("yesNo(data." + flag + ")").doesNotContain("+ data." + flag);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"eform/actions/AddEForm2Action.java", "fax/action/Fax2Action.java",
            "eform/util/EFormSavedRenderResponse.java", "documentManager/actions/DocumentPreview2Action.java"})
    @DisplayName("should keep no English render-failure text in the actions that show it")
    void shouldTakeTheTextFromTheBundle_inEveryCallSite(String source) throws IOException {
        String java = Files.readString(JAVA.resolve(source), StandardCharsets.UTF_8);
        assertThat(java)
                .doesNotContain("could not be fully rendered")
                .doesNotContain("Some content of this eForm could not be rendered")
                .doesNotContain("could not be added to this patient")
                .doesNotContain("could not be downloaded")
                .doesNotContain("PDF preview could not be generated")
                .doesNotContain("Failed to render eForm PDF")
                .doesNotContain("renderer is busy")
                .doesNotContain("approval is invalid or expired")
                .doesNotContain("This eForm and its attachments could not be prepared for faxing")
                .doesNotContain("The document could not be prepared for faxing");
    }

    /** Each action's own render-failure messages, as it takes them from {@link EFormRenderMessage}. */
    static Stream<Arguments> callSiteMessages() {
        return Stream.of(
                Arguments.of("eform/actions/AddEForm2Action.java", List.of(
                        "EFormRenderMessage.NOT_ADDED_TO_DOCUMENTS.text(request)",
                        "EFormRenderMessage.NOT_DOWNLOADED.text(request)",
                        "EFormRenderMessage.EDOC_FAILURE.text(request)",
                        "EFormRenderMessage.DOWNLOAD_FAILURE.text(request)",
                        "EFormRenderMessage.PREVIEW_NOT_GENERATED.text(request)")),
                Arguments.of("fax/action/Fax2Action.java", List.of(
                        "EFormRenderMessage.NOT_FAXED.text(request)",
                        "EFormRenderMessage.FAX_NOT_PREPARED.text(request)",
                        "EFormRenderMessage.FAX_DOCUMENT_NOT_PREPARED.text(request)")),
                Arguments.of("eform/util/EFormSavedRenderResponse.java", List.of(
                        "(edoc ? EFormRenderMessage.NOT_ADDED_TO_DOCUMENTS : EFormRenderMessage.NOT_DOWNLOADED).text(request)",
                        "request.setAttribute(\"missingContentMessage\", message);")),
                Arguments.of("documentManager/actions/DocumentPreview2Action.java", List.of(
                        "EFORM_RENDER_FAILED(\"eform_render_failed\", EFormRenderMessage.PREVIEW_RENDER_FAILED)",
                        "EFORM_RENDER_BUSY(\"eform_render_busy\", EFormRenderMessage.PREVIEW_RENDER_BUSY)",
                        "EFORM_APPROVAL_INVALID(\"eform_approval_invalid\", EFormRenderMessage.PREVIEW_APPROVAL_INVALID)",
                        "EFORM_MISSING_CONTENT(\"eform_missing_content\", EFormRenderMessage.PREVIEW_MISSING_CONTENT)",
                        "return translated == null ? message : translated.text(request);")));
    }

    /**
     * The check above only finds English left behind; this one fails when an action stops using one
     * of its render-failure messages (dropped, or replaced by a blank string). The behaviour on each
     * failure path is covered by the actions' own tests, such as {@code Fax2ActionIncompleteRenderUnitTest}
     * and {@code EFormSavedRenderResponseUnitTest}.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("callSiteMessages")
    @DisplayName("should keep taking each action's render-failure messages from EFormRenderMessage")
    void shouldUseItsRenderMessages_inEveryCallSite(String source, List<String> uses) throws IOException {
        String java = Files.readString(JAVA.resolve(source), StandardCharsets.UTF_8);
        assertThat(java).contains(uses);
    }

    @Test
    @DisplayName("should give the document fax path's preparation failure from the bundle")
    void shouldTranslateTheDocumentFaxFailure_inFax2Action() throws IOException {
        String java = Files.readString(JAVA.resolve("fax/action/Fax2Action.java"), StandardCharsets.UTF_8);
        assertThat(java).contains("EFormRenderMessage.FAX_DOCUMENT_NOT_PREPARED.text(request)");
    }

    @Test
    @DisplayName("should show the document preview's busy notice, cancel button and frame title from bundle text, encoded")
    void shouldTranslateTheBusyNotice_inTheAttachmentPage() throws IOException {
        String jsp = Files.readString(JSP.resolve("documentManager/attachDocument.jsp"), StandardCharsets.UTF_8);
        assertThat(jsp)
                .contains("<fmt:message key=\"eform.preview.msgRenderBusy\" var=\"previewBusyText\"/>")
                .contains("<fmt:message key=\"document.preview.btnCancel\" var=\"previewCancelText\"/>")
                .contains("<fmt:message key=\"document.preview.frameTitle\" var=\"previewFrameTitle\"/>")
                .contains("<span id=\"preview-waiting-text\"><carlos:encode value=\"${previewBusyText}\"/></span>")
                .contains("<carlos:encode value=\"${previewCancelText}\"/></button>")
                .contains("title=\"${carlos:forHtmlAttribute(previewFrameTitle)}\"")
                // The server's busy message replaces the line as text, never as markup.
                .contains("document.getElementById('preview-waiting-text').textContent = message;")
                .doesNotContain("Waiting for your preview")
                .doesNotContain(">Cancel preview<")
                .doesNotContain("title=\"Attachment preview\"");
    }

    @Test
    @DisplayName("should load the attachment page's bundle in the language the server's busy message uses")
    void shouldNegotiateLikeTheServer_inTheAttachmentPage() throws IOException {
        String jsp = Files.readString(JSP.resolve("documentManager/attachDocument.jsp"), StandardCharsets.UTF_8);
        String setLocale = "<fmt:setLocale value=\"<%= io.github.carlos_emr.carlos.utility.LocaleUtils.resolveBundleLocale(request) %>\"/>";
        // The busy line starts as the page's own text and is then replaced by the server's: both must pick one language.
        assertThat(jsp).contains(setLocale);
        assertThat(jsp.indexOf(setLocale)).isLessThan(jsp.indexOf("<fmt:setBundle basename=\"oscarResources\"/>"));
    }
}
