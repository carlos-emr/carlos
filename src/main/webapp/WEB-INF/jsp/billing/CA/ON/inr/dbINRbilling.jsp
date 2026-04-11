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
<%--
    dbINRbilling.jsp (WEB-INF view)

    View fragment for DbINRbilling2Action.
    Renders success/failure confirmation and closes the popup.

    Request Attributes (set by DbINRbilling2Action):
    - billSaved (Boolean): true if the billing record was saved successfully

    @since 2026-04-05
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="org.owasp.encoder.Encode" %>
<html>
<head>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <script language="JavaScript">
        function start() { this.focus(); }
    </script>
</head>
<body onload="start()">
<center>
    <table border="0" cellspacing="0" cellpadding="0" width="90%">
        <tr bgcolor="#486ebd">
            <th align="CENTER"><font face="Helvetica" color="#FFFFFF">ADD A BILLING RECORD</font></th>
        </tr>
    </table>
    <%
        Boolean billSaved = (Boolean) request.getAttribute("billSaved");
        if (Boolean.TRUE.equals(billSaved)) {
    %>
    <p><h1>Successful Addition of a billing Record.</h1></p>
    <script language="JavaScript">
        self.close();
        self.opener.refresh();
    </script>
    <%
        } else {
    %>
    <p><h1>Sorry, addition has failed.</h1></p>
    <%
        }
    %>
    <p></p>
    <hr width="90%"/>
    <form><input type="button" value="Close this window" onClick="window.close()"></form>
</center>
</body>
</html>
