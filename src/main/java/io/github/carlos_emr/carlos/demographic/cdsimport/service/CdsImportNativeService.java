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

import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsAllergyDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImmunizationDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportActorDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportReceiptDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportUnitDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsMedicationNativeDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsSnapshotDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Composes focused writers inside the already-owned unit; never owns a whole-patient transaction. */
@Service
public class CdsImportNativeService {
    private static final Set<String> HISTORIES =
            Set.of(
                    "ClinicalNotes",
                    "PersonalHistory",
                    "FamilyHistory",
                    "PastHealth",
                    "ProblemList",
                    "RiskFactors",
                    "AlertsAndSpecialNeeds");
    private final CdsFieldParser fields;
    private final CdsClinicalNormalizationService normalization;
    private final CdsMedicationPersister medications;
    private final CdsAllergyPersister allergies;
    private final CdsImmunizationPersister immunizations;
    private final CdsDemographicPersister demographics;
    private final CdsHistoryPersister histories;
    private final CdsAppointmentPersister appointments;
    private final CdsNativePreparationService preparation;
    private final CdsProviderResolver providers;
    private final CdsSourceFieldService sourceFields;
    private final CdsImportAccessService access;
    private final CdsImportLedgerService ledger;
    private final CdsTargetReceiptService receipts;
    private final Map<String, CdsCategoryImportService> categories;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    public CdsImportNativeService(
            CdsFieldParser fields,
            CdsClinicalNormalizationService normalization,
            CdsMedicationPersister medications,
            CdsAllergyPersister allergies,
            CdsImmunizationPersister immunizations,
            CdsDemographicPersister demographics,
            CdsHistoryPersister histories,
            CdsAppointmentPersister appointments,
            CdsNativePreparationService preparation,
            CdsProviderResolver providers,
            CdsSourceFieldService sourceFields,
            CdsImportAccessService access,
            CdsImportLedgerService ledger,
            CdsTargetReceiptService receipts,
            List<CdsCategoryImportService> categories) {
        this.fields = fields;
        this.normalization = normalization;
        this.medications = medications;
        this.allergies = allergies;
        this.immunizations = immunizations;
        this.demographics = demographics;
        this.histories = histories;
        this.appointments = appointments;
        this.preparation = preparation;
        this.providers = providers;
        this.sourceFields = sourceFields;
        this.access = access;
        this.ledger = ledger;
        this.receipts = receipts;
        Map<String, CdsCategoryImportService> selected = new HashMap<>();
        for (CdsCategoryImportService category : categories)
            for (String section : category.sections()) {
                if (selected.putIfAbsent(section, category) != null)
                    throw new IllegalStateException("Duplicate CDS category mapping");
            }
        this.categories = Map.copyOf(selected);
    }

    public void validate(CdsRecordDto record, CdsImportCommand command) throws IOException {
        validate(command, record, null, null);
    }

    public void validate(
            CdsImportCommand command,
            CdsRecordDto record,
            CdsSnapshotDto snapshot,
            String xmlRelative)
            throws IOException {
        CdsImportLedgerService.requireNoTransaction();
        fields.verifyRecord(record);
        switch (record.section()) {
            case "MedicationsAndTreatments" -> normalization.medication(record, command);
            case "AllergiesAndAdverseReactions" -> normalization.allergy(record, command);
            case "Immunizations" -> normalization.immunization(record, command);
            case "Demographics" -> demographics.validate(record, command);
            case "Appointments" -> appointments.validate(record, command);
            case "CareElements", "NewCategory" -> {
                /* Bounded child units and source-field units carry their logical values. */
            }
            default -> {
                if (HISTORIES.contains(record.section())) histories.validate(record, command);
                else category(record.section()).validate(record, command, snapshot, xmlRelative);
            }
        }
    }

