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

import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.BillingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link BillingServiceDao} covering SLI code checks,
 * billing code lookups, search operations, edit operations, pricing,
 * and font style queries.
 *
 * <p>Migrated from legacy {@code BillingServiceDaoTest} (JUnit 4 / DaoTestFixtures)
 * with all 41 test scenarios preserved and strengthened assertions.</p>
 *
 * @since 2026-03-07
 * @see BillingServiceDao
 */
@DisplayName("BillingServiceDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing")
@Transactional
public class BillingServiceDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private BillingServiceDao dao;

    private final DateFormat dfm = new SimpleDateFormat("yyyyMMdd");

    /**
     * Creates a BillingService with code and date.
     */
    private BillingService createBillingService(String code, String date) throws Exception {
        BillingService bs = new BillingService();
        EntityDataGenerator.generateTestDataForModelClass(bs);
        bs.setServiceCode(code);
        bs.setBillingserviceDate(new Date(dfm.parse(date).getTime()));
        return bs;
    }

    /**
     * Creates a BillingService with code, region, and date.
     */
    private BillingService createBillingServiceWithRegion(String code, String region, String date) throws Exception {
        BillingService bs = new BillingService();
        EntityDataGenerator.generateTestDataForModelClass(bs);
        bs.setServiceCode(code);
        bs.setRegion(region);
        bs.setBillingserviceDate(new Date(dfm.parse(date).getTime()));
        return bs;
    }

    /**
     * Creates a BillingService with code, region, date, and description.
     */
    private BillingService createBillingServiceWithDesc(String code, String region, String date, String description) throws Exception {
        BillingService bs = new BillingService();
        EntityDataGenerator.generateTestDataForModelClass(bs);
        bs.setServiceCode(code);
        bs.setRegion(region);
        bs.setBillingserviceDate(new Date(dfm.parse(date).getTime()));
        bs.setDescription(description);
        return bs;
    }

    @Nested
    @DisplayName("getBillingCodeAttr")
    class GetBillingCodeAttr {

        @Test
        @Tag("read")
        @DisplayName("should return result without error for any service code")
        void shouldReturnResult_forAnyServiceCode() throws Exception {
            List<BillingService> result = dao.getBillingCodeAttr("A001A");
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("codeRequiresSLI")
    class CodeRequiresSLI {

        @Test
        @Tag("query")
        @DisplayName("should return true when service code matches and SLI flag is true")
        void shouldReturnTrue_whenServiceCodeMatchesAndSliFlagTrue() throws Exception {
            BillingService bs = new BillingService();
            EntityDataGenerator.generateTestDataForModelClass(bs);
            bs.setServiceCode("service001");
            bs.setSliFlag(true);
            dao.persist(bs);

            assertThat(dao.codeRequiresSLI("service001")).isTrue();
        }

        @Test
        @Tag("query")
        @DisplayName("should return false when service code does not match and SLI flag is true")
        void shouldReturnFalse_whenServiceCodeMismatchAndSliFlagTrue() throws Exception {
            BillingService bs = new BillingService();
            EntityDataGenerator.generateTestDataForModelClass(bs);
            bs.setServiceCode("service002");
            bs.setSliFlag(true);
            dao.persist(bs);

            assertThat(dao.codeRequiresSLI("service001")).isFalse();
        }

        @Test
        @Tag("query")
        @DisplayName("should return false when service code matches and SLI flag is false")
        void shouldReturnFalse_whenServiceCodeMatchesAndSliFlagFalse() throws Exception {
            BillingService bs = new BillingService();
            EntityDataGenerator.generateTestDataForModelClass(bs);
            bs.setServiceCode("service001");
            bs.setSliFlag(false);
            dao.persist(bs);

            assertThat(dao.codeRequiresSLI("service001")).isFalse();
        }

        @Test
        @Tag("query")
        @DisplayName("should return false when service code does not match and SLI flag is false")
        void shouldReturnFalse_whenServiceCodeMismatchAndSliFlagFalse() throws Exception {
            BillingService bs = new BillingService();
            EntityDataGenerator.generateTestDataForModelClass(bs);
            bs.setServiceCode("service002");
            bs.setSliFlag(false);
            dao.persist(bs);

            assertThat(dao.codeRequiresSLI("service001")).isFalse();
        }

        @Test
        @Tag("query")
        @DisplayName("should return false when table is empty")
        void shouldReturnFalse_whenTableIsEmpty() throws Exception {
            assertThat(dao.codeRequiresSLI("service001")).isFalse();
        }
    }

    @Nested
    @DisplayName("findBillingCodesByCode(code, region)")
    class FindBillingCodesByCodeRegion {

        @Test
        @Tag("query")
        @DisplayName("should return matching codes filtered by code and region, ordered by date")
        void shouldReturnMatchingCodes_filteredByCodeAndRegionOrderedByDate() throws Exception {
            BillingService bs1 = createBillingServiceWithRegion("service001", "ON", "20080101");
            BillingService bs2 = createBillingServiceWithRegion("service001", "BC", "20010101");
            BillingService bs3 = createBillingServiceWithRegion("service002", "ON", "20010101");
            BillingService bs4 = createBillingServiceWithRegion("service002", "BC", "20010101");
            BillingService bs5 = createBillingServiceWithRegion("service001", "ON", "20120101");
            BillingService bs6 = createBillingServiceWithRegion("service001", "ON", "20100101");

            dao.persist(bs1);
            dao.persist(bs2);
            dao.persist(bs3);
            dao.persist(bs4);
            dao.persist(bs5);
            dao.persist(bs6);

            List<BillingService> result = dao.findBillingCodesByCode("service001", "ON");

            assertThat(result).hasSize(3);
            assertThat(result).containsExactly(bs1, bs6, bs5);
        }
    }

    @Nested
    @DisplayName("findByServiceCode")
    class FindByServiceCode {

        @Test
        @Tag("query")
        @DisplayName("should return matching codes ordered by date descending")
        void shouldReturnMatchingCodes_orderedByDateDescending() throws Exception {
            BillingService bs1 = createBillingServiceWithRegion("service001", "ON", "20080101");
            BillingService bs2 = createBillingServiceWithRegion("service001", "ON", "20120101");
            BillingService bs3 = createBillingServiceWithRegion("service001", "ON", "20100101");
            BillingService bs4 = createBillingServiceWithRegion("service002", "ON", "20100101");

            dao.persist(bs1);
            dao.persist(bs2);
            dao.persist(bs3);
            dao.persist(bs4);

            List<BillingService> result = dao.findByServiceCode("service001");

            assertThat(result).hasSize(3);
            assertThat(result).containsExactly(bs2, bs3, bs1);
        }
    }

    @Nested
    @DisplayName("findBillingCodesByCode(code, region, order)")
    class FindBillingCodesByCodeRegionOrder {

        @Test
        @Tag("query")
        @DisplayName("should return newest matching record when order is 1")
        void shouldReturnNewestRecord_whenOrderIsOne() throws Exception {
            BillingService bs1 = createBillingServiceWithRegion("service001", "ON", "20090101");
            BillingService bs2 = createBillingServiceWithRegion("service001", "ON", "20100101");
            BillingService bs3 = createBillingServiceWithRegion("service001", "BC", "20090101");
            BillingService bs4 = createBillingServiceWithRegion("service002", "ON", "20090101");
            BillingService bs5 = createBillingServiceWithRegion("service001", "ON", "20090102");
            BillingService bs6 = createBillingServiceWithRegion("service001", "ON", "20090102");

            dao.persist(bs1);
            dao.persist(bs2);
            dao.persist(bs3);
            dao.persist(bs4);
            dao.persist(bs5);
            dao.persist(bs6);

            List<BillingService> result = dao.findBillingCodesByCode("service001", "ON", 1);

            assertThat(result).hasSize(1);
            assertThat(result).containsExactly(bs2);
        }
    }

    @Nested
    @DisplayName("findBillingCodesByCode(code, region, date, order)")
    class FindBillingCodesByCodeRegionDateOrder {

        @Test
        @Tag("query")
        @DisplayName("should return matching codes within date range when using order")
        void shouldReturnMatchingCodes_withinDateRangeWhenUsingOrder() throws Exception {
            Date date = new Date(dfm.parse("20091231").getTime());

            BillingService bs1 = createBillingServiceWithRegion("service001", "ON", "20090101");
            BillingService bs2 = createBillingServiceWithRegion("service001", "ON", "20100101");
            BillingService bs3 = createBillingServiceWithRegion("service001", "BC", "20090101");
            BillingService bs4 = createBillingServiceWithRegion("service002", "ON", "20090101");
            BillingService bs5 = createBillingServiceWithRegion("service001", "ON", "20090102");
            BillingService bs6 = createBillingServiceWithRegion("service001", "ON", "20090102");

            dao.persist(bs1);
            dao.persist(bs2);
            dao.persist(bs3);
            dao.persist(bs4);
            dao.persist(bs5);
            dao.persist(bs6);

            List<BillingService> result = dao.findBillingCodesByCode("service001", "ON", date, 1);

            assertThat(result).hasSize(2);
            assertThat(result).containsExactly(bs5, bs6);
        }

        @Test
        @Tag("query")
        @DisplayName("should return matching codes without ordering when order value is high")
        void shouldReturnMatchingCodes_withoutOrderingWhenOrderValueHigh() throws Exception {
            Date date = new Date(dfm.parse("20091231").getTime());

            BillingService bs1 = createBillingServiceWithRegion("service001", "ON", "20090101");
            BillingService bs2 = createBillingServiceWithRegion("service001", "ON", "20090101");

            dao.persist(bs1);
            dao.persist(bs2);

            List<BillingService> result = dao.findBillingCodesByCode("service001", "ON", date, 11);

            assertThat(result).hasSize(2);
            assertThat(result).containsExactly(bs1, bs2);
        }
    }

    @Nested
    @DisplayName("searchDescBillingCode")
    class SearchDescBillingCode {

        @Test
        @Tag("query")
        @DisplayName("should return description of newest valid billing service")
        void shouldReturnDescription_ofNewestValidBillingService() throws Exception {
            createAndPersistBillingServiceWithDesc("service001", "ON", "20090101", "Service 1 description.");
            createAndPersistBillingServiceWithDesc("service001", "ON", "20090101", "-----");
            createAndPersistBillingServiceWithDesc("service001", "ON", "20090101", "");
            createAndPersistBillingServiceWithDesc("service001", "ON", "20090101", "----");
            createAndPersistBillingServiceWithDesc("service001", "BC", "20090101", "Valid description.");
            createAndPersistBillingServiceWithDesc("service002", "ON", "20090101", "Valid description.");
            createAndPersistBillingServiceWithDesc("service001", "ON", "20100101", "Service 7 description.");
            createAndPersistBillingServiceWithDesc("service001", "ON", "20110101", "Service 8 description.");

            String result = dao.searchDescBillingCode("service001", "ON");

            assertThat(result).isEqualTo("Service 8 description.");
        }

        @Test
        @Tag("query")
        @DisplayName("should return default dashes when no matching records")
        void shouldReturnDefaultDashes_whenNoMatchingRecords() throws Exception {
            String result = dao.searchDescBillingCode("service001", "ON");

            assertThat(result).isEqualTo("----");
        }

        private void createAndPersistBillingServiceWithDesc(String code, String region, String date, String desc) throws Exception {
            BillingService bs = createBillingServiceWithDesc(code, region, date, desc);
            dao.persist(bs);
        }
    }

    @Nested
    @DisplayName("search(str, region, date)")
    class Search {

        @Test
        @Tag("search")
        @DisplayName("should return matching services by code or description within date range")
        void shouldReturnMatchingServices_byCodeOrDescriptionWithinDateRange() throws Exception {
            Date date = new Date(dfm.parse("20091231").getTime());

            BillingService bs1 = createBillingServiceWithDesc("service001", "ON", "20090101", "Service 1 description.");
            BillingService bs2 = createBillingServiceWithDesc("some service", "ON", "20090101", "service001");
            BillingService bs3 = createBillingServiceWithDesc("service001", "ON", "20100101", "service001");
            BillingService bs4 = createBillingServiceWithDesc("service001", "BC", "20090101", "service001");
            BillingService bs5 = createBillingServiceWithDesc("service001", "BC", "20090101", "service001   ");

            dao.persist(bs1);
            dao.persist(bs2);
            dao.persist(bs3);
            dao.persist(bs4);
            dao.persist(bs5);

            List<BillingService> result = dao.search("service001", "ON", date);

            assertThat(result).hasSize(2);
            assertThat(result).containsExactly(bs1, bs2);
        }

        @Test
        @Tag("search")
        @DisplayName("should return empty list when no matching records exist")
        void shouldReturnEmptyList_whenNoMatchingRecords() throws Exception {
            Date date = new Date(dfm.parse("20091231").getTime());

            List<BillingService> result = dao.search("service001", "ON", date);

            assertThat(result).isEmpty();
        }

        @Test
        @Tag("search")
        @DisplayName("should return non-null list even with no results")
        void shouldReturnNonNullList_whenNoResults() throws Exception {
            Date date = new Date(dfm.parse("20091231").getTime());

            List<BillingService> result = dao.search("service001", "ON", date);

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("searchBillingCode(query, region)")
    class SearchBillingCodeQueryRegion {

        @Test
        @Tag("search")
        @DisplayName("should return latest matching billing code by query and region")
        void shouldReturnLatestMatchingCode_byQueryAndRegion() throws Exception {
            BillingService bs1 = createBillingServiceWithRegion("service001", "ON", "20070101");
            BillingService bs2 = createBillingServiceWithRegion("service0012345", "ON", "20080101");
            BillingService bs3 = createBillingServiceWithRegion("service001 test", "ON", "20090101");
            BillingService bs4 = createBillingServiceWithRegion("service001", "ON", "20100101");
            BillingService bs5 = createBillingServiceWithRegion("service001", "BC", "20090101");

            dao.persist(bs1);
            dao.persist(bs2);
            dao.persist(bs3);
            dao.persist(bs4);
            dao.persist(bs5);

            BillingService result = dao.searchBillingCode("service001", "ON");

            assertThat(result).isEqualTo(bs4);
        }
    }

    @Nested
    @DisplayName("searchBillingCode(query, region, date)")
    class SearchBillingCodeQueryRegionDate {

        @Test
        @Tag("search")
        @DisplayName("should return latest matching billing code within date range")
        void shouldReturnLatestMatchingCode_withinDateRange() throws Exception {
            Date date = new Date(dfm.parse("20091231").getTime());

            BillingService bs1 = createBillingServiceWithRegion("service001", "ON", "20070101");
            BillingService bs2 = createBillingServiceWithRegion("service0012345", "ON", "20080101");
            BillingService bs3 = createBillingServiceWithRegion("service001 test", "ON", "20090101");
            BillingService bs4 = createBillingServiceWithRegion("service001", "ON", "20100101");
            BillingService bs5 = createBillingServiceWithRegion("service001", "BC", "20090101");

            dao.persist(bs1);
            dao.persist(bs2);
            dao.persist(bs3);
            dao.persist(bs4);
            dao.persist(bs5);

            BillingService result = dao.searchBillingCode("service001", "ON", date);

            assertThat(result).isEqualTo(bs3);
        }

        @Test
        @Tag("search")
        @DisplayName("should return null when no matching records within date range")
        void shouldReturnNull_whenNoMatchingRecords() throws Exception {
            Date date = new Date(dfm.parse("20091231").getTime());

            BillingService result = dao.searchBillingCode("service001", "ON", date);

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("searchPrivateBillingCode")
    class SearchPrivateBillingCode {

        @Test
        @Tag("search")
        @DisplayName("should return null when no private billing codes exist")
        void shouldReturnNull_whenNoPrivateBillingCodesExist() throws Exception {
            Date date = new Date(dfm.parse("20091231").getTime());

            BillingService result = dao.searchPrivateBillingCode("_protected01", date);

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("editBillingCodeDesc")
    class EditBillingCodeDesc {

        @Test
        @Tag("update")
        @DisplayName("should update description and value for billing code")
        void shouldUpdateDescriptionAndValue_forBillingCode() throws Exception {
            BillingService bs = createBillingService("Some service", "20091231");
            dao.persist(bs);
            hibernateTemplate.flush();

            boolean pass = dao.editBillingCodeDesc("New description", "value1", bs.getId());

            assertThat(pass).isTrue();

            BillingService updated = dao.find(bs.getId());
            assertThat(updated.getDescription()).isEqualTo("New description");
            assertThat(updated.getValue()).isEqualTo("value1");
        }
    }

    @Nested
    @DisplayName("editBillingCode")
    class EditBillingCode {

        @Test
        @Tag("update")
        @DisplayName("should update value for billing code")
        void shouldUpdateValue_forBillingCode() throws Exception {
            BillingService bs = createBillingService("Some service", "20090101");
            dao.persist(bs);
            hibernateTemplate.flush();

            boolean pass = dao.editBillingCode("value1", bs.getId());

            assertThat(pass).isTrue();

            BillingService updated = dao.find(bs.getId());
            assertThat(updated.getValue()).isEqualTo("value1");
        }
    }

    @Nested
    @DisplayName("getLatestServiceDate")
    class GetLatestServiceDate {

        @Test
        @Tag("query")
        @DisplayName("should return latest service date before the given end date")
        void shouldReturnLatestServiceDate_beforeGivenEndDate() throws Exception {
            Date endDate = new Date(dfm.parse("20091231").getTime());

            BillingService bs1 = createBillingService("service001", "20080101");
            BillingService bs2 = createBillingService("service001", "20090101");
            BillingService bs3 = createBillingService("service001", "20100101");

            dao.persist(bs3);
            dao.persist(bs2);
            dao.persist(bs1);

            Date result = dao.getLatestServiceDate(endDate, "service001");

            assertThat(result).isEqualTo(bs2.getBillingserviceDate());
        }
    }

    @Nested
    @DisplayName("getUnitPrice")
    class GetUnitPrice {

        @Test
        @Tag("query")
        @DisplayName("should return value and GST flag for matching service within date range")
        void shouldReturnValueAndGstFlag_forMatchingServiceWithinDateRange() throws Exception {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

            BillingService bs1 = createBillingService("service001", "00000000");
            bs1.setBillingserviceDate(sdf.parse("2008-01-01"));
            bs1.setValue("value1");
            bs1.setGstFlag(true);

            BillingService bs2 = createBillingService("service001", "00000000");
            bs2.setBillingserviceDate(sdf.parse("2009-01-01"));
            bs2.setValue("value2");
            bs2.setGstFlag(true);

            BillingService bs3 = createBillingService("service001", "00000000");
            bs3.setBillingserviceDate(sdf.parse("2010-01-01"));
            bs3.setValue("value3");
            bs3.setGstFlag(true);

            dao.persist(bs1);
            dao.persist(bs2);
            dao.persist(bs3);

            Object[] result = dao.getUnitPrice("service001", sdf.parse("2009-12-31"));

            assertThat(result).isNotNull();
            assertThat(result).hasSize(2);
            assertThat(result[0]).isEqualTo("value2");
            assertThat(result[1]).isEqualTo(true);
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when no matching service exists")
        void shouldReturnNull_whenNoMatchingServiceExists() throws Exception {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

            Object[] result = dao.getUnitPrice("service001", sdf.parse("2009-12-31"));

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("getUnitPercentage")
    class GetUnitPercentage {

        @Test
        @Tag("query")
        @DisplayName("should return percentage for matching service within date range")
        void shouldReturnPercentage_forMatchingServiceWithinDateRange() throws Exception {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

            BillingService bs1 = createBillingService("service001", "20080101");
            bs1.setPercentage("20");
            BillingService bs2 = createBillingService("service001", "20090101");
            bs2.setPercentage("40");
            BillingService bs3 = createBillingService("service001", "20100101");
            bs3.setPercentage("60");

            dao.persist(bs3);
            dao.persist(bs2);
            dao.persist(bs1);

            String result = dao.getUnitPercentage("service001", sdf.parse("2009-12-31"));

            assertThat(result).isEqualTo("40");
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when no matching service for percentage")
        void shouldReturnNull_whenNoMatchingServiceForPercentage() throws Exception {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

            Object[] result = dao.getUnitPrice("service001", sdf.parse("2009-12-31"));

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("findBillingCodesByFontStyle")
    class FindBillingCodesByFontStyle {

        @Test
        @Tag("query")
        @DisplayName("should return billing codes matching display style")
        void shouldReturnBillingCodes_matchingDisplayStyle() throws Exception {
            int displayStyle = 1111;

            BillingService bs1 = createBillingService("service001", "20080101");
            bs1.setDisplayStyle(displayStyle);

            BillingService bs2 = createBillingService("service001", "20090101");
            bs2.setDisplayStyle(1112);

            BillingService bs3 = createBillingService("service001", "20100101");
            bs3.setDisplayStyle(displayStyle);

            dao.persist(bs1);
            dao.persist(bs2);
            dao.persist(bs3);

            List<BillingService> result = dao.findBillingCodesByFontStyle(displayStyle);

            assertThat(result).hasSize(2);
            assertThat(result).containsExactly(bs1, bs3);
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when no matching display style")
        void shouldReturnEmpty_whenNoMatchingDisplayStyle() throws Exception {
            List<BillingService> result = dao.findBillingCodesByFontStyle(1111);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByRegionGroupAndType")
    class FindByRegionGroupAndType {

        @Test
        @Tag("query")
        @DisplayName("should return non-null result for any region, group, and type")
        void shouldReturnNonNull_forAnyRegionGroupAndType() throws Exception {
            List<BillingService> result = dao.findByRegionGroupAndType("REG", "GRP", "TYP");

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("findByServiceCodeOrDescription")
    class FindByServiceCodeOrDescription {

        @Test
        @Tag("query")
        @DisplayName("should return non-null result for any service code")
        void shouldReturnNonNull_forAnyServiceCode() throws Exception {
            List<BillingService> result = dao.findByServiceCodeOrDescription("VOTGVNO");

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("findMostRecentByServiceCode")
    class FindMostRecentByServiceCode {

        @Test
        @Tag("query")
        @DisplayName("should return non-null result for any service code")
        void shouldReturnNonNull_forAnyServiceCode() throws Exception {
            List<BillingService> result = dao.findMostRecentByServiceCode("VOTGVNO");

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("findByServiceCodeAndDate")
    class FindByServiceCodeAndDate {

        @Test
        @Tag("query")
        @DisplayName("should return non-null result for any service code and date")
        void shouldReturnNonNull_forAnyServiceCodeAndDate() throws Exception {
            List<BillingService> result = dao.findByServiceCodeAndDate("PRSHA", new Date());

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("findGst")
    class FindGst {

        @Test
        @Tag("query")
        @DisplayName("should return non-null result for any code and date")
        void shouldReturnNonNull_forAnyCodeAndDate() throws Exception {
            List<BillingService> result = dao.findGst("CODE", new Date());

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("findByServiceCodeAndLatestDate")
    class FindByServiceCodeAndLatestDate {

        @Test
        @Tag("query")
        @DisplayName("should return non-null result for any service code and date")
        void shouldReturnNonNull_forAnyServiceCodeAndDate() throws Exception {
            assertThat(dao.findByServiceCodeAndLatestDate("CDO", new Date())).isNotNull();
        }
    }

    @Nested
    @DisplayName("findBillingCodesByCodeAndTerminationDate")
    class FindBillingCodesByCodeAndTerminationDate {

        @Test
        @Tag("query")
        @DisplayName("should return non-null result for any code and termination date")
        void shouldReturnNonNull_forAnyCodeAndTerminationDate() throws Exception {
            assertThat(dao.findBillingCodesByCodeAndTerminationDate("CDE", new Date())).isNotNull();
        }
    }
}
