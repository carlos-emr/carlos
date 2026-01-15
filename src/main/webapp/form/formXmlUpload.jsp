<%@ page import="ca.openosp.OscarProperties" %><%--

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
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName2$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName2$%>" objectName="_form" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_form");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<% java.util.Properties oscarVariables = OscarProperties.getInstance(); %>
<%
    if (session.getValue("user") == null)
        response.sendRedirect(request.getContextPath() + "/logout.jsp");
    String user_no;
    user_no = (String) session.getAttribute("user");
    String docdownload = oscarVariables.getProperty("project_home");
    ;
    session.setAttribute("homepath", docdownload);

%>
<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnImportFormData"/></title>
        <style>
            .css-tooltip {
                position: relative;
                display: inline-block;
            }
            .css-tooltip:focus {
                outline: 2px solid #005a9c;
                outline-offset: 2px;
            }
            .tooltip-text {
                display: none;
                position: absolute;
                left: 0;
                top: 100%;
                margin-top: 8px;
                width: 250px;
                padding: 12px;
                background: #fff;
                border: 1px solid #ccc;
                border-radius: 4px;
                box-shadow: 2px 2px 5px rgba(0,0,0,0.15);
                font-family: Arial, sans-serif;
                font-size: 13px;
                line-height: 1.5;
                color: #333;
                z-index: 1000;
            }
            .css-tooltip:hover .tooltip-text,
            .css-tooltip:focus-within .tooltip-text {
                display: block;
            }
        </style>
    </head>

    <body>


    <div class="well">

        <h3><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnImportFormData"/></h3>

        <form action="${pageContext.request.contextPath}/form/xmlUpload.do" method="POST" enctype="multipart/form-data">

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


            Select data in zip format:<br />

            <input type="file" name="file1" value="">
            <span class="css-tooltip" tabindex="0" aria-describedby="upload-warning-tooltip">
                <img border="0" src="<%= request.getContextPath() %>/images/icon_alertsml.gif" alt="Warning"/>
                <span id="upload-warning-tooltip" class="tooltip-text" role="tooltip"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.uploadWarningBody"/></span>
            </span>

            <input type="submit" name="Submit" class="btn btn-primary" value="Import">

            <p><i class="icon-info-sign"></i> Use this function to import data for a specific form into the OSCAR
                database</p>

        </form>

    </div>
    </body>
</html>
