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
.b th {color:black;}
</style>
<script>
	function deleteProvider(id) {
		if(!confirm("Are you sure you want to delete the staff entry?")) {
			return;
		}
		document.programManagerForm.elements['provider.id'].value=id;
		document.programManagerForm.elements['method'].value='delete_provider';
		document.programManagerForm.submit();
	}
	
	function editProvider(id) {
		document.programManagerForm.elements['provider.id'].value=id;
		document.programManagerForm.elements['method'].value='edit_provider';
		document.programManagerForm.submit();
	}

	function assignTeam(id,selectBox) {
		var team_id = selectBox.options[selectBox.selectedIndex].value;
		if(team_id == '') { return;}
		document.programManagerForm.elements['teamId'].value=team_id;	
		document.programManagerForm.elements['provider.id'].value=id;
		document.programManagerForm.elements['method'].value='assign_team';
		document.programManagerForm.submit();
	}
	
	function removeTeam(id,team_id) {
		if(!confirm("Are you sure you want to delete the team entry?")) {
			return;
		}
		document.programManagerForm.elements['teamId'].value=team_id;	
		document.programManagerForm.elements['provider.id'].value=id;
		document.programManagerForm.elements['method'].value='remove_team';
		document.programManagerForm.submit();
	}
	
	function assignRole(id,selectBox) {
		var role_id = selectBox.options[selectBox.selectedIndex].value;
		document.programManagerForm.elements['provider.roleId'].value=role_id;	
		document.programManagerForm.elements['provider.id'].value=id;
		document.programManagerForm.elements['method'].value='assign_role';
		document.programManagerForm.submit();
	}
	
	function add_provider(form) {
		if (!form.elements['provider.providerNo'].value) {
			alert('You must choose a provider');
			return false;
		}
		form.elements['provider.id'].value = '';
		form.elements['method'].value='save_provider';
		form.submit();
	}
</script>
<input type="hidden" name="teamId" value="0" />
<div class="tabs">
<table cellpadding="3" cellspacing="0" border="0">
	<tr>
		<th title="Programs">Staff</th>
	</tr>
</table>
</div>
<!--  show current staff -->
<c:if test="${not empty providers}">
<table class="carlos-table-spaced simple" id="provider" data-carlos-table data-page-size="0" data-default-sort="0" data-default-order="ascending">
<thead><tr>


    <th scope="col"></th>
    <th scope="col"><span data-table-sort>Name</span></th>
    <th scope="col"><span data-table-sort>Phone</span></th>
    <th scope="col">Team</th>
    <th scope="col">Role</th>
</tr></thead>
<tbody><c:forEach var="provider" items="${providers}" varStatus="tableRowStatus"><tr class="${tableRowStatus.count % 2 == 0 ? 'even' : 'odd'}">


    <td>
		<a href="javascript:void(0);" onclick="deleteProvider('${carlos:forJavaScript(provider.id)}');return false;"> Delete </a>
    </td>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(provider.provider.formattedName))}" data-sort-type="${carlos:tableSortType(provider.provider.formattedName)}">${carlos:forHtml(provider.provider.formattedName)}</td>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(provider.provider.phone))}" data-sort-type="${carlos:tableSortType(provider.provider.phone)}">${carlos:forHtml(provider.provider.phone)}</td>
    <td>
		<table width="100%" cellspacing="2" cellpadding="2">
			<c:forEach var="team" items="${provider.teams}">
				<tr>
					<td>${carlos:forHtml(team.name)}</td>
					<td><a href="javascript:void(0);" onclick="removeTeam('${carlos:forJavaScript(provider.id)}','${carlos:forJavaScript(team.id)}');return false;">Remove</a>
				</tr>
			</c:forEach>
		</table>
		<select name="x" onchange="assignTeam('${carlos:forJavaScript(provider.id)}',this)">
			<option value="" SELECTED></option>
			<c:forEach var="team" items="${teams}">
				<option value="${carlos:forHtmlAttribute(team.id)}">${carlos:forHtml(team.name)}</option>
			</c:forEach>
		</select>
    </td>
    <td>
		<select name="x" onchange="assignRole('${carlos:forJavaScript(provider.id)}',this);">
			<option value="0">&nbsp;</option>
			<c:forEach var="role" items="${roles}">
				<c:choose>
					<c:when test="${role.id == provider.roleId}">
						<option value="${carlos:forHtmlAttribute(role.id)}" selected>${carlos:forHtml(role.name)}</option>
					</c:when>
					<c:otherwise>
						<option value="${carlos:forHtmlAttribute(role.id)}">${carlos:forHtml(role.name)}</option>
					</c:otherwise>
				</c:choose>
			</c:forEach>
		</select>
    </td>
</tr></c:forEach></tbody>
</table>
</c:if>
<c:if test="${empty providers}">No staff currently assigned to this program.</c:if>
<br />
<table width="100%" border="1" cellspacing="2" cellpadding="3">
	<tr class="b">
		<td style="width: 20%;"><label for="program-staff-provider">Provider:</label></td>
		<td>
			<input type="hidden" name="provider.id" />
			<select name="provider.providerNo" id="program-staff-provider">
				<option value="">Select a provider</option>
				<c:forEach var="candidate" items="${availableProviders}">
					<option value="${carlos:forHtmlAttribute(candidate.providerNo)}" <c:if test="${candidate.providerNo == requestScope.staffProvider.providerNo}">selected</c:if>>${carlos:forHtmlContent(candidate.formattedName)}</option>
				</c:forEach>
			</select>
		</td>
	</tr>
	<tr class="b">
		<td style="width: 20%;"><label for="program-staff-role">Role:</label></td>
		<td>
			<select name="provider.roleId" id="program-staff-role">
				<c:forEach var="role" items="${roles}">
					<option value="${carlos:forHtmlAttribute(role.id)}" <c:if test="${role.id == requestScope.staffProvider.roleId}">selected</c:if>>${carlos:forHtmlContent(role.name)}</option>
				</c:forEach>
			</select>
		</td>
	</tr>
	<tr>
		<td colspan="2">
			<input type="button" value="Save" onclick="add_provider(this.form)" />
			<a href="${pageContext.request.contextPath}/PMmodule/ProgramManager?method=edit&amp;id=${carlos:forUriComponent(requestScope.id)}&amp;view.tab=Staff">Cancel</a>
		</td>
	</tr>
</table>
