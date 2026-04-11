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
        <title><fmt:message key="admin.sitesAdminDetail.title"/></title>
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
    <form action="<%= request.getContextPath() %>/admin/ManageSites.do" method="post">
        <table class="MainTable">
            <tr class="MainTableTopRow">
                <td class="MainTableTopRowLeftColumn"><fmt:message key="admin.sitesAdminDetail.labelAdmin"/></td>
                <td class="MainTableTopRowRightColumn">
                    <table class="TopStatusBar" style="width: 100%;">
                        <tr>
                            <td><fmt:message key="admin.sitesAdminDetail.headingAddNewSatelliteSite"/></td>
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
                                ${savedMessage}
                        </div>
                    </c:if>

                    <fmt:message key="admin.sitesAdminDetail.btnSave" var="btnSave"/>
                    <fmt:message key="admin.sitesAdminDetail.btnCancel" var="btnCancel"/>
                    <table>
                        <tr>
                            <td><fmt:message key="admin.sitesAdminDetail.labelSiteName"/>:<sup style="color:red">*</sup></td>
                            <td><input type="text" name="site.name" maxlength="30" /></td>
                        </tr>
                        <tr>
                            <td><fmt:message key="admin.sitesAdminDetail.labelShortName"/>:<sup style="color:red">*</sup></td>
                            <td><input type="text" name="site.shortName" maxlength="10" /></td>
                        </tr>
                        <tr>
                            <td><fmt:message key="admin.sitesAdminDetail.labelThemeColor"/>:<sup style="color:red">*</sup></td>
                            <td><input type="text" id="colorField" name="site.bgColor" type="color"
                                             onchange="this.style.backgroundColor = this.value;" />
                            </td>
                        </tr>
                        <tr>
                            <td><fmt:message key="admin.sitesAdminDetail.labelActive"/>:</td>
                            <td><input type="checkbox" name="site.status" value="1" /></td>
                        </tr>
                        <tr>
                            <td><fmt:message key="admin.sitesAdminDetail.labelTelephone"/>:</td>
                            <td><input type="text" name="site.phone" /></td>
                        </tr>
                        <tr>
                            <td><fmt:message key="admin.sitesAdminDetail.labelFax"/>:</td>
                            <td><input type="text" name="site.fax" /></td>
                        </tr>
                        <tr>
                            <td><fmt:message key="admin.sitesAdminDetail.labelAddress"/>:</td>
                            <td><input type="text" name="site.address" /></td>
                        </tr>
                        <tr>
                            <td><fmt:message key="admin.sitesAdminDetail.labelCity"/>:</td>
                            <td><input type="text" name="site.city" /></td>
                        </tr>
                        <tr>
                            <td><fmt:message key="admin.sitesAdminDetail.labelProvince"/>:</td>
                            <td><select name="site.province">
                                <option value="AB"><fmt:message key="admin.sitesAdminDetail.optionAB"/></option>
                                <option value="BC"><fmt:message key="admin.sitesAdminDetail.optionBC"/></option>
                                <option value="MB"><fmt:message key="admin.sitesAdminDetail.optionMB"/></option>
                                <option value="NB"><fmt:message key="admin.sitesAdminDetail.optionNB"/></option>
                                <option value="NL"><fmt:message key="admin.sitesAdminDetail.optionNL"/></option>
                                <option value="NT"><fmt:message key="admin.sitesAdminDetail.optionNT"/></option>
                                <option value="NS"><fmt:message key="admin.sitesAdminDetail.optionNS"/></option>
                                <option value="NU"><fmt:message key="admin.sitesAdminDetail.optionNU"/></option>
                                <option value="ON"><fmt:message key="admin.sitesAdminDetail.optionON"/></option>
                                <option value="PE"><fmt:message key="admin.sitesAdminDetail.optionPE"/></option>
                                <option value="QC"><fmt:message key="admin.sitesAdminDetail.optionQC"/></option>
                                <option value="SK"><fmt:message key="admin.sitesAdminDetail.optionSK"/></option>
                                <option value="YT"><fmt:message key="admin.sitesAdminDetail.optionYT"/></option>
                            </select></td>
                        </tr>
                        <tr>
                            <td><fmt:message key="admin.sitesAdminDetail.labelPostalCode"/>:</td>
                            <td><input type="text" name="site.postal" /></td>
                        </tr>
                        <% if (IsPropertiesOn.isProviderFormalizeEnable()) { %>
                        <tr>
                            <td><fmt:message key="admin.sitesAdminDetail.labelProviderIdFrom"/>:</td>
                            <td><input type="text" name="site.providerIdFrom" /></td>
                        </tr>
                        <tr>
                            <td><fmt:message key="admin.sitesAdminDetail.labelProviderIdTo"/>:</td>
                            <td><input type="text" name="site.providerIdTo" /></td>
                        </tr>
                        <% } %>
                    </table>

                    <input type="hidden" name="site.siteId" />
                    <input type="hidden" name="method" value="save" />
                    <input type="submit" class="button" value="${btnSave}" /> <input type="submit" class="button"
                                                                                           onclick="this.form.method.value='view'" value="${btnCancel}" />

                </td>
            </tr>
            <tr>
                <td class="MainTableBottomRowLeftColumn">&nbsp;</td>

                <td class="MainTableBottomRowRightColumn">&nbsp;</td>
            </tr>
        </table>
    </form>


</html>
