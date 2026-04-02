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
package io.github.carlos_emr.carlos.app;

import io.github.carlos_emr.carlos.app.MultiReadHttpServletRequest.ExposedByteArrayOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link MultiReadHttpServletRequest}.
 *
 * <p>Tests the {@link ExposedByteArrayOutputStream} freeze semantics, the
 * {@code parseMultipartFormFields} parser with oversized buffers, and the
 * zero-copy {@code CachedServletInputStream} behavior.</p>
 *
 * @since 2026-04-02
 */
@Tag("unit")
@DisplayName("MultiReadHttpServletRequest")
class MultiReadHttpServletRequestTest {

    // ------------------------------------------------------------------
    // ExposedByteArrayOutputStream
    // ------------------------------------------------------------------

    /**
     * Tests the freeze/read lifecycle of {@link ExposedByteArrayOutputStream}.
     *
     * <p>Verifies that all write methods are allowed before freeze and rejected after,
     * all read methods require frozen state, and copy-producing methods
     * ({@code toByteArray}, {@code toString}) are unconditionally disabled.</p>
     */
    @Nested
    @DisplayName("ExposedByteArrayOutputStream")
    class ExposedByteArrayOutputStreamTest {

        @Test
        @DisplayName("should allow writes before freeze")
        void shouldAllowWrites_beforeFreeze() {
            ExposedByteArrayOutputStream stream = new ExposedByteArrayOutputStream();
            stream.write(65); // 'A'
            stream.write(new byte[]{66, 67}, 0, 2); // 'B', 'C'
            stream.writeBytes(new byte[]{68}); // 'D'

            stream.freeze();
            assertThat(stream.getCount()).isEqualTo(4);
            byte[] buf = stream.getBuffer();
            assertThat(buf[0]).isEqualTo((byte) 'A');
            assertThat(buf[1]).isEqualTo((byte) 'B');
            assertThat(buf[2]).isEqualTo((byte) 'C');
            assertThat(buf[3]).isEqualTo((byte) 'D');
        }

        @Test
        @DisplayName("should throw on write(int) after freeze")
        void shouldThrow_onWriteIntAfterFreeze() {
            ExposedByteArrayOutputStream stream = new ExposedByteArrayOutputStream();
            stream.freeze();

            assertThatThrownBy(() -> stream.write(65))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("frozen");
        }

        @Test
        @DisplayName("should throw on write(byte[],int,int) after freeze")
        void shouldThrow_onWriteArrayAfterFreeze() {
            ExposedByteArrayOutputStream stream = new ExposedByteArrayOutputStream();
            stream.freeze();

            assertThatThrownBy(() -> stream.write(new byte[]{1, 2}, 0, 2))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("frozen");
        }

        @Test
        @DisplayName("should throw on writeBytes(byte[]) after freeze")
        void shouldThrow_onWriteBytesAfterFreeze() {
            ExposedByteArrayOutputStream stream = new ExposedByteArrayOutputStream();
            stream.freeze();

            assertThatThrownBy(() -> stream.writeBytes(new byte[]{1}))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("frozen");
        }

        @Test
        @DisplayName("should throw on reset() after freeze")
        void shouldThrow_onResetAfterFreeze() {
            ExposedByteArrayOutputStream stream = new ExposedByteArrayOutputStream();
            stream.write(65);
            stream.freeze();

            assertThatThrownBy(() -> stream.reset())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("frozen");
        }

        @Test
        @DisplayName("should throw on toByteArray()")
        void shouldThrow_onToByteArray() {
            ExposedByteArrayOutputStream stream = new ExposedByteArrayOutputStream();
            stream.write(65);

            assertThatThrownBy(() -> stream.toByteArray())
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("getBuffer()");
        }

        @Test
        @DisplayName("should throw on getBuffer() before freeze")
        void shouldThrow_onGetBufferBeforeFreeze() {
            ExposedByteArrayOutputStream stream = new ExposedByteArrayOutputStream();
            stream.write(65);

            assertThatThrownBy(() -> stream.getBuffer())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("frozen");
        }

