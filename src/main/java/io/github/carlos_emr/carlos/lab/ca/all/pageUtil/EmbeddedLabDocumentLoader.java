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
 *
 * The inline lab PDF display is adapted from open-osp/Open-O commits
 * 4b5a3d62e6 and b63af33e90 (GPL); this CARLOS implementation adds the
 * per-OBX detection, PDF signature check and size handling below.
 */
package io.github.carlos_emr.carlos.lab.ca.all.pageUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

import org.apache.commons.codec.binary.Base64InputStream;

import io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.PATHL7Handler;

/**
 * Decodes and classifies the document an HL7 {@code ED} OBX carries, for the lab display page and
 * the two endpoints that serve it ({@link ViewEmbeddedDocumentFromLab2Action} inline,
 * {@link DownloadEmbeddedDocumentFromLab2Action} as an attachment).
 *
 * <p>All three read the payload through this class so the page never offers a preview or a
 * download link the endpoints would then refuse. Only content that starts with the PDF signature
 * ({@code %PDF-}) is ever classed as a PDF: an {@code ED} payload is sender-controlled, and
 * serving anything else as {@code application/pdf} from the CARLOS origin would let a crafted
 * message choose what the browser renders.</p>
 *
 * <p>Decoding follows the declared HL7 ED.4 encoding when the parser exposes it
 * ({@link MessageHandler#getOBXDocumentEncoding(int, int)}): {@code A} is text ({@link Status#TEXT}),
 * {@code Hex} is hex octets, anything else is base64. Base64 is decoded strictly (RFC 4648 after
 * dropping the line breaks senders wrap at 76 or 80 columns); if that fails, the lenient decoder
 * the former download action used is tried so a payload that downloaded before still downloads,
 * and the PDF signature check still applies to its output.</p>
 *
 * <p>The payload is {@link MessageHandler#getOBXEmbeddedDocumentData(int, int)}: ED.5 where the
 * parser can read it, since most handlers' {@code getOBXResult} returns OBX-5 component 1, which
 * for a standards-compliant {@code ED} value is not the document.</p>
 *
 * <p>Legacy PATHL7 PDFs keep the payload in ED.1 with ED.2 to ED.5 empty; that shape is detected
 * here from the message ({@link PATHL7Handler#isLegacy(int, int)}), never from a request flag.</p>
 *
 * @since 2026-09-30
 */
public final class EmbeddedLabDocumentLoader {

    /** The PDF file signature every served document must start with. */
    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    /**
     * How much of an over-limit payload is decoded to read its signature: far more than the
     * eight base64 (or ten hex) characters the five signature bytes need, so stray characters the
     * lenient decoder skips near the start do not hide it, and still a fixed, small amount.
     */
    private static final int HEAD_PREFIX_CHARS = 1024;

    /** Classification of an embedded document. */
    public enum Status {
        /** A PDF within the size limit. */
        PDF,
        /** A PDF larger than the size limit; download only. */
        TOO_LARGE,
        /** Content that does not start with the PDF signature, or that cannot be decoded. */
        NOT_PDF,
        /**
         * A payload the sender declares as text (ED.4 {@code A}), or, with no declared encoding, a
         * non-PDF payload not even shaped like base64 (legacy text in OBX-5.1); readable as the
         * result value.
         */
        TEXT,
        /** No payload at all. */
        EMPTY
    }

    /**
     * An inspected document: its status and decoded size, without keeping the bytes.
     *
     * @param status the classification
     * @param sizeBytes the decoded size in bytes, estimated from the encoded length for a
     *                  document over the limit (which is not decoded in full), {@code 0} when empty
     */
    public record Inspection(Status status, long sizeBytes) {

        /** Whether the document is a PDF, previewable or not. */
        public boolean isPdf() {
            return status == Status.PDF || status == Status.TOO_LARGE;
        }

        /**
         * Whether the payload is binary content that is not a PDF (an image, say): the page can
         * neither serve it nor usefully print it, so it shows a short note instead of the
         * encoded bytes.
         */
        public boolean isUndisplayable() {
            return status == Status.NOT_PDF;
        }
    }

