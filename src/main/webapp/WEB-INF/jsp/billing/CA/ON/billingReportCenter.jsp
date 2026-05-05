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
  Purpose: Supports billingReportCenter in the Ontario billing workflow.
  Expected request model data includes: reportCenterModel.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<%--
    billingReportCenter.jsp (view) - Ontario billing report center landing.
    Rendered by ViewBillingReportCenter2Action which:
      - enforces _report r privilege
      - redirects admin/doctor roles to the new-report dashboard
      - resolves the provider-list select rows + the three echoed
        parameters into ${reportCenterModel}.
    Pure presentation here — no DAO lookups inline.
    @since 2006
--%>
<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>

<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Billing Report Center</title>

    <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet" type="text/css">
    <link rel="stylesheet" type="text/css" media="all" href="${pageContext.request.contextPath}/share/css/searchBox.css">

    <script src="${pageContext.request.contextPath}/js/global.js"></script>

    <script>
        function selectprovider(s) {
            var a;
            if (self.location.href.lastIndexOf("&providerview=") > 0) a = self.location.href.substring(0, self.location.href.lastIndexOf("&providerview="));
            else a = self.location.href;
            self.location.href = a + "&providerview=" + s.options[s.selectedIndex].value;
        }

        function openBrWindow(theURL, winName, features) {
            window.open(theURL, winName, features);
        }

        function refresh() {
            var u = self.location.href;
            if (u.lastIndexOf("view=1") > 0) {
                var idx = u.lastIndexOf("view=1");
                self.location.href = u.substring(0, idx) + "view=0" + u.substring(idx + 6);
            } else {
                history.go(0);
            }
        }
    </script>

    <style type="text/css" media="print">
        .searchBox { display: none; }
    </style>
</head>

