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
    public record Result(String sourceManifestSha256, String unitAddress, Set<String> decidedSourceFields, Set<String> deferredSourceFields,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields, Set<CdsExportNativeFieldKeyDto> deferredNativeFields,
            Set<String> blockers, List<CdsExportRetainedSectionWriter.Change> proposedChanges){
        public Result{decidedSourceFields=Set.copyOf(decidedSourceFields); deferredSourceFields=Set.copyOf(deferredSourceFields); fields=Map.copyOf(fields); deferredNativeFields=Set.copyOf(deferredNativeFields); blockers=Set.copyOf(blockers); proposedChanges=List.copyOf(proposedChanges); }
        public boolean coreRepresentable(){return blockers.isEmpty()&&fields.values().stream().noneMatch(field-> field.outcome()==Outcome.REQUIRES_REVIEW); }
        public boolean demographicComplete(){return coreRepresentable()&&deferredNativeFields.isEmpty()&&deferredSourceFields.isEmpty(); }
        public List<CdsExportRetainedSectionWriter.Change> coreChanges()throws IOException{if (!coreRepresentable())throw new IOException("CDS_EXPORT_DEMOGRAPHIC_CORE_REQUIRES_REVIEW"); return proposedChanges; }
        @Override public String toString(){return "CdsExportDemographicRetainedCoreReconciler.Result[protected]"; }
    }
    public Result reconcile(int patient, String address, CdsExportRetainedIndexDto index, CdsExportRetainedReceiptVerificationDto checked, List<CdsExportRetainedFieldDto> allFields, CdsExportCapturedTextRowDto current, CdsExportNativeRowReceiptDto witness, CdsExportRetainedSectionWriter.Values values, Runnable checkpoint)throws IOException{
        Objects.requireNonNull(values); Objects.requireNonNull(checkpoint); values.requireActive(); checkpoint.run();
        var names=new CdsExportDemographicNamesReconciler().reconcile(patient, address, index, checked, allFields, current, witness, values, checkpoint);
        var birth=new CdsExportDemographicBirthDateReconciler().reconcile(patient, address, index, checked, allFields, current, witness, values, checkpoint);
        var scalars=new CdsExportDemographicScalarReconciler().reconcile(patient, address, index, checked, allFields, current, witness, values, checkpoint);
        var statusDate=new CdsExportDemographicOptionalDateReconciler().reconcile(patient, address, index, checked, allFields, current, witness, values, checkpoint, Set.of(CdsExportDemographicOptionalDateReconciler.DateField.STATUS));
        var health=new CdsExportDemographicHealthCardReconciler().reconcile(patient, address, index, checked, allFields, current, witness, values, checkpoint);
        var addresses=new CdsExportDemographicAddressReconciler().reconcile(patient, address, index, checked, allFields, current, witness, values, checkpoint);
        var fields=new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        for (var component:List.of(names.fields(), birth.fields(), scalars.fields(), statusDate.fields(), health.fields(), addresses.fields()))for (var entry:component.entrySet())if (fields.putIfAbsent(entry.getKey(), entry.getValue())!=null)throw new IOException("CDS_EXPORT_DEMOGRAPHIC_CORE_EVIDENCE_OVERLAP");
        var decided=new HashSet<String>(); for (var source:List.of(names.sourceFields(), Set.of(birth.sourceField().id()), scalars.sourceFields(), statusDate.sourceFields(), health.sourceFields(), addresses.sourceFields()))if (!Collections.disjoint(decided, source))throw new IOException("CDS_EXPORT_DEMOGRAPHIC_CORE_EVIDENCE_OVERLAP"); else decided.addAll(source);
        var parent=new CdsExportRetainedOccurrenceFields().fieldsFor(patient, CdsCategory.DEMOGRAPHICS, address, index, checked, allFields, checkpoint); var sourceIds=new HashSet<String>(); parent.forEach(field-> sourceIds.add(field.id())); if (!sourceIds.containsAll(decided))throw new IOException("CDS_EXPORT_DEMOGRAPHIC_CORE_EVIDENCE_SCOPE_INVALID"); sourceIds.removeAll(decided);
        var deferred=new HashSet<CdsExportNativeFieldKeyDto>(); for (String column:current.fields().keySet())if (!column.equals("demographic_no")){var key=new CdsExportNativeFieldKeyDto("demographic", current.recordKey(), column); if (!fields.containsKey(key))deferred.add(key); }
        var changes=new ArrayList<CdsExportRetainedSectionWriter.Change>(); boolean ready=fields.values().stream().noneMatch(field-> field.outcome()==Outcome.REQUIRES_REVIEW);
        if (ready){changes.addAll(names.changes()); changes.addAll(birth.changes()); changes.addAll(scalars.changes()); changes.addAll(statusDate.changes()); changes.addAll(health.changes()); changes.addAll(addresses.changes()); }
        values.requireActive(); checkpoint.run(); return new Result(index.sourceManifestSha256(), address, decided, sourceIds, fields, deferred, Set.of(), changes);
    }
    /** Coordinated phones/note ownership; all remaining fields and source IDs still require the enclosing assembly. */
    public Result reconcileWithAdjuncts(int patient, Set<CdsCategory> selected, String address, CdsExportRetainedIndexDto index, CdsExportRetainedReceiptVerificationDto checked, List<CdsExportRetainedFieldDto> allFields, CdsExportCapturedTextRowDto current, CdsExportNativeRowReceiptDto witness, List<CdsExportCapturedTextRowDto> properties, List<CdsExportNativeRowReceiptDto> propertyWitnesses, CdsExportCapturedTextRowDto custom, CdsExportNativeRowReceiptDto customWitness, int maximumRows, long maximumBytes, CdsExportRetainedSectionWriter.Values values, Runnable checkpoint)throws IOException{
        var core=reconcile(patient, address, index, checked, allFields, current, witness, values, checkpoint);
        var phones=new CdsExportDemographicRetainedPhonesReconciler().reconcile(patient, address, index, checked, allFields, current, witness, properties, propertyWitnesses, maximumRows, maximumBytes, values, checkpoint);
        var note=new CdsExportDemographicRetainedNoteReconciler().reconcile(patient, selected, address, index, checked, allFields, current, witness, custom, customWitness, values, checkpoint);
        var fields=new LinkedHashMap<>(core.fields()); for (var component:List.of(phones.fields(), note.fields()))for (var entry:component.entrySet())if (fields.putIfAbsent(entry.getKey(), entry.getValue())!=null)throw new IOException("CDS_EXPORT_DEMOGRAPHIC_CORE_EVIDENCE_OVERLAP");
        var decided=new HashSet<>(core.decidedSourceFields()); var sourceDeferred=new HashSet<>(core.deferredSourceFields());
        for (var component:List.of(phones.sourceFields(), note.sourceFields())){
            if (!Collections.disjoint(decided, component)||!sourceDeferred.containsAll(component))throw new IOException("CDS_EXPORT_DEMOGRAPHIC_CORE_EVIDENCE_OVERLAP"); decided.addAll(component); sourceDeferred.removeAll(component);
        }
        var nativeDeferred=new HashSet<>(core.deferredNativeFields()); nativeDeferred.removeAll(fields.keySet()); nativeDeferred.addAll(note.deferredNativeFields());
        for (var row:properties)if (phones.deferredPropertyRows().contains(row.recordKey()))for (String column:row.fields().keySet())nativeDeferred.add(new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), column));
        var blockers=new HashSet<>(core.blockers()); blockers.addAll(note.blockers()); var changes=new ArrayList<CdsExportRetainedSectionWriter.Change>();
        if (core.coreRepresentable()&&phones.phonesRepresentable()&&note.noteRepresentable()){changes.addAll(core.coreChanges()); changes.addAll(phones.changes()); changes.addAll(note.changes()); }
        values.requireActive(); checkpoint.run(); return new Result(index.sourceManifestSha256(), address, decided, sourceDeferred, fields, nativeDeferred, blockers, changes);
    }
}
