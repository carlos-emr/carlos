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
package io.github.carlos_emr.carlos.prevention;

import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionExtDao;
import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link PreventionData#requirePreventionType(int, String)}, which keeps an RH edit or delete from
 * reaching another type's record (issue #4992): prevention/AddPrevention accepts the RH form's type
 * though no Preventions-page item lists it.
 *
 * @since 2026-10-22
 */
@DisplayName("PreventionData record type check")
@Tag("unit")
@Tag("prevention")
class PreventionDataTypeCheckUnitTest extends CarlosUnitTestBase {

    private PreventionDao preventionDao;
    private Object originalPreventionDao;

    @BeforeEach
    void replaceDao() {
        preventionDao = mock(PreventionDao.class);
        registerMock(PreventionDao.class, preventionDao);
        // PreventionData resolves its DAOs in its static initializer, which may run in this test.
        registerMock(PreventionExtDao.class, mock(PreventionExtDao.class));
        registerMock(PartialDateDao.class, mock(PartialDateDao.class));
        originalPreventionDao = ReflectionTestUtils.getField(PreventionData.class, "preventionDao");
        ReflectionTestUtils.setField(PreventionData.class, "preventionDao", preventionDao);
    }

    @AfterEach
    void restoreDao() {
        ReflectionTestUtils.setField(PreventionData.class, "preventionDao", originalPreventionDao);
    }

    private static Prevention recordOfType(String type) {
        Prevention prevention = new Prevention();
        prevention.setPreventionType(type);
        return prevention;
    }

    @Test
    @DisplayName("should accept a record of the posted type")
    void shouldAccept_whenRecordIsOfPostedType() {
        when(preventionDao.find(100)).thenReturn(recordOfType("RH"));

        assertThatCode(() -> PreventionData.requirePreventionType(100, "RH")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should refuse a record of another type")
    void shouldRefuse_whenRecordIsAnotherType() {
        when(preventionDao.find(100)).thenReturn(recordOfType("Flu"));

        assertThatThrownBy(() -> PreventionData.requirePreventionType(100, "RH"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should refuse a record that does not exist")
    void shouldRefuse_whenRecordIsMissing() {
        when(preventionDao.find(100)).thenReturn(null);

        assertThatThrownBy(() -> PreventionData.requirePreventionType(100, "RH"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
