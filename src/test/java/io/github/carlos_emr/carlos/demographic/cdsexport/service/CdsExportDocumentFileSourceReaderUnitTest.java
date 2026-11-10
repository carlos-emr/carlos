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
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dto.CdsExportRawRowDto;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("unit")
@Tag("cds-export")
class CdsExportDocumentFileSourceReaderUnitTest {
    private final DocumentDao documents = mock(DocumentDao.class);

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "-1", "0", "1099511627777", " 1"})
    void shouldKeepMetadataAndAnEmptyDocumentDomainAvailable_whenCaptureBoundsAreInvalid(
            String bound) throws Exception {
        var reader = new CdsExportDocumentFileSourceReader(documents, "", bound);
        when(documents.findCdsExportRowsAfter(27, null, 32)).thenReturn(List.of());
        assertThat(reader.count(27)).isZero();
        var values = new ByteArrayOutputStream();
        var index = new ByteArrayOutputStream();
        var result =
                new CdsExportNativeCaptureComposer()
                        .compose(
                                values,
                                index,
                                Set.of(reader.table()),
                                10,
                                1024,
                                () -> {},
                                out -> {
                                    out.beginTable(reader.table(), reader.columns(), 0);
                                    reader.capture(27, out);
                                    out.endTable();
                                });
        assertThat(result.fields()).isZero();
        assertThat(result.valueBytes()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "-1", "0", "1099511627777", " 1"})
    void shouldRefuseInvalidBounds_beforeOpeningAnyPresentNativeFile(String bound) {
        var row = mock(CdsExportRawRowDto.class);
        when(row.id()).thenReturn(7L);
        when(documents.findCdsExportRowsAfter(27, null, 32)).thenReturn(List.of(row));
        var reader = new CdsExportDocumentFileSourceReader(documents, "", bound);
        assertThatThrownBy(
                        () -> reader.capture(27, mock(CdsExportNativeCaptureComposer.Writer.class)))
                .hasMessage("CDS_EXPORT_DOCUMENT_BOUND_INVALID");
        verify(row, never()).cells();
    }

    @Test
    void shouldDeclareTheCompleteDocumentAndFileDependencyRecipe_forDocumentFileSourceReaderContract() throws Exception {
        var reader = new CdsExportDocumentFileSourceReader(documents, "", "1024");
        assertThat(reader.requiredTables())
                .containsExactlyInAnyOrder("document", "ctl_document", "native_document_files");
        assertThatCode(
                        () ->
                                new CdsExportSourceSchemaValidator()
                                        .verify(reader.schema(), reader.columns()))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldPropagateCancellation_beforeAnySourceQuery() {
        var reader = new CdsExportDocumentFileSourceReader(documents, "", "1024");
        assertThatThrownBy(
                        () ->
                                reader.capture(
                                        27,
                                        mock(CdsExportNativeCaptureComposer.Writer.class),
                                        () -> {
                                            throw new java.util.concurrent.CancellationException(
                                                    "CANCELLED");
                                        }))
                .hasMessage("CANCELLED");
        verifyNoInteractions(documents);
    }
}
