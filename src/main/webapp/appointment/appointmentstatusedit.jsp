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

--%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>

<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
%>
<security:oscarSec roleName="<%=roleName$%>"
                   objectName="_admin,_admin.userAdmin,_admin.schedule" rights="r" reverse="<%=true%>">
    <%response.sendRedirect(request.getContextPath() + "/logout.jsp");%>
</security:oscarSec>

<html>
<head>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.appt.status.mgr.title"/></title>
    <link href="<%= request.getContextPath() %>/css/jquery.ui.colorPicker.css" rel="stylesheet" type="text/css"/>
    <script src="<%= request.getContextPath() %>/js/jquery-1.7.1.min.js" type="text/javascript"></script>
    <script src="<%= request.getContextPath() %>/js/jquery-ui-1.8.18.custom.min.js" type="text/javascript"></script>
    <script src="<%= request.getContextPath() %>/js/jquery.ui.colorPicker.min.js" type="text/javascript"></script>
</head>
<link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>
<body>
<script type="text/javascript">
    $(document).ready(function () {
        $('#apptColor').colorPicker({
            format: 'hex',
            colorChange: function (e, ui) {
                $('#apptColor').val(ui.color);
            }
        });

        $('#colorpicker').colorPicker('setColor', $('#old_color').val());

    });
</script>

<table border=0 cellspacing=0 cellpadding=0 width="100%">
    <tr bgcolor="#486ebd">
        <th align="CENTER" NOWRAP><font face="Helvetica" color="#FFFFFF"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.appt.status.mgr.title"/></font></th>
    </tr>
</table>


<form action="${pageContext.request.contextPath}/appointment/apptStatusSetting.do" method="post">
    <input type="hidden" name="dispatch" value="update"/>
    <input type="hidden" name="ID" value="${fn:escapeXml(ID)}"/>
    <table>
        <tr>
            <td class="tdLabel"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.appt.status.mgr.label.status"/>:
            </td>
            <td><input type="text" readonly="readonly" name="apptStatus" value="${fn:escapeXml(apptStatus)}" size="40"/></td>
        </tr>
        <tr>
            <td class="tdLabel"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.appt.status.mgr.label.desc"/>:
            </td>
            <td><input type="text" name="apptDesc" value="${fn:escapeXml(apptDesc)}" size="40" /></td>
        </tr>
        <tr>
            <td class="tdLabel"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.appt.status.mgr.label.oldcolor"/>:
            </td>
            <td><input type="text" readonly="true" id="old_color" name="apptOldColor" value="${fn:escapeXml(apptOldColor)}" size="40"/>
            </td>
        </tr>
        <tr>
            <td class="tdLabel"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.appt.status.mgr.label.newcolor"/>:
            </td>
            <td>
                <input id="apptColor" name="apptColor" value="${fn:escapeXml(apptOldColor)}" size="20"/>
            </td>
        </tr>

        <div id="list_entries"></div>
        <tr>
            <td colspan="2">
                <input type="submit"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="ca.openosp.openo.appt.status.mgr.label.submit"/>"/>
            </td>
        </tr>
    </table>
</form>
</body>
</html>
