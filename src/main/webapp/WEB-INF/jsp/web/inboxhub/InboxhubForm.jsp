<!--
Copyright (c) 2023. Magenta Health Inc. All Rights Reserved.

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
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA
-->

<%@ page import="java.util.*" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ page import="io.github.carlos_emr.carlos.lab.ca.on.*" %>
<%@ page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>

<%@ page import="org.apache.logging.log4j.Logger" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.OscarLogDao" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.inboxhub.query.InboxhubQuery" %>
<%@ page import="io.github.carlos_emr.carlos.mds.data.CategoryData" %>
<%@ page import="org.owasp.encoder.Encode" %>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>


<!DOCTYPE html>

<input type="hidden" class="totalDocsCount" id="totalDocsCount" value="${totalDocsCount}" />
<input type="hidden" class="totalLabsCount" id="totalLabsCount" value="${totalLabsCount}" />
<input type="hidden" class="totalHRMCount" id="totalHRMCount" value="${totalHRMCount}" />
<input type="hidden" class="totalResultsCount" id="totalResultsCount" value="${totalResultsCount}" />


<!-- Search form Accordion -->
<div class="accordion" id="inbox-hub-search">
    <div class="accordion-item">
        <h2 class="accordion-header" id="headingSearch">
            <button class="accordion-button" type="button" data-bs-toggle="collapse" data-bs-target="#collapseSearch" aria-expanded="true" aria-controls="collapseSearch">
                <fmt:message key="inboxhub.form.search"/>
            </button>
        </h2>
        <div id="collapseSearch" class="accordion-collapse collapse show" aria-labelledby="headingSearch" data-bs-parent="#inbox-hub-search">
            <div class="accordion-body">
                 <form action="${pageContext.request.contextPath}/web/inboxhub/Inboxhub?method=displayInboxForm" method="post" id="inboxSearchForm" data-revoke-state="${carlos:forHtmlAttribute(param.inboxhubRevokeState)}" onsubmit="return validatePatientOptions();">
                    <div class="m-2">
                        <input type="hidden" name="query.viewMode" id="btnViewMode" value="${query.viewMode ? 'true' : 'false'}">

                        <div class="mb-1">
                            <!--Provider-->
                            <label class="fw-bold text-uppercase">
                                <fmt:message key="inbox.inboxmanager.msgProviders"/>
                            </label>
                            <input type="hidden" name="query.searchAll" id="searchProviderAll" value="${carlos:forHtmlAttribute(query.searchAll)}"/>
                            <!-- Any Provider -->
                            <div class="form-check">
                                <input class="form-check-input" type="radio" name="providerRadios" value="option1" id="anyProvider" ${query.searchAll eq 'true' ? 'checked' : ''} onClick="changeValueElementByName('query.searchAll', 'true');toggleInputVisibility('specificProvider', 'specificProviderId', 200);"/>
                                <label class="form-check-label" for="anyProvider"><fmt:message key="oscarMDS.search.formAnyProvider"/></label>
                            </div>
                            <!-- No Provier -->
                            <div class="form-check">
                                <input class="form-check-input" type="radio" name="providerRadios" value="option2" id="noProvider" ${query.searchAll eq 'false' ? 'checked' : ''} onClick="changeValueElementByName('query.searchAll', 'false');toggleInputVisibility('specificProvider', 'specificProviderId', 200);"/>
                                <label class="form-check-label" for="noProvider"><fmt:message key="oscarMDS.search.formNoProvider"/></label>
                            </div>
                            <!-- Specific Provider -->
                            <div class="form-check">
                                <input class="form-check-input" type="radio" name="providerRadios" value="option3" id="specificProvider" ${query.searchAll eq '' ? 'checked' : ''} onclick="changeValueElementByName('query.searchAll', ''); changeValueElementByName('query.searchProviderNo', document.getElementsByName('query.searchProviderNo')[0].value);toggleInputVisibility('specificProvider', 'specificProviderId', 200);" />
                                <label class="form-check-label" for="specificProvider"><fmt:message key="oscarMDS.search.formSpecificProvider"/></label>
                                <div id="specificProviderId" class="ms-3">
                                    <input type="hidden" name="query.searchProviderNo" id="findProvider" value="${carlos:forHtmlAttribute(query.searchProviderNo)}"/>
                                    <div class="input-group input-group-sm">
                                        <input class="form-control pe-0 m-1" type="text" id="autocompleteProvider" name="query.searchProviderName" value="<carlos:encode value='${query.searchProviderName}' context="htmlAttribute"/>" placeholder="<fmt:message key='inboxhub.form.providerPlaceholder'/>"/>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div class="mb-1">
                            <!--Patient(s)-->
                            <label class="fw-bold text-uppercase">
                                <fmt:message key="inbox.inboxmanager.msgPatinets"/>
                            </label>
                            <!-- All Patients (including unmatched) -->
                            <input type="hidden" name="query.unmatched" id="unmatchedId" value="${carlos:forHtmlAttribute(query.unmatched)}"/>
                            <div class="form-check">
                                <input class="form-check-input" type="radio" name="patientsRadios" value="patientsOption1" id="allPatients" ${query.unmatched eq 'false' and query.patientFirstName eq '' and query.patientLastName eq '' and query.patientHealthNumber eq '' ? 'checked' : ''} onClick="changeValueElementByName('query.unmatched', 'false');toggleInputVisibility('specificPatients', 'specificPatientsId', 200);"/>
                                <label class="form-check-label" for="allPatients"><fmt:message key="oscarMDS.search.formAllPatients"/></label>
                            </div>
                            <!-- Unmatched to Existing Patient -->
                            <div class="form-check">
                                <input class="form-check-input" type="radio" name="patientsRadios" value="patientsOption2" id="unmatchedPatients" ${query.unmatched eq 'true' ? 'checked' : ''} onClick="changeValueElementByName('query.unmatched', 'true');toggleInputVisibility('specificPatients', 'specificPatientsId', 200);" />
                                <label class="form-check-label" for="unmatchedPatients"><fmt:message key="oscarMDS.search.formExistingPatient"/></label>
                            </div>
                            <!-- Specific Patient(s) -->
                            <div class="form-check">
                                <input class="form-check-input" type="radio" name="patientsRadios" value="patientsOption3" id="specificPatients" ${query.unmatched eq 'false' and (query.patientFirstName ne '' or query.patientLastName ne '' or query.patientHealthNumber ne '') ? 'checked' : ''} onClick="changeValueElementByName('query.unmatched', 'false');toggleInputVisibility('specificPatients', 'specificPatientsId', 200);"/>
                                <label class="form-check-label" for="specificPatients"><fmt:message key="oscarMDS.search.formSpecificPatients"/></label> <br>
                                <div id="specificPatientsId" class="d-grid ms-3">
                                    <div class="input-group input-group-sm">
                                        <input class="form-control pe-0 m-1" type="text" name="query.patientFirstName" id="inputFirstName" value="<carlos:encode value='${query.patientFirstName}' context="htmlAttribute"/>" placeholder="<fmt:message key='admin.provider.formFirstName'/>"/>
                                    </div>
                                    <div class="input-group input-group-sm">
                                        <input class="form-control pe-0 mb-1 mx-1" type="text" name="query.patientLastName" id="inputLastName" value="<carlos:encode value='${query.patientLastName}' context="htmlAttribute"/>" placeholder="<fmt:message key='admin.provider.formLastName'/>"/>
                                    </div>
                                    <div class="input-group input-group-sm">
                                        <input class="form-control pe-0 mb-1 mx-1" type="text" name="query.patientHealthNumber" id="inputHIN" value="<carlos:encode value='${query.patientHealthNumber}' context="htmlAttribute"/>" placeholder="<fmt:message key='oscarMDS.index.msgHealthNumber'/>"/>
                                    </div>
                                    <div class="text-danger d-none ms-1" id="specificPatientErrorMessage"><fmt:message key="inboxhub.form.specificPatientError"/></div>
                                </div>
                            </div>
                        </div>

                        <div class="mb-1">
                            <!-- Date Range-->
                            <label class="fw-bold text-uppercase">
                                <fmt:message key="inbox.inboxmanager.msgDateRange"/>
                            </label>
                            <div id="dateId" class="inbox-form-date-range">
                                <div class="inbox-form-datepicker-wrapper mb-1 d-flex">
                                    <label class="my-auto pe" for="startDate"><fmt:message key="inboxhub.form.startDate"/></label>
                                    <div class="input-group input-group-sm d-inline-flex">
                                        <input class="form-control pe-0 inbox-form-datepicker-input" type="text" placeholder="<fmt:message key='inboxhub.form.datePlaceholder'/>" id="startDate" name="query.startDate" value="${carlos:forHtmlAttribute(query.startDate)}"/>
                                        <span class="input-group-text" for="startDate" id="startDateIcon"><i class="fa-solid fa-calendar"></i></span>
                                    </div>
                                    <i class="fa-solid fa-circle-xmark clear-btn" aria-hidden="true" id="clearStartDate"></i>
                                </div>
                                <div class="inbox-form-datepicker-wrapper d-flex">
                                    <label class="my-auto" for="endDate"><fmt:message key="inboxhub.form.endDate"/></label>
                                    <div class="input-group input-group-sm d-inline-flex">
                                        <input class="form-control pe-0 inbox-form-datepicker-input" type="text" placeholder="<fmt:message key='inboxhub.form.datePlaceholder'/>" id="endDate" name="query.endDate" value="${carlos:forHtmlAttribute(query.endDate)}"/>
                                        <span class="input-group-text" for="endDate" id="endDateIcon"><i class="fa-solid fa-calendar"></i></span>
                                    </div>
                                    <i class="fa-solid fa-circle-xmark clear-btn" aria-hidden="true" id="clearEndDate"></i>
                                </div>
                            </div>
                        </div>

                        <div class="mb-1">
                            <!--Type-->
                            <label class="fw-bold text-uppercase">
                                <fmt:message key="inbox.inboxmanager.msgType"/>
                            </label>
                            <div class="form-check">
                                <input type="checkbox" class="form-check-input" name="query.doc" value="true" ${query.doc || (!query.doc && !query.lab && !query.hrm) ? 'checked' : ''} id="btnDoc" autocomplete="off">
                                <label class="form-check-label" for="btnDoc"><fmt:message key="inbox.inboxmanager.msgTypeDocs"/></label><br>
                            </div>
                            <div class="form-check">
                                <input type="checkbox" class="form-check-input" name="query.lab" value="true" ${query.lab || (!query.doc && !query.lab && !query.hrm) ? 'checked' : ''} id="btnLab" autocomplete="off">
                                <label class="form-check-label" for="btnLab"><fmt:message key="inbox.inboxmanager.msgTypeLabs"/></label><br>
                            </div>

                            <c:if test="${!CarlosProperties.getInstance().isBritishColumbiaBillingRegion()}">
                                <div class="form-check">
                                    <input type="checkbox" class="form-check-input" name="query.hrm" value="true" ${query.hrm || (!query.doc && !query.lab && !query.hrm) ? 'checked' : ''} id="btnHRM" autocomplete="off">
                                    <label class="form-check-label" for="btnHRM"><fmt:message key="inbox.inboxmanager.msgTypeHRM"/></label><br>
                                </div>
                            </c:if>
                        </div>

                        <div class="mb-1">
                            <!--Review Status-->
                            <label class="fw-bold text-uppercase">
                                <fmt:message key="inbox.inboxmanager.msgReviewStatus"/>
                            </label>
                            <input type="hidden" name="query.status" id="statusId" value="${carlos:forHtmlAttribute(query.status)}"/>
                            <div class="form-check">
                                <input type="radio" class="form-check-input" name="statusReview" id="statusAll" value="All"
                                    ${empty query.status ? 'checked' : ''} onclick="changeValueElementByName('query.status', '')">
                                <label class="form-check-label" for="statusAll"><fmt:message key="inbox.inboxmanager.msgAll"/>
                            </div>
                            <div class="form-check">
                                <input type="radio" class="form-check-input" name="statusReview" id="statusNew" value="N"
                                    ${query.status eq 'N' ? 'checked' : ''} onclick="changeValueElementByName('query.status', 'N')">
                                <label class="form-check-label" for="statusNew"><fmt:message key="inbox.inboxmanager.msgNew"/></label>
                            </div>
                            <div class="form-check">
                                <input type="radio" class="form-check-input" name="statusReview" id="statusAcknowledged" value="A"
                                    ${query.status eq 'A' ? 'checked' : ''} onclick="changeValueElementByName('query.status', 'A')">
                                <label class="form-check-label" for="statusAcknowledged"><fmt:message key="inbox.inboxmanager.msgAcknowledged"/></label>
                            </div>
                            <div class="form-check">
                                <input type="radio" class="form-check-input" name="statusReview" id="statusFiled" value="F"
                                    ${query.status eq 'F' ? 'checked' : ''} onclick="changeValueElementByName('query.status', 'F')">
                                <label class="form-check-label" for="statusFiled"><fmt:message key="inbox.inboxmanager.msgFiled"/></label>
                            </div>
                        </div>

                        <div class="mb-2">
                            <!--Abnormal-->
                            <label class="fw-bold text-uppercase">
                                <fmt:message key="inbox.inboxmanager.msgResultStatus"/>
                            </label>
                            <input type="hidden" name="query.abnormal" id="abnormalId" value="${carlos:forHtmlAttribute(query.abnormal)}"/>
                            <div class="form-check">
                                <input type="radio" class="form-check-input" name="abnormalResult" id="abnormalAll" value="All"
                                    ${query.abnormal eq 'all' ? 'checked' : ''} onclick="changeValueElementByName('query.abnormal', 'all')">
                                <label class="form-check-label" for="abnormalAll"><fmt:message key="inbox.inboxmanager.msgAll"/></label>
                            </div>
                            <div class="form-check">
                                <input type="radio" class="form-check-input" name="abnormalResult" id="Abnormal" value="Abnormal"
                                    ${query.abnormal eq 'abnormalOnly' ? 'checked' : ''} onclick="changeValueElementByName('query.abnormal', 'abnormalOnly')">
                                <label class="form-check-label" for="Abnormal"><fmt:message key="global.abnormal"/></label>
                            </div>
                            <div class="form-check">
                                <input type="radio" class="form-check-input" name="abnormalResult" id="abnormalNormal" value="Normal"
                                    ${query.abnormal eq 'normalOnly' ? 'checked' : ''} onclick="changeValueElementByName('query.abnormal', 'normalOnly')">
                                <label class="form-check-label" for="abnormalNormal"><fmt:message key="inbox.inboxmanager.msgNormal"/></label>
                            </div>
                        </div>

                        <!--Search Button-->
                        <div class="d-grid gap-1">
                            <button id="inboxhubFormSearchBtn" class="btn btn-primary btn-sm" type="submit" value='<fmt:message key="oscarMDS.search.btnSearch"/>'>
                                <span id="inboxhubFormSearchSpinner" class="spinner-border spinner-border-sm" role="status" aria-hidden="true" style="display: none;"></span>
                                <span id="inboxhubFormSearchText"><fmt:message key="oscarMDS.search.btnSearch"/></span>
                            </button>
                            <button type="button" class="btn btn-secondary btn-sm" onclick="resetInboxFilters();"><fmt:message key="inboxhub.form.btnReset"/></button>
                        </div>
                    </div>
                </form>
            </div>
        </div>
    </div>
