/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.carlos_emr.carlos.commn.dao.DrugDaoImpl;
import io.github.carlos_emr.carlos.commn.exception.AccessDeniedException;
import io.github.carlos_emr.carlos.commn.model.AbstractModel;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.Prescription;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Unit tests for {@link RxManagerImpl}.
 *
 * <p>Tests prescription management logic including access control,
 * drug CRUD operations, discontinuation with 14 valid reasons,
 * prescription creation, and drug validation.</p>
 *
 * <p>Extends {@link RxManagerImpl} directly to access protected methods
 * and inject mock DAOs without Spring context.</p>
 *
 * @since 2026-03-07
 */
@Tag("unit")
@Tag("manager")
@DisplayName("RxManager")
class RxManagerUnitTest extends RxManagerImpl {

    private Drug mergedDrug = null;
    private int addNewDrugCallCount = 0;

    @BeforeEach
    void setUp() {
        this.drugDao = new MockDrugDao();
        this.securityInfoManager = new TestSecurityInfoManager();
        this.prescriptionManager = new MockPrescriptionManager();
        this.mergedDrug = null;
        this.addNewDrugCallCount = 0;
    }

    @AfterEach
    void tearDown() {
        this.drugDao = null;
        this.mergedDrug = null;
    }

    @Nested
    @DisplayName("readCheck")
    class ReadCheck {

        @Test
        @DisplayName("should pass with allowed demographic")
        void shouldPass_withAllowedDemographic() {
            LoggedInInfo info = new LoggedInInfo();
            readCheck(info, 1);
            // no exception means success
        }

