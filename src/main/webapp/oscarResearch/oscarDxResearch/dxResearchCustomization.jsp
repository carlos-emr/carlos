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

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>

<fmt:setBundle basename="oscarResources"/>

<html>
    <head>
        <title><fmt:message key="oscarResearch.oscarDxResearch.dxCustomization.title"/></title>
        <script type="text/javascript" src="${pageContext.servletContext.contextPath}/js/global.js"></script>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <link rel="stylesheet" type="text/css" href="${pageContext.servletContext.contextPath}/oscarResearch/oscarDxResearch/dxResearch.css"/>

    </head>

    <body topmargin="0" leftmargin="0" vlink="#0000FF" onload="setfocus();">
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
    <table width="100%" bgcolor="#EEEEFF">
        <tr bgcolor="#000000">
            <td class="subject" colspan="3">&nbsp;&nbsp;&nbsp;<fmt:message key="oscarResearch.oscarDxResearch.dxResearch.msgDxResearch"/></td>
        </tr>
        <tr>
            <td class=heading colspan="3"><fmt:message key="oscarResearch.oscarDxResearch.dxCustomization.title"/></td>
        </tr>
        <tr>
            <td>&nbsp;</td>
        </tr>
        <tr>
            <td valign="center"><input type="button" class="mbttn"
                                       style="width: 180px"
                                       onClick="popupPage(230,600,'oscarResearch/oscarDxResearch/dxResearchNewQuickList.jsp')"
                                       value="<fmt:message key="oscarResearch.oscarDxResearch.dxCustomization.addNewQuickList"/>"/>
            </td>
            <td valign="center"><input type="button" class="mbttn"
                                       style="width: 180px"
                                       onClick="popupPage(230,600,'oscarResearch/oscarDxResearch/dxResearchLoadQuickList.do')"
                                       value="<fmt:message key="oscarResearch.oscarDxResearch.dxCustomization.editQuickList"/>"/>
            </td>
            <td valign="center"><input type="button" class="mbttn"
                                       style="width: 180px"
                                       onClick="popupPage(230,600,'oscarResearch/oscarDxResearch/dxResearchSelectAssociations.jsp')"
                                       value="<fmt:message key="oscarResearch.oscarDxResearch.dxCustomization.editAssociations"/>"/>
            </td>
        </tr>
        <tr>
            <td></td>
        </tr>
    </table>
    </body>
</html>
