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
package io.github.carlos_emr.carlos.webserv.conversion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.managers.DrugLookUpManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.conversion.DrugConverterImpl;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DrugSearchTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DrugTo1;

/**
 * Unit tests for {@link DrugConverterImpl}.
 *
 * <p>Tests bidirectional conversion between {@link Drug} domain model and
 * {@link DrugTo1} transfer object, including drug strength population logic.</p>
 *
 * <p>Extends {@link DrugConverterImpl} directly to access protected methods
 * and inject a mock {@link DrugLookUpManager}.</p>
 *
 * @since 2026-03-07
 */
@Tag("unit")
@DisplayName("DrugConverter")
class DrugConverterUnitTest extends DrugConverterImpl {

    private final LoggedInInfo loggedInInfo = new LoggedInInfo();

    @BeforeEach
    void setUp() {
        this.drugLookUpManager = new MockDrugLookUpManager();
    }

    @AfterEach
    void tearDown() {
        this.drugLookUpManager = null;
    }

    @Nested
    @DisplayName("getAsTransferObject")
    class DomainToTransfer {

        @Test
        @DisplayName("should convert Drug to DrugTo1 with all fields")
        void shouldConvertDrug_withAllFields() {
            Date startDate = new Date(1704067200000L);
            Date endDate = new Date(1706745600000L);
            Date archivedDate = new Date(1704067200000L);

            Drug drug = new Drug();
            drug.setId(1);
            drug.setDemographicId(1);
            drug.setProviderNo("1");
            drug.setBrandName("Foobar");
            drug.setGenericName("Barbang");
            drug.setRegionalIdentifier("12345");
            drug.setAtc("abcde");
            drug.setTakeMax(2);
            drug.setTakeMin(1);
            drug.setRxDate(startDate);
            drug.setEndDate(endDate);
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
            drug.setArchivedDate(archivedDate);
            drug.setArchivedReason("reason");

            DrugTo1 transfer = getAsTransferObject(loggedInInfo, drug);

            assertThat(transfer.getDrugId()).isEqualTo(1);
            assertThat(transfer.getDemographicNo()).isEqualTo(1);
            assertThat(transfer.getProviderNo()).isEqualTo("1");
            assertThat(transfer.getBrandName()).isEqualTo("Foobar");
            assertThat(transfer.getGenericName()).isEqualTo("Barbang");
            assertThat(transfer.getRegionalIdentifier()).isEqualTo("12345");
            assertThat(transfer.getAtc()).isEqualTo("abcde");
            assertThat(transfer.getTakeMin()).isCloseTo(1.0f, org.assertj.core.data.Offset.offset(0.01f));
            assertThat(transfer.getTakeMax()).isCloseTo(2.0f, org.assertj.core.data.Offset.offset(0.01f));
            assertThat(transfer.getRxDate().toString()).isEqualTo(startDate.toString());
            assertThat(transfer.getEndDate().toString()).isEqualTo(endDate.toString());
            assertThat(transfer.getFrequency()).isEqualTo("BID");
            assertThat(transfer.getDuration()).isEqualTo(28);
            assertThat(transfer.getDurationUnit()).isEqualTo("D");
            assertThat(transfer.getRoute()).isEqualTo("PO");
            assertThat(transfer.getForm()).isEqualTo("TAB");
            assertThat(transfer.isPrn()).isTrue();
            assertThat(transfer.getMethod()).isEqualTo("Take");
            assertThat(transfer.getRepeats()).isEqualTo(5);
            assertThat(transfer.getInstructions()).isEqualTo("some string");
            assertThat(transfer.isArchived()).isFalse();
            assertThat(transfer.getArchivedDate().toString()).isEqualTo(archivedDate.toString());
            assertThat(transfer.getArchivedReason()).isEqualTo("reason");
        }
    }

    @Nested
    @DisplayName("getAsDomainObject")
    class TransferToDomain {

