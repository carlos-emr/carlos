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




<%@include file="/WEB-INF/jsp/casemgmt/taglibs.jsp" %>



<%@page import="java.util.Properties" %>

















<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>



<%@page import="io.github.carlos_emr.CarlosProperties" %>




<%@page import="io.github.carlos_emr.carlos.encounter.display.EchartDisplayPreferenceResolver" %>
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>


<% java.util.Properties oscarVariables = CarlosProperties.getInstance(); %>
<c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>

<div id="cppBoxes">
    <div id="divR1">
        <!-- social history -->
        <div id="divR1I1" class="topBox">
        </div>

        <div id="divR1I2" class="topBox">

        </div>
    </div>

    <div id="divR2">
        <!--Ongoing Concerns cell -->
        <div id="divR2I1" class="topBox">
        </div>
        <!--Reminders cell -->
        <div id="divR2I2" class="topBox">
        </div>
    </div>

</div>

<div id="notCPP">

</div>

<%-- This leaves the OCEAN toolbar accessible but not taking up prime real estate.
     Matches OSCAR19's newCaseManagementView.jsp: a static sibling right after #notCPP, present
     from initial page load (no AJAX-load race). Two switches must both allow it: the clinic-wide
     echart_show_ocean admin toggle (default "on" when unset, same as OSCAR19's
     echartPreferencesMap.getOrDefault("echart_show_ocean", true)), and the provider's own
     "Display OCEAN UI" eChart preference (default "off"; see EchartDisplayPreferenceResolver). --%>
<%
    EchartDisplayPreferenceResolver caseManagementViewEchartResolver = SpringUtils.getBean(EchartDisplayPreferenceResolver.class);
    boolean echartShowOcean = caseManagementViewEchartResolver.isOceanShown(caseManagementViewEchartResolver.resolve(
            LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo()));
    if (echartShowOcean) {
%>
<div id="ocean_placeholder" style="display:none">
</div>
<% } %>
