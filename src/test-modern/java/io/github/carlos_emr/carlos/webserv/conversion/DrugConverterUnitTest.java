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
 * Migrated from legacy JUnit 4 DrugConverterTest to JUnit 5 for the CARLOS EMR project (2026).
 */
package io.github.carlos_emr.carlos.webserv.conversion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
 * <p>Tests domain-to-transfer and transfer-to-domain conversion of Drug objects,
 * including drug strength population logic.
 * Migrated from legacy JUnit 4 DrugConverterTest.
 *
 * @since 2026-03-07
 */
@Tag("unit")
@Tag("prescription")
@DisplayName("DrugConverter unit tests")
class DrugConverterUnitTest {

    private TestableDrugConverter converter;
    private LoggedInInfo info;

    @BeforeEach
    void setUp() {
        converter = new TestableDrugConverter();
        converter.setDrugLookUpManager(new MockDrugLookUpManager());
        info = new LoggedInInfo();
    }

    @Nested
    @DisplayName("Domain to Transfer conversion")
    class DomainToTransfer {

        @Test
        @DisplayName("should convert all Drug fields to DrugTo1 transfer object")
        void shouldConvertAllDrugFields_toDrugTo1() {
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

            DrugTo1 t = converter.getAsTransferObject(info, d);

            assertThat(t.getDrugId()).isEqualTo(1);
            assertThat(t.getDemographicNo()).isEqualTo(1);
            assertThat(t.getProviderNo()).isEqualTo("1");
            assertThat(t.getBrandName()).isEqualTo("Foobar");
            assertThat(t.getGenericName()).isEqualTo("Barbang");
            assertThat(t.getRegionalIdentifier()).isEqualTo("12345");
            assertThat(t.getAtc()).isEqualTo("abcde");
            assertThat(t.getTakeMin()).isCloseTo(1.0f, org.assertj.core.data.Offset.offset(0.01f));
            assertThat(t.getTakeMax()).isCloseTo(2.0f, org.assertj.core.data.Offset.offset(0.01f));
            assertThat(t.getRxDate().toString()).isEqualTo(startDate.toString());
            assertThat(t.getEndDate().toString()).isEqualTo(endDate.toString());
            assertThat(t.getFrequency()).isEqualTo("BID");
            assertThat(t.getDuration()).isEqualTo(28);
            assertThat(t.getDurationUnit()).isEqualTo("D");
            assertThat(t.getRoute()).isEqualTo("PO");
            assertThat(t.getForm()).isEqualTo("TAB");
            assertThat(t.isPrn()).isTrue();
            assertThat(t.getMethod()).isEqualTo("Take");
            assertThat(t.getRepeats()).isEqualTo(5);
            assertThat(t.getInstructions()).isEqualTo("some string");
            assertThat(t.getArchivedDate().toString()).isEqualTo(archivedDate.toString());
            assertThat(t.getArchivedReason()).isEqualTo("reason");
            assertThat(t.isArchived()).isFalse();
        }
    }

    @Nested
    @DisplayName("Transfer to Domain conversion")
    class TransferToDomain {

        @Test
        @DisplayName("should convert all DrugTo1 fields to Drug domain object")
        void shouldConvertAllDrugTo1Fields_toDrugDomain() {
            Date startDate = new Date();
            Date endDate = new Date();
            Date archivedDate = new Date();

            DrugTo1 t = new DrugTo1();
            t.setDemographicNo(1);
            t.setProviderNo("1");
            t.setGenericName("bangbar");
            t.setBrandName("foobar");
            t.setRegionalIdentifier("12345");
            t.setAtc("abcde");
            t.setTakeMax(2.0f);
            t.setTakeMin(1.0f);
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
            t.setStrength(10.0f);
            t.setStrengthUnit("MG");
            t.setExternalProvider("foo");
            t.setLongTerm(false);
            t.setNoSubstitutions(false);

            Drug d = converter.getAsDomainObject(info, t);

            assertThat(d.getDemographicId()).isEqualTo(1);
            assertThat(d.getProviderNo()).isEqualTo("1");
            assertThat(d.getGenericName()).isEqualTo("bangbar");
            assertThat(d.getBrandName()).isEqualTo("foobar");
            assertThat(d.getRegionalIdentifier()).isEqualTo("12345");
            assertThat(d.getAtc()).isEqualTo("abcde");
            assertThat(d.getTakeMin()).isCloseTo(1.0f, org.assertj.core.data.Offset.offset(0.01f));
            assertThat(d.getTakeMax()).isCloseTo(2.0f, org.assertj.core.data.Offset.offset(0.01f));
            assertThat(d.getRxDate().toString()).isEqualTo(startDate.toString());
            assertThat(d.getEndDate().toString()).isEqualTo(endDate.toString());
            assertThat(d.getFreqCode()).isEqualTo("BID");
            assertThat(d.getDuration()).isEqualTo("28");
            assertThat(d.getDurUnit()).isEqualTo("D");
            assertThat(d.getRoute()).isEqualTo("PO");
            assertThat(d.getDrugForm()).isEqualTo("TAB");
            assertThat(d.getMethod()).isEqualTo("take");
            assertThat(d.getSpecial()).isEqualTo("some string");
            assertThat(d.isPrn()).isFalse();
            assertThat(d.getArchivedReason()).isEqualTo("reason");
            assertThat(d.isArchived()).isFalse();
            assertThat(d.getArchivedDate().toString()).isEqualTo(archivedDate.toString());
            assertThat(d.isLongTerm()).isFalse();
            assertThat(d.isNoSubs()).isFalse();
            assertThat(d.getOutsideProviderName()).isEqualTo("foo");
        }
    }

