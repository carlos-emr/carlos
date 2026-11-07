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

import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsDemographicNativeDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedReceiptVerificationDto.Reason;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/** Retained phone occurrences plus original/current extension and mobile witnesses; other properties remain explicit hand-offs. */
public final class CdsExportDemographicRetainedPhonesReconciler {
    private static final String ROOT="/OmdCds[1]/PatientRecord[1]/Demographics[1]/";
    private static final Pattern PHONE=Pattern.compile("PhoneNumber\\[([1-9][0-9]{0,17})\\](?:/.*)?");
    private static final Map<String, String> KEYS=Map.of("R", "hPhoneExt", "W", "wPhoneExt", "C", "demo_cell");
    public record Result(String sourceManifestSha256, String unitAddress, Set<String> sourceFields, Set<String> deferredPropertyRows,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields, List<CdsExportRetainedSectionWriter.Change> proposedChanges){
        public Result{sourceFields=Set.copyOf(sourceFields); deferredPropertyRows=Set.copyOf(deferredPropertyRows); fields=Map.copyOf(fields); proposedChanges=List.copyOf(proposedChanges); }
        public boolean phonesRepresentable(){return fields.values().stream().noneMatch(field-> field.outcome()==Outcome.REQUIRES_REVIEW); }
        public List<CdsExportRetainedSectionWriter.Change> changes()throws IOException{if (!phonesRepresentable())throw new IOException("CDS_EXPORT_DEMOGRAPHIC_RETAINED_PHONES_REQUIRES_REVIEW"); return proposedChanges; }
        @Override public String toString(){return "CdsExportDemographicRetainedPhonesReconciler.Result[protected]"; }
    }
    private record Property(boolean unchanged){ }
    public Result reconcile(int patient, String address, CdsExportRetainedIndexDto index, CdsExportRetainedReceiptVerificationDto checked, List<CdsExportRetainedFieldDto> allFields, CdsExportCapturedTextRowDto current, CdsExportNativeRowReceiptDto witness, List<CdsExportCapturedTextRowDto> propertyRows, List<CdsExportNativeRowReceiptDto> witnesses, int maximumRows, long maximumBytes, CdsExportRetainedSectionWriter.Values values, Runnable checkpoint)throws IOException{
        Objects.requireNonNull(values); Objects.requireNonNull(checkpoint); values.requireActive(); checkpoint.run();
        var evidence=new CdsExportDemographicProjectionEvidence().verify(patient, address, index, checked, current, witness, checkpoint);
        if (propertyRows==null||witnesses==null||maximumRows<1||maximumRows>8192||maximumBytes<1||maximumBytes>(64L<<20)||propertyRows.size()>maximumRows||witnesses.size()!=propertyRows.size())throw new IOException("CDS_EXPORT_DEMOGRAPHIC_RETAINED_PHONE_RESOURCE_LIMIT");
        var proofs=new HashMap<String, CdsExportNativeRowReceiptDto>(); for (var proof:witnesses){checkpoint.run(); if (proof==null||!"demographicExt".equals(proof.table())||proofs.putIfAbsent(proof.id(), proof)!=null)scope(); }
        var rows=new HashMap<String, CdsExportCapturedTextRowDto>(); var known=new ArrayList<CdsExportCapturedTextRowDto>(); var deferred=new HashSet<String>(); long bytes=0;
        for (var row:propertyRows){
            checkpoint.run(); if (row==null||!"demographicExt".equals(row.table())||!row.fields().keySet().equals(Set.copyOf(Plan.DEMOGRAPHIC_EXT.columns()))||!Integer.toString(patient).equals(row.fields().get("demographic_no")))scope(); String id=row.fields().get("id");
            try {long number=Long.parseLong(id); if (number<1||!Long.toString(number).equals(id)||!CdsExportPatientTableSourceReader.key(number).equals(row.recordKey()))scope(); }catch (NumberFormatException|NullPointerException invalid){scope(); }
            if (rows.putIfAbsent(id, row)!=null)scope(); requirePropertyWitness(row, proofs.get(id), checkpoint); bytes=Math.addExact(bytes, 2048); for (String value:row.fields().values())if (value!=null)bytes=Math.addExact(bytes, 32L+value.getBytes(StandardCharsets.UTF_8).length); if (bytes>maximumBytes)throw new IOException("CDS_EXPORT_DEMOGRAPHIC_RETAINED_PHONE_RESOURCE_LIMIT");
            if (KEYS.containsValue(row.fields().get("key_val")))known.add(row); else deferred.add(row.recordKey());
        }
        if (!rows.keySet().equals(proofs.keySet()))scope();
        var nativeRaw=new LinkedHashMap<>(current.fields()); nativeRaw.remove("demographic_no"); var demographic=new CdsExportDemographicRowDto(patient, nativeRaw); var mapping=new CdsExportDemographicPhonesMapper().map(new CdsExportDemographicMapper().map(demographic), demographic, known, maximumRows, maximumBytes, checkpoint); var fields=new LinkedHashMap<>(mapping.relatedFields());
        var original=properties(address, index, checked, rows, proofs, evidence.preparation(), checkpoint);
        var source=new CdsExportRetainedOccurrenceFields().fieldsFor(patient, CdsCategory.DEMOGRAPHICS, address, index, checked, allFields, checkpoint); var sourceGroups=new TreeMap<Long, Map<String, CdsExportRetainedFieldDto>>(); var sourceIds=new HashSet<String>(); long maximum=0;
        for (var field:source)if (field.sourcePath().startsWith(ROOT)){
            String relative=field.sourcePath().substring(ROOT.length()); var match=PHONE.matcher(relative); if (!match.matches())continue; long ordinal=Long.parseLong(match.group(1)); maximum=Math.max(maximum, ordinal); String prefix="PhoneNumber["+ordinal+"]"; if (sourceGroups.computeIfAbsent(ordinal, ignored-> new TreeMap<>()).putIfAbsent(relative.length()==prefix.length()?"":relative.substring(prefix.length()+1), field)!=null)scope(); sourceIds.add(field.id());
        }
        var byKind=new HashMap<String, List<Long>>(); for (var group:sourceGroups.entrySet()){String kind=read(values, group.getValue().get("@phoneNumberType"), 1, checkpoint); if (!KEYS.containsKey(kind))scope(); byKind.computeIfAbsent(kind, ignored-> new ArrayList<>()).add(group.getKey()); }
        var changes=new ArrayList<CdsExportRetainedSectionWriter.Change>();
        for (String kind:List.of("R", "W", "C")){
            checkpoint.run(); String key=KEYS.get(kind); var properties=known.stream().filter(row-> key.equals(row.fields().get("key_val"))).toList(); var property=properties.size()==1?properties.getFirst():null; var prior=original.get(key); var occurrences=byKind.getOrDefault(kind, List.of()); var prepared=kind.equals("R")?evidence.preparation().home():kind.equals("W")?evidence.preparation().work():evidence.preparation().cell();
            String column=kind.equals("R")?"phone":"phone2";
            boolean propertyProven=prior!=null||expected(evidence.preparation(), key)==null; boolean propertyChanged=prior==null?property!=null:!prior.unchanged();
            boolean numberProven=kind.equals("C")?propertyProven:evidence.groups().get(kind.equals("R")?CdsExportDemographicProjectionEvidence.Group.HOME_PHONE:CdsExportDemographicProjectionEvidence.Group.WORK_PHONE).preparedProjectionProven();
            boolean numberChanged=kind.equals("C")?propertyChanged:evidence.groups().get(kind.equals("R")?CdsExportDemographicProjectionEvidence.Group.HOME_PHONE:CdsExportDemographicProjectionEvidence.Group.WORK_PHONE).changedColumns().contains(column); boolean edited=numberChanged||propertyChanged;
            boolean metadataInvalid=properties.size()>1||properties.stream().anyMatch(row-> !"0".equals(row.fields().get("hidden"))||fields.get(new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), "key_val")).outcome()==Outcome.REQUIRES_REVIEW); String rule; boolean review=false;
            if (!numberProven||!propertyProven){rule="PREPARED_NATIVE_PHONE_PROJECTION_NOT_PROVEN"; review=true; }
            else if (metadataInvalid){rule="HIDDEN_OR_DUPLICATE_PHONE_PROPERTY_REQUIRES_REVIEW"; review=true; }
            else if (prepared!=null&&occurrences.isEmpty()){rule="PREPARED_SOURCE_PHONE_REQUIRED"; review=true; }
            else if (!edited){rule="VERIFIED_UNCHANGED_SOURCE_PHONE"; if (property!=null)markProperty(property, fields, Outcome.MAPPED, "PhoneNumber["+kind+"]", "VERIFIED_UNCHANGED_SOURCE_PHONE_PROPERTY"); }
            else if (occurrences.size()>1||prepared==null&&!occurrences.isEmpty()){rule="EDITED_AMBIGUOUS_SOURCE_PHONE_REQUIRES_REVIEW"; review=true; }
            else {
                String prefix=occurrences.isEmpty()?null:"PhoneNumber["+occurrences.getFirst()+"]"; var priorFields=occurrences.isEmpty()?Map.<String, CdsExportRetainedFieldDto>of():sourceGroups.get(occurrences.getFirst());
                String sourceExtension=priorFields.containsKey("extension[1]")?read(values, priorFields.get("extension[1]"), 5, checkpoint):null;
                boolean unprojectedCellExtension=kind.equals("C")&&sourceExtension!=null&&!sourceExtension.isEmpty(); var phone=mapping.phones().stream().filter(candidate-> candidate.type().equals(kind)).findFirst().orElse(null);
                boolean invalidNative=property!=null&&fields.get(new CdsExportNativeFieldKeyDto(property.table(), property.recordKey(), "value")).outcome()==Outcome.REQUIRES_REVIEW||!kind.equals("C")&&mapping.fields().get(column).outcome()==CdsExportDemographicMappingDto.Outcome.REQUIRES_REVIEW;
                if (unprojectedCellExtension&&numberChanged||invalidNative){rule="EDITED_UNPROJECTED_OR_UNREPRESENTABLE_PHONE_REQUIRES_REVIEW"; review=true; }
                else if (prefix==null&&phone==null)rule="EXPLICIT_CURRENT_NATIVE_PHONE_ABSENCE";
                else if (numberChanged||prefix==null){
                    String target=prefix==null?"PhoneNumber["+(++maximum)+"]":prefix; CdsExportXmlComposer.Content content=phone==null?null:xml-> {xml.attribute("", "phoneNumberType", kind); xml.element("cds_dt", "phoneNumber", phone.number()); String extension=phone.extension(); if (extension==null&&sourceExtension!=null&&sourceExtension.isEmpty())extension=""; if (extension!=null)xml.element("cds_dt", "extension", extension); }; replace(priorFields, target, "", content, changes); rule="EXACT_CURRENT_NATIVE_PHONE";
                }else {
                    String extension=phone==null?null:phone.extension(); replace(priorFields, prefix, "extension[1]", extension==null?null:xml-> xml.text(extension), changes); rule="EXACT_CURRENT_NATIVE_PHONE_EXTENSION";
                }
            }
            if (!kind.equals("C")){String raw=current.fields().get(column); fields.put(new CdsExportNativeFieldKeyDto("demographic", current.recordKey(), column), new CdsExportFieldAssessmentDto(raw==null?Presence.SQL_NULL:raw.isEmpty()?Presence.EMPTY:Presence.VALUE, review?Outcome.REQUIRES_REVIEW:Outcome.MAPPED, "PhoneNumber["+kind+"]", rule)); }
            if (review)for (var row:properties)markProperty(row, fields, Outcome.REQUIRES_REVIEW, "PhoneNumber["+kind+"]", rule);
            // A source-only mobile occurrence can require review without a current mobile row.
            if (review&&kind.equals("C")&&properties.isEmpty())throw new IOException("CDS_EXPORT_DEMOGRAPHIC_MOBILE_RECONCILIATION_REQUIRES_REVIEW");
        }
        values.requireActive(); checkpoint.run(); return new Result(index.sourceManifestSha256(), address, sourceIds, deferred, fields, changes);
    }
    private static Map<String, Property> properties(String address, CdsExportRetainedIndexDto index, CdsExportRetainedReceiptVerificationDto checked, Map<String, CdsExportCapturedTextRowDto> rows, Map<String, CdsExportNativeRowReceiptDto> proofs, CdsDemographicNativeDto preparation, Runnable checkpoint)throws IOException{
        String run=address.substring(0, 36), key=address.substring(37); var unit=index.tables().get("cds_import_unit").get(CdsImportKeys.hash(run, key)); CdsNativeBaselineCodec.Document baseline=null;
        if (unit.fields().get("native_baseline")!=null)try {baseline=CdsNativeBaselineCodec.decode(unit.fields().get("native_baseline"), unit.fields().get("target_references"), unit.fields().get("target_digest")); }catch (IllegalArgumentException invalid){scope(); }
        var result=new HashMap<String, Property>(); for (var ref:checked.units().get(address))if (ref.expected().table().equals("demographicExt")){
            checkpoint.run(); String id=ref.expected().id(); var row=rows.get(id); var proof=proofs.get(id); if (row==null||proof==null||ref.reason()==Reason.ROW_NOT_IN_CAPTURE)throw new IOException("CDS_EXPORT_DEMOGRAPHIC_PHONE_PROPERTY_DELETION_REQUIRES_REVIEW"); if (!Objects.equals(ref.currentDigest(), proof.digest()))scope();
            var old=baseline==null?ref.reason()==Reason.UNCHANGED_ROW?proof.state():null:baseline.rows().stream().filter(state-> state.table().equals("demographicExt")&&state.id().equals(id)).findFirst().orElse(null); if (old==null||!old.digest().equals(ref.expected().digest()))throw new IOException("CDS_EXPORT_DEMOGRAPHIC_PHONE_FIELD_BASELINE_REQUIRED");
            var difference=CdsNativeRowComparison.compare(old, proof.state()); if (difference.schemaChanged()||!Set.of("date_time").containsAll(difference.unqualifiedColumns()))throw new IOException("CDS_EXPORT_DEMOGRAPHIC_PHONE_FIELD_CODEC_REQUIRES_REVIEW");
            String originalKey=null; for (String candidate:KEYS.values())if (CdsExportCapturedTextWitness.matches(old, "key_val", candidate))originalKey=candidate; if (originalKey==null||!originalKey.equals(row.fields().get("key_val"))||!CdsExportCapturedTextWitness.matches(old, "demographic_no", row.fields().get("demographic_no"))||!CdsExportCapturedTextWitness.matches(old, "hidden", "0")||expected(preparation, originalKey)==null||!CdsExportCapturedTextWitness.matches(old, "value", expected(preparation, originalKey)))throw new IOException("CDS_EXPORT_DEMOGRAPHIC_PHONE_PROPERTY_PROJECTION_REQUIRES_REVIEW");
            for (var other:index.references().entrySet())if (!other.getKey().equals(address)&&other.getValue().stream().anyMatch(candidate-> candidate.table().equals("demographicExt")&&candidate.id().equals(id)))throw new IOException("CDS_EXPORT_DEMOGRAPHIC_PHONE_PROPERTY_ALIAS_REQUIRES_REVIEW");
            if (result.putIfAbsent(originalKey, new Property(Collections.disjoint(difference.changedColumns(), Set.of("demographic_no", "hidden", "key_val", "value"))))!=null)throw new IOException("CDS_EXPORT_DEMOGRAPHIC_PHONE_PROPERTY_ALIAS_REQUIRES_REVIEW");
        }return result;
    }
    private static String expected(CdsDemographicNativeDto preparation, String key){var phone=key.equals("hPhoneExt")?preparation.home():key.equals("wPhoneExt")?preparation.work():preparation.cell(); String value=phone==null?null:key.equals("demo_cell")?phone.number():phone.extension(); return value==null||value.isEmpty()?null:value; }
    /** Matches the existing seven-column SELECT * recipe; the captured invisible key has a separate disposition. */
    private static void requirePropertyWitness(CdsExportCapturedTextRowDto row, CdsExportNativeRowReceiptDto witness, Runnable checkpoint)throws IOException{
        if (!row.exactNumbers().isEmpty())scope(); var selected=new LinkedHashMap<>(row.fields()); selected.remove("dashboard_single_value_key");
        CdsExportCapturedTextWitness.require(new CdsExportCapturedTextRowDto(row.table(), row.recordKey(), selected), witness, checkpoint);
        String key=row.fields().get("key_val");
        // These exact ASCII phone keys are not the excluded dashboard-history key. Unknown
        // properties, including their generated value and collation semantics, stay deferred.
        if (KEYS.containsValue(key)&&!Objects.equals(key, row.fields().get("dashboard_single_value_key")))throw new IOException("CDS_EXPORT_DEMOGRAPHIC_PHONE_DERIVED_KEY_REQUIRES_REVIEW");
    }
    private static void markProperty(CdsExportCapturedTextRowDto row, Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields, Outcome outcome, String path, String rule){for (String column:List.of("key_val", "value")){var key=new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), column); fields.put(key, new CdsExportFieldAssessmentDto(fields.get(key).presence(), outcome, path, rule)); }}
    private static void replace(Map<String, CdsExportRetainedFieldDto> fields, String prefix, String path, CdsExportXmlComposer.Content content, List<CdsExportRetainedSectionWriter.Change> changes){var ids=new HashSet<String>(); fields.forEach((key, field)-> {if (path.isEmpty()||key.equals(path)||key.startsWith(path+"/"))ids.add(field.id()); }); if (!ids.isEmpty()||content!=null)changes.add(new CdsExportRetainedSectionWriter.Change(prefix+(path.isEmpty()?"":"/"+path), ids, content)); }
    private static String read(CdsExportRetainedSectionWriter.Values values, CdsExportRetainedFieldDto field, int maximum, Runnable checkpoint)throws IOException{if (field==null)scope(); var result=new StringBuilder(); char[] block=new char[128]; try (Reader input=values.text(field)){int count; while ((count=input.read(block))!=-1){checkpoint.run(); if (count==0||result.length()+count>maximum*2)scope(); result.append(block, 0, count); }}finally {Arrays.fill(block, '\0'); }if (result.codePointCount(0, result.length())>maximum)scope(); return result.toString(); }
    private static void scope()throws IOException{throw new IOException("CDS_EXPORT_DEMOGRAPHIC_RETAINED_PHONE_SCOPE_INVALID"); }
}
