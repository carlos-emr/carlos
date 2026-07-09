<%@ taglib uri="carlos" prefix="carlos" %>
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
<fmt:setBundle basename="oscarResources"/>
<%@ include file="/WEB-INF/jsp/common/messages.jsp" %>
<c:url var="facilityViewUri" value="/PMmodule/FacilityManager">
    <c:param name="method" value="view"/>
    <c:param name="id" value="${requestScope.id}"/>
</c:url>

<div class="tabs" id="tabs">
    <table cellpadding="3" cellspacing="0" border="0">
        <tr>
            <fmt:message key="pmmodule.admin.viewFacility.titleFacility" var="titleFacility"/>
<th title="${carlos:forHtmlAttribute(titleFacility)}"><fmt:message key="pmmodule.admin.viewFacility.thFacilitySummary"/></th>
        </tr>
    </table>
</div>

<table width="100%" border="1" cellspacing="2" cellpadding="3">
    <tr class="b">
        <td width="20%"><fmt:message key='pmmodule.admin.viewFacility.tdFacilityId'/></td>
        <td>${carlos:forHtml(requestScope.id)}</td>
    </tr>
    <tr class="b">
        <td width="20%"><fmt:message key='pmmodule.admin.viewFacility.tdName'/></td>
        <td>${carlos:forHtml(facility.name)}</td>
    </tr>
    <tr class="b">
        <td width="20%"><fmt:message key='pmmodule.admin.viewFacility.tdDescription'/></td>
        <td>${carlos:forHtml(facility.description)}</td>
    </tr>
    <tr class="b">
        <td width="20%"><fmt:message key='pmmodule.admin.viewFacility.tdHic'/></td>
        <td>${carlos:forHtml(facility.hic)}</td>
    </tr>
    <tr class="b">
        <td width="20%"><fmt:message key='pmmodule.admin.viewFacility.tdPrimaryContactName'/></td>
        <td>${carlos:forHtml(facility.contactName)}</td>
    </tr>
    <tr class="b">
        <td width="20%"><fmt:message key='pmmodule.admin.viewFacility.tdPrimaryContactEmail'/></td>
        <td>${carlos:forHtml(facility.contactEmail)}</td>
    </tr>
    <tr class="b">
        <td width="20%"><fmt:message key='pmmodule.admin.viewFacility.tdPrimaryContactPhone'/></td>
        <td>${carlos:forHtml(facility.contactPhone)}</td>
    </tr>
    <tr class="b">
        <td width="20%"><fmt:message key="pmmodule.admin.viewFacility.tdDigitalSignaturesEnabled"/></td>
        <td>${carlos:forHtml(facility.enableDigitalSignatures)}</td>
    </tr>
</table>

<div class="tabs" id="tabs">
    <table cellpadding="3" cellspacing="0" border="0">
        <tr>
            <fmt:message key="pmmodule.admin.viewFacility.titleAssociatedPrograms" var="titleAssociatedPrograms"/>
<th title="${carlos:forHtmlAttribute(titleAssociatedPrograms)}"><fmt:message key="pmmodule.admin.viewFacility.thAssociatedPrograms"/></th>
        </tr>
    </table>
</div>
<display:table class="simple" cellspacing="2" cellpadding="3"
               id="program" name="associatedPrograms" export="false"
               requestURI="${facilityViewUri}">
    <fmt:message key="pmmodule.admin.viewFacility.emptyListPrograms" var="emptyListMsg"/>
<display:setProperty name="basic.msg.empty_list" value="${emptyListMsg}"/>
    <fmt:message key="pmmodule.admin.viewFacility.titleProgramName" var="titleProgramName"/>
<display:column sortable="true" sortProperty="name" title="${carlos:forHtmlAttribute(titleProgramName)}">
        <c:choose>
            <c:when test="${program.facilityId == facility.id}">
                <a href="${pageContext.request.contextPath}/PMmodule/ProgramManagerView?id=${carlos:forUriComponent(program.id)}">${carlos:forHtml(program.name)}</a>
            </c:when>
            <c:otherwise>${carlos:forHtml(program.name)}</c:otherwise>
        </c:choose>
    </display:column>
    <fmt:message key="pmmodule.admin.viewFacility.titleProgramType" var="titleProgramType"/>
<display:column property="type" sortable="true" title="${carlos:forHtmlAttribute(titleProgramType)}"/>
    <fmt:message key="pmmodule.admin.viewFacility.titleClientsInQueue" var="titleClientsInQueue"/>
<display:column property="queueSize" sortable="true" title="${carlos:forHtmlAttribute(titleClientsInQueue)}"/>
</display:table>

<br/>
<div class="tabs" id="tabs">
    <table cellpadding="3" cellspacing="0" border="0">
        <tr>
            <fmt:message key="pmmodule.admin.viewFacility.titleFacilityMessages" var="titleFacilityMessages"/>
<th title="${carlos:forHtmlAttribute(titleFacilityMessages)}"><fmt:message key="pmmodule.admin.viewFacility.thMessages"/></th>
        </tr>
    </table>
</div>
<br/>
This table displays client automatic discharges from this facility from the past seven days. An
automatic discharge occurs when the client is admitted to another facility
while still admitted in this facility.

<table width="100%" border="1" cellspacing="2" cellpadding="3">
    <tr>
        <th><fmt:message key="pmmodule.admin.viewFacility.thName"/></th>
        <th><fmt:message key="pmmodule.admin.viewFacility.thClientDob"/></th>
        <th><fmt:message key="pmmodule.admin.viewFacility.thBedProgram"/></th>
        <th><fmt:message key="pmmodule.admin.viewFacility.thDischargeDateTime"/></th>
    </tr>
    <c:forEach var="client" items="${associatedClients}">
        <tr class="b" <c:if test="${client.inOneDay}">style="color:red;"</c:if>>
            <td>${carlos:forHtml(client.name)}</td>
            <td>${carlos:forHtml(client.dob)}</td>
            <td>${carlos:forHtml(client.programName)}</td>
            <td>${carlos:forHtml(client.dischargeDate)}</td>
        </tr>
    </c:forEach>
</table>

<br/>
Automatic discharges in the past 24 hours appear red.

    <div>
    <p>
        <a href="${pageContext.request.contextPath}/PMmodule/FacilityManager?method=edit&amp;id=${carlos:forUriComponent(requestScope.id)}"><fmt:message key="pmmodule.admin.viewFacility.aEditFacility"/></a>
        |
        <a href="${pageContext.request.contextPath}/PMmodule/FacilityManager?method=list"><fmt:message key='pmmodule.admin.viewFacility.aReturnToFacilitiesList'/></a>
    </p>
</div>
