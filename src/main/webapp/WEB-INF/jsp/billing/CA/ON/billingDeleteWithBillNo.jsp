<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
    Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.

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
  Purpose: Supports billingDeleteWithBillNo in the Ontario billing workflow.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<%--
    billingDeleteWithBillNo.jsp (view) - Ontario billing deletion result (known bill number).
    Displays success and closes the popup.
    Rendered by BillingDeleteWithBillNo2Action.
    @since 2026
--%>
<%@ page contentType="text/html;charset=UTF-8" %>
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
            <th align="CENTER"><font face="Helvetica" color="#FFFFFF">BILLING RECORD</font></th>
        </tr>
    </table>
    <p>
    <h1>Successful deletion of a billing record.</h1>

    <script type="text/javascript">
        self.close();
        try {
            if (self.opener && self.opener.refresh) {
                self.opener.refresh();
            } else {
                new BroadcastChannel('carlos_schedule_refresh').postMessage('refresh');
            }
        } catch(e) {
            new BroadcastChannel('carlos_schedule_refresh').postMessage('refresh');
        }
    </script>
    <p></p>
    <hr width="90%">
    <form><input type="button" value="Close this window" onClick="window.close()"></form>
</center>
</body>
</html>
