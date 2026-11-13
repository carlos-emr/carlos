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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import java.io.IOException;
import java.util.*;

/**
 * Coordinates the fixed demographic core without claiming associated-row or whole-chart
 * completeness. Remaining native columns and source IDs are mandatory hand-offs to the
 * enclosing assembly; an unchanged source subtree never qualifies those native facts.
 */
public final class CdsExportDemographicRetainedCoreReconciler {
    public record Result(
            String sourceManifestSha256,
            String unitAddress,
            Set<String> decidedSourceFields,
            Set<String> deferredSourceFields,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            Set<CdsExportNativeFieldKeyDto> deferredNativeFields,
            Set<String> blockers,
            List<CdsExportRetainedSectionWriter.Change> proposedChanges,
            Map<CdsExportNativeFieldKeyDto, List<CdsExportFieldAssessmentDto>> providerUses) {
        public Result(
                String sourceManifestSha256,
                String unitAddress,
                Set<String> decidedSourceFields,
                Set<String> deferredSourceFields,
                Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
                Set<CdsExportNativeFieldKeyDto> deferredNativeFields,
                Set<String> blockers,
                List<CdsExportRetainedSectionWriter.Change> proposedChanges) {
            this(
                    sourceManifestSha256,
                    unitAddress,
                    decidedSourceFields,
                    deferredSourceFields,
                    fields,
                    deferredNativeFields,
                    blockers,
                    proposedChanges,
                    initialProviderUses(fields));
        }

        public Result {
            decidedSourceFields = Set.copyOf(decidedSourceFields);
            deferredSourceFields = Set.copyOf(deferredSourceFields);
            fields = Map.copyOf(fields);
            deferredNativeFields = Set.copyOf(deferredNativeFields);
            blockers = Set.copyOf(blockers);
            proposedChanges = List.copyOf(proposedChanges);
            var uses =
                    new LinkedHashMap<
                            CdsExportNativeFieldKeyDto, List<CdsExportFieldAssessmentDto>>();
            for (var entry : providerUses.entrySet()) {
                if (!publicProviderField(entry.getKey())
                        || !fields.containsKey(entry.getKey())
                        || entry.getValue().isEmpty()
                        || entry.getValue().size() > 16)
                    throw new IllegalArgumentException(
                            "CDS_EXPORT_DEMOGRAPHIC_PROVIDER_USES_INVALID");
                var items = List.copyOf(entry.getValue());
                if (!summarizeProviderUses(items).equals(fields.get(entry.getKey())))
                    throw new IllegalArgumentException(
                            "CDS_EXPORT_DEMOGRAPHIC_PROVIDER_USES_INVALID");
                uses.put(entry.getKey(), items);
            }
            for (var key : fields.keySet())
                if (publicProviderField(key) && !uses.containsKey(key))
                    throw new IllegalArgumentException(
                            "CDS_EXPORT_DEMOGRAPHIC_PROVIDER_USES_INVALID");
            providerUses = Map.copyOf(uses);
        }

        public boolean coreRepresentable() {
            return blockers.isEmpty()
                    && fields.values().stream()
                            .noneMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW);
        }

        public boolean demographicComplete() {
            return coreRepresentable()
                    && deferredNativeFields.isEmpty()
                    && deferredSourceFields.isEmpty();
        }

        public List<CdsExportRetainedSectionWriter.Change> coreChanges() throws IOException {
            if (!coreRepresentable())
                throw new IOException("CDS_EXPORT_DEMOGRAPHIC_CORE_REQUIRES_REVIEW");
            return proposedChanges;
        }

