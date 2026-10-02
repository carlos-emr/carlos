document.addEventListener("DOMContentLoaded", function(){


    /**
     * Trigger these functions every time this page loads.
     */
    hideElements();
    addNavElement();
    disableTextareaResize();
    moveSubjectReverse();
    hideAdminPreviewSaveButton();

    // Add eForm attachments
    addEFormAttachments();

    // If download EForm
    const isDownload = document.getElementById("isDownloadEForm") ? document.getElementById("isDownloadEForm").value : "false";
    if (isDownload && isDownload === "true") {
        downloadEForm();
        showRenderAdvisory();
    }

    // Handle EForm errors
    const error = document.getElementById("error") ? document.getElementById("error").value : "false";
    const errorMessage = document.getElementById("errorMessage") ? document.getElementById("errorMessage").value : "";
    if (error === "true") {
        showError(errorMessage);
    }

		// add listener to the subject element
		if(document.forms[0].elements["subject"]) {
			document.forms[0].elements["subject"].addEventListener("input", moveSubjectReverse);
			document.forms[0].elements["subject"].addEventListener("click", moveSubjectReverse);
		}

	const isSuccessAndAutoclose = document.getElementById("isSuccess_Autoclose") &&
		document.getElementById("isSuccess_Autoclose").value === 'true';
    const warningMessage = document.getElementById("warningMessage") ? document.getElementById("warningMessage").value : "";
    if (warningMessage) {
        showWarningAlert(warningMessage.replaceAll(String.raw`\n`, "\n"), isSuccessAndAutoclose ? remoteClose : undefined);
    } else if (isSuccessAndAutoclose) {
		showSuccessAlert(remoteClose);
	}
	});

window.onerror = function uncaughtExceptionHandler(message, source, lineNumber, colno, error) {
    // return alert('This eForm contains source code errors that will cause a failure of functionality or loss of data.\n\n' +
    // 	'Please go to OSCARGalaxy.org for an updated version of this eForm, or  if a new version is not available, contact info@oscarbc.ca to request a repair.\n\n' +
    // 	'E-forms are a community project managed by OSCAR BC; eForm collections are hosted on OSCAR Galaxy for download and import.\n\n' +
    // 	'Error Message:' + message);
    let eform = {};
    eform.formId = document.getElementById("fid").value;
    eform.error = message;
    let context = document.getElementById("context").value;
    jQuery.post(context + "/eform/logEformError", eform);
}

/**
 * True when this eForm is open without a patient: the eForm manager's preview, which
 * efmshowform_data.jsp renders with demographic "-1" precisely because it cannot be submitted.
 * There is no chart to save into, so Print must not offer or attempt a chart save.
 */
function isAdminPreview() {
    const demographicNo = document.getElementById("demographicNo");
    return demographicNo?.value === "-1";
}

function hideAdminPreviewSaveButton() {
    if (!isAdminPreview()) {
        return;
    }

    const savePdfButton = document.getElementById("remoteSavePdfButton");
    if (savePdfButton) savePdfButton.hidden = true;
    const remoteSubmitButton = document.getElementById("remoteSubmitButton");
    if (remoteSubmitButton) {
        remoteSubmitButton.style.display = "none";
    }
}

function getEForm() {
	let ef = document.forms['saveEForm'];
	if (!ef) {
		ef = Array.prototype.find.call(
			document.forms,
			f => f.action && f.action.includes('addEForm')
		);
	}
	return ef;
}

/*
 * The server adds a hidden newForm=true fallback (data-carlos-newform-fallback) when the template
 * has no control that always submits newForm. A template's own submit button named newForm posts
 * its value only when it is the submitter; the toolbar's form.submit() has no submitter, so the
 * fallback must stay for those paths. When that button itself submits the form, leave the fallback
 * out so newForm is posted exactly once, with the button's value. The form data set is built
 * synchronously right after the submit event, so the fallback is re-enabled on the next task: a
 * submission another listener cancels cannot leave it disabled for a later toolbar save.
 * Capture phase, so it runs whatever template handlers do with the event afterwards.
 */
document.addEventListener("submit", function (event) {
    const submitter = event.submitter;
    // A disabled submitter (e.g. a template onclick that disables the button and then calls
    // requestSubmit, or a button in a disabled fieldset) contributes no value, so the fallback
    // must stay.
    if (!submitter || submitter.name !== "newForm" || submitter.type === "image"
            || submitter.matches(":disabled")) {
        return;
    }
    const fallbacks = Array.from(event.target.querySelectorAll("input[data-carlos-newform-fallback]"))
        .filter(input => !input.disabled);
    fallbacks.forEach(input => { input.disabled = true; });
    setTimeout(() => fallbacks.forEach(input => { input.disabled = false; }), 0);
}, true);

function submitEForm() {
	const ef = getEForm();
	if (!ef) {
		showErrorAlert();
		return false;
	}
	ef.submit();
	return true;
}

let editorLoadingBlockCount = 0;

/**
 * True when the Rich Text Letter editor is still initializing: its template dropdown still shows the
 * legacy " loading... " placeholder. Saving now would serialize the half-built editor and persist a
 * broken snapshot that renders as an empty "loading" page forever, so every save/download/fax/email
 * entry point checks this BEFORE showing a (locked) spinner or appending action inputs — otherwise
 * an abort would leave an undismissable overlay and stale hidden inputs behind. After a few
 * consecutive blocks the editor is probably broken (a failed template fetch never leaves the
 * placeholder), so escalate the message and post a marker to the server rather than telling the
 * clinician to "wait" forever. The (visible) alert is raised here so callers stay simple.
 */