    @Nested
    @DisplayName("Drug strength population")
    class DrugStrength {

        @Test
        @DisplayName("should populate drug strength with normal input")
        void shouldPopulateDrugStrength_withNormalInput() {
            Drug d = new Drug();
            DrugTo1 t = new DrugTo1();
            t.setStrengthUnit("mg");
            t.setStrength(100f);

            Boolean result = converter.populateDrugStrength(d, t);

            assertThat(d.getDosage()).isEqualTo("100.0 mg");
            assertThat(d.getUnit()).isEqualTo("mg");
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should default drug strength when no strength info provided")
        void shouldDefaultDrugStrength_whenNoStrengthInfo() {
            Drug d = new Drug();
            d.setBrandName("aspirin");
            DrugTo1 t = new DrugTo1();

            Boolean result = converter.populateDrugStrength(d, t);

            assertThat(d.getDosage()).isEqualTo("1.0 mg");
            assertThat(d.getUnit()).isEqualTo("mg");
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when strength is null")
        void shouldReturnFalse_whenStrengthIsNull() {
            Drug d = new Drug();
            DrugTo1 t = new DrugTo1();
            t.setStrengthUnit("mg");
            t.setStrength(null);

            assertThat(converter.populateDrugStrength(d, t)).isFalse();
        }

        @Test
        @DisplayName("should return false when strength unit is null")
        void shouldReturnFalse_whenStrengthUnitIsNull() {
            Drug d = new Drug();
            DrugTo1 t = new DrugTo1();
            t.setStrengthUnit(null);
            t.setStrength(100f);

            assertThat(converter.populateDrugStrength(d, t)).isFalse();
        }
    }

    @Nested
    @DisplayName("To1 strength population from drug lookup")
    class To1Strength {

        @Test
        @DisplayName("should populate To1 strength from drug lookup for recognized drug")
        void shouldPopulateTo1Strength_forRecognizedDrug() {
            DrugTo1 t = new DrugTo1();
            Drug d = new Drug();
            d.setBrandName("aspirin");

            Boolean resp = converter.populateTo1Strength(t, d);

            assertThat(resp).isTrue();
            assertThat(t.getStrength()).isEqualTo(1.0f);
            assertThat(t.getStrengthUnit()).isEqualTo("mg");
        }

        @Test
        @DisplayName("should not overwrite existing strength")
        void shouldNotOverwriteExistingStrength() {
            DrugTo1 t = new DrugTo1();
            t.setStrength(100.0f);
            Drug d = new Drug();
            d.setBrandName("aspirin");

            assertThat(converter.populateTo1Strength(t, d)).isFalse();
            assertThat(t.getStrength()).isEqualTo(100.0f);
        }

        @Test
        @DisplayName("should not overwrite existing strength unit")
        void shouldNotOverwriteExistingStrengthUnit() {
            DrugTo1 t = new DrugTo1();
            t.setStrengthUnit("mg");
            Drug d = new Drug();
            d.setBrandName("aspirin");

            assertThat(converter.populateTo1Strength(t, d)).isFalse();
            assertThat(t.getStrengthUnit()).isEqualTo("mg");
        }

        @Test
        @DisplayName("should return false for null brand name")
        void shouldReturnFalse_forNullBrandName() {
            DrugTo1 t = new DrugTo1();
            Drug d = new Drug();

            assertThat(converter.populateTo1Strength(t, d)).isFalse();
        }

        @Test
        @DisplayName("should return false for unrecognized drug")
        void shouldReturnFalse_forUnrecognizedDrug() {
            DrugTo1 t = new DrugTo1();
            Drug d = new Drug();
            d.setBrandName("not aspirin");

            assertThat(converter.populateTo1Strength(t, d)).isFalse();
        }
    }

    /**
     * Testable subclass exposing protected methods.
     */
    static class TestableDrugConverter extends DrugConverterImpl {
        void setDrugLookUpManager(DrugLookUpManager manager) {
            this.drugLookUpManager = manager;
        }

        @Override
        public DrugTo1 getAsTransferObject(LoggedInInfo info, Drug d) {
            return super.getAsTransferObject(info, d);
        }

        @Override
        public Drug getAsDomainObject(LoggedInInfo info, DrugTo1 t) {
            return super.getAsDomainObject(info, t);
        }

        @Override
        public boolean populateDrugStrength(Drug d, DrugTo1 t) {
            return super.populateDrugStrength(d, t);
        }

        @Override
        public Boolean populateTo1Strength(DrugTo1 t, Drug d) {
            return super.populateTo1Strength(t, d);
        }
    }

    /**
     * Mock DrugLookUpManager that recognizes "aspirin" as a known drug.
     */
    static class MockDrugLookUpManager extends DrugLookUpManager {

        public MockDrugLookUpManager() {
        }

        @Override
        public List<DrugSearchTo1> search(String name) {
            List<DrugSearchTo1> toReturn = new ArrayList<>();
            if ("aspirin".equals(name)) {
                DrugSearchTo1 d = new DrugSearchTo1();
                d.setName("aspirin");
                d.setId(1);
                toReturn.add(d);
            }
            return toReturn;
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
