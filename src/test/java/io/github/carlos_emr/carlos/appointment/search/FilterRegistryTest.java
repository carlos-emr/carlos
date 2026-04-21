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
package io.github.carlos_emr.carlos.appointment.search;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.appointment.search.filters.AvailableTimeSlotFilter;
import io.github.carlos_emr.carlos.appointment.search.filters.ExistingAppointmentFilter;
import io.github.carlos_emr.carlos.appointment.search.filters.FutureApptFilter;
import io.github.carlos_emr.carlos.appointment.search.filters.MultiUnitFilter;
import io.github.carlos_emr.carlos.appointment.search.filters.OpenAccessFilter;
import io.github.carlos_emr.carlos.appointment.search.filters.SufficientContiguousTimeFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link FilterRegistry}.
 *
 * <p>Verifies that the type-safe registry produces correct concrete filter instances
 * for every known short key, that the legacy fully-qualified class name compatibility
 * map resolves to the same concrete types, and that unknown keys are rejected with a
 * descriptive {@link IllegalArgumentException} rather than silently producing a null
 * instance or widening the accepted set.</p>
 *
 * @since 2026-04-20
 */
@Tag("unit")
@Tag("fast")
@Tag("appointment")
@DisplayName("FilterRegistry known-key + legacy FQCN resolution")
class FilterRegistryTest {

    @Test
    @DisplayName("should create SufficientContiguousTimeFilter for short key")
    void shouldCreateSufficientContiguousTimeFilter_forShortKey() {
        AvailableTimeSlotFilter filter = FilterRegistry.create("SufficientContiguousTime");
        assertThat(filter).isNotNull().isInstanceOf(SufficientContiguousTimeFilter.class);
    }

    @Test
    @DisplayName("should create ExistingAppointmentFilter for short key")
    void shouldCreateExistingAppointmentFilter_forShortKey() {
        AvailableTimeSlotFilter filter = FilterRegistry.create("ExistingAppointment");
        assertThat(filter).isNotNull().isInstanceOf(ExistingAppointmentFilter.class);
    }

    @Test
    @DisplayName("should create MultiUnitFilter for short key")
    void shouldCreateMultiUnitFilter_forShortKey() {
        AvailableTimeSlotFilter filter = FilterRegistry.create("MultiUnit");
        assertThat(filter).isNotNull().isInstanceOf(MultiUnitFilter.class);
    }

    @Test
    @DisplayName("should create OpenAccessFilter for short key")
    void shouldCreateOpenAccessFilter_forShortKey() {
        AvailableTimeSlotFilter filter = FilterRegistry.create("OpenAccess");
        assertThat(filter).isNotNull().isInstanceOf(OpenAccessFilter.class);
    }

    @Test
    @DisplayName("should create FutureApptFilter for short key")
    void shouldCreateFutureApptFilter_forShortKey() {
        AvailableTimeSlotFilter filter = FilterRegistry.create("FutureAppt");
        assertThat(filter).isNotNull().isInstanceOf(FutureApptFilter.class);
    }

    @Test
    @DisplayName("should create filter for each legacy fully-qualified class name")
    void shouldCreateFilter_forLegacyFqcn() {
        assertThat(FilterRegistry.create(SufficientContiguousTimeFilter.class.getName()))
                .isInstanceOf(SufficientContiguousTimeFilter.class);
        assertThat(FilterRegistry.create(ExistingAppointmentFilter.class.getName()))
                .isInstanceOf(ExistingAppointmentFilter.class);
        assertThat(FilterRegistry.create(MultiUnitFilter.class.getName()))
                .isInstanceOf(MultiUnitFilter.class);
        assertThat(FilterRegistry.create(OpenAccessFilter.class.getName()))
                .isInstanceOf(OpenAccessFilter.class);
        assertThat(FilterRegistry.create(FutureApptFilter.class.getName()))
                .isInstanceOf(FutureApptFilter.class);
    }

    @Test
    @DisplayName("should return true from isKnown for registry keys and legacy FQCNs")
    void shouldReturnTrue_fromIsKnownForRegistryKeysAndLegacyFqcns() {
        assertThat(FilterRegistry.isKnown("FutureAppt")).isTrue();
        assertThat(FilterRegistry.isKnown(FutureApptFilter.class.getName())).isTrue();
    }

    @Test
    @DisplayName("should return false from isKnown for unknown or null keys")
    void shouldReturnFalse_fromIsKnownForUnknownOrNullKeys() {
        assertThat(FilterRegistry.isKnown(null)).isFalse();
        assertThat(FilterRegistry.isKnown("")).isFalse();
        assertThat(FilterRegistry.isKnown("NotARealFilter")).isFalse();
        assertThat(FilterRegistry.isKnown("java.lang.Runtime")).isFalse();
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when create called with unknown key")
    void shouldThrowIllegalArgumentException_whenCreateCalledWithUnknownKey() {
        assertThatThrownBy(() -> FilterRegistry.create("NotARealFilter"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NotARealFilter");
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when create called with null")
    void shouldThrowIllegalArgumentException_whenCreateCalledWithNull() {
        assertThatThrownBy(() -> FilterRegistry.create(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should reject arbitrary class names that are not on the filter allow list")
    void shouldReject_arbitraryClassNames() {
        // Guards against regressions that would re-widen to unsafe reflection behaviour.
        assertThatThrownBy(() -> FilterRegistry.create("java.lang.Runtime"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FilterRegistry.create("io.github.carlos_emr.carlos.appointment.search.FilterRegistry"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should return new instance on each create call")
    void shouldReturnNewInstance_onEachCreateCall() {
        AvailableTimeSlotFilter a = FilterRegistry.create("MultiUnit");
        AvailableTimeSlotFilter b = FilterRegistry.create("MultiUnit");
        assertThat(a).isNotSameAs(b);
    }

    @Test
    @DisplayName("should expose all five known filter keys in REGISTRY")
    void shouldExposeAllFiveKnownFilterKeys_inRegistry() {
        assertThat(FilterRegistry.REGISTRY).containsOnlyKeys(
                "SufficientContiguousTime",
                "ExistingAppointment",
                "MultiUnit",
                "OpenAccess",
                "FutureAppt"
        );
    }
}
