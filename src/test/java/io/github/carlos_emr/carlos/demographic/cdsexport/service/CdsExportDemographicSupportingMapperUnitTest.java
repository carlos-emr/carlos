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

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Presence;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsFieldParser;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsXmlParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A chart with every value the demographic add and edit screens can write is exported, never
 * held: values with a CDS element map to it, the patient alert becomes an AlertsAndSpecialNeeds
 * occurrence and every other value travels exactly in the native demographic NewCategory record.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportDemographicSupportingMapperUnitTest {
    private static final String HASH = "a".repeat(64);
    private static final String ALERT = "Latex allergy\r\nUses a wheelchair  (call ahead)";
    private static final Set<CdsCategory> ALL =
            Set.of(
                    CdsCategory.DEMOGRAPHICS,
                    CdsCategory.ALERTS_AND_SPECIAL_NEEDS,
                    CdsCategory.NEW_CATEGORY);

    /** Every base-row column with no Demographics element, as the forms can write it. */
    static final Map<String, String> ANCILLARY = ancillary();

    /** Extension properties the Master Record form writes, populated. */
    static final Map<String, String> PROPERTIES = properties();

    @TempDir Path root;
    private Map<String, List<CdsExportCapturedTextRowDto>> rows;

    @BeforeEach
    void setup() {
        rows = new LinkedHashMap<>();
        for (String table :
                List.of(
                        "demographic",
                        "demographicExt",
                        "demographiccust",
                        "demographicArchive",
                        "provider",
                        "Contact",
                        "DemographicContact",
                        "pharmacyInfo",
                        "demographicPharmacy")) rows.put(table, new ArrayList<>());
        var values = new LinkedHashMap<String, String>();
        CdsExportDemographicRowDto.COLUMNS.forEach(column -> values.put(column, null));
        values.put("demographic_no", "27");
        values.putAll(
                Map.ofEntries(
                        Map.entry("title", "MS"),
                        Map.entry("first_name", "Élodie😀"),
                        Map.entry("last_name", "D’Arcy"),
                        Map.entry("middleNames", "Anne"),
                        Map.entry("year_of_birth", "2000"),
                        Map.entry("month_of_birth", "02"),
                        Map.entry("date_of_birth", "29"),
                        Map.entry("sex", "F"),
                        Map.entry("patient_status", "AC"),
                        Map.entry("patient_status_date", "2024-01-02"),
                        Map.entry("hin", "1234567890"),
                        Map.entry("ver", "AB"),
                        Map.entry("hc_type", "ON"),
                        Map.entry("hc_renew_date", "2030-02-03"),
                        Map.entry("chart_no", "C-17"),
                        Map.entry("address", "12 Main St"),
                        Map.entry("city", "Ottawa"),
                        Map.entry("province", "ON"),
                        Map.entry("postal", "K1A 0B1"),
                        Map.entry("residentialProvince", "ON"),
                        Map.entry("phone", "613-555-0101"),
                        Map.entry("phone2", "613-555-0102"),
                        Map.entry("email", "patient@example.org"),
                        Map.entry("official_lang", "English"),
                        Map.entry("spoken_lang", "English"),
                        Map.entry("sin", "046454286"),
                        Map.entry("date_joined", "2009-04-01"),
                        Map.entry("eff_date", "2024-01-15"),
                        Map.entry("lastUpdateUser", "999998"),
                        Map.entry("lastUpdateDate", "2024-01-15 10:11:12")));
        values.putAll(ANCILLARY);
        rows.get("demographic")
                .add(new CdsExportCapturedTextRowDto("demographic", "0000000027", values));
        long id = 0;
        for (var property : PROPERTIES.entrySet())
            rows.get("demographicExt")
                    .add(
                            CdsExportDemographicPhonesMapperUnitTest.property(
                                    ++id, property.getKey(), property.getValue()));
        custom("101", "102", "103", ALERT, true);
        rows.get("provider").add(provider("101", "Nora", "Nurse"));
        rows.get("provider").add(provider("102", "Rex", "Resident"));
    }

    private void custom(String nurse, String resident, String midwife, String alert, boolean alerts) {
        var values = new LinkedHashMap<String, String>();
        (alerts ? Plan.DEMOGRAPHIC_CUST : Plan.DEMOGRAPHIC_CUST_DEMOGRAPHICS)
                .columns()
                .forEach(column -> values.put(column, null));
        values.put("demographic_no", "27");
        values.put("content", "<unotes>Native note</unotes>");
        values.put("cust1", nurse);
        values.put("cust2", resident);
        values.put("cust4", midwife);
        if (alerts) values.put("cust3", alert);
        rows.get("demographiccust").clear();
        rows.get("demographiccust")
                .add(
                        new CdsExportCapturedTextRowDto(
                                "demographiccust",
                                CdsExportPatientTableSourceReader.key(27),
                                values));
    }

    static CdsExportCapturedTextRowDto provider(String id, String first, String last) {
        return new CdsExportCapturedTextRowDto(
                "provider",
                CdsExportPublicProviderSourceReader.key(id),
                Map.of(
                        "provider_no",
                        id,
                        "first_name",
                        first,
                        "last_name",
                        last,
                        "ohip_no",
                        "",
                        "practitionerNoType",
                        "",
                        "practitionerNo",
                        ""));
    }

    private void set(String column, String value) {
        var row = rows.get("demographic").getFirst();
        var values = new LinkedHashMap<>(row.fields());
        values.put(column, value);
        rows.get("demographic")
                .set(0, new CdsExportCapturedTextRowDto(row.table(), row.recordKey(), values));
    }

    private CdsExportNativeDemographicAssembler.Result assemble(Set<CdsCategory> categories)
            throws IOException {
        return new CdsExportNativeDemographicAssembler()
                .assemble(27, categories, HASH, rows, 100, 1L << 20, () -> {});
    }

    private CdsExportDemographicSupportingMapper.Rows supportingRows() {
        var custom = rows.get("demographiccust");
        var providers = new LinkedHashMap<String, CdsExportCapturedTextRowDto>();
        var references =
                CdsExportDemographicSupportingMapper.references(
                        rows.get("demographic").getFirst(),
                        custom.isEmpty() ? null : custom.getFirst());
        for (var provider : rows.get("provider"))
            if (references.contains(provider.fields().get("provider_no")))
                providers.put(provider.fields().get("provider_no"), provider);
        var related = new LinkedHashMap<String, List<CdsExportCapturedTextRowDto>>();
        for (String table : CdsExportDemographicSupportingMapper.Rows.RELATED)
            related.put(table, rows.get(table));
        return new CdsExportDemographicSupportingMapper.Rows(
                27,
                rows.get("demographic").getFirst(),
                custom.isEmpty() ? null : custom.getFirst(),
                rows.get("demographicExt"),
                providers,
                related);
    }

    private static CdsExportNativeFieldKeyDto key(String table, String recordKey, String column) {
        return new CdsExportNativeFieldKeyDto(table, recordKey, column);
    }

    @Test
    void shouldNeverHoldAChart_whenEveryFieldTheDemographicScreensWriteIsFilled() throws Exception {
        var result = assemble(ALL);
        assertThat(result.fields().values())
                .noneMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW);
        for (String column : ANCILLARY.keySet())
            assertThat(result.fields().get(key("demographic", "0000000027", column)))
                    .isEqualTo(
                            new CdsExportFieldAssessmentDto(
                                    Presence.VALUE,
                                    Outcome.MAPPED,
                                    CdsExportDemographicSupportingMapper.DESTINATION,
                                    CdsExportDemographicSupportingMapper.CARRIED_RULE));
        String custom = CdsExportPatientTableSourceReader.key(27);
        for (String column : List.of("cust1", "cust2", "cust4"))
            assertThat(result.fields().get(key("demographiccust", custom, column)).destination())
                    .isEqualTo(CdsExportDemographicSupportingMapper.DESTINATION);
        assertThat(result.fields().get(key("demographiccust", custom, "cust3")).destination())
                .isEqualTo(CdsExportDemographicSupportingMapper.ALERT_DESTINATION);
        // The care-team providers' public rows are accounted for by the record that names them.
        assertThat(
                        result.fields()
                                .get(
                                        key(
                                                "provider",
                                                CdsExportPublicProviderSourceReader.key("101"),
                                                "last_name"))
                                .destination())
                .isEqualTo(CdsExportDemographicSupportingMapper.DESTINATION);
        for (var row : rows.get("demographicExt"))
            if (!Set.of("hPhoneExt", "demo_cell").contains(row.fields().get("key_val")))
                assertThat(result.fields().get(key(row.table(), row.recordKey(), "value")).destination())
                        .isEqualTo(CdsExportDemographicSupportingMapper.DESTINATION);
        // The record carries exactly the values the Demographics receipt sends to it.
        var record = CdsExportDemographicSupportingMapper.map(supportingRows(), () -> {});
        var sent = new HashSet<CdsExportNativeFieldKeyDto>();
        result.fields()
                .forEach(
                        (field, decision) -> {
                            if (CdsExportDemographicSupportingMapper.carries(decision))
                                sent.add(field);
                        });
        assertThat(record.fields().keySet()).isEqualTo(sent);
    }

    @Test
    void shouldCarryEveryValueExactly_inAFixedOrderVersionedJsonRecord() throws Exception {
        var record = CdsExportDemographicSupportingMapper.map(supportingRows(), () -> {});
        String content = CdsExportScreeningMapper.tokenSafe(record.json());
        assertThat(content).doesNotContain(" ", "\t", "\n", "\r");
        var decoded = JsonMapper.builder().build().readTree(content);
        assertThat(decoded.get("version").asInt()).isEqualTo(1);
        var names = new ArrayList<String>();
        decoded.fieldNames().forEachRemaining(names::add);
        assertThat(names)
                .containsExactly(
                        "version", "demographic", "roster", "careTeam", "properties", "related");
        var demographic = new LinkedHashMap<String, String>();
        decoded.get("demographic")
                .fields()
                .forEachRemaining(entry -> demographic.put(entry.getKey(), entry.getValue().textValue()));
        assertThat(demographic).containsExactlyEntriesOf(ANCILLARY);
        var team = decoded.get("careTeam");
        assertThat(team).hasSize(3);
        assertThat(team.get(0).get("role").textValue()).isEqualTo("Nurse");
        assertThat(team.get(0).get("providerNo").textValue()).isEqualTo("101");
        assertThat(team.get(0).get("publicProvider").get("last_name").textValue()).isEqualTo("Nurse");
        assertThat(team.get(1).get("role").textValue()).isEqualTo("Resident");
        assertThat(team.get(1).get("publicProvider").get("first_name").textValue()).isEqualTo("Rex");
        // A number with no captured provider row still travels; no name is invented.
        assertThat(team.get(2).get("role").textValue()).isEqualTo("Midwife");
        assertThat(team.get(2).get("providerNo").textValue()).isEqualTo("103");
        assertThat(team.get(2).get("publicProvider").isNull()).isTrue();
        var carried = new LinkedHashMap<String, String>();
        decoded.get("properties")
                .forEach(entry -> carried.put(entry.get("key").textValue(), entry.get("value").textValue()));
        var expected = new LinkedHashMap<>(PROPERTIES);
        // The home extension and mobile number map to PhoneNumber; the rest travel here, among
        // them a work extension longer than the CDS five-character extension.
        expected.remove("hPhoneExt");
        expected.remove("demo_cell");
        assertThat(carried).containsExactlyInAnyOrderEntriesOf(expected);
    }

    @Test
    void shouldAccountForCarriedValuesAsNotSelected_whenNewCategoryIsNotSelected() throws Exception {
        var result =
                assemble(Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.ALERTS_AND_SPECIAL_NEEDS));
        assertThat(result.fields().values())
                .noneMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW)
                .noneMatch(
                        field ->
                                CdsExportDemographicSupportingMapper.DESTINATION.equals(
                                        field.destination()));
        assertThat(result.fields().get(key("demographic", "0000000027", "pronoun")))
                .isEqualTo(
                        new CdsExportFieldAssessmentDto(
                                Presence.VALUE,
                                Outcome.OPERATIONAL,
                                "",
                                CdsExportDemographicSupportingMapper.NOT_SELECTED_RULE));
        assertThat(
                        result.fields()
                                .get(
                                        key(
                                                "provider",
                                                CdsExportPublicProviderSourceReader.key("102"),
                                                "first_name"))
                                .rule())
                .isEqualTo(CdsExportDemographicSupportingMapper.NOT_SELECTED_RULE);
    }

    @Test
    void shouldKeepNullAndEmptyDistinct_andNeverCarryAFormPlaceholder() throws Exception {
        set("alias", null);
        set("pref_name", "");
        set("newsletter", "Unknown");
        set("country_of_origin", "-1");
        set("family_doctor", "<rdohip></rdohip><rd></rd>");
        var result = assemble(ALL);
        assertThat(result.fields().get(key("demographic", "0000000027", "alias")))
                .extracting(CdsExportFieldAssessmentDto::presence, CdsExportFieldAssessmentDto::outcome)
                .containsExactly(Presence.SQL_NULL, Outcome.ABSENT);
        assertThat(result.fields().get(key("demographic", "0000000027", "pref_name")))
                .extracting(CdsExportFieldAssessmentDto::presence, CdsExportFieldAssessmentDto::outcome)
                .containsExactly(Presence.EMPTY, Outcome.ABSENT);
        for (String column : List.of("newsletter", "country_of_origin", "family_doctor"))
            assertThat(result.fields().get(key("demographic", "0000000027", column)).outcome())
                    .isEqualTo(Outcome.OPERATIONAL);
        var json =
                JsonMapper.builder()
                        .build()
                        .readTree(CdsExportDemographicSupportingMapper.map(supportingRows(), () -> {}).json());
        assertThat(json.get("demographic").has("alias")).isFalse();
        assertThat(json.get("demographic").has("pref_name")).isFalse();
        assertThat(json.get("demographic").has("newsletter")).isFalse();
    }

    @Test
    void shouldWriteNoRecord_forAPatientWithOnlyFormDefaults() throws Exception {
        for (String column : ANCILLARY.keySet()) set(column, null);
        set("newsletter", "Unknown");
        set("country_of_origin", "-1");
        rows.get("demographicExt").clear();
        rows.get("demographicExt")
                .add(CdsExportDemographicPhonesMapperUnitTest.property(1, "rxInteractionWarningLevel", "0"));
        rows.get("demographicExt").add(CdsExportDemographicPhonesMapperUnitTest.property(2, "ethnicity", ""));
        custom(null, "", null, null, true);
        // No care-team column names a provider, so none is in the demographic provider slice.
        rows.put("provider", new ArrayList<>());
        assertThat(CdsExportDemographicSupportingMapper.map(supportingRows(), () -> {})).isNull();
        assertThat(assemble(ALL).fields().values())
                .noneMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW)
                .noneMatch(field -> field.outcome() == Outcome.MAPPED
                        && CdsExportDemographicSupportingMapper.DESTINATION.equals(field.destination()));
    }

    @Test
    void shouldStillHoldAValueNoXmlDocumentCanCarry_forSupportingRecordContract() {
        set("pronoun", "she/her￾");
        assertThatThrownBy(() -> assemble(ALL)).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
        assertThatThrownBy(() -> CdsExportDemographicSupportingMapper.map(supportingRows(), () -> {}))
                .isInstanceOf(CdsExportMappingReviewException.class)
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_SUPPORTING_TEXT_REQUIRES_REVIEW");
    }

    @Test
    void shouldHoldOnlyAMalformedPatientAlert_forPatientAlertContract() throws Exception {
        custom("101", "102", "103", "bell\u0007", true);
        assertThatThrownBy(() -> assemble(ALL)).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
        assertThatThrownBy(
                        () ->
                                CdsExportPatientAlertMapper.map(
                                        27, rows.get("demographiccust").getFirst()))
                .hasMessage("CDS_EXPORT_PATIENT_ALERT_WIRE_TEXT_REQUIRES_REVIEW");
        // Not selected: the alert column is not captured and there is no occurrence.
        custom("101", "102", "103", null, false);
        assertThat(CdsExportPatientAlertMapper.map(27, rows.get("demographiccust").getFirst()))
                .isNull();
        assertThat(assemble(Set.of(CdsCategory.DEMOGRAPHICS)).fields().values())
                .noneMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW);
    }

    @Test
    void shouldRoundTripEveryCarriedValue_throughThePinnedSchemaAndTheCarlosImporter()
            throws Exception {
        var result = assemble(ALL);
        var record = CdsExportDemographicSupportingMapper.map(supportingRows(), () -> {});
        var alert = CdsExportPatientAlertMapper.map(27, rows.get("demographiccust").getFirst());
        var wire = new ByteArrayOutputStream();
        new CdsExportXmlComposer()
                .compose(
                        wire,
                        HASH,
                        HASH,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        ALL,
                        List.of(
                                        new CdsExportXmlComposer.Section(
                                                new CdsExportIdentityDto(
                                                        HASH, CdsCategory.DEMOGRAPHICS, HASH),
                                                xml ->
                                                        new CdsExportDemographicXmlWriter()
                                                                .writeWithRelated(
                                                                        result.demographic(),
                                                                        result.pharmacy(),
                                                                        result.contacts(),
                                                                        result.professional(),
                                                                        "C27",
                                                                        xml)),
                                        new CdsExportXmlComposer.Section(
                                                new CdsExportIdentityDto(
                                                        HASH,
                                                        CdsCategory.ALERTS_AND_SPECIAL_NEEDS,
                                                        HASH),
                                                xml -> CdsExportPatientAlertMapper.write(alert, xml)),
                                        new CdsExportXmlComposer.Section(
                                                new CdsExportIdentityDto(
                                                        HASH, CdsCategory.NEW_CATEGORY, HASH),
                                                xml ->
                                                        CdsExportDemographicSupportingMapper.write(
                                                                record.json(), xml)))
                                .iterator(),
                        ignored -> {},
                        1L << 20,
                        () -> {});
        new CdsExportXmlValidator()
                .validate(
                        new ByteArrayInputStream(wire.toByteArray()),
                        "C27",
                        ALL,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportXmlLimitsDto.defaults(),
                        ignored -> {},
                        () -> {});
        Path directory = Files.createTempDirectory(root, "roundtrip-");
        var records = new ArrayList<CdsRecordDto>();
        new CdsXmlParser()
                .parse(
                        Files.write(directory.resolve("patient.xml"), wire.toByteArray()),
                        directory.resolve("parsed"),
                        CdsInputLimitsDto.defaults(),
                        () -> false,
                        records::add);
        var alerts = text(section(records, "AlertsAndSpecialNeeds"));
        // The CARLOS importer files AlertDescription as the alert's Reminders note text.
        assertThat(alerts).containsEntry("AlertDescription[1]", ALERT);
        var supporting = text(section(records, "NewCategory"));
        assertThat(supporting)
                .containsEntry("CategoryName[1]", CdsExportDemographicSupportingMapper.CATEGORY_NAME)
                .containsEntry(
                        "ResidualInfo[1]/DataElement[1]/Name[1]",
                        CdsExportDemographicSupportingMapper.ELEMENT_NAME);
        // The importer keeps the record as source fields; the decoded JSON is the exact record.
        String content = supporting.get("ResidualInfo[1]/DataElement[1]/Content[1]");
        assertThat(JsonMapper.builder().build().readTree(content))
                .isEqualTo(JsonMapper.builder().build().readTree(record.json()));
        var demographics = text(section(records, "Demographics"));
        assertThat(demographics)
                .containsEntry("PhoneNumber[1]/extension[1]", "1234")
                .containsEntry("NoteAboutPatient[1]", "Native note");
    }

    @Test
    void shouldNeverHoldAChart_withOrdinaryRosterWireTextPharmacyAndContactValues()
            throws Exception {
        // Values the demographic, pharmacy and contact screens accept that no CDS element can
        // hold as typed: fee-for-service roster, intersex, "Other" official language, an "OT"
        // province, an untrimmed address, a "+" e-mail, a SIN with spaces, a second pharmacy with
        // Phone 2, and an inactive contact with an address.
        set("roster_status", "FS");
        set("sex", "X");
        set("official_lang", "Other");
        set("province", "OT");
        set("address", "12  Main St ");
        set("postal", "K1A 0B1");
        set("email", "patient+clinic@example.org");
        set("sin", "046 454 286");
        rows.get("demographicPharmacy").add(CdsExportPharmacyMapperUnitTest.link(-1, 9));
        rows.get("demographicPharmacy")
                .add(
                        CdsExportPersonalContactMapperUnitTest.changed(
                                CdsExportPharmacyMapperUnitTest.link(-2, 10), "preferredOrder", "2"));
        rows.get("pharmacyInfo").add(CdsExportPharmacyMapperUnitTest.pharmacy(9));
        rows.get("pharmacyInfo")
                .add(
                        CdsExportPersonalContactMapperUnitTest.changed(
                                CdsExportPharmacyMapperUnitTest.pharmacy(10), "phone2", "613-555-0109"));
        rows.get("DemographicContact")
                .add(
                        CdsExportPersonalContactMapperUnitTest.changed(
                                CdsExportPersonalContactMapperUnitTest.association(-1, 9), "active", "0"));
        rows.get("Contact")
                .add(
                        CdsExportPersonalContactMapperUnitTest.changed(
                                CdsExportPersonalContactMapperUnitTest.contact(9), "address", "1 Side Rd"));
        var result = assemble(ALL);
        assertThat(result.fields().values())
                .noneMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW);
        assertThat(result.demographic().demographic().administrativeGender()).isEqualTo("O");
        assertThat(result.demographic().demographic().sin()).isEqualTo("046454286");
        assertThat(result.demographic().demographic().email()).isNull();
        assertThat(result.pharmacy().mapping().preferred()).isNotNull();
        var record = CdsExportDemographicSupportingMapper.map(supportingRows(), () -> {});
        var sent = new HashSet<CdsExportNativeFieldKeyDto>();
        result.fields()
                .forEach(
                        (field, decision) -> {
                            if (CdsExportDemographicSupportingMapper.carries(decision))
                                sent.add(field);
                        });
        assertThat(record.fields().keySet()).isEqualTo(sent);
        var json = JsonMapper.builder().build().readTree(record.json());
        for (var carried :
                Map.of(
                                "sex", "X",
                                "official_lang", "Other",
                                "province", "OT",
                                "address", "12  Main St ",
                                "email", "patient+clinic@example.org",
                                "sin", "046 454 286")
                        .entrySet())
            assertThat(json.get("demographic").get(carried.getKey()).textValue())
                    .isEqualTo(carried.getValue());
        assertThat(json.get("roster").get("roster_status").textValue()).isEqualTo("FS");
        var related = new HashSet<String>();
        json.get("related")
                .forEach(
                        row ->
                                row.get("fields")
                                        .fieldNames()
                                        .forEachRemaining(
                                                column -> related.add(row.get("table").textValue() + "." + column)));
        assertThat(related)
                .contains(
                        "demographicPharmacy.preferredOrder",
                        "pharmacyInfo.name",
                        "pharmacyInfo.phone2",
                        "DemographicContact.active",
                        "Contact.address");
        // Schema-valid as written.
        var wire = new ByteArrayOutputStream();
        new CdsExportXmlComposer()
                .compose(
                        wire,
                        HASH,
                        HASH,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.NEW_CATEGORY),
                        List.of(
                                        new CdsExportXmlComposer.Section(
                                                new CdsExportIdentityDto(
                                                        HASH, CdsCategory.DEMOGRAPHICS, HASH),
                                                xml ->
                                                        new CdsExportDemographicXmlWriter()
                                                                .writeWithRelated(
                                                                        result.demographic(),
                                                                        result.pharmacy(),
                                                                        result.contacts(),
                                                                        result.professional(),
                                                                        "C27",
                                                                        xml)),
                                        new CdsExportXmlComposer.Section(
                                                new CdsExportIdentityDto(
                                                        HASH, CdsCategory.NEW_CATEGORY, HASH),
                                                xml ->
                                                        CdsExportDemographicSupportingMapper.write(
                                                                record.json(), xml)))
                                .iterator(),
                        ignored -> {},
                        1L << 20,
                        () -> {});
        new CdsExportXmlValidator()
                .validate(
                        new ByteArrayInputStream(wire.toByteArray()),
                        "C27",
                        Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.NEW_CATEGORY),
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportXmlLimitsDto.defaults(),
                        ignored -> {},
                        () -> {});
    }

    @Test
    void shouldOwnExactlyTheCareTeamProviders_inTheDemographicProviderSlice() throws Exception {
        rows.get("provider").add(provider("101 ", "Collation", "Candidate"));
        rows.get("provider").add(provider("999", "Another", "Category"));
        var owned = CdsExportDemographicOccurrenceService.demographicRows(rows);
        assertThat(owned.get("provider"))
                .extracting(row -> row.fields().get("provider_no"))
                .containsExactlyInAnyOrder("101", "102");
    }

    private static CdsRecordDto section(List<CdsRecordDto> records, String section) {
        return records.stream()
                .filter(record -> record.section().equals(section))
                .findFirst()
                .orElseThrow();
    }

    private static Map<String, String> text(CdsRecordDto record) throws IOException {
        var parser = new CdsFieldParser();
        var values = new LinkedHashMap<String, String>();
        parser.forEachField(
                record,
                field -> {
                    if (!field.kind().equals("BINARY"))
                        try {
                            values.put(field.path(), parser.readLogicalText(record, field, 1 << 20));
                        } catch (IOException error) {
                            throw new UncheckedIOException(error);
                        }
                });
        return values;
    }

    private static Map<String, String> ancillary() {
        var values = new LinkedHashMap<String, String>();
        values.put("alias", "Bobby  Tables");
        values.put("anonymous", "one-time");
        values.put("children", "Y");
        values.put("citizenship", "Canadian");
        values.put("consentToUseEmailForCare", "1");
        values.put("country_of_origin", "CA");
        values.put("end_date", "2030-01-01");
        values.put(
                "family_doctor",
                "<rdohip>123456</rdohip><rd>Referral,Rita</rd><family_doc>Dr. Family</family_doc>");
        values.put("gender", "Woman");
        values.put("genderId", "3");
        values.put("myOscarUserName", "patient.portal");
        values.put("newsletter", "Paper");
        values.put("pcn_indicator", "PCN");
        values.put("pref_name", "Lodie");
        values.put("previousAddress", "9 Old Road\nApt 2");
        values.put("pronoun", "she/her");
        values.put("pronounId", "2");
        values.put("sourceOfIncome", "E");
        return Collections.unmodifiableMap(values);
    }

    private static Map<String, String> properties() {
        var values = new LinkedHashMap<String, String>();
        values.put("hPhoneExt", "1234");
        values.put("wPhoneExt", "123456");
        values.put("demo_cell", "613-555-0103");
        values.put("aboriginal", "Yes");
        values.put("ethnicity", "Métis");
        values.put("area", "East");
        values.put("cytolNum", "CY-77");
        values.put("given_consent", "1");
        values.put("rxInteractionWarningLevel", "2");
        values.put("primaryEMR", "CARLOS");
        values.put("phoneComment", "call after 5");
        values.put("usSigned", "signed");
        values.put("privacyConsent", "yes");
        values.put("informedConsent", "yes");
        values.put("paper_chart_archived", "true");
        values.put("paper_chart_archived_date", "2020-01-01");
        values.put("paper_chart_archived_program", "Main");
        values.put("HasPrimaryCarePhysician", "Yes");
        values.put("EmploymentStatus", "Employed");
        values.put("PHU", "Ottawa");
        values.put("statusNum", "1234567890");
        values.put("fNationCom", "Akwesasne");
        return Collections.unmodifiableMap(values);
    }
}
