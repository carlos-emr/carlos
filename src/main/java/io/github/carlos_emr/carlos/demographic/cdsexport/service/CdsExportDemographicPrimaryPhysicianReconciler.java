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

import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.*;
import java.io.*;
import java.util.*;

/** Primary relationship reconciliation; an unresolved source physician never becomes the import operator. */
public final class CdsExportDemographicPrimaryPhysicianReconciler {
    private static final String ROOT="/OmdCds[1]/PatientRecord[1]/Demographics[1]/PrimaryPhysician[1]";
    private static final Map<String, String> PATHS=Map.of("Name[1]/FirstName[1]", "first_name", "Name[1]/LastName[1]", "last_name", "OHIPPhysicianId[1]", "ohip_no", "PrimaryPhysicianCPSO[1]", "practitionerNo");
    public record Result(String sourceManifestSha256, String unitAddress, Set<String> sourceFields,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields, Set<String> blockers, List<CdsExportRetainedSectionWriter.Change> proposedChanges) {
        public Result{sourceFields=Set.copyOf(sourceFields); fields=Map.copyOf(fields); blockers=Set.copyOf(blockers); proposedChanges=List.copyOf(proposedChanges); }
        public boolean representable(){return blockers.isEmpty()&&fields.values().stream().noneMatch(value-> value.outcome()==Outcome.REQUIRES_REVIEW); }
        public List<CdsExportRetainedSectionWriter.Change> changes()throws IOException{if (!representable())throw new IOException("CDS_EXPORT_PRIMARY_PHYSICIAN_REQUIRES_REVIEW"); return proposedChanges; }
        @Override public String toString(){return "CdsExportDemographicPrimaryPhysicianReconciler.Result[protected]"; }
    }
    /** Reconciles one proven primary relationship; unprojected or conflicting source details require preservation or review. */
    public Result reconcile(int patient, String address, CdsExportRetainedIndexDto index, CdsExportRetainedReceiptVerificationDto checked,
            List<CdsExportRetainedFieldDto> allFields, CdsExportCapturedTextRowDto current, CdsExportNativeRowReceiptDto witness,
            CdsExportPublicProviderLookupsDto lookups, CdsExportRetainedSectionWriter.Values values, Runnable checkpoint)throws IOException {
        Objects.requireNonNull(values); Objects.requireNonNull(checkpoint); values.requireActive(); checkpoint.run(); lookups.requireGeneration(index.sourceManifestSha256()); var provider=lookups.find(current.fields().get("provider_no"));
        var evidence=new CdsExportDemographicProjectionEvidence().verify(patient, address, index, checked, current, witness, checkpoint);
        var parent=new CdsExportRetainedOccurrenceFields().fieldsFor(patient, CdsCategory.DEMOGRAPHICS, address, index, checked, allFields, checkpoint);
        var ids=new HashSet<String>(); var paths=new HashSet<String>(); var source=new HashMap<String, String>();
        for (var field:parent)if (field.sourcePath().startsWith(ROOT)||field.sourcePath().startsWith(ROOT.substring(0, ROOT.length()-3)+"[")) {
            checkpoint.run(); if (!field.sourcePath().equals(ROOT)&&!field.sourcePath().startsWith(ROOT+"/"))scope(); if (!ids.add(field.id())||!paths.add(field.sourcePath()))scope();
            String path=field.sourcePath().equals(ROOT)?"":field.sourcePath().substring(ROOT.length()+1); verifyType(path, field); String text=read(values, field, checkpoint);
            if (PATHS.containsKey(path)){int max=path.startsWith("Name[1]/")?50:6; if (text.codePointCount(0, text.length())>max)throw new IOException("CDS_EXPORT_PRIMARY_PHYSICIAN_SOURCE_CAPACITY_REQUIRES_REVIEW"); source.put(path, text); }
            else if (!Set.of("", "Name[1]").contains(path)||!text.isEmpty())scope();
        }
        var fields=new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>(); var blockers=new LinkedHashSet<String>(); var changes=new ArrayList<CdsExportRetainedSectionWriter.Change>();
        String id=current.fields().get("provider_no"), old=evidence.preparation().primaryProvider(); boolean present=present(id), wasPresent=present(old); String rule;
        var projection=evidence.groups().get(CdsExportDemographicProjectionEvidence.Group.PRIMARY_PROVIDER);
        if (!projection.preparedProjectionProven()){blockers.add("PRIMARY_PHYSICIAN_PROJECTION_NOT_PROVEN"); rule="PRIMARY_PHYSICIAN_PROJECTION_NOT_PROVEN"; }
        else if (wasPresent&&ids.isEmpty()){blockers.add("PREPARED_PRIMARY_PHYSICIAN_SOURCE_REQUIRED"); rule="PREPARED_PRIMARY_PHYSICIAN_SOURCE_REQUIRED"; }
        else if (!present) {
            if (provider!=null)scope();
            if (wasPresent&&!ids.isEmpty())changes.add(new CdsExportRetainedSectionWriter.Change("PrimaryPhysician[1]", ids, null));
            rule=wasPresent?"EXPLICIT_NATIVE_PRIMARY_RELATION_CLEARED":"UNPROJECTED_SOURCE_PRIMARY_DETAILS_RETAINED_WITHOUT_AN_INVENTED_PROVIDER";
        }else {
            if (provider==null||!id.equals(provider.fields().get("provider_no"))){blockers.add("CAPTURED_PRIMARY_PHYSICIAN_REQUIRED"); rule="CAPTURED_PRIMARY_PHYSICIAN_REQUIRED"; }
            else {
                var mapped=new CdsExportPublicProviderMapper().map(provider, CdsExportPublicProviderMapper.Target.PRIMARY_PHYSICIAN); fields.putAll(mapped.fields());
                if (!mapped.representable()){blockers.add("CURRENT_PRIMARY_PHYSICIAN_VALUES_REQUIRES_REVIEW"); rule="CURRENT_PRIMARY_PHYSICIAN_VALUES_REQUIRES_REVIEW"; }
                else if (!wasPresent&&!ids.isEmpty()){blockers.add("UNPROJECTED_SOURCE_PRIMARY_PHYSICIAN_CONFLICT"); rule="UNPROJECTED_SOURCE_PRIMARY_PHYSICIAN_CONFLICT"; }
                else {
                    var nativeValue=mapped.value(); var targets=new LinkedHashMap<String, String>(); targets.put("Name[1]/FirstName[1]", nativeValue.firstName()); targets.put("Name[1]/LastName[1]", nativeValue.lastName()); targets.put("OHIPPhysicianId[1]", nativeValue.ohipNumber()); targets.put("PrimaryPhysicianCPSO[1]", nativeValue.cpsoNumber());
                    boolean changed=!Objects.equals(id, old), update=ids.isEmpty();
                    if (!changed)for (var entry:source.entrySet()) {
                        String target=targets.get(entry.getKey()); if (present(target)&&!target.equals(entry.getValue()))blockers.add("SOURCE_AND_CURRENT_PRIMARY_DETAILS_CONFLICT");
                        else if (!present(target))targets.put(entry.getKey(), entry.getValue());
                    }
                    for (var entry:targets.entrySet())if (present(entry.getValue())&&!Objects.equals(entry.getValue(), source.get(entry.getKey())))update=true;
                    if (changed)update=true;
                    if (!changed&&update&&source.values().stream().anyMatch(String::isEmpty))blockers.add("EXPLICIT_EMPTY_SOURCE_PRIMARY_DETAILS_REQUIRE_REVIEW");
                    if (blockers.isEmpty()&&update) {
                        CdsExportPublicProviderDto wire;
                        try {wire=new CdsExportPublicProviderDto(targets.get("Name[1]/FirstName[1]"), targets.get("Name[1]/LastName[1]"), targets.get("OHIPPhysicianId[1]"), targets.get("PrimaryPhysicianCPSO[1]")); }
                        catch (IllegalArgumentException malformed){throw new IOException("CDS_EXPORT_PRIMARY_PHYSICIAN_VALUES_REQUIRES_REVIEW"); }
                        changes.add(new CdsExportRetainedSectionWriter.Change("PrimaryPhysician[1]", ids, xml-> new CdsExportPublicProviderXmlWriter().write(wire, xml, true)));
                    }
                    rule=changed?"EXPLICIT_CURRENT_NATIVE_PRIMARY_PHYSICIAN":"CURRENT_PRIMARY_RELATION_WITH_RETAINED_UNPROJECTED_DETAILS";
                }
            }
        }
        fields.put(new CdsExportNativeFieldKeyDto("demographic", current.recordKey(), "provider_no"), new CdsExportFieldAssessmentDto(id==null?Presence.SQL_NULL:id.isEmpty()?Presence.EMPTY:Presence.VALUE, blockers.isEmpty()?present?Outcome.MAPPED:Outcome.ABSENT:Outcome.REQUIRES_REVIEW, "PrimaryPhysician", rule));
        if (!blockers.isEmpty())changes.clear(); values.requireActive(); checkpoint.run(); return new Result(index.sourceManifestSha256(), address, ids, fields, blockers, changes);
    }
    private static boolean present(String value){return value!=null&&!value.isEmpty(); }
    private static String read(CdsExportRetainedSectionWriter.Values values, CdsExportRetainedFieldDto field, Runnable checkpoint)throws IOException {
        var text=new StringBuilder(); char[] block=new char[256]; try (Reader input=values.text(field)){for (int n; (n=input.read(block))!=-1; ){checkpoint.run(); if (n==0||text.length()+n>65536)throw new IOException("CDS_EXPORT_PRIMARY_PHYSICIAN_SOURCE_CAPACITY_REQUIRES_REVIEW"); text.append(block, 0, n); }}finally {Arrays.fill(block, '\0'); }return text.toString();
    }
    private static void verifyType(String path, CdsExportRetainedFieldDto field)throws IOException {
        var type=CdsWireSchema.schemaModel().element("cds", "OmdCds").child("cds", "PatientRecord").child("cds", "Demographics").child("cds", "PrimaryPhysician"); String namespace="cds";
        if (!path.isEmpty())for (String segment:path.split("/")){if (type==null)scope(); int end=segment.indexOf('['); if (end<1||!segment.endsWith("[1]"))scope(); String local=segment.substring(0, end); var candidates=type.childNames().stream().filter(name-> name.local().equals(local)).toList(); if (candidates.size()!=1)scope(); namespace=candidates.getFirst().namespace(); type=type.child(namespace, local); }
        if (type==null||!namespace.equals(field.namespace())||!field.valueType().equals("{"+type.getTypeNamespace()+"}"+type.getTypeName()))scope();
    }
    private static void scope()throws IOException{throw new IOException("CDS_EXPORT_PRIMARY_PHYSICIAN_SOURCE_SCOPE_INVALID"); }
}
