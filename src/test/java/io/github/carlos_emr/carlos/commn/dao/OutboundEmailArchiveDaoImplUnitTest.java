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

package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.OutboundEmailArchive;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("OutboundEmailArchiveDaoImpl")
@Tag("unit")
@Tag("dao")
class OutboundEmailArchiveDaoImplUnitTest {

    private static final String FIND_FOR_READ_JPQL =
            "SELECT archive FROM OutboundEmailArchive archive "
                    + "LEFT JOIN FETCH archive.demographic "
                    + "LEFT JOIN FETCH archive.document "
                    + "WHERE archive.id = :archiveId";
    private static final String FIND_FOR_UPDATE_SQL =
            "SELECT * FROM outboundEmailArchive WHERE id = ?1 FOR UPDATE";
    private static final String EXISTS_ACTIVE_BY_DOCUMENT_NO_JPQL =
            "SELECT COUNT(archive) FROM OutboundEmailArchive archive "
                    + "WHERE archive.deleted = false AND ("
                    + "archive.document.documentNo = :documentNo "
                    + "OR EXISTS (SELECT attachment.id FROM OutboundEmailArchiveAttachment attachment "
                    + "WHERE attachment.archive = archive "
                    + "AND attachment.document.documentNo = :documentNo))";
    private static final String EXISTS_BY_DOCUMENT_NO_JPQL =
            "SELECT COUNT(archive) FROM OutboundEmailArchive archive "
                    + "WHERE archive.document.documentNo = :documentNo "
                    + "OR EXISTS (SELECT attachment.id FROM OutboundEmailArchiveAttachment attachment "
                    + "WHERE attachment.archive = archive "
                    + "AND attachment.document.documentNo = :documentNo)";
    private static final String FIND_EXISTING_DOCUMENT_NOS_JPQL =
            "SELECT DISTINCT document.documentNo FROM Document document "
                    + "WHERE document.documentNo IN :documentNos AND ("
                    + "EXISTS (SELECT archive.id FROM OutboundEmailArchive archive "
                    + "WHERE archive.document = document) "
                    + "OR EXISTS (SELECT attachment.id FROM OutboundEmailArchiveAttachment attachment "
                    + "WHERE attachment.document = document))";
    private static final String EXISTS_BY_FILE_NAME_JPQL =
            "SELECT COUNT(archive) FROM OutboundEmailArchive archive "
                    + "WHERE archive.fileName = :fileName "
                    + "OR EXISTS (SELECT attachment.id FROM OutboundEmailArchiveAttachment attachment "
                    + "WHERE attachment.archive = archive AND attachment.fileName = :fileName)";

    private OutboundEmailArchiveDaoImpl dao;
    private EntityManager entityManager;
    private Query query;
    private TypedQuery<OutboundEmailArchive> typedQuery;
    private TypedQuery<Long> countQuery;
    private TypedQuery<Integer> documentNoQuery;

    @BeforeEach
    void setUp() {
        dao = new OutboundEmailArchiveDaoImpl();
        entityManager = mock(EntityManager.class);
        query = mock(Query.class);
        typedQuery = mock(TypedQuery.class);
        countQuery = mock(TypedQuery.class);
        documentNoQuery = mock(TypedQuery.class);

        dao.entityManager = entityManager;
    }

    @Test
    void shouldFetchDocumentAndDemographic_whenFindingArchiveForRead() {
        OutboundEmailArchive archive = new OutboundEmailArchive();
        when(entityManager.createQuery(FIND_FOR_READ_JPQL, OutboundEmailArchive.class)).thenReturn(typedQuery);
        when(typedQuery.setParameter("archiveId", 888)).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(List.of(archive));

        OutboundEmailArchive result = dao.findForRead(888);

        assertThat(result).isSameAs(archive);
        verify(entityManager).createQuery(FIND_FOR_READ_JPQL, OutboundEmailArchive.class);
        verify(typedQuery).setParameter("archiveId", 888);
        verify(typedQuery).getResultList();
    }

