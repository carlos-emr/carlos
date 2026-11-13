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
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowComparison;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit") @Tag("cds-export")
class CdsExportRetainedPharmacyReconcilerUnitTest {
    CdsExportRetainedPharmacyBindingUnitTest fixture; final List<CdsExportCapturedTextRowDto> rows = new ArrayList<>();
    @BeforeEach void setup() { fixture = new CdsExportRetainedPharmacyBindingUnitTest(); fixture.setup(); rows.clear(); rows.addAll(fixture.fixture.originals); }
    private static String id(CdsExportCapturedTextRowDto row) { return row.fields().get(row.table().equals("pharmacyInfo") ? "recordID" : "id"); }
    private CdsExportRetainedPharmacyReconciler.NativeRows nativeRows(String digest) {
        var witnesses = rows.stream().map(row -> { var state = CdsExportRetainedProfessionalContactBindingUnitTest.state(row); return new CdsExportNativeRowReceiptDto(row.table(), state.id(), row.recordKey(), state.digest(), state); }).toList();
        return new CdsExportRetainedPharmacyReconciler.NativeRows(digest, rows.stream().filter(row -> row.table().equals("demographicPharmacy")).toList(), rows.stream().filter(row -> row.table().equals("pharmacyInfo")).toList(), witnesses);
    }
    private CdsExportRetainedReceiptVerificationDto checked(CdsExportDemographicProjectionEvidenceUnitTest.Graph graph) {
        String address = graph.index().nativeParents().values().iterator().next(); var units = new LinkedHashMap<>(graph.checked().units()); var refs = new ArrayList<CdsExportRetainedReceiptVerificationDto.CheckedReference>();
        for (var ref : units.get(address)) {
            if (!Set.of("pharmacyInfo", "demographicPharmacy").contains(ref.expected().table())) { refs.add(ref); continue; }
            var found = rows.stream().filter(row -> row.table().equals(ref.expected().table()) && ref.expected().id().equals(id(row))).toList();
            if (found.isEmpty()) refs.add(new CdsExportRetainedReceiptVerificationDto.CheckedReference(ref.expected(), null, CdsExportRetainedReceiptVerificationDto.Reason.ROW_NOT_IN_CAPTURE));
            else {
                var original = fixture.fixture.originals.stream().filter(row -> row.table().equals(ref.expected().table()) && ref.expected().id().equals(id(row))).findFirst().orElseThrow();
                var state = CdsExportRetainedProfessionalContactBindingUnitTest.state(found.getFirst());
                refs.add(new CdsExportRetainedReceiptVerificationDto.CheckedReference(ref.expected(), state.digest(), CdsExportRetainedReceiptVerificationDto.Reason.RECEIPT_FIELD_CODEC_UNQUALIFIED, CdsNativeRowComparison.compare(CdsExportRetainedProfessionalContactBindingUnitTest.state(original), state)));
            }
        }
        units.put(address, refs); return new CdsExportRetainedReceiptVerificationDto(graph.checked().sourceManifestSha256(), units, graph.checked().unchangedUnits(), graph.checked().reviewUnits());
    }
    private CdsExportRetainedPharmacyReconciler.Result reconcile() throws Exception {
        var graph = fixture.fixture.graph(); return reconcile(graph, nativeRows(graph.index().sourceManifestSha256()), checked(graph), () -> { });
    }
    private CdsExportRetainedPharmacyReconciler.Result reconcile(CdsExportDemographicProjectionEvidenceUnitTest.Graph graph,
            CdsExportRetainedPharmacyReconciler.NativeRows nativeRows, CdsExportRetainedReceiptVerificationDto checked, Runnable checkpoint) throws Exception {
        return new CdsExportRetainedPharmacyReconciler().reconcile(27, graph.index().nativeParents().values().iterator().next(), graph.index(), checked, graph.fields(), graph.current(), graph.witness(), nativeRows, 100, 1L << 20, CdsExportRetainedPharmacyBindingUnitTest.values(), checkpoint);
    }
    private void change(String table, String column, String value) {
        for (int n = 0; n < rows.size(); n++) if (rows.get(n).table().equals(table)) { rows.set(n, CdsExportRetainedPharmacyBindingUnitTest.changed(rows.get(n), column, value)); return; } throw new AssertionError();
    }
    @Test void shouldRetainAllStructuredSourceDetails_whenTheOriginalNativeProjectionIsUnchanged() throws Exception {
        var result = reconcile(); assertThat(result.representable()).isTrue(); assertThat(result.changes()).isEmpty(); assertThat(result.fields()).hasSize(22); assertThat(result.sourceFields()).hasSize(16);
        assertThat(result.fields().get(new CdsExportNativeFieldKeyDto("pharmacyInfo", rows.getFirst().recordKey(), "address")).rule()).isEqualTo("VERIFIED_UNCHANGED_RETAINED_PHARMACY_GROUP");
    }
    @Test void shouldReplaceOnlyTheEditedName_whileKeepingAddressLinesPhoneExtensionAndFaxComponents() throws Exception {
        completeSource();
        change("pharmacyInfo", "name", "Current & Pharmacy"); var result = reconcile(); assertThat(result.representable()).isTrue(); assertThat(result.changes()).hasSize(1);
        assertThat(result.changes().getFirst().relativePath()).isEqualTo("PreferredPharmacy[1]/Name[1]"); assertThat(result.changes().getFirst().expectedPriorFields()).hasSize(1);
        var graph = fixture.fixture.graph(); var prepared = new CdsExportRetainedSectionWriter().prepareChanges(graph.fields(), CdsExportRetainedPharmacyBindingUnitTest.values(), result.changes(), () -> { });
        assertThat(prepared.replacedFields()).containsExactlyElementsOf(result.changes().getFirst().expectedPriorFields()); assertThat(prepared.retainedFields()).hasSize(24);
        var output = new ByteArrayOutputStream(); String key = "f".repeat(64), version = "2026.08.0-test"; var selected = Set.of(CdsCategory.DEMOGRAPHICS);
        new CdsExportXmlComposer().compose(output, graph.index().sourceManifestSha256(), key, version, CdsLogicalProfile.ONTARIO_MD_5_2, selected,
                List.of(new CdsExportXmlComposer.Section(new CdsExportIdentityDto(key, CdsCategory.DEMOGRAPHICS, key), prepared.content())).iterator(), ignored -> { }, 1L << 20, () -> { });
        new CdsExportXmlValidator().validate(new ByteArrayInputStream(output.toByteArray()), "old27", selected, version, CdsLogicalProfile.ONTARIO_MD_5_2, CdsExportXmlLimitsDto.defaults(), ignored -> { }, () -> { });
        var parser = javax.xml.parsers.DocumentBuilderFactory.newDefaultInstance(); parser.setNamespaceAware(true);
        var document = parser.newDocumentBuilder().parse(new ByteArrayInputStream(output.toByteArray()));
        var pharmacy = (org.w3c.dom.Element) document.getElementsByTagNameNS("cds", "PreferredPharmacy").item(0);
        assertThat(pharmacy.getElementsByTagNameNS("cds", "Name").item(0).getTextContent()).isEqualTo("Current & Pharmacy");
        assertThat(pharmacy.getElementsByTagNameNS("cds_dt", "Line2").item(0).getTextContent()).isEqualTo("Unit 4");
        assertThat(pharmacy.getElementsByTagNameNS("cds_dt", "Line3").item(0).getTextContent()).isEqualTo("West Entrance");
        assertThat(pharmacy.getElementsByTagNameNS("cds_dt", "extension").item(0).getTextContent()).isEqualTo("0012");
        assertThat(pharmacy.getElementsByTagNameNS("cds_dt", "exchange").item(0).getTextContent()).isEqualTo("555");
    }
    @Test void shouldDeleteAnExplicitlyClearedOptionalGroup_withoutWritingAnEmptyPhoneNumber() throws Exception {
        change("pharmacyInfo", "phone1", null); var result = reconcile(); assertThat(result.representable()).isTrue(); assertThat(result.changes()).hasSize(1); assertThat(result.changes().getFirst().content()).isNull(); assertThat(result.changes().getFirst().expectedPriorFields()).hasSize(3);
    }
    @Test void shouldKeepAnUnprojectedSourcePharmacy_whenNoNewNativePreferenceConflicts() throws Exception {
        fixture.fixture.originals.clear(); rows.clear(); var result = reconcile(); assertThat(result.representable()).isTrue(); assertThat(result.changes()).isEmpty(); assertThat(result.sourceFields()).hasSize(16);
    }
    @Test void shouldRequireReview_whenNewNativePreferenceWouldOverwriteSourceOnlyDetails() throws Exception {
        fixture.fixture.originals.clear(); var result = reconcile(); assertThat(result.representable()).isFalse(); assertThat(result.blockers()).contains("UNPROJECTED_SOURCE_PREFERRED_PHARMACY_CONFLICT"); assertThat(result.proposedChanges()).isEmpty();
    }
    @Test void shouldRequireReviewForAMissingOriginalAssociation_withoutTreatingItAsSuccessfulEmptyOutput() throws Exception {
        rows.clear(); var result = reconcile(); assertThat(result.representable()).isFalse(); assertThat(result.blockers()).contains("ORIGINAL_PREFERRED_PHARMACY_MISSING_OR_REPLACED");
    }
    @Test void shouldApplyAValidNameEdit_whileTheNotesTravelInTheNativeRecord() throws Exception {
        // Notes have no PreferredPharmacy element: they are carried in the native demographic record and no longer withhold the name edit.
        change("pharmacyInfo", "name", "Current"); change("pharmacyInfo", "notes", "Additional clinical context"); var result = reconcile(); assertThat(result.representable()).isTrue(); assertThat(result.proposedChanges()).hasSize(1);
    }
    @Test void shouldRequireReviewForInactiveAndChangedPreferences_beforeOfferingAnySourceReplacement() throws Exception {
        for (String column : List.of("status", "preferredOrder")) { setup(); change("demographicPharmacy", column, "0"); assertThat(reconcile().representable()).isFalse(); }
        // "No Consent" set on the imported link in CARLOS is carried in the native record and no longer holds the chart.
        setup(); change("demographicPharmacy", "consentToContact", "0"); assertThat(reconcile().representable()).isTrue();
    }
    @Test void shouldRequireReviewForDuplicateFirstPreferences_withoutChoosingTheOriginalByConvenience() throws Exception {
        rows.add(CdsExportPharmacyMapperUnitTest.link(2, 9)); var result = reconcile(); assertThat(result.representable()).isFalse(); assertThat(result.proposedChanges()).isEmpty();
    }
    @Test void shouldRefuseAStaleReceiptComparison_evenWhenCurrentRowsHaveValidIndependentWitnesses() throws Exception {
        change("pharmacyInfo", "name", "Current"); var graph = fixture.fixture.graph(); assertThatThrownBy(() -> reconcile(graph, nativeRows(graph.index().sourceManifestSha256()), graph.checked(), () -> { })).hasMessage("CDS_EXPORT_PHARMACY_CURRENT_WITNESS_SCOPE_INVALID");
    }
    @Test void shouldRefuseAnotherCapture_beforeCombiningItsPharmacyRowsWithRetainedSource() throws Exception {
        var graph = fixture.fixture.graph(); assertThatThrownBy(() -> reconcile(graph, nativeRows("f".repeat(64)), checked(graph), () -> { })).hasMessage("CDS_EXPORT_PHARMACY_NATIVE_GENERATION_INVALID");
    }
    @Test void shouldRequireEveryCurrentWitness_withThePharmacyRecordIdColumnExplicitlyBound() throws Exception {
        var graph = fixture.fixture.graph(); var original = nativeRows(graph.index().sourceManifestSha256()); var missing = new CdsExportRetainedPharmacyReconciler.NativeRows(original.sourceManifestSha256(), original.links(), original.pharmacies(), List.of());
        assertThatThrownBy(() -> reconcile(graph, missing, checked(graph), () -> { })).hasMessage("CDS_EXPORT_CAPTURED_TEXT_WITNESS_SCOPE_INVALID");
    }
    @Test void shouldPropagateCancellation_beforeAnyReconciledPharmacyCanEscape() throws Exception {
        var graph = fixture.fixture.graph(); assertThatThrownBy(() -> reconcile(graph, nativeRows(graph.index().sourceManifestSha256()), checked(graph), () -> { throw new IllegalStateException("CANCELLED_PHARMACY"); })).hasMessage("CANCELLED_PHARMACY");
    }
    @Test void shouldAddARepresentableNativePreference_whenNoOriginalSourceOrProjectionExists() throws Exception {
        fixture.fixture.source.keySet().removeIf(path -> path.startsWith("PreferredPharmacy[")); fixture.fixture.fixture.preparedPharmacy(null); fixture.fixture.originals.clear();
        rows.clear(); rows.add(CdsExportPharmacyMapperUnitTest.pharmacy(9)); rows.add(CdsExportPharmacyMapperUnitTest.link(1, 9));
        var result = reconcile(); assertThat(result.representable()).isTrue(); assertThat(result.sourceFields()).isEmpty(); assertThat(result.changes()).hasSize(1);
        assertThat(result.changes().getFirst().relativePath()).isEqualTo("PreferredPharmacy[1]"); assertThat(result.changes().getFirst().expectedPriorFields()).isEmpty();
    }
    @Test void shouldWithholdNameChanges_whenDuplicatePreferencesPreventOneCurrentPharmacy() throws Exception {
        change("pharmacyInfo", "name", "Current"); rows.add(CdsExportPharmacyMapperUnitTest.link(2, 9)); var result = reconcile(); assertThat(result.representable()).isFalse(); assertThat(result.proposedChanges()).isEmpty();
    }
    @Test void shouldChangeOnlyTheCity_whilePreservingAllOriginalStreetLinesAndResidentialAddressType() throws Exception {
        completeSource(); fixture.source("Address[1]/@addressType", "R"); change("pharmacyInfo", "city", "Toronto"); var result = reconcile();
        assertThat(result.representable()).isTrue(); assertThat(result.changes()).hasSize(1); assertThat(result.changes().getFirst().relativePath()).isEqualTo("PreferredPharmacy[1]/Address[1]/Structured[1]/City[1]");
        var pharmacy = renderedPharmacy(result); assertThat(pharmacy.getElementsByTagNameNS("cds_dt", "City").item(0).getTextContent()).isEqualTo("Toronto");
        assertThat(pharmacy.getElementsByTagNameNS("cds_dt", "Line2").item(0).getTextContent()).isEqualTo("Unit 4");
        assertThat(((org.w3c.dom.Element) pharmacy.getElementsByTagNameNS("cds", "Address").item(0)).getAttribute("addressType")).isEqualTo("R");
    }
    @Test void shouldReplaceAnExplicitlyEditedStreet_withoutKeepingObsoleteSecondAndThirdLines() throws Exception {
        completeSource(); change("pharmacyInfo", "address", "99 New Street"); var result = reconcile(); assertThat(result.representable()).isTrue(); assertThat(result.changes()).hasSize(3);
        var pharmacy = renderedPharmacy(result); assertThat(pharmacy.getElementsByTagNameNS("cds_dt", "Line1").item(0).getTextContent()).isEqualTo("99 New Street");
        assertThat(pharmacy.getElementsByTagNameNS("cds_dt", "Line2").getLength()).isZero(); assertThat(pharmacy.getElementsByTagNameNS("cds_dt", "Line3").getLength()).isZero();
        assertThat(pharmacy.getElementsByTagNameNS("cds_dt", "City").item(0).getTextContent()).isEqualTo("Ottawa");
    }
    @Test void shouldReconcileJurisdictionAndPostalChoiceTogether_whenTheCurrentProvinceAndPostalValueChange() throws Exception {
        completeSource(); change("pharmacyInfo", "province", "US-NY"); change("pharmacyInfo", "postalCode", "12345"); var result = reconcile(); assertThat(result.representable()).isTrue();
        var pharmacy = renderedPharmacy(result); assertThat(pharmacy.getElementsByTagNameNS("cds_dt", "CountrySubdivisionCode").item(0).getTextContent()).isEqualTo("US-NY");
        assertThat(pharmacy.getElementsByTagNameNS("cds_dt", "ZipCode").item(0).getTextContent()).isEqualTo("12345"); assertThat(pharmacy.getElementsByTagNameNS("cds_dt", "PostalCode").getLength()).isZero();
    }
    @Test void shouldCarryAnUnchangedPostalValue_whenTheNewJurisdictionCannotHoldIt() throws Exception {
        // A Canadian postal code under a US state fits neither arm: it travels in the native record and the edits still apply.
        change("pharmacyInfo", "name", "Current"); change("pharmacyInfo", "province", "US-NY"); var result = reconcile(); assertThat(result.representable()).isTrue();
        assertThat(CdsExportDemographicSupportingMapper.carries(result.fields().get(new CdsExportNativeFieldKeyDto("pharmacyInfo", CdsExportPharmacyMapperUnitTest.pharmacy(9).recordKey(), "postalCode")))).isTrue();
    }
    @Test void shouldRetainSourceOnlyLongCities_andRequireReviewBeforeANewNativeCityReplacesThem() throws Exception {
        fixture.source("Address[1]/Structured[1]/City[1]", "C".repeat(70)); var old = fixture.preparation;
        fixture.fixture.fixture.preparedPharmacy(new io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsDemographicRelationsDto.Pharmacy(old.name(), old.address(), null, old.province(), old.postal(), old.phone(), old.fax(), old.email()));
        fixture.fixture.originals.set(0, CdsExportRetainedPharmacyBindingUnitTest.changed(fixture.fixture.originals.getFirst(), "city", null)); change("pharmacyInfo", "city", null);
        assertThat(reconcile().representable()).isTrue(); change("pharmacyInfo", "city", "Toronto"); var result = reconcile(); assertThat(result.representable()).isFalse(); assertThat(result.proposedChanges()).isEmpty();
    }
    private org.w3c.dom.Element renderedPharmacy(CdsExportRetainedPharmacyReconciler.Result result) throws Exception {
        var graph = fixture.fixture.graph(); var prepared = new CdsExportRetainedSectionWriter().prepareChanges(graph.fields(), CdsExportRetainedPharmacyBindingUnitTest.values(), result.changes(), () -> { });
        var output = new ByteArrayOutputStream(); String key = "f".repeat(64), version = "2026.08.0-test"; var selected = Set.of(CdsCategory.DEMOGRAPHICS);
        new CdsExportXmlComposer().compose(output, graph.index().sourceManifestSha256(), key, version, CdsLogicalProfile.ONTARIO_MD_5_2, selected,
                List.of(new CdsExportXmlComposer.Section(new CdsExportIdentityDto(key, CdsCategory.DEMOGRAPHICS, key), prepared.content())).iterator(), ignored -> { }, 1L << 20, () -> { });
        new CdsExportXmlValidator().validate(new ByteArrayInputStream(output.toByteArray()), "old27", selected, version, CdsLogicalProfile.ONTARIO_MD_5_2, CdsExportXmlLimitsDto.defaults(), ignored -> { }, () -> { });
        var parser = javax.xml.parsers.DocumentBuilderFactory.newDefaultInstance(); parser.setNamespaceAware(true);
        return (org.w3c.dom.Element) parser.newDocumentBuilder().parse(new ByteArrayInputStream(output.toByteArray())).getElementsByTagNameNS("cds", "PreferredPharmacy").item(0);
    }
    private void completeSource() {
        fixture.fixture.source.putAll(Map.of("Names[1]/LegalName[1]/FirstName[1]/Part[1]", "Ada", "Names[1]/LegalName[1]/FirstName[1]/PartType[1]", "GIV",
                "Names[1]/LegalName[1]/LastName[1]/Part[1]", "Family", "Names[1]/LegalName[1]/LastName[1]/PartType[1]", "FAMC",
                "Names[1]/LegalName[1]/@namePurpose", "L", "Gender[1]", "F", "UniqueVendorIdSequence[1]", "old27", "PersonStatusCode[1]/PersonStatusAsEnum[1]", "A"));
    }
}
