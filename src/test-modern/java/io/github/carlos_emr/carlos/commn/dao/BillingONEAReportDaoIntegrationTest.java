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
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingProviderData;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.BillingONEAReport;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link BillingONEAReportDao}.
 *
 * <p>Migrated from legacy {@code BillingONEAReportDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see BillingONEAReportDao
 */
@DisplayName("BillingONEAReport Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing")
@Transactional
public class BillingONEAReportDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private BillingONEAReportDao dao;

    private BillingONEAReport createReport(int billNumber) throws Exception {
        BillingONEAReport report = new BillingONEAReport();
        EntityDataGenerator.generateTestDataForModelClass(report);
        report.setBillingNo(billNumber);
        return report;
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should return accurate billing errors list data")
        void shouldReturnAccurateBillingErrorsListData_whenReportPersisted() throws Exception {
            BillingONEAReport eaRpt = createReport(1);
            eaRpt.setClaimError("error01");
            eaRpt.setCodeError("error02");
            dao.persist(eaRpt);

            List<String> eaReportErrors = dao.getBillingErrorList(eaRpt.getBillingNo());

            assertThat(eaReportErrors.get(0)).isEqualTo("error01");
            assertThat(eaReportErrors.get(1)).isEqualTo("error02");
        }

        @Test
        @Tag("query")
        @DisplayName("should return trimmed billing errors and exclude blank errors")
        void shouldReturnTrimmedErrors_whenErrorsContainWhitespace() throws Exception {
            BillingONEAReport eaRpt = createReport(1);
            eaRpt.setClaimError("   ");
            eaRpt.setCodeError("   error02    ");
            dao.persist(eaRpt);

            List<String> eaReportErrors = dao.getBillingErrorList(eaRpt.getBillingNo());
            List<String> expectedList = new ArrayList<>(Arrays.asList("error02"));

            assertThat(eaReportErrors).isEqualTo(expectedList);
        }

        @Test
        @Tag("query")
        @DisplayName("should return billing errors ordered by process date descending")
        void shouldReturnErrorsOrderedByProcessDateDescending_whenMultipleReportsExist() throws Exception {
            DateFormat dfm = new SimpleDateFormat("yyyyMMdd");

            BillingONEAReport eaRpt1 = createReport(1);
            Date date1 = new Date(dfm.parse("20090101").getTime());
            eaRpt1.setProcessDate(date1);
            eaRpt1.setClaimError("error01");
            eaRpt1.setCodeError("error01");

            BillingONEAReport eaRpt2 = createReport(1);
            Date date2 = new Date(dfm.parse("20100101").getTime());
            eaRpt2.setProcessDate(date2);
            eaRpt2.setClaimError("error02");
            eaRpt2.setCodeError("error02");

            BillingONEAReport eaRpt3 = createReport(1);
            Date date3 = new Date(dfm.parse("20110101").getTime());
            eaRpt3.setProcessDate(date3);
            eaRpt3.setClaimError("error03");
            eaRpt3.setCodeError("error03");

            dao.persist(eaRpt1);
            dao.persist(eaRpt2);
            dao.persist(eaRpt3);

            List<String> eaReportErrors = dao.getBillingErrorList(eaRpt1.getBillingNo());
            List<String> expectedResult = Arrays.asList(
                    "error03", "error03",
                    "error02", "error02",
                    "error01", "error01");

            assertThat(eaReportErrors).hasSize(expectedResult.size());
            for (int i = 0; i < eaReportErrors.size(); i++) {
                assertThat(eaReportErrors.get(i)).isEqualTo(expectedResult.get(i));
            }
        }

        @Test
        @Tag("query")
        @DisplayName("should find reports by magic parameters")
        void shouldFindReports_byMagicParameters() throws Exception {
            assertThat(dao.findByMagic("OHIP", "BGNO", "SPEC CODE", new Date(), new Date(), "REPORT")).isNotNull();

            List<BillingProviderData> data = new ArrayList<>();
            BillingProviderData d = new BillingProviderData();
            EntityDataGenerator.generateTestDataForModelClass(d);
            data.add(d);
            d = new BillingProviderData();
            EntityDataGenerator.generateTestDataForModelClass(d);
            data.add(d);
            assertThat(dao.findByMagic(data, new Date(), new Date(), "REPORT")).isNotNull();
        }
    }
}
