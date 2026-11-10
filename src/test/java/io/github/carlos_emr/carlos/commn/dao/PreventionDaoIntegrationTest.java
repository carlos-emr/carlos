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
import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.commn.model.PartialDate;
import io.github.carlos_emr.carlos.prevention.dto.PreventionDateDTO;
import io.github.carlos_emr.carlos.prevention.dto.PreventionDatePrecisionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link PreventionDao} covering demographic-based queries,
 * deleted/active filtering, type+date searches, and date-based lookups.
 *
 * <p>Migrated from legacy {@code PreventionDaoTest} (JUnit 4 / DaoTestFixtures)
 * with expanded coverage including edge cases for date ranges and deletion flags.</p>
 *
 * @since 2026-03-07
 * @see PreventionDao
 */
@DisplayName("PreventionDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("prevention")
@Transactional
public class PreventionDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private PreventionDao preventionDao;
    @Autowired
    private PartialDateDao partialDateDao;
    @Autowired
    private PreventionExtDao preventionExtDao;

    private void markImported(Prevention prevention) {
        var provenance = new io.github.carlos_emr.carlos.commn.model.PreventionExt();
        provenance.setPreventionId(prevention.getId());
        provenance.setKeyval("cdsDatePrecision");
        provenance.setVal("YEAR");
        preventionExtDao.persist(provenance);
    }

    private static final int DEMO_1 = 20001;
    private static final int DEMO_2 = 20002;
    private final DateFormat dfm = new SimpleDateFormat("yyyyMMdd");

    private Prevention createPrevention(int demographicId, String type, Date preventionDate,
                                         boolean deleted, boolean refused) {
        Prevention p = new Prevention();
        p.setDemographicId(demographicId);
        p.setPreventionType(type);
        p.setPreventionDate(preventionDate);
        p.setDeleted(deleted);
        p.setRefused(refused);
        p.setCreatorProviderNo("999998");
        p.setLastUpdateDate(new Date());
        preventionDao.persist(p);
        return p;
    }

    private Prevention createSimplePrevention(int demographicId, boolean deleted) {
        return createPrevention(demographicId, "Flu", new Date(), deleted, false);
    }

    @Test
    void shouldKeepStorageAnchor_forNativeYearOnlyPrevention() {
        Date anchor = java.sql.Date.valueOf("2019-01-01");
        Prevention nativeRecord = createPrevention(DEMO_1, "Td", anchor, false, false);
        partialDateDao.persist(new PartialDate(4, nativeRecord.getId(), 7, "YYYY"));
        // aaaf4444 selected these native rows using their stored dates, including
        // the latest-per-type query. CDS provenance must not change that contract.
        assertThat(preventionDao.findUniqueByDemographicId(DEMO_1)).singleElement()
                .satisfies(row -> assertThat(row.getPreventionDate().getTime()).isEqualTo(anchor.getTime()));
        assertThat(preventionDao.findByTypeAndDate("Td", anchor, java.sql.Date.valueOf("2019-12-31")))
                .extracting(Prevention::getId).contains(nativeRecord.getId());
        assertThat(preventionDao.hasUncertainPreventionDates(DEMO_1)).isFalse();
    }

    @Test
    void shouldPreservePartialAndUnknownDates_inLightweightProjection() {
        Prevention exact = createPrevention(DEMO_1, "Td", java.sql.Date.valueOf("2019-01-01"), false, false);
        Prevention partial = createPrevention(DEMO_1, "Td", java.sql.Date.valueOf("2019-01-01"), false, false);
        Prevention unknown = createPrevention(DEMO_1, "Td", null, false, false);
        markImported(partial);
        markImported(unknown);
        partialDateDao.persist(new PartialDate(4, partial.getId(), 7, "YYYY"));

        assertThat(preventionDao.hasUncertainPreventionDates(DEMO_1)).isTrue();
        assertThat(preventionDao.hasUncertainPreventionDates(DEMO_2)).isFalse();
        assertThat(preventionDao.hasUncertainPreventionDates(null)).isFalse();

        var rows = preventionDao.findPreventionDTOsByDemographicId(DEMO_1);
        assertThat(rows).hasSize(3);
        var exactRow = rows.stream().filter(p -> p.getId().equals(exact.getId())).findFirst().orElseThrow();
        assertThat(exactRow.getPreventionDateInfo().getPrecision()).isEqualTo(PreventionDateDTO.Precision.FULL_DATE);
        assertThat(exactRow.getPreventionDate()).isNotNull();
        assertThat(exactRow.getPreventionDate().getTime()).isEqualTo(exact.getPreventionDate().getTime());
        var partialRow = rows.stream().filter(p -> p.getId().equals(partial.getId())).findFirst().orElseThrow();
        assertThat(partialRow.getPreventionDateInfo().getPrecision()).isEqualTo(PreventionDateDTO.Precision.YEAR);
        assertThat(partialRow.getPreventionDateInfo().getOriginalValue()).isEqualTo("2019");
        assertThatThrownBy(partialRow::getPreventionDate).isInstanceOf(PreventionDatePrecisionException.class);
        var unknownRow = rows.stream().filter(p -> p.getId().equals(unknown.getId())).findFirst().orElseThrow();
        assertThat(unknownRow.getPreventionDateInfo().getPrecision()).isEqualTo(PreventionDateDTO.Precision.UNKNOWN);
    }

    @Test
    void shouldUseLatestNativeFormat_whenPrecisionMetadataIsCleared() {
        Prevention partial = createPrevention(DEMO_1, "Td", java.sql.Date.valueOf("2019-01-01"), false, false);
        partialDateDao.persist(new PartialDate(4, partial.getId(), 7, "YYYY"));
        partialDateDao.persist(new PartialDate(4, partial.getId(), 7, null));
        assertThat(preventionDao.findPreventionDTOsByDemographicId(DEMO_1)).singleElement()
                .satisfies(row -> assertThat(row.getPreventionDateInfo().isExact()).isTrue());
    }

    @Test
    void shouldRefuseDateOnlyQueries_whenTimingIsUncertain() {
        Prevention partial = createPrevention(DEMO_1, "Td", java.sql.Date.valueOf("2019-01-01"), false, false);
        markImported(partial);
        partialDateDao.persist(new PartialDate(4, partial.getId(), 7, "YYYY"));
        assertThatThrownBy(() -> preventionDao.findUniqueByDemographicId(DEMO_1))
                .isInstanceOf(PreventionDatePrecisionException.class);
        assertThatThrownBy(() -> preventionDao.findByTypeAndDate("Td", java.sql.Date.valueOf("2019-05-01"),
                java.sql.Date.valueOf("2019-06-01"))).isInstanceOf(PreventionDatePrecisionException.class);
    }

    @Nested
    @DisplayName("findByDemographicId")
    class FindByDemographicId {

        @Test
        @Tag("query")
        @DisplayName("should return all preventions for demographic")
        void shouldReturnAllPreventions_forDemographic() {
            createSimplePrevention(DEMO_1, false);
            createSimplePrevention(DEMO_1, true);
            createSimplePrevention(DEMO_2, false);

            List<Prevention> results = preventionDao.findByDemographicId(DEMO_1);
            assertThat(results).hasSize(2);
            assertThat(results).allMatch(p -> p.getDemographicId() == DEMO_1);
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty for demographic with no preventions")
        void shouldReturnEmpty_forDemographicWithNoPreventions() {
            List<Prevention> results = preventionDao.findByDemographicId(99999);
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("findNotDeletedByDemographicId")
    class FindNotDeleted {

        @BeforeEach
        void setUp() {
            createSimplePrevention(DEMO_1, false);
            createSimplePrevention(DEMO_1, false);
            createSimplePrevention(DEMO_1, true);
            createSimplePrevention(DEMO_2, false);
        }

        @Test
        @Tag("filter")
        @DisplayName("should return only non-deleted preventions for demographic")
        void shouldReturnOnlyNonDeleted_forDemographic() {
            List<Prevention> results = preventionDao.findNotDeletedByDemographicId(DEMO_1);
            assertThat(results).hasSize(2);
            assertThat(results).allMatch(p -> !p.isDeleted());
        }

        @Test
        @Tag("filter")
        @DisplayName("should not include preventions from other demographics")
        void shouldNotIncludePreventions_fromOtherDemographics() {
            List<Prevention> results = preventionDao.findNotDeletedByDemographicId(DEMO_1);
            assertThat(results).allMatch(p -> p.getDemographicId() == DEMO_1);
        }
    }

    @Nested
    @DisplayName("findByTypeAndDate (type + date range filter)")
    class FindByTypeAndDate {

        @Test
        @Tag("query")
        @DisplayName("should find preventions matching type within date range")
        void shouldFindPreventions_matchingTypeWithinDateRange() throws ParseException {
            Date startDate = dfm.parse("20081012");
            Date endDate = dfm.parse("20121001");
            String type = "delta";

            // In range, correct type, not deleted, not refused
            Prevention inRange = createPrevention(DEMO_1, type, dfm.parse("20100915"), false, false);
            // Out of range (too late)
            createPrevention(DEMO_1, type, dfm.parse("20130101"), false, false);
            // Wrong type
            createPrevention(DEMO_1, "omega", dfm.parse("20091025"), false, false);
            // Deleted
            createPrevention(DEMO_1, type, dfm.parse("20091025"), true, false);

            List<Prevention> results = preventionDao.findByTypeAndDate(type, startDate, endDate);
            assertThat(results)
                .extracting(Prevention::getId)
                .contains(inRange.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when no preventions match criteria")
        void shouldReturnEmpty_whenNoCriteriaMatch() throws ParseException {
            Date startDate = dfm.parse("20200101");
            Date endDate = dfm.parse("20200601");

            createPrevention(DEMO_1, "flu", dfm.parse("20190101"), false, false);

            List<Prevention> results = preventionDao.findByTypeAndDate("flu", startDate, endDate);
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("findActiveByDemoId")
    class FindActiveByDemoId {

        @Test
        @Tag("query")
        @DisplayName("should return active preventions for demographic")
        void shouldReturnActivePreventions_forDemographic() {
            createSimplePrevention(DEMO_1, false);
            createSimplePrevention(DEMO_1, true);

            List<Prevention> results = preventionDao.findActiveByDemoId(DEMO_1);
            assertThat(results).hasSize(1);
            assertThat(results).allMatch(p -> !p.isDeleted());
            assertThat(results).allMatch(p -> p.getDemographicId() == DEMO_1);
        }

        @Test
        @Tag("query")
        @DisplayName("should return non-null result for demographic with no records")
        void shouldReturnNonNullResult_forDemographicWithNoRecords() {
            List<Prevention> results = preventionDao.findActiveByDemoId(99999);
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByUpdateDate (date-based queries)")
    class FindByUpdateDate {

        @Test
        @Tag("query")
        @DisplayName("should find preventions updated after yesterday")
        void shouldFindPreventions_updatedAfterYesterday() {
            createSimplePrevention(DEMO_1, false);
            Calendar cal = new GregorianCalendar();
            cal.add(Calendar.DAY_OF_YEAR, -1);
            List<Prevention> results = preventionDao.findByUpdateDate(cal.getTime(), 99);
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getDemographicId()).isEqualTo(DEMO_1);
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty for future update date")
        void shouldReturnEmpty_forFutureUpdateDate() {
            createSimplePrevention(DEMO_1, false);
            Calendar cal = new GregorianCalendar();
            cal.add(Calendar.DAY_OF_YEAR, 1);
            List<Prevention> results = preventionDao.findByUpdateDate(cal.getTime(), 99);
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("findNonDeletedIdsByDemographic")
    class FindNonDeletedIds {

        @Test
        @Tag("query")
        @DisplayName("should return IDs of non-deleted preventions only")
        void shouldReturnIds_ofNonDeletedPreventionsOnly() {
            Prevention active = createSimplePrevention(DEMO_1, false);
            Prevention deleted = createSimplePrevention(DEMO_1, true);

            List<Integer> ids = preventionDao.findNonDeletedIdsByDemographic(DEMO_1);
            assertThat(ids).contains(active.getId());
            assertThat(ids).doesNotContain(deleted.getId());
        }
    }
}
