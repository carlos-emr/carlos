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

--%>

<%@ page import="java.util.ResourceBundle" %>
<%@ page import="ca.openosp.openo.encounter.oscarConsultationRequest.config.pageUtil.EctConTitlebar" %>
<%@ page import="ca.openosp.OscarProperties" %>
<% java.util.Properties oscarVariables = OscarProperties.getInstance(); %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.consult" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin&type=_admin.consult");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>
<html>

    <%
        ResourceBundle oscarR = ResourceBundle.getBundle("oscarResources", request.getLocale());

        String transactionType = new String(oscarR.getString("oscarEncounter.oscarConsultationRequest.config.AddDepartment.addOperation"));
        String id = null;
        int whichType = 1;
        if (request.getAttribute("upd") != null) {
            transactionType = new String(oscarR.getString("oscarEncounter.oscarConsultationRequest.config.AddDepartment.updateOperation"));
            whichType = 2;
            id = (String) request.getAttribute("id");
        }
    %>

    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><%=transactionType%>
        </title>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>
    </head>
    <script language="javascript">
        function BackToOscar() {
            window.close();
        }
    </script>

    <link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/css/encounterStyles.css">
    <body class="BodyStyle" vlink="#0000FF">

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
    <!--  -->
    <table class="MainTable" id="scrollNumber1" name="encounterTable">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn">Consultation</td>
            <td class="MainTableTopRowRightColumn">
                <table class="TopStatusBar">
                    <tr>
                        <td class="Header"><%=transactionType%>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr style="vertical-align: top">
            <td class="MainTableLeftColumn">
                <%
                    EctConTitlebar titlebar = new EctConTitlebar(request);
                    out.print(titlebar.estBar(request));
                %>
            </td>
            <td class="MainTableRightColumn">
                <table cellpadding="0" cellspacing="2"
                       style="border-collapse: collapse" bordercolor="#111111" width="100%"
                       height="100%">

                    <!----Start new rows here-->
                    <%
                        String added = (String) request.getAttribute("Added");
                        if (added != null) { %>
                    <tr>
                        <td style="color: red;">
                            <fmt:setBundle basename="oscarResources"/>
                            <fmt:message  key="oscarEncounter.oscarConsultationRequest.config.AddDepartment.msgDepartmentAdded">
                                <fmt:param value="<%=added%>" />
                            </fmt:message>
                        </td>
                    </tr>
                    <%}%>
                    <tr>
                        <td>

                            <form action="${pageContext.request.contextPath}/oscarEncounter/AddDepartment.do" method="post">
                            <table>
                                    <input type="hidden" name="id" id="id" value="<%=id%>"/>
                                <tr>
                                    <td>Name</td>
                                    <td><input type="text" name="name"/></td>
                                </tr>

                                <td>Annotation
                                </td>
                                <td colspan="4"><textarea name="annotation" cols="30" rows="3"></textarea>
                                </td>
                    </tr>

                    <tr>
                        <td colspan="6">
                            <input type="hidden" name="whichType" value="<%=whichType%>"/>
                            <input type="submit" name="transType" value="<%=transactionType%>"/>
                        </td>
                    </tr>
                </table>
                </form>
            </td>
        </tr>
        <!----End new rows here-->

        <tr height="100%">
            <td></td>
        </tr>
    </table>
    </td>
    </tr>
    <tr>
        <td class="MainTableBottomRowLeftColumn"></td>
        <td class="MainTableBottomRowRightColumn"></td>
    </tr>
    </table>
    </body>
</html>
