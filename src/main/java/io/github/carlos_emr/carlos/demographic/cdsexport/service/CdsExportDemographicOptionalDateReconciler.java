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

import io.github.carlos_emr.carlos.commn.model.CdsClinicalException;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.*;
import java.io.*;
import java.time.*;
import java.util.*;

/** Reconciles optional demographic dates using the same effective-date policy as birth dates. */
public final class CdsExportDemographicOptionalDateReconciler {
    private static final String ROOT="/OmdCds[1]/PatientRecord[1]/Demographics[1]/";
    public enum DateField {
        STATUS("patient_status_date", "PersonStatusDate[1]", CdsExportDemographicProjectionEvidence.Group.STATUS),
        EXPIRY("hc_renew_date", "HealthCard[1]/Expirydate[1]", CdsExportDemographicProjectionEvidence.Group.HEALTH_CARD);
        final String column, path; final CdsExportDemographicProjectionEvidence.Group group;
        DateField(String column, String path, CdsExportDemographicProjectionEvidence.Group group){this.column=column; this.path=path; this.group=group; }
    }
    public record Result(String sourceManifestSha256, String unitAddress, Set<String> sourceFields, Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields, List<CdsExportRetainedSectionWriter.Change> proposedChanges, Map<String, String> wireValues){
        public Result{sourceFields=Set.copyOf(sourceFields); fields=Map.copyOf(fields); proposedChanges=List.copyOf(proposedChanges); wireValues=Map.copyOf(wireValues); }
        public boolean representable(){return fields.values().stream().noneMatch(field-> field.outcome()==Outcome.REQUIRES_REVIEW); }
        public List<CdsExportRetainedSectionWriter.Change> changes()throws IOException{if (!representable())throw new IOException("CDS_EXPORT_DEMOGRAPHIC_OPTIONAL_DATES_REQUIRES_REVIEW"); return proposedChanges; }
        @Override public String toString(){return "CdsExportDemographicOptionalDateReconciler.Result[protected]"; }
    }
    public Result reconcile(int patient, String address, CdsExportRetainedIndexDto index, CdsExportRetainedReceiptVerificationDto checked, List<CdsExportRetainedFieldDto> allFields, CdsExportCapturedTextRowDto current, CdsExportNativeRowReceiptDto witness, CdsExportRetainedSectionWriter.Values values, Runnable checkpoint)throws IOException{
        return reconcile(patient, address, index, checked, allFields, current, witness, values, checkpoint, EnumSet.allOf(DateField.class));
    }
    public Result reconcile(int patient, String address, CdsExportRetainedIndexDto index, CdsExportRetainedReceiptVerificationDto checked, List<CdsExportRetainedFieldDto> allFields, CdsExportCapturedTextRowDto current, CdsExportNativeRowReceiptDto witness, CdsExportRetainedSectionWriter.Values values, Runnable checkpoint, Set<DateField> selected)throws IOException{
        selected=Set.copyOf(selected); if (selected.isEmpty())throw new IllegalArgumentException("CDS_EXPORT_DEMOGRAPHIC_DATE_SELECTION_REQUIRED");
        Objects.requireNonNull(values); Objects.requireNonNull(checkpoint); values.requireActive(); checkpoint.run();
        var evidence=new CdsExportDemographicProjectionEvidence().verify(patient, address, index, checked, current, witness, checkpoint);
        var source=new CdsExportRetainedOccurrenceFields().fieldsFor(patient, CdsCategory.DEMOGRAPHICS, address, index, checked, allFields, checkpoint);
        var fields=new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>(); var changes=new ArrayList<CdsExportRetainedSectionWriter.Change>(); var ids=new HashSet<String>(); var wires=new HashMap<String, String>();
        for (var date:DateField.values())if (selected.contains(date)){
            checkpoint.run(); var candidates=source.stream().filter(field-> field.sourcePath().equals(ROOT+date.path)).toList(); if (candidates.size()>1)throw new IOException("CDS_EXPORT_DEMOGRAPHIC_OPTIONAL_DATE_SOURCE_INVALID"); var field=candidates.isEmpty()?null:candidates.getFirst(); if (field!=null)ids.add(field.id());
            String prepared=date==DateField.STATUS?evidence.preparation().details().statusDate():evidence.preparation().details().healthCardExpiry(); String raw=current.fields().get(date.column), wire=null, original=null, rule; boolean review=false; var projection=evidence.groups().get(date.group);
            if (field!=null)original=read(values, field, checkpoint);
            if (!projection.preparedProjectionProven()){rule="PREPARED_NATIVE_DATE_PROJECTION_NOT_PROVEN"; review=true; }
            else if (prepared!=null&&field==null){rule="PREPARED_SOURCE_DATE_REQUIRED"; review=true; }
            else if (raw==null||raw.isEmpty()){
                if (prepared==null&&field!=null){rule="UNPROJECTED_SOURCE_DATE_REQUIRES_REVIEW"; review=true; }
                else rule="EXPLICIT_CURRENT_NATIVE_DATE_ABSENCE";
            } else {
                try {
                    if (!raw.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw new DateTimeException("lexical"); LocalDate currentDate=LocalDate.parse(raw); if (currentDate.getYear()<1)throw new DateTimeException("range");
                    if (field==null){wire=currentDate.toString(); rule="EXACT_ADDED_CURRENT_NATIVE_DATE"; }
                    else if (prepared==null){rule="EDITED_UNPROJECTED_SOURCE_DATE_REQUIRES_REVIEW"; review=true; }
                    else {var decision=new CdsExportRetainedCalendarMapper().map(original, LocalDate.parse(prepared), currentDate, !projection.changedColumns().contains(date.column), date.path); wire=decision.lexical(); rule=decision.rule().name(); review=wire==null; }
                } catch (CdsClinicalException|DateTimeException|ArithmeticException invalid){rule="CURRENT_OR_SOURCE_DATE_REQUIRES_REVIEW"; review=true; }
            }
            if (!review&&!Objects.equals(original, wire))changes.add(new CdsExportRetainedSectionWriter.Change(date.path, field==null?Set.of():Set.of(field.id()), wire==null?null:content(wire)));
            if (!review&&wire!=null)wires.put(date.column, wire);
            fields.put(new CdsExportNativeFieldKeyDto("demographic", current.recordKey(), date.column), new CdsExportFieldAssessmentDto(raw==null?Presence.SQL_NULL:raw.isEmpty()?Presence.EMPTY:Presence.VALUE, review?Outcome.REQUIRES_REVIEW:wire==null?Outcome.ABSENT:Outcome.MAPPED, date.path, rule));
        }
        values.requireActive(); checkpoint.run(); return new Result(index.sourceManifestSha256(), address, ids, fields, changes, wires);
    }
    private static CdsExportXmlComposer.Content content(String wire){return xml-> xml.text(wire); }
    private static String read(CdsExportRetainedSectionWriter.Values values, CdsExportRetainedFieldDto field, Runnable checkpoint)throws IOException{
        var result=new StringBuilder(); char[] block=new char[128]; try (Reader input=values.text(field)){int count; while ((count=input.read(block))!=-1){checkpoint.run(); if (count==0||result.length()+count>64)throw new IOException("CDS_EXPORT_DEMOGRAPHIC_OPTIONAL_DATE_SOURCE_INVALID"); result.append(block, 0, count); }}finally {Arrays.fill(block, '\0'); }return result.toString();
    }
}
