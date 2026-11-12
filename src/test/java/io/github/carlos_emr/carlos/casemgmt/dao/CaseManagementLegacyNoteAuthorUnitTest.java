/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.dao;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.commn.model.Provider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("dao")
class CaseManagementLegacyNoteAuthorUnitTest {
    private final EntityManager entityManager = mock(EntityManager.class);
    private final CaseManagementNoteDAOImpl dao = new CaseManagementNoteDAOImpl() {
        @Override
        protected EntityManager entityManager() { return entityManager; }
    };

    @Test
    void shouldReturnRecordedAuthor_whenRevisionUuidMissing() {
        Provider author = new Provider();
        author.setProviderNo("LEGACY1");
        Query query = mock(Query.class);
        when(entityManager.createQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(author));
        for (String uuid : Arrays.asList(null, "", " ")) {
            CaseManagementNote note = note(uuid, "LEGACY1");
            assertThat(dao.getEditors(note)).containsExactly(author);
        }
        verify(entityManager, times(3)).createQuery(
                "select p from Provider p where p.providerNo = ?1");
        verify(query, times(3)).setParameter(1, "LEGACY1");
    }

    @Test
    void shouldReturnNoEditor_whenRevisionUuidAndAuthorMissing() {
        for (String provider : Arrays.asList(null, "", " ")) {
            assertThat(dao.getEditors(note(null, provider))).isEmpty();
        }
        verifyNoInteractions(entityManager);
    }

    @Test
    void shouldReturnNoHistory_whenRevisionUuidMissing() {
        for (String uuid : Arrays.asList(null, "", " ")) {
            assertThat(dao.getHistory(note(uuid, "LEGACY1"))).isEmpty();
        }
        verifyNoInteractions(entityManager);
    }

    @Test
    void shouldRetainEditorGroup_whenRevisionUuidPresent() {
        Query query = mock(Query.class);
        when(entityManager.createQuery(anyString())).thenReturn(query);
        Provider first = new Provider();
        first.setProviderNo("FIRST");
        Provider second = new Provider();
        second.setProviderNo("SECOND");
        when(query.getResultList()).thenReturn(List.of(first, second));
        assertThat(dao.getEditors(note("known-revision-uuid", "LEGACY1"))).containsExactly(first, second);
        verify(query).setParameter(1, "known-revision-uuid");
    }

    private CaseManagementNote note(String uuid, String provider) {
        CaseManagementNote note = new CaseManagementNote();
        note.setUuid(uuid);
        note.setProviderNo(provider);
        return note;
    }
}
