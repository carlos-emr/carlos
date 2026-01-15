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

<%-- 
    Document   : listGuidelines
    Created on : 29-Jun-2009, 1:14:43 AM
    Author     : apavel
--%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
"http://www.w3.org/TR/html4/loose.dtd">

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_eChart" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_eChart");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<%@ page import="ca.openosp.openo.decisionSupport.model.DSGuideline" %>

<%
    pageContext.setAttribute("demographic_no", request.getParameter("demographic_no"));
    pageContext.setAttribute("provider_no", request.getParameter("provider_no"));
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
%>

<html>
<head>
    <title>GuidelineList</title>
    <link rel="stylesheet" href="decisionSupport.css" type="text/css"></link>
</head>
<body>
<div style="font-size: 16px; font-weight: bold;"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarencounter.guidelinelist.youcurrently"/></div>
<c:if test="${not empty demographic_no}">
    <div style="font-size: 10px;"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarencounter.guidelinelist.demographicno"/> <c:out value="${demographic_no}"/></div>
</c:if>
<br>
<table class="dsTable">
    <tr>
        <th>Version</th>
        <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarencounter.guidelinelist.title"/></th>
        <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarencounter.guidelinelist.author"/></th>
        <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarencounter.guidelinelist.dateimported"/></th>
        <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarrx.showallergies.status"/></th>
        <c:if test="${not empty demographic_no}">
            <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarencounter.guidelinelist.evaluated"/></th>
        </c:if>

    </tr>
    <c:forEach var="guideline" items="${guidelines}" varStatus="index" >
        <c:set var="cssClass" value="${index.index % 2 == 0 ? 'even' : 'odd'}"/>
        <tr class="${cssClass}">
            <td>${guideline.version}</td>
            <td>${guideline.title}</td>
            <td>${guideline.author}</td>
            <td><fmt:formatDate value="${guideline.dateStart}" pattern="MMM d, yyyy"/></td>
            <td>
                <c:choose>
                    <c:when test="${guideline.status == 'A'}">
                        <span class="good"><fmt:message key="oscarencounter.guidelinelist.active"/></span>
                    </c:when>
                    <c:when test="${guideline.status == 'F'}">
                    <span class="bad">
                        <fmt:message key="oscarencounter.guidelinelist.failedon"/>
                        <fmt:formatDate value="${guideline.dateDecomissioned}" pattern="MMM d, yyyy"/>
                        <fmt:message key="oscarencounter.guidelinelist.invalid"/>
                    </span>
                    </c:when>
                </c:choose>
            </td>

            <c:if test="${not empty param.demographic_no}">
                <c:set var="dsGuideline" value="${guideline}"/>
                <c:set var="passed" value="${dsGuideline.evaluate(loggedInInfo, param.demographic_no) != null}"/>
                <td>
                    <c:choose>
                        <c:when test="${passed}">
                            <span class="good"><fmt:message key="oscarencounter.guidelinelist.passed"/></span>
                        </c:when>
                        <c:otherwise>
                            <span class="bad"><fmt:message key="oscarencounter.guidelinelist.failed"/></span>
                        </c:otherwise>
                    </c:choose>
                    - <a href="${pageContext.request.contextPath}/oscarEncounter/decisionSupport/guidelineAction.do?method=detail&guidelineId=${guideline.id}&provider_no=${provider_no}&demographic_no=${demographic_no}">
                    <fmt:message key="oscarencounter.guidelinelist.moreinfo"/>
                </a>
                </td>
            </c:if>
        </tr>
    </c:forEach>
</table>
</body>
</html>
