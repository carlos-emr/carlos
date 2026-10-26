/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.demographic.data;

import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Verifies patient labels use the current caller, clinical data and locale without shared caching.
 *
 * @since 2026-09-27
 */
@Tag("unit")
class DemographicNameAgeStringUnitTest {
    @Test
    void shouldCheckCurrentCaller_whenAnotherCallerPreviouslyLoadedPatient() {
        var first = mock(LoggedInInfo.class);
        var second = mock(LoggedInInfo.class);
        try (var readers = mockConstruction(DemographicData.class, (reader, context) -> {
            when(reader.getNameAgeSexArray(first, 42)).thenReturn(new String[]{"FAKE-Family", "FAKE-First", "F", "20"});
            when(reader.getNameAgeSexArray(second, 42)).thenThrow(new SecurityException("patient access denied"));
        })) {
            var helper = DemographicNameAgeString.getInstance();
            assertThat(helper.getNameAgeSexHashtable(first, "42")).containsEntry("lastName", "FAKE-Family");
            assertThatThrownBy(() -> helper.getNameAgeSexHashtable(second, "42")).isInstanceOf(SecurityException.class);
            assertThat(readers.constructed()).hasSize(2);
            verify(readers.constructed().get(1)).getNameAgeSexArray(second, 42);
        }
    }

    @Test
    void shouldResolveFreshLabel_whenPatientDataOrLocaleChanges() {
        var caller = mock(LoggedInInfo.class);
        try (var readers = mockConstruction(DemographicData.class, (reader, context) ->
                when(reader.getNameAgeSexArray(caller, 42)).thenReturn(new String[]{"FAKE-Family", "FAKE-First", "F", "age-" + context.getCount()}))) {
            var helper = DemographicNameAgeString.getInstance();
            assertThat(helper.getNameAgeSexHashtable(caller, "42")).containsEntry("age", "age-1");
            assertThat(helper.getNameAgeSexHashtable(caller, "42")).containsEntry("age", "age-2");
            assertThat(readers.constructed()).hasSize(2);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc", "0", "-1", "2147483648", "99999999999", "١", "../../etc"})
    void shouldReturnEmptyWithoutLookup_whenIdentifierIsInvalid(String value) {
        var caller = mock(LoggedInInfo.class);
        try (var readers = mockConstruction(DemographicData.class)) {
            assertThat(DemographicNameAgeString.getInstance().getNameAgeSexHashtable(caller, value)).isEmpty();
            assertThat(readers.constructed()).isEmpty();
        }
    }

    @Test
    void shouldReturnEmpty_whenPatientDoesNotExist() {
        var caller = mock(LoggedInInfo.class);
        try (var readers = mockConstruction(DemographicData.class)) {
            assertThat(DemographicNameAgeString.getInstance().getNameAgeSexHashtable(caller, "42")).isEmpty();
            verify(readers.constructed().getFirst()).getNameAgeSexArray(caller, 42);
        }
    }
}
