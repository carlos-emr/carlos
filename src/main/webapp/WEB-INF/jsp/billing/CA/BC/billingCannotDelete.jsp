<%--
    Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
    This software is published under the GPL GNU General Public License.

    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.
--%>
<%--
    billingCannotDelete.jsp (view) - BC billing cannot-delete response.
    Rendered by BillingDeleteWithoutNo2Action and BillingDeleteNoAppt2Action (BC) when
    the billing record has already been submitted to Teleplan/MSP.
    @since 2026
--%>
<%@ page contentType="text/html;charset=UTF-8" %>
<!DOCTYPE html>
<html>
<head>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
</head>
<body>
<center>
    <table border="0" cellspacing="0" cellpadding="0" width="90%">
        <tr bgcolor="#486ebd">
            <th align="CENTER"><font face="Helvetica" color="#FFFFFF">DELETE A BILLING RECORD</font></th>
        </tr>
    </table>
    <p>
    <h1><%= request.getAttribute("cannotDelete") != null
            ? "Sorry, cannot delete billed items."
            : "Unable to delete billing record." %></h1>

    <form><input type="button" value="Back to previous page" onClick="window.close()"></form>
</center>
</body>
</html>
