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
import java.util.regex.Pattern;

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

        // One block: the default label, then the override ONLY inside the SUCCESS test. Separate
        // contains() checks would still pass if the message moved outside the c:if and every row
        // read "Accepted".
        assertThat(jsp).containsPattern(Pattern.compile(
                "<c:set var=\"emailStatusLabel\" value=\"\\$\\{emailStatusResult\\.status}\"/>\\s*"
                        + "<c:if test=\"\\$\\{emailStatusResult\\.status eq 'SUCCESS'}\">\\s*"
                        + "<fmt:message key=\"" + Pattern.quote(ACCEPTED_LABEL_KEY) + "\" var=\"emailStatusLabel\"/>\\s*"
                        + "</c:if>\\s*<!-- Email Card -->"));
        assertThat(jsp)
                .contains("${carlos:forHtml(emailStatusLabel)}")
                .contains("status-tag-${fn:toLowerCase(emailStatusResult.status)}")
                .doesNotContain("${carlos:forHtml(emailStatusResult.status)}");
    }

    @Test
    @DisplayName("status filter should show the accepted-by-mail-server label but submit SUCCESS")
    void shouldLabelSuccessAsAcceptedByMailServer_inStatusFilterOptions() throws IOException {
        String jsp = Files.readString(MANAGE_EMAILS_JSP, StandardCharsets.UTF_8);

        assertThat(jsp).containsPattern(Pattern.compile(
                "<c:set var=\"statusLabel\" value=\"\\$\\{status}\"/>\\s*"
                        + "<c:if test=\"\\$\\{status eq 'SUCCESS'}\">\\s*"
                        + "<fmt:message key=\"" + Pattern.quote(ACCEPTED_LABEL_KEY) + "\" var=\"statusLabel\"/>\\s*"
                        + "</c:if>\\s*"
                        + "<option value=\"\\$\\{ status }\">\\s*"
                        + "\\$\\{carlos:forHtml\\(statusLabel\\)}\\s*</option>"));
        assertThat(jsp)
                .contains("${carlos:forHtml(statusLabel)}")
                .contains(".status-tag-success")
                .doesNotContain("${carlos:forHtml(status)}");
    }

    @Test
    @DisplayName("accepted-by-mail-server label should resolve in every shipped locale")
    void shouldDefineAcceptedByMailServerLabel_inEveryLocale() throws IOException {
        String english = loadBundle("en").getProperty(ACCEPTED_LABEL_KEY);
        assertThat(english).isEqualTo("Accepted by mail server");
        for (String locale : LOCALES) {
            String label = loadBundle(locale).getProperty(ACCEPTED_LABEL_KEY);
            assertThat(label)
                    .as("oscarResources_%s.properties should define %s", locale, ACCEPTED_LABEL_KEY)
                    .isNotBlank();
            if (!"en".equals(locale)) {
                // Translated, not an English placeholder (the bundles' translation rule).
                assertThat(label).as("oscarResources_%s.properties translates %s", locale, ACCEPTED_LABEL_KEY)
                        .isNotEqualTo(english);
            }
        }
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
