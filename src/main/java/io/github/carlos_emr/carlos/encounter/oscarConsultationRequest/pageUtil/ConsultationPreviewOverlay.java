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

import io.github.carlos_emr.carlos.commn.model.LookupList;
import io.github.carlos_emr.carlos.commn.model.LookupListItem;
import io.github.carlos_emr.carlos.managers.LookupListManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;

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
                    Map.entry("referalDate", (form, value) -> form.referalDate = value),
                    Map.entry("appointmentNotes", (form, value) -> form.appointmentNotes = value),
                    Map.entry("appointmentDate", (form, value) -> form.appointmentDate = value),
                    Map.entry("appointmentHour", (form, value) -> form.appointmentHour = value),
                    Map.entry("appointmentMinute", (form, value) -> form.appointmentMinute = value),
                    Map.entry("appointmentPm", (form, value) -> form.appointmentPm = value));

    /** The checkbox is absent from the POST when unchecked, so it is handled apart from the map. */
    private static final String PATIENT_WILL_BOOK_PARAMETER = "patientWillBook";

    /**
     * Hidden marker the form renders beside the checkbox, inside the same property gate.
     *
     * <p>An unchecked checkbox and a checkbox that was never rendered look identical in a POST, and
     * {@code CONSULTATION_PATIENT_WILL_BOOK} is false by default — so reading absence as "not
     * checked" would clear a stored patient-will-book on every preview of a deployment that does
     * not show the control at all. The marker is what tells the two apart.</p>
     */
    private static final String PATIENT_WILL_BOOK_RENDERED_PARAMETER = "patientWillBookRendered";

    /** The appointment instruction the clinician picked; the PDF prints its resolved label. */
    private static final String APPOINTMENT_INSTRUCTIONS_PARAMETER = "appointmentInstructions";

    /** The lookup list the form's instruction select is built from. */
    private static final String APPOINTMENT_INSTRUCTION_LOOKUP_LIST = "consultApptInst";

    private ConsultationPreviewOverlay() {
    }

    /**
     * A resolver for the appointment-instruction label, reading the same lookup list the form's
     * select is built from ({@code consultApptInst}, see ConsultationFormRequest.jsp).
     *
     * <p>Answers the empty string for a value the list does not hold and for an empty selection,
     * which is what the stored record does for an unmatched value. Kept here rather than inside
     * {@link #apply} so the overlay itself needs no Spring context.</p>
     */
    static UnaryOperator<String> appointmentInstructionLabelResolver(LoggedInInfo loggedInInfo) {
        return value -> {
            if (value == null || value.isEmpty()) {
                return "";
            }
            LookupList list = SpringUtils.getBean(LookupListManager.class)
                    .findLookupListByName(loggedInInfo, APPOINTMENT_INSTRUCTION_LOOKUP_LIST);
            if (list == null || list.getItems() == null) {
                return "";
            }
            for (LookupListItem item : list.getItems()) {
                if (value.equals(item.getValue())) {
                    return item.getLabel();
                }
            }
            return "";
        };
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
     * @param form          the form object the PDF renders, already populated from the stored record
     * @param request       the preview POST carrying the clinician's current, unsaved values
     * @param labelForValue resolves an appointment-instruction value to the label the PDF prints,
     *                      or null when this deployment does not use the lookup. Injected rather
     *                      than looked up here so this class keeps no container dependency and
     *                      stays testable without one.
     */
    static void apply(EctConsultationFormRequestUtil form, HttpServletRequest request,
                      UnaryOperator<String> labelForValue) {
        if (form == null || request == null) {
            return;
        }
        for (Map.Entry<String, BiConsumer<EctConsultationFormRequestUtil, String>> field : OVERLAID.entrySet()) {
            String posted = request.getParameter(field.getKey());
            if (posted != null) {
                field.getValue().accept(form, posted);
            }
        }
        // An unchecked checkbox posts nothing at all, so absence is the value here -- but ONLY when
        // the control was on the page. The form renders it behind CONSULTATION_PATIENT_WILL_BOOK,
        // which is false by default, so without the marker every preview on a default deployment
        // would clear a stored patient-will-book and print an appointment date instead.
        if (request.getParameter(PATIENT_WILL_BOOK_RENDERED_PARAMETER) != null) {
            form.pwb = request.getParameter(PATIENT_WILL_BOOK_PARAMETER) != null ? "1" : "0";
        }

        // The instruction is posted as a lookup value while the PDF prints the matching label, so
        // copying the value alone would leave the preview showing the stored instruction. Both move
        // together or neither does.
        String instructions = request.getParameter(APPOINTMENT_INSTRUCTIONS_PARAMETER);
        if (instructions != null && labelForValue != null) {
            form.setAppointmentInstructionsForPreview(instructions, labelForValue.apply(instructions));
        }
    }
}
