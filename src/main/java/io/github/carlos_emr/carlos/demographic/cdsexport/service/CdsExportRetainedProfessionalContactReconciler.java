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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import java.io.IOException;
import java.util.*;

/** Reconciles exact original physician relationships and current witnessed names; missing, moved or conflicting links require review. */
public final class CdsExportRetainedProfessionalContactReconciler {
    public record NativeRows(String sourceManifestSha256, List<CdsExportCapturedTextRowDto> associations,
            List<CdsExportCapturedTextRowDto> contacts, List<CdsExportNativeRowReceiptDto> witnesses) {
        public NativeRows{if (sourceManifestSha256==null||!sourceManifestSha256.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("CDS_EXPORT_PROFESSIONAL_NATIVE_GENERATION_INVALID"); associations=List.copyOf(associations); contacts=List.copyOf(contacts); witnesses=List.copyOf(witnesses); if ((long) associations.size()+contacts.size()>8192||witnesses.size()>8192)throw new IllegalArgumentException("CDS_EXPORT_PROFESSIONAL_NATIVE_ROWS_INVALID"); }
        @Override public String toString(){return "CdsExportRetainedProfessionalContactReconciler.NativeRows[protected]"; }
    }
    public record Result(String sourceManifestSha256, String parentUnitAddress, Set<String> sourceFields,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields, Set<CdsExportNativeFieldKeyDto> deferredNativeFields,
            Map<CdsExportNativeFieldKeyDto, Set<CdsExportProfessionalContactMapper.Role>> nameUses,
            Set<String> blockers, List<CdsExportRetainedSectionWriter.Change> proposedChanges) {
        public Result{sourceFields=Set.copyOf(sourceFields); fields=Map.copyOf(fields); deferredNativeFields=Set.copyOf(deferredNativeFields); var uses=new HashMap<CdsExportNativeFieldKeyDto, Set<CdsExportProfessionalContactMapper.Role>>(); nameUses.forEach((key, value)-> uses.put(key, Set.copyOf(value))); nameUses=Map.copyOf(uses); blockers=Set.copyOf(blockers); proposedChanges=List.copyOf(proposedChanges); }
        public boolean representable(){return blockers.isEmpty()&&fields.values().stream().noneMatch(field-> field.outcome()==Outcome.REQUIRES_REVIEW); }
        public List<CdsExportRetainedSectionWriter.Change> changes()throws IOException {if (!representable())throw new IOException("CDS_EXPORT_PROFESSIONAL_RELATIONSHIP_REQUIRES_REVIEW"); return proposedChanges; }
        @Override public String toString(){return "CdsExportRetainedProfessionalContactReconciler.Result[protected]"; }
    }
    /** Uses one authenticated capture. Unprocessed personal/other contact facts remain explicit hand-offs to the enclosing assembler. */
    public Result reconcile(int patient, String address, CdsExportRetainedIndexDto index, CdsExportRetainedReceiptVerificationDto checked,
            List<CdsExportRetainedFieldDto> allFields, CdsExportCapturedTextRowDto demographic, CdsExportNativeRowReceiptDto demographicWitness,
            NativeRows current, int maximumRows, long maximumBytes, CdsExportRetainedSectionWriter.Values values, Runnable checkpoint)throws IOException {
        Objects.requireNonNull(values); Objects.requireNonNull(checkpoint); values.requireActive(); checkpoint.run(); if (!index.sourceManifestSha256().equals(current.sourceManifestSha256()))throw new IOException("CDS_EXPORT_PROFESSIONAL_NATIVE_GENERATION_INVALID");
        if (maximumRows<1||maximumRows>8192||maximumBytes<1||maximumBytes>(64L<<20))throw new IllegalArgumentException("CDS_EXPORT_PROFESSIONAL_CONTACT_LIMITS_INVALID"); if ((long) current.associations().size()+current.contacts().size()>maximumRows)throw new IOException("CDS_EXPORT_PROFESSIONAL_CONTACT_RESOURCE_LIMIT");
        var witnesses=new HashMap<String, CdsExportNativeRowReceiptDto>(); for (var witness:current.witnesses()){checkpoint.run(); if (!Set.of("Contact", "DemographicContact").contains(witness.table())||witnesses.putIfAbsent(witness.table()+"/"+witness.recordKey(), witness)!=null)scope(); }
        var all=new ArrayList<>(current.associations()); all.addAll(current.contacts()); var rows=new HashMap<String, CdsExportCapturedTextRowDto>(); var associations=new HashMap<String, CdsExportCapturedTextRowDto>(); long bytes=0;
        for (var row:all){checkpoint.run(); String key=row.table()+"/"+row.recordKey(); if (rows.putIfAbsent(key, row)!=null)scope(); CdsExportCapturedTextWitness.require(row, witnesses.remove(key), checkpoint); for (var value:row.fields().values())if (value!=null)bytes=Math.addExact(bytes, 64L+value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length); if (bytes>maximumBytes)throw new IOException("CDS_EXPORT_PROFESSIONAL_CONTACT_RESOURCE_LIMIT"); }
        if (!witnesses.isEmpty())scope();
        for (var row:current.associations()){if (!row.table().equals("DemographicContact")||!Integer.toString(patient).equals(row.fields().get("demographicNo"))||associations.putIfAbsent(row.fields().get("id"), row)!=null)scope(); }
        for (var row:current.contacts())if (!row.table().equals("Contact"))scope();
        for (var reference:checked.units().getOrDefault(address, List.of()))if (Set.of("Contact", "DemographicContact").contains(reference.expected().table())){
            checkpoint.run(); var actual=all.stream().filter(row-> row.table().equals(reference.expected().table())&&reference.expected().id().equals(row.fields().get("id"))).toList(); if (actual.size()>1)scope();
            if (actual.isEmpty()){if (reference.reason()!=CdsExportRetainedReceiptVerificationDto.Reason.ROW_NOT_IN_CAPTURE)scope(); }
            else {var row=actual.getFirst(); var witness=current.witnesses().stream().filter(item-> item.table().equals(row.table())&&item.recordKey().equals(row.recordKey())).findFirst().orElseThrow(); if (!Objects.equals(reference.currentDigest(), witness.digest()))scope(); }
        }
        var professional=current.associations().stream().filter(row-> "professional".equals(row.fields().get("category"))).toList(); var ids=new HashSet<String>(); professional.stream().filter(row-> "2".equals(row.fields().get("type"))).forEach(row-> ids.add(row.fields().get("contactId"))); var contacts=current.contacts().stream().filter(row-> ids.contains(row.fields().get("id"))).toList();
        var nativeMapping=new CdsExportProfessionalContactMapper().map(patient, professional, contacts, maximumRows, maximumBytes, checkpoint); var deferred=new HashSet<CdsExportNativeFieldKeyDto>(); for (var row:all)for (String column:row.fields().keySet()){var key=new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), column); if (!nativeMapping.fields().containsKey(key))deferred.add(key); }
        var blockers=new LinkedHashSet<String>(); var sourceIds=new HashSet<String>(); var changes=new ArrayList<CdsExportRetainedSectionWriter.Change>();
        if (!nativeMapping.representable())blockers.add("CURRENT_PROFESSIONAL_CONTACTS_REQUIRE_REVIEW");
        for (var role:CdsExportProfessionalContactMapper.Role.values()) {
            checkpoint.run(); var binding=new CdsExportRetainedProfessionalContactBinding().bind(patient, address, role, index, checked, allFields, demographic, demographicWitness, values, checkpoint); if (!Collections.disjoint(sourceIds, binding.sourceFields()))scope(); sourceIds.addAll(binding.sourceFields());
            var candidates=nativeMapping.physicians().stream().filter(physician-> physician.role()==role).toList(); var physician=candidates.isEmpty()?null:candidates.getFirst();
            if (binding.projected()) {
                var link=associations.get(binding.association().id());
                if (link==null||physician==null||!link.recordKey().equals(physician.associationRecordKey())){blockers.add("ORIGINAL_PROFESSIONAL_RELATIONSHIP_MISSING_OR_MOVED"); continue; }
                if (!CdsExportCapturedTextWitness.matches(binding.association(), "contactId", link.fields().get("contactId"))){blockers.add("ORIGINAL_PROFESSIONAL_CONTACT_REFERENCE_CHANGED"); continue; }
                if (!Objects.equals(physician.name().firstName(), binding.preparation().firstName())||!Objects.equals(physician.name().lastName(), binding.preparation().lastName()))changes.add(new CdsExportRetainedSectionWriter.Change(role.wireName()+"[1]", binding.sourceFields(), xml-> new CdsExportProfessionalContactXmlWriter().writeNameContent(physician, xml)));
            }else if (physician!=null) {
                if (!binding.sourceFields().isEmpty()){blockers.add("UNPROJECTED_SOURCE_PROFESSIONAL_RELATIONSHIP_CONFLICT"); continue; }
                changes.add(new CdsExportRetainedSectionWriter.Change(role.wireName()+"[1]", Set.of(), xml-> new CdsExportProfessionalContactXmlWriter().writeNameContent(physician, xml)));
            }
        }
        if (!blockers.isEmpty())changes.clear(); values.requireActive(); checkpoint.run(); return new Result(index.sourceManifestSha256(), address, sourceIds, nativeMapping.fields(), deferred, nativeMapping.nameUses(), blockers, changes);
    }
    private static void scope()throws IOException{throw new IOException("CDS_EXPORT_PROFESSIONAL_CURRENT_WITNESS_SCOPE_INVALID"); }
}
