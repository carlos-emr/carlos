var docDescriptions = new Array();

var highlightMatch = function (full, snippet, matchIndex) {
    return full.substring(0, matchIndex) +
        "<span class='match'>" +
        full.substr(matchIndex, snippet.length) +
        "</span>" +
        full.substring(matchIndex + snippet.length);
};

var formatDocumentDescriptionResults = function (oResultData, sQuery, sResultMatch) {
    var query = sQuery.toLowerCase(),
        fname = oResultData[1],
        lname = oResultData[2],
        fnameMatchIndex = fname.toLowerCase().indexOf(query),
        lnameMatchIndex = lname.toLowerCase().indexOf(query),

        displayfname, displaylname;

    if (fnameMatchIndex > -1) {
        displayfname = highlightMatch(fname, query, fnameMatchIndex);
    } else {
        displayfname = fname;
    }

    if (lnameMatchIndex > -1) {
        displaylname = highlightMatch(lname, query, lnameMatchIndex);
    } else {
        displaylname = lname;
    }

    return displayfname + " " + displaylname;


};

function setupDocDescriptionTypeahead(docId) {
    if (jQuery("#docDesc_" + docId)) {

        var url = window.contextpath + "/documentManager/ManageDocument.do?method=searchDocumentDescriptions";

        jQuery("#docDesc_" + docId).autocomplete({
            source: url,
            minLength: 2,

            focus: function (event, ui) {
                jQuery("#docDesc_" + docId).val(ui.item.label);

                return false;
            },
            select: function (event, ui) {
                jQuery("#docDesc_" + docId).val(ui.item.label);

                docDescriptions.push(ui.item.label);

                jQuery('.demographic_link_button').removeAttr('disabled');
                return false;
            }
        });
    }
}

function checkDesc() {
    var curVal = $('docDesc_' + docId).value;
    var isCurValValid = false;
    for (var i = 0; i < docDescriptions.length; i++) {
        if (curVal == docDescriptions[i]) {
            isCurValValid = true;
            break;
        }
    }
}
