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
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
"http://www.w3.org/TR/html4/loose.dtd">
<%-- This JSP is the first page you see when you enter 'report by template' --%>
<%@page import="io.github.carlos_emr.carlos.commn.dao.DemographicDao" %>
<%@page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.FlowSheetUserCreatedDao" %>
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
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>


<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    Provider provider = loggedInInfo.getLoggedInProvider();
%>
<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title>CARLOS Jobs</title>
        <link href="<%=request.getContextPath() %>/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet" type="text/css">
        <script src="<%=request.getContextPath() %>/js/global.js"></script>
        <script src="<%=request.getContextPath() %>/library/jquery/jquery-3.7.1.min.js"></script>
        <script src="<%=request.getContextPath() %>/library/jquery/jquery-compat.js"></script>
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
            document.addEventListener('DOMContentLoaded', function () {

                loadValidations();
                loadWarnings();
                loadTargets();
            });

            function loadItem() {
                <c:set var="__enc_1"><carlos:encode value='<%= flowsheetId %>' context="uriComponent"/></c:set>
                <c:set var="__enc_2"><carlos:encode value='<%= measurementType %>' context="uriComponent"/></c:set>
                jQuery.getJSON("<%=request.getContextPath()%>/admin/Flowsheet?method=getFlowsheetItem&flowsheetId=<carlos:encode value='${__enc_1}' context="javaScript"/>&measurementType=<carlos:encode value='${__enc_2}' context="javaScript"/>", {},
                    function (xml) {
                        document.getElementById('displayName').value = xml.displayName;
                        document.getElementById('guideline').value = xml.guideline;
                        document.getElementById('graphable').value = xml.graphable;
                        document.getElementById('measuringInstruction').value = xml.measuringInstruction;
                        document.getElementById('validations').value = xml.validationId;
                    });
            }

            function loadValidations() {
                jQuery.getJSON("<%=request.getContextPath()%>/admin/Flowsheet?method=getValidations", {},
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
                <c:set var="__enc_3"><carlos:encode value='<%= flowsheetId %>' context="uriComponent"/></c:set>
                <c:set var="__enc_4"><carlos:encode value='<%= measurementType %>' context="uriComponent"/></c:set>
                jQuery.getJSON("<%=request.getContextPath()%>/admin/Flowsheet?method=getWarnings&flowsheetId=<carlos:encode value='${__enc_3}' context="javaScript"/>&measurementType=<carlos:encode value='${__enc_4}' context="javaScript"/>", {},
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
                <c:set var="__enc_5"><carlos:encode value='<%= flowsheetId %>' context="uriComponent"/></c:set>
                <c:set var="__enc_6"><carlos:encode value='<%= measurementType %>' context="uriComponent"/></c:set>
                jQuery.getJSON("<%=request.getContextPath()%>/admin/Flowsheet?method=getTargets&flowsheetId=<carlos:encode value='${__enc_5}' context="javaScript"/>&measurementType=<carlos:encode value='${__enc_6}' context="javaScript"/>", {},
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
                jQuery.post('<%=request.getContextPath()%>/admin/Flowsheet?method=saveFlowsheetItem',
                    jQuery('#theForm').serialize(),
                    function (data) {
                        <c:set var="__enc_7"><carlos:encode value='<%= flowsheetId %>' context="uriComponent"/></c:set>
                        location.href = '<%=request.getContextPath()%>/encounter/oscarMeasurements/adminFlowsheet/ViewFlowsheetEditor?id=<carlos:encode value='${__enc_7}' context="javaScript"/>';
                    });
            }

            function addNewWarning() {
                <c:set var="__enc_8"><carlos:encode value='<%= flowsheetId %>' context="uriComponent"/></c:set>
                <c:set var="__enc_9"><carlos:encode value='<%= measurementType %>' context="uriComponent"/></c:set>
                location.href = '<%=request.getContextPath()%>/encounter/oscarMeasurements/adminFlowsheet/ViewFlowsheetAddWarning?flowsheetId=<carlos:encode value='${__enc_8}' context="javaScript"/>&measurementType=<carlos:encode value='${__enc_9}' context="javaScript"/>';
            }

            function addNewTarget() {
                <c:set var="__enc_10"><carlos:encode value='<%= flowsheetId %>' context="uriComponent"/></c:set>
                <c:set var="__enc_11"><carlos:encode value='<%= measurementType %>' context="uriComponent"/></c:set>
                location.href = '<%=request.getContextPath()%>/encounter/oscarMeasurements/adminFlowsheet/ViewFlowsheetAddTarget?flowsheetId=<carlos:encode value='${__enc_10}' context="javaScript"/>&measurementType=<carlos:encode value='${__enc_11}' context="javaScript"/>';
            }

            function updateDetails() {
                var template = document.getElementById('template').value;

                $.post('<%=request.getContextPath()%>/admin/Flowsheet?method=getTemplateDetails', {template: template}, function (data) {
                    //  loadFlowsheet();
                });
            }

            function removeWarning(hash) {
                jQuery.post('<%=request.getContextPath()%>/admin/Flowsheet?method=removeWarning', {
                        flowsheetId: '<carlos:encode value='<%= flowsheetId %>' context="javaScriptBlock"/>',
                        type: '<carlos:encode value='<%= measurementType %>' context="javaScriptBlock"/>',
                        hash: hash
                    },
                    function (data) {
                        loadWarnings();
                    });
            }

            function removeTarget(hash) {
                jQuery.post('<%=request.getContextPath()%>/admin/Flowsheet?method=removeTarget', {
                        flowsheetId: '<carlos:encode value='<%= flowsheetId %>' context="javaScriptBlock"/>',
                        type: '<carlos:encode value='<%= measurementType %>' context="javaScriptBlock"/>',
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
        <input type="hidden" name="flowsheetId" value="<carlos:encode value='<%= flowsheetId %>' context="htmlAttribute"/>"/>
        <input type="hidden" name="measurementType" value="<carlos:encode value='<%= measurementType %>' context="htmlAttribute"/>"/>

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
    <table id="warningTable" class="table table-bordered table-striped table-hover table-sm" style="width:70%">
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
    <table id="targetTable" class="table table-bordered table-striped table-hover table-sm" style="width:70%">
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
