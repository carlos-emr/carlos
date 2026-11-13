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
package io.github.carlos_emr.carlos.demographic.cdsexport.validator;

import static org.assertj.core.api.Assertions.*;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit")
@Tag("cds-export")
class CdsExportSupportingFilesValidatorUnitTest {
    private CdsExportPackageMetadataDto metadata;
    private List<CdsExportPhysicianSummaryDto> physicians;
    private List<CdsExportSupportingFilesValidator.Patient> actual;
    private byte[] readMe, log;

    @BeforeEach
    void setup() throws Exception {
        metadata =
                new CdsExportPackageMetadataDto(
                        "ExportRoot",
                        "Synthetic clinic",
                        "fixture@example.test",
                        "2026.08.0-test",
                        Instant.parse("2026-11-07T15:00:00Z"),
                        ZoneId.of("America/Toronto"),
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.LABORATORY_RESULTS),
                        "Synthetic approved audit arrangement");
        physicians = List.of(new CdsExportPhysicianSummaryDto("Alice", "Fixture", "123456", 1));
        var name =
                new CdsExportPatientFileDto(
                        "Alice",
                        "Fixture",
                        "123456",
                        "Ada",
                        "Fixture",
                        "C27",
                        LocalDate.of(1980, 1, 2));
        var counts = Map.of(CdsCategory.DEMOGRAPHICS, 1L, CdsCategory.LABORATORY_RESULTS, 0L);
        var xml =
                new CdsExportXmlValidationDto(
                        "C27",
                        "Ada",
                        "Fixture",
                        "1980-01-02-05:00",
                        counts,
                        0,
                        0,
                        0,
                        "a".repeat(64),
                        1234);
        var path = new CdsExportPathComposer().compose(name);
        actual =
                List.of(
                        new CdsExportSupportingFilesValidator.Patient(
                                metadata.exportRoot()
                                        + "/"
                                        + path.physicianFolder()
                                        + "/"
                                        + path.patientXmlFile(),
                                name,
                                xml));
        var text = new ByteArrayOutputStream();
        var csv = new ByteArrayOutputStream();
        new CdsExportSupportingFilesComposer()
                .compose(
                        text,
                        csv,
                        metadata,
                        physicians.iterator(),
                        List.of(
                                        new CdsExportPatientSummaryDto(
                                                name,
                                                counts,
                                                null,
                                                null,
                                                xml.xmlSha256(),
                                                xml.xmlBytes()))
                                .iterator(),
                        () -> {});
        readMe = text.toByteArray();
        log = csv.toByteArray();
    }

    private CdsExportSupportingFilesValidator.Result validate() throws Exception {
        return validate(readMe, log, actual, 1000000, () -> {});
    }

    private CdsExportSupportingFilesValidator.Result validate(
            byte[] text,
            byte[] csv,
            List<CdsExportSupportingFilesValidator.Patient> patients,
            long maximum,
            Runnable checkpoint)
            throws Exception {
        return new CdsExportSupportingFilesValidator()
                .validate(
                        new ByteArrayInputStream(text),
                        new ByteArrayInputStream(csv),
                        metadata,
                        physicians.iterator(),
                        patients.iterator(),
                        100,
                        100,
                        maximum,
                        checkpoint);
    }

    private static byte[] replace(byte[] source, String before, String after) {
        String original = new String(source, StandardCharsets.UTF_8);
        assertThat(original).contains(before);
        return original.replace(before, after).getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void shouldBindBothFinishedFiles_toActualXmlCountsPathsAndExactBytes() throws Exception {
        var result = validate();
        assertThat(result.physicians()).isEqualTo(1);
        assertThat(result.patientFiles()).isEqualTo(1);
        assertThat(result.readMeBytes()).isEqualTo(readMe.length);
        assertThat(result.eventLogBytes()).isEqualTo(log.length);
        assertThat(result.readMeSha256())
                .isEqualTo(
                        HexFormat.of()
                                .formatHex(MessageDigest.getInstance("SHA-256").digest(readMe)));
        assertThat(result.eventLogSha256())
                .isEqualTo(
                        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(log)));
    }

    @Test
    void shouldRefuseAReadmeThatOmitsAHeldPatientOrLocalTable_listedInTheMetadata() throws Exception {
        var partial =
                new CdsExportPackageMetadataDto(
                        metadata.exportRoot(),
                        metadata.physicianGroup(),
                        metadata.groupContactInformation(),
                        metadata.applicationVersion(),
                        metadata.exportTime(),
                        metadata.clinicalZone(),
                        metadata.profile(),
                        metadata.categories(),
                        metadata.auditAccessDescription(),
                        List.of(new CdsExportHeldPatientDto(41, "CDS_EXPORT_UNMAPPED_DATA")),
                        List.of(
                                new io.github.carlos_emr.carlos.commn.dto.CdsExportLocalSourceDto(
                                        "clinic_notes_local", 7)));
        var complete = metadata;
        metadata = partial;
        // The ReadMe composed for a complete package must not validate as the partial package.
        assertThatThrownBy(this::validate).isInstanceOf(java.io.IOException.class);
        var text = new ByteArrayOutputStream();
        new CdsExportSupportingFilesComposer()
                .compose(
                        text,
                        new ByteArrayOutputStream(),
                        partial,
                        physicians.iterator(),
                        List.of(
                                        new CdsExportPatientSummaryDto(
                                                actual.getFirst().naming(),
                                                actual.getFirst().xml().occurrences(),
                                                null,
                                                null,
                                                actual.getFirst().xml().xmlSha256(),
                                                actual.getFirst().xml().xmlBytes()))
                                .iterator(),
                        () -> {});
        readMe = text.toByteArray();
        assertThat(validate().patientFiles()).isEqualTo(1);
        readMe = replace(readMe, "Held patient reason: \"CDS_EXPORT_UNMAPPED_DATA\"",
                "Held patient reason: \"CDS_EXPORT_ACCESS_CHANGED\"");
        assertThatThrownBy(this::validate).isInstanceOf(java.io.IOException.class);
        metadata = complete;
    }

    @Test
    void shouldListDataSummariesAndListedTables_andRefuseAReadmeThatDropsOne() throws Exception {
        // Decision 6 and 20: the receiver sees, per patient, how many records travel as CARLOS
        // data summaries and which removed-feature tables held rows that were not exported, and for
        // the whole job which unlinked removed-feature tables were not exported.
        var listedClinic =
                List.of(new io.github.carlos_emr.carlos.commn.dto.CdsExportListedSourceDto("DrugProduct", 3));
        var withListed =
                new CdsExportPackageMetadataDto(
                        metadata.exportRoot(),
                        metadata.physicianGroup(),
                        metadata.groupContactInformation(),
                        metadata.applicationVersion(),
                        metadata.exportTime(),
                        metadata.clinicalZone(),
                        metadata.profile(),
                        metadata.categories(),
                        metadata.auditAccessDescription(),
                        List.of(),
                        List.of(),
                        listedClinic,
                        List.of());
        var summaries = new java.util.LinkedHashMap<String, Long>();
        summaries.put("Encounter form", 80L);
        summaries.put("Tickler", 1L);
        var listed =
                List.of(new io.github.carlos_emr.carlos.commn.dto.CdsExportListedSourceDto("CdsHospitalisationDays", 2));
        var complete = metadata;
        var plain = actual;
        metadata = withListed;
        actual =
                List.of(
                        new CdsExportSupportingFilesValidator.Patient(
                                plain.getFirst().relativePath(),
                                plain.getFirst().naming(),
                                plain.getFirst().xml(),
                                summaries,
                                listed));
        var text = new ByteArrayOutputStream();
        new CdsExportSupportingFilesComposer()
                .compose(
                        text,
                        new ByteArrayOutputStream(),
                        withListed,
                        physicians.iterator(),
                        List.of(
                                        new CdsExportPatientSummaryDto(
                                                plain.getFirst().naming(),
                                                plain.getFirst().xml().occurrences(),
                                                null,
                                                null,
                                                plain.getFirst().xml().xmlSha256(),
                                                plain.getFirst().xml().xmlBytes(),
                                                summaries,
                                                listed))
                                .iterator(),
                        () -> {});
        readMe = text.toByteArray();
        assertThat(new String(readMe, StandardCharsets.UTF_8))
                .contains(
                        CdsExportSupportingFilesComposer.DATA_SUMMARY_NOTICE,
                        CdsExportSupportingFilesComposer.LISTED_NOTICE,
                        "Listed clinic tables not exported: \"1\"\nListed clinic table not exported: \"DrugProduct: 3 rows\"\n",
                        "Patient data summary records: \"Encounter form: 80\"\nPatient data summary records: \"Tickler: 1\"\n",
                        "Patient listed table not exported: \"CdsHospitalisationDays: 2 rows\"\n");
        assertThat(validate().patientFiles()).isEqualTo(1);
        byte[] intact = readMe;
        readMe = replace(intact, "Patient listed table not exported: \"CdsHospitalisationDays: 2 rows\"\n", "");
        assertThatThrownBy(this::validate).isInstanceOf(java.io.IOException.class);
        readMe = replace(intact, "Tickler: 1", "Tickler: 2");
        assertThatThrownBy(this::validate).isInstanceOf(java.io.IOException.class);
        readMe = replace(intact, "DrugProduct: 3 rows", "DrugProduct: 4 rows");
        assertThatThrownBy(this::validate).isInstanceOf(java.io.IOException.class);
        metadata = complete;
        actual = plain;
    }

    @Test
    void shouldCountRemovedAndPatientIndependentEforms_andRefuseAReadmeThatAltersTheCount()
            throws Exception {
        // Plan item 2.5: eForms the chart no longer lists are captured, accounted and never
        // exported; the receiver is told how many, per patient.
        var withheld = new java.util.LinkedHashMap<String, Long>();
        withheld.put("Removed eForm (deleted or replaced by an edit)", 2L);
        withheld.put("Patient-independent eForm", 1L);
        var plain = actual;
        actual =
                List.of(
                        new CdsExportSupportingFilesValidator.Patient(
                                plain.getFirst().relativePath(),
                                plain.getFirst().naming(),
                                plain.getFirst().xml(),
                                java.util.Map.of(),
                                List.of(),
                                withheld));
        var text = new ByteArrayOutputStream();
        new CdsExportSupportingFilesComposer()
                .compose(
                        text,
                        new ByteArrayOutputStream(),
                        metadata,
                        physicians.iterator(),
                        List.of(
                                        new CdsExportPatientSummaryDto(
                                                plain.getFirst().naming(),
                                                plain.getFirst().xml().occurrences(),
                                                null,
                                                null,
                                                plain.getFirst().xml().xmlSha256(),
                                                plain.getFirst().xml().xmlBytes(),
                                                java.util.Map.of(),
                                                List.of(),
                                                withheld))
                                .iterator(),
                        () -> {});
        readMe = text.toByteArray();
        assertThat(new String(readMe, StandardCharsets.UTF_8))
                .contains(
                        "Patient records not exported: \"Removed eForm (deleted or replaced by an edit): 2\"\n"
                                + "Patient records not exported: \"Patient-independent eForm: 1\"\n");
        assertThat(validate().patientFiles()).isEqualTo(1);
        byte[] intact = readMe;
        readMe = replace(intact, "Patient-independent eForm: 1", "Patient-independent eForm: 3");
        assertThatThrownBy(this::validate).isInstanceOf(java.io.IOException.class);
        readMe =
                replace(
                        intact,
                        "Patient records not exported: \"Patient-independent eForm: 1\"\n",
                        "");
        assertThatThrownBy(this::validate).isInstanceOf(java.io.IOException.class);
        actual = plain;
    }

    @Test
    void shouldRefuseAReadmeWithADifferentUnlinkedInboxCount_thanTheMetadata() throws Exception {
        var counted =
                new CdsExportPackageMetadataDto(
                        metadata.exportRoot(),
                        metadata.physicianGroup(),
                        metadata.groupContactInformation(),
                        metadata.applicationVersion(),
                        metadata.exportTime(),
                        metadata.clinicalZone(),
                        metadata.profile(),
                        metadata.categories(),
                        metadata.auditAccessDescription(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(
                                new io.github.carlos_emr.carlos.commn.dto
                                        .CdsExportUnlinkedSourceDto("providerLabRouting", 43)));
        var complete = metadata;
        metadata = counted;
        // A ReadMe that says nothing was left out must not validate for a job that left rows out.
        assertThatThrownBy(this::validate).isInstanceOf(java.io.IOException.class);
        var text = new ByteArrayOutputStream();
        new CdsExportSupportingFilesComposer()
                .compose(
                        text,
                        new ByteArrayOutputStream(),
                        counted,
                        physicians.iterator(),
                        List.of(
                                        new CdsExportPatientSummaryDto(
                                                actual.getFirst().naming(),
                                                actual.getFirst().xml().occurrences(),
                                                null,
                                                null,
                                                actual.getFirst().xml().xmlSha256(),
                                                actual.getFirst().xml().xmlBytes()))
                                .iterator(),
                        () -> {});
        readMe = text.toByteArray();
        assertThat(validate().patientFiles()).isEqualTo(1);
        readMe =
                replace(
                        readMe,
                        "Unlinked inbox table rows: \"43\"",
                        "Unlinked inbox table rows: \"42\"");
        assertThatThrownBy(this::validate).isInstanceOf(java.io.IOException.class);
        metadata = complete;
    }

    @Test
    void shouldRefuseAReadmeThatDropsTheAdministrativeFieldsNotice_fromItsFixedText() {
        readMe =
                replace(
                        readMe,
                        "Practice-administrative demographic fields \"date joined\" and \"effective date\" are not part of"
                                + " CDS 5.2 Demographics and are not exported.\n",
                        "");
        assertThatThrownBy(this::validate).hasMessage("CDS_EXPORT_SUPPORTING_FILES_INVALID");
    }

    @Test
    void shouldRefuseAlteredAuditText_inTheFinishedReadme() {
        readMe = replace(readMe, "Synthetic approved audit arrangement", "Different terms");
        assertThatThrownBy(this::validate).hasMessage("CDS_EXPORT_SUPPORTING_FILES_INVALID");
    }

    // The ReadMe must not claim screenings that a package without PastHealth does not carry.
    @Test
    void shouldRefuseAReadme_thatMisstatesWhetherScreeningsAreIncluded() {
        String field = "Prevention screenings (exported only as PastHealth): ";
        String actualValue =
                new String(readMe, java.nio.charset.StandardCharsets.UTF_8).contains(field + "\"INCLUDED\"")
                        ? "\"INCLUDED\""
                        : "\"NOT_SELECTED\"";
        readMe =
                replace(
                        readMe,
                        field + actualValue,
                        field + (actualValue.equals("\"INCLUDED\"") ? "\"NOT_SELECTED\"" : "\"INCLUDED\""));
        assertThatThrownBy(this::validate).hasMessage("CDS_EXPORT_SUPPORTING_FILES_INVALID");
    }

    @Test
    void shouldBindLegalDateWhitespace_toTheSameCalendarDayAndFilename() throws Exception {
        var first = actual.getFirst();
        var x = first.xml();
        var xml =
                new CdsExportXmlValidationDto(
                        x.senderPatientId(),
                        x.legalFirstName(),
                        x.legalLastName(),
                        " \t" + x.dateOfBirth() + "\r\n",
                        x.occurrences(),
                        x.textReports(),
                        x.binaryReports(),
                        x.externalReferences(),
                        x.xmlSha256(),
                        x.xmlBytes());
        actual =
                List.of(
                        new CdsExportSupportingFilesValidator.Patient(
                                first.relativePath(), first.naming(), xml));
        assertThat(validate().patientFiles()).isEqualTo(1);
    }

    @Test
    void shouldRefuseAnAlteredPatientHash_evenWhenItsFilePathAndCountsStillMatch() {
        readMe = replace(readMe, "a".repeat(64), "b".repeat(64));
        assertThatThrownBy(this::validate).hasMessage("CDS_EXPORT_SUPPORTING_FILES_INVALID");
    }

    @Test
    void shouldRefuseAnAlteredPatientPath_withoutCountingTheReadmeAsOutputEvidence() {
        readMe = replace(readMe, actual.getFirst().relativePath(), "ExportRoot/other.xml");
        assertThatThrownBy(this::validate).hasMessage("CDS_EXPORT_SUPPORTING_FILES_INVALID");
    }

    @Test
    void shouldRefuseIncorrectCsvCounts_insteadOfTrustingTheComposerSummary() {
        log = replace(log, "\"C27\",\"1\"", "\"C27\",\"2\"");
        assertThatThrownBy(this::validate).hasMessage("CDS_EXPORT_SUPPORTING_FILES_INVALID");
    }

    @Test
    void shouldKeepNotSelectedDistinctFromKnownZero_inEveryCategoryColumn() {
        log = replace(log, "NOT_SELECTED", "0");
        assertThatThrownBy(this::validate).hasMessage("CDS_EXPORT_SUPPORTING_FILES_INVALID");
    }

    @Test
    void shouldRefuseAnotherPatientId_inTheFinishedEventLog() {
        log = replace(log, "C27", "C28");
        assertThatThrownBy(this::validate).hasMessage("CDS_EXPORT_SUPPORTING_FILES_INVALID");
    }

    @Test
    void shouldRefuseExtraRowsOrText_afterTheExpectedInventoriesEnd() throws Exception {
        assertThatThrownBy(
                        () ->
                                validate(
                                        (new String(readMe, StandardCharsets.UTF_8) + "extra\n")
                                                .getBytes(StandardCharsets.UTF_8),
                                        log,
                                        actual,
                                        1000000,
                                        () -> {}))
                .hasMessage("CDS_EXPORT_SUPPORTING_FILES_INVALID");
        assertThatThrownBy(
                        () ->
                                validate(
                                        readMe,
                                        (new String(log, StandardCharsets.UTF_8)
                                                        + new String(log, StandardCharsets.UTF_8)
                                                                .lines()
                                                                .skip(1)
                                                                .findFirst()
                                                                .orElseThrow()
                                                        + "\n")
                                                .getBytes(StandardCharsets.UTF_8),
                                        actual,
                                        1000000,
                                        () -> {}))
                .hasMessage("CDS_EXPORT_SUPPORTING_FILES_INVALID");
    }

    @Test
    void shouldRefuseTruncatedRows_andMissingFinalTerminators() throws Exception {
        assertThatThrownBy(
                        () ->
                                validate(
                                        readMe,
                                        Arrays.copyOf(log, log.length - 1),
                                        actual,
                                        1000000,
                                        () -> {}))
                .hasMessage("CDS_EXPORT_SUPPORTING_FILES_INVALID");
        assertThatThrownBy(
                        () ->
                                validate(
                                        Arrays.copyOf(readMe, readMe.length - 1),
                                        log,
                                        actual,
                                        1000000,
                                        () -> {}))
                .hasMessage("CDS_EXPORT_SUPPORTING_FILES_INVALID");
    }

    @Test
    void shouldRefuseMalformedUtf8_insteadOfReplacingAnInvalidName() {
        readMe = readMe.clone();
        readMe[0] = (byte) 0xff;
        assertThatThrownBy(this::validate)
                .isInstanceOf(java.nio.charset.CharacterCodingException.class);
    }

    @Test
    void shouldCheckTheCombinedByteBudget_withoutAcceptingOneValidFileAfterTheOtherExhaustsIt() {
        assertThatThrownBy(
                        () ->
                                validate(
                                        readMe,
                                        log,
                                        actual,
                                        readMe.length + log.length - 1,
                                        () -> {}))
                .hasMessage("CDS_EXPORT_SUPPORTING_FILES_RESOURCE_LIMIT");
    }

    @Test
    void shouldRefuseAChangedXmlIdentity_evenWhenItsShaAndOccurrenceCountsWereCopied() {
        var original = actual.getFirst();
        var x = original.xml();
        actual =
                List.of(
                        new CdsExportSupportingFilesValidator.Patient(
                                original.relativePath(),
                                original.naming(),
                                new CdsExportXmlValidationDto(
                                        "C28",
                                        x.legalFirstName(),
                                        x.legalLastName(),
                                        x.dateOfBirth(),
                                        x.occurrences(),
                                        x.textReports(),
                                        x.binaryReports(),
                                        x.externalReferences(),
                                        x.xmlSha256(),
                                        x.xmlBytes())));
        assertThatThrownBy(this::validate).hasMessage("CDS_EXPORT_SUPPORTING_FILES_INVALID");
    }

    @Test
    void shouldRefuseAChangedActualZipPath_evenWhenTheReadmeRetainsTheExpectedName() {
        var original = actual.getFirst();
        actual =
                List.of(
                        new CdsExportSupportingFilesValidator.Patient(
                                "other.xml", original.naming(), original.xml()));
        assertThatThrownBy(this::validate).hasMessage("CDS_EXPORT_SUPPORTING_FILES_INVALID");
    }

    @Test
    void shouldRefuseWrongPhysicianTotals_withoutDroppingOrDuplicatingPatientRows() {
        physicians = List.of(new CdsExportPhysicianSummaryDto("Alice", "Fixture", "123456", 2));
        assertThatThrownBy(this::validate).hasMessage("CDS_EXPORT_SUPPORTING_FILES_INVALID");
    }

    @Test
    void shouldPropagateCancellation_beforeReturningAnySupportingReceipt() {
        assertThatThrownBy(
                        () ->
                                validate(
                                        readMe,
                                        log,
                                        actual,
                                        1000000,
                                        () -> {
                                            throw new java.util.concurrent.CancellationException(
                                                    "SYNTHETIC_CANCELLED");
                                        }))
                .hasMessage("SYNTHETIC_CANCELLED");
    }

    @Test
    void shouldAuthenticateEofAndLeaveCallerStreamsOpen_afterBothFilesValidate() throws Exception {
        class OwnedInput extends ByteArrayInputStream {
            boolean closed;

            OwnedInput(byte[] data) {
                super(data);
            }

            @Override
            public void close() {
                closed = true;
            }
        }
        var text = new OwnedInput(readMe);
        var csv = new OwnedInput(log);
        new CdsExportSupportingFilesValidator()
                .validate(
                        text,
                        csv,
                        metadata,
                        physicians.iterator(),
                        actual.iterator(),
                        100,
                        100,
                        1000000,
                        () -> {});
        assertThat(text.available()).isZero();
        assertThat(csv.available()).isZero();
        assertThat(text.closed).isFalse();
        assertThat(csv.closed).isFalse();
    }

    @Test
    void
            shouldRefuseDuplicatePatientIdentifiers_evenWhenDifferentNamesProduceDifferentValidFilePaths()
                    throws Exception {
        var first = actual.getFirst();
        var name = first.naming();
        var secondName =
                new CdsExportPatientFileDto(
                        name.physicianFirstName(),
                        name.physicianLastName(),
                        name.physicianBillingNumber(),
                        "Ada2",
                        name.patientLastName(),
                        name.senderPatientId(),
                        name.dateOfBirth());
        var x = first.xml();
        var secondXml =
                new CdsExportXmlValidationDto(
                        x.senderPatientId(),
                        "Ada2",
                        x.legalLastName(),
                        x.dateOfBirth(),
                        x.occurrences(),
                        0,
                        0,
                        0,
                        x.xmlSha256(),
                        x.xmlBytes());
        var path = new CdsExportPathComposer().compose(secondName);
        var second =
                new CdsExportSupportingFilesValidator.Patient(
                        metadata.exportRoot()
                                + "/"
                                + path.physicianFolder()
                                + "/"
                                + path.patientXmlFile(),
                        secondName,
                        secondXml);
        actual = new ArrayList<>(List.of(first, second));
        actual.sort(Comparator.comparing(CdsExportSupportingFilesValidator.Patient::relativePath));
        physicians = List.of(new CdsExportPhysicianSummaryDto("Alice", "Fixture", "123456", 2));
        var text = new ByteArrayOutputStream();
        var events = new ByteArrayOutputStream();
        new CdsExportSupportingFilesComposer()
                .compose(
                        text,
                        events,
                        metadata,
                        physicians.iterator(),
                        actual.stream()
                                .map(
                                        p ->
                                                new CdsExportPatientSummaryDto(
                                                        p.naming(),
                                                        p.xml().occurrences(),
                                                        null,
                                                        null,
                                                        p.xml().xmlSha256(),
                                                        p.xml().xmlBytes()))
                                .iterator(),
                        () -> {});
        readMe = text.toByteArray();
        log = events.toByteArray();
        assertThatThrownBy(this::validate).hasMessage("CDS_EXPORT_SUPPORTING_FILES_INVALID");
    }

    @Test
    void shouldRejectAnUnboundedReadmeLine_beforeRetainingItsEntireValue() {
        byte[] oversized =
                ("Physician group: \"" + "x".repeat(65537) + "\"\n")
                        .getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> validate(oversized, log, actual, 1000000, () -> {}))
                .hasMessage("CDS_EXPORT_SUPPORTING_FILES_RESOURCE_LIMIT");
    }

    @Test
    void shouldCountBothReportRepresentations_withoutTreatingThemAsSeparateSourceReports()
            throws Exception {
        metadata =
                new CdsExportPackageMetadataDto(
                        metadata.exportRoot(),
                        metadata.physicianGroup(),
                        metadata.groupContactInformation(),
                        metadata.applicationVersion(),
                        metadata.exportTime(),
                        metadata.clinicalZone(),
                        metadata.profile(),
                        Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.REPORTS),
                        metadata.auditAccessDescription());
        var original = actual.getFirst();
        var x = original.xml();
        var xml =
                new CdsExportXmlValidationDto(
                        x.senderPatientId(),
                        x.legalFirstName(),
                        x.legalLastName(),
                        x.dateOfBirth(),
                        Map.of(CdsCategory.DEMOGRAPHICS, 1L, CdsCategory.REPORTS, 2L),
                        2,
                        1,
                        1,
                        x.xmlSha256(),
                        x.xmlBytes());
        actual =
                List.of(
                        new CdsExportSupportingFilesValidator.Patient(
                                original.relativePath(), original.naming(), xml));
        composeActual();
        assertThat(validate().patientFiles()).isEqualTo(1);
        log = replace(log, ",\"2\",\"1\"\n", ",\"3\",\"1\"\n");
        assertThatThrownBy(this::validate).hasMessage("CDS_EXPORT_SUPPORTING_FILES_INVALID");
    }

    @Test
    void shouldReconcileMultiplePhysicians_inTheActualArchiveOrder() throws Exception {
        var first = actual.getFirst();
        var x = first.xml();
        var name =
                new CdsExportPatientFileDto(
                        "Zoe",
                        "Fixture",
                        "654321",
                        "Grace",
                        "Fixture",
                        "C28",
                        LocalDate.of(1990, 2, 3));
        var xml =
                new CdsExportXmlValidationDto(
                        "C28",
                        "Grace",
                        "Fixture",
                        "1990-02-03",
                        x.occurrences(),
                        0,
                        0,
                        0,
                        "b".repeat(64),
                        4321);
        var path = new CdsExportPathComposer().compose(name);
        actual =
                List.of(
                        first,
                        new CdsExportSupportingFilesValidator.Patient(
                                metadata.exportRoot()
                                        + "/"
                                        + path.physicianFolder()
                                        + "/"
                                        + path.patientXmlFile(),
                                name,
                                xml));
        physicians =
                List.of(
                        physicians.getFirst(),
                        new CdsExportPhysicianSummaryDto("Zoe", "Fixture", "654321", 1));
        composeActual();
        var result = validate();
        assertThat(result.physicians()).isEqualTo(2);
        assertThat(result.patientFiles()).isEqualTo(2);
        actual = List.of(actual.getLast(), actual.getFirst());
        assertThatThrownBy(this::validate).hasMessage("CDS_EXPORT_SUPPORTING_FILES_INVALID");
    }

    @Test
    void shouldEnforceTheExactByteLimit_includingTheLastTerminator() throws Exception {
        assertThat(
                        validate(readMe, log, actual, readMe.length + log.length, () -> {})
                                .patientFiles())
                .isEqualTo(1);
    }

    @Test
    void shouldRefuseMalformedCsvQuoting_evenWhenTheApparentValuesMatch() {
        log = replace(log, "\"C27\"", "C27");
        assertThatThrownBy(this::validate).hasMessage("CDS_EXPORT_SUPPORTING_FILES_INVALID");
    }

    private void composeActual() throws IOException {
        var text = new ByteArrayOutputStream();
        var events = new ByteArrayOutputStream();
        new CdsExportSupportingFilesComposer()
                .compose(
                        text,
                        events,
                        metadata,
                        physicians.iterator(),
                        actual.stream()
                                .map(
                                        p ->
                                                new CdsExportPatientSummaryDto(
                                                        p.naming(),
                                                        p.xml().occurrences(),
                                                        metadata.categories()
                                                                        .contains(
                                                                                CdsCategory.REPORTS)
                                                                ? p.xml().textReports()
                                                                : null,
                                                        metadata.categories()
                                                                        .contains(
                                                                                CdsCategory.REPORTS)
                                                                ? p.xml().binaryReports()
                                                                : null,
                                                        p.xml().xmlSha256(),
                                                        p.xml().xmlBytes()))
                                .iterator(),
                        () -> {});
        readMe = text.toByteArray();
        log = events.toByteArray();
    }
}
