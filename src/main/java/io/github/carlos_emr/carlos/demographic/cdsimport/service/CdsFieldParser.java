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
import java.nio.file.attribute.PosixFilePermissions;
import java.io.BufferedOutputStream;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

/** Bounded access to persisted source fields; no record-sized collection or XML graph is built. */
@Service
public class CdsFieldParser {
    private record CorrectionCache(Path path,String digest,String xmlDigest,List<JsonNode> entries) { }
    /** One bounded immutable manifest, never a cache that grows with patients or imports. */
    private volatile CorrectionCache correctionCache;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_METADATA_LINE = 65536;

    public void forEachField(CdsRecordDto record, Consumer<CdsFieldDto> consumer) throws IOException {
        try (InputStream slice = new BufferedInputStream(
                openSlice(record.fieldsFile(), record.fieldsOffset(), record.fieldsLength()), 65536)) {
            byte[] line;
            long count = 0;
            MessageDigest digest = CdsPaths.sha256();
            while ((line = readMetadataLine(slice)) != null) {
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

    private String firstValue(CdsRecordDto record, String path, String namespace, int maxCharacters,
            String explicitWhitespace) throws IOException {
        if (path == null || maxCharacters < 0) throw new IllegalArgumentException("Invalid field request");
        CdsFieldDto[] first = new CdsFieldDto[1];
        boolean indexed = path.indexOf('[') >= 0;
        forEachField(record, field -> {
            if (first[0] != null || field.kind().equals("BINARY")) return;
            boolean knownNamespace = field.namespace().equals("cds") || field.namespace().equals("cds_dt")
                    || field.kind().equals("ATTRIBUTE") && field.namespace().isEmpty();
            if (namespace == null ? !knownNamespace : !namespace.equals(field.namespace())) return;
            String candidate = indexed ? field.path() : unindexed(field.path());
            if (candidate.equals(path)) first[0] = field;
        });
        return first[0] == null ? null : readLogicalText(record, first[0], maxCharacters,
                explicitWhitespace == null ? first[0].whitespace() : explicitWhitespace);
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
                int value = in.read();
                if (value < 0) verify(); else digest.update((byte) value);
                return value;
            }
            @Override public int read(byte[] bytes, int offset, int length) throws IOException {
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
                offset, length, source.valuesFile(), digest, count,source.corrections());
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
                                metadata, start, position - start, record.valuesFile(), CdsPaths.hex(digest.digest()), count,record.corrections()));
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
                                original.schemaType(), original.whitespace());
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
                + field.whitespace() + "\n").getBytes(StandardCharsets.UTF_8);
    }

    public void forEachRecord(Path manifest, Consumer<CdsRecordDto> consumer) throws IOException {
        Path parent = manifest.toAbsolutePath().getParent();
        try (InputStream input = new BufferedInputStream(
                Files.newInputStream(manifest, LinkOption.NOFOLLOW_LINKS), 65536)) {
            byte[] line;
            while ((line = readMetadataLine(input)) != null) {
                JsonNode node = JSON.readTree(line);
                Path fields = member(parent, node.path("fieldsFile").asText());
                Path values = member(parent, node.path("valuesFile").asText());
                consumer.accept(new CdsRecordDto(node.path("section").asText(), node.path("ordinal").asLong(),
                        node.path("sourceXPath").asText(), fields, node.path("fieldsOffset").asLong(),
                        node.path("fieldsLength").asLong(), values, node.path("sha256").asText(),
                        node.path("fieldCount").asLong()));
            }
        }
    }

    public static String unindexed(String path) {
        return path.replaceAll("\\[[0-9]+\\]", "");
    }

    private String readLogicalText(CdsRecordDto record, CdsFieldDto field, int maximum, String whitespace)
            throws IOException {
        if (maximum < 0 || field.kind().equals("BINARY") || !Set.of("preserve", "replace", "collapse").contains(whitespace)) {
            throw new IllegalArgumentException("Invalid textual CDS field request");
        }
        InputStream logical=logicalValue(record,field);
        try (InputStream input = logical;
                Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT))) {
            StringBuilder result = new StringBuilder(Math.min(maximum, 4096));
            boolean pending = false;
            long count = 0;
            char[] chars = new char[4096];
            int n;
            while ((n = reader.read(chars)) >= 0) {
                if (Thread.currentThread().isInterrupted()) throw failure("CDS_INPUT_CANCELLED");
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
            return result.toString();
        }
    }

    private InputStream logicalValue(CdsRecordDto record,CdsFieldDto field) throws IOException {
        if(record.corrections()==null) return openValue(record,field);
        Path parent=record.fieldsFile().toAbsolutePath().getParent();
        Path manifest=CdsPaths.within(record.corrections().manifestFile(),parent);
        List<JsonNode> entries=correctionEntries(record,manifest);
        String full=record.sourceXPath()+(field.path().isEmpty() ? "" : "/"+field.path());
        JsonNode match=null;
        for(JsonNode entry:entries) {
            if(!entry.path("sourceXPath").asText().equals(full)) continue;
            if(match!=null) throw failure("CDS_CORRECTION_TARGET_AMBIGUOUS");
            if(!entry.path("namespace").asText().equals(field.namespace()) || !entry.path("kind").asText().equals(field.kind())
                    || field.kind().equals("BINARY") || !entry.path("schemaType").asText().equals(field.schemaType())
                    || !entry.path("whitespace").asText().equals(field.whitespace())
                    || !entry.path("originalSha256").asText().equals(field.sha256()) || entry.path("originalLength").asLong(-1)!=field.length()) {
                throw failure("CDS_CORRECTION_SOURCE_IDENTITY_MISMATCH");
            }
            match=entry;
        }
        if(match==null) return openValue(record,field);
        long length=match.path("replacementBytes").asLong(-1);
        String hash=match.path("replacementSha256").asText();
        if(length<0 || length>262144 || !hash.matches("[0-9a-f]{64}")) throw failure("CDS_CORRECTION_REPLACEMENT_INVALID");
        String member=match.path("replacementFile").asText();
        if(!member.matches("[A-Za-z0-9_-]{1,128}\\.[A-Za-z0-9]{1,12}")) throw failure("CDS_CORRECTION_REPLACEMENT_INVALID");
        Path replacement=member(manifest.getParent(),member);
        if(Files.size(replacement)!=length) throw failure("CDS_CORRECTION_REPLACEMENT_CHANGED");
        CdsRecordDto value=new CdsRecordDto(record.section(),record.ordinal(),record.sourceXPath(),record.fieldsFile(),
                record.fieldsOffset(),record.fieldsLength(),replacement,record.sha256(),record.fieldCount());
        return openValue(value,new CdsFieldDto(field.path(),field.namespace(),field.kind(),0,length,hash,field.schemaType(),field.whitespace()));
    }

    private List<JsonNode> correctionEntries(CdsRecordDto record,Path manifest) throws IOException {
        var reference=record.corrections(); CorrectionCache cached=correctionCache;
        if(cached!=null && cached.path().equals(manifest) && cached.digest().equals(reference.sha256())
                && cached.xmlDigest().equals(reference.acceptedXmlSha256())) {
            MessageDigest hash=CdsPaths.sha256(); long count=0;
            try(InputStream input=Files.newInputStream(manifest,LinkOption.NOFOLLOW_LINKS)) {
                byte[] buffer=new byte[8192]; int n;
                while((n=input.read(buffer))>=0) {
                    count+=n; if(count>(1<<20)) throw failure("CDS_CORRECTION_MANIFEST_CAPACITY_EXCEEDED"); hash.update(buffer,0,n);
                }
            }
            if(!reference.sha256().equals(CdsPaths.hex(hash.digest()))) throw failure("CDS_CORRECTION_MANIFEST_HASH_MISMATCH");
            return cached.entries();
        }
        byte[] bytes;
        try(InputStream input=Files.newInputStream(manifest,LinkOption.NOFOLLOW_LINKS)) {
            bytes=input.readNBytes(1<<20);
            if(input.read()!=-1) throw failure("CDS_CORRECTION_MANIFEST_CAPACITY_EXCEEDED");
        }
        if(!reference.sha256().equals(CdsPaths.hex(CdsPaths.sha256().digest(bytes)))) throw failure("CDS_CORRECTION_MANIFEST_HASH_MISMATCH");
        JsonNode document=JSON.readTree(bytes),entries=document.path("corrections");
        if(document.path("version").asInt()!=1 || !reference.acceptedXmlSha256().equals(document.path("acceptedXmlSha256").asText())
                || !entries.isArray() || entries.size()>128) throw failure("CDS_CORRECTION_MANIFEST_INVALID");
        List<JsonNode> list=new ArrayList<>(entries.size()); entries.forEach(list::add);
        List<JsonNode> immutable=List.copyOf(list);
        correctionCache=new CorrectionCache(manifest,reference.sha256(),reference.acceptedXmlSha256(),immutable);
        return immutable;
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
