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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import java.io.IOException;
import java.util.*;

/** Legacy unotes is plain-text framing. No XML/HTML parsing, entity decoding, trimming or first-closing-tag truncation is allowed. */
public final class CdsExportPatientNoteMapper {
    public CdsExportPatientNoteMappingDto map(
            int patient, Set<CdsCategory> selected, CdsExportCapturedTextRowDto row)
            throws IOException {
        if (patient < 1
                || selected == null
                || !selected.contains(CdsCategory.DEMOGRAPHICS)
                || row == null
                || !"demographiccust".equals(row.table())
                || !CdsExportPatientTableSourceReader.key(patient).equals(row.recordKey())
                || !Integer.toString(patient).equals(row.fields().get("demographic_no"))) scope();
        var expected =
                new HashSet<>(Set.of("content", "cust1", "cust2", "cust4", "demographic_no"));
        boolean alerts = selected.contains(CdsCategory.ALERTS_AND_SPECIAL_NEEDS);
        if (alerts) expected.add("cust3");
        if (!row.fields().keySet().equals(expected)) scope();
        var decisions =
                new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var assess = new CdsExportNativeFieldAssessments(row, decisions);
        assess.operational("demographic_no", "EXACT_PATIENT_OWNER");
        var delegated = new HashSet<CdsExportNativeFieldKeyDto>();
        if (alerts) {
            var field = new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), "cust3");
            decisions.remove(field);
            delegated.add(field);
        }
        for (String column : Set.of("cust1", "cust2", "cust4")) {
            String value = assess.raw(column);
            if (value != null && !value.isEmpty())
                assess.review(column, "", "DEMOGRAPHIC_CARE_TEAM_CARRIER_REQUIRES_REVIEW");
        }
        String raw = assess.raw("content"), text = raw;
        if (raw != null) {
            if (raw.startsWith("<unotes>") && raw.endsWith("</unotes>") && raw.length() >= 17)
                text = raw.substring(8, raw.length() - 9);
            else if (raw.indexOf("<unotes>") >= 0
                    && raw.indexOf("</unotes>", raw.indexOf("<unotes>") + 8) >= 0) {
                assess.review(
                        "content",
                        "NoteAboutPatient",
                        "AMBIGUOUS_PATIENT_NOTE_FRAMING_REQUIRES_REVIEW");
                text = null;
            }
            if (text != null) {
                if (!CdsExportNativeFieldAssessments.legal(text, 65536)) {
                    assess.review(
                            "content",
                            "NoteAboutPatient",
                            "PATIENT_NOTE_WIRE_TEXT_REQUIRES_REVIEW");
                    text = null;
                } else if (text.codePointCount(0, text.length()) > 64000) {
                    assess.review(
                            "content",
                            "NoteAboutPatient",
                            "PATIENT_NOTE_DICTIONARY_LENGTH_REQUIRES_REVIEW");
                    text = null;
                } else
                    assess.set(
                            "content",
                            Outcome.MAPPED,
                            "NoteAboutPatient",
                            raw.equals(text)
                                    ? "EXACT_UNFRAMED_PATIENT_NOTE"
                                    : "EXACT_OUTER_UNOTES_PLAIN_TEXT_FRAME");
            }
        }
        return new CdsExportPatientNoteMappingDto(patient, text, decisions, delegated);
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_PATIENT_NOTE_SCOPE_INVALID");
    }
}
