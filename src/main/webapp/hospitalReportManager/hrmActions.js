function addComment(reportId) {
    let comment = jQuery("#commentField_" + reportId + "_hrm").val();
    let data = {method: "addComment", reportId: reportId, comment: comment};
    jQuery.ajax({
        type: "POST",
        url: contextpath + "/hospitalReportManager/Modify.do",
        data: data,
        success: function (data) {
            if (data != null)
                document.getElementById("commentstatus" + reportId).innerHTML = data;
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
                document.getElementById("commentstatus" + reportId).innerHTML = data;
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
                document.getElementById("similarNotice").innerHTML = "";
            }
        }
    });
}

function addDemoToHrm(reportId) {
    var demographicNo = document.getElementById("demofind" + reportId + "hrm").value;
    jQuery.ajax({
        type: "POST",
        url: contextpath + "/hospitalReportManager/Modify.do",
        data: "method=assignDemographic&reportId=" + reportId + "&demographicNo=" + demographicNo,
        success: function (data) {
            if (data != null && data.indexOf('Success') !== -1) {
                document.getElementById("demostatus" + reportId).innerHTML = data + "<br/>" +
                    document.getElementById('autocompletedemo' + reportId + 'hrm').value.split('(')[0] +
                    "<a href=\"#\" onclick=\"removeDemoFromHrm('" + reportId + "')\">(remove)</a>";
                document.getElementById('autocompletedemo' + reportId + 'hrm').style.display = 'none';
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

function removeDemoFromHrm(reportId) {
    jQuery.ajax({
        type: "POST",
        url: contextpath + "/hospitalReportManager/Modify.do",
        data: "method=removeDemographic&reportId=" + reportId,
        success: function (data) {
            if (data != null && data.indexOf('Success') !== -1) {
                document.getElementById("demostatus" + reportId).innerHTML = data + "<br/>" +
                    "<i>Not currently linked</i>";
                document.getElementById('autocompletedemo' + reportId + 'hrm').value = "";
                document.getElementById('autocompletedemo' + reportId + 'hrm').style.display = '';
                document.getElementById('demofind' + reportId + 'hrm').value = null;
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
                document.getElementById("provstatus" + reportId).innerHTML = data;
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
                document.getElementById("provstatus" + reportId).innerHTML = data;
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
                document.getElementById("subclassstatus" + reportId).innerHTML = data;
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
                document.getElementById("descriptionstatus" + reportId).innerHTML = data;
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
    document.getElementById('chooseCategory_' + reportId).style.display = '';
    document.getElementById('showCategory_' + reportId).style.display = 'none';
}

function updateCategory(reportId) {
    var categoryId = document.getElementById('selectedCategory_' + reportId).value;
    var categoryName = document.getElementById('selectedCategory_' + reportId).options[document.getElementById('selectedCategory_' + reportId).selectedIndex].text;
    if (categoryId) {
        jQuery.ajax({
            type: "POST",
            url: contextpath + "/hospitalReportManager/Modify.do",
            data: "method=updateCategory&reportId=" + reportId + "&categoryId=" + categoryId,
            success: function (data) {
                if (data != null) {
                    if (data.indexOf('Success') !== -1) {
                        document.getElementById('hrmCategory_' + reportId).innerHTML = document.getElementById('selectedCategory_' + reportId).innerHTML = categoryName;
                        document.getElementById('chooseCategory_' + reportId).style.display = 'none';
                        document.getElementById('showCategory_' + reportId).style.display = '';
                        toggleButtonBar(false, reportId);
                    }
                }
            }
        });
    }

}

function setupHrmDemoAutoCompletion(docId) {
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

                addDemoToHrm(docId);
                return false;
            }
        });
    }
}
