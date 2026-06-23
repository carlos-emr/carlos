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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.carlos.commn.dao.DiagnosticCodeDao;
import io.github.carlos_emr.carlos.commn.model.DiagnosticCode;
import io.github.carlos_emr.carlos.test.logging.LogCapture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit coverage for {@code DiagCodeDescriptionPersister} description save/update behavior. */
@DisplayName("DiagCodeDescriptionPersister")
@Tag("unit")
@Tag("billing")
class DiagCodeDescriptionPersisterUnitTest {

    @Test
    void shouldUpdateDescription_forEveryMatchingCode() {
        DiagnosticCodeDao dao = mock(DiagnosticCodeDao.class);
        DiagCodeDescriptionPersister persister = new DiagCodeDescriptionPersister(dao);
        DiagnosticCode code = new DiagnosticCode();
        when(dao.findByDiagnosticCode("001")).thenReturn(List.of(code));

        boolean updated = persister.updateDescription("update001", "Acute infection");

        assertThat(updated).isTrue();
        assertThat(code.getDescription()).isEqualTo("Acute infection");
        verify(dao).merge(code);
    }

    @Test
    void shouldThrowTypedException_whenSubmitValueCannotCarryCode() {
        DiagnosticCodeDao dao = mock(DiagnosticCodeDao.class);
        DiagCodeDescriptionPersister persister = new DiagCodeDescriptionPersister(dao);

        assertThatThrownBy(() -> persister.updateDescription("x", "ignored"))
                .isInstanceOf(DiagDescriptionUpdateException.class)
                .hasMessageContaining("missing diagnostic code");

        verify(dao, never()).merge(any(DiagnosticCode.class));
    }

    @Test
    void shouldThrowTypedException_whenDiagnosticCodeDoesNotExist() {
        DiagnosticCodeDao dao = mock(DiagnosticCodeDao.class);
        DiagCodeDescriptionPersister persister = new DiagCodeDescriptionPersister(dao);
        when(dao.findByDiagnosticCode("999")).thenReturn(List.of());

        assertThatThrownBy(() -> persister.updateDescription("update999", "ignored"))
                .isInstanceOf(DiagDescriptionUpdateException.class)
                .hasMessageContaining("999")
                .hasMessageContaining("not found");

        verify(dao, never()).merge(any(DiagnosticCode.class));
    }

    @Test
    void shouldThrowTypedException_whenDaoLookupFails() {
        DiagnosticCodeDao dao = mock(DiagnosticCodeDao.class);
        DiagCodeDescriptionPersister persister = new DiagCodeDescriptionPersister(dao);
        when(dao.findByDiagnosticCode("001")).thenThrow(new RuntimeException("lock timeout"));

        assertThatThrownBy(() -> persister.updateDescription("update001", "Acute infection"))
                .isInstanceOf(DiagDescriptionUpdateException.class)
                .hasMessageContaining("001")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("should omit diagnostic code before logging")
    void shouldOmitDiagnosticCode_whenDaoLookupFails() {
        DiagnosticCodeDao dao = mock(DiagnosticCodeDao.class);
        DiagCodeDescriptionPersister persister = new DiagCodeDescriptionPersister(dao);
        when(dao.findByDiagnosticCode("1\r\n")).thenThrow(new RuntimeException("lock\r\nforged-exception"));

        try (LogCapture capture = LogCapture.forLogger(DiagCodeDescriptionPersister.class)) {
            assertThatThrownBy(() -> persister.updateDescription("update1\r\n", "Acute infection"))
                    .isInstanceOf(DiagDescriptionUpdateException.class);

            assertThat(capture.messages()).hasSize(1);
            String logged = capture.messages().get(0);
            assertThat(logged).doesNotContain("\r").doesNotContain("\n");
            assertThat(logged).contains(RuntimeException.class.getName());
            assertThat(logged).doesNotContain("1\r\n", "1\\r\\n", "lock\r\nforged-exception",
                    "lock\\r\\nforged-exception", "forged-exception");
        }
    }

    @Test
    void shouldThrowTypedException_whenMergeFails() {
        DiagnosticCodeDao dao = mock(DiagnosticCodeDao.class);
        DiagCodeDescriptionPersister persister = new DiagCodeDescriptionPersister(dao);
        DiagnosticCode code = new DiagnosticCode();
        when(dao.findByDiagnosticCode("001")).thenReturn(List.of(code));
        doThrow(new RuntimeException("deadlock")).when(dao).merge(code);

        assertThatThrownBy(() -> persister.updateDescription("update001", "Acute infection"))
                .isInstanceOf(DiagDescriptionUpdateException.class)
                .hasMessageContaining("001")
                .hasCauseInstanceOf(RuntimeException.class);
    }
}
