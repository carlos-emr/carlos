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

--%>


<%@ include file="/casemgmt/taglibs.jsp" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_casemgmt.notes" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_casemgmt.notes");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ page import="io.github.carlos_emr.carlos.casemgmt.model.*" %>
<%@ page import="io.github.carlos_emr.carlos.casemgmt.web.formbeans.*" %>


<input type="hidden" name="primaryPhysician" id="primaryPhysician"/>
<input type="hidden" name="primaryCounsellor" id="primaryCounsellor"/>
<input type="hidden" name="otherFileNumber" id="otherFileNumber"/>

<input type="hidden" name="familyHistory" id="familyHistory"/>
<input type="hidden" name="medicalHistory" id="medicalHistory"/>
<input type="hidden" name="socialHistory" id="socialHistory"/>

<input type="hidden" name="pastMedications" id="pastMedications"/>
<input type="hidden" name="otherSupportSystems" id="otherSupportSystems"/>

<table width="100%" border="0" cellpadding="0" cellspacing="1"
       bgcolor="#C0C0C0">
    <tr class="title">
        <td>Reminders</td>
    </tr>
    <tr>
        <td bgcolor="white"><textarea name="reminders" rows="4" cols="85"></textarea></td>
    </tr>
</table>
<input type="submit" name="submit" value="save" onclick="this.form.method.value='patientCPPSave'"/>
<c:if test="${not empty messages}">
    <c:forEach var="message" items="${messages}">
        <div style="color: blue"><I><c:out value="${message}"/></I></div>
    </c:forEach>
</c:if>
