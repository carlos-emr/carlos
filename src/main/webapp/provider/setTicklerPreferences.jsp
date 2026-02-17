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

<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ page import="java.util.ResourceBundle" %>
<%
    ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", request.getLocale());

    String providertitle = (String) request.getAttribute("providertitle");
    String providermsgPrefs = (String) request.getAttribute("providermsgPrefs");
    String providerbtnCancel = (String) request.getAttribute("providerbtnCancel");
    String providerMsg = (String) request.getAttribute("providerMsg");
    String providerbtnSubmit = (String) request.getAttribute("providerbtnSubmit");
    String providerbtnClose = (String) request.getAttribute("providerbtnClose");
%>

<!DOCTYPE html>
<html lang="en">
    <head>
        <%@ include file="/includes/global-head.jspf" %>
        <title><%=bundle.getString(providertitle)%></title>
    </head>

    <body>
    <div class="container">

        <div class="page-header-bar">
            <h4 class="page-header-title">
                <i class="fas fa-tasks page-header-icon"></i>&nbsp;<%=bundle.getString(providertitle)%>
            </h4>
        </div>

        <%if (request.getAttribute("status") == null) {%>
        <form action="${pageContext.request.contextPath}/setTicklerPreferences.do" method="post" class="mt-3">
            <input type="hidden" name="method" value="<c:out value="${method}"/>">

            <h5>Default Tickler Task Assignee:</h5>
            <c:if test="${not empty providerMsg}">
                <p class="text-muted"><c:out value="${providerMsg}"/></p>
            </c:if>

            <div class="mt-3">
                <div class="form-check mb-2">
                    <input type="radio" class="form-check-input" id="taskAssigneeDefault"
                           name="taskAssigneeMRP.value" value="default"
                           <c:if test="${taskAssigneeMRPValue == 'default'}">checked</c:if>
                           onclick="checkAssignee()">
                    <label class="form-check-label" for="taskAssigneeDefault">Default</label>
                </div>

                <div class="form-check mb-2">
                    <input type="radio" class="form-check-input" id="taskAssigneeMRP"
                           name="taskAssigneeMRP.value" value="mrp"
                           <c:if test="${taskAssigneeMRPValue == 'mrp'}">checked</c:if>
                           onclick="checkAssignee()">
                    <label class="form-check-label" for="taskAssigneeMRP">MRP</label>
                </div>

                <div class="form-check mb-2">
                    <input type="radio" class="form-check-input" id="taskAssigneeProvider"
                           name="taskAssigneeMRP.value" value="provider"
                           <c:if test="${taskAssigneeMRPValue == 'providers'}">checked</c:if>
                           onclick="checkAssignee()">
                    <label class="form-check-label" for="taskAssigneeProvider">Set a provider</label>
                </div>
            </div>

            <input type="hidden" id="taskAssignee" name="taskAssigneeSelection.value">

            <div class="mt-3 mb-3 ps-3" style="min-height:60px;">
                <div style="display:none;" id="taskAssigneeDefaultContainer">
                    <p class="text-muted">No preference set.</p>
                </div>

                <div style="display:none;" id="taskAssigneeMRPContainer">
                    <p class="text-muted">Most Responsible Physician (MRP) as specified on the patients master record (demographics).</p>
                </div>

                <div style="display:none;" id="taskAssigneeProviderContainer">
                    <p class="text-muted mb-2">Select a provider from the list to set as your default assignee:</p>
                    <select name="taskAssigneeSelection.value" class="form-select form-select-sm" style="max-width:300px;"
                            onchange="updateTaskAssignee(this.value)">
                        <c:forEach var="provider" items="${providerSelect}">
                            <option value="${provider.value}"
                                <c:if test="${fn:trim(selectedProvider) == fn:trim(provider.value)}">selected</c:if>>
                                ${provider.label}
                            </option>
                        </c:forEach>
                    </select>
                </div>
            </div>

            <div class="d-flex gap-2">
                <input type="submit" class="btn btn-primary btn-sm" value="<%=bundle.getString(providerbtnSubmit)%>">
            </div>
        </form>

        <%} else {%>
        <div class="alert alert-success mt-3"><%=bundle.getString(providerMsg)%></div>
        <%}%>

    </div>

    <script>
        function checkAssignee() {
            var one = document.getElementById("taskAssigneeDefault");
            var divDefault = document.getElementById("taskAssigneeDefaultContainer");

            if (one.checked) {
                divDefault.style.display = "block";
                updateTaskAssignee('');
            } else {
                divDefault.style.display = "none";
            }

            var mrp = document.getElementById("taskAssigneeMRP");
            var divMRP = document.getElementById("taskAssigneeMRPContainer");

            if (mrp.checked) {
                divMRP.style.display = "block";
                updateTaskAssignee('mrp');
            } else {
                divMRP.style.display = "none";
            }

            var provider = document.getElementById("taskAssigneeProvider");
            var divProvider = document.getElementById("taskAssigneeProviderContainer");

            if (provider.checked) {
                divProvider.style.display = "block";
            } else {
                divProvider.style.display = "none";
            }
        }

        function updateTaskAssignee(v) {
            document.getElementById("taskAssignee").value = v;
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
