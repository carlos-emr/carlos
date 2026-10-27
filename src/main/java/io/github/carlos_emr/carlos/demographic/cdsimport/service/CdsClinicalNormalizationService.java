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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsAllergyDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsClinicalDiagnosticDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImmunizationDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsMedicationDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsMedicationDto.Currentness;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsClinicalException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporal;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsMedicationDose;
import java.io.IOException;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Pure clinical interpretation of one bounded source occurrence; never performs a database write. */
@Service
public class CdsClinicalNormalizationService {
    private static final Set<String> RX_STATUSES = Set.of("New", "Active", "Suspended",
            "Aborted", "Completed", "Obsolete", "Nullified");
    private static final Set<String> TREATMENTS = Set.of("CHRON", "ACU", "ONET",
            "PRN Long-term", "PRN Short-term");
    private final CdsFieldParser fields;

    public CdsClinicalNormalizationService(CdsFieldParser fields) { this.fields = fields; }

    public CdsMedicationDto medication(CdsRecordDto record) throws IOException {
        return medication(record, null);
    }

    public CdsMedicationDto medication(CdsRecordDto record, CdsImportCommand command) throws IOException {
        requireSection(record, "MedicationsAndTreatments");
        List<CdsClinicalDiagnosticDto> diagnostics = new ArrayList<>();
        String status = text(record, "PrescriptionStatus", 10);
        if (status != null && !RX_STATUSES.contains(status)) {
            throw new CdsClinicalException("CDS_RX_STATUS_UNRECOGNIZED", "PrescriptionStatus");
        }
        String treatment = text(record, "TreatmentType", 50);
        if (treatment != null && !TREATMENTS.contains(treatment)) {
            throw new CdsClinicalException("CDS_TREATMENT_TYPE_UNRECOGNIZED", "TreatmentType");
        }
        Boolean past = indicator(record, "PastMedications", false);
        Currentness currentness = currentness(status, past);
        if (currentness == Currentness.UNKNOWN) {
            diagnostics.add(new CdsClinicalDiagnosticDto("CDS_RX_CURRENTNESS_UNKNOWN", "PrescriptionStatus"));
        }
        String duration = numericText(text(record, "Duration", 1024), "Duration", true);
        String quantity = numericText(text(record, "Quantity", 1024), "Quantity", false);
        String dose = text(record, "Dosage", 120);
        if (dose != null && CdsMedicationDose.parse(dose).isEmpty()) {
            diagnostics.add(new CdsClinicalDiagnosticDto("CDS_DOSE_PRESERVED_AS_TEXT", "Dosage"));
        }
        String name = text(record, "DrugName", 120);
        String din = text(record, "DrugIdentificationNumber", 20);
        if ((name == null || name.isBlank()) && (din == null || din.isBlank())) {
            throw new CdsClinicalException("CDS_RX_IDENTITY_REQUIRES_REVIEW", "DrugName");
        }
        String first = text(record, "PrescribedBy/Name/FirstName", 50);
        String last = text(record, "PrescribedBy/Name/LastName", 50);
        String prescriber = first == null && last == null ? null
                : String.join(" ", first == null ? "" : first, last == null ? "" : last).strip();
        if (prescriber != null && prescriber.codePointCount(0, prescriber.length()) > 101) {
            throw new CdsClinicalException("CDS_TARGET_TEXT_CAPACITY_EXCEEDED", "PrescribedBy/Name");
        }
        String strengthAmount = null, strengthUnit = null;
        boolean strengthSourceOnly = false;
        try {
            strengthAmount = text(record, "Strength/Amount", 32768);
            strengthUnit = text(record, "Strength/UnitOfMeasure", 32768);
        } catch (CdsInputException exceedsNativeBudget) {
            if (!"CDS_TARGET_TEXT_CAPACITY_EXCEEDED".equals(exceedsNativeBudget.code())) throw exceedsNativeBudget;
            strengthSourceOnly = true;
            strengthAmount = null; strengthUnit = null;
            diagnostics.add(new CdsClinicalDiagnosticDto("CDS_RX_STRENGTH_COMPLETE_VALUE_IN_SOURCE", "Strength"));
        }
        String doseUnit = text(record, "DosageUnitOfMeasure", 50);
        String form = text(record, "Form", 120), route = text(record, "Route", 120), frequency = text(record, "Frequency", 120);
        Integer repeats = integer(text(record, "NumberOfRefills", 100), "NumberOfRefills");
        boolean requiresReconciliation = strengthSourceOnly || blank(name) || blank(strengthAmount)
                || blank(strengthUnit) || blank(dose) || blank(doseUnit) || blank(route) || blank(frequency)
                || blank(quantity) || repeats == null;
        if (requiresReconciliation) diagnostics.add(new CdsClinicalDiagnosticDto("CDS_RX_RECONCILIATION_REQUIRED", "MedicationsAndTreatments"));
        return new CdsMedicationDto(name, din, text(record, "DrugDescription", 2000),
                strengthAmount, strengthUnit, dose, doseUnit, form, route, frequency,
                duration, integer(text(record, "RefillDuration", 1024), "RefillDuration"), quantity,
                integer(text(record, "RefillQuantity", 1024), "RefillQuantity"),
                repeats,
                indicator(record, "LongTermMedication", false), past,
                indicator(record, "PatientCompliance", false),
                directIndicator(text(record, "SubstitutionNotAllowed", 1), "SubstitutionNotAllowed"),
                directIndicator(text(record, "NonAuthoritativeIndicator", 1), "NonAuthoritativeIndicator"),
                treatment, status, currentness, prescriber,
                text(record, "PrescribedBy/OHIPPhysicianId", 20), first, last, text(record, "Notes", 32768),
                text(record, "PrescriptionInstructions", 32768), text(record, "DispenseInterval", 10),
                text(record, "ProtocolIdentifier", 20),
                text(record, "PriorPrescriptionReferenceIdentifier", 20),
                text(record, "PrescriptionIdentifier", 50), date(record, "PrescriptionWrittenDate", command),
                date(record, "StartDate", command), strengthSourceOnly, requiresReconciliation, diagnostics);
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }

