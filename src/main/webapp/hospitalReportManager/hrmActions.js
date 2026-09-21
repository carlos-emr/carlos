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
    // Left undefined when the server did not say. The Inboxhub reads absent as "one row" and
    // zero as "none", so the two must not be conflated on the way through.
    var clearedCount = (data && typeof data.clearedCount === 'number') ? data.clearedCount : undefined;
    return {success: success, message: message, clearedCount: clearedCount};
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

        // A previous attempt may have left its reason in signoffstatus; a successful revoke keeps
        // the page open, so without this the clinician reads a stale failure beside a button that
        // now says the opposite.
        showHrmStatus("signoffstatus" + reportId, "");

        // Keep the button truthful first: on the inline inbox card and on any window that does
        // not close, this is the only feedback the clinician gets.
        updateSignOffButton(reportId, isSign);

        if (!isSign) {
            return;
        }

        notifyInboxhubAfterHrmSignOff(reportId, result.clearedCount);
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
 * Tells the inbox a report has been signed off: broadcast first, direct call as the fallback.
 *
 * The BroadcastChannel is the primary route. It reaches the Inboxhub's own listener, and it is
 * the only route that survives Cross-Origin-Opener-Policy, which severs window.opener.
 *
 * The direct call then covers only what the broadcast cannot reach:
 *   - the legacy oscarMDS inbox, which has no listener on that channel at all;
 *   - any inbox at all when BroadcastChannel is unavailable.
 *
 * A modern Inboxhub that heard the broadcast is deliberately NOT also driven directly. Doing
 * both notified it twice, and the second notification was not free: see dropFromInboxhubDirectly
 * for how the first one's re-fetch clears the handled record and makes the listener re-fetch too.
 *
 * The id and type travel together because segment ids are not unique across report types.
 *
 * @param {string} reportId HRM document id that was signed off
 * @param {number} clearedCount routing rows the SERVER reported taking out of the inbox. The
 *                 badges count routing rows, so this must not be guessed: zero is a real answer
 *                 (the report was already signed off, or had no row in this inbox) and moving the
 *                 badge anyway walks it below the truth until a full page reload.
 */
function notifyInboxhubAfterHrmSignOff(reportId, clearedCount) {
    // Broadcast FIRST, then tell directly only what the broadcast cannot reach.
    //
    // Doing both unconditionally notified a modern Inboxhub twice. With unloaded pages left,
    // dropAcknowledgedInboxhubItem answers false, so the direct route re-fetched; that calls
    // resetDataPageCount(), which calls forgetHandledInboxhubItems(); the broadcast listener
    // then found no handled marker, answered false in its turn and started a SECOND full
    // fetch. Two whole searches for one sign-off, which is the cost #3826 removed.
    var broadcast = false;
    try {
        const bc = new BroadcastChannel('inboxhub-refresh');
        bc.postMessage({
            action: 'refresh',
            segmentID: String(reportId),
            labType: 'HRM',
            clearedCount: clearedCount
        });
        bc.close();
        broadcast = true;
    } catch (e) {
        // BroadcastChannel unsupported; the direct route below is all there is.
    }
    dropFromInboxhubDirectly(reportId, clearedCount, broadcast);
}

/**
 * Reaches the inbox window directly.
 *
 * The inbox is either the window that opened this popup, the window this document is framed in,
 * or — for the legacy inline preview — this very window.
 *
 * The legacy oscarMDS inbox is refreshed, never asked to remove a row. Its removeReport() takes a
 * single id, ignores any type argument, and deletes "#labdoc_<id>"; Page.jsp reuses that
 * unqualified id for document, HL7 and HRM rows alike, and those key sequences are independent, so
 * a colliding id removes whichever row comes first in the document. Taking another patient's
 * report off a clinician's screen as though it had been dealt with is far worse than leaving this
 * one visible until the list reloads — the server already filters signed-off reports out of it.
 * refreshCategoryList() re-fetches the counts, which is the part that cannot wait.
 */
