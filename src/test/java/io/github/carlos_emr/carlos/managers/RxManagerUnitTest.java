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
import org.junit.jupiter.params.provider.CsvSource;
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
        void shouldRejectStaleVersion_beforeAnyMedicationWrite() {
            Drug stored = new Drug(); stored.setId(4); stored.setDemographicId(1);
            stored.setGenericName("ASA"); stored.setRowVersion(5L);
            ((MockDrugDao) drugDao).seed(stored);
            Drug incoming = new Drug(); incoming.setId(4); incoming.setDemographicId(1);
            incoming.setGenericName("ASA"); incoming.setRowVersion(4L);
            assertThatThrownBy(() -> updateDrug(new LoggedInInfo(), incoming)).isInstanceOf(RxSourceVersionValidator.Conflict.class);
            assertThat(mergedDrug).isNull();
            assertThat(stored.isArchived()).isFalse();
        }

        @ParameterizedTest @org.junit.jupiter.params.provider.NullSource @ValueSource(longs = {5L})
        void shouldReplaceMedication_withMatchingOrLegacyVersion(Long token) {
            Drug stored = new Drug(); stored.setId(4); stored.setDemographicId(1);
            stored.setGenericName("ASA"); stored.setRowVersion(5L);
            ((MockDrugDao) drugDao).seed(stored);
            Drug incoming = new Drug(); incoming.setId(4); incoming.setDemographicId(1);
            incoming.setGenericName("ASA"); incoming.setRowVersion(token);
            assertThat(updateDrug(new LoggedInInfo(), incoming)).isNotNull();
            assertThat(mergedDrug.isArchived()).isTrue();
        }

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

        /**
         * The REST transfer object carries the quantity as a whole number, so a stored "2.5" (of
         * 2.5 mL) reaches the client as no quantity. A replacement that states none keeps the
         * stored amount and its unit instead of dropping them (#6185).
         */
        @ParameterizedTest(name = "stored quantity \"{0}\" {1}")
        @CsvSource(nullValues = "NULL", value = {"2.5, mL", "0.5, mL", "100 mL, NULL"})
        @DisplayName("should keep a stored quantity the transfer object cannot carry when the replacement states none")
        void shouldKeepStoredQuantity_whenReplacementStatesNoneAndStoredIsNotWhole(String quantity, String unitName) {
            Drug stored = new Drug(); stored.setId(4); stored.setDemographicId(1);
            stored.setGenericName("ASA"); stored.setQuantity(quantity); stored.setUnitName(unitName);
            ((MockDrugDao) drugDao).seed(stored);
            Drug incoming = new Drug(); incoming.setId(4); incoming.setDemographicId(1);
            incoming.setGenericName("ASA");

            Drug replacement = updateDrug(new LoggedInInfo(), incoming);

            assertThat(replacement.getQuantity()).isEqualTo(quantity);
            assertThat(replacement.getUnitName()).isEqualTo(unitName);
        }

        @ParameterizedTest
        @CsvSource({"30.0", "30", "030", "2.5", "100 mL"})
        void shouldPreserveQuantityAndUnit_whenRestEchoesPublishedMedication(String quantity) throws Exception {
            Drug stored = new Drug();
            stored.setId(4);
            stored.setDemographicId(1);
            stored.setGenericName("ASA");
            stored.setProviderNo("999001");
            stored.setQuantity(quantity);
            stored.setUnitName("mL");
            ((MockDrugDao) drugDao).seed(stored);
            var converter = new io.github.carlos_emr.carlos.webserv.rest.conversion.DrugConverterImpl();
            LoggedInInfo info = new LoggedInInfo();
            var dto = converter.getAsTransferObject(info, stored);
            dto.setExternalProvider("Changed provider");
            Drug replacement = updateDrug(info, converter.getAsDomainObject(info, dto));
            assertThat(replacement.getQuantity()).isEqualTo(quantity);
            assertThat(replacement.getUnitName()).isEqualTo("mL");
            assertThat(stored.getQuantity()).isEqualTo(quantity);
            assertThat(stored.getUnitName()).isEqualTo("mL");
        }

        @ParameterizedTest
        @CsvSource({"111,tab", "222,mL"})
        void shouldKeepSubmittedQuantity_whenUnitOrProductChanges(String productId, String unit) {
            Drug source = new Drug();
            source.setId(4);
            source.setDemographicId(1);
            source.setGenericName("ASA");
            source.setRegionalIdentifier("111");
            source.setQuantity("30.0");
            source.setUnitName("mL");
            ((MockDrugDao) drugDao).seed(source);
            Drug changed = new Drug();
            changed.setId(4);
            changed.setDemographicId(1);
            changed.setGenericName("ASA");
            changed.setRegionalIdentifier(productId);
            changed.setQuantity("30");
            changed.setUnitName(unit);
            Drug replacement = updateDrug(new LoggedInInfo(), changed);
            assertThat(replacement.getQuantity()).isEqualTo("30");
            assertThat(replacement.getUnitName()).isEqualTo(unit);
        }

        @Test
        @DisplayName("should take the quantity the replacement states")
        void shouldTakeReplacementQuantity_whenReplacementStatesOne() {
            Drug stored = new Drug(); stored.setId(4); stored.setDemographicId(1);
            stored.setGenericName("ASA"); stored.setQuantity("2.5"); stored.setUnitName("mL");
            ((MockDrugDao) drugDao).seed(stored);
            Drug incoming = new Drug(); incoming.setId(4); incoming.setDemographicId(1);
            incoming.setGenericName("ASA"); incoming.setQuantity("30");

            Drug replacement = updateDrug(new LoggedInInfo(), incoming);

            assertThat(replacement.getQuantity()).isEqualTo("30");
            assertThat(replacement.getUnitName()).isNull();
        }

        @ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = {"brand", "generic", "custom", "form"})
        void shouldKeepNewClinicalValues_whenAProductDescriptorChanges(String descriptor) {
            Drug source = new Drug();
            source.setQuantity("30.0");
            Drug edited = new Drug();
            edited.recordRestFieldPresence(false, false, false);
            switch (descriptor) {
                case "brand" -> edited.setBrandName("new brand");
                case "generic" -> edited.setGenericName("new generic");
                case "custom" -> edited.setCustomName("new custom drug");
                case "form" -> edited.setDrugForm("new form");
                default -> throw new AssertionError(descriptor);
            }
            edited.preserveUnchangedMedicationValues(source);
            assertThat(edited.getQuantity()).isNull();
        }

        @Test
        void shouldKeepRecordedBoundsAndQuantity_whenConvertingTheStagedPrescription() {
            var staged = new io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData.Prescription(44, "999001", 1);
            staged.setTakeMin(0.33333334f);
            staged.setTakeMax(1f);
            staged.setQuantity("30.0");
            Drug drug = new Drug(staged);
            assertThat(drug.getTakeMin()).isEqualTo(0.33333334f);
            assertThat(drug.getTakeMax()).isEqualTo(1f);
            assertThat(drug.getQuantity()).isEqualTo("30.0");
        }

        @ParameterizedTest
        @CsvSource(nullValues = "omitted", value = {
                "1,3,5,omitted", "1,3,omitted,0.5", "5,3,5,2", "1,3,NaN,omitted", "1,3,omitted,Infinity"
        })
        void shouldRefuseInvalidCombinedDoseBeforeArchiving(float oldMin, float oldMax, Float minimum, Float maximum) {
            Drug stored = new Drug();
            stored.setId(4);
            stored.setDemographicId(1);
            stored.setGenericName("ASA");
            stored.setTakeMin(oldMin);
            stored.setTakeMax(oldMax);
            ((MockDrugDao) drugDao).seed(stored);
            Drug edited = new Drug();
            edited.setId(4);
            edited.setDemographicId(1);
            edited.setGenericName("ASA");
            edited.recordRestFieldPresence(false, minimum != null, maximum != null);
            if (minimum != null) edited.setTakeMin(minimum);
            if (maximum != null) edited.setTakeMax(maximum);
            assertThatThrownBy(() -> updateDrug(new LoggedInInfo(), edited))
                    .isInstanceOf(Drug.InvalidDoseRangeException.class);
            assertThat(stored.isArchived()).isFalse();
            assertThat(stored.getTakeMin()).isEqualTo(oldMin);
            assertThat(stored.getTakeMax()).isEqualTo(oldMax);
            assertThat(addNewDrugCallCount).isZero();
        }

        @Test
        void shouldAllowAnUnchangedHistoricalRangeOnlyForTheSameProduct() {
            Drug stored = new Drug();
            stored.setTakeMin(5);
            stored.setTakeMax(3);
            Drug echo = new Drug();
            echo.setDemographicId(1);
            echo.recordRestFieldPresence(false, false, false);
            echo.preserveUnchangedMedicationValues(stored);
            echo.requireValidRestDoseRange(stored);
            assertThat(echo.getTakeMin()).isEqualTo(5f);
            assertThat(echo.getTakeMax()).isEqualTo(3f);
            echo.setRegionalIdentifier("new DIN");
            assertThatThrownBy(() -> echo.requireValidRestDoseRange(stored)).isInstanceOf(Drug.InvalidDoseRangeException.class);
            assertThatThrownBy(() -> addDrug(new LoggedInInfo(), echo)).isInstanceOf(Drug.InvalidDoseRangeException.class);
        }

        @Test
        void shouldKeepNewCatalogId_whenNativeCallerSelectsADifferentProductWithTheSameName() {
            Drug source = new Drug();
            source.setGcnSeqNo("01");
            source.setGenericName("ASA");
            source.setDosage("old catalog strength");
            Drug changed = new Drug();
            changed.setGcnSeqNo("02");
            changed.setGenericName("ASA");
            changed.preserveUnchangedMedicationValues(source);
            assertThat(changed.getGcnSeqNo()).isEqualTo("02");
            assertThat(changed.getDosage()).isNull();
        }

        @ParameterizedTest
        @CsvSource({
                "json, omitted", "xml, omitted", "json, clear", "json, zero", "xml, zero",
                "json, differentProduct", "xml, differentProduct"
        })
        void shouldRespectSubmittedMedicationFieldsOnTheWire(String format, String change) throws Exception {
            Drug stored = new Drug();
            stored.setId(4);
            stored.setDemographicId(1);
            stored.setGenericName("ASA");
            stored.setRegionalIdentifier("111");
            stored.setGcnSeqNo("001234");
            stored.setQuantity("30.0");
            stored.setUnitName("mL");
            stored.setTakeMin(0.33333334f);
            stored.setTakeMax(3f);
            ((MockDrugDao) drugDao).seed(stored);

            String product = "differentProduct".equals(change) ? "222" : "111";
            var type = io.github.carlos_emr.carlos.webserv.rest.to.model.DrugTo1.class;
            io.github.carlos_emr.carlos.webserv.rest.to.model.DrugTo1 dto;
            if ("json".equals(format)) {
                String changes = switch (change) {
                    case "clear" -> ",\"quantity\":null";
                    case "zero" -> ",\"takeMin\":0,\"quantity\":5";
                    default -> "";
                };
                dto = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                        "{\"drugId\":4,\"demographicNo\":1,\"archived\":false,\"prn\":false,\"providerNo\":\"999001\",\"genericName\":\"ASA\",\"regionalIdentifier\":\"" + product + "\"" + changes + "}", type);
            } else {
                String changes = "zero".equals(change) ? "<takeMin>0</takeMin><quantity>5</quantity>" : "";
                String xml = "<drug><drugId>4</drugId><demographicNo>1</demographicNo><archived>false</archived><prn>false</prn><providerNo>999001</providerNo>"
                        + "<genericName>ASA</genericName><regionalIdentifier>" + product + "</regionalIdentifier>" + changes + "</drug>";
                dto = type.cast(jakarta.xml.bind.JAXBContext.newInstance(type).createUnmarshaller()
                        .unmarshal(new java.io.StringReader(xml)));
            }
            var converter = new io.github.carlos_emr.carlos.webserv.rest.conversion.DrugConverterImpl();
            var info = new LoggedInInfo();
            Drug replacement = updateDrug(info, converter.getAsDomainObject(info, dto));
            boolean changedProduct = "differentProduct".equals(change);
            assertThat(replacement.getGcnSeqNo()).isEqualTo(changedProduct ? "0" : "001234");
            if ("omitted".equals(change)) {
                assertThat(replacement.getQuantity()).isEqualTo("30.0");
                assertThat(replacement.getUnitName()).isEqualTo("mL");
            } else if ("zero".equals(change)) {
                assertThat(replacement.getQuantity()).isEqualTo("5");
            } else {
                assertThat(replacement.getQuantity()).isNull();
            }
            assertThat(replacement.getTakeMin()).isEqualTo(changedProduct || "zero".equals(change) ? 0f : 0.33333334f);
            assertThat(replacement.getTakeMax()).isEqualTo(changedProduct ? 0f : 3f);
            assertThat(stored.getGcnSeqNo()).isEqualTo("001234");
            assertThat(stored.getQuantity()).isEqualTo("30.0");
        }

        @Test
        @DisplayName("should leave the quantity out when the client could see the stored whole number and sent none")
        void shouldLeaveQuantityOut_whenStoredQuantityIsWholeAndReplacementStatesNone() {
            Drug stored = new Drug(); stored.setId(4); stored.setDemographicId(1);
            stored.setGenericName("ASA"); stored.setQuantity("30");
            ((MockDrugDao) drugDao).seed(stored);
            Drug incoming = new Drug(); incoming.setId(4); incoming.setDemographicId(1);
            incoming.setGenericName("ASA");

            Drug replacement = updateDrug(new LoggedInInfo(), incoming);

            assertThat(replacement.getQuantity()).isNull();
        }

        @ParameterizedTest
        @CsvSource(nullValues = "NULL", value = {
                "5 mg / 20 mg, 5 mg",
                "250 mg / 5 mL, 250 mg",
                "'1,000 mg', NULL",
                "0.123456789 mg, 0.12345679 mg"
        })
        void shouldKeepStoredDosage_whenReplacementEchoesTheRestProjection(String dosage, String postedDosage) {
            Drug stored = new Drug();
            stored.setId(4);
            stored.setDemographicId(1);
            stored.setGenericName("ASA");
            stored.setDosage(dosage);
            stored.setUnit("stored unit");
            ((MockDrugDao) drugDao).seed(stored);
            Drug incoming = new Drug();
            incoming.setId(4);
            incoming.setDemographicId(1);
            incoming.setGenericName("ASA");
            incoming.setDosage(postedDosage);

            Drug replacement = updateDrug(new LoggedInInfo(), incoming);

            assertThat(replacement.getDosage()).isEqualTo(dosage);
            assertThat(replacement.getUnit()).isEqualTo("stored unit");
        }

        @ParameterizedTest
        @CsvSource({"12345, 10 mg", "67890, 5 mg"})
        void shouldTakeReplacementDosage_whenStrengthOrProductChanges(String regionalIdentifier, String dosage) {
            Drug stored = new Drug();
            stored.setId(4);
            stored.setDemographicId(1);
            stored.setGenericName("ASA");
            stored.setRegionalIdentifier("12345");
            stored.setDosage("5 mg / 20 mg");
            ((MockDrugDao) drugDao).seed(stored);
            Drug incoming = new Drug();
            incoming.setId(4);
            incoming.setDemographicId(1);
            incoming.setGenericName("ASA");
            incoming.setRegionalIdentifier(regionalIdentifier);
            incoming.setDosage(dosage);

            Drug replacement = updateDrug(new LoggedInInfo(), incoming);

            assertThat(replacement.getDosage()).isEqualTo(dosage);
        }

        @Test
        void shouldLeaveStrengthMissing_whenRestChangesProductAndOmitsStrength() throws Exception {
            Drug source = new Drug();
            source.setId(4);
            source.setDemographicId(1);
            source.setProviderNo("999001");
            source.setGenericName("ASA");
            source.setRegionalIdentifier("111");
            source.setDosage("5 mg / 20 mg");
            ((MockDrugDao) drugDao).seed(source);
            var converter = new io.github.carlos_emr.carlos.webserv.rest.conversion.DrugConverterImpl();
            var info = new LoggedInInfo();
            var dto = converter.getAsTransferObject(info, source);
            dto.setRegionalIdentifier("222");
            dto.setStrength(null);
            dto.setStrengthUnit(null);
            Drug updated = updateDrug(info, converter.getAsDomainObject(info, dto));
            assertThat(updated.getDosage()).isNull();
            assertThat(updated.getUnit()).isNull();
            assertThat(source.getDosage()).isEqualTo("5 mg / 20 mg");
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

        @Test
        @DisplayName("should not archive another patient's drug when a foreign drug id is supplied")
        void shouldNotArchiveDrug_whenDemographicDoesNotMatch() {
            // A victim's drug that belongs to demographic 9.
            Drug victim = new Drug();
            victim.setId(3);
            victim.setDemographicId(9);
            victim.setGenericName("ASA"); // so addNewDrug would succeed IF the guard were reached
            victim.setArchived(false);
            ((MockDrugDao) drugDao).seed(victim);

            // Attacker is authorized for their own demographic (1, which passes writeCheck's <5 rule)
            // but points the drug id at the victim's drug (3).
            LoggedInInfo info = new LoggedInInfo();
            Drug request = new Drug();
            request.setDemographicId(1);
            request.setId(3);
            request.setGenericName("ASA");

            // The cross-patient attempt must be DENIED EXPLICITLY (throw), not laundered into a null
            // that prescribe() reinterprets as "drug not in the DB, add it" — which would create a
            // phantom medication and report success.
            assertThatThrownBy(() -> updateDrug(info, request))
                    .isInstanceOf(io.github.carlos_emr.carlos.commn.exception.AccessDeniedException.class);
            assertThat(mergedDrug).isNull();         // no drug archived
            assertThat(victim.isArchived()).isFalse(); // victim's drug untouched
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
        void shouldRejectMixedPatients_beforeCreatingTheHeaderOrAnyDrug() {
            var prescriptions = org.mockito.Mockito.mock(PrescriptionManager.class);
            prescriptionManager = prescriptions;
            Drug first = createTestDrug();
            Drug other = createTestDrug();
            other.setId(null); // A new drug has no persisted source row for the later source check.
            other.setDemographicId(2); // The caller has write access to both patients.
            assertThatThrownBy(() -> prescribe(new LoggedInInfo(), List.of(first, other), 1))
                    .isInstanceOf(AccessDeniedException.class);
            org.mockito.Mockito.verifyNoInteractions(prescriptions);
            assertThat(addNewDrugCallCount).isZero();
            assertThat(mergedDrug).isNull();
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
        @DisplayName("should reject a missing update source without inserting a replacement")
        void shouldRejectMissingUpdate_withoutInsertingReplacement() {
            List<Drug> drugs = new ArrayList<>();
            Drug drug = createTestDrug();
            drug.setId(3); // not in MockDrugDao
            drug.setGenericName("ASA"); // will succeed in addNewDrug
            drugs.add(drug);
            LoggedInInfo info = new LoggedInInfo();

            assertThatThrownBy(() -> prescribe(info, drugs, 1))
                    .isInstanceOf(PrescriptionWriteException.class);
            assertThat(addNewDrugCallCount).isZero();
        }

        @Test
        @DisplayName("should throw when adding a new prescription drug fails")
        void shouldThrow_whenAddingNewDrugFails() {
            List<Drug> drugs = new ArrayList<>();
            Drug drug = createTestDrug();
            drug.setId(3); // not in MockDrugDao
            drug.setGenericName("NOT ASA"); // will fail in addNewDrug
            drugs.add(drug);
            LoggedInInfo info = new LoggedInInfo();

            drug.setId(null);
            assertThatThrownBy(() -> prescribe(info, drugs, 1))
                    .isInstanceOf(PrescriptionWriteException.class);
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

    /**
     * Ownership contract for {@code archiveDrug}. Callers such as the re-prescribe flow stage drug
     * ids from the request and rely on this method to refuse ids belonging to another patient, so
     * both of its {@code false} branches are pinned here rather than trusted from the caller side.
     */
    @Nested
    @DisplayName("archiveDrug")
    class ArchiveDrug {

        @Test
        @DisplayName("should return false when the drug belongs to another demographic")
        void shouldReturnFalse_whenDrugBelongsToAnotherDemographic() {
            // A victim's drug, owned by demographic 9.
            Drug victim = new Drug();
            victim.setId(3);
            victim.setDemographicId(9);
            victim.setBrandName("Aspirin");
            victim.setArchived(false);
            ((MockDrugDao) drugDao).seed(victim);

            // The caller is authorized for demographic 1 (passes writeCheck's <5 rule) but points
            // the drug id at the victim's drug.
            LoggedInInfo info = new LoggedInInfo();

            assertThat(archiveDrug(info, 3, 1, Drug.REPRESCRIBED)).isFalse();
            assertThat(mergedDrug).isNull();
            assertThat(victim.isArchived()).isFalse();
        }

        @Test
        @DisplayName("should return false when no drug matches the id")
        void shouldReturnFalse_whenDrugNotFound() {
            LoggedInInfo info = new LoggedInInfo();

            assertThat(archiveDrug(info, 20, 1, Drug.REPRESCRIBED)).isFalse();
            assertThat(mergedDrug).isNull();
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

        /** Seed an extra drug (e.g. one belonging to a different demographic) for a single test. */
        void seed(Drug d) {
            this.drugs.add(d);
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
