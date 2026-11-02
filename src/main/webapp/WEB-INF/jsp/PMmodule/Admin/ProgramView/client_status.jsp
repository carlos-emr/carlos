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
<div class="tabs" id="tabs">
<table cellpadding="3" cellspacing="0" border="0">
	<tr>
		<th title="Programs">Client Status Management</th>
	</tr>
</table>
</div>
<!--  show current staff -->
<%@ include file="/WEB-INF/jsp/common/carlosTableAssets.jspf" %>
<c:if test="${not empty client_statuses}">
<table class="simple" cellspacing="2" cellpadding="3" id="status" data-carlos-table data-page-size="0" data-default-sort="0" data-default-order="ascending">
<thead><tr>


    <th scope="col"><span data-table-sort>Name</span></th>
</tr></thead>
<tbody><c:forEach var="status" items="${client_statuses}" varStatus="tableRowStatus"><tr class="${tableRowStatus.count % 2 == 0 ? 'even' : 'odd'}">


    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(status.name))}" data-sort-type="${carlos:tableSortType(status.name)}"><c:out value="${status.name}"/></td>
</tr></c:forEach></tbody>
</table>
</c:if>
<c:if test="${empty client_statuses}"><p class="table-empty">No statuses are currently defined for this program.</p></c:if>
