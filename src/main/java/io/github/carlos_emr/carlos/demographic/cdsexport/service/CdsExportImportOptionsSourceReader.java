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
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.commn.dao.CdsImportRunDao;
import io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import java.io.*;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Freezes minimum verified acceptance/copy options in the same encrypted patient capture.
 * Unapplied runs require no original files. Only this patient's applied copy keys enter
 * export storage; another student's copies and the private command never enter capture.
 */
@Service
public class CdsExportImportOptionsSourceReader implements CdsExportNativeSourceReader {
    public static final String TABLE="retained_import_options";
    private final CdsImportRunDao source;
    private final CdsExportConfigurationService configuration;
    private final CdsExportRetainedImportOptionsReader reader;
    private final Set<String> selected;
    @Autowired public CdsExportImportOptionsSourceReader(CdsImportRunDao source, CdsExportConfigurationService configuration) {
        this(source, configuration, new CdsExportRetainedImportOptionsReader(), Set.of(CdsCategory.DEMOGRAPHICS.wireName()));
    }
    CdsExportImportOptionsSourceReader(CdsImportRunDao source, CdsExportConfigurationService configuration, CdsExportRetainedImportOptionsReader reader, Set<String> selected) {
        this.source=Objects.requireNonNull(source); this.configuration=Objects.requireNonNull(configuration); this.reader=Objects.requireNonNull(reader); this.selected=Set.copyOf(selected);
    }
    @Override public CdsExportNativeSourceReader forRequest(CdsExportCommand command) {
        return new CdsExportImportOptionsSourceReader(source, configuration, reader, command.categories().stream().map(CdsCategory::wireName).collect(java.util.stream.Collectors.toSet()));
    }
    @Override public String table(){return TABLE; }
    @Override public Set<String> columns(){return Set.of("options"); }
    @Override public Set<CdsCategory> categories(){return Set.of(CdsCategory.DEMOGRAPHICS); }
    @Override public Set<String> requiredTables(){return Set.of("cds_import_run", "cds_import_patient", "cds_import_unit"); }
    @Override public CdsExportTableSchemaDto schema(){try (var input=getClass().getResourceAsStream("/cds-export/source-schema/"+TABLE+".json")){if (input==null)throw new IOException("CDS_EXPORT_SOURCE_SCHEMA_CONTRACT_MISSING"); return new ObjectMapper().readValue(input, CdsExportTableSchemaDto.class); }catch (IOException failed){throw new UncheckedIOException(failed); }}
    @Override public long count(int patient){if (patient<1)throw new IllegalArgumentException("CDS_EXPORT_IMPORT_OPTIONS_SCOPE_INVALID"); long count=source.countCdsExportRetainedRows(patient, selected); if (count<0||count>4096)throw new IllegalStateException("CDS_EXPORT_IMPORT_OPTIONS_RUN_LIMIT"); return count; }
    @Override public void capture(int patient, CdsExportNativeCaptureComposer.Writer output)throws IOException {capture(patient, output, ()-> {}); }
    @Override public void capture(int patient, CdsExportNativeCaptureComposer.Writer output, Runnable checkpoint)throws IOException {
        Objects.requireNonNull(checkpoint); checkpoint.run();
        if (patient<1)throw new IllegalArgumentException("CDS_EXPORT_IMPORT_OPTIONS_SCOPE_INVALID"); String after=null; int count=0;
        while (true) {
            var page=source.findCdsExportRetainedRowsAfter(patient, selected, after, 32); if (page.size()>32)invalid();
            for (var row:page) {
                checkpoint.run();
                if (!Plan.IMPORT_RUNS.validRetainedKey(row.id())||after!=null&&row.id().compareTo(after)<=0||!row.cells().keySet().equals(Set.copyOf(Plan.IMPORT_RUNS.columns()))||++count>4096)invalid();
                var copies=source.readAppliedCdsExportCopyKeys(patient, row.id(), selected); if (copies==null||copies.size()>4096||copies.stream().anyMatch(Objects::isNull)||copies.size()!=new HashSet<>(copies).size())invalid(); String encoded=null;
                if (!copies.isEmpty()) {
                    var fields=new LinkedHashMap<String, String>();
                    for (var field:row.cells().entrySet()) {
                        if (field.getValue().sqlNull())fields.put(field.getKey(), null);
                        else try (var input=new CdsExportRawValueInputStream(field.getValue(), (offset, length)-> source.readCdsExportRetainedChunk(patient, selected, row.id(), field.getKey(), offset, length));
                                var text=new InputStreamReader(input, StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT))) {
                            var value=new StringBuilder(); char[] block=new char[1024]; for (int n; (n=text.read(block))!=-1; ){checkpoint.run(); if (n==0||value.length()+n>65536)invalid(); value.append(block, 0, n); }Arrays.fill(block, '\0'); fields.put(field.getKey(), value.toString());
                        }
                    }
                    var original=new CdsExportCapturedTextRowDto("cds_import_run", row.id(), fields);
                    var options=reader.readProtected(configuration.retainedImportRoot(), original, checkpoint);
                    encoded=CdsExportRetainedImportOptionsReader.encodeCaptured(CdsExportRetainedImportOptionsReader.selectCopies(options, Set.copyOf(copies)));
                }
                final String frozen=encoded; output.row(row.id(), writer-> writer.text("options", frozen)); after=row.id();
            }
            if (page.size()<32)return;
        }
    }
    private static void invalid()throws IOException{throw new IOException("CDS_EXPORT_IMPORT_OPTIONS_SOURCE_INVALID"); }
}
