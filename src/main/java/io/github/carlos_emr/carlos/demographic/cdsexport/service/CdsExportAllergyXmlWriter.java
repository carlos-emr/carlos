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

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeExtensionContract;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportAllergyDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDateDto;
import java.io.IOException;
import java.util.Objects;

/** Schema-ordered allergy writer. Additional source fields still require full reconciliation/coverage. */
public final class CdsExportAllergyXmlWriter {
    public void write(CdsExportAllergyDto allergy, CdsExportXmlComposer.ContentWriter xml)
            throws IOException {
        java.util.Objects.requireNonNull(xml).content(guarded -> writeValue(allergy, guarded));
    }

    private void writeValue(CdsExportAllergyDto allergy, CdsExportXmlComposer.ContentWriter xml)
            throws IOException {
        Objects.requireNonNull(allergy);
        Objects.requireNonNull(xml);
        validate(allergy);
        xml.start("cds", "ResidualInfo");
        residual(
                xml,
                CdsNativeExtensionContract.ALLERGY_ARCHIVED,
                CdsNativeExtensionContract.BOOLEAN_TYPE,
                Boolean.toString(allergy.archived()));
        if ("ATC".equals(allergy.codeType()))
            residual(
                    xml,
                    CdsNativeExtensionContract.ALLERGY_ATC,
                    CdsNativeExtensionContract.STRING_TYPE,
                    allergy.codeValue());
        if (allergy.ageOfOnset() != null) {
            String age = allergy.ageOfOnset();
            if (age.isEmpty()
                    || age.codePointCount(0, age.length()) > 4
                    || !age.equals(age.strip())
                    || age.contains("  ")
                    || age.codePoints().anyMatch(code -> code < 0x20)) {
                throw new IOException("CDS_EXPORT_ALLERGY_AGE_REQUIRES_REVIEW");
            }
            residual(
                    xml,
                    CdsNativeExtensionContract.ALLERGY_AGE_OF_ONSET,
                    CdsNativeExtensionContract.STRING_TYPE,
                    age);
        }
        if (allergy.onsetOfReaction() != null) {
            String onset =
                    CdsNativeExtensionContract.ALLERGY_ONSET_VALUES.get(allergy.onsetOfReaction());
            if (onset == null) throw new IOException("CDS_EXPORT_ALLERGY_ONSET_REQUIRES_REVIEW");
            residual(
                    xml,
                    CdsNativeExtensionContract.ALLERGY_ONSET_OF_REACTION,
                    CdsNativeExtensionContract.STRING_TYPE,
                    onset);
        }
        if (allergy.nativeSeverity() != null)
            residual(
                    xml,
                    CdsNativeExtensionContract.ALLERGY_SEVERITY,
                    CdsNativeExtensionContract.STRING_TYPE,
                    CdsNativeExtensionContract.ALLERGY_SEVERITY_VALUES.get(
                            allergy.nativeSeverity()));
        xml.end();
        optional(xml, "OffendingAgentDescription", allergy.description());
        optional(xml, "PropertyOfOffendingAgent", allergy.property());
        if (allergy.codeValue() != null && "DIN".equals(allergy.codeType())) {
            xml.start("cds", "Code");
            xml.element("cds_dt", "CodeType", allergy.codeType());
            xml.element("cds_dt", "CodeValue", allergy.codeValue());
            xml.end();
        }
        optional(xml, "ReactionType", allergy.reactionType());
        date(xml, "StartDate", allergy.start());
        optional(xml, "LifeStage", allergy.lifeStage());
        optional(xml, "Severity", allergy.severity());
        optional(xml, "Reaction", allergy.reaction());
        date(xml, "RecordedDate", allergy.recorded());
        optional(xml, "Notes", allergy.notes());
    }

    /** Same capacity/lexical gate for direct and retained assembly. */
    static void validate(CdsExportAllergyDto allergy) throws IOException {
        Objects.requireNonNull(allergy);
        if (allergy.codeType() != null
                && !("DIN".equals(allergy.codeType())
                        || "ATC".equals(allergy.codeType())
                                && CdsNativeExtensionContract.validAllergyAtc(allergy.codeValue())))
            throw new IOException("CDS_EXPORT_ALLERGY_CODE_AUTHORITY_REQUIRES_REVIEW");
        if ((allergy.description() == null || allergy.description().isBlank())
                && allergy.codeValue() == null) {
            throw new IOException("CDS_EXPORT_ALLERGY_AGENT_REQUIRES_REVIEW");
        }
        String[] values = {
            allergy.description(),
            allergy.property(),
            allergy.codeType(),
            allergy.codeValue(),
            allergy.reactionType(),
            allergy.lifeStage(),
            allergy.severity(),
            allergy.reaction(),
            allergy.notes()
        };
        int[] limits = {120, 2, 3, 20, 2, 1, 2, 120, 32000};
        for (int i = 0; i < values.length; i++)
            if (values[i] != null && !CdsExportNativeFieldAssessments.legal(values[i], limits[i]))
                throw new IOException("CDS_EXPORT_ALLERGY_VALUES_REQUIRES_REVIEW");
        String code = allergy.codeValue();
        if (code != null
                && (code.isEmpty()
                        || code.startsWith(" ")
                        || code.endsWith(" ")
                        || code.contains("  ")
                        || code.indexOf('\t') >= 0
                        || code.indexOf('\r') >= 0
                        || code.indexOf('\n') >= 0))
            throw new IOException("CDS_EXPORT_ALLERGY_VALUES_REQUIRES_REVIEW");
        String age = allergy.ageOfOnset();
        if (age != null
                && (age.isEmpty()
                        || !CdsExportNativeFieldAssessments.legal(age, 4)
                        || !age.equals(age.strip())
                        || age.contains("  ")
                        || age.codePoints().anyMatch(value -> value < 0x20)))
            throw new IOException("CDS_EXPORT_ALLERGY_AGE_REQUIRES_REVIEW");
        if (allergy.onsetOfReaction() != null
                && !CdsNativeExtensionContract.ALLERGY_ONSET_VALUES.containsKey(
                        allergy.onsetOfReaction()))
            throw new IOException("CDS_EXPORT_ALLERGY_ONSET_REQUIRES_REVIEW");
    }

    private static void residual(
            CdsExportXmlComposer.ContentWriter xml, String name, String type, String value)
            throws IOException {
        xml.start("cds_dt", "DataElement");
        residualFields(xml, name, type, value);
        xml.end();
    }

    static void residualFields(
            CdsExportXmlComposer.ContentWriter xml, String name, String type, String value)
            throws IOException {
        xml.element("cds_dt", "Name", name);
        xml.element("cds_dt", "DataType", type);
        xml.element("cds_dt", "Content", value);
    }

    private static void optional(CdsExportXmlComposer.ContentWriter xml, String name, String value)
            throws IOException {
        if (value != null) xml.element("cds", name, value);
    }

    private static void date(
            CdsExportXmlComposer.ContentWriter xml, String name, CdsExportDateDto date)
            throws IOException {
        CdsExportDateXmlWriter.write(xml, name, date, name.equals("RecordedDate"));
    }

    static void dateContent(
            CdsExportXmlComposer.ContentWriter xml, CdsExportDateDto date, boolean permitsClock)
            throws IOException {
        CdsExportDateXmlWriter.content(xml, date, permitsClock);
    }
}
