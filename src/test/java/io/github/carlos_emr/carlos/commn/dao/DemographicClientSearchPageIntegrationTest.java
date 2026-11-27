/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.commn.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.github.carlos_emr.carlos.PMmodule.web.formbean.ClientSearchFormBean;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pins the window contract of {@link DemographicDao#searchClientPage}, the server-side paging the
 * program client search uses since #6134 (9c2014ef38): the HQL query and the native SOUNDEX query
 * each yield at most ten rows, the exact filtered total, the requested zero-based page clamped to
 * the last page (page zero when nothing matches), a primary sort taken only from the documented
 * allowlist (0/1 client number, 2 name, ... 7 alert; any other code falls back to the client
 * number), a descending flag that reverses only that primary sort while equal values stay in
 * ascending client-number order across page boundaries, and a refusal of negative pages. The
 * client-number lookup is windowed the same way.
 *
 * <p>Each parameterized case runs once per query: {@code soundex=false} searches the literal first
 * name, which only the HQL {@code LIKE} predicate can match, and {@code soundex=true} searches a
 * different spelling that only {@code SOUNDEX()} admits, so the native id-page-then-hydrate path
 * provides every row. A random chart number confines every count to the case's own fixtures.
 * {@code ServerTablePagingIntegrationTest} covers every allowlisted column in both directions and
 * SQL-syntax search values; this class adds the clamped non-empty window, the fallback for codes
 * outside the allowlist, filtered counts, hydrated SOUNDEX rows, the windowed client-number lookup
 * and the negative-page refusal (#6336). Fixture surnames are synthetic {@code FAKE-} values.</p>
 */
@Tag("integration")
@Tag("dao")
@Tag("demographic")
@Transactional
class DemographicClientSearchPageIntegrationTest extends CarlosTestBase {
    private static final int PAGE_SIZE = 10;
    private static final int CLIENT_NUMBER = 1;
    private static final int NAME = 2;

    @Autowired
    private DemographicDao demographics;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldClampPagesPastTheEnd_toTheLastTenRowWindow(boolean soundex) {
        String chart = chartNumber();
        List<Integer> clients = new ArrayList<>();
        for (int i = 0; i < 23; i++) {
            clients.add(persistClient(chart, "Robert", "FAKE-Window", "M"));
        }
        entityManager.flush();
        entityManager.clear();
        ClientSearchFormBean criteria = criteria(chart, soundex);

        QueryPage<Demographic> first = demographics.searchClientPage(criteria, 0, CLIENT_NUMBER, false);
        assertThat(clientNumbers(first)).containsExactlyElementsOf(clients.subList(0, 10));
        assertThat(first.total()).isEqualTo(23);
        assertThat(first.page()).isZero();
        assertThat(first.size()).isEqualTo(PAGE_SIZE);
        assertThat(first.pageCount()).isEqualTo(3);
        assertThat(first.previous()).isFalse();
        assertThat(first.next()).isTrue();

        for (int requested : new int[] {2, 3, 999, Integer.MAX_VALUE}) {
            QueryPage<Demographic> last = demographics.searchClientPage(criteria, requested, CLIENT_NUMBER, false);
            assertThat(last.page()).as("page served for requested page %s", requested).isEqualTo(2);
            assertThat(clientNumbers(last)).as("rows for requested page %s", requested)
                    .containsExactlyElementsOf(clients.subList(20, 23));
            assertThat(last.total()).isEqualTo(23);
            assertThat(last.size()).isEqualTo(PAGE_SIZE);
            assertThat(last.previous()).isTrue();
            assertThat(last.next()).isFalse();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldServeAnEmptyFirstPage_whenNoClientMatches(boolean soundex) {
        String chart = chartNumber();
        persistClient(chart, "Robert", "FAKE-Elsewhere", "M");
        entityManager.flush();
        entityManager.clear();

        // A chart number no fixture carries: the request for page 7 is clamped to page zero.
        QueryPage<Demographic> page = demographics.searchClientPage(criteria(chartNumber(), soundex), 7, NAME, true);

        assertThat(page.rows()).isEmpty();
        assertThat(page.total()).isZero();
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(PAGE_SIZE);
        assertThat(page.pageCount()).isEqualTo(1);
        assertThat(page.next()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldFallBackToClientNumberOrder_forSortCodesOutsideTheAllowlist(boolean soundex) {
        String chart = chartNumber();
        List<Integer> ascending = new ArrayList<>();
        // Surnames fall as client numbers rise, so name order is the reverse of number order.
        for (int i = 0; i < 12; i++) {
            ascending.add(persistClient(chart, "Robert", "FAKE-" + (char) ('L' - i), "M"));
        }
        entityManager.flush();
        entityManager.clear();
        List<Integer> descending = ascending.reversed();
        ClientSearchFormBean criteria = criteria(chart, soundex);

        assertThat(readEveryPage(criteria, NAME, false, 12)).as("allowlisted name sort")
                .containsExactlyElementsOf(descending);
        for (int code : new int[] {0, CLIENT_NUMBER, -1, 8, 42, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThat(readEveryPage(criteria, code, false, 12)).as("sort code %s ascending", code)
                    .containsExactlyElementsOf(ascending);
            assertThat(readEveryPage(criteria, code, true, 12)).as("sort code %s descending", code)
                    .containsExactlyElementsOf(descending);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldReverseOnlyTheNameOrder_whenSortingNamesDescending(boolean soundex) {
        String chart = chartNumber();
        List<Integer> alpha = new ArrayList<>();
        List<Integer> mike = new ArrayList<>();
        List<Integer> zulu = new ArrayList<>();
        // Interleaved so that neither direction of the name order matches insertion order.
        for (int i = 0; i < 12; i++) {
            switch (i % 3) {
                case 0 -> mike.add(persistClient(chart, "Robert", "FAKE-Mike", "M"));
                case 1 -> zulu.add(persistClient(chart, "Robert", "FAKE-Zulu", "M"));
                default -> alpha.add(persistClient(chart, "Robert", "FAKE-Alpha", "M"));
            }
        }
        entityManager.flush();
        entityManager.clear();
        ClientSearchFormBean criteria = criteria(chart, soundex);

        assertThat(readEveryPage(criteria, NAME, false, 12))
                .containsExactlyElementsOf(Stream.of(alpha, mike, zulu).flatMap(List::stream).toList());
        // Page zero ends inside the FAKE-Alpha group: its ties continue in ascending client-number
        // order on page one, while the surname groups themselves are reversed.
        assertThat(readEveryPage(criteria, NAME, true, 12))
                .containsExactlyElementsOf(Stream.of(zulu, mike, alpha).flatMap(List::stream).toList());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldCountEveryFilteredClient_independentlyOfTheReturnedWindow(boolean soundex) {
        String chart = chartNumber();
        List<Integer> men = new ArrayList<>();
        for (int i = 0; i < 13; i++) {
            men.add(persistClient(chart, "Robert", "FAKE-Count", "M"));
        }
        for (int i = 0; i < 4; i++) {
            persistClient(chart, "Robert", "FAKE-Count", "F");
        }
        // Shares the chart number but matches neither the LIKE nor the SOUNDEX name predicate.
        persistClient(chart, "FAKE-Other", "FAKE-Count", "M");
        entityManager.flush();
        entityManager.clear();
        ClientSearchFormBean criteria = criteria(chart, soundex);
        criteria.setGender("M");

        for (int requested : new int[] {0, 1, 5}) {
            assertThat(demographics.searchClientPage(criteria, requested, CLIENT_NUMBER, false).total())
                    .as("total for requested page %s", requested).isEqualTo(13);
        }
        QueryPage<Demographic> second = demographics.searchClientPage(criteria, 1, CLIENT_NUMBER, false);
        assertThat(clientNumbers(second)).containsExactlyElementsOf(men.subList(10, 13));
        // Rows are fully mapped entities: the gender description is a formula column that native
        // d.* rows do not carry, so the SOUNDEX page must have been hydrated through HQL.
        String male = genderDescription("M");
        assertThat(second.rows()).allSatisfy(row -> {
            assertThat(row.getSex()).isEqualTo("M");
            assertThat(row.getSexDesc()).isEqualTo(male);
        });

        criteria.setGender("F");
        assertThat(demographics.searchClientPage(criteria, 0, CLIENT_NUMBER, false).total()).isEqualTo(4);
        criteria.setGender(null);
        assertThat(demographics.searchClientPage(criteria, 0, CLIENT_NUMBER, false).total()).isEqualTo(17);
    }

    @Test
    void shouldWindowTheClientNumberLookup_toItsSingleMatch() {
        String chart = chartNumber();
        int client = persistClient(chart, "Robert", "FAKE-Number", "F");
        entityManager.flush();
        entityManager.clear();
        ClientSearchFormBean criteria = new ClientSearchFormBean();
        criteria.setSearchOutsideDomain(true);
        criteria.setDemographicNo(Integer.toString(client));

        for (int requested : new int[] {0, 4}) {
            QueryPage<Demographic> page = demographics.searchClientPage(criteria, requested, NAME, true);
            assertThat(clientNumbers(page)).as("rows for requested page %s", requested).containsExactly(client);
            assertThat(page.total()).isEqualTo(1);
            assertThat(page.page()).isZero();
            assertThat(page.size()).isEqualTo(PAGE_SIZE);
        }

        criteria.setDemographicNo(Integer.toString(Integer.MAX_VALUE));
        assertEmptyFirstPage(demographics.searchClientPage(criteria, 3, CLIENT_NUMBER, false));
        // A client number that is not an integer short-circuits before any query runs.
        criteria.setDemographicNo(client + "x");
        assertEmptyFirstPage(demographics.searchClientPage(criteria, 3, CLIENT_NUMBER, false));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldRefuseANegativePage_forBothSearchQueries(boolean soundex) {
        ClientSearchFormBean criteria = criteria(chartNumber(), soundex);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> demographics.searchClientPage(criteria, -1, CLIENT_NUMBER, false))
                .withMessage("Invalid page");
    }

    /**
     * Search criteria confined to one chart number. "Rupert" is not a substring of the fixtures'
     * "Robert", so with SOUNDEX on only the native {@code SOUNDEX()} predicate admits them, while
     * the HQL query is given the literal name its {@code LIKE} predicate matches.
     */
    private static ClientSearchFormBean criteria(String chart, boolean soundex) {
        ClientSearchFormBean criteria = new ClientSearchFormBean();
        criteria.setChartNo(chart);
        criteria.setSearchOutsideDomain(true);
        criteria.setSearchUsingSoundex(soundex);
        criteria.setFirstName(soundex ? "Rupert" : "Robert");
        return criteria;
    }

    /** Reads every page in turn, checking each window, and returns the client numbers in order. */
    private List<Integer> readEveryPage(ClientSearchFormBean criteria, int sort, boolean descending, int expectedTotal) {
        List<Integer> clients = new ArrayList<>();
        for (int page = 0; page * PAGE_SIZE < expectedTotal; page++) {
            QueryPage<Demographic> window = demographics.searchClientPage(criteria, page, sort, descending);
            assertThat(window.total()).isEqualTo(expectedTotal);
            assertThat(window.page()).isEqualTo(page);
            assertThat(window.rows()).hasSize(Math.min(PAGE_SIZE, expectedTotal - page * PAGE_SIZE));
            clients.addAll(clientNumbers(window));
        }
        return clients;
    }

    private static void assertEmptyFirstPage(QueryPage<Demographic> page) {
        assertThat(page.rows()).isEmpty();
        assertThat(page.total()).isZero();
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(PAGE_SIZE);
    }

    private static List<Integer> clientNumbers(QueryPage<Demographic> page) {
        return page.rows().stream().map(Demographic::getDemographicNo).toList();
    }

    /** A chart number no other fixture uses; it fits the 15-character {@code chart_no} column. */
    private static String chartNumber() {
        return "P" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private int persistClient(String chart, String firstName, String lastName, String sex) {
        Demographic patient = new Demographic();
        patient.setFirstName(firstName);
        patient.setLastName(lastName);
        patient.setSex(sex);
        patient.setPrefName("");
        patient.setYearOfBirth("1990");
        patient.setMonthOfBirth("01");
        patient.setDateOfBirth("01");
        patient.setChartNo(chart);
        entityManager.persist(patient);
        return patient.getDemographicNo();
    }

    private String genderDescription(String code) {
        return (String) entityManager.createNativeQuery("SELECT description FROM lst_gender WHERE code = :code")
                .setParameter("code", code).getSingleResult();
    }
}
