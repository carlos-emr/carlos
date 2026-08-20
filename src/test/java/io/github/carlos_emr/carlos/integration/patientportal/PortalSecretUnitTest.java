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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The wrapper that makes credential protection structural rather than remembered.
 *
 * <p>Three values in this package are credentials. Held as plain strings, each relied on someone
 * remembering to redact it; these tests pin the properties that replace remembering.
 */
@Tag("unit")
@Tag("patient-portal")
@DisplayName("PortalSecret")
class PortalSecretUnitTest {

    private static final String VALUE = "one-time-activation-token-abc123";

    @Test
    @DisplayName("should never render the value")
    void shouldRedactValue_inToStringOutput() {
        PortalSecret secret = PortalSecret.of(VALUE);

        assertThat(secret.toString()).doesNotContain(VALUE);
        assertThat(secret.toString()).contains("REDACTED");
    }

    @Test
    @DisplayName("should keep the value out of an interpolated string")
    void shouldRedactValue_whenInterpolatedIntoAMessage() {
        String rendered = String.format("token=%s", PortalSecret.of(VALUE));

        assertThat(rendered).doesNotContain(VALUE);
    }

    /**
     * The path that a redacting toString does not cover: Jackson reads fields and getters, not
     * toString. A serializer failure is the correct outcome — far better than a credential
     * appearing in a REST response the day a DTO reaches one.
     */
    @Test
    @DisplayName("should refuse to serialize rather than publish the credential")
    void shouldFailSerialization_ratherThanEmitTheValue() {
        assertThatThrownBy(() -> new ObjectMapper().writeValueAsString(PortalSecret.of(VALUE)))
                .isInstanceOf(Exception.class)
                .hasMessageNotContaining(VALUE);
    }

    @Test
    @DisplayName("should return the value only through the audited accessor")
    void shouldReturnValue_whenExplicitlyExposed() {
        assertThat(PortalSecret.of(VALUE).expose()).isEqualTo(VALUE);
    }

    @Test
    @DisplayName("should reject a blank credential")
    void shouldThrow_whenValueIsBlank() {
        assertThatThrownBy(() -> PortalSecret.of("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PortalSecret.of(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should treat an absent optional credential as null rather than blank")
    void shouldReturnNull_whenOptionalValueIsAbsent() {
        assertThat(PortalSecret.ofNullable(null)).isNull();
        assertThat(PortalSecret.ofNullable("   ")).isNull();
        assertThat(PortalSecret.ofNullable(VALUE)).isNotNull();
    }

    @Test
    @DisplayName("should compare by value")
    void shouldCompareByValue_whenTwoSecretsHoldTheSameCredential() {
        assertThat(PortalSecret.of(VALUE)).isEqualTo(PortalSecret.of(VALUE));
        assertThat(PortalSecret.of(VALUE)).isNotEqualTo(PortalSecret.of("different"));
    }

    /**
     * A wrapper must not consider itself equal to the raw credential it holds, or wrapping would
     * be cosmetic.
     *
     * <p>Written as a boolean assertion rather than {@code isNotEqualTo(VALUE)}: comparing a
     * PortalSecret to a String is a dissimilar-type comparison that passes no matter what equals
     * does, which is an assertion that cannot fail. SonarCloud flagged the original as a bug and
     * was right to.
     */
    @Test
    @DisplayName("should not equal the bare string it wraps")
    void shouldNotEqualRawValue_whenComparedToTheUnwrappedCredential() {
        assertThat(PortalSecret.of(VALUE).equals(VALUE)).isFalse();
        assertThat(PortalSecret.of(VALUE).equals(null)).isFalse();
    }
}
