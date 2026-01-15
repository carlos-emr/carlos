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
        <title>Flowsheet Manager</title>
        <link href="<%=request.getContextPath() %>/css/bootstrap.css" rel="stylesheet" type="text/css">
        <script src="<%=request.getContextPath() %>/js/global.js"></script>
        <script src="<%=request.getContextPath() %>/library/jquery/jquery-3.6.4.min.js"></script>
        <script src="<%=request.getContextPath() %>/share/javascript/Oscar.js"></script>


        <script>
            $(document).ready(function () {
                listSystemFlowsheets();
                listFlowsheets1();
            });

            function listSystemFlowsheets() {
                jQuery.getJSON("<%=request.getContextPath()%>/admin/Flowsheet.do?method=listSystem", {},
                    function (xml) {
                        var arr = new Array();
                        if (xml.results instanceof Array) {
                            arr = xml.results;
                        } else {
                            arr[0] = xml.results;
                        }

                        for (var i = 0; i < arr.length; i++) {
                            var fs = arr[i];
                            var html = '<tr>';
                            html += '<td></td>';
                            html += '<td>' + fs.displayName + '</td>';
                            html += '<td>' + fs.name + '</td>';
                            html += '<td>' + fs.triggers + '</td>';

                            html += '</tr>';

                            jQuery('#systemFsTable tbody').append(html);
                        }
                    });
            }

            function listFlowsheets1() {
                listFlowsheets('clinic');
                listFlowsheets('provider');
                listFlowsheets('patient');
            }

            function listFlowsheets(scope) {
                jQuery.getJSON("<%=request.getContextPath()%>/admin/Flowsheet.do?method=list&scope=" + scope, {},
                    function (xml) {
                        jQuery('#' + scope + 'Table tbody').empty();
                        var arr = new Array();
                        if (xml.results instanceof Array) {
                            arr = xml.results;
                        } else {
                            arr[0] = xml.results;
                        }

                        for (var i = 0; i < arr.length; i++) {
                            var fs = arr[i];
                            var html = '<tr>';
                            html += '<td><a href="javascript:void(0)" onClick="deleteFlowsheet(' + fs.id + ')"><img src="<%=request.getContextPath()%>/images/icons/101.png" border="0"/></a></td>';
                            html += '<td><u><a href="javascript:void();" onclick="editFlowsheet(' + fs.id + ');">' + fs.displayName + '</a></u></td>';
                            html += '<td>' + fs.template + '</td>';
                            html += '<td>' + fs.triggers + '</td>';
                            html += '<td>' + fs.dateCreated + '</td>';
                            html += '<td>' + fs.createdBy + '</td>';
                            html += '<td>' + fs.details + '</td>';
                            html += '</tr>';

                            jQuery('#' + scope + 'Table tbody').append(html);
                        }
                    });
            }

            function addNewFlowsheet(scope) {
                location.href = '<%=request.getContextPath()%>/oscarEncounter/oscarMeasurements/adminFlowsheet/FlowsheetAdd.jsp?scope=' + scope;
            }

            function editFlowsheet(id) {
                location.href = '<%=request.getContextPath()%>/oscarEncounter/oscarMeasurements/adminFlowsheet/FlowsheetEditor.jsp?id=' + id;
            }

            function deleteFlowsheet(id) {
                if (confirm('Are you sure you would like to delete this flowhseet?')) {
                    jQuery.getJSON("<%=request.getContextPath()%>/admin/Flowsheet.do?method=deleteFlowsheet&id=" + id, {},
                        function (xml) {
                            listFlowsheets1();
                        }
                    );
                }
            }
        </script>
    </head>

    <body>
    <h2>Manage Flowsheets</h2>
    <br/>

    <h4>System Flowsheets</h4>
    <table id="systemFsTable" name="systemFsTable"
           class="table table-bordered table-striped table-hover table-condensed">
        <thead>
        <tr>
            <th></th>
            <th style="width:20%">Display Name</th>
            <th style="width:20%">Name (Template)</th>
            <th>Trigger(s)</th>
        </tr>
        </thead>
        <tbody>
        </tbody>
    </table>
    <br/>
    <br/>

    <h4>Custom Flowsheets</h4>
    <br/><br/>
    <h6>Clinic-level</h6>
    <table id="clinicTable" name="fsTable" class="table table-bordered table-striped table-hover table-condensed">
        <thead>
        <tr>
            <th></th>
            <th>Name</th>
            <th>System Template</th>
            <th>Trigger(s)</th>
            <th>Date Created</th>
            <th>Created By</th>
            <th></th>
        </tr>
        </thead>
        <tbody>
        </tbody>
    </table>
    <input type="button" class="btn btn-primary" value="Add New Custom Flowsheet for the clinic"
           onClick="addNewFlowsheet('clinic')"/>


    <br/><br/>
    <h6>Provider-level</h6>
    <table id="providerTable" name="fsTable" class="table table-bordered table-striped table-hover table-condensed">
        <thead>
        <tr>
            <th></th>
            <th>Name</th>
            <th>System Template</th>
            <th>Trigger(s)</th>
            <th>Date Created</th>
            <th>Created By</th>
            <th>Provider</th>
        </tr>
        </thead>
        <tbody>
        </tbody>
    </table>
    <input type="button" class="btn btn-primary" value="Add New Custom Flowsheet for a Provider"
           onClick="addNewFlowsheet('provider')"/>


    <br/><br/>
    <h6>Patient-level</h6>
    <table id="patientTable" name="fsTable" class="table table-bordered table-striped table-hover table-condensed">
        <thead>
        <tr>
            <th></th>
            <th>Name</th>
            <th>System Template</th>
            <th>Trigger(s)</th>
            <th>Date Created</th>
            <th>Created By</th>
            <th>Patient</th>
        </tr>
        </thead>
        <tbody>
        </tbody>
    </table>
    <input type="button" class="btn btn-primary" value="Add New Custom Flowsheet for a Patient"
           onClick="addNewFlowsheet('patient')"/>

    </body>
</html>