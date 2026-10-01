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
    @DisplayName("should keep the OBX-5.1 payload when ED.5 is empty")
    void shouldFallBackToResult_whenDataComponentIsEmpty() throws Exception {
        DefaultGenericHandler handler = handler("OBX|2|ED|RPT^Report||" + PDF_BASE64 + "||||||F|||20260930100000");

        assertThat(handler.getOBXEmbeddedDocumentData(0, 1)).isEqualTo(PDF_BASE64);
        assertThat(handler.getOBXDocumentEncoding(0, 1)).isNull();
    }

    @Test
    @DisplayName("should leave ordinary results untouched")
    void shouldReturnResult_forNonEdObservation() throws Exception {
        DefaultGenericHandler handler = handler("OBX|2|ED|RPT^Report||^TEXT^PDF^Base64^" + PDF_BASE64 + "||||||F|||20260930100000");

        assertThat(handler.getOBXEmbeddedDocumentData(0, 0)).isEqualTo("5.2");
        assertThat(handler.getOBXDocumentEncoding(0, 0)).isNull();
    }
}
