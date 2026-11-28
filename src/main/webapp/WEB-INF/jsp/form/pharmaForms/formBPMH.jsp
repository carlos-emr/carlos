<%--

    Copyright (c) 2015-2019. The Pharmacists Clinic, Faculty of Pharmaceutical Sciences, University of British Columbia. All Rights Reserved.
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
    The Pharmacists Clinic
    Faculty of Pharmaceutical Sciences
    University of British Columbia
    Vancouver, British Columbia, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>

<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>

<%-- 
	Author: Dennis Warren 
	Company: Colcamex Resources
	Date: November 2014 
	Comment: The Pharmacists Clinic, Faculty of Pharmaceutical Sciences, University of British Columbia.
--%>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>

<%@ taglib uri="jakarta.tags.core" prefix="c" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    String roleName2$ = session.getAttribute("userrole") + "," + session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName2$%>" objectName="_form" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_form");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<html>

    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <title><fmt:message key="colcamex.formBPMH.title"/></title>
        <link rel="stylesheet" type="text/css" media="screen"
              href="${ pageContext.request.contextPath }/form/pharmaForms/index.css"/>
        <link rel="stylesheet" type="text/css" media="screen"
              href="${ pageContext.request.contextPath }/library/bootstrap/5.3.8/css/bootstrap.min.css"/>
        <link rel="stylesheet" type="text/css" href="${ pageContext.request.contextPath }/css/healthCareTeam.css"/>
        <script type="text/javascript" src="${ pageContext.request.contextPath }/library/jquery/jquery-3.7.1.min.js"></script>
        <script src="${ pageContext.request.contextPath }/library/jquery/jquery-compat.js"></script>
        <script type="text/javascript">

            function popUpData(data) {
                if (data) {
                    location.reload();
                }
            }

            jQuery(document).ready(function ($) {
                const rows = document.getElementById('bpmhMedicationRows');
                const add = document.getElementById('bpmhAddReportedMedication');
                if (add) {
                    const maxDrugIndex = 255;
                    function usedDrugIndices() {
                        const used = new Set();
                        for (const field of rows.querySelectorAll('[name^="form.drugs["]')) {
                            const match = /^form\.drugs\[(\d+)\]\./.exec(field.name);
                            if (match) used.add(Number(match[1]));
                        }
                        return used;
                    }
                    function nextDrugIndex() {
                        const used = usedDrugIndices();
                        let candidate = 0;
                        while (used.has(candidate)) candidate++;
                        return candidate;
                    }
                    function clearRowLimitValidity() {
                        for (const flagged of rows.querySelectorAll('[data-bpmh-limit-invalid]')) {
                            if (typeof flagged.setCustomValidity === 'function') flagged.setCustomValidity('');
                            flagged.removeAttribute('data-bpmh-limit-invalid');
                        }
                    }
                    function syncAddAvailability() {
                        clearRowLimitValidity();
                        add.disabled = nextDrugIndex() > maxDrugIndex;
                    }
                    rows.addEventListener('click', function (event) {
                        const remove = event.target.closest('[data-remove-reported-medication]');
                        if (remove) {
                            remove.closest('tr').remove();
                            syncAddAvailability();
                        }
                    });
                    add.addEventListener('click', function () {
                        const index = nextDrugIndex();
                        if (index > maxDrugIndex) {
                            syncAddAvailability();
                            return;
                        }
                        const row = document.getElementById('bpmhReportedMedicationTemplate').content.cloneNode(true);
                        for (const field of row.querySelectorAll('[data-medication-field]')) {
                            field.name = 'form.drugs[' + index + '].' + field.dataset.medicationField;
                        }
                        const name = row.querySelector('input');
                        rows.append(row);
                        syncAddAvailability();
                        name.focus();
                    });
                    const bpmhForm = rows.closest ? rows.closest('form') : null;
                    if (bpmhForm) {
                        bpmhForm.addEventListener('submit', function (event) {
                            clearRowLimitValidity();
                            for (const idx of usedDrugIndices()) {
                                if (idx > maxDrugIndex) {
                                    event.preventDefault();
                                    const invalidField = rows.querySelector('[name^="form.drugs[' + idx + ']."]');
                                    if (invalidField && typeof invalidField.setCustomValidity === 'function') {
                                        invalidField.setAttribute('data-bpmh-limit-invalid', 'true');
                                        invalidField.setCustomValidity('Medication row limit (256) exceeded; remove excess rows before saving.');
                                        if (typeof invalidField.reportValidity === 'function') invalidField.reportValidity();
                                    }
                                    return;
                                }
                            }
                        });
                    }
                    syncAddAvailability();
                }
                $("#editFamilyDr").click(function () {
                    var familyDrContactId = $("#familyDrContactId").val();
                    var demographicNo = '${ bpmh.demographic.demographicNo }';
                    var source = "${ pageContext.request.contextPath }/demographic/ViewManageHealthCareTeam?" +
                        "demographicNo=" + demographicNo +
                        "&view=detached" +
                        "&element=providerId";
                    var windowspecs = "width=700,height=400,left=-1000,top=-1000, scrollbars=yes, resizable=yes";
                    window.open(source, "manageHealthCareTeam", windowspecs);
                })
            })
        </script>

    </head>

    <body id="formBPMH">

    <!--  HEADING  -->
    <!-- FORM BEGIN -->
    <form action="${pageContext.request.contextPath}/formBPMH" method="post">
        <%-- BpmhFormRetrieve2Action binds its bean as "form": every posted field is form.<property>. --%>
        <input type="hidden" name="form.demographicNo" id="demographicNo" value="${carlos:forHtmlAttribute(bpmh.demographicNo)}"/>
        <input type="hidden" name="form.formId" id="formId" value="${carlos:forHtmlAttribute(bpmh.formId)}"/>
        <input type="hidden" name="form.reviewToken" value="${carlos:forHtmlAttribute(bpmh.reviewToken)}"/>
        <c:if test="${bpmhReviewChanged}">
            <div class="alert alert-warning" role="alert">
                <p><fmt:message key="colcamex.formBPMH.review.changed"/></p>
                <c:forEach var="removed" items="${bpmh.removedReviewRows}">
                    <p><fmt:message key="colcamex.formBPMH.review.removed"/> #${carlos:forHtml(removed.id)}
                      <br/>${carlos:forHtml(removed.patientReportedUse)}
                      <br/>${carlos:forHtml(removed.instruction)}</p>
                </c:forEach>
            </div>
        </c:if>
        <header>

            <h1><fmt:message key="colcamex.formBPMH.title"/></h1>

            <c:set var="controls" value="on" scope="page"/>

            <div id="bpmhId">

                <c:if test="${empty bpmh.provider}">
                    <span class="red">
                        <fmt:message key="colcamex.formBPMH.preparedby"/>
                        <fmt:message key="colcamex.formBPMH.error.unknown"/>
                    </span>
                </c:if>


                <c:if test="${not empty bpmh.provider}">
		
				<span>	
						<fmt:message key="colcamex.formBPMH.preparedby"/>
						${carlos:forHtml(bpmh.provider.formattedName)}
						<c:choose>
                            <c:when test="${not empty bpmh.provider.ohipNo}">
                                &#40;${carlos:forHtml(bpmh.provider.ohipNo)}&#41;
                            </c:when>
                            <c:otherwise>
                                &#40;<fmt:message key="colcamex.formBPMH.error.unknown"/>&#41;
                            </c:otherwise>
                        </c:choose>

								
					</c:if>
				</span>


                <c:if test="${empty bpmh.formDateFormatted}">
                    <span class="red">
                </c:if>
                <c:if test="${not empty bpmh.formDateFormatted}">
                    <span>
                </c:if>

					<fmt:message key="colcamex.formBPMH.preparedon"/>
					${carlos:forHtml(bpmh.formDateFormatted)}
				</span>
									
				<span>				
					<fmt:message key="colcamex.formBPMH.printedon"/>
					${carlos:forHtml(bpmh.editDateFormatted)}
				</span>

            </div>
        </header>

        <!-- SUB HEADING -->
        <section id="subHeader">

            <!--  PATIENT  -->
            <table id="patientId">
                <tr>
                    <th colspan="6"><fmt:message key="colcamex.formBPMH.patient"/></th>
                </tr>
                <tr>
                    <td rowspan="2" class="columnTitle"><fmt:message key="colcamex.formBPMH.patient.name"/></td>
                    <td rowspan="2">
                        ${carlos:forHtml(bpmh.demographic.fullName)}
                    </td>
                    <td class="columnTitle"><fmt:message key="colcamex.formBPMH.patient.insurance"/></td>
                    <td>
                        ${carlos:forHtml(bpmh.demographic.hin)}
                    </td>
                    <td class="columnTitle"><fmt:message key="colcamex.formBPMH.patient.gender"/></td>
                    <td>
                        ${carlos:forHtml(bpmh.demographic.sex)}
                    </td>
                </tr>

                <tr>
                    <td class="columnTitle"><fmt:message key="colcamex.formBPMH.patient.dob"/></td>
                    <td>
                        ${carlos:forHtml(bpmh.demographic.formattedDob)}
                    </td>
                    <td class="columnTitle"><fmt:message key="colcamex.formBPMH.patient.phone"/></td>
                    <td>
                        ${carlos:forHtml(bpmh.demographic.phone)}
                    </td>
                </tr>
            </table>
            <table id="allergies">
                <tr>
                    <td class="columnTitle">
                        <fmt:message key="colcamex.formBPMH.patient.allergies"/>
                    </td>
                    <td>
                        <c:if test="${not empty bpmh.allergiesString}">
                            ${carlos:forHtml(bpmh.allergiesString)}
                        </c:if>
                    </td>
                </tr>
            </table>
        </section>

        <!-- FAMILY PHYSICIAN -->
        <section id="familyPhysician">
            <input type="hidden" name="form.familyDrContactId" id="familyDrContactId" value="${carlos:forHtmlAttribute(bpmh.familyDrContactId)}"/>
            <table id="providerId">
                <tr>
                    <th colspan="7"><fmt:message key="colcamex.formBPMH.familyDr"/></th>
                </tr>
                <tr>
                    <td class="columnTitle"><fmt:message key="colcamex.formBPMH.familyDr.name"/></td>
                    <td>
                        <c:choose>
                            <c:when test="${empty bpmh.familyDrName}">
                                <span class="red">Unknown</span>
                            </c:when>
                            <c:otherwise>
                                ${carlos:forHtml(bpmh.familyDrName)}
                            </c:otherwise>
                        </c:choose>

                    </td>

                    <td class="columnTitle"><fmt:message key="colcamex.formBPMH.familyDr.phone"/></td>
                    <td>
                        <c:choose>
                            <c:when test="${empty bpmh.familyDrPhone}">
                                <span class="red">Unknown</span>
                            </c:when>
                            <c:otherwise>
                                ${carlos:forHtml(bpmh.familyDrPhone)}
                            </c:otherwise>
                        </c:choose>
                    </td>

                    <td class="columnTitle"><fmt:message key="colcamex.formBPMH.familyDr.fax"/></td>
                    <td>
                        <c:choose>
                            <c:when test="${empty bpmh.familyDrFax}">
                                <span class="red">Unknown</span>
                            </c:when>
                            <c:otherwise>
                                ${carlos:forHtml(bpmh.familyDrFax)}
                            </c:otherwise>
                        </c:choose>
                    </td>
                    <c:if test="${bpmh.formId == 0}">
                        <c:if test="${not empty bpmh.familyDrContactId}">
                            <td class="columnTitle" style="text-align:center;background-color:#CCC;border-top:#ccc thin solid;">
                                <input type="button" id="editFamilyDr" value="edit"/>
                            </td>
                        </c:if>
                    </c:if>

                </tr>

            </table>
        </section>

        <!-- DRUG DATA -->
        <section id="drugData">
            <table id="drugtable">

                <tr>
                    <th colspan="4"><fmt:message key="colcamex.formBPMH.drugs"/></th>
                </tr>
                <tr id="drugtableSubheading">

                    <td class="columnTitle">
                        <fmt:message key="colcamex.formBPMH.drugs.what"/><br/>
                        <span class="smallText">
							<fmt:message key="colcamex.formBPMH.drugs.what.sub"/>
						</span>
                    </td>
                    <td class="columnTitle">
                        <fmt:message key="colcamex.formBPMH.drugs.how"/><br/>
                        <span class="smallText">
							<fmt:message key="colcamex.formBPMH.drugs.how.sub"/>
						</span>
                    </td>
                    <td class="columnTitle">
                        <fmt:message key="colcamex.formBPMH.drugs.why"/><br/>
                        <span class="smallText">
							<fmt:message key="colcamex.formBPMH.drugs.why.sub"/>
						</span>
                    </td>
                    <td class="columnTitle" id="drugInstructionsHeader">
                        <fmt:message key="colcamex.formBPMH.drugs.instructions"/><br/>
                        <span class="smallText">
							<fmt:message key="colcamex.formBPMH.drugs.instructions.sub"/>
						</span>
                    </td>
                </tr>
                <tbody id="bpmhMedicationRows">
                <c:forEach var="drugs" items="${bpmh.drugs}" varStatus="status">
                    <tr>
                        <td id="drugWhat${status.index}">
                            <c:choose>
                                <c:when test="${bpmh.formId == 0 and empty drugs.id}">
                                    <label><fmt:message key="colcamex.formBPMH.reported.medication"/>
                                        <input name="form.drugs[${status.index}].reportedMedication"
                                               value="${carlos:forHtmlAttribute(drugs.reportedMedication)}"
                                               required pattern=".*\S.*"/>
                                    </label>
                                    <c:if test="${bpmhInvalidReportedMedication and empty drugs.what}">
                                        <p class="text-danger" role="alert"><fmt:message key="colcamex.formBPMH.reported.nameRequired"/></p>
                                    </c:if>
                                </c:when>
                                <c:otherwise>
                                    ${carlos:forHtml(drugs.what)}
                                    <c:if test="${not empty drugs.reportedMedication}">
                                        <small><fmt:message key="colcamex.formBPMH.reported.medication"/></small>
                                    </c:if>
                                    <c:if test="${bpmh.formId == 0}">
                                        <input type="hidden" name="form.drugs[${status.index}].id" value="${carlos:forHtmlAttribute(drugs.id)}"/>
                                        <input type="hidden" name="form.drugs[${status.index}].reviewedVersion"
                                               value="${carlos:forHtmlAttribute(drugs.reviewedVersion)}"/>
                                    </c:if>
                                </c:otherwise>
                            </c:choose>
                            <c:if test="${bpmhReviewChanged and drugs.reviewChanged}">
                                <p><fmt:message key="colcamex.formBPMH.review.rowChanged"/></p>
                            </c:if>
                        </td>
                        <td>
                            <c:choose>
                                <c:when test="${not empty drugs.how}">
                                    <div style="white-space:pre-wrap">${carlos:forHtml(drugs.how)}</div>
                                </c:when>
                                <c:when test="${empty drugs.reportedMedication and not empty drugs.id}">
                                    <fmt:message key="colcamex.formBPMH.review.unknown"/>
                                </c:when>
                            </c:choose>
                            <c:choose>
                                <c:when test="${bpmh.formId == 0}">
                                    <label><fmt:message key="colcamex.formBPMH.reported.use"/>
                                        <textarea name="form.drugs[${status.index}].patientReportedUse"
                                                  rows="2">${carlos:forHtml(drugs.patientReportedUse)}</textarea>
                                    </label>
                                </c:when>
                                <c:when test="${not empty drugs.patientReportedUse}">
                                    <div style="white-space:pre-wrap"><fmt:message key="colcamex.formBPMH.reported.use"/>
