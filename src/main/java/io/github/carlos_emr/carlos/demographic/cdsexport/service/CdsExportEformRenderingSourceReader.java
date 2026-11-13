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
import io.github.carlos_emr.carlos.commn.dao.CdsExportEformRenderReadDao;
import io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

/**
 * The rendered form of each current eForm of the patient, captured as the synthetic table
 * {@value #TABLE} in the same encrypted, authenticated patient capture as the eForm rows.
 *
 * <p>The renders run before the source transaction ({@link #forCapture(int, CdsExportRunControl,
 * Spool)}): a browser render takes seconds and reads the eForm through the web application, so it
 * cannot run inside the capture's snapshot. Each eForm is rendered one at a time as the job's
 * effective provider, re-authorized for every render ({@link CdsExportRunControl#authorizeRendering}),
 * and the PDF goes straight to the capture's encrypted spool. Inside the snapshot the current
 * eForms are read again; a render is captured only when its eForm row is digest-for-digest the row
 * the capture holds, so an eForm edited, replaced or added meanwhile is captured as {@code CHANGED}.
 *
 * <p>One row per current eForm: {@code fdid}, {@code outcome} ({@code RENDERED} or {@code
 * UNAVAILABLE}), {@code reason} (empty, or a {@link CdsExportEformRenderService.Reason} code),
 * the saving provider's {@code author_first_name}/{@code author_last_name} and {@code content} (the
 * PDF, or empty). No outcome holds the patient: the eForm's data summary carries every stored value
 * either way, with a note when the rendered form is unavailable.
 *
 * @since 2026-11-13
 */
@Service
public class CdsExportEformRenderingSourceReader implements CdsExportNativeSourceReader {
    public static final String TABLE = "native_eform_renderings";
    static final String RENDERED = "RENDERED";
    static final String UNAVAILABLE = "UNAVAILABLE";
    private static final Logger LOG = LogManager.getLogger(CdsExportEformRenderingSourceReader.class);

    /** A render made before the snapshot, for the eForm row with this digest. */
    record Prepared(String digest, CdsExportEformRenderService.Outcome outcome) {}

    private final CdsExportEformRenderReadDao source;
    private final CdsExportEformRenderService renders;
    private final Map<Integer, Prepared> prepared;
    private final Spool spool;

    @org.springframework.beans.factory.annotation.Autowired
    public CdsExportEformRenderingSourceReader(
            CdsExportEformRenderReadDao source, CdsExportEformRenderService renders) {
        this(source, renders, null, null);
    }

    private CdsExportEformRenderingSourceReader(
            CdsExportEformRenderReadDao source,
            CdsExportEformRenderService renders,
            Map<Integer, Prepared> prepared,
            Spool spool) {
        this.source = Objects.requireNonNull(source);
        this.renders = Objects.requireNonNull(renders);
        this.prepared = prepared == null ? null : Map.copyOf(prepared);
        this.spool = spool;
    }

    @Override
    public String table() {
        return TABLE;
    }

    @Override
    public Set<String> columns() {
        return Set.of("author_first_name", "author_last_name", "content", "fdid", "outcome", "reason");
    }

    @Override
    public Set<CdsCategory> categories() {
        return Set.of(CdsCategory.REPORTS);
    }

    @Override
    public Set<String> requiredTables() {
        return Set.of("eform_data", TABLE);
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

    /**
     * Renders every current eForm of the patient, one at a time, before the source transaction.
     * The renders and their spooled PDFs belong to the returned reader only.
     */
    @Override
    public CdsExportNativeSourceReader forCapture(
            int patient, CdsExportRunControl control, Spool spool) throws IOException {
        Objects.requireNonNull(control);
        Objects.requireNonNull(spool);
        control.checkpoint();
        var current = source.current(patient);
        var result = new TreeMap<Integer, Prepared>();
        long rendered = 0, unavailable = 0, total = 0, longest = 0;
        for (var eform : current) {
            control.checkpoint();
            CdsExportEformRenderService.Outcome outcome;
            if (!renders.available()) {
                outcome = CdsExportEformRenderService.Outcome.unavailable(
                        CdsExportEformRenderService.Reason.NOT_IN_THIS_PROCESS, 0);
            } else {
                // Fresh Reports and eForm rights for this patient, and the identity to render as.
                var who = control.authorizeRendering(patient);
                outcome = renders.render(eform.fdid(), who, spool, control::checkpoint);
            }
            if (outcome.rendered()) rendered++;
            else unavailable++;
            total += outcome.millis();
            longest = Math.max(longest, outcome.millis());
            result.put(eform.fdid(), new Prepared(eform.digest(), outcome));
        }
        control.checkpoint();
        if (!current.isEmpty())
            // Counts and times only: no patient, eForm or provider identifier.
            LOG.info(
                    "CDS_EXPORT_EFORM_RENDERS rendered={} unavailable={} renderMs={} longestMs={}",
                    rendered,
                    unavailable,
                    total,
                    longest);
        return new CdsExportEformRenderingSourceReader(source, renders, result, spool);
    }

    @Override
    public long count(int patient) {
        return source.count(patient);
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
        for (var eform : source.current(patient)) {
            checkpoint.run();
            var made = prepared == null ? null : prepared.get(eform.fdid());
            CdsExportEformRenderService.Reason reason;
            CdsExportStagedFileDto file = null;
            if (prepared == null) reason = CdsExportEformRenderService.Reason.NOT_PREPARED;
            else if (made == null || !made.digest().equals(eform.digest()))
                reason = CdsExportEformRenderService.Reason.CHANGED;
            else {
                reason = made.outcome().reason();
                file = made.outcome().file();
            }
            var content = file;
            var why = reason;
            output.row(
                    CdsExportPatientTableSourceReader.key(eform.fdid()),
                    row -> {
                        row.text("author_first_name", eform.authorFirstName());
                        row.text("author_last_name", eform.authorLastName());
                        if (content == null) row.binary("content", new ByteArrayInputStream(new byte[0]));
                        else
                            try (var input = spool.open(content)) {
                                row.binary("content", input);
                            }
                        row.text("fdid", Integer.toString(eform.fdid()));
                        row.text("outcome", content == null ? UNAVAILABLE : RENDERED);
                        row.text("reason", why == null ? "" : why.name());
                    });
        }
    }
}
