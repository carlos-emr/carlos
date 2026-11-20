/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.persistence.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class InboxDocumentCompletenessUnitTest extends CarlosUnitTestBase {
    private InboxResultsDaoImpl dao;
    private Query query;
    private DocumentDao documents;
    @BeforeEach void setup() {
        dao = new InboxResultsDaoImpl(); var em = mock(EntityManager.class); query = mock(Query.class);
        ReflectionTestUtils.setField(dao, "entityManager", em);
        when(em.createNativeQuery(anyString())).thenReturn(query);
        registerMock(SystemPreferencesDao.class, mock(SystemPreferencesDao.class));
        documents = mock(DocumentDao.class); registerMock(DocumentDao.class, documents);
        for (int id : new int[]{1, 3}) {
            Document d = new Document(id); d.setDoctype("Lab"); d.setObservationdate(new Date());
            when(documents.find(id)).thenReturn(d);
        }
    }
    private Object[] row(String id) { return new Object[]{1, id, "N", "Patient", "Synthetic", "", "F", 42, null, "DOC", "Report", "2026-01-01"}; }
    private InboxDocumentResults read() {
        return dao.readDocumentResults("7", "42", "", "", "", "N", true, 0, 20, false, null, null, null, null);
    }
    @Test void shouldKeepLaterValidRows_andReportMissingAndMalformedRows() {
        when(query.getResultList()).thenReturn(List.of(row("1"), row("2"), row("bad"), row("9999999999"), row("3")));
        var result = read();
        assertThat(result.items()).extracting(r -> r.segmentID).containsExactly("1", "3");
        assertThat(result.omittedRows()).isEqualTo(3);
        assertThatThrownBy(result::requireComplete).isInstanceOf(IllegalStateException.class);
    }
    @Test void shouldRefusePartialSuccess_whenCalledThroughTheLegacyAdapter() {
        when(query.getResultList()).thenReturn(List.of(row("1"), row("2"), row("3")));
        assertThatThrownBy(() -> dao.populateDocumentResultsData("7", "42", "", "", "", "N",
                true, 0, 20, false, null, null, null, null)).isInstanceOf(IllegalStateException.class);
    }
    @Test void shouldPropagateDatabaseFailure_withoutReturningAnEmptyPage() {
        when(query.getResultList()).thenThrow(new PersistenceException("synthetic query failure"));
        assertThatThrownBy(this::read).isInstanceOf(IllegalStateException.class).hasCauseInstanceOf(PersistenceException.class);
    }
    @Test void shouldDistinguishAValidEmptyPage_fromAnIncompletePage() {
        when(query.getResultList()).thenReturn(List.of());
        assertThat(read().requireComplete()).isEmpty(); assertThat(read().omittedRows()).isZero();
    }
}
