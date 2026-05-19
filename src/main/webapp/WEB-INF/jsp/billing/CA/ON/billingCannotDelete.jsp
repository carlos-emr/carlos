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
  Purpose: Supports billingCannotDelete in the Ontario billing workflow.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<%--
    billingCannotDelete.jsp (view) - Ontario billing cannot-delete response.
    Rendered by BillingDeleteWithoutNo2Action and BillingDeleteNoAppt2Action when
    the billing record has already been submitted to the insurer.
    @since 2026
--%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<!DOCTYPE html>
<html>
<head>
    <script type="text/javascript" src="${pageContext.request.contextPath}/js/global.js"></script>
</head>
<body>
<center>
    <table border="0" cellspacing="0" cellpadding="0" width="90%">
        <tr bgcolor="#486ebd">
            <th align="CENTER"><font face="Helvetica" color="#FFFFFF">DELETE A BILLING RECORD</font></th>
        </tr>
    </table>
    <p>
    <h1><c:choose>
        <c:when test="${not empty cannotDelete}">Sorry, cannot delete billed items.</c:when>
        <c:otherwise>Unable to delete billing record.</c:otherwise>
    </c:choose></h1>

    <form><input type="button" value="Back to previous page" onClick="window.close()"></form>
</center>
</body>
</html>
