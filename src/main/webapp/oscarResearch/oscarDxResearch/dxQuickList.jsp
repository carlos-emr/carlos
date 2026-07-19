<%--

    Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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

    This software was written for the
    Department of Family Medicine
    McMaster University
    Hamilton
    Ontario, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
<%--
Liberated by Dennis Warren @ Colcamex 
Created by RJ
Required Parameters to plug-in: 

	disabled : returns true or false.
	quickList : default quick list name by parameter

 --%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>


<table id="dxCodeQuicklist">
    <tr>
        <td class="heading">
            ${ quickList }
            <div class="panel panel-default">
                <div class="panel-body">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarResearch.oscarDxResearch.quickList"/>
                    <small class="pull-right">
                        <a class="oscar-dialog-link" href="${pageContext.request.contextPath}/oscarResearch/oscarDxResearch/dxResearchCustomization.jsp">
                            add/edit
                        </a>
                    </small>
                </div>
            </div>
        </td>
    </tr>
    <tr>
        <td class="quickList">
            <select class="form-control" style="overflow:auto" name="quickList"
                         onchange="javascript:changeList(this,'${ demographicNo }','${ providerNo }');">
                <c:forEach var="quickLists" items="${allQuickLists.dxQuickListBeanVector}">
                    <option value="${ quickLists.quickListName }" ${ quickLists.quickListName eq param.quickList || quickLists.lastUsed eq 'Selected' ? 'selected' : '' } >
                        <c:out value="${quickLists.quickListName}"/>
                    </option>
                </c:forEach>
            </select>
            <ul class="list-group">
                <c:forEach var="item" items="${allQuickListItems.dxQuickListItemsVector}">
                    <li class="list-group-item">
  					<span class="pull-right">
 						<a href="#" title="${ item.dxSearchCode }"
                                   onclick="javascript:submitform( '${ item.dxSearchCode }', '${ item.type }' )">
                            add
                        </a>
					</span>
                        <c:out value="${item.type}"/>:
                        <c:out value="${item.description}"/>
                    </li>
                </c:forEach>
            </ul>
        </td>
    </tr>
</table>

<script type="text/javascript">
    function changeList(quickList, demographicNo, providerNo) {
        location.href = 'setupDxResearch.do?demographicNo='
            + encodeURIComponent(demographicNo)
            + '&quickList='
            + encodeURIComponent(quickList.value)
            + '&providerNo='
            + encodeURIComponent(providerNo);
    }
</script>
