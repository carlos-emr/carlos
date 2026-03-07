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
import io.github.carlos_emr.carlos.commn.model.ConsultationResponse;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.consultations.ConsultationResponseSearchFilter;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link ConsultResponseDao} covering persist,
 * consultation response count with filters, and search operations.
 *
 * <p>Migrated from legacy {@code ConsultResponseDaoTest} (JUnit 4 / DaoTestFixtures)
 * with expanded coverage and BDD-style naming.</p>
 *
 * @since 2026-03-07
 * @see ConsultResponseDao
 */
@DisplayName("ConsultResponseDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("consultation")
@Transactional
public class ConsultResponseDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ConsultResponseDao consultDao;

    @Autowired
    private ProfessionalSpecialistDao specialistDao;

    @Autowired
    private DemographicDao demographicDao;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist consultation response with generated ID")
        void shouldPersistConsultationResponse_whenValidDataProvided() throws Exception {
            ConsultationResponse entity = new ConsultationResponse();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            consultDao.persist(entity);

            assertThat(entity.getId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("getConsultationCount")
    class GetConsultationCount {

        private Demographic d1, d2;
        private Integer demoNo1, demoNo2;
        private Integer referringDocId;
        private Date date1, date2;
        private static final String STATUS_1 = "1";
        private static final String STATUS_2 = "2";
        private static final String TEAM_1 = "tttt1";
        private static final String TEAM_2 = "tttt2";
        private static final String URGENCY_1 = "u1";
        private static final String URGENCY_2 = "u2";

        @BeforeEach
        void setUp() throws Exception {
            d1 = new Demographic();
            EntityDataGenerator.generateTestDataForModelClass(d1);
            d1.setDemographicNo(null);
            demographicDao.save(d1);

            d2 = new Demographic();
            EntityDataGenerator.generateTestDataForModelClass(d2);
            d2.setDemographicNo(null);
            demographicDao.save(d2);

            demoNo1 = d1.getDemographicNo();
            demoNo2 = d2.getDemographicNo();

            ProfessionalSpecialist sp = new ProfessionalSpecialist();
            EntityDataGenerator.generateTestDataForModelClass(sp);
            specialistDao.persist(sp);
            referringDocId = sp.getId();

            String[] format = new String[]{"yyyy-MM-dd"};
            date1 = DateUtils.parseDate("2015-03-05", format);
            date2 = DateUtils.parseDate("2015-03-26", format);

            // cr1: demo1, appt=date1, ref=date1, resp=date1, status1, team1, urgency1
            consultDao.persist(createResponse(demoNo1, date1, date1, date1, STATUS_1, TEAM_1, URGENCY_1));
            // cr2: demo2, appt=date2, ref=date2, resp=date2, status2, team2, urgency2
            consultDao.persist(createResponse(demoNo2, date2, date2, date2, STATUS_2, TEAM_2, URGENCY_2));
            // cr3: demo1, appt=date2, ref=date1, resp=date1, status1, team1, urgency1
            consultDao.persist(createResponse(demoNo1, date2, date1, date1, STATUS_1, TEAM_1, URGENCY_1));
            // cr4: demo2, appt=date1, ref=date2, resp=date1, status2, team2, urgency2
            consultDao.persist(createResponse(demoNo2, date1, date2, date1, STATUS_2, TEAM_2, URGENCY_2));
            // cr5: demo1, appt=date1, ref=date1, resp=date1, status1, team1, urgency2
            consultDao.persist(createResponse(demoNo1, date1, date1, date1, STATUS_1, TEAM_1, URGENCY_2));
            // cr6: demo2, appt=date2, ref=date1, resp=date1, status1, team2, urgency2
            consultDao.persist(createResponse(demoNo2, date2, date1, date1, STATUS_1, TEAM_2, URGENCY_2));
        }

        @Test
        @Tag("query")
        @DisplayName("should count responses filtered by demographic, status, team, urgency")
        void shouldCountResponses_filteredByDemographicStatusTeamUrgency() {
            ConsultationResponseSearchFilter filter = new ConsultationResponseSearchFilter();
            filter.setDemographicNo(demoNo1);
            filter.setStatus(Integer.valueOf(STATUS_1));
            filter.setTeam(TEAM_1);
            filter.setUrgency(URGENCY_1);

            int count = consultDao.getConsultationCount(filter);

            assertThat(count).isEqualTo(2);
        }

        @Test
        @Tag("query")
        @DisplayName("should count responses filtered by appointment date range")
        void shouldCountResponses_filteredByAppointmentDateRange() {
            ConsultationResponseSearchFilter filter = new ConsultationResponseSearchFilter();
            filter.setAppointmentStartDate(DateUtils.setDays(date2, 20));
            filter.setAppointmentEndDate(DateUtils.setDays(date2, 27));

            int count = consultDao.getConsultationCount(filter);

            assertThat(count).isEqualTo(3);
        }

        @Test
        @Tag("query")
        @DisplayName("should count responses filtered by referral date range")
        void shouldCountResponses_filteredByReferralDateRange() {
            ConsultationResponseSearchFilter filter = new ConsultationResponseSearchFilter();
            filter.setReferralStartDate(DateUtils.setDays(date1, 1));
            filter.setReferralEndDate(DateUtils.setDays(date1, 10));

            int count = consultDao.getConsultationCount(filter);

            assertThat(count).isEqualTo(4);
        }

        @Test
        @Tag("query")
        @DisplayName("should count responses filtered by response date range")
        void shouldCountResponses_filteredByResponseDateRange() {
            ConsultationResponseSearchFilter filter = new ConsultationResponseSearchFilter();
            filter.setResponseStartDate(date1);
            filter.setResponseEndDate(DateUtils.setDays(date1, 25));

            int count = consultDao.getConsultationCount(filter);

            assertThat(count).isEqualTo(5);
        }

        private ConsultationResponse createResponse(Integer demoNo, Date apptDate, Date refDate,
                                                     Date respDate, String status, String sendTo, String urgency) throws Exception {
            ConsultationResponse cr = new ConsultationResponse();
            EntityDataGenerator.generateTestDataForModelClass(cr);
            cr.setReferringDocId(referringDocId);
            cr.setDemographicNo(demoNo);
            cr.setAppointmentDate(apptDate);
            cr.setReferralDate(refDate);
            cr.setResponseDate(respDate);
            cr.setStatus(status);
            cr.setSendTo(sendTo);
            cr.setUrgency(urgency);
            return cr;
        }
    }

    @Nested
    @DisplayName("search")
    class SearchOperations {

        @Test
        @Tag("search")
        @DisplayName("should return search results filtered by demographic, status, team, urgency")
        void shouldReturnSearchResults_filteredByDemographicStatusTeamUrgency() throws Exception {
            Demographic d1 = new Demographic();
            EntityDataGenerator.generateTestDataForModelClass(d1);
            d1.setDemographicNo(null);
            demographicDao.save(d1);

            ProfessionalSpecialist sp = new ProfessionalSpecialist();
            EntityDataGenerator.generateTestDataForModelClass(sp);
            specialistDao.persist(sp);

            String[] format = new String[]{"yyyy-MM-dd"};
            Date date1 = DateUtils.parseDate("2015-03-05", format);
            Date date2 = DateUtils.parseDate("2015-03-26", format);

            ConsultationResponse cr1 = createSearchResponse(sp.getId(), d1.getDemographicNo(), date1, date1, date1, "1", "tttt1", "u1");
            consultDao.persist(cr1);

            ConsultationResponse cr2 = createSearchResponse(sp.getId(), d1.getDemographicNo(), date2, date2, date1, "1", "tttt1", "u1");
            consultDao.persist(cr2);

            ConsultationResponseSearchFilter filter = new ConsultationResponseSearchFilter();
            filter.setNumToReturn(99);
            filter.setDemographicNo(d1.getDemographicNo());
            filter.setStatus(1);
            filter.setTeam("tttt1");
            filter.setUrgency("u1");

            List<Object[]> results = consultDao.search(filter);

            assertThat(results).hasSize(2);
            for (Object[] result : results) {
                assertThat(result[0]).isInstanceOf(ConsultationResponse.class);
                ConsultationResponse cr = (ConsultationResponse) result[0];
                assertThat(cr.getDemographicNo()).isEqualTo(d1.getDemographicNo());
                assertThat(result[1]).isInstanceOf(ProfessionalSpecialist.class);
                assertThat(result[2]).isInstanceOf(Demographic.class);
                Demographic demo = (Demographic) result[2];
                assertThat(demo.getDemographicNo()).isEqualTo(d1.getDemographicNo());
            }
        }

        @Test
        @Tag("search")
        @DisplayName("should return search results filtered by appointment date range")
        void shouldReturnSearchResults_filteredByAppointmentDateRange() throws Exception {
            Demographic d1 = new Demographic();
            EntityDataGenerator.generateTestDataForModelClass(d1);
            d1.setDemographicNo(null);
            demographicDao.save(d1);

            ProfessionalSpecialist sp = new ProfessionalSpecialist();
            EntityDataGenerator.generateTestDataForModelClass(sp);
            specialistDao.persist(sp);

            String[] format = new String[]{"yyyy-MM-dd"};
            Date date1 = DateUtils.parseDate("2015-03-05", format);
            Date date2 = DateUtils.parseDate("2015-03-26", format);

            consultDao.persist(createSearchResponse(sp.getId(), d1.getDemographicNo(), date1, date1, date1, "1", "tttt1", "u1"));
            consultDao.persist(createSearchResponse(sp.getId(), d1.getDemographicNo(), date2, date2, date1, "1", "tttt1", "u1"));

            ConsultationResponseSearchFilter filter = new ConsultationResponseSearchFilter();
            filter.setNumToReturn(99);
            filter.setAppointmentStartDate(DateUtils.setDays(date2, 20));
            filter.setAppointmentEndDate(DateUtils.setDays(date2, 27));

            List<Object[]> results = consultDao.search(filter);

            assertThat(results).hasSize(1);
        }

        @Test
        @Tag("search")
        @DisplayName("should return search results filtered by referral date range")
        void shouldReturnSearchResults_filteredByReferralDateRange() throws Exception {
            Demographic d1 = new Demographic();
            EntityDataGenerator.generateTestDataForModelClass(d1);
            d1.setDemographicNo(null);
            demographicDao.save(d1);

            ProfessionalSpecialist sp = new ProfessionalSpecialist();
            EntityDataGenerator.generateTestDataForModelClass(sp);
            specialistDao.persist(sp);

            String[] format = new String[]{"yyyy-MM-dd"};
            Date date1 = DateUtils.parseDate("2015-03-05", format);
            Date date2 = DateUtils.parseDate("2015-03-26", format);

            consultDao.persist(createSearchResponse(sp.getId(), d1.getDemographicNo(), date1, date1, date1, "1", "tttt1", "u1"));
            consultDao.persist(createSearchResponse(sp.getId(), d1.getDemographicNo(), date2, date2, date1, "1", "tttt1", "u1"));

            ConsultationResponseSearchFilter filter = new ConsultationResponseSearchFilter();
            filter.setNumToReturn(99);
            filter.setReferralStartDate(DateUtils.setDays(date1, 1));
            filter.setReferralEndDate(DateUtils.setDays(date1, 10));

            List<Object[]> results = consultDao.search(filter);

            assertThat(results).hasSize(1);
        }

        @Test
        @Tag("search")
        @DisplayName("should return search results filtered by response date range")
        void shouldReturnSearchResults_filteredByResponseDateRange() throws Exception {
            Demographic d1 = new Demographic();
            EntityDataGenerator.generateTestDataForModelClass(d1);
            d1.setDemographicNo(null);
            demographicDao.save(d1);

            ProfessionalSpecialist sp = new ProfessionalSpecialist();
            EntityDataGenerator.generateTestDataForModelClass(sp);
            specialistDao.persist(sp);

            String[] format = new String[]{"yyyy-MM-dd"};
            Date date1 = DateUtils.parseDate("2015-03-05", format);
            Date date2 = DateUtils.parseDate("2015-03-26", format);

            consultDao.persist(createSearchResponse(sp.getId(), d1.getDemographicNo(), date1, date1, date1, "1", "tttt1", "u1"));
            consultDao.persist(createSearchResponse(sp.getId(), d1.getDemographicNo(), date2, date2, date2, "2", "tttt2", "u2"));

            ConsultationResponseSearchFilter filter = new ConsultationResponseSearchFilter();
            filter.setNumToReturn(99);
            filter.setResponseStartDate(DateUtils.setDays(date2, 25));
            filter.setResponseEndDate(date2);

            List<Object[]> results = consultDao.search(filter);

            assertThat(results).hasSize(1);
        }

        private ConsultationResponse createSearchResponse(Integer refDocId, Integer demoNo,
                                                           Date apptDate, Date refDate, Date respDate,
                                                           String status, String sendTo, String urgency) throws Exception {
            ConsultationResponse cr = new ConsultationResponse();
            EntityDataGenerator.generateTestDataForModelClass(cr);
            cr.setReferringDocId(refDocId);
            cr.setDemographicNo(demoNo);
            cr.setAppointmentDate(apptDate);
            cr.setReferralDate(refDate);
            cr.setResponseDate(respDate);
            cr.setStatus(status);
            cr.setSendTo(sendTo);
            cr.setUrgency(urgency);
            return cr;
        }
    }
}
