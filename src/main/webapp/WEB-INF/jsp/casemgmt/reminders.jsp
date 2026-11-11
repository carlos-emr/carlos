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


<%@ taglib prefix="source" tagdir="/WEB-INF/tags" %>
<%@ include file="/WEB-INF/jsp/casemgmt/taglibs.jsp" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_casemgmt.notes" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_casemgmt.notes");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ page import="io.github.carlos_emr.carlos.casemgmt.model.*" %>
<%@ page import="io.github.carlos_emr.carlos.casemgmt.web.formbeans.*" %>


<input type="hidden" name="cpp.primaryPhysician" value="${carlos:forHtmlAttribute(requestScope.cpp.primaryPhysician)}"/>
<input type="hidden" name="cpp.primaryCounsellor" value="${carlos:forHtmlAttribute(requestScope.cpp.primaryCounsellor)}"/>
<input type="hidden" name="cpp.otherFileNumber" value="${carlos:forHtmlAttribute(requestScope.cpp.otherFileNumber)}"/>
<input type="hidden" name="cpp.familyHistory" value="${carlos:forHtmlAttribute(requestScope.cpp.familyHistory)}"/>
<input type="hidden" name="cpp.medicalHistory" value="${carlos:forHtmlAttribute(requestScope.cpp.medicalHistory)}"/>
<input type="hidden" name="cpp.socialHistory" value="${carlos:forHtmlAttribute(requestScope.cpp.socialHistory)}"/>
<input type="hidden" name="cpp.pastMedications" value="${carlos:forHtmlAttribute(requestScope.cpp.pastMedications)}"/>
<input type="hidden" name="cpp.otherSupportSystems" value="${carlos:forHtmlAttribute(requestScope.cpp.otherSupportSystems)}"/>
<input type="hidden" name="cpp.ongoingConcerns" value="${carlos:forHtmlAttribute(requestScope.cpp.ongoingConcerns)}"/>

<table role="presentation" width="100%" border="0" cellpadding="0" cellspacing="1"
       bgcolor="#C0C0C0">
    <tr class="title">
        <td><label for="cpp.reminders">Reminders</label> <source:cdsSourceFields demographicNo="${requestScope.demographicNo}" category="AlertsAndSpecialNeeds"/></td>
    </tr>
    <tr>
        <td bgcolor="white"><textarea id="cpp.reminders" name="cpp.reminders" rows="4" cols="85"><carlos:encode value="${requestScope.cpp.reminders}"/></textarea></td>
    </tr>
</table>
<input type="submit" value="Save" onclick="this.form.elements.namedItem('method').value='patientCPPSave'"/>
<c:if test="${not empty messages}">
    <c:forEach var="message" items="${messages}">
        <div style="color: blue"><I>${carlos:forHtml(message)}</I></div>
    </c:forEach>
</c:if>