function editorStillLoading() {
    if (typeof window.cancelPendingFaxSubmission === 'function') { window.cancelPendingFaxSubmission(); }
    // Measurement loads must settle before any save/download/fax workflow flags or spinner.
    if (typeof window.measurementHistoryStillLoading === 'function' && window.measurementHistoryStillLoading()) {
        if (typeof window.cancelLetterOutput === 'function') { window.cancelLetterOutput(); }
        window.needToConfirm = true;
        alert('Measurements are still loading. Please wait before saving or printing this letter.');
        return true;
    }
	// Scoped to the editor's OWN template dropdown (#template, created by editControl2.js and
	// repopulated when efmformrtl_templates returns). The previous query was every `select option`
	// in the document, and the "loading..." literal appears nowhere in CARLOS-shipped code — it can
	// only come from a stored eForm's markup. So any third-party form that happened to ship an
	// option with that exact text blocked Save, Download, Fax, Email and Add-to-Documents outright,
	// with no timeout, and the escalation below told the clinician to reopen the letter, which
	// reproduced it.
	const templateSelect = document.getElementById('template');
	const stillLoading = templateSelect !== null
		&& Array.from(templateSelect.options)
			.some((option) => option.textContent.trim() === 'loading...');
	if (!stillLoading) {
		editorLoadingBlockCount = 0;
		return false;
	}
	editorLoadingBlockCount += 1;
	if (editorLoadingBlockCount >= 3) {
		try {
			const contextEl = document.getElementById('context');
			const fidEl = document.getElementById('fid');
			if (contextEl && fidEl) {
				jQuery.post(contextEl.value + '/eform/logEformError',
					{ formId: fidEl.value, error: 'RTL editor never left loading state; save blocked' });
			}
		} catch (e) {
			// best-effort telemetry only; never let it block the guard
		}
		// After three blocks the editor is not going to finish (a failed template fetch never
		// leaves the placeholder), so the choice is the clinician's: refusing forever loses whatever
		// they have typed, which is its own kind of data loss. The risk is named rather than implied.
		return !confirm('The letter editor did not finish loading. Saving now may store an incomplete '
			+ 'letter that reopens as a blank "loading" page.\n\nClick OK to save anyway, or Cancel to '
			+ 'close and reopen the letter.');
	}
	alert('The letter editor is still loading. Please wait a moment and try again.');
	return true;
}

/**
 * True when the eForm declares HTML5 constraints that are not satisfied, in which case the save
 * cannot proceed and the reason has been shown to the user.
 *
 * <p>Why this guard has to exist: {@link remoteSave} submits through the eForm's own
 * {@code <input type="submit" name="SubmitButton">} when the form declares one, and clicking a
 * native submit button runs constraint validation. A form with an unsatisfied {@code required}
 * field therefore never posts — but the click throws nothing, so remoteSave used to report success
 * and the composite callers carried on. Since remoteDownload/remoteFax/remoteEmail/saveAsEdoc show
 * a LOCKED spinner and set their workflow flag BEFORE saving, the result was an undismissable
 * overlay over a form that was never saved: the same hazard the editorStillLoading() guard above
 * already defends against. Observed on a real clinic form whose Past Medical History field is
 * marked required.</p>
 *
 * <p>The check is deliberately applied to every save path, not only the native-button one. The
 * other paths reach the server through HTMLFormElement.submit(), which bypasses constraint
 * validation entirely — so before this, whether a form author's {@code required} was enforced at
 * all depended on the incidental detail of whether their form declared a submit button. Two
 * different validation semantics for the same action is the underlying defect; this makes the
 * stricter, author-intended one uniform.</p>
 *
 * <p>Cleanup here (unlike the editorStillLoading guard) must also clear the workflow flags: those
 * callers check editorStillLoading BEFORE setting their flag, but the form's validity cannot be
 * known until the save is actually attempted, so by this point the flag is already on the form and
 * a later plain Save would otherwise ride it into a download/fax/email.</p>
 */
function eFormValidationBlocked() {
	// Input types the readonly attribute applies to (HTML spec); it is ignored on all others.
	// Function-scoped on purpose: this is a classic script sharing the global lexical scope with
	// eForm template scripts, so a top-level const could collide with a template's own name.
	const READONLY_INPUT_TYPES = ["text", "search", "url", "tel", "email", "password", "date", "month",
		"week", "time", "datetime-local", "number"];
	const ef = getEForm();
	// moveSubjectReverse() turns the template's subject input into type="hidden", which the browser
	// excludes from constraint validation, and the toolbar's own subject lives in a separate form
	// that is never submitted. Enforce the template's required subject on the field the clinician
	// actually edits, so hiding the original does not silently drop the author's constraint.
	const templateSubject = ef && ef.elements ? ef.elements["subject"] : null;
	const toolbarSubject = document.getElementById("remote_eform_subject");
	// A disabled template subject (directly or through a disabled fieldset) is barred from
	// native constraint validation, so its requirement must not carry over either.
	// A readonly textarea, or a readonly input of a type readonly applies to, is barred as well;
	// on other input types (checkbox, radio, file, range, color...) readonly is ignored, so their
	// requirement still holds. moveSubjectReverse() has since made the input type="hidden", so
	// read the template's own type it recorded. (:read-only is broader than the attribute, so
	// check the property.)
	const templateSubjectReadOnly = !!templateSubject && templateSubject.readOnly === true
		&& (templateSubject.tagName === "TEXTAREA" || (templateSubject.tagName === "INPUT"
			&& READONLY_INPUT_TYPES.includes(templateSubject.dataset.carlosOriginalType
				|| (templateSubject.getAttribute("type") || "text").toLowerCase())));
	const templateSubjectDisabled = !!templateSubject && ((typeof templateSubject.matches === "function"
		&& templateSubject.matches(":disabled")) || templateSubjectReadOnly);
	if (templateSubject && templateSubject.required === true && !templateSubjectDisabled && toolbarSubject
			&& typeof toolbarSubject.checkValidity === "function") {
		toolbarSubject.required = true;
		if (!toolbarSubject.checkValidity()) {
			toolbarSubject.reportValidity();
			HideSpin();
			clearWorkflowFlags();
			return true;
		}
	}
	// No resolvable form, or a browser/form without the constraint API: nothing can be asserted, so
	// never block on it — the pre-existing submit paths stay exactly as they were.
	if (!ef || typeof ef.checkValidity !== "function" || ef.checkValidity()) {
		return false;
	}
	// reportValidity focuses the offending control and shows the browser's own message, which names
	// the field. Do not substitute a generic alert: the clinician needs to know WHICH field.
	if (typeof ef.reportValidity === "function") {
		ef.reportValidity();
	}
	HideSpin();
	clearWorkflowFlags();
	return true;
}

	/**
	 * Triggers the eForm save/submit function
	 */
