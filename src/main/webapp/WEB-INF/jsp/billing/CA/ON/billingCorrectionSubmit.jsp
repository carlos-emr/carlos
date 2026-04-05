<%--
    Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
    This software is published under the GPL GNU General Public License.

    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.
--%>
<%--
    billingCorrectionSubmit.jsp (view) - Ontario billing correction result page.
    Displayed after BillingCorrectionSubmit2Action completes the correction.
    @since 2026
--%>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<!DOCTYPE html>
<html>
<head>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <title><fmt:setBundle basename="oscarResources"/><fmt:message key="billing.billingCorrectionSubmit.title"/></title>
</head>
<body>
<table border="0" cellspacing="0" cellpadding="0" width="100%">
    <tr bgcolor="#486ebd">
        <th align="CENTER" nowrap><font face="Helvetica" color="#FFFFFF">
            <fmt:setBundle basename="oscarResources"/><fmt:message key="billing.billingCorrectionSubmit.msgSuccessfull"/>
        </font></th>
    </tr>
</table>

<form action="${pageContext.request.contextPath}/billing/CA/ON/billingCorrection.jsp">
    <input type="hidden" name="billing_no" value="">
    <input type="submit" value="<fmt:setBundle basename='oscarResources'/><fmt:message key='billing.billingCorrectionSubmit.btnCorrectAnother'/>" name="submit">
    <input type="button" value="<fmt:setBundle basename='oscarResources'/><fmt:message key='billing.billingCorrectionSubmit.btnClose'/>" onClick="window.close()">
</form>
</body>
</html>
