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
  Purpose: Supports billingONNewReport in the Ontario billing workflow.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<!DOCTYPE html>
<%@page import="io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOnNewReportViewModelAssembler" %>
<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>

<%-- Data assembly runs in ViewBillingOnNewReport2Action via
     BillingOnNewReportViewModelAssembler. The view model is stashed on the request
     as ${model}; this JSP only renders. The four inline JDBC queries (unbilled
     / billed / paid / unpaid) and the multisite + provider dropdown lookups
     the legacy JSP performed inline now live in the assembler. --%>
<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>
    <title>Ontario Billing Report</title>

    <link href="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.11/css/dataTables.bootstrap5.min.css" rel="stylesheet">

    <script src="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.11/js/jquery.dataTables.min.js"></script>
    <script src="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.11/js/dataTables.bootstrap5.min.js"></script>

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

        function calToday(field) {
            var calDate = new Date();
            var varMonth = calDate.getMonth() + 1;
            varMonth = varMonth > 9 ? varMonth : ("0" + varMonth);
            var varDate = calDate.getDate() > 9 ? calDate.getDate() : ("0" + calDate.getDate());
            field.value = calDate.getFullYear() + '/' + varMonth + '/' + varDate;
        }

        // Multisite provider lookup: maps clinic name -> array of {value, text}.
        var _providers = {};
        <c:if test="${model.multisitesEnabled}">
            <c:forEach var="site" items="${model.siteOptions}">
                _providers["<carlos:encode value='${site.name}' context='javaScript'/>"] = [
                    <c:forEach var="prov" items="${site.providers}">
                    {value: '<carlos:encode value="${prov.providerNo}" context="javaScript"/>', text: '<carlos:encode value="${prov.displayName}" context="javaScript"/>'},
                    </c:forEach>
                ];
            </c:forEach>
        </c:if>

        function changeSite(sel) {
            var provSelect = sel.form.providerview;
            provSelect.length = 0;
            if (sel.value !== "none") {
                var providers = _providers[sel.value];
                for (var i = 0; i < providers.length; i++) {
                    var opt = document.createElement('option');
                    opt.value = providers[i].value;
                    opt.textContent = providers[i].text;
                    provSelect.add(opt);
                }
            }
            sel.style.backgroundColor = sel.options[sel.selectedIndex].style.backgroundColor;
        }
    </script>

    <style type="text/css" media="print">
        .searchBox { display: none; }
    </style>
</head>

