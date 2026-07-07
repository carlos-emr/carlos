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
  Purpose: Supports billingON3rdViewPayment in the Ontario billing workflow.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">

<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <title>View payment details</title>
</head>
<body>

<label>View payment details:</label>
<hr>
<p/>
<table width="100%" border="0">
    <tbody>
    <tr>
        <th>Patient Name</th>
        <th>Invoice #</th>
        <th>Service Code</th>
        <th>Payment</th>
        <th>Discount</th>
        <th>Refund Credit / Overpayment</th>
        <th>Refund / Write off</th>
    </tr>
    <c:if test="${not empty itemDataList}">
        <c:forEach var="itemData" items="${itemDataList}" varStatus="idx">
            <tr align="center">
                <td>${carlos:forHtml(itemData.patientName)}</td>
                <td>${carlos:forHtml(itemData.claimHeaderId)}</td>
                <td>${carlos:forHtml(itemData.serviceCode)}</td>
                <td>${carlos:forHtml(itemData.paid)}</td>
                <td>${carlos:forHtml(itemData.discount)}</td>
                <td>${carlos:forHtml(itemData.credit)}</td>
                <td>${carlos:forHtml(itemData.refund)}</td>
            </tr>
        </c:forEach>
    </c:if>
    </tbody>
</table>
<hr/>
<c:if test="${not empty billPayment}">
    <table width="100%" border="0">
        <tr align="right">
            <td width="86%">Date:</td>
            <td><carlos:encode value="${billPayment.paymentDateFormatted}" context="html"/>
            </td>
        </tr>
        <tr align="right">
            <td width="86%">Payment type:</td>
            <td><carlos:encode value="${paymentTypeName}" context="html"/>
            </td>
        </tr>
        <tr align="right">
            <td width="86%">Payment:</td>
            <td><carlos:encode value="${billPayment.total_payment}" context="html"/>
            </td>
        </tr>
        <tr align="right">
            <td width="86%">Discount:</td>
            <td><carlos:encode value="${billPayment.total_discount}" context="html"/>
            </td>
        </tr>
        <tr align="right">
            <td width="86%">Refund Credit / Overpayment:</td>
            <td><carlos:encode value="${billPayment.total_credit}" context="html"/>
            </td>
        </tr>
        <tr align="right">
            <td width="86%">Refund / Write off:</td>
            <td><carlos:encode value="${billPayment.total_refund}" context="html"/>
            </td>
        </tr>
    </table>
</c:if>
</body>
</html>
