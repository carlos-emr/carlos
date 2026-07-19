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
<%@ page import="io.github.carlos_emr.carlos.PMmodule.model.ProgramSignature" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.model.Program" %>
<script>
    function save() {
        var maxAllowed = document.programManagerForm.elements['program.maxAllowed'].value;
        if (isNaN(maxAllowed)) {
            alert("Maximum participants '" + maxAllowed + "' is not a number");
            return false;
        }
        if (document.programManagerForm.elements['program.maxAllowed'].value <= 0) {
            alert('Maximum participants must be a positive integer');
            return false;
        }

        if (document.programManagerForm.elements['program.name'].value == null || document.programManagerForm.elements['program.name'].value.length <= 0) {
            alert('The program name can not be blank.');
            return false;
        }


        document.programManagerForm.method.value = 'save';
        document.programManagerForm.submit()
    }

    function getProgramSignatures(id) {
        if (id == null || id == "") return;
        var url = '<%=request.getContextPath() %>/PMmodule/ProgramManager.do?method=programSignatures&programId=';
        window.open(url + id, 'signature', 'width=600,height=600,scrollbars=1');
    }
</script>
<input type="hidden" name="numOfMembers" id="numOfMembers"/>
<input type="hidden" name="id" id="id"/>
<%
    Program p = (Program) request.getAttribute("oldProgram");

%>
<input type="hidden" name="old_maxAllowed"
       value=<%if(p!=null) { %> "<%=p.getMaxAllowed() %>" <%} else { %> "0" <%} %> />
<input type="hidden" name="old_name" value=<%if(p!=null) { %> "<%=p.getName()%>" <%} else { %> "" <%} %> />
<input type="hidden" name="old_descr" value=<%if(p!=null) { %> "<%=p.getDescription()%>" <%} else { %> "" <%} %> />
<input type="hidden" name="old_type" value=<%if(p!=null) { %> "<%=p.getType()%>" <%} else { %> "" <%} %> />
<input type="hidden" name="old_address" value=<%if(p!=null) { %> "<%=p.getAddress()%>"<%} else { %> "" <%} %> />
<input type="hidden" name="old_phone" value=<%if(p!=null) { %> "<%=p.getPhone()%>" <%} else { %> "" <%} %> />
<input type="hidden" name="old_fax" value=<%if(p!=null) { %> "<%=p.getFax() %>" <%} else { %> "" <%} %> />
<input type="hidden" name="old_url" value=<%if(p!=null) { %> "<%=p.getUrl()%>" <%} else { %> "" <%} %> />
<input type="hidden" name="old_email" value=<%if(p!=null) { %> "<%=p.getEmail()%>" <%} else { %> "" <%} %> />
<input type="hidden" name="old_emergencyNumber"
       value=<%if(p!=null) { %> "<%=p.getEmergencyNumber()%>"<%} else { %> "" <%} %> />
<input type="hidden" name="old_location" value=<%if(p!=null) { %> "<%=p.getLocation()%>" <%} else { %> "" <%} %> />
<input type="hidden" name="old_programStatus"
       value=<%if(p!=null) { %> "<%=p.getProgramStatus()%>" <%} else { %> "" <%} %> />
<input type="hidden" name="old_manOrWoman" value=<%if(p!=null) { %> "<%=p.getManOrWoman() %>" <%} else { %> "" <%} %> />
<input type="hidden" name="old_abstinenceSupport"
       value=<%if(p!=null) { %> "<%=p.getAbstinenceSupport() %>" <%} else { %> "" <%} %> />
<input type="hidden" name="old_exclusiveView"
       value=<%if(p!=null) { %> "<%=p.getExclusiveView() %>" <%} else { %> "" <%} %> />
<input type="hidden" name="old_holdingTank"
       value=<%if(p!=null) { %> "<%=p.isHoldingTank() %>" <%} else { %> "" <%} %> />
<input type="hidden" name="old_allowBatchAdmission"
       value=<%if(p!=null) { %> "<%=p.isAllowBatchAdmission() %>" <%} else { %> "" <%} %> />
<input type="hidden" name="old_allowBatchDischarge"
       value=<%if(p!=null) { %> "<%=p.isAllowBatchDischarge() %>" <%} else { %> "" <%} %> />
<input type="hidden" name="old_hic" value=<%if(p!=null) { %> "<%=p.isHic() %>" <%} else { %> "" <%} %> />
<input type="hidden" name="old_transgender"
       value=<%if(p!=null) { %> "<%=p.isTransgender() %>" <%} else { %> "" <%} %> />
<input type="hidden" name="old_firstNation"
       value=<%if(p!=null) { %> "<%=p.isFirstNation() %>" <%} else { %> "" <%} %> />
