/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.inboxhub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.SystemPreferencesDao;
import io.github.carlos_emr.carlos.commn.model.SystemPreferences;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

/**
 * Pins {@link InboxDateSearchService#resolve()} added in #6135 (#6336): the stored
 * {@code inboxDateSearchType} value is returned, a missing or empty value keeps the legacy
 * {@code serviceObservation} default, and DAO failures propagate.
 *
 * <p>The service is currently not referenced from {@code src/main} (inbox categories use
 * {@code commn.service.InboxDateSearchTypeResolver}); this test pins its contract while it exists.
 */
@Tag("unit")
@Tag("lab")
class InboxDateSearchServiceUnitTest {
    private static final SystemPreferences.LAB_DISPLAY_PREFERENCE_KEYS KEY =
            SystemPreferences.LAB_DISPLAY_PREFERENCE_KEYS.inboxDateSearchType;

    private final SystemPreferencesDao preferences = mock(SystemPreferencesDao.class);
    private final InboxDateSearchService service = new InboxDateSearchService(preferences);

    @Test
    void shouldReturnStoredValue_whenPreferenceIsSet() {
        when(preferences.findPreferenceByName(KEY)).thenReturn(new SystemPreferences(KEY.name(), "receivedCreated"));

        assertThat(service.resolve()).isEqualTo("receivedCreated");
        verify(preferences).findPreferenceByName(KEY);
    }

    @Test
    void shouldDefaultToServiceObservation_whenNoPreferenceRowExists() {
        when(preferences.findPreferenceByName(KEY)).thenReturn(null);

        assertThat(service.resolve()).isEqualTo("serviceObservation");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldDefaultToServiceObservation_whenStoredValueIsNullOrEmpty(String value) {
        when(preferences.findPreferenceByName(KEY)).thenReturn(new SystemPreferences(KEY.name(), value));

        assertThat(service.resolve()).isEqualTo("serviceObservation");
    }

    @Test
    void shouldPropagateReadFailure_whenTheDaoThrows() {
        IllegalStateException failure = new IllegalStateException("synthetic read failure");
        when(preferences.findPreferenceByName(KEY)).thenThrow(failure);

        assertThatThrownBy(service::resolve).isSameAs(failure);
    }
}
