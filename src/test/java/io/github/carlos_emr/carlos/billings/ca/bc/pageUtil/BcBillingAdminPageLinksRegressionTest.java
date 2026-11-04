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
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #4767: the BC billing administration menu, the legacy admin page and the BC billing-code and
 * referral pages opened their pages through the actions that save ({@code billingAddCode},
 * {@code AddReferralDoc}, {@code ProcessRemittance}, {@code billingEditCode}), which ended on an error
 * page. They now open read-only gates; only POST forms still name the writers.
 */
@Tag("unit")
@Tag("billing")
@DisplayName("BC billing administration links (#4767)")
class BcBillingAdminPageLinksRegressionTest {

    private static final Path LEFT_NAV = Path.of("src/main/webapp/WEB-INF/jsp/administration/leftNav.jspf");
    private static final Path ADMIN_JSP = Path.of("src/main/webapp/WEB-INF/jsp/admin/admin.jsp");
    private static final Path BC_JSP_DIR = Path.of("src/main/webapp/WEB-INF/jsp/billing/CA/BC");
    private static final Path STRUTS_BILLING = Path.of("src/main/webapp/WEB-INF/classes/struts-billing.xml");
    private static final Path TABLE_WRAPPER =
            Path.of("src/main/java/io/github/carlos_emr/carlos/billings/ca/bc/pageUtil/BillCodesTableWrapper.java");

    private static final List<String> PAGES = List.of("billingCodeAdjust.jsp", "billingPrivateCodeAdjust.jsp",
            "billingAddCode.jsp", "billingAddPrivateCode.jsp", "billingEditCode.jsp", "billingManageReferralDoc.jsp",
            "billingAddReferralDoc.jsp", "viewReconcileReports.jsp");

    /** Writer routes a GET link must not open (their only remaining callers are POST forms). */
    private static final Pattern WRITER_ROUTE =
            Pattern.compile("(?:CA/BC/|\")(billingAddCode|AddReferralDoc|ProcessRemittance|billingEditCode)(?=[\"'?])");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /** Every writer-route mention that is not the action of a {@code method="post"} form. */
    private static List<String> getMentionsOfWriters(String source) {
        List<String> found = new ArrayList<>();
        Matcher matcher = WRITER_ROUTE.matcher(source);
        while (matcher.find()) {
            int lineStart = source.lastIndexOf('\n', matcher.start()) + 1;
            int lineEnd = source.indexOf('\n', matcher.end());
            String line = source.substring(lineStart, lineEnd < 0 ? source.length() : lineEnd);
            boolean postFormAction = line.contains("<form") && line.contains("action=") && line.contains("method=\"post\"");
            if (!postFormAction) {
                found.add(line.trim());
            }
        }
        return found;
    }

    @Test
    @DisplayName("should point the four BC billing menu entries at their read-only gates")
    void shouldPointMenuEntries_atGates() throws IOException {
        String nav = read(LEFT_NAV);
        assertThat(nav)
                .contains("rel=\"${ctx}/billing/CA/BC/ViewBillingPrivateCodeAdjust\"><fmt:message key=\"admin.admin.ManagePrivFrm\"/>")
                .contains("rel=\"${ctx}/billing/CA/BC/ViewBillingCodeAdjust\"><fmt:message key=\"admin.admin.ManageBillCodes\"/>")
                .contains("href=\"${ctx}/billing/CA/BC/ViewBillingManageReferralDoc\" class=\"contentLink defaultManageReferralDoc\"")
                .contains("rel=\"${ctx}/billing/CA/BC/ViewReconcileReports\"><fmt:message key=\"admin.admin.reconciliationReports\"/>");
        assertThat(getMentionsOfWriters(nav)).isEmpty();
    }

    @Test
    @DisplayName("should open the same four pages from the legacy admin page through the gates")
    void shouldPointAdminPagePopups_atGates() throws IOException {
        String admin = read(ADMIN_JSP);
        assertThat(admin)
                .contains("/billing/CA/BC/ViewBillingPrivateCodeAdjust\");return false;'><fmt:message key=\"admin.admin.ManagePrivFrm\"/>")
                .contains("/billing/CA/BC/ViewBillingCodeAdjust\");return false;'><fmt:message key=\"admin.admin.ManageBillCo")
                .contains("/billing/CA/BC/ViewBillingManageReferralDoc\");")
                .contains("/billing/CA/BC/ViewReconcileReports\");");
        assertThat(getMentionsOfWriters(admin)).isEmpty();
    }

