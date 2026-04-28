<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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

    CARLOS EMR Project
    https://github.com/carlos-emr/carlos
--%>
<%--
  Page role: Renders `billingON3rdInv.jsp` for the Ontario billing workflow.
  Expected request model data includes: invoiceModel.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>

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
    <script type="text/javascript" src="${pageContext.request.contextPath}/library/jquery/jquery-3.7.1.min.js"></script>
    <script src="${pageContext.request.contextPath}/library/jquery/jquery-compat.js"></script>
    <script>
        jQuery.noConflict();
    </script>
    <script type="text/javascript" src="${pageContext.request.contextPath}/js/global.js"></script>
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
<form action="${pageContext.request.contextPath}/BillingInvoice" method="post">
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
            <c:choose>
                <c:when test="${invoiceModel.multisiteEnabled}">
                    <c:choose>
                        <c:when test="${invoiceModel.siteLogoAvailable}">
            <img src="${pageContext.request.contextPath}/documentManager/ManageDocument?method=display&doc_no=<carlos:encode value="${invoiceModel.siteLogoId}" context="uriComponent"/>"/>
                        </c:when>
                        <c:otherwise>
            <b><carlos:encode value="${invoiceModel.siteName}" context="html"/></b><br/>
            <carlos:encode value="${invoiceModel.siteAddress}" context="html"/><br/>
            <carlos:encode value="${invoiceModel.siteCity}" context="html"/>, <carlos:encode value="${invoiceModel.siteProvince}" context="html"/><br/>
            <carlos:encode value="${invoiceModel.sitePostal}" context="html"/><br/>
            Tel.: <carlos:encode value="${invoiceModel.sitePhone}" context="html"/><br/>
                        </c:otherwise>
                    </c:choose>
                </c:when>
                <c:when test="${invoiceModel.clinicLogoImgExists}">
            <img src="${pageContext.request.contextPath}/billing/ca/on/DisplayInvoiceLogo"/>
                </c:when>
                <c:otherwise>
            <b><carlos:encode value="${invoiceModel.clinicName}" context="html"/></b><br/>
            <carlos:encode value="${invoiceModel.clinicAddress}" context="html"/><br/>
            <carlos:encode value="${invoiceModel.clinicCity}" context="html"/>, <carlos:encode value="${invoiceModel.clinicProvince}" context="html"/><br/>
            <carlos:encode value="${invoiceModel.clinicPostal}" context="html"/><br/>
            Tel.: <carlos:encode value="${invoiceModel.clinicPhone}" context="html"/><br/>
                </c:otherwise>
            </c:choose>
        </td>
        <td align="right" valign="top"><font size="+2"><b>Invoice
            - <carlos:encode value="${invoiceModel.invoiceNoStr}" context="html"/>
        </b></font><br/>
            Print Date:<carlos:encode value="${invoiceModel.printDate}" context="html"/><br/>
            <c:if test="${invoiceModel.dueDateEnabled}">
            <b><fmt:message key="oscar.billing.CA.ON.3rdpartyinvoice.dueDate"/>:</b><carlos:encode value="${invoiceModel.dueDateStr}" context="html"/>
            </c:if>
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
        <td id="ptName">Patient: <c:choose><c:when test="${invoiceModel.invoiceLoaded}"><carlos:encode value="${invoiceModel.patientName}" context="html"/></c:when><c:otherwise>N/A</c:otherwise></c:choose>
        </td>
        <td id="ptDemoNo"> (<c:choose><c:when test="${invoiceModel.invoiceLoaded}"><carlos:encode value="${invoiceModel.patientDemoNo}" context="html"/></c:when><c:otherwise>N/A</c:otherwise></c:choose>)</td>
        <td id="ptGender"><c:choose><c:when test="${invoiceModel.invoiceLoaded}"><carlos:encode value="${invoiceModel.patientGender}" context="html"/></c:when><c:otherwise>N/A</c:otherwise></c:choose>
        </td>
        <td id="ptDOB"> DOB: <c:choose><c:when test="${invoiceModel.invoiceLoaded}"><carlos:encode value="${invoiceModel.patientDob}" context="html"/></c:when><c:otherwise>N/A</c:otherwise></c:choose>
        </td>
    </tr>
    <tr>
        <td id="ptHin">
            Insurance No: <c:choose><c:when test="${invoiceModel.invoiceLoaded and not empty invoiceModel.patientHin}"><carlos:encode value="${invoiceModel.patientHin}" context="html"/></c:when><c:otherwise>N/A</c:otherwise></c:choose>
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
    <c:forEach var="__ii" items="${invoiceModel.invoiceItems}">
    <tr align="center">
        <td><carlos:encode value="${__ii.itemId}" context="html"/>
        </td>
        <td><carlos:encode value="${__ii.description}" context="html"/>
        </td>
        <td><carlos:encode value="${__ii.serviceCode}" context="html"/>
        </td>
        <td><carlos:encode value="${__ii.quantity}" context="html"/>
        </td>
        <td><carlos:encode value="${__ii.dx}" context="html"/>
        </td>
        <td align="right"><carlos:encode value="${__ii.fee}" context="html"/>
        </td>
    </tr>
    </c:forEach>
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
