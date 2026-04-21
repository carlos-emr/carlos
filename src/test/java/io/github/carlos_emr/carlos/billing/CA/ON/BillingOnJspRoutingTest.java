/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * Maintained by the CARLOS EMR Project (2026+).
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
    void shouldUseContextAwareRoutes_forOntarioBillingJspWorkflows() throws IOException {
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
        assertThat(billingOn).contains("jQuery.getJSON(ctx + ajaxUrl, {term: request.term}, response);");
        assertThat(billingOn).contains("initCodeAutocomplete(jQuery(\"input[name^='dxCode']\"), \"/billing/CA/ON/ViewBillingDigSearchAjax\");");
        assertThat(billingOn).contains("initCodeAutocomplete(jQuery(\"input[name^='serviceCode']\"), \"/billing/CA/ON/ViewBillingCodeSearchAjax\");");

        String reportInr = readJspContent(BILLING_ON_JSP_DIR.resolve("inr/reportINR.jsp"));
        assertThat(reportInr).contains("String inrBillingAction = request.getContextPath()");
        assertThat(reportInr).contains("action=\"<%= inrBillingAction %>\"");
    }

    private String readJspContent(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
