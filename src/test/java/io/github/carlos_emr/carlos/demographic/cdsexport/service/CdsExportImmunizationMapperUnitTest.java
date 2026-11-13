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

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;

import static org.assertj.core.api.Assertions.*;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsWireSchema;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;

/** Native counterexamples: a valid transport or a nearby code never establishes missing clinical semantics. */
class CdsExportImmunizationMapperUnitTest {
    private final CdsExportImmunizationMapper mapper =
            new CdsExportImmunizationMapper(java.time.ZoneId.of("America/Toronto"));
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    private Map<String, String> parent;
    private List<CdsExportCapturedTextRowDto> extras, dates;
    private CdsExportPreventionClassifier definitions;

    @BeforeEach
    void prepare() throws Exception {
        parent = new TreeMap<>();
        CdsExportPatientTableReadSupport.Plan.PREVENTIONS_FULL
                .columns()
                .forEach(column -> parent.put(column, null));
        parent.putAll(
                Map.of(
                        "id",
                        "17",
                        "demographic_no",
                        "27",
                        "prevention_type",
                        "Td",
                        "prevention_date",
                        "2024-02-29 12:34:56",
                        "deleted",
                        "0",
                        "never",
                        "0",
                        "refused",
                        "0",
                        "creator",
                        "AUDIT_ACTOR",
                        "creation_date",
                        "2025-01-01 00:00:00",
                        "lastUpdateDate",
                        "2026-01-01 00:00:00"));
        extras = new ArrayList<>();
        dates = new ArrayList<>();
        definitions =
                CdsExportPreventionClassifier.fromCaptured(
                        "a".repeat(64),
                        "<preventions><item name='Td' layout='injection' healthCanadaType='Td'/><item name='H1N1' layout='h1n1' healthCanadaType='Inf'/><item name='Tuberculosis' layout='injection' headingName='Screenings'/><item name='OtherA' layout='injection' healthCanadaType=''/><item name='FutureVaccine' layout='injection' healthCanadaType='COVID-19'/></preventions>"
                                .getBytes(StandardCharsets.UTF_8),
                        List.of());
    }

    private CdsExportImmunizationMappingDto map() throws Exception {
        return mapper.map(
                27,
                new CdsExportCapturedTextRowDto(
                        "preventions", CdsExportPatientTableSourceReader.key(17), parent),
                extras,
                dates,
                definitions);
    }

    private void extra(int id, String key, String value) {
        var fields = new TreeMap<String, String>();
        fields.put("id", Integer.toString(id));
        fields.put("prevention_id", "17");
        fields.put("keyval", key);
        fields.put("val", value);
        extras.add(
                new CdsExportCapturedTextRowDto(
                        "preventionsExt", CdsExportPatientTableSourceReader.key(id), fields));
    }

    private void date(int id, String field, String format) {
        var fields = new TreeMap<String, String>();
        fields.putAll(
                Map.of(
                        "id",
                        Integer.toString(id),
                        "table_id",
                        "17",
                        "table_name",
                        "4",
                        "field_name",
                        field));
        fields.put("format", format);
        dates.add(
                new CdsExportCapturedTextRowDto(
                        "prevention_precision", CdsExportPatientTableSourceReader.key(id), fields));
    }

    @Test
    void shouldVerifyMapsExactNativeEventClock_insteadOfCreationOrImportClocks() throws Exception {
        var result = map();
        assertThat(result.nativeRowRepresentable()).isTrue();
        assertThat(result.fields()).hasSize(16);
        assertThat(result.value().name()).isEqualTo("Td");
        assertThat(result.value().type()).isEqualTo("Td");
        assertThat(result.value().date())
                .isEqualTo(
                        new CdsExportDateDto(
                                CdsExportDateDto.Precision.DATE_TIME, "2024-02-29T12:34:56-05:00"));
        assertThat(result.value().refused()).isFalse();
    }

    @Test
    void shouldVerifyAllNativeStandardExtensionsMap_withoutTrimmingOrChangingNullAndEmpty()
            throws Exception {
        extra(30, "name", " Brand café 😀 ");
        extra(31, "manufacture", "");
        extra(32, "lot", null);
        extra(33, "route", "IM");
        extra(34, "location", " Left arm ");
        extra(35, "dose", "0.50 mL");
        extra(36, "instructions", "Exact\r\nInstructions");
        extra(37, "comments", "Exact café 😀\r\nNotes ");
        extra(38, "din", "00123456");
        var result = map();
        assertThat(result.nativeRowRepresentable()).isTrue();
        assertThat(result.fields()).hasSize(52);
        var value = result.value();
        assertThat(value.name()).isEqualTo(" Brand café 😀 ");
        // The form saves an empty row for every unfilled input; it is not a fact (review F5),
        // so no empty element is written. The exact empty text stays in the residual.
        assertThat(value.manufacturer()).isNull();
        assertThat(value.lot()).isNull();
        assertThat(value.route()).isEqualTo("IM");
        assertThat(value.site()).isEqualTo(" Left arm ");
        assertThat(value.dose()).isEqualTo("0.50 mL");
        assertThat(value.instructions()).isEqualTo("Exact\r\nInstructions");
        assertThat(value.notes()).isEqualTo("Exact café 😀\r\nNotes ");
        assertThat(value.code()).isEqualTo("00123456");
        assertThat(value.codeSystem()).isEqualTo("DIN");
    }

