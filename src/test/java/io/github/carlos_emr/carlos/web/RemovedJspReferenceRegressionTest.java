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
package io.github.carlos_emr.carlos.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Removed JSP reference regression tests")
@Tag("unit")
@Tag("regression")
class RemovedJspReferenceRegressionTest {
    private static final Pattern OSCAR_JS_SCRIPT =
            Pattern.compile("<script\\b[^>]*src=[\"'][^\"']*/share/javascript/Oscar\\.js[\"'][^>]*>",
                    Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("Appointment admin day should not link to removed PMmodule popup JSPs")
    void shouldNotContainRemovedPmmodulePopups_inAppointmentAdminDayJsp() throws IOException {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/provider/appointmentprovideradminday.jsp"));

        assertThat(jsp)
                .doesNotContain("/PMmodule/createAnonymousClient.jsp")
                .doesNotContain("/PMmodule/createPEClient.jsp");
    }

    @Test
    @DisplayName("SearchDrug3 should not reference removed TreatmentMyD JSP")
    void shouldNotContainRemovedTreatmentMyDJsp_inSearchDrug3Jsp() throws IOException {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/rx/SearchDrug3.jsp"));

        assertThat(jsp).doesNotContain("/rx/TreatmentMyD.jsp");
    }

    @Test
    @DisplayName("WEB-INF JSPs should not reference removed DataTables assets")
    void shouldNotReferenceRemovedDataTablesAssets_inWebInfJsps() throws IOException {
        Path jspRoot = Path.of("src/main/webapp/WEB-INF/jsp");

        try (Stream<Path> paths = Files.walk(jspRoot)) {
            List<Path> offenders = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".jsp") || path.toString().endsWith(".jspf"))
                    .filter(path -> containsAny(path, "/library/DataTables/datatables.min.js",
                            "/library/DataTables/DataTables-1.13.11/css/jquery.dataTables.min.css"))
                    .toList();

            assertThat(offenders)
                    .as("JSPs must reference shipped DataTables-1.13.11 assets, not removed compatibility paths")
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("JSPs using global head should not include Oscar.js directly")
    void shouldNotDuplicateOscarJsInclude_inGlobalHeadJsps() throws IOException {
        Path jspRoot = Path.of("src/main/webapp/WEB-INF/jsp");

        try (Stream<Path> paths = Files.walk(jspRoot)) {
            List<Path> offenders = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".jsp") || path.toString().endsWith(".jspf"))
                    .filter(path -> !path.endsWith("global-head.jspf"))
                    .filter(RemovedJspReferenceRegressionTest::hasGlobalHeadIncludeAndOscarJsScript)
                    .toList();

            assertThat(offenders)
                    .withFailMessage(() -> formatOffenderMessage(
                            "global-head.jspf already includes Oscar.js; JSPs that use it must not include Oscar.js again",
                            offenders))
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("Oscar.js force-window list should tolerate duplicate script includes")
    void shouldUseRedeclarableForceWindowList_inOscarJs() throws IOException {
        String oscarJs = Files.readString(Path.of("src/main/webapp/share/javascript/Oscar.js"));

        assertThat(oscarJs)
                .as("legacy JSPs can still include Oscar.js more than once on the same page")
                .contains("window.forceWindowPaths = window.forceWindowPaths || [")
                .doesNotContain("const forceWindowPaths = [")
                .doesNotContain("let forceWindowPaths = [");
    }

    @Test
    @DisplayName("Print-label JSPs should HTML-encode stored default printer names")
    void shouldEncodeDefaultPrinterName_inPrintLabelJsps() throws IOException {
        String printDemoLabel = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/demographic/printDemoLabel.jsp"));
        String printClientLabLabel = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/demographic/printClientLabLabel.jsp"));
        String printEnvelope = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/demographic/printEnvelope.jsp"));

        assertThat(printDemoLabel)
                .contains("<carlos:encode value='<%= defaultPrinterName %>' context=\"html\"/>")
                .doesNotContain("<%=defaultPrinterName%>");
        assertThat(printClientLabLabel)
                .contains("<carlos:encode value='<%= defaultPrinterName %>' context=\"html\"/>")
                .doesNotContain("<%=defaultPrinterName%>");
        assertThat(printEnvelope)
                .contains("<carlos:encode value='<%= defaultPrinterName %>' context=\"html\"/>")
                .doesNotContain("<%=defaultPrinterName%>");
    }

    @Test
    @DisplayName("Standalone admin JSPs should guard admin-chrome helper calls")
    void shouldGuardAdminChromeHelpers_inStandaloneAdminJsps() throws IOException {
        String myGroup = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/admin/admindisplaymygroup.jsp"));
        String labForwarding = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/admin/labforwardingrules.jsp"));

        assertThat(myGroup)
                .doesNotContain("if it is")
                .contains("typeof parent.parent.resizeIframe === 'function'");
        assertThat(labForwarding).contains("typeof registerFormSubmit === 'function'");
    }

    @Test
    @DisplayName("Security update JSP should archive admin security changes through SecurityManager")
    void shouldRouteAdminSecurityUpdates_throughSecurityManagerInSecurityUpdateJsp() throws IOException {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/admin/securityupdate.jsp"));

        assertThat(jsp)
                .contains("LoggedInInfo.getLoggedInInfoFromSession(request)")
                .contains("securityManager.updateSecurityRecord(loggedInInfo, s);")
                .doesNotContain("securityDao.saveEntity(s);");
    }

    @Test
    @DisplayName("Lab forwarding JSP should load jQuery for standalone popup use")
    void shouldLoadJqueryBeforeInlineScript_inLabForwardingJsp() throws IOException {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/admin/labforwardingrules.jsp"));

        assertThat(jsp.indexOf("/library/jquery/jquery-3.7.1.min.js"))
                .isPositive()
                .isLessThan(jsp.indexOf("$(\"#providers-selection\")"));
    }

    @Test
    @DisplayName("Admin routes, UI, permissions, and docs should not expose removed Traceability report")
    void shouldNotExposeTraceabilityReport_fromAdminSurfaces() throws IOException {
        String strutsAdmin = Files.readString(Path.of("src/main/webapp/WEB-INF/classes/struts-admin.xml"));
        String adminJsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/admin/admin.jsp"));
        String adminLeftNav = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/administration/leftNav.jspf"));
        String oscarData = Files.readString(Path.of("database/mysql/oscardata.sql"));
        String traceabilityPermissionCleanup = Files.readString(Path.of(
                "database/mysql/updates/update-2026-05-26-remove-traceability-permission.sql"));

        assertThat(strutsAdmin)
                .doesNotContain("GenerateTraceAction")
                .doesNotContain("GenerateTraceabilityReportAction")
                .doesNotContain("ViewTraceReport")
                .doesNotContain("traceReport.jsp")
                .doesNotContain("admin.traceability");
        assertThat(adminJsp)
                .doesNotContain("ViewTraceReport")
                .doesNotContain("admin.traceability")
                .doesNotContain("traceabilityReport");
        assertThat(adminLeftNav)
                .doesNotContain("ViewTraceReport")
                .doesNotContain("admin.traceability")
                .doesNotContain("traceabilityReport");
        assertThat(Files.exists(Path.of("src/main/webapp/WEB-INF/jsp/admin/traceReport.jsp"))).isFalse();
        assertThat(Files.exists(Path.of("src/main/java/io/github/carlos_emr/carlos/admin/gate/ViewTraceReport2Action.java"))).isFalse();
        assertThat(oscarData).doesNotContain("_admin.traceability");
        assertThat(traceabilityPermissionCleanup)
                .contains("secObjPrivilege")
                .contains("secObjectName")
                .contains("_admin.traceability");

        Path traceabilitySourceRoot = Path.of("src/main/java/io/github/carlos_emr/carlos/admin/traceability");
        if (Files.exists(traceabilitySourceRoot)) {
            try (Stream<Path> paths = Files.walk(traceabilitySourceRoot)) {
                assertThat(paths.filter(Files::isRegularFile).toList())
                        .as("Traceability report backend source files should be removed")
                        .isEmpty();
            }
        }

        try (Stream<Path> paths = Files.walk(Path.of("src/main/resources"))) {
            List<Path> offenders = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("oscarResources_"))
                    .filter(path -> containsAny(path, "admin.admin.traceabilityReport",
                            "admin.admin.downloadTraceabilityData", "admin.admin.downloadEmpty"))
                    .toList();

            assertThat(offenders)
                    .as("Removed Traceability report message keys should not remain in resource bundles")
                    .isEmpty();
        }

        try (Stream<Path> paths = Files.walk(Path.of("docs"))) {
            List<Path> offenders = paths
                    .filter(Files::isRegularFile)
                    .filter(RemovedJspReferenceRegressionTest::isTextDocumentationFile)
                    .filter(path -> containsAny(path, "GenerateTraceAction",
                            "GenerateTraceabilityReportAction", "ViewTraceReport", "traceReport.jsp",
                            "admin.traceability", "traceabilityReport", "downloadTraceabilityData",
                            "Utilities for traceability", "Build 'traceability report'"))
                    .toList();

            assertThat(offenders)
                    .as("Removed Traceability report routes and message keys should not remain in docs")
                    .isEmpty();
        }
    }

