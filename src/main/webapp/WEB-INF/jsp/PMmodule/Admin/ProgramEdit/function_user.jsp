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
<c:url var="programManagerFunctionUsersUri" value="/PMmodule/ProgramManager">
    <c:param name="method" value="edit"/>
    <c:param name="id" value="${requestScope.id}"/>
    <c:param name="view.tab" value="Function User"/>
</c:url>
<script>
    function search_provider(name) {
        var url = '<%=request.getContextPath() %>/PMmodule/ProviderSearch';
        url += '?q=' + name;
        url += '&formName=programManagerForm';
        url += '&formElementId=function.providerNo';
        url += '&formElementName=providerName';

        window.open(url, 'provider_search', 'width=500, height=400, scrollbars=yes');
    }

    function deleteFunctionalUser(id) {
        if (!confirm("Are you sure you want to delete the functional user entry?")) {
            return;
        }
        document.programManagerForm.elements['function.id'].value = id;
        document.programManagerForm.method.value = 'delete_function';
        document.programManagerForm.submit();
    }

    function editFunctionalUser(id) {
        document.programManagerForm.elements['function.id'].value = id;
        document.programManagerForm.method.value = 'edit_function';
        document.programManagerForm.submit();
    }

    function add_functional_user(form) {
        alert('temporarily disabled');
        return false;
    }
</script>
<div class="tabs">
    <table cellpadding="3" cellspacing="0" border="0">
        <tr>
            <th title="Programs">Functional Users</th>
        </tr>
    </table>
</div>
<input type="hidden" name="function.id" id="function.id" value="${carlos:forHtmlAttribute(function.id)}"/>
<input type="hidden" name="function.providerNo" id="function.providerNo" value="${carlos:forHtmlAttribute(function.providerNo)}"/>
<%@ include file="/WEB-INF/jsp/common/carlosTableAssets.jspf" %>
<table class="simple" cellspacing="2" cellpadding="3" id="functional" data-carlos-table data-page-size="0" data-default-sort="0" data-default-order="ascending">
<thead><tr>


    <th scope="col"></th>
    <th scope="col"><span data-table-sort>Functional User Type</span></th>
    <th scope="col"><span data-table-sort>Provider Name</span></th>
</tr></thead>
<tbody><c:forEach var="functional" items="${functional_users}" varStatus="tableRowStatus"><tr class="${tableRowStatus.count % 2 == 0 ? 'even' : 'odd'}">


    <td>
        <a onclick="deleteFunctionalUser('${carlos:forJavaScript(functional.id)}');return false;" href="javascript:void(0);">
            Delete </a>
    </td>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(functional.userType.name))}" data-sort-type="${carlos:tableSortType(functional.userType.name)}"><c:out value="${functional.userType.name}"/></td>
    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(functional.provider.formattedName))}" data-sort-type="${carlos:tableSortType(functional.provider.formattedName)}"><c:out value="${functional.provider.formattedName}"/></td>
</tr></c:forEach></tbody>
</table>
<c:if test="${empty functional_users}"><p class="table-empty">No functional users defined for this program</p></c:if>
<br/>
<br/>
<table width="100%" border="1" cellspacing="2" cellpadding="3">
    <tr class="b">
        <td width="20%">Provider :</td>
        <td>
            <%
                String providerName = (String) request.getAttribute("providerName");
                if (providerName == null) {
                    providerName = "";
                }
            %>
            <input type="text" name="providerName" size="30" value="<%=providerName%>"/>
            <input type="button" value="Search" onclick="search_provider(this.form.providerName.value);"/>
        </td>
    </tr>
    <tr class="b">
        <td width="20%">Functional User Type:</td>
        <td>
            <select name="function.userTypeId" id="function.userTypeId">
                <c:forEach var="functionalUserType" items="${functionalUserTypes}">
                    <option value="${functionalUserType.id}">
                        ${functionalUserType.name}
                    </option>
                </c:forEach>
            </select>
        </td>
    </tr>
    <tr>
        <td colspan="2">
            <input type="button" value="Save" onclick="add_functional_user(this.form)"/>
            <button type="button" onclick="window.history.back();">Cancel</button>
        </td>
    </tr>
</table>
