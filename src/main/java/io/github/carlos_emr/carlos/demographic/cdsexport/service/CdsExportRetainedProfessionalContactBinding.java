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
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsDemographicRelationsDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.*;
import java.util.*;

/** Source physician names bind through the demographic parent's original relationship and contact witnesses, never name equality alone. */
public final class CdsExportRetainedProfessionalContactBinding {
    public record Result(String sourceManifestSha256, String parentUnitAddress, CdsExportProfessionalContactMapper.Role role,
            Set<String> sourceFields, CdsDemographicRelationsDto.Person preparation, CdsNativeRowState association, CdsNativeRowState contact) {
        public Result{sourceFields=Set.copyOf(sourceFields); if ((association==null)!=(contact==null))throw new IllegalArgumentException("CDS_EXPORT_PROFESSIONAL_BINDING_INVALID"); }
        public boolean projected(){return association!=null; }
        @Override public String toString(){return "CdsExportRetainedProfessionalContactBinding.Result[protected]"; }
    }
    /** Distinguishes original projection from source-only details before any current relationship can replace them. */
    public Result bind(int patient, String address, CdsExportProfessionalContactMapper.Role role, CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked, List<CdsExportRetainedFieldDto> allFields,
            CdsExportCapturedTextRowDto current, CdsExportNativeRowReceiptDto witness,
            CdsExportRetainedSectionWriter.Values values, Runnable checkpoint)throws IOException {
        Objects.requireNonNull(role); Objects.requireNonNull(values); Objects.requireNonNull(checkpoint); checkpoint.run(); values.requireActive();
        var evidence=new CdsExportDemographicProjectionEvidence().verify(patient, address, index, checked, current, witness, checkpoint);
        var parent=new CdsExportRetainedOccurrenceFields().fieldsFor(patient, CdsCategory.DEMOGRAPHICS, address, index, checked, allFields, checkpoint);
        String prefix="/OmdCds[1]/PatientRecord[1]/Demographics[1]/"+role.wireName()+"[1]"; var source=new LinkedHashMap<String, String>(); var ids=new HashSet<String>();
        for (var field:parent)if (field.sourcePath().startsWith(prefix)||field.sourcePath().startsWith(prefix.substring(0, prefix.length()-3)+"[")) {
            checkpoint.run(); if (!field.sourcePath().equals(prefix)&&!field.sourcePath().startsWith(prefix+"/"))scope(); String path=field.sourcePath().equals(prefix)?"":field.sourcePath().substring(prefix.length()+1);
            if (!ids.add(field.id())||source.containsKey(path))scope(); verifyType(role, path, field); String value=read(values, field, checkpoint); if (value.codePointCount(0, value.length())>50)throw new IOException("CDS_EXPORT_PROFESSIONAL_SOURCE_NAME_REQUIRES_REVIEW"); source.put(path, value);
        }
        if (source.containsKey("")&&(!source.get("").isEmpty()||source.size()>1))scope();
        var relations=evidence.preparation().relations(); var preparation=role==CdsExportProfessionalContactMapper.Role.FAMILY?relations.familyPhysician():relations.referringPhysician();
        if (preparation!=null&&(!Objects.equals(preparation.firstName(), source.get("FirstName[1]"))||!Objects.equals(preparation.lastName(), source.get("LastName[1]"))))throw new IOException("CDS_EXPORT_PROFESSIONAL_SOURCE_PREPARATION_DIFFERS");
        String run=address.substring(0, 36), unitKey=address.substring(37); var unit=index.tables().get("cds_import_unit").get(CdsImportKeys.hash(run, unitKey)); var refs=index.references().get(address);
        if (unit==null||refs==null)scope(); boolean hasContacts=refs.stream().anyMatch(ref-> Set.of("Contact", "DemographicContact").contains(ref.table()));
        if (unit.fields().get("native_baseline")==null) {
            if (hasContacts)throw new IOException("CDS_EXPORT_PROFESSIONAL_FIELD_BASELINE_REQUIRED");
            return result(index, address, role, ids, preparation, null, null, values, checkpoint);
        }
        CdsNativeBaselineCodec.Document baseline;
        try {baseline=CdsNativeBaselineCodec.decode(unit.fields().get("native_baseline"), unit.fields().get("target_references"), unit.fields().get("target_digest")); }
        catch (IllegalArgumentException malformed){throw new IOException("CDS_EXPORT_PROFESSIONAL_FIELD_BASELINE_REQUIRED"); }
        var declared=new HashMap<String, String>(); for (var ref:refs){String previous=declared.putIfAbsent(ref.table()+"/"+ref.id(), ref.digest()); if (previous!=null&&!previous.equals(ref.digest()))scope(); }var witnessed=new HashMap<String, String>(); for (var row:baseline.rows())witnessed.put(row.table()+"/"+row.id(), row.digest()); if (!declared.equals(witnessed))scope();
        var candidates=new ArrayList<CdsNativeRowState>();
        for (var row:baseline.rows())if (row.table().equals("DemographicContact")) {
            checkpoint.run(); if (CdsExportCapturedTextWitness.matches(row, "role", role.nativeLabel())&&CdsExportCapturedTextWitness.matches(row, "category", "professional"))candidates.add(row);
        }
        if (candidates.isEmpty())return result(index, address, role, ids, preparation, null, null, values, checkpoint);
        if (candidates.size()!=1||preparation==null||ids.isEmpty())throw new io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException("CDS_EXPORT_PROFESSIONAL_ORIGINAL_RELATIONSHIP_REQUIRES_REVIEW");
        var association=candidates.getFirst();
        if (!CdsExportCapturedTextWitness.matches(association, "demographicNo", Integer.toString(patient))||!CdsExportCapturedTextWitness.matches(association, "type", "2")
                ||!CdsExportCapturedTextWitness.matches(association, "active", "1")||!CdsExportCapturedTextWitness.matches(association, "deleted", "0")||!CdsExportCapturedTextWitness.matches(association, "mrp", "0"))throw new io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException("CDS_EXPORT_PROFESSIONAL_ORIGINAL_RELATIONSHIP_REQUIRES_REVIEW");
        var contacts=new ArrayList<CdsNativeRowState>(); for (var row:baseline.rows())if (row.table().equals("Contact")){checkpoint.run(); if (CdsExportCapturedTextWitness.matches(association, "contactId", row.id()))contacts.add(row); }
        if (contacts.size()!=1)throw new IOException("CDS_EXPORT_PROFESSIONAL_ORIGINAL_CONTACT_REQUIRED"); var contact=contacts.getFirst();
        if (!CdsExportCapturedTextWitness.matches(contact, "firstName", preparation.firstName())||!CdsExportCapturedTextWitness.matches(contact, "lastName", preparation.lastName())
                ||!CdsExportCapturedTextWitness.matches(contact, "type", "ProfessionalContact")||!CdsExportCapturedTextWitness.matches(contact, "deleted", "0"))throw new IOException("CDS_EXPORT_PROFESSIONAL_PREPARED_PROJECTION_NOT_PROVEN");
        return result(index, address, role, ids, preparation, association, contact, values, checkpoint);
    }
    private static Result result(CdsExportRetainedIndexDto index, String address, CdsExportProfessionalContactMapper.Role role, Set<String> ids, CdsDemographicRelationsDto.Person preparation, CdsNativeRowState association, CdsNativeRowState contact, CdsExportRetainedSectionWriter.Values values, Runnable checkpoint)throws IOException {values.requireActive(); checkpoint.run(); return new Result(index.sourceManifestSha256(), address, role, ids, preparation, association, contact); }
    private static String read(CdsExportRetainedSectionWriter.Values values, CdsExportRetainedFieldDto field, Runnable checkpoint)throws IOException {var text=new StringBuilder(); char[] block=new char[256]; try (Reader input=values.text(field)){for (int n; (n=input.read(block))!=-1; ){checkpoint.run(); if (n==0||text.length()+n>65536)throw new IOException("CDS_EXPORT_PROFESSIONAL_SOURCE_NAME_REQUIRES_REVIEW"); text.append(block, 0, n); }}finally {Arrays.fill(block, '\0'); }return text.toString(); }
    private static void verifyType(CdsExportProfessionalContactMapper.Role role, String path, CdsExportRetainedFieldDto field)throws IOException {var type=CdsWireSchema.schemaModel().element("cds", "OmdCds").child("cds", "PatientRecord").child("cds", "Demographics").child("cds", role.wireName()); String namespace="cds"; if (!path.isEmpty()){if (!Set.of("FirstName[1]", "LastName[1]").contains(path))scope(); namespace="cds_dt"; type=type.child(namespace, path.substring(0, path.indexOf('['))); }if (type==null||!namespace.equals(field.namespace())||!("{"+type.getTypeNamespace()+"}"+type.getTypeName()).equals(field.valueType()))scope(); }
    private static void scope()throws IOException{throw new IOException("CDS_EXPORT_PROFESSIONAL_SOURCE_BINDING_SCOPE_INVALID"); }
}
