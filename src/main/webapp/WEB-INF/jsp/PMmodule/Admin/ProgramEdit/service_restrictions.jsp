<%@ page import="io.github.carlos_emr.carlos.PMmodule.model.ProgramClientRestriction" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
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
	function save() {
		var maxDays = document.programManagerForm.elements['program.maximumServiceRestrictionDays'].value;
		if(maxDays != undefined && isNaN(maxDays)) {
			alert("Maximum length of service restriction '" + maxDays + "' is not a number");
			return false;
		}

        var defDays = document.programManagerForm.elements['program.defaultServiceRestrictionDays'].value;
		if(isNaN(defDays)) {
			alert("Default length of service restrcition '" + defDays + "' is not a number");
			return false;
		}

        document.programManagerForm.elements['method'].value='save_restriction_settings';
		document.programManagerForm.submit()
	}

</script>

<div class="tabs" id="tabs">
    <table cellpadding="3" cellspacing="0" border="0">
        <tr>
            <th title="Service Restrictions">Service Restriction Settings</th>
        </tr>
    </table>
</div>
Please define the following parameters control the behaviour of new service restrictions for this program.
<table width="100%" border="1" cellspacing="2" cellpadding="3">
	<tr class="b">
		<td width="20%">Maximum length of service restriction (in days):</td>
		<td><html:text property="program.maximumServiceRestrictionDays" size="4" maxlength="4"/>&nbsp;(empty or zero means no maximum)</td>
	</tr>
	<tr class="b">
		<td width="20%">Default service restriction length (in days):</td>
		<td><html:text property="program.defaultServiceRestrictionDays" size="4" maxlength="4"/></td>
	</tr>
	<tr>
		<td colspan="2">
			<input type="button" value="Save" onclick="return save()" />
		</td>
	</tr>
</table>
<br/>
<div class="tabs" id="tabs">
    <table cellpadding="3" cellspacing="0" border="0">
        <tr>
            <th title="Service Restrictions">Current Service Restrictions</th>
        </tr>
    </table>
</div>
<script type="text/javascript">
    function disableRestriction(id) {
        document.programManagerForm.elements['restriction.id'].value = id;
        document.programManagerForm.elements['method'].value='disable_restriction';
        document.programManagerForm.submit();
    }

    function enableRestriction(id) {
        document.programManagerForm.elements['restriction.id'].value = id;
        document.programManagerForm.elements['method'].value='enable_restriction';
        document.programManagerForm.submit();
    }
</script>
<html:hidden property="restriction.id" />

<%@ include file="/WEB-INF/jsp/common/carlosTableAssets.jspf" %>
<table class="simple" cellspacing="2" cellpadding="3" id="restriction" data-carlos-table data-page-size="0" data-default-sort="0" data-default-order="ascending">
<thead><tr>



    <th scope="col"></th>
    <th scope="col"><span data-table-sort>Id</span></th>
    <th scope="col"><span data-table-sort>Client</span></th>
    <th scope="col"><span data-table-sort>Restricted By</span></th>
    <th scope="col"><span data-table-sort>Comments</span></th>
    <th scope="col"><span data-table-sort>Start date</span></th>
    <th scope="col"><span data-table-sort>End date</span></th>
