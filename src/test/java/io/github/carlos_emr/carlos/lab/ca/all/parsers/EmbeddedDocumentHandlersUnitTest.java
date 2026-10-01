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
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.carlos_emr.carlos.commn.dao.Hl7TextInfoDao;
import io.github.carlos_emr.carlos.lab.ca.all.pageUtil.EmbeddedLabDocumentLoader;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * Every lab handler whose {@code getOBXResult} reads OBX-5 component 1 still yields a
 * standards-compliant {@code ED} value's ED.5 document and ED.4 encoding through
 * {@link MessageHandler#getOBXSegment(int, int)}, so the page classifies it as a PDF (and the
 * endpoints serve it) for every lab type, not only the handlers first wired up for #3977. A
 * text payload (ED.4 {@code A}) is returned with the handlers' {@code \.br\} normalisation.
 *
 * @since 2026-10-01
 */
@Tag("unit")
@Tag("fast")
@Tag("lab")
@DisplayName("Embedded ED documents across lab handlers")
class EmbeddedDocumentHandlersUnitTest extends CarlosUnitTestBase {

    private static final String PDF_BASE64 = Base64.getEncoder()
            .encodeToString(PathL7EmbeddedDocumentMessage.PDF);

    /** BioTest and GDML look up earlier versions of the lab; with none stored they parse this one. */
    @BeforeEach
    void registerLabLookup() {
        createAndRegisterMock(Hl7TextInfoDao.class);
    }

    /** An ORU^R01 with an ordinary result at (0, 0) and the given OBX at (0, 1). */
    private static String message(String sendingApplication, String version, String obx) {
        return String.join("\r",
                "MSH|^~\\&|" + sendingApplication + "|CARLOSTEST|HTTPCLIENT|carlos|20261001101500||ORU^R01|PW4118MSG|P|" + version + "|||ER|AL",
                "PID||9999999999|4118||PLAYWRIGHT^HANDLERS||19800102|F",
                "ORC|RE||ACC4118|||||||||99999^FAKE-ORDERING^DOC",
                "OBR|1|PL4118|ACC4118|RPT^Report||20261001100000|20261001100000|||||||20261001100000"
                        + "||99999^FAKE-ORDERING^DOC||||||20261001100000||CHEM4|F",
                "OBX|1|NM|GLU^Glucose||5.2|mmol/L|3.3-7.7|N|||F|||20261001100000",
                obx) + "\r";
    }

    static Stream<Arguments> handlers() {
        return Stream.of(
                Arguments.of("AlphaHandler", (Supplier<MessageHandler>) AlphaHandler::new, "2.3"),
                Arguments.of("BioTestHandler", (Supplier<MessageHandler>) BioTestHandler::new, "2.3"),
                Arguments.of("CDLHandler", (Supplier<MessageHandler>) CDLHandler::new, "2.3"),
                Arguments.of("CLSHandler", (Supplier<MessageHandler>) CLSHandler::new, "2.3"),
                Arguments.of("CMLHandler", (Supplier<MessageHandler>) CMLHandler::new, "2.3"),
                Arguments.of("ExcellerisOntarioHandler", (Supplier<MessageHandler>) ExcellerisOntarioHandler::new, "2.3.1"),
                Arguments.of("GDMLHandler", (Supplier<MessageHandler>) GDMLHandler::new, "2.3"),
                Arguments.of("HHSEmrDownloadHandler", (Supplier<MessageHandler>) HHSEmrDownloadHandler::new, "2.3"),
                Arguments.of("MDSHandler", (Supplier<MessageHandler>) MDSHandler::new, "2.3"),
                Arguments.of("MEDITECHHandler", (Supplier<MessageHandler>) MEDITECHHandler::new, "2.3"),
                Arguments.of("SpireHandler", (Supplier<MessageHandler>) SpireHandler::new, "2.3"),
                Arguments.of("TRUENORTHHandler", (Supplier<MessageHandler>) TRUENORTHHandler::new, "2.3"));
    }

    private static MessageHandler init(Supplier<MessageHandler> factory, String version, String obx) throws Exception {
        MessageHandler handler = factory.get();
        handler.init(message("GENLAB", version, obx));
        return handler;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("handlers")
    @DisplayName("should return ED.5 and ED.4 and classify the PDF for a standards-compliant ED value")
    void shouldReadDataComponent_forStandardEdValue(String name, Supplier<MessageHandler> factory, String version) throws Exception {
        MessageHandler handler = init(factory, version,
                "OBX|2|ED|RPT^Report||^TEXT^PDF^Base64^" + PDF_BASE64 + "||||||F|||20261001100000");

        assertThat(handler.getOBXCount(0)).isEqualTo(2);
        assertThat(handler.isOBXEmbeddedDocument(0, 1)).isTrue();
        assertThat(handler.getOBXEmbeddedDocumentData(0, 1)).isEqualTo(PDF_BASE64);
        assertThat(handler.getOBXDocumentEncoding(0, 1)).isEqualTo("Base64");
        assertThat(EmbeddedLabDocumentLoader.inspect(handler, 0, 1, 0).status())
                .isEqualTo(EmbeddedLabDocumentLoader.Status.PDF);
        assertThat(EmbeddedLabDocumentLoader.load(handler, 0, 1, 0).bytes())
                .isEqualTo(PathL7EmbeddedDocumentMessage.PDF);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("handlers")
    @DisplayName("should return a text ED.5 with HL7 line breaks as markers, and the raw value as data")
    void shouldNormaliseLineBreaks_forTextEdValue(String name, Supplier<MessageHandler> factory, String version) throws Exception {
        MessageHandler handler = init(factory, version,
                "OBX|2|ED|RPT^Report||^TEXT^PLAIN^A^Line one\\.br\\Line two ||||||F|||20261001100000");

        assertThat(handler.getOBXDocumentEncoding(0, 1)).isEqualTo("A");
        assertThat(EmbeddedLabDocumentLoader.inspect(handler, 0, 1, 0).status())
                .isEqualTo(EmbeddedLabDocumentLoader.Status.TEXT);
        // <br /> from the shared normalisation; Spire already rewrites \.br\ to <br> on parse.
        // Both are markers htmlWithBreakMarkers renders as line breaks.
        assertThat(handler.getOBXEmbeddedDocumentText(0, 1)).matches("Line one<br ?/?>Line two");
        // The data is untouched (not trimmed): it is what the PDF decoder reads.
        assertThat(handler.getOBXEmbeddedDocumentData(0, 1)).endsWith("Line two ");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("handlers")
    @DisplayName("should leave an ordinary result untouched")
    void shouldReturnResult_forNonEdObservation(String name, Supplier<MessageHandler> factory, String version) throws Exception {
        MessageHandler handler = init(factory, version,
                "OBX|2|ED|RPT^Report||^TEXT^PDF^Base64^" + PDF_BASE64 + "||||||F|||20261001100000");

        assertThat(handler.isOBXEmbeddedDocument(0, 0)).isFalse();
        assertThat(handler.getOBXEmbeddedDocumentData(0, 0)).isEqualTo(handler.getOBXResult(0, 0));
        assertThat(handler.getOBXEmbeddedDocumentText(0, 0)).isEqualTo(handler.getOBXResult(0, 0));
        assertThat(handler.getOBXDocumentEncoding(0, 0)).isNull();
    }
}