function remoteSave() {

	try {
		// Last line of defense for direct callers (the plain Save button): composite callers
		// (remoteDownload/remoteFax/remoteEmail) check editorStillLoading() BEFORE their own
		// spinner/input mutations, so by the time they reach here the check is already clear. Hide any
		// spinner a caller may have shown and abort with the function's boolean contract.
		if (editorStillLoading()) {
			HideSpin();
			return false;
		}

		// A legacy string timer that never ran can leave fields unpopulated. That is ADVISORY, not a
		// hard stop: the server render delivers the document with its own advisory banner rather than
		// withholding it, so blocking here would trap the clinician on the client for a document the
		// server would have produced. Surface the shim's warning banner and proceed. The shim's own
		// capture-phase submit listener never fires for these paths, which submit via
		// HTMLFormElement.submit() (no submit event), so consult it directly for the notice.
		const timerCompat = window.__carlosEformTimerCompat;
		if (timerCompat && typeof timerCompat.warnBeforeSubmission === "function") {
			timerCompat.warnBeforeSubmission();
		}

		// Must run before appendImageInputs()/moveSubject() below mutate the form, and before the
		// submit-bound spinner is armed: an abort after those leaves the toolbar's inputs on a form
		// the user is still editing.
		if (eFormValidationBlocked()) {
			return false;
		}

		// bind the spinner to the form submit event.
		jQuery('form').on('submit', function(e) {
			ShowSpin(true);
		});

		appendImageInputs();

		moveSubject();

		if (typeof saveRTL === "function") {
			window["saveRTL"]();
			document.RichTextLetter.submit();
			return true;
		}

		if (document.getElementsByName("SubmitButton") && document.getElementsByName("SubmitButton")[0]) {
			try {
				document.getElementsByName("SubmitButton")[0].click();
				return true;
			} catch (error) {
				showErrorAlert();
			}
		}

		if(typeof releaseDirtyFlag === "function")
		{
			window["releaseDirtyFlag"]();
		}

		if (typeof submission === "function") {
			try {
				window["submission"]();
				return submitEForm();
			} catch (e) {
				showErrorAlert();
			}
		}

		try {
			return submitEForm();
		} catch (e) {
			showErrorAlert();
		}

		HideSpin();
	} catch (e) {
		showErrorAlert();
	}

	return false;
}

/**
 * Triggers the eForm attach function
 */
jQuery(document).on('click', '*[data-poload]', function () {
    const demographicNo = document.getElementById("demographicNo").value;
    const fdid = document.getElementById("fdid").value;
    const context = document.getElementById("context").value;
    let trigger = jQuery(this);
    trigger.data('poload', context + '/previewDocs?method=fetchEFormDocuments&demographicNo=' + demographicNo + '&fdid=' + fdid);
    trigger.off('click');
    let title = trigger.attr("title");
    let pickerLoaded = false;
    jQuery("#attachDocumentDisplay").load(trigger.data('poload'), function (response, status, xhr) {
        if (status === "success" && jQuery('#attachDocumentsForm').length) {
            pickerLoaded = true;
            // Disable the floating toolbar when the attachment window opens
            const eformFloatingToolbar = document.getElementById("eform_floating_toolbar");
            eformFloatingToolbar.classList.add("disabled-toolbar");

            jQuery('#attachDocumentList').find(".delegateAttachment").each(function (index, data) {
                let delegate = "#" + this.id.split("_")[1];
                let element = jQuery('#attachDocumentsForm').find(delegate);
                if (element.length === 0) {
                    if (this.name === 'formNo' && document.getElementById('entry_formNo' + this.value)) {
                        element = addFormIfNotFound(data, demographicNo, delegate);
                    } else {
                        // Unlisted/restricted attachments cannot disappear merely because the
                        // picker does not offer them. Show an explicit removal choice instead.
                        element = jQuery('<input>', {
                            type: 'checkbox', name: this.name, value: this.value,
                            id: this.id.substring('delegate_'.length), class: 'unlisted_attachment_check'
                        });
                        const label = jQuery('<label>').append(element).append(
                            document.createTextNode(' Existing attachment unavailable in this list; uncheck to remove.'));
                        jQuery('#attachDocumentsForm').append(jQuery('<div>').append(label));
                    }
                }
                element.attr("checked", true);

                // Expand list if selected lab is older version
                if (element.attr('data-version')) {
                    expandLabVersionList(element.parent().parent().parent().find('.collapse-arrow'));
                }
            });
        }
    }).dialog({
        title: title,
        modal: true,
        closeText: "Save and Close",
        height: 'auto',
        width: 'auto',
        resizable: true,
        open: function (event, ui) {
            jQuery(this).parent().css({
                top: 0,
                left: 0
            });

            let closeBtn = jQuery(this).parent().find(".ui-dialog-titlebar-close");
            closeBtn.removeClass("ui-button-icon-only");
            closeBtn.addClass("save-and-close-button");
            closeBtn.html("Save and Close");
        },

        beforeClose: function (event, ui) {
            if (!pickerLoaded) return;
            // before the dialog is closed:

            // check if list exists, if yes then empty it otherwise create new
            if (jQuery('#attachDocumentList').length === 0) {
                const attachDocumentList = jQuery('<div>', {'id': 'attachDocumentList'});
                jQuery('form:first').append(attachDocumentList);
            }
            // Build and validate every delegate before touching the stored list: a selection
            // that eformAttachmentSubmissionValue refuses (a lab checkbox without a valid
            // source) must not leave the list half replaced or empty, or the next save would
            // silently drop the attachments the form already had.
            let inputs;
            try {
                inputs = jQuery('#attachDocumentsForm').find(".document_check:checked:not(input[disabled='disabled']), .lab_check:checked:not(input[disabled='disabled']), .form_check:checked:not(input[disabled='disabled']), .eForm_check:checked:not(input[disabled='disabled']), .hrm_check:checked:not(input[disabled='disabled']), .unlisted_attachment_check:checked"
                ).map(function () {
                    const element = jQuery(this);
                    return jQuery("<input />", {
                        type: 'hidden',
                        name: element.attr('name'),
                        value: eformAttachmentSubmissionValue(element),
                        id: "delegate_" + element.attr('id'),
                        class: 'delegateAttachment'
                    })[0];
                }).get();
            } catch (selectionError) {
                alert('An attachment selection is invalid. Please reselect it.');
                return false;
            }

            // pass the checked documents to the eForm document list(attachDocumentList)
            jQuery('#attachDocumentList').empty().append(inputs);

            // show total attachments
            jQuery('#remoteTotalAttachments').empty().append(jQuery('.delegateAttachment').length);

            // Enable the floating toolbar when the attachment window closes
            const eformFloatingToolbar = document.getElementById("eform_floating_toolbar");
            eformFloatingToolbar.classList.remove("disabled-toolbar");
        }
    });
});

/** Preserve the lab source when transferring a picker selection to the saved form. */
function eformAttachmentSubmissionValue(element) {
    const value = element.val();
    if (element.attr('name') !== 'labNo' || element.hasClass('unlisted_attachment_check')) return value;
    const source = element.attr('data-lab-type');
    if (!/^(HL7|MDS|CML|BCP)$/.test(source || '') || !/^[1-9][0-9]*$/.test(value)) {
        throw new Error('Invalid lab attachment selection');
    }
    return source + ':' + value;
}

