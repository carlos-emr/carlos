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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The BC bill form and the private-bill receipt, as pages: what they must render for a bill to be
 * entered and an invoice to be updated or printed (issues #4716, #4717 and #5068). The browser
 * checks {@code billing-bc-bill-create-save}, {@code billing-bc-private-bill-receipt} and
 * {@code billing-bc-receipt-print-notes} drive the same paths live.
 */
@Tag("unit")
@Tag("billing")
@DisplayName("BC bill form and receipt JSP regressions")
class BillingBcFormJspRegressionTest {

    private static final Path BILLING_BC_JSP = Path.of("src/main/webapp/WEB-INF/jsp/billing/CA/BC/billingBC.jsp");
    private static final Path BILL_RECEIPT_JSP = Path.of("src/main/webapp/WEB-INF/jsp/billing/CA/BC/billReceipt.jsp");
    private static final Path BILLING_VIEW_JSP = Path.of("src/main/webapp/WEB-INF/jsp/billing/CA/BC/billingView.jsp");
    private static final Path STRUTS_BILLING_XML = Path.of("src/main/webapp/WEB-INF/classes/struts-billing.xml");
    private static final Path WCB_CLOSE_JSP = Path.of("src/main/webapp/WEB-INF/jsp/billing/CA/BC/wcbClose.jsp");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /**
     * Issue #4716: nothing stores {@code BillingCreateBillingForm} in the session any more, and the
     * Payment Method list read it unguarded, so every open of the bill form answered HTTP 500. The
     * page builds the form when the session has none, before any use of it.
     */
    @Test
    @DisplayName("should build the bill form when the session has none, before its first use")
    void shouldBuildForm_whenSessionHasNone() throws IOException {
        String page = read(BILLING_BC_JSP);

        assertThat(page).containsSubsequence(
                "BillingCreateBillingForm thisForm = (BillingCreateBillingForm) request.getSession().getAttribute(\"BillingCreateBillingForm\");",
                "if (thisForm == null) {",
                "thisForm = new BillingCreateBillingForm();",
                "thisForm.setXml_provider(",
                "\"Pri\".equalsIgnoreCase(thisForm.getXml_billtype())");
        assertThat(page).doesNotContain("if (thisForm != null) {");
    }

    /**
     * The page's scripts address the form as {@code document.BillingCreateBillingForm} and
     * {@code #bcBillingForm} (validation, autocomplete), the names Struts 1's form tag used to give it.
     */
    @Test
    @DisplayName("should give the bill form the name and id its scripts use")
    void shouldNameForm_asItsScriptsAddressIt() throws IOException {
        String page = read(BILLING_BC_JSP);

        assertThat(page).contains("document.BillingCreateBillingForm.xml_billtype")
                .contains("jQuery(\"#bcBillingForm\").validate(")
                .contains("<form name=\"BillingCreateBillingForm\" id=\"bcBillingForm\"")
                .doesNotContain("style=\"bcBillingForm\"");
    }

    /**
     * The bill type is posted (it had lost its name to a leftover Struts 1 {@code property}
     * attribute) and shows the type the page worked out for this bill.
     */
    @Test
    @DisplayName("should post the bill type and preselect the computed one")
    void shouldPostBillType_andPreselectComputedType() throws IOException {
        String page = read(BILLING_BC_JSP);

        assertThat(Pattern.compile("<select[^>]*\\bid=\"xml_billtype\"[^>]*\\bname=\"xml_billtype\"", Pattern.DOTALL)
                .matcher(page).find()).as("the bill type select has a name").isTrue();
        assertThat(page).doesNotContain("property=\"xml_billtype\"")
                .contains("<option value=\"Pri\" <%= \"Pri\".equals(formBillType) ? \"selected\" : \"\" %>>Private</option>");
    }

    /**
     * The service date is read-only and required by the page's validation; it must show the date
     * the page computed (the appointment's, or the service date), as the bound form did.
     */
    @Test
    @DisplayName("should show the computed service date and billing provider")
    void shouldShowComputedDefaults_inTheFields() throws IOException {
        String page = read(BILLING_BC_JSP);

        assertThat(page).contains("id=\"xml_appointment_date\" value=\"<carlos:encode value='<%= thisForm.getXml_appointment_date() %>' context=\"htmlAttribute\"/>\"")
                .contains("billphysician[j].getProviderNo().equals(thisForm.getXml_provider()) ? \"selected\" : \"\"");
    }

    /**
     * "Bill WCB" lists the patient's WCB forms through their gate action: the page fetched the bare
     * JSP name, which answered 404 once the JSPs moved under WEB-INF. The list is loaded once, since
     * submitting runs the same function again, and reloading the list then cleared the chosen WCB
     * form while the diagnosis was still being checked, so Continue was refused.
     */
    @Test
    @DisplayName("should list the patient's WCB forms through their action, once")
    void shouldListWcbForms_throughTheirActionOnce() throws IOException {
        assertThat(read(BILLING_BC_JSP)).doesNotContain("\"wcbForms.jsp")
                .containsSubsequence(
                        "function toggleWCB() {",
                        "if (jQuery(\"#wcbForms\").children().length === 0) {",
                        "callReplacementWebService(\"<%= request.getContextPath() %>/billing/CA/BC/ViewWcbForms<%=wcb%>\", 'wcbForms');");
        assertThat(read(STRUTS_BILLING_XML)).contains("<action name=\"billing/CA/BC/ViewWcbForms\"");
    }

    /**
     * library/bootstrap-datetimepicker.min.js was removed with the Bootstrap 5 migration (#635) and
     * nothing on the page calls it; once the form rendered again, its include answered 404.
     */
    @Test
    @DisplayName("should load no script that the WAR does not ship")
    void shouldNotLoadRemovedDatetimepicker_onBillForm() throws IOException {
        assertThat(read(BILLING_BC_JSP)).doesNotContain("bootstrap-datetimepicker");
    }

    /**
     * Issue #4717: the receipt's Update Invoice posted an empty bill number, so the update failed
     * with HTTP 500. The hidden field carries the receipt's own bill number.
     */
    @Test
    @DisplayName("should post the receipt's bill number with Update Invoice")
    void shouldPostBillNumber_withUpdateInvoice() throws IOException {
        String page = read(BILL_RECEIPT_JSP);

        assertThat(page).containsSubsequence(
                "<form action=\"${pageContext.request.contextPath}/billing/CA/BC/UpdateBilling\" method=\"post\">",
                "<input type=\"hidden\" name=\"billingNo\" id=\"billingNo\" value=\"${carlos:forHtmlAttribute(billingViewBean.billingNo)}\"/>");
    }

    /**
     * Issue #4673: the receipt shown after Update Invoice read the invoice the session held, which
     * was the one viewed last in any window, not the one just updated. The update now redirects to
     * the view of the receipt it came from, every bill of it (a private bill is one row per service
     * line), and both pages read only the invoice their own request loaded.
     */
    @Test
    @DisplayName("should show the updated invoice's own whole receipt after Update Invoice")
    void shouldRedirectToUpdatedInvoice_afterUpdateInvoice() throws IOException {
        String xml = read(STRUTS_BILLING_XML);
        Matcher mapping = Pattern.compile(
                "<action name=\"billing/CA/BC/UpdateBilling\"[^>]*>(.*?)</action>", Pattern.DOTALL).matcher(xml);
        assertThat(mapping.find()).isTrue();

        assertThat(mapping.group(1).replaceAll("(?s)<!--.*?-->", ""))
                .contains("<result name=\"success\" type=\"redirect\">${receiptView}</result>");
        assertThat(read(BILL_RECEIPT_JSP)).containsSubsequence(
                "<form action=\"${pageContext.request.contextPath}/billing/CA/BC/UpdateBilling\" method=\"post\">",
                "<c:forEach var=\"receiptBillingNo\" items=\"${requestScope.receiptBillingNos}\">",
                "<input type=\"hidden\" name=\"receiptBillingNo\" value=\"${carlos:forHtmlAttribute(receiptBillingNo)}\"/>");
        for (Path page : new Path[]{BILL_RECEIPT_JSP, BILLING_VIEW_JSP}) {
            assertThat(read(page)).as(page.toString())
                    .contains("(BillingViewBean) request.getAttribute(\"billingViewBean\")")
                    .doesNotContain("findAttribute(\"billingViewBean\")");
        }
    }

    /**
     * Issue #4864 item 10 and finding 432: a refused Continue returns to the bill form, which lists
     * the reasons the bill holds. Each is shown as text.
     */
    @Test
    @DisplayName("should list a refused Continue's reasons as text")
    void shouldListRefusalReasons_asText() throws IOException {
        assertThat(read(BILLING_BC_JSP))
                .contains("java.util.List<String> actionErrors = (java.util.List<String>) request.getAttribute(\"actionErrors\");")
                .contains("<li><%= SafeEncode.forHtmlContent(error) %></li>")
                .doesNotContain("<li><%= error %></li>");
    }

    /** Issue #4864 item 7: Save and Close's page wrote a stray "v" ahead of its own markup. */
    @Test
    @DisplayName("should write nothing ahead of Save and Close's page")
    void shouldWriteNothing_beforeSaveAndClosesPage() throws IOException {
        String jsp = read(WCB_CLOSE_JSP);
        int header = jsp.indexOf("--%>");
        assertThat(header).isPositive();
        String afterHeader = jsp.substring(header + "--%>".length());
        assertThat(afterHeader.substring(0, afterHeader.indexOf("<%")).trim()).isEmpty();
    }

    /**
     * Issue #5068: the receipt's Edit Invoice, Print, Print With Notes and Cancel sit inside the
     * Update Invoice form. A button with no type submits its form, so Print posted the update and
     * reloaded the receipt before it could print. Only Update Invoice submits the form.
     */
    @Test
    @DisplayName("should submit the receipt's form only from Update Invoice")
    void shouldSubmitReceiptForm_onlyFromUpdateInvoice() throws IOException {
        String page = read(BILL_RECEIPT_JSP);
        int start = page.indexOf("<form action=\"${pageContext.request.contextPath}/billing/CA/BC/UpdateBilling\"");
        assertThat(start).as("the Update Invoice form").isNotNegative();
        int end = page.indexOf("</form>", start);
        assertThat(end).isGreaterThan(start);
        // Comments, scriptlets and custom tags out, so an attribute that holds one (Edit Invoice's
        // onclick) does not end its button's start tag early.
        String form = page.substring(start, end)
                .replaceAll("(?s)<%--.*?--%>|<!--.*?-->", "")
                .replaceAll("(?s)<%.*?%>", "")
                .replaceAll("<\\w+:[^>]*>", "");

        List<String> labels = new ArrayList<>();
        List<String> submitting = new ArrayList<>();
        Pattern typeButton = Pattern.compile("\\btype\\s*=\\s*\"button\"");
        Matcher button = Pattern.compile("(?s)<button\\b([^>]*)>(.*?)</button>").matcher(form);
        while (button.find()) {
            String label = button.group(2).trim();
            labels.add(label);
            if (!typeButton.matcher(button.group(1)).find()) {
                submitting.add(label);
            }
        }
        assertThat(labels).containsExactly("Edit Invoice", "Print", "Print With Notes", "Cancel");
        assertThat(submitting).as("buttons without type=\"button\", which submit the Update Invoice form").isEmpty();
        assertThat(Pattern.compile("type=\"submit\"").matcher(form).results().count()).isEqualTo(1);
        assertThat(form).contains("<input type=\"submit\" name=\"submit\" class=\"header\" value=\"Update Invoice\" />");
    }
}