    @Test
    void shouldReturnNull_whenReadArchiveDoesNotExist() {
        when(entityManager.createQuery(FIND_FOR_READ_JPQL, OutboundEmailArchive.class)).thenReturn(typedQuery);
        when(typedQuery.setParameter("archiveId", 888)).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(List.of());

        assertThat(dao.findForRead(888)).isNull();
    }

    @Test
    void shouldReturnNull_whenReadArchiveIdIsNull() {
        assertThat(dao.findForRead(null)).isNull();
        verify(entityManager, never()).createQuery(FIND_FOR_READ_JPQL, OutboundEmailArchive.class);
    }

    @Test
    void shouldUseMariaDbCompatibleForUpdateSql_whenFindingArchiveForUpdate() {
        OutboundEmailArchive archive = new OutboundEmailArchive();
        when(entityManager.createNativeQuery(FIND_FOR_UPDATE_SQL, OutboundEmailArchive.class)).thenReturn(query);
        when(query.setParameter(1, 888)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(archive));

        OutboundEmailArchive result = dao.findForUpdate(888);

        assertThat(result).isSameAs(archive);
        verify(entityManager).createNativeQuery(FIND_FOR_UPDATE_SQL, OutboundEmailArchive.class);
        verify(query).setParameter(1, 888);
        verify(query).getResultList();
    }

