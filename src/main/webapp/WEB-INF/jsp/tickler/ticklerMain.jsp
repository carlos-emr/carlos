function saveNoteDialog() {
    jQuery.ajax({
        method: "POST",
        url: ctx + '/CaseManagementEntry',   // out-of-scope action
        data: {
            method: "ticklerSaveNote",
            noteId: ...,
            value: ...,           // clinical note text — PHI
            demographicNo: ...,   // patient ID
            ticklerNo: ...
        },
        async: false,
        ...
    });
}