/**
 * This function adds the old form to the attachment window only if that form is displayed in the consultForm/eForm attachments.
 * The attachment window only displays the latest (updated) forms.
 */
function addFormIfNotFound(form, demographicNo, delegate) {
    const checkboxName = form.getAttribute('name');
    const formValue = form.getAttribute('value');
    const formId = "formNo" + formValue;
    const formName = document.getElementById("entry_" + formId).getAttribute('data-formName');
    const formDate = document.getElementById("entry_" + formId).getAttribute('data-formDate');

    const checkbox = jQuery('<input>', {
        class: 'form_check',
        type: 'checkbox',
        name: checkboxName,
        id: formId,
        value: formValue,
        title: formName
    });

    const label = jQuery('<label>', {
        for: formId,
        text: "(Not Latest Version) " + formName + " " + formDate
    });

    const previewButton = jQuery('<button>', {
        class: 'preview-button',
        type: 'button',
        text: 'Preview',
        title: 'Preview'
    }).click(function () {
        const formPreviewParameters = 'method=renderFormPDF'
            + '&formId=' + encodeURIComponent(formValue)
            + '&formName=' + encodeURIComponent(formName)
            + '&demographicNo=' + encodeURIComponent(demographicNo);
        getPdf('FORM', formValue, formPreviewParameters);
    });

    const newLiFormElement = jQuery('<li>', {
        class: 'form',
    }).append(checkbox).append(label).append(previewButton);
    jQuery('#formList').find('.selectAllHeading').after(newLiFormElement);

    return jQuery('#attachDocumentsForm').find(delegate);
}

function addEFormAttachments() {
    const eFormAttachments = jQuery('.delegateAttachment');
    const attachDocumentList = jQuery('<div>', {'id': 'attachDocumentList'});
    jQuery('form:first').append(attachDocumentList);
    eFormAttachments.appendTo(attachDocumentList);

    // Old form versions
    const oldVersionForms = jQuery('.delegateOldFormAttachment');
    const eForm = jQuery('#FormName');
    oldVersionForms.appendTo(eForm);
}

/**
 * Adds a hidden input field into the eForm form with instructions to
 * open 'Save as' window dialog
 */
function remoteDownload() {
    // Check BEFORE ShowSpin(true) (a locked overlay) and before appending the action input: if the
    // editor is still loading, aborting after either would strand an undismissable spinner and a
    // stale saveAndDownloadEForm=true that a later plain Save would silently ride into a download.
    if (editorStillLoading()) {
        return;
    }
    clearWorkflowFlags();
    ShowSpin(true);
    setHiddenFormInput("saveAndDownloadEForm", "saveAndDownloadEForm", "true");

    remoteSave();
}

/**
 * Tells the reader that the render reported a condition that did not withhold the document.
 *
 * Advisory conditions - an uncaught error in the form's own script, a dialog the renderer had to
 * suppress, a legacy timer that threw - now deliver the PDF instead of blocking it. Without this the
 * clinician receives a possibly-truncated document with no indication anything happened; on the
 * preview path they already get an equivalent banner.
 *
 * A count only, rendered with textContent: console and dialog text is form-authored and can carry
 * PHI, and this page renders clinical documents so it must never become an HTML sink.
 */
function showRenderAdvisory() {
    const field = document.getElementById("advisoryIssues");
    const count = field ? Number(field.value) : 0;
    if (!count || count < 1) {
        return;
    }
    const notice = document.createElement("div");
    notice.id = "carlos-render-advisory";
    notice.setAttribute("role", "status");
    notice.style.cssText = "position:fixed;z-index:2147483646;top:0;left:0;right:0;padding:10px;"
            + "background:#fff3cd;color:#664d03;border-bottom:1px solid #ffc107;"
            + "font:14px sans-serif;text-align:center";
    notice.textContent = "This form reported " + count
            + (count === 1 ? " issue" : " issues")
            + " while rendering. The downloaded PDF may be missing content - check it against the form.";
    document.body.insertBefore(notice, document.body.firstChild);
}

function downloadEForm() {
    const eFormPDF = document.getElementById("eFormPDF").value;
    const eFormPDFName = document.getElementById("eFormPDFName").value;
    if (!eFormPDF && !eFormPDFName) {
        return;
    }
    const pdfData = new Uint8Array(atob(eFormPDF).split('').map(char => char.charCodeAt(0)));
    const pdfBlob = new Blob([pdfData], {type: 'application/pdf'});
    const downloadLink = document.createElement('a');
    downloadLink.href = URL.createObjectURL(pdfBlob);
    downloadLink.download = eFormPDFName;
    downloadLink.click();
    URL.revokeObjectURL(downloadLink.href);
    document.getElementById("eFormPDF").value = "";
    document.getElementById("eFormPDFName").value = "";
}

/**
 * Print the live page, including unsaved edits. The browser's print dialog offers Save as PDF.
 * Call the browser directly: template PrintButton/formPrint handlers may also submit the form.
 * Rich Text Letters keep the actual printable document in their editor frame.
 */
function remotePrintOnly() {
    if (editorStillLoading()) return;
    const options = document.getElementById('remotePrintOptions');
    if (options) options.open = false;
    const editor = document.getElementById('Letter') && document.getElementById('edit');
    const printWindow = editor?.contentWindow || window;
    printWindow.focus();
    printWindow.print();
}

/**
 * Adds a hidden input field into the eForm form with instructions to
 * open the Oscar Fax dialog.
 */
function remoteFax() {
    // Check before appending any action input: aborting the save after appending faxEForm=true (and
    // stale recipient values) would leave them on the form for a later plain Save to ride into the
    // fax workflow unexpectedly.
    if (editorStillLoading()) {
        return;
    }
    clearWorkflowFlags();
    // Resolve the recipient before declaring the fax intent, so a failure here cannot leave
    // faxEForm=true on the form for a later plain Save to ride into the fax workflow.
    const chosen = selectedEformFaxRecipient();
    setHiddenFormInput("faxAction", "faxEForm", "true");
    // Include empty overrides too: clearing a recipient must not resurrect a template's old number.
    setHiddenFormInput("recipient", "recipient", chosen.name);
    setHiddenFormInput("recipientFaxNumber", "recipientFaxNumber", chosen.fax);

    remoteSave();
}

