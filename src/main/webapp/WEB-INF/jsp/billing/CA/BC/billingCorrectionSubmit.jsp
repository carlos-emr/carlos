<%--
    Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
    This software is published under the GPL GNU General Public License.

    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.
--%>
<%--
    billingCorrectionSubmit.jsp (view) - BC billing correction result page.
    Displayed after BillingCorrectionSubmit2Action (BC) completes the correction.
    @since 2026
--%>
<!DOCTYPE html>
<html>
<head>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
</head>
<body>
<table border="0" cellspacing="0" cellpadding="0" width="100%">
    <tr bgcolor="#486ebd">
        <th align="CENTER" nowrap><font face="Helvetica" color="#FFFFFF">Billing Correction Successfully Done</font></th>
    </tr>
</table>

<form action="${pageContext.request.contextPath}/billing/CA/BC/billingCorrection.jsp">
    <input type="hidden" name="billing_no" value="">
    <input type="submit" value="Correct Another One" name="submit">
    <input type="button" value="Successful - Close this window" onClick="window.close()">
</form>
</body>
</html>
