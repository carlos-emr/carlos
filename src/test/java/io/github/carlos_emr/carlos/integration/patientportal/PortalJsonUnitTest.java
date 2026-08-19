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
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The readers that decide whether a portal contract change is loud or silent.
 *
 * <p>Jackson's convenience accessors coerce a missing field to {@code 0} or {@code false}, which is
 * how an absent {@code force_password_reset} became "the patient can sign in". These tests pin
 * which readers tolerate absence and which refuse it.
 */
@Tag("unit")
@Tag("patient-portal")
@DisplayName("PortalJson")
class PortalJsonUnitTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonNode node(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Nested
    @DisplayName("required readers")
    class RequiredReaders {

        @Test
        @DisplayName("should read an identifier the portal supplied")
        void shouldReadIdentifier_whenPresent() {
            assertThat(PortalJson.requiredLong(node("{\"id\":42}"), "id")).isEqualTo(42L);
            assertThat(PortalJson.requiredInt(node("{\"n\":7}"), "n")).isEqualTo(7);
        }

        @Test
        @DisplayName("should refuse an absent identifier rather than reporting zero")
        void shouldThrow_whenIdentifierIsAbsent() {
            assertThatThrownBy(() -> PortalJson.requiredLong(node("{}"), "id"))
                    .isInstanceOf(PortalContractException.class)
                    .hasMessageContaining("id");
        }

        @Test
        @DisplayName("should refuse a null identifier")
        void shouldThrow_whenIdentifierIsJsonNull() {
            assertThatThrownBy(() -> PortalJson.requiredLong(node("{\"id\":null}"), "id"))
                    .isInstanceOf(PortalContractException.class);
        }

        @Test
        @DisplayName("should refuse an identifier that is not a number")
        void shouldThrow_whenIdentifierIsNotNumeric() {
            assertThatThrownBy(() -> PortalJson.requiredLong(node("{\"id\":\"abc\"}"), "id"))
                    .isInstanceOf(PortalContractException.class);
        }

        @Test
        @DisplayName("should read a flag the portal supplied")
        void shouldReadFlag_whenPresent() {
            assertThat(PortalJson.requiredBool(node("{\"f\":true}"), "f")).isTrue();
            assertThat(PortalJson.requiredBool(node("{\"f\":false}"), "f")).isFalse();
        }

        /**
         * Every boolean in this package defaults to the reassuring answer when absent —
         * {@code force_password_reset=false} reads as "the patient can sign in" and
         * {@code created=false} as "no second passphrase was minted". Absence must be loud.
         */
        @Test
        @DisplayName("should refuse an absent flag rather than defaulting it to false")
        void shouldThrow_whenFlagIsAbsent() {
            assertThatThrownBy(() -> PortalJson.requiredBool(node("{}"), "force_password_reset"))
                    .isInstanceOf(PortalContractException.class)
                    .hasMessageContaining("force_password_reset");
        }

        @Test
        @DisplayName("should refuse a flag that arrived as a string")
        void shouldThrow_whenFlagIsNotBoolean() {
            assertThatThrownBy(() -> PortalJson.requiredBool(node("{\"f\":\"true\"}"), "f"))
                    .isInstanceOf(PortalContractException.class);
        }
    }

    @Nested
    @DisplayName("optional readers")
    class OptionalReaders {

        @Test
        @DisplayName("should return null for an absent or null field")
        void shouldReturnNull_whenFieldIsAbsentOrNull() {
            assertThat(PortalJson.text(node("{}"), "x")).isNull();
            assertThat(PortalJson.text(node("{\"x\":null}"), "x")).isNull();
            assertThat(PortalJson.optionalLong(node("{}"), "x")).isNull();
            assertThat(PortalJson.optionalInt(node("{\"x\":null}"), "x")).isNull();
        }

        @Test
        @DisplayName("should read a present optional value")
        void shouldReadValue_whenPresent() {
            assertThat(PortalJson.text(node("{\"x\":\"v\"}"), "x")).isEqualTo("v");
            assertThat(PortalJson.optionalInt(node("{\"x\":5}"), "x")).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("timestamps")
    class Timestamps {

        @Test
        @DisplayName("should accept both offset forms the portal may emit")
        void shouldParseTimestamp_inEitherOffsetForm() {
            Instant expected = Instant.parse("2026-08-19T12:00:00Z");

            assertThat(PortalJson.timestamp(node("{\"t\":\"2026-08-19T12:00:00Z\"}"), "t"))
                    .isEqualTo(expected);
            assertThat(PortalJson.timestamp(node("{\"t\":\"2026-08-19T12:00:00+00:00\"}"), "t"))
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should apply a non-zero offset rather than dropping it")
        void shouldApplyOffset_whenNotUtc() {
            assertThat(PortalJson.timestamp(node("{\"t\":\"2026-08-19T12:00:00-04:00\"}"), "t"))
                    .isEqualTo(Instant.parse("2026-08-19T16:00:00Z"));
        }

        @Test
        @DisplayName("should return null for an absent timestamp")
        void shouldReturnNull_whenTimestampIsAbsent() {
            assertThat(PortalJson.timestamp(node("{}"), "t")).isNull();
        }

        /**
         * A raw DateTimeParseException used to escape past every caller that catches
         * PatientPortalException, surfacing as a generic CARLOS error page.
         */
        @Test
        @DisplayName("should refuse a timestamp with no offset instead of escaping unmapped")
        void shouldThrowContractException_whenTimestampHasNoOffset() {
            assertThatThrownBy(
                            () -> PortalJson.timestamp(node("{\"t\":\"2026-08-19T12:00:00\"}"), "t"))
                    .isInstanceOf(PortalContractException.class);
        }

        @Test
        @DisplayName("should keep the offending value out of the failure message")
        void shouldOmitFieldValue_fromContractFailureMessage() {
            assertThatThrownBy(
                            () ->
                                    PortalJson.timestamp(
                                            node("{\"requested_at\":\"patient@example.com\"}"),
                                            "requested_at"))
                    .isInstanceOf(PortalContractException.class)
                    .hasMessageNotContaining("patient@example.com")
                    .hasMessageContaining("requested_at");
        }
    }
}
