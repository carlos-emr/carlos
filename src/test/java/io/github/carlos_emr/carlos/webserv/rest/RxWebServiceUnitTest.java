/**
 * Copyright (c) 2013-2015. Department of Computer Science, University of Victoria. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Computer Science
 * LeadLab
 * University of Victoria
 * Victoria, Canada
 *
 * <p>
 * Migrated from legacy JUnit 4 RxWebServiceTest to JUnit 5 for the CARLOS EMR project (2026).
 */
package io.github.carlos_emr.carlos.webserv.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.naming.OperationNotSupportedException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.exception.AccessDeniedException;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.Prescription;
import io.github.carlos_emr.carlos.managers.RxManager;
import io.github.carlos_emr.carlos.managers.RxManagerImpl;
import io.github.carlos_emr.carlos.managers.SecurityInfoManagerImpl;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.conversion.ConversionException;
import io.github.carlos_emr.carlos.webserv.rest.conversion.DrugConverterImpl;
import io.github.carlos_emr.carlos.webserv.rest.conversion.PrescriptionConverterImpl;
import io.github.carlos_emr.carlos.webserv.rest.to.DrugResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.DrugSearchResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.RestResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.PrescriptionResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DrugTo1;

/**
 * Unit tests for {@link RxWebService}.
 *
 * <p>Tests the REST prescription web service using mock implementations of
 * RxManager, DrugConverter, and SecurityInfoManager. Covers drug listing,
 * adding, updating, discontinuing, prescribing, and history operations.
 * Migrated from legacy JUnit 4 RxWebServiceTest.
 *
 * @since 2026-03-07
 */
@Tag("unit")
@Tag("prescription")
@DisplayName("RxWebService unit tests")
class RxWebServiceUnitTest {

    private RxWebService service;

    @BeforeEach
    void setUp() {
        service = new TestableRxWebService();
    }

    private DrugTo1 createTestTransferObject() {
        DrugTo1 t = new DrugTo1();
        Date startDate = new Date();
        Date endDate = new Date();
        Date archivedDate = new Date();

        t.setDrugId(1);
        t.setDemographicNo(1);
        t.setProviderNo("1");
        t.setGenericName("bangbar");
        t.setBrandName("foobar");
        t.setRegionalIdentifier("12345");
        t.setAtc("abcde");
        t.setTakeMax((float) 2.0);
        t.setTakeMin((float) 1.0);
        t.setRxDate((Date) startDate.clone());
        t.setEndDate((Date) endDate.clone());
        t.setFrequency("BID");
        t.setDuration(28);
        t.setDurationUnit("D");
        t.setRoute("PO");
        t.setForm("TAB");
        t.setPrn(false);
        t.setMethod("take");
        t.setRepeats(5);
        t.setInstructions("some string");
        t.setArchived(false);
        t.setArchivedReason("reason");
        t.setArchivedDate((Date) archivedDate.clone());
        t.setNoSubstitutions(false);
        t.setLongTerm(false);
        t.setExternalProvider(null);
        return t;
    }

    private Drug createTestDrug() {
        Date startDate = new Date();
        Date endDate = new Date();
        Date archivedDate = new Date();

        Drug d = new Drug();
        d.setId(1);
        d.setDemographicId(1);
        d.setProviderNo("1");
        d.setBrandName("Foobar");
        d.setGenericName("Barbang");
        d.setRegionalIdentifier("12345");
        d.setAtc("abcde");
        d.setTakeMax(2);
        d.setTakeMin(1);
        d.setRxDate((Date) startDate.clone());
        d.setEndDate((Date) endDate.clone());
        d.setFreqCode("BID");
        d.setDuration("28");
        d.setDurUnit("D");
        d.setRoute("PO");
        d.setDrugForm("TAB");
        d.setPrn(true);
        d.setMethod("Take");
        d.setRepeat(5);
        d.setSpecial("some string");
        d.setArchived(false);
        d.setArchivedDate((Date) archivedDate.clone());
        d.setArchivedReason("reason");
        return d;
    }

