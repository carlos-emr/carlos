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

    @since 2001-01-01
    @see oscarMDS/ViewPatientSearch (Struts action that renders this page)
    @see io.github.carlos_emr.carlos.mds.pageUtil.SearchPatient2Action
    @see io.github.carlos_emr.carlos.mds.pageUtil.PatientMatch2Action

--%>
<%@ page import="java.util.*, java.sql.*" %>
<%@ page import="io.github.carlos_emr.carlos.utility.DbConnectionFilter" %>
<%@ page import="io.github.carlos_emr.MyDateFormat, io.github.carlos_emr.Misc" %>
<%@ page import="io.github.carlos_emr.carlos.util.StringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ page import="io.github.carlos_emr.carlos.lab.ca.all.parsers.Factory" %>
<%@ page import="io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler" %>
<%@ page import="org.slf4j.Logger, org.slf4j.LoggerFactory" %>

<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="carlos" prefix="carlos" %>

<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_lab" rights="r" reverse="<%=true%>">
    <%authed = false;%>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_lab");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>
<jsp:useBean id="providerBean" class="java.util.Properties" scope="session"/>

<%
    Logger logger = LoggerFactory.getLogger("PatientSearch.jsp");
%>

<%
    // === Parameters ===
    String p_from      = StringUtils.noNull(request.getParameter("from"));
    String p_labNo     = StringUtils.noNull(request.getParameter("labNo"));
    String p_labType   = StringUtils.noNull(request.getParameter("labType"));
    String p_keyword   = StringUtils.noNull(request.getParameter("keyword")).trim();
    String p_searchMode = StringUtils.noNull(request.getParameter("search_mode"));
    String p_orderby   = StringUtils.noNull(request.getParameter("orderby"));

    // === Server-side DOB validation ===
    if ("search_dob".equals(p_searchMode) && !p_keyword.isEmpty()) {
        // Validate YYYY-MM-DD format with strict range checks
        if (!p_keyword.matches("^\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])$")) {
            request.setAttribute("searchUnavailable", true);
            request.setAttribute("searchErrorMessage", "oscarMDS.patientSearch.errorInvalidDate");
            p_searchMode = ""; // Disable search to prevent query execution
        }
    }

    int parsedLimit1 = 0;
    int parsedLimit2 = 500;
    if (request.getParameter("limit1") != null) {
        try { parsedLimit1 = Math.max(0, Integer.parseInt(request.getParameter("limit1"))); } catch (NumberFormatException e) {}
    }
    if (request.getParameter("limit2") != null) {
        try {
            int tmp = Integer.parseInt(request.getParameter("limit2"));
            if (tmp > 0) parsedLimit2 = Math.min(tmp, 500);
        } catch (NumberFormatException e) {}
    }

    // === HL7 patient data extraction for Add as New Patient pre-fill ===
    String labPatientFirstName = "", labPatientLastName  = "";
    String labPatientDobYear   = "", labPatientDobMonth  = "", labPatientDobDay = "";
    String labPatientSex       = "", labPatientAddress   = "", labPatientCity    = "";
    String labPatientProvince  = "", labPatientPostal    = "", labPatientPhone   = "";
    String labPatientHin       = "", labPatientHinVer    = "";
    if (!p_labNo.isEmpty()) {
        try {
            MessageHandler labHandler = Factory.getHandler(p_labNo);
            labPatientFirstName = StringUtils.noNull(labHandler.getFirstName());
            labPatientLastName  = StringUtils.noNull(labHandler.getLastName());
            String dob = StringUtils.noNull(labHandler.getDOB());
            if (dob.length() == 10) {
                labPatientDobYear  = dob.substring(0, 4);
                labPatientDobMonth = dob.substring(5, 7);
                labPatientDobDay   = dob.substring(8, 10);
            }
            labPatientSex      = StringUtils.noNull(labHandler.getSex());
            labPatientAddress  = StringUtils.noNull(labHandler.getPatientAddress());
            labPatientCity     = StringUtils.noNull(labHandler.getPatientCity());
            labPatientProvince = StringUtils.noNull(labHandler.getPatientProvince());
            labPatientPostal   = StringUtils.noNull(labHandler.getPatientPostal());
            labPatientPhone    = StringUtils.noNull(labHandler.getHomePhone());
            labPatientHin      = StringUtils.noNull(labHandler.getHealthNum());
            labPatientHinVer   = StringUtils.noNull(labHandler.getHealthNumVersion());
        } catch (Exception e) { /* lab data unavailable; button opens blank add-patient form */ }
    }

    boolean hasSearch = !p_searchMode.isEmpty() && !p_keyword.isEmpty();
    boolean isLabPrefillAvailable = hasSearch && !p_labNo.isEmpty();

    // === DB query: build patient rows list ===
    List<Map<String,String>> patientRows = new ArrayList<>();
    int nItems = 0;

    if (!p_searchMode.isEmpty()) {
        GregorianCalendar now = new GregorianCalendar();
        int curYear  = now.get(Calendar.YEAR);
        int curMonth = now.get(Calendar.MONTH) + 1;
        int curDay   = now.get(Calendar.DAY_OF_MONTH);

        // Sanitize ORDER BY against allowed column names
        String safeOrderby = "order by last_name";
        if (!p_orderby.isEmpty()) {
            Set<String> validCols = Set.of("last_name","first_name","demographic_no","chart_no",
                "hin","phone","sex","year_of_birth","month_of_birth","date_of_birth",
                "roster_status","patient_status","provider_no");
            String[] parts = p_orderby.trim().split("\\s+");
            if (parts.length >= 1 && parts.length <= 2) {
                String col = parts[0];
                String dir = "";
                if (parts.length == 2) {
                    if ("asc".equalsIgnoreCase(parts[1]))       dir = " ASC";
                    else if ("desc".equalsIgnoreCase(parts[1])) dir = " DESC";
                    else col = "";
                }
                if (validCols.contains(col)) safeOrderby = "order by " + col + dir;
            }
        }

        String regularexp = "like";
        String fieldname;
        boolean isNameByLastAndFirst = false;

        if ("search_address".equals(p_searchMode)) {
            fieldname = "address";
        } else if ("search_phone".equals(p_searchMode)) {
            fieldname = "phone";
        } else if ("search_hin".equals(p_searchMode)) {
            fieldname = "hin";
        } else if ("search_dob".equals(p_searchMode)) {
            fieldname = "year_of_birth " + regularexp + " ? and month_of_birth " + regularexp + " ? and date_of_birth";
        } else if ("search_chart_no".equals(p_searchMode)) {
            fieldname = "chart_no";
        } else { // search_name (default)
            String kwtrim = p_keyword.trim();
            if (kwtrim.indexOf(",") < 0 || kwtrim.indexOf(",") == kwtrim.length() - 1) {
                fieldname = "last_name";
            } else {
                isNameByLastAndFirst = true;
                fieldname = "last_name " + regularexp + " ? and first_name";
            }
        }

        // Merged non-head records are excluded by the NOT EXISTS subquery so no per-row
        // getHead() call is needed. LIMIT/OFFSET push pagination to the database engine.
        String sql = "select demographic_no,first_name,last_name,roster_status,patient_status,sex,"
            + "chart_no,year_of_birth,month_of_birth,date_of_birth,provider_no "
            + "from demographic "
            + "where " + fieldname + " " + regularexp + " ? "
            + "and not exists (select 1 from demographic_merged dm "
            + "  where dm.demographic_no = demographic.demographic_no and dm.deleted = 0) "
            + safeOrderby + " limit ? offset ?";

        Connection dbConn = DbConnectionFilter.getThreadLocalDbConnection();
        try (PreparedStatement ps = dbConn.prepareStatement(sql)) {
            int pidx = 1;
            if ("search_dob".equals(p_searchMode)) {
                String yearStr = "" + MyDateFormat.getYearFromStandardDate(p_keyword) + "%";
                String monStr  = "" + MyDateFormat.getMonthFromStandardDate(p_keyword) + "%";
                String dayStr  = "" + MyDateFormat.getDayFromStandardDate(p_keyword) + "%";
                if (monStr.length() == 2) monStr = "0" + monStr; // zero-pad single-digit month
                ps.setString(pidx++, yearStr);
                ps.setString(pidx++, monStr);
                ps.setString(pidx++, dayStr);
            } else if ("search_name".equals(p_searchMode)) {
                String kw = p_keyword + "%";
                if (kw.indexOf(",") < 0) {
                    ps.setString(pidx++, kw);
                } else {
                    int commaIdx = kw.indexOf(",");
                    if (isNameByLastAndFirst) {
                        ps.setString(pidx++, kw.substring(0, commaIdx).trim() + "%");
                        ps.setString(pidx++, kw.substring(commaIdx + 1).trim() + "%");
                    } else {
                        ps.setString(pidx++, kw.substring(0, commaIdx).trim() + "%");
                    }
                }
            } else {
                ps.setString(pidx++, p_keyword + "%");
            }
            ps.setInt(pidx++, parsedLimit2);
            ps.setInt(pidx,   parsedLimit1);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    nItems++;
                    int age = 0;
                    String monthStr = Misc.getString(rs, "month_of_birth");
                    if (!monthStr.isEmpty()) {
                        try {
                            int bYear  = Integer.parseInt(Misc.getString(rs, "year_of_birth"));
                            int bMonth = Integer.parseInt(monthStr);
                            int bDay   = Integer.parseInt(Misc.getString(rs, "date_of_birth"));
                            age = curYear - bYear;
                            if (curMonth < bMonth || (curMonth == bMonth && curDay < bDay)) age--;
                        } catch (NumberFormatException e2) {}
                    }

                    String demoNo = Misc.getString(rs, "demographic_no");
                    String dob = Misc.getString(rs, "year_of_birth") + "-"
                        + Misc.getString(rs, "month_of_birth") + "-"
                        + Misc.getString(rs, "date_of_birth");

                    String pNo = Misc.getString(rs, "provider_no");
                    Map<String,String> row = new LinkedHashMap<>();
                    row.put("demoNo",       demoNo);
                    row.put("lastName",     Misc.toUpperLowerCase(Misc.getString(rs, "last_name")));
                    row.put("firstName",    Misc.toUpperLowerCase(Misc.getString(rs, "first_name")));
                    row.put("age",          String.valueOf(age));
                    row.put("rosterStatus", Misc.getString(rs, "roster_status"));
                    row.put("patientStatus",Misc.getString(rs, "patient_status"));
                    row.put("sex",          Misc.getString(rs, "sex"));
                    row.put("dob",          dob);
                    row.put("providerName", providerBean.getProperty(pNo, ""));
                    patientRows.add(row);
                }
            }
        } catch (SQLException e) {
            logger.error("Database error during patient search", e);
            request.setAttribute("searchUnavailable", true);
            request.setAttribute("searchErrorMessage", "oscarMDS.patientSearch.errorDatabase");
        } catch (NumberFormatException e) {
            logger.error("Invalid numeric parameter during patient search", e);
            request.setAttribute("searchUnavailable", true);
            request.setAttribute("searchErrorMessage", "oscarMDS.patientSearch.errorInvalidParams");
        }
    }

    boolean isTruncated = (nItems == parsedLimit2 && parsedLimit2 == 500);
    int nextLimit1 = parsedLimit1 + nItems;

    request.setAttribute("patientRows",   patientRows);
    request.setAttribute("nItems",        nItems);
    request.setAttribute("isTruncated",   isTruncated);
    request.setAttribute("labNo",         p_labNo);
    request.setAttribute("labType",       p_labType);
    request.setAttribute("from",          p_from);
    request.setAttribute("keyword",       p_keyword);
    request.setAttribute("searchMode",    p_searchMode);
    request.setAttribute("orderby",       p_orderby.isEmpty() ? "last_name" : p_orderby);
    request.setAttribute("nextLimit1",    nextLimit1);
    request.setAttribute("hasSearch",     hasSearch);
    request.setAttribute("prefillLastName",     isLabPrefillAvailable ? labPatientLastName : "");
    request.setAttribute("prefillFirstName",    isLabPrefillAvailable ? labPatientFirstName : "");
    request.setAttribute("prefillYearOfBirth",  isLabPrefillAvailable ? labPatientDobYear : "");
    request.setAttribute("prefillMonthOfBirth", isLabPrefillAvailable ? labPatientDobMonth : "");
    request.setAttribute("prefillDateOfBirth",  isLabPrefillAvailable ? labPatientDobDay : "");
    request.setAttribute("prefillSex",          isLabPrefillAvailable ? labPatientSex : "");
    request.setAttribute("prefillAddress",      isLabPrefillAvailable ? labPatientAddress : "");
    request.setAttribute("prefillCity",         isLabPrefillAvailable ? labPatientCity : "");
    request.setAttribute("prefillProvince",     isLabPrefillAvailable ? labPatientProvince : "");
    request.setAttribute("prefillPostal",       isLabPrefillAvailable ? labPatientPostal : "");
    request.setAttribute("prefillPhone",        isLabPrefillAvailable ? labPatientPhone : "");
    request.setAttribute("prefillHin",          isLabPrefillAvailable ? labPatientHin : "");
    request.setAttribute("prefillVer",          isLabPrefillAvailable ? labPatientHinVer : "");
    // PID-11 address province is not a health-card type; DemographicAdd applies the configured HC type default.
    request.setAttribute("prefillHcType",       "");