        @Test
        @DisplayName("should convert DrugTo1 to Drug with all fields")
        void shouldConvertTransfer_withAllFields() {
            Date startDate = new Date(1704067200000L);
            Date endDate = new Date(1706745600000L);
            Date archivedDate = new Date(1704067200000L);

            DrugTo1 transfer = new DrugTo1();
            transfer.setDemographicNo(1);
            transfer.setProviderNo("1");
            transfer.setGenericName("bangbar");
            transfer.setBrandName("foobar");
            transfer.setRegionalIdentifier("12345");
            transfer.setAtc("abcde");
            transfer.setTakeMax(2.0f);
            transfer.setTakeMin(1.0f);
            transfer.setRxDate(startDate);
            transfer.setEndDate(endDate);
            transfer.setFrequency("BID");
            transfer.setDuration(28);
            transfer.setDurationUnit("D");
            transfer.setRoute("PO");
            transfer.setForm("TAB");
            transfer.setPrn(false);
            transfer.setMethod("take");
            transfer.setRepeats(5);
            transfer.setInstructions("some string");
            transfer.setArchived(false);
            transfer.setArchivedReason("reason");
            transfer.setArchivedDate(archivedDate);
            transfer.setStrength(10.0f);
            transfer.setStrengthUnit("MG");
            transfer.setExternalProvider("foo");
            transfer.setLongTerm(false);
            transfer.setNoSubstitutions(false);

            Drug drug = getAsDomainObject(loggedInInfo, transfer);

            assertThat(drug.getDemographicId()).isEqualTo(1);
            assertThat(drug.getProviderNo()).isEqualTo("1");
            assertThat(drug.getGenericName()).isEqualTo("bangbar");
            assertThat(drug.getBrandName()).isEqualTo("foobar");
            assertThat(drug.getRegionalIdentifier()).isEqualTo("12345");
            assertThat(drug.getAtc()).isEqualTo("abcde");
            assertThat(drug.getTakeMin()).isCloseTo(1.0f, org.assertj.core.data.Offset.offset(0.01f));
            assertThat(drug.getTakeMax()).isCloseTo(2.0f, org.assertj.core.data.Offset.offset(0.01f));
            assertThat(drug.getRxDate().toString()).isEqualTo(startDate.toString());
            assertThat(drug.getEndDate().toString()).isEqualTo(endDate.toString());
            assertThat(drug.getFreqCode()).isEqualTo("BID");
            assertThat(drug.getDuration()).isEqualTo("28");
            assertThat(drug.getDurUnit()).isEqualTo("D");
            assertThat(drug.getRoute()).isEqualTo("PO");
            assertThat(drug.getDrugForm()).isEqualTo("TAB");
            assertThat(drug.getMethod()).isEqualTo("take");
            assertThat(drug.getSpecial()).isEqualTo("some string");
            assertThat(drug.isPrn()).isFalse();
            assertThat(drug.getArchivedReason()).isEqualTo("reason");
            assertThat(drug.isArchived()).isFalse();
            assertThat(drug.getArchivedDate().toString()).isEqualTo(archivedDate.toString());
            assertThat(drug.isLongTerm()).isFalse();
            assertThat(drug.isNoSubs()).isFalse();
            assertThat(drug.getOutsideProviderName()).isEqualTo("foo");
        }
    }

    @Nested
    @DisplayName("populateDrugStrength")
    class PopulateDrugStrength {

        @Test
        @DisplayName("should populate dosage from valid strength and unit")
        void shouldPopulateDosage_fromValidStrengthAndUnit() {
            Drug drug = new Drug();
            DrugTo1 transfer = new DrugTo1();
            transfer.setStrengthUnit("mg");
            transfer.setStrength(100.0f);

            Boolean result = populateDrugStrength(drug, transfer);

            assertThat(result).isTrue();
            assertThat(drug.getDosage()).isEqualTo("100.0 mg");
            assertThat(drug.getUnit()).isEqualTo("mg");
        }

        @Test
        @DisplayName("should use defaults when no strength info provided")
        void shouldUseDefaults_whenNoStrengthInfo() {
            Drug drug = new Drug();
            drug.setBrandName("aspirin");
            DrugTo1 transfer = new DrugTo1();

            Boolean result = populateDrugStrength(drug, transfer);

            assertThat(result).isTrue();
            assertThat(drug.getDosage()).isEqualTo("1.0 mg");
            assertThat(drug.getUnit()).isEqualTo("mg");
        }

