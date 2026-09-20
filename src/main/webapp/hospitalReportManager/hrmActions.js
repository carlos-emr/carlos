/*
 * CARLOS EMR — Hospital Report Manager (HRM) report viewer actions.
 *
 * Every call here posts to /hospitalReportManager/Modify, which replies with
 * {"success": <boolean>, "message": "<display text>"} as application/json.
 *
 * The JSON contract is load-bearing, not a style choice. The endpoint used to forward to a
 * text/html JSP fragment and these handlers wrote the raw response body into the page; the
 * response-decorating filter chain appends a <script> block to text/html responses, so a
 * clinician adding a comment saw that JavaScript rendered as literal text beside the comment
 * box while the comment itself saved. Those filters skip non-HTML responses, so keep the reply
 * JSON and keep rendering only `message` — never the response body — into the DOM.
 */

/**
 * Normalises a reply into the {success, message} shape the handlers below expect.
 *
 * A caller that reaches the error path has no parsed body, and an older cached page can still
 * be talking to a build that answered in some other shape; both must degrade to a visible
 * status rather than an empty box.
 *
 * @param {Object} data parsed JSON reply, or null/undefined on transport failure
 * @param {string} fallbackMessage text to show when the reply carried none
 * @return {{success: boolean, message: string}} status for display
 */
function hrmResult(data, fallbackMessage) {
    var success = !!(data && data.success);
    var message = (data && typeof data.message === 'string' && data.message.length > 0)
        ? data.message
        : fallbackMessage;
    return {success: success, message: message};
}

/**
 * Writes a status message beside the control that was used.
 *
 * textContent, never innerHTML: the message is server text and must never be parsed as markup.
 */
function showHrmStatus(elementId, message) {
    var el = document.getElementById(elementId);
    if (el) {
        el.textContent = message;
    }
}

/** Shared POST shape for the HRM modify endpoint. */
function hrmModify(data, onResult) {
    jQuery.ajax({
        type: "POST",
        url: contextpath + "/hospitalReportManager/Modify",
        data: data,
        dataType: "json",
        success: function (reply) {
            onResult(hrmResult(reply, "Error encountered"));
        },
        error: function (xhr, status, err) {
            console.error('HRM modify request failed:', status, err);
            onResult(hrmResult(null, "Error encountered - please try again"));
        }
    });
}

function addComment(reportId) {
    let comment = jQuery("#commentField_" + reportId + "_hrm").val();
    hrmModify({method: "addComment", reportId: reportId, comment: comment}, function (result) {
        showHrmStatus("commentstatus" + reportId, result.message);
    });
}

function deleteComment(commentId, reportId) {
    hrmModify({method: "deleteComment", commentId: commentId}, function (result) {
        showHrmStatus("commentstatus" + reportId, result.message);
    });
}

/**
 * Signs off (or revokes sign-off on) one HRM report.
 *
 * Sign-off has to reach the inbox that the report was opened from, because that inbox filters
 * on the sign-off flag server-side: leave it un-notified and the clinician sees the report still
 * sitting in their list, which reads as "sign-off did nothing". This used to be gated on a
 * `listView` flag the inbox never actually sent (the Inboxhub link carries no `listView`
 * parameter, and oscarMDS/Page.jsp sends `isListView`, which the action's criteria object does
 * not bind), so the notification branch was dead and only the button label changed.
 *
 * Revoking deliberately does NOT notify: the report belongs back in the inbox.
 *
 * @param {string} reportId HRM document id
 * @param {boolean} isSign true to sign off, false to revoke a previous sign-off
 */
function doSignOff(reportId, isSign) {
    var data = "method=signOff&signedOff=" + (isSign ? "1" : "0") + "&reportId=" + encodeURIComponent(reportId);

    hrmModify(data, function (result) {
        if (!result.success) {
            showHrmStatus("signoffstatus" + reportId, result.message);
            return;
        }

        // Keep the button truthful first: on the inline inbox card and on any window that does
        // not close, this is the only feedback the clinician gets.
        updateSignOffButton(reportId, isSign);

        if (!isSign) {
            return;
        }

        notifyInboxhubAfterHrmSignOff(reportId);
        closeOrHideHrmReport(reportId);
    });
}

