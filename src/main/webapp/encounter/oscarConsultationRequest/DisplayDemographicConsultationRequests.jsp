<%--

    Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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

    This software was written for the
    Department of Family Medicine
    McMaster University
    Hamilton
    Ontario, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>

<%--
  DisplayDemographicConsultationRequests.jsp - Patient consultation request list

  Purpose:
  Displays all consultation requests for a specific patient in a sortable,
  filterable DataTable. Allows opening existing requests and creating new ones.

  Parameters:
  - de (required): Demographic number identifying the patient

  Security:
  - Requires _con read rights

  @since 2002-11-08
--%>

<!DOCTYPE html>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_con" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_con");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ page import="io.github.carlos_emr.carlos.managers.DemographicManager" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.data.*"%>
<%@ page import="io.github.carlos_emr.carlos.encounter.pageUtil.*"%>
<%@ page import="io.github.carlos_emr.carlos.providers.data.ProviderData" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ConsultationRequestExtDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.EctConsultationFormRequestUtil" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.EctViewConsultationRequestsUtil" %>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>

<%
    String demo = request.getParameter("de");
    String proNo = (String) session.getAttribute("user");
    DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
    Demographic demographic = null;

    ProviderData pdata = new ProviderData(proNo);
    String team = pdata.getTeam();

    if (demo != null) {
        demographic = demographicManager.getDemographic(LoggedInInfo.getLoggedInInfoFromSession(request), demo);
    } else
        request.getRequestDispatcher("/errorpage.jsp").forward(request, response);

    EctConsultationFormRequestUtil consultUtil;
    consultUtil = new EctConsultationFormRequestUtil();
    consultUtil.estPatient(LoggedInInfo.getLoggedInInfoFromSession(request), demo);

    EctViewConsultationRequestsUtil theRequests;
    theRequests = new EctViewConsultationRequestsUtil();
    theRequests.estConsultationVecByDemographic(LoggedInInfo.getLoggedInInfoFromSession(request), demo);
%>

<%-- Set the resource bundle once for the entire page --%>
<fmt:setBundle basename="oscarResources" scope="page"/>

