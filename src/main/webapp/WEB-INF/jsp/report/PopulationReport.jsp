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
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_report,_admin.reporting" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_report&type=_admin.reporting");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>
<!DOCTYPE html>
<html>
<head>
    <title><fmt:message key="report.PopulationReport.title"/></title>
    <link rel="stylesheet" href="<%=request.getContextPath() %>/css/fontawesome-all.min.css">
</head>
<body>

<%@ include file="/taglibs.jsp" %>

<div class="pb-2 mt-4 mb-3 border-bottom">
    <h4>
        <fmt:message key="admin.admin.popRpt"/>
        <div class="float-end">
            <button name='print' onClick='window.print()' class="btn btn-secondary">
                <i class="fa-solid fa-print"></i>
                <fmt:message key="global.btnPrint"/>
            </button>
        </div>
    </h4>
</div>

<table
        class="table table-bordered table-striped table-sm table-hover">
    <colgroup>
        <col style="width:58.333%">
        <col style="width:16.667%">
    </colgroup>
    <thead>
    <tr>
        <td><fmt:message key="report.PopulationReport.heading"/></td>
        <td>${carlos:forHtml(date)}</td>
    </tr>
    </thead>
    <tbody>
    <tr>
        <td><fmt:message key="report.PopulationReport.summary"/></td>
        <td>${carlos:forHtml(time)}</td>
    </tr>
    </tbody>
</table>

<!-- Shelter Population -->
<table
        class="table table-bordered table-striped table-sm table-hover">
    <colgroup>
        <col style="width:58.333%">
        <col style="width:16.667%">
    </colgroup>
    <caption><fmt:message key="report.PopulationReport.caption.homelessShelterPopulation"/></caption>
    <tbody>
    <tr>
        <th><fmt:message key="report.PopulationReport.label.usedPastYear"/></th>
        <td>${carlos:forHtml(shelterPopulation.pastYear)}</td>
    </tr>
    <tr class="odd">
        <th><fmt:message key="report.PopulationReport.label.currentIndividuals"/></th>
        <td>${carlos:forHtml(shelterPopulation.current)}</td>
    </tr>
    </tbody>
</table>

<!-- Shelter Usage -->
<table
        class="table table-bordered table-striped table-sm table-hover">
    <colgroup>
        <col style="width:33.333%">
        <col style="width:33.333%">
    </colgroup>
    <caption><fmt:message key="report.PopulationReport.caption.shelterUse"/></caption>
    <tbody>
    <tr>
        <th><fmt:message key="report.PopulationReport.label.lowUse"/></th>
        <td>${carlos:forHtml(shelterUsage.low)}</td>
    </tr>
    <tr class="odd">
        <th><fmt:message key="report.PopulationReport.label.moderateUse"/></th>
        <td>${carlos:forHtml(shelterUsage.medium)}</td>
    </tr>
    <tr>
        <th><fmt:message key="report.PopulationReport.label.highUse"/></th>
        <td>${carlos:forHtml(shelterUsage.high)}</td>
    </tr>
    </tbody>
</table>

<!-- Mortality -->
<c:if test="${not empty mortalities}">
    <table
            class="table table-bordered table-striped table-sm table-hover">
        <colgroup>
            <col style="width:41.667%">
            <col style="width:33.333%">
        </colgroup>
        <caption><fmt:message key="report.PopulationReport.caption.mortality"/></caption>
        <tbody>
        <tr>
            <th><fmt:message key="report.PopulationReport.label.deathCount"/></th>
            <td>${carlos:forHtml(mortalities.count)}</td>
        </tr>
        <tr class="odd">
            <th><fmt:message key="report.PopulationReport.label.deathRate"/></th>
            <td>${carlos:forHtml(mortalities.percent)}</td>
        </tr>
        </tbody>
    </table>
</c:if>

<!-- Major Medical Condition -->
<table
        class="table table-bordered table-striped table-sm table-hover">
    <caption><fmt:message key="report.PopulationReport.caption.majorMedicalCondition"/></caption>
    <colgroup>
        <col style="width:16.667%">
        <col style="width:25%">
        <col style="width:33.333%">
    </colgroup>
    <thead>
    <tr>
        <th><fmt:message key="report.PopulationReport.header.condition"/></th>
        <td><fmt:message key="report.PopulationReport.header.numCases"/></td>
        <td><fmt:message key="report.PopulationReport.header.prevalence"/></td>
    </tr>
    </thead>
    <tbody>
    <c:forEach varStatus="status" var="condition"
               items="${majorMedicalConditions}">
        <tr>
            <th>${carlos:forHtml(condition.key)}</th>
            <td><c:choose>
                <c:when
                        test="${condition.value.count > 0 && condition.value.count < 5}">1 - 5</c:when>
                <c:otherwise>
                    ${carlos:forHtml(condition.value.count)}
                </c:otherwise>
            </c:choose></td>
            <td>${carlos:forHtml(condition.value.percent)}</td>
        </tr>
    </c:forEach>
    </tbody>
