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

<%@ include file="/taglibs.jsp" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.DemographicContact" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%
    String id = request.getParameter("id");
    ProviderDao providerDao = (ProviderDao) SpringUtils.getBean(ProviderDao.class);
    request.setAttribute("providers", providerDao.getActiveProviders());
%>

<div id="procontact_<%= Encode.forHtmlAttribute(id) %>">
    <input type="hidden" name="procontact_<%= Encode.forHtmlAttribute(id) %>.id" id="procontact_<%= Encode.forHtmlAttribute(id) %>.id" value=""/>

    <a href="#" onclick="deleteProContact(<%= Encode.forJavaScriptAttribute(id) %>);">[Delete]</a>

    &nbsp;

    <select name="procontact_<%= Encode.forHtmlAttribute(id) %>.role" id="procontact_<%= Encode.forHtmlAttribute(id) %>.role">
        <option value="Referring Doctor">Referring Doctor</option>
        <option value="Family Doctor">Family Doctor</option>
        <option value="Specialist">Specialist</option>
        <option value="Dietician">Dietician</option>
    </select>

    &nbsp;

    <select name="procontact_<%= Encode.forHtmlAttribute(id) %>.consentToContact" id="procontact_<%= Encode.forHtmlAttribute(id) %>.consentToContact"
            title="Consent to Contact">
        <option value="1">Consent</option>
        <option value="0">No Consent</option>
    </select>

    &nbsp;

    <select name="procontact_<%= Encode.forHtmlAttribute(id) %>.active" id="procontact_<%= Encode.forHtmlAttribute(id) %>.active" title="Active">
        <option value="1">Active</option>
        <option value="0">Inactive</option>
    </select>

    &nbsp;

    <!--  they can be an internal (Demographic) or external (Contact) contact -->

    <select name="procontact_<%= Encode.forHtmlAttribute(id) %>.type" id="procontact_<%= Encode.forHtmlAttribute(id) %>.type">
        <option value="<%=DemographicContact.TYPE_PROVIDER%>">Internal</option>
        <%if (CarlosProperties.getInstance().getProperty("NEW_CONTACTS_UI_EXTERNAL_CONTACT", "true").equals("true")) { %>
        <option value="<%=DemographicContact.TYPE_CONTACT%>">External</option>
        <% } %>
        <option value="<%=DemographicContact.TYPE_PROFESSIONALSPECIALIST%>"
        ">Professional Specialist</option>
    </select>

    &nbsp;

    <input type="hidden" name="procontact_<%= Encode.forHtmlAttribute(id) %>.contactId" value="0"/>
    <input type="text" name="procontact_<%= Encode.forHtmlAttribute(id) %>.contactName" id="procontact_<%= Encode.forHtmlAttribute(id) %>.contactName" size="20"
           readonly="readonly"/>
    <a href="#" onclick="doProfessionalSearch('<%= Encode.forJavaScriptAttribute(id) %>');return false;"><%= Encode.forHtml(request.getParameter("search") != null ? request.getParameter("search") : "") %></a>
</div>
