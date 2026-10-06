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
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    // In-action security failures pass a request attribute; legacy redirects still pass errormsg
    // as a query parameter. Prefer the attribute so direct request state is not dropped.
    Object errormsgAttr = request.getAttribute("errormsg");
    String errormsg = errormsgAttr instanceof String
            ? (String) errormsgAttr
            : request.getParameter("errormsg");
    request.setAttribute("errormsg", errormsg);
%>

<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title>Login Failure</title>
    </head>
    <body>
    <p><carlos:encode value="${errormsg}" context="html"/>
    <p>Please correct and try again.
    </body>
</html>
