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
<%@ page import="io.github.carlos_emr.carlos.lab.ca.on.LabResultData" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="io.github.carlos_emr.carlos.lab.ca.all.Hl7textResultsData" %>
<%@ page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    String attachmentSecurityObjectRequest = (String) request.getAttribute("attachmentSecurityObject");
    String attachmentSecurityObject = "_eform".equals(attachmentSecurityObjectRequest) ? "_eform" : "_con";
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="<%=attachmentSecurityObject%>" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=" + attachmentSecurityObject);%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<fmt:message var="previewAction" key="encounter.oscarConsultationRequest.AttachDocPopup.previewAction"/>
<%-- Completeness-report category labels, shared with EFormRenderMissingContent.jsp and
     fax/EFormMissingContent.jsp so the nine categories cannot drift between surfaces. Resolved into
     vars here because they are consumed inside a JavaScript string literal further down. --%>
<fmt:message var="lblFailedContentResources" key="eform.renderIssue.failedContentResources"/>
<fmt:message var="lblExcludedContentElements" key="eform.renderIssue.excludedContentElements"/>
<fmt:message var="lblSignatureMissing" key="eform.renderIssue.signatureMissing"/>
<fmt:message var="lblProviderStampMissing" key="eform.renderIssue.providerStampMissing"/>
<fmt:message var="lblTimerCompatibilityFailure" key="eform.renderIssue.timerCompatibilityFailure"/>
<fmt:message var="lblSevereConsoleErrors" key="eform.renderIssue.severeConsoleErrors"/>
<fmt:message var="lblContainedInteractions" key="eform.renderIssue.containedInteractions"/>
<fmt:message var="lblStabilizationCapped" key="eform.renderIssue.stabilizationCapped"/>
<fmt:message var="lblLabDecisionSupportStubbed" key="eform.renderIssue.labDecisionSupportStubbed"/>

<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="jakarta.tags.functions" prefix="fn" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<c:set var="attachmentSelectionDisabled" value="${canManageAttachments ne true}"/>

<!DOCTYPE html >
<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <title><fmt:message key="encounter.oscarConsultationRequest.AttachDocPopup.title"/></title>

    <style>

        .attachmentContainer * {
            font-family: "Helvetica Neue", Helvetica, Arial, sans-serif !important;
            font-size: 12px !important;
            color: black;
        }

        .attachmentContainer table {
            border-collapse: collapse;
            border: none;
        }

        .attachmentContainer table, .attachmentContainer table tr td {
            background-color: white !important;
        }

        .attachmentContainer {
            display: flex;
            width: 95vw;
            background-color: white !important;
        }

        @media (min-width: 2400px) {
            .attachmentContainer {
                width: 97vw;
            }
        }

        .attachmentList {
            overflow-y: scroll;
            min-width: 320px;
            min-height: 580px;
            width: 100%;
            flex-basis: 40%;
        }

        @media (min-height: 1600px) {
            .attachmentList {
                height: 97vh;
            }
        }

        #attachDocumentsPanel {
            width: 100%;
            font-size: x-small;
        }

        .preview-button {
            padding: 2px 4px;
            color: black;
            border: none;
            border-radius: 2px;
            cursor: pointer;
        }

        #attachDocumentsForm li.selectAllHeading {
            justify-content: space-between;
            align-items: center;
            border-bottom: black thin inset
        }

        .show-all-button {
            padding: 2px 4px;
            color: black;
            border: none;
            border-radius: 2px;
            cursor: pointer;
            margin-left: auto;
            background-color: lightblue;
        }

        #pdfPreview {
            width: 100%;
            display: flex;
            justify-content: center;
            align-items: center;
            flex-basis: 60%;
        }

        #pdfObject {
            width: 100%;
            height: 100%;
        }

        .preview-filler {
            border: 2px solid black;
            border-radius: 7px;
            padding: 10px;
        }

        .preview-pane {
            background-color: lightgray;
        }

        /* Sits above the preview iframe, which fills the pane; the banner must not overlay the
           document it is warning about. */
        .preview-advisory {
            background-color: #fff3cd;
            border: 1px solid #ffc107;
            border-radius: 4px;
            color: #664d03;
            font-weight: bold;
            margin-bottom: 6px;
            padding: 8px 10px;
        }

        .flex {
            display: flex !important;
        }

        /* Bootstrap 5: using .d-none utility for display:none */

        .attachmentContainer ul li:nth-of-type(odd) {
            background-color: white;
        }

        .attachmentContainer ul li:nth-of-type(even) {
            background-color: whitesmoke;
        }

        .attachmentContainer .collapse-arrow {
            cursor: pointer;
        }

        .attachmentContainer .collapsible-content {
            display: none;
            padding: 0 0 0 20px !important;
        }

        .attachmentContainer .collapse-arrow {
            display: inline-block;
            width: 6px;
            height: 6px;
            border: solid black;
            border-width: 0 3px 3px 0;
            transform: rotate(-45deg);
            border-radius: 0px;
        }

        .attachmentContainer .caret-down {
            transform: rotate(45deg) !important;
        }
    </style>


