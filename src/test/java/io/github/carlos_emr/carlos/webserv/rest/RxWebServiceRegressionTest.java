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
package io.github.carlos_emr.carlos.webserv.rest;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.Prescription;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.RxManager;
import io.github.carlos_emr.carlos.managers.RxManagerImpl;
import io.github.carlos_emr.carlos.managers.SecurityInfoManagerImpl;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.conversion.ConversionException;
import io.github.carlos_emr.carlos.webserv.rest.conversion.DrugConverterImpl;
import io.github.carlos_emr.carlos.webserv.rest.conversion.PrescriptionConverterImpl;
import io.github.carlos_emr.carlos.webserv.rest.to.DrugResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.DrugSearchResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DrugTo1;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.naming.OperationNotSupportedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for Rx REST service exception handling contracts.
 *
 * @since 2026-05-07
 */
@DisplayName("RxWebService REST exception regression tests")
@Tag("integration")
@Tag("rest")
@Tag("regression")
class RxWebServiceRegressionTest {

    private static final String TEST_PROVIDER_NUMBER = "999998";

    private RxWebService service;

    @BeforeEach
    void setUp() {
        service = new TestableRxWebService();
    }

    @Test
    @DisplayName("should return drug list for valid request")
    void shouldReturnDrugList_forValidRequest() throws OperationNotSupportedException {
        DrugSearchResponse response = service.drugs(1);

        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getContent()).extracting("brandName").containsExactly("Aspirin", "Tylenol");
    }

    @Test
    @DisplayName("should return success false for conversion error")
    void shouldReturnSuccessFalse_forConversionError() {
        DrugTo1 drug = createDrugTransfer(3);

        DrugResponse response = service.addDrug(drug, 1);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).contains("Could not convert");
    }

    @Test
    @DisplayName("should return success false when add drug fails")
    void shouldReturnSuccessFalse_whenAddDrugFails() {
        DrugTo1 drug = createDrugTransfer(2);

        DrugResponse response = service.addDrug(drug, 1);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).contains("Could not add");
    }

    private static DrugTo1 createDrugTransfer(int drugId) {
        DrugTo1 drug = new DrugTo1();
        Date today = new Date();
        drug.setDrugId(drugId);
        drug.setDemographicNo(1);
        drug.setProviderNo("1");
        drug.setGenericName("acetaminophen");
        drug.setBrandName("Tylenol");
        drug.setRegionalIdentifier("12345");
        drug.setAtc("N02BE01");
        drug.setTakeMax(2.0F);
        drug.setTakeMin(1.0F);
        drug.setRxDate(today);
        drug.setEndDate(today);
        drug.setFrequency("BID");
        drug.setDuration(7);
        drug.setDurationUnit("D");
        drug.setRoute("PO");
        drug.setForm("TAB");
        drug.setMethod("take");
        drug.setRepeats(0);
        drug.setInstructions("as directed");
        return drug;
    }

    private static Drug createDrug(int drugId, String brandName, boolean archived) {
        Drug drug = new Drug();
        drug.setId(drugId);
        drug.setDemographicId(1);
        drug.setProviderNo("1");
        drug.setGenericName(brandName.toLowerCase());
        drug.setBrandName(brandName);
        drug.setDuration("7");
        drug.setArchived(archived);
        return drug;
    }

    private static class MockRxManager extends RxManagerImpl {

        private final List<Drug> drugs = List.of(
                createDrug(1, "Aspirin", true),
                createDrug(2, "Tylenol", false));

        @Override
        public List<Drug> getDrugs(LoggedInInfo info, int demographicNo, String status) {
            if (RxManager.ALL.equals(status)) {
                return drugs;
            }
            return drugs.stream().filter(drug -> RxManager.ARCHIVED.equals(status) == drug.isArchived()).toList();
        }

        @Override
        public Drug addDrug(LoggedInInfo info, Drug drug) {
            return drug.getId() == 1 ? drug : null;
        }

        @Override
        public PrescriptionDrugs prescribe(LoggedInInfo info, List<Drug> drugs, Integer demoNo) {
            Prescription prescription = new Prescription();
            prescription.setProviderNo("1");
            prescription.setDemographicId(demoNo);
            return new PrescriptionDrugs(prescription, new ArrayList<>(drugs));
        }
    }

    private static class MockDrugConverter extends DrugConverterImpl {

        @Override
        public Drug getAsDomainObject(LoggedInInfo info, DrugTo1 transferObject) {
            if (transferObject.getDrugId() > 2) {
                throw new ConversionException("invalid drug");
            }
            return super.getAsDomainObject(info, transferObject);
        }
    }

    private static class PermissiveSecurityInfoManager extends SecurityInfoManagerImpl {

        @Override
        public boolean hasPrivilege(LoggedInInfo loggedInInfo, String objectName, String privilege, int demographicNo) {
            return true;
        }
    }

    private static class TestableRxWebService extends RxWebService {

        TestableRxWebService() {
            rxManager = new MockRxManager();
            drugConverter = new MockDrugConverter();
            prescriptionConverter = new PrescriptionConverterImpl();
            securityInfoManager = new PermissiveSecurityInfoManager();
        }

        @Override
        protected LoggedInInfo getLoggedInInfo() {
            LoggedInInfo loggedInInfo = new LoggedInInfo();
            loggedInInfo.setLoggedInProvider(new Provider(TEST_PROVIDER_NUMBER, "Regression", "doctor", "U",
                    "general", "Rest"));
            return loggedInInfo;
        }
    }
}
