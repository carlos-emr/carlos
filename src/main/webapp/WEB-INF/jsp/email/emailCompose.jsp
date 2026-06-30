<!DOCTYPE html>

<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.functions" prefix="fn" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>

<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <fmt:message key="email.compose.title" var="emailComposeTitle"/>
    <fmt:message key="email.compose.label.sender" var="emailComposeSenderLabel"/>
    <fmt:message key="email.compose.label.patient" var="emailComposePatientLabel"/>
    <fmt:message key="email.compose.placeholder.searchPatient" var="emailComposeSearchPatientPlaceholder"/>
    <fmt:message key="email.compose.label.emailAddresses" var="emailComposeEmailAddressesLabel"/>
    <fmt:message key="email.compose.btn.removeEmail" var="emailComposeRemoveEmail"/>
    <fmt:message key="email.compose.msg.warning" var="emailComposeWarning"/>
    <fmt:message key="email.compose.msg.additionalEmailAddressData" var="emailComposeAdditionalEmailAddressData"/>
    <fmt:message key="email.compose.msg.noOutgoingEmailAccount" var="emailComposeNoOutgoingEmailAccount"/>
    <fmt:message key="email.compose.msg.noValidEmail" var="emailComposeNoValidEmail"/>
    <fmt:message key="email.compose.msg.updateDemographic" var="emailComposeUpdateDemographic"/>
    <fmt:message key="email.compose.msg.andTryAgain" var="emailComposeAndTryAgain"/>
    <fmt:message key="email.compose.msg.additionalSnippets" var="emailComposeAdditionalSnippets"/>
    <fmt:message key="email.compose.msg.warningAdditionalSnippets" var="emailComposeWarningAdditionalSnippets"/>
    <fmt:message key="email.compose.msg.correctEmailBeforeProceeding" var="emailComposeCorrectEmailBeforeProceeding"/>
    <fmt:message key="email.compose.heading.body" var="emailComposeBodyLabel"/>
    <fmt:message key="email.compose.placeholder.body" var="emailComposeBodyPlaceholder"/>
    <fmt:message key="email.compose.msg.unencryptedBody" var="emailComposeUnencryptedBody"/>
    <fmt:message key="email.compose.msg.unencryptedSubject" var="emailComposeUnencryptedSubject"/>
    <fmt:message key="email.compose.msg.encryptionDisabledWarning" var="emailComposeEncryptionDisabledWarning"/>
    <fmt:message key="email.compose.label.encryption" var="emailComposeEncryptionLabel"/>
    <fmt:message key="email.compose.tooltip.encryption" var="emailComposeEncryptionTooltip"/>
    <fmt:message key="email.compose.label.encryptedMessage" var="emailComposeEncryptedMessageLabel"/>
    <fmt:message key="email.compose.tooltip.encryptedMessage" var="emailComposeEncryptedMessageTooltip"/>
    <fmt:message key="email.compose.placeholder.encryptedMessage" var="emailComposeEncryptedMessagePlaceholder"/>
    <fmt:message key="email.compose.label.password" var="emailComposePasswordLabel"/>
    <fmt:message key="email.compose.placeholder.password" var="emailComposePasswordPlaceholder"/>
    <fmt:message key="email.compose.label.clue" var="emailComposeClueLabel"/>
    <fmt:message key="email.compose.tooltip.clue" var="emailComposeClueTooltip"/>
    <fmt:message key="email.compose.placeholder.clue" var="emailComposeCluePlaceholder"/>
    <fmt:message key="email.compose.label.encryptAttachments" var="emailComposeEncryptAttachmentsLabel"/>
    <fmt:message key="email.compose.tooltip.encryptAttachments" var="emailComposeEncryptAttachmentsTooltip"/>
    <fmt:message key="email.compose.section.additionalOptions" var="emailComposeAdditionalOptions"/>
    <fmt:message key="email.compose.label.chartOptions" var="emailComposeChartOptions"/>
    <fmt:message key="email.compose.btn.addAdditionalParameters" var="emailComposeAddAdditionalParameters"/>
    <fmt:message key="email.compose.btn.send" var="emailComposeSend"/>
    <fmt:message key="email.compose.btn.cancel" var="emailComposeCancel"/>
    <fmt:message key="email.compose.msg.windowClosing" var="emailComposeWindowClosing"/>
    <fmt:message key="email.compose.btn.close" var="emailComposeClose"/>
    <fmt:message key="email.compose.msg.subjectRequired" var="emailComposeSubjectRequired"/>
    <fmt:message key="email.compose.msg.bodyRequired" var="emailComposeBodyRequired"/>
    <fmt:message key="email.compose.msg.passwordRequired" var="emailComposePasswordRequired"/>
    <fmt:message key="email.compose.msg.clueRequired" var="emailComposeClueRequired"/>
    <fmt:message key="email.compose.msg.passwordMinLength" var="emailComposePasswordMinLength"/>
    <fmt:message key="email.compose.msg.minimumRecipient" var="emailComposeMinimumRecipient"/>
    <fmt:message key="email.compose.state.on" var="emailComposeStateOn"/>
    <fmt:message key="email.compose.state.off" var="emailComposeStateOff"/>

    <title>${emailComposeTitle}</title>

    <c:set var="ctx" value="${ pageContext.request.contextPath }" scope="page"/>
    <link rel="stylesheet" href="${ctx}/library/bootstrap/5.3.8/css/bootstrap.min.css" type="text/css"/>
    <link href="${ctx}/library/jquery/jquery-ui-1.14.2.min.css" rel="stylesheet" type="text/css"/>
    <link href="${ctx}/css/fontawesome-all.min.css" rel="stylesheet">

    <script type="text/javascript" src="${ctx}/library/jquery/jquery-3.7.1.min.js"></script>
    <script src="${ctx}/library/jquery/jquery-compat.js"></script>
    <script type="text/javascript" src="${ctx}/library/jquery/jquery.validate-1.21.0.min.js"></script>
    <script type="text/javascript" src="${ctx}/library/jquery/jquery-ui-1.14.2.min.js"></script>
    <script type="text/javascript" src="${ctx}/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>

    <%--
        Action return flashy confirmation messages.
    --%>
    <c:if test="${ not empty isEmailSuccessful }">
        <script type="text/javascript">
            $(document).ready(function () {
                $("#page-body").slideUp("slow");
            })
        </script>
    </c:if>

    <style type="text/css">

        * {
            font-family: Arial, Helvetica, sans-serif;
            font-size: small;
        }

        body {
            max-width: 1600px;
            margin: auto;
        }

        #additionalRecipientControlPanel, #form-control-buttons {
            margin-bottom: 15px;
        }

        #form-control-buttons button {
            margin-left: 15px;
        }

        ul.ui-widget {
            margin: 10px;
            max-width: 100%;
            height: auto;
            max-height: 400px;
            overflow-y: scroll;
        }

        .recipientGroup {
            margin-bottom: 3px;
        }

        #oscarEmailHeader {
            width: 100%;
            border-collapse: collapse;
            margin-top: .5%;
            margin-bottom: 15px;
        }

        table#oscarEmailHeader tr td {
            padding: 1px 5px;
            background-color: #F3F3F3;
        }

        #oscarEmailHeader #oscarEmailHeaderLeftColumn {
            background-color: white;
            padding: 0px;
            padding-right: .5% !important;
            width: 20%;
        }

        #oscarEmailHeader #oscarEmailHeaderLeftColumn h1 {
            margin: 0px;
            padding: 7px !important;
            display: block;
            font-size: large !important;
            background-color: black;
            color: white;
            font-weight: bold;
        }

        #oscarEmailHeaderRightColumn {
            vertical-align: top;
            text-align: right;
            padding-top: 3px;
            padding-right: 3px;
        }

        span.HelpAboutLogout a {
            font-size: x-small;
            color: black;
            float: right;
            padding: 0 3px;
        }

        label.invalid {
            color: red;
            font-weight: normal;
        }

        input.invalid {
            border-color: red;
        }

        /*
         * Compose form is a normal vertical document, not a flex/grid dashboard.
         * Each major section is a full-width card stacked top to bottom; constraining
         * the overall width keeps line lengths readable on wide desktops.
         */
        .email-compose-form {
            max-width: 980px;
        }

        .email-compose-form > .card,
        .email-compose-form > #additionalParams,
        .email-compose-form > #form-control-buttons {
            width: 100%;
        }

        #isEncryption {
            color: green;
            font-size: 15px;
        }

        #isEncryption.off {
            color: red;
        }

        .accordion-button * {
            margin-right: 5px;
        }

        /* Bootstrap 5: using .d-none utility for display:none */

        .error-message {
            color: red;
            font-size: 12px;
            margin-top: 5px;
        }

        .custom-toast {
            position: fixed;
            z-index: 9999;
        }
    </style>