<html>
<head>
    <title><fmt:message key="encounter.oscarConsultationRequest.DisplayDemographicConsultationRequests.title"/></title>
    <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">

    <%@ include file="/includes/global-head.jspf" %>
    <link href="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.4/css/dataTables.bootstrap5.min.css" rel="stylesheet">
    <script src="${pageContext.request.contextPath}/library/DataTables/datatables.min.js"></script>

    <style>
        /* DataTables — CARLOS design overrides (matching ticklerMain.jsp pattern) */
        table.dataTable thead th {
            background-color: #f5f5f5 !important;
            border-bottom: 2px solid #337ab7 !important;
            color: #333;
            font-weight: 600;
            white-space: nowrap;
        }
        .dataTables_wrapper .dataTables_paginate .paginate_button.current,
        .dataTables_wrapper .dataTables_paginate .paginate_button.current:hover {
            background: #337ab7 !important;
            color: white !important;
            border-color: #337ab7 !important;
        }
        .page-item.active .page-link {
            background-color: #337ab7 !important;
            border-color: #337ab7 !important;
        }
        .page-link {
            color: #337ab7;
        }
        .dataTables_wrapper .dataTables_info,
        .dataTables_wrapper .dataTables_paginate {
            font-size: 13px;
        }
        .dataTables_wrapper .dataTables_length { display: none; }

        /* Consultation status colors — matches status codes from EctViewConsultationRequestsUtil:
           1=Nothing Done, 2=Specialist Called, 3=Patient Called, 4=Appointment Made, 5=Booked */
        .stat1 { color: red; }
        .stat2 { color: orange; }
        .stat3 { color: green; }
        .stat4 { color: #337ab7; }
        .stat5 { color: purple; }
    </style>

    <script type="text/javascript">
        jQuery(document).ready(function () {
            jQuery('#consultTable').DataTable({
                "order": [[7, 'desc']], // Column 7 = Referral Date (newest first)
                "language": {
                    "url": "<%=request.getContextPath() %>/library/DataTables/i18n/<fmt:message key="global.i18nLanguagecode"/>.json"
                }
            });
        });

        /**
         * Opens a consultation request in a popup window. When this window regains
         * focus (user closes the popup or switches back), the page reloads to
         * reflect any status changes made in the popup.
         */
        var consultPopupOpen = false;
        function popupConsultation(vheight, vwidth, varpage) {
            var windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
            var popup = window.open(varpage, "consultPopup", windowprops);
            // Only flag reload if popup was successfully opened (not blocked by browser)
            if (popup) {
                consultPopupOpen = true;
            }
        }

        // Reload the list when this window regains focus after a popup was opened
        window.addEventListener('focus', function() {
            if (consultPopupOpen) {
                consultPopupOpen = false;
                window.location.reload();
            }
        });

        /**
         * Navigates back: reloads the opener if in a popup, otherwise uses browser history.
         */
        function goBack() {
            try {
                if (window.opener && !window.opener.closed) {
                    window.opener.location.reload();
                    window.close();
                } else if (window.history.length > 1) {
                    window.history.back();
                } else {
                    window.close();
                }
            } catch (e) {
                window.history.back();
            }
        }
    </script>
</head>

<body onload="window.focus()">
<div class="container-fluid" style="padding:0 15px;">

    <%-- Page header matching search.jsp / consultation form pattern --%>
    <div class="page-header-bar d-flex align-items-center justify-content-between">
        <h4 class="page-header-title">
            <i class="fa-solid fa-stethoscope page-header-icon"></i>
            &nbsp;<fmt:message key="encounter.oscarConsultationRequest.DisplayDemographicConsultationRequests.msgConsReqFor"/>
            <%= Encode.forHtml(demographic.getLastName()) %>, <%= Encode.forHtml(demographic.getFirstName()) %>
            <%= Encode.forHtml(demographic.getSex()) %> <%= Encode.forHtml(demographic.getAge()) %>
        </h4>
        <div>
            <%
                String newConsultUrl = request.getContextPath()
                    + "/encounter/oscarConsultationRequest/ConsultationFormRequest.jsp"
                    + "?de=" + Encode.forUriComponent(demo)
                    + "&teamVar=" + Encode.forUriComponent(team);
            %>
            <a class="btn btn-primary btn-sm"
               href="javascript:popupConsultation(700,960,'<%= Encode.forJavaScriptAttribute(newConsultUrl) %>')">
                <i class="fa-solid fa-plus me-1"></i><fmt:message key="encounter.oscarConsultationRequest.ConsultChoice.btnNewCon"/>
            </a>
            <input type="button" class="btn btn-secondary btn-sm"
                   value="<fmt:message key="global.btnBack"/>"
                   onclick="goBack()">
        </div>
    </div>

    <p class="text-muted" style="margin:5px 0 10px;">
        <fmt:message key="encounter.oscarConsultationRequest.DisplayDemographicConsultationRequests.msgClickLink"/>
    </p>

    <table id="consultTable" class="table table-sm table-striped table-hover" style="width:100%;">
        <thead>
            <tr>
                <th><fmt:message key="encounter.oscarConsultationRequest.DisplayDemographicConsultationRequests.msgStatus"/></th>
                <th><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.formUrgency"/></th>
                <th><fmt:message key="encounter.oscarConsultationRequest.DisplayDemographicConsultationRequests.msgPat"/></th>
                <th><fmt:message key="encounter.oscarConsultationRequest.DisplayDemographicConsultationRequests.msgMRP"/></th>
                <th><fmt:message key="encounter.oscarConsultationRequest.DisplayDemographicConsultationRequests.msgProvider"/></th>
                <th><fmt:message key="encounter.oscarConsultationRequest.DisplayDemographicConsultationRequests.msgService"/></th>
                <th><fmt:message key="encounter.oscarConsultationRequest.DisplayDemographicConsultationRequests.msgSpecialist"/></th>
                <th><fmt:message key="encounter.oscarConsultationRequest.DisplayDemographicConsultationRequests.msgRefDate"/></th>
            </tr>
        </thead>
        <tbody>
            <%
                for (int i = 0; i < theRequests.ids.size(); i++) {
                    String id       = (String) theRequests.ids.get(i);
                    String status   = (String) theRequests.status.get(i);
                    String patient  = (String) theRequests.patient.get(i);
                    String provider = (String) theRequests.provider.get(i);
                    String service  = (String) theRequests.service.get(i);
                    String specialist = (String) theRequests.vSpecialist.get(i);
                    String date     = (String) theRequests.date.get(i);
                    String urgency  = (String) theRequests.urgency.get(i);
                    Provider cProv  = (Provider) theRequests.consultProvider.get(i);

                    // Determine i18n key for status
                    String statusKey = "";
                    switch (status != null ? status : "") {
                        case "1": statusKey = "encounter.oscarConsultationRequest.DisplayDemographicConsultationRequests.msgNothingDone"; break;
                        case "2": statusKey = "encounter.oscarConsultationRequest.DisplayDemographicConsultationRequests.msgSpecialistCall"; break;
                        case "3": statusKey = "encounter.oscarConsultationRequest.DisplayDemographicConsultationRequests.msgPatCall"; break;
                        case "4": statusKey = "encounter.oscarConsultationRequest.DisplayDemographicConsultationRequests.msgAppMade"; break;
                        case "5": statusKey = "encounter.oscarConsultationRequest.DisplayDemographicConsultationRequests.msgBookCon"; break;
                    }

                    // Determine i18n key for urgency (null-safe)
                    String urgencyKey = "";
                    switch (urgency != null ? urgency : "") {
                        case "1": urgencyKey = "encounter.oscarConsultationRequest.ConsultationFormRequest.msgUrgent"; break;
                        case "2": urgencyKey = "encounter.oscarConsultationRequest.ConsultationFormRequest.msgNUrgent"; break;
                        case "3": urgencyKey = "encounter.oscarConsultationRequest.ConsultationFormRequest.msgReturn"; break;
                    }
                    // Prebuild view URL for JS embedding (JS-attribute-encoded to prevent XSS)
                    String viewRequestUrl = request.getContextPath()
                        + "/encounter/ViewRequest.do"
                        + "?de=" + Encode.forUriComponent(demo)
                        + "&requestId=" + Encode.forUriComponent(id);
            %>
            <tr>
                <td class="stat<%=Encode.forHtmlAttribute(status)%>">
                    <% if (!statusKey.isEmpty()) { %>
                        <fmt:message key="<%= statusKey %>"/>
                    <% } %>
                </td>
                <td class="stat<%=Encode.forHtmlAttribute(status)%>">
                    <% if (!urgencyKey.isEmpty()) { %>
                        <fmt:message key="<%= urgencyKey %>"/>
                    <% } %>
                </td>
                <td class="stat<%=Encode.forHtmlAttribute(status)%>">
                    <a href="javascript:popupConsultation(700,960,'<%= Encode.forJavaScriptAttribute(viewRequestUrl) %>')">
                        <%=Encode.forHtml(patient)%>
                    </a>
                </td>
                <td class="stat<%=Encode.forHtmlAttribute(status)%>"><%=Encode.forHtml(provider)%></td>
                <td class="stat<%=Encode.forHtmlAttribute(status)%>"><%= (cProv != null) ? Encode.forHtml(cProv.getFormattedName()) : "" %></td>
                <td class="stat<%=Encode.forHtmlAttribute(status)%>">
                    <a href="javascript:popupConsultation(700,960,'<%= Encode.forJavaScriptAttribute(viewRequestUrl) %>')">
                        <%=Encode.forHtml(StringUtils.trimToEmpty(service))%>
                    </a>
                </td>
                <td class="stat<%=Encode.forHtmlAttribute(status)%>"><%= Encode.forHtml(StringUtils.trimToEmpty(specialist)) %></td>
                <td class="stat<%=Encode.forHtmlAttribute(status)%>"><%=Encode.forHtml(date)%></td>
            </tr>
            <%}%>
        </tbody>
    </table>

</div>
</body>
</html>