    @Test
    @DisplayName("should leave writer routes only in the POST forms of the BC billing-code and referral pages")
    void shouldLeaveWriterRoutes_onlyInPostForms() throws IOException {
        for (String page : PAGES) {
            assertThat(getMentionsOfWriters(read(BC_JSP_DIR.resolve(page)))).as(page).isEmpty();
        }
        assertThat(read(TABLE_WRAPPER)).contains("<a href=\\\"ViewBillingEditCode?codeId=").doesNotContain("\"billingEditCode?");
    }

    @Test
    @DisplayName("should send the browser back to a gate, not a writer, after deleting a private code")
    void shouldRedirectToGate_afterDeletingPrivateCode() throws IOException {
        String struts = read(STRUTS_BILLING);
        assertThat(struts).contains("<result name=\"success\" type=\"redirect\">/billing/CA/BC/ViewBillingPrivateCodeAdjust</result>");
        Matcher redirect = Pattern.compile("type=\"redirect\">([^<]*)</result>").matcher(struts);
        while (redirect.find()) {
            assertThat(WRITER_ROUTE.matcher("\"" + redirect.group(1) + "\"").find()).as(redirect.group(1)).isFalse();
        }
    }

    @Test
    @DisplayName("should build the private code list's sort links on its gate, not the forwarded page path")
    void shouldSortOnGate_inPrivateCodeList() throws IOException {
        // A forwarded page's own URI is its WEB-INF path, which displaytag would otherwise use (404).
        for (String page : PAGES) {
            Matcher table = Pattern.compile("<display:table\\b[^>]*>").matcher(read(BC_JSP_DIR.resolve(page)));
            while (table.find()) {
                assertThat(table.group()).as(page).containsPattern("requestURI=\"/billing/CA/BC/View[A-Za-z]+\"");
            }
        }
        assertThat(read(BC_JSP_DIR.resolve("billingPrivateCodeAdjust.jsp")))
                .contains("requestURI=\"/billing/CA/BC/ViewBillingPrivateCodeAdjust\"");
    }

    @Test
    @DisplayName("should keep the billing code search on the code list's sort link")
    void shouldKeepSearch_onCodeListSortLink() throws IOException {
        // The code list shows only the codes matching bCode; a sort link without it opens an empty list.
        assertThat(read(BC_JSP_DIR.resolve("billingCodeAdjust.jsp"))).contains(
                "/billing/CA/BC/ViewBillingCodeAdjust?sortOrder=<%=newOrder%>&bCode=<carlos:encode value='<%= bCode %>' context=\"uriComponent\"/>");
    }

    @Test
    @DisplayName("should label each Teleplan File2 menu item for what its route does")
    void shouldLabelTeleplanFile2Items_forTheirRoutes() throws IOException {
        // #4439 D4: the generation item read "Simulate Submission File2" and the simulation "Generate Teleplan File2".
        assertThat(read(LEFT_NAV))
                .contains("rel=\"${ctx}/billing/CA/BC/GenerateTeleplanFile\"><fmt:message key=\"admin.admin.genTeleplanFile2\"/>")
                .contains("rel=\"${ctx}/billing/CA/BC/SimulateTeleplanFile\"><fmt:message key=\"admin.admin.simulateSubFile2\"/>");
        assertThat(read(ADMIN_JSP))
                .contains("/billing/CA/BC/GenerateTeleplanFile\");return false;'><fmt:message key=\"admin.admin.genTeleplanFile2\"/>")
                .contains("/billing/CA/BC/SimulateTeleplanFile\");return false;'><fmt:message key=\"admin.admin.simulateSubFile2\"/>");
    }

    @Test
    @DisplayName("should map each gate to its page")
    void shouldMapEachGate_toItsPage() throws IOException {
        String struts = read(STRUTS_BILLING);
        Map<String, String> gates = Map.of(
                "ViewBillingCodeAdjust", "billingCodeAdjust", "ViewBillingPrivateCodeAdjust", "billingPrivateCodeAdjust",
                "ViewBillingAddCode", "billingAddCode", "ViewBillingAddPrivateCode", "billingAddPrivateCode",
                "ViewBillingEditCode", "billingEditCode", "ViewBillingManageReferralDoc", "billingManageReferralDoc",
                "ViewBillingAddReferralDoc", "billingAddReferralDoc", "ViewReconcileReports", "viewReconcileReports");
        gates.forEach((route, page) -> assertThat(struts).as(route).contains(
                "<action name=\"billing/CA/BC/" + route + "\" class=\"io.github.carlos_emr.carlos.billings.ca.bc.pageUtil."
                        + route + "2Action\">\n            <result name=\"success\">/WEB-INF/jsp/billing/CA/BC/" + page + ".jsp</result>"));
    }
}