    public CdsAllergyDto allergy(CdsRecordDto record) throws IOException {
        return allergy(record, null);
    }

    public CdsAllergyDto allergy(CdsRecordDto record, CdsImportCommand command) throws IOException {
        requireSection(record, "AllergiesAndAdverseReactions");
        String property = vocabulary(text(record, "PropertyOfOffendingAgent", 2),
                Set.of("DR", "ND", "UK"), "PropertyOfOffendingAgent");
        String reactionType = vocabulary(text(record, "ReactionType", 2), Set.of("AL", "AR"), "ReactionType");
        String severity = vocabulary(text(record, "Severity", 2), Set.of("NO", "MI", "MO", "LT"), "Severity");
        String description = text(record, "OffendingAgentDescription", 120);
        String code = text(record, "Code/CodeValue", 20);
        if ((description == null || description.isBlank()) && (code == null || code.isBlank())) {
            throw new CdsClinicalException("CDS_ALLERGY_AGENT_REQUIRES_REVIEW", "OffendingAgentDescription");
        }
        return new CdsAllergyDto(description, property, code, text(record, "Code/CodeType", 3),
                reactionType, text(record, "Reaction", 120), severity, text(record, "LifeStage", 1),
                text(record, "Notes", 32768), date(record, "StartDate", command), date(record, "RecordedDate", command));
    }

    public CdsImmunizationDto immunization(CdsRecordDto record) throws IOException {
        return immunization(record, null);
    }

    public CdsImmunizationDto immunization(CdsRecordDto record, CdsImportCommand command) throws IOException {
        requireSection(record, "Immunizations");
        String name = text(record, "ImmunizationName", 120);
        if (name == null || name.isBlank()) {
            throw new CdsClinicalException("CDS_IMMUNIZATION_NAME_REQUIRED", "ImmunizationName");
        }
        return new CdsImmunizationDto(name, text(record, "ImmunizationType", 100),
                text(record, "Manufacturer", 120), text(record, "LotNumber", 120),
                text(record, "Route", 120), text(record, "Site", 120), text(record, "Dose", 120),
                text(record, "ImmunizationCode/value", 20), text(record, "ImmunizationCode/CodingSystem", 250),
                indicator(record, "RefusedFlag", true), text(record, "Instructions", 250),
                text(record, "Notes", 32768), date(record, "Date", command));
    }

