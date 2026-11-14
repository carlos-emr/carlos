/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeExtensionContract;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportNativeHistoryFixture;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsClinicalDiagnosticDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A note or CPP item holding a character XML cannot carry, sent from one CARLOS to another: the
 * exporter's own document is parsed by the importer, which must file the stored text, prove the
 * carrier first, and never guess when it cannot.
 */
@Tag("unit")
@Tag("cds-import")
class CdsHistoryExactNarrativeUnitTest {
    private static final long LIMIT = 262144;
    /** A bell, a vertical tab and U+FFFF: none can stand in an XML document. */
    private static final String STORED = "Asthma\u0007 in mother;\u000b two  spaces ￿\r\nline two";

    @TempDir Path directory;
    private final CdsFieldParser fields = new CdsFieldParser();

    private CdsRecordDto record(byte[] document, String section) throws Exception {
        Path root = Files.createTempDirectory(directory, "history-");
        var records = new ArrayList<CdsRecordDto>();
        new CdsXmlParser()
                .parse(
                        Files.write(root.resolve("patient.xml"), document),
                        root.resolve("parsed"),
                        CdsInputLimitsDto.defaults(),
                        () -> false,
                        records::add);
        return records.stream().filter(r -> r.section().equals(section)).findFirst().orElseThrow();
    }

    private CdsFieldDto field(CdsRecordDto record, String path) throws Exception {
        CdsFieldDto[] found = {null};
        fields.forEachField(
                record,
                field -> {
                    if (CdsFieldParser.unindexed(field.path()).equals(path)) found[0] = field;
                });
        return found[0];
    }

    private CdsHistoryPersister persister() {
        return new CdsHistoryPersister(
                fields,
                null,
                null,
                null,
                null,
                org.mockito.Mockito.mock(
                        io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO.class),
                org.mockito.Mockito.mock(
                        io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementIssueDAO.class),
                org.mockito.Mockito.mock(io.github.carlos_emr.carlos.casemgmt.dao.IssueDAO.class),
                org.mockito.Mockito.mock(
                        io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteExtDAO.class));
    }

    private String noteText(String... bodies) throws Exception {
        var record =
                record(
                        CdsExportNativeHistoryFixture.document(
                                CdsCategory.CLINICAL_NOTES, null, bodies),
                        "ClinicalNotes");
        return fields.readLogicalText(record, field(record, "MyClinicalNotesContent"), 1_000_000);
    }

    /** The sender's text with each stored body back in its place. */
    private static String expected(String shown, String... bodies) {
        String expected = shown;
        int from = 0;
        for (String body : bodies) {
            String written = CdsNativeExtensionContract.xmlElementText(body);
            int at = expected.indexOf(written, from);
            assertThat(at).isGreaterThanOrEqualTo(0);
            expected = expected.substring(0, at) + body + expected.substring(at + written.length());
            from = at + body.length();
        }
        return expected;
    }

    @Test
    void shouldRestoreTheExactTextOfACppItem_fromTheDocumentTheExporterWrites() throws Exception {
        var record =
                record(
                        CdsExportNativeHistoryFixture.document(
                                CdsCategory.FAMILY_HISTORY, "FamHistory", STORED),
                        "FamilyHistory");
        var state = new CdsHistoryResidualReader(fields).read(record);
        assertThat(state.revisionDetails()).isNotNull();
        String shown =
                fields.readLogicalText(
                        record, field(record, "ProblemDiagnosisProcedureDescription"), 1000);
        assertThat(shown)
                .isEqualTo(CdsNativeExtensionContract.xmlElementText(STORED))
                .isNotEqualTo(STORED);
        var notices = new ArrayList<CdsClinicalDiagnosticDto>();

        String restored =
                persister().exactItemText(record, state.revisionDetails(), shown, LIMIT, notices::add);

        assertThat(restored).isEqualTo(STORED);
        assertThat(notices).isEmpty();
    }