</head>
<body>
<form id="attachDocumentsForm">
    <%-- Script placed in body so functions are available when loaded via jQuery .load() into a parent page --%>
    <script type="text/javascript">
        function toggleLabVersionList(collapseBtn) {
            jQuery(collapseBtn).toggleClass('caret-down');
            jQuery(collapseBtn).parent().find('.collapsible-content').slideToggle(100);
        }

        function expandLabVersionList(collapseBtn) {
            jQuery(collapseBtn).addClass('caret-down');
            jQuery(collapseBtn).parent().find('.collapsible-content').slideDown(100);
        }

        if (typeof pdfCache == 'undefined') {
            var pdfCache = []; //because this is a global variable, only redeclare it if it doesn't exist. This is relevant when opening the attachment window multiple times in sequence
        }

        function toggleSelectAll(element, startClassName) {
            jQuery("[class^='" + startClassName + "']:not(input[disabled='disabled'])").prop('checked', jQuery(element).prop("checked"));
        }

        // advisoryIssues is cached with the PDF: a re-preview is served from here without another
        // request, so storing only the bytes would silently drop the "this form reported a script
        // error" notice the second time the same document is opened.
        function addPdfAttachment(attachmentName, attachmentId, base64Data, advisoryIssues) {
            const newAttachment = {
                attachmentName,
                attachmentId,
                base64Data,
                advisoryIssues
            };
            pdfCache.push(newAttachment);
        }

        function getPdfAttachment(attachmentName, attachmentId) {
            const foundAttachment = pdfCache.find(
                attachment =>
                    attachment.attachmentName === attachmentName &&
                    attachment.attachmentId === attachmentId
            );

            return foundAttachment ? foundAttachment : null;
        }

        // var + typeof guard (not a top-level `let`): this script is re-evaluated in the parent
        // page's global scope every time the attach dialog is reopened via jQuery .load(), and a
        // re-declared top-level `let`/`const` throws a SyntaxError that discards the whole block.
        // Same idiom the pdfCache global above uses; preserving the value across reopens also lets a
        // prior blob URL still be revoked.
        if (typeof previewBlobUrl === 'undefined') {
            var previewBlobUrl = null;
        }

        function showPDF(base64Data) {
            if (!base64Data) {
                showError();
                return;
            }

            const previewFiller = document.getElementById('preview-filler');
            previewFiller.classList.add('d-none');
            // Render via a blob: URL in the iframe. A data:application/pdf <object> was silently
            // blocked by the eForm pages' CSP (object-src 'none'); iframes fall under frame-src,
            // which those pages permit for blob:. The blob also avoids giant data: URLs. Revoke the
            // previous preview's URL so repeated previews do not leak object URLs.
            // atob() throws InvalidCharacterError on corrupt/truncated base64; without this guard the
            // throw escapes after the spinner is already in its locked state, leaving an
            // undismissable full-screen overlay with no message. Route the failure to showError,
            // which restores the spinner and tells the user.
            let previewUrl;
            try {
                const bytes = Uint8Array.from(atob(base64Data), (c) => c.charCodeAt(0));
                const blob = new Blob([bytes], { type: 'application/pdf' });
                previewUrl = URL.createObjectURL(blob);
            } catch (e) {
                previewFiller.classList.remove('d-none');
                showError("The preview data could not be decoded.");
                return;
            }
            if (previewBlobUrl) {
                URL.revokeObjectURL(previewBlobUrl);
            }
            previewBlobUrl = previewUrl;
            const pdfFrame = document.getElementById('pdfObject');
            pdfFrame.classList.remove('d-none');
            pdfFrame.src = previewBlobUrl;
            HideSpin();
        }

        // Advisory conditions never withhold the document, so this is a banner rather than the
        // confirm() dialog the blocking path uses. Cleared on every preview so a clean render does
        // not inherit the previous document's notice.
        function showAdvisory(advisoryIssues) {
            const advisory = document.getElementById('preview-advisory');
            if (!advisory) {
                return;
            }
            const count = Number(advisoryIssues) || 0;
            if (count < 1) {
                advisory.classList.add('d-none');
                advisory.textContent = '';
                return;
            }
            // textContent, not innerHTML: the count is server-generated, but this element sits in a
            // page that renders clinical documents and must never become an HTML sink.
            advisory.textContent = "This form reported " + count
                + (count === 1 ? " script error" : " script errors")
                + " while rendering. The document below may be missing content — check it against the form.";
            advisory.classList.remove('d-none');
        }

        function showError(errorMessage) {
            if (errorMessage) {
                alert("A preview of this document could not be generated.\n\n" + errorMessage);
            } else {
                alert("A preview of this document could not be generated.");
            }
            HideSpin();
        }

        function getPdf(attachmentName, attachmentId, parameters) {
            // Please include "<%=request.getContextPath()%>/WEB-INF/jsp/includes/spinner.jspf" into the parent page to control the visibility of the spinner (show/hide).
            ShowSpin(true);
            const cached = getPdfAttachment(attachmentName, attachmentId);
            if (cached !== null) {
                showPDF(cached.base64Data);
                showAdvisory(cached.advisoryIssues);
                return;
            }

            jQuery.ajax({
                type: 'POST',
                url: "${ pageContext.request.contextPath }/previewDocs",
                data: parameters,
                dataType: "json",
                success: function (data) {
                    if (data.base64Data) {
                        addPdfAttachment(attachmentName, attachmentId, data.base64Data, data.advisoryIssues);
                        showPDF(data.base64Data);
                        showAdvisory(data.advisoryIssues);
                    } else if (data.missingContent) {
                        HideSpin();
                        // Every category EFormRenderCompletenessReport carries must appear here.
                        // The approval digest binds to the COMPLETE issue set, so omitting a
                        // category asks the clinician to approve issues they were never shown.
                        //
                        // Labels come from the SAME eform.renderIssue.* keys the two JSP surfaces use.
                        // They were previously duplicated as hardcoded English in three files and could
                        // drift independently; one key per category means all three move together, and
                        // this dialog stops being English-only. forJavaScript, not forHtmlContent:
                        // these are interpolated into a JS string literal.
                        const details = "\n\n${carlos:forJavaScript(lblFailedContentResources)}: " + data.failedContentResources
                            + "\n${carlos:forJavaScript(lblExcludedContentElements)}: " + data.excludedContentElements
                            + "\n${carlos:forJavaScript(lblSignatureMissing)}: " + data.signatureMissing
                            + "\n${carlos:forJavaScript(lblProviderStampMissing)}: " + data.providerStampMissing
                            + "\n${carlos:forJavaScript(lblTimerCompatibilityFailure)}: " + data.timerCompatibilityFailure
                            + "\n${carlos:forJavaScript(lblSevereConsoleErrors)}: " + data.severeConsoleErrors
                            + "\n${carlos:forJavaScript(lblContainedInteractions)}: " + data.containedInteractions
                            + "\n${carlos:forJavaScript(lblStabilizationCapped)}: " + data.stabilizationCapped
                            + "\n${carlos:forJavaScript(lblLabDecisionSupportStubbed)}: " + data.labDecisionSupportStubbed;
                        if (data.renderApproval
                                && confirm(data.errorMessage + details + "\n\nApprove these issues and render?")) {
                            getPdf(attachmentName, attachmentId, parameters
                                + "&renderApproval=" + encodeURIComponent(data.renderApproval));
                        }
                    } else {
                        showError(data.errorMessage);
                    }
                },
                error: function (xhr, status, error) {
                    // A non-JSON response (typically a login redirect after session expiry) lands here.
                    // Give the actionable hint instead of the context-free generic message.
                    if (xhr.responseJSON && xhr.responseJSON.errorMessage) {
                        showError(xhr.responseJSON.errorMessage);
                    } else if (xhr.status === 0 || xhr.status === 401 || xhr.status === 403 || status === "parsererror") {
                        showError("Your session may have expired. Reload the page and sign in again.");
                    } else {
                        showError("");
                    }
                }
            });
        }

        function showAll(showButton, attachmentType) {
            let hiddenAttachments = document.getElementsByClassName(attachmentType);
            Array.from(hiddenAttachments).forEach(function (attachment) {
                attachment.classList.remove('d-none');
            });
            showButton.classList.add('d-none');
            showButton.parentNode.classList.remove('flex');
        }
    </script>
    <div class="attachmentContainer">
        <div class="attachmentList">
            <table id="attachDocumentsPanel">
                <c:if test="${not empty allEForms }">
                    <tr>
                        <td><h2><fmt:message key="encounter.oscarConsultationRequest.AttachDocPopup.eFormsExcludingAttachments"/></h2></td>
                    </tr>
                    <tr>
                        <td>
                            <ul id="eFormList" style="list-style-type: none;padding:0;">
                                <li class="selectAllHeading ${allEForms.size() > 5 ? 'flex' : ''}">
                                    <input id="selectAllEForms" type="checkbox"
                                           onclick="toggleSelectAll(this, 'eForm_');" value="eForm_check"
                                           title="Select/un-select all eForms."
                                           <c:if test="${attachmentSelectionDisabled}">disabled="disabled"</c:if>/>
                                     <label for="selectAllEForms"><fmt:message key="encounter.oscarConsultationRequest.AttachDocPopup.selectAll"/></label>
                                     <button class="show-all-button ${allEForms.size() > 5 ? '' : 'd-none'}" type="button"
                                             onclick="showAll(this, 'eForm')"><fmt:message key="encounter.oscarConsultationRequest.AttachDocPopup.showMoreEForms"><fmt:param value="${allEForms.size() - 5}"/></fmt:message>
                                     </button>
                                 </li>
                                 <c:forEach items="${ allEForms }" var="eForm" varStatus="loop">
                                     <c:set var="eFormPreviewParameters">method=renderEFormPDF&eFormId=${carlos:forUriComponent(eForm.id)}&demographicNo=${carlos:forUriComponent(demographicNo)}</c:set>
                                     <c:set var="eFormPreviewOnclick">getPdf('EFORM', '${carlos:forJavaScript(eForm.id)}', '${carlos:forJavaScript(eFormPreviewParameters)}')</c:set>
                                     <li class="eForm ${loop.index > 4 ? 'd-none' : ''}">
                                         <c:set var="eFormDisplayName" value="${empty eForm.subject ? eForm.formName : eForm.subject}"/>
                                         <input class="eForm_check" type="checkbox" name="eFormNo"
                                                id="eFormNo${ eForm.id }" value="${eForm.id}" title="${carlos:forHtmlAttribute(eForm.formName)}"
                                                <c:if test="${attachmentSelectionDisabled}">disabled="disabled"</c:if>/>
                                         <label for="eFormNo${eForm.id}">
                                             ${carlos:forHtml(eFormDisplayName)} ${carlos:forHtml(eForm.getFormDate())}
                                         </label>
                                         <button class="preview-button" type="button" title="${carlos:forHtmlAttribute(previewAction)}"
                                                 onclick="${carlos:forHtmlAttribute(eFormPreviewOnclick)}">
                                             ${carlos:forHtmlContent(previewAction)}
                                         </button>
                                     </li>
                                 </c:forEach>
                            </ul>
                        </td>
                    </tr>
                </c:if>

                <c:if test="${not empty allDocuments }">
                    <tr>
                        <td><h2><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.sectionDocuments"/></h2></td>
                    </tr>
                    <tr>
                        <td>
                            <ul id="documentList" style="list-style-type: none;padding:0px;">
                                <li class="selectAllHeading ${allDocuments.size() > 20 ? 'flex' : ''}">
                                    <input id="selectAllDocuments" type="checkbox"
                                           onclick="toggleSelectAll(this, 'document_');" value="document_check"
                                           title="Select/un-select all documents."
                                           <c:if test="${attachmentSelectionDisabled}">disabled="disabled"</c:if>/>
                                    <label for="selectAllDocuments"><fmt:message key="encounter.oscarConsultationRequest.AttachDocPopup.selectAll"/></label>
                                    <button class="show-all-button ${allDocuments.size() > 20 ? '' : 'd-none'}"
                                            type="button"
                                            onclick="showAll(this, 'doc')"><fmt:message key="encounter.oscarConsultationRequest.AttachDocPopup.showMoreDocuments"><fmt:param value="${allDocuments.size() - 20}"/></fmt:message>
                                    </button>
                                </li>
                                <c:forEach items="${ allDocuments }" var="document" varStatus="loop">
                                    <c:set var="documentPreviewParameters">method=renderEDocPDF&eDocId=${carlos:forUriComponent(document.docId)}&demographicNo=${carlos:forUriComponent(demographicNo)}</c:set>
                                    <c:set var="documentPreviewOnclick">getPdf('DOC', '${carlos:forJavaScript(document.docId)}', '${carlos:forJavaScript(documentPreviewParameters)}')</c:set>
                                    <li class="doc ${loop.index > 19 ? 'd-none' : ''}">
                                        <input class="document_check" type="checkbox" name="docNo"
                                               id="docNo${document.docId}" value="${document.docId}"
                                               title="${ carlos:forHtmlAttribute(document.description) }"
                                               <c:if test="${attachmentSelectionDisabled}">disabled="disabled"</c:if>/>
                                        <label for="docNo${document.docId}">${carlos:forHtml(document.description)} ${carlos:forHtml(document.observationDate)}</label>
                                        <button class="preview-button" type="button" title="${carlos:forHtmlAttribute(previewAction)}"
                                                 onclick="${carlos:forHtmlAttribute(documentPreviewOnclick)}">
                                            ${carlos:forHtmlContent(previewAction)}
                                        </button>
                                    </li>
                                </c:forEach>
                            </ul>
                        </td>
                    </tr>
                </c:if>

                <c:if test="${not empty allLabsSortedByVersions }">
                    <tr>
                        <td><h2><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.sectionLabs"/></h2></td>
                    </tr>
                    <tr>
                        <td>
                            <ul id="labList" style="list-style-type: none;padding:0px;">
                                <li class="selectAllHeading ${allLabsSortedByVersions.size() > 20 ? 'flex' : ''}">
                                    <input id="selectAllLabs" type="checkbox" onclick="toggleSelectAll(this, 'lab_');"
                                           value="lab_check" title="Select/un-select all documents."
                                           <c:if test="${attachmentSelectionDisabled}">disabled="disabled"</c:if>/>
                                    <label for="selectAllLabs"><fmt:message key="encounter.oscarConsultationRequest.AttachDocPopup.selectAll"/></label>
                                    <button class="show-all-button ${allLabsSortedByVersions.size() > 20 ? '' : 'd-none'}"
                                            type="button"
                                            onclick="showAll(this, 'lab')"><fmt:message key="encounter.oscarConsultationRequest.AttachDocPopup.showMoreLabs"><fmt:param value="${allLabsSortedByVersions.size() - 20}"/></fmt:message>
                                    </button>
                                </li>
                                <c:forEach items="${ allLabsSortedByVersions }" var="lab" varStatus="loop">
                                    <c:set var="labName" value="${fn:substring(lab.labName, 0, 30)}"/>
                                    <c:set var="totalVersions" value="${fn:length(lab.labVersionIds)}"/>
                                    <c:set var="labPreviewParameters">method=renderLabPDF&segmentId=${carlos:forUriComponent(lab.segmentID)}&demographicNo=${carlos:forUriComponent(demographicNo)}</c:set>
                                    <c:set var="labPreviewOnclick">getPdf('LAB', '${carlos:forJavaScript(lab.segmentID)}', '${carlos:forJavaScript(labPreviewParameters)}')</c:set>
                                    <li class="lab ${loop.index > 19 ? 'd-none' : ''}">
                                        <input class="lab_check" type="checkbox" name="labNo"
                                               id="labNo${ lab.segmentID }" value="${lab.segmentID}"
                                               title="${carlos:forHtmlAttribute(labName)}"
                                               <c:if test="${attachmentSelectionDisabled}">disabled="disabled"</c:if>/>
                                        <label for="labNo${lab.segmentID}" title="${carlos:forHtmlAttribute(labName)}">${carlos:forHtml(labName)}&nbsp;</label>
                                        <label for="labNo${lab.segmentID}"
                                               class="lab-date">${lab.labDateFormated}</label>
                                        <c:if test="${not empty lab.labVersionIds}">
                                            &nbsp;<i class="collapse-arrow" onclick="toggleLabVersionList(this)"></i>&nbsp;
                                        </c:if>
                                        <button class="preview-button" type="button" title="${carlos:forHtmlAttribute(previewAction)}"
                                                 onclick="${carlos:forHtmlAttribute(labPreviewOnclick)}">
                                            ${carlos:forHtmlContent(previewAction)}
                                        </button>
                                        <ul class="collapsible-content" style="list-style-type: none;padding:0px;">
                                            <c:forEach items="${ lab.labVersionIds }" var="version"
                                                       varStatus="versionLoop">
                                                <c:set var="labVersionPreviewParameters">method=renderLabPDF&segmentId=${carlos:forUriComponent(version.key)}&demographicNo=${carlos:forUriComponent(demographicNo)}</c:set>
                                                <c:set var="labVersionPreviewOnclick">getPdf('LAB', '${carlos:forJavaScript(version.key)}', '${carlos:forJavaScript(labVersionPreviewParameters)}')</c:set>
                                                <li>
                                                    <input class="lab_check"
                                                           data-version="${totalVersions - versionLoop.index}"
                                                           type="checkbox" name="labNo" id="labNo${ version.key }"
                                                           value="${version.key}"
                                                           title="v${totalVersions - versionLoop.index} ${carlos:forHtmlAttribute(labName)}"
                                                           <c:if test="${attachmentSelectionDisabled}">disabled="disabled"</c:if>/>
                                                    <em>
                                                        <label for="labNo${version.key}"
                                                               title="v${totalVersions - versionLoop.index} ${carlos:forHtmlAttribute(labName)}">
                                                             <fmt:message key="encounter.oscarConsultationRequest.AttachDocPopup.earlierVersionOf">
                                                                 <fmt:param value="${totalVersions - versionLoop.index}"/>
                                                                 <fmt:param value="${totalVersions + 1}"/>
                                                             </fmt:message>&nbsp;
                                                         </label>
                                                        <label for="labNo${version.key}"
                                                               class="lab-date">(${version.value})</label>
                                                    </em>
                                                     <button class="preview-button" type="button" title="${carlos:forHtmlAttribute(previewAction)}"
                                                             onclick="${carlos:forHtmlAttribute(labVersionPreviewOnclick)}">
                                                         ${carlos:forHtmlContent(previewAction)}
                                                     </button>
                                                </li>
                                            </c:forEach>
                                        </ul>
                                    </li>
                                </c:forEach>
                            </ul>
                        </td>
                    </tr>
                </c:if>

                <c:if test="${not empty allHRMDocuments }">
                    <tr>
                        <td><h2><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.sectionHRM"/></h2></td>
                    </tr>
                    <tr>
                        <td>
                            <ul id="hrmList" style="list-style-type: none;padding:0;">
                                <li class="selectAllHeading ${allHRMDocuments.size() > 20 ? 'flex' : ''}">
                                    <input id="selectAllHRMS" type="checkbox" onclick="toggleSelectAll(this, 'hrm_');"
                                           value="hrm_check" title="Select/un-select all HRM documents."
                                           <c:if test="${attachmentSelectionDisabled}">disabled="disabled"</c:if>/>
                                    <label for="selectAllHRMS"><fmt:message key="encounter.oscarConsultationRequest.AttachDocPopup.selectAll"/></label>
                                    <button class="show-all-button ${allHRMDocuments.size() > 20 ? '' : 'd-none'}"
                                            type="button"
                                            onclick="showAll(this, 'hrm')"><fmt:message key="encounter.oscarConsultationRequest.AttachDocPopup.showMoreHRM"><fmt:param value="${allHRMDocuments.size() - 20}"/></fmt:message>
                                     </button>
                                 </li>
                                <c:forEach items="${ allHRMDocuments }" var="hrm" varStatus="loop">
                                    <c:set var="hrmPreviewParameters">method=renderHrmPDF&hrmId=${carlos:forUriComponent(hrm['id'])}&demographicNo=${carlos:forUriComponent(demographicNo)}</c:set>
                                    <c:set var="hrmPreviewOnclick">getPdf('HRM', '${carlos:forJavaScript(hrm['id'])}', '${carlos:forJavaScript(hrmPreviewParameters)}')</c:set>
                                    <li class="hrm ${loop.index > 19 ? 'd-none' : ''}">
                                        <input class="hrm_check" type="checkbox" name="hrmNo" id="hrmNo${ hrm['id'] }"
                                               value="${hrm['id']}" title="${carlos:forHtmlAttribute(hrm['name'])}"
                                               <c:if test="${attachmentSelectionDisabled}">disabled="disabled"</c:if>/>
                                        <label for="hrmNo${hrm['id']}">
                                            ${carlos:forHtml(hrm['name'])} ${carlos:forHtml(hrm['report_date'])}
                                        </label>
                                        <button class="preview-button" type="button" title="${carlos:forHtmlAttribute(previewAction)}"
                                                 onclick="${carlos:forHtmlAttribute(hrmPreviewOnclick)}">
                                            ${carlos:forHtmlContent(previewAction)}
                                        </button>
                                    </li>
                                </c:forEach>
                            </ul>
                        </td>
                    </tr>
                </c:if>

                <c:if test="${not empty allForms }">
                    <tr>
                        <td><h2><fmt:message key="encounter.oscarConsultationRequest.AttachDocPopup.formsCurrentOnly"/></h2></td>
                    </tr>
                    <tr>
                        <td>
                            <ul id="formList" style="list-style-type: none;padding:0;">
                                <li class="selectAllHeading ${allForms.size() > 20 ? 'flex' : ''}">
                                    <input id="selectAllForms" type="checkbox" onclick="toggleSelectAll(this, 'form_');"
                                           value="form_check" title="Select/un-select all forms."
                                           <c:if test="${attachmentSelectionDisabled}">disabled="disabled"</c:if>/>
                                    <label for="selectAllForms"><fmt:message key="encounter.oscarConsultationRequest.AttachDocPopup.selectAll"/></label>
                                    <button class="show-all-button ${allForms.size() > 20 ? '' : 'd-none'}" type="button"
                                            onclick="showAll(this, 'form')"><fmt:message key="encounter.oscarConsultationRequest.AttachDocPopup.showMoreForms"><fmt:param value="${allForms.size() - 20}"/></fmt:message>
                                    </button>
                                </li>
                                <c:forEach items="${ allForms }" var="form" varStatus="loop">
                                    <c:set var="formPreviewParameters">method=renderFormPDF&formId=${carlos:forUriComponent(form.formId)}&formName=${carlos:forUriComponent(form.formName)}&demographicNo=${carlos:forUriComponent(demographicNo)}</c:set>
                                    <c:set var="formPreviewOnclick">getPdf('FORM', '${carlos:forJavaScript(form.formId)}', '${carlos:forJavaScript(formPreviewParameters)}')</c:set>
                                    <li class="form ${loop.index > 19 ? 'd-none' : ''}">
                                         <input class="form_check" type="checkbox" name="formNo"
                                                id="formNo${ form.formId }" value="${form.formId}"
                                                title="${carlos:forHtmlAttribute(form.formName)}"
                                                <c:if test="${attachmentSelectionDisabled}">disabled="disabled"</c:if>/>
                                        <label for="formNo${form.formId}">
                                            ${carlos:forHtml(form.formName)} ${carlos:forHtml(form.getEdited())}
                                        </label>
                                        <button class="preview-button" type="button" title="${carlos:forHtmlAttribute(previewAction)}"
                                                onclick="${carlos:forHtmlAttribute(formPreviewOnclick)}">
                                            ${carlos:forHtmlContent(previewAction)}
                                        </button>
                                    </li>
                                </c:forEach>
                            </ul>
                        </td>
                    </tr>
                </c:if>
            </table>
        </div>

        <div id="pdfPreview" class="preview-pane">
            <%-- iframe, not <object>: the eForm pages harden with CSP object-src 'none', which
                 silently blocked <object>-based PDF previews. Iframes are governed by frame-src,
                 which those pages open to 'self' and blob: for exactly this preview. --%>
            <%-- Advisory banner: shown when a render completed but reported a non-blocking
                 condition, such as a suppressed browser interaction or failed legacy timer. The
                 PDF is still delivered — this must never gate the preview — but the reader has to
                 be told. Severe page-script errors are blocking and reach this preview only after
                 an exact approval. --%>
            <div id="preview-advisory" class="preview-advisory d-none" role="status"></div>
            <iframe id="pdfObject" class="d-none" title="Attachment preview"></iframe>
            <div id="preview-filler" class="preview-filler">
                <fmt:message key="encounter.oscarConsultationRequest.AttachDocPopup.clickAnyItemToPreview"/>
            </div>
        </div>
    </div>
</form>
</body>
</html>
