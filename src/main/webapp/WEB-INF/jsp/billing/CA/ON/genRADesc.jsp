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
  Purpose: Supports genRADesc in the Ontario billing workflow.
  Expected request model data includes: raDescModel.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<fmt:setBundle basename="oscarResources"/>

<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <script type="text/javascript" src="${pageContext.request.contextPath}/js/global.js"></script>
    <title>CARLOS EMR</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/web.css">
</head>

<body onLoad="setfocus()" topmargin="0" leftmargin="0" rightmargin="0">
<table border="0" cellspacing="0" cellpadding="0" width="100%">
    <tr bgcolor="#486ebd">
        <th align="left">
            <form><input type="button" onclick="window.print()"
                         value="Print"></form>
        </th>
        <th align="center"><font face="Helvetica" color="#FFFFFF">
            Reconcillation Report </font></th>
        <th align="right">
            <form><input type="button"
                         onClick="popupPage(700,600,'${pageContext.request.contextPath}/billing/CA/ON/ViewBillingClipboard')" value="Clipboard"></form>
        </th>
    </tr>
</table>

Cheque amount:
<carlos:encode value="${raDescModel.chequeTotal}" context="html"/>
<br>
Local clinic :
<carlos:encode value="${raDescModel.localTotal}" context="html"/>
<br>
Other clinic :
<carlos:encode value="${raDescModel.otherTotal}" context="html"/><br>

OB Total :
<carlos:encode value="${raDescModel.obTotal}" context="html"/><br>
Colposcopy Total :
<carlos:encode value="${raDescModel.coTotal}" context="html"/><br>

<c:if test="${raDescModel.raFileIncomplete}">
    <div class="alert alert-danger">
        <carlos:encode value="${raDescModel.raFileWarning}" context="html"/>
    </div>
</c:if>

<br>
<br>
<table bgcolor="#EEEEEE" bordercolor="#666666" border="1">
    <tr><td colspan="4">Balance Forward Record - Amount Brought Forward (ABF)</td></tr>
    <tr><td>Claims Adjustment</td><td>Advances</td><td>Reductions</td><td>Deductions</td></tr>
    <tr>
        <td><carlos:encode value="${raDescModel.balanceForwardRow.claimsAdjustment}" context="html"/></td>
        <td><carlos:encode value="${raDescModel.balanceForwardRow.advances}" context="html"/></td>
        <td><carlos:encode value="${raDescModel.balanceForwardRow.reductions}" context="html"/></td>
        <td><carlos:encode value="${raDescModel.balanceForwardRow.deductions}" context="html"/></td>
    </tr>
</table>
<br>
<c:if test="${not empty raDescModel.transactionRows}">
<table bgcolor="#EEEEFF" bordercolor="#666666" border="1">
    <tr><td colspan="5">Accounting Transaction Record</td></tr>
    <tr><td width="14%">Transaction</td><td width="12%">Transaction Date</td>
        <td width="17%">Cheque Issued</td><td width="13%">Amount</td><td width="44%">Message</td></tr>
    <c:forEach var="__txn" items="${raDescModel.transactionRows}">
    <tr>
        <td width="14%"><carlos:encode value="${__txn.transaction}" context="html"/></td>
        <td width="12%"><carlos:encode value="${__txn.transactionDate}" context="html"/></td>
        <td width="17%"><carlos:encode value="${__txn.chequeIssued}" context="html"/></td>
        <td width="13%"><carlos:encode value="${__txn.amount}" context="html"/></td>
        <td width="44%"><carlos:encode value="${__txn.message}" context="html"/></td>
    </tr>
    </c:forEach>
</table>
</c:if>

<c:if test="${not empty raDescModel.premiumRows}">
<form action="${pageContext.request.contextPath}/billing/CA/ON/ApplyPractitionerPremium" method="post">
    <input type="hidden" name="rano" value="<carlos:encode value="${raDescModel.raNo}" context="htmlAttribute"/>"/>
    <input type="hidden" name="method" value="applyPremium"/>
    <%-- Checkbox/select input names are keyed by premiumId so the apply action
         can update only the practitioner-premium rows the operator selected. --%>
    <h3><fmt:message key="oscar.billing.on.genRADesc.premiumTitle"/></h3>
    <table>
        <thead>
        <th style="width:30px;font-family: helvetica; background-color: #486ebd; color:white;"><fmt:message key="oscar.billing.on.genRADesc.applyPremium"/></th>
        <th style="font-family: helvetica; background-color: #486ebd; color:white;"><fmt:message key="oscar.billing.on.genRADesc.ohipNo"/></th>
        <th style="font-family: helvetica; background-color: #486ebd; color:white;"><fmt:message key="oscar.billing.on.genRADesc.providerName"/></th>
        <th style="font-family: helvetica; background-color: #486ebd; color:white;"><fmt:message key="oscar.billing.on.genRADesc.totalMonthlyPayment"/></th>
        <th style="font-family: helvetica; background-color: #486ebd; color:white;"><fmt:message key="oscar.billing.on.genRADesc.paymentDate"/></th>
        </thead>
        <c:forEach var="__row" items="${raDescModel.premiumRows}">
        <tr>
            <td><input name="choosePremium<carlos:encode value='${__row.premiumId}' context='htmlAttribute'/>" type="checkbox" value="Y" <c:if test="${__row.checked}">checked</c:if>/>
            <td><carlos:encode value="${__row.providerOhipNo}" context="html"/></td>
            <td><select name="providerNo<carlos:encode value='${__row.premiumId}' context='htmlAttribute'/>">
                <c:forEach var="__opt" items="${__row.providerOptions}">
                <option value="<carlos:encode value='${__opt.providerNo}' context='htmlAttribute'/>" <c:if test="${__opt.selected}">selected="selected"</c:if>><carlos:encode value="${__opt.formattedName}" context="html"/></option>
                </c:forEach>
            </select></td>
            <td><carlos:encode value="${__row.amountPay}" context="html"/></td>
            <td><carlos:encode value="${__row.payDateStr}" context="html"/></td>
        </tr>
        </c:forEach>
        <tr>
            <td colspan="5" style="text-align: right"><input type="submit"
                                                             value="<fmt:message key="oscar.billing.on.genRADesc.submitPremium"/>"/>
            </td>
        </tr>
    </table>
</form>
</c:if>
<pre><carlos:encode value="${raDescModel.messageTxt}" context="html"/></pre>

</body>
</html>