%>
<!DOCTYPE html>
<html lang="${pageContext.request.locale.language}">
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>
    <link rel="stylesheet" type="text/css" href="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.11/css/dataTables.bootstrap5.min.css">
    <script type="text/javascript" src="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.11/js/jquery.dataTables.min.js"></script>
    <script type="text/javascript" src="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.11/js/dataTables.bootstrap5.min.js"></script>
    <title><fmt:message key="oscarMDS.segmentDisplay.patientSearch.title"/></title>
    <style>
        body { font-size: 13px; }
        .bg-carlos-teal { background-color: #339999; }
        .btn-carlos-teal {
            background-color: #339999;
            color: #ffffff;
            border-color: #2b8080;
        }
        .btn-carlos-teal:hover,
        .btn-carlos-teal:focus {
            background-color: #2b8080;
            color: #ffffff;
            border-color: #226666;
        }
        .dataTables_wrapper .dataTables_filter input { min-width: 180px; }
        #patientsTable tbody tr { cursor: pointer; }
        #patientsTable tbody tr:hover { background-color: #e8f4f8 !important; }
    </style>
</head>
<body>
    <div class="container-fluid py-2 px-3">

        <%-- Page heading --%>
        <div class="bg-carlos-teal text-white fw-bold px-3 py-2 rounded-top mb-0">
            <fmt:message key="oscarMDS.segmentDisplay.patientSearch.title"/>
        </div>

        <%-- Search form --%>
        <div class="card rounded-0 rounded-bottom border-top-0 mb-2">
            <div class="card-body p-2">
                <form method="post" id="titlesearch" name="titlesearch"
                      action="${pageContext.request.contextPath}/oscarMDS/ViewPatientSearch">
                    <input type="hidden" name="from"        value="${carlos:forHtmlAttribute(from)}"/>
                    <input type="hidden" name="labNo"       value="${carlos:forHtmlAttribute(labNo)}"/>
                    <input type="hidden" name="labType"     value="${carlos:forHtmlAttribute(labType)}"/>
                    <input type="hidden" name="orderby"     value="last_name"/>
                    <input type="hidden" name="dboperation" value="search_titlename"/>
                    <input type="hidden" name="limit1"      value="0"/>
                    <input type="hidden" name="limit2"      value="500"/>
                    <input type="hidden" name="displaymode" value="Search"/>
                    <div class="row g-2 align-items-center flex-wrap">
                        <div class="col-auto">
                            <div class="d-flex flex-wrap gap-2">
                                <div class="form-check form-check-inline mb-0">
                                    <input class="form-check-input" type="radio" id="search_name"
                                           name="search_mode" value="search_name"
                                           ${searchMode == 'search_name' || searchMode == '' ? 'checked' : ''}>
                                    <label class="form-check-label" for="search_name">
                                        <fmt:message key="oscarMDS.segmentDisplay.patientSearch.formName"/>
                                    </label>
                                </div>
                                <div class="form-check form-check-inline mb-0">
                                    <input class="form-check-input" type="radio" id="search_phone"
                                           name="search_mode" value="search_phone"
                                           ${searchMode == 'search_phone' ? 'checked' : ''}>
                                    <label class="form-check-label" for="search_phone">
                                        <fmt:message key="oscarMDS.segmentDisplay.patientSearch.formPhone"/>
                                    </label>
                                </div>
                                <div class="form-check form-check-inline mb-0">
                                    <input class="form-check-input" type="radio" id="search_dob"
                                           name="search_mode" value="search_dob"
                                           ${searchMode == 'search_dob' ? 'checked' : ''}>
                                    <label class="form-check-label" for="search_dob">
                                        <fmt:message key="oscarMDS.segmentDisplay.patientSearch.formDOB"/>
                                    </label>
                                </div>
                                <div class="form-check form-check-inline mb-0">
                                    <input class="form-check-input" type="radio" id="search_address"
                                           name="search_mode" value="search_address"
                                           ${searchMode == 'search_address' ? 'checked' : ''}>
                                    <label class="form-check-label" for="search_address">
                                        <fmt:message key="oscarMDS.segmentDisplay.patientSearch.formAddress"/>
                                    </label>
                                </div>
                                <div class="form-check form-check-inline mb-0">
                                    <input class="form-check-input" type="radio" id="search_hin"
                                           name="search_mode" value="search_hin"
                                           ${searchMode == 'search_hin' ? 'checked' : ''}>
                                    <label class="form-check-label" for="search_hin">
                                        <fmt:message key="oscarMDS.segmentDisplay.patientSearch.formHIN"/>
                                    </label>
                                </div>
                            </div>
                        </div>
                        <div class="col-auto">
                            <input type="text" class="form-control form-control-sm" id="keyword" name="keyword"
                                   value="${carlos:forHtmlAttribute(keyword)}" size="20" maxlength="100">
                        </div>
                        <div class="col-auto">
                            <button type="submit" class="btn btn-sm btn-primary" name="displaymode" value="Search">
                                <fmt:message key="oscarMDS.segmentDisplay.patientSearch.btnSearch"/>
                            </button>
                        </div>
                    </div>
                </form>
            </div>
        </div>

        <%-- Search error alert: shown when database or validation errors occur --%>
        <c:if test="${searchUnavailable}">
            <div class="alert alert-danger py-2" role="alert">
                <strong>Search Error:</strong>
                <c:choose>
                    <c:when test="${not empty searchErrorMessage}">
                        <fmt:message key="${searchErrorMessage}"/>
                    </c:when>
                    <c:otherwise>
                        <fmt:message key="oscarMDS.patientSearch.errorGeneric"/>
                    </c:otherwise>
                </c:choose>
            </div>
        </c:if>

        <%-- Patient match error alert: shown when patient match operation fails --%>
        <div id="patientMatchError" class="alert alert-danger py-2" role="alert" style="display:none;">
            <strong>Error:</strong> <span id="patientMatchErrorMessage"></span>
        </div>

        <%-- Truncation alert: shown when 500 results were returned (limit reached) --%>
        <c:if test="${isTruncated}">
            <div class="alert alert-info d-flex justify-content-between align-items-center py-2" role="alert">
                <span><fmt:message key="oscarMDS.patientSearch.msgTruncated"/></span>
                <button type="button" class="btn btn-sm btn-outline-primary ms-3 text-nowrap" onclick="document.getElementById('loadMoreForm').submit()">
                    <fmt:message key="oscarMDS.patientSearch.btnLoadMore"/>
                </button>
            </div>
        </c:if>

        <%-- Patient results table --%>
        <c:if test="${hasSearch}">
            <div class="card mb-2">
                <div class="card-body p-2">
                    <p class="small text-muted mb-1">
                        <em><fmt:message key="oscarMDS.segmentDisplay.patientSearch.msgResults"/></em>:
                        <strong>${carlos:forHtmlContent(keyword)}</strong>
                    </p>
                    <table id="patientsTable" class="table table-sm table-bordered table-striped table-hover w-100">
                        <thead class="table-dark">
                            <tr>
                                <th><fmt:message key="oscarMDS.segmentDisplay.patientSearch.msgPatientId"/></th>
                                <th><fmt:message key="oscarMDS.segmentDisplay.patientSearch.msgLastName"/></th>
                                <th><fmt:message key="oscarMDS.segmentDisplay.patientSearch.msgFirstName"/></th>
                                <th><fmt:message key="oscarMDS.segmentDisplay.patientSearch.msgAge"/></th>
                                <th><fmt:message key="oscarMDS.segmentDisplay.patientSearch.msgRosterStatus"/></th>
                                <th><fmt:message key="oscarMDS.segmentDisplay.patientSearch.msgPatientStatus"/></th>
                                <th><fmt:message key="oscarMDS.segmentDisplay.patientSearch.msgSex"/></th>
                                <th><fmt:message key="oscarMDS.segmentDisplay.patientSearch.msgDOB"/></th>
                                <th><fmt:message key="oscarMDS.segmentDisplay.patientSearch.msgDoctor"/></th>
                            </tr>
                        </thead>
                        <tbody>
                            <c:forEach var="row" items="${patientRows}">
                                <tr onclick="selectPatient('${carlos:forJavaScriptAttribute(row.demoNo)}')">
                                    <td class="text-center">
                                        <button type="button" class="btn btn-sm btn-carlos-teal"
                                                onclick="selectPatient('${carlos:forJavaScriptAttribute(row.demoNo)}'); event.stopPropagation();">
                                            ${carlos:forHtmlContent(row.demoNo)}
                                        </button>
                                    </td>
                                    <td>${carlos:forHtmlContent(row.lastName)}</td>
                                    <td>${carlos:forHtmlContent(row.firstName)}</td>
                                    <td class="text-center">${carlos:forHtmlContent(row.age)}</td>
                                    <td>${carlos:forHtmlContent(row.rosterStatus)}</td>
                                    <td>${carlos:forHtmlContent(row.patientStatus)}</td>
                                    <td class="text-center">${carlos:forHtmlContent(row.sex)}</td>
                                    <td>${carlos:forHtmlContent(row.dob)}</td>
                                    <td>${carlos:forHtmlContent(row.providerName)}</td>
                                </tr>
                            </c:forEach>
                        </tbody>
                    </table>
                </div>
            </div>
        </c:if>

        <%-- Add as New Patient button --%>
        <div class="text-center my-2">
            <button type="button" class="btn btn-carlos-teal fw-bold"
                    onclick="openAddPatient()">
                <fmt:message key="oscarMDS.patientSearch.btnAddNewPatient"/>
            </button>
        </div>

        <%-- Select patient instruction --%>
        <p class="text-center small text-muted mt-1">
            <fmt:message key="oscarMDS.segmentDisplay.patientSearch.msgSearchMessage"/>
        </p>

    </div><%-- /container-fluid --%>

    <%-- Hidden form for patient selection via PatientMatch action --%>
    <form method="post" id="addform" name="addform"
          action="${pageContext.request.contextPath}/oscarMDS/PatientMatch">
        <input type="hidden" name="labNo"         value="${carlos:forHtmlAttribute(labNo)}"/>
        <input type="hidden" name="labType"       value="${carlos:forHtmlAttribute(labType)}"/>
        <input type="hidden" name="demographicNo" id="addformDemoNo" value=""/>
    </form>

    <%-- Hidden POST form for lab pre-fill to keep PHI out of the popup URL --%>
    <form method="post" id="addPatientForm" name="addPatientForm"
          action="${pageContext.request.contextPath}/demographic/DemographicAdd"
          target="addNewPatient">
        <input type="hidden" name="prefill_last_name"      value="${carlos:forHtmlAttribute(prefillLastName)}"/>
        <input type="hidden" name="prefill_first_name"     value="${carlos:forHtmlAttribute(prefillFirstName)}"/>
        <input type="hidden" name="prefill_year_of_birth"  value="${carlos:forHtmlAttribute(prefillYearOfBirth)}"/>
        <input type="hidden" name="prefill_month_of_birth" value="${carlos:forHtmlAttribute(prefillMonthOfBirth)}"/>
        <input type="hidden" name="prefill_date_of_birth"  value="${carlos:forHtmlAttribute(prefillDateOfBirth)}"/>
        <input type="hidden" name="prefill_sex"            value="${carlos:forHtmlAttribute(prefillSex)}"/>
        <input type="hidden" name="prefill_address"        value="${carlos:forHtmlAttribute(prefillAddress)}"/>
        <input type="hidden" name="prefill_city"           value="${carlos:forHtmlAttribute(prefillCity)}"/>
        <input type="hidden" name="prefill_province"       value="${carlos:forHtmlAttribute(prefillProvince)}"/>
        <input type="hidden" name="prefill_postal"         value="${carlos:forHtmlAttribute(prefillPostal)}"/>
        <input type="hidden" name="prefill_phone"          value="${carlos:forHtmlAttribute(prefillPhone)}"/>
        <input type="hidden" name="prefill_hin"            value="${carlos:forHtmlAttribute(prefillHin)}"/>
        <input type="hidden" name="prefill_ver"            value="${carlos:forHtmlAttribute(prefillVer)}"/>
        <input type="hidden" name="prefill_hc_type"        value="${carlos:forHtmlAttribute(prefillHcType)}"/>
    </form>

    <%-- Hidden form for Load More pagination (POST to avoid PHI in URL) --%>
    <form method="post" id="loadMoreForm" name="loadMoreForm"
          action="${pageContext.request.contextPath}/oscarMDS/ViewPatientSearch">
        <input type="hidden" name="keyword"       value="${carlos:forHtmlAttribute(keyword)}"/>
        <input type="hidden" name="search_mode"   value="${carlos:forHtmlAttribute(searchMode)}"/>
        <input type="hidden" name="displaymode"   value="Search"/>
        <input type="hidden" name="dboperation"   value="search_titlename"/>
        <input type="hidden" name="orderby"       value="${carlos:forHtmlAttribute(orderby)}"/>
        <input type="hidden" name="limit1"        value="${nextLimit1}"/>
        <input type="hidden" name="limit2"        value="500"/>
        <input type="hidden" name="from"          value="${carlos:forHtmlAttribute(from)}"/>
        <input type="hidden" name="labNo"         value="${carlos:forHtmlAttribute(labNo)}"/>
        <input type="hidden" name="labType"       value="${carlos:forHtmlAttribute(labType)}"/>
    </form>

    <fmt:message var="i18nDobFormat" key="oscarMDS.patientSearch.msgDobFormat"/>
    <fmt:message var="i18nPatientMatchError" key="oscarMDS.patientSearch.errorPatientMatch"/>
    <script>
        document.addEventListener('DOMContentLoaded', function () {
            // Resize popup window to accommodate DataTables with multiple results
            try { window.resizeTo(1200, 800); } catch (e) {}

            // Focus keyword field
            var kwField = document.getElementById('keyword');
            if (kwField) { kwField.focus(); kwField.select(); }

            // DataTables initialization
            if (document.getElementById('patientsTable')) {
                jQuery('#patientsTable').DataTable({
                    language: {
                        url: '${pageContext.request.contextPath}/library/DataTables/i18n/<fmt:message key="global.i18n.datatablescode"/>.json'
                    },
                    pageLength: 25,
                    order: [[1, 'asc']],
                    columnDefs: [
                        { orderable: false, targets: 0 } // patient ID button — not sortable
                    ]
                });
            }
        });

        // DOB format validation on form submit
        document.getElementById('titlesearch').addEventListener('submit', function (e) {
            var radios = document.getElementsByName('search_mode');
            for (var i = 0; i < radios.length; i++) {
                if (radios[i].checked && radios[i].value === 'search_dob') {
                    var dob = document.getElementById('keyword').value;
                    // Auto-format 8-digit input to YYYY-MM-DD
                    if (dob.length === 8 && /^\d{8}$/.test(dob)) {
                        dob = dob.substring(0, 4) + '-' + dob.substring(4, 6) + '-' + dob.substring(6, 8);
                        document.getElementById('keyword').value = dob;
                    }
                    // Validate YYYY-MM-DD format with strict range checks
                    var dobPattern = /^(\d{4})-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])$/;
                    if (!dobPattern.test(document.getElementById('keyword').value)) {
                        alert('${carlos:forJavaScript(i18nDobFormat)}');
                        e.preventDefault();
                        return false;
                    }
                    // Additional validation: ensure date components are valid
                    var parts = document.getElementById('keyword').value.match(dobPattern);
                    if (parts) {
                        var year = parseInt(parts[1], 10);
                        var month = parseInt(parts[2], 10);
                        var day = parseInt(parts[3], 10);
                        // Basic sanity check: year between 1900 and current year + 1
                        var currentYear = new Date().getFullYear();
                        if (year < 1900 || year > currentYear + 1) {
                            alert('${carlos:forJavaScript(i18nDobFormat)}');
                            e.preventDefault();
                            return false;
                        }
                        // Validate day is valid for the month (simplified check)
                        if (month === 2 && day > 29) {
                            alert('${carlos:forJavaScript(i18nDobFormat)}');
                            e.preventDefault();
                            return false;
                        }
                        if ([4, 6, 9, 11].indexOf(month) !== -1 && day > 30) {
                            alert('${carlos:forJavaScript(i18nDobFormat)}');
                            e.preventDefault();
                            return false;
                        }
                    }
                    break;
                }
            }
        });

        function openAddPatient() {
            const addPatientForm = document.getElementById('addPatientForm');
            const originalTarget = addPatientForm.getAttribute('target');
            const popup = window.open('', 'addNewPatient', 'scrollbars=yes,resizable=yes,width=900,height=700');
            try {
                if (!popup) {
                    addPatientForm.removeAttribute('target');
                } else {
                    addPatientForm.target = 'addNewPatient';
                }
                addPatientForm.submit();
            } finally {
                if (originalTarget !== null) {
                    addPatientForm.setAttribute('target', originalTarget);
                } else {
                    addPatientForm.removeAttribute('target');
                }
            }
            if (popup && !popup.closed) {
                popup.focus();
            }
        }

        // Match patient: POST to PatientMatch, notify lab display and inboxhub, then close popup
        function selectPatient(demoNo) {
            var labNo = '${carlos:forJavaScript(labNo)}';
            var labType = '${carlos:forJavaScript(labType)}';
            var csrfToken = document.querySelector('input[name="CSRF-TOKEN"]');
            var token = csrfToken ? csrfToken.value : '';
            var body = 'labNo=' + encodeURIComponent(labNo)
                     + '&labType=' + encodeURIComponent(labType)
                     + '&demographicNo=' + encodeURIComponent(demoNo)
                     + '&CSRF-TOKEN=' + encodeURIComponent(token);
            fetch('${pageContext.request.contextPath}/oscarMDS/PatientMatch', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: body
            }).then(function(response) {
                if (!response.ok) {
                    throw new Error('PatientMatch failed');
                }
                // Notify lab display row (may be null due to COOP on action-served pages)
                if (window.opener && typeof window.opener.updateLabDemoStatus === 'function') {
                    try { window.opener.updateLabDemoStatus(labNo); } catch (e) {}
                }
                // Notify inboxhub list to refresh (BroadcastChannel is unaffected by COOP)
                try {
                    var bc = new BroadcastChannel('inboxhub-refresh');
                    bc.postMessage('refresh');
                    bc.close();
                } catch (e) {}
                window.close();
            }).catch(function(error) {
                // Display error message and keep popup open for retry
                console.error('Patient match error:', error);
                var errorDiv = document.getElementById('patientMatchError');
                var errorMsg = document.getElementById('patientMatchErrorMessage');
                if (errorDiv && errorMsg) {
                    errorMsg.textContent = '${carlos:forJavaScript(i18nPatientMatchError)}';
                    errorDiv.style.display = 'block';
                    // Scroll to error message
                    errorDiv.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                }
            });
        }

        // Legacy addName compatibility (used by some older callers)
        function addName(lastname, firstname, chartno) {
            var from = '${carlos:forJavaScript(from)}';
            if (!from.startsWith('/') || from.startsWith('//')) {
                from = '${pageContext.request.contextPath}/oscarMDS/ViewPatientSearch';
            }
            var addform = document.getElementById('addform');
            addform.method = 'post';
            addform.action = from;

            // Create or update hidden inputs for POST data
            var nameInput = addform.querySelector('input[name="name"]');
            if (!nameInput) {
                nameInput = document.createElement('input');
                nameInput.type = 'hidden';
                nameInput.name = 'name';
                addform.appendChild(nameInput);
            }
            nameInput.value = lastname + ',' + firstname;

            var chartNoInput = addform.querySelector('input[name="chart_no"]');
            if (!chartNoInput) {
                chartNoInput = document.createElement('input');
                chartNoInput.type = 'hidden';
                chartNoInput.name = 'chart_no';
                addform.appendChild(chartNoInput);
            }
            chartNoInput.value = chartno;

            var bFirstDispInput = addform.querySelector('input[name="bFirstDisp"]');
            if (!bFirstDispInput) {
                bFirstDispInput = document.createElement('input');
                bFirstDispInput.type = 'hidden';
                bFirstDispInput.name = 'bFirstDisp';
                addform.appendChild(bFirstDispInput);
            }
            bFirstDispInput.value = 'false';

            addform.submit();
        }
    </script>
</body>
</html>
