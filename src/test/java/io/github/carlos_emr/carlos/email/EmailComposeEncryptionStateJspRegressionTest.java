/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the email compose page's rendering: the encryption UI is synchronized before
 * send-result branches can return, and the post-send auto-close says how long it really waits.
 *
 * @since 2026-08-25
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
@Tag("security")
@DisplayName("Email compose encryption-state rendering")
class EmailComposeEncryptionStateJspRegressionTest {

    private static final Path EMAIL_COMPOSE_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/email/emailCompose.jsp");

    @Test
    @DisplayName("should apply encryption state before failed-send initialization returns")
    void shouldApplyEncryptionState_beforeSendResultBranchReturns() throws IOException {
        String jsp = Files.readString(EMAIL_COMPOSE_JSP, StandardCharsets.UTF_8);

        int domReady = jsp.indexOf("document.addEventListener(\"DOMContentLoaded\"");
        int applyState = jsp.indexOf("applyEncryptionState();", domReady);
        int errorBranch = jsp.indexOf(
                "if (document.getElementById('isEmailError').value === 'true')", domReady);

        assertThat(domReady).isGreaterThanOrEqualTo(0);
        assertThat(errorBranch).isGreaterThanOrEqualTo(0);
        assertThat(applyState)
                .isGreaterThan(domReady)
                .isLessThan(errorBranch);
    }

    @Test
    @DisplayName("should localize consent labels without using them as state")
    void shouldLocalizeConsentLabel_withoutUsingLabelAsState() throws IOException {
        String jsp = Files.readString(EMAIL_COMPOSE_JSP, StandardCharsets.UTF_8);

        assertThat(jsp).contains("<fmt:message key=\"${emailConsentMessageKey}\"");
        assertThat(jsp).contains("emailConsentStatus eq 'UNKNOWN'");
        assertThat(jsp).doesNotContain("emailConsentStatus eq 'Unknown'");
    }

