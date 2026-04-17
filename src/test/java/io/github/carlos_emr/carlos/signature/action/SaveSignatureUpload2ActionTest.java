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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.mockito.*;
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

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private DigitalSignatureManager mockDigitalSignatureManager;
    @Mock private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    private SaveSignatureUpload2Action action;
    private String signatureKey;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

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
    void tearDown() {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (signatureKey != null) {
            new File(DigitalSignatureUtils.getTempFilePath(signatureKey)).delete();
        }
    }

    @Nested
    @DisplayName("HTTP method gate")
    class MethodGate {

        @Test
        @DisplayName("should send 405 with Allow: POST on GET")
        void shouldSend405_onGet() throws Exception {
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
        void shouldThrow_whenSessionMissing() {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(null);
            mockRequest.setParameter(DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY, signatureKey);

            assertThatThrownBy(() -> action.execute())
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("_con w");
        }

        @Test
        @DisplayName("should throw SecurityException when _con w is denied")
        void shouldThrow_whenConWDenied() {
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
        void shouldReturn400_whenKeyMissing() throws Exception {
            action.execute();
            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        }

        @Test
        @DisplayName("should return 400 and not write a file when signatureKey contains path-traversal")
        void shouldReturn400_whenKeyHasPathTraversal() throws Exception {
            mockRequest.setParameter(DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY, "../etc/passwd");

            action.execute();

            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            File temp = new File(DigitalSignatureUtils.getTempFilePath("../etc/passwd"));
            assertThat(temp).doesNotExist();
        }

        @Test
        @DisplayName("should return 400 when signatureKey contains special characters")
        void shouldReturn400_whenKeyHasSpecialChars() throws Exception {
            mockRequest.setParameter(DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY, "abc;rm-rf");
            action.execute();
            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
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
        void shouldReturn413_andCleanup_whenUploadTooLarge() throws Exception {
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
        void shouldReturn400_whenDemographicNoNotNumeric() throws Exception {
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
        @DisplayName("should return 200 with signatureId HTML fragment when manager returns a signature")
        void shouldReturn200_withSignatureIdFragment_whenManagerSucceeds() throws Exception {
            primeIpadUpload();
            mockRequest.setParameter("demographicNo", "42");
            mockRequest.setParameter(ModuleType.class.getSimpleName(), ModuleType.PRESCRIPTION.name());
            DigitalSignature saved = mock(DigitalSignature.class);
            when(saved.getId()).thenReturn(777);
            when(mockDigitalSignatureManager.processAndSaveDigitalSignature(
                    any(LoggedInInfo.class), eq(signatureKey), eq(42), eq(ModuleType.PRESCRIPTION)))
                    .thenReturn(saved);

            action.execute();

            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(mockResponse.getContentType()).startsWith("text/html");
            assertThat(mockResponse.getContentAsString())
                    .contains("name=\"signatureId\"")
                    .contains("value=\"777\"");
        }
    }
}
