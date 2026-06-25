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
package io.github.carlos_emr.carlos.form;

import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.sql.ResultSet;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@DisplayName("FrmConsultantRecord Tests")
@Tag("unit")
@Tag("form")
class FrmConsultantRecordUnitTest extends CarlosUnitTestBase {

    private static final int TEST_DEMOGRAPHIC_NO = 123;

    @Nested
    @DisplayName("getInitRefDoc")
    class GetInitRefDoc {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {
                "<rdohip>123456",
                "<family_doc>Dr Smith</family_doc>",
                "</rdohip><rdohip>123456</rdohip>"
        })
        @DisplayName("should leave refdocno unset when family doctor is invalid")
        void shouldLeaveRefdocnoUnset_whenFamilyDoctorIsInvalid(String familyDoctor) throws Exception {
            Properties props = invokeGetInitRefDocWithFamilyDoctor(familyDoctor);

            assertThat(props).doesNotContainKey("refdocno");
        }

        @Test
        @DisplayName("should set refdocno when rdohip tag is present")
        void shouldSetRefdocno_whenRdohipTagIsPresent() throws Exception {
            Properties props = invokeGetInitRefDocWithFamilyDoctor("<rdohip>123456</rdohip>");

            assertThat(props).containsEntry("refdocno", "123456");
        }

        private Properties invokeGetInitRefDocWithFamilyDoctor(String familyDoctor) throws Exception {
            registerMock(DemographicManager.class, mock(DemographicManager.class));
            registerMock(DemographicExtDao.class, mock(DemographicExtDao.class));
            registerMock(ProfessionalSpecialistDao.class, mock(ProfessionalSpecialistDao.class));
            registerMock(ClinicDAO.class, mock(ClinicDAO.class));

            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.next()).thenReturn(true);
            when(resultSet.getString("family_doctor")).thenReturn(familyDoctor);

            try (MockedStatic<LegacyJdbcQuery> legacyJdbcQuery = mockStatic(LegacyJdbcQuery.class)) {
                legacyJdbcQuery.when(() -> LegacyJdbcQuery.getPreparedResultSet(contains("family_doctor"), eq(TEST_DEMOGRAPHIC_NO)))
                        .thenReturn(resultSet);

                return new FrmConsultantRecord().getInitRefDoc(new Properties(), TEST_DEMOGRAPHIC_NO);
            }
        }
    }
}
