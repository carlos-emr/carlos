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
package io.github.carlos_emr.carlos.lab.ca.all.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

import io.github.carlos_emr.carlos.lab.ca.all.pageUtil.EmbeddedLabDocumentLoader.Document;
import io.github.carlos_emr.carlos.lab.ca.all.pageUtil.EmbeddedLabDocumentLoader.Status;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.PATHL7Handler;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.PathL7EmbeddedDocumentMessage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EmbeddedLabDocumentLoader}: only content carrying the PDF signature is
 * classed as a PDF, the size limit yields {@link Status#TOO_LARGE} without a full decode, and the
 * declared ED.4 encoding and the legacy PATHL7 shape are honoured.
 *
 * @since 2026-09-30
 */
@Tag("unit")
@Tag("fast")
@Tag("lab")
@DisplayName("EmbeddedLabDocumentLoader")
class EmbeddedLabDocumentLoaderUnitTest {

    private static final byte[] PDF = PathL7EmbeddedDocumentMessage.PDF;
    private static final String PDF_BASE64 = Base64.getEncoder().encodeToString(PDF);

    private static PATHL7Handler pathL7(String message) throws Exception {
        PATHL7Handler handler = new PATHL7Handler();
        handler.init(message);
        return handler;
    }

    private static String withEdValue(String edValue) {
        return PathL7EmbeddedDocumentMessage.message().replace("^TEXT^PDF^Base64^" + PDF_BASE64, edValue);
    }

    @Test
    @DisplayName("should load the PDF from a base64 ED segment")
    void shouldLoadPdf_fromBase64EdSegment() throws Exception {
        Document document = EmbeddedLabDocumentLoader.load(pathL7(PathL7EmbeddedDocumentMessage.message()), 1, 0, 0);

        assertThat(document.status()).isEqualTo(Status.PDF);
        assertThat(document.bytes()).isEqualTo(PDF);
        assertThat(document.sizeBytes()).isEqualTo(PDF.length);
    }

    @Test
    @DisplayName("should decode base64 that the sender wrapped across lines")
    void shouldLoadPdf_whenBase64IsWrapped() {
        MessageHandler handler = handlerReturning(PDF_BASE64.substring(0, 20) + "\n  " + PDF_BASE64.substring(20), "Base64");

        assertThat(EmbeddedLabDocumentLoader.load(handler, 0, 0, 0).bytes()).isEqualTo(PDF);
    }

    @Test
    @DisplayName("should refuse decoded content that is not a PDF")
    void shouldClassifyNotPdf_whenSignatureIsMissing() throws Exception {
        String html = Base64.getEncoder().encodeToString("<html><script>alert(1)</script></html>".getBytes(StandardCharsets.US_ASCII));
        PATHL7Handler handler = pathL7(withEdValue("^TEXT^PDF^Base64^" + html));

        Document document = EmbeddedLabDocumentLoader.load(handler, 1, 0, 0);

        assertThat(document.status()).isEqualTo(Status.NOT_PDF);
        assertThat(document.bytes()).isNull();
    }

    @Test
    @DisplayName("should classify an over-limit PDF as too large from its first bytes only")
    void shouldClassifyTooLarge_whenPdfExceedsLimit() throws Exception {
        PATHL7Handler handler = pathL7(PathL7EmbeddedDocumentMessage.message());

        Document document = EmbeddedLabDocumentLoader.load(handler, 1, 0, 10);
        EmbeddedLabDocumentLoader.Inspection inspection = EmbeddedLabDocumentLoader.inspect(handler, 1, 0, 10);

        assertThat(document.status()).isEqualTo(Status.TOO_LARGE);
        assertThat(document.bytes()).isNull();
        assertThat(document.sizeBytes()).isEqualTo(PDF.length);
        assertThat(inspection.isPdf()).isTrue();
        assertThat(inspection.status()).isEqualTo(Status.TOO_LARGE);
    }

    @Test
    @DisplayName("should classify an over-limit payload without the PDF signature as not a PDF")
    void shouldClassifyNotPdf_whenOverLimitPayloadIsNotPdf() {
        String text = Base64.getEncoder().encodeToString("x".repeat(400).getBytes(StandardCharsets.US_ASCII));
        MessageHandler handler = handlerReturning(text, "Base64");

        assertThat(EmbeddedLabDocumentLoader.inspect(handler, 0, 0, 10).status()).isEqualTo(Status.NOT_PDF);
    }

    @Test
    @DisplayName("should serve a PDF exactly at the limit")
    void shouldLoadPdf_whenSizeEqualsLimit() throws Exception {
        Document document = EmbeddedLabDocumentLoader.load(pathL7(PathL7EmbeddedDocumentMessage.message()), 1, 0, PDF.length);

        assertThat(document.status()).isEqualTo(Status.PDF);
    }

    @Test
    @DisplayName("should decode a payload declared as Hex")
    void shouldLoadPdf_fromHexEncoding() throws Exception {
        PATHL7Handler handler = pathL7(withEdValue("^TEXT^PDF^Hex^" + HexFormat.of().formatHex(PDF)));

        assertThat(handler.getOBXDocumentEncoding(1, 0)).isEqualTo("Hex");
        assertThat(EmbeddedLabDocumentLoader.load(handler, 1, 0, 0).bytes()).isEqualTo(PDF);
    }

    @Test
    @DisplayName("should never class a payload declared as text (A) as a PDF")
    void shouldClassifyText_forTextEncoding() {
        MessageHandler handler = handlerReturning("%PDF-1.4 not really", "A");

        EmbeddedLabDocumentLoader.Inspection inspection = EmbeddedLabDocumentLoader.inspect(handler, 0, 0, 0);
        assertThat(inspection.status()).isEqualTo(Status.TEXT);
        assertThat(inspection.isPdf()).isFalse();
        assertThat(inspection.isUndisplayable()).isFalse();
    }

    @Test
    @DisplayName("should report a binary payload that is not a PDF as undisplayable")
    void shouldFlagUndisplayable_forBinaryNonPdf() {
        String png = Base64.getEncoder().encodeToString(new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
        EmbeddedLabDocumentLoader.Inspection inspection = EmbeddedLabDocumentLoader.inspect(handlerReturning(png, null), 0, 0, 0);

        assertThat(inspection.isUndisplayable()).isTrue();
        assertThat(inspection.isPdf()).isFalse();
    }

    @Test
    @DisplayName("should report an empty payload as empty")
    void shouldClassifyEmpty_whenPayloadIsBlank() {
        assertThat(EmbeddedLabDocumentLoader.load(handlerReturning("  ", null), 0, 0, 0).status()).isEqualTo(Status.EMPTY);
        assertThat(EmbeddedLabDocumentLoader.load(handlerReturning(null, null), 0, 0, 0).status()).isEqualTo(Status.EMPTY);
    }

    @Test
    @DisplayName("should fall back to lenient base64 and still require the PDF signature")
    void shouldLoadPdf_whenStrictBase64Fails() {
        // A stray non-alphabet character defeats the strict decoder; the lenient one skips it.
        MessageHandler handler = handlerReturning(PDF_BASE64.substring(0, 8) + "*" + PDF_BASE64.substring(8), null);

        assertThat(EmbeddedLabDocumentLoader.load(handler, 0, 0, 0).bytes()).isEqualTo(PDF);
    }

    @Test
    @DisplayName("should classify a lenient payload over the limit as a PDF, not by a raw prefix")
    void shouldClassifyTooLarge_whenLenientPayloadExceedsLimit() {
        // A stray character inside the first eight Base64 characters: a raw prefix decode would
        // miss the signature, the lenient decode of the whole value does not.
        MessageHandler handler = handlerReturning(PDF_BASE64.substring(0, 3) + "*" + PDF_BASE64.substring(3), null);

        EmbeddedLabDocumentLoader.Inspection inspection = EmbeddedLabDocumentLoader.inspect(handler, 0, 0, 10);

        assertThat(inspection.status()).isEqualTo(Status.TOO_LARGE);
        assertThat(inspection.sizeBytes()).isEqualTo(PDF.length);
        assertThat(EmbeddedLabDocumentLoader.load(handler, 0, 0, 0).bytes()).isEqualTo(PDF);
    }

    @Test
    @DisplayName("should agree between inspect and load for every classification")
    void shouldAgree_betweenInspectAndLoad() {
        String hexPdf = HexFormat.of().formatHex(PDF);
        MessageHandler[] handlers = {
                handlerReturning(PDF_BASE64, "Base64"),
                handlerReturning(hexPdf, "Hex"),
                handlerReturning(hexPdf + "a", "Hex"),
                handlerReturning(hexPdf.replace('0', 'z'), "Hex"),
                handlerReturning("not base64 at all %%%", null),
                handlerReturning("%PDF-1.4 text", "A"),
                handlerReturning("", null),
        };
        for (MessageHandler handler : handlers) {
            for (long limit : new long[] {0, 10, PDF.length}) {
                Document document = EmbeddedLabDocumentLoader.load(handler, 0, 0, limit);
                EmbeddedLabDocumentLoader.Inspection inspection = EmbeddedLabDocumentLoader.inspect(handler, 0, 0, limit);
                assertThat(inspection.status()).isEqualTo(document.status());
                assertThat(inspection.sizeBytes()).isEqualTo(document.sizeBytes());
            }
        }
        assertThat(EmbeddedLabDocumentLoader.load(handlerReturning(hexPdf + "a", "Hex"), 0, 0, 0).bytes()).isEqualTo(PDF);
        assertThat(EmbeddedLabDocumentLoader.inspect(handlerReturning(hexPdf.replace('0', 'z'), "Hex"), 0, 0, 0).status())
                .isEqualTo(Status.NOT_PDF);
    }

    @Test
    @DisplayName("should read the legacy PATHL7 PDF from ED.1")
    void shouldLoadPdf_fromLegacyPathL7Shape() throws Exception {
        PATHL7Handler handler = pathL7(withEdValue(PDF_BASE64));

        assertThat(handler.isLegacy(1, 0)).isTrue();
        Document document = EmbeddedLabDocumentLoader.load(handler, 1, 0, 0);

        assertThat(document.status()).isEqualTo(Status.PDF);
        assertThat(document.bytes()).isEqualTo(PDF);
    }

    @Test
    @DisplayName("should require the full five-byte PDF signature")
    void shouldRequireFullSignature_forPdfCheck() {
        assertThat(EmbeddedLabDocumentLoader.isPdf("%PDF-".getBytes(StandardCharsets.US_ASCII))).isTrue();
        assertThat(EmbeddedLabDocumentLoader.isPdf("%PDF".getBytes(StandardCharsets.US_ASCII))).isFalse();
        assertThat(EmbeddedLabDocumentLoader.isPdf(" %PDF-".getBytes(StandardCharsets.US_ASCII))).isFalse();
        assertThat(EmbeddedLabDocumentLoader.isPdf(null)).isFalse();
    }

    private static MessageHandler handlerReturning(String payload, String encoding) {
        MessageHandler handler = mock(MessageHandler.class, CALLS_REAL_METHODS);
        when(handler.getOBXEmbeddedDocumentData(0, 0)).thenReturn(payload);
        when(handler.getOBXDocumentEncoding(0, 0)).thenReturn(encoding);
        return handler;
    }
}
