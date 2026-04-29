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
  Page role: Renders `billingDigUpdate.jsp` for the Ontario billing workflow.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<%@page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    // BillingDiagUpdate2Action enforces _billing w + POST. The assembler
    // already ran the DiagnosticCodeDao.merge mutation; this JSP just
    // shows the success/error banner. Defensive fallback: if a caller forwards
    // here without going through the action, show the error banner — never
    // re-trigger the mutation.
    %>
<html>
<head>
    <script type="text/javascript" src="${pageContext.request.contextPath}/js/global.js"></script>
    <script LANGUAGE="JavaScript">
        <!--
        function start() {
            this.focus();
        }

        //-->
    </script>

</head>
<body onload="start()">

<center>
    <table border="0" cellspacing="0" cellpadding="0" width="90%">
        <tr bgcolor="#486ebd">
            <th align="CENTER"><font face="Helvetica" color="#FFFFFF">
                ADD A BILLING RECORD</font></th>
        </tr>
    </table>

    <c:choose>
        <c:when test="${not digUpdateModel.error}">
            <p>
            <h1>Successful Addition of a billing Record.</h1></p>
        </c:when>
        <c:otherwise>
            <p>
            <h1>Sorry, addition has failed.</h1></p>
        </c:otherwise>
    </c:choose>
    <p></p>
    <hr width="90%"></hr>
    <form><input type="button" value="Close this window"
                 onClick="window.close()"></form>
</center>
</body>
</html>
