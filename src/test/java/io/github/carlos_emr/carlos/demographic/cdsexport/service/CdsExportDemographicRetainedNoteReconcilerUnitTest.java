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
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.commn.model.DemographicCust;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedReceiptVerificationDto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.*;
import org.w3c.dom.Document;

@Tag("unit") @Tag("cds-export")
class CdsExportDemographicRetainedNoteReconcilerUnitTest {
    private CdsExportDemographicProjectionEvidenceUnitTest fixture;
    private final Map<String, String> source=new LinkedHashMap<>();
    private final List<CdsExportCapturedTextRowDto> old=new ArrayList<>(), current=new ArrayList<>();
    private boolean active;
    private Set<CdsCategory> selected;
    @BeforeEach void setup(){
        fixture=new CdsExportDemographicProjectionEvidenceUnitTest(); fixture.setup(); fixture.preparedNote("Original 😀 note", false); source.clear(); old.clear(); current.clear(); active=true; selected=Set.of(CdsCategory.DEMOGRAPHICS);
        source.put("Names[1]/LegalName[1]/@namePurpose", "L"); source.put("Names[1]/LegalName[1]/FirstName[1]/Part[1]", "Ada"); source.put("Names[1]/LegalName[1]/FirstName[1]/PartType[1]", "GIV"); source.put("Names[1]/LegalName[1]/LastName[1]/Part[1]", "Family"); source.put("Names[1]/LegalName[1]/LastName[1]/PartType[1]", "FAMC"); source.put("DateOfBirth[1]", "1980-01-02"); source.put("Gender[1]", "F"); source.put("UniqueVendorIdSequence[1]", "27"); source.put("PersonStatusCode[1]/PersonStatusAsEnum[1]", "A"); source.put("NoteAboutPatient[1]", "Original 😀 note");
        old.add(cust(DemographicCust.wrapPatientNotes("Original 😀 note"), true)); current.add(cust(DemographicCust.wrapPatientNotes("Original 😀 note"), false));
    }
    @Test void unchangedSelectedNoteFieldsRetainTheirSourceWithoutQualifyingAlertsOrCareTeamCarriers()throws Exception{
        var result=reconcile(); assertThat(result.noteRepresentable()).isTrue(); assertThat(result.alertsOmitted()).isTrue(); assertThat(result.fields()).hasSize(2); assertThat(result.deferredNativeFields().stream().map(CdsExportNativeFieldKeyDto::column)).containsExactlyInAnyOrder("cust1", "cust2", "cust4"); assertThat(result.changes()).isEmpty(); assertThat(text(render(result), "NoteAboutPatient")).containsExactly("Original 😀 note"); assertThat(result.toString()).doesNotContain("Original", result.unitAddress());
    }
    @Test void anEditedPlainNoteUsesItsCurrentExactTextWithoutTreatingEmbeddedFramesAsMarkup()throws Exception{
        change("content", DemographicCust.wrapPatientNotes("Current </unotes> 😀\nSecond line")); var result=reconcile(); assertThat(result.changes()).hasSize(1); assertThat(text(render(result), "NoteAboutPatient")).containsExactly("Current </unotes> 😀\nSecond line");
    }
    @Test void nullClearsAndEmptyNotesRemainDifferent()throws Exception{
        change("content", null); assertThat(text(render(reconcile()), "NoteAboutPatient")).isEmpty(); change("content", DemographicCust.wrapPatientNotes("")); assertThat(text(render(reconcile()), "NoteAboutPatient")).containsExactly("");
    }
    @Test void independentCareTeamChangesStayDeferredAndDoNotRewriteAnUnchangedNote()throws Exception{
        change("cust1", "Unresolved care team reference"); var result=reconcile(); assertThat(result.noteRepresentable()).isTrue(); assertThat(result.changes()).isEmpty(); assertThat(result.deferredNativeFields().stream().map(CdsExportNativeFieldKeyDto::column)).contains("cust1");
    }
    @Test void aSourceOnlyFullNoteCannotBeOverwrittenByALaterNativePlaceholderEdit()throws Exception{
        fixture.preparedNote("Native storage placeholder", true); source.put("NoteAboutPatient[1]", "Complete source note"); old.set(0, cust(DemographicCust.wrapPatientNotes("Native storage placeholder"), true)); current.set(0, cust(DemographicCust.wrapPatientNotes("Native storage placeholder"), false)); assertThat(reconcile().changes()).isEmpty(); change("content", DemographicCust.wrapPatientNotes("Edited placeholder")); var result=reconcile(); assertThat(result.noteRepresentable()).isFalse(); assertThatThrownBy(result::changes).hasMessage("CDS_EXPORT_DEMOGRAPHIC_RETAINED_NOTE_REQUIRES_REVIEW");
    }
    @Test void aLaterAddedNativeNoteCanBeAppendedWhenNoSourceOrPreparedNoteExisted()throws Exception{
        fixture.preparedNote(null, false); source.remove("NoteAboutPatient[1]"); old.clear(); change("content", DemographicCust.wrapPatientNotes("Added native note")); var result=reconcile(); assertThat(result.changes().getFirst().expectedPriorFields()).isEmpty(); assertThat(text(render(result), "NoteAboutPatient")).containsExactly("Added native note");
    }
    @Test void aMatchedExistingNoteWithoutAnAppliedReceiptCannotReplayPreparedSource()throws Exception{
        old.clear(); assertThat(reconcile().noteRepresentable()).isFalse();
    }
    @Test void missingAndUnprojectedSourceNotesRequireReview()throws Exception{
        source.remove("NoteAboutPatient[1]"); assertThat(reconcile().noteRepresentable()).isFalse(); setup(); fixture.preparedNote(null, false); old.set(0, cust(null, true)); current.set(0, cust(null, false)); assertThat(reconcile().noteRepresentable()).isFalse();
    }
    @Test void ambiguousNativeFramingCannotFallBackToOriginalText()throws Exception{
        change("content", "prefix <unotes>ambiguous</unotes> suffix"); assertThat(reconcile().noteRepresentable()).isFalse();
    }
    @Test void deletingAnImportedCustRowCannotResurrectItsOriginalNote()throws Exception{
        current.clear(); var result=reconcile(); assertThat(result.noteRepresentable()).isFalse(); assertThat(result.blockers()).containsExactly("CDS_EXPORT_DEMOGRAPHIC_NOTE_ROW_DELETION_REQUIRES_REVIEW");
    }
    @Test void selectedAlertsRemainExplicitlyDeferredUnderAFullCustWitness()throws Exception{
        selected=Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.ALERTS_AND_SPECIAL_NEEDS); current.set(0, cust(DemographicCust.wrapPatientNotes("Original 😀 note"), true)); var result=reconcile(); assertThat(result.noteRepresentable()).isTrue(); assertThat(result.alertsOmitted()).isFalse(); assertThat(result.deferredNativeFields().stream().map(CdsExportNativeFieldKeyDto::column)).contains("cust3");
    }
    @Test void expiredValuesAndSubstitutedRowsFailClosed()throws Exception{
        active=false; assertThatThrownBy(this::reconcile).hasMessage("EXPIRED_VALUES"); active=true; var graph=graph(); var substituted=changed(current.getFirst(), "content", "Substituted"); assertThatThrownBy(()-> invoke(graph, List.of(substituted), graph.witnesses())).hasMessage("CDS_EXPORT_DEMOGRAPHIC_RETAINED_NOTE_SCOPE_INVALID");
    }
    private void change(String column, String value){current.set(0, changed(current.getFirst(), column, value)); }
    private static CdsExportCapturedTextRowDto changed(CdsExportCapturedTextRowDto row, String column, String value){var fields=new LinkedHashMap<>(row.fields()); fields.put(column, value); return new CdsExportCapturedTextRowDto(row.table(), row.recordKey(), fields); }
    private static CdsExportCapturedTextRowDto cust(String content, boolean full){var fields=new LinkedHashMap<String, String>(); (full?Plan.DEMOGRAPHIC_CUST:Plan.DEMOGRAPHIC_CUST_DEMOGRAPHICS).columns().forEach(column-> fields.put(column, null)); fields.put("demographic_no", "27"); fields.put("content", content); if (full)fields.put("cust3", "Unselected alert body"); return new CdsExportCapturedTextRowDto("demographiccust", CdsExportPatientTableSourceReader.key(27), fields); }
    private static CdsNativeRowState state(CdsExportCapturedTextRowDto row){var columns=new ArrayList<CdsNativeRowDigest.Column>(); var cells=new ArrayList<CdsNativeRowState.Cell>(); for (var entry:row.fields().entrySet()){columns.add(new CdsNativeRowDigest.Column(entry.getKey(), "VARCHAR", 255, 0)); String value=entry.getValue(); cells.add(value==null?new CdsNativeRowState.Cell(true, 0, null):new CdsNativeRowState.Cell(false, value.getBytes(StandardCharsets.UTF_8).length, sha(value))); }return new CdsNativeRowState(row.table(), row.fields().get("demographic_no"), columns, cells); }
    private static CdsExportNativeRowReceiptDto witness(CdsExportCapturedTextRowDto row){var state=state(row); return new CdsExportNativeRowReceiptDto(row.table(), state.id(), row.recordKey(), state.digest(), state, !row.fields().containsKey("cust3")); }
    private static String sha(String value){return HexFormat.of().formatHex(CdsImportKeys.digest().digest(value.getBytes(StandardCharsets.UTF_8))); }
    private record Graph(CdsExportDemographicProjectionEvidenceUnitTest.Graph base, String address, List<CdsExportNativeRowReceiptDto> witnesses){ }
    private Graph graph()throws Exception{
        var original=fixture.withSource(source); String address=original.index().nativeParents().values().iterator().next(), run=address.substring(0, 36), key=address.substring(37), unitId=CdsImportKeys.hash(run, key); var unit=original.index().tables().get("cds_import_unit").get(unitId); var raw=new LinkedHashMap<>(unit.fields()); var baseline=CdsNativeBaselineCodec.decode(raw.get("native_baseline"), raw.get("target_references"), raw.get("target_digest")); var states=new ArrayList<>(baseline.rows()); var refs=new ArrayList<>(original.index().references().get(address)); var checkedRefs=new ArrayList<>(original.checked().units().get(address));
        for (var row:old){var oldState=state(row); states.add(oldState); var reference=new CdsExportRetainedIndexDto.Reference(row.table(), oldState.id(), oldState.digest()); refs.add(reference); var actual=current.stream().filter(candidate-> candidate.fields().get("demographic_no").equals(oldState.id())).findFirst().orElse(null); if (actual==null)checkedRefs.add(new CheckedReference(reference, null, Reason.ROW_NOT_IN_CAPTURE)); else {var currentState=state(actual); if (!actual.fields().containsKey("cust3")){var partial=CdsExportPartialRowComparator.compare(oldState, currentState); checkedRefs.add(new CheckedReference(reference, currentState.digest(), Reason.RECEIPT_PARTIAL_PROJECTION, null, partial)); }else {var difference=CdsNativeRowComparison.compare(oldState, currentState); checkedRefs.add(new CheckedReference(reference, currentState.digest(), oldState.digest().equals(currentState.digest())?Reason.UNCHANGED_ROW:Reason.CHANGED_ROW, difference)); }}}
        String references=new ObjectMapper().writeValueAsString(Map.of("protocol", 2, "refs", refs)), digest=sha(references); raw.put("target_references", references); raw.put("target_digest", digest); raw.put("native_baseline", CdsNativeBaselineCodec.encode(references, digest, states)); var tables=new LinkedHashMap<String, Map<String, CdsExportCapturedTextRowDto>>(); original.index().tables().forEach((table, rows)-> tables.put(table, new LinkedHashMap<>(rows))); tables.get("cds_import_unit").put(unitId, new CdsExportCapturedTextRowDto("cds_import_unit", unitId, raw)); var allRefs=new LinkedHashMap<>(original.index().references()); allRefs.put(address, refs); var allChecked=new LinkedHashMap<>(original.checked().units()); allChecked.put(address, checkedRefs);
        var index=new CdsExportRetainedIndexDto(original.index().sourceManifestSha256(), original.index().metadataRows(), original.index().metadataBytes(), tables, original.index().appliedUnits(), allRefs, original.index().nativeParents()); var checked=new CdsExportRetainedReceiptVerificationDto(original.checked().sourceManifestSha256(), allChecked, original.checked().unchangedUnits(), original.checked().reviewUnits()); var base=new CdsExportDemographicProjectionEvidenceUnitTest.Graph(index, checked, original.fields(), original.current(), original.witness()); return new Graph(base, address, current.stream().map(CdsExportDemographicRetainedNoteReconcilerUnitTest::witness).toList());
    }
    private CdsExportDemographicRetainedNoteReconciler.Result reconcile()throws Exception{return invoke(graph(), current, null); }
    private CdsExportDemographicRetainedNoteReconciler.Result invoke(Graph graph, List<CdsExportCapturedTextRowDto> rows, List<CdsExportNativeRowReceiptDto> witnesses)throws Exception{var base=graph.base(); var proof=witnesses==null?graph.witnesses():witnesses; return new CdsExportDemographicRetainedNoteReconciler().reconcile(27, selected, graph.address(), base.index(), base.checked(), base.fields(), base.current(), base.witness(), rows.isEmpty()?null:rows.getFirst(), proof.isEmpty()?null:proof.getFirst(), values(), ()-> {}); }
    private CdsExportRetainedSectionWriter.Values values(){return new CdsExportRetainedSectionWriter.Values(){public void requireActive()throws IOException{if (!active)throw new IOException("EXPIRED_VALUES"); }public Reader text(CdsExportRetainedFieldDto field)throws IOException{requireActive(); return new StringReader(field.logicalInline()==null?field.originalInline():field.logicalInline()); }public InputStream binary(CdsExportRetainedFieldDto field){throw new AssertionError("No binary phone"); }}; }
    private Document render(CdsExportDemographicRetainedNoteReconciler.Result result)throws Exception{
        var graph=graph(); var plan=new CdsExportRetainedSectionWriter().prepareChanges(graph.base().fields(), values(), result.changes(), ()-> {}); var bytes=new ByteArrayOutputStream(); var selected=Set.of(CdsCategory.DEMOGRAPHICS); String patient="a".repeat(64), version="2026.08.0-test"; var section=new CdsExportXmlComposer.Section(new CdsExportIdentityDto(patient, CdsCategory.DEMOGRAPHICS, "b".repeat(64)), plan.content()); new CdsExportXmlComposer().compose(bytes, "c".repeat(64), patient, version, CdsLogicalProfile.ONTARIO_MD_5_2, selected, List.of(section).iterator(), ignored-> {}, 1L<<20, ()-> {}); new CdsExportXmlValidator().validate(new ByteArrayInputStream(bytes.toByteArray()), "27", selected, version, CdsLogicalProfile.ONTARIO_MD_5_2, CdsExportXmlLimitsDto.defaults(), ignored-> {}, ()-> {}); var factory=DocumentBuilderFactory.newDefaultInstance(); factory.setNamespaceAware(true); return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes.toByteArray()));
    }
    private static List<String> text(Document document, String local){var nodes=document.getElementsByTagNameNS("*", local); var values=new ArrayList<String>(); for (int n=0; n<nodes.getLength(); n++)values.add(nodes.item(n).getTextContent()); return values; }
}
