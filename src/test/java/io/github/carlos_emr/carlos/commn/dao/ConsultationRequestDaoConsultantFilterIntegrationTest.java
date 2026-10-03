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
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.commn.model.ConsultationServices;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.consultation.dto.ConsultantOptionDto;
import io.github.carlos_emr.carlos.consultation.dto.ConsultationListFilterDto;
import io.github.carlos_emr.carlos.consultation.dto.ConsultationMrpOptionDto;
import io.github.carlos_emr.carlos.consultations.ConsultationRequestSearchFilter;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the Consultations list Consultant and Provider (MRP) filters (issue #3976):
 * {@link ConsultationRequestDao#getConsults(ConsultationListFilterDto)},
 * {@link ConsultationRequestDao#searchDistinctConsultants(String, int)},
 * {@link ConsultationRequestDao#findDistinctConsultMrps()} and the matching
 * {@code consultantId} / {@code mrpNo} filters of {@link ConsultRequestDao#search}.
 *
 * <p>Specialist surnames use a per-run unique prefix so rows seeded by other tests in the
 * shared H2 database can never match the name searches.</p>
 *
 * @since 2026-09-30
 */
@DisplayName("ConsultationRequestDao consultant and MRP filter integration tests")
@Tag("integration")
@Tag("dao")
@Tag("filter")
@Tag("consultation")
@Transactional
class ConsultationRequestDaoConsultantFilterIntegrationTest extends CarlosTestBase {

    private static final String[] DATE_FORMAT = new String[]{"yyyy-MM-dd"};

    @Autowired
    private ConsultationRequestDao consultationRequestDao;

    @Autowired
    private ConsultRequestDao consultRequestDao;

    /** Unique per test instance; keeps name searches and team filters isolated. */
    private String uid;
    private String team;

    private ProfessionalSpecialist brianSmith;
    private ProfessionalSpecialist annaJones;
    private ProfessionalSpecialist unreferenced;
    private Provider mrpA;
    private Provider mrpB;
    private Provider mrpWithoutConsults;
    private Integer serviceId;

    private ConsultationRequest toSmithForA;
    private ConsultationRequest toSmithForB;
    private ConsultationRequest toJonesForA;
    private ConsultationRequest toJonesForB;

    @BeforeEach
    void setUp() throws Exception {
        uid = Long.toString(System.nanoTime(), 36).replaceAll("[0-9]", "q");
        team = "t" + Long.toString(System.nanoTime() % 1_000_000_000L, 36);

        brianSmith = saveSpecialist("Qz" + uid + "smith", "Brian");
        annaJones = saveSpecialist("Qz" + uid + "jones", "Anna");
        unreferenced = saveSpecialist("Qz" + uid + "smithers", "Bob");

        String providerBase = String.valueOf(700000 + (int) (System.nanoTime() % 90000));
        mrpA = saveProvider(providerBase, "Alpha" + uid, "Ann");
        mrpB = saveProvider(String.valueOf(Integer.parseInt(providerBase) + 1), "Bravo" + uid, "Ben");
        mrpWithoutConsults = saveProvider(String.valueOf(Integer.parseInt(providerBase) + 2), "Charlie" + uid, "Cy");

        ConsultationServices cs = new ConsultationServices();
        EntityDataGenerator.generateTestDataForModelClass(cs);
        hibernateTemplate.save(cs);
        hibernateTemplate.flush();
        serviceId = cs.getId();

        Integer patientOfA = saveDemographic(mrpA.getProviderNo());
        Integer patientOfB = saveDemographic(mrpB.getProviderNo());

        Date referral = DateUtils.parseDate("2026-03-10", DATE_FORMAT);
        toSmithForA = saveConsult(patientOfA, brianSmith, referral);
        toSmithForB = saveConsult(patientOfB, brianSmith, referral);
        toJonesForA = saveConsult(patientOfA, annaJones, referral);
        toJonesForB = saveConsult(patientOfB, annaJones, referral);
        hibernateTemplate.flush();
    }

    private ProfessionalSpecialist saveSpecialist(String lastName, String firstName) {
        ProfessionalSpecialist ps = new ProfessionalSpecialist();
        ps.setLastName(lastName);
        ps.setFirstName(firstName);
        ps.setDeleted(false);
        hibernateTemplate.save(ps);
        return ps;
    }

    private Provider saveProvider(String providerNo, String lastName, String firstName) {
        Provider provider = new Provider();
        provider.setProviderNo(providerNo);
        provider.setLastName(lastName);
        provider.setFirstName(firstName);
        provider.setProviderType("doctor");
        provider.setSpecialty("GP");
        provider.setSex("");
        provider.setStatus("1");
        hibernateTemplate.save(provider);
        return provider;
    }

    private Integer saveDemographic(String mrpProviderNo) throws Exception {
        Demographic demographic = new Demographic();
        EntityDataGenerator.generateTestDataForModelClass(demographic);
        demographic.setDemographicNo(null);
        demographic.setProviderNo(mrpProviderNo);
        hibernateTemplate.save(demographic);
        hibernateTemplate.flush();
        return demographic.getDemographicNo();
    }

    private ConsultationRequest saveConsult(Integer demographicNo, ProfessionalSpecialist specialist, Date referralDate) {
        ConsultationRequest cr = new ConsultationRequest();
        cr.setDemographicId(demographicNo);
        cr.setProfessionalSpecialist(specialist);
        cr.setServiceId(serviceId);
        cr.setStatus("1");
        cr.setReferralDate(referralDate);
        cr.setProviderNo("999998");
        cr.setReasonForReferral("Test referral");
        cr.setSendTo(team);
        cr.setLastUpdateDate(new Date());
        hibernateTemplate.save(cr);
        return cr;
    }

    private ConsultationListFilterDto listFilter(Integer consultantId, String mrpProviderNo) {
        return new ConsultationListFilterDto(team, true, null, null, null, null, null, 0, 100,
                consultantId, mrpProviderNo);
    }

    @Test
    void shouldApplyPrivacyBeforePagination_whenOnlySomeMrpsAndSitesAreVisible() {
        // Default order is referral date descending: put three rejected rows before the sole visible row.
        toSmithForA.setReferralDate(java.sql.Date.valueOf("2026-03-09"));
        toSmithForA.setSiteName("Visible");
        toJonesForA.setSiteName("Hidden");
        toSmithForB.setSiteName("Visible");
        toJonesForB.setSiteName("Visible");
        hibernateTemplate.flush();
        var unrestricted = new ConsultationListFilterDto(team, true, null, null, null, null, null,
                0, 2, null, null);
        assertThat(consultationRequestDao.getConsults(unrestricted)).extracting(ConsultationRequest::getId)
                .doesNotContain(toSmithForA.getId());
        var allowed = java.util.Set.of(mrpA.getProviderNo());
        var sites = java.util.Set.of("Visible");
        var filter = new ConsultationListFilterDto(team, true, null, null, null, null, null,
                0, 2, null, null, allowed, sites);
        assertThat(consultationRequestDao.getConsults(filter)).extracting(ConsultationRequest::getId)
                .containsExactly(toSmithForA.getId());
        var next = new ConsultationListFilterDto(team, true, null, null, null, null, null,
                1, 2, null, null, allowed, sites);
        assertThat(consultationRequestDao.getConsults(next)).isEmpty();
    }

    @Test
    void shouldFailClosed_whenPrivacyAllowsNoProvidersOrSites() {
        for (boolean emptyProviders : new boolean[]{true, false}) {
            var filter = new ConsultationListFilterDto(team, true, null, null, null, null, null,
                    0, 100, null, null, emptyProviders ? java.util.Set.of() : null,
                    emptyProviders ? null : java.util.Set.of());
            assertThat(consultationRequestDao.getConsults(filter)).isEmpty();
        }
    }

    @Nested
    @DisplayName("getConsults(ConsultationListFilterDto)")
    class ListFilters {

        @Test
        @Tag("query")
        @DisplayName("should return every request of the team when no consultant or MRP filter is set")
        void shouldReturnAllTeamRequests_whenNoConsultantOrMrpFilter() {
            List<ConsultationRequest> results = consultationRequestDao.getConsults(listFilter(null, null));

            assertThat(results).extracting(ConsultationRequest::getId).containsExactlyInAnyOrder(
                    toSmithForA.getId(), toSmithForB.getId(), toJonesForA.getId(), toJonesForB.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return only requests sent to the chosen consultant")
        void shouldReturnOnlyConsultantRequests_whenConsultantFilterSet() {
            List<ConsultationRequest> results = consultationRequestDao.getConsults(listFilter(brianSmith.getId(), null));

            assertThat(results).extracting(ConsultationRequest::getId)
                    .containsExactlyInAnyOrder(toSmithForA.getId(), toSmithForB.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return only requests for patients of the chosen MRP")
        void shouldReturnOnlyMrpPatientRequests_whenMrpFilterSet() {
            List<ConsultationRequest> results = consultationRequestDao.getConsults(listFilter(null, mrpB.getProviderNo()));

            assertThat(results).extracting(ConsultationRequest::getId)
                    .containsExactlyInAnyOrder(toSmithForB.getId(), toJonesForB.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should intersect the consultant and MRP filters")
        void shouldIntersectFilters_whenConsultantAndMrpBothSet() {
            List<ConsultationRequest> results = consultationRequestDao.getConsults(
                    listFilter(annaJones.getId(), mrpA.getProviderNo()));

            assertThat(results).extracting(ConsultationRequest::getId).containsExactly(toJonesForA.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should treat a blank MRP as no MRP filter")
        void shouldIgnoreMrpFilter_whenMrpIsBlank() {
            List<ConsultationRequest> results = consultationRequestDao.getConsults(listFilter(brianSmith.getId(), "  "));

            assertThat(results).hasSize(2);
        }

        @Test
        @Tag("query")
        @DisplayName("should combine the new filters with the referral date range")
        void shouldCombineWithDateRange_whenDateRangeExcludesRequests() throws Exception {
            ConsultationListFilterDto filter = new ConsultationListFilterDto(team, true,
                    DateUtils.parseDate("2026-04-01", DATE_FORMAT), null, null, null, null, 0, 100,
                    brianSmith.getId(), mrpA.getProviderNo());

            assertThat(consultationRequestDao.getConsults(filter)).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should keep the legacy positional overload unfiltered by consultant and MRP")
        void shouldReturnAllTeamRequests_forPositionalOverload() {
            List<ConsultationRequest> results = consultationRequestDao.getConsults(team, true, null, null, null, null,
                    null, 0, 100);

            assertThat(results).hasSize(4);
        }

        @Test
        @Tag("query")
        @DisplayName("should sort the consultant-filtered list by specialist name")
        void shouldSortBySpecialist_whenOrderByToken6() {
            ConsultationListFilterDto filter = new ConsultationListFilterDto(team, true, null, null, "6", "1", null,
                    0, 100, null, mrpA.getProviderNo());

            List<ConsultationRequest> results = consultationRequestDao.getConsults(filter);

            // "...smith" sorts after "...jones"; desc="1" reverses it.
            assertThat(results).extracting(ConsultationRequest::getId)
                    .containsExactly(toSmithForA.getId(), toJonesForA.getId());
        }
    }

    @Nested
    @DisplayName("searchDistinctConsultants")
    class ConsultantSearch {

        @ParameterizedTest(name = "[{index}] \"{0}\"")
        @ValueSource(strings = {"%sSMITH,B", "%ssmith b", "brian %ssmith", "  BRIAN , %ssmith  "})
        @Tag("search")
        @DisplayName("should match regardless of token order, case and comma or space separators")
        void shouldMatchSpecialist_regardlessOfTokenOrderAndSeparator(String pattern) {
            String keyword = pattern.replace("%s", "qz" + uid);

            List<ConsultantOptionDto> results = consultationRequestDao.searchDistinctConsultants(keyword, 20);

            assertThat(results).extracting(ConsultantOptionDto::id).containsExactly(brianSmith.getId());
            assertThat(results.get(0).label()).isEqualTo(brianSmith.getLastName() + ", Brian");
        }

        @Test
        @Tag("search")
        @DisplayName("should return a specialist with several consults only once")
        void shouldReturnSpecialistOnce_whenReferencedByManyConsults() {
            List<ConsultantOptionDto> results = consultationRequestDao.searchDistinctConsultants("qz" + uid, 20);

            assertThat(results).extracting(ConsultantOptionDto::id)
                    .containsExactly(annaJones.getId(), brianSmith.getId());
        }

        @Test
        @Tag("search")
        @DisplayName("should not suggest a specialist no consultation request was sent to")
        void shouldExcludeSpecialist_whenNoConsultReferencesIt() {
            List<ConsultantOptionDto> results = consultationRequestDao.searchDistinctConsultants(
                    "qz" + uid + "smithers", 20);

            assertThat(results).isEmpty();
            assertThat(unreferenced.getId()).isNotNull();
        }

        @Test
        @Tag("search")
        @DisplayName("should match percent, underscore and bang literally")
        void shouldMatchWildcardsLiterally_whenKeywordContainsLikeMetacharacters() throws Exception {
            // A bare wildcard is text, not "match everything": nothing referenced contains "%" yet.
            assertThat(consultationRequestDao.searchDistinctConsultants("%", 20)).isEmpty();
            assertThat(consultationRequestDao.searchDistinctConsultants("__", 20)).isEmpty();

            ProfessionalSpecialist percent = saveSpecialist("Pc" + uid + "%x", "Lit");
            ProfessionalSpecialist percentDecoy = saveSpecialist("Pc" + uid + "yx", "Decoy");
            ProfessionalSpecialist underscore = saveSpecialist("Us" + uid + "_x", "Lit");
            ProfessionalSpecialist underscoreDecoy = saveSpecialist("Us" + uid + "yx", "Decoy");
            ProfessionalSpecialist bang = saveSpecialist("Bg" + uid + "!x", "Lit");
            Integer demo = saveDemographic(mrpA.getProviderNo());
            for (ProfessionalSpecialist ps : List.of(percent, percentDecoy, underscore, underscoreDecoy, bang)) {
                saveConsult(demo, ps, new Date());
            }
            hibernateTemplate.flush();

            assertThat(consultationRequestDao.searchDistinctConsultants("pc" + uid + "%x", 20))
                    .extracting(ConsultantOptionDto::id).containsExactly(percent.getId());
            assertThat(consultationRequestDao.searchDistinctConsultants("us" + uid + "_x", 20))
                    .extracting(ConsultantOptionDto::id).containsExactly(underscore.getId());
            assertThat(consultationRequestDao.searchDistinctConsultants("bg" + uid + "!x", 20))
                    .extracting(ConsultantOptionDto::id).containsExactly(bang.getId());
        }

        @Test
        @Tag("search")
        @DisplayName("should cap the number of suggestions at maxResults")
        void shouldCapResults_whenMoreSpecialistsMatchThanMaxResults() {
            List<ConsultantOptionDto> results = consultationRequestDao.searchDistinctConsultants("qz" + uid, 1);

            assertThat(results).hasSize(1);
        }

        @Test
        @Tag("search")
        @DisplayName("should return nothing for a null, blank or separator-only keyword")
        void shouldReturnEmpty_whenKeywordHasNoTokens() {
            assertThat(consultationRequestDao.searchDistinctConsultants(null, 20)).isEmpty();
            assertThat(consultationRequestDao.searchDistinctConsultants("   ", 20)).isEmpty();
            assertThat(consultationRequestDao.searchDistinctConsultants(" , ,", 20)).isEmpty();
            assertThat(consultationRequestDao.searchDistinctConsultants("qz" + uid, 0)).isEmpty();
        }
    }

    @Nested
    @DisplayName("findDistinctConsultMrps")
    class MrpOptions {

        @Test
        @Tag("query")
        @DisplayName("should list each MRP with consults once and skip MRPs without consults")
        void shouldListDistinctMrps_whenPatientsHaveConsults() {
            List<ConsultationMrpOptionDto> options = consultationRequestDao.findDistinctConsultMrps();

            assertThat(options).extracting(ConsultationMrpOptionDto::providerNo)
                    .contains(mrpA.getProviderNo(), mrpB.getProviderNo())
                    .doesNotContain(mrpWithoutConsults.getProviderNo())
                    .doesNotHaveDuplicates();
            assertThat(options).filteredOn(o -> o.providerNo().equals(mrpA.getProviderNo()))
                    .extracting(ConsultationMrpOptionDto::label)
                    .containsExactly(mrpA.getLastName() + ", Ann");
        }
    }

    @Nested
    @DisplayName("ConsultRequestDao.search (REST /consults/searchRequests)")
    class RestSearch {

        private ConsultationRequestSearchFilter restFilter() {
            ConsultationRequestSearchFilter filter = new ConsultationRequestSearchFilter();
            filter.setTeam(team);
            filter.setNumToReturn(99);
            return filter;
        }

        private List<Integer> searchIds(ConsultationRequestSearchFilter filter) {
            return consultRequestDao.search(filter).stream()
                    .map(row -> ((ConsultationRequest) row[0]).getId())
                    .toList();
        }

        @Test
        @Tag("search")
        @DisplayName("should filter REST search results by consultant")
        void shouldFilterRestSearch_byConsultantId() {
            ConsultationRequestSearchFilter filter = restFilter();
            filter.setConsultantId(annaJones.getId());

            assertThat(searchIds(filter)).containsExactlyInAnyOrder(toJonesForA.getId(), toJonesForB.getId());
            assertThat(consultRequestDao.getConsultationCount2(filter)).isEqualTo(2);
        }

        @Test
        @Tag("search")
        @DisplayName("should keep filtering REST search results by MRP")
        void shouldFilterRestSearch_byMrpNo() {
            ConsultationRequestSearchFilter filter = restFilter();
            filter.setMrpNo(Integer.valueOf(mrpA.getProviderNo()));

            assertThat(searchIds(filter)).containsExactlyInAnyOrder(toSmithForA.getId(), toJonesForA.getId());
            assertThat(consultRequestDao.getConsultationCount2(filter)).isEqualTo(2);
        }

        @Test
        @Tag("search")
        @DisplayName("should intersect REST consultant and MRP filters")
        void shouldIntersectRestFilters_forConsultantAndMrp() {
            ConsultationRequestSearchFilter filter = restFilter();
            filter.setConsultantId(brianSmith.getId());
            filter.setMrpNo(Integer.valueOf(mrpB.getProviderNo()));

            assertThat(searchIds(filter)).containsExactly(toSmithForB.getId());
        }

        @Test
        @Tag("search")
        @DisplayName("should ignore a non-positive REST consultant id")
        void shouldIgnoreConsultantFilter_whenIdNotPositive() {
            ConsultationRequestSearchFilter filter = restFilter();
            filter.setConsultantId(0);

            assertThat(searchIds(filter)).hasSize(4);
        }
    }
}
