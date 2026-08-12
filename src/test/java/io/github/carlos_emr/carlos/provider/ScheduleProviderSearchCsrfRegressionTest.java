/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.provider;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the Schedule provider-search result POST against losing its CSRF token.
 *
 * @since 2026-08-12
 */
@DisplayName("Schedule provider search CSRF regression")
@Tag("unit")
@Tag("provider")
@Tag("security")
class ScheduleProviderSearchCsrfRegressionTest {

    private static final Path PROVIDER_SEARCH = projectRoot().resolve(Path.of(
            "src", "main", "webapp", "WEB-INF", "jsp", "provider",
            "receptionistfindprovider.jsp"));
    private static final Path SCHEDULE_BROWSER_CHECK = projectRoot().resolve(Path.of(
            "scripts", "schedule-links-playwright-checks.js"));

    @Test
    @DisplayName("should include server-rendered CSRF token in provider selection POST")
    void shouldIncludeServerRenderedCsrfToken_inProviderSelectionPost() throws IOException {
        String jsp = Files.readString(PROVIDER_SEARCH, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("prefix=\"csrf\"")
                .contains("id=\"providerSelectionCsrfToken\"")
                .contains("name=\"<csrf:tokenname/>\" value=\"<csrf:tokenvalue/>\"")
                .contains("document.getElementById('providerSelectionCsrfToken')")
                .contains("fields[csrfToken.name] = csrfToken.value")
                .contains("onClick=\"return selectProvider(")
                .contains("context=\"javaScriptBlock\"/>")
                .contains("context=\"javaScriptAttribute\"/>");
    }

    @Test
    @DisplayName("should stop provider selection when CSRF token is unavailable")
    void shouldStopProviderSelection_whenCsrfTokenIsUnavailable() throws IOException {
        String jsp = Files.readString(PROVIDER_SEARCH, StandardCharsets.UTF_8);
        int tokenLookup = requiredIndex(jsp,
                "var csrfToken = document.getElementById('providerSelectionCsrfToken');");
        int missingTokenGuard = requiredIndex(jsp, "if (!csrfToken || !csrfToken.value)");
        int formCreation = requiredIndex(jsp, "var form = document.createElement('form');");

        assertThat(missingTokenGuard).isGreaterThan(tokenLookup).isLessThan(formCreation);
        assertThat(jsp.substring(missingTokenGuard, formCreation))
                .contains("return false;");
    }

    @Test
    @DisplayName("should capture provider POST before search can auto-select its only result")
    void shouldCaptureProviderPost_beforeSearchCanAutoSelectOnlyResult() throws IOException {
        String browserCheck = Files.readString(SCHEDULE_BROWSER_CHECK, StandardCharsets.UTF_8);
        int requestListener = requiredIndex(browserCheck,
                "const requestPromise = context.waitForEvent('request'");
        int searchSubmit = requiredIndex(browserCheck, "await searchInput.press('Enter');");

        assertThat(requestListener).isLessThan(searchSubmit);
        assertThat(browserCheck)
                .contains("context.waitForEvent('page', { timeout: 10000 }).catch(() => null)")
                .contains("if (!resultPage)")
                .contains("type: 'missing-search-popup'")
                .contains("const providerLinkCount = await resultPage.locator(")
                .contains("if (providerLinkCount > 1)")
                .contains("const providerRequest = await requestPromise;");
    }

    private static int requiredIndex(String source, String token) {
        int index = source.indexOf(token);
        assertThat(index).as("required JSP contract: %s", token).isGreaterThanOrEqualTo(0);
        return index;
    }

    private static Path projectRoot() {
        try {
            Path location = Path.of(ScheduleProviderSearchCsrfRegressionTest.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
            Path current = Files.isRegularFile(location) ? location.getParent() : location;
            while (current != null) {
                if (Files.isRegularFile(current.resolve(
                        "src/main/webapp/WEB-INF/jsp/provider/receptionistfindprovider.jsp"))) {
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
