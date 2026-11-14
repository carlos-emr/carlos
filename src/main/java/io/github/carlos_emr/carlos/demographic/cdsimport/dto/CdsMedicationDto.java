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
package io.github.carlos_emr.carlos.demographic.cdsimport.dto;

import java.util.List;

/**
 * Independent prescription meanings; no catalogue lookup can replace these source values.
 *
 * <p>{@code quantityUnit} and {@code asNeeded} (#6198) are restored from a CARLOS sender's
 * {@code CARLOS.Medication.Source.v1}: the native quantity unit ({@code drugs.unitName}), whose
 * standard Quantity the exporter wrote as the amount followed by that unit, and the exact native
 * as-needed flag ({@code drugs.prn}). Null means not restored; the persister then derives them as
 * before. Both are omitted from the sealed preparation when null, so a payload sealed before they
 * existed keeps its bytes and digest ({@code CdsPreparedMedicationDefaults} supplies them after
 * seal verification).
 */
public record CdsMedicationDto(String name, String din, String description, String strengthAmount,
        String strengthUnit, String dose, String doseUnit, String form, String route,
        String frequency, String duration, String durationUnit, Integer refillDurationDays, String quantity,
        Integer refillQuantity, Integer repeats, Boolean longTerm, Boolean past,
        Boolean compliance, Boolean substitutionNotAllowed, Boolean nonAuthoritative,
        String treatmentType, String prescriptionStatus, Currentness currentness,
        String prescriberName, String prescriberOhip, String prescriberFirstName, String prescriberLastName,
        String providerNotes,
        String pharmacyInstructions, String dispenseInterval, String protocol,
        String priorPrescription, String prescriptionIdentifier, CdsTemporalDto written,
        CdsTemporalDto start, boolean strengthSourceOnly, boolean sourceRequiresReconciliation,
        List<CdsClinicalDiagnosticDto> diagnostics,
        @com.fasterxml.jackson.annotation.JsonInclude(
                        com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                String quantityUnit,
        @com.fasterxml.jackson.annotation.JsonInclude(
                        com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                Boolean asNeeded) {
    public enum Currentness { CURRENT, HISTORICAL, SUSPENDED, NEW, UNKNOWN }
    public CdsMedicationDto {
        if (strengthSourceOnly && !sourceRequiresReconciliation) {
            throw new IllegalArgumentException("A source-only medication strength requires clinical reconciliation");
        }
        if (quantityUnit != null && quantityUnit.isEmpty())
            throw new IllegalArgumentException("CDS_MEDICATION_QUANTITY_UNIT_INVALID");
        diagnostics = List.copyOf(diagnostics);
    }

    /** A prescription without restored native context (any source but a CARLOS sender's carrier). */
    public CdsMedicationDto(String name, String din, String description, String strengthAmount,
            String strengthUnit, String dose, String doseUnit, String form, String route,
            String frequency, String duration, String durationUnit, Integer refillDurationDays,
            String quantity, Integer refillQuantity, Integer repeats, Boolean longTerm, Boolean past,
            Boolean compliance, Boolean substitutionNotAllowed, Boolean nonAuthoritative,
            String treatmentType, String prescriptionStatus, Currentness currentness,
            String prescriberName, String prescriberOhip, String prescriberFirstName,
            String prescriberLastName, String providerNotes, String pharmacyInstructions,
            String dispenseInterval, String protocol, String priorPrescription,
            String prescriptionIdentifier, CdsTemporalDto written, CdsTemporalDto start,
            boolean strengthSourceOnly, boolean sourceRequiresReconciliation,
            List<CdsClinicalDiagnosticDto> diagnostics) {
        this(name, din, description, strengthAmount, strengthUnit, dose, doseUnit, form, route,
                frequency, duration, durationUnit, refillDurationDays, quantity, refillQuantity,
                repeats, longTerm, past, compliance, substitutionNotAllowed, nonAuthoritative,
                treatmentType, prescriptionStatus, currentness, prescriberName, prescriberOhip,
                prescriberFirstName, prescriberLastName, providerNotes, pharmacyInstructions,
                dispenseInterval, protocol, priorPrescription, prescriptionIdentifier, written,
                start, strengthSourceOnly, sourceRequiresReconciliation, diagnostics, null, null);
    }
}
