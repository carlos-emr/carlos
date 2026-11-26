// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.commn.dao;

import static org.assertj.core.api.Assertions.*;
import io.github.carlos_emr.carlos.PMmodule.web.formbean.ClientSearchFormBean;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.commn.model.Admission;
import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Tag("integration") @Tag("dao") @Transactional
class ServerTablePagingIntegrationTest extends CarlosTestBase {
    @Autowired private ProfessionalSpecialistDao specialists;
    @Autowired private DemographicDao demographics;
    @jakarta.persistence.PersistenceContext(unitName = "entityManagerFactory")
    private jakarta.persistence.EntityManager entityManager;

    @Test
    void shouldBoundRowsAndRetainEverySpecialist_whenTraversingDuplicateNames() {
        String prefix = "Page" + Long.toString(System.nanoTime(), 36);
        Set<Integer> expected = new HashSet<>();
        for (int i = 0; i < 37; i++) {
            var specialist = new ProfessionalSpecialist();
            specialist.setFirstName("Same");
            specialist.setLastName(prefix);
            specialist.setDeleted(false);
            specialists.persist(specialist);
            expected.add(specialist.getId());
        }
        Set<Integer> actual = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            var page = specialists.findDirectoryPage(null, prefix, "", null, null, true, i);
            assertThat(page.total()).isEqualTo(37);
            assertThat(page.rows()).hasSizeLessThanOrEqualTo(15);
            for (var row : page.rows()) assertThat(actual.add(row.getId())).isTrue();
        }
        assertThat(actual).isEqualTo(expected);
        var last = specialists.findDirectoryPage(null, prefix, "", null, null, true, 999);
        assertThat(last.page()).isEqualTo(2);
        assertThat(last.rows()).hasSize(7);
    }

    @Test
    void shouldApplyDirectoryFiltersAndVisibility_whenSelectingReferralLabels() {
        String marker = "Lab" + Long.toString(System.nanoTime(), 36);
        var specialist = new ProfessionalSpecialist();
        specialist.setLastName(marker);
        specialist.setFirstName("Label");
        specialist.setReferralNo("5317");
        specialist.setSpecialtyType("Cardiology");
        specialist.setStreetAddress("123 Clinic Street");
        specialist.setDeleted(false);
        specialists.persist(specialist);
        entityManager.flush();

        assertThat(specialists.findDirectoryPage("5317", marker, "Lab", "Cardio", "Clinic", false, 0).rows())
                .extracting(ProfessionalSpecialist::getId).containsExactly(specialist.getId());
        assertThat(specialists.findDirectoryPage("5318", marker, "Lab", "Cardio", "Clinic", false, 0).rows()).isEmpty();
        assertThat(specialists.findDirectoryPage("5317", marker + "X", "Lab", "Cardio", "Clinic", false, 0).rows()).isEmpty();
        assertThat(specialists.findDirectoryPage("5317", marker, "Other", "Cardio", "Clinic", false, 0).rows()).isEmpty();
        assertThat(specialists.findDirectoryPage("5317", marker, "Lab", "Neurology", "Clinic", false, 0).rows()).isEmpty();
        assertThat(specialists.findDirectoryPage("5317", marker, "Lab", "Cardio", "Other Street", false, 0).rows()).isEmpty();
        assertThat(specialists.findDirectoryPage(" ", null, null, "", "", false, 0).rows()).isEmpty();
        specialist.setHideFromView(true);
        entityManager.flush();
        assertThat(specialists.findDirectoryPage(null, marker, null, null, null, false, 0).rows()).isEmpty();
        assertThat(specialists.findDirectoryPage(null, marker, null, null, null, true, 0).rows())
                .extracting(ProfessionalSpecialist::getId).containsExactly(specialist.getId());
    }

    @Test
    void shouldKeepFiltersAndAllSortColumns_whenPagingClientSearch() {
        String prefix = "Fake" + Long.toString(System.nanoTime(), 36);
        Set<Integer> expected = new HashSet<>();
        for (int i = 0; i < 23; i++) {
            Demographic patient = new Demographic();
            patient.setFirstName(prefix);
            patient.setLastName("Same");
            patient.setSex("M");
            patient.setPrefName("");
            patient.setYearOfBirth("1990");
            patient.setMonthOfBirth("01");
            patient.setDateOfBirth("01");
            entityManager.persist(patient);
            expected.add(patient.getDemographicNo());
        }
        ClientSearchFormBean criteria = new ClientSearchFormBean();
        criteria.setFirstName(prefix);
        criteria.setSearchOutsideDomain(true);
        Set<Integer> actual = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            var page = demographics.searchClientPage(criteria, i, 1, false);
            assertThat(page.total()).isEqualTo(23);
            assertThat(page.rows()).hasSizeLessThanOrEqualTo(10);
            for (var row : page.rows()) assertThat(actual.add(row.getDemographicNo())).isTrue();
        }
        assertThat(actual).isEqualTo(expected);
        criteria.setFirstName(prefix + "absent");
        assertThat(demographics.searchClientPage(criteria, 999, 1, false).rows()).isEmpty();
    }

    @Test
    void shouldBoundNativeSoundexAndKeepItsFilters_whenPagingClientSearch() {
        String chart = "SX" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Set<Integer> expected = new HashSet<>();
        for (int i = 0; i < 23; i++) {
            Demographic patient = new Demographic();
            patient.setFirstName("Robert");
            patient.setLastName("Synthetic");
            patient.setSex("M");
            patient.setPrefName("");
            patient.setChartNo(chart);
            patient.setYearOfBirth("1990");
            patient.setMonthOfBirth("01");
            patient.setDateOfBirth("01");
            entityManager.persist(patient);
            expected.add(patient.getDemographicNo());
        }
        entityManager.flush();
        ClientSearchFormBean criteria = new ClientSearchFormBean();
        criteria.setFirstName("Rupert");
        criteria.setChartNo(chart);
        criteria.setSearchUsingSoundex(true);
        criteria.setSearchOutsideDomain(true);
        criteria.setActive("0");
        criteria.setGender("M");
        Set<Integer> actual = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            var page = demographics.searchClientPage(criteria, i, 2, false);
            assertThat(page.total()).isEqualTo(23);
            assertThat(page.rows()).hasSizeLessThanOrEqualTo(10);
            for (var row : page.rows()) assertThat(actual.add(row.getDemographicNo())).isTrue();
        }
        assertThat(actual).isEqualTo(expected);
        criteria.setGender("F");
        assertThat(demographics.searchClientPage(criteria, 0, 1, false).total()).isZero();
        criteria.setGender("M");
        criteria.setSearchOutsideDomain(false);
        assertThat(demographics.searchClientPage(criteria, 0, 1, false).total()).isZero();
    }

    @Test
    void shouldKeepSearchValuesBound_whenCriteriaContainSqlSyntax() {
        // Own fixtures, found first by ordinary searches, so the zero results below are the bound
        // literal at work rather than whatever the database happens to hold.
        String prefix = "Sql" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        ProfessionalSpecialist specialist = new ProfessionalSpecialist();
        specialist.setFirstName("Same");
        specialist.setLastName(prefix);
        specialist.setDeleted(false);
        specialists.persist(specialist);
        Demographic patient = new Demographic();
        patient.setFirstName("Robert");
        patient.setLastName(prefix);
        patient.setSex("M");
        patient.setPrefName("");
        patient.setChartNo(prefix);
        patient.setYearOfBirth("1990");
        patient.setMonthOfBirth("01");
        patient.setDateOfBirth("01");
        entityManager.persist(patient);
        entityManager.flush();

        assertThat(specialists.findDirectoryPage(null, prefix, "", null, null, true, 0).rows())
                .extracting(ProfessionalSpecialist::getId).containsExactly(specialist.getId());
        ClientSearchFormBean control = new ClientSearchFormBean();
        control.setChartNo(prefix);
        control.setSearchOutsideDomain(true);
        control.setActive("");
        assertThat(demographics.searchClientPage(control, 0, 2, false).rows())
                .extracting(Demographic::getDemographicNo).containsExactly(patient.getDemographicNo());
        control.setFirstName("Rupert");
        control.setSearchUsingSoundex(true);
        assertThat(demographics.searchClientPage(control, 0, 2, false).rows())
                .extracting(Demographic::getDemographicNo).containsExactly(patient.getDemographicNo());

        // Each literal would match the fixture (or everything) if it were spliced into the SQL.
        String literal = prefix + "' OR 1=1 --";
        assertThat(specialists.findDirectoryPage(null, literal, "", null, null, true, 0).total()).isZero();
        ClientSearchFormBean criteria = new ClientSearchFormBean();
        criteria.setFirstName(literal);
        criteria.setSearchOutsideDomain(true);
        criteria.setActive("");
        assertThat(demographics.searchClientPage(criteria, 0, 2, false).total()).isZero();
        criteria.setFirstName("Rupert");
        criteria.setChartNo(literal);
        criteria.setSearchUsingSoundex(true);
        assertThat(demographics.searchClientPage(criteria, 0, 2, false).total()).isZero();
    }

    private record SortFixture(int id, String last, String first, String birth, String gender,
            String chart, boolean admitted, boolean alert) { }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldOrderEveryColumnAndDirection_withIdTiesAcrossPages(boolean soundex) {
        String prefix = "S" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Program service = new Program();
        service.setName(prefix);
        service.setType("Service");
        service.setFacilityId(1);
        entityManager.persist(service);
        Map<String, String> descriptions = new java.util.HashMap<>();
        for (String code : List.of("M", "F")) {
            descriptions.put(code, (String) entityManager.createNativeQuery(
                    "SELECT description FROM lst_gender WHERE code = :code")
                    .setParameter("code", code).getSingleResult());
        }
        List<SortFixture> fixtures = new ArrayList<>();
        for (int i = 0; i < 23; i++) {
            Demographic patient = new Demographic();
            patient.setFirstName((soundex ? "" : prefix) + (i % 2 == 0 ? "Robert" : "Rupert"));
            patient.setLastName(List.of("Zulu", "Alpha", "Mike").get(i % 3));
            patient.setSex(i % 2 == 0 ? "M" : "F");
            patient.setPrefName("");
            patient.setYearOfBirth(Integer.toString(1990 + i % 3));
            patient.setMonthOfBirth("01");
            patient.setDateOfBirth("01");
            patient.setChartNo(prefix + i % 6);
            entityManager.persist(patient);
            boolean admitted = i % 3 != 0;
            boolean alert = i % 4 != 0;
            if (admitted) {
                Admission admission = new Admission();
                admission.setClientId(patient.getDemographicNo());
                admission.setProgramId(service.getId());
                admission.setProviderNo("999998");
                admission.setAdmissionStatus("current");
                entityManager.persist(admission);
            }
            if (alert) {
                entityManager.createNativeQuery("INSERT INTO health_safety (demographic_no) VALUES (:patient)")
                        .setParameter("patient", patient.getDemographicNo()).executeUpdate();
            }
            fixtures.add(new SortFixture(patient.getDemographicNo(), patient.getLastName().toLowerCase(Locale.ROOT),
                    patient.getFirstName().toLowerCase(Locale.ROOT), patient.getYearOfBirth() + "/01/01",
                    descriptions.get(patient.getSex()), patient.getChartNo(), admitted, alert));
        }
        entityManager.flush();
        entityManager.clear();
        ClientSearchFormBean criteria = new ClientSearchFormBean();
        criteria.setChartNo(prefix);
        criteria.setSearchOutsideDomain(true);
        criteria.setActive("");
        if (soundex) {
            criteria.setFirstName("Rupert");
            criteria.setSearchUsingSoundex(true);
        }
        for (int sort = 0; sort <= 7; sort++) {
            Comparator<SortFixture> primary = switch (sort) {
                case 2 -> Comparator.comparing(SortFixture::last).thenComparing(SortFixture::first);
                case 3 -> Comparator.comparing(SortFixture::birth);
                case 4 -> Comparator.comparing(SortFixture::gender);
                case 5 -> Comparator.comparing(SortFixture::chart);
                case 6 -> Comparator.comparing(SortFixture::admitted);
                case 7 -> Comparator.comparing(SortFixture::alert);
                default -> Comparator.comparingInt(SortFixture::id);
            };
            for (boolean descending : new boolean[]{false, true}) {
                Comparator<SortFixture> ordered = (descending ? primary.reversed() : primary)
                        .thenComparingInt(SortFixture::id);
                List<Integer> expectedOrder = fixtures.stream().sorted(ordered).map(SortFixture::id).toList();
                List<Integer> actualOrder = new ArrayList<>();
                for (int page = 0; page < 3; page++) {
                    var result = demographics.searchClientPage(criteria, page, sort, descending);
                    assertThat(result.total()).isEqualTo(23);
                    assertThat(result.rows()).hasSizeLessThanOrEqualTo(10);
                    result.rows().forEach(row -> actualOrder.add(row.getDemographicNo()));
                }
                assertThat(actualOrder).as("soundex=%s sort=%s descending=%s", soundex, sort, descending)
                        .containsExactlyElementsOf(expectedOrder);
            }
        }
    }

}
