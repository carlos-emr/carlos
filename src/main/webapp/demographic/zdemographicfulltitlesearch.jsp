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

<%@page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@ page import="java.lang.*" %>
<%@page import="ca.openosp.OscarProperties" %>
<%@page import="org.apache.commons.text.StringEscapeUtils" %>

<%
    boolean fromMessenger = request.getParameter("fromMessenger") == null ? false : (request.getParameter("fromMessenger")).equalsIgnoreCase("true") ? true : false;
    String roleName = session.getAttribute("userrole") + "," + session.getAttribute("user");
%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>

<script type="application/javascript">
    function checkdbstatus() {
        if (document.titlesearch.search_mode.value === 'search_band_number') {
            document.titlesearch.dboperation.value = 'search_status_id';
        } else {
            document.titlesearch.dboperation.value = 'search_titlename';
        }
    }

    function searchInactive() {
        document.titlesearch.ptstatus.value = "inactive";
        if (checkTypeIn()) document.titlesearch.submit();
    }

    function searchAll() {
        document.titlesearch.ptstatus.value = "";
        if (checkTypeIn()) document.titlesearch.submit();
    }

    function searchOutOfDomain() {
        document.titlesearch.outofdomain.value = "true";
        if (checkTypeIn()) document.titlesearch.submit();
    }
    
    function formatDateInput(input) {
        // Remove any non-digit characters
        var value = input.value.replace(/\D/g, '');
        
        // Format as YYYY-MM-DD
        if (value.length > 0) {
            // Ensure we only take the first 8 digits
            value = value.substring(0, Math.min(8, value.length));
            
            // Add hyphens
            if (value.length > 4) {
                value = value.substring(0, 4) + '-' + value.substring(4);
            }
            if (value.length > 7) {
                value = value.substring(0, 7) + '-' + value.substring(7);
            }
        }
        
        input.value = value;
    }
    
    function checkTypeIn() {
        var dob = document.titlesearch.keyword;
        if (document.titlesearch.search_mode.value == "search_dob") {
            // Remove hyphens for validation
            var dobValue = dob.value.replace(/-/g, '');
            
            // Check if we have enough digits
            if (dobValue.length < 8) {
                alert("Date format must be YYYY-MM-DD");
                return false;
            }
        }
        return true;
    }

</script>
<div class="searchBox">
    <form method="get" name="titlesearch" action="<%=request.getContextPath()%>/demographic/demographiccontrol.jsp"
          onsubmit="return checkTypeIn()">

        <% String searchMode = request.getParameter("search_mode");
            String keyWord = request.getParameter("keyword");
            if (searchMode == null || searchMode.equals("")) {
                searchMode = OscarProperties.getInstance().getProperty("default_search_mode", "search_name");
            }
            if (keyWord == null) {
                keyWord = "";
            }
        %>
        <div class="input-group select-group">
            <select class="form-control input-group-addon" name="search_mode" id="search_mode" onchange="if(this.value === 'search_dob') document.titlesearch.keyword.value = '';">
                <option value="search_name" <%=searchMode.equals("search_name") ? "selected" : ""%>>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.zdemographicfulltitlesearch.formName"/>
                </option>
                <option value="search_phone" <%=searchMode.equals("search_phone") ? "selected" : ""%>>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.zdemographicfulltitlesearch.formPhone"/>
                </option>
                <option value="search_dob" <%=searchMode.equals("search_dob") ? "selected" : ""%>>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.zdemographicfulltitlesearch.formDOB"/>
                </option>
                <option value="search_address" <%=searchMode.equals("search_address") ? "selected" : ""%>>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.zdemographicfulltitlesearch.formAddr"/>
                </option>
                <option value="search_hin" <%=searchMode.equals("search_hin") ? "selected" : ""%>>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.zdemographicfulltitlesearch.formHIN"/>
                </option>
                <option value="search_chart_no" <%=searchMode.equals("search_chart_no") ? "selected" : ""%>>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.zdemographicfulltitlesearch.formChart"/>
                </option>
                <option value="search_demographic_no" <%=searchMode.equals("search_demographic_no") ? "selected" : ""%>>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.zdemographicfulltitlesearch.formDemographicNo"/>
                </option>
                <oscar:oscarPropertiesCheck value="true" defaultVal="false" property="FIRST_NATIONS_MODULE">
                    <option value="search_band_number" <%=searchMode.equals("search_band_number") ? "selected" : ""%> >
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.zdemographicfulltitlesearch.formBandNumber"/>
                    </option>
                </oscar:oscarPropertiesCheck>
            </select>

            <input class="wideInput form-control" type="search" placeholder="Search Patient" NAME="keyword"
                   VALUE="<%=StringEscapeUtils.escapeHtml4(keyWord)%>" SIZE="17" MAXLENGTH="100"
                   oninput="if(document.titlesearch.search_mode.value === 'search_dob') formatDateInput(this);"
                   onkeyup="if(document.titlesearch.search_mode.value === 'search_dob') formatDateInput(this);">


            <INPUT TYPE="hidden" NAME="orderby" VALUE="last_name, first_name">
            <INPUT TYPE="hidden" NAME="dboperation" VALUE="search_titlename">
            <INPUT TYPE="hidden" NAME="limit1" VALUE="0">
            <INPUT TYPE="hidden" NAME="limit2" VALUE="10">
            <INPUT TYPE="hidden" NAME="displaymode" VALUE="Search">
            <INPUT TYPE="hidden" NAME="ptstatus" VALUE="active">
            <INPUT TYPE="hidden" NAME="fromMessenger" VALUE="<%=fromMessenger%>">
            <INPUT TYPE="hidden" NAME="outofdomain" VALUE="">
            <div class="input-group-btn">
                <INPUT TYPE="SUBMIT" class="rightButton blueButton top btn btn-primary" VALUE="Active" SIZE="17"
                       TITLE="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.zdemographicfulltitlesearch.tooltips.searchActive"/>">


                <INPUT TYPE="button" class="btn btn-secondary" onclick="searchInactive();"
                       TITLE="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.zdemographicfulltitlesearch.tooltips.searchInactive"/>"
                       VALUE="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.search.Inactive"/>">


                <INPUT TYPE="button" class="btn btn-secondary" onclick="searchAll();"
                       TITLE="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.zdemographicfulltitlesearch.tooltips.searchAll"/>"
                       VALUE="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.search.All"/>">
            </div>
            <%
                LoggedInInfo loggedInInfo2 = LoggedInInfo.getLoggedInInfoFromSession(request);
                if (loggedInInfo2.getCurrentFacility().isIntegratorEnabled()) {
            %>
            <input type="checkbox" class="form-control" name="includeIntegratedResults" id="includeIntegratedResults"
                   value="true"/><label for="includeIntegratedResults">Include Integrator</label>
            <%}%>

            <security:oscarSec roleName="<%=roleName%>" objectName="_search.outofdomain" rights="r">
                <INPUT TYPE="button" onclick="searchOutOfDomain();"
                       TITLE="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.zdemographicfulltitlesearch.tooltips.searchOutOfDomain"/>"
                       VALUE="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.search.OutOfDomain"/>">
            </security:oscarSec>

            <caisi:isModuleLoad moduleName="caisi">
                <input type="button" value="cancel"
                       onclick="location.href='${request.contextPath}/PMmodule/ProviderInfo.do'">
            </caisi:isModuleLoad>
        </div>

    </form>
</div>