    /**
     * A loaded document.
     *
     * @param status the classification
     * @param bytes the PDF bytes when {@code status} is {@link Status#PDF}; otherwise {@code null}
     * @param sizeBytes as for {@link Inspection#sizeBytes()}
     */
    public record Document(Status status, byte[] bytes, long sizeBytes) {

        @Override
        public boolean equals(Object other) {
            return other instanceof Document that && status == that.status && sizeBytes == that.sizeBytes
                    && Arrays.equals(bytes, that.bytes);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hash(status, sizeBytes) + Arrays.hashCode(bytes);
        }

        /** Never prints the document content, only its size. */
        @Override
        public String toString() {
            return "Document[status=" + status + ", sizeBytes=" + sizeBytes + "]";
        }
    }

    private EmbeddedLabDocumentLoader() {
    }

    /**
     * Classifies the document in the given OBX for the lab display page, without keeping it: the
     * payload is decoded as a stream into a byte counter, and only the first five bytes (the PDF
     * signature) are retained. A payload whose encoded length alone puts it over {@code maxBytes}
     * is not decoded in full: only a short prefix is, for the signature, and its size is estimated
     * from the encoded length. {@link #load} runs the same classification first, so the page never
     * offers a preview or download the endpoints would refuse.
     *
     * @param handler the parsed lab
     * @param obr the OBR group index
     * @param obx the OBX index within the group
     * @param maxBytes the largest size classed as {@link Status#PDF}; {@code 0} or less for no limit
     * @return the inspection; never {@code null}
     */
    public static Inspection inspect(MessageHandler handler, int obr, int obx, long maxBytes) {
        Classified classified = classify(handler, obr, obx, maxBytes);
        return new Inspection(classified.status(), classified.sizeBytes());
    }

