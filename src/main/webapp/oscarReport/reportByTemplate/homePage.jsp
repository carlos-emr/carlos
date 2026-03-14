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

<%
    if (session.getValue("user") == null) response.sendRedirect(request.getContextPath() + "/logout.jsp");
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
%>

<%@ page import="java.util.*,io.github.carlos_emr.carlos.report.reportByTemplate.*,io.github.carlos_emr.carlos.managers.RBTGroupManager" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.RBTGroup" %>
<%@ page import="io.github.carlos_emr.carlos.report.reportByTemplate.ReportManager" %>
<%@ page import="io.github.carlos_emr.carlos.report.reportByTemplate.ReportObject" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>

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

        <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.3/css/bootstrap.min.css" rel="stylesheet" type="text/css">
        <link href="${pageContext.request.contextPath}/css/DT_bootstrap.css" rel="stylesheet" type="text/css">
        <script src="${pageContext.servletContext.contextPath}/library/jquery/jquery-3.6.4.min.js"></script>
        <script src="${pageContext.servletContext.contextPath}/library/bootstrap/5.3.3/js/bootstrap.bundle.min.js"></script>
        <script src="${pageContext.servletContext.contextPath}/js/global.js"></script>

    </head>

    <body>

    <%@ include file="rbtTopNav.jspf" %>

    <h3>Template Library</h3>  <!-- add to oscarResources_en.properties? -->
    <%
        RBTGroupManager rbtGroupManager = SpringUtils.getBean(RBTGroupManager.class);

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        List<String> templateGroups = rbtGroupManager.getTemplateGroups(loggedInInfo);
        String value = "";
    %>
    <div class="row">
        <form class="d-flex flex-wrap align-items-center gap-2">
            <div class="input-group">
                <span class="input-group-text">Group</span>
                <select name="templates" id="viewSelect">
                    <option value="all">All Templates</option>

                    <%
                        for (int i = 0; i < templateGroups.size(); i++) {

                            List<RBTGroup> templatesInGroup = rbtGroupManager.getGroup(loggedInInfo, (templateGroups.get(i)));
                            value = "";
                            for (int x = 0; x < templatesInGroup.size(); x++) {
                                value = value + String.valueOf((templatesInGroup.get(x)).getTemplateId()) + ",";
                            }
                    %>

                    <option value="<%=value%>"><%=templateGroups.get(i)%>
                    </option>
                    <% }%>
                </select>
            </div>
            <input type="text" id="userSearch" placeholder="Search"/>
        </form>
    </div>

    <div class="row">

        <table class="table table-sm table-striped table-hover" style="font-size:14px;" id="rbtTable">
            <thead>
            <tr>
                <th onclick="sortTable(0)"><a class="contentLink">#</a></th>
                <th onclick="sortTable(1)"><a class="contentLink">Template Name</a></th>
                <th onclick="sortTable(2)"><a class="contentLink">Description</a></th>
            </tr>
            </thead>

            <tbody id="tableData">
            <%
                ArrayList templates = (new ReportManager()).getReportTemplatesNoParam();
                String templateViewId = request.getParameter("templateviewid");
                if (templateViewId == null) templateViewId = "";
            %>
            <%
                for (int i = 0; i < templates.size(); i++) {
                    ReportObject curReport = (ReportObject) templates.get(i);
            %>
            <tr id="t<%=i%>">
                <td align="center"><%=String.valueOf(i + 1)%>
                </td>
                <td><a style="display:block;outline:none;"
                       href="reportConfiguration.jsp?templateid=<%=curReport.getTemplateId()%>"><%=curReport.getTitle()%>
                </a></td>
                <td><%=curReport.getDescription()%>
                </td>
                <td style="display:none;" id="<%=curReport.getTemplateId()%>"></td>
            </tr>
            <% } %>

            <%if (templates.isEmpty()) {%>
            <tr>
                <td align="center">0</td>
                <td>No Templates</td>
                <td>No templates in the database, please create a template file by clicking on "Add Template"</td>
            </tr>
            <%}%>
            </tbody>
        </table>
    </div>

    <script type="text/javascript">
        //Table Display
        jQuery("#viewSelect").on("change", function () {
            updateTableDisplay();
        });

        function updateTableDisplay() {
            var select = document.getElementById("viewSelect");
            var value = select.value;
            value = value.substring(0, value.length - 1);
            var array = value.split(',');
            var table = document.getElementById("tableData");
            var rows = table.rows;
            var index = "";
            var i, tid;

            for (i = 0; i < (rows.length); i++) {
                tid = rows[i].getElementsByTagName("TD")[3].id.toString();
                index = rows[i].id.toString();
                if ((array.includes(tid, 1)) || value == "al") {
                    document.getElementById(index).style.display = 'table-row';
                } else {
                    document.getElementById(index).style.display = 'none';
                }
            }
        }

        function inGroup(element) {
            var select = document.getElementById("viewSelect");
            var value = select.value;
            value = value.substring(0, value.length - 1);
            var array = value.split(',');
            var bool = false;
            var tid;

            if (value == "al") {
                return true;
            }

            tid = element.getElementsByTagName("TD")[3].id.toString();
            if (array.includes(tid)) {
                bool = true;
            }
            return bool;
        }

        // Search  NB .context is a jQuery 1.3 - 2.4 property
        jQuery(document).ready(function () {
            jQuery("#userSearch").on("keyup", function () {
                var value = jQuery(this).val().toLowerCase();
                jQuery("#tableData tr").filter(
                    function () {
                        jQuery(this).toggle(
                            (jQuery(this).text().toLowerCase().indexOf(value) > -1) &&
                            inGroup(jQuery(this).context))
                    });
            });
        });

        // Sort by table columns
        function sortTable(n) {
            var table, rows, switching, i, x, y, shouldSwitch, dir, switchcount = 0;
            table = document.getElementById("tableData");
            switching = true;
            dir = "asc";

            while (switching) {
                switching = false;
                rows = table.rows;
                for (i = 0; i < (rows.length - 1); i++) {
                    shouldSwitch = false;
                    x = rows[i].getElementsByTagName("TD")[n];
                    y = rows[i + 1].getElementsByTagName("TD")[n];

                    if (n == 1) {
                        x = new DOMParser().parseFromString(x.innerHTML, "text/html").body.childNodes[0];
                        y = new DOMParser().parseFromString(y.innerHTML, "text/html").body.childNodes[0];
                    }

                    if (dir == "asc") {
                        /* If it is the first row then it is a number*/
                        if (n == 0) {
                            if (Number(x.innerHTML) > Number(y.innerHTML)) {
                                shouldSwitch = true;
                                break;
                            }
                        } else {
                            if (x.innerHTML.toLowerCase() > y.innerHTML.toLowerCase()) {
                                shouldSwitch = true;
                                break;
                            }
                        }
                    } else if (dir == "desc") {
                        /* If it is the first row then it is a number*/
                        if (n == 0) {
                            if (Number(x.innerHTML) < Number(y.innerHTML)) {
                                shouldSwitch = true;
                                break;
                            }
                        } else {
                            if (x.innerHTML.toLowerCase() < y.innerHTML.toLowerCase()) {
                                shouldSwitch = true;
                                break;
                            }
                        }
                    }
                }

                if (shouldSwitch) {
                    rows[i].parentNode.insertBefore(rows[i + 1], rows[i]);
                    switching = true;
                    switchcount++;
                } else {
                    if (switchcount == 0 && dir == "asc") {
                        dir = "desc";
                        switching = true;
                    }
                }
            }
        }
    </script>

    </body>
</html>