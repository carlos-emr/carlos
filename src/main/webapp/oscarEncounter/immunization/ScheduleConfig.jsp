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
<security:oscarSec roleName="<%=roleName$%>" objectName="_eChart" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_eChart");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>


<%@page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@ page import="ca.openosp.openo.encounter.immunization.data.*, ca.openosp.openo.util.*, ca.openosp.openo.demographic.data.*" %>
<%@ page import="ca.openosp.openo.encounter.immunization.pageUtil.*, java.util.*, org.w3c.dom.*" %>
<%@ page import="ca.openosp.openo.demographic.data.DemographicData" %>
<%@ page import="ca.openosp.openo.encounter.pageUtil.EctSessionBean" %>
<%@ page import="ca.openosp.openo.encounter.immunization.data.EctImmConfigData" %>
<%@ page import="ca.openosp.openo.commn.model.Demographic" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/css/encounterStyles.css">
<html>
<head>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <title><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.immunization.ScheduleConfig.title"/></title>
    <%
        EctSessionBean bean = (EctSessionBean) request.getSession().getAttribute("EctSessionBean");

        String demoNo = request.getParameter("demographic_no") == null ? (String) request.getAttribute("demographic_no") : request.getParameter("demographic_no");

        String last_name = "";
        String first_name = "";
        String sex = "";
        String age = "";
        if (demoNo != null) {
            DemographicData dData = new DemographicData();
            Demographic demographic = dData.getDemographic(LoggedInInfo.getLoggedInInfoFromSession(request), demoNo);
            last_name = demographic.getLastName();
            first_name = demographic.getFirstName();
            sex = demographic.getSex();
            age = demographic.getAge();
        } else {
            if (bean.demographicNo != null) {
                demoNo = bean.demographicNo;
                last_name = bean.getPatientLastName();
                first_name = bean.getPatientFirstName();
                sex = bean.getPatientSex();
                age = bean.getPatientAge();
            }
        }
    %>

</head>
<body class="BodyStyle" vlink="#0000FF">
<!--  -->
<table class="MainTable" id="scrollNumber1" name="encounterTable">
    <tr class="MainTableTopRow">
        <td class="MainTableTopRowLeftColumn">
            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.immunization.ScheduleConfig.msgImm"/>
        </td>
        <td class="MainTableTopRowRightColumn">
            <table class="TopStatusBar">
                <tr>
                    <td class="Header"
                        style="padding-left:2px;padding-right:2px;border-right:2px solid #003399;text-align:left;font-size:80%;font-weight:bold;width:100%;"
                        NOWRAP>
                        <%=last_name %>, <%=first_name%> <%=sex%> <%=age%>
                    </td>
                    <td>
                    </td>
                    <td style="text-align:right" NOWRAP>
                        <a href="javascript:history.go(-1);"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnBack"/></a> | <a
                            href="javascript:window.close();"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnClose"/></a> |
                    </td>
                </tr>
            </table>
        </td>
    </tr>
    <tr>
        <td class="MainTableLeftColumn">
        </td>
        <td class="MainTableRightColumn">
            <%--
            String sCfg = new EctImmConfigData().getImmunizationConfig();
            Document cfgDoc = UtilXML.parseXML(sCfg);
            Element cfgRoot = cfgDoc.getDocumentElement();
            NodeList cfgSets = cfgRoot.getElementsByTagName("immunizationSet");
            --%> <%
            Vector cfgSet = new EctImmConfigData().getImmunizationConfigName();
            Vector cfgId = new EctImmConfigData().getImmunizationConfigId();
        %>
            <form action="${pageContext.request.contextPath}/oscarEncounter/immunization/saveConfig.do" method="post">
                <input type="hidden" name="demographic_no" value="<%=demoNo%>">
                <input type="hidden" name="xmlDoc" value="<%--= UtilMisc.encode64(UtilXML.toXML(cfgDoc)) --%>"/>

                <%
                    //for(int i=0; i<cfgSets.getLength(); i++) {    Element cfgSet = (Element)cfgSets.item(i);
                    for (int i = 0; i < cfgSet.size(); i++) {
// cfgSet.getAttribute("name")
                %>
                <div style="font-weight: bold"><input type="checkbox"
                                                      name="chkSet<%--=i--%>"
                                                      value="<%=cfgId.get(i)%>"/> <%=(String) cfgSet.get(i)%>;
                </div>
                <%
                    }
                %>
                <br>
                <table width="80%">
                    <tr>
                        <td>
                            <input type="submit" name="submit"
                                    value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.immunization.ScheduleConfig.addTemplate"/>" />
                            <input type="button" value='<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnCancel"/>'
                                   onclick="javascript:location.href='loadSchedule.do?demographic_no=<%=demoNo%>';"/>
                        </td>
                        <td align="right">
                            <input type="button"
                                   value='<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.immunization.ScheduleConfig.createTemplate"/>'
                                   onclick="javascript:location.href='config/initConfig.do';"/>
                        </td>
                    </tr>
                </table>
            </form></td>
    </tr>
    <tr>
        <td class="MainTableBottomRowLeftColumn"></td>
        <td class="MainTableBottomRowRightColumn"></td>
    </tr>
</table>
</body>
</html>
