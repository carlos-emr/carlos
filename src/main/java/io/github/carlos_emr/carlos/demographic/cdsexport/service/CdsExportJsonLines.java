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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Strict bounded internal JSON-lines transport. Caller owns the authenticated underlying stream. */
final class CdsExportJsonLines {
    private static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final InputStream input;
    private final int maximum;
    private final Runnable checkpoint;
    CdsExportJsonLines(InputStream input, int maximum, Runnable checkpoint) {
        if (maximum < 1 || maximum > 1_048_576) throw new IllegalArgumentException("CDS_EXPORT_JSON_LINE_LIMIT_INVALID");
        this.input = new BufferedInputStream(input, 65_536); this.maximum = maximum; this.checkpoint = checkpoint;
    }
    com.fasterxml.jackson.databind.JsonNode required() throws IOException {
        var node = next(); if (node == null) invalid(); return node;
    }
    JsonNode next() throws IOException {
        checkpoint.run(); var bytes = new ByteArrayOutputStream(); int value = input.read();
        if (value == -1) return null;
        while (value != '\n') {
            if (value == -1 || value == '\r' || bytes.size() == maximum) invalid();
            bytes.write(value); value = input.read(); if ((bytes.size() & 8191) == 0) checkpoint.run();
        }
        String line = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
        var node = JSON.readTree(line); if (node == null || !node.isObject()) invalid(); return node;
    }
    private static void invalid() throws IOException { throw new IOException("CDS_EXPORT_JSON_LINES_INVALID"); }
}
