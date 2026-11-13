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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import java.io.IOException;
import java.util.List;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Set;

/**
 * One demographic assembly order shared by future CLI and browser execution: fixed
 * scalars, phones, notes, enrolment, primary physician, personal/professional contacts,
 * pharmacy and ancillary base-row decisions. Every dependency belongs to the same
 * authenticated capture; remaining retained-source and associated-row hand-offs prevent
 * a demographic-complete result. This component does not qualify an export profile.
 */
public final class CdsExportDemographicAssembler {
    /** Immutable bounded row carriers; construction supplies no authentication or authority. */
    public record NativeRows(
            CdsExportCapturedTextRowDto current,
            CdsExportNativeRowReceiptDto witness,
            List<CdsExportCapturedTextRowDto> properties,
            List<CdsExportNativeRowReceiptDto> propertyWitnesses,
            CdsExportCapturedTextRowDto custom,
            CdsExportNativeRowReceiptDto customWitness,
            List<CdsExportCapturedTextRowDto> archives,
            List<CdsExportNativeRowReceiptDto> archiveWitnesses,
            CdsExportPublicProviderLookupsDto lookups,
            CdsExportRetainedProfessionalContactReconciler.NativeRows contacts,
            CdsExportRetainedPharmacyReconciler.NativeRows pharmacies) {
        public NativeRows {
            Objects.requireNonNull(current);
            Objects.requireNonNull(witness);
            Objects.requireNonNull(lookups);
            Objects.requireNonNull(contacts);
            Objects.requireNonNull(pharmacies);
            if (properties.size() > 8192
                    || archives.size() > 8192
                    || properties.size() != propertyWitnesses.size()
                    || archives.size() != archiveWitnesses.size()) {
                throw new IllegalArgumentException(
                        "CDS_EXPORT_DEMOGRAPHIC_ASSEMBLY_ROW_SCOPE_INVALID");
            }
            properties = List.copyOf(properties);
            propertyWitnesses = List.copyOf(propertyWitnesses);
            archives = List.copyOf(archives);
            archiveWitnesses = List.copyOf(archiveWitnesses);
            if ((custom == null) != (customWitness == null))
                throw new IllegalArgumentException(
                        "CDS_EXPORT_DEMOGRAPHIC_ASSEMBLY_NOTE_SCOPE_INVALID");
        }

        @Override
        public String toString() {
            return "CdsExportDemographicAssembler.NativeRows[protected]";
        }
    }

    /**
     * Composes existing owning reconcilers before the final base-row closure. The caller
     * owns bounded, authenticated row collections and the active retained-value scope.
     * Any unresolved component withholds all proposed XML changes; no partial result is
     * silently emitted. Unselected alerts leave their note carriers explicitly deferred.
     */
    public CdsExportDemographicCareAssembler.Result assemble(
            int patient,
            Set<CdsCategory> selected,
            String address,
            CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked,
            List<CdsExportRetainedFieldDto> allFields,
            NativeRows rows,
            int maximumRows,
            long maximumBytes,
            CdsExportRetainedSectionWriter.Values values,
            Runnable checkpoint)
            throws IOException {
        Objects.requireNonNull(rows);
        Objects.requireNonNull(values);
        Objects.requireNonNull(checkpoint);
        if (maximumRows < 1
                || maximumRows > 8192
                || maximumBytes < 1
                || maximumBytes > (64L << 20)
                || selected == null
                || !selected.contains(CdsCategory.DEMOGRAPHICS)) {
            throw new IllegalArgumentException("CDS_EXPORT_DEMOGRAPHIC_ASSEMBLY_SCOPE_INVALID");
        }
        values.requireActive();
        checkpoint.run();
        var core =
                new CdsExportDemographicRetainedCoreReconciler()
                        .reconcileWithPrimaryPhysician(
                                patient,
                                selected,
                                address,
                                index,
                                checked,
                                allFields,
                                rows.current(),
                                rows.witness(),
                                rows.properties(),
                                rows.propertyWitnesses(),
                                rows.custom(),
                                rows.customWitness(),
                                rows.archives(),
                                rows.archiveWitnesses(),
                                rows.lookups(),
                                maximumRows,
                                maximumBytes,
                                values,
                                checkpoint);
        core =
                CdsExportDemographicRetainedCoreReconciler.withArchivedPrimaryPhysicians(
                        core,
                        CdsExportDemographicAncillaryReconciler.assessArchivedPrimaryPhysicians(
                                patient, rows.archives(), rows.lookups().providers(), checkpoint));
        var care =
                new CdsExportDemographicCareAssembler()
                        .assemble(
                                core,
                                patient,
                                index,
                                checked,
                                allFields,
                                rows.current(),
                                rows.witness(),
                                rows.contacts(),
                                rows.pharmacies(),
                                maximumRows,
                                maximumBytes,
                                values,
                                checkpoint);
        var completeBaseRow =
                new CdsExportDemographicAncillaryReconciler()
                        .reconcile(
                                care.demographic(),
                                patient,
                                rows.current(),
                                rows.witness(),
                                checkpoint);
        boolean newCategory = selected.contains(CdsCategory.NEW_CATEGORY);
        var careCarriers =
                CdsExportDemographicRetainedCoreReconciler.withCareTeamProviders(
                        accountCareCarriers(completeBaseRow, rows.custom(), checkpoint),
                        CdsExportDemographicSupportingMapper.careTeamProviders(
                                rows.custom(), rows.lookups().providers(), newCategory));
        var accounted =
                withSelection(
                        withNativeCarriers(
                                accountAlertCarrier(
                                        careCarriers, rows.custom(), selected, checkpoint),
                                patient,
                                rows.current()),
                        newCategory);
        values.requireActive();
        checkpoint.run();
        return new CdsExportDemographicCareAssembler.Result(accounted, care.professionalNameUses());
    }

