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
<script type="text/javascript">
function removeFromQueue(id) {
	document.programManagerForm.elements['queue.id'].value = id;
	document.programManagerForm.elements['method'].value='remove_queue';
	document.programManagerForm.submit();
}

function removeFromRemoteQueue(remoteReferralId) {
	document.programManagerForm.elements['remoteReferralId'].value = remoteReferralId;
	document.programManagerForm.elements['method'].value='remove_remote_queue';
	document.programManagerForm.submit();
}
</script>
<html:hidden property="queue.id" />
<div class="tabs" id="tabs">
	<table cellpadding="3" cellspacing="0" border="0">
		<tr>
			<th title="Programs">Local Queue</th>
		</tr>
	</table>
</div>
<!--  show current clients -->
<c:if test="${not empty queue}">
<table class="carlos-table-spaced simple" id="queue_entry" data-carlos-table data-page-size="0" data-default-sort="0" data-default-order="ascending">
<thead><tr>


    <th scope="col"></th>
    <th scope="col"><span data-table-sort>Client Name</span></th>
    <th scope="col"><span data-table-sort>Referral Date</span></th>
    <th scope="col"><span data-table-sort>Referring Provider</span></th>
    <caisi:isModuleLoad moduleName="pmm.refer.temporaryAdmission.enabled">
        <th scope="col"><span data-table-sort>Temporary Admission</span></th>
    </caisi:isModuleLoad>
    <th scope="col"><span data-table-sort>Notes</span></th>
</tr></thead>
<tbody><c:forEach var="queue_entry" items="${queue}" varStatus="tableRowStatus"><tr class="${tableRowStatus.count % 2 == 0 ? 'even' : 'odd'}">


    <td>
		<a href="javascript:void(0);" onclick="removeFromQueue('${carlos:forJavaScript(queue_entry.id)}');return false;"> Remove </a>
    </td>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(queue_entry.clientFormattedName))}" data-sort-type="${carlos:tableSortType(queue_entry.clientFormattedName)}">${carlos:forHtml(queue_entry.clientFormattedName)}</td>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(queue_entry.referralDate))}" data-sort-type="${carlos:tableSortType(queue_entry.referralDate)}">${carlos:forHtml(queue_entry.referralDate)}</td>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(queue_entry.providerFormattedName))}" data-sort-type="${carlos:tableSortType(queue_entry.providerFormattedName)}">${carlos:forHtml(queue_entry.providerFormattedName)}</td>
  	<caisi:isModuleLoad moduleName="pmm.refer.temporaryAdmission.enabled">
        <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(queue_entry.temporaryAdmission))}" data-sort-type="${carlos:tableSortType(queue_entry.temporaryAdmission)}">${carlos:forHtml(queue_entry.temporaryAdmission)}</td>
	</caisi:isModuleLoad>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(queue_entry.notes))}" data-sort-type="${carlos:tableSortType(queue_entry.notes)}">${carlos:forHtml(queue_entry.notes)}</td>
</tr></c:forEach></tbody>
</table>
</c:if>
<c:if test="${empty queue}"><p class="table-empty">Queue is empty.</p></c:if>

<c:if test="${remoteQueue!=null}">
	<br /><br />

	<input type="hidden" name="remoteReferralId" />

	<div class="tabs" id="tabs">
		<table cellpadding="3" cellspacing="0" border="0">
			<tr>
				<th title="Programs">Remote Queue</th>
			</tr>
		</table>
	</div>
	<!--  show current clients -->
    <c:if test="${not empty remoteQueue}">
    <table class="carlos-table-spaced simple" id="remote_queue_entry" data-carlos-table data-page-size="0" data-default-sort="0" data-default-order="ascending">
<thead><tr>


        <th scope="col"></th>
        <th scope="col"><span data-table-sort>Client Name</span></th>
        <th scope="col"><span data-table-sort>Referral Date</span></th>
        <th scope="col"><span data-table-sort>Referring Provider</span></th>
        <th scope="col"><span data-table-sort>Notes</span></th>
    </tr></thead>
<tbody><c:forEach var="queue_entry" items="${remoteQueue}" varStatus="tableRowStatus"><tr class="${tableRowStatus.count % 2 == 0 ? 'even' : 'odd'}">


        <td>
			<a href="javascript:void(0);" onclick="removeFromRemoteQueue('${carlos:forJavaScript(queue_entry.remoteReferral.remoteReferralId)}');return false;"> Remove </a>
        </td>
        <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(queue_entry.clientName))}" data-sort-type="${carlos:tableSortType(queue_entry.clientName)}">${carlos:forHtml(queue_entry.clientName)}</td>
        <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(queue_entry.remoteReferral.referralDate))}" data-sort-type="${carlos:tableSortType(queue_entry.remoteReferral.referralDate)}">${carlos:forHtml(queue_entry.remoteReferral.referralDate)}</td>
        <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(queue_entry.providerName))}" data-sort-type="${carlos:tableSortType(queue_entry.providerName)}">${carlos:forHtml(queue_entry.providerName)}</td>
        <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(queue_entry.remoteReferral.reasonForReferral))}" data-sort-type="${carlos:tableSortType(queue_entry.remoteReferral.reasonForReferral)}">${carlos:forHtml(queue_entry.remoteReferral.reasonForReferral)}</td>
    </tr></c:forEach></tbody>
</table>
    </c:if>
<c:if test="${empty remoteQueue}"><p class="table-empty">Queue is empty.</p></c:if>
</c:if>
