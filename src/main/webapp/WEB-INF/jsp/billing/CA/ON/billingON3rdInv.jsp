<%--

    Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
    This software is published under the GPL GNU General Public License.
    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public License
    as published by the Free Software Foundation; either version 2
    of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>

<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@page import="io.github.carlos_emr.carlos.billings.ca.on.data.BillingON3rdInvViewModel" %>
<fmt:setBundle basename="oscarResources"/>

<%
    // ViewBillingON3rdInv2Action enforces _billing r and assembles the view
    // model with the 9 DAO lookups the JSP body used to perform.
    BillingON3rdInvViewModel invoiceModel =
            (BillingON3rdInvViewModel) request.getAttribute("invoiceModel");
    if (invoiceModel == null) {
        // Defensive fallback: any caller that forwards directly here gets a
        // safe stub render. Mirrors the pattern used by billingON.jsp /
        // billingONCorrection.jsp.
        io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().warn(
                "billingON3rdInv.jsp reached without invoiceModel — caller should "
              + "route through billing/CA/ON/ViewBillingON3rdInv.");
        invoiceModel = BillingON3rdInvViewModel.builder().build();
    }
%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html>
<head>
    <style type="text/css" media="print">
        .doNotPrint {
            display: none;
        }
    </style>
    <style type="text/css" media="">
        .titleBar {
            background-color: gray;
            padding-top: .5em;
            padding-bottom: .5em;
            padding-left: .5em;
        }
    </style>
    <script type="text/javascript" src="<%=request.getContextPath()%>/library/jquery/jquery-3.7.1.min.js"></script>
    <script src="<%=request.getContextPath()%>/library/jquery/jquery-compat.js"></script>
    <script>
        jQuery.noConflict();
    </script>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <script type="text/javascript">
        function submitForm(methodName) {
            // The sendEmail() method in BillingInvoice2Action.java is not supported.
            if (methodName == "print") {
                document.forms[0].method.value = "getPrintPDF";
            }
            document.forms[0].submit();
        }
    </script>
    <title>Billing Invoice</title>
    <oscar:customInterface section="invoice"/>
</head>
<body>
<form action="<%=request.getContextPath()%>/BillingInvoice" method="post">
    <input type="hidden" name="method" value=""/>
    <input type="hidden" name="invoiceNo" id="invoiceNo" value="<carlos:encode value="${invoiceModel.invoiceNoStr}" context="htmlAttribute"/>"/>
    <div class="doNotPrint">
        <div class="titleBar">
            <input type="button" name="printInvoice" value="<fmt:message key="billing.billing3rdInv.printPDF"/>"
                   onClick="submitForm('print')"/>
            <input type="button" name="printHtml" value="Print" onclick="window.print();">
        </div>
    </div>
</form>
<table width="100%" border="0">
    <tr>
        <td>
            <% if (invoiceModel.isMultisiteEnabled()) {
                if (invoiceModel.isSiteLogoAvailable()) { %>
            <img src="<%=request.getContextPath() %>/documentManager/ManageDocument?method=display&doc_no=<carlos:encode value="${invoiceModel.siteLogoId}" context="uriComponent"/>"/>
            <% } else { %>
            <b><carlos:encode value="${invoiceModel.siteName}" context="html"/></b><br/>
            <carlos:encode value="${invoiceModel.siteAddress}" context="html"/><br/>
            <carlos:encode value="${invoiceModel.siteCity}" context="html"/>, <carlos:encode value="${invoiceModel.siteProvince}" context="html"/><br/>
            <carlos:encode value="${invoiceModel.sitePostal}" context="html"/><br/>
            Tel.: <carlos:encode value="${invoiceModel.sitePhone}" context="html"/><br/>
            <% } %>
            <% } else if (invoiceModel.isClinicLogoImgExists()) { %>
            <img src="<%=request.getContextPath() %>/billing/ca/on/DisplayInvoiceLogo"/>
            <% } else { %>
            <b><carlos:encode value="${invoiceModel.clinicName}" context="html"/></b><br/>
            <carlos:encode value="${invoiceModel.clinicAddress}" context="html"/><br/>
            <carlos:encode value="${invoiceModel.clinicCity}" context="html"/>, <carlos:encode value="${invoiceModel.clinicProvince}" context="html"/><br/>
            <carlos:encode value="${invoiceModel.clinicPostal}" context="html"/><br/>
            Tel.: <carlos:encode value="${invoiceModel.clinicPhone}" context="html"/><br/>
            <% } %>
        </td>
        <td align="right" valign="top"><font size="+2"><b>Invoice
            - <carlos:encode value="${invoiceModel.invoiceNoStr}" context="html"/>
        </b></font><br/>
            Print Date:<carlos:encode value="${invoiceModel.printDate}" context="html"/><br/>
            <% if (invoiceModel.isDueDateEnabled()) { %>
            <b><fmt:message key="oscar.billing.CA.ON.3rdpartyinvoice.dueDate"/>:</b><carlos:encode value="${invoiceModel.dueDateStr}" context="html"/>
            <% } %>
        </td>
    </tr>
