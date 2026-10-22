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
package io.github.carlos_emr.carlos.eform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The server half of the eForm signature data-URI carrier (#3552).
 *
 * <p>{@link EFormDataUriCarrier#PARAMETER} is the one eForm save argument the WAF leaves unscored for
 * XSS, SQL injection and command injection, so the carrier's acceptance rule is the security
 * boundary: only a raster image or jSignature stroke data URI may come through, and anything else
 * refuses the save instead of storing it. The page script mirrors the same rule; the browser check
 * {@code scripts/eform-toolbar-browser-check.js} and node test
 * {@code scripts/eform-data-uri-carrier.test.js} pin that side.</p>
 *
 * @since 2026-10-22
 */
@Tag("unit")
@Tag("security")
@Tag("eform")
@DisplayName("eForm data-URI carrier")
class EFormDataUriCarrierUnitTest {

    /** A real 1x1 PNG, as canvas.toDataURL() writes one. */
    static final String PNG = "data:image/png;base64,"
            + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl5n2QAAAAASUVORK5CYII=";
    static final String JSIGNATURE = "data:image/jsignature;base30,3E13Z5Y5_1O24Z66_1O1Z3_3E2Z4";

    private static String dataUri(String type, byte... bytes) {
        return "data:image/" + type + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

    @Test
    @DisplayName("should decide every shared case exactly as the page script does")
    void shouldMatchPageScript_forSharedCases() throws IOException {
        // The same file drives scripts/eform-data-uri-carrier.test.js against eform_floating_toolbar.js.
        JsonNode cases = new ObjectMapper().readTree(Files.readString(
                resolveProjectPath(Path.of("scripts", "fixtures", "eform", "data-uri-carrier-cases.json")),
                StandardCharsets.UTF_8)).get("cases");
        assertThat(cases).isNotEmpty();
        for (JsonNode testCase : cases) {
            assertThat(EFormDataUriCarrier.isAcceptedDataUri(testCase.get("value").asText()))
                    .as(testCase.get("name").asText())
                    .isEqualTo(testCase.get("accepted").asBoolean());
        }
    }

    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of(System.getProperty(
                "maven.multiModuleProjectDirectory",
                System.getProperty("user.dir"))).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return relativePath;
    }

    @Nested
    @DisplayName("accepted values")
    class Accepted {

        @Test
        void shouldAcceptCanvasPng_forSignaturePad() {
            assertThat(EFormDataUriCarrier.isAcceptedDataUri(PNG)).isTrue();
        }

        @Test
        void shouldAcceptJSignatureStrokes_withBase30Alphabet() {
            assertThat(EFormDataUriCarrier.isAcceptedDataUri(JSIGNATURE)).isTrue();
        }

        @Test
        void shouldAcceptEmptyJSignature_forClearedPad() {
            // jSignature writes an empty stroke list for a cleared pad; it is still harmless.
            assertThat(EFormDataUriCarrier.isAcceptedDataUri("data:image/jsignature;base30,")).isTrue();
        }

        @Test
        void shouldAcceptEachRasterFormat_whenBytesMatchItsSignature() {
            assertThat(EFormDataUriCarrier.isAcceptedDataUri(
                    dataUri("jpeg", bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10)))).isTrue();
            assertThat(EFormDataUriCarrier.isAcceptedDataUri(
                    dataUri("gif", "GIF89a\u0001\u0000".getBytes(StandardCharsets.ISO_8859_1)))).isTrue();
            assertThat(EFormDataUriCarrier.isAcceptedDataUri(
                    dataUri("webp", "RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1)))).isTrue();
        }

        @Test
        void shouldAcceptLargestPayload_atDecodedLimit() {
            byte[] image = new byte[EFormDataUriCarrier.MAX_BASE64_CHARS / 4 * 3];
            System.arraycopy(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A), 0, image, 0, 8);
            String value = dataUri("png", image);
            assertThat(value.length() - "data:image/png;base64,".length()).isEqualTo(EFormDataUriCarrier.MAX_BASE64_CHARS);
            assertThat(EFormDataUriCarrier.isAcceptedDataUri(value)).isTrue();
        }
    }

    @Nested
    @DisplayName("refused values")
    class Refused {

        @ParameterizedTest
        @ValueSource(strings = {
            "data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==",
            "data:image/svg+xml;base64,PHN2ZyBvbmxvYWQ9YWxlcnQoMSk+",
            "javascript:alert(1)",
            "data:image/png;base64,iVBORw0KGgo=\"><script>alert(1)</script>",
            "data:image/png;base64,iVBORw0K Ggo=",
            "data:image/png;base64,iVBORw0KGgo",
            "data:image/png;base64,",
            "DATA:image/png;base64,iVBORw0KGgoAAAANSUhEUg==",
            "data:image/PNG;base64,iVBORw0KGgoAAAANSUhEUg==",
            "data:image/jsignature;base30,3E1<script>",
            "data:image/jsignature;base30,a b",
            "image/jsignature;base30,3E13Z5Y5",
            " data:image/png;base64,iVBORw0KGgoAAAANSUhEUg==",
            "",
        })
        void shouldRefuse_forAnythingButAnImageDataUri(String value) {
            assertThat(EFormDataUriCarrier.isAcceptedDataUri(value)).isFalse();
        }

        @Test
        void shouldRefuseNull_forMissingValue() {
            assertThat(EFormDataUriCarrier.isAcceptedDataUri(null)).isFalse();
        }

        @Test
        void shouldRefuseRaster_whenBytesAreNotThatFormat() {
            // Labelled PNG, but the bytes are HTML: the label must be honest.
            assertThat(EFormDataUriCarrier.isAcceptedDataUri(
                    dataUri("png", "<html><script>alert(1)</script>".getBytes(StandardCharsets.US_ASCII)))).isFalse();
            assertThat(EFormDataUriCarrier.isAcceptedDataUri(
                    dataUri("jpeg", bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)))).isFalse();
            assertThat(EFormDataUriCarrier.isAcceptedDataUri(
                    dataUri("webp", "RIFF\u0000\u0000\u0000\u0000AVI LIST".getBytes(StandardCharsets.ISO_8859_1)))).isFalse();
        }

        @Test
        void shouldRefusePayload_overDecodedLimit() {
            byte[] image = new byte[EFormDataUriCarrier.MAX_BASE64_CHARS / 4 * 3 + 3];
            System.arraycopy(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A), 0, image, 0, 8);
            assertThat(EFormDataUriCarrier.isAcceptedDataUri(dataUri("png", image))).isFalse();
        }

        @Test
        void shouldRefuseJSignature_overLengthLimit() {
            char[] strokes = new char[EFormDataUriCarrier.MAX_BASE30_CHARS + 1];
            Arrays.fill(strokes, 'a');
            assertThat(EFormDataUriCarrier.isAcceptedDataUri(
                    "data:image/jsignature;base30," + new String(strokes))).isFalse();
        }
    }

    @Nested
    @DisplayName("restore")
    class Restore {

        @Test
        void shouldReplaceEachReference_withItsCarriedValue() throws Exception {
            List<String> values = new ArrayList<>(List.of(
                    "Routine follow up.", "carlos-data-uri:1", "carlos-data-uri:0", "carlos-data-uri:1"));

            int restored = EFormDataUriCarrier.restore(values, new String[] {PNG, JSIGNATURE});

            assertThat(restored).isEqualTo(3);
            assertThat(values).containsExactly("Routine follow up.", JSIGNATURE, PNG, JSIGNATURE);
        }

        @Test
        void shouldLeaveValuesUnchanged_whenNothingIsCarried() throws Exception {
            // Text that merely reads like a reference stays exactly as the clinician typed it.
            List<String> values = new ArrayList<>(List.of("carlos-data-uri:0", "carlos-data-uri:x"));

            assertThat(EFormDataUriCarrier.restore(values, null)).isZero();
            assertThat(EFormDataUriCarrier.restore(values, new String[0])).isZero();
            assertThat(values).containsExactly("carlos-data-uri:0", "carlos-data-uri:x");
        }

        @Test
        void shouldIgnoreUnreferencedValidValue_whenNoFieldNamesIt() throws Exception {
            List<String> values = new ArrayList<>(List.of("Routine follow up."));

            assertThat(EFormDataUriCarrier.restore(values, new String[] {PNG})).isZero();
            assertThat(values).containsExactly("Routine follow up.");
        }

        @Test
        void shouldRefuseSubmission_whenAnyCarriedValueIsNotAnImage() {
            // Referenced or not: the page never sends an invalid value, so one means tampering.
            List<String> values = new ArrayList<>(List.of("carlos-data-uri:0"));

            assertThatThrownBy(() -> EFormDataUriCarrier.restore(values,
                    new String[] {PNG, "data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg=="}))
                    .isInstanceOf(EFormDataUriCarrier.InvalidCarriedValueException.class)
                    .hasMessageNotContaining("PHNjcmlwdD5");
            assertThat(values).containsExactly("carlos-data-uri:0");
        }

        @ParameterizedTest
        @ValueSource(strings = {"carlos-data-uri:2", "carlos-data-uri:01", "carlos-data-uri:-1",
            "carlos-data-uri:", "carlos-data-uri:0 ", "carlos-data-uri:1000"})
        void shouldRefuseSubmission_whenReferenceIsMissingOrMalformed(String reference) {
            List<String> values = new ArrayList<>(List.of(reference));

            assertThatThrownBy(() -> EFormDataUriCarrier.restore(values, new String[] {PNG, JSIGNATURE}))
                    .isInstanceOf(EFormDataUriCarrier.InvalidCarriedValueException.class);
        }

        @Test
        void shouldRefuseSubmission_whenTooManyValuesAreCarried() {
            String[] carried = new String[EFormDataUriCarrier.MAX_VALUES + 1];
            Arrays.fill(carried, PNG);

            assertThatThrownBy(() -> EFormDataUriCarrier.restore(new ArrayList<>(), carried))
                    .isInstanceOf(EFormDataUriCarrier.InvalidCarriedValueException.class);
        }

        @Test
        void shouldAcceptMaximumCarriedCount_atLimit() throws Exception {
            String[] carried = new String[EFormDataUriCarrier.MAX_VALUES];
            Arrays.fill(carried, PNG);
            List<String> values = new ArrayList<>(List.of("carlos-data-uri:255"));

            assertThat(EFormDataUriCarrier.restore(values, carried)).isEqualTo(1);
            assertThat(values).containsExactly(PNG);
        }
    }
}
