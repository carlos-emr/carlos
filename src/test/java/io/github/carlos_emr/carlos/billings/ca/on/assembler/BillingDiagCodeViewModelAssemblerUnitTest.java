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
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import io.github.carlos_emr.carlos.commn.dao.DiagnosticCodeDao;
import io.github.carlos_emr.carlos.commn.model.DiagnosticCode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Regression coverage for whole-term diagnostic searches (issue #4170). */
@Tag("unit")
class BillingDiagCodeViewModelAssemblerUnitTest {
    private final DiagnosticCodeDao dao = mock(DiagnosticCodeDao.class);
    private final BillingDiagCodeViewModelAssembler assembler = new BillingDiagCodeViewModelAssembler(dao);

    @ParameterizedTest
    @ValueSource(strings = {"diabetes type 2", "type 1 diabetes", "2 vessel disease", "Cushing's syndrome", "ABC123", "123..4", "12:34"})
    void shouldKeepDescriptionIntact_whenItContainsDigitsOrPunctuation(String term) {
        assembler.assembleSearch("2", term);
        verify(dao).searchText("%" + term + "%");
        verifyNoMoreInteractions(dao);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2", "250", "250.1", "V10", "E123.4", "ZR123", "250.", "V10."})
    void shouldSearchCodePrefix_whenTermIsACode(String term) {
        assembler.assembleSearch("", term);
        verify(dao).searchCode(term + "%");
        verifyNoMoreInteractions(dao);
    }

    @Test
    void shouldUseRange_whenDescriptionIsEmpty() {
        assembler.assembleSearch("2", "");
        verify(dao).searchCode("2%");
    }

    @Test
    void shouldSelectExactMatch_whenDescriptionReturnsOneCode() {
        DiagnosticCode code = new DiagnosticCode();
        code.setDiagnosticCode("250.1");
        code.setDescription("Diabetes type 1");
        when(dao.searchText("%Diabetes type 1%")).thenReturn(List.of(code));
        var model = assembler.assembleSearch("", "  Diabetes type 1  ");
        assertThat(model.getRows()).hasSize(1);
        assertThat(model.isAutoSelect()).isTrue();
        assertThat(model.getAutoSelectCode()).isEqualTo("250.1");
    }

    @Test
    void shouldAvoidQuery_whenTermIsBlank() {
        assertThat(assembler.assembleSearch("", " ").isNoMatch()).isTrue();
        verifyNoInteractions(dao);
    }
    @Test
    void shouldSearchWholeDescription_whenLongDigitSequenceHasInvalidSuffix() {
        String term = "1".repeat(10000) + "x";
        assembler.assembleSearch("", term);
        verify(dao).searchText("%" + term + "%");
        verifyNoMoreInteractions(dao);
    }

}
