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

package io.github.carlos_emr.carlos.email.archive;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Reads and writes the outbound email archive keyring file (#3448).
 *
 * <p>The file is plain ASCII, one {@code name=value} per line, so an operator can read it and add a
 * key by hand when CARLOS cannot write it (a read-only secret mount):</p>
 * <pre>
 * # comments and blank lines are ignored
 * format=1
 * current=2
 * key.1=&lt;Base64 of 32 random bytes&gt;
 * key.2=&lt;Base64 of 32 random bytes&gt;
 * </pre>
 *
 * <p>Parsing is strict and fails closed. {@code java.util.Properties} is deliberately not used: it
 * silently keeps the last of two {@code key.1} lines, which would turn a botched hand edit into
 * artifacts that no longer open. Here a duplicate, an unknown name, an unknown {@code format}, a
 * key that is not exactly 32 bytes, or a {@code current} naming no key is an error. Error messages
 * give the line number and field name only, never a value.</p>
 *
 * @since 2026-09-30
 */
public final class OutboundEmailArchiveKeyringParser {

    /** The only format this build reads and writes. */
    public static final String FORMAT_1 = "1";

    /** A keyring file is a few hundred bytes; anything this large is not one. */
    public static final int MAX_FILE_BYTES = 64 * 1024;

    private static final String KEY_PREFIX = "key.";
    /** Positive decimal without leading zeros, so {@code key.01} cannot shadow {@code key.1}. */
    private static final Pattern KEY_ID = Pattern.compile("[1-9][0-9]{0,9}");

    private static final String HEADER_COMMENT = """
            # CARLOS EMR outbound email archive keyring.
            # These keys encrypt the archived copy of every email sent to a patient.
            # Back this file up with the server configuration, and keep a copy off this server.
            # Never delete or change a key.N line: archived emails encrypted with that key
            # would become permanently unreadable. To add a key, see "Rotating the archive key"
            # in docs/outbound-email-archive.md.
            """;

    private OutboundEmailArchiveKeyringParser() {
    }

    /**
     * Parses a keyring file.
     *
     * @param content the file's bytes
     * @return the keyring it describes
     * @throws IllegalArgumentException naming the line and field at fault, never a value
     */
    public static OutboundEmailArchiveKeyring parse(byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("the file is empty");
        }
        if (content.length > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("the file is larger than " + MAX_FILE_BYTES + " bytes");
        }
        for (byte b : content) {
            if (b < 0) {
                throw new IllegalArgumentException("the file contains non-ASCII bytes");
            }
        }
        String format = null;
        Integer current = null;
        TreeMap<Integer, byte[]> keys = new TreeMap<>();
        String[] lines = new String(content, StandardCharsets.US_ASCII).split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            int lineNumber = index + 1;
            String line = lines[index].strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int equals = line.indexOf('=');
            if (equals <= 0) {
                throw new IllegalArgumentException("line " + lineNumber + " is not name=value");
            }
            String name = line.substring(0, equals).strip();
            String value = line.substring(equals + 1).strip();
            if ("format".equals(name)) {
                requireFirst(format == null, lineNumber, name);
                format = value;
            } else if ("current".equals(name)) {
                requireFirst(current == null, lineNumber, name);
                current = parseKeyId(value, lineNumber, name);
            } else if (name.startsWith(KEY_PREFIX)) {
                int keyId = parseKeyId(name.substring(KEY_PREFIX.length()), lineNumber, "key id");
                requireFirst(!keys.containsKey(keyId), lineNumber, name);
                keys.put(keyId, decodeKey(value, lineNumber, name));
            } else {
                throw new IllegalArgumentException("line " + lineNumber + " has an unknown name");
            }
        }
        if (!FORMAT_1.equals(format)) {
            throw new IllegalArgumentException(format == null
                    ? "format is missing"
                    : "format is not " + FORMAT_1 + ", the only version this CARLOS build reads");
        }
        if (current == null) {
            throw new IllegalArgumentException("current is missing");
        }
        if (keys.isEmpty()) {
            throw new IllegalArgumentException("the file holds no key.N lines");
        }
        if (!keys.containsKey(current)) {
            throw new IllegalArgumentException("current names key " + current + ", which is not in the file");
        }
        try {
            return new OutboundEmailArchiveKeyring(current, keys);
        } finally {
            // The keyring holds its own copies; do not leave decoded key bytes behind.
            keys.values().forEach(material -> Arrays.fill(material, (byte) 0));
        }
    }

    /**
     * Renders a keyring in the file format {@link #parse(byte[])} reads.
     *
     * @param keyring the keyring to write
     * @return the file content, ASCII
     */
    public static byte[] format(OutboundEmailArchiveKeyring keyring) {
        StringBuilder out = new StringBuilder(HEADER_COMMENT)
                .append("format=").append(FORMAT_1).append('\n')
                .append("current=").append(keyring.currentKeyId()).append('\n');
        Base64.Encoder encoder = Base64.getEncoder();
        for (int keyId : keyring.keyIds()) {
            out.append(KEY_PREFIX).append(keyId).append('=')
                    .append(encoder.encodeToString(keyring.encodedKey(keyId))).append('\n');
        }
        return out.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private static void requireFirst(boolean first, int lineNumber, String name) {
        if (!first) {
            throw new IllegalArgumentException("line " + lineNumber + " repeats " + name);
        }
    }

    private static int parseKeyId(String value, int lineNumber, String name) {
        if (!KEY_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("line " + lineNumber + ": " + name + " is not a positive whole number");
        }
        long keyId = Long.parseLong(value);
        if (keyId > OutboundEmailArchiveKeyring.MAX_KEY_ID) {
            throw new IllegalArgumentException("line " + lineNumber + ": " + name + " is too large");
        }
        return (int) keyId;
    }

    private static byte[] decodeKey(String value, int lineNumber, String name) {
        byte[] material;
        try {
            material = Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            // The decoder's message can quote the offending character; drop it.
            material = null;
        }
        if (material == null || material.length != OutboundEmailArchiveKeyring.KEY_BYTES) {
            throw new IllegalArgumentException("line " + lineNumber + ": " + name + " is not a Base64-encoded "
                    + OutboundEmailArchiveKeyring.KEY_BYTES + "-byte key");
        }
        return material;
    }
}
