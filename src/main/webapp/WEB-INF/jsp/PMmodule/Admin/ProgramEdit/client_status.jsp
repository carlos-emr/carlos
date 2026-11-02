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
function deleteStatus(id) {
	if(!confirm("Are you sure you want to delete the status entry?")) {
		return;
	}
	document.programManagerForm.elements['client_status.id'].value=id;
	document.programManagerForm.elements['method'].value='delete_status';
	document.programManagerForm.submit();
}

function editStatus(id) {
	document.programManagerForm.elements['client_status.id'].value=id;
	document.programManagerForm.elements['method'].value='edit_status';
	document.programManagerForm.submit();
}

function add_status(form) {
	if (form.elements['client_status.name'].value == '') {
		alert('You must choose a status name');
		return false;
	}
	
	form.elements['client_status.id'].value='0';
	form.elements['method'].value='save_status';
	form.submit();
}
</script>
<div class="tabs">
<table cellpadding="3" cellspacing="0" border="0">
	<tr>
		<th title="Programs">Client Status</th>
	</tr>
</table>
</div>
<!--  show current staff -->
<%@ include file="/WEB-INF/jsp/common/carlosTableAssets.jspf" %>
<table class="simple" cellspacing="2" cellpadding="3" id="status" data-carlos-table data-page-size="0" data-default-sort="0" data-default-order="ascending">
<thead><tr>


    <th scope="col"></th>
    <th scope="col"><span data-table-sort>Name</span></th>
</tr></thead>
<tbody><c:forEach var="status" items="${client_statuses}" varStatus="tableRowStatus"><tr class="${tableRowStatus.count % 2 == 0 ? 'even' : 'odd'}">


    <td>
		<a onclick="deleteStatus('${carlos:forJavaScript(status.id)}');return false;" href="javascript:void(0);"> Delete </a>
    </td>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(status.name))}" data-sort-type="${carlos:tableSortType(status.name)}"><c:out value="${status.name}"/></td>
</tr></c:forEach></tbody>
</table>
<c:if test="${empty client_statuses}"><p class="table-empty">No statuses are currently defined for this program.</p></c:if>
<br />
<html:hidden property="client_status.id" />
<table width="100%" border="1" cellspacing="2" cellpadding="3">
	<tr class="b">
		<td width="20%">Name:</td>
		<td><html:text property="client_status.name" size="50" maxlength="255"/></td>
	</tr>
	<tr>
		<td colspan="2">
			<input type="button" value="Save" onclick="add_status(this.form)" /> <html:cancel />
		</td>
	</tr>
</table>
