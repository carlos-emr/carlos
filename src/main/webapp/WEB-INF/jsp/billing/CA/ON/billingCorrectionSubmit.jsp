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
  Purpose: Supports billingCorrectionSubmit in the Ontario billing workflow.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<%--
    billingCorrectionSubmit.jsp (view) - Ontario billing correction result page.
    Displayed after BillingCorrectionSubmit2Action completes the correction.
    @since 2026
--%>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<!DOCTYPE html>
<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <script type="text/javascript" src="${pageContext.request.contextPath}/js/global.js"></script>
    <title><fmt:message key="billing.billingCorrectionSubmit.title"/></title>
</head>
<body>
<table border="0" cellspacing="0" cellpadding="0" width="100%">
    <tr bgcolor="${not empty correctionError ? '#bd4848' : '#486ebd'}">
        <th align="CENTER" nowrap><font face="Helvetica" color="#FFFFFF">
            <c:choose>
                <c:when test="${not empty correctionError}">
                    <fmt:message key="billing.billingCorrectionSubmit.msgFailed"/>
                </c:when>
                <c:otherwise>
                    <fmt:message key="billing.billingCorrectionSubmit.msgSuccessfull"/>
                </c:otherwise>
            </c:choose>
        </font></th>
    </tr>
    <c:if test="${not empty correctionErrorMessage}">
        <tr>
            <td align="CENTER">
                <p style="color:#bd4848"><strong><fmt:message key="billing.billingCorrectionSubmit.lblReason"/></strong>
                    <carlos:encode value="${correctionErrorMessage}"/></p>
            </td>
        </tr>
    </c:if>
</table>

<form action="${pageContext.request.contextPath}/billing/CA/ON/ViewBillingCorrection">
    <input type="hidden" name="billing_no" value="">
    <c:if test="${empty correctionError}">
        <input type="submit" value="<fmt:message key='billing.billingCorrectionSubmit.btnCorrectAnother'/>" name="submit">
    </c:if>
    <input type="button" value="<fmt:message key='billing.billingCorrectionSubmit.btnClose'/>" onClick="window.close()">
</form>
</body>
</html>
