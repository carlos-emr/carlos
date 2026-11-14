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

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsExactNumeric;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedFieldDto;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The stored replay form of one checked capture read, for a capture too large to replay from
 * memory ({@link CdsExportVerifiedCaptures}).
 *
 * <p>Every reader of a chart reads the patient's whole capture. Read from its members each time,
 * a long imported chart is decrypted, parsed line by line and hashed more than a hundred times
 * over. Instead, the one complete read that passes every check of {@link
 * CdsExportNativeCaptureReader#scan} also writes each verified field, in order, to a second
 * staged member: its row, column, kind, length, SHA-256 and exact numeric evidence as a few
 * binary values, not as a JSON line. The member is staged like the capture itself, encrypted and
 * authenticated under the job's key, and is erased when the patient's session ends.
 *
 * <p>A later read streams that member beside the capture's value member. It delivers the same
 * fields in the same order with the same bytes, holds one field and one chunk in memory whatever
 * the chart's size, and still hashes every value and compares it with the digest the complete
 * read verified, so a value that is not byte for byte the verified one is refused. Both members
 * are read to their authenticated end before the read returns, and staging authenticates every
 * chunk of either before any byte of it is read. What is not repeated is the parsing of the JSON
 * index and the strict UTF-8 check of bytes whose digest already proves them to be the checked
 * bytes; everything a read does around the transport runs as on any read.
 *
 * <p>The member costs staging disk, about 46 bytes for each field and nothing for the values,
 * and no heap that grows with the chart.
 *
 * @since 2026-11-14
 */
final class CdsExportCaptureReplay {
    /** The staging purpose of a replay member. */
    static final String PURPOSE = "SOURCE_REPLAY";

    private static final int MAGIC = 0x43525031;
    private static final byte NAME = 1, ROW = 2, FIELD = 3, END = 4;
    private static final int CHUNK = 65_536;
    private static final int CHECKPOINT_FIELDS = 256;
    private static final HexFormat HEX = HexFormat.of();
    private static final CdsExportCapturedFieldDto.Kind[] KINDS =
            CdsExportCapturedFieldDto.Kind.values();
    private static final CdsExactNumeric.Codec[] CODECS = CdsExactNumeric.Codec.values();

    private CdsExportCaptureReplay() {}

    /** Writes the fields of one complete read as that read verifies them. */
    static final class Writer {
        private final DataOutputStream output;
        private final Map<String, Integer> names = new HashMap<>();
        private String table, recordKey;
        private long fields, next;
        private boolean broken;

        Writer(OutputStream output) throws IOException {
            this.output = new DataOutputStream(Objects.requireNonNull(output));
            this.output.writeInt(MAGIC);
        }

        /** The sink the reader writes to: every call reaches {@code sink} unchanged. */
        CdsExportNativeCaptureReader.Fields tee(CdsExportNativeCaptureReader.Fields sink) {
            Objects.requireNonNull(sink);
            return new CdsExportNativeCaptureReader.Fields() {
                @Override
                public OutputStream output(CdsExportCapturedFieldDto field) throws IOException {
                    return sink.output(field);
                }

                @Override
                public void verified(CdsExportCapturedFieldDto field) throws IOException {
                    add(field);
                    sink.verified(field);
                }
            };
        }

        private void add(CdsExportCapturedFieldDto field) throws IOException {
            // A replay delivers values in the order they lie in the value member; a read that
            // is not gapless is not written and is never replayed.
            if (broken || field.offset() != next) {
                broken = true;
                return;
            }
            int tableIndex = name(field.table());
            int columnIndex = name(field.column());
            if (tableIndex < 0 || columnIndex < 0) {
                broken = true;
                return;
            }
            if (!field.table().equals(table) || !field.recordKey().equals(recordKey)) {
                table = field.table();
                recordKey = field.recordKey();
                output.writeByte(ROW);
                output.writeShort(tableIndex);
                output.writeUTF(recordKey);
            }
            output.writeByte(FIELD);
            output.writeShort(columnIndex);
            output.writeByte(field.kind().ordinal());
            output.writeLong(field.bytes());
            output.write(HEX.parseHex(field.sha256()));
            var exact = field.exactNumeric();
            output.writeByte(exact == null ? 0 : 1 + exact.codec().ordinal());
            if (exact != null) output.writeUTF(exact.bits());
            next += field.bytes();
            fields++;
        }

        private int name(String name) throws IOException {
            Integer known = names.get(name);
            if (known != null) return known;
            if (names.size() == Short.MAX_VALUE) return -1;
            output.writeByte(NAME);
            output.writeUTF(name);
            names.put(name, names.size());
            return names.size() - 1;
        }

        /**
         * Ends the member for a read that passed every check and returned {@code result}; false
         * when what was written is not that read and must not be kept.
         */
        boolean finish(CdsExportNativeCaptureReader.Result result) throws IOException {
            Objects.requireNonNull(result);
            output.writeByte(END);
            output.writeLong(fields);
            output.writeLong(next);
            output.flush();
            return !broken && fields == result.fields() && next == result.valueBytes();
        }
    }

    /**
     * Replays a stored read into {@code output}: {@code replay} is the stored member and {@code
     * values} the capture's value member, both opened from staging and consumed here to their
     * authenticated end. {@code expected} is the result the complete read returned.
     */
    static CdsExportNativeCaptureReader.Result read(
            InputStream replay,
            InputStream values,
            CdsExportNativeCaptureReader.Result expected,
            long maximumFields,
            long maximumBytes,
            Runnable checkpoint,
            CdsExportNativeCaptureReader.Fields output)
            throws IOException {
        Objects.requireNonNull(expected);
        Objects.requireNonNull(checkpoint);
        Objects.requireNonNull(output);
        if (maximumFields < 1
                || maximumBytes < 1
                || expected.fields() > maximumFields
                || expected.valueBytes() > maximumBytes) invalid();
        var input = new DataInputStream(Objects.requireNonNull(replay));
        Objects.requireNonNull(values);
        List<String> names = new ArrayList<>();
        MessageDigest digest = sha256();
        byte[] buffer = new byte[CHUNK];
        byte[] recorded = new byte[32];
        String table = null, recordKey = null;
        long fields = 0, offset = 0;
        int sinceCheckpoint = 0;
        try {
            if (input.readInt() != MAGIC) invalid();
            checkpoint.run();
            while (true) {
                byte tag = input.readByte();
                if (tag == NAME) {
                    if (names.size() == Short.MAX_VALUE) invalid();
                    names.add(input.readUTF());
                } else if (tag == ROW) {
                    table = name(names, input.readUnsignedShort());
                    recordKey = input.readUTF();
                } else if (tag == FIELD) {
                    if (table == null || fields == expected.fields()) invalid();
                    String column = name(names, input.readUnsignedShort());
                    int kind = input.readUnsignedByte();
                    long length = input.readLong();
                    input.readFully(recorded);
                    int codec = input.readUnsignedByte();
                    if (kind >= KINDS.length
                            || codec > CODECS.length
                            || length < 0
                            || length > expected.valueBytes() - offset) invalid();
                    String bits = codec == 0 ? null : input.readUTF();
                    CdsExportCapturedFieldDto field;
                    try {
                        field =
                                new CdsExportCapturedFieldDto(
                                        table,
                                        recordKey,
                                        column,
                                        KINDS[kind],
                                        offset,
                                        length,
                                        HEX.formatHex(recorded),
                                        bits == null
                                                ? null
                                                : new CdsExactNumeric(CODECS[codec - 1], bits));
                    } catch (IllegalArgumentException malformed) {
                        throw new IOException("CDS_EXPORT_CAPTURE_READBACK_INVALID");
                    }
                    if (++sinceCheckpoint == CHECKPOINT_FIELDS) {
                        checkpoint.run();
                        sinceCheckpoint = 0;
                    }
                    var target =
                            Objects.requireNonNull(
                                    output.output(field), "CDS_EXPORT_FIELD_SINK_REQUIRED");
                    long remaining = length;
                    while (remaining > 0) {
                        int count = (int) Math.min(CHUNK, remaining);
                        if (count == CHUNK) checkpoint.run();
                        if (values.readNBytes(buffer, 0, count) != count) invalid();
                        digest.update(buffer, 0, count);
                        target.write(buffer, 0, count);
                        remaining -= count;
                    }
                    // The bytes delivered are the bytes the complete read checked, or the read
                    // fails before the consumer is told the field is verified.
                    if (!MessageDigest.isEqual(digest.digest(), recorded)) invalid();
                    output.verified(field);
                    offset += length;
                    fields++;
                } else if (tag == END) {
                    if (input.readLong() != fields
                            || input.readLong() != offset
                            || fields != expected.fields()
                            || offset != expected.valueBytes()
                            // Both members end here, and staging authenticates each at its end.
                            || input.read() != -1
                            || values.read() != -1) invalid();
                    checkpoint.run();
                    return expected;
                } else invalid();
            }
        } catch (EOFException | java.io.UTFDataFormatException malformed) {
            throw new IOException("CDS_EXPORT_CAPTURE_READBACK_INVALID");
        } finally {
            Arrays.fill(buffer, (byte) 0);
            digest.reset();
        }
    }

    private static String name(List<String> names, int index) throws IOException {
        if (index >= names.size()) invalid();
        return names.get(index);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_CAPTURE_READBACK_INVALID");
    }
}
