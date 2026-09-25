<%--

    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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

    CARLOS EMR Project
    https://github.com/carlos-emr/carlos

--%>
<%--
    eChart Display Settings

    System-wide (not per-provider) toggles affecting what renders in the eChart.
    Currently: "Display OCEAN UI" (SystemPreferences key echart_show_ocean, matching
    OSCAR19's naming and default-on-when-unset behavior) — shows/hides the
    #ocean_placeholder div in newCaseManagementView.jsp (a static sibling right after
    #notCPP, at the bottom of the encounter, matching OSCAR19's own layout) that the
    Ocean Toolbar (CognisantMD eForms/eReferral integration) attaches itself to. This
    is independent of cme_js=ocean in carlos.properties, which controls whether the
    CME script tag loads at all (see newEncounterLayout.jsp / CustomInterfaceTag);
    this toggle only controls the placeholder the loaded script looks for, letting an
    admin turn the toolbar off at runtime without touching server config or
    restarting Tomcat. ocean_host ships preconfigured in carlos.properties, matching
    OSCAR19's own default oscar_mcmaster.properties.

    Gated on the general "_admin" privilege (see EchartDisplaySettings2Action for why
    "_admin.encounter" is not used).

    @since 2026-09-18
--%>
<!DOCTYPE HTML>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = session.getAttribute("userrole") + "," + session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_admin");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.SystemPreferences" %>
<fmt:setBundle basename="oscarResources"/>

<html lang="${pageContext.response.locale.language}">
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <title><fmt:message key="admin.echartDisplaySettings.title"/></title>
        <link href="<%=request.getContextPath() %>/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet" type="text/css">
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <script type="text/javascript" src="<%=request.getContextPath() %>/library/jquery/jquery-3.7.1.min.js"></script>
        <script src="<%=request.getContextPath() %>/library/jquery/jquery-compat.js"></script>
        <script type="text/javascript" src="<%=request.getContextPath() %>/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>
    </head>

    <body class="BodyStyle">

    <h4><fmt:message key="admin.echartDisplaySettings.heading"/></h4>

    <form name="echartDisplaySettingsForm" method="post" action="${pageContext.request.contextPath}/admin/EchartDisplaySettings">
        <input type="hidden" name="dboperation" value="">

        <div class="card" style="max-width: 640px;">
            <div class="card-body">
                <div class="form-check form-switch mb-2">
                    <input class="form-check-input" type="checkbox" id="echart_show_ocean"
                           name="<%=SystemPreferences.ECHART_PREFERENCE_KEYS.echart_show_ocean.name()%>"
                           value="true" ${displayOceanUI ? "checked" : ""} />
                    <label class="form-check-label" for="echart_show_ocean">
                        <fmt:message key="admin.echartDisplaySettings.displayOceanUI"/>
                    </label>
                </div>
                <div class="form-text text-muted">
                    <fmt:message key="admin.echartDisplaySettings.displayOceanUIHelp"/>
                </div>
            </div>
        </div>

        <p></p>
        <input type="button"
               onclick="document.forms['echartDisplaySettingsForm'].dboperation.value='Save'; document.forms['echartDisplaySettingsForm'].submit();"
               name="saveEchartDisplaySettings" value="<fmt:message key='global.save'/>"/>
        <c:if test="${saved}">
            <span style="color:green;"><fmt:message key="admin.echartDisplaySettings.saved"/></span>
        </c:if>
    </form>
    </body>
</html>
