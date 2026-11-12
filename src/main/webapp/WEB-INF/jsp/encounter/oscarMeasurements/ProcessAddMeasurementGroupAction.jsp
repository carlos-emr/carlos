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

<%
    if (session.getAttribute("user") == null) response.sendRedirect(request.getContextPath() + "/logoutPage");
%>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<%
    String groupName = (String) session.getAttribute("groupName");
%>

<%@ page
        import="java.lang.*,io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.*" %>

<html>

    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="encounter.Measurements.msgProcessAddMeasurementGroupAction"/>
        </title>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
    </head>

    <script language="javascript">

        function submitForm() {

            document.forms[0].submit();

        }


    </script>

    <%-- No stylesheet: this page only shows "Processing..." while it submits itself, and the
         /styles.css it used to link does not exist (a 404 on every group save). --%>
    <body topmargin="0" leftmargin="0" vlink="#0000FF">
    <% 
    java.util.List<String> actionErrors = (java.util.List<String>) request.getAttribute("actionErrors");
    if (actionErrors != null && !actionErrors.isEmpty()) {
%>
    <div class="action-errors">
        <ul>
            <% for (String error : actionErrors) { %>
                <li><%= error %></li>
            <% } %>
        </ul>
    </div>
<% } %>
    <table>
        <%-- GET on purpose: SetupAddMeasurementGroup only renders the type-selection page, and
             nothing is stored until types are chosen there (issue #3335). --%>
        <form action="encounter/oscarMeasurements/SetupAddMeasurementGroup">
            <tr>
                <input type="hidden" name="groupName" value="<carlos:encode value='<%= groupName %>' context="htmlAttribute"/>"/>
                <input type="hidden" name="styleSheet" value="<carlos:encode value='${styleSheet}' context="htmlAttribute"/>"/>
                <td>Processing...</td>
                <script>
                    submitForm();
                </script>
            </tr>
        </form>
    </table>


    </body>
</html>
