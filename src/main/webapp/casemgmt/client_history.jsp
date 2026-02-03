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


<%@ include file="/casemgmt/taglibs.jsp" %>

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

<%@ page import="io.github.carlos_emr.carlos.casemgmt.model.*" %>
<%@ page import="io.github.carlos_emr.carlos.casemgmt.web.formbeans.*" %>
<%@ page import="io.github.carlos_emr.OscarProperties" %>

<input type="hidden" name="reminders" id="reminders"/>
<table width="100%" border="0" cellpadding="0" cellspacing="1"
       bgcolor="#C0C0C0">
    <%if (!OscarProperties.getInstance().isTorontoRFQ()) { %>
    <tr>
        <td bgcolor="white">Primary Health Care Provider</td>
        <td bgcolor="white"><input type="text" name="cpp.primaryPhysician" id="cpp.primaryPhysician" /></td>
    </tr>
    <%} %>
    <tr>
        <td bgcolor="white">Primary Counsellor/Caseworker</td>
        <td bgcolor="white"><input type="text" name="cpp.primaryCounsellor" id="cpp.primaryCounsellor" /></td>
    </tr>
    <tr>
        <td bgcolor="white">Other File Number</td>
        <td bgcolor="white"><input type="text" name="cpp.otherFileNumber" id="cpp.otherFileNumber" /></td>
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
        <td>Social History</td>
        <td>Family History</td>
    </tr>
    <tr>
        <td bgcolor="white"><textarea name="socialHistory" rows="5" cols="45"></textarea></td>
        <td bgcolor="white"><textarea name="familyHistory" rows="5" cols="45"></textarea></td>
    </tr>

    <tr class="title">
        <td>Medical History</td>
        <td>Past Medications</td>
    </tr>
    <tr>
        <td bgcolor="white"><textarea name="medicalHistory"
                                      rows="5" cols="45"></textarea></td>
        <td bgcolor="white"><textarea name="pastMedications"
                                      rows="5" cols="45"></textarea></td>
    </tr>

    <tr class="title">
        <td colspan="2">Other Support Systems</td>
    </tr>
    <tr>
        <td colspan="2" bgcolor="white"><textarea
                name="otherSupportSystems" rows="2" cols="95"></textarea></td>
    </tr>
</table>
<input type="submit" name="submit" value="Save" onclick="this.form.method.value='patientCPPSave'"/>
<input type="submit" name="submit" value="Print Preview" onclick="this.form.method.value='patientCppPrintPreview'" />
<c:if test="${not empty requestScope.messages}">
    <c:forEach var="message" items="${requestScope.messages}">
        <div style="color: blue"><I><c:out value="${message}"/></I></div>
    </c:forEach>
</c:if>