/**
 * Sets (creating once, then reusing by id) a hidden input on the primary form. Reuse-by-id keeps
 * repeated aborted/retried actions from accumulating duplicate id/name inputs (form encoding takes
 * the first, so a stale duplicate could otherwise win over a fresh value).
 */
function setHiddenFormInput(id, name, value) {
    // Scoped to inputs the toolbar itself created. A bare getElementById(id) matched the eForm's own
    // markup too — eForms are third-party HTML and a referral form with its own visible "recipient"
    // field is entirely plausible — so the clinician's typed value was overwritten on Fax. The
    // removal side below was already guarded by this marker, which meant clearWorkflowFlags() then
    // refused to remove the hijacked field and the overwritten value survived into the save.
    let input = document.querySelector(
        '[data-carlos-workflow-flag][id="' + CSS.escape(id) + '"]');
    if (!input) {
        input = document.createElement("input");
        input.setAttribute("id", id);
        input.setAttribute("name", name);
        input.setAttribute("type", "hidden");
        // Ownership marker. eForms are third-party HTML and may legitimately carry their own
        // visible inputs with these ids (a referral form with its own "recipient" field is entirely
        // plausible), so clearWorkflowFlags() must remove only the nodes the toolbar itself created.
        // Removing by bare id deleted the clinician's field and silently dropped its value.
        input.dataset.carlosWorkflowFlag = "true";
        document.forms[0].prepend(input);
    }
    input.setAttribute("value", value);
    input.value = value;
}

/**
 * Removes every workflow-intent hidden input (and fax recipient inputs). Each composite action
 * (download/fax/email/edocument) calls this before setting its own flag, so a flag left behind by a
 * previously prevented/aborted attempt cannot ride a later action into the wrong server-side
 * workflow (e.g. a stale faxEForm=true making a later Save enter the fax path).
 */
function clearWorkflowFlags() {
    if (typeof window.cancelPendingFaxSubmission === 'function') { window.cancelPendingFaxSubmission(); }
    // Scoped to toolbar-created nodes only (see setHiddenFormInput). Never select by bare id: the
    // surrounding eForm is author-supplied HTML and may own an element of the same name.
    document.querySelectorAll('[data-carlos-workflow-flag]').forEach(function (el) {
        if (el.parentNode) {
            el.parentNode.removeChild(el);
        }
    });
}

/**
 * Adds a hidden input field into the eForm form with instructions to
 * open the Oscar Email dialog.
 */
function remoteEmail() {
    // Reject a pending letter before asking for consent or changing workflow intent.
    if (editorStillLoading()) {
        return;
    }
    if (!document.getElementById("hasValidRecipient") || !document.getElementById("emailConsentStatus") || !document.getElementById("emailConsentName")) {
        alert("Valid recipient or consent parameter is not defined in the EForm.");
        return;
    }

    const hasValidRecipient = document.getElementById("hasValidRecipient").value;
    const emailConsentStatus = document.getElementById("emailConsentStatus").value;
    const emailConsentName = document.getElementById("emailConsentName").value;

    if (hasValidRecipient === "false") {
        alert("Sorry - this patient does not have a valid email address in their demographic. Please update their demographic and try again.");
        return;
    }

    if (emailConsentStatus !== "Explicit Opt-In") {
        const userResponse = prompt("This patient has not explicitly opted-in: [" + emailConsentName + "]\nType 'Yes' to acknowledge you understand the risks before proceeding.", "No");
        if (userResponse === null || userResponse.toLowerCase() !== 'yes') {
            return;
        }
    }

    clearWorkflowFlags();
    setHiddenFormInput("emailAction", "emailEForm", "true");
    remoteSave();

}

/**
 * Triggers the eForm print function
 */
/**
 * Clears any workflow intent left on the form, then saves.
 *
 * Bound to the plain Save button. remoteSave() itself must NOT do this: the composite actions
 * (remoteDownload/remoteFax/remoteEmail/remoteEdocument) clear and then set their own flag before
 * calling it, so clearing inside remoteSave would erase the intent they just declared.
 *
 * Without it, a cancelled composite leaves its inputs behind. A form-authored onsubmit that returns
 * false cancels the POST, but remoteSave() still reports success, so faxEForm/recipient/
 * recipientFaxNumber survive on the form — and AddEForm2Action reads those parameters verbatim, so
 * the clinician's next plain Save would take the fax branch with the earlier recipient.
 */
function remoteSaveOnly() {
    // The manager preview has no patient to save into (demographic -1); refuse even when the
    // Save button is reached before the toolbar guard hid it (#3904).
    if (isAdminPreview()) {
        return false;
    }
    clearWorkflowFlags();
    return remoteSave();
}

function remotePrint() {
    if (editorStillLoading()) {
        return;
    }
    // Same reason as remoteSaveOnly above: Print saves, and must not inherit a cancelled Fax's intent.
    clearWorkflowFlags();

    if (typeof formPrint === "function") {
        try {
            console.log("Printing document remotely with formPrint method");
            formPrint();
        } catch (e) {
            console.log("Eform returns fatal error while using formPrint function " + e);
            hailMary();
        }
    } else if (typeof printLetter === "function") {
        try {
            console.log("Printing document remotely with printLetter method")
            printLetter();
        } catch (e) {
            console.log("Eform returns fatal error while using printLetter function " + e);
            hailMary();
        }
    } else if (document.getElementsByName("PrintButton") && document.getElementsByName("PrintButton")[0]) {
        try {
            console.log(document.getElementsByName("PrintButton"));
            console.log("Remotely clicking button with name PrintButton");
            document.getElementsByName("PrintButton")[0].click();
        } catch (e) {
            console.log("Error locating PrintButton " + e);
            hailMary();
        }
    } else if (document.getElementById('edit')) {
        try {
            console.log("Content has been edited and no print method was found. Executing window.print");
            document.getElementById('edit').contentWindow.print();
        } catch (e) {
            console.log("Error locating PrintButton " + e);
            hailMary();
        }
    } else {
        hailMary()
    }

    // The save follows the print, as it always has: remoteSave() submits the form and navigates this
    // window away, so it must not run before the page has been handed to the printer.
    // The manager preview has no patient, so Print only prints there. Before #3901 an edited
    // preview, or one without dirty detection, still reached remoteSave() and posted a
    // patientless form; the new prompt would otherwise also offer a meaningless chart save.
    if (isAdminPreview()) {
        return;
    }
    saveAfterPrint(typeof needToConfirm === 'undefined' ? undefined : needToConfirm);
}

