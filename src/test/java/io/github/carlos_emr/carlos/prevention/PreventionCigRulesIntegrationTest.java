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
package io.github.carlos_emr.carlos.prevention;

import java.net.URL;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.kie.api.KieBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionExtDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.PartialDate;
import io.github.carlos_emr.carlos.commn.model.PreventionExt;
import io.github.carlos_emr.carlos.drools.DroolsCompilationException;
import io.github.carlos_emr.carlos.drools.DroolsHelper;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * End-to-end persistence and Drools integration tests for the Canadian Immunization Guide rules (#5048).
 *
 * <p>Persists real {@link io.github.carlos_emr.carlos.commn.model.Prevention}, {@link PreventionExt} and
 * {@link PartialDate} rows into H2, loads the patient's rule fact through
 * {@link PreventionData#getPrevention(LoggedInInfo, Integer)} (exercising
 * {@link PreventionDao#findActiveByDemoId(Integer)} ordering, SQL date hydration, and partial-date lookup),
 * and evaluates the shipped {@code prevention.drl} through {@link PreventionDSImpl#getMessages(Prevention)}.</p>
 *
 * @since 2026-11-27
 */
@Tag("integration")
@Tag("prevention")
@Tag("drools")
@Isolated("PreventionData and PreventionDSImpl retain static references")
@Transactional
@DisplayName("CIG prevention rules end-to-end persistence and Drools integration")
class PreventionCigRulesIntegrationTest extends CarlosTestBase {

    private static final int PATIENT = 89005048;
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private static final String NEEDS_SECOND_ROTA = "Needs Second Rota vaccine";
    private static final String NEEDS_THIRD_ROTA = "Needs Third Rota vaccine";
    private static final String NEEDS_ADULT_TDAP = "Needs adult Tdap (one dose)";
    private static final String NEEDS_TD = "Needs Td";
    private static final String NEEDS_REPEAT_TD = "Needs repeat Td";
    private static final String REVIEW = "review history before applying due or dose rules";

    private static KieBase kieBase;

    @Autowired
    private PreventionDao preventionDao;
    @Autowired
    private PreventionExtDao preventionExtDao;
    @Autowired
    private PartialDateDao partialDateDao;
    @Autowired
    private DemographicManager demographicManager;
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private final Map<String, Object> originalBeans = new HashMap<>();
    private final LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
    private KieBase previousRuleBase;

    @BeforeAll
    static void loadPreventionDrl() throws DroolsCompilationException {
        URL url = PreventionCigRulesIntegrationTest.class.getResource("/oscar/prevention/prevention.drl");
        assertThat(url).as("prevention.drl must be on the classpath").isNotNull();
        kieBase = DroolsHelper.loadFromUrl(url);
    }

    @BeforeEach
    void setUpBeans() {
        replaceBean("preventionDao", preventionDao);
        replaceBean("preventionExtDao", preventionExtDao);
        replaceBean("partialDateDao", partialDateDao);
        previousRuleBase = PreventionDSImpl.kieBase;
        PreventionDSImpl.kieBase = kieBase;
    }

    @AfterEach
    void restoreBeans() {
        PreventionDSImpl.kieBase = previousRuleBase;
        originalBeans.forEach((name, bean) -> ReflectionTestUtils.setField(PreventionData.class, name, bean));
        reset(demographicManager);
    }

    private void replaceBean(String name, Object bean) {
        originalBeans.put(name, ReflectionTestUtils.getField(PreventionData.class, name));
        ReflectionTestUtils.setField(PreventionData.class, name, bean);
    }

    private static Date atStartOfDay(LocalDate date) {
        return Date.from(date.atStartOfDay(ZONE).toInstant());
    }

    private void stubPatientBorn(LocalDate dob) {
        Demographic patient = new Demographic();
        patient.setDemographicNo(PATIENT);
        patient.setSex("F");
        patient.setYearOfBirth(String.format("%04d", dob.getYear()));
        patient.setMonthOfBirth(String.format("%02d", dob.getMonthValue()));
        patient.setDateOfBirth(String.format("%02d", dob.getDayOfMonth()));
        when(demographicManager.getDemographic(loggedInInfo, PATIENT)).thenReturn(patient);
    }

    private io.github.carlos_emr.carlos.commn.model.Prevention persistRecord(
            String type, LocalDate date, boolean refused, boolean ineligible, LocalDate nextDate) {
        io.github.carlos_emr.carlos.commn.model.Prevention entity =
                new io.github.carlos_emr.carlos.commn.model.Prevention();
        entity.setDemographicId(PATIENT);
        entity.setPreventionType(type);
        entity.setPreventionDate(atStartOfDay(date));
        entity.setProviderNo("999998");
        entity.setCreatorProviderNo("999998");
        entity.setDeleted(false);
        entity.setNever(false);
        if (refused) {
            entity.setRefused(true);
        } else if (ineligible) {
            entity.setIneligible(true);
        }
        if (nextDate != null) {
            entity.setNextDate(atStartOfDay(nextDate));
        }
        preventionDao.persist(entity);
        return entity;
    }

    private Prevention evaluatePersistedHistory() throws Exception {
        entityManager.flush();
        entityManager.clear();
        Prevention loaded = PreventionData.getPrevention(loggedInInfo, PATIENT);
        return new PreventionDSImpl().getMessages(loaded);
    }

    private static long count(Prevention evaluated, String warning) {
        return evaluated.getWarnings().stream().filter(warning::equals).count();
    }

    @Test
    void shouldRemindSecondRotaDose_whenPersistedPriorRefusalWithNextDatePrecedesGivenDoseOutOfInsertOrder()
            throws Exception {
        LocalDate today = LocalDate.now(ZONE);
        stubPatientBorn(today.minusDays(150));

        // Persist the later given dose FIRST and the earlier refusal SECOND so findActiveByDemoId must order by date.
        persistRecord("Rota-5", today.minusDays(45), false, false, null);
        persistRecord("Rota", today.minusDays(90), true, false, today.plusDays(7));

        Prevention evaluated = evaluatePersistedHistory();

        assertThat(count(evaluated, NEEDS_SECOND_ROTA)).isEqualTo(1);
        assertThat(count(evaluated, NEEDS_THIRD_ROTA)).isZero();
    }

    @Test
    void shouldRemindThirdRotaDose_whenPersistedMixedSeriesHasRota1AndRota5() throws Exception {
        LocalDate today = LocalDate.now(ZONE);
        stubPatientBorn(today.minusDays(150));

        persistRecord("Rota-1", today.minusDays(65), false, false, today.minusDays(30));
        persistRecord("Rota-5", today.minusDays(30), false, false, null);

        Prevention evaluated = evaluatePersistedHistory();

        assertThat(count(evaluated, NEEDS_THIRD_ROTA)).isEqualTo(1);
        assertThat(count(evaluated, NEEDS_SECOND_ROTA)).isZero();
    }

    @Test
    void shouldSwitchFromAdultTdapToRepeatTd_whenPersistedAdultTdapIsMarkedIneligible() throws Exception {
        LocalDate today = LocalDate.now(ZONE);
        stubPatientBorn(today.minusYears(30));

        // Teen Tdap at 15 and Td 12 years ago: adult Tdap takes precedence while due.
        persistRecord("Tdap", today.minusYears(15), false, false, null);
        persistRecord("Td", today.minusYears(12), false, false, null);

        Prevention beforeIneligible = evaluatePersistedHistory();
        assertThat(count(beforeIneligible, NEEDS_ADULT_TDAP)).isEqualTo(1);
        assertThat(count(beforeIneligible, NEEDS_REPEAT_TD)).isZero();

        // Clinician marks adult Tdap ineligible today: adult Tdap clears and overdue repeat Td shows.
        persistRecord("Tdap", today, false, true, null);

        Prevention afterIneligible = evaluatePersistedHistory();
        assertThat(count(afterIneligible, NEEDS_ADULT_TDAP)).isZero();
        assertThat(count(afterIneligible, NEEDS_REPEAT_TD)).isEqualTo(1);
    }

    @Test
    void shouldRequireReviewWithoutDueWarnings_whenPersistedImportedRotaHasPartialMonthDate() throws Exception {
        LocalDate today = LocalDate.now(ZONE);
        stubPatientBorn(today.minusDays(150));

        persistRecord("Rota-1", today.minusDays(65), false, false, null);
        io.github.carlos_emr.carlos.commn.model.Prevention imported =
                persistRecord("Rota-5", today.minusDays(30).withDayOfMonth(1), false, false, null);
        entityManager.flush();

        PreventionExt provenance = new PreventionExt();
        provenance.setPreventionId(imported.getId());
        provenance.setKeyval("cdsDatePrecision");
        provenance.setVal("MONTH");
        preventionExtDao.persist(provenance);
        partialDateDao.setPartialDate(PartialDate.PREVENTION, imported.getId(),
                PartialDate.PREVENTION_PREVENTIONDATE, PartialDate.YEARMONTH);

        Prevention evaluated = evaluatePersistedHistory();

        assertThat(evaluated.isDateReviewRequired()).isTrue();
        assertThat(count(evaluated, NEEDS_SECOND_ROTA)).isZero();
        assertThat(count(evaluated, NEEDS_THIRD_ROTA)).isZero();
        assertThat(evaluated.getWarningMsgs()).containsKey("Rota-5");
        assertThat(evaluated.getWarnings()).allMatch(warning -> warning.contains(REVIEW));
    }

    @Test
    void shouldSwitchFromAdultTdapToRepeatTd_whenPersistedAdultTdapIsRefused() throws Exception {
        LocalDate today = LocalDate.now(ZONE);
        stubPatientBorn(today.minusYears(70));

        persistRecord("Td", today.minusYears(12), false, false, null);

        Prevention beforeRefusal = evaluatePersistedHistory();
        assertThat(count(beforeRefusal, NEEDS_ADULT_TDAP)).isEqualTo(1);
        assertThat(count(beforeRefusal, NEEDS_REPEAT_TD)).isZero();

        // Refused adult Tdap (status 1) clears the adult Tdap prompt and lets the overdue repeat Td show.
        persistRecord("Tdap", today, true, false, null);

        Prevention afterRefusal = evaluatePersistedHistory();
        assertThat(count(afterRefusal, NEEDS_ADULT_TDAP)).isZero();
        assertThat(count(afterRefusal, NEEDS_REPEAT_TD)).isEqualTo(1);
    }

    @Test
    void shouldAskForFirstTd_whenPersistedExternallyCompletedAdultTdapIsTenYearsOld() throws Exception {
        LocalDate today = LocalDate.now(ZONE);
        stubPatientBorn(today.minusYears(70));

        io.github.carlos_emr.carlos.commn.model.Prevention externalTdap =
                new io.github.carlos_emr.carlos.commn.model.Prevention();
        externalTdap.setDemographicId(PATIENT);
        externalTdap.setPreventionType("Tdap");
        externalTdap.setPreventionDate(atStartOfDay(today.minusMonths(120)));
        externalTdap.setProviderNo("999998");
        externalTdap.setCreatorProviderNo("999998");
        externalTdap.setDeleted(false);
        externalTdap.setNever(false);
        externalTdap.setCompletedExternally(true);
        preventionDao.persist(externalTdap);

        Prevention evaluated = evaluatePersistedHistory();

        assertThat(count(evaluated, NEEDS_ADULT_TDAP)).isZero();
        assertThat(count(evaluated, NEEDS_TD)).isEqualTo(1);
        assertThat(count(evaluated, NEEDS_REPEAT_TD)).isZero();
    }
}
