<%@ taglib uri="carlos" prefix="carlos" %>

<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ page import="java.util.ResourceBundle" %>
<%
    java.util.ResourceBundle oscarResources = java.util.ResourceBundle.getBundle("oscarResources", request.getLocale());
%>
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
<fmt:setBundle basename="oscarResources"/>

<script>
    function ConfirmDelete(name) {
        if (confirm('<%= SafeEncode.forJavaScript(oscarResources.getString("pmmodule.admin.listFacilities.confirmDelete")) %>' + " " + name + " ?")) {
            return true;
        }
        return false;
    }
</script>
<%@ include file="/WEB-INF/jsp/common/messages.jsp" %>
<div class="tabs" id="tabs">
    <table cellpadding="3" cellspacing="0" border="0">
        <tr>
            <fmt:message key="pmmodule.admin.listFacilities.titleFacilities" var="titleFacilities"/>
<th title="${carlos:forHtmlAttribute(titleFacilities)}"><fmt:message key="pmmodule.admin.listFacilities.thFacilitiesManagement"/></th>
        </tr>
    </table>
</div>
<form action="${pageContext.request.contextPath}/PMmodule/FacilityManager" method="post">
    <display:table class="simple" cellspacing="2" cellpadding="3"
                   id="facility" name="facilities" export="false" pagesize="0"
                   requestURI="/PMmodule/FacilityManager">
        <display:setProperty name="paging.banner.placement" value="bottom"/>
        <display:setProperty name="paging.banner.item_name" value="agency"/>
        <display:setProperty name="paging.banner.items_name"
                             value="facilities"/>
        <fmt:message key="pmmodule.admin.listFacilities.emptyList" var="emptyListMsg"/>
<display:setProperty name="basic.msg.empty_list" value="${emptyListMsg}"/>

        <display:column sortable="false" title="">
            <a
                    href="<%=request.getContextPath() %>/PMmodule/FacilityManager?method=view&id=${carlos:forUriComponent(facility.id)}"><fmt:message key="pmmodule.admin.listFacilities.aDetails"/></a>
        </display:column>
        <display:column sortable="false" title="">
            <a
                    href="<%=request.getContextPath() %>/PMmodule/FacilityManager?method=edit&id=${carlos:forUriComponent(facility.id)}"><fmt:message key="pmmodule.admin.listFacilities.aEdit"/></a>
        </display:column>
        <display:column sortable="false" title="">
            <a href="javascript:void(0)"
                    onclick="if(ConfirmDelete('${carlos:forJavaScript(facility.name)}')){document.getElementById('deleteForm_${carlos:forJavaScript(facility.id)}').submit()}"><fmt:message key="pmmodule.admin.listFacilities.aDisable"/></a>
            <form id="deleteForm_${carlos:forHtmlAttribute(facility.id)}" method="post"
                  action="<%=request.getContextPath() %>/PMmodule/FacilityManager" style="display:none">
                <input type="hidden" name="method" value="delete"/>
                <input type="hidden" name="id" value="${carlos:forHtmlAttribute(facility.id)}"/>
                <input type="hidden" name="name" value="${carlos:forHtmlAttribute(facility.name)}"/>
            </form>
        </display:column>


        <fmt:message key="pmmodule.admin.listFacilities.titleName" var="titleName"/>
<display:column property="name" sortable="true" title="${carlos:forHtmlAttribute(titleName)}"/>
        <fmt:message key="pmmodule.admin.listFacilities.titleDescription" var="titleDescription"/>
<display:column property="description" sortable="true"
                        title="${carlos:forHtmlAttribute(titleDescription)}"/>
        <fmt:message key="pmmodule.admin.listFacilities.titleContactName" var="titleContactName"/>
<display:column property="contactName" sortable="true"
                        title="${carlos:forHtmlAttribute(titleContactName)}"/>
        <fmt:message key="pmmodule.admin.listFacilities.titleHic" var="titleHic"/>
<display:column property="hic" sortable="true" title="${carlos:forHtmlAttribute(titleHic)}"/>
    </display:table>
</form>
<div>
    <p><a
            href="<%=request.getContextPath() %>/PMmodule/FacilityManager?method=add"><fmt:message key="pmmodule.admin.listFacilities.aAddNewFacility"/></a></p>
</div>