    @Test
    void shouldExportAVaccineSavedByThePreventionForm_withItsEmptyInputRowsAndNativeContext()
            throws Exception {
        // AddPrevention2Action saves the provider, a reminder date and one extension row per
        // input whether filled or not (addHashtoArray), including a blank name. Review F5.
        parent.put("prevention_type", "FutureVaccine");
        parent.put("provider_no", "999998");
        parent.put("next_date", "2025-02-28");
        parent.put("snomedId", "28531000087107");
        int id = 30;
        for (String key :
                List.of(
                        "location2", "din", "result", "reason", "neverReason", "dosage", "product",
                        "workflowId", "formId", "name", "expiryDate", "providerName"))
            extra(id++, key, "");
        extra(id++, "location", "Left deltoid");
        extra(id++, "lot", "AB123");
        extra(id++, "route", "IM");
        extra(id++, "dose", "0.5 mL");
        extra(id++, "manufacture", "Example Biologics");
        extra(id++, "comments", "No reaction");
        extra(id++, "chronic", "on");
        extra(id, "brandSnomedId", "28571000087109");
        var result = map();
        assertThat(result.nativeRowRepresentable()).isTrue();
        assertThat(
                        result.fields().values().stream()
                                .filter(
                                        field ->
                                                field.outcome()
                                                        == CdsExportFieldAssessmentDto.Outcome
                                                                .REQUIRES_REVIEW))
                .isEmpty();
        var value = result.value();
        // The blank name row does not hide the configured type name.
        assertThat(value.name()).isEqualTo("FutureVaccine");
        assertThat(value.type()).isNull();
        assertThat(value.site()).isEqualTo("Left deltoid");
        assertThat(value.lot()).isEqualTo("AB123");
        assertThat(value.code()).isNull();
        assertThat(value.notes()).isEqualTo("No reaction");
        assertThat(value.sourceContext())
                .contains("\"provider_no\":\"999998\"", "\"brandSnomedId\"", "\"chronic\"")
                .doesNotContain(" ");
        String xml =
                new String(
                        CdsExportNativeHistoryMapperUnitTest.render(
                                CdsCategory.IMMUNIZATIONS,
                                body -> new CdsExportImmunizationXmlWriter().write(value, body)),
                        StandardCharsets.UTF_8);
        assertThat(xml)
                .contains("CARLOS.Prevention.Source.v1", "FutureVaccine", "Left deltoid")
                .doesNotContain("ImmunizationType");
        assertThat(xml.indexOf("ResidualInfo")).isLessThan(xml.indexOf("ImmunizationName"));
    }

    @Test
    void shouldRestoreTheOtherProviderAndReminderDate_whenTheCarlosImporterReadsTheExport()
            throws Exception {
        // #6198: the prevention form's "Other" provider (-1) with a typed name, and a reminder
        // date. CDS has no Immunizations element for either; the CARLOS importer restores both
        // from CARLOS.Prevention.Source.v1.
        parent.put("provider_no", "-1");
        parent.put("next_date", "2025-02-28");
        extra(30, "providerName", "FAKE-Other  clinician café");
        extra(31, "lot", "AB123");
        var value = map().value();
        assertThat(value.sourceContext()).doesNotContain(" ");
        var received = receive(value);
        assertThat(received.providerName()).isEqualTo("FAKE-Other  clinician café");
        assertThat(received.reminderDate()).isEqualTo("2025-02-28");
        assertThat(received.lot()).isEqualTo("AB123");
        assertThat(received.refused()).isFalse();
        // The importer writes the name as the form does, so the reconciler expects that row.
        assertThat(CdsExportImmunizationProjectionEvidence.expectedExtensions(received))
                .containsEntry("providerName", "FAKE-Other  clinician café");
        // A CARLOS provider number belongs to the sender and is never restored.
        parent.put("provider_no", "999998");
        parent.put("next_date", null);
        var chosen = receive(map().value());
        assertThat(chosen.providerName()).isNull();
        assertThat(chosen.reminderDate()).isNull();
    }

