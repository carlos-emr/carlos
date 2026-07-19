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
<%@ include file="/common/messages.jsp" %>
<script type="text/javascript" src="<%=request.getContextPath()%>/js/validation.js"></script>
<script type="text/javascript">

    //Check if string is a whole number(digits only).
    var isWhole_re = /^\s*\d+\s*$/;

    function isWhole(s) {
        var result = String(s).search(isWhole_re) != -1
        if (s.trim().length > 0 && !result) {
            alert('Default Client ID must be a number');
        }
        return result;
    }

    function validateForm() {
        if (bCancel == true)
            return confirm("Do you really want to Cancel?");
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
<%
    ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
%>

<div class="tabs" id="tabs">
    <table cellpadding="3" cellspacing="0" border="0">
        <tr>
            <th title="Facility">Edit facility</th>
        </tr>
    </table>
</div>

<form action="${pageContext.request.contextPath}/PMmodule/FacilityManager.do" method="post"
           onsubmit="return validateForm();">
    <input type="hidden" name="method" value="save"/>
    <table width="100%" border="1" cellspacing="2" cellpadding="3">
        <tr class="b">
            <td width="20%">Facility Id:</td>
            <td><c:out value="${requestScope.id}"/></td>

        </tr>
        <tr class="b">
            <td width="20%">Name: *</td>
            <td><input type="text" name="facility.name" size="32" maxlength="32"
                           id="facilityName"/></td>
        </tr>
        <tr class="b">
            <td width="20%">Description: *</td>
            <td><input type="text" name="facility.description" size="70"
                           maxlength="70" id="facilityDesc"/></td>
        </tr>
        <tr class="b">
            <td width="20%">HIC:</td>
            <td><input type="checkbox" name="facility.hic"/></td>
        </tr>
        <tr class="b">
            <td width="20%">Primary Contact Name:</td>
            <td><input type="text" name="facility.contactName" id="facility.contactName" /></td>
        </tr>
        <tr class="b">
            <td width="20%">Primary Contact Email:</td>
            <td><input type="text" name="facility.contactEmail" id="facility.contactEmail" /></td>
        </tr>
        <tr class="b">
            <td width="20%">Primary Contact Phone:</td>
            <td><input type="text" name="facility.contactPhone" id="facility.contactPhone" /></td>
        </tr>
        <%
            Integer orgId = (Integer) request.getAttribute("orgId");
            Integer sectorId = (Integer) request.getAttribute("sectorID");

        %>
        <tr class="b">
            <td width="20%">Organization:</td>
            <td><select name="facility.orgId">
                <option value="0">&nbsp;</option>
                <c:forEach var="org" items="${orgList}">
                    <c:choose>
                        <c:when test="${orgId == org.code }">
                            <option value="<c:out value="${org.code}"/>" selected><c:out
                                    value="${org.description}"/></option>
                        </c:when>
                        <c:otherwise>
                            <option value="<c:out value="${org.code}"/>"><c:out
                                    value="${org.description}"/></option>
                        </c:otherwise>
                    </c:choose>
                </c:forEach>
            </select></td>
        </tr>
        <tr class="b">
            <td width="20%">Sector:</td>
            <td><select name="facility.sectorId">
                <option value="0">&nbsp;</option>
                <c:forEach var="sector" items="${sectorList}">
                    <c:choose>
                        <c:when test="${sectorId == sector.code }">
                            <option value="<c:out value="${sector.code}"/>" selected><c:out
                                    value="${sector.description}"/></option>
                        </c:when>
                        <c:otherwise>
                            <option value="<c:out value="${sector.code}"/>"><c:out
                                    value="${sector.description}"/></option>
                        </c:otherwise>
                    </c:choose>
                </c:forEach>
            </select></td>
        </tr>
        <tr class="b">
            <td width="20%">Enable Digital Signatures:</td>
            <td><input type="checkbox" name="facility.enableDigitalSignatures"/></td>
        </tr>
        <tr class="b">
            <td width="20%">Enable Integrator:</td>
            <td><input type="checkbox" name="facility.integratorEnabled"/></td>
        </tr>
        <tr class="b">
            <td width="20%">Integrator Url:</td>
            <td><input type="text" name="facility.integratorUrl" id="facility.integratorUrl" /></td>
        </tr>
        <tr class="b">
            <td width="20%">Integrator User:</td>
            <td><input type="text" name="facility.integratorUser" id="facility.integratorUser" /></td>
        </tr>
        <tr class="b">
            <td width="20%">Integrator Password:</td>
            <td><input type="password" name="integratorPassword" id="integratorPassword"/></td>
        </tr>
        <tr class="b">
            <td width="20%">Allow SIMS Integration:</td>
            <td><input type="checkbox" name="facility.allowSims"/></td>
        </tr>
        <tr class="b">
            <td width="20%">Enable Integrated Referrals:</td>
            <td><input type="checkbox" name="facility.enableIntegratedReferrals"/></td>
        </tr>
        <tr class="b">
            <td width="20%">Enable Health Number Registry:</td>
            <td><input type="checkbox" name="facility.enableHealthNumberRegistry"/></td>
        </tr>
        <tr class="b">
            <td width="20%">Enable Anonymous Clients:</td>
            <td><input type="checkbox" name="facility.enableAnonymous"/></td>
        </tr>
        <tr class="b">
            <td width="20%">Enable Phone Encounter Clients:</td>
            <td><input type="checkbox" name="facility.enablePhoneEncounter"/></td>
        </tr>
        <tr class="b">
            <td width="20%">Enable Group Notes:</td>
            <td><input type="checkbox" name="facility.enableGroupNotes"/></td>
        </tr>

        <tr class="b">
            <td width="20%">Assign vacancy withdrawn tickler notification:</td>
            <td>
                <select name="facility.vacancyWithdrawnTicklerProvider" id="facility.vacancyWithdrawnTicklerProvider">
                    <option value="">Select Below</option>
                    <%for (Provider p : providerDao.getActiveProviders()) { %>
                    <option value="<%=p.getProviderNo() %>"><%=p.getFormattedName() %>
                    </option>
                    <% } %>
                </select>
                &nbsp;Default client ID:&nbsp;
                <input type="text" name="facility.vacancyWithdrawnTicklerDemographic" id="facility.vacancyWithdrawnTicklerDemographic" />
            </td>
        </tr>

        <tr class="b">
            <td width="20%">Assign new vacancy tickler notification to:</td>
            <td>
                <select name="facility.assignNewVacancyTicklerProvider" id="facility.assignNewVacancyTicklerProvider">
                    <option value="">Select Below</option>
                    <%for (Provider p : providerDao.getActiveProviders()) { %>
                    <option value="<%=p.getProviderNo() %>"><%=p.getFormattedName() %>
                    </option>
                    <% } %>
                </select>
                &nbsp;Default client ID:&nbsp;
                <input type="text" name="facility.assignNewVacancyTicklerDemographic" id="facility.assignNewVacancyTicklerDemographic" />
            </td>
        </tr>

        <tr class="b">
            <td width="20%">Assign notification of rejected applicant from a vacancy:</td>
            <td>
                <select name="facility.assignRejectedVacancyApplicant" id="facility.assignRejectedVacancyApplicant">
                    <option value="">Select Below</option>
                    <%for (Provider p : providerDao.getActiveProviders()) { %>
                    <option value="<%=p.getProviderNo() %>"><%=p.getFormattedName() %>
                    </option>
                    <% } %>
                </select>

            </td>
        </tr>

        <tr class="b">
            <td width="20%">Registration Intake</td>
            <td>
                <select name="facility.registrationIntake" id="facility.registrationIntake">
                    <option value="-1">Null</option>
                    <optionsCollection property="registrationIntakeForms" label="formName" value="id"/>
                </select>
            </td>
        </tr>
        <tr class="b">
            <td width="20%">Display All vacancies</td>
            <td>
                <select name="facility.displayAllVacancies" id="facility.displayAllVacancies">
                    <option value="1">All vacancies in all facilities</option>
                    <option value="0">All vacancies in users facility program domain</option>
                </select>
            </td>
        </tr>

        <tr class="b">
            <td width="20%">Enable Mandatory Encounter Time in Encounter:</td>
            <td><input type="checkbox" name="facility.enableEncounterTime"/></td>
        </tr>
        <tr class="b">
            <td width="20%">Enable Mandatory Transportation Time in Encounter:</td>
            <td><input type="checkbox" name="facility.enableEncounterTransportationTime"/></td>
        </tr>

        <tr class="b">
            <td width="20%">Rx Interaction Warning Level:</td>
            <td>
                <select name="facility.rxInteractionWarningLevel" id="facility.rxInteractionWarningLevel">
                    <option value="0">Not Specified</option>
                    <option value="1">Low</option>
                    <option value="2">Medium</option>
                    <option value="3">High</option>
                    <option value="4">None</option>
                </select>

            </td>
        </tr>

        <tr>
            <td colspan="2"><input type="submit" name="submit" value="Save" onclick="bCancel=false;" />
                <button type="button" onclick="window.history.back();">Cancel</button></td>
        </tr>
    </table>
</form>
<div>
    <p><a
            href="<%=request.getContextPath() %>/PMmodule/FacilityManager.do?method=list">Return
        to facilities list</a></p>
</div>
