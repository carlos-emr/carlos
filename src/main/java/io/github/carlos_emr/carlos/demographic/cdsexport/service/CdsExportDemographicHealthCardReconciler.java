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

import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.*;
import java.io.IOException;
import java.util.*;

/** Whole health-card decisions, including optional expiry through the shared calendar reconciler. */
public final class CdsExportDemographicHealthCardReconciler {
    private static final String ROOT="/OmdCds[1]/PatientRecord[1]/Demographics[1]/HealthCard[1]";
    private static final Map<String, String> COLUMNS=Map.of("hin", "Number[1]", "ver", "Version[1]", "hc_type", "ProvinceCode[1]");
    public record Result(String sourceManifestSha256, String unitAddress, Set<String> sourceFields, Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields, List<CdsExportRetainedSectionWriter.Change> proposedChanges){
        public Result{sourceFields=Set.copyOf(sourceFields); fields=Map.copyOf(fields); proposedChanges=List.copyOf(proposedChanges); }
        public boolean representable(){return fields.values().stream().noneMatch(field-> field.outcome()==Outcome.REQUIRES_REVIEW); }
        public List<CdsExportRetainedSectionWriter.Change> changes()throws IOException{if (!representable())throw new IOException("CDS_EXPORT_DEMOGRAPHIC_HEALTH_CARD_REQUIRES_REVIEW"); return proposedChanges; }
        @Override public String toString(){return "CdsExportDemographicHealthCardReconciler.Result[protected]"; }
    }
    public Result reconcile(int patient, String address, CdsExportRetainedIndexDto index, CdsExportRetainedReceiptVerificationDto checked, List<CdsExportRetainedFieldDto> allFields, CdsExportCapturedTextRowDto current, CdsExportNativeRowReceiptDto witness, CdsExportRetainedSectionWriter.Values values, Runnable checkpoint)throws IOException{
        Objects.requireNonNull(values); Objects.requireNonNull(checkpoint); values.requireActive(); checkpoint.run();
        var evidence=new CdsExportDemographicProjectionEvidence().verify(patient, address, index, checked, current, witness, checkpoint);
        var source=new CdsExportRetainedOccurrenceFields().fieldsFor(patient, CdsCategory.DEMOGRAPHICS, address, index, checked, allFields, checkpoint).stream().filter(field-> field.sourcePath().equals(ROOT)||field.sourcePath().startsWith(ROOT+"/")).toList();
        var expiry=new CdsExportDemographicOptionalDateReconciler().reconcile(patient, address, index, checked, allFields, current, witness, values, checkpoint, Set.of(CdsExportDemographicOptionalDateReconciler.DateField.EXPIRY));
        var raw=new LinkedHashMap<>(current.fields()); raw.remove("demographic_no"); var mapping=new CdsExportDemographicMapper().map(new CdsExportDemographicRowDto(patient, raw)); var projection=evidence.groups().get(CdsExportDemographicProjectionEvidence.Group.HEALTH_CARD);
        var fields=new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>(expiry.fields()); var changes=new ArrayList<CdsExportRetainedSectionWriter.Change>(); var ids=new HashSet<String>(); source.forEach(field-> ids.add(field.id()));
        boolean absent=List.of("hin", "ver", "hc_type", "hc_renew_date").stream().allMatch(column-> current.fields().get(column)==null||current.fields().get(column).isEmpty());
        boolean expectedCard=evidence.preparation().healthNumber()!=null||evidence.preparation().issuer()!=null||evidence.preparation().scalars().get("HealthCard/Version")!=null||evidence.preparation().details().healthCardExpiry()!=null;
        if (projection.preparedProjectionProven()&&absent&&!source.isEmpty())changes.add(new CdsExportRetainedSectionWriter.Change("HealthCard[1]", ids, null));
        for (var column:new TreeSet<>(COLUMNS.keySet())){
            checkpoint.run(); String leaf=COLUMNS.get(column), rule; boolean review=false; var prior=source.stream().filter(field-> field.sourcePath().equals(ROOT+"/"+leaf)).toList();
            if (prior.size()>1)throw new IOException("CDS_EXPORT_DEMOGRAPHIC_HEALTH_CARD_SOURCE_INVALID");
            if (!projection.preparedProjectionProven()){rule="PREPARED_NATIVE_HEALTH_CARD_PROJECTION_NOT_PROVEN"; review=true; }
            else if (expectedCard&&source.isEmpty()){rule="PREPARED_SOURCE_HEALTH_CARD_REQUIRED"; review=true; }
            else if (absent)rule="EXPLICIT_CURRENT_NATIVE_HEALTH_CARD_ABSENCE";
            else if (!projection.changedColumns().contains(column)){
                boolean required=column.equals("hin")||column.equals("hc_type")||column.equals("ver")&&evidence.preparation().scalars().get("HealthCard/Version")!=null; if (required&&prior.isEmpty()){rule="PREPARED_SOURCE_HEALTH_CARD_REQUIRED"; review=true; }else rule="VERIFIED_UNCHANGED_SOURCE_HEALTH_CARD_PART";
            } else if (mapping.fields().get(column).outcome()==CdsExportDemographicMappingDto.Outcome.REQUIRES_REVIEW){rule="CURRENT_NATIVE_HEALTH_CARD_PART_REQUIRES_REVIEW"; review=true; }
            else {
                var card=mapping.healthCard(); String value=card==null?null:switch (column){case "hin"-> card.number(); case "ver"-> card.version(); case "hc_type"-> card.province(); default-> throw new IllegalStateException(); };
                if (value==null&&!column.equals("ver")){rule="REQUIRED_CURRENT_HEALTH_CARD_PART_MISSING"; review=true; }
                else {var priorIds=new HashSet<String>(); prior.forEach(field-> priorIds.add(field.id())); if (!priorIds.isEmpty()||value!=null)changes.add(new CdsExportRetainedSectionWriter.Change("HealthCard[1]/"+leaf, priorIds, value==null?null:xml-> xml.text(value))); rule="EXACT_CURRENT_NATIVE_HEALTH_CARD_PART"; }
            }
            String value=current.fields().get(column); fields.put(new CdsExportNativeFieldKeyDto("demographic", current.recordKey(), column), new CdsExportFieldAssessmentDto(value==null?Presence.SQL_NULL:value.isEmpty()?Presence.EMPTY:Presence.VALUE, review?Outcome.REQUIRES_REVIEW:absent?Outcome.ABSENT:Outcome.MAPPED, "HealthCard/"+leaf, rule));
        }
        if (!absent){
            if (source.isEmpty()&&fields.values().stream().noneMatch(field-> field.outcome()==Outcome.REQUIRES_REVIEW)){
                var card=mapping.healthCard(); String date=expiry.wireValues().get("hc_renew_date"); changes.clear(); changes.add(new CdsExportRetainedSectionWriter.Change("HealthCard[1]", Set.of(), xml-> {
                    xml.element("cds_dt", "Number", card.number()); if (card.version()!=null)xml.element("cds_dt", "Version", card.version()); if (date!=null)xml.element("cds_dt", "Expirydate", date); xml.element("cds_dt", "ProvinceCode", card.province());
                }));
            }else changes.addAll(expiry.proposedChanges());
        }
        values.requireActive(); checkpoint.run(); return new Result(index.sourceManifestSha256(), address, ids, fields, changes);
    }
}
