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
<!DOCTYPE html>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>


<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@ page import="ca.openosp.openo.commn.model.Provider" %>

<%
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    Provider provider = loggedInInfo.getLoggedInProvider();
%>
<html>
    <head>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <script src="<%= request.getContextPath() %>/js/global.js"></script>
        <title>Manage REST Clients (OAuth)</title>

        <link rel="stylesheet" href="<%=request.getContextPath() %>/library/jquery/jquery-ui.structure-1.12.1.min.css">
        <link rel="stylesheet" href="<%=request.getContextPath() %>/library/jquery/jquery-ui.theme-1.12.1.min.css">
        <link href="<%=request.getContextPath() %>/css/bootstrap.css" rel="stylesheet" type="text/css">
        <!-- Bootstrap 2.3.1 -->
        <link href="<%=request.getContextPath() %>/css/DT_bootstrap.css" rel="stylesheet" type="text/css">

        <script src="<%=request.getContextPath() %>/library/jquery/jquery-3.6.4.min.js"></script>
        <script src="<%=request.getContextPath() %>/library/DataTables/datatables.min.js"></script>
        <!-- DataTables 1.13.4 -->
        <script src="<%=request.getContextPath() %>/library/jquery/jquery-ui-1.12.1.min.js"></script>
        <script src="<%=request.getContextPath() %>/js/jquery.validate.js"></script>
        <script src="<%=request.getContextPath() %>/js/DT_bootstrap.js"></script>
        <script src="<%= request.getContextPath() %>/share/javascript/Oscar.js"></script>


        <script>

            function editJobType(jobTypeId) {
                jQuery.getJSON("<%= request.getContextPath() %>/ws/rs/jobs/jobType/" + jobTypeId, {},
                    function (xml) {
                        if (xml.types) {
                            var job;
                            if (xml.types instanceof Array) {
                                job = xml.types[0];
                            } else {
                                job = xml.types;
                            }

                            $('#jobTypeName').val(job.name);
                            $('#jobTypeDescription').val(job.description);
                            $('#jobTypeClassName').val(job.className);
                            $('#jobTypeEnabled').prop('checked', job.enabled);
                            $('#jobTypeId').val(job.id);
                        }
                    });
                $('#new-jobtype').dialog('open');
            }

            function addNewJobType() {
                $('#jobTypeId').val('0');
                $('#jobTypeName').val('');
                $('#jobTypeDescription').val('');
                $('#jobTypeClassName').val('');
                $('#jobTypeEnabled').prop('checked', true);
                $('#new-jobtype').dialog('open');
            }

            function clearJobs() {
                $("#jobTypeTable tbody tr").remove();
            }

            function listJobs() {
                return getJobTypes();
            }

            function getJobTypes() {
                jQuery.getJSON("${pageContext.request.contextPath}/ws/rs/jobs/types/all", {async: false},
                    function (xml) {
                        if (xml.types) {
                            var arr = new Array();
                            if (xml.types instanceof Array) {
                                arr = xml.types;
                            } else {
                                arr[0] = xml.types;
                            }

                            for (var i = 0; i < arr.length; i++) {
                                var job = arr[i];
                                var html = '<tr>';

                                html += '<td><u><a href="javascript:void(0);" onclick="editJobType(' + job.id + ');">' + job.name + '</a></u></td>';
                                html += '<td>' + job.description + '</td>';
                                html += '<td>' + job.className + '</td>';
                                html += '<td>' + job.currentlyValid + '</td>';
                                html += '<td>' + job.enabled + '</td>';
                                html += '<td>' + new Date(job.updated) + '</td>';
                                html += '</tr>';

                                $('#jobTypeTable tbody').append(html);

                            }
                            initiate();
                        }

                    });
            }

            function initiate() {
                $('#jobTypeTable').DataTable({
                    "order": []
                });
                return;
            }

            $(document).ready(function () {
                listJobs();


                $("#new-jobtype").dialog({
                    autoOpen: false,
                    height: 525,
                    width: 620,
                    modal: true,
                    buttons: {
                        "Save Job Type": {
                            class: "btn btn-primary", text: "Save Job Type", click: function () {
                                $.post('${pageContext.request.contextPath}/ws/rs/jobs/saveJobType', $('#jobTypeForm').serialize(), function (data) {
                                    clearJobs();
                                    listJobs();
                                });
                                $(this).dialog("close");

                            }
                        },
                        Cancel: {
                            class: "btn", text: "Cancel", click: function () {
                                $(this).dialog("close");
                            }
                        }
                    },
                    close: function () {

                    }
                });

            });
        </script>
    </head>

    <body class="BodyStyle">
    <h4>Manage Job Types</h4>
    <table id="jobTypeTable" class="table table-bordered table-striped table-hover table-condensed">
        <thead>
        <tr>
            <th>Name</th>
            <th>Description</th>
            <th>JAVA Class Name</th>
            <th>Valid Class</th>
            <th>Enabled</th>
            <th>Updated</th>
        </tr>
        </thead>
        <tbody>
        </tbody>
    </table>
    <input type="button" class="btn btn-primary" value="Add New" onClick="addNewJobType()"/>


    <div id="new-jobtype" title="OSCAR Job Type Editor">
        <p class="validateTips"></p>

        <form id="jobTypeForm">
            <input type="hidden" name="jobType.id" id="jobTypeId" value="0"/>
            <fieldset>
                <div class="control-group">
                    <label class="control-label" for="jobTypeName">Name:*</label>
                    <div class="controls">
                        <input class="input-block-level" type="text" name="jobType.name" id="jobTypeName" value=""/>
                    </div>
                </div>
                <div class="control-group">
                    <label class="control-label" for="jobTypeDescription">Description:</label>
                    <div class="controls">
                        <textarea class="input-block-level" rows="5" name="jobType.description"
                                  id="jobTypeDescription"></textarea>
                    </div>
                </div>
                <div class="control-group">
                    <label class="control-label" for="jobTypeClassName">JAVA Class Name:</label>
                    <div class="controls">
                        <input class="input-block-level" type="text" name="jobType.className" id="jobTypeClassName"
                               value=""/>
                    </div>
                </div>
                <div class="control-group">
                    <label class="control-label" for="jobTypeEnabled">Enabled: <input type="checkbox"
                                                                                      name="jobType.enabled"
                                                                                      id="jobTypeEnabled"/></label>
                    <div class="controls">

                    </div>
                </div>

            </fieldset>
        </form>
    </div>


    </body>
</html>