    /**
     * Decodes the document in the given OBX.
     *
     * @param handler the parsed lab
     * @param obr the OBR group index
     * @param obx the OBX index within the group
     * @param maxBytes the largest size returned as {@link Status#PDF}; {@code 0} or less for no
     *                 limit (the download path, where the whole message is already in memory)
     * @return the document, with bytes only for {@link Status#PDF}; never {@code null}
     */
    public static Document load(MessageHandler handler, int obr, int obx, long maxBytes) {
        Classified classified = classify(handler, obr, obx, maxBytes);
        if (classified.status() != Status.PDF) {
            return new Document(classified.status(), null, classified.sizeBytes());
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream((int) Math.min(classified.sizeBytes(), (long) Integer.MAX_VALUE - 8));
        decode(classified.compact(), classified.hex(), new byte[PDF_SIGNATURE.length], bytes, false);
        return new Document(Status.PDF, bytes.toByteArray(), classified.sizeBytes());
    }

    /** A classification, carrying the normalised payload so {@link #load} decodes it only once more. */
    private record Classified(Status status, long sizeBytes, String compact, boolean hex) {
    }

    private static Classified classify(MessageHandler handler, int obr, int obx, long maxBytes) {
        String payload = payload(handler, obr, obx);
        if (payload == null || payload.isBlank()) {
            return new Classified(Status.EMPTY, 0, null, false);
        }
        String encoding = handler.getOBXDocumentEncoding(obr, obx);
        if ("A".equals(encoding)) {
            // Declared as text (for example PATHL7 CELLPATHR RTF in ED.1): never a PDF, but
            // unlike an undecodable binary it is readable as the result value.
            return new Classified(Status.TEXT, payload.length(), null, false);
        }
        String compact = payload.replaceAll("\\s+", "");
        boolean hex = "Hex".equals(encoding);
        byte[] head = new byte[PDF_SIGNATURE.length];
        if (maxBytes > 0) {
            long estimated = estimateDecodedSize(compact, hex);
            if (estimated > maxBytes) {
                // Over the limit by its encoded length alone: decode only far enough to read the
                // signature, so an oversized payload costs no more than a small one.
                long headSize = decode(compact, hex, head, null, true);
                if (headSize < PDF_SIGNATURE.length || !isPdf(head)) {
                    return notPdf(encoding, compact, payload, headSize < 0 ? 0 : estimated);
                }
                return new Classified(Status.TOO_LARGE, estimated, null, false);
            }
        }
        long size = decode(compact, hex, head, null, false);
        if (size < PDF_SIGNATURE.length || !isPdf(head)) {
            return notPdf(encoding, compact, payload, Math.max(size, 0));
        }
        if (maxBytes > 0 && size > maxBytes) {
            return new Classified(Status.TOO_LARGE, size, null, false);
        }
        return new Classified(Status.PDF, size, compact, hex);
    }

    /**
     * The decoded size implied by the encoded length, without decoding: half the characters for
     * hex (an unmatched final character is dropped, as {@link #decodeHex} does), and three bytes
     * per four base64-alphabet characters otherwise. Counting only alphabet characters (standard
     * or URL-safe, never {@code =} padding) up to the first padding character makes this exact
     * both for strict base64 and for the lenient decoder, which skips every other character and
     * stops at padding.
     */
    static long estimateDecodedSize(String compact, boolean hex) {
        if (hex) {
            return compact.length() / 2;
        }
        long alphabet = 0;
        for (int i = 0; i < compact.length(); i++) {
            char c = compact.charAt(i);
            if (c == '=') {
                // Padding ends the data: the lenient decoder the full decode falls back to stops
                // here, so anything after it must not count towards the size either.
                break;
            }
            if (isBase64Alphabet(c)) {
                alphabet++;
            }
        }
        return alphabet * 3 / 4;
    }

    /**
     * A payload that is not a PDF. With no declared ED.4 encoding (a legacy feed that puts the
     * value in OBX-5.1) and a payload that is not even shaped like base64, it is the sender's
     * text, shown as the result value as it was before ED documents were detected; otherwise it
     * is undisplayable binary (an image, say). Base64-shaped text stays {@link Status#NOT_PDF}:
     * without a declared encoding it cannot be told from encoded bytes.
     */
    private static Classified notPdf(String encoding, String compact, String payload, long sizeBytes) {
        if (encoding == null && !isBase64Shaped(compact)) {
            return new Classified(Status.TEXT, payload.length(), null, false);
        }
        return new Classified(Status.NOT_PDF, sizeBytes, null, false);
    }

    /**
     * Whether {@code compact} has the shape of strict standard base64: only alphabet characters,
     * a length that is a multiple of four, and at most two {@code =} only at the end.
     */
    static boolean isBase64Shaped(String compact) {
        int length = compact.length();
        if (length == 0 || length % 4 != 0) {
            return false;
        }
        int padding = compact.endsWith("==") ? 2 : compact.endsWith("=") ? 1 : 0;
        for (int i = 0; i < length - padding; i++) {
            char c = compact.charAt(i);
            if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '+' || c == '/')) {
                return false;
            }
        }
        return true;
    }

    /** Whether the bytes start with the PDF signature {@code %PDF-}. */
    static boolean isPdf(byte[] bytes) {
        if (bytes == null || bytes.length < PDF_SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < PDF_SIGNATURE.length; i++) {
            if (bytes[i] != PDF_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    private static String payload(MessageHandler handler, int obr, int obx) {
        if (handler instanceof PATHL7Handler pathL7 && pathL7.isLegacy(obr, obx)) {
            return pathL7.getLegacyOBXResult(obr, obx);
        }
        return handler.getOBXEmbeddedDocumentData(obr, obx);
    }

    /**
     * Decodes {@code compact} as a stream, copying the first bytes into {@code head} and, when
     * {@code sink} is not {@code null}, every byte into it.
     *
     * <p>Base64 is decoded strictly (RFC 4648; the caller has already dropped the line breaks
     * senders wrap at 76 or 80 columns). If that fails, the lenient commons-codec decoder, which
     * skips any non-alphabet byte, is tried: it is what the former download action used, so a
     * payload that downloaded before still does. The caller still requires the PDF signature, so
     * the fallback cannot turn text into something served as a PDF. Hex drops an unmatched final
     * character.</p>
     *
     * <p>With {@code headOnly} (and no sink) decoding stops once {@code head} is filled, so only a
     * bounded prefix of an oversized payload is ever decoded; the returned size is then that
     * prefix's, not the document's.</p>
     *
     * @return the decoded size in bytes, or {@code -1} when the payload cannot be decoded
     */
    private static long decode(String compact, boolean hex, byte[] head, ByteArrayOutputStream sink, boolean headOnly) {
        // Hex needs two characters per byte, base64 four per three: a fixed prefix far longer than
        // the signature needs. For base64 the prefix is counted in alphabet characters, because
        // the lenient decoder the full decode falls back to skips everything else; a raw prefix
        // could then end before the data and disagree with the uncapped path. Hex is not
        // lenient (any non-digit fails the full decode too), so a raw prefix already agrees.
        String input = compact;
        if (headOnly && sink == null) {
            input = hex ? compact.substring(0, Math.min(compact.length(), HEAD_PREFIX_CHARS)) : base64HeadInput(compact);
        }
        if (hex) {
            return decodeHex(input, head, sink);
        }
        byte[] ascii = input.getBytes(StandardCharsets.ISO_8859_1);
        if (headOnly && sink == null) {
            // A prefix cut mid-quantum is not strict base64; the lenient decoder reads it, and
            // the signature check below still decides.
            try (InputStream in = new Base64InputStream(new ByteArrayInputStream(ascii))) {
                return drain(in, head, null);
            } catch (IOException | IllegalArgumentException notBase64) {
                return -1;
            }
        }
        try (InputStream in = Base64.getDecoder().wrap(new ByteArrayInputStream(ascii))) {
            return drain(in, head, sink);
        } catch (IOException | IllegalArgumentException notStrictBase64) {
            Arrays.fill(head, (byte) 0);
            if (sink != null) {
                sink.reset();
            }
            try (InputStream in = new Base64InputStream(new ByteArrayInputStream(ascii))) {
                return drain(in, head, sink);
            } catch (IOException | IllegalArgumentException notBase64) {
                return -1;
            }
        }
    }

    /**
     * The first {@link #HEAD_PREFIX_CHARS} base64-alphabet characters of {@code compact}, skipping
     * every other character exactly as the lenient decoder does, and stopping at {@code =}
     * padding, where that decoder stops. One linear scan with a small, fixed-size result.
     */
    static String base64HeadInput(String compact) {
        StringBuilder head = new StringBuilder(HEAD_PREFIX_CHARS);
        for (int i = 0; i < compact.length() && head.length() < HEAD_PREFIX_CHARS; i++) {
            char c = compact.charAt(i);
            if (c == '=') {
                head.append(c);
                break;
            }
            if (isBase64Alphabet(c)) {
                head.append(c);
            }
        }
        return head.toString();
    }

    private static boolean isBase64Alphabet(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                || c == '+' || c == '/' || c == '-' || c == '_';
    }

    private static long drain(InputStream in, byte[] head, ByteArrayOutputStream sink) throws IOException {
        byte[] buffer = new byte[8192];
        long size = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            if (size < head.length) {
                System.arraycopy(buffer, 0, head, (int) size, (int) Math.min(read, head.length - size));
            }
            if (sink != null) {
                sink.write(buffer, 0, read);
            }
            size += read;
        }
        return size;
    }

    private static long decodeHex(String compact, byte[] head, ByteArrayOutputStream sink) {
        int pairs = compact.length() / 2;
        for (int i = 0; i < pairs; i++) {
            int high = Character.digit(compact.charAt(2 * i), 16);
            int low = Character.digit(compact.charAt(2 * i + 1), 16);
            if (high < 0 || low < 0) {
                return -1;
            }
            byte value = (byte) ((high << 4) | low);
            if (i < head.length) {
                head[i] = value;
            }
            if (sink != null) {
                sink.write(value);
            }
        }
        return pairs;
    }
}
