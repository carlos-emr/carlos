/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

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
