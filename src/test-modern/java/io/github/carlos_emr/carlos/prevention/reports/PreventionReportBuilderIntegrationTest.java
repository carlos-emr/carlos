/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 *
 * Modifications by CARLOS Contributors, 2026.
 */
package io.github.carlos_emr.carlos.prevention.reports;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.dao.utils.AuthUtils;
import io.github.carlos_emr.carlos.prev.reports.Report;
import io.github.carlos_emr.carlos.prev.reports.ReportBuilder;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.PreventionSearchTo1;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ReportBuilder}, verifying that prevention reports
 * can be generated with demographic and roster criteria.
 *
 * <p>Migrated from legacy JUnit 4 {@code PreventionReportBuilderTest}.</p>
 *
 * @see ReportBuilder
 * @see Report
 * @see PreventionSearchTo1
 * @since 2015-01-01
 */
@Tag("integration")
@Tag("prevention")
@Tag("read")
@DisplayName("PreventionReportBuilder Integration Tests")
class PreventionReportBuilderIntegrationTest extends CarlosTestBase {

    @Test
    @DisplayName("should run prevention report with age and roster criteria")
    void shouldRunPreventionReport_withAgeAndRosterCriteria() {
        String providerNo = "-1";
        PreventionSearchTo1 preventionSearchTo1 = new PreventionSearchTo1();
        preventionSearchTo1.setAge1("2");
        preventionSearchTo1.setAgeStyle("2");
        preventionSearchTo1.setAgeCalc("0");
        preventionSearchTo1.setRosterStat("RO");
        preventionSearchTo1.setSex("1");

        LoggedInInfo loggedInInfo = AuthUtils.initLoginContext();
        ReportBuilder reportBuilder = new ReportBuilder();
        Report report = reportBuilder.runReport(loggedInInfo, providerNo, preventionSearchTo1);
        logger.info("Number of items: " + report.getItems().size());

        // The legacy test asserted assertEquals(1, 1) which always passes.
        // Preserving the intent: verify report runs without error and returns non-null.
        assertThat(report).isNotNull();
        assertThat(report.getItems()).isNotNull();
    }
}