    @Test
    void shouldReturnNull_whenForUpdateArchiveDoesNotExist() {
        when(entityManager.createNativeQuery(FIND_FOR_UPDATE_SQL, OutboundEmailArchive.class)).thenReturn(query);
        when(query.setParameter(1, 888)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        assertThat(dao.findForUpdate(888)).isNull();
    }

    @Test
    void shouldReturnNull_whenForUpdateArchiveIdIsNull() {
        assertThat(dao.findForUpdate(null)).isNull();
        verify(entityManager, never()).createNativeQuery(FIND_FOR_UPDATE_SQL, OutboundEmailArchive.class);
    }

    @Test
    void shouldReturnTrue_whenActiveArchiveExistsForDocumentNo() {
        when(entityManager.createQuery(EXISTS_ACTIVE_BY_DOCUMENT_NO_JPQL, Long.class)).thenReturn(countQuery);
        when(countQuery.setParameter("documentNo", 321)).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(1L);

        assertThat(dao.existsActiveByDocumentNo(321)).isTrue();

        verify(entityManager).createQuery(EXISTS_ACTIVE_BY_DOCUMENT_NO_JPQL, Long.class);
        verify(countQuery).setParameter("documentNo", 321);
        verify(countQuery).getSingleResult();
    }

    @Test
    void shouldReturnFalse_whenNoActiveArchiveExistsForDocumentNo() {
        when(entityManager.createQuery(EXISTS_ACTIVE_BY_DOCUMENT_NO_JPQL, Long.class)).thenReturn(countQuery);
        when(countQuery.setParameter("documentNo", 321)).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(0L);

        assertThat(dao.existsActiveByDocumentNo(321)).isFalse();
    }

    @Test
    void shouldReturnFalse_whenDocumentNoIsNull() {
        assertThat(dao.existsActiveByDocumentNo(null)).isFalse();
        verify(entityManager, never()).createQuery(EXISTS_ACTIVE_BY_DOCUMENT_NO_JPQL, Long.class);
    }

    @Test
    void shouldReturnTrue_whenAnyArchiveExistsForDocumentNo() {
        when(entityManager.createQuery(EXISTS_BY_DOCUMENT_NO_JPQL, Long.class)).thenReturn(countQuery);
        when(countQuery.setParameter("documentNo", 321)).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(1L);

        assertThat(dao.existsByDocumentNo(321)).isTrue();

        verify(entityManager).createQuery(EXISTS_BY_DOCUMENT_NO_JPQL, Long.class);
        verify(countQuery).setParameter("documentNo", 321);
        verify(countQuery).getSingleResult();
    }

    @Test
    void shouldReturnFalse_whenNoArchiveExistsForDocumentNo() {
        when(entityManager.createQuery(EXISTS_BY_DOCUMENT_NO_JPQL, Long.class)).thenReturn(countQuery);
        when(countQuery.setParameter("documentNo", 321)).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(0L);

        assertThat(dao.existsByDocumentNo(321)).isFalse();
    }

    @Test
    void shouldReturnFalse_whenAnyArchiveDocumentNoIsNull() {
        assertThat(dao.existsByDocumentNo(null)).isFalse();
        verify(entityManager, never()).createQuery(EXISTS_BY_DOCUMENT_NO_JPQL, Long.class);
    }

    @Test
    void shouldFindArchiveDocumentNumbersInOneQuery() {
        when(entityManager.createQuery(FIND_EXISTING_DOCUMENT_NOS_JPQL, Integer.class))
                .thenReturn(documentNoQuery);
        when(documentNoQuery.setParameter("documentNos", List.of(321, 654)))
                .thenReturn(documentNoQuery);
        when(documentNoQuery.getResultList()).thenReturn(List.of(321));

        assertThat(dao.findExistingDocumentNos(List.of(321, 654, 321))).isEqualTo(Set.of(321));

        verify(documentNoQuery).setParameter("documentNos", List.of(321, 654));
    }

    @Test
    void shouldReturnTrue_whenArchiveExistsForFileName() {
        when(entityManager.createQuery(EXISTS_BY_FILE_NAME_JPQL, Long.class)).thenReturn(countQuery);
        when(countQuery.setParameter("fileName", "archive.eml")).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(1L);

        assertThat(dao.existsByFileName("archive.eml")).isTrue();

        verify(entityManager).createQuery(EXISTS_BY_FILE_NAME_JPQL, Long.class);
        verify(countQuery).setParameter("fileName", "archive.eml");
        verify(countQuery).getSingleResult();
    }

    @Test
    void shouldReturnFalse_whenNoArchiveExistsForFileName() {
        when(entityManager.createQuery(EXISTS_BY_FILE_NAME_JPQL, Long.class)).thenReturn(countQuery);
        when(countQuery.setParameter("fileName", "document.pdf")).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(0L);

        assertThat(dao.existsByFileName("document.pdf")).isFalse();
    }

    @Test
    void shouldReturnFalse_whenArchiveFileNameIsBlank() {
        assertThat(dao.existsByFileName(" ")).isFalse();
        verify(entityManager, never()).createQuery(EXISTS_BY_FILE_NAME_JPQL, Long.class);
    }

    @Test
    void shouldRejectPhysicalDeletionMethods() {
        OutboundEmailArchive archive = new OutboundEmailArchive();
        List<OutboundEmailArchive> archives = List.of(archive);

        assertThatThrownBy(() -> dao.remove(archive))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("controlled tombstone workflow");
        assertThatThrownBy(() -> dao.remove(888))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("controlled tombstone workflow");
        assertThatThrownBy(() -> dao.batchRemove(archives))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("controlled tombstone workflow");
        assertThatThrownBy(() -> dao.batchRemove(archives, 25))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("controlled tombstone workflow");
        assertThatThrownBy(() -> dao.batchRemoveAtomically(archives))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("controlled tombstone workflow");
        assertThatThrownBy(() -> dao.batchRemoveAtomically(archives, 25))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("controlled tombstone workflow");
        assertThatThrownBy(() -> dao.batchRemoveWithIndependentCommits(archives))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("controlled tombstone workflow");
        assertThatThrownBy(() -> dao.batchRemoveWithIndependentCommits(archives, 25))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("controlled tombstone workflow");
        verifyNoInteractions(entityManager);
    }
}
