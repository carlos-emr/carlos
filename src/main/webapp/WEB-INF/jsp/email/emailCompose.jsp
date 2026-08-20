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
    <fmt:message key="email.compose.heading.message" var="emailComposeMessageLabel"/>
    <fmt:message key="email.compose.placeholder.message" var="emailComposeMessagePlaceholder"/>
    <fmt:message key="email.compose.msg.encryptedMessageNotice" var="emailComposeEncryptedMessageNotice"/>
    <fmt:message key="email.compose.msg.unencryptedSubject" var="emailComposeUnencryptedSubject"/>
    <fmt:message key="email.compose.msg.encryptionDisabledWarning" var="emailComposeEncryptionDisabledWarning"/>
    <fmt:message key="email.compose.modal.disableEncryption.title" var="emailComposeDisableEncryptionTitle"/>
    <fmt:message key="email.compose.modal.disableEncryption.body" var="emailComposeDisableEncryptionBody"/>
    <fmt:message key="email.compose.modal.disableEncryption.confirm" var="emailComposeDisableEncryptionConfirm"/>
    <fmt:message key="email.compose.modal.disableEncryption.cancel" var="emailComposeDisableEncryptionCancel"/>
    <fmt:message key="email.compose.label.encryption" var="emailComposeEncryptionLabel"/>
    <fmt:message key="email.compose.tooltip.encryption" var="emailComposeEncryptionTooltip"/>
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
    <fmt:message key="email.compose.msg.messageRequired" var="emailComposeMessageRequired"/>
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

                <%-- To and From sit side by side: recipient (To) first/leftmost, sender (From) on the right.
                     Equal-height cards keep the row tidy when the To card grows with extra recipients. --%>
                <div class="row g-3">
                    <div class="col-md-6">
                        <div class="card h-100">
                            <div class="card-header">
                                <h5 class="card-title"><fmt:message key="messenger.ViewMessage.msgTo"/></h5>
                            </div>
                            <div class="card-body">
                                <div class="mb-3">
                                    <label class="form-label" for="receiverName">${emailComposePatientLabel}</label>
                                    <input class="autocomplete form-control" type="text" name="recipient"
                                           value="${carlos:forHtmlAttribute(receiverName)}" id="receiverName" placeholder="${emailComposeSearchPatientPlaceholder}"
                                           disabled/>
                                </div>
                                <div id="receiverEmailsContainer">
                                    <c:forEach items="${ receiverEmailList }" var="receiverEmail" varStatus="loop">
                                        <div class="mb-3">
                                            <label class="form-label" for="receiverEmailAddress${loop.count}">${emailComposeEmailAddressesLabel}</label>
                                            <div class="input-group">
                                                <input class="form-control" type="email" name="receiverEmailAddress"
                                                       value="${carlos:forHtmlAttribute(receiverEmail)}" id="receiverEmailAddress${loop.count}"
                                                       placeholder="example@example.com" disabled/>
                                                <button type="button" title="${emailComposeRemoveEmail}" class="btn btn-danger"
                                                        onclick="removeReceiverEmail(this)"><i class="fa-solid fa-xmark"></i>
                                                </button>
                                            </div>
                                            <c:if test="${not empty receiverEmail}">
                                                <input type="hidden" name="receiverEmailAddress"
                                                       value="${carlos:forHtmlAttribute(receiverEmail)}"/>
                                            </c:if>
                                        </div>
                                    </c:forEach>
                                </div>
                            </div>
                            <div class="card-footer">
                                <span class="fa-solid fa-triangle-exclamation"></span> ${carlos:forHtml(emailConsentName)}: <b>${carlos:forHtml(emailConsentStatus)}</b>
                                <input type="hidden" name="emailConsentStatus" value="${carlos:forHtmlAttribute(emailConsentStatus)}"/>
                            </div>
                        </div>
                    </div>
                    <div class="col-md-6">
                        <div class="card h-100">
                            <div class="card-header">
                                <h5 class="card-title"><fmt:message key="messenger.ViewMessage.msgFrom"/></h5>
                            </div>
                            <div class="card-body">
                                <div class="mb-3">
                                    <label class="form-label" for="senderEmailAddress">${emailComposeSenderLabel}</label>
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

                <%-- Message + encryption combined into a single card (issue #3118 follow-up): the one
                     "Message" field and the controls that govern its protection now live together — the
                     encryption toggle in this header, and the password / clue / encrypt-attachments in the
                     body below. Delivery is governed entirely by the toggle: when encryption is ON the
                     message is rendered into the password-protected PDF (server-side routing in
                     EmailSend2Action maps it to encryptedMessage) and the visible email body is a fixed,
                     PHI-free notice; when OFF it is sent as the cleartext MIME body. The initial value is
                     seeded server-side into the "message" request attribute (from bodyEmail/encryptedMessage
                     on compose and resend) so the client can never populate both channels. All element ids
                     are unchanged, so showEncryptionOptions() keeps swapping the options and footer
                     notice/warning on toggle to keep the protection unambiguous. --%>
                <div class="card mt-4">
                    <div class="card-header d-flex justify-content-between align-items-center">
                        <h5 class="card-title mb-0">${emailComposeMessageLabel}</h5>
                        <div class="d-flex align-items-center gap-2">
                            <span class="fa-solid fa-lock"></span>
                            <span>${emailComposeEncryptionLabel}</span>
                            <span id="encryptionOptionsInfo" class="fa-solid fa-circle-info"
                                  data-bs-toggle="tooltip" data-bs-placement="right"
                                  title="${emailComposeEncryptionTooltip}"></span>
                            <div class="form-check form-switch mb-0">
                                <input class="form-check-input" type="checkbox" id="encryptionSwitch"
                                       onClick="showEncryptionOptions()" ${ isEmailEncrypted ? 'checked' : '' }>
                                <label class="form-check-label" for="encryptionSwitch" id="isEncryption">${emailComposeStateOn}</label>
                            </div>
                        </div>
                    </div>
                    <div class="card-body">
                        <div class="container">
                            <div class="row">
                                <div class="col-sm-12">
                                    <%-- Visually-hidden label: the visible "Message" heading lives in the card
                                         header, but the textarea still needs a programmatically associated label
                                         for accessibility (SonarCloud Web:InputWithoutLabelCheck). The submitted
                                         content is preserved across a failed-send re-render server-side, by
                                         EmailSend2Action re-seeding the "message" request attribute. --%>
                                    <label for="message" class="visually-hidden">${emailComposeMessageLabel}</label>
                                    <textarea class="form-control" name="message" id="message" rows="7"
                                              placeholder="${emailComposeMessagePlaceholder}"><carlos:encode value="${message}"/></textarea>
                                    <div class="error-message" id="messageError"></div>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div class="card-footer text-success ${ isEmailEncrypted ? '' : 'd-none' }" id="messageEncryptedNotice">
                        <span class="fa-solid fa-lock me-2"></span> ${emailComposeEncryptedMessageNotice}
                    </div>
                    <%-- Encryption controls for the message above (issue #3118 follow-up): the disable-off
                         warning plus the password / clue / encrypt-attachments controls now live in this
                         same card, governed by the encryption toggle in the header. Ids are unchanged so
                         showEncryptionOptions() keeps toggling them. --%>
                    <div class="alert alert-danger rounded-0 border-0 mb-0 d-flex align-items-center ${ isEmailEncrypted ? 'd-none' : '' }" id="encryptionDisabledWarning" role="alert">
                        <span class="fa-solid fa-triangle-exclamation me-2"></span> ${emailComposeEncryptionDisabledWarning}
                    </div>
                    <div class="card-body" id="encryptionOptions">
                        <div class="container">
                            <%-- The message content itself now lives in the single "Message" field above;
                                 this card only carries the password / clue / encrypt-attachments controls
                                 that govern how that message (and any attachments) are protected. --%>
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

                <%-- Confirmation gate shown when the provider turns encryption OFF. Disabling encryption
                     sends the message and any attachments as unencrypted plain text, so require an explicit
                     acknowledgement before applying it; dismissing/cancelling reverts the toggle to ON
                     (see showEncryptionOptions / confirmDisableEncryption). --%>
                <div class="modal fade" id="disableEncryptionModal" tabindex="-1"
                     aria-labelledby="disableEncryptionModalLabel" aria-hidden="true">
                    <div class="modal-dialog">
                        <div class="modal-content">
                            <div class="modal-header">
                                <h5 class="modal-title text-danger" id="disableEncryptionModalLabel">
                                    <span class="fa-solid fa-triangle-exclamation me-2"></span>${emailComposeDisableEncryptionTitle}
                                </h5>
                                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="${emailComposeClose}"></button>
                            </div>
                            <div class="modal-body">
                                <p class="mb-0">${emailComposeDisableEncryptionBody}</p>
                            </div>
                            <div class="modal-footer">
                                <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">${emailComposeDisableEncryptionCancel}</button>
                                <button type="button" class="btn btn-danger" id="confirmDisableEncryptionBtn" onclick="confirmDisableEncryption()">${emailComposeDisableEncryptionConfirm}</button>
                            </div>
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
                                            <div class="accordion-header" id="emailAttachmentHeader${loop.count}">
                                                <button class="accordion-button collapsed" type="button"
                                                        data-bs-toggle="collapse"
                                                        data-bs-target="#emailAttachmentBody${loop.count}"
                                                        aria-expanded="false"
                                                        aria-controls="emailAttachmentBody${loop.count}">
                                                    <i class="fa-solid fa-file attachmentIcon"></i> <span
                                                        class="attachmentName">${carlos:forHtml(emailAttachment.fileName)}</span>
                                                    <span class="text-muted attachmentSize">${carlos:forHtml(emailAttachment.fileSize)}</span>
                                                </button>
                                            </div>
                                            <div id="emailAttachmentBody${loop.count}"
                                                 class="accordion-collapse collapse"
                                                 aria-labelledby="emailAttachmentHeader${loop.count}"
                                                 data-bs-parent="#emailAttachmentList">
                                                <div class="accordion-body">
                                                    <object id="emailAttachmentPDF${loop.count}"
                                                            data="${ctx}/previewDocs?method=renderPDF&amp;previewToken=${emailAttachment.previewToken}"
                                                            type="application/pdf" width="100%" height="500">
                                                        <%-- Accessible fallback shown when the browser cannot render the inline PDF preview. --%>
                                                        <p class="text-muted mb-0">${carlos:forHtml(emailAttachment.fileName)}</p>
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

        // Apply the initial encryption state without prompting (a resend may load with encryption off,
        // and the confirmation modal must only appear on a deliberate user toggle, not on page load).
        applyEncryptionState();

        // If the disable-encryption confirmation is dismissed (Cancel / X / Esc / backdrop) without
        // confirming, revert the toggle back to ON so encryption is never silently disabled.
        const disableEncryptionModalEl = document.getElementById("disableEncryptionModal");
        if (disableEncryptionModalEl) {
            disableEncryptionModalEl.addEventListener("hidden.bs.modal", function () {
                if (!disableEncryptionConfirmed) {
                    document.getElementById("encryptionSwitch").checked = true;
                    applyEncryptionState();
                }
            });
        }

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
    const emailComposeMessageRequiredMsg = "<carlos:encode value='${emailComposeMessageRequired}' context="javaScript"/>";
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
        const message = document.getElementById('message');
        const isEncrypted = document.getElementById('encryptionSwitch').checked;
        const isAttachmentEncrypted = document.getElementById('encryptAttachmentSwitch').checked;
        const emailPDFPassword = document.getElementById('emailPDFPassword');
        const emailPDFPasswordClue = document.getElementById('emailPDFPasswordClue');
        const hasMessage = message.value.trim() !== '';
        const hasAttachments = document.querySelectorAll('.emailAttachmentItem').length > 0;
        const hasSender = document.getElementById('totalSenderEmails') && document.getElementById('totalSenderEmails').value > 0;
        const hasRecipint = document.getElementById('totalRecipintEmails') && document.getElementById('totalRecipintEmails').value > 0;

        if (!hasSender || !hasRecipint) {
            return false;
        }

        const errors = {};

        validateField(subjectEmail, emailComposeSubjectRequiredMsg, errors, 'subjectError');
        validateField(message, emailComposeMessageRequiredMsg, errors, 'messageError');
        // When encryption is on the message is rendered into the password-protected PDF, so a
        // password/clue is required whenever there is a message to encrypt (there always is, since
        // the message field is mandatory) or encrypted attachments are being sent.
        if (isEncrypted) {
            if (hasMessage || (hasAttachments && isAttachmentEncrypted)) {
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

    let disableEncryptionConfirmed = false;

    // Applies the current encryption toggle state to the form: shows/hides the password/clue/attachment
    // options, updates the hidden isEmailEncrypted flag and the On/Off label, and swaps the single
    // message notice (green "secure PDF" when on) for the "encryption is off" warning (when off).
    function applyEncryptionState() {
        const checkbox = document.getElementById("encryptionSwitch");
        document.getElementById("encryptionOptions").classList.toggle('d-none', !checkbox.checked);
        document.getElementById("isEmailEncrypted").value = checkbox.checked ? "true" : "false";
        document.getElementById("isEncryption").innerHTML = checkbox.checked ? emailComposeStateOnMsg : emailComposeStateOffMsg;
        document.getElementById("isEncryption").classList.toggle("off", !checkbox.checked);
        document.getElementById("encryptionDisabledWarning").classList.toggle('d-none', checkbox.checked);
        document.getElementById("messageEncryptedNotice").classList.toggle('d-none', !checkbox.checked);
    }

    // Guards the encryption toggle. Turning encryption OFF sends the message and any attachments as
    // plain text, so require an explicit confirmation via the modal before applying the off state;
    // dismissing/cancelling the modal reverts the toggle to ON (handled by the hidden.bs.modal
    // listener registered on load). Turning encryption back ON needs no confirmation.
    function showEncryptionOptions() {
        const checkbox = document.getElementById("encryptionSwitch");
        if (!checkbox.checked) {
            disableEncryptionConfirmed = false;
            bootstrap.Modal.getOrCreateInstance(document.getElementById("disableEncryptionModal")).show();
            return;
        }
        applyEncryptionState();
    }

    // Invoked by the modal's confirm button: the provider has acknowledged the risk, so record the
    // confirmation, close the modal and apply the encryption-off state.
    function confirmDisableEncryption() {
        disableEncryptionConfirmed = true;
        bootstrap.Modal.getOrCreateInstance(document.getElementById("disableEncryptionModal")).hide();
        applyEncryptionState();
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
