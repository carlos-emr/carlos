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
<%@ include file="/WEB-INF/jsp/common/messages.jsp" %>
<c:url var="programListUri" value="/PMmodule/ProgramManager">
    <c:param name="method" value="list"/>
    <c:if test="${not empty param.searchStatus}">
        <c:param name="searchStatus" value="${param.searchStatus}"/>
    </c:if>
    <c:if test="${not empty param.searchType}">
        <c:param name="searchType" value="${param.searchType}"/>
    </c:if>
    <c:if test="${not empty param.searchFacilityId}">
        <c:param name="searchFacilityId" value="${param.searchFacilityId}"/>
    </c:if>
</c:url>
<script type="text/javascript">
    function confirmDelete(name) {
        return confirm("Are you sure you want to delete " + name + " ?");
    }
</script>

<div class="tabs" id="tabs">
    <table cellpadding="3" cellspacing="0" border="0">
        <tr>
            <th title="Programs">Programs</th>
        </tr>
    </table>
</div>

<form action="${pageContext.request.contextPath}/PMmodule/ProgramManager" method="get">
    <input type="hidden" name="method" value="list"/>
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
                <td>
                    <select name="searchStatus">
                        <option value="Any" <c:if test="${empty param.searchStatus || param.searchStatus == 'Any'}">selected</c:if>>Any</option>
                        <option value="active" <c:if test="${param.searchStatus == 'active'}">selected</c:if>>active</option>
                        <option value="inactive" <c:if test="${param.searchStatus == 'inactive'}">selected</c:if>>inactive</option>
                    </select>
                </td>
                <td>
                    <select name="searchType">
                        <option value="Any" <c:if test="${empty param.searchType || param.searchType == 'Any'}">selected</c:if>>Any</option>
                        <option value="Bed" <c:if test="${param.searchType == 'Bed'}">selected</c:if>>Bed</option>
                        <option value="Service" <c:if test="${param.searchType == 'Service'}">selected</c:if>>Service</option>
                        <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="false">
                            <option value="External" <c:if test="${param.searchType == 'External'}">selected</c:if>>External</option>
                            <option value="community" <c:if test="${param.searchType == 'community'}">selected</c:if>>Community</option>
                        </caisi:isModuleLoad>
                    </select>
                </td>
                <td>
                    <select name="searchFacilityId">
                        <option value="0" <c:if test="${empty param.searchFacilityId || param.searchFacilityId == '0'}">selected</c:if>>Any</option>
                        <c:forEach var="facility" items="${facilities}">
                            <option value="${e:forHtmlAttribute(facility.id)}" <c:if test="${param.searchFacilityId == facility.id.toString()}">selected</c:if>>
                                ${e:forHtml(facility.name)}
                            </option>
                        </c:forEach>
                    </select>
                </td>
                <td><input type="submit" value="Search"/></td>
            </tr>
        </tbody>
    </table>
</form>

<display:table class="simple" cellspacing="2" cellpadding="3"
               id="program" name="programs" export="false" pagesize="0"
               requestURI="${programListUri}">
    <display:setProperty name="paging.banner.placement" value="bottom"/>
    <display:setProperty name="paging.banner.item_name" value="program"/>
    <display:setProperty name="paging.banner.items_name" value="programs"/>
    <display:setProperty name="basic.msg.empty_list" value="No programs found."/>

    <display:column sortable="false" title="">
        <a href="${pageContext.request.contextPath}/PMmodule/ProgramManager?method=delete&amp;id=${e:forUriComponent(program.id)}&amp;name=${e:forUriComponent(program.name)}"
           onclick="return confirmDelete('${e:forJavaScript(program.nameJs)}');">Delete</a>
    </display:column>
    <display:column sortable="false" title="">
        <c:choose>
            <c:when test="${program.programStatus == 'active'}">
                <a href="${pageContext.request.contextPath}/PMmodule/ProgramManager?method=edit&amp;id=${e:forHtmlAttribute(program.id)}">Edit</a>
            </c:when>
            <c:otherwise>Edit</c:otherwise>
        </c:choose>
    </display:column>
    <display:column sortable="true" title="Name">
        <a href="${pageContext.request.contextPath}/PMmodule/ProgramManagerView?id=${e:forHtmlAttribute(program.id)}">
            ${e:forHtml(program.name)}
        </a>
    </display:column>
    <display:column property="description" sortable="true" title="Description"/>
    <display:column property="type" sortable="true" title="Type"/>
    <display:column property="programStatus" sortable="true" title="Status"/>
    <display:column property="location" sortable="true" title="Location"/>
    <display:column sortable="true" title="Participation">
        ${e:forHtml(program.numOfMembers)}/${e:forHtml(program.maxAllowed)}
        (${e:forHtml(program.queueSize)} waiting)
    </display:column>
</display:table>

<div>
    <p><a href="${pageContext.request.contextPath}/PMmodule/ProgramManager?method=add">Add new program</a></p>
</div>
