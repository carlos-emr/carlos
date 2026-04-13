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

<script>
    function assignTeam(id, selectBox) {
        var team_id = selectBox.options[selectBox.selectedIndex].value;
        document.programManagerViewForm.elements['teamId'].value = team_id;
        document.programManagerViewForm.elements['admissionId'].value = id;
        document.programManagerViewForm.method.value = 'assign_team_client';
        document.programManagerViewForm.submit();
    }

    function assignStatus(id, selectBox) {
        var status_id = selectBox.options[selectBox.selectedIndex].value;
        document.programManagerViewForm.elements['clientStatusId'].value = status_id;
        document.programManagerViewForm.elements['admissionId'].value = id;
        document.programManagerViewForm.method.value = 'assign_status_client';
        document.programManagerViewForm.submit();
    }

    function dischargeToCommunity(id, selectBox) {
        var com = selectBox.options[selectBox.selectedIndex].value;
        document.programManagerViewForm.community.value = com;
        document.programManagerViewForm.elements['admissionId'].value = id;
        document.programManagerViewForm.method.value = 'discharge_To_Community';
        document.programManagerViewForm.submit();
    }

    function do_batch_discharge(community) {
        //get clients
        var elements = document.programManagerViewForm.elements;
        var admissionIds = new Array();
        var x = 0;
        var numClients = 0;
        for (var i = 0; i < elements.length; i++) {
            if (elements[i].type == 'checkbox' && elements[i].name.substring(0, 8) == 'checked_') {
                if (elements[i].checked == true) {
                    var admissionId = elements[i].name.substring(8);
                    admissionIds[x++] = admissionId;
                    numClients++;
                }
            }
        }

        //get program to admit to
        var programBox;
        var programName = '';
        if (community) {
            programBox = document.programManagerViewForm.batch_discharge_community_program;
            document.programManagerViewForm.type.value = 'community';
            if (programBox && programBox.selectedIndex >= 0) {
                var programId = programBox.options[programBox.selectedIndex].value;
                programName = programBox.options[programBox.selectedIndex].text;
                if (programId == 0) {
                    alert('Please choose a Community Program from the list');
                    return;
                }
            }
        } else {
            document.programManagerViewForm.type.value = 'service';
        }

        if (numClients == 0) {
            alert('You have to select the clients');
            return;
        }

        var msg = 'You are about to discharge ' + numClients + ' client(s)';
        if (programName) {
            msg += ' and admit them to ' + programName;
        }
        msg += '.\nAre you sure you would like to continue?';

        if (confirm(msg)) {
            document.programManagerViewForm.method.value = 'batch_discharge';
            document.programManagerViewForm.submit();
        }
    }
</script>
<input type="hidden" name="teamId" value=""/>
<input type="hidden" name="admissionId" value=""/>
<input type="hidden" name="community" value=""/>
<input type="hidden" name="type" value=""/>
<input type="hidden" name="program_name"
       value="<c:out value="${program_name}"/>"/>
<input type="hidden" name="clientStatusId"/>

<div class="tabs" id="tabs">
    <table cellpadding="3" cellspacing="0" border="0">
        <tr>
            <th title="Programs">Clients</th>
        </tr>
    </table>
</div>
<!-- show current clients -->
<display:table class="simple" cellspacing="2" cellpadding="3"
               id="admission" name="admissions" export="false" pagesize="0"
               requestURI="/PMmodule/ProgramManagerView.do">
    <display:setProperty name="paging.banner.placement" value="bottom"/>
    <display:setProperty name="basic.msg.empty_list"
                         value="No clients currently admitted to this program."/>
    <display:column>
        <input type="checkbox" name="checked_<c:out value="${admission.id}"/>">
    </display:column>
    <display:column property="client.formattedName" sortable="true"
                    title="Name"/>
    <display:column property="admissionDate" sortable="true"
                    title="Admission Date"/>

    <caisi:isModuleLoad moduleName="pmm.refer.temporaryAdmission.enabled">
        <display:column property="temporaryAdmission" sortable="true"
                        title="Temporary Admission"/>
    </caisi:isModuleLoad>
    <display:column property="admissionNotes" sortable="true"
                    title="Admission Notes"/>

    <display:column property="teamName" sortable="true" title="Team"/>

    <display:column sortable="false" title="">
        <select name="x"
                onchange="assignTeam('<c:out value="${admission.id}"/>',this);">
            <option value="0">&nbsp;</option>
            <c:forEach var="team" items="${teams}">
                <c:choose>
                    <c:when test="${team.id == admission.teamId}">
                        <option value="<c:out value="${team.id}"/>" selected><c:out
                                value="${team.name}"/></option>
                    </c:when>
                    <c:otherwise>
                        <option value="<c:out value="${team.id}"/>"><c:out
                                value="${team.name}"/></option>
                    </c:otherwise>
                </c:choose>
            </c:forEach>
        </select>
    </display:column>

    <display:column sortable="false" title="Status">
        <select name="y"
                onchange="assignStatus('<c:out value="${admission.id}"/>',this);">
            <option value="0">&nbsp;</option>
            <c:forEach var="status" items="${client_statuses}">
                <c:choose>
                    <c:when test="${status.id == admission.clientStatusId}">
                        <option value="<c:out value="${status.id}"/>" selected><c:out
                                value="${status.name}"/></option>
                    </c:when>
                    <c:otherwise>
                        <option value="<c:out value="${status.id}"/>"><c:out
                                value="${status.name}"/></option>
                    </c:otherwise>
                </c:choose>
            </c:forEach>
        </select>
    </display:column>
</display:table>
<br/>


<c:if
        test="${requestScope.allowBatchDischarge == true and program.type eq 'Service'}">
    <input type="button" value="Batch Discharge"
           onclick="do_batch_discharge(false)"/>

    <!--
    <input type="button" value="Batch Discharge To CAISI Service Program" onclick="do_batch_discharge(false,false)" />
    <select name="batch_discharge_program">
    <option value="0"></option>
    <c:forEach var="program" items="${servicePrograms}">
        <c:if test="${program.id != requestScope.id}">
            <option value="<c:out value="${program.id}"/>"><c:out value="${program.name}"/></option>
        </c:if>
    </c:forEach>
    </select>
    <br />

    <input type="button" value="Batch Discharge To Community Program" onclick="do_batch_discharge(true,false)" />
    <select name="batch_discharge_community_program">
    <option value="0"></option>
    <c:forEach var="program" items="${communityPrograms}">
        <c:if test="${program.id != requestScope.id}">
            <option value="<c:out value="${program.id}"/>"><c:out value="${program.name}"/></option>
        </c:if>
    </c:forEach>
    </select>
    -->
</c:if>
