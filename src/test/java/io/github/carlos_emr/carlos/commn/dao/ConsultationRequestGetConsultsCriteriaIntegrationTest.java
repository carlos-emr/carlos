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
import io.github.carlos_emr.carlos.commn.model.ConsultationRequestExt;
import io.github.carlos_emr.carlos.commn.model.ConsultationServices;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Equivalence and security tests for the JPA Criteria refactor of
 * {@link ConsultationRequestDaoImpl#getConsults(String, boolean, java.util.Date, java.util.Date, String, String, String, Integer, Integer)}.
 *
 * <p>The legacy implementation concatenated the {@code team} value straight into an HQL
 * string (issue #1748); the refactor binds it as a Criteria parameter. These tests pin the
 * preserved behaviour (status/date/sort/pagination semantics) and the closed injection, and
 * document the one deliberate behaviour change approved with the refactor: the dead
 * {@code ConsultationRequestExt} ("ereferral_service") join was dropped, so it no longer
 * multiplies result rows.</p>
 *
 * <p>{@code ConsultationRequestDaoImpl} itself carries no stereotype, but its concrete
 * Spring bean is {@code ConsultationRequestMergedDemographicDaoImpl}
 * ({@code @Repository("consultationRequestDao")}), a subclass that inherits the refactored
 * {@code getConsults(team, ...)}. Autowiring {@link ConsultationRequestDao} resolves that same
 * managed bean (the one production obtains via {@code SpringUtils.getBean}), so these tests run
 * the genuine {@code @PersistenceContext} injection path. Fixtures are isolated from any
 * pre-existing rows by tagging them with a per-test {@code sendTo} marker (or a unique date
 * window) and filtering on it.</p>
 *
 * @since 2026-06-18
 * @see ConsultationRequestDaoImpl
 */
@DisplayName("ConsultationRequestDao.getConsults Criteria refactor")
@Tag("integration")
@Tag("dao")
@Tag("consultation")
@Transactional
public class ConsultationRequestGetConsultsCriteriaIntegrationTest extends CarlosTestBase {

    // The production bean for ConsultationRequestDao is ConsultationRequestMergedDemographicDaoImpl,
    // annotated @Repository("consultationRequestDao"); it extends ConsultationRequestDaoImpl and does
    // not override the 9-arg getConsults, so it inherits the refactored method. Autowiring by type
    // resolves that same managed bean here, exercising the real @PersistenceContext-injected
    // EntityManager path that production uses via SpringUtils.getBean(ConsultationRequestDao.class).
    @Autowired
    private ConsultationRequestDao dao;

    // Separate handle for persisting fixture entities; in a @Transactional test it shares the same
    // transaction-bound persistence context as the DAO bean, so persisted rows are visible (the
    // Criteria query auto-flushes before executing).
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    // ---------------------------------------------------------------------------------------
    // Core security assertion: the injection is closed because team is now a bound parameter.
    // ---------------------------------------------------------------------------------------

    @Test
    @Tag("search")
    @DisplayName("should return only literal matches when team contains an injection payload")
    void shouldReturnOnlyLiteralMatches_whenTeamContainsInjectionPayload() {
        String team = "cardiology_" + marker();
        persistConsult(c -> {
            c.setSendTo(team);
            c.setStatus("1");
            c.setReferralDate(date(2031, 1, 1));
        });
        persistConsult(c -> {
            c.setSendTo("ortho_" + marker());
            c.setStatus("1");
            c.setReferralDate(date(2031, 1, 2));
        });

        // Legacy code would have concatenated this into "... cr.sendTo = 'x' OR '1'='1' ..."
        // and returned everything. Bound as a parameter it is a literal that matches nothing.
        List<ConsultationRequest> injected =
                dao.getConsults(team + "' OR '1'='1", true, null, null, null, null, null, null, null);
        assertThat(injected).isEmpty();

        List<ConsultationRequest> literal =
                dao.getConsults(team, true, null, null, null, null, null, null, null);
        assertThat(literal).extracting(ConsultationRequest::getSendTo).containsOnly(team);
        assertThat(literal).hasSize(1);
    }

    @Test
    @Tag("filter")
    @DisplayName("should match exactly when team equals a stored value")
    void shouldMatchExactly_whenTeamEqualsStoredValue() {
        String team = "team_" + marker();
        persistConsult(c -> {
            c.setSendTo(team);
            c.setReferralDate(date(2031, 2, 1));
        });
        persistConsult(c -> {
            c.setSendTo(team);
            c.setReferralDate(date(2031, 2, 2));
        });
        persistConsult(c -> {
            c.setSendTo("other_" + marker());
            c.setReferralDate(date(2031, 2, 3));
        });

        List<ConsultationRequest> results =
                dao.getConsults(team, true, null, null, null, null, null, null, null);

        assertThat(results).hasSize(2).extracting(ConsultationRequest::getSendTo).containsOnly(team);
    }

    // ---------------------------------------------------------------------------------------
    // status / showCompleted (NULL-excluding semantics preserved).
    // ---------------------------------------------------------------------------------------

    @Test
    @Tag("filter")
    @DisplayName("should exclude completed and null-status rows when showCompleted is false")
    void shouldExcludeCompletedAndNullStatus_whenShowCompletedFalse() {
        String team = "status_" + marker();
        persistConsult(c -> {
            c.setSendTo(team);
            c.setStatus("1");
            c.setReferralDate(date(2032, 1, 1));
        });
        persistConsult(c -> {
            c.setSendTo(team);
            c.setStatus("4");
            c.setReferralDate(date(2032, 1, 2));
        });
        persistConsult(c -> {
            c.setSendTo(team);
            c.setStatus(null);
            c.setReferralDate(date(2032, 1, 3));
        });

        List<ConsultationRequest> active =
                dao.getConsults(team, false, null, null, null, null, null, null, null);
        assertThat(active).extracting(ConsultationRequest::getStatus).containsExactly("1");

        List<ConsultationRequest> all =
                dao.getConsults(team, true, null, null, null, null, null, null, null);
        assertThat(all).hasSize(3);
    }

    // ---------------------------------------------------------------------------------------
    // No-filter default: all rows for the (date-window-isolated) team, referralDate desc.
    // ---------------------------------------------------------------------------------------

    @Test
    @Tag("query")
    @DisplayName("should return all matching rows ordered by referral date descending for default sort")
    void shouldReturnAllRowsOrderedByReferralDateDesc_forDefaultSort() {
        // team="" exercises the "filter omitted" path; a unique far-future date window isolates
        // these rows from any pre-existing seed data.
        Date windowStart = date(2090, 1, 1);
        ConsultationRequest earliest = persistConsult(c -> {
            c.setSendTo("x_" + marker());
            c.setReferralDate(date(2090, 1, 10));
        });
        ConsultationRequest latest = persistConsult(c -> {
            c.setSendTo("y_" + marker());
            c.setReferralDate(date(2090, 3, 10));
        });
        ConsultationRequest middle = persistConsult(c -> {
            c.setSendTo("z_" + marker());
            c.setReferralDate(date(2090, 2, 10));
        });

        List<ConsultationRequest> results =
                dao.getConsults("", true, windowStart, null, null, null, "0", null, null);

        assertThat(results).extracting(ConsultationRequest::getId)
                .containsExactly(latest.getId(), middle.getId(), earliest.getId());
    }

    // ---------------------------------------------------------------------------------------
    // Date bounds (inclusive) + NULL date exclusion, for referral and appointment.
    // ---------------------------------------------------------------------------------------

    @Test
    @Tag("filter")
    @DisplayName("should apply inclusive referral-date bounds and drop null referral dates when searchDate is not 1")
    void shouldApplyInclusiveReferralBounds_whenSearchDateNotOne() {
        String team = "refdate_" + marker();
        ConsultationRequest before = persistConsult(c -> {
            c.setSendTo(team);
            c.setReferralDate(date(2033, 1, 5));
        });
        ConsultationRequest lower = persistConsult(c -> {
            c.setSendTo(team);
            c.setReferralDate(date(2033, 1, 10));
        });
        ConsultationRequest upper = persistConsult(c -> {
            c.setSendTo(team);
            c.setReferralDate(date(2033, 1, 20));
        });
        ConsultationRequest after = persistConsult(c -> {
            c.setSendTo(team);
            c.setReferralDate(date(2033, 1, 25));
        });
        ConsultationRequest nullDate = persistConsult(c -> {
            c.setSendTo(team);
            c.setReferralDate(null);
        });

        List<ConsultationRequest> results = dao.getConsults(team, true,
                date(2033, 1, 10), date(2033, 1, 20), null, null, "0", null, null);

        assertThat(results).extracting(ConsultationRequest::getId)
                .containsExactlyInAnyOrder(lower.getId(), upper.getId())
                .doesNotContain(before.getId(), after.getId(), nullDate.getId());
    }

    @Test
    @Tag("filter")
    @DisplayName("should apply inclusive appointment-date bounds when searchDate is 1")
    void shouldApplyInclusiveAppointmentBounds_whenSearchDateIsOne() {
        String team = "apptdate_" + marker();
        // referralDate is set out-of-window to prove the filter targets appointmentDate, not referralDate.
        ConsultationRequest inWindow = persistConsult(c -> {
            c.setSendTo(team);
            c.setAppointmentDate(date(2034, 6, 15));
            c.setReferralDate(date(2000, 1, 1));
        });
        ConsultationRequest outWindow = persistConsult(c -> {
            c.setSendTo(team);
            c.setAppointmentDate(date(2034, 7, 1));
            c.setReferralDate(date(2034, 6, 15));
        });

        List<ConsultationRequest> results = dao.getConsults(team, true,
                date(2034, 6, 1), date(2034, 6, 30), null, null, "1", null, null);

        assertThat(results).extracting(ConsultationRequest::getId).containsExactly(inWindow.getId());
        assertThat(results).extracting(ConsultationRequest::getId).doesNotContain(outWindow.getId());
    }

    // ---------------------------------------------------------------------------------------
    // Ordering: direction, default fallback, and the entity-join columns (the risky part).
    // ---------------------------------------------------------------------------------------

    @Test
    @Tag("query")
    @DisplayName("should order by appointment date ascending and descending per the desc flag")
    void shouldOrderByAppointmentDate_perDescFlag() {
        String team = "apptsort_" + marker();
        ConsultationRequest early = persistConsult(c -> {
            c.setSendTo(team);
            c.setAppointmentDate(date(2035, 1, 1));
        });
        ConsultationRequest late = persistConsult(c -> {
            c.setSendTo(team);
            c.setAppointmentDate(date(2035, 12, 1));
        });

        List<ConsultationRequest> asc = dao.getConsults(team, true, null, null, "8", null, null, null, null);
        assertThat(asc).extracting(ConsultationRequest::getId).containsExactly(early.getId(), late.getId());

        List<ConsultationRequest> desc = dao.getConsults(team, true, null, null, "8", "1", null, null, null);
        assertThat(desc).extracting(ConsultationRequest::getId).containsExactly(late.getId(), early.getId());
    }

    @Test
    @Tag("query")
    @DisplayName("should fall back to referral date descending for an unknown order token")
    void shouldFallBackToReferralDateDesc_forUnknownToken() {
        String team = "unknown_" + marker();
        ConsultationRequest a = persistConsult(c -> {
            c.setSendTo(team);
            c.setReferralDate(date(2036, 1, 1));
        });
        ConsultationRequest b = persistConsult(c -> {
            c.setSendTo(team);
            c.setReferralDate(date(2036, 5, 1));
        });

        // Unknown token + desc=null must still come back referralDate DESC (legacy default branch).
        List<ConsultationRequest> results = dao.getConsults(team, true, null, null, "99", null, null, null, null);
        assertThat(results).extracting(ConsultationRequest::getId).containsExactly(b.getId(), a.getId());
    }

    @Test
    @Tag("query")
    @DisplayName("should order by demographic last name through the entity join when token is 3")
    void shouldOrderByDemographicLastNameThroughEntityJoin_whenTokenIsThree() {
        String team = "demosort_" + marker();
        Demographic younger = persistDemographic("Zulu");
        Demographic older = persistDemographic("Alpha");
        ConsultationRequest z = persistConsult(c -> {
            c.setSendTo(team);
            c.setDemographicId(younger.getDemographicNo());
        });
        ConsultationRequest a = persistConsult(c -> {
            c.setSendTo(team);
            c.setDemographicId(older.getDemographicNo());
        });

        List<ConsultationRequest> asc = dao.getConsults(team, true, null, null, "3", null, null, null, null);
        assertThat(asc).extracting(ConsultationRequest::getId).containsExactly(a.getId(), z.getId());
    }

    @Test
    @Tag("query")
    @DisplayName("should order by provider last name through the chained entity join when token is 4")
    void shouldOrderByProviderLastNameThroughEntityJoin_whenTokenIsFour() {
        String team = "provsort_" + marker();
        Provider pB = persistProvider("prov_b_" + marker(), "Brown");
        Provider pA = persistProvider("prov_a_" + marker(), "Adams");
        Demographic dB = persistDemographicWithProvider("PatB", pB.getProviderNo());
        Demographic dA = persistDemographicWithProvider("PatA", pA.getProviderNo());
        ConsultationRequest cB = persistConsult(c -> {
            c.setSendTo(team);
            c.setDemographicId(dB.getDemographicNo());
        });
        ConsultationRequest cA = persistConsult(c -> {
            c.setSendTo(team);
            c.setDemographicId(dA.getDemographicNo());
        });

        List<ConsultationRequest> asc = dao.getConsults(team, true, null, null, "4", null, null, null, null);
        assertThat(asc).extracting(ConsultationRequest::getId).containsExactly(cA.getId(), cB.getId());
    }

    @Test
    @Tag("query")
    @DisplayName("should order by service description through the entity join when token is 5")
    void shouldOrderByServiceDescThroughEntityJoin_whenTokenIsFive() {
        String team = "svcsort_" + marker();
        ConsultationServices svcB = persistService("Radiology");
        ConsultationServices svcA = persistService("Cardiology");
        ConsultationRequest cB = persistConsult(c -> {
            c.setSendTo(team);
            c.setServiceId(svcB.getServiceId());
        });
        ConsultationRequest cA = persistConsult(c -> {
            c.setSendTo(team);
            c.setServiceId(svcA.getServiceId());
        });

        List<ConsultationRequest> asc = dao.getConsults(team, true, null, null, "5", null, null, null, null);
        assertThat(asc).extracting(ConsultationRequest::getId).containsExactly(cA.getId(), cB.getId());
    }

    @Test
    @Tag("query")
    @DisplayName("should order by specialist last name through the association join when token is 6")
    void shouldOrderBySpecialistLastNameThroughAssociationJoin_whenTokenIsSix() {
        // Token 6 sorts on the native professionalSpecialist @ManyToOne association join, a
        // different mechanism from the three Hibernate entity joins covered above.
        String team = "specsort_" + marker();
        ProfessionalSpecialist zulu = persistSpecialist("Zulu");
        ProfessionalSpecialist alpha = persistSpecialist("Alpha");
        ConsultationRequest z = persistConsult(c -> {
            c.setSendTo(team);
            c.setProfessionalSpecialist(zulu);
        });
        ConsultationRequest a = persistConsult(c -> {
            c.setSendTo(team);
            c.setProfessionalSpecialist(alpha);
        });

        List<ConsultationRequest> asc = dao.getConsults(team, true, null, null, "6", null, null, null, null);
        assertThat(asc).extracting(ConsultationRequest::getId).containsExactly(a.getId(), z.getId());

        List<ConsultationRequest> desc = dao.getConsults(team, true, null, null, "6", "1", null, null, null);
        assertThat(desc).extracting(ConsultationRequest::getId).containsExactly(z.getId(), a.getId());
    }

    @Test
    @Tag("query")
    @DisplayName("should break ties on the always-ascending secondary service description when the primary key ties")
    void shouldBreakTiesOnSecondaryServiceDesc_whenPrimaryKeyTies() {
        String team = "tiebreak_" + marker();
        ConsultationServices bravo = persistService("Bravo");
        ConsultationServices alpha = persistService("Alpha");
        // Both rows share status "1", so the token-1 primary sort key ties and the always-ascending
        // secondary "service.serviceDesc" must break the tie (Alpha service before Bravo service).
        ConsultationRequest withBravo = persistConsult(c -> {
            c.setSendTo(team);
            c.setStatus("1");
            c.setServiceId(bravo.getServiceId());
        });
        ConsultationRequest withAlpha = persistConsult(c -> {
            c.setSendTo(team);
            c.setStatus("1");
            c.setServiceId(alpha.getServiceId());
        });

        List<ConsultationRequest> primaryAsc = dao.getConsults(team, true, null, null, "1", null, null, null, null);
        assertThat(primaryAsc).extracting(ConsultationRequest::getId).containsExactly(withAlpha.getId(), withBravo.getId());

        // The secondary remains ascending even when the primary direction is DESC; with the primary
        // key tied, the secondary still orders Alpha service before Bravo service.
        List<ConsultationRequest> primaryDesc = dao.getConsults(team, true, null, null, "1", "1", null, null, null);
        assertThat(primaryDesc).extracting(ConsultationRequest::getId).containsExactly(withAlpha.getId(), withBravo.getId());
    }

    // ---------------------------------------------------------------------------------------
    // Pagination edges.
    // ---------------------------------------------------------------------------------------

    @Test
    @Tag("query")
    @DisplayName("should cap results at the default limit when limit is null")
    void shouldCapResultsAtDefaultLimit_whenLimitNull() {
        String team = "cap_" + marker();
        for (int i = 0; i < ConsultationRequestDao.DEFAULT_CONSULT_REQUEST_RESULTS_LIMIT + 5; i++) {
            persistConsult(c -> {
                c.setSendTo(team);
                c.setReferralDate(date(2037, 1, 1));
            });
        }

        List<ConsultationRequest> results =
                dao.getConsults(team, true, null, null, null, null, null, null, null);

        assertThat(results).hasSize(ConsultationRequestDao.DEFAULT_CONSULT_REQUEST_RESULTS_LIMIT);
    }

    @Test
    @Tag("query")
    @DisplayName("should return an empty list when limit is zero")
    void shouldReturnEmpty_whenLimitIsZero() {
        String team = "zero_" + marker();
        persistConsult(c -> {
            c.setSendTo(team);
            c.setReferralDate(date(2038, 1, 1));
        });

        List<ConsultationRequest> results =
                dao.getConsults(team, true, null, null, null, null, null, 0, 0);

        assertThat(results).isEmpty();
    }

    @Test
    @Tag("query")
    @DisplayName("should return an empty list when the offset is past the end")
    void shouldReturnEmpty_whenOffsetPastEnd() {
        String team = "off_" + marker();
        persistConsult(c -> {
            c.setSendTo(team);
            c.setReferralDate(date(2039, 1, 1));
        });
        persistConsult(c -> {
            c.setSendTo(team);
            c.setReferralDate(date(2039, 1, 2));
        });

        List<ConsultationRequest> results =
                dao.getConsults(team, true, null, null, null, null, null, 50, null);

        assertThat(results).isEmpty();
    }

    // ---------------------------------------------------------------------------------------
    // Approved behaviour change: dropping the dead ext join removes accidental duplicates.
    // ---------------------------------------------------------------------------------------

    @Test
    @Tag("query")
    @DisplayName("should return a consult once even with multiple ereferral ext rows after dropping the dead ext join")
    void shouldReturnConsultOnce_whenMultipleEreferralExtRowsExist() {
        String team = "dedupe_" + marker();
        ConsultationRequest consult = persistConsult(c -> {
            c.setSendTo(team);
            c.setReferralDate(date(2040, 1, 1));
        });
        // Two matching ext rows would have multiplied the row under the legacy LEFT JOIN ... ON
        // ext.key='ereferral_service'. That join is dropped, so the consult appears exactly once.
        persistExt(consult.getId(), "ereferral_service");
        persistExt(consult.getId(), "ereferral_service");

        List<ConsultationRequest> results =
                dao.getConsults(team, true, null, null, null, null, null, null, null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(consult.getId());
    }

    // ---------------------------------------------------------------------------------------
    // Fixtures.
    // ---------------------------------------------------------------------------------------

    private interface ConsultCustomizer {
        void apply(ConsultationRequest consult);
    }

    private ConsultationRequest persistConsult(ConsultCustomizer customizer) {
        ConsultationRequest consult = new ConsultationRequest();
        consult.setStatus("1");
        consult.setReferralDate(date(2030, 1, 1));
        consult.setReasonForReferral("test");
        consult.setLastUpdateDate(new Date());
        customizer.apply(consult);
        entityManager.persist(consult);
        return consult;
    }

    private Demographic persistDemographic(String lastName) {
        return persistDemographicWithProvider(lastName, null);
    }

    private Demographic persistDemographicWithProvider(String lastName, String providerNo) {
        Demographic demographic = new Demographic();
        demographic.setLastName(lastName);
        demographic.setFirstName("Test");
        demographic.setSex("M");
        if (providerNo != null) {
            demographic.setProviderNo(providerNo);
        }
        entityManager.persist(demographic);
        return demographic;
    }

    private Provider persistProvider(String providerNo, String lastName) {
        Provider provider = new Provider();
        provider.setProviderNo(providerNo);
        provider.setLastName(lastName);
        provider.setFirstName("Test");
        provider.setProviderType("doctor");
        provider.setSex("M");
        provider.setSpecialty("general");
        entityManager.persist(provider);
        return provider;
    }

    private ConsultationServices persistService(String serviceDesc) {
        ConsultationServices service = new ConsultationServices();
        service.setServiceDesc(serviceDesc);
        entityManager.persist(service);
        return service;
    }

    private ProfessionalSpecialist persistSpecialist(String lastName) {
        ProfessionalSpecialist specialist = new ProfessionalSpecialist();
        specialist.setFirstName("Test");
        specialist.setLastName(lastName);
        specialist.setSpecialtyType("Cardiology");
        specialist.setReferralNo("REF-" + marker());
        entityManager.persist(specialist);
        return specialist;
    }

    private void persistExt(Integer requestId, String key) {
        ConsultationRequestExt ext = new ConsultationRequestExt();
        ext.setRequestId(requestId);
        ext.setKey(key);
        ext.setValue("1");
        entityManager.persist(ext);
    }

    private static String marker() {
        return Long.toString(System.nanoTime());
    }

    private static Date date(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month - 1, day, 0, 0, 0);
        return calendar.getTime();
    }
}
