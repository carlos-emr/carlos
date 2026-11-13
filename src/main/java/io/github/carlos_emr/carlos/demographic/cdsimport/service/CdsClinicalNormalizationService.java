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

import com.google.common.base.Ascii;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsAllergyDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsClinicalDiagnosticDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImmunizationDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsMedicationDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsMedicationDto.Currentness;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto;
import io.github.carlos_emr.carlos.commn.model.CdsClinicalException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporal;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsMedicationDose;
import java.io.IOException;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Pure clinical interpretation of one bounded source occurrence; never performs a database write. */
@Service
public class CdsClinicalNormalizationService {
    private static final Set<String> RX_STATUSES =
            Set.of("New", "Active", "Suspended", "Aborted", "Completed", "Obsolete", "Nullified");
    private static final Set<String> TREATMENTS =
            Set.of("CHRON", "ACU", "ONET", "PRN Long-term", "PRN Short-term");
    private static final Pattern MEDICATION_DURATION = Pattern.compile(
            "([0-9]+)\\s*(D|DAYS?|W|WEEKS?|M|MONTHS?|Y|YEARS?)?", Pattern.CASE_INSENSITIVE);
    /** The native {@code drugs.unitName} capacity, and the exporter's DosageUnitOfMeasure bound. */
    private static final int QUANTITY_UNIT_CAPACITY = 50;
    private final CdsFieldParser fields;

    public CdsClinicalNormalizationService(CdsFieldParser fields) {
        this.fields = fields;
    }

    /** Exact native archive state from the shared versioned carrier; unknown vendor content stays opaque. */
    public boolean medicationArchived(CdsRecordDto record) throws IOException {
        requireSection(record, "MedicationsAndTreatments");
        return new CdsMedicationResidualReader(fields).read(record).archived();
    }

    public CdsMedicationDto medication(CdsRecordDto record) throws IOException {
        return medication(record, null);
    }

