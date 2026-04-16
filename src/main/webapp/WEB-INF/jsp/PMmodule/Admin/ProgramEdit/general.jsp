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
<script type="text/javascript">
    function saveProgram() {
        var maxAllowed = document.getElementById("program-maxAllowed").value;
        if (isNaN(maxAllowed) || Number(maxAllowed) <= 0) {
            alert("Maximum participants must be a positive integer");
            return false;
        }
        var programName = document.getElementById("program-name").value;
        if (!programName || !programName.trim()) {
            alert("The program name can not be blank.");
            return false;
        }
        return true;
    }

    function openProgramSignatures(id) {
        if (!id) {
            return false;
        }
        window.open(
            "<c:url value='/PMmodule/ProgramManager'/>?method=programSignatures&programId=" + encodeURIComponent(id),
            "signature",
            "width=600,height=600,scrollbars=1"
        );
        return false;
    }
</script>

<input type="hidden" name="method" value="save"/>
    <input type="hidden" name="view.tab" value="General"/>
    <input type="hidden" name="program.id" value="<c:out value="${program.id}"/>"/>
    <input type="hidden" name="program.numOfMembers" value="<c:out value="${empty program.numOfMembers ? 0 : program.numOfMembers}"/>"/>

    <input type="hidden" name="old_maxAllowed" value="<c:out value="${empty program.maxAllowed ? 0 : program.maxAllowed}"/>"/>
    <input type="hidden" name="old_name" value="<c:out value="${program.name}"/>"/>
    <input type="hidden" name="old_descr" value="<c:out value="${program.description}"/>"/>
    <input type="hidden" name="old_type" value="<c:out value="${program.type}"/>"/>
    <input type="hidden" name="old_address" value="<c:out value="${program.address}"/>"/>
    <input type="hidden" name="old_phone" value="<c:out value="${program.phone}"/>"/>
    <input type="hidden" name="old_fax" value="<c:out value="${program.fax}"/>"/>
    <input type="hidden" name="old_url" value="<c:out value="${program.url}"/>"/>
    <input type="hidden" name="old_email" value="<c:out value="${program.email}"/>"/>
    <input type="hidden" name="old_emergencyNumber" value="<c:out value="${program.emergencyNumber}"/>"/>
    <input type="hidden" name="old_location" value="<c:out value="${program.location}"/>"/>
    <input type="hidden" name="old_programStatus" value="<c:out value="${program.programStatus}"/>"/>
    <input type="hidden" name="old_manOrWoman" value="<c:out value="${program.manOrWoman}"/>"/>
    <input type="hidden" name="old_abstinenceSupport" value="<c:out value="${program.abstinenceSupport}"/>"/>
    <input type="hidden" name="old_exclusiveView" value="<c:out value="${program.exclusiveView}"/>"/>
    <input type="hidden" name="old_holdingTank" value="<c:out value="${program.holdingTank}"/>"/>
    <input type="hidden" name="old_allowBatchAdmission" value="<c:out value="${program.allowBatchAdmission}"/>"/>
    <input type="hidden" name="old_allowBatchDischarge" value="<c:out value="${program.allowBatchDischarge}"/>"/>
    <input type="hidden" name="old_hic" value="<c:out value="${program.hic}"/>"/>
    <input type="hidden" name="old_transgender" value="<c:out value="${program.transgender}"/>"/>
    <input type="hidden" name="old_firstNation" value="<c:out value="${program.firstNation}"/>"/>
    <input type="hidden" name="old_alcohol" value="<c:out value="${program.alcohol}"/>"/>
    <input type="hidden" name="old_physicalHealth" value="<c:out value="${program.physicalHealth}"/>"/>
    <input type="hidden" name="old_mentalHealth" value="<c:out value="${program.mentalHealth}"/>"/>
    <input type="hidden" name="old_housing" value="<c:out value="${program.housing}"/>"/>
    <input type="hidden" name="old_facility_id" value="<c:out value="${empty program.facilityId ? 0 : program.facilityId}"/>"/>
    <input type="hidden" name="old_enableEncounterTime" value="<c:out value="${program.enableEncounterTime}"/>"/>
    <input type="hidden" name="old_enableEncounterTransportationTime" value="<c:out value="${program.enableEncounterTransportationTime}"/>"/>

    <div class="tabs">
        <table cellpadding="3" cellspacing="0" border="0">
            <tr>
                <th title="Programs">General Information</th>
            </tr>
        </table>
    </div>
    <table width="100%" border="1" cellspacing="2" cellpadding="3">
        <tr class="b">
            <td width="20%">Name:</td>
            <td><input type="text" name="program.name" id="program-name" size="30" maxlength="70" value="<c:out value="${program.name}"/>"/></td>
        </tr>
        <tr class="b">
            <td width="20%">Facility:</td>
            <td>
                <select name="program.facilityId">
                    <c:forEach var="facility" items="${facilities}">
                        <option value="<c:out value="${facility.id}"/>" <c:if test="${program.facilityId == facility.id}">selected</c:if>>
                            <c:out value="${facility.name}"/>
                        </option>
                    </c:forEach>
                </select>
            </td>
        </tr>
        <tr class="b">
            <td width="20%">Description:</td>
            <td><input type="text" name="program.description" size="30" maxlength="255" value="<c:out value="${program.description}"/>"/></td>
        </tr>
        <tr class="b">
            <td width="20%">Functional Centre:</td>
            <td>
                <select name="program.functionalCentreId">
                    <option value="">&nbsp;</option>
                    <c:forEach var="functionalCentre" items="${functionalCentres}">
                        <option value="<c:out value="${functionalCentre.accountId}"/>" <c:if test="${program.functionalCentreId == functionalCentre.accountId}">selected</c:if>>
                            <c:out value="${functionalCentre.accountId}"/>, <c:out value="${functionalCentre.description}"/>
                        </option>
                    </c:forEach>
                </select>
            </td>
        </tr>
        <tr class="b">
            <td width="20%">HIC:</td>
            <td><input type="checkbox" name="program.hic" <c:if test="${program.hic}">checked</c:if>/></td>
        </tr>
        <tr class="b">
            <td width="20%">Address:</td>
            <td><input type="text" name="program.address" size="30" maxlength="255" value="<c:out value="${program.address}"/>"/></td>
        </tr>
        <tr class="b">
            <td width="20%">Phone:</td>
            <td><input type="text" name="program.phone" size="30" maxlength="25" value="<c:out value="${program.phone}"/>"/></td>
        </tr>
        <tr class="b">
            <td width="20%">Fax:</td>
            <td><input type="text" name="program.fax" size="30" maxlength="25" value="<c:out value="${program.fax}"/>"/></td>
        </tr>
        <tr class="b">
            <td width="20%">URL:</td>
            <td><input type="text" name="program.url" size="30" maxlength="100" value="<c:out value="${program.url}"/>"/></td>
        </tr>
        <tr class="b">
            <td width="20%">Email:</td>
            <td><input type="text" name="program.email" size="30" maxlength="50" value="<c:out value="${program.email}"/>"/></td>
        </tr>
        <tr class="b">
            <td width="20%">Emergency Number:</td>
            <td><input type="text" name="program.emergencyNumber" size="30" maxlength="25" value="<c:out value="${program.emergencyNumber}"/>"/></td>
        </tr>
        <tr class="b">
            <td width="20%">Type:</td>
            <td>
                <select name="program.type">
                    <option value="Bed" <c:if test="${program.type == 'Bed'}">selected</c:if>>Bed</option>
                    <option value="Service" <c:if test="${program.type == 'Service'}">selected</c:if>>Service</option>
                    <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="false">
                        <option value="External" <c:if test="${program.type == 'External'}">selected</c:if>>External</option>
                        <option value="community" <c:if test="${program.type == 'community'}">selected</c:if>>Community</option>
                    </caisi:isModuleLoad>
                </select>
            </td>
        </tr>
        <tr class="b">
            <td width="20%">Status:</td>
            <td>
                <select name="program.programStatus">
                    <option value="active" <c:if test="${program.programStatus == 'active'}">selected</c:if>>active</option>
                    <option value="inactive" <c:if test="${program.programStatus == 'inactive'}">selected</c:if>>inactive</option>
                </select>
            </td>
        </tr>
        <tr class="b">
            <td width="20%">Location:</td>
            <td><input type="text" name="program.location" size="30" maxlength="70" value="<c:out value="${program.location}"/>"/></td>
        </tr>
        <tr class="b">
            <td width="20%">Max Participants:</td>
            <td><input type="text" name="program.maxAllowed" id="program-maxAllowed" size="8" maxlength="8" value="<c:out value="${program.maxAllowed}"/>"/></td>
        </tr>
        <tr class="b">
            <td width="20%">Holding Tank:</td>
            <td><input type="checkbox" name="program.holdingTank" <c:if test="${program.holdingTank}">checked</c:if>/></td>
        </tr>
        <tr class="b">
            <td width="20%">Allow Batch Admissions:</td>
            <td><input type="checkbox" name="program.allowBatchAdmission" <c:if test="${program.allowBatchAdmission}">checked</c:if>/></td>
        </tr>
        <tr class="b">
            <td width="20%">Allow Batch Discharges:</td>
            <td><input type="checkbox" name="program.allowBatchDischarge" <c:if test="${program.allowBatchDischarge}">checked</c:if>/></td>
        </tr>
        <tr class="b">
            <td width="20%">Man or Woman:</td>
            <td>
                <select name="program.manOrWoman">
                    <option value="" <c:if test="${empty program.manOrWoman}">selected</c:if>>&nbsp;</option>
                    <option value="Man" <c:if test="${program.manOrWoman == 'Man'}">selected</c:if>>Man</option>
                    <option value="Woman" <c:if test="${program.manOrWoman == 'Woman'}">selected</c:if>>Woman</option>
                </select>
            </td>
        </tr>
        <tr class="b">
            <td width="20%">Transgender:</td>
            <td><input type="checkbox" name="program.transgender" <c:if test="${program.transgender}">checked</c:if>/></td>
        </tr>
        <tr class="b">
            <td width="20%">First Nation:</td>
            <td><input type="checkbox" name="program.firstNation" <c:if test="${program.firstNation}">checked</c:if>/></td>
        </tr>
        <tr class="b">
            <td width="20%">Alcohol:</td>
            <td><input type="checkbox" name="program.alcohol" <c:if test="${program.alcohol}">checked</c:if>/></td>
        </tr>
        <tr class="b">
            <td width="20%">Abstinence Support?</td>
            <td>
                <select name="program.abstinenceSupport">
                    <option value="" <c:if test="${empty program.abstinenceSupport}">selected</c:if>>&nbsp;</option>
                    <option value="Harm Reduction" <c:if test="${program.abstinenceSupport == 'Harm Reduction'}">selected</c:if>>Harm Reduction</option>
                    <option value="Abstinence Support" <c:if test="${program.abstinenceSupport == 'Abstinence Support'}">selected</c:if>>Abstinence Support</option>
                    <option value="Not Applicable" <c:if test="${program.abstinenceSupport == 'Not Applicable'}">selected</c:if>>Not Applicable</option>
                </select>
            </td>
        </tr>
        <tr class="b">
            <td width="20%">Physical Health:</td>
            <td><input type="checkbox" name="program.physicalHealth" <c:if test="${program.physicalHealth}">checked</c:if>/></td>
        </tr>
        <tr class="b">
            <td width="20%">Mental Health:</td>
            <td><input type="checkbox" name="program.mentalHealth" <c:if test="${program.mentalHealth}">checked</c:if>/></td>
        </tr>
        <tr class="b">
            <td width="20%">Housing:</td>
            <td><input type="checkbox" name="program.housing" <c:if test="${program.housing}">checked</c:if>/></td>
        </tr>
        <tr class="b">
            <td width="20%">Exclusive View:</td>
            <td>
                <select name="program.exclusiveView">
                    <option value="no" <c:if test="${program.exclusiveView == 'no'}">selected</c:if>>No</option>
                    <option value="appointment" <c:if test="${program.exclusiveView == 'appointment'}">selected</c:if>>Appointment View</option>
                    <option value="case-management" <c:if test="${program.exclusiveView == 'case-management'}">selected</c:if>>Case-management View</option>
                </select>
                (Selecting "No" allows users to switch views)
            </td>
        </tr>
        <tr class="b">
            <td width="20%">Minimum Age (inclusive):</td>
            <td><input type="text" name="program.ageMin" size="8" maxlength="8" value="<c:out value="${program.ageMin}"/>"/></td>
        </tr>
        <tr class="b">
            <td width="20%">Maximum Age (inclusive):</td>
            <td><input type="text" name="program.ageMax" size="8" maxlength="8" value="<c:out value="${program.ageMax}"/>"/></td>
        </tr>
        <tr class="b">
            <td width="20%">Enable Mandatory Encounter Time in Encounter:</td>
            <td><input type="checkbox" name="program.enableEncounterTime" <c:if test="${program.enableEncounterTime}">checked</c:if>/></td>
        </tr>
        <tr class="b">
            <td width="20%">Enable Mandatory Transportation Time in Encounter:</td>
            <td><input type="checkbox" name="program.enableEncounterTransportationTime" <c:if test="${program.enableEncounterTransportationTime}">checked</c:if>/></td>
        </tr>
        <tr>
            <td colspan="2">
                <input type="submit" value="Save" onclick="return saveProgram();"/>
                <button type="button" onclick="window.location.href='${pageContext.request.contextPath}/PMmodule/ProgramManager?method=list';">Return to program list</button>
            </td>
        </tr>
    </table>

<c:if test="${not empty requestScope.id}">
    <br/>
    <div class="tabs">
        <table cellpadding="3" cellspacing="0" border="0">
            <tr>
                <th title="signatures">Signature</th>
            </tr>
        </table>
    </div>
    <table width="100%" border="1" cellspacing="2" cellpadding="3">
        <tr class="b">
            <td>&nbsp;</td>
            <td>Provider Name</td>
            <td>Role</td>
            <td>Date</td>
        </tr>
        <tr class="b">
            <td>
                <a href="javascript:void(0)" onclick="return openProgramSignatures('<c:out value="${requestScope.id}"/>');">
                    <img alt="View details" src="${pageContext.request.contextPath}/images/details.gif" border="0"/>
                </a>
            </td>
            <td><c:out value="${programFirstSignature.providerName}"/></td>
            <td><c:out value="${programFirstSignature.caisiRoleName}"/></td>
            <td><c:out value="${programFirstSignature.updateDate}"/></td>
        </tr>
    </table>
</c:if>
