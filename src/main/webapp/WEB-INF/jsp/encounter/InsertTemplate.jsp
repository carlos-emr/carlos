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

<%@ page import="java.lang.*,io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.*" %>
<%@ page import="org.owasp.encoder.Encode" %>
<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title></title>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
    </head>

    <script language="javascript">

        function write2Parent(text) {

            opener.document.encForm.enTextarea.value = opener.document.encForm.enTextarea.value + "\n\n" + text;
            opener.setTimeout("document.encForm.enTextarea.scrollTop=2147483647", 0);  // setTimeout is needed to allow browser to realize that text field has been updated
            opener.document.encForm.enTextarea.focus();
            window.close();
        }


    </script>

    <link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/styles.css">
    <body topmargin="0" leftmargin="0" vlink="#0000FF">
    <table>
        <tr>
            <td>Processing...</td>
            <%
                String tmplValue = (String) request.getAttribute("templateValue");
            %>
            <script>
            <% if (tmplValue != null && !tmplValue.isEmpty()) { %>
                var text = "<%= Encode.forJavaScript(tmplValue) %>";
                write2Parent(text);
            <% } else { %>
                window.close();
            <% } %>
            </script>
        </tr>
    </table>


    </body>
</html>


<%-- <%=request.getAttribute("templateValue")%> --%>
