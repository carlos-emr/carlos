<%@ page import="io.github.carlos_emr.carlos.PMmodule.model.ProgramClientRestriction" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
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
	function save() {
		var maxDays = document.programManagerForm.elements['program.maximumServiceRestrictionDays'].value;
		if(maxDays != undefined && isNaN(maxDays)) {
			alert('<%= SafeEncode.forJavaScript(oscarResources.getString("pmmodule.admin.programEdit.serviceRestrictions.alertMaxLength")) %>' + " '" + maxDays + "' " + '<%= SafeEncode.forJavaScript(oscarResources.getString("pmmodule.admin.programEdit.serviceRestrictions.alertIsNotNumber")) %>');
			return false;
		}

        var defDays = document.programManagerForm.elements['program.defaultServiceRestrictionDays'].value;
		if(isNaN(defDays)) {
			alert('<%= SafeEncode.forJavaScript(oscarResources.getString("pmmodule.admin.programEdit.serviceRestrictions.alertDefaultLength")) %>' + " '" + defDays + "' " + '<%= SafeEncode.forJavaScript(oscarResources.getString("pmmodule.admin.programEdit.serviceRestrictions.alertIsNotNumber")) %>');
			return false;
		}

        document.programManagerForm.elements['method'].value='save_restriction_settings';
		document.programManagerForm.submit()
	}

</script>

<div class="tabs" id="tabs">
    <table cellpadding="3" cellspacing="0" border="0">
        <tr>
            <fmt:message key="pmmodule.admin.programEdit.serviceRestrictions.titleServiceRestrictions" var="titleServiceRestrictions"/>
<th title="${carlos:forHtmlAttribute(titleServiceRestrictions)}"><fmt:message key="pmmodule.admin.programEdit.serviceRestrictions.thSettings"/></th>
        </tr>
    </table>
</div>
<fmt:message key='pmmodule.admin.programEdit.serviceRestrictions.textDefineParameters'/>
<table width="100%" border="1" cellspacing="2" cellpadding="3">
	<tr class="b">
		<td width="20%"><fmt:message key="pmmodule.admin.programEdit.serviceRestrictions.tdMaxLength"/></td>
		<td><html:text property="program.maximumServiceRestrictionDays" size="4" maxlength="4"/>&nbsp;(empty or zero means no maximum)</td>
	</tr>
	<tr class="b">
		<td width="20%"><fmt:message key="pmmodule.admin.programEdit.serviceRestrictions.tdDefaultLength"/></td>
		<td><html:text property="program.defaultServiceRestrictionDays" size="4" maxlength="4"/></td>
	</tr>
	<tr>
		<td colspan="2">
			<input type="button" value="<fmt:message key='pmmodule.admin.programEdit.serviceRestrictions.btnSave'/>" onclick="return save()" />
		</td>
	</tr>
</table>
<br/>
<div class="tabs" id="tabs">
    <table cellpadding="3" cellspacing="0" border="0">
        <tr>
            <fmt:message key="pmmodule.admin.programEdit.serviceRestrictions.titleServiceRestrictions" var="titleServiceRestrictions"/>
<th title="${carlos:forHtmlAttribute(titleServiceRestrictions)}"><fmt:message key="pmmodule.admin.programEdit.serviceRestrictions.thCurrent"/></th>
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

<display:table class="simple" cellspacing="2" cellpadding="3" id="restriction" name="service_restrictions" export="false" pagesize="0" requestURI="/PMmodule/ProgramManager">
    <display:setProperty name="paging.banner.placement" value="bottom" />
    <fmt:message key="pmmodule.admin.programEdit.serviceRestrictions.emptyList" var="emptyListMsg"/>
<display:setProperty name="basic.msg.empty_list" value="${emptyListMsg}"/>

    <display:column sortable="false">
        <%
            String demographicNo = "" + ((ProgramClientRestriction)pageContext.getAttribute("restriction")).getDemographicNo();
        %>
        <caisirole:SecurityAccess accessName="Disable service restriction" accessType="access" providerNo='<%=((Provider)request.getSession().getAttribute("provider")).getProviderNo()%>' demoNo="<%=demographicNo%>" programId='<%=request.getParameter("id")%>'>
            <a onclick="disableRestriction('${carlos:forJavaScript(restriction.id)}');return false;" href="javascript:void(0);"><fmt:message key="pmmodule.admin.programEdit.serviceRestrictions.aDisable"/></a>
        </caisirole:SecurityAccess>
    </display:column>
    <fmt:message key="pmmodule.admin.programEdit.serviceRestrictions.titleId" var="titleId"/>
