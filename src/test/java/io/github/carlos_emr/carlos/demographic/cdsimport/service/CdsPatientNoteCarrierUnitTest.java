/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.commn.model.DemographicCust;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeExtensionContract;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportPatientNoteFixture;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsClinicalDiagnosticDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A patient note that NoteAboutPatient cannot hold exactly, sent from one CARLOS to another: the
 * exporter's own document is parsed by the importer, which must file the note the sender's chart
 * showed, prove the carried column first, and never guess when it cannot.
 */
@Tag("unit")
@Tag("cds-import")
class CdsPatientNoteCarrierUnitTest {
    @TempDir Path directory;
    private final CdsFieldParser fields = new CdsFieldParser();
    private final CdsDemographicDetailsService details = new CdsDemographicDetailsService(fields, null);

    private CdsRecordDto demographics(byte[] document) throws Exception {
        Path root = Files.createTempDirectory(directory, "note-");
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

    private String element(CdsRecordDto record) throws Exception {
        return fields.firstText(record, "NoteAboutPatient", 131072);
    }

    @Test
    void shouldRestoreANoteHoldingCharactersXmlCannotCarry_fromTheDocumentTheExporterWrites()
            throws Exception {
        // A bell, a vertical tab and U+FFFF: none can stand in an XML document.
        String typed = "Prefers mornings\u0007.\u000b Interpreter ￿ needed\r\nsecond line";
        var record =
                demographics(
                        CdsExportPatientNoteFixture.document(DemographicCust.wrapPatientNotes(typed)));
        String element = element(record);
        assertThat(element).isEqualTo(CdsNativeExtensionContract.xmlElementText(typed));
        var notices = new ArrayList<CdsClinicalDiagnosticDto>();

        assertThat(details.carriedPatientNote(record, element, notices::add)).isEqualTo(typed);
        assertThat(notices).isEmpty();
    }

    @Test
    void shouldRestoreTheWholeNote_whenTheElementHoldsItsShortenedForm() throws Exception {
        // Before: a note over 64,000 characters arrived with no patient note at all.
        String typed = "Long history. ".repeat(4700) + "\u0007 end";
        assertThat(typed.length()).isGreaterThan(CdsNativeExtensionContract.PATIENT_NOTE_LENGTH);
        var record =
                demographics(
                        CdsExportPatientNoteFixture.document(DemographicCust.wrapPatientNotes(typed)));
        String element = element(record);
        assertThat(element)
                .endsWith(CdsNativeExtensionContract.PATIENT_NOTE_SHORTENED_MARK)
                .hasSize(CdsNativeExtensionContract.PATIENT_NOTE_LENGTH);
        var notices = new ArrayList<CdsClinicalDiagnosticDto>();

        assertThat(details.carriedPatientNote(record, element, notices::add)).isEqualTo(typed);
        assertThat(notices).isEmpty();
    }

    @Test
    void shouldShowWhatTheSendersChartShowed_forALegacyFrame() throws Exception {
        // The element is the note as the sender's chart shows it; nothing needs restoring.
        String column = "prefix<unotes>Legacy note</unotes>";
        var record = demographics(CdsExportPatientNoteFixture.document(column));
        var notices = new ArrayList<CdsClinicalDiagnosticDto>();

        assertThat(details.carriedPatientNote(record, element(record), notices::add))
                .isEqualTo(DemographicCust.patientNotesText(column))
                .isEqualTo("Legacy note");
        assertThat(notices).isEmpty();
    }

    @Test
    void shouldKeepTheElement_whenTheSenderTypedTheReplacementCharacterItself() throws Exception {
        // No carrier: the U+FFFD is the sender's own text.
        String typed = "Shown as � on the sender's chart too";
        var record =
                demographics(
                        CdsExportPatientNoteFixture.document(DemographicCust.wrapPatientNotes(typed)));
        var notices = new ArrayList<CdsClinicalDiagnosticDto>();

        assertThat(details.carriedPatientNote(record, element(record), notices::add)).isEqualTo(typed);
        assertThat(notices).isEmpty();
    }

    @Test
    void shouldReportAndKeepTheElement_whenTheCarriedNoteDoesNotProveItself() throws Exception {
        String typed = "Prefers mornings\u0007 only";
        String document =
                new String(
                        CdsExportPatientNoteFixture.document(DemographicCust.wrapPatientNotes(typed)),
                        StandardCharsets.UTF_8);
        // The record now carries another note than the element shows: nothing is guessed.
        String other = document.replace("mornings\\u0007", "evenings\\u0007");
        assertThat(other).isNotEqualTo(document);
        var record = demographics(other.getBytes(StandardCharsets.UTF_8));
        String element = element(record);
        var notices = new ArrayList<CdsClinicalDiagnosticDto>();

        assertThat(details.carriedPatientNote(record, element, notices::add)).isEqualTo(element);
        assertThat(notices)
                .extracting(CdsClinicalDiagnosticDto::code)
                .containsExactly(CdsDemographicDetailsService.PATIENT_NOTE_CARRIER_NOT_APPLIED);
    }

    @Test
    void shouldNeverBringBackACarriedNote_whenTheChartHasNoNote() throws Exception {
        // A record kept from an earlier sender still carries a long note, but the chart that
        // sent this file has none now: an absent element is never restored.
        String typed = "Long history. ".repeat(4700);
        String document =
                new String(
                        CdsExportPatientNoteFixture.document(DemographicCust.wrapPatientNotes(typed)),
                        StandardCharsets.UTF_8);
        int start = document.indexOf("<NoteAboutPatient>");
        int end = document.indexOf("</NoteAboutPatient>") + "</NoteAboutPatient>".length();
        assertThat(start).isPositive();
        var record =
                demographics(
                        (document.substring(0, start) + document.substring(end))
                                .getBytes(StandardCharsets.UTF_8));
        var notices = new ArrayList<CdsClinicalDiagnosticDto>();

        assertThat(element(record)).isNull();
        assertThat(details.carriedPatientNote(record, null, notices::add)).isNull();
        assertThat(notices).isEmpty();
    }
}