/**
 * Decides what Print does about saving the eForm to the eChart, given the eForm's dirty flag.
 *
 * - "save": the form was edited (flag truthy), or it has no dirty-form detection at all (flag
 *   undefined). Both keep their long-standing behaviour of saving on every print.
 * - "confirm": the form has dirty detection and it reports no manual edit. This used to skip the
 *   save silently, so a printed form was missing from the chart with no hint to the provider
 *   (issue #3901). Clinicians routinely print forms whose content is entirely pre-filled from the
 *   chart, so "not edited" does not mean "nothing worth keeping"; the provider decides.
 *
 * Adapted from MagentaHealth/Open-O b5dca89b7a, which also prompts for forms without dirty
 * detection. CARLOS keeps saving those unconditionally: without a dirty flag the toolbar cannot
 * tell an untouched form from an edited one, and prompting there would let a Cancel drop edits.
 *
 * @param {*} dirtyFlag the eForm's global needToConfirm, or undefined when the form declares none
 * @returns {"save"|"confirm"}
 */
function printSaveDecision(dirtyFlag) {
    if (typeof dirtyFlag === 'undefined' || dirtyFlag) {
        return "save";
    }
    return "confirm";
}

/**
 * Localized text for the "save the unedited form?" prompt, rendered by the server onto the
 * toolbar fragment's root element. English fallback for when the fragment did not load.
 */
function printSaveUneditedConfirmMessage() {
    const toolbar = document.getElementById("eform_floating_toolbar");
    const message = toolbar ? toolbar.getAttribute("data-print-save-unedited-confirm") : null;
    return message || "You haven't manually edited this eForm. Would you like to save a copy to the patient's chart anyway?";
}

/**
 * Applies {@link printSaveDecision} once per Print click. The form has already been printed by the
 * time this runs, so Cancel only declines the chart copy; it never withdraws the printout.
 *
 * @param {*} dirtyFlag see printSaveDecision
 * @returns {boolean} true when a save was attempted and remoteSave() reported success
 */
function saveAfterPrint(dirtyFlag) {
    if (printSaveDecision(dirtyFlag) === "confirm" && !confirm(printSaveUneditedConfirmMessage())) {
        return false;
    }
    return remoteSave();
}

function hailMary() {
    console.log("Just do window print.")
    try {
        window.print();
    } catch {
        alert("Cannot print. Try the print button on the eForm.");
    }
}

/**
 * Adds a hidden input field into the eForm form with instructions to
 * to generate a PDF of this form and then to
 * save it into the eChart Documents directory.
 */
function remoteEdocument() {
    // Check BEFORE clearing/appending the action input, matching remoteDownload/remoteFax/remoteEmail:
    // aborting after setting saveAsEdoc=true would strand that flag on the form, and a later plain
    // Save would silently ride it into the save-as-eDoc workflow.
    if (editorStillLoading()) {
        return;
    }
    clearWorkflowFlags();
    setHiddenFormInput("saveAsEdoc", "saveAsEdoc", "true");

    remoteSave();

}

/**
 * Close the entire eForm window.
 */
function remoteClose() {
    window.close();
}

/**
 * Move the eForm subject value from the remote tool bar into the
 * eForm form.
 * Should be done just before the save process.
 */
function moveSubject() {
    let remoteSubject = document.getElementById("remote_eform_subject");
    let remoteSubjectValue;

    if (remoteSubject) {
        remoteSubjectValue = remoteSubject.value;
    }

    let localSubject = document.forms[0].elements["subject"];
    if (localSubject && remoteSubject) {
        localSubject.value = remoteSubjectValue;
    }
}

function moveSubjectReverse() {
    let subjectElement = document.forms[0].elements["subject"];
    let subjectElementValue = "";

    if (subjectElement) {
        subjectElementValue = subjectElement.value;
    } else // create the subject element for later
    {
        subjectElement = document.createElement("input");
        subjectElement.id = "subject";
        subjectElement.name = "subject";
        subjectElement.type = "hidden";
        document.forms[0].appendChild(subjectElement);
    }

    // Keep the original field as the submitted value and as a target for template scripts.
    if (subjectElement.labels) {
        Array.from(subjectElement.labels).forEach(label => { label.hidden = true; });
    }
    // Many Galaxy forms use a bare text node instead of a label element.
    const caption = subjectElement.previousSibling;
    if (caption?.nodeType === Node.TEXT_NODE) {
        caption.textContent = caption.textContent.replace(/\bSubject:\s*$/i, '');
    }
    if (subjectElement.tagName === "INPUT") {
        // Record the template's own type before hiding the field: the save-time subject check
        // needs it to know whether readonly applied (an unknown or missing type is text).
        if (!subjectElement.dataset.carlosOriginalType) {
            const type = (subjectElement.getAttribute("type") || "text").toLowerCase();
            subjectElement.dataset.carlosOriginalType = type;
        }
        subjectElement.type = "hidden";
    }
    subjectElement.hidden = true;
    let localSubject = document.getElementById("remote_eform_subject");
    if (localSubject) {
        localSubject.value = subjectElementValue;
    }
}

/**
 * Close this toolbar. Exposes buttons and text that is
 * hidden underneath.
 * A button is still visible on the right side to
 * restore the toolbar.
 */
function closeToolbar() {

    let toolbarContainer = document.getElementById("eform_floating_toolbar");
    let toolbarNav = document.getElementById("eform_floating_toolbar_nav");
    if (toolbarContainer && toolbarNav) {
        toolbarNav.style.display = "none";

        const openToolbarButton = document.getElementById("openToolbarButton");
        openToolbarButton.style.display = "block";
        openToolbarButton.style.minHeight = "50px";

    }
}

/**
 * Restore the floating toolbar.
 * @returns
 */
function openToolbar() {
    const openToolbarButton = document.getElementById("openToolbarButton");
    const toolbarNav = document.getElementById("eform_floating_toolbar_nav");
    const toolbarContainer = document.getElementById("eform_floating_toolbar");
    if (toolbarContainer && openToolbarButton && toolbarNav) {
        toolbarContainer.removeAttribute("style");
        toolbarNav.removeAttribute("style");
        openToolbarButton.style.display = "none";
    }
}

/**
 * Many eforms will already have various buttons for printing, submitting, etc.
 * These buttons should not necessarily be removed because remotesave() and remoteprint() may rely on these buttons
 * To avoid user confusion as to which button to click, this function hides these buttons
 */