</div>
<!-- End of the Search form Accordion -->

<c:if test="${ categoryData.unmatchedDocs gt 0 or categoryData.unmatchedLabs gt 0 or categoryData.unmatchedHRMCount gt 0 or not empty requestScope.categoryData.patientList }">
<div class="category-list"> 
    <c:set var="allTypes" value="${!query.doc and !query.lab and !query.hrm}" />
    <c:set var="showHRM" value="${(query.hrm or allTypes) and (query.abnormalBool == null or !query.abnormalBool)}" />

    <c:if test="${ categoryData.unmatchedDocs gt 0 or categoryData.unmatchedLabs gt 0 or categoryData.unmatchedHRMCount gt 0}">
    <!-- Unmatched List Accordion -->
    <div class="accordion mt-1" id="inbox-hub-unmatched-list">
        <div class="accordion-item">
            <h2 class="accordion-header" id="headingUnmatchedList">
                <a class="text-decoration-none accordion-button ${carlos:forHtmlAttribute(param.providerNo eq 0 ? '' : 'collapsed')}" type="button" data-bs-toggle="collapse" data-bs-target="#collapseUnmatched" aria-expanded="false" aria-controls="collapseUnmatched">
                    <fmt:message key="inbox.inboxmanager.msgUnmatched"/>
                </a>
            </h2>
            <div id="collapseUnmatched" class="accordion-collapse collapse ${carlos:forHtmlAttribute(param.providerNo eq 0 ? 'show' : '')}" aria-labelledby="headingUnmatchedList" data-bs-parent="#inbox-hub-unmatched-list">
                <div class="accordion-body my-2 ms-3">
                    <div class="accordion-item border-0">
                        <div class="accordion-header category-list-header d-flex" id="headingUnmatchedAll">
                            <c:set var="unmatchedDocCount" value="${ categoryData.unmatchedDocs }" />
                            <c:set var="unmatchedLabCount" value="${ categoryData.unmatchedLabs }" />
                            <c:set var="unmatchedHrmCount" value="${ categoryData.unmatchedHRMCount }" />
                            <c:set var="totalUnmatchedCount" value="0" />
                            <c:set var="totalUnmatchedCount" value="${totalUnmatchedCount
                                + (query.doc or allTypes ? categoryData.unmatchedDocs : 0)
                                + (query.lab or allTypes ? categoryData.unmatchedLabs : 0)
                                + (showHRM ? categoryData.unmatchedHRMCount : 0)}" />
                            <span class="collapse-btn" data-bs-toggle="collapse" data-bs-target="#collapseUnmatchedAll" aria-expanded="true" aria-controls="collapseUnmatchedAll"></span>
                            <a id="patient0all" class="text-decoration-none text-wrap text-start collapse-heading btn category-btn py-1 px-0 ms-3" onclick="filterView(0, 'all', this)">
                                <fmt:message key="inbox.inboxmanager.msgAll"/> (<span id="patientNumDocs0">${carlos:forHtml(totalUnmatchedCount)}</span>)
                            </a>
                        </div>
                        <div id="collapseUnmatchedAll" class="accordion-collapse collapse show" aria-labelledby="headingUnmatchedAll">
                            <div class="accordion-body collapse-sub-category-list">
                                <ul class="list-unstyled" id="labdoc0showSublist">
                                    <c:if test="${ not empty categoryData.unmatchedDocs and (query.doc or allTypes) }" >
                                    <li>
                                        <a id="patient0docs" href="javascript:void(0);" class="btn category-btn text-decoration-none" onclick="filterView(0, 'doc', this);" title="<fmt:message key='inboxhub.list.documents'/>">
                                            <fmt:message key="inboxhub.list.documents"/> (<span id="pDocNum_0">${carlos:forHtml(categoryData.unmatchedDocs)}</span>)
                                        </a>
                                    </li>
                                    </c:if>
                                    <c:if test="${ not empty categoryData.unmatchedLabs and (query.lab or allTypes) }" >
                                    <li>
                                        <a id="patient0hl7s" href="javascript:void(0);" class="btn category-btn text-decoration-none" onclick="filterView(0, 'lab', this);" title="<fmt:message key='inboxhub.form.hl7'/>">
                                            <fmt:message key="inboxhub.form.hl7"/> (<span id="pLabNum_0">${carlos:forHtml(categoryData.unmatchedLabs)}</span>)
                                        </a>
                                    </li>
                                    </c:if>
                                    <c:if test="${ not empty categoryData.unmatchedHRMCount and !CarlosProperties.getInstance().isBritishColumbiaBillingRegion() and showHRM}" >
                                    <li>
                                        <a id="patient0hrms" href="javascript:void(0);" class="btn category-btn text-decoration-none" onclick="filterView(0, 'hrm', this);" title="<fmt:message key='inboxhub.form.hrm'/>">
                                            <fmt:message key="inboxhub.form.hrm"/> (<span id="pHRMNum_0">${carlos:forHtml(categoryData.unmatchedHRMCount)}</span>)
                                        </a>
                                    </li>
                                    </c:if>
                                </ul>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
    <!-- End of the Unmatched Accordion -->
    </c:if>
    <c:if test="${ not empty requestScope.categoryData.patientList and !query.unmatched}">
    <!-- Matched List Accordion -->
    <div class="accordion mt-1" id="inbox-hub-matched-list">
        <div class="accordion-item">
            <h2 class="accordion-header" id="headingMatchedList">
                <button class="accordion-button" type="button" data-bs-toggle="collapse" data-bs-target="#collapseMatched" aria-expanded="false" aria-controls="collapseMatched">
                    <fmt:message key="inboxhub.form.matched"/>
                </button>
            </h2>
            <div id="collapseMatched" class="accordion-collapse collapse show" aria-labelledby="headingMatchedList" data-bs-parent="#inbox-hub-matched-list">
                <div class="accordion-body my-2 ms-3">
                    <c:forEach items="${requestScope.categoryData.patientList}" var="patient">
                    <c:set var="patientId" value="${patient.id}" />
                    <c:set var="patientName" value="${ patient.lastName }, ${patient.firstName}" />
                    <%-- Subtracting hrm count from document count because document count include both documents and HRMs 
    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
                    <c:set var="docCount" value="${ patient.docCount - patient.hrmCount }" />
                    <c:set var="labCount" value="${ patient.labCount }" />
                    <c:set var="hrmCount" value="${ patient.hrmCount }" />
                    <c:set var="numDocs" value="0" />
                    <c:set var="numDocs" value="${numDocs
                                + (query.doc or allTypes ? docCount : 0)
                                + (query.lab or allTypes ? labCount : 0)
                                + (showHRM ? hrmCount : 0)}" />
                    <div class="accordion-item border-0">
                        <div class="accordion-header category-list-header d-flex" id="headingPatient${patientId}MatchedAll">
                            <span class="collapse-btn collapsed" data-bs-toggle="collapse" data-bs-target="#collapsePatient${patientId}MatchedAll" aria-expanded="true" aria-controls="collapsePatient${patientId}MatchedAll"></span>
                            <a id="patient${patientId}all" href="javascript:void(0);" class="text-decoration-none text-wrap text-start collapse-heading btn category-btn py-1 px-0 ms-3" onclick="filterView(${patientId}, 'all', this);" title="<carlos:encode value='${patientName}' context="htmlAttribute"/>">
                                <carlos:encode value='${patientName}' context="html"/> (<span id="patientNumDocs${patientId}">${numDocs}</span>)
                            </a>
                        </div>
                        <div id="collapsePatient${patientId}MatchedAll" class="accordion-collapse collapse" aria-labelledby="headingPatient${patientId}MatchedAll">
                            <div class="accordion-body collapse-sub-category-list">
                                <ul class="list-unstyled" id="labdoc${patientId}showSublist">
                                    <c:if test="${not empty docCount and (query.doc or allTypes)}">
                                    <li>
                                        <a id="patient${patientId}docs" href="javascript:void(0);" class="btn category-btn text-decoration-none" onclick="filterView(${patientId}, 'doc', this);" title="<fmt:message key='inboxhub.list.documents'/>">
                                            <fmt:message key="inboxhub.list.documents"/> (<span id="pDocNum_${patientId}">${carlos:forHtml(docCount)}</span>)
                                        </a>
                                    </li>
                                    </c:if>
                                    <c:if test="${not empty labCount and (query.lab or allTypes)}">
                                    <li>
                                        <a id="patient${patientId}hl7s" href="javascript:void(0);" class="btn category-btn text-decoration-none" onclick="filterView(${patientId}, 'lab', this);" title="<fmt:message key='inboxhub.form.hl7'/>">
                                            <fmt:message key="inboxhub.form.hl7"/> (<span id="pLabNum_${patientId}">${carlos:forHtml(labCount)}</span>)
                                        </a>
                                    </li>
                                    </c:if>
                                    <c:if test="${not empty hrmCount and !CarlosProperties.getInstance().isBritishColumbiaBillingRegion() and showHRM}">
                                    <li>
                                        <a id="patient${patientId}hrms" href="javascript:void(0);" class="btn category-btn text-decoration-none" onclick="filterView(${patientId}, 'hrm', this);" title="<fmt:message key='inboxhub.form.hrm'/>">
                                            <fmt:message key="inboxhub.form.hrm"/> (<span id="pLabNum_${patientId}">${carlos:forHtml(hrmCount)}</span>)
                                        </a>
                                    </li>
                                    </c:if>
                                </ul>
                            </div>
                        </div>
                    </div>
                    </c:forEach>
                </div>
            </div>
        </div>
    </div>
    <!-- End of the Matched Accordion -->
    </c:if>
