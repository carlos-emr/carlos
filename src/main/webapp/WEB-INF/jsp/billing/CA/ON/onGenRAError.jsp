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
  Page role: Renders `onGenRAError.jsp` for the Ontario billing workflow.
  Expected request model data includes: onGenRAErrorModel.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    // Defensive top-of-page model resolver. The canonical entrypoint is
    // billing/CA/ON/ViewOnGenRAError; any direct forward gets the privilege
    // check + assembler re-run inline so the body can stay 100% EL.
    %>
<c:if test="${onGenRAErrorModel.valid}">
<html>
<head>
    <script type="text/javascript" src="${pageContext.request.contextPath}/js/global.js"></script>
    <link rel="stylesheet" type="text/css" href="billingON.css"/>
    <title>Billing Reconcilliation</title>
</head>

<body leftmargin="0" topmargin="0" marginwidth="0" marginheight="0">

<table border="0" cellspacing="0" cellpadding="0" width="100%">
    <form action="${pageContext.request.contextPath}/billing/CA/ON/ViewOnGenRAError">
        <tr class="myDarkGreen">
            <th align='LEFT'><font color="#FFFFFF"> Billing
                Reconcilliation - Error Report</font></th>
            <th align='RIGHT'><select name="proNo">
                <option value="all" ${onGenRAErrorModel.selectedProviderOhip == 'all' ? 'selected' : ''}>All
                    Providers
                </option>
                <c:forEach var="opt" items="${onGenRAErrorModel.providerOptions}">
                    <option value="<carlos:encode value='${opt.ohipNo}' context='htmlAttribute'/>"
                            ${onGenRAErrorModel.selectedProviderOhip == opt.ohipNo ? 'selected' : ''}>
                        <carlos:encode value='${opt.lastName}' context='html'/>,<carlos:encode value='${opt.firstName}' context='html'/>
                    </option>
                </c:forEach>
            </select><input type=submit name='submit' value='Generate'> <input
                    type="hidden" name="rano" value="<carlos:encode value='${onGenRAErrorModel.raNo}' context='htmlAttribute'/>"> <input
                    type='button' name='print' value='Print' onClick='window.print()'>
                <input type='button' name='close' value='Close'
                       onClick='window.close()'></th>
        </tr>
    </form>
</table>

<c:choose>
    <c:when test="${not onGenRAErrorModel.showProviderRows}">
        <%-- "All providers" / no-selection branch — legacy JSP rendered an empty default row only. --%>
        <table width="100%" border="1" cellspacing="0" cellpadding="0">
            <tr class="myYellow">
                <th width="10%">Billing No</th>
                <th width="15%">Demographic</th>
                <th width="10%">Service Date</th>
                <th width="10%">Service Code</th>
                <th width="15%">Count</th>
                <th width="15%">Claim</th>
                <th width="15%">Pay</th>
                <th>Error</th>
            </tr>
            <tr>
                <td>&nbsp;</td>
                <td>&nbsp;</td>
                <td>&nbsp;</td>
                <td>&nbsp;</td>
                <td>&nbsp;</td>
                <td align="right">&nbsp;</td>
                <td align="right">&nbsp;</td>
                <td align="right">&nbsp;</td>
            </tr>
        </table>
    </c:when>
    <c:otherwise>
        <table width="100%" border="0" cellspacing="1" cellpadding="0" class="myIvory">
            <tr class="myYellow">
                <th width="10%">Billing No</th>
                <th width="25%">Demographic</th>
                <th width="10%">Service Date</th>
                <th width="10%">Service Code</th>
                <th width="10%">Count</th>
                <th width="15%">Claim</th>
                <th width="15%">Pay</th>
                <th>Error</th>
            </tr>
            <c:forEach var="row" items="${onGenRAErrorModel.errorRows}" varStatus="rs">
                <tr class="${rs.index % 2 == 0 ? 'myGreen' : ''}">
                    <td align="center"><carlos:encode value='${row.account}' context='html'/></td>
                    <td><carlos:encode value='${row.demoLast}' context='html'/></td>
                    <td align="center"><carlos:encode value='${row.serviceDate}' context='html'/></td>
                    <td align="center"><carlos:encode value='${row.serviceCode}' context='html'/></td>
                    <td align="center"><carlos:encode value='${row.serviceNo}' context='html'/></td>
                    <td align="right"><carlos:encode value='${row.amountSubmit}' context='html'/></td>
                    <td align="right"><carlos:encode value='${row.amountPay}' context='html'/></td>
                    <td align="right"><carlos:encode value='${row.explain}' context='html'/></td>
                </tr>
            </c:forEach>
        </table>
    </c:otherwise>
</c:choose>

</body>
</html>
</c:if>
