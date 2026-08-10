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
<%--
    Appointment Status Edit

    Purpose:
      Edits the description and colour of one existing appointment status.

    Features:
      Server- and browser-side validation, an exact-value colour picker, inline
      action errors, and a CSRF-protected POST update with a cancel path.

    Parameters:
      id - numeric appointment-status identifier loaded by the paired action.

    @since 2026-08-07
--%>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="/struts-tags" prefix="s" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="carlos" prefix="carlos" %>

<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
%>
<security:oscarSec roleName="<%=roleName$%>"
                   objectName="_admin,_admin.userAdmin,_admin.schedule" rights="r" reverse="<%=true%>">
    <%response.sendRedirect(request.getContextPath() + "/logoutPage");%>
</security:oscarSec>

<html>
<head>
    <link rel="icon" href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/images/favicon.ico"/>
    <script type="text/javascript" src="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/js/global.js"></script>
    <title><fmt:message key="admin.appt.status.mgr.title"/></title>
    <link rel="stylesheet" type="text/css" media="all" href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/share/css/extractedFromPages.css"/>
    <link href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/css/jquery.ui.colorPicker.css" rel="stylesheet" type="text/css"/>
    <script src="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/library/jquery/jquery-3.7.1.min.js" type="text/javascript"></script>
    <script src="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/library/jquery/jquery-ui-1.14.2.min.js" type="text/javascript"></script>
    <script src="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/js/jquery.ui.colorPicker.min.js" type="text/javascript"></script>
</head>
<body>
<script type="text/javascript">
    $(document).ready(function () {
        var colorInput = $('#apptColor');
        var replaceLegacyColorInput = $('#replaceLegacyColor');
        var exactTypedColor = colorInput.val();
        var typedSincePickerUse = false;

        colorInput.on('input', function () {
            exactTypedColor = this.value;
            typedSincePickerUse = true;
            replaceLegacyColorInput.prop('checked', true);
        });

        var initialColor = colorInput.val();
        colorInput.colorPicker({format: 'hex'});
        if (/^#[0-9A-Fa-f]{6}$/.test(initialColor)) {
            colorInput.colorPicker('setColor', initialColor);
        }

        // The legacy picker converts typed hex through rounded HSL values, which can
        // change valid input (for example #123456 to #113456) on blur. Preserve an
        // exact typed value while still allowing picker gestures to supply a colour.
        exactTypedColor = initialColor;
        colorInput.val(exactTypedColor);
        typedSincePickerUse = false;
        colorInput.closest('.colorpicker').on('mousedown touchstart', function (event) {
            if (event.target !== colorInput[0]) {
                typedSincePickerUse = false;
                replaceLegacyColorInput.prop('checked', true);
            }
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


<form action="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/appointment/apptStatusSetting" method="post">
    <s:actionerror/>
    <input type="hidden" name="dispatch" value="update"/>
    <input type="hidden" name="id" value="${carlos:forHtmlAttribute(id)}"/>
    <table>
        <tr>
            <td class="tdLabel"><label for="apptStatus"><fmt:message key="admin.appt.status.mgr.label.status"/>:</label>
            </td>
            <td><input id="apptStatus" type="text" readonly="readonly" value="${carlos:forHtmlAttribute(apptStatus)}" size="40"/></td>
        </tr>
        <tr>
            <td class="tdLabel"><label for="apptDesc"><fmt:message key="admin.appt.status.mgr.label.desc"/>:</label>
            </td>
            <td><input id="apptDesc" type="text" name="apptDesc" value="${carlos:forHtmlAttribute(apptDesc)}" size="40" maxlength="30" required /></td>
        </tr>
        <tr>
            <td class="tdLabel"><label for="old_color"><fmt:message key="admin.appt.status.mgr.label.oldcolor"/>:</label>
            </td>
            <td><input type="text" readonly="true" id="old_color" value="${carlos:forHtmlAttribute(apptOldColor)}" size="40"/>
            </td>
        </tr>
        <tr>
            <td class="tdLabel"><label for="apptColor"><fmt:message key="admin.appt.status.mgr.label.newcolor"/>:</label>
            </td>
            <td>
                <input id="apptColor" name="apptColor" value="${carlos:forHtmlAttribute(apptColor)}" size="20"
                       maxlength="7" pattern="#[0-9A-Fa-f]{6}" required/>
            </td>
        </tr>
        <s:if test="legacyColor">
            <tr>
                <td></td>
                <td>
                    <input type="checkbox" id="replaceLegacyColor" name="replaceLegacyColor" value="true"/>
                    <label for="replaceLegacyColor"><fmt:message key="admin.appt.status.mgr.label.replaceLegacyColor"/></label>
                </td>
            </tr>
        </s:if>

        <div id="list_entries"></div>
        <tr>
            <td colspan="2">
                <input type="submit"
                       value="<fmt:message key="io.github.carlos_emr.carlos.appt.status.mgr.label.submit"/>"/>
                <a href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/appointment/apptStatusSetting?dispatch=view"><fmt:message key="global.btnCancel"/></a>
            </td>
        </tr>
    </table>
</form>
</body>
</html>
