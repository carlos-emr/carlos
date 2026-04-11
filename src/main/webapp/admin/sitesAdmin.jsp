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
<%@ taglib uri="owasp.encoder.jakarta" prefix="e" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>"
                   objectName="_admin,_admin.misc" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin&type=_admin.misc");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page import="io.github.carlos_emr.carlos.commn.model.Site" %>
<%@ page import="io.github.carlos_emr.carlos.commn.IsPropertiesOn" %>
<fmt:setBundle basename="oscarResources"/>
<html lang="${pageContext.request.locale.language}">
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="admin.sitesAdmin.title"/></title>
        <link rel="stylesheet" type="text/css"
              href="<%= request.getContextPath() %>/share/css/OscarStandardLayout.css">

        <script type="text/javascript" language="JavaScript"
                src="<%= request.getContextPath() %>/share/javascript/Oscar.js"></script>
        <link href="${request.contextPath}/css/displaytag.css" rel="stylesheet"></link>
    </head>

    <body vlink="#0000FF" class="BodyStyle">

    <table class="MainTable">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn"><fmt:message key="admin.sitesAdmin.labelAdmin"/></td>
            <td class="MainTableTopRowRightColumn">
                <table class="TopStatusBar" style="width: 100%;">
                    <tr>
                        <td><fmt:message key="admin.sitesAdmin.headingManageSatelliteSiteDetails"/></td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td class="MainTableLeftColumn" valign="top" width="160px;">
                &nbsp;
            </td>
            <td class="MainTableRightColumn" valign="top">

                <form action="<%= request.getContextPath() %>/admin/ManageSites.do" method="post">
                    <input type="hidden" name="method" value="add"/>
                    <fmt:message key="admin.sitesAdmin.btnAddNewSite" var="btnAddNewSite"/>
                    <input type="submit" style="border:1px solid #666666;" value="${btnAddNewSite}" />
                </form>

                <fmt:message key="admin.sitesAdmin.thActive" var="thActive"/>
                <fmt:message key="admin.sitesAdmin.thSiteName" var="thSiteName"/>
                <fmt:message key="admin.sitesAdmin.thShortName" var="thShortName"/>
                <fmt:message key="admin.sitesAdmin.thColor" var="thColor"/>
                <fmt:message key="admin.sitesAdmin.thTelephone" var="thTelephone"/>
                <fmt:message key="admin.sitesAdmin.thFax" var="thFax"/>
                <fmt:message key="admin.sitesAdmin.thAddress" var="thAddress"/>
                <fmt:message key="admin.sitesAdmin.thCity" var="thCity"/>
                <fmt:message key="admin.sitesAdmin.thProvince" var="thProvince"/>
                <fmt:message key="admin.sitesAdmin.thPostalCode" var="thPostalCode"/>
                <fmt:message key="admin.sitesAdmin.thProviderIdFrom" var="thProviderIdFrom"/>
                <fmt:message key="admin.sitesAdmin.thProviderIdTo" var="thProviderIdTo"/>
                <display-el:table name="sites" id="site" class="its"
                                  style="border:1px solid #666666; width:99%;margin-top:2px;">
                    <display-el:column title="${thActive}"><c:choose><c:when
                            test="${site.status==0}"><fmt:message key="global.no"/></c:when><c:otherwise><fmt:message key="global.yes"/></c:otherwise></c:choose></display-el:column>
                    <display-el:column title="${thSiteName}">
                        <a href="<%= request.getContextPath() %>/admin/ManageSites.do?method=update&siteId=${e:forHtmlAttribute(site.siteId)}">${e:forHtml(site.name)}</a></display-el:column>
                    <display-el:column property="shortName" title="${thShortName}"/>
                    <display-el:column property="bgColor" title="${thColor}" style="background-color:${site.bgColor}"/>
                    <display-el:column property="phone" title="${thTelephone}"/>
                    <display-el:column property="fax" title="${thFax}"/>
                    <display-el:column property="address" title="${thAddress}" style="width: 200px;"/>
                    <display-el:column property="city" title="${thCity}"/>
                    <display-el:column property="province" title="${thProvince}"/>
                    <display-el:column property="postal" title="${thPostalCode}"/>
                    <% if (IsPropertiesOn.isProviderFormalizeEnable()) { %>
                    <display-el:column property="providerIdFrom" title="${thProviderIdFrom}"/>
                    <display-el:column property="providerIdTo" title="${thProviderIdTo}"/>
                    <% } %>
                </display-el:table>


            </td>
        </tr>
        <tr>
            <td class="MainTableBottomRowLeftColumn">&nbsp;</td>

            <td class="MainTableBottomRowRightColumn">&nbsp;</td>
        </tr>
    </table>

</html>