<display:column property="id" sortable="true" title="${carlos:forHtmlAttribute(titleId)}" />
    <fmt:message key="pmmodule.admin.programEdit.serviceRestrictions.titleClient" var="titleClient"/>
<display:column property="client.formattedName" sortable="true" title="${carlos:forHtmlAttribute(titleClient)}" />
    <fmt:message key="pmmodule.admin.programEdit.serviceRestrictions.titleRestrictedBy" var="titleRestrictedBy"/>
<display:column property="provider.formattedName" sortable="true" title="${carlos:forHtmlAttribute(titleRestrictedBy)}"/>
    <fmt:message key="pmmodule.admin.programEdit.serviceRestrictions.titleComments" var="titleComments"/>
<display:column property="comments" sortable="true" title="${carlos:forHtmlAttribute(titleComments)}" />
    <fmt:message key="pmmodule.admin.programEdit.serviceRestrictions.titleStartDate" var="titleStartDate"/>
<display:column property="startDate" sortable="true" title="${carlos:forHtmlAttribute(titleStartDate)}" />
    <fmt:message key="pmmodule.admin.programEdit.serviceRestrictions.titleEndDate" var="titleEndDate"/>
<display:column property="endDate" sortable="true" title="${carlos:forHtmlAttribute(titleEndDate)}" />
</display:table>

<br/>
<div class="tabs" id="tabs">
    <table cellpadding="3" cellspacing="0" border="0">
        <tr>
            <fmt:message key="pmmodule.admin.programEdit.serviceRestrictions.titleServiceRestrictions" var="titleServiceRestrictions"/>
<th title="${carlos:forHtmlAttribute(titleServiceRestrictions)}"><fmt:message key="pmmodule.admin.programEdit.serviceRestrictions.thDisabled"/></th>
        </tr>
    </table>
</div>

<display:table class="simple" cellspacing="2" cellpadding="3" id="restriction" name="disabled_service_restrictions" export="false" pagesize="0" requestURI="/PMmodule/ProgramManager">
    <display:setProperty name="paging.banner.placement" value="bottom" />
    <fmt:message key="pmmodule.admin.programEdit.serviceRestrictions.emptyList" var="emptyListMsg"/>
<display:setProperty name="basic.msg.empty_list" value="${emptyListMsg}"/>

    <display:column sortable="false">
        <%
            String demographicNo = "" + ((ProgramClientRestriction)pageContext.getAttribute("restriction")).getDemographicNo();
        %>
        <caisirole:SecurityAccess accessName="Create service restriction" accessType="access" providerNo='<%=((Provider)request.getSession().getAttribute("provider")).getProviderNo()%>' demoNo="<%=demographicNo%>" programId='<%=request.getParameter("id")%>'>
            <a onclick="enableRestriction('${carlos:forJavaScript(restriction.id)}');return false;" href="javascript:void(0);"><fmt:message key="pmmodule.admin.programEdit.serviceRestrictions.aEnable"/></a>
        </caisirole:SecurityAccess>
    </display:column>
    <fmt:message key="pmmodule.admin.programEdit.serviceRestrictions.titleId" var="titleId"/>
<display:column property="id" sortable="true" title="${carlos:forHtmlAttribute(titleId)}" />
    <fmt:message key="pmmodule.admin.programEdit.serviceRestrictions.titleClient" var="titleClient"/>
<display:column property="client.formattedName" sortable="true" title="${carlos:forHtmlAttribute(titleClient)}" />
    <fmt:message key="pmmodule.admin.programEdit.serviceRestrictions.titleRestrictedBy" var="titleRestrictedBy"/>
<display:column property="provider.formattedName" sortable="true" title="${carlos:forHtmlAttribute(titleRestrictedBy)}"/>
    <fmt:message key="pmmodule.admin.programEdit.serviceRestrictions.titleComments" var="titleComments"/>
<display:column property="comments" sortable="true" title="${carlos:forHtmlAttribute(titleComments)}" />
    <fmt:message key="pmmodule.admin.programEdit.serviceRestrictions.titleStartDate" var="titleStartDate"/>
<display:column property="startDate" sortable="true" title="${carlos:forHtmlAttribute(titleStartDate)}" />
    <fmt:message key="pmmodule.admin.programEdit.serviceRestrictions.titleEndDate" var="titleEndDate"/>
<display:column property="endDate" sortable="true" title="${carlos:forHtmlAttribute(titleEndDate)}" />
</display:table>
