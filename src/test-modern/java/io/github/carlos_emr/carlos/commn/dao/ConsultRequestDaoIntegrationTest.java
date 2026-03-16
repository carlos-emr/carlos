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
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.commn.model.ConsultationServices;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.consultations.ConsultationRequestSearchFilter;
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
 * Integration tests for {@link ConsultRequestDao} covering persist,
 * consultation count with filters, and search operations.
 *
 * <p>Migrated from legacy {@code ConsultRequestDaoTest} (JUnit 4 / DaoTestFixtures)
 * with expanded coverage and BDD-style naming.</p>
 *
 * @since 2026-03-07
 * @see ConsultRequestDao
 */
@DisplayName("ConsultRequestDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("consultation")
@Transactional
public class ConsultRequestDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ConsultRequestDao consultDao;

    @Autowired
    private ConsultationServiceDao serviceDao;

    @Autowired
    private DemographicDao demographicDao;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist consultation request with generated ID")
        void shouldPersistConsultationRequest_whenValidDataProvided() throws Exception {
            ConsultationRequest entity = new ConsultationRequest();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            consultDao.persist(entity);

            assertThat(entity.getId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("getConsultationCount2")
    class GetConsultationCount {

        private Demographic d1, d2;
        private Integer demoNo1, demoNo2;
        private Integer serviceId;
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

            ConsultationServices cs = new ConsultationServices();
            EntityDataGenerator.generateTestDataForModelClass(cs);
            serviceDao.persist(cs);
            serviceId = cs.getId();

            String[] format = new String[]{"yyyy-MM-dd"};
            date1 = DateUtils.parseDate("2015-03-05", format);
            date2 = DateUtils.parseDate("2015-03-26", format);

            // cr1: demo1, date1, date1, status1, team1, urgency1
            ConsultationRequest cr1 = createConsultRequest(serviceId, demoNo1, date1, date1, STATUS_1, TEAM_1, URGENCY_1);
            consultDao.persist(cr1);

            // cr2: demo2, date2, date2, status2, team2, urgency2
            ConsultationRequest cr2 = createConsultRequest(serviceId, demoNo2, date2, date2, STATUS_2, TEAM_2, URGENCY_2);
            consultDao.persist(cr2);

            // cr3: demo1, date2, date1, status1, team1, urgency1
            ConsultationRequest cr3 = createConsultRequest(serviceId, demoNo1, date2, date1, STATUS_1, TEAM_1, URGENCY_1);
            consultDao.persist(cr3);

            // cr4: demo2, date1, date1, status2, team2, urgency2
            ConsultationRequest cr4 = createConsultRequest(serviceId, demoNo2, date1, date1, STATUS_2, TEAM_2, URGENCY_2);
            consultDao.persist(cr4);

            // cr5: demo1, date2, date1, status1, team1, urgency2
            ConsultationRequest cr5 = createConsultRequest(serviceId, demoNo1, date2, date1, STATUS_1, TEAM_1, URGENCY_2);
            consultDao.persist(cr5);
        }

        @Test
        @Tag("query")
        @DisplayName("should count consultations filtered by demographic, status, team, urgency")
        void shouldCountConsultations_filteredByDemographicStatusTeamUrgency() throws Exception {
            ConsultationRequestSearchFilter filter = new ConsultationRequestSearchFilter();
            filter.setDemographicNo(demoNo1);
            filter.setStatus(Integer.valueOf(STATUS_1));
            filter.setTeam(TEAM_1);
            filter.setUrgency(URGENCY_1);

            int count = consultDao.getConsultationCount2(filter);

            assertThat(count).isEqualTo(2);
        }

        @Test
        @Tag("query")
        @DisplayName("should count consultations filtered by appointment date range")
        void shouldCountConsultations_filteredByAppointmentDateRange() throws Exception {
            ConsultationRequestSearchFilter filter = new ConsultationRequestSearchFilter();
            filter.setAppointmentStartDate(DateUtils.setDays(date2, 20));
            filter.setAppointmentEndDate(DateUtils.setDays(date2, 27));

            int count = consultDao.getConsultationCount2(filter);

            assertThat(count).isEqualTo(3);
        }

        @Test
        @Tag("query")
        @DisplayName("should count consultations filtered by referral date range")
        void shouldCountConsultations_filteredByReferralDateRange() throws Exception {
            ConsultationRequestSearchFilter filter = new ConsultationRequestSearchFilter();
            filter.setReferralStartDate(DateUtils.setDays(date1, 1));
            filter.setReferralEndDate(DateUtils.setDays(date1, 10));

            int count = consultDao.getConsultationCount2(filter);

            assertThat(count).isEqualTo(4);
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

            Demographic d2 = new Demographic();
            EntityDataGenerator.generateTestDataForModelClass(d2);
            d2.setDemographicNo(null);
            demographicDao.save(d2);

            ConsultationServices cs = new ConsultationServices();
            EntityDataGenerator.generateTestDataForModelClass(cs);
            serviceDao.persist(cs);

            String[] format = new String[]{"yyyy-MM-dd"};
            Date date1 = DateUtils.parseDate("2015-03-05", format);
            Date date2 = DateUtils.parseDate("2015-03-26", format);

            ConsultationRequest cr1 = createConsultRequest(cs.getId(), d1.getDemographicNo(), date1, date1, "1", "tttt1", "u1");
            consultDao.persist(cr1);

            ConsultationRequest cr2 = createConsultRequest(cs.getId(), d2.getDemographicNo(), date2, date2, "2", "tttt2", "u2");
            consultDao.persist(cr2);

            ConsultationRequest cr3 = createConsultRequest(cs.getId(), d1.getDemographicNo(), date2, date2, "1", "tttt1", "u1");
            consultDao.persist(cr3);

            ConsultationRequestSearchFilter filter = new ConsultationRequestSearchFilter();
            filter.setNumToReturn(99);
            filter.setDemographicNo(d1.getDemographicNo());
            filter.setStatus(1);
            filter.setTeam("tttt1");
            filter.setUrgency("u1");

            List<Object[]> results = consultDao.search(filter);

            assertThat(results).hasSize(2);
            for (Object[] result : results) {
                assertThat(result[0]).isInstanceOf(ConsultationRequest.class);
                ConsultationRequest cr = (ConsultationRequest) result[0];
                assertThat(cr.getDemographicId()).isEqualTo(d1.getDemographicNo());
                assertThat(result[2]).isInstanceOf(ConsultationServices.class);
                assertThat(result[3]).isInstanceOf(Demographic.class);
                Demographic demo = (Demographic) result[3];
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

            ConsultationServices cs = new ConsultationServices();
            EntityDataGenerator.generateTestDataForModelClass(cs);
            serviceDao.persist(cs);

            String[] format = new String[]{"yyyy-MM-dd"};
            Date date1 = DateUtils.parseDate("2015-03-05", format);
            Date date2 = DateUtils.parseDate("2015-03-26", format);

            ConsultationRequest cr1 = createConsultRequest(cs.getId(), d1.getDemographicNo(), date1, date1, "1", "tttt1", "u1");
            consultDao.persist(cr1);

            ConsultationRequest cr2 = createConsultRequest(cs.getId(), d1.getDemographicNo(), date2, date2, "1", "tttt1", "u1");
            consultDao.persist(cr2);

            ConsultationRequestSearchFilter filter = new ConsultationRequestSearchFilter();
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

            ConsultationServices cs = new ConsultationServices();
            EntityDataGenerator.generateTestDataForModelClass(cs);
            serviceDao.persist(cs);

            String[] format = new String[]{"yyyy-MM-dd"};
            Date date1 = DateUtils.parseDate("2015-03-05", format);
            Date date2 = DateUtils.parseDate("2015-03-26", format);

            ConsultationRequest cr1 = createConsultRequest(cs.getId(), d1.getDemographicNo(), date1, date1, "1", "tttt1", "u1");
            consultDao.persist(cr1);

            ConsultationRequest cr2 = createConsultRequest(cs.getId(), d1.getDemographicNo(), date2, date2, "1", "tttt1", "u1");
            consultDao.persist(cr2);

            ConsultationRequestSearchFilter filter = new ConsultationRequestSearchFilter();
            filter.setNumToReturn(99);
            filter.setReferralStartDate(DateUtils.setDays(date1, 1));
            filter.setReferralEndDate(DateUtils.setDays(date1, 10));

            List<Object[]> results = consultDao.search(filter);

            assertThat(results).hasSize(1);
        }
    }

    private ConsultationRequest createConsultRequest(Integer serviceId, Integer demoNo,
                                                      Date appointmentDate, Date referralDate,
                                                      String status, String sendTo, String urgency) throws Exception {
        ConsultationRequest cr = new ConsultationRequest();
        EntityDataGenerator.generateTestDataForModelClass(cr);
        cr.setServiceId(serviceId);
        cr.setDemographicId(demoNo);
        cr.setAppointmentDate(appointmentDate);
        cr.setReferralDate(referralDate);
        cr.setStatus(status);
        cr.setSendTo(sendTo);
        cr.setUrgency(urgency);
        return cr;
    }
}