    /** Writes one vaccine with its residual, validates it and reads it with the importer. */
    private io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImmunizationDto receive(
            CdsExportImmunizationDto value) throws Exception {
        byte[] xml =
                CdsExportNativeHistoryMapperUnitTest.render(
                        CdsCategory.IMMUNIZATIONS,
                        body -> new CdsExportImmunizationXmlWriter().write(value, body));
        var folder = java.nio.file.Files.createTempDirectory(directory, "receive-");
        var file = java.nio.file.Files.write(folder.resolve("patient.xml"), xml);
        var records =
                new ArrayList<io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto>();
        new io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsXmlParser()
                .parse(
                        file,
                        folder.resolve("parsed"),
                        io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto
                                .defaults(),
                        () -> false,
                        records::add);
        return new io.github.carlos_emr.carlos.demographic.cdsimport.service
                        .CdsClinicalNormalizationService(
                        new io.github.carlos_emr.carlos.demographic.cdsimport.service
                                .CdsFieldParser())
                .immunization(records.get(1));
    }

    @Test
    void shouldCarryATypedTextThatIsNotAnEightDigitDin_insteadOfExportingOrRefusingIt()
            throws Exception {
        extra(30, "din", "1234567");
        var result = map();
        assertThat(result.value().code()).isNull();
        assertThat(result.value().sourceContext()).contains("1234567");
        assertThat(
                        result.fields().entrySet().stream()
                                .filter(entry -> entry.getKey().column().equals("val"))
                                .map(entry -> entry.getValue().rule()))
                .containsExactly("NATIVE_TEXT_IS_NOT_A_DIN_CARRIED");
    }

    @Test
    void shouldIgnoreTheSupersededVersionLink_ofAnEditedVaccine() throws Exception {
        extra(30, "previousId", "16");
        var result = map();
        assertThat(result.nativeRowRepresentable()).isTrue();
        assertThat(result.value().name()).isEqualTo("Td");
    }

    @Test
    void shouldVerifyExplicitRefusalRemainsTrue_forImmunizationMapperContract() throws Exception {
        parent.put("refused", "1");
        assertThat(map().value().refused()).isTrue();
    }

    @Test
    void shouldVerifyIneligibleAndCompletedExternallyAreNeverCollapsed_toNotRefused()
            throws Exception {
        // The prevention form saves 2 (ineligible) and 3 (completed externally); they no longer
        // hold the chart (review F5). Ineligible was not given, so it is never exported as given.
        parent.put("refused", "2");
        var ineligible = map().value();
        assertThat(ineligible.refused()).isTrue();
        assertThat(ineligible.notes()).isEqualTo("Ineligible");
        assertThat(decision(map(), "preventions", "refused").rule())
                .isEqualTo("INELIGIBLE_IS_NOT_ADMINISTERED");
        parent.put("refused", "3");
        extra(30, "comments", "Given at pharmacy");
        var external = map().value();
        assertThat(external.refused()).isFalse();
        assertThat(external.notes()).isEqualTo("Completed externally\nGiven at pharmacy");
        assertThat(external.sourceContext()).contains("\"refused\":\"3\"");
        extras.clear();
        for (String flag : Arrays.asList("x", null, "")) {
            parent.put("refused", flag);
            assertThat(map().value()).isNull();
            assertThat(decision(map(), "preventions", "refused").rule())
                    .isEqualTo("UNKNOWN_NATIVE_STATUS_NEEDS_RECONCILIATION");
        }
    }

    @Test
    void
            shouldVerifyArchivedAndUnknownArchiveStatesCannotBecomeActiveEvents_forImmunizationMapperContract()
                    throws Exception {
        // NULL left this list: a legacy NULL flag records no deletion, so the row is current
        // (see the next test). "1" reaches the mapper only for an imported occurrence whose row
        // was later edited or removed; the occurrence service skips native superseded versions.
        for (String flag : Arrays.asList("1", "x", "")) {
            parent.put("deleted", flag);
            assertThat(map().value()).isNull();
        }
        parent.put("deleted", "1");
        assertThat(decision(map(), "preventions", "deleted").rule())
                .isEqualTo("SUPERSEDED_OR_REMOVED_VERSION_IS_NOT_CURRENT");
    }

    @Test
    void shouldTreatALegacyNullDeletedFlag_asTheCurrentVersion() throws Exception {
        parent.put("deleted", null);
        var result = map();
        assertThat(result.value()).isNotNull();
        assertThat(decision(result, "preventions", "deleted").rule())
                .isEqualTo("NO_RECORDED_DELETION");
    }

    @Test
    void
            shouldVerifyDuplicateExtensionKeysRetainEveryRowAndNeverUseFirstOrLast_forImmunizationMapperContract()
                    throws Exception {
        extra(30, "comments", "First");
        extra(31, "comments", "Second");
        var result = map();
        assertThat(result.value()).isNull();
        assertThat(result.fields()).hasSize(24);
        assertThat(
                        result.fields().entrySet().stream()
                                .filter(
                                        entry ->
                                                entry.getKey().table().equals("preventionsExt")
                                                        && entry.getKey().column().equals("val")))
                .allSatisfy(
                        entry ->
                                assertThat(entry.getValue().outcome())
                                        .isEqualTo(
                                                CdsExportFieldAssessmentDto.Outcome
                                                        .REQUIRES_REVIEW));
    }

