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


<!DOCTYPE html>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_demographic" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_demographic");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ page import="java.net.*" %>
<%@ page import="java.sql.*" %>
<%@ page import="java.util.*" %>

<%@ page import="io.github.carlos_emr.*" %>
<%@ page import="io.github.carlos_emr.carlos.commn.IsPropertiesOn" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.AppointmentStatusDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ProviderDataDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.SiteDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Appointment" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.AppointmentArchive" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.AppointmentStatus" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.LookupList" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.LookupListItem" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.ProviderData" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Site" %>
<%@ page import="io.github.carlos_emr.carlos.db.*"%>
<%@ page import="io.github.carlos_emr.carlos.managers.AppointmentManager" %>
<%@ page import="io.github.carlos_emr.carlos.managers.DemographicManager" %>
<%@ page import="io.github.carlos_emr.carlos.managers.LookupListManager" %>
<%@ page import="io.github.carlos_emr.carlos.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="org.springframework.beans.BeanUtils" %>
<%@ page import="org.springframework.web.context.WebApplicationContext" %>
<%@ page import="org.springframework.web.context.support.WebApplicationContextUtils" %>


<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ taglib uri="/WEB-INF/special_tag.tld" prefix="special" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>

<%!
    private List<Site> sites = new java.util.ArrayList<Site>();
    private HashMap<String, String[]> siteBgColor = new HashMap<String, String[]>();
%>

<%
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    OscarAppointmentDao appointmentDao = (OscarAppointmentDao) SpringUtils.getBean(OscarAppointmentDao.class);
    ProviderDataDao providerDao = SpringUtils.getBean(ProviderDataDao.class);
    AppointmentStatusDao appointmentStatusDao = SpringUtils.getBean(AppointmentStatusDao.class);
    LookupListManager lookupListManager = SpringUtils.getBean(LookupListManager.class);
    LookupList reasonCodes = lookupListManager.findLookupListByName(loggedInInfo, "reasonCode");
    Map<Integer, LookupListItem> reasonCodesMap = new HashMap<Integer, LookupListItem>();
    for (LookupListItem lli : reasonCodes.getItems()) {
        reasonCodesMap.put(lli.getId(), lli);
    }


    if (IsPropertiesOn.isMultisitesEnable()) {
        SiteDao siteDao = (SiteDao) WebApplicationContextUtils.getWebApplicationContext(application)
                .getBean(SiteDao.class);
        sites = siteDao.getAllActiveSites();
        //get all sites bgColors
        for (Site st : sites) {
            siteBgColor.put(st.getName(), new String[]{st.getBgColor(), st.getShortName()});
        }
    }

    String curProvider_no = (String) session.getAttribute("user");
    String demographic_no = request.getParameter("demographic_no");
    String strLimit1 = "0";
    String strLimit2 = "500";
    if (request.getParameter("limit1") != null)
        strLimit1 = request.getParameter("limit1");
    if (request.getParameter("limit2") != null)
        strLimit2 = request.getParameter("limit2");

    DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
    Demographic patientDemo = (demographic_no != null && !demographic_no.isEmpty()) ? demographicManager.getDemographic(loggedInInfo, demographic_no) : null;
    String demolastname = patientDemo != null ? patientDemo.getLastName() : "";
    String demofirstname = patientDemo != null ? patientDemo.getFirstName() : "";
    String deepColor = "#CCCCFF", weakColor = "#EEEEFF";
    String showDeleted = request.getParameter("deleted");
    String orderby = "";
    if (request.getParameter("orderby") != null)
        orderby = request.getParameter("orderby");

    Map<String, ProviderData> providerMap = new HashMap<String, ProviderData>();
%>

