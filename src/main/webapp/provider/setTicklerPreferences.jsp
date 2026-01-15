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

<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/casemgmt/taglibs.jsp" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@page import="java.util.*" %>
<%@ page import="java.util.ResourceBundle"%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
"http://www.w3.org/TR/html4/loose.dtd">
<%
    ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", request.getLocale());

    String providertitle = (String) request.getAttribute("providertitle");
    String providermsgPrefs = (String) request.getAttribute("providermsgPrefs");
    String providerbtnCancel = (String) request.getAttribute("providerbtnCancel");
    String providerMsg = (String) request.getAttribute("providerMsg");
    String providerbtnSubmit = (String) request.getAttribute("providerbtnSubmit");
    String providerbtnClose = (String) request.getAttribute("providerbtnClose");
%>
<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title><%=bundle.getString(providertitle)%></title>
        <link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/oscarEncounter/encounterStyles.css">

        <style>
            input[type="radio"] {
                margin-left: 20px
            }
        </style>
    </head>

    <body class="BodyStyle" vlink="#0000FF">
    <table class="MainTable" id="scrollNumber1" name="encounterTable">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn" style="width:135px">
                <%=bundle.getString(providermsgPrefs)%>
            </td>
            <td style="color: white" class="MainTableTopRowRightColumn"></td>
        </tr>
        <tr>
            <td class="MainTableLeftColumn"></td>
            <td class="MainTableRightColumn">
                <%if (request.getAttribute("status") == null) {%>
                <form action="${pageContext.request.contextPath}/setTicklerPreferences.do" method="post">
                    <input type="hidden" name="method" value="<c:out value="${method}"/>">

                    <h2>Default Tickler Task Assignee:</h2>
                    <h3><c:out value="${providerMsg}"/></h3>

                    <input type="radio" id="taskAssigneeDefault" name="taskAssigneeMRP.value" value="default"
                        <c:if test="${taskAssigneeMRPValue == 'default'}">checked</c:if> onclick="checkAssignee()" /> Default

                    <input type="radio" id="taskAssigneeMRP" name="taskAssigneeMRP.value" value="mrp"
                        <c:if test="${taskAssigneeMRPValue == 'mrp'}">checked</c:if> onclick="checkAssignee()" /> MRP

                    <input type="radio" id="taskAssigneeProvider" name="taskAssigneeMRP.value" value="provider"
                        <c:if test="${taskAssigneeMRPValue == 'providers'}">checked</c:if> onclick="checkAssignee()" /> Set a provider

                    <input type="hidden" id="taskAssignee" name="taskAssigneeSelection.value" />

                    <div style="margin-top:20px;margin-bottom:20px;padding-left:20px;height:50px">
                        <div style="display:none;" id="taskAssigneeDefaultContainer">
                            <h3>No preference set.</h3>
                        </div>

                        <div style="display:none;" id="taskAssigneeMRPContainer">
                            <h3>Most Responsible Physician (MRP) as specified on the patients master record
                                (demographics).</h3>
                        </div>

                        <div style="display:none;" id="taskAssigneeProviderContainer">
                            <h3>Select a provider from the list to set as your default assignee:</h3>
                            <br>
                            <select name="taskAssigneeSelection.value" onchange="updateTaskAssignee(this.value)">
                                <c:forEach var="provider" items="${providerSelect}">
                                    <option value="${provider.value}"
                                        <c:if test="${fn:trim(selectedProvider) == fn:trim(provider.value)}">selected</c:if>>
                                        ${provider.label}
                                    </option>
                                </c:forEach>
                            </select>
                        </div>
                    </div>

                    <input type="submit" value="<%=bundle.getString(providerbtnSubmit)%>"/>
                    <input type="button" value="<%=bundle.getString(providerbtnCancel)%>"
                           onclick="window.close();"/>
                </form>
                <%} else {%>
                <h1><%=bundle.getString(providerMsg)%></h1>
                <br/><br/>
                <input type="button" value="<%=bundle.getString(providerbtnClose)%>" onclick="window.close();"/>
                <%}%>
            </td>
        </tr>
        <tr>
            <td class="MainTableBottomRowLeftColumn"></td>
            <td class="MainTableBottomRowRightColumn"></td>
        </tr>
    </table>

    <script>
        function checkAssignee() {
            one = document.getElementById("taskAssigneeDefault");
            divDefault = document.getElementById("taskAssigneeDefaultContainer");

            if (one.checked) {
                divDefault.style.display = "block";
                updateTaskAssignee('');//clear
            } else {
                divDefault.style.display = "none";
            }

            mrp = document.getElementById("taskAssigneeMRP");
            divMRP = document.getElementById("taskAssigneeMRPContainer");

            if (mrp.checked) {
                divMRP.style.display = "block";
                updateTaskAssignee('mrp');
            } else {
                divMRP.style.display = "none";
            }

            provider = document.getElementById("taskAssigneeProvider");
            divProvider = document.getElementById("taskAssigneeProviderContainer");

            if (provider.checked) {
                divProvider.style.display = "block";
            } else {
                divProvider.style.display = "none";
            }
        }

        function updateTaskAssignee(v) {
            el = document.getElementById("taskAssignee");
            el.value = v;
        }

        function updateProviderSelect() {
            var savedAssignee = document.forms[0]['taskAssigneeSelection.value'].value;
            if (savedAssignee.length > 0 && savedAssignee != 'mrp') {
                document.forms[0]['taskAssigneeProvider.value'].value = savedAssignee;
            }
        }

        updateProviderSelect();

        window.onload = function () {
            checkAssignee();
        };
    </script>

    </body>
</html>
