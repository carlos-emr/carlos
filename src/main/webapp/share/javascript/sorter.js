function popup(vheight, vwidth, varpage, windowname) {
    if (!windowname)
        windowname = "helpwindow";

    var page = varpage;
    windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes";
    var win = window.open(varpage, windowname, windowprops);
}


function resizeUl() {
    var sum = 0;

    sum = 0;
    $("#builder li").each(function () {
        sum += $(this).width();
    });
    if (sum == 0) $("#builder").width('99%');
    else $("#builder").width(sum + 40);
}

var splitSaveController;
var selected = [];
var selectedTop = [];

function clearPreviousSelection() {
    if ($(selected).length > 0)
        $(selected).removeClass("selected");
    selected = [];
}

function clearPreviousTopSelection() {
    if ($(selectedTop).length > 0)
        $(selectedTop).removeClass("selected");
    selectedTop = [];
}

function _rotater(e) {
    if (splitSaveController && splitSaveController.isLocked()) return;
    var parent = $(e).parent();
    var preview = parent.find("img.page, canvas").get(0);
    if (!preview || (preview.tagName === 'IMG' && (!preview.complete || !preview.naturalWidth))) return;
    // Reuse the already decoded image; the legacy plugin otherwise draws a
    // newly allocated Image before its asynchronous load has completed.
    if (preview.tagName === 'IMG') {
        // Image.width depends on its responsive CSS while attached, then changes
        // after replacement. Keep one intrinsic-sized decoded source for every turn.
        var original = document.createElement('canvas');
        original.width = preview.naturalWidth;
        original.height = preview.naturalHeight;
        original.getContext('2d').drawImage(preview, 0, 0);
        preview.oImage = original;
    }
    $(preview).rotateRight();
    var rotated = parent.find("canvas, img.page").get(0);
    // The plugin replaces the image with a canvas whose oImage still uses this
    // blob on subsequent rotations. Transfer ownership before the DOM sweep.
    if (rotated && window.CarlosDocumentImages) CarlosDocumentImages.retain(preview, rotated);

    var r = parseInt($(e).attr("rotate"), 10);
    r = Number.isFinite(r) ? r : 0;
    $(e).attr("rotate", (r + 90) % 360);
    parent.addClass("rotated");
    resizeUl();
};

function _resizeui() {
    $("#buildercontainer").height($(window).height() - 80);
    $("#picker").height($(window).height());
}

function _zoom(d) {
    var preview = $(d).find('img.page, canvas').get(0);
    if (!preview || (preview.tagName === 'IMG' && (!preview.complete || !preview.naturalWidth))) return;
    var modal = $(preview).clone();
    if (preview.tagName === 'CANVAS') {
        modal.get(0).getContext('2d').drawImage(preview, 0, 0);
    }
    // Clone only this page, not all images with an equal src. Keep its decoded
    // resource alive if the original page is moved/removed while the dialog is open.
    if (window.CarlosDocumentImages) CarlosDocumentImages.retain(preview, modal.get(0));
    modal.dialog({
        height: $(window).height() - 40,
        modal: true,
        draggable: false,
        resizable: false,
        width: preview.naturalWidth || preview.width,
        close: function () { $(this).dialog('destroy').remove(); }
    });
}

function _remove(r) {
    if (splitSaveController && splitSaveController.isLocked()) return;

    if ($(r).parent().parent().parent().attr('id') == "builder") {
        var rem = $(r).parent();
        $("#picker").append($(rem).parent());

        clearPreviousSelection();
        _resetsortable();
    }
}

function _resetsortable() {

}

var selectionMode = "single";
var temp;

