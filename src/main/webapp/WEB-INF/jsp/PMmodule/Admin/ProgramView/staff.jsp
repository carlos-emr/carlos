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
<style>
.non_sorted_header {
	color: black;
	background: white;
}
</style>
<div class="tabs" id="tabs">
<table cellpadding="3" cellspacing="0" border="0">
	<tr>
		<th title="Programs">Providers</th>
	</tr>
</table>
</div>
<c:if test="${not empty providers}">
<table class="carlos-table-spaced simple" id="provider" data-carlos-table data-page-size="0" data-default-sort="3" data-default-order="ascending">
<thead><tr>

    <th scope="col" class="non_sorted_header">Team(s)</th>
    <th scope="col"><span data-table-sort>Name</span></th>
    <th scope="col"><span data-table-sort>Phone</span></th>
    <th scope="col"><span data-table-sort>Role</span></th>
</tr></thead>
<tbody><c:forEach var="provider" items="${providers}" varStatus="tableRowStatus"><tr class="${tableRowStatus.count % 2 == 0 ? 'even' : 'odd'}">

    <td>
		<table width="100%" cellspacing="2" cellpadding="2">
			<c:forEach var="team" items="${provider.teams}">
				<tr>
					<td>${carlos:forHtml(team.name)}</td>
				</tr>
			</c:forEach>
		</table>
    </td>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(provider.provider.formattedName))}" data-sort-type="${carlos:tableSortType(provider.provider.formattedName)}">${carlos:forHtml(provider.provider.formattedName)}</td>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(provider.provider.phone))}" data-sort-type="${carlos:tableSortType(provider.provider.phone)}">${carlos:forHtml(provider.provider.phone)}</td>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(provider.role.name))}" data-sort-type="${carlos:tableSortType(provider.role.name)}">${carlos:forHtml(provider.role.name)}</td>
</tr></c:forEach></tbody>
</table>
</c:if>
<c:if test="${empty providers}"><p class="table-empty">Nothing found to display.</p></c:if>
