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
package io.github.carlos_emr.carlos.demographic.cdsexport.dto;

import java.util.Map;
import java.util.Objects;

/** Exact standard medication values. Native display strings are never split into clinical components. */
public record CdsExportMedicationDto(
        CdsExportDateDto written,
        CdsExportDateDto start,
        String strengthAmount,
        String strengthUnit,
        CdsExportPublicProviderDto prescriber,
        Map<Field, String> fields) {
    public enum Field {
        DrugIdentificationNumber(20),
        DrugName(120),
        NumberOfRefills(100),
        Dosage(120),
        DosageUnitOfMeasure(50),
        Form(120),
        Route(120),
        Frequency(120),
        Duration(1000),
        RefillDuration(1000),
        Quantity(1000),
        RefillQuantity(1000),
        LongTermMedication(1),
        PastMedications(1),
        Notes(32000),
        PrescriptionInstructions(32000),
        PatientCompliance(1),
        TreatmentType(50),
        PrescriptionStatus(10),
        NonAuthoritativeIndicator(1),
        PrescriptionIdentifier(50),
        PriorPrescriptionReferenceIdentifier(20),
        DispenseInterval(10),
        DrugDescription(2000),
        SubstitutionNotAllowed(1),
        ProblemCode(10),
        ProtocolIdentifier(20);
        private final int maximum;

        Field(int maximum) {
            this.maximum = maximum;
        }

        public int maximum() {
            return maximum;
        }

        public boolean indicator() {
            return this == LongTermMedication
                    || this == PastMedications
                    || this == PatientCompliance;
        }

        public boolean directIndicator() {
            return this == NonAuthoritativeIndicator || this == SubstitutionNotAllowed;
        }
    }

    public CdsExportMedicationDto {
        Objects.requireNonNull(written);
        Objects.requireNonNull(start);
        fields = Map.copyOf(fields);
        if ((strengthAmount == null) != (strengthUnit == null)
                || fields.get(Field.DrugName) == null
                        && fields.get(Field.DrugIdentificationNumber) == null) {
            throw new IllegalArgumentException("CDS_EXPORT_MEDICATION_VALUES_REQUIRES_REVIEW");
        }
    }

    @Override
    public String toString() {
        return "CdsExportMedicationDto[protected]";
    }
}
