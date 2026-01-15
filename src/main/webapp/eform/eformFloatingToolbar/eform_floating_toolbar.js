document.addEventListener("DOMContentLoaded", function(){


    /**
     * Trigger these functions every time this page loads.
     */
    removeElements();
    hideElements();
    addNavElement();
    moveSubjectReverse();

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
	if (isSuccessAndAutoclose) {
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
    jQuery.post(context + "/eform/logEformError.do", eform);
}

function getEForm() {
	let ef = document.forms['saveEForm'];
	if (!ef) {
		ef = Array.prototype.find.call(
			document.forms,
			f => f.action && f.action.includes('addEForm.do')
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

	/**
	 * Triggers the eForm save/submit function
	 */
function remoteSave() {

	try {
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
    trigger.data('poload', context + '/previewDocs.do?method=fetchEFormDocuments&demographicNo=' + demographicNo + '&fdid=' + fdid);
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
        getPdf('FORM', formValue, 'method=renderFormPDF&formId=' + formValue + '&formName=' + formName + '&demographicNo=' + demographicNo);
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
    ShowSpin(true);
    const newElement = document.createElement("input");
    newElement.setAttribute("id", "saveAndDownloadEForm");
    newElement.setAttribute("name", "saveAndDownloadEForm");
    newElement.setAttribute("value", "true");
    newElement.setAttribute("type", "hidden");
    document.forms[0].appendChild(newElement);

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
    const newElement = document.createElement("input");
    newElement.setAttribute("id", "faxAction");
    newElement.setAttribute("name", "faxEForm");
    newElement.setAttribute("value", "true");
    newElement.setAttribute("type", "hidden");
    document.forms[0].appendChild(newElement);

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
            const recipientElement = document.createElement("input");
            recipientElement.setAttribute("id", "recipient");
            recipientElement.setAttribute("name", "recipient");
            recipientElement.setAttribute("value", recipient);
            recipientElement.setAttribute("type", "hidden");

            document.forms[0].appendChild(recipientElement);

            const recipientNumberElement = document.createElement("input");
            recipientNumberElement.setAttribute("id", "recipientFaxNumber");
            recipientNumberElement.setAttribute("name", "recipientFaxNumber");
            recipientNumberElement.setAttribute("value", recipientFaxNumber);
            recipientNumberElement.setAttribute("type", "hidden");

            document.forms[0].appendChild(recipientNumberElement);
        }
    }

    remoteSave();
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

    const newElement = document.createElement("input");
    newElement.setAttribute("id", "emailAction");
    newElement.setAttribute("name", "emailEForm");
    newElement.setAttribute("value", "true");
    newElement.setAttribute("type", "hidden");
    document.forms[0].appendChild(newElement);
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

    const edocElement = document.getElementById("saveAsEdoc");
    if (edocElement) {
        edocElement.value = 'true';
    } else {
        const newElement = document.createElement("input");
        newElement.setAttribute("id", "saveAsEdoc");
        newElement.setAttribute("name", "saveAsEdoc");
        newElement.setAttribute("value", "true");
        newElement.setAttribute("type", "hidden");
        document.forms[0].appendChild(newElement);
    }

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
    const file = "../eform/eformFloatingToolbar/eform_floating_toolbar.jspf";
    const xhttp = new XMLHttpRequest();
    xhttp.onreadystatechange = function () {
        if (this.readyState === 4) {

            if (this.status === 200) {
                let toolbarWrapper = document.createElement("div");
                toolbarWrapper.setAttribute("id", "toolbarWrapper");
                toolbarWrapper.setAttribute("class", "hidden-print DoNotPrint no-print");
                toolbarWrapper.innerHTML = this.responseText;
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
     * Place the new CSS style into the parent page.
     */
    let headelement = document.getElementsByTagName("head");
    let style = document.createElement("link");
    style.setAttribute("rel", "stylesheet");
    style.setAttribute("type", "text/css");
    style.setAttribute("href", "../library/bootstrap/3.0.0/css/eform_floating_toolbar_bootstrap_custom.min.css");
    headelement[0].appendChild(style);

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
