<%--


    Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
    This software is published under the GPL GNU General Public License.
    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public License
    as published by the Free Software Foundation; either version 2
    of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.

    This software was written for
    Centre for Research on Inner City Health, St. Michael's Hospital,
    Toronto, Ontario, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>


<%@ include file="/taglibs.jsp" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ taglib uri="carlos" prefix="carlos" %>

<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ page import="java.util.ResourceBundle" %>
<%
    java.util.ResourceBundle oscarResources = java.util.ResourceBundle.getBundle("oscarResources", request.getLocale());
%>
<c:url var="programManagerClientsUri" value="/PMmodule/ProgramManager">
    <c:param name="method" value="edit"/>
    <c:param name="id" value="${requestScope.id}"/>
    <c:param name="view.tab" value="Clients"/>
</c:url>
<script>
    function assignTeam(id, selectBox) {
        var team_id = selectBox.options[selectBox.selectedIndex].value;
        document.programManagerForm.elements['admission.teamId'].value = team_id;
        document.programManagerForm.elements['admission.id'].value = id;
        document.programManagerForm.elements['method'].value = 'assign_team_client';
        document.programManagerForm.submit();
    }

    function assignStatus(id, selectBox) {
        var status_id = selectBox.options[selectBox.selectedIndex].value;
        document.programManagerForm.elements['admission.clientStatusId'].value = status_id;
        document.programManagerForm.elements['admission.id'].value = id;
        document.programManagerForm.elements['method'].value = 'assign_status_client';
        document.programManagerForm.submit();
    }
</script>
<input type="hidden" name="view.tab" value="Clients"/>
<input type="hidden" name="admission.id" id="admissionId"/>
<input type="hidden" name="admission.teamId" id="teamId"/>
<input type="hidden" name="admission.clientStatusId" id="clientStatusId"/>
<div class="tabs">
    <table cellpadding="3" cellspacing="0" border="0">
        <tr>
            <fmt:message key="pmmodule.admin.programEdit.clients.titlePrograms" var="titlePrograms"/>
<th title="${carlos:forHtmlAttribute(titlePrograms)}"><fmt:message key="pmmodule.admin.programEdit.clients.thClients"/></th>
        </tr>
    </table>
</div>
<!-- show current clients -->
<display:table class="simple" cellspacing="2" cellpadding="3" id="admission" name="admissions" export="false"
               pagesize="0" requestURI="${programManagerClientsUri}">
    <display:setProperty name="paging.banner.placement" value="bottom"/>
    <fmt:message key="pmmodule.admin.programEdit.clients.emptyList" var="emptyListMsg"/>
<display:setProperty name="basic.msg.empty_list" value="${emptyListMsg}"/>

    <display:column sortable="false" title="">
        <a href="javascript:void(0);" onclick="alert('<%= SafeEncode.forJavaScript(oscarResources.getString("pmmodule.admin.programEdit.clients.alertDischarge")) %>');">
            Discharge </a>
    </display:column>
    <fmt:message key="pmmodule.admin.programEdit.clients.titleName" var="titleName"/>
<display:column property="client.formattedName" sortable="true" title="${carlos:forHtmlAttribute(titleName)}"/>
    <fmt:message key="pmmodule.admin.programEdit.clients.titleAdmissionDate" var="titleAdmissionDate"/>
<display:column property="admissionDate" sortable="true" title="${carlos:forHtmlAttribute(titleAdmissionDate)}"/>
    <caisi:isModuleLoad moduleName="pmm.refer.temporaryAdmission.enabled">
        <fmt:message key="pmmodule.admin.programEdit.clients.titleTemporaryAdmission" var="titleTemporaryAdmission"/>
<display:column property="temporaryAdmission" sortable="true" title="${carlos:forHtmlAttribute(titleTemporaryAdmission)}"/>
    </caisi:isModuleLoad>
    <fmt:message key="pmmodule.admin.programEdit.clients.titleAdmissionNotes" var="titleAdmissionNotes"/>
<display:column property="admissionNotes" sortable="true" title="${carlos:forHtmlAttribute(titleAdmissionNotes)}"/>
    <fmt:message key="pmmodule.admin.programEdit.clients.titleTeam" var="titleTeam"/>
<display:column property="teamName" sortable="true" title="${carlos:forHtmlAttribute(titleTeam)}"/>
    <display:column sortable="false" title="">
        <select name="x" onchange="assignTeam('${carlos:forJavaScript(admission.id)}',this);">
            <option value="0">&nbsp;</option>
            <c:forEach var="team" items="${teams}">
                <c:choose>
                    <c:when test="${team.id == admission.teamId}">
                        <option value="${carlos:forHtmlAttribute(team.id)}" selected>${carlos:forHtml(team.name)}</option>
                    </c:when>
                    <c:otherwise>
                        <option value="${carlos:forHtmlAttribute(team.id)}">${carlos:forHtml(team.name)}</option>
                    </c:otherwise>
                </c:choose>
            </c:forEach>
        </select>
    </display:column>
    <fmt:message key="pmmodule.admin.programEdit.clients.titleStatus" var="titleStatus"/>
<display:column sortable="false" title="${carlos:forHtmlAttribute(titleStatus)}">
        <select name="y" onchange="assignStatus('${carlos:forJavaScript(admission.id)}',this);">
            <option value="0">&nbsp;</option>
            <c:forEach var="status" items="${client_statuses}">
                <c:choose>
                    <c:when test="${status.id == admission.clientStatusId}">
                        <option value="${carlos:forHtmlAttribute(status.id)}" selected>${carlos:forHtml(status.name)}</option>
                    </c:when>
                    <c:otherwise>
                        <option value="${carlos:forHtmlAttribute(status.id)}">${carlos:forHtml(status.name)}</option>
                    </c:otherwise>
                </c:choose>
            </c:forEach>
        </select>
    </display:column>
</display:table>