    /** Tests for drug listing operations. */
    @Nested
    @DisplayName("Drug listing")
    class DrugListing {

        @Test
        @DisplayName("should return all drugs when no status filter specified")
        void shouldReturnAllDrugs_whenNoStatusFilter() throws OperationNotSupportedException {
            DrugSearchResponse resp = service.drugs(1, null);
            assertThat(resp.getContent()).hasSize(2);
            assertThat(resp.getContent().get(0).getBrandName()).isEqualTo("Aspirin");
            assertThat(resp.getContent().get(0).getGenericName()).isEqualTo("ASA");
            assertThat(resp.getContent().get(0).getDrugId()).isEqualTo(1);
            assertThat(resp.getContent().get(1).getBrandName()).isEqualTo("Tylenol");
            assertThat(resp.getContent().get(1).getGenericName()).isEqualTo("Acetaminophen");
            assertThat(resp.getContent().get(1).getDrugId()).isEqualTo(2);
        }

        @Test
        @DisplayName("should return only current drugs when status is CURRENT")
        void shouldReturnCurrentDrugs_whenStatusIsCurrent() throws OperationNotSupportedException {
            DrugSearchResponse resp = service.drugs(1, RxManager.CURRENT);
            assertThat(resp.getContent()).hasSize(1);
            assertThat(resp.getContent().get(0).getBrandName()).isEqualTo("Tylenol");
            assertThat(resp.getContent().get(0).getGenericName()).isEqualTo("Acetaminophen");
            assertThat(resp.getContent().get(0).getDrugId()).isEqualTo(2);
        }

        @Test
        @DisplayName("should return only archived drugs when status is ARCHIVED")
        void shouldReturnArchivedDrugs_whenStatusIsArchived() throws OperationNotSupportedException {
            DrugSearchResponse resp = service.drugs(1, RxManager.ARCHIVED);
            assertThat(resp.getContent()).hasSize(1);
            assertThat(resp.getContent().get(0).getBrandName()).isEqualTo("Aspirin");
            assertThat(resp.getContent().get(0).getGenericName()).isEqualTo("ASA");
            assertThat(resp.getContent().get(0).getDrugId()).isEqualTo(1);
        }

        @Test
        @DisplayName("should throw OperationNotSupportedException for invalid status")
        void shouldThrowException_whenInvalidStatus() {
            assertThatThrownBy(() -> service.drugs(1, "foobar"))
                    .isInstanceOf(OperationNotSupportedException.class);
        }

