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
<%@ page
        import="java.util.*, java.sql.*, ca.openosp.*, java.text.*, java.lang.*,java.net.*, ca.openosp.openo.appt.*, ca.openosp.openo.commn.dao.AppointmentTypeDao, ca.openosp.openo.commn.model.AppointmentType, ca.openosp.openo.utility.SpringUtils" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib prefix="s" uri="/struts-tags" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ include file="/admin/dbconnection.jsp" %>
<%--RJ 07/07/2006 --%>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");

    String sError = "";
    if (request.getParameter("err") != null && !request.getParameter("err").equals(""))
        sError = "Error: " + request.getParameter("err");
%>

<%@ page errorPage="/errorpage.jsp" %>
<%@ page import="java.util.*" %>
<%@ page import="java.sql.*" %>
<%@ page import="ca.openosp.openo.util.*" %>
<%@ page import="ca.openosp.openo.login.*" %>
<%@ page import="ca.openosp.openo.log.*" %>
<%@ page import="org.apache.commons.text.StringEscapeUtils" %>
<%@ page import="ca.openosp.openo.commn.IsPropertiesOn" %>
<html>
<head>
    <title>
        APPOINTMENT TYPES
    </title>
    <script language="JavaScript">
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
            window.alert("Please enter appointment type name");
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
                    alert("Please enter value in Names field");
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
                        &nbsp;&nbsp;&nbsp;&nbsp; Appointment Types
                    </th>
                </tr>
                <tr>
                    <td colspan=7>
                        <center>
                            <form action="${pageContext.request.contextPath}/appointment/appointmentTypeAction.do" method="post">
                                <input TYPE="hidden" NAME="oper" VALUE="save"/>
                                <input TYPE="hidden" NAME="id"
                                       VALUE="<c:out value="${id}"/>"/>
                                <table border=0 cellspacing=0 cellpadding=0 width="100%">
                                    <tr bgcolor="#CCCCFF">
                                        <th><font face="Helvetica">EDIT APPOINTMENT TYPE</font></th>
                                    </tr>
                                </table>
                                <table border="0" cellpadding="0" cellspacing="0" width="100%">
                                    <tr>
                                        <td width="100%">
                                            <table BORDER="0" CELLPADDING="0" CELLSPACING="1" WIDTH="100%"
                                                   BGCOLOR="#C0C0C0">
                                                <tr valign="middle" BGCOLOR="#EEEEFF">
                                                    <td width="30%">
                                                        <div align="right"><font face="arial">Name:</font></div>
                                                    </td>
                                                    <td width="25%"><INPUT TYPE="TEXT" NAME="name"
                                                                           VALUE="<c:out value="${name}"/>"
                                                                           WIDTH="10" HEIGHT="20" border="0" hspace="2"
                                                                           maxlength="50"
                                                                           onChange="checkTimeTypeIn(this)">
                                                    <td width="20%">
                                                        <div align="right"><font face="arial">Duration:</font></div>
                                                    </td>
                                                    <td width="25%"><INPUT TYPE="TEXT" NAME="duration"
                                                                           VALUE="<c:out value="${duration}"/>"
                                                                           WIDTH="5" HEIGHT="20" border="0"
                                                                           onChange="checkTimeTypeIn(this)"></td>
                                                </tr>
                                                <tr valign="middle" BGCOLOR="#EEEEFF">
                                                    <td>
                                                        <div align="right"><font face="arial"><font
                                                                face="arial">Reason:</font></font></div>
                                                    </td>
                                                    <td><TEXTAREA NAME="reason" COLS="40" ROWS="2" border="0" hspace="2">
                                                        <c:out value="${reason}"/></TEXTAREA>
                                                    </td>
                                                    <td>
                                                        <div align="right"><font face="arial">Notes:</font></div>
                                                    </td>
                                                    <td><TEXTAREA NAME="notes" COLS="40" ROWS="2" border="0" hspace="2">
                                                        <c:out value="${notes}"/>
                                                    </TEXTAREA>
                                                    </td>
                                                </tr>
                                                <tr valign="middle" BGCOLOR="#EEEEFF">
                                                    <td align="right"><font face="arial">Location:</font></td>
                                                    <td>
                                                        <c:if test="${not empty locationsList}">
                                                            <select name="location">
                                                                <option value="0">Select Location</option>
                                                                <c:forEach var="location" items="${locationsList}">
                                                                    <c:set var="locValue" value="${location.label}" />
                                                                    <option value="${locValue}">
                                                                        <c:out value="${location.label}" />
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
                                                        <div align="right"><font face="arial">Resources:</font></div>
                                                    </td>
                                                    <td><INPUT TYPE="TEXT" NAME="resources"
                                                               VALUE="<c:out value="${resources}"/>"
                                                               WIDTH="10" HEIGHT="20" maxlength="10" border="0"
                                                               hspace="2"></td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>
                                </table>
                                <table border="0" cellpadding="0" cellspacing="0" width="100%">
                                    <tr bgcolor="#CCCCFF">
                                        <TD nowrap align="center"><input type="submit" value="    Save  " />
                                        </TD>
                                    </tr>
                                </table>
                            </form>
                        </center>
                    </td>
                </tr>
                <tr bgcolor="silver">
                    <th width="15%" nowrap>
                        Name
                    </th>
                    <th width="5%" nowrap>
                        Duration
                    </th>
                    <th width="20%" nowrap>
                        Reason
                    </th>
                    <th width="20%" nowrap>
                        Notes
                    </th>
                    <th width="15%" nowrap>
                        Location
                    </th>
                    <th width="15%" nowrap>
                        Resources
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
                        <%= type.getName() %>
                    </td>
                    <th>
                        <%= Integer.toString(type.getDuration()) %> min
                    </th>
                    <th>
                        <%= type.getReason() %>
                    </th>
                    <th>
                        <%= type.getNotes() %>
                    </th>
                    <th nowrap>
                        <%= type.getLocation() %>
                    </th>
                    <th nowrap>
                        <%= type.getResources() %>
                    </th>
                    <th nowrap>
                        <a href="${pageContext.request.contextPath}/appointment/appointmentTypeAction.do?oper=edit&no=<%= type.getId() %>">edit</a>
                        &nbsp;&nbsp;
                        <a href="javascript:delType('<%= type.getId() %>')">delete</a>
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
    function delType(id) {
        var answer = confirm("Type will be deleted! Are you sure?")
        if (answer) {
            window.location = "${pageContext.request.contextPath}/appointment/appointmentTypeAction.do?oper=del&no=" + id;
        }
    }
</script>
</html>
