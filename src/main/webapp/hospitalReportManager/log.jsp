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
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.managers.SecurityInfoManager" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<%
    SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    boolean isHrmAdmin = securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm.administrator", "r", null);
    boolean isAdmin = securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin.hrm", "r", null);
    boolean isHrm = securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm", "r", null);
%>

<!DOCTYPE html >
<html>
    <head>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>HRM Inbox - OSCAR EMR</title>

        <link rel="stylesheet" type="text/css"
              href="${ pageContext.request.contextPath }/library/bootstrap/3.0.0/css/bootstrap.min.css"/>
        <link rel="stylesheet" type="text/css"
              href="${ pageContext.request.contextPath }/library/DataTables-1.10.12/media/css/jquery.dataTables.min.css"/>
        <link rel="stylesheet" type="text/css"
              href="${ pageContext.request.contextPath }/hospitalReportManager/inbox.css"/>


        <script src="<%=request.getContextPath() %>/library/jquery/jquery-3.6.4.min.js"></script>
        <script src="${ pageContext.request.contextPath }/library/bootstrap/3.0.0/js/bootstrap.min.js"></script>
        <script src="${ pageContext.request.contextPath }/library/DataTables-1.10.12/media/js/dataTables.bootstrap.min.js"></script>
        <script src="<%=request.getContextPath() %>/library/DataTables/datatables.min.js"></script>
        <!-- DataTables 1.13.4 -->

        <script>
            // table sorting
            $(document).ready(function () {
                $('#libraryTable').DataTable({
                    serverSide: true,
                    ajax: "<%=request.getContextPath()%>/hospitalReportManager/hrm.do?method=viewLog",
                    searching: false,
                    "dom": '<"top"i>rt<"bottom"lp><"clear">',
                    "language": {"url": "<%=request.getContextPath() %>/library/DataTables/i18n/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.i18nLanguagecode"/>.json"},
                    "columns": [
                        {
                            "data": "transaction_date", render: function (data, type, full, meta) {
                                console.log(JSON.stringify(full));
                                return '<a href="javascript:void(0);" onClick="showDetailedLogs(' + full.id + ')">' + data + '</a>';
                            }
                        },
                        {"data": "transaction_type"},
                        {"data": "external_system"},
                        {"data": "initiating_provider"},
                        {"data": "connected"},
                        {"data": "downloaded"},
                        {"data": "num_files_downloaded"},
                        {"data": "deleted"}
                    ]
                });

            });

            function showDetailedLogs(id) {
                $.ajax({
                    type: "GET",
                    url: '<%=request.getContextPath()%>/hospitalReportManager/hrm.do?method=getDetailedLog&id=' + id,
                    dataType: 'json',
                    async: true,
                    success: function (data) {
                        //alert(JSON.stringify(data.items));
                        if (data != null && data.items != null) {
                            var html = '';
                            for (var x = 0; x < data.items.length; x++) {
                                console.log(data.items[x].id);
                                html += "<table>";

                                html += "<tr><td><b>Filename:</b></td><td>" + data.items[x].encrypted_filename + "</td></tr>";
                                html += "<tr><td><b>Recipient:</b></td><td>" + data.items[x].recipient + "</td></tr>";
                                html += "<tr><td><b>Errors:</b></td><td>" + data.items[x].error + "</td></tr>";
                                html += "</table><br/><br/>";
                            }
                            $("#detailed_info").html(html);
                        }
                    }
                });
            }

        </script>
    </head>
    <body>
    <div>
        <div class="col-sm-12">

            <!-- Fixed navbar -->
            <nav class="navbar navbar-default navbar-fixed-top">
                <div class="container">
                    <div class="navbar-header">
                        <button type="button" class="navbar-toggle collapsed" data-toggle="collapse"
                                data-target="#navbar" aria-expanded="false" aria-controls="navbar">
                            <span class="sr-only"></span>
                            <span class="icon-bar"></span>
                            <span class="icon-bar"></span>
                            <span class="icon-bar"></span>
                        </button>
                        <b>
                            <a class="navbar-brand" href="#">Health Report Manager</a>
                        </b>
                    </div>
                    <div id="navbar" class="navbar-collapse collapse">
                        <ul class="nav navbar-nav">

                        </ul>
                        <ul class="nav navbar-nav navbar-right">
                            <li><a href="inbox.jsp">HRM Inbox</a></li>
                        </ul>
                    </div><!--/.nav-collapse -->
                </div>
            </nav>

            <div class="table-responsive" id="libraryTableContainer">

                <div class="col-sm-12">
                    <h3>Transaction Log</h3>
                    <br/>
                    <table class="table table-striped table-condensed" id="libraryTable" style="width:100%">
                        <thead>
                        <tr>
                            <th>Transaction Date</th>
                            <th>Transaction Type</th>
                            <th>External System</th>
                            <th>Initiating Provider</th>
                            <th>Connected</th>
                            <th>Downloaded</th>
                            <th>Number of Files</th>
                            <th>Deleted</th>
                        </tr>
                        </thead>

                        <tbody>
                        </tbody>
                    </table>
                </div>


                <br/>

                <div class="col-sm-12">
                    <h3>Detailed information</h3>
                    <div id="detailed_info">
                    </div>
                </div>


            </div>

        </div>
    </div> <!-- end container -->
    </body>
</html>