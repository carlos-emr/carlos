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
    Appointment Status Settings

    Purpose:
      Lists appointment statuses and provides the administration controls used
      to edit, reset, enable, and disable them.

    Features:
      Encoded status rendering, edit links, action feedback, and CSRF-protected
      POST forms for reset and activation changes.

    Request attributes:
      allStatus - appointment statuses rendered in the settings table.
      useStatus - status-usage data populated by the paired action.

    @since 2026-08-07
--%>
<%@ page import="java.util.*,io.github.carlos_emr.carlos.commn.model.*" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.AppointmentStatus" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="/struts-tags" prefix="s" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>

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
    <script type="text/javascript" src="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/library/jquery/jquery-3.7.1.min.js"></script>
    <script src="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/library/jquery/jquery-compat.js"></script>
    <script>
        jQuery.noConflict();
    </script>
    <link rel="stylesheet" type="text/css" media="all" href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/share/css/extractedFromPages.css"/>
<style>
    .inline-action { display: inline; }
    .link-button { background: none; border: 0; color: #0000EE; cursor: pointer; padding: 0; text-decoration: underline; }
    .status-header { background-color: #486ebd; }
    .status-title { color: #FFFFFF; font-family: Helvetica, sans-serif; text-align: center; white-space: nowrap; }
    .status-reset { color: #CCCCCC; font-family: Helvetica, sans-serif; text-align: right; white-space: nowrap; }
</style>
</head>
<body>
<table border=0 cellspacing=0 cellpadding=0 width="100%">
    <tr class="status-header">
        <th class="status-title"><fmt:message key="admin.appt.status.mgr.title"/></th>
        <th class="status-reset">
            <form class="inline-action" action="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/appointment/apptStatusSetting" method="post">
                <input type="hidden" name="dispatch" value="reset"/>
                <button class="link-button" type="submit"><fmt:message key="admin.appt.status.mgr.label.reset"/></button>
            </form>
        </th>
    </tr>
</table>

<s:actionerror/>
<s:actionmessage/>


<table class="borderAll" width="100%">
    <tr>
        <th><fmt:message key="admin.appt.status.mgr.label.status"/></th>
        <th><fmt:message key="admin.appt.status.mgr.label.desc"/></th>
        <th><fmt:message key="admin.appt.status.mgr.label.color"/></th>
        <th><fmt:message key="admin.appt.status.mgr.label.enable"/></th>
        <th><fmt:message key="admin.appt.status.mgr.label.active"/></th>
        <th>&nbsp;</th>
    </tr>
    <%
        List apptsList = (List) request.getAttribute("allStatus");
        AppointmentStatus apptStatus = null;
        int iStatusID = 0;
        String strStatus = "";
        String strDesc = "";
        String strColor = "";
        int iActive = 0;
        int iEditable = 0;
        for (int i = 0; i < apptsList.size(); i++) {
            apptStatus = (AppointmentStatus) apptsList.get(i);
            iStatusID = apptStatus.getId();
            strStatus = apptStatus.getStatus();
            strDesc = apptStatus.getDescription();
            strColor = apptStatus.getColor();
            String displayColor = strColor != null && strColor.matches("#[0-9A-Fa-f]{6}")
                    ? strColor : "#FFFFFF";
            iActive = apptStatus.getActive();
            iEditable = apptStatus.getEditable();
    %>
    <tr class=<%=(i % 2 == 0) ? "even" : "odd"%>>
        <td class="nowrap"><%=SafeEncode.forHtmlContent(strStatus)%>
        </td>
        <td class="nowrap"><%=SafeEncode.forHtmlContent(strDesc)%>
        </td>
        <td class="nowrap" style="background-color: <%=SafeEncode.forCssString(displayColor)%>"><%=SafeEncode.forHtmlContent(strColor)%>
        </td>
        <td class="nowrap"><%=iActive%>
        </td>
        <td class="nowrap">
            <%
                String url = request.getContextPath();
                url = url + "/appointment/apptStatusSetting?dispatch=modify&id=";
                url = url + iStatusID;
            %> <a href="<%=SafeEncode.forHtmlAttribute(url)%>">Edit</a> &nbsp;&nbsp;&nbsp; <%
            int iToStatus = (iActive > 0) ? 0 : 1;
            String activationMessageKey = (iActive > 0)
                    ? "admin.appt.status.mgr.label.disable"
                    : "admin.appt.status.mgr.label.enableAction";
            if (iEditable == 1) {
        %>
            <form class="inline-action" action="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/appointment/apptStatusSetting" method="post">
                <input type="hidden" name="dispatch" value="changestatus"/>
                <input type="hidden" name="id" value="<%=iStatusID%>"/>
                <input type="hidden" name="active" value="<%=iToStatus%>"/>
                <button class="link-button" type="submit">
                    <fmt:message key="<%=activationMessageKey%>"/>
                </button>
            </form>
            <%
                }
            %>
        </td>
    </tr>
    <%
        }
    %>
</table>
<br>

<%
    String strUseStatus = (String) request.getAttribute("useStatus");
    if (null != strUseStatus && strUseStatus.length() > 0) {
%>
The code [<%=SafeEncode.forHtmlContent(strUseStatus)%>] has been used before, please enable that
status.
<%
    }
%>
</body>
</html>