    /** Large IO and checksum verification finish before the writer obtains its bounded DB transaction. */
    public void prepare(
            String run,
            long epoch,
            CdsImportUnitDto unit,
            CdsRecordDto record,
            CdsImportCommand command,
            CdsSnapshotDto snapshot,
            String xmlRelative)
            throws IOException {
        CdsImportLedgerService.requireNoTransaction();
        if (unit.category().endsWith("_FIELDS")) return;
        switch (record.section()) {
            case "Demographics" ->
                    demographics.prepare(run, epoch, unit.unitKey(), record, command);
            case "Appointments" ->
                    appointments.prepare(run, epoch, unit.unitKey(), record, command);
            case "MedicationsAndTreatments" -> {
                var source = normalization.medication(record, command);
                var provider =
                        providers.resolve(
                                source.prescriberOhip(),
                                null,
                                source.prescriberFirstName(),
                                source.prescriberLastName(),
                                command.matchProviderNames());
                preparation.store(
                        run,
                        epoch,
                        unit.unitKey(),
                        record.effectiveSha256(),
                        command,
                        new CdsMedicationNativeDto(source, provider));
            }
            case "AllergiesAndAdverseReactions" ->
                    preparation.store(
                            run,
                            epoch,
                            unit.unitKey(),
                            record.effectiveSha256(),
                            command,
                            normalization.allergy(record, command));
            case "Immunizations" ->
                    preparation.store(
                            run,
                            epoch,
                            unit.unitKey(),
                            record.effectiveSha256(),
                            command,
                            normalization.immunization(record, command));
            default -> {
                if (HISTORIES.contains(record.section()))
                    histories.prepare(run, epoch, unit.unitKey(), record, command);
                else {
                    CdsCategoryImportService category = categories.get(record.section());
                    if (category != null)
                        category.prepare(
                                run, epoch, unit.unitKey(), record, command, snapshot, xmlRelative);
                }
            }
        }
    }

    public CdsImportReceiptDto write(
            String run,
            long epoch,
            CdsImportUnitDto unit,
            CdsRecordDto record,
            CdsImportCommand command,
            CdsImportActorDto actor,
            CdsSnapshotDto snapshot,
            String xmlRelative)
            throws IOException {
        CdsImportUnitService.requireActive(run, epoch, unit.unitKey());
        if ("Demographics".equals(unit.category())) {
            access.context(actor, command, null);
            Map<String, String> target =
                    demographics.write(
                            run,
                            epoch,
                            unit.unitKey(),
                            record,
                            command,
                            diagnostic ->
                                    notice(
                                            run,
                                            epoch,
                                            unit,
                                            "WARNING",
                                            diagnostic.code(),
                                            diagnostic.field()));
            int demographicNo = Integer.parseInt(target.get("demographic"));
            access.context(actor, command, demographicNo);
            ledger.bindTarget(run, epoch, unit.patientKey(), demographicNo);
            if (command.timeshiftDays() != 0)
                notice(
                        run,
                        epoch,
                        unit,
                        "WARNING",
                        "CDS_SYNTHETIC_DATE_SHIFT_APPLIED",
                        "DateOfBirth");
            return receipts.capture(target, run, unit.unitKey());
        }
        Integer target =
                entityManager
                        .createQuery(
                                "select p.targetDemographicNo from CdsImportPatient p "
                                        + "where p.runId=:run and p.patientKey=:patient",
                                Integer.class)
                        .setParameter("run", run)
                        .setParameter("patient", unit.patientKey())
                        .getSingleResult();
        if (target == null) throw new CdsImportException("PATIENT_TARGET_NOT_BOUND");
        access.context(actor, command, target);
        if (unit.category().endsWith("_FIELDS"))
            return sourceFields.write(run, epoch, unit.unitKey(), record);
        Map<String, String> written;
        switch (record.section()) {
            case "MedicationsAndTreatments" -> {
                var prepared =
                        preparation.load(
                                run,
                                epoch,
                                unit.unitKey(),
                                record.effectiveSha256(),
                                command,
                                CdsMedicationNativeDto.class);
                var source = prepared.source();
                if (!prepared.provider().matched())
                    notice(
                            run,
                            epoch,
                            unit,
                            "WARNING",
                            "CDS_MEDICATION_PROVIDER_" + prepared.provider().outcome(),
                            "PrescribedBy");
                for (var diagnostic : source.diagnostics())
                    notice(run, epoch, unit, "WARNING", diagnostic.code(), diagnostic.field());
                temporalNotice(run, epoch, unit, "PrescriptionWrittenDate", source.written());
                temporalNotice(run, epoch, unit, "StartDate", source.start());
                written =
                        medications.write(
                                target, command.providerNo(), source, prepared.provider());
            }
            case "AllergiesAndAdverseReactions" -> {
                var source =
                        preparation.load(
                                run,
                                epoch,
                                unit.unitKey(),
                                record.effectiveSha256(),
                                command,
                                CdsAllergyDto.class);
                temporalNotice(run, epoch, unit, "StartDate", source.start());
                temporalNotice(run, epoch, unit, "RecordedDate", source.recorded());
                written = allergies.write(target, command.providerNo(), source);
            }
            case "Immunizations" -> {
                var source =
                        preparation.load(
                                run,
                                epoch,
                                unit.unitKey(),
                                record.effectiveSha256(),
                                command,
                                CdsImmunizationDto.class);
                temporalNotice(run, epoch, unit, "Date", source.date());
                written = immunizations.write(target, command.providerNo(), source);
            }
            case "Appointments" ->
                    written =
                            appointments.write(
                                    run,
                                    epoch,
                                    unit.unitKey(),
                                    target,
                                    record,
                                    command,
                                    diagnostic ->
                                            notice(
                                                    run,
                                                    epoch,
                                                    unit,
                                                    "WARNING",
                                                    diagnostic.code(),
                                                    diagnostic.field()));
            case "CareElements", "NewCategory" ->
                    written = Map.of("demographic", target.toString());
            default ->
                    written =
                            HISTORIES.contains(record.section())
                                    ? histories.write(
                                            run,
                                            epoch,
                                            unit.unitKey(),
                                            target,
                                            record,
                                            command,
                                            diagnostic ->
                                                    notice(
                                                            run,
                                                            epoch,
                                                            unit,
                                                            "WARNING",
                                                            diagnostic.code(),
                                                            diagnostic.field()))
                                    : category(record.section())
                                            .write(
                                                    run,
                                                    epoch,
                                                    unit.unitKey(),
                                                    target,
                                                    record,
                                                    command);
        }
        return receipts.capture(written, run, unit.unitKey());
    }

