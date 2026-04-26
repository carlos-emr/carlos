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

<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<fmt:setBundle basename="oscarResources"/>

<html>
<head>
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

<br>
<br>
<%-- balanceForwardHtml + transactionHtml are pre-rendered HTML row blocks
     assembled by the assembler from RA-file H6/H7 records. Emitted raw
     (matches the legacy raw-HTML output behavior) since the content is
     server-controlled (parsed from a server-side fixed-width OHIP file). --%>
<table bgcolor="#EEEEEE" bordercolor="#666666" border="1">
    ${raDescModel.balanceForwardHtml}
</table>
<br>
<table bgcolor="#EEEEFF" bordercolor="#666666" border="1">
    ${raDescModel.transactionHtml}
</table>

<c:if test="${not empty raDescModel.premiumRows}">
<form action="${pageContext.request.contextPath}/billing/CA/ON/ApplyPractitionerPremium" method="post">
    <input type="hidden" name="rano" value="<carlos:encode value="${raDescModel.raNo}" context="htmlAttribute"/>"/>
    <input type="hidden" name="method" value="applyPremium"/>
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
