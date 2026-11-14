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

import io.github.carlos_emr.carlos.commn.model.DemographicCust;
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
        // The nurse, resident and midwife selects (add-form-clinical.jsp, edit-form-clinical.jsp)
        // have no Demographics element; the provider number and its public name travel in the
        // native demographic NewCategory record (CdsExportDemographicSupportingMapper).
        for (String column : CdsExportDemographicSupportingMapper.CARE_TEAM.keySet()) {
            String value = assess.raw(column);
            if (value != null && !value.isEmpty())
                decisions.put(
                        new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), column),
                        CdsExportDemographicSupportingMapper.carried(value));
            else assess.set(column, Outcome.ABSENT, "", "ABSENT_DEMOGRAPHIC_CARE_TEAM_CARRIER");
        }
        String raw = assess.raw("content"), text = null;
        if (raw != null) {
            var note = note(raw);
            text = note.text();
            assess.set("content", Outcome.MAPPED, note.destination(), note.rule());
        }
        return new CdsExportPatientNoteMappingDto(patient, text, decisions, delegated);
    }

    /** NoteAboutPatient is a text64K: 64,000 characters as the migration dictionary counts it. */
    static final int NOTE_LENGTH = 64000;

    /**
     * How one stored {@code demographiccust.content} value is exported.
     *
     * @param text the text of NoteAboutPatient, or null when the element is not written
     * @param destination NoteAboutPatient, the native demographic record, or both
     * @param rule the decision's rule
     */
    record Note(String text, String destination, String rule) {
        /** True when the native demographic record carries the exact stored column. */
        boolean carried() {
            return destination.endsWith(CdsExportDemographicSupportingMapper.DESTINATION);
        }

        @Override
        public String toString() {
            return "Note[protected]";
        }
    }

    /**
     * Decides a stored patient note. No note holds the patient:
     *
     * <ul>
     *   <li>the note every CARLOS screen writes, {@code <unotes>text</unotes>}, or a bare text, is
     *       NoteAboutPatient exactly;
     *   <li>a legacy value that has the frame somewhere inside it is written as the chart shows it
     *       ({@link DemographicCust#patientNotesText}), and the exact column travels in the native
     *       demographic record;
     *   <li>a character XML 1.0 cannot carry is written as U+FFFD ({@link CdsExportXmlText}), and
     *       the exact column travels in the record;
     *   <li>a note longer than the element's 64,000 characters (the column holds 65,535 bytes) is
     *       not cut: the element is left out and the whole column travels in the record.
     * </ul>
     *
     * <p>Without NewCategory the record is not written, and {@link
     * CdsExportDemographicSupportingMapper#withSelection} accounts the exact form as not selected,
     * as it does for every other value the record carries.
     */
    static Note note(String raw) {
        String carrier = CdsExportDemographicSupportingMapper.DESTINATION;
        boolean framed =
                raw.startsWith("<unotes>") && raw.endsWith("</unotes>") && raw.length() >= 17;
        String shown = DemographicCust.patientNotesText(raw);
        // The chart's reader took an inner frame: the value is not what a screen wrote.
        boolean legacy = !framed && !shown.equals(raw);
        String element = CdsExportXmlText.element(shown);
        if (element.codePointCount(0, element.length()) > NOTE_LENGTH)
            return new Note(
                    null, carrier, "PATIENT_NOTE_LONGER_THAN_THE_ELEMENT_CARRIED_WHOLE");
        if (!element.equals(shown))
            return new Note(
                    element,
                    "NoteAboutPatient," + carrier,
                    "PATIENT_NOTE_WITH_U_FFFD_EXACT_COLUMN_CARRIED");
        if (legacy)
            return new Note(
                    element,
                    "NoteAboutPatient," + carrier,
                    "PATIENT_NOTE_AS_THE_CHART_SHOWS_IT_EXACT_COLUMN_CARRIED");
        return new Note(
                element,
                "NoteAboutPatient",
                framed ? "EXACT_OUTER_UNOTES_PLAIN_TEXT_FRAME" : "EXACT_UNFRAMED_PATIENT_NOTE");
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_PATIENT_NOTE_SCOPE_INVALID");
    }
}
