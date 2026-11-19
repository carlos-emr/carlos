/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.commn.service;

import io.github.carlos_emr.carlos.commn.dao.InboxCategoryDao.DateSearchType;
import io.github.carlos_emr.carlos.commn.dao.SystemPreferencesDao;
import io.github.carlos_emr.carlos.commn.model.SystemPreferences;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Maps the {@code inboxDateSearchType} preference to the inbox date mode (issue #5772).
 * @since 2026-11-19
 */
@Tag("unit")
@Tag("lab")
class InboxDateSearchTypeResolverUnitTest {
    private final SystemPreferencesDao preferences = mock(SystemPreferencesDao.class);
    private final InboxDateSearchTypeResolver resolver = new InboxDateSearchTypeResolver(preferences);

    private void stored(SystemPreferences row) {
        when(preferences.findPreferenceByName(SystemPreferences.LAB_DISPLAY_PREFERENCE_KEYS.inboxDateSearchType))
                .thenReturn(row);
    }

    @Test
    void shouldSelectObservationDates_whenNoPreferenceRowIsStored() {
        stored(null);

        assertThat(resolver.resolve()).isEqualTo(DateSearchType.SERVICE_OBSERVATION);
        verify(preferences).findPreferenceByName(SystemPreferences.LAB_DISPLAY_PREFERENCE_KEYS.inboxDateSearchType);
    }

    @ParameterizedTest
    @CsvSource(value = {"receivedCreated,RECEIVED_CREATED", "serviceObservation,SERVICE_OBSERVATION",
            "NULL,SERVICE_OBSERVATION", "'',SERVICE_OBSERVATION", "unrecognised,SERVICE_OBSERVATION"},
            nullValues = "NULL")
    void shouldMapStoredValue_toTheInboxDateMode(String value, DateSearchType expected) {
        stored(new SystemPreferences(SystemPreferences.LAB_DISPLAY_PREFERENCE_KEYS.inboxDateSearchType.name(), value));

        assertThat(resolver.resolve()).isEqualTo(expected);
    }

    @Test
    void shouldPropagateReadFailure_insteadOfChoosingAMode() {
        when(preferences.findPreferenceByName(SystemPreferences.LAB_DISPLAY_PREFERENCE_KEYS.inboxDateSearchType))
                .thenThrow(new DataAccessResourceFailureException("synthetic"));

        assertThatThrownBy(resolver::resolve).isInstanceOf(DataAccessResourceFailureException.class);
    }
}
