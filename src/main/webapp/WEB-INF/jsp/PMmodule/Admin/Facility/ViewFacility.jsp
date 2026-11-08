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
<%@ include file="/WEB-INF/jsp/common/messages.jsp" %>
<c:url var="facilityViewUri" value="/PMmodule/FacilityManager">
    <c:param name="method" value="view"/>
    <c:param name="id" value="${requestScope.id}"/>
</c:url>

<div class="tabs" id="tabs">
    <table cellpadding="3" cellspacing="0" border="0">
        <tr>
            <th title="Facility">Facility summary</th>
        </tr>
    </table>
</div>

<table width="100%" border="1" cellspacing="2" cellpadding="3">
    <tr class="b">
        <td width="20%">Facility Id:</td>
        <td>${carlos:forHtml(requestScope.id)}</td>
    </tr>
    <tr class="b">
        <td width="20%">Name:</td>
        <td>${carlos:forHtml(facility.name)}</td>
    </tr>
    <tr class="b">
        <td width="20%">Description:</td>
        <td>${carlos:forHtml(facility.description)}</td>
    </tr>
    <tr class="b">
        <td width="20%">HIC:</td>
        <td>${carlos:forHtml(facility.hic)}</td>
    </tr>
    <tr class="b">
        <td width="20%">Primary Contact Name:</td>
        <td>${carlos:forHtml(facility.contactName)}</td>
    </tr>
    <tr class="b">
        <td width="20%">Primary Contact Email:</td>
        <td>${carlos:forHtml(facility.contactEmail)}</td>
    </tr>
    <tr class="b">
        <td width="20%">Primary Contact Phone:</td>
        <td>${carlos:forHtml(facility.contactPhone)}</td>
    </tr>
    <tr class="b">
        <td width="20%">Digital Signatures Enabled:</td>
        <td>${carlos:forHtml(facility.enableDigitalSignatures)}</td>
    </tr>
</table>

<div class="tabs" id="tabs">
    <table cellpadding="3" cellspacing="0" border="0">
        <tr>
            <th title="Associated programs">Associated programs</th>
        </tr>
    </table>
</div>
<%@ include file="/WEB-INF/jsp/common/carlosTableAssets.jspf" %>
<c:if test="${not empty associatedPrograms}">
<table class="carlos-table-spaced simple" id="program" data-carlos-table data-page-size="0" data-default-sort="0" data-default-order="ascending">
<thead><tr>

    <th scope="col"><span data-table-sort>Program Name</span></th>
    <th scope="col"><span data-table-sort>Program Type</span></th>
    <th scope="col"><span data-table-sort>Clients in Queue</span></th>
</tr></thead>
<tbody><c:forEach var="program" items="${associatedPrograms}" varStatus="tableRowStatus"><tr class="${tableRowStatus.count % 2 == 0 ? 'even' : 'odd'}">

    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(program.name))}" data-sort-type="${carlos:tableSortType(program.name)}">
        <c:choose>
            <c:when test="${program.facilityId == facility.id}">
                <a href="${pageContext.request.contextPath}/PMmodule/ProgramManagerView?id=${carlos:forUriComponent(program.id)}">${carlos:forHtml(program.name)}</a>
            </c:when>
            <c:otherwise>${carlos:forHtml(program.name)}</c:otherwise>
        </c:choose>
    </td>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(program.type))}" data-sort-type="${carlos:tableSortType(program.type)}">${carlos:forHtml(program.type)}</td>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(program.queueSize))}" data-sort-type="${carlos:tableSortType(program.queueSize)}">${carlos:forHtml(program.queueSize)}</td>
</tr></c:forEach></tbody>
</table>
</c:if>
<c:if test="${empty associatedPrograms}">No programs.</c:if>

<br/>
<div class="tabs" id="tabs">
    <table cellpadding="3" cellspacing="0" border="0">
        <tr>
            <th title="Facility Messages">Messages</th>
        </tr>
    </table>
</div>
<br/>
This table displays client automatic discharges from this facility from the past seven days. An
automatic discharge occurs when the client is admitted to another facility
while still admitted in this facility.

<table width="100%" border="1" cellspacing="2" cellpadding="3">
    <tr>
        <th>Name</th>
        <th>Client DOB</th>
        <th>Bed Program</th>
        <th>Discharge Date/Time</th>
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
        <a href="${pageContext.request.contextPath}/PMmodule/FacilityManager?method=edit&amp;id=${carlos:forUriComponent(requestScope.id)}">Edit facility</a>
        |
        <a href="${pageContext.request.contextPath}/PMmodule/FacilityManager?method=list">Return to facilities list</a>
    </p>
</div>
