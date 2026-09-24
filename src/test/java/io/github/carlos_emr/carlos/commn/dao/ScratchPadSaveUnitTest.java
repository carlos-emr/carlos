/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ScratchPad;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Tag("unit")
class ScratchPadSaveUnitTest {
    private ScratchPadDaoImpl dao;
    private Query query;

    @BeforeEach
    void setup() {
        dao = new ScratchPadDaoImpl();
        dao.entityManager = mock(EntityManager.class);
        query = mock(Query.class);
        when(dao.entityManager.find(Provider.class, "999998", LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(new Provider("999998"));
        when(dao.entityManager.createQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
    }

    private ScratchPad existing() {
        ScratchPad pad = new ScratchPad();
        pad.setId(7);
        pad.setProviderNo("999998");
        pad.setText("saved");
        when(query.getResultList()).thenReturn(List.of(pad));
        return pad;
    }

    @Test
    void shouldLockOwnerAndPreserveLiteralText_whenSavingFirstVersion() {
        String text = " A+B %20 &amp; <note>\n";
        ScratchPadDao.SaveResult result = dao.saveIfCurrent("999998", 0, text);
        assertThat(result.conflict()).isFalse();
        var order = inOrder(dao.entityManager, query);
        order.verify(dao.entityManager).find(Provider.class, "999998", LockModeType.PESSIMISTIC_WRITE);
        order.verify(dao.entityManager).createQuery(anyString());
        order.verify(query).setParameter("providerNo", "999998");
        order.verify(query).setLockMode(LockModeType.PESSIMISTIC_WRITE);
        order.verify(query).setMaxResults(1);
        order.verify(query).getResultList();
        ArgumentCaptor<ScratchPad> saved = ArgumentCaptor.forClass(ScratchPad.class);
        order.verify(dao.entityManager).persist(saved.capture());
        assertThat(saved.getValue().getText()).isEqualTo(text);
        assertThat(saved.getValue().getProviderNo()).isEqualTo("999998");
        assertThat(saved.getValue().isStatus()).isTrue();
        assertThat(result.version()).isSameAs(saved.getValue());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6, 8, Integer.MAX_VALUE})
    void shouldRejectInsert_whenRevisionIsStaleOrFuture(int expectedId) {
        ScratchPad previous = existing();
        ScratchPadDao.SaveResult result = dao.saveIfCurrent("999998", expectedId, "unsaved");
        assertThat(result.conflict()).isTrue();
        assertThat(result.version()).isSameAs(previous);
        verify(dao.entityManager, never()).persist(any());
    }

    @Test
    void shouldReuseVersion_whenTextIsUnchanged() {
        ScratchPad previous = existing();
        ScratchPadDao.SaveResult result = dao.saveIfCurrent("999998", 7, "saved");
        assertThat(result.conflict()).isFalse();
        assertThat(result.version()).isSameAs(previous);
        verify(dao.entityManager, never()).persist(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6, 7})
    void shouldAcknowledgeWithoutDuplicate_whenRetryTextIsAlreadyCurrent(int expectedId) {
        ScratchPad previous = existing();
        ScratchPadDao.SaveResult result = dao.saveIfCurrent("999998", expectedId, "saved");
        assertThat(result.conflict()).isFalse();
        assertThat(result.version()).isSameAs(previous);
        verify(dao.entityManager, never()).persist(any());
    }

    @Test
    void shouldRejectFutureRevision_whenTextIsAlreadyCurrent() {
        existing();
        assertThat(dao.saveIfCurrent("999998", 8, "saved").conflict()).isTrue();
        verify(dao.entityManager, never()).persist(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " saved ", "saved\n", "saved+"})
    void shouldPersistText_whenClearingOrChangingWhitespace(String text) {
        existing();
        ScratchPadDao.SaveResult result = dao.saveIfCurrent("999998", 7, text);
        assertThat(result.conflict()).isFalse();
        assertThat(result.version().getText()).isEqualTo(text);
        verify(dao.entityManager).persist(result.version());
    }

    @Test
    void shouldRejectStaleEditor_whenHistoryWasDeleted() {
        assertThat(dao.saveIfCurrent("999998", 7, "stale").conflict()).isTrue();
        verify(dao.entityManager, never()).persist(any());
    }

    @Test
    void shouldRejectInsert_whenProviderIsMissingOrInputInvalid() {
        when(dao.entityManager.find(Provider.class, "999998", LockModeType.PESSIMISTIC_WRITE)).thenReturn(null);
        assertThatThrownBy(() -> dao.saveIfCurrent("999998", 0, "note")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dao.saveIfCurrent("999998", -1, "note")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dao.saveIfCurrent("999998", 0, null)).isInstanceOf(IllegalArgumentException.class);
        verify(dao.entityManager, never()).persist(any());
    }
}
