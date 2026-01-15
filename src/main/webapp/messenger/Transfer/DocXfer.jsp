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

<%--
/**
 * Document Transfer Initialization Page
 *
 * This JSP page provides the initial interface for the document transfer system
 * in the OpenO EMR messenger. It allows users to input a demographic number and
 * proceed to document selection for transfer to other healthcare providers.
 *
 * Main Features:
 * - Simple form interface for demographic number input
 * - Support for both XML and standard submission modes
 * - Integration with document transfer workflow
 * - Displays servlet path for debugging purposes
 *
 * Security Requirements:
 * - Requires "_msg" object write permissions via security taglib
 * - User session validation and role-based access control
 *
 * Request Parameters:
 * - demo: Pre-populated demographic number (optional)
 *
 * Form Actions:
 * - Submit to XML: Processes demographic for XML-based transfer
 * - Submit: Standard form submission to SelectItems.jsp
 * - Reset: Clears form fields
 *
 * Navigation Flow:
 * 1. User enters or confirms demographic number
 * 2. Clicks submit to proceed to SelectItems.jsp
 * 3. SelectItems.jsp displays available documents for transfer
 *
 * @since 2003
 */
--%>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_msg" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_msg");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<html>
<head>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <title>DocXfer</title>
</head>
<body>
<h1>Document Transfer</h1>

<!-- Debug: Servlet Path -->
<%= this.getServletContext().getRealPath(request.getServletPath()) %>

<form method="post" action="<%= request.getContextPath() %>/messenger/Transfer/SelectItems.jsp">Demographic No: <input
        type="text" name="demographicNo"
        value="<%= request.getParameter("demo")%>"/> <input type="submit"
                                                            name="submitXml" value="Submit to XML"> <input type="submit"
                                                                                                           name="submit"
                                                                                                           value="Submit"
                                                                                                           onclick="javascript:form.action='<%= request.getContextPath() %>/messenger/Transfer/SelectItems.jsp';">
    <input
            type="reset" value="Reset"></form>
</body>
</html>
