/* printControl - Changes eform to add a server side generated PDF 
 *                with print functionality intact (if print button on the form).
 */

if (typeof jQuery == "undefined") {
    alert("The printControl library requires jQuery. Please ensure that it is loaded first");
}
var printControl = {
    initialize: function () {

        var submit = jQuery("input[name='SubmitButton']");
        var printSave = jQuery("input[name='PrintSaveButton']");
        submit.append("<input name='pdfSaveButton' type='button'>");
        submit.append("<input name='pdfButton' type='button'>");
        var pdf = jQuery("input[name='pdfButton']");
        var pdfSave = jQuery("input[name='pdfSaveButton']");
        if (!pdf) {
            pdf = jQuery("input[name='pdfButton']");
        }
        if (!pdfSave) {
            pdfSave = jQuery("input[name='pdfSaveButton']");
        }

        pdf.insertAfter(submit);
        pdfSave.insertAfter(submit);

        if (pdf) {
            pdf.attr("onclick", "").unbind("click");
            pdf.attr("value", "PDF");
            pdf.click(function () {
                submitPrintButton(false);
            });
        }
        if (pdfSave) {
            pdfSave.attr("onclick", "").unbind("click");
            pdfSave.attr("value", "Submit & PDF");
            pdfSave.click(function () {
                submitPrintButton(true);
            });
        }
        if (printSave) {
            printSave.attr("value", "Submit & Print");
        }

    }
};

/*
 * Posts the form with print=true. On the server (AddEForm2Action) that flag is the legacy alias of
 * the toolbar's saveAndDownloadEForm workflow: the eForm is saved, rendered to PDF, and the PDF is
 * handed back as a download. skipSave is advisory only — every render works from a saved eForm,
 * so "PDF" and "Submit & PDF" both persist the form before rendering it.
 */
function submitPrintButton(save) {

    // Setting this form to print. jQuery('#x') is never null or falsy, so the previous
    // `if (printHolder == null || !printHolder)` guard never appended the hidden inputs: the print
    // flag never reached the server and both buttons were a plain Save with no PDF. The length
    // check is what actually tells us whether the input already exists.
    if (jQuery('#printHolder').length === 0) {
        jQuery("form").append("<input id='printHolder' type='hidden' name='print' value='true' >");
    }
    var printHolder = jQuery('#printHolder');
    printHolder.val("true");

    if (jQuery("#saveHolder").length === 0) {
        jQuery("form").append("<input id='saveHolder' type='hidden' name='skipSave' value='" + !save + "' >");
    }
    var saveHolder = jQuery("#saveHolder");
    saveHolder.val(!save);
    needToConfirm = false;

    if (document.getElementById('Letter') != null) {
        if (typeof saveRTL === "function") {
            // The Rich Text Letter's own serializer: it entity-escapes the editor HTML before it is
            // stored, and both readers of the stored value (editControl2.js on reopen and the PDF
            // composer) unconditionally decode. Writing the raw editor HTML here instead — as this
            // used to — stored the letter in a different encoding than the Save button did, so a
            // letter containing literal "&lt;" text came back mangled.
            saveRTL();
        } else {
            document.getElementById('Letter').value = editControlContents('edit');
        }
    }

    jQuery("form")[0].submit();
    // No fixed close timer after "Submit & PDF": the response IS the PDF download (rendered
    // server-side, which can take longer than the 3 s the legacy string timer allowed), so closing
    // the window on a clock could tear the page down before the download page ran and lose the
    // PDF. The download result page owns the auto-close instead: for "Submit & PDF" the server
    // sets isSuccess_Autoclose and the floating toolbar closes the window after the download has
    // started and the saved alert has shown, as it does after a plain Submit. "PDF" (preview)
    // leaves the window open.
    printHolder.val("false");
    saveHolder.val("false");

}


jQuery(document).ready(function () {
    printControl.initialize();
});
