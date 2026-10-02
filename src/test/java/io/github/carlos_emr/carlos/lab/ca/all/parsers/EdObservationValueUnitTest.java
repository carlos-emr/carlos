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
package io.github.carlos_emr.carlos.lab.ca.all.parsers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.lab.ca.all.pageUtil.EmbeddedLabDocumentLoader;

/**
 * Unit tests for {@link EdObservationValue} through {@link DefaultGenericHandler}: a handler whose
 * {@code getOBXResult} reads OBX-5 component 1 still yields the ED.5 document and its ED.4
 * encoding, and a payload sent in OBX-5.1 keeps working.
 *
 * @since 2026-09-30
 */
@Tag("unit")
@Tag("fast")
@Tag("lab")
@DisplayName("EdObservationValue")
class EdObservationValueUnitTest {

    private static final String PDF_BASE64 = Base64.getEncoder()
            .encodeToString(PathL7EmbeddedDocumentMessage.PDF);

    /**
     * CRLF-separated with a non-standard trigger event: {@link DefaultGenericHandler} walks the
     * flat segment list HAPI only builds for a structure it does not model (see
     * {@code LabTextLineBreakRenderingUnitTest}).
     */
    private static String message(String version, String trigger, String obx) {
        return String.join("\r\n",
                "MSH|^~\\&|GENLAB|CARLOSTEST|HTTPCLIENT|carlos|20260930101500||" + trigger + "|PW3977GEN|P|" + version + "|||ER|AL",
                "PID||9999999999|3977||PLAYWRIGHT^GENERIC||19800102|F",
                "ORC|RE||ACC3977|||||||||99999^FAKE-ORDERING^DOC",
                "OBR|1||ACC3977|RPT^Report||20260930100000|20260930100000|||||||20260930100000"
                        + "||99999^FAKE-ORDERING^DOC||||||20260930100000||CHEM4|F",
                "OBX|1|NM|GLU^Glucose||5.2|mmol/L|3.3-7.7|N|||F|||20260930100000",
                obx);
    }

    private static DefaultGenericHandler handler(String obx) throws Exception {
        DefaultGenericHandler handler = new DefaultGenericHandler();
        handler.init(message("2.3", "ORU^Z01", obx));
        return handler;
    }

    @Test
    @DisplayName("should return ED.5 and ED.4 from an Excelleris (2.3.1) ED value")
    void shouldReadDataComponent_forExcellerisEdValue() throws Exception {
        ExcellerisOntarioHandler handler = new ExcellerisOntarioHandler();
        handler.init(message("2.3.1", "ORU^R01",
                "OBX|2|ED|RPT^Report|A|^TEXT^PDF^Base64^" + PDF_BASE64 + "||||||F|||20260930100000"));

        assertThat(handler.isOBXEmbeddedDocument(0, 1)).isTrue();
        assertThat(handler.getOBXEmbeddedDocumentData(0, 1)).isEqualTo(PDF_BASE64);
        assertThat(handler.getOBXDocumentEncoding(0, 1)).isEqualTo("Base64");
    }

    @Test
    @DisplayName("should keep the Excelleris OBX-4 sub-ID label on an ED text value")
    void shouldPrefixSubId_forExcellerisEdText() throws Exception {
        ExcellerisOntarioHandler handler = new ExcellerisOntarioHandler();
        handler.init(message("2.3.1", "ORU^R01",
                "OBX|2|ED|RPT^Report|A|^TEXT^PLAIN^A^First line\\.br\\Second line||||||F|||20260930100000"));

        assertThat(handler.getOBXSubIdWithEmbeddedDocumentText(0, 1)).isEqualTo("A) First line<br />Second line");
        // The OBX-5.1 form, unchanged, shows only the (empty) source application after the label.
        assertThat(handler.getOBXSubIdWithObservationValue(0, 1)).isEqualTo("A) ");
    }

    @Test
    @DisplayName("should label the OBX-5.1 text when an Excelleris ED value has no ED.5")
    void shouldPrefixSubIdToResult_whenExcellerisDataComponentIsEmpty() throws Exception {
        ExcellerisOntarioHandler handler = new ExcellerisOntarioHandler();
        handler.init(message("2.3.1", "ORU^R01",
                "OBX|2|ED|RPT^Report|B|Legacy text\\.br\\payload||||||F|||20260930100000"));

        assertThat(handler.getOBXSubIdWithEmbeddedDocumentText(0, 1))
                .isEqualTo(handler.getOBXSubIdWithObservationValue(0, 1))
                .isEqualTo("B) Legacy text<br />payload");
    }

    @Test
    @DisplayName("should return ED.5 and ED.4 for a standards-compliant ED value")
    void shouldReadDataComponent_forStandardEdValue() throws Exception {
        DefaultGenericHandler handler = handler("OBX|2|ED|RPT^Report||^TEXT^PDF^Base64^" + PDF_BASE64 + "||||||F|||20260930100000");

        assertThat(handler.isOBXEmbeddedDocument(0, 1)).isTrue();
        // The ordinary result is component 1: the (empty) source application, not the document.
        assertThat(handler.getOBXResult(0, 1)).isEmpty();
        assertThat(handler.getOBXEmbeddedDocumentData(0, 1)).isEqualTo(PDF_BASE64);
        assertThat(handler.getOBXDocumentEncoding(0, 1)).isEqualTo("Base64");
    }

