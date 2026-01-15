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

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.consult" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin&type=_admin.consult");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ page import="java.util.*" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.openo.commn.dao.PropertyDao, ca.openosp.openo.commn.model.Property" %>
<%@ page import="ca.openosp.openo.managers.ConsultationManager" %>
<%@ page import="ca.openosp.openo.encounter.oscarConsultationRequest.config.pageUtil.EctConTitlebar" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%
    PropertyDao dao = (PropertyDao) SpringUtils.getBean(PropertyDao.class);
    ConsultationManager manager = (ConsultationManager) SpringUtils.getBean(ConsultationManager.class);

    boolean consultRequestEnabled = false;
    boolean consultResponseEnabled = false;

    List<Property> results = dao.findByName(manager.CON_REQUEST_ENABLED);
    if (results.size() > 0 && manager.ENABLED_YES.equals(results.get(0).getValue())) consultRequestEnabled = true;
    results = dao.findByName(manager.CON_RESPONSE_ENABLED);
    if (results.size() > 0 && manager.ENABLED_YES.equals(results.get(0).getValue())) consultResponseEnabled = true;

    if (!consultRequestEnabled && !consultResponseEnabled) consultRequestEnabled = true;
%>
<html>


    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.config.EnableRequestResponse.title"/>
        </title>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>
    </head>

    <link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/css/encounterStyles.css">
    <body class="BodyStyle" vlink="#0000FF">
    <!--  -->
    <table class="MainTable" id="scrollNumber1" name="encounterTable">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn">Consultation</td>
            <td class="MainTableTopRowRightColumn">
                <table class="TopStatusBar">
                    <tr>
                        <td class="Header"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.config.EnableRequestResponse.title"/>
                        </td>
                        <td></td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr style="vertical-align: top">
            <td class="MainTableLeftColumn">
                <%
                    EctConTitlebar titlebar = new EctConTitlebar(request);
                    out.print(titlebar.estBar(request));
                %>
            </td>
            <td class="MainTableRightColumn">
                <table cellpadding="0" cellspacing="2"
                       style="border-collapse: collapse" bordercolor="#111111" width="100%"
                       height="100%">

                    <tr>
                        <td>&nbsp;</td>
                    </tr>
                    <%
                        String updated = (String) request.getAttribute("ENABLE_REQUEST_RESPONSE_UPDATED");
                        if (updated != null) { %>
                    <tr>
                        <td><font color="red"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.config.EnableRequestResponse.msgUpdated"/></font>
                        </td>
                    </tr>
                    <%}%>
                    <tr>
                        <td>
                            <table>
                                <form action="${pageContext.request.contextPath}/oscarEncounter/EnableConRequestResponse.do" method="post">
                                    <tr>
                                        <td>
                                            <input type="checkbox"
                                                   name="consultRequestEnabled" value="true" <%=consultRequestEnabled ? "checked" : "" %>/>
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.config.EnableRequestResponse.enableRequest"/>
                                            <br/>
                                            <input type="checkbox"
                                                   name="consultResponseEnabled" value="true" <%=consultResponseEnabled ? "checked" : "" %>/>
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.config.EnableRequestResponse.enableResponse"/>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td>&nbsp;</td>
                                    </tr>
                                    <tr>
                                        <td><input type="submit"
                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.config.EnableRequestResponse.btnUpdate"/>"/>
                                        </td>
                                    </tr>
                                </form>
                            </table>
                        </td>
                    </tr>

                    <tr height="100%">
                        <td></td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td class="MainTableBottomRowLeftColumn"></td>
            <td class="MainTableBottomRowRightColumn"></td>
        </tr>
    </table>
    </body>
</html>
