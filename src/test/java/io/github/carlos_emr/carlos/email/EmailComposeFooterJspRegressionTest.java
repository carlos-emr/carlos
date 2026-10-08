/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the compose screen's Footer card (issue #3981): the posted field and its encoding, the shared
 * Edit footer window it opens, its help line, the encrypted-message notice that says the footer stays
 * visible, and that choosing another sending account leaves the footer alone.
 *
 * @since 2026-09-29
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
@Tag("security")
@DisplayName("Email compose footer rendering")
class EmailComposeFooterJspRegressionTest {

    private static final Path EMAIL_COMPOSE_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/email/emailCompose.jsp");
    private static final List<String> LOCALES = List.of("en", "es", "fr", "pl", "pt_BR");

    @Test
    @DisplayName("should post the footer from a hidden field filled through the CARLOS attribute encoder")
    void shouldRenderFooterField_withEncodedValue() throws IOException {
        String jsp = Files.readString(EMAIL_COMPOSE_JSP, StandardCharsets.UTF_8);

        int field = jsp.indexOf("<input type=\"hidden\" name=\"footerEmail\" id=\"footerEmail\"");
        assertThat(field).isGreaterThanOrEqualTo(0);
        String element = jsp.substring(field, jsp.indexOf('\n', field)).strip();
        // The footer is HTML; in an attribute it must be attribute-encoded, never placed raw.
        assertThat(element).endsWith("value=\"<carlos:encode value='${footerEmail}' context='htmlAttribute'/>\"/>");
        assertThat(jsp)
                .doesNotContain(">${footerEmail}<")
                .doesNotContain("value=\"${footerEmail}\"")
                .doesNotContain("e:forHtmlContent value=\"${footerEmail}\"");
        // Inside the send form, after the message box.
        assertThat(field)
                .isGreaterThan(jsp.indexOf("name=\"message\" id=\"message\""))
                .isLessThan(jsp.indexOf("</form>"));
    }

