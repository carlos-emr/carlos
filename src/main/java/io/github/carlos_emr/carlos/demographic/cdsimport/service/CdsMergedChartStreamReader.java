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

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsMergedChartContract;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.TreeMap;
import org.apache.commons.io.input.ReaderInputStream;

/**
 * Reads the fixed merged-chart JSON contract without materializing its embedded XML string.
 * Only protocol keys, chart identifiers, section ranges and the digest are retained. The document
 * flows through JSON unescaping, strict UTF-8 encoding, schema validation and hashing in chunks.
 */
final class CdsMergedChartStreamReader {
    private final Reader input;
    private int pending = -2;

    private CdsMergedChartStreamReader(Reader input) {
        this.input = input;
    }

    static CdsMergedChartReader.ChartScope read(Reader input) throws IOException {
        try (var buffered = new BufferedReader(new java.io.FilterReader(input) {
            @Override public void close() { /* The caller owns and authenticates the field. */ }
        }, 8192)) {
            return new CdsMergedChartStreamReader(buffered).chart();
        }
    }

    private CdsMergedChartReader.ChartScope chart() throws IOException {
        expect('{');
        key("version");
        if (number() != CdsMergedChartContract.VERSION) throw invalid();
        expect(',');
        key("chart");
        // C plus the ten decimal digits of a positive Java int is the identifier contract.
        int chart = CdsMergedChartContract.chart(string(11));
        expect(',');
        key("head");
        int head = CdsMergedChartContract.chart(string(11));
        expect(',');
        key("positions");
        var positions = positions();
        if (chart == head || positions.containsKey(CdsCategory.DEMOGRAPHICS)) throw invalid();
        expect(',');
        key("document");
        expect('"');
        String digest;
        try (var document = ReaderInputStream.builder()
                .setReader(new JsonString())
                .setCharsetEncoder(StandardCharsets.UTF_8.newEncoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT))
                .setBufferSize(8192).get()) {
            digest = CdsMergedChartContract.verifyDocument(chart, document);
        } catch (CharacterCodingException _) {
            throw invalid();
        }
        expect(',');
        key("documentSha256");
        String expected = string(64);
        if (!expected.matches("[0-9a-f]{64}") || !CdsImportKeys.sameDigest(digest, expected))
            throw invalid();
        expect('}');
        if (peek() != -1) throw invalid();
        return new CdsMergedChartReader.ChartScope(chart, head, positions);
    }

    private TreeMap<CdsCategory, CdsMergedChartContract.Range> positions() throws IOException {
        var positions = new TreeMap<CdsCategory, CdsMergedChartContract.Range>();
        int longestName = CdsCategory.wireNames().stream().mapToInt(String::length).max().orElseThrow();
        expect('{');
        if (peek() != '}') {
            while (true) {
                String name = string(longestName);
                if (!CdsCategory.wireNames().contains(name)) throw invalid();
                CdsCategory category = CdsCategory.fromWireName(name);
                expect(':');
                expect('{');
                key("first");
                long first = number();
                expect(',');
                key("count");
                long count = number();
                expect('}');
                if (positions.putIfAbsent(category, new CdsMergedChartContract.Range(first, count)) != null)
                    throw invalid();
                if (peek() != ',') break;
                expect(',');
            }
        }
        expect('}');
        return positions;
    }

    private void key(String name) throws IOException {
        if (!name.equals(string(name.length()))) throw invalid();
        expect(':');
    }

    /** These lengths come from the fixed protocol, never from a clinical document limit. */
    private String string(int maximum) throws IOException {
        expect('"');
        var value = new StringBuilder();
        try (var string = new JsonString()) {
            for (int character; (character = string.read()) != -1; ) {
                if (value.length() == maximum) throw invalid();
                value.append((char) character);
            }
        }
        return value.toString();
    }

    private long number() throws IOException {
        var text = new StringBuilder();
        if (peek() == '-') {
            text.append('-');
            take();
        }
        int first = take();
        if (first < '0' || first > '9') throw invalid();
        text.append((char) first);
        while (pendingDigit()) {
            if (first == '0' || text.length() == 20) throw invalid();
            text.append((char) take());
        }
        try { return Long.parseLong(text.toString()); }
        catch (NumberFormatException _) { throw invalid(); }
    }

    private boolean pendingDigit() throws IOException {
        if (pending == -2) pending = input.read();
        return pending >= '0' && pending <= '9';
    }

    private int peek() throws IOException {
        if (pending == -2) pending = input.read();
        while (pending == ' ' || pending == '\t' || pending == '\r' || pending == '\n')
            pending = input.read();
        return pending;
    }

    private int take() throws IOException {
        int value = pending == -2 ? input.read() : pending;
        pending = -2;
        return value;
    }

    private void expect(char expected) throws IOException {
        if (peek() != expected) throw invalid();
        take();
    }

    /** Consumes exactly the opened JSON string; it cannot read into the following metadata. */
    private final class JsonString extends Reader {
        private boolean ended;

        @Override public int read() throws IOException {
            if (ended) return -1;
            int value = take();
            if (value == '"') {
                ended = true;
                return -1;
            }
            if (value < 0x20) throw invalid();
            if (value != '\\') return value;
            return switch (take()) {
                case '"' -> '"';
                case '\\' -> '\\';
                case '/' -> '/';
                case 'b' -> '\b';
                case 'f' -> '\f';
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case 'u' -> unicode();
                default -> throw invalid();
            };
        }

        private int unicode() throws IOException {
            int value = 0;
            for (int index = 0; index < 4; index++) {
                int digit = take();
                int hex = digit >= '0' && digit <= '9' ? digit - '0'
                        : digit >= 'a' && digit <= 'f' ? digit - 'a' + 10
                        : digit >= 'A' && digit <= 'F' ? digit - 'A' + 10 : -1;
                if (hex < 0) throw invalid();
                value = (value << 4) | hex;
            }
            return value;
        }

        @Override public int read(char[] buffer, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, buffer.length);
            int count = 0;
            while (count < length) {
                int value = read();
                if (value == -1) break;
                buffer[offset + count++] = (char) value;
            }
            return count == 0 && length != 0 ? -1 : count;
        }

        @Override public void close() { /* The field parser owns and authenticates the carrier. */ }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException(CdsMergedChartReader.INVALID);
    }
}
