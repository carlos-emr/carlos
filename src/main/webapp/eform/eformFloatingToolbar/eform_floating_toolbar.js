document.addEventListener("DOMContentLoaded", function(){


    /**
     * Trigger these functions every time this page loads.
     */
    removeElements();
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
    }

    // Handle EForm errors
    const error = document.getElementById("error") ? document.getElementById("error").value : "false";
    const errorMessage = document.getElementById("errorMessage") ? document.getElementById("errorMessage").value : "";
    if (error === "true") {
        showError(errorMessage);
    }

		// add listener to the subject element
		if(document.forms[0].elements["subject"]) {
			document.forms[0].elements["subject"].addEventListener("input", function () {
				document.getElementById("remote_eform_subject").value = this.value;
			})
			document.forms[0].elements["subject"].addEventListener("click", function () {
				document.getElementById("remote_eform_subject").value = this.value;
			})
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

function hideAdminPreviewSaveButton() {
    const demographicNo = document.getElementById("demographicNo");
    if (demographicNo?.value !== "-1") {
        return;
    }

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
	const stillLoading = Array.from(document.querySelectorAll('select option'))
		.some((option) => option.textContent.trim() === 'loading...');
	if (!stillLoading) {
		editorLoadingBlockCount = 0;
		return false;
	}
	editorLoadingBlockCount += 1;
	if (editorLoadingBlockCount >= 3) {
		alert('The letter editor did not finish loading. Close and reopen the letter; if this keeps happening, contact support.');
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
	} else {
		alert('The letter editor is still loading. Please wait a moment and try again.');
	}
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
	const ef = getEForm();
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

		// A legacy string timer that never ran can leave fields unpopulated. The compat shim's own
		// capture-phase submit listener cannot help here: every save path below reaches the server
		// through HTMLFormElement.submit(), which fires no submit event by design.
		const timerCompat = window.__carlosEformTimerCompat;
		if (timerCompat && typeof timerCompat.shouldBlockSubmission === "function"
				&& timerCompat.shouldBlockSubmission()) {
			HideSpin();
			return false;
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
    jQuery("#attachDocumentDisplay").load(trigger.data('poload'), function (response, status, xhr) {
        if (status === "success") {
            // Disable the floating toolbar when the attachment window opens
            const eformFloatingToolbar = document.getElementById("eform_floating_toolbar");
            eformFloatingToolbar.classList.add("disabled-toolbar");

            jQuery('#attachDocumentList').find(".delegateAttachment").each(function (index, data) {
                let delegate = "#" + this.id.split("_")[1];
                let element = jQuery('#attachDocumentsForm').find(delegate);
                if (element.length === 0) {
                    element = addFormIfNotFound(data, demographicNo, delegate);
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
            // before the dialog is closed:

            // check if list exists, if yes then empty it otherwise create new
            if (jQuery('#attachDocumentList').length === 0) {
                const attachDocumentList = jQuery('<div>', {'id': 'attachDocumentList'});
                jQuery('form:first').append(attachDocumentList);
            }
            jQuery('#attachDocumentList').empty();

            // pass the checked documents to the eForm document list(attachDocumentList)
            jQuery('#attachDocumentsForm').find(".document_check:checked:not(input[disabled='disabled']), .lab_check:checked:not(input[disabled='disabled']), .form_check:checked:not(input[disabled='disabled']), .eForm_check:checked:not(input[disabled='disabled']), .hrm_check:checked:not(input[disabled='disabled'])"
            ).each(function (index, data) {
                let element = jQuery(this);
                let input = jQuery("<input />", {
                    type: 'hidden',
                    name: element.attr('name'),
                    value: element.val(),
                    id: "delegate_" + element.attr('id'),
                    class: 'delegateAttachment'
                });
                jQuery('#attachDocumentList').append(input);
            });

            // show total attachments
            jQuery('#remoteTotalAttachments').empty().append(jQuery('.delegateAttachment').length);

            // Enable the floating toolbar when the attachment window closes
            const eformFloatingToolbar = document.getElementById("eform_floating_toolbar");
            eformFloatingToolbar.classList.remove("disabled-toolbar");
        }
    });
});

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
    setHiddenFormInput("faxAction", "faxEForm", "true");

    /*
     * This helps carry forward the select list values of fax recipients
     * from the eForm.
     */
    const faxnumList = document.getElementById("faxnumList");
    if (faxnumList) {
        const selectedOption = faxnumList.options[faxnumList.options.selectedIndex];
        const recipientFaxNumber = selectedOption.getAttribute("value");
        const recipient = selectedOption.getAttribute('name');

        if (recipientFaxNumber) {
            // Reuse-by-id so repeated fax attempts refresh (not duplicate) the recipient inputs.
            setHiddenFormInput("recipient", "recipient", recipient);
            setHiddenFormInput("recipientFaxNumber", "recipientFaxNumber", recipientFaxNumber);
        }
    }

    remoteSave();
}

/**
 * Sets (creating once, then reusing by id) a hidden input on the primary form. Reuse-by-id keeps
 * repeated aborted/retried actions from accumulating duplicate id/name inputs (form encoding takes
 * the first, so a stale duplicate could otherwise win over a fresh value).
 */
function setHiddenFormInput(id, name, value) {
    let input = document.getElementById(id);
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
        document.forms[0].appendChild(input);
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

    // Check before appending emailEForm=true so an editor-still-loading abort does not leave it on
    // the form for a later plain Save to ride into the email workflow.
    if (editorStillLoading()) {
        return;
    }
    clearWorkflowFlags();
    setHiddenFormInput("emailAction", "emailEForm", "true");
    remoteSave();

}

/**
 * Triggers the eForm print function
 */
function remotePrint() {

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

		/*
		 * Needs to be saved if this is
		 * a new eForm or it has been altered.
		 */
		if(typeof needToConfirm !== 'undefined' && needToConfirm) {
			console.log("eForm needs to be saved.")
			remoteSave();
		}

		/*
		 * for situations when the eForm does not contain dirty form
		 * detection; save it everytime.
		 */
		else if(typeof needToConfirm === 'undefined') {
			remoteSave();
	}
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
    if (localSubject) {
        localSubject.value = remoteSubjectValue;
    }
}

function moveSubjectReverse() {
    let subjectElement = document.forms[0].elements["subject"];
    let subjectElementValue;

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

        toolbarContainer.style.display = "table";
        toolbarContainer.style.position = "fixed";
        toolbarContainer.style.opacity = "100%";
        toolbarContainer.style.zIndex = "1029";
        toolbarContainer.style.bottom = "0";
        toolbarContainer.style.right = "0";
        toolbarContainer.style.marginBottom = "0";

        const openToolbarButton = document.getElementById("openToolbarButton");
        openToolbarButton.style.display = "table";
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
 * Remove all fax control buttons from the current
 * eform to avoid any confusion on what fax system is being used.
 */
function removeElements() {
    let element = document.getElementById("faxControl");

    if (element) {
        element.parentNode.removeChild(element);
    }

    element = document.querySelectorAll("script");
    const scriptArray = Array.from(element);

    if (scriptArray.length > 0) {
        const script = scriptArray.find(script => script.src.includes("faxControl.js"))
        if (script) {
            script.parentNode.removeChild(script);
        }
    }

    element = document.getElementById("fax_button");

    if (element) {
        element.parentNode.removeChild(element);

        /*
         * add a dummy placeholder back in because the eForm developers
         * created a hard dependency on the existence of this element.
         */
        const inputElement = document.createElement("input");
        inputElement.setAttribute("type", "hidden");
        inputElement.setAttribute("id", "fax_button");
        document.forms[0].appendChild(inputElement);
    }

    element = document.getElementById("faxSave_button");

    if (element) {
        element.parentNode.removeChild(element);

        /*
         * add a dummy placeholder back in because the eForm developers
         * created a hard dependency on the existence of this element.
         */
        const inputElement = document.createElement("input");
        inputElement.setAttribute("type", "hidden");
        inputElement.setAttribute("id", "faxSave_button");
        document.forms[0].appendChild(inputElement);
    }

    element = document.getElementById("faxEForm");

    if (element) {
        element.parentNode.removeChild(element);

        /*
         * add a dummy placeholder back in because the eForm developers
         * created a hard dependency on the existence of this element.
         */
        const inputElement = document.createElement("input");
        inputElement.setAttribute("type", "hidden");
        inputElement.setAttribute("id", "faxEForm");
        document.forms[0].appendChild(inputElement);
    }

    /*
     * sometimes these are in there too.
     */
    let inputElement = document.createElement("input");
    inputElement.setAttribute("type", "hidden");
    inputElement.setAttribute("id", "otherFaxInput");
    document.forms[0].appendChild(inputElement);
}

/**
 * A wrapper function to dismiss uncaught exceptions for when
 * this function contained in the removed faxControl.js file is
 * called.
 * Do nothing.
 */
function AddOtherFax() {
    // do nothing
    return false;
}

/**
 * Many eforms will already have various buttons for printing, submitting, etc.
 * These buttons should not necessarily be removed because remotesave() and remoteprint() may rely on these buttons
 * To avoid user confusion as to which button to click, this function hides these buttons
 */
function hideElements() {
    const idsOfButtonsToHide = ["SubmitButton", "ResetButton", "PrintButton", "PrintSubmitButton"];
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

    /*
     * Get the total height of the current eform
     */
    let body = document.body;
    let html = document.documentElement;
    let documentheight = Math.max(body.scrollHeight, body.offsetHeight,
        html.clientHeight, html.scrollHeight, html.offsetHeight);

    /*
     * Include the eForm tool bar overlay
     */
    includeHTML(body);

    /*
     * Add a wedge to the bottom of the eform that will add
     * 65 pixels to the bottom so that the eForm clears the remote button
     * panel
     */
    let formelement = document.getElementsByTagName("form");
    let spacer = document.createElement("div");
    spacer.setAttribute("id", "eformPageSpacer");
    spacer.setAttribute("class", "hidden-print DoNotPrint no-print");
    spacer.style.position = "absolute";
    spacer.style.left = 0;
    spacer.style.top = documentheight + 50;
    spacer.style.width = "100%";
    spacer.style.margin = 0;
    spacer.style.padding = 0;
    spacer.style.height = "1px";
    formelement[0].appendChild(spacer);

    /*
     * Inject Bootstrap 5 CSS into the eForm page so that toolbar components render correctly.
     * This is required for standalone HTML eForms that do not load Bootstrap themselves.
     */
    let headelement = document.getElementsByTagName("head");
    let bootstrapStyle = document.createElement("link");
    bootstrapStyle.setAttribute("rel", "stylesheet");
    bootstrapStyle.setAttribute("type", "text/css");
    bootstrapStyle.setAttribute("href", "../library/bootstrap/5.3.8/css/bootstrap.min.css");
    headelement[0].appendChild(bootstrapStyle);

    /*
     * Inject toolbar-specific CSS that provides the critical #toolbarWrapper positioning
     * (position:fixed, z-index:10000) and scoped styles not present in bootstrap.min.css.
     * Previously bundled inside eform_floating_toolbar_bootstrap_custom.min.css (Bootstrap 3).
     */
    let toolbarStyle = document.createElement("link");
    toolbarStyle.setAttribute("rel", "stylesheet");
    toolbarStyle.setAttribute("type", "text/css");
    toolbarStyle.setAttribute("href", "../eform/eformFloatingToolbar/eform_floating_toolbar_custom.css");
    headelement[0].appendChild(toolbarStyle);

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
