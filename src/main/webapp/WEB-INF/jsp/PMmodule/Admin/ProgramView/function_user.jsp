<%@ taglib uri="carlos" prefix="carlos" %>
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
<c:url var="programManagerViewFunctionUsersUri" value="/PMmodule/ProgramManagerView">
    <c:param name="id" value="${requestScope.id}"/>
    <c:param name="tab" value="Function User"/>
</c:url>
<div class="tabs" id="tabs">
    <table cellpadding="3" cellspacing="0" border="0">
        <tr>
            <th title="Programs">Function User</th>
        </tr>
    </table>
</div>
<c:if test="${not empty functional_users}">
<table class="carlos-table-spaced simple" id="functional" data-carlos-table data-page-size="0" data-default-sort="0" data-default-order="ascending">
<thead><tr>


    <th scope="col"><span data-table-sort>User Type</span></th>
    <th scope="col"><span data-table-sort>Provider Name</span></th>
</tr></thead>
<tbody><c:forEach var="functional" items="${functional_users}" varStatus="tableRowStatus"><tr class="${tableRowStatus.count % 2 == 0 ? 'even' : 'odd'}">


    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(functional.userType.name))}" data-sort-type="${carlos:tableSortType(functional.userType.name)}">${carlos:forHtml(functional.userType.name)}</td>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(functional.provider.formattedName))}" data-sort-type="${carlos:tableSortType(functional.provider.formattedName)}">${carlos:forHtml(functional.provider.formattedName)}</td>
</tr></c:forEach></tbody>
</table>
</c:if>
<c:if test="${empty functional_users}"><p class="table-empty">No functional users defined for this program</p></c:if>
