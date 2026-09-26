/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.billings.ca.bc.MSP;

import io.github.carlos_emr.carlos.utility.SafeEncode;
import io.github.carlos_emr.Misc;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Used to consolidate the teleplan submission html into one place.
 *
 * <p>This is the single place BC Teleplan/MSP report and billing-simulation HTML is assembled. The
 * fragments are later written to the report file and emitted raw by {@code billingSim.jsp}, so every
 * record-derived value (patient name, PHN, codes, amounts, provider/billing numbers) must be encoded
 * here at construction time. Callers must not hand-build rows with string concatenation.
 *
 * @author jay
 */
public class HtmlTeleplanHelper {

    private static final String ADJUST_BILL_PAGE = "adjustBill.jsp";
    private static final String ADJUST_BILL_ID_PARAM = "billingmaster_no";
    private static final String WCB_CORRECTION_PAGE = "billingTeleplanCorrectionWCB.jsp";
    private static final String WCB_CORRECTION_ID_PARAM = "billing_no";
    private static final String POPUP_FEATURES = "resizable=yes,scrollbars=yes,top=0,left=0,width=900,height=600";

    /**
     * Creates a new instance of HtmlTeleplanHelper
     */
    public HtmlTeleplanHelper() {
    }

    // FindSecBugs POTENTIAL_XML_INJECTION: Teleplan validation producers encode billing/message data and return fixed table-row HTML fragments for report assembly.
    @SuppressFBWarnings(value = "POTENTIAL_XML_INJECTION", justification = "Teleplan validation producers encode billing/message data and return fixed table-row HTML fragments for report assembly")
    private static void appendTrustedTeleplanValidationRows(StringBuilder html, String validationRowsHtml) {
        html.append(validationRowsHtml);
    }


    public static String htmlHeaderGen(String errorMsg) {
        StringBuilder htmlContentHeader = new StringBuilder();
        htmlContentHeader.append("<html>");
        htmlContentHeader.append("<head><script type='text/javascript'>function openBrWindow(theURL,winName,features) { window.open(theURL,winName,features);}</script> </head>");
        htmlContentHeader.append("<body><style type='text/css'><!-- .bodytext{  font-family: Tahoma, Arial, Helvetica, sans-serif;  font-size: 12px; font-style: normal;  line-height: normal;  font-weight: normal;  font-variant: normal;  text-transform: none;  color: #003366;  text-decoration: none; --></style>\n");
        htmlContentHeader.append("<table width='100%' border='0' cellspacing='0' cellpadding='0'> \n");
        appendTrustedTeleplanValidationRows(htmlContentHeader, errorMsg);
        return htmlContentHeader.toString();
    }

    public static String htmlNewProviderSection(String providerNo, Date date) {
        String dateStr = new SimpleDateFormat("yyyyMMdd").format(new Date());
        StringBuilder htmlContentHeader = new StringBuilder();
        htmlContentHeader.append("<tr> \n");
        htmlContentHeader.append("<td colspan='4' class='bodytext'>Billing Invoice for Billing No.").append(SafeEncode.forHtmlContent(providerNo)).append("</td> \n");
        htmlContentHeader.append("<td colspan='7' class='bodytext'>Payment date of ").append(SafeEncode.forHtmlContent(dateStr)).append("</td> \n");
        htmlContentHeader.append("</tr> \n");

        htmlContentHeader.append("<tr> \n");
        htmlContentHeader.append("<td width='9%' class='bodytext'>INVOICE</td> \n");
        htmlContentHeader.append("<td width='19%' class='bodytext'>NAME</td> \n");
        htmlContentHeader.append("<td width='12%' class='bodytext'>HEALTH #</td> \n");
        htmlContentHeader.append("<td width='10%' class='bodytext'>BILLDATE</td> \n");
        htmlContentHeader.append("<td width='8%' class='bodytext'>CODE</td>\n ");
        htmlContentHeader.append("<td width='14%' align='right' class='bodytext'>BILLED</td> \n");
        htmlContentHeader.append("<td width='4%' align='right' class='bodytext'>DX</td> \n");
        htmlContentHeader.append("<td width='5%' align='right' class='bodytext'>DX2</td> \n");
        htmlContentHeader.append("<td width='6%' align='right' class='bodytext'>DX3</td> \n");
        htmlContentHeader.append("<td width='8%' align='right' class='bodytext'>SEQUENCE</td> \n");
        htmlContentHeader.append("<td width='5%' align='right' class='bodytext'>COMMENT</td> \n");
        htmlContentHeader.append("</tr> \n");
        return htmlContentHeader.toString();
    }

