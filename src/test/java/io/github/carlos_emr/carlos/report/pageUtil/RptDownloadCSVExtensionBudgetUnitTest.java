/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.report.pageUtil;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.report.data.ConfiguredReportQuery;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.io.StringReader;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Actual bounded reader regressions for extension export refusal before response commitment.
 * @since 2026-11-16
 */
@Tag("unit")
@Tag("report")
class RptDownloadCSVExtensionBudgetUnitTest {
    @ParameterizedTest
    @EnumSource(BudgetCase.class)
    void shouldRefuseBeforeMaterializingTheCohort_whenExtensionBudgetIsExceeded(BudgetCase budget)
            throws Exception {
        ConfiguredReportQuery.Limit limit = budget.limit;
        var request = new MockHttpServletRequest();
        request.getSession(true);
        request.setParameter("demoReport", "true");
        request.setParameter("first_name", "on");
        request.setParameter("prefer_language", "on");
        request.setParameter("filter_1", "on");
        request.setParameter("value_1", "demographic.first_name='FAKE'");
        request.setParameter("dateFormat_1", "");
        var response = new MockHttpServletResponse();
        var properties = mock(CarlosProperties.class);
        when(properties.containsKey(limit.setting())).thenReturn(true);
        when(properties.getProperty(limit.setting())).thenReturn(limit == ConfiguredReportQuery.Limit.ROWS ? "1"
                : budget == BudgetCase.COMBINED_OUTPUT ? "96" : "64");
        var demographic = new ConfiguredReportQuery.Row(Map.of("demographic_no", "41", "first_name", "FAKE"), List.of());
        ResultSet rows = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(rows.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(3);
        String[] names = {"demographic_no", "extension_key", "value"};
        for (int i = 1; i <= names.length; i++) {
            when(metadata.getColumnName(i)).thenReturn(names[i - 1]);
            when(metadata.getColumnLabel(i)).thenReturn(names[i - 1]);
            when(metadata.getColumnType(i)).thenReturn(i == 1 ? Types.INTEGER : Types.VARCHAR);
        }
        when(rows.next()).thenReturn(true);
        when(rows.getString(1)).thenReturn("41", "42");
        when(rows.getCharacterStream(2)).thenAnswer(call -> new StringReader("prefer_language"));
        AtomicInteger charactersRead = new AtomicInteger();
        when(rows.getCharacterStream(3)).thenAnswer(call -> new StringReader(
                limit == ConfiguredReportQuery.Limit.ROWS ? "French"
                        : "x".repeat(budget == BudgetCase.COMBINED_OUTPUT ? 20 : 262_144)) {
            @Override public int read(char[] buffer, int offset, int length) throws java.io.IOException {
                int count = super.read(buffer, offset, length);
                if (count > 0) charactersRead.addAndGet(count);
                return count;
            }
        });
        var legacy = mock(DemographicExtDao.class);
        when(legacy.findReportValues(anyString(), anyCollection(), anyBoolean()))
                .thenReturn(List.of(new DemographicExtDao.ReportValue(1, 41, "French")));
        try (var settings = mockStatic(CarlosProperties.class); var spring = mockStatic(SpringUtils.class);
                var jdbc = mockStatic(LegacyJdbcQuery.class);
                var query = mockStatic(ConfiguredReportQuery.class, CALLS_REAL_METHODS)) {
            settings.when(CarlosProperties::getInstance).thenReturn(properties);
            spring.when(() -> SpringUtils.getBean(DemographicExtDao.class)).thenReturn(legacy);
            query.when(() -> ConfiguredReportQuery.read(startsWith("select demographic_no,"), any(Object[].class)))
                    .thenReturn(new ConfiguredReportQuery.Result(List.of(), List.of(demographic)));
            jdbc.when(() -> LegacyJdbcQuery.getBoundedReportResultSet(any(), anyInt(), anyLong(), any(Object[].class)))
                    .thenReturn(rows);
            new RptDownloadCSVServlet().service(request, response);
        }
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getHeader("Content-Disposition")).isNull();
        assertThat(request.getAttribute("carlosReportLimitKey")).isEqualTo(limit.messageKey());
        verify(rows).close();
        verify(rows, times(budget == BudgetCase.OUTPUT ? 1 : 2)).next();
        assertThat(charactersRead.get()).isLessThanOrEqualTo(2048);
    }

    private enum BudgetCase {
        ROWS(ConfiguredReportQuery.Limit.ROWS),
        OUTPUT(ConfiguredReportQuery.Limit.OUTPUT),
        COMBINED_OUTPUT(ConfiguredReportQuery.Limit.OUTPUT);

        private final ConfiguredReportQuery.Limit limit;

        BudgetCase(ConfiguredReportQuery.Limit limit) {
            this.limit = limit;
        }
    }
}
