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

/** Independent prescription meanings; no catalogue lookup can replace these source values. */
public record CdsMedicationDto(String name, String din, String description, String strengthAmount,
        String strengthUnit, String dose, String doseUnit, String form, String route,
        String frequency, String durationDays, Integer refillDurationDays, String quantity,
        Integer refillQuantity, Integer repeats, Boolean longTerm, Boolean past,
        Boolean compliance, Boolean substitutionNotAllowed, Boolean nonAuthoritative,
        String treatmentType, String prescriptionStatus, Currentness currentness,
        String prescriberName, String prescriberOhip, String providerNotes,
        String pharmacyInstructions, String dispenseInterval, String protocol,
        String priorPrescription, String prescriptionIdentifier, CdsTemporalDto written,
        CdsTemporalDto start, List<CdsClinicalDiagnosticDto> diagnostics) {
    public enum Currentness { CURRENT, HISTORICAL, SUSPENDED, NEW, UNKNOWN }
    public CdsMedicationDto { diagnostics = List.copyOf(diagnostics); }
}
