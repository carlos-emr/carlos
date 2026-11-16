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
package io.github.carlos_emr.carlos.demographic.cds.contract;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import io.github.carlos_emr.carlos.demographic.cds.security.CdsNativeBaselineKeys;

/**
 * Additional versioned field witnesses bound to the exact, unchanged protocol-2 receipt.
 * Absence is a legacy/unknown baseline, never permission to backfill from current rows.
 * Every supplied row must reconstruct its receipt digest and the complete referenced-row
 * set. Stored receipt JSON and its digest are not rewritten or reinterpreted.
 *
 * <p>The witness document holds an unsalted per-column SHA-256 and byte length, which can be
 * reversed by enumeration for low-entropy PHI. It is therefore stored only sealed ({@link
 * #seal}): AES-256-GCM under a per-installation key held outside the database ({@link
 * CdsNativeBaselineKeys}), with the key ID recorded in the envelope and the receipt digest bound
 * as associated data. {@link #unseal} is the only reader of stored values and refuses an
 * unsealed document; {@link #encode}/{@link #decode} handle the plaintext document only in
 * memory (for example in an export's retained index after unsealing).
 *
 * <p>New witnesses are protocol-4 envelopes: the document is deflated before it is sealed. Every
 * committed import unit stores its witness twice (unit and checkpoint), and the document repeats
 * each row's column metadata, so an uncompressed witness averaged about 23 KB and took an
 * overflow page per copy; deflated it is about a quarter of that and usually stays in the row.
 * The compression is bound into the associated data, so a protocol number changed in storage
 * fails authentication. Protocol-3 envelopes (the same seal without compression) written by
 * earlier builds remain readable.
 *
 * <p>No size of the importer's choosing bounds a witness. It is authenticated, so only the
 * importer writes one that opens, and its size is what the importer wrote: one row for each row
 * its receipt names (at most 192, the receipt's own bound, which {@link #decode} checks row for
 * row), each of at most MariaDB's own 4,096 columns ({@link CdsNativeRowDigest#MAXIMUM_COLUMNS}).
 * A target table widened locally is therefore witnessed whole. The stored columns are LONGTEXT;
 * the only remaining limits are the VM's own ({@link #MAX_BYTES}, {@link #MAX_SEALED_BYTES}).
 */
public final class CdsNativeBaselineCodec {
    /**
     * The longest witness document: the VM's own limit on one array, not a format bound. Any
     * document the importer can build is far below it; it turns an impossible document into a
     * refusal rather than an {@link OutOfMemoryError}.
     */
    public static final int MAX_BYTES = Integer.MAX_VALUE - 8;

    /**
     * The longest stored witness: one Java string, held with the other columns of its import-unit
     * row (its receipt, digests and keys, well under 2 MiB) by a reader of that row. The VM's own
     * limit, as {@link #MAX_BYTES}; earlier builds stored at most 12 MiB.
     */
    public static final int MAX_SEALED_BYTES = Integer.MAX_VALUE - 8 - (2 << 20);

    /**
     * The longest string of a stored envelope: its sealed document, which is no longer than the
     * envelope itself. The envelope's other strings (key ID, nonce) are far shorter.
     */
    static final int MAX_SEALED_TEXT = MAX_SEALED_BYTES;

    /**
     * The longest string of a witness document or of the protocol-2 receipt it is bound to: a
     * column name or SQL type ({@link CdsNativeRowDigest.Column}, 128 characters). Table names,
     * row IDs and digests are at most 64.
     */
    static final int MAX_DOCUMENT_TEXT = 128;

    /**
     * The deepest witness document: document, rows, row, cells, cell, exact-numeric witness. A
     * receipt (document, refs, ref) is shallower.
     */
    static final int MAX_DOCUMENT_DEPTH = 6;

    /** The stored envelope is one flat object. */
    private static final int MAX_ENVELOPE_DEPTH = 1;

    private static final String SEALED_DOMAIN = "carlos-cds-native-baseline-v3";

    /** Protocol 4: the same seal over the deflated document, under its own associated data. */
    private static final String DEFLATED_DOMAIN = "carlos-cds-native-baseline-v4-deflate";
    private static final java.security.SecureRandom NONCES = new java.security.SecureRandom();

