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

<%@ page
        import="java.util.*,ca.openosp.openo.report.reportByTemplate.*,java.sql.*, org.apache.commons.text.StringEscapeUtils" %>
<%@ page import="ca.openosp.openo.report.reportByTemplate.ReportObjectGeneric" %>
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
        <title>Report by Template</title>

        <link href="${pageContext.request.contextPath}/css/bootstrap.css" rel="stylesheet">
        <link href="${pageContext.request.contextPath}/css/DT_bootstrap.css" rel="stylesheet">
        <link href="${pageContext.request.contextPath}/css/bootstrap-responsive.css" rel="stylesheet">
        <link href="${pageContext.request.contextPath}/library/DataTables-1.10.12/media/css/jquery.dataTables.min.css"
              rel="stylesheet">
        <script src="${pageContext.request.contextPath}/share/javascript/Oscar.js"></script>
        <script src="${pageContext.request.contextPath}/library/jquery/jquery-3.6.4.min.js"></script>
        <script src="${pageContext.request.contextPath}/js/bootstrap.min.2.js"></script>
        <script src="${ pageContext.request.contextPath }/library/DataTables/datatables.min.js"></script><!-- 1.13.4 -->

        <script>
            function clearSession() {
                new Ajax.Request('clearSession.jsp', '{asynchronous:true}');
            }

            jQuery(document).ready(function () {
                jQuery('.reportTable').DataTable(
                    {
                        "aLengthMenu": [[-1, 10, 25, 50, 100], ["All", 10, 25, 50, 100]]
                    }
                );
            });
        </script>
        <style media="print">
            .noprint, .showhidequery, .sqlBorderDiv, .controls, .dataTables_length, .dataTables_filter, .dataTables_paginate {
                display: none;
            }

            div.sub-actions a + .result-btn {
                display: inline-block;
                padding-left: 5px;
                border-left: #0088cc 2px solid;
            }

        </style>
    </head>
    <%

        ReportObjectGeneric curreport = (ReportObjectGeneric) request.getAttribute("reportobject");
        Integer sequenceLength = (Integer) request.getAttribute("sequenceLength");
        List<String> sqlList = new ArrayList<String>();
        List<String> htmlList = new ArrayList<String>();
        List<String> csvList = new ArrayList<String>();

        if (curreport.isSequence()) {
            for (int x = 0; x < sequenceLength; x++) {
                sqlList.add((String) request.getAttribute("sql-" + x));
                htmlList.add((String) request.getAttribute("resultsethtml-" + x));
                csvList.add((String) request.getAttribute("csv-" + x));
            }
        } else {
            sqlList.add((String) request.getAttribute("sql"));
            htmlList.add((String) request.getAttribute("resultsethtml"));
            csvList.add((String) request.getAttribute("csv"));
        }

        pageContext.setAttribute("htmlList", htmlList);

    %>

    <body onunload="clearSession();">

        <%@ include file="rbtTopNav.jspf" %>

    <h3>
        <c:out value="${ reportobject.title }"/><br>
        <small><c:out value="${ reportobject.description }"/></small>
    </h3>

    <div class="reportBorderDiv row-fluid">
        <c:forEach items="${ htmlList }" var="htmlOut">
            <c:choose>
                <c:when test="${ not fn:startsWith(htmlOut, '<table') }">
                    <div class="alert alert-error">
                        <a href="#" data-dismiss="alert" class="close">&times;</a>
                        <c:out value="${ htmlOut }"/>
                    </div>
                </c:when>
                <c:otherwise>
                    ${ htmlOut }
                </c:otherwise>
            </c:choose>
        </c:forEach>
    </div>

    <div class="noprint form-actions">

        <div style="margin-bottom:15px;" class="controls controls-row">
            <input type="button" class="btn btn-primary" value="Back"
                   onclick="document.location='reportConfiguration.jsp?templateid=${ reportobject.templateId }'">
            <input type="button" class="btn btn-primary" value="Print" onclick="window.print();">

            <%
                for (int x = 0; x < csvList.size(); x++) {
            %>

            <form style="display:inline;" action="${pageContext.request.contextPath}/oscarReport/reportByTemplate/generateOutFilesAction.do" method="post">
                <%if (x > 1) { %>
                <label><%=(x + 1)%>
                </label>
                <%}%>
                <input type="hidden" class="btn" name="csv" value="<%=StringEscapeUtils.escapeHtml4(csvList.get(x))%>">
                <input type="submit" class="btn" name="getCSV" value="Export to CSV">
                <input type="submit" class="btn" name="getXLS" value="Export to XLS">
            </form>

            <% } %>
        </div>
        <div class="row-fluid sub-actions">
            <a href="#" class="showhidequery result-btn" onclick="showHideItem('sqlDiv')">
                Show/Hide Query
            </a>
            <a href="javascript:void(0)" class="edit result-btn"
               style="padding-left: 5px;border-left:#0088cc 2px solid;"
               onclick="document.location='addEditTemplate.jsp?templateid=${ reportobject.templateId }&opentext=1'">
                Edit Template
            </a>
            <div class="sqlBorderDiv" id="sqlDiv" style="display:none;background-color:white;padding:5px;">
                <samp style="font-size: 11px;">
                    <%
                        for (int x = 0; x < sqlList.size(); x++) {
                            out.println((x + 1) + ")" + org.apache.commons.text.StringEscapeUtils.escapeHtml4(sqlList.get(x).trim()));
                        }
                    %>
                </samp>
            </div>
        </div>
    </div>

</html>