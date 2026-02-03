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
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.BillingONPayment" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.BillingPaymentType" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.BillingPaymentTypeDao" %>

<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>

<%
    BillingONPayment billPayment = (BillingONPayment) request.getAttribute("billPayment");
%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<%@ page
        import="java.util.*, io.github.carlos_emr.carlos.util.*,io.github.carlos_emr.carlos.billing.ca.on.pageUtil.*,io.github.carlos_emr.carlos.billing.ca.on.data.*,io.github.carlos_emr.carlos.providers.data.*,java.math.* ,io.github.carlos_emr.carlos.billing.ca.on.administration.*" %>

<html>
<head>
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
                <td><c:out value="${itemData.patientName}"/></td>
                <td><c:out value="${itemData.ch1_id}"/></td>
                <td><c:out value="${itemData.service_code}"/></td>
                <td><c:out value="${itemData.paid}"/></td>
                <td><c:out value="${itemData.discount}"/></td>
                <td><c:out value="${itemData.credit}"/></td>
                <td><c:out value="${itemData.refund}"/></td>
            </tr>
        </c:forEach>
    </c:if>
    </tbody>
</table>
<hr/>
<%
    if (billPayment != null) {
        String payType = "";
        BillingPaymentTypeDao billPayTypeDao = (BillingPaymentTypeDao) SpringUtils.getBean(BillingPaymentTypeDao.class);
        if (billPayTypeDao != null) {
            BillingPaymentType payTypeTmp = billPayTypeDao.find(billPayment.getPaymentTypeId());
            if (payTypeTmp != null) {
                payType = payTypeTmp.getPaymentType();
            }
        }
%>
<table width="100%" border="0">
    <tr align="right">
        <td width="86%">Date:</td>
        <td><%=billPayment.getPaymentDateFormatted() %>
        </td>
    </tr>
    <tr align="right">
        <td width="86%">Payment type:</td>
        <td><%=payType %>
        </td>
    </tr>
    <tr align="right">
        <td width="86%">Payment:</td>
        <td><%=billPayment.getTotal_payment() %>
        </td>
    </tr>
    <tr align="right">
        <td width="86%">Discount:</td>
        <td><%=billPayment.getTotal_discount() %>
        </td>
    </tr>
    <tr align="right">
        <td width="86%">Refund Credit / Overpayment:</td>
        <td><%=billPayment.getTotal_credit()%>
        </td>
    </tr>
    <tr align="right">
        <td width="86%">Refund / Write off:</td>
        <td><%=billPayment.getTotal_refund() %>
        </td>
    </tr>
</table>
<%} %>
</body>
</html>
