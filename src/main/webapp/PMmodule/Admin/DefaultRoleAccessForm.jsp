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

<div class="tabs" id="tabs">
    <table cellpadding="3" cellspacing="0" border="0">
        <tr>
            <th title="Programs">Access</th>
        </tr>
    </table>
</div>

<form action="${pageContext.request.contextPath}/PMmodule/Admin/DefaultRoleAccess.do" method="post">
    <input type="hidden" name="method" value="save"/>
    <input type="hidden" name="id" id="id"/>

    <table width="100%" border="1" cellspacing="2" cellpadding="3"
           class="b">
        <tr class="b">
            <td width="20%">Role:</td>
            <td><select name="form.roleId" id="form.roleId">
                <c:forEach var="role" items="${roles}">
                    <option value="${role.id}">
                            ${role.name}
                    </option>
                </c:forEach>
            </select></td>
        </tr>

        <tr class="b">
            <td width="20%">Access Type:</td>
            <td><select name="form.accessTypeId" id="form.accessTypeId">
                <c:forEach var="access_type" items="${access_types}">
                    <option value="${access_type.id}">
                            ${access_type.name}
                    </option>
                </c:forEach>
            </select></td>
        </tr>

        <tr>
            <td colspan="2"><input type="submit" value="Save" /> <input
                    type="button" value="Cancel"
                    onclick="location.href='<%=request.getContextPath() %>/PMmodule/Admin/DefaultRoleAccess'"/>
            </td>
        </tr>
    </table>
</form>
