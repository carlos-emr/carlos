/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the encryption UI is synchronized before send-result branches can return.
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
    @DisplayName("should collapse the composer only after successful delivery")
    void shouldCollapseComposerOnly_whenDeliverySucceeds() throws IOException {
        String jsp = Files.readString(EMAIL_COMPOSE_JSP, StandardCharsets.UTF_8);

        int slideUp = jsp.indexOf("$(\"#page-body\").slideUp");
        int successOnlyGuard = jsp.lastIndexOf(
                "<c:if test=\"${ isEmailSuccessful eq true }\">", slideUp);

        assertThat(slideUp).isGreaterThanOrEqualTo(0);
        assertThat(successOnlyGuard).isGreaterThanOrEqualTo(0);
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
}
