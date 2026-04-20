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

<%@ taglib uri="/struts-tags" prefix="s"%>
<%@ taglib uri="jakarta.tags.core" prefix="c"%> 
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt"%> 
<%@ taglib uri="http://www.oscar-emr.com/tags/integration" prefix="i"%> 
<%@ taglib uri="jakarta.tags.functions" prefix="fn"%>

<!-- Display field errors -->
<s:if test="hasFieldErrors()">
    <s:iterator value="fieldErrors">
        <s:iterator value="value">
            <div class="alert alert-danger">
                <p><s:property escapeHtml="false" /></p>
            </div>
        </s:iterator>
    </s:iterator>
</s:if>

<!-- Display action errors -->
<s:if test="hasActionErrors()">
    <s:iterator value="actionErrors">
        <div class="alert alert-danger">
            <p><s:property escapeHtml="false" /></p>
        </div>
    </s:iterator>
</s:if>

<!-- Display action messages -->
<s:if test="hasActionMessages()">
    <s:iterator value="actionMessages">
        <div class="alert alert-info">
            <p><s:property escapeHtml="false" /></p>
        </div>
    </s:iterator>
</s:if>
