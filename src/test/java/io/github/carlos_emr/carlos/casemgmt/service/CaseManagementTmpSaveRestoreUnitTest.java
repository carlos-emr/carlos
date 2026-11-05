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
package io.github.carlos_emr.carlos.casemgmt.service;

import io.github.carlos_emr.carlos.commn.dao.CaseManagementTmpSaveDao;
import io.github.carlos_emr.carlos.commn.model.CaseManagementTmpSave;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Draft restoration must tolerate missing chart program context.
 * @since 2026-10-24
 */
@Tag("unit")
@Tag("casemgmt")
@DisplayName("Dated temporary chart note restoration")
class CaseManagementTmpSaveRestoreUnitTest extends CarlosUnitTestBase {
    private CaseManagementManagerImpl manager;
    private CaseManagementTmpSaveDao dao;
    private final Date cutoff = new Date(123456789L);

    @BeforeEach
    void setUp() {
        dao = mock(CaseManagementTmpSaveDao.class);
        manager = new CaseManagementManagerImpl();
        manager.setCaseManagementTmpSaveDao(dao);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "invalid")
    void shouldReturnNoDraft_whenProgramIsMissingOrInvalid(String program) {
        assertThat(manager.restoreTmpSave("999998", "1001", program, cutoff)).isNull();
        verifyNoInteractions(dao);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "invalid")
    void shouldReturnNoDraft_whenPatientIsMissingOrInvalid(String patient) {
        assertThat(manager.restoreTmpSave("999998", patient, "10016", cutoff)).isNull();
        verifyNoInteractions(dao);
    }

    @Test
    void shouldRestorePatientDraft_whenContextIsValid() {
        CaseManagementTmpSave draft = new CaseManagementTmpSave();
        when(dao.find("999998", 1001, 10016, cutoff)).thenReturn(draft);
        assertThat(manager.restoreTmpSave("999998", "1001", "10016", cutoff)).isSameAs(draft);
        verify(dao).find("999998", 1001, 10016, cutoff);
    }
}