</table>

<!-- Major Mental Illness -->
<table
        class="table table-bordered table-striped table-sm table-hover">
    <caption><fmt:message key="report.PopulationReport.caption.majorMentalIllness"/></caption>
    <colgroup>
        <col style="width:16.667%">
        <col style="width:25%">
        <col style="width:33.333%">
    </colgroup>
    <thead>
    <tr>
        <th><fmt:message key="report.PopulationReport.header.condition"/></th>
        <td><fmt:message key="report.PopulationReport.header.numCases"/></td>
        <td><fmt:message key="report.PopulationReport.header.prevalence"/></td>
    </tr>
    </thead>
    <tbody>
    <c:forEach varStatus="status" var="condition"
               items="${majorMentalIllnesses}">
        <tr>
            <th>${carlos:forHtml(condition.key)}</th>
            <td><c:choose>
                <c:when
                        test="${condition.value.count > 0 && condition.value.count < 5}">1 - 5</c:when>
                <c:otherwise>
                    ${carlos:forHtml(condition.value.count)}
                </c:otherwise>
            </c:choose></td>
            <td>${carlos:forHtml(condition.value.percent)}</td>
        </tr>
    </c:forEach>
    </tbody>
</table>

<!-- Serious Medical Conditions -->
<table
        class="table table-bordered table-striped table-sm table-hover">
    <caption><fmt:message key="report.PopulationReport.caption.seriousMedicalConditions"/></caption>
    <colgroup>
        <col style="width:16.667%">
        <col style="width:25%">
        <col style="width:33.333%">
    </colgroup>
    <thead>
    <tr>
        <th><fmt:message key="report.PopulationReport.header.condition"/></th>
        <td><fmt:message key="report.PopulationReport.header.numCases"/></td>
        <td><fmt:message key="report.PopulationReport.header.prevalence"/></td>
    </tr>
    </thead>
    <tbody>
    <c:forEach varStatus="status" var="condition"
               items="${seriousMedicalConditions}">
        <tr>
            <th>${carlos:forHtml(condition.key)}</th>
            <td><c:choose>
                <c:when
                        test="${condition.value.count > 0 && condition.value.count < 5}">1 - 5</c:when>
                <c:otherwise>
                    ${carlos:forHtml(condition.value.count)}
                </c:otherwise>
            </c:choose></td>
            <td>${carlos:forHtml(condition.value.percent)}</td>
        </tr>
    </c:forEach>
    </tbody>
</table>

<!-- Notes -->
<table
        class="table table-bordered table-striped table-sm table-hover">
    <caption><fmt:message key="report.PopulationReport.caption.notes"/></caption>
    <colgroup>
        <col style="width:75%">
    </colgroup>
    <tbody>
    <tr>
        <td><fmt:message key="report.PopulationReport.note1"/></td>
    </tr>
    <tr>
        <td><fmt:message key="report.PopulationReport.note2"/></td>
    </tr>
    <tr>
        <td><fmt:message key="report.PopulationReport.note3"/></td>
    </tr>
    <tr>
        <td><fmt:message key="report.PopulationReport.note4"/></td>
    </tr>
    </tbody>
</table>

<c:forEach var="categoryCodeDescription"
           items="${categoryCodeDescriptions}">
    <table
            class="table table-bordered table-striped table-sm table-hover">
        <caption>
            ${carlos:forHtml(categoryCodeDescription.key)}
        </caption>
        <colgroup>
            <col style="width:16.667%">
            <col style="width:58.333%">
        </colgroup>
        <thead>
        <tr>
            <th><fmt:message key="report.PopulationReport.header.code"/></th>
            <th><fmt:message key="report.PopulationReport.header.description"/></th>
        </tr>
        </thead>
        <tbody>
        <c:forEach var="codeDescription"
                   items="${categoryCodeDescription.value}">
            <tr>
                <th>${carlos:forHtml(codeDescription.key)}</th>
                <td>${carlos:forHtml(codeDescription.value)}</td>
            </tr>
        </c:forEach>
        </tbody>
    </table>
</c:forEach>
</body>
</html>
