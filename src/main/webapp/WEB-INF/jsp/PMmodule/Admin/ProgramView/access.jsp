<%@ taglib uri="carlos" prefix="carlos" %>
<!-- 
/*
* 
* Copyright (c) 2001-2002. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved. *
* This software is published under the GPL GNU General Public License. 
* This program is free software; you can redistribute it and/or 
* modify it under the terms of the GNU General Public License 
* as published by the Free Software Foundation; either version 2 
* of the License, or (at your option) any later version. * 
* This program is distributed in the hope that it will be useful, 
* but WITHOUT ANY WARRANTY; without even the implied warranty of 
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the 
* GNU General Public License for more details. * * You should have received a copy of the GNU General Public License 
* along with this program; if not, write to the Free Software 
* Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA. * 
* 
* <OSCAR TEAM>
* 
* This software was written for 
* Centre for Research on Inner City Health, St. Michael's Hospital, 
* Toronto, Ontario, Canada 
*/
 -->

<%@ include file="/taglibs.jsp"%>
<fmt:setBundle basename="oscarResources"/>
<div class="tabs" id="tabs">
<table cellpadding="3" cellspacing="0" border="0">
	<tr>
		<fmt:message key="pmmodule.admin.programView.access.titlePrograms" var="titlePrograms"/>
<th title="${carlos:forHtmlAttribute(titlePrograms)}"><fmt:message key="pmmodule.admin.programView.access.thAccess"/></th>
	</tr>
</table>
</div>
<display:table class="simple" cellspacing="2" cellpadding="3"
	id="access" name="accesses" export="false" pagesize="0"
	requestURI="/PMmodule/ProgramManagerView">
	<display:setProperty name="paging.banner.placement" value="bottom" />
	<fmt:message key="pmmodule.admin.programView.access.emptyList" var="emptyListMsg"/>
<display:setProperty name="basic.msg.empty_list" value="${emptyListMsg}"/>
	<fmt:message key="pmmodule.admin.programView.access.titleName" var="titleName"/>
<display:column property="accessType.name" sortable="true" title="${carlos:forHtmlAttribute(titleName)}" />
	<fmt:message key="pmmodule.admin.programView.access.titleType" var="titleType"/>
<display:column property="accessType.type" sortable="true" title="${carlos:forHtmlAttribute(titleType)}" />
	<fmt:message key="pmmodule.admin.programView.access.titleAllRoles" var="titleAllRoles"/>
<display:column property="allRoles" sortable="true" title="${carlos:forHtmlAttribute(titleAllRoles)}" />
	<fmt:message key="pmmodule.admin.programView.access.titleRoles" var="titleRoles"/>
<display:column sortable="true" title="${carlos:forHtmlAttribute(titleRoles)}">
		<ul>
			<c:forEach var="role" items="${access.roles}">
				<li>${carlos:forHtml(role.name)}</li>
			</c:forEach>
		</ul>
	</display:column>
</display:table>
