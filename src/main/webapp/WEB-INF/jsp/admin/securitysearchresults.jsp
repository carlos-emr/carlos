<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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

    CARLOS EMR Project
    https://github.com/carlos-emr/carlos
--%>
<%--
  Page role: Renders `securitysearchresults.jsp` for the administration area.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<!DOCTYPE html>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>

<%@ page import="java.sql.*, java.util.*, io.github.carlos_emr.*" buffer="none" %>

<%@ page import="java.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Security" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.SecurityDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.UserProperty" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");

    boolean isSiteAccessPrivacy = false;
    boolean authed = true;
%>

<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.userAdmin" rights="*" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_admin&type=_admin.userAdmin");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>


<security:oscarSec objectName="_site_access_privacy" roleName="<%=roleName$%>" rights="r" reverse="false">
    <%
        isSiteAccessPrivacy = true;
    %>
</security:oscarSec>
<%
    SecurityDao securityDao = SpringUtils.getBean(SecurityDao.class);
    UserPropertyDAO userPropertyDao = SpringUtils.getBean(UserPropertyDAO.class);
%>


<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <title><fmt:message key="admin.securitysearchresults.title"/></title>
        <c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>
        <link href="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.11/css/dataTables.bootstrap5.min.css" rel="stylesheet">
        <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet" type="text/css">
        <!-- Bootstrap 2.3.1 -->

        <script src="${pageContext.request.contextPath}/library/jquery/jquery-3.7.1.min.js"></script>
        <script src="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.11/js/jquery.dataTables.min.js"></script>
        <script src="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.11/js/dataTables.bootstrap5.min.js"></script>
        <script>
            function setfocus() {
                document.searchprovider.keyword.focus();
                document.searchprovider.keyword.select();
            }

        </script>
        <script>
            jQuery(document).ready(function () {
                jQuery('#tblResults').DataTable({
                    "language": {
                        "url": "<%=request.getContextPath() %>/library/DataTables/i18n/<fmt:message key="global.i18n.datatablescode"/>.json"
                    }
                });
            });
        </script>
    <link rel="stylesheet" href="<%=request.getContextPath() %>/css/fontawesome-all.min.css">
    </head>
    <body onLoad="setfocus()">

    <h4><i class="fa-solid fa-magnifying-glass" title=""></i>&nbsp;<fmt:message key="admin.securitysearchresults.description"/></h4>
    <div name="alert" style="display:none;" class="alert alert-danger"></div>
    <div class="card card-body bg-body-tertiary">
        <form method="post" action="${pageContext.request.contextPath}/admin/SecuritySearchResults" name="searchprovider">
            <table style="width:100%">
                <tr>
                    <td style="text-align:right; vertical-align:middle"><b><i><fmt:message key="admin.securitysearchrecordshtm.msgCriteria"/></i></b>&nbsp;&nbsp;
                    </td>
                    <td style="white-space: nowrap;">
                        <input type="radio" name="search_mode" value="search_username">
                        <fmt:message key="admin.securityrecord.formUserName"/></td>
                    <td style="white-space: nowrap;">
                        <input type="radio" checked name="search_mode"
                               value="search_providerno"> <fmt:message key="admin.securityrecord.formProviderNo"/></td>
                    <td style="vertical-align:middle; text-align:left">
                        <div class="input-group" name="keywordwrap">
                            <input type="text" name="keyword" class="form-control" maxlength="100">
                            <button type="submit" name="button" class="btn input-group-text" style="height:30px; width:30px;">
                                <i class="fa-solid fa-magnifying-glass"
                                   title="<fmt:message key="admin.securitysearchrecordshtm.btnSearch"/>"></i></button>
                        </div>
                        <input type="hidden" name="orderby" value="user_name">
                        <input type="hidden" name="limit1" value="0">
                        <input type="hidden" name="limit2" value="10000">
                    </td>
                </tr>
            </table>
        </form>
    </div>
    <table style="width:100%">
        <tr>
            <td style="text-align:left"><i><fmt:message key="admin.search.keywords"/></i>:
                <carlos:encode value='<%= request.getParameter("keyword") != null ? request.getParameter("keyword") : "" %>' context="html"/><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
            </td>
        </tr>
    </table>
    <table style="width:100%" id="tblResults" class="table table-hover table-striped table-sm">
        <thead>
        <tr>
            <th style="text-align:center; width:20%"><b><fmt:message key="admin.securityrecord.formUserName"/></b></th>
            <th style="text-align:center; width:40%"><b><fmt:message key="admin.securityrecord.formPassword"/></b></th>
            <th style="text-align:center; width:20%"><b><fmt:message key="admin.securityrecord.formProviderNo"/></b></th>
            <th style="text-align:center; width:20%"><b><fmt:message key="admin.securityrecord.formPIN"/></b></th>
        </tr>
        </thead>
        <%
            List<Security> securityList = securityDao.findAllOrderBy("userName");

            //if action is good, then give me the result
            String searchMode = request.getParameter("search_mode") != null ? request.getParameter("search_mode") : "";
            String keyword = (request.getParameter("keyword") != null ? request.getParameter("keyword").trim() : "") + "%";

            // if search mode is provider_no
            if ("search_providerno".equals(searchMode))
                securityList = securityDao.findByLikeProviderNo(keyword);

            // if search mode is user_name
            if ("search_username".equals(searchMode))
                securityList = securityDao.findByLikeUserName(keyword);

            for (Security securityRecord : securityList) {
        %>
        <tr>
            <td>
                <a href='${pageContext.request.contextPath}/admin/ViewSecurityUpdateSecurity?keyword=<%=securityRecord.getId()%>'><carlos:encode value='<%= securityRecord.getUserName() %>' context="html"/>
                </a></td>
            <td style="text-align:center">*********</td>
            <td style="text-align:center"><%= securityRecord.getProviderNo() %>
            </td>
            <td style="text-align:center">****</td>
        </tr>
        <%
            }
        %>
    </table>
    <br>
    <p><fmt:message key="admin.securitysearchresults.msgClickForDetail"/></p>
    </body>
</html>
