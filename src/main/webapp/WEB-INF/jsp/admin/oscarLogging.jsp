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
    Purpose:
      Displays CARLOS EMR logging reports for authorized administrators.

    Features:
      - Selects a report date and report type.
      - Validates report request parameters server-side.
      - Reads logging report files only from the configured LOGGING_PATH.

    Parameters:
      - reportDate: Optional report date in YYYY-MM-DD format.
      - reportType: Optional report type; supported values are "general" and "mysql".

    @since 2026
--%>
<%@ include file="/taglibs.jsp" %>
<fmt:setBundle basename="oscarResources"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"
       scope="request"/>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.reporting" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_admin&type=_admin.reporting");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>
<!DOCTYPE html>
<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <title><fmt:message key="admin.oscarLogging.title"/></title>
    <link rel="stylesheet" href="<%=request.getContextPath() %>/css/fontawesome-all.min.css">
    <link href="<%=request.getContextPath() %>/library/flatpickr/flatpickr.min.css" rel="stylesheet">
    <script src="<%=request.getContextPath() %>/library/flatpickr/flatpickr.min.js"></script>
</head>
<body>


<%@page import="java.io.File" %>
<%@page import="org.apache.commons.io.FileUtils" %>
<%@page import="io.github.carlos_emr.carlos.util.*, io.github.carlos_emr.*, java.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.util.UtilDateUtilities" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ page import="io.github.carlos_emr.carlos.utility.PathValidationUtils" %>
<div class="pb-2 mt-4 mb-3 border-bottom">
    <h4>
        <fmt:message key="admin.oscarLogging.heading"/>
    </h4>
</div>

<%
    String reportDate = request.getParameter("reportDate");
    String reportType = request.getParameter("reportType");
    
    if (reportDate != null && !reportDate.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid date format");
        return;
    }

    boolean runReport;
    if (reportDate == null) {
        reportDate = UtilDateUtilities.getToday("yyyy-MM-dd");
        runReport = false;
    } else {
        runReport = true;
    }
    if (reportType == null) {
        reportType = "general";
    }
%>
<form id="logForm" action="${ctx}/admin/ViewOscarLogging" class="card card-body bg-body-tertiary">

    <fieldset>
        <h4>
            <fmt:message key="admin.oscarLogging.viewHeading"/> <br>
            <small><fmt:message key="admin.oscarLogging.subheading"/></small>
        </h4>
        <div class="mb-3">
            <label class="form-label"><fmt:message key="admin.oscarLogging.date"/></label>
            <div>
                <input type="text" id="reportDate" name="reportDate" class="form-control"
                       size="10" value="<carlos:encode value='<%= reportDate %>' context="htmlAttribute"/>">
            </div>
        </div>
        <div class="mb-3">
            <label class="form-label"><fmt:message key="admin.oscarLogging.selectReport"/></label>
            <div>
                <select name="reportType" id="reportType" class="form-select">
                    <option value="general" <%if (reportType.equals("general")) {%>
                            selected <%}%>><fmt:message key="admin.oscarLogging.generalReport"/>
                    </option>
                    <option value="mysql" <%if (reportType.equals("mysql")) {%>
                            selected <%}%>><fmt:message key="admin.oscarLogging.mysqlReport"/>
                    </option>
                </select>
            </div>
        </div>
        <div class="mb-3">
            <div>
                <button type="submit" class="btn btn-primary">
                    <i class="fa-solid fa-download"></i> <fmt:message key="admin.oscarLogging.getReport"/>
                </button>
            </div>
        </div>
    </fieldset>
</form>

<%
    if (runReport) {
        Properties pr = CarlosProperties.getInstance();
        String path = pr.getProperty("LOGGING_PATH");

        if (path == null || path.trim().isEmpty()) {
            out.write("<div class=\"alert alert-danger\">Logging path is not configured.</div>");
            return;
        }

        String suffix = reportDate.replaceAll("-", "");
        String reportFileName = "";

        if (reportType.equals("general")) {
            reportFileName = "report" + suffix + ".html";
        } else if (reportType.equals("mysql")) {
            reportFileName = "reportmysql" + suffix + ".html";
        } else {
            out.write("<div class=\"alert alert-danger\">Invalid report type.</div>");
            return;
        }

        try {
            File requestedFile = PathValidationUtils.validateExistingPath(
                new File(new File(path), reportFileName),
                new File(path)
            );

            if (requestedFile.exists() && requestedFile.isFile()) {
                String temp = FileUtils.readFileToString(requestedFile, "UTF-8");
                pageContext.setAttribute("logResults", temp);
%>
                <pre id="log-results"><carlos:encode value="${logResults}" context="html"/></pre>
<%
            }
        } catch (SecurityException e) {
            out.write("<div class=\"alert alert-danger\">Invalid file path.</div>");
            return;
        } catch (java.io.IOException e) {
            out.write("<div class=\"alert alert-danger\">Error reading log file.</div>");
            return;
        }
    }
%>

<script>
    flatpickr("#reportDate", {dateFormat: "Y-m-d", allowInput: true});
    flatpickr("#endDate", {dateFormat: "m/Y", allowInput: true});

    $(document).ready(function () {
        $('#logForm').validate(
            {
                rules: {
                    reportDate: {
                        required: true,
                        oscarDate: true
                    }
                }
            });
    });

    registerFormSubmit('logForm', 'dynamic-content');

</script>
</body>
</html>
