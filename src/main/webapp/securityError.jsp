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
<%@ page import="java.util.List,
                 org.owasp.encoder.Encode" %>

<html>
<head>
</head>
<body>

<h5>Security Exception</h5>

You tried to access a resource with insufficient privileges.

<%
    String[] vals = request.getParameterValues("type");
    if (vals != null) {
        for (String val : vals) {
%>
<h5>Object:<%= Encode.forHtml(val) %>
</h5>
<%
        }
    }
%>
<% 
    java.util.List<String> actionErrors = (java.util.List<String>) request.getAttribute("actionErrors");
    if (actionErrors != null && !actionErrors.isEmpty()) {
%>
    <div class="action-errors">
        <ul>
            <% for (String error : actionErrors) { %>
                <li><%= Encode.forHtml(error) %></li>
            <% } %>
        </ul>
    </div>
<% } %>

</body>
</html>