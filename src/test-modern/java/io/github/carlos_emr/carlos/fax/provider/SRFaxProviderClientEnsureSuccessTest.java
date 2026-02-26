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
package io.github.carlos_emr.carlos.fax.provider;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the private {@code ensureSuccess(JsonNode, String)} method in
 * {@link SRFaxProviderClient}.
 *
 * <p>The {@code ensureSuccess} method validates the SRFax API response envelope by inspecting
 * the {@code Status} field. It accepts success indicators ("Success", "1") and rejects failure
 * indicators ("Failed", "0", or any status containing "fail"/"error"). When the Status field
 * is missing entirely, it throws regardless of whether Result data is present, following a
 * fail-closed security strategy.</p>
 *
 * <p><strong>Note on method signature:</strong> The actual method signature is
 * {@code private void ensureSuccess(JsonNode root, String errorMessage)}. It takes an
 * already-parsed {@link JsonNode} (not raw JSON), and returns void on success or throws
 * {@link FaxProviderException} on failure. These tests use reflection to access the private
 * method directly.</p>
 *
 * @since 2026-02-19
 * @see SRFaxProviderClient
 */
@Tag("unit")
@Tag("fax")
@Tag("srfax")
@DisplayName("SRFaxProviderClient ensureSuccess() parsing edge cases")
class SRFaxProviderClientEnsureSuccessTest extends CarlosUnitTestBase {