function hideElements() {
    const idsOfButtonsToHide = ["SubmitButton", "ResetButton", "PrintButton", "PrintSubmitButton",
        "PrintSaveButton", "pdfButton", "pdfSaveButton", "fax_button", "faxSave_button"];
    for (let i = 0; i < idsOfButtonsToHide.length; i++) {
        let el = document.getElementById(idsOfButtonsToHide[i]);

        if (!el) {
            el = document.getElementsByName(idsOfButtonsToHide[i]);
        }

        if (el && el.constructor === NodeList && el.length > 0) {
            for (let i = 0; i < el.length; i++) {
                el[i].style.display = "none";
            }
        } else if (el && el.constructor !== NodeList) {
            el.style.display = "none";
        }
    }
}

/**
 * A javascript includes method
 * @returns
 */
function includeHTML(elmnt) {
    const file = "../eform/eformFloatingToolbar/eform_floating_toolbar";
    const xhttp = new XMLHttpRequest();
    xhttp.onreadystatechange = function () {
        if (this.readyState === 4) {

            if (this.status === 200) {
                let toolbarWrapper = document.createElement("div");
                toolbarWrapper.setAttribute("id", "toolbarWrapper");
                toolbarWrapper.setAttribute("class", "hidden-print DoNotPrint no-print");
                // Same-origin JSP fragment (eform_floating_toolbar) with server-defined
                // event handlers — innerHTML is required for toolbar functionality.
                toolbarWrapper.innerHTML = this.responseText; // nosemgrep: javascript.browser.security.insecure-document-method.insecure-document-method
                elmnt.append(toolbarWrapper);
                // Initialize only once the asynchronous toolbar input exists.
                moveSubjectReverse();
                // The toolbar arrives after DOMContentLoaded, so the preview guard that ran there
                // found no Save button yet: hide it now that the fragment is in the DOM (#3904).
                hideAdminPreviewSaveButton();
                initializeFaxRecipient();
                positionToolbarAfterForm(toolbarWrapper);

                // After adding floating toolbar update number of attachments
                jQuery('#remoteTotalAttachments').empty().append(jQuery('.delegateAttachment').length);

					// Check email privilege and if not, hide the Email button.
					handleEmailPrivilege();
            }

            if (this.status === 404) {
                elmnt.append("eForm tool bar not found.");
            }
        }
    }
    xhttp.open("GET", file, true);
    xhttp.send();
    /* Exit the function: */
    return;

}

/**
 * Insert additional elements into the eForm to support
 * launch of the floating toolbar.
 */
function addNavElement() {

    includeHTML(document.body);

}

/** Place the toolbar after even absolutely positioned legacy eForm pages. */
function positionToolbarAfterForm(wrapper) {
    // Keep open menus inside the viewport when the toolbar wraps on a narrow screen.
    wrapper.querySelectorAll('details').forEach(details => {
        details.addEventListener('toggle', () => {
            if (!details.open) return;
            const menu = details.querySelector('.eform-options');
            const anchor = details.getBoundingClientRect();
            const left = anchor.left;
            menu.style.right = 'auto';
            menu.style.left = Math.max(8 - left,
                Math.min(0, document.documentElement.clientWidth - left - menu.offsetWidth - 8)) + 'px';
            const above = window.innerHeight - anchor.bottom < menu.offsetHeight
                && anchor.top >= menu.offsetHeight;
            menu.style.top = above ? 'auto' : '100%';
            menu.style.bottom = above ? '100%' : 'auto';
        });
    });
    let queued = false;
    function place() {
        queued = false;
        // Reset the gap before measuring so the toolbar cannot grow its own offset.
        wrapper.style.marginTop = "16px";
        let bottom = 0;
        document.querySelectorAll('body *').forEach(element => {
            if (element === wrapper || wrapper.contains(element) || element.contains(wrapper) || element.closest('dialog')) return;
            const style = getComputedStyle(element);
            if (style.position === 'fixed' || style.display === 'none') return;
            const rect = element.getBoundingClientRect();
            if (rect.width || rect.height) bottom = Math.max(bottom, rect.bottom + window.scrollY);
        });
        const top = wrapper.getBoundingClientRect().top + window.scrollY;
        wrapper.style.marginTop = Math.max(16, Math.ceil(bottom - top + 32)) + 'px';
    }
    function schedule() {
        if (!queued) { queued = true; requestAnimationFrame(place); }
    }
    const observer = new MutationObserver(records => {
        if (records.some(record => !wrapper.contains(record.target))) schedule();
    });
    observer.observe(document.body, {subtree: true, childList: true, characterData: true,
        attributes: true, attributeFilter: ['class', 'style', 'hidden', 'width', 'height',
            'open', 'rows', 'cols', 'size', 'type', 'id']});
    if (window.ResizeObserver) {
        const resize = new ResizeObserver(schedule);
        Array.from(document.forms).filter(form => !wrapper.contains(form)).forEach(form => resize.observe(form));
    }
    document.addEventListener('load', schedule, true);
    window.addEventListener('resize', schedule);
    schedule();
}

function selectedEformFaxRecipient() {
    const fax = document.getElementById('remoteFaxNumber');
    const name = document.getElementById('remoteFaxRecipient');
    // The edited number wins even when the name field is missing: falling through to the eForm's
    // own recipient would fax a number the clinician had replaced.
    if (fax && fax.dataset.edited === 'true') return {name: name ? name.value.trim() : '', fax: fax.value.trim()};
    const chosen = window.carlosEformFax ? window.carlosEformFax.recipient() : {name: '', fax: ''};
    if (name && name.dataset.edited === 'true') chosen.name = name.value.trim();
    return chosen;
}

