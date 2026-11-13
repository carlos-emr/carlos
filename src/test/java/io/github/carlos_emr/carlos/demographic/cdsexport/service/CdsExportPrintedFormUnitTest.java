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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDateDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportIdentityDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportReportReferenceDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportXmlLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryOccurrenceService.PrintOutcome;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryOccurrenceService.PrintRefusal;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryOccurrenceService.Printed;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryOccurrenceService.Printer;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryPlanner.Group;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryPlanner.Row;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryPlanner.Value;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryRegistry.Kind;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.ConsultationLetterDto;
import io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.ConsultationPDFCreator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The printed form beside a data summary: written as its own schema-valid Reports entry, made
 * only for identities that may read the record, never holding the patient when it cannot be made
 * (the summary notes the reason category) and reproducible for an unchanged record.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportPrintedFormUnitTest {
    private static final String KEY = "a".repeat(64);

    private static Row row(String table, long id, String... columns) {
        var values = new LinkedHashMap<String, Value>();
        for (int i = 0; i < columns.length; i += 2)
            values.put(columns[i], columns[i + 1] == null ? Value.sqlNull() : Value.text(columns[i + 1]));
        return new Row(table, CdsExportPatientTableSourceReader.key(id), values);
    }

    private static Group request() {
        return new Group(
                Kind.CONSULTATION_REQUEST,
                "consultationRequests",
                "5",
                List.of(row("consultationRequests", 5, "requestId", "5", "referalDate", "2024-02-03",
                        "providerNo", "101")),
                0,
                0,
                new CdsExportDateDto(CdsExportDateDto.Precision.DATE, "2024-02-03"),
                false);
    }

    private static CdsExportRunControl control(boolean readable, List<String> asked) {
        return new CdsExportRunControl() {
            @Override
            public void checkpoint() {}

            @Override
            public void captureCheckpoint() {}

            @Override
            public void authorizePatient(int patient) {}

            @Override
            public void authorizeCategory(int patient, CdsCategory category, boolean sensitiveLaboratory) {}

            @Override
            public boolean recordReadable(int patient, String object) {
                asked.add(patient + object);
                return readable;
            }
        };
    }

    private static Printer printer(AtomicInteger calls, Printed printed, RuntimeException failure, String refusal) {
        return new Printer() {
            @Override
            public boolean prints(Group group) {
                return group.kind() == Kind.CONSULTATION_REQUEST;
            }

            @Override
            public String readObject() {
                return "_con";
            }

            @Override
            public Printed print(Group group, CdsExportDataSummaryOccurrenceService.Plan plan) throws IOException {
                calls.incrementAndGet();
                if (failure != null) throw failure;
                if (refusal != null) throw new PrintRefusal(refusal);
                return printed;
            }
        };
    }

    private static CdsExportDataSummaryOccurrenceService service(Printer printer) {
        return new CdsExportDataSummaryOccurrenceService(
                mock(CdsExportNativeCaptureService.class),
                mock(CdsExportStagingFactory.class),
                (group, patient) -> new byte[0],
                Map.of(),
                List.of(printer));
    }

    private static CdsExportDataSummaryOccurrenceService.Plan plan(Group group) {
        return new CdsExportDataSummaryOccurrenceService.Plan(
                List.of(group), CdsExportDataSummaryHtmlComposer.Patient.of(Map.of()), Map.of());
    }

    /** A real request-free consultation letter: OpenPDF writes a new date, ID and font tag each time. */
    private static byte[] letter() throws Exception {
        var letter = new ConsultationLetterDto(
                Locale.ENGLISH,
                new ConsultationLetterDto.Letterhead("Synthetic Clinic", "", "", "", "Synthetic Clinic", "1 Test St",
                        "Testville", "ON", "A1A 1A1", "555-0100", "555-0101"),
                new ConsultationLetterDto.Request("0", "2024-02-03", "2", "Service", "Reply", true, false),
                null,
                new ConsultationLetterDto.Patient("FIXTURE, SYNTHETIC", "", "", "", "", "", "", "", "", "", "", "", "",
                        "", "", ""),
                new ConsultationLetterDto.Clinical("Reason", "", "", "", "", "", ""),
                new ConsultationLetterDto.Footer(false, "", "", false, "", ""),
                new ConsultationLetterDto.Images(false, null, false, null, null));
        var output = new ByteArrayOutputStream();
        new ConsultationPDFCreator(letter, output).printLetter();
        return output.toByteArray();
    }

    private static Printed printed(byte[] pdf, List<String> notes) {
        return new Printed(
                pdf,
                "Consultation request letter",
                new CdsExportDateDto(CdsExportDateDto.Precision.DATE, "2024-02-03"),
                "DOCTOR, REFERRING",
                Map.of("referalDate", "Reports/EventDateTime", "providerNo", "Reports/SourceAuthorPhysician"),
                notes);
    }

    @Test
    void shouldWriteSchemaValidPrintedReports_besideTheDataSummary() throws Exception {
        var group = request();
        var printed = printed(letter(), List.of(CdsExportConsultationLetterComposer.SIGNATURE_NOTE));
        var outcome = new PrintOutcome(printed, null);
        var patient = new LinkedHashMap<String, String>();
        CdsExportDemographicRowDto.COLUMNS.forEach(column -> patient.put(column, null));
        patient.putAll(Map.of("first_name", "Synthetic", "last_name", "Fixture", "year_of_birth", "1980",
                "month_of_birth", "01", "date_of_birth", "01", "patient_status", "AC", "sex", "U"));
        var demographics = new CdsExportDemographicMapper().map(new CdsExportDemographicRowDto(27, patient));
        var sections = new ArrayList<CdsExportXmlComposer.Section>();
        sections.add(new CdsExportXmlComposer.Section(
                new CdsExportIdentityDto(KEY, CdsCategory.DEMOGRAPHICS, "b".repeat(64)),
                writer -> new CdsExportDemographicXmlWriter().write(demographics, writer)));
        String notes = CdsExportDataSummaryOccurrenceService.notes(group, outcome);
        sections.add(new CdsExportXmlComposer.Section(
                CdsExportDataSummaryOccurrenceService.identity(KEY, group),
                writer -> CdsExportDataSummaryOccurrenceService.write(group, "Reports/summary.pdf", notes, writer)));
        sections.add(new CdsExportXmlComposer.Section(
                CdsExportDataSummaryOccurrenceService.printedIdentity(KEY, group),
                writer -> CdsExportDataSummaryOccurrenceService.writePrinted(group, printed, "Reports/printed.pdf",
                        writer)));
        var output = new ByteArrayOutputStream();
        var selected = Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.REPORTS);
        new CdsExportXmlComposer().compose(output, "d".repeat(64), KEY, "2026.08.0-test",
                CdsLogicalProfile.ONTARIO_MD_5_2, selected, sections.iterator(), ignored -> {}, 1L << 20, () -> {});
        var references = new ArrayList<CdsExportReportReferenceDto>();
        var checked = new CdsExportXmlValidator().validate(new ByteArrayInputStream(output.toByteArray()), "27",
                selected, "2026.08.0-test", CdsLogicalProfile.ONTARIO_MD_5_2, CdsExportXmlLimitsDto.defaults(),
                references::add, () -> {});
        assertThat(checked.occurrences().get(CdsCategory.REPORTS)).isEqualTo(2L);
        assertThat(references).extracting(CdsExportReportReferenceDto::relativePath)
                .containsExactly("Reports/summary.pdf", "Reports/printed.pdf");
        String xml = output.toString(StandardCharsets.UTF_8);
        String second = xml.substring(xml.lastIndexOf("<Reports>"), xml.lastIndexOf("</Reports>"));
        assertThat(second).containsSubsequence(
                "<FilePath>Reports/printed.pdf</FilePath>",
                "<Class>Other Letter</Class>",
                "<SubClass>Consultation request letter</SubClass>",
                "FullDate>2024-02-03</",
                "<AuthorFreeText>DOCTOR, REFERRING</AuthorFreeText>",
                "<Notes>CARLOS printed form:",
                "(SIGNATURE_UNAVAILABLE).</Notes>");
        assertThat(notes).startsWith(CdsExportDataSummaryOccurrenceService.NOTES)
                .contains("exported as a separate Reports entry (Consultation request letter).",
                        "(SIGNATURE_UNAVAILABLE).");
        assertThat(CdsExportDataSummaryOccurrenceService.printedIdentity(KEY, group))
                .isNotEqualTo(CdsExportDataSummaryOccurrenceService.identity(KEY, group));
    }

    @Test
    void shouldAssessTheMetadataThePrintedFormUses_asMappedToItsReportsElements() {
        var assessments = CdsExportDataSummaryOccurrenceService.printedAssessments(
                request(), printed(new byte[] {1}, List.of()));

        assertThat(assessments).hasSize(2);
        assertThat(assessments.values()).allSatisfy(assessment -> {
            assertThat(assessment.outcome()).isEqualTo(CdsExportFieldAssessmentDto.Outcome.MAPPED);
            assertThat(assessment.presence()).isEqualTo(CdsExportFieldAssessmentDto.Presence.VALUE);
            assertThat(assessment.rule()).isEqualTo(CdsExportDataSummaryOccurrenceService.PRINTED_METADATA_RULE);
        });
        assertThat(assessments.entrySet()).extracting(entry -> entry.getKey().column() + "=" + entry.getValue().destination())
                .containsExactly("providerNo=Reports/SourceAuthorPhysician", "referalDate=Reports/EventDateTime");
    }

    @Test
    void shouldLeaveTheRecordToItsSummary_whenTheIdentitiesMayNotReadIt() {
        var calls = new AtomicInteger();
        var asked = new ArrayList<String>();
        var outcome = service(printer(calls, null, null, null))
                .printed(request(), plan(request()), 27, control(false, asked));

        assertThat(outcome.printed()).isNull();
        assertThat(outcome.reason()).isEqualTo("NOT_AUTHORIZED");
        assertThat(asked).containsExactly("27_con");
        assertThat(calls).hasValue(0);
        assertThat(CdsExportDataSummaryOccurrenceService.notes(request(), outcome))
                .endsWith("could not be produced (NOT_AUTHORIZED); this summary carries every stored value.");
    }

    @Test
    void shouldRecordTheReasonCategory_whenThePrintedFormCannotBeMade() {
        var refused = service(printer(new AtomicInteger(), null, null, "TEXT_OVERFLOW"))
                .printed(request(), plan(request()), 27, control(true, new ArrayList<>()));
        var failed = service(printer(new AtomicInteger(), null, new IllegalStateException("x"), null))
                .printed(request(), plan(request()), 27, control(true, new ArrayList<>()));
        var notPdf = service(printer(new AtomicInteger(), printed("no".getBytes(StandardCharsets.US_ASCII), List.of()),
                        null, null))
                .printed(request(), plan(request()), 27, control(true, new ArrayList<>()));

        assertThat(refused.reason()).isEqualTo("TEXT_OVERFLOW");
        assertThat(failed.reason()).isEqualTo("RENDER_FAILED");
        assertThat(notPdf.reason()).isEqualTo("RENDER_FAILED");
        assertThat(CdsExportDataSummaryOccurrenceService.notes(request(), refused)).contains("(TEXT_OVERFLOW)");
    }

    @Test
    void shouldGiveTheSameBytes_forTheSameRecordPrintedTwice() throws Exception {
        byte[] first = letter();
        byte[] second = letter();
        var asked = new ArrayList<String>();
        var one = service(printer(new AtomicInteger(), printed(first, List.of()), null, null))
                .printed(request(), plan(request()), 27, control(true, asked));
        var two = service(printer(new AtomicInteger(), printed(second, List.of()), null, null))
                .printed(request(), plan(request()), 27, control(true, asked));

        assertThat(one.printed().pdf()).isEqualTo(two.printed().pdf());
        assertThat(CdsExportPrintedPdf.tags(one.printed().pdf())).isNotEmpty().allMatch(tag -> tag.startsWith("AAAA"));
        assertThat(CdsExportPrintedPdf.tag(27)).isEqualTo("AAAABB");
    }

    @Test
    void shouldLabelEachSummaryRevision_withItsNumberAndSaveTime() {
        var revision = new Group(Kind.ENCOUNTER_FORM, "formRourke2020", "12",
                List.of(row("formRourke2020", 12, "ID", "12", "formEdited", "2024-05-06 07:08:09")), 2, 3, null, false);

        assertThat(CdsExportDataSummaryOccurrenceService.notes(revision, null))
                .isEqualTo(CdsExportDataSummaryOccurrenceService.NOTES + " Revision 2 of 3, saved 2024-05-06 07:08:09.");
        assertThat(revision.subClass()).isEqualTo("formRourke2020 revision 2 of 3");
    }
}
