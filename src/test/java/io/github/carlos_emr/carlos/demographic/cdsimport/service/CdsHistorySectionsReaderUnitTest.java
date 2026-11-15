/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementIssueDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteExtDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.IssueDAO;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportNativeHistoryFixture;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsClinicalDiagnosticDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A CPP item its sender's chart files under several CPP sections, sent from one CARLOS to
 * another: the exporter writes it once with every section tag in its revision details, and the
 * importer reads the other tags from the document the exporter writes, links each the catalogue
 * holds, and never guesses at a list that does not prove itself.
 */
@Tag("unit")
@Tag("cds-import")
class CdsHistorySectionsReaderUnitTest {
    @TempDir Path directory;
    private final CdsFieldParser fields = new CdsFieldParser();
    private final IssueDAO catalogue = mock(IssueDAO.class);

    private CdsRecordDto record(byte[] document, String section) throws Exception {
        Path root = Files.createTempDirectory(directory, "sections-");
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

    private CdsHistoryPersister persister() {
        return new CdsHistoryPersister(
                fields,
                null,
                null,
                null,
                null,
                mock(CaseManagementNoteDAO.class),
                mock(CaseManagementIssueDAO.class),
                catalogue,
                mock(CaseManagementNoteExtDAO.class));
    }

    /** The receiving catalogue: one entry for each of these section tags, none for any other. */
    private void catalogueHolds(String... codes) {
        when(catalogue.findCandidatesByCode(anyString())).thenReturn(List.of());
        for (String code : codes) {
            var entry = new Issue();
            entry.setCode(code);
            when(catalogue.findCandidatesByCode(code)).thenReturn(List.of(entry));
        }
    }

    private List<String> otherSections(
            CdsRecordDto record, List<CdsClinicalDiagnosticDto> notices) throws Exception {
        var state = new CdsHistoryResidualReader(fields).read(record);
        return persister().otherSections(record, state.revisionDetails(), notices::add);
    }

    private static List<String> read(String details, String own) throws Exception {
        return CdsHistorySectionsReader.otherSections(new StringReader(details), own);
    }

    @Test
    void shouldReadTheOtherSections_fromTheDocumentTheExporterWrites() throws Exception {
        catalogueHolds("FamHistory", "MedHistory", "Concerns");
        var record =
                record(
                        CdsExportNativeHistoryFixture.filedUnderDocument(
                                "Mother: type 2 diabetes", "Concerns", "MedHistory", "FamHistory"),
                        "FamilyHistory");
        var notices = new ArrayList<CdsClinicalDiagnosticDto>();

        assertThat(otherSections(record, notices)).containsExactly("MedHistory", "Concerns");
        assertThat(notices).isEmpty();
    }

    @Test
    void shouldReadNoOtherSection_fromAnItemOfOneSection() throws Exception {
        catalogueHolds("FamHistory", "MedHistory");
        var record =
                record(
                        CdsExportNativeHistoryFixture.document(
                                CdsCategory.FAMILY_HISTORY, "FamHistory", "Father: hypertension"),
                        "FamilyHistory");
        var notices = new ArrayList<CdsClinicalDiagnosticDto>();

        assertThat(otherSections(record, notices)).isEmpty();
        assertThat(notices).isEmpty();
    }

    @Test
    void shouldReportASectionTheCatalogueLacks_asNotRestored() throws Exception {
        // The receiving chart has no single entry for one tag: the item keeps the others, and
        // the record's retained source keeps every tag.
        catalogueHolds("SocHistory", "Reminders");
        var record =
                record(
                        CdsExportNativeHistoryFixture.filedUnderDocument(
                                "Lives alone", "SocHistory", "Concerns", "Reminders"),
                        "PersonalHistory");
        var notices = new ArrayList<CdsClinicalDiagnosticDto>();

        assertThat(otherSections(record, notices)).containsExactly("Reminders");
        assertThat(notices)
                .extracting(CdsClinicalDiagnosticDto::code)
                .containsExactly(CdsHistoryPersister.SECTION_NOT_RESTORED);
    }

    @Test
    void shouldReadTheSectionsOfAnEditedImportedItem_fromItsNativeHistory() throws Exception {
        // An imported item the sender edited: its own details are wrapped as nativeHistory.
        String details =
                "{\"version\":1,\"originalImportedProjection\":\"Item\",\"nativeHistory\":"
                        + "{\"version\":1,\"revisions\":[{\"revision\":1,\"narrative\":\"Item\"}],"
                        + "\"sections\":[\"SocHistory\",\"Reminders\"]}}";

        assertThat(read(details, "SocHistory")).containsExactly("Reminders");
        assertThat(read(details, "Reminders")).containsExactly("SocHistory");
    }

    @Test
    void shouldGiveTheOtherSectionsInRecordOrder_whateverOrderTheyAreListedIn()
            throws Exception {
        String details = "{\"version\":1,\"sections\":[\"Reminders\",\"FamHistory\",\"SocHistory\"]}";

        assertThat(read(details, "FamHistory")).containsExactly("SocHistory", "Reminders");
    }

    @Test
    void shouldSkipEveryOtherValue_withoutHoldingIt() throws Exception {
        // A narrative far longer than any tag, and members this reader does not know.
        String details =
                "{\"version\":1,\"revisions\":[{\"narrative\":\""
                        + "x".repeat(200_000)
                        + "\",\"issues\":[{\"definition.code\":\"FamHistory\"}]}],"
                        + "\"importedSource\":{\"elements\":[]},"
                        + "\"sections\":[\"FamHistory\",\"MedHistory\"]}";

        assertThat(read(details, "FamHistory")).containsExactly("MedHistory");
        assertThat(read("{\"version\":1,\"revisions\":[]}", "FamHistory")).isEmpty();
    }

    @Test
    void shouldRefuseSectionsThatDoNotProveThemselves_insteadOfGuessing() throws Exception {
        for (String details :
                List.of(
                        "{\"sections\":[\"FamHistory\",\"Diabetes\"]}",
                        "{\"sections\":[\"FamHistory\",\"MedHistory\",\"FamHistory\"]}",
                        "{\"sections\":[\"FamHistory\"]}",
                        "{\"sections\":[\"MedHistory\",\"Concerns\"]}",
                        "{\"sections\":\"FamHistory,MedHistory\"}",
                        "{\"sections\":[\"FamHistory\",1]}",
                        "{\"sections\":[\"FamHistory\",\"" + "M".repeat(100) + "\"]}",
                        "{\"sections\":[\"FamHistory\",\"MedHistory\"],"
                                + "\"nativeHistory\":{\"sections\":[\"FamHistory\",\"Concerns\"]}}",
                        "{\"sections\":[\"FamHistory\",\"MedHistory\"],"
                                + "\"sections\":[\"FamHistory\",\"Concerns\"]}",
                        "{\"sections\":[\"FamHistory\",\"MedHistory\"]} {}",
                        "[\"FamHistory\",\"MedHistory\"]",
                        "not json"))
            assertThatThrownBy(() -> read(details, "FamHistory"))
                    .as(details)
                    .hasMessage("CDS_HISTORY_SECTIONS_MALFORMED");
        // Only a CPP item has sections.
        assertThatThrownBy(() -> read("{\"sections\":[\"FamHistory\",\"MedHistory\"]}", "OMeds"))
                .hasMessage("CDS_HISTORY_SECTIONS_MALFORMED");
    }

    @Test
    void shouldFileTheItemUnderItsOwnSectionOnly_whenItsSectionsCannotBeRead() throws Exception {
        catalogueHolds("FamHistory", "MedHistory");
        var exported =
                new String(
                        CdsExportNativeHistoryFixture.filedUnderDocument(
                                "Mother: asthma", "FamHistory", "MedHistory"),
                        java.nio.charset.StandardCharsets.UTF_8);
        String listed = "\"sections\":[\"FamHistory\",\"MedHistory\"]";
        assertThat(exported).contains(listed);
        var record =
                record(
                        exported.replace(listed, "\"sections\":[\"FamHistory\",\"Diabetes\"]")
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        "FamilyHistory");
        var notices = new ArrayList<CdsClinicalDiagnosticDto>();

        assertThat(otherSections(record, notices)).isEmpty();
        assertThat(notices)
                .extracting(CdsClinicalDiagnosticDto::code)
                .containsExactly(CdsHistoryPersister.SECTIONS_NOT_APPLIED);
        assertThat(Set.of(CdsHistoryPersister.SECTIONS_NOT_APPLIED, CdsHistoryPersister.SECTION_NOT_RESTORED))
                .containsExactlyInAnyOrder(
                        "CDS_HISTORY_SECTIONS_NOT_APPLIED", "CDS_HISTORY_SECTION_NOT_RESTORED");
    }
}
