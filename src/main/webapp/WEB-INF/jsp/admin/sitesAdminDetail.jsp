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
<%-- This JSP is the multi-site admin site detail page --%>
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
        <link href="${request.contextPath}/css/displaytag.css" rel="stylesheet"></link>
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

                    <table>
                        <tr>
                            <td><fmt:message key="admin.sitesAdminDetail.label.siteName"/>:<sup style="color:red">*</sup></td>
                            <td><input type="text" name="site.name" maxlength="30" /></td>
                        </tr>
                        <tr>
                            <td><fmt:message key="admin.sitesAdminDetail.label.shortName"/>:<sup style="color:red">*</sup></td>
                            <td><input type="text" name="site.shortName" maxlength="10" /></td>
                        </tr>
                        <tr>
                            <td><fmt:message key="admin.sitesAdminDetail.label.themeColor"/>:<sup style="color:red">*</sup></td>
                            <td><input type="text" id="colorField" name="site.bgColor" type="color"
                                             onchange="this.style.backgroundColor = this.value;" />
                            </td>
                        </tr>
                        <tr>
                            <td><fmt:message key="admin.sitesAdminDetail.label.active"/>:</td>
                            <td><input type="checkbox" name="site.status" value="1" /></td>
                        </tr>
                        <tr>
                            <td><fmt:message key="admin.sitesAdminDetail.label.telephone"/>:</td>
                            <td><input type="text" name="site.phone" /></td>
                        </tr>
                        <tr>
                            <td><fmt:message key="admin.sitesAdminDetail.label.fax"/>:</td>
                            <td><input type="text" name="site.fax" /></td>
                        </tr>
                        <tr>
                            <td><fmt:message key="admin.sitesAdminDetail.label.address"/>:</td>
                            <td><input type="text" name="site.address" /></td>
                        </tr>
                        <tr>
                            <td><fmt:message key="admin.sitesAdminDetail.label.city"/>:</td>
                            <td><input type="text" name="site.city" /></td>
                        </tr>
                        <tr>
                            <td><fmt:message key="admin.sitesAdminDetail.label.province"/>:</td>
                            <td><select name="site.province">
                                <option value="AB"><fmt:message key="admin.sitesAdminDetail.province.AB"/></option>
                                <option value="BC"><fmt:message key="admin.sitesAdminDetail.province.BC"/></option>
                                <option value="MB"><fmt:message key="admin.sitesAdminDetail.province.MB"/></option>
                                <option value="NB"><fmt:message key="admin.sitesAdminDetail.province.NB"/></option>
                                <option value="NL"><fmt:message key="admin.sitesAdminDetail.province.NL"/></option>
                                <option value="NT"><fmt:message key="admin.sitesAdminDetail.province.NT"/></option>
                                <option value="NS"><fmt:message key="admin.sitesAdminDetail.province.NS"/></option>
                                <option value="NU"><fmt:message key="admin.sitesAdminDetail.province.NU"/></option>
                                <option value="ON"><fmt:message key="admin.sitesAdminDetail.province.ON"/></option>
                                <option value="PE"><fmt:message key="admin.sitesAdminDetail.province.PE"/></option>
                                <option value="QC"><fmt:message key="admin.sitesAdminDetail.province.QC"/></option>
                                <option value="SK"><fmt:message key="admin.sitesAdminDetail.province.SK"/></option>
                                <option value="YT"><fmt:message key="admin.sitesAdminDetail.province.YT"/></option>
                            </select></td>
                        </tr>
                        <tr>
                            <td><fmt:message key="admin.sitesAdminDetail.label.postalCode"/>:</td>
                            <td><input type="text" name="site.postal" /></td>
                        </tr>
                        <% if (IsPropertiesOn.isProviderFormalizeEnable()) { %>
                        <tr>
                            <td><fmt:message key="admin.sitesAdminDetail.label.providerIdFrom"/>:</td>
                            <td><input type="text" name="site.providerIdFrom" /></td>
                        </tr>
                        <tr>
                            <td><fmt:message key="admin.sitesAdminDetail.label.providerIdTo"/>:</td>
                            <td><input type="text" name="site.providerIdTo" /></td>
                        </tr>
                        <% } %>
                    </table>

                    <input type="hidden" name="site.siteId" />
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
