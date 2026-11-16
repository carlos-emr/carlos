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
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Strict bounded internal JSON-lines transport. Caller owns the authenticated underlying stream. */
final class CdsExportJsonLines {
    private static final ObjectMapper JSON =
            new ObjectMapper()
                    .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final InputStream input;
    private final int maximum;
    private final Runnable checkpoint;
    // Read in blocks rather than a byte at a time: one transport line per captured field.
    private final byte[] buffer = new byte[65_536];
    private int position, limit;
    private byte[] line = new byte[256];
    private final CharsetDecoder decoder =
            StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);

    CdsExportJsonLines(InputStream input, int maximum, Runnable checkpoint) {
        if (maximum < 1 || maximum > 1_048_576)
            throw new IllegalArgumentException("CDS_EXPORT_JSON_LINE_LIMIT_INVALID");
        this.input = input;
        this.maximum = maximum;
        this.checkpoint = checkpoint;
    }

    com.fasterxml.jackson.databind.JsonNode required() throws IOException {
        var node = next();
        if (node == null) invalid();
        return node;
    }

    /**
     * The next line as a JSON object, or null at a clean end of input. A line ends at a line
     * feed; a carriage return, a line longer than the bound, an unterminated last line, invalid
     * UTF-8 or anything but one JSON object is refused.
     */
    JsonNode next() throws IOException {
        checkpoint.run();
        if (!fill()) return null;
        int length = 0;
        while (true) {
            if (position == limit && !fill()) invalid();
            int start = position;
            while (position < limit) {
                byte value = buffer[position];
                if (value == '\n') break;
                if (value == '\r') invalid();
                position++;
            }
            int count = position - start;
            if (count > maximum - length) invalid();
            if (length + count > line.length)
                line = Arrays.copyOf(line, Math.min(maximum, Math.max(length + count, line.length * 2)));
            System.arraycopy(buffer, start, line, length, count);
            // The same cadence as before: at least one checkpoint per 8 KiB of a long line.
            if ((length >>> 13) != ((length + count) >>> 13)) checkpoint.run();
            length += count;
            if (position < limit) {
                position++; // the line feed
                break;
            }
        }
        String text;
        try {
            text = decoder.reset().decode(ByteBuffer.wrap(line, 0, length)).toString();
        } finally {
            Arrays.fill(line, 0, length, (byte) 0);
        }
        var node = JSON.readTree(text);
        if (node == null || !node.isObject()) invalid();
        return node;
    }

    /** True when at least one unread byte is buffered; false at the end of input. */
    private boolean fill() throws IOException {
        if (position < limit) return true;
        position = 0;
        limit = 0;
        // As the buffered stream this replaces: a read that returns no byte is the end of input.
        int count = input.read(buffer, 0, buffer.length);
        if (count <= 0) return false;
        limit = count;
        return true;
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_JSON_LINES_INVALID");
    }
}
