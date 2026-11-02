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

<script>
    function ConfirmDelete(name) {
        if (confirm("Are you sure you want to delete " + name + " ?")) {
            return true;
        }
        return false;
    }
</script>
<%@ include file="/WEB-INF/jsp/common/messages.jsp" %>
<div class="tabs" id="tabs">
    <table cellpadding="3" cellspacing="0" border="0">
        <tr>
            <th title="Facilities">Facilities management</th>
        </tr>
    </table>
</div>
<form action="${pageContext.request.contextPath}/PMmodule/FacilityManager" method="post">
    <%@ include file="/WEB-INF/jsp/common/carlosTableAssets.jspf" %>
<table class="simple" cellspacing="2" cellpadding="3" id="facility" data-carlos-table data-page-size="0" data-default-sort="0" data-default-order="ascending">
<thead><tr>





        <th scope="col"></th>
        <th scope="col"></th>
        <th scope="col"></th>


        <th scope="col"><span data-table-sort>Name</span></th>
        <th scope="col"><span data-table-sort>Description</span></th>
        <th scope="col"><span data-table-sort>Contact name</span></th>
        <th scope="col"><span data-table-sort>HIC?</span></th>
    </tr></thead>
<tbody><c:forEach var="facility" items="${facilities}" varStatus="tableRowStatus"><tr class="${tableRowStatus.count % 2 == 0 ? 'even' : 'odd'}">





        <td>
            <a
                    href="<%=request.getContextPath() %>/PMmodule/FacilityManager?method=view&id=${carlos:forUriComponent(facility.id)}">
                Details </a>
        </td>
        <td>
            <a
                    href="<%=request.getContextPath() %>/PMmodule/FacilityManager?method=edit&id=${carlos:forUriComponent(facility.id)}">
                Edit </a>
        </td>
        <td>
            <a href="javascript:void(0)"
                    onclick="if(ConfirmDelete('${carlos:forJavaScript(facility.name)}')){document.getElementById('deleteForm_${carlos:forJavaScript(facility.id)}').submit()}">
                Disable </a>
            <form id="deleteForm_${carlos:forHtmlAttribute(facility.id)}" method="post"
                  action="<%=request.getContextPath() %>/PMmodule/FacilityManager" style="display:none">
                <input type="hidden" name="method" value="delete"/>
                <input type="hidden" name="id" value="${carlos:forHtmlAttribute(facility.id)}"/>
                <input type="hidden" name="name" value="${carlos:forHtmlAttribute(facility.name)}"/>
            </form>
        </td>


        <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(facility.name))}" data-sort-type="${carlos:tableSortType(facility.name)}"><c:out value="${facility.name}"/></td>
        <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(facility.description))}" data-sort-type="${carlos:tableSortType(facility.description)}"><c:out value="${facility.description}"/></td>
        <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(facility.contactName))}" data-sort-type="${carlos:tableSortType(facility.contactName)}"><c:out value="${facility.contactName}"/></td>
        <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(facility.hic))}" data-sort-type="${carlos:tableSortType(facility.hic)}"><c:out value="${facility.hic}"/></td>
    </tr></c:forEach></tbody>
</table>
<c:if test="${empty facilities}"><p class="table-empty">No facilities found.</p></c:if>
</form>
<div>
    <p><a
            href="<%=request.getContextPath() %>/PMmodule/FacilityManager?method=add">
        Add new facility </a></p>
</div>
