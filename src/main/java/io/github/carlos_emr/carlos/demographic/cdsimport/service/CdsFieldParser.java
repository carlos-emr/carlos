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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsCorrectionSetDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import java.io.ByteArrayOutputStream;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.FilterInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.Channels;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths;
import java.util.Set;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Optional;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.nio.file.attribute.PosixFilePermissions;
import java.io.BufferedOutputStream;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

/** Bounded access to persisted source fields; no record-sized collection or XML graph is built. */
@Service
public class CdsFieldParser {
    private static final Pattern ORDINAL=Pattern.compile("\\[[0-9]+\\]");
    private record FieldIndex(CdsRecordDto record, Map<String, CdsFieldDto> first, boolean complete) { }
    private final AtomicReference<FieldIndex> firstFields=new AtomicReference<>();
    private record CorrectionCache(Path path, String digest, String xmlDigest, List<JsonNode> entries, boolean physicallyVerified) { }
    /** One bounded immutable manifest, never a cache that grows with patients or imports. */
    private final AtomicReference<CorrectionCache> correctionCache=new AtomicReference<>();
    private static final ObjectMapper JSON = new ObjectMapper(com.fasterxml.jackson.core.JsonFactory.builder()
            .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final int MAX_METADATA_LINE = 65536;
    private final ThreadLocal<ControlState> controls=new ThreadLocal<>();

    private static final class ControlState {
        final Runnable probe;
        final ControlState previous;
        long nextProbe;
        ControlState(Runnable probe, ControlState previous) { this.probe=probe; this.previous=previous; }
    }

    /** Scoped operational control for I/O outside clinical transactions; nested callers restore their predecessor. */
    public final class ControlScope implements AutoCloseable {
        private final ControlState state;
        private final Thread owner=Thread.currentThread();
        private boolean closed;
        private ControlScope(ControlState state) { this.state=state; }
        @Override public void close() {
            if (closed) return;
            if (owner!=Thread.currentThread() || controls.get()!=state) throw new IllegalStateException("CDS_IO_CONTROL_SCOPE_ORDER_INVALID");
            if (state.previous==null) controls.remove(); else controls.set(state.previous);
            closed=true;
        }
    }

    public ControlScope control(Runnable probe) {
        if (probe==null) throw new IllegalArgumentException("Missing CDS operational probe");
        ControlState state=new ControlState(probe, controls.get());
        controls.set(state); return new ControlScope(state);
    }

    /** Allows related bounded file hashing to share the current operational I/O scope. */
    public void checkpoint() throws IOException { checkControl(); }

    private void checkControl() throws IOException {
        if (Thread.currentThread().isInterrupted()) throw failure("CDS_INPUT_CANCELLED");
        ControlState state=controls.get();
        if (state!=null) {
            long now=System.nanoTime();
            if (now>=state.nextProbe) {
                state.nextProbe=now+250_000_000L;
                state.probe.run();
            }
        }
    }

    public void forEachField(CdsRecordDto record, Consumer<CdsFieldDto> consumer) throws IOException {
        try (InputStream slice = new BufferedInputStream(
                openSlice(record.fieldsFile(), record.fieldsOffset(), record.fieldsLength()), 65536)) {
            byte[] line;
            long count = 0;
            MessageDigest digest = CdsPaths.sha256();
            while ((line = readMetadataLine(slice)) != null) {
                checkControl();
                CdsFieldDto field = JSON.readValue(line, CdsFieldDto.class);
                digest.update(identity(field));
                consumer.accept(field);
                count++;
            }
            if (count != record.fieldCount()) {
                throw failure("CDS_SPOOL_FIELD_COUNT_MISMATCH");
            }
            if (!CdsPaths.hex(digest.digest()).equals(record.sha256())) {
                throw failure("CDS_SPOOL_RECORD_HASH_MISMATCH");
            }
        }
    }

    /** Matches a full relative path. Unindexed caller paths deliberately match the first occurrence. */
    public String firstText(CdsRecordDto record, String path, int maxCharacters) throws IOException {
        return firstText(record, path, null, maxCharacters);
    }

    public String firstText(CdsRecordDto record, String path, String namespace, int maxCharacters)
            throws IOException {
        return firstValue(record, path, namespace, maxCharacters, null);
    }

    /** Explicit XML Schema token semantics for a caller that knows the field's domain contract. */
    public String firstToken(CdsRecordDto record, String path, int maxNormalizedCharacters) throws IOException {
        return firstValue(record, path, null, maxNormalizedCharacters, "collapse");
    }

    /** Reads one known descriptor with its pinned schema whitespace semantics. */
    public String readLogicalText(CdsRecordDto record, CdsFieldDto field, int maxCharacters) throws IOException {
        return readLogicalText(record, field, maxCharacters, field.whitespace());
    }

    /** Collapses XML whitespace incrementally without materializing the raw lexical value. */
    public String readToken(CdsRecordDto record, CdsFieldDto field, int maxNormalizedCharacters) throws IOException {
        return readLogicalText(record, field, maxNormalizedCharacters, "collapse");
    }

    /** Returns only an explicit audited override; a large original remains a streamed source reference. */
    public Optional<String> correctedLogicalText(CdsRecordDto record, CdsFieldDto field, int maxCharacters) throws IOException {
        if (findCorrection(record, field)==null) return Optional.empty();
        return Optional.of(readLogicalText(record, field, maxCharacters));
    }

    @FunctionalInterface public interface CorrectionFileConsumer {
        void accept(Path path, String sha256, long bytes) throws IOException;
    }

    /** At most129 immutable files; publication verifies all, while a field page adopts only its used values. */
    public void forEachCorrectionFile(CdsRecordDto record, boolean matchingFieldsOnly, CorrectionFileConsumer consumer) throws IOException {
        if (record.corrections()==null) return;
        var reference=record.corrections(); Path manifest=CdsPaths.within(reference.manifestFile(), record.fieldsFile().getParent());
        List<JsonNode> entries=correctionEntries(reference, manifest);
        consumer.accept(manifest, reference.sha256(), Files.size(manifest));
        Set<String> wanted=new HashSet<>();
        try { if (matchingFieldsOnly) forEachField(record, field-> {
            String full=record.sourceXPath()+(field.path().isEmpty() ? "" : "/"+field.path());
            for (JsonNode entry:entries) if (entry.path("sourceXPath").asText().equals(full)) {
                try {verifyCorrection(entry, field); } catch (IOException failure) {throw new java.io.UncheckedIOException(failure); }
                wanted.add(full);
            }
        }); } catch (java.io.UncheckedIOException failed) {throw failed.getCause(); }
        Map<String, String> seen=new HashMap<>();
        for (JsonNode entry:entries) {
            if (matchingFieldsOnly && !wanted.contains(entry.path("sourceXPath").asText())) continue;
            String name=entry.path("replacementFile").asText(), digest=entry.path("replacementSha256").asText();
            long bytes=entry.path("replacementBytes").asLong(-1);
            if (!name.matches("[A-Za-z0-9_-]{1,128}\\.[A-Za-z0-9]{1,12}") || !digest.matches("[0-9a-f]{64}") || bytes<0 || bytes>262144) {
                throw failure("CDS_CORRECTION_REPLACEMENT_INVALID");
            }
            String identity=digest+":"+bytes, old=seen.putIfAbsent(name, identity);
            if (old!=null && !old.equals(identity)) throw failure("CDS_CORRECTION_REPLACEMENT_CONFLICT");
            if (old==null) consumer.accept(member(manifest.getParent(), name), digest, bytes);
        }
    }

    /** Coordinator calls this only after every correction artifact has current-epoch physical SHA proof. */
    void acceptVerifiedCorrections(CdsRecordDto record) throws IOException {
        if (record.corrections()==null) return;
        var reference=record.corrections(); List<JsonNode> entries=correctionEntries(reference, reference.manifestFile());
        correctionCache.set(new CorrectionCache(reference.manifestFile(), reference.sha256(), reference.acceptedXmlSha256(), entries, true));
    }

    private String firstValue(CdsRecordDto record, String path, String namespace, int maxCharacters,
            String explicitWhitespace) throws IOException {
        if (path == null || maxCharacters < 0) throw new IllegalArgumentException("Invalid field request");
        checkControl();
        FieldIndex index=firstIndex(record);
        String normalized=unindexed(path);
        CdsFieldDto selected=null;
        if (namespace!=null) selected=index.first().get(namespace+"\u0000"+normalized);
        else for (CdsFieldDto value:index.first().values()) {
            if (unindexed(value.path()).equals(normalized) && knownNamespace(value)) {selected=value; break; }
        }
        boolean indexed = path.indexOf('[') >= 0;
        if (selected==null && !index.complete() || selected!=null && indexed && !selected.path().equals(path)) {
            CdsFieldDto[] first={null};
            forEachField(record, field-> {
                if (first[0]!=null || field.kind().equals("BINARY")
                        || (namespace==null ? !knownNamespace(field) : !namespace.equals(field.namespace()))) return;
                if ((indexed ? field.path() : unindexed(field.path())).equals(path)) first[0]=field;
            });
            selected=first[0];
        }
        return selected==null ? null : readLogicalText(record, selected, maxCharacters,
                explicitWhitespace==null ? selected.whitespace() : explicitWhitespace);
    }

    private FieldIndex firstIndex(CdsRecordDto record) throws IOException {
        FieldIndex cached=firstFields.get();
        if (cached!=null && sameSlice(cached.record(), record)) return cached;
        Map<String, CdsFieldDto> first=new LinkedHashMap<>(); boolean[] complete={true};
        forEachField(record, field-> {
            if (field.kind().equals("BINARY")) return;
            String key=field.namespace()+"\u0000"+unindexed(field.path());
            if (first.containsKey(key)) return;
            if (first.size()==128) {complete[0]=false; return; }
            first.put(key, field);
        });
        FieldIndex built=new FieldIndex(record, Collections.unmodifiableMap(first), complete[0]);
        firstFields.set(built); return built;
    }

    private static boolean sameSlice(CdsRecordDto a, CdsRecordDto b) {
        return a.fieldsFile().equals(b.fieldsFile()) && a.valuesFile().equals(b.valuesFile())
                && a.fieldsOffset()==b.fieldsOffset() && a.fieldsLength()==b.fieldsLength()
                && a.fieldCount()==b.fieldCount() && a.sha256().equals(b.sha256());
    }

    private static boolean knownNamespace(CdsFieldDto field) {
        return field.namespace().equals("cds") || field.namespace().equals("cds_dt")
                || field.kind().equals("ATTRIBUTE") && field.namespace().isEmpty();
    }

    public String readText(CdsRecordDto record, CdsFieldDto field, int maxCharacters) throws IOException {
        if (maxCharacters < 0 || field.kind().equals("BINARY")) {
            throw new IllegalArgumentException("Invalid textual CDS field request");
        }
        try (InputStream stream = openValue(record, field);
                InputStreamReader reader = new InputStreamReader(stream,
                        StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT))) {
            StringBuilder result = new StringBuilder(Math.min(maxCharacters, 4096));
            char[] buffer = new char[4096];
            int characters = 0;
            int n;
            while ((n = reader.read(buffer)) >= 0) {
                checkControl();
                for (int i = 0; i < n; i++) {
                    if (!Character.isLowSurrogate(buffer[i])) characters++;
                }
                if (characters > maxCharacters) {
                    throw failure("CDS_TARGET_TEXT_CAPACITY_EXCEEDED");
                }
                result.append(buffer, 0, n);
            }
            return result.toString();
        }
    }

