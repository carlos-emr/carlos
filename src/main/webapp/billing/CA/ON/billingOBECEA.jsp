<%@ page import="io.github.carlos_emr.CarlosProperties" %><%--

    Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>


<% java.util.Properties oscarVariables = CarlosProperties.getInstance(); %>
<%
    if (session.getAttribute("user") == null)
        response.sendRedirect(request.getContextPath() + "/logout.jsp");

    String user_no;
    user_no = (String) session.getAttribute("user");
    String docdownload = oscarVariables.getProperty("project_home");
    ;
    session.setAttribute("homepath", docdownload);

%>
<!DOCTYPE html>
<html>
    <head>

        <link href="<%=request.getContextPath() %>/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet">

        <title>EDT OBEC Response Report Generator</title>
    </head>

    <body>

    <p>EDT OBEC Response Report Generator</p>

    <form action="${pageContext.request.contextPath}/oscarBilling/DocumentErrorReportUpload.do" method="POST" enctype="multipart/form-data">


        <div class="alert alert-danger">

            <% 
    java.util.List<String> actionErrors = (java.util.List<String>) request.getAttribute("actionErrors");
    if (actionErrors != null && !actionErrors.isEmpty()) {
%>
    <div class="action-errors">
        <% for (String error : actionErrors) { %>
            <p><%= error %></p>
        <% } %>
    </div>
<% } %>
        </div>

        <div class="card card-body bg-body-tertiary">
            Select diskette <input type="file" name="file1" value="" required>

            <input type="submit" name="Submit" class="btn btn-primary" value="Create Report">
        </div>


    </form>
    </body>
</html>
