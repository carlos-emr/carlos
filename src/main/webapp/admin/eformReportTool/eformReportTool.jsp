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
<!DOCTYPE html>
<%-- This JSP is the first page you see when you enter 'report by template' --%>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + ","
            + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.reporting" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin&type=_admin.reporting");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>


<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@page import="org.apache.commons.lang3.StringUtils" %>

<%
    LoggedInInfo loggedInInfo = LoggedInInfo
            .getLoggedInInfoFromSession(request);
    Provider provider = loggedInInfo.getLoggedInProvider();
%>
<html>
    <head>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <script src="<%=request.getContextPath()%>/js/global.js"></script>
        <title>OSCAR Products</title>
        <link href="<%=request.getContextPath()%>/library/bootstrap/5.3.3/css/bootstrap.min.css" rel="stylesheet" type="text/css">
        <link href="<%=request.getContextPath()%>/library/flatpickr/flatpickr.min.css" rel="stylesheet" type="text/css">

        <script src="<%=request.getContextPath() %>/library/jquery/jquery-3.7.1.min.js"></script>
        <script src="<%=request.getContextPath() %>/library/jquery/jquery-compat.js"></script>

        <link rel="stylesheet" href="<%=request.getContextPath() %>/library/jquery/jquery-ui.structure-1.14.2.min.css">
        <link rel="stylesheet" href="<%=request.getContextPath() %>/library/jquery/jquery-ui.theme-1.14.2.min.css">
        <script src="<%=request.getContextPath() %>/library/jquery/jquery-ui-1.14.2.min.js"></script>

        <script src="<%=request.getContextPath()%>/library/bootstrap/5.3.3/js/bootstrap.bundle.min.js"></script>
        <script src="<%=request.getContextPath()%>/library/flatpickr/flatpickr.min.js"></script>
        <script src="<%=request.getContextPath()%>/library/jquery/jquery.validate.min.js"></script>

        <script src="<%=request.getContextPath()%>/share/javascript/Oscar.js"></script>



        <script>
            $(document)
                .ready(
                    function () {

                        listReports();

                        $("#btnAdd").bind('click', function () {
                            fetchEFormsAndOpenDialog();

                        });

                        $("#new-report")
                            .dialog(
                                {
                                    autoOpen: false,
                                    height: 450,
                                    width: 800,
                                    modal: true,
                                    buttons: {
                                        "Add": {
                                            class: "btn btn-primary",
                                            text: "Save",
                                            click: function () {
                                                var e = {
                                                    name: $(
                                                        "#eformReportToolName")
                                                        .val(),
                                                    eformId: $(
                                                        "#eformReportToolEformId")
                                                        .val()
                                                };

                                                if ($(
                                                        "#eformExpiryDate")
                                                        .val() != null
                                                    && $(
                                                        "#eformExpiryDate")
                                                        .val()
                                                        .length > 0) {
                                                    e.expiryDateString = $(
                                                        "#eformExpiryDate")
                                                        .val();
                                                }

                                                $.ajax({
                                                        url: '${pageContext.request.contextPath}/ws/rs/reporting/eformReportTool/add',
                                                        type: 'POST',
                                                        data: JSON
                                                            .stringify(e),
                                                        contentType: "application/json; charset=utf-8",
                                                        dataType: 'json',
                                                        success: function (data) {
                                                            listReports();
                                                        },
                                                        error: function (xhr, status, error) {
                                                            alert('Error: ' + error + '\nHTTP Status: ' + xhr.status);
                                                        }
                                                    });

                                                $(this).dialog("close");

                                            }
                                        },
                                        Cancel: {
                                            class: "btn",
                                            text: "Cancel",
                                            click: function () {

                                                $(this).dialog("close");
                                            }
                                        }
                                    },
                                    close: function () {

                                    }
                                });

                    });

            function fetchEFormsAndOpenDialog() {
                jQuery.getJSON("${pageContext.request.contextPath}/ws/rs/forms/allEForms", {}, function (xml) {
                    $("#eformReportToolEformId option").remove();
                    //alert(JSON.stringify(xml));
                    if (xml.content) {
                        for (var x = 0; x < xml.content.length; x++) {
                            $("#eformReportToolEformId").append(
                                "<option value=\"" + xml.content[x].id + "\">"
                                + xml.content[x].formName + "</option>");
                        }
                    }
                    $('#new-report').dialog('open');

                });
            }

            function populate(eftId) {
                var e = {
                    id: eftId
                };
                $.ajax({
                    url: '${pageContext.request.contextPath}/ws/rs/reporting/eformReportTool/populate',
                    type: 'POST',
                    data: JSON.stringify(e),
                    contentType: "application/json; charset=utf-8",
                    dataType: 'json',
                    success: function (data) {
                        listReports();
                    },
                    error: function (xhr, status, error) {
                        alert('Error: ' + error + '\nHTTP Status: ' + xhr.status);
                    }
                });
            }

            function markLatest(eftId) {
                var e = {
                    id: eftId
                };
                $.ajax({
                    url: '${pageContext.request.contextPath}/ws/rs/reporting/eformReportTool/markLatest',
                    type: 'POST',
                    data: JSON.stringify(e),
                    contentType: "application/json; charset=utf-8",
                    dataType: 'json',
                    success: function (data) {
                        listReports();
                    },
                    error: function (xhr, status, error) {
                        alert('Error: ' + error + '\nHTTP Status: ' + xhr.status);
                    }
                });
            }

            function removeItem(eftId) {
                if (confirm("Are you sure? This will delete your temporary table")) {
                    var e = {
                        id: eftId
                    };
                    $.ajax({
                        url: '${pageContext.request.contextPath}/ws/rs/reporting/eformReportTool/remove',
                        type: 'POST',
                        data: JSON.stringify(e),
                        contentType: "application/json; charset=utf-8",
                        dataType: 'json',
                        success: function (data) {
                            listReports();

                        },
                        error: function (xhr, status, error) {
                            alert('Error: ' + error + '\nHTTP Status: ' + xhr.status);
                        }
                    });
                }
            }

            function listReports() {
                $
                    .ajax({
                        url: '${pageContext.request.contextPath}/ws/rs/reporting/eformReportTool/list',
                        type: 'GET',
                        dataType: 'json',
                        success: function (data) {

                            $("#listTable tbody").empty();

                            if (data.content) {
                                for (var x = 0; x < data.content.length; x++) {
                                    var e = data.content[x];
                                    var dateLastPopulated = "";
                                    var expiryDate = "";
                                    if (e.dateLastPopulated != null) {
                                        dateLastPopulated = new Date(
                                            e.dateLastPopulated);


                                    }
                                    if (e.expiryDate != null) {
                                        expiryDate = new Date(e.expiryDate);
                                    }
                                    $("#listTable tbody")
                                        .append(
                                            "<tr> <td><a onClick=\"removeItem('"
                                            + e.id
                                            + "')\">(Remove)</a>&nbsp;<a onClick=\"populate('"
                                            + e.id
                                            + "')\">(Populate)</a> &nbsp;<a onClick=\"markLatest('"
                                            + e.id
                                            + "')\">(MarkLatest)</a></td> <td>"
                                            + e.name
                                            + "</td> <td>"
                                            + e.tableName
                                            + "</td> <td>"
                                            + e.eformName
                                            + "</td> <td>"
                                            + new Date(
                                                e.dateCreated)
                                            + "</td> <td>"
                                            + expiryDate
                                            + "</td> <td>"
                                            + dateLastPopulated
                                            + "</td>  <td>"
                                            + e.latestMarked
                                            + "</td>  <td>"
                                            + e.numRecordsInTable
                                            + "</td></tr>");
                                }
                            }

                        },
                        error: function (xhr, status, error) {
                            alert('Error: ' + error + '\nHTTP Status: ' + xhr.status);
                        }
                    });

            }
        </script>

        <style>
            .red {
                color: red
            }
        </style>

    </head>

    <body class="BodyStyle">
    <h4>EForm Reporting Tool</h4>


    <!--  display list of existing tables made, with ability to delete any one of them -->
    <table id="listTable" class="table table-striped table-hover table-sm" style="width: 100%;">
        <thead>
        <th>&nbsp;</th>
        <th>Name</th>
        <th>Table Name</th>
        <th>Eform Name</th>
        <th>Created</th>
        <th>Expires</th>
        <th>Last Populated</th>
        <th>Latest Marked</th>
        <th># of Records</th>
        </thead>
        <tbody></tbody>
    </table>

    <!-- button to add new  -->
    <button id="btnAdd" class="btn btn-secondary">Add New</button>

    <!-- add new should show form with name, eform name, and expiry date -->

    <div id="new-report" title="Create new OSCAR EForm Report table">
        <p class="validateTips"></p>

        <form id="reportForm">

            <div>
                <div class="d-flex gap-2">
                    <div class="mb-3 col-md-8" id="group1">
                        <label class="form-label" for="eformReportToolEformId">Choose
                            EForm:</label>
                        <div>
                            <select id="eformReportToolEformId"
                                    name="eformReportTool.eformId">
                                <option></option>
                            </select>
                        </div>
                    </div>
                </div>
                <div class="d-flex gap-2">
                    <div class="mb-3 col-md-8" id="group2">
                        <label class="form-label" for="eformReportToolName">Name:</label>
                        <div>
                            <input type="text" name="eformReportTool.name"
                                   id="eformReportToolName"/>
                        </div>
                    </div>


                </div>
                <div class="d-flex gap-2">

                    <div class="mb-3 col-md-8" id="group3">
                        <label class="form-label" for="eformExpiryDate">Expiry
                            Date:</label>
                        <div>
                            <input type="text" name="eformReportTool.expiryDate"
                                   id="eformExpiryDate" value=""/>
                        </div>
                    </div>

                </div>

            </div>

        </form>
    </div>


    <script>
        flatpickr('#eformExpiryDate', {dateFormat: "Y-m-d", allowInput: true});
    </script>

    </body>
</html>