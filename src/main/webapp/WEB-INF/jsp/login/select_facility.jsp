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
<%@page import="io.github.carlos_emr.carlos.commn.dao.FacilityDao" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Facility" %>
<%@page import="java.util.List" %>
<%@page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@include file="/WEB-INF/jsp/layouts/caisi_html_top.jspf" %>
<%@ page import="io.github.carlos_emr.carlos.login.Login2Action" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ taglib uri="https://owasp.org/www-project-csrfguard/Owasp.CsrfGuard.tld" prefix="csrf" %>
<h2>Please select which facility you would like to currently work in</h2>
<%
    FacilityDao facilityDao = (FacilityDao) SpringUtils.getBean(FacilityDao.class);
    ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);

    Provider provider = (Provider) session.getAttribute("provider");
    List<Integer> facilityIds = providerDao.getFacilityIds(provider.getProviderNo());

    // Validate nextPage to prevent open redirect (CWE-601): allowlist of valid Struts2 result identifiers
    java.util.Set<String> allowedNextPages = java.util.Set.of("provider", "caisiPMM", "programLocation", "failure");
    String rawNextPage = request.getParameter("nextPage");
    String safeNextPage = (rawNextPage != null && allowedNextPages.contains(rawNextPage)) ? rawNextPage : "";
    if (rawNextPage != null && safeNextPage.isEmpty()) {
        org.apache.logging.log4j.LogManager.getLogger("select_facility")
            .warn("Rejected nextPage parameter: {}", io.github.carlos_emr.carlos.utility.LogSafe.sanitize(rawNextPage)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
    }
%>
<ul>
    <%
        for (Integer facilityId : facilityIds) {
            Facility facility = facilityDao.find(facilityId);
    %>
    <li>
        <form method="post" action="${pageContext.request.contextPath}/select_facility">
            <input type="hidden" name="<csrf:tokenname/>" value="<csrf:tokenvalue/>"/>
            <input type="hidden" name="nextPage" value="<carlos:encode value='<%= safeNextPage %>' context="htmlAttribute"/>"/>
            <input type="hidden" name="<%=Login2Action.SELECTED_FACILITY_ID%>" value="<%=facility.getId()%>"/>
            <button type="submit"><carlos:encode value='<%= facility.getName() %>' context="html"/></button>
        </form>
    </li>
    <%
        }
    %>
</ul>

<%@include file="/WEB-INF/jsp/layouts/caisi_html_bottom.jspf" %>