    @Test
    void
            shouldVerifyEvenEqualDuplicateCommentsRemainSeparateUnresolvedSourceRows_forImmunizationMapperContract()
                    throws Exception {
        extra(30, "comments", "Same");
        extra(31, "comments", "Same");
        assertThat(map().value()).isNull();
    }

    @Test
    void
            shouldVerifyConflictingLegacyAndImportedNamesCannotSilentlyChooseOne_forImmunizationMapperContract()
                    throws Exception {
        extra(30, "name", "Current Name");
        extra(31, "cdsName", "Old imported name");
        assertThat(map().value()).isNull();
    }

    @Test
    void
            shouldVerifyConflictingNativeTypeAndImportedTypeRequiresFieldReconciliation_forImmunizationMapperContract()
                    throws Exception {
        extra(30, "cdsType", "HB");
        assertThat(map().value()).isNull();
        assertThat(decision(map(), "preventions", "prevention_type").rule())
                .isEqualTo("CONFLICTING_NATIVE_IMMUNIZATION_TYPE");
    }

    @Test
    void shouldVerifyExplicitImportedDinNeedsItsPairedAuthority_forImmunizationMapperContract()
            throws Exception {
        extra(30, "cdsCode", "00123456");
        assertThat(map().value()).isNull();
        extra(31, "cdsCodeSystem", "DIN");
        assertThat(map().value().code()).isEqualTo("00123456");
    }

    @Test
    void
            shouldVerifyASnomedCodeOrEightDigitsCannotInventDinAuthority_forImmunizationMapperContract()
                    throws Exception {
        extra(30, "cdsCode", "00123456");
        extra(31, "cdsCodeSystem", "SNOMED");
        assertThat(map().value()).isNull();
    }

    @Test
    void shouldVerifyANativeSnomedIdNeverTurnsIntoStandardDin_forImmunizationMapperContract()
            throws Exception {
        parent.put("snomedId", "00123456");
        // The CVC concept is carried in the residual (review F5), never as a DIN.
        assertThat(map().value().code()).isNull();
        assertThat(decision(map(), "preventions", "snomedId").destination())
                .isEqualTo("ResidualInfo");
        assertThat(map().value().sourceContext()).contains("\"snomedId\":\"00123456\"");
    }

    @Test
    void shouldVerifyCompetingDinFieldsCannotChooseOneEven_whenBothAreEightDigits()
            throws Exception {
        extra(30, "din", "00123456");
        extra(31, "cdsCode", "00987654");
        extra(32, "cdsCodeSystem", "DIN");
        assertThat(map().value()).isNull();
    }

    @Test
    void shouldVerifyConfiguredH1n1UsesItsExplicitWireType_insteadOfAnUnlistedName()
            throws Exception {
        parent.put("prevention_type", "H1N1");
        assertThat(map().value().type()).isEqualTo("Inf");
    }

    @Test
    void
            shouldVerifyAFutureConfiguredVaccineDoesNotExtendTheOldWireEnumeration_forImmunizationMapperContract()
                    throws Exception {
        parent.put("prevention_type", "FutureVaccine");
        // A configured vaccine outside CT-020 (here COVID-19) is exported by name with no
        // ImmunizationType, its native type carried (review F5); the old list is not extended.
        assertThat(map().value().type()).isNull();
        assertThat(map().value().name()).isEqualTo("FutureVaccine");
        assertThat(decision(map(), "preventions", "prevention_type").rule())
                .isEqualTo("NATIVE_TYPE_OUTSIDE_CT020_CARRIED_IN_RESIDUAL");
        // An explicit imported wire type outside the list still needs review.
        extra(30, "cdsType", "COVID-19");
        assertThat(map().value()).isNull();
        assertThat(decision(map(), "preventions", "prevention_type").rule())
                .isEqualTo("NO_PINNED_WIRE_TYPE_EQUIVALENCE");
    }

    @Test
    void
            shouldVerifyMantouxAndAmbiguousOtherInjectionsNeverBecomeVaccinationEvents_forImmunizationMapperContract()
                    throws Exception {
        for (String type : List.of("Tuberculosis", "OtherA")) {
            parent.put("prevention_type", type);
            extra(30, "name", "Clinical name");
            assertThat(map().value()).isNull();
            extras.clear();
        }
    }

