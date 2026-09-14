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
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ taglib uri="jakarta.tags.core" prefix="core" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.functions" prefix="fn" %>
<fmt:setBundle basename="oscarResources"/>

<core:set var="ctx" value="${ pageContext.servletContext.contextPath }"/>
<core:set var="url"
       value="${ ctx }/demographic/DemographicEdit?demographic_no=${ carlos:forUriComponent(param.demographicNo) }&appointment="/>

<%-- tableId is caller-supplied (request parameter) and lands in an HTML id token.
     Two constraints shape this:
     1. CodeQL's malformed-id check reads the JSP source text, not the evaluated
        expression, so the id attribute itself must hold no literal whitespace.
        Hence the value is computed here and the attribute is a bare EL reference.
     2. An EL quoted string may only escape a backslash, an apostrophe or a quote,
        so a regex escape such as the \s in fn:replaceAll fails JSP translation.
        fn:replace with a literal space is the portable form. --%>
<core:set var="topLinkTableId"
       value="${ not empty param.tableId ? fn:replace(param.tableId, ' ', '_') : 'topLink' }"/>

<%-- role="presentation" because this is a layout table: one row of left/centre/right
     banner cells, no tabular data and so no <th> to give it. Marking it keeps screen
     readers from announcing it as a data table (and satisfies Sonar Web:S5256). --%>
<table id="${carlos:forHtmlAttribute(topLinkTableId)}" role="presentation">
    <tr>
        <td id="topLinkLeftColumn">
            <h1>${carlos:forHtmlContent(param.title)}</h1>
        </td>

        <td id="topLinkCenterColumn">

            <core:if test="${ not empty param.patientName }">
                <a href="javascript:void(0)" onClick="popupPage(700,1000,'${carlos:forJavaScriptAttribute(url)}'); return false;"
                   title="<fmt:message key="provider.appointmentProviderAdminDay.msgMasterFile"/>">
                    ${carlos:forHtmlContent(param.patientName)}
                </a>
            </core:if>

            <core:if test="${ not empty param.sex }">
	        <span class="label">
	        	sex
	        </span>
                <span>
                        ${carlos:forHtmlContent(param.sex)}
                </span>
            </core:if>

            <core:if test="${ not empty param.age }">
	        <span class="label">
	        	age
	        </span>
                <span>
                        ${carlos:forHtmlContent(param.age)}
                </span>
            </core:if>

            <core:if test="${ not empty param.phone }">
	        <span class="label">
	        	home
	        </span>
                <span>
                        ${carlos:forHtmlContent(param.phone)}
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