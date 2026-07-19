// No imports needed for this file

function saveNoteDialog() {
    jQuery.ajax({
        method: "POST", // Add this line
        url: ctx + '/CaseManagementEntry',
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