        @Test
        @DisplayName("should throw RuntimeException when privilege check fails")
        void shouldThrowRuntimeException_whenPrivilegeCheckFails() {
            assertThatThrownBy(() -> service.drugs(6, null))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    /** Tests for add drug operations. */
    @Nested
    @DisplayName("Add drug")
    class AddDrug {

        @Test
        @DisplayName("should succeed with valid input")
        void shouldSucceed_withValidInput() {
            DrugTo1 t = createTestTransferObject();
            DrugResponse resp = service.addDrug(t, 1);
            assertThat(resp.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("should deny access when demographic exceeds privilege limit")
        void shouldDenyAccess_whenDemographicExceedsPrivilegeLimit() {
            DrugTo1 t = createTestTransferObject();
            assertThatThrownBy(() -> service.addDrug(t, 10))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("should fail when drug ID causes manager to reject")
        void shouldFail_whenDrugIdCausesManagerToReject() {
            DrugTo1 t = createTestTransferObject();
            t.setDrugId(2);
            DrugResponse resp = service.addDrug(t, 1);
            assertThat(resp.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("should handle conversion exception for invalid transfer object")
        void shouldHandleConversionException_forInvalidTransferObject() {
            DrugTo1 t = createTestTransferObject();
            t.setDrugId(3);
            DrugResponse resp = service.addDrug(t, 1);
            assertThat(resp.isSuccess()).isFalse();
        }
    }

    /** Tests for update drug operations. */
    @Nested
    @DisplayName("Update drug")
    class UpdateDrug {

        @Test
        @DisplayName("should succeed with valid drug ID")
        void shouldSucceed_withValidDrugId() {
            DrugTo1 t = createTestTransferObject();
            t.setDrugId(1);
            DrugResponse r = service.updateDrug(t, 1);
            assertThat(r).isNotNull();
            assertThat(r.isSuccess()).isTrue();
            assertThat(r.getDrug()).isNotNull();
            assertThat(r.getDrug().getGenericName()).isEqualTo("bangbar");
        }

        @Test
        @DisplayName("should deny access when demographic exceeds privilege limit")
        void shouldDenyAccess_whenDemographicExceedsPrivilegeLimit() {
            DrugTo1 t = createTestTransferObject();
            assertThatThrownBy(() -> service.updateDrug(t, 6))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("should return unsuccessful response for invalid drug ID")
        void shouldReturnUnsuccessful_forInvalidDrugId() {
            DrugTo1 t = createTestTransferObject();
            t.setDrugId(2);
            DrugResponse r = service.updateDrug(t, 1);
            assertThat(r).isNotNull();
            assertThat(r.isSuccess()).isFalse();
            assertThat(r.getDrug()).isNull();
        }

        @Test
        @DisplayName("should handle poorly formed transfer object gracefully")
        void shouldHandlePoorlyFormedTransferObject_gracefully() {
            DrugTo1 t = createTestTransferObject();
            t.setDuration(null);
            DrugResponse r = service.updateDrug(t, 1);
            assertThat(r).isNotNull();
        }
    }

    /** Tests for discontinue drug operations. */
    @Nested
    @DisplayName("Discontinue drug")
    class DiscontinueDrug {

        @Test
        @DisplayName("should succeed with valid drug and demographic IDs")
        void shouldSucceed_withValidIds() {
            RestResponse<String> r = service.discontinueDrug(1, "deleted", 1);
            assertThat(r).isNotNull();
            assertThat(r.getStatus()).isEqualTo(io.github.carlos_emr.carlos.webserv.rest.to.GenericRestResponse.ResponseStatus.SUCCESS);
        }

        @Test
        @DisplayName("should fail when drug or demographic ID is invalid")
        void shouldFail_whenIdsAreInvalid() {
            RestResponse<String> r = service.discontinueDrug(2, "delete", 1);
            assertThat(r).isNotNull();
            assertThat(r.getStatus()).isEqualTo(io.github.carlos_emr.carlos.webserv.rest.to.GenericRestResponse.ResponseStatus.ERROR);
        }

        @Test
        @DisplayName("should deny access for unauthorized demographic")
        void shouldDenyAccess_forUnauthorizedDemographic() {
            assertThatThrownBy(() -> service.discontinueDrug(1, "delete", 20))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    /** Tests for prescribe operations. */
    @Nested
    @DisplayName("Prescribe")
    class Prescribe {

        @Test
        @DisplayName("should succeed with single valid drug")
        void shouldSucceed_withSingleValidDrug() {
            List<DrugTo1> toPrescribe = new ArrayList<>();
            toPrescribe.add(createTestTransferObject());
            PrescriptionResponse resp = service.prescribe(toPrescribe, 1);
            assertThat(resp.isSuccess()).isTrue();
            assertThat(resp.getDrugs()).isNotNull().hasSize(1);
            assertThat(resp.getDrugs().get(0).getDemographicNo()).isEqualTo(1);
            assertThat(resp.getDrugs().get(0).getBrandName()).isEqualTo("foobar");
            assertThat(resp.getPrescription()).isNotNull();
            assertThat(resp.getPrescription().getProviderNo()).isEqualTo(1);
            assertThat(resp.getPrescription().getDemographicNo()).isEqualTo(1);
            assertThat(resp.getPrescription().getTextView()).isEqualTo("SOME TEXT");
        }

        @Test
        @DisplayName("should succeed with multiple drugs")
        void shouldSucceed_withMultipleDrugs() {
            List<DrugTo1> toPrescribe = new ArrayList<>();
            toPrescribe.add(createTestTransferObject());
            toPrescribe.add(createTestTransferObject());
            PrescriptionResponse resp = service.prescribe(toPrescribe, 1);
            assertThat(resp.isSuccess()).isTrue();
            assertThat(resp.getDrugs()).hasSize(2);
            assertThat(resp.getPrescription()).isNotNull();
            assertThat(resp.getPrescription().getProviderNo()).isEqualTo(1);
            assertThat(resp.getPrescription().getDemographicNo()).isEqualTo(1);
            assertThat(resp.getPrescription().getTextView()).isEqualTo("SOME TEXT");
        }

        @Test
        @DisplayName("should fail for empty drug list")
        void shouldFail_forEmptyDrugList() {
            List<DrugTo1> toPrescribe = new ArrayList<>();
            PrescriptionResponse resp = service.prescribe(toPrescribe, 1);
            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getMessage()).isNotNull();
        }

        @Test
        @DisplayName("should handle failed conversion gracefully")
        void shouldHandleFailedConversion_gracefully() {
            List<DrugTo1> toPrescribe = new ArrayList<>();
            toPrescribe.add(createTestTransferObject());
            toPrescribe.get(0).setDrugId(5);
            PrescriptionResponse resp = service.prescribe(toPrescribe, 1);
            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getMessage()).isNotNull();
        }

        @Test
        @DisplayName("should deny access for unauthorized demographic")
        void shouldDenyAccess_forUnauthorizedDemographic() {
            assertThatThrownBy(() -> service.prescribe(null, 20))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("should handle null drug list parameter")
        void shouldHandleNullDrugList_gracefully() {
            PrescriptionResponse resp = service.prescribe(null, 1);
            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getMessage()).isNotNull();
        }

        @Test
        @DisplayName("should handle invalid negative demographic parameter")
        void shouldHandleInvalidDemographic_whenNegative() {
            List<DrugTo1> toPrescribe = new ArrayList<>();
            toPrescribe.add(createTestTransferObject());
            PrescriptionResponse resp = service.prescribe(toPrescribe, -1);
            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getMessage()).isNotNull();
        }

        @Test
        @DisplayName("should handle failure to create prescription")
        void shouldHandleFailure_toCreatePrescription() {
            List<DrugTo1> toPrescribe = new ArrayList<>();
            toPrescribe.add(createTestTransferObject());
            toPrescribe.get(0).setDrugId(2);
            PrescriptionResponse resp = service.prescribe(toPrescribe, 1);
            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getMessage()).isNotNull();
        }
    }

    /** Tests for drug history operations. */
    @Nested
    @DisplayName("Drug history")
    class DrugHistory {

        @Test
        @DisplayName("should return drug history for valid drug ID")
        void shouldReturnHistory_forValidDrugId() {
            DrugSearchResponse resp = service.history(1, 1);
            assertThat(resp).isNotNull();
            assertThat(resp.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("should return empty history for unknown drug ID")
        void shouldReturnEmptyHistory_forUnknownDrugId() {
            DrugSearchResponse resp = service.history(6, 1);
            assertThat(resp).isNotNull();
            assertThat(resp.getContent()).isEmpty();
        }

        @Test
        @DisplayName("should deny access for unauthorized demographic")
        void shouldDenyAccess_forUnauthorizedDemographic() {
            assertThatThrownBy(() -> service.history(1, 6))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    // ============ MOCK Testing Sub Classes =========

    static class MockRxManager extends RxManagerImpl {

        private final RxWebServiceUnitTest testInstance;
        protected List<Drug> drugs;
        Drug d;

        MockRxManager(RxWebServiceUnitTest testInstance) {
            this.testInstance = testInstance;
            drugs = new ArrayList<>();

            d = new Drug();
            d.setId(1);
            d.setGenericName("ASA");
            d.setBrandName("Aspirin");
            d.setProviderNo("1");
            d.setDuration("28");
            d.setArchived(true);
            d.setArchivedDate(new Date());
            d.setArchivedReason("allergy");
            drugs.add(d);

            d = new Drug();
            d.setId(2);
            d.setGenericName("Acetaminophen");
            d.setBrandName("Tylenol");
            d.setProviderNo("1");
            d.setDuration("28");
            d.setArchived(false);
            drugs.add(d);
        }

        @Override
        public List<Drug> getDrugs(LoggedInInfo info, int demographicNo, String status)
                throws UnsupportedOperationException {
            if (status.equals(RxManager.ALL)) return getAllDrugs(info, demographicNo);
            else if (status.equals(RxManager.CURRENT)) return getCurrentDrugs(info, demographicNo);
            else if (status.equals(RxManager.ARCHIVED)) return getArchivedDrugs(info, demographicNo);
            else return null;
        }

        private List<Drug> getAllDrugs(LoggedInInfo info, int id) {
            return this.drugs;
        }

        private List<Drug> getCurrentDrugs(LoggedInInfo info, int id) {
            List<Drug> toReturn = new ArrayList<>();
            for (Drug d : this.drugs) {
                if (!d.isArchived()) toReturn.add(d);
            }
            return toReturn;
        }

        private List<Drug> getArchivedDrugs(LoggedInInfo info, int id) {
            List<Drug> toReturn = new ArrayList<>();
            for (Drug d : this.drugs) {
                if (d.isArchived()) toReturn.add(d);
            }
            return toReturn;
        }

        @Override
        public Drug addDrug(LoggedInInfo info, Drug d) {
            if (d.getId() == 1) return d;
            return null;
        }

        @Override
        public Drug updateDrug(LoggedInInfo info, Drug d) {
            if (d.getId() == 1) return d;
            return null;
        }

        @Override
        public boolean discontinue(LoggedInInfo i, int drugId, int demo, String reason) {
            return drugId == 1 && demo == 1;
        }

        @Override
        public PrescriptionDrugs prescribe(LoggedInInfo info, List<Drug> drugs, Integer demoNo) {
            if (drugs.get(0).getId() > 1) return null;
            Prescription p = new Prescription();
            p.setProviderNo("1");
            p.setDemographicId(1);
            p.setTextView("SOME TEXT");
            return new PrescriptionDrugs(p, drugs);
        }

        @Override
        public List<Drug> getHistory(Integer id, LoggedInInfo info, Integer demographicNo) {
            if (id > 5) {
                return new ArrayList<>();
            } else {
                List<Drug> toReturn = new ArrayList<>();
                Drug d = testInstance.createTestDrug();
                toReturn.add(d);
                return toReturn;
            }
        }
    }

    static class MockDrugConverter extends DrugConverterImpl {

        MockDrugConverter() {
            super();
        }

        @Override
        public Drug getAsDomainObject(LoggedInInfo info, DrugTo1 t) {
            if (t.getDrugId() > 2) {
                throw new ConversionException("Test conversion exception");
            } else {
                return super.getAsDomainObject(info, t);
            }
        }
    }

    /** Mock SecurityInfoManager that returns true for demographicNo < 5. */
    static class TestMockSecurityInfoManager extends SecurityInfoManagerImpl {

        @Override
        public boolean hasPrivilege(LoggedInInfo loggedInInfo, String objectName, String privilege, int demographicNo) {
            return demographicNo < 5;
        }
    }

    class TestableRxWebService extends RxWebService {

        TestableRxWebService() {
            super();
            this.rxManager = new MockRxManager(RxWebServiceUnitTest.this);
            this.drugConverter = new MockDrugConverter();
            this.securityInfoManager = new TestMockSecurityInfoManager();
            this.prescriptionConverter = new PrescriptionConverterImpl();
        }

        @Override
        protected LoggedInInfo getLoggedInInfo() {
            return null;
        }
    }
}