        @Test
        @DisplayName("should throw AccessDeniedException with denied demographic")
        void shouldThrow_withDeniedDemographic() {
            LoggedInInfo info = new LoggedInInfo();
            assertThatThrownBy(() -> readCheck(info, 10))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("writeCheck")
    class WriteCheck {

        @Test
        @DisplayName("should pass with allowed demographic")
        void shouldPass_withAllowedDemographic() {
            LoggedInInfo info = new LoggedInInfo();
            writeCheck(info, 1);
        }

        @Test
        @DisplayName("should throw AccessDeniedException with denied demographic")
        void shouldThrow_withDeniedDemographic() {
            LoggedInInfo info = new LoggedInInfo();
            assertThatThrownBy(() -> writeCheck(info, 6))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("getDrugs")
    class GetDrugs {

        @Test
        @DisplayName("should return all drugs with status ALL")
        void shouldReturnAllDrugs_withStatusAll() {
            LoggedInInfo info = new LoggedInInfo();
            List<Drug> drugs = getDrugs(info, 1, RxManager.ALL);

            assertThat(drugs).hasSize(2);
            assertThat(drugs.get(0).getBrandName()).isEqualTo("Aspirin");
            assertThat(drugs.get(1).getBrandName()).isEqualTo("Tylenol");
        }

        @Test
        @DisplayName("should return only archived drugs with status ARCHIVED")
        void shouldReturnArchivedDrugs_withStatusArchived() {
            LoggedInInfo info = new LoggedInInfo();
            List<Drug> drugs = getDrugs(info, 1, RxManager.ARCHIVED);

            assertThat(drugs).hasSize(1);
            assertThat(drugs.get(0).getBrandName()).isEqualTo("Aspirin");
        }

        @Test
        @DisplayName("should return only current drugs with status CURRENT")
        void shouldReturnCurrentDrugs_withStatusCurrent() {
            LoggedInInfo info = new LoggedInInfo();
            List<Drug> drugs = getDrugs(info, 1, RxManager.CURRENT);

            assertThat(drugs).hasSize(1);
            assertThat(drugs.get(0).getBrandName()).isEqualTo("Tylenol");
        }

        @Test
        @DisplayName("should throw UnsupportedOperationException with invalid status")
        void shouldThrow_withInvalidStatus() {
            LoggedInInfo info = new LoggedInInfo();
            assertThatThrownBy(() -> getDrugs(info, 1, "FOOBAR"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("addDrug")
    class AddDrug {

        @Test
        @DisplayName("should return drug with generated ID for valid drug")
        void shouldReturnDrug_forValidDrug() {
            LoggedInInfo info = new LoggedInInfo();
            Drug drug = new Drug();
            drug.setGenericName("ASA");
            drug.setDemographicId(1);

            Drug result = addDrug(info, drug);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1);
        }

        @Test
        @DisplayName("should return null when DAO rejects drug")
        void shouldReturnNull_whenDaoRejects() {
            LoggedInInfo info = new LoggedInInfo();
            Drug drug = new Drug();
            drug.setGenericName("Foobar");
            drug.setDemographicId(1);

            assertThat(addDrug(info, drug)).isNull();
        }
    }

    @Nested
    @DisplayName("updateDrug")
    class UpdateDrug {

        @Test
        @DisplayName("should archive old drug and return new version")
        void shouldArchiveOldDrug_andReturnNewVersion() {
            LoggedInInfo info = new LoggedInInfo();
            Drug drug = new Drug();
            drug.setDemographicId(1);
            drug.setId(1);
            drug.setGenericName("ASA");

            Drug result = updateDrug(info, drug);

            assertThat(result).isNotNull();
            assertThat(drug.getGenericName()).isEqualTo("ASA");
            assertThat(mergedDrug).isNotNull();
            assertThat(mergedDrug.isArchived()).isTrue();
            assertThat(mergedDrug.getArchivedReason()).isEqualTo("represcribed");
        }

        @Test
        @DisplayName("should return null when add fails")
        void shouldReturnNull_whenAddFails() {
            LoggedInInfo info = new LoggedInInfo();
            Drug drug = new Drug();
            drug.setDemographicId(1);
            drug.setId(1);
            drug.setGenericName("foobar");

            Drug result = updateDrug(info, drug);

            assertThat(result).isNull();
            assertThat(mergedDrug).isNull();
        }
    }

    @Nested
    @DisplayName("discontinue")
    class Discontinue {

        @Test
        @DisplayName("should return false for non-existent drug ID")
        void shouldReturnFalse_forNonExistentDrugId() {
            LoggedInInfo info = new LoggedInInfo();
            assertThat(discontinue(info, 20, 1, "allergy")).isFalse();
        }

        @Test
        @DisplayName("should return false when drug demographic does not match")
        void shouldReturnFalse_whenDemographicMismatch() {
            LoggedInInfo info = new LoggedInInfo();
            assertThat(discontinue(info, 1, 2, "allergy")).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "adverseReaction", "allergy", "discontinuedByAnotherPhysician",
                "drugInteraction", "cost", "deleted", "increasedRiskBenefitRatio",
                "newScientificEvidence", "ineffectiveTreatment", "noLongerNecessary",
                "patientRequest", "simplifyingTreatment", "unknown", "other"
        })
        @DisplayName("should discontinue with valid reason")
        void shouldDiscontinue_withValidReason(String reason) {
            LoggedInInfo info = new LoggedInInfo();

            boolean result = discontinue(info, 1, 1, reason);

            assertThat(result).isTrue();
            assertThat(mergedDrug).isNotNull();
            assertThat(mergedDrug.getArchivedReason()).isEqualTo(reason);
            assertThat(mergedDrug.isArchived()).isTrue();
            assertThat(mergedDrug.getArchivedDate()).isNotNull();
        }

        @Test
        @DisplayName("should throw UnsupportedOperationException for invalid reason")
        void shouldThrow_forInvalidReason() {
            LoggedInInfo info = new LoggedInInfo();
            assertThatThrownBy(() -> discontinue(info, 1, 1, "foobar"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("prescribe")
    class Prescribe {

        @Test
        @DisplayName("should create prescription with single drug")
        void shouldCreatePrescription_withSingleDrug() {
            List<Drug> drugs = new ArrayList<>();
            drugs.add(createTestDrug());
            LoggedInInfo info = new LoggedInInfo();

            PrescriptionDrugs pd = prescribe(info, drugs, 1);

            assertThat(pd).isNotNull();
            assertThat(pd.drugs).hasSize(1);
            assertThat(pd.prescription).isNotNull();
        }

        @Test
        @DisplayName("should create prescription with multiple drugs")
        void shouldCreatePrescription_withMultipleDrugs() {
            List<Drug> drugs = new ArrayList<>();
            drugs.add(createTestDrug());
            drugs.add(createTestDrug());
            drugs.add(createTestDrug());
            LoggedInInfo info = new LoggedInInfo();

            PrescriptionDrugs pd = prescribe(info, drugs, 1);

            assertThat(pd).isNotNull();
            assertThat(pd.drugs).hasSize(3);
            assertThat(pd.prescription).isNotNull();
        }

        @Test
        @DisplayName("should return null when info is null")
        void shouldReturnNull_whenInfoNull() {
            List<Drug> drugs = new ArrayList<>();
            drugs.add(createTestDrug());

            assertThat(prescribe(null, drugs, 1)).isNull();
        }

        @Test
        @DisplayName("should return null when drugs list is null")
        void shouldReturnNull_whenDrugsNull() {
            LoggedInInfo info = new LoggedInInfo();
            assertThat(prescribe(info, null, 1)).isNull();
        }

        @Test
        @DisplayName("should return null when drugs list is empty")
        void shouldReturnNull_whenDrugsEmpty() {
            LoggedInInfo info = new LoggedInInfo();
            assertThat(prescribe(info, new ArrayList<>(), 1)).isNull();
        }

        @Test
        @DisplayName("should return null when demographic number is invalid")
        void shouldReturnNull_whenDemographicInvalid() {
            List<Drug> drugs = new ArrayList<>();
            drugs.add(createTestDrug());
            LoggedInInfo info = new LoggedInInfo();

            assertThat(prescribe(info, drugs, -1)).isNull();
        }

        @Test
        @DisplayName("should return null when drug cannot be prescribed")
        void shouldReturnNull_whenDrugCannotBePrescribed() {
            List<Drug> drugs = new ArrayList<>();
            Drug drug = createTestDrug();
            drug.setProviderNo("");
            drugs.add(drug);
            LoggedInInfo info = new LoggedInInfo();

            assertThat(prescribe(info, drugs, 1)).isNull();
        }

        @Test
        @DisplayName("should throw AccessDeniedException for unauthorized demographic")
        void shouldThrow_forUnauthorizedDemographic() {
            List<Drug> drugs = new ArrayList<>();
            drugs.add(createTestDrug());
            LoggedInInfo info = new LoggedInInfo();

            assertThatThrownBy(() -> prescribe(info, drugs, 10))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("should attempt to add drug when it does not exist in DB")
        void shouldAttemptAdd_whenDrugNotInDb() {
            List<Drug> drugs = new ArrayList<>();
            Drug drug = createTestDrug();
            drug.setId(3); // not in MockDrugDao
            drug.setGenericName("ASA"); // will succeed in addNewDrug
            drugs.add(drug);
            LoggedInInfo info = new LoggedInInfo();

            PrescriptionDrugs pd = prescribe(info, drugs, 1);

            assertThat(pd).isNotNull();
            assertThat(addNewDrugCallCount).isEqualTo(1);
            assertThat(pd.drugs.get(0).getGenericName()).isEqualTo("ASA");
        }

        @Test
        @DisplayName("should return null when adding new drug fails")
        void shouldReturnNull_whenAddingNewDrugFails() {
            List<Drug> drugs = new ArrayList<>();
            Drug drug = createTestDrug();
            drug.setId(3); // not in MockDrugDao
            drug.setGenericName("NOT ASA"); // will fail in addNewDrug
            drugs.add(drug);
            LoggedInInfo info = new LoggedInInfo();

            PrescriptionDrugs pd = prescribe(info, drugs, 1);

            assertThat(pd).isNull();
            assertThat(addNewDrugCallCount).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("canPrescribe")
    class CanPrescribe {

        @Test
        @DisplayName("should return true for valid drug")
        void shouldReturnTrue_forValidDrug() {
            assertThat(canPrescribe(createTestDrug())).isTrue();
        }

        @Test
        @DisplayName("should return false for null drug")
        void shouldReturnFalse_forNullDrug() {
            assertThat(canPrescribe(null)).isFalse();
        }

        @Test
        @DisplayName("should return false when provider is null")
        void shouldReturnFalse_whenProviderNull() {
            Drug drug = createTestDrug();
            drug.setProviderNo(null);
            assertThat(canPrescribe(drug)).isFalse();
        }

        @Test
        @DisplayName("should return false when provider is empty")
        void shouldReturnFalse_whenProviderEmpty() {
            Drug drug = createTestDrug();
            drug.setProviderNo("");
            assertThat(canPrescribe(drug)).isFalse();
        }

        @Test
        @DisplayName("should return false when demographic is null")
        void shouldReturnFalse_whenDemographicNull() {
            Drug drug = createTestDrug();
            drug.setDemographicId(null);
            assertThat(canPrescribe(drug)).isFalse();
        }

        @Test
        @DisplayName("should return false when demographic is negative")
        void shouldReturnFalse_whenDemographicNegative() {
            Drug drug = createTestDrug();
            drug.setDemographicId(-1);
            assertThat(canPrescribe(drug)).isFalse();
        }

        @Test
        @DisplayName("should return false when rx date is null")
        void shouldReturnFalse_whenRxDateNull() {
            Drug drug = createTestDrug();
            drug.setRxDate(null);
            assertThat(canPrescribe(drug)).isFalse();
        }

        @Test
        @DisplayName("should return false when end date is null")
        void shouldReturnFalse_whenEndDateNull() {
            Drug drug = createTestDrug();
            drug.setEndDate(null);
            assertThat(canPrescribe(drug)).isFalse();
        }

        @Test
        @DisplayName("should return false when start date is after end date")
        void shouldReturnFalse_whenStartAfterEnd() {
            Drug drug = createTestDrug();
            drug.setEndDate(new Date(100000000));
            drug.setRxDate(new Date(200000000));
            assertThat(canPrescribe(drug)).isFalse();
        }

        @Test
        @DisplayName("should return false when instructions are null")
        void shouldReturnFalse_whenInstructionsNull() {
            Drug drug = createTestDrug();
            drug.setSpecial(null);
            assertThat(canPrescribe(drug)).isFalse();
        }

        @Test
        @DisplayName("should return false when instructions are empty")
        void shouldReturnFalse_whenInstructionsEmpty() {
            Drug drug = createTestDrug();
            drug.setSpecial("");
            assertThat(canPrescribe(drug)).isFalse();
        }
    }

    @Nested
    @DisplayName("getHistory")
    class GetHistory {

        @Test
        @DisplayName("should return history for valid drug")
        void shouldReturnHistory_forValidDrug() {
            LoggedInInfo info = new LoggedInInfo();
            List<Drug> drugs = getHistory(1, info, 1);

            assertThat(drugs).isNotNull();
            assertThat(drugs).hasSize(1);
        }

        @Test
        @DisplayName("should return empty list for invalid drug ID")
        void shouldReturnEmptyList_forInvalidDrugId() {
            LoggedInInfo info = new LoggedInInfo();
            List<Drug> drugs = getHistory(6, info, 1);

            assertThat(drugs).isNotNull();
            assertThat(drugs).isEmpty();
        }

        @Test
        @DisplayName("should deny access for unauthorized demographic")
        void shouldDenyAccess_forUnauthorizedDemographic() {
            LoggedInInfo info = new LoggedInInfo();
            assertThatThrownBy(() -> getHistory(1, info, 6))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    // =========== HELPER METHODS =================

    private Drug createTestDrug() {
        Drug drug = new Drug();
        drug.setId(1);
        drug.setDemographicId(1);
        drug.setProviderNo("1");
        drug.setBrandName("Aspirin");
        drug.setGenericName("ASA");
        drug.setRegionalIdentifier("12345");
        drug.setAtc("abcde");
        drug.setTakeMax(2);
        drug.setTakeMin(1);
        drug.setRxDate(new Date(1704067200000L));
        drug.setEndDate(new Date(1706745600000L));
        drug.setFreqCode("BID");
        drug.setDuration("28");
        drug.setDurUnit("D");
        drug.setRoute("PO");
        drug.setDrugForm("TAB");
        drug.setPrn(true);
        drug.setMethod("Take");
        drug.setRepeat(5);
        drug.setSpecial("some string");
        drug.setArchived(false);
        drug.setArchivedDate(new Date(1704067200000L));
        drug.setArchivedReason("reason");
        return drug;
    }

    private Prescription createTestPrescription() {
        Prescription p = new Prescription();
        p.setDemographicId(1);
        p.setProviderNo("1");
        p.setTextView("PRESCRIPTION TEXT");
        p.setDatePrescribed(new Date(1704067200000L));
        p.setComments("COMMENT TEXT");
        return p;
    }

    // =========== MOCK CLASSES =================

    /**
     * Mock security manager that grants access for demographicNo < 5.
     */
    private static class TestSecurityInfoManager extends SecurityInfoManagerImpl {
        @Override
        public boolean hasPrivilege(LoggedInInfo loggedInInfo, String objectName,
                                    String privilege, int demographicNo) {
            return (demographicNo < 5);
        }
    }

    /**
     * Mock prescription manager that creates prescriptions for demographicNo <= 10.
     */
    private class MockPrescriptionManager extends PrescriptionManagerImpl {
        @Override
        public Prescription createNewPrescription(LoggedInInfo info,
                                                  List<Drug> drugs, Integer demographicNo) {
            if (demographicNo > 10) return null;
            return createTestPrescription();
        }
    }

    /**
     * Mock drug DAO with two test drugs: Aspirin (archived) and Tylenol (current).
     */
    private class MockDrugDao extends DrugDaoImpl {
        private final List<Drug> drugs;

        MockDrugDao() {
            drugs = new ArrayList<>();

            Drug d1 = new Drug();
            d1.setId(1);
            d1.setDemographicId(1);
            d1.setGenericName("ASA");
            d1.setBrandName("Aspirin");
            d1.setArchived(true);
            d1.setArchivedDate(new Date(1704067200000L));
            d1.setArchivedReason("allergy");
            drugs.add(d1);

            Drug d2 = new Drug();
            d2.setId(2);
            d2.setDemographicId(1);
            d2.setGenericName("Acetaminophen");
            d2.setBrandName("Tylenol");
            d2.setArchived(false);
            drugs.add(d2);
        }

        @Override
        public List<Drug> findByDemographicId(Integer demographicId) {
            return this.drugs;
        }

        @Override
        public List<Drug> findByDemographicId(Integer demographicId, Boolean archived) {
            List<Drug> result = new ArrayList<>();
            for (Drug d : this.drugs) {
                if (d.isArchived() == archived) {
                    result.add(d);
                }
            }
            return result;
        }

        @Override
        public List<Drug> findByDemographicIdAndDrugId(int demographicNo, Integer drugId) {
            if (drugId > 5) {
                return new ArrayList<>();
            }
            List<Drug> result = new ArrayList<>();
            Drug d = createTestDrug();
            d.setId(drugId);
            d.setDemographicId(demographicNo);
            result.add(d);
            return result;
        }

        @Override
        public List<Drug> findByDemographicIdAndAtc(int demographicNo, String atc) {
            return new ArrayList<>();
        }

        @Override
        public boolean addNewDrug(Drug d) {
            addNewDrugCallCount++;
            if ("ASA".equals(d.getGenericName())) {
                d.setId(1);
                return true;
            }
            return false;
        }

        @Override
        public void persist(AbstractModel<?> o) {
        }

        @Override
        public Drug find(Object i) {
            int id = (Integer) i;
            for (Drug d : this.drugs) {
                if (id == d.getId()) return d;
            }
            return null;
        }

        @Override
        public Drug find(int i) {
            for (Drug d : this.drugs) {
                if (i == d.getId()) return d;
            }
            return null;
        }

        @Override
        public void merge(AbstractModel<?> o) {
            mergedDrug = (Drug) o;
        }
    }
}
