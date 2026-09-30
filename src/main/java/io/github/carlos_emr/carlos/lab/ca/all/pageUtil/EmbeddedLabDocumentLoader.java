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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

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
 * ({@link MessageHandler#getOBXDocumentEncoding(int, int)}): {@code A} is text and never a PDF,
 * {@code Hex} is hex octets, anything else is base64. Base64 is decoded strictly (RFC 4648 after
 * dropping the line breaks senders wrap at 76 or 80 columns); if that fails, the lenient decoder
 * the former download action used is tried so a payload that downloaded before still downloads,
 * and the PDF signature check still applies to its output.</p>
 *
 * <p>Legacy PATHL7 PDFs keep the payload in ED.1 with ED.2 to ED.5 empty; that shape is detected
 * here from the message ({@link PATHL7Handler#isLegacy(int, int)}), never from a request flag.</p>
 *
 * @since 2026-09-30
 */
public final class EmbeddedLabDocumentLoader {

    /** The PDF file signature every served document must start with. */
    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    /** Base64 characters needed to decode at least {@link #PDF_SIGNATURE}'s five bytes. */
    private static final int BASE64_SIGNATURE_CHARS = 8;

    /** Classification of an embedded document. */
    public enum Status {
        /** A PDF within the size limit. */
        PDF,
        /** A PDF larger than the size limit; download only. */
        TOO_LARGE,
        /** Content that does not start with the PDF signature, or that cannot be decoded. */
        NOT_PDF,
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
    }

    /**
     * A loaded document.
     *
     * @param status the classification
     * @param bytes the PDF bytes when {@code status} is {@link Status#PDF}; otherwise {@code null}
     * @param sizeBytes as for {@link Inspection#sizeBytes()}
     */
    public record Document(Status status, byte[] bytes, long sizeBytes) {
    }

    private EmbeddedLabDocumentLoader() {
    }

    /**
     * Classifies the document in the given OBX for the lab display page. A document within the
     * limit is decoded in full, so the page offers exactly what {@link #load} would serve; one over
     * the limit only has its first bytes decoded to check the signature.
     *
     * @param handler the parsed lab
     * @param obr the OBR group index
     * @param obx the OBX index within the group
     * @param maxBytes the largest size classed as {@link Status#PDF}; {@code 0} or less for no limit
     * @return the inspection; never {@code null}
     */
    public static Inspection inspect(MessageHandler handler, int obr, int obx, long maxBytes) {
        Document document = load(handler, obr, obx, maxBytes);
        return new Inspection(document.status(), document.sizeBytes());
    }

    /**
     * Decodes the document in the given OBX.
     *
     * @param handler the parsed lab
     * @param obr the OBR group index
     * @param obx the OBX index within the group
     * @param maxBytes the largest size returned as {@link Status#PDF}; {@code 0} or less for no
     *                 limit (the download path, where the whole message is already in memory)
     * @return the document; never {@code null}
     */
    public static Document load(MessageHandler handler, int obr, int obx, long maxBytes) {
        String payload = payload(handler, obr, obx);
        if (payload == null || payload.isBlank()) {
            return new Document(Status.EMPTY, null, 0);
        }
        String encoding = handler.getOBXDocumentEncoding(obr, obx);
        if ("A".equals(encoding)) {
            // Declared as text (for example PATHL7 CELLPATHR RTF in ED.1): not a PDF.
            return new Document(Status.NOT_PDF, null, payload.length());
        }
        String compact = payload.replaceAll("\\s+", "");
        boolean hex = "Hex".equals(encoding);
        long estimatedSize = hex ? compact.length() / 2L : base64DecodedLength(compact);
        if (maxBytes > 0 && estimatedSize > maxBytes) {
            byte[] head = hex ? decodeHex(compact.substring(0, Math.min(compact.length(), PDF_SIGNATURE.length * 2)))
                    : decodeBase64(compact.substring(0, Math.min(compact.length(), BASE64_SIGNATURE_CHARS)));
            return new Document(isPdf(head) ? Status.TOO_LARGE : Status.NOT_PDF, null, estimatedSize);
        }
        byte[] bytes = hex ? decodeHex(compact) : decodeBase64(compact);
        if (!isPdf(bytes)) {
            return new Document(Status.NOT_PDF, null, bytes == null ? 0 : bytes.length);
        }
        if (maxBytes > 0 && bytes.length > maxBytes) {
            return new Document(Status.TOO_LARGE, null, bytes.length);
        }
        return new Document(Status.PDF, bytes, bytes.length);
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
        return handler.getOBXResult(obr, obx);
    }

    private static long base64DecodedLength(String compact) {
        int padding = compact.endsWith("==") ? 2 : compact.endsWith("=") ? 1 : 0;
        return Math.max(0L, compact.length() / 4L * 3L - padding);
    }

    /**
     * Strict base64 first; the lenient commons-codec decoder (which skips any non-alphabet byte)
     * only as the fallback the former download action relied on. The caller still requires the
     * PDF signature, so the fallback cannot turn text into something served as a PDF.
     */
    private static byte[] decodeBase64(String compact) {
        try {
            return Base64.getDecoder().decode(compact);
        } catch (IllegalArgumentException notStrictBase64) {
            return org.apache.commons.codec.binary.Base64.decodeBase64(compact);
        }
    }

    private static byte[] decodeHex(String compact) {
        try {
            return HexFormat.of().parseHex(compact.length() % 2 == 0 ? compact : compact.substring(0, compact.length() - 1));
        } catch (IllegalArgumentException notHex) {
            return null;
        }
    }
}
