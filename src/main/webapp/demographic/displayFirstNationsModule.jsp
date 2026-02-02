<%--

    Copyright 2015. Trimara Corporation. All Rights Reserved.
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
<security:oscarSec roleName="<%=roleName$%>" objectName="_demographic" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_demographic");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page import="java.util.*" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.DemographicExtDao" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<%
    String demographic_no = request.getParameter("demo");
    DemographicExtDao demographicExtDao = SpringUtils.getBean(DemographicExtDao.class);
    Map<String, String> demoExt = demographicExtDao.getAllValuesForDemo(Integer.valueOf(demographic_no));

//Creates a hashmap to easily determine description of the ethnicity id in the demoExt map
    Map<String, String> ethnicityMap = new HashMap<String, String>();
    ethnicityMap.put("-1", "Not Set");
    ethnicityMap.put("1", "On-reserve");
    ethnicityMap.put("2", "Off-reserve");
    ethnicityMap.put("3", "Non-status On-reserve");
    ethnicityMap.put("4", "Non-status Off-reserve");
    ethnicityMap.put("5", "Metis");
    ethnicityMap.put("6", "Inuit");
    ethnicityMap.put("11", "Homeless");
    ethnicityMap.put("12", "Out of Country Residents");
    ethnicityMap.put("13", "Other");

    pageContext.setAttribute("demoExt", demoExt);
    pageContext.setAttribute("ethnicityMap", ethnicityMap);

%>
<ul>
    <li><strong>First Nation Identity (INAC)</strong></li>
    <li>
        <span class="label">Status Number:</span>
        <span class="info"><c:out value='${ demoExt["statusNum"] }'/></span>
    </li>
    <li>
        <span class="label">First Nation Community: </span>
        <span class="info">
		<c:out value='${param.fncommunity}'/>
	</span>
    </li>

    <li>
        <span class="label">Family Number: </span>
        <span class="info"><c:out value='${ demoExt["fNationFamilyNumber"] }'/></span>
    </li>

    <li>
        <span class="label">Family Position: </span>
        <span class="info"><c:out value='${ demoExt["fNationFamilyPosition"] }'/></span>
    </li>
    <li>
        <span class="label">First Nation Status: </span>
        <span class="info"><c:out value='${ ethnicityMap[demoExt["ethnicity"]] }'/></span>
    </li>

</ul>
