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
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsDemographicDetailsDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.*;
import org.w3c.dom.Document;

@Tag("unit")
@Tag("cds-export")
class CdsExportRetainedEnrolmentReconcilerUnitTest {
    private static final String HASH = "a".repeat(64);
    private CdsExportDemographicProjectionEvidenceUnitTest fixture;
    private final Map<String, String> source = new LinkedHashMap<>();
    private final List<CdsExportCapturedTextRowDto> archives = new ArrayList<>();
    private final List<CdsExportNativeRowReceiptDto> witnesses = new ArrayList<>();
    private final Map<String, CdsExportCapturedTextRowDto> providers = new HashMap<>();
    private int shift;

    @BeforeEach
    void setup() {
        fixture = new CdsExportDemographicProjectionEvidenceUnitTest();
        fixture.setup();
        shift = 0;
        source.clear();
        archives.clear();
        witnesses.clear();
        providers.clear();
        source.put("Names[1]/NamePrefix[1]", "Dr");
        source.put("Names[1]/LegalName[1]/@namePurpose", "L");
        source.put("Names[1]/LegalName[1]/FirstName[1]/Part[1]", "Ada");
        source.put("Names[1]/LegalName[1]/FirstName[1]/PartType[1]", "GIV");
        source.put("Names[1]/LegalName[1]/LastName[1]/Part[1]", "Family");
        source.put("Names[1]/LegalName[1]/LastName[1]/PartType[1]", "FAMC");
        source.put("Names[1]/LegalName[1]/OtherName[1]/Part[1]", "Source Middle");
        source.put("Names[1]/LegalName[1]/OtherName[1]/PartType[1]", "GIV");
        source.put("DateOfBirth[1]", "1980-01-02");
        source.put("Gender[1]", "F");
        source.put("UniqueVendorIdSequence[1]", "27");
        source.put("PersonStatusCode[1]/PersonStatusAsPlainText[1]", "AC");
        source.put("PersonStatusDate[1]", "2024-01-02");
        source.put("HealthCard[1]/Number[1]", "0123456789");
        source.put("HealthCard[1]/Version[1]", "AB");
        source.put("HealthCard[1]/ProvinceCode[1]", "CA-ON");
        source.put("HealthCard[1]/Expirydate[1]", "2028-01-02");
        source.put("Address[1]/@addressType", "M");
        source.put("Address[1]/Structured[1]/Line1[1]", "123 Source");
        source.put("Address[1]/Structured[1]/City[1]", "Town");
        source.put("Address[1]/Structured[1]/CountrySubdivisionCode[1]", "CA-ON");
        source.put("Address[1]/Structured[1]/PostalZipCode[1]/PostalCode[1]", "K1A0B1");
        source.put("PhoneNumber[1]/@phoneNumberType", "R");
        source.put("PhoneNumber[1]/phoneNumber[1]", "6135550000");
        source.put("ChartNumber[1]", "C27");
        source.put("Email[1]", "old@example.test");
        source.put("SIN[1]", "123456789");
        source.put("PreferredOfficialLanguage[1]", "ENG");
        source.put("PreferredSpokenLanguage[1]", "fra");
        history(1, "0", "2018-01-02", "2019-01-02");
        history(2, "1", "2020-01-02", null);
        prepared("RO", "2020-01-02", null, 2);
    }

    private void history(int ordinal, String status, String start, String end) {
        String prefix = "Enrolment[1]/EnrolmentHistory[" + ordinal + "]/";
        source.put(prefix + "EnrollmentStatus[1]", status);
        source.put(prefix + "EnrollmentDate[1]", start);
        if (end != null) source.put(prefix + "EnrollmentTerminationDate[1]", end);
    }

    private void prepared(String status, String start, String end, long count) {
        fixture.preparedRoster(
                new CdsDemographicDetailsDto.Enrollment(status, start, end, null, null), count);
    }

    private void current(String status, String start, String end, String reason, String provider) {
        fixture.currentField("roster_status", status);
        fixture.currentField("roster_date", start);
        fixture.currentField("roster_termination_date", end);
        fixture.currentField("roster_termination_reason", reason);
        fixture.currentField("roster_enrolled_to", provider);
    }

