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
<%@ taglib uri="owasp.encoder.jakarta" prefix="e" %>
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
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<style type="text/css" media="print">
    .header {
        display: none;
    }

    .header INPUT {
        display: none;
    }

    .header A {
        display: none;
    }
</style>
<table width="100%" border="0" cellpadding="0" cellspacing="1">

    <tr>
        <td><b>Client Name : </b>${e:forHtml(requestScope.casemgmt_demoName)}</td>
    </tr>
    <tr>
        <td><b>Age : </b>${e:forHtml(requestScope.casemgmt_demoAge)}</td>
        <td><b>DOB : </b>${e:forHtml(requestScope.casemgmt_demoDOB)}</td>
    </tr>
    <tr>
        <td><b>Team : </b>${e:forHtml(requestScope.teamName)}</td>
        <td><b>Other File Number : </b>${e:forHtml(cpp.otherFileNumber)}</td>
    </tr>

    <%if (!CarlosProperties.getInstance().isTorontoRFQ()) { %>
    <tr>
        <td><b>Primary Health Care Provider : </b>${e:forHtml(cpp.primaryPhysician)}</td>
    </tr>
    <%} %>
    <tr>
        <td><b>Primary Counsellor/Caseworker : </b>${e:forHtml(cpp.primaryCounsellor)}</td>
    </tr>

    <tr height="10">
        <td bgcolor="white" colspan="2">&nbsp;</td>
    </tr>

    <tr>
        <td><b>Updated Last : </b>${e:forHtml(requestScope.cpp.update_date)}</td>
    </tr>


    <tr>
        <td><b>Social History</b></td>
        <td><b>Family History</b></td>
    </tr>
    <tr>
        <td>${e:forHtml(cpp.socialHistory)}</td>
        <td>${e:forHtml(cpp.familyHistory)}</td>
    </tr>
    <tr>
        <td>&nbsp;</td>
    </tr>
    <tr>
        <td><b>Medical History</b></td>
        <td><b>Past Medications</b></td>
    </tr>
    <tr>
        <td>${e:forHtml(cpp.medicalHistory)}</td>
        <td>${e:forHtml(cpp.pastMedications)}</td>
    </tr>
    <tr>
        <td>&nbsp;</td>
    </tr>
    <tr>
        <td colspan="2"><b>Other Support Systems</b></td>
    </tr>
    <tr>
        <td colspan="2">${e:forHtml(cpp.otherSupportSystems)}</td>
    </tr>

    <tr>
    <tr>
        <td>&nbsp;</td>
    </tr>
</table>
<table class="header">
    <tr>
        <td><input type="button" value="Print" onclick="window.print()">
            <input type="button" value="Close" onclick="window.close()"/></td>
    </tr>
</table>

<c:if test="${not empty requestScope.messages}">
    <c:forEach var="message" items="${requestScope.messages}">
        <div style="color: blue"><I>${e:forHtml(message)}</I></div>
    </c:forEach>
</c:if>
