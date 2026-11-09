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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("unit")
@Tag("cds-export")
class CdsExportCapturedDocumentFilesUnitTest {
    private static final String HASH = "a".repeat(64), TABLE = "native_document_files";
    private final CdsExportNativeCaptureService captures =
            mock(CdsExportNativeCaptureService.class);
    private final CdsExportLeaseDto lease = mock(CdsExportLeaseDto.class);
    private final CdsExportCohortService.Patient patient =
            mock(CdsExportCohortService.Patient.class);
    private final CdsExportRunControl control = mock(CdsExportRunControl.class);
    private CdsExportPatientCaptureDto source;
    private String document = "7",
            filename = "native-file.pdf",
            key = CdsExportPatientTableSourceReader.key(7);
    private byte[] body = {0, 1, 2, (byte) 255};
    private boolean corrupt;

    private void prepare() throws Exception {
        var columns = Set.of("document_no", "filename", "content");
        var actual = mock(CdsExportNativeCaptureDto.class);
        when(actual.tables())
                .thenReturn(Map.of(TABLE, new CdsExportCapturedTableDto(columns, 1, HASH)));
        when(actual.values())
                .thenReturn(new CdsExportStagedFileDto("2".repeat(64), "SOURCE_VALUES", HASH, 1));
        when(actual.index())
                .thenReturn(new CdsExportStagedFileDto("3".repeat(64), "SOURCE_INDEX", HASH, 1));
        source =
                new CdsExportPatientCaptureDto(
                        actual,
                        new CdsExportStagedFileDto("1".repeat(64), "SOURCE_MANIFEST", HASH, 1));
        var bytes = new ByteArrayOutputStream();
        var index = new ByteArrayOutputStream();
        new CdsExportNativeCaptureComposer()
                .compose(
                        bytes,
                        index,
                        Set.of(TABLE),
                        10,
                        4096,
                        () -> {},
                        out -> {
                            out.beginTable(TABLE, columns, 1);
                            out.row(
                                    key,
                                    row -> {
                                        row.text("document_no", document);
                                        row.text("filename", filename);
                                        row.binary("content", new ByteArrayInputStream(body));
                                    });
                            out.endTable();
                        });
        byte[] data = bytes.toByteArray();
        if (corrupt) data[data.length - 1] ^= 1;
        doAnswer(
                        call -> {
                            new CdsExportNativeCaptureReader()
                                    .scan(
                                            new ByteArrayInputStream(data),
                                            new ByteArrayInputStream(index.toByteArray()),
                                            Map.of(
                                                    TABLE,
                                                    new CdsExportNativeCaptureReader.Table(
                                                            columns, 1)),
                                            10,
                                            4096,
                                            () -> {},
                                            call.getArgument(7));
                            return actual;
                        })
                .when(captures)
                .scan(any(), any(), anySet(), any(), anyLong(), anyLong(), any(), any());
    }

    private Map<Integer, CdsExportCapturedDocumentFiles.File> read() throws Exception {
        return new CdsExportCapturedDocumentFiles()
                .read(
                        captures,
                        lease,
                        patient,
                        Set.of(TABLE),
                        source,
                        new CdsExportClinicalOccurrenceService.Bounds(10, 4096, 10, 4096, 10, 10),
                        control);
    }

    @Test
    void shouldReturnOnlyDescriptorsFromTheWholeVerifiedCapture() throws Exception {
        prepare();
        var files = read();
        assertThat(files).containsOnlyKeys(7);
        var file = files.get(7);
        assertThat(file.filename()).isEqualTo(filename);
        assertThat(file.content().bytes()).isEqualTo(body.length);
        assertThat(file.content().kind()).isEqualTo(CdsExportCapturedFieldDto.Kind.BINARY);
        assertThat(file.toString()).doesNotContain(filename);
    }

    @Test
    void shouldKeepEmptyFiles_asPresentBinaryEvidence() throws Exception {
        body = new byte[0];
        prepare();
        assertThat(read().get(7).content().bytes()).isZero();
    }

    @Test
    void shouldRefuseCorruptBytesBeforeExposingAnyDescriptor() throws Exception {
        corrupt = true;
        prepare();
        assertThatThrownBy(this::read).isInstanceOf(IOException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"07", "0", "-1", "x"})
    void shouldRefuseNoncanonicalDocumentIdentities(String value) throws Exception {
        document = value;
        prepare();
        assertThatThrownBy(this::read).isInstanceOf(IOException.class);
    }

    @Test
    void shouldRefuseARecordKeyForAnotherDocument() throws Exception {
        key = CdsExportPatientTableSourceReader.key(8);
        prepare();
        assertThatThrownBy(this::read).isInstanceOf(IOException.class);
    }

    @Test
    void shouldRequireFreshAuthorization_afterTheLastDescriptorCallback() throws Exception {
        prepare();
        doThrow(new SecurityException("REVOKED"))
                .when(captures)
                .authorizeSources(lease, patient, Set.of(TABLE), control);
        assertThatThrownBy(this::read).hasMessage("REVOKED");
    }
}
