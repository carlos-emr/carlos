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

<%@ page import="ca.openosp.openo.report.reportByTemplate.*, java.sql.*, org.apache.commons.lang3.StringUtils" %>
<%@ page import="ca.openosp.openo.report.reportByTemplate.ReportManager" %>
<%@ page import="ca.openosp.openo.report.reportByTemplate.ReportObject" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

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

        <script src="${pageContext.servletContext.contextPath}/library/jquery/jquery-1.12.0.min.js"></script>
        <script src="${pageContext.request.contextPath}/library/jquery/jquery-ui-1.12.1.min.js"></script>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/library/jquery/jquery-ui.theme-1.12.1.min.css">
        <link rel="stylesheet"
              href="${pageContext.request.contextPath}/library/jquery/jquery-ui.structure-1.12.1.min.css">


        <script>
            $(function () {
                $(document).tooltip();
            });
        </script>

    </head>

    <body>

    <%@ include file="rbtTopNav.jspf" %>

    <%
        // can be three options: add/edit/upload
        // add --> no relevant request parameters/attributes
        // edit --> templateid in request parameter or attribute
        // upload --> templateXML in request parameter and no templateid
        ReportManager rm = new ReportManager();
        String action = "add";
        // try templateid from attribute and parameter - check to see if we are editing or adding
        String templateid = "";
        String templatexml = "";
        if ((request.getAttribute("templateid") != null) && (!((String) request.getAttribute("templateid")).equals(""))) {
            templateid = (String) request.getAttribute("templateid");
            templatexml = rm.getTemplateXml(templateid);
            action = "edit";
        } else if ((request.getParameter("templateid") != null) && (!request.getParameter("templateid").equals(""))) {
            templateid = request.getParameter("templateid");
            templatexml = rm.getTemplateXml(templateid);
            action = "edit";
        }

        if ("edit".equals(action) && templateid != null) {
            ReportObject curreport = rm.getReportTemplateNoParam(templateid);
            pageContext.setAttribute("curreport", curreport);
            pageContext.setAttribute("templatexml", templatexml);
        }
        pageContext.setAttribute("action", action);
        pageContext.setAttribute("templateid", templateid);
    %>

    <h3>
        <%=StringUtils.capitalize(action)%>

        <c:if test="${ action eq 'add' }">
            Template
        </c:if>
        <c:if test="${ action eq 'edit' }">
            : <c:out value="${ curreport.title }"/><br/>
            <small><c:out value="${ curreport.description }"/></small>
        </c:if>
    </h3>

    <c:if test="${ not empty message }">
        <c:choose>
            <c:when test="${ not fn:startsWith(fn:toLowerCase(message), 'error') and not fn:startsWith(fn:toLowerCase(message), 'exception')}">
                <div class="alert alert-success">
                    <a href="#" data-dismiss="alert" class="close">&times;</a>
                    <c:out value="${ message }"/>
                </div>
            </c:when>
            <c:otherwise>
                <div class="alert alert-error">
                    <a href="#" data-dismiss="alert" class="close">&times;</a>
                    <c:out value="${ message }"/>
                </div>
            </c:otherwise>
        </c:choose>
    </c:if>
    <c:if test="${ empty opentext and empty param.opentext }">
        <!-- <form class="form-horizontal" action="<%=request.getContextPath() %>/oscarReport/reportByTemplate/uploadTemplates.do"
                        enctype="multipart/form-data">
        <div class="row-fluid">
        <div class="well">
        <div class="control-group">
        <label class="control-label" for="uploadReportXml">Select template</label>
        <div class="controls">
        <input type="file" id="uploadReportXml" class="input-file" name="templateFile" title="Upload a formatted template file. The extension is usually xml or txt">
        </div>
        </div>
        <input type="hidden" name="action" value="${ action }">
        <input type="hidden" name="opentext" value="${ empty opentext ? param.opentext : opentext }">
        <input type="hidden" name="templateid" value="${ templateid }">
        <input type="hidden" name="uuid" value="${ curreport.uuid }">
        <div class="control-group">
        <div class="controls">
        <input type="submit" class="btn btn-primary pull-right" value="Upload & <%=StringUtils.capitalize(action)%>">
        </div>
        </div>
        </div>
        </div>
    </form> -->
        <form class="form-horizontal" action="${pageContext.request.contextPath}/oscarReport/reportByTemplate/uploadTemplates.do"
                   method="post" enctype="multipart/form-data" onsubmit="return validateFileUpload()">
            <div class="row-fluid">
                <div class="well">
                    <div class="control-group">
                        <label class="control-label" for="uploadReportXml">Select template</label>
                        <div class="controls">
                            <input type="file" id="uploadReportXml" class="input-file" name="templateFile"
                                   title="Upload a formatted template file. The extension is usually xml or txt">
                        </div>
                    </div>
                    <input type="hidden" name="action" value="${ action }">
                    <input type="hidden" name="opentext" value="${ empty opentext ? param.opentext : opentext }">
                    <input type="hidden" name="templateid" value="${ templateid }">
                    <input type="hidden" name="uuid" value="${ curreport.uuid }">
                    <div class="control-group">
                        <div class="controls">
                            <input type="submit" class="btn btn-primary pull-right"
                                   value="Upload & <%=StringUtils.capitalize(action)%>">
                        </div>
                    </div>
                </div>
            </div>
        </form>
    </c:if>
    <c:if test="${ opentext eq '1' or param.opentext eq '1' }">

        <form class="form" action="${pageContext.request.contextPath}/oscarReport/reportByTemplate/addEditTemplatesAction.do" method="post">
            <div class="row-fluid">
                <div class="well">
                    <textarea id="xmltext" name="xmltext"
                              style="width:99%;height:300px;overflow-y:scroll;">${ templatexml }</textarea>
                    <input type="hidden" name="action" value="${ action }">
                    <input type="hidden" name="opentext" value="${ empty opentext ? param.opentext : opentext }">
                    <input type="hidden" name="templateid" value="${ templateid }">
                    <input type="hidden" name="uuid" value="${ curreport.uuid }">
                </div>

                <div class="form-actions">
                    <input type="submit" class="btn pull-right" value="Save">

                    <c:if test="${ action eq 'edit' }">
                        <input type="submit" class="btn btn-primary pull-right" name="done" value="Done">
                    </c:if>
                    <c:if test="${ action ne 'edit' }">
                        <input type="button" class="btn pull-right" name="cancel" value="Cancel"
                               onclick="document.location='homePage.jsp'">
                    </c:if>
                </div>
            </div>
        </form>

    </c:if>

    <script type="text/javascript">
        jQuery("#xmltext").on("keyup", function () {
            jQuery(".alert").hide();
        });
    </script>

    <script type="text/javascript">
        function validateFileUpload() {
            var fileUpload = document.getElementById('uploadReportXml');
            if (fileUpload.files.length == 0) {
                alert('Please upload a file before submitting the form.');
                return false;
            }
            return true;
        }

        jQuery("#xmltext").on("keyup", function () {
            jQuery(".alert").hide();
        });
    </script>

    </body>
</html>