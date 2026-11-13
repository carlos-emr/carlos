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
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeExtensionContract;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDateDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNativeHistoryDto;
import java.io.IOException;
import java.util.List;

/** Schema-ordered history output; complete native text is never clipped to a wire field. */
public final class CdsExportNativeHistoryXmlWriter {
    public void write(CdsExportNativeHistoryDto value, CdsExportXmlComposer.ContentWriter xml)
            throws IOException {
        if (value.category() == CdsCategory.CLINICAL_NOTES) {
            text(xml, value, "NoteType");
            text(xml, value, "MyClinicalNotesContent");
            date(xml, value, "EventDateTime");
            for (var participant : value.participants()) {
                xml.start("cds", "ParticipatingProviders");
                xml.start("cds", "Name");
                xml.element("cds_dt", "FirstName", participant.provider().firstName());
                xml.element("cds_dt", "LastName", participant.provider().lastName());
                xml.end();
                if (participant.provider().ohipNumber() != null)
                    xml.element("cds", "OHIPPhysicianId", participant.provider().ohipNumber());
                date(xml, "DateTimeNoteCreated", participant.created());
                xml.end();
            }
            return;
        }
        xml.start("cds", "ResidualInfo");
        xml.start("cds_dt", "DataElement");
        xml.element("cds_dt", "Name", "CARLOS.NativeHistory.v1");
        xml.element("cds_dt", "DataType", "string");
        xml.element("cds_dt", "Content", value.metadata());
        xml.end();
        residualElement(
                xml,
                CdsNativeExtensionContract.HISTORY_ARCHIVED,
                "boolean",
                Boolean.toString(value.archived()));
        if (value.category() == CdsCategory.PERSONAL_HISTORY
                && value.text().containsKey("NativePersonalNarrative"))
            residualElement(
                    xml,
                    CdsNativeExtensionContract.HISTORY_PERSONAL_NARRATIVE,
                    "string",
                    value.text().get("NativePersonalNarrative"));
        if (value.category() != CdsCategory.PERSONAL_HISTORY
                && value.text().containsKey("NativeNarrative"))
            residualElement(
                    xml,
                    CdsNativeExtensionContract.HISTORY_NARRATIVE,
                    "string",
                    CdsNativeExtensionContract.encodeNarrative(value.text().get("NativeNarrative")));
        xml.end();
        List<String> order =
                switch (value.category()) {
                    case PERSONAL_HISTORY -> List.of();
                    case FAMILY_HISTORY ->
                            List.of(
                                    "StartDate",
                                    "AgeAtOnset",
                                    "LifeStage",
                                    "ProblemDiagnosisProcedureDescription",
                                    "DiagnosisProcedureCode",
                                    "Treatment",
                                    "Relationship",
                                    "Notes");
                    case PAST_HEALTH ->
                            List.of(
                                    "PastHealthProblemDescriptionOrProcedures",
                                    "DiagnosisProcedureCode",
                                    "OnsetOrEventDate",
                                    "LifeStage",
                                    "ResolvedDate",
                                    "ProcedureDate",
                                    "Notes",
                                    "ProblemStatus");
                    case PROBLEM_LIST ->
                            List.of(
                                    "ProblemDiagnosisDescription",
                                    "DiagnosisCode",
                                    "ProblemDescription",
                                    "ProblemStatus",
                                    "OnsetDate",
                                    "LifeStage",
                                    "ResolutionDate",
                                    "Notes");
                    case RISK_FACTORS ->
                            List.of(
                                    "RiskFactor",
                                    "ExposureDetails",
                                    "AgeOfOnset",
                                    "StartDate",
                                    "EndDate",
                                    "LifeStage",
                                    "Notes");
                    case ALERTS_AND_SPECIAL_NEEDS ->
                            List.of("AlertDescription", "Notes", "DateActive", "EndDate");
                    default -> throw new IOException("CDS_EXPORT_NATIVE_HISTORY_CATEGORY_INVALID");
                };
        for (String name : order) {
            if (value.dates().containsKey(name)) date(xml, value, name);
            else if ((name.equals("DiagnosisCode") || name.equals("DiagnosisProcedureCode"))
                    && value.code() != null) {
                xml.start("cds", name);
                xml.element("cds_dt", "StandardCodingSystem", value.code().system());
                xml.element("cds_dt", "StandardCode", value.code().value());
                xml.element("cds_dt", "StandardCodeDescription", value.code().description());
                xml.end();
            } else text(xml, value, name);
        }
    }

    static void residualElement(
            CdsExportXmlComposer.ContentWriter xml, String name, String type, String content)
            throws IOException {
        xml.start("cds_dt", "DataElement");
        xml.element("cds_dt", "Name", name);
        xml.element("cds_dt", "DataType", type);
        xml.element("cds_dt", "Content", content);
        xml.end();
    }

    private static void text(
            CdsExportXmlComposer.ContentWriter xml, CdsExportNativeHistoryDto value, String name)
            throws IOException {
        if (value.text().containsKey(name)) xml.element("cds", name, value.text().get(name));
    }

    private static void date(
            CdsExportXmlComposer.ContentWriter xml, CdsExportNativeHistoryDto value, String name)
            throws IOException {
        if (value.dates().containsKey(name)) date(xml, name, value.dates().get(name));
    }

    private static void date(
            CdsExportXmlComposer.ContentWriter xml, String name, CdsExportDateDto value)
            throws IOException {
        xml.start("cds", name);
        dateContents(xml, value);
        xml.end();
    }

    static void dateContents(CdsExportXmlComposer.ContentWriter xml, CdsExportDateDto value)
            throws IOException {
        String arm =
                switch (value.precision()) {
                    case YEAR -> "YearOnly";
                    case YEAR_MONTH -> "YearMonth";
                    case DATE -> "FullDate";
                    case DATE_TIME -> "FullDateTime";
                    default -> throw new IOException("CDS_EXPORT_NATIVE_HISTORY_DATE_INVALID");
                };
        xml.element("cds_dt", arm, value.lexical());
    }
}
