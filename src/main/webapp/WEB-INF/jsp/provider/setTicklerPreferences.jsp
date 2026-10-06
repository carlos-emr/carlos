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

<%--
    setTicklerPreferences.jsp

    POSTs to setTicklerPreferences via method 

    Provides UI for provider Tickler settings

    @param method       
    @since 2017-07-28
	
--%>

<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>

<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>

<!DOCTYPE html>
<html lang="${pageContext.request.locale.language}">
    <head>
        <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>
    <%--    The global-head.jspf fragment provides:
        - Viewport meta tag for responsive design
        - global.js (legacy focus/refresh helpers)
        - jQuery 3.7.1
        - Bootstrap 5.3.3 (JS bundle + CSS)
        - jQuery UI 1.14.2 CSS (JS must be included page-specifically where dialogs/widgets are needed)
        - Font Awesome 6.7.2 (icon library)
        - searchBox.css (shared search/form styles)
        - global.css (CARLOS design tokens and common classes)
    --%>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <title><fmt:message key="provider.providerpreference.link.ticklerPrefs"/></title>
    </head>
    <body>
    <div class="container">

    <!-- ============================================================
         PAGE HEADER BAR — short title + long title (icon optional).
         Mirrors the OSCAR MainTableTopRow / TopStatusBar pattern.
         Structure:
           [icon?] [Short Title]   [Long Title .....................]
         ============================================================ -->
    <div class="page-header-bar d-flex align-items-center justify-content-between
                py-2 mb-3 border-bottom" id="header">
        <div class="d-flex align-items-center gap-2">
            <i class="fa-solid fa-user-gear" aria-hidden="true"></i>
            <span class="fw-semibold"><fmt:message key="provider.providerpreference.link.ticklerPrefs"/></span>
        </div>
        <div class="text-muted small"><fmt:message key="provider.setTicklerPreferences.header"/></div>
    </div>
        <%if (request.getAttribute("status") == null) {%>
            <c:if test="${ticklerPreferenceError}">
                <div class="alert alert-danger" role="alert"><fmt:message key="provider.ticklerPreference.invalidAssignee"/></div>
            </c:if>
            <form action="${pageContext.request.contextPath}/setTicklerPreferences" method="post">
                <input type="hidden" name="method" value="${carlos:forHtmlAttribute(method)}">
    <!-- ============================================================
         MAIN CONTENT WRAPPER — light background card to separate
         page content from the body background.
         ============================================================ -->
    <div class="bg-light border rounded p-2">

            <!-- ==================================================
                 CONTENT ROW — two-column layout:
                   col-12 col-md-2 : left sidebar  (OSCAR left col)
                   col-12 col-md-10: right content (OSCAR right col)
                 On small screens both columns stack vertically.
                 ================================================== -->
            <div class="row g-2">

                <!-- LEFT SIDEBAR COLUMN
                     Mirrors: MainTableLeftColumn
                     Contains navigation links / contextual actions. -->
                <div class="col-12 col-md-2">

                </div>
                <!-- RIGHT CONTENT COLUMN
                     Mirrors: MainTableRightColumn
                     Contains the primary form and data entry area. -->
                <div class="col-12 col-md-10">

                    <!-- input group -->
                    <div class="mb-3">
                    	<span><fmt:message key="provider.setLabRecallPrefs.delegate"/></span>
                    	<div class="form-check">
                        <input type="radio" id="taskAssigneeDefault" name="taskAssigneeMRP.value" 
                        value="default"
                        <c:if test="${taskAssigneeMRPValue == 'default'}">checked</c:if> onclick="checkAssignee()" /> <fmt:message key="provider.setTicklerPreferences.defaultOption"/>
			</div>
			<div class="form-check">
                    	<input type="radio" id="taskAssigneeMRP" name="taskAssigneeMRP.value" 
                    	title="<fmt:message key="provider.setTicklerPreferences.mrpDescription"/>"
                    	value="mrp"
                        <c:if test="${taskAssigneeMRPValue == 'mrp'}">checked</c:if> onclick="checkAssignee()" /> <fmt:message key="tickler.ticklerMain.MRP"/>
			</div>
			<div class="form-check">
                    	<input type="radio" id="taskAssigneeProvider" name="taskAssigneeMRP.value" 
                    	value="provider"
                        <c:if test="${taskAssigneeMRPValue == 'provider'}">checked</c:if> onclick="checkAssignee()" /> <fmt:message key="provider.setTicklerPreferences.setProviderOption"/>

                    	<input type="hidden" id="taskAssignee" name="taskAssigneeSelection.value" />
			</div>
                    </div>
                    <div class="mb-3">
                        <div style="display:none;" id="taskAssigneeDefaultContainer">
                            <span><fmt:message key="provider.setTicklerPreferences.noPreference"/></span>
                        </div>

                        <div style="display:none;" id="taskAssigneeMRPContainer">
                            <span><fmt:message key="provider.setTicklerPreferences.mrpDescription"/></span>
                        </div>

                        <div style="display:none;" id="taskAssigneeProviderContainer">
                            <label for="assigneeSelect"><fmt:message key="provider.setTicklerPreferences.providerDescription"/></label>
                            <br>
                            <select id="assigneeSelect" onchange="updateTaskAssignee(this.value)" class="form-select form-select-sm" title="<fmt:message key='admin.jobs.choose'/>">
                                <c:forEach var="provider" items="${providerSelect}">
                                    <option value="${carlos:forHtmlAttribute(provider.value)}"
                                        <c:if test="${fn:trim(selectedProvider) == fn:trim(provider.value)}">selected</c:if>>
                                        ${carlos:forHtmlContent(provider.label)}
                                    </option>
                                </c:forEach>
                            </select>
                        </div>
                    </div>
                          
                    <!-- Primary action -->
                    <div class="mb-2">
			<input type="submit" class="btn btn-primary btn-sm" value=" <fmt:message key="global.btnSubmit"/>">
                        <input type="button" class="btn btn-secondary btn-sm" value="<fmt:message key="global.btnCancel"/>" onclick="window.close();">
                    </div>

                </div><!-- end right column -->

            </div><!-- end .row -->

    </div><!-- end .bg-light -->                           
        </form> <%} else {%>
		<div id="AlertBanner"
			class="alert alert-success alert-dismissible"
			role="alert">
			<span id="AlertText"><fmt:message key="provider.ticklerPreference.savedMsg"/></span>
			<button type="button"
			class="btn-close"
			onclick="this.closest('.alert').style.display='none'"
			aria-label="<fmt:message key="global.btnClose"/>"></button>
		</div>
        <br/><br/>
                <input type="button" class="btn btn-primary btn-sm" value="<fmt:message key="global.btnClose"/>" onclick="window.close();"/>
<% } %>

    <%if (request.getAttribute("status") == null) {%>
    <script>
        function updateTaskAssignee(value) {
            document.getElementById("taskAssignee").value = value;
        }

        function checkAssignee() {
            const defaultSelected = document.getElementById("taskAssigneeDefault").checked;
            const mrpSelected = document.getElementById("taskAssigneeMRP").checked;
            const providerSelected = document.getElementById("taskAssigneeProvider").checked;
            const select = document.getElementById("assigneeSelect");
            document.getElementById("taskAssigneeDefaultContainer").style.display = defaultSelected ? "block" : "none";
            document.getElementById("taskAssigneeMRPContainer").style.display = mrpSelected ? "block" : "none";
            document.getElementById("taskAssigneeProviderContainer").style.display = providerSelected ? "block" : "none";
            select.disabled = !providerSelected;
            updateTaskAssignee(providerSelected ? select.value : mrpSelected ? "mrp" : "");
        }

        checkAssignee();
    </script>
    <%}%>

    </body>
</html>
