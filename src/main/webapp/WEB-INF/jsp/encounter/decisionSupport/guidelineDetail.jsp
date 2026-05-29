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
    Document   : listGuidelines
    Created on : 29-Jun-2009, 1:14:43 AM
    Author     : apavel
--%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
"http://www.w3.org/TR/html4/loose.dtd">

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>


<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_eChart" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_eChart");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ page import="io.github.carlos_emr.carlos.decisionSupport.model.DSGuideline" %>
<%@ page import="java.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.decisionSupport.model.DSCondition" %>

<%
    pageContext.setAttribute("demographic_no", request.getParameter("demographic_no"));
    pageContext.setAttribute("provider_no", request.getParameter("provider_no"));

%>

<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <title>GuidelineList</title>
    <link rel="stylesheet" href="decisionSupport.css" type="text/css"></link>
</head>
<body>
<div style="font-size: 16px; font-weight: bold;"><fmt:message key="encounter.guidelinedetail.guidelineassessment"/> ${carlos:forHtml(patientName)}</div>
<br>
<c:if test="${not empty consequences}">
    <c:forEach items="${consequences}" var="consequence">
                    <span class="good" style="font-size: 12px; font-weight: bold;">
                    GUIDELINE PASSED: ${carlos:forHtml(consequence.text)}<br/>
                    </span>
    </c:forEach>
</c:if>
<table style="font-size: 10px;  border-top: 1px solid black; border-collapse: collapse; margin-top: 15px;">
    <tr>
        <th><fmt:message key="encounter.guidelinelist.title"/></th>
        <td>${carlos:forHtml(guideline.title)}</td>
    </tr>
    <tr>
        <th><fmt:message key="encounter.guidelinelist.author"/></th>
        <td>${carlos:forHtml(guideline.author)}</td>
    </tr>
    <tr>
        <th><fmt:message key="oscarrx.showallergies.startdate"/></th>
        <td>${carlos:forHtml(guideline.dateStart)}</td>
    </tr>
</table>
Conditions:
<table class="dsTable">
    <tr>
        <th>Type</th>
        <th><fmt:message key="oscarrx.showallergies.operator"/></th>
        <th><fmt:message key="oscarrx.showallergies.expected"/></th>
        <th><fmt:message key="oscarrx.showallergies.actual"/></th>
        <th><fmt:message key="oscarrx.showallergies.evaluate"/></th>
    </tr>
    <c:forEach var="conditionResult" items="${conditionResults}" varStatus="status">
        <tr class="${status.index % 2 == 1 ? 'odd' : 'even'}">
            <td>${carlos:forHtml(conditionResult.condition.conditionType)}</td>
            <td>${carlos:forHtml(conditionResult.condition.listOperator)}</td>
            <td>${carlos:forHtml(conditionResult.condition.values)}</td>
            <td>
                <c:choose>
                    <c:when test="${empty conditionResult.actualValues}"><span class="bad"><fmt:message key="encounter.guidelinedetail.error"/></span></c:when>
                    <c:otherwise>${carlos:forHtml(conditionResult.actualValues)}</c:otherwise>
                </c:choose>
            </td>
            <td style="text-align: center;">
                <c:choose>
                    <c:when test="${conditionResult.result == true}">
                        <span class="good"><fmt:message key="encounter.guidelinelist.passed"/></span>
                    </c:when>
                    <c:otherwise>
                        <span class="bad"><fmt:message key="encounter.guidelinelist.fail"/></span>
                    </c:otherwise>
                </c:choose>
            </td>
        </tr>
    </c:forEach>
</table>
<input type="button" value="<fmt:message key="encounter.guidelinedetail.btnlistguideline"/>"
       onclick="document.location='guidelineAction?method=list&demographic_no=${carlos:forJavaScript(demographic_no)}&provider_no=${carlos:forJavaScript(provider_no)}'">
</body>
</html>
