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

--%>

<%
    if (session.getValue("user") == null) response.sendRedirect(request.getContextPath() + "/logout.jsp");
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
%>

<%@ page import="ca.openosp.openo.report.reportByTemplate.*, org.apache.commons.text.StringEscapeUtils" %>
<%@ page import="ca.openosp.openo.report.reportByTemplate.ReportManager" %>
<%@ page import="ca.openosp.openo.report.reportByTemplate.ReportObject" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<security:oscarSec roleName="<%=roleName$%>"
                   objectName="_admin,_report" rights="r" reverse="<%=true%>">
    <%
        response.sendRedirect(request.getContextPath() + "/logout.jsp");
    %>
</security:oscarSec>
<!DOCTYPE html>

<html>
    <head>
        <link href="${pageContext.request.contextPath}/css/bootstrap.css" rel="stylesheet" type="text/css">
        <link href="${pageContext.request.contextPath}/css/DT_bootstrap.css" rel="stylesheet" type="text/css">
        <link href="${pageContext.request.contextPath}/css/bootstrap-responsive.css" rel="stylesheet" type="text/css">
        <script type="text/javascript" src="${pageContext.servletContext.contextPath}/js/jquery-1.9.1.min.js"></script>
        <script src="${pageContext.request.contextPath}/js/bootstrap.min.2.js"></script>

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
        <c:out value="${ curreport.title }"/><br/>
        <small><c:out value="${ curreport.description }"/></small>
    </h3>


    <%if (templateid == null) { %>
    <jsp:forward page="homePage.jsp"/>
    <%}%>

    <div class="xmlBorderDiv">
        <pre style="font-size: 11px;"><%=StringEscapeUtils.escapeHtml4(xml)%></pre>
    </div>

    <div id="viewTemplateActions" class="form-actions noprint">
        <input type="button" class="btn" value="Back" onclick="javascript: window.history.back();return false;"/>
        <input type="button" class="btn" value="Print" onclick="javascript: window.print();"/>
        <input type="button" class="btn btn-primary" value="Edit"
               onclick="document.location='addEditTemplate.jsp?templateid=<%=templateid%>&opentext=1'"/>
    </div>

</html>
