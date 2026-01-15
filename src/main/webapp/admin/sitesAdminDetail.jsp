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

<%@page import="ca.openosp.openo.commn.model.Site" %>
<%@ page import="ca.openosp.openo.commn.IsPropertiesOn" %>
<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title>Clinic</title>
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
    <form action="<%= request.getContextPath() %>/admin/ManageSites.do">
        <table class="MainTable">
            <tr class="MainTableTopRow">
                <td class="MainTableTopRowLeftColumn">admin</td>
                <td class="MainTableTopRowRightColumn">
                    <table class="TopStatusBar" style="width: 100%;">
                        <tr>
                            <td>Add New Satellite Site</td>
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

                    <table>
                        <tr>
                            <td>Site Name:<sup style="color:red">*</sup></td>
                            <td><input type="text" name="site.name" maxlength="30" /></td>
                        </tr>
                        <tr>
                            <td>Short Name:<sup style="color:red">*</sup></td>
                            <td><input type="text" name="site.shortName" maxlength="10" /></td>
                        </tr>
                        <tr>
                            <td>Theme Color:<sup style="color:red">*</sup></td>
                            <td><input type="text" id="colorField" name="site.bgColor" type="color"
                                             onchange="this.style.backgroundColor = this.value;" />
                            </td>
                        </tr>
                        <tr>
                            <td>Active:</td>
                            <td><input type="checkbox" name="site.status" value="1" /></td>
                        </tr>
                        <tr>
                            <td>Telephone:</td>
                            <td><input type="text" name="site.phone" /></td>
                        </tr>
                        <tr>
                            <td>FAX:</td>
                            <td><input type="text" name="site.fax" /></td>
                        </tr>
                        <tr>
                            <td>Address:</td>
                            <td><input type="text" name="site.address" /></td>
                        </tr>
                        <tr>
                            <td>City:</td>
                            <td><input type="text" name="site.city" /></td>
                        </tr>
                        <tr>
                            <td>Province:</td>
                            <td><select name="site.province">
                                <option value="AB">AB-Alberta</option>
                                <option value="BC">BC-British Columbia</option>
                                <option value="MB">MB-Manitoba</option>
                                <option value="NB">NB-New Brunswick</option>
                                <option value="NL">NL-Newfoundland & Labrador</option>
                                <option value="NT">NT-Northwest Territory</option>
                                <option value="NS">NS-Nova Scotia</option>
                                <option value="NU">NU-Nunavut</option>
                                <option value="ON">ON-Ontario</option>
                                <option value="PE">PE-Prince Edward Island</option>
                                <option value="QC">QC-Quebec</option>
                                <option value="SK">SK-Saskatchewan</option>
                                <option value="YT">YT-Yukon</option>
                            </select></td>
                        </tr>
                        <tr>
                            <td>Postal Code:</td>
                            <td><input type="text" name="site.postal" /></td>
                        </tr>
                        <% if (IsPropertiesOn.isProviderFormalizeEnable()) { %>
                        <tr>
                            <td>ProviderID From:</td>
                            <td><input type="text" name="site.providerIdFrom" /></td>
                        </tr>
                        <tr>
                            <td>ProviderID To:</td>
                            <td><input type="text" name="site.providerIdTo" /></td>
                        </tr>
                        <% } %>
                    </table>

                    <input type="hidden" name="site.siteId" />
                    <input type="hidden" name="method" value="save" />
                    <input type="submit" class="button" value="Save" /> <input type="submit" class="button"
                                                                                           onclick="this.form.method.value='view'" value="Cancel" />

                </td>
            </tr>
            <tr>
                <td class="MainTableBottomRowLeftColumn">&nbsp;</td>

                <td class="MainTableBottomRowRightColumn">&nbsp;</td>
            </tr>
        </table>
    </form>


</html>