    @Test
    @DisplayName("should never return an encoded ED.5 document as display text")
    void shouldNotReturnPayloadAsText_forBase64EdValue() throws Exception {
        DefaultGenericHandler handler = handler("OBX|2|ED|RPT^Report||^TEXT^PDF^Base64^" + PDF_BASE64 + "||||||F|||20260930100000");

        assertThat(handler.getOBXEmbeddedDocumentData(0, 1)).isEqualTo(PDF_BASE64);
        assertThat(handler.getOBXEmbeddedDocumentText(0, 1))
                .doesNotContain(PDF_BASE64)
                .isEqualTo(handler.getOBXResult(0, 1));
    }

    @Test
    @DisplayName("should keep the OBX-5.1 payload when ED.5 is empty")
    void shouldFallBackToResult_whenDataComponentIsEmpty() throws Exception {
        DefaultGenericHandler handler = handler("OBX|2|ED|RPT^Report||" + PDF_BASE64 + "||||||F|||20260930100000");

        assertThat(handler.getOBXEmbeddedDocumentData(0, 1)).isEqualTo(PDF_BASE64);
        assertThat(handler.getOBXDocumentEncoding(0, 1)).isNull();
    }

    @Test
    @DisplayName("should show legacy OBX-5.1 text of an ED value as its text")
    void shouldClassifyLegacyResultAsText_whenDataComponentIsEmpty() throws Exception {
        DefaultGenericHandler handler = handler("OBX|2|ED|RPT^Report||Report to follow, see note.||||||F|||20260930100000");

        assertThat(handler.getOBXDocumentEncoding(0, 1)).isNull();
        assertThat(EmbeddedLabDocumentLoader.inspect(handler, 0, 1, 0).status())
                .isEqualTo(EmbeddedLabDocumentLoader.Status.TEXT);
        assertThat(handler.getOBXEmbeddedDocumentText(0, 1)).isEqualTo("Report to follow, see note.");
    }

    @Test
    @DisplayName("should show a base64-shaped legacy OBX-5.1 value such as NONE as its text")
    void shouldClassifyBase64ShapedResultAsText_whenDataComponentIsEmpty() throws Exception {
        DefaultGenericHandler handler = handler("OBX|2|ED|RPT^Report||NONE||||||F|||20260930100000");

        assertThat(handler.isOBXEmbeddedDocumentResultFallback(0, 1)).isTrue();
        assertThat(EmbeddedLabDocumentLoader.inspect(handler, 0, 1, 0).status())
                .isEqualTo(EmbeddedLabDocumentLoader.Status.TEXT);
        assertThat(handler.getOBXEmbeddedDocumentText(0, 1)).isEqualTo("NONE");
    }

    @Test
    @DisplayName("should show undeclared ED.5 text that the loader classes as text")
    void shouldReturnUndeclaredDataAsText_whenNotBase64Shaped() throws Exception {
        DefaultGenericHandler handler = handler("OBX|2|ED|RPT^Report||^TEXT^PLAIN^^Culture pending\\.br\\see note.||||||F|||20260930100000");

        assertThat(handler.getOBXDocumentEncoding(0, 1)).isNull();
        assertThat(handler.isOBXEmbeddedDocumentResultFallback(0, 1)).isFalse();
        assertThat(EmbeddedLabDocumentLoader.inspect(handler, 0, 1, 0).status())
                .isEqualTo(EmbeddedLabDocumentLoader.Status.TEXT);
        assertThat(handler.getOBXEmbeddedDocumentText(0, 1)).isEqualTo("Culture pending<br />see note.");
    }

    @Test
    @DisplayName("should keep an undeclared base64-shaped ED.5 that is not a PDF undisplayable")
    void shouldClassifyNotPdf_forUndeclaredBase64ShapedData() throws Exception {
        String png = Base64.getEncoder().encodeToString(new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
        DefaultGenericHandler handler = handler("OBX|2|ED|RPT^Report||^IM^PNG^^" + png + "||||||F|||20260930100000");

        assertThat(handler.isOBXEmbeddedDocumentResultFallback(0, 1)).isFalse();
        assertThat(handler.getOBXDocumentEncoding(0, 1)).isNull();
        assertThat(EmbeddedLabDocumentLoader.inspect(handler, 0, 1, 0).status())
                .isEqualTo(EmbeddedLabDocumentLoader.Status.NOT_PDF);
        assertThat(handler.getOBXEmbeddedDocumentText(0, 1)).doesNotContain(png);
    }

    @Test
    @DisplayName("should leave ordinary results untouched")
    void shouldReturnResult_forNonEdObservation() throws Exception {
        DefaultGenericHandler handler = handler("OBX|2|ED|RPT^Report||^TEXT^PDF^Base64^" + PDF_BASE64 + "||||||F|||20260930100000");

        assertThat(handler.getOBXEmbeddedDocumentData(0, 0)).isEqualTo("5.2");
        assertThat(handler.getOBXDocumentEncoding(0, 0)).isNull();
    }
}
