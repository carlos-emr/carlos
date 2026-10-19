/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Published under the GNU General Public License, version 2 or later.
 */
package io.github.carlos_emr.carlos.fax.provider;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.hc.core5.http.HttpEntity;

/** Owns a private spool until a fully parsed, successful provider reply is accepted. */
final class FaxDownload implements AutoCloseable {
    private final Path path;
    private final ObjectNode metadata;
    private boolean released;

    private FaxDownload(Path path, ObjectNode metadata) {
        this.path = path;
        this.metadata = metadata;
    }

    static FaxDownload read(HttpEntity entity, ObjectMapper mapper, String documentField) throws IOException {
        return read(entity, mapper, documentField, null);
    }

    /** Test seam for proving cleanup without observing other downloads' private spools. */
    static FaxDownload read(HttpEntity entity, ObjectMapper mapper, String documentField, Path directory)
            throws IOException {
        Path path = createSpool(directory);
        try {
            ObjectNode metadata = decode(entity, mapper, documentField, path);
            if (Files.size(path) == 0) {
                throw new IOException("Empty fax document");
            }
            // Ownership is transferred to the returned resource only after parsing succeeds.
            return new FaxDownload(path, metadata);
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException cleanup) {
                e.addSuppressed(cleanup);
            }
            if (e instanceof IOException io) {
                throw io;
            }
            // Jackson's binary decoder reports some alphabet/padding failures unchecked.
            throw new IOException("Invalid fax response content", e);
        }
    }

    /** Exclusive random creation plus owner-only permissions protects spools in shared temp directories. */
    @SuppressWarnings("java:S5443") // Secure NIO creation; explicit POSIX 0600 is asserted in the spool lifecycle test.
    private static Path createSpool(Path directory) throws IOException {
        java.nio.file.attribute.FileAttribute<?>[] attributes =
                java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix")
                ? new java.nio.file.attribute.FileAttribute<?>[] {
                    java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                            java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"))}
                : new java.nio.file.attribute.FileAttribute<?>[0];
        return directory == null ? Files.createTempFile("carlos-fax-", ".pdf", attributes)
                : Files.createTempFile(directory, "carlos-fax-", ".pdf", attributes);
    }

    private static ObjectNode decode(HttpEntity entity, ObjectMapper mapper, String documentField, Path path)
            throws IOException {
        ObjectNode metadata = mapper.createObjectNode();
        try (FaxResponseStream input = FaxResponseStream.open(entity);
                JsonParser parser = mapper.getFactory().createParser(input);
                OutputStream output = new BufferedOutputStream(Files.newOutputStream(path))) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IOException("Expected fax response object");
            }
            boolean foundDocument = false;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                if (parser.currentToken() != JsonToken.FIELD_NAME) {
                    throw new IOException("Malformed fax response");
                }
                String field = parser.currentName();
                input.document = documentField.equals(field);
                parser.nextToken();
                if (input.document) {
                    foundDocument = decodeDocument(parser, output, foundDocument);
                } else {
                    readMetadataField(parser, mapper, metadata, field);
                }
                input.document = false;
            }
            if (!foundDocument || parser.nextToken() != null) {
                throw new IOException("Missing fax document or trailing response content");
            }
        }
        return metadata;
    }

    private static boolean decodeDocument(JsonParser parser, OutputStream output, boolean alreadySeen) throws IOException {
        if (alreadySeen || parser.currentToken() != JsonToken.VALUE_STRING) {
            throw new IOException("Invalid or duplicate fax document");
        }
        parser.readBinaryValue(output);
        return true;
    }

    private static void readMetadataField(JsonParser parser, ObjectMapper mapper, ObjectNode metadata, String field)
            throws IOException {
        if (metadata.has(field)) {
            throw new IOException("Duplicate fax response field");
        }
        metadata.set(field, mapper.readTree(parser));
    }

    ObjectNode metadata() {
        return metadata;
    }

    Path release() {
        released = true;
        return path;
    }

    @Override
    public void close() throws IOException {
        if (!released) {
            Files.deleteIfExists(path);
        }
    }

}
