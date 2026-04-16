<%--


    Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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

    This software was written for
    Centre for Research on Inner City Health, St. Michael's Hospital,
    Toronto, Ontario, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
<%@ include file="/taglibs.jsp" %>
<%@ include file="/WEB-INF/jsp/common/messages.jsp" %>
<c:set var="selectedTab" value="${requestScope.tab}"/>
<c:if test="${empty selectedTab}">
    <c:set var="selectedTab" value="${param.tab}"/>
</c:if>
<c:if test="${empty selectedTab}">
    <c:set var="selectedTab" value="General"/>
</c:if>

<table width="100%">
    <tr>
        <td style="text-align: right;" align="right"><c:out value="${program.name}"/></td>
    </tr>
</table>

<div class="tabs">
    <table cellpadding="0" cellspacing="0" border="0">
        <tr>
            <td <c:if test="${selectedTab == 'General'}">style="background-color: #555;"</c:if>>
                <a href="${pageContext.request.contextPath}/PMmodule/ProgramManagerView?id=<c:out value="${requestScope.id}"/>">General</a>
            </td>
            <td <c:if test="${selectedTab == 'Staff'}">style="background-color: #555;"</c:if>>
                <a href="${pageContext.request.contextPath}/PMmodule/ProgramManagerView?id=<c:out value="${requestScope.id}"/>&amp;tab=Staff">Staff</a>
            </td>
            <td <c:if test="${selectedTab == 'Function User'}">style="background-color: #555;"</c:if>>
                <a href="${pageContext.request.contextPath}/PMmodule/ProgramManagerView?id=<c:out value="${requestScope.id}"/>&amp;tab=Function%20User">Function User</a>
            </td>
            <td <c:if test="${selectedTab == 'Teams'}">style="background-color: #555;"</c:if>>
                <a href="${pageContext.request.contextPath}/PMmodule/ProgramManagerView?id=<c:out value="${requestScope.id}"/>&amp;tab=Teams">Teams</a>
            </td>
            <td <c:if test="${selectedTab == 'Clients'}">style="background-color: #555;"</c:if>>
                <a href="${pageContext.request.contextPath}/PMmodule/ProgramManagerView?id=<c:out value="${requestScope.id}"/>&amp;tab=Clients">Clients</a>
            </td>
            <td <c:if test="${selectedTab == 'Queue'}">style="background-color: #555;"</c:if>>
                <a href="${pageContext.request.contextPath}/PMmodule/ProgramManagerView?id=<c:out value="${requestScope.id}"/>&amp;tab=Queue">Queue</a>
            </td>
            <td <c:if test="${selectedTab == 'Access'}">style="background-color: #555;"</c:if>>
                <a href="${pageContext.request.contextPath}/PMmodule/ProgramManagerView?id=<c:out value="${requestScope.id}"/>&amp;tab=Access">Access</a>
            </td>
            <td <c:if test="${selectedTab == 'Client Status'}">style="background-color: #555;"</c:if>>
                <a href="${pageContext.request.contextPath}/PMmodule/ProgramManagerView?id=<c:out value="${requestScope.id}"/>&amp;tab=Client%20Status">Client Status</a>
            </td>
            <td <c:if test="${selectedTab == 'Service Restrictions'}">style="background-color: #555;"</c:if>>
                <a href="${pageContext.request.contextPath}/PMmodule/ProgramManagerView?id=<c:out value="${requestScope.id}"/>&amp;tab=Service%20Restrictions">Service Restrictions</a>
            </td>
            <td <c:if test="${selectedTab == 'Vacancies'}">style="background-color: #555;"</c:if>>
                <a href="${pageContext.request.contextPath}/PMmodule/ProgramManagerView?id=<c:out value="${requestScope.id}"/>&amp;tab=Vacancies">Vacancies</a>
            </td>
        </tr>
    </table>
</div>

<form name="programManagerViewForm" action="${pageContext.request.contextPath}/PMmodule/ProgramManagerView" method="post">
    <input type="hidden" name="id" value="<c:out value="${requestScope.id}"/>"/>
    <input type="hidden" name="tab" value="<c:out value="${selectedTab}"/>"/>
    <input type="hidden" name="method" value=""/>
    <input type="hidden" name="vacancyOrTemplateId" value="<c:out value="${requestScope.vacancyOrTemplateId}"/>"/>

    <c:choose>
        <c:when test="${selectedTab == 'Staff'}">
            <jsp:include page="/WEB-INF/jsp/PMmodule/Admin/ProgramView/staff.jsp"/>
        </c:when>
        <c:when test="${selectedTab == 'Function User'}">
            <jsp:include page="/WEB-INF/jsp/PMmodule/Admin/ProgramView/function_user.jsp"/>
        </c:when>
        <c:when test="${selectedTab == 'Teams'}">
            <jsp:include page="/WEB-INF/jsp/PMmodule/Admin/ProgramView/teams.jsp"/>
        </c:when>
        <c:when test="${selectedTab == 'Clients'}">
            <jsp:include page="/WEB-INF/jsp/PMmodule/Admin/ProgramView/clients.jsp"/>
        </c:when>
        <c:when test="${selectedTab == 'Queue'}">
            <jsp:include page="/WEB-INF/jsp/PMmodule/Admin/ProgramView/queue.jsp"/>
        </c:when>
        <c:when test="${selectedTab == 'Access'}">
            <jsp:include page="/WEB-INF/jsp/PMmodule/Admin/ProgramView/access.jsp"/>
        </c:when>
        <c:when test="${selectedTab == 'Client Status'}">
            <jsp:include page="/WEB-INF/jsp/PMmodule/Admin/ProgramView/client_status.jsp"/>
        </c:when>
        <c:when test="${selectedTab == 'Service Restrictions'}">
            <jsp:include page="/WEB-INF/jsp/PMmodule/Admin/ProgramView/service_restrictions.jsp"/>
        </c:when>
        <c:when test="${selectedTab == 'Vacancies'}">
            <jsp:include page="/WEB-INF/jsp/PMmodule/Admin/ProgramView/vacancies.jsp"/>
        </c:when>
        <c:otherwise>
            <jsp:include page="/WEB-INF/jsp/PMmodule/Admin/ProgramView/general.jsp"/>
        </c:otherwise>
    </c:choose>
</form>