    @Test
    @DisplayName("should collapse the composer after acceptance, an unconfirmed outcome or an unresolved portal delivery")
    void shouldCollapseComposer_whenAcceptedUnconfirmedOrAwaitingPortalRecovery() throws IOException {
        String jsp = Files.readString(EMAIL_COMPOSE_JSP, StandardCharsets.UTF_8);

        int slideUp = jsp.indexOf("$(\"#page-body\").slideUp");
        int successOnlyGuard = jsp.lastIndexOf(
                "<c:if test=\"${ isEmailSuccessful eq true or isEmailDeliveryUnconfirmed eq true or portalDeliveryNeedsRecovery }\">", slideUp);

        assertThat(slideUp).isGreaterThanOrEqualTo(0);
        assertThat(successOnlyGuard).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("should hide and blank the manual password controls when portal delivery is enabled")
    void shouldHideAndBlankPasswordControls_whenPortalDeliveryIsEnabled() throws IOException {
        String jsp = Files.readString(EMAIL_COMPOSE_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("value=\"${carlos:forHtmlAttribute(portalEmailEnabled ? '' : emailPDFPassword)}\"")
                .contains("${carlos:forHtmlContent(portalEmailEnabled ? '' : emailPDFPasswordClue)}");
        int passwordRow = jsp.indexOf("for=\"emailPDFPassword\"");
        int clueRow = jsp.indexOf("for=\"emailPDFPasswordClue\"");
        String hiddenRow = "<div class=\"row mt-3 mb-3 align-items-center ${portalEmailEnabled ? 'd-none' : ''}\">";
        assertThat(jsp.lastIndexOf(hiddenRow, passwordRow)).isGreaterThanOrEqualTo(0);
        assertThat(jsp.lastIndexOf(hiddenRow, clueRow)).isGreaterThan(jsp.lastIndexOf(hiddenRow, passwordRow));
    }

    @Test
    @DisplayName("should submit attachment encryption only while message encryption is on")
    void shouldSubmitAttachmentEncryptionOnly_whenMessageEncryptionOn() throws IOException {
        String jsp = Files.readString(EMAIL_COMPOSE_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("checkbox.checked && attachmentCheckbox.checked ? \"true\" : \"false\"")
                .contains("document.getElementById(\"encryptionSwitch\").checked && checkbox.checked");
    }

    @Test
    @DisplayName("should allow Enter to activate confirmation modal buttons")
    void shouldAllowEnter_whenConfirmationButtonFocused() throws IOException {
        String jsp = Files.readString(EMAIL_COMPOSE_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("targetTag !== \"textarea\" && targetTag !== \"button\"");
    }

    @Test
    @DisplayName("should contextually encode retry values at HTML and JavaScript sinks")
    void shouldEncodeRetryValues_atRenderedSinks() throws IOException {
        String jsp = Files.readString(EMAIL_COMPOSE_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("value=\"${carlos:forHtmlAttribute(demographicId)}\"")
                .contains("value=\"${carlos:forHtmlAttribute(fdid)}\"")
                .contains("value=\"${carlos:forHtmlAttribute(openEFormAfterEmail)}\"")
                .contains("value=\"${carlos:forHtmlAttribute(deleteEFormAfterEmail)}\"")
                .contains("value=\"${carlos:forHtmlAttribute(transactionType)}\"")
                .contains("name=\"emailConsentName\" value=\"${carlos:forHtmlAttribute(emailConsentName)}\"")
                .contains("${consentOverride ? 'checked' : ''}")
                .contains("<carlos:encode value=\"${consentOverrideReason}\"/>")
                .contains("class=\"alert-link\">${carlos:forHtmlContent(receiverName)}</a>")
                .contains("placeholder=\"${carlos:forHtmlAttribute(emailComposeMessagePlaceholder)}\"")
                .contains("title=\"${carlos:forHtmlAttribute(emailComposeEncryptionTooltip)}\"")
                .contains("aria-label=\"${carlos:forHtmlAttribute(emailComposeClose)}\"")
                .contains("fdid=${carlos:forJavaScript(carlos:forUriComponent(fdid))}")
                .contains("demographic_no=${carlos:forJavaScript(carlos:forUriComponent(demographicId))}")
                .contains("\"${carlos:forJavaScript(isEmailAutoSend)}\" === \"true\"")
                .doesNotContain("value=\"${demographicId}\"")
                .doesNotContain("value=\"${fdid}\"")
                .doesNotContain("value=\"${openEFormAfterEmail}\"")
                .doesNotContain("value=\"${deleteEFormAfterEmail}\"")
                .doesNotContain("value=\"${transactionType}\"")
                .doesNotContain("class=\"alert-link\">${ receiverName }</a>")
                .doesNotContain(">${receiverName}</a>")
                .doesNotContain("fdid=${fdid}")
                .doesNotContain("demographic_no=${demographicId}");
    }

    @Test
    @DisplayName("should apply validation styling to the form control before each error element")
    void shouldApplyValidationStylingToFormControl_whenFieldIsInvalid() throws IOException {
        String jsp = Files.readString(EMAIL_COMPOSE_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("const invalidField = field || errorElement.previousElementSibling")
                .contains("invalidField.classList.add(\"is-invalid\")")
                .contains("invalidField.classList.remove(\"is-invalid\")")
                .doesNotContain("errorElement.parentNode.firstElementChild.classList");
    }

    @Test
    @DisplayName("should show a refused-address hint only in the definite-failure branch")
    void shouldRenderRefusalHints_onlyInFailureBranch() throws IOException {
        String jsp = Files.readString(EMAIL_COMPOSE_JSP, StandardCharsets.UTF_8);

        int unconfirmedBranch = jsp.indexOf("id=\"deliveryUnconfirmedWarning\"");
        int failureBranch = jsp.indexOf("<c:otherwise>", unconfirmedBranch);
        int failureBranchEnd = jsp.indexOf("</c:otherwise>", failureBranch);
        assertThat(unconfirmedBranch).isPositive();
        assertThat(failureBranch).isGreaterThan(unconfirmedBranch);
        for (String hint : new String[] {"id=\"recipientRefusedHint\"", "id=\"senderRefusedHint\""}) {
            int at = jsp.indexOf(hint);
            assertThat(at).as(hint).isGreaterThan(failureBranch).isLessThan(failureBranchEnd);
            assertThat(jsp.indexOf(hint, at + 1)).as(hint + " rendered once").isEqualTo(-1);
        }
        assertThat(jsp).contains("emailRefusal eq 'RECIPIENT'").contains("emailRefusal eq 'SENDER'");
    }

    @Test
    @DisplayName("should close the window after the number of seconds its message announces, in every locale")
    void shouldAnnounceRealCloseDelay_inEveryLocale() throws IOException {
        String jsp = Files.readString(EMAIL_COMPOSE_JSP, StandardCharsets.UTF_8);

        // One number drives both the timer and the message.
        Matcher constant = Pattern.compile("<c:set var=\"windowCloseSeconds\" value=\"\\$\\{(\\d+)}\"/>").matcher(jsp);
        assertThat(constant.find()).as("windowCloseSeconds constant").isTrue();
        long delay = Long.parseLong(constant.group(1));
        // Set before both uses: set after them, the message would say "null" and the timer would be 0.
        assertThat(jsp)
                .containsSubsequence("<c:set var=\"windowCloseSeconds\"",
                        "<fmt:message key=\"email.compose.msg.windowClosing\" var=\"emailComposeWindowClosing\">",
                        "<fmt:param value=\"${windowCloseSeconds}\"/>",
                        "setTimeout(() => window.close(), ${windowCloseSeconds * 1000});")
                .doesNotContainPattern("window\\.close\\(\\), \\d");

        // Every locale shows that number, rendered the way JSTL's MessageFormat renders it.
        for (String locale : List.of("en", "es", "fr", "pl", "pt_BR")) {
            String pattern = loadBundle(locale).getProperty("email.compose.msg.windowClosing");
            String rendered = new MessageFormat(pattern, Locale.forLanguageTag(locale.replace('_', '-')))
                    .format(new Object[]{delay});
            assertThat(rendered).as(locale).contains("<b>" + delay + "</b> ").doesNotContain("{");
        }
        // Polish changes its word for "seconds" with the number.
        String polish = loadBundle("pl").getProperty("email.compose.msg.windowClosing");
        Map<Long, String> polishForms = Map.of(0L, "sekund", 1L, "sekund\u0119", 3L, "sekundy", 8L, "sekund", 12L, "sekund");
        polishForms.forEach((seconds, word) ->
                assertThat(new MessageFormat(polish, Locale.forLanguageTag("pl")).format(new Object[]{seconds}))
                        .as("pl, %d", seconds)
                        .endsWith("<b>" + seconds + "</b> " + word + "..."));
    }

    private static Properties loadBundle(String locale) throws IOException {
        Properties bundle = new Properties();
        try (Reader reader = Files.newBufferedReader(
                Path.of("src/main/resources/oscarResources_" + locale + ".properties"), StandardCharsets.ISO_8859_1)) {
            bundle.load(reader);
        }
        return bundle;
    }
}
