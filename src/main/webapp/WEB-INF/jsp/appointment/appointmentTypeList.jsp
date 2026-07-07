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
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
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

        function checkTypeNum(typeIn) {
            var typeInOK = true;
            var i = 0;
            var length = typeIn.length;
            var ch;
            // walk through a string and find a number
            if (length >= 1) {
                while (i < length) {
                    ch = typeIn.substring(i, i + 1);
                    if (ch == ":") {
                        i++;
                        continue;
                    }
                    if ((ch < "0") || (ch > "9")) {
                        typeInOK = false;
                        break;
                    }
                    i++;
                }
            } else typeInOK = false;
            return typeInOK;
        }

        function checkTimeTypeIn(obj) {
            if (!checkTypeNum(obj.value)) {
//		  alert ("Please enter numeric value in Duration field");
            } else {
                if (obj.value == '') {
                    alert(i18nNamesField);
                    onBlockFieldFocus(obj);
                }
            }
        }
    </script>
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
                        <font face="Helvetica" color="#FFFFFF">&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<% 
    java.util.List<String> actionErrors = (java.util.List<String>) request.getAttribute("actionErrors");
    if (actionErrors != null && !actionErrors.isEmpty()) {
%>
    <div class="action-errors">
        <ul>
            <% for (String error : actionErrors) { %>
                <li><%= error %></li>
            <% } %>
        </ul>
    </div>
<% } %>
                        </font>
                    </th>
                    <td nowrap>
                        <font size="-1" color="#FFFFFF">&nbsp;
                        </font>
                    </td>
                </tr>
            </table>
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
                            <form action="${pageContext.request.contextPath}/appointment/appointmentTypeAction" method="post">
                                <input TYPE="hidden" NAME="oper" VALUE="save"/>
                                <input TYPE="hidden" NAME="id"
                                       VALUE="${carlos:forHtmlAttribute(id)}"/>
                                <table border=0 cellspacing=0 cellpadding=0 width="100%">
                                    <tr bgcolor="#CCCCFF">
                                        <th><font face="Helvetica"><fmt:message key="appointment.appointmentTypeList.formEditTitle"/></font></th>
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
                                                                           onChange="checkTimeTypeIn(this)">
                                                    <td width="20%">
                                                        <div align="right"><font face="arial"><fmt:message key="duration"/><fmt:message key="global.labelSeparator"/></font></div>
                                                    </td>
                                                    <td width="25%"><INPUT TYPE="TEXT" NAME="duration"
                                                                           VALUE="${carlos:forHtmlAttribute(duration)}"
                                                                           WIDTH="5" HEIGHT="20" border="0"
                                                                           onChange="checkTimeTypeIn(this)"></td>
                                                </tr>
                                                <tr valign="middle" BGCOLOR="#EEEEFF">
                                                    <td>
                                                        <div align="right"><font face="arial"><font
                                                                face="arial"><fmt:message key="reason"/><fmt:message key="global.labelSeparator"/></font></font></div>
                                                    </td>
                                                    <td><TEXTAREA NAME="reason" COLS="40" ROWS="2" border="0" hspace="2">
                                                        ${carlos:forHtml(reason)}</TEXTAREA>
                                                    </td>
                                                    <td>
                                                        <div align="right"><font face="arial"><fmt:message key="Appointment.formNotes"/><fmt:message key="global.labelSeparator"/></font></div>
                                                    </td>
                                                    <td><TEXTAREA NAME="notes" COLS="40" ROWS="2" border="0" hspace="2">
                                                        ${carlos:forHtml(notes)}
                                                    </TEXTAREA>
                                                    </td>
                                                </tr>
                                                <tr valign="middle" BGCOLOR="#EEEEFF">
                                                    <td align="right"><font face="arial"><fmt:message key="location"/><fmt:message key="global.labelSeparator"/></font></td>
                                                    <td>
                                                        <c:if test="${not empty locationsList}">
                                                            <select name="location">
                                                                <option value="0"><fmt:message key="appointment.appointmentTypeList.lblSelectLocation"/></option>
                                                                <c:forEach var="location" items="${locationsList}">
                                                                    <c:set var="locValue" value="${location.label}" />
                                                                    <option value="${locValue}">
                                                                        ${carlos:forHtml(location.label)}
                                                                    </option>
                                                                </c:forEach>
                                                            </select>
                                                        </c:if>

                                                        <c:if test="${empty locationsList}">
                                                            <input type="text" name="location"
                                                                   value="${location}"
                                                                   width="30" height="20" border="0" hspace="2" maxlength="30"/>
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
                        <a href="${pageContext.request.contextPath}/appointment/appointmentTypeAction?oper=edit&no=<%= type.getId() %>"><fmt:message key="global.btnEdit"/></a>
                        &nbsp;&nbsp;
                        <a href="javascript:delType('<%= type.getId() %>')"><fmt:message key="global.btnDelete"/></a>
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

    function delType(id) {
        var answer = confirm(i18nDeleteConfirm);
        if (answer) {
            var form = document.createElement('form');
            form.method = 'post';
            form.action = '${pageContext.request.contextPath}/appointment/appointmentTypeAction';
            var fields = {oper: 'del', no: id};
            for (var key in fields) {
                var input = document.createElement('input');
                input.type = 'hidden';
                input.name = key;
                input.value = fields[key];
                form.appendChild(input);
            }
            document.body.appendChild(form);
            form.submit();
        }
    }
</script>
</html>