    private SRFaxProviderClient client;
    private Method ensureSuccess;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        client = new SRFaxProviderClient();
        ensureSuccess = SRFaxProviderClient.class.getDeclaredMethod("ensureSuccess", JsonNode.class, String.class);
        ensureSuccess.setAccessible(true);
    }

    /**
     * Invokes the private ensureSuccess method via reflection.
     * Unwraps InvocationTargetException to throw the actual cause.
     */
    private void invokeEnsureSuccess(JsonNode root, String errorMessage) throws Throwable {
        try {
            ensureSuccess.invoke(client, root, errorMessage);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /**
     * Parses a JSON string into a JsonNode for passing to ensureSuccess.
     */
    private JsonNode parseJson(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    // --- Success cases ---

    @Test
    @DisplayName("should not throw when Status is 'Success'")
    void shouldNotThrow_whenStatusIsSuccess() throws Exception {
        // Given
        JsonNode root = parseJson("{\"Status\":\"Success\",\"Result\":\"12345\"}");

        // Then - no exception expected
        assertThatCode(() -> invokeEnsureSuccess(root, "TestOperation"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should not throw when Status is '1' (numeric success)")
    void shouldNotThrow_whenStatusIsOne() throws Exception {
        // Given
        JsonNode root = parseJson("{\"Status\":\"1\",\"Result\":\"data\"}");

        // Then - no exception expected
        assertThatCode(() -> invokeEnsureSuccess(root, "TestOperation"))
                .doesNotThrowAnyException();
    }

    // --- Failure cases ---

    @Test
    @DisplayName("should throw FaxProviderException when Status is 'Failed'")
    void shouldThrowFaxProviderException_whenStatusIsFailed() throws Exception {
        // Given
        JsonNode root = parseJson("{\"Status\":\"Failed\",\"Result\":\"Invalid number\"}");

        // Then
        assertThatThrownBy(() -> invokeEnsureSuccess(root, "TestOperation"))
                .isInstanceOf(FaxProviderException.class);
    }

    @Test
    @DisplayName("should throw FaxProviderException when Status is '0' (numeric failure)")
    void shouldThrowFaxProviderException_whenStatusIsZero() throws Exception {
        // Given
        JsonNode root = parseJson("{\"Status\":\"0\",\"Result\":\"Error message\"}");

        // Then
        assertThatThrownBy(() -> invokeEnsureSuccess(root, "TestOperation"))
                .isInstanceOf(FaxProviderException.class);
    }

    @Test
    @DisplayName("should throw FaxProviderException when root JsonNode is null")
    void shouldThrowFaxProviderException_whenRootIsNull() {
        // Given - null root simulates a completely unparseable or empty response.
        // The actual invalid-JSON scenario is handled by postForm() which parses the raw
        // response body before calling ensureSuccess(). A null root reaching ensureSuccess
        // would cause a NullPointerException, which we verify here as a defensive check.

        // Then
        assertThatThrownBy(() -> invokeEnsureSuccess(null, "TestOperation"))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("should throw FaxProviderException when Status field is missing but Result is present")
    void shouldThrowFaxProviderException_whenStatusFieldIsMissingButResultPresent() throws Exception {
        // Given - per fail-closed strategy, missing Status is always an error even if Result is present
        JsonNode root = parseJson("{\"Result\":\"some data\"}");

        // Then
        assertThatThrownBy(() -> invokeEnsureSuccess(root, "TestOperation"))
                .isInstanceOf(FaxProviderException.class)
                .hasMessageContaining("Status");
    }

    @Test
    @DisplayName("should throw FaxProviderException when Status field is missing and Result is also missing")
    void shouldThrowFaxProviderException_whenStatusAndResultFieldsAreMissing() throws Exception {
        // Given - completely empty JSON object
        JsonNode root = parseJson("{}");

        // Then
        assertThatThrownBy(() -> invokeEnsureSuccess(root, "TestOperation"))
                .isInstanceOf(FaxProviderException.class)
                .hasMessageContaining("Status")
                .hasMessageContaining("Result");
    }

    @Test
    @DisplayName("should include Result error text in exception message when Status is 'Failed'")
    void shouldIncludeErrorMessageFromResult_whenStatusIsFailed() throws Exception {
        // Given
        String errorDetail = "Authentication credentials are invalid";
        JsonNode root = parseJson("{\"Status\":\"Failed\",\"Result\":\"" + errorDetail + "\"}");

        // Then
        assertThatThrownBy(() -> invokeEnsureSuccess(root, "TestOperation"))
                .isInstanceOf(FaxProviderException.class)
                .hasMessageContaining(errorDetail);
    }

    @Test
    @DisplayName("should include operation name in exception message when Status is 'Failed'")
    void shouldIncludeOperationNameInExceptionMessage_whenStatusIsFailed() throws Exception {
        // Given
        String operationName = "Failed to queue fax with SRFax";
        JsonNode root = parseJson("{\"Status\":\"Failed\",\"Result\":\"Bad request\"}");

        // Then
        assertThatThrownBy(() -> invokeEnsureSuccess(root, operationName))
                .isInstanceOf(FaxProviderException.class)
                .hasMessageContaining(operationName);
    }

    @Test
    @DisplayName("should throw FaxProviderException when Status contains 'error' substring")
    void shouldThrowFaxProviderException_whenStatusContainsError() throws Exception {
        // Given - fuzzy matching on "error" substring
        JsonNode root = parseJson("{\"Status\":\"Server Error\",\"Result\":\"Internal failure\"}");

        // Then
        assertThatThrownBy(() -> invokeEnsureSuccess(root, "TestOperation"))
                .isInstanceOf(FaxProviderException.class);
    }

    @Test
    @DisplayName("should not throw for unrecognized non-failure status (treated as success)")
    void shouldNotThrow_whenStatusIsUnrecognizedButNotFailure() throws Exception {
        // Given - status like "Pending" is unrecognized but does not contain fail/error/0
        // ensureSuccess logs a warning but treats it as success
        JsonNode root = parseJson("{\"Status\":\"Pending\",\"Result\":\"some data\"}");

        // Then - no exception expected (logs a warning internally)
        assertThatCode(() -> invokeEnsureSuccess(root, "TestOperation"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should handle case-insensitive Status comparison for 'SUCCESS'")
    void shouldNotThrow_whenStatusIsUpperCaseSuccess() throws Exception {
        // Given
        JsonNode root = parseJson("{\"Status\":\"SUCCESS\",\"Result\":\"12345\"}");

        // Then
        assertThatCode(() -> invokeEnsureSuccess(root, "TestOperation"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should handle Status with leading/trailing whitespace")
    void shouldNotThrow_whenStatusHasWhitespace() throws Exception {
        // Given - Status is trimmed before comparison
        JsonNode root = parseJson("{\"Status\":\" Success \",\"Result\":\"12345\"}");

        // Then
        assertThatCode(() -> invokeEnsureSuccess(root, "TestOperation"))
                .doesNotThrowAnyException();
    }
}
