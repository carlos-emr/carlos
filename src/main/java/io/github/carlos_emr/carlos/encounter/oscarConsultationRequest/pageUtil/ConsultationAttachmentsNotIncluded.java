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

import io.github.carlos_emr.carlos.commn.model.ConsultDocs;
import io.github.carlos_emr.carlos.encounter.data.EctFormData;
import io.github.carlos_emr.carlos.managers.ConsultationManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;
import org.openpdf.text.DocumentException;

/**
 * Discloses unavailable forms and HRMs without exposing a hidden report's type, ID or title.
 * Callers authorize the consultation and its stored patient before looking up attachments.
 *
 * @since 2026-11-04
 */
public final class ConsultationAttachmentsNotIncluded {
    public static final String REQUEST_ATTRIBUTE = "consultAttachmentsNotIncluded";
    private static final String PREFIX = "encounter.oscarConsultationRequest.attachmentsNotIncluded.";

    private ConsultationAttachmentsNotIncluded() {
    }

    /**
     * Compares persisted attachments with the patient-scoped lists used by the renderers.
     *
     * @param manager ConsultationManager used to read the stored attachment relationships
     * @param info LoggedInInfo for the authorized consultation user
     * @param requestId int ID of the verified consultation
     * @param forms List of clinical forms available to the renderer
     * @param hrms List of HRM maps available to the renderer
     * @param locale Locale negotiated for the reader, or null for English
     * @return List of localized omission warnings, empty when every attachment is available
     */
    public static List<String> describe(ConsultationManager manager, LoggedInInfo info, int requestId,
            List<EctFormData.PatientForm> forms, List<? extends Map<String, ?>> hrms, Locale locale) {
        return describe(manager.getAttachedDocumentsByType(info, requestId, ConsultDocs.DOCTYPE_FORM),
                manager.getAttachedDocumentsByType(info, requestId, ConsultDocs.DOCTYPE_HRM), forms, hrms, locale);
    }

    /**
     * Compares already-fetched relationships with the same patient-scoped lists used by the view.
     * No attachment queries are performed. Callers must authorize the consultation and its patient.
     *
     * @param storedForms List of stored clinical-form relationships for the verified consultation
     * @param storedHrms List of stored HRM relationships for the same consultation
     * @param forms List of clinical forms available to the renderer
     * @param hrms List of HRM maps available to the renderer
     * @param locale Locale negotiated for the reader, or null for English
     * @return List of localized omission warnings, empty when every attachment is available
     */
    public static List<String> describe(List<ConsultDocs> storedForms, List<ConsultDocs> storedHrms,
            List<EctFormData.PatientForm> forms, List<? extends Map<String, ?>> hrms, Locale locale) {
        ResourceBundle bundle = ConsultationLabsNotIncluded.bundle(locale == null ? Locale.ENGLISH : locale);
        Set<String> formIds = forms.stream().filter(Objects::nonNull).map(EctFormData.PatientForm::getFormId).collect(Collectors.toSet());
        Set<String> hrmIds = hrms.stream().filter(Objects::nonNull).map(row -> String.valueOf(row.get("id"))).collect(Collectors.toSet());
        List<String> warnings = new ArrayList<>();
        if (hasOmission(storedForms, formIds)) {
            warnings.add(bundle.getString(PREFIX + "form"));
        }
        // Collapse HRM omissions to one generic message, including when permission or region hides
        // the whole list. Neither the report type nor its count identifies restricted records.
        if (hasOmission(storedHrms, hrmIds)) {
            warnings.add(bundle.getString(PREFIX + "restricted"));
        }
        return warnings;
    }

    private static boolean hasOmission(List<ConsultDocs> stored, Set<String> available) {
        return stored.stream().anyMatch(row -> !available.contains(Integer.toString(row.getDocumentNo())));
    }

    /**
     * Creates the notice page included in preview, print and fax packets.
     *
     * @param warnings List of localized omission warnings; must not be empty
     * @param locale Locale negotiated for the reader, or null for English
     * @return byte[] containing the PDF notice page
     * @throws IllegalArgumentException when warnings is null or empty
     * @throws DocumentException when the PDF notice cannot be rendered
     */
    public static byte[] noticePage(List<String> warnings, Locale locale) throws DocumentException {
        if (warnings == null || warnings.isEmpty()) throw new IllegalArgumentException("An attachment notice needs a warning");
        return ConsultationLabsNotIncluded.noticePage(warnings, locale, PREFIX + "title", PREFIX + "explanation");
    }

    /**
     * Formats the preview alert using one translatable message template.
     *
     * @param warnings List of localized omission warnings
     * @param locale Locale negotiated for the reader, or null for English
     * @return String containing the localized alert heading and warning list
     */
    public static String previewWarning(List<String> warnings, Locale locale) {
        ResourceBundle bundle = ConsultationLabsNotIncluded.bundle(locale == null ? Locale.ENGLISH : locale);
        return new MessageFormat(bundle.getString(PREFIX + "preview"), locale == null ? Locale.ENGLISH : locale)
                .format(new Object[]{String.join("\n", warnings)});
    }
}