        @Override
        public String toString() {
            return "CdsExportDemographicRetainedCoreReconciler.Result[protected]";
        }
    }

    public Result reconcile(
            int patient,
            String address,
            CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked,
            List<CdsExportRetainedFieldDto> allFields,
            CdsExportCapturedTextRowDto current,
            CdsExportNativeRowReceiptDto witness,
            CdsExportRetainedSectionWriter.Values values,
            Runnable checkpoint)
            throws IOException {
        Objects.requireNonNull(values);
        Objects.requireNonNull(checkpoint);
        values.requireActive();
        checkpoint.run();
        var names =
                new CdsExportDemographicNamesReconciler()
                        .reconcile(
                                patient,
                                address,
                                index,
                                checked,
                                allFields,
                                current,
                                witness,
                                values,
                                checkpoint);
        var birth =
                new CdsExportDemographicBirthDateReconciler()
                        .reconcile(
                                patient,
                                address,
                                index,
                                checked,
                                allFields,
                                current,
                                witness,
                                values,
                                checkpoint);
        var scalars =
                new CdsExportDemographicScalarReconciler()
                        .reconcile(
                                patient,
                                address,
                                index,
                                checked,
                                allFields,
                                current,
                                witness,
                                values,
                                checkpoint);
        var statusDate =
                new CdsExportDemographicOptionalDateReconciler()
                        .reconcile(
                                patient,
                                address,
                                index,
                                checked,
                                allFields,
                                current,
                                witness,
                                values,
                                checkpoint,
                                Set.of(
                                        CdsExportDemographicOptionalDateReconciler.DateField
                                                .STATUS));
        var health =
                new CdsExportDemographicHealthCardReconciler()
                        .reconcile(
                                patient,
                                address,
                                index,
                                checked,
                                allFields,
                                current,
                                witness,
                                values,
                                checkpoint);
        var addresses =
                new CdsExportDemographicAddressReconciler()
                        .reconcile(
                                patient,
                                address,
                                index,
                                checked,
                                allFields,
                                current,
                                witness,
                                values,
                                checkpoint);
        var fields = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        for (var component :
                List.of(
                        names.fields(),
                        birth.fields(),
                        scalars.fields(),
                        statusDate.fields(),
                        health.fields(),
                        addresses.fields()))
            for (var entry : component.entrySet())
                if (fields.putIfAbsent(entry.getKey(), entry.getValue()) != null)
                    throw new IOException("CDS_EXPORT_DEMOGRAPHIC_CORE_EVIDENCE_OVERLAP");
        var decided = new HashSet<String>();
        for (var source :
                List.of(
                        names.sourceFields(),
                        Set.of(birth.sourceField().id()),
                        scalars.sourceFields(),
                        statusDate.sourceFields(),
                        health.sourceFields(),
                        addresses.sourceFields()))
            if (!Collections.disjoint(decided, source))
                throw new IOException("CDS_EXPORT_DEMOGRAPHIC_CORE_EVIDENCE_OVERLAP");
            else decided.addAll(source);
        var parent =
                new CdsExportRetainedOccurrenceFields()
                        .fieldsFor(
                                patient,
                                CdsCategory.DEMOGRAPHICS,
                                address,
                                index,
                                checked,
                                allFields,
                                checkpoint);
        var sourceIds = new HashSet<String>();
        parent.forEach(field -> sourceIds.add(field.id()));
        if (!sourceIds.containsAll(decided))
            throw new IOException("CDS_EXPORT_DEMOGRAPHIC_CORE_EVIDENCE_SCOPE_INVALID");
        sourceIds.removeAll(decided);
        var deferred = new HashSet<CdsExportNativeFieldKeyDto>();
        for (String column : current.fields().keySet())
            if (!column.equals("demographic_no")) {
                var key =
                        new CdsExportNativeFieldKeyDto("demographic", current.recordKey(), column);
                if (!fields.containsKey(key)) deferred.add(key);
            }
        var changes = new ArrayList<CdsExportRetainedSectionWriter.Change>();
        boolean ready =
                fields.values().stream()
                        .noneMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW);
        if (ready) {
            changes.addAll(names.changes());
            changes.addAll(birth.changes());
            changes.addAll(scalars.changes());
            changes.addAll(statusDate.changes());
            changes.addAll(health.changes());
            changes.addAll(addresses.changes());
        }
        values.requireActive();
        checkpoint.run();
        return new Result(
                index.sourceManifestSha256(),
                address,
                decided,
                sourceIds,
                fields,
                deferred,
                Set.of(),
                changes);
    }

    /** Coordinated phones/note ownership; all remaining fields and source IDs still require the enclosing assembly. */
    public Result reconcileWithAdjuncts(
            int patient,
            Set<CdsCategory> selected,
            String address,
            CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked,
            List<CdsExportRetainedFieldDto> allFields,
            CdsExportCapturedTextRowDto current,
            CdsExportNativeRowReceiptDto witness,
            List<CdsExportCapturedTextRowDto> properties,
            List<CdsExportNativeRowReceiptDto> propertyWitnesses,
            CdsExportCapturedTextRowDto custom,
            CdsExportNativeRowReceiptDto customWitness,
            int maximumRows,
            long maximumBytes,
            CdsExportRetainedSectionWriter.Values values,
            Runnable checkpoint)
            throws IOException {
        var core =
                reconcile(
                        patient,
                        address,
                        index,
                        checked,
                        allFields,
                        current,
                        witness,
                        values,
                        checkpoint);
        var phones =
                new CdsExportDemographicRetainedPhonesReconciler()
                        .reconcile(
                                patient,
                                address,
                                index,
                                checked,
                                allFields,
                                current,
                                witness,
                                properties,
                                propertyWitnesses,
                                maximumRows,
                                maximumBytes,
                                values,
                                checkpoint);
        var note =
                new CdsExportDemographicRetainedNoteReconciler()
                        .reconcile(
                                patient,
                                selected,
                                address,
                                index,
                                checked,
                                allFields,
                                current,
                                witness,
                                custom,
                                customWitness,
                                values,
                                checkpoint);
        var fields = new LinkedHashMap<>(core.fields());
        for (var component : List.of(phones.fields(), note.fields()))
            for (var entry : component.entrySet())
                if (fields.putIfAbsent(entry.getKey(), entry.getValue()) != null)
                    throw new IOException("CDS_EXPORT_DEMOGRAPHIC_CORE_EVIDENCE_OVERLAP");
        var decided = new HashSet<>(core.decidedSourceFields());
        var sourceDeferred = new HashSet<>(core.deferredSourceFields());
        for (var component : List.of(phones.sourceFields(), note.sourceFields())) {
            if (!Collections.disjoint(decided, component) || !sourceDeferred.containsAll(component))
                throw new IOException("CDS_EXPORT_DEMOGRAPHIC_CORE_EVIDENCE_OVERLAP");
            decided.addAll(component);
            sourceDeferred.removeAll(component);
        }
        var nativeDeferred = new HashSet<>(core.deferredNativeFields());
        nativeDeferred.removeAll(fields.keySet());
        nativeDeferred.addAll(note.deferredNativeFields());
        for (var row : properties)
            if (phones.deferredPropertyRows().contains(row.recordKey()))
                for (String column : row.fields().keySet())
                    nativeDeferred.add(
                            new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), column));
        var blockers = new HashSet<>(core.blockers());
        blockers.addAll(note.blockers());
        var changes = new ArrayList<CdsExportRetainedSectionWriter.Change>();
        if (core.coreRepresentable() && phones.phonesRepresentable() && note.noteRepresentable()) {
            changes.addAll(core.coreChanges());
            changes.addAll(phones.changes());
            changes.addAll(note.changes());
        }
        values.requireActive();
        checkpoint.run();
        return new Result(
                index.sourceManifestSha256(),
                address,
                decided,
                sourceDeferred,
                fields,
                nativeDeferred,
                blockers,
                changes);
    }

    /** One coordinated result for core, phone, note and retained/native enrolment history. */
    public Result reconcileWithHistory(
            int patient,
            Set<CdsCategory> selected,
            String address,
            CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked,
            List<CdsExportRetainedFieldDto> allFields,
            CdsExportCapturedTextRowDto current,
            CdsExportNativeRowReceiptDto witness,
            List<CdsExportCapturedTextRowDto> properties,
            List<CdsExportNativeRowReceiptDto> propertyWitnesses,
            CdsExportCapturedTextRowDto custom,
            CdsExportNativeRowReceiptDto customWitness,
            List<CdsExportCapturedTextRowDto> archives,
            List<CdsExportNativeRowReceiptDto> archiveWitnesses,
            CdsExportPublicProviderLookupsDto lookups,
            int maximumRows,
            long maximumBytes,
            CdsExportRetainedSectionWriter.Values values,
            Runnable checkpoint)
            throws IOException {
        var core =
                reconcileWithAdjuncts(
                        patient,
                        selected,
                        address,
                        index,
                        checked,
                        allFields,
                        current,
                        witness,
                        properties,
                        propertyWitnesses,
                        custom,
                        customWitness,
                        maximumRows,
                        maximumBytes,
                        values,
                        checkpoint);
        var history =
                new CdsExportRetainedEnrolmentReconciler()
                        .reconcile(
                                patient,
                                address,
                                index,
                                checked,
                                allFields,
                                current,
                                witness,
                                archives,
                                archiveWitnesses,
                                lookups,
                                maximumRows,
                                maximumBytes,
                                values,
                                checkpoint);
        var combined = withHistory(core, history);
        values.requireActive();
        checkpoint.run();
        return combined;
    }

    static Result withHistory(Result core, CdsExportRetainedEnrolmentReconciler.Result history)
            throws IOException {
        if (!core.sourceManifestSha256().equals(history.sourceManifestSha256())
                || !core.unitAddress().equals(history.unitAddress())
                || !Collections.disjoint(core.decidedSourceFields(), history.sourceFields())
                || !core.deferredSourceFields().containsAll(history.sourceFields())
                || !Collections.disjoint(core.fields().keySet(), history.fields().keySet()))
            throw new IOException("CDS_EXPORT_DEMOGRAPHIC_HISTORY_EVIDENCE_OVERLAP");
        var fields = new LinkedHashMap<>(core.fields());
        fields.putAll(history.fields());
        var decided = new HashSet<>(core.decidedSourceFields());
        decided.addAll(history.sourceFields());
        var sourceDeferred = new HashSet<>(core.deferredSourceFields());
        sourceDeferred.removeAll(history.sourceFields());
        var nativeDeferred = new HashSet<>(core.deferredNativeFields());
        nativeDeferred.removeAll(history.fields().keySet());
        nativeDeferred.addAll(history.deferredNativeFields());
        var blockers = new HashSet<>(core.blockers());
        blockers.addAll(history.blockers());
        var changes = new ArrayList<CdsExportRetainedSectionWriter.Change>();
        if (core.coreRepresentable() && history.representable()) {
            changes.addAll(core.coreChanges());
            changes.addAll(history.changes());
        }
        var uses = new LinkedHashMap<>(core.providerUses());
        uses.putAll(initialProviderUses(history.fields()));
        return new Result(
                core.sourceManifestSha256(),
                core.unitAddress(),
                decided,
                sourceDeferred,
                fields,
                nativeDeferred,
                blockers,
                changes,
                uses);
    }

    /** Coordinates primary and enrolment relationships while retaining each use of a shared public provider fact. */
    public Result reconcileWithPrimaryPhysician(
            int patient,
            Set<CdsCategory> selected,
            String address,
            CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked,
            List<CdsExportRetainedFieldDto> allFields,
            CdsExportCapturedTextRowDto current,
            CdsExportNativeRowReceiptDto witness,
            List<CdsExportCapturedTextRowDto> properties,
            List<CdsExportNativeRowReceiptDto> propertyWitnesses,
            CdsExportCapturedTextRowDto custom,
            CdsExportNativeRowReceiptDto customWitness,
            List<CdsExportCapturedTextRowDto> archives,
            List<CdsExportNativeRowReceiptDto> archiveWitnesses,
            CdsExportPublicProviderLookupsDto lookups,
            int maximumRows,
            long maximumBytes,
            CdsExportRetainedSectionWriter.Values values,
            Runnable checkpoint)
            throws IOException {
        var core =
                reconcileWithHistory(
                        patient,
                        selected,
                        address,
                        index,
                        checked,
                        allFields,
                        current,
                        witness,
                        properties,
                        propertyWitnesses,
                        custom,
                        customWitness,
                        archives,
                        archiveWitnesses,
                        lookups,
                        maximumRows,
                        maximumBytes,
                        values,
                        checkpoint);
        var primary =
                new CdsExportDemographicPrimaryPhysicianReconciler()
                        .reconcile(
                                patient,
                                address,
                                index,
                                checked,
                                allFields,
                                current,
                                witness,
                                lookups,
                                values,
                                checkpoint);
        var combined = withPrimaryPhysician(core, primary);
        values.requireActive();
        checkpoint.run();
        return combined;
    }

    static Result withPrimaryPhysician(
            Result core, CdsExportDemographicPrimaryPhysicianReconciler.Result primary)
            throws IOException {
        if (!core.sourceManifestSha256().equals(primary.sourceManifestSha256())
                || !core.unitAddress().equals(primary.unitAddress())
                || !Collections.disjoint(core.decidedSourceFields(), primary.sourceFields())
                || !core.deferredSourceFields().containsAll(primary.sourceFields()))
            throw new IOException("CDS_EXPORT_DEMOGRAPHIC_PRIMARY_EVIDENCE_OVERLAP");
        var fields = new LinkedHashMap<>(core.fields());
        var uses =
                new LinkedHashMap<CdsExportNativeFieldKeyDto, List<CdsExportFieldAssessmentDto>>(
                        core.providerUses());
        for (var entry : primary.fields().entrySet()) {
            var old = fields.get(entry.getKey());
            if (old == null) {
                fields.put(entry.getKey(), entry.getValue());
                if (publicProviderField(entry.getKey()))
                    uses.put(entry.getKey(), List.of(entry.getValue()));
            } else {
                if (!publicProviderField(entry.getKey())
                        || old.presence() != entry.getValue().presence())
                    throw new IOException("CDS_EXPORT_DEMOGRAPHIC_PRIMARY_EVIDENCE_OVERLAP");
                var items = new ArrayList<>(uses.get(entry.getKey()));
                if (!items.contains(entry.getValue())) items.add(entry.getValue());
                if (items.size() > 16)
                    throw new IOException("CDS_EXPORT_DEMOGRAPHIC_PROVIDER_USES_LIMIT");
                uses.put(entry.getKey(), List.copyOf(items));
                fields.put(entry.getKey(), summarizeProviderUses(items));
            }
        }
        var decided = new HashSet<>(core.decidedSourceFields());
        decided.addAll(primary.sourceFields());
        var sourceDeferred = new HashSet<>(core.deferredSourceFields());
        sourceDeferred.removeAll(primary.sourceFields());
        var nativeDeferred = new HashSet<>(core.deferredNativeFields());
        nativeDeferred.removeAll(primary.fields().keySet());
        var blockers = new HashSet<>(core.blockers());
        blockers.addAll(primary.blockers());
        var changes = new ArrayList<CdsExportRetainedSectionWriter.Change>();
        if (core.coreRepresentable() && primary.representable()) {
            changes.addAll(core.coreChanges());
            changes.addAll(primary.changes());
        }
        return new Result(
                core.sourceManifestSha256(),
                core.unitAddress(),
                decided,
                sourceDeferred,
                fields,
                nativeDeferred,
                blockers,
                changes,
                uses);
    }

    /**
     * Adds the audit-arrangement use of each former most-responsible physician named by an
     * archive snapshot ({@link CdsExportDemographicAncillaryReconciler#assessArchivedPrimaryPhysicians})
     * beside any primary or enrolled use of the same captured provider row. An OPERATIONAL use
     * never hides another use's review and adds no XML change.
     */
    static Result withArchivedPrimaryPhysicians(
            Result core, Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> archived)
            throws IOException {
        return withProviderUses(
                core,
                archived,
                Set.of(Outcome.OPERATIONAL),
                "CDS_EXPORT_DEMOGRAPHIC_ARCHIVED_PHYSICIAN_SCOPE_INVALID");
    }

    /**
     * Adds the use of each public provider row that the patient's nurse, resident or midwife
     * names ({@link CdsExportDemographicSupportingMapper#careTeamProviders}): carried in the native
     * demographic NewCategory record, or accounted for as not selected. A carried use never hides
     * another use's review and adds no XML change to the retained Demographics section.
     */
    static Result withCareTeamProviders(
            Result core, Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> careTeam)
            throws IOException {
        return withProviderUses(
                core,
                careTeam,
                Set.of(Outcome.MAPPED, Outcome.OPERATIONAL),
                "CDS_EXPORT_DEMOGRAPHIC_CARE_TEAM_PROVIDER_SCOPE_INVALID");
    }

    private static Result withProviderUses(
            Result core,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> added,
            Set<Outcome> allowed,
            String invalid)
            throws IOException {
        var fields = new LinkedHashMap<>(core.fields());
        var uses =
                new LinkedHashMap<CdsExportNativeFieldKeyDto, List<CdsExportFieldAssessmentDto>>(
                        core.providerUses());
        for (var entry : added.entrySet()) {
            if (!publicProviderField(entry.getKey())
                    || !allowed.contains(entry.getValue().outcome()))
                throw new IOException(invalid);
            var old = fields.get(entry.getKey());
            if (old == null) {
                fields.put(entry.getKey(), entry.getValue());
                uses.put(entry.getKey(), List.of(entry.getValue()));
                continue;
            }
            if (old.presence() != entry.getValue().presence()) throw new IOException(invalid);
            var items = new ArrayList<>(uses.get(entry.getKey()));
            if (!items.contains(entry.getValue())) items.add(entry.getValue());
            if (items.size() > 16)
                throw new IOException("CDS_EXPORT_DEMOGRAPHIC_PROVIDER_USES_LIMIT");
            uses.put(entry.getKey(), List.copyOf(items));
            fields.put(entry.getKey(), summarizeProviderUses(items));
        }
        var nativeDeferred = new HashSet<>(core.deferredNativeFields());
        nativeDeferred.removeAll(added.keySet());
        return new Result(
                core.sourceManifestSha256(),
                core.unitAddress(),
                core.decidedSourceFields(),
                core.deferredSourceFields(),
                fields,
                nativeDeferred,
                core.blockers(),
                core.proposedChanges(),
                uses);
    }

    private static boolean publicProviderField(CdsExportNativeFieldKeyDto key) {
        return key.table().equals("provider")
                && io.github.carlos_emr.carlos.commn.dto.CdsExportProviderRowDto.COLUMNS.contains(
                        key.column());
    }

    private static Map<CdsExportNativeFieldKeyDto, List<CdsExportFieldAssessmentDto>>
            initialProviderUses(
                    Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields) {
        var result =
                new LinkedHashMap<CdsExportNativeFieldKeyDto, List<CdsExportFieldAssessmentDto>>();
        fields.forEach(
                (key, value) -> {
                    if (publicProviderField(key)) result.put(key, List.of(value));
                });
        return result;
    }

    private static CdsExportFieldAssessmentDto summarizeProviderUses(
            List<CdsExportFieldAssessmentDto> uses) {
        var presence = uses.getFirst().presence();
        if (uses.stream().anyMatch(use -> use.presence() != presence))
            throw new IllegalArgumentException("CDS_EXPORT_DEMOGRAPHIC_PROVIDER_USES_INVALID");
        if (uses.size() == 1) return uses.getFirst();
        Outcome outcome =
                uses.stream().anyMatch(use -> use.outcome() == Outcome.REQUIRES_REVIEW)
                        ? Outcome.REQUIRES_REVIEW
                        : uses.stream().anyMatch(use -> use.outcome() == Outcome.MAPPED)
                                ? Outcome.MAPPED
                                : uses.stream()
                                                .anyMatch(
                                                        use -> use.outcome() == Outcome.OPERATIONAL)
                                        ? Outcome.OPERATIONAL
                                        : Outcome.ABSENT;
        return new CdsExportFieldAssessmentDto(
                presence, outcome, "PublicProvider", "EXPLICIT_SHARED_PUBLIC_PROVIDER_USES");
    }
}