    /** The caller owns and closes this length-limited stream within its current operation. */
    public InputStream openValue(CdsRecordDto record, CdsFieldDto field) throws IOException {
        Path parent = record.fieldsFile().toAbsolutePath().getParent();
        Path path = PathValidationUtils.validatePrivateImportPath(record.valuesFile().toFile(), parent.toFile()).toPath();
        return new FilterInputStream(openSlice(path, field.offset(), field.length())) {
            private final MessageDigest digest = CdsPaths.sha256();
            private boolean verified;
            private void verify() throws IOException {
                if (!verified) {
                    verified = true;
                    if (!field.sha256().equals(CdsPaths.hex(digest.digest()))) throw failure("CDS_SPOOL_VALUE_HASH_MISMATCH");
                }
            }
            @Override public int read() throws IOException {
                checkControl();
                int value = in.read();
                if (value < 0) verify(); else digest.update((byte) value);
                return value;
            }
            @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                checkControl();
                int n = in.read(bytes, offset, length);
                if (n < 0) verify(); else if (n > 0) digest.update(bytes, offset, n);
                return n;
            }
        };
    }

    /** Complete byte verification for execution planning/reconciliation without materializing values. */
    public void verifyRecord(CdsRecordDto record) throws IOException {
        try {
            forEachField(record, field -> {
                try (InputStream input = openValue(record, field)) {
                    byte[] buffer = new byte[65536];
                    while (input.read(buffer) >= 0) {
                        if (Thread.currentThread().isInterrupted()) throw failure("CDS_INPUT_CANCELLED");
                    }
                } catch (IOException failed) { throw new java.io.UncheckedIOException(failed); }
            });
        } catch (java.io.UncheckedIOException failed) { throw failed.getCause(); }
    }

    /**
     * Emits bounded slices of the same immutable field/value files for separately sealed field
     * dependency units. Callers use their own monotonic page ordinal as a stable unit key.
     * This is planning only: original record identity is checked before planning can be sealed.
     */
    public void forEachPage(CdsRecordDto record, int maximumFields, Consumer<CdsRecordDto> pages)
            throws IOException {
        if (maximumFields < 1 || maximumFields > 4096) throw new IllegalArgumentException("Invalid CDS field page size");
        try (InputStream input = new BufferedInputStream(openSlice(record.fieldsFile(), record.fieldsOffset(), record.fieldsLength()), 65536)) {
            MessageDigest original = CdsPaths.sha256();
            MessageDigest page = CdsPaths.sha256();
            long offset = record.fieldsOffset();
            long pageStart = offset;
            long count = 0;
            long total = 0;
            byte[] row;
            while ((row = readMetadataLine(input)) != null) {
                checkControl();
                CdsFieldDto field = JSON.readValue(row, CdsFieldDto.class);
                byte[] identity = identity(field);
                original.update(identity); page.update(identity);
                offset = Math.addExact(offset, row.length + 1L); count++; total++;
                if (count == maximumFields) {
                    pages.accept(page(record, pageStart, offset - pageStart, CdsPaths.hex(page.digest()), count));
                    pageStart = offset; count = 0; page = CdsPaths.sha256();
                }
            }
            if (count != 0) pages.accept(page(record, pageStart, offset - pageStart, CdsPaths.hex(page.digest()), count));
            if (total != record.fieldCount() || !CdsPaths.hex(original.digest()).equals(record.sha256())) {
                throw failure("CDS_SPOOL_RECORD_HASH_MISMATCH");
            }
        }
    }

    /** Loads a fixed set of native scalar values in one metadata pass, without a record-sized map. */
    public Map<String, String> selectedTexts(CdsRecordDto record, Map<String, Integer> capacities)
            throws IOException {
        if (capacities.size() > 128 || capacities.values().stream().anyMatch(n -> n == null || n < 0)) {
            throw new IllegalArgumentException("Invalid bounded CDS scalar selection");
        }
        Map<String, String> selected = new HashMap<>();
        try {
            forEachField(record, field -> {
                if (field.kind().equals("BINARY") || !(field.namespace().equals("cds")
                        || field.namespace().equals("cds_dt")
                        || field.kind().equals("ATTRIBUTE") && field.namespace().isEmpty())) return;
                String path = unindexed(field.path());
                Integer capacity = capacities.get(path);
                if (capacity != null && !selected.containsKey(path)) {
                    try { selected.put(path, readLogicalText(record, field, capacity, field.whitespace())); }
                    catch (IOException failed) { throw new java.io.UncheckedIOException(failed); }
                }
            });
        } catch (java.io.UncheckedIOException failed) { throw failed.getCause(); }
        return Map.copyOf(selected);
    }

    private static CdsRecordDto page(CdsRecordDto source, long offset, long length, String digest, long count) {
        return new CdsRecordDto(source.section(), source.ordinal(), source.sourceXPath(), source.fieldsFile(),
                offset, length, source.valuesFile(), digest, count, source.corrections());
    }

    public void forEachSubrecord(CdsRecordDto record, String topLevelName, Consumer<CdsRecordDto> consumer)
            throws IOException {
        forEachSubrecord(record, topLevelName, CdsInputLimitsDto.defaults(), consumer);
    }

    /**
     * Derives native child occurrences into one shared append-only metadata file per source XML.
     * Child paths are rebased, original sourceXPath/ordinal and value offsets remain explicit.
     * The original parent field pages are still the sole source-field preservation inventory.
     */
    public void forEachSubrecord(CdsRecordDto record, String topLevelName, CdsInputLimitsDto limits,
            Consumer<CdsRecordDto> consumer) throws IOException {
        FieldIndex index=firstIndex(record);
        if (index.complete()) {
            boolean found=false;
            for (CdsFieldDto field:index.first().values()) {
                int slash=field.path().indexOf('/'); String first=slash<0?field.path():field.path().substring(0, slash);
                int bracket=first.lastIndexOf('[');
                if (bracket>0 && (topLevelName==null || topLevelName.equals("*") || first.substring(0, bracket).equals(topLevelName))) {found=true; break; }
            }
            if (!found)return;
        }
        if (topLevelName != null && !topLevelName.equals("*") && !topLevelName.matches("[A-Za-z][A-Za-z0-9]*")) {
            throw new IllegalArgumentException("Invalid CDS child selection");
        }
        Path parent = record.fieldsFile().toAbsolutePath().getParent();
        Path metadata = CdsPaths.within(parent.resolve("native-fields.jsonl"), parent);
        try (FileChannel channel = FileChannel.open(metadata,
                Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
            FileLock lock;
            try { lock = channel.tryLock(); }
            catch (OverlappingFileLockException busy) { throw failure("CDS_DERIVED_SPOOL_BUSY"); }
            if (lock == null) throw failure("CDS_DERIVED_SPOOL_BUSY");
            try (lock) {
                channel.position(channel.size());
                long initial = channel.position();
                class ChildSpool {
                    final java.io.OutputStream output = new BufferedOutputStream(Channels.newOutputStream(channel), 65536);
                    long position = initial;
                    long start = initial;
                    long count;
                    long nextDiskCheck = initial;
                    String scope;
                    String name;
                    long ordinal;
                    MessageDigest digest = CdsPaths.sha256();
                    void emit() throws IOException {
                        if (scope == null) return;
                        output.flush(); channel.force(false);
                        consumer.accept(new CdsRecordDto(name, ordinal, record.sourceXPath() + "/" + scope,
                                metadata, start, position - start, record.valuesFile(), CdsPaths.hex(digest.digest()), count, record.corrections()));
                        scope = null; count = 0; start = position; digest = CdsPaths.sha256();
                    }
                    void field(CdsFieldDto original) throws IOException {
                        if (Thread.currentThread().isInterrupted()) throw failure("CDS_INPUT_CANCELLED");
                        int slash = original.path().indexOf('/');
                        String first = slash < 0 ? original.path() : original.path().substring(0, slash);
                        int bracket = first.lastIndexOf('[');
                        if (bracket < 1 || !first.endsWith("]")) { emit(); return; }
                        String selectedName = first.substring(0, bracket);
                        boolean selected = topLevelName == null || topLevelName.equals("*") || topLevelName.equals(selectedName);
                        if (!selected) { emit(); return; }
                        if (!first.equals(scope)) {
                            emit(); scope = first; name = selectedName;
                            ordinal = Long.parseLong(first.substring(bracket + 1, first.length() - 1));
                            start = position;
                        }
                        CdsFieldDto rebased = new CdsFieldDto(slash < 0 ? "" : original.path().substring(slash + 1),
                                original.namespace(), original.kind(), original.offset(), original.length(), original.sha256(),
                                original.schemaType(), original.whitespace(), original.schemaDefault());
                        byte[] row = JSON.writeValueAsBytes(rebased);
                        output.write(row); output.write('\n'); position = Math.addExact(position, row.length + 1L);
                        digest.update(identity(rebased)); count++;
                        if (position - initial > limits.maxExpandedBytes()) throw failure("CDS_INPUT_BYTE_LIMIT");
                        if (position >= nextDiskCheck) {
                            if (Files.getFileStore(parent).getUsableSpace() < limits.minimumFreeDiskBytes()) throw failure("CDS_INPUT_DISK_RESERVE");
                            nextDiskCheck = Math.addExact(position, 16L << 20);
                        }
                    }
                }
                ChildSpool spool = new ChildSpool();
                try { forEachField(record, field -> { try { spool.field(field); } catch (IOException failed) { throw new java.io.UncheckedIOException(failed); } }); }
                catch (java.io.UncheckedIOException failed) { throw failed.getCause(); }
                spool.emit(); spool.output.flush(); channel.force(true); CdsPaths.forceDirectory(parent);
            }
        }
    }

    static byte[] identity(CdsFieldDto field) {
        return (field.path() + "\u0000" + field.namespace() + "\u0000" + field.kind() + "\u0000"
                + field.length() + "\u0000" + field.sha256() + "\u0000" + field.schemaType() + "\u0000"
                + field.whitespace()+"\u0000"+field.schemaDefault()+"\u0000"+CdsSchemaValidator.MAIN_SHA256+"\u0000"
                + CdsSchemaValidator.DATATYPE_SHA256 + "\n").getBytes(StandardCharsets.UTF_8);
    }

    public void forEachRecord(Path manifest, Consumer<CdsRecordDto> consumer) throws IOException {
        Path parent = manifest.toAbsolutePath().getParent();
        try (InputStream input = new BufferedInputStream(
                Files.newInputStream(manifest, LinkOption.NOFOLLOW_LINKS), 65536)) {
            byte[] line;
            while ((line = readMetadataLine(input)) != null) {
                checkControl();
                JsonNode node = JSON.readTree(line);
                Path fields = member(parent, node.path("fieldsFile").asText());
                Path values = member(parent, node.path("valuesFile").asText());
                consumer.accept(new CdsRecordDto(node.path("section").asText(), node.path("ordinal").asLong(),
                        node.path("sourceXPath").asText(), fields, node.path("fieldsOffset").asLong(),
                        node.path("fieldsLength").asLong(), values, node.path("sha256").asText(),
                        node.path("fieldCount").asLong(), node.hasNonNull("corrections")
                                ? JSON.treeToValue(node.path("corrections"), CdsCorrectionSetDto.class) : null));
            }
        }
    }

    public static String unindexed(String path) {
        return ORDINAL.matcher(path).replaceAll("");
    }

    private String readLogicalText(CdsRecordDto record, CdsFieldDto field, int maximum, String whitespace)
            throws IOException {
        if (maximum < 0 || field.kind().equals("BINARY") || !Set.of("preserve", "replace", "collapse").contains(whitespace)) {
            throw new IllegalArgumentException("Invalid textual CDS field request");
        }
        InputStream logical=logicalValue(record, field);
        try (InputStream input = logical;
                Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT))) {
            StringBuilder result = new StringBuilder(Math.min(maximum, 4096));
            boolean pending = false;
            long count = 0;
            char[] chars = new char[4096];
            int n;
            while ((n = reader.read(chars)) >= 0) {
                checkControl();
                for (int i = 0; i < n; i++) {
                    char c = chars[i];
                    boolean space = c == ' ' || c == '\t' || c == '\n' || c == '\r';
                    if (whitespace.equals("collapse") && space) { pending = result.length() > 0; continue; }
                    if (pending) { result.append(' '); count++; pending = false; }
                    if (!Character.isLowSurrogate(c)) count++;
                    if (count > maximum) throw failure("CDS_TARGET_TEXT_CAPACITY_EXCEEDED");
                    result.append(whitespace.equals("replace") && space ? ' ' : c);
                }
            }
            String value=result.toString();
            if (value.isEmpty() && !field.schemaDefault().isEmpty()) {
                value=field.schemaDefault();
                if (value.codePointCount(0, value.length())>maximum) throw failure("CDS_TARGET_TEXT_CAPACITY_EXCEEDED");
            }
            return value;
        }
    }

    private InputStream logicalValue(CdsRecordDto record, CdsFieldDto field) throws IOException {
        JsonNode match=findCorrection(record, field);
        if (match==null) return openValue(record, field);
        Path manifest=record.corrections().manifestFile();
        long length=match.path("replacementBytes").asLong(-1);
        String hash=match.path("replacementSha256").asText();
        if (length<0 || length>262144 || !hash.matches("[0-9a-f]{64}")) throw failure("CDS_CORRECTION_REPLACEMENT_INVALID");
        String member=match.path("replacementFile").asText();
        if (!member.matches("[A-Za-z0-9_-]{1,128}\\.[A-Za-z0-9]{1,12}")) throw failure("CDS_CORRECTION_REPLACEMENT_INVALID");
        Path replacement=member(manifest.getParent(), member);
        if (Files.size(replacement)!=length) throw failure("CDS_CORRECTION_REPLACEMENT_CHANGED");
        CdsRecordDto value=new CdsRecordDto(record.section(), record.ordinal(), record.sourceXPath(), record.fieldsFile(),
                record.fieldsOffset(), record.fieldsLength(), replacement, record.sha256(), record.fieldCount());
        return openValue(value, new CdsFieldDto(field.path(), field.namespace(), field.kind(), 0, length, hash, field.schemaType(), field.whitespace()));
    }

    private JsonNode findCorrection(CdsRecordDto record, CdsFieldDto field) throws IOException {
        if (record.corrections()==null) return null;
        Path parent=record.fieldsFile().toAbsolutePath().getParent();
        Path manifest=CdsPaths.within(record.corrections().manifestFile(), parent);
        List<JsonNode> entries=correctionEntries(record.corrections(), manifest);
        String full=record.sourceXPath()+(field.path().isEmpty() ? "" : "/"+field.path());
        JsonNode match=null;
        for (JsonNode entry:entries) {
            if (!entry.path("sourceXPath").asText().equals(full)) continue;
            if (match!=null) throw failure("CDS_CORRECTION_TARGET_AMBIGUOUS");
            verifyCorrection(entry, field);
            match=entry;
        }
        return match;
    }

    List<JsonNode> correctionEntries(io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsCorrectionSetDto reference, Path manifest) throws IOException {
        CorrectionCache cached=correctionCache.get();
        if (cached!=null && cached.path().equals(manifest) && cached.digest().equals(reference.sha256())
                && cached.xmlDigest().equals(reference.acceptedXmlSha256())) {
            if (cached.physicallyVerified()) return cached.entries();
            MessageDigest hash=CdsPaths.sha256(); long count=0;
            try (InputStream input=Files.newInputStream(manifest, LinkOption.NOFOLLOW_LINKS)) {
                byte[] buffer=new byte[8192]; int n;
                while ((n=input.read(buffer))>=0) {
                    count+=n; if (count>(1<<20)) throw failure("CDS_CORRECTION_MANIFEST_CAPACITY_EXCEEDED"); hash.update(buffer, 0, n);
                }
            }
            if (!reference.sha256().equals(CdsPaths.hex(hash.digest()))) throw failure("CDS_CORRECTION_MANIFEST_HASH_MISMATCH");
            return cached.entries();
        }
        byte[] bytes;
        try (InputStream input=Files.newInputStream(manifest, LinkOption.NOFOLLOW_LINKS)) {
            bytes=input.readNBytes(1<<20);
            if (input.read()!=-1) throw failure("CDS_CORRECTION_MANIFEST_CAPACITY_EXCEEDED");
        }
        if (!reference.sha256().equals(CdsPaths.hex(CdsPaths.sha256().digest(bytes)))) throw failure("CDS_CORRECTION_MANIFEST_HASH_MISMATCH");
        JsonNode document=JSON.readTree(bytes), entries=document.path("corrections");
        if (document.path("version").asInt()!=1 || !reference.acceptedXmlSha256().equals(document.path("acceptedXmlSha256").asText())
                || !entries.isArray() || entries.size()>128) throw failure("CDS_CORRECTION_MANIFEST_INVALID");
        List<JsonNode> list=new ArrayList<>(entries.size()); entries.forEach(list::add);
        List<JsonNode> immutable=List.copyOf(list);
        correctionCache.set(new CorrectionCache(manifest, reference.sha256(), reference.acceptedXmlSha256(), immutable, false));
        return immutable;
    }

    static void verifyCorrection(JsonNode entry, CdsFieldDto field) throws IOException {
        if (!entry.path("namespace").asText().equals(field.namespace()) || !entry.path("kind").asText().equals(field.kind())
                || field.kind().equals("BINARY") || !entry.path("schemaType").asText().equals(field.schemaType())
                || !entry.path("whitespace").asText().equals(field.whitespace())
                || !entry.path("originalSha256").asText().equals(field.sha256()) || entry.path("originalLength").asLong(-1)!=field.length()) {
            throw failure("CDS_CORRECTION_SOURCE_IDENTITY_MISMATCH");
        }
    }

    static String replacementLexical(JsonNode entry, io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsCorrectionSetDto reference) throws IOException {
        String name=entry.path("replacementFile").asText(), hash=entry.path("replacementSha256").asText();
        long length=entry.path("replacementBytes").asLong(-1);
        if (!name.matches("[A-Za-z0-9_-]{1,128}\\.[A-Za-z0-9]{1,12}") || length<0 || length>262144 || !hash.matches("[0-9a-f]{64}")) {
            throw failure("CDS_CORRECTION_REPLACEMENT_INVALID");
        }
        byte[] bytes;
        try (InputStream input=Files.newInputStream(member(reference.manifestFile().getParent(), name), LinkOption.NOFOLLOW_LINKS)) {
            bytes=input.readNBytes((int) length);
            if (bytes.length!=length || input.read()!=-1 || !hash.equals(CdsPaths.hex(CdsPaths.sha256().digest(bytes)))) throw failure("CDS_CORRECTION_REPLACEMENT_CHANGED");
        }
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
    }

    private static Path member(Path parent, String name) throws IOException {
        if (name.isEmpty() || name.contains("/") || name.contains("\\") || name.equals("..")) {
            throw failure("CDS_SPOOL_MEMBER_INVALID");
        }
        return PathValidationUtils.validatePrivateImportPath(parent.resolve(name).toFile(), parent.toFile()).toPath();
    }

    private static InputStream openSlice(Path path, long offset, long length) throws IOException {
        if (offset < 0 || length < 0) throw failure("CDS_SPOOL_RANGE_INVALID");
        FileChannel channel = FileChannel.open(path, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
        try {
            if (offset > channel.size() || length > channel.size() - offset) {
                throw failure("CDS_SPOOL_RANGE_INVALID");
            }
            channel.position(offset);
            return new InputStream() {
                private long remaining = length;
                @Override public int read() throws IOException {
                    byte[] one = new byte[1];
                    return read(one, 0, 1) < 0 ? -1 : one[0] & 255;
                }
                @Override public int read(byte[] bytes, int start, int count) throws IOException {
                    if (count == 0) return 0;
                    if (remaining == 0) return -1;
                    int n = channel.read(ByteBuffer.wrap(bytes, start, (int) Math.min(count, remaining)));
                    if (n < 0) throw failure("CDS_SPOOL_TRUNCATED");
                    remaining -= n;
                    return n;
                }
                @Override public void close() throws IOException { channel.close(); }
            };
        } catch (IOException | RuntimeException failed) {
            channel.close();
            throw failed;
        }
    }

    static byte[] readMetadataLine(InputStream input) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream(256);
        int value;
        while ((value = input.read()) != -1) {
            if (value == '\n') return line.toByteArray();
            if (line.size() >= MAX_METADATA_LINE) throw failure("CDS_SPOOL_METADATA_LIMIT");
            line.write(value);
        }
        return line.size() == 0 ? null : line.toByteArray();
    }

    private static CdsInputException failure(String code) { return new CdsInputException(code, "", ""); }
}
