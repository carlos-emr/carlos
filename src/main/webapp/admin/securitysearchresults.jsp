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
<!DOCTYPE html>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ page import="java.sql.*, java.util.*, ca.openosp.*" buffer="none" %>

<%@ page import="java.util.*" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.openo.commn.model.Security" %>
<%@ page import="ca.openosp.openo.commn.dao.SecurityDao" %>
<%@ page import="ca.openosp.openo.commn.model.UserProperty" %>
<%@ page import="ca.openosp.openo.commn.dao.UserPropertyDAO" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");

    boolean isSiteAccessPrivacy = false;
    boolean authed = true;
%>

<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.userAdmin" rights="*" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin&type=_admin.userAdmin");%>
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
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securitysearchresults.title"/></title>
        <c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>
        <link href="${pageContext.request.contextPath}/css/DT_bootstrap.css" rel="stylesheet">
        <link href="${pageContext.request.contextPath}/library/DataTables-1.10.12/media/css/jquery.dataTables.min.css"
              rel="stylesheet">
        <link href="${pageContext.request.contextPath}/css/bootstrap.css" rel="stylesheet" type="text/css">
        <!-- Bootstrap 2.3.1 -->

        <script src="${pageContext.request.contextPath}/library/jquery/jquery-3.6.4.min.js"></script>
        <script src="${pageContext.request.contextPath}/library/DataTables/datatables.min.js"></script>
        <!-- DataTables 1.13.4 -->
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
                        "url": "<%=request.getContextPath() %>/library/DataTables/i18n/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.i18nLanguagecode"/>.json"
                    }
                });
            });
        </script>
    </head>
    <body onLoad="setfocus()">

    <h4><i class="icon-search" title=""></i>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securitysearchresults.description"/></h4>
    <div name="alert" style="display:none;" class="alert alert-error"></div>
    <div class="well">
        <form method="post" action="securitysearchresults.jsp" name="searchprovider">
            <table style="width:100%">
                <tr>
                    <td style="text-align:right; vertical-align:middle"><b><i><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securitysearchrecordshtm.msgCriteria"/></i></b>&nbsp;&nbsp;
                    </td>
                    <td style="white-space: nowrap;">
                        <input type="radio" name="search_mode" value="search_username">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityrecord.formUserName"/></td>
                    <td style="white-space: nowrap;">
                        <input type="radio" checked name="search_mode"
                               value="search_providerno"> <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityrecord.formProviderNo"/></td>
                    <td style="vertical-align:middle; text-align:left">
                        <div class="input-append" name="keywordwrap">
                            <input type="text" name="keyword" class="input input-large" maxlength="100">
                            <button type="submit" name="button" class="btn add-on" style="height:30px; width:30px;">
                                <i class="icon-search"
                                   title="<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securitysearchrecordshtm.btnSearch"/>"></i></button>
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
            <td style="text-align:left"><i><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.search.keywords"/></i>:
                <%=Encode.forHtmlContent(request.getParameter("keyword"))%>
            </td>
        </tr>
    </table>
    <table style="width:100%" id="tblResults" class="table table-hover table-striped table-condensed">
        <thead>
        <tr>
            <th style="text-align:center; width:20%"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityrecord.formUserName"/></b></th>
            <th style="text-align:center; width:40%"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityrecord.formPassword"/></b></th>
            <th style="text-align:center; width:20%"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityrecord.formProviderNo"/></b></th>
            <th style="text-align:center; width:20%"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityrecord.formPIN"/></b></th>
        </tr>
        </thead>
        <%
            List<Security> securityList = securityDao.findAllOrderBy("user_name");

            //if action is good, then give me the result
            String searchMode = request.getParameter("search_mode");
            String keyword = request.getParameter("keyword").trim() + "%";

            // if search mode is provider_no
            if (searchMode.equals("search_providerno"))
                securityList = securityDao.findByLikeProviderNo(keyword);

            // if search mode is user_name
            if (searchMode.equals("search_username"))
                securityList = securityDao.findByLikeUserName(keyword);

            for (Security securityRecord : securityList) {
        %>
        <tr>
            <td>
                <a href='securityupdatesecurity.jsp?keyword=<%=securityRecord.getId()%>'><%= Encode.forHtmlContent(securityRecord.getUserName()) %>
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
    <p><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securitysearchresults.msgClickForDetail"/></p>
    </body>
</html>