$(document).ready(function () {
    $(document).disableSelection();

    var saveStatus = $('<p id="split-save-status" role="status" aria-live="polite"></p>');
    var messages = window.CarlosDocumentMutationMessages || {};
    var splitToken = '', splitRevision = '';
    function csrfToken() { return $('input[name="CSRF-TOKEN"]').first().val() || ''; }
    var cancelSave = $('<button id="split-save-cancel" type="button" hidden></button>')
            .text(messages.cancel || 'Cancel waiting');
    cancelSave.on('click', function () { splitSaveController.cancelWaiting(); });
    $('#pickertoolscontainer').append(saveStatus, cancelSave);
    splitSaveController = window.CarlosDocumentMutation.create({
        setTimeout: function (callback, delay) { return window.setTimeout(callback, delay); },
        clearTimeout: function (timer) { window.clearTimeout(timer); },
        beforeSend: function () {
            return splitToken.length > 0 && csrfToken() === splitToken
                    && /^[0-9a-f]{64}$/.test(splitRevision) && $('#splitSourceRevision').val() === splitRevision;
        },
        send: async function (body) {
            // Pin both token channels. A later CSRFGuard/session refresh must not
            // silently attach a new user's token to an earlier clinical intent.
            var response = await fetch(ctx + '/documentManager/SplitDocument', {
                method: 'POST', credentials: 'same-origin', redirect: 'error',
                headers: {'Content-Type': 'application/x-www-form-urlencoded', 'CSRF-TOKEN': splitToken}, body: body
            });
            return {status: response.status, data: await response.json(), retryAfter: response.headers.get('Retry-After')};
        },
        isSuccess: function (data) {
            return Number.isSafeInteger(data.newDocNum) && data.newDocNum > 0 && data.newDocNum <= 2147483647;
        },
        onState: function (state) {
            var locked = state !== 'idle';
            $('#pickertools li:not(#tool_done)').attr('aria-disabled', String(locked));
            $('#builder').attr('aria-busy', String(state === 'pending' || state === 'waiting'));
            cancelSave.prop('hidden', state !== 'waiting');
            saveStatus.attr('role', state === 'uncertain' ? 'alert' : 'status');
            if (state === 'pending') saveStatus.text(messages.pending || 'Saving selected pages…');
            else if (state === 'idle') saveStatus.text('');
        },
        onWaiting: function () { saveStatus.text(messages.waiting || 'Waiting for document capacity. Your selected pages are preserved.'); },
        onRejected: function (data) {
            var key = data && data.sourceChanged ? 'sourceChanged' : data && data.local ? 'sourceUnavailable' : 'rejected';
            saveStatus.attr('role', 'alert').text(messages[key] || 'The document was not saved. Reload it and check the selected pages before trying again.');
        },
        onUncertain: function () {
            saveStatus.text(messages.uncertain || 'The save could not be confirmed. Your selected pages are preserved. Check the patient documents before saving another copy.');
        },
        onCancelled: function () { saveStatus.text(messages.cancelled || 'Save cancelled. Your selected pages are preserved.'); },
        onSuccess: function (data) {
            var documentId = data.newDocNum;
            $('#builder').empty();
            clearPreviousSelection();
            resizeUl();
            popup(screen.height, screen.width, ctx + '/documentManager/ViewShowDocument?segmentID=' + documentId
                    + '&demoName=' + encodeURIComponent($('#demoName').val()) + '&inWindow=true', 'assignDoc');
        }
    });
    window.addEventListener('pagehide', function () { splitSaveController.hide(); });
    window.addEventListener('pageshow', function () { splitSaveController.show(); });
    window.addEventListener('beforeunload', function (event) {
        if (splitSaveController.isLocked()) { event.preventDefault(); event.returnValue = ''; }
    });

    $("#picker img").wrap("<div rotate=0 />");

    $("#picker div").append(function (index, html) {
        return "<span class='num'>" + ++index + "</span>" +
            "<span class='jog-control'><img style='box-shadow: none; -webkit-box-shadow: none; -moz-box-shadow: none' src='" + ctx + "/images/icons/132.png' />" +
            "<img style='box-shadow: none; -webkit-box-shadow: none; -moz-box-shadow: none' src='" + ctx + "/images/icons/131.png' />" +
            "<img style='box-shadow: none; -webkit-box-shadow: none; -moz-box-shadow: none' src='" + ctx + "/images/icons/114.png' /></span>";
    });

    $(document).on("contextmenu", function (e) {
        return false;
    });

    $(window).resize(function () {
        _resizeui();
    });

    _resizeui();

    $("#picker div").click(function (e) {
        if (splitSaveController.isLocked()) return false;
        $(this).addClass("selected");
        if ($(this).parent().attr('id') == "builder")
            clearPreviousSelection();
        else if ($(this).parent().attr('id') == "picker")
            clearPreviousTopSelection();

        if (e.shiftKey) {
            selected.push(this);
        } else {
            clearPreviousSelection();
            selected = [this];
        }
    }).hover(function (e) {

    });

    $("#picker div").dblclick(function (e) {
        _zoom(this);
    });

    $("#tool_add").click(function (e) {
        if (splitSaveController.isLocked()) return false;
        if ($(selected).length > 0) {
            var selectedp = $(selected).parent();
            for (var s = 0; s < $(selectedp).length; s++)
                $("#builder").append(selectedp[s]);

            $("#picker").remove($(selected).parent());
            selected = [];
            $(".selected").removeClass("selected");

            resizeUl();
        }
    });

    $("#tool_remove").click(function (e) {
        if (splitSaveController.isLocked()) return false;
        var selectedr = $(selected).find("img, canvas");
        if ($(selectedr).length > 0) {
            for (var s = 0; s < $(selectedr).length; s++)
                _remove(selectedr[s]);
        }
    });

    $("#tool_rotate").click(function (e) {
        if (splitSaveController.isLocked()) return false;
        if ($(selected).length > 0) {
            for (var s = 0; s < $(selected).length; s++)
                _rotater(selected[s]);
        }
    });

    $("#tool_savecontinue").click(function (e) {
        if (splitSaveController.isLocked() || !$('#builder').children().length) return false;
        var fields = new URLSearchParams();
        $("#builder").children().each(function () {
            var num = $(this).find("span.num").first().text();
            var rotate = $(this).find("div").attr("rotate");
            fields.append('page', num + ',' + rotate);
        });
        fields.append('method', 'split');
        fields.append('document', $('#document_no').val());
        fields.append('queueID', $('#queueID').val() || '1');
        splitToken = csrfToken();
        splitRevision = $('#splitSourceRevision').val() || '';
        fields.append('CSRF-TOKEN', splitToken);
        fields.append('sourceRevision', splitRevision);
        splitSaveController.start(fields.toString());
    });

    $("#tool_done").click(function (e) {

        if (confirm("Are you sure want to exit?")) {
            window.close();
        }

    });

    $(".jog-control").find(">:first-child").click(function (e) {
        if (splitSaveController.isLocked()) return false;
        // "prev" button
        var li = $(this).parent().parent().parent();
        var index = $(li).index();
        if (index != 0) {
            $("#builder li:eq(" + (index - 1) + ")").before(li);
        }
    });

    $(".jog-control").find(">:first-child").next().click(function (e) {
        if (splitSaveController.isLocked()) return false;
        // "next" button
        var li = $(this).parent().parent().parent();
        var index = $(li).index();
        $("#builder li:eq(" + (index + 1) + ")").after(li);
    });

    $(".jog-control").find(">:first-child").next().next().click(function (e) {
        // "rotate" button
        var div = $(this).parent().parent();
        _rotater(div);
    });

    $(".jog-control").find("img").hover(function (e) {
        $(this).css("opacity", "1.0");
    }, function (e) {
        $(this).css("opacity", "0.5");
    });
});

$(window).on('load', resizeUl);