    @Test
    void shouldExportTheShippedCatchAllInjection_asAnUntypedImmunizationInsteadOfHolding()
            throws Exception {
        // A30: "Other immunization or injection" (OtherA, injection layout under Other) records
        // the vaccine fields; it is exported as Immunizations without ImmunizationType, which the
        // CARLOS importer maps back to OtherA. Every native row travels in the residual.
        definitions =
                CdsExportPreventionClassifier.fromCaptured(
                        "a".repeat(64),
                        "<preventions><item name='OtherA' layout='injection' headingName='Other' healthCanadaType=''/></preventions>"
                                .getBytes(StandardCharsets.UTF_8),
                        List.of());
        parent.put("prevention_type", "OtherA");
        extra(30, "name", "Vitamin B12");
        extra(31, "lot", "B12-77");
        var named = map();
        assertThat(named.nativeRowRepresentable()).isTrue();
        assertThat(named.value().name()).isEqualTo("Vitamin B12");
        assertThat(named.value().type()).isNull();
        assertThat(decision(named, "preventions", "prevention_type").rule())
                .isEqualTo("CATCH_ALL_INJECTION_AS_UNTYPED_IMMUNIZATION");
        String xml =
                new String(
                        CdsExportNativeHistoryMapperUnitTest.render(
                                CdsCategory.IMMUNIZATIONS,
                                body ->
                                        new CdsExportImmunizationXmlWriter()
                                                .write(named.value(), body)),
                        StandardCharsets.UTF_8);
        assertThat(xml).contains("Vitamin B12", "B12-77").doesNotContain("ImmunizationType");
        // The name box is optional: an unnamed entry is named by its label on the screen.
        extras.clear();
        extra(30, "name", "");
        var unnamed = map();
        assertThat(unnamed.nativeRowRepresentable()).isTrue();
        assertThat(unnamed.value().name()).isEqualTo(CdsExportImmunizationMapper.CATCH_ALL_LABEL);
        assertThat(unnamed.value().sourceContext()).contains("OtherA");
    }

    @Test
    void shouldMoveAnOverlongRecordedName_wholeToNotesInsteadOfHoldingTheVaccine()
            throws Exception {
        // Census chart FAKE-C2N-A31a, as the prevention form stored it: a 160-character brand
        // name (ImmunizationName holds 120) held the patient as CDS_EXPORT_UNMAPPED_DATA.
        String longName = "FAKE tetanus diphtheria booster ".repeat(5);
        String lot = "FAKE-LOT-" + "X".repeat(40);
        String dose = "0.5 mL given in the left deltoid after verbal consent ".repeat(2);
        parent.put("prevention_date", "2026-09-16 00:00:00");
        int id = 30;
        extra(id++, "location", "");
        extra(id++, "din", "");
        extra(id++, "lot", lot);
        extra(id++, "route", "IM");
        extra(id++, "dose", dose);
        extra(id++, "comments", "");
        extra(id++, "manufacture", "FAKE maker");
        extra(id++, "name", longName);
        extra(id++, "expiryDate", "");
        extra(id, "providerName", "");
        var result = map();
        assertThat(result.nativeRowRepresentable()).isTrue();
        var value = result.value();
        // The configured type names the vaccine, as when the name box is left empty.
        assertThat(value.name()).isEqualTo("Td");
        assertThat(value.type()).isEqualTo("Td");
        assertThat(value.lot()).isEqualTo(lot);
        assertThat(value.dose()).isEqualTo(dose);
        assertThat(value.manufacturer()).isEqualTo("FAKE maker");
        assertThat(value.notes()).isEqualTo("Name: " + longName);
        assertThat(value.sourceContext()).contains("FAKE\\u0020tetanus");
        var moved =
                result.fields().entrySet().stream()
                        .filter(
                                entry ->
                                        CdsExportImmunizationMapper.OVERLONG_MOVED_TO_NOTES.equals(
                                                entry.getValue().rule()))
                        .toList();
        assertThat(moved).singleElement().satisfies(entry -> {
            assertThat(entry.getKey().column()).isEqualTo("val");
            assertThat(entry.getValue().outcome())
                    .isEqualTo(CdsExportFieldAssessmentDto.Outcome.MAPPED);
            assertThat(entry.getValue().destination()).isEqualTo("Notes;ResidualInfo");
        });
        String xml =
                new String(
                        CdsExportNativeHistoryMapperUnitTest.render(
                                CdsCategory.IMMUNIZATIONS,
                                body -> new CdsExportImmunizationXmlWriter().write(value, body)),
                        StandardCharsets.UTF_8);
        assertThat(xml).contains("Notes>Name: " + longName + "</", lot, dose);
    }

