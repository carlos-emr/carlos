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
  Page role: Renders `jobTypes.jsp` for the administration area.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<!DOCTYPE html>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_admin");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>


<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>


<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Provider" %>

<%
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    Provider provider = loggedInInfo.getLoggedInProvider();
%>
<html>
    <head>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <script src="<%= request.getContextPath() %>/js/global.js"></script>
        <title>Manage REST Clients (OAuth)</title>

        <link rel="stylesheet" href="<%=request.getContextPath() %>/library/jquery/jquery-ui.structure-1.14.2.min.css">
        <link rel="stylesheet" href="<%=request.getContextPath() %>/library/jquery/jquery-ui.theme-1.14.2.min.css">
        <link href="<%=request.getContextPath() %>/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet" type="text/css">
        <!-- Bootstrap 2.3.1 -->
        <link href="<%=request.getContextPath() %>/library/DataTables/DataTables-1.13.11/css/dataTables.bootstrap5.min.css" rel="stylesheet" type="text/css">

        <script src="<%=request.getContextPath() %>/library/jquery/jquery-3.7.1.min.js"></script>
        <script src="<%=request.getContextPath() %>/library/DataTables/datatables.min.js"></script>
        <!-- DataTables 1.13.4 -->
        <script src="<%=request.getContextPath() %>/library/jquery/jquery-ui-1.14.2.min.js"></script>
        <script src="<%=request.getContextPath() %>/library/jquery/jquery.validate-1.21.0.min.js"></script>
        <script src="<%=request.getContextPath() %>/library/DataTables/DataTables-1.13.11/js/dataTables.bootstrap5.min.js"></script>
        <script src="<%= request.getContextPath() %>/share/javascript/Oscar.js"></script>


        <script>
            const jobTypesSaveLabel = "<fmt:message key='admin.jobTypes.save'/>";
            const jobTypesCancelLabel = "<fmt:message key='admin.jobTypes.cancel'/>";

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
                                var $row = $('<tr>');
                                var $nameLink = $('<a>').attr('href', 'javascript:void(0);')
                                    .text(job.name)
                                    .on('click', (function(id) { return function() { editJobType(id); }; })(job.id));
                                $row.append($('<td>').append($('<u>').append($nameLink)));
                                $row.append($('<td>').text(job.description));
                                $row.append($('<td>').text(job.className));
                                $row.append($('<td>').text(job.currentlyValid));
                                $row.append($('<td>').text(job.enabled));
                                $row.append($('<td>').text(new Date(job.updated)));

                                $('#jobTypeTable tbody').append($row);

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
                        saveJobType: {
                            class: "btn btn-primary", text: jobTypesSaveLabel, click: function () {
                                $.post('${pageContext.request.contextPath}/ws/rs/jobs/saveJobType', $('#jobTypeForm').serialize(), function (data) {
                                    clearJobs();
                                    listJobs();
                                });
                                $(this).dialog("close");

                            }
                        },
                        cancel: {
                            class: "btn", text: jobTypesCancelLabel, click: function () {
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
    <h4><fmt:message key="admin.jobTypes.heading"/></h4>
    <table id="jobTypeTable" class="table table-bordered table-striped table-hover table-sm">
        <thead>
        <tr>
            <th><fmt:message key="admin.jobTypes.table.name"/></th>
            <th><fmt:message key="admin.jobTypes.table.description"/></th>
            <th><fmt:message key="admin.jobTypes.table.className"/></th>
            <th><fmt:message key="admin.jobTypes.table.validClass"/></th>
            <th><fmt:message key="admin.jobTypes.table.enabled"/></th>
            <th><fmt:message key="admin.jobTypes.table.updated"/></th>
        </tr>
        </thead>
        <tbody>
        </tbody>
    </table>
    <input type="button" class="btn btn-primary" value="<fmt:message key='admin.jobTypes.addNew'/>" onClick="addNewJobType()"/>


    <div id="new-jobtype" title="<fmt:message key='admin.jobTypes.editorTitle'/>">
        <p class="validateTips"></p>

        <form id="jobTypeForm">
            <input type="hidden" name="jobType.id" id="jobTypeId" value="0"/>
            <fieldset>
                <div class="mb-3">
                    <label class="form-label" for="jobTypeName"><fmt:message key="admin.jobTypes.name"/>:*</label>
                    <input class="form-control" type="text" name="jobType.name" id="jobTypeName" value=""/>
                </div>
                <div class="mb-3">
                    <label class="form-label" for="jobTypeDescription"><fmt:message key="admin.jobTypes.description"/>:</label>
                    <textarea class="form-control" rows="5" name="jobType.description"
                              id="jobTypeDescription"></textarea>
                </div>
                <div class="mb-3">
                    <label class="form-label" for="jobTypeClassName"><fmt:message key="admin.jobTypes.className"/>:</label>
                    <input class="form-control" type="text" name="jobType.className" id="jobTypeClassName"
                           value=""/>
                </div>
                <div class="mb-3">
                    <label class="form-label" for="jobTypeEnabled"><fmt:message key="admin.jobTypes.enabled"/>: <input type="checkbox"
                                                                                      name="jobType.enabled"
                                                                                      id="jobTypeEnabled"/></label>
                    <div>

                    </div>
                </div>

            </fieldset>
        </form>
    </div>


    </body>
</html>
