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

<%-- ========== PAGE IMPORTS ========== --%>
<%@ page import="java.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.DemographicDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.DemographicArchiveDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.DemographicArchive" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@ page import="io.github.carlos_emr.carlos.util.DateUtils" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.pageUtil.Util" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.apache.commons.lang3.math.NumberUtils" %>

<%-- ========== TAGLIB DECLARATIONS ========== --%>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>

<%-- ========== SECURITY CHECK ========== --%>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_demographic" rights="r" reverse="<%=true%>">
    <%authed = false;%>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_demographic");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%-- ========== BUSINESS LOGIC ========== --%>
<%
    // Get Spring beans
    ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
    DemographicDao demographicDao = SpringUtils.getBean(DemographicDao.class);
    DemographicArchiveDao demoArchiveDao = SpringUtils.getBean(DemographicArchiveDao.class);

    // Read request parameters
    String demographicNo = request.getParameter("demographicNo");

    int demographicId = NumberUtils.toInt(demographicNo, -1);
    Demographic demographic = demographicId > 0
            ? demographicDao.getClientByDemographicNo(demographicId)
            : null;
    if (demographic == null) {
        response.sendError(404);
        return;
    }

    // Build history rows — deduplicate consecutive identical entries
    List<Map<String, String>> historyRows = new ArrayList<>();
    String lastRosterStatusDisplay = null;
    String lastRosterDateDisplay = null;
    String lastRosterEnrolledToDisplay = null;
    String lastRosterTerminationDateDisplay = null;
    String lastRosterTerminationReasonDisplay = null;

    List<DemographicArchive> archiveList = demoArchiveDao.findByDemographicNoChronologically(demographic.getDemographicNo());

    for (DemographicArchive da : archiveList) {
        String itemRosterStatus = da.getRosterStatus();
        if (itemRosterStatus == null || "".equals(itemRosterStatus)) {
            continue;
        }

        String iRosterEnrolledTo = da.getRosterEnrolledTo();
        Provider enrolledToProvider = providerDao.getProvider(iRosterEnrolledTo);

        String dRosterEnrolledTo = enrolledToProvider != null ? enrolledToProvider.getFormattedName() : "N/A";
        String dRosterDate = DateUtils.formatDate(da.getRosterDate(), request.getLocale());
        String dRosterTerminationDate = da.getRosterTerminationDate() != null
                ? DateUtils.formatDate(da.getRosterTerminationDate(), request.getLocale()) : "";
        String dRosterTerminationReason = "";
        if (da.getRosterTerminationReason() != null) {
            String reason = Util.rosterTermReasonProperties.getReasonByCode(da.getRosterTerminationReason());
            if (reason != null) dRosterTerminationReason = reason;
        }

        // Compute a stable display value for deduplication comparison
        String dRosterStatusDisplay;
        if ("RO".equals(itemRosterStatus)) dRosterStatusDisplay = "ROSTERED";
        else if ("TE".equals(itemRosterStatus)) dRosterStatusDisplay = "TERMINATED";
        else if ("FS".equals(itemRosterStatus)) dRosterStatusDisplay = "FEE FOR SERVICE";
        else dRosterStatusDisplay = itemRosterStatus;

        // Skip if all fields are identical to the previous printed row
        if (dRosterStatusDisplay.equals(lastRosterStatusDisplay)
                && dRosterEnrolledTo.equals(lastRosterEnrolledToDisplay)
                && dRosterDate.equals(lastRosterDateDisplay)
                && dRosterTerminationDate.equals(lastRosterTerminationDateDisplay)
                && dRosterTerminationReason.equals(lastRosterTerminationReasonDisplay)) {
            continue;
        }

        Map<String, String> row = new LinkedHashMap<>();
        row.put("dateOfRecord", DateUtils.formatDate(da.getLastUpdateDate(), request.getLocale()));
        row.put("rosterStatusCode", itemRosterStatus);
        row.put("enrolledTo", dRosterEnrolledTo);
        row.put("dateRostered", dRosterDate);
        row.put("dateTerminated", dRosterTerminationDate);
        row.put("terminationReason", dRosterTerminationReason);
        historyRows.add(row);

        lastRosterStatusDisplay = dRosterStatusDisplay;
        lastRosterEnrolledToDisplay = dRosterEnrolledTo;
        lastRosterDateDisplay = dRosterDate;
        lastRosterTerminationDateDisplay = dRosterTerminationDate;
        lastRosterTerminationReasonDisplay = dRosterTerminationReason;
    }

    // Current enrollment status info
    String currentRosterStatus = demographic.getRosterStatus();
    String currentEnrolledToName = providerDao.getProviderName(demographic.getRosterEnrolledTo());
    String currentRosterDate = DateUtils.formatDate(demographic.getRosterDate(), request.getLocale());
    String currentTermDate = demographic.getRosterTerminationDate() != null
            ? DateUtils.formatDate(demographic.getRosterTerminationDate(), request.getLocale()) : "";
    String currentTermReason = "";
    if (demographic.getRosterTerminationReason() != null) {
        String r = Util.rosterTermReasonProperties.getReasonByCode(demographic.getRosterTerminationReason());
        if (r != null) currentTermReason = r;
    }

    // Set page context attributes for JSTL
    pageContext.setAttribute("ctx", request.getContextPath());
    pageContext.setAttribute("demographic", demographic);
    pageContext.setAttribute("hasEnrollmentStatus", !StringUtils.isEmpty(currentRosterStatus));
    pageContext.setAttribute("historyRows", historyRows);
    pageContext.setAttribute("hasHistory", !historyRows.isEmpty());
    pageContext.setAttribute("currentRosterStatus", currentRosterStatus != null ? currentRosterStatus : "");
    pageContext.setAttribute("currentEnrolledToName", currentEnrolledToName != null ? currentEnrolledToName : "");
    pageContext.setAttribute("currentRosterDate", currentRosterDate != null ? currentRosterDate : "");
    pageContext.setAttribute("currentTermDate", currentTermDate);
    pageContext.setAttribute("currentTermReason", currentTermReason);
