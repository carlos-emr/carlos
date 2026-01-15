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
<%@ page import="ca.openosp.OscarProperties" %>
<%@ page import="ca.openosp.openo.lab.ca.on.*" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="https://www.owasp.org/index.php/OWASP_Java_Encoder_Project" prefix="e" %>
<%@page import="ca.openosp.openo.utility.MiscUtils,org.apache.commons.text.StringEscapeUtils" %>
<%@page import="org.apache.logging.log4j.Logger,ca.openosp.openo.commn.dao.OscarLogDao,ca.openosp.openo.utility.SpringUtils" %>
<%@page import="ca.openosp.openo.inboxhub.query.InboxhubQuery" %>
<%@ page import="ca.openosp.openo.mds.data.CategoryData" %>
<%@ page import="org.owasp.encoder.Encode" %>

<fmt:setBundle basename="oscarResources"/>

<!DOCTYPE html>

<input type="hidden" class="totalDocsCount" id="totalDocsCount" value="${totalDocsCount}" />
<input type="hidden" class="totalLabsCount" id="totalLabsCount" value="${totalLabsCount}" />
<input type="hidden" class="totalHRMCount" id="totalHRMCount" value="${totalHRMCount}" />
<input type="hidden" class="totalResultsCount" id="totalResultsCount" value="${totalResultsCount}" />

<!-- Preview button -->
<div class="card mb-1 shadow-sm rounded-1">
  <div class="card-body p-2">
    <div class="d-grid">
        <input type="checkbox" class="btn-check btn-sm" name="viewMode2" id="btnViewMode2" autocomplete="off" onchange="fetchInboxhubDataByMode(this)" ${query.viewMode ? 'checked' : ''}>
        <label class="btn btn-secondary btn-sm" id="btnViewModeLabel" for="btnViewMode2"><c:out value="${query.viewMode ? 'List Mode' : 'Preview Mode'}" /></label>
    </div>
  </div>
</div>

