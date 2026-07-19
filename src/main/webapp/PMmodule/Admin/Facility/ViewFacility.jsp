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
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ include file="/taglibs.jsp" %>

<%@ include file="/common/messages.jsp" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Facility" %>
<%@ page import="java.util.List" %>

<div class="tabs" id="tabs">
    <table cellpadding="3" cellspacing="0" border="0">
        <tr>
            <th title="Facility">Facility summary</th>
        </tr>
    </table>
</div>
<form action="${pageContext.request.contextPath}/PMmodule/FacilityManager.do" method="post">
    <input type="hidden" name="method" value="save"/>
    <table width="100%" border="1" cellspacing="2" cellpadding="3">
        <tr class="b">
            <td width="20%">Facility Id:</td>
            <td><c:out value="${requestScope.id}"/></td>
        </tr>
        <tr class="b">
            <td width="20%">Name:</td>
            <td><c:out value="${requestScope.facilityManagerForm.facility.name}"/></td>
        </tr>
        <tr class="b">
            <td width="20%">Description:</td>
            <td><c:out value="${facilityManagerForm.facility.description}"/></td>
        </tr>
        <tr class="b">
            <td width="20%">HIC:</td>
            <td><c:out value="${facilityManagerForm.facility.hic}"/></td>
        </tr>
        <tr class="b">
            <td width="20%">Primary Contact Name:</td>
            <td><c:out value="${facilityManagerForm.facility.contactName}"/></td>
        </tr>
        <tr class="b">
            <td width="20%">Primary Contact Email:</td>
            <td><c:out value="${facilityManagerForm.facility.contactEmail}"/></td>
        </tr>
        <tr class="b">
            <td width="20%">Primary Contact Phone:</td>
            <td><c:out value="${facilityManagerForm.facility.contactPhone}"/></td>
        </tr>

        <tr class="b">
            <td width="20%">Digital Signatures Enabled:</td>
            <td><c:out value="${facilityManagerForm.facility.enableDigitalSignatures}"/></td>
        </tr>

        <tr class="b">
            <td width="20%">Integrator Enabled:</td>
            <td>
                <c:out value="${facilityManagerForm.facility.integratorEnabled}"/>
                <%
                    // this needs to be checked against the running facility, not the viewing facility
                    // because the running facility is the one who will contact the integrator to see the facility list.
                    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
                    if (loggedInInfo.getCurrentFacility().isIntegratorEnabled()) {
                %>
                &nbsp;
                <a target="_blank"
                   href="<%= request.getContextPath() %>/admin/viewIntegratedCommunity.jsp?facilityId=<c:out value="${requestScope.id}" />">
                    View Integrated Facilities Community
                </a>
                <%
                    }
                %>
            </td>
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
                   requestURI="/PMmodule/FacilityManager.do">
        <display:setProperty name="basic.msg.empty_list" value="No programs."/>

        <%
            if (request.getAttribute("program") != null) {
        %>

        <c:choose>
            <c:when test="${program.facilityId == facility.id}">
                <display:column sortable="true" sortProperty="name" title="Program Name">
                    <a href="${pageContext.request.contextPath}/PMmodule/ProgramManagerView?id=${program.id}">
                        <c:out value="${program.name}" />
                    </a>
                </display:column>
            </c:when>
            <c:otherwise>
                <display:column sortable="true" sortProperty="name" title="Program Name">
                    <c:out value="${program.name}" />
                </display:column>
            </c:otherwise>
        </c:choose>

        <display:column property="type" sortable="true" title="Program Type"/>
        <display:column property="queueSize" sortable="true"
                        title="Clients in Queue"/>

        <% } %>
    </display:table>

    <br>
    <div class="tabs" id="tabs">
        <table cellpadding="3" cellspacing="0" border="0">
            <tr>
                <th title="Facility Messages">Messages</th>
            </tr>
        </table>
    </div>
    <br>This table displays client automatic discharges from this facility from the past seven days. An
    automatic discharge occurs when the client is admitted to another facility
    while still admitted in this facility.

    <table width="100%" border="1" cellspacing="2" cellpadding="3">
        <tr>
            <th>Name</th>
            <th>Client DOB</th>
            <th>Program</th>
            <th>Discharge Date/Time</th>
        </tr>
        <c:forEach var="client" items="${associatedClients}">

            <%String styleColor = ""; %>
            <c:if test="${client.inOneDay}">
                <%styleColor = "style=\"color:red;\"";%>
            </c:if>
            <tr class="b" <%=styleColor%>>
                <td><c:out value="${client.name}"/></td>
                <td><c:out value="${client.dob}"/></td>
                <td><c:out value="${client.programName}"/></td>
                <td><c:out value="${client.dischargeDate}"/></td>
            </tr>

        </c:forEach>
    </table>


    <br>
    Automatic discharges in the past 24 hours appear red.
</form>
