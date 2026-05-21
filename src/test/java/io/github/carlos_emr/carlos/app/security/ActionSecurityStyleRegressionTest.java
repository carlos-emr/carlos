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
package io.github.carlos_emr.carlos.app.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards cross-cutting Struts2 action security style conventions.
 *
 * @since 2026-05-21
 */
@DisplayName("Struts2 action security style regressions")
@Tag("unit")
@Tag("security")
class ActionSecurityStyleRegressionTest {

    private static final Path PRODUCTION_JAVA = Path.of("src", "main", "java");
    private static final Path REPORT_AGE_SEX_JSP = Path.of("src", "main", "webapp", "WEB-INF", "jsp",
            "oscarReport", "oscarReportAgeSex.jsp");
    private static final Path TICKLER_DEMO_MAIN_JSP = Path.of("src", "main", "webapp", "WEB-INF", "jsp",
            "tickler", "ticklerDemoMain.jsp");

    @Test
    @DisplayName("should use the standard security object message across production Java sources")
    void shouldUseStandardSecurityObjectMessage_acrossProductionJavaSources() throws IOException {
        List<Path> oldMessageFiles;
        try (Stream<Path> paths = Files.walk(PRODUCTION_JAVA)) {
            oldMessageFiles = paths
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(ActionSecurityStyleRegressionTest::containsOldSecurityObjectMessage)
                    .toList();
        }

        assertThat(oldMessageFiles)
                .as("Use 'missing required security object: <priv>' for privilege failures")
                .isEmpty();
    }

    @Test
    @DisplayName("should annotate execute methods in Struts2 actions")
    void shouldAnnotateExecuteMethods_inStruts2Actions() throws IOException {
        List<String> missingOverride = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(PRODUCTION_JAVA)) {
            for (Path path : paths.filter(path -> path.getFileName().toString().endsWith("2Action.java")).toList()) {
                List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i).matches("\\s*public\\s+String\\s+execute\\s*\\(.*")) {
                        int previous = i - 1;
                        while (previous >= 0 && lines.get(previous).trim().isEmpty()) {
                            previous--;
                        }
                        if (previous < 0 || !"@Override".equals(lines.get(previous).trim())) {
                            missingOverride.add(path + ":" + (i + 1));
                        }
                    }
                }
            }
        }

        assertThat(missingOverride).isEmpty();
    }

    @Test
    @DisplayName("should gate method-routed action methods directly")
    void shouldGateMethodRoutedActionMethods_directly() throws IOException {
        String groupPreference = read(PRODUCTION_JAVA.resolve(Path.of("io", "github", "carlos_emr", "carlos",
                "commn", "web", "GroupPreference2Action.java")));
        String rxManagePharmacy = read(PRODUCTION_JAVA.resolve(Path.of("io", "github", "carlos_emr", "carlos",
                "prescript", "pageUtil", "RxManagePharmacy2Action.java")));
        String userPreference = read(PRODUCTION_JAVA.resolve(Path.of("io", "github", "carlos_emr", "carlos",
                "provider", "web", "UserPreference2Action.java")));
        String providerInfo = read(PRODUCTION_JAVA.resolve(Path.of("io", "github", "carlos_emr", "carlos",
                "PMmodule", "web", "ProviderInfo2Action.java")));

        assertThat(groupPreference).contains("public String save() {\n        requirePreferenceWritePrivilege();");
        assertThat(groupPreference).contains("public String setDefaultBillingForm() {\n        requirePreferenceWritePrivilege();");
        assertThat(userPreference).contains("public String saveGeneral() {\n        requirePreferenceWritePrivilege();");

        assertThat(rxManagePharmacy).contains("public String delete() throws IOException {\n        requireRxWritePrivilege();");
        assertThat(rxManagePharmacy).contains("public String unlink() {\n        requireRxWritePrivilege();");
        assertThat(rxManagePharmacy).contains("public String setPreferred() {\n        requireRxWritePrivilege();");
        assertThat(rxManagePharmacy).contains("public String add() {\n        requireRxWritePrivilege();");
        assertThat(rxManagePharmacy).contains("public String save() {\n        requireRxWritePrivilege();");
        assertThat(rxManagePharmacy).contains("public String search() {\n        requireRxReadPrivilege();");
        assertThat(rxManagePharmacy).contains("public String searchCity() {\n        requireRxReadPrivilege();");
        assertThat(rxManagePharmacy).contains("public String getPharmacyInfo() throws IOException {\n        requireRxReadPrivilege();");
        assertThat(rxManagePharmacy).contains("public String getPharmacyFromDemographic() throws IOException {\n        requireRxReadPrivilege();");
        assertThat(rxManagePharmacy).contains("public String getTotalDemographicsPreferedToPharmacy() throws IOException {\n        requireRxReadPrivilege();");

        assertThat(providerInfo).contains("securityInfoManager.hasPrivilege(loggedInInfo, \"_pmm_management\", \"r\", null)");
        assertThat(providerInfo).contains("providerNo.equals(loggedInInfo.getLoggedInProviderNo())");
    }

    @Test
    @DisplayName("should encode calendar query parameters in legacy JSP scriptlets")
    void shouldEncodeCalendarQueryParameters_inLegacyJspScriptlets() throws IOException {
        String reportAgeSex = read(REPORT_AGE_SEX_JSP);
        String ticklerDemoMain = read(TICKLER_DEMO_MAIN_JSP);

        assertCalendarQueryParametersEncoded(reportAgeSex);
        assertCalendarQueryParametersEncoded(ticklerDemoMain);
        assertThat(reportAgeSex).doesNotContain("year=<%=curYear%>");
        assertThat(ticklerDemoMain).doesNotContain("year=<%=curYear%>");
    }

    private static void assertCalendarQueryParametersEncoded(String jsp) {
        assertThat(jsp).contains("year=<%=SafeEncode.forUriComponent(String.valueOf(curYear))%>");
        assertThat(jsp).contains("month=<%=SafeEncode.forUriComponent(String.valueOf(curMonth))%>");
    }

    private static boolean containsOldSecurityObjectMessage(Path path) {
        try {
            return read(path).contains("missing required " + "sec object");
        } catch (IOException e) {
            throw new AssertionError("Unable to read " + path, e);
        }
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
