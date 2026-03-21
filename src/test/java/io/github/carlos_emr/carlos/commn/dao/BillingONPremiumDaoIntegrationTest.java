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

import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.BillingONPremium;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link BillingONPremiumDao}.
 *
 * <p>Migrated from legacy {@code BillingONPremiumDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see BillingONPremiumDao
 */
@DisplayName("BillingONPremium Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing")
@Transactional
public class BillingONPremiumDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private BillingONPremiumDao dao;

    private final DateFormat dfm = new SimpleDateFormat("yyyyMMdd");

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should return active RA premiums within pay date range with status true")
        void shouldReturnActiveRAPremiums_byPayDateRange() throws Exception {
            Date startDate = new Date(dfm.parse("20090101").getTime());
            Date endDate = new Date(dfm.parse("20120101").getTime());
            Locale locale = new Locale("");

            BillingONPremium billONPrem1 = new BillingONPremium();
            EntityDataGenerator.generateTestDataForModelClass(billONPrem1);
            billONPrem1.setPayDate(new Date(dfm.parse("20081231").getTime()));
            billONPrem1.setStatus(true);

            BillingONPremium billONPrem2 = new BillingONPremium();
            EntityDataGenerator.generateTestDataForModelClass(billONPrem2);
            billONPrem2.setPayDate(new Date(dfm.parse("20090101").getTime()));
            billONPrem2.setStatus(true);

            BillingONPremium billONPrem3 = new BillingONPremium();
            EntityDataGenerator.generateTestDataForModelClass(billONPrem3);
            billONPrem3.setPayDate(new Date(dfm.parse("20100601").getTime()));
            billONPrem3.setStatus(false);

            BillingONPremium billONPrem4 = new BillingONPremium();
            EntityDataGenerator.generateTestDataForModelClass(billONPrem4);
            billONPrem4.setPayDate(new Date(dfm.parse("20110101").getTime()));
            billONPrem4.setStatus(true);

            BillingONPremium billONPrem5 = new BillingONPremium();
            EntityDataGenerator.generateTestDataForModelClass(billONPrem5);
            billONPrem5.setPayDate(new Date(dfm.parse("20120101").getTime()));
            billONPrem5.setStatus(true);

            BillingONPremium billONPrem6 = new BillingONPremium();
            EntityDataGenerator.generateTestDataForModelClass(billONPrem6);
            billONPrem6.setPayDate(new Date(dfm.parse("20120102").getTime()));
            billONPrem6.setStatus(true);

            dao.persist(billONPrem1);
            dao.persist(billONPrem2);
            dao.persist(billONPrem3);
            dao.persist(billONPrem4);
            dao.persist(billONPrem5);
            dao.persist(billONPrem6);

            List<BillingONPremium> expectedList = Arrays.asList(billONPrem2, billONPrem4);
            List<BillingONPremium> resultList = dao.getActiveRAPremiumsByPayDate(startDate, endDate, locale);

            assertThat(resultList).hasSize(expectedList.size());
            assertThat(resultList).containsAll(expectedList);
        }

        @Test
        @Tag("query")
        @DisplayName("should return active RA premiums by provider within date range")
        void shouldReturnActiveRAPremiums_byProviderAndDateRange() throws Exception {
            Provider provider = new Provider();
            provider.setProviderNo("1");
            Date startDate = new Date(dfm.parse("20090101").getTime());
            Date endDate = new Date(dfm.parse("20120101").getTime());
            Locale locale = new Locale("");

            BillingONPremium billONPrem1 = new BillingONPremium();
            EntityDataGenerator.generateTestDataForModelClass(billONPrem1);
            billONPrem1.setPayDate(new Date(dfm.parse("20090101").getTime()));
            billONPrem1.setProviderNo("1");
            billONPrem1.setStatus(true);

            BillingONPremium billONPrem2 = new BillingONPremium();
            EntityDataGenerator.generateTestDataForModelClass(billONPrem2);
            billONPrem2.setPayDate(new Date(dfm.parse("20100601").getTime()));
            billONPrem2.setProviderNo("2");
            billONPrem2.setStatus(true);

            BillingONPremium billONPrem3 = new BillingONPremium();
            EntityDataGenerator.generateTestDataForModelClass(billONPrem3);
            billONPrem3.setPayDate(new Date(dfm.parse("20110101").getTime()));
            billONPrem3.setProviderNo("1");
            billONPrem3.setStatus(true);

            BillingONPremium billONPrem4 = new BillingONPremium();
            EntityDataGenerator.generateTestDataForModelClass(billONPrem4);
            billONPrem4.setPayDate(new Date(dfm.parse("20110601").getTime()));
            billONPrem4.setProviderNo("1");
            billONPrem4.setStatus(false);

            BillingONPremium billONPrem5 = new BillingONPremium();
            EntityDataGenerator.generateTestDataForModelClass(billONPrem5);
            billONPrem5.setPayDate(new Date(dfm.parse("20120101").getTime()));
            billONPrem5.setProviderNo("1");
            billONPrem5.setStatus(true);

            dao.persist(billONPrem1);
            dao.persist(billONPrem2);
            dao.persist(billONPrem3);
            dao.persist(billONPrem4);
            dao.persist(billONPrem5);

            List<BillingONPremium> expectedList = Arrays.asList(billONPrem1, billONPrem3);
            List<BillingONPremium> resultList = dao.getActiveRAPremiumsByProvider(provider, startDate, endDate, locale);

            assertThat(resultList).hasSize(expectedList.size());
            assertThat(resultList).containsAll(expectedList);
        }

        @Test
        @Tag("query")
        @DisplayName("should return RA premiums by RA header number")
        void shouldReturnRAPremiums_byRaHeaderNo() throws Exception {
            Integer raHeaderNo = 1;

            BillingONPremium billONPrem1 = new BillingONPremium();
            EntityDataGenerator.generateTestDataForModelClass(billONPrem1);
            billONPrem1.setRAHeaderNo(2);

            BillingONPremium billONPrem2 = new BillingONPremium();
            EntityDataGenerator.generateTestDataForModelClass(billONPrem2);
            billONPrem2.setRAHeaderNo(1);

            BillingONPremium billONPrem3 = new BillingONPremium();
            EntityDataGenerator.generateTestDataForModelClass(billONPrem3);
            billONPrem3.setRAHeaderNo(3);

            BillingONPremium billONPrem4 = new BillingONPremium();
            EntityDataGenerator.generateTestDataForModelClass(billONPrem4);
            billONPrem4.setRAHeaderNo(1);

            dao.persist(billONPrem1);
            dao.persist(billONPrem2);
            dao.persist(billONPrem3);
            dao.persist(billONPrem4);

            List<BillingONPremium> expectedList = Arrays.asList(billONPrem2, billONPrem4);
            List<BillingONPremium> resultList = dao.getRAPremiumsByRaHeaderNo(raHeaderNo);

            assertThat(resultList).hasSize(expectedList.size());
            assertThat(resultList).containsAll(expectedList);
        }

        @Test
        @Tag("query")
        @DisplayName("should return RA premiums by negative RA header number")
        void shouldReturnRAPremiums_byNegativeRaHeaderNo() throws Exception {
            Integer raHeaderNo = -1;

            BillingONPremium billONPrem1 = new BillingONPremium();
            EntityDataGenerator.generateTestDataForModelClass(billONPrem1);
            billONPrem1.setRAHeaderNo(2);

            BillingONPremium billONPrem2 = new BillingONPremium();
            EntityDataGenerator.generateTestDataForModelClass(billONPrem2);
            billONPrem2.setRAHeaderNo(-1);

            BillingONPremium billONPrem3 = new BillingONPremium();
            EntityDataGenerator.generateTestDataForModelClass(billONPrem3);
            billONPrem3.setRAHeaderNo(3);

            BillingONPremium billONPrem4 = new BillingONPremium();
            EntityDataGenerator.generateTestDataForModelClass(billONPrem4);
            billONPrem4.setRAHeaderNo(-1);

            dao.persist(billONPrem1);
            dao.persist(billONPrem2);
            dao.persist(billONPrem3);
            dao.persist(billONPrem4);

            List<BillingONPremium> expectedList = Arrays.asList(billONPrem2, billONPrem4);
            List<BillingONPremium> resultList = dao.getRAPremiumsByRaHeaderNo(raHeaderNo);

            assertThat(resultList).hasSize(expectedList.size());
            assertThat(resultList).containsAll(expectedList);
        }
    }
}