<input type="hidden" name="old_alcohol" value=<%if(p!=null) { %> "<%=p.isAlcohol()%>" <%} else { %> "" <%} %> />
<input type="hidden" name="old_physicalHealth"
       value=<%if(p!=null) { %> "<%=p.isPhysicalHealth() %>" <%} else { %> "" <%} %> />
<input type="hidden" name="old_mentalHealth"
       value=<%if(p!=null) { %> "<%=p.isMentalHealth() %>" <%} else { %> "" <%} %> />
<input type="hidden" name="old_housing" value=<%if(p!=null) { %> "<%=p.isHousing() %>" <%} else { %> "" <%} %> />
<input type="hidden" name="old_facility_id"
       value=<%if(p!=null) { %> "<%=p.getFacilityId() %>" <%} else { %> "" <%} %> />
<input type="hidden" name="old_enableEncounterTime"
       value=<%if(p!=null) { %> "<%=p.getEnableEncounterTime() %>" <%} else { %> "" <%} %> />
<input type="hidden" name="old_enableEncounterTransportationTime"
       value=<%if(p!=null) { %> "<%=p.isEnableEncounterTransportationTime() %>" <%} else { %> "" <%} %> />
<input type="hidden" name="old_enableOCAN" value=<%if(p!=null) { %> "<%=p.isEnableOCAN() %>" <%} else { %> "" <%} %> />

<div class="tabs">
    <table cellpadding="3" cellspacing="0" border="0">
        <tr>
            <th title="Programs">General Information</th>
            <th title="Templates" class="nofocus">
                <a onclick="javascript:clickTab2('General','Vacancy Templates');return false;"
                   href="javascript:void(0)">Vacancy Templates</a>
            </th>
        </tr>
    </table>
