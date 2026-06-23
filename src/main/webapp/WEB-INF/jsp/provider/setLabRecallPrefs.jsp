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
    setLabRecallPrefs.jsp

    POSTs to setProviderStaleDate via method saveLabRecallPrefs

    Provides UI for Lab Recall settings

    @param method       viewLabRecall
    @since 2017-07-28
	
--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/casemgmt/taglibs.jsp" %>
<%@ page import="java.util.*" %>
<%@ page import="java.util.ResourceBundle"%>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>

<%
    if (session.getAttribute("user") == null)
        response.sendRedirect(request.getContextPath() + "/logout.htm");

    ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", request.getLocale());

    String providertitle = (String) request.getAttribute("providertitle");
    String providermsgPrefs = (String) request.getAttribute("providermsgPrefs");
    String providermsgProvider = (String) request.getAttribute("providermsgProvider");
    String providermsgEdit = (String) request.getAttribute("providermsgEdit");
    String providermsgSuccess = (String) request.getAttribute("providermsgSuccess");
%>

<!DOCTYPE html>
<html lang="${pageContext.request.locale.language}">
<head>
	<link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
	<base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
	
    <title><fmt:message key="provider.btnLabRecallSettings"/></title>
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

</head>
<body>

<!-- ================================================================
     CONTAINER — outermost wrapper; constrains max-width and centers
     content on large screens while staying full-width on mobile.
     ================================================================ -->
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
            <span class="fw-semibold"><fmt:message key="provider.setLabRecall.msgProfileView"/></span>
        </div>
        <div class="text-muted small"><fmt:message key="provider.setLabRecall.title"/></div>
    </div>
	<%if (request.getAttribute("status") == null) {%>
			<%=bundle.getString(providermsgEdit)%>
			<form action="${pageContext.request.contextPath}/setProviderStaleDate" method="post">
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
                        <label for="labRecallDelegate.value" class="form-label form-label-sm" 
							title="<fmt:message key="provider.setLabRecallPrefs.required"/>">
                            <fmt:message key="provider.setLabRecallPrefs.delegate"/><span style="color:red;">*</span>
                        </label>
                        <select name="labRecallDelegate.value" id="labRecallDelegate.value" onchange="delegateCheck();" title="<fmt:message key="admin.jobs.choose"/>"
						class="form-select form-select-sm">
                            <c:forEach var="provider" items="${providerSelect}">
                                <option value="${carlos:forHtmlAttribute(provider.value)}" <c:if test="${provider.value == labRecallDelegate.value}">selected</c:if> >
                                    ${carlos:forHtmlContent(provider.label)}
                                </option>
                            </c:forEach>
                        </select>
                    </div>
                    <!-- input group -->
                    <div class="mb-3">
                        <label for="labRecallMsgSubject.value" class="form-label form-label-sm">
                            <fmt:message key="provider.setLabRecallPrefs.defaultSubject"/>
                        </label>
                        <input type="text" name="labRecallMsgSubject.value" id="labRecallMsgSubject.value" class="form-control"
						value="${carlos:forHtmlAttribute(subject.value)}" />
                    </div>
                    <!-- input group -->
                    <div class="mb-3">
                        <div class="form-label form-label-sm">
                            <fmt:message key="provider.setLabRecallPrefs.ticklerAssignee"/>
                        </div>
                        <div class="form-check">
                            <input type="checkbox" name="labRecallTicklerAssignee.checked" id="labRecallTicklerAssignee.checked" class="form-check-input" <c:if test="${labRecallTicklerAssignee.checked}">checked</c:if> />
                            <label class="form-check-label" for="labRecallTicklerAssignee.checked">
                                <fmt:message key="provider.setLabRecallPrefs.defaultToDelegate"/>
                            </label>
                        </div>
                    </div>
                    <!-- input group -->
                    <div class="mb-3">
                        <label for="labRecallTicklerPriority.value" class="form-label form-label-sm">
                            <fmt:message key="provider.setLabRecallPrefs.ticklerPriority"/>
                        </label>
                        <select name="labRecallTicklerPriority.value" id="labRecallTicklerPriority.value" 
						class="form-select form-select-sm">
                            <option value="" ><fmt:message key="admin.jobs.choose"/></option>
                            <option value="High" <c:if test="${'High' eq labRecallTicklerPriority.value}">
selected</c:if>><fmt:message key="tickler.ticklerMain.priority.high"/></option>
							<option value="Normal" <c:if test="${'Normal' eq labRecallTicklerPriority.value}">
selected</c:if>><fmt:message key="tickler.ticklerMain.priority.normal"/></option>
							<option value="Low" <c:if test="${'Low' eq labRecallTicklerPriority.value}">
selected</c:if>><fmt:message key="tickler.ticklerMain.priority.low"/></option>
                        </select>
                    </div>					
                    <!-- Primary action -->
                    <div class="mb-2">
						<input type="submit" name="btnApply" class="btn btn-primary btn-sm" value=" <fmt:message key="provider.setLabRecallPrefs.submit"/>">
                        <input type="button" name="delete" class="btn btn-danger btn-sm" value="<fmt:message key="provider.setLabRecallPrefs.delete"/>" onclick="deleteProp();" style="display:none;">
                    </div>

                </div><!-- end right column -->

            </div><!-- end .row -->

    </div><!-- end .bg-light -->
				</form> <%} else {%>
				<div id="AlertBanner"
					 class="alert alert-success alert-dismissible"
					 role="alert">
					<span id="AlertText"><%=providermsgSuccess%>"/></span>
					<button type="button"
							class="btn-close"
							onclick="this.closest('.alert').style.display='none'"
							aria-label="<fmt:message key="global.btnCancel"/>"></button>
				</div>
        <br/><br/>
                <input type="button" class="btn btn-primary btn-sm" value="<fmt:message key="global.btnClose"/>" onclick="window.close();"/>
            <%}%>

</div><!-- end .container -->

    <script>
        function deleteProp() {
            var r = confirm('<carlos:encode value="<%=bundle.getString(\"provider.setLabRecallPrefs.confirmDelete\") %>" context="javascriptBlock"/>');
            if (r == true) {
                document.forms[0].reset();
                document.forms[0]['labRecallDelegate.value'].value = "";
                document.forms[0].submit();
            }
        }

        function delegateCheck() {
            var delegate = document.forms[0]['labRecallDelegate.value'].value;
            if (delegate != "") {
                document.forms[0]['btnApply'].disabled = false;
            } else {
                document.forms[0]['btnApply'].disabled = true;
            }
        }

        if (document.forms.length > 0) {
            delegateCheck();

            if (document.forms[0]['labRecallDelegate.value'].value != "") {
                document.forms[0]['delete'].style.display = "inline";
            }
        }

    </script>

</body>
</html>
