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
<script>
    function ConfirmDelete(name) {
        if (confirm("Are you sure you want to delete " + name + " ?")) {
            return true;
        }
        return false;
    }

    function submitForm(method) {
        document.programManagerForm.method.value = method;
        document.programManagerForm.submit()
    }
</script>
<%@ include file="/common/messages.jsp" %>
<div class="tabs" id="tabs">
    <table cellpadding="3" cellspacing="0" border="0">
        <tr>
            <th title="Programs">Programs</th>
        </tr>
    </table>
</div>
<form action="${pageContext.request.contextPath}/PMmodule/ProgramManager.do" method="post">
    <table class="simple" cellspacing="2" cellpadding="3" width="100%">
        <thead>
        <tr>
            <th>Status</th>
            <th>Type</th>
            <th>Facility</th>
            <th>&nbsp;</th>
        </tr>
        </thead>
        <tbody>
        <tr>
            <td><select name="searchStatus" id="searchStatus">
                <option value="Any"/>
                <option value="active"/>
                <option value="inactive"/>
            </select></td>
            <td><select name="searchType" id="searchType">
                <option value="Any"/>
                <option value="Service"/>
                <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="false">
                    <option value="External"/>
                    <option value="community">Community</option>
                </caisi:isModuleLoad>
            </select></td>
            <td><select property="searchFacilityId">
                <option value="0">Any</option>
                <c:forEach var="facility" items="${facilities}">
                    <option value="${facility.id}">
                        <c:out value="${facility.name}"/>
                    </option>
                </c:forEach>
            </select></td>
            <td><input type="button" name="search" value="Search"
                       onclick="javascript:submitForm('list')" ;/></td>
        </tr>
        <tr>
            <td colspan="4">&nbsp;</td>
        </tr>
        </tbody>
    </table>
</form>
<display:table class="simple" cellspacing="2" cellpadding="3"
               id="program" name="programs" export="false" pagesize="0"
               requestURI="/PMmodule/ProgramManager.do">
    <display:setProperty name="paging.banner.placement" value="bottom"/>
    <display:setProperty name="paging.banner.item_name" value="program"/>
    <display:setProperty name="paging.banner.items_name" value="programs"/>
    <display:setProperty name="basic.msg.empty_list"
                         value="No programs found."/>

    <display:column sortable="false" title="">
        <a
                onclick="return ConfirmDelete('<c:out value="${program.nameJs}"/>')"
                href="<%=request.getContextPath() %>/PMmodule/ProgramManager.do?method=delete&id=<c:out value="${program.id}"/>&name=<c:out value="${program.name}"/>">
            Delete </a>
    </display:column>

    <c:choose>
        <c:when test="${program.programStatus=='active'}">
            <display:column sortable="false" title="">
                <a
                        href="<%=request.getContextPath() %>/PMmodule/ProgramManager.do?method=edit&id=<c:out value="${program.id}" />">
                    Edit </a>
            </display:column>
        </c:when>
        <c:otherwise>
            <display:column sortable="false" title="">
                Edit
            </display:column>
        </c:otherwise>
    </c:choose>

    <display:column sortable="true" title="Name">
        <a
                href="<%=request.getContextPath() %>/PMmodule/ProgramManagerView.do?id=<c:out value="${program.id}" />">
            <c:out value="${program.name}"/> </a>
    </display:column>
    <display:column property="description" sortable="true"
                    title="Description"/>
    <display:column property="type" sortable="true" title="Type"/>
    <display:column property="programStatus" sortable="true" title="Status"/>
    <display:column property="location" sortable="true" title="Location"/>
    <display:column sortable="true" title="Participation">
        <c:out value="${program.numOfMembers}"/>/<c:out
            value="${program.maxAllowed}"/>&nbsp;(<c:out
            value="${program.queueSize}"/> waiting)
    </display:column>
</display:table>
