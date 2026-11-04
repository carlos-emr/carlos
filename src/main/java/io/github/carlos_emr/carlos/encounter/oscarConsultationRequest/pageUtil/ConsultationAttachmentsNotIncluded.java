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
 */
public final class ConsultationAttachmentsNotIncluded {
    public static final String REQUEST_ATTRIBUTE = "consultAttachmentsNotIncluded";
    private static final String PREFIX = "encounter.oscarConsultationRequest.attachmentsNotIncluded.";

    private ConsultationAttachmentsNotIncluded() {
    }

    /** Compares persisted attachments with the patient-scoped lists used by the renderers. */
    public static List<String> describe(ConsultationManager manager, LoggedInInfo info, int requestId,
            List<EctFormData.PatientForm> forms, List<? extends Map<String, ?>> hrms, Locale locale) {
        ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", locale == null ? Locale.ENGLISH : locale);
        Set<String> formIds = forms.stream().filter(Objects::nonNull).map(EctFormData.PatientForm::getFormId).collect(Collectors.toSet());
        Set<String> hrmIds = hrms.stream().filter(Objects::nonNull).map(row -> String.valueOf(row.get("id"))).collect(Collectors.toSet());
        List<String> warnings = new ArrayList<>();
        if (hasOmission(manager.getAttachedDocumentsByType(info, requestId, ConsultDocs.DOCTYPE_FORM), formIds)) {
            warnings.add(bundle.getString(PREFIX + "form"));
        }
        // Collapse HRM omissions to one generic message, including when permission or region hides
        // the whole list. Neither the report type nor its count identifies restricted records.
        if (hasOmission(manager.getAttachedDocumentsByType(info, requestId, ConsultDocs.DOCTYPE_HRM), hrmIds)) {
            warnings.add(bundle.getString(PREFIX + "restricted"));
        }
        return warnings;
    }

    private static boolean hasOmission(List<ConsultDocs> stored, Set<String> available) {
        return stored.stream().anyMatch(row -> !available.contains(Integer.toString(row.getDocumentNo())));
    }

    /** Adds a visible page to preview, print and fax packets rather than silently omitting content. */
    public static byte[] noticePage(List<String> warnings, Locale locale) throws DocumentException {
        if (warnings == null || warnings.isEmpty()) throw new IllegalArgumentException("An attachment notice needs a warning");
        return ConsultationLabsNotIncluded.noticePage(warnings, locale, PREFIX + "title", PREFIX + "explanation");
    }

    public static String previewWarning(List<String> warnings, Locale locale) {
        ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", locale == null ? Locale.ENGLISH : locale);
        return bundle.getString(PREFIX + "title") + "\n" + String.join("\n", warnings);
    }
}
