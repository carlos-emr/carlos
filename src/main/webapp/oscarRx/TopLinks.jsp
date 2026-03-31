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
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="jakarta.tags.core" prefix="core" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>

<core:set var="ctx" value="${ pageContext.servletContext.contextPath }"/>
<core:set var="url"
       value="${ ctx }/demographic/demographiccontrol.jsp?demographic_no=${ param.demographicNo }&displaymode=edit&dboperation=search_detail&appointment="/>

<table id="${ not empty param.tableId ? param.tableId : 'topLink' }">
    <tr>
        <td id="topLinkLeftColumn">
            <h1><core:out value="${ param.title }"/></h1>
        </td>

        <td id="topLinkCenterColumn">

            <core:if test="${ not empty param.patientName }">
                <a href="javascript:void(0)" onClick="popupPage(700,1000,'${ url }'); return false;"
                   title="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.msgMasterFile"/>">
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
	    		  <fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.Index.msgMRP"/>  			   
		    </span>
                    <span>
		     	<core:out value="${ param.mrp }"/>
		    </span>
                </security:oscarSec>
            </core:if>
        </td>

        <td id="topLinkRightColumn">
	 		<span class="HelpAboutLogout" style="color:white;">

                 <a style="color:white;" href="${ ctx }/encounter/About.jsp" target="_new">About</a>
             </span>
        </td>
    </tr>
</table>