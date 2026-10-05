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
package io.github.carlos_emr.carlos.managers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.ConsentDao;
import io.github.carlos_emr.carlos.commn.dao.ConsentTypeDao;
import io.github.carlos_emr.carlos.commn.model.Consent;
import io.github.carlos_emr.carlos.commn.model.ConsentType;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Runs a chart save's consent change against the real consent DAOs and EntityManager.
 *
 * <p>The unit tests mock the DAO, so they cannot show that an implied opt-out switched to Opt-in
 * with "Patient confirmed consent directly" ticked ends, in one save, as one live record that is
 * an explicit opt-in: the flip has to reach the row before the confirmation reads it back.</p>
 *
 * @since 2026-10-05
 */
@DisplayName("PatientConsentManager chart save with the real consent DAOs")
@Tag("integration")
@Tag("manager")
class PatientConsentManagerChartSaveIntegrationTest extends CarlosTestBase {

    private static final int PATIENT = 521;

    @Autowired
    private ConsentDao consentDao;

    @Autowired
    private ConsentTypeDao consentTypeDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private PatientConsentManagerImpl manager;
    private LoggedInInfo loggedInInfo;
    private ConsentType emailType;

    @BeforeEach
    void setUp() {
        emailType = new ConsentType();
        emailType.setName("Email");
        emailType.setType("chart_save_test_email_consent");
        emailType.setActive(true);
        entityManager.persist(emailType);

        loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(), eq("_demographic"), eq(SecurityInfoManager.WRITE), anyInt()))
                .thenReturn(true);

        manager = new PatientConsentManagerImpl();
        ReflectionTestUtils.setField(manager, "consentDao", consentDao);
        ReflectionTestUtils.setField(manager, "consentTypeDao", consentTypeDao);
        ReflectionTestUtils.setField(manager, "securityInfoManager", securityInfoManager);
    }

    @Test
    @DisplayName("should turn an implied opt-out into one explicit opt-in when switched to Opt-in with the box ticked")
    void shouldRecordExplicitOptIn_whenImpliedOptOutIsSwitchedWithBoxTicked() {
        Consent impliedOptOut = new Consent();
        impliedOptOut.setDemographicNo(PATIENT);
        impliedOptOut.setConsentType(emailType);
        impliedOptOut.setExplicit(false);
        impliedOptOut.setOptout(true);
        impliedOptOut.setDeleted(false);
        impliedOptOut.setEditDate(new Date(1_000L));
        entityManager.persist(impliedOptOut);
        entityManager.flush();
        entityManager.clear();

        ChartConsentOutcome outcome;
        try (MockedStatic<LogAction> ignored = mockStatic(LogAction.class)) {
            // The page showed this implied opt-out; staff pick Opt-in and tick the box.
            outcome = manager.saveChartConsent(loggedInInfo, PATIENT, emailType.getId(), new ChartConsentRequest(
                    ChartConsentRequest.Choice.OPT_IN, true, true, impliedOptOut.getId(), Boolean.TRUE));
        }
        entityManager.flush();
        entityManager.clear();

        assertThat(outcome).isEqualTo(ChartConsentOutcome.APPLIED);
        List<Consent> live = consentDao.findLiveByDemographicAndConsentTypeId(PATIENT, emailType.getId());
        assertThat(live).hasSize(1);
        Consent decided = live.get(0);
        assertThat(decided.getId()).as("the same record is edited, no new one is added").isEqualTo(impliedOptOut.getId());
        assertThat(decided.isOptout()).isFalse();
        assertThat(decided.isExplicit()).isTrue();
        assertThat(decided.getLastEnteredBy()).isEqualTo("999998");
    }
}
