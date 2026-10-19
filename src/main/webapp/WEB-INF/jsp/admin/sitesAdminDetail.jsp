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
<%-- This JSP is the multi-site admin site detail page. Its fields post as site.* (SitesManage2Action.getSite());
     each one is filled from that site, so an opened site shows its stored values and its Save updates it. --%>
<%@ include file="/taglibs.jsp" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
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
        <style>.button {
            border: 1px solid #666666;
        } </style>

    </head>

    <body vlink="#0000FF" class="BodyStyle" onload="document.getElementById('colorField').style.backgroundColor = document.getElementById('colorField').value;">
    <form action="<%= request.getContextPath() %>/admin/ManageSites" method="post">
        <table class="MainTable">
            <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn"><fmt:message key="global.admin"/></td>
                <td class="MainTableTopRowRightColumn">
                    <table class="TopStatusBar" style="width: 100%;">
                        <tr>
                            <td><fmt:message key="admin.sitesAdminDetail.heading"/></td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="MainTableLeftColumn" valign="top" width="160px;">
                    &nbsp;
                </td>
                <td class="MainTableRightColumn" valign="top">
                    <c:if test="${not empty savedMessage}">
                        <div class="messages">
                                ${carlos:forHtml(savedMessage)}
                        </div>
                    </c:if>
                    <c:if test="${not empty missingFields}">
                        <%-- errors.required is bundle text (the Struts 1 page printed it the same way); each argument is a label. --%>
                        <ul class="messages">
                            <c:forEach items="${missingFields}" var="labelKey">
                                <fmt:message key="errors.required"><fmt:param><fmt:message key="${labelKey}"/></fmt:param></fmt:message>
                            </c:forEach>
                        </ul>
                    </c:if>

                    <table>
                        <tr>
                            <td><label for="siteName"><fmt:message key="admin.sitesAdminDetail.label.siteName"/>:</label><sup style="color:red">*</sup></td>
                            <td><input type="text" id="siteName" name="site.name" maxlength="30" value="${carlos:forHtmlAttribute(site.name)}" /></td>
                        </tr>
                        <tr>
                            <td><label for="siteShortName"><fmt:message key="admin.sitesAdminDetail.label.shortName"/>:</label><sup style="color:red">*</sup></td>
                            <td><input type="text" id="siteShortName" name="site.shortName" maxlength="10" value="${carlos:forHtmlAttribute(site.shortName)}" /></td>
                        </tr>
                        <tr>
                            <td><label for="colorField"><fmt:message key="admin.sitesAdminDetail.label.themeColor"/>:</label><sup style="color:red">*</sup></td>
                            <td><input type="text" id="colorField" name="site.bgColor" type="color" value="${carlos:forHtmlAttribute(site.bgColor)}"
                                             onchange="this.style.backgroundColor = this.value;" />
                            </td>
                        </tr>
                        <tr>
                            <td><label for="siteStatus"><fmt:message key="admin.sitesAdminDetail.label.active"/>:</label></td>
                            <td><input type="checkbox" id="siteStatus" name="site.status" value="1" <c:if test="${site.status == 1}">checked</c:if> /></td>
                        </tr>
                        <tr>
                            <td><label for="sitePhone"><fmt:message key="admin.sitesAdminDetail.label.telephone"/>:</label></td>
                            <td><input type="text" id="sitePhone" name="site.phone" value="${carlos:forHtmlAttribute(site.phone)}" /></td>
                        </tr>
                        <tr>
                            <td><label for="siteFax"><fmt:message key="admin.sitesAdminDetail.label.fax"/>:</label></td>
                            <td><input type="text" id="siteFax" name="site.fax" value="${carlos:forHtmlAttribute(site.fax)}" /></td>
                        </tr>
                        <tr>
                            <td><label for="siteAddress"><fmt:message key="admin.sitesAdminDetail.label.address"/>:</label></td>
                            <td><input type="text" id="siteAddress" name="site.address" value="${carlos:forHtmlAttribute(site.address)}" /></td>
                        </tr>
                        <tr>
                            <td><label for="siteCity"><fmt:message key="admin.sitesAdminDetail.label.city"/>:</label></td>
                            <td><input type="text" id="siteCity" name="site.city" value="${carlos:forHtmlAttribute(site.city)}" /></td>
                        </tr>
                        <tr>
                            <td><label for="siteProvince"><fmt:message key="admin.sitesAdminDetail.label.province"/>:</label></td>
                            <td><select id="siteProvince" name="site.province">
                                <c:forTokens items="AB,BC,MB,NB,NL,NT,NS,NU,ON,PE,QC,SK,YT" delims="," var="code">
                                <option value="${carlos:forHtmlAttribute(code)}" <c:if test="${site.province == code}">selected</c:if>><fmt:message key="admin.sitesAdminDetail.province.${code}"/></option>
                                </c:forTokens>
                            </select></td>
                        </tr>
                        <tr>
                            <td><label for="sitePostal"><fmt:message key="admin.sitesAdminDetail.label.postalCode"/>:</label></td>
                            <td><input type="text" id="sitePostal" name="site.postal" value="${carlos:forHtmlAttribute(site.postal)}" /></td>
                        </tr>
                        <% if (IsPropertiesOn.isProviderFormalizeEnable()) { %>
                        <tr>
                            <td><label for="siteProviderIdFrom"><fmt:message key="admin.sitesAdminDetail.label.providerIdFrom"/>:</label></td>
                            <td><input type="text" id="siteProviderIdFrom" name="site.providerIdFrom" value="${carlos:forHtmlAttribute(site.providerIdFrom)}" /></td>
                        </tr>
                        <tr>
                            <td><label for="siteProviderIdTo"><fmt:message key="admin.sitesAdminDetail.label.providerIdTo"/>:</label></td>
                            <td><input type="text" id="siteProviderIdTo" name="site.providerIdTo" value="${carlos:forHtmlAttribute(site.providerIdTo)}" /></td>
                        </tr>
                        <% } %>
                    </table>

                    <input type="hidden" name="site.siteId" value="${carlos:forHtmlAttribute(site.siteId)}" />
                    <input type="hidden" name="method" value="save" />
                    <input type="submit" class="button" value="<fmt:message key='global.btnSave'/>" /> <input type="submit" class="button"
                                                                                           onclick="this.form.method.value='view'" value="<fmt:message key='global.btnCancel'/>" />

                </td>
            </tr>
            <tr>
                <td class="MainTableBottomRowLeftColumn">&nbsp;</td>

                <td class="MainTableBottomRowRightColumn">&nbsp;</td>
            </tr>
        </table>
    </form>


</html>
