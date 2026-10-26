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
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.sql.ResultSet;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@DisplayName("FrmLabReq07Record Tests")
@Tag("unit")
@Tag("form")
class FrmLabReq07RecordUnitTest extends CarlosUnitTestBase {

    private static final String PROVIDER_NO = "999998";

    /**
     * The render path rebuilds {@code practitionerNo} from the provider record on every open,
     * discarding whatever the stored form row held. Issue #3724 is the consequence: a provider
     * with no {@code ohip_no} used to yield {@code 0000--00}, whose double hyphen the packaged
     * front door's libinjection rule (CRS 942100) rejects with 403 when the form is posted back,
     * so such a requisition could never be saved again.
     */
    @Nested
    @DisplayName("getFormCustRecord")
    class GetFormCustRecord {

        @Test
        @DisplayName("should leave the practitioner number empty when the provider has no billing number")
        void shouldLeavePractitionerNumberEmpty_whenProviderHasNoBillingNumber() throws Exception {
            Properties props = renderWith("", "<xml_p_fax>1234560789</xml_p_fax>");

            assertThat(props.getProperty("practitionerNo")).isEmpty();
        }

        @Test
        @DisplayName("should build the practitioner number when the provider has a billing number")
        void shouldBuildPractitionerNumber_whenProviderHasBillingNumber() throws Exception {
            Properties props = renderWith("123456", "<xml_p_specialty_code>07</xml_p_specialty_code>");

            assertThat(props.getProperty("practitionerNo")).isEqualTo("0000-123456-07");
        }

        @Test
        @DisplayName("should discard a stored practitioner number that the proxy would reject")
        void shouldDiscardStoredPractitionerNumber_whenItCarriesEmptySegments() throws Exception {
            Properties props = new Properties();
            props.setProperty("demoProvider", PROVIDER_NO);
            // What the shipped Ontario demo snapshot holds for this row, and what the previous
            // render path regenerated. Re-emitting it is what made the save unsaveable.
            props.setProperty("practitionerNo", "0000--00");

            assertThat(render(props, "", "").getProperty("practitionerNo")).doesNotContain("--");
        }

        private Properties renderWith(String ohipNo, String comments) throws Exception {
            Properties props = new Properties();
            props.setProperty("demoProvider", PROVIDER_NO);
            return render(props, ohipNo, comments);
        }

        private Properties render(Properties props, String ohipNo, String comments) throws Exception {
            // FrmRecord resolves the first two from Spring in its constructor; the record itself
            // resolves the clinic DAO as a field initialiser.
            registerMock(DemographicManager.class, mock(DemographicManager.class));
            registerMock(DemographicExtDao.class, mock(DemographicExtDao.class));
            registerMock(ClinicDAO.class, mock(ClinicDAO.class));

            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.next()).thenReturn(true);
            when(resultSet.getString("provName")).thenReturn("carlosdoc, doctor");
            when(resultSet.getString("ohip_no")).thenReturn(ohipNo);
            when(resultSet.getString("comments")).thenReturn(comments);

            try (MockedStatic<LegacyJdbcQuery> legacyJdbcQuery = mockStatic(LegacyJdbcQuery.class)) {
                legacyJdbcQuery.when(() -> LegacyJdbcQuery.getPreparedResultSet(contains("provider"), anyString()))
                        .thenReturn(resultSet);

                return new FrmLabReq07Record().getFormCustRecord(null, null, props, PROVIDER_NO);
            }
        }
    }
}
