function popup(vheight, vwidth, varpage, windowname) {
    if (!windowname)
        windowname = "helpwindow";

    var page = varpage;
    windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes";
    var win = window.open(varpage, windowname, windowprops);
}


(function ($) {
// VERTICALLY ALIGN FUNCTION
    $.fn.vAlign = function () {
        return this.each(function (i) {
            var ah = $(this).height();
            var ph = $(this).parent().height();
            var mh = (ph - ah) / 2;
            $(this).css('margin-top', mh);
        });
    };
})(jQuery);

function resizeUl() {
    var sum = 0;

    sum = 0;
    $("#builder li").each(function () {
        sum += $(this).width();
    });
    if (sum == 0) $("#builder").width('99%');
    else $("#builder").width(sum + 40);
}

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
    var parent = $(e).parent();
    var preview = parent.find("img.page, canvas").get(0);
    if (!preview || (preview.tagName === 'IMG' && (!preview.complete || !preview.naturalWidth))) return;
    // Reuse the already decoded image; the legacy plugin otherwise draws a
    // newly allocated Image before its asynchronous load has completed.
    if (preview.tagName === 'IMG') preview.oImage = preview;
    $(preview).rotateRight();
    var rotated = parent.find("canvas, img.page").get(0);
    // The plugin replaces the image with a canvas whose oImage still uses this
    // blob on subsequent rotations. Transfer ownership before the DOM sweep.
    if (rotated && window.CarlosDocumentImages) CarlosDocumentImages.retain(preview, rotated);

    var r = parseInt($(e).attr("rotate"), 10);
    r = Number.isFinite(r) ? r : 0;
    $(e).attr("rotate", (r + 90) % 360);
    parent.addClass("rotated");
    parent.vAlign();
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
        var selectedr = $(selected).find("img, canvas");
        if ($(selectedr).length > 0) {
            for (var s = 0; s < $(selectedr).length; s++)
                _remove(selectedr[s]);
        }
    });

    $("#tool_rotate").click(function (e) {
        if ($(selected).length > 0) {
            for (var s = 0; s < $(selected).length; s++)
                _rotater(selected[s]);
        }
    });

    $("#tool_savecontinue").click(function (e) {
        $("#builder").children().each(function () {
            var num = $(this).find("span.num").first().text();
            var rotate = $(this).find("div").attr("rotate");
            $(this).attr("id", "page_" + num + "," + rotate);
        });

        $("#builder").sortable();
        var serialized = $("#builder").sortable('serialize', {key: 'page'});
        serialized = encodeURI(serialized);
        $("#builder").sortable("destroy");
        $("#builder").empty();

        var docnum = $("#document_no").attr('value');
        var queueId = $("#queueID").attr('value');
        var demoName = $("#demoName").attr('value');

        $("#tool_savecontinue span").html("Wait...");

        $.ajax({
            type: 'POST',
            url: ctx + '/documentManager/SplitDocument',
            data: serialized + '&method=split&document=' + docnum + '&queueID=' + queueId,
            dataType: 'json',
            success: function (data) {
                $("#tool_savecontinue span").html("Save &amp; Continue");
                popup(screen.height, screen.width, ctx + "/documentManager/ViewShowDocument?segmentID=" + data["newDocNum"] + '&demoName=' + encodeURIComponent(demoName) + "&inWindow=true", "assignDoc");
                return false;
            }
        });
    });

    $("#tool_done").click(function (e) {

        if (confirm("Are you sure want to exit?")) {
            window.close();
        }

    });

    $(".jog-control").find(">:first-child").click(function (e) {
        // "prev" button
        var li = $(this).parent().parent().parent();
        var index = $(li).index();
        if (index != 0) {
            $("#builder li:eq(" + (index - 1) + ")").before(li);
        }
    });

    $(".jog-control").find(">:first-child").next().click(function (e) {
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
