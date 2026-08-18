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
  Purpose: Supports billingONSavePrintInvoice in the Ontario billing workflow.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<%--
    billingONSavePrintInvoice.jsp (view) - Ontario billing save + print invoice page.
    Opens the invoice popup, closes the billing window, and triggers a schedule refresh.
    Rendered by BillingOnSave2Action on "Save &amp; Print Invoice" or "Settle &amp; Print Invoice".
    @since 2026
--%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<!DOCTYPE html>
<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <script type="text/javascript" src="${pageContext.request.contextPath}/js/global.js"></script>
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

    popupPage(700, 720, '${pageContext.request.contextPath}/billing/CA/ON/ViewBillingON3rdInv?billingNo=${empty billingNo ? 0 : billingNo}');
    self.close();
    try { if (self.opener && self.opener.refresh) { self.opener.refresh(); } else { new BroadcastChannel('carlos_schedule_refresh').postMessage('refresh'); } } catch(e) { new BroadcastChannel('carlos_schedule_refresh').postMessage('refresh'); }
</script>
</body>
</html>
