<%--
    Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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

    This software was written for
    Centre for Research on Inner City Health, St. Michael's Hospital,
    Toronto, Ontario, Canada

    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>


<%@ include file="/taglibs.jsp" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page import="java.util.Calendar" %>
<html>
<head>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>

    <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>

    <title>System Messages</title>
    <script type="text/javascript"
            src="<%=request.getContextPath()%>/js/validation.js"></script>
</head>
<script>
    function openBrWindow(theURL, winName, features) {
        window.open(theURL, winName, features);
    }
</script>
<body>
<body>
<table border="0" cellspacing="0" cellpadding="0" width="100%"
       bgcolor="#CCCCFF">
    <tr class="subject">
        <th colspan="4">CAISI</th>
    </tr>

    <tr>
        <td class="searchTitle" colspan="4">System Message Editor</td>
    </tr>
</table>

<br/>
<form action="<%=request.getContextPath() %>/SystemMessage.do" onsubmit="return validateRequiredFieldByName('system_message.message', 'Message', 4000);">
    <input type="hidden" name="method" value="save"/>
    <input type="hidden" name="id" id="id"/>
    <table width="60%" border="0" cellpadding="0" cellspacing="1"
           bgcolor="#C0C0C0">
        <tr>
            <td class="fieldTitle">Expiry Day:&nbsp;</td>
            <td class="fieldValue"><input type="text" name="system_message.expiry_day"/> <%
                Calendar rightNow = Calendar.getInstance();
                int year = rightNow.get(Calendar.YEAR);
                int month = rightNow.get(Calendar.MONTH) + 1;
                int day = rightNow.get(Calendar.DAY_OF_MONTH);
                String formattedDate = year + "-" + month + "-" + day;
            %> <a href="#"
                  onClick="openBrWindow('calendar/oscarCalendarPopup.jsp?type=caisi&openerForm=systemMessageForm&amp;openerElement=system_message.expiry_day&amp;year=<%=year %>&amp;month=<%=month %>','','width=300,height=300')"><img
                    border="0" src="images/calendar.jpg"/></a></td>
            <td></td>
        </tr>
        <tr>
            <td class="fieldTitle">Expiry Time:&nbsp;</td>
            <td class="fieldValue">Hour: <select
                    name="expiry_hour">
                <%for (int x = 1; x < 24; x++) { %>
                <option value="<%=String.valueOf(x) %>"><%=x %>
                </option>
                <% } %>
            </select> &nbsp;&nbsp; Minute: <select
                    property="expiry_minute">
                <%for (int x = 0; x < 60; x++) {%>
                <option value="<%=String.valueOf(x) %>"><%=x %>
                </option>
                <% } %>
            </select></td>
            <td></td>
        </tr>
        <tr>
            <td class="fieldTitle">Message&nbsp;</td>
            <td colspan="2" class="fieldValue"><input type="text" size="60"
                                                          name="system_message.message"/></td>
        </tr>
        <tr>
            <td class="fieldValue" colspan="3">
                <input type="submit" name="submit" value="Save" />
                <input type="button" value="Cancel"
                       onclick="location.href='SystemMessage.do'"/></td>
        </tr>
    </table>
</form>
</body>
</html>