    /**
     * A current base-row value whose exact form the native demographic record carries (an
     * e-mail outside the CDS pattern, a SIN typed with separators, untrimmed text...) keeps that
     * destination after a retained reconciler decided its element, so the receipt states where
     * the exact value went. The record is built from the same native decision.
     */
    static CdsExportDemographicRetainedCoreReconciler.Result withNativeCarriers(
            CdsExportDemographicRetainedCoreReconciler.Result prior,
            int patient,
            CdsExportCapturedTextRowDto current) {
        var raw = new LinkedHashMap<>(current.fields());
        raw.remove("demographic_no");
        var nativeBase =
                new CdsExportDemographicMapper()
                        .map(
                                new io.github.carlos_emr.carlos.commn.dto
                                        .CdsExportDemographicRowDto(patient, raw));
        var fields = new LinkedHashMap<>(prior.fields());
        for (var entry : nativeBase.fields().entrySet()) {
            var key =
                    new CdsExportNativeFieldKeyDto(
                            current.table(), current.recordKey(), entry.getKey());
            var decision = fields.get(key);
            var nativeDecision = entry.getValue();
            if (decision == null
                    || decision.outcome() != CdsExportFieldAssessmentDto.Outcome.MAPPED
                    || CdsExportDemographicSupportingMapper.carries(decision)
                    || !java.util.Arrays.asList(nativeDecision.destination().split(",", -1))
                            .contains(CdsExportDemographicSupportingMapper.DESTINATION)
                    || nativeDecision.outcome() != CdsExportDemographicMappingDto.Outcome.MAPPED)
                continue;
            fields.put(
                    key,
                    new CdsExportFieldAssessmentDto(
                            decision.presence(),
                            decision.outcome(),
                            decision.destination().isEmpty()
                                    ? CdsExportDemographicSupportingMapper.DESTINATION
                                    : decision.destination()
                                            + ","
                                            + CdsExportDemographicSupportingMapper.DESTINATION,
                            decision.rule()));
        }
        return new CdsExportDemographicRetainedCoreReconciler.Result(
                prior.sourceManifestSha256(),
                prior.unitAddress(),
                prior.decidedSourceFields(),
                prior.deferredSourceFields(),
                fields,
                prior.deferredNativeFields(),
                prior.blockers(),
                prior.proposedChanges(),
                prior.providerUses());
    }

    /**
     * Without NewCategory every value decided for the native demographic record is accounted for
     * as not selected (the ReadMe says so). Provider rows already carry the selection.
     */
    static CdsExportDemographicRetainedCoreReconciler.Result withSelection(
            CdsExportDemographicRetainedCoreReconciler.Result prior, boolean newCategory) {
        if (newCategory) return prior;
        var fields = new LinkedHashMap<>(prior.fields());
        fields.replaceAll(
                (key, value) ->
                        key.table().equals("provider")
                                ? value
                                : CdsExportDemographicSupportingMapper.withSelection(value, false));
        return new CdsExportDemographicRetainedCoreReconciler.Result(
                prior.sourceManifestSha256(),
                prior.unitAddress(),
                prior.decidedSourceFields(),
                prior.deferredSourceFields(),
                fields,
                prior.deferredNativeFields(),
                prior.blockers(),
                prior.proposedChanges(),
                prior.providerUses());
    }

