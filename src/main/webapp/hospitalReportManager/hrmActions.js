function addComment(reportId) {
    let comment = jQuery("#commentField_" + reportId + "_hrm").val();
    let data = {method: "addComment", reportId: reportId, comment: comment};
    jQuery.ajax({
        type: "POST",
        url: contextpath + "/hospitalReportManager/Modify.do",
        data: data,
        success: function (data) {
            if (data != null)
                document.getElementById("commentstatus" + reportId).textContent = data;
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
                document.getElementById("commentstatus" + reportId).textContent = data;
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
                // Remove the signed-off report from the opener's table if accessible.
                // window.opener is null when Inboxhub is the opener due to Struts 7's
                // CoopInterceptor setting Cross-Origin-Opener-Policy: same-origin.
                if (self.opener && typeof self.opener.removeReport === 'function') {
                    self.opener.removeReport(reportId);
                }
                // Notify the Inboxhub to refresh its data after sign-off.
                // BroadcastChannel provides reliable same-origin cross-window messaging
                // that is unaffected by COOP headers.
                try {
                    const bc = new BroadcastChannel('inboxhub-refresh');
                    bc.postMessage('refresh');
                    bc.close();
                } catch (e) {
                    // BroadcastChannel unsupported — user must manually refresh the inbox
                }
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
                var container = document.getElementById("demostatus" + reportId);
                container.textContent = '';
                container.appendChild(document.createTextNode(data));
                container.appendChild(document.createElement('br'));
                var demoName = document.getElementById('autocompletedemo' + reportId + 'hrm').value.split('(')[0];
                container.appendChild(document.createTextNode(demoName));
                var removeLink = document.createElement('a');
                removeLink.href = '#';
                removeLink.textContent = '(remove)';
                removeLink.addEventListener('click', function(e) { e.preventDefault(); removeDemoFromHrm(reportId); });
                container.appendChild(removeLink);
                document.getElementById('autocompletedemo' + reportId + 'hrm').style.display = 'none';
                toggleButtonBar(true, reportId);
            }
        },
        error: function (xhr, status, err) {
            console.error('Failed to assign demographic to HRM report:', status, err);
            var container = document.getElementById("demostatus" + reportId);
            container.textContent = 'Error: could not assign patient. Please try again.';
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
                var container = document.getElementById("demostatus" + reportId);
                container.textContent = '';
                container.appendChild(document.createTextNode(data));
                container.appendChild(document.createElement('br'));
                var italic = document.createElement('i');
                italic.textContent = 'Not currently linked';
                container.appendChild(italic);
                document.getElementById('autocompletedemo' + reportId + 'hrm').value = "";
                document.getElementById('autocompletedemo' + reportId + 'hrm').style.display = '';
                document.getElementById('demofind' + reportId + 'hrm').value = null;
                toggleButtonBar(false, reportId);
            }
        },
        error: function (xhr, status, err) {
            console.error('Failed to remove demographic from HRM report:', status, err);
            var container = document.getElementById("demostatus" + reportId);
            container.textContent = 'Error: could not remove patient link. Please try again.';
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
                document.getElementById("provstatus" + reportId).textContent = data;
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
                document.getElementById("provstatus" + reportId).textContent = data;
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
                document.getElementById("subclassstatus" + reportId).textContent = data;
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
                document.getElementById("descriptionstatus" + reportId).textContent = data;
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
                        document.getElementById('hrmCategory_' + reportId).textContent = document.getElementById('selectedCategory_' + reportId).textContent = categoryName;
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

        let searchDemoUrl = window.contextpath + "/demographic/SearchDemographic.do";
        let activeOnly = jQuery("#activeOnly" + docId + "hrm").is(":checked");

        jQuery("#autocompletedemo" + docId + "hrm").autocomplete({
            source: function (req, res) {
                jQuery.ajax({
                    url: searchDemoUrl,
                    type: 'POST',
                    data: { jqueryJSON: 'true', activeOnly: activeOnly ? 'true' : 'false', term: req.term },
                    success: function (data) { res(data); },
                    error: function () { res([]); }
                });
            },
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
