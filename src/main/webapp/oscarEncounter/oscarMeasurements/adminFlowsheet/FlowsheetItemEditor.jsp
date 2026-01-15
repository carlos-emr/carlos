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
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
"http://www.w3.org/TR/html4/loose.dtd">
<%-- This JSP is the first page you see when you enter 'report by template' --%>
<%@page import="ca.openosp.openo.commn.dao.DemographicDao" %>
<%@page import="ca.openosp.openo.PMmodule.dao.ProviderDao" %>
<%@page import="ca.openosp.openo.commn.dao.FlowSheetUserCreatedDao" %>
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
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title>OSCAR Jobs</title>
        <link href="<%=request.getContextPath() %>/css/bootstrap.css" rel="stylesheet" type="text/css">
        <script src="<%=request.getContextPath() %>/js/global.js"></script>
        <script src="<%=request.getContextPath() %>/library/jquery/jquery-3.6.4.min.js"></script>
        <script src="<%=request.getContextPath() %>/share/javascript/Oscar.js"></script>

        <style>
            .red {
                color: red
            }

        </style>
        <%
            String flowsheetId = request.getParameter("flowsheetId");
            String measurementType = request.getParameter("measurementType");
        %>
        <script>
            $(document).ready(function () {

                loadValidations();
                loadWarnings();
                loadTargets();
            });

            function loadItem() {
                jQuery.getJSON("<%=request.getContextPath()%>/admin/Flowsheet.do?method=getFlowsheetItem&flowsheetId=<%=flowsheetId%>&measurementType=<%=measurementType%>", {},
                    function (xml) {
                        $("#displayName").val(xml.displayName);
                        $("#guideline").val(xml.guideline);
                        $("#graphable").val(xml.graphable);
                        $("#measuringInstruction").val(xml.measuringInstruction);
                        $("#validations").val(xml.validationId);
                    });
            }

            function loadValidations() {
                jQuery.getJSON("<%=request.getContextPath()%>/admin/Flowsheet.do?method=getValidations", {},
                    function (xml) {
                        var arr = new Array();
                        if (xml.results instanceof Array) {
                            arr = xml.results;
                        } else {
                            arr[0] = xml.results;
                        }

                        for (var i = 0; i < arr.length; i++) {
                            jQuery('#validations').append("<option value=" + arr[i].id + ">" + arr[i].name + "</option>");
                        }

                        loadItem();
                    });
            }

            function loadWarnings() {
                jQuery.getJSON("<%=request.getContextPath()%>/admin/Flowsheet.do?method=getWarnings&flowsheetId=<%=flowsheetId%>&measurementType=<%=measurementType%>", {},
                    function (xml) {
                        var arr = new Array();
                        if (xml.results instanceof Array) {
                            arr = xml.results;
                        } else {
                            arr[0] = xml.results;
                        }

                        $("#warningTable tbody tr").remove();

                        for (var x = 0; x < xml.rules.length; x++) {
                            var i = xml.rules[x];
                            $("#warningTable tbody").append("<tr><td><a href=\"javascript:void(0)\" onClick=\"removeWarning('" + i.hash + "')\"><img src=\"<%=request.getContextPath()%>/images/icons/101.png\" border=\"0\"/></a></td><td>" + i.strength + "</td><td>" + i.type + "</td><td>" + i.param + "</td><td>" + i.value + "</td></tr>");
                        }
                    });
            }

            function loadTargets() {
                jQuery.getJSON("<%=request.getContextPath()%>/admin/Flowsheet.do?method=getTargets&flowsheetId=<%=flowsheetId%>&measurementType=<%=measurementType%>", {},
                    function (xml) {
                        var arr = new Array();
                        if (xml.results instanceof Array) {
                            arr = xml.results;
                        } else {
                            arr[0] = xml.results;
                        }

                        $("#targetTable tbody tr").remove();

                        for (var x = 0; x < xml.rules.length; x++) {
                            var i = xml.rules[x];
                            $("#targetTable tbody").append("<tr><td><a href=\"javascript:void(0)\" onClick=\"removeTarget('" + i.hash + "')\"><img src=\"<%=request.getContextPath()%>/images/icons/101.png\" border=\"0\"/></a></td><td>" + i.indicator + "</td><td>" + i.type + "</td><td>" + i.param + "</td><td>" + i.value + "</td></tr>");
                        }
                    });
            }

            function saveItem() {
                jQuery.post('<%=request.getContextPath()%>/admin/Flowsheet.do?method=saveFlowsheetItem',
                    jQuery('#theForm').serialize(),
                    function (data) {
                        location.href = '<%=request.getContextPath()%>/oscarEncounter/oscarMeasurements/adminFlowsheet/FlowsheetEditor.jsp?id=<%=flowsheetId %>';
                    });
            }

            function addNewWarning() {
                location.href = '<%=request.getContextPath()%>/oscarEncounter/oscarMeasurements/adminFlowsheet/FlowsheetAddWarning.jsp?flowsheetId=<%=flowsheetId %>&measurementType=<%=measurementType%>';
            }

            function addNewTarget() {
                location.href = '<%=request.getContextPath()%>/oscarEncounter/oscarMeasurements/adminFlowsheet/FlowsheetAddTarget.jsp?flowsheetId=<%=flowsheetId %>&measurementType=<%=measurementType%>';
            }

            function updateDetails() {
                var template = $("#template").val();

                $.post('<%=request.getContextPath()%>/admin/Flowsheet.do?method=getTemplateDetails', {template: template}, function (data) {
                    //  loadFlowsheet();
                });
            }

            function removeWarning(hash) {
                jQuery.post('<%=request.getContextPath()%>/admin/Flowsheet.do?method=removeWarning', {
                        flowsheetId: <%=flowsheetId%>,
                        type: '<%=measurementType%>',
                        hash: hash
                    },
                    function (data) {
                        loadWarnings();
                    });
            }

            function removeTarget(hash) {
                jQuery.post('<%=request.getContextPath()%>/admin/Flowsheet.do?method=removeTarget', {
                        flowsheetId: <%=flowsheetId%>,
                        type: '<%=measurementType%>',
                        hash: hash
                    },
                    function (data) {
                        loadTargets();
                    });
            }

        </script>
    </head>

    <body>
    <h2>Flowsheet Item Editor</h2>
    <br/>
    <form name="theForm" id="theForm">
        <input type="hidden" name="flowsheetId" value="<%=flowsheetId %>"/>
        <input type="hidden" name="measurementType" value="<%=measurementType %>"/>

        <table style="width:20%">
            <tr>
                <td><b>Display Name:</b></td>
                <td><input type="text" name="displayName" id="displayName" value=""/></td>
            </tr>
            <tr>
                <td><b>Guidelines:</b></td>
                <td><input type="text" name="guideline" id="guideline" value=""/></td>
            </tr>
            <tr>
                <td><b>Graphable:</b></td>
                <td>
                    <select name="graphable" id="graphable">
                        <option value="yes">Yes</option>
                        <option value="no">No</option>
                    </select>
                </td>
            </tr>
            <tr>
                <td><b>Measuring Instruction:</b></td>
                <td><input type="text" name="measuringInstruction" id="measuringInstruction" value=""/></td>
            </tr>
            <tr>
                <td><b>Validation:</b></td>
                <td>
                    <select id="validations" name="validations">
                        <option value="">Select Below</option>
                    </select>
                </td>
            </tr>
            <tr>
                <td colspan="2">
                    <input type="button" value="Save" onClick="saveItem()"/>
                </td>
            </tr>
        </table>
    </form>

    <br/>
    <br/>

    <b>Recommendations/Warnings:</b>
    <table id="warningTable" class="table table-bordered table-striped table-hover table-condensed" style="width:70%">
        <thead>
        <th></th>
        <th>Type</th>
        <th>Condition</th>
        <th>Parameter</th>
        <th>Value</th>
        </thead>
        <tbody>
        </tbody>
    </table>
    <input type="button" class="btn btn-primary" value="Add New" onClick="addNewWarning()"/>
    <br/>
    <br/>

    <b>Targets:</b>
    <table id="targetTable" class="table table-bordered table-striped table-hover table-condensed" style="width:70%">
        <thead>
        <th></th>
        <th>Indicator</th>
        <th>Type</th>
        <th>Parameter</th>
        <th>Value</th>

        </thead>
        <tbody>
        </tbody>
    </table>
    <input type="button" class="btn btn-primary" value="Add New" onClick="addNewTarget()"/>
    </body>
</html>