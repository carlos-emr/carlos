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
    dbUpdateINRbilling.jsp (WEB-INF view)

    View fragment for InrBillingRecordUpdate2Action.
    Renders success/failure confirmation and closes the popup.

    Request Attributes (set by InrBillingRecordUpdate2Action):
    - errorCode (String): validation error messages (empty = no error)
    - inraction (String): the action performed ("update" or "delete")
    - billinginr_no (String): the billing record number

    @since 2026-04-05
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <script language="JavaScript">
        function start() { this.focus(); }
    </script>
</head>
<body onload="start()">
<center>
    <table border="0" cellspacing="0" cellpadding="0" width="90%">
        <tr bgcolor="#486ebd">
            <th align="CENTER"><font face="Helvetica" color="#FFFFFF">UPDATE A BILLING RECORD</font></th>
        </tr>
    </table>
    <%
        String errorCode = (String) request.getAttribute("errorCode");
        String inraction = (String) request.getAttribute("inraction");
        String billinginr_no = (String) request.getAttribute("billinginr_no");
        if (errorCode == null) errorCode = "";
        if (inraction == null) inraction = "";
        if (billinginr_no == null) billinginr_no = "";

        if (errorCode.isEmpty()) {
    %>
    <script language="JavaScript">
        self.close();
        self.opener.refresh();
    </script>
    <%
        } else {
    %>
    <div style="white-space: pre-line"><carlos:encode value='<%= errorCode %>' context="html"/></div>
    <input type="button" value="Change" onClick="history.go(-1);return false;">
    <%
        }
    %>
    <p><carlos:encode value='<%= inraction %>' context="html"/> Bill number <carlos:encode value='<%= billinginr_no %>' context="html"/></p>
    <hr width="90%"/>
    <form><input type="button" value="Close this window" onClick="window.close()"></form>
</center>
</body>
</html>
