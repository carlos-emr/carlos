/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.AdmissionDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicContactDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicCustDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicPharmacyDao;
import io.github.carlos_emr.carlos.commn.dao.PharmacyInfoDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalContactDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicExt;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDemographicDocumentFixture;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsClinicalDiagnosticDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsDemographicNativeDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsIdentityDecisionDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsPatientIdentityDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsProviderMatchDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

/**
 * Demographic values no Demographics element can hold exactly, sent from one CARLOS to another:
 * the exporter's own document is parsed by the importer, whose persister validates, prepares and
 * writes the new chart. The receiving chart must hold the stored values the sender's did.
 */
@Tag("unit")
@Tag("cds-import")
class CdsDemographicCarrierRoundTripUnitTest extends CarlosUnitTestBase {
    @TempDir Path directory;
    private final CdsFieldParser fields = new CdsFieldParser();
    private CdsNativePreparationService preparation;
    private DemographicDao demographicDao;
    private DemographicExtDao extensionsDao;
    private CdsDemographicPersister persister;
    private CdsImportCommand command;

    @BeforeEach
    void setUp() {
        preparation = mock(CdsNativePreparationService.class);
        demographicDao = mock(DemographicDao.class);
        extensionsDao = mock(DemographicExtDao.class);
        var providers = mock(CdsProviderResolver.class);
        when(providers.resolve(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(
                        new CdsProviderMatchDto(
                                null, CdsProviderMatchDto.Outcome.SOURCE_IDENTITY_ABSENT));
        // The real source reading; only the database lookup of an existing chart is replaced.
        var identity =
                new CdsPatientIdentityResolver(fields) {
                    @Override
                    public CdsIdentityDecisionDto resolve(
                            CdsPatientIdentityDto source, CdsImportCommand ignored) {
                        return new CdsIdentityDecisionDto(
                                CdsIdentityDecisionDto.Outcome.NO_MATCH, null);
                    }
                };
        persister =
                new CdsDemographicPersister(
                        fields,
                        identity,
                        providers,
                        preparation,
                        new CdsDemographicDetailsService(fields, providers),
                        new CdsDemographicRelationsService(
                                fields,
                                mock(ProfessionalContactDao.class),
                                mock(DemographicContactDao.class),
                                mock(PharmacyInfoDao.class),
                                mock(DemographicPharmacyDao.class)),
                        demographicDao,
                        mock(DemographicCustDao.class),
                        extensionsDao,
                        mock(AdmissionDao.class));
        command =
                new CdsImportCommand(
                        directory,
                        "owned-submission",
                        "999998",
                        null,
                        null,
                        "clinic",
                        "pcon-2024-02",
                        false,
                        false,
                        true,
                        0,
                        null,
                        false);
    }

    /** The chart the receiving CARLOS writes, and its extension rows. */
    private record Imported(
            Demographic patient,
            List<DemographicExt> properties,
            CdsDemographicNativeDto prepared,
            List<CdsClinicalDiagnosticDto> notices) {}

    private Imported transfer(Map<String, String> row, List<CdsExportCapturedTextRowDto> rows)
            throws Exception {
        return transfer(CdsExportDemographicDocumentFixture.document(row, rows));
    }

    private Imported transfer(byte[] document) throws Exception {
        var record = demographics(document);
        org.mockito.Mockito.clearInvocations(preparation, demographicDao, extensionsDao);
        var stored = new AtomicReference<CdsDemographicNativeDto>();
        doAnswer(
                        call -> {
                            stored.set(call.getArgument(5));
                            return null;
                        })
                .when(preparation)
                .store(any(), org.mockito.ArgumentMatchers.anyLong(), any(), any(), any(), any());
        persister.validate(record, command);
        persister.prepare("run", 1, "unit", record, command);
        when(preparation.load(
                        eq("run"),
                        eq(1L),
                        eq("unit"),
                        any(),
                        eq(command),
                        eq(CdsDemographicNativeDto.class)))
                .thenReturn(stored.get());
        doAnswer(
                        call -> {
                            ((Demographic) call.getArgument(0)).setDemographicNo(42);
                            return null;
                        })
                .when(demographicDao)
                .persistImportedDemographic(any());
        var ids = new AtomicInteger(10);
        doAnswer(
                        call -> {
                            ((DemographicExt) call.getArgument(0)).setId(ids.incrementAndGet());
                            return null;
                        })
                .when(extensionsDao)
                .persist(any());
        var notices = new ArrayList<CdsClinicalDiagnosticDto>();
        persister.write("run", 1, "unit", record, command, notices::add);
        var patients = ArgumentCaptor.forClass(Demographic.class);
        verify(demographicDao, org.mockito.Mockito.atLeastOnce())
                .persistImportedDemographic(patients.capture());
        var extensions = ArgumentCaptor.forClass(DemographicExt.class);
        verify(extensionsDao, org.mockito.Mockito.atLeast(0)).persist(extensions.capture());
        return new Imported(
                patients.getValue(), extensions.getAllValues(), stored.get(), notices);
    }

    private CdsRecordDto demographics(byte[] document) throws Exception {
        Path root = Files.createTempDirectory(directory, "demographic-");
        var records = new ArrayList<CdsRecordDto>();
        new CdsXmlParser()
                .parse(
                        Files.write(root.resolve("patient.xml"), document),
                        root.resolve("parsed"),
                        CdsInputLimitsDto.defaults(),
                        () -> false,
                        records::add);
        return records.stream()
                .filter(record -> record.section().equals("Demographics"))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void shouldRestoreMiddleNamesLongerThanANamePart_fromTheDocumentTheExporterWrites()
            throws Exception {
        // Before: a single middle name longer than 50 characters held the chart
        // (WIRE_LENGTH_REQUIRES_REVIEW). The part holds its first 50 characters.
        for (String middle :
                List.of(
                        "Anne " + "x".repeat(60),
                        "y".repeat(51),
                        "Marie-Claire  Anne Josephine Elisabeth Margaret Victoria")) {
            var row = CdsExportDemographicDocumentFixture.row();
            row.put("middleNames", middle);
            var imported = transfer(row, List.of());
            assertThat(imported.patient().getMiddleNames()).isEqualTo(middle);
            assertThat(imported.prepared().carried()).containsEntry("middleNames", middle);
        }
    }

    @Test
    void shouldRestoreTheStoredIdentity_whenTheRequiredElementsHoldTheirDocumentedValues()
            throws Exception {
        // The REST service and the PHS/STAR feed save names, sex, status and birth date as they
        // are sent: an empty first name, a blank last name, no sex, no status and a birth date
        // of February 30. The document holds the empty Part, Gender U, an empty plain-text
        // status and 0001-01-01; the receiving chart holds the stored values.
        var row = CdsExportDemographicDocumentFixture.row();
        row.put("first_name", "");
        row.put("last_name", "  Doe ");
        row.put("sex", "");
        row.put("patient_status", "");
        row.put("month_of_birth", "2");
        row.put("date_of_birth", "30");
        var imported = transfer(row, List.of());
        var patient = imported.patient();
        assertThat(patient.getFirstName()).isEmpty();
        assertThat(patient.getLastName()).isEqualTo("  Doe ");
        assertThat(patient.getSex()).isEmpty();
        assertThat(patient.getPatientStatus()).isEmpty();
        assertThat(patient.getYearOfBirth()).isEqualTo("1980");
        assertThat(patient.getMonthOfBirth()).isEqualTo("2");
        assertThat(patient.getDateOfBirth()).isEqualTo("30");
        assertThat(imported.notices())
                .extracting(CdsClinicalDiagnosticDto::code)
                .contains(CdsDemographicCarriedValuesResolver.RESTORED)
                .doesNotContain(CdsDemographicCarriedValuesResolver.NOT_RESTORED);
    }

    @Test
    void shouldRestoreCodesAndTextNoElementHoldsExactly_fromTheDocumentTheExporterWrites()
            throws Exception {
        var row = CdsExportDemographicDocumentFixture.row();
        row.put("sex", "T");
        row.put("title", "DR");
        row.put("patient_status", "Away\u0001");
        row.put("first_name", "Anne\u0007");
        row.put("phone", "905\u0007555");
        var imported = transfer(row, List.of());
        var patient = imported.patient();
        assertThat(patient.getSex()).isEqualTo("T");
        assertThat(patient.getTitle()).isEqualTo("DR");
        assertThat(patient.getPatientStatus()).isEqualTo("Away\u0001");
        assertThat(patient.getFirstName()).isEqualTo("Anne\u0007");
        assertThat(patient.getPhone()).isEqualTo("905\u0007555");
        // An unlisted title travels the same way.
        row.put("title", "Mx");
        assertThat(transfer(row, List.of()).patient().getTitle()).isEqualTo("Mx");
    }

    @Test
    void shouldRestoreAHealthCardTheExporterLeftOut_andAVersionLongerThanTheElement()
            throws Exception {
        // A version and issuer typed without a number: the card is left out of the document.
        var row = CdsExportDemographicDocumentFixture.row();
        row.put("hin", "");
        row.put("ver", "AB");
        row.put("hc_type", "ON");
        row.put("hc_renew_date", "2030-01-31");
        var withoutNumber = transfer(row, List.of()).patient();
        assertThat(withoutNumber.getVer()).isEqualTo("AB");
        assertThat(withoutNumber.getHcType()).isEqualTo("ON");
        assertThat(withoutNumber.getHcRenewDate()).isNotNull();
        // A three-character version: the card is written without Version.
        row.put("hin", "1234567890");
        row.put("ver", "ABC");
        row.put("hc_renew_date", null);
        var version = transfer(row, List.of()).patient();
        assertThat(version.getHin()).isEqualTo("1234567890");
        assertThat(version.getVer()).isEqualTo("ABC");
    }

    @Test
    void shouldRestoreOptionalValuesTheExporterLeftOutOrNormalized_fromTheDocumentTheExporterWrites()
            throws Exception {
        String street = "1 Long Street Name Beyond The Fifty Character Line1 X";
        assertThat(street.length()).isBetween(51, 60);
        var row = CdsExportDemographicDocumentFixture.row();
        row.put("chart_no", "C\u0000");
        row.put("email", "synthetic+tag@example.ca");
        row.put("sin", "123 456 789");
        row.put("official_lang", "ENG");
        row.put("spoken_lang", "Not a language");
        row.put("address", street);
        row.put("city", "Toronto  West");
        row.put("province", "ON");
        row.put("postal", "M5V 2T6");
        row.put("residentialProvince", "OT");
        row.put("residentialPostal", "12345");
        var patient = transfer(row, List.of()).patient();
        assertThat(patient.getChartNo()).isEqualTo("C\u0000");
        assertThat(patient.getEmail()).isEqualTo("synthetic+tag@example.ca");
        assertThat(patient.getSin()).isEqualTo("123 456 789");
        assertThat(patient.getOfficialLanguage()).isEqualTo("ENG");
        assertThat(patient.getSpokenLanguage()).isEqualTo("Not a language");
        assertThat(patient.getAddress()).isEqualTo(street);
        assertThat(patient.getCity()).isEqualTo("Toronto  West");
        assertThat(patient.getProvince()).isEqualTo("ON");
        assertThat(patient.getPostal()).isEqualTo("M5V 2T6");
        assertThat(patient.getResidentialProvince()).isEqualTo("OT");
        assertThat(patient.getResidentialPostal()).isEqualTo("12345");
        // A language stored as its code is written as that code; the importer would store the
        // language's name, so the code travels too.
        for (String spoken : List.of("eng", "fre", "fra")) {
            row.put("spoken_lang", spoken);
            assertThat(transfer(row, List.of()).patient().getSpokenLanguage()).isEqualTo(spoken);
        }
    }

    @Test
    void shouldRestoreValuesAnImportWouldStoreInAnotherForm_fromTheDocumentTheExporterWrites()
            throws Exception {
        // The element holds each value as before; without the record the receiving chart would
        // hold 1234567890, ON, A, 905 555 0100, ON and MO.
        var row = CdsExportDemographicDocumentFixture.row();
        row.put("hin", "1234 567 890");
        row.put("hc_type", "CA-ON");
        row.put("ver", " A");
        row.put("phone", "905  555 0100");
        row.put("address", "1 Main St");
        row.put("province", "CA-ON");
        row.put("patient_status", "Moved");
        var imported = transfer(row, List.of());
        var patient = imported.patient();
        assertThat(patient.getHin()).isEqualTo("1234 567 890");
        assertThat(patient.getHcType()).isEqualTo("CA-ON");
        assertThat(patient.getVer()).isEqualTo(" A");
        assertThat(patient.getPhone()).isEqualTo("905  555 0100");
        assertThat(patient.getProvince()).isEqualTo("CA-ON");
        assertThat(patient.getPatientStatus()).isEqualTo("Moved");
        assertThat(imported.notices())
                .extracting(CdsClinicalDiagnosticDto::code)
                .doesNotContain(CdsDemographicCarriedValuesResolver.NOT_RESTORED);
    }

    @Test
    void shouldKeepAnElementChangedAfterTheExport_andReportTheCarriedValueNotRestored()
            throws Exception {
        // A carried value is restored only over the element the exporter wrote from it. When the
        // document's element was changed afterwards, the element's value stands.
        var row = CdsExportDemographicDocumentFixture.row();
        row.put("official_lang", "ENG");
        row.put("chart_no", "  C1");
        String document =
                new String(
                        CdsExportDemographicDocumentFixture.document(row, List.of()),
                        java.nio.charset.StandardCharsets.UTF_8);
        assertThat(document).containsOnlyOnce(">ENG<").containsOnlyOnce(">C1<");
        var imported =
                transfer(
                        document.replace(">ENG<", ">FRE<")
                                .replace(">C1<", ">C2<")
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(imported.patient().getOfficialLanguage()).isEqualTo("French");
        assertThat(imported.patient().getChartNo()).isEqualTo("C2");
        assertThat(imported.notices())
                .filteredOn(
                        notice ->
                                notice.code().equals(CdsDemographicCarriedValuesResolver.NOT_RESTORED))
                .extracting(CdsClinicalDiagnosticDto::field)
                .containsExactlyInAnyOrder("PreferredOfficialLanguage", "ChartNumber");
    }

    @Test
    void shouldRestoreHiddenKeylessAndCarriedPropertyRows_asTheSenderStoredThem()
            throws Exception {
        // Before: a hidden row or a value without a key held the chart
        // (HIDDEN_OR_UNKNOWN_PROPERTY_HISTORY_REQUIRES_REVIEW, UNMAPPED_NATIVE_DEMOGRAPHIC_PROPERTY).
        var row = CdsExportDemographicDocumentFixture.row();
        row.put("phone", "905-555-0100");
        var hidden =
                CdsExportDemographicDocumentFixture.with(
                        CdsExportDemographicDocumentFixture.property(1, "hPhoneExt", "12"),
                        "hidden",
                        "1");
        var keyless = CdsExportDemographicDocumentFixture.property(2, null, "Additional value");
        var ethnicity = CdsExportDemographicDocumentFixture.property(3, "ethnicity", "Other");
        var imported = transfer(row, List.of(hidden, keyless, ethnicity));
        assertThat(imported.properties())
                .extracting(DemographicExt::getKey, DemographicExt::getValue, DemographicExt::isHidden)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("hPhoneExt", "12", true),
                        org.assertj.core.groups.Tuple.tuple(null, "Additional value", false),
                        org.assertj.core.groups.Tuple.tuple("ethnicity", "Other", false));
    }

    @Test
    void shouldKeepTheRowTheChartShows_andReportAnotherRowOfARepeatedPhoneKey()
            throws Exception {
        // The sender's table has two hPhoneExt rows (adopted without the one-row-per-key index).
        // The shown row is the element's extension; CARLOS keeps one row per key, so the other
        // row is reported and stays in the import's retained record.
        var row = CdsExportDemographicDocumentFixture.row();
        row.put("phone", "905-555-0100");
        var older = CdsExportDemographicDocumentFixture.property(1, "hPhoneExt", "12");
        var later =
                CdsExportDemographicDocumentFixture.with(
                        CdsExportDemographicDocumentFixture.property(2, "hPhoneExt", "34"),
                        "date_time",
                        "2024-02-01 00:00:00");
        var imported = transfer(row, List.of(older, later));
        assertThat(imported.properties())
                .extracting(DemographicExt::getKey, DemographicExt::getValue)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("hPhoneExt", "34"));
        assertThat(imported.notices())
                .extracting(CdsClinicalDiagnosticDto::code)
                .contains(CdsDemographicCarriedValuesResolver.NOT_RESTORED);
    }

    @Test
    void shouldRestoreNothingFromAnEarlierSendersRecord_whenTheExportingChartCarriesNothing()
            throws Exception {
        // A chart imported from another CARLOS re-exports that sender's native demographic record
        // and then its own, which carries nothing here (the name was edited after the import).
        // The earlier record's "Synthetic " would prove itself against the element; the
        // receiving chart holds the name the exporting chart holds.
        var earlier = CdsExportDemographicDocumentFixture.row();
        earlier.put("first_name", "Synthetic ");
        String document =
                new String(
                        CdsExportDemographicDocumentFixture.document(
                                CdsExportDemographicDocumentFixture.row(), List.of(), earlier),
                        java.nio.charset.StandardCharsets.UTF_8);
        assertThat(document.split("CARLOS.NativeDemographic.v1", -1)).hasSize(3);
        var imported = transfer(document.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(imported.patient().getFirstName()).isEqualTo("Synthetic");
        assertThat(imported.prepared().carried()).isEmpty();
        assertThat(imported.notices())
                .extracting(CdsClinicalDiagnosticDto::code)
                .doesNotContain(
                        CdsDemographicCarriedValuesResolver.RESTORED,
                        CdsDemographicCarriedValuesResolver.NOT_RESTORED);
        // As the exporting chart's own record, the same value is restored.
        assertThat(transfer(earlier, List.of()).patient().getFirstName()).isEqualTo("Synthetic ");
    }

    @Test
    void shouldImportTheElements_whenTheSenderCarriesNothing() throws Exception {
        var imported = transfer(CdsExportDemographicDocumentFixture.row(), List.of());
        assertThat(imported.prepared().carried()).isEmpty();
        assertThat(imported.properties()).isEmpty();
        assertThat(imported.patient().getFirstName()).isEqualTo("Synthetic");
        assertThat(imported.notices())
                .extracting(CdsClinicalDiagnosticDto::code)
                .doesNotContain(
                        CdsDemographicCarriedValuesResolver.RESTORED,
                        CdsDemographicCarriedValuesResolver.NOT_RESTORED);
    }
}