    @Test
    void shouldRestoreTheExactText_whenTheItemTravelsInItsNarrativeCarrier() throws Exception {
        // PersonalHistory has no description element, and a problem longer than its element
        // (250) has none either: both travel in CARLOS.History.Narrative.v1, written with
        // U+FFFD, and the stored text is in the revision details.
        String longer = STORED + " " + "x".repeat(300);
        for (var sent :
                List.of(
                        new String[] {"PersonalHistory", "SocHistory", STORED},
                        new String[] {"ProblemList", "Concerns", longer})) {
            var category =
                    sent[0].equals("PersonalHistory")
                            ? CdsCategory.PERSONAL_HISTORY
                            : CdsCategory.PROBLEM_LIST;
            var record =
                    record(CdsExportNativeHistoryFixture.document(category, sent[1], sent[2]), sent[0]);
            var state = new CdsHistoryResidualReader(fields).read(record);
            assertThat(state.narrative()).as(sent[0]).isNotNull();
            String shown =
                    CdsNativeExtensionContract.decodeNarrative(
                            fields.readLogicalText(record, state.narrative(), 2_000_000));
            assertThat(shown).isEqualTo(CdsNativeExtensionContract.xmlElementText(sent[2]));
            var notices = new ArrayList<CdsClinicalDiagnosticDto>();

            assertThat(
                            persister()
                                    .exactItemText(
                                            record,
                                            state.revisionDetails(),
                                            shown,
                                            LIMIT,
                                            notices::add))
                    .as(sent[0])
                    .isEqualTo(sent[2]);
            assertThat(notices).isEmpty();
        }
    }

    @Test
    void shouldRestoreTheCurrentText_whateverTheLengthOfAnEarlierRevision() throws Exception {
        // Every earlier revision's narrative travels in the details too. Only the current one is
        // read: an earlier one longer than any note the receiver stores does not stop it.
        String earlier = "Draft ".repeat(5000);
        var record =
                record(
                        CdsExportNativeHistoryFixture.document(
                                CdsCategory.FAMILY_HISTORY, "FamHistory", earlier, STORED),
                        "FamilyHistory");
        var state = new CdsHistoryResidualReader(fields).read(record);
        String shown = CdsNativeExtensionContract.xmlElementText(STORED);
        var notices = new ArrayList<CdsClinicalDiagnosticDto>();

        assertThat(persister().exactItemText(record, state.revisionDetails(), shown, 1000, notices::add))
                .isEqualTo(STORED);
        assertThat(notices).isEmpty();
    }

    @Test
    void shouldLeaveAnItemWithNothingToRestore_withoutAReport() throws Exception {
        var record =
                record(
                        CdsExportNativeHistoryFixture.document(
                                CdsCategory.FAMILY_HISTORY, "FamHistory", "Asthma in mother"),
                        "FamilyHistory");
        var state = new CdsHistoryResidualReader(fields).read(record);
        var notices = new ArrayList<CdsClinicalDiagnosticDto>();

        assertThat(
                        persister()
                                .exactItemText(
                                        record,
                                        state.revisionDetails(),
                                        "Asthma in mother",
                                        LIMIT,
                                        notices::add))
                .isEqualTo("Asthma in mother");
        // U+FFFD the sender typed itself: the details carry no other text for it.
        var typed =
                record(
                        CdsExportNativeHistoryFixture.document(
                                CdsCategory.FAMILY_HISTORY, "FamHistory", "Shown � here"),
                        "FamilyHistory");
        assertThat(
                        persister()
                                .exactItemText(
                                        typed,
                                        new CdsHistoryResidualReader(fields)
                                                .read(typed)
                                                .revisionDetails(),
                                        "Shown � here",
                                        LIMIT,
                                        notices::add))
                .isEqualTo("Shown � here");
        assertThat(notices).isEmpty();
    }

    @Test
    void shouldReportAndKeepTheElement_whenTheCarriedTextDoesNotProveItself() throws Exception {
        var record =
                record(
                        CdsExportNativeHistoryFixture.document(
                                CdsCategory.FAMILY_HISTORY, "FamHistory", STORED),
                        "FamilyHistory");
        var state = new CdsHistoryResidualReader(fields).read(record);
        var notices = new ArrayList<CdsClinicalDiagnosticDto>();
        // The element says something else than the carried text shows: nothing is guessed.
        assertThat(
                        persister()
                                .exactItemText(
                                        record,
                                        state.revisionDetails(),
                                        "Diabetes� in father",
                                        LIMIT,
                                        notices::add))
                .isEqualTo("Diabetes� in father");
        assertThat(notices)
                .extracting(CdsClinicalDiagnosticDto::code)
                .containsExactly(CdsHistoryPersister.EXACT_NARRATIVE_NOT_APPLIED);
        // Details that are not the document the exporter writes are reported the same way.
        notices.clear();
        String shown = CdsNativeExtensionContract.xmlElementText(STORED);
        var other =
                record(
                        new String(
                                        CdsExportNativeHistoryFixture.document(
                                                CdsCategory.FAMILY_HISTORY, "FamHistory", STORED),
                                        StandardCharsets.UTF_8)
                                .replace("{\"version\":1,", "{\"version\":2,")
                                .getBytes(StandardCharsets.UTF_8),
                        "FamilyHistory");
        assertThat(
                        persister()
                                .exactItemText(
                                        other,
                                        new CdsHistoryResidualReader(fields)
                                                .read(other)
                                                .revisionDetails(),
                                        shown,
                                        LIMIT,
                                        notices::add))
                .isEqualTo(shown);
        assertThat(notices).hasSize(1);
    }

