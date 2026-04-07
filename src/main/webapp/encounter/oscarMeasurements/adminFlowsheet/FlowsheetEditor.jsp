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
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin");%>
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
<%@ page import="org.owasp.encoder.Encode" %>

<%
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    Provider provider = loggedInInfo.getLoggedInProvider();
%>
<html>
    <head>
        <title>Flowsheet Editor</title>
        <link href="<%=request.getContextPath() %>/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet" type="text/css">
        <script src="<%=request.getContextPath() %>/js/global.js"></script>
        <script src="<%=request.getContextPath() %>/library/jquery/jquery-3.7.1.min.js"></script>
        <script src="<%=request.getContextPath() %>/library/jquery/jquery-compat.js"></script>
        <script src="<%=request.getContextPath() %>/share/javascript/Oscar.js"></script>


        <%
            String id = request.getParameter("id");
        %>
        <script>
            document.addEventListener('DOMContentLoaded', function () {
                loadFlowsheet();
                loadTypes();
                loadPreventionTypes();
            });

            function editItem(flowsheetId, measurementType) {
                location.href = '<%=request.getContextPath()%>/encounter/oscarMeasurements/adminFlowsheet/FlowsheetItemEditor.jsp?flowsheetId=' + flowsheetId + '&measurementType=' + measurementType;
            }

            function removeItem(id) {
                jQuery.post('<%=request.getContextPath()%>/admin/Flowsheet.do?method=removeItem', {
                        flowsheetId: '<%=Encode.forJavaScript(id)%>',
                        id: id
                    },
                    function (data) {
                        loadFlowsheet();
                    });
            }

            function sortItem(id, direction) {
                alert('sort ' + direction);
            }

            function loadFlowsheet() {
                jQuery.getJSON("<%=request.getContextPath()%>/admin/Flowsheet.do?method=getFlowsheet&id=<%=Encode.forUriComponent(id)%>", {},
                    function (xml) {
                        $("#itemTable tbody").empty();
                        document.getElementById('name').textContent = xml.name;
                        document.getElementById('template').textContent = xml.template;
                        document.getElementById('createdBy').textContent = xml.createdBy;
                        document.getElementById('createdDate').textContent = xml.createdDate;
                        document.getElementById('dxCodeTriggers').textContent = xml.dxCodeTriggers;
                        document.getElementById('recommendationColour').textContent = xml.recommendationColour;
                        document.getElementById('warningColour').textContent = xml.warningColour;


                        for (var x = 0; x < xml.items.length; x++) {
                            (function(i) {
                                let type = i.measurementType !== undefined ? i.measurementType : i.preventionType;
                                let measuringInst = i.measuringInstruction !== undefined ? i.measuringInstruction : "";
                                let validation = i.validation !== undefined ? i.validation : "";

                                var $tr = $('<tr>');
                                var $tdActions = $('<td>');
                                var $removeLink = $('<a>').attr('href', 'javascript:void(0)').on('click', function() { removeItem(type); });
                                $removeLink.append($('<img>').attr({src: '<%=request.getContextPath()%>/images/icons/101.png', border: '0'}));
                                var $editLink = $('<a>').attr('href', 'javascript:void(0)').on('click', function() { editItem('<%=Encode.forJavaScript(id)%>', type); });
                                $editLink.append($('<img>').attr({src: '<%=request.getContextPath()%>/images/edit.png', border: '0'}));
                                var $upLink = $('<a>').attr('href', 'javascript:void(0)').on('click', function() { sortItem(type, 'up'); });
                                $upLink.append($('<img>').attr({src: '<%=request.getContextPath()%>/images/icon_up_sort_arrow.png', border: '0'}));
                                var $downLink = $('<a>').attr('href', 'javascript:void(0)').on('click', function() { sortItem(type, 'down'); });
                                $downLink.append($('<img>').attr({src: '<%=request.getContextPath()%>/images/icon_down_sort_arrow.png', border: '0'}));
                                $tdActions.append($removeLink).append('\u00a0').append($editLink).append('\u00a0').append($upLink).append('\u00a0').append($downLink);
                                $tr.append($tdActions);
                                $tr.append($('<td>').text(type));
                                $tr.append($('<td>').text(i.displayName));
                                $tr.append($('<td>').text(i.guideline));
                                $tr.append($('<td>').text(i.graphable));
                                $tr.append($('<td>').text(measuringInst));
                                $tr.append($('<td>').text(validation));
                                $("#itemTable tbody").append($tr);
                            })(xml.items[x]);
                        }
                    });
            }

            function loadTypes() {
                jQuery.getJSON("<%=request.getContextPath()%>/admin/Flowsheet.do?method=getMeasurementTypes", {},
                    function (xml) {
                        var arr = new Array();
                        if (xml.results instanceof Array) {
                            arr = xml.results;
                        } else {
                            arr[0] = xml.results;
                        }

                        for (var i = 0; i < arr.length; i++) {
                            jQuery('#types').append($('<option>').attr('value', arr[i].id).text(arr[i].displayName));
                        }
                    });
            }


            function loadPreventionTypes() {
                jQuery.getJSON("<%=request.getContextPath()%>/admin/Flowsheet.do?method=getPreventionTypes", {},
                    function (xml) {
                        var arr = new Array();
                        if (xml.results instanceof Array) {
                            arr = xml.results;
                        } else {
                            arr[0] = xml.results;
                        }

                        for (var i = 0; i < arr.length; i++) {
                            jQuery('#preventionTypes').append($('<option>').attr('value', arr[i].id).text(arr[i].displayName));
                        }
                    });
            }


            function addMeasurement() {
                var typeId = document.getElementById('types').value;

                $.post('<%=request.getContextPath()%>/admin/Flowsheet.do?method=addMeasurement', {
                    flowsheetId:'<%=Encode.forJavaScript(id)%>',
                    measurementTypeId: typeId
                }, function (data) {
                    loadFlowsheet();
                });
            }

            function addPrevention() {
                var typeId = document.getElementById('preventionTypes').value;

                $.post('<%=request.getContextPath()%>/admin/Flowsheet.do?method=addPrevention', {
                    flowsheetId:'<%=Encode.forJavaScript(id)%>',
                    preventionType: typeId
                }, function (data) {
                    loadFlowsheet();
                });
            }


        </script>
    </head>

    <body>
    <h2>Flowsheet Editor</h2>
    <br/>

    <table style="width:20%">
        <tr>
            <td><b>Name:</b></td>
            <td><span id="name"></span></td>
        </tr>
        <tr>
            <td><b>Template:</b></td>
            <td><span id="template"></span></td>
        </tr>
        <tr>
            <td><b>Created By:</b></td>
            <td><span id="createdBy"></span></td>
        </tr>
        <tr>
            <td><b>Date Created:</b></td>
            <td><span id="createdDate"></span></td>
        </tr>
        <tr>
            <td><b>Triggers:</b></td>
            <td><span id="dxCodeTriggers"></span></td>
        </tr>
        <tr>
            <td><b>Recommendation Colour:</b></td>
            <td><span id="recommendationColour"></span></td>
        </tr>
        <tr>
            <td><b>Warning Colour:</b></td>
            <td><span id="warningColour"></span></td>
        </tr>
    </table>
    <br/>

    Add new measurement type to flowsheet :

    <select id="types" onChange="addMeasurement()">
        <option value="0">Select Below</option>
    </select>

    &nbsp;&nbsp;&nbsp;

    <select id="preventionTypes" onChange="addPrevention()">
        <option value="0">Select Below</option>
    </select>

    <br/>

    <table id="itemTable" name="itemTable" class="table table-bordered table-striped table-hover table-sm">
        <thead>
        <tr>
            <th></th>
            <th>Type</th>
            <th>Display Name</th>
            <th>Guideline</th>
            <th>Graphable</th>
            <th>Measuring Instruction</th>
            <th>Validation</th>
        </tr>
        </thead>
        <tbody>
        </tbody>
    </table>

    </body>
</html>