    @Test
    void shouldPlaceEveryOverlongValue_afterTheStatusAndBeforeTheComments() throws Exception {
        parent.put("refused", "3");
        String lot = "L".repeat(121), instructions = "I".repeat(251);
        extra(30, "lot", lot);
        extra(31, "dose", "0.5 mL");
        extra(32, "instructions", instructions);
        extra(33, "comments", "Given at pharmacy");
        var value = map().value();
        assertThat(value).isNotNull();
        assertThat(value.lot()).isNull();
        assertThat(value.instructions()).isNull();
        assertThat(value.dose()).isEqualTo("0.5 mL");
        assertThat(value.notes())
                .isEqualTo(
                        "Completed externally\nLot number: "
                                + lot
                                + "\nInstructions: "
                                + instructions
                                + "\nGiven at pharmacy");
        // A value at the element's own limit still maps to it.
        extras.clear();
        extra(30, "lot", "L".repeat(120));
        assertThat(map().value().lot()).hasSize(120);
        // A catch-all entry whose long name moved is named by its label on the screen.
        definitions =
                CdsExportPreventionClassifier.fromCaptured(
                        "a".repeat(64),
                        "<preventions><item name='OtherA' layout='injection' headingName='Other' healthCanadaType=''/></preventions>"
                                .getBytes(StandardCharsets.UTF_8),
                        List.of());
        parent.put("prevention_type", "OtherA");
        parent.put("refused", "0");
        extras.clear();
        extra(30, "name", "N".repeat(121));
        var other = map().value();
        assertThat(other.name()).isEqualTo(CdsExportImmunizationMapper.CATCH_ALL_LABEL);
        assertThat(other.notes()).isEqualTo("Name: " + "N".repeat(121));
    }

    @Test
    void shouldStillHoldMovedTextThatNotesCannotHold_withTheNotesCapacityRule() throws Exception {
        extra(30, "name", "N".repeat(121));
        extra(31, "comments", "C".repeat(31_900));
        var result = map();
        assertThat(result.value()).isNull();
        assertThat(
                        result.fields().values().stream()
                                .filter(
                                        field ->
                                                field.outcome()
                                                        == CdsExportFieldAssessmentDto.Outcome
                                                                .REQUIRES_REVIEW)
                                .map(CdsExportFieldAssessmentDto::rule)
                                .distinct())
                .containsExactly("IMMUNIZATION_NOTES_CAPACITY_REQUIRES_REVIEW");
    }

    @Test
    void shouldExportAnRhImmuneGlobulinInjection_asAnUntypedImmunization() throws Exception {
        // The Rh Immune Globulin form (addRhInjection.jsp) posts type RH with product, lot,
        // dosage and reason, and no name box.
        parent.put("prevention_type", "RH");
        extra(30, "product", "WinRho SDF");
        extra(31, "lot", "RH-123");
        extra(32, "dosage", "300");
        extra(33, "reason", "28 weeks");
        var result = map();
        assertThat(result.nativeRowRepresentable()).isTrue();
        assertThat(result.value().name()).isEqualTo(CdsExportImmunizationMapper.RH_LABEL);
        assertThat(result.value().type()).isNull();
        assertThat(result.value().lot()).isEqualTo("RH-123");
        assertThat(result.value().sourceContext()).contains("WinRho", "28\\u0020weeks");
        assertThat(decision(result, "preventions", "prevention_type").rule())
                .isEqualTo("RH_IMMUNE_GLOBULIN_AS_UNTYPED_IMMUNIZATION");
        CdsExportNativeHistoryMapperUnitTest.render(
                CdsCategory.IMMUNIZATIONS,
                body -> new CdsExportImmunizationXmlWriter().write(result.value(), body));
    }

    @Test
    void
            shouldVerifyYearAndMonthMarkersNeverInventAClinicalMonthDayOrClock_forImmunizationMapperContract()
                    throws Exception {
        date(30, "7", "YYYY");
        assertThat(map().value().date())
                .isEqualTo(new CdsExportDateDto(CdsExportDateDto.Precision.YEAR, "2024"));
        dates.clear();
        date(30, "7", "YYYY-MM");
        assertThat(map().value().date())
                .isEqualTo(new CdsExportDateDto(CdsExportDateDto.Precision.YEAR_MONTH, "2024-02"));
    }

    @Test
    void
            shouldVerifyUnknownDateStaysAbsentAndCannotUseCreationOrModificationDates_forImmunizationMapperContract()
                    throws Exception {
        parent.put("prevention_date", null);
        assertThat(map().value().date().precision()).isEqualTo(CdsExportDateDto.Precision.ABSENT);
        date(30, "7", "YYYY");
        assertThat(map().value()).isNull();
    }

    @Test
    void
            shouldVerifyDuplicateOrUnsupportedEventPrecisionRemainsReview_forImmunizationMapperContract()
                    throws Exception {
        date(30, "7", "YYYY");
        date(31, "7", "YYYY");
        assertThat(map().value()).isNull();
        dates.clear();
        date(30, "7", "YYYY-MM-DD");
        assertThat(map().value()).isNull();
    }

