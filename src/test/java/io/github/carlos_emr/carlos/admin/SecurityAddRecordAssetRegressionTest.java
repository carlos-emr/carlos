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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards admin login-record JSP behavior that is otherwise rendered directly
 * by the container.
 *
 * @since 2026-05-30
 */
@DisplayName("Security add record asset regressions")
@Tag("unit")
@Tag("admin")
class SecurityAddRecordAssetRegressionTest {

    private static final Path SECURITY_ADD_RECORD_JSP =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "admin", "securityaddarecord.jsp");

    @Test
    @DisplayName("should offer same-site providers without login records only when site access privacy and multisites are enabled")
    void shouldOfferSameSiteProviders_whenSiteAccessPrivacyAndMultisitesEnabled() throws IOException {
        String jsp = readSecurityAddRecordJsp();

        assertThat(jsp)
                .contains("ProviderSiteDao providerSiteDao = SpringUtils.getBean(ProviderSiteDao.class);")
                .contains("List<Security> s = securityDao.findByProviderNo(p.getProviderNo());")
                .contains("if (s.isEmpty()) {")
                // The multisites gate is load-bearing: providersite rows only exist in multisite
                // deployments, so site-scoped filtering on a standalone install yields an empty
                // provider dropdown (2026.08 alpha regression).
                .containsPattern("if \\(isSiteAccessPrivacy && IsPropertiesOn\\.isMultisitesEnable\\(\\)\\) \\{\\s+"
                        + "for \\(Provider p : providerSiteDao\\.findActiveProvidersBySharedSites\\(curProvider_no\\)\\)")
                .doesNotContain("if (s.size() > 0) {")
                .doesNotContain("providerSiteDao.findActiveProvidersWithSites(curProvider_no)")
                .doesNotContainPattern("if \\(isSiteAccessPrivacy\\) \\{\\s+"
                        + "for \\(Provider p : providerSiteDao\\.findActiveProvidersBySharedSites");
    }

    @Test
    @DisplayName("should keep all active providers without login records when site filtering does not apply")
    void shouldKeepAllActiveProviders_whenSiteFilteringDoesNotApply() throws IOException {
        String jsp = readSecurityAddRecordJsp();

        assertThat(jsp)
                .containsPattern("\\} else \\{\\s+for \\(Provider p : providerDao\\.getActiveProviders\\(\\)\\) \\{\\s+"
                        + "List<Security> s = securityDao\\.findByProviderNo\\(p\\.getProviderNo\\(\\)\\);\\s+"
                        + "if \\(s\\.isEmpty\\(\\)\\) \\{");
    }

    private static String readSecurityAddRecordJsp() throws IOException {
        return Files.readString(SECURITY_ADD_RECORD_JSP, StandardCharsets.UTF_8);
    }
}