/** Flips the sign-off button between "Sign-Off" and "Revoke Sign-Off". */
function updateSignOffButton(reportId, isSign) {
    var signOffButton = document.getElementById('signoff' + reportId);
    if (!signOffButton) {
        return;
    }
    signOffButton.value = isSign ? "Revoke Sign-Off" : "Sign-Off";
    // Assigning the handler rather than writing a javascript: string into an onClick attribute:
    // an attribute rebuilt from string concatenation is both an injection sink and the first
    // thing a Content-Security-Policy without 'unsafe-inline' silently stops working.
    signOffButton.onclick = function () {
        if (isSign) {
            revokeSignOffHrm(reportId);
        } else {
            signOffHrm(reportId);
        }
    };
}

/**
 * Tells the inbox a report has been signed off, by BOTH routes.
 *
 * Both, deliberately: the Inboxhub listens on the BroadcastChannel, while the legacy oscarMDS
 * inbox has no listener at all and can only be reached through window.opener.removeReport().
 * A report opened from either generation has to leave the list, and this viewer cannot tell
 * which one opened it. BroadcastChannel is also the only route that survives
 * Cross-Origin-Opener-Policy, which severs window.opener.
 *
 * Being told twice is safe: the Inboxhub keys counter adjustments per item so the badge moves
 * once, and removing a row that has already gone is a no-op.
 *
 * The id and type travel together because segment ids are not unique across report types.
 */
function notifyInboxhubAfterHrmSignOff(reportId) {
    dropFromInboxhubDirectly(reportId);
    try {
        const bc = new BroadcastChannel('inboxhub-refresh');
        bc.postMessage({action: 'refresh', segmentID: String(reportId), labType: 'HRM'});
        bc.close();
    } catch (e) {
        // BroadcastChannel unsupported; the direct route above is all there is.
    }
}

/**
 * Reaches the inbox window directly.
 *
 * The inbox is either the window that opened this popup or, for the inline/preview shape, the
 * window this document sits inside. An older inbox has only removeReport() and cannot be told a
 * count, so it drops the row and takes one off the badge — short of exact, and far better than
 * leaving a signed-off report on screen.
 */
function dropFromInboxhubDirectly(reportId) {
    var segmentId = String(reportId);
    try {
        var inbox = null;
        var legacyInbox = false;
        if (self.opener && typeof self.opener.dropAcknowledgedInboxhubItem === 'function') {
            inbox = self.opener;
        } else if (window.parent !== window
                && typeof window.parent.dropAcknowledgedInboxhubItem === 'function') {
            inbox = window.parent;
        } else if (self.opener && typeof self.opener.removeReport === 'function') {
            inbox = self.opener;
            legacyInbox = true;
        } else if (window.parent !== window && typeof window.parent.removeReport === 'function') {
            inbox = window.parent;
            legacyInbox = true;
        }
        if (!inbox) {
            return false;
        }
        if (legacyInbox) {
            inbox.removeReport(segmentId, 'HRM');
        } else {
            inbox.dropAcknowledgedInboxhubItem(segmentId, 'HRM');
        }
        if (typeof inbox.fetchInboxhubData === 'function') {
            inbox.fetchInboxhubData();
        }
        return true;
    } catch (e) {
        // No reachable inbox window (COOP, or a cross-origin opener).
        return false;
    }
}

/**
 * Gets the signed-off report off the screen, in whichever of its three shapes it is being shown.
 *
 * The Inboxhub's view mode frames each report in a .document-card, the inbox opens it as a popup
 * from list mode, and the legacy oscarMDS preview <jsp:include>s the viewer straight into the
 * page. Only the popup has a window to close; window.close() is a no-op in the other two, which
 * is why signing off from a card used to look like nothing had happened.
 *
 * The inline case is marked server-side rather than guessed at, so a report opened as an
 * ordinary top-level page is left alone rather than blanked.
 */
function closeOrHideHrmReport(reportId) {
    if (window.frameElement) {
        var frameCard = window.frameElement.closest('.document-card.card');
        if (frameCard) {
            frameCard.style.display = 'none';
        }
        return;
    }
    if (window.opener && window.opener !== window) {
        window.close();
        return;
    }
    var card = document.getElementById('hrmdoc_' + reportId);
    if (card && card.getAttribute('data-inbox-inline') === 'true') {
        card.style.display = 'none';
    }
}

function makeIndependent(reportId) {
    hrmModify({method: "makeIndependent", reportId: reportId}, function (result) {
        if (result.success) {
            showHrmStatus("similarNotice", "");
        }
    });
}

