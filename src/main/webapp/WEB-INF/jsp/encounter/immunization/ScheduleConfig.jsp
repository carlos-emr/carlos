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


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_eChart" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_eChart");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>


<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.immunization.data.*, io.github.carlos_emr.carlos.util.*, io.github.carlos_emr.carlos.demographic.data.*" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.immunization.pageUtil.*, java.util.*, org.w3c.dom.*" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.data.DemographicData" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.pageUtil.EctSessionBean" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.immunization.data.EctImmConfigData" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<fmt:setBundle basename="oscarResources"/>


<link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/css/encounterStyles.css">
<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <title><fmt:message key="encounter.immunization.ScheduleConfig.title"/></title>
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
            <fmt:message key="encounter.immunization.ScheduleConfig.msgImm"/>
        </td>
        <td class="MainTableTopRowRightColumn">
            <table class="TopStatusBar">
                <tr>
                    <td class="Header"
                        style="padding-left:2px;padding-right:2px;border-right:2px solid #003399;text-align:left;font-size:80%;font-weight:bold;width:100%;"
                        NOWRAP>
                        <carlos:encode value='<%= last_name %>' context="html"/>, <carlos:encode value='<%= first_name %>' context="html"/> <carlos:encode value='<%= sex %>' context="html"/> <carlos:encode value='<%= age %>' context="html"/>
                    </td>
                    <td>
                    </td>
                    <td style="text-align:right" NOWRAP>
                        <a href="javascript:history.go(-1);"><fmt:message key="global.btnBack"/></a> | <a
                            href="javascript:window.close();"><fmt:message key="global.btnClose"/></a> |
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
            <form action="${pageContext.request.contextPath}/encounter/immunization/saveConfig" method="post">
                <input type="hidden" name="demographic_no" value="<carlos:encode value='<%= demoNo %>' context="htmlAttribute"/>">
                <input type="hidden" name="xmlDoc" value="<%--= UtilMisc.encode64(UtilXML.toXML(cfgDoc)) --%>"/>

                <%
                    //for(int i=0; i<cfgSets.getLength(); i++) {    Element cfgSet = (Element)cfgSets.item(i);
                    for (int i = 0; i < cfgSet.size(); i++) {
// cfgSet.getAttribute("name")
                %>
                <div style="font-weight: bold"><input type="checkbox"
                                                      name="chkSet<%--=i--%>"
                                                      value="<carlos:encode value='<%= String.valueOf(cfgId.get(i)) %>' context="htmlAttribute"/>"/> <carlos:encode value='<%= (String) cfgSet.get(i) %>' context="html"/>;
                </div>
                <%
                    }
                %>
                <br>
                <table width="80%">
                    <tr>
                        <td>
                            <input type="submit" name="submit"
                                    value="<fmt:message key="encounter.immunization.ScheduleConfig.addTemplate"/>" />
                                         <c:set var="__enc_1"><carlos:encode value='<%= demoNo %>' context="uriComponent"/></c:set>
                      <input type="button" value='<fmt:message key="global.btnCancel"/>'
                                   onclick="javascript:location.href='loadSchedule?demographic_no=<carlos:encode value='${__enc_1}' context="javaScriptAttribute"/>';"/>
                        </td>
                        <td align="right">
                            <input type="button"
                                   value='<fmt:message key="encounter.immunization.ScheduleConfig.createTemplate"/>'
                                   onclick="javascript:location.href='config/initConfig';"/>
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