    /**
     * Close the selected legacy alert carrier only after the note reconciler authenticated its
     * exact row. A populated alert is exported as AlertsAndSpecialNeeds/AlertDescription.
     */
    static CdsExportDemographicRetainedCoreReconciler.Result accountAlertCarrier(
            CdsExportDemographicRetainedCoreReconciler.Result prior,
            CdsExportCapturedTextRowDto custom,
            Set<CdsCategory> selected,
            Runnable checkpoint)
            throws IOException {
        if (custom == null || !selected.contains(CdsCategory.ALERTS_AND_SPECIAL_NEEDS))
            return prior;
        checkpoint.run();
        var key = new CdsExportNativeFieldKeyDto("demographiccust", custom.recordKey(), "cust3");
        var fields = new LinkedHashMap<>(prior.fields());
        var deferred = new HashSet<>(prior.deferredNativeFields());
        if (!custom.table().equals("demographiccust")
                || !custom.fields().containsKey("cust3")
                || fields.containsKey(key)
                || !deferred.remove(key)) {
            throw new IOException("CDS_EXPORT_DEMOGRAPHIC_ASSEMBLY_ALERT_SCOPE_INVALID");
        }
        // The alert is exported natively as AlertsAndSpecialNeeds/AlertDescription; it is never
        // an imported Demographics element, so no retained element changes.
        var decision = CdsExportNativeDemographicAssembler.alert(custom.fields().get("cust3"));
        fields.put(key, decision);
        boolean ready =
                prior.coreRepresentable()
                        && decision.outcome() != CdsExportFieldAssessmentDto.Outcome.REQUIRES_REVIEW;
        return new CdsExportDemographicRetainedCoreReconciler.Result(
                prior.sourceManifestSha256(),
                prior.unitAddress(),
                prior.decidedSourceFields(),
                prior.deferredSourceFields(),
                fields,
                deferred,
                prior.blockers(),
                ready ? prior.coreChanges() : List.of(),
                prior.providerUses());
    }

    /**
     * The note reconciler already verified this exact row and witness; close only its three
     * explicit care-team hand-offs. A populated nurse, resident or midwife is carried in the
     * native demographic NewCategory record, never held.
     */
    static CdsExportDemographicRetainedCoreReconciler.Result accountCareCarriers(
            CdsExportDemographicRetainedCoreReconciler.Result prior,
            CdsExportCapturedTextRowDto custom,
            Runnable checkpoint)
            throws IOException {
        if (custom == null) return prior;
        if (!custom.table().equals("demographiccust"))
            throw new IOException("CDS_EXPORT_DEMOGRAPHIC_ASSEMBLY_CARE_SCOPE_INVALID");
        var fields = new LinkedHashMap<>(prior.fields());
        var deferred = new HashSet<>(prior.deferredNativeFields());
        for (String column : CdsExportDemographicSupportingMapper.CARE_TEAM.keySet()) {
            checkpoint.run();
            var key = new CdsExportNativeFieldKeyDto(custom.table(), custom.recordKey(), column);
            if (!custom.fields().containsKey(column)
                    || fields.containsKey(key)
                    || !deferred.remove(key)) {
                throw new IOException("CDS_EXPORT_DEMOGRAPHIC_ASSEMBLY_CARE_SCOPE_INVALID");
            }
            String value = custom.fields().get(column);
            var presence = CdsExportDemographicSupportingMapper.presence(value);
            fields.put(
                    key,
                    presence == CdsExportFieldAssessmentDto.Presence.VALUE
                            ? CdsExportDemographicSupportingMapper.carried(value)
                            : new CdsExportFieldAssessmentDto(
                                    presence,
                                    CdsExportFieldAssessmentDto.Outcome.ABSENT,
                                    "",
                                    "ABSENT_DEMOGRAPHIC_CARE_TEAM_CARRIER"));
        }
        boolean ready =
                prior.coreRepresentable()
                        && fields.values().stream()
                                .noneMatch(
                                        field ->
                                                field.outcome()
                                                        == CdsExportFieldAssessmentDto.Outcome
                                                                .REQUIRES_REVIEW);
        return new CdsExportDemographicRetainedCoreReconciler.Result(
                prior.sourceManifestSha256(),
                prior.unitAddress(),
                prior.decidedSourceFields(),
                prior.deferredSourceFields(),
                fields,
                deferred,
                prior.blockers(),
                ready ? prior.coreChanges() : List.of(),
                prior.providerUses());
    }
}
