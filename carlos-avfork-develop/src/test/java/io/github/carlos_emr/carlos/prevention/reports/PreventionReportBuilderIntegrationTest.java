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
import org.junit.jupiter.api.Nested;
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

    @Nested
    @DisplayName("Report generation with search criteria")
    class ReportGeneration {

        @Test
        @DisplayName("should generate report with empty items list when no matching demographics")
        void shouldGenerateReport_withEmptyItemsList_whenNoMatchingDemographics() {
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

            assertThat(report).isNotNull();
            assertThat(report.getItems()).isNotNull();
            // With no demographics in test database matching the roster criteria,
            // the items list should be empty
            assertThat(report.getItems()).isEmpty();
            assertThat(report.getTotalPatients()).isEqualTo(0);
            assertThat(report.getIneligiblePatients()).isEqualTo(0);
            assertThat(report.getUp2date()).isEqualTo(0);
        }

        @Test
        @DisplayName("should preserve search configuration in report")
        void shouldPreserveSearchConfiguration_inReport() {
            String providerNo = "-1";
            PreventionSearchTo1 searchConfig = new PreventionSearchTo1();
            searchConfig.setAge1("50");
            searchConfig.setAgeStyle("2");
            searchConfig.setAgeCalc("0");
            searchConfig.setSex("2");

            LoggedInInfo loggedInInfo = AuthUtils.initLoginContext();
            ReportBuilder reportBuilder = new ReportBuilder();
            Report report = reportBuilder.runReport(loggedInInfo, providerNo, searchConfig);

            assertThat(report.getSearchConfig()).isNotNull();
            assertThat(report.getSearchConfig().getAge1()).isEqualTo("50");
            assertThat(report.getSearchConfig().getSex()).isEqualTo("2");
            assertThat(report.getSearchConfig().getProviderNo()).isEqualTo("-1");
            assertThat(report.getSearchConfig().getAgeAsOf()).isNotNull();
        }

        @Test
        @DisplayName("should generate report for male patients filter")
        void shouldGenerateReport_forMalePatientFilter() {
            String providerNo = "-1";
            PreventionSearchTo1 preventionSearchTo1 = new PreventionSearchTo1();
            preventionSearchTo1.setAge1("18");
            preventionSearchTo1.setAgeStyle("2");
            preventionSearchTo1.setAgeCalc("0");
            preventionSearchTo1.setSex("2");

            LoggedInInfo loggedInInfo = AuthUtils.initLoginContext();
            ReportBuilder reportBuilder = new ReportBuilder();
            Report report = reportBuilder.runReport(loggedInInfo, providerNo, preventionSearchTo1);

            assertThat(report).isNotNull();
            assertThat(report.getItems()).isNotNull();
            assertThat(report.isActive()).isTrue();
        }

        @Test
        @DisplayName("should generate report without roster status filter")
        void shouldGenerateReport_withoutRosterStatusFilter() {
            String providerNo = "-1";
            PreventionSearchTo1 preventionSearchTo1 = new PreventionSearchTo1();
            preventionSearchTo1.setAge1("65");
            preventionSearchTo1.setAgeStyle("2");
            preventionSearchTo1.setAgeCalc("0");
            // No roster status set - should not filter by roster
            preventionSearchTo1.setSex("1");

            LoggedInInfo loggedInInfo = AuthUtils.initLoginContext();
            ReportBuilder reportBuilder = new ReportBuilder();
            Report report = reportBuilder.runReport(loggedInInfo, providerNo, preventionSearchTo1);

            assertThat(report).isNotNull();
            assertThat(report.getItems()).isNotNull();
            // Items may be non-empty since no roster filter restricts the result
            assertThat(report.getSearchConfig().getRosterStat()).isNull();
        }
    }
}
