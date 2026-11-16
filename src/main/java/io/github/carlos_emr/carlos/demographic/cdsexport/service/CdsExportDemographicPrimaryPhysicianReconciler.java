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
        }else if (provider==null||!id.equals(provider.fields().get("provider_no"))) {
            // A number that names no captured provider record (the REST API stores any number):
            // no PrimaryPhysician states it. A changed number no longer names the imported
            // physician, so the imported element is removed; the number travels in the native
            // demographic record, as on a chart added in CARLOS.
            boolean changed=!Objects.equals(id, old);
            if (changed&&!ids.isEmpty())changes.add(new CdsExportRetainedSectionWriter.Change("PrimaryPhysician[1]", ids, null));
            fields.put(new CdsExportNativeFieldKeyDto("demographic", current.recordKey(), "provider_no"), new CdsExportFieldAssessmentDto(Presence.VALUE, Outcome.MAPPED, CdsExportDemographicSupportingMapper.DESTINATION, CdsExportDemographicMapper.PRIMARY_PHYSICIAN_WITHOUT_A_PROVIDER_RECORD));
            values.requireActive(); checkpoint.run(); return new Result(index.sourceManifestSha256(), address, ids, fields, blockers, changes);
        }else {
            var mapped=new CdsExportPublicProviderMapper().map(provider, CdsExportPublicProviderMapper.Target.PRIMARY_PHYSICIAN); fields.putAll(mapped.fields());
            // Provider names are varchar(30) NOT NULL (V1 baseline): a captured row always forms
            // a CDS name, so this review stays only for a row no CARLOS schema stores.
            if (!mapped.representable()){blockers.add("CURRENT_PRIMARY_PHYSICIAN_VALUES_REQUIRES_REVIEW"); rule="CURRENT_PRIMARY_PHYSICIAN_VALUES_REQUIRES_REVIEW"; }
            else {
                var nativeValue=mapped.value(); var targets=new LinkedHashMap<String, String>(); targets.put("Name[1]/FirstName[1]", nativeValue.firstName()); targets.put("Name[1]/LastName[1]", nativeValue.lastName()); targets.put("OHIPPhysicianId[1]", nativeValue.ohipNumber()); targets.put("PrimaryPhysicianCPSO[1]", nativeValue.cpsoNumber());
                boolean changed=!Objects.equals(id, old), update=ids.isEmpty();
                // A physician set in CARLOS on a chart whose imported physician the importer
                // matched to no provider: the chart's physician replaces the imported element,
                // which stays in the import's retained source and is listed as replaced.
                boolean supersedes=!wasPresent&&!ids.isEmpty();
                boolean edited=false;
                if (!changed&&!supersedes)for (var entry:source.entrySet()) {
                    String target=targets.get(entry.getKey());
                    // The provider record edited since the import (or matched by number with
                    // another spelling): CARLOS shows the record's values, so they replace the
                    // imported ones. A value the record lacks keeps the imported one (an
                    // explicitly empty imported part states nothing to keep).
                    if (present(target)&&!target.equals(entry.getValue()))edited=true;
                    else if (!present(target)&&present(entry.getValue()))targets.put(entry.getKey(), entry.getValue());
                }
                for (var entry:targets.entrySet())if (present(entry.getValue())&&!Objects.equals(entry.getValue(), source.get(entry.getKey())))update=true;
                if (changed||supersedes)update=true;
                if (update) {
                    CdsExportPublicProviderDto wire;
                    try {wire=new CdsExportPublicProviderDto(targets.get("Name[1]/FirstName[1]"), targets.get("Name[1]/LastName[1]"), targets.get("OHIPPhysicianId[1]"), targets.get("PrimaryPhysicianCPSO[1]")); }
                    catch (IllegalArgumentException malformed){throw new IOException("CDS_EXPORT_PRIMARY_PHYSICIAN_VALUES_REQUIRES_REVIEW"); }
                    changes.add(new CdsExportRetainedSectionWriter.Change("PrimaryPhysician[1]", ids, xml-> new CdsExportPublicProviderXmlWriter().write(wire, xml, true)));
                }
                rule=supersedes?"CURRENT_PRIMARY_PHYSICIAN_SUPERSEDES_UNMATCHED_SOURCE":changed?"EXPLICIT_CURRENT_NATIVE_PRIMARY_PHYSICIAN":edited?"CURRENT_PROVIDER_RECORD_SUPERSEDES_SOURCE_PRIMARY_DETAILS":"CURRENT_PRIMARY_RELATION_WITH_RETAINED_UNPROJECTED_DETAILS";
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