    private void archive(
            long id, String status, String start, String end, String reason, String provider) {
        var row =
                CdsExportEnrolmentHistoryUnitTest.archive(id, status, start, end, reason, provider);
        archives.add(row);
        witnesses.add(witness(row));
    }

    private static CdsExportNativeRowReceiptDto witness(CdsExportCapturedTextRowDto row) {
        var columns = new ArrayList<CdsNativeRowDigest.Column>();
        var cells = new ArrayList<CdsNativeRowState.Cell>();
        for (var entry : row.fields().entrySet()) {
            columns.add(
                    new CdsNativeRowDigest.Column(
                            entry.getKey(),
                            entry.getKey().equals("lastUpdateDate") ? "TIMESTAMP" : "VARCHAR",
                            255,
                            0));
            var v = entry.getValue();
            cells.add(
                    v == null
                            ? new CdsNativeRowState.Cell(true, 0, null)
                            : new CdsNativeRowState.Cell(
                                    false,
                                    v.getBytes(StandardCharsets.UTF_8).length,
                                    HexFormat.of()
                                            .formatHex(
                                                    CdsImportKeys.digest()
                                                            .digest(
                                                                    v.getBytes(
                                                                            StandardCharsets
                                                                                    .UTF_8)))));
        }
        var state = new CdsNativeRowState(row.table(), row.fields().get("id"), columns, cells);
        return new CdsExportNativeRowReceiptDto(
                row.table(), row.fields().get("id"), row.recordKey(), state.digest(), state);
    }

    private CdsExportDemographicProjectionEvidenceUnitTest.Graph graph() throws Exception {
        var g = fixture.withSource(source);
        String run = g.index().appliedUnits().iterator().next().substring(0, 36);
        var tables = new LinkedHashMap<String, Map<String, CdsExportCapturedTextRowDto>>();
        g.index().tables().forEach((k, v) -> tables.put(k, new LinkedHashMap<>(v)));
        var raw = new LinkedHashMap<>(tables.get("cds_import_run").get(run).fields());
        raw.put("dry_run", "0");
        raw.put("profile", "omd-5.2");
        raw.put("snapshot_location", "/owned/job-" + HASH + ".json");
        tables.get("cds_import_run")
                .put(run, new CdsExportCapturedTextRowDto("cds_import_run", run, raw));
        var option =
                new CdsExportRetainedImportOptionsReader.Options(
                        run,
                        HASH,
                        raw.get("options_digest"),
                        raw.get("ruleset_digest"),
                        "omd-5.2",
                        shift != 0,
                        shift);
        tables.put(
                "retained_import_options",
                Map.of(
                        run,
                        new CdsExportCapturedTextRowDto(
                                "retained_import_options",
                                run,
                                Map.of(
                                        "options",
                                        CdsExportRetainedImportOptionsReader.encodeCaptured(
                                                option)))));
        var index =
                new CdsExportRetainedIndexDto(
                        g.index().sourceManifestSha256(),
                        g.index().metadataRows() + 1,
                        g.index().metadataBytes() + 1024,
                        tables,
                        g.index().appliedUnits(),
                        g.index().references(),
                        g.index().nativeParents());
        return new CdsExportDemographicProjectionEvidenceUnitTest.Graph(
                index, g.checked(), g.fields(), g.current(), g.witness());
    }

    private static String address(CdsExportDemographicProjectionEvidenceUnitTest.Graph graph) {
        return graph.index().nativeParents().values().iterator().next();
    }

    private CdsExportRetainedSectionWriter.Values values() {
        return new CdsExportRetainedSectionWriter.Values() {
            public void requireActive() {}

            public Reader text(CdsExportRetainedFieldDto field) {
                return new StringReader(
                        field.logicalInline() == null
                                ? field.originalInline()
                                : field.logicalInline());
            }

            public InputStream binary(CdsExportRetainedFieldDto field) {
                throw new AssertionError("No binary fixture");
            }
        };
    }

    private CdsExportRetainedEnrolmentReconciler.Result reconcile() throws Exception {
        var g = graph();
        return reconcile(g, () -> {});
    }

