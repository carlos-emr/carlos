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
Purpose: Render a new professional-contact association row in the contact editor.
Features: Provider/contact selection, role, consent, active status and notes.
Parameters: id identifies the form row; the hidden association ID starts at zero
            until Contact2Action.saveManage persists the new association.
@since 2026.08 new-association ID contract
--%>

<%@ include file="/taglibs.jsp" %>
<fmt:setBundle basename="oscarResources"/>
<fmt:message key="demographic.procontact.title.consentToContact" var="procontactConsentTitle"/>
<fmt:message key="demographic.procontact.title.active" var="procontactActiveTitle"/>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.DemographicContact" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    String id = request.getParameter("id");
    ProviderDao providerDao = (ProviderDao) SpringUtils.getBean(ProviderDao.class);
    request.setAttribute("providers", providerDao.getActiveProviders());
%>

<div id="procontact_${carlos:forHtmlAttribute(param.id)}">
    <input type="hidden" name="procontact_${carlos:forHtmlAttribute(param.id)}.id" id="procontact_${carlos:forHtmlAttribute(param.id)}.id" value="0"/>

    <a href="#" onclick="deleteProContact(<carlos:encode value='<%= id %>' context="javaScriptAttribute"/>);">[<fmt:message key="global.btnDelete"/>]</a>

    &nbsp;

    <select name="procontact_${carlos:forHtmlAttribute(param.id)}.role" id="procontact_${carlos:forHtmlAttribute(param.id)}.role">
        <option value="Referring Doctor"><fmt:message key="demographic.procontact.role.referringDoctor"/></option>
        <option value="Family Doctor"><fmt:message key="demographic.procontact.role.familyDoctor"/></option>
        <option value="Specialist"><fmt:message key="demographic.procontact.role.specialist"/></option>
        <option value="Dietician"><fmt:message key="demographic.procontact.role.dietician"/></option>
    </select>

    &nbsp;

    <select name="procontact_${carlos:forHtmlAttribute(param.id)}.consentToContact" id="procontact_${carlos:forHtmlAttribute(param.id)}.consentToContact"
            title="${procontactConsentTitle}">
        <option value="1"><fmt:message key="demographic.procontact.consent"/></option>
        <option value="0"><fmt:message key="demographic.procontact.noConsent"/></option>
    </select>

    &nbsp;

    <select name="procontact_${carlos:forHtmlAttribute(param.id)}.active" id="procontact_${carlos:forHtmlAttribute(param.id)}.active" title="${procontactActiveTitle}">
        <option value="1"><fmt:message key="demographic.procontact.active"/></option>
        <option value="0"><fmt:message key="demographic.procontact.inactive"/></option>
    </select>

    &nbsp;

    <!--  they can be an internal (Demographic) or external (Contact) contact -->

    <select name="procontact_${carlos:forHtmlAttribute(param.id)}.type" id="procontact_${carlos:forHtmlAttribute(param.id)}.type">
        <option value="<%=DemographicContact.TYPE_PROVIDER%>"><fmt:message key="demographic.procontact.internal"/></option>
        <%if (CarlosProperties.getInstance().getProperty("NEW_CONTACTS_UI_EXTERNAL_CONTACT", "true").equals("true")) { %>
        <option value="<%=DemographicContact.TYPE_CONTACT%>"><fmt:message key="demographic.procontact.external"/></option>
        <% } %>
        <option value="<%=DemographicContact.TYPE_PROFESSIONALSPECIALIST%>"
        "><fmt:message key="demographic.procontact.professionalSpecialist"/></option>
    </select>

    &nbsp;

    <input type="hidden" name="procontact_${carlos:forHtmlAttribute(param.id)}.contactId" value="0"/>
    <input type="text" name="procontact_${carlos:forHtmlAttribute(param.id)}.contactName" id="procontact_${carlos:forHtmlAttribute(param.id)}.contactName" size="20"
           readonly="readonly"/>
    <a href="#" onclick="doProfessionalSearch('<carlos:encode value='<%= id %>' context="javaScriptAttribute"/>');return false;"><carlos:encode value='<%= request.getParameter("search") != null ? request.getParameter("search") : "" %>' context="html"/></a><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
</div>