        @Test
        @DisplayName("should throw on getCount() before freeze")
        void shouldThrow_onGetCountBeforeFreeze() {
            ExposedByteArrayOutputStream stream = new ExposedByteArrayOutputStream();
            stream.write(65);

            assertThatThrownBy(() -> stream.getCount())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("frozen");
        }

        @Test
        @DisplayName("should return buffer larger than count after freeze")
        void shouldReturnBufferLargerThanCount_afterFreeze() {
            ExposedByteArrayOutputStream stream = new ExposedByteArrayOutputStream();
            // Write enough to trigger at least one buffer growth
            byte[] data = "Hello".getBytes(StandardCharsets.UTF_8);
            stream.write(data, 0, data.length);
            stream.freeze();

            assertThat(stream.getCount()).isEqualTo(data.length);
            // Internal buffer is typically over-allocated (default initial size is 32)
            assertThat(stream.getBuffer().length).isGreaterThanOrEqualTo(stream.getCount());
        }

        @Test
        @DisplayName("should return correct data via ByteArrayInputStream after freeze")
        void shouldReturnCorrectData_viaByteArrayInputStreamAfterFreeze() throws Exception {
            ExposedByteArrayOutputStream stream = new ExposedByteArrayOutputStream();
            byte[] data = "test body content".getBytes(StandardCharsets.UTF_8);
            stream.write(data, 0, data.length);
            stream.freeze();

            // Simulate CachedServletInputStream's zero-copy read
            ByteArrayInputStream in = new ByteArrayInputStream(
                    stream.getBuffer(), 0, stream.getCount());

            byte[] result = in.readAllBytes();
            assertThat(result).isEqualTo(data);
        }

        // writeTo() tests

        @Test
        @DisplayName("should throw on writeTo() before freeze")
        void shouldThrow_onWriteToBeforeFreeze() {
            ExposedByteArrayOutputStream stream = new ExposedByteArrayOutputStream();
            stream.write(65);
            ByteArrayOutputStream target = new ByteArrayOutputStream();

            assertThatThrownBy(() -> stream.writeTo(target))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("frozen");
        }

        @Test
        @DisplayName("should write correct data via writeTo() after freeze")
        void shouldWriteCorrectData_viaWriteToAfterFreeze() throws Exception {
            ExposedByteArrayOutputStream stream = new ExposedByteArrayOutputStream();
            byte[] data = "writeTo test".getBytes(StandardCharsets.UTF_8);
            stream.write(data, 0, data.length);
            stream.freeze();

            ByteArrayOutputStream target = new ByteArrayOutputStream();
            stream.writeTo(target);

            assertThat(target.toByteArray()).isEqualTo(data);
        }

        // size() tests

        @Test
        @DisplayName("should throw on size() before freeze")
        void shouldThrow_onSizeBeforeFreeze() {
            ExposedByteArrayOutputStream stream = new ExposedByteArrayOutputStream();
            stream.write(65);

            assertThatThrownBy(() -> stream.size())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("frozen");
        }

        @Test
        @DisplayName("should return count via size() after freeze")
        void shouldReturnCount_viaSizeAfterFreeze() {
            ExposedByteArrayOutputStream stream = new ExposedByteArrayOutputStream();
            byte[] data = "size test".getBytes(StandardCharsets.UTF_8);
            stream.write(data, 0, data.length);
            stream.freeze();

            assertThat(stream.size()).isEqualTo(data.length);
            assertThat(stream.size()).isEqualTo(stream.getCount());
        }

        // toString() tests

        @Test
        @DisplayName("should return safe descriptor from toString()")
        void shouldReturnSafeDescriptor_fromToString() {
            ExposedByteArrayOutputStream stream = new ExposedByteArrayOutputStream();
            assertThat(stream.toString()).contains("frozen=false", "count=?");

            stream.write(65);
            stream.freeze();
            assertThat(stream.toString()).contains("frozen=true", "count=1");
        }

