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
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.CdsExportEformRenderReadDao;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportEformRenderService.Reason;
import io.github.carlos_emr.carlos.eform.util.EFormBrowserPdfService.RenderedEformPdf;
import io.github.carlos_emr.carlos.eform.util.EFormRenderCompletenessReport;
import io.github.carlos_emr.carlos.utility.EformContentUnavailableException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Each current eForm is rendered before the snapshot, as the freshly authorized provider, and
 * captured only when the snapshot's row is the row that was rendered.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportEformRenderingSourceReaderUnitTest {
    private static final byte[] PDF = "%PDF-1.7\nsynthetic\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
    private static final String A = "a".repeat(64), B = "b".repeat(64), C = "c".repeat(64);

    @TempDir Path directory;

    private final AtomicInteger authorizations = new AtomicInteger();

    private final CdsExportRunControl control =
            new CdsExportRunControl() {
                @Override
                public void checkpoint() {}

                @Override
                public void captureCheckpoint() {}

                @Override
                public void authorizePatient(int patient) {}

                @Override
                public void authorizeCategory(
                        int patient, CdsCategory category, boolean sensitiveLaboratory) {}

                @Override
                public LoggedInInfo authorizeRendering(int patient) {
                    authorizations.incrementAndGet();
                    return new LoggedInInfo();
                }
            };

    private CdsExportEformRenderService renders() {
        var settings = new Properties();
        settings.setProperty("cds.export.eformRenderIntervalMillis", "0");
        return new CdsExportEformRenderService(
                (who, fdid) -> {
                    if (fdid == 9)
                        throw new EformContentUnavailableException(
                                "omitted", fdid, EFormRenderCompletenessReport.complete());
                    try {
                        Path file = Files.createTempFile(directory, "eform-browser-render-", ".pdf");
                        Files.write(file, PDF);
                        return new RenderedEformPdf(file);
                    } catch (IOException failed) {
                        throw new java.io.UncheckedIOException(failed);
                    }
                },
                settings,
                System::nanoTime);
    }

    /** Captures the prepared reader through the real composer and reads it back. */
    private static Map<String, CdsExportEformRenderings.Rendering> capture(
            CdsExportNativeSourceReader reader, int patient, long rows) throws IOException {
        var values = new ByteArrayOutputStream();
        var index = new ByteArrayOutputStream();
        new CdsExportNativeCaptureComposer()
                .compose(
                        values,
                        index,
                        Set.of(reader.table()),
                        1_000_000,
                        1L << 30,
                        () -> {},
                        writer -> {
                            writer.beginTable(reader.table(), reader.columns(), rows);
                            reader.capture(patient, writer, () -> {});
                            writer.endTable();
                        });
        var result = new TreeMap<String, CdsExportEformRenderings.Rendering>();
        var sink = new CdsExportEformRenderings.Fields(1000, result);
        new CdsExportNativeCaptureReader()
                .scan(
                        new ByteArrayInputStream(values.toByteArray()),
                        new ByteArrayInputStream(index.toByteArray()),
                        Map.of(
                                reader.table(),
                                new CdsExportNativeCaptureReader.Table(reader.columns(), rows)),
                        1_000_000,
                        1L << 30,
                        () -> {},
                        sink);
        sink.finish();
        return result;
    }

    @Test
    void shouldCaptureEachRenderOrItsReason_onlyForTheRowThatWasRendered() throws Exception {
        var source = mock(CdsExportEformRenderReadDao.class);
        when(source.current(27))
                .thenReturn(
                        List.of(
                                new CdsExportEformRenderReadDao.Current(5, A, "Ann", "Fixture"),
                                new CdsExportEformRenderReadDao.Current(9, B, null, null),
                                new CdsExportEformRenderReadDao.Current(11, C, null, null)))
                // Inside the snapshot: eForm 11 was edited after its render, and 14 was added.
                .thenReturn(
                        List.of(
                                new CdsExportEformRenderReadDao.Current(5, A, "Ann", "Fixture"),
                                new CdsExportEformRenderReadDao.Current(9, B, null, null),
                                new CdsExportEformRenderReadDao.Current(11, A, null, null),
                                new CdsExportEformRenderReadDao.Current(14, B, null, null)));
        var spool = new CdsExportEformRenderServiceUnitTest.Spool();
        var reader = new CdsExportEformRenderingSourceReader(source, renders());

        var prepared = reader.forCapture(27, control, spool);
        var captured = capture(prepared, 27, 4);

        assertThat(authorizations).hasValue(3);
        assertThat(captured.keySet()).containsExactly("11", "14", "5", "9");
        var rendered = captured.get("5");
        assertThat(rendered.rendered()).isTrue();
        assertThat(rendered.content().bytes()).isEqualTo(PDF.length);
        assertThat(rendered.authorFirstName()).isEqualTo("Ann");
        assertThat(rendered.row().fields()).containsEntry("outcome", "RENDERED").containsEntry("reason", "");
        assertThat(captured.get("9").reason()).isEqualTo(Reason.INCOMPLETE);
        assertThat(captured.get("11").reason()).isEqualTo(Reason.CHANGED);
        assertThat(captured.get("14").reason()).isEqualTo(Reason.CHANGED);
        assertThat(captured.get("9").row().fields()).containsEntry("content", "");
    }

    @Test
    void shouldNoteEveryEform_whenTheProcessHasNoRenderer() throws Exception {
        var source = mock(CdsExportEformRenderReadDao.class);
        when(source.current(27))
                .thenReturn(List.of(new CdsExportEformRenderReadDao.Current(5, A, null, null)));
        var reader =
                new CdsExportEformRenderingSourceReader(
                        source,
                        new CdsExportEformRenderService(null, new Properties(), System::nanoTime));

        var captured =
                capture(
                        reader.forCapture(27, control, new CdsExportEformRenderServiceUnitTest.Spool()),
                        27,
                        1);

        // A command-line process neither authorizes nor attempts a render.
        assertThat(authorizations).hasValue(0);
        assertThat(captured.get("5").reason()).isEqualTo(Reason.NOT_IN_THIS_PROCESS);
        assertThat(capture(reader, 27, 1).get("5").reason()).isEqualTo(Reason.NOT_PREPARED);
    }
}
