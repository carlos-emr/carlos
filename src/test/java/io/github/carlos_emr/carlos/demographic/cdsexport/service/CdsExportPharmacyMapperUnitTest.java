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
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("unit")
@Tag("cds-export")
class CdsExportPharmacyMapperUnitTest {
    @TempDir Path directory;
    private static final String HASH = "a".repeat(64);
    private final CdsExportPharmacyMapper mapper = new CdsExportPharmacyMapper();

    /** The stored value travels in the native demographic NewCategory record. */
    private static void assertCarried(
            CdsExportPharmacyMapper.Result result, CdsExportCapturedTextRowDto row, String column) {
        var decision =
                result.fields().get(new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), column));
        assertThat(CdsExportDemographicSupportingMapper.carries(decision)).isTrue();
    }

    @Test
    void shouldVerifyAbsentJurisdictionRequiresReview_beforeChoosingPostalOrZipCode() throws Exception {
        for (String province : Arrays.asList(null, "")) {
            var result =
                    map(
                            List.of(link(-1, 9)),
                            List.of(
                                    changed(
                                            changed(pharmacy(9), "province", province),
                                            "postalCode",
                                            "12345")));
            // Neither postal arm is guessed: the stored value travels in the native record.
            assertThat(result.nativeRepresentable()).isTrue();
            assertThat(result.preferred().address().postalCode()).isNull();
            assertCarried(result, pharmacy(9), "postalCode");
        }
        assertThatThrownBy(
                        () -> new CdsExportPreferredPharmacyDto.Address(null, null, null, "12345"))
                .hasMessage("CDS_EXPORT_PHARMACY_ADDRESS_INVALID");
    }

    @Test
    void shouldVerifyExactGuideFieldsAndRankOneAssociationHaveCompleteIndependentDispositions_forPharmacyMapperContract()
            throws Exception {
        var result = map(List.of(link(-1, 9)), List.of(pharmacy(9)));
        assertThat(result.nativeRepresentable()).isTrue();
        assertThat(result.fields()).hasSize(22);
        assertThat(result.preferred().name()).isEqualTo("Café 😀 Pharmacy");
        assertThat(result.preferred().address())
                .isEqualTo(
                        new CdsExportPreferredPharmacyDto.Address(
                                "12 Main St", "Ottawa", "CA-ON", "K1A0B1"));
        assertThat(result.preferred().phone())
                .isEqualTo(new CdsExportDemographicMappingDto.Phone("W", "613-555-0101"));
        assertThat(result.preferred().fax().type()).isEqualTo("W");
        assertThat(
                        result.fields()
                                .get(
                                        new CdsExportNativeFieldKeyDto(
                                                "demographicPharmacy",
                                                CdsExportPatientTableSourceReader.key(-1),
                                                "consentToContact"))
                                .presence())
                .isEqualTo(Presence.SQL_NULL);
    }

    @Test
    void shouldVerifyActualSchemaAndReceivingImporterPreserveNameAddressPhoneFaxAndEmail_forPharmacyMapperContract() throws Exception {
        var record = parse(map(List.of(link(-1, 9)), List.of(pharmacy(9))), HASH);
        var fields = new CdsFieldParser();
        assertThat(fields.firstText(record, "PreferredPharmacy/Name", 50))
                .isEqualTo("Café 😀 Pharmacy");
        assertThat(
                        fields.firstText(
                                record,
                                "PreferredPharmacy/Address/Structured/CountrySubdivisionCode",
                                7))
                .isEqualTo("CA-ON");
        assertThat(
                        fields.firstText(
                                record,
                                "PreferredPharmacy/Address/Structured/PostalZipCode/PostalCode",
                                10))
                .isEqualTo("K1A0B1");
        assertThat(fields.firstText(record, "PreferredPharmacy/PhoneNumber/@phoneNumberType", 1))
                .isEqualTo("W");
        assertThat(fields.firstText(record, "PreferredPharmacy/FaxNumber/phoneNumber", 25))
                .isEqualTo("613-555-0102");
        assertThat(fields.firstText(record, "PreferredPharmacy/EmailAddress", 50))
                .isEqualTo("rx@example.test");
    }

    @ParameterizedTest
    @ValueSource(strings = {"phone2", "notes", "serviceLocationIdentifier"})
    void shouldVerifyAdditionalPopulatedNativeFieldsNeverDisappearBehindAValidPreferredPharmacy_forPharmacyMapperContract(String column)
            throws Exception {
        var result =
                map(
                        List.of(link(-1, 9)),
                        List.of(changed(pharmacy(9), column, "Extra native value")));
        // Phone 2, Notes and the service location have no PreferredPharmacy element.
        assertThat(result.nativeRepresentable()).isTrue();
        assertThat(result.preferred()).isNotNull();
        assertCarried(result, pharmacy(9), column);
        parse(result, HASH);
    }

    @Test
    void shouldExportADeletedPreferredPharmacy_andLeaveARemovedLinkOutOfTheChart() throws Exception {
        // A removed link (status 0) is no longer in the chart and is not exported.
        var removed = map(List.of(changed(link(-1, 9), "status", "0")), List.of(pharmacy(9)));
        assertThat(removed.nativeRepresentable()).isTrue();
        assertThat(removed.preferred()).isNull();
        assertThat(removed.fields()).hasSize(22);
        assertThat(removed.fields().values())
                .noneMatch(field -> field.outcome().name().equals("MAPPED"));
        // A pharmacy deleted in Manage Pharmacy but still preferred is what the chart shows.
        var deleted = map(List.of(link(-1, 9)), List.of(changed(pharmacy(9), "status", "0")));
        assertThat(deleted.nativeRepresentable()).isTrue();
        assertThat(deleted.preferred()).isNotNull();
        assertCarried(deleted, pharmacy(9), "status");
    }

    @Test
    void shouldVerifyDuplicateFirstPreferencesAreNeverSelected_byRowOrderEvenForTheSamePharmacy()
            throws Exception {
        var result = map(List.of(link(-1, 9), link(-2, 9)), List.of(pharmacy(9)));
        assertThat(result.nativeRepresentable()).isFalse();
        assertThat(result.preferred()).isNull();
        assertThat(result.fields()).hasSize(29);
    }

    @Test
    void shouldVerifyAdditionalRankedPharmaciesNeedAHistoryOrNativeCarrier_insteadOfSilentOmission()
            throws Exception {
        var result =
                map(
                        List.of(link(-1, 9), changed(link(-2, 10), "preferredOrder", "2")),
                        List.of(pharmacy(9), pharmacy(10)));
        assertThat(result.preferred().name()).isEqualTo("Café 😀 Pharmacy");
        // The second preferred pharmacy travels whole in the native record.
        assertThat(result.nativeRepresentable()).isTrue();
        assertThat(result.fields()).hasSize(44);
        assertCarried(result, changed(link(-2, 10), "preferredOrder", "2"), "preferredOrder");
        assertCarried(result, pharmacy(10), "name");
    }

    @Test
    void shouldVerifyAbsentFirstPreferenceMissingLinkedRowsAndUnknownFlagsCannotProduceSuccessfulEmptyOutput_forPharmacyMapperContract()
            throws Exception {
        for (var row :
                List.of(changed(link(-1, 9), "status", ""), changed(link(-1, 9), "status", null))) {
            var result = map(List.of(row), List.of(pharmacy(9)));
            assertThat(result.nativeRepresentable()).isFalse();
            assertThat(result.preferred()).isNull();
        }
        // A lone second preference has no first one to export; it travels in the record.
        var second = map(List.of(changed(link(-1, 9), "preferredOrder", "2")), List.of(pharmacy(9)));
        assertThat(second.nativeRepresentable()).isTrue();
        assertThat(second.preferred()).isNull();
        var missing = map(List.of(link(-1, 9)), List.of());
        assertThat(missing.nativeRepresentable()).isFalse();
        assertThat(missing.preferred()).isNull();
        var empty = map(List.of(), List.of());
        assertThat(empty.nativeRepresentable()).isTrue();
        assertThat(empty.preferred()).isNull();
    }

    @Test
    void shouldNameEachPharmacyReviewRule_withItsOwnHoldCode() throws Exception {
        assertThat(map(List.of(link(-1, 9)), List.of(pharmacy(9))).reviewCode()).isNull();
        assertThat(map(List.of(link(-1, 9)), List.of()).reviewCode())
                .isEqualTo("CDS_EXPORT_NATIVE_PHARMACY_LINK_DANGLING");
        assertThat(map(List.of(changed(link(-1, 9), "status", "")), List.of(pharmacy(9))).reviewCode())
                .isEqualTo("CDS_EXPORT_NATIVE_PHARMACY_LINK_STATUS_UNKNOWN");
        assertThat(map(List.of(link(-1, 9), link(-2, 9)), List.of(pharmacy(9))).reviewCode())
                .isEqualTo("CDS_EXPORT_NATIVE_PHARMACY_FIRST_PREFERENCE_TIED");
        assertThat(
                        map(List.of(changed(link(-1, 9), "consentToContact", "7")), List.of(pharmacy(9)))
                                .reviewCode())
                .isEqualTo("CDS_EXPORT_NATIVE_PHARMACY_CONSENT_UNKNOWN");
        // A removed link to a pharmacy row that no longer exists is not in the chart either.
        var removed = map(List.of(changed(link(-1, 9), "status", "0")), List.of());
        assertThat(removed.nativeRepresentable()).isTrue();
        assertThat(removed.reviewCode()).isNull();
        for (String code : CdsExportPharmacyMapper.REVIEW_CODES.values())
            assertThat(new io.github.carlos_emr.carlos.demographic.cdsexport.support
                                    .CdsExportMappingReviewException(code))
                    .hasMessage(code);
    }

    @Test
    void shouldVerifyContactConsentIsCapturedSourceContextAndNeverInventedOrDiscarded_forPharmacyMapperContract() throws Exception {
        // Updated for D5: "1" is the model default that DemographicPharmacyDaoImpl persists for every
        // pharmacy a patient is linked to, so it used to hold every UI-linked pharmacy. It is now
        // operational. An explicit refusal ("0", "No Consent") is carried exactly in the native
        // demographic record; only an unknown spelling, which no screen writes, requires review.
        assertThat(
                        map(List.of(changed(link(-1, 9), "consentToContact", "true")), List.of(pharmacy(9)))
                                .nativeRepresentable())
                .isFalse();
        var refused = changed(link(-1, 9), "consentToContact", "0");
        var carried = map(List.of(refused), List.of(pharmacy(9)));
        assertThat(carried.nativeRepresentable()).isTrue();
        assertThat(
                        carried.fields()
                                .get(
                                        new CdsExportNativeFieldKeyDto(
                                                "demographicPharmacy", refused.recordKey(), "consentToContact"))
                                .rule())
                .isEqualTo(CdsExportContactConsent.REFUSAL_CARRIED_RULE);
        var linked = changed(link(-1, 9), "consentToContact", "1");
        var result = map(List.of(linked), List.of(pharmacy(9)));
        assertThat(result.nativeRepresentable()).isTrue();
        assertThat(
                        result.fields()
                                .get(
                                        new CdsExportNativeFieldKeyDto(
                                                "demographicPharmacy",
                                                linked.recordKey(),
                                                "consentToContact"))
                                .rule())
                .isEqualTo("DEFAULT_CONTACT_CONSENT_HAS_NO_CDS_CARRIER");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "name",
                "address",
                "city",
                "phone1",
                "fax",
                "email",
                "province",
                "postalCode"
            })
    void shouldVerifyOverlengthOrInvalidLexicalFieldsRequireReviewRatherThanTruncation_forPharmacyMapperContract(String column)
            throws Exception {
        String value =
                switch (column) {
                    case "name" -> "😀".repeat(51);
                    case "address" -> "X".repeat(51);
                    case "city" -> "X".repeat(81);
                    case "phone1", "fax" -> "1".repeat(26);
                    case "email" -> "rx+tag@example.test";
                    case "province" -> "Ontario";
                    default -> "K1A  0B1";
                };
        var result = map(List.of(link(-1, 9)), List.of(changed(pharmacy(9), column, value)));
        // Nothing is truncated or held: the stored value travels exactly in the native record
        // (a name the element cannot hold takes the whole pharmacy with it).
        assertThat(result.nativeRepresentable()).isTrue();
        assertCarried(result, pharmacy(9), column);
        parse(result, HASH);
    }

    @Test
    void shouldVerifyUnicodeGuideBoundaryAndLiteralPhoneExtensionArePreserved_withoutReverseParsing()
            throws Exception {
        var row =
                changed(
                        changed(pharmacy(9), "name", "😀".repeat(50)),
                        "phone1",
                        "613-555-0101 ext 5");
        var result = map(List.of(link(-1, 9)), List.of(row));
        assertThat(result.nativeRepresentable()).isTrue();
        assertThat(result.preferred().phone().number()).isEqualTo("613-555-0101 ext 5");
        assertThat(result.preferred().phone().extension()).isNull();
        parse(result, HASH);
    }

    @Test
    void shouldVerifyUnsupportedMultilineAddressAndNonTokenValuesRequireExplicitReconciliation_forPharmacyMapperContract()
            throws Exception {
        // Whitespace an xs:token collapses: the collapsed value is written, the exact one carried.
        for (var pair :
                List.of(
                        Map.entry("address", "12 Main St\nSuite 3"),
                        Map.entry("phone1", " 613-555-0101"))) {
            var result =
                    map(
                            List.of(link(-1, 9)),
                            List.of(changed(pharmacy(9), pair.getKey(), pair.getValue())));
            assertThat(result.nativeRepresentable()).isTrue();
            assertCarried(result, pharmacy(9), pair.getKey());
        }
        // Text no XML document can hold still requires review.
        assertThat(
                        map(
                                        List.of(link(-1, 9)),
                                        List.of(changed(pharmacy(9), "name", "Bad\u0001value")))
                                .nativeRepresentable())
                .isFalse();
    }

    @Test
    void shouldVerifyExactUsSubdivisionUsesTheZipChoice_withoutCanadianNormalization() throws Exception {
        var result =
                map(
                        List.of(link(-1, 9)),
                        List.of(
                                changed(
                                        changed(pharmacy(9), "province", "US-NY"),
                                        "postalCode",
                                        "12345-6789")));
        assertThat(result.nativeRepresentable()).isTrue();
        assertThat(
                        new CdsFieldParser()
                                .firstText(
                                        parse(result, HASH),
                                        "PreferredPharmacy/Address/Structured/PostalZipCode/ZipCode",
                                        10))
                .isEqualTo("12345-6789");
    }

    @Test
    void shouldVerifyMissingRequiredNamesCannotLeaveARepresentableAssociation_forPharmacyMapperContract() throws Exception {
        for (String name : Arrays.asList(null, "")) {
            var result = map(List.of(link(-1, 9)), List.of(changed(pharmacy(9), "name", name)));
            assertThat(result.nativeRepresentable()).isFalse();
            assertThat(result.preferred()).isNull();
        }
    }

    @Test
    void shouldVerifyForeignRowsUnlinkedChildrenChangedColumnSetsAndNoncanonicalKeysAreRejected_forPharmacyMapperContract()
            throws Exception {
        assertThatThrownBy(
                        () ->
                                map(
                                        List.of(changed(link(-1, 9), "demographic_no", "28")),
                                        List.of(pharmacy(9))))
                .hasMessage("CDS_EXPORT_PHARMACY_SCOPE_INVALID");
        assertThatThrownBy(() -> map(List.of(link(-1, 9)), List.of(pharmacy(10))))
                .hasMessage("CDS_EXPORT_PHARMACY_SCOPE_INVALID");
        assertThatThrownBy(() -> map(List.of(link(-1, 9), link(-1, 9)), List.of(pharmacy(9))))
                .hasMessage("CDS_EXPORT_PHARMACY_SCOPE_INVALID");
        assertThatThrownBy(() -> map(List.of(link(-1, 9)), List.of(pharmacy(9), pharmacy(9))))
                .hasMessage("CDS_EXPORT_PHARMACY_SCOPE_INVALID");
        assertThatThrownBy(
                        () ->
                                map(
                                        List.of(changed(link(-1, 9), "pharmacyID", "09")),
                                        List.of(pharmacy(9))))
                .hasMessage("CDS_EXPORT_PHARMACY_SCOPE_INVALID");
        var fields = new TreeMap<>(pharmacy(9).fields());
        fields.remove("notes");
        assertThatThrownBy(
                        () ->
                                map(
                                        List.of(link(-1, 9)),
                                        List.of(
                                                new CdsExportCapturedTextRowDto(
                                                        "pharmacyInfo",
                                                        pharmacy(9).recordKey(),
                                                        fields))))
                .hasMessage("CDS_EXPORT_PHARMACY_SCOPE_INVALID");
    }

    @Test
    void shouldVerifyByteAndCombinedRowLimitsAndCancellationAreEnforced_forPharmacyMapperContract() {
        assertThatThrownBy(
                        () ->
                                mapper.map(
                                        27,
                                        List.of(link(-1, 9)),
                                        List.of(pharmacy(9)),
                                        1,
                                        1L << 20,
                                        () -> {}))
                .hasMessage("CDS_EXPORT_PHARMACY_RESOURCE_LIMIT");
        assertThatThrownBy(
                        () ->
                                mapper.map(
                                        27,
                                        List.of(link(-1, 9)),
                                        List.of(pharmacy(9)),
                                        100,
                                        64,
                                        () -> {}))
                .hasMessage("CDS_EXPORT_PHARMACY_RESOURCE_LIMIT");
        assertThatThrownBy(
                        () ->
                                mapper.map(
                                        27,
                                        List.of(link(-1, 9)),
                                        List.of(pharmacy(9)),
                                        100,
                                        1L << 20,
                                        () -> {
                                            throw new IllegalStateException("CANCELLED");
                                        }))
                .hasMessage("CANCELLED");
    }

    @Test
    void shouldVerifyEqualDispositionCountsCannotSubstituteAnotherFieldIdentity_forPharmacyMapperContract() throws Exception {
        var value = map(List.of(link(-1, 9)), List.of(pharmacy(9)));
        var fields = new HashMap<>(value.fields());
        var key = new CdsExportNativeFieldKeyDto("pharmacyInfo", pharmacy(9).recordKey(), "notes");
        var prior = fields.remove(key);
        fields.put(
                new CdsExportNativeFieldKeyDto(
                        "pharmacyInfo", CdsExportPatientTableSourceReader.key(10), "notes"),
                prior);
        assertThatThrownBy(
                        () ->
                                new CdsExportPharmacyMapper.Result(
                                        27, 1, 1, value.preferred(), fields))
                .hasMessage("CDS_EXPORT_PHARMACY_MAPPING_INVALID");
    }

    @Test
    void shouldVerifyProtectedValuesDoNotEnterDebugStrings_forPharmacyMapperContract() throws Exception {
        var value = map(List.of(link(-1, 9)), List.of(pharmacy(9)));
        assertThat(value.toString() + value.preferred() + value.preferred().address())
                .doesNotContain("Café", "Main", "Ottawa", "613");
    }

    @Test
    void shouldVerifyDifferentPatientOrGenerationComponentsCannotBeCombined_forPharmacyMapperContract() throws Exception {
        var value = map(List.of(link(-1, 9)), List.of(pharmacy(9)));
        assertThatThrownBy(() -> parse(value, "b".repeat(64)))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_PHARMACY_REQUIRES_REVIEW");
        var other =
                new CdsExportPharmacyMapper.Result(
                        28,
                        value.linkRows(),
                        value.pharmacyRows(),
                        value.preferred(),
                        value.fields());
        assertThatThrownBy(() -> parse(other, HASH))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_PHARMACY_REQUIRES_REVIEW");
    }

    static CdsExportCapturedTextRowDto link(long id, long reference) {
        var fields = columns(CdsExportPatientTableReadSupport.Plan.PHARMACY_LINK);
        fields.putAll(
                Map.of(
                        "id",
                        Long.toString(id),
                        "demographic_no",
                        "27",
                        "pharmacyID",
                        Long.toString(reference),
                        "status",
                        "1",
                        "preferredOrder",
                        "1"));
        return new CdsExportCapturedTextRowDto(
                "demographicPharmacy", CdsExportPatientTableSourceReader.key(id), fields);
    }

    static CdsExportCapturedTextRowDto pharmacy(long id) {
        var fields = columns(CdsExportPatientTableReadSupport.Plan.PHARMACY);
        fields.putAll(
                Map.of(
                        "recordID",
                        Long.toString(id),
                        "name",
                        "Café 😀 Pharmacy",
                        "address",
                        "12 Main St",
                        "city",
                        "Ottawa",
                        "province",
                        "ON",
                        "postalCode",
                        "K1A 0B1",
                        "phone1",
                        "613-555-0101",
                        "fax",
                        "613-555-0102",
                        "email",
                        "rx@example.test",
                        "status",
                        "1"));
        fields.put("uid", "0");
        return new CdsExportCapturedTextRowDto(
                "pharmacyInfo", CdsExportPatientTableSourceReader.key(id), fields);
    }

    static CdsExportCapturedTextRowDto changed(
            CdsExportCapturedTextRowDto row, String column, String value) {
        var fields = new TreeMap<>(row.fields());
        fields.put(column, value);
        return new CdsExportCapturedTextRowDto(row.table(), row.recordKey(), fields);
    }

    private static Map<String, String> columns(CdsExportPatientTableReadSupport.Plan plan) {
        var values = new TreeMap<String, String>();
        plan.columns().forEach(column -> values.put(column, null));
        return values;
    }

    private CdsExportPharmacyMapper.Result map(
            List<CdsExportCapturedTextRowDto> links, List<CdsExportCapturedTextRowDto> pharmacies)
            throws Exception {
        return mapper.map(27, links, pharmacies, 100, 1L << 20, () -> {});
    }

    private CdsRecordDto parse(CdsExportPharmacyMapper.Result pharmacy, String sourceHash)
            throws Exception {
        var row = CdsExportEnrolmentHistoryUnitTest.current(null, null, null, null, null);
        var history = CdsExportEnrolmentHistoryUnitTest.history();
        history.accept(row, null);
        var demographic =
                CdsExportEnrolmentHistoryUnitTest.projection(row, history.finish(0), null);
        var section =
                new CdsExportXmlComposer.Section(
                        new CdsExportIdentityDto(HASH, CdsCategory.DEMOGRAPHICS, "b".repeat(64)),
                        xml ->
                                new CdsExportDemographicXmlWriter()
                                        .writeWithPharmacy(
                                                demographic,
                                                new CdsExportPharmacyProjectionService.Result(
                                                        sourceHash, pharmacy),
                                                xml));
        var output = new ByteArrayOutputStream();
        var selected = Set.of(CdsCategory.DEMOGRAPHICS);
        new CdsExportXmlComposer()
                .compose(
                        output,
                        HASH,
                        HASH,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        selected,
                        List.of(section).iterator(),
                        ignored -> {},
                        1L << 20,
                        () -> {});
        new CdsExportXmlValidator()
                .validate(
                        new ByteArrayInputStream(output.toByteArray()),
                        "27",
                        selected,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportXmlLimitsDto.defaults(),
                        ignored -> {},
                        () -> {});
        var records = new ArrayList<CdsRecordDto>();
        new CdsXmlParser()
                .parse(
                        Files.write(
                                directory.resolve(UUID.randomUUID() + ".xml"),
                                output.toByteArray()),
                        directory.resolve(UUID.randomUUID().toString()),
                        CdsInputLimitsDto.defaults(),
                        () -> false,
                        records::add);
        return records.getFirst();
    }
}