<body>
<div class="container">
<div class="searchBox">

    <div style="background:#f5f5f5; padding:8px 15px; border-bottom:1px solid #ddd; margin-bottom:10px;">
        <h4 style="margin:0; font-size:18px; display:inline-block;">
            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="currentColor" viewBox="0 0 16 16" style="vertical-align:text-bottom">
                <path d="M1.92.506a.5.5 0 0 1 .434.14L3 1.293l.646-.647a.5.5 0 0 1 .708 0L5 1.293l.646-.647a.5.5 0 0 1 .708 0L7 1.293l.646-.647a.5.5 0 0 1 .708 0L9 1.293l.646-.647a.5.5 0 0 1 .708 0l.646.647.646-.647a.5.5 0 0 1 .708 0l.646.647.646-.647a.5.5 0 0 1 .801.13l.5 1A.5.5 0 0 1 15 2v12a.5.5 0 0 1-.053.224l-.5 1a.5.5 0 0 1-.8.13L13 14.707l-.646.647a.5.5 0 0 1-.708 0L11 14.707l-.646.647a.5.5 0 0 1-.708 0L9 14.707l-.646.647a.5.5 0 0 1-.708 0L7 14.707l-.646.647a.5.5 0 0 1-.708 0L5 14.707l-.646.647a.5.5 0 0 1-.708 0L3 14.707l-.646.647a.5.5 0 0 1-.801-.13l-.5-1A.5.5 0 0 1 1 14V2a.5.5 0 0 1 .053-.224l.5-1a.5.5 0 0 1 .367-.27m.217 1.338L2 2.118v11.764l.137.274.51-.51a.5.5 0 0 1 .707 0l.646.647.646-.646a.5.5 0 0 1 .708 0l.646.646.646-.646a.5.5 0 0 1 .708 0l.646.646.646-.646a.5.5 0 0 1 .708 0l.646.646.646-.646a.5.5 0 0 1 .708 0l.646.646.646-.646a.5.5 0 0 1 .708 0l.509.509.137-.274V2.118l-.137-.274-.51.51a.5.5 0 0 1-.707 0L12 1.707l-.646.647a.5.5 0 0 1-.708 0L10 1.707l-.646.647a.5.5 0 0 1-.708 0L8 1.707l-.646.647a.5.5 0 0 1-.708 0L6 1.707l-.646.647a.5.5 0 0 1-.708 0L4 1.707l-.646.647a.5.5 0 0 1-.708 0l-.509-.51zM3 4.5a.5.5 0 0 1 .5-.5h6a.5.5 0 1 1 0 1h-6a.5.5 0 0 1-.5-.5m0 2a.5.5 0 0 1 .5-.5h6a.5.5 0 1 1 0 1h-6a.5.5 0 0 1-.5-.5m0 2a.5.5 0 0 1 .5-.5h6a.5.5 0 1 1 0 1h-6a.5.5 0 0 1-.5-.5m0 2a.5.5 0 0 1 .5-.5h6a.5.5 0 0 1 0 1h-6a.5.5 0 0 1-.5-.5m8-6a.5.5 0 0 1 .5-.5h1a.5.5 0 0 1 0 1h-1a.5.5 0 0 1-.5-.5m0 2a.5.5 0 0 1 .5-.5h1a.5.5 0 0 1 0 1h-1a.5.5 0 0 1-.5-.5m0 2a.5.5 0 0 1 .5-.5h1a.5.5 0 0 1 0 1h-1a.5.5 0 0 1-.5-.5m0 2a.5.5 0 0 1 .5-.5h1a.5.5 0 0 1 0 1h-1a.5.5 0 0 1-.5-.5"/>
            </svg>
            &nbsp;Billing Report Center
        </h4>
        <span style="float:right;">
            <a href="#" onClick="popupPage(700,720,'${pageContext.request.contextPath}/oscarReport/ViewManageProvider?action=billingreport'); return false;" class="btn btn-sm btn-secondary">Manage Provider List</a>
        </span>
    </div>

    <form name="serviceform" method="post" action="${pageContext.request.contextPath}/billing/CA/ON/ViewBillingReportControl">
        <div class="d-flex flex-wrap align-items-center gap-2" style="margin-bottom:10px;">
            <div class="form-check form-check-inline">
                <input class="form-check-input" type="radio" name="reportAction" value="unbilled" checked>
                <label class="form-check-label">Unbilled</label>
            </div>
            <div class="form-check form-check-inline">
                <input class="form-check-input" type="radio" name="reportAction" value="billed">
                <label class="form-check-label">Billed</label>
            </div>
            <div class="form-check form-check-inline">
                <input class="form-check-input" type="radio" name="reportAction" value="unsettled">
                <label class="form-check-label">Unsettled</label>
            </div>
            <div class="form-check form-check-inline">
                <input class="form-check-input" type="radio" name="reportAction" value="billob">
                <label class="form-check-label">OB</label>
            </div>
            <div class="form-check form-check-inline">
                <input class="form-check-input" type="radio" name="reportAction" value="flu">
                <label class="form-check-label">FLU</label>
            </div>

            &nbsp;&nbsp;Provider
            <select name="providerview" class="form-select form-select-sm" style="width:auto; display:inline-block;">
                <c:forEach var="row" items="${reportCenterModel.providerRows}">
                    <option value="<carlos:encode value='${row.ohip}' context='htmlAttribute'/>"
                            <c:if test="${reportCenterModel.selectedProviderView eq row.ohip}">selected</c:if>>
                        <carlos:encode value="${row.displayName}" context="html"/>
                    </option>
                </c:forEach>
            </select>

            <input type="hidden" name="verCode" value="V03">

            <label style="margin-left:10px;">From:
                <input type="date" name="xml_vdate" class="form-control form-control-sm" style="width:auto; display:inline-block;" value="<carlos:encode value='${reportCenterModel.xmlVdate}' context='htmlAttribute'/>">
            </label>
            <label>To:
                <input type="date" name="xml_appointment_date" class="form-control form-control-sm" style="width:auto; display:inline-block;" value="<carlos:encode value='${reportCenterModel.xmlAppointmentDate}' context='htmlAttribute'/>">
            </label>

            <input type="submit" name="Submit" class="btn btn-sm btn-primary" value="Create Report">
        </div>
    </form>


</div>
</div>

</body>
</html>
