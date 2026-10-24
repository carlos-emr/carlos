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


<%@ taglib uri="carlos" prefix="carlos" %>
<%@ include file="/WEB-INF/jsp/casemgmt/taglibs.jsp" %>

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

<%@ page import="io.github.carlos_emr.carlos.casemgmt.model.*" %>
<%@ page import="io.github.carlos_emr.carlos.casemgmt.web.formbeans.*" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>

<input type="hidden" name="cpp.reminders" value="${carlos:forHtmlAttribute(requestScope.cpp.reminders)}"/>
<input type="hidden" name="cpp.ongoingConcerns" value="${carlos:forHtmlAttribute(requestScope.cpp.ongoingConcerns)}"/>
<table role="presentation" width="100%" border="0" cellpadding="0" cellspacing="1"
       bgcolor="#C0C0C0">
    <%if (!CarlosProperties.getInstance().isTorontoRFQ()) { %>
    <tr>
        <td bgcolor="white"><label for="cpp.primaryPhysician">Primary Health Care Provider</label></td>
        <td bgcolor="white"><input type="text" name="cpp.primaryPhysician" id="cpp.primaryPhysician" value="${carlos:forHtmlAttribute(requestScope.cpp.primaryPhysician)}" /></td>
    </tr>
    <%} else { %>
    <input type="hidden" name="cpp.primaryPhysician" value="${carlos:forHtmlAttribute(requestScope.cpp.primaryPhysician)}"/>
    <%} %>
    <tr>
        <td bgcolor="white"><label for="cpp.primaryCounsellor">Primary Counsellor/Caseworker</label></td>
        <td bgcolor="white"><input type="text" name="cpp.primaryCounsellor" id="cpp.primaryCounsellor" value="${carlos:forHtmlAttribute(requestScope.cpp.primaryCounsellor)}" /></td>
    </tr>
    <tr>
        <td bgcolor="white"><label for="cpp.otherFileNumber">Other File Number</label></td>
        <td bgcolor="white"><input type="text" name="cpp.otherFileNumber" id="cpp.otherFileNumber" value="${carlos:forHtmlAttribute(requestScope.cpp.otherFileNumber)}" /></td>
    </tr>
    <tr height="10">
        <td bgcolor="white" colspan="2">&nbsp;</td>
    </tr>

    <tr>
        <td bgcolor="white">Updated Last</td>
        <td bgcolor="white"><c:out
                value="${requestScope.cpp.update_date}"/></td>
    </tr>


    <tr class="title">
        <td><label for="cpp.socialHistory">Social History</label></td>
        <td><label for="cpp.familyHistory">Family History</label></td>
    </tr>
    <tr>
        <td bgcolor="white"><textarea id="cpp.socialHistory" name="cpp.socialHistory" rows="5" cols="45"><c:out value="${requestScope.cpp.socialHistory}"/></textarea></td>
        <td bgcolor="white"><textarea id="cpp.familyHistory" name="cpp.familyHistory" rows="5" cols="45"><c:out value="${requestScope.cpp.familyHistory}"/></textarea></td>
    </tr>

    <tr class="title">
        <td><label for="cpp.medicalHistory">Medical History</label></td>
        <td><label for="cpp.pastMedications">Past Medications</label></td>
    </tr>
    <tr>
        <td bgcolor="white"><textarea id="cpp.medicalHistory" name="cpp.medicalHistory"
                                      rows="5" cols="45"><c:out value="${requestScope.cpp.medicalHistory}"/></textarea></td>
        <td bgcolor="white"><textarea id="cpp.pastMedications" name="cpp.pastMedications"
                                      rows="5" cols="45"><c:out value="${requestScope.cpp.pastMedications}"/></textarea></td>
    </tr>

    <tr class="title">
        <td colspan="2"><label for="cpp.otherSupportSystems">Other Support Systems</label></td>
    </tr>
    <tr>
        <td colspan="2" bgcolor="white"><textarea
                id="cpp.otherSupportSystems" name="cpp.otherSupportSystems" rows="2" cols="95"><c:out value="${requestScope.cpp.otherSupportSystems}"/></textarea></td>
    </tr>
</table>
<input type="submit" value="Save" onclick="this.form.elements.namedItem('method').value='patientCPPSave'"/>
<input type="submit" value="Print Preview" onclick="this.form.elements.namedItem('method').value='patientCppPrintPreview'" />
<c:if test="${not empty requestScope.messages}">
    <c:forEach var="message" items="${requestScope.messages}">
        <div style="color: blue"><I><c:out value="${message}"/></I></div>
    </c:forEach>
</c:if>