    public CdsMedicationDto medication(CdsRecordDto record, CdsImportCommand command)
            throws IOException {
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
            diagnostics.add(
                    new CdsClinicalDiagnosticDto(
                            "CDS_RX_CURRENTNESS_UNKNOWN", "PrescriptionStatus"));
        }
        String durationText = text(record, "Duration", 1024);
        String duration = null, durationUnit = null;
        if (durationText != null) {
            var match = MEDICATION_DURATION.matcher(durationText.strip());
            if (!match.matches()) {
                throw new CdsClinicalException("CDS_NUMERIC_VALUE_REQUIRES_REVIEW", "Duration");
            }
            duration = match.group(1);
            durationUnit = match.group(2) == null ? "D"
                    : Ascii.toUpperCase(match.group(2).substring(0, 1));
        }
        // #6198: a CARLOS sender's complete native row restores the quantity unit and the
        // as-needed flag, which CDS has no element for, only when the standard elements the
        // exporter wrote from the same row agree with it.
        var carried = new CdsMedicationResidualReader(fields).read(record).source();
        String standardQuantity = text(record, "Quantity", 1024);
        String quantity, quantityUnit = null;
        if (carried != null && carried.quantityUnit() != null) {
            quantityUnit = carried.quantityUnit();
            quantity = carriedQuantity(standardQuantity, carried);
        } else quantity = numericText(standardQuantity, "Quantity", false);
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
        String prescriber =
                first == null && last == null
                        ? null
                        : String.join(" ", first == null ? "" : first, last == null ? "" : last)
                                .strip();
        if (prescriber != null && prescriber.codePointCount(0, prescriber.length()) > 101) {
            throw new CdsClinicalException(
                    "CDS_TARGET_TEXT_CAPACITY_EXCEEDED", "PrescribedBy/Name");
        }
        String strengthAmount = null, strengthUnit = null;
        boolean strengthSourceOnly = false;
        try {
            strengthAmount = text(record, "Strength/Amount", 32768);
            strengthUnit = text(record, "Strength/UnitOfMeasure", 32768);
        } catch (CdsInputException exceedsNativeBudget) {
            if (!"CDS_TARGET_TEXT_CAPACITY_EXCEEDED".equals(exceedsNativeBudget.code()))
                throw exceedsNativeBudget;
            strengthSourceOnly = true;
            strengthAmount = null;
            strengthUnit = null;
            diagnostics.add(
                    new CdsClinicalDiagnosticDto(
                            "CDS_RX_STRENGTH_COMPLETE_VALUE_IN_SOURCE", "Strength"));
        }
        String doseUnit = text(record, "DosageUnitOfMeasure", 50);
        String form = text(record, "Form", 120),
                route = text(record, "Route", 120),
                frequency = text(record, "Frequency", 120);
        Integer repeats = integer(text(record, "NumberOfRefills", 100), "NumberOfRefills");
        Boolean asNeeded = carried == null ? null : carriedAsNeeded(carried, frequency, treatment);
        String description = text(record, "DrugDescription", 2000);
        // M6: the native strength column holds only the standard Strength. A description naming
        // other strengths (a combination product such as "875 mg / 125 mg", where Strength is
        // the first ingredient) means the native strength is incomplete, so the prescription is
        // not renewable until reconciled. The same strength restated per dosage unit ("500
        // mg/capsule") is not another strength (#6197).
        boolean otherStrengths = describesOtherStrengths(description, strengthAmount, strengthUnit);
        if (otherStrengths)
            diagnostics.add(
                    new CdsClinicalDiagnosticDto(
                            "CDS_RX_DESCRIPTION_NAMES_OTHER_STRENGTHS", "DrugDescription"));
        boolean requiresReconciliation =
                strengthSourceOnly
                        || otherStrengths
                        || blank(name)
                        || blank(strengthAmount)
                        || blank(strengthUnit)
                        || blank(dose)
                        || blank(doseUnit)
                        || blank(route)
                        || blank(frequency)
                        || blank(quantity)
                        || repeats == null;
        if (requiresReconciliation)
            diagnostics.add(
                    new CdsClinicalDiagnosticDto(
                            "CDS_RX_RECONCILIATION_REQUIRED", "MedicationsAndTreatments"));
        return new CdsMedicationDto(
                name,
                din,
                description,
                strengthAmount,
                strengthUnit,
                dose,
                doseUnit,
                form,
                route,
                frequency,
                duration,
                durationUnit,
                integer(text(record, "RefillDuration", 1024), "RefillDuration"),
                quantity,
                integer(text(record, "RefillQuantity", 1024), "RefillQuantity"),
                repeats,
                indicator(record, "LongTermMedication", false),
                past,
                indicator(record, "PatientCompliance", false),
                directIndicator(
                        text(record, "SubstitutionNotAllowed", 1), "SubstitutionNotAllowed"),
                directIndicator(
                        text(record, "NonAuthoritativeIndicator", 1), "NonAuthoritativeIndicator"),
                treatment,
                status,
                currentness,
                prescriber,
                text(record, "PrescribedBy/OHIPPhysicianId", 20),
                first,
                last,
                text(record, "Notes", 32768),
                text(record, "PrescriptionInstructions", 32768),
                text(record, "DispenseInterval", 10),
                text(record, "ProtocolIdentifier", 20),
                text(record, "PriorPrescriptionReferenceIdentifier", 20),
                text(record, "PrescriptionIdentifier", 50),
                date(record, "PrescriptionWrittenDate", command),
                date(record, "StartDate", command),
                strengthSourceOnly,
                requiresReconciliation,
                diagnostics,
                quantityUnit,
                asNeeded);
    }

    /**
     * The native quantity behind a standard Quantity that the CARLOS exporter wrote as the amount
     * followed by the carried quantity unit ("100 mL"). The carrier and the standard element must
     * agree exactly: a bare amount, another amount or unit, or a Quantity without the carried
     * amount (or the reverse) is contradictory and requires review, never a guess.
     */
    private static String carriedQuantity(
            String standard, CdsMedicationResidualReader.SourceContext carried) {
        String amount = carried.quantity(), unit = carried.quantityUnit();
        if (unit.codePointCount(0, unit.length()) > QUANTITY_UNIT_CAPACITY)
            throw new CdsClinicalException(
                    "CDS_MEDICATION_NATIVE_STATE_REQUIRES_REVIEW", "ResidualInfo");
        boolean recorded = amount != null && !amount.isEmpty();
        if (standard == null && !recorded) return null;
        if (standard == null
                || !recorded
                || !amount.matches("[0-9]+(?:\\.[0-9]+)?")
                || !standard.equals(amount + " " + unit.strip()))
            throw new CdsClinicalException("CDS_MEDICATION_NATIVE_FIELD_REQUIRES_REVIEW", "Quantity");
        return amount;
    }

    /**
     * The exact native as-needed flag. The CARLOS exporter always shows a set flag in the
     * standard elements (a PRN Frequency such as "PRN" or "BID PRN", or a PRN TreatmentType), so a
     * set flag beside elements that name no PRN is contradictory and requires review. An unset
     * flag beside a PRN frequency is an ordinary saved prescription (the box left unticked) and
     * is restored as recorded. A captured SQL NULL restores nothing.
     */
    private static Boolean carriedAsNeeded(
            CdsMedicationResidualReader.SourceContext carried, String frequency, String treatment) {
        if (carried.prn() == null) return null;
        boolean flagged =
                switch (carried.prn()) {
                    case "1" -> true;
                    case "0" -> false;
                    default ->
                            throw new CdsClinicalException(
                                    "CDS_MEDICATION_NATIVE_STATE_REQUIRES_REVIEW", "ResidualInfo");
                };
        boolean named =
                frequency != null && Ascii.toUpperCase(frequency).contains("PRN")
                        || treatment != null && treatment.startsWith("PRN ");
        if (flagged && !named)
            throw new CdsClinicalException("CDS_MEDICATION_NATIVE_FIELD_REQUIRES_REVIEW", "Frequency");
        return flagged;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public CdsAllergyDto allergy(CdsRecordDto record) throws IOException {
        return allergy(record, null);
    }

    public CdsAllergyDto allergy(CdsRecordDto record, CdsImportCommand command) throws IOException {
        requireSection(record, "AllergiesAndAdverseReactions");
        String property =
                vocabulary(
                        text(record, "PropertyOfOffendingAgent", 2),
                        Set.of("DR", "ND", "UK"),
                        "PropertyOfOffendingAgent");
        String reactionType =
                vocabulary(text(record, "ReactionType", 2), Set.of("AL", "AR"), "ReactionType");
        String severity =
                vocabulary(text(record, "Severity", 2), Set.of("NO", "MI", "MO", "LT"), "Severity");
        String description = text(record, "OffendingAgentDescription", 120);
        String code = text(record, "Code/CodeValue", 20);
        String codeSystem = text(record, "Code/CodeType", 3);
        var nativeState = new CdsAllergyResidualReader(fields).read(record);
        if (nativeState.atc() != null) {
            // One native coding slot cannot represent two independent coding authorities.
            if (code != null || codeSystem != null)
                throw new CdsClinicalException("CDS_ALLERGY_CODE_CONFLICT_REQUIRES_REVIEW", "Code");
            code = nativeState.atc();
            codeSystem = "ATC";
        }
        if ((description == null || description.isBlank()) && (code == null || code.isBlank())) {
            throw new CdsClinicalException(
                    "CDS_ALLERGY_AGENT_REQUIRES_REVIEW", "OffendingAgentDescription");
        }
        // The CARLOS severity carrier exists only because the standard Severity has no value
        // for it; a record asserting both is contradictory, never resolved by precedence.
        if (nativeState.nativeSeverity() != null && severity != null)
            throw new CdsClinicalException("CDS_ALLERGY_NATIVE_FIELD_REQUIRES_REVIEW", "Severity");
        // A reaction longer than the standard element travels only in CARLOS.Allergy.Reaction.v1
        // (its Notes repeat it for other receivers); the carrier restores it exactly, and a record
        // asserting both a standard Reaction and the carrier is contradictory.
        String reaction = text(record, "Reaction", 120);
        if (nativeState.reaction() != null) {
            if (reaction != null)
                throw new CdsClinicalException(
                        "CDS_ALLERGY_NATIVE_FIELD_REQUIRES_REVIEW", "Reaction");
            reaction = nativeState.reaction();
        }
        return new CdsAllergyDto(
                description,
                property,
                code,
                codeSystem,
                reactionType,
                reaction,
                severity,
                text(record, "LifeStage", 1),
                text(record, "Notes", 32768),
                date(record, "StartDate", command),
                date(record, "RecordedDate", command),
                nativeState.archived(),
                nativeState.ageOfOnset(),
                nativeState.onsetOfReaction(),
                nativeState.atc() != null,
                // Default (decision 9): a source archive marker is not restored natively.
                nativeState.archived(),
                nativeState.nativeSeverity());
    }

    public CdsImmunizationDto immunization(CdsRecordDto record) throws IOException {
        return immunization(record, null);
    }

    public CdsImmunizationDto immunization(CdsRecordDto record, CdsImportCommand command)
            throws IOException {
        requireSection(record, "Immunizations");
        String name = text(record, "ImmunizationName", 120);
        if (name == null || name.isBlank()) {
            throw new CdsClinicalException("CDS_IMMUNIZATION_NAME_REQUIRED", "ImmunizationName");
        }
        String type = text(record, "ImmunizationType", 100),
                manufacturer = text(record, "Manufacturer", 120),
                lot = text(record, "LotNumber", 120),
                route = text(record, "Route", 120),
                site = text(record, "Site", 120),
                dose = text(record, "Dose", 120),
                code = text(record, "ImmunizationCode/value", 20),
                codeSystem = text(record, "ImmunizationCode/CodingSystem", 250);
        boolean refused = indicator(record, "RefusedFlag", true);
        String instructions = text(record, "Instructions", 250), notes = text(record, "Notes", 32768);
        CdsTemporalDto date = date(record, "Date", command);
        // #6198: a CARLOS sender's complete native row restores the free-text administering
        // provider and the reminder date, which CDS has no Immunizations element for.
        var carried = new CdsImmunizationResidualReader(fields).read(record);
        String providerName = null, reminderDate = null;
        if (carried != null) {
            // The carrier must describe this event: its native status is the RefusedFlag the
            // exporter wrote from it (given and completed externally N; refused and ineligible Y).
            boolean nativeRefused =
                    switch (carried.refused() == null ? "" : carried.refused()) {
                        case "0", "3" -> false;
                        case "1", "2" -> true;
                        default ->
                                throw new CdsClinicalException(
                                        "CDS_IMMUNIZATION_NATIVE_STATE_REQUIRES_REVIEW",
                                        "ResidualInfo");
                    };
            if (nativeRefused != refused)
                throw new CdsClinicalException(
                        "CDS_IMMUNIZATION_NATIVE_FIELD_REQUIRES_REVIEW", "RefusedFlag");
            // Only the form's "Other" provider (-1) is a free-text name; a CARLOS provider number
            // belongs to the sender and is never restored. Two recorded names are not resolved
            // by first/last precedence.
            if ("-1".equals(carried.providerNo()) && !carried.providerNames().isEmpty()) {
                if (carried.providerNames().size() != 1)
                    throw new CdsClinicalException(
                            "CDS_IMMUNIZATION_NATIVE_STATE_REQUIRES_REVIEW", "ResidualInfo");
                providerName = carried.providerNames().getFirst();
            }
            reminderDate = reminderDate(carried.nextDate(), command);
        }
        return new CdsImmunizationDto(
                name,
                type,
                manufacturer,
                lot,
                route,
                site,
                dose,
                code,
                codeSystem,
                refused,
                instructions,
                notes,
                date,
                providerName,
                reminderDate);
    }

    /**
     * The native reminder date ({@code preventions.next_date}, captured as {@code yyyy-MM-dd}),
     * shifted by the authorized synthetic-copy days like every other clinical date so a shifted
     * chart never keeps a real reminder date. MySQL's zero date is how a legacy row records no
     * reminder (the prevention form shows it empty); any other value that is not a civil date in
     * years 1-9999, before or after the shift, requires review.
     */
    private static String reminderDate(String captured, CdsImportCommand command) {
        if (captured == null || captured.equals("0000-00-00")) return null;
        java.time.LocalDate date;
        try {
            date =
                    java.time.LocalDate.parse(
                            captured,
                            java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd")
                                    .withResolverStyle(java.time.format.ResolverStyle.STRICT));
            if (command != null && command.timeshiftDays() != 0)
                date = date.plusDays(command.timeshiftDays());
        } catch (java.time.DateTimeException malformed) {
            date = null;
        }
        if (date == null || date.getYear() < 1 || date.getYear() > 9999)
            throw new CdsClinicalException(
                    "CDS_IMMUNIZATION_NATIVE_STATE_REQUIRES_REVIEW", "ResidualInfo");
        return date.toString();
    }

    public CdsTemporalDto date(CdsRecordDto record, String field) throws IOException {
        return date(record, field, null);
    }

    /** Supports both the actual direct date/dateTime leaves and structured date-choice types. */
    public CdsTemporalDto date(CdsRecordDto record, String field, CdsImportCommand command)
            throws IOException {
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
        if (Set.of("Aborted", "Completed", "Obsolete", "Nullified")
                .contains(status == null ? "" : status)) {
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

    private Boolean indicator(CdsRecordDto record, String field, boolean required)
            throws IOException {
        String simple = fields.firstToken(record, field + "/ynIndicatorsimple", 1);
        String bool = fields.firstToken(record, field + "/boolean", 5);
        if (simple != null && bool != null)
            throw new CdsClinicalException("CDS_INDICATOR_CHOICE_CONFLICT", field);
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
        if (value != null && !supported.contains(value))
            throw new CdsClinicalException("CDS_CODE_INVALID", field);
        return value;
    }

    private static final String STRENGTH_AMOUNT = "[0-9]+(?:[.,][0-9]+)?";
    private static final String STRENGTH_UNIT =
            "[A-Za-z%\u00b5\u03bc][A-Za-z0-9%\u00b5\u03bc]*";

    // A strength is an amount and a unit, optionally per a quantity: a measured quantity
    // ("250 mg/5 mL", a concentration) or one dosage unit ("500 mg/capsule"). Groups: amount,
    // unit, per-quantity amount (optional), per-quantity unit.
    private static final Pattern STRENGTH_TOKEN =
            Pattern.compile(
                    "("
                            + STRENGTH_AMOUNT
                            + ")\\s*("
                            + STRENGTH_UNIT
                            + ")(?:\\s*/\\s*("
                            + STRENGTH_AMOUNT
                            + ")?\\s*("
                            + STRENGTH_UNIT
                            + "))?");

    /** The standard Strength/UnitOfMeasure, which may itself be per a quantity ("mg/5 mL"). */
    private static final Pattern STRENGTH_UNIT_TEXT =
            Pattern.compile(
                    "("
                            + STRENGTH_UNIT
                            + ")(?:\\s*/\\s*("
                            + STRENGTH_AMOUNT
                            + ")?\\s*("
                            + STRENGTH_UNIT
                            + "))?");

    /**
     * Words naming one dosage unit (#6197). "500 mg/capsule", "500mg / tab" or "500 mg/1 tablet"
     * restates the per-unit strength 500 mg and names no other strength. A measured quantity
     * ("/5 mL"), a rate ("/h"), another ingredient ("/125 mg") or more than one unit ("/2
     * tablets") is not a dosage unit, so such a description still names another strength.
     */
    private static final Set<String> DOSAGE_UNITS =
            Set.of(
                    "tablet", "tablets", "tab", "tabs", "capsule", "capsules", "cap", "caps",
                    "caplet", "caplets", "pill", "pills", "lozenge", "lozenges", "troche",
                    "troches", "suppository", "suppositories", "supp", "supps", "patch",
                    "patches", "dose", "doses", "puff", "puffs", "actuation", "actuations",
                    "spray", "sprays", "vial", "vials", "ampoule", "ampoules", "ampule",
                    "ampules", "amp", "amps", "sachet", "sachets", "packet", "packets");

    /**
     * True when the description names a strength other than the standard Strength (M6). A
     * combination ("875 mg / 125 mg") or a concentration ("250 mg/5 mL" beside Strength 250 mg,
     * whose native column would lose the volume) is another strength. The same amount and unit
     * restated per one dosage unit ("500 mg/capsule", "500 mg per tablet") is not.
     */
    static boolean describesOtherStrengths(String description, String amount, String unit) {
        if (description == null || blank(amount) || blank(unit)) return false;
        String standard = standardStrengthKey(amount.strip(), unit.strip());
        if (standard == null) return false;
        var matcher = STRENGTH_TOKEN.matcher(description);
        while (matcher.find()) {
            String token =
                    strengthKey(
                            matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4));
            if (token != null && !token.equals(standard)) return true;
        }
        return false;
    }

    private static String standardStrengthKey(String amount, String unit) {
        var parsed = STRENGTH_UNIT_TEXT.matcher(unit);
        if (parsed.matches())
            return strengthKey(amount, parsed.group(1), parsed.group(2), parsed.group(3));
        // A unit outside the strength grammar ("MG (base)") is compared as exact text, so any
        // strength the description names reads as another one.
        String number = strengthAmount(amount);
        return number == null ? null : number + " " + Ascii.toLowerCase(unit.replaceAll("\\s+", ""));
    }

    private static String strengthKey(
            String amount, String unit, String perAmount, String perUnit) {
        String number = strengthAmount(amount);
        if (number == null) return null;
        String key = number + " " + Ascii.toLowerCase(unit);
        if (perUnit == null) return key;
        String per = Ascii.toLowerCase(perUnit);
        String count = perAmount == null ? null : strengthAmount(perAmount);
        if (perAmount != null && count == null) return null;
        if (DOSAGE_UNITS.contains(per) && (count == null || count.equals("1"))) return key;
        return key + "/" + (count == null ? "" : count) + per;
    }

    private static String strengthAmount(String amount) {
        try {
            return new BigDecimal(amount.replace(',', '.')).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException notNumeric) {
            return null;
        }
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
        try {
            return new BigDecimal(value).intValueExact();
        } catch (ArithmeticException overflow) {
            throw new CdsClinicalException("CDS_TARGET_NUMBER_CAPACITY_EXCEEDED", field);
        }
    }

    private static void requireSection(CdsRecordDto record, String section) {
        if (!record.section().equals(section))
            throw new IllegalArgumentException("Wrong CDS clinical section");
    }
}
