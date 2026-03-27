<%@ page import="io.github.carlos_emr.carlos.encounter.pageUtil.EctSessionBean" %><%--

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

<!-- Jan 20, 2003,-->
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>


<%
    EctSessionBean sessionbean = null;
    if ((sessionbean = (EctSessionBean) request.getSession().getAttribute("EctSessionBean")) == null) {
        response.sendRedirect("error.jsp");
        return;
    }
%>
<link rel="stylesheet" type="text/css" href="encounterStyles.css">
<html>
    <script type="text/javascript" language=javascript>
        function loadUp() {
            window.resizeTo(900, 50);
            setTimeout("window.close()", 1000);
            <%
                if (sessionbean.status != null && !sessionbean.status.equals("")) {
                    out.print("opener.refresh();");
                }
            %>
        }
    </script>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.timeOut.title"/></title>
    </head>
    <body onload="javascript:loadUp()">

    <!--  -->
    <table class="MainTable" id="scrollNumber1"
           name="<fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.timeOut.msgEncounter"/>">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn"></td>
            <td class="MainTableTopRowRightColumn">
                <table class="TopStatusBar">
                    <tr>
                        <td style="color: white"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.timeOut.msgSaveExit"/></td>
                        <td></td>
                        <td></td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
    </body>
</html>
