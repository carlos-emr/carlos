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
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsSnapshotDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsSnapshotFileDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsZipDirectory;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.FilterInputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.CRC32;
import org.springframework.stereotype.Service;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;
import java.io.Reader;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsXmlText;

/** Copies an authorized source to durable, opaque private storage with a disk-backed name inventory. */
@Service
public class CdsSnapshotService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int BUFFER = 65536;

    /**
     * Creates a complete immutable snapshot. Failure leaves an unpublished .pending directory for
     * protected diagnosis/owned cleanup; it never changes the original or reports acceptance.
     */
    public CdsSnapshotDto snapshot(Path input, Path allowedSourceRoot, Path durableRoot,
            String snapshotId, CdsInputLimitsDto limits, BooleanSupplier cancelled) throws IOException {
        try { return snapshotInternal(input, allowedSourceRoot, durableRoot, snapshotId, limits, cancelled); }
        catch (CdsInputException safe) { throw safe; }
        catch (IOException failed) {
            String detail = failed.getMessage();
            if (detail != null && detail.length() > 4096) detail = detail.substring(0, 4096);
            throw new CdsInputException("CDS_SOURCE_SNAPSHOT_FAILED", "", detail);
        } catch (IllegalArgumentException malformedArchive) {
            throw new CdsInputException("CDS_SOURCE_INPUT_INVALID", "", "");
        }
    }

    private CdsSnapshotDto snapshotInternal(Path input, Path allowedSourceRoot, Path durableRoot,
            String snapshotId, CdsInputLimitsDto limits, BooleanSupplier cancelled) throws IOException {
        if (snapshotId == null || !snapshotId.matches("[A-Za-z0-9_-]{1,80}")) {
            throw new IllegalArgumentException("Invalid opaque snapshot ID");
        }
        Path source = CdsPaths.within(input, allowedSourceRoot);
        Path root = durableRoot.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)
                || Files.isSymbolicLink(input)) throw CdsPaths.failure("CDS_SOURCE_PATH_INVALID");
        if (root.startsWith(source)) throw CdsPaths.failure("CDS_SOURCE_CONTAINS_STAGING");
        Path pending = CdsPaths.privateDirectory(root.resolve(snapshotId + ".pending"), root);
        Path members = CdsPaths.privateDirectory(pending.resolve("members"), pending);
        Path index = CdsPaths.privateDirectory(pending.resolve("names"), pending);
        Path manifest = pending.resolve("files.jsonl");
        Inventory inventory = new Inventory(pending, members, index, manifest, limits, cancelled);
        try (inventory) {
            if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
                copyDirectory(source, inventory);
            } else if (source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                Path archive;
                try (InputStream raw = openSource(source, allowedSourceRoot)) { archive = inventory.copyArchive(raw); }
                long zipEntries = 0;
                try (CountingSource raw = new CountingSource(Files.newInputStream(archive, LinkOption.NOFOLLOW_LINKS), limits.maxSourceBytes(), inventory::check);
                        ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw, BUFFER), Charset.forName("IBM437"))) {
                    inventory.archiveInput = raw;
                    ZipEntry entry;
                    while ((entry = zip.getNextEntry()) != null) {
                        if (++zipEntries > limits.maxEntries()) throw CdsPaths.failure("CDS_INPUT_ENTRY_LIMIT");
                        inventory.check();
                        String relative = normalizedRelative(entry.getName(), limits);
                        if (entry.isDirectory()) {
                            // A trailing slash is only a name convention. Never let closeEntry
                            // inflate an unaccounted body, including an unknown descriptor size.
                            if (entry.getSize() > 0 || zip.read() != -1) {
                                throw CdsPaths.failure("CDS_ZIP_DIRECTORY_PAYLOAD");
                            }
                            inventory.reserveDirectory(relative);
                        } else {
                            inventory.copy(relative, zip, false);
                        }
                        zip.closeEntry();
                    }
                }
                CdsZipDirectory.validate(archive, index, pending, limits.maxEntries(), zipEntries, inventory::check);
            } else {
                boolean declaredXml=source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xml");
                try (InputStream bytes=openSource(source, allowedSourceRoot)) {
                    inventory.copy(source.getFileName().toString(), bytes, declaredXml);
                }
                if (inventory.xmls==0) throw CdsPaths.failure("CDS_SOURCE_SHAPE_UNSUPPORTED");
                copyReferencedFiles(source, inventory);
            }
            if (inventory.xmls == 0) throw CdsPaths.failure("CDS_NO_PATIENT_XML");
            inventory.finish();
        }
        Path published = root.resolve(snapshotId);
        inventory.finalCheck();
        try {
            Files.move(pending, published, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            // Acceptance is not durable if this deployment cannot publish its storage atomically.
            throw CdsPaths.failure("CDS_ATOMIC_SNAPSHOT_PUBLICATION_UNSUPPORTED");
        }
        CdsPaths.forceDirectory(root);
        return new CdsSnapshotDto(published, published.resolve("files.jsonl"), inventory.digest,
                inventory.files, inventory.xmls, inventory.total);
    }

    /** Loads and revalidates an accepted snapshot on resume, rather than trusting ephemeral state. */
    public CdsSnapshotDto load(Path published) throws IOException { return load(published, true); }

    public CdsSnapshotDto load(Path published, boolean verifyContent) throws IOException {
        return load(published, verifyContent, ()-> false);
    }

    public CdsSnapshotDto load(Path published, boolean verifyContent, BooleanSupplier cancelled) throws IOException {
        long[] next={0};
        BooleanSupplier checked=()-> {
            if (Thread.currentThread().isInterrupted()) return true;
            long now=System.nanoTime();
            if (now>=next[0]) {next[0]=now+250_000_000L; return cancelled.getAsBoolean(); }
            return false;
        };
        if (Files.isSymbolicLink(published) || !Files.isDirectory(published, LinkOption.NOFOLLOW_LINKS)
                || published.getFileName().toString().endsWith(".pending")) throw CdsPaths.failure("CDS_SNAPSHOT_NOT_PUBLISHED");
        Path root = published.toRealPath(LinkOption.NOFOLLOW_LINKS);
        JsonNode seal;
        try (InputStream input = Files.newInputStream(root.resolve("snapshot.seal"), LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(65536);
            if (input.read() != -1) throw CdsPaths.failure("CDS_SNAPSHOT_SEAL_INVALID");
            seal = JSON.readTree(bytes);
        }
        if (seal == null || seal.path("version").asInt() != 2) throw CdsPaths.failure("CDS_SNAPSHOT_SEAL_UNSUPPORTED");
        String expectedManifest = seal.path("manifestSha256").asText();
        MessageDigest digest = CdsPaths.sha256(); long count = 0, xmls = 0, size = 0, manifestOffset = 0;
        CdsSnapshotDto provisional = new CdsSnapshotDto(root, root.resolve("files.jsonl"), "", 0, 0, 0);
        try (InputStream input = new BufferedInputStream(Files.newInputStream(root.resolve("files.jsonl"), LinkOption.NOFOLLOW_LINKS), BUFFER)) {
            byte[] row;
            while ((row = CdsFieldParser.readMetadataLine(input)) != null) {
                if (checked.getAsBoolean()) throw CdsPaths.failure("CDS_INPUT_CANCELLED");
                digest.update(row); digest.update((byte) '\n');
                JsonNode node = JSON.readTree(row);
                CdsSnapshotFileDto member = file(provisional, node);
                if (member.length() < 0 || !member.sha256().matches("[0-9a-f]{64}")
                        || Files.size(member.path()) != member.length()) throw CdsPaths.failure("CDS_SNAPSHOT_MEMBER_CHANGED");
                if (verifyContent && !member.sha256().equals(hashFile(member.path(), checked))) throw CdsPaths.failure("CDS_SNAPSHOT_MEMBER_CHANGED");
                if (verifyContent) {
                    JsonNode marker = readNameMarker(provisional, member.relativeName());
                    requireIndexBinding(marker, node, manifestOffset, row.length);
                }
                manifestOffset = Math.addExact(manifestOffset, row.length + 1L);
                count++; if (member.patientXml()) xmls++;
                size = Math.addExact(size, member.length());
            }
        }
        String manifestHash = CdsPaths.hex(digest.digest());
        String archiveHash = seal.path("archiveSha256").asText();
        if (!archiveHash.isEmpty()) {
            Path archive = root.resolve("source-archive.bin");
            if (!Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS)
                    || verifyContent && !archiveHash.equals(hashFile(archive, checked))) throw CdsPaths.failure("CDS_SNAPSHOT_ARCHIVE_CHANGED");
        }
        if (!manifestHash.equals(expectedManifest) || count != seal.path("files").asLong(-1)
                || xmls != seal.path("patientXmlFiles").asLong(-1) || size != seal.path("totalBytes").asLong(-1)) {
            throw CdsPaths.failure("CDS_SNAPSHOT_SEAL_MISMATCH");
        }
        return new CdsSnapshotDto(root, root.resolve("files.jsonl"), fingerprint(manifestHash, archiveHash), count, xmls, size);
    }

    public void forEachXml(CdsSnapshotDto snapshot, Consumer<CdsSnapshotFileDto> consumer)
            throws IOException {
        forEachFile(snapshot, member -> { if (member.patientXml()) consumer.accept(member); });
    }

    /** Includes accompanying source audit/readme/event files and XML report payloads. */
    public void forEachFile(CdsSnapshotDto snapshot, Consumer<CdsSnapshotFileDto> consumer)
            throws IOException {
        try (InputStream input = new BufferedInputStream(Files.newInputStream(
                CdsPaths.within(snapshot.filesManifestFile(), snapshot.root()), LinkOption.NOFOLLOW_LINKS), BUFFER)) {
            byte[] line;
            while ((line = CdsFieldParser.readMetadataLine(input)) != null) {
                JsonNode node = JSON.readTree(line);
                consumer.accept(file(snapshot, node));
            }
        }
    }

    /** Resolves only a supplied snapshot member relative to its patient's XML folder. */
    public CdsSnapshotFileDto resolveReference(CdsSnapshotDto snapshot, String relativeXml,
            String relativeReference) throws IOException {
        String reference = normalizedRelative(relativeReference == null ? null : relativeReference.replace('\\', '/'), CdsInputLimitsDto.defaults());
        String xml = normalizedRelative(relativeXml, CdsInputLimitsDto.defaults());
        int separator = xml.lastIndexOf('/');
        String relative = (separator < 0 ? "" : xml.substring(0, separator + 1)) + reference;
        try {
            JsonNode marker = readNameMarker(snapshot, relative);
            long offset = marker.path("manifestOffset").asLong(-1);
            int length = marker.path("manifestLength").asInt(-1);
            JsonNode row = readManifestRow(snapshot, offset, length);
            requireIndexBinding(marker, row, offset, length);
            if (!relative.equals(row.path("relativeName").asText())) {
                throw CdsPaths.failure("CDS_SNAPSHOT_NAME_INDEX_INVALID");
            }
            // The derived name index is a locator, never clinical evidence. Return only
            // values from the manifest whose digest was verified when the snapshot loaded.
            return file(snapshot, row);
        } catch (java.nio.file.NoSuchFileException missing) {
            throw CdsPaths.failure("CDS_ATTACHMENT_NOT_SUPPLIED");
        }
    }

    private static JsonNode readNameMarker(CdsSnapshotDto snapshot, String relative) throws IOException {
        Path index = snapshot.root().resolve("names").resolve(nameKey(relative));
        try (InputStream input = Files.newInputStream(CdsPaths.within(index, snapshot.root()), LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = CdsFieldParser.readMetadataLine(input);
            if (bytes == null || input.read() != -1) throw CdsPaths.failure("CDS_SNAPSHOT_NAME_INDEX_INVALID");
            JsonNode marker = JSON.readTree(bytes);
            if (marker == null || !marker.isObject() || !relative.equals(marker.path("relativeName").asText())
                    || marker.path("directory").asBoolean()) throw CdsPaths.failure("CDS_SNAPSHOT_NAME_INDEX_INVALID");
            return marker;
        }
    }

    private static void requireIndexBinding(JsonNode marker, JsonNode row, long offset, int length) throws IOException {
        if (row == null || !row.isObject() || !marker.path("manifestOffset").isIntegralNumber()
                || !marker.path("manifestOffset").canConvertToLong()
                || !marker.path("manifestLength").isIntegralNumber() || !marker.path("manifestLength").canConvertToInt()
                || marker.path("manifestOffset").longValue() != offset
                || marker.path("manifestLength").intValue() != length) {
            throw CdsPaths.failure("CDS_SNAPSHOT_NAME_INDEX_INVALID");
        }
        for (String field : new String[] {"relativeName", "member", "sha256", "length", "patientXml"}) {
            if (row.get(field) == null || !row.get(field).equals(marker.get(field))) {
                throw CdsPaths.failure("CDS_SNAPSHOT_NAME_INDEX_INVALID");
            }
        }
    }

    private static JsonNode readManifestRow(CdsSnapshotDto snapshot, long offset, int length) throws IOException {
        if (offset < 0 || length < 1 || length > 65536) throw CdsPaths.failure("CDS_SNAPSHOT_NAME_INDEX_INVALID");
        Path manifest = CdsPaths.within(snapshot.filesManifestFile(), snapshot.root());
        try (SeekableByteChannel channel = Files.newByteChannel(manifest, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            if (offset > channel.size() || length >= channel.size() - offset) throw CdsPaths.failure("CDS_SNAPSHOT_NAME_INDEX_INVALID");
            if (offset != 0) {
                channel.position(offset - 1);
                ByteBuffer preceding = ByteBuffer.allocate(1);
                if (channel.read(preceding) != 1 || preceding.array()[0] != '\n') throw CdsPaths.failure("CDS_SNAPSHOT_NAME_INDEX_INVALID");
            }
            channel.position(offset);
            ByteBuffer bytes = ByteBuffer.allocate(length + 1);
            while (bytes.hasRemaining()) {
                if (Thread.currentThread().isInterrupted()) throw CdsPaths.failure("CDS_INPUT_CANCELLED");
                if (channel.read(bytes) < 1) throw CdsPaths.failure("CDS_SNAPSHOT_NAME_INDEX_INVALID");
            }
            if (bytes.array()[length] != '\n') throw CdsPaths.failure("CDS_SNAPSHOT_NAME_INDEX_INVALID");
            for (int i = 0; i < length; i++) if (bytes.array()[i] == '\n') throw CdsPaths.failure("CDS_SNAPSHOT_NAME_INDEX_INVALID");
            return JSON.readTree(bytes.array(), 0, length);
        }
    }

    private static CdsSnapshotFileDto file(CdsSnapshotDto snapshot, JsonNode node) throws IOException {
        String member = node.path("member").asText();
        if (!member.matches("[0-9]{12}\\.bin")) throw CdsPaths.failure("CDS_SNAPSHOT_MEMBER_INVALID");
        Path path = CdsPaths.within(snapshot.root().resolve("members").resolve(member), snapshot.root());
        return new CdsSnapshotFileDto(node.path("relativeName").asText(), path,
                node.path("sha256").asText(), node.path("length").asLong(), node.path("patientXml").asBoolean());
    }

    private static void copyDirectory(Path source, Inventory inventory) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs)
                    throws IOException {
                inventory.check();
                if (!directory.equals(source)) {
                    inventory.reserveDirectory(source.relativize(directory).toString().replace('\\', '/'));
                }
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                if (!attrs.isRegularFile() || attrs.isSymbolicLink()) throw CdsPaths.failure("CDS_UNSAFE_SOURCE_MEMBER");
                String relative = normalizedRelative(source.relativize(path).toString().replace('\\', '/'), inventory.limits);
                try (InputStream input = openSource(path, source)) { inventory.copy(relative, input, false); }
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFileFailed(Path path, IOException failed) throws IOException {
                throw CdsPaths.failure("CDS_SOURCE_READ_FAILED");
            }
        });
    }

    private static InputStream openSource(Path path, Path root) throws IOException {
        Path base = root.toRealPath(LinkOption.NOFOLLOW_LINKS);
        Path lexical = path.toAbsolutePath().normalize();
        CdsPaths.within(lexical, base);
        Path relative = base.relativize(lexical);
        Path filesystemRoot = base.getRoot();
        DirectoryStream<Path> stream = Files.newDirectoryStream(filesystemRoot);
        if (!(stream instanceof SecureDirectoryStream<Path> secure)) {
            stream.close();
            throw CdsPaths.failure("CDS_SECURE_SOURCE_OPEN_UNSUPPORTED");
        }
        SecureDirectoryStream<Path> current = secure;
        try {
            for (Path component : filesystemRoot.relativize(base)) {
                SecureDirectoryStream<Path> next = current.newDirectoryStream(component, LinkOption.NOFOLLOW_LINKS);
                if (current != secure) current.close();
                current = next;
            }
            for (int i = 0; i < relative.getNameCount() - 1; i++) {
                SecureDirectoryStream<Path> next = current.newDirectoryStream(relative.getName(i), LinkOption.NOFOLLOW_LINKS);
                if (current != secure) current.close();
                current = next;
            }
            SeekableByteChannel channel = current.newByteChannel(relative.getFileName(),
                    Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
            return Channels.newInputStream(channel);
        } finally {
            if (current != secure) current.close();
            secure.close();
        }
    }

    private static String normalizedRelative(String raw, CdsInputLimitsDto limits) throws IOException {
        if (raw == null || raw.isBlank() || raw.length() > limits.maxPathCharacters()
                || raw.startsWith("/") || raw.startsWith("\\") || raw.matches("^[A-Za-z]:.*")
                || raw.indexOf('\0') >= 0 || raw.indexOf('\\') >= 0) {
            throw CdsPaths.failure("CDS_SOURCE_MEMBER_PATH_INVALID");
        }
        String name = raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
        for (String part : name.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw CdsPaths.failure("CDS_SOURCE_MEMBER_PATH_INVALID");
            }
        }
        return name;
    }

    private static String nameKey(String name) {
        String normalized = Normalizer.normalize(name, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        return CdsPaths.hex(CdsPaths.sha256().digest(normalized.getBytes(StandardCharsets.UTF_8)));
    }

    private static String hashFile(Path path) throws IOException {
        return hashFile(path, ()-> false);
    }

    private static String hashFile(Path path, BooleanSupplier cancelled) throws IOException {
        MessageDigest digest = CdsPaths.sha256();
        try (InputStream input = new BufferedInputStream(Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS), BUFFER)) {
            byte[] buffer = new byte[BUFFER]; int n;
            while ((n = input.read(buffer)) >= 0) {
                if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) throw CdsPaths.failure("CDS_INPUT_CANCELLED");
                if (n > 0) digest.update(buffer, 0, n);
            }
        }
        return CdsPaths.hex(digest.digest());
    }

    private static String fingerprint(String manifestHash, String archiveHash) {
        return archiveHash.isEmpty() ? manifestHash : CdsPaths.hex(CdsPaths.sha256().digest(
                (manifestHash + "\n" + archiveHash).getBytes(StandardCharsets.US_ASCII)));
    }

    private static boolean patientCandidate(Path source, CdsInputLimitsDto limits) throws IOException {
        boolean[] candidate = {true};
        try (InputStream raw=Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS);
                InputStream input=new java.io.FilterInputStream(raw) {
                    long remaining=65536;
                    @Override public int read() throws IOException {if (remaining==0) return -1; int value=in.read(); if (value>=0)remaining--; return value; }
                    @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                        if (length==0)return 0; if (remaining==0)return -1;
                        int n=in.read(bytes, offset, (int) Math.min(length, remaining)); if (n>0)remaining-=n; return n;
                    }
                };
                Reader text = CdsXmlText.reader(input, limits.maxBoundedScalarCharacters())) {
            var reader = CdsXmlParser.xmlReader();
            reader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
            reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            reader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            reader.setContentHandler(new DefaultHandler() {
                @Override public void startElement(String uri, String local, String qualified, Attributes attrs) throws SAXException {
                    candidate[0] = "cds".equals(uri) || Set.of("OmdCds", "PatientRecord", "Demographics", "Reports").contains(local);
                    throw new RootFound();
                }
            });
            reader.setErrorHandler(new DefaultHandler() {
                @Override public void error(SAXParseException failure) throws SAXException { throw failure; }
                @Override public void fatalError(SAXParseException failure) throws SAXException { throw failure; }
            });
            try { reader.parse(new InputSource(text)); }
            catch (RootFound complete) { return candidate[0]; }
            catch (SAXException malformed) { return true; }
        } catch (javax.xml.parsers.ParserConfigurationException | SAXException configuration) {
            throw CdsPaths.failure("CDS_SECURE_XML_PARSER_UNAVAILABLE");
        } catch (IOException unresolved) {
            return true;
        }
        return true;
    }

    private static void copyReferencedFiles(Path originalXml, Inventory inventory) throws IOException {
        Path copied = inventory.members.resolve("000000000001.bin");
        try (InputStream input = Files.newInputStream(copied, LinkOption.NOFOLLOW_LINKS);
                Reader text = CdsXmlText.reader(input, inventory.limits.maxBoundedScalarCharacters())) {
            var reader = CdsXmlParser.xmlReader();
            reader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
            reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            reader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            reader.setContentHandler(new DefaultHandler() {
                private int depth;
                private int reportDepth;
                private StringBuilder reference;
                private final Set<String> names = new HashSet<>();
                private final Set<String> aliases = new HashSet<>();
                private final Set<String> namespaces = new HashSet<>();
                @Override public void startPrefixMapping(String prefix, String uri) throws SAXException {
                    if (prefix.length()>inventory.limits.maxXmlNameCharacters() || uri.length()>inventory.limits.maxNamespaceUriCharacters()
                            || aliases.add(prefix) && aliases.size()>inventory.limits.maxNamespaceAliases()
                            || namespaces.add(uri) && namespaces.size()>inventory.limits.maxNamespaceAliases()) {
                        throw new SAXException(CdsPaths.failure("CDS_XML_NAMESPACE_RESOURCE_LIMIT"));
                    }
                }
                private void name(String value) throws SAXException {
                    if (value.length()>inventory.limits.maxXmlNameCharacters() || names.add(value) && names.size()>inventory.limits.maxUniqueXmlNames()) {
                        throw new SAXException(CdsPaths.failure("CDS_XML_NAME_RESOURCE_LIMIT"));
                    }
                }
                @Override public void startElement(String uri, String local, String qualified, Attributes attrs) throws SAXException {
                    name(qualified);
                    if (attrs.getLength()>128) throw new SAXException(CdsPaths.failure("CDS_XML_ATTRIBUTE_RESOURCE_LIMIT"));
                    for (int i=0; i<attrs.getLength(); i++) name(attrs.getQName(i));
                    depth++;
                    if (depth > inventory.limits.maxXmlDepth()) throw new SAXException(CdsPaths.failure("CDS_XML_DEPTH_LIMIT"));
                    if (depth == 3 && uri.equals("cds") && local.equals("Reports")) reportDepth = depth;
                    if (reportDepth == 3 && depth == 4 && uri.equals("cds") && local.equals("FilePath")) reference = new StringBuilder();
                }
                @Override public void characters(char[] chars, int offset, int length) throws SAXException {
                    try {
                        inventory.check();
                        if (reference != null) {
                            if (reference.length() + (long) length > inventory.limits.maxPathCharacters()) throw CdsPaths.failure("CDS_SOURCE_MEMBER_PATH_INVALID");
                            reference.append(chars, offset, length);
                        }
                    } catch (IOException failed) { throw new SAXException(failed); }
                }
                @Override public void endElement(String uri, String local, String qualified) throws SAXException {
                    if (reference != null && depth == 4 && uri.equals("cds") && local.equals("FilePath")) {
                        try {
                            if (!reference.toString().isBlank()) {
                                String relative = normalizedRelative(reference.toString().replace('\\', '/'), inventory.limits);
                                Path key = inventory.index.resolve(nameKey(relative));
                                if (!Files.exists(key, LinkOption.NOFOLLOW_LINKS)) {
                                    Path source = CdsPaths.within(originalXml.getParent().resolve(relative), originalXml.getParent());
                                    try (InputStream bytes = openSource(source, originalXml.getParent())) { inventory.copy(relative, bytes, 2); }
                                    catch (java.nio.file.NoSuchFileException missing) { throw CdsPaths.failure("CDS_ATTACHMENT_NOT_SUPPLIED"); }
                                }
                            }
                            reference = null;
                        } catch (IOException failed) { throw new SAXException(failed); }
                    }
                    if (reportDepth == depth) reportDepth = 0;
                    depth--;
                }
            });
            reader.setErrorHandler(new DefaultHandler() {
                @Override public void error(SAXParseException failure) throws SAXException { throw failure; }
                @Override public void fatalError(SAXParseException failure) throws SAXException { throw failure; }
            });
            try { reader.parse(new InputSource(text)); }
            catch (SAXException failed) {
                if (failed.getException() instanceof CdsInputException known) throw known;
                if (failed.getException() instanceof IOException io) throw io;
                // Preserve malformed XML as a candidate: the full preflight parser records its
                // failure. This discovery pass cannot classify it as a successful patient.
            }
        } catch (javax.xml.parsers.ParserConfigurationException | SAXException configuration) {
            throw CdsPaths.failure("CDS_SECURE_XML_PARSER_UNAVAILABLE");
        }
    }

    private static final class RootFound extends SAXException { }

    private static final class Inventory implements AutoCloseable {
        private final Path pending;
        private final Path members;
        private final Path index;
        private final Path manifest;
        private final OutputStream output;
        private final CdsInputLimitsDto limits;
        private final BooleanSupplier cancelled;
        private final long started = System.nanoTime();
        private final MessageDigest manifestDigest = CdsPaths.sha256();
        private long entries;
        private long files;
        private long xmls;
        private long total;
        private long manifestOffset;
        private long archiveCopied;
        private long nextResourceCheck;
        private long nextDiskCheck;
        private String digest;
        private String archiveSha256 = "";
        private CountingSource archiveInput;

        Inventory(Path pending, Path members, Path index, Path manifest, CdsInputLimitsDto limits,
                BooleanSupplier cancelled) throws IOException {
            this.pending = pending; this.members = members; this.index = index; this.manifest = manifest;
            this.limits = limits; this.cancelled = cancelled;
            output = new BufferedOutputStream(CdsPaths.newPrivateFile(manifest, pending), BUFFER);
        }

        void check() throws IOException {
            long now=System.nanoTime();
            if (Thread.currentThread().isInterrupted()) throw CdsPaths.failure("CDS_INPUT_CANCELLED");
            if (now>=nextResourceCheck) {
                if (cancelled.getAsBoolean()) throw CdsPaths.failure("CDS_INPUT_CANCELLED");
                now = System.nanoTime();
                nextResourceCheck=now+250_000_000L;
            }
            if ((now - started) / 1_000_000 > limits.maxDurationMillis()) throw CdsPaths.failure("CDS_INPUT_DEADLINE");
            long written=Math.addExact(total, archiveCopied);
            if (written>=nextDiskCheck) {
                if (Files.getFileStore(pending).getUsableSpace() < limits.minimumFreeDiskBytes()) throw CdsPaths.failure("CDS_INPUT_DISK_RESERVE");
                nextDiskCheck=written+(16L<<20);
            }
        }

        void finalCheck() throws IOException {
            // The last durable cancellation/lease probe cannot be hidden by the hot-loop throttle.
            nextResourceCheck = 0;
            check();
        }

        void reserveDirectory(String name) throws IOException {
            String relative = normalizedRelative(name, limits);
            parents(relative);
            reserveDirectory(relative, true);
        }

        void parents(String name) throws IOException {
            int position = name.indexOf('/');
            while (position >= 0) { reserveDirectory(name.substring(0, position), false); position = name.indexOf('/', position + 1); }
        }

        void reserveDirectory(String relative, boolean explicit) throws IOException {
            Path key = index.resolve(nameKey(relative));
            if (Files.exists(key, LinkOption.NOFOLLOW_LINKS)) {
                JsonNode existing;
                try (InputStream input = Files.newInputStream(key, LinkOption.NOFOLLOW_LINKS)) { existing = JSON.readTree(input.readNBytes(65536)); }
                if (existing == null || !relative.equals(existing.path("relativeName").asText())
                        || !existing.path("directory").asBoolean() || explicit && existing.path("explicit").asBoolean()) {
                    throw CdsPaths.failure("CDS_SOURCE_NAME_COLLISION");
                }
                if (explicit) {
                    ((ObjectNode) existing).put("explicit", true);
                    Files.write(key, JSON.writeValueAsBytes(existing), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                    CdsPaths.forceFile(key);
                }
            } else reserve(relative, JSON.createObjectNode().put("relativeName", relative).put("directory", true).put("explicit", explicit));
        }

        void reserve(String relative, ObjectNode node) throws IOException {
            if (++entries > limits.maxEntries()) throw CdsPaths.failure("CDS_INPUT_ENTRY_LIMIT");
            Path key = index.resolve(nameKey(relative));
            try (OutputStream marker = CdsPaths.newPrivateFile(key, pending)) {
                marker.write(JSON.writeValueAsBytes(node)); marker.write('\n');
            } catch (java.nio.file.FileAlreadyExistsException collision) {
                throw CdsPaths.failure("CDS_SOURCE_NAME_COLLISION");
            }
            CdsPaths.forceFile(key);
        }

        Path copyArchive(InputStream input) throws IOException {
            Path path = pending.resolve("source-archive.bin"); MessageDigest hash = CdsPaths.sha256();
            long bytes = 0;
            try (OutputStream output = new BufferedOutputStream(CdsPaths.newPrivateFile(path, pending), BUFFER)) {
                byte[] buffer = new byte[BUFFER]; int n;
                while ((n = input.read(buffer)) >= 0) {
                    check(); bytes = Math.addExact(bytes, n); archiveCopied=bytes;
                    if (bytes > limits.maxSourceBytes()) throw CdsPaths.failure("CDS_INPUT_BYTE_LIMIT");
                    hash.update(buffer, 0, n); output.write(buffer, 0, n);
                }
            }
            CdsPaths.forceFile(path); archiveSha256 = CdsPaths.hex(hash.digest()); return path;
        }

        void copy(String name, InputStream source, boolean ignored) throws IOException {
            copy(name, source, ignored ? 1 : 0);
        }

        void copy(String name, InputStream source, int sourceRole) throws IOException {
            check();
            String relative = normalizedRelative(name, limits);
            parents(relative);
            String member = String.format(Locale.ROOT, "%012d.bin", files + 1);
            Path destination = members.resolve(member);
            MessageDigest bytesDigest = CdsPaths.sha256();
            CRC32 crc = new CRC32();
            CdsXmlText.Signature signature=new CdsXmlText.Signature();
            long size = 0;
            try (OutputStream target = new BufferedOutputStream(CdsPaths.newPrivateFile(destination, pending), BUFFER)) {
                byte[] buffer = new byte[BUFFER];
                int n;
                while ((n = source.read(buffer)) >= 0) {
                    if (n == 0) continue;
                    check();
                    size = Math.addExact(size, n); total = Math.addExact(total, n);
                    if (size > limits.maxSourceBytes() || total > limits.maxExpandedBytes()) {
                        throw CdsPaths.failure("CDS_INPUT_BYTE_LIMIT");
                    }
                    if (archiveInput != null && total / (double) Math.max(1, archiveInput.count) > limits.maxCompressionRatio()) {
                        throw CdsPaths.failure("CDS_ZIP_COMPRESSION_RATIO_LIMIT");
                    }
                    signature.accept(buffer, 0, n);
                    bytesDigest.update(buffer, 0, n); crc.update(buffer, 0, n); target.write(buffer, 0, n);
                }
            }
            CdsPaths.forceFile(destination);
            boolean xmlName = relative.toLowerCase(Locale.ROOT).endsWith(".xml");
            boolean xml = sourceRole == 1 || sourceRole == 0 && (xmlName || signature.xml()) && patientCandidate(destination, limits);
            ObjectNode node = JSON.createObjectNode().put("relativeName", relative).put("member", member)
                    .put("sha256", CdsPaths.hex(bytesDigest.digest())).put("length", size).put("patientXml", xml);
            node.put("crc32", crc.getValue());
            node.put("sourceRole", xml ? "patient-xml-candidate" : xmlName || signature.xml() ? "ancillary-xml" : "ancillary");
            byte[] row = JSON.writeValueAsBytes(node);
            reserve(relative, node.deepCopy().put("manifestOffset", manifestOffset).put("manifestLength", row.length));
            output.write(row); output.write('\n'); manifestDigest.update(row); manifestDigest.update((byte) '\n');
            manifestOffset = Math.addExact(manifestOffset, row.length + 1L);
            files++; if (xml) xmls++;
        }

        void finish() throws IOException {
            finalCheck();
            output.flush(); CdsPaths.forceFile(manifest);
            String manifestHash = CdsPaths.hex(manifestDigest.digest());
            digest = fingerprint(manifestHash, archiveSha256);
            finalCheck();
            try (OutputStream seal = CdsPaths.newPrivateFile(pending.resolve("snapshot.seal"), pending)) {
                ObjectNode metadata = JSON.createObjectNode().put("version", 2).put("manifestSha256", manifestHash)
                        .put("archiveSha256", archiveSha256).put("files", files).put("patientXmlFiles", xmls).put("totalBytes", total);
                seal.write(JSON.writeValueAsBytes(metadata)); seal.write('\n');
            }
            CdsPaths.forceFile(pending.resolve("snapshot.seal"));
            CdsPaths.forceDirectory(members); CdsPaths.forceDirectory(index); CdsPaths.forceDirectory(pending);
        }

        @Override public void close() throws IOException { output.close(); }
    }

    private static final class CountingSource extends FilterInputStream {
        private long count;
        private final long maximum;
        private final CdsZipDirectory.Check check;
        CountingSource(InputStream input, long maximum, CdsZipDirectory.Check check) {
            super(input); this.maximum = maximum; this.check = check;
        }
        @Override public int read() throws IOException { check.run(); int value = in.read(); if (value >= 0) add(1); return value; }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            check.run();
            int n = in.read(bytes, offset, Math.min(length, BUFFER)); if (n > 0) add(n); return n;
        }
        private void add(int n) throws IOException { count = Math.addExact(count, n); if (count > maximum) throw CdsPaths.failure("CDS_INPUT_BYTE_LIMIT"); }
    }
}
