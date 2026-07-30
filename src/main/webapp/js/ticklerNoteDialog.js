/**
 * Shared tickler-note dialog logic for ticklerMain.jsp (Tickler Manager) and
 * ticklerDemoMain.jsp (Schedule-view tickler popup). Both pages render the same
 * #note-form dialog markup and call these as global functions from onclick attributes.
 *
 * ticklerGetNote returns {} (not null) when a tickler has no note yet, so callers
 * must only assign a DOM field when the corresponding response property is present -
 * otherwise assigning `undefined` stringifies into the literal text "undefined".
 */

function resetTicklerNoteFields() {
    document.getElementById('tickler_note_noteId').value = '';
    document.getElementById('tickler_note').value = '';
    document.getElementById('tickler_note_revision').textContent = '';
    document.getElementById('tickler_note_revision_url').setAttribute('onclick', '');
    document.getElementById('tickler_note_editor').textContent = '';
    document.getElementById('tickler_note_obsDate').textContent = '';
}

function applyTicklerNoteFields(data, contextPath) {
    if (data == null) {
        return;
    }
    if (data.noteId != null) {
        document.getElementById('tickler_note_noteId').value = data.noteId;
        document.getElementById('tickler_note_revision_url').setAttribute('onclick',
            "window.open('" + contextPath + "/CaseManagementEntry?method=notehistory&noteId=" + encodeURIComponent(data.noteId) + "')");
    }
    if (data.note != null) {
        document.getElementById('tickler_note').value = data.note;
    }
    if (data.revision != null) {
        document.getElementById('tickler_note_revision').textContent = data.revision;
    }
    if (data.editor != null) {
        document.getElementById('tickler_note_editor').textContent = data.editor;
    }
    if (data.obsDate != null) {
        document.getElementById('tickler_note_obsDate').textContent = data.obsDate;
    }
}