    /** The stored form: a sealed witness document and the key that sealed it. */
    public record Envelope(int protocol, String keyId, String nonce, String sealed) {
        @Override
        public String toString() {
            return "CdsNativeBaselineCodec.Envelope[protected]";
        }
    }
    /** Witness documents and their receipts: short strings, bounded nesting. */
    private static final JsonMapper JSON = mapper(MAX_DOCUMENT_DEPTH, MAX_DOCUMENT_TEXT);

    /**
     * Stored envelopes, whose {@code sealed} member is the whole Base64 document. Reading an
     * envelope with the document limits refused every witness over about 48 KiB (a Demographics
     * field page of a realistic chart), so the importer stopped with
     * NATIVE_BASELINE_REQUIRES_REVIEW on the witness it had just sealed.
     */
    private static final JsonMapper ENVELOPE = mapper(MAX_ENVELOPE_DEPTH, MAX_SEALED_TEXT);

    private static JsonMapper mapper(int depth, int text) {
        return JsonMapper.builder(
                        com.fasterxml.jackson.core.JsonFactory.builder()
                                .enable(
                                        com.fasterxml.jackson.core.StreamReadFeature
                                                .STRICT_DUPLICATE_DETECTION)
                                .streamReadConstraints(
                                        com.fasterxml.jackson.core.StreamReadConstraints.builder()
                                                .maxNestingDepth(depth)
                                                .maxStringLength(text)
                                                .maxNumberLength(20)
                                                .build())
                                .build())
                .disable(com.fasterxml.jackson.databind.MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
    }

    /**
     * A witness document: one row state for each row its receipt names, no more and no fewer
     * ({@link #verify}), so its row count follows the receipt's own bound.
     */
    public record Document(int protocol, String targetDigest, List<CdsNativeRowState> rows) {
        public Document {
            rows = List.copyOf(rows);
            if (protocol != 1 && protocol != 2
                    || targetDigest == null
                    || !targetDigest.matches("[0-9a-f]{64}")
                    || rows.isEmpty()) invalid();
        }

        @Override
        public String toString() {
            return "CdsNativeBaselineCodec.Document[protected]";
        }
    }

    private CdsNativeBaselineCodec() {}

    public static String encode(
            String references, String targetDigest, List<CdsNativeRowState> rows)
            throws IOException {
        var document = new Document(2, targetDigest, rows);
        verify(document, references, targetDigest);
        return JSON.writeValueAsString(document);
    }

    public static Document decode(String encoded, String references, String targetDigest)
            throws IOException {
        if (encoded == null || encoded.isEmpty()) invalid();
        JsonNode tree = JSON.readTree(encoded);
        if (tree != null
                && tree.path("protocol").isIntegralNumber()
                && tree.path("protocol").intValue() == 1) {
            // Normalize only the optional field absent from the exact original v1 contract.
            for (var row : tree.path("rows"))
                for (var cell : row.path("cells")) {
                    if (!cell.isObject() || cell.has("exactNumeric")) invalid();
                    ((com.fasterxml.jackson.databind.node.ObjectNode) cell).putNull("exactNumeric");
                }
        }
        Document document = JSON.treeToValue(tree, Document.class);
        if (document.protocol() == 1
                && document.rows().stream()
                        .flatMap(row -> row.cells().stream())
                        .anyMatch(cell -> cell.exactNumeric() != null)) invalid();
        verify(document, references, targetDigest);
        return document;
    }

    /**
     * Seals a verified witness document for storage under the active installation key. A
     * missing key refuses ({@code CDS_NATIVE_BASELINE_KEY_UNAVAILABLE}); nothing is stored
     * unsealed.
     */
    public static String seal(
            String references,
            String targetDigest,
            List<CdsNativeRowState> rows,
            CdsNativeBaselineKeys keys)
            throws IOException {
        java.util.Objects.requireNonNull(keys);
        byte[] plaintext =
                deflate(encode(references, targetDigest, rows).getBytes(StandardCharsets.UTF_8));
        String keyId = keys.activeKeyId();
        byte[] nonce = new byte[12];
        NONCES.nextBytes(nonce);
        byte[] sealed;
        try {
            var cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    javax.crypto.Cipher.ENCRYPT_MODE,
                    keys.key(keyId),
                    new javax.crypto.spec.GCMParameterSpec(128, nonce));
            cipher.updateAAD(associated(DEFLATED_DOMAIN, keyId, targetDigest));
            sealed = cipher.doFinal(plaintext);
        } catch (java.security.GeneralSecurityException failure) {
            throw new IOException("CDS_NATIVE_BASELINE_SEAL_FAILED");
        }
        var encoder = java.util.Base64.getEncoder();
        String encoded =
                ENVELOPE.writeValueAsString(
                        new Envelope(
                                4,
                                keyId,
                                encoder.encodeToString(nonce),
                                encoder.encodeToString(sealed)));
        if (encoded.length() > MAX_SEALED_BYTES) invalid();
        return encoded;
    }

