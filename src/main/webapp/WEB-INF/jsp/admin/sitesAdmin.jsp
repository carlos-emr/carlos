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
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
"http://www.w3.org/TR/html4/loose.dtd">
<%-- This JSP is the multi-site admin page --%>
<%@ include file="/taglibs.jsp" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>"
                   objectName="_admin,_admin.misc" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_admin&type=_admin.misc");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page import="io.github.carlos_emr.carlos.commn.model.Site" %>
<%@ page import="io.github.carlos_emr.carlos.commn.IsPropertiesOn" %>
<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="admin.admin.sitesAdmin"/></title>
        <link rel="stylesheet" type="text/css"
              href="<%= request.getContextPath() %>/share/css/OscarStandardLayout.css">

        <script type="text/javascript" language="JavaScript"
                src="<%= request.getContextPath() %>/share/javascript/Oscar.js"></script>
        <link href="${pageContext.request.contextPath}/css/displaytag.css" rel="stylesheet"></link>
    </head>

    <body vlink="#0000FF" class="BodyStyle">

    <table class="MainTable">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn"><fmt:message key="global.admin"/></td>
            <td class="MainTableTopRowRightColumn">
                <table class="TopStatusBar" style="width: 100%;">
                    <tr>
                        <td><fmt:message key="admin.admin.sitesAdmin"/></td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td class="MainTableLeftColumn" valign="top" width="160px;">
                &nbsp;
            </td>
            <td class="MainTableRightColumn" valign="top">

                <form action="<%= request.getContextPath() %>/admin/ManageSites" method="post">
                    <input type="hidden" name="method" value="add"/>
                    <input type="submit" style="border:1px solid #666666;" value="<fmt:message key='admin.sitesAdmin.btnAddNewSite'/>" />
                </form>

                <table class="its" style="border:1px solid #666666; width:99%;margin-top:2px;" id="site" data-carlos-table data-page-size="0" data-default-sort="0" data-default-order="ascending">
<thead><tr>
                    <th scope="col"><fmt:message key='admin.sitesAdmin.col.active'/></th>
                    <%-- A GET link, so it opens the site with the read-only method=edit (the GET guard refuses method=update). --%>
                    <th scope="col"><fmt:message key='admin.sitesAdmin.col.siteName'/></th>
                    <%-- displaytag prints property= columns unescaped, so each value is written as encoded text. --%>
                    <th scope="col"><fmt:message key='admin.sitesAdmin.col.shortName'/></th>
                    <th scope="col"><fmt:message key='admin.sitesAdmin.col.color'/></th>
                    <th scope="col"><fmt:message key='admin.sitesAdmin.col.telephone'/></th>
                    <th scope="col"><fmt:message key='admin.sitesAdmin.col.fax'/></th>
                    <th scope="col"><fmt:message key='admin.sitesAdmin.col.address'/></th>
                    <th scope="col"><fmt:message key='admin.sitesAdmin.col.city'/></th>
                    <th scope="col"><fmt:message key='admin.sitesAdmin.col.province'/></th>
                    <th scope="col"><fmt:message key='admin.sitesAdmin.col.postalCode'/></th>

                    <th scope="col"><fmt:message key='admin.sitesAdmin.col.providerIdFrom'/></th>
                    <th scope="col"><fmt:message key='admin.sitesAdmin.col.providerIdTo'/></th>

                </tr></thead>
<tbody><c:forEach var="site" items="${sites}" varStatus="tableRowStatus"><tr class="${tableRowStatus.count % 2 == 0 ? 'even' : 'odd'}">
                    <td><c:choose><c:when
                            test="${site.status==0}"><fmt:message key="admin.sitesAdmin.value.no"/></c:when><c:otherwise><fmt:message key="admin.sitesAdmin.value.yes"/></c:otherwise></c:choose></td>
                    <%-- A GET link, so it opens the site with the read-only method=edit (the GET guard refuses method=update). --%>
                    <td>
                        <a href="<%= request.getContextPath() %>/admin/ManageSites?method=edit&siteId=${carlos:forHtmlAttribute(site.siteId)}">${carlos:forHtml(site.name)}</a></td>
                    <%-- displaytag prints property= columns unescaped, so each value is written as encoded text. --%>
                    <td>${carlos:forHtml(site.shortName)}</td>
                    <td style="background-color:${site.cssBgColor}">${carlos:forHtml(site.bgColor)}</td>
                    <td>${carlos:forHtml(site.phone)}</td>
                    <td>${carlos:forHtml(site.fax)}</td>
                    <td style="width: 200px;">${carlos:forHtml(site.address)}</td>
                    <td>${carlos:forHtml(site.city)}</td>
                    <td>${carlos:forHtml(site.province)}</td>
                    <td>${carlos:forHtml(site.postal)}</td>
                    <% if (IsPropertiesOn.isProviderFormalizeEnable()) { %>
                    <td>${carlos:forHtml(site.providerIdFrom)}</td>
                    <td>${carlos:forHtml(site.providerIdTo)}</td>
                    <% } %>
                </tr></c:forEach></tbody>
</table>
<c:if test="${empty sites}"><p class="table-empty">Nothing found to display.</p></c:if>


            </td>
        </tr>
        <tr>
            <td class="MainTableBottomRowLeftColumn">&nbsp;</td>

            <td class="MainTableBottomRowRightColumn">&nbsp;</td>
        </tr>
    </table>

</html>

<link rel="stylesheet" href="${pageContext.request.contextPath}/css/carlos-tables.css"/>
<script src="${pageContext.request.contextPath}/js/carlos-tables.js" defer></script>
