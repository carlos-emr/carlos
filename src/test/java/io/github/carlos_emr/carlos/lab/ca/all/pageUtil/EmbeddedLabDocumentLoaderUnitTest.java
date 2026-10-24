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
    @DisplayName("should size an over-limit hex PDF from its encoded length")
    void shouldClassifyTooLarge_fromEncodedLengthForHex() {
        String hexPdf = HexFormat.of().formatHex(PDF).repeat(41) + "a";
        MessageHandler handler = handlerReturning(hexPdf, "Hex");

        EmbeddedLabDocumentLoader.Inspection inspection = EmbeddedLabDocumentLoader.inspect(handler, 0, 0, 10);

        assertThat(inspection.status()).isEqualTo(Status.TOO_LARGE);
        assertThat(inspection.sizeBytes()).isEqualTo(hexPdf.length() / 2);
        assertThat(EmbeddedLabDocumentLoader.load(handler, 0, 0, 0).status()).isEqualTo(Status.PDF);
    }

    @Test
    @DisplayName("should not promise an over-limit hex PDF the download path refuses")
    void shouldClassifyNotPdf_whenOverLimitHexIsCorruptPastSignature() {
        // A PDF whose hex breaks after the first kilobyte: the full decode the download endpoint
        // runs refuses it, so the page must not class it as a (too large) PDF either.
        String hexPdf = HexFormat.of().formatHex(PDF).repeat(20);
        String corruptTail = hexPdf + "zz" + hexPdf;
        MessageHandler handler = handlerReturning(corruptTail, "Hex");

        EmbeddedLabDocumentLoader.Inspection inspection = EmbeddedLabDocumentLoader.inspect(handler, 0, 0, 10);

        assertThat(EmbeddedLabDocumentLoader.load(handler, 0, 0, 0).status()).isEqualTo(Status.NOT_PDF);
        assertThat(inspection.status()).isEqualTo(Status.NOT_PDF);
        assertThat(inspection.isPdf()).isFalse();
    }

    @Test
    @DisplayName("should agree with the download path on an over-limit base64 PDF with stray characters")
    void shouldAgreeWithDownloadPath_whenOverLimitBase64HasStrayCharacters() {
        // Stray characters after the signature defeat the strict decoder; the download path's
        // lenient fallback still yields the PDF, so the capped page classification may promise it.
        String base64 = Base64.getEncoder().encodeToString((new String(PDF, StandardCharsets.US_ASCII)
                + "x".repeat(3000)).getBytes(StandardCharsets.US_ASCII));
        String corruptTail = base64.substring(0, 2000) + "*%" + base64.substring(2000);
        MessageHandler handler = handlerReturning(corruptTail, "Base64");

        assertThat(EmbeddedLabDocumentLoader.inspect(handler, 0, 0, 10).status()).isEqualTo(Status.TOO_LARGE);
        assertThat(EmbeddedLabDocumentLoader.load(handler, 0, 0, 0).status()).isEqualTo(Status.PDF);
    }

    @Test
    @DisplayName("should validate hex the way the full decode does, ignoring an unmatched final character")
    void shouldMatchDecodeHex_forDecodableHexCheck() {
        assertThat(EmbeddedLabDocumentLoader.isDecodableHex("0aFf")).isTrue();
        assertThat(EmbeddedLabDocumentLoader.isDecodableHex("0aFfz")).isTrue();
        assertThat(EmbeddedLabDocumentLoader.isDecodableHex("0azF")).isFalse();
        assertThat(EmbeddedLabDocumentLoader.isDecodableHex("")).isTrue();
    }

    @Test
    @DisplayName("should find the signature past more than a kilobyte of skipped characters, as the full decode does")
    void shouldAgreeWithUncappedDecode_whenJunkPrecedesPdf() {
        // 2000 characters the lenient decoder skips, then the PDF: the capped (over-limit) path
        // must classify it as the uncapped path does, not stop inside the junk.
        MessageHandler handler = handlerReturning("*%".repeat(1000) + PDF_BASE64, "Base64");

        assertThat(EmbeddedLabDocumentLoader.load(handler, 0, 0, 0).status()).isEqualTo(Status.PDF);
        assertThat(EmbeddedLabDocumentLoader.inspect(handler, 0, 0, 0).status()).isEqualTo(Status.PDF);
        EmbeddedLabDocumentLoader.Inspection capped = EmbeddedLabDocumentLoader.inspect(handler, 0, 0, 10);
        assertThat(capped.status()).isEqualTo(Status.TOO_LARGE);
        assertThat(capped.isPdf()).isTrue();
        assertThat(capped.sizeBytes()).isEqualTo(PDF.length);
    }

    @Test
    @DisplayName("should build the capped base64 input from alphabet characters only, stopping at padding")
    void shouldSkipNonAlphabetAndStopAtPadding_forBase64HeadInput() {
        assertThat(EmbeddedLabDocumentLoader.base64HeadInput("**QU*%JD==QUJD")).isEqualTo("QUJD=");
        assertThat(EmbeddedLabDocumentLoader.base64HeadInput("#".repeat(5000) + "A".repeat(5000))).hasSize(1024);
    }

    @Test
    @DisplayName("should size a padded PDF exactly at the limit, ignoring anything after the padding")
    void shouldClassifyPaddedPdfAtLimit_asPdf() {
        // One byte over a multiple of three, so the base64 ends in "==".
        byte[] pdf = "%PDF-1.4\n%%EOF\n!".getBytes(StandardCharsets.US_ASCII);
        assertThat(pdf.length % 3).isEqualTo(1);
        String padded = Base64.getEncoder().encodeToString(pdf);
        assertThat(padded).endsWith("==");

        for (String payload : new String[] {padded, padded + "QUJDREVG"}) {
            MessageHandler handler = handlerReturning(payload, "Base64");
            // The full decode stops at the padding, so the document is exactly pdf.length bytes.
            assertThat(EmbeddedLabDocumentLoader.load(handler, 0, 0, 0).bytes()).as(payload).isEqualTo(pdf);
            assertThat(EmbeddedLabDocumentLoader.inspect(handler, 0, 0, pdf.length).status()).as(payload).isEqualTo(Status.PDF);
            assertThat(EmbeddedLabDocumentLoader.load(handler, 0, 0, pdf.length).status()).as(payload).isEqualTo(Status.PDF);
            assertThat(EmbeddedLabDocumentLoader.inspect(handler, 0, 0, pdf.length - 1).status()).as(payload).isEqualTo(Status.TOO_LARGE);
            assertThat(EmbeddedLabDocumentLoader.inspect(handler, 0, 0, pdf.length - 1).sizeBytes()).as(payload).isEqualTo(pdf.length);
        }
    }

    @Test
    @DisplayName("should class undeclared legacy text in OBX-5.1 as text, on both the capped and uncapped paths")
    void shouldClassifyText_forUndeclaredLegacyText() {
        MessageHandler handler = handlerReturning("Specimen received; see the attached note, page 2.", null);

        for (long limit : new long[] {0, 10}) {
            assertThat(EmbeddedLabDocumentLoader.inspect(handler, 0, 0, limit).status()).as("limit %d", limit).isEqualTo(Status.TEXT);
            assertThat(EmbeddedLabDocumentLoader.load(handler, 0, 0, limit).status()).as("limit %d", limit).isEqualTo(Status.TEXT);
        }
        // A declared binary encoding is never reinterpreted as text, and base64-shaped content
        // without a declaration stays undisplayable binary.
        assertThat(EmbeddedLabDocumentLoader.inspect(handlerReturning("Specimen received; see note.", "Base64"), 0, 0, 0).status())
                .isEqualTo(Status.NOT_PDF);
        assertThat(EmbeddedLabDocumentLoader.isBase64Shaped(Base64.getEncoder().encodeToString(new byte[] {1, 2}))).isTrue();
        assertThat(EmbeddedLabDocumentLoader.isBase64Shaped("Specimenreceived;seenote.")).isFalse();
        assertThat(EmbeddedLabDocumentLoader.isBase64Shaped("QU=J")).isFalse();
    }

    @Test
    @DisplayName("should class a base64-shaped OBX-5.1 fallback that is not a PDF as text")
    void shouldClassifyText_forBase64ShapedResultFallback() {
        MessageHandler fallback = handlerReturning("NONE", null);
        when(fallback.isOBXEmbeddedDocumentResultFallback(0, 0)).thenReturn(true);
        MessageHandler undeclaredData = handlerReturning("NONE", null);

        for (long limit : new long[] {0, 1}) {
            assertThat(EmbeddedLabDocumentLoader.inspect(fallback, 0, 0, limit).status()).as("limit %d", limit).isEqualTo(Status.TEXT);
            assertThat(EmbeddedLabDocumentLoader.load(fallback, 0, 0, limit).status()).as("limit %d", limit).isEqualTo(Status.TEXT);
            // The same characters in an undeclared ED.5 cannot be told from encoded bytes.
            assertThat(EmbeddedLabDocumentLoader.inspect(undeclaredData, 0, 0, limit).status()).as("limit %d", limit).isEqualTo(Status.NOT_PDF);
        }
        // A PDF sent in OBX-5.1 by a legacy feed is still a PDF.
        MessageHandler legacyPdf = handlerReturning(PDF_BASE64, null);
        when(legacyPdf.isOBXEmbeddedDocumentResultFallback(0, 0)).thenReturn(true);
        assertThat(EmbeddedLabDocumentLoader.inspect(legacyPdf, 0, 0, 0).status()).isEqualTo(Status.PDF);
    }

    @Test
    @DisplayName("should treat base64 the decoder reads, URL-safe or unpadded, as binary and worded text as text")
    void shouldMatchDecoderAlphabet_forBase64Shape() {
        assertThat(EmbeddedLabDocumentLoader.isBase64Shaped("-_8A")).isTrue();
        assertThat(EmbeddedLabDocumentLoader.isBase64Shaped("QUJ")).isTrue();
        assertThat(EmbeddedLabDocumentLoader.isBase64Shaped("QUJDQ")).isFalse();
        assertThat(EmbeddedLabDocumentLoader.isBase64Shaped("QUJD\r\nQUJD")).isTrue();
        assertThat(EmbeddedLabDocumentLoader.isBase64Shaped("Specimen received")).isFalse();
        assertThat(EmbeddedLabDocumentLoader.isBase64Shaped("QUJ=")).isTrue();
        assertThat(EmbeddedLabDocumentLoader.isBase64Shaped("QU=")).isFalse();
        assertThat(EmbeddedLabDocumentLoader.isBase64Shaped("QUJD \t")).isTrue();
        assertThat(EmbeddedLabDocumentLoader.isBase64Shaped("QUJD\n")).isTrue();
        assertThat(EmbeddedLabDocumentLoader.isBase64Shaped(" NONE")).isTrue();
        assertThat(EmbeddedLabDocumentLoader.isBase64Shaped("QU JD")).isFalse();

        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, (byte) 0xFB, (byte) 0xFF};
        String urlSafe = Base64.getUrlEncoder().withoutPadding().encodeToString(png);
        assertThat(urlSafe).containsAnyOf("-", "_").doesNotContain("=");
        assertThat(EmbeddedLabDocumentLoader.inspect(handlerReturning(urlSafe, null), 0, 0, 0).status()).isEqualTo(Status.NOT_PDF);
        assertThat(EmbeddedLabDocumentLoader.inspect(handlerReturning("Specimen received", null), 0, 0, 0).status()).isEqualTo(Status.TEXT);
    }

    @Test
    @DisplayName("should show legacy PATHL7 ED.1 text, base64-shaped or not, as the text the views print")
    void shouldClassifyLegacyPathL7Text_asTextWithItsValue() throws Exception {
        PATHL7Handler worded = pathL7(withEdValue("Report to follow\\.br\\see note."));
        PATHL7Handler shaped = pathL7(withEdValue("NONE"));

        assertThat(worded.isLegacy(1, 0)).isTrue();
        assertThat(EmbeddedLabDocumentLoader.inspect(worded, 1, 0, 0).status()).isEqualTo(Status.TEXT);
        assertThat(worded.getOBXEmbeddedDocumentText(1, 0)).isEqualTo("Report to follow<br />see note.");
        assertThat(EmbeddedLabDocumentLoader.inspect(shaped, 1, 0, 0).status()).isEqualTo(Status.TEXT);
        assertThat(shaped.getOBXEmbeddedDocumentText(1, 0)).isEqualTo("NONE");
        // A legacy PDF in ED.1 is still the PDF.
        assertThat(EmbeddedLabDocumentLoader.inspect(pathL7(withEdValue(PDF_BASE64)), 1, 0, 0).status()).isEqualTo(Status.PDF);
        // An undeclared base64-shaped ED.5 is not the legacy fallback and stays undisplayable.
        PATHL7Handler undeclared = pathL7(withEdValue("^TEXT^^^" + "QUJD".repeat(30)));
        assertThat(undeclared.isOBXEmbeddedDocumentResultFallback(1, 0)).isFalse();
        assertThat(EmbeddedLabDocumentLoader.inspect(undeclared, 1, 0, 0).status()).isEqualTo(Status.NOT_PDF);
    }

    @Test
    @DisplayName("should normalise PATHL7 ED.5 text line breaks once, and leave CELLPATHR RTF as sent")
    void shouldNormaliseStandardPathL7Text_andLeaveCellPathRtfRaw() throws Exception {
        PATHL7Handler standard = pathL7(withEdValue("^TEXT^PLAIN^A^Line one\\.br\\Line two "));

        assertThat(standard.isLegacy(1, 0)).isFalse();
        assertThat(EmbeddedLabDocumentLoader.inspect(standard, 1, 0, 0).status()).isEqualTo(Status.TEXT);
        assertThat(standard.getOBXEmbeddedDocumentText(1, 0)).isEqualTo("Line one<br />Line two");

        // CELLPATHR keeps raw RTF in ED.1 (declared A); its text is passed through untouched.
        String rtf = "{\\rtf1 Diagnosis\\par\\.br\\ }";
        PATHL7Handler cellPath = pathL7(withEdValue(rtf + "^TEXT^RTF^A^").replace("||PATH|F", "||CELLPATHR|F"));
        assertThat(cellPath.getOBXDocumentEncoding(1, 0)).isEqualTo("A");
        assertThat(cellPath.getOBXEmbeddedDocumentText(1, 0)).isEqualTo(cellPath.getOBXResult(1, 0));
    }

    @Test
    @DisplayName("should return exactly the decoded document from the single classifying decode")
    void shouldReturnExactBytes_fromSingleDecodePass() throws Exception {
        byte[] document = (new String(PDF, StandardCharsets.US_ASCII) + "x".repeat(30000)).getBytes(StandardCharsets.US_ASCII);
        String base64 = Base64.getEncoder().encodeToString(document);
        String hex = HexFormat.of().formatHex(document);
        MessageHandler[] handlers = {
                handlerReturning(base64, "Base64"),
                handlerReturning(base64.replaceAll("(.{76})", "$1\r\n"), null),
                // Strict decoding fails only after several 8 KB reads have been written: the lenient
                // retry must start from an empty buffer, not append to the partial strict output.
                handlerReturning(base64.substring(0, 30000) + "*" + base64.substring(30000), null),
                handlerReturning(hex, "Hex"),
                handlerReturning(hex + "a", "Hex"),
        };
        for (MessageHandler handler : handlers) {
            for (long limit : new long[] {0, document.length}) {
                Document loaded = EmbeddedLabDocumentLoader.load(handler, 0, 0, limit);
                assertThat(loaded.status()).isEqualTo(Status.PDF);
                assertThat(loaded.bytes()).isEqualTo(document);
                assertThat(loaded.sizeBytes()).isEqualTo(document.length);
            }
            Document overLimit = EmbeddedLabDocumentLoader.load(handler, 0, 0, document.length - 1);
            assertThat(overLimit.status()).isEqualTo(Status.TOO_LARGE);
            assertThat(overLimit.bytes()).isNull();
        }
        // Classifications that are not a PDF still carry no bytes.
        assertThat(EmbeddedLabDocumentLoader.load(handlerReturning("Report to follow.", null), 0, 0, 0).bytes()).isNull();
        assertThat(EmbeddedLabDocumentLoader.load(handlerReturning(
                Base64.getEncoder().encodeToString("<html></html>".getBytes(StandardCharsets.US_ASCII)), "Base64"), 0, 0, 0).bytes()).isNull();
    }

    @Test
    @DisplayName("should never buffer a large payload that is not a PDF on the uncapped load path")
    void shouldNotBuffer_forLargeNonPdfLoad() {
        byte[] html = ("<html>" + "x".repeat(200_000) + "</html>").getBytes(StandardCharsets.US_ASCII);
        for (MessageHandler handler : new MessageHandler[] {
                handlerReturning(Base64.getEncoder().encodeToString(html), "Base64"),
                handlerReturning(HexFormat.of().formatHex(html), "Hex")}) {
            Document document = EmbeddedLabDocumentLoader.load(handler, 0, 0, 0);
            assertThat(document.status()).isEqualTo(Status.NOT_PDF);
            assertThat(document.bytes()).isNull();
            assertThat(document.sizeBytes()).isEqualTo(html.length);
        }
    }

    @Test
    @DisplayName("should start buffering only once the PDF signature is confirmed, whatever the chunking")
    void shouldBufferOnlyAfterSignature_forPdfBuffer() {
        byte[] html = "<html>not a pdf</html>".getBytes(StandardCharsets.US_ASCII);
        EmbeddedLabDocumentLoader.PdfBuffer rejected = new EmbeddedLabDocumentLoader.PdfBuffer();
        rejected.write(html, 0, html.length);
        assertThat(rejected.isBuffering()).isFalse();
        assertThat(rejected.bytes()).isNull();

        // Signature split across single bytes, then the rest in one chunk.
        EmbeddedLabDocumentLoader.PdfBuffer pdf = new EmbeddedLabDocumentLoader.PdfBuffer();
        for (int i = 0; i < 3; i++) {
            pdf.write(PDF[i]);
            assertThat(pdf.isBuffering()).isFalse();
        }
        pdf.write(PDF, 3, PDF.length - 3);
        assertThat(pdf.isBuffering()).isTrue();
        assertThat(pdf.bytes()).isEqualTo(PDF);

        // A retry starts from nothing.
        pdf.reset();
        assertThat(pdf.isBuffering()).isFalse();
        pdf.write(html, 0, html.length);
        assertThat(pdf.bytes()).isNull();
    }

    @Test
    @DisplayName("should estimate the decoded size exactly for strict and lenient base64 and for hex")
    void shouldEstimateDecodedSize_fromEncodedLength() {
        for (int length = 0; length <= 7; length++) {
            byte[] bytes = new byte[length];
            String base64 = Base64.getEncoder().encodeToString(bytes);
            assertThat(EmbeddedLabDocumentLoader.estimateDecodedSize(base64, false)).as("length %d", length).isEqualTo(length);
            assertThat(EmbeddedLabDocumentLoader.estimateDecodedSize("*" + base64 + "%", false)).isEqualTo(length);
        }
        assertThat(EmbeddedLabDocumentLoader.estimateDecodedSize(HexFormat.of().formatHex(PDF) + "a", true)).isEqualTo(PDF.length);
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
    @DisplayName("should compare documents by content and never print their bytes")
    void shouldCompareByContentAndHideBytes_whenComparingDocuments() {
        Document one = new Document(Status.PDF, PDF.clone(), PDF.length);
        Document two = new Document(Status.PDF, PDF.clone(), PDF.length);

        assertThat(one).isEqualTo(two).hasSameHashCodeAs(two);
        assertThat(one).isNotEqualTo(new Document(Status.PDF, new byte[] {1}, PDF.length));
        assertThat(one.toString()).doesNotContain("%PDF").contains("sizeBytes=" + PDF.length);
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
