/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryPlanner.Group;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryPlanner.Row;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryPlanner.Storage;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryPlanner.Value;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryRegistry.Kind;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderDegradation;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderRequest;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderResult;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderSource;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.service.HtmlPdfRenderService;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.service.HtmlPdfTestServices;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A record the PDF cannot show completely is exported, never held: the exact record travels
 * inside the PDF as attached files and the page and the notice say so.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportDataSummaryPdfServiceUnitTest {
    private static final byte[] PNG =
            Base64.getDecoder()
                    .decode(
                            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    private static final CdsExportDataSummaryHtmlComposer.Patient PATIENT =
            CdsExportDataSummaryHtmlComposer.Patient.of(
                    Map.of("demographic_no", "27", "last_name", "FAKE-Fixture", "first_name", "Synthetic"));

    /** Tamil (no bundled glyph), a vertical tab, a lone surrogate, a zero-width joiner. */
    private static final String UNDRAWABLE =
            "Seen with interpreter தமிழ்; pasted\u000bbreak; lone \ud83d; joined a‍b";

    private static Value binary(byte[] bytes) throws Exception {
        return new Value(
                Storage.BINARY,
                null,
                bytes.length,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                bytes);
    }

    private static Group tickler(String message) {
        var values = new LinkedHashMap<String, Value>();
        values.put("message", Value.text(message));
        values.put("priority", Value.sqlNull());
        values.put("tickler_no", Value.text("5"));
        return new Group(
                Kind.TICKLER,
                "tickler",
                "5",
                List.of(new Row("tickler", "00000000000000000005", values)),
                0,
                0,
                null,
                false);
    }

    private static Group photo() throws Exception {
        var values = new LinkedHashMap<String, Value>();
        values.put("contents", binary(PNG));
        values.put("image_id", Value.text("5"));
        values.put("image_type", Value.text("png"));
        return new Group(
                Kind.PATIENT_PHOTO,
                "client_image",
                "5",
                List.of(new Row("client_image", "00000000000000000005", values)),
                0,
                0,
                null,
                false);
    }

    private static byte[] anyPdf() throws Exception {
        return HtmlPdfTestServices.newService()
                .render(
                        new HtmlPdfRenderRequest(
                                "<html><body><p>printed page</p></body></html>",
                                null,
                                List.of(
                                        java.nio.file.Path.of(System.getProperty("java.io.tmpdir"))
                                                .toAbsolutePath()
                                                .normalize()),
                                HtmlPdfRenderSource.CDS_DATA_SUMMARY))
                .pdf();
    }

    private static String text(byte[] pdf) throws Exception {
        try (var document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isPositive();
            return new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
        }
    }

    @Test
    void shouldPrintAnOrdinaryRecord_withoutANoticeOrAnAttachment() throws Exception {
        var service = new CdsExportDataSummaryPdfService(HtmlPdfTestServices.newService());

        var rendered = service.document(tickler("Recall in 6 months — café, 語"), PATIENT);

        assertThat(rendered.notice()).isNull();
        assertThat(CdsExportDataSummaryPdfService.attachments(rendered.pdf())).isEmpty();
    }

    @Test
    void shouldAttachTheExactRecord_whenAValueHoldsCharactersThePageCannotShow() throws Exception {
        var service = new CdsExportDataSummaryPdfService(HtmlPdfTestServices.newService());
        var group = tickler(UNDRAWABLE);
        assertThat(service.exact(group)).isFalse();

        var rendered = service.document(group, PATIENT);

        // Exported, with the page saying where the exact record is.
        assertThat(rendered.notice()).isEqualTo(CdsExportDataSummaryPdfService.ATTACHED_NOTICE);
        assertThat(text(rendered.pdf()))
                .contains("The exact record is attached inside this PDF")
                .contains("Seen with interpreter");
        var attached = CdsExportDataSummaryPdfService.attachments(rendered.pdf());
        assertThat(attached).containsOnlyKeys(CdsExportDataSummaryPdfService.RECORD_FILE);
        byte[] json = attached.get(CdsExportDataSummaryPdfService.RECORD_FILE);
        // Valid UTF-8 that decodes to the stored text exactly, lone surrogate included.
        assertThat(new String(json, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8))
                .isEqualTo(json);
        var record = new ObjectMapper().readTree(json);
        assertThat(record.path("carrier").asText()).isEqualTo("CARLOS.DataSummary.Record.v1");
        assertThat(record.path("table").asText()).isEqualTo("tickler");
        var values = record.path("rows").path(0).path("values");
        assertThat(values.path("message").path("text").asText()).isEqualTo(UNDRAWABLE);
        assertThat(values.path("priority").path("state").asText()).isEqualTo("null");
        assertThat(values.path("tickler_no").path("text").asText()).isEqualTo("5");
    }

    @Test
    void shouldAttachTheImageItself_whenTheRendererNeverDrawsIt() throws Exception {
        byte[] page = anyPdf();
        var renderer = mock(HtmlPdfRenderService.class);
        when(renderer.drawsEveryCharacter(any())).thenReturn(true);
        when(renderer.render(any()))
                .thenReturn(
                        new HtmlPdfRenderResult(
                                page,
                                EnumSet.of(HtmlPdfRenderDegradation.IMAGES_OMITTED_FOR_MEMORY)));
        var service = new CdsExportDataSummaryPdfService(renderer);

        var rendered = service.document(photo(), PATIENT);

        assertThat(rendered.notice()).isEqualTo(CdsExportDataSummaryPdfService.ATTACHED_NOTICE);
        var attached = CdsExportDataSummaryPdfService.attachments(rendered.pdf());
        assertThat(attached)
                .containsOnlyKeys(
                        CdsExportDataSummaryPdfService.RECORD_FILE,
                        "carlos-data-summary-image-1.png");
        assertThat(attached.get("carlos-data-summary-image-1.png")).isEqualTo(PNG);
        var contents =
                new ObjectMapper()
                        .readTree(attached.get(CdsExportDataSummaryPdfService.RECORD_FILE))
                        .path("rows")
                        .path(0)
                        .path("values")
                        .path("contents");
        assertThat(contents.path("file").asText()).isEqualTo("carlos-data-summary-image-1.png");
        assertThat(contents.path("bytes").asLong()).isEqualTo(PNG.length);
        assertThat(contents.path("sha256").asText())
                .isEqualTo(
                        HexFormat.of()
                                .formatHex(MessageDigest.getInstance("SHA-256").digest(PNG)));
        // Rendered while the module reported no free slot, then once more with the notice.
        verify(renderer, times(CdsExportDataSummaryPdfService.SLOT_ATTEMPTS + 1)).render(any());
    }

    @Test
    void shouldPrintTheImage_whenASlotComesFreeOnALaterRender() throws Exception {
        byte[] page = anyPdf();
        var renderer = mock(HtmlPdfRenderService.class);
        when(renderer.drawsEveryCharacter(any())).thenReturn(true);
        when(renderer.render(any()))
                .thenReturn(
                        new HtmlPdfRenderResult(
                                page,
                                EnumSet.of(HtmlPdfRenderDegradation.IMAGES_OMITTED_FOR_MEMORY)))
                .thenReturn(new HtmlPdfRenderResult(page, EnumSet.noneOf(HtmlPdfRenderDegradation.class)));
        var service = new CdsExportDataSummaryPdfService(renderer);

        var rendered = service.document(photo(), PATIENT);

        assertThat(rendered.notice()).isNull();
        assertThat(CdsExportDataSummaryPdfService.attachments(rendered.pdf())).isEmpty();
        verify(renderer, times(2)).render(any());
    }

    @Test
    void shouldExportTheRecordInANoticePdf_whenNoPdfCanBeRendered() throws Exception {
        var renderer = mock(HtmlPdfRenderService.class);
        when(renderer.drawsEveryCharacter(any())).thenReturn(true);
        when(renderer.render(any())).thenThrow(new PDFGenerationException("synthetic"));
        var service = new CdsExportDataSummaryPdfService(renderer);

        var rendered = service.document(tickler("ordinary text"), PATIENT);

        assertThat(rendered.notice()).isEqualTo(CdsExportDataSummaryPdfService.UNPRINTED_NOTICE);
        CdsExportDataSummaryPdfService.requirePdf(rendered.pdf());
        assertThat(text(rendered.pdf())).contains("could not print this record as a PDF");
        var attached = CdsExportDataSummaryPdfService.attachments(rendered.pdf());
        assertThat(
                        new ObjectMapper()
                                .readTree(attached.get(CdsExportDataSummaryPdfService.RECORD_FILE))
                                .path("rows")
                                .path(0)
                                .path("values")
                                .path("message")
                                .path("text")
                                .asText())
                .isEqualTo("ordinary text");
    }

    @Test
    void shouldWithholdACredentialInTheAttachedRecord_asThePageDoes() throws Exception {
        String table = "emailLog";
        String column = "password";
        assertThat(CdsExportDataSummaryRegistry.withheld(table, column)).isTrue();
        var values = new LinkedHashMap<String, Value>();
        values.put(column, Value.text("secret ம"));
        values.put("id", Value.text("5"));
        var group =
                new Group(
                        CdsExportDataSummaryRegistry.rule(table).kind(),
                        table,
                        "5",
                        List.of(new Row(table, "00000000000000000005", values)),
                        0,
                        0,
                        null,
                        false);
        var renderer = mock(HtmlPdfRenderService.class);
        when(renderer.drawsEveryCharacter(any())).thenReturn(true);
        when(renderer.render(any())).thenThrow(new PDFGenerationException("synthetic"));

        var rendered = new CdsExportDataSummaryPdfService(renderer).document(group, PATIENT);

        byte[] json =
                CdsExportDataSummaryPdfService.attachments(rendered.pdf())
                        .get(CdsExportDataSummaryPdfService.RECORD_FILE);
        assertThat(new String(json, StandardCharsets.UTF_8)).doesNotContain("secret");
        assertThat(
                        new ObjectMapper()
                                .readTree(json)
                                .path("rows")
                                .path(0)
                                .path("values")
                                .path(column)
                                .path("state")
                                .asText())
                .isEqualTo("withheld");
    }

    @Test
    void shouldStopAsACancellation_whenTheWaitForTheRendererIsInterrupted() throws Exception {
        var renderer = mock(HtmlPdfRenderService.class);
        when(renderer.drawsEveryCharacter(any())).thenReturn(true);
        when(renderer.render(any()))
                .thenAnswer(
                        call -> {
                            Thread.currentThread().interrupt();
                            throw new PDFGenerationException("interrupted", true);
                        });
        var service = new CdsExportDataSummaryPdfService(renderer);
        try {
            assertThatThrownBy(() -> service.document(tickler("ordinary text"), PATIENT))
                    .isInstanceOf(CancellationException.class);
        } finally {
            // Clear the flag this test set on the JUnit thread.
            assertThat(Thread.interrupted()).isTrue();
        }
    }
}