function addDemoToHrm(reportId) {
    var demographicNo = document.getElementById("demofind" + reportId + "hrm").value;
    hrmModify({method: "assignDemographic", reportId: reportId, demographicNo: demographicNo},
        function (result) {
            var container = document.getElementById("demostatus" + reportId);
            if (!container) {
                return;
            }
            container.textContent = '';
            if (!result.success) {
                container.textContent = result.message;
                return;
            }
            container.appendChild(document.createTextNode(result.message));
            container.appendChild(document.createElement('br'));
            var demoName = document.getElementById('autocompletedemo' + reportId + 'hrm').value.split('(')[0];
            container.appendChild(document.createTextNode(demoName));
            var removeLink = document.createElement('a');
            removeLink.href = '#';
            removeLink.textContent = '(remove)';
            removeLink.addEventListener('click', function (e) {
                e.preventDefault();
                removeDemoFromHrm(reportId);
            });
            container.appendChild(removeLink);
            document.getElementById('autocompletedemo' + reportId + 'hrm').style.display = 'none';
            toggleButtonBar(true, reportId);
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
    hrmModify({method: "removeDemographic", reportId: reportId}, function (result) {
        var container = document.getElementById("demostatus" + reportId);
        if (!container) {
            return;
        }
        container.textContent = '';
        if (!result.success) {
            container.textContent = result.message;
            return;
        }
        container.appendChild(document.createTextNode(result.message));
        container.appendChild(document.createElement('br'));
        var italic = document.createElement('i');
        italic.textContent = 'Not currently linked';
        container.appendChild(italic);
        document.getElementById('autocompletedemo' + reportId + 'hrm').value = "";
        document.getElementById('autocompletedemo' + reportId + 'hrm').style.display = '';
        document.getElementById('demofind' + reportId + 'hrm').value = null;
        toggleButtonBar(false, reportId);
    });
}

function addProvToHrm(reportId, providerNo) {
    hrmModify({method: "assignProvider", reportId: reportId, providerNo: providerNo},
        function (result) {
            showHrmStatus("provstatus" + reportId, result.message);
        });
}

function removeProvFromHrm(mappingId, reportId) {
    hrmModify({method: "removeProvider", providerMappingId: mappingId}, function (result) {
        showHrmStatus("provstatus" + reportId, result.message);
    });
}

function makeActiveSubClass(reportId, subClassId) {
    hrmModify({method: "makeActiveSubClass", reportId: reportId, subClassId: subClassId},
        function (result) {
            showHrmStatus("subclassstatus" + reportId, result.message);
            // Reload only once the server has answered. Reloading alongside the request raced it,
            // so the page could redraw from the pre-change row and show the old active sub-class.
            if (result.success) {
                window.location.reload();
            }
        });
}


function printHrm(hrmReportId) {
    window.location = contextpath + "/hospitalReportManager/PrintHRMReport?segmentId=" + hrmReportId + "&hrmReportId=" + hrmReportId;
}

function setDescription(reportId) {
    let description = jQuery("#descriptionField_" + reportId + "_hrm").val();
    hrmModify({method: "setDescription", reportId: reportId, description: description},
        function (result) {
            showHrmStatus("descriptionstatus" + reportId, result.message);
        });
}

function signOffHrm(reportId) {
    doSignOff(reportId, true);
}

function revokeSignOffHrm(reportId) {
    doSignOff(reportId, false);
}

function editCategory(reportId) {
    document.getElementById('chooseCategory_' + reportId).style.display = '';
    document.getElementById('showCategory_' + reportId).style.display = 'none';
}

function updateCategory(reportId) {
    var select = document.getElementById('selectedCategory_' + reportId);
    var categoryId = select.value;
    var categoryName = select.options[select.selectedIndex].text;
    if (!categoryId) {
        return;
    }
    hrmModify({method: "updateCategory", reportId: reportId, categoryId: categoryId},
        function (result) {
            if (!result.success) {
                return;
            }
            // Only the read-only label is rewritten. Setting textContent on the <select> itself
            // destroyed its <option> children, so the picker came back empty on the next edit.
            document.getElementById('hrmCategory_' + reportId).textContent = categoryName;
            document.getElementById('chooseCategory_' + reportId).style.display = 'none';
            document.getElementById('showCategory_' + reportId).style.display = '';
            toggleButtonBar(false, reportId);
        });
}

function setupHrmDemoAutoCompletion(docId) {
    if (jQuery("#autocompletedemo" + docId + "hrm")) {

        let searchDemoUrl = window.contextpath + "/demographic/SearchDemographic";
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

                addDemoToHrm(docId);
                return false;
            }
        });
    }
}
