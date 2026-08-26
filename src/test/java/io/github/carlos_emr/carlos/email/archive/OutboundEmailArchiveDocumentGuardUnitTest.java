/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */
package io.github.carlos_emr.carlos.email.archive;

import io.github.carlos_emr.carlos.commn.dao.OutboundEmailArchiveDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("OutboundEmailArchiveDocumentGuard")
@Tag("unit")
@Tag("email")
class OutboundEmailArchiveDocumentGuardUnitTest {

    private OutboundEmailArchiveDao archiveDao;

    @BeforeEach
    void setUp() {
        archiveDao = mock(OutboundEmailArchiveDao.class);
    }

    @Test
    @DisplayName("should recognize an archive document from its request-supplied id")
    void shouldRecognizeArchiveDocument_fromRequestSuppliedId() {
        when(archiveDao.existsByDocumentNo(321)).thenReturn(true);

        assertThat(OutboundEmailArchiveDocumentGuard.isArchiveDocument(archiveDao, "321")).isTrue();
    }

    @Test
    @DisplayName("should tolerate surrounding whitespace in a request-supplied id")
    void shouldTolerateSurroundingWhitespace_inRequestSuppliedId() {
        // Request parameters arrive as raw strings. Rejecting " 321 " would leave the guard open
        // on a value the downstream parse would happily accept.
        when(archiveDao.existsByDocumentNo(321)).thenReturn(true);

        assertThat(OutboundEmailArchiveDocumentGuard.isArchiveDocument(archiveDao, " 321 ")).isTrue();
    }

    @Test
    @DisplayName("should report false without querying when the id is not a number")
    void shouldReportFalse_withoutQuerying_whenIdIsNotANumber() {
        // A non-numeric id cannot name an archive, and the caller's own validation rejects it
        // separately. Answering false rather than throwing keeps a malformed-input error from
        // surfacing to the user as a security error.
        assertThat(OutboundEmailArchiveDocumentGuard.isArchiveDocument(archiveDao, "not-a-number")).isFalse();

        verify(archiveDao, never()).existsByDocumentNo(any());
    }

    @Test
    @DisplayName("should report false without querying for null and blank ids")
    void shouldReportFalse_withoutQuerying_forNullAndBlankIds() {
        assertThat(OutboundEmailArchiveDocumentGuard.isArchiveDocument(archiveDao, (String) null)).isFalse();
        assertThat(OutboundEmailArchiveDocumentGuard.isArchiveDocument(archiveDao, "   ")).isFalse();
        assertThat(OutboundEmailArchiveDocumentGuard.isArchiveDocument(archiveDao, (Integer) null)).isFalse();

        verifyNoInteractions(archiveDao);
    }

    @Test
    @DisplayName("should recognize an archive document from its document number")
    void shouldRecognizeArchiveDocument_fromDocumentNumber() {
        when(archiveDao.existsByDocumentNo(321)).thenReturn(true);

        assertThat(OutboundEmailArchiveDocumentGuard.isArchiveDocument(archiveDao, Integer.valueOf(321))).isTrue();
    }

    @Test
    @DisplayName("should pass through a non-archive document")
    void shouldPassThrough_forNonArchiveDocument() {
        when(archiveDao.existsByDocumentNo(999)).thenReturn(false);

        assertThat(OutboundEmailArchiveDocumentGuard.isArchiveDocument(archiveDao, "999")).isFalse();
    }

    @Test
    @DisplayName("should recognize an archive artifact by stored file name")
    void shouldRecognizeArchiveArtifact_byStoredFileName() {
        when(archiveDao.existsByFileName("20260707120000_outbound-email-44.eml")).thenReturn(true);

        assertThat(OutboundEmailArchiveDocumentGuard.isArchiveFileName(
                archiveDao, "20260707120000_outbound-email-44.eml")).isTrue();
    }

    @Test
    @DisplayName("should recognize an archive basename hidden behind discarded path components")
    void shouldRecognizeArchiveBasename_hiddenBehindDiscardedPathComponents() {
        when(archiveDao.existsByFileName("archive.eml")).thenReturn(true);

        assertThat(OutboundEmailArchiveDocumentGuard.isArchiveFileName(
                archiveDao, "discarded-directory/archive.eml")).isTrue();
    }

    @Test
    @DisplayName("should report false without querying for null and blank file names")
    void shouldReportFalse_withoutQuerying_forNullAndBlankFileNames() {
        assertThat(OutboundEmailArchiveDocumentGuard.isArchiveFileName(archiveDao, null)).isFalse();
        assertThat(OutboundEmailArchiveDocumentGuard.isArchiveFileName(archiveDao, "  ")).isFalse();

        verify(archiveDao, never()).existsByFileName(anyString());
    }

    @Test
    @DisplayName("should fail closed when no archive DAO is available")
    void shouldFailClosed_whenNoArchiveDaoIsAvailable() {
        assertThatThrownBy(() -> OutboundEmailArchiveDocumentGuard.isArchiveDocument(null, "321"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Outbound email archive DAO is required");
        assertThatThrownBy(() -> OutboundEmailArchiveDocumentGuard.isArchiveDocument(null, Integer.valueOf(321)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Outbound email archive DAO is required");
        assertThatThrownBy(() -> OutboundEmailArchiveDocumentGuard.isArchiveFileName(null, "archive.eml"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Outbound email archive DAO is required");
    }
}