    private CdsExportRetainedEnrolmentReconciler.Result reconcile(
            CdsExportDemographicProjectionEvidenceUnitTest.Graph g, Runnable checkpoint)
            throws Exception {
        return new CdsExportRetainedEnrolmentReconciler()
                .reconcile(
                        27,
                        address(g),
                        g.index(),
                        g.checked(),
                        g.fields(),
                        g.current(),
                        g.witness(),
                        archives,
                        witnesses,
                        new CdsExportPublicProviderLookupsDto(
                                g.index().sourceManifestSha256(), providers),
                        1000,
                        16L << 20,
                        values(),
                        checkpoint);
    }

    private Document render(CdsExportRetainedEnrolmentReconciler.Result result) throws Exception {
        return render(result.changes());
    }

    private Document render(List<CdsExportRetainedSectionWriter.Change> changes) throws Exception {
        var g = graph();
        var plan =
                new CdsExportRetainedSectionWriter()
                        .prepareChanges(g.fields(), values(), changes, () -> {});
        var bytes = new ByteArrayOutputStream();
        var selected = Set.of(CdsCategory.DEMOGRAPHICS);
        String patient = HASH, version = "2026.08.0-test";
        var section =
                new CdsExportXmlComposer.Section(
                        new CdsExportIdentityDto(patient, CdsCategory.DEMOGRAPHICS, "b".repeat(64)),
                        plan.content());
        new CdsExportXmlComposer()
                .compose(
                        bytes,
                        "c".repeat(64),
                        patient,
                        version,
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        selected,
                        List.of(section).iterator(),
                        ignored -> {},
                        1L << 20,
                        () -> {});
        new CdsExportXmlValidator()
                .validate(
                        new ByteArrayInputStream(bytes.toByteArray()),
                        "27",
                        selected,
                        version,
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportXmlLimitsDto.defaults(),
                        ignored -> {},
                        () -> {});
        var factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes.toByteArray()));
    }

    private static List<String> text(Document document, String local) {
        var nodes = document.getElementsByTagNameNS("*", local);
        var values = new ArrayList<String>();
        for (int n = 0; n < nodes.getLength(); n++) values.add(nodes.item(n).getTextContent());
        return values;
    }

    @Test
    void shouldPreserveEveryRetainedHistory_whenTheNativeProjectionIsUnchanged() throws Exception {
        var result = reconcile();
        assertThat(result.representable()).isTrue();
        assertThat(result.retainedHistories()).isEqualTo(2);
        assertThat(result.additionalHistories()).isZero();
        assertThat(result.fields()).hasSize(5);
        assertThat(result.sourceFields()).hasSize(5);
        assertThat(result.changes()).isEmpty();
        assertThat(result.supersededHistories()).isZero();
        assertThat(text(render(result), "EnrollmentStatus")).containsExactly("0", "1");
    }

    @Test
    void shouldExportEveryOpenImportedEpisodeExactlyAsBefore_whenTheRosterIsUnchanged()
            throws Exception {
        // An unedited chart whose source history holds two open episodes keeps both: nothing in
        // CARLOS contradicts them.
        source.remove("Enrolment[1]/EnrolmentHistory[1]/EnrollmentTerminationDate[1]");
        source.put("Enrolment[1]/EnrolmentHistory[1]/EnrollmentStatus[1]", "1");
        var result = reconcile();
        assertThat(result.representable()).isTrue();
        assertThat(result.supersededHistories()).isZero();
        assertThat(result.changes()).isEmpty();
        assertThat(text(render(result), "EnrollmentStatus")).containsExactly("1", "1");
        assertThat(
                        result.fields()
                                .get(CdsExportEnrolmentHistoryUnitTest.key("roster_status"))
                                .rule())
                .isEqualTo("VERIFIED_RETAINED_ENROLMENT_PROJECTION");
    }

    @Test
    void shouldAppendAnExplicitNativeTermination_withoutDeletingTheEarlierSourceEpisode()
            throws Exception {
        current("TE", "2020-01-02", "2024-01-02", "12", null);
        var result = reconcile();
        assertThat(result.representable()).isTrue();
        assertThat(result.additionalHistories()).isEqualTo(1);
        assertThat(text(render(result), "EnrollmentStatus")).containsExactly("0", "1", "0");
        assertThat(text(render(result), "EnrollmentTerminationDate"))
                .containsExactly("2019-01-02", "2024-01-02");
    }

    @Test
    void shouldShiftSourceHistoryOnce_andKeepLaterNativeEditsOnTheirRecordedCalendar()
            throws Exception {
        shift = 365;
        prepared("RO", "2021-01-01", null, 2);
        current("TE", "2021-01-01", "2025-02-03", null, null);
        var result = reconcile();
        assertThat(result.representable()).isTrue();
        assertThat(result.changes()).hasSize(4);
        var xml = render(result);
        assertThat(text(xml, "EnrollmentDate"))
                .containsExactly("2019-01-02", "2021-01-01", "2021-01-01");
        assertThat(text(xml, "EnrollmentTerminationDate"))
                .containsExactly("2020-01-02", "2025-02-03");
    }

    @Test
    void shouldFoldRepeatedNativeSnapshots_intoTheProvenSourceProjection() throws Exception {
        archive(1, "RO", "2020-01-02", null, null, null);
        archive(2, "RO", "2020-01-02", null, null, null);
        var result = reconcile();
        assertThat(result.representable()).isTrue();
        assertThat(result.additionalHistories()).isZero();
        assertThat(result.fields())
                .hasSize(
                        5
                                + 2
                                        * io.github.carlos_emr.carlos.commn.dao
                                                .CdsExportPatientTableReadSupport.Plan
                                                .DEMOGRAPHIC_ARCHIVE
                                                .columns()
                                                .size());
        assertThat(result.deferredNativeFields()).isEmpty();
        assertThat(
                        result.fields().entrySet().stream()
                                .filter(
                                        entry ->
                                                entry.getKey().table().equals("demographicArchive")
                                                        && entry.getKey()
                                                                .column()
                                                                .equals("first_name"))
                                .map(entry -> entry.getValue().rule()))
                .containsOnly("SUPERSEDED_DEMOGRAPHIC_SNAPSHOT_IN_APPROVED_AUDIT");
        assertThat(text(render(result), "EnrolmentHistory")).hasSize(2);
    }

    @Test
    void shouldUseRetainedTerminationEvidence_whenAnArchiveContainsAnEarlierActiveSnapshot()
            throws Exception {
        archive(1, "RO", "2018-01-02", null, null, null);
        var result = reconcile();
        assertThat(result.representable()).isTrue();
        assertThat(result.additionalHistories()).isEqualTo(1);
        assertThat(text(render(result), "EnrollmentDate"))
                .containsExactly("2018-01-02", "2020-01-02", "2018-01-02");
    }

    @Test
    void shouldAvoidAnotherTerminationOccurrence_whenTheSourceAlreadyContainsIt() throws Exception {
        archive(1, "TE", "2018-01-02", "2019-01-02", null, null);
        assertThat(reconcile().additionalHistories()).isZero();
    }

    @Test
    void shouldAppendTheLatestNativeTermination_besideTheImportedOneOfTheSameEpisode()
            throws Exception {
        // Updated: a native termination of an imported episode that differs from the imported
        // one used to hold the chart. The imported history exports as imported and the latest
        // native version of the episode is appended, as on a never-imported chart.
        archive(1, "TE", "2018-01-02", "2018-12-01", null, null);
        var result = reconcile();
        assertThat(result.representable()).isTrue();
        assertThat(result.additionalHistories()).isEqualTo(1);
        assertThat(text(render(result), "EnrollmentTerminationDate"))
                .containsExactly("2019-01-02", "2018-12-01");
    }

    @Test
    void shouldSupersedeTheImportedOpenEnrolmentWithoutInventingATermination_whenTheFlagIsNr()
            throws Exception {
        // Updated twice: clearing the roster of an imported enrolled patient used to hold the
        // chart, then exported the imported open enrolment. NR contradicts it: the open source
        // episode is superseded (as the never-imported path supersedes an archived one), the
        // closed one exports as imported, and no termination is invented.
        current("NR", null, null, null, null);
        var result = reconcile();
        assertThat(result.representable()).isTrue();
        assertThat(result.additionalHistories()).isZero();
        assertThat(result.supersededHistories()).isEqualTo(1);
        assertThat(text(render(result), "EnrollmentStatus")).containsExactly("0");
        assertThat(text(render(result), "EnrollmentTerminationDate"))
                .containsExactly("2019-01-02");
    }

    @Test
    void shouldKeepClosedSourceHistory_whenTheCurrentFlagIsExplicitlyNr() throws Exception {
        source.put("Enrolment[1]/EnrolmentHistory[2]/EnrollmentStatus[1]", "0");
        source.put("Enrolment[1]/EnrolmentHistory[2]/EnrollmentTerminationDate[1]", "2024-01-02");
        prepared("TE", "2020-01-02", "2024-01-02", 2);
        current("NR", null, null, null, null);
        var result = reconcile();
        assertThat(result.representable()).isTrue();
        assertThat(result.additionalHistories()).isZero();
        assertThat(text(render(result), "EnrollmentStatus")).containsExactly("0", "0");
    }

    @Test
    void shouldShiftTheImportedHistoryAndCarryTheLeftoverDate_whenTheCurrentFlagBecomesNr()
            throws Exception {
        // Updated: NR with a leftover roster date used to hold the chart. NR with leftover
        // values is not an enrolment event: the date travels in the native demographic record.
        // The open imported episode NR contradicts is superseded (its shifted dates with it) and
        // the closed one is still shifted once and exported.
        shift = 365;
        prepared("RO", "2021-01-01", null, 2);
        fixture.currentField("roster_status", "NR");
        var result = reconcile();
        assertThat(result.representable()).isTrue();
        assertThat(result.additionalHistories()).isZero();
        assertThat(result.supersededHistories()).isEqualTo(1);
        assertThat(result.fields().get(CdsExportEnrolmentHistoryUnitTest.key("roster_date")).destination())
                .isEqualTo(CdsExportDemographicSupportingMapper.DESTINATION);
        assertThat(text(render(result), "EnrollmentDate")).containsExactly("2019-01-02");
    }

    @Test
    void shouldSupersedeTheImportedOpenEpisode_whenThePatientIsReRosteredWithoutATermination()
            throws Exception {
        // Updated twice: re-rostering an imported patient without a termination used to hold
        // the chart, then left the imported episode open beside the new one. The new episode
        // is appended and the open imported one it contradicts is superseded, as on a
        // never-imported chart; no termination is invented.
        current("RO", "2022-01-02", null, null, null);
        var result = reconcile();
        assertThat(result.representable()).isTrue();
        assertThat(result.additionalHistories()).isEqualTo(1);
        assertThat(result.supersededHistories()).isEqualTo(1);
        assertThat(text(render(result), "EnrollmentDate"))
                .containsExactly("2018-01-02", "2022-01-02");
        assertThat(text(render(result), "EnrollmentTerminationDate"))
                .containsExactly("2019-01-02");
    }

    @Test
    void shouldAuditANewerArchivedEpisode_whenTheCurrentStateReturnedToTheImportedOne()
            throws Exception {
        // Updated: an archived episode newer than the current state used to hold the chart. It
        // was superseded without a termination: it stays in the approved audit arrangement.
        archive(1, "RO", "2026-01-02", null, null, null);
        var result = reconcile();
        assertThat(result.representable()).isTrue();
        assertThat(result.additionalHistories()).isZero();
        assertThat(
                        result.fields()
                                .get(
                                        new CdsExportNativeFieldKeyDto(
                                                "demographicArchive",
                                                archives.getFirst().recordKey(),
                                                "roster_date"))
                                .rule())
                .isEqualTo("SUPERSEDED_ROSTER_SNAPSHOT_IN_APPROVED_AUDIT");
        assertThat(text(render(result), "EnrolmentHistory")).hasSize(2);
    }

    @Test
    void shouldSupersedeTheImportedOpenEnrolment_whenAnImportedEnrolledPatientIsMovedToFs()
            throws Exception {
        // FS is not an enrolment event: the current roster values and the enrolled provider's
        // public row travel in the native demographic record. Updated: the imported open
        // enrolment FS contradicts used to be exported as if the patient were still enrolled.
        // It is superseded: its fields stay in the retained source and are listed as replaced,
        // the current roster status names the rule, and the closed episode exports as imported.
        current("FS", "2020-01-02", null, null, "12");
        providers.put("12", CdsExportEnrolmentHistoryUnitTest.provider("12"));
        var result = reconcile();
        assertThat(result.representable()).isTrue();
        assertThat(result.additionalHistories()).isZero();
        assertThat(result.supersededHistories()).isEqualTo(1);
        var status = result.fields().get(CdsExportEnrolmentHistoryUnitTest.key("roster_status"));
        assertThat(status.destination()).isEqualTo(CdsExportDemographicSupportingMapper.DESTINATION);
        assertThat(status.rule()).isEqualTo("CURRENT_ROSTER_SUPERSEDES_IMPORTED_OPEN_ENROLMENT");
        assertThat(CdsExportDemographicSupportingMapper.carries(status)).isTrue();
        var g = graph();
        var openEpisode =
                g.fields().stream()
                        .filter(
                                field ->
                                        field.sourcePath()
                                                .contains("Enrolment[1]/EnrolmentHistory[2]/"))
                        .map(CdsExportRetainedFieldDto::id)
                        .collect(java.util.stream.Collectors.toSet());
        assertThat(openEpisode).hasSize(2);
        var plan =
                new CdsExportRetainedSectionWriter()
                        .prepareChanges(g.fields(), values(), result.changes(), () -> {});
        assertThat(plan.replacedFields()).isEqualTo(openEpisode);
        assertThat(
                        result.fields()
                                .get(
                                        new CdsExportNativeFieldKeyDto(
                                                "provider",
                                                CdsExportPublicProviderSourceReader.key("12"),
                                                "last_name"))
                                .destination())
                .isEqualTo(CdsExportDemographicSupportingMapper.DESTINATION);
        assertThat(text(render(result), "EnrollmentStatus")).containsExactly("0");
    }

    @Test
    void shouldRemoveTheWholeEnrolment_whenFsSupersedesTheOnlyImportedEpisode() throws Exception {
        // With nothing left to export, the export no longer has an Enrolment element at all.
        source.keySet().removeIf(name -> name.startsWith("Enrolment["));
        history(1, "1", "2020-01-02", null);
        prepared("RO", "2020-01-02", null, 1);
        current("FS", "2020-01-02", null, null, null);
        var result = reconcile();
        assertThat(result.representable()).isTrue();
        assertThat(result.supersededHistories()).isEqualTo(1);
        assertThat(result.changes())
                .extracting(CdsExportRetainedSectionWriter.Change::relativePath)
                .containsExactly("Enrolment[1]");
        assertThat(text(render(result), "Enrolment")).isEmpty();
    }

    @Test
    void shouldAppendTheNewEpisode_whenTheEnrolledPhysicianOfAnImportedChartChanges()
            throws Exception {
        // The patient is re-rostered to another physician without a termination (an archive
        // snapshot names a former one, whose open episode is superseded). The new episode
        // exports with its physician, the open imported episode is superseded, and the provider
        // rows are decided by their uses.
        archive(1, "RO", "2023-05-06", null, null, "12");
        current("RO", "2024-01-02", null, null, "34");
        providers.put("12", CdsExportEnrolmentHistoryUnitTest.provider("12"));
        providers.put("34", CdsExportEnrolmentHistoryUnitTest.provider("34"));
        var result = reconcile();
        assertThat(result.representable()).isTrue();
        assertThat(result.additionalHistories()).isEqualTo(1);
        assertThat(result.supersededHistories()).isEqualTo(1);
        assertThat(
                        result.fields()
                                .get(CdsExportEnrolmentHistoryUnitTest.key("roster_status"))
                                .rule())
                .isEqualTo("CURRENT_ROSTER_SUPERSEDES_IMPORTED_OPEN_ENROLMENT");
        var xml = render(result);
        assertThat(text(xml, "EnrollmentDate")).containsExactly("2018-01-02", "2024-01-02");
        assertThat(text(xml, "EnrollmentStatus")).containsExactly("0", "1");
        assertThat(text(xml, "OHIPPhysicianId")).containsExactly("123456");
        assertThat(
                        result.fields()
                                .get(
                                        new CdsExportNativeFieldKeyDto(
                                                "provider",
                                                CdsExportPublicProviderSourceReader.key("12"),
                                                "last_name"))
                                .rule())
                .isEqualTo("SUPERSEDED_ROSTER_PROVIDER_IN_APPROVED_AUDIT");
        assertThat(
                        result.fields()
                                .get(
                                        new CdsExportNativeFieldKeyDto(
                                                "provider",
                                                CdsExportPublicProviderSourceReader.key("34"),
                                                "last_name"))
                                .outcome())
                .isEqualTo(CdsExportFieldAssessmentDto.Outcome.MAPPED);
    }

    @Test
    void shouldRequireTheAppliedPreparationProjection_beforeReconcilingAMatchedExistingChart()
            throws Exception {
        fixture.oldField("roster_date", "2017-01-02");
        var result = reconcile();
        assertThat(result.representable()).isFalse();
        assertThat(result.blockers()).contains("PREPARED_ENROLMENT_PROJECTION_NOT_PROVEN");
    }

    @Test
    void shouldRejectSubstitutedArchiveFields_evenWhenTheEnrolmentColumnsWereUnchanged()
            throws Exception {
        archive(1, "RO", "2020-01-02", null, null, null);
        var row = archives.getFirst();
        var fields = new LinkedHashMap<>(row.fields());
        fields.put("first_name", "Substituted");
        archives.set(0, new CdsExportCapturedTextRowDto(row.table(), row.recordKey(), fields));
        assertThatThrownBy(this::reconcile)
                .hasMessage("CDS_EXPORT_CAPTURED_TEXT_WITNESS_SCOPE_INVALID");
    }

    @Test
    void shouldRejectMissingOrUnrelatedArchiveWitnesses_beforeReturningChanges() throws Exception {
        archive(1, "RO", "2020-01-02", null, null, null);
        witnesses.clear();
        assertThatThrownBy(this::reconcile)
                .hasMessage("CDS_EXPORT_ENROLMENT_RECONCILIATION_SCOPE_INVALID");
    }

    @Test
    void shouldRejectForeignPatientArchives_insteadOfMergingTheirHistory() throws Exception {
        archive(1, "RO", "2020-01-02", null, null, null);
        var row = archives.getFirst();
        var fields = new LinkedHashMap<>(row.fields());
        fields.put("demographic_no", "28");
        archives.set(0, new CdsExportCapturedTextRowDto(row.table(), row.recordKey(), fields));
        witnesses.set(0, witness(archives.getFirst()));
        assertThatThrownBy(this::reconcile).hasMessage("CDS_EXPORT_ENROLMENT_SOURCE_SCOPE_INVALID");
    }

    @Test
    void shouldCreateTheCompleteEnrolmentParent_whenTheChartHasANewNativeEvent() throws Exception {
        source.keySet().removeIf(name -> name.startsWith("Enrolment["));
        fixture.preparedRoster(null, 0);
        current("RO", "2024-01-02", null, null, "12");
        providers.put("12", CdsExportEnrolmentHistoryUnitTest.provider("12"));
        var result = reconcile();
        assertThat(result.representable()).isTrue();
        assertThat(result.changes()).hasSize(1);
        assertThat(result.changes().getFirst().relativePath()).isEqualTo("Enrolment[1]");
        var xml = render(result);
        assertThat(text(xml, "EnrollmentDate")).containsExactly("2024-01-02");
        assertThat(text(xml, "OHIPPhysicianId")).containsExactly("123456");
    }

    @Test
    void shouldNeverInventAPhysician_forANewNativeReferenceWithoutACapturedProvider() throws Exception {
        source.keySet().removeIf(name -> name.startsWith("Enrolment["));
        fixture.preparedRoster(null, 0);
        current("RO", "2024-01-02", null, null, "12");
        var result = reconcile();
        // The new episode is exported without EnrolledToPhysician; the number is carried.
        assertThat(result.representable()).isTrue();
        assertThat(text(render(result), "OHIPPhysicianId")).isEmpty();
    }

    @Test
    void shouldRejectUnsupportedSourcePhysicianLengths_withoutTruncatingTheSource()
            throws Exception {
        source.put(
                "Enrolment[1]/EnrolmentHistory[1]/EnrolledToPhysician[1]/OHIPPhysicianId[1]",
                "123456789012");
        var result = reconcile();
        assertThat(result.representable()).isFalse();
        assertThat(result.sourceFields()).hasSize(6);
    }

    @Test
    void shouldRefuseMissingCapturedOptions_beforeUsingAHistoryShift() throws Exception {
        var g = graph();
        var tables = new LinkedHashMap<>(g.index().tables());
        tables.remove("retained_import_options");
        var index =
                new CdsExportRetainedIndexDto(
                        g.index().sourceManifestSha256(),
                        g.index().metadataRows(),
                        g.index().metadataBytes(),
                        tables,
                        g.index().appliedUnits(),
                        g.index().references(),
                        g.index().nativeParents());
        var noOptions =
                new CdsExportDemographicProjectionEvidenceUnitTest.Graph(
                        index, g.checked(), g.fields(), g.current(), g.witness());
        assertThatThrownBy(() -> reconcile(noOptions, () -> {}))
                .hasMessage("CDS_EXPORT_CAPTURED_IMPORT_OPTIONS_REQUIRED");
    }

    @Test
    void shouldPropagateCancellation_beforeAnySourceOrNativeHistoryCanBePublished()
            throws Exception {
        var g = graph();
        assertThatThrownBy(
                        () ->
                                reconcile(
                                        g,
                                        () -> {
                                            throw new IllegalStateException("CANCELLED_ENROLMENT");
                                        }))
                .hasMessage("CANCELLED_ENROLMENT");
    }

    @Test
    void shouldComposeEnrolmentWithCoreEdits_usingDisjointSourceAndNativeOwnership()
            throws Exception {
        fixture.currentField("email", "edited@example.test");
        current("TE", "2020-01-02", "2024-01-02", "12", null);
        var g = graph();
        var enrol = reconcile(g, () -> {});
        var core =
                new CdsExportDemographicRetainedCoreReconciler()
                        .reconcile(
                                27,
                                address(g),
                                g.index(),
                                g.checked(),
                                g.fields(),
                                g.current(),
                                g.witness(),
                                values(),
                                () -> {});
        assertThat(core.coreRepresentable()).isTrue();
        assertThat(Collections.disjoint(core.fields().keySet(), enrol.fields().keySet())).isTrue();
        assertThat(Collections.disjoint(core.decidedSourceFields(), enrol.sourceFields())).isTrue();
        var changes = new ArrayList<>(core.coreChanges());
        changes.addAll(enrol.changes());
        var xml = render(changes);
        assertThat(text(xml, "Email")).containsExactly("edited@example.test");
        assertThat(text(xml, "EnrollmentStatus")).containsExactly("0", "1", "0");
    }

    @Test
    void shouldRejectProviderLookupsFromAnotherCapture_beforeReconcilingAnyEnrolmentHistory()
            throws Exception {
        var g = graph();
        var stale = new CdsExportPublicProviderLookupsDto("f".repeat(64), providers);
        assertThatThrownBy(
                        () ->
                                new CdsExportRetainedEnrolmentReconciler()
                                        .reconcile(
                                                27,
                                                address(g),
                                                g.index(),
                                                g.checked(),
                                                g.fields(),
                                                g.current(),
                                                g.witness(),
                                                archives,
                                                witnesses,
                                                stale,
                                                1000,
                                                16L << 20,
                                                values(),
                                                () -> {}))
                .hasMessage("CDS_EXPORT_PUBLIC_PROVIDER_LOOKUPS_GENERATION_INVALID");
    }

    @Test
    void shouldHandleANullNativePhysicianReference_withAnImmutableEmptyCapturedLookup()
            throws Exception {
        current("TE", "2020-01-02", "2024-01-02", "12", null);
        var g = graph();
        var result =
                new CdsExportRetainedEnrolmentReconciler()
                        .reconcile(
                                27,
                                address(g),
                                g.index(),
                                g.checked(),
                                g.fields(),
                                g.current(),
                                g.witness(),
                                archives,
                                witnesses,
                                new CdsExportPublicProviderLookupsDto(
                                        g.index().sourceManifestSha256(), Map.of()),
                                1000,
                                16L << 20,
                                values(),
                                () -> {});
        assertThat(result.representable()).isTrue();
        assertThat(result.additionalHistories()).isEqualTo(1);
    }
}
