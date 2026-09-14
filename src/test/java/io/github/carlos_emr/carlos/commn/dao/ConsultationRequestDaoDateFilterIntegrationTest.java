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

import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Consultation request date filter integration tests")
@Tag("integration")
@Tag("dao")
@Tag("consultation")
@Transactional
class ConsultationRequestDaoDateFilterIntegrationTest extends CarlosTestBase {

    private static final int DEMOGRAPHIC_NO = 60001;
    private static final String[] DATE_FORMAT = new String[]{"yyyy-MM-dd"};

    @Autowired
    private ConsultRequestDao consultRequestDao;

    @Autowired
    private ConsultationRequestDao consultationRequestDao;

    @Test
    @Tag("query")
    @DisplayName("should filter consultation list by referral date range")
    void shouldFilterConsultationList_whenReferralDateRangeProvided() throws Exception {
        String team = "referral-date-team-" + System.nanoTime();
        Date outsideDate = DateUtils.parseDate("2015-03-05", DATE_FORMAT);
        Date insideDate = DateUtils.parseDate("2015-03-26", DATE_FORMAT);
        ConsultationRequest requestOutsideDateRange = createConsultRequest(team, insideDate, outsideDate);
        ConsultationRequest requestInsideDateRange = createConsultRequest(team, outsideDate, insideDate);

        List<ConsultationRequest> results = consultationRequestDao.getConsults(
                team, true, DateUtils.parseDate("2015-03-20", DATE_FORMAT),
                DateUtils.parseDate("2015-03-31", DATE_FORMAT), null, null, null, 0, 99);

        assertThat(results).extracting(ConsultationRequest::getId)
                .contains(requestInsideDateRange.getId())
                .doesNotContain(requestOutsideDateRange.getId());
    }

    @Test
    @Tag("query")
    @DisplayName("should filter consultation list by appointment date range")
    void shouldFilterConsultationList_whenAppointmentDateRangeProvided() throws Exception {
        String team = "appointment-date-team-" + System.nanoTime();
        Date outsideDate = DateUtils.parseDate("2015-03-05", DATE_FORMAT);
        Date insideDate = DateUtils.parseDate("2015-03-26", DATE_FORMAT);
        ConsultationRequest requestOutsideDateRange = createConsultRequest(team, outsideDate, insideDate);
        ConsultationRequest requestInsideDateRange = createConsultRequest(team, insideDate, outsideDate);

        List<ConsultationRequest> results = consultationRequestDao.getConsults(
                team, true, DateUtils.parseDate("2015-03-20", DATE_FORMAT),
                DateUtils.parseDate("2015-03-31", DATE_FORMAT), null, null, "1", 0, 99);

        assertThat(results).extracting(ConsultationRequest::getId)
                .contains(requestInsideDateRange.getId())
                .doesNotContain(requestOutsideDateRange.getId());
    }

    private ConsultationRequest createConsultRequest(String team, Date appointmentDate, Date referralDate) {
        ConsultationRequest req = new ConsultationRequest();
        req.setDemographicId(DEMOGRAPHIC_NO);
        req.setStatus("1");
        req.setReferralDate(referralDate);
        req.setAppointmentDate(appointmentDate);
        req.setProviderNo("999998");
        req.setReasonForReferral("Test referral");
        req.setSendTo(team);
        req.setLastUpdateDate(new Date());
        consultRequestDao.persist(req);
        return req;
    }
}
