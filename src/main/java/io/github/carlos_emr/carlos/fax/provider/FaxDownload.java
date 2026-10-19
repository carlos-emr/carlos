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
        Path path = directory == null ? Files.createTempFile("carlos-fax-", ".pdf")
                : Files.createTempFile(directory, "carlos-fax-", ".pdf");
        FaxDownload download = new FaxDownload(path, mapper.createObjectNode());
        try {
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
                    JsonToken value = parser.nextToken();
                    if (documentField.equals(field)) {
                        if (foundDocument || value != JsonToken.VALUE_STRING) {
                            throw new IOException("Invalid or duplicate fax document");
                        }
                        foundDocument = true;
                        parser.readBinaryValue(output);
                    } else {
                        if (download.metadata.has(field)) {
                            throw new IOException("Duplicate fax response field");
                        }
                        download.metadata.set(field, mapper.readTree(parser));
                    }
                    input.document = false;
                }
                if (!foundDocument || parser.nextToken() != null) {
                    throw new IOException("Missing fax document or trailing response content");
                }
            }
            if (Files.size(path) == 0) {
                throw new IOException("Empty fax document");
            }
            return download;
        } catch (IOException | RuntimeException e) {
            try {
                download.close();
            } catch (IOException cleanup) {
                e.addSuppressed(cleanup);
            }
            if (e instanceof IOException io) {
                throw io;
            }
            // Jackson's streaming binary decoder reports some bad alphabet/padding
            // errors as IllegalArgumentException rather than JsonParseException.
            throw new IOException("Invalid fax response content", e);
        }
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
