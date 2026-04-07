<%--

    Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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

    This software was written for the
    Department of Family Medicine
    McMaster University
    Hamilton
    Ontario, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>

<%@ include file="/taglibs.jsp" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>


<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>

<security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="r" reverse="<%=true%>">
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin");%>
    <%authed = false; %>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>
<html>
    <head>
        <title>Edit Facility</title>
        <link rel="stylesheet" type="text/css" href='${request.contextPath}/css/tigris.css'/>
        <link rel="stylesheet" type="text/css" href='${request.contextPath}/css/displaytag.css'/>

        <script type="text/javascript"
                src="<%=request.getContextPath()%>/js/validation.js">
        </script>
        <script type="text/javascript">
            function validateForm() {
                if (bCancel) return bCancel;

                var isOk = false;
                isOk = validateRequiredField('facilityName', 'Facility Name', 32);
                if (isOk) isOk = validateRequiredField('facilityDesc', 'Facility Description', 70);
                return isOk;
            }
        </script>
        <!-- don't close in 1 statement, will break IE7 -->

    </head>
    <body>
    <h1>Edit Facility</h1>
    <form action="${pageContext.request.contextPath}/FacilityManager.do" method="post"
               onsubmit="return validateForm();">
        <input type="hidden" name="method" value="save"/>
        <input type="hidden" name="orgId" id="orgId"/>
        <input type="hidden" name="sectorId" id="sectorId"/>
        <table width="100%" border="1" cellspacing="2" cellpadding="3">
            <tr class="b">
                <td>Facility Id:</td>
                <td><c:out value="${requestScope.id}"/></td>
            </tr>
            <tr class="b">
                <td>Name: *</td>
                <td><input type="text" name="facility.name" size="32" maxlength="32" id="facilityName"/></td>
            </tr>
            <tr class="b">
                <td>Description: *</td>
                <td><input type="text" name="facility.description" size="60" maxlength="70" id="facilityDesc"/></td>
            </tr>
            <tr class="b">
                <td width="20%">Enable Digital Signatures:</td>
                <td><input type="checkbox" name="facility.enableDigitalSignatures"/></td>
            </tr>

            <tr class="b">
                <td>Rx Interaction Warning Level:</td>
                <td>
                    <select name="rxInteractionWarningLevel">
                        <option value="0">Not Specified</option>
                        <option value="1">Low</option>
                        <option value="2">Medium</option>
                        <option value="3">High</option>
                        <option value="4">None</option>
                    </select>
                </td>
            </tr>
            <tr>
                <td colspan="2">* Mandatory fields</td>
            <tr>
                <td colspan="2">
                    <input type="submit" name="submit" value="Save" onclick="bCancel=false;" />
                    <button type="button" onclick="window.history.back();">Cancel</button></td>
            </tr>
        </table>
    </form>
    </body>
</html>
