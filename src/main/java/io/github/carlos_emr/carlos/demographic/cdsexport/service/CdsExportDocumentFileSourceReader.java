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
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** Native files are encrypted within the same patient snapshot as their complete document/link metadata. */
@Service
public class CdsExportDocumentFileSourceReader implements CdsExportNativeSourceReader {
    static final String TABLE = "native_document_files";
    private final DocumentDao documents;
    private final String root;
    private final String configuredMaximumBytes;
    public CdsExportDocumentFileSourceReader(@Qualifier("documentDaoImpl") DocumentDao documents, @Value("${DOCUMENT_DIR:}") String root,
            @Value("${cds.export.maxDocumentBytes:1073741824}") String maximumBytes) {
        this.documents = Objects.requireNonNull(documents); this.root = Objects.requireNonNull(root);
        configuredMaximumBytes = Objects.requireNonNull(maximumBytes);
    }
    @Override public String table() { return TABLE; }
    @Override public Set<String> columns() { return Set.of("document_no", "filename", "content"); }
    @Override public Set<CdsCategory> categories() { return Set.of(CdsCategory.REPORTS); }
    @Override public Set<String> requiredTables() { return Set.of("document", "ctl_document", TABLE); }
    @Override public CdsExportTableSchemaDto schema() {
        try (var input = getClass().getResourceAsStream("/cds-export/source-schema/" + TABLE + ".json")) {
            if (input == null) throw new IOException("CDS_EXPORT_SOURCE_SCHEMA_CONTRACT_MISSING"); return new ObjectMapper().readValue(input, CdsExportTableSchemaDto.class);
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    @Override public long count(int patient) { return documents.countCdsExportRows(patient); }
    @Override public void capture(int patient, CdsExportNativeCaptureComposer.Writer output) throws IOException { capture(patient, output, () -> {}); }
    @Override public void capture(int patient, CdsExportNativeCaptureComposer.Writer output, Runnable checkpoint) throws IOException {
        Long after = null; long maximumBytes = -1;
        while (true) {
            checkpoint.run(); var page = documents.findCdsExportRowsAfter(patient, after, 32); if (page.size() > 32) invalid();
            for (var document : page) {
                checkpoint.run(); if (document.id() < 1 || after != null && document.id() <= after) invalid();
                if (maximumBytes < 1) maximumBytes = maximumBytes(); long frozenMaximum = maximumBytes;
                var cell = document.cells().get("docfilename"); if (cell == null || cell.sqlNull() || cell.inlineBytes() == null || cell.bytes() > 1024) invalid();
                String filename = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(cell.inlineBytes())).toString();
                output.row(CdsExportPatientTableSourceReader.key(document.id()), row -> {
                    row.text("document_no", Long.toString(document.id())); row.text("filename", filename);
                    try (var input = new CdsExportDocumentInputStream(Path.of(root), filename, frozenMaximum, checkpoint)) { row.binary("content", input); }
                }); after = document.id();
            }
            if (page.size() < 32) break;
        }
    }
    /** Invalid active capture settings cannot disable metadata-only job observation or an empty document domain. */
    private long maximumBytes() throws IOException {
        try {
            if (!configuredMaximumBytes.matches("[0-9]{1,13}")) throw new NumberFormatException();
            long value = Long.parseLong(configuredMaximumBytes); if (value < 1 || value > (1L << 40)) throw new NumberFormatException(); return value;
        } catch (NumberFormatException invalid) { throw new IOException("CDS_EXPORT_DOCUMENT_BOUND_INVALID"); }
    }
    private static void invalid() throws IOException { throw new IOException("CDS_EXPORT_DOCUMENT_SOURCE_INVALID"); }
}
