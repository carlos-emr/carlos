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
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("unit")
@Tag("cds-export")
class CdsExportReportOccurrenceServiceUnitTest {
    private Map<String, String> document;
    private Map<String, String> link;
    private CdsReportNativeDto prepared;
    private CdsReportNativeDto.Variant variant;

    @BeforeEach
    void setup() {
        document =
                new HashMap<>(
                        Map.of(
                                "document_no",
                                "7",
                                "doctype",
                                "CDS",
                                "status",
                                "A",
                                "docfilename",
                                "original.txt",
                                "docClass",
                                "Consultant Report",
                                "contenttype",
                                "text/plain;charset=UTF-8",
                                "cds_content_precision",
                                "UNKNOWN",
                                "docdesc",
                                "Imported report",
                                "abnormal",
                                "0",
                                "public1",
                                "0"));
        document.put("restrictToProgram", "0");
        link =
                new HashMap<>(
                        Map.of(
                                "document_no",
                                "7",
                                "module",
                                "demographic",
                                "module_id",
                                "27",
                                "status",
                                "A"));
        variant =
                new CdsReportNativeDto.Variant(
                        "embedded",
                        "txt",
                        "text/plain;charset=UTF-8",
                        "a".repeat(64),
                        12,
                        false,
                        List.of());
        prepared =
                new CdsReportNativeDto(
                        Map.of("Class", "Consultant Report", "Format", "Text"),
                        List.of(variant),
                        CdsTemporalDto.unknown(),
                        CdsTemporalDto.unknown(),
                        CdsTemporalDto.unknown());
    }

    private void bind() throws IOException {
        CdsExportReportOccurrenceService.bind(
                new CdsExportCapturedTextRowDto(
                        "document", CdsExportPatientTableSourceReader.key(7), document),
                new CdsExportCapturedTextRowDto(
                        "ctl_document", CdsExportPatientTableSourceReader.key(7), link),
                prepared,
                variant,
                "original.txt",
                27);
    }

    @Test
    void shouldBindOriginalPreparedMetadata_withoutInventingSourceTiming() {
        assertThatCode(this::bind).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "docClass",
                "doctype",
                "status",
                "docfilename",
                "contenttype",
                "cds_content_precision",
                "docdesc",
                "docSubClass",
                "sourceFacility",
                "source"
            })
    void shouldRefuseAnyChangedMetadata_beforeReturningOriginalSource(String column) {
        document.put(column, "Changed");
        assertThatThrownBy(this::bind).isInstanceOf(IOException.class);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "docxml",
                "report_media",
                "responsible",
                "reviewer",
                "reviewdatetime",
                "appointment_no",
                "public1",
                "abnormal",
                "restrictToProgram"
            })
    void shouldRequireReview_forUnrepresentedClinicalOrAccessContext(String column) {
        document.put(column, "1");
        assertThatThrownBy(this::bind).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @ParameterizedTest
    @ValueSource(strings = {"document_no", "module", "module_id", "status"})
    void shouldRefuseChangedPatientAssociation_forReportOccurrenceServiceContract(String column) {
        link.put(column, "Changed");
        assertThatThrownBy(this::bind).isInstanceOf(IOException.class);
    }

    @Test
    void shouldKeepEntryProviderLocal_withoutTurningItIntoSourceAuthor() {
        document.put("doccreator", "999998");
        assertThatCode(this::bind).doesNotThrowAnyException();
    }

    @Test
    void shouldKeepUnprojectedLongSourceAuthors_inTheirOriginalFields() {
        var metadata = new HashMap<>(prepared.values());
        metadata.put("NativeSourceAuthor", "X".repeat(120));
        prepared =
                new CdsReportNativeDto(
                        metadata,
                        List.of(variant),
                        prepared.event(),
                        prepared.received(),
                        prepared.sent());
        assertThatCode(this::bind).doesNotThrowAnyException();
    }

    @Test
    void shouldRefuseAClinicalMediaReviewFlag_evenWhenTheProjectionIsUnchanged() {
        variant =
                new CdsReportNativeDto.Variant(
                        variant.name(),
                        variant.extension(),
                        variant.mime(),
                        variant.digest(),
                        variant.length(),
                        true,
                        List.of());
        document.put("docdesc", "Imported report (source media review required)");
        assertThatThrownBy(this::bind).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldRequireAnExplicitAttachmentSink_insteadOfSilentlyOmittingAFile() {
        CdsExportClinicalOccurrenceService.Receipts sink = receipt -> {};
        assertThatThrownBy(() -> sink.attachment(null, null))
                .hasMessage("CDS_EXPORT_REPORT_ATTACHMENT_SINK_REQUIRED");
    }
}
