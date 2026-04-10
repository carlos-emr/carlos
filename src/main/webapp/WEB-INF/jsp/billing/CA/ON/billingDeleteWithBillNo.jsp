<%--
    Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
    This software is published under the GPL GNU General Public License.

    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.
--%>
<%--
    billingDeleteWithBillNo.jsp (view) - Ontario billing deletion result (known bill number).
    Displays success and closes the popup.
    Rendered by BillingDeleteWithBillNo2Action.
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
