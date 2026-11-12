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

/**
 * Names the session attributes in which a chart keeps its working state between requests, one
 * attribute per patient.
 *
 * <p>One login can have several patients' charts open at once, each in its own window. The chart
 * state that outlives a request used to sit in one attribute per login, so a request from one
 * chart read or replaced the state of whichever chart had last written it (issue #4673):</p>
 * <ul>
 *   <li>the editor's one-use filter handoff to the next note list: another chart's note list
 *       could consume it;</li>
 *   <li>the text the last save wrote into the legacy eChart record, which the next save removes
 *       before appending its own: a save after another patient's save looked for that patient's
 *       text in this patient's record;</li>
 *   <li>the editor's "a draft is being restored" signal to the chart view: another chart's view
 *       could consume it and so withhold its own unsaved-draft prompt;</li>
 *   <li>the appointment last opened from the day sheet, which Health Tracker saves link their
 *       measurements and note to and whose clinic site a multisite prescription preselects as its
 *       address: work in one patient's window was tied to the visit last opened for another
 *       patient.</li>
 * </ul>
 * <p>Each such attribute is now keyed by the patient it belongs to. Two windows of the same
 * patient still share their state, as they did before. A patient number is never read back from
 * the session to decide which patient a request is for: requests name their patient
 * (see {@code EncounterPatientContext.requireDemographicNo}).</p>
 *
 * @since 2026-10-16
 */
public final class ChartSessionKeys {

    private ChartSessionKeys() {
    }

    /**
     * The attribute holding one note-list filter that the note editor hands to the next note list
     * of the same patient. The next note list consumes it once.
     *
     * @param name the filter name ({@code note_sort}, {@code filter_roles}, {@code filter_provider}
     *             or {@code issues})
     * @param demographicNo the patient whose chart owns the filter
     * @return the session attribute name
     * @throws IllegalArgumentException if {@code demographicNo} is not a patient number
     */
    public static String noteFilterHandoff(String name, String demographicNo) {
        return key(name, demographicNo);
    }

    /**
     * The attribute holding the text the last note save wrote into this patient's legacy eChart
     * record; the next save of the same patient removes that text before appending its own.
     *
     * @param demographicNo the patient whose eChart record received the text
     * @return the session attribute name
     * @throws IllegalArgumentException if {@code demographicNo} is not a patient number
     */
    public static String lastSavedNoteText(String demographicNo) {
        return key("lastSavedNoteString", demographicNo);
    }

    /**
     * The attribute by which the note editor tells this patient's next chart view that the
     * patient's unsaved draft is already being restored, so the view does not offer it again.
     *
     * @param demographicNo the patient whose draft is being restored
     * @return the session attribute name
     * @throws IllegalArgumentException if {@code demographicNo} is not a patient number
     */
    public static String draftRestoring(String demographicNo) {
        return key("restoring", demographicNo);
    }

    /**
     * The attribute holding the appointment this patient was last opened for from the day sheet
     * (its encounter, master record or prescription link). Readers must confirm the appointment
     * still belongs to the patient; see {@code EncounterPatientContext.currentAppointment}.
     *
     * @param demographicNo the patient the appointment is booked for
     * @return the session attribute name
     * @throws IllegalArgumentException if {@code demographicNo} is not a patient number
     */
    public static String currentAppointment(String demographicNo) {
        return key("cur_appointment_no", demographicNo);
    }

    /**
     * Whether {@code demographicNo} is a patient number these keys accept: digits only, at most
     * ten of them.
     *
     * @param demographicNo the candidate, may be null
     * @return true if a key can be built for it
     */
    public static boolean isPatientNumber(String demographicNo) {
        return demographicNo != null && demographicNo.matches("[0-9]{1,10}");
    }

    private static String key(String name, String demographicNo) {
        if (!isPatientNumber(demographicNo)) {
            throw new IllegalArgumentException("Invalid demographicNo");
        }
        // Canonical digits, so "0101" and "101" name the same chart's state.
        return name + ":" + Long.parseLong(demographicNo);
    }
}
