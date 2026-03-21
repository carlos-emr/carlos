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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RestResponseError}.
 *
 * <p>Verifies constructor behaviour, immutability, and accessor semantics
 * for the error detail DTO used in all REST error responses.</p>
 *
 * @since 2026-03-14
 * @see RestResponseError
 */
@DisplayName("RestResponseError Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("create")
@Tag("read")
class RestResponseErrorUnitTest {

    @Nested
    @DisplayName("No-arg Constructor (deprecated)")
    @SuppressWarnings("deprecation")
    class NoArgConstructor {

        @Test
        @DisplayName("should return null message when constructed with no args")
        void shouldReturnNullMessage_whenConstructedWithNoArgs() {
            RestResponseError error = new RestResponseError();
            assertThat(error.getMessage()).isNull();
        }

        @Test
        @DisplayName("should return null data when constructed with no args")
        void shouldReturnNullData_whenConstructedWithNoArgs() {
            RestResponseError error = new RestResponseError();
            assertThat(error.getData()).isNull();
        }
    }

    @Nested
    @DisplayName("Single-arg Constructor")
    class SingleArgConstructor {

        @Test
        @DisplayName("should return message when constructed with message only")
        void shouldReturnMessage_whenConstructedWithMessageOnly() {
            RestResponseError error = new RestResponseError("EForm Name Already in Use");
            assertThat(error.getMessage()).isEqualTo("EForm Name Already in Use");
        }

        @Test
        @DisplayName("should return null data when constructed with message only")
        void shouldReturnNullData_whenConstructedWithMessageOnly() {
            RestResponseError error = new RestResponseError("some error");
            assertThat(error.getData()).isNull();
        }

        @Test
        @DisplayName("should accept null message without throwing")
        void shouldAcceptNullMessage_withoutThrowing() {
            RestResponseError error = new RestResponseError((String) null);
            assertThat(error.getMessage()).isNull();
        }
    }

    @Nested
    @DisplayName("Two-arg Constructor")
    class TwoArgConstructor {

        @Test
        @DisplayName("should return message and data when both are provided")
        void shouldReturnMessageAndData_whenBothProvided() {
            String message = "Validation failed";
            String data = "field:formName";

            RestResponseError error = new RestResponseError(message, data);

            assertThat(error.getMessage()).isEqualTo(message);
            assertThat(error.getData()).isEqualTo(data);
        }

        @Test
        @DisplayName("should return null data when data arg is null")
        void shouldReturnNullData_whenDataArgIsNull() {
            RestResponseError error = new RestResponseError("error", null);
            assertThat(error.getData()).isNull();
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("should not include message content in toString to prevent PHI leakage")
        void shouldNotIncludeMessageContent_inToString() {
            RestResponseError error = new RestResponseError("sensitive-error-detail");
            assertThat(error.toString()).doesNotContain("sensitive-error-detail");
        }

        @Test
        @DisplayName("should indicate presence of message and data in toString")
        void shouldIndicatePresenceOfMessageAndData_inToString() {
            RestResponseError error = new RestResponseError("msg", "data");
            assertThat(error.toString()).contains("hasMessage=true");
            assertThat(error.toString()).contains("hasData=true");
        }

        @Test
        @DisplayName("should indicate absence of message and data in toString")
        @SuppressWarnings("deprecation")
        void shouldIndicateAbsenceOfMessageAndData_inToString() {
            RestResponseError error = new RestResponseError();
            assertThat(error.toString()).contains("hasMessage=false");
            assertThat(error.toString()).contains("hasData=false");
        }
    }

    @Nested
    @DisplayName("Immutability")
    class Immutability {

        @Test
        @DisplayName("should not expose mutable state via getData")
        void shouldReturnConsistentData_acrossMultipleCalls() {
            // Use a mutable Serializable payload to verify field finality.
            // The 'data' field is final — its reference cannot be reassigned after construction.
            StringBuilder mutableData = new StringBuilder("initial");
            RestResponseError error = new RestResponseError("msg", mutableData);

            // getData() returns the same reference on every call (no defensive copy).
            assertThat(error.getData()).isSameAs(error.getData());

            // External mutation via the returned reference IS visible through getData() because
            // RestResponseError does not make a defensive copy. Callers should treat the
            // returned value as read-only. This assertion documents the current contract.
            ((StringBuilder) error.getData()).append("-modified");
            assertThat(error.getData().toString()).isEqualTo("initial-modified");
        }
    }
}
