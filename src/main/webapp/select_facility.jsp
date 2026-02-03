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
<%@page import="io.github.carlos_emr.carlos.commn.dao.FacilityDao" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Facility" %>
<%@page import="java.util.List" %>
<%@page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@include file="/layouts/caisi_html_top.jspf" %>
<%@ page import="io.github.carlos_emr.carlos.login.Login2Action" %>
<h2>Please select which facility you would like to currently work in</h2>
<%
    FacilityDao facilityDao = (FacilityDao) SpringUtils.getBean(FacilityDao.class);
    ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);

    Provider provider = (Provider) session.getAttribute("provider");
    List<Integer> facilityIds = providerDao.getFacilityIds(provider.getProviderNo());
%>
<ul>
    <%
        for (Integer facilityId : facilityIds) {
            Facility facility = facilityDao.find(facilityId);
    %>
    <li>
        <a href='?nextPage=<%=request.getParameter("nextPage")%>&<%=Login2Action.SELECTED_FACILITY_ID%>=<%=facility.getId()%>'><%=facility.getName()%>
        </a></li>
    <%
        }
    %>
</ul>

<%@include file="/layouts/caisi_html_bottom.jspf" %>
