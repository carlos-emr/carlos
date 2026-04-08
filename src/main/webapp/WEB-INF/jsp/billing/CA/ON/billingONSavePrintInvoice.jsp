<%--
    Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
    This software is published under the GPL GNU General Public License.

    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.
--%>
<%--
    billingONSavePrintInvoice.jsp (view) - Ontario billing save + print invoice page.
    Opens the invoice popup, closes the billing window, and triggers a schedule refresh.
    Rendered by BillingONSave2Action on "Save &amp; Print Invoice" or "Settle &amp; Print Invoice".
    @since 2026
--%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%
    Integer billingNo = (Integer) request.getAttribute("billingNo");
%>
<!DOCTYPE html>
<html>
<head>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
</head>
<body>
<script type="text/javascript">
    function popupPage(vheight, vwidth, varpage) {
        var page = "" + varpage;
        var windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
        var popup = window.open(page, "billcorrection", windowprops);
        if (popup != null) {
            if (popup.opener == null) {
                popup.opener = self;
            }
            popup.focus();
        }
    }

    popupPage(700, 720, '<%= request.getContextPath() %>/billing/CA/ON/billingON3rdInv.jsp?billingNo=<%= billingNo != null ? billingNo : 0 %>');
    self.close();
    try { if (self.opener && self.opener.refresh) { self.opener.refresh(); } else { new BroadcastChannel('carlos_schedule_refresh').postMessage('refresh'); } } catch(e) { new BroadcastChannel('carlos_schedule_refresh').postMessage('refresh'); }
</script>
</body>
</html>