</table>

<hr>
<table width="100%" border="0">
    <tr>
        <td width="50%" valign="top">Bill To<br/>
            <pre><carlos:encode value="${invoiceModel.billTo}" context="html"/></pre>
        </td>
        <td valign="top">Remit To<br/>
            <pre><carlos:encode value="${invoiceModel.remitTo}" context="html"/></pre>
        </td>
    </tr>
</table>

<oscar:customInterface section="billingInvoice"/>
<table width="100%" border="0">
    <tr>
        <td id="ptName">Patient: <% if (invoiceModel.isInvoiceLoaded()) { %><carlos:encode value="${invoiceModel.patientName}" context="html"/><% } else { %>N/A<% } %>
        </td>
        <td id="ptDemoNo"> (<% if (invoiceModel.isInvoiceLoaded()) { %><carlos:encode value="${invoiceModel.patientDemoNo}" context="html"/><% } else { %>N/A<% } %>)</td>
        <td id="ptGender"><% if (invoiceModel.isInvoiceLoaded()) { %><carlos:encode value="${invoiceModel.patientGender}" context="html"/><% } else { %>N/A<% } %>
        </td>
        <td id="ptDOB"> DOB: <% if (invoiceModel.isInvoiceLoaded()) { %><carlos:encode value="${invoiceModel.patientDob}" context="html"/><% } else { %>N/A<% } %>
        </td>
    </tr>
    <tr>
        <td id="ptHin">
            Insurance No: <% if (invoiceModel.isInvoiceLoaded() && !invoiceModel.getPatientHin().isEmpty()) { %><carlos:encode value="${invoiceModel.patientHin}" context="html"/><% } else { %>N/A<% } %>
        </td>
    </tr>
</table>

<hr>

<table width="100%" border="0">
    <tr>
        <td><carlos:encode value="${invoiceModel.invoiceComment}" context="html"/>
        </td>
    </tr>
</table>

<table width="100%" border="0">
    <tr>
        <th>Service Date</th>
        <th>Practitioner</th>
        <th>Payee</th>
        <th>Ref. Doctor</th>
    </tr>
    <tr align="center">
        <td><carlos:encode value="${invoiceModel.billingDateStr}" context="html"/>
        </td>
        <td><carlos:encode value="${invoiceModel.providerFormattedName}" context="html"/>
        </td>
        <td><carlos:encode value="${invoiceModel.payeeName}" context="html"/>
        </td>
        <td><carlos:encode value="${invoiceModel.invoiceRefNum}" context="html"/>
        </td>
    </tr>
</table>

<hr/>

<table width="100%" border="0">
    <tr>
        <th>Item #:</th>
        <th>Description</th>
        <th>Service Code</th>
        <th>Qty</th>
        <th>Dx</th>
        <th>Amount</th>
    </tr>
    <% for (BillingON3rdInvViewModel.InvoiceItem __ii : invoiceModel.getInvoiceItems()) { %>
    <tr align="center">
        <td><carlos:encode value='<%= __ii.itemId() %>' context="html"/>
        </td>
        <td><carlos:encode value='<%= __ii.description() %>' context="html"/>
        </td>
        <td><carlos:encode value='<%= __ii.serviceCode() %>' context="html"/>
        </td>
        <td><carlos:encode value='<%= __ii.quantity() %>' context="html"/>
        </td>
        <td><carlos:encode value='<%= __ii.dx() %>' context="html"/>
        </td>
        <td align="right"><carlos:encode value='<%= __ii.fee() %>' context="html"/>
        </td>
    </tr>
    <% } %>
</table>

<hr/>
<table width="100%" border="0">
    <tr align="right">
        <td width="86%">Total:</td>
        <td><carlos:encode value="${invoiceModel.totalAmount}" context="html"/>
        </td>
    </tr>
    <tr align="right">
        <td>Payments:</td>
        <td><carlos:encode value="${invoiceModel.paymentAmount}" context="html"/>
        </td>
    </tr>
    <tr align="right">
        <td>Discounts:</td>
        <td><carlos:encode value="${invoiceModel.discountAmount}" context="html"/>
        </td>
    </tr>
    <tr align="right">
        <td>Refund Credit / Overpayment:</td>
        <td><carlos:encode value="${invoiceModel.creditAmount}" context="html"/>
        </td>
    </tr>
    <tr align="right">
        <td>Refund / Write off:</td>
        <td><carlos:encode value="${invoiceModel.refundAmount}" context="html"/>
        </td>
    </tr>

    <tr align="right">
        <td><b>Balance:</b></td>
        <td><carlos:encode value="${invoiceModel.balanceAmount}" context="html"/>
        </td>
    </tr>
    <tr align="right">
        <td>(<carlos:encode value="${invoiceModel.paymentMethodLabel}" context="html"/>)</td>
        <td></td>
    </tr>
</table>

</body>
</html>