    /** Opens a stored witness with the key it names and verifies it against its receipt. */
    public static Document unseal(
            String stored, String references, String targetDigest, CdsNativeBaselineKeys keys)
            throws IOException {
        return decode(unsealedText(stored, references, targetDigest, keys), references, targetDigest);
    }

    /**
     * The verified plaintext document of a stored witness, for in-memory consumers that decode
     * it later with {@link #decode}. Never write the result back to storage.
     */
    public static String unsealedText(
            String stored, String references, String targetDigest, CdsNativeBaselineKeys keys)
            throws IOException {
        java.util.Objects.requireNonNull(keys);
        if (stored == null
                || stored.isEmpty()
                || stored.length() > MAX_SEALED_BYTES
                || targetDigest == null
                || !targetDigest.matches("[0-9a-f]{64}")) invalid();
        JsonNode tree;
        try {
            tree = ENVELOPE.readTree(stored);
        } catch (com.fasterxml.jackson.core.JsonProcessingException malformed) {
            // Not one flat envelope within its bounds (an unsealed document included): a stored
            // witness that cannot be trusted, not an I/O failure.
            throw new IllegalArgumentException("CDS_NATIVE_BASELINE_REQUIRES_REVIEW");
        }
        // An unsealed (protocol 1 or 2) document is refused: no released build stored one,
        // and accepting it would let a database writer replace a sealed witness unnoticed.
        exact(tree, Set.of("protocol", "keyId", "nonce", "sealed"));
        if (!tree.get("protocol").isIntegralNumber()
                || !tree.get("protocol").canConvertToInt()
                || tree.get("protocol").intValue() != 3 && tree.get("protocol").intValue() != 4)
            invalid();
        boolean deflated = tree.get("protocol").intValue() == 4;
        String keyId = text(tree, "keyId");
        byte[] nonce, sealed;
        try {
            var decoder = java.util.Base64.getDecoder();
            nonce = decoder.decode(text(tree, "nonce"));
            sealed = decoder.decode(text(tree, "sealed"));
        } catch (IllegalArgumentException malformed) {
            throw new IllegalArgumentException("CDS_NATIVE_BASELINE_REQUIRES_REVIEW");
        }
        if (nonce.length != 12 || sealed.length < 16) invalid();
        var key = keys.key(keyId);
        byte[] plaintext;
        try {
            var cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    javax.crypto.Cipher.DECRYPT_MODE,
                    key,
                    new javax.crypto.spec.GCMParameterSpec(128, nonce));
            cipher.updateAAD(
                    associated(deflated ? DEFLATED_DOMAIN : SEALED_DOMAIN, keyId, targetDigest));
            plaintext = cipher.doFinal(sealed);
        } catch (javax.crypto.AEADBadTagException tampered) {
            throw new IllegalArgumentException("CDS_NATIVE_BASELINE_REQUIRES_REVIEW");
        } catch (java.security.GeneralSecurityException failure) {
            throw new IOException("CDS_NATIVE_BASELINE_SEAL_FAILED");
        }
        if (deflated) plaintext = inflate(plaintext);
        String text;
        try {
            text =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .decode(java.nio.ByteBuffer.wrap(plaintext))
                            .toString();
        } catch (java.nio.charset.CharacterCodingException malformed) {
            throw new IllegalArgumentException("CDS_NATIVE_BASELINE_REQUIRES_REVIEW");
        }
        decode(text, references, targetDigest);
        return text;
    }

    private static byte[] associated(String domain, String keyId, String targetDigest) {
        return (domain + "\0" + keyId + "\0" + targetDigest).getBytes(StandardCharsets.UTF_8);
    }

    /** A zlib stream of the document. */
    private static byte[] deflate(byte[] document) {
        // Closing (Java 25: AutoCloseable) releases the native zlib state, as end() did.
        try (var deflater = new java.util.zip.Deflater(java.util.zip.Deflater.DEFAULT_COMPRESSION)) {
            deflater.setInput(document);
            deflater.finish();
            var output = new java.io.ByteArrayOutputStream(Math.max(64, document.length / 3));
            byte[] buffer = new byte[16384];
            while (!deflater.finished()) output.write(buffer, 0, deflater.deflate(buffer));
            return output.toByteArray();
        }
    }

    /**
     * The authenticated zlib stream of a protocol-4 witness, refused rather than truncated when it
     * is malformed, ends early or carries trailing bytes. It was authenticated first, so it is
     * the importer's own document, inflated whole whatever its size (up to the VM's own limit).
     */
    private static byte[] inflate(byte[] compressed) {
        try (var inflater = new java.util.zip.Inflater()) {
            inflater.setInput(compressed);
            var output =
                    new java.io.ByteArrayOutputStream(
                            (int) Math.min(MAX_BYTES, 4L * compressed.length + 64));
            byte[] buffer = new byte[16384];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) invalid();
                if ((long) output.size() + count > MAX_BYTES) invalid();
                output.write(buffer, 0, count);
            }
            if (inflater.getRemaining() != 0) invalid();
            return output.toByteArray();
        } catch (java.util.zip.DataFormatException malformed) {
            throw new IllegalArgumentException("CDS_NATIVE_BASELINE_REQUIRES_REVIEW");
        }
    }

    // FindSecBugs UNSAFE_HASH_EQUALS: SHA-256 of an import unit's receipt reference list and of native rows, compared with the digests recorded beside them in the same ledger; integrity of stored evidence, not a secret.
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "UNSAFE_HASH_EQUALS", justification = "SHA-256 of an import unit's receipt reference list and of native rows, compared with the digests recorded beside them in the same ledger; integrity of stored evidence, not a secret")
    private static void verify(Document document, String references, String targetDigest)
            throws IOException {
        if (document == null
                || references == null
                || references.isEmpty()
                || references.length() > 65_536
                || targetDigest == null
                || !targetDigest.matches("[0-9a-f]{64}")
                || !targetDigest.equals(document.targetDigest())
                || !targetDigest.equals(sha(references))) invalid();
        JsonNode receipt = JSON.readTree(references);
        exact(receipt, Set.of("protocol", "refs"));
        if (!receipt.get("protocol").isIntegralNumber()
                || !receipt.get("protocol").canConvertToInt()
                || receipt.get("protocol").intValue() != 2
                || !receipt.get("refs").isArray()
                || receipt.get("refs").isEmpty()
                || receipt.get("refs").size() > 192) invalid();
        var expected = new HashMap<String, String>();
        for (var reference : receipt.get("refs")) {
            exact(reference, Set.of("table", "id", "digest"));
            String table = text(reference, "table"),
                    id = text(reference, "id"),
                    digest = text(reference, "digest");
            if (!table.matches("[A-Za-z0-9_]{1,64}")
                    || !id.matches("[A-Za-z0-9_-]{1,64}")
                    || !digest.matches("[0-9a-f]{64}")) invalid();
            String previous = expected.putIfAbsent(table + "/" + id, digest);
            if (previous != null && !previous.equals(digest)) invalid();
        }
        var actual = new HashMap<String, String>();
        for (var row : document.rows())
            if (actual.putIfAbsent(row.table() + "/" + row.id(), row.digest()) != null) invalid();
        if (!actual.equals(expected)) invalid();
    }

    private static void exact(JsonNode value, Set<String> fields) {
        if (value == null || !value.isObject() || value.size() != fields.size()) invalid();
        for (String field : fields) if (!value.has(field)) invalid();
    }

    private static String text(JsonNode value, String name) {
        if (!value.get(name).isTextual()) invalid();
        return value.get(name).textValue();
    }

    private static String sha(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("CDS_NATIVE_BASELINE_SHA256_UNAVAILABLE");
        }
    }

    private static void invalid() {
        throw new IllegalArgumentException("CDS_NATIVE_BASELINE_REQUIRES_REVIEW");
    }
}
