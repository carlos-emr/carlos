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
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<%@ page import="ca.openosp.openo.rx.data.*,java.util.*" %>
<%@ page import="ca.openosp.openo.prescript.pageUtil.RxSessionBean" %>
<%@ page import="ca.openosp.openo.prescript.data.RxPatientData" %>
<%@ page import="ca.openosp.openo.prescript.data.RxPharmacyData" %>
<%@ page import="ca.openosp.openo.commn.model.PharmacyInfo" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    RxSessionBean bean = null;
    RxPatientData.Patient patient = null;
    String roleName2$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
    String surname = "", firstName = "";
%>
<security:oscarSec roleName="<%=roleName2$%>" objectName="_rx" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_rx");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="SelectPharmacy.title"/></title>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">

        <c:if test="${empty RxSessionBean}">
            <c:redirect url="error.html"/>
        </c:if>
        <c:if test="${not empty sessionScope.RxSessionBean}">
    <%
        // Directly access the RxSessionBean from the session
        bean = (RxSessionBean) session.getAttribute("RxSessionBean");
        if (bean != null && !bean.isValid()) {
            response.sendRedirect("error.html");
            return; // Ensure no further JSP processing
        }
        patient = (RxPatientData.Patient) session.getAttribute("Patient");
        if (patient != null) {
            surname = patient.getSurname();
            firstName = patient.getFirstName();
        }
    %>
        </c:if>
        <link rel="stylesheet" type="text/css" href="oscarRx/styles.css">
    </head>
    <body topmargin="0" leftmargin="0" vlink="#0000FF">

    <table border="0" cellpadding="0" cellspacing="0"
           style="border-collapse: collapse" bordercolor="#111111" width="100%"
           id="AutoNumber1" height="100%">
        <%@ include file="TopLinks.jsp"%><!-- Row One included here-->
        <tr>
            <%@ include file="SideLinksNoEditFavorites.jsp"%><!-- <td></td>Side Bar File --->
            <td width="100%" style="border-left: 2px solid #A9A9A9;" height="100%"
                valign="top">
                <table cellpadding="0" cellspacing="2"
                       style="border-collapse: collapse" bordercolor="#111111" width="100%"
                       height="100%">
                    <tr>
                        <td width="0%" valign="top">
                            <div class="DivCCBreadCrumbs"><a href="oscarRx/SearchDrug.jsp"> <fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.title"/></a> > <fmt:setBundle basename="oscarResources"/><fmt:message key="SelectPharmacy.title"/></div>
                        </td>
                    </tr>
                    <!----Start new rows here-->
                    <tr>
                        <td>
                            <div class="DivContentTitle"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.nameText"/></b>
                                <%=surname%>
                                ,
                                <%=firstName%>
                            </div>
                            <br/>
                            &nbsp; <fmt:setBundle basename="oscarResources"/><fmt:message key="SelectPharmacy.instructions"/></td>
                    </tr>
                    <tr>
                        <td>
                            <div class="DivContentSectionHead"><a
                                    href="<%= request.getContextPath() %>/oscarRx/ManagePharmacy.jsp?type=Add"><fmt:setBundle basename="oscarResources"/><fmt:message key="SelectPharmacy.addLink"/></a></div>
                        </td>
                    </tr>
                    <tr>
                        <td>
                            <% RxPharmacyData pharmacy = new RxPharmacyData();
                                List<PharmacyInfo> pharList = pharmacy.getAllPharmacies();
                            %>

                            <div style=" width:860px; height:460px; overflow:auto;">
                                <table>
                                    <tr>
                                        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="SelectPharmacy.table.pharmacyName"/></td>
                                        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="SelectPharmacy.table.address"/></td>
                                        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="SelectPharmacy.table.city"/></td>
                                        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="SelectPharmacy.table.phone"/></td>
                                        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="SelectPharmacy.table.fax"/></td>
                                        <td>&nbsp;</td>
                                        <td>&nbsp;</td>
                                    </tr>
                                    <% for (int i = 0; i < pharList.size(); i++) {
                                        PharmacyInfo ph = pharList.get(i);
                                    %>
                                    <tr>
                                        <td><a
                                                href="<%= request.getContextPath() %>/oscarRx/LinkPharmacy.do?ID=<%=ph.getId()%>&DemoId=<%=patient.getDemographicNo()%>"><%=ph.getName()%>
                                        </a></td>
                                        <td><%=ph.getAddress()%>
                                        </td>
                                        <td><%=ph.getCity()%>
                                        </td>
                                        <td><%=ph.getPhone1()%>
                                        </td>
                                        <td><%=ph.getFax()%>
                                        </td>
                                        <td><a href="<%= request.getContextPath() %>/oscarRx/ManagePharmacy.jsp?type=Edit&ID=<%=ph.getId()%>"><fmt:setBundle basename="oscarResources"/><fmt:message key="SelectPharmacy.editLink"/></a></td>
                                        <td><a href="<%= request.getContextPath() %>/oscarRx/ManagePharmacy.jsp?type=Delete&ID=<%=ph.getId()%>"><fmt:setBundle basename="oscarResources"/><fmt:message key="SelectPharmacy.deleteLink"/></a></td>
                                    </tr>
                                    <% } %>
                                </table>
                            </div>
                        </td>
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