</div>
</c:if>

<div aria-live="polite" aria-atomic="true" class="position-absolute bottom-0 end-0 p-3" style="z-index: 11; display: none;">
    <div id="ajaxErrorToast" class="toast align-items-center text-white bg-danger border-0" role="alert" aria-live="assertive" aria-atomic="true" data-bs-delay="5000">
        <div class="d-flex">
            <div class="toast-body">
                <fmt:message key="inboxhub.form.ajaxError"/>
            </div>
            <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="<fmt:message key='global.btnClose'/>"></button>
        </div>
    </div>
</div>

<script>
    var page = 1;
    var pageSize = 20;
    var inboxhubListProgressWidth = 0;
    var hasMoreData = true;
    var isFetchingData = false;
    var currentFetchRequest = null;
    var inboxSearchFormData = "";
    var filter = "";
    var searchProviderNo = "<carlos:encode value='${sessionScope.user}' context="javaScript"/>";

    jQuery(document).ready( function() {
        toggleInputVisibility('specificProvider', 'specificProviderId', 0);
        toggleInputVisibility('specificPatients', 'specificPatientsId', 0);

        document.getElementById('startDateIcon').addEventListener('click', function() {
            document.getElementById('startDate').focus();
        });
        document.getElementById('endDateIcon').addEventListener('click', function() {
            document.getElementById('endDate').focus();
        });

        // Initialize datepickers and clear buttons
        setupDatepicker('#startDate', '#clearStartDate');
        setupDatepicker('#endDate', '#clearEndDate');

        restoreInboxhubAfterHrmRevoke();
        inboxSearchFormData = jQuery("#inboxSearchForm").serialize();
        fetchInboxhubData();

        autoCompleteProvider();
    });

    function changeValueElementByName(name, newValue) {
        let inPatient = document.getElementsByName(name);
        if (inPatient && inPatient.length > 0) {
            inPatient[0].value = newValue;
        }
    }

    function toggleInputVisibility(selectedRadioId, inputDivId, animationTime) {
        const selectedRadio = document.getElementById(selectedRadioId);
        const inputDiv = jQuery('#' + inputDivId);
        if (selectedRadio.checked) {
            inputDiv.hide().removeClass('d-none').slideDown(animationTime);  // Show with animation and remove 'd-none'
        } else {
            inputDiv.slideUp(animationTime, function() {
                inputDiv.addClass('d-none');  // Hide with animation and add 'd-none'
            });
        }
    }

    function updateInputDisabled(itemName, inputDivId, radioValue) {
        const selectedRadio = sessionStorage.getItem(itemName);
        const inputDiv = document.getElementById(inputDivId);
        const inputs = inputDiv.getElementsByTagName('input');
        let disableVal = true;
        if (selectedRadio === radioValue) {
            disableVal = false;
        }
        for (var i = 0; i < inputs.length; i++) {
            inputs[i].disabled = disableVal;
        }
    }

    function setupDatepicker(dateInputId, clearBtnId) {
        let dateInput = jQuery(dateInputId);
        let clearBtn = jQuery(clearBtnId);
        let inputEl = dateInput[0];

        flatpickr(inputEl, {
            dateFormat: 'Y-m-d',
            allowInput: false,
            onChange: function() {
                clearBtn.toggle(!!dateInput.val());
            },
            onClose: function() {
                clearBtn.toggle(!!dateInput.val());
            }
        });

        // Clear button click handler
        clearBtn.on('click', function() {
            inputEl._flatpickr.clear();
            clearBtn.toggle(false);
        });

        // Initialize clear button visibility
        clearBtn.toggle(!!dateInput.val());
    }

    /**
     * Adds a click event to all links within the '.category-list' to highlight the clicked link.
     */
    function highlightClickedLink(link) {
        // If the clicked link already has the 'selected' class, remove it
        if (link.classList.contains('selected')) {
            link.classList.remove('selected');
        } else {
            // Otherwise, remove 'selected' from all links and add it to the clicked link
            document.querySelectorAll('.category-list a').forEach(item => {
                item.classList.remove('selected');
            });
            link.classList.add('selected');
        }
    }

    function validatePatientOptions() {
        // Get the selected patient option value
        const selectedValue = document.querySelector('input[name="patientsRadios"]:checked').value;

        // If the "patientsOption1" radio is selected, clear the patient details fields
        if (selectedValue === "patientsOption1") {
            ['query.patientFirstName', 'query.patientLastName', 'query.patientHealthNumber'].forEach(fieldName => {
                changeValueElementByName(fieldName, '')
            });
        }

        // If "patientsOption3" is selected, validate the patient details fields
        if (selectedValue === "patientsOption3") {
            // Retrieve the values of the specific patient input fields (first name, last name, health number)
            const fields = ['inputFirstName', 'inputLastName', 'inputHIN'].map(id => 
                document.getElementById(id).value.trim()
            );

            const errorMessage = document.getElementById('specificPatientErrorMessage');
            
            const isAllFieldsEmpty = fields.every(field => field === '');
            
            // If all fields are empty, display the error message and prevent form submission
            if (isAllFieldsEmpty) {
                errorMessage.classList.remove('d-none'); // Show error message
                return false; // Prevent form submission
            }

            // If at least one field is filled, hide the error message
            errorMessage.classList.add('d-none'); // Hide error message
        }

        ShowSpin(true);
        return true;
    }

    function filterView(demographicFilter, typeFilter, link) {
        // Adds a click event to all links within the '.category-list' to highlight the clicked link.
        highlightClickedLink(link);

        filter = link.classList.contains("selected") ? ("&demographicFilter=" + demographicFilter + "&typeFilter=" + typeFilter) : "";
        fetchInboxhubData();
    }

    <fmt:message key="inboxhub.form.listMode" var="listModeLabelVar"/>
    <fmt:message key="inboxhub.form.previewMode" var="previewModeLabelVar"/>
    <fmt:message key="inboxhub.form.percentComplete" var="percentCompleteLabelVar"/>
    var listModeLabel = '${carlos:forJavaScript(listModeLabelVar)}';
    var previewModeLabel = '${carlos:forJavaScript(previewModeLabelVar)}';
    var percentCompleteLabel = '${carlos:forJavaScript(percentCompleteLabelVar)}';

    function fetchInboxhubDataByMode(btnViewMode2) {
        jQuery('#btnViewMode').val(btnViewMode2.checked ? 'true' : 'false');
        jQuery("#btnViewModeLabel").html(btnViewMode2.checked ? listModeLabel : previewModeLabel);
        fetchInboxhubData();
    }

    function fetchInboxhubData() {
        // Re-serialize the search form so programmatic checkbox/radio changes take effect
        inboxSearchFormData = jQuery("#inboxSearchForm").serialize();
        const viewModeBtn = document.getElementById("btnViewMode");
        viewModeBtn.disabled = true;
        resetDataPageCount();
        if (viewModeBtn.value === 'true') {
            fetchInboxhubViewData();
        } else {
            fetchInboxhubListData();
        }
    }

    /**
     * Listens for refresh requests from lab/HRM popup windows after acknowledge or sign-off.
     *
     * A popup cannot be relied on to call fetchInboxhubData() through window.opener: a
     * deployment that sends Cross-Origin-Opener-Policy severs the opener, and a lab shown in
     * an iframe has none to begin with. Nothing in this repository sets that header, and on
     * the packaged install measured for this change the opener was reachable — so it is a
     * useful route, not a dependable one. BroadcastChannel provides same-origin cross-window
     * messaging that works in either case.
     *
     * Senders: oscarMDSIndex.js updateStatus(), hrmActions.js doSignOff()
     * Channel: 'inboxhub-refresh'
     */
    /**
     * A revoked HRM sign-off restores routing rows. Re-submit the current search so the server
     * replaces the totals as well as the result set and clears acknowledgement deduplication.
     * An AJAX list fetch alone keeps both the old totals and countedAcknowledgedItems.
     */
    function refreshInboxhubAfterHrmRevoke() {
        const form = document.getElementById('inboxSearchForm');
        let saved = form.querySelector('input[name="inboxhubRevokeState"]');
        if (!saved) {
            saved = document.createElement('input');
            saved.type = 'hidden';
            saved.name = 'inboxhubRevokeState';
            form.appendChild(saved);
        }
        // Category selection and toolbar modes live outside the form. Carry them in this
        // request so separate inbox tabs keep independent state.
        saved.value = JSON.stringify({filter: filter, activeTypeFilter: activeTypeFilter,
            ackToggleState: ackToggleState, rapidReviewState: rapidReviewState});
        // This is a resync after a committed mutation. An unfinished patient-search edit
        // must not let the search validator cancel it and leave counts/deduplication stale.
        HTMLFormElement.prototype.submit.call(form);
    }

    /** Restores validated display state before the first AJAX result request after a revoke. */
    function restoreInboxhubAfterHrmRevoke() {
        const form = document.getElementById('inboxSearchForm');
        const raw = form.getAttribute('data-revoke-state');
        if (!raw) { return; }
        let state;
        try {
            state = JSON.parse(raw);
        } catch (e) {
            return;
        }
        if (!state || typeof state !== 'object') { return; }
        const category = new URLSearchParams(typeof state.filter === 'string' ? state.filter : '');
        const demographic = category.get('demographicFilter');
        const type = category.get('typeFilter');
        const suffix = {all: 'all', doc: 'docs', lab: 'hl7s', hrm: 'hrms'};
        if (/^\d{1,10}$/.test(demographic || '') && Number(demographic) <= 2147483647
                && Object.prototype.hasOwnProperty.call(suffix, type)) {
            // Reconstruct only known filter parameters; never append the submitted string itself.
            filter = '&demographicFilter=' + demographic + '&typeFilter=' + type;
            const link = document.getElementById('patient' + demographic + suffix[type]);
            if (link) { link.classList.add('selected'); }
        }
        activeTypeFilter = ['DOC', 'HL7', 'HRM'].includes(state.activeTypeFilter)
            ? state.activeTypeFilter : null;
        ackToggleState = state.ackToggleState === true;
        rapidReviewState = state.rapidReviewState === true;
    }

    try {
        const inboxhubRefreshChannel = new BroadcastChannel('inboxhub-refresh');
        inboxhubRefreshChannel.onmessage = function(event) {
            if (event && event.data && event.data.action === 'hrm-revoked') {
                if (event.data.labType === 'HRM' && isInboxhubItemToken(event.data.segmentID)) {
                    refreshInboxhubAfterHrmRevoke();
                }
                return;
            }
            // Senders post {action, segmentID, labType}. A popup running a cached older
            // script can still post the bare string 'refresh'; both must keep working.
            const acknowledgedId = (event && event.data && event.data.segmentID) ? event.data.segmentID : null;
            const acknowledgedType = (event && event.data && event.data.labType) ? event.data.labType : null;
            // How many routing rows the server actually took out of NEW. A lab is stored as
            // one routing row PER VERSION but shown as one collapsed inbox row, so
            // acknowledging a three-version lab clears three of the rows the counters count.
            const acknowledgedRows = (event && event.data) ? event.data.clearedCount : null;
            // Take the item off screen and off the stored totals first. When it was on
            // screen that is the whole job: the badges are re-read from hidden inputs on
            // every draw, so the totals have to move whether or not anything is re-fetched.
            const handledInPlace = acknowledgedId
                ? dropAcknowledgedInboxhubItem(acknowledgedId, acknowledgedType, acknowledgedRows)
                : false;
            if (handledInPlace) {
                // Deliberately NO full re-fetch. fetchInboxhubData re-runs the whole search from
                // page 1 and replaces #inboxhubMode wholesale, which drops the clinician back
                // at the top of a list they had scrolled into, discards every page after the
                // first, and in preview mode reloads every card's iframe — one full lab
                // render each. Acknowledging one item changes no other item, so nothing left
                // on screen needs re-reading. (While preview pages remain unloaded the helper
                // has already asked for the one page the removal can have changed; see
                // resyncInboxhubPreviewBoundary.) Rapid Review has advanced inside the helper
                // too, once per item, so the popup's own window.opener call and this broadcast
                // cannot open two results between them.
            } else {
                // Two reasons to ask the server instead.
                //
                // THE ITEM WAS NOT ON SCREEN: it may have been filtered out, or the inbox may
                // be listing Acknowledged items, where this acknowledgement ADDS a row rather
                // than removing one. Only the server can say which.
                //
                // OR LIST MODE IS STILL LOADING (dropAcknowledgedInboxhubItem reports false for
                // that too), and then dropping the row in place is not enough.
                // The server pages by OFFSET, not by cursor: LabDataController turns the page
                // number into `page - 1` and the DAOs multiply it out
                // (HRMDocumentToProviderDao: `setFirstResult(page * pageSize)`). An
                // acknowledged result leaves the New set, so every later result shifts up by
                // one and the next page number starts one item too far in -- the result on the
                // page boundary is never fetched at all. In an inbox that is a lab nobody
                // looks at, which is the whole failure mode this screen exists to prevent.
                //
                // The client cannot compensate for that shift. One page number drives three
                // windows at two different page sizes (labs at 100 per page, documents and HRM
                // at pageSize), so no arithmetic on it expresses "everything moved up by one
                // item". Re-fetching re-syncs all three. List mode chains loadMoreListData
                // until the whole result set is loaded, so this is a state it leaves on its
                // own within moments; preview mode, which pages only as the clinician scrolls
                // and pays a full lab render per card, re-syncs the boundary page in place
                // instead (see resyncInboxhubPreviewBoundary) and never reaches this branch
                // for an item that was on screen.
                fetchInboxhubData();
                // When Rapid Review is on, open the next item after the refresh completes. For
                // an item that WAS on screen the helper has armed this already; this covers the
                // item that was not (and the bare 'refresh' a patient match posts).
                if (rapidReviewState) {
                    armPendingRapidReview(inboxhubResultSetGeneration);
                }
            }
        };
    } catch (e) {
        // BroadcastChannel unsupported — user must manually refresh the inbox
    }

    /**
     * Takes one item off the STORED Documents/Labs/HRMs totals and re-renders the badges.
     *
     * The badges are painted from these hidden inputs on every list draw, so editing the
     * badge text alone is undone by the next refresh — the stored value is what has to move.
     *
     * The overall total moves only when the type's own total did, and by the same amount.
     * Moving the two independently let a type already at zero walk the "all results" figure
     * below the truth.
     *
     * @param {string} labType 'DOC', 'HRM', or a lab type such as 'HL7'
     * @param {number} rows how many of that type's rows to take off; the caller knows this
     *                 because one acknowledgement can clear several (see clearedCount)
     */
    function decrementInboxhubStatFor(labType, rows) {
        // Zero is a real answer, not a missing one: the server reports it when the item's
        // routing rows had already left NEW, and moving the badge then would take off a row
        // nobody cleared.
        if (!(rows > 0)) { return; }
        const countInputId = labType === 'DOC' ? 'totalDocsCount' :
                             labType === 'HRM' ? 'totalHRMCount' : 'totalLabsCount';
        const typeInput = jQuery('#' + countInputId);
        const typeCount = parseInt(typeInput.val(), 10);
        if (isNaN(typeCount) || typeCount <= 0) { return; }
        // Never below zero: the stored total is a snapshot taken when the page rendered, and
        // an item acknowledged in another window may already be missing from it.
        // CategoryData counts DISTINCT HRM document ids, including when historical duplicate
        // routing rows exist. Labs count their version routing rows. One HRM notification
        // names one report, so even a multi-row transition removes only one HRM from the badge.
        const taken = Math.min(typeCount, labType === 'HRM' ? 1 : rows);
        typeInput.val(typeCount - taken);

        const allInput = jQuery('#totalResultsCount');
        const allCount = parseInt(allInput.val(), 10);
        if (!isNaN(allCount) && allCount > 0) {
            allInput.val(Math.max(0, allCount - taken));
        }
        showInboxhubStats();
    }

    /**
     * Drops one item from whichever inbox view is on screen, without touching any counter.
     *
     * Counting is deliberately separate: a lab's older versions have no row of their own
     * (the inbox collapses a version chain to one row) yet each still holds a routing row
     * that the counters count, so "row removed" and "total moved" are different questions.
     *
     * Popups call in through window.opener and cannot know which mode the inbox is showing,
     * so the mode is decided here, from what is actually on the page. List mode has a
     * DataTable and the row must go through its API or the table's own row bookkeeping keeps
     * the row alive across the next draw; preview mode has cards and no #inbox_table at all,
     * where reaching for the DataTable API would throw and take the rest of the update down
     * with it.
     *
     * Returning whether the item was found is what lets the caller skip the full re-fetch:
     * an acknowledgement dealt with here needs no round trip, and one that found nothing may
     * still need one (see the BroadcastChannel listener).
     *
     * @param {string} segmentId segment id of the item
     * @param {string} labType its report type
     * @return {boolean} true when the item's row or card was found and taken off screen
     */
    function removeInboxhubRow(segmentId, labType) {
        const rowEl = inboxhubItemElement(segmentId, labType);
        if (rowEl.length === 0) {
            // The remembered neighbours are deliberately left alone. This can be the broadcast
            // for an item the popup's window.opener call already removed, or an older version
            // in a lab's chain, which never has a row of its own and is reported after the
            // version that did -- in both cases the neighbours remembered a moment ago are the
            // ones Rapid Review must still open. An item that was never on screen forces a
            // re-fetch, and resetDataPageCount() drops what is stale then.
            return false;
        }
        if (jQuery('#inbox_table').length > 0) {
            // Remember the row that moves up into the acknowledged one's place BEFORE it
            // goes — afterwards there is nothing left to ask — so Rapid Review can open the
            // next result instead of the first row of the table. The DataTable renders its
            // rows in display order and pages nothing (paging: false), so the next <tr> in
            // the DOM is the row the clinician sees below this one, whatever the sort. The
            // row above is remembered too: when the acknowledged row was the LAST one loaded
            // its successor is not on screen yet, and "the row after the one above" is how it
            // is found once it has arrived.
            const row = rowEl.first();
            rememberNextInboxhubItem(row.next('tr'), row.prev('tr'));
            jQuery('#inbox_table').DataTable().row(rowEl).remove().draw(false);
            markInboxhubItemHandled(segmentId, labType, rowEl);
            return true;
        }
        if (jQuery('#inboxViewItems').length > 0) {
            // Same reason as above: Rapid Review advances to the card that took the
            // acknowledged one's place rather than scrolling back to the top of the list.
            const card = rowEl.first();
            rememberNextInboxhubItem(card.next('.document-card'), card.prev('.document-card'));
            card.remove();
            markInboxhubItemHandled(segmentId, labType, rowEl);
            // Nothing is topped up from the server here, and that is deliberate. A removal
            // can shorten the list past the point where #inboxViewItems scrolls, which is how
            // preview mode asks for its next page. Once the inbox holds the whole result set
            // there is no next page to ask for; while pages remain, dropAcknowledgedInboxhubItem
            // re-syncs the boundary page (resyncInboxhubPreviewBoundary), and that merge is
            // what puts the item that shifted into the loaded window on screen. A top-up call
            // here would either be rejected by its own hasMoreData guard or duplicate that
            // re-sync's request.
            return true;
        }
        return false;
    }

    /**
     * Finds one inbox item's element, qualified by report type.
     *
     * A segment id is NOT unique across report types: documents, HRM reports and HL7 labs
     * come from independent key sequences. New markup has type-qualified DOM ids and a
     * data-segment-id; cached older markup can still carry id="labdoc_&lt;id&gt;". Matching the
     * type as well is what stops an acknowledgement removing another type's row and, worse,
     * decrementing another type's total — which a list re-fetch does NOT repair, because the
     * totals are stored, not recomputed.
     *
     * @param {string} segmentId segment id of the item
     * @param {string} labType its report type, or null when the sender did not say
     * @return {Object} jQuery set of matching elements, possibly empty
     */
    function inboxhubItemElement(segmentId, labType) {
        if (!isInboxhubItemToken(segmentId)) { return jQuery(); }
        const candidates = jQuery('[data-segment-id="' + segmentId + '"], [id="labdoc_' + segmentId + '"]');
        if (labType === null || labType === undefined) {
            // No type given (a popup running a cached older script). One match is
            // unambiguous; more than one means the id is shared across report types and
            // picking either would remove a row and decrement a total at random, so this
            // does nothing and leaves it to the list refresh.
            return candidates.length === 1 ? candidates : jQuery();
        }
        if (!isInboxhubItemToken(labType)) { return jQuery(); }
        return candidates.filter('[data-lab-type="' + labType + '"]');
    }

    /**
     * Whether a broadcast value has the shape a real segment id or report type has.
     *
     * Both reach a jQuery attribute selector and the counter bookkeeping from a
     * BroadcastChannel message, so anything outside this shape is rejected rather than
     * interpolated: it keeps the selector literal and stops a malformed id from minting an
     * endless supply of fresh counter keys, each good for one more decrement.
     */
    function isInboxhubItemToken(value) {
        return value !== null && value !== undefined && /^[A-Za-z0-9_-]+$/.test(String(value));
    }

    /**
     * Item keys already taken off the stored totals.
     *
     * Two routes drop an acknowledged item — a popup whose window.opener survived calls
     * removeReport() directly, and the broadcast arrives moments later — and both must not
     * count it twice. The guard is the key, NOT the row's presence in the DOM: the row can
     * already be gone for reasons that have nothing to do with the acknowledgement (the
     * clinician changed a filter while the popup was open), and the totals still have to
     * move. Reset only by a full page load, which is also when the totals are re-rendered.
     */
    var countedAcknowledgedItems = Object.create(null);

    /**
     * Items this window has already taken off screen, keyed as countedAcknowledgedItems is.
     *
     * One acknowledgement can reach the inbox twice — a popup whose window.opener survived
     * calls removeInboxhubRow directly, and the broadcast lands moments later — and by the
     * second arrival the row is already gone. Without this record that arrival cannot tell
     * "already dealt with" from "never on screen", and would fall back to the full re-fetch
     * that costs the clinician their place in the list.
     *
     * Scoped to the CURRENT RESULT SET, which is why it is cleared by resetDataPageCount()
     * and countedAcknowledgedItems is not. The two records answer different questions with
     * different lifetimes: this one is about what is on screen, and a fetch replaces the
     * screen; that one is about the stored totals, which are page-load state a fetch does
     * not re-render. Carrying this one across a fetch makes it claim an item is dealt with
     * in a result set that never showed it — acknowledge in the New view, switch to the
     * Acknowledged view, and a later notification for that same item would be answered with
     * "already handled" instead of the re-fetch that adds its row.
     */
    var handledInboxhubItems = Object.create(null);

    /**
     * Drops the per-result-set record of what this window has taken off screen.
     *
     * Called by resetDataPageCount(), i.e. whenever a fetch is about to replace the rendered
     * result set. Nothing can be "already off screen" once the screen itself is discarded.
     */
    function forgetHandledInboxhubItems() {
        handledInboxhubItems = Object.create(null);
        advancedInboxhubItems = Object.create(null);
    }

    /**
     * The neighbours of an acknowledged item, captured before the removal.
     *
     * Held as identities ({segmentId, labType}) and NOT as elements, because the elements
     * may not survive to be opened: the DataTable redraws its rows after a removal, and on
     * the re-fetch route the whole result set is replaced and the rows are rendered again,
     * on whichever page they now land. Resolving the identities against the DOM at the moment
     * of opening is what makes the same record serve both modes and both advance routes.
     *
     * The successor is what Rapid Review opens. The predecessor is the fallback for when the
     * successor was not on screen at the time — the acknowledged item was the last one loaded
     * — and is found again as "the item after the one above" once the next page, or the
     * preview boundary re-sync, has rendered it. Both null when nothing was remembered, which
     * is when Rapid Review falls back to the first row.
     */
    var nextInboxhubItem = null;
    var previousInboxhubItem = null;

    /**
     * Records the items on either side of the one about to be removed.
     *
     * @param {Object} following jQuery set holding the next row or card, possibly empty
     * @param {Object} preceding jQuery set holding the previous row or card, possibly empty
     */
    function rememberNextInboxhubItem(following, preceding) {
        nextInboxhubItem = inboxhubItemIdentity(following);
        previousInboxhubItem = inboxhubItemIdentity(preceding);
    }

    /** The identity a rendered row or card carries, or null for an empty set or a malformed one. */
    function inboxhubItemIdentity(element) {
        if (!element || element.length === 0) { return null; }
        const segmentId = element.attr('data-segment-id');
        const labType = element.attr('data-lab-type');
        return inboxhubItemKey(segmentId, labType) === null
            ? null : { segmentId: String(segmentId), labType: String(labType) };
    }

    function forgetNextInboxhubItem() {
        nextInboxhubItem = null;
        previousInboxhubItem = null;
    }

    function hasRememberedNextInboxhubItem() {
        return nextInboxhubItem !== null || previousInboxhubItem !== null;
    }

    /**
     * The rendered element Rapid Review should advance to, or an empty set when it is not
     * on screen (yet): nothing was remembered, the item sits on a page not loaded yet, or
     * another window acknowledged it in the meantime.
     *
     * @param {string} kind 'tr' for list rows or '.document-card' for preview cards
     */
    function nextInboxhubElement(kind) {
        if (nextInboxhubItem !== null) {
            const next = inboxhubItemElement(nextInboxhubItem.segmentId, nextInboxhubItem.labType).filter(kind);
            if (next.length > 0) { return next.first(); }
        }
        if (previousInboxhubItem !== null) {
            const before = inboxhubItemElement(previousInboxhubItem.segmentId, previousInboxhubItem.labType).filter(kind);
            if (before.length > 0) { return before.first().next(kind); }
        }
        return jQuery();
    }

    /** The link that opens the next item in LIST mode, or null when it is not rendered. */
    function nextInboxhubListRowLink() {
        const row = nextInboxhubElement('tr');
        if (row.length === 0) { return null; }
        const link = row.find('a');
        return link.length > 0 ? link[0] : null;
    }

    /**
     * The per-item bookkeeping key, or null when the message did not name a usable item.
     *
     * Type-qualified because segment ids are NOT unique across report types: documents, HRM
     * reports and HL7 labs come from independent key sequences, so an id on its own can name
     * another type's item.
     */
    function inboxhubItemKey(segmentId, labType) {
        if (!isInboxhubItemToken(segmentId) || !isInboxhubItemToken(labType)) { return null; }
        return labType + ':' + segmentId;
    }

    /**
     * Records that this window has already taken an item off screen.
     *
     * The type can be absent when a popup running a cached older script calls in with an id
     * alone; the rendered element is then the one place it can come from, which is why the
     * element found by the caller is passed in rather than looked up a second time.
     *
     * @param {string} segmentId segment id of the item
     * @param {string} labType its report type, or null when the caller did not say
     * @param {Object} itemEl the element that was removed, used to recover a missing type
     */
    function markInboxhubItemHandled(segmentId, labType, itemEl) {
        const resolvedType = labType || (itemEl && itemEl.length > 0 ? itemEl.data('labType') : null);
        const key = inboxhubItemKey(segmentId, resolvedType);
        if (key !== null) { handledInboxhubItems[key] = true; }
    }

    /**
     * Whether this window has already taken the named item off screen.
     */
    function isInboxhubItemHandled(segmentId, labType) {
        const key = inboxhubItemKey(segmentId, labType);
        return key !== null && handledInboxhubItems[key] === true;
    }

    /**
     * Takes an acknowledged item off the stored totals, at most once per item.
     *
     * Lab totals count ROUTING rows, not the rows drawn in the list: a lab is stored as one
     * routing row per version and collapsed to a single inbox row, so acknowledging it
     * clears as many rows as the chain is long. HRM totals count distinct documents instead,
     * so decrementInboxhubStatFor caps a positive HRM transition at one. The amount is a parameter and
     * not assumed to be one — the server is the only party that knows how many it filed.
     *
     * @param {string} segmentId segment id of the acknowledged item
     * @param {string} labType its report type; without one there is no total to pick
     * @param {number} clearedCount routing rows the server cleared; defaults to one when the
     *                 sender did not say (documents and HRM reports have no version chain,
     *                 and a popup running a cached older script sends nothing)
     */
    function countAcknowledgedInboxhubItem(segmentId, labType, clearedCount) {
        const key = inboxhubItemKey(segmentId, labType);
        if (key === null) { return; }
        if (countedAcknowledgedItems[key]) { return; }
        // A zero consumes nothing. Two windows can acknowledge the same item at once: whichever
        // request commits second finds the rows already out of NEW and reports 0, and that
        // message can arrive FIRST. Marking the item counted on it discarded the positive count
        // that followed, leaving the badge high until a full reload. Zero is still a real answer
        // — it just never moves the total, so there is nothing to record as spent.
        const clearedRows = clearedRowsFrom(clearedCount);
        if (clearedRows === 0) { return; }
        countedAcknowledgedItems[key] = true;
        decrementInboxhubStatFor(labType, clearedRows);
    }

    /**
     * How many rows a message says were cleared: its own number, or one when it did not say.
     *
     * The distinction matters because ZERO is a real answer. The server reports it when the
     * item's routing rows had already left NEW — a second window acknowledging a lab the
     * clinician has already dealt with, a popup left open from before, or an inbox being
     * viewed on another provider's behalf, where the acknowledgement writes the session
     * provider's row and the badge is counting somebody else's. Reading zero as "the sender
     * did not say" and moving the badge by one drops a row nobody cleared, and only a full
     * page reload puts it back.
     *
     * Absent is still one: documents, HRM reports and a popup running a cached older script
     * all send nothing, and for them one item is one row.
     */
    function clearedRowsFrom(clearedCount) {
        if (clearedCount === null || clearedCount === undefined) { return 1; }
        const rows = parseInt(clearedCount, 10);
        return isNaN(rows) ? 1 : Math.max(0, rows);
    }

    /**
     * Removes an acknowledged item from the inbox and from its counters.
     *
     * @param {string} segmentId segment id of the acknowledged lab, document or HRM report
     * @param {string} labType its report type; older senders may not supply one
     * @param {number} clearedCount routing rows the acknowledgement cleared; see above
     * @return {boolean} true when the item was on screen AND the inbox holds the whole result
     *                   set, so nothing needs re-fetching; false when the server must be asked
     */
    function dropAcknowledgedInboxhubItem(segmentId, labType, clearedCount) {
        const itemEl = inboxhubItemElement(segmentId, labType);
        // A sender that predates the typed message — a popup running a cached script — gives
        // only an id, and then the rendered row is the one place the type can come from.
        const resolvedType = labType || (itemEl.length > 0 ? itemEl.data('labType') : null);
        removeInboxhubRow(segmentId, resolvedType);
        // The total moves whether or not a row was on screen — the clinician may have changed
        // a filter while the popup was open. Row removal and counting are separate for that
        // reason, and the per-item key keeps this and removeReport from counting it twice.
        countAcknowledgedInboxhubItem(segmentId, resolvedType, clearedCount);
        // Not "did THIS call remove something": the popup's direct window.opener route may
        // have removed it already, and that is just as much a reason to skip the re-fetch.
        if (!isInboxhubItemHandled(segmentId, resolvedType)) { return false; }
        // The paging condition belongs HERE, in the contract, rather than in the caller. Three
        // routes reach this function -- the BroadcastChannel listener, labDisplay.jsp's
        // dropFromInboxhubDirectly() for a browser without BroadcastChannel, and the legacy
        // removeReport() opener entry point -- and a gate applied in one of them leaves the
        // others reintroducing the page-boundary bug. What every caller actually needs to
        // know is "is a full re-sync still required", so that is what this answers. Once
        // hasMoreData is false no later page will ever be asked for, so no offset can be
        // skipped. While pages remain, preview mode re-syncs the one page the removal can
        // have changed and reports that nothing more is needed; list mode, still chaining its
        // pages in, leaves the answer to the full re-fetch.
        const settled = !hasMoreData || resyncInboxhubPreviewBoundary();
        // Rapid Review is decided here for the same reason: whichever route delivered the
        // acknowledgement, the clinician expects the next result to open. Settled in place,
        // the next item is on screen and opens now -- at most once per item, because the
        // opener call and the broadcast that follows it both land here. Not settled, the
        // caller re-fetches (or list mode is still paging in on its own), and the advance
        // follows the redraw that renders the remembered row.
        if (rapidReviewState) {
            if (settled) {
                advanceRapidReviewOnce(segmentId, resolvedType);
            } else {
                // The caller's re-fetch is one resetDataPageCount() away; the advance is for
                // the result set that reset brings, not for whatever the clinician searches
                // for later.
                armPendingRapidReview(inboxhubResultSetGeneration + 1);
            }
        }
        return settled;
    }

    /**
     * Items Rapid Review has already advanced past, keyed as handledInboxhubItems is.
     *
     * One acknowledgement reaches dropAcknowledgedInboxhubItem up to twice -- the popup's
     * direct window.opener call and its broadcast -- and opening the next result twice would
     * hand the clinician the successor and then, the remembered item being spent, the first
     * row. Same lifetime as the handled record: a fetch replaces the screen and with it what
     * "already advanced" was about.
     */
    var advancedInboxhubItems = Object.create(null);

    /**
     * Opens the next result for Rapid Review, once per acknowledged item.
     */
    function advanceRapidReviewOnce(segmentId, labType) {
        const key = inboxhubItemKey(segmentId, labType);
        if (key !== null) {
            if (advancedInboxhubItems[key]) { return; }
            advancedInboxhubItems[key] = true;
        }
        openNextInboxItem();
    }

    /**
     * Bumped every time a fetch replaces the rendered result set, so a boundary re-sync that
     * was in flight when the clinician changed the search cannot merge cards from the old
     * query into the new list.
     */
    var inboxhubResultSetGeneration = 0;

    /**
     * Preview mode, pages still unloaded, one item just taken off screen: asks the server for
     * the ONE page that removal can have changed, and merges the answer into the rendered
     * cards without touching any of them.
     *
     * The server pages by offset (see the BroadcastChannel listener). Removing one result
     * shifts every later result up a place, so the item that sat first on the next unloaded
     * page now sits last on the last loaded one — and that is the only item on any loaded
     * page that this window has not rendered. Every earlier page's window gains only an item
     * that was already rendered from the page after it. Re-fetching the last loaded page is
     * therefore a complete re-sync of what is on screen, at the cost of one card render
     * instead of one per card; the full re-fetch it replaces re-rendered every iframe on the
     * page and dropped every page after the first.
     *
     * Preview increments `page` after each page it appends, so the last loaded page is
     * `page - 1`. A scroll-triggered fetch of the next page may be in flight at the same
     * time; the merge de-duplicates by item identity, so whichever answer lands second adds
     * nothing twice.
     *
     * @return {boolean} true when this is preview mode and the re-sync was started (or is
     *                   moot), so the caller must NOT fall back to the full re-fetch; false in
     *                   list mode, which still needs one
     */
    function resyncInboxhubPreviewBoundary() {
        if (jQuery('#inboxViewItems').length === 0) { return false; }
        // A next-page request already in flight may have been computed BEFORE the
        // acknowledgement committed. Appended as it is, it would carry the pre-shift window
        // while every later page is fetched post-shift, and the result on ITS far boundary
        // would never be fetched by anyone. So it is withdrawn here, the boundary page is
        // re-synced against what is actually on screen, and the same page is asked for again
        // — post-shift — once the merge is done. (A response that has already landed needs
        // nothing of the sort: it is then the last loaded page, and the page re-synced below.)
        let resumePaging = false;
        if (isFetchingData && currentFetchRequest) {
            currentFetchRequest.abort();
            isFetchingData = false;
            resumePaging = true;
        }
        const boundaryPage = page - 1;
        if (boundaryPage < 1) {
            if (resumePaging) { fetchInboxhubViewData(); }
            return true;
        }
        const generation = inboxhubResultSetGeneration;
        const url = inboxContextPath + "/web/inboxhub/Inboxhub?method=displayInboxView";
        jQuery.ajax({
            url: url,
            method: 'POST',
            data: inboxSearchFormData + filter + "&page=" + boundaryPage + "&pageSize=" + pageSize,
            success: function(data) {
                if (generation !== inboxhubResultSetGeneration) { return; }
                mergeInboxhubPreviewCards(data);
                if (resumePaging) { fetchInboxhubViewData(); }
            },
            error: function(xhr, status) {
                // The full re-fetch is the safe answer when the boundary page cannot be read:
                // slower, but it never leaves a result unfetched.
                if (status !== 'abort' && generation === inboxhubResultSetGeneration) {
                    fetchInboxhubData();
                }
            }
        });
        return true;
    }

    /**
     * Merges a re-fetched preview page into the cards already on screen.
     *
     * Cards already rendered are LEFT WHERE THEY ARE — moving an iframe in the DOM reloads
     * its document, which is the very cost this exists to avoid — and only the cards the
     * window has never shown are inserted, each next to the neighbour the server listed it
     * beside so the page keeps the server's order. A card this window itself took off
     * screen is not put back, even if a stale answer still lists it. The page's own scripts
     * are not run: the only one that matters is the end-of-results flag, which is read off
     * the response text instead.
     *
     * @param {string} data the HTML the server rendered for one preview page
     */
    function mergeInboxhubPreviewCards(data) {
        const container = document.getElementById('inboxViewItems');
        if (!container) { return; }
        const fetched = new DOMParser().parseFromString(data, 'text/html').querySelectorAll('.document-card');
        const entries = [];
        fetched.forEach(function(card) {
            const segmentId = card.getAttribute('data-segment-id');
            const labType = card.getAttribute('data-lab-type');
            if (inboxhubItemKey(segmentId, labType) === null) { return; }
            const rendered = inboxhubItemElement(segmentId, labType).filter('.document-card');
            entries.push({ card: card, segmentId: segmentId, labType: labType,
                rendered: rendered.length > 0 ? rendered[0] : null });
        });
        entries.forEach(function(entry, at) {
            if (entry.rendered !== null || isInboxhubItemHandled(entry.segmentId, entry.labType)) { return; }
            let anchor = null;
            for (let before = at - 1; before >= 0 && anchor === null; before--) {
                if (entries[before].rendered !== null) { anchor = entries[before].rendered; }
            }
            if (anchor !== null) {
                anchor.after(entry.card);
            } else {
                let following = null;
                for (let next = at + 1; next < entries.length && following === null; next++) {
                    if (entries[next].rendered !== null) { following = entries[next].rendered; }
                }
                if (following !== null) { following.before(entry.card); } else { container.append(entry.card); }
            }
            entry.rendered = entry.card;
        });
        if (/hasMoreData\s*=\s*false/.test(data)) { hasMoreData = false; }
        settlePendingPreviewAdvance();
    }

    /**
     * Resets all inbox filters to defaults by reloading the page with no parameters.
     * This ensures counts, filters, and toolbar state all return to the default view
     * showing New items for the current provider.
     */
    function resetInboxFilters() {
        var ctxPath = "<carlos:encode value='${pageContext.request.contextPath}' context="javaScript"/>";
        window.location.href = ctxPath + '/web/inboxhub/Inboxhub?method=displayInboxForm';
    }

    /**
     * Whether Rapid Review still owes the clinician the next result, and for which result set.
     *
     * Armed when the next item could not be opened at once: the caller is about to re-fetch
     * (list mode still loading, or the item was not on screen), or preview is fetching the
     * card that took the acknowledged one's place. The generation says which result set the
     * advance belongs to, so resetDataPageCount() can tell the re-fetch it expects from a
     * search the clinician makes later — the latter must not open a row of the new list.
     */
    var pendingRapidReviewOpen = false;
    var pendingRapidReviewGeneration = 0;

    function armPendingRapidReview(generation) {
        pendingRapidReviewOpen = true;
        pendingRapidReviewGeneration = generation;
    }

    /**
     * Rapid Review auto-advance, after the acknowledged item was dropped in place.
     *
     * Nothing is being re-fetched on this route, so the next item is already rendered and
     * can be reached at once. In list mode that is the row that took the acknowledged
     * one's place — NOT the first row of the table, which is where a clinician who started
     * part-way down the list used to be sent back to — and only when nothing followed does
     * this fall back to the top. Preview mode has no link to open, so it brings the card that
     * took the acknowledged one's place into view instead; when that card is still on its way
     * (the acknowledged one was the last loaded and the boundary re-sync is fetching its
     * successor) the advance waits for the merge. Either way the clinician stays where they
     * were working.
     */
    function openNextInboxItem() {
        if (jQuery('#inbox_table').length > 0) {
            const nextLink = nextInboxhubListRowLink() || document.querySelector('#inbox_table tbody tr a');
            forgetNextInboxhubItem();
            if (nextLink) {
                nextLink.click();
            }
            return;
        }
        const nextCard = nextInboxhubElement('.document-card');
        if (nextCard.length > 0) {
            forgetNextInboxhubItem();
            nextCard[0].scrollIntoView({ block: 'start' });
            return;
        }
        if (hasMoreData && hasRememberedNextInboxhubItem()) {
            armPendingRapidReview(inboxhubResultSetGeneration);
        } else {
            forgetNextInboxhubItem();
        }
    }

    /**
     * Rapid Review auto-advance on the re-fetch route, run after each list page is drawn.
     *
     * Only for the path that could not drop the item in place and asked the server for a
     * fresh list. The row to open is the one that followed the acknowledged item, remembered
     * before the re-fetch discarded the screen — or, when the acknowledged row was the last
     * one loaded, the row after the one that preceded it; it is opened as soon as a drawn
     * page holds it. The re-fetched list arrives one page at a time (labs 100 to a page,
     * documents and HRM at pageSize), so a row from further down the inbox may not be on
     * page one — this waits while pages remain, and falls back to the first row only once
     * the whole result set is loaded and the row is genuinely gone (acknowledged from
     * another window), or when nothing was remembered in the first place.
     *
     * Called from addDataInInboxhubListTable AFTER the DataTable has drawn, so the row it
     * looks for is rendered and in its final order.
     */
    function advancePendingRapidReview() {
        if (!pendingRapidReviewOpen) { return; }
        const nextLink = nextInboxhubListRowLink();
        if (nextLink) {
            pendingRapidReviewOpen = false;
            forgetNextInboxhubItem();
            nextLink.click();
            return;
        }
        if (hasRememberedNextInboxhubItem() && hasMoreData) { return; }
        pendingRapidReviewOpen = false;
        forgetNextInboxhubItem();
        const firstLink = document.querySelector('#inbox_table tbody tr a');
        if (firstLink) {
            firstLink.click();
        }
    }

    /**
     * Rapid Review auto-advance in preview mode once more cards have arrived — from the
     * boundary re-sync or from the next page — for an acknowledged card whose successor was
     * not on screen at the time. Scrolls to it if it is now rendered; gives up once nothing
     * more will arrive.
     */
    function settlePendingPreviewAdvance() {
        if (!pendingRapidReviewOpen || jQuery('#inboxViewItems').length === 0) { return; }
        const nextCard = nextInboxhubElement('.document-card');
        if (nextCard.length > 0) {
            pendingRapidReviewOpen = false;
            forgetNextInboxhubItem();
            nextCard[0].scrollIntoView({ block: 'start' });
            return;
        }
        if (!hasRememberedNextInboxhubItem() || !hasMoreData) {
            pendingRapidReviewOpen = false;
            forgetNextInboxhubItem();
        }
    }

    // State variables preserved across inbox refreshes (the toolbar HTML inside
    // #inboxhubMode is replaced on each fetch, so checkbox state must be restored)
    var activeTypeFilter = null;
    var ackToggleState = false;
    var rapidReviewState = false;

    /**
     * Filters the inbox to show only one type (DOC, HL7, HRM) or all types.
     * Clicking the active filter or "ALL" resets to show everything.
     * Updates the type checkboxes in the search panel and highlights the active badge.
     *
     * @param {string} type - 'ALL', 'DOC', 'HL7', or 'HRM'
     */
    function filterByType(type) {
        var btnDoc = document.getElementById('btnDoc');
        var btnLab = document.getElementById('btnLab');
        var btnHRM = document.getElementById('btnHRM');

        if (type === 'ALL' || activeTypeFilter === type) {
            // Reset to show all types
            if (btnDoc) btnDoc.checked = true;
            if (btnLab) btnLab.checked = true;
            if (btnHRM) btnHRM.checked = true;
            activeTypeFilter = null;
        } else {
            // Filter to only the selected type
            if (btnDoc) btnDoc.checked = (type === 'DOC');
            if (btnLab) btnLab.checked = (type === 'HL7');
            if (btnHRM) btnHRM.checked = (type === 'HRM');
            activeTypeFilter = type;
        }
        highlightActiveTypeFilter();
        fetchInboxhubData();
    }

    /**
     * Highlights the active type filter badge and removes highlight from others.
     * Called after filterByType changes the active filter.
     */
    function highlightActiveTypeFilter() {
        var filters = document.querySelectorAll('.inbox-type-filter');
        for (var i = 0; i < filters.length; i++) {
            filters[i].classList.remove('active-type-filter');
        }
        var activeId = activeTypeFilter ? ('filter' + activeTypeFilter) : 'filterAll';
        var activeEl = document.getElementById(activeId);
        if (activeEl) activeEl.classList.add('active-type-filter');
    }

    /**
     * Toggles the inbox between showing New items and Acknowledged items.
     * Updates the hidden status filter field and refreshes the inbox data.
     *
     * @param {boolean} checked - true to show Acknowledged, false to show New
     */
    function toggleAcknowledged(checked) {
        ackToggleState = checked;
        changeValueElementByName('query.status', checked ? 'A' : 'N');
        // Also update the radio buttons in the search panel to stay in sync
        var radioId = checked ? 'statusAcknowledged' : 'statusNew';
        var radio = document.getElementById(radioId);
        if (radio) radio.checked = true;

        fetchInboxhubData();
    }

    /**
     * Toggles Rapid Review mode. When enabled, acknowledging a lab in the popup
     * automatically opens the next item in the list for sequential review.
     *
     * @param {boolean} checked - true to enable auto-advance, false to disable
     */
    function toggleRapidReview(checked) {
        rapidReviewState = checked;
    }

    /**
     * Restores toolbar toggle states after the inbox HTML is replaced.
     * Called from addDataInInboxhubListTable when page 1 data is loaded.
     */
    function restoreToolbarState() {
        var ackToggle = document.getElementById('ackToggle');
        if (ackToggle) ackToggle.checked = ackToggleState;
        var rapidToggle = document.getElementById('rapidReviewToggle');
        if (rapidToggle) rapidToggle.checked = rapidReviewState;
        highlightActiveTypeFilter();
    }

    function fetchInboxhubListData() {
        if (!hasMoreData || isFetchingData) { return; }
        isFetchingData = true; 
        const url = "<carlos:encode value='${pageContext.request.contextPath}' context="javaScript"/>/web/inboxhub/Inboxhub?method=displayInboxList";
        currentFetchRequest = jQuery.ajax({
			url: url,
			method: 'POST',
			data: inboxSearchFormData + filter + "&page=" + page + "&pageSize=" + pageSize,		
			success: function(data) {
                HideSpin();
                addDataInInboxhubListTable(data);
                isFetchingData = false;
                jQuery('#btnViewMode').prop('disabled', false);
                loadMoreListData();
			},
            error: function(xhr, status, error) {
                if (status !== 'abort') { toastErrorMessage(); }
                jQuery('#btnViewMode').prop('disabled', false);
                HideSpin();
            }
        });
    }

    function fetchInboxhubViewData() {
        if (!hasMoreData || isFetchingData) { return; }
        ShowSpin(true);
        isFetchingData = true;
        const url = "<carlos:encode value='${pageContext.request.contextPath}' context="javaScript"/>/web/inboxhub/Inboxhub?method=displayInboxView";
        currentFetchRequest = jQuery.ajax({
			url: url,
			method: 'POST',
			data: inboxSearchFormData + filter + "&page=" + page + "&pageSize=" + pageSize,			
			success: function(data) {
                HideSpin();
                addDataInInboxhubViewTable(data);
                isFetchingData = false;
                jQuery('#btnViewMode').prop('disabled', false);
                page++;
			},
            error: function(xhr, status, error) {
                if (status !== 'abort') { toastErrorMessage(); }
                jQuery('#btnViewMode').prop('disabled', false);
                HideSpin();
            }
        });
    }

    function addDataInInboxhubListTable(data) {
        if (page == 1) {
            jQuery("#inboxhubMode").html(data);
            jQuery('#inbox_table').DataTable().draw(false); // `draw(false)` prevents resetting the scroll position
            showInboxhubStats();
            restoreToolbarState();
            startInboxhubListProgress();
            updateInboxhubListProgress();
            // Rapid Review auto-open: after acknowledging an item, open the next one. After the
            // draw, so the row is rendered and in its final order; on every page, because the
            // row may not be on this one.
            advancePendingRapidReview();
            return;
        }

        let inboxhubListTable = jQuery('#inbox_table').DataTable();

        // Check if the string contains <script> tags
        if (!containsScriptTag(data)) {
            // Split the concatenated rows by the closing </tr> tag, and re-add </tr> to each split part
            const splitRows = data.split(/<\/tr>/i).map(row => row + '</tr>').filter(row => row.trim() !== '</tr>');
            // Add rows to DataTable without destroying it
            jQuery.each(splitRows, function(index, row) {
                inboxhubListTable.row.add(jQuery(row));
            });

            // Redraw the table
            inboxhubListTable.draw(false); // `draw(false)` prevents resetting the scroll position
        } else {
            jQuery("#inboxhubMode").append(data);
        }

        updateInboxhubListProgress();
        advancePendingRapidReview();
    }

    /**
     * Helper function to detect presence of <script> tags (in any weird browser-accepted form) in an HTML string.
     */
    function containsScriptTag(htmlString) {
        try {
            const parser = new DOMParser();
            // Parse as text/html for browser compliance
            const doc = parser.parseFromString(htmlString, 'text/html');
            return doc.getElementsByTagName('script').length > 0;
        } catch (e) {
            // If parsing fails, err on the side of caution
            return true;
        }
    }

    function addDataInInboxhubViewTable(data) {
        if (page == 1) {
            jQuery("#inboxhubMode").html(data);
        } else {
            jQuery("#inboxViewItems").append(data);
        }
        settlePendingPreviewAdvance();
    }

    function autoCompleteProvider() {
        jQuery("#autocompleteProvider").autocomplete({
            source: inboxContextPath + "/provider/SearchProvider?method=labSearch",
            minLength: 2,
            focus: function (event, ui) {
                jQuery("#autocompleteProvider").val(ui.item.label);
                return false;
            },
            select: function (event, ui) {
                jQuery("#autocompleteProvider").val(ui.item.label);
                jQuery("#findProvider").val(ui.item.value);
                return false;
            }
        })
    }

    function loadMoreListData() {
        page++;
        fetchInboxhubListData();
    }

    function resetDataPageCount() {
        ShowSpin(true);

        // Enable search and hide the spinner if it is present.
        jQuery('#inboxhubFormSearchBtn').prop('disabled', false); // Enable search button
        jQuery('#inboxhubFormSearchSpinner').hide(); // Hide spinner

        if (currentFetchRequest) {
            currentFetchRequest.abort();  // Cancel the ongoing AJAX request
        }
        jQuery("#inboxhubMode").empty();
        // The rendered result set is going away, so what this window took off screen is no
        // longer a statement about anything. See handledInboxhubItems for why the counted
        // record, whose totals survive the fetch, deliberately does NOT follow it here.
        forgetHandledInboxhubItems();
        inboxhubResultSetGeneration++;
        // A pending Rapid Review advance belongs to ONE result set: the re-fetch an
        // acknowledgement asked for. That re-fetch is this very reset, and the advance was
        // armed for the generation it creates. Any other reset is the clinician searching
        // for something else, and the remembered neighbours of a row in the OLD list must
        // not open a row of the new one.
        if (!pendingRapidReviewOpen || pendingRapidReviewGeneration !== inboxhubResultSetGeneration) {
            pendingRapidReviewOpen = false;
            forgetNextInboxhubItem();
        }
        page = 1;
        hasMoreData = true;
        isFetchingData = false;
    }

    // Show the toast and wrapper div
    function toastErrorMessage() {
        jQuery('#ajaxErrorToast').parent().css('display', 'block'); // Show the wrapper
        bootstrap.Toast.getOrCreateInstance(document.getElementById('ajaxErrorToast')).show(); // Show the toast
    }

    // Hide the toast and wrapper div completely when the toast is dismissed
    document.getElementById('ajaxErrorToast').addEventListener('hidden.bs.toast', function () {
        this.parentElement.style.display = 'none'; // Hide the wrapper to prevent background blocking
    });

    function showInboxhubStats() {
        jQuery('#totalDocsCountStat').text(jQuery('#totalDocsCount').val());
        jQuery('#totalLabsCountStat').text(jQuery('#totalLabsCount').val());
        jQuery('#totalHRMsCountStat').text(jQuery('#totalHRMCount').val());
    }

    function startInboxhubListProgress() {
        const totalResultsCount = jQuery("#totalResultsCount").val();
        inboxhubListProgressWidth = 0;
        jQuery('#loadInboxListProgressBar').attr('aria-valuemax', totalResultsCount);
        jQuery('#loadInboxListProgressBar').css('width', inboxhubListProgressWidth + '%').attr('aria-valuenow', inboxhubListProgressWidth);
        jQuery('#inboxListProgressCount').text(inboxhubListProgressWidth + percentCompleteLabel);
        
        jQuery('#inboxhubFormSearchBtn').prop('disabled', true); // Disable button
        jQuery('#inboxhubFormSearchSpinner').show(); // Show spinner
        jQuery('#stopLoadingInboxList').show(); // Show stop button
        jQuery('#loadInboxListProgress').show(); // Show progress bar
        jQuery('#loadingLabel').show();
    }

    function stopInboxhubListProgress(hideTime) {
        if (currentFetchRequest) {
            currentFetchRequest.abort();  // Cancel the ongoing AJAX request
        }
        hasMoreData = false;
        jQuery('#inboxhubFormSearchBtn').prop('disabled', false); // Enable search button
        jQuery('#inboxhubFormSearchSpinner').hide(); // Hide spinner
        setTimeout(function() {
            jQuery('#stopLoadingInboxList').hide(); // Hide stop button
            jQuery('#loadInboxListProgress').hide(); // Hide progress bar
            jQuery('#loadingLabel').hide();
        }, hideTime);
    }

    function updateInboxhubListProgress() {
        const totalResultsCount = jQuery("#totalResultsCount").val();
        const currentlyLoadedResultsCount = jQuery('#inboxhubListModeTableBody tr').length;

        if (totalResultsCount < currentlyLoadedResultsCount && hasMoreData) {
            return;
        }

        if (totalResultsCount >= currentlyLoadedResultsCount && hasMoreData) {
            const percentage = (currentlyLoadedResultsCount / totalResultsCount) * 100;
            const formattedPercentage = percentage === 100 ? '100' : percentage.toFixed(2); // Keep 2 digits after decimal
            jQuery('#loadInboxListProgressBar').css('width', formattedPercentage + '%').attr('aria-valuenow', currentlyLoadedResultsCount);
            jQuery('#inboxListProgressCount').text(formattedPercentage + percentCompleteLabel);
            return;
        }

        if (!hasMoreData) {
            jQuery('#loadInboxListProgressBar').css('width', 100 + '%').attr('aria-valuenow', totalResultsCount);
            jQuery('#inboxListProgressCount').text(100 + percentCompleteLabel);
            stopInboxhubListProgress(1000);
        }
    }
</script>
