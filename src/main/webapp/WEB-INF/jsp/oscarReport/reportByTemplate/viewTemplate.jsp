<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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

    CARLOS EMR Project
    https://github.com/carlos-emr/carlos
--%>
<%--
  Page role: Renders `viewTemplate.jsp` for the reporting workflow.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<%
    if (session.getAttribute("user") == null) response.sendRedirect(request.getContextPath() + "/logoutPage");
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
%>
<%@ page import="io.github.carlos_emr.carlos.report.reportByTemplate.*" %>
<%@ page import="io.github.carlos_emr.carlos.report.reportByTemplate.ReportManager" %>
<%@ page import="io.github.carlos_emr.carlos.report.reportByTemplate.ReportObject" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>

<%@ taglib uri="jakarta.tags.core" prefix="c" %>

<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<security:oscarSec roleName="<%=roleName$%>"
                   objectName="_admin,_report" rights="r" reverse="<%=true%>">
    <%
        response.sendRedirect(request.getContextPath() + "/logoutPage");
    %>
</security:oscarSec>
<!DOCTYPE html>

<html>
    <head>
        <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet" type="text/css">
        <link href="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.11/css/dataTables.bootstrap5.min.css" rel="stylesheet" type="text/css">
        <script type="text/javascript" src="${pageContext.servletContext.contextPath}/library/jquery/jquery-3.7.1.min.js"></script>
        <script src="${pageContext.servletContext.contextPath}/library/jquery/jquery-compat.js"></script>
        <script src="${pageContext.request.contextPath}/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>

    </head>
    <%
        String templateid = request.getParameter("templateid");
        if (templateid == null) {
            templateid = (String) request.getAttribute("templateid");
        }
        ReportManager reportManager = new ReportManager();
        ReportObject curreport = reportManager.getReportTemplateNoParam(templateid);
        String xml = reportManager.getTemplateXml(templateid);
        pageContext.setAttribute("curreport", curreport);
    %>

    <body>

    <%@ include file="rbtTopNav.jspf" %>
    <h3>
        ${carlos:forHtml(curreport.title)}<br/>
        <small>${carlos:forHtml(curreport.description)}</small>
    </h3>


    <%if (templateid == null) { %>
    <jsp:forward page="/oscarReport/reportByTemplate/ViewHomePage"/>
    <%}%>

    <div class="xmlBorderDiv">
        <pre style="font-size: 11px;"><carlos:encode value='<%= xml %>' context="html"/></pre>
    </div>

    <div id="viewTemplateActions" class="form-actions noprint">
        <input type="button" class="btn btn-secondary" value="Back" onclick="javascript: window.history.back();return false;"/>
        <input type="button" class="btn btn-secondary" value="Print" onclick="javascript: window.print();"/>
        <input type="button" class="btn btn-primary" value="Edit"
               onclick="document.location='<%= request.getContextPath() %>/oscarReport/reportByTemplate/ViewAddEditTemplate?templateid=<carlos:encode value='<%= templateid %>' context="uriComponent"/>&opentext=1'"/>
    </div>

</html>
