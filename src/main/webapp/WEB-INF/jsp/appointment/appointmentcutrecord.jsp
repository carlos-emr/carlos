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
<%--
    View for AppointmentCutRecord2Action.
    Renders the result of an appointment cut/move operation.

    Request attributes (set by action):
        success  (Boolean) - true when appointment was cut

    @since 2026-04-05
--%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<html>
    <head>
        <script type="text/javascript" src="${pageContext.request.contextPath}/js/global.js"></script>
    </head>
    <body>
    <center>
        <table border="0" cellspacing="0" cellpadding="0" width="90%">
            <tr bgcolor="#486ebd">
                <th align="CENTER"><font face="Helvetica" color="#FFFFFF">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.appointmentupdatearecord.msgMainLabel"/></font></th>
            </tr>
        </table>

        <c:choose>
            <c:when test="${success}">
                <p>
                <h1><fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.appointmentupdatearecord.msgUpdateSuccess"/></h1>
                <script language="JavaScript">
                    self.opener.refresh();
                    self.close();
                </script>
            </c:when>
            <c:otherwise>
                <p>
                <h1><fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.appointmentupdatearecord.msgUpdateFailure"/></h1>
            </c:otherwise>
        </c:choose>

        <p></p>
        <hr width="90%"/>
        <form>
            <input type="button" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnClose"/>" onClick="closeit()">
        </form>
    </center>
    </body>
</html>
