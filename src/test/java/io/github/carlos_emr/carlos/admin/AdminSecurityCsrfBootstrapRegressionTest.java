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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Guards the admin MFA reset POST on {@code securityupdatesecurity.jsp} against
 * losing its CSRF token.
 *
 * <p>The reset is a {@code fetch()} POST, which CSRFGuard's client script never
 * hijacks, so the token has to be read out of the page. Reading it from the
 * classic {@code updatearecord} form's injected input worked only for as long as
 * that unrelated form stayed on the page; the canonical bootstrap owns the token
 * instead. The bootstrap populates its input from an async fetch, so the handler
 * must wait on {@code window.csrfTokenReady} and fail closed rather than POST an
 * empty token that {@code CarlosCsrfGuardFilter} silently rejects.</p>
 *
 * @since 2026-09-18
 */
@DisplayName("Admin security CSRF bootstrap regression")
@Tag("unit")
@Tag("admin")
@Tag("security")
class AdminSecurityCsrfBootstrapRegressionTest {

    private static final String SECURITY_UPDATE_SECURITY_JSP =
            "src/main/webapp/WEB-INF/jsp/admin/securityupdatesecurity.jsp";
    private static final String CSRF_BOOTSTRAP_INCLUDE =
            "<%@ include file=\"/WEB-INF/jspf/csrf-token.jspf\" %>";

    @Test
    @DisplayName("should include the canonical CSRF bootstrap exactly once inside body")
    void shouldIncludeCanonicalCsrfBootstrapOnce_insideBody() throws IOException {
        String jsp = securityUpdateSecurityJsp();

        assertThat(occurrencesOf(jsp, CSRF_BOOTSTRAP_INCLUDE))
                .as("a duplicate include loads csrfTokenFetch.js twice and registers "
                        + "two DOMContentLoaded handlers")
                .isEqualTo(1);
        assertThat(jsp.indexOf(CSRF_BOOTSTRAP_INCLUDE))
                .as("the bootstrap emits a hidden input, so it only renders inside <body>")
                .isGreaterThan(requiredIndex(jsp, "<body"))
                .isLessThan(requiredIndex(jsp, "</body>"));
    }

    @Test
    @DisplayName("should wait for the bootstrap token before posting the MFA reset")
    void shouldWaitForBootstrapToken_beforePostingMfaReset() throws IOException {
        String jsp = securityUpdateSecurityJsp();
        int resolverAwait = requiredIndex(jsp, "await window.csrfTokenReady;");
        int resolverRetry = requiredIndex(jsp, "await fetchCsrfToken(");
        int handlerTokenLookup = requiredIndex(jsp, "var csrfToken = await resolveCsrfToken();");
        int post = requiredIndex(jsp, "fetch(url, {");

        assertThat(List.of(resolverAwait, resolverRetry))
                .as("the readiness wait and its retry both belong to the resolver, "
                        + "ahead of the handler that consumes the token")
                .allSatisfy(index -> assertThat(index).isLessThan(handlerTokenLookup));
        assertThat(handlerTokenLookup)
                .as("the token must be resolved before the POST is issued")
                .isLessThan(post);
        assertThat(jsp)
                .as("reading the token straight out of the DOM reintroduces the empty-token POST")
                .doesNotContain("var csrfToken = csrfEl ? csrfEl.value : '';");
    }

    @Test
    @DisplayName("should abort the MFA reset visibly when no token can be obtained")
    void shouldAbortMfaResetVisibly_whenNoTokenCanBeObtained() throws IOException {
        String jsp = securityUpdateSecurityJsp();
        int tokenLookup = requiredIndex(jsp, "var csrfToken = await resolveCsrfToken();");
        int post = requiredIndex(jsp, "fetch(url, {");

        assertThat(jsp.substring(tokenLookup, post))
                .as("a rejected reset that only logs to the console reads to the "
                        + "administrator as a successful reset")
                .contains("if (!csrfToken) {")
                .contains("alert(\"<fmt:message "
                        + "key=\"admin.securityAddRecord.mfa.reset.tokenUnavailable\"/>\");")
                .contains("return;");
    }

    @Test
    @DisplayName("should define the token-unavailable message for every bundled locale")
    void shouldDefineTokenUnavailableMessage_forEveryBundledLocale() throws IOException {
        Path resources = projectRoot().resolve(Path.of("src", "main", "resources"));

        try (var bundles = Files.list(resources)) {
            List<Path> localeFiles = bundles
                    .filter(path -> path.getFileName().toString().startsWith("oscarResources_"))
                    .filter(path -> path.getFileName().toString().endsWith(".properties"))
                    .sorted()
                    .toList();

            assertThat(localeFiles).as("bundled oscarResources locales").isNotEmpty();
            for (Path bundle : localeFiles) {
                assertThat(Files.readString(bundle, StandardCharsets.UTF_8))
                        .as("%s must define the MFA reset token-unavailable message",
                                bundle.getFileName())
                        .contains("admin.securityAddRecord.mfa.reset.tokenUnavailable=");
            }
        }
    }

    private static String securityUpdateSecurityJsp() throws IOException {
        return Files.readString(projectRoot().resolve(SECURITY_UPDATE_SECURITY_JSP),
                StandardCharsets.UTF_8);
    }

    private static int occurrencesOf(String source, String token) {
        int count = 0;
        for (int index = source.indexOf(token); index >= 0;
                index = source.indexOf(token, index + token.length())) {
            count++;
        }
        return count;
    }

    private static int requiredIndex(String source, String token) {
        int index = source.indexOf(token);
        assertThat(index).as("required JSP contract: %s", token).isGreaterThanOrEqualTo(0);
        return index;
    }

    /**
     * Walks up from the compiled test classes to the module root. Surefire's working
     * directory is not guaranteed to be the module root, so a bare relative path would
     * make this suite pass or fail depending on how it was launched.
     */
    private static Path projectRoot() {
        try {
            Path location = Path.of(AdminSecurityCsrfBootstrapRegressionTest.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
            Path current = Files.isRegularFile(location) ? location.getParent() : location;
            while (current != null) {
                if (Files.isRegularFile(current.resolve(SECURITY_UPDATE_SECURITY_JSP))) {
                    return current;
                }
                current = current.getParent();
            }
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Unable to resolve test class location", e);
        }
        throw new IllegalStateException("Unable to locate CARLOS EMR project root from test classpath");
    }
}
