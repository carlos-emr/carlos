/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 * Maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.signature.action;

import io.github.carlos_emr.carlos.commn.model.DigitalSignature;
import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;
import io.github.carlos_emr.carlos.managers.DigitalSignatureManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.DigitalSignatureUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

import org.apache.commons.codec.binary.Base64;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.owasp.encoder.Encode;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SaveSignatureUpload2Action}.
 *
 * <p>Covers the documented response contract: method gating, authorization,
 * signatureKey validation, IPAD Base64 branch, raw-stream size cap + cleanup,
 * DB-persist success/failure, and the HTML-fragment response.
 */
@DisplayName("SaveSignatureUpload2Action Unit Tests")
@Tag("unit")
@Tag("signature")
class SaveSignatureUpload2ActionTest extends CarlosUnitTestBase {

    private static final String VALID_KEY_PREFIX = "testkey";

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private DigitalSignatureManager mockDigitalSignatureManager;
    @Mock private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    private SaveSignatureUpload2Action action;
    private String signatureKey;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);

        // Fresh alphanumeric key per test so parallel runs don't collide on /tmp.
        signatureKey = VALID_KEY_PREFIX + UUID.randomUUID().toString().replace("-", "");

        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
        mockRequest.setMethod("POST");

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(DigitalSignatureManager.class, mockDigitalSignatureManager);

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_con"), eq("w"), isNull()))
                .thenReturn(true);

        action = new SaveSignatureUpload2Action();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (signatureKey != null) {
            new File(DigitalSignatureUtils.getTempFilePath(signatureKey)).delete();
        }
        // Release the inline mock-maker state from openMocks(this); leaving it open across every
        // test in the class accumulates registrations and corrupts later mockStatic stubbing.
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Nested
    @DisplayName("HTTP method gate")
    class MethodGate {

        @Test
        @DisplayName("should send 405 with Allow: POST on GET")
        void shouldReturn405_whenMethodIsGet() throws Exception {
            mockRequest.setMethod("GET");

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
        }

        @Test
        @DisplayName("should send 405 on DELETE")
        void shouldSend405_onDelete() throws Exception {
            mockRequest.setMethod("DELETE");
            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }
    }

    @Nested
    @DisplayName("Source-param validation")
    class SourceValidation {

        @Test
        @DisplayName("should return 400 when source is unknown")
        void shouldReturn400_whenSourceUnknown() throws Exception {
            mockRequest.setParameter(DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY, signatureKey);
            mockRequest.setParameter("source", "BROWSER");
            action.execute();
            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        }

        @Test
        @DisplayName("should return 400 when source is IPAD but signatureImage missing")
        void shouldReturn400_whenIpadSourceMissingImage() throws Exception {
            mockRequest.setParameter(DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY, signatureKey);
            mockRequest.setParameter("source", "IPAD");
            action.execute();
            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        }

        @Test
        @DisplayName("should return 413 when IPAD base64 payload exceeds encoded cap")
        void shouldReturn413_whenIpadBase64TooLarge() throws Exception {
            // MAX_BASE64_CHARS ≈ 5 MB * 4/3 ≈ 6.99 MB. Build a payload one char over.
            int maxBase64Chars = ((5 * 1024 * 1024 + 2) / 3) * 4;
            StringBuilder sb = new StringBuilder(maxBase64Chars + 10);
            sb.append("data:image/png;base64,");
            for (int i = 0; i < maxBase64Chars + 1; i++) sb.append('A');
            mockRequest.setParameter(DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY, signatureKey);
            mockRequest.setParameter("source", "IPAD");
            mockRequest.setParameter("signatureImage", sb.toString());

            action.execute();

            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            File temp = new File(DigitalSignatureUtils.getTempFilePath(signatureKey));
            assertThat(temp).doesNotExist();
        }
    }

    @Nested
    @DisplayName("Authorization gate")
    class AuthzGate {

        @Test
        @DisplayName("should throw SecurityException when session is missing")
        void shouldThrowSecurityException_whenLoggedInInfoIsNull() {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(null);
            mockRequest.setParameter(DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY, signatureKey);

            assertThatThrownBy(() -> action.execute())
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("_con w");
        }

        @Test
        @DisplayName("should throw SecurityException when _con w is denied")
        void shouldThrowSecurityException_whenMissingConWritePrivilege() {
            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_con"), eq("w"), isNull()))
                    .thenReturn(false);
            mockRequest.setParameter(DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY, signatureKey);

            assertThatThrownBy(() -> action.execute())
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("_con w");
        }
    }

    @Nested
    @DisplayName("signatureKey validation")
    class KeyValidation {

        @Test
        @DisplayName("should return 400 when signatureKey is missing")
        void shouldReturn400_whenSignatureKeyIsNull() throws Exception {
            action.execute();
            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        }

        @ParameterizedTest
        @ValueSource(strings = {"../foo", "foo/bar", "foo.bar", ""})
        @DisplayName("should return 400 when signatureKey is empty or contains non-alphanumeric characters")
        void shouldReturn400_whenSignatureKeyIsEmptyOrContainsNonAlphanumeric(String invalidSignatureKey) throws Exception {
            mockRequest.setParameter(DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY, invalidSignatureKey);
            action.execute();
            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        }

        @Test
        @DisplayName("should return 400 when path validation throws")
        void shouldReturn400_whenPathValidationThrows() throws Exception {
            mockRequest.setParameter(DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY, signatureKey);

            // CALLS_REAL_METHODS: keep getTempFilePath's real PathValidationUtils calls working and
            // override only validateExistingPath. A plain mockStatic stubs every method to null,
            // so getTempFilePath NPEs before the validateExistingPath call this test targets - and
            // whether that null surfaces is order-sensitive under the inline mock maker.
            try (MockedStatic<PathValidationUtils> pathValidationUtilsMock = mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS)) {
                pathValidationUtilsMock.when(() -> PathValidationUtils.validateExistingPath(any(File.class), any(File.class)))
                        .thenThrow(new SecurityException("simulated traversal"));

                action.execute();
            }

            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            assertThat(new File(DigitalSignatureUtils.getTempFilePath(signatureKey))).doesNotExist();
        }
    }

    @Nested
    @DisplayName("IPAD Base64 branch")
    class IpadBranch {

        @Test
        @DisplayName("should return 400 when Base64 payload decodes to empty bytes")
        void shouldReturn400_whenBase64DecodesToEmpty() throws Exception {
            mockRequest.setParameter(DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY, signatureKey);
            mockRequest.setParameter("source", "IPAD");
            // commons-codec Base64 ignores non-alphabet chars; input with no base64
            // chars at all decodes to a zero-length byte[].
            mockRequest.setParameter("signatureImage", "data:image/png;base64,!@#$%^");

            action.execute();

            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            File temp = new File(DigitalSignatureUtils.getTempFilePath(signatureKey));
            assertThat(temp).doesNotExist();
        }

        @Test
        @DisplayName("should return 400 when Base64 decode throws")
        void shouldReturn400_whenBase64DecodeFails() throws Exception {
            mockRequest.setParameter(DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY, signatureKey);
            mockRequest.setParameter("source", "IPAD");
            mockRequest.setParameter("signatureImage", "data:image/png;base64,garbage");

            try (MockedConstruction<Base64> ignored = mockConstruction(Base64.class,
                    (mock, context) -> when(mock.decode(any(byte[].class)))
                            .thenThrow(new IllegalArgumentException("bad base64")))) {
                action.execute();
            }

            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            assertThat(new File(DigitalSignatureUtils.getTempFilePath(signatureKey))).doesNotExist();
        }

        @Test
        @DisplayName("should write file and return 200 with signatureId empty when saveToDB is false")
        void shouldWriteFile_andReturn200_whenSaveToDbFalse() throws Exception {
            // 1x1 PNG pixel
            String dataUri = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABAQAAAAA3bvkkAAAAC0lEQVR42mNgAAIAAAUAAeImBZsAAAAASUVORK5CYII=";
            mockRequest.setParameter(DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY, signatureKey);
            mockRequest.setParameter("source", "IPAD");
            mockRequest.setParameter("signatureImage", dataUri);

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(mockResponse.getContentAsString())
                    .contains("name=\"signatureId\"")
                    .contains("value=\"\"");
            File written = new File(DigitalSignatureUtils.getTempFilePath(signatureKey));
            assertThat(written).exists();
            assertThat(written.length()).isGreaterThan(0);
            verifyNoInteractions(mockDigitalSignatureManager);
        }
    }

    @Nested
    @DisplayName("Raw-stream branch")
    class RawStreamBranch {

        @Test
        @DisplayName("should return 413 and delete partial file when upload exceeds 5 MB")
        void shouldReturn413_whenRawStreamUploadExceeds5MB() throws Exception {
            mockRequest.setParameter(DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY, signatureKey);
            // 5 MB + 1 byte
            byte[] oversized = new byte[(5 * 1024 * 1024) + 1];
            mockRequest.setContent(oversized);

            action.execute();

            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            File temp = new File(DigitalSignatureUtils.getTempFilePath(signatureKey));
            assertThat(temp).doesNotExist();
        }

        @Test
        @DisplayName("should delete temp file when IOException occurs during write")
        void shouldDeleteTempFile_whenIOExceptionDuringWrite() throws Exception {
            MockHttpServletRequest failingRequest = new MockHttpServletRequest() {
                @Override
                public ServletInputStream getInputStream() {
                    return new ServletInputStream() {
                        private final byte[] prefix = "abc".getBytes(StandardCharsets.US_ASCII);
                        private boolean firstRead = true;

                        @Override
                        public int read(byte[] b, int off, int len) throws IOException {
                            if (firstRead) {
                                firstRead = false;
                                System.arraycopy(prefix, 0, b, off, Math.min(len, prefix.length));
                                return Math.min(len, prefix.length);
                            }
                            throw new IOException("Simulated stream failure");
                        }

                        @Override
                        public int read() throws IOException {
                            throw new IOException("Single-byte read not supported by this test stream");
                        }

                        @Override
                        public boolean isFinished() {
                            return false;
                        }

                        @Override
                        public boolean isReady() {
                            return true;
                        }

                        @Override
                        public void setReadListener(ReadListener listener) {
                        }
                    };
                }
            };
            failingRequest.setMethod("POST");
            failingRequest.setParameter(DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY, signatureKey);
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(failingRequest);

            action.execute();

            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            assertThat(new File(DigitalSignatureUtils.getTempFilePath(signatureKey))).doesNotExist();
        }

        @Test
        @DisplayName("should write file and return 200 when upload is under the size cap")
        void shouldWriteFile_andReturn200_whenUnderCap() throws Exception {
            mockRequest.setParameter(DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY, signatureKey);
            byte[] payload = "hello signature".getBytes(StandardCharsets.US_ASCII);
            mockRequest.setContent(payload);

            action.execute();

            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            File temp = new File(DigitalSignatureUtils.getTempFilePath(signatureKey));
            assertThat(temp).exists();
            assertThat(Files.readAllBytes(temp.toPath())).isEqualTo(payload);
        }
    }

    @Nested
    @DisplayName("saveToDB branch")
    class SaveToDbBranch {

        private void primeIpadUpload() {
            String dataUri = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABAQAAAAA3bvkkAAAAC0lEQVR42mNgAAIAAAUAAeImBZsAAAAASUVORK5CYII=";
            mockRequest.setParameter(DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY, signatureKey);
            mockRequest.setParameter("source", "IPAD");
            mockRequest.setParameter("signatureImage", dataUri);
            mockRequest.setParameter("saveToDB", "true");
        }

        @Test
        @DisplayName("should return 400 when demographicNo is non-numeric")
        void shouldReturn400_whenDemographicNoIsNotInteger() throws Exception {
            primeIpadUpload();
            mockRequest.setParameter("demographicNo", "not-a-number");

            action.execute();

            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            verifyNoInteractions(mockDigitalSignatureManager);
        }

        @Test
        @DisplayName("should return 500 and delete temp file when manager returns null")
        void shouldReturn500_whenManagerReturnsNull() throws Exception {
            primeIpadUpload();
            mockRequest.setParameter("demographicNo", "42");
            when(mockDigitalSignatureManager.processAndSaveDigitalSignature(
                    any(LoggedInInfo.class), anyString(), anyInt(), any()))
                    .thenReturn(null);

            action.execute();

            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            File temp = new File(DigitalSignatureUtils.getTempFilePath(signatureKey));
            assertThat(temp).doesNotExist();
        }

        @Test
        @DisplayName("should return 500 and delete temp file when manager throws")
        void shouldReturn500_whenManagerThrows() throws Exception {
            primeIpadUpload();
            mockRequest.setParameter("demographicNo", "42");
            when(mockDigitalSignatureManager.processAndSaveDigitalSignature(
                    any(LoggedInInfo.class), anyString(), anyInt(), any()))
                    .thenThrow(new RuntimeException("db down"));

            action.execute();

            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            File temp = new File(DigitalSignatureUtils.getTempFilePath(signatureKey));
            assertThat(temp).doesNotExist();
        }

        @Test
        @DisplayName("should encode signatureId for HTML attribute when save succeeds")
        void shouldEncodeSignatureIdForHtmlAttribute_whenSaveSucceeds() throws Exception {
            primeIpadUpload();
            mockRequest.setParameter("demographicNo", "42");
            mockRequest.setParameter(ModuleType.class.getSimpleName(), ModuleType.PRESCRIPTION.name());
            DigitalSignature saved = mock(DigitalSignature.class);
            when(saved.getId()).thenReturn(777);
            when(mockDigitalSignatureManager.processAndSaveDigitalSignature(
                    any(LoggedInInfo.class), eq(signatureKey), eq(42), eq(ModuleType.PRESCRIPTION)))
                    .thenReturn(saved);

            // CALLS_REAL_METHODS: the action's logging path (LogSafe.sanitize -> Encode.forJava)
            // legitimately calls other Encode methods. A blanket mockStatic nulls them out and NPEs
            // inside LogSafe, so override only forHtmlAttribute and let the rest run for real. The
            // output assertion below already proves forHtmlAttribute("777") was called (its stubbed
            // value appears verbatim); a separate verify lambda would re-invoke the real method.
            try (MockedStatic<Encode> encodeMock = mockStatic(Encode.class, CALLS_REAL_METHODS)) {
                encodeMock.when(() -> Encode.forHtmlAttribute("777")).thenReturn("encoded-777");

                action.execute();

                assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
                assertThat(mockResponse.getContentType()).startsWith("text/html");
                assertThat(mockResponse.getContentAsString()).isEqualTo(
                        "<input type=\"hidden\" name=\"signatureId\" value=\"encoded-777\"/>");
            }
        }
    }
}