function dropFromInboxhubDirectly(reportId, clearedCount, broadcastSent) {
    var segmentId = String(reportId);
    try {
        // The Inboxhub can be told exactly which item and how many rows; it is preferred wherever
        // it is reachable. The legacy inbox can only be refreshed (see above).
        var inbox = null;
        var legacyInbox = false;
        if (self.opener && typeof self.opener.dropAcknowledgedInboxhubItem === 'function') {
            inbox = self.opener;
        } else if (window.parent !== window
                && typeof window.parent.dropAcknowledgedInboxhubItem === 'function') {
            inbox = window.parent;
        } else if (self.opener && typeof self.opener.refreshCategoryList === 'function') {
            inbox = self.opener;
            legacyInbox = true;
        } else if (window.parent !== window
                && typeof window.parent.refreshCategoryList === 'function') {
            inbox = window.parent;
            legacyInbox = true;
        } else if (typeof window.refreshCategoryList === 'function') {
            // The legacy oscarMDS preview <jsp:include>s this viewer straight into the inbox page,
            // so the inbox IS this window: no opener, and window.parent is itself. Checked last so
            // a popup or a framed card never matches it. Without this the card was hidden but the
            // legacy counts kept counting a report already signed off.
            inbox = window;
            legacyInbox = true;
        }
        if (!inbox) {
            return false;
        }
        // The legacy oscarMDS inbox has no listener on that channel, so it is told directly
        // whether or not the broadcast went out. A modern Inboxhub that already heard it must
        // NOT be driven again: its listener does the whole job, including the re-fetch.
        if (broadcastSent && !legacyInbox) {
            return true;
        }
        var handledInPlace = false;
        if (legacyInbox) {
            inbox.refreshCategoryList();
        } else {
            // The return value says whether the inbox dealt with the item AND needs no
            // re-sync; it answers false while unloaded pages remain, because the inbox pages
            // by offset and an acknowledgement shifts every later result up a place. The
            // condition lives in that function's contract so this route, labDisplay.jsp's
            // and the BroadcastChannel listener cannot drift apart. An Inboxhub from before
            // the return value existed answers undefined and falls through to the re-fetch,
            // exactly as this code used to do unconditionally.
            handledInPlace = inbox.dropAcknowledgedInboxhubItem(segmentId, 'HRM', clearedCount) === true;
        }
        // Only when the inbox could not finish the job itself. Re-fetching on top of an
        // in-place drop re-runs the whole search: it costs the clinician their place in the
        // list, discards every page after the first, and in preview mode reloads every
        // remaining card's iframe. The legacy inbox has no fetchInboxhubData at all, so its
        // refreshCategoryList() above is the whole of its refresh.
        if (!handledInPlace && typeof inbox.fetchInboxhubData === 'function') {
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
    var card = document.getElementById('hrmdoc_' + reportId);
    // window.opener is NOT a good enough test for "the inbox opened this": ticklerMain,
    // ticklerDemoMain and the eChart's HRM shortcut all open this route in a popup too, and
    // closing those on sign-off takes away the revoke affordance they rely on. The inbox links
    // say so explicitly with inWindow=true, which the page republishes here.
    //
    // The marker is the WHOLE test. Also requiring window.opener reintroduced the COOP case the
    // broadcast exists for: a severed opener left a signed-off report sitting open, since it is
    // neither framed nor inline and so matched nothing below either. window.close() is a no-op
    // on a top-level page the script did not open, so the marker alone is still the guard the
    // tickler and eChart popups need — they never carry it.
    if (card && card.getAttribute('data-inbox-window') === 'true') {
        window.close();
        return;
    }
    if (card && card.getAttribute('data-inbox-inline') === 'true') {
        card.style.display = 'none';
    }
}

function makeIndependent(reportId) {
    hrmModify({method: "makeIndependent", reportId: reportId}, function (result) {
        if (!result.success) {
            // Its own element, not similarNotice<id>: that span holds the whole list of similar
            // reports, and overwriting it with an error would delete what the clinician is
            // reading. Silence here read as "it worked".
            showHrmStatus("similarstatus" + reportId, result.message);
            return;
        }
        showHrmStatus("similarstatus" + reportId, "");
        showHrmStatus("similarNotice" + reportId, "");
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
        // Nothing is cleared until the unlink is known to have landed. This container holds the
        // patient's name AND the (remove) link, so emptying it first left a failed unlink with
        // the database link still in place, no name on screen, no way to retry, and the
        // autocomplete still hidden — the clinician had to reload to get back to where they were.
        if (!result.success) {
            var failureNotice = container.querySelector('.hrm-demo-status');
            if (!failureNotice) {
                failureNotice = document.createElement('div');
                failureNotice.className = 'hrm-demo-status';
                container.appendChild(failureNotice);
            }
            failureNotice.textContent = result.message;
            return;
        }
        container.textContent = '';
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
                // The chooser stays open on a failure, so say why rather than leaving the
                // clinician looking at a picker that appears to have done nothing.
                showHrmStatus("categorystatus" + reportId, result.message);
                return;
            }
            showHrmStatus("categorystatus" + reportId, "");
            // Only the read-only label is rewritten. Setting textContent on the <select> itself
            // destroyed its <option> children, so the picker came back empty on the next edit.
            document.getElementById('hrmCategory_' + reportId).textContent = categoryName;
            document.getElementById('chooseCategory_' + reportId).style.display = 'none';
            document.getElementById('showCategory_' + reportId).style.display = '';
            // Deliberately NOT toggleButtonBar(false, ...): filing a report under a category says
            // nothing about its patient link, and disabling Msg/Tickler/eChart/Master/Appt History
            // here took those actions away from a report that was still linked.
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
