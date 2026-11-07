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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Coordinates personal contacts, external physicians and pharmacy with one retained demographic core. */
public final class CdsExportDemographicCareAssembler {
    public record Result(CdsExportDemographicRetainedCoreReconciler.Result demographic,
            Map<CdsExportNativeFieldKeyDto, Set<CdsExportProfessionalContactMapper.Role>> professionalNameUses) {
        public Result {
            Objects.requireNonNull(demographic); var uses = new LinkedHashMap<CdsExportNativeFieldKeyDto, Set<CdsExportProfessionalContactMapper.Role>>();
            professionalNameUses.forEach((key, roles) -> {
                if (!demographic.fields().containsKey(key) || roles.isEmpty()) throw new IllegalArgumentException("CDS_EXPORT_DEMOGRAPHIC_CARE_NAME_USES_INVALID");
                uses.put(key, Set.copyOf(roles));
            }); professionalNameUses = Map.copyOf(uses);
        }
        @Override public String toString() { return "CdsExportDemographicCareAssembler.Result[protected]"; }
    }

    /**
     * The complete captured contact universe enters this boundary. Routing is accepted only
     * when both sibling assemblers account for its exact field identities. Shared contacts
     * across personal/professional roles require explicit review rather than a filtered omission.
     */
    public Result assemble(CdsExportDemographicRetainedCoreReconciler.Result core, int patient,
            CdsExportRetainedIndexDto index, CdsExportRetainedReceiptVerificationDto checked, List<CdsExportRetainedFieldDto> allFields,
            CdsExportCapturedTextRowDto demographic, CdsExportNativeRowReceiptDto demographicWitness,
            CdsExportRetainedProfessionalContactReconciler.NativeRows contacts, CdsExportRetainedPharmacyReconciler.NativeRows pharmacies,
            int maximumRows, long maximumBytes, CdsExportRetainedSectionWriter.Values values, Runnable checkpoint) throws IOException {
        Objects.requireNonNull(core); Objects.requireNonNull(contacts); Objects.requireNonNull(pharmacies); Objects.requireNonNull(values); Objects.requireNonNull(checkpoint);
        values.requireActive(); checkpoint.run();
        if (!core.sourceManifestSha256().equals(index.sourceManifestSha256()) || !core.sourceManifestSha256().equals(contacts.sourceManifestSha256())
                || !core.sourceManifestSha256().equals(pharmacies.sourceManifestSha256())) scope();
        var all = new ArrayList<>(contacts.associations()); all.addAll(contacts.contacts()); all.addAll(pharmacies.links()); all.addAll(pharmacies.pharmacies());
        if (maximumRows < 1 || maximumRows > 8192 || maximumBytes < 1 || maximumBytes > (64L << 20)) throw new IllegalArgumentException("CDS_EXPORT_DEMOGRAPHIC_CARE_LIMITS_INVALID");
        if (all.size() > maximumRows) limit(); long bytes = 0;
        for (var row : all) {
            checkpoint.run(); bytes = Math.addExact(bytes, 2048L);
            for (var value : row.fields().values()) if (value != null) bytes = Math.addExact(bytes, 32L + value.getBytes(StandardCharsets.UTF_8).length);
            if (bytes > maximumBytes) limit();
        }
        var professional = new CdsExportRetainedProfessionalContactReconciler().reconcile(patient, core.unitAddress(), index, checked, allFields,
                demographic, demographicWitness, contacts, maximumRows, maximumBytes, values, checkpoint);
        var personalRows = new ArrayList<CdsExportCapturedTextRowDto>(); var personalWitnesses = new ArrayList<CdsExportNativeRowReceiptDto>();
        var remaining = new HashSet<CdsExportNativeFieldKeyDto>();
        for (var row : concat(contacts.associations(), contacts.contacts())) {
            checkpoint.run(); var keys = new HashSet<CdsExportNativeFieldKeyDto>(); row.fields().keySet().forEach(column -> keys.add(new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), column)));
            if (Collections.disjoint(keys, professional.deferredNativeFields())) continue;
            if (!professional.deferredNativeFields().containsAll(keys)) throw new IOException("CDS_EXPORT_CONTACT_SHARED_ROLE_REQUIRES_REVIEW");
            remaining.addAll(keys); personalRows.add(row);
            contacts.witnesses().stream().filter(item -> item.table().equals(row.table()) && item.recordKey().equals(row.recordKey())).forEach(personalWitnesses::add);
        }
        if (!remaining.equals(professional.deferredNativeFields())) scope();
        var personalContactIds = new HashSet<String>(); personalRows.stream().filter(row -> row.table().equals("Contact")).forEach(row -> personalContactIds.add(row.fields().get("id")));
        for (var row : personalRows) if (row.table().equals("DemographicContact") && "2".equals(row.fields().get("type")) && !personalContactIds.contains(row.fields().get("contactId"))) {
            throw new IOException("CDS_EXPORT_CONTACT_SHARED_ROLE_REQUIRES_REVIEW");
        }
        var personal = new CdsExportContactRetainedAssembler().prepare(patient, core.unitAddress(), index, checked, allFields,
                personalRows.stream().filter(row -> row.table().equals("DemographicContact")).toList(), personalRows.stream().filter(row -> row.table().equals("Contact")).toList(),
                personalWitnesses, maximumRows, maximumBytes, values, checkpoint);
        if (!personal.fields().keySet().equals(remaining)) scope();
        var pharmacy = new CdsExportRetainedPharmacyReconciler().reconcile(patient, core.unitAddress(), index, checked, allFields,
                demographic, demographicWitness, pharmacies, maximumRows, maximumBytes, values, checkpoint);
        var result = combine(core, professional, personal, pharmacy);
        values.requireActive(); checkpoint.run(); return result;
    }

    static Result combine(CdsExportDemographicRetainedCoreReconciler.Result core, CdsExportRetainedProfessionalContactReconciler.Result professional,
            CdsExportContactRetainedAssembler.Result personal, CdsExportRetainedPharmacyReconciler.Result pharmacy) throws IOException {
        if (!core.sourceManifestSha256().equals(professional.sourceManifestSha256()) || !core.sourceManifestSha256().equals(personal.sourceManifestSha256())
                || !core.sourceManifestSha256().equals(pharmacy.sourceManifestSha256()) || !core.unitAddress().equals(professional.parentUnitAddress())
                || !core.unitAddress().equals(personal.parentUnitAddress()) || !core.unitAddress().equals(pharmacy.parentUnitAddress())
                || !personal.fields().keySet().equals(professional.deferredNativeFields())) scope();
        var fields = new LinkedHashMap<>(core.fields()); var decided = new HashSet<>(core.decidedSourceFields()); var sourceDeferred = new HashSet<>(core.deferredSourceFields());
        var nativeDeferred = new HashSet<>(core.deferredNativeFields()); var blockers = new HashSet<>(core.blockers());
        for (var component : List.of(professional.fields(), personal.fields(), pharmacy.fields())) {
            for (var entry : component.entrySet()) if (fields.putIfAbsent(entry.getKey(), entry.getValue()) != null) scope();
            nativeDeferred.removeAll(component.keySet());
        }
        for (var component : List.of(professional.sourceFields(), personal.sourceContactFields(), pharmacy.sourceFields())) {
            if (!Collections.disjoint(decided, component) || !sourceDeferred.containsAll(component)) scope(); decided.addAll(component); sourceDeferred.removeAll(component);
        }
        blockers.addAll(professional.blockers()); blockers.addAll(pharmacy.blockers()); var changes = new ArrayList<CdsExportRetainedSectionWriter.Change>();
        if (core.coreRepresentable() && professional.representable() && pharmacy.representable()
                && fields.values().stream().noneMatch(field -> field.outcome() == CdsExportFieldAssessmentDto.Outcome.REQUIRES_REVIEW)) {
            changes.addAll(core.coreChanges()); changes.addAll(professional.changes()); changes.addAll(personal.changes()); changes.addAll(pharmacy.changes());
        }
        var combined = new CdsExportDemographicRetainedCoreReconciler.Result(core.sourceManifestSha256(), core.unitAddress(), decided, sourceDeferred,
                fields, nativeDeferred, blockers, changes, core.providerUses());
        return new Result(combined, professional.nameUses());
    }
    private static List<CdsExportCapturedTextRowDto> concat(List<CdsExportCapturedTextRowDto> first, List<CdsExportCapturedTextRowDto> second) {
        var all = new ArrayList<>(first); all.addAll(second); return all;
    }
    private static void scope() throws IOException { throw new IOException("CDS_EXPORT_DEMOGRAPHIC_CARE_EVIDENCE_SCOPE_INVALID"); }
    private static void limit() throws IOException { throw new IOException("CDS_EXPORT_DEMOGRAPHIC_CARE_RESOURCE_LIMIT"); }
}
