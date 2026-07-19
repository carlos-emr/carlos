function addComment(reportId) {
    let comment = jQuery("#commentField_" + reportId + "_hrm").val();
    let data = {method: "addComment", reportId: reportId, comment: comment};
    jQuery.ajax({
        type: "POST",
        url: contextpath + "/hospitalReportManager/Modify.do",
        data: data,
        success: function (data) {
            if (data != null)
                $("commentstatus" + reportId).innerHTML = data;
        }
    });
}

function deleteComment(commentId, reportId) {
    jQuery.ajax({
        type: "POST",
        url: contextpath + "/hospitalReportManager/Modify.do",
        data: "method=deleteComment&commentId=" + commentId,
        success: function (data) {
            if (data != null)
                $("commentstatus" + reportId).innerHTML = data;
        }
    });
}

function doSignOff(reportId, view, isSign) {
    var data;
    if (isSign)
        data = "method=signOff&signedOff=1&reportId=" + reportId;
    else
        data = "method=signOff&signedOff=0&reportId=" + reportId;

    jQuery.ajax({
        type: "POST",
        url: contextpath + "/hospitalReportManager/Modify.do",
        data: data,
        success: function (data) {
            if (view) {
                self.opener.removeReport(reportId);
                window.close();
            } else {
                var signOffButton = document.getElementById('signoff' + reportId);
                var buttonText = "Sign-Off";
                var buttonClick = "";

                if (isSign) {
                    buttonText = "Revoke " + buttonText;
                    buttonClick = "javascript: revokeSignOffHrm('" + reportId + "');";
                } else {
                    buttonClick = "javascript: signOffHrm('" + reportId + "', " + view + ");";
                }

                signOffButton.value = buttonText;
                signOffButton.setAttribute("onClick", buttonClick);
            }
        }
    });
}

function makeIndependent(reportId) {
    jQuery.ajax({
        type: "POST",
        url: contextpath + "/hospitalReportManager/Modify.do",
        data: "method=makeIndependent&reportId=" + reportId,
        success: function (data) {
            if (data != null && data.indexOf('Success') !== -1) {
                $("similarNotice").innerHTML = "";
            }
        }
    });
}

function addDemoToHrm(reportId, csrfToken) {
    let headers = {};
    if (typeof csrfToken !== 'undefined' && csrfToken !== null) {
        headers = csrfToken;
    }
    var demographicNo = $("demofind" + reportId + "hrm").value;
    jQuery.ajax({
        type: "POST",
        url: contextpath + "/hospitalReportManager/Modify.do",
        data: "method=assignDemographic&reportId=" + reportId + "&demographicNo=" + demographicNo,
        headers: headers,
        success: function (data) {
            if (data != null && data.indexOf('Success') !== -1) {
                $("demostatus" + reportId).innerHTML = data + "<br/>" +
                    $('autocompletedemo' + reportId + 'hrm').value.split('(')[0] +
                    "<a href=\"#\" onclick=\"removeDemoFromHrm('" + reportId + "')\">(remove)</a>";
                $('autocompletedemo' + reportId + 'hrm').hide();
                toggleButtonBar(true, reportId);
            }
        }
    });
}

function toggleButtonBar(show, reportId) {
    jQuery("#msgBtn_" + reportId).prop('disabled', !show);
    jQuery("#mainTickler_" + reportId).prop('disabled', !show);
    jQuery("#mainEchart_" + reportId).prop('disabled', !show);
    jQuery("#mainMaster_" + reportId).prop('disabled', !show);
    jQuery("#mainApptHistory_" + reportId).prop('disabled', !show);

}

function removeDemoFromHrm(reportId, csrfToken) {
    let headers = {};
    if (typeof csrfToken !== 'undefined' && csrfToken !== null) {
        headers = csrfToken;
    }
    jQuery.ajax({
        type: "POST",
        url: contextpath + "/hospitalReportManager/Modify.do",
        data: "method=removeDemographic&reportId=" + reportId,
        headers: headers,
        success: function (data) {
            if (data != null && data.indexOf('Success') !== -1) {
                $("demostatus" + reportId).innerHTML = data + "<br/>" +
                    "<i>Not currently linked</i>";
                $('autocompletedemo' + reportId + 'hrm').value = "";
                $('autocompletedemo' + reportId + 'hrm').show();
                $('demofind' + reportId + 'hrm').value = null;
                toggleButtonBar(false, reportId);
            }
        }
    });
}