</head>
<body>
<jsp:include page="/WEB-INF/jsp/includes/spinner.jspf" flush="true"/>
<div id="bodyrow" class="container-fluid">

    <div id="bodycolumn" class="col-sm-12">

        <div id="page-header">
            <table id="oscarEmailHeader">
                <tr>
                    <td id="oscarEmailHeaderLeftColumn"><h1>${emailComposeTitle}</h1></td>

                    <td id="oscarEmailHeaderRightColumn" align=right>
                    </td>
                </tr>
            </table>
        </div>

        <div id="page-body">

            <c:choose>
                <c:when test="${transactionType eq 'EFORM'}">
                    <c:set var="emailSendAction" value="${ctx}/email/emailSendAction?method=sendEFormEmail"/>
                </c:when>
                <c:when test="${transactionType eq 'DIRECT'}">
                    <c:set var="emailSendAction" value="${ctx}/email/emailSendAction?method=sendDirectEmail"/>
                </c:when>
            </c:choose>

            <input type="hidden" name="isEmailError" id="isEmailError" value="${isEmailError}"/>
            <input type="hidden" name="emailErrorMessage" id="emailErrorMessage" value="${emailErrorMessage}"/>
            <input type="hidden" name="isEmailSuccessful" id="isEmailSuccessful" value="${isEmailSuccessful}"/>
            <input type="hidden" name="emailPatientChartOption" id="emailPatientChartOption"
                   value="${carlos:forHtmlAttribute(empty param.emailPatientChartOption ? emailPatientChartOption : param.emailPatientChartOption)}"/>
            <input type="hidden" name="totalSenderEmails" id="totalSenderEmails" value="${fn:length(senderAccounts)}"/>
            <input type="hidden" name="totalRecipintEmails" id="totalRecipintEmails"
                   value="${fn:length(receiverEmailList)}"/>
            <input type="hidden" name="totalInvalidRecipintEmails" id="totalInvalidRecipintEmails"
                   value="${fn:length(invalidReceiverEmailList)}"/>

            <form id="emailComposeForm" class="email-compose-form" action='${ emailSendAction }' method="post"
                  onsubmit="return validateEmailForm()" novalidate>
                <input type="hidden" name="demographicId" value="${demographicId}"/>
                <input type="hidden" name="fdid" value="${fdid}"/>
                <input type="hidden" name="fid" id="fid" value="${carlos:forHtmlAttribute(fid)}"/>
                <input type="hidden" name="openEFormAfterEmail" value="${openEFormAfterEmail}"/>
                <input type="hidden" name="deleteEFormAfterEmail" value="${deleteEFormAfterEmail}"/>
                <input type="hidden" name="transactionType" id="transactionType" value="${transactionType}"/>

                <div class="card">
                    <div class="card-header">
                        <h5 class="card-title"><fmt:message key="messenger.ViewMessage.msgFrom"/></h5>
                    </div>
                    <div class="card-body">
                        <div class="container">
                            <div class="row">
                                <div class="col-sm-12 mb-3">
                                    <label for="senderEmailAddress">${emailComposeSenderLabel}</label>
                                    <select class="form-select" name="senderConfigId" id="senderEmailAddress"
                                            onchange="showAdditionalParamOption()">
                                        <c:forEach items="${ senderAccounts }" var="senderAccount">
                                            <option value="${carlos:forHtmlAttribute(senderAccount.id)}"
                                                    data-email-type="${carlos:forHtmlAttribute(senderAccount.emailType)}"
                                                    <c:if test="${ senderAccount.id eq senderConfigId or senderAccount.senderEmail eq senderEmail }">selected</c:if>>
                                                ${carlos:forHtml(senderAccount.senderFirstName)} ${carlos:forHtml(senderAccount.senderLastName)} (${carlos:forHtml(senderAccount.senderEmail)})
                                            </option>
                                        </c:forEach>
                                    </select>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <div class="card mt-4">
                    <div class="card-header">
                        <h5 class="card-title"><fmt:message key="messenger.ViewMessage.msgTo"/></h5>
                    </div>
                    <div class="card-body">
                        <div class="container">
                            <div class="row mb-3 align-items-center">
                                <div class="col-sm-3">
                                    <label class="col-form-label" for="receiverName">${emailComposePatientLabel}</label>
                                </div>
                                <div class="col-sm-9">
                                    <input class="autocomplete form-control" type="text" name="recipient"
                                           value="${ receiverName }" id="receiverName" placeholder="${emailComposeSearchPatientPlaceholder}"
                                           disabled/>
                                </div>
                            </div>
                            <div id="receiverEmailsContainer">
                                <c:forEach items="${ receiverEmailList }" var="receiverEmail" varStatus="loop">
                                    <div class="row mb-3 mt-3 align-items-center">
                                        <div class="col-sm-3">
                                            <label class="col-form-label" for="receiverEmailAddress${loop.index + 1}">${emailComposeEmailAddressesLabel}</label>
                                        </div>
                                        <div class="col-sm-8">
                                            <input class="form-control" type="email" name="receiverEmailAddress"
                                                   value="${ receiverEmail }" id="receiverEmailAddress${loop.index + 1}"
                                                   placeholder="example@example.com" disabled/>
                                            <c:if test="${not empty receiverEmail}">
                                                <input type="hidden" name="receiverEmailAddress"
                                                       value="${receiverEmail}"/>
                                            </c:if>
                                        </div>
                                        <div class="col-sm-1">
                                            <button type="button" title="${emailComposeRemoveEmail}" class="btn btn-danger"
                                                    onclick="removeReceiverEmail(this)"><i class="fa-solid fa-xmark"></i>
                                            </button>
                                        </div>
                                    </div>
                                </c:forEach>
                            </div>
                        </div>
                    </div>
                    <div class="card-footer">
                        <span class="fa-solid fa-triangle-exclamation"></span> ${carlos:forHtml(emailConsentName)}: <b>${carlos:forHtml(emailConsentStatus)}</b>
                        <input type="hidden" name="emailConsentStatus" value="${emailConsentStatus}"/>
                    </div>
                </div>

                <div class="modal fade" id="errorMessageModal" tabindex="-1"
                     aria-labelledby="errorMessageModalLabel" aria-hidden="true">
                    <div class="modal-dialog">
                        <div class="modal-content">
                            <div class="modal-header">
                                <h5 class="modal-title" id="errorMessageModalLabel">${empty receiverEmailList or empty senderAccounts ? emailComposeWarning : emailComposeAdditionalEmailAddressData}</h5>
                                <button type="button" name="close" class="btn-close" data-bs-dismiss="modal"
                                        aria-label="${emailComposeClose}"></button>
                            </div>
                            <div class="modal-body">
                                <c:if test="${empty senderAccounts}">
                                    <p>${emailComposeNoOutgoingEmailAccount}</p>
                                    <c:if test="${empty receiverEmailList or not empty invalidReceiverEmailList}">
                                        <hr>
                                    </c:if>
                                </c:if>
                                <c:choose>
                                    <c:when test="${empty receiverEmailList && empty invalidReceiverEmailList}">
                                        <p>${emailComposeNoValidEmail}
                                            ${emailComposeUpdateDemographic} (<a href="#"
                                                                                onclick="openDemographicPage(event)"
                                                                                class="alert-link">${ receiverName }</a>)
                                            ${emailComposeAndTryAgain}</p>
                                    </c:when>
                                    <c:when test="${empty receiverEmailList && not empty invalidReceiverEmailList}">
                                        <p>${emailComposeNoValidEmail}
                                            ${emailComposeAdditionalSnippets} <a
                                                    href="#" onclick="openDemographicPage(event)"
                                                    class="alert-link">${ receiverName }</a></p>
                                        <ul>
                                            <c:forEach items="${ invalidReceiverEmailList }" var="invalidEmail">
                                                <li>${carlos:forHtml(invalidEmail)}</li>
                                            </c:forEach>
                                        </ul>
                                    </c:when>
                                    <c:when test="${not empty invalidReceiverEmailList}">
                                        <p><strong>${emailComposeWarning}:</strong> ${emailComposeWarningAdditionalSnippets}
                                            <a href="#" onclick="openDemographicPage(event)"
                                                                class="alert-link">${ receiverName }</a></p>
                                        <ul>
                                            <c:forEach items="${ invalidReceiverEmailList }" var="invalidEmail">
                                                <li>${carlos:forHtml(invalidEmail)}</li>
                                            </c:forEach>
                                        </ul>
                                    </c:when>
                                </c:choose>
                            </div>
                            <c:if test="${not empty invalidReceiverEmailList}">
                                <div class="modal-footer justify-content-start">
                                    <p>${emailComposeCorrectEmailBeforeProceeding}</p>
                                </div>
                            </c:if>
                        </div>
                    </div>
                </div>

                <div class="card mt-4">
                    <div class="card-header">
                        <h5 class="card-title"><fmt:message key="messenger.ViewMessage.msgSubject"/></h5>
                    </div>
                    <div class="card-body">
                        <div class="container">
                            <div class="row">
                                <div class="col-sm-12">
                                    <c:set var="subjectEmail"
                                           value="${ empty param.subjectEmail ? subjectEmail : param.subjectEmail }"/>
                                    <input class="form-control" type="text" name="subjectEmail" id="subjectEmail"
                                           placeholder="<fmt:message key='messenger.ViewMessage.msgSubject'/>" value="${carlos:forHtmlAttribute(subjectEmail)}"
                                           autocomplete="off"/>
                                    <div class="error-message" id="subjectError"></div>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div class="card-footer text-danger">
                        <span class="fa-solid fa-triangle-exclamation me-2"></span> ${emailComposeUnencryptedSubject}
                    </div>
                </div>

                <div class="card mt-4">
                    <div class="card-header">
                        <h5 class="card-title">${emailComposeBodyLabel}</h5>
                    </div>
                    <div class="card-body">
                        <div class="container">
                            <div class="row">
                                <div class="col-sm-12">
                                    <textarea class="form-control" name="bodyEmail" id="bodyEmail" rows="7"
                                              placeholder="${emailComposeBodyPlaceholder}">${carlos:forHtml(empty param.bodyEmail ? bodyEmail : param.bodyEmail)}</textarea>
                                    <div class="error-message" id="bodyError"></div>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div class="card-footer text-danger">
                        <span class="fa-solid fa-triangle-exclamation me-2"></span> ${emailComposeUnencryptedBody}
                    </div>
                </div>

                <div class="card mt-4">
                    <div class="card-header d-flex justify-content-between align-items-center">
                        <h5 class="card-title mb-0">
                            <span class="fa-solid fa-lock"></span> ${emailComposeEncryptionLabel} <span id="encryptionOptionsInfo"
                                                                             class="fa-solid fa-circle-info"
                                                                             data-bs-toggle="tooltip"
                                                                             data-bs-placement="right"
                                                                             title="${emailComposeEncryptionTooltip}"></span>
                        </h5>
                        <div class="form-check form-switch mb-0">
                            <input class="form-check-input" type="checkbox" id="encryptionSwitch"
                                   onClick="showEncryptionOptions()" ${ isEmailEncrypted ? 'checked' : '' }>
                            <label class="form-check-label" for="encryptionSwitch" id="isEncryption">${emailComposeStateOn}</label>
                        </div>
                    </div>
                    <div class="alert alert-danger rounded-0 border-0 mb-0 d-flex align-items-center ${ isEmailEncrypted ? 'd-none' : '' }" id="encryptionDisabledWarning" role="alert">
                        <span class="fa-solid fa-triangle-exclamation me-2"></span> ${emailComposeEncryptionDisabledWarning}
                    </div>
                    <div class="card-body" id="encryptionOptions">
                        <div class="container">
                            <div class="row">
                                <div class="col-sm-12 mb-3">
                                    <label>${emailComposeEncryptedMessageLabel} <span id="encryptedMessageInfo" class="fa-solid fa-circle-info"
                                                                   data-bs-toggle="tooltip" data-bs-placement="right"
                                                                   title="${emailComposeEncryptedMessageTooltip}"></span></label>
                                    <textarea class="form-control" name="encryptedMessage" id="encryptedMessage"
                                              rows="5" placeholder="${emailComposeEncryptedMessagePlaceholder}">${carlos:forHtml(empty param.encryptedMessageEmail ? encryptedMessageEmail : param.encryptedMessageEmail)}</textarea>
                                    <div class="error-message" id="encryptedMessageError"></div>
                                </div>
                            </div>
                            <div class="row mt-3 mb-3 align-items-center">
                                <div class="col-sm-3">
                                    <label class="col-form-label" for="emailPDFPassword">${emailComposePasswordLabel}</label>
                                </div>
                                <div class="col-sm-9">
                                    <input class="form-control" type="text" name="emailPDFPassword"
                                           id="emailPDFPassword" placeholder="${emailComposePasswordPlaceholder}"
                                           value="${carlos:forHtmlAttribute(not empty param.passwordEmail ? param.passwordEmail : emailPDFPassword)}"
                                           autocomplete="off"/>
                                    <div class="error-message" id="emailPDFPasswordError"></div>
                                </div>
                            </div>
                            <div class="row mt-3 mb-3 align-items-center">
                                <div class="col-sm-3">
                                    <label class="col-form-label" for="emailPDFPasswordClue">${emailComposeClueLabel} <span id="clueInfo" class="fa-solid fa-circle-info" data-bs-toggle="tooltip"
                                                      data-bs-placement="right"
                                                      title="${emailComposeClueTooltip}"></span></label>
                                </div>
                                <div class="col-sm-9">
                                    <textarea class="form-control" name="emailPDFPasswordClue" id="emailPDFPasswordClue"
                                              rows="2" placeholder="${emailComposeCluePlaceholder}">${carlos:forHtml(not empty param.passwordClueEmail ? param.passwordClueEmail : emailPDFPasswordClue)}</textarea>
                                    <div class="error-message" id="emailPDFPasswordClueError"></div>
                                </div>
                            </div>
                            <div class="row mt-3 mb-3 align-items-center">
                                <div class="col-sm-3">
                                    <label class="col-form-label" for="encryptAttachmentSwitch">${emailComposeEncryptAttachmentsLabel} <span id="encryptAttachmentInfo" class="fa-solid fa-circle-info"
                                                                     data-bs-toggle="tooltip" data-bs-placement="right"
                                                                     title="${emailComposeEncryptAttachmentsTooltip}"></span></label>
                                </div>
                                <div class="col-sm-9">
                                    <div class="form-check form-switch">
                                        <input class="form-check-input" type="checkbox" id="encryptAttachmentSwitch"
                                               onClick="toggleEncryptAttachmentStatus(this)" ${ isEmailAttachmentEncrypted ? 'checked' : '' }>
                                    </div>
                                </div>
                            </div>
                            <input type="hidden" name="isEmailAttachmentEncrypted" id="isEmailAttachmentEncrypted"
                                   value="${ isEmailAttachmentEncrypted ? 'true' : 'false' }"/>
                            <input type="hidden" name="isEmailEncrypted" id="isEmailEncrypted"
                                   value="${ isEmailEncrypted ? 'true' : 'false' }"/>
                        </div>
                    </div>
                </div>

                <div class="card mt-4">
                    <div class="card-header">
                        <h5 class="card-title">
                            ${emailComposeAdditionalOptions}
                        </h5>
                    </div>
                    <div class="card-body">
                        <div class="container">
                            <div class="row">
                                <div class="col-sm-12">
                                    <label>${emailComposeChartOptions}</label>
                                    <div class="form-check">
											<input class="form-check-input" type="radio" name="patientChartOption" id="doNotAddAsNoteOption" value="doNotAddAsNote" onClick="toggleInternalTextArea()">
                                            <label class="form-check-label" for="doNotAddAsNoteOption">
                                                <fmt:message key="email.compose.chart.doNotAdd"/>
                                            </label>
                                    </div>
                                    <div class="form-check">
											<input class="form-check-input" type="radio" name="patientChartOption" id="addFullNoteOption" value="addFullNote" checked onClick="toggleInternalTextArea()">
                                            <label class="form-check-label" for="addFullNoteOption">
                                                <fmt:message key="email.compose.chart.addNote"/>
                                            </label>
										<div id="internalCommentContainer" class="d-none">
											<textarea class="form-control" id="internalComment" name="internalComment" placeholder="<fmt:message key='email.compose.chart.internalComment'/>" rows="3">${carlos:forHtml(not empty param.internalComment ? param.internalComment : internalComment)}</textarea>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <div class="card mt-4">
                    <div class="card-header">
                        <h5 class="card-title"><fmt:message key="messenger.ViewMessage.msgAttachments"/></h5>
                    </div>
                    <div class="card-body">
                        <div class="container">
                            <div class="row">
                                <div class="accordion col-sm-12" id="emailAttachmentList">
                                    <c:forEach items="${ emailAttachmentList }" var="emailAttachment" varStatus="loop">
                                        <div class="accordion-item emailAttachmentItem">
                                            <div class="accordion-header" id="emailAttachmentHeader${loop.index + 1}">
                                                <button class="accordion-button collapsed" type="button"
                                                        data-bs-toggle="collapse"
                                                        data-bs-target="#emailAttachmentBody${loop.index + 1}"
                                                        aria-expanded="false"
                                                        aria-controls="emailAttachmentBody${loop.index + 1}">
                                                    <i class="fa-solid fa-file attachmentIcon"></i> <span
                                                        class="attachmentName">${carlos:forHtml(emailAttachment.fileName)}</span>
                                                    <span class="text-muted attachmentSize">${carlos:forHtml(emailAttachment.fileSize)}</span>
                                                </button>
                                            </div>
                                            <div id="emailAttachmentBody${loop.index + 1}"
                                                 class="accordion-collapse collapse"
                                                 aria-labelledby="emailAttachmentHeader${loop.index + 1}"
                                                 data-bs-parent="#emailAttachmentList">
                                                <div class="accordion-body">
                                                    <object id="emailAttachmentPDF${loop.index + 1}"
                                                            data="${ctx}/previewDocs?method=renderPDF&pdfPath=${emailAttachment.filePath}"
                                                            type="application/pdf" width="100%" height="500">
                                                    </object>
                                                </div>
                                            </div>
                                        </div>
                                    </c:forEach>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <div id="additionalParams" class="m-2 row d-none">
                    <div class="col-sm-3">
                        <button type="button" class="btn btn-link text-decoration-none"
                                onclick="showAdditionalParamsTextBox()">${emailComposeAddAdditionalParameters}
                        </button>
                    </div>
                    <div class="col-sm-9">
                        <c:set var="emailAdditionalParams"
                               value="${not empty emailAdditionalParams ? emailAdditionalParams : ''}"/>
                        <input type="text" class="form-control ${ not empty emailAdditionalParams ? '' : 'd-none' }"
                               name="additionalURLParams" id="additionalURLParams"
                               placeholder="<fmt:message key='email.compose.additionalParams.placeholder'/>"
                               value="${carlos:forHtmlAttribute(emailAdditionalParams)}">
                    </div>
                </div>

                <div class="container mt-4" id="form-control-buttons">
                    <div class="row">
                        <div class="col-sm-12">
                            <button type="submit" id="btnSend" class="btn btn-primary btn-md float-end" value="${emailComposeSend}">
                                <span class="btn-label"><i class="fa-solid fa-location-arrow"></i></span>
                                ${emailComposeSend}
                            </button>
                            <button formnovalidate="formnovalidate" id="btnCancel"
                                    class="btn btn-danger btn-md float-end" value="${emailComposeCancel}" name="close"
                                    onclick="cancelEmail()">
                                <span class="btn-label"><i class="fa-solid fa-xmark"></i></span>
                                ${emailComposeCancel}
                            </button>
                        </div>
                    </div>
                </div>
            </form>
        </div>

        <%-- the confirmation tags. --%>
        <c:if test="${ not empty isEmailSuccessful }">
            <c:choose>
                <c:when test="${ emailLog.status eq 'SUCCESS' }">
				<div class="alert alert-success" role="alert" id="successMessage">
					<p><fmt:message key="email.compose.msg.sentTo"/> <b>${carlos:forHtml(fn:join(emailLog.toEmail, ', '))}</b> <fmt:message key="email.compose.msg.successfullySent"/></p>
                    </div>
				<p class="mt-1" id="windowCloseMessage">${emailComposeWindowClosing}</p>
                </c:when>
                <c:otherwise>
                    <div class="alert alert-danger" role="alert">
                        <p><fmt:message key="email.compose.msg.yourEmailTo"/> <b>${carlos:forHtml(fn:join(emailLog.toEmail, ', '))}</b> <fmt:message key="email.compose.msg.wasNotSent"/>
                            <fmt:message key="email.compose.msg.reviewErrorAndTryAgain"/><br><br>
                            <b><fmt:message key="email.compose.msg.errorMessage"/></b> <br>
                            ${carlos:forHtml(emailLog.errorMessage)}</p>
                    </div>
                </c:otherwise>
            </c:choose>
            <input type="button" class="btn btn-danger btn-md float-end" value="${emailComposeClose}" onclick="window.close();"/>
        </c:if>
    </div>