    public CdsTemporalDto date(CdsRecordDto record, String field) throws IOException {
        return date(record, field, null);
    }

    /** Supports both the actual direct date/dateTime leaves and structured date-choice types. */
    public CdsTemporalDto date(CdsRecordDto record, String field, CdsImportCommand command) throws IOException {
        String direct = fields.firstToken(record, field, 128);
        String year = fields.firstToken(record, field + "/YearOnly", 64);
        String month = fields.firstToken(record, field + "/YearMonth", 64);
        String day = fields.firstToken(record, field + "/FullDate", 64);
        String time = fields.firstToken(record, field + "/FullDateTime", 128);
        if (direct != null) {
            if (year != null || month != null || day != null || time != null) {
                throw new CdsClinicalException("CDS_DATE_CHOICE_CONFLICT", field);
            }
            if (direct.contains("T")) time = direct;
            else day = direct;
        }
        CdsTemporalDto value = CdsTemporal.parse(year, month, day, time, field);
        return command == null ? value : CdsTemporal.shift(value, command.timeshiftDays(), field);
    }

    /** CT035 and PastMedications are independent; missing dates are deliberately irrelevant. */
    public static Currentness currentness(String status, Boolean past) {
        if ("Active".equals(status)) {
            if (Boolean.TRUE.equals(past)) {
                throw new CdsClinicalException("CDS_MEDICATION_STATE_CONFLICT", "PastMedications");
            }
            return Currentness.CURRENT;
        }
        if ("Suspended".equals(status)) return Currentness.SUSPENDED;
        if ("New".equals(status)) return Currentness.NEW;
        if (Set.of("Aborted", "Completed", "Obsolete", "Nullified").contains(status == null ? "" : status)) {
            if (Boolean.FALSE.equals(past)) {
                throw new CdsClinicalException("CDS_MEDICATION_STATE_CONFLICT", "PastMedications");
            }
            return Currentness.HISTORICAL;
        }
        return Boolean.TRUE.equals(past) ? Currentness.HISTORICAL : Currentness.UNKNOWN;
    }

    public static Boolean directIndicator(String value, String field) {
        if (value == null) return null;
        return switch (value) {
            case "Y", "y" -> true;
            case "N", "n" -> false;
            default -> throw new CdsClinicalException("CDS_INDICATOR_INVALID", field);
        };
    }

    private Boolean indicator(CdsRecordDto record, String field, boolean required) throws IOException {
        String simple = fields.firstToken(record, field + "/ynIndicatorsimple", 1);
        String bool = fields.firstToken(record, field + "/boolean", 5);
        if (simple != null && bool != null) throw new CdsClinicalException("CDS_INDICATOR_CHOICE_CONFLICT", field);
        if (simple != null) return directIndicator(simple, field);
        if (bool != null) {
            return switch (bool) {
                case "true", "1" -> true;
                case "false", "0" -> false;
                default -> throw new CdsClinicalException("CDS_INDICATOR_INVALID", field);
            };
        }
        if (required) throw new CdsClinicalException("CDS_INDICATOR_REQUIRED", field);
        return null;
    }

    private String text(CdsRecordDto record, String field, int capacity) throws IOException {
        return fields.firstText(record, field, capacity);
    }

    private static String vocabulary(String value, Set<String> supported, String field) {
        if (value != null && !supported.contains(value)) throw new CdsClinicalException("CDS_CODE_INVALID", field);
        return value;
    }

    private static String numericText(String value, String field, boolean integral) {
        if (value == null) return null;
        if (!value.matches(integral ? "[0-9]+" : "[0-9]+(?:\\.[0-9]+)?")) {
            throw new CdsClinicalException("CDS_NUMERIC_VALUE_REQUIRES_REVIEW", field);
        }
        return value;
    }

    private static Integer integer(String value, String field) {
        if (value == null) return null;
        numericText(value, field, true);
        try { return new BigDecimal(value).intValueExact(); }
        catch (ArithmeticException overflow) { throw new CdsClinicalException("CDS_TARGET_NUMBER_CAPACITY_EXCEEDED", field); }
    }

    private static void requireSection(CdsRecordDto record, String section) {
        if (!record.section().equals(section)) throw new IllegalArgumentException("Wrong CDS clinical section");
    }
}