    @Test
    void shouldRestoreEveryRevisionOfAnEncounterNote_fromTheDocumentTheExporterWrites()
            throws Exception {
        // Three revisions: the first and the last hold characters XML cannot carry, and the
        // second quotes a revision heading in its own text.
        String first = "Seen for cough\u0007.", second = "Quoted:\n\n[Revision 3]\nnot a heading",
                third = "Final\u000b text ￿";
        for (String[] bodies : List.of(new String[] {STORED}, new String[] {first, second, third})) {
            String shown = noteText(bodies);
            assertThat(shown).contains("�");
            var notices = new ArrayList<CdsClinicalDiagnosticDto>();

            String restored = CdsHistoryPersister.exactNoteText(shown, LIMIT, notices::add);

            assertThat(notices).isEmpty();
            assertThat(restored).isEqualTo(expected(shown, bodies));
            assertThat(restored.substring(0, restored.lastIndexOf("[CARLOS source note details]")))
                    .doesNotContain("�");
        }
    }

    @Test
    void shouldPlaceEachBodyByItsLength_whenABodyQuotesItsOwnHeading() throws Exception {
        // The second revision quotes its own heading: found by its text alone, the heading would
        // point into that body. The lengths the exporter states put each body in its one place.
        String first = "Seen\u0007 today.",
                second = "Copied:\n\n[Revision 2]\nSeen today.\n\n[Revision 2]\nagain";
        String shown = noteText(first, second);
        var notices = new ArrayList<CdsClinicalDiagnosticDto>();

        String restored = CdsHistoryPersister.exactNoteText(shown, LIMIT, notices::add);

        assertThat(notices).isEmpty();
        assertThat(restored).isEqualTo(expected(shown, first, second));
    }

    @Test
    void shouldReportAndChangeNothing_whenTheBodiesCannotBePlaced() throws Exception {
        String first = "Seen\u0007 today.", second = "Plan unchanged.";
        String shown = noteText(first, second);
        var notices = new ArrayList<CdsClinicalDiagnosticDto>();
        // A body length that does not match the text, and details without lengths.
        for (String altered :
                List.of(
                        shown.replace("\"bodyLength\":15", "\"bodyLength\":14"),
                        shown.replaceAll(",\"bodyLength\":[0-9]+", ""))) {
            assertThat(altered).isNotEqualTo(shown);
            notices.clear();
            assertThat(CdsHistoryPersister.exactNoteText(altered, LIMIT, notices::add))
                    .isEqualTo(altered);
            assertThat(notices)
                    .extracting(CdsClinicalDiagnosticDto::code)
                    .containsExactly(CdsHistoryPersister.EXACT_NARRATIVE_NOT_APPLIED);
        }
    }

    @Test
    void shouldLeaveANoteThatIsNotACarlosExportAlone_andReportDetailsItCannotPlace()
            throws Exception {
        var notices = new ArrayList<CdsClinicalDiagnosticDto>();
        // Text from another sender, even one that ends like revision details: not a carrier.
        for (String text :
                List.of(
                        "Plain note",
                        "Note �" + CdsNativeExtensionContract.NOTE_DETAILS_HEADING + "not json",
                        "Note �"
                                + CdsNativeExtensionContract.NOTE_DETAILS_HEADING
                                + "{\"version\":1,\"revisions\":[{\"revision\":1}]}"))
            assertThat(CdsHistoryPersister.exactNoteText(text, LIMIT, notices::add)).isEqualTo(text);
        assertThat(notices).isEmpty();
        // Details that carry a body the text does not show are reported and nothing changes.
        String hostile =
                "Seen for cough�"
                        + CdsNativeExtensionContract.NOTE_DETAILS_HEADING
                        + "{\"version\":1,\"revisions\":[{\"revision\":1,\"exactNarrative\":"
                        + "\"Something else\\u0007\",\"bodyLength\":15}]}";
        assertThat(CdsHistoryPersister.exactNoteText(hostile, LIMIT, notices::add)).isEqualTo(hostile);
        assertThat(notices)
                .extracting(CdsClinicalDiagnosticDto::code)
                .containsExactly(CdsHistoryPersister.EXACT_NARRATIVE_NOT_APPLIED);
    }
}
