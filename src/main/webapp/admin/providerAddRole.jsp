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

--%>

<%@ page errorPage="/errorpage.jsp" %>
<%@ page import="java.util.*" %>
<%@ page import="java.sql.*" %>
<%@ page import="org.apache.commons.text.StringEscapeUtils" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>

<%@ page import="ca.openosp.openo.login.*" %>
<%@ page import="ca.openosp.openo.log.*" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.openo.commn.model.SecRole" %>
<%@ page import="ca.openosp.openo.commn.dao.SecRoleDao" %>
<%@ page import="ca.openosp.openo.PMmodule.utility.RoleCache" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="ca.openosp.openo.log.LogAction" %>
<%@ page import="ca.openosp.openo.log.LogConst" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%
    SecRoleDao secRoleDao = SpringUtils.getBean(SecRoleDao.class);

    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    String curUser_no = (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.userAdmin" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin&type=_admin.userAdmin");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%
    java.util.ResourceBundle oscarRec = ResourceBundle.getBundle("oscarResources", request.getLocale());
    String searchFirst = oscarRec.getString("admin.provideraddrole.title2");
    int ROLENAME_LENGTH = 30;
    String ip = request.getRemoteAddr();
    String msg = searchFirst;
    String role_name = request.getParameter("role_name");
    String action = "search"; // add/edit
    Properties prop = new Properties();

    if (request.getParameter("submit") != null && request.getParameter("submit").equals("Save")) {
        // check the input data
        String encodedRoleName = Encode.forHtmlContent(StringUtils.trimToEmpty(role_name));
        if (request.getParameter("action").startsWith("edit")) {
            // update the code
            SecRole secRole = secRoleDao.findByName(request.getParameter("action").substring(4));
            if (secRole != null) {
                secRole.setName(role_name);
                secRoleDao.merge(secRole);
                RoleCache.reload();
                msg = encodedRoleName + " is updated.<br>" + searchFirst;
                action = "search";
                prop.setProperty("role_name", role_name);
                LogAction.addLog(curUser_no, LogConst.UPDATE, LogConst.CON_ROLE, role_name, ip);
            } else {
                msg = encodedRoleName + " is <font color='red'>NOT</font> updated. Action failed! Try edit it again.";
                action = "edit" + role_name;
                prop.setProperty("role_name", role_name);
            }

        } else if (request.getParameter("action").startsWith("add")) {
            if (role_name.equals(request.getParameter("action").substring("add".length()))) {
                SecRole secRole = new SecRole();
                secRole.setName(role_name);
                secRole.setDescription(role_name);
                secRoleDao.persist(secRole);
                RoleCache.reload();

                msg = encodedRoleName + " is added.<br>" + searchFirst;
                action = "search";
                prop.setProperty("role_name", role_name);
                LogAction.addLog(curUser_no, LogConst.ADD, LogConst.CON_ROLE, role_name, ip);
            } else {
                msg = "You can <font color='red'>NOT</font> save the role  - " + encodedRoleName + ". Please search the role name first.";
                action = "search";
                prop.setProperty("role_name", role_name);
            }
        } else {
            msg = "You can <font color='red'>NOT</font> save the role. Please search the role name first.";
        }
    } else if (request.getParameter("submit") != null && request.getParameter("submit").equals("Search")) {
        // check the input data
        if (role_name == null || role_name.length() < 2) {
            msg = "Please type in a role name.";
        } else {
            SecRole secRole = null;
            try {
                secRole = secRoleDao.findByName(role_name);
            } catch (javax.persistence.NoResultException e) {
            }

            if (secRole != null) {
                prop.setProperty("role_name", secRole.getName());
                msg = "You can edit the role. (Please note: The change of the role may affect data in other tables.)";
                action = "edit" + role_name;
            } else {
                prop.setProperty("role_name", role_name);
                msg = "It is a NEW role. You can add it.";
                action = "add" + role_name;
            }
        }
    }
%>


