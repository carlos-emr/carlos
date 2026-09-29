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
package io.github.carlos_emr.carlos.admin;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.utility.SafeEncode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for manage emails JSP encoding.
 *
 * @since 2026-07-20
 */
@DisplayName("Manage emails JSP encoding")
@Tag("unit")
@Tag("security")
class ManageEmailsJspEncodingRegressionTest {
    private static final String BASEDIR_PROPERTY = "basedir";
    private static final Path EMAIL_STATUS_RESULTS_JSP_PATH =
            Path.of("src/main/webapp/WEB-INF/jsp/admin/emailStatusResults.jspf");
    private static final Path MANAGE_EMAILS_JSP_PATH =
            Path.of("src/main/webapp/WEB-INF/jsp/admin/manageEmails.jsp");
    private static final Path EMAIL_COMPOSE_JSP_PATH =
            Path.of("src/main/webapp/WEB-INF/jsp/email/emailCompose.jsp");

    @Test
    void shouldEncodeEmailStatusErrorPopoverContent_inHtmlAttributeContext() throws Exception {
        String jsp = Files.readString(resolveProjectPath(EMAIL_STATUS_RESULTS_JSP_PATH));
        String dangerousErrorMessage = "Could not send: \" onmouseover=\"alert(1)";
        String encodedErrorMessage = SafeEncode.forHtmlAttribute(dangerousErrorMessage);
        String renderedAttribute = "data-bs-content=\"" + encodedErrorMessage + "\"";

        assertThat(jsp)
                .contains("<%@ taglib uri=\"carlos\" prefix=\"carlos\" %>")
                .doesNotContain("<%@ taglib uri=\"owasp.encoder.jakarta.advanced\" prefix=\"e\" %>")
                .contains("data-bs-content=\"${carlos:forHtmlAttribute(emailStatusDetail)}\"")
                .doesNotContain("data-bs-content=\"${emailStatusResult.errorMessage}\"");
        assertThat(encodedErrorMessage)
                .contains("onmouseover")
                .doesNotContain("\"");
        assertThat(renderedAttribute)
                .doesNotContain("\" onmouseover=\"");
    }

    @Test
    void shouldKeepPendingEmailRecoveryVisible_whenRenderingManagementViews() throws Exception {
        String resultsJsp = Files.readString(resolveProjectPath(EMAIL_STATUS_RESULTS_JSP_PATH));
        String manageJsp = Files.readString(resolveProjectPath(MANAGE_EMAILS_JSP_PATH));
        String composeJsp = Files.readString(resolveProjectPath(EMAIL_COMPOSE_JSP_PATH));

        assertThat(resultsJsp)
                .contains("emailStatusResult.resolvable")
                .contains("emailStatusResult.status ne 'PENDING' or emailStatusResult.resolvable")
                .contains("admin.manageEmails.pendingDetail")
                .contains("emailStatusResult.status eq 'PENDING' and empty emailStatusDetail")
                .contains("<i class=\"fa-solid fa-lock\"></i> Encrypted")
                .doesNotContain("emailStatusResult.password");
        assertThat(manageJsp)
                .contains(".status-tag-pending")
                .contains(".vertical-status-divider-pending")
                .contains("method=setResolved")
                .contains("bootstrap.Popover.getInstance(statusElement)")
                .contains("statusElement.removeAttribute('data-bs-content')");
        assertThat(composeJsp)
                .contains("email.compose.msg.pendingResendWarning")
                .contains("class=\"alert alert-warning\" id=\"emailResendWarning\"")
                .contains("window.confirm(resendWarning.value)")
                .contains("email.compose.msg.statusTrackingFailed")
                .contains("email.compose.msg.deliveryUnconfirmed")
                .contains("<c:when test=\"${ isEmailSuccessful }\">")
                .contains("<c:when test=\"${ isEmailDeliveryUnconfirmed }\">")
                .contains("document.getElementById('isEmailStatusRecorded').value === 'true'")
                .doesNotContain("alert(resendWarning.value)");
    }

    /**
     * Resolves a project-relative path from the Maven {@code basedir} property or
     * current working directory, walking parent directories for IDE and CLI runs.
     *
     * @param relativePath path relative to the project root
     * @return resolved regular file or directory path
     */
    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of(System.getProperty(BASEDIR_PROPERTY, System.getProperty("user.dir")))
                .toAbsolutePath()
                .normalize();
        for (int checkedParents = 0; current != null && checkedParents < 6; checkedParents++) {
            Path candidate = current.resolve(relativePath).normalize();
            if (Files.isRegularFile(candidate) || Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate " + relativePath + " from "
                + System.getProperty(BASEDIR_PROPERTY, System.getProperty("user.dir")));
    }
}
