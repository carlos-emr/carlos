<%--

    Copyright (c) 2008-2012 Indivica Inc.

    This software is made available under the terms of the
    GNU General Public License, Version 2, 1991 (GPLv2).
    License details are available via "indivica.ca/gplv2"
    and "gnu.org/licenses/gpl-2.0.html".


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
<%--
    Hospital Report Manager - Provider Confidentiality Statement

    Purpose: Allows providers to view and update their HRM confidentiality statement.
             The SFTP integration for fetching new reports from Ontario MD has been removed.
             This page provides read-only access to the confidentiality statement configuration.

    Features:
        - Display provider's current confidentiality statement
        - Edit and save confidentiality statement
        - Form validation and CSRF protection

    Parameters:
        - statementSuccess (request attribute): Boolean indicating save result

    @since 2006-04-20
--%>
<!DOCTYPE html>

<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@page import="org.owasp.encoder.Encode" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.misc" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin&type=_admin.misc");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
%>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="java.util.*" %>
<%@ page
        import="io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMProviderConfidentialityStatementDao" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<html>
    <head>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Hospital Report Manager</title>
        <style>
            body {
                margin-left: 30px !important;
            }
        </style>

        <script src="<%= request.getContextPath() %>/js/global.js"></script>

        <link href="<%=request.getContextPath() %>/css/bootstrap.css" rel="stylesheet" type="text/css">
        <link rel="stylesheet" type="text/css"
              href="${ pageContext.request.contextPath }/hospitalReportManager/inbox.css">


    </head>
    <body>
    <div class="container">
        <h4>Hospital Report Manager</h4>

        <%
            HRMProviderConfidentialityStatementDao hrmProviderConfidentialityStatementDao = (HRMProviderConfidentialityStatementDao) SpringUtils.getBean(HRMProviderConfidentialityStatementDao.class);
            String statement = hrmProviderConfidentialityStatementDao.getConfidentialityStatementForProvider(loggedInInfo.getLoggedInProviderNo());
        %>
        <form action="<%=request.getContextPath() %>/hospitalReportManager/Statement.do" method="post">
            <div class="control-group">
                <label class="control-label">Provider Confidentiality Statement</label>
                <div class="controls">
                    <textarea name="statement"><%= Encode.forHtml(statement != null ? statement : "") %></textarea>
                </div>
            </div>
            <div>
                <input type="submit" class="btn btn-primary" name="submit" value="Save Statement">
                <% if (request.getAttribute("statementSuccess") != null && (Boolean) request.getAttribute("statementSuccess")) { %>
                Success
                <% } else if (request.getAttribute("statementSuccess") != null && !((Boolean) request.getAttribute("statementSuccess"))) { %>
                Error
                <% } %>
            </div>
        </form>
    </div>
    </body>
</html>