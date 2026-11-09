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

import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsMedicationDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportMedicationDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportMedicationDto.Field;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPublicProviderDto;
import java.io.IOException;
import java.util.EnumMap;

/** Historical prepared meanings only; separate evidence must authorize their current-chart selection. */
public final class CdsExportPreparedMedicationMapper {
    public CdsExportMedicationDto map(CdsMedicationDto source) throws IOException {
        java.util.Objects.requireNonNull(source);
        if (source.strengthSourceOnly())
            throw new IOException("CDS_EXPORT_MEDICATION_LARGE_STRENGTH_REQUIRES_RETAINED_CARRIER");
        var values = new EnumMap<Field, String>(Field.class);
        put(values, Field.DrugIdentificationNumber, source.din());
        put(values, Field.DrugName, source.name());
        put(values, Field.DrugDescription, source.description());
        put(values, Field.Dosage, source.dose());
        put(values, Field.DosageUnitOfMeasure, source.doseUnit());
        put(values, Field.Form, source.form());
        put(values, Field.Route, source.route());
        put(values, Field.Frequency, source.frequency());
        put(values, Field.Duration, source.durationDays());
        put(values, Field.RefillDuration, source.refillDurationDays());
        put(values, Field.Quantity, source.quantity());
        put(values, Field.RefillQuantity, source.refillQuantity());
        put(values, Field.NumberOfRefills, source.repeats());
        flag(values, Field.LongTermMedication, source.longTerm());
        flag(values, Field.PastMedications, source.past());
        flag(values, Field.PatientCompliance, source.compliance());
        flag(values, Field.SubstitutionNotAllowed, source.substitutionNotAllowed());
        flag(values, Field.NonAuthoritativeIndicator, source.nonAuthoritative());
        put(values, Field.TreatmentType, source.treatmentType());
        put(values, Field.PrescriptionStatus, source.prescriptionStatus());
        put(values, Field.Notes, source.providerNotes());
        put(values, Field.PrescriptionInstructions, source.pharmacyInstructions());
        put(values, Field.DispenseInterval, source.dispenseInterval());
        put(values, Field.ProtocolIdentifier, source.protocol());
        put(values, Field.PriorPrescriptionReferenceIdentifier, source.priorPrescription());
        put(values, Field.PrescriptionIdentifier, source.prescriptionIdentifier());
        CdsExportPublicProviderDto prescriber = null;
        if (source.prescriberFirstName() != null
                || source.prescriberLastName() != null
                || source.prescriberOhip() != null
                || source.prescriberName() != null) {
            // Full-name display is not evidence from which first/last names can be recovered.
            if (source.prescriberFirstName() == null || source.prescriberLastName() == null)
                throw new IOException("CDS_EXPORT_PREPARED_PRESCRIBER_COMPONENTS_REQUIRED");
            prescriber =
                    new CdsExportPublicProviderDto(
                            source.prescriberFirstName(),
                            source.prescriberLastName(),
                            source.prescriberOhip());
        }
        var dates = new CdsExportPreparedTemporalMapper();
        return new CdsExportMedicationDto(
                dates.map(source.written(), true),
                dates.map(source.start(), false),
                source.strengthAmount(),
                source.strengthUnit(),
                prescriber,
                values);
    }

    private static void flag(EnumMap<Field, String> fields, Field key, Boolean value) {
        if (value != null) fields.put(key, value ? "Y" : "N");
    }

    private static void put(EnumMap<Field, String> fields, Field key, Object value) {
        if (value != null) fields.put(key, value.toString());
    }
}