    public static String htmlContentHeaderGen(String providerNo, String output, String errorMsg) {
        StringBuilder htmlContentHeader = new StringBuilder();
        htmlContentHeader.append("<html><body><style type='text/css'><!-- .bodytext{  font-family: Tahoma, Arial, Helvetica, sans-serif;  font-size: 12px; font-style: normal;  line-height: normal;  font-weight: normal;  font-variant: normal;  text-transform: none;  color: #003366;  text-decoration: none; --></style>");
        htmlContentHeader.append("<table width='100%' border='0' cellspacing='0' cellpadding='0'>");
        htmlContentHeader.append("<tr>");
        htmlContentHeader.append("<td colspan='4' class='bodytext'>Billing Invoice for Billing No.").append(SafeEncode.forHtmlContent(providerNo)).append("</td>");
        htmlContentHeader.append("<td colspan='7' class='bodytext'>Payment date of ").append(SafeEncode.forHtmlContent(output)).append("</td>");
        htmlContentHeader.append("</tr>");
        htmlContentHeader.append("<tr>");
        htmlContentHeader.append("<td width='9%' class='bodytext'>INVOICE</td>");
        htmlContentHeader.append("<td width='19%' class='bodytext'>NAME</td>");
        htmlContentHeader.append("<td width='12%' class='bodytext'>HEALTH #</td>");
        htmlContentHeader.append("<td width='10%' class='bodytext'>BILLDATE</td>");
        htmlContentHeader.append("<td width='8%' class='bodytext'>CODE</td>");
        htmlContentHeader.append("<td width='14%' align='right' class='bodytext'>BILLED</td>");
        htmlContentHeader.append("<td width='4%' align='right' class='bodytext'>DX</td>");
        htmlContentHeader.append("<td width='5%' align='right' class='bodytext'>DX2</td>");
        htmlContentHeader.append("<td width='6%' align='right' class='bodytext'>DX3</td>");
        htmlContentHeader.append("<td width='8%' align='right' class='bodytext'>SEQUENCE</td>");
        htmlContentHeader.append("<td width='5%' align='right' class='bodytext'>COMMENT</td>");
        htmlContentHeader.append("</tr>");
        appendTrustedTeleplanValidationRows(htmlContentHeader, errorMsg);
        return htmlContentHeader.toString();
    }

    /**
     * Builds one MSP/ICBC claim row whose invoice link opens the {@code adjustBill.jsp} correction popup.
     *
     * <p>Every interpolated value comes from patient/claim records, so each one is encoded for the
     * context it lands in: HTML body for cell text, and URI component then JavaScript-in-attribute for
     * the id placed inside the {@code onClick} {@code openBrWindow('...')} call.
     *
     * @return a complete {@code <tr>} fragment safe to concatenate into the Teleplan report table
     */
    public static String htmlLine(String billingMasterNo, String invNo, String demoName, String phn, String serviceDate, String billingCode, String billAmount, String dx1, String dx2, String dx3) {
        return claimRow(ADJUST_BILL_PAGE, ADJUST_BILL_ID_PARAM, billingMasterNo, invNo, demoName, phn, serviceDate, billingCode, billAmount, dx1, dx2, dx3);
    }

    /**
     * Builds one WCB claim row whose invoice link opens the WCB correction popup keyed by {@code billing_no}.
     * Same encoding contract as {@link #htmlLine}.
     *
     * @return a complete {@code <tr>} fragment safe to concatenate into the Teleplan report table
     */
    public static String wcbHtmlLine(String billingNo, String invNo, String demoName, String phn, String serviceDate, String billingCode, String billAmount, String dx1, String dx2, String dx3) {
        return claimRow(WCB_CORRECTION_PAGE, WCB_CORRECTION_ID_PARAM, billingNo, invNo, demoName, phn, serviceDate, billingCode, billAmount, dx1, dx2, dx3);
    }

    /**
     * Builds a red validation-error row linking to {@code adjustBill.jsp} for the given billing master record.
     *
     * @param billingMasterNo billingmaster id placed (encoded) in the popup URL
     * @param message plain-text validation message; encoded for HTML body
     * @return an empty string when {@code message} is empty, otherwise a complete {@code <tr>} fragment
     */
    public static String adjustBillErrorRow(String billingMasterNo, String message) {
        return errorRow(ADJUST_BILL_PAGE, ADJUST_BILL_ID_PARAM, billingMasterNo, message);
    }

    /**
     * Builds a red validation-error row linking to the WCB correction popup for the given billing number.
     *
     * @param billingNo billing id placed (encoded) in the popup URL
     * @param message plain-text validation message; encoded for HTML body
     * @return an empty string when {@code message} is empty, otherwise a complete {@code <tr>} fragment
     */
    public static String wcbCorrectionErrorRow(String billingNo, String message) {
        return errorRow(WCB_CORRECTION_PAGE, WCB_CORRECTION_ID_PARAM, billingNo, message);
    }

