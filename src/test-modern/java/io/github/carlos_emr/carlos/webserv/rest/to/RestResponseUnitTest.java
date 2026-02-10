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

import io.github.carlos_emr.carlos.webserv.rest.to.GenericRestResponse.ResponseStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the RestResponse type-safe response wrapper classes.
 *
 * <p>Tests factory methods, status values, and error handling for
 * {@link RestResponse} and its supporting classes.</p>
 *
 * @see RestResponse
 * @see GenericRestResponse
 * @see RestResponseError
 * @since 2026-02-10
 */
@DisplayName("RestResponse Unit Tests")
@Tag("unit")
@Tag("fast")
class RestResponseUnitTest {

    /**
     * Tests for the ResponseStatus enum values.
     */
    @Nested
    @DisplayName("ResponseStatus")
    class ResponseStatusTests {

        @Test
        @DisplayName("should have SUCCESS and ERROR values")
        void shouldHaveSuccessAndErrorValues() {
            assertThat(ResponseStatus.values()).containsExactly(ResponseStatus.SUCCESS, ResponseStatus.ERROR);
        }
    }

    /**
     * Tests for RestResponse success factory method.
     */
    @Nested
    @DisplayName("successResponse")
    class SuccessResponseTests {

        @Test
        @DisplayName("should create response with SUCCESS status")
        void shouldCreateResponseWithSuccessStatus() {
            RestResponse<String> response = RestResponse.successResponse("test");

            assertThat(response.getStatus()).isEqualTo(ResponseStatus.SUCCESS);
        }

        @Test
        @DisplayName("should include body in response")
        void shouldIncludeBodyInResponse() {
            RestResponse<String> response = RestResponse.successResponse("hello");

            assertThat(response.getBody()).isEqualTo("hello");
        }

        @Test
        @DisplayName("should have null error in success response")
        void shouldHaveNullErrorInSuccessResponse() {
            RestResponse<String> response = RestResponse.successResponse("test");

            assertThat(response.getError()).isNull();
        }

        @Test
        @DisplayName("should include headers in response")
        void shouldIncludeHeadersInResponse() {
            RestResponse<String> response = RestResponse.successResponse("test");

            assertThat(response.getHeaders()).isNotNull();
        }

        @Test
        @DisplayName("should support list body type")
        void shouldSupportListBodyType() {
            List<String> items = List.of("a", "b", "c");
            RestResponse<List<String>> response = RestResponse.successResponse(items);

            assertThat(response.getBody()).hasSize(3);
            assertThat(response.getBody()).containsExactly("a", "b", "c");
        }

        @Test
        @DisplayName("should support null body")
        void shouldSupportNullBody() {
            RestResponse<String> response = RestResponse.successResponse(null);

            assertThat(response.getStatus()).isEqualTo(ResponseStatus.SUCCESS);
            assertThat(response.getBody()).isNull();
        }
    }

    /**
     * Tests for RestResponse error factory methods.
     */
    @Nested
    @DisplayName("errorResponse")
    class ErrorResponseTests {

        @Test
        @DisplayName("should create response with ERROR status")
        void shouldCreateResponseWithErrorStatus() {
            RestResponse<String> response = RestResponse.errorResponse("fail");

            assertThat(response.getStatus()).isEqualTo(ResponseStatus.ERROR);
        }

        @Test
        @DisplayName("should include error message")
        void shouldIncludeErrorMessage() {
            RestResponse<String> response = RestResponse.errorResponse("Something went wrong");

            assertThat(response.getError()).isNotNull();
            assertThat(response.getError().getMessage()).isEqualTo("Something went wrong");
        }

        @Test
        @DisplayName("should have null body in error response")
        void shouldHaveNullBodyInErrorResponse() {
            RestResponse<String> response = RestResponse.errorResponse("fail");

            assertThat(response.getBody()).isNull();
        }

        @Test
        @DisplayName("should include headers in error response")
        void shouldIncludeHeadersInErrorResponse() {
            RestResponse<String> response = RestResponse.errorResponse("fail");

            assertThat(response.getHeaders()).isNotNull();
        }

        @Test
        @DisplayName("should include additional data in error response")
        void shouldIncludeAdditionalDataInErrorResponse() {
            RestResponse<String> response = RestResponse.errorResponse("fail", "extra-context");

            assertThat(response.getError()).isNotNull();
            assertThat(response.getError().getMessage()).isEqualTo("fail");
            assertThat(response.getError().getData()).isEqualTo("extra-context");
        }
    }

    /**
     * Tests for RestResponseError construction and properties.
     */
    @Nested
    @DisplayName("RestResponseError")
    class RestResponseErrorTests {

        @Test
        @DisplayName("should create error with message only")
        void shouldCreateErrorWithMessageOnly() {
            RestResponseError error = new RestResponseError("test error");

            assertThat(error.getMessage()).isEqualTo("test error");
            assertThat(error.getData()).isNull();
        }

        @Test
        @DisplayName("should create error with message and data")
        void shouldCreateErrorWithMessageAndData() {
            RestResponseError error = new RestResponseError("test error", "context");

            assertThat(error.getMessage()).isEqualTo("test error");
            assertThat(error.getData()).isEqualTo("context");
        }

        @Test
        @DisplayName("should allow setting message via setter")
        void shouldAllowSettingMessageViaSetter() {
            RestResponseError error = new RestResponseError();
            error.setMessage("updated");

            assertThat(error.getMessage()).isEqualTo("updated");
        }
    }
}
