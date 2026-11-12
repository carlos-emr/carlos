/**
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
package io.github.carlos_emr.carlos.casemgmt.common;

import java.util.regex.Pattern;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;

/**
 * What a chart note's audit row carries in the {@code data} column of the {@code log} table.
 *
 * <p>The row already names the note ({@code contentId}), the patient ({@code demographic_no}), the
 * provider and the client address. {@code data} used to be
 * {@link CaseManagementNote#getAuditString()}: the whole note text followed by the descriptions of
 * its issues, so the audit log held a second copy of the clinical record that every audit viewer
 * could read and that outlived the chart's own access rules. An audit row has to say <em>that</em> a
 * note was read or written, not carry the note.</p>
 *
 * <p>This is the replacement: the note's thread id (the {@code uuid} its revisions share, since
 * each save stores a new {@code note_id}), whether the revision is signed, and how many issues it
 * is filed under. Nothing a clinician typed.</p>
 *
 * @since 2026-11-12
 */
public final class NoteAuditData {

    /** A generated note thread id; the column is plain text, so anything else is not copied. */
    private static final Pattern THREAD_ID = Pattern.compile("[0-9A-Za-z-]{1,64}");

    private NoteAuditData() {
    }

    /**
     * Describes one revision of a note for its audit row.
     *
     * @param note the revision read or written; {@code null} yields {@code null} (no data)
     * @return for example {@code uuid=0b4c...; signed=true; issues=2}; never the note text, its
     *         history or an issue description
     */
    public static String describe(CaseManagementNote note) {
        if (note == null) {
            return null;
        }
        StringBuilder data = new StringBuilder();
        String uuid = note.getUuid();
        if (uuid != null && THREAD_ID.matcher(uuid).matches()) {
            data.append("uuid=").append(uuid).append("; ");
        }
        data.append("signed=").append(note.isSigned());
        data.append("; issues=").append(note.getIssues() == null ? 0 : note.getIssues().size());
        return data.toString();
    }
}
