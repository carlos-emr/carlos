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

import io.github.carlos_emr.carlos.commn.dao.CdsExportHospitalReportReadDao;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.commn.dto.CdsExportRawCellDto;
import io.github.carlos_emr.carlos.commn.dto.CdsExportRawRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The stored HRM message file of each report, resolved as the report viewer resolves
 * {@code HRMDocument.reportFile} and read through the trusted document stream. A file that is
 * not there, a name the stream refuses and no recorded file are captured as states, never a
 * hold; a file over the document bound still holds the patient.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportHospitalReportFileSourceReaderUnitTest {
    private final CdsExportHospitalReportReadDao reports = mock(CdsExportHospitalReportReadDao.class);
    private Path root;

    @BeforeEach
    void setup() throws Exception {
        root =
                Files.createTempDirectory(
                        Path.of(
                                        System.getProperty(
                                                "cds.export.test.documentRoot",
                                                System.getProperty("user.home")))
                                .toAbsolutePath(),
                        "owned-hrm-document-",
                        PosixFilePermissions.asFileAttribute(
                                PosixFilePermissions.fromString("rwx------")));
    }

    @AfterEach
    void cleanup() throws Exception {
        try (var walk = Files.walk(root)) {
            for (var path : walk.sorted(java.util.Comparator.reverseOrder()).toList())
                Files.deleteIfExists(path);
        }
    }

    private static CdsExportRawRowDto row(long id, String reportFile) throws Exception {
        if (reportFile == null)
            return new CdsExportRawRowDto(
                    id,
                    Map.of(
                            "reportFile",
                            new CdsExportRawCellDto(true, 0, CdsExportRawCellDto.EMPTY_SHA256, null)));
        byte[] bytes = reportFile.getBytes(StandardCharsets.UTF_8);
        return new CdsExportRawRowDto(
                id,
                Map.of(
                        "reportFile",
                        new CdsExportRawCellDto(
                                false,
                                bytes.length,
                                HexFormat.of()
                                        .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                                bytes)));
    }

    private Path file(String relative, byte[] content) throws Exception {
        Path path = root.resolve(relative);
        Files.createDirectories(
                path.getParent(),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        Files.write(path, content);
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        return path;
    }

    /** Captures the rows and reads them back: column values by report, binary as its bytes. */
    private Map<String, Map<String, Object>> capture(
            CdsExportHospitalReportFileSourceReader reader, List<CdsExportRawRowDto> rows)
            throws Exception {
        when(reports.page(eq(Plan.HRM_REPORT_FILES), eq(27), isNull(), eq(32))).thenReturn(rows);
        var values = new ByteArrayOutputStream();
        var index = new ByteArrayOutputStream();
        new CdsExportNativeCaptureComposer()
                .compose(
                        values,
                        index,
                        Set.of(reader.table()),
                        100,
                        1 << 20,
                        () -> {},
                        out -> {
                            out.beginTable(reader.table(), reader.columns(), rows.size());
                            reader.capture(27, out);
                            out.endTable();
                        });
        var result = new TreeMap<String, Map<String, Object>>();
        new CdsExportNativeCaptureReader()
                .scan(
                        new ByteArrayInputStream(values.toByteArray()),
                        new ByteArrayInputStream(index.toByteArray()),
                        Map.of(
                                reader.table(),
                                new CdsExportNativeCaptureReader.Table(
                                        reader.columns(), rows.size())),
                        100,
                        1 << 20,
                        () -> {},
                        new CdsExportNativeCaptureReader.Fields() {
                            private ByteArrayOutputStream pending;

                            @Override
                            public OutputStream output(CdsExportCapturedFieldDto field) {
                                pending = new ByteArrayOutputStream();
                                return pending;
                            }

                            @Override
                            public void verified(CdsExportCapturedFieldDto field) {
                                var fields =
                                        result.computeIfAbsent(
                                                field.recordKey(), ignored -> new TreeMap<>());
                                fields.put(
                                        field.column(),
                                        switch (field.kind()) {
                                            case SQL_NULL -> "NULL";
                                            case BINARY -> pending.toByteArray();
                                            case UTF8 ->
                                                    pending.toString(StandardCharsets.UTF_8);
                                        });
                            }
                        });
        return result;
    }

    @Test
    void shouldReadEachStoredMessage_asTheReportViewerResolvesItsReportFile() throws Exception {
        byte[] message = "<OmdCds/>".getBytes(StandardCharsets.UTF_8);
        file("LabUpload.20220225.xml.1690313901104", message);
        Path nested = file("hrm/2026/LabUpload with spaces.xml.1691537353747", message);
        var reader = new CdsExportHospitalReportFileSourceReader(reports, root.toString(), "1024");
        var captured =
                capture(
                        reader,
                        List.of(
                                row(1, "LabUpload.20220225.xml.1690313901104"),
                                row(2, nested.toString()),
                                row(3, "LabUpload.purged.xml"),
                                row(4, null),
                                row(5, ""),
                                row(6, "hrm\\windows.xml"),
                                row(7, "/opt/oscar/hrm/elsewhere.xml")));
        var states = new ArrayList<String>();
        captured.values().forEach(fields -> states.add((String) fields.get("state")));
        assertThat(states)
                .containsExactly(
                        "READ",
                        "READ",
                        "MISSING",
                        "NOT_RECORDED",
                        "NOT_RECORDED",
                        "UNSUPPORTED_NAME",
                        "MISSING");
        var first = captured.get(CdsExportPatientTableSourceReader.key(1));
        assertThat((byte[]) first.get("content")).isEqualTo(message);
        assertThat(first.get("report_file")).isEqualTo("LabUpload.20220225.xml.1690313901104");
        assertThat((byte[]) captured.get(CdsExportPatientTableSourceReader.key(2)).get("content"))
                .isEqualTo(message);
        assertThat(captured.get(CdsExportPatientTableSourceReader.key(3)).get("content"))
                .isEqualTo("NULL");
        assertThat(captured.get(CdsExportPatientTableSourceReader.key(4)).get("report_file"))
                .isEqualTo("NULL");
    }

    @Test
    void shouldHoldOnlyThePatient_whenAStoredMessageExceedsTheDocumentBound() throws Exception {
        file("LabUpload.large.xml", new byte[2048]);
        var reader = new CdsExportHospitalReportFileSourceReader(reports, root.toString(), "1024");
        assertThatThrownBy(() -> capture(reader, List.of(row(1, "LabUpload.large.xml"))))
                .isInstanceOf(CdsExportMappingReviewException.class)
                .hasMessage("CDS_EXPORT_NATIVE_REPORT_FILE_TOO_LARGE");
    }

    @Test
    void shouldRefuseAnUnusableDocumentDirectory_beforeOpeningAnyFile() throws Exception {
        var reader = new CdsExportHospitalReportFileSourceReader(reports, "relative/dir", "1024");
        assertThatThrownBy(() -> capture(reader, List.of(row(1, "LabUpload.xml"))))
                .hasMessage("CDS_EXPORT_DOCUMENT_SOURCE_UNAVAILABLE");
    }

    @Test
    void shouldResolveAbsoluteNamesInsideAndOutsideTheDocumentDirectory_forTheViewerRule() {
        Path directory = Path.of("/var/lib/carlos/document");
        assertThat(
                        CdsExportHospitalReportFileSourceReader.relative(
                                directory, "/var/lib/carlos/document/hrm/a.xml"))
                .isEqualTo("hrm/a.xml");
        assertThat(CdsExportHospitalReportFileSourceReader.relative(directory, "/opt/hrm/a.xml"))
                .isEqualTo("opt/hrm/a.xml");
        assertThat(CdsExportHospitalReportFileSourceReader.relative(directory, "a.xml"))
                .isEqualTo("a.xml");
        assertThat(CdsExportHospitalReportFileSourceReader.relative(directory, "/var/lib/carlos/document"))
                .isNull();
    }

    @Test
    void shouldDeclareEveryHrmCaptureAsItsDependency_forTheFileContract() throws Exception {
        var reader = new CdsExportHospitalReportFileSourceReader(reports, "", "1024");
        assertThat(reader.requiredTables())
                .containsExactlyInAnyOrder(
                        "HRMDocumentToDemographic",
                        "HRMDocument",
                        "HRMDocumentComment",
                        "HRMDocumentSubClass",
                        "HRMDocumentToProvider",
                        "hrm_report_files",
                        "provider",
                        "clinical_timezone");
        assertThatCode(
                        () ->
                                new CdsExportSourceSchemaValidator()
                                        .verify(reader.schema(), reader.columns()))
                .doesNotThrowAnyException();
        when(reports.count(Plan.HRM_REPORT_FILES, 27)).thenReturn(3L);
        assertThat(reader.count(27)).isEqualTo(3);
    }
}
