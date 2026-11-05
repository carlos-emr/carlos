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
		<th title="Service Restrictions">Service Restrictions</th>
	</tr>
</table>
</div>
<c:if test="${not empty service_restrictions}">
<table class="carlos-table-spaced simple" id="restriction" data-carlos-table data-page-size="0" data-default-sort="0" data-default-order="ascending">
<thead><tr>


    <th scope="col"><span data-table-sort>Id</span></th>
    <th scope="col"><span data-table-sort>Client</span></th>
    <th scope="col"><span data-table-sort>Restricted By</span></th>
    <th scope="col"><span data-table-sort>Comments</span></th>
    <th scope="col"><span data-table-sort>Start date</span></th>
    <th scope="col"><span data-table-sort>End date</span></th>
</tr></thead>
<tbody><c:forEach var="restriction" items="${service_restrictions}" varStatus="tableRowStatus"><tr class="${tableRowStatus.count % 2 == 0 ? 'even' : 'odd'}">


    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(restriction.id))}" data-sort-type="${carlos:tableSortType(restriction.id)}">${carlos:forHtml(restriction.id)}</td>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(restriction.client.formattedName))}" data-sort-type="${carlos:tableSortType(restriction.client.formattedName)}">${carlos:forHtml(restriction.client.formattedName)}</td>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(restriction.provider.formattedName))}" data-sort-type="${carlos:tableSortType(restriction.provider.formattedName)}">${carlos:forHtml(restriction.provider.formattedName)}</td>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(restriction.comments))}" data-sort-type="${carlos:tableSortType(restriction.comments)}">${carlos:forHtml(restriction.comments)}</td>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(restriction.startDate))}" data-sort-type="${carlos:tableSortType(restriction.startDate)}">${carlos:forHtml(restriction.startDate)}</td>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(restriction.endDate))}" data-sort-type="${carlos:tableSortType(restriction.endDate)}">${carlos:forHtml(restriction.endDate)}</td>
</tr></c:forEach></tbody>
</table>
</c:if>
<c:if test="${empty service_restrictions}"><p class="table-empty">No service restrictions currently in place for this program.</p></c:if>
