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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.lab.ca.all.pageUtil.EmbeddedLabDocumentLoader;

/**
 * Regression tests for {@link EpsilonHandler} (#4124).
 *
 * <p>The handler parses Epsilon labs as a structured ORU^R01 but extends
 * {@link DefaultGenericHandler}, whose count, value-type, identifier, embedded-document and
 * patient accessors read the superclass's message, terser and OBR/OBX groups. Before the fix
 * those were never built, so {@code getOBRCount()} was 0 (both lab views and the CDS export
 * rendered no rows), the OBX accessors threw {@code NullPointerException}, and so did the patient
 * name the uploader reads first.</p>
 *
 * <p>The fixtures are the repository's fictitious Epsilon samples. The uploader stores one
 * message per PID ({@code Utilities.separateMessages}), so the samples are split the same way.</p>
 *
 * @since 2026-10-01
 */
@Tag("unit")
@Tag("fast")
@Tag("lab")
@DisplayName("EpsilonHandler")
class EpsilonHandlerUnitTest {

    private static final Path FIXTURES = Path.of("src", "test", "resources", "labs", "HL7-Epsilon");

    private static String fixture(String name) throws IOException {
        return Files.readString(FIXTURES.resolve(name), StandardCharsets.UTF_8);
    }

    /** Splits a feed into one message per PID, repeating the MSH, as {@code Utilities.separateMessages} does. */
    private static List<String> separateMessages(String feed) {
        List<String> messages = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String msh = "";
        boolean seenPid = false;
        for (String line : feed.split("\r\n|\r|\n")) {
            if (line.length() <= 3) {
                continue;
            }
            if (line.startsWith("MSH")) {
                if (current.length() > 0) {
                    messages.add(current.toString());
                    current.setLength(0);
                }
                msh = line;
                seenPid = false;
            } else if (line.startsWith("PID")) {
                if (seenPid) {
                    messages.add(current.toString());
                    current.setLength(0);
                    current.append(msh).append("\r\n");
                }
                seenPid = true;
            }
            current.append(line).append("\r\n");
        }
        messages.add(current.toString());
        return messages;
    }

    private static MessageHandler handler(String message) {
        MessageHandler handler = Factory.getHandler("EPSILON", message);
        assertThat(handler).isInstanceOf(EpsilonHandler.class);
        return handler;
    }

    @Nested
    @DisplayName("OBR and OBX structure")
    class Structure {

