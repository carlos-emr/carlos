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

--%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<style type="text/css">
    .ui-autocomplete-loading {
        background: white url('<%= request.getContextPath() %>/images/ui-anim_basic_16x16.gif') right center no-repeat;
    }

    .ui-autocomplete {
        max-height: 200px;
        overflow-y: auto;
        overflow-x: hidden;
        background-color: whitesmoke;
        border: #ccc thin solid;
    }

    .ui-menu .ui-menu {

        background-color: whitesmoke;
    }

    .ui-menu .ui-menu-item a {
        border-bottom: white thin solid;
    }

    .ui-menu .ui-menu-item a.ui-state-hover,
    .ui-menu .ui-menu-item a.ui-state-active {
        background-color: yellow;
    }

</style>
<link rel="stylesheet" type="text/css" href="${ oscar_context_path }/css/jquery.ui.autocomplete.css"/>
<script src="<%=request.getContextPath() %>/library/jquery/jquery-3.6.4.min.js"></script>
<script src="<%=request.getContextPath() %>/library/jquery/jquery-ui-1.12.1.min.js"></script>
<script type="text/javascript" src="${ oscar_context_path }/js/dxJSONCodeSearch.js"></script>

<table>
    <c:if test="${param.enableCodeSystemSelect == 'true'}">
        <tr>
            <td class="label"><label for="codingSystem">Disease Code System</label></td>
            <td>
                <select name="codingSystem" id="codingSystem">
                    <option value="icd9">icd9</option>
                        <%-- option value="limitUse">Limited Use</option --%>
                </select>
            </td>
        </tr>
    </c:if>
    <c:if test="${param.enableCodeSystemSelect == 'false'}">
        <%-- default is icd9 --%>
        <input type="hidden" name="codingSystem" id="codingSystem" value="icd9"/>
    </c:if>
    <tr>
        <td><label for="jsonDxSearch">Indication</label></td>
        <td>
            <input type="text" class="codeTxt" name="jsonDxSearch" id="jsonDxSearch"/>
        </td>
    </tr>

</table>
