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

/** Verifies the encryption UI is synchronized before send-result branches can return. */
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
        int sendResultBranch = jsp.indexOf("// A successful send is terminal", domReady);

        assertThat(domReady).isGreaterThanOrEqualTo(0);
        assertThat(applyState)
                .isGreaterThan(domReady)
                .isLessThan(sendResultBranch);
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
    @DisplayName("should contextually encode retry values at HTML and JavaScript sinks")
    void shouldEncodeRetryValues_atRenderedSinks() throws IOException {
        String jsp = Files.readString(EMAIL_COMPOSE_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("value=\"${carlos:forHtmlAttribute(demographicId)}\"")
                .contains("value=\"${carlos:forHtmlAttribute(fdid)}\"")
                .contains("value=\"${carlos:forHtmlAttribute(openEFormAfterEmail)}\"")
                .contains("value=\"${carlos:forHtmlAttribute(deleteEFormAfterEmail)}\"")
                .contains("value=\"${carlos:forHtmlAttribute(transactionType)}\"")
                .contains("class=\"alert-link\">${carlos:forHtml(receiverName)}</a>")
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
                .contains("errorElement.previousElementSibling.classList.add(\"is-invalid\")")
                .contains("errorElement.previousElementSibling.classList.remove(\"is-invalid\")")
                .doesNotContain("errorElement.parentNode.firstElementChild.classList");
    }
}
