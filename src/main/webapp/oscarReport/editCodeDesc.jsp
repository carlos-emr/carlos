<%--

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
<!DOCTYPE html>

<%@ include file="/taglibs.jsp" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_report,_admin.reporting" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_report&type=_admin.reporting");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<html>
    <head>

        <title>Dx Register Report</title>


        <script src="<%= request.getContextPath() %>/library/jquery/jquery-3.6.4.min.js"></script>

        <style>
            input {
                font-size: 100%;
            }
        </style>

        <link href="<%=request.getContextPath() %>/library/bootstrap/5.3.3/css/bootstrap.min.css" rel="stylesheet">
    </head>

    <%
        String editingCodeType = (String) session.getAttribute("editingCodeType");
        String editingCodeCode = (String) session.getAttribute("editingCodeCode");
        String editingCodeDesc = (String) session.getAttribute("editingCodeDesc");

    %>
    <body>

    <form action="<%= request.getContextPath() %>/report/DxresearchReport.do" method="post">
        <input type="hidden" name="method" value="editDesc"/>

        <input type="hidden" name="editingCodeType" value=<%=editingCodeType%>/>
        <input type="hidden" name="editingCodeCode" value=<%=editingCodeCode%>/>

        <table class="table">
            <tr>
                <th>Code type</th>
                <th>Code</th>
                <th>Description</th>
                <th>Action</th>
            </tr>
            <tr>
                <td><%=editingCodeType%>
                </td>
                <td><%=editingCodeCode%>
                </td>
                <td><input name="editingCodeDesc" value=<%=editingCodeDesc%> class="span4"></td>
                <td><input type="submit" name="submit" class="btn btn-primary" value="Modify"></td>
            </tr>
        </table>

    </form>

    </body>
</html>
