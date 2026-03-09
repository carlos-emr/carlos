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

import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link EFormDataDao} query methods.
 *
 * <p>Validates JPQL queries for electronic form data. Covers demographic-based
 * lookups, form ID filtering, date range queries, and metadata projections.
 * Critical for Hibernate 6 migration due to dynamic query construction and
 * {@code Map} projections.</p>
 *
 * <p>Native SQL methods ({@code findByDemographicIdCurrentAttachedToConsult},
 * {@code findByDemographicIdCurrentAttachedToEForm},
 * {@code getDemographicNosMissingVarName}) require additional tables
 * (ctlDocument, eform_values) not available in test infrastructure.</p>
 *
 * @since 2026-03-04
 * @see EFormDataDao
 * @see EFormDataDaoImpl
 */
@DisplayName("EFormDataDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("eform")
@Transactional
public class EFormDataDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private EFormDataDao eFormDataDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private static final int DEMO_NO = 100;
    private static final int FORM_ID = 1;
    private static final int FORM_ID_2 = 2;
    private static final String PROVIDER_NO = "999001";

    private Date today;
    private Date yesterday;
    private Date lastWeek;
    private Date nextWeek;

    @BeforeEach
    void setUp() {
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.MARCH, 4, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        today = cal.getTime();

        cal.add(Calendar.DAY_OF_MONTH, -1);
        yesterday = cal.getTime();

        cal.setTime(today);
        cal.add(Calendar.DAY_OF_MONTH, -7);
        lastWeek = cal.getTime();

        cal.setTime(today);
        cal.add(Calendar.DAY_OF_MONTH, 7);
        nextWeek = cal.getTime();
    }

    private EFormData createEFormData(int demoId, int formId, boolean current, Date formDate) {
        EFormData efd = new EFormData();
        efd.setDemographicId(demoId);
        efd.setFormId(formId);
        efd.setFormName("TestForm");
        efd.setSubject("Test Subject");
        efd.setCurrent(current);
        efd.setFormDate(formDate);
        efd.setFormTime(formDate);
        efd.setProviderNo(PROVIDER_NO);
        efd.setFormData("<form>test</form>");
        efd.setShowLatestFormOnly(false);
        efd.setPatientIndependent(false);
        efd.setRoleType("");
        return efd;
    }

    private EFormData createAndPersist(int demoId, int formId, boolean current, Date formDate) {
        EFormData efd = createEFormData(demoId, formId, current, formDate);
        entityManager.persist(efd);
        entityManager.flush();
        return efd;
    }

    // ========================================================================
    // findByDemographicId
    // ========================================================================

    @Nested
    @DisplayName("findByDemographicId")
    @Tag("read")
    class FindByDemographicId {

        @Test
        @DisplayName("should return all eform data for demographic")
        void shouldReturnAllEFormData_forDemographic() {
            // Given
            createAndPersist(DEMO_NO, FORM_ID, true, today);
            createAndPersist(DEMO_NO, FORM_ID_2, false, yesterday);

            // When
            List<EFormData> result = eFormDataDao.findByDemographicId(DEMO_NO);

            // Then
            assertThat(result).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("should return empty list for demographic with no eforms")
        void shouldReturnEmpty_whenNoEForms() {
            // When
            List<EFormData> result = eFormDataDao.findByDemographicId(99999);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // findByDemographicIdSinceLastDate
    // ========================================================================

    @Nested
    @DisplayName("findByDemographicIdSinceLastDate")
    @Tag("read")
    class FindByDemographicIdSinceLastDate {

        @Test
        @DisplayName("should return eform data since date")
        void shouldReturnEFormData_sinceDate() {
            // Given
            createAndPersist(DEMO_NO, FORM_ID, true, today);

            // When
            List<EFormData> result = eFormDataDao.findByDemographicIdSinceLastDate(DEMO_NO, yesterday);

            // Then
            assertThat(result).isNotEmpty();
        }
    }

    // ========================================================================
    // findDemographicIdSinceLastDate
    // ========================================================================

    @Nested
    @DisplayName("findDemographicIdSinceLastDate")
    @Tag("read")
    class FindDemographicIdSinceLastDate {

        @Test
        @DisplayName("should return demographic IDs with eforms since date")
        void shouldReturnDemoIds_sinceDate() {
            // Given
            createAndPersist(DEMO_NO, FORM_ID, true, today);

            // When
            List<Integer> result = eFormDataDao.findDemographicIdSinceLastDate(yesterday);

            // Then
            assertThat(result).contains(DEMO_NO);
        }
    }

    // ========================================================================
    // findByFormDataId
    // ========================================================================

    @Nested
    @DisplayName("findByFormDataId")
    @Tag("read")
    class FindByFormDataId {

        @Test
        @DisplayName("should return eform data by ID")
        void shouldReturnEFormData_byId() {
            // Given
            EFormData efd = createAndPersist(DEMO_NO, FORM_ID, true, today);

            // When
            EFormData result = eFormDataDao.findByFormDataId(efd.getId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(efd.getId());
        }

        @Test
        @DisplayName("should return null for non-existent ID")
        void shouldReturnNull_forNonExistentId() {
            // When
            EFormData result = eFormDataDao.findByFormDataId(99999);

            // Then
            assertThat(result).isNull();
        }
    }

    // ========================================================================
    // findByDemographicIdCurrent
    // ========================================================================

    @Nested
    @DisplayName("findByDemographicIdCurrent")
    @Tag("read")
    class FindByDemographicIdCurrent {

        @Test
        @DisplayName("should return current eform data for demographic")
        void shouldReturnCurrentEFormData_forDemographic() {
            // Given
            createAndPersist(DEMO_NO, FORM_ID, true, today);
            createAndPersist(DEMO_NO, FORM_ID_2, false, yesterday);

            // When
            List<EFormData> result = eFormDataDao.findByDemographicIdCurrent(DEMO_NO, true);

            // Then
            assertThat(result).isNotEmpty();
            assertThat(result).allSatisfy(e -> assertThat(e.isCurrent()).isTrue());
        }

        @Test
        @DisplayName("should return non-current eform data when current is false")
        void shouldReturnNonCurrent_whenCurrentIsFalse() {
            // Given
            createAndPersist(DEMO_NO, FORM_ID, false, today);

            // When
            List<EFormData> result = eFormDataDao.findByDemographicIdCurrent(DEMO_NO, false);

            // Then
            assertThat(result).isNotEmpty();
            assertThat(result).allSatisfy(e -> assertThat(e.isCurrent()).isFalse());
        }

        @Test
        @DisplayName("should support pagination")
        void shouldSupportPagination_forCurrentEForms() {
            // Given
            createAndPersist(DEMO_NO, FORM_ID, true, today);
            createAndPersist(DEMO_NO, FORM_ID_2, true, yesterday);

            // When
            List<EFormData> result = eFormDataDao.findByDemographicIdCurrent(DEMO_NO, true, 0, 1);

            // Then
            assertThat(result).hasSize(1);
        }
    }

    // ========================================================================
    // findByDemographicIdCurrentNoData
    // ========================================================================

    @Nested
    @DisplayName("findByDemographicIdCurrentNoData")
    @Tag("read")
    class FindByDemographicIdCurrentNoData {

        @Test
        @DisplayName("should return metadata without form data")
        void shouldReturnMetadata_withoutFormData() {
            // Given
            createAndPersist(DEMO_NO, FORM_ID, true, today);

            // When
            List<Map<String, Object>> result = eFormDataDao.findByDemographicIdCurrentNoData(DEMO_NO, true);

            // Then
            assertThat(result).isNotEmpty();
        }
    }

    // ========================================================================
    // findPatientIndependent
    // ========================================================================

    @Nested
    @DisplayName("findPatientIndependent")
    @Tag("read")
    class FindPatientIndependent {

        @Test
        @DisplayName("should return patient-independent eforms")
        void shouldReturnPatientIndependent_eForms() {
            // Given
            EFormData efd = createEFormData(0, FORM_ID, true, today);
            efd.setPatientIndependent(true);
            entityManager.persist(efd);
            entityManager.flush();

            // When
            List<EFormData> result = eFormDataDao.findPatientIndependent(true);

            // Then
            assertThat(result).isNotEmpty();
            assertThat(result).allSatisfy(e -> assertThat(e.isPatientIndependent()).isTrue());
        }
    }

    // ========================================================================
    // findByFormId
    // ========================================================================

    @Nested
    @DisplayName("findByFormId")
    @Tag("read")
    class FindByFormId {

        @Test
        @DisplayName("should return eform data by form ID")
        void shouldReturnEFormData_byFormId() {
            // Given
            createAndPersist(DEMO_NO, FORM_ID, true, today);

            // When
            List<EFormData> result = eFormDataDao.findByFormId(FORM_ID);

            // Then
            assertThat(result).isNotEmpty();
            assertThat(result).allSatisfy(e -> assertThat(e.getFormId()).isEqualTo(FORM_ID));
        }

        @Test
        @DisplayName("should return empty list for non-existent form ID")
        void shouldReturnEmpty_forNonExistentFormId() {
            // When
            List<EFormData> result = eFormDataDao.findByFormId(99999);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // findDemographicNosByFormId
    // ========================================================================

    @Nested
    @DisplayName("findDemographicNosByFormId")
    @Tag("read")
    class FindDemographicNosByFormId {

        @Test
        @DisplayName("should return demographic numbers by form ID")
        void shouldReturnDemoNos_byFormId() {
            // Given
            createAndPersist(DEMO_NO, FORM_ID, true, today);

            // When
            List<Integer> result = eFormDataDao.findDemographicNosByFormId(FORM_ID);

            // Then
            assertThat(result).contains(DEMO_NO);
        }
    }

    // ========================================================================
    // findAllFdidByFormId
    // ========================================================================

    @Nested
    @DisplayName("findAllFdidByFormId")
    @Tag("read")
    class FindAllFdidByFormId {

        @Test
        @DisplayName("should return all form data IDs by form ID")
        void shouldReturnAllFdids_byFormId() {
            // Given
            EFormData efd = createAndPersist(DEMO_NO, FORM_ID, true, today);

            // When
            List<Integer> result = eFormDataDao.findAllFdidByFormId(FORM_ID);

            // Then
            assertThat(result).contains(efd.getId());
        }
    }

    // ========================================================================
    // findMetaFieldsByFormId (Object[] return)
    // ========================================================================

    @Nested
    @DisplayName("findMetaFieldsByFormId")
    @Tag("read")
    class FindMetaFieldsByFormId {

        @Test
        @DisplayName("should return meta fields as Object arrays")
        void shouldReturnMetaFields_asObjectArrays() {
            // Given
            createAndPersist(DEMO_NO, FORM_ID, true, today);

            // When
            List<Object[]> result = eFormDataDao.findMetaFieldsByFormId(FORM_ID);

            // Then
            assertThat(result).isNotEmpty();
        }
    }

    // ========================================================================
    // findAllCurrentFdidByFormId
    // ========================================================================

    @Nested
    @DisplayName("findAllCurrentFdidByFormId")
    @Tag("read")
    class FindAllCurrentFdidByFormId {

        @Test
        @DisplayName("should return current form data IDs by form ID")
        void shouldReturnCurrentFdids_byFormId() {
            // Given
            EFormData efd = createAndPersist(DEMO_NO, FORM_ID, true, today);
            createAndPersist(DEMO_NO, FORM_ID, false, yesterday);

            // When
            List<Integer> result = eFormDataDao.findAllCurrentFdidByFormId(FORM_ID);

            // Then
            assertThat(result).contains(efd.getId());
        }
    }

    // ========================================================================
    // findByFormIdProviderNo
    // ========================================================================

    @Nested
    @DisplayName("findByFormIdProviderNo")
    @Tag("read")
    class FindByFormIdProviderNo {

        @Test
        @DisplayName("should return eforms by form ID and provider")
        void shouldReturnEForms_byFormIdAndProvider() {
            // Given
            createAndPersist(DEMO_NO, FORM_ID, true, today);

            // When
            List<EFormData> result = eFormDataDao.findByFormIdProviderNo(
                    Arrays.asList(PROVIDER_NO), FORM_ID);

            // Then
            assertThat(result).isNotEmpty();
        }
    }

    // ========================================================================
    // findByDemographicIdAndFormName
    // ========================================================================

    @Nested
    @DisplayName("findByDemographicIdAndFormName")
    @Tag("read")
    class FindByDemographicIdAndFormName {

        @Test
        @DisplayName("should return eforms by demographic and form name")
        void shouldReturnEForms_byDemoAndFormName() {
            // Given
            createAndPersist(DEMO_NO, FORM_ID, true, today);

            // When
            List<EFormData> result = eFormDataDao.findByDemographicIdAndFormName(DEMO_NO, "TestForm");

            // Then
            assertThat(result).isNotEmpty();
        }
    }

    // ========================================================================
    // findByDemographicIdAndFormId
    // ========================================================================

    @Nested
    @DisplayName("findByDemographicIdAndFormId")
    @Tag("read")
    class FindByDemographicIdAndFormId {

        @Test
        @DisplayName("should return eforms by demographic and form ID")
        void shouldReturnEForms_byDemoAndFormId() {
            // Given
            createAndPersist(DEMO_NO, FORM_ID, true, today);

            // When
            List<EFormData> result = eFormDataDao.findByDemographicIdAndFormId(DEMO_NO, FORM_ID);

            // Then
            assertThat(result).isNotEmpty();
        }
    }

    // ========================================================================
    // findByFidsAndDates
    // ========================================================================

    @Nested
    @DisplayName("findByFidsAndDates")
    @Tag("search")
    class FindByFidsAndDates {

        @Test
        @DisplayName("should return eforms by form IDs and date range")
        void shouldReturnEForms_byFidsAndDates() {
            // Given
            createAndPersist(DEMO_NO, FORM_ID, true, today);

            // When
            TreeSet<Integer> fids = new TreeSet<>(Arrays.asList(FORM_ID));
            List<EFormData> result = eFormDataDao.findByFidsAndDates(fids, yesterday, nextWeek);

            // Then
            assertThat(result).isNotEmpty();
        }
    }

    // ========================================================================
    // findByFdids
    // ========================================================================

    @Nested
    @DisplayName("findByFdids")
    @Tag("read")
    class FindByFdids {

        @Test
        @DisplayName("should return eforms by form data IDs")
        void shouldReturnEForms_byFdids() {
            // Given
            EFormData efd = createAndPersist(DEMO_NO, FORM_ID, true, today);

            // When
            List<EFormData> result = eFormDataDao.findByFdids(Arrays.asList(efd.getId()));

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(efd.getId());
        }
    }

    // ========================================================================
    // getLatestFdid (aggregate - MAX)
    // ========================================================================

    @Nested
    @DisplayName("getLatestFdid")
    @Tag("aggregate")
    class GetLatestFdid {

        @Test
        @DisplayName("should return latest form data ID")
        void shouldReturnLatestFdid_forFormAndDemographic() {
            // Given
            createAndPersist(DEMO_NO, FORM_ID, true, yesterday);
            EFormData latest = createAndPersist(DEMO_NO, FORM_ID, true, today);

            // When
            Integer result = eFormDataDao.getLatestFdid(FORM_ID, DEMO_NO);

            // Then
            assertThat(result).isEqualTo(latest.getId());
        }

        @Test
        @DisplayName("should return null when no matching eforms")
        void shouldReturnNull_whenNoMatchingEForms() {
            // When
            Integer result = eFormDataDao.getLatestFdid(99999, 99999);

            // Then
            assertThat(result).isNull();
        }
    }

    // ========================================================================
    // getProvidersForEforms
    // ========================================================================

    @Nested
    @DisplayName("getProvidersForEforms")
    @Tag("read")
    class GetProvidersForEforms {

        @Test
        @DisplayName("should return provider numbers for eform IDs")
        void shouldReturnProviders_forEformIds() {
            // Given
            EFormData efd = createAndPersist(DEMO_NO, FORM_ID, true, today);

            // When
            List<String> result = eFormDataDao.getProvidersForEforms(Arrays.asList(efd.getId()));

            // Then
            assertThat(result).contains(PROVIDER_NO);
        }
    }

    // ========================================================================
    // findemographicIdSinceLastDate
    // ========================================================================

    @Nested
    @DisplayName("findemographicIdSinceLastDate")
    @Tag("read")
    class FinDemographicIdSinceLastDate {

        @Test
        @DisplayName("should return demographic IDs updated since date")
        void shouldReturnDemoIds_updatedSinceDate() {
            // Given
            createAndPersist(DEMO_NO, FORM_ID, true, today);

            // When
            List<Integer> result = eFormDataDao.findemographicIdSinceLastDate(yesterday);

            // Then
            assertThat(result).contains(DEMO_NO);
        }
    }
}