</div>

<script type="text/javascript">
    document.addEventListener("DOMContentLoaded", function () {
        // Initialize BS5 tooltips
        document.querySelectorAll('[data-bs-toggle="tooltip"]').forEach(function (el) {
            new bootstrap.Tooltip(el);
        });

        // Check if any error
        if (document.getElementById('isEmailError').value === 'true') {
            // Open EForm again on sent
            showErrorAndClose();
            return;
        }

        // After sending email
        if (document.getElementById('isEmailSuccessful').value === 'true' || document.getElementById('isEmailSuccessful').value === 'false') {
            // Open EForm again on sent
            openEFormAfterSend();

		if (document.getElementById('isEmailSuccessful').value === 'true') {
			// Close the window after 3 seconds
			setTimeout(() => {
				window.close();
			}, 3000);
		}
            return;
        }

        // Auto-send email
        autoSendEmail();

        // Convert attachment size into kb/mb
        convertAttachmentSize();

        // Display an error if there are 0 senders, 0 recipients, or if the recipients' addresses are invalid.
        displayErrorOnInvalidEmail();

        // Show encryption options
        showEncryptionOptions();

        // Select chart option from user's preference
        selectPatientChartOption();

        // Show additional field option if API type sender is selected
        showAdditionalParamOption();

	// Toggle internal note text area
	toggleInternalTextArea();
    });

    document.addEventListener("keydown", function (event) {
        if (event.key === "Enter" && event.target.tagName.toLowerCase() !== "textarea") {
            event.preventDefault();
        }
    });

    const emailComposeSubjectRequiredMsg = "<carlos:encode value='${emailComposeSubjectRequired}' context="javaScript"/>";
    const emailComposeBodyRequiredMsg = "<carlos:encode value='${emailComposeBodyRequired}' context="javaScript"/>";
    const emailComposePasswordRequiredMsg = "<carlos:encode value='${emailComposePasswordRequired}' context="javaScript"/>";
    const emailComposeClueRequiredMsg = "<carlos:encode value='${emailComposeClueRequired}' context="javaScript"/>";
    const emailComposePasswordMinLengthMsg = "<carlos:encode value='${emailComposePasswordMinLength}' context="javaScript"/>";
    const emailComposeMinimumRecipientMsg = "<carlos:encode value='${emailComposeMinimumRecipient}' context="javaScript"/>";
    const emailComposeStateOnMsg = "<carlos:encode value='${emailComposeStateOn}' context="javaScript"/>";
    const emailComposeStateOffMsg = "<carlos:encode value='${emailComposeStateOff}' context="javaScript"/>";

    function validateEmailForm() {
        if (!validateForm()) {
            return false;
        }
        ShowSpin(true);
        return true;
    }

    function validateForm() {
        const subjectEmail = document.getElementById('subjectEmail');
        const bodyEmail = document.getElementById('bodyEmail');
        const isEncrypted = document.getElementById('encryptionSwitch').checked;
        const hasEncryptedMessage = document.getElementById('encryptedMessage').value.trim() !== '';
        const isAttachmentEncrypted = document.getElementById('encryptAttachmentSwitch').checked;
        const emailPDFPassword = document.getElementById('emailPDFPassword');
        const emailPDFPasswordClue = document.getElementById('emailPDFPasswordClue');
        const hasAttachments = document.querySelectorAll('.emailAttachmentItem').length > 0;
        const hasSender = document.getElementById('totalSenderEmails') && document.getElementById('totalSenderEmails').value > 0;
        const hasRecipint = document.getElementById('totalRecipintEmails') && document.getElementById('totalRecipintEmails').value > 0;

        if (!hasSender || !hasRecipint) {
            return false;
        }

        const errors = {};

        validateField(subjectEmail, emailComposeSubjectRequiredMsg, errors, 'subjectError');
        validateField(bodyEmail, emailComposeBodyRequiredMsg, errors, 'bodyError');
        if (isEncrypted) {
            if (hasEncryptedMessage) {
                validateField(emailPDFPassword, emailComposePasswordRequiredMsg, errors, 'emailPDFPasswordError');
                validateField(emailPDFPasswordClue, emailComposeClueRequiredMsg, errors, 'emailPDFPasswordClueError');
            } else if (hasAttachments && isAttachmentEncrypted) {
                validateField(emailPDFPassword, emailComposePasswordRequiredMsg, errors, 'emailPDFPasswordError');
                validateField(emailPDFPasswordClue, emailComposeClueRequiredMsg, errors, 'emailPDFPasswordClueError');
            } else {
                clearError('emailPDFPasswordError');
                clearError('emailPDFPasswordClueError');
            }
        }

        if (Object.keys(errors).length === 0) {
            return true;
        }
        return false;
    }

    function validateField(field, errorMessage, errors, errorElementId) {
        clearError(errorElementId);

        if (field.value.trim() === '') {
            errors[field.name] = errorMessage;
            displayError(errorElementId, errorMessage);
        } else if (field.value.trim().length < 5 && field.id === 'emailPDFPassword') {
            errorMessage = emailComposePasswordMinLengthMsg;
            errors[field.name] = errorMessage;
            displayError(errorElementId, errorMessage);
        }
    }

    function displayError(errorElementId, errorMessage) {
        const errorElement = document.getElementById(errorElementId);
        errorElement.innerHTML = errorMessage;
        errorElement.parentNode.firstElementChild.classList.add("is-invalid");
        setTimeout(function () {
            errorElement.scrollIntoView({block: 'center'});
        }, 100);
    }

    function clearError(errorElementId) {
        const errorElement = document.getElementById(errorElementId);
        errorElement.innerHTML = '';
        errorElement.parentNode.firstElementChild.classList.remove("is-invalid");
    }

    function showEncryptionOptions() {
        const checkbox = document.getElementById("encryptionSwitch");
        document.getElementById("encryptionOptions").classList.toggle('d-none', !checkbox.checked);
        document.getElementById("isEmailEncrypted").value = checkbox.checked ? "true" : "false";
        document.getElementById("isEncryption").innerHTML = checkbox.checked ? emailComposeStateOnMsg : emailComposeStateOffMsg;
        document.getElementById("isEncryption").classList.toggle("off", !checkbox.checked);
        // Make the risk explicit whenever encryption is turned off: the message and any
        // attachments will leave CARLOS unencrypted, so PHI must not be included.
        document.getElementById("encryptionDisabledWarning").classList.toggle('d-none', checkbox.checked);
    }

    function toggleEncryptAttachmentStatus(checkbox) {
        document.getElementById("isEmailAttachmentEncrypted").value = checkbox.checked ? "true" : "false";
    }

    function removeReceiverEmail(button) {
        let receiverEmailsContainer = document.getElementById("receiverEmailsContainer");
        let formGroup = button.closest('.mb-3');
        if (receiverEmailsContainer.children.length > 1) {
            receiverEmailsContainer.removeChild(formGroup);
        } else {
            alert(emailComposeMinimumRecipientMsg);
        }
    }

    // Open EForm again on sent
    function openEFormAfterSend() {
        const isOpenEForm = "${isOpenEForm}" === "true";
        if (isOpenEForm) {
            window.open("${ctx}/eform/efmshowform_data?fdid=${fdid}", "_blank", "width=800,height=600");
        }
    }

    // Auto-send email
    function autoSendEmail() {
        const emailComposeForm = document.getElementById('emailComposeForm');
        const isAutoSend = "${isEmailAutoSend}" === "true";
        if (isAutoSend && validateForm()) {
            ShowSpin(true);
            emailComposeForm.submit();
        }
    }

    // Convert attachment size into kb/mb
    function convertAttachmentSize() {
        let sizeElements = document.getElementsByClassName("attachmentSize");

        for (let i = 0; i < sizeElements.length; i++) {
            let attachmentSize;

            let sizeInBytes = parseFloat(sizeElements[i].innerHTML);

            if (isNaN(sizeInBytes) || sizeInBytes <= 0) {
                attachmentSize = '0bytes';
            } else {
                const units = ['bytes', 'KB', 'MB'];
                let j = 0;

                while (sizeInBytes >= 1024 && j < units.length - 1) {
                    sizeInBytes /= 1024;
                    j++;
                }

                attachmentSize = sizeInBytes.toFixed(1) + units[j];
            }

            sizeElements[i].innerHTML = attachmentSize;
        }
    }

    // Display an error if there are 0 senders, 0 recipients, or if the recipients' addresses are invalid.
    function displayErrorOnInvalidEmail() {
        const hasSender = document.getElementById('totalSenderEmails') && document.getElementById('totalSenderEmails').value > 0;
        const hasValidRecipient = document.getElementById('totalRecipintEmails') && document.getElementById('totalRecipintEmails').value > 0;
        const hasInvalidRecipint = document.getElementById('totalInvalidRecipintEmails') && document.getElementById('totalInvalidRecipintEmails').value > 0;

        if (!hasSender || !hasValidRecipient || hasInvalidRecipint) {
            const errorMessageModal = new bootstrap.Modal(document.getElementById('errorMessageModal'));
            errorMessageModal.show();
        }

        if (!hasSender || !hasValidRecipient) {
            disableForm();
        }
    }

    // Select chart option from user's preference
    function selectPatientChartOption() {
        const emailPatientChartOptionValue = document.getElementById('emailPatientChartOption').value;
        const radioButton = document.querySelector('input[name="patientChartOption"][value="' + emailPatientChartOptionValue + '"]');

        // Check the radio button if it exists
        radioButton && (radioButton.checked = true);
    }

    function disableForm() {
        const emailComposeFormFields = document.getElementById("emailComposeForm").getElementsByTagName('*');
        for (let i = 0; i < emailComposeFormFields.length; i++) {
            if (emailComposeFormFields[i].name === "close") {
                continue;
            }
            emailComposeFormFields[i].disabled = true;
        }
    }

    function openDemographicPage(event) {
        event.preventDefault();
        window.open("${ctx}/demographic/DemographicEdit?demographic_no=${demographicId}", "_blank", "width=1027,height=700");
    }

    function cancelEmail() {
        const transactionType = document.getElementById("transactionType").value;
        if (transactionType === 'DIRECT') {
            window.close();
        }
        const emailComposeForm = document.getElementById("emailComposeForm");
        emailComposeForm.action = "${ctx}/email/emailSendAction?method=cancel";
        emailComposeForm.submit();
    }

    function showAdditionalParamOption() {
        const senderEmailAddress = document.getElementById('senderEmailAddress');
        const selectedSender = senderEmailAddress.options[senderEmailAddress.selectedIndex];
        if (selectedSender === null) {
            return;
        }

        const senderEmailType = selectedSender.getAttribute('data-email-type');
        if (senderEmailType && senderEmailType === "API") {
            document.getElementById('additionalParams').classList.remove('d-none');
        } else {
            document.getElementById('additionalParams').classList.add('d-none');
        }
    }

    function showAdditionalParamsTextBox() {
        document.getElementById('additionalURLParams').classList.toggle('d-none');
    }

    function showErrorAndClose() {
        const errorMessage = document.getElementById('emailErrorMessage').value.replace(/\\n/g, '\n');
        alert(errorMessage);
        window.close();
    }

function toggleInternalTextArea() {
	const addFullNoteOption = document.getElementById('addFullNoteOption');
	const internalCommentContainer = document.getElementById('internalCommentContainer');

	if (addFullNoteOption.checked) {
		internalCommentContainer.classList.remove('d-none'); // Show the textarea
	} else {
		internalCommentContainer.classList.add('d-none'); // Hide the textarea
	}
}

</script>
</body>
</html>
