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

/** Discrete demographic values retain authenticated source representations until their exact native dependency changes. */
public final class CdsExportDemographicScalarReconciler {
    private static final String ROOT="/OmdCds[1]/PatientRecord[1]/Demographics[1]/";
    private enum Scalar {
        GENDER("sex", "Gender[1]", CdsExportDemographicProjectionEvidence.Group.GENDER),
        CHART("chart_no", "ChartNumber[1]", CdsExportDemographicProjectionEvidence.Group.CHART),
        EMAIL("email", "Email[1]", CdsExportDemographicProjectionEvidence.Group.EMAIL),
        SIN("sin", "SIN[1]", CdsExportDemographicProjectionEvidence.Group.SOCIAL_INSURANCE),
        OFFICIAL("official_lang", "PreferredOfficialLanguage[1]", CdsExportDemographicProjectionEvidence.Group.OFFICIAL_LANGUAGE),
        SPOKEN("spoken_lang", "PreferredSpokenLanguage[1]", CdsExportDemographicProjectionEvidence.Group.SPOKEN_LANGUAGE),
        STATUS("patient_status", "PersonStatusCode[1]", CdsExportDemographicProjectionEvidence.Group.STATUS);
        final String column, path; final CdsExportDemographicProjectionEvidence.Group group;
        Scalar(String column, String path, CdsExportDemographicProjectionEvidence.Group group){this.column=column; this.path=path; this.group=group; }
    }
    public record Result(String sourceManifestSha256, String unitAddress, Set<String> sourceFields, Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields, List<CdsExportRetainedSectionWriter.Change> proposedChanges){
        public Result{sourceFields=Set.copyOf(sourceFields); fields=Map.copyOf(fields); proposedChanges=List.copyOf(proposedChanges); }
        public boolean representable(){return fields.values().stream().noneMatch(field-> field.outcome()==Outcome.REQUIRES_REVIEW); }
        public List<CdsExportRetainedSectionWriter.Change> changes()throws IOException{if (!representable())throw new IOException("CDS_EXPORT_DEMOGRAPHIC_SCALARS_REQUIRES_REVIEW"); return proposedChanges; }
        @Override public String toString(){return "CdsExportDemographicScalarReconciler.Result[protected]"; }
    }
    public Result reconcile(int patient, String address, CdsExportRetainedIndexDto index, CdsExportRetainedReceiptVerificationDto checked, List<CdsExportRetainedFieldDto> allFields, CdsExportCapturedTextRowDto current, CdsExportNativeRowReceiptDto witness, CdsExportRetainedSectionWriter.Values values, Runnable checkpoint)throws IOException{
        Objects.requireNonNull(values); Objects.requireNonNull(checkpoint); values.requireActive(); checkpoint.run();
        var evidence=new CdsExportDemographicProjectionEvidence().verify(patient, address, index, checked, current, witness, checkpoint);
        var source=new CdsExportRetainedOccurrenceFields().fieldsFor(patient, CdsCategory.DEMOGRAPHICS, address, index, checked, allFields, checkpoint);
        var raw=new LinkedHashMap<>(current.fields()); raw.remove("demographic_no"); var mapping=new CdsExportDemographicMapper().map(new CdsExportDemographicRowDto(patient, raw));
        var fields=new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>(); var changes=new ArrayList<CdsExportRetainedSectionWriter.Change>(); var ids=new HashSet<String>();
        for (var scalar:Scalar.values()){
            checkpoint.run(); String path=ROOT+scalar.path; var prior=source.stream().filter(field-> field.sourcePath().equals(path)||field.sourcePath().startsWith(path+"/")).toList(); prior.forEach(field-> ids.add(field.id()));
            var projection=evidence.groups().get(scalar.group); String rule; boolean review=false;
            boolean projected=projected(scalar, evidence.preparation());
            if (!projection.preparedProjectionProven()){rule="PREPARED_NATIVE_SCALAR_PROJECTION_NOT_PROVEN"; review=true; }
            else if ((scalar==Scalar.GENDER||scalar==Scalar.STATUS||projected)&&prior.isEmpty()){rule="PREPARED_SOURCE_SCALAR_REQUIRED"; review=true; }
            else if (!projection.changedColumns().contains(scalar.column))rule="VERIFIED_UNCHANGED_SOURCE_SCALAR";
            else if (!projected&&!prior.isEmpty()){rule="EDITED_UNPROJECTED_SOURCE_SCALAR_REQUIRES_REVIEW"; review=true; }
            else if (mapping.fields().get(scalar.column).outcome()==CdsExportDemographicMappingDto.Outcome.REQUIRES_REVIEW){rule="CURRENT_NATIVE_SCALAR_REQUIRES_REVIEW"; review=true; }
            else {
                String value=switch (scalar){case GENDER-> mapping.administrativeGender(); case CHART-> mapping.chartNumber(); case EMAIL-> mapping.email(); case SIN-> mapping.sin(); case OFFICIAL-> mapping.officialLanguage(); case SPOKEN-> mapping.spokenLanguage(); case STATUS-> mapping.personStatus(); };
                CdsExportXmlComposer.Content replacement=value==null?null:scalar==Scalar.STATUS?xml-> xml.element("cds", mapping.enumeratedStatus()?"PersonStatusAsEnum":"PersonStatusAsPlainText", value):xml-> xml.text(value);
                var priorIds=new HashSet<String>(); prior.forEach(field-> priorIds.add(field.id())); if (!priorIds.isEmpty()||replacement!=null)changes.add(new CdsExportRetainedSectionWriter.Change(scalar.path, priorIds, replacement)); rule="EXACT_CURRENT_NATIVE_SCALAR";
            }
            String value=current.fields().get(scalar.column); fields.put(new CdsExportNativeFieldKeyDto("demographic", current.recordKey(), scalar.column), new CdsExportFieldAssessmentDto(value==null?Presence.SQL_NULL:value.isEmpty()?Presence.EMPTY:Presence.VALUE, review?Outcome.REQUIRES_REVIEW:Outcome.MAPPED, scalar.path, rule));
        }
        values.requireActive(); checkpoint.run(); return new Result(index.sourceManifestSha256(), address, ids, fields, changes);
    }
    private static boolean projected(Scalar scalar, io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsDemographicNativeDto value){
        String prepared=switch (scalar){case GENDER-> value.gender(); case CHART-> value.scalars().get("ChartNumber"); case EMAIL-> value.scalars().get("Email"); case SIN-> value.scalars().get("SIN"); case OFFICIAL-> value.scalars().get("PreferredOfficialLanguage"); case SPOKEN-> value.details().spokenLanguage(); case STATUS-> value.status(); }; return prepared!=null;
    }
}
