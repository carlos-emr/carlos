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
    Modifications by CARLOS Contributors, 2026.

--%>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="jakarta.tags.core" prefix="core" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>

<core:set var="ctx" value="${ pageContext.servletContext.contextPath }"/>
<script src="${pageContext.request.contextPath}/share/javascript/rx-patient-context.js"
        data-demographic-no="<%= io.github.carlos_emr.carlos.prescript.pageUtil.RxSessionBeanResolver.requestedDemographicNo(request) > 0 ? String.valueOf(io.github.carlos_emr.carlos.prescript.pageUtil.RxSessionBeanResolver.requestedDemographicNo(request)) : "" %>"
        data-chart-context="<%= io.github.carlos_emr.carlos.utility.SafeEncode.forHtmlAttribute(io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext.token(request)) %>"></script>
<core:url var="url" value="/demographic/DemographicEdit">
    <core:param name="demographic_no" value="${param.demographicNo}"/>
    <core:param name="appointment" value=""/>
</core:url>

<table role="presentation" id="${carlos:forHtmlAttribute(not empty param.tableId ? param.tableId : 'topLink')}">
    <tr>
        <td id="topLinkLeftColumn">
            <h1><core:out value="${ param.title }"/></h1>
        </td>

        <td id="topLinkCenterColumn">

            <core:if test="${ not empty param.patientName }">
                <a href="${carlos:forHtmlAttribute(url)}" onclick="popupPage(700,1000,this.href); return false;"
                   title="<fmt:message key="provider.appointmentProviderAdminDay.msgMasterFile"/>">
                    <core:out value="${ param.patientName }"/>
                </a>
            </core:if>

            <core:if test="${ not empty param.sex }">
	        <span class="label">
	        	sex
	        </span>
                <span>
                        ${ param.sex }
                </span>
            </core:if>

            <core:if test="${ not empty param.age }">
	        <span class="label">
	        	age
	        </span>
                <span>
                        ${ param.age }
                </span>
            </core:if>

            <core:if test="${ not empty param.phone }">
	        <span class="label">
	        	home
	        </span>
                <span>
                        ${ param.phone }
                </span>
            </core:if>

            <core:if test="${ not empty param.mrp }">
                <security:oscarSec roleName="${ security }" objectName="_newCasemgmt.doctorName" rights="r">
	    	<span class="label">	
	    		  <fmt:message key="encounter.Index.msgMRP"/>  			   
		    </span>
                    <span>
		     	<core:out value="${ param.mrp }"/>
		    </span>
                </security:oscarSec>
            </core:if>
        </td>

        <td id="topLinkRightColumn">
	 		<span class="HelpAboutLogout" style="color:white;">

                 <a style="color:white;" href="${ ctx }/encounter/ViewAbout" target="_new">About</a>
             </span>
        </td>
    </tr>
</table>