function addProvToHrm(reportId, providerNo) {
    jQuery.ajax({
        type: "POST",
        url: contextpath + "/hospitalReportManager/Modify.do",
        data: "method=assignProvider&reportId=" + reportId + "&providerNo=" + providerNo,
        success: function (data) {
            if (data != null)
                $("provstatus" + reportId).innerHTML = data;
        }
    });
}

function removeProvFromHrm(mappingId, reportId) {
    jQuery.ajax({
        type: "POST",
        url: contextpath + "/hospitalReportManager/Modify.do",
        data: "method=removeProvider&providerMappingId=" + mappingId,
        success: function (data) {
            if (data != null)
                $("provstatus" + reportId).innerHTML = data;
        }
    });
}

function makeActiveSubClass(reportId, subClassId) {
    jQuery.ajax({
        type: "POST",
        url: contextpath + "/hospitalReportManager/Modify.do",
        data: "method=makeActiveSubClass&reportId=" + reportId + "&subClassId=" + subClassId,
        success: function (data) {
            if (data != null)
                $("subclassstatus" + reportId).innerHTML = data;
        }
    });

    window.location.reload();
}


function printHrm(hrmReportId) {
    window.location = contextpath + "/hospitalReportManager/PrintHRMReport.do?segmentId=" + hrmReportId + "&hrmReportId=" + hrmReportId;
}

function setDescription(reportId) {
    let comment = jQuery("#descriptionField_" + reportId + "_hrm").val();
    let data = {method: "setDescription", reportId: reportId, description: comment};
    jQuery.ajax({
        type: "POST",
        url: contextpath + "/hospitalReportManager/Modify.do",
        data: data,
        success: function (data) {
            if (data != null)
                $("descriptionstatus" + reportId).innerHTML = data;
        }
    });
}

function signOffHrm(reportId, view) {

    doSignOff(reportId, view, true);
}

function revokeSignOffHrm(reportId) {
    doSignOff(reportId, false);
}

function editCategory(reportId) {
    $('chooseCategory_' + reportId).show();
    $('showCategory_' + reportId).hide();
}

function updateCategory(reportId) {
    var categoryId = $('selectedCategory_' + reportId).value;
    var categoryName = $('selectedCategory_' + reportId).options[$('selectedCategory_' + reportId).selectedIndex].text;
    if (categoryId) {
        jQuery.ajax({
            type: "POST",
            url: contextpath + "/hospitalReportManager/Modify.do",
            data: "method=updateCategory&reportId=" + reportId + "&categoryId=" + categoryId,
            success: function (data) {
                if (data != null) {
                    if (data.indexOf('Success') !== -1) {
                        $('hrmCategory_' + reportId).innerHTML = $('selectedCategory_' + reportId).innerHTML = categoryName;
                        $('chooseCategory_' + reportId).hide();
                        $('showCategory_' + reportId).show();
                        toggleButtonBar(false, reportId);
                    }
                }
            }
        });
    }

}

function setupHrmDemoAutoCompletion(docId, csrfToken) {
    if (jQuery("#autocompletedemo" + docId + "hrm")) {

        let url = window.contextpath + "/demographic/SearchDemographic.do?jqueryJSON=true";
        if (jQuery("#activeOnly" + docId + "hrm").is(":checked")) {
            url = window.contextpath + "/demographic/SearchDemographic.do?jqueryJSON=true&activeOnly=true";
        }

        jQuery("#autocompletedemo" + docId + "hrm").autocomplete({
            source: url,
            minLength: 2,
            focus: function (event, ui) {
                jQuery("#autocompletedemo" + docId + "hrm").val(ui.item.label);
                return false;
            },
            select: function (event, ui) {
                jQuery("#autocompletedemo" + docId + "hrm").val(ui.item.label);
                jQuery("#demofind" + docId + "hrm").val(ui.item.value);
                jQuery("routetodemo" + docId + "hrm").value = ui.item.value;

                addDemoToHrm(docId, csrfToken);
                return false;
            }
        });
    }
}
