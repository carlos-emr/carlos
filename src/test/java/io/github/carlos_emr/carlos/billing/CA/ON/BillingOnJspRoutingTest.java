/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
package io.github.carlos_emr.carlos.billing.CA.ON;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("Billing Ontario JSP Routing Unit Tests")
@Tag("unit")
@Tag("billing")
class BillingOnJspRoutingTest {

    private static final Path BILLING_ON_JSP_DIR = Path.of("src/main/webapp/WEB-INF/jsp/billing/CA/ON");

    @Test
    void shouldUseContextAwareRoutes_inOntarioBillingJspWorkflows() throws IOException {
        try (Stream<Path> files = Files.walk(BILLING_ON_JSP_DIR)) {
            List<Path> jspFiles = files
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String fileName = path.getFileName().toString();
                        return fileName.endsWith(".jsp") || fileName.endsWith(".jspf");
                    })
                    .toList();

            for (Path jspFile : jspFiles) {
                String content = readJspContent(jspFile);
                assertThat(content)
                        .as("billing ON HTML routes should stay context-aware in %s", jspFile)
                        .doesNotContain("action=\"/billing/")
                        .doesNotContain("href=\"/billing/");

                assertThat(content)
                        .as("billing ON direct client redirects should stay context-aware in %s", jspFile)
                        .doesNotContain("location.href = \"/billing/")
                        .doesNotContain("location.href=\"/billing/")
                        .doesNotContain("window.open(\"/billing/")
                        .doesNotContain("openBrWindow(\"/billing/")
                        .doesNotContain("response.sendRedirect(\"/billing/");
            }
        }

        String billingOn = readJspContent(BILLING_ON_JSP_DIR.resolve("billingON.jsp"));
        assertThat(billingOn).contains("jQuery.getJSON(ctx + ajaxUrl");
        assertThat(billingOn).contains("/billing/CA/ON/ViewBillingDigSearchAjax");
        assertThat(billingOn).contains("/billing/CA/ON/ViewBillingCodeSearchAjax");
        assertThat(billingOn).doesNotContain("ctx + \"/billing/CA/ON/ViewBillingDigSearchAjax\"");
        assertThat(billingOn).doesNotContain("ctx + \"/billing/CA/ON/ViewBillingCodeSearchAjax\"");

        // reportINR.jsp was refactored from a scriptlet-built `inrBillingAction`
        // to a ViewModel-supplied `reportInrModel.inrBillingActionUrl` rendered
        // through `${carlos:forHtmlAttribute(...)}`. The original
        // /billing/CA/ON/ViewInr* routes are still constructed (now in the
        // ViewInrReport2Action assembler) and remain context-path-aware.
        String reportInr = readJspContent(BILLING_ON_JSP_DIR.resolve("inr/reportINR.jsp"));
        assertThat(reportInr).contains("${carlos:forHtmlAttribute(reportInrModel.inrBillingActionUrl)}");
    }

    @Test
    void shouldRenderPartialWarnings_forPaymentAndShortcutHistory() throws IOException {
        String billingOnPayment = readJspContent(BILLING_ON_JSP_DIR.resolve("billingONPayment.jsp"));
        assertThat(billingOnPayment).contains("paymentModel.paymentsPartial");
        assertThat(billingOnPayment).contains("Payment totals may be incomplete.");

        String billingShortcutPg1 = readJspContent(BILLING_ON_JSP_DIR.resolve("billingShortcutPg1.jsp"));
        assertThat(billingShortcutPg1).contains("shortcutPg1Model.historyPartial");
        assertThat(billingShortcutPg1).contains("shortcutPg1Model.historyPartialRowCount");
        assertThat(billingShortcutPg1).contains("Billing history may be incomplete.");
    }

    @Test
    void shouldRenderReviewAlertsInJspMarkup_insteadOfEscapedViewModelHtml() throws IOException {
        String billingOnReview = readJspContent(BILLING_ON_JSP_DIR.resolve("billingONReview.jsp"));

        assertThat(billingOnReview).contains("reviewModel.reviewAlerts");
        assertThat(billingOnReview).contains("class=\"alert alert-danger\"");
        assertThat(billingOnReview).contains("reviewModel.demoHeaderLine");
        assertThat(billingOnReview).doesNotContain("reviewModel.wrongMessage");
    }

    private String readJspContent(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
