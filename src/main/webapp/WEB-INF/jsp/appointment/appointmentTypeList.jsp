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
<%@ page
        import="java.util.*, java.sql.*, io.github.carlos_emr.*, java.text.*, java.lang.*,java.net.*, io.github.carlos_emr.carlos.appt.*, io.github.carlos_emr.carlos.commn.dao.AppointmentTypeDao, io.github.carlos_emr.carlos.commn.model.AppointmentType, io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib prefix="s" uri="/struts-tags" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ include file="/WEB-INF/jsp/admin/dbconnection.jsp" %>
<%--RJ 07/07/2006 --%>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");

    String sError = "";
    if (request.getParameter("err") != null && !request.getParameter("err").equals(""))
        sError = "Error: " + request.getParameter("err");
%>

<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ page import="java.util.*" %>
<%@ page import="java.sql.*" %>
<%@ page import="io.github.carlos_emr.carlos.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.login.*" %>
<%@ page import="io.github.carlos_emr.carlos.log.*" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ page import="io.github.carlos_emr.carlos.commn.IsPropertiesOn" %>
<html>
<head>
    <link rel="icon" href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/images/favicon.ico"/>
    <fmt:message key="appointment.appointmentTypeList.errAppointmentTypeName" var="msgAppointmentTypeName"/>
    <fmt:message key="appointment.appointmentTypeList.errNamesField" var="msgNamesField"/>
    <fmt:message key="appointment.appointmentTypeList.msgAppointmentType" var="msgAppointmentType"/>
    <fmt:message key="global.confirmDeleteItem" var="msgDeleteConfirm">
        <fmt:param value="${msgAppointmentType}"/>
    </fmt:message>
    <title>
        <fmt:message key="appointment.appointmentTypeList.title"/>
    </title>
    <script language="JavaScript">
        const i18nAppointmentTypeName = "${carlos:forJavaScript(msgAppointmentTypeName)}";
        const i18nNamesField = "${carlos:forJavaScript(msgNamesField)}";

        function popupPage(vheight, vwidth, title, varpage) {
            var page = "" + varpage;
            var leftVal = (screen.width - 850) / 2;
            var topVal = (screen.height - 300) / 2;
            windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,top=" + topVal + ",left=" + leftVal;
            var popup = window.open(page, title, windowprops);
            if (popup != null) {
                if (popup.opener == null) {
                    popup.opener = self;
                }
                popup.focus();
            }
        }

        function popupResponce(href) {
            window.location.href = href;
        }

        function setfocus() {
            this.focus();
            document.forms[0].name.focus();
            document.forms[0].name.select();
        }

        function upCaseCtrl(ctrl) {
            ctrl.value = ctrl.value.toUpperCase();
        }

        function onBlockFieldFocus(obj) {
            obj.blur();
            document.forms[0].name.focus();
            document.forms[0].name.select();
            window.alert(i18nAppointmentTypeName);
        }

    </script>
    <style>
        .inline-action { display: inline; }
        .link-button { background: none; border: 0; color: #0000EE; cursor: pointer; padding: 0; text-decoration: underline; }
    </style>
</head>
<body topmargin="0" leftmargin="0" rightmargin="0">
<table width="100%">
    <tr>
        <td colspan="3" height="30"></td>
    </tr>
    <tr>
        <td width="100">&nbsp;</td>
        <td align="center">
            <table border="0" cellspacing="0" cellpadding="0" width="100%">
                <tr bgcolor="#486ebd" height="30">
                    <th align="LEFT" width="90%">
                        <font face="Helvetica" color="#FFFFFF">&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
                        </font>
                    </th>
                    <td nowrap>
                        <font size="-1" color="#FFFFFF">&nbsp;
                        </font>
                    </td>
                </tr>
            </table>
            <s:actionerror/>
            <s:actionmessage/>
            <table width="100%" border="0" bgcolor="ivory" cellspacing="1" cellpadding="1">
                <tr bgcolor="mediumaquamarine">
                    <th align="right"></th>
                    <th colspan="6" align="left">
                        &nbsp;&nbsp;&nbsp;&nbsp; <fmt:message key="appointment.appointmentTypeList.title"/>
                    </th>
                </tr>
                <tr>
                    <td colspan=7>
                        <center>
                            <form action="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/appointment/appointmentTypeAction" method="post">
                                <input TYPE="hidden" NAME="oper" VALUE="save"/>
                                <input TYPE="hidden" NAME="id"
                                       VALUE="${carlos:forHtmlAttribute(id)}"/>
                                <table border=0 cellspacing=0 cellpadding=0 width="100%">
                                    <tr bgcolor="#CCCCFF">
                                        <th><font face="Helvetica">
                                            <c:choose>
                                                <c:when test="${not empty id}"><fmt:message key="appointment.appointmentTypeList.formEditTitle"/></c:when>
                                                <c:otherwise><fmt:message key="appointment.appointmentTypeList.formAddTitle"/></c:otherwise>
                                            </c:choose>
                                        </font></th>
                                    </tr>
                                </table>
                                <table border="0" cellpadding="0" cellspacing="0" width="100%">
                                    <tr>
                                        <td width="100%">
                                            <table BORDER="0" CELLPADDING="0" CELLSPACING="1" WIDTH="100%"
                                                   BGCOLOR="#C0C0C0">
                                                <tr valign="middle" BGCOLOR="#EEEEFF">
                                                    <td width="30%">
                                                        <div align="right"><font face="arial"><fmt:message key="name"/><fmt:message key="global.labelSeparator"/></font></div>
                                                    </td>
                                                    <td width="25%"><INPUT TYPE="TEXT" NAME="name"
                                                                           VALUE="${carlos:forHtmlAttribute(name)}"
                                                                           WIDTH="10" HEIGHT="20" border="0" hspace="2"
                                                                           maxlength="50"
                                                                           required>
                                                    <td width="20%">
                                                        <div align="right"><font face="arial"><fmt:message key="duration"/><fmt:message key="global.labelSeparator"/></font></div>
                                                    </td>
                                                    <td width="25%"><INPUT TYPE="text" NAME="duration"
                                                                           VALUE="${carlos:forHtmlAttribute(duration)}"
                                                                           WIDTH="5" HEIGHT="20" border="0"
                                                                           inputmode="numeric" pattern="[0-9]+"
                                                                           maxlength="10" required></td>
                                                </tr>
                                                <tr valign="middle" BGCOLOR="#EEEEFF">
                                                    <td>
                                                        <div align="right"><font face="arial"><font
                                                                face="arial"><fmt:message key="reason"/><fmt:message key="global.labelSeparator"/></font></font></div>
                                                    </td>
                                                    <td><TEXTAREA NAME="reason" COLS="40" ROWS="2" border="0" hspace="2"
                                                                  maxlength="80">${carlos:forHtmlContent(reason)}</TEXTAREA>
                                                    </td>
                                                    <td>
                                                        <div align="right"><font face="arial"><fmt:message key="Appointment.formNotes"/><fmt:message key="global.labelSeparator"/></font></div>
                                                    </td>
                                                    <td><TEXTAREA NAME="notes" COLS="40" ROWS="2" border="0" hspace="2"
                                                                  maxlength="80">${carlos:forHtmlContent(notes)}</TEXTAREA>
                                                    </td>
                                                </tr>
                                                <tr valign="middle" BGCOLOR="#EEEEFF">
                                                    <td align="right"><font face="arial"><fmt:message key="location"/><fmt:message key="global.labelSeparator"/></font></td>
                                                    <td>
                                                        <c:if test="${not empty locationsList}">
                                                            <select name="location">
                                                                <option value="0"><fmt:message key="appointment.appointmentTypeList.lblSelectLocation"/></option>
                                                                <c:forEach var="siteLocation" items="${locationsList}">
                                                                    <c:set var="locValue" value="${siteLocation.label}" />
                                                                    <option value="${carlos:forHtmlAttribute(locValue)}" <c:if test="${siteLocation.label eq location}">selected</c:if>>
                                                                        ${carlos:forHtmlContent(siteLocation.label)}
                                                                    </option>
                                                                </c:forEach>
                                                            </select>
                                                        </c:if>

                                                        <c:if test="${empty locationsList}">
                                                            <input type="text" name="location"
                                                                   value="${carlos:forHtmlAttribute(location)}"
                                                                   width="30" height="20" border="0" hspace="2" maxlength="255"/>
                                                        </c:if>
                                                    </td>
                                                    <td>
                                                        <div align="right"><font face="arial"><fmt:message key="Appointment.formResources"/><fmt:message key="global.labelSeparator"/></font></div>
                                                    </td>
                                                    <td><INPUT TYPE="TEXT" NAME="resources"
                                                               VALUE="${carlos:forHtmlAttribute(resources)}"
                                                               WIDTH="10" HEIGHT="20" maxlength="10" border="0"
                                                               hspace="2"></td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>
                                </table>
                                <table border="0" cellpadding="0" cellspacing="0" width="100%">
                                    <tr bgcolor="#CCCCFF">
                                        <TD nowrap align="center"><input type="submit" value="<fmt:message key='global.btnSave'/>" />
                                            <c:if test="${not empty id}">
                                                <a href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/appointment/appointmentTypeAction"><fmt:message key="appointment.appointmentTypeList.btnNew"/></a>
                                                <a href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/appointment/appointmentTypeAction"><fmt:message key="global.btnCancel"/></a>
                                            </c:if>
                                        </TD>
                                    </tr>
                                </table>
                            </form>
                        </center>
                    </td>
                </tr>
                <tr bgcolor="silver">
                    <th width="15%" nowrap>
                        <fmt:message key="name"/>
                    </th>
                    <th width="5%" nowrap>
                        <fmt:message key="duration"/>
                    </th>
                    <th width="20%" nowrap>
                        <fmt:message key="reason"/>
                    </th>
                    <th width="20%" nowrap>
                        <fmt:message key="Appointment.formNotes"/>
                    </th>
                    <th width="15%" nowrap>
                        <fmt:message key="location"/>
                    </th>
                    <th width="15%" nowrap>
                        <fmt:message key="Appointment.formResources"/>
                    </th>
                    <th width="10%" nowrap>
                    </th>
                </tr>
                <%
                    boolean bMultisites = IsPropertiesOn.isMultisitesEnable();
                    List<AppointmentType> types = new ArrayList<AppointmentType>();
                    AppointmentTypeDao dao = (AppointmentTypeDao) SpringUtils.getBean(AppointmentTypeDao.class);
                    types = dao.listAll();

                    int rowNum = 0;
                    String color = "#ccCCFF";
                    String bgColor = "#EEEEFF";
                    if (types != null && types.size() > 0) {
                        for (AppointmentType type : types) {
                            bgColor = bgColor.equals("#EEEEFF") ? color : "#EEEEFF";
                %>
                <tr bgcolor="<%=bgColor%>">
                    <td>
                        <%= SafeEncode.forHtmlContent(type.getName()) %>
                    </td>
                    <th>
                        <%= Integer.toString(type.getDuration()) %> <fmt:message key="appointment.appointmentTypeList.msgMinutesAbbrev"/>
                    </th>
                    <th>
                        <%= SafeEncode.forHtmlContent(type.getReason()) %>
                    </th>
                    <th>
                        <%= SafeEncode.forHtmlContent(type.getNotes()) %>
                    </th>
                    <th nowrap>
                        <%= SafeEncode.forHtmlContent(type.getLocation()) %>
                    </th>
                    <th nowrap>
                        <%= SafeEncode.forHtmlContent(type.getResources()) %>
                    </th>
                    <th nowrap>
                        <a href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/appointment/appointmentTypeAction?oper=edit&amp;no=<%= type.getId() %>"><fmt:message key="global.btnEdit"/></a>
                        &nbsp;&nbsp;
                        <form class="inline-action delete-appointment-type" action="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/appointment/appointmentTypeAction" method="post">
                            <input type="hidden" name="oper" value="del"/>
                            <input type="hidden" name="no" value="<%= type.getId() %>"/>
                            <button class="link-button" type="submit"><fmt:message key="global.btnDelete"/></button>
                        </form>
                    </th>
                </tr>
                <%
                        }
                    }
                %>
            </table>
        <td width="100">&nbsp;</td>
    </tr>
</table>
</body>
<script type="text/javascript">
    const i18nDeleteConfirm = "${carlos:forJavaScript(msgDeleteConfirm)}";
    document.querySelectorAll('.delete-appointment-type').forEach(function (form) {
        form.addEventListener('submit', function (event) {
            if (!window.confirm(i18nDeleteConfirm)) {
                event.preventDefault();
            }
        });
    });
</script>
</html>
