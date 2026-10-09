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
package io.github.carlos_emr.carlos.sms.admin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The SMS settings page labels each provider credential with the provider's translated label, and says which
 * ones are needed before sending can be switched on.
 *
 * @since 2026-10-08
 */
@DisplayName("SMS settings page credential labels")
@Tag("unit")
@Tag("fast")
class ConfigureSmsCredentialLabelsRegressionTest {

    private static final Path CONFIGURE_SMS_JSP = projectRoot()
            .resolve("src/main/webapp/WEB-INF/jsp/admin/configureSms.jsp");

    @Test
    @DisplayName("should label each credential from the provider's message key, never its raw field name")
    void shouldLabelCredential_fromProviderMessageKey() throws IOException {
        String jsp = Files.readString(CONFIGURE_SMS_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("<fmt:message key=\"${field.labelKey}\"/></label>")
                .contains("<c:if test=\"${field.required}\"><fmt:message key=\"sms.config.credentialRequiredHint\"/></c:if>")
                .doesNotContain("<carlos:encode value=\"${field.name}\"/></label>");
    }

    @Test
    void shouldScopeCredentialGroups_toTheirProviders() throws IOException {
        String jsp = Files.readString(CONFIGURE_SMS_JSP, StandardCharsets.UTF_8);

        assertThat(jsp).contains("${smsConfig.credentialGroups[credentialProvider]}")
                .contains("name=\"credential.<carlos:encode value='${credentialProvider}'")
                .contains("group.disabled = !selected;")
                .contains("group.hidden = !selected;")
                .doesNotContain("name=\"credential.<carlos:encode value='${field.name}'");
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("maven.multiModuleProjectDirectory", System.getProperty("user.dir")));
    }
}
