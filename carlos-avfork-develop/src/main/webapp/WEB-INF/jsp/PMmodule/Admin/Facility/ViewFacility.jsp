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
<display:table class="simple" cellspacing="2" cellpadding="3"
               id="program" name="associatedPrograms" export="false"
               requestURI="${facilityViewUri}">
    <display:setProperty name="basic.msg.empty_list" value="No programs."/>
    <display:column sortable="true" sortProperty="name" title="Program Name">
        <c:choose>
            <c:when test="${program.facilityId == facility.id}">
                <a href="${pageContext.request.contextPath}/PMmodule/ProgramManagerView?id=${carlos:forUriComponent(program.id)}">${carlos:forHtml(program.name)}</a>
            </c:when>
            <c:otherwise>${carlos:forHtml(program.name)}</c:otherwise>
        </c:choose>
    </display:column>
    <display:column property="type" sortable="true" title="Program Type"/>
    <display:column property="queueSize" sortable="true" title="Clients in Queue"/>
</display:table>

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