</div>
<table width="100%" border="1" cellspacing="2" cellpadding="3">
    <tr class="b">
        <td width="20%">Name:</td>
        <td><input type="checkbox" name="program.name" size="30" maxlength="70" /></td>
    </tr>
    <tr class="b">
        <td width="20%">Facility</td>
        <td><select name="program.facilityId">
            <c:forEach var="facility" items="${facilities}">
                <option value="${facility.id}">
                    <c:out value="${facility.name}"/>
                </option>
            </c:forEach>
        </select></td>
    </tr>
    <tr class="b">
        <td width="20%">Description:</td>
        <td><input type="text" name="program.description" size="30"
                       maxlength="255"/></td>
    </tr>
    <tr class="b">
        <td width="20%">Functional Centre:</td>
        <td>
            <select name="program.functionalCentreId" id="program.functionalCentreId">
                <option value="">&nbsp;</option>

                <c:forEach var="functionalCentre" items="${functionalCentres}">
                    <option value="<c:out value="${functionalCentre.accountId}" />"
                            <c:if test="${oldProgram.functionalCentreId == functionalCentre.accountId}">selected</c:if> >
                        <c:out value="${functionalCentre.accountId}"/>, <c:out
                            value="${functionalCentre.description}"/></option>
                </c:forEach>
            </select>
        </td>
    </tr>
    <tr class="b">
        <td width="20%">HIC:</td>
        <td><input type="checkbox" name="program.hic"/></td>
    </tr>
    <tr class="b">
        <td width="20%">Address:</td>
        <td><input type="text" name="program.address" size="30"
                       maxlength="255"/></td>
    </tr>
    <tr class="b">
        <td width="20%">Phone:</td>
        <td><input type="checkbox" name="program.phone" size="30" maxlength="25" /></td>
    </tr>
    <tr class="b">
        <td width="20%">Fax:</td>
        <td><input type="checkbox" name="program.fax" size="30" maxlength="25" /></td>
    </tr>
    <tr class="b">
        <td width="20%">URL:</td>
        <td><input type="checkbox" name="program.url" size="30" maxlength="100" /></td>
    </tr>
    <tr class="b">
        <td width="20%">Email:</td>
        <td><input type="checkbox" name="program.email" size="30" maxlength="50" /></td>
    </tr>
    <tr class="b">
        <td width="20%">Emergency Number:</td>
        <td><input type="text" name="program.emergencyNumber" size="30"
                       maxlength="25"/></td>
    </tr>
    <tr class="b">
        <td width="20%">Type:</td>
        <td><select name="program.type" id="program.type">
            <option value="Service"/>
            <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="false">
                <option value="External"/>
                <option value="community">Community</option>
            </caisi:isModuleLoad>
        </select></td>
    </tr>
    <tr class="b">
        <td width="20%">Status:</td>
        <td><select name="program.programStatus" id="program.programStatus">
            <option value="active"/>
            <option value="inactive"/>
        </select></td>
    </tr>
    <tr class="b">
        <td width="20%">Location:</td>
        <td><input type="text" name="program.location" size="30"
                       maxlength="70"/></td>
    </tr>
    <tr class="b">
        <td width="20%">Max Participants:</td>
        <td><input type="text" name="program.maxAllowed" size="8"
                       maxlength="8"/></td>
    </tr>
    <tr class="b">
        <td width="20%">Holding Tank:</td>
        <td><input type="checkbox" name="program.holdingTank"/></td>
    </tr>
    <tr class="b">
        <td width="20%">Allow Batch Admissions:</td>
        <td><input type="checkbox" name="program.allowBatchAdmission"/></td>
    </tr>
    <tr class="b">
        <td width="20%">Allow Batch Discharges:</td>
        <td><input type="checkbox" name="program.allowBatchDischarge"/></td>
    </tr>
    <tr class="b">
        <td width="20%">Man or Woman:</td>
        <td><select name="program.manOrWoman" id="program.manOrWoman">
            <option value=""/>
            <option value="Man"/>
            <option value="Woman"/>
        </select></td>
    </tr>
    <tr class="b">
        <td width="20%">Transgender:</td>
        <td><input type="checkbox" name="program.transgender"/></td>
    </tr>
    <tr class="b">
        <td width="20%">First Nation:</td>
        <td><input type="checkbox" name="program.firstNation"/></td>
    </tr>
    <tr class="b">
        <td width="20%">Alcohol:</td>
        <td><input type="checkbox" name="program.alcohol"/></td>
    </tr>
    <tr class="b">
        <td width="20%">Abstinence Support?</td>
        <td><select name="program.abstinenceSupport" id="program.abstinenceSupport">
            <option value=" "/>
            <option value="Harm Reduction"/>
            <option value="Abstinence Support"/>
            <option value="Not Applicable"/>
        </select></td>
    </tr>
    <tr class="b">
        <td width="20%">Physical Health:</td>
        <td><input type="checkbox" name="program.physicalHealth"/></td>
    </tr>
    <tr class="b">
        <td width="20%">Mental Health:</td>
        <td><input type="checkbox" name="program.mentalHealth"/></td>
    </tr>
    <tr class="b">
        <td width="20%">Housing:</td>
        <td><input type="checkbox" name="program.housing"/></td>
    </tr>
    <tr class="b">
        <td width="20%">Exclusive View:</td>
        <td><select name="program.exclusiveView" id="program.exclusiveView">
            <option value="no">No</option>
            <option value="appointment">Appointment View</option>
            <option value="case-management">Case-management View</option>
        </select> (Selecting "No" allows users to switch views)
        </td>
    </tr>
    <tr class="b">
        <td width="20%">Minimum Age (inclusive):</td>
        <td><input type="checkbox" name="program.ageMin" size="8" maxlength="8" /></td>
    </tr>
    <tr class="b">
        <td width="20%">Maximum Age (inclusive):</td>
        <td><input type="checkbox" name="program.ageMax" size="8" maxlength="8" /></td>
    </tr>
    <tr class="b">
        <td width="20%">Enable Mandatory Encounter Time:</td>
        <td><input type="checkbox" name="program.enableEncounterTime"/></td>
    </tr>
    <tr class="b">
        <td width="20%">Enable Mandatory Transportation Time:</td>
        <td><input type="checkbox" name="program.enableEncounterTransportationTime"/></td>
    </tr>
    <tr class="b">
        <td width="20%">Email Notification Addresses (csv):</td>
        <td><input type="text" name="program.emailNotificationAddressesCsv" id="program.emailNotificationAddressesCsv" /></td>
    </tr>
    <tr class="b">
        <td width="20%">Enable OCAN:</td>
        <td><input type="checkbox" name="program.enableOCAN"/></td>
    </tr>
    <tr>
        <td colspan="2"><input type="button" value="Save" onclick="return save()"/> <button type="button" onclick="window.history.back();">Cancel</button></td>
    </tr>
</table>

</br>
<div class="tabs">
    <table cellpadding="3" cellspacing="0" border="0">
        <tr>
            <th title="sinatures">Signature</th>
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
        <td><a href="javascript:void(0)"
               onClick="getProgramSignatures('<c:out value="${id}"/>')"> <img
                alt="View details" src="<c:out value='${ctx}' />/images/details.gif"
                border="0"/> </a></td>
        <td><c:out value="${programFirstSignature.providerName}"/></td>
        <td><c:out value="${programFirstSignature.caisiRoleName}"/></td>
        <td><c:out value="${programFirstSignature.updateDate}"/></td>
    </tr>
</table>