<body>
<div class="container">
<div class="searchBox">

    <div class="page-header-bar">
        <h4 class="page-header-title">
            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="currentColor" viewBox="0 0 16 16" class="page-header-icon">
                <path d="M1.92.506a.5.5 0 0 1 .434.14L3 1.293l.646-.647a.5.5 0 0 1 .708 0L5 1.293l.646-.647a.5.5 0 0 1 .708 0L7 1.293l.646-.647a.5.5 0 0 1 .708 0L9 1.293l.646-.647a.5.5 0 0 1 .708 0l.646.647.646-.647a.5.5 0 0 1 .708 0l.646.647.646-.647a.5.5 0 0 1 .801.13l.5 1A.5.5 0 0 1 15 2v12a.5.5 0 0 1-.053.224l-.5 1a.5.5 0 0 1-.8.13L13 14.707l-.646.647a.5.5 0 0 1-.708 0L11 14.707l-.646.647a.5.5 0 0 1-.708 0L9 14.707l-.646.647a.5.5 0 0 1-.708 0L7 14.707l-.646.647a.5.5 0 0 1-.708 0L5 14.707l-.646.647a.5.5 0 0 1-.708 0L3 14.707l-.646.647a.5.5 0 0 1-.801-.13l-.5-1A.5.5 0 0 1 1 14V2a.5.5 0 0 1 .053-.224l.5-1a.5.5 0 0 1 .367-.27m.217 1.338L2 2.118v11.764l.137.274.51-.51a.5.5 0 0 1 .707 0l.646.647.646-.646a.5.5 0 0 1 .708 0l.646.646.646-.646a.5.5 0 0 1 .708 0l.646.646.646-.646a.5.5 0 0 1 .708 0l.646.646.646-.646a.5.5 0 0 1 .708 0l.646.646.646-.646a.5.5 0 0 1 .708 0l.509.509.137-.274V2.118l-.137-.274-.51.51a.5.5 0 0 1-.707 0L12 1.707l-.646.647a.5.5 0 0 1-.708 0L10 1.707l-.646.647a.5.5 0 0 1-.708 0L8 1.707l-.646.647a.5.5 0 0 1-.708 0L6 1.707l-.646.647a.5.5 0 0 1-.708 0L4 1.707l-.646.647a.5.5 0 0 1-.708 0l-.509-.51zM3 4.5a.5.5 0 0 1 .5-.5h6a.5.5 0 1 1 0 1h-6a.5.5 0 0 1-.5-.5m0 2a.5.5 0 0 1 .5-.5h6a.5.5 0 1 1 0 1h-6a.5.5 0 0 1-.5-.5m0 2a.5.5 0 0 1 .5-.5h6a.5.5 0 1 1 0 1h-6a.5.5 0 0 1-.5-.5m0 2a.5.5 0 0 1 .5-.5h6a.5.5 0 0 1 0 1h-6a.5.5 0 0 1-.5-.5m8-6a.5.5 0 0 1 .5-.5h1a.5.5 0 0 1 0 1h-1a.5.5 0 0 1-.5-.5m0 2a.5.5 0 0 1 .5-.5h1a.5.5 0 0 1 0 1h-1a.5.5 0 0 1-.5-.5m0 2a.5.5 0 0 1 .5-.5h1a.5.5 0 0 1 0 1h-1a.5.5 0 0 1-.5-.5m0 2a.5.5 0 0 1 .5-.5h1a.5.5 0 0 1 0 1h-1a.5.5 0 0 1-.5-.5"/>
            </svg>
            &nbsp;Ontario Billing Report
        </h4>
    </div>

    <form name="serviceform" method="post" action="${pageContext.request.contextPath}/billing/CA/ON/ViewBillingONReport">
        <div class="d-flex flex-wrap align-items-center gap-2" style="margin-bottom:10px;">
            <div class="form-check form-check-inline">
                <input class="form-check-input" type="radio" name="reportAction" value="unbilled" ${model.reportAction == 'unbilled' ? 'checked' : ''}>
                <label class="form-check-label">Unbilled</label>
            </div>
            <div class="form-check form-check-inline">
                <input class="form-check-input" type="radio" name="reportAction" value="billed" ${model.reportAction == 'billed' ? 'checked' : ''}>
                <label class="form-check-label">Billed</label>
            </div>

            &nbsp;&nbsp;Provider
            <c:choose>
                <c:when test="${model.multisitesEnabled}">
                    <select id="site" name="site" class="form-select form-select-sm" style="width:auto; display:inline-block;" onchange="changeSite(this)">
                        <option value="none" style="background-color:white">---select clinic---</option>
                        <c:forEach var="site" items="${model.siteOptions}">
                            <option value="<carlos:encode value='${site.name}' context='htmlAttribute'/>"
                                    style="background-color:<carlos:encode value='${site.bgColor}' context='cssString'/>"
                                    ${site.name == model.selectedSite ? 'selected' : ''}>
                                <carlos:encode value='${site.name}' context='html'/>
                            </option>
                        </c:forEach>
                    </select>
                    <select id="providerview" name="providerview" class="form-select form-select-sm" style="width:auto; display:inline-block;"></select>
                    <c:if test="${not empty model.providerView and model.providerView != 'all'}">
                        <script>
                            changeSite(document.getElementById("site"));
                            document.getElementById("providerview").value = '<carlos:encode value="${model.providerView}" context="javaScript"/>';
                        </script>
                    </c:if>
                </c:when>
                <c:otherwise>
                    <select name="providerview" class="form-select form-select-sm" style="width:auto; display:inline-block;">
                        <c:forEach var="opt" items="${model.providerOptions}">
                            <option value="<carlos:encode value='${opt.providerNo}' context='htmlAttribute'/>"
                                    ${opt.providerNo == model.providerView ? 'selected' : ''}>
                                <carlos:encode value='${opt.lastName}' context='html'/>, <carlos:encode value='${opt.firstName}' context='html'/>
                            </option>
                        </c:forEach>
                    </select>
                </c:otherwise>
            </c:choose>

            <label style="margin-left:10px;">From:
                <input type="date" name="xml_vdate" id="xml_vdate" class="form-select form-select-sm" style="width:auto; display:inline-block;" value="<carlos:encode value='${model.xmlVdate}' context='htmlAttribute'/>">
            </label>
            <label>To:
                <input type="date" name="xml_appointment_date" id="xml_appointment_date" class="form-select form-select-sm" style="width:auto; display:inline-block;" value="<carlos:encode value='${model.xmlAppointmentDate}' context='htmlAttribute'/>">
            </label>

            <input type="submit" name="Submit" class="btn btn-sm btn-primary" value="Create Report">
        </div>
        <a href="#" onClick="popupPage(700,720,'${pageContext.request.contextPath}/oscarReport/ViewManageProvider?action=billingreport'); return false;" class="btn btn-sm btn-secondary">Manage Provider List</a>
    </form>

    <table id="reportTbl" class="table table-sm table-striped table-hover" style="margin-top:10px;">
        <thead>
        <tr>
            <c:forEach var="header" items="${model.columnHeaders}">
                <th><carlos:encode value="${header}" context="html"/></th>
            </c:forEach>
        </tr>
        </thead>
        <tbody>
        <c:forEach var="row" items="${model.rows}">
            <tr>
                <c:forEach var="header" items="${model.columnHeaders}">
                    <c:set var="cell" value="${row.cells[header]}"/>
                    <td>
                        <c:choose>
                            <c:when test="${not empty cell and not empty cell.popupUrl}">
                                <a href="#"
                                   onclick="popupPage(${cell.popupHeight},${cell.popupWidth}, '<carlos:encode value="${cell.popupUrl}" context="javaScriptAttribute"/>'); return false;"
                                   title="<carlos:encode value="${cell.title}" context="htmlAttribute"/>">
                                    <carlos:encode value="${cell.text}" context="html"/>
                                </a>
                            </c:when>
                            <c:when test="${not empty cell and not empty cell.text}">
                                <carlos:encode value="${cell.text}" context="html"/>
                            </c:when>
                            <c:otherwise>&nbsp;</c:otherwise>
                        </c:choose>
                    </td>
                </c:forEach>
            </tr>
        </c:forEach>

        <c:if test="${not empty model.totalRow}">
            <tr>
                <c:forEach var="cell" items="${model.totalRow}">
                    <th><carlos:encode value="${cell}" context="html"/></th>
                </c:forEach>
            </tr>
        </c:if>
        </tbody>
    </table>


</div>
</div>

<script>
    $('#reportTbl').DataTable({
        "order": [],
        "language": {
            "url": "${pageContext.request.contextPath}/library/DataTables/i18n/<fmt:message key="global.i18n.datatablescode"/>.json"
        }
    });
</script>

</body>
</html>