<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title><fmt:message key="demographic.demographicappthistory.title"/></title>

    <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>
    <!--
        The global-head.jspf fragment provides:
        - Viewport meta tag for responsive design
        - global.js (legacy focus/refresh helpers)
        - jQuery 3.7.1
        - Bootstrap 5.3.3 (JS bundle + CSS)
        - jQuery UI 1.14.2 CSS (JS must be included page-specifically where dialogs/widgets are needed)
        - Font Awesome 6.7.2 (icon library)
        - searchBox.css (shared search/form styles)
        - global.css (CARLOS design tokens and common classes)
    -->
    <c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>
    <script>
        var ctx = '<%=request.getContextPath()%>';
    </script>
    <oscar:customInterface section="appthistory"/>

    <link rel="stylesheet" type="text/css" href="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.4/css/dataTables.bootstrap5.min.css">
    <script type="text/javascript" src="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.4/js/jquery.dataTables.min.js"></script>
    <script type="text/javascript" src="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.4/js/dataTables.bootstrap5.min.js"></script>

    <script type="text/javascript">
    function popupPageNew(vheight, vwidth, varpage) {
            var page = "" + varpage;
            windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes";
            var popup = window.open(page, "demographicprofile", windowprops);
            if (popup != null) {
                if (popup.opener == null) {
                    popup.opener = self;
                }
            }
    }

    function selectAllCheckboxes() {
            jQuery("input[name='sel']").each(function() {
                jQuery(this).attr('checked', true);
            });
    }

    function deselectAllCheckboxes() {
            jQuery("input[name='sel']").each(function() {
                jQuery(this).attr('checked', false);
            });
    }


    function toggleShowDeleted(value) {
                if (value) {
                    //show deleted
                    //appt_history_w_deleted
                    <c:set var="__enc_1"><carlos:encode value='<%= demographic_no %>' context="uriComponent"/></c:set>
                    <c:set var="__enc_2"><carlos:encode value='<%= orderby %>' context="uriComponent"/></c:set>
                    location.href = '<%=request.getContextPath()%>/demographic/DemographicApptHistory?demographic_no=<carlos:encode value='${__enc_1}' context="javaScript"/>&orderby=<carlos:encode value='${__enc_2}' context="javaScript"/>&dboperation=appt_history_w_deleted&limit1=<carlos:encode value='<%= strLimit1 %>' context="javaScript"/>&limit2=<carlos:encode value='<%= strLimit2 %>' context="javaScript"/>&deleted=true';
                } else {
                    //don't show deleted
                    <c:set var="__enc_3"><carlos:encode value='<%= demographic_no %>' context="uriComponent"/></c:set>
                    <c:set var="__enc_4"><carlos:encode value='<%= orderby %>' context="uriComponent"/></c:set>
                    location.hr                    
ef = '<%=request.getContextPath()%>/demographic/DemographicApptHistory?demographic_no=<carlos:encode value='${__enc_3}' context="javaScript"/>&orderby=<carlos:encode value='${__enc_4}' context="javaScript"/>&dboperation=appt_history&limit1=<carlos:encode value='<%= strLimit1 %>' context="javaScript"/>&limit2=<carlos:encode value='<%= strLimit2 %>' context="javaScript"/>';
                }
    }

    jQuery(document).ready(function () {
        apptResultsTable = jQuery("#apptHistoryTbl").DataTable({
            searching: true,
            paging: true,
            pageLength: 10,
            language: {
                url: '${pageContext.request.contextPath}/library/DataTables/i18n/<fmt:message key="global.i18n.datatablescode"/>.json'
                }
        });
        <%if(showDeleted != null && showDeleted.equals("true")) { %>
        jQuery("#showDeleted").attr('checked', true);
         <% } else {%>
        jQuery("#showDeleted").attr('checked', false);
        <%} %>
    });


    </script>


</head>
<body>

<!-- ================================================================
     CONTAINER — outermost wrapper; constrains max-width and centers
     content on large screens while staying full-width on mobile.
     ================================================================ -->
