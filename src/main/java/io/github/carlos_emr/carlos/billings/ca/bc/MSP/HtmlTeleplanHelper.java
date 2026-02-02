//CHECKSTYLE:OFF
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
 */


package io.github.carlos_emr.carlos.billings.ca.bc.MSP;

import io.github.carlos_emr.Misc;
import org.owasp.encoder.Encode;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Used to consolidate the teleplan submission html into one place.
 *
 * @author jay
 */
public class HtmlTeleplanHelper {

    /**
     * Creates a new instance of HtmlTeleplanHelper
     */
    public HtmlTeleplanHelper() {
    }


    public static String htmlHeaderGen(String errorMsg) {
        StringBuilder htmlContentHeader = new StringBuilder();
        htmlContentHeader.append("<html>");
        htmlContentHeader.append("<head><script type='text/javascript'>function openBrWindow(theURL,winName,features) { window.open(theURL,winName,features);}</script> </head>");
        htmlContentHeader.append("<body><style type='text/css'><!-- .bodytext{  font-family: Tahoma, Arial, Helvetica, sans-serif;  font-size: 12px; font-style: normal;  line-height: normal;  font-weight: normal;  font-variant: normal;  text-transform: none;  color: #003366;  text-decoration: none; --></style>\n");
        htmlContentHeader.append("<table width='100%' border='0' cellspacing='0' cellpadding='0'> \n");
        htmlContentHeader.append("<tr> \n");
        htmlContentHeader.append("<td colspan='11' class='bodytext'>").append(Encode.forHtml(errorMsg)).append("</td> \n");
        htmlContentHeader.append("</tr> \n");
        return htmlContentHeader.toString();
    }

    public static String htmlNewProviderSection(String providerNo, Date date) {
        String dateStr = "";
        try {
            dateStr = new SimpleDateFormat("yyyyMMdd").format(new Date());
        } catch (Exception e) {
        }
        StringBuilder htmlContentHeader = new StringBuilder();
        htmlContentHeader.append("<tr> \n");
        htmlContentHeader.append("<td colspan='4' class='bodytext'>Billing Invoice for Billing No.").append(Encode.forHtml(providerNo)).append("</td> \n");
        htmlContentHeader.append("<td colspan='7' class='bodytext'>Payment date of ").append(Encode.forHtml(dateStr)).append("</td> \n");
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
        htmlContentHeader.append("<td colspan='4' class='bodytext'>Billing Invoice for Billing No.").append(Encode.forHtml(providerNo)).append("</td>");
        htmlContentHeader.append("<td colspan='7' class='bodytext'>Payment date of ").append(Encode.forHtml(output)).append("</td>");
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
        htmlContentHeader.append(Encode.forHtml(errorMsg));
        return htmlContentHeader.toString();
    }

    public static String htmlLine(String billingMasterNo, String invNo, String demoName, String phn, String serviceDate, String billingCode, String billAmount, String dx1, String dx2, String dx3) {
        StringBuilder htmlContent = new StringBuilder();
        String paddedBillingMasterNo = Misc.forwardZero(billingMasterNo, 7);
        htmlContent.append("<tr> \n");
        htmlContent.append("<td class='bodytext'> \n");
        htmlContent.append("<a href='#' onClick=\"openBrWindow('adjustBill.jsp?billingmaster_no=");
        htmlContent.append(Encode.forJavaScript(paddedBillingMasterNo));
        htmlContent.append("','','resizable=yes,scrollbars=yes,top=0,left=0,width=900,height=600'); return false;\">");
        htmlContent.append(Encode.forHtml(invNo));
        htmlContent.append("</a>");
        htmlContent.append("</td>\n");
        htmlContent.append("<td class='bodytext'>").append(Encode.forHtml(demoName)).append("</td>\n");
        htmlContent.append("<td class='bodytext'>").append(Encode.forHtml(phn)).append("</td>\n");
        htmlContent.append("<td class='bodytext'>").append(Encode.forHtml(serviceDate)).append("</td>\n");
        htmlContent.append("<td class='bodytext'>").append(Encode.forHtml(billingCode)).append("</td>\n");
        htmlContent.append("<td align='right' class='bodytext'>").append(Encode.forHtml(billAmount)).append("</td>\n");
        htmlContent.append("<td align='right' class='bodytext'>").append(Encode.forHtml(Misc.backwardSpace(dx1, 5))).append("</td>\n");
        htmlContent.append("<td align='right' class='bodytext'>").append(Encode.forHtml(Misc.backwardSpace(dx2, 5))).append("</td>\n");
        htmlContent.append("<td align='right' class='bodytext'>").append(Encode.forHtml(Misc.backwardSpace(dx3, 5))).append("</td>\n");
        htmlContent.append("<td class='bodytext'>").append(Encode.forHtml(paddedBillingMasterNo)).append("</td>\n");
        htmlContent.append("<td class='bodytext'>&nbsp;</td>\n");
        htmlContent.append("</tr>\n");
        return htmlContent.toString();
    }

    public static String htmlFooter(String providerNo, int count, BigDecimal total) {
        StringBuilder htmlFooter = new StringBuilder();
        htmlFooter.append("<tr><td colspan='11' class='bodytext'>&nbsp;</td>  </tr>  <tr>    <td colspan='5' class='bodytext'>Billing No: ");
        htmlFooter.append(Encode.forHtml(providerNo));
        htmlFooter.append(": ");
        htmlFooter.append(count);
        htmlFooter.append(" RECORDS PROCESSED</td>    <td colspan='6' class='bodytext'>TOTAL: ");
        htmlFooter.append(total);
        htmlFooter.append("</td></tr>");
        return htmlFooter.toString();
    }

    public static String htmlBottom() {
        return "</table></body></html>";
    }
}
