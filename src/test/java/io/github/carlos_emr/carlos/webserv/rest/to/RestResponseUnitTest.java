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
package io.github.carlos_emr.carlos.webserv.rest.to;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.webserv.rest.to.GenericRestResponse.ResponseStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

/**
 * Unit tests for {@link RestResponse}, {@link GenericRestResponse}, and {@link RestResponseHeaders}.
 *
 * <p>Covers all static factory methods on RestResponse, the status/body/error semantics from
 * GenericRestResponse, and the CarlosProperties-backed build metadata in RestResponseHeaders.</p>
 *
 * @since 2026-03-14
 * @see RestResponse
 * @see GenericRestResponse
 * @see RestResponseHeaders
 */
@DisplayName("RestResponse / GenericRestResponse / RestResponseHeaders Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("create")
@Tag("read")
class RestResponseUnitTest {

    private MockedStatic<CarlosProperties> oscarPropertiesMock;

    @BeforeEach
    void setUpCarlosPropertiesMock() {
        oscarPropertiesMock = mockStatic(CarlosProperties.class);
        oscarPropertiesMock.when(CarlosProperties::getBuildDate).thenReturn("2026-01-01");
        oscarPropertiesMock.when(CarlosProperties::getBuildTag).thenReturn("v1.0");
    }

    @AfterEach
    void tearDownCarlosPropertiesMock() {
        if (oscarPropertiesMock != null) {
            oscarPropertiesMock.close();
        }
    }

    // ─── RestResponse factory methods ──────────────────────────────────────────

    @Nested
    @DisplayName("successResponse factories")
    class SuccessResponseFactories {

        @Test
        @DisplayName("should set status to SUCCESS when body is provided")
        void shouldSetStatusSuccess_whenBodyProvided() {
            RestResponse<String> response = RestResponse.successResponse("hello");
            assertThat(response.getStatus()).isEqualTo(ResponseStatus.SUCCESS);
        }

        @Test
        @DisplayName("should carry the body when body is provided")
        void shouldCarryBody_whenBodyProvided() {
            RestResponse<String> response = RestResponse.successResponse("hello");
            assertThat(response.getBody()).isEqualTo("hello");
        }

        @Test
        @DisplayName("should set error to null on success response")
        void shouldSetErrorNull_onSuccessResponse() {
            RestResponse<String> response = RestResponse.successResponse("hello");
            assertThat(response.getError()).isNull();
        }

        @Test
        @DisplayName("should attach headers on success response")
        void shouldAttachHeaders_onSuccessResponse() {
            RestResponse<String> response = RestResponse.successResponse("body");
            assertThat(response.getHeaders()).isNotNull();
        }

        @Test
        @DisplayName("should set status to SUCCESS when custom headers and body are provided")
        void shouldSetStatusSuccess_whenCustomHeadersAndBodyProvided() {
            RestResponseHeaders headers = new RestResponseHeaders();
            RestResponse<Integer> response = RestResponse.successResponse(headers, 42);
            assertThat(response.getStatus()).isEqualTo(ResponseStatus.SUCCESS);
            assertThat(response.getBody()).isEqualTo(42);
            assertThat(response.getHeaders()).isSameAs(headers);
        }
    }

    @Nested
    @DisplayName("errorResponse factories")
    class ErrorResponseFactories {

        @Test
        @DisplayName("should set status to ERROR when error message is provided")
        void shouldSetStatusError_whenErrorMessageProvided() {
            RestResponse<String> response = RestResponse.errorResponse("Something went wrong");
            assertThat(response.getStatus()).isEqualTo(ResponseStatus.ERROR);
        }

        @Test
        @DisplayName("should set body to null on error response")
        void shouldSetBodyNull_onErrorResponse() {
            RestResponse<String> response = RestResponse.errorResponse("oops");
            assertThat(response.getBody()).isNull();
        }

        @Test
        @DisplayName("should populate error message on error response")
        void shouldPopulateErrorMessage_onErrorResponse() {
            RestResponse<String> response = RestResponse.errorResponse("Access Denied");
            assertThat(response.getError()).isNotNull();
            assertThat(response.getError().getMessage()).isEqualTo("Access Denied");
        }

        @Test
        @DisplayName("should attach error data when data argument is provided")
        void shouldAttachErrorData_whenDataProvided() {
            RestResponse<Void> response = RestResponse.errorResponse("Bad Request", "field:formName");
            assertThat(response.getError().getData()).isEqualTo("field:formName");
        }

        @Test
        @DisplayName("should use custom error object when RestResponseError is passed directly")
        void shouldUseCustomError_whenRestResponseErrorPassedDirectly() {
            RestResponseError error = new RestResponseError("custom error", "extra info");
            RestResponse<String> response = RestResponse.errorResponse(error);
            assertThat(response.getError()).isSameAs(error);
        }

        @Test
        @DisplayName("should use custom headers on error response when headers provided")
        void shouldUseCustomHeaders_whenErrorResponseHeadersProvided() {
            RestResponseHeaders headers = new RestResponseHeaders();
            RestResponseError error = new RestResponseError("failure");
            RestResponse<String> response = RestResponse.errorResponse(headers, error);
            assertThat(response.getHeaders()).isSameAs(headers);
            assertThat(response.getStatus()).isEqualTo(ResponseStatus.ERROR);
        }
    }

    // ─── GenericRestResponse ───────────────────────────────────────────────────

    @Nested
    @DisplayName("GenericRestResponse constructor validation")
    class GenericRestResponseConstructorValidation {

        @Test
        @DisplayName("should throw IllegalArgumentException when status is null")
        void shouldThrowIllegalArgumentException_whenStatusIsNull() {
            assertThatThrownBy(() -> new RestResponse<>(new RestResponseHeaders(), "body", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("status must not be null");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when SUCCESS response has non-null error")
        void shouldThrowIllegalArgumentException_whenSuccessResponseHasError() {
            assertThatThrownBy(() -> new RestResponse<>(new RestResponseHeaders(), "body",
                    new RestResponseError("oops"), ResponseStatus.SUCCESS))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("error must be null for SUCCESS");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when ERROR response has non-null body")
        void shouldThrowIllegalArgumentException_whenErrorResponseHasBody() {
            assertThatThrownBy(() -> new RestResponse<>(new RestResponseHeaders(), "body",
                    new RestResponseError("oops"), ResponseStatus.ERROR))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("body must be null for ERROR");
        }
    }

    @Nested
    @DisplayName("GenericRestResponse toString")
    class GenericRestResponseToString {

        @Test
        @DisplayName("should include status in toString output")
        void shouldIncludeStatus_inToStringOutput() {
            RestResponse<String> response = RestResponse.successResponse("data");
            assertThat(response.toString()).contains("SUCCESS");
        }

        @Test
        @DisplayName("should not include body content in toString to prevent PHI leakage")
        void shouldNotIncludeBodyContent_whenToStringCalledToPreventPhiLeakage() {
            RestResponse<String> response = RestResponse.successResponse("sensitive-patient-data");
            assertThat(response.toString()).doesNotContain("sensitive-patient-data");
        }

        @Test
        @DisplayName("should not include error message in toString to prevent leakage")
        void shouldNotIncludeErrorMessage_inToString() {
            RestResponse<String> response = RestResponse.errorResponse("InternalSecret");
            assertThat(response.toString()).doesNotContain("InternalSecret");
        }
    }

    // ─── RestResponseHeaders ───────────────────────────────────────────────────

    @Nested
    @DisplayName("RestResponseHeaders")
    class RestResponseHeadersTests {

        @Test
        @DisplayName("should read build date from CarlosProperties")
        void shouldReadBuildDate_fromCarlosProperties() {
            RestResponseHeaders headers = new RestResponseHeaders();
            assertThat(headers.getBuildDate()).isEqualTo("2026-01-01");
        }

        @Test
        @DisplayName("should read build tag from CarlosProperties")
        void shouldReadBuildTag_fromCarlosProperties() {
            RestResponseHeaders headers = new RestResponseHeaders();
            assertThat(headers.getBuildTag()).isEqualTo("v1.0");
        }

        @Test
        @DisplayName("should return null build date when CarlosProperties returns null")
        void shouldReturnNullBuildDate_whenCarlosPropertiesReturnsNull() {
            oscarPropertiesMock.when(CarlosProperties::getBuildDate).thenReturn(null);
            RestResponseHeaders headers = new RestResponseHeaders();
            assertThat(headers.getBuildDate()).isNull();
        }
    }
}
