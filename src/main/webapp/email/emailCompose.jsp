<!DOCTYPE html>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib prefix="html" uri="http://www.springframework.org/tags/form" %>

<html>
<head>
    <title>OSCAR Email</title>

    <c:set var="ctx" value="${ pageContext.request.contextPath }" scope="page"/>
    <link rel="stylesheet" href="${ctx}/library/bootstrap/5.0.2/css/bootstrap.min.css" type="text/css"/>
    <link href="${ctx}/library/jquery/jquery-ui.min.css" rel="stylesheet" type="text/css"/>
    <link href="${ctx}/css/font-awesome.min.css" rel="stylesheet">

    <script type="text/javascript" src="${ctx}/library/jquery/jquery-3.6.4.min.js"></script>
    <script type="text/javascript" src="${ctx}/library/jquery/jquery.validate.min.js"></script>
    <script type="text/javascript" src="${ctx}/library/jquery/jquery-ui.min.js"></script>
    <script type="text/javascript" src="${ctx}/library/bootstrap/5.0.2/js/bootstrap.min.js"></script>

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

        .encryptionLock {
            float: right;
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

        .hide {
            display: none;
        }

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
<jsp:include page="/images/spinner.jsp" flush="true"/>
<div id="bodyrow" class="container-fluid">

    <div id="bodycolumn" class="col-sm-12">

        <div id="page-header">
            <table id="oscarEmailHeader">
                <tr>
                    <td id="oscarEmailHeaderLeftColumn"><h1>OSCAR Email</h1></td>

                    <td id="oscarEmailHeaderRightColumn" align=right>
                    </td>
                </tr>
            </table>
        </div>

        <div id="page-body">

            <c:choose>
                <c:when test="${transactionType eq 'EFORM'}">
                    <c:set var="emailSendAction" value="${ctx}/email/emailSendAction.do?method=sendEFormEmail"/>
                </c:when>
                <c:when test="${transactionType eq 'DIRECT'}">
                    <c:set var="emailSendAction" value="${ctx}/email/emailSendAction.do?method=sendDirectEmail"/>
                </c:when>
            </c:choose>

            <input type="hidden" name="isEmailError" id="isEmailError" value="${isEmailError}"/>
            <input type="hidden" name="emailErrorMessage" id="emailErrorMessage" value="${emailErrorMessage}"/>
            <input type="hidden" name="isEmailSuccessful" id="isEmailSuccessful" value="${isEmailSuccessful}"/>
            <input type="hidden" name="emailPatientChartOption" id="emailPatientChartOption"
                   value="${ empty param.emailPatientChartOption ? emailPatientChartOption : param.emailPatientChartOption }"/>
            <input type="hidden" name="totalSenderEmails" id="totalSenderEmails" value="${fn:length(senderAccounts)}"/>
            <input type="hidden" name="totalRecipintEmails" id="totalRecipintEmails"
                   value="${fn:length(receiverEmailList)}"/>
            <input type="hidden" name="totalInvalidRecipintEmails" id="totalInvalidRecipintEmails"
                   value="${fn:length(invalidReceiverEmailList)}"/>

            <form id="emailComposeForm" class="form-inline" action='${ emailSendAction }' method="post"
                  onsubmit="return validateEmailForm()" novalidate>
                <input type="hidden" name="demographicId" value="${demographicId}"/>
                <input type="hidden" name="fdid" value="${fdid}"/>
                <input type="hidden" name="openEFormAfterEmail" value="${openEFormAfterEmail}"/>
                <input type="hidden" name="deleteEFormAfterEmail" value="${deleteEFormAfterEmail}"/>
                <input type="hidden" name="transactionType" id="transactionType" value="${transactionType}"/>

                <div class="card">
                    <div class="card-header">
                        <h5 class="card-title">From</h5>
                    </div>
                    <div class="card-body">
                        <div class="container">
                            <div class="row">
                                <div class="col-sm-12 form-group">
                                    <label for="senderEmailAddress">Sender</label>
                                    <select class="form-select" name="senderEmailAddress" id="senderEmailAddress"
                                            onchange="showAdditionalParamOption()">
                                        <c:forEach items="${ senderAccounts }" var="senderAccount">
                                            <option value="${ senderAccount.senderEmail }"
                                                    data-email-type="${ senderAccount.emailType }" ${ senderAccount.senderEmail eq senderEmail or senderAccount.senderEmail eq param.senderEmail ? 'selected' : '' }>
                                                <c:out value="${ senderAccount.senderFirstName }"/> <c:out
                                                    value="${ senderAccount.senderLastName }"/> <c:out
                                                    value="(${ senderAccount.senderEmail })"/>
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
                        <h5 class="card-title">To</h5>
                    </div>
                    <div class="card-body">
                        <div class="container">
                            <div class="row form-group">
                                <div class="col-sm-1">
                                    <label for="receiverName">Patient</label>
                                </div>
                                <div class="col-sm-10">
                                    <input class="autocomplete form-control" type="text" name="recipient"
                                           value="${ receiverName }" id="receiverName" placeholder="Search: last, first"
                                           disabled/>
                                </div>
                            </div>
                            <div id="receiverEmailsContainer">
                                <c:forEach items="${ receiverEmailList }" var="receiverEmail" varStatus="loop">
                                    <div class="row form-group mt-3">
                                        <div class="col-sm-1">
                                            <label for="receiverEmailAddress${loop.index + 1}">Email(s)</label>
                                        </div>
                                        <div class="col-sm-10">
                                            <input class="form-control" type="email" name="receiverEmailAddress"
                                                   value="${ receiverEmail }" id="receiverEmailAddress${loop.index + 1}"
                                                   placeholder="example@example.com" disabled/>
                                            <c:if test="${not empty receiverEmail}">
                                                <input type="hidden" name="receiverEmailAddress"
                                                       value="${receiverEmail}"/>
                                            </c:if>
                                        </div>
                                        <div class="col-sm-1">
                                            <button type="button" title="Remove Email" class="btn btn-danger"
                                                    onclick="removeReceiverEmail(this)"><i class="icon-remove"></i>
                                            </button>
                                        </div>
                                    </div>
                                </c:forEach>
                            </div>
                        </div>
                    </div>
                    <div class="card-footer">
                        <span class="icon-warning-sign"></span> <c:out value="${ emailConsentName }"/>: <b><c:out
                            value="${ emailConsentStatus }"/></b>
                        <input type="hidden" name="emailConsentStatus" value="${emailConsentStatus}"/>
                    </div>
                </div>

                <div class="modal fade" id="errorMessageModal" tabindex="-1" role="dialog"
                     aria-labelledby="errorMessageModalLabel" aria-hidden="true">
                    <div class="modal-dialog" role="document">
                        <div class="modal-content">
                            <div class="modal-header">
                                <h5 class="modal-title" id="errorMessageModalLabel"><c:out
                                        value="${ empty receiverEmailList or empty senderAccounts ? 'Warning' : 'Additional Email Address Data' }"/></h5>
                                <button type="button" name="close" class="btn-close" data-bs-dismiss="modal"
                                        aria-label="Close"></button>
                            </div>
                            <div class="modal-body">
                                <c:if test="${empty senderAccounts}">
                                    <p>Sorry - no outgoing email account has been configured. Please contact your system
                                        administrator for more information.</p>
                                    <c:if test="${empty receiverEmailList or not empty invalidReceiverEmailList}">
                                        <hr>
                                    </c:if>
                                </c:if>
                                <c:choose>
                                    <c:when test="${empty receiverEmailList && empty invalidReceiverEmailList}">
                                        <p>Sorry - this patient does not have a valid email address in their
                                            demographic.
                                            Please update their demographic (<a href="#"
                                                                                onclick="openDemographicPage(event)"
                                                                                class="alert-link">${ receiverName }</a>)
                                            and try again.</p>
                                    </c:when>
                                    <c:when test="${empty receiverEmailList && not empty invalidReceiverEmailList}">
                                        <p>Sorry - this patient does not have a valid email address in their
                                            demographic.
                                            These additional snippets were found in the email address field of <a
                                                    href="#" onclick="openDemographicPage(event)"
                                                    class="alert-link">${ receiverName }</a></p>
                                        <ul>
                                            <c:forEach items="${ invalidReceiverEmailList }" var="invalidEmail">
                                                <li>${invalidEmail}</li>
                                            </c:forEach>
                                        </ul>
                                    </c:when>
                                    <c:when test="${not empty invalidReceiverEmailList}">
                                        <p><strong>Warning:</strong> these additional snippets were found in the email
                                            address field of <a href="#" onclick="openDemographicPage(event)"
                                                                class="alert-link">${ receiverName }</a></p>
                                        <ul>
                                            <c:forEach items="${ invalidReceiverEmailList }" var="invalidEmail">
                                                <li>${invalidEmail}</li>
                                            </c:forEach>
                                        </ul>
                                    </c:when>
                                </c:choose>
                            </div>
                            <c:if test="${not empty invalidReceiverEmailList}">
                                <div class="modal-footer justify-content-start">
                                    <p> You may wish to correct the patient's email address before proceeding</p>
                                </div>
                            </c:if>
                        </div>
                    </div>
                </div>

                <div class="card mt-4">
                    <div class="card-header">
                        <h5 class="card-title">Subject</h5>
                    </div>
                    <div class="card-body">
                        <div class="container">
                            <div class="row">
                                <div class="col-sm-12">
                                    <c:set var="subjectEmail"
                                           value="${ empty param.subjectEmail ? subjectEmail : param.subjectEmail }"/>
                                    <input class="form-control" type="text" name="subjectEmail" id="subjectEmail"
                                           placeholder="Subject" value="<c:out value='${subjectEmail}' />"
                                           autocomplete="off"/>
                                    <div class="error-message" id="subjectError"></div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <div class="card mt-4">
                    <div class="card-header">
                        <h5 class="card-title">Body</h5>
                    </div>
                    <div class="card-body">
                        <div class="container">
                            <div class="row">
                                <div class="col-sm-12">
                                    <textarea class="form-control" name="bodyEmail" id="bodyEmail" rows="7"
                                              placeholder="@message..."><c:out
                                            value="${ empty param.bodyEmail ? bodyEmail : param.bodyEmail }"/></textarea>
                                    <div class="error-message" id="bodyError"></div>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div class="card-footer">
                        <span class="icon-warning-sign"></span> Unencrypted Body of Email
                    </div>
                </div>

                <div class="card mt-4">
                    <div class="card-header">
                        <h5 class="card-title">
                            <span class="icon-lock"></span> Encryption <span id="encryptionOptionsInfo"
                                                                             class="icon-info-sign"
                                                                             data-toggle="tooltip"
                                                                             data-placement="auto right"
                                                                             title="Emails will be sent encrypted by default. Encryption settings can be modified by disabling this feature."></span>
                            <div class="form-check form-switch encryptionLock">
                                <input class="form-check-input" type="checkbox" id="encryptionSwitch"
                                       onClick="showEncryptionOptions()" ${ isEmailEncrypted ? 'checked' : '' }>
                                <label class="form-check-label" for="encryptionSwitch" id="isEncryption">ON</label>
                            </div>
                        </h5>
                    </div>
                    <div class="card-body" id="encryptionOptions">
                        <div class="container">
                            <div class="row">
                                <div class="col-sm-12 form-group">
                                    <label>Encrypted message <span id="encryptedMessageInfo" class="icon-info-sign"
                                                                   data-toggle="tooltip" data-placement="auto right"
                                                                   title="Message will be added into the encrypted pdf"></span></label>
                                    <textarea class="form-control" name="encryptedMessage" id="encryptedMessage"
                                              rows="5" placeholder="..."><c:out
                                            value="${ empty param.encryptedMessageEmail ? encryptedMessageEmail : param.encryptedMessageEmail }"/></textarea>
                                    <div class="error-message" id="encryptedMessageError"></div>
                                </div>
                            </div>
                            <div class="row mt-3 form-group">
                                <div class="col-sm-2">
                                    <label>Password</label>
                                </div>
                                <div class="col-sm-10">
                                    <input class="form-control" type="text" name="emailPDFPassword"
                                           id="emailPDFPassword" placeholder="YYYYMMDDHIN"
                                           value='${ not empty param.passwordEmail ? param.passwordEmail : emailPDFPassword }'
                                           autocomplete="off"/>
                                    <div class="error-message" id="emailPDFPasswordError"></div>
                                </div>
                            </div>
                            <div class="row mt-3 form-group">
                                <div class="col-sm-2">
                                    <label>Clue <span id="clueInfo" class="icon-info-sign" data-toggle="tooltip"
                                                      data-placement="auto right"
                                                      title="Clue will be added into the email body (visible)"></span></label>
                                </div>
                                <div class="col-sm-10">
                                    <textarea class="form-control" name="emailPDFPasswordClue" id="emailPDFPasswordClue"
                                              rows="2" placeholder="..."><c:out
                                            value="${ not empty param.passwordClueEmail ? param.passwordClueEmail : emailPDFPasswordClue }"/></textarea>
                                    <div class="error-message" id="emailPDFPasswordClueError"></div>
                                </div>
                            </div>
                            <div class="row mt-3 form-group">
                                <div class="col-sm-2">
                                    <label>Encrypt Attachments <span id="encryptAttachmentInfo" class="icon-info-sign"
                                                                     data-toggle="tooltip" data-placement="auto right"
                                                                     title="Email attachments will be encrypted when enabled"></span></label>
                                </div>
                                <div class="col-sm-10">
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
                            Additional options
                        </h5>
                    </div>
                    <div class="card-body">
                        <div class="container">
                            <div class="row">
                                <div class="col-sm-12">
                                    <label>Chart options</label>
                                    <div class="form-check">
                                        <div class="form-check-label">
											<input class="form-check-input" type="radio" name="patientChartOption" id="doNotAddAsNoteOption" value="doNotAddAsNote" onClick="toggleInternalTextArea()">
                                            <label class="form-check-label" for="doNotAddAsNoteOption">
                                                Do not add to patient chart
                                            </label>
                                        </div>
                                    </div>
                                    <div class="form-check">
                                        <div class="form-check-label">
											<input class="form-check-input" type="radio" name="patientChartOption" id="addFullNoteOption" value="addFullNote" checked onClick="toggleInternalTextArea()">
                                            <label class="form-check-label" for="addFullNoteOption">
                                                Chart as new note in patient's chart
                                            </label>
										</div>
										<div id="internalCommentContainer" class="d-none">
											<textarea class="form-control" id="internalComment" name="internalComment" placeholder="Internal comment to include" rows="3"><c:out value="${ not empty param.internalComment ? param.internalComment : internalComment }" /></textarea>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <div class="card mt-4">
                    <div class="card-header">
                        <h5 class="card-title">Attachments</h5>
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
                                                    <i class="icon-file attachmentIcon"></i> <span
                                                        class="attachmentName"><c:out
                                                        value="${emailAttachment.fileName}"/></span>
                                                    <span class="text-muted attachmentSize"><c:out
                                                            value="${emailAttachment.fileSize}"/></span>
                                                </button>
                                            </div>
                                            <div id="emailAttachmentBody${loop.index + 1}"
                                                 class="accordion-collapse collapse"
                                                 aria-labelledby="emailAttachmentHeader${loop.index + 1}"
                                                 data-bs-parent="#emailAttachmentList">
                                                <div class="accordion-body">
                                                    <object id="emailAttachmentPDF${loop.index + 1}"
                                                            data="${ctx}/previewDocs.do?method=renderPDF&pdfPath=${emailAttachment.filePath}"
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

                <div id="additionalParams" class="m-2 row hide">
                    <div class="col-sm-3">
                        <button type="button" class="btn btn-link text-decoration-none"
                                onclick="showAdditionalParamsTextBox()">Add Additional Parameters
                        </button>
                    </div>
                    <div class="col-sm-9">
                        <c:set var="emailAdditionalParams"
                               value="${not empty emailAdditionalParams ? emailAdditionalParams : ''}"/>
                        <input type="text" class="form-control ${ not empty emailAdditionalParams ? '' : 'hide' }"
                               name="additionalURLParams" id="additionalURLParams"
                               placeholder="Extra Parameters (if applicable)"
                               value="<c:out value='${emailAdditionalParams}' />">
                    </div>
                </div>

                <div class="container mt-4" id="form-control-buttons">
                    <div class="row">
                        <div class="col-sm-12">
                            <button type="submit" id="btnSend" class="btn btn-primary btn-md pull-right" value="Send">
                                <span class="btn-label"><i class="icon-location-arrow"></i></span>
                                Send
                            </button>
                            <button formnovalidate="formnovalidate" id="btnCancel"
                                    class="btn btn-danger btn-md pull-right" value="Cancel" name="close"
                                    onclick="cancelEmail()">
                                <span class="btn-label"><i class="icon-remove"></i></span>
                                Cancel
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
					<p>Your email to <b><c:out value="${fn:join(emailLog.toEmail, ', ')}" /></b> was successfully sent.</p>
                    </div>
				<p class="mt-1" id="windowCloseMessage">This window will close in <b>3</b> seconds...</p>
                </c:when>
                <c:otherwise>
                    <div class="alert alert-danger" role="alert">
                        <p> Your email to <b><c:out value="${fn:join(emailLog.toEmail, ', ')}"/></b> was NOT sent.
                            Please review the error message and try again.<br><br>
                            <b>Error Message:</b> <br>
                            <c:out value="${emailLog.errorMessage}"/></p>
                    </div>
                </c:otherwise>
            </c:choose>
            <input type="button" class="btn btn-danger btn-md pull-right" value="Close" onclick="window.close();"/>
        </c:if>
    </div>
</div>

<script type="text/javascript">
    document.addEventListener("DOMContentLoaded", function () {
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

        validateField(subjectEmail, 'Subject is required', errors, 'subjectError');
        validateField(bodyEmail, 'Body is required', errors, 'bodyError');
        if (isEncrypted) {
            if (hasEncryptedMessage) {
                validateField(emailPDFPassword, 'Password is required', errors, 'emailPDFPasswordError');
                validateField(emailPDFPasswordClue, 'Clue is required', errors, 'emailPDFPasswordClueError');
            } else if (hasAttachments && isAttachmentEncrypted) {
                validateField(emailPDFPassword, 'Password is required', errors, 'emailPDFPasswordError');
                validateField(emailPDFPasswordClue, 'Clue is required', errors, 'emailPDFPasswordClueError');
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
            errorMessage = 'Password must be at least 5 characters long.';
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
        document.getElementById("encryptionOptions").classList.toggle('hide', !checkbox.checked);
        document.getElementById("isEmailEncrypted").value = checkbox.checked ? "true" : "false";
        document.getElementById("isEncryption").innerHTML = checkbox.checked ? "ON" : "OFF";
        document.getElementById("isEncryption").classList.toggle("off", !checkbox.checked);
    }

    function toggleEncryptAttachmentStatus(checkbox) {
        document.getElementById("isEmailAttachmentEncrypted").value = checkbox.checked ? "true" : "false";
    }

    function removeReceiverEmail(button) {
        let receiverEmailsContainer = document.getElementById("receiverEmailsContainer");
        let formGroup = button.closest('.form-group');
        if (receiverEmailsContainer.children.length > 1) {
            receiverEmailsContainer.removeChild(formGroup);
        } else {
            alert("This recipient cannot be removed because a minimum of one recipient is required");
        }
    }

    // Open EForm again on sent
    function openEFormAfterSend() {
        const isOpenEForm = "${isOpenEForm}" === "true";
        if (isOpenEForm) {
            window.open("${ctx}/eform/efmshowform_data.jsp?fdid=${fdid}", "_blank", "width=800,height=600");
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
        window.open("${ctx}/demographic/demographiccontrol.jsp?demographic_no=${demographicId}&displaymode=edit&dboperation=search_detail", "_blank", "width=1027,height=700");
    }

    function cancelEmail() {
        const transactionType = document.getElementById("transactionType").value;
        if (transactionType === 'DIRECT') {
            window.close();
        }
        const emailComposeForm = document.getElementById("emailComposeForm");
        emailComposeForm.action = "${ctx}/email/emailSendAction.do?method=cancel";
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
            document.getElementById('additionalParams').classList.remove('hide');
        } else {
            document.getElementById('additionalParams').classList.add('hide');
        }
    }

    function showAdditionalParamsTextBox() {
        document.getElementById('additionalURLParams').classList.toggle('hide');
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