        @Test
        @DisplayName("should throw on toString(String)")
        void shouldThrow_onToStringWithCharsetName() {
            ExposedByteArrayOutputStream stream = new ExposedByteArrayOutputStream();

            assertThatThrownBy(() -> stream.toString("UTF-8"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("should throw on toString(Charset)")
        void shouldThrow_onToStringWithCharset() {
            ExposedByteArrayOutputStream stream = new ExposedByteArrayOutputStream();

            assertThatThrownBy(() -> stream.toString(StandardCharsets.UTF_8))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @SuppressWarnings("deprecation")
        @Test
        @DisplayName("should throw on toString(int)")
        void shouldThrow_onToStringWithHiByte() {
            ExposedByteArrayOutputStream stream = new ExposedByteArrayOutputStream();

            assertThatThrownBy(() -> stream.toString(0))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // ------------------------------------------------------------------
    // parseMultipartFormFields
    // ------------------------------------------------------------------

    /**
     * Tests the {@code parseMultipartFormFields} parser, focusing on the
     * {@code bodyLength} parameter that limits parsing to valid bytes within
     * an over-allocated buffer (the zero-copy contract).
     */
    @Nested
    @DisplayName("parseMultipartFormFields")
    class ParseMultipartFormFieldsTest {

        private static final String BOUNDARY = "----WebKitFormBoundary7MA4YWxkTrZu0gW";
        private static final String CONTENT_TYPE = "multipart/form-data; boundary=" + BOUNDARY;

        private byte[] buildMultipartBody(String... nameValuePairs) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < nameValuePairs.length; i += 2) {
                sb.append("--").append(BOUNDARY).append("\r\n");
                sb.append("Content-Disposition: form-data; name=\"")
                  .append(nameValuePairs[i]).append("\"\r\n");
                sb.append("\r\n");
                sb.append(nameValuePairs[i + 1]).append("\r\n");
            }
            sb.append("--").append(BOUNDARY).append("--\r\n");
            return sb.toString().getBytes(StandardCharsets.ISO_8859_1);
        }

        @Test
        @DisplayName("should parse single text field")
        void shouldParseSingleTextField() {
            byte[] body = buildMultipartBody("OWASP-CSRFTOKEN", "abc123");

            Map<String, List<String>> result = MultiReadHttpServletRequest
                    .parseMultipartFormFields(body, body.length, CONTENT_TYPE);

            assertThat(result).containsKey("OWASP-CSRFTOKEN");
            assertThat(result.get("OWASP-CSRFTOKEN")).containsExactly("abc123");
        }

        @Test
        @DisplayName("should parse multiple text fields")
        void shouldParseMultipleTextFields() {
            byte[] body = buildMultipartBody(
                    "token", "csrf-value",
                    "name", "test-user");

            Map<String, List<String>> result = MultiReadHttpServletRequest
                    .parseMultipartFormFields(body, body.length, CONTENT_TYPE);

            assertThat(result).hasSize(2);
            assertThat(result.get("token")).containsExactly("csrf-value");
            assertThat(result.get("name")).containsExactly("test-user");
        }

        @Test
        @DisplayName("should ignore garbage bytes beyond bodyLength in oversized buffer")
        void shouldIgnoreGarbageBytes_beyondBodyLengthInOversizedBuffer() {
            byte[] validBody = buildMultipartBody("field1", "value1");
            int validLength = validBody.length;

            // Create an oversized buffer with a fake boundary and field after the valid data
            byte[] garbageField = buildMultipartBody("phantom", "should-not-appear");
            byte[] oversizedBuffer = new byte[validLength + garbageField.length + 100];
            System.arraycopy(validBody, 0, oversizedBuffer, 0, validLength);
            System.arraycopy(garbageField, 0, oversizedBuffer, validLength, garbageField.length);

            Map<String, List<String>> result = MultiReadHttpServletRequest
                    .parseMultipartFormFields(oversizedBuffer, validLength, CONTENT_TYPE);

            assertThat(result).containsOnlyKeys("field1");
            assertThat(result.get("field1")).containsExactly("value1");
            assertThat(result).doesNotContainKey("phantom");
        }

        @Test
        @DisplayName("should return empty map when bodyLength is zero")
        void shouldReturnEmptyMap_whenBodyLengthIsZero() {
            byte[] body = buildMultipartBody("field", "value");

            Map<String, List<String>> result = MultiReadHttpServletRequest
                    .parseMultipartFormFields(body, 0, CONTENT_TYPE);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty map for missing boundary")
        void shouldReturnEmptyMap_forMissingBoundary() {
            byte[] body = buildMultipartBody("field", "value");

            Map<String, List<String>> result = MultiReadHttpServletRequest
                    .parseMultipartFormFields(body, body.length, "multipart/form-data");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should skip file upload parts")
        void shouldSkipFileUploadParts() {
            StringBuilder sb = new StringBuilder();
            // Text field
            sb.append("--").append(BOUNDARY).append("\r\n");
            sb.append("Content-Disposition: form-data; name=\"token\"\r\n");
            sb.append("\r\n");
            sb.append("csrf123\r\n");
            // File field
            sb.append("--").append(BOUNDARY).append("\r\n");
            sb.append("Content-Disposition: form-data; name=\"file\"; filename=\"test.pdf\"\r\n");
            sb.append("Content-Type: application/pdf\r\n");
            sb.append("\r\n");
            sb.append("fake-pdf-content\r\n");
            // Close
            sb.append("--").append(BOUNDARY).append("--\r\n");

            byte[] body = sb.toString().getBytes(StandardCharsets.ISO_8859_1);

            Map<String, List<String>> result = MultiReadHttpServletRequest
                    .parseMultipartFormFields(body, body.length, CONTENT_TYPE);

            assertThat(result).containsOnlyKeys("token");
            assertThat(result.get("token")).containsExactly("csrf123");
        }

        @Test
        @DisplayName("should not throw when bodyLength truncates mid-boundary")
        void shouldNotThrow_whenBodyLengthTruncatesMidBoundary() {
            byte[] body = buildMultipartBody("field1", "value1", "field2", "value2");

            // Truncate so the second boundary marker is incomplete — the parser
            // can't find the closing boundary for field1, so it returns empty.
            // The key assertion is that no ArrayIndexOutOfBoundsException is thrown.
            String bodyStr = new String(body, StandardCharsets.ISO_8859_1);
            int secondBoundaryPos = bodyStr.indexOf("--" + BOUNDARY, 1);
            int truncatedLength = secondBoundaryPos + 5;

            Map<String, List<String>> result = MultiReadHttpServletRequest
                    .parseMultipartFormFields(body, truncatedLength, CONTENT_TYPE);

            // Parser gracefully returns without crashing; field2 is never found
            assertThat(result).doesNotContainKey("field2");
        }
    }

    // ------------------------------------------------------------------
    // MultiReadHttpServletRequest — public API integration
    // ------------------------------------------------------------------

    /**
     * Tests the zero-copy path through the public {@code getInputStream()},
     * {@code getReader()}, and {@code getParameter()} APIs using a real
     * {@link MultiReadHttpServletRequest} wrapping a Spring {@link MockHttpServletRequest}.
     */
    @Nested
    @DisplayName("MultiReadHttpServletRequest public API")
    class PublicApiTest {

        @Test
        @DisplayName("should return correct body on first getInputStream() read")
        void shouldReturnCorrectBody_onFirstGetInputStream() throws IOException {
            byte[] body = "hello world".getBytes(StandardCharsets.UTF_8);
            MockHttpServletRequest mock = new MockHttpServletRequest();
            mock.setContent(body);

            MultiReadHttpServletRequest wrapper = new MultiReadHttpServletRequest(mock);
            byte[] result = wrapper.getInputStream().readAllBytes();

            assertThat(result).isEqualTo(body);
        }

        @Test
        @DisplayName("should return identical body on second getInputStream() read")
        void shouldReturnIdenticalBody_onSecondGetInputStream() throws IOException {
            byte[] body = "multi-read test body".getBytes(StandardCharsets.UTF_8);
            MockHttpServletRequest mock = new MockHttpServletRequest();
            mock.setContent(body);

            MultiReadHttpServletRequest wrapper = new MultiReadHttpServletRequest(mock);
            byte[] first = wrapper.getInputStream().readAllBytes();
            byte[] second = wrapper.getInputStream().readAllBytes();

            assertThat(first).isEqualTo(body);
            assertThat(second).isEqualTo(body);
        }

        @Test
        @DisplayName("should return correct text from getReader()")
        void shouldReturnCorrectText_fromGetReader() throws IOException {
            String text = "reader test content";
            MockHttpServletRequest mock = new MockHttpServletRequest();
            mock.setContent(text.getBytes(StandardCharsets.UTF_8));
            mock.setCharacterEncoding("UTF-8");

            MultiReadHttpServletRequest wrapper = new MultiReadHttpServletRequest(mock);
            StringBuilder sb = new StringBuilder();
            try (var reader = wrapper.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            assertThat(sb.toString()).isEqualTo(text);
        }

        @Test
        @DisplayName("should return empty body for zero-length content")
        void shouldReturnEmptyBody_forZeroLengthContent() throws IOException {
            MockHttpServletRequest mock = new MockHttpServletRequest();
            mock.setContent(new byte[0]);

            MultiReadHttpServletRequest wrapper = new MultiReadHttpServletRequest(mock);
            byte[] result = wrapper.getInputStream().readAllBytes();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should extract CSRF token via getParameter() from multipart body")
        void shouldExtractCsrfToken_viaGetParameterFromMultipartBody() throws IOException {
            String boundary = "----TestBoundary";
            String body = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"OWASP-CSRFTOKEN\"\r\n"
                    + "\r\n"
                    + "token-value-123\r\n"
                    + "--" + boundary + "--\r\n";

            MockHttpServletRequest mock = new MockHttpServletRequest();
            mock.setContentType("multipart/form-data; boundary=" + boundary);
            mock.setContent(body.getBytes(StandardCharsets.ISO_8859_1));

            MultiReadHttpServletRequest wrapper = new MultiReadHttpServletRequest(mock);
            String token = wrapper.getParameter("OWASP-CSRFTOKEN");

            assertThat(token).isEqualTo("token-value-123");
        }

        @Test
        @DisplayName("should allow getInputStream() after getParameter() on multipart request")
        void shouldAllowGetInputStream_afterGetParameterOnMultipartRequest() throws IOException {
            String boundary = "----TestBoundary";
            String body = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"field1\"\r\n"
                    + "\r\n"
                    + "value1\r\n"
                    + "--" + boundary + "--\r\n";
            byte[] bodyBytes = body.getBytes(StandardCharsets.ISO_8859_1);

            MockHttpServletRequest mock = new MockHttpServletRequest();
            mock.setContentType("multipart/form-data; boundary=" + boundary);
            mock.setContent(bodyBytes);

            MultiReadHttpServletRequest wrapper = new MultiReadHttpServletRequest(mock);

            // getParameter() triggers cacheInputStream() + multipart parsing
            assertThat(wrapper.getParameter("field1")).isEqualTo("value1");

            // getInputStream() should still return the full body
            byte[] result = wrapper.getInputStream().readAllBytes();
            assertThat(result).isEqualTo(bodyBytes);
        }
    }
}