    @Test
    @DisplayName("should edit the footer in the shared window, included once outside the send form")
    void shouldOpenSharedEditor_forThisEmailOnly() throws IOException {
        String jsp = Files.readString(EMAIL_COMPOSE_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("data-bs-target=\"#footerEditorModal\"")
                .contains("data-footer-editor-target=\"footerEmail\" data-footer-editor-preview=\"footerEmailPreview\"")
                .contains("<div id=\"footerEmailPreview\"")
                .contains("<c:set var=\"footerEditorScopeKey\" value=\"email.footerEditor.scopeThisEmail\"/>")
                .contains("<c:set var=\"footerEditorApplyKey\" value=\"email.footerEditor.applyThisEmail\"/>");
        String include = "<%@ include file=\"/WEB-INF/jsp/email/footerEditorModal.jspf\" %>";
        assertThat(jsp.indexOf(include)).isEqualTo(jsp.lastIndexOf(include))
                .isGreaterThan(jsp.indexOf("</form>"))
                .isGreaterThan(jsp.indexOf("bootstrap.bundle.min.js"));
    }

    @Test
    @DisplayName("should offer to make the footer the user's usual one, report the outcome, and stay open if it failed")
    void shouldOfferSaveAsMine_insideSendForm() throws IOException {
        String jsp = Files.readString(EMAIL_COMPOSE_JSP, StandardCharsets.UTF_8);

        int box = jsp.indexOf("name=\"saveFooterAsMine\" id=\"saveFooterAsMine\" value=\"true\"");
        assertThat(box).isGreaterThan(jsp.indexOf("id=\"footerEmail\"")).isLessThan(jsp.indexOf("</form>"));
        assertThat(jsp)
                .contains("<fmt:message key=\"email.compose.footer.saveAsMine\"/>")
                .contains("<c:if test=\"${footerSavedAsMine}\">")
                .contains("<c:if test=\"${footerSaveAsMineFailed}\">")
                .contains("<c:if test=\"${footerSavedAsClinic}\">")
                .contains("<c:if test=\"${footerSaveAsMineStale}\">")
                .contains("<c:if test=\"${footerSaveAsMineNotDone}\">")
                // A retry form keeps the box ticked.
                .contains("${param.saveFooterAsMine eq 'true' ? 'checked' : ''}")
                // The clinic footer the window showed goes back with the send, attribute-encoded.
                .contains("name=\"footerClinicShown\"")
                .contains("value=\"${carlos:forHtmlAttribute(empty footerClinicFingerprint ? param.footerClinicShown : footerClinicFingerprint)}\"")
                // On an unconfirmed delivery the form is hidden: the note shows in the result too.
                .contains("id=\"footerSaveAsMineNotDoneUnconfirmed\"")
                // A failed or refused save keeps the window open so the user sees it.
                .contains("&& !document.getElementById('footerSaveAsMineFailed')")
                .contains("&& !document.getElementById('footerSaveAsMineStale')");
        for (String locale : LOCALES) {
            for (String key : List.of("email.compose.footer.saveAsMine", "email.compose.footer.savedAsMine",
                    "email.compose.footer.saveAsMineFailed", "email.compose.footer.savedAsClinic",
                    "email.compose.footer.saveAsMineStale", "email.compose.footer.saveAsMineNotDone")) {
                assertThat(bundle(locale).getProperty(key)).as(locale + " " + key).isNotBlank();
                if (!"en".equals(locale)) {
                    assertThat(bundle(locale).getProperty(key)).as(locale + " " + key)
                            .isNotEqualTo(bundle("en").getProperty(key));
                }
            }
        }
    }

    @Test
    @DisplayName("should show the help line and the encrypted-message notice from the bundle in every locale")
    void shouldShowFooterHelpLine_fromBundleKey() throws IOException {
        String jsp = Files.readString(EMAIL_COMPOSE_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("<fmt:message key=\"email.compose.footer.help\" var=\"emailComposeFooterHelp\"/>")
                .contains("<fmt:message key=\"email.compose.footer.heading\" var=\"emailComposeFooterLabel\"/>")
                .contains("id=\"footerEmailHelp\"")
                .contains("${emailComposeFooterHelp}")
                .contains("aria-describedby=\"footerEmailHelp\"");
        String english = bundle("en").getProperty("email.compose.footer.help");
        assertThat(english).contains("unencrypted", "not saved to the chart", "Do not include patient information");
        assertThat(bundle("en").getProperty("email.compose.footer.heading")).isEqualTo("Footer");
        String englishNotice = bundle("en").getProperty("email.compose.msg.encryptedMessageNotice");
        assertThat(englishNotice).contains("password-protected PDF", "the footer remain visible");
        for (String locale : LOCALES) {
            if ("en".equals(locale)) {
                continue;
            }
            // Every locale carries its own translation, not the English text.
            assertThat(bundle(locale).getProperty("email.compose.footer.help")).as(locale)
                    .isNotBlank().isNotEqualTo(english);
            assertThat(bundle(locale).getProperty("email.compose.footer.heading")).as(locale)
                    .isNotBlank().isNotEqualTo("Footer");
            assertThat(bundle(locale).getProperty("email.compose.msg.encryptedMessageNotice")).as(locale)
                    .isNotBlank().isNotEqualTo(englishNotice);
        }
    }

    @Test
    @DisplayName("should not swap the footer when staff choose another sending account")
    void shouldKeepFooter_whenSenderChanges() throws IOException {
        String jsp = Files.readString(EMAIL_COMPOSE_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("onchange=\"showAdditionalParamOption()\"")
                .doesNotContain("data-default-footer")
                .doesNotContain("applySenderDefaultFooter")
                .doesNotContain("followsSender");
    }

    private static Properties bundle(String locale) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(
                Path.of("src/main/resources/oscarResources_" + locale + ".properties"))) {
            properties.load(in);
        }
        return properties;
    }
}
