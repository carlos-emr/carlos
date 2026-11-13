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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dao.CdsExportHospitalReportReadDao;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * The stored message file of each of the patient's Hospital Report Manager reports, captured in
 * the same patient snapshot as the report rows. {@code HRMDocument.reportFile} names the file
 * the report viewer reads ({@code HRMReportParser.parseReport}): an absolute path inside
 * DOCUMENT_DIR, or a name resolved below DOCUMENT_DIR (an absolute path elsewhere is resolved
 * below it too, as {@code new File(DOCUMENT_DIR, reportFile)} does). The file is read through
 * the same directory-relative, no-follow, trusted-storage stream as a native document.
 *
 * <p>A report whose file cannot be read concerns only that report, and HRM reports are ordinary
 * chart content, so it never holds the patient: no recorded file ({@code NOT_RECORDED}), a file
 * that is not there ({@code MISSING}) and a name the stream refuses ({@code UNSUPPORTED_NAME})
 * are captured as that state with no content, and the report is exported from what CARLOS
 * recorded about it. Untrusted storage still stops the job and a file over the configured
 * document bound still holds the patient, exactly as for native documents.
 *
 * @since 2026-11-13
 */
@Service
public class CdsExportHospitalReportFileSourceReader implements CdsExportNativeSourceReader {
    static final String TABLE = CdsExportHospitalReportSources.FILES;

    /** What the capture found for a report's stored message file. */
    enum State {
        READ,
        NOT_RECORDED,
        MISSING,
        UNSUPPORTED_NAME
    }

    private static final String MISSING_CODE = "CDS_EXPORT_NATIVE_REPORT_FILE_MISSING";
    private static final String UNSUPPORTED_CODE = "CDS_EXPORT_NATIVE_REPORT_FILENAME_UNSUPPORTED";

    private final CdsExportHospitalReportReadDao reports;
    private final String root;
    private final String configuredMaximumBytes;

    public CdsExportHospitalReportFileSourceReader(
            CdsExportHospitalReportReadDao reports,
            @Value("${DOCUMENT_DIR:}") String root,
            @Value("${cds.export.maxDocumentBytes:1073741824}") String maximumBytes) {
        this.reports = Objects.requireNonNull(reports);
        this.root = Objects.requireNonNull(root);
        configuredMaximumBytes = Objects.requireNonNull(maximumBytes);
    }

    @Override
    public String table() {
        return TABLE;
    }

    @Override
    public Set<String> columns() {
        return Set.of("hrm_document_id", "report_file", "state", "content");
    }

    @Override
    public Set<CdsCategory> categories() {
        return Set.of(CdsCategory.REPORTS);
    }

    @Override
    public Set<String> requiredTables() {
        return CdsExportHospitalReportSources.REQUIRED;
    }