    @Test
    void
            shouldVerifyNextDateAndUnknownPrecisionFieldsCannotBeMergedIntoEventDate_forImmunizationMapperContract()
                    throws Exception {
        for (String field : List.of("8", "999")) {
            date(30, field, "YYYY");
            assertThat(map().value()).isNull();
            dates.clear();
        }
    }

    @Test
    void
            shouldVerifyUnknownAndStaleImportedExtensionsCannotBeIgnoredOrOverrideCurrentEvent_forImmunizationMapperContract()
                    throws Exception {
        // Import provenance on a row the reconciler did not claim is a stale hint (review).
        for (String key : List.of("cdsDateOriginal", "cdsDatePrecision")) {
            extra(30, key, "2020");
            assertThat(map().value()).isNull();
            extras.clear();
        }
        // Any other native key is carried exactly and cannot change the event (review F5).
        extra(30, "customClinicalValue", "2020");
        var carried = map().value();
        assertThat(carried.date()).isEqualTo(map().value().date());
        assertThat(carried.sourceContext()).contains("\"customClinicalValue\"");
    }

    @Test
    void
            shouldVerifyAdministeringCliniciansDueDatesReminderSuppressionAndProgramAffiliationRemainExplicit_forImmunizationMapperContract()
                    throws Exception {
        // These have no CDS Immunizations element; they are carried exactly in the residual
        // instead of holding the chart (review F5).
        for (String column :
                List.of("provider_no", "provider_name", "next_date", "never", "programNo")) {
            String prior =
                    parent.put(column, column.equals("never") ? "1" : "Recorded native value");
            // A program on an explicitly unrestricted row; a restriction still needs review.
            parent.put("restrictToProgram", column.equals("programNo") ? "0" : null);
            var result = map();
            assertThat(result.nativeRowRepresentable()).isTrue();
            assertThat(decision(result, "preventions", column).destination())
                    .isEqualTo("ResidualInfo");
            assertThat(result.value().sourceContext())
                    .contains(column.equals("never") ? "\"never\":\"1\"" : "Recorded\\u0020native");
            parent.put(column, prior);
        }
        parent.put("restrictToProgram", null);
        parent.put("programNo", "10034");
        assertThat(map().value()).isNull();
    }

    private CdsExportImmunizationMappingDto map(java.util.function.Predicate<String> readable)
            throws Exception {
        return mapper.map(
                27,
                new CdsExportCapturedTextRowDto(
                        "preventions", CdsExportPatientTableSourceReader.key(17), parent),
                extras,
                dates,
                definitions,
                readable);
    }

    // OPEN-6: a vaccine restricted to a program is exported when a fresh check says every
    // exporting identity works in it, and its flag and program travel exactly in the residual.
    @Test
    void shouldExportARestrictedVaccine_whenItsProgramIsReadableLive() throws Exception {
        parent.put("restrictToProgram", "1");
        parent.put("programNo", "10034");
        var asked = new ArrayList<String>();
        var result =
                map(
                        program -> {
                            asked.add(program);
                            return true;
                        });
        assertThat(asked).containsExactly("10034");
        assertThat(result.nativeRowRepresentable()).isTrue();
        for (String column : List.of("restrictToProgram", "programNo")) {
            var field = decision(result, "preventions", column);
            assertThat(field.outcome()).isEqualTo(CdsExportFieldAssessmentDto.Outcome.MAPPED);
            assertThat(field.destination()).isEqualTo("ResidualInfo");
            assertThat(field.rule()).isEqualTo("PROGRAM_SCOPE_AUTHORIZED");
        }
        assertThat(result.value().sourceContext())
                .contains("\"restrictToProgram\":\"1\"", "\"programNo\":\"10034\"");
        // A legacy NULL flag beside a program is restricted as well.
        parent.put("restrictToProgram", null);
        assertThat(map(program -> true).nativeRowRepresentable()).isTrue();
    }

    @Test
    void shouldHoldARestrictedVaccine_whenItsProgramIsNotReadableLive() throws Exception {
        parent.put("restrictToProgram", "1");
        parent.put("programNo", "10034");
        var result = map(program -> false);
        assertThat(result.value()).isNull();
        assertThat(decision(result, "preventions", "restrictToProgram").rule())
                .isEqualTo("PROGRAM_SCOPE_REQUIRES_REVIEW");
    }

    @Test
    void shouldTreatAFlagWithoutAProgramOrAZeroFlag_asUnrestrictedWithoutAskingAnyProgram()
            throws Exception {
        for (var scope :
                Arrays.asList(
                        new String[] {"1", null},
                        new String[] {"1", "-1"},
                        new String[] {"0", "10034"})) {
            parent.put("restrictToProgram", scope[0]);
            parent.put("programNo", scope[1]);
            var result =
                    map(
                            program -> {
                                throw new AssertionError("No program to check");
                            });
            assertThat(result.nativeRowRepresentable()).isTrue();
            assertThat(decision(result, "preventions", "restrictToProgram").rule())
                    .isEqualTo("UNRESTRICTED_PROGRAM_SCOPE");
        }
    }