    // correctionPage/idParam are always one of the compile-time constants above, never request data;
    // only the id value and the cell text are record-derived and therefore encoded.
    private static String claimRow(String correctionPage, String idParam, String id, String invNo, String demoName, String phn, String serviceDate, String billingCode, String billAmount, String dx1, String dx2, String dx3) {
        String paddedId = Misc.forwardZero(id, 7);
        StringBuilder htmlContent = new StringBuilder();
        htmlContent.append("<tr> \n");
        htmlContent.append("<td class='bodytext'> \n");
        appendCorrectionLinkOpen(htmlContent, correctionPage, idParam, paddedId);
        htmlContent.append(SafeEncode.forHtmlContent(invNo));
        htmlContent.append("</a>");
        htmlContent.append("</td>\n");
        htmlContent.append("<td class='bodytext'>").append(SafeEncode.forHtmlContent(demoName)).append("</td>\n");
        htmlContent.append("<td class='bodytext'>").append(SafeEncode.forHtmlContent(phn)).append("</td>\n");
        htmlContent.append("<td class='bodytext'>").append(SafeEncode.forHtmlContent(serviceDate)).append("</td>\n");
        htmlContent.append("<td class='bodytext'>").append(SafeEncode.forHtmlContent(billingCode)).append("</td>\n");
        htmlContent.append("<td align='right' class='bodytext'>").append(SafeEncode.forHtmlContent(billAmount)).append("</td>\n");
        htmlContent.append("<td align='right' class='bodytext'>").append(SafeEncode.forHtmlContent(Misc.backwardSpace(dx1, 5))).append("</td>\n");
        htmlContent.append("<td align='right' class='bodytext'>").append(SafeEncode.forHtmlContent(Misc.backwardSpace(dx2, 5))).append("</td>\n");
        htmlContent.append("<td align='right' class='bodytext'>").append(SafeEncode.forHtmlContent(Misc.backwardSpace(dx3, 5))).append("</td>\n");
        htmlContent.append("<td class='bodytext'>").append(SafeEncode.forHtmlContent(paddedId)).append("</td>\n");
        htmlContent.append("<td class='bodytext'>&nbsp;</td>\n");
        htmlContent.append("</tr>\n");
        return htmlContent.toString();
    }

    private static String errorRow(String correctionPage, String idParam, String id, String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }
        StringBuilder html = new StringBuilder("<tr bgcolor='red'><td colspan='11'>");
        appendCorrectionLinkOpen(html, correctionPage, idParam, Misc.forwardZero(id, 7));
        html.append(SafeEncode.forHtmlContent(message)).append("</a></td></tr>");
        return html.toString();
    }

    // The id sits inside a JS string literal inside a double-quoted onClick attribute and is also a
    // URL query value: URI-encode first (so '&', '#', '=' cannot add parameters), then JS-attribute
    // encode (so quotes cannot break out of the string or the attribute).
    private static void appendCorrectionLinkOpen(StringBuilder html, String correctionPage, String idParam, String id) {
        html.append("<a href='#' onClick=\"openBrWindow('")
                .append(correctionPage).append('?').append(idParam).append('=')
                .append(SafeEncode.forJavaScriptAttribute(SafeEncode.forUriComponent(id)))
                .append("','','").append(POPUP_FEATURES).append("'); return false;\">");
    }

    public static String htmlFooter(String providerNo, int count, BigDecimal total) {
        return htmlFooter(providerNo, String.valueOf(count), total);
    }

    /**
     * Footer variant for the legacy extract beans, which carry the processed-record count as a string.
     * The count is HTML-encoded like the provider number.
     */
    public static String htmlFooter(String providerNo, String count, BigDecimal total) {
        StringBuilder htmlFooter = new StringBuilder();
        htmlFooter.append("<tr><td colspan='11' class='bodytext'>&nbsp;</td>  </tr>  <tr>    <td colspan='5' class='bodytext'>Billing No: ");
        htmlFooter.append(SafeEncode.forHtmlContent(providerNo));
        htmlFooter.append(": ");
        htmlFooter.append(SafeEncode.forHtmlContent(count));
        htmlFooter.append(" RECORDS PROCESSED</td>    <td colspan='6' class='bodytext'>TOTAL: ");
        htmlFooter.append(total);
        htmlFooter.append("</td></tr>");
        return htmlFooter.toString();
    }

    public static String htmlBottom() {
        return "</table></body></html>";
    }
}