<!DOCTYPE HTML>
<html>
    <head>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.addRole"/></title>
        <script src="${pageContext.request.contextPath}/js/global.js"></script>
        <link href="${pageContext.request.contextPath}/css/bootstrap.css" rel="stylesheet" type="text/css">
        <!-- Bootstrap 2.3.1 -->

        <link href="${pageContext.request.contextPath}/library/jquery/jquery-ui.theme-1.12.1.min.css" rel="stylesheet">
        <link href="${pageContext.request.contextPath}/library/jquery/jquery-ui.structure-1.12.1.min.css"
              rel="stylesheet">

        <script src="${pageContext.request.contextPath}/library/jquery/jquery-3.6.4.min.js"></script>
        <script src="${pageContext.request.contextPath}/library/jquery/jquery-ui-1.12.1.min.js"></script>

        <script type="text/javascript" language="JavaScript">
            <!--
            function setfocus() {
                this.focus();
                document.forms[0].role_name.focus();
                document.forms[0].role_name.select();
            }

            function onSearch() {
                //document.forms[0].submit.value="Search";
                var ret = checkreferral_no();
                return ret;
            }

            function onSave() {
                //document.forms[0].submit.value="Save";
                var ret = checkreferral_no();
                if (ret == true) {
                    //ret = checkAllFields();
                }
                if (ret == true) {
                    ret = confirm("Are you sure you want to save?");
                }
                return ret;
            }

            function checkreferral_no() {
                var b = true;
                if (document.forms[0].role_name.value.length < 2) {
                    b = false;
                    alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provideraddrole.msgyoumusttype"/>");
                }
                return b;
            }

            function isreferral_no(s) {
                // temp for 0.
                if (s.length == 0) return true;
                if (s.length != 6) return false;
                var i;
                for (i = 0; i < s.length; i++) {
                    // Check that current character is number.
                    var c = s.charAt(i);
                    if (((c < "0") || (c > "9"))) return false;
                }
                return true;
            }

            function checkAllFields() {
                var b = true;
                if (document.forms[0].last_name.value.length <= 0) {
                    b = false;
                    alert("The field \"Last Name\" is empty.");
                } else if (document.forms[0].first_name.value.length <= 0) {
                    b = false;
                    alert("The field \"First Name\" is empty.");
                }
                return b;
            }

            function isNumber(s) {
                var i;
                for (i = 0; i < s.length; i++) {
                    // Check that current character is number.
                    var c = s.charAt(i);
                    if (c == ".") continue;
                    if (((c < "0") || (c > "9"))) return false;
                }
                // All characters are numbers.
                return true;
            }

            //-->

            $(document).ready(function () {
                var currentRoles = [
                    <%
                    List<SecRole> secRoles = secRoleDao.findAll();
                    for(SecRole secRole:secRoles) {
                        %>
                    "<%=Encode.forHtmlAttribute(secRole.getName())%>",
                    <%}%>
                    ""
                ];
                $("#role_name").autocomplete({
                        source: currentRoles,
                        minLength: 2
                    }
                );


            });
        </script>
    </head>
    <body onLoad="setfocus()">
    <h4><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.addRole"/></h4>

    <span style="display: inline-block; width:100%; margin:auto; text-align:center;" class="alert"><%=msg%></span>
    <br><br>
    <div class="well">
        <form method="post" name="baseurl" action="providerAddRole.jsp" class="form-horizontal">
            <div class="control-group">
                <label class="control-label" for="role_name"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provideraddrole.rolename"/></label>
                <div class="controls">
                    <input type="text" name="role_name" id="role_name"
                           value="<%=Encode.forHtmlAttribute(prop.getProperty("role_name", ""))%>"
                           maxlength='30'>
                    <input type="submit" name="submit" value="Search" class="btn"
                           onclick="javascript:return onSearch();">
                </div>
            </div>
            <div class="control-group">
                <div class="controls">
                    <input
                            type="hidden" name="action" value='<%=action%>'/> <% if (!"search".equals(action)) {%>
                    <input type="submit" name="submit" class="btn btn-primary"
                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.resourcebaseurl.btnSave"/>"
                           onclick="javascript:return onSave();"> <% }%>
                </div>
            </div>
        </form>
    </div>

    </body>
</html>