</tr></thead>
<tbody><c:forEach var="restriction" items="${service_restrictions}" varStatus="tableRowStatus"><tr class="${tableRowStatus.count % 2 == 0 ? 'even' : 'odd'}">



    <td>
        <%
            String demographicNo = "" + ((ProgramClientRestriction)pageContext.getAttribute("restriction")).getDemographicNo();
        %>
        <caisirole:SecurityAccess accessName="Disable service restriction" accessType="access" providerNo='<%=((Provider)request.getSession().getAttribute("provider")).getProviderNo()%>' demoNo="<%=demographicNo%>" programId='<%=request.getParameter("id")%>'>
            <a onclick="disableRestriction('${carlos:forJavaScript(restriction.id)}');return false;" href="javascript:void(0);"> Disable </a>
        </caisirole:SecurityAccess>
    </td>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(restriction.id))}" data-sort-type="${carlos:tableSortType(restriction.id)}"><c:out value="${restriction.id}"/></td>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(restriction.client.formattedName))}" data-sort-type="${carlos:tableSortType(restriction.client.formattedName)}"><c:out value="${restriction.client.formattedName}"/></td>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(restriction.provider.formattedName))}" data-sort-type="${carlos:tableSortType(restriction.provider.formattedName)}"><c:out value="${restriction.provider.formattedName}"/></td>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(restriction.comments))}" data-sort-type="${carlos:tableSortType(restriction.comments)}"><c:out value="${restriction.comments}"/></td>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(restriction.startDate))}" data-sort-type="${carlos:tableSortType(restriction.startDate)}"><c:out value="${restriction.startDate}"/></td>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(restriction.endDate))}" data-sort-type="${carlos:tableSortType(restriction.endDate)}"><c:out value="${restriction.endDate}"/></td>
</tr></c:forEach></tbody>
</table>
<c:if test="${empty service_restrictions}"><p class="table-empty">No service restrictions currently in place for this program.</p></c:if>

<br/>
<div class="tabs" id="tabs">
    <table cellpadding="3" cellspacing="0" border="0">
        <tr>
            <th title="Service Restrictions">Disabled Service Restrictions</th>
        </tr>
    </table>
</div>

<table class="simple" cellspacing="2" cellpadding="3" id="disabled_restriction" data-carlos-table data-page-size="0" data-default-sort="0" data-default-order="ascending">
<thead><tr>



    <th scope="col"></th>
    <th scope="col"><span data-table-sort>Id</span></th>
    <th scope="col"><span data-table-sort>Client</span></th>
    <th scope="col"><span data-table-sort>Restricted By</span></th>
    <th scope="col"><span data-table-sort>Comments</span></th>
    <th scope="col"><span data-table-sort>Start date</span></th>
    <th scope="col"><span data-table-sort>End date</span></th>
</tr></thead>
<tbody><c:forEach var="restriction" items="${disabled_service_restrictions}" varStatus="tableRowStatus"><tr class="${tableRowStatus.count % 2 == 0 ? 'even' : 'odd'}">



    <td>
        <%
            String demographicNo = "" + ((ProgramClientRestriction)pageContext.getAttribute("restriction")).getDemographicNo();
        %>
        <caisirole:SecurityAccess accessName="Create service restriction" accessType="access" providerNo='<%=((Provider)request.getSession().getAttribute("provider")).getProviderNo()%>' demoNo="<%=demographicNo%>" programId='<%=request.getParameter("id")%>'>
            <a onclick="enableRestriction('${carlos:forJavaScript(restriction.id)}');return false;" href="javascript:void(0);"> Enable </a>
        </caisirole:SecurityAccess>
    </td>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(restriction.id))}" data-sort-type="${carlos:tableSortType(restriction.id)}"><c:out value="${restriction.id}"/></td>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(restriction.client.formattedName))}" data-sort-type="${carlos:tableSortType(restriction.client.formattedName)}"><c:out value="${restriction.client.formattedName}"/></td>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(restriction.provider.formattedName))}" data-sort-type="${carlos:tableSortType(restriction.provider.formattedName)}"><c:out value="${restriction.provider.formattedName}"/></td>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(restriction.comments))}" data-sort-type="${carlos:tableSortType(restriction.comments)}"><c:out value="${restriction.comments}"/></td>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(restriction.startDate))}" data-sort-type="${carlos:tableSortType(restriction.startDate)}"><c:out value="${restriction.startDate}"/></td>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(restriction.endDate))}" data-sort-type="${carlos:tableSortType(restriction.endDate)}"><c:out value="${restriction.endDate}"/></td>
</tr></c:forEach></tbody>
</table>
<c:if test="${empty disabled_service_restrictions}"><p class="table-empty">No service restrictions currently in place for this program.</p></c:if>
