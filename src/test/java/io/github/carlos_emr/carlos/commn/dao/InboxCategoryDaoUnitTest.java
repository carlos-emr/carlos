/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.SystemPreferences;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pins a consistent date-search preference across one inbox SQL shape.
 * @since 2026-11-06
 */
@Tag("dao")
class InboxCategoryDaoUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @CsvSource({"serviceObservation,2026-1-5", "receivedCreated,2026-1-5",
            "serviceObservation,2026-01-05", "receivedCreated,2026-01-05"})
    void shouldNormalizeBothCalendarBounds_whenInboxDatesAreNotPadded(String mode, String date) throws Exception {
        var preferences = mock(SystemPreferencesDao.class);
        var preference = mock(SystemPreferences.class);
        when(preference.getValue()).thenReturn(mode);
        when(preferences.findPreferenceByName(SystemPreferences.LAB_DISPLAY_PREFERENCE_KEYS.inboxDateSearchType))
                .thenReturn(preference);
        var em = mock(EntityManager.class);
        var query = mock(Query.class);
        when(em.createNativeQuery(anyString(), eq(Tuple.class))).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        var dao = new InboxCategoryDao();
        ReflectionTestUtils.setField(dao, "entityManager", em);
        var filter = new InboxCategoryDao.Filter("", "", "", false, false, "", "", "", date, date, mode);
        assertThat(dao.count(filter, InboxCategoryDao.Operation.LAB_UNMATCHED, null).total()).isZero();
        verify(query).setParameter(1, "2026-01-05");
        verify(query).setParameter(2, "2026-01-06");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-2-30", "2026-13-1", "2026-0-1", "2026-1-0", "invalid"})
    void shouldRefuseImpossibleDates_beforeQuerying(String date) {
        var em = mock(EntityManager.class);
        var dao = new InboxCategoryDao();
        ReflectionTestUtils.setField(dao, "entityManager", em);
        var filter = new InboxCategoryDao.Filter("", "", "", false, false, "", "", "", date, date);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                dao.count(filter, InboxCategoryDao.Operation.LAB_UNMATCHED, null))
                .isInstanceOf(java.time.format.DateTimeParseException.class);
        verifyNoInteractions(em);
    }

    @Test
    void shouldKeepReceivedDateJoinAndPredicate_whenPreferenceChangesDuringCount() throws Exception {
        var preferences = mock(SystemPreferencesDao.class);
        var received = mock(SystemPreferences.class);
        var observation = mock(SystemPreferences.class);
        when(received.getValue()).thenReturn("receivedCreated");
        when(preferences.findPreferenceByName(SystemPreferences.LAB_DISPLAY_PREFERENCE_KEYS.inboxDateSearchType))
                .thenReturn(received, observation);
        var em = mock(EntityManager.class);
        var query = mock(Query.class);
        when(em.createNativeQuery(anyString(), eq(Tuple.class))).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        var dao = new InboxCategoryDao();
        ReflectionTestUtils.setField(dao, "entityManager", em);
        var filter = new InboxCategoryDao.Filter("", "", "", false, false, "", "", "", "2026-01-01", "", new io.github.carlos_emr.carlos.inboxhub.InboxDateSearchService(preferences).resolve());

        assertThat(dao.count(filter, InboxCategoryDao.Operation.LAB_UNMATCHED, null).total()).isZero();

        var sql = ArgumentCaptor.forClass(String.class);
        verify(em).createNativeQuery(sql.capture(), eq(Tuple.class));
        assertThat(sql.getValue()).contains("JOIN hl7TextMessage message", "message.created >= ?");
        verify(query).setParameter(1, "2026-01-01");
        verify(preferences, times(1)).findPreferenceByName(SystemPreferences.LAB_DISPLAY_PREFERENCE_KEYS.inboxDateSearchType);
    }
}
