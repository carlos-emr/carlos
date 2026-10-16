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
package io.github.carlos_emr.carlos.billings.ca.bc.MSP;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #4801: BC Billing ▸ Accounting Reports. The page's form posted to an address no action is mapped
 * at, three PDFs printed a report parameter the action no longer set ({@code provider}), and the
 * spreadsheet Payments and Refunds summary named a template file that was not shipped.
 *
 * <p>These tests read the page, the action, the struts mapping and the report templates, so a change
 * on one side alone fails the build.</p>
 *
 * @since 2026-10-17
 */
@Tag("unit")
@Tag("billing")
@DisplayName("BC Account Reports page, action and templates (#4801)")
class BcAccountReportsRegressionTest {

    private static final Path PAGE = Path.of("src/main/webapp/WEB-INF/jsp/billing/CA/BC/billingAccountReports.jsp");
    private static final Path STRUTS_BILLING = Path.of("src/main/webapp/WEB-INF/classes/struts-billing.xml");
    private static final Path ACTION =
            Path.of("src/main/java/io/github/carlos_emr/carlos/billings/ca/bc/MSP/CreateBillingReport2Action.java");
    private static final Path REPORTS = Path.of("src/main/resources/oscar/oscarBilling/ca/bc/reports");
    /** The report templates may be loaded only from this allowlist. */
    private static final Path DOCUMENT_CREATOR = Path.of("src/main/java/io/github/carlos_emr/OscarDocumentCreator.java");

    /** The report types the Accounting Reports page offers, each as PDF and as spreadsheet. */
    private static final List<String> ACCOUNT_REPORTS =
            List.of("REP_INVOICE", "REP_REJ", "REP_ACCOUNT_REC", "REP_WO", "REP_PAYREF", "REP_PAYREF_SUM");
    /** The MSP remittance reports the Reconciliation Reports page links to, in both formats. */
    private static final List<String> REMITTANCE_REPORTS = List.of("REP_MSPREM", "REP_MSPREMSUM");
    private static final List<String> FORMATS = List.of("pdf", "csv");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static Set<String> matches(String text, String regex) {
        Set<String> found = new TreeSet<>();
        Matcher matcher = Pattern.compile(regex).matcher(text);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    /** The template file the action names for each report type ({@code cfgReports()}). */
    private static Map<String, String> templateByReportType() throws IOException {
        Map<String, String> templates = new LinkedHashMap<>();
        Matcher matcher = Pattern.compile("reportCfg\\.setProperty\\(MSPReconcile\\.(\\w+), \"([^\"]+)\"\\)")
                .matcher(read(ACTION));
        while (matcher.find()) {
            templates.put(matcher.group(1), matcher.group(2));
        }
        return templates;
    }

    @Test
    @DisplayName("should post the report form to the mapped report action")
    void shouldPostReportForm_toMappedRoute() throws IOException {
        // The page sets <base href> to the application root, so a relative action resolves there.
        assertThat(read(PAGE)).contains(
                "<form action=\"<%= request.getContextPath() %>/billing/CA/BC/createBillingReportAction\" method=\"post\">");
        assertThat(read(STRUTS_BILLING)).contains("<action name=\"billing/CA/BC/createBillingReportAction\"");
    }

    @Test
    @DisplayName("should set every parameter the account report templates print")
    void shouldSetEveryParameter_theAccountReportTemplatesPrint() throws IOException {
        Set<String> set = matches(read(ACTION), "reportParams\\.put\\(\"(\\w+)\"");
        Map<String, String> templates = templateByReportType();
        for (String report : ACCOUNT_REPORTS) {
            for (String format : FORMATS) {
                Path template = REPORTS.resolve(format + "_" + templates.get(report));
                Set<String> printed = matches(read(template), "\\$P\\{(\\w+)\\}");
                assertThat(set).as(template.toString()).containsAll(printed);
            }
        }
        // The three reports that print the provider: a rename once set it under another name.
        assertThat(set).contains("provider", "payee");
    }

    @Test
    @DisplayName("should ship and allow every report template the action names, in each format it is offered")
    void shouldShipEveryTemplate_theActionNames() throws IOException {
        Map<String, String> templates = templateByReportType();
        String allowed = read(DOCUMENT_CREATOR);
        // Top-level report templates only; the MSP remittance summary's sub-reports are not checked here.
        for (String report : List.of(ACCOUNT_REPORTS, REMITTANCE_REPORTS).stream().flatMap(List::stream).toList()) {
            assertThat(templates).as(report).containsKey(report);
            for (String format : FORMATS) {
                String file = format + "_" + templates.get(report);
                assertThat(REPORTS.resolve(file)).as(report + " " + format).exists();
                // A template missing from the allowlist loads as null, the same 500 as a missing file.
                assertThat(allowed).as(report + " " + format + " allowlist")
                        .contains("m.put(bc + \"" + file + "\",");
            }
        }
    }
}
