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
<%@ include file="/WEB-INF/jsp/common/messages.jsp" %>
<script type="text/javascript" src="<%=request.getContextPath()%>/js/validation.js"></script>
<script type="text/javascript">

    //Check if string is a whole number(digits only).
    var isWhole_re = /^\s*\d+\s*$/;

    function isWhole(s) {
        var result = String(s).search(isWhole_re) != -1
        if (s.trim().length > 0 && !result) {
            alert('<%= SafeEncode.forJavaScript(oscarResources.getString("pmmodule.admin.editFacility.alertClientIdNumber")) %>');
        }
        return result;
    }

    function validateForm() {
        if (bCancel == true)
            return confirm('<%= SafeEncode.forJavaScript(oscarResources.getString("pmmodule.admin.editFacility.confirmCancel")) %>');
        var isOk = false;
        isOk = validateRequiredField('facilityName', 'Facility Name', 32);
        if (isOk) isOk = validateRequiredField('facilityDesc', 'Facility Description', 70);
        if (isOk) isOk = isWhole($("input[name='facility.assignNewVacancyTicklerDemographic']").val()) || $("input[name='facility.assignNewVacancyTicklerDemographic']").val() == '';

        return isOk;
    }
</script>
<!-- don't close in 1 statement, will break IE7 -->

<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@ taglib uri="carlos" prefix="carlos" %>

<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ page import="java.util.ResourceBundle" %>
<%
    java.util.ResourceBundle oscarResources = java.util.ResourceBundle.getBundle("oscarResources", request.getLocale());
%>
<%
    ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
%>

<div class="tabs" id="tabs">
    <table cellpadding="3" cellspacing="0" border="0">
        <tr>
            <fmt:message key="pmmodule.admin.editFacility.titleFacility" var="titleFacility"/>
<th title="${carlos:forHtmlAttribute(titleFacility)}"><fmt:message key="pmmodule.admin.editFacility.titleEditFacility"/></th>
        </tr>
    </table>
</div>