%>

<fmt:setBundle basename="oscarResources"/>

<!DOCTYPE html>
<html lang="${pageContext.request.locale.language}">
<head>
    <meta charset="UTF-8">
    <title><fmt:message key="demographic.enrollementhistory.enrollmentHistory"/></title>
    <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>
    <link rel="stylesheet" type="text/css" href="${ctx}/library/DataTables/DataTables-1.13.4/css/dataTables.bootstrap5.min.css">
    <script type="text/javascript" src="${ctx}/library/DataTables/DataTables-1.13.4/js/jquery.dataTables.min.js"></script>
    <script type="text/javascript" src="${ctx}/library/DataTables/DataTables-1.13.4/js/dataTables.bootstrap5.min.js"></script>
    <script>
        jQuery(document).ready(function () {
            jQuery('#enrollmentHistoryTbl').DataTable({
                searching: false,
                pageLength: 25,
                language: {
                    url: '${ctx}/library/DataTables/i18n/<fmt:message key="global.i18n.datatablescode"/>.json'
                }
            });
        });
    </script>
</head>
<body>
<div class="container mt-2">

    <div class="page-header-bar d-flex align-items-center justify-content-between py-2 mb-3 border-bottom" id="header">
        <div class="d-flex align-items-center gap-2">
            <i class="fa-solid fa-file-medical"></i>
            <span class="fw-semibold"><fmt:message key="demographic.enrollementhistory.enrollmentHistory"/></span>
        </div>
        <div class="text-muted small">
            <carlos:encode value="${demographic.formattedName}" context="html"/>
            (<carlos:encode value="${demographic.formattedDob}" context="html"/>)
        </div>
    </div>

    <div class="bg-light border rounded p-3">

        <c:if test="${hasEnrollmentStatus}">
            <h5 class="mb-3"><fmt:message key="demographic.enrollementhistory.currentEnrollmentStatus"/></h5>
            <dl class="row mb-4">
                <dt class="col-sm-4"><fmt:message key="demographic.enrollementhistory.status"/>:</dt>
                <dd class="col-sm-8">
                    <c:choose>
                        <c:when test="${currentRosterStatus eq 'RO'}"><fmt:message key="demographic.enrollementhistory.Rostered"/></c:when>
                        <c:when test="${currentRosterStatus eq 'TE'}"><fmt:message key="demographic.enrollementhistory.terminated"/></c:when>
                        <c:when test="${currentRosterStatus eq 'FS'}"><fmt:message key="demographic.enrollementhistory.feeforservice"/></c:when>
                        <c:otherwise><carlos:encode value="${currentRosterStatus}" context="html"/></c:otherwise>
                    </c:choose>
                </dd>

                <c:if test="${currentRosterStatus eq 'RO' or currentRosterStatus eq 'TE'}">
                    <dt class="col-sm-4"><fmt:message key="demographic.enrollementhistory.dateRostered"/>:</dt>
                    <dd class="col-sm-8"><carlos:encode value="${currentRosterDate}" context="html"/></dd>
                    <dt class="col-sm-4"><fmt:message key="demographic.enrollementhistory.enrolledTo"/>:</dt>
                    <dd class="col-sm-8"><carlos:encode value="${currentEnrolledToName}" context="html"/></dd>
                </c:if>

                <c:if test="${currentRosterStatus eq 'TE'}">
                    <dt class="col-sm-4"><fmt:message key="demographic.enrollementhistory.dateTerminated"/>:</dt>
                    <dd class="col-sm-8"><carlos:encode value="${currentTermDate}" context="html"/></dd>
                    <dt class="col-sm-4"><fmt:message key="demographic.enrollementhistory.terminationReason"/>:</dt>
                    <dd class="col-sm-8"><carlos:encode value="${currentTermReason}" context="html"/></dd>
                </c:if>
            </dl>
        </c:if>

        <h5 class="mb-3"><fmt:message key="demographic.enrollementhistory.patientEnrollmentHistory"/></h5>

        <c:choose>
            <c:when test="${hasHistory}">
                <table id="enrollmentHistoryTbl" class="table table-striped table-hover table-sm">
                    <thead>
                        <tr>
                            <th><fmt:message key="demographic.enrollementhistory.dateOfRecord"/></th>
                            <th><fmt:message key="demographic.enrollementhistory.status"/></th>
                            <th><fmt:message key="demographic.enrollementhistory.enrolledTo"/></th>
                            <th><fmt:message key="demographic.enrollementhistory.dateRostered"/></th>
                            <th><fmt:message key="demographic.enrollementhistory.dateTerminated"/></th>
                            <th><fmt:message key="demographic.enrollementhistory.terminationReason"/></th>
                        </tr>
                    </thead>
                    <tbody>
                        <c:forEach items="${historyRows}" var="row">
                            <tr>
                                <td><carlos:encode value="${row.dateOfRecord}" context="html"/></td>
                                <td>
                                    <c:choose>
                                        <c:when test="${row.rosterStatusCode eq 'RO'}"><fmt:message key="demographic.enrollementhistory.Rostered"/></c:when>
                                        <c:when test="${row.rosterStatusCode eq 'TE'}"><fmt:message key="demographic.enrollementhistory.terminated"/></c:when>
                                        <c:when test="${row.rosterStatusCode eq 'FS'}"><fmt:message key="demographic.enrollementhistory.feeforservice"/></c:when>
                                        <c:otherwise><carlos:encode value="${row.rosterStatusCode}" context="html"/></c:otherwise>
                                    </c:choose>
                                </td>
                                <td><carlos:encode value="${row.enrolledTo}" context="html"/></td>
                                <td><carlos:encode value="${row.dateRostered}" context="html"/></td>
                                <td><carlos:encode value="${row.dateTerminated}" context="html"/></td>
                                <td><carlos:encode value="${row.terminationReason}" context="html"/></td>
                            </tr>
                        </c:forEach>
                    </tbody>
                </table>
            </c:when>
            <c:otherwise>
                <p class="text-muted"><fmt:message key="demographic.enrollementhistory.noEnrollmentDataFound"/></p>
            </c:otherwise>
        </c:choose>

        <div class="mt-3">
            <button type="button" class="btn btn-secondary" onclick="window.close()">
                <fmt:message key="global.btnClose"/>
            </button>
        </div>

    </div><%-- end bg-light --%>

</div><%-- end container --%>
</body>
</html>
