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
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ page
        import="ca.openosp.openo.rx.pageUtil.*,ca.openosp.openo.rx.data.*,java.util.*" %>
<%@ page import="ca.openosp.openo.prescript.pageUtil.RxSessionBean" %>
<%@ page import="ca.openosp.openo.prescript.data.RxPharmacyData" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%
    String roleName2$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName2$%>" objectName="_rx" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_rx");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<!DOCTYPE HTML>
<html>
    <head>
        <link rel="stylesheet" type="text/css" href="<%=request.getContextPath()%>/oscarRx/styles.css">
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="ManagePharmacy.title"/></title>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <%
            // Check if RxSessionBean is missing in the session
            if (session.getAttribute("RxSessionBean") == null) {
                response.sendRedirect("error.html");
            } else {
                // Check if RxSessionBean is present but not valid
                RxSessionBean rxBean = (RxSessionBean) session.getAttribute("RxSessionBean");
                if (!rxBean.isValid()) {
                    response.sendRedirect("error.html");
                }
            }
        %>
        <%
            String ID = null;
            if (request.getParameter("ID") != null && request.getParameter("type") != null && request.getParameter("type").equals("Delete")) {
                RxPharmacyData rxp = new RxPharmacyData();
                rxp.deletePharmacy(request.getParameter("ID"));

                response.sendRedirect(request.getContextPath() + "/oscarRx/SelectPharmacy2.jsp");
                return;
            }
            ID = (String) request.getParameter("ID");
        %>
    </head>
    <body topmargin="0" leftmargin="0" vlink="#0000FF">

    <table border="0" cellpadding="0" cellspacing="0"
           style="border-collapse: collapse" bordercolor="#111111" width="100%"
           id="AutoNumber1" height="100%">
        <tr>
            <%@ include file="SideLinksNoEditFavorites.jsp"%><!-- <td></td>Side Bar File --->
            <td width="100%" height="100%"
                valign="top">
                <table cellpadding="0" cellspacing="2"
                       style="border-collapse: collapse" bordercolor="#111111" width="100%"
                       height="100%">
                    <tr>
                        <td width="0%" valign="top">
                            <div class="DivCCBreadCrumbs"><a href="oscarRx/SearchDrug.jsp"> <fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.title"/></a></div>
                        </td>
                    </tr>
                    <!----Start new rows here-->
                    <tr>
                        <td>
                            <div class="DivContentTitle"><fmt:setBundle basename="oscarResources"/><fmt:message key="ManagePharmacy.title"/></div>
                        </td>
                    </tr>
                    <tr>
                        <td>
                            <div class="DivContentSectionHead" style="height:8px; text-indent: 10px">
                                <% if (request.getParameter("ID") == null) { %> <fmt:setBundle basename="oscarResources"/><fmt:message key="ManagePharmacy.subTitle.add"/> <%} else {%> <fmt:setBundle basename="oscarResources"/><fmt:message key="ManagePharmacy.subTitle.update"/> <%}%>
                            </div>
                        </td>
                    </tr>
                    <tr>
                        <td><form action="${pageContext.request.contextPath}/oscarRx/managePharmacy.do" method="post">
                            <table>
                                <tr>
                                    <td>
                                        <%String type = request.getParameter("type"); %>
                                        <input type="hidden" name="pharmacyAction" id="pharmacyAction" value="<%=type%>"/>
                                        <input type="hidden" name="ID" id="ID" value="<%=ID%>"/> <fmt:setBundle basename="oscarResources"/><fmt:message key="ManagePharmacy.txtfld.label.pharmacyName"/> :
                                    </td>
                                    <td><input type="text" name="name" id="name" /></td>
                                </tr>
                                <tr>
                                    <td><fmt:setBundle basename="oscarResources"/><fmt:message key="ManagePharmacy.txtfld.label.address"/>
                                        :
                                    </td>
                                    <td><input type="text" name="address" id="address" /></td>
                                </tr>
                                <tr>
                                    <td><fmt:setBundle basename="oscarResources"/><fmt:message key="ManagePharmacy.txtfld.label.city"/>
                                        :
                                    </td>
                                    <td><input type="text" name="city" id="city" /></td>
                                </tr>
                                <tr>
                                    <td><fmt:setBundle basename="oscarResources"/><fmt:message key="ManagePharmacy.txtfld.label.province"/>
                                        :
                                    </td>
                                    <td><input type="text" name="province" id="province" /></td>
                                </tr>
                                <tr>
                                    <td><fmt:setBundle basename="oscarResources"/><fmt:message key="ManagePharmacy.txtfld.label.postalCode"/> :
                                    </td>
                                    <td><input type="text" name="postalCode" id="postalCode" /></td>
                                </tr>
                                <tr>
                                    <td><fmt:setBundle basename="oscarResources"/><fmt:message key="ManagePharmacy.txtfld.label.phone1"/>
                                        :
                                    </td>
                                    <td><input type="text" name="phone1" id="phone1" /></td>
                                </tr>
                                <tr>
                                    <td><fmt:setBundle basename="oscarResources"/><fmt:message key="ManagePharmacy.txtfld.label.phone2"/>
                                        :
                                    </td>
                                    <td><input type="text" name="phone2" id="phone2" /></td>
                                </tr>
                                <tr>
                                    <td><fmt:setBundle basename="oscarResources"/><fmt:message key="ManagePharmacy.txtfld.label.fax"/> :
                                    </td>
                                    <td><input type="text" name="fax" id="fax" /></td>
                                </tr>
                                <tr>
                                    <td><fmt:setBundle basename="oscarResources"/><fmt:message key="ManagePharmacy.txtfld.label.email"/>
                                        :
                                    </td>
                                    <td><input type="text" name="email" id="email" /></td>
                                </tr>
                                <tr>
                                    <td colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="ManagePharmacy.txtfld.label.notes"/> :
                                    </td>
                                </tr>
                                <tr>
                                    <td>&nbsp;</td>
                                    <td><textarea name="notes"></textarea></td>
                                </tr>

                                <tr>
                                    <td><input type="submit"
                                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="ManagePharmacy.submitBtn.label.submit"/>"/>
                                    </td>
                                </tr>
                            </table>
                        </form></td>
                    </tr>

                    <tr>
                        <td>
                            <%
                                String sBack = request.getContextPath() + "/oscarRx/SearchDrug.jsp";
                            %> <input type=button class="ControlPushButton"
                                      onclick="javascript:window.location.href='<%=sBack%>';"
                                      value="Back to Search Drug"/></td>
                    </tr>
                    <!----End new rows here-->
                    <tr height="100%">
                        <td></td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td height="0%"
                style="border-bottom: 2px solid #A9A9A9; border-top: 2px solid #A9A9A9;"></td>
            <td height="0%"
                style="border-bottom: 2px solid #A9A9A9; border-top: 2px solid #A9A9A9;"></td>
        </tr>
        <tr>
            <td width="100%" height="0%" colspan="2">&nbsp;</td>
        </tr>
        <tr>
            <td width="100%" height="0%" style="padding: 5" bgcolor="#DCDCDC"
                colspan="2"></td>
        </tr>
    </table>

    </body>

</html>
