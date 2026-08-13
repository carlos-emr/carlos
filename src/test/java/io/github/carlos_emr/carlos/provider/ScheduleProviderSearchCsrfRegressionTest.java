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
package io.github.carlos_emr.carlos.provider;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
                .as("failing closed must be visible to the user, not console-only")
                .contains("alert(\"${carlos:forJavaScriptBlock(missingSecurityTokenMessage)}\");")
                .contains("return false;");
        assertThat(jsp)
                .contains("<fmt:message key=\"receptionist.receptionistfindprovider"
                        + ".msgMissingSecurityToken\" var=\"missingSecurityTokenMessage\"/>");
    }

    @Test
    @DisplayName("should route every provider selection call site through the guarded contract")
    void shouldRouteEveryCallSite_throughGuardedContract() throws IOException {
        String jsp = Files.readString(PROVIDER_SEARCH, StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("selectProvider\\((.*?)\\)").matcher(jsp);

        List<String> callSites = new ArrayList<>();
        while (matcher.find()) {
            callSites.add(matcher.group(1));
        }

        // One declaration site (`p`) plus the result-row, group-row and auto-select callers.
        assertThat(callSites).as("provider selection call sites").hasSizeGreaterThanOrEqualTo(4);
        assertThat(callSites)
                .as("the guarded signature takes only the provider/group id; a stale second "
                        + "argument means a call site was missed when the contract changed")
                .allSatisfy(arguments -> assertThat(arguments).doesNotContain(","));
        assertThat(callSites)
                .as("scriptlet output must reach the JavaScript string literal through an encoder")
                .allSatisfy(arguments -> {
                    if (arguments.contains("<%")) {
                        assertThat(arguments).contains("<carlos:encode");
                    }
                });

        assertThat(jsp)
                .as("onClick handlers must propagate the fail-closed return value")
                .doesNotContain("onClick=\"selectProvider(");
    }

    @Test
    @DisplayName("should encode every provider identity rendered by the result table")
    void shouldEncodeEveryProviderIdentity_inResultTable() throws IOException {
        String jsp = Files.readString(PROVIDER_SEARCH, StandardCharsets.UTF_8);

        assertThat(jsp)
                .as("provider number, last name and first name are operator-authored free text")
                .doesNotContain("<%=sp%>")
                .doesNotContain("<%=spnl%>")
                .doesNotContain("<%=spnf%>")
                .doesNotContain("<%=spnl+\", \"+spnf%>")
                .contains("<carlos:encode value='<%= spnl %>' context=\"html\"/>")
                .contains("<carlos:encode value='<%= spnf %>' context=\"html\"/>");
    }

    @Test
    @DisplayName("should populate group-search rows without dereferencing the absent provider")
    void shouldPopulateGroupSearchRows_withoutDereferencingAbsentProvider() throws IOException {
        String jsp = Files.readString(PROVIDER_SEARCH, StandardCharsets.UTF_8);
        int groupBranch = requiredIndex(jsp, "g = (MyGroup) o;");
        int providerBranch = requiredIndex(jsp, "p = (Provider) o;");

        assertThat(groupBranch).isLessThan(providerBranch);
        assertThat(jsp.substring(groupBranch, providerBranch))
                .as("`p` is still null in the group branch, so any dereference throws")
                .doesNotContain("p.getLastName()")
                .doesNotContain("p.getFirstName()");
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
                .contains("const testProviderLastName = process.env.TEST_PROVIDER_LAST_NAME || '';")
                .contains("await searchInput.fill(testProviderLastName);")
                .doesNotContain("await searchInput.fill('test');")
                .contains("context.waitForEvent('page', { timeout: 10000 }).catch(() => null)")
                .contains("if (!resultPage)")
                .contains("type: 'missing-search-popup'")
                .contains("const resultLoaded = await resultPage.waitForLoadState(")
                .contains("if (!resultLoaded)")
                .contains("type: 'search-popup-load-failure'")
                .contains("let providerRequest = await Promise.race(")
                .contains("providerRequest = providerRequest || await requestPromise;")
                .contains("responseWithTimeout(providerRequest, 30000)")
                .as("the token name is configurable, so it must be read from the rendered "
                        + "page; the auto-select path navigates away, so capture it from "
                        + "the document response rather than the live DOM")
                .contains("resultPage.on('response'")
                .contains("resourceType() !== 'document'")
                .contains("id=\"providerSelectionCsrfToken\"[^>]*\\sname=\"([^\"]+)\"")
                .contains("const observedTokenName = renderedTokenName")
                .contains("type: 'csrf-token-name-unresolved'")
                .contains("new URLSearchParams(requestBody).get(tokenName)")
                .contains("type: 'missing-csrf-token'")
                .contains("response.status() >= 400")
                .contains("finding.type === 'http'");
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
