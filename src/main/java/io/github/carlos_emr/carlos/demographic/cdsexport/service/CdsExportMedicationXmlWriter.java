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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportMedicationDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportMedicationDto.Field;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDateDto;
import java.io.IOException;

/** Pinned-schema order and indicator choices, independent of source capture and clinical precedence. */
public final class CdsExportMedicationXmlWriter {
    public void write(CdsExportMedicationDto value, CdsExportXmlComposer.ContentWriter xml) throws IOException {
        java.util.Objects.requireNonNull(xml).content(guarded -> writeValue(value, guarded));
    }
    private void writeValue(CdsExportMedicationDto value, CdsExportXmlComposer.ContentWriter xml) throws IOException {
        java.util.Objects.requireNonNull(value); java.util.Objects.requireNonNull(xml);
        validate(value);
        date(xml, "PrescriptionWrittenDate", value.written()); date(xml, "StartDate", value.start());
        field(xml, value, Field.DrugIdentificationNumber); field(xml, value, Field.DrugName);
        if (value.strengthAmount() != null) {
            xml.start("cds", "Strength"); xml.element("cds_dt", "Amount", value.strengthAmount());
            xml.element("cds_dt", "UnitOfMeasure", value.strengthUnit()); xml.end();
        }
        for (var field : new Field[]{Field.NumberOfRefills, Field.Dosage, Field.DosageUnitOfMeasure, Field.Form,
                Field.Route, Field.Frequency, Field.Duration, Field.RefillDuration, Field.Quantity, Field.RefillQuantity,
                Field.LongTermMedication, Field.PastMedications}) field(xml, value, field);
        if (value.prescriber() != null) {
            var provider = value.prescriber(); xml.start("cds", "PrescribedBy"); xml.start("cds", "Name");
            xml.element("cds_dt", "FirstName", provider.firstName()); xml.element("cds_dt", "LastName", provider.lastName()); xml.end();
            if (provider.ohipNumber() != null) xml.element("cds", "OHIPPhysicianId", provider.ohipNumber());
            xml.end();
        }
        for (var field : new Field[]{Field.Notes, Field.PrescriptionInstructions, Field.PatientCompliance, Field.TreatmentType,
                Field.PrescriptionStatus, Field.NonAuthoritativeIndicator, Field.PrescriptionIdentifier,
                Field.PriorPrescriptionReferenceIdentifier, Field.DispenseInterval, Field.DrugDescription,
                Field.SubstitutionNotAllowed, Field.ProblemCode, Field.ProtocolIdentifier}) field(xml, value, field);
    }
    private static void field(CdsExportXmlComposer.ContentWriter xml, CdsExportMedicationDto value, Field field) throws IOException {
        String text = value.fields().get(field); if (text == null) return;
        if (field.indicator()) {
            xml.start("cds", field.name()); xml.element("cds_dt", "ynIndicatorsimple", text); xml.end();
        } else xml.element("cds", field.name(), text);
    }
    static void validate(CdsExportMedicationDto value) throws IOException {
        String name = value.fields().get(Field.DrugName), din = value.fields().get(Field.DrugIdentificationNumber);
        if ((name == null || name.isBlank()) && (din == null || din.isBlank())) invalid();
        for (var entry : value.fields().entrySet()) {
            var field = entry.getKey(); String text = entry.getValue();
            if (!CdsExportNativeFieldAssessments.legal(text, field.maximum())
                    || (field.indicator() || field.directIndicator()) && !text.equals("Y") && !text.equals("N")
                    || field == Field.NumberOfRefills && !token(text)) invalid();
        }
        if (value.strengthAmount() != null && (!token(value.strengthAmount()) || !token(value.strengthUnit())
                || !CdsExportNativeFieldAssessments.legal(value.strengthAmount(), 10)
                || !CdsExportNativeFieldAssessments.legal(value.strengthUnit(), 20))) invalid();
    }
    private static boolean token(String text) {
        return text != null && !text.isEmpty() && !text.startsWith(" ") && !text.endsWith(" ")
                && !text.contains("  ") && text.indexOf('\t') < 0 && text.indexOf('\r') < 0 && text.indexOf('\n') < 0;
    }
    private static void date(CdsExportXmlComposer.ContentWriter xml, String name, CdsExportDateDto date) throws IOException {
        CdsExportDateXmlWriter.write(xml, name, date, name.equals("PrescriptionWrittenDate"));
    }
    private static void invalid() throws IOException { throw new IOException("CDS_EXPORT_MEDICATION_VALUES_REQUIRES_REVIEW"); }
}