    @Override
    public CdsExportTableSchemaDto schema() {
        try (var input =
                getClass().getResourceAsStream("/cds-export/source-schema/" + TABLE + ".json")) {
            if (input == null) throw new IOException("CDS_EXPORT_SOURCE_SCHEMA_CONTRACT_MISSING");
            return new ObjectMapper().readValue(input, CdsExportTableSchemaDto.class);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** One row per linked report, whatever its file state. */
    @Override
    public long count(int patient) {
        return reports.count(Plan.HRM_REPORT_FILES, patient);
    }

    @Override
    public void capture(int patient, CdsExportNativeCaptureComposer.Writer output)
            throws IOException {
        capture(patient, output, () -> {});
    }

    @Override
    public void capture(
            int patient, CdsExportNativeCaptureComposer.Writer output, Runnable checkpoint)
            throws IOException {
        Long after = null;
        long maximumBytes = -1;
        Path directory = null;
        while (true) {
            checkpoint.run();
            var page = reports.page(Plan.HRM_REPORT_FILES, patient, after, 32);
            if (page.size() > 32) invalid();
            for (var report : page) {
                checkpoint.run();
                long id = report.id();
                if (id < 1 || id > Integer.MAX_VALUE || after != null && id <= after) invalid();
                var cell = report.cells().get("reportFile");
                if (cell == null
                        || !cell.sqlNull() && (cell.inlineBytes() == null || cell.bytes() > 1024))
                    invalid();
                String stored = cell.sqlNull() ? null : decode(cell.inlineBytes());
                String relative = null;
                State state;
                if (stored == null || stored.isEmpty()) state = State.NOT_RECORDED;
                else {
                    if (maximumBytes < 1) maximumBytes = maximumBytes();
                    if (directory == null) directory = directory();
                    relative = relative(directory, stored);
                    state = relative == null ? State.UNSUPPORTED_NAME : State.READ;
                }
                long frozenMaximum = maximumBytes;
                Path frozenDirectory = directory;
                String frozenRelative = relative;
                State found = state;
                output.row(
                        CdsExportPatientTableSourceReader.key(id),
                        row -> {
                            State result = found;
                            InputStream input = null;
                            if (result == State.READ) {
                                try {
                                    input =
                                            new CdsExportDocumentInputStream(
                                                    frozenDirectory,
                                                    frozenRelative,
                                                    frozenMaximum,
                                                    checkpoint);
                                } catch (CdsExportMappingReviewException unreadable) {
                                    // A purged file or a name the stream refuses concerns this
                                    // report alone: it is exported from its recorded details.
                                    // A file over the document bound still holds the patient.
                                    if (MISSING_CODE.equals(unreadable.getMessage()))
                                        result = State.MISSING;
                                    else if (UNSUPPORTED_CODE.equals(unreadable.getMessage()))
                                        result = State.UNSUPPORTED_NAME;
                                    else throw unreadable;
                                }
                            }
                            row.text("hrm_document_id", Long.toString(id));
                            row.text("report_file", stored);
                            row.text("state", result.name());
                            if (input == null) row.text("content", (String) null);
                            else
                                try (var content = input) {
                                    row.binary("content", content);
                                }
                        });
                after = id;
            }
            if (page.size() < 32) break;
        }
    }

    /**
     * The name below DOCUMENT_DIR the report viewer would read, or null when it is no plain
     * relative name. An absolute path inside DOCUMENT_DIR is taken relative to it; any other
     * absolute path is resolved below DOCUMENT_DIR, as {@code new File(DOCUMENT_DIR, reportFile)}
     * does, and the stream then checks the name as it checks a native document's.
     */
    static String relative(Path directory, String stored) {
        try {
            Path candidate = Path.of(stored);
            if (!candidate.isAbsolute()) return stored;
            Path normal = candidate.normalize();
            if (normal.equals(candidate) && normal.startsWith(directory)) {
                String inside = directory.relativize(normal).toString();
                return inside.isEmpty() ? null : inside;
            }
            String below = stored;
            while (below.startsWith("/")) below = below.substring(1);
            return below.isEmpty() ? null : below;
        } catch (InvalidPathException malformed) {
            return null;
        }
    }

    /** DOCUMENT_DIR as the document stream requires it: absolute and in normal form. */
    private Path directory() throws IOException {
        try {
            Path directory = Path.of(root);
            if (root.isEmpty() || !directory.isAbsolute() || !directory.equals(directory.normalize()))
                throw new IOException("CDS_EXPORT_DOCUMENT_SOURCE_UNAVAILABLE");
            return directory;
        } catch (InvalidPathException malformed) {
            throw new IOException("CDS_EXPORT_DOCUMENT_SOURCE_UNAVAILABLE");
        }
    }

    /** Invalid capture settings cannot disable metadata-only job observation. */
    private long maximumBytes() throws IOException {
        try {
            if (!configuredMaximumBytes.matches("[0-9]{1,13}")) throw new NumberFormatException();
            long value = Long.parseLong(configuredMaximumBytes);
            if (value < 1 || value > (1L << 40)) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException invalid) {
            throw new IOException("CDS_EXPORT_DOCUMENT_BOUND_INVALID");
        }
    }

    private static String decode(byte[] bytes) throws IOException {
        return StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_HOSPITAL_REPORT_FILE_SOURCE_INVALID");
    }
}
