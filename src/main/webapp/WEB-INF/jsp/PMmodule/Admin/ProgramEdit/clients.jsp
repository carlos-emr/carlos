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
<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ taglib uri="carlos" prefix="carlos" %>
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
            <th title="Programs">Clients</th>
        </tr>
    </table>
</div>
<!-- show current clients -->
<c:if test="${not empty admissions}">
<table class="carlos-table-spaced simple" id="admission" data-carlos-table data-page-size="0" data-default-sort="0" data-default-order="ascending">
<thead><tr>



    <th scope="col"></th>
    <th scope="col"><span data-table-sort>Name</span></th>
    <th scope="col"><span data-table-sort>Admission Date</span></th>
    <caisi:isModuleLoad moduleName="pmm.refer.temporaryAdmission.enabled">
        <th scope="col"><span data-table-sort>Temporary Admission</span></th>
    </caisi:isModuleLoad>
    <th scope="col"><span data-table-sort>Admission Notes</span></th>
    <th scope="col"><span data-table-sort>Team</span></th>
    <th scope="col"></th>
    <th scope="col">Status</th>
</tr></thead>
<tbody><c:forEach var="admission" items="${admissions}" varStatus="tableRowStatus"><tr class="${tableRowStatus.count % 2 == 0 ? 'even' : 'odd'}">



    <td>
        <a href="javascript:void(0);" onclick="alert('Please discharge clients from the client manager');">
            Discharge </a>
    </td>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(admission.client.formattedName))}" data-sort-type="${carlos:tableSortType(admission.client.formattedName)}">${carlos:forHtml(admission.client.formattedName)}</td>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(admission.admissionDate))}" data-sort-type="${carlos:tableSortType(admission.admissionDate)}">${carlos:forHtml(admission.admissionDate)}</td>
    <caisi:isModuleLoad moduleName="pmm.refer.temporaryAdmission.enabled">
        <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(admission.temporaryAdmission))}" data-sort-type="${carlos:tableSortType(admission.temporaryAdmission)}">${carlos:forHtml(admission.temporaryAdmission)}</td>
    </caisi:isModuleLoad>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(admission.admissionNotes))}" data-sort-type="${carlos:tableSortType(admission.admissionNotes)}">${carlos:forHtml(admission.admissionNotes)}</td>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(admission.teamName))}" data-sort-type="${carlos:tableSortType(admission.teamName)}">${carlos:forHtml(admission.teamName)}</td>
    <td>
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
    </td>
    <td>
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
    </td>
</tr></c:forEach></tbody>
</table>
</c:if>
<c:if test="${empty admissions}"><p class="table-empty">No clients currently admitted to this program.</p></c:if>
