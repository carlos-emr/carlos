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
<script>
	function deleteAccess(id) {
		if(!confirm("Are you sure you want to delete the access entry?")) {
			return;
		}
		document.programManagerForm.elements['access.id'].value=id;
		document.programManagerForm.elements['method'].value='delete_access';
		document.programManagerForm.submit();
	}
</script>
<div class="tabs">
<table cellpadding="3" cellspacing="0" border="0">
	<tr>
		<th title="Programs">Access</th>
	</tr>
</table>
</div>
<c:if test="${not empty accesses}">
<table class="carlos-table-spaced simple" id="access" data-carlos-table data-page-size="0" data-default-sort="0" data-default-order="ascending">
<thead><tr>


    <th scope="col"></th>
    <th scope="col"><span data-table-sort>Name</span></th>
    <th scope="col"><span data-table-sort>Type</span></th>
    <th scope="col"><span data-table-sort>All Roles</span></th>
    <th scope="col"><span data-table-sort>Role(s)</span></th>
</tr></thead>
<tbody><c:forEach var="access" items="${accesses}" varStatus="tableRowStatus"><tr class="${tableRowStatus.count % 2 == 0 ? 'even' : 'odd'}">


    <td>
		<a href="javascript:void(0);"
			onclick="deleteAccess('${carlos:forJavaScript(access.id)}');return false;">
		Delete </a>
    </td>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(access.accessType.name))}" data-sort-type="${carlos:tableSortType(access.accessType.name)}">${carlos:forHtml(access.accessType.name)}</td>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(access.accessType.type))}" data-sort-type="${carlos:tableSortType(access.accessType.type)}">${carlos:forHtml(access.accessType.type)}</td>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(access.allRoles))}" data-sort-type="${carlos:tableSortType(access.allRoles)}">${carlos:forHtml(access.allRoles)}</td>
    <td>
		<ul>
			<c:forEach var="role" items="${access.roles}">
				<li>${carlos:forHtml(role.name)}</li>
			</c:forEach>
		</ul>
    </td>
</tr></c:forEach></tbody>
</table>
</c:if>
<c:if test="${empty accesses}">No access currently defined for this program.</c:if>
<br />
<br />
<table width="100%" border="1" cellspacing="2" cellpadding="3">
	<tr class="b">
		<td width="20%">Name :</td>
		<td><html:select property="access.accessTypeId">
			<html:options collection="accessTypes" property="id"
				labelProperty="name" />
		</html:select> <html:hidden property="access.id" /> <html:hidden
			property="access.programId" /></td>
	</tr>
	<tr class="b">
		<td width="20%">All Roles:</td>
		<td><html:checkbox property="access.allRoles" /></td>
	</tr>
	<tr class="b">
		<td width="20%">Roles:</td>
		<td><c:forEach var="role" items="${roles}">
			<input name="checked_role" value="${carlos:forHtmlAttribute(role.id)}"
				type="checkbox" />&nbsp;${carlos:forHtml(role.name)}
			<br />
		</c:forEach></td>
	</tr>
	<tr>
		<td colspan="2"><input type="button" value="Save"
			onclick="this.form.elements['method'].value='save_access';this.form.submit()" />
		<html:cancel /></td>
	</tr>
</table>