    @Test
    void shouldHoldAMalformedRestrictionFlag_evenWhenEveryProgramIsReadable() throws Exception {
        for (String program : Arrays.asList("10034", null)) {
            parent.put("restrictToProgram", "2");
            parent.put("programNo", program);
            var result = map(ignored -> true);
            assertThat(result.value()).isNull();
            assertThat(decision(result, "preventions", "restrictToProgram").rule())
                    .isEqualTo("PROGRAM_SCOPE_REQUIRES_REVIEW");
        }
    }

    @Test
    void
            shouldVerifyExactDictionaryNoteLimitCountsCodePointsAndNeverTruncates_forImmunizationMapperContract()
                    throws Exception {
        extra(30, "comments", "😀".repeat(32000));
        assertThat(map().value().notes().codePointCount(0, map().value().notes().length()))
                .isEqualTo(32000);
        extras.clear();
        extra(30, "comments", "😀".repeat(32001));
        assertThat(map().value()).isNull();
    }

    @Test
    void
            shouldVerifyForeignParentsChildrenPrecisionAndDuplicatePhysicalKeysAreScopeErrors_forImmunizationMapperContract() {
        parent.put("demographic_no", "28");
        assertThatThrownBy(this::map).hasMessage("CDS_EXPORT_IMMUNIZATION_MAPPING_SCOPE_INVALID");
        parent.put("demographic_no", "27");
        extra(30, "comments", "One");
        extras.add(extras.getFirst());
        assertThatThrownBy(this::map).hasMessage("CDS_EXPORT_IMMUNIZATION_MAPPING_SCOPE_INVALID");
    }

    @Test
    void
            shouldVerifyNullAndEmptyNativeExtensionValuesHaveDifferentFieldPresence_forImmunizationMapperContract()
                    throws Exception {
        extra(30, "lot", null);
        extra(31, "manufacture", "");
        var result = map();
        // Neither becomes an element (review F5); presence still distinguishes them.
        assertThat(result.value().manufacturer()).isNull();
        assertThat(result.value().lot()).isNull();
        assertThat(
                        result.fields().entrySet().stream()
                                .filter(
                                        entry ->
                                                entry.getKey().table().equals("preventionsExt")
                                                        && entry.getKey().column().equals("val"))
                                .map(entry -> entry.getValue().presence())
                                .toList())
                .containsExactlyInAnyOrder(
                        CdsExportFieldAssessmentDto.Presence.SQL_NULL,
                        CdsExportFieldAssessmentDto.Presence.EMPTY);
    }

    @Test
    void
            shouldVerifyWireVocabularyExactlyMatchesTheHashPinnedOriginalSchema_forImmunizationMapperContract()
                    throws Exception {
        var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var document =
                factory.newDocumentBuilder()
                        .parse(new ByteArrayInputStream(CdsWireSchema.datatypeBytes()));
        var nodes =
                document.getElementsByTagNameNS(
                        javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI, "simpleType");
        Set<String> actual = new HashSet<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            var element = (org.w3c.dom.Element) nodes.item(i);
            if (!element.getAttribute("name").equals("immunizationType")) continue;
            var enumerations =
                    element.getElementsByTagNameNS(
                            javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI, "enumeration");
            for (int j = 0; j < enumerations.getLength(); j++)
                actual.add(((org.w3c.dom.Element) enumerations.item(j)).getAttribute("value"));
        }
        assertThat(CdsExportImmunizationVocabulary.TYPES)
                .containsExactlyInAnyOrderElementsOf(actual);
        assertThat(actual).doesNotContain("H1N1", "COVID-19");
    }

    @Test
    void
            shouldVerifyProtectedIntermediateObjectsDoNotPrintClinicalNamesOrValues_forImmunizationMapperContract()
                    throws Exception {
        extra(30, "name", "PRIVATE NAME");
        assertThat(map().toString()).isEqualTo("CdsExportImmunizationMappingDto[protected]");
        assertThat(definitions.definition("Td").orElseThrow().toString())
                .isEqualTo("CdsExportPreventionClassifier.Definition[protected]");
    }

    private static CdsExportFieldAssessmentDto decision(
            CdsExportImmunizationMappingDto result, String table, String column) {
        return result.fields().entrySet().stream()
                .filter(
                        entry ->
                                entry.getKey().table().equals(table)
                                        && entry.getKey().column().equals(column))
                .findFirst()
                .orElseThrow()
                .getValue();
    }
}