${carlos:forHtml(drugs.patientReportedUse)}</div>
                                </c:when>
                            </c:choose>
                        </td>
                        <td>
                            <c:choose>
                                <c:when test="${bpmh.formId == 0 and empty drugs.id}">
                                    <button type="button" data-remove-reported-medication><fmt:message key="colcamex.formBPMH.reported.remove"/></button>
                                </c:when>
                                <c:when test="${empty drugs.why and empty drugs.reportedMedication}">
                                    <fmt:message key="colcamex.formBPMH.review.unknown"/>
                                </c:when>
                                <c:otherwise><div style="white-space:pre-wrap">${carlos:forHtml(drugs.why)}</div></c:otherwise>
                            </c:choose>
                        </td>
                        <td>
                            <c:choose>
                                <c:when test="${bpmh.formId == 0}">
                                    <textarea name="form.drugs[${status.index}].instruction" id="drugInstruction${status.index}"
                                              aria-labelledby="drugInstructionsHeader drugWhat${status.index}" rows="2">${carlos:forHtml(drugs.instruction)}</textarea>
                                </c:when>
                                <c:otherwise><div style="white-space:pre-wrap">${carlos:forHtml(drugs.instruction)}</div></c:otherwise>
                            </c:choose>
                        </td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
            <c:if test="${bpmh.formId == 0}">
                <button type="button" id="bpmhAddReportedMedication"><fmt:message key="colcamex.formBPMH.reported.add"/></button>
                <template id="bpmhReportedMedicationTemplate">
                    <tr>
                        <td><label><fmt:message key="colcamex.formBPMH.reported.medication"/>
                            <input data-medication-field="reportedMedication" required pattern=".*\S.*"/>
                        </label></td>
                        <td><label><fmt:message key="colcamex.formBPMH.reported.use"/>
                            <textarea data-medication-field="patientReportedUse" rows="2"></textarea>
                        </label></td>
                        <td><button type="button" data-remove-reported-medication><fmt:message key="colcamex.formBPMH.reported.remove"/></button></td>
                        <td><label><fmt:message key="colcamex.formBPMH.drugs.instructions"/>
                            <textarea data-medication-field="instruction" rows="2"></textarea>
                        </label></td>
                    </tr>
                </template>
            </c:if>
        </section>
        <section id="declaration">
            <table>
                <tr>
                    <td>
                        <input type="checkbox" name="form.confirm" id="confirm" value="true" ${bpmh.confirm ? 'checked' : ''} ${bpmh.formId != 0 ? 'disabled' : ''}/>
                        <label for="confirm"><fmt:message key="colcamex.formBPMH.confirm"/></label>
                    </td>
                </tr>
            </table>
        </section>
        <section id="note">
            <table>
                <tr>
                    <td id="bpmhNoteLabel">
                        <fmt:message key="colcamex.formBPMH.notes"/>
                    </td>
                </tr>
                <tr>
                    <td>
                        <c:choose>
                            <c:when test="${bpmh.formId == 0}">
                                <%-- "note" is the section's id. --%>
                                <textarea name="form.note" id="bpmhNote" aria-labelledby="bpmhNoteLabel" rows="4">${carlos:forHtml(bpmh.note)}</textarea>
                            </c:when>
                            <c:otherwise>
                                ${carlos:forHtml(bpmh.note)}&nbsp;
                            </c:otherwise>
                        </c:choose>
                    </td>
                </tr>
            </table>
        </section>

        <section id="controls">
            <table>
                <tr>
                    <td>
                        <c:choose>
                            <c:when test="${empty bpmh.allergiesString}">
                                <c:set var="controls" value="off" scope="page"/>
                                <span style="color:red;"><fmt:message key="form.bpmh.allergyNotationRequired"/></span>
                            </c:when>
                        </c:choose>
                        <c:choose>
                            <c:when test="${empty bpmh.provider}">
                                <c:set var="controls" value="off" scope="page"/>
                                <span class="red">
                                    <fmt:message key="colcamex.formBPMH.error.missing.provider"/>
                                </span>
                            </c:when>
                        </c:choose>


                        <c:if test="${ controls eq 'on' }">

                            <%-- The action dispatches on "method"; a button keeps the translated label as its text. --%>
                            <button type="submit" name="method" value="print"><fmt:message key="colcamex.formBPMH.print"/></button>

                            <c:if test="${bpmh.formId == 0}">
                            <button type="submit" name="method" value="save"><fmt:message key="colcamex.formBPMH.save"/></button>
                            </c:if>

                        </c:if>

                        <c:if test="${not empty savedMessage}">
                            <div class="messages">
                                    ${carlos:forHtml(savedMessage)}
                            </div>
                        </c:if>
                    </td>
                </tr>
            </table>
        </section>
    </form>

    <!-- FOOTER -->
    <footer>
        <span><fmt:message key="colcamex.formBPMH.formowner"/></span>
    </footer>

    </body>
</html>
