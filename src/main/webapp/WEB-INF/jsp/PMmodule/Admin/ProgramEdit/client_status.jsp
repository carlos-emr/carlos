<%@ taglib uri="carlos" prefix="carlos" %>

<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ page import="java.util.ResourceBundle" %>
<%
    java.util.ResourceBundle oscarResources = java.util.ResourceBundle.getBundle("oscarResources", request.getLocale());
%>
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
<script>
function deleteStatus(id) {
	if(!confirm('<%= SafeEncode.forJavaScript(oscarResources.getString("pmmodule.admin.programEdit.clientStatus.confirmDelete")) %>')) {
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
		alert('<%= SafeEncode.forJavaScript(oscarResources.getString("pmmodule.admin.programEdit.clientStatus.alertChooseName")) %>');
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
		<fmt:message key="pmmodule.admin.programEdit.clientStatus.titlePrograms" var="titlePrograms"/>
<th title="${carlos:forHtmlAttribute(titlePrograms)}"><fmt:message key="pmmodule.admin.programEdit.clientStatus.thClientStatus"/></th>
	</tr>
</table>
</div>
<!--  show current staff -->
<display:table class="simple" cellspacing="2" cellpadding="3" id="status" name="client_statuses" export="false" pagesize="0" requestURI="/PMmodule/ProgramManager">
	<display:setProperty name="paging.banner.placement" value="bottom" />
	<fmt:message key="pmmodule.admin.programEdit.clientStatus.emptyList" var="emptyListMsg"/>
<display:setProperty name="basic.msg.empty_list" value="${emptyListMsg}"/>
	<display:column sortable="false" title="">
		<a onclick="deleteStatus('${carlos:forJavaScript(status.id)}');return false;" href="javascript:void(0);"><fmt:message key="pmmodule.admin.programEdit.clientStatus.aDelete"/></a>
	</display:column>
	<fmt:message key="pmmodule.admin.programEdit.clientStatus.titleName" var="titleName"/>
<display:column property="name" sortable="true" title="${carlos:forHtmlAttribute(titleName)}" />
</display:table>
<br />
<html:hidden property="client_status.id" />
<table width="100%" border="1" cellspacing="2" cellpadding="3">
	<tr class="b">
		<td width="20%"><fmt:message key='pmmodule.admin.programEdit.clientStatus.tdName'/></td>
		<td><html:text property="client_status.name" size="50" maxlength="255"/></td>
	</tr>
	<tr>
		<td colspan="2">
			<input type="button" value="<fmt:message key='pmmodule.admin.programEdit.clientStatus.btnSave'/>" onclick="add_status(this.form)" /> <html:cancel />
		</td>
	</tr>
</table>