    private static boolean isTextDocumentationFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.equals("element-list")
                || fileName.equals("3rdpartylicenses")
                || fileName.endsWith(".css")
                || fileName.endsWith(".html")
                || fileName.endsWith(".js")
                || fileName.endsWith(".json")
                || fileName.endsWith(".md")
                || fileName.endsWith(".txt")
                || fileName.endsWith(".xml")
                || fileName.endsWith(".xsd")
                || fileName.endsWith(".yaml")
                || fileName.endsWith(".yml");
    }

    private static boolean containsAny(Path path, String... needles) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            for (String needle : needles) {
                if (content.contains(needle)) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to inspect " + path, e);
        }
    }

    private static final Pattern GLOBAL_HEAD_INCLUDE = Pattern.compile(
            "<%@\\s*include\\s+file\\s*=\\s*(['\"])(?:/?(?:WEB-INF/jsp/)?)?(?:[^\"']+/)*includes/global-head\\.jspf\\1\\s*%>",
            Pattern.CASE_INSENSITIVE);

    private static boolean hasGlobalHeadIncludeAndOscarJsScript(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (!content.contains("global-head.jspf") || !content.contains("Oscar.js")) {
                return false;
            }
            return GLOBAL_HEAD_INCLUDE.matcher(content).find()
                    && OSCAR_JS_SCRIPT.matcher(content).find();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to inspect " + path, e);
        }
    }

    private static String formatOffenderMessage(String baseMessage, List<Path> offenders) {
        return baseMessage + System.lineSeparator()
                + offenders.stream()
                .map(Path::toString)
                .sorted()
                .collect(Collectors.joining(System.lineSeparator()));
    }
}
