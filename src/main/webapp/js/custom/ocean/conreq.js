var section = 'consultation';

// Instead of importing conreq.js using the CME tag (as done in Oscar19/OscarPro),
// we are opting to directly import conreq.js into ConsultationFormRequest.jsp to load the ocean refer button without utilizing the CME tag.

// Here, the 'ocean-host' script attribute is added in the conreq.js script tag as follows:
// '<script id="mainScript" src="${ pageContext.request.contextPath }/js/custom/ocean/conreq.js?no-cache=<%=randomNo%>&autoRefresh=true" ocean-host=<%=Encode.forUriComponent(props.getProperty("ocean_host"))%>></script>'
// when it is being imported into the ConsultationFormRequest.jsp.
(function (script) {
    let oceanHost = script.attributes['ocean-host'];
    if (oceanHost && oceanHost.value) {
        window.oceanHost = decodeURIComponent(oceanHost.value);
    }
})(document.currentScript);


jQuery(document).ready(function () {
    jQuery("#ocean").append("<div id='ocean_div'></div>");
    jQuery.ajax({
        url: window.oceanHost + "/robots.txt",
        cache: true,
        dataType: "text",
        success: function () {
            jQuery.ajax({
                url: window.oceanHost + "/oscar_resources/OscarToolbar.js",
                cache: true,
                dataType: "script"
            });
        },
        error: function (jqXHR, textStatus, error) {
            console.log("Ocean toolbar error: " + textStatus + ", " + error);
            jQuery("#ocean_div").show().css("padding", "5px").css("text-align", "center");
        }
    });
});

// This code will be executed when a user sends a new e-refer to OceanMD from the consult request form.
// It involves saving attachments in the 'EreferAttachment' table by sending the selected attachments (documents)
// and demographic information to the EreferAction.java class.
function eRefer(event) {
    let demographicNo = document.getElementById("demographicNo").serialize();
    let documents = getDocuments(event);
    let data = demographicNo + "&" + documents + "&method=attachOceanEReferralConsult";
    jQuery.ajax({
        type: 'POST',
        url: document.getElementById("contextPath").value + '/oscarEncounter/eRefer.do',
        data: data,
        success: function (response) {
            console.log(response);
        }
    });
}

function getDocuments(event) {
    let documents = "";
    let attachments = document.getElementsByClassName("delegateAttachment");
    let isFormAttached = false;
    for (const attachment of attachments) {
        documents = documents + attachment.name.charAt(0).toUpperCase() + attachment.value + "|";
        if (attachment.name.charAt(0).toUpperCase() === 'F') {
            isFormAttached = true;
        }
    }

    if (isFormAttached) {
        const canProceed = confirm("Forms cannot be attached to Ocean referrals.\n\nWorkaround:\nCreate a PDF of the form and upload it to the patient's chart as a document. Then you can attach that document with the referral.\n\nTo proceed without attaching Form, please press \'OK\'");
        if (!canProceed) {
            event.stopImmediatePropagation();
            documents = "";
        }
    }

    return "documents=" + documents;
}

// This code will be executed when a user tries to edit attachments on an already created consult request for Ocean e-refer.
// It will update the attachments in the consult request and send those attachments to OceanMD
// by saving them into the 'EreferAttachment' table.
function attachOceanAttachments() {
    let demographicNo = document.getElementById("demographicNo").serialize();
    let requestId = document.getElementById("requestId").serialize();
    let documents = getDocuments();
    let data = demographicNo + "&" + requestId + "&" + documents + "&method=editOceanEReferralConsult";
    jQuery.ajax({
        type: 'POST',
        url: document.getElementById("contextPath").value + '/oscarEncounter/eRefer.do',
        data: data,
        success: function (response) {
            console.log(response);
        }
    });
}