function initializeFaxRecipient() {
    const fax = document.getElementById('remoteFaxNumber');
    const name = document.getElementById('remoteFaxRecipient');
    if (!fax) return;
    // Edit tracking for the number does not depend on the name field: without it a replacement
    // number typed here was never marked edited, and Fax sent the eForm's own recipient instead.
    ['input', 'change'].forEach(event => {
        // Any number entered or chosen in the field itself replaces the pending cleared state.
        fax.addEventListener(event, () => { fax.dataset.edited = 'true'; delete fax.dataset.cleared; });
    });
    // A number the clinician typed is theirs; one filled in from the directory, the eForm or the
    // list belongs to the recipient it was chosen for. Directory selection assigns both fields and
    // dispatches a synthetic change, so only trusted typing marks the number as typed.
    fax.addEventListener('input', event => { if (event.isTrusted) fax.dataset.typed = 'true'; });
    fax.addEventListener('change', event => { if (!event.isTrusted) delete fax.dataset.typed; });
    if (!name) {
        // Show the number that will be used, so the clinician can see what they are replacing.
        fax.value = selectedEformFaxRecipient().fax;
        return;
    }
    function refresh() {
        if (fax.dataset.edited === 'true') return;
        const chosen = selectedEformFaxRecipient();
        fax.value = chosen.fax;
        // A number the clinician typed into the form's own other-fax field is theirs, like one
        // typed here, so editing the recipient name must keep it. Numbers from a list, the
        // designer or the directory stay tied to their recipient.
        if (chosen.manual) fax.dataset.typed = 'true';
        else delete fax.dataset.typed;
        if (name.dataset.edited !== 'true') name.value = chosen.name;
    }
    const fromForm = document.getElementById('remoteFaxFromForm');
    function refreshOptions() {
        fromForm.replaceChildren(new Option("Use the eForm's fax number", ""));
        const seen = new Set();
        ['faxnumList', 'otherFaxSelect'].forEach(id => {
            const select = document.getElementById(id);
            if (!select || !select.options) return;
            Array.from(select.options).forEach(option => {
                if (!option.value.trim() || seen.has(option.value)) return;
                seen.add(option.value);
                const name = option.getAttribute('name') || option.textContent.trim();
                const item = new Option(name + ' — ' + option.value, option.value);
                item.dataset.recipientName = name;
                fromForm.add(item);
            });
        });
        if (fax.dataset.edited === 'true') fromForm.value = fax.value;
    }
    fromForm.addEventListener('change', () => {
        const option = fromForm.options[fromForm.selectedIndex];
        delete fax.dataset.cleared;
        if (option && option.value) {
            fax.value = option.value;
            name.value = option.dataset.recipientName;
            fax.dataset.edited = 'true';
            delete fax.dataset.typed;
        } else {
            delete fax.dataset.edited;
            delete fax.dataset.typed;
            delete name.dataset.edited;
            refresh();
        }
    });
    ['input', 'change'].forEach(event => {
        name.addEventListener(event, () => { name.dataset.edited = 'true'; });
    });
    // Typing a different recipient name must not keep the previous recipient's number: the fax
    // would go there under the new name. Clear it (as an explicit empty override, so the eForm's
    // number is not resurrected either) until a directory row or a typed number supplies one.
    // The clear is marked separately from a number the clinician chose, so a later explicit choice
    // on the eForm itself can still fill the number for the newly typed recipient.
    name.addEventListener('input', event => {
        if (!event.isTrusted || fax.dataset.typed === 'true') return;
        fax.value = '';
        fax.dataset.edited = 'true';
        fax.dataset.cleared = 'true';
        fromForm.value = '';
    });
    document.getElementById('remoteFaxOptions').addEventListener('toggle', () => { refreshOptions(); refresh(); });
    document.addEventListener('change', event => {
        if (!['otherFaxInput', 'faxnumList', 'otherFaxSelect'].includes(event.target.id)) return;
        // A list or designer selection made after the name was retyped is a new, explicit source:
        // lift only the pending clear (never a typed or menu-chosen number) and let it fill in.
        if (fax.dataset.cleared === 'true') {
            delete fax.dataset.cleared;
            delete fax.dataset.edited;
        }
        refresh();
    });
    if (typeof setupFaxRecipientAutocomplete === 'function') {
        setupFaxRecipientAutocomplete({contextPath: document.getElementById('context').value,
            nameInputId: name.id, faxInputId: fax.id, dropdownId: 'remoteFaxSuggestions'});
    }
    refresh();
}

function showError(message) {
    if (!message) {
        message = "Failed to process eForm. Please refer to the server logs for more details."
    }
    alert(message.replace(/\\n/g, "\n"));
}

/*
 * Show or hide the loading spinner
 * if locked is true: can't click away
 * if locked is false: can click away from it
 */
function ShowSpin(locked) {
    let screen = document.getElementById("oscar-spinner-screen");
    let spinner = document.getElementById("oscar-spinner");

    screen.classList.add("active-oscar-spinner");
    spinner.classList.add("active-oscar-spinner");

    if (locked) {
        screen.removeEventListener("click", HideSpin);
    } else {
        screen.addEventListener("click", HideSpin);
    }
    return true;
}

function HideSpin() {
    let screen = document.getElementById("oscar-spinner-screen");
    let spinner = document.getElementById("oscar-spinner");

    screen.classList.remove("active-oscar-spinner");
    spinner.style.opacity = "0";

    setTimeout(function () {
        spinner.classList.remove("active-oscar-spinner");
        spinner.style.opacity = "1";
    }, 300);
}

	/**
	 * A counter hack for a hack.
	 * This method moves the image SRC values into hidden place-holders in the Form element
	 * A counter-measure to ensure images that are set by Javascript methods are captured
	 * when the form is saved or rendered into a pdf.
	 */
	function appendImageInputs() {
		jQuery("form[method='POST'] img").each(function () {
			const id = jQuery(this).attr('id');
			const src = jQuery(this).attr('src') || "";

			// Skip image if it doesn't have an ID
			if (!id || id.trim() === "") {
				return true;
			}

			const inputId = 'openosp-img-' + id;

			// Remove any existing hidden input for this image
			jQuery("input[type='hidden'][id='" + inputId + "']").remove();

			// Add a fresh hidden input
			jQuery('<input>', {
				id: inputId,
				name: 'openosp-image-link',
				value: JSON.stringify({ id: id, value: src }),
				type: 'hidden'
			}).appendTo("form[method='POST']");
		});
	}

	jQuery(window).on('load', function() {
		appendImageInputs();
	})

	/**
	 * Disables resizing on all textarea elements to prevent content from being
	 * truncated during PDF generation.
	 */
	function disableTextareaResize() {
		if (document.getElementById("eform-disable-textarea-resize")) {
			return;
		}

		const style = document.createElement("style");
		style.id = "eform-disable-textarea-resize";
		style.textContent = "textarea { resize: none !important; }";
		document.head.appendChild(style);
	}

	function handleEmailPrivilege() {
		// Get the value of the element with ID 'hasEmailPrivilege'
		const hasEmailPrivilege = document.getElementById('hasEmailPrivilege');

		if (hasEmailPrivilege) {
			const value = hasEmailPrivilege.value.toLowerCase();
			if (value === 'false') {
				// If 'hasEmailPrivilege' is false, hide the 'remoteEmailButton'
				document.getElementById('remoteEmailButton').style.display = 'none';
			}
		}
	}
