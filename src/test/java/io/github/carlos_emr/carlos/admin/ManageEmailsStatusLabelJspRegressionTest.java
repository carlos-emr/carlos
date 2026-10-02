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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import io.github.carlos_emr.carlos.commn.model.converter.EmailLogStatusConverter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the Manage Emails label for {@link EmailStatus#SUCCESS} (issue #3834).
 *
 * <p>{@code SUCCESS} is written when the SMTP relay or email API accepts the message, which is
 * not delivery: a later bounce is never recorded. Manage Emails must therefore show it as
 * "Accepted by mail server" in both the status tag and the status filter, while the enum
 * constant, the stored value, the filter option value and the {@code status-tag-success} class
 * stay {@code SUCCESS}.</p>
 *
 * @since 2026-09-29
 */
@DisplayName("Manage emails SUCCESS status label")
@Tag("unit")
@Tag("email")
class ManageEmailsStatusLabelJspRegressionTest {

    private static final Path EMAIL_STATUS_RESULTS_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/admin/emailStatusResults.jspf");
    private static final Path MANAGE_EMAILS_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/admin/manageEmails.jsp");
    private static final String ACCEPTED_LABEL_KEY = "admin.manageEmails.acceptedByMailServer";
    private static final List<String> LOCALES = List.of("en", "fr", "es", "pt_BR", "pl");

    @Test
    @DisplayName("status tag should show the accepted-by-mail-server label for SUCCESS rows")
    void shouldLabelSuccessAsAcceptedByMailServer_inStatusResultsTag() throws IOException {
        String jsp = Files.readString(EMAIL_STATUS_RESULTS_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("<c:set var=\"emailStatusLabel\" value=\"${emailStatusResult.status}\"/>")
                .contains("<c:if test=\"${emailStatusResult.status eq 'SUCCESS'}\">")
                .contains("<fmt:message key=\"" + ACCEPTED_LABEL_KEY + "\" var=\"emailStatusLabel\"/>")
                .contains("${carlos:forHtml(emailStatusLabel)}")
                .contains("status-tag-${fn:toLowerCase(emailStatusResult.status)}")
                .doesNotContain("${carlos:forHtml(emailStatusResult.status)}");
    }

    @Test
    @DisplayName("status filter should show the accepted-by-mail-server label but submit SUCCESS")
    void shouldLabelSuccessAsAcceptedByMailServer_inStatusFilterOptions() throws IOException {
        String jsp = Files.readString(MANAGE_EMAILS_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("<option value=\"${ status }\">")
                .contains("<c:set var=\"statusLabel\" value=\"${status}\"/>")
                .contains("<c:if test=\"${status eq 'SUCCESS'}\">")
                .contains("<fmt:message key=\"" + ACCEPTED_LABEL_KEY + "\" var=\"statusLabel\"/>")
                .contains("${carlos:forHtml(statusLabel)}")
                .contains(".status-tag-success")
                .doesNotContain("${carlos:forHtml(status)}");
    }

    @Test
    @DisplayName("accepted-by-mail-server label should resolve in every shipped locale")
    void shouldDefineAcceptedByMailServerLabel_inEveryLocale() throws IOException {
        for (String locale : LOCALES) {
            assertThat(loadBundle(locale).getProperty(ACCEPTED_LABEL_KEY))
                    .as("oscarResources_%s.properties should define %s", locale, ACCEPTED_LABEL_KEY)
                    .isNotBlank();
        }
        assertThat(loadBundle("en").getProperty(ACCEPTED_LABEL_KEY))
                .isEqualTo("Accepted by mail server");
    }

    @Test
    @DisplayName("SUCCESS should still be the enum constant and stored value the JSPs compare against")
    void shouldKeepStoredSuccessValue_forDisplayOnlyRelabel() {
        EmailLogStatusConverter converter = new EmailLogStatusConverter();

        assertThat(EmailStatus.SUCCESS.name()).isEqualTo("SUCCESS");
        assertThat(converter.convertToDatabaseColumn(EmailStatus.SUCCESS)).isEqualTo("SUCCESS");
        assertThat(converter.convertToEntityAttribute("SUCCESS")).isEqualTo(EmailStatus.SUCCESS);
    }

    private static Properties loadBundle(String locale) throws IOException {
        Properties bundle = new Properties();
        Path bundlePath = Path.of("src/main/resources/oscarResources_" + locale + ".properties");
        try (var reader = Files.newBufferedReader(bundlePath, StandardCharsets.UTF_8)) {
            bundle.load(reader);
        }
        return bundle;
    }
}