        @Test
        @DisplayName("should return false when strength is null but unit is present")
        void shouldReturnFalse_whenStrengthNull() {
            Drug drug = new Drug();
            DrugTo1 transfer = new DrugTo1();
            transfer.setStrengthUnit("mg");
            transfer.setStrength(null);

            Boolean result = populateDrugStrength(drug, transfer);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when unit is null but strength is present")
        void shouldReturnFalse_whenUnitNull() {
            Drug drug = new Drug();
            DrugTo1 transfer = new DrugTo1();
            transfer.setStrengthUnit(null);
            transfer.setStrength(100.0f);

            Boolean result = populateDrugStrength(drug, transfer);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("populateTo1Strength")
    class PopulateTo1Strength {

        @Test
        @DisplayName("should populate strength from drug lookup for recognized drug")
        void shouldPopulateStrength_forRecognizedDrug() {
            DrugTo1 transfer = new DrugTo1();
            Drug drug = new Drug();
            drug.setBrandName("aspirin");

            Boolean result = populateTo1Strength(transfer, drug);

            assertThat(result).isTrue();
            assertThat(transfer.getStrength()).isEqualTo(1.0f);
            assertThat(transfer.getStrengthUnit()).isEqualTo("mg");
        }

        @Test
        @DisplayName("should not override existing strength on transfer")
        void shouldNotOverride_existingStrength() {
            DrugTo1 transfer = new DrugTo1();
            transfer.setStrength(100.0f);
            Drug drug = new Drug();
            drug.setBrandName("aspirin");

            Boolean result = populateTo1Strength(transfer, drug);

            assertThat(result).isFalse();
            assertThat(transfer.getStrength()).isEqualTo(100.0f);
        }

        @Test
        @DisplayName("should not override existing strength unit on transfer")
        void shouldNotOverride_existingStrengthUnit() {
            DrugTo1 transfer = new DrugTo1();
            transfer.setStrengthUnit("mg");
            Drug drug = new Drug();
            drug.setBrandName("aspirin");

            Boolean result = populateTo1Strength(transfer, drug);

            assertThat(result).isFalse();
            assertThat(transfer.getStrengthUnit()).isEqualTo("mg");
        }

        @Test
        @DisplayName("should return false when brand name is null")
        void shouldReturnFalse_whenBrandNameNull() {
            DrugTo1 transfer = new DrugTo1();
            Drug drug = new Drug();

            Boolean result = populateTo1Strength(transfer, drug);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false for unrecognized drug")
        void shouldReturnFalse_forUnrecognizedDrug() {
            DrugTo1 transfer = new DrugTo1();
            Drug drug = new Drug();
            drug.setBrandName("not aspirin");

            Boolean result = populateTo1Strength(transfer, drug);

            assertThat(result).isFalse();
        }
    }

    /**
     * Mock drug lookup manager that only recognizes "aspirin" with 1.0 mg strength.
     */
    private static class MockDrugLookUpManager extends DrugLookUpManager {

        public MockDrugLookUpManager() {
        }

        @Override
        public List<DrugSearchTo1> search(String name) {
            List<DrugSearchTo1> results = new ArrayList<>();
            if ("aspirin".equals(name)) {
                DrugSearchTo1 d = new DrugSearchTo1();
                d.setName("aspirin");
                d.setId(1);
                results.add(d);
            }
            return results;
        }

        @Override
        public DrugSearchTo1 details(String id) {
            if ("1".equals(id)) {
                DrugSearchTo1 s = new DrugSearchTo1();
                List<DrugSearchTo1.DrugComponentTo1> components = new ArrayList<>();
                DrugSearchTo1.DrugComponentTo1 c = new DrugSearchTo1.DrugComponentTo1();
                c.setStrength(1.0);
                c.setUnit("mg");
                components.add(c);
                s.setComponents(components);
                return s;
            }
            return null;
        }
    }
}
