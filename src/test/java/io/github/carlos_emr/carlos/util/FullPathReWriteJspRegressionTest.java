/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("security")
@DisplayName("FullPathReWrite JSP regressions")
class FullPathReWriteJspRegressionTest {

    private static final Path ADJUST_BILL_JSP = Path.of(
            "src/main/webapp/WEB-INF/jsp/billing/CA/BC/adjustBill.jsp");
    private static final Path BILLING_BC_JSP = Path.of(
            "src/main/webapp/WEB-INF/jsp/billing/CA/BC/billingBC.jsp");
    private static final Path BILLING_SVC_TRAY_ASSOC_JSP = Path.of(
            "src/main/webapp/WEB-INF/jsp/billing/CA/BC/billingSVCTrayAssoc.jsp");
    private static final Path DXCODE_SVCCODE_ASSOC_JSP = Path.of(
            "src/main/webapp/WEB-INF/jsp/billing/CA/BC/dxcode_svccode_assoc.jsp");
    private static final Path FORMWCB_JSP = Path.of(
            "src/main/webapp/WEB-INF/jsp/billing/CA/BC/formwcb.jsp");
    private static final Path WEBAPP = Path.of("src/main/webapp");
    private static final Path STRUTS_CONFIG_DIR = Path.of("src/main/webapp/WEB-INF/classes");

    /** Whole start tag, so {@code jspPage} is found whatever its attribute position. */
    private static final Pattern REWRITE_TAG = Pattern.compile("<rewrite:reWrite\\b([^>]*)>");
    private static final Pattern JSP_PAGE_ATTRIBUTE = Pattern.compile(
            "\\bjspPage\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')");
    private static final Pattern STRUTS_ACTION_NAME = Pattern.compile(
            "<action\\b[^>]*?\\bname=\"([^\"]+)\"");
    private static final Pattern JSP_COMMENT = Pattern.compile("<%--.*?--%>", Pattern.DOTALL);

    /**
     * Issue #4132: page-relative rewrite targets resolved against the internal
     * {@code /WEB-INF/jsp/...} path after a gate forward, so Prevention Print and eDoc
     * Combine PDF answered 404. Every live call site must name a context-relative Struts
     * route that actually exists, so the link no longer depends on the URL that rendered
     * the page.
     */
    @Test
    @DisplayName("should target mapped context-relative Struts routes from every rewrite call site")
    void shouldTargetMappedContextRelativeRoutes_fromEveryRewriteCallSite() throws IOException {
        Set<String> actionNames = strutsActionNames();
        List<String> targets = new ArrayList<>();
        List<String> problems = new ArrayList<>();

        try (Stream<Path> files = Files.walk(WEBAPP)) {
            for (Path jsp : files.filter(f -> f.toString().endsWith(".jsp")
                    || f.toString().endsWith(".jspf")).toList()) {
                String source = JSP_COMMENT.matcher(read(jsp)).replaceAll("");
                Matcher matcher = REWRITE_TAG.matcher(source);
                while (matcher.find()) {
                    Matcher attribute = JSP_PAGE_ATTRIBUTE.matcher(matcher.group(1));
                    if (!attribute.find()) {
                        problems.add(jsp + ": rewrite tag without a literal jspPage: " + matcher.group());
                        continue;
                    }
                    String target = attribute.group(1) != null ? attribute.group(1) : attribute.group(2);
                    targets.add(target);
                    if (!target.startsWith("/")) {
                        problems.add(jsp + ": page-relative target '" + target + "'");
                    } else if (!actionNames.contains(target.substring(1))) {
                        problems.add(jsp + ": unmapped route '" + target + "'");
                    }
                }
            }
        }

        assertThat(targets)
                .as("rewrite call sites discovered")
                .contains("/prevention/printPrevention", "/documentManager/combinePDFs");
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("should encode runtime query values in rewrite popup URLs")
    void shouldEncodeRuntimeQueryValues_inRewritePopupUrls() throws IOException {
        String adjustBill = read(ADJUST_BILL_JSP);
        String billingBC = read(BILLING_BC_JSP);
        String billingSvcTrayAssoc = read(BILLING_SVC_TRAY_ASSOC_JSP);
        String dxcodeSvcCodeAssoc = read(DXCODE_SVCCODE_ASSOC_JSP);
        String formwcb = read(FORMWCB_JSP);

        assertThat(adjustBill)
                .contains("var t0 = encodeURIComponent(document.forms['reprocessBilling'].elements[d].value)")
                .contains("var t0 = encodeURIComponent(document.forms[form].elements[code].value)")
                .contains("var t0 = encodeURIComponent(document.forms['reprocessBilling'].service_code.value)")
                .contains("encodeURIComponent(form)")
                .contains("encodeURIComponent(field)")
                .contains("encodeURIComponent(str)")
                .contains("encodeURIComponent(serviceDate)")
                .contains("encodeURIComponent(providerNo)")
                .doesNotContain("&formElement=' + d")
                .doesNotContain("&formName=' + form")
                .doesNotContain("&formElementPrice=' + price")
                .doesNotContain("&searchStr=' + str")
                .doesNotContain("&serviceDate=' + serviceDate")
                .doesNotContain("&providerNo=' + providerNo");

        assertThat(billingBC)
                .contains("var t0 = encodeURIComponent(document.BillingCreateBillingForm.xml_other1.value)")
                .contains("var t0 = encodeURIComponent(document.BillingCreateBillingForm.elements[d].value)")
                .contains("encodeURIComponent(d)")
                .doesNotContain("escape(document.BillingCreateBillingForm")
                .doesNotContain("escape(document.serviceform")
                .doesNotContain("&formElement=' + d");

        assertThat(billingSvcTrayAssoc)
                .contains("encodeURIComponent(form)")
                .contains("encodeURIComponent(field)")
                .contains("encodeURIComponent(str)")
                .doesNotContain("+ '?form=' + form + '&field=' + field + '&searchStr=' + str");

        assertThat(dxcodeSvcCodeAssoc)
                .contains("var t0 = encodeURIComponent(document.forms[0].xml_other1.value)")
                .doesNotContain("escape(document.forms[0].xml_other1.value)");

        assertThat(formwcb)
                .contains("context=\"javaScriptBlock\"")
                .contains("encodeURIComponent(form)")
                .contains("encodeURIComponent(field)")
                .contains("encodeURIComponent(str)")
                .doesNotContain("&searchStr=' + str");
    }

    private static Set<String> strutsActionNames() throws IOException {
        Set<String> names = new HashSet<>();
        try (Stream<Path> files = Files.list(STRUTS_CONFIG_DIR)) {
            for (Path config : files.filter(f -> f.getFileName().toString().matches("struts.*\\.xml")).toList()) {
                Matcher matcher = STRUTS_ACTION_NAME.matcher(read(config));
                while (matcher.find()) {
                    names.add(matcher.group(1));
                }
            }
        }
        return names;
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
