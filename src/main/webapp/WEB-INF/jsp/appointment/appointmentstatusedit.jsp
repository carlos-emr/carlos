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
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="jakarta.tags.functions" prefix="fn" %>
<%@ taglib uri="/struts-tags" prefix="s" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>

<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
%>
<security:oscarSec roleName="<%=roleName$%>"
                   objectName="_admin,_admin.userAdmin,_admin.schedule" rights="r" reverse="<%=true%>">
    <%response.sendRedirect(request.getContextPath() + "/logoutPage");%>
</security:oscarSec>

<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <title><fmt:message key="admin.appt.status.mgr.title"/></title>
    <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>
    <link href="<%= request.getContextPath() %>/css/jquery.ui.colorPicker.css" rel="stylesheet" type="text/css"/>
    <script src="<%= request.getContextPath() %>/library/jquery/jquery-3.7.1.min.js" type="text/javascript"></script>
    <script src="<%= request.getContextPath() %>/library/jquery/jquery-ui-1.14.2.min.js" type="text/javascript"></script>
    <script src="<%= request.getContextPath() %>/js/jquery.ui.colorPicker.min.js" type="text/javascript"></script>
</head>
<body>
<script type="text/javascript">
    $(document).ready(function () {
        var colorInput = $('#apptColor');
        var exactTypedColor = colorInput.val();
        var typedSincePickerUse = false;

        colorInput.on('input change', function () {
            exactTypedColor = this.value;
            typedSincePickerUse = true;
        });

        colorInput.colorPicker({format: 'hex'});
        colorInput.colorPicker('setColor', $('#old_color').val());

        // The legacy picker converts typed hex through rounded HSL values, which can
        // change valid input (for example #123456 to #113456) on blur. Preserve an
        // exact typed value while still allowing picker gestures to supply a colour.
        exactTypedColor = $('#old_color').val();
        colorInput.val(exactTypedColor);
        typedSincePickerUse = false;
        colorInput.closest('.colorpicker').on('mousedown touchstart', 'canvas, .slider, .picker', function () {
            typedSincePickerUse = false;
        });
        colorInput.closest('form').on('submit', function () {
            if (typedSincePickerUse) {
                colorInput.val(exactTypedColor);
            }
        });

    });
</script>

<table border=0 cellspacing=0 cellpadding=0 width="100%">
    <tr bgcolor="#486ebd">
        <th align="CENTER" NOWRAP><font face="Helvetica" color="#FFFFFF"><fmt:message key="admin.appt.status.mgr.title"/></font></th>
    </tr>
</table>


<form action="${pageContext.request.contextPath}/appointment/apptStatusSetting" method="post">
    <s:actionerror/>
    <input type="hidden" name="dispatch" value="update"/>
    <input type="hidden" name="id" value="${fn:escapeXml(id)}"/>
    <table>
        <tr>
            <td class="tdLabel"><fmt:message key="admin.appt.status.mgr.label.status"/>:
            </td>
            <td><input type="text" readonly="readonly" value="${fn:escapeXml(apptStatus)}" size="40"/></td>
        </tr>
        <tr>
            <td class="tdLabel"><fmt:message key="admin.appt.status.mgr.label.desc"/>:
            </td>
            <td><input type="text" name="apptDesc" value="${fn:escapeXml(apptDesc)}" size="40" maxlength="30" required /></td>
        </tr>
        <tr>
            <td class="tdLabel"><fmt:message key="admin.appt.status.mgr.label.oldcolor"/>:
            </td>
            <td><input type="text" readonly="true" id="old_color" value="${fn:escapeXml(apptOldColor)}" size="40"/>
            </td>
        </tr>
        <tr>
            <td class="tdLabel"><fmt:message key="admin.appt.status.mgr.label.newcolor"/>:
            </td>
            <td>
                <input id="apptColor" name="apptColor" value="${fn:escapeXml(apptColor)}" size="20"
                       maxlength="7" pattern="#[0-9A-Fa-f]{6}" required/>
            </td>
        </tr>

        <div id="list_entries"></div>
        <tr>
            <td colspan="2">
                <input type="submit"
                       value="<fmt:message key="io.github.carlos_emr.carlos.appt.status.mgr.label.submit"/>"/>
                <a href="${pageContext.request.contextPath}/appointment/apptStatusSetting?dispatch=view"><fmt:message key="global.btnCancel"/></a>
            </td>
        </tr>
    </table>
</form>
</body>
</html>