    public boolean verify(CdsImportReceiptDto receipt) {
        return receipts.verify(receipt);
    }

    public void forEachNativeChild(
            CdsRecordDto record, String suffix, Consumer<CdsRecordDto> children)
            throws IOException {
        if ("_CONTACT".equals(suffix) && "Demographics".equals(record.section()))
            fields.forEachSubrecord(record, "Contact", children);
        else if ("_CARE".equals(suffix) && "CareElements".equals(record.section()))
            fields.forEachSubrecord(record, "*", children);
        else throw new CdsImportException("NATIVE_CHILD_KIND_INVALID");
    }

    private CdsCategoryImportService category(String section) {
        CdsCategoryImportService selected = categories.get(section);
        if (selected == null) throw new CdsImportException("CATEGORY_MAPPING_NOT_CONFIGURED");
        return selected;
    }

    private void temporalNotice(
            String run, long epoch, CdsImportUnitDto unit, String path, CdsTemporalDto date) {
        if (date.appliedShiftDays() != 0)
            notice(run, epoch, unit, "WARNING", "CDS_SYNTHETIC_DATE_SHIFT_APPLIED", path);
        if (date.precision() == CdsTemporalDto.Precision.YEAR
                || date.precision() == CdsTemporalDto.Precision.MONTH) {
            notice(run, epoch, unit, "WARNING", "CDS_PARTIAL_DATE_RETAINED", path);
        } else if (date.precision() == CdsTemporalDto.Precision.UNKNOWN) {
            notice(run, epoch, unit, "INFO", "CDS_DATE_NOT_SUPPLIED", path);
        }
    }

    private void notice(
            String run,
            long epoch,
            CdsImportUnitDto unit,
            String severity,
            String code,
            String path) {
        ledger.diagnostic(
                run,
                epoch,
                unit.patientKey(),
                unit.unitKey(),
                severity,
                code,
                "NORMALIZATION",
                path,
                "Source meaning retained; inspect the category-local original, normalized value and"
                        + " precision",
                null,
                false);
    }
}