<div class="container">

    <!-- ============================================================
         ALERT BANNER — hidden by default; shown via JavaScript when
         a server-side or client-side error needs to be surfaced.
         Usage: document.getElementById('jsAlertText').textContent = msg;
                document.getElementById('jsAlertBanner').style.display = '';
         ============================================================ -->
    <div id="jsAlertBanner"
         class="alert alert-danger alert-dismissible"
         style="display:none"
         role="alert">
        <span id="jsAlertText"></span>
        <button type="button"
                class="btn-close"
                onclick="this.closest('.alert').style.display='none'"
                aria-label="Close"></button>
    </div>

    <!-- ============================================================
         PAGE HEADER BAR — short title + long title (icon optional).
         Mirrors the OSCAR MainTableTopRow / TopStatusBar pattern.
         Structure:
           [icon?] [Short Title]   [Long Title .....................]
         ============================================================ -->
    <div class="page-header-bar d-flex align-items-center justify-content-between
                py-2 mb-3 border-bottom" id="header">
        <div class="d-flex align-items-center gap-2">
            <!--
                Optional Fontawesome Icon — replace "bi-file-earmark-text"
                with any icon from https://icons.getbootstrap.com/
                or remove the <i> tag entirely if no icon is needed.
            -->
            <i class="fa-solid fa-clock-rotate-left"></i>
            <span class="fw-semibold"><fmt:message key="demographic.demographicappthistory.msgHistory"/></span>
        </div>
        <div class="text-muted small"><fmt:message key="demographic.demographicappthistory.msgResults"/>: <carlos:encode value='<%= demolastname %>' context="html"/>
                            ,<carlos:encode value='<%= demofirstname %>' context="html"/>(<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("demographic_no")) %>' context="html"/>)</div>
    </div>

    <!-- ============================================================
         MAIN CONTENT WRAPPER — light background card to separate
         page content from the body background.
         ============================================================ -->
    <div class="bg-light border rounded p-2">

        <form action="/something.do" method="post">

            <!-- ==================================================
                 CONTENT ROW — two-column layout:
                   col-12 col-md-2 : left sidebar  (OSCAR left col)
                   col-12 col-md-10: right content (OSCAR right col)
                 On small screens both columns stack vertically.
                 ================================================== -->
            <div class="row g-2">

                <!-- LEFT SIDEBAR COLUMN
                     Mirrors: MainTableLeftColumn
                     Contains navigation links / contextual actions. -->
                <div class="col-12 col-md-2">
                    <nav class="d-flex flex-column gap-1" aria-label="Sidebar navigation">
                        <fmt:message key="demographic.demographicappthistory.msgShowDeleted"/><input type="checkbox" name="showDeleted" id="showDeleted" onChange="toggleShowDeleted(this.checked);"/>
                    </nav>
                </div>

                <!-- RIGHT CONTENT COLUMN
                     Mirrors: MainTableRightColumn
                     Contains the primary form and data entry area. -->
                <div class="col-12 col-md-10">

                    <table id="apptHistoryTbl" class="table" >
                    <thead>
                    <tr>
                        <th><fmt:message key="demographic.demographicappthistory.msgApptDate"/></th>
                        <th><fmt:message key="demographic.demographicappthistory.msgFrom"/></th>
                        <th><fmt:message key="demographic.demographicappthistory.msgTo"/></th>
                        <th><fmt:message key="demographic.demographicappthistory.msgStatus"/></th>
                        <th><fmt:message key="demographic.demographicappthistory.msgType"/></th>
                        <th><fmt:message key="demographic.demographicappthistory.msgReason"/></th>
                        <th><fmt:message key="demographic.demographicappthistory.msgProvider"/></th>
                        <th><fmt:message key="demographic.demographicappthistory.msgComments"/></th>
                        <% if (IsPropertiesOn.isMultisitesEnable()) { %>
                        <th><fmt:message key="Appointment.formLocation"/></th>
                        <% } %>
                    </tr>
                    </thead>
                    <tbody>
                    <%
                        int iRSOffSet = 0;
                        int iPageSize = 10;
                        int iRow = 0;
                        if (request.getParameter("limit1") != null) {
                            try { iRSOffSet = Integer.parseInt(request.getParameter("limit1")); }
                            catch (NumberFormatException ignored) { /* keep default */ }
                        }
                        if (request.getParameter("limit2") != null) {
                            try { iPageSize = Integer.parseInt(request.getParameter("limit2")); }
                            catch (NumberFormatException ignored) { /* keep default */ }
                        }
                        List<Object> appointmentList;
                        AppointmentManager appointmentManager = SpringUtils.getBean(AppointmentManager.class);

                        if (!"true".equals(showDeleted)) {
                            appointmentList = new java.util.ArrayList<Object>();
                            appointmentList.addAll(appointmentManager.getAppointmentHistoryWithoutDeleted(loggedInInfo, new Integer(demographic_no), iRSOffSet, iPageSize));
                        } else {
                            appointmentList = appointmentManager.getAppointmentHistoryWithDeleted(loggedInInfo, new Integer(demographic_no), iRSOffSet, iPageSize);
                        }
                        boolean bodd = false;
                        int nItems = 0;


                        if (appointmentList == null) {
                            out.println("failed!!!");
                        } else {

                            for (Object obj : appointmentList) {
                                boolean deleted = false;

                                Appointment appointment = null;
                                AppointmentArchive appointmentArchive = null;
                                if (obj instanceof Appointment) {
                                    appointment = (Appointment) obj;
                                }
                                if (obj instanceof AppointmentArchive) {
                                    appointmentArchive = (AppointmentArchive) obj;
                                    appointment = new Appointment();

                                    BeanUtils.copyProperties(appointmentArchive, appointment);
                                    appointment.setId(appointmentArchive.getAppointmentNo());

                                    deleted = true;
                                }
                                iRow++;

                                if (iRow > iPageSize) break;
                                bodd = bodd ? false : true; //for the color of rows
                                nItems++; //to calculate if it is the end of records

                                ProviderData provider = providerDao.findByProviderNo(appointment.getProviderNo());
                                AppointmentStatus as = appointmentStatusDao.findByStatus(appointment.getStatus());

                                if (provider != null) {
                                    providerMap.put(provider.getId(), provider);
                                }

                                String reasonCodeName = null;
                                if (appointment.getReasonCode() != null) {
                                    LookupListItem lookupListItem = reasonCodesMap.get(appointment.getReasonCode());
                                    if (lookupListItem != null) {
                                        reasonCodeName = lookupListItem.getLabel();
                                    }
                                    if (reasonCodeName != null) {
                                        reasonCodeName = reasonCodeName.trim();
                                    }
                                }

                    %>
                    <tr <%=(deleted) ? "style='text-decoration: line-through' " : "" %>
                            appt_no="<carlos:encode value='<%= appointment.getId().toString() %>' context="htmlAttribute"/>"
                            demographic_no="<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(demographic_no) %>' context="htmlAttribute"/>" 
                            provider_no="<carlos:encode value='<%= provider!=null?provider.getId():"" %>' context="htmlAttribute"/>"
                        <c:set var="__enc_5"><carlos:encode value='<%= demographic_no %>' context="uriComponent"/></c:set>
                        <c:set var="__enc_6"><carlos:encode value='<%= appointment.getId().toString() %>' context="uriComponent"/></c:set> >
                        <td>
                            <a href=# onClick="popupPageNew(535,860, '<%= request.getContextPath() %>/appointment/editappointment?demographic_no=<carlos:encode value='${__enc_5}' context="javaScriptAttribute"/>&appointment_no=<carlos:encode value='${__enc_6}' context="javaScriptAttribute"/>&dboperation=search');return false;"><carlos:encode value='<%= appointment.getAppointmentDate() != null ? appointment.getAppointmentDate().toString() : "" %>' context="html"/>
                            </a>
                        </td>
                        <td class="time">
                            <carlos:encode value='<%= appointment.getStartTime() != null ? appointment.getStartTime().toString().substring(0,5) : "" %>' context="html"/>&nbsp;
                        </td>
                        <td class="time">
                            <carlos:encode value='<%= appointment.getEndTime() != null ? appointment.getEndTime().toString().substring(0,5) : "" %>' context="html"/>&nbsp;
                        </td>
                        <td>
                            <%if (as != null && as.getDescription() != null) {%>
                            <carlos:encode value='<%= as.getDescription() %>' context="html"/>&nbsp;
                            <% } %>
                        </td>
                        <td>
                            <carlos:encode value='<%= appointment.getType() %>' context="html"/>&nbsp;
                        </td>
                        <td><%=(reasonCodeName != null && !reasonCodeName.isEmpty()) ? SafeEncode.forHtml(reasonCodeName) : ""%><%=(appointment.getReason() != null && !appointment.getReason().isEmpty()) ? ((reasonCodeName != null && !reasonCodeName.isEmpty()) ? " - " : "") + SafeEncode.forHtml(appointment.getReason()) : ""%>
                        </td>
                        <% if (provider != null) {%>
                        <td><carlos:encode value='<%= (provider.getLastName() == null ? "N/A" : provider.getLastName()) + "," + (provider.getFirstName() == null ? "N/A" : provider.getFirstName()) %>' context="html"/>&nbsp;
                        </td>
                        <%} else { %>
                        <td><fmt:message key="global.na"/></td>
                        <%}%>


                        <%
                            String remarks = appointment.getRemarks();
                            if (remarks == null)
                                remarks = "";

                            String comments = "";
                            boolean newline = false;

                            if (appointment.getStatus() != null) {
                                if (appointment.getStatus().startsWith("N")) {
                                    comments = "No Show";
                                } else if (appointment.getStatus().startsWith("C")) {
                                    comments = "Cancelled";
                                }
                            }

                            if (!remarks.isEmpty() && !comments.isEmpty()) {
                                newline = true;
                            }
                        %>
                        <td>&nbsp;<carlos:encode value='<%= remarks %>' context="html"/><% if (newline) {%><br/>&nbsp;<%}%><carlos:encode value='<%= comments %>' context="html"/>
                        </td>
                        <%
                            if (IsPropertiesOn.isMultisitesEnable()) {
                                String[] sbc = siteBgColor.get(appointment.getLocation());
                                String siteColor = sbc != null && sbc.length > 0 ? sbc[0] : "";
                                String siteLabel = sbc != null && sbc.length > 1 ? sbc[1] : io.github.carlos_emr.carlos.util.StringUtils.noNull(appointment.getLocation());
                        %>
                        <td style='background-color:<carlos:encode value='<%= siteColor %>' context="cssString"/>'><carlos:encode value='<%= siteLabel %>' context="html"/>
                        </td>
                        <%
                            }
                        %>
                    </tr>
                    <%
                            }
                        }
                    %>
                </tbody>
                </table>
                <%
                    int nPrevPage = 0, nNextPage = 0;
                    nNextPage = Integer.parseInt(strLimit2) + Integer.parseInt(strLimit1);
                    nPrevPage = Integer.parseInt(strLimit1) - Integer.parseInt(strLimit2);
                    if (nPrevPage >= 0) {
                %>
                    <div class="mb-2">
                        <a class="btn btn-primary" href="DemographicApptHistory?demographic_no=<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("demographic_no")) %>' context="uriComponent"/>&dboperation=<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("dboperation")) %>' context="uriComponent"/>&orderby=<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("orderby")) %>' context="uriComponent"/>&limit1=<%=nPrevPage%>&limit2=<carlos:encode value='<%= strLimit2 %>' context="uriComponent"/>">
                        <fmt:message key="demographic.demographicappthistory.btnPrevPage"/></a>
                    </div>
                <%
                    }

                    if (nItems >= Integer.parseInt(strLimit2)) {
                %>
                    <div class="mb-2">
                        <a class="btn btn-primary" href="DemographicApptHistory?demographic_no=<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("demographic_no")) %>' context="uriComponent"/>&dboperation=<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("dboperation")) %>' context="uriComponent"/>&orderby=<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("orderby")) %>' context="uriComponent"/>&limit1=<%=nNextPage%>&limit2=<carlos:encode value='<%= strLimit2 %>' context="uriComponent"/>">
                        <fmt:message key="demographic.demographicappthistory.btnNextPage"/></a>
                    </div>
                <%
                    }
                %>
                </div><!-- end right column -->
            </div><!-- end .row -->

        </form>

    </div><!-- end .bg-light -->

</div><!-- end .container -->

</body>
</html>
