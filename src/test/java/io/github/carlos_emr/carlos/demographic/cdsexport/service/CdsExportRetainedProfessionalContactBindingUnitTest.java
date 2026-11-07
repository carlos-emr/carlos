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

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsDemographicRelationsDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit") @Tag("cds-export")
class CdsExportRetainedProfessionalContactBindingUnitTest {
    CdsExportDemographicProjectionEvidenceUnitTest fixture; final Map<String, String> source=new LinkedHashMap<>(); final List<CdsExportCapturedTextRowDto> originals=new ArrayList<>();
    @BeforeEach void setup(){fixture=new CdsExportDemographicProjectionEvidenceUnitTest(); fixture.setup(); source.clear(); originals.clear(); source.put("DateOfBirth[1]", "1980-01-02"); source.put("FamilyPhysician[1]/FirstName[1]", "Élodie😀 & Bea"); source.put("FamilyPhysician[1]/LastName[1]", "Fixture"); fixture.preparedRelations(new CdsDemographicRelationsDto.Person("Élodie😀 & Bea", "Fixture"), null); originals.add(CdsExportProfessionalContactMapperUnitTest.contact(9)); originals.add(CdsExportProfessionalContactMapperUnitTest.association(1, 9, "Family Doctor")); }
    static CdsNativeRowState state(CdsExportCapturedTextRowDto row){var columns=new ArrayList<CdsNativeRowDigest.Column>(); var cells=new ArrayList<CdsNativeRowState.Cell>(); for (var entry:row.fields().entrySet()){columns.add(new CdsNativeRowDigest.Column(entry.getKey(), Set.of("created", "updateDate").contains(entry.getKey())?"TIMESTAMP":"VARCHAR", 255, 0)); byte[] value=entry.getValue()==null?null:entry.getValue().getBytes(StandardCharsets.UTF_8); cells.add(value==null?new CdsNativeRowState.Cell(true, 0, null):new CdsNativeRowState.Cell(false, value.length, HexFormat.of().formatHex(CdsImportKeys.digest().digest(value)))); }return new CdsNativeRowState(row.table(), row.fields().get("id"), columns, cells); }
    CdsExportDemographicProjectionEvidenceUnitTest.Graph graph()throws Exception {
        var g=fixture.withSource(source); String address=g.index().nativeParents().values().iterator().next(), run=address.substring(0, 36), key=address.substring(37), unitId=CdsImportKeys.hash(run, key); var tables=new LinkedHashMap<>(g.index().tables()); var units=new LinkedHashMap<>(tables.get("cds_import_unit")); var unit=units.get(unitId); var values=new LinkedHashMap<>(unit.fields()); var baseline=CdsNativeBaselineCodec.decode(values.get("native_baseline"), values.get("target_references"), values.get("target_digest")); var states=new ArrayList<>(baseline.rows()); var refs=new ArrayList<>(g.index().references().get(address)); var checkedRefs=new ArrayList<>(g.checked().units().get(address));
        for (var row:originals){var state=state(row); states.add(state); var ref=new CdsExportRetainedIndexDto.Reference(row.table(), state.id(), state.digest()); refs.add(ref); checkedRefs.add(new CdsExportRetainedReceiptVerificationDto.CheckedReference(ref, state.digest(), CdsExportRetainedReceiptVerificationDto.Reason.RECEIPT_FIELD_CODEC_UNQUALIFIED, CdsNativeRowComparison.compare(state, state))); }
        var wire=refs.stream().map(ref-> Map.of("table", ref.table(), "id", ref.id(), "digest", ref.digest())).toList(); String encoded=new ObjectMapper().writeValueAsString(Map.of("protocol", 2, "refs", wire)), digest=HexFormat.of().formatHex(CdsImportKeys.digest().digest(encoded.getBytes(StandardCharsets.UTF_8))); values.put("target_references", encoded); values.put("target_digest", digest); values.put("native_baseline", CdsNativeBaselineCodec.encode(encoded, digest, states)); units.put(unitId, new CdsExportCapturedTextRowDto(unit.table(), unit.recordKey(), values)); tables.put("cds_import_unit", units); var references=new LinkedHashMap<>(g.index().references()); references.put(address, refs); var verified=new LinkedHashMap<>(g.checked().units()); verified.put(address, checkedRefs); var index=new CdsExportRetainedIndexDto(g.index().sourceManifestSha256(), g.index().metadataRows(), g.index().metadataBytes(), tables, g.index().appliedUnits(), references, g.index().nativeParents()); var checked=new CdsExportRetainedReceiptVerificationDto(g.checked().sourceManifestSha256(), verified, g.checked().unchangedUnits(), g.checked().reviewUnits()); return new CdsExportDemographicProjectionEvidenceUnitTest.Graph(index, checked, g.fields(), g.current(), g.witness());
    }
    private CdsExportRetainedSectionWriter.Values values(){return new CdsExportRetainedSectionWriter.Values(){public void requireActive(){}public Reader text(CdsExportRetainedFieldDto f){return new StringReader(f.logicalInline()==null?f.originalInline():f.logicalInline()); }public InputStream binary(CdsExportRetainedFieldDto f){throw new AssertionError(); }}; }
    private CdsExportRetainedProfessionalContactBinding.Result bind(CdsExportProfessionalContactMapper.Role role)throws Exception {var g=graph(); return new CdsExportRetainedProfessionalContactBinding().bind(27, g.index().nativeParents().values().iterator().next(), role, g.index(), g.checked(), g.fields(), g.current(), g.witness(), values(), ()-> {}); }
    @Test void shouldBindTheOriginalFamilyRelationship_byItsPatientRoleAndExactContactReference()throws Exception {var result=bind(CdsExportProfessionalContactMapper.Role.FAMILY); assertThat(result.projected()).isTrue(); assertThat(result.sourceFields()).hasSize(2); assertThat(result.association().id()).isEqualTo("1"); assertThat(result.contact().id()).isEqualTo("9"); }
    @Test void shouldDistinguishIdenticalNames_inDifferentOriginalPhysicianRoles()throws Exception {source.put("ReferredPhysician[1]/FirstName[1]", "Élodie😀 & Bea"); source.put("ReferredPhysician[1]/LastName[1]", "Fixture"); fixture.preparedRelations(new CdsDemographicRelationsDto.Person("Élodie😀 & Bea", "Fixture"), new CdsDemographicRelationsDto.Person("Élodie😀 & Bea", "Fixture")); originals.add(CdsExportProfessionalContactMapperUnitTest.contact(10)); originals.add(CdsExportProfessionalContactMapperUnitTest.association(2, 10, "Referring Doctor")); assertThat(bind(CdsExportProfessionalContactMapper.Role.FAMILY).contact().id()).isEqualTo("9"); assertThat(bind(CdsExportProfessionalContactMapper.Role.REFERRING).contact().id()).isEqualTo("10"); }
    @Test void shouldKeepAnUnprojectedSourceRelationship_sourceOnlyWhenTheMatchedChartHasNoOriginalCareReferences()throws Exception {originals.clear(); var result=bind(CdsExportProfessionalContactMapper.Role.FAMILY); assertThat(result.projected()).isFalse(); assertThat(result.sourceFields()).hasSize(2); }
    @Test void shouldRejectAnotherSourceOccurrence_insteadOfMatchingByTheSameNames() {source.put("FamilyPhysician[2]/FirstName[1]", "Élodie😀 & Bea"); assertThatThrownBy(()-> bind(CdsExportProfessionalContactMapper.Role.FAMILY)).hasMessage("CDS_EXPORT_PROFESSIONAL_SOURCE_BINDING_SCOPE_INVALID"); }
    @Test void shouldRejectAConflictingOriginalContactName_beforeSelectingAnyCurrentValue() {originals.set(0, CdsExportProfessionalContactMapperUnitTest.changed(originals.getFirst(), "lastName", "Another")); assertThatThrownBy(()-> bind(CdsExportProfessionalContactMapper.Role.FAMILY)).hasMessage("CDS_EXPORT_PROFESSIONAL_PREPARED_PROJECTION_NOT_PROVEN"); }
    @Test void shouldRejectAnotherPatient_inTheOriginalRelationshipWitness() {originals.set(1, CdsExportProfessionalContactMapperUnitTest.changed(originals.get(1), "demographicNo", "28")); assertThatThrownBy(()-> bind(CdsExportProfessionalContactMapper.Role.FAMILY)).hasMessage("CDS_EXPORT_PROFESSIONAL_ORIGINAL_RELATIONSHIP_REQUIRES_REVIEW"); }
    @Test void shouldRequireAnExactOriginalContactReference_withoutChoosingAnEqualName() {originals.set(1, CdsExportProfessionalContactMapperUnitTest.changed(originals.get(1), "contactId", "10")); assertThatThrownBy(()-> bind(CdsExportProfessionalContactMapper.Role.FAMILY)).hasMessage("CDS_EXPORT_PROFESSIONAL_ORIGINAL_CONTACT_REQUIRED"); }
    @Test void shouldRejectMultipleOriginalFamilyRelationships_beforeReplacingOneSingularWireElement() {originals.add(CdsExportProfessionalContactMapperUnitTest.association(2, 9, "Family Doctor")); assertThatThrownBy(()-> bind(CdsExportProfessionalContactMapper.Role.FAMILY)).hasMessage("CDS_EXPORT_PROFESSIONAL_ORIGINAL_RELATIONSHIP_REQUIRES_REVIEW"); }
    @Test void shouldVerifyEffectiveSourceNames_againstTheSealedPreparation() {source.put("FamilyPhysician[1]/FirstName[1]", "Different"); assertThatThrownBy(()-> bind(CdsExportProfessionalContactMapper.Role.FAMILY)).hasMessage("CDS_EXPORT_PROFESSIONAL_SOURCE_PREPARATION_DIFFERS"); }
    @Test void shouldRefuseUnsupportedSourceLengths_withoutTruncatingTheOriginalName() {source.put("FamilyPhysician[1]/FirstName[1]", "😀".repeat(51)); assertThatThrownBy(()-> bind(CdsExportProfessionalContactMapper.Role.FAMILY)).hasMessage("CDS_EXPORT_PROFESSIONAL_SOURCE_NAME_REQUIRES_REVIEW"); }
    @Test void shouldPropagateCancellation_beforeReadingAnyRelationshipField()throws Exception {var g=graph(); assertThatThrownBy(()-> new CdsExportRetainedProfessionalContactBinding().bind(27, g.index().nativeParents().values().iterator().next(), CdsExportProfessionalContactMapper.Role.FAMILY, g.index(), g.checked(), g.fields(), g.current(), g.witness(), values(), ()-> {throw new IllegalStateException("CANCELLED_CARE_BINDING"); })).hasMessage("CANCELLED_CARE_BINDING"); }
}