<form action="${pageContext.request.contextPath}/PMmodule/FacilityManager" method="post"
           onsubmit="return validateForm();">
    <input type="hidden" name="method" value="save"/>
    <table width="100%" border="1" cellspacing="2" cellpadding="3">
        <tr class="b">
            <td width="20%"><fmt:message key='pmmodule.admin.editFacility.tdFacilityId'/></td>
            <td>${carlos:forHtml(requestScope.id)}</td>

        </tr>
        <tr class="b">
            <td width="20%"><fmt:message key='pmmodule.admin.editFacility.tdNameReq'/></td>
            <td><input type="text" name="facility.name" size="32" maxlength="32"
                           id="facilityName" value="${carlos:forHtmlAttribute(facility.name)}"/></td>
        </tr>
        <tr class="b">
            <td width="20%"><fmt:message key='pmmodule.admin.editFacility.tdDescriptionReq'/></td>
            <td><input type="text" name="facility.description" size="70"
                           maxlength="70" id="facilityDesc" value="${carlos:forHtmlAttribute(facility.description)}"/></td>
        </tr>
        <tr class="b">
            <td width="20%"><fmt:message key='pmmodule.admin.editFacility.tdHic'/></td>
            <td><input type="checkbox" name="facility.hic" <c:if test="${facility.hic}">checked</c:if>/></td>
        </tr>
        <tr class="b">
            <td width="20%"><fmt:message key='pmmodule.admin.editFacility.tdPrimaryContactName'/></td>
            <td><input type="text" name="facility.contactName" id="facility.contactName" value="${carlos:forHtmlAttribute(facility.contactName)}"/></td>
        </tr>
        <tr class="b">
            <td width="20%"><fmt:message key='pmmodule.admin.editFacility.tdPrimaryContactEmail'/></td>
            <td><input type="text" name="facility.contactEmail" id="facility.contactEmail" value="${carlos:forHtmlAttribute(facility.contactEmail)}"/></td>
        </tr>
        <tr class="b">
            <td width="20%"><fmt:message key='pmmodule.admin.editFacility.tdPrimaryContactPhone'/></td>
            <td><input type="text" name="facility.contactPhone" id="facility.contactPhone" value="${carlos:forHtmlAttribute(facility.contactPhone)}"/></td>
        </tr>
        <%
            Integer orgId = (Integer) request.getAttribute("orgId");
            Integer sectorId = (Integer) request.getAttribute("sectorID");

        %>
        <tr class="b">
            <td width="20%"><fmt:message key='pmmodule.admin.editFacility.tdOrganization'/></td>
            <td><select name="facility.orgId">
                <option value="0">&nbsp;</option>
                <c:forEach var="org" items="${orgList}">
                    <c:choose>
                        <c:when test="${orgId == org.code }">
                            <option value="${carlos:forHtmlAttribute(org.code)}" selected>${carlos:forHtml(org.description)}</option>
                        </c:when>
                        <c:otherwise>
                            <option value="${carlos:forHtmlAttribute(org.code)}">${carlos:forHtml(org.description)}</option>
                        </c:otherwise>
                    </c:choose>
                </c:forEach>
            </select></td>
        </tr>
        <tr class="b">
            <td width="20%"><fmt:message key='pmmodule.admin.editFacility.tdSector'/></td>
            <td><select name="facility.sectorId">
                <option value="0">&nbsp;</option>
                <c:forEach var="sector" items="${sectorList}">
                    <c:choose>
                        <c:when test="${sectorId == sector.code }">
                            <option value="${carlos:forHtmlAttribute(sector.code)}" selected>${carlos:forHtml(sector.description)}</option>
                        </c:when>
                        <c:otherwise>
                            <option value="${carlos:forHtmlAttribute(sector.code)}">${carlos:forHtml(sector.description)}</option>
                        </c:otherwise>
                    </c:choose>
                </c:forEach>
            </select></td>
        </tr>
        <tr class="b">
            <td width="20%"><fmt:message key='pmmodule.admin.editFacility.tdEnableDigitalSignatures'/></td>
            <td><input type="checkbox" name="facility.enableDigitalSignatures" <c:if test="${facility.enableDigitalSignatures}">checked</c:if>/></td>
        </tr>
        <tr class="b">
            <td width="20%"><fmt:message key='pmmodule.admin.editFacility.tdEnableHealthNumberRegistry'/></td>
            <td><input type="checkbox" name="facility.enableHealthNumberRegistry" <c:if test="${facility.enableHealthNumberRegistry}">checked</c:if>/></td>
        </tr>
        <tr class="b">
            <td width="20%"><fmt:message key='pmmodule.admin.editFacility.tdEnableAnonymousClients'/></td>
            <td><input type="checkbox" name="facility.enableAnonymous" <c:if test="${facility.enableAnonymous}">checked</c:if>/></td>
        </tr>
        <tr class="b">
            <td width="20%"><fmt:message key='pmmodule.admin.editFacility.tdEnablePhoneEncounterClients'/></td>
            <td><input type="checkbox" name="facility.enablePhoneEncounter" <c:if test="${facility.enablePhoneEncounter}">checked</c:if>/></td>
        </tr>
        <tr class="b">
            <td width="20%"><fmt:message key='pmmodule.admin.editFacility.tdEnableGroupNotes'/></td>
            <td><input type="checkbox" name="facility.enableGroupNotes" <c:if test="${facility.enableGroupNotes}">checked</c:if>/></td>
        </tr>

        <tr class="b">
            <td width="20%"><fmt:message key='pmmodule.admin.editFacility.tdAssignVacancyWithdrawn'/></td>
            <td>
                <select name="facility.vacancyWithdrawnTicklerProvider" id="facility.vacancyWithdrawnTicklerProvider">
                    <option value=""><fmt:message key='pmmodule.admin.editFacility.tdSelectBelow'/></option>
                    <%for (Provider p : providerDao.getActiveProviders()) { %>
                    <option value="<%=p.getProviderNo() %>" <%= java.util.Objects.equals(p.getProviderNo(), String.valueOf(request.getAttribute("facility") != null ? ((io.github.carlos_emr.carlos.commn.model.Facility) request.getAttribute("facility")).getVacancyWithdrawnTicklerProvider() : null)) ? "selected" : "" %>><%=p.getFormattedName() %>
                    </option>
                    <% } %>
                </select>
                &nbsp;<fmt:message key='pmmodule.admin.editFacility.tdDefaultClientId'/>:&nbsp;
                <input type="text" name="facility.vacancyWithdrawnTicklerDemographic" id="facility.vacancyWithdrawnTicklerDemographic" value="${carlos:forHtmlAttribute(facility.vacancyWithdrawnTicklerDemographic)}" />
            </td>
        </tr>

        <tr class="b">
            <td width="20%"><fmt:message key='pmmodule.admin.editFacility.tdAssignNewVacancy'/></td>
            <td>
                <select name="facility.assignNewVacancyTicklerProvider" id="facility.assignNewVacancyTicklerProvider">
                    <option value=""><fmt:message key='pmmodule.admin.editFacility.tdSelectBelow'/></option>
                    <%for (Provider p : providerDao.getActiveProviders()) { %>
                    <option value="<%=p.getProviderNo() %>" <%= java.util.Objects.equals(p.getProviderNo(), String.valueOf(request.getAttribute("facility") != null ? ((io.github.carlos_emr.carlos.commn.model.Facility) request.getAttribute("facility")).getAssignNewVacancyTicklerProvider() : null)) ? "selected" : "" %>><%=p.getFormattedName() %>
                    </option>
                    <% } %>
                </select>
                &nbsp;<fmt:message key='pmmodule.admin.editFacility.tdDefaultClientId'/>:&nbsp;
                <input type="text" name="facility.assignNewVacancyTicklerDemographic" id="facility.assignNewVacancyTicklerDemographic" value="${carlos:forHtmlAttribute(facility.assignNewVacancyTicklerDemographic)}" />
            </td>
        </tr>

        <tr class="b">
            <td width="20%"><fmt:message key='pmmodule.admin.editFacility.tdAssignNotificationRejected'/></td>
            <td>
                <select name="facility.assignRejectedVacancyApplicant" id="facility.assignRejectedVacancyApplicant">
                    <option value=""><fmt:message key='pmmodule.admin.editFacility.tdSelectBelow'/></option>
                    <%for (Provider p : providerDao.getActiveProviders()) { %>
                    <option value="<%=p.getProviderNo() %>" <%= java.util.Objects.equals(p.getProviderNo(), String.valueOf(request.getAttribute("facility") != null ? ((io.github.carlos_emr.carlos.commn.model.Facility) request.getAttribute("facility")).getAssignRejectedVacancyApplicant() : null)) ? "selected" : "" %>><%=p.getFormattedName() %>
                    </option>
                    <% } %>
                </select>

            </td>
        </tr>

        <tr class="b">
            <td width="20%"><fmt:message key='pmmodule.admin.editFacility.tdRegistrationIntake'/></td>
            <td>
                <select name="facility.registrationIntake" id="facility.registrationIntake">
                    <option value="-1"><fmt:message key='pmmodule.admin.editFacility.tdNull'/></option>
                    <c:forEach var="registrationIntakeForm" items="${registrationIntakeForms}">
                        <option value="${carlos:forHtmlAttribute(registrationIntakeForm.id)}" <c:if test="${facility.registrationIntake == registrationIntakeForm.id}">selected</c:if>>
                            ${carlos:forHtml(registrationIntakeForm.formName)}
                        </option>
                    </c:forEach>
                </select>
            </td>
        </tr>
        <tr class="b">
            <td width="20%"><fmt:message key='pmmodule.admin.editFacility.tdDisplayAllVacancies'/></td>
            <td>
                <select name="facility.displayAllVacancies" id="facility.displayAllVacancies">
                    <option value="1" <c:if test="${facility.displayAllVacancies == 1}">selected</c:if>><fmt:message key='pmmodule.admin.editFacility.tdAllVacanciesInAllFacilities'/></option>
                    <option value="0" <c:if test="${facility.displayAllVacancies == 0}">selected</c:if>><fmt:message key='pmmodule.admin.editFacility.tdAllVacanciesInUsersFacilityProgramDomain'/></option>
                </select>
            </td>
        </tr>

        <tr class="b">
            <td width="20%"><fmt:message key='pmmodule.admin.editFacility.tdEnableMandatoryEncounter'/></td>
            <td><input type="checkbox" name="facility.enableEncounterTime" <c:if test="${facility.enableEncounterTime}">checked</c:if>/></td>
        </tr>
        <tr class="b">
            <td width="20%"><fmt:message key='pmmodule.admin.editFacility.tdEnableMandatoryTransportation'/></td>
            <td><input type="checkbox" name="facility.enableEncounterTransportationTime" <c:if test="${facility.enableEncounterTransportationTime}">checked</c:if>/></td>
        </tr>

        <tr class="b">
            <td width="20%"><fmt:message key='pmmodule.admin.editFacility.tdRxInteractionWarning'/></td>
            <td>
                <select name="facility.rxInteractionWarningLevel" id="facility.rxInteractionWarningLevel">
                    <option value="0" <c:if test="${facility.rxInteractionWarningLevel == 0}">selected</c:if>><fmt:message key='pmmodule.admin.editFacility.tdNotSpecified'/></option>
                    <option value="1" <c:if test="${facility.rxInteractionWarningLevel == 1}">selected</c:if>><fmt:message key='pmmodule.admin.editFacility.tdLow'/></option>
                    <option value="2" <c:if test="${facility.rxInteractionWarningLevel == 2}">selected</c:if>><fmt:message key='pmmodule.admin.editFacility.tdMedium'/></option>
                    <option value="3" <c:if test="${facility.rxInteractionWarningLevel == 3}">selected</c:if>><fmt:message key='pmmodule.admin.editFacility.tdHigh'/></option>
                    <option value="4" <c:if test="${facility.rxInteractionWarningLevel == 4}">selected</c:if>><fmt:message key='pmmodule.admin.editFacility.tdNone'/></option>
                </select>

            </td>
        </tr>

        <tr>
            <td colspan="2"><input type="submit" name="submit" value="<fmt:message key='pmmodule.admin.editFacility.btnSave'/>" onclick="bCancel=false;" />
                <button type="button" onclick="window.history.back();"><fmt:message key="pmmodule.admin.editFacility.btnCancel"/></button></td>
        </tr>
    </table>
</form>
<div>
    <p><a
            href="<%=request.getContextPath() %>/PMmodule/FacilityManager?method=list">Return
        to facilities list</a></p>
</div>
