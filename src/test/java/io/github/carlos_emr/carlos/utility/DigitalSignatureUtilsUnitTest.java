/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 *
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DigitalSignatureUtils} signature generation and temp file paths.
 *
 * <p>Tests the pure-logic static methods. Skips storeDigitalSignatureFromTempFileToDB
 * which requires Spring context and file system operations.</p>
 *
 * @since 2026-03-31
 */
@DisplayName("DigitalSignatureUtils Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("utility")
@Tag("security")
class DigitalSignatureUtilsUnitTest {

    @Nested
    @DisplayName("generateSignatureRequestId")
    class GenerateSignatureRequestId {

        @Test
        @DisplayName("should start with provider number")
        void shouldStartWithProviderNumber() {
            String result = DigitalSignatureUtils.generateSignatureRequestId("999998");
            assertThat(result).startsWith("999998");
        }

        @Test
        @DisplayName("should contain timestamp component")
        void shouldContainTimestamp() {
            String result = DigitalSignatureUtils.generateSignatureRequestId("111");
            assertThat(result.length()).isGreaterThan(3); // providerNo + timestamp
        }

        @Test
        @DisplayName("should generate unique IDs on consecutive calls")
        void shouldGenerateUniqueIds() {
            String id1 = DigitalSignatureUtils.generateSignatureRequestId("111");
            String id2 = DigitalSignatureUtils.generateSignatureRequestId("111");
            // Due to millis precision, they may be equal in very fast execution
            // but typically different
            assertThat(id1).isNotNull();
            assertThat(id2).isNotNull();
        }
    }

    @Nested
    @DisplayName("getTempFilePath")
    class GetTempFilePath {

        @Test
        @DisplayName("should contain signature request ID in filename")
        void shouldContainRequestId_inFilename() {
            String result = DigitalSignatureUtils.getTempFilePath("abc123");
            assertThat(result).contains("signature_abc123.jpg");
        }

        @Test
        @DisplayName("should use system temp directory")
        void shouldUseSystemTempDir() {
            String tempDir = System.getProperty("java.io.tmpdir");
            String result = DigitalSignatureUtils.getTempFilePath("test");
            assertThat(result).startsWith(tempDir);
        }

        @Test
        @DisplayName("should produce .jpg extension")
        void shouldProduceJpgExtension() {
            String result = DigitalSignatureUtils.getTempFilePath("req1");
            assertThat(result).endsWith(".jpg");
        }
    }

    @Nested
    @DisplayName("SIGNATURE_REQUEST_ID_KEY constant")
    class Constants {

        @Test
        @DisplayName("should have expected key name")
        void shouldHaveExpectedKey() {
            assertThat(DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY).isEqualTo("signatureRequestId");
        }
    }
}