        @Test
        @DisplayName("should expose the OBR group and OBX rows of the first patient in the general sample")
        void shouldExposeObrAndObxRows_forGeneralSample() throws Exception {
            MessageHandler handler = handler(fixture("medhealth-general.hl7"));

            assertThat(handler.getOBRCount()).isEqualTo(1);
            assertThat(handler.getOBXCount(0)).isEqualTo(3);
            assertThat(handler.getHeaders()).containsExactly("FOBT (ColonCancerCheck)");
            assertThat(handler.getOBXValueType(0, 0)).isEqualTo("FT");
            assertThat(handler.getOBXIdentifier(0, 0)).isEqualTo("FOBT (ColonCancerCheck)");
            assertThat(handler.getOBXResult(0, 0)).isEqualTo("Negative");
            assertThat(handler.getOBXResultStatus(0, 0)).isEqualTo("Final");
            assertThat(handler.getOBXAbnormalFlag(0, 0)).isEmpty();
            assertThat(handler.isOBXEmbeddedDocument(0, 0)).isFalse();
            assertThat(handler.getOBXFinalResultCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("should expose every OBX of every stored message of the general sample")
        void shouldExposeAllObxRows_forEachStoredMessage() throws Exception {
            List<String> messages = separateMessages(fixture("medhealth-general.hl7"));
            assertThat(messages).hasSize(5);

            int obxTotal = 0;
            for (String message : messages) {
                MessageHandler handler = handler(message);
                assertThat(handler.getOBRCount()).isPositive();
                for (int i = 0; i < handler.getOBRCount(); i++) {
                    for (int j = 0; j < handler.getOBXCount(i); j++) {
                        // Every row the lab views render must resolve without throwing.
                        assertThat(handler.getOBXIdentifier(i, j)).isNotEmpty();
                        assertThat(handler.getOBXValueType(i, j)).isNotEmpty();
                        assertThat(handler.getOBXResult(i, j)).isNotNull();
                        assertThat(handler.isOBXEmbeddedDocument(i, j)).isFalse();
                        obxTotal++;
                    }
                }
                // Each row's identifier is one of the headers the views group rows under.
                assertThat(handler.getHeaders()).contains(handler.getOBXIdentifier(0, 0));
            }
            assertThat(obxTotal).isEqualTo(32);
        }

        @Test
        @DisplayName("should name the OBR group and its text rows in the pap sample")
        void shouldNameObrGroup_forPapSample() throws Exception {
            MessageHandler handler = handler(separateMessages(fixture("medhealth-pap.hl7")).get(0));

            assertThat(handler.getOBRCount()).isEqualTo(1);
            assertThat(handler.getOBXCount(0)).isEqualTo(11);
            assertThat(handler.getOBRName(0)).isEqualTo("CYTOLOGY");
            assertThat(handler.getOBRIdentifier(0)).isEqualTo("CYT");
            assertThat(handler.getOBXName(0, 0)).isEqualTo("CYTOLOGY");
            assertThat(handler.getOBXValueType(0, 0)).isEqualTo("TX");
            assertThat(handler.getOBXResult(0, 0))
                    .isEqualTo("Type of Specimen: Cervicovaginal brush for liquid based cytology");
        }

        @Test
        @DisplayName("should keep each OBR group's own OBX rows when a message has several orders")
        void shouldGroupObxRowsByOrder_whenMessageHasSeveralOrders() {
            MessageHandler handler = handler(String.join("\r\n",
                    "MSH|^~\\&|Epsilon-System||||201204041604||ORU^R01|PW4124-MULTI|P|2.3|||||||",
                    "PID|1||9999999999|9999999999^AA|FAKE-EPSILON^PATIENT||19800102|F",
                    "OBR|1||||R|201204030004||||||||201204030004||99999^FAKE^DR. ORDERING||||||||F",
                    "OBX|1|NM|GENERAL CHEMISTRY^GLU^Glucose|1|5.2|mmol/L|3.3-7.7|N|||F",
                    "OBR|2||||R|201204030004||||||||201204030004||99999^FAKE^DR. ORDERING||||||||F",
                    "OBX|1|NM|HEMATOLOGY^HGB^Hemoglobin|1|140|g/L|120-160|N|||F",
                    "OBX|2|NM|HEMATOLOGY^WBC^White Cells|2|12.5|10*9/L|4.0-11.0|H|||F") + "\r\n");

            assertThat(handler.getOBRCount()).isEqualTo(2);
            assertThat(handler.getOBXCount(0)).isEqualTo(1);
            assertThat(handler.getOBXCount(1)).isEqualTo(2);
            assertThat(handler.getOBXIdentifier(1, 1)).isEqualTo("HEMATOLOGY");
            assertThat(handler.getOBXName(1, 1)).isEqualTo("WBC");
            assertThat(handler.getOBXResult(1, 1)).isEqualTo("12.5");
            assertThat(handler.getOBXUnits(1, 1)).isEqualTo("10*9/L");
            assertThat(handler.getOBXReferenceRange(1, 1)).isEqualTo("4.0-11.0");
            assertThat(handler.isOBXAbnormal(1, 1)).isTrue();
            assertThat(handler.getHeaders()).containsExactly("GENERAL CHEMISTRY", "HEMATOLOGY");
        }
    }

    @Nested
    @DisplayName("Patient and message fields")
    class PatientFields {

        @Test
        @DisplayName("should read the patient fields the uploader matches on")
        void shouldReadPatientFields_forUploaderMatching() throws Exception {
            MessageHandler handler = handler(fixture("medhealth-general.hl7"));

            // getFirstName() used to throw NullPointerException, failing every Epsilon upload.
            assertThat(handler.getFirstName()).isEqualTo("JANE");
            assertThat(handler.getLastName()).isEqualTo("SMITH");
            assertThat(handler.getPatientName()).isEqualTo("JANE SMITH");
            assertThat(handler.getDOB()).isEqualTo("1954-03-23");
            assertThat(handler.getSex()).isEqualTo("F");
            assertThat(handler.getHealthNum()).isEqualTo("2222222222");
            assertThat(handler.getDocNums()).containsExactly("011111");
        }

        @Test
        @DisplayName("should format message and service dates like the other generic handlers")
        void shouldFormatDates_likeOtherGenericHandlers() throws Exception {
            EpsilonHandler handler = (EpsilonHandler) handler(fixture("medhealth-general.hl7"));

            assertThat(handler.getMsgDate()).isEqualTo("2012-04-04 16:04");
            assertThat(handler.getServiceDate()).isEqualTo("2012-04-03 00:04");
            assertThat(handler.getMsgDateAsDate()).isNotNull();
        }

        @Test
        @DisplayName("should keep an unreadable date as sent instead of dropping it")
        void shouldKeepRawValue_whenDateIsUnreadable() {
            EpsilonHandler handler = new EpsilonHandler();

            assertThat(handler.formatDateTime("not-a-date")).isEqualTo("not-a-date");
            assertThat(handler.formatDateTime(" ")).isEmpty();
            assertThat(handler.formatDateTime(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Embedded ED documents")
    class EmbeddedDocuments {

        private static final String PDF_BASE64 = Base64.getEncoder()
                .encodeToString(PathL7EmbeddedDocumentMessage.PDF);

        private MessageHandler withObx(String obx) {
            return handler(String.join("\r\n",
                    "MSH|^~\\&|Epsilon-System||||201204041604||ORU^R01|PW4124-ED|P|2.3|||||||",
                    "PID|1||9999999999|9999999999^AA|FAKE-EPSILON^PATIENT||19800102|F",
                    "OBR|1||||R|201204030004||||||||201204030004||99999^FAKE^DR. ORDERING||||||||F",
                    "OBX|1|NM|GENERAL CHEMISTRY^GLU^Glucose|1|5.2|mmol/L|3.3-7.7||||F",
                    obx) + "\r\n");
        }

        @Test
        @DisplayName("should classify and load a standards-compliant ED PDF")
        void shouldClassifyPdf_forStandardEdValue() {
            MessageHandler handler = withObx(
                    "OBX|2|ED|GENERAL CHEMISTRY^RPT^Report|2|^TEXT^PDF^Base64^" + PDF_BASE64 + "||||||F");

            assertThat(handler.getOBXCount(0)).isEqualTo(2);
            assertThat(handler.isOBXEmbeddedDocument(0, 1)).isTrue();
            assertThat(handler.getOBXEmbeddedDocumentData(0, 1)).isEqualTo(PDF_BASE64);
            assertThat(handler.getOBXDocumentEncoding(0, 1)).isEqualTo("Base64");
            assertThat(EmbeddedLabDocumentLoader.inspect(handler, 0, 1, 0).status())
                    .isEqualTo(EmbeddedLabDocumentLoader.Status.PDF);
            assertThat(EmbeddedLabDocumentLoader.load(handler, 0, 1, 0).bytes())
                    .isEqualTo(PathL7EmbeddedDocumentMessage.PDF);
        }

        @Test
        @DisplayName("should report a non-PDF binary ED payload as undisplayable")
        void shouldReportUndisplayable_forNonPdfBinary() {
            String png = Base64.getEncoder().encodeToString(new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
            MessageHandler handler = withObx(
                    "OBX|2|ED|GENERAL CHEMISTRY^IMG^Image|2|^IMAGE^PNG^Base64^" + png + "||||||F");

            assertThat(handler.isOBXEmbeddedDocument(0, 1)).isTrue();
            assertThat(EmbeddedLabDocumentLoader.inspect(handler, 0, 1, 0).isUndisplayable()).isTrue();
        }

        @Test
        @DisplayName("should leave an ordinary result untouched")
        void shouldReturnResult_forNonEdObservation() {
            MessageHandler handler = withObx(
                    "OBX|2|ED|GENERAL CHEMISTRY^RPT^Report|2|^TEXT^PDF^Base64^" + PDF_BASE64 + "||||||F");

            assertThat(handler.isOBXEmbeddedDocument(0, 0)).isFalse();
            assertThat(handler.getOBXEmbeddedDocumentData(0, 0)).isEqualTo("5.2");
            assertThat(handler.getOBXDocumentEncoding(0, 0)).isNull();
        }
    }
}
