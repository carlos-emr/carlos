<%--


    Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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

    This software was written for
    Centre for Research on Inner City Health, St. Michael's Hospital,
    Toronto, Ontario, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>


<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo, io.github.carlos_emr.carlos.commn.model.Facility" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.OscarProperties" %>
<%@ page import="io.github.carlos_emr.carlos.managers.DemographicManager" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.pageUtil.EctSessionBean" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Provider" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

    EctSessionBean bean = null;
    if ((bean = (EctSessionBean) request.getSession().getAttribute("EctSessionBean")) == null) {
        response.sendRedirect("error.jsp");
        return;
    }

    Facility facility = loggedInInfo.getCurrentFacility();
    String demoNo = bean.demographicNo;
    DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
    Demographic demographic = demographicManager.getDemographicWithExt(loggedInInfo, Integer.parseInt(demoNo));

    // this is accessed in the newEncounterLayout after this header is included.
    String privateConsentEnabledProperty = OscarProperties.getInstance().getProperty("privateConsentEnabled");
    boolean privateConsentEnabled = privateConsentEnabledProperty != null && privateConsentEnabledProperty.equals("true");

%>

<c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>

<script type="text/javascript">
function copyToClip(text, el) {
    var orig = el.title;
    function showFeedback() {
        el.title = 'Copied!';
        el.style.opacity = '0.5';
        setTimeout(function() { el.style.opacity = '1'; el.title = orig; }, 600);
    }
    if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(showFeedback).catch(function() {
            fallbackCopy(text);
            showFeedback();
        });
    } else {
        fallbackCopy(text);
        showFeedback();
    }
}
function fallbackCopy(text) {
    var ta = document.createElement('textarea');
    ta.value = text;
    ta.style.position = 'fixed';
    ta.style.left = '-9999px';
    document.body.appendChild(ta);
    ta.select();
    document.execCommand('copy');
    document.body.removeChild(ta);
}
</script>

<div id="header-top-row">
    <div id="left-column">
        <div id="branding-logo">
            <img alt="OSCAR EMR" src="<%=request.getContextPath()%>/images/oscar_logo_small.png" width="19px">
        </div>
        <%= demographic.getStandardIdentificationHTML(request.getContextPath()) %>
    </div>
    <div id="right-column">
    </div>
</div>

<div id="header-bottom-row">
    <% if (OscarProperties.getInstance().hasProperty("ONTARIO_MD_INCOMINGREQUESTOR")) {%>
    <div>
        <a href="javascript:void(0);" onClick="popupPage(600,175,'Calculators','<c:out
                value="${ctx}"/>/commons/omdDiseaseList.jsp?sex=<%=bean.patientSex%>&age=<%=demographic.getAge()%>'); return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Header.OntMD"/></a>
    </div>
    <%}%>

    <div>
        <%=getEChartLinks() %>
    </div>

</div>

<%!
    String getEChartLinks() {
        String str = OscarProperties.getInstance().getProperty("ECHART_LINK");
        if (str == null) {
            return "";
        }
        try {
            String[] httpLink = str.split("\\|");
            return "<a target=\"_blank\" href=\"" + httpLink[1] + "\">" + httpLink[0] + "</a>";
        } catch (Exception e) {
            MiscUtils.getLogger().error("ECHART_LINK is not in the correct format. title|url :" + str, e);
        }
        return "";
    }
%>
