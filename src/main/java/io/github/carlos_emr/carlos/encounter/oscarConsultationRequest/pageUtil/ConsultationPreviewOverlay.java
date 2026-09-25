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
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Lets the consultation <em>print preview</em> show what the clinician has typed but not yet saved.
 *
 * <p>The Print button on the consultation request screen exists to preview unsaved work:
 * {@code getConsultFormPrintPreview()} serializes the whole form and POSTs it by AJAX rather than
 * navigating, so the clinician keeps their edits and stays on the page. The server then ignored
 * every posted field — {@code ConsultationPDFCreator} rebuilds the form from the database with
 * {@code estRequestFromId} — so the preview showed the <em>stored</em> consultation. On a request
 * whose clinical fields had never been filled in, that is a generic referral with nothing in it,
 * which is what issue #3721 reports.</p>
 *
 * <p><strong>What is overlaid, and what deliberately is not.</strong> Only fields the clinician
 * types or picks on this form, and that the PDF renders straight from the same value. Patient
 * identity and demographics, the referring provider, the letterhead block and the resolved
 * specialist are NOT overlaid:</p>
 *
 * <ul>
 *   <li>Patient and provider fields are not editable on this form, and a preview must never render
 *       identity that came from the request rather than from the record — a crafted POST would
 *       otherwise put any name, health number or address onto a document that looks official.</li>
 *   <li>The letterhead and the specialist are <em>resolved</em> server-side from an id: the PDF
 *       prints {@code getProfessionalSpecialist()} and the letterhead address, phone and fax that
 *       belong together. Copying the posted id alone would leave the resolved block describing a
 *       different office than the name above it, which is worse than showing the saved one. Making
 *       those follow an unsaved pick needs the lookups the save path runs, and is tracked
 *       separately rather than half-done here.</li>
 * </ul>
 *
 * <p>Nothing here writes to the database. The overlay applies to one in-memory form object used to
 * render one PDF, and only on the preview branch, which sets
 * {@link #PREVIEW_USES_UNSAVED_VALUES_ATTRIBUTE}. The fax and stored-record print paths save first,
 * so they keep reading the record and are unaffected.</p>
 *
 * @since 2026-09-25
 */
final class ConsultationPreviewOverlay {

    /**
     * Request attribute the preview branch sets to ask for this overlay.
     *
     * <p>An explicit opt-in rather than "whenever the parameters happen to be there": the fax
     * submission posts the same form and reaches the same PDF creator, and there the record has
     * already been saved, so reading the request instead of the record could only introduce a
     * difference between the faxed document and the filed one.</p>
     */
    static final String PREVIEW_USES_UNSAVED_VALUES_ATTRIBUTE =
            "consultationPreviewUsesUnsavedValues";

    /**
     * Form field name to the form object it fills, for the fields a clinician edits here.
     *
     * <p>Ordered so the mapping reads next to the screen it mirrors: the referral's clinical text
     * first, then urgency and the booking fields.</p>
     */
    private static final Map<String, BiConsumer<EctConsultationFormRequestUtil, String>> OVERLAID =
            Map.ofEntries(
                    Map.entry("reasonForConsultation", (form, value) -> form.reasonForConsultation = value),
                    Map.entry("clinicalInformation", (form, value) -> form.clinicalInformation = value),
                    Map.entry("concurrentProblems", (form, value) -> form.concurrentProblems = value),
                    Map.entry("currentMedications", (form, value) -> form.currentMedications = value),
                    Map.entry("allergies", (form, value) -> form.allergies = value),
                    Map.entry("urgency", (form, value) -> form.urgency = value),
                    Map.entry("appointmentNotes", (form, value) -> form.appointmentNotes = value),
                    Map.entry("appointmentDate", (form, value) -> form.appointmentDate = value),
                    Map.entry("appointmentHour", (form, value) -> form.appointmentHour = value),
                    Map.entry("appointmentMinute", (form, value) -> form.appointmentMinute = value),
                    Map.entry("appointmentPm", (form, value) -> form.appointmentPm = value));

    /** The checkbox is absent from the POST when unchecked, so it is handled apart from the map. */
    private static final String PATIENT_WILL_BOOK_PARAMETER = "patientWillBook";

    private ConsultationPreviewOverlay() {
    }

    /** Is this the preview render that should show unsaved values? */
    static boolean requested(HttpServletRequest request) {
        return request != null
                && Boolean.TRUE.equals(request.getAttribute(PREVIEW_USES_UNSAVED_VALUES_ATTRIBUTE));
    }

    /**
     * Copies the posted value of each overlaid field onto the form, leaving the rest as the record
     * has it.
     *
     * <p>A field absent from the POST keeps its stored value; a field posted <em>empty</em> is
     * copied as empty, because clearing a box and printing has to show it cleared — that is the
     * same edit as typing into it.</p>
     *
     * @param form    the form object the PDF renders, already populated from the stored record
     * @param request the preview POST carrying the clinician's current, unsaved values
     */
    static void apply(EctConsultationFormRequestUtil form, HttpServletRequest request) {
        if (form == null || request == null) {
            return;
        }
        for (Map.Entry<String, BiConsumer<EctConsultationFormRequestUtil, String>> field : OVERLAID.entrySet()) {
            String posted = request.getParameter(field.getKey());
            if (posted != null) {
                field.getValue().accept(form, posted);
            }
        }
        // An unchecked checkbox posts nothing at all, so unlike every field above, absence IS the
        // value here: not checked. That reading is only safe because apply() runs solely on the
        // preview branch, where the request is known to be this form's own POST.
        form.pwb = request.getParameter(PATIENT_WILL_BOOK_PARAMETER) != null ? "1" : "0";
    }
}