<!-- Search form Accordion -->
<div class="accordion" id="inbox-hub-search">
    <div class="accordion-item">
        <h2 class="accordion-header" id="headingSearch">
            <button class="accordion-button" type="button" data-bs-toggle="collapse" data-bs-target="#collapseSearch" aria-expanded="true" aria-controls="collapseSearch">
                Search
            </button>
        </h2>
        <div id="collapseSearch" class="accordion-collapse collapse show" aria-labelledby="headingSearch" data-bs-parent="#inbox-hub-search">
            <div class="accordion-body">
                 <form action="${pageContext.request.contextPath}/web/inboxhub/Inboxhub.do?method=displayInboxForm" method="post" id="inboxSearchForm" onsubmit="return validatePatientOptions();">
                    <div class="m-2">
                        <input type="hidden" name="query.viewMode" id="btnViewMode" value="${query.viewMode ? 'true' : 'false'}">

                        <div class="mb-1">
                            <!--Provider-->
                            <label class="fw-bold text-uppercase">
                                <fmt:message key="inbox.inboxmanager.msgProviders"/>
                            </label>
                            <input type="hidden" name="query.searchAll" id="searchProviderAll" value="${query.searchAll}"/>
                            <!-- Any Provider -->
                            <div class="form-check">
                                <input class="btn-check-input" type="radio" name="providerRadios" value="option1" id="anyProvider" ${query.searchAll eq 'true' ? 'checked' : ''} onClick="changeValueElementByName('query.searchAll', 'true');toggleInputVisibility('specificProvider', 'specificProviderId', 200);"/>
                                <label class="form-check-label" for="anyProvider"><fmt:message key="oscarMDS.search.formAnyProvider"/></label>
                            </div>
                            <!-- No Provier -->
                            <div class="form-check">
                                <input class="btn-check-input" type="radio" name="providerRadios" value="option2" id="noProvider" ${query.searchAll eq 'false' ? 'checked' : ''} onClick="changeValueElementByName('query.searchAll', 'false');toggleInputVisibility('specificProvider', 'specificProviderId', 200);"/>
                                <label class="form-check-label" for="noProvider"><fmt:message key="oscarMDS.search.formNoProvider"/></label>
                            </div>
                            <!-- Specific Provider -->
                            <div class="form-check">
                                <input class="btn-check-input" type="radio" name="providerRadios" value="option3" id="specificProvider" ${query.searchAll eq '' ? 'checked' : ''} onclick="changeValueElementByName('query.searchAll', ''); changeValueElementByName('query.searchProviderNo', document.getElementsByName('query.searchProviderNo')[0].value);toggleInputVisibility('specificProvider', 'specificProviderId', 200);" />
                                <label class="form-check-label" for="specificProvider"><fmt:message key="oscarMDS.search.formSpecificProvider"/></label>
                                <div id="specificProviderId" class="ms-3">
                                    <input type="hidden" name="query.searchProviderNo" id="findProvider" value="${query.searchProviderNo}"/>
                                    <div class="input-group input-group-sm">
                                        <input class="form-control pe-0 m-1" type="text" id="autocompleteProvider" name="query.searchProviderName" value="<e:forHtmlAttribute value='${query.searchProviderName}' />" placeholder="Provider"/>
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
                            <input type="hidden" name="query.unmatched" id="unmatchedId" value="${query.unmatched}"/>
                            <div class="form-check">
                                <input class="btn-check-input" type="radio" name="patientsRadios" value="patientsOption1" id="allPatients" ${query.unmatched eq 'false' and query.patientFirstName eq '' and query.patientLastName eq '' and query.patientHealthNumber eq '' ? 'checked' : ''} onClick="changeValueElementByName('query.unmatched', 'false');toggleInputVisibility('specificPatients', 'specificPatientsId', 200);"/>
                                <label class="form-check-label" for="allPatients"><fmt:message key="oscarMDS.search.formAllPatients"/></label>
                            </div>
                            <!-- Unmatched to Existing Patient -->
                            <div class="form-check">
                                <input class="btn-check-input" type="radio" name="patientsRadios" value="patientsOption2" id="unmatchedPatients" ${query.unmatched eq 'true' ? 'checked' : ''} onClick="changeValueElementByName('query.unmatched', 'true');toggleInputVisibility('specificPatients', 'specificPatientsId', 200);" />
                                <label class="form-check-label" for="unmatchedPatients"><fmt:message key="oscarMDS.search.formExistingPatient"/></label>
                            </div>
                            <!-- Specific Patient(s) -->
                            <div class="form-check">
                                <input class="btn-check-input" type="radio" name="patientsRadios" value="patientsOption3" id="specificPatients" ${query.unmatched eq 'false' and (query.patientFirstName ne '' or query.patientLastName ne '' or query.patientHealthNumber ne '') ? 'checked' : ''} onClick="changeValueElementByName('query.unmatched', 'false');toggleInputVisibility('specificPatients', 'specificPatientsId', 200);"/>
                                <label class="form-check-label" for="specificPatients"><fmt:message key="oscarMDS.search.formSpecificPatients"/></label> <br>
                                <div id="specificPatientsId" class="d-grid ms-3">
                                    <div class="input-group input-group-sm">
                                        <input class="form-control pe-0 m-1" type="text" name="query.patientFirstName" id="inputFirstName" value="<e:forHtmlAttribute value='${query.patientFirstName}'/>" placeholder="<fmt:message key='admin.provider.formFirstName'/>"/>
                                    </div>
                                    <div class="input-group input-group-sm">
                                        <input class="form-control pe-0 mb-1 mx-1" type="text" name="query.patientLastName" id="inputLastName" value="<e:forHtmlAttribute value='${query.patientLastName}'/>" placeholder="<fmt:message key='admin.provider.formLastName'/>"/>
                                    </div>
                                    <div class="input-group input-group-sm">
                                        <input class="form-control pe-0 mb-1 mx-1" type="text" name="query.patientHealthNumber" id="inputHIN" value="<e:forHtmlAttribute value='${query.patientHealthNumber}'/>" placeholder="<fmt:message key='oscarMDS.index.msgHealthNumber'/>"/>
                                    </div>
                                    <div class="text-danger d-none ms-1" id="specificPatientErrorMessage">Please fill at least one field for the specific patient.</div>
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
                                    <label class="my-auto pe" for="startDate">Start</label>
                                    <div class="input-group input-group-sm d-inline-flex">
                                        <input class="form-control pe-0 inbox-form-datepicker-input" type="text" placeholder="yyyy-mm-dd" id="startDate" name="query.startDate" value="${query.startDate}"/>
                                        <span class="input-group-text" for="startDate" id="startDateIcon"><i class="icon-calendar"></i></span>
                                    </div>
                                    <i class="icon-remove-sign clear-btn" aria-hidden="true" id="clearStartDate"></i>
                                </div>
                                <div class="inbox-form-datepicker-wrapper d-flex">
                                    <label class="my-auto" for="endDate">End</label>
                                    <div class="input-group input-group-sm d-inline-flex">
                                        <input class="form-control pe-0 inbox-form-datepicker-input" type="text" placeholder="yyyy-mm-dd" id="endDate" name="query.endDate" value="${query.endDate}"/>
                                        <span class="input-group-text" for="endDate" id="endDateIcon"><i class="icon-calendar"></i></span>
                                    </div>
                                    <i class="icon-remove-sign clear-btn" aria-hidden="true" id="clearEndDate"></i>
                                </div>
                            </div>
                        </div>

                        <div class="mb-1">
                            <!--Type-->
                            <label class="fw-bold text-uppercase">
                                <fmt:message key="inbox.inboxmanager.msgType"/>
                            </label>
                            <div class="form-check">
                                <input type="checkbox" class="btn-check-input" name="query.doc" value="true" ${query.doc || (!query.doc && !query.lab && !query.hrm) ? 'checked' : ''} id="btnDoc" autocomplete="off">
                                <label class="form-check-label" for="btnDoc"><fmt:message key="inbox.inboxmanager.msgTypeDocs"/></label><br>
                            </div>
                            <div class="form-check">
                                <input type="checkbox" class="btn-check-input" name="query.lab" value="true" ${query.lab || (!query.doc && !query.lab && !query.hrm) ? 'checked' : ''} id="btnLab" autocomplete="off">
                                <label class="form-check-label" for="btnLab"><fmt:message key="inbox.inboxmanager.msgTypeLabs"/></label><br>
                            </div>

                            <c:if test="${!OscarProperties.getInstance().isBritishColumbiaBillingRegion()}">
                                <div class="form-check">
                                    <input type="checkbox" class="btn-check-input" name="query.hrm" value="true" ${query.hrm || (!query.doc && !query.lab && !query.hrm) ? 'checked' : ''} id="btnHRM" autocomplete="off">
                                    <label class="form-checkbox-label" for="btnHRM"><fmt:message key="inbox.inboxmanager.msgTypeHRM"/></label><br>
                                </div>
                            </c:if>
                        </div>

                        <div class="mb-1">
                            <!--Review Status-->
                            <label class="fw-bold text-uppercase">
                                <fmt:message key="inbox.inboxmanager.msgReviewStatus"/>
                            </label>
                            <input type="hidden" name="query.status" id="statusId" value="${query.status}"/>
                            <div class="form-check">
                                <input type="radio" class="btn-check-input" name="statusReview" id="statusAll" id="All" value="All"
                                    ${empty query.status ? 'checked' : ''} onclick="changeValueElementByName('query.status', '')">
                                <label class="form-check-label" for="statusAll"><fmt:message key="inbox.inboxmanager.msgAll"/>
                            </div>
                            <div class="form-check">
                                <input type="radio" class="btn-check-input" name="statusReview" id="statusNew" value="N"
                                    ${query.status eq 'N' ? 'checked' : ''} onclick="changeValueElementByName('query.status', 'N')">
                                <label class="form-check-label" for="statusNew"><fmt:message key="inbox.inboxmanager.msgNew"/></label>
                            </div>
                            <div class="form-check">
                                <input type="radio" class="btn-check-input" name="statusReview" id="statusAcknowledged" value="A"
                                    ${query.status eq 'A' ? 'checked' : ''} onclick="changeValueElementByName('query.status', 'A')">
                                <label class="form-check-label" for="statusAcknowledged"><fmt:message key="inbox.inboxmanager.msgAcknowledged"/></label>
                            </div>
                            <div class="form-check">
                                <input type="radio" class="btn-check-input" name="statusReview" id="statusFiled" value="F"
                                    ${query.status eq 'F' ? 'checked' : ''} onclick="changeValueElementByName('query.status', 'F')">
                                <label class="form-check-label" for="statusFiled"><fmt:message key="inbox.inboxmanager.msgFiled"/></label>
                            </div>
                        </div>

                        <div class="mb-2">
                            <!--Abnormal-->
                            <label class="fw-bold text-uppercase">
                                <fmt:message key="inbox.inboxmanager.msgResultStatus"/>
                            </label>
                            <input type="hidden" name="query.abnormal" id="abnormalId" value="${query.abnormal}"/>
                            <div class="form-check">
                                <input type="radio" class="btn-check-input" name="abnormalResult" id="All" value="All"
                                    ${query.abnormal eq 'all' ? 'checked' : ''} onclick="changeValueElementByName('query.abnormal', 'all')">
                                <label class="form-check-label" for="All"><fmt:message key="inbox.inboxmanager.msgAll"/></label>
                            </div>
                            <div class="form-check">
                                <input type="radio" class="btn-check-input" name="abnormalResult" id="Abnormal" value="Abnormal"
                                    ${query.abnormal eq 'abnormalOnly' ? 'checked' : ''} onclick="changeValueElementByName('query.abnormal', 'abnormalOnly')">
                                <label class="form-check-label" for="Abnormal"><fmt:message key="global.abnormal"/></label>
                            </div>
                            <div class="form-check">
                                <input type="radio" class="btn-check-input" name="abnormalResult" id="Normal" value="Normal"
                                    ${query.abnormal eq 'normalOnly' ? 'checked' : ''} onclick="changeValueElementByName('query.abnormal', 'normalOnly')">
                                <label class="form-check-label" for="Normal"><fmt:message key="inbox.inboxmanager.msgNormal"/></label>
                            </div>
                        </div>

                        <!--Search Button-->
                        <div class="d-grid">
                            <button id="inboxhubFormSearchBtn" class="btn btn-primary btn-sm" type="submit" value='<fmt:message key="oscarMDS.search.btnSearch"/>'>
                                <span id="inboxhubFormSearchSpinner" class="spinner-border spinner-border-sm" role="status" aria-hidden="true" style="display: none;"></span>
                                <span id="inboxhubFormSearchText"><fmt:message key="oscarMDS.search.btnSearch"/></span>
                            </button>
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
                <a class="text-decoration-none accordion-button ${ param.providerNo eq 0 ? '' : 'collapsed' }" type="button" data-bs-toggle="collapse" data-bs-target="#collapseUnmatched" aria-expanded="false" aria-controls="collapseUnmatched">
                    Unmatched
                </a>
            </h2>
            <div id="collapseUnmatched" class="accordion-collapse collapse ${ param.providerNo eq 0 ? 'show' : '' }" aria-labelledby="headingUnmatchedList" data-bs-parent="#inbox-hub-unmatched-list">
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
                                All (<span id="patientNumDocs0"><c:out value="${totalUnmatchedCount}" /></span>)
                            </a>
                        </div>
                        <div id="collapseUnmatchedAll" class="accordion-collapse collapse show" aria-labelledby="headingUnmatchedAll">
                            <div class="accordion-body collapse-sub-category-list">
                                <ul class="list-unstyled" id="labdoc0showSublist">
                                    <c:if test="${ not empty categoryData.unmatchedDocs and (query.doc or allTypes) }" >
                                    <li>
                                        <a id="patient0docs" href="javascript:void(0);" class="btn category-btn text-decoration-none" onclick="filterView(0, 'doc', this);" title="Documents">
                                            Documents (<span id="pDocNum_0"><c:out value="${categoryData.unmatchedDocs}" /></span>)
                                        </a>
                                    </li>
                                    </c:if>
                                    <c:if test="${ not empty categoryData.unmatchedLabs and (query.lab or allTypes) }" >
                                    <li>
                                        <a id="patient0hl7s" href="javascript:void(0);" class="btn category-btn text-decoration-none" onclick="filterView(0, 'lab', this);" title="HL7">
                                            HL7 (<span id="pLabNum_0"><c:out value="${categoryData.unmatchedLabs}" /></span>)
                                        </a>
                                    </li>
                                    </c:if>
                                    <c:if test="${ not empty categoryData.unmatchedHRMCount and !OscarProperties.getInstance().isBritishColumbiaBillingRegion() and showHRM}" >
                                    <li>
                                        <a id="patient0hrms" href="javascript:void(0);" class="btn category-btn text-decoration-none" onclick="filterView(0, 'hrm', this);" title="HRM">
                                            HRM (<span id="pHRMNum_0"><c:out value="${categoryData.unmatchedHRMCount}" /></span>)
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
                    Matched
                </button>
            </h2>
            <div id="collapseMatched" class="accordion-collapse collapse show" aria-labelledby="headingMatchedList" data-bs-parent="#inbox-hub-matched-list">
                <div class="accordion-body my-2 ms-3">
                    <c:forEach items="${requestScope.categoryData.patientList}" var="patient">
                    <c:set var="patientId" value="${patient.id}" />
                    <c:set var="patientName" value="${ patient.lastName }, ${patient.firstName}" />
                    <%-- Subtracting hrm count from document count because document count include both documents and HRMs --%>
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
                            <a id="patient${patientId}all" href="javascript:void(0);" class="text-decoration-none text-wrap text-start collapse-heading btn category-btn py-1 px-0 ms-3" onclick="filterView(${patientId}, 'all', this);" title="<e:forHtmlAttribute value='${patientName}' />">
                                <e:forHtmlContent value='${patientName}' /> (<span id="patientNumDocs${patientId}">${numDocs}</span>)
                            </a>
                        </div>
                        <div id="collapsePatient${patientId}MatchedAll" class="accordion-collapse collapse" aria-labelledby="headingPatient${patientId}MatchedAll">
                            <div class="accordion-body collapse-sub-category-list">
                                <ul class="list-unstyled" id="labdoc${patientId}showSublist">
                                    <c:if test="${not empty docCount and (query.doc or allTypes)}">
                                    <li>
                                        <a id="patient${patientId}docs" href="javascript:void(0);" class="btn category-btn text-decoration-none" onclick="filterView(${patientId}, 'doc', this);" title="Documents">
                                            Documents (<span id="pDocNum_${patientId}">${docCount}</span>)
                                        </a>
                                    </li>
                                    </c:if>
                                    <c:if test="${not empty labCount and (query.lab or allTypes)}">
                                    <li>
                                        <a id="patient${patientId}hl7s" href="javascript:void(0);" class="btn category-btn text-decoration-none" onclick="filterView(${patientId}, 'lab', this);" title="HL7">
                                            HL7 (<span id="pLabNum_${patientId}">${labCount}</span>)
                                        </a>
                                    </li>
                                    </c:if>
                                    <c:if test="${not empty hrmCount and !OscarProperties.getInstance().isBritishColumbiaBillingRegion() and showHRM}">
                                    <li>
                                        <a id="patient${patientId}hrms" href="javascript:void(0);" class="btn category-btn text-decoration-none" onclick="filterView(${patientId}, 'hrm', this);" title="HRM">
                                            HRM (<span id="pLabNum_${patientId}">${hrmCount}</span>)
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
                An error occurred. Please try again later.
            </div>
            <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Close"></button>
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
    var searchProviderNo = "<e:forJavaScript value='${sessionScope.user}' />";

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
        
        dateInput.datetimepicker({
            format: 'YYYY-MM-DD',
            useCurrent: false,
        }).on('dp.change', function(e) {
            clearBtn.toggle(!!dateInput.val());
        }).on('dp.hide', function(e) {
        });

        // Disable manual typing
        dateInput.on('keydown paste', function(e) {
            e.preventDefault();
        });

        // Attach event listeners for input change and clear button
        dateInput.on('input', clearBtn.toggle(!!dateInput.val()));
        clearBtn.on('click', function() {
            dateInput.val('');
            clearBtn.toggle(!!dateInput.val());
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

    function fetchInboxhubDataByMode(btnViewMode2) {
        jQuery('#btnViewMode').val(btnViewMode2.checked ? 'true' : 'false');
    	jQuery("#btnViewModeLabel").html(btnViewMode2.checked ? 'List Mode' : 'Preview Mode');
        fetchInboxhubData();
    }

    function fetchInboxhubData() {
        const viewModeBtn = document.getElementById("btnViewMode");
        viewModeBtn.disabled = true;
        resetDataPageCount();
        if (viewModeBtn.value === 'true') {
            fetchInboxhubViewData();
        } else {
            fetchInboxhubListData();
        }
    }

    function fetchInboxhubListData() {
        if (!hasMoreData || isFetchingData) { return; }
        isFetchingData = true; 
        const url = "<e:forJavaScript value='${pageContext.request.contextPath}' />/web/inboxhub/Inboxhub.do?method=displayInboxList";
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
        const url = "<e:forJavaScript value='${pageContext.request.contextPath}' />/web/inboxhub/Inboxhub.do?method=displayInboxView";
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
            startInboxhubListProgress();
            updateInboxhubListProgress();
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
    }

    function autoCompleteProvider() {
        jQuery("#autocompleteProvider").autocomplete({
            source: contextPath + "/provider/SearchProvider.do?method=labSearch",
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
        page = 1;
        hasMoreData = true;
        isFetchingData = false;
    }

    // Show the toast and wrapper div
    function toastErrorMessage() {
        jQuery('#ajaxErrorToast').parent().css('display', 'block'); // Show the wrapper
        jQuery('#ajaxErrorToast').toast('show'); // Show the toast
    }

    // Hide the toast and wrapper div completely when the toast is dismissed
    jQuery('#ajaxErrorToast').on('hidden.bs.toast', function () {
        jQuery(this).parent().css('display', 'none'); // Hide the wrapper to prevent background blocking
    });

    function showInboxhubStats() {
        jQuery('#totalDocsCountStat').text(jQuery('#totalDocsCount').val());
        jQuery('#totalLabssCountStat').text(jQuery('#totalLabsCount').val());
        jQuery('#totalHRMsCountStat').text(jQuery('#totalHRMCount').val());
    }

    function startInboxhubListProgress() {
        const totalResultsCount = jQuery("#totalResultsCount").val();
        inboxhubListProgressWidth = 0;
        jQuery('#loadInboxListProgressBar').attr('aria-valuemax', totalResultsCount);
        jQuery('#loadInboxListProgressBar').css('width', inboxhubListProgressWidth + '%').attr('aria-valuenow', inboxhubListProgressWidth);
        jQuery('#inboxListProgressCount').text(inboxhubListProgressWidth + '% Complete');
        
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
            jQuery('#inboxListProgressCount').text(formattedPercentage + '% Complete');
            return;
        }

        if (!hasMoreData) {
            jQuery('#loadInboxListProgressBar').css('width', 100 + '%').attr('aria-valuenow', totalResultsCount);
            jQuery('#inboxListProgressCount').text(100 + '% Complete');
            stopInboxhubListProgress(1000);
        }
    }
</script>