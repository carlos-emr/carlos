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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.*;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;

/**
 * Scoped effective-value access after complete metadata and owned-payload verification.
 * No original import path is reopened. Each range authenticates its own field; one
 * pinned descriptor authenticates the whole frozen member before and after the callback.
 * Random reads decrypt only overlapping chunks and retain at most one plaintext chunk.
 * Consumers must keep output private until this entire callback and its final authority
 * check succeed. Source access is distinct from clinical reconciliation/qualification.
 */
@Service
public class CdsExportRetainedValuesService {
    @FunctionalInterface public interface Work {
        void use(CdsExportRetainedIndexDto index, List<CdsExportRetainedFieldDto> fields,
                CdsExportRetainedSectionWriter.Values values) throws IOException;
    }
    private final CdsExportRetainedIndexService indices;
    private final CdsExportRetainedPayloadService payloads;
    private final CdsExportNativeCaptureService captures;
    private final CdsExportStagingFactory stores;
    public CdsExportRetainedValuesService(CdsExportRetainedIndexService indices, CdsExportRetainedPayloadService payloads,
            CdsExportNativeCaptureService captures, CdsExportStagingFactory stores) {
        this.indices = Objects.requireNonNull(indices); this.payloads = Objects.requireNonNull(payloads);
        this.captures = Objects.requireNonNull(captures); this.stores = Objects.requireNonNull(stores);
    }
    // Cleanup only throws without a primary failure; otherwise its error is suppressed on that failure.
    @SuppressWarnings("java:S1143")
    public void withValues(CdsExportLeaseDto lease, CdsExportCohortService.Patient patient, Set<String> requiredTables,
            CdsExportStagedFileDto sourceManifest, CdsExportStagedFileDto payloadManifest, long maximumFields,
            long maximumBytes, int maximumRows, long maximumIndexBytes, CdsExportRunControl control, Work work) throws IOException {
        Objects.requireNonNull(work); var tables = Set.copyOf(requiredTables);
        var index = indices.index(lease, patient, tables, sourceManifest, maximumFields, maximumBytes, maximumRows, maximumIndexBytes, control);
        var fields = new TreeMap<String, CdsExportRetainedFieldDto>(); var mapper = new CdsExportRetainedFieldMapper();
        for (var row : index.tables().get("cds_import_field").values()) {
            control.checkpoint(); var field = mapper.map(patient.patient(), lease.document().command().categories(), row);
            if (fields.putIfAbsent(field.id(), field) != null) invalid();
        }
        var ranges = new HashMap<String, CdsExportCapturedFieldDto>();
        var payload = payloads.scan(lease, patient, tables, sourceManifest, payloadManifest, maximumFields, maximumBytes, control, descriptor -> {
            var field = fields.get(descriptor.recordKey());
            if (field == null || ranges.putIfAbsent(field.id(), descriptor) != null) invalid();
            var original = field.originalPayload();
            if (original == null) { if (descriptor.kind() != CdsExportCapturedFieldDto.Kind.SQL_NULL) invalid(); }
            else if (descriptor.kind() != CdsExportCapturedFieldDto.Kind.BINARY || descriptor.bytes() != original.bytes()
                    || !descriptor.sha256().equals(original.sha256())) invalid();
            return OutputStream.nullOutputStream();
        });
        if (payload.fields() != fields.size() || !ranges.keySet().equals(fields.keySet())) invalid();
        var store = stores.openExisting(lease.runId(), lease.epoch(), lease.document().storage(), control::checkpoint);
        Runnable authorize = () -> captures.authorizeSources(lease, patient, tables, control);
        try (var verified = store.verifiedRanges(payload.values())) {
            var scope = new Scope(fields, ranges, verified, control::checkpoint, authorize);
            Throwable primary = null;
            try {
                work.use(index, List.copyOf(fields.values()), scope);
                if (scope.failed || !scope.open.isEmpty()) throw new IOException("CDS_EXPORT_RETAINED_VALUES_INCOMPLETE");
                authorize.run(); control.checkpoint();
            } catch (IOException | RuntimeException | Error failure) { primary = failure; throw failure; }
            finally {
                scope.active = false;
                for (var stream : List.copyOf(scope.open)) try { stream.close(); }
                catch (IOException | RuntimeException cleanup) { if (primary != null) primary.addSuppressed(cleanup); else throw cleanup; }
            }
        }
        // A potentially large final whole-file verification must precede the last live check.
        authorize.run(); control.checkpoint();
    }
    private static void invalid() throws IOException { throw new IOException("CDS_EXPORT_RETAINED_VALUES_REQUIRES_REVIEW"); }
    private static final class Scope implements CdsExportRetainedSectionWriter.Values {
        private final Map<String, CdsExportRetainedFieldDto> fields;
        private final Map<String, CdsExportCapturedFieldDto> ranges;
        private final CdsExportStagingService.VerifiedRanges verified;
        private final Runnable checkpoint, authorize;
        private final Thread owner = Thread.currentThread();
        private final Set<InputStream> open = Collections.newSetFromMap(new IdentityHashMap<>());
        private boolean active = true;
        private volatile boolean failed;
        private Scope(Map<String, CdsExportRetainedFieldDto> fields, Map<String, CdsExportCapturedFieldDto> ranges,
                CdsExportStagingService.VerifiedRanges verified, Runnable checkpoint, Runnable authorize) {
            this.fields = Map.copyOf(fields); this.ranges = Map.copyOf(ranges);
            this.verified = verified; this.checkpoint = checkpoint; this.authorize = authorize;
        }
        private void check() throws IOException {
            if (!active || failed || Thread.currentThread() != owner) throw new IOException("CDS_EXPORT_RETAINED_VALUES_SCOPE_INVALID");
            checkpoint.run();
        }
        @Override public void requireActive() throws IOException {
            try { check(); authorize.run(); check(); }
            catch (IOException | RuntimeException | Error failure) { failed = true; throw failure; }
        }
        @Override public Reader text(CdsExportRetainedFieldDto field) throws IOException {
            return new GuardedReader(new InputStreamReader(value(field, false), StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)));
        }
        @Override public InputStream binary(CdsExportRetainedFieldDto field) throws IOException { return value(field, true); }
        // Ownership transfers to Guarded and the scope open-set; the enclosing scope closes every unretired stream.
        @SuppressWarnings("java:S2095")
        private InputStream value(CdsExportRetainedFieldDto field, boolean binary) throws IOException {
            try {
                check(); if (!open.isEmpty() || field == null || !field.equals(fields.get(field.id()))) invalid();
                boolean isBinary = field.valueType().equals("{http://www.w3.org/2001/XMLSchema}base64Binary");
                if (isBinary != binary || binary && field.logicalInline() != null) invalid();
                InputStream input;
                if (!binary && field.logicalInline() != null) input = utf8(field.logicalInline());
                else if (!binary && field.originalInline() != null) input = utf8(field.originalInline());
                else {
                    if (field.originalPayload() == null) invalid(); var range = ranges.get(field.id()); authorize.run();
                    input = new CdsExportAuthenticatedRangeInputStream(verified.open(range.offset(), range.bytes(), range.sha256()), 0, range.bytes(),
                            range.sha256(), this::check, authorize);
                }
                var guarded = new Guarded(input); open.add(guarded); return guarded;
            } catch (IOException | RuntimeException | Error failure) { failed = true; throw failure; }
        }
        private static InputStream utf8(String text) throws IOException {
            var bytes = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(text));
            byte[] exact = new byte[bytes.remaining()]; bytes.get(exact); return new ByteArrayInputStream(exact);
        }
        private final class Guarded extends InputStream {
            private final InputStream input;
            private boolean closed;
            private Guarded(InputStream input) { this.input = input; }
            @Override public int read() throws IOException { byte[] one = new byte[1]; int count = read(one); return count == -1 ? -1 : one[0] & 255; }
            @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                try { check(); if (closed) invalid(); return input.read(bytes, offset, length); }
                catch (IOException | RuntimeException | Error failure) { failed = true; throw failure; }
            }
            @Override public void close() throws IOException {
                if (closed) return;
                try { input.close(); }
                catch (IOException | RuntimeException | Error failure) { failed = true; throw failure; }
                finally { closed = true; open.remove(this); }
            }
        }
        private final class GuardedReader extends Reader {
            private final Reader input;
            private boolean closed;
            private GuardedReader(Reader input) { this.input = input; }
            @Override public int read(char[] chars, int offset, int length) throws IOException {
                try { check(); if (closed) invalid(); return input.read(chars, offset, length); }
                catch (IOException | RuntimeException | Error failure) { failed = true; throw failure; }
            }
            // Cleanup only throws without a primary failure; otherwise its error is suppressed on that failure.
            @SuppressWarnings("java:S1143")
            @Override public void close() throws IOException {
                if (closed) return;
                Throwable primary = null;
                try {
                    if (!failed && active) {
                        char[] buffer = new char[8192];
                        try { while (read(buffer) != -1) { /* Validate the complete UTF-8 value before closing. */ } }
                        finally { Arrays.fill(buffer, '\u0000'); }
                    }
                } catch (IOException | RuntimeException | Error failure) { primary = failure; throw failure; }
                finally {
                    closed = true;
                    try { input.close(); }
                    catch (IOException | RuntimeException cleanup) { if (primary != null) primary.addSuppressed(cleanup); else { failed = true; throw cleanup; } }
                }
            }
        }
    }
}
