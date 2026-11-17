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
package io.github.carlos_emr;

import io.github.carlos_emr.carlos.db.DBPreparedHandlerParam;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import java.sql.ResultSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Guards the parameterized report-index query paths, including unfiltered reports.
 * @since 2026-10-24
 */
@Tag("unit")
@DisplayName("Appointment main bean report query parameters")
class AppointmentMainBeanUnitTest {
    private final AppointmentMainBean bean = new AppointmentMainBean();

    @Test
    void shouldRunUnfilteredQuery_whenParametersAreEmpty() throws Exception {
        assertBoundQuery(new String[0], "SELECT provider_no FROM provider");
    }

    @Test
    void shouldBindParameters_whenProviderIsSpecified() throws Exception {
        assertBoundQuery(new String[]{"999998"}, "SELECT provider_no FROM provider WHERE provider_no=?");
    }

    private void assertBoundQuery(String[] parameters, String query) throws Exception {
        bean.doConfigure(new String[][]{{"providers", query}});
        ResultSet result = mock(ResultSet.class);
        try (MockedStatic<LegacyJdbcQuery> jdbc = mockStatic(LegacyJdbcQuery.class)) {
            jdbc.when(() -> LegacyJdbcQuery.queryResults(query, parameters)).thenReturn(result);
            assertThat(bean.queryResults(parameters, "providers")).isSameAs(result);
            jdbc.verify(() -> LegacyJdbcQuery.queryResults(query, parameters));
        }
    }

    @Test
    void shouldUseWildcardQuery_whenFirstParameterIsAsterisk() throws Exception {
        String query = "SELECT provider_no FROM provider";
        bean.doConfigure(new String[][]{{"search*", query}, {"providers", "unused"}});
        ResultSet result = mock(ResultSet.class);
        try (MockedStatic<LegacyJdbcQuery> jdbc = mockStatic(LegacyJdbcQuery.class)) {
            jdbc.when(() -> LegacyJdbcQuery.queryResults(eq(query), any(DBPreparedHandlerParam[].class)))
                    .thenReturn(result);
            assertThat(bean.queryResults(new String[]{"*"}, "providers")).isSameAs(result);
            jdbc.verify(() -> LegacyJdbcQuery.queryResults(query, new DBPreparedHandlerParam[0]));
        }
    }
}
