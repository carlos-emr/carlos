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
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<fmt:message key="admin.program.key.programs" var="titleVar0"/>

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
            <th title="${titleVar0}"><fmt:message key='admin.program.key.functional.users'/></th>
        </tr>
    </table>
</div>
<input type="hidden" name="function.id" id="function.id" value="${carlos:forHtmlAttribute(function.id)}"/>
<input type="hidden" name="function.providerNo" id="function.providerNo" value="${carlos:forHtmlAttribute(function.providerNo)}"/>
<display:table class="simple" cellspacing="2" cellpadding="3" id="functional" name="functional_users" export="false"
               pagesize="0" requestURI="${programManagerFunctionUsersUri}">
    <display:setProperty name="paging.banner.placement" value="bottom"/>
    <fmt:message key="admin.program.key.no.functional.users.defined.for.this.program" var="adminprogramkeynofunctionalusersdefinedforthisprogram"/>
	<display:setProperty name="basic.msg.empty_list"
		value="${adminprogramkeynofunctionalusersdefinedforthisprogram}" />
    <display:column sortable="false" title="">
        <a onclick="deleteFunctionalUser('${carlos:forJavaScript(functional.id)}');return false;" href="javascript:void(0);"><fmt:message key='admin.program.key.delete'/></a>
    </display:column>
    <display:column property="userType.name" sortable="true" titleKey="admin.program.key.functional.user.type"/>
    <display:column property="provider.formattedName" sortable="true" titleKey="admin.program.key.provider.name"/>
</display:table>
<br/>
<br/>
<table width="100%" border="1" cellspacing="2" cellpadding="3">
    <tr class="b">
        <td width="20%"><fmt:message key='admin.program.key.provider'/></td>
        <td>
            <%
                String providerName = (String) request.getAttribute("providerName");
                if (providerName == null) {
                    providerName = "";
                }
            %>
            <input type="text" name="providerName" size="30" value="<%=providerName%>"/>
            <input type="button" value="<fmt:message key='admin.program.button.search'/>" onclick="search_provider(this.form.providerName.value);"/>
        </td>
    </tr>
    <tr class="b">
        <td width="20%"><fmt:message key='admin.program.key.functional.user.type'/></td>
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
            <input type="button" value="<fmt:message key='admin.program.button.save'/>" onclick="add_functional_user(this.form)"/>
            <button type="button" onclick="window.history.back();"><fmt:message key='admin.program.key.cancel'/></button>
        </td>
    </tr>
</table>
