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
import io.github.carlos_emr.carlos.db.DBHandler;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.sql.ResultSet;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
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

        @Test
        @DisplayName("should leave refdocno unset when family doctor is null")
        void shouldLeaveRefdocnoUnset_whenFamilyDoctorIsNull() throws Exception {
            Properties props = invokeGetInitRefDoc_withFamilyDoctor(null);

            assertThat(props).doesNotContainKey("refdocno");
        }

        @Test
        @DisplayName("should leave refdocno unset when family doctor is empty")
        void shouldLeaveRefdocnoUnset_whenFamilyDoctorIsEmpty() throws Exception {
            Properties props = invokeGetInitRefDoc_withFamilyDoctor("");

            assertThat(props).doesNotContainKey("refdocno");
        }

        @Test
        @DisplayName("should leave refdocno unset when rdohip closing tag is missing")
        void shouldLeaveRefdocnoUnset_whenRdohipClosingTagMissing() throws Exception {
            Properties props = invokeGetInitRefDoc_withFamilyDoctor("<rdohip>123456");

            assertThat(props).doesNotContainKey("refdocno");
        }

        @Test
        @DisplayName("should leave refdocno unset when family doctor XML is unrelated")
        void shouldLeaveRefdocnoUnset_whenFamilyDoctorXmlIsUnrelated() throws Exception {
            Properties props = invokeGetInitRefDoc_withFamilyDoctor("<family_doc>Dr Smith</family_doc>");

            assertThat(props).doesNotContainKey("refdocno");
        }

        @Test
        @DisplayName("should set refdocno when rdohip tag is present")
        void shouldSetRefdocno_whenRdohipTagIsPresent() throws Exception {
            Properties props = invokeGetInitRefDoc_withFamilyDoctor("<rdohip>123456</rdohip>");

            assertThat(props).containsEntry("refdocno", "123456");
        }

        private Properties invokeGetInitRefDoc_withFamilyDoctor(String familyDoctor) throws Exception {
            registerMock(ProfessionalSpecialistDao.class, mock(ProfessionalSpecialistDao.class));
            registerMock(ClinicDAO.class, mock(ClinicDAO.class));

            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.next()).thenReturn(true);
            when(resultSet.getString("family_doctor")).thenReturn(familyDoctor);

            try (MockedStatic<DBHandler> dbHandler = mockStatic(DBHandler.class)) {
                dbHandler.when(() -> DBHandler.GetPreSQL("SELECT family_doctor FROM demographic WHERE demographic_no = ?", TEST_DEMOGRAPHIC_NO))
                        .thenReturn(resultSet);

                return new FrmConsultantRecord().getInitRefDoc(new Properties(), TEST_DEMOGRAPHIC_NO);
            }
        }